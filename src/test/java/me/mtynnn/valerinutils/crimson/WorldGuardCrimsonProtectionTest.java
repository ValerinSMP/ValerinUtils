package me.mtynnn.valerinutils.crimson;

import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.StringFlag;
import org.junit.jupiter.api.Test;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldGuardCrimsonProtectionTest {
    @Test
    void onlyExistingStateFlagIsReusable() {
        StateFlag stateFlag = new StateFlag(WorldGuardCrimsonProtection.FLAG_NAME, false);
        assertSame(stateFlag, WorldGuardCrimsonProtection.compatibleStateFlag(stateFlag));
        assertNull(WorldGuardCrimsonProtection.compatibleStateFlag(
                new StringFlag(WorldGuardCrimsonProtection.FLAG_NAME)));
    }

    @Test
    void listenersNeverReceiveAlreadyCancelledEvents() throws Exception {
        for (var method : new java.lang.reflect.Method[] {
                WorldGuardCrimsonProtection.class.getDeclaredMethod("onBreak", BlockBreakEvent.class),
                WorldGuardCrimsonProtection.class.getDeclaredMethod("onPlace", BlockPlaceEvent.class)}) {
            EventHandler handler = method.getAnnotation(EventHandler.class);
            assertNotNull(handler);
            assertTrue(handler.ignoreCancelled());
        }
    }
}
