package me.mtynnn.valerinutils;

import me.mtynnn.valerinutils.commands.ValerinUtilsCommand;
import me.mtynnn.valerinutils.core.CommandRegistry;
import me.mtynnn.valerinutils.core.ConfigManager;
import me.mtynnn.valerinutils.core.DatabaseManager;
import me.mtynnn.valerinutils.core.ModuleManager;
import me.mtynnn.valerinutils.core.MessageService;
import me.mtynnn.valerinutils.core.PlayerData;
import me.mtynnn.valerinutils.core.PlayerDataManager;
import me.mtynnn.valerinutils.crimson.CrimsonProtectionHook;
import me.mtynnn.valerinutils.crimson.WorldGuardCrimsonProtection;
import me.mtynnn.valerinutils.dimension.DimensionAccessGuard;
import me.mtynnn.valerinutils.integrations.excellenteconomy.ExcellentEconomyEarningsTracker;
import me.mtynnn.valerinutils.worldguard.WorldGuardMobSpawnFlags;
import me.mtynnn.valerinutils.modules.killrewards.KillRewardsModule;
import me.mtynnn.valerinutils.modules.menuitem.MenuItemModule;
import me.mtynnn.valerinutils.modules.codes.CodesModule;
import me.mtynnn.valerinutils.modules.vouchers.VouchersModule;
import me.mtynnn.valerinutils.modules.utility.UtilityModule;
import me.mtynnn.valerinutils.modules.deathspawn.DeathSpawnModule;

