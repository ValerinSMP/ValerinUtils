package me.Mtynnn.valerinUtils.modules.externalplaceholders.providers;

import ac.grim.grimac.api.GrimAbstractAPI;
import ac.grim.grimac.api.GrimAPIProvider;
import ac.grim.grimac.api.GrimUser;
import ac.grim.grimac.api.alerts.AlertManager;
import me.Mtynnn.valerinUtils.ValerinUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GrimACProvider implements PlaceholderProvider, Listener {

    private final ValerinUtils plugin;
    private File dataFile;
    private FileConfiguration dataConfig;
    
    // Cache local para los toggles
    private final Map<UUID, Boolean> alertsCache = new HashMap<>();

    public GrimACProvider(ValerinUtils plugin) {
        this.plugin = plugin;
        loadData();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("Could not create data.yml for GrimACProvider");
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        // Cargar cache desde archivo
        if (dataConfig.contains("grimac.alerts")) {
            for (String uuidStr : dataConfig.getConfigurationSection("grimac.alerts").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    alertsCache.put(uuid, dataConfig.getBoolean("grimac.alerts." + uuidStr));
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    private void saveData() {
        try {
            for (Map.Entry<UUID, Boolean> entry : alertsCache.entrySet()) {
                dataConfig.set("grimac.alerts." + entry.getKey().toString(), entry.getValue());
            }
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save data.yml for GrimACProvider");
        }
    }

    @Override
    public String getId() {
        return "grimac";
    }

    @Override
    public String getPluginName() {
        return "GrimAC";
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        UUID uuid = player.getUniqueId();
        String uuidStr = uuid.toString();
        
        // Recargar config para asegurar datos actualizados
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        switch (identifier.toLowerCase()) {
            case "alerts_enabled":
                // Si está en cache, usar cache. Si no, leer de archivo. Default true.
                if (alertsCache.containsKey(uuid)) {
                    return alertsCache.get(uuid) ? "true" : "false";
                }
                return dataConfig.getBoolean("grimac.alerts." + uuidStr, true) ? "true" : "false";
            default:
                return null;
        }
    }

    @Override
    public void reload() {
        loadData();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        
        String command = event.getMessage().toLowerCase();
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Detectar comando de toggle alerts: /grim alerts, /grimac alerts, /grimanticheat alerts
        if (command.equals("/grim alerts") || command.equals("/grimac alerts") || 
            command.equals("/grimanticheat alerts") || command.equals("/grim alert") ||
            command.equals("/grimac alert") || command.equals("/grimanticheat alert")) {
            new BukkitRunnable() {
                @Override
                public void run() {
                    toggleAlerts(uuid);
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        
        // Si el jugador tiene alerts desactivados en nuestro cache, desactivarlos en GrimAC
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        boolean savedState = dataConfig.getBoolean("grimac.alerts." + uuid.toString(), true);
        
        if (!savedState) {
            // Esperar a que GrimAC cargue al jugador y luego desactivar alerts
            new BukkitRunnable() {
                @Override
                public void run() {
                    GrimAbstractAPI api = GrimAPIProvider.get();
                    if (api != null && api.hasStarted()) {
                        AlertManager alertManager = api.getAlertManager();
                        GrimUser grimUser = api.getGrimUser(uuid);
                        if (alertManager != null && grimUser != null) {
                            alertManager.setAlertsEnabled(grimUser, false, false);
                            alertsCache.put(uuid, false);
                        }
                    }
                }
            }.runTaskLater(plugin, 20L); // Esperar 1 segundo para que GrimAC cargue al jugador
        } else {
            alertsCache.put(uuid, true);
        }
    }

    private void toggleAlerts(UUID uuid) {
        boolean current = alertsCache.getOrDefault(uuid, true);
        alertsCache.put(uuid, !current);
        saveData();
    }
}
