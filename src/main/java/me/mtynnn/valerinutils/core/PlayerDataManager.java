package me.mtynnn.valerinutils.core;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

public class PlayerDataManager implements Listener {
    private final ValerinUtils plugin;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(ValerinUtils plugin) { this.plugin = plugin; }
    public PlayerData get(UUID uuid) { return cache.get(uuid); }

    @EventHandler
    public void onAsyncLogin(AsyncPlayerPreLoginEvent event) {
        load(event.getUniqueId(), event.getName());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PlayerData data = cache.remove(event.getPlayer().getUniqueId());
        if (data == null || !data.isDirty()) return;
        if (plugin.getDatabaseManager().crossServer()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> saveToDB(data));
        } else saveToDB(data);
    }

    public void saveAllAndClear() {
        Runnable save = () -> cache.values().stream().filter(PlayerData::isDirty).forEach(this::saveToDB);
        if (plugin.getDatabaseManager().crossServer()) plugin.getCrossServerService().runBlocking(save);
        else save.run();
        cache.clear();
    }

    public void reloadOnlinePlayers() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        Map<UUID, String> players = new java.util.LinkedHashMap<>();
        Bukkit.getOnlinePlayers().forEach(player -> players.put(player.getUniqueId(), player.getName()));
        Runnable load = () -> players.forEach(this::load);
        if (plugin.getDatabaseManager().crossServer()) plugin.getCrossServerService().runBlocking(load);
        else load.run();
    }

    private void load(UUID uuid, String name) {
        try {
            PlayerData data = loadFromDB(uuid);
            if (data == null) data = new PlayerData(uuid, name);
            else data.setName(name);
            cache.put(uuid, data);
        } catch (SQLException error) {
            plugin.getLogger().severe("Error loading data for " + name + ": " + error.getMessage());
        }
    }

    public void removeStaleEntries() {
        cache.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
    }

    private PlayerData loadFromDB(UUID uuid) throws SQLException {
        Connection operation = plugin.getDatabaseManager().getConnection();
        try (PreparedStatement statement = operation.prepareStatement("SELECT * FROM player_data WHERE uuid=?")) {
            statement.setString(1, uuid.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return null;
                PlayerData data = new PlayerData(uuid, result.getString("name"));
                data.setKills(result.getInt("kills"));
                data.setDeaths(result.getInt("deaths"));
                data.setDailyRewardsCount(result.getInt("daily_kills"));
                data.setLastDailyReset(result.getLong("last_daily_reset"));
                data.setMenuDisabled(result.getBoolean("menu_disabled"));
                data.setNickname(result.getString("nickname"));
                data.setTotalMoneyEarned(result.getDouble("total_money_earned"));
                data.setTotalShardsEarned(result.getDouble("total_shards_earned"));
                data.setGraceExpiresAt(result.getLong("grace_expires_at"));
                data.setGracePvpWarned(result.getBoolean("grace_pvp_warned"));
                if (plugin.getDatabaseManager().crossServer()) data.setRevision(result.getLong("revision"));
                data.setDirty(false);
                return data;
            }
        } finally {
            plugin.getDatabaseManager().closeOperation(operation);
        }
    }

    private void saveToDB(PlayerData data) {
        if (plugin.getDatabaseManager().crossServer()) {
            saveCrossServer(data);
            return;
        }
        String values = "(uuid,name,kills,deaths,daily_kills,last_daily_reset,menu_disabled,nickname,total_money_earned,total_shards_earned,grace_expires_at,grace_pvp_warned) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)";
        String updates = plugin.getDatabaseManager().crossServer()
                ? "name=VALUES(name),kills=VALUES(kills),deaths=VALUES(deaths),daily_kills=VALUES(daily_kills),last_daily_reset=VALUES(last_daily_reset),menu_disabled=VALUES(menu_disabled),nickname=VALUES(nickname),grace_expires_at=VALUES(grace_expires_at),grace_pvp_warned=VALUES(grace_pvp_warned)"
                : "name=excluded.name,kills=excluded.kills,deaths=excluded.deaths,daily_kills=excluded.daily_kills,last_daily_reset=excluded.last_daily_reset,menu_disabled=excluded.menu_disabled,nickname=excluded.nickname,total_money_earned=excluded.total_money_earned,total_shards_earned=excluded.total_shards_earned,grace_expires_at=excluded.grace_expires_at,grace_pvp_warned=excluded.grace_pvp_warned";
        String sql = "INSERT INTO player_data " + values
                + (plugin.getDatabaseManager().crossServer() ? " ON DUPLICATE KEY UPDATE " : " ON CONFLICT(uuid) DO UPDATE SET ")
                + updates;
        Connection operation = null;
        try {
            operation = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement statement = operation.prepareStatement(sql)) {
                statement.setString(1, data.getUuid().toString());
                statement.setString(2, data.getName());
                statement.setInt(3, data.getKills());
                statement.setInt(4, data.getDeaths());
                statement.setInt(5, data.getDailyRewardsCount());
                statement.setLong(6, data.getLastDailyReset());
                statement.setBoolean(7, data.isMenuDisabled());
                statement.setString(8, data.getNickname());
                statement.setDouble(9, data.getTotalMoneyEarned());
                statement.setDouble(10, data.getTotalShardsEarned());
                statement.setLong(11, data.getGraceExpiresAt());
                statement.setBoolean(12, data.isGracePvpWarned());
                statement.executeUpdate();
            }
            data.setDirty(false);
            if (plugin.getDatabaseManager().crossServer()) plugin.getCrossServerService().invalidatePlayer(data.getUuid());
        } catch (SQLException error) {
            plugin.getLogger().severe("Could not save data for " + data.getName() + ": " + error.getMessage());
        } finally {
            plugin.getDatabaseManager().closeOperation(operation);
        }
    }

    private void saveCrossServer(PlayerData data) {
        String insert = "INSERT IGNORE INTO player_data(uuid,name,kills,deaths,daily_kills,last_daily_reset,menu_disabled,nickname,total_money_earned,total_shards_earned,grace_expires_at,grace_pvp_warned,revision) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,1)";
        String update = "UPDATE player_data SET name=?,kills=?,deaths=?,daily_kills=?,last_daily_reset=?,menu_disabled=?,nickname=?,grace_expires_at=?,grace_pvp_warned=?,revision=revision+1 WHERE uuid=? AND revision=?";
        Connection connection = null;
        try {
            connection = plugin.getDatabaseManager().getConnection();
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                bindAll(statement, data, true);
                if (statement.executeUpdate() == 1) {
                    data.setRevision(1);
                    data.setDirty(false);
                    plugin.getCrossServerService().invalidatePlayer(data.getUuid());
                    return;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setString(1, data.getName());
                statement.setInt(2, data.getKills());
                statement.setInt(3, data.getDeaths());
                statement.setInt(4, data.getDailyRewardsCount());
                statement.setLong(5, data.getLastDailyReset());
                statement.setBoolean(6, data.isMenuDisabled());
                statement.setString(7, data.getNickname());
                statement.setLong(8, data.getGraceExpiresAt());
                statement.setBoolean(9, data.isGracePvpWarned());
                statement.setString(10, data.getUuid().toString());
                statement.setLong(11, data.getRevision());
                if (statement.executeUpdate() != 1) {
                    invalidate(data.getUuid());
                    return;
                }
            }
            data.setRevision(data.getRevision() + 1);
            data.setDirty(false);
            plugin.getCrossServerService().invalidatePlayer(data.getUuid());
        } catch (SQLException error) {
            plugin.getLogger().warning("Could not atomically save " + data.getUuid() + ": " + error.getMessage());
        } finally {
            plugin.getDatabaseManager().closeOperation(connection);
        }
    }

    private static void bindAll(PreparedStatement statement, PlayerData data, boolean includeUuid) throws SQLException {
        int offset = 0;
        if (includeUuid) statement.setString(++offset, data.getUuid().toString());
        statement.setString(++offset, data.getName());
        statement.setInt(++offset, data.getKills());
        statement.setInt(++offset, data.getDeaths());
        statement.setInt(++offset, data.getDailyRewardsCount());
        statement.setLong(++offset, data.getLastDailyReset());
        statement.setBoolean(++offset, data.isMenuDisabled());
        statement.setString(++offset, data.getNickname());
        statement.setDouble(++offset, data.getTotalMoneyEarned());
        statement.setDouble(++offset, data.getTotalShardsEarned());
        statement.setLong(++offset, data.getGraceExpiresAt());
        statement.setBoolean(++offset, data.isGracePvpWarned());
    }

    public boolean addEarnings(UUID uuid, EarningsCurrency currency, double amount) {
        if (amount <= 0 || !Double.isFinite(amount)) return false;
        if (plugin.getDatabaseManager().crossServer()) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (!plugin.getDatabaseManager().incrementEarnings(uuid.toString(), currency, amount)) return;
                plugin.getCrossServerService().invalidatePlayer(uuid);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    PlayerData current = cache.get(uuid);
                    if (current != null) current.addEarnings(currency, amount);
                });
            });
            return true;
        }
        PlayerData data = cache.get(uuid);
        return persistThenPublish(() -> plugin.getDatabaseManager().incrementEarnings(uuid.toString(), currency, amount),
                () -> { if (data != null) data.addEarnings(currency, amount); });
    }

    public void invalidate(UUID uuid) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> invalidate(uuid));
            return;
        }
        cache.remove(uuid);
        plugin.getDatabaseManager().invalidatePlayerCache(uuid.toString());
        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) Bukkit.getScheduler().runTaskAsynchronously(plugin,
                () -> load(uuid, player.getName()));
    }

    static boolean persistThenPublish(BooleanSupplier persist, Runnable publish) {
        if (!persist.getAsBoolean()) return false;
        publish.run();
        return true;
    }
}
