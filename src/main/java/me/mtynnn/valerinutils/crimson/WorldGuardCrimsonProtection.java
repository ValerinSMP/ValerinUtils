package me.mtynnn.valerinutils.crimson;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.Set;
import java.util.function.Function;

public final class WorldGuardCrimsonProtection implements CrimsonProtectionHook {
    static final String FLAG_NAME = "valerin-crimson-protection";
    private static final CrimsonProtectionSettings DISABLED = new CrimsonProtectionSettings(
            false, Set.of(), Set.of(), false, "valerinutils.crimsonprotection.bypass");

    private final ValerinUtils plugin;
    private final StateFlag flag;
    private final CrimsonProtectionSnapshot snapshot = new CrimsonProtectionSnapshot(DISABLED);
    private Function<Block, String> nexoLookup = block -> null;
    private boolean nexoAvailable;
    private boolean nexoWarningLogged;

    private WorldGuardCrimsonProtection(ValerinUtils plugin, StateFlag flag) {
        this.plugin = plugin;
        this.flag = flag;
    }

    public static Object registerFlag(ValerinUtils plugin) {
        var registry = WorldGuard.getInstance().getFlagRegistry();
        StateFlag candidate = new StateFlag(FLAG_NAME, false);
        try {
            registry.register(candidate);
            return candidate;
        } catch (FlagConflictException conflict) {
            StateFlag existing = compatibleStateFlag(registry.get(FLAG_NAME));
            if (existing != null) {
                plugin.getLogger().info("[CrimsonProtection] Reusing existing StateFlag '" + FLAG_NAME + "'.");
                return existing;
            }
            plugin.getLogger().severe("[CrimsonProtection] Flag '" + FLAG_NAME
                    + "' already exists with an incompatible type; only this feature is disabled.");
            return null;
        }
    }

    static StateFlag compatibleStateFlag(Flag<?> existing) {
        return existing instanceof StateFlag stateFlag ? stateFlag : null;
    }

    public static CrimsonProtectionHook create(ValerinUtils plugin, Object registeredFlag) {
        if (!(registeredFlag instanceof StateFlag stateFlag)) {
            throw new IllegalArgumentException("Expected a StateFlag");
        }
        return new WorldGuardCrimsonProtection(plugin, stateFlag);
    }

    @Override
    public void reload(FileConfiguration root) {
        if (!snapshot.reload(root == null ? null : root.getConfigurationSection("crimson-protection"))) {
            plugin.getLogger().severe("[CrimsonProtection] Invalid crimson-protection settings; keeping the last valid snapshot.");
            return;
        }
        refreshNexoLookup();
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        CrimsonProtectionSettings current = snapshot.current();
        String world = event.getBlock().getWorld().getName();
        boolean bypass = event.getPlayer().hasPermission(current.bypassPermission());
        if (!current.inScope(world, bypass) || !flagAllows(event.getBlock(), event.getPlayer())) {
            return;
        }

        String nexoId = null;
        if (nexoAvailable) {
            try {
                nexoId = nexoLookup.apply(event.getBlock());
            } catch (LinkageError | RuntimeException error) {
                warnNexoUnavailable("Nexo block lookup failed: " + error.getMessage());
            }
        } else {
            warnNexoUnavailable("Nexo is not enabled");
        }

        if (current.shouldCancelBreak(world, true, bypass, nexoId)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        CrimsonProtectionSettings current = snapshot.current();
        String world = event.getBlock().getWorld().getName();
        boolean bypass = event.getPlayer().hasPermission(current.bypassPermission());
        if (current.inScope(world, bypass)
                && flagAllows(event.getBlock(), event.getPlayer())
                && current.shouldCancelPlace(world, true, bypass)) {
            event.setCancelled(true);
        }
    }

    private boolean flagAllows(Block block, org.bukkit.entity.Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return false;
        }
        var query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        return query.testState(
                BukkitAdapter.adapt(block.getLocation()),
                WorldGuardPlugin.inst().wrapPlayer(player),
                flag);
    }

    private void refreshNexoLookup() {
        nexoAvailable = Bukkit.getPluginManager().isPluginEnabled("Nexo");
        if (!nexoAvailable) {
            nexoLookup = block -> null;
            return;
        }
        try {
            nexoLookup = NexoCrimsonBlockLookup::id;
        } catch (LinkageError error) {
            nexoAvailable = false;
            nexoLookup = block -> null;
            warnNexoUnavailable("Nexo 1.26.0 API is unavailable: " + error.getMessage());
        }
    }

    private void warnNexoUnavailable(String reason) {
        if (nexoWarningLogged) {
            return;
        }
        nexoWarningLogged = true;
        plugin.getLogger().warning("[CrimsonProtection] " + reason
                + "; mining remains denied inside active protection regions.");
    }
}
