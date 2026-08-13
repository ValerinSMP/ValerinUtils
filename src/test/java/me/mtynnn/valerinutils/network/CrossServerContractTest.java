package me.mtynnn.valerinutils.network;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class CrossServerContractTest {
    @Test
    void localModeNeedsNoNetworkConfiguration() {
        assertFalse(CrossServerConfig.parse(new YamlConfiguration()).enabled());
    }

    @Test
    void crossModeRequiresAllIdentityAndStorageFields() {
        YamlConfiguration config = new YamlConfiguration();
        config.set("enabled", true);
        assertThrows(IllegalArgumentException.class, () -> CrossServerConfig.parse(config));
        config.set("network-id", "valerin");
        config.set("server-id", "survival-1");
        config.set("mysql.host", "127.0.0.1");
        config.set("mysql.database", "utils");
        config.set("mysql.username", "user");
        config.set("mysql.password", "secret");
        config.set("redis.host", "127.0.0.1");
        assertTrue(CrossServerConfig.parse(config).enabled());
    }

    @Test
    void envelopeValidatesNetworkTypeSizeUuidAndLoopback() {
        CrossServerConfig config = configured();
        NetworkEnvelope envelope = NetworkEnvelope.create(config, "BROADCAST", "hello");
        NetworkEnvelope decoded = NetworkEnvelope.decode(envelope.encode(), config);
        assertNotNull(decoded);
        assertEquals("hello", decoded.payload());
        assertTrue(decoded.loopback(config));
        CrossServerConfig other = new CrossServerConfig(true, "other", "sync-1", "x", "u", "p", "r", 1, "", 0, 1);
        assertNull(NetworkEnvelope.decode(envelope.encode(), other));
    }

    @Test
    void envelopeAcceptsPublicCrossServerFeatures() {
        CrossServerConfig config = configured();
        for (String type : java.util.List.of("BROADCAST", "HELPOP", "PRESENCE", "REMOTE_ACTION")) {
            assertNotNull(NetworkEnvelope.decode(NetworkEnvelope.create(config, type, "payload").encode(), config));
        }
    }

    @Test
    void eventPayloadRejectsMalformedAndPreservesRunIdentity() {
        assertNull(CrossServerService.parseEventPayload("bad"));
        java.util.UUID run = java.util.UUID.randomUUID();
        CrossServerService.GlobalEvent state = CrossServerService.parseEventPayload(
                "xp|" + run + "|true|10|20|30");
        assertEquals(run, state.runId());
        assertTrue(state.active());
    }

    @Test
    void duplicateAndLoopbackEnvelopesAreIgnored() {
        CrossServerConfig local = configured();
        CrossServerConfig remote = new CrossServerConfig(true, "valerin", "sync-1", "x", "u", "p", "r", 1, "", 0, 1);
        NetworkEnvelope envelope = NetworkEnvelope.create(remote, "BROADCAST", "hello");
        java.util.Map<java.util.UUID, Boolean> seen = new java.util.HashMap<>();
        assertTrue(CrossServerService.acceptEnvelope(envelope, local, seen));
        assertFalse(CrossServerService.acceptEnvelope(envelope, local, seen));
        assertFalse(CrossServerService.acceptEnvelope(NetworkEnvelope.create(local, "BROADCAST", "x"), local, seen));
    }

    @Test
    void eventCommandsApplyOncePerBackendAndRun(@TempDir java.nio.file.Path directory) throws Exception {
        String url = "jdbc:sqlite:" + directory.resolve("events.db");
        try (java.sql.Connection connection = java.sql.DriverManager.getConnection(url);
             java.sql.Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE event_applications(run_id TEXT,server_id TEXT,phase TEXT,PRIMARY KEY(run_id,server_id,phase))");
            java.util.UUID run = java.util.UUID.randomUUID();
            assertTrue(CrossServerService.claimApplication(connection, run, "survival", "START", false));
            assertFalse(CrossServerService.claimApplication(connection, run, "survival", "START", false));
            assertTrue(CrossServerService.claimApplication(connection, run, "sync", "START", false));
        }
    }

    @Test
    void redisOutagePreventsGlobalEventStart() {
        assertFalse(CrossServerService.globalStartAllowed(false, false));
        assertFalse(CrossServerService.globalStartAllowed(true, false));
        assertTrue(CrossServerService.globalStartAllowed(true, true));
    }

    private static CrossServerConfig configured() {
        return new CrossServerConfig(true, "valerin", "survival-1", "jdbc:mysql://db/utils",
                "user", "secret", "redis", 6379, "", 0, 2000);
    }
}
