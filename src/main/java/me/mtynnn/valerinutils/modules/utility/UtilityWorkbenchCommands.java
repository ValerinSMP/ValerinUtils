package me.mtynnn.valerinutils.modules.utility;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

final class UtilityWorkbenchCommands {

    private final UtilityModule module;

    UtilityWorkbenchCommands(UtilityModule module) {
        this.module = module;
    }

    void openStandardUi(Player player, String configKey) {
        if (!module.checkStatus(player, configKey)) {
            return;
        }

        module.playSound(player, configKey);
        switch (configKey) {
            case "craft" -> player.openWorkbench(null, true);
            case "enderchest" -> player.openInventory(player.getEnderChest());
            case "anvil" -> player.openAnvil(null, true);
            case "smithing" -> player.openSmithingTable(null, true);
            case "cartography" -> player.openCartographyTable(null, true);
            case "grindstone" -> player.openGrindstone(null, true);
            case "loom" -> player.openLoom(null, true);
            case "stonecutter" -> player.openStonecutter(null, true);
            default -> {
            }
        }
    }

    void openDisposal(Player player) {
        if (!module.checkStatus(player, "disposal")) {
            return;
        }

        String title = module.getConfig().getString("messages.disposal-title", "&8Basurero");
        Inventory inv = Bukkit.createInventory(new UtilityDisposalHolder(), 54, module.plugin().translateColors(title));
        player.openInventory(inv);
        module.playSound(player, "disposal");
    }
}
