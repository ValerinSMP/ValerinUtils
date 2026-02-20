package me.mtynnn.valerinutils.modules.pvpmina;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;

public class PvpMinaModule implements Module {

    private final ValerinUtils plugin;
    private final PvpMinaRuntime runtime;
    private final PvpMinaRulesListener rulesListener;
    private final PvpMinaCommandHandler commandHandler;

    public PvpMinaModule(ValerinUtils plugin) {
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
    public void enable() {
        FileConfiguration cfg = plugin.getConfigManager().getConfig("pvpmina");
        if (cfg == null || !cfg.getBoolean("enabled", true)) {
            return;
        }

        runtime.enable();
        Bukkit.getPluginManager().registerEvents(rulesListener, plugin);

        PluginCommand cmd = plugin.getCommand("pvpmina");
        if (cmd != null) {
            cmd.setExecutor(commandHandler);
        }
    }

    @Override
    public void disable() {
        runtime.disable();
        org.bukkit.event.HandlerList.unregisterAll(rulesListener);

        PluginCommand cmd = plugin.getCommand("pvpmina");
        if (cmd != null) {
            cmd.setExecutor(null);
        }
    }
}
