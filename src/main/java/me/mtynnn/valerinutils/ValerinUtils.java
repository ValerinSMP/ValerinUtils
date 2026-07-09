package me.mtynnn.valerinutils;

import me.mtynnn.valerinutils.commands.MenuItemCommand;
import me.mtynnn.valerinutils.commands.ValerinUtilsCommand;
import me.mtynnn.valerinutils.core.CommandHousekeeper;
import me.mtynnn.valerinutils.core.CommandRegistry;
import me.mtynnn.valerinutils.core.ConfigManager;
import me.mtynnn.valerinutils.core.DatabaseManager;
import me.mtynnn.valerinutils.core.ModuleManager;
import me.mtynnn.valerinutils.core.MessageService;
import me.mtynnn.valerinutils.core.PlayerData;
import me.mtynnn.valerinutils.core.PlayerDataManager;
import me.mtynnn.valerinutils.modules.killrewards.KillRewardsModule;
import me.mtynnn.valerinutils.modules.menuitem.MenuItemModule;
import me.mtynnn.valerinutils.modules.codes.CodesModule;
import me.mtynnn.valerinutils.modules.vouchers.VouchersModule;
import me.mtynnn.valerinutils.modules.utility.UtilityModule;
import me.mtynnn.valerinutils.modules.deathmessages.DeathMessagesModule;

