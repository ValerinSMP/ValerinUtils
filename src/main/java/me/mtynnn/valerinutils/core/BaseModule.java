package me.mtynnn.valerinutils.core;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.Set;

public abstract class BaseModule extends AbstractModule {

    private final Set<Listener> listeners = new HashSet<>();

    protected BaseModule(ValerinUtils plugin) {
        super(plugin);
    }

    @Override
    public final void enable() {
        onEnableModule();
    }

    @Override
    public final void disable() {
        try {
            onDisableModule();
        } finally {
            for (Listener listener : listeners) {
                HandlerList.unregisterAll(listener);
            }
            listeners.clear();
            plugin.getCommandRegistry().unbindOwner(getId());
        }
    }

    protected abstract void onEnableModule();

    protected void onDisableModule() {
    }

    protected final boolean isEnabledInConfig() {
        FileConfiguration config = cfg();
        return config == null || config.getBoolean("enabled", true);
    }

    protected final void registerListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        listeners.add(listener);
    }

    protected final boolean registerCommand(String commandName, CommandExecutor executor) {
        return plugin.getCommandRegistry().bind(getId(), commandName, executor);
    }

    protected final boolean registerCommand(String commandName, CommandExecutor executor, TabCompleter completer) {
        return plugin.getCommandRegistry().bind(getId(), commandName, executor, completer);
    }
}
