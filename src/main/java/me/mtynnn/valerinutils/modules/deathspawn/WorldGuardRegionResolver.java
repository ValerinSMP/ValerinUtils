package me.mtynnn.valerinutils.modules.deathspawn;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.HashSet;
import java.util.Set;

final class WorldGuardRegionResolver {
    private WorldGuardRegionResolver() {
    }

    static boolean isInside(Location location, String expectedRegion) {
        ApplicableRegionSet regions = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .createQuery()
                .getApplicableRegions(BukkitAdapter.adapt(location));
        Set<String> regionIds = new HashSet<>();
        for (ProtectedRegion region : regions) {
            regionIds.add(region.getId());
        }
        return RegionIdMatcher.matches(expectedRegion, regionIds);
    }

    static Set<String> regionIds(World world) {
        var manager = WorldGuard.getInstance()
                .getPlatform()
                .getRegionContainer()
                .get(BukkitAdapter.adapt(world));
        return manager == null ? Set.of() : Set.copyOf(manager.getRegions().keySet());
    }
}
