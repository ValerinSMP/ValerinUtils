package me.mtynnn.valerinutils.dimension;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public final class DimensionAccessGuard implements Listener {
    private final ValerinUtils plugin;
    private final Map<UUID, Long> deniedUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Object> pendingFallbacks = new ConcurrentHashMap<>();
    private volatile Settings settings = Settings.disabled();

    public DimensionAccessGuard(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration root) {
        Settings updated = Settings.updated(settings,
                root == null ? null : root.getConfigurationSection("dimension-access"));
        if (updated == settings) {
            plugin.getLogger().warning("[DimensionAccess] Invalid configuration; keeping the last valid snapshot.");
            return;
        }
        settings = updated;
        if (!updated.enabled()) {
            deniedUntil.clear();
            pendingFallbacks.clear();
        }
    }

    public void stop() {
        deniedUntil.clear();
        pendingFallbacks.clear();
    }

    public boolean canAccess(Player player, Dimension dimension) {
        return allows(player.isOp(), player.hasPermission(dimension.permission()), dimension.allowOperators());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Dimension dimension = settings.dimension(event.getTo().getWorld().getName());
        if (dimension != null && !canAccess(event.getPlayer(), dimension)) {
            event.setCancelled(true);
            deny(event.getPlayer(), dimension, "teleport", false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        enforce(event.getPlayer(), "world-change");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        enforce(player, "join");
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) enforce(player, "join-delayed");
        }, settings.checkDelayTicks());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Dimension dimension = settings.dimension(event.getRespawnLocation().getWorld().getName());
        if (dimension != null && !canAccess(event.getPlayer(), dimension)) {
            plugin.getServer().getScheduler().runTask(plugin,
                    () -> deny(event.getPlayer(), dimension, "respawn", false));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        deniedUntil.remove(playerId);
        pendingFallbacks.remove(playerId);
    }

    private void enforce(Player player, String cause) {
        Dimension dimension = settings.dimension(player.getWorld().getName());
        if (dimension != null && !canAccess(player, dimension)) {
            deny(player, dimension, cause, true);
        }
    }

    private void deny(Player player, Dimension dimension, String cause, boolean validateContext) {
        long now = plugin.getServer().getCurrentTick();
        if (deniedUntil.getOrDefault(player.getUniqueId(), 0L) > now) return;
        deniedUntil.put(player.getUniqueId(), now + settings.denialCooldownTicks());

        player.sendMessage(plugin.parseComponent(dimension.denyMessage()));
        dimension.sound().play(player);
        queueFallback(player.getUniqueId(), player.getWorld().getName(), dimension,
                validateContext, settings.fallbackServer(), settings.fallbackDelayTicks());
        plugin.getLogger().info("[DimensionAccess] player=" + player.getName()
                + " dimension=" + dimension.id() + " world=" + player.getWorld().getName()
                + " cause=" + cause + " permission=" + dimension.permission() + " action=fallback-queued");
    }

    private void queueFallback(UUID playerId, String sourceWorld, Dimension dimension,
                               boolean validateContext, String server, long delayTicks) {
        Object token = new Object();
        if (pendingFallbacks.putIfAbsent(playerId, token) != null) return;
        deferFallback(delayTicks, () -> {
            if (!pendingFallbacks.remove(playerId, token)) {
                logFallback(playerId, dimension, "fallback-skipped reason=token-invalid");
                return;
            }
            if (!plugin.isEnabled()) {
                logFallback(playerId, dimension, "fallback-skipped reason=plugin-disabled");
                return;
            }
            Player player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) {
                logFallback(playerId, dimension, "fallback-skipped reason=offline");
                return;
            }
            boolean sameContext = player.getWorld().getName().equalsIgnoreCase(sourceWorld);
            boolean hasAccess = canAccess(player, dimension);
            if (!shouldTransfer(validateContext, sameContext, hasAccess)) {
                logFallback(playerId, dimension, "fallback-skipped reason="
                        + (hasAccess ? "permission-granted" : "context-changed"));
                return;
            }
            if (connect(player, server)) {
                logFallback(playerId, dimension, "fallback-sent server=" + server);
            } else {
                logFallback(playerId, dimension, "fallback-skipped reason=send-failed");
            }
        }, (delay, task) -> plugin.getServer().getScheduler().runTaskLater(plugin, task, delay));
    }

    private boolean connect(Player player, String server) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF("Connect");
            output.writeUTF(server);
            player.sendPluginMessage(plugin, "BungeeCord", bytes.toByteArray());
            return true;
        } catch (IOException error) {
            plugin.getLogger().warning("[DimensionAccess] Could not connect " + player.getName()
                    + " to " + server + ": " + error.getMessage());
            return false;
        }
    }

    private void logFallback(UUID playerId, Dimension dimension, String action) {
        plugin.getLogger().info("[DimensionAccess] playerId=" + playerId
                + " dimension=" + dimension.id() + " permission=" + dimension.permission()
                + " action=" + action);
    }

    static boolean allows(boolean operator, boolean permission, boolean allowOperators) {
        return permission || allowOperators && operator;
    }

    static void deferFallback(long delayTicks, Runnable fallback, BiConsumer<Long, Runnable> scheduler) {
        scheduler.accept(Math.max(1, delayTicks), fallback);
    }

    static boolean shouldTransfer(boolean validateContext, boolean sameContext, boolean canAccess) {
        return (!validateContext || sameContext) && !canAccess;
    }

    record Dimension(String id, Set<String> worlds, String permission, boolean allowOperators,
                     String denyMessage, DenySound sound) {
    }

    record DenySound(boolean enabled, Sound sound, float volume, float pitch) {
        void play(Player player) {
            if (enabled && sound != null) player.playSound(player.getLocation(), sound, volume, pitch);
        }
    }

    record Settings(boolean enabled, String fallbackServer, long fallbackDelayTicks, long checkDelayTicks,
                    long denialCooldownTicks, Map<String, Dimension> dimensions) {
        static Settings disabled() {
            return new Settings(false, "survival", 1, 10, 20, Map.of());
        }

        static Settings updated(Settings current, ConfigurationSection root) {
            Settings parsed = parse(root);
            return parsed == null ? current : parsed;
        }

        static Settings parse(ConfigurationSection root) {
            if (root == null || !root.getBoolean("enabled", true)) return disabled();
            String fallbackServer = root.getString("fallback-server", "survival");
            if (fallbackServer == null || fallbackServer.trim().isEmpty()) return null;
            fallbackServer = fallbackServer.trim();
            Map<String, Dimension> dimensions = new LinkedHashMap<>();
            ConfigurationSection entries = root.getConfigurationSection("dimensions");
            if (entries != null) {
                for (String id : entries.getKeys(false)) {
                    ConfigurationSection entry = entries.getConfigurationSection(id);
                    if (entry == null) continue;
                    Set<String> worlds = entry.getStringList("worlds").stream()
                            .map(world -> world.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
                    String permission = entry.getString("permission", "").trim();
                    if (worlds.isEmpty() || permission.isEmpty()) continue;
                    ConfigurationSection sound = entry.getConfigurationSection("deny-sound");
                    boolean soundEnabled = sound != null && sound.getBoolean("enabled", true);
                    Sound soundType = null;
                    if (soundEnabled) {
                        try {
                            soundType = Sound.valueOf(sound.getString("sound", "BLOCK_NOTE_BLOCK_BASS").toUpperCase(Locale.ROOT));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    dimensions.put(id.toLowerCase(Locale.ROOT), new Dimension(
                            id.toLowerCase(Locale.ROOT), worlds, permission,
                            entry.getBoolean("allow-operators", true),
                            entry.getString("deny-message", "<red>No tienes acceso a esta dimensión."),
                            new DenySound(soundEnabled, soundType,
                                    sound == null ? 1F : (float) sound.getDouble("volume", 1),
                                    sound == null ? 1F : (float) sound.getDouble("pitch", 1))));
                }
            }
            return new Settings(true, fallbackServer,
                    Math.max(1, root.getLong("fallback-delay-ticks", 1)),
                    Math.max(0, root.getLong("check-delay-ticks", 10)),
                    Math.max(1, root.getLong("denial-cooldown-ticks", 20)), Map.copyOf(dimensions));
        }

        Dimension dimension(String world) {
            if (!enabled || world == null) return null;
            String normalized = world.toLowerCase(Locale.ROOT);
            return dimensions.values().stream().filter(dimension -> dimension.worlds().contains(normalized))
                    .findFirst().orElse(null);
        }
    }
}
