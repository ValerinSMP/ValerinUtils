package me.mtynnn.valerinutils.core;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class ConfigManager {
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");

    private final ValerinUtils plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    private final Map<String, File> files = new HashMap<>();

    public ConfigManager(ValerinUtils plugin) {
        this.plugin = plugin;
        setupModulesFolder();
    }

    private void setupModulesFolder() {
        File modulesFolder = new File(plugin.getDataFolder(), "modules");
        if (!modulesFolder.exists()) {
            modulesFolder.mkdirs();
        }
    }

    public void loadAll() {
        // 1. Initialize all configs (Create defaults from JAR if missing)
        registerConfig("settings", "settings.yml");
        registerConfig("debug", "debug.yml");
        registerConfig("killrewards", "modules/killrewards.yml");
        registerConfig("tiktok", "modules/tiktok.yml");
        registerConfig("joinquit", "modules/joinquit.yml");
        registerConfig("vote40", "modules/vote40.yml");
        registerConfig("menuitem", "modules/menuitem.yml");
        registerConfig("deathmessages", "modules/deathmessages.yml");
        registerConfig("geodes", "modules/geodes.yml");
        registerConfig("votetracking", "modules/votetracking.yml");
        registerConfig("kits", "modules/kits.yml");
        registerConfig("codes", "modules/codes.yml");
        registerConfig("utilities", "modules/utilities.yml");
        registerConfig("pvpmina", "modules/pvpmina.yml");
        // 2. Check for migration (Will merge legacy values into the just-created
        // defaults)
        migrateLegacyConfig(); // Legacy config.yml -> new structure

        // 3. Update Settings (Add missing keys for updates)
        updateSettingsConfig();

        // 4. Update Module Configs (Add missing keys from new versions)
        updateModuleConfigs();
        updateDebugConfig();

        // 5. One-way migration: legacy color codes -> MiniMessage in all configs
        migrateLegacyFormattingToMiniMessage();
    }

    private void updateSettingsConfig() {
        FileConfiguration settings = getConfig("settings");
        if (settings == null)
            return;

        boolean changed = false;

        // Verify menuitem messages
        if (!settings.contains("messages.menuitem-cooldown")) {
            settings.set("messages.menuitem-cooldown",
                    "%prefix%<red>Debes esperar <yellow>%time%s <red>para volver a usar esto.");
            changed = true;
        }

        // Setup missing menuitem keys if needed
        if (!settings.contains("messages.menuitem-usage")) {
            settings.set("messages.menuitem-usage", "%prefix%<gray>Uso: <yellow>/menu item <on|off|toggle>");
            changed = true;
        }
        if (!settings.contains("messages.menuitem-on")) {
            settings.set("messages.menuitem-on", "%prefix%<green>Item de menú activado.");
            changed = true;
        }
        if (!settings.contains("messages.menuitem-off")) {
            settings.set("messages.menuitem-off", "%prefix%<red>Item de menú desactivado.");
            changed = true;
        }
        if (!settings.contains("messages.menuitem-toggled-on")) {
            settings.set("messages.menuitem-toggled-on", "%prefix%<green>Item de menú activado.");
            changed = true;
        }
        if (!settings.contains("messages.menuitem-toggled-off")) {
            settings.set("messages.menuitem-toggled-off", "%prefix%<red>Item de menú desactivado.");
            changed = true;
        }
        if (!settings.contains("messages.menuitem-slot-occupied")) {
            settings.set("messages.menuitem-slot-occupied",
                    "%prefix%<red>El slot está ocupado, no se puede dar el item.");
            changed = true;
        }

        if (changed) {
            saveConfig("settings");
            plugin.getLogger().info("settings.yml updated with new keys.");
        }
    }

    /**
     * Auto-update module configs with new keys from defaults.
     * This ensures users coming from older versions get new features.
     */
    private void updateModuleConfigs() {
        // JoinQuit - Add MOTD section if missing
        updateJoinQuitConfig();
        updateKitsConfig();
        updateUtilitiesConfig();
        updateDeathMessagesConfig();
    }

    private void updateJoinQuitConfig() {
        FileConfiguration config = getConfig("joinquit");
        if (config == null)
            return;

        boolean changed = false;

        // Add MOTD section keys individually (so partial configs get updated too)
        if (!config.contains("join.motd.enabled")) {
            config.set("join.motd.enabled", false);
            changed = true;
        }
        if (!config.contains("join.motd.clear-chat")) {
            config.set("join.motd.clear-chat", true);
            changed = true;
        }
        if (!config.contains("join.motd.lines")) {
            config.set("join.motd.lines", java.util.Arrays.asList(
                    "",
                    "<gold>╔══════════════════════════════════════╗",
                    "<gold>║  <aqua><bold>BIENVENIDO A TU SERVIDOR  <gold>║",
                    "<gold>╠══════════════════════════════════════╣",
                    "<gold>║  <gray>❯ <white>Jugadores Online: <green>%server_online%/%server_max_players%",
                    "<gold>║  <gray>❯ <white>Tu Rango: <green>%luckperms_prefix%",
                    "<gold>╚══════════════════════════════════════╝",
                    ""));
            changed = true;
        }

        if (changed) {
            saveConfig("joinquit");
            plugin.getLogger().info("[JoinQuit] Config updated with new keys.");
        }
    }

    private void updateKitsConfig() {
        FileConfiguration config = getConfig("kits");
        if (config == null)
            return;

        boolean changed = false;

        if (!config.contains("settings.debug_command_spam")) {
            config.set("settings.debug_command_spam", false);
            changed = true;
        }
        if (!config.contains("settings.respawn_kit_only_on_death")) {
            config.set("settings.respawn_kit_only_on_death", true);
            changed = true;
        }
        if (!config.contains("settings.respawn_kit_overwrite")) {
            config.set("settings.respawn_kit_overwrite", false);
            changed = true;
        }
        if (!config.contains("settings.respawn_kit_disabled_worlds")) {
            config.set("settings.respawn_kit_disabled_worlds", java.util.Collections.emptyList());
            changed = true;
        }

        if (changed) {
            saveConfig("kits");
            plugin.getLogger().info("[Kits] Config updated with new keys.");
        }
    }

    private void updateUtilitiesConfig() {
        FileConfiguration config = getConfig("utilities");
        if (config == null)
            return;

        boolean changed = false;

        if (!config.contains("commands.disposal.enabled")) {
            config.set("commands.disposal.enabled", true);
            changed = true;
        }
        if (!config.contains("commands.disposal.permission")) {
            config.set("commands.disposal.permission", "valerinutils.utility.disposal");
            changed = true;
        }
        if (!config.contains("messages.disposal-title")) {
            config.set("messages.disposal-title", "<dark_gray>Basurero");
            changed = true;
        }
        if (!config.contains("sounds.disposal")) {
            config.set("sounds.disposal", "BLOCK_BARREL_OPEN");
            changed = true;
        }

        if (changed) {
            saveConfig("utilities");
            plugin.getLogger().info("[Utility] Config updated with new keys.");
        }
    }

    private void updateDeathMessagesConfig() {
        FileConfiguration config = getConfig("deathmessages");
        if (config == null)
            return;

        boolean changed = false;

        if (!config.contains("spawn.first-join.enabled")) {
            config.set("spawn.first-join.enabled", false);
            changed = true;
        }
        if (!config.contains("spawn.first-join.location")) {
            config.set("spawn.first-join.location", "world_lobby;-4;141;107;0;0");
            changed = true;
        }
        if (!config.contains("spawn.first-join.delay-ticks")) {
            config.set("spawn.first-join.delay-ticks", 1);
            changed = true;
        }

        if (!config.contains("spawn.on-death.enabled")) {
            config.set("spawn.on-death.enabled", false);
            changed = true;
        }
        if (!config.contains("spawn.on-death.location")) {
            config.set("spawn.on-death.location", "world_lobby;-4;141;107;0;0");
            changed = true;
        }

        if (changed) {
            saveConfig("deathmessages");
            plugin.getLogger().info("[DeathMessages] Config updated with new keys.");
        }
    }

    public FileConfiguration getConfig(String name) {
        return configs.get(name);
    }

    public void reloadConfigs() {
        for (String id : files.keySet()) {
            File file = files.get(id);
            if (file.exists()) {
                configs.put(id, YamlConfiguration.loadConfiguration(file));
            }
        }
        // Re-run auto-updater after reload to ensure defaults are there
        updateSettingsConfig();
        updateModuleConfigs();
        updateDebugConfig();
        migrateLegacyFormattingToMiniMessage();
        plugin.getLogger().info("All configuration files reloaded.");
    }

    private void updateDebugConfig() {
        FileConfiguration debug = getConfig("debug");
        if (debug == null) {
            return;
        }

        boolean changed = false;
        String[] modules = {
                "menuitem", "externalplaceholders", "joinquit", "vote40", "votetracking",
                "killrewards", "codes", "deathmessages", "geodes", "kits", "utility", "pvpmina"
        };
        for (String moduleId : modules) {
            String path = "modules." + moduleId + ".enabled";
            if (!debug.contains(path)) {
                debug.set(path, false);
                changed = true;
            }
        }

        if (changed) {
            saveConfig("debug");
            plugin.getLogger().info("debug.yml updated with module debug keys.");
        }
    }

    public void saveConfig(String name) {
        if (!configs.containsKey(name) || !files.containsKey(name))
            return;
        try {
            configs.get(name).save(files.get(name));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config: " + name, e);
        }
    }

    private void registerConfig(String id, String path) {
        File file = new File(plugin.getDataFolder(), path);

        if (!file.exists()) {
            try {
                if (plugin.getResource(path) != null) {
                    plugin.saveResource(path, false);
                } else {
                    file.createNewFile();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        files.put(id, file);
        configs.put(id, YamlConfiguration.loadConfiguration(file));
    }

    private void migrateLegacyConfig() {
        File oldConfig = new File(plugin.getDataFolder(), "config.yml");
        if (!oldConfig.exists())
            return; // No migration needed or already migrated

        plugin.getLogger().info("Detected legacy config.yml. Starting migration to ValerinUtils 2.0 structure...");
        FileConfiguration legacy = YamlConfiguration.loadConfiguration(oldConfig);

        // Migrate Settings
        File settingsFile = new File(plugin.getDataFolder(), "settings.yml");
        FileConfiguration settings = YamlConfiguration.loadConfiguration(settingsFile);
        settings.set("debug", legacy.getBoolean("debug", false));
        settings.set("database.type", "sqlite"); // Default
        try {
            settings.save(settingsFile);
            // Refresh loaded config
            configs.put("settings", settings);
        } catch (Exception e) {
        }

        // Migrate Modules
        migrateModule(legacy, "modules.killrewards", "modules/killrewards.yml", "killrewards");
        migrateModule(legacy, "modules.vote40", "modules/vote40.yml", "vote40");
        migrateModule(legacy, "modules.tiktok", "modules/tiktok.yml", "tiktok");
        migrateModule(legacy, "tiktok", "modules/tiktok.yml", "tiktok");
        migrateModule(legacy, "modules.joinquit", "modules/joinquit.yml", "joinquit");
        migrateModule(legacy, "joinquit", "modules/joinquit.yml", "joinquit");
        migrateModule(legacy, "modules.menuitem", "modules/menuitem.yml", "menuitem");
        migrateModule(legacy, "menuitem", "modules/menuitem.yml", "menuitem");
        migrateModule(legacy, "votetracking", "modules/votetracking.yml", "votetracking");
        migrateModule(legacy, "modules.votetracking", "modules/votetracking.yml", "votetracking");

        // Rename old config
        File backup = new File(plugin.getDataFolder(), "config.yml.old");
        oldConfig.renameTo(backup);
        plugin.getLogger().info("Migration complete. config.yml renamed to config.yml.old");
    }

    private void migrateModule(FileConfiguration legacy, String legacyPath, String newPath, String configKey) {
        if (!legacy.contains(legacyPath))
            return;

        File newFile = new File(plugin.getDataFolder(), newPath);
        FileConfiguration newConfig = YamlConfiguration.loadConfiguration(newFile);

        ConfigurationSection section = legacy.getConfigurationSection(legacyPath);
        if (section != null) {
            for (String key : section.getKeys(true)) {
                newConfig.set(key, section.get(key));
            }
        } else {
            Object val = legacy.get(legacyPath);
            if (val instanceof Boolean) {
                newConfig.set("enabled", val);
            }
        }

        try {
            newConfig.save(newFile);
            configs.put(configKey, newConfig);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to migrate " + legacyPath);
        }
    }

    private void migrateLegacyFormattingToMiniMessage() {
        for (String id : configs.keySet()) {
            FileConfiguration cfg = configs.get(id);
            if (cfg == null) {
                continue;
            }

            boolean changed = migrateSectionStrings(cfg, cfg);
            if (changed) {
                saveConfig(id);
                plugin.getLogger().info("[" + id + "] migrated legacy formatting to MiniMessage.");
            }
        }
    }

    private boolean migrateSectionStrings(FileConfiguration root, ConfigurationSection section) {
        boolean changed = false;

        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                if (migrateSectionStrings(root, child)) {
                    changed = true;
                }
                continue;
            }

            String path = section.getCurrentPath() == null ? key : section.getCurrentPath() + "." + key;
            if (value instanceof String text) {
                String converted = legacyToMiniMessage(text);
                if (!converted.equals(text)) {
                    root.set(path, converted);
                    changed = true;
                }
                continue;
            }

            if (value instanceof List<?> list && !list.isEmpty()) {
                boolean listChanged = false;
                List<Object> migrated = new ArrayList<>(list.size());
                for (Object obj : list) {
                    if (obj instanceof String text) {
                        String converted = legacyToMiniMessage(text);
                        migrated.add(converted);
                        if (!converted.equals(text)) {
                            listChanged = true;
                        }
                    } else {
                        migrated.add(obj);
                    }
                }

                if (listChanged) {
                    root.set(path, migrated);
                    changed = true;
                }
            }
        }

        return changed;
    }

    private String legacyToMiniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String input = text.replace('§', '&');
        if (input.indexOf('&') < 0) {
            return text;
        }

        String withHex = LEGACY_HEX_PATTERN.matcher(input).replaceAll("<color:#$1>");
        StringBuilder output = new StringBuilder(withHex.length() + 16);

        for (int index = 0; index < withHex.length(); index++) {
            char ch = withHex.charAt(index);
            if (ch == '&' && index + 1 < withHex.length()) {
                char code = Character.toLowerCase(withHex.charAt(index + 1));
                String replacement = switch (code) {
                    case '0' -> "<black>";
                    case '1' -> "<dark_blue>";
                    case '2' -> "<dark_green>";
                    case '3' -> "<dark_aqua>";
                    case '4' -> "<dark_red>";
                    case '5' -> "<dark_purple>";
                    case '6' -> "<gold>";
                    case '7' -> "<gray>";
                    case '8' -> "<dark_gray>";
                    case '9' -> "<blue>";
                    case 'a' -> "<green>";
                    case 'b' -> "<aqua>";
                    case 'c' -> "<red>";
                    case 'd' -> "<light_purple>";
                    case 'e' -> "<yellow>";
                    case 'f' -> "<white>";
                    case 'k' -> "<obfuscated>";
                    case 'l' -> "<bold>";
                    case 'm' -> "<strikethrough>";
                    case 'n' -> "<underlined>";
                    case 'o' -> "<italic>";
                    case 'r' -> "<reset>";
                    default -> null;
                };

                if (replacement != null) {
                    output.append(replacement);
                    index++;
                    continue;
                }
            }
            output.append(ch);
        }

        return output.toString();
    }

}
