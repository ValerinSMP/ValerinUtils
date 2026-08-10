package me.mtynnn.valerinutils.crimson;

import org.junit.jupiter.api.Test;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrimsonProtectionSettingsTest {
    private static final CrimsonProtectionSettings ENABLED = new CrimsonProtectionSettings(
            true, Set.of("world_crimson"), Set.of("CrImSoN_Ore"), true,
            "valerinutils.crimsonprotection.bypass");

    @Test
    void breakIsNoOpOutsideTheActiveScope() {
        assertFalse(new CrimsonProtectionSettings(false, Set.of("world_crimson"), Set.of("crimson_ore"), true,
                "permission").shouldCancelBreak("world_crimson", true, false, null));
        assertFalse(ENABLED.shouldCancelBreak("world", true, false, null));
        assertFalse(ENABLED.shouldCancelBreak("world_crimson", false, false, null));
        assertFalse(ENABLED.shouldCancelBreak("world_crimson", true, true, null));
    }

    @Test
    void activeRegionOnlyAllowsConfiguredNexoBlocks() {
        assertFalse(ENABLED.shouldCancelBreak("world_crimson", true, false, "CRIMSON_ORE"));
        assertTrue(ENABLED.shouldCancelBreak("world_crimson", true, false, null));
        assertTrue(ENABLED.shouldCancelBreak("world_crimson", true, false, "stone"));
    }

    @Test
    void placeHonorsDenyPlaceAndScope() {
        assertTrue(ENABLED.shouldCancelPlace("world_crimson", true, false));
        assertFalse(ENABLED.shouldCancelPlace("world_crimson", false, false));
        assertFalse(ENABLED.shouldCancelPlace("world_crimson", true, true));
        assertFalse(new CrimsonProtectionSettings(true, Set.of("world_crimson"), Set.of("crimson_ore"), false,
                "permission").shouldCancelPlace("world_crimson", true, false));
    }

    @Test
    void parserRejectsInvalidConfigAndNormalizesValidLists() {
        YamlConfiguration valid = new YamlConfiguration();
        valid.set("enabled", true);
        valid.set("worlds", java.util.List.of(" World_Crimson "));
        valid.set("allowed-break-ids", java.util.List.of(" CRIMSON_ORE "));
        valid.set("deny-place", true);
        valid.set("bypass-permission", " custom.bypass ");

        CrimsonProtectionSettings parsed = CrimsonProtectionSettings.parse(valid).orElseThrow();
        assertFalse(parsed.shouldCancelBreak("world_crimson", true, false, "crimson_ore"));
        assertTrue(parsed.worlds().contains("world_crimson"));
        assertTrue(parsed.allowedBreakIds().contains("crimson_ore"));

        valid.set("worlds", "not-a-list");
        assertTrue(CrimsonProtectionSettings.parse(valid).isEmpty());
    }

    @Test
    void invalidReloadKeepsTheLastValidSnapshot() {
        CrimsonProtectionSnapshot snapshot = new CrimsonProtectionSnapshot(ENABLED);
        YamlConfiguration invalid = new YamlConfiguration();
        invalid.set("enabled", true);
        invalid.set("worlds", "not-a-list");

        assertFalse(snapshot.reload(invalid));
        assertTrue(snapshot.current().shouldCancelPlace("world_crimson", true, false));
    }
}
