package me.mtynnn.valerinutils.core;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.network.CrossServerConfig;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;

public class DatabaseManager {

    private final ValerinUtils plugin;
    private Connection connection;
    private final String url;
    private final CrossServerConfig crossConfig;
    private final Map<String, Integer> integerCache = new ConcurrentHashMap<>();

    private static final String COLUMN_EXISTS_MSG = "duplicate column name";

    public DatabaseManager(ValerinUtils plugin, CrossServerConfig crossConfig) {
        this.plugin = plugin;
        this.crossConfig = crossConfig;
        this.url = "jdbc:sqlite:" + new File(plugin.getDataFolder(), "ValerinUtils.db").getAbsolutePath();
    }

    public void initialize() {
        if (crossConfig.enabled()) {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
                try (Connection ignored = openConnection()) {
                    createTables();
                }
                plugin.getLogger().info("Connected to MySQL cross-server storage.");
            } catch (ClassNotFoundException | SQLException error) {
                throw new IllegalStateException("Could not initialize MySQL", error);
            }
            return;
        }
        try {
            if (connection != null && !connection.isClosed()) {
                return;
            }

            // Ensure the JDBC driver is available
            try {
                Class.forName("org.sqlite.JDBC");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().log(Level.SEVERE, "SQLite JDBC driver not found! Database initialization failed.",
                        e);
                return;
            }

            connection = DriverManager.getConnection(url);

            // Enable WAL mode for better concurrent read/write performance
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
            }

