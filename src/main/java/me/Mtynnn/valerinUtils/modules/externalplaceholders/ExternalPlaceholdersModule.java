package me.Mtynnn.valerinUtils.modules.externalplaceholders;

import me.Mtynnn.valerinUtils.ValerinUtils;
import me.Mtynnn.valerinUtils.core.Module;

import me.Mtynnn.valerinUtils.modules.externalplaceholders.providers.PlaceholderProvider;
import me.Mtynnn.valerinUtils.modules.externalplaceholders.providers.RoyalEconomyProvider;

import org.bukkit.Bukkit;

import java.util.LinkedHashMap;
import java.util.Map;

public class ExternalPlaceholdersModule implements Module {

    private final ValerinUtils plugin;
    private final Map<String, PlaceholderProvider> providers = new LinkedHashMap<>();

    public ExternalPlaceholdersModule(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "externalplaceholders";
    }

    @Override
    public void enable() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().info("[ExternalPlaceholders] PlaceholderAPI not found, skipping providers");
            return;
        }

        // Registrar RoyalEconomy si está presente
        if (Bukkit.getPluginManager().getPlugin("RoyaleEconomy") != null) {
            RoyalEconomyProvider royalProvider = new RoyalEconomyProvider(plugin);
            providers.put("royaleconomy", royalProvider);
            plugin.getLogger().info("[ExternalPlaceholders] RoyaleEconomy hooked");
        }

        plugin.getLogger().info("[ExternalPlaceholders] " + providers.size() + " provider(s) loaded");
    }

    @Override
    public void disable() {
        providers.clear();
    }

    /**
     * Obtiene un provider por su id (ej: "royaleconomy")
     */
    public PlaceholderProvider getProvider(String id) {
        return providers.get(id.toLowerCase());
    }

    /**
     * Obtiene todos los providers registrados
     */
    public Map<String, PlaceholderProvider> getProviders() {
        return providers;
    }

    /**
     * Recarga todos los providers (para cuando se hace /valerinutils reload)
     */
    public void reloadAll() {
        for (PlaceholderProvider provider : providers.values()) {
            provider.reload();
        }
    }
}
