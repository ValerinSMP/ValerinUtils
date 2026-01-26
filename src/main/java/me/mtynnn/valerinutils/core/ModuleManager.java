package me.mtynnn.valerinutils.core;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public class ModuleManager {

    private final JavaPlugin plugin;
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }
    public void registerModule(Module module){
        modules.put(module.getId(), module);
    }
    public Module getModule(String id) {
        return modules.get(id);
    }

    public void enableAll(){
        for (Module module : modules.values()){
            String path = "modules."+module.getId()+".enabled";
            boolean enabled = plugin.getConfig().getBoolean(path, true);
            if (!enabled){
                plugin.getLogger().info("Modulo desactivado en la config: "+module.getId());
                continue;
            }
        module.enable();
        plugin.getLogger().info("Modulo activado: "+module.getId());
        }
    }
    public void disableAll(){
        for (Module module : modules.values())
            module.disable();
    }
}
