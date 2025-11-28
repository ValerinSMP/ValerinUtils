package me.Mtynnn.valerinUtils;

import me.Mtynnn.valerinUtils.commands.MenuItemCommand;
import me.Mtynnn.valerinUtils.core.ModuleManager;
import me.Mtynnn.valerinUtils.modules.externalplaceholders.ExternalPlaceholdersModule;
import me.Mtynnn.valerinUtils.modules.menuitem.MenuItemModule;
import me.Mtynnn.valerinUtils.modules.vote40.Vote40Module;
import me.Mtynnn.valerinUtils.commands.ValerinUtilsCommand;
import org.bukkit.plugin.java.JavaPlugin;
import me.Mtynnn.valerinUtils.placeholders.ValerinUtilsExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;

public final class ValerinUtils extends JavaPlugin {

    private static ValerinUtils instance;
    private ModuleManager moduleManager;
    private MenuItemModule menuItemModule;
    private ExternalPlaceholdersModule externalPlaceholdersModule;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();

        moduleManager = new ModuleManager(this);
        menuItemModule = new MenuItemModule(this);
        moduleManager.registerModule(menuItemModule);

        // Módulo de placeholders externos (RoyalEconomy, etc.)
        externalPlaceholdersModule = new ExternalPlaceholdersModule(this);
        moduleManager.registerModule(externalPlaceholdersModule);

        if (Bukkit.getPluginManager().getPlugin("Votifier") != null
                || Bukkit.getPluginManager().getPlugin("VotifierPlus") != null) {
            moduleManager.registerModule(new Vote40Module(this));
            getLogger().info("Votifier/VotifierPlus hooked - Vote40Module registered");
        }

        moduleManager.enableAll();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ValerinUtilsExpansion(this).register();
            getLogger().info("PlaceholderAPI hooked");
        } else {
            getLogger().info("PlaceholderAPI not found");
        }
        // comando admin /valerinutils
        if (getCommand("valerinutils") != null) {
            ValerinUtilsCommand mainCmd = new ValerinUtilsCommand(this);
            getCommand("valerinutils").setExecutor(mainCmd);
            getCommand("valerinutils").setTabCompleter(mainCmd);
        }

        // comando jugador /menuitem
        if (getCommand("menuitem") != null) {
            MenuItemCommand mic = new MenuItemCommand(this, menuItemModule);
            getCommand("menuitem").setExecutor(mic);
            getCommand("menuitem").setTabCompleter(mic);
        }

        getLogger().info("ValerinUtils enabled");
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        getLogger().info("ValerinUtils disabled");
    }

    public static ValerinUtils getInstance() {
        return instance;
    }

    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public MenuItemModule getMenuItemModule() {
        return menuItemModule;
    }

    public ExternalPlaceholdersModule getExternalPlaceholdersModule() {
        return externalPlaceholdersModule;
    }

    public String getMessage(String key) {
        FileConfiguration cfg = getConfig();

        String prefixRaw = cfg.getString("messages.prefix", "&8[&bValerin&fUtils&8]&r ");
        String prefix = ChatColor.translateAlternateColorCodes('&', prefixRaw);

        String raw = cfg.getString("messages." + key, "&cMensaje faltante: " + key);
        raw = raw.replace("%prefix%", prefix);

        return ChatColor.translateAlternateColorCodes('&', raw);
    }
}