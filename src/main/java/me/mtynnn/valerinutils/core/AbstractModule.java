package me.mtynnn.valerinutils.core;

import me.mtynnn.valerinutils.ValerinUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

public abstract class AbstractModule implements Module {

    protected final ValerinUtils plugin;

    protected AbstractModule(ValerinUtils plugin) {
        this.plugin = plugin;
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

