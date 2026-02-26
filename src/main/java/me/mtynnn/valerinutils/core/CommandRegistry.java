package me.mtynnn.valerinutils.core;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class CommandRegistry {

    private final ValerinUtils plugin;
    private final Map<String, Set<String>> ownerToCommands = new HashMap<>();

    public CommandRegistry(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    public boolean bind(String ownerId, String commandName, CommandExecutor executor) {
        TabCompleter completer = executor instanceof TabCompleter tabCompleter ? tabCompleter : null;
        return bind(ownerId, commandName, executor, completer);
    }

    public boolean bind(String ownerId, String commandName, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand command = plugin.getCommand(commandName);
        if (command == null) {
            plugin.getLogger().warning("[CommandRegistry] Comando no registrado en plugin.yml: /" + commandName
                    + " (owner=" + ownerId + ")");
            return false;
        }
        command.setExecutor(executor);
        command.setTabCompleter(tabCompleter);
        ownerToCommands.computeIfAbsent(ownerId, key -> new HashSet<>()).add(commandName.toLowerCase());
        return true;
    }

    public void unbindOwner(String ownerId) {
        Set<String> commands = ownerToCommands.remove(ownerId);
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (String commandName : commands) {
            PluginCommand command = plugin.getCommand(commandName);
            if (command == null) {
                continue;
            }
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    public Set<String> getBoundCommands(String ownerId) {
        Set<String> commands = ownerToCommands.get(ownerId);
        if (commands == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(commands);
    }
}
