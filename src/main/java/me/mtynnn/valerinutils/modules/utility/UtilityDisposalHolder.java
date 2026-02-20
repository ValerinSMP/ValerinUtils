package me.mtynnn.valerinutils.modules.utility;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

final class UtilityDisposalHolder implements InventoryHolder {
    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }
}
