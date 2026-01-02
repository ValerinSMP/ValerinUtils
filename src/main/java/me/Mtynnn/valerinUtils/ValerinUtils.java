package me.Mtynnn.valerinUtils;

import me.Mtynnn.valerinUtils.commands.MenuItemCommand;
import me.Mtynnn.valerinUtils.core.ModuleManager;
import me.Mtynnn.valerinUtils.modules.externalplaceholders.ExternalPlaceholdersModule;
import me.Mtynnn.valerinUtils.modules.menuitem.MenuItemModule;
import me.Mtynnn.valerinUtils.modules.vote40.Vote40Module;
import me.Mtynnn.valerinUtils.modules.joinquit.JoinQuitModule;
import me.Mtynnn.valerinUtils.modules.ecoskillsrecount.EcoSkillsRecountModule;
import me.Mtynnn.valerinUtils.commands.ValerinUtilsCommand;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.plugin.java.JavaPlugin;
import me.Mtynnn.valerinUtils.placeholders.ValerinUtilsExpansion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;

public final class ValerinUtils extends JavaPlugin {

    private static ValerinUtils instance;
    private ModuleManager moduleManager;
    private MenuItemModule menuItemModule;
    private ExternalPlaceholdersModule externalPlaceholdersModule;
    private JoinQuitModule joinQuitModule;
    private EcoSkillsRecountModule ecoSkillsRecountModule;

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

        // Módulo EcoSkills Recount
        if (Bukkit.getPluginManager().getPlugin("EcoSkills") != null) {
            ecoSkillsRecountModule = new EcoSkillsRecountModule(this);
            moduleManager.registerModule(ecoSkillsRecountModule);
            getLogger().info("EcoSkills hooked - EcoSkillsRecountModule registered");
        }

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

    public JoinQuitModule getJoinQuitModule() {
        return joinQuitModule;
    }

    public EcoSkillsRecountModule getEcoSkillsRecountModule() {
        return ecoSkillsRecountModule;
    }

    public String getMessage(String key) {
        FileConfiguration cfg = getConfig();

        String prefixRaw = cfg.getString("messages.prefix", "&8[&bValerin&fUtils&8]&r ");
        String prefix = ChatColor.translateAlternateColorCodes('&', prefixRaw);

        String raw = cfg.getString("messages." + key, "&cMensaje faltante: " + key);
        raw = raw.replace("%prefix%", prefix);

        return translateColors(raw);
    }

    public String translateColors(String message) {
        if (message == null)
            return "";
        // Soporte para &#RRGGBB
        Pattern pattern = Pattern.compile("&#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(message);
        while (matcher.find()) {
            String color = message.substring(matcher.start(), matcher.end());
            // net.md_5.bungee.api.ChatColor es accesible en Paper/Spigot moderno
            try {
                message = message.replace(color, net.md_5.bungee.api.ChatColor.of(color.substring(1)).toString());
            } catch (Exception e) {
                // Falladback si no es compatible
            }
            matcher = pattern.matcher(message);
        }
        return ChatColor.translateAlternateColorCodes('&', message);
    }

    public Component parseComponent(String text) {
        if (text == null)
            return Component.empty();

        // Estrategia Híbrida:
        // 1. Convertir códigos de color legacy (&c, &#RRGGBB) a tags MiniMessage
        String processed = legacyToMiniMessage(text);

        // 2. Parsear con MiniMessage
        return MiniMessage.miniMessage().deserialize(processed);
    }

    private String legacyToMiniMessage(String text) {
        if (text == null)
            return "";

        // Reemplazar &#RRGGBB a <#RRGGBB>
        // Patrón para &#......
        text = text.replaceAll("&#([0-9a-fA-F]{6})", "<#$1>");

        // Reemplazar &x a <color>
        // Mapeo básico de colores legacy
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

        // Decoraciones
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