            plugin.getLogger().info("Connected to SQLite database (WAL mode).");
            createTables();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not initialize database connection", e);
        }
    }

    public Connection getConnection() {
        if (crossConfig.enabled()) {
            try {
                return openConnection();
            } catch (SQLException error) {
                throw new IllegalStateException("Could not open MySQL connection", error);
            }
        }
        try {
            if (connection == null || connection.isClosed()) {
                initialize();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check connection status", e);
        }
        return connection;
    }

    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error while closing database connection", e);
        }
    }

    private void createTables() {
        if (crossConfig.enabled()) {
            createMySqlTables();
            return;
        }
        // Table: player_data
        // Stores all player stats centralized
        String sql = "CREATE TABLE IF NOT EXISTS player_data (" +
                "uuid TEXT PRIMARY KEY, " +
                "name TEXT, " +
                "kills INTEGER DEFAULT 0, " +
                "deaths INTEGER DEFAULT 0, " +
                "daily_kills INTEGER DEFAULT 0, " +
                "last_daily_reset BIGINT DEFAULT 0, " +
                "menu_disabled BOOLEAN DEFAULT 0, " +
                "nickname TEXT" +
                ");";

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(sql);

            // Attempt to add columns if missing (simple schema migration)
            addColumnIfMissing(stmt, "player_data", "menu_disabled", "BOOLEAN DEFAULT 0");
            addColumnIfMissing(stmt, "player_data", "nickname", "TEXT");
            addColumnIfMissing(stmt, "player_data", "total_money_earned", "REAL DEFAULT 0");
            addColumnIfMissing(stmt, "player_data", "total_shards_earned", "REAL DEFAULT 0");
            addColumnIfMissing(stmt, "player_data", "grace_expires_at", "BIGINT DEFAULT 0");
            addColumnIfMissing(stmt, "player_data", "grace_pvp_warned", "BOOLEAN DEFAULT 0");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create tables", e);
        }

        // Table: player_votes
        // Stores individual vote records for detailed stats
        String votesSql = "CREATE TABLE IF NOT EXISTS player_votes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "uuid TEXT, " +
                "service_name TEXT, " +
                "timestamp BIGINT" +
                ");";

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(votesSql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create player_votes table", e);
        }

        // Table: server_data
        // Stores global server stats (key-value pairs)
        String serverDataSql = "CREATE TABLE IF NOT EXISTS server_data (" +
                "key TEXT PRIMARY KEY, " +
                "value TEXT" +
                ");";

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(serverDataSql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create server_data table", e);
        }

        // Table: player_codes
        // Tracks one-time codes used by players
        String codesSql = "CREATE TABLE IF NOT EXISTS player_codes (" +
                "uuid TEXT, " +
                "code TEXT, " +
                "PRIMARY KEY (uuid, code)" +
                ");";

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(codesSql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create player_codes table", e);
        }
    }

    private void createMySqlTables() {
        try (Connection connection = openConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS player_data(uuid VARCHAR(36) PRIMARY KEY,name VARCHAR(64),kills INT DEFAULT 0,deaths INT DEFAULT 0,daily_kills INT DEFAULT 0,last_daily_reset BIGINT DEFAULT 0,menu_disabled BOOLEAN DEFAULT 0,nickname TEXT,total_money_earned DOUBLE DEFAULT 0,total_shards_earned DOUBLE DEFAULT 0,grace_expires_at BIGINT DEFAULT 0,grace_pvp_warned BOOLEAN DEFAULT 0,revision BIGINT NOT NULL DEFAULT 0)");
            statement.execute("CREATE TABLE IF NOT EXISTS player_votes(id BIGINT AUTO_INCREMENT PRIMARY KEY,uuid VARCHAR(36),service_name VARCHAR(128),timestamp BIGINT,INDEX votes_uuid_time(uuid,timestamp))");
            statement.execute("CREATE TABLE IF NOT EXISTS server_data(`key` VARCHAR(191) PRIMARY KEY,`value` TEXT)");
            statement.execute("CREATE TABLE IF NOT EXISTS player_codes(uuid VARCHAR(36),code VARCHAR(191),PRIMARY KEY(uuid,code))");
            try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "player_data", "revision")) {
                if (!columns.next()) statement.execute("ALTER TABLE player_data ADD COLUMN revision BIGINT NOT NULL DEFAULT 0");
            }
        } catch (SQLException error) {
            throw new IllegalStateException("Could not create MySQL tables", error);
        }
    }

    public boolean crossServer() { return crossConfig.enabled(); }

    public Connection openConnection() throws SQLException {
        return crossConfig.enabled()
                ? DriverManager.getConnection(crossConfig.mysqlUrl(), crossConfig.mysqlUser(), crossConfig.mysqlPassword())
                : getConnection();
    }

    public void closeOperation(Connection operation) {
        if (crossConfig.enabled() && operation != null) try { operation.close(); } catch (SQLException ignored) { }
    }

    public boolean incrementEarnings(String uuid, EarningsCurrency currency, double amount) {
        Connection operation = null;
        try {
            operation = getConnection();
            incrementEarnings(operation, uuid, currency, amount, crossConfig.enabled());
            return true;
        } catch (SQLException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not persist " + currency.id() + " earnings for " + uuid, e);
            return false;
        } finally {
            closeOperation(operation);
        }
    }

    static void incrementEarnings(Connection connection, String uuid, EarningsCurrency currency, double amount)
            throws SQLException {
        incrementEarnings(connection, uuid, currency, amount, false);
    }

    static void incrementEarnings(Connection connection, String uuid, EarningsCurrency currency, double amount,
                                  boolean mysql) throws SQLException {
        String column = currency.column();
        String sql = mysql
                ? "INSERT INTO player_data (uuid," + column + ") VALUES (?,?) ON DUPLICATE KEY UPDATE "
                        + column + "=COALESCE(" + column + ",0)+VALUES(" + column + ")"
                : "INSERT INTO player_data (uuid, " + column + ") VALUES (?, ?) "
                        + "ON CONFLICT(uuid) DO UPDATE SET " + column + "=COALESCE(" + column + ", 0)+excluded." + column;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setDouble(2, amount);
            ps.executeUpdate();
        }
    }

    private void addColumnIfMissing(Statement stmt, String table, String column, String type) throws SQLException {
        try {
            stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type + ";");
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains(COLUMN_EXISTS_MSG)) {
                return;
            }
            throw e;
        }
    }

    // ================== Reward Codes ==================

    public boolean hasUsedCode(String uuid, String code) {
        if (shouldDeferJdbc(crossConfig.enabled(), Bukkit.isPrimaryThread())) return false;
        String sql = "SELECT 1 FROM player_codes WHERE uuid = ? AND code = ?";
        Connection operation = null;
        try {
            operation = getConnection();
            try (PreparedStatement ps = operation.prepareStatement(sql)) {
                ps.setString(1, uuid);
                ps.setString(2, code.toUpperCase());
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error checking code usage", e);
            return false;
        } finally {
            closeOperation(operation);
        }
    }

    public void markCodeUsed(String uuid, String code) {
        tryMarkCodeUsed(uuid, code);
    }

    public boolean tryMarkCodeUsed(String uuid, String code) {
        Connection operation = null;
        try {
            operation = getConnection();
            return tryClaimCode(operation, uuid, code, crossConfig.enabled());
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error marking code as used", e);
            return false;
        } finally {
            closeOperation(operation);
        }
    }

    static boolean tryClaimCode(Connection connection, String uuid, String code, boolean mysql) throws SQLException {
        String sql = mysql ? "INSERT IGNORE INTO player_codes(uuid,code) VALUES(?,?)"
                : "INSERT OR IGNORE INTO player_codes(uuid,code) VALUES(?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid);
            statement.setString(2, code.toUpperCase());
            return statement.executeUpdate() == 1;
        }
    }

    static boolean shouldDeferJdbc(boolean crossServer, boolean primaryThread) {
        return crossServer && primaryThread;
    }

    // ================== Server Data (Global Stats) ==================

    public int getServerInt(String key, int defaultValue) {
        if (shouldDeferJdbc(crossConfig.enabled(), Bukkit.isPrimaryThread())) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> getServerInt(key, defaultValue));
            return integerCache.getOrDefault("server:" + key, defaultValue);
        }
        String sql = crossConfig.enabled() ? "SELECT `value` FROM server_data WHERE `key`=?"
                : "SELECT value FROM server_data WHERE key = ?";
        Connection operation = null;
        try {
            operation = getConnection();
            try (PreparedStatement ps = operation.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int value = Integer.parseInt(rs.getString("value"));
                    integerCache.put("server:" + key, value);
                    return value;
                }
            }}
        } catch (Exception e) {
            plugin.getLogger().warning("Could not get server data for key: " + key);
        }
        finally { closeOperation(operation); }
        return defaultValue;
    }

    public void setServerInt(String key, int value) {
        if (shouldDeferJdbc(crossConfig.enabled(), Bukkit.isPrimaryThread())) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> setServerInt(key, value));
            return;
        }
        String sql = crossConfig.enabled()
                ? "INSERT INTO server_data(`key`,`value`) VALUES (?,?) ON DUPLICATE KEY UPDATE `value`=VALUES(`value`)"
                : "INSERT INTO server_data (key, value) VALUES (?, ?) ON CONFLICT(key) DO UPDATE SET value=excluded.value";
        Connection operation = null;
        try {
            operation = getConnection();
            try (PreparedStatement ps = operation.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, String.valueOf(value));
            ps.executeUpdate();
            }
            integerCache.put("server:" + key, value);
            if (crossConfig.enabled()) plugin.getCrossServerService().invalidateServer(key);
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not set server data for key: " + key);
        } finally { closeOperation(operation); }
    }

    // Async helper to run standard queries if needed, but we used PreparedStatement
    // in Logic

    // ================== Vote Tracking ==================

    public void addVote(String uuid, String serviceName, long timestamp) {
        if (shouldDeferJdbc(crossConfig.enabled(), Bukkit.isPrimaryThread())) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> addVote(uuid, serviceName, timestamp));
            return;
        }
        String sql = "INSERT INTO player_votes (uuid, service_name, timestamp) VALUES (?, ?, ?)";
        Connection operation = null;
        try {
            operation = getConnection();
            try (PreparedStatement ps = operation.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, serviceName);
            ps.setLong(3, timestamp);
            ps.executeUpdate();
            }
            if (crossConfig.enabled()) plugin.getCrossServerService().invalidatePlayer(java.util.UUID.fromString(uuid));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not add vote record", e);
        } finally { closeOperation(operation); }
    }

    public int getVotesBetween(String uuid, long startTimestamp, long endTimestamp) {
        String cacheKey = "votes:" + uuid + ":" + startTimestamp + ":" + endTimestamp;
        if (shouldDeferJdbc(crossConfig.enabled(), Bukkit.isPrimaryThread())) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> getVotesBetween(uuid, startTimestamp, endTimestamp));
            return integerCache.getOrDefault(cacheKey, 0);
        }
        String sql = "SELECT COUNT(*) FROM player_votes WHERE uuid = ? AND timestamp >= ? AND timestamp <= ?";
        Connection operation = null;
        try {
            operation = getConnection();
            try (PreparedStatement ps = operation.prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setLong(2, startTimestamp);
            ps.setLong(3, endTimestamp);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int value = rs.getInt(1); integerCache.put(cacheKey, value); return value;
                }
            }}
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not count votes", e);
        }
        finally { closeOperation(operation); }
        return 0;
    }

    public int getTotalVotes(String uuid) {
        String cacheKey = "votes-total:" + uuid;
        if (shouldDeferJdbc(crossConfig.enabled(), Bukkit.isPrimaryThread())) {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> getTotalVotes(uuid));
            return integerCache.getOrDefault(cacheKey, 0);
        }
        String sql = "SELECT COUNT(*) FROM player_votes WHERE uuid = ?";
        Connection operation = null;
        try {
            operation = getConnection();
            try (PreparedStatement ps = operation.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    int value = rs.getInt(1); integerCache.put(cacheKey, value); return value;
                }
            }}
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not count total votes", e);
        }
        finally { closeOperation(operation); }
        return 0;
    }

    public void invalidatePlayerCache(String uuid) {
        integerCache.keySet().removeIf(key -> key.startsWith("votes:" + uuid + ":")
                || key.equals("votes-total:" + uuid));
    }

    public void invalidateServerCache(String key) {
        integerCache.remove("server:" + key);
    }
}
