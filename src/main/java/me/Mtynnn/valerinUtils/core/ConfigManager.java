package me.mtynnn.valerinutils.core;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ConfigManager {

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
        registerConfig("killrewards", "modules/killrewards.yml");
        registerConfig("tiktok", "modules/tiktok.yml");
        registerConfig("joinquit", "modules/joinquit.yml");
        registerConfig("vote40", "modules/vote40.yml");
        registerConfig("menuitem", "modules/menuitem.yml");

        // 2. Check for migration (Will merge legacy values into the just-created
        // defaults)
        migrateLegacyConfig(); // Legacy config.yml -> new structure

        // 3. Update Settings (Add missing keys for updates)
        updateSettingsConfig();
    }

    private void updateSettingsConfig() {
        FileConfiguration settings = getConfig("settings");
        if (settings == null)
            return;

        boolean changed = false;

        // Verify menuitem messages
        if (!settings.contains("messages.menuitem-cooldown")) {
            settings.set("messages.menuitem-cooldown", "%prefix%&cDebes esperar &e%time%s &cpara volver a usar esto.");
            changed = true;
        }

        // Setup missing menuitem keys if needed
        if (!settings.contains("messages.menuitem-usage")) {
            settings.set("messages.menuitem-usage", "%prefix%&7Uso: &e/menu item <on|off|toggle>");
            changed = true;
        }
        if (!settings.contains("messages.menuitem-on")) {
            settings.set("messages.menuitem-on", "%prefix%&aItem de menú activado.");
            changed = true;
        }
        if (!settings.contains("messages.menuitem-off")) {
            settings.set("messages.menuitem-off", "%prefix%&cItem de menú desactivado.");
            changed = true;
        }
        if (!settings.contains("messages.menuitem-toggled-on")) {
            settings.set("messages.menuitem-toggled-on", "%prefix%&aItem de menú activado.");
            changed = true;
        }
        if (!settings.contains("messages.menuitem-toggled-off")) {
            settings.set("messages.menuitem-toggled-off", "%prefix%&cItem de menú desactivado.");
            changed = true;
        }
        if (!settings.contains("messages.menuitem-slot-occupied")) {
            settings.set("messages.menuitem-slot-occupied", "%prefix%&cEl slot está ocupado, no se puede dar el item.");
            changed = true;
        }

        if (changed) {
            saveConfig("settings");
            plugin.getLogger().info("settings.yml updated with new keys.");
        }
    }

    public FileConfiguration getConfig(String name) {
        return configs.get(name);
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
        // Strategy:
        // 1. Try to get data from 'modules.<name>' (Legacy nested)
        // 2. Try to get data from '<name>' (Legacy root)

        // KillRewards (Was fully nested in modules.killrewards)
        migrateModule(legacy, "modules.killrewards", "modules/killrewards.yml", "killrewards");

        // Vote40 (Was fully nested)
        migrateModule(legacy, "modules.vote40", "modules/vote40.yml", "vote40");

        // TikTok (Split: modules.tiktok.enabled + tiktok root)
        migrateModule(legacy, "modules.tiktok", "modules/tiktok.yml", "tiktok"); // Get enabled
        migrateModule(legacy, "tiktok", "modules/tiktok.yml", "tiktok"); // Get root data

        // JoinQuit (Split)
        migrateModule(legacy, "modules.joinquit", "modules/joinquit.yml", "joinquit");
        migrateModule(legacy, "joinquit", "modules/joinquit.yml", "joinquit");

        // MenuItem (Split)
        migrateModule(legacy, "modules.menuitem", "modules/menuitem.yml", "menuitem");
        migrateModule(legacy, "menuitem", "modules/menuitem.yml", "menuitem");

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
            // It might be just a value, not a section?
            // Usually we are migrating sections.
            // If legacyPath points to a value, we can't easily map it to root without
            // knowing key.
            // But our legacy config structure is always sections for these modules.
        }

        try {
            newConfig.save(newFile);
            // Update the cache so the plugin uses the new values immediately
            configs.put(configKey, newConfig);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to migrate " + legacyPath);
        }
    }
}
