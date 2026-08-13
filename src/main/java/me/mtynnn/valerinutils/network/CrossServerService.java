package me.mtynnn.valerinutils.network;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import org.bukkit.scheduler.BukkitTask;

public final class CrossServerService {
    public record GlobalEvent(String id, UUID runId, boolean active, long startMs, long endMs, long nextMs) { }
    public record Presence(UUID uuid, String name, String serverId, String world, long updatedAt) { }
    public record PendingVoucher(UUID grantId, String targetName, String typeId, int amount,
                                 String status, boolean freshClaim) { }
    public enum VoucherResult { PENDING, DONE, PARTIAL }

    private final ValerinUtils plugin;
    private final CrossServerConfig config;
    private final UUID instanceId = UUID.randomUUID();
    private final ExecutorService io = Executors.newSingleThreadExecutor(runnable ->
            Thread.ofPlatform().name("valerinutils-cross-io").daemon(true).unstarted(runnable));
    private final CopyOnWriteArrayList<Consumer<NetworkEnvelope>> listeners = new CopyOnWriteArrayList<>();
    private final Map<String, Presence> presence = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID> voucherGrantsInFlight = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Boolean> seen = java.util.Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID, Boolean> eldest) { return size() > 4096; }
    });
    private RedisBus redis;
    private volatile boolean ready;
    private BukkitTask heartbeat;
    private volatile boolean serverClaimed;

    public CrossServerService(ValerinUtils plugin, CrossServerConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void start() {
        if (!config.enabled()) return;
        try {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    Class.forName("com.mysql.cj.jdbc.Driver");
                    createNetworkTables();
                    claimServerId();
                    serverClaimed = true;
                    redis = new RedisBus(config, this::receive);
                    try {
                        redis.start();
                        ready = true;
                    } catch (java.io.IOException redisError) {
                        redis = null;
                        ready = false;
                        plugin.getLogger().warning("[CrossServer] Redis unavailable; MySQL remains active, "
                                + "global events and replication are paused: " + redisError.getMessage());
                    }
                } catch (Exception error) {
                    throw new IllegalStateException(error);
                }
            }, io).join();
            heartbeat = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                io.execute(this::refreshServerId);
                Bukkit.getOnlinePlayers().forEach(this::playerOnline);
            }, 400L, 400L);
        } catch (RuntimeException error) {
            throw new IllegalStateException("Cross-server startup failed: " + rootMessage(error), error);
        }
    }

    public void stop() {
        ready = false;
        if (!config.enabled()) {
            io.shutdownNow();
            return;
        }
        if (heartbeat != null) heartbeat.cancel();
        if (redis != null) redis.close();
        if (serverClaimed) try { io.submit(this::releaseServerId).get(); } catch (Exception ignored) { }
        io.shutdownNow();
    }

    public boolean enabled() { return config.enabled(); }
    public boolean ready() { return ready; }
    public CrossServerConfig config() { return config; }

    public void listen(Consumer<NetworkEnvelope> listener) { listeners.add(listener); }

    public void runBlocking(Runnable operation) {
        try {
            if (Thread.currentThread().getName().equals("valerinutils-cross-io")) operation.run();
            else io.submit(operation).get();
        } catch (Exception error) {
            throw new IllegalStateException("Cross-server I/O failed", error);
        }
    }

    public void publish(String type, String payload) {
        if (!ready) return;
        io.execute(() -> {
            NetworkEnvelope envelope = NetworkEnvelope.create(config, type, payload);
            seen.put(envelope.eventId(), Boolean.TRUE);
            if (!redis.publish(envelope.encode())) {
                plugin.getLogger().warning("[CrossServer] Redis publish failed for " + type);
            }
        });
    }

    public void broadcast(String message) {
        publish("BROADCAST", message);
    }

    public void helpOp(String message) { publish("HELPOP", message); }

    public void playerOnline(Player player) {
        Presence value = new Presence(player.getUniqueId(), player.getName(), config.serverId(),
                player.getWorld().getName(), System.currentTimeMillis());
        presence.put(player.getName().toLowerCase(Locale.ROOT), value);
        publish("PRESENCE", presencePayload(true, value));
    }

    public void playerOffline(Player player) {
        presence.remove(player.getName().toLowerCase(Locale.ROOT));
        publish("PRESENCE", presencePayload(false, new Presence(player.getUniqueId(), player.getName(),
                config.serverId(), player.getWorld().getName(), System.currentTimeMillis())));
    }

    public Presence presence(String name) {
        if (name == null) return null;
        Presence value = presence.get(name.toLowerCase(Locale.ROOT));
        return value != null && System.currentTimeMillis() - value.updatedAt() <= 60_000 ? value : null;
    }

    public void applyPresence(String payload) {
        String[] values = payload.split("\u001f", -1);
        if (values.length != 7) return;
        try {
            Presence value = new Presence(UUID.fromString(values[1]), values[2], values[3], values[4],
                    Long.parseLong(values[5]));
            String key = value.name().toLowerCase(Locale.ROOT);
            Presence current = presence.get(key);
            if (current != null && current.updatedAt() > value.updatedAt()) return;
            if (Boolean.parseBoolean(values[0])) presence.put(key, value); else presence.remove(key);
        } catch (RuntimeException ignored) { }
    }

    public boolean routeAction(String targetName, String action, String argument) {
        Presence target = presence(targetName);
        if (!ready || target == null || target.serverId().equals(config.serverId())) return false;
        publish("REMOTE_ACTION", target.serverId() + "\u001f" + target.uuid() + "\u001f" + action
                + "\u001f" + (argument == null ? "" : argument));
        return true;
    }

    public void enqueueVoucher(String targetName, String typeId, int amount, Consumer<UUID> callback) {
        io.execute(() -> {
            UUID grantId = UUID.randomUUID();
            UUID stored = null;
            try (Connection connection = connection(); PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO pending_commands(id,target_name,command_line,status,created_at) VALUES(?,?,?,'PENDING',?)")) {
                insert.setString(1, grantId.toString());
                insert.setString(2, targetName.toLowerCase(Locale.ROOT));
                insert.setString(3, typeId + "\u001f" + amount);
                insert.setLong(4, System.currentTimeMillis());
                if (insert.executeUpdate() == 1) stored = grantId;
            } catch (SQLException error) { warn("queue voucher", error); }
            deliver(callback, stored);
        });
    }

    public void deliverPending(Player player) {
        String target = player.getName().toLowerCase(Locale.ROOT);
        io.execute(() -> {
            java.util.List<PendingVoucher> grants = new java.util.ArrayList<>();
            try (Connection connection = connection()) {
                connection.setAutoCommit(false);
                try (PreparedStatement select = connection.prepareStatement(
                        "SELECT id,command_line,status FROM pending_commands WHERE target_name=? FOR UPDATE")) {
                    select.setString(1, target);
                    try (ResultSet result = select.executeQuery()) {
                        while (result.next()) {
                            PendingVoucher grant = parseVoucher(result.getString(1), target, result.getString(2),
                                    result.getString(3), false);
                            if (grant == null || !voucherGrantsInFlight.add(grant.grantId())) continue;
                            if ("PENDING".equals(grant.status())) {
                                try (PreparedStatement claim = connection.prepareStatement(
                                        "UPDATE pending_commands SET status='CLAIMED',claimed_server=?,claimed_at=? "
                                                + "WHERE id=? AND status='PENDING'")) {
                                    claim.setString(1, config.serverId()); claim.setLong(2, System.currentTimeMillis());
                                    claim.setString(3, grant.grantId().toString());
                                    if (claim.executeUpdate() == 1) grant = new PendingVoucher(grant.grantId(), target,
                                            grant.typeId(), grant.amount(), "CLAIMED", true);
                                    else { voucherGrantsInFlight.remove(grant.grantId()); continue; }
                                }
                            }
                            grants.add(grant);
                        }
                    }
                }
                connection.commit();
            } catch (SQLException error) {
                grants.forEach(grant -> voucherGrantsInFlight.remove(grant.grantId()));
                warn("claim pending vouchers", error);
                return;
            }
            for (PendingVoucher grant : grants) deliver(plugin::deliverPendingVoucher, grant);
        });
    }

    public void resolveVoucher(PendingVoucher grant, VoucherResult result, int leftover,
                               Consumer<Boolean> callback) {
        io.execute(() -> {
            boolean committed = false;
            try (Connection connection = connection()) {
                connection.setAutoCommit(false);
                if (result == VoucherResult.PENDING) {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE pending_commands SET status='PENDING',claimed_server=NULL,claimed_at=NULL "
                                    + "WHERE id=? AND status='CLAIMED'")) {
                        update.setString(1, grant.grantId().toString());
                        committed = update.executeUpdate() == 1;
                    }
                } else {
                    try (PreparedStatement update = connection.prepareStatement(
                            "UPDATE pending_commands SET status='DONE' WHERE id=? AND status='CLAIMED'")) {
                        update.setString(1, grant.grantId().toString());
                        committed = update.executeUpdate() == 1;
                    }
                    if (committed && result == VoucherResult.PARTIAL && leftover > 0) {
                        try (PreparedStatement insert = connection.prepareStatement(
                                "INSERT INTO pending_commands(id,target_name,command_line,status,created_at) "
                                        + "VALUES(?,?,?,'PENDING',?)")) {
                            insert.setString(1, UUID.randomUUID().toString()); insert.setString(2, grant.targetName());
                            insert.setString(3, grant.typeId() + "\u001f" + leftover);
                            insert.setLong(4, System.currentTimeMillis());
                            committed = insert.executeUpdate() == 1;
                        }
                    }
                }
                if (committed) connection.commit(); else connection.rollback();
            } catch (SQLException error) { warn("resolve voucher grant", error); }
            voucherGrantsInFlight.remove(grant.grantId());
            deliver(callback, committed);
        });
    }

    public void cleanupVoucher(UUID grantId) {
        io.execute(() -> {
            try (Connection connection = connection(); PreparedStatement delete = connection.prepareStatement(
                    "DELETE FROM pending_commands WHERE id=? AND status='DONE'")) {
                delete.setString(1, grantId.toString()); delete.executeUpdate();
            } catch (SQLException error) { warn("cleanup voucher grant", error); }
            voucherGrantsInFlight.remove(grantId);
        });
    }

    public void releaseVoucher(UUID grantId) { voucherGrantsInFlight.remove(grantId); }

    public void invalidatePlayer(UUID playerId) {
        publish("INVALIDATE_PLAYER", playerId.toString());
    }

    public void invalidateServer(String key) {
        publish("INVALIDATE_SERVER", key);
    }

    public void syncEvent(String id, long proposedNext, Consumer<GlobalEvent> callback) {
        io.execute(() -> {
            try (Connection connection = connection();
                 PreparedStatement insert = connection.prepareStatement(
                         "INSERT IGNORE INTO global_events(event_id,active,next_ms) VALUES(?,0,?)")) {
                insert.setString(1, id);
                insert.setLong(2, proposedNext);
                insert.executeUpdate();
                deliver(callback, readEvent(connection, id));
            } catch (SQLException error) {
                warn("sync event " + id, error);
            }
        });
    }

    public void claimEventStart(String id, int durationSeconds, long nextMs, boolean force,
                                Consumer<GlobalEvent> callback) {
        io.execute(() -> {
            if (!globalStartAllowed(ready, redis != null && redis.ping())) {
                deliver(callback, null);
                return;
            }
            long now = System.currentTimeMillis();
            UUID runId = UUID.randomUUID();
            try (Connection connection = connection()) {
                connection.setAutoCommit(false);
                String sql = "UPDATE global_events SET run_id=?,active=1,start_ms=?,end_ms=?,next_ms=? "
                        + "WHERE event_id=? AND active=0" + (force ? "" : " AND next_ms<=?");
                try (PreparedStatement update = connection.prepareStatement(sql)) {
                    update.setString(1, runId.toString());
                    update.setLong(2, now);
                    update.setLong(3, now + durationSeconds * 1000L);
                    update.setLong(4, nextMs);
                    update.setString(5, id);
                    if (!force) update.setLong(6, now);
                    if (update.executeUpdate() != 1) {
                        connection.rollback();
                        deliver(callback, readEvent(connection, id));
                        return;
                    }
                    connection.commit();
                }
                GlobalEvent event = readEvent(connection, id);
                redis.publish(NetworkEnvelope.create(config, "EVENT_START", eventPayload(event)).encode());
                deliver(callback, event);
            } catch (SQLException error) {
                warn("claim event " + id, error);
                deliver(callback, null);
            }
        });
    }

    public void claimEventStop(String id, UUID runId, long nextMs, Consumer<GlobalEvent> callback) {
        io.execute(() -> {
            try (Connection connection = connection();
                 PreparedStatement update = connection.prepareStatement(
                         "UPDATE global_events SET active=0,next_ms=? WHERE event_id=? AND run_id=? AND active=1")) {
                update.setLong(1, nextMs);
                update.setString(2, id);
                update.setString(3, runId.toString());
                if (update.executeUpdate() == 1) {
                    GlobalEvent event = readEvent(connection, id);
                    if (redis != null) redis.publish(NetworkEnvelope.create(config, "EVENT_STOP", eventPayload(event)).encode());
                    deliver(callback, event);
                } else deliver(callback, readEvent(connection, id));
            } catch (SQLException error) {
                warn("stop event " + id, error);
            }
        });
    }

    public void claimEventApplication(UUID runId, String phase, Consumer<Boolean> callback) {
        io.execute(() -> {
            boolean claimed = false;
            try (Connection connection = connection()) {
                claimed = claimApplication(connection, runId, config.serverId(), phase, true);
            } catch (SQLException error) {
                warn("claim event application", error);
            }
            deliver(callback, claimed);
        });
    }

    static boolean claimApplication(Connection connection, UUID runId, String serverId,
                                    String phase, boolean mysql) throws SQLException {
        String sql = (mysql ? "INSERT IGNORE" : "INSERT OR IGNORE")
                + " INTO event_applications(run_id,server_id,phase) VALUES(?,?,?)";
        try (PreparedStatement insert = connection.prepareStatement(sql)) {
            insert.setString(1, runId.toString());
            insert.setString(2, serverId);
            insert.setString(3, phase);
            return insert.executeUpdate() == 1;
        }
    }

    static boolean globalStartAllowed(boolean redisReady, boolean pingSucceeded) {
        return redisReady && pingSucceeded;
    }

    public Connection connection() throws SQLException {
        return DriverManager.getConnection(config.mysqlUrl(), config.mysqlUser(), config.mysqlPassword());
    }

    private void receive(String raw) {
        NetworkEnvelope envelope = NetworkEnvelope.decode(raw, config);
        if (!acceptEnvelope(envelope, config, seen)) return;
        Bukkit.getScheduler().runTask(plugin, () -> listeners.forEach(listener -> listener.accept(envelope)));
    }

    static boolean acceptEnvelope(NetworkEnvelope envelope, CrossServerConfig config, Map<UUID, Boolean> seen) {
        return envelope != null && !envelope.loopback(config)
                && seen.putIfAbsent(envelope.eventId(), Boolean.TRUE) == null;
    }

    private void createNetworkTables() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS global_events(event_id VARCHAR(128) PRIMARY KEY,run_id CHAR(36),active BOOLEAN NOT NULL DEFAULT 0,start_ms BIGINT NOT NULL DEFAULT 0,end_ms BIGINT NOT NULL DEFAULT 0,next_ms BIGINT NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE IF NOT EXISTS event_applications(run_id CHAR(36),server_id VARCHAR(64),phase VARCHAR(16),PRIMARY KEY(run_id,server_id,phase))");
            statement.execute("CREATE TABLE IF NOT EXISTS server_registry(server_id VARCHAR(64) PRIMARY KEY,instance_id CHAR(36) NOT NULL,last_seen BIGINT NOT NULL)");
            statement.execute("CREATE TABLE IF NOT EXISTS pending_commands(id CHAR(36) PRIMARY KEY,target_name VARCHAR(16) NOT NULL,command_line VARCHAR(1024) NOT NULL,status VARCHAR(16) NOT NULL,created_at BIGINT NOT NULL,claimed_server VARCHAR(64),claimed_at BIGINT)");
        }
    }

    private void claimServerId() throws SQLException {
        long now = System.currentTimeMillis();
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT instance_id,last_seen FROM server_registry WHERE server_id=? FOR UPDATE")) {
                select.setString(1, config.serverId());
                try (ResultSet result = select.executeQuery()) {
                    if (result.next() && now - result.getLong("last_seen") < 60_000
                            && !instanceId.toString().equals(result.getString("instance_id"))) {
                        connection.rollback();
                        throw new SQLException("server-id already active: " + config.serverId());
                    }
                }
            }
            try (PreparedStatement upsert = connection.prepareStatement(
                    "INSERT INTO server_registry(server_id,instance_id,last_seen) VALUES(?,?,?) ON DUPLICATE KEY UPDATE instance_id=VALUES(instance_id),last_seen=VALUES(last_seen)")) {
                upsert.setString(1, config.serverId());
                upsert.setString(2, instanceId.toString());
                upsert.setLong(3, now);
                upsert.executeUpdate();
            }
            connection.commit();
        }
    }

    private void refreshServerId() {
        try (Connection connection = connection(); PreparedStatement update = connection.prepareStatement(
                "UPDATE server_registry SET last_seen=? WHERE server_id=? AND instance_id=?")) {
            update.setLong(1, System.currentTimeMillis());
            update.setString(2, config.serverId());
            update.setString(3, instanceId.toString());
            if (update.executeUpdate() != 1) {
                ready = false;
                plugin.getLogger().severe("[CrossServer] Lost unique server-id lease; network actions disabled.");
            }
        } catch (SQLException error) {
            plugin.getLogger().warning("[CrossServer] Could not refresh server-id lease: " + error.getMessage());
        }
    }

    private void releaseServerId() {
        try (Connection connection = connection(); PreparedStatement delete = connection.prepareStatement(
                "DELETE FROM server_registry WHERE server_id=? AND instance_id=?")) {
            delete.setString(1, config.serverId());
            delete.setString(2, instanceId.toString());
            delete.executeUpdate();
        } catch (SQLException ignored) { }
    }

    public static GlobalEvent parseEventPayload(String payload) {
        try {
            String[] values = payload.split("\\|", -1);
            if (values.length != 6) return null;
            return new GlobalEvent(values[0], UUID.fromString(values[1]), Boolean.parseBoolean(values[2]),
                    Long.parseLong(values[3]), Long.parseLong(values[4]), Long.parseLong(values[5]));
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static String eventPayload(GlobalEvent event) {
        return event.id() + "|" + event.runId() + "|" + event.active() + "|" + event.startMs()
                + "|" + event.endMs() + "|" + event.nextMs();
    }

    private GlobalEvent readEvent(Connection connection, String id) throws SQLException {
        try (PreparedStatement select = connection.prepareStatement("SELECT * FROM global_events WHERE event_id=?")) {
            select.setString(1, id);
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) return null;
                String run = result.getString("run_id");
                return new GlobalEvent(id, run == null ? new UUID(0, 0) : UUID.fromString(run),
                        result.getBoolean("active"), result.getLong("start_ms"), result.getLong("end_ms"),
                        result.getLong("next_ms"));
            }
        }
    }

    private <T> void deliver(Consumer<T> callback, T value) {
        Bukkit.getScheduler().runTask(plugin, () -> callback.accept(value));
    }

    private static PendingVoucher parseVoucher(String id, String target, String value, String status, boolean fresh) {
        try {
            String[] parts = value.split("\u001f", -1);
            if (parts.length != 2 || parts[0].isBlank()) return null;
            int amount = Integer.parseInt(parts[1]);
            if (amount < 1) return null;
            return new PendingVoucher(UUID.fromString(id), target, parts[0], amount, status, fresh);
        } catch (RuntimeException ignored) { return null; }
    }

    private static String presencePayload(boolean online, Presence value) {
        return online + "\u001f" + value.uuid() + "\u001f" + value.name() + "\u001f" + value.serverId()
                + "\u001f" + value.world() + "\u001f" + value.updatedAt() + "\u001f1";
    }

    private void warn(String action, Exception error) {
        plugin.getLogger().warning("[CrossServer] Could not " + action + ": " + error.getMessage());
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage();
    }
}
