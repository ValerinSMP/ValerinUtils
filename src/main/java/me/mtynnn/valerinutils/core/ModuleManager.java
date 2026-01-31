package me.mtynnn.valerinutils.core;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ModuleManager {

    private final JavaPlugin plugin;
    private final Map<String, Module> modules = new LinkedHashMap<>();
    private final Set<String> enabledModules = new HashSet<>();

    public ModuleManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerModule(Module module) {
        modules.put(module.getId(), module);
    }

    public Module getModule(String id) {
        return modules.get(id);
    }

    public boolean isModuleEnabled(String id) {
        return enabledModules.contains(id);
    }

    public void enableAll() {
        for (Module module : modules.values()) {
            String path = "modules." + module.getId() + ".enabled";
            boolean enabled = plugin.getConfig().getBoolean(path, true);
            if (!enabled) {
                plugin.getLogger().info("Modulo desactivado en la config: " + module.getId());
                continue;
            }
            module.enable();
            enabledModules.add(module.getId());
            plugin.getLogger().info("Modulo activado: " + module.getId());
        }
    }

    public void disableAll() {
        for (Module module : modules.values()) {
            if (enabledModules.contains(module.getId())) {
                module.disable();
            }
        }
        enabledModules.clear();
    }

    /**
     * Reload all modules. Disables modules that were previously enabled,
     * then re-enables based on current config.
     */
    public void reloadAll() {
        // First, disable all currently enabled modules
        for (Module module : modules.values()) {
            if (enabledModules.contains(module.getId())) {
                module.disable();
                plugin.getLogger().info("Modulo desactivado para reload: " + module.getId());
            }
        }
        enabledModules.clear();

        // Then re-enable based on new config
        for (Module module : modules.values()) {
            String path = "modules." + module.getId() + ".enabled";
            boolean enabled = plugin.getConfig().getBoolean(path, true);
            if (!enabled) {
                plugin.getLogger().info("Modulo desactivado en la config: " + module.getId());
                continue;
            }
            module.enable();
            enabledModules.add(module.getId());
            plugin.getLogger().info("Modulo reactivado: " + module.getId());
        }
    }
}
