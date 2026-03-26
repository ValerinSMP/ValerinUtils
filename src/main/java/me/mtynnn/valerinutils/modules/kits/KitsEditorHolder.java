package me.mtynnn.valerinutils.modules.kits;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

final class KitsEditorHolder implements InventoryHolder {
    private final String kitName;

    KitsEditorHolder(String kitName) {
        this.kitName = kitName;
    }

    public String getKitName() {
        return kitName;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return null;
    }
}
