package me.mtynnn.valerinutils.modules.utility;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Locale;

final class UtilitySellCommand {

    private final UtilityModule module;

    UtilitySellCommand(UtilityModule module) {
        this.module = module;
    }

    void execute(Player player, String[] args) {
        if (!module.checkStatus(player, "sell")) {
            return;
        }
        if (args.length == 0) {
            player.sendMessage(module.getMessage("sell-usage"));
            return;
        }

        Economy economy = getEconomy();
        if (economy == null) {
            player.sendMessage(module.getMessage("sell-economy-missing"));
            return;
        }

        FileConfiguration prices = module.plugin().getConfigManager().getConfig("sellprice");
        if (prices == null || !prices.getBoolean("enabled", true)) {
            player.sendMessage(module.getMessage("sell-disabled"));
            return;
        }

        String mode = args[0].toLowerCase(Locale.ROOT);
        SellResult result = switch (mode) {
            case "hand" -> sellHand(player, prices);
            case "inventory", "inv", "all" -> sellInventory(player, prices);
            default -> {
                player.sendMessage(module.getMessage("sell-usage"));
                yield SellResult.empty();
            }
        };

        if (result.total() <= 0D) {
            player.sendMessage(module.getMessage("sell-nothing"));
            return;
        }

        economy.depositPlayer(player, result.total());
        player.sendMessage(module.getMessage("sell-success")
                .replace("%items%", String.valueOf(result.items()))
                .replace("%amount%", formatMoney(result.total())));
    }

    private SellResult sellHand(Player player, FileConfiguration prices) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (isEmpty(item)) {
            return SellResult.empty();
        }
        double unitPrice = getSellPrice(prices, item.getType());
        if (unitPrice <= 0D) {
            return SellResult.empty();
        }
        int amount = item.getAmount();
        double total = unitPrice * amount;
        player.getInventory().setItemInMainHand(null);
        return new SellResult(amount, total);
    }

    private SellResult sellInventory(Player player, FileConfiguration prices) {
        int soldItems = 0;
        double total = 0D;
        PlayerInventory inv = player.getInventory();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack stack = inv.getItem(slot);
            if (isEmpty(stack)) {
                continue;
            }
            double unitPrice = getSellPrice(prices, stack.getType());
            if (unitPrice <= 0D) {
                continue;
            }
            soldItems += stack.getAmount();
            total += unitPrice * stack.getAmount();
            inv.setItem(slot, null);
        }
        return soldItems <= 0 ? SellResult.empty() : new SellResult(soldItems, total);
    }

    private double getSellPrice(FileConfiguration prices, Material material) {
        if (material == null) {
            return 0D;
        }
        String key = material.name();
        double direct = prices.getDouble("prices." + key, Double.NaN);
        if (!Double.isNaN(direct)) {
            return direct;
        }

        String lower = key.toLowerCase(Locale.ROOT);
        double lowerValue = prices.getDouble("prices." + lower, Double.NaN);
        if (!Double.isNaN(lowerValue)) {
            return lowerValue;
        }

        String compact = lower.replace("_", "");
        double compactValue = prices.getDouble("prices." + compact, Double.NaN);
        if (!Double.isNaN(compactValue)) {
            return compactValue;
        }

        return 0D;
    }

    private Economy getEconomy() {
        var rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private String formatMoney(double amount) {
        return String.format(Locale.US, "%,.2f", amount);
    }

    private record SellResult(int items, double total) {
        static SellResult empty() {
            return new SellResult(0, 0D);
        }
    }
}
