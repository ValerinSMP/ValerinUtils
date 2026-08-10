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

    private static void createTable(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE player_data (uuid TEXT PRIMARY KEY, "
                    + "total_money_earned REAL DEFAULT 0, total_shards_earned REAL DEFAULT 0)");
        }
    }
}
