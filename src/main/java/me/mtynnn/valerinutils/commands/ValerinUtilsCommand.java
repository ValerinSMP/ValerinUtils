package me.mtynnn.valerinutils.commands;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ValerinUtilsCommand implements CommandExecutor, TabCompleter {

    private final ValerinUtils plugin;

    public ValerinUtilsCommand(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission("valerinutils.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(plugin.getMessage("valerinutils-usage"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            // 1. Reload config files first
            plugin.getConfigManager().reloadConfigs();
            plugin.updateConfig();

            // 2. Reload all modules (disable → re-enable based on config)
            plugin.getModuleManager().reloadAll();

            sender.sendMessage(plugin.getMessage("valerinutils-reload-ok"));
            return true;
        }

        if (args[0].equalsIgnoreCase("debug")) {
            if (args.length < 2) {
                sender.sendMessage(plugin.translateColors("%prefix%&cUso: /valerinutils debug <modulo> [on|off|toggle]"));
                return true;
            }

            String moduleId = args[1].toLowerCase();
            Set<String> known = plugin.getModuleManager().getRegisteredModuleIds();
            if (!known.contains(moduleId)) {
                sender.sendMessage(plugin.translateColors(
                        "%prefix%&cMódulo desconocido: &e" + moduleId + "&c. Usa tab para ver opciones."));
                return true;
            }

            boolean newValue;
            if (args.length >= 3) {
                String mode = args[2].toLowerCase();
                switch (mode) {
                    case "on", "true", "enable" -> {
                        plugin.setModuleDebugEnabled(moduleId, true);
                        newValue = true;
                    }
                    case "off", "false", "disable" -> {
                        plugin.setModuleDebugEnabled(moduleId, false);
                        newValue = false;
                    }
                    case "toggle" -> newValue = plugin.toggleModuleDebug(moduleId);
                    default -> {
                        sender.sendMessage(plugin.translateColors(
                                "%prefix%&cUso: /valerinutils debug <modulo> [on|off|toggle]"));
                        return true;
                    }
                }
            } else {
                newValue = plugin.toggleModuleDebug(moduleId);
            }

            sender.sendMessage(plugin.translateColors("%prefix%&7Debug de &e" + moduleId + "&7: "
                    + (newValue ? "&aACTIVADO" : "&cDESACTIVADO")));
            return true;
        }

        sender.sendMessage(plugin.getMessage("valerinutils-usage"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (!sender.hasPermission("valerinutils.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if ("reload".startsWith(args[0].toLowerCase())) {
                completions.add("reload");
            }
            if ("debug".startsWith(args[0].toLowerCase())) {
                completions.add("debug");
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("debug")) {
            String partial = args[1].toLowerCase();
            return plugin.getModuleManager().getRegisteredModuleIds().stream()
                    .filter(m -> m.startsWith(partial))
                    .toList();
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("debug")) {
            List<String> modes = List.of("toggle", "on", "off");
            String partial = args[2].toLowerCase();
            return modes.stream().filter(m -> m.startsWith(partial)).toList();
        }

        return Collections.emptyList();
    }
}
