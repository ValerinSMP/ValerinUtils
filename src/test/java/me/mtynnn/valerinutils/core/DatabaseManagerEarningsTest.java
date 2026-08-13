package me.mtynnn.valerinutils.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DatabaseManagerEarningsTest {

    @Test
    void offlineUpsertSurvivesAConnectionRestart(@TempDir Path tempDir) throws Exception {
        String url = "jdbc:sqlite:" + tempDir.resolve("earnings.db");
        try (Connection connection = DriverManager.getConnection(url)) {
            createTable(connection);
            DatabaseManager.incrementEarnings(connection, "offline-player", EarningsCurrency.MONEY, 10.75);
            DatabaseManager.incrementEarnings(connection, "offline-player", EarningsCurrency.MONEY, 2.5);
            DatabaseManager.incrementEarnings(connection, "offline-player", EarningsCurrency.SHARDS, 4);
        }

        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT total_money_earned, total_shards_earned FROM player_data WHERE uuid='offline-player'")) {
            assertEquals(13.25, result.getDouble(1));
            assertEquals(4, result.getDouble(2));
        }
    }

    @Test
    void failedPersistenceDoesNotPublishToCache() {
        AtomicInteger published = new AtomicInteger();

        assertFalse(PlayerDataManager.persistThenPublish(() -> false, published::incrementAndGet));
        assertEquals(0, published.get());
    }

    @Test
    void concurrentIncrementsDoNotLoseEarnings(@TempDir Path tempDir) throws Exception {
        String url = "jdbc:sqlite:" + tempDir.resolve("concurrent.db");
        try (Connection connection = DriverManager.getConnection(url)) { createTable(connection); }
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> incrementMany(url));
            var second = executor.submit(() -> incrementMany(url));
            first.get();
            second.get();
        }
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT total_money_earned FROM player_data WHERE uuid='p'")) {
            assertEquals(200, result.getDouble(1));
        }
    }

    private static void incrementMany(String url) {
        for (int index = 0; index < 100; index++) {
            try (Connection connection = DriverManager.getConnection(url)) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("PRAGMA busy_timeout=5000");
                }
                DatabaseManager.incrementEarnings(connection, "p", EarningsCurrency.MONEY, 1);
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        }
    }

    private static void createTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE player_data (uuid TEXT PRIMARY KEY, "
                    + "total_money_earned REAL DEFAULT 0, total_shards_earned REAL DEFAULT 0)");
        }
    }
}
