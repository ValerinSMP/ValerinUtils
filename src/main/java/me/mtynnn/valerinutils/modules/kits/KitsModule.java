package me.mtynnn.valerinutils.modules.kits;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;

public final class KitsModule implements Module {

    private final ValerinUtils plugin;
    private final KitsAutoKitService autoKitService;
    private final KitsCommandHandler commandHandler;
    private final KitsListener listener;

    public KitsModule(ValerinUtils plugin) {
        this.plugin = plugin;
        this.autoKitService = new KitsAutoKitService(plugin, this);
        this.commandHandler = new KitsCommandHandler(plugin, this, autoKitService);
        this.listener = new KitsListener(plugin, this, autoKitService);
    }

    @Override
    public String getId() {
        return "kits";
    }

    @Override
    public void enable() {
        registerCommand("vukits");
        registerCommand("kits");
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        debug("Módulo habilitado.");
    }

    @Override
    public void disable() {
        unregisterCommand("vukits");
        unregisterCommand("kits");
        HandlerList.unregisterAll(listener);
        commandHandler.saveState();
        autoKitService.clear();
        debug("Módulo deshabilitado.");
    }

    public FileConfiguration getConfig() {
        return plugin.getConfigManager().getConfig(getId());
    }

    public boolean isDebugEnabled() {
        FileConfiguration cfg = getConfig();
        return cfg != null && cfg.getBoolean("settings.debug_command_spam", false);
    }

    public void debug(String message) {
        plugin.debug(getId(), message);
    }

    KitsCommandHandler commandHandler() {
        return commandHandler;
    }

    private void registerCommand(String name) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            return;
        }
        command.setExecutor(commandHandler);
        command.setTabCompleter(commandHandler);
    }

    private void unregisterCommand(String name) {
        PluginCommand command = plugin.getCommand(name);
        if (command == null) {
            return;
        }
        command.setExecutor(null);
        command.setTabCompleter(null);
    }
}
