package me.mtynnn.valerinutils.worldguard;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Mob;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;

public final class WorldGuardMobSpawnFlags implements Listener {
    public static final String HOSTILE_FLAG_NAME = "valerin-hostile-mob-spawning";
    public static final String PASSIVE_FLAG_NAME = "valerin-passive-mob-spawning";

    private final StateFlag hostileFlag;
    private final StateFlag passiveFlag;

    private WorldGuardMobSpawnFlags(StateFlag hostileFlag, StateFlag passiveFlag) {
        this.hostileFlag = hostileFlag;
        this.passiveFlag = passiveFlag;
    }

    public static Object register(ValerinUtils plugin) {
        return new WorldGuardMobSpawnFlags(
                register(plugin, HOSTILE_FLAG_NAME),
                register(plugin, PASSIVE_FLAG_NAME));
    }

    public static Listener listener(Object registered) {
        if (!(registered instanceof WorldGuardMobSpawnFlags listener)) {
            throw new IllegalArgumentException("Expected registered mob spawn flags");
        }
        return listener;
    }

    private static StateFlag register(ValerinUtils plugin, String name) {
        var registry = WorldGuard.getInstance().getFlagRegistry();
        StateFlag candidate = new StateFlag(name, true);
        try {
            registry.register(candidate);
            return candidate;
        } catch (FlagConflictException conflict) {
            Flag<?> existing = registry.get(name);
            if (existing instanceof StateFlag stateFlag) {
                plugin.getLogger().info("[MobSpawnFlags] Reusing existing StateFlag '" + name + "'.");
                return stateFlag;
            }
            plugin.getLogger().severe("[MobSpawnFlags] Flag '" + name
                    + "' already exists with an incompatible type; only this flag is disabled.");
            return null;
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent event) {
        MobKind kind = kind(event.getEntity() instanceof Mob, event.getEntity() instanceof Enemy);
        StateFlag flag = kind == MobKind.HOSTILE ? hostileFlag : kind == MobKind.PASSIVE ? passiveFlag : null;
        if (flag == null) {
            return;
        }

        StateFlag.State state = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery().queryState(
                BukkitAdapter.adapt(event.getLocation()), null, flag);
        if (state == StateFlag.State.DENY) {
            event.setCancelled(true);
        }
    }

    static MobKind kind(boolean mob, boolean enemy) {
        if (!mob) return MobKind.NONE;
        return enemy ? MobKind.HOSTILE : MobKind.PASSIVE;
    }

    enum MobKind {
        HOSTILE,
        PASSIVE,
        NONE
    }
}
