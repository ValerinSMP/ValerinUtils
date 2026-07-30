package me.mtynnn.valerinutils.modules.deathspawn;

import java.util.Collection;

final class RegionIdMatcher {
    private RegionIdMatcher() {
    }

    static boolean matches(String expectedRegion, Collection<String> applicableRegions) {
        if (expectedRegion == null || expectedRegion.isBlank()) {
            return true;
        }
        return applicableRegions.stream().anyMatch(expectedRegion::equalsIgnoreCase);
    }
}
