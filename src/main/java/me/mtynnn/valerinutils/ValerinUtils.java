package me.mtynnn.valerinutils;

import me.mtynnn.valerinutils.commands.MenuItemCommand;
import me.mtynnn.valerinutils.core.ModuleManager;
import me.mtynnn.valerinutils.modules.externalplaceholders.ExternalPlaceholdersModule;
import me.mtynnn.valerinutils.modules.menuitem.MenuItemModule;
import me.mtynnn.valerinutils.modules.vote40.Vote40Module;
import me.mtynnn.valerinutils.modules.joinquit.JoinQuitModule;
import me.mtynnn.valerinutils.modules.killrewards.KillRewardsModule;
import me.mtynnn.valerinutils.commands.ValerinUtilsCommand;
import me.mtynnn.valerinutils.placeholders.ValerinUtilsExpansion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;

public final class ValerinUtils extends JavaPlugin {

    private static ValerinUtils instance;
    private ModuleManager moduleManager;
    private MenuItemModule menuItemModule;
    private ExternalPlaceholdersModule externalPlaceholdersModule;
    private JoinQuitModule joinQuitModule;
    private KillRewardsModule killRewardsModule;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        updateConfig();

        moduleManager = new ModuleManager(this);
        menuItemModule = new MenuItemModule(this);
        moduleManager.registerModule(menuItemModule);

        // Módulo de placeholders externos (RoyalEconomy, etc.)
        externalPlaceholdersModule = new ExternalPlaceholdersModule(this);
        moduleManager.registerModule(externalPlaceholdersModule);

        // Módulo JoinQuit
        joinQuitModule = new JoinQuitModule(this);
        moduleManager.registerModule(joinQuitModule);

        if (Bukkit.getPluginManager().getPlugin("Votifier") != null
                || Bukkit.getPluginManager().getPlugin("VotifierPlus") != null) {
            Vote40Module voteModule = new Vote40Module(this);
            moduleManager.registerModule(voteModule);
            getLogger().info("Votifier/VotifierPlus hooked - Vote40Module registered");
        }

        killRewardsModule = new KillRewardsModule(this);
        moduleManager.registerModule(killRewardsModule);

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

    public JoinQuitModule getJoinQuitModule() {
        return joinQuitModule;
    }

    public KillRewardsModule getKillRewardsModule() {
        return killRewardsModule;
    }

    public String getMessage(String key) {
        FileConfiguration cfg = getConfig();

        String prefixRaw = cfg.getString("messages.prefix", "&8[&bValerin&fUtils&8]&r ");
        String prefix = ChatColor.translateAlternateColorCodes('&', prefixRaw);

        // Check if list first
        if (cfg.isList("messages." + key)) {
            List<String> list = cfg.getStringList("messages." + key);
            if (list.isEmpty())
                return "";
            // Return first line if single String expected
            return translateColors(list.get(0).replace("%prefix%", prefix));
        }

        String raw = cfg.getString("messages." + key, "&cMensaje faltante: " + key);
        raw = raw.replace("%prefix%", prefix);

        return translateColors(raw);
    }

    public List<String> getMessageList(String key) {
        FileConfiguration cfg = getConfig();

        String prefixRaw = cfg.getString("messages.prefix", "&8[&bValerin&fUtils&8]&r ");
        String prefix = ChatColor.translateAlternateColorCodes('&', prefixRaw);

        if (cfg.isList("messages." + key)) {
            List<String> rawList = cfg.getStringList("messages." + key);
            List<String> processed = new ArrayList<>();
            for (String line : rawList) {
                line = line.replace("%prefix%", prefix);
                processed.add(translateColors(line));
            }
            return processed;
        } else {
            // Fallback for single string
            String raw = cfg.getString("messages." + key);
            if (raw == null) {
                return Collections.singletonList(translateColors("&cMensaje faltante: " + key));
            }
            raw = raw.replace("%prefix%", prefix);
            return Collections.singletonList(translateColors(raw));
        }
    }

    public String translateColors(String message) {
        if (message == null)
            return "";
        // Soporte para &#RRGGBB
        Pattern pattern = Pattern.compile("&#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String color = message.substring(matcher.start(), matcher.end());
            try {
                message = message.replace(color, net.md_5.bungee.api.ChatColor.of(color.substring(1)).toString());
            } catch (Exception e) {
                // Fallback
            }
            matcher = pattern.matcher(message);
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public Component parseComponent(String text) {
        if (text == null)
            return Component.empty();

        String processed = legacyToMiniMessage(text);
        return MiniMessage.miniMessage().deserialize(processed);
    }

    private String legacyToMiniMessage(String text) {
        if (text == null)
            return "";

        text = text.replaceAll("&#([0-9a-fA-F]{6})", "<#$1>");

        text = text.replace("&0", "<black>");
        text = text.replace("&1", "<dark_blue>");
        text = text.replace("&2", "<dark_green>");
        text = text.replace("&3", "<dark_aqua>");
        text = text.replace("&4", "<dark_red>");
        text = text.replace("&5", "<dark_purple>");
        text = text.replace("&6", "<gold>");
        text = text.replace("&7", "<gray>");
        text = text.replace("&8", "<dark_gray>");
        text = text.replace("&9", "<blue>");
        text = text.replace("&a", "<green>");
        text = text.replace("&b", "<aqua>");
        text = text.replace("&c", "<red>");
        text = text.replace("&d", "<light_purple>");
        text = text.replace("&e", "<yellow>");
        text = text.replace("&f", "<white>");

        text = text.replace("&k", "<obfuscated>");
        text = text.replace("&l", "<bold>");
        text = text.replace("&m", "<strikethrough>");
        text = text.replace("&n", "<underlined>");
        text = text.replace("&o", "<italic>");
        text = text.replace("&r", "<reset>");

        return text;
    }

    public boolean isDebug() {
        return getConfig().getBoolean("debug", false);
    }

    public void updateConfig() {
        getConfig().options().copyDefaults(true);
        saveConfig();
        if (isDebug()) {
            getLogger().info("Config updated merged with defaults.");
        }
    }
}
