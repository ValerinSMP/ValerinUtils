package me.mtynnn.valerinutils.core;

import me.mtynnn.valerinutils.ValerinUtils;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class DatabaseManager {

    private final ValerinUtils plugin;
    private Connection connection;
    private final String url;

    public DatabaseManager(ValerinUtils plugin) {
        this.plugin = plugin;
        this.url = "jdbc:sqlite:" + new File(plugin.getDataFolder(), "ValerinUtils.db").getAbsolutePath();
    }

    public void initialize() {
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
        // Table: player_data
        // Stores all player stats centralized
        String sql = "CREATE TABLE IF NOT EXISTS player_data (" +
                "uuid TEXT PRIMARY KEY, " +
                "name TEXT, " +
                "tiktok_claimed BOOLEAN DEFAULT 0, " +
                "kills INTEGER DEFAULT 0, " +
                "deaths INTEGER DEFAULT 0, " +
                "daily_kills INTEGER DEFAULT 0, " +
                "last_daily_reset BIGINT DEFAULT 0, " +
                "menu_disabled BOOLEAN DEFAULT 0, " +
                "royal_pay_disabled BOOLEAN DEFAULT 0, " +
                "death_messages_disabled BOOLEAN DEFAULT 0, " +
                "starter_kit_received BOOLEAN DEFAULT 0, " +
                "nickname TEXT" +
                ");";

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(sql);

            // Attempt to add columns if missing (simple schema migration)
            try {
                stmt.execute("ALTER TABLE player_data ADD COLUMN menu_disabled BOOLEAN DEFAULT 0;");
            } catch (SQLException ignored) {
                // Column likely exists
            }
            try {
                stmt.execute("ALTER TABLE player_data ADD COLUMN royal_pay_disabled BOOLEAN DEFAULT 0;");
            } catch (SQLException ignored) {
                // Column likely exists
            }
            try {
                stmt.execute("ALTER TABLE player_data ADD COLUMN death_messages_disabled BOOLEAN DEFAULT 0;");
            } catch (SQLException ignored) {
            }
            try {
                stmt.execute("ALTER TABLE player_data ADD COLUMN starter_kit_received BOOLEAN DEFAULT 0;");
            } catch (SQLException ignored) {
            }
            try {
                stmt.execute("ALTER TABLE player_data ADD COLUMN nickname TEXT;");
            } catch (SQLException ignored) {
            }
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

    // ================== Reward Codes ==================

    public boolean hasUsedCode(String uuid, String code) {
        String sql = "SELECT 1 FROM player_codes WHERE uuid = ? AND code = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, code.toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error checking code usage", e);
        }
        return false;
    }

    public void markCodeUsed(String uuid, String code) {
        String sql = "INSERT INTO player_codes (uuid, code) VALUES (?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, code.toUpperCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error marking code as used", e);
        }
    }

    // ================== Server Data (Global Stats) ==================

    public int getServerInt(String key, int defaultValue) {
        String sql = "SELECT value FROM server_data WHERE key = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Integer.parseInt(rs.getString("value"));
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Could not get server data for key: " + key);
        }
        return defaultValue;
    }

    public void setServerInt(String key, int value) {
        String sql = "INSERT INTO server_data (key, value) VALUES (?, ?) " +
                "ON CONFLICT(key) DO UPDATE SET value=excluded.value";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setString(2, String.valueOf(value));
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Could not set server data for key: " + key);
        }
    }

    // Async helper to run standard queries if needed, but we used PreparedStatement
    // in Logic

    // ================== Vote Tracking ==================

    public void addVote(String uuid, String serviceName, long timestamp) {
        String sql = "INSERT INTO player_votes (uuid, service_name, timestamp) VALUES (?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setString(2, serviceName);
            ps.setLong(3, timestamp);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not add vote record", e);
        }
    }

    public int getVotesBetween(String uuid, long startTimestamp, long endTimestamp) {
        String sql = "SELECT COUNT(*) FROM player_votes WHERE uuid = ? AND timestamp >= ? AND timestamp <= ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setLong(2, startTimestamp);
            ps.setLong(3, endTimestamp);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not count votes", e);
        }
        return 0;
    }

    public int getTotalVotes(String uuid) {
        String sql = "SELECT COUNT(*) FROM player_votes WHERE uuid = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not count total votes", e);
        }
        return 0;
    }
}
