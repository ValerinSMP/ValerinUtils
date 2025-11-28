package me.Mtynnn.valerinUtils.modules.externalplaceholders;

import me.Mtynnn.valerinUtils.ValerinUtils;
import me.Mtynnn.valerinUtils.core.Module;
import me.Mtynnn.valerinUtils.modules.externalplaceholders.providers.EcoSkillsProvider;
import me.Mtynnn.valerinUtils.modules.externalplaceholders.providers.GrimACProvider;
import me.Mtynnn.valerinUtils.modules.externalplaceholders.providers.PlaceholderProvider;
import me.Mtynnn.valerinUtils.modules.externalplaceholders.providers.RoyalEconomyProvider;
import me.Mtynnn.valerinUtils.modules.externalplaceholders.providers.TabProvider;
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
        // Registrar RoyalEconomy si está presente
        if (Bukkit.getPluginManager().getPlugin("RoyaleEconomy") != null) {
            RoyalEconomyProvider royalProvider = new RoyalEconomyProvider(plugin);
            providers.put("royaleconomy", royalProvider);
            plugin.getLogger().info("[ExternalPlaceholders] RoyaleEconomy hooked");
        }

        // Registrar EcoSkills si está presente
        if (Bukkit.getPluginManager().getPlugin("EcoSkills") != null) {
            EcoSkillsProvider ecoSkillsProvider = new EcoSkillsProvider(plugin);
            providers.put("ecoskills", ecoSkillsProvider);
            plugin.getLogger().info("[ExternalPlaceholders] EcoSkills hooked");
        }

        // Registrar TAB si está presente
        if (Bukkit.getPluginManager().getPlugin("TAB") != null) {
            TabProvider tabProvider = new TabProvider(plugin);
            providers.put("tab", tabProvider);
            plugin.getLogger().info("[ExternalPlaceholders] TAB hooked");
        }

        // Registrar GrimAC si está presente
        if (Bukkit.getPluginManager().getPlugin("GrimAC") != null) {
            GrimACProvider grimACProvider = new GrimACProvider(plugin);
            providers.put("grimac", grimACProvider);
            plugin.getLogger().info("[ExternalPlaceholders] GrimAC hooked");
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