import me.mtynnn.valerinutils.modules.grace.GraceModule;
import me.mtynnn.valerinutils.modules.itemsign.ItemSignModule;
import me.mtynnn.valerinutils.placeholders.ValerinUtilsExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ValerinUtils extends JavaPlugin implements Listener {
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern LEGACY_BUNGEE_HEX_AMPERSAND = Pattern.compile("(?i)&x(&[0-9a-f]){6}");
    private static final Pattern LEGACY_BUNGEE_HEX_SECTION = Pattern.compile("(?i)§x(§[0-9a-f]){6}");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)\\b((?:https?://)?(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,}(?:/[\\p{Alnum}\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?)(?<![.,;:!?])");

    private static ValerinUtils instance;
    private ModuleManager moduleManager;
    private CommandRegistry commandRegistry;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private MessageService messageService;

    private MenuItemModule menuItemModule;
    private KillRewardsModule killRewardsModule;
    private CodesModule codesModule;
    private DeathMessagesModule deathMessagesModule;
    private ItemSignModule itemSignModule;
    private UtilityModule utilityModule;
    private VouchersModule vouchersModule;
    private GraceModule graceModule;
    private ValerinUtilsExpansion placeholderExpansion;

    private PlayerDataManager playerDataManager;
    private CommandHousekeeper commandHousekeeper;
    private me.mtynnn.valerinutils.core.EarningsTracker earningsTracker;

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Enforce cleanup if MenuItem module is disabled in config
        // This handles cases where items persist from previous sessions when module was
        // enabled
        if (menuItemModule != null) {
            FileConfiguration config = configManager.getConfig("menuitem");
            boolean enabled = true;
            if (config != null) {
                enabled = config.getBoolean("enabled", true);
            }
            if (!enabled) {
                menuItemModule.clearMenuItem(event.getPlayer());
            }
        }
    }

    // Performance: cached values
    private String cachedGlobalPrefix = null;

    @Override
    public void onEnable() {
        instance = this;

        // 1. Initialize Managers
        configManager = new ConfigManager(this);
        configManager.loadAll();
        messageService = new MessageService(this);
        commandRegistry = new CommandRegistry(this);

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        playerDataManager = new PlayerDataManager(this);
        commandHousekeeper = new CommandHousekeeper(this);

        // 2. Data Migration (v1 -> v2)
        performDataMigration();

        // 3. Reload Support: Load data for online players (Pre-load before modules)
        playerDataManager.reloadOnlinePlayers();

        // 4. Register Events
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(playerDataManager, this);

        if (Bukkit.getPluginManager().isPluginEnabled("RoyaleEconomy")) {
            earningsTracker = new me.mtynnn.valerinutils.core.EarningsTracker(this);
            earningsTracker.start();
        }

        // 4. Initialize Modules
        moduleManager = new ModuleManager(this);

        menuItemModule = new MenuItemModule(this);
        moduleManager.registerModule(menuItemModule);

        killRewardsModule = new KillRewardsModule(this);
        moduleManager.registerModule(killRewardsModule);

        codesModule = new CodesModule(this);
        moduleManager.registerModule(codesModule);

        deathMessagesModule = new DeathMessagesModule(this);
        moduleManager.registerModule(deathMessagesModule);

        itemSignModule = new ItemSignModule(this);
        moduleManager.registerModule(itemSignModule);

        utilityModule = new UtilityModule(this);
        moduleManager.registerModule(utilityModule);

        vouchersModule = new VouchersModule(this);
        moduleManager.registerModule(vouchersModule);

        graceModule = new GraceModule(this);
        moduleManager.registerModule(graceModule);

        commandHousekeeper.reinstateAll();
        moduleManager.enableAll();

        // Sync the Brigadier dispatcher immediately so stale BukkitCommandNodes
        // are rebuilt before any player can issue a command after a PlugManX reload.
        commandHousekeeper.syncNow();

        commandHousekeeper.schedule();

        // Cleanup periodico de cache: elimina entradas de jugadores ya desconectados
        // Previene acumulacion por crashes o QuitEvent perdidos tras PlugMan reload
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            playerDataManager.removeStaleEntries();
        }, 6000L, 6000L);

        // 5. Hooks & Commands
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new ValerinUtilsExpansion(this);
            placeholderExpansion.register();
        }

        if (getCommand("valerinutils") != null) {
            ValerinUtilsCommand mainCmd = new ValerinUtilsCommand(this);
            getCommand("valerinutils").setExecutor(mainCmd);
            getCommand("valerinutils").setTabCompleter(mainCmd);
        }

        if (getCommand("menuitem") != null) {
            MenuItemCommand mic = new MenuItemCommand(this, menuItemModule);
            getCommand("menuitem").setExecutor(mic);
            getCommand("menuitem").setTabCompleter(mic);
        }

        // 6. Startup Banner
        printStartupBanner();

        // 8. Cleanup ghost MenuItems if module is disabled (Reload fix)
        if (!moduleManager.isModuleEnabled("menuitem")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                menuItemModule.clearMenuItem(p);
            }
        }
    }

    private void printStartupBanner() {
        String version = getPluginMeta().getVersion();

        // Check hooks
        boolean papiHooked = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        boolean luckPermsHooked = Bukkit.getPluginManager().getPlugin("LuckPerms") != null;
        boolean vaultHooked = Bukkit.getPluginManager().getPlugin("Vault") != null;

        // Check enabled modules
        boolean menuItemEnabled = moduleManager.isModuleEnabled("menuitem");
        boolean killRewardsEnabled = moduleManager.isModuleEnabled("killrewards");
        boolean codesEnabled = moduleManager.isModuleEnabled("codes");
        boolean itemSignEnabled = moduleManager.isModuleEnabled("itemsign");
        boolean utilityEnabled = moduleManager.isModuleEnabled("utility");
        boolean vouchersEnabled = moduleManager.isModuleEnabled("vouchers");

        getLogger().info("");
        getLogger().info("  ValerinUtils v" + version);
        getLogger().info("  Developed by mtynnn");
        getLogger().info("");
        getLogger().info("  Hooks: PAPI " + (papiHooked ? "✔" : "✘")
                + " | LuckPerms " + (luckPermsHooked ? "✔" : "✘")
                + " | Vault " + (vaultHooked ? "✔" : "✘"));
        getLogger().info("  Modules: MenuItem " + (menuItemEnabled ? "✔" : "✘")
                + " | KillRewards " + (killRewardsEnabled ? "✔" : "✘")
                + " | Codes " + (codesEnabled ? "✔" : "✘")
                + " | ItemSign " + (itemSignEnabled ? "✔" : "✘")
                + " | Utility " + (utilityEnabled ? "✔" : "✘")
                + " | Vouchers " + (vouchersEnabled ? "✔" : "✘"));
        getLogger().info("");
        getLogger().info("  ValerinUtils has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (earningsTracker != null) {
            earningsTracker.stop();
        }
        playerDataManager.saveAllAndClear();

        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        commandHousekeeper.clearBindings();

        if (placeholderExpansion != null) {
            try {
                placeholderExpansion.unregister();
            } catch (Throwable ignored) {
            }
            placeholderExpansion = null;
        }

        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
        getLogger().info("ValerinUtils disabled");
    }

    public CommandHousekeeper getCommandHousekeeper() {
        return commandHousekeeper;
    }

    public static ValerinUtils getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public MessageService messages() {
        return messageService;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerDataManager.get(uuid);
    }

    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    // --- Migration Logic (Data) ---

    private void performDataMigration() {
        // 1. MenuItem Data
        File menuFile = new File(getDataFolder(), "menuitem_data.yml");
        if (menuFile.exists()) {
            getLogger().info("Migrating menuitem_data.yml to database...");
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(menuFile);
            List<String> disabled = cfg.getStringList("menuitem-disabled");
            int count = 0;
            for (String uuidStr : disabled) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String sql = "INSERT INTO player_data (uuid, menu_disabled) VALUES (?, true) " +
                            "ON CONFLICT(uuid) DO UPDATE SET menu_disabled=true";
                    try (PreparedStatement ps = databaseManager.getConnection().prepareStatement(sql)) {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                        count++;
                    }
                } catch (Exception e) {
                }
            }
            getLogger().info("Migrated " + count + " MenuItem records.");
            menuFile.renameTo(new File(getDataFolder(), "menuitem_data.yml.bak"));
        }

        // 2. RoyaleEconomy Pay Data
        File royalFile = new File(getDataFolder(), "royaleconomy_data.yml");
        if (royalFile.exists()) {
            getLogger().info("Migrating royaleconomy_data.yml to database...");
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(royalFile);
            List<String> disabled = cfg.getStringList("royaleconomy-pay-disabled");
            int count = 0;
            for (String uuidStr : disabled) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String sql = "INSERT INTO player_data (uuid, royal_pay_disabled) VALUES (?, true) " +
                            "ON CONFLICT(uuid) DO UPDATE SET royal_pay_disabled=true";
                    try (PreparedStatement ps = databaseManager.getConnection().prepareStatement(sql)) {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                        count++;
                    }
                } catch (Exception e) {
                }
            }
            getLogger().info("Migrated " + count + " RoyaleEconomy records.");
            royalFile.renameTo(new File(getDataFolder(), "royaleconomy_data.yml.bak"));
        }

        // 2. KillRewards Data
        File killFile = new File(getDataFolder(), "killrewards_data.yml");
        if (killFile.exists()) {
            getLogger().info("Migrating killrewards_data.yml to database...");
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(killFile);

            // Stats
            if (cfg.contains("stats")) {
                for (String key : cfg.getConfigurationSection("stats").getKeys(false)) {
                    List<Integer> vals = cfg.getIntegerList("stats." + key);
                    if (vals.size() >= 2) {
                        try {
                            String sql = "INSERT INTO player_data (uuid, kills, deaths) VALUES (?, ?, ?) " +
                                    "ON CONFLICT(uuid) DO UPDATE SET kills=?, deaths=?";
                            try (PreparedStatement ps = databaseManager.getConnection().prepareStatement(sql)) {
                                ps.setString(1, key);
                                ps.setInt(2, vals.get(0));
                                ps.setInt(3, vals.get(1));
                                ps.setInt(4, vals.get(0));
                                ps.setInt(5, vals.get(1));
                                ps.executeUpdate();
                            }
                        } catch (Exception e) {
                        }
                    }
                }
            }

            // Daily limits
            if (cfg.contains("daily")) {
                for (String key : cfg.getConfigurationSection("daily").getKeys(false)) {
                    long day = cfg.getLong("daily." + key + ".day");
                    int count = cfg.getInt("daily." + key + ".count");
                    try {
                        String sql = "INSERT INTO player_data (uuid, last_daily_reset, daily_kills) VALUES (?, ?, ?) " +
                                "ON CONFLICT(uuid) DO UPDATE SET last_daily_reset=?, daily_kills=?";
                        try (PreparedStatement ps = databaseManager.getConnection().prepareStatement(sql)) {
                            ps.setString(1, key);
                            ps.setLong(2, day);
                            ps.setInt(3, count);
                            ps.setLong(4, day);
                            ps.setInt(5, count);
                            ps.executeUpdate();
                        }
                    } catch (Exception e) {
                    }
                }
            }
            killFile.renameTo(new File(getDataFolder(), "killrewards_data.yml.bak"));
            getLogger().info("KillRewards migration complete.");
        }
    }

    // --- Getters for Modules ---
    public ModuleManager getModuleManager() {
        return moduleManager;
    }

    public CommandRegistry getCommandRegistry() {
        return commandRegistry;
    }

    public MenuItemModule getMenuItemModule() {
        return menuItemModule;
    }

    public KillRewardsModule getKillRewardsModule() {
        return killRewardsModule;
    }

    public CodesModule getCodesModule() {
        return codesModule;
    }

    public DeathMessagesModule getDeathMessagesModule() {
        return deathMessagesModule;
    }

    // --- Debug flags (per-module) ---
    public boolean isModuleDebugEnabled(String moduleId) {
        FileConfiguration dbg = configManager.getConfig("debug");
        if (dbg == null || moduleId == null || moduleId.isBlank()) {
            return false;
        }
        return dbg.getBoolean("modules." + moduleId.toLowerCase() + ".enabled", false);
    }

    public boolean toggleModuleDebug(String moduleId) {
        boolean next = !isModuleDebugEnabled(moduleId);
        setModuleDebugEnabled(moduleId, next);
        return next;
    }

    public void setModuleDebugEnabled(String moduleId, boolean enabled) {
        FileConfiguration dbg = configManager.getConfig("debug");
        if (dbg == null || moduleId == null || moduleId.isBlank()) {
            return;
        }
        dbg.set("modules." + moduleId.toLowerCase() + ".enabled", enabled);
        configManager.saveConfig("debug");
    }

    public void debug(String moduleId, String message) {
        if (!isModuleDebugEnabled(moduleId) || message == null || message.isBlank()) {
            return;
        }
        getLogger().info("[Debug][" + moduleId.toLowerCase() + "] " + message);
    }

    // --- Message Utils (Legacy Compat) ---
    public String getMessage(String key) {
        if (messageService == null) {
            return "";
        }
        return messageService.settings(key, "<red>Mensaje faltante: " + key);
    }

    public List<String> getMessageList(String key) {
        if (messageService == null) {
            return Collections.emptyList();
        }
        return messageService.settingsList(key);
    }

    public String translateColors(String message) {
        if (message == null)
            return "";

        return LegacyComponentSerializer.legacySection().serialize(parseComponent(message));
    }

    public String getGlobalPrefix() {
        // Use cached value if available
        if (cachedGlobalPrefix != null) {
            return cachedGlobalPrefix;
        }
        if (configManager == null)
            return "";
        FileConfiguration settings = configManager.getConfig("settings");
        if (settings == null)
            return "";
        String prefix = settings.getString("messages.prefix", "<dark_gray>[<aqua>Valerin<white>Utils<dark_gray>] ");
        cachedGlobalPrefix = prefix;
        return cachedGlobalPrefix;
    }

    public Component parseComponent(String text) {
        if (text == null)
            return Component.empty();

        String normalized = normalizeToMiniMessage(text);
        try {
            return MiniMessage.miniMessage().deserialize(normalized);
        } catch (Exception ignored) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(normalized.replace('§', '&'));
        }
    }

    private String normalizeToMiniMessage(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        String message = input;
        if (message.contains("%prefix%")) {
            message = message.replace("%prefix%", getGlobalPrefix());
        }

        message = convertBungeeHex(message, LEGACY_BUNGEE_HEX_AMPERSAND, '&');
        message = convertBungeeHex(message, LEGACY_BUNGEE_HEX_SECTION, '§');
        message = LEGACY_HEX_PATTERN.matcher(message).replaceAll("<color:#$1>");

        if (message.indexOf('&') < 0 && message.indexOf('§') < 0) {
            return autoLinkUrlsOutsideMiniTags(message);
        }

        StringBuilder out = new StringBuilder(message.length() + 32);
        for (int index = 0; index < message.length(); index++) {
            char ch = message.charAt(index);
            if ((ch == '&' || ch == '§') && index + 1 < message.length()) {
                char code = Character.toLowerCase(message.charAt(index + 1));
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
                    out.append(replacement);
                    index++;
                    continue;
                }
            }
            out.append(ch);
        }

        return autoLinkUrlsOutsideMiniTags(out.toString());
    }

    private String autoLinkUrlsOutsideMiniTags(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder(input.length() + 32);
        StringBuilder plainSegment = new StringBuilder();
        boolean inMiniTag = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '<') {
                if (!inMiniTag) {
                    appendAutoLinkedPlainSegment(result, plainSegment);
                    plainSegment.setLength(0);
                    inMiniTag = true;
                }
                result.append(ch);
                continue;
            }
            if (ch == '>') {
                result.append(ch);
                if (inMiniTag) {
                    inMiniTag = false;
                }
                continue;
            }

            if (inMiniTag) {
                result.append(ch);
            } else {
                plainSegment.append(ch);
            }
        }

        appendAutoLinkedPlainSegment(result, plainSegment);
        return result.toString();
    }

    private void appendAutoLinkedPlainSegment(StringBuilder result, StringBuilder plainSegment) {
        if (plainSegment == null || plainSegment.length() == 0) {
            return;
        }

        String plain = plainSegment.toString();
        Matcher matcher = URL_PATTERN.matcher(plain);
        int cursor = 0;

        while (matcher.find()) {
            result.append(plain, cursor, matcher.start());
            String displayUrl = matcher.group(1);
            String openUrl = normalizeOpenUrl(displayUrl);
            result.append("<click:open_url:'")
                .append(escapeMiniMessageClickValue(openUrl))
                .append("'>")
                .append(displayUrl)
                    .append("</click>");
            cursor = matcher.end();
        }

        if (cursor < plain.length()) {
            result.append(plain, cursor, plain.length());
        }
    }

    private String normalizeOpenUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        String trimmed = rawUrl.trim();
        if (trimmed.regionMatches(true, 0, "http://", 0, 7)
                || trimmed.regionMatches(true, 0, "https://", 0, 8)) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private String escapeMiniMessageClickValue(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    private String convertBungeeHex(String input, Pattern pattern, char marker) {
        Matcher matcher = pattern.matcher(input);
        StringBuffer sb = new StringBuffer(input.length());
        while (matcher.find()) {
            String sequence = matcher.group();
            StringBuilder hex = new StringBuilder(6);
            for (int i = 0; i < sequence.length(); i++) {
                if (sequence.charAt(i) == marker && i + 1 < sequence.length()) {
                    char next = sequence.charAt(i + 1);
                    if (Character.digit(next, 16) >= 0) {
                        hex.append(next);
                    }
                }
            }
            String replacement = "<color:#" + hex + ">";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public void updateConfig() {
        // Invalidate cached prefix
        cachedGlobalPrefix = null;

        // Delegate to ConfigManager
        if (configManager != null) {
            configManager.loadAll();
            getLogger().info("Configurations reloaded via ConfigManager.");
        } else {
            // Fallback if called before init (should not happen)
            reloadConfig();
        }
    }

    public boolean isDebug() {
        FileConfiguration s = configManager.getConfig("settings");
        return s != null && s.getBoolean("debug", false);
    }
}
