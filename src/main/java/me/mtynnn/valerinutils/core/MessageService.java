package me.mtynnn.valerinutils.core;

import me.mtynnn.valerinutils.ValerinUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class MessageService {

    private final ValerinUtils plugin;

    public MessageService(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    public String settings(String key, String def) {
        FileConfiguration settings = plugin.getConfigManager().getConfig("settings");
        if (settings == null) {
            return plugin.translateColors(def);
        }
        String raw = settings.getString("messages." + key, def);
        return legacy(raw);
    }

    public List<String> settingsList(String key) {
        FileConfiguration settings = plugin.getConfigManager().getConfig("settings");
        if (settings == null) {
            return Collections.emptyList();
        }
        String path = "messages." + key;
        if (!settings.isList(path)) {
            return List.of(legacy(settings.getString(path, "")));
        }
        List<String> raw = settings.getStringList(path);
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(legacy(line));
        }
        return out;
    }

    public String module(String moduleId, String key, String def) {
        FileConfiguration cfg = plugin.getConfigManager().getConfig(moduleId);
        if (cfg == null) {
            return legacy(def);
        }
        return legacy(cfg.getString("messages." + key, def));
    }

    public List<String> moduleList(String moduleId, String key) {
        FileConfiguration cfg = plugin.getConfigManager().getConfig(moduleId);
        if (cfg == null) {
            return Collections.emptyList();
        }
        String path = "messages." + key;
        if (!cfg.isList(path)) {
            return List.of(legacy(cfg.getString(path, "")));
        }
        List<String> raw = cfg.getStringList(path);
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(legacy(line));
        }
        return out;
    }

    public String legacy(String raw) {
        if (raw == null) {
            return "";
        }
        return plugin.translateColors(applyInternalPlaceholders(raw));
    }

    public Component component(String raw) {
        if (raw == null) {
            return Component.empty();
        }
        return plugin.parseComponent(applyInternalPlaceholders(raw));
    }

    public Component component(String raw, Map<String, String> placeholders) {
        if (raw == null) {
            return Component.empty();
        }
        String text = applyInternalPlaceholders(raw);
        if (placeholders != null && !placeholders.isEmpty()) {
            for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                String value = entry.getValue() == null ? "" : entry.getValue();
                text = text.replace(entry.getKey(), value);
            }
        }
        return plugin.parseComponent(text);
    }

    public String applyInternalPlaceholders(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("%prefix%", plugin.getGlobalPrefix());
    }
}

