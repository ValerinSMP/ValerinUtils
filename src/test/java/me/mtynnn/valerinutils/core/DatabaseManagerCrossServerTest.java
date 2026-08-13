package me.mtynnn.valerinutils.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseManagerCrossServerTest {
    @Test
    void concurrentCodeClaimHasOneWinner(@TempDir Path directory) throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("codes.db");
        try (Connection connection = DriverManager.getConnection(url); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE player_codes(uuid TEXT,code TEXT,PRIMARY KEY(uuid,code))");
        }
        Callable<Boolean> claim = () -> {
            try (Connection connection = DriverManager.getConnection(url)) {
                return DatabaseManager.tryClaimCode(connection, "player", "welcome", false);
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(claim);
            var second = executor.submit(claim);
            assertNotEquals(first.get(), second.get());
        }
    }

    @Test
    void crossServerJdbcIsDeferredOffBukkitMain() {
        assertTrue(DatabaseManager.shouldDeferJdbc(true, true));
        assertFalse(DatabaseManager.shouldDeferJdbc(false, true));
        assertFalse(DatabaseManager.shouldDeferJdbc(true, false));
    }
}
