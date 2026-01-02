package me.Mtynnn.valerinUtils.modules.ecoskillsrecount;

import me.Mtynnn.valerinUtils.ValerinUtils;
import me.Mtynnn.valerinUtils.core.Module;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class EcoSkillsRecountModule implements Module, Listener {

    private final ValerinUtils plugin;
    private final File dataFile;
    private FileConfiguration dataConfig;
    private final Set<UUID> recountedPlayers = new HashSet<>();

    public EcoSkillsRecountModule(ValerinUtils plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "ecoskills_recount_data.yml");
        loadData();
    }

    @Override
    public String getId() {
        return "ecoskillsrecount";
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[EcoSkillsRecount] Module enabled");
    }

    @Override
    public void disable() {
        saveData();
    }

    private void loadData() {
        recountedPlayers.clear();
        
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("[EcoSkillsRecount] Could not create ecoskills_recount_data.yml: " + e.getMessage());
            }
        }
        
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        for (String uuidStr : dataConfig.getStringList("recounted-players")) {
            try {
                recountedPlayers.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) {
            }
        }
        
        plugin.getLogger().info("[EcoSkillsRecount] Loaded " + recountedPlayers.size() + " recounted players");
    }

    private void saveData() {
        // Recargar antes de escribir para no pisar datos externos
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        
        var list = recountedPlayers.stream()
                .map(UUID::toString)
                .toList();
        
        dataConfig.set("recounted-players", list);
        
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[EcoSkillsRecount] Could not save ecoskills_recount_data.yml: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Si ya fue recontado, no hacer nada
        if (recountedPlayers.contains(uuid)) {
            return;
        }

        // Verificar después de un delay (para que el jugador cargue completamente)
        long delayTicks = plugin.getConfig().getLong("modules.ecoskillsrecount.delay-ticks", 20L);
        
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            
            // Verificar si el jugador tiene alguna skill con nivel > 0
            if (hasAnySkillProgress(player)) {
                executeRecount(player);
            }
        }, delayTicks);
    }

    /**
     * Verifica si el jugador tiene alguna skill con nivel mayor a 0
     * Usa reflection para no depender de la API directa de EcoSkills
     */
    private boolean hasAnySkillProgress(Player player) {
        try {
            // Intentar usar la API de EcoSkills mediante reflection
            Class<?> apiClass = Class.forName("com.willfp.ecoskills.api.EcoSkillsAPI");
            Class<?> skillClass = Class.forName("com.willfp.ecoskills.skills.Skill");
            
            // Obtener método getRegisteredSkills()
            java.lang.reflect.Method getRegisteredSkills = apiClass.getMethod("getRegisteredSkills");
            Object skills = getRegisteredSkills.invoke(null);
            
            // Iterar sobre las skills
            if (skills instanceof Iterable<?>) {
                for (Object skill : (Iterable<?>) skills) {
                    // Obtener nivel del jugador para esta skill
                    java.lang.reflect.Method getSkillLevel = apiClass.getMethod("getSkillLevel", Player.class, skillClass);
                    int level = (int) getSkillLevel.invoke(null, player, skill);
                    
                    if (level > 0) {
                        if (plugin.isDebug()) {
                            java.lang.reflect.Method getIdMethod = skillClass.getMethod("getId");
                            String skillId = (String) getIdMethod.invoke(skill);
                            plugin.getLogger().info("[EcoSkillsRecount] Player " + player.getName() + " has skill " 
                                    + skillId + " at level " + level);
                        }
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            if (plugin.isDebug()) {
                plugin.getLogger().warning("[EcoSkillsRecount] Error checking skills for " + player.getName() + ": " + e.getMessage());
            }
        }
        
        return false;
    }

    /**
     * Ejecuta el comando /ecoskills recount para el jugador
     */
    private void executeRecount(Player player) {
        String command = "ecoskills recount " + player.getName();
        
        plugin.getLogger().info("[EcoSkillsRecount] Executing recount for " + player.getName());
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                
                // Marcar como recontado
                recountedPlayers.add(player.getUniqueId());
                saveData();
                
                plugin.getLogger().info("[EcoSkillsRecount] Successfully recounted " + player.getName());
                
                // Mensaje opcional al jugador
                if (plugin.getConfig().getBoolean("modules.ecoskillsrecount.notify-player", false)) {
                    String message = plugin.getMessage("ecoskillsrecount-done");
                    if (message != null && !message.isEmpty()) {
                        player.sendMessage(message);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[EcoSkillsRecount] Error executing recount for " + player.getName() + ": " + e.getMessage());
            }
        });
    }

    /**
     * Permite resetear el estado de recount de un jugador (por comando admin si se necesita)
     */
    public void resetPlayer(UUID uuid) {
        if (recountedPlayers.remove(uuid)) {
            saveData();
        }
    }

    /**
     * Obtiene el conjunto de jugadores ya recontados (solo lectura)
     */
    public Set<UUID> getRecountedPlayers() {
        return new HashSet<>(recountedPlayers);
    }
}
