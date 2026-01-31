package me.mtynnn.valerinutils.modules.killrewards;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class KillRewardsModule implements Module, Listener {

    private final ValerinUtils plugin;
    private File dataFile;
    private FileConfiguration dataConfig;

    // Cache para KDR: UUID -> int[]{kills, deaths}
    private final Map<UUID, int[]> statsCache = new HashMap<>();

    // Cache para Cooldowns: KillerUUID -> Map<VictimUUID, Timestamp>
    private final Map<UUID, Map<UUID, Long>> cooldowns = new HashMap<>();

    // Cache para Límite Diario: KillerUUID -> Pair<TimestampDia, Count>
    // Usaremos un string "Dia" (epoch day) para simplificar
    private final Map<UUID, DailyTracker> dailyLimits = new HashMap<>();

    private static class DailyTracker {
        long lastDay;
        int count;

        DailyTracker(long lastDay, int count) {
            this.lastDay = lastDay;
            this.count = count;
        }
    }

    public KillRewardsModule(ValerinUtils plugin) {
        this.plugin = plugin;
        loadData();
    }

    @Override
    public String getId() {
        return "killrewards";
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        saveData();
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Actualizar stats KDR siempre, independientemente de si hay recompensa
        updateStats(victim, killer);

        if (killer == null)
            return;
        if (victim.equals(killer))
            return; // No suicidios

        if (!checksPass(killer, victim)) {
            return;
        }

        giveRewards(killer, victim);
    }

    private void updateStats(Player victim, Player killer) {
        // Victim death
        int[] vStats = statsCache.computeIfAbsent(victim.getUniqueId(), k -> new int[] { 0, 0 });
        vStats[1]++; // Add death

        // Killer kill (si existe)
        if (killer != null) {
            int[] kStats = statsCache.computeIfAbsent(killer.getUniqueId(), k -> new int[] { 0, 0 });
            kStats[0]++; // Add kill
        }
    }

    private boolean checksPass(Player killer, Player victim) {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("modules.killrewards.anti-abuse");
        if (config == null)
            return true;

        // 1. Same IP
        if (config.getBoolean("same-ip-check", true)) {
            String ip1 = getIp(killer);
            String ip2 = getIp(victim);
            if (ip1 != null && ip1.equals(ip2)) {
                if (plugin.isDebug())
                    plugin.getLogger()
                            .info("[KillRewards] Same IP detected: " + killer.getName() + " -> " + victim.getName());
                return false;
            }
        }

        // 2. Playtime Victim
        int minPlayTime = config.getInt("victim.min-playtime-minutes", 0);
        if (minPlayTime > 0) {
            long ticks = victim.getStatistic(Statistic.PLAY_ONE_MINUTE);
            long minutes = (ticks / 20) / 60;
            if (minutes < minPlayTime) {
                if (plugin.isDebug())
                    plugin.getLogger().info("[KillRewards] Victim playtime too low: " + minutes + " < " + minPlayTime);
                return false;
            }
        }

        // 3. KDR Victim
        double minKdr = config.getDouble("victim.min-kdr", 0.0);
        if (minKdr > 0) {
            double kdr = calculateKdr(victim.getUniqueId());
            if (kdr < minKdr) {
                if (plugin.isDebug())
                    plugin.getLogger().info("[KillRewards] Victim KDR too low: " + kdr + " < " + minKdr);
                return false;
            }
        }

        // 4. Cooldown per victim
        long cooldownSec = config.getLong("cooldown-per-victim", 0);
        if (cooldownSec > 0) {
            long now = System.currentTimeMillis();
            Map<UUID, Long> killerCooldowns = cooldowns.computeIfAbsent(killer.getUniqueId(), k -> new HashMap<>());
            long lastKill = killerCooldowns.getOrDefault(victim.getUniqueId(), 0L);

            if (now - lastKill < cooldownSec * 1000L) {
                if (plugin.isDebug())
                    plugin.getLogger().info("[KillRewards] Cooldown active for victim: " + victim.getName());
                return false;
            }
            // Update cooldown is done after confirming reward
        }

        // 5. Daily limit
        int limit = config.getInt("daily-limit", 0);
        if (limit > 0) {
            long currentDay = System.currentTimeMillis() / 86400000L; // Dias desde epoch
            DailyTracker tracker = dailyLimits.computeIfAbsent(killer.getUniqueId(),
                    k -> new DailyTracker(currentDay, 0));

            if (tracker.lastDay != currentDay) {
                tracker.lastDay = currentDay;
                tracker.count = 0;
            }

            if (tracker.count >= limit) {
                if (plugin.isDebug())
                    plugin.getLogger().info("[KillRewards] Daily limit reached for: " + killer.getName());
                return false;
            }
        }

        return true;
    }

    private void giveRewards(Player killer, Player victim) {
        ConfigurationSection config = plugin.getConfig().getConfigurationSection("modules.killrewards");
        if (config == null)
            return;

        // Update limits/cooldowns first
        long now = System.currentTimeMillis();

        // Cooldown
        if (config.getLong("anti-abuse.cooldown-per-victim", 0) > 0) {
            cooldowns.computeIfAbsent(killer.getUniqueId(), k -> new HashMap<>())
                    .put(victim.getUniqueId(), now);
        }

        // Daily
        if (config.getInt("anti-abuse.daily-limit", 0) > 0) {
            DailyTracker tracker = dailyLimits.get(killer.getUniqueId());
            if (tracker != null)
                tracker.count++;
        }

        // Process rewards
        List<Map<?, ?>> rewards = config.getMapList("rewards");
        Random random = new Random();

        for (Map<?, ?> reward : rewards) {
            double chance = 100.0;
            if (reward.containsKey("chance")) {
                Object c = reward.get("chance");
                if (c instanceof Number)
                    chance = ((Number) c).doubleValue();
            }

            if (random.nextDouble() * 100.0 <= chance) {
                List<String> commands = new ArrayList<>();
                Object cmds = reward.get("commands");
                if (cmds instanceof List) {
                    commands = (List<String>) cmds;
                } else if (cmds instanceof String) {
                    commands.add((String) cmds);
                }

                for (String cmd : commands) {
                    if (cmd == null)
                        continue;
                    String parsed = cmd.replace("%killer%", killer.getName())
                            .replace("%victim%", victim.getName());
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
                }
            }
        }

        // Save async to avoid lag spikes during combat?
        // For simplicity saving sync but periodically would be better.
        // Let's stick to onDisable save for performance, but maybe save daily limits
        // more often?
        // Let's rely on onDisable for now, risky if crash but faster.
    }

    private String getIp(Player p) {
        if (p.getAddress() != null) {
            return p.getAddress().getAddress().getHostAddress();
        }
        return null;
    }

    private double calculateKdr(UUID uuid) {
        int[] s = statsCache.get(uuid);
        if (s == null)
            return 0.0; // No data yet

        int kills = s[0];
        int deaths = s[1];

        if (deaths == 0)
            return kills > 0 ? (double) kills : 0.0;
        return (double) kills / (double) deaths;
    }

    // Persistencia
    private void loadData() {
        dataFile = new File(plugin.getDataFolder(), "killrewards_data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException ignored) {
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        // Load Stats
        if (dataConfig.contains("stats")) {
            for (String key : dataConfig.getConfigurationSection("stats").getKeys(false)) {
                List<Integer> vals = dataConfig.getIntegerList("stats." + key);
                if (vals.size() >= 2) {
                    statsCache.put(UUID.fromString(key), new int[] { vals.get(0), vals.get(1) });
                }
            }
        }

        // Load Cooldowns
        // Estructura: cooldowns.<killerUUID>.<victimUUID> = timestamp
        if (dataConfig.contains("cooldowns")) {
            ConfigurationSection sec = dataConfig.getConfigurationSection("cooldowns");
            for (String kUuid : sec.getKeys(false)) {
                ConfigurationSection vSec = sec.getConfigurationSection(kUuid);
                if (vSec != null) {
                    Map<UUID, Long> map = new HashMap<>();
                    for (String vUuid : vSec.getKeys(false)) {
                        map.put(UUID.fromString(vUuid), vSec.getLong(vUuid));
                    }
                    cooldowns.put(UUID.fromString(kUuid), map);
                }
            }
        }

        // Load Daily Limits
        // Estructura: daily.<killerUUID>.day = long, daily.<killerUUID>.count = int
        if (dataConfig.contains("daily")) {
            ConfigurationSection sec = dataConfig.getConfigurationSection("daily");
            for (String kUuid : sec.getKeys(false)) {
                long day = sec.getLong(kUuid + ".day");
                int count = sec.getInt(kUuid + ".count");
                dailyLimits.put(UUID.fromString(kUuid), new DailyTracker(day, count));
            }
        }
    }

    private void saveData() {
        // Stats
        for (Map.Entry<UUID, int[]> entry : statsCache.entrySet()) {
            dataConfig.set("stats." + entry.getKey().toString(), List.of(entry.getValue()[0], entry.getValue()[1]));
        }

        // Cooldowns
        // Limpiar cooldowns expirados antes de guardar para no inflar archivo?
        // Por simplicidad guardamos todo por ahora, o podríamos filtrar.
        long now = System.currentTimeMillis();
        long maxCooldown = plugin.getConfig().getLong("modules.killrewards.anti-abuse.cooldown-per-victim", 0) * 1000L;

        for (Map.Entry<UUID, Map<UUID, Long>> entry : cooldowns.entrySet()) {
            String kUuid = entry.getKey().toString();
            for (Map.Entry<UUID, Long> inner : entry.getValue().entrySet()) {
                // Solo guardar si todavía es relevante (si el tiempo que pasó es menor al
                // cooldown)
                // Aunque el usuario podría cambiar la config... mejor guardar si es reciente.
                // Guardemos todos para asegurar persistencia correcta.
                dataConfig.set("cooldowns." + kUuid + "." + inner.getKey().toString(), inner.getValue());
            }
        }

        // Daily
        for (Map.Entry<UUID, DailyTracker> entry : dailyLimits.entrySet()) {
            String kUuid = entry.getKey().toString();
            dataConfig.set("daily." + kUuid + ".day", entry.getValue().lastDay);
            dataConfig.set("daily." + kUuid + ".count", entry.getValue().count);
        }

        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar killrewards_data.yml");
        }
    }
}
