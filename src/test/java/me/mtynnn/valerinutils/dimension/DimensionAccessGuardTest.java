package me.mtynnn.valerinutils.dimension;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DimensionAccessGuardTest {
    @Test
    void permissionOrConfiguredOperatorAccessesDimension() {
        assertTrue(DimensionAccessGuard.allows(false, true, false));
        assertTrue(DimensionAccessGuard.allows(true, false, true));
        assertFalse(DimensionAccessGuard.allows(true, false, false));
        assertFalse(DimensionAccessGuard.allows(false, false, true));
    }

    @Test
    void configurationMapsOnlyRestrictedWorlds() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("enabled", true);
        yaml.set("fallback-server", "survival");
        yaml.set("fallback-delay-ticks", -5);
        yaml.set("dimensions.crimson.worlds", java.util.List.of("world_crimson"));
        yaml.set("dimensions.crimson.permission", "huskhomes.warp.crimson");

        DimensionAccessGuard.Settings settings = DimensionAccessGuard.Settings.parse(yaml);
        assertEquals("crimson", settings.dimension("WORLD_CRIMSON").id());
        assertNull(settings.dimension("world"));
        assertEquals("survival", settings.fallbackServer());
        assertEquals(1, settings.fallbackDelayTicks());
    }

    @Test
    void fallbackRunsAfterTheCurrentEventCallback() {
        AtomicInteger transfers = new AtomicInteger();
        AtomicLong scheduledDelay = new AtomicLong();
        AtomicReference<Runnable> scheduledTask = new AtomicReference<>();

        DimensionAccessGuard.deferFallback(0, transfers::incrementAndGet, (delay, task) -> {
            scheduledDelay.set(delay);
            scheduledTask.set(task);
        });

        assertEquals(0, transfers.get());
        assertEquals(1, scheduledDelay.get());
        scheduledTask.get().run();
        assertEquals(1, transfers.get());
    }

    @Test
    void teleportFallbackIgnoresSourceWorldButJoinFallbackDoesNot() {
        assertTrue(DimensionAccessGuard.shouldTransfer(false, false, false));
        assertFalse(DimensionAccessGuard.shouldTransfer(true, false, false));
        assertFalse(DimensionAccessGuard.shouldTransfer(false, false, true));
    }

    @Test
    void invalidReloadKeepsLastValidSnapshot() {
        YamlConfiguration valid = new YamlConfiguration();
        valid.set("enabled", true);
        valid.set("fallback-server", "survival");
        DimensionAccessGuard.Settings previous = DimensionAccessGuard.Settings.parse(valid);

        YamlConfiguration invalid = new YamlConfiguration();
        invalid.set("enabled", true);
        invalid.set("fallback-server", "  ");

        assertSame(previous, DimensionAccessGuard.Settings.updated(previous, invalid));
    }
}
