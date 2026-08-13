package me.mtynnn.valerinutils.commands;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.ModuleManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import me.mtynnn.valerinutils.network.StorageMigrator;

public class ValerinUtilsCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private final ValerinUtils plugin;
    private final ValerinUtilsHelp help;

    public ValerinUtilsCommand(ValerinUtils plugin) {
        this.plugin = plugin;
        this.help = new ValerinUtilsHelp(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean adminRoot = command.getName().equalsIgnoreCase("valerinutilsadmin");

        if (!adminRoot) {
            if (args.length == 0) {
                help.send(sender, null);
                return true;
            }
            if (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("ayuda")) {
                help.send(sender, args.length >= 2 ? args[1] : null);
                return true;
            }
            if (args[0].equalsIgnoreCase("about") || args[0].equalsIgnoreCase("info")) {
                sendAbout(sender);
                return true;
            }
            sender.sendMessage(plugin.messages().component(plugin.messages().settings("unknown-subcommand",
                    "<#FF3300>Subcomando desconocido: <white>'%input%'</white>. "
                            + "Usa <#FFD166>/valerinutils help</#FFD166>.")
                    .replace("%input%", args[0])));
            return true;
        }

        if (!sender.hasPermission("valerinutils.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("ayuda")) {
            sendAdminHelp(sender);
            return true;
        }
        if (args[0].equalsIgnoreCase("about") || args[0].equalsIgnoreCase("info")) {
            sendAbout(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            return handleReload(sender, args);
        }

        if (args[0].equalsIgnoreCase("storage-migrate")) {
            if (args.length < 2) {
                sender.sendMessage(plugin.parseComponent("%prefix%<yellow>Uso: /valerinutilsadmin storage-migrate <dry-run|start|status>"));
                return true;
            }
            switch (args[1].toLowerCase()) {
                case "status" -> sender.sendMessage(plugin.parseComponent("%prefix%<gray>Storage migration: <white>" + StorageMigrator.status()));
                case "dry-run" -> StorageMigrator.run(plugin, sender, true);
                case "start" -> {
                    if (!org.bukkit.Bukkit.getOnlinePlayers().isEmpty()) {
                        sender.sendMessage(plugin.parseComponent("%prefix%<red>La migración requiere cero jugadores conectados."));
                    } else StorageMigrator.run(plugin, sender, false);
                }
                default -> sender.sendMessage(plugin.parseComponent("%prefix%<yellow>Uso: /valerinutilsadmin storage-migrate <dry-run|start|status>"));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("debug")) {
            if (args.length < 2) {
                sender.sendMessage(plugin.translateColors("%prefix%&cUso: /valerinutilsadmin debug <modulo> [on|off|toggle]"));
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
                                "%prefix%&cUso: /valerinutilsadmin debug <modulo> [on|off|toggle]"));
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

        if (args[0].equalsIgnoreCase("deathspawn")) {
            if (!plugin.getModuleManager().isModuleEnabled("deathspawn")) {
                sender.sendMessage(plugin.parseComponent(
                        "%prefix%<#FFC43B>El módulo <white>deathspawn</white> está desactivado.</#FFC43B>"));
                return true;
            }
            return plugin.getDeathSpawnModule().handleAdminCommand(
                    sender, Arrays.copyOfRange(args, 1, args.length));
        }

        sender.sendMessage(plugin.getMessage("valerinutils-usage"));
        return true;
    }

    private void sendAdminHelp(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(MINI_MESSAGE.deserialize(
                "<#FFD166><bold>ValerinUtils Admin</bold></#FFD166> <#FFD166>| Comandos:</#FFD166>"));
        sender.sendMessage(MINI_MESSAGE.deserialize(
                "<click:suggest_command:'/valerinutilsadmin reload '>"
                        + "<hover:show_text:'<gray>Recarga todos los módulos o uno concreto.</gray>'>"
                        + "<#FFD166><italic>/vutilsadmin reload</italic></#FFD166></hover></click>"
                        + "   <gray>Recargar configuración</gray>"));
        sender.sendMessage(MINI_MESSAGE.deserialize(
                "<click:suggest_command:'/valerinutilsadmin debug '>"
                        + "<hover:show_text:'<gray>Controla el diagnóstico de un módulo.</gray>'>"
                        + "<#FFD166><italic>/vutilsadmin debug</italic></#FFD166></hover></click>"
                        + "   <gray>Controlar diagnóstico</gray>"));
        if (plugin.getModuleManager().isModuleEnabled("deathspawn")) {
            sender.sendMessage(MINI_MESSAGE.deserialize(
                    "<click:suggest_command:'/valerinutilsadmin deathspawn '>"
                            + "<hover:show_text:'<gray>Configura respawns por región desde el juego.</gray>'>"
                            + "<#FFD166><italic>/vutilsadmin deathspawn</italic></#FFD166></hover></click>"
                            + "   <gray>Configurar respawns</gray>"));
        }
        sender.sendMessage(Component.empty());
    }

    private void sendAbout(CommandSender sender) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(MINI_MESSAGE.deserialize("<#FFD166><bold>ValerinUtils</bold></#FFD166> <gray>v"
                + plugin.getDescription().getVersion() + "</gray>"));
        sender.sendMessage(MINI_MESSAGE.deserialize(
                "<gray>Colección modular de utilidades de <white>ValerinSMP</white>.</gray>"));
        sender.sendMessage(MINI_MESSAGE.deserialize(
                "<click:open_url:'https://github.com/ValerinSMP/ValerinUtils'>"
                        + "<hover:show_text:'<gray>Abrir repositorio</gray>'>"
                        + "<#FFD166><underlined>GitHub</underlined></#FFD166></hover></click>"));
        sender.sendMessage(Component.empty());
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
        boolean adminRoot = command.getName().equalsIgnoreCase("valerinutilsadmin");

        if (!adminRoot) {
            if (args.length == 1) {
                return List.of("help", "about").stream()
                        .filter(option -> option.startsWith(args[0].toLowerCase()))
                        .toList();
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("help")) {
                String partial = args[1];
                List<String> pages = new ArrayList<>();
                for (int page = 1; page <= help.totalPages(sender); page++) {
                    String value = String.valueOf(page);
                    if (value.startsWith(partial)) {
                        pages.add(value);
                    }
                }
                return pages;
            }
            return Collections.emptyList();
        }

        if (!sender.hasPermission("valerinutils.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String partial = args[0].toLowerCase();
            if ("help".startsWith(partial)) {
                completions.add("help");
            }
            if ("about".startsWith(partial)) {
                completions.add("about");
            }
            if ("reload".startsWith(partial)) {
                completions.add("reload");
            }
            if ("storage-migrate".startsWith(partial)) completions.add("storage-migrate");
            if ("debug".startsWith(partial)) {
                completions.add("debug");
            }
            if (plugin.getModuleManager().isModuleEnabled("deathspawn")
                    && "deathspawn".startsWith(partial)) {
                completions.add("deathspawn");
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

        if (args.length == 2 && args[0].equalsIgnoreCase("storage-migrate")) {
            return List.of("dry-run", "start", "status").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase())).toList();
        }

        if (args[0].equalsIgnoreCase("deathspawn")) {
            if (!plugin.getModuleManager().isModuleEnabled("deathspawn")) {
                return Collections.emptyList();
            }
            return plugin.getDeathSpawnModule().tabCompleteAdmin(
                    sender, Arrays.copyOfRange(args, 1, args.length));
        }

        return Collections.emptyList();
    }
}
