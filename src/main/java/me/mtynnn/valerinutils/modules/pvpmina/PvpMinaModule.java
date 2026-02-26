package me.mtynnn.valerinutils.modules.pvpmina;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.BaseModule;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

public class PvpMinaModule extends BaseModule {

    private final ValerinUtils plugin;
    private final PvpMinaRuntime runtime;
    private final PvpMinaRulesListener rulesListener;
    private final PvpMinaCommandHandler commandHandler;

    public PvpMinaModule(ValerinUtils plugin) {
        super(plugin);
        this.plugin = plugin;
        this.runtime = new PvpMinaRuntime(plugin);
        this.rulesListener = new PvpMinaRulesListener(plugin, runtime);
        this.commandHandler = new PvpMinaCommandHandler(plugin, runtime);
    }

    @Override
    public String getId() {
        return "pvpmina";
    }

    @Override
    protected void onEnableModule() {
        FileConfiguration cfg = plugin.getConfigManager().getConfig("pvpmina");
        if (cfg == null || !cfg.getBoolean("enabled", true)) {
            return;
        }

        runtime.enable();
        registerListener(rulesListener);
        registerCommand("pvpmina", commandHandler);
    }

    @Override
    protected void onDisableModule() {
        runtime.disable();
    }
}