import me.mtynnn.valerinutils.modules.grace.GraceModule;
import me.mtynnn.valerinutils.modules.itemsign.ItemSignModule;
import me.mtynnn.valerinutils.modules.events.EventsModule;
import me.mtynnn.valerinutils.modules.vipslots.VipSlotsModule;
import me.mtynnn.valerinutils.network.CrossServerConfig;
import me.mtynnn.valerinutils.network.CrossServerService;
import me.mtynnn.valerinutils.network.StorageMigrator;
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
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ValerinUtils extends JavaPlugin implements Listener {
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern LEGACY_BUNGEE_HEX_AMPERSAND = Pattern.compile("(?i)&x(&[0-9a-f]){6}");
    private static final Pattern LEGACY_BUNGEE_HEX_SECTION = Pattern.compile("(?i)§x(§[0-9a-f]){6}");
    private static final Pattern URL_PATTERN = Pattern.compile(
            "(?i)\\b((?:https?://)?(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,}(?:/[\\p{Alnum}\\-._~:/?#\\[\\]@!$&'()*+,;=%]*)?)(?<![.,;:!?])");

    private static ValerinUtils instance;
    private final Set<String> malformedMessageWarnings = ConcurrentHashMap.newKeySet();
    private ModuleManager moduleManager;
    private CommandRegistry commandRegistry;
    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private MessageService messageService;
    private Object crimsonProtectionFlag;
    private CrimsonProtectionHook crimsonProtection;
    private ExcellentEconomyEarningsTracker earningsTracker;
    private Object mobSpawnFlags;
    private DimensionAccessGuard dimensionAccessGuard;
    private EventsModule eventsModule;
    private VipSlotsModule vipSlotsModule;
    private CrossServerService crossServerService;

    private MenuItemModule menuItemModule;
    private KillRewardsModule killRewardsModule;
    private CodesModule codesModule;
    private DeathSpawnModule deathSpawnModule;
    private ItemSignModule itemSignModule;
    private UtilityModule utilityModule;
    private VouchersModule vouchersModule;
    private GraceModule graceModule;
    private ValerinUtilsExpansion placeholderExpansion;

    private PlayerDataManager playerDataManager;
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
        if (crossServerService != null && crossServerService.enabled()) {
            crossServerService.playerOnline(event.getPlayer());
            crossServerService.deliverPending(event.getPlayer());
        }
    }

    // Performance: cached values
    private String cachedGlobalPrefix = null;

    @Override
    public void onLoad() {
        if (getServer().getPluginManager().getPlugin("WorldGuard") == null) {
            return;
        }
        try {
            crimsonProtectionFlag = WorldGuardCrimsonProtection.registerFlag(this);
        } catch (LinkageError | RuntimeException error) {
            getLogger().severe("[CrimsonProtection] Could not register the WorldGuard flag; only this feature is disabled: "
                    + error.getMessage());
        }
        try {
            mobSpawnFlags = WorldGuardMobSpawnFlags.register(this);
        } catch (LinkageError | RuntimeException error) {
            getLogger().severe("[MobSpawnFlags] Could not register WorldGuard flags: " + error.getMessage());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (crossServerService != null && crossServerService.enabled()) crossServerService.playerOffline(event.getPlayer());
    }

    @Override
    public void onEnable() {
        long startedAt = System.nanoTime();
        getLogger().info("Starting ValerinUtils v" + getDescription().getVersion() + "...");
        getLogger().info("Platform: Paper 1.21.11+ | Java 21 bytecode");
        instance = this;

        // 1. Initialize Managers
        configManager = new ConfigManager(this);
        configManager.loadAll();
        messageService = new MessageService(this);
        commandRegistry = new CommandRegistry(this);

        CrossServerConfig crossConfig;
        try {
            crossConfig = CrossServerConfig.parse(configManager.getConfig("settings")
                    .getConfigurationSection("cross-server"));
            if (crossConfig.enabled() && java.util.concurrent.CompletableFuture.supplyAsync(
                    () -> StorageMigrator.hasPendingSqlite(getDataFolder(), crossConfig)).join()) {
                throw new IllegalStateException("SQLite still contains data. Disable cross-server and run "
                        + "/valerinutilsadmin storage-migrate start first.");
            }
            crossServerService = new CrossServerService(this, crossConfig);
            crossServerService.start();
            databaseManager = new DatabaseManager(this, crossConfig);
            if (crossConfig.enabled()) crossServerService.runBlocking(databaseManager::initialize);
            else databaseManager.initialize();
        } catch (RuntimeException error) {
            getLogger().severe("[CrossServer] " + error.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        playerDataManager = new PlayerDataManager(this);
        if (crossServerService.enabled()) {
            crossServerService.listen(envelope -> {
                if (envelope.type().equals("BROADCAST")) {
                    for (String line : envelope.payload().split("\u001e", -1)) {
                        Bukkit.broadcast(parseComponent(line));
                    }
                } else if (envelope.type().equals("HELPOP") && utilityModule != null) {
                    utilityModule.deliverNetworkHelpOp(envelope.payload());
                } else if (envelope.type().equals("PRESENCE")) {
                    crossServerService.applyPresence(envelope.payload());
                } else if (envelope.type().equals("REMOTE_ACTION")) {
                    applyRemoteAction(envelope.payload());
                } else if (envelope.type().equals("INVALIDATE_PLAYER")) {
                    try {
                        playerDataManager.invalidate(UUID.fromString(envelope.payload()));
                    } catch (IllegalArgumentException ignored) { }
                } else if (envelope.type().equals("INVALIDATE_SERVER")) {
                    databaseManager.invalidateServerCache(envelope.payload());
                }
            });
        }

        // 2. Data Migration (v1 -> v2)
        if (!databaseManager.crossServer()) performDataMigration();

        // 3. Reload Support: Load data for online players (Pre-load before modules)
        playerDataManager.reloadOnlinePlayers();

        // 4. Register Events
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(playerDataManager, this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
        dimensionAccessGuard = new DimensionAccessGuard(this);
        dimensionAccessGuard.reload(configManager.getConfig("settings"));
        getServer().getPluginManager().registerEvents(dimensionAccessGuard, this);
        initializeCrimsonProtection();
        initializeMobSpawnFlags();
        earningsTracker = new ExcellentEconomyEarningsTracker(this);
        earningsTracker.start();
        eventsModule = new EventsModule(this);
        eventsModule.start(configManager.getConfig("settings"));
        vipSlotsModule = new VipSlotsModule(this);
        vipSlotsModule.reload(configManager.getConfig("settings"));
        getServer().getPluginManager().registerEvents(vipSlotsModule, this);

        // 4. Initialize Modules
        moduleManager = new ModuleManager(this);

        menuItemModule = new MenuItemModule(this);
        moduleManager.registerModule(menuItemModule);

        killRewardsModule = new KillRewardsModule(this);
        moduleManager.registerModule(killRewardsModule);

        codesModule = new CodesModule(this);
        moduleManager.registerModule(codesModule);

        deathSpawnModule = new DeathSpawnModule(this);
        moduleManager.registerModule(deathSpawnModule);

        itemSignModule = new ItemSignModule(this);
        moduleManager.registerModule(itemSignModule);

        utilityModule = new UtilityModule(this);
        moduleManager.registerModule(utilityModule);

        vouchersModule = new VouchersModule(this);
        moduleManager.registerModule(vouchersModule);

        graceModule = new GraceModule(this);
        moduleManager.registerModule(graceModule);

        moduleManager.enableAll();

        // Limpia periódicamente entradas de jugadores que ya no están conectados.
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            playerDataManager.removeStaleEntries();
        }, 6000L, 6000L);

        // 5. Hooks & Commands
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new ValerinUtilsExpansion(this);
            placeholderExpansion.register();
            eventsModule.registerPlaceholders();
        }

        if (getCommand("valerinutils") != null) {
            ValerinUtilsCommand mainCmd = new ValerinUtilsCommand(this);
            commandRegistry.bind("core", "valerinutils", mainCmd, mainCmd);
            if (getCommand("valerinutilsadmin") != null) {
                commandRegistry.bind("core", "valerinutilsadmin", mainCmd, mainCmd);
            }
        }

        // 6. Startup Banner
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        getLogger().info("Enabled successfully in " + elapsedMs + " ms.");

        // 8. Cleanup ghost MenuItems if module is disabled (Reload fix)
        if (!moduleManager.isModuleEnabled("menuitem")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                menuItemModule.clearMenuItem(p);
            }
        }
    }

    @Override
    public void onDisable() {
        long startedAt = System.nanoTime();
        getLogger().info("Stopping ValerinUtils...");
        if (earningsTracker != null) {
            earningsTracker.stop();
            earningsTracker = null;
        }
        if (dimensionAccessGuard != null) {
            dimensionAccessGuard.stop();
            dimensionAccessGuard = null;
        }
        if (eventsModule != null) {
            eventsModule.stop();
            eventsModule = null;
        }
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, "BungeeCord");
        playerDataManager.saveAllAndClear();

        if (moduleManager != null) {
            moduleManager.disableAll();
        }

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
        if (crossServerService != null) {
            crossServerService.stop();
            crossServerService = null;
        }
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        getLogger().info("Disabled successfully in " + elapsedMs + " ms.");
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

    public CrossServerService getCrossServerService() {
        return crossServerService;
    }

    public void publishBroadcast(List<String> formattedLines) {
        if (crossServerService != null && crossServerService.enabled()) {
            crossServerService.broadcast(String.join("\u001e", formattedLines));
        }
    }

    public void publishHelpOp(String formatted) {
        if (crossServerService != null && crossServerService.enabled()) crossServerService.helpOp(formatted);
    }

    public boolean routeRemoteAction(String targetName, String action, String argument) {
        return crossServerService != null && crossServerService.routeAction(targetName, action, argument);
    }

    public boolean routeRemoteCommand(String targetName, String commandLine) {
        return routeRemoteAction(targetName, "COMMAND", commandLine);
    }

    public void queueNetworkVoucher(String targetName, String typeId, int amount,
                                    java.util.function.Consumer<UUID> callback) {
        if (crossServerService == null || !crossServerService.enabled()) callback.accept(null);
        else crossServerService.enqueueVoucher(targetName, typeId, amount, callback);
    }

    public void deliverPendingVoucher(CrossServerService.PendingVoucher grant) {
        Player target = Bukkit.getPlayerExact(grant.targetName());
        if (vouchersModule == null) {
            crossServerService.resolveVoucher(grant, CrossServerService.VoucherResult.PENDING, 0, ignored -> { });
        } else vouchersModule.deliverPendingVoucher(target, grant);
    }

    private void applyRemoteAction(String payload) {
        String[] values = payload.split("\u001f", 4);
        if (values.length != 4 || crossServerService == null
                || !values[0].equals(crossServerService.config().serverId())) return;
        try {
            Player target = Bukkit.getPlayer(UUID.fromString(values[1]));
            if (target == null || !target.isOnline()) return;
            if (values[2].equals("COMMAND")) {
                if (values[3].matches("(?i)(nick|grace) .{1,1000}"))
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), values[3]);
            } else if (values[2].equals("PENDING_VOUCHERS")) {
                crossServerService.deliverPending(target);
            } else if (utilityModule != null) {
                utilityModule.applyRemoteAction(target, values[2], values[3]);
            }
        } catch (IllegalArgumentException ignored) { }
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

    public DeathSpawnModule getDeathSpawnModule() {
        return deathSpawnModule;
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
        String prefix = settings.getString("messages.prefix",
                "<dark_gray>[<#FFD166>ᴠᴀʟᴇʀɪɴ</#FFD166>]</dark_gray> <reset>");
        cachedGlobalPrefix = prefix;
        return cachedGlobalPrefix;
    }

    public Component parseComponent(String text) {
        if (text == null)
            return Component.empty();

        String normalized = normalizeToMiniMessage(text);
        try {
            return MiniMessage.miniMessage().deserialize(normalized);
        } catch (Exception exception) {
            if (malformedMessageWarnings.add(normalized)) {
                getLogger().warning("[Messages] MiniMessage invalido; se enviara como texto plano: "
                        + normalized.replace('\n', ' '));
            }
            return Component.text(MiniMessage.miniMessage().stripTags(normalized));
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
            reloadCrimsonProtection();
            if (dimensionAccessGuard != null) {
                dimensionAccessGuard.reload(configManager.getConfig("settings"));
            }
            if (eventsModule != null) {
                eventsModule.reload(configManager.getConfig("settings"));
            }
            if (vipSlotsModule != null) {
                vipSlotsModule.reload(configManager.getConfig("settings"));
            }
            getLogger().info("Configurations reloaded via ConfigManager.");
        } else {
            // Fallback if called before init (should not happen)
            reloadConfig();
        }
    }

    private void initializeCrimsonProtection() {
        if (crimsonProtectionFlag == null || !Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return;
        }
        try {
            crimsonProtection = WorldGuardCrimsonProtection.create(this, crimsonProtectionFlag);
            reloadCrimsonProtection();
            getServer().getPluginManager().registerEvents(crimsonProtection, this);
        } catch (LinkageError | RuntimeException error) {
            crimsonProtection = null;
            getLogger().severe("[CrimsonProtection] Could not initialize; only this feature is disabled: "
                    + error.getMessage());
        }
    }

    private void reloadCrimsonProtection() {
        if (crimsonProtection != null && configManager != null) {
            crimsonProtection.reload(configManager.getConfig("settings"));
        }
    }

    private void initializeMobSpawnFlags() {
        if (mobSpawnFlags == null || !Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return;
        }
        try {
            getServer().getPluginManager().registerEvents(WorldGuardMobSpawnFlags.listener(mobSpawnFlags), this);
        } catch (LinkageError | RuntimeException error) {
            getLogger().severe("[MobSpawnFlags] Could not initialize: " + error.getMessage());
        }
    }

    public boolean isDebug() {
        FileConfiguration s = configManager.getConfig("settings");
        return s != null && s.getBoolean("debug", false);
    }
}
