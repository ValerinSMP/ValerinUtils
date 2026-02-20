package me.mtynnn.valerinutils.modules.pvpmina;

import me.mtynnn.valerinutils.ValerinUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

final class PvpMinaRuntime {

    private final ValerinUtils plugin;
    private final Random random = new Random();
    private final Set<Material> currentBlockedItems = new HashSet<>();

    private String currentMode;
    private Instant nextRotationTime;
    private BukkitTask rotationTask;
    private BossBar activeBossBar;
    private long intervalMinutes;
    private String targetWorldName;

    PvpMinaRuntime(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    void enable() {
        reloadConfigCache();
        if (currentMode == null) {
            rotateMode(true);
        } else {
            loadModeSettings(currentMode);
        }
        startTask();
    }

    void disable() {
        if (rotationTask != null && !rotationTask.isCancelled()) {
            rotationTask.cancel();
        }
        if (activeBossBar != null) {
            activeBossBar.removeAll();
        }
    }

    FileConfiguration getConfig() {
        return plugin.getConfigManager().getConfig("pvpmina");
    }

    void reloadConfigCache() {
        FileConfiguration cfg = getConfig();
        if (cfg == null) {
            return;
        }

        this.targetWorldName = cfg.getString("world", "world_minapvp");
        this.intervalMinutes = cfg.getLong("interval-minutes", 120);

        try {
            BarColor color = BarColor.valueOf(cfg.getString("messages.bossbar-color", "RED").toUpperCase());
            BarStyle style = BarStyle.valueOf(cfg.getString("messages.bossbar-style", "SOLID").toUpperCase());
            if (activeBossBar == null) {
                activeBossBar = Bukkit.createBossBar("", color, style);
            } else {
                activeBossBar.setColor(color);
                activeBossBar.setStyle(style);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[PvpMina] Invalid BossBar color/style in config. Using defaults.");
            if (activeBossBar == null) {
                activeBossBar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);
            }
        }
    }

    void rotateMode(boolean firstRun) {
        FileConfiguration cfg = getConfig();
        if (cfg == null) {
            return;
        }
        ConfigurationSection modesSec = cfg.getConfigurationSection("modes");
        if (modesSec == null) {
            return;
        }

        List<String> modes = new ArrayList<>(modesSec.getKeys(false));
        if (modes.isEmpty()) {
            return;
        }

        if (currentMode != null && !firstRun) {
            List<String> endCmds = modesSec.getStringList(currentMode + ".commands-on-end");
            dispatchConsoleCommands(endCmds);
        }

        String newMode;
        if (modes.size() > 1 && currentMode != null) {
            do {
                newMode = modes.get(random.nextInt(modes.size()));
            } while (newMode.equals(currentMode));
        } else {
            newMode = modes.get(random.nextInt(modes.size()));
        }

        currentMode = newMode;
        nextRotationTime = Instant.now().plus(Duration.ofMinutes(intervalMinutes));

        loadModeSettings(newMode);
        dispatchConsoleCommands(modesSec.getStringList(newMode + ".commands-on-start"));

        if (!firstRun) {
            List<String> messages = cfg.getStringList("messages.mode-change");
            if (messages.isEmpty()) {
                String singleMsg = cfg.getString("messages.mode-change");
                if (singleMsg != null) {
                    messages.add(singleMsg);
                }
            }

            String soundName = cfg.getString("messages.change-sound", "ENTITY_WITHER_SPAWN");
            Sound sound;
            try {
                sound = Sound.valueOf(soundName.toUpperCase());
            } catch (IllegalArgumentException e) {
                sound = Sound.ENTITY_WITHER_SPAWN;
            }

            String displayName = getModeDisplayName(newMode);
            for (Player player : Bukkit.getOnlinePlayers()) {
                for (String line : messages) {
                    String processed = line.replace("%mode%", displayName).replace("{nombredelmodo}", displayName);
                    player.sendMessage(parseComponent(processed));
                }
                player.playSound(player.getLocation(), sound, 1f, 1f);
            }
        }
    }

    boolean forceMode(String mode) {
        FileConfiguration cfg = getConfig();
        if (cfg == null || cfg.getConfigurationSection("modes." + mode) == null) {
            return false;
        }

        if (currentMode != null) {
            dispatchConsoleCommands(cfg.getStringList("modes." + currentMode + ".commands-on-end"));
        }

        currentMode = mode;
        nextRotationTime = Instant.now().plus(Duration.ofMinutes(intervalMinutes));
        loadModeSettings(currentMode);
        dispatchConsoleCommands(cfg.getStringList("modes." + currentMode + ".commands-on-start"));
        updateBossBar();
        return true;
    }

    Set<String> getModeKeys() {
        FileConfiguration cfg = getConfig();
        if (cfg == null || cfg.getConfigurationSection("modes") == null) {
            return Set.of();
        }
        return cfg.getConfigurationSection("modes").getKeys(false);
    }

    String getModeDisplayName(String mode) {
        FileConfiguration cfg = getConfig();
        if (cfg == null) {
            return mode;
        }
        return cfg.getString("modes." + mode + ".display-name", mode);
    }

    String getCurrentMode() {
        return currentMode;
    }

    String getTargetWorldName() {
        return targetWorldName;
    }

    Set<Material> getCurrentBlockedItems() {
        return currentBlockedItems;
    }

    BossBar getActiveBossBar() {
        return activeBossBar;
    }

    void updateBossBar() {
        if (activeBossBar == null || nextRotationTime == null) {
            return;
        }

        World world = Bukkit.getWorld(targetWorldName);
        if (world == null) {
            return;
        }

        long totalSeconds = intervalMinutes * 60;
        long remainingSeconds = Duration.between(Instant.now(), nextRotationTime).getSeconds();
        if (remainingSeconds < 0) {
            remainingSeconds = 0;
        }
        float progress = (float) remainingSeconds / totalSeconds;
        activeBossBar.setProgress(Math.max(0f, Math.min(1f, progress)));

        String timeStr = String.format("%02d:%02d:%02d",
                remainingSeconds / 3600,
                (remainingSeconds % 3600) / 60,
                remainingSeconds % 60);

        String titleRaw = getConfig().getString("messages.bossbar-title", "Mode: %mode% (%time_left%)")
                .replace("%mode%", getModeDisplayName(currentMode))
                .replace("%time_left%", timeStr);
        String legacyTitle = LegacyComponentSerializer.legacySection().serialize(parseComponent(titleRaw));
        activeBossBar.setTitle(legacyTitle);

        NamespacedKey key = new NamespacedKey(plugin, "pvpmina_bossbar_hidden");
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean hidden = player.getPersistentDataContainer().has(key, PersistentDataType.BYTE);
            if (!hidden && targetWorldName.equals(player.getWorld().getName())) {
                if (!activeBossBar.getPlayers().contains(player)) {
                    activeBossBar.addPlayer(player);
                }
            } else if (activeBossBar.getPlayers().contains(player)) {
                activeBossBar.removePlayer(player);
            }
        }
    }

    Component parseComponent(String text) {
        return plugin.parseComponent(text);
    }

    void dispatchConsoleCommands(List<String> cmds) {
        if (cmds == null || cmds.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (String cmd : cmds) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
        });
    }

    private void startTask() {
        rotationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Instant now = Instant.now();
            if (nextRotationTime == null || now.isAfter(nextRotationTime)) {
                rotateMode(false);
            }
            updateBossBar();
        }, 20L, 20L);
    }

    private void loadModeSettings(String mode) {
        FileConfiguration cfg = getConfig();
        currentBlockedItems.clear();
        if (cfg == null) {
            return;
        }

        List<String> blockedNames = cfg.getStringList("modes." + mode + ".blocked-items");
        for (String name : blockedNames) {
            try {
                currentBlockedItems.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[PvpMina] Invalid material in blocked-items: " + name);
            }
        }
    }
}
