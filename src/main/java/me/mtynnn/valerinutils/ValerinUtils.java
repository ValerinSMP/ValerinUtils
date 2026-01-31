package me.mtynnn.valerinutils;

import me.mtynnn.valerinutils.commands.MenuItemCommand;
import me.mtynnn.valerinutils.commands.ValerinUtilsCommand;
import me.mtynnn.valerinutils.core.ConfigManager;
import me.mtynnn.valerinutils.core.DatabaseManager;
import me.mtynnn.valerinutils.core.ModuleManager;
import me.mtynnn.valerinutils.core.PlayerData;
import me.mtynnn.valerinutils.modules.externalplaceholders.ExternalPlaceholdersModule;
import me.mtynnn.valerinutils.modules.joinquit.JoinQuitModule;
import me.mtynnn.valerinutils.modules.killrewards.KillRewardsModule;
import me.mtynnn.valerinutils.modules.menuitem.MenuItemModule;
import me.mtynnn.valerinutils.modules.tiktok.TikTokModule;
import me.mtynnn.valerinutils.modules.vote40.Vote40Module;
import me.mtynnn.valerinutils.placeholders.ValerinUtilsExpansion;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ValerinUtils extends JavaPlugin implements Listener {

    private static ValerinUtils instance;
    private ModuleManager moduleManager;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;

    private MenuItemModule menuItemModule;
    private ExternalPlaceholdersModule externalPlaceholdersModule;
    private JoinQuitModule joinQuitModule;
    private KillRewardsModule killRewardsModule;
    private TikTokModule tikTokModule;

    // Cache
    private final Map<UUID, PlayerData> playerDataCache = new ConcurrentHashMap<>();

    // Performance: Pre-compiled patterns and cached values
    private static final Pattern HEX_PATTERN = Pattern.compile("&#[a-fA-F0-9]{6}");
    private static final Pattern HEX_TO_MINI_PATTERN = Pattern.compile("&#([0-9a-fA-F]{6})");
    private String cachedGlobalPrefix = null;

    // Legacy color code mappings for fast conversion
    private static final Map<String, String> LEGACY_TO_MINI = Map.ofEntries(
            Map.entry("&0", "<black>"), Map.entry("&1", "<dark_blue>"),
            Map.entry("&2", "<dark_green>"), Map.entry("&3", "<dark_aqua>"),
            Map.entry("&4", "<dark_red>"), Map.entry("&5", "<dark_purple>"),
            Map.entry("&6", "<gold>"), Map.entry("&7", "<gray>"),
            Map.entry("&8", "<dark_gray>"), Map.entry("&9", "<blue>"),
            Map.entry("&a", "<green>"), Map.entry("&b", "<aqua>"),
            Map.entry("&c", "<red>"), Map.entry("&d", "<light_purple>"),
            Map.entry("&e", "<yellow>"), Map.entry("&f", "<white>"),
            Map.entry("&k", "<obfuscated>"), Map.entry("&l", "<bold>"),
            Map.entry("&m", "<strikethrough>"), Map.entry("&n", "<underlined>"),
            Map.entry("&o", "<italic>"), Map.entry("&r", "<reset>"));

    @Override
    public void onEnable() {
        instance = this;

        // 1. Initialize Managers
        configManager = new ConfigManager(this);
        configManager.loadAll(); // Detects and migrates configs

        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();

        // 2. Data Migration (v1 -> v2)
        performDataMigration();

        // 3. Register Events
        getServer().getPluginManager().registerEvents(this, this);

        // 4. Initialize Modules
        moduleManager = new ModuleManager(this);

        menuItemModule = new MenuItemModule(this);
        moduleManager.registerModule(menuItemModule);

        externalPlaceholdersModule = new ExternalPlaceholdersModule(this);
        moduleManager.registerModule(externalPlaceholdersModule);

        joinQuitModule = new JoinQuitModule(this);
        moduleManager.registerModule(joinQuitModule);

        if (Bukkit.getPluginManager().getPlugin("Votifier") != null
                || Bukkit.getPluginManager().getPlugin("VotifierPlus") != null) {
            Vote40Module voteModule = new Vote40Module(this);
            moduleManager.registerModule(voteModule);
        }

        killRewardsModule = new KillRewardsModule(this);
        moduleManager.registerModule(killRewardsModule);

        tikTokModule = new TikTokModule(this);
        moduleManager.registerModule(tikTokModule);

        moduleManager.enableAll();

        // 5. Hooks & Commands
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ValerinUtilsExpansion(this).register();
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

        getLogger().info("ValerinUtils 2.0 (Optimized) enabled");
    }

    @Override
    public void onDisable() {
        // Save all online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            savePlayerDataSync(p.getUniqueId());
        }

        if (moduleManager != null) {
            moduleManager.disableAll();
        }
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
        getLogger().info("ValerinUtils disabled");
    }

    public static ValerinUtils getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    // --- Data Logic (Centralized here or in a DataManager, keeping here for
    // simplicity access) ---

    // Async Load
    @EventHandler
    public void onAsyncLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();

        try {
            PlayerData data = loadPlayerDataFromDB(uuid);
            if (data == null) {
                data = new PlayerData(uuid, name);
            } else {
                data.setName(name); // update name
            }
            playerDataCache.put(uuid, data);
        } catch (Exception e) {
            getLogger().severe("Error loading data for " + name);
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerData data = playerDataCache.remove(uuid);
        if (data != null && data.isDirty()) {
            // Save Async
            CompletableFuture.runAsync(() -> savePlayerDataToDB(data));
        }
    }

    public PlayerData getPlayerData(UUID uuid) {
        return playerDataCache.get(uuid);
    }

    private PlayerData loadPlayerDataFromDB(UUID uuid) throws SQLException {
        String sql = "SELECT * FROM player_data WHERE uuid = ?";
        try (PreparedStatement ps = databaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PlayerData pd = new PlayerData(uuid, rs.getString("name"));
                    pd.setTikTokClaimed(rs.getBoolean("tiktok_claimed"));
                    pd.setKills(rs.getInt("kills"));
                    pd.setDeaths(rs.getInt("deaths"));
                    pd.setDailyRewardsCount(rs.getInt("daily_kills"));
                    pd.setLastDailyReset(rs.getLong("last_daily_reset"));
                    pd.setMenuDisabled(rs.getBoolean("menu_disabled"));
                    pd.setRoyalPayDisabled(rs.getBoolean("royal_pay_disabled"));
                    pd.setDirty(false);
                    return pd;
                }
            }
        }
        return null;
    }

    private void savePlayerDataToDB(PlayerData data) {
        if (data == null)
            return;
        String sql = "INSERT INTO player_data (uuid, name, tiktok_claimed, kills, deaths, daily_kills, last_daily_reset, menu_disabled, royal_pay_disabled) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET " +
                "name=excluded.name, tiktok_claimed=excluded.tiktok_claimed, kills=excluded.kills, " +
                "deaths=excluded.deaths, daily_kills=excluded.daily_kills, last_daily_reset=excluded.last_daily_reset, menu_disabled=excluded.menu_disabled, royal_pay_disabled=excluded.royal_pay_disabled";

        try (PreparedStatement ps = databaseManager.getConnection().prepareStatement(sql)) {
            ps.setString(1, data.getUuid().toString());
            ps.setString(2, data.getName());
            ps.setBoolean(3, data.isTikTokClaimed());
            ps.setInt(4, data.getKills());
            ps.setInt(5, data.getDeaths());
            ps.setInt(6, data.getDailyRewardsCount());
            ps.setLong(7, data.getLastDailyReset());
            ps.setBoolean(8, data.isMenuDisabled());
            ps.setBoolean(9, data.isRoyalPayDisabled());
            ps.executeUpdate();
        } catch (SQLException e) {
            getLogger().severe("Could not save data for " + data.getName());
            e.printStackTrace();
        }
    }

    private void savePlayerDataSync(UUID uuid) {
        PlayerData data = playerDataCache.get(uuid);
        if (data != null && data.isDirty()) {
            savePlayerDataToDB(data);
        }
    }

    // --- Migration Logic (Data) ---

    private void performDataMigration() {
        // 1. TikTok Data
        File tiktokFile = new File(getDataFolder(), "tiktok_data.yml");
        if (tiktokFile.exists()) {
            getLogger().info("Migrating tiktok_data.yml to database...");
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(tiktokFile);
            List<String> claimed = cfg.getStringList("claimed");
            int count = 0;
            for (String uuidStr : claimed) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    // Insert or Update to set tiktok_claimed = true
                    String sql = "INSERT INTO player_data (uuid, tiktok_claimed) VALUES (?, true) " +
                            "ON CONFLICT(uuid) DO UPDATE SET tiktok_claimed=true";
                    try (PreparedStatement ps = databaseManager.getConnection().prepareStatement(sql)) {
                        ps.setString(1, uuid.toString());
                        ps.executeUpdate();
                        count++;
                    }
                } catch (Exception e) {
                }
            }
            getLogger().info("Migrated " + count + " TikTok records.");
            tiktokFile.renameTo(new File(getDataFolder(), "tiktok_data.yml.bak"));
        }

        // 1.5 MenuItem Data (NEW)
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

        // 1.6 RoyaleEconomy Pay Data
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

        // 1.7 JoinQuit Data (unique-players)
        File joinquitFile = new File(getDataFolder(), "joinquit_data.yml");
        if (joinquitFile.exists()) {
            getLogger().info("Migrating joinquit_data.yml to database...");
            FileConfiguration cfg = YamlConfiguration.loadConfiguration(joinquitFile);
            int uniquePlayers = cfg.getInt("unique-players", 0);
            if (uniquePlayers > 0) {
                databaseManager.setServerInt("unique_players", uniquePlayers);
                getLogger().info("Migrated unique_players: " + uniquePlayers);
            }
            joinquitFile.renameTo(new File(getDataFolder(), "joinquit_data.yml.bak"));
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

    public TikTokModule getTikTokModule() {
        return tikTokModule;
    }

    // --- Message Utils (Legacy Compat) ---
    public String getMessage(String key) {
        // Now mostly used by MenuItem or others, but better to use ConfigManager
        // For compatibility, we'll look in settings.yml or fallback
        // Or specific modules calling this? Modules should check their own config now.
        // But for global messages...
        FileConfiguration settings = configManager.getConfig("settings");
        if (settings == null)
            return "";

        String prefixRaw = settings.getString("messages.prefix", "&8[&bValerin&fUtils&8]&r ");
        String prefix = ChatColor.translateAlternateColorCodes('&', prefixRaw);

        if (settings.isList("messages." + key)) {
            List<String> list = settings.getStringList("messages." + key);
            if (list.isEmpty())
                return "";
            return translateColors(list.get(0).replace("%prefix%", prefix));
        }

        String raw = settings.getString("messages." + key);
        if (raw == null)
            return translateColors("&cMensaje faltante: " + key); // Fail gracefully
        return translateColors(raw.replace("%prefix%", prefix));
    }

    public List<String> getMessageList(String key) {
        FileConfiguration settings = configManager.getConfig("settings");
        if (settings == null)
            return Collections.emptyList();

        String prefixRaw = settings.getString("messages.prefix", "&8[&bValerin&fUtils&8]&r ");
        String prefix = ChatColor.translateAlternateColorCodes('&', prefixRaw);

        if (settings.isList("messages." + key)) {
            List<String> rawList = settings.getStringList("messages." + key);
            List<String> processed = new ArrayList<>();
            for (String line : rawList) {
                processed.add(translateColors(line.replace("%prefix%", prefix)));
            }
            return processed;
        }
        return Collections.singletonList(getMessage(key));
    }

    public String translateColors(String message) {
        if (message == null)
            return "";

        // Global Prefix Support
        if (message.contains("%prefix%")) {
            message = message.replace("%prefix%", getGlobalPrefix());
        }

        // Use pre-compiled pattern
        Matcher matcher = HEX_PATTERN.matcher(message);
        while (matcher.find()) {
            String color = message.substring(matcher.start(), matcher.end());
            try {
                message = message.replace(color, net.md_5.bungee.api.ChatColor.of(color.substring(1)).toString());
            } catch (Exception e) {
                // Invalid color code, skip
            }
            matcher = HEX_PATTERN.matcher(message);
        }
        return ChatColor.translateAlternateColorCodes('&', message);
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
        String prefix = settings.getString("messages.prefix", "&8[&bValerin&fUtils&8]&r ");
        cachedGlobalPrefix = ChatColor.translateAlternateColorCodes('&', prefix);
        return cachedGlobalPrefix;
    }

    public Component parseComponent(String text) {
        if (text == null)
            return Component.empty();

        String processed = legacyToMiniMessage(text);
        return MiniMessage.miniMessage().deserialize(processed);
    }

    private String legacyToMiniMessage(String text) {
        if (text == null || text.isEmpty())
            return "";

        // Use pre-compiled pattern for hex colors
        text = HEX_TO_MINI_PATTERN.matcher(text).replaceAll("<#$1>");

        // Use StringBuilder for efficient replacements
        StringBuilder sb = new StringBuilder(text);
        for (Map.Entry<String, String> entry : LEGACY_TO_MINI.entrySet()) {
            int index;
            while ((index = sb.indexOf(entry.getKey())) != -1) {
                sb.replace(index, index + entry.getKey().length(), entry.getValue());
            }
        }
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
