package me.mtynnn.valerinutils.network;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.security.MessageDigest;

public final class StorageMigrator {
    private static final List<String> TABLES = List.of("player_data", "player_codes", "server_data", "player_votes");
    private static final AtomicReference<String> STATUS = new AtomicReference<>("idle");

    private StorageMigrator() { }

    public static boolean hasPendingSqlite(File dataFolder) {
        File file = new File(dataFolder, "ValerinUtils.db");
        if (!file.isFile() || file.length() == 0) return false;
        try {
            Class.forName("org.sqlite.JDBC");
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:file:"
                    + file.getAbsolutePath().replace('\\', '/') + "?mode=ro")) {
                for (String table : TABLES) {
                    try (Statement statement = connection.createStatement();
                         ResultSet result = statement.executeQuery("SELECT 1 FROM " + table + " LIMIT 1")) {
                        if (result.next()) return true;
                    } catch (SQLException ignored) { }
                }
            }
        } catch (Exception ignored) { }
        return false;
    }

    public static boolean hasPendingSqlite(File dataFolder, CrossServerConfig target) {
        if (!hasPendingSqlite(dataFolder)) return false;
        try (Connection connection = DriverManager.getConnection(target.mysqlUrl(), target.mysqlUser(), target.mysqlPassword());
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT `value` FROM server_data WHERE `key`='migration:sqlite-complete'")) {
            try (ResultSet result = statement.executeQuery()) {
                return !result.next() || !sourceFingerprint(dataFolder).equals(result.getString(1));
            }
        } catch (Exception error) {
            return true;
        }
    }

    public static String status() { return STATUS.get(); }

    public static synchronized void run(ValerinUtils plugin, CommandSender sender, boolean dryRun) {
        if (STATUS.get().equals("running") || STATUS.get().equals("dry-run-running")) {
            sender.sendMessage(plugin.parseComponent("%prefix%<yellow>Migración ya activa: " + STATUS.get()));
            return;
        }
        STATUS.set(dryRun ? "dry-run-running" : "running");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String result;
            try {
                CrossServerConfig target = CrossServerConfig.migrationTarget(plugin.getConfigManager()
                        .getConfig("settings").getConfigurationSection("cross-server"));
                result = migrate(plugin.getDataFolder(), target, dryRun);
                STATUS.set((dryRun ? "dry-run-complete: " : "complete: ") + result);
            } catch (Exception error) {
                STATUS.set("failed: " + error.getMessage());
            }
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(plugin.parseComponent(
                    "%prefix%<gray>Storage migration: <white>" + STATUS.get())));
        });
    }

    static String migrate(File dataFolder, CrossServerConfig target, boolean dryRun) throws Exception {
        Class.forName("org.sqlite.JDBC");
        Class.forName("com.mysql.cj.jdbc.Driver");
        File sourceFile = new File(dataFolder, "ValerinUtils.db");
        if (!sourceFile.isFile()) return "0 rows";
        int copied = 0;
        int conflicts = 0;
        try (Connection source = DriverManager.getConnection("jdbc:sqlite:file:"
                + sourceFile.getAbsolutePath().replace('\\', '/') + "?mode=ro");
             Connection destination = DriverManager.getConnection(target.mysqlUrl(), target.mysqlUser(), target.mysqlPassword())) {
            try (Statement schema = destination.createStatement()) {
                schema.execute("CREATE TABLE IF NOT EXISTS player_data(uuid VARCHAR(36) PRIMARY KEY,name VARCHAR(64),kills INT DEFAULT 0,deaths INT DEFAULT 0,daily_kills INT DEFAULT 0,last_daily_reset BIGINT DEFAULT 0,menu_disabled BOOLEAN DEFAULT 0,nickname TEXT,total_money_earned DOUBLE DEFAULT 0,total_shards_earned DOUBLE DEFAULT 0,grace_expires_at BIGINT DEFAULT 0,grace_pvp_warned BOOLEAN DEFAULT 0,revision BIGINT NOT NULL DEFAULT 0)");
                schema.execute("CREATE TABLE IF NOT EXISTS player_codes(uuid VARCHAR(36),code VARCHAR(191),PRIMARY KEY(uuid,code))");
                schema.execute("CREATE TABLE IF NOT EXISTS server_data(`key` VARCHAR(191) PRIMARY KEY,`value` TEXT)");
                schema.execute("CREATE TABLE IF NOT EXISTS player_votes(id BIGINT AUTO_INCREMENT PRIMARY KEY,uuid VARCHAR(36),service_name VARCHAR(128),timestamp BIGINT)");
            }
            destination.setAutoCommit(false);
            for (String table : TABLES) {
                try (Statement statement = source.createStatement(); ResultSet rows = statement.executeQuery("SELECT * FROM " + table)) {
                    ResultSetMetaData metadata = rows.getMetaData();
                    StringBuilder columns = new StringBuilder();
                    StringBuilder placeholders = new StringBuilder();
                    for (int index = 1; index <= metadata.getColumnCount(); index++) {
                        if (index > 1) { columns.append(','); placeholders.append(','); }
                        columns.append('`').append(metadata.getColumnName(index)).append('`');
                        placeholders.append('?');
                    }
                    try (PreparedStatement insert = destination.prepareStatement("INSERT IGNORE INTO `" + table
                            + "`(" + columns + ") VALUES(" + placeholders + ")")) {
                        while (rows.next()) {
                            for (int index = 1; index <= metadata.getColumnCount(); index++) insert.setObject(index, rows.getObject(index));
                            if (dryRun) copied++;
                            else if (insert.executeUpdate() == 1) copied++;
                            else conflicts++;
                        }
                    }
                } catch (SQLException missing) {
                    // Older SQLite installations may not contain every table yet.
                }
            }
            if (dryRun) destination.rollback(); else destination.commit();
            if (!dryRun) {
                try (PreparedStatement marker = destination.prepareStatement(
                        "INSERT INTO server_data(`key`,`value`) VALUES('migration:sqlite-complete',?) "
                                + "ON DUPLICATE KEY UPDATE `value`=VALUES(`value`)")) {
                    marker.setString(1, sourceFingerprint(dataFolder));
                    marker.executeUpdate();
                }
            }
        }
        return copied + " rows, " + conflicts + " conflicts";
    }

    private static String sourceFingerprint(File dataFolder) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        File file = new File(dataFolder, "ValerinUtils.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:file:"
                + file.getAbsolutePath().replace('\\', '/') + "?mode=ro")) {
            for (String table : TABLES) {
                digest.update(table.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                try (Statement statement = connection.createStatement();
                     ResultSet rows = statement.executeQuery("SELECT * FROM " + table + " ORDER BY rowid")) {
                    ResultSetMetaData metadata = rows.getMetaData();
                    while (rows.next()) for (int column = 1; column <= metadata.getColumnCount(); column++) {
                        Object value = rows.getObject(column);
                        digest.update(String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                    }
                } catch (SQLException ignored) { }
            }
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }
}
