package me.mtynnn.valerinutils.modules.kits;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.BaseModule;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

public final class KitsModule extends BaseModule {

    private final ValerinUtils plugin;
    private final KitsAutoKitService autoKitService;
    private final KitsCommandHandler commandHandler;
    private final KitsListener listener;

    public KitsModule(ValerinUtils plugin) {
        super(plugin);
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
    protected void onEnableModule() {
        registerCommand("vukits", commandHandler, commandHandler);
        registerCommand("kits", commandHandler, commandHandler);
        registerListener(listener);
        debug("Módulo habilitado.");
    }

    @Override
    protected void onDisableModule() {
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

    public void logDebug(String message) {
        plugin.debug(getId(), message);
    }

    KitsCommandHandler commandHandler() {
        return commandHandler;
    }

}
