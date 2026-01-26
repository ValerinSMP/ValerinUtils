package me.mtynnn.valerinutils.commands;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.modules.menuitem.MenuItemModule;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MenuItemCommand implements CommandExecutor, TabCompleter {

    private final ValerinUtils plugin;
    private final MenuItemModule module;

    public MenuItemCommand(ValerinUtils plugin, MenuItemModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessage("only-players"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(plugin.getMessage("menuitem-usage"));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "on" -> {
                boolean success = module.setDisabled(player, false);
                if (success) {
                    sender.sendMessage(plugin.getMessage("menuitem-on"));
                } else {
                    sender.sendMessage(plugin.getMessage("menuitem-slot-occupied"));
                }
            }
            case "off" -> {
                module.setDisabled(player, true);
                sender.sendMessage(plugin.getMessage("menuitem-off"));
            }
            case "toggle" -> {
                boolean disabled = module.isDisabled(player);
                boolean success = module.setDisabled(player, !disabled);
                if (success) {
                    sender.sendMessage(!disabled
                            ? plugin.getMessage("menuitem-toggled-off")
                            : plugin.getMessage("menuitem-toggled-on"));
                } else {
                    sender.sendMessage(plugin.getMessage("menuitem-slot-occupied"));
                }
            }
            default -> sender.sendMessage(plugin.getMessage("menuitem-usage"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = Arrays.asList("on", "off", "toggle");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : base) {
                if (s.startsWith(prefix))
                    out.add(s);
            }
            return out;
        }
        return List.of();
    }
}
