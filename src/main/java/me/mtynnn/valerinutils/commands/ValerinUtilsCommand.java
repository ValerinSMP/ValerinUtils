package me.mtynnn.valerinutils.commands;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
            return completions;
        }

        return Collections.emptyList();
    }
}
