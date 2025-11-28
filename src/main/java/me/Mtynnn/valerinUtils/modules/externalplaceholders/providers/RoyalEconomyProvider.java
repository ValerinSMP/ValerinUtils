package me.Mtynnn.valerinUtils.modules.externalplaceholders.providers;

import me.Mtynnn.valerinUtils.ValerinUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Provider de placeholders para RoyaleEconomy.
 * Lee los archivos de datos de RoyaleEconomy para exponer información que el plugin no ofrece.
 * También mantiene un cache propio para actualizaciones en tiempo real.
 * 
 * Placeholders disponibles:
 * - pay_enabled: true si el jugador puede recibir pagos, false si los tiene desactivados
 */
public class RoyalEconomyProvider implements PlaceholderProvider, Listener {

    private final ValerinUtils plugin;
    
    // Cache propio de ValerinUtils (tiempo real)
    private final Set<UUID> payDisabledCache = new HashSet<>();
    private File cacheFile;
    private FileConfiguration cacheConfig;
    
    // Datos de RoyaleEconomy (solo se actualiza en reinicios)
    private final Set<String> royaleEconomyData = new HashSet<>();

    public RoyalEconomyProvider(ValerinUtils plugin) {
        this.plugin = plugin;
        loadCache();
        loadRoyaleEconomyData();
        
        // Registrar listener para interceptar comandos
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public @NotNull String getId() {
        return "royaleconomy";
    }

    @Override
    public @NotNull String getPluginName() {
        return "RoyaleEconomy";
    }

    @Override
    public @Nullable String onPlaceholderRequest(@NotNull Player player, @NotNull String params) {
        return switch (params.toLowerCase()) {
            case "pay_enabled" -> {
                UUID uuid = player.getUniqueId();
                
                // Nuestro cache es la única fuente de verdad
                // (ya incluye datos sincronizados de RoyaleEconomy al inicio)
                boolean isDisabled = payDisabledCache.contains(uuid);
                
                yield String.valueOf(!isDisabled);
            }
            default -> null;
        };
    }

    @Override
    public void reload() {
        loadCache();
        loadRoyaleEconomyData();
        plugin.getLogger().info("[RoyalEconomyProvider] Data reloaded");
    }

    /**
     * Intercepta el comando /pay toggle para actualizar nuestro cache en tiempo real
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled()) return;
        
        String message = event.getMessage().toLowerCase();
        
        // Detectar variantes del comando de toggle
        // /pay toggle, /pagar toggle, etc.
        if (isPayToggleCommand(message)) {
            Player player = event.getPlayer();
            
            // Esperar un tick para que RoyaleEconomy procese el comando primero
            new BukkitRunnable() {
                @Override
                public void run() {
                    togglePayStatus(player);
                }
            }.runTaskLater(plugin, 1L);
        }
    }
    
    /**
     * Detecta si el comando es un toggle de pay
     */
    private boolean isPayToggleCommand(String command) {
        // Remover el / inicial
        if (command.startsWith("/")) {
            command = command.substring(1);
        }
        
        // Comandos conocidos de RoyaleEconomy para pay toggle
        String[] payCommands = {"pay", "pagar"};
        
        for (String payCmd : payCommands) {
            if (command.equals(payCmd + " toggle") || command.startsWith(payCmd + " toggle ")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Cambia el estado de pay del jugador en nuestro cache
     */
    private void togglePayStatus(Player player) {
        UUID uuid = player.getUniqueId();
        
        if (payDisabledCache.contains(uuid)) {
            // Estaba desactivado -> activar
            payDisabledCache.remove(uuid);
        } else {
            // Estaba activado -> desactivar
            payDisabledCache.add(uuid);
        }
        
        saveCache();
    }
    
    /**
     * Verifica si un jugador tiene el pay desactivado
     */
    public boolean isPayDisabled(Player player) {
        UUID uuid = player.getUniqueId();
        return payDisabledCache.contains(uuid);
    }
    
    /**
     * Establece el estado de pay de un jugador
     */
    public void setPayDisabled(Player player, boolean disabled) {
        UUID uuid = player.getUniqueId();
        
        if (disabled) {
            payDisabledCache.add(uuid);
        } else {
            payDisabledCache.remove(uuid);
        }
        
        saveCache();
    }

    // ================== Persistencia del cache propio ==================
    
    private void loadCache() {
        payDisabledCache.clear();
        
        cacheFile = new File(plugin.getDataFolder(), "data.yml");
        if (!cacheFile.exists()) {
            try {
                cacheFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("[RoyalEconomyProvider] Could not create data.yml: " + e.getMessage());
            }
        }
        
        cacheConfig = YamlConfiguration.loadConfiguration(cacheFile);
        
        List<String> list = cacheConfig.getStringList("royaleconomy-pay-disabled");
        for (String uuidStr : list) {
            try {
                payDisabledCache.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }
    
    private void saveCache() {
        // Recargar el archivo para no perder datos de otros módulos
        cacheConfig = YamlConfiguration.loadConfiguration(cacheFile);
        
        List<String> list = payDisabledCache.stream()
                .map(UUID::toString)
                .toList();
        
        cacheConfig.set("royaleconomy-pay-disabled", list);
        
        try {
            cacheConfig.save(cacheFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[RoyalEconomyProvider] Could not save cache: " + e.getMessage());
        }
    }

    // ================== Datos de RoyaleEconomy ==================
    
    private void loadRoyaleEconomyData() {
        royaleEconomyData.clear();

        Plugin royaleEconomy = Bukkit.getPluginManager().getPlugin("RoyaleEconomy");
        if (royaleEconomy == null) {
            return;
        }

        File payDataFile = new File(royaleEconomy.getDataFolder(), "database/payData.yml");
        
        if (!payDataFile.exists()) {
            return;
        }

        FileConfiguration payConfig = YamlConfiguration.loadConfiguration(payDataFile);
        List<String> toggledList = payConfig.getStringList("pay-toggled");
        royaleEconomyData.addAll(toggledList);
        
        // Sincronizar: si RoyaleEconomy tiene datos que no tenemos, añadirlos a nuestro cache
        for (String entry : toggledList) {
            try {
                UUID uuid = UUID.fromString(entry);
                if (!payDisabledCache.contains(uuid)) {
                    payDisabledCache.add(uuid);
                }
            } catch (IllegalArgumentException ignored) {
                // Es un nombre, no UUID - ignorar para el cache
            }
        }
        
        if (!toggledList.isEmpty()) {
            saveCache();
        }
    }
}
