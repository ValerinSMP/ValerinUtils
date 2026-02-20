package me.mtynnn.valerinutils.modules.kits;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

final class KitsMenuHolder implements InventoryHolder {
    private final int page;

    KitsMenuHolder(int page) {
        this.page = page;
    }

    int getPage() {
        return page;
    }

    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("Virtual holder");
    }
}
