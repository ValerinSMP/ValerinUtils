package me.mtynnn.valerinutils.modules.deathspawn;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionIdMatcherTest {
    @Test
    void matchesRegionIgnoringCase() {
        assertTrue(RegionIdMatcher.matches("koth_dragon", List.of("__global__", "KOTH_DRAGON")));
    }

    @Test
    void rejectsDifferentRegion() {
        assertFalse(RegionIdMatcher.matches("koth_dragon", List.of("__global__", "koth_castle")));
    }

    @Test
    void acceptsRulesWithoutRegionRestriction() {
        assertTrue(RegionIdMatcher.matches("", List.of()));
    }
}
