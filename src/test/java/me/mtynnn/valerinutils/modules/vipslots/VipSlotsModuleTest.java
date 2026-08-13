package me.mtynnn.valerinutils.modules.vipslots;

import org.bukkit.event.player.PlayerLoginEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VipSlotsModuleTest {
    @Test
    void bypassesOnlyFullServerResult() {
        assertTrue(VipSlotsModule.shouldBypass(PlayerLoginEvent.Result.KICK_FULL, true));
        assertFalse(VipSlotsModule.shouldBypass(PlayerLoginEvent.Result.KICK_BANNED, true));
        assertFalse(VipSlotsModule.shouldBypass(PlayerLoginEvent.Result.KICK_WHITELIST, true));
        assertFalse(VipSlotsModule.shouldBypass(PlayerLoginEvent.Result.KICK_FULL, false));
    }
}
