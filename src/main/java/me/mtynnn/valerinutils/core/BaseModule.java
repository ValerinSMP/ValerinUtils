package me.mtynnn.valerinutils.core;

import me.mtynnn.valerinutils.ValerinUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class BaseModule {

    protected final ValerinUtils plugin;
    private final Set<Listener> listeners = new HashSet<>();

    protected BaseModule(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    public abstract String getId();

    public final void enable() {
        onEnableModule();
    }

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

    protected final FileConfiguration cfg() {
        return plugin.getConfigManager().getConfig(getId());
    }

    protected final String msg(String key) {
        return plugin.messages().module(getId(), key, "");
    }

    protected final String msg(String key, String def) {
        return plugin.messages().module(getId(), key, def);
    }

    protected final List<String> msgList(String key) {
        return plugin.messages().moduleList(getId(), key);
    }

    protected final Component comp(String raw) {
        return plugin.messages().component(raw);
    }

    protected final void debug(String message) {
        plugin.debug(getId(), message);
    }
}
