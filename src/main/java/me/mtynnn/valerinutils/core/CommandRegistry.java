package me.mtynnn.valerinutils.core;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public final class CommandRegistry {

    private final ValerinUtils plugin;
    private final Map<String, Set<String>> ownerToCommands = new HashMap<>();
    private final Map<String, Set<String>> declaredCommands = new HashMap<>();

    public CommandRegistry(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    public void declare(String ownerId, Set<String> commandNames) {
        Set<String> normalized = new HashSet<>();
        for (String commandName : commandNames) {
            normalized.add(commandName.toLowerCase());
        }
        declaredCommands.put(ownerId, normalized);
        installDisabledExecutors(ownerId, normalized);
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
        command.setExecutor((sender, invokedCommand, label, args) -> {
            if (Bukkit.isPrimaryThread()) {
                return executor.onCommand(sender, invokedCommand, label, args);
            }
            String[] safeArgs = args.clone();
            Bukkit.getScheduler().runTask(plugin,
                    () -> executor.onCommand(sender, invokedCommand, label, safeArgs));
            return true;
        });
        command.setTabCompleter(tabCompleter == null ? null
                : (sender, invokedCommand, alias, args) ->
                        completeOnPrimaryThread(tabCompleter, sender, invokedCommand, alias, args));
        ownerToCommands.computeIfAbsent(ownerId, key -> new HashSet<>()).add(commandName.toLowerCase());
        return true;
    }

    public void unbindOwner(String ownerId) {
        ownerToCommands.remove(ownerId);
        Set<String> commands = declaredCommands.getOrDefault(ownerId, Collections.emptySet());
        installDisabledExecutors(ownerId, commands);
    }

    private List<String> completeOnPrimaryThread(
            TabCompleter completer,
            org.bukkit.command.CommandSender sender,
            org.bukkit.command.Command command,
            String alias,
            String[] args
    ) {
        if (Bukkit.isPrimaryThread()) {
            List<String> result = completer.onTabComplete(sender, command, alias, args);
            return result == null ? Collections.emptyList() : result;
        }
        CompletableFuture<List<String>> future = new CompletableFuture<>();
        String[] safeArgs = args.clone();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                List<String> result = completer.onTabComplete(sender, command, alias, safeArgs);
                future.complete(result == null ? Collections.emptyList() : List.copyOf(result));
            } catch (Throwable throwable) {
                future.complete(Collections.emptyList());
            }
        });
        try {
            return future.get(2, TimeUnit.SECONDS);
        } catch (Exception exception) {
            return Collections.emptyList();
        }
    }

    private void installDisabledExecutors(String ownerId, Set<String> commands) {
        for (String commandName : commands) {
            PluginCommand command = plugin.getCommand(commandName);
            if (command == null) {
                continue;
            }
            command.setExecutor((sender, ignoredCommand, ignoredLabel, ignoredArgs) -> {
                sender.sendMessage(plugin.parseComponent(
                        "%prefix%<#FFC43B>El módulo <white>" + ownerId
                                + "</white> está desactivado.</#FFC43B>"));
                return true;
            });
            command.setTabCompleter((sender, ignoredCommand, ignoredAlias, ignoredArgs) -> Collections.emptyList());
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
