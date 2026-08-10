package me.mtynnn.valerinutils.crimson;

import com.nexomc.nexo.api.NexoBlocks;
import org.bukkit.block.Block;

final class NexoCrimsonBlockLookup {
    private NexoCrimsonBlockLookup() {
    }

    static String id(Block block) {
        var mechanic = NexoBlocks.customBlockMechanic(block);
        return mechanic == null ? null : mechanic.getItemID();
    }
}
