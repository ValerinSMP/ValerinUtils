package me.mtynnn.valerinutils.commands;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.ModuleManager;
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
            return handleReload(sender, args);
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
                        "%prefix%&cModulo desconocido: &e" + moduleId + "&c. Usa tab para ver opciones."));
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
            plugin.getLogger().info("[DebugCommand] " + sender.getName() + " set debug " + moduleId + "=" + newValue);
            return true;
        }

        sender.sendMessage(plugin.getMessage("valerinutils-usage"));
        return true;
    }

    private boolean handleReload(CommandSender sender, String[] args) {
        String target = args.length >= 2 ? args[1].toLowerCase() : "all";

        if (target.equals("all")) {
            plugin.getConfigManager().reloadConfigs();
            plugin.updateConfig();
            plugin.getModuleManager().reloadAll();
            sender.sendMessage(plugin.getMessage("valerinutils-reload-ok"));
            return true;
        }

        Set<String> known = plugin.getModuleManager().getRegisteredModuleIds();
        if (!known.contains(target)) {
            sender.sendMessage(message("valerinutils-reload-unknown",
                    "%prefix%<red>Modulo desconocido: <yellow>%module%<red>.")
                    .replace("%module%", target));
            return true;
        }

        if (!plugin.getConfigManager().reloadConfig(target)) {
            sender.sendMessage(message("valerinutils-reload-config-missing",
                    "%prefix%<red>No se pudo recargar la config de <yellow>%module%<red>.")
                    .replace("%module%", target));
            return true;
        }

        if (target.equals("utility") && !plugin.getConfigManager().reloadConfig("sellprice")) {
            sender.sendMessage(message("valerinutils-reload-config-missing",
                    "%prefix%<red>No se pudo recargar la config de <yellow>%module%<red>.")
                    .replace("%module%", "sellprice"));
            return true;
        }

        ModuleManager.ReloadResult result = plugin.getModuleManager().reloadModule(target);
        switch (result) {
            case RELOADED -> sender.sendMessage(message("valerinutils-reload-module-ok",
                    "%prefix%<green>Modulo <yellow>%module% <green>recargado correctamente.")
                    .replace("%module%", target));
            case DISABLED_BY_CONFIG -> sender.sendMessage(message("valerinutils-reload-module-disabled",
                    "%prefix%<yellow>Modulo <gold>%module% <yellow>recargado y dejado desactivado por config.")
                    .replace("%module%", target));
            case UNKNOWN_MODULE -> sender.sendMessage(message("valerinutils-reload-unknown",
                    "%prefix%<red>Modulo desconocido: <yellow>%module%<red>.")
                    .replace("%module%", target));
            case FAILED -> sender.sendMessage(plugin.translateColors("%prefix%<red>No se pudo recargar el modulo <yellow>"
                    + target + "<red>. Revisa consola."));
        }
        return true;
    }

    private String message(String key, String fallback) {
        return plugin.translateColors(plugin.getConfigManager().getConfig("settings")
                .getString("messages." + key, fallback));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (!sender.hasPermission("valerinutils.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String partial = args[0].toLowerCase();
            if ("reload".startsWith(partial)) {
                completions.add("reload");
            }
            if ("debug".startsWith(partial)) {
                completions.add("debug");
            }
            return completions;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            String partial = args[1].toLowerCase();
            List<String> completions = new ArrayList<>();
            if ("all".startsWith(partial)) {
                completions.add("all");
            }
            completions.addAll(plugin.getModuleManager().getRegisteredModuleIds().stream()
                    .filter(m -> m.startsWith(partial))
                    .toList());
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
