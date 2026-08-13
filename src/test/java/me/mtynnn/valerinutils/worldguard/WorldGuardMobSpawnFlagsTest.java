package me.mtynnn.valerinutils.worldguard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldGuardMobSpawnFlagsTest {
    @Test
    void separatesHostilePassiveAndNonMobEntities() {
        assertEquals(WorldGuardMobSpawnFlags.MobKind.HOSTILE,
                WorldGuardMobSpawnFlags.kind(true, true));
        assertEquals(WorldGuardMobSpawnFlags.MobKind.PASSIVE,
                WorldGuardMobSpawnFlags.kind(true, false));
        assertEquals(WorldGuardMobSpawnFlags.MobKind.NONE,
                WorldGuardMobSpawnFlags.kind(false, false));
    }
}
