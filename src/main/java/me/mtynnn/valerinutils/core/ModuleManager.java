package me.mtynnn.valerinutils.core;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

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

    public Set<String> getRegisteredModuleIds() {
        return Collections.unmodifiableSet(modules.keySet());
    }

    public boolean isModuleEnabled(String id) {
        return enabledModules.contains(id);
    }

    public void enableAll() {
        if (!(plugin instanceof me.mtynnn.valerinutils.ValerinUtils vPlugin))
            return;

        for (Module module : modules.values()) {
            // Check specific config for module
            org.bukkit.configuration.file.FileConfiguration cfg = vPlugin.getConfigManager().getConfig(module.getId());
            boolean enabled = true;
            if (cfg != null) {
                enabled = cfg.getBoolean("enabled", true);
            } else {
                // Fallback to main config for backwards compatibility or global toggles
                String path = "modules." + module.getId() + ".enabled";
                enabled = plugin.getConfig().getBoolean(path, true);
            }

            if (!enabled) {
                vPlugin.getLogger().info("Modulo desactivado en la config: " + module.getId());
                // Force disable to cleanup any residual state (like items from previous
                // session)
                safeDisable(vPlugin, module, "config-disable");
                continue;
            }
            if (safeEnable(vPlugin, module, "startup")) {
                enabledModules.add(module.getId());
                vPlugin.getLogger().info("Modulo activado: " + module.getId());
            }
        }
    }

    public void disableAll() {
        me.mtynnn.valerinutils.ValerinUtils vPlugin = plugin instanceof me.mtynnn.valerinutils.ValerinUtils vp ? vp : null;
        for (Module module : modules.values()) {
            if (enabledModules.contains(module.getId())) {
                if (vPlugin != null) {
                    safeDisable(vPlugin, module, "shutdown");
                } else {
                    module.disable();
                }
            }
        }
        enabledModules.clear();
    }

    /**
     * Reload all modules. Disables modules that were previously enabled,
     * then re-enables based on current config.
     */
    public void reloadAll() {
        if (!(plugin instanceof me.mtynnn.valerinutils.ValerinUtils vPlugin))
            return;

        Set<String> alreadyDisabled = new java.util.HashSet<>();

        // First, disable all currently enabled modules
        for (Module module : modules.values()) {
            if (enabledModules.contains(module.getId())) {
                safeDisable(vPlugin, module, "reload-disable");
                alreadyDisabled.add(module.getId());
                vPlugin.getLogger().info("Modulo desactivado previo reload: " + module.getId());
            }
        }
        enabledModules.clear();

        // Then re-enable based on new config
        for (Module module : modules.values()) {
            // Check specific config for module
            org.bukkit.configuration.file.FileConfiguration cfg = vPlugin.getConfigManager().getConfig(module.getId());
            boolean enabled = true;
            if (cfg != null) {
                enabled = cfg.getBoolean("enabled", true);
            } else {
                String path = "modules." + module.getId() + ".enabled";
                enabled = plugin.getConfig().getBoolean(path, true);
            }

            if (!enabled) {
                // Force disable ONLY if not already disabled during this run
                if (!alreadyDisabled.contains(module.getId())) {
                    vPlugin.getLogger().info("Modulo desactivado en la config: " + module.getId());
                    safeDisable(vPlugin, module, "reload-config-disable");
                }
                continue;
            }
            if (safeEnable(vPlugin, module, "reload-enable")) {
                enabledModules.add(module.getId());
                vPlugin.getLogger().info("Modulo reactivado: " + module.getId());
            }
        }
    }

    private boolean safeEnable(me.mtynnn.valerinutils.ValerinUtils plugin, Module module, String phase) {
        try {
            module.enable();
            return true;
        } catch (Throwable t) {
            plugin.getLogger().severe("No se pudo habilitar el modulo '" + module.getId() + "' (" + phase + "): "
                    + t.getClass().getSimpleName() + " - " + t.getMessage());
            return false;
        }
    }

    private void safeDisable(me.mtynnn.valerinutils.ValerinUtils plugin, Module module, String phase) {
        try {
            module.disable();
        } catch (Throwable t) {
            plugin.getLogger().severe("No se pudo deshabilitar el modulo '" + module.getId() + "' (" + phase + "): "
                    + t.getClass().getSimpleName() + " - " + t.getMessage());
        }
    }
}
