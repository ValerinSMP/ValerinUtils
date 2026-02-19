package me.mtynnn.valerinutils.modules.killrewards;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import me.mtynnn.valerinutils.core.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.*;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.RegisteredServiceProvider;

public class KillRewardsModule implements Module, Listener {

    private final ValerinUtils plugin;
    private Economy economy = null;

    // Cache para Cooldowns: KillerUUID -> Map<VictimUUID, Timestamp>
    private final Map<UUID, Map<UUID, Long>> cooldowns = new HashMap<>();

    public KillRewardsModule(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "killrewards";
    }

    @Override
    public void enable() {
        setupEconomy();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        cooldowns.clear();
    }

    private FileConfiguration getConfig() {
        return plugin.getConfigManager().getConfig("killrewards");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        debug("PlayerDeathEvent triggered (LOWEST). Victim: " + victim.getName() +
                ", Killer: " + (killer != null ? killer.getName() : "null"));

        // Actualizar stats (en memoria/BD via PlayerData)
        updateStats(victim, killer);

        if (killer == null) {
            debug("No killer, skipping rewards.");
            return;
        }
        if (victim.equals(killer)) {
            debug("Suicide, skipping rewards.");
            return;
        }

        if (!checksPass(killer, victim)) {
            debug("Checks failed for " + killer.getName() + " -> " + victim.getName());
            return;
        }

        debug("All checks passed! Processing rewards for " + killer.getName());

        // 1. Percentage Reward (Vault)
        handlePercentageReward(killer, victim);

        // 2. Standard Rewards (Commands)
        giveRewards(killer, victim);
    }

    private void handlePercentageReward(Player killer, Player victim) {
        if (economy == null) {
            debug("Vault Economy not found, skipping percentage reward.");
            return;
        }

        FileConfiguration cfg = getConfig();
        if (cfg == null || !cfg.getBoolean("percentage-reward.enabled", false)) {
            return;
        }

        double percentage = cfg.getDouble("percentage-reward.percentage", 0.0);
        if (percentage <= 0)
            return;

        double victimBalance = economy.getBalance(victim);
        double rewardAmount = victimBalance * (percentage / 100.0);

        if (rewardAmount > 0) {
            economy.depositPlayer(killer, rewardAmount);
            debug("Deposited " + rewardAmount + " to " + killer.getName() + " (from " + victimBalance + ")");

            String rewardMsg = cfg.getString("percentage-reward.reward-message", "&aReceived $%amount%");
            rewardMsg = rewardMsg.replace("%amount%", String.format("%.2f", rewardAmount))
                    .replace("%victim%", victim.getName())
                    .replace("%percentage%", String.valueOf(percentage));
            killer.sendMessage(plugin.translateColors(rewardMsg));
        } else {
            // Optional: fallback message or debug
            debug("Reward amount is 0, skipping deposit.");
        }
    }

    private void updateStats(Player victim, Player killer) {
        // Victim death
        PlayerData vData = plugin.getPlayerData(victim.getUniqueId());
        if (vData != null) {
            vData.addDeath();
            // Async save is handled by global auto-save or quit
        }

        // Killer kill
        if (killer != null) {
            PlayerData kData = plugin.getPlayerData(killer.getUniqueId());
            if (kData != null) {
                kData.addKill();
            }
        }
    }

    private boolean checksPass(Player killer, Player victim) {
        FileConfiguration cfg = getConfig();
        if (cfg == null) {
            debug("Config is null! Module may not be properly initialized.");
            return false;
        }
        if (!cfg.getBoolean("enabled", true)) {
            debug("Module is disabled in config.");
            return false;
        }

        List<String> disabledWorlds = cfg.getStringList("disabled-worlds");
        if (disabledWorlds.contains(victim.getWorld().getName())) {
            debug("KillRewards disabled in world: " + victim.getWorld().getName());
            return false;
        }

        ConfigurationSection antiAbuse = cfg.getConfigurationSection("anti-abuse");
        if (antiAbuse == null) {
            debug("No anti-abuse section, all checks pass.");
            return true; // No settings = pass
        }

        // 1. Same IP
        if (antiAbuse.getBoolean("same-ip-check", true)) {
            String ip1 = getIp(killer);
            String ip2 = getIp(victim);
            if (ip1 != null && ip1.equals(ip2)) {
                debug("Same IP detected: " + killer.getName() + " -> " + victim.getName());
                return false;
            }
        }

        // 2. Playtime Victim
        int minPlayTime = antiAbuse.getInt("victim.min-playtime-minutes", 0);
        if (minPlayTime > 0) {
            long ticks = victim.getStatistic(Statistic.PLAY_ONE_MINUTE);
            long minutes = (ticks / 20) / 60;
            if (minutes < minPlayTime) {
                debug("Victim playtime too low: " + minutes + " < " + minPlayTime);
                return false;
            }
        }

        // 3. KDR Victim
        double minKdr = antiAbuse.getDouble("victim.min-kdr", 0.0);
        if (minKdr > 0) {
            // Use cached data from DB
            PlayerData vData = plugin.getPlayerData(victim.getUniqueId());
            double kdr = 0.0;
            if (vData != null) {
                int k = vData.getKills();
                int d = vData.getDeaths();
                if (d == 0)
                    kdr = k > 0 ? (double) k : 0.0;
                else
                    kdr = (double) k / d;
            }

            if (kdr < minKdr) {
                debug("Victim KDR too low: " + kdr + " < " + minKdr);
                return false;
            }
        }

        // 4. Cooldown per victim
        long cooldownSec = antiAbuse.getLong("cooldown-per-victim", 0);
        if (cooldownSec > 0) {
            long now = System.currentTimeMillis();
            Map<UUID, Long> killerCooldowns = cooldowns.computeIfAbsent(killer.getUniqueId(), k -> new HashMap<>());
            long lastKill = killerCooldowns.getOrDefault(victim.getUniqueId(), 0L);

            if (now - lastKill < cooldownSec * 1000L) {
                debug("Cooldown active for victim: " + victim.getName());
                return false;
            }
        }

        // 5. Daily limit
        int limit = antiAbuse.getInt("daily-limit", 0);
        if (limit > 0) {
            long currentDay = System.currentTimeMillis() / 86400000L; // Days since epoch
            PlayerData kData = plugin.getPlayerData(killer.getUniqueId());

            if (kData != null) {
                if (kData.getLastDailyReset() != currentDay) {
                    kData.setLastDailyReset(currentDay);
                    kData.setDailyRewardsCount(0);
                }

                if (kData.getDailyRewardsCount() >= limit) {
                    debug("Daily limit reached for: " + killer.getName());
                    return false;
                }
            }
        }

        // 6. Team Abuse Check
        if (antiAbuse.getBoolean("prevent-team-killing", false)) {
            if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
                try {
                    // Use PAPI to check if both are in the same team
                    // %vteams_team_id% usually returns an ID if in a team
                    String killerTeam = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(killer,
                            "%vteams_team_id%");
                    String victimTeam = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(victim,
                            "%vteams_team_id%");

                    // Verify parsed values are valid (PAPI returns strict string if not parsed?)
                    // Usually returns "identifier_param" if failed, or empty string.
                    // We check if it is not null, not empty, and not the raw placeholder.
                    boolean kValid = killerTeam != null && !killerTeam.isEmpty() && !killerTeam.startsWith("%");
                    boolean vValid = victimTeam != null && !victimTeam.isEmpty() && !victimTeam.startsWith("%");

                    if (kValid && vValid && killerTeam.equals(victimTeam)) {
                        debug("Same team detected (" + killerTeam + "): " + killer.getName() + " -> "
                                + victim.getName());
                        // Send message
                        String msg = plugin.getConfigManager().getConfig("settings").getString(
                                "messages.killrewards.team-kill-deny", "&cNo puedes abusar matando a tu equipo.");
                        if (msg != null && !msg.isEmpty()) {
                            killer.sendMessage(plugin.translateColors(msg));
                        }
                        return false;
                    }

                } catch (NoClassDefFoundError | Exception e) {
                    debug("Error checking team placeholders: " + e.getMessage());
                }
            }
        }

        return true;
    }

    private void giveRewards(Player killer, Player victim) {
        FileConfiguration cfg = getConfig();
        if (cfg == null)
            return;

        long now = System.currentTimeMillis();

        // Update Cooldown
        if (cfg.getLong("anti-abuse.cooldown-per-victim", 0) > 0) {
            cooldowns.computeIfAbsent(killer.getUniqueId(), k -> new HashMap<>())
                    .put(victim.getUniqueId(), now);
        }

        // Update Daily
        if (cfg.getInt("anti-abuse.daily-limit", 0) > 0) {
            PlayerData kData = plugin.getPlayerData(killer.getUniqueId());
            if (kData != null) {
                kData.setDailyRewardsCount(kData.getDailyRewardsCount() + 1);
            }
        }

        // Process rewards
        List<Map<?, ?>> rewards = cfg.getMapList("rewards");
        Random random = new Random();
        debug("Processing " + rewards.size() + " rewards...");

        for (Map<?, ?> reward : rewards) {
            double chance = 100.0;
            if (reward.containsKey("chance")) {
                Object c = reward.get("chance");
                if (c instanceof Number)
                    chance = ((Number) c).doubleValue();
            }

            double roll = random.nextDouble() * 100.0;
            if (roll <= chance) {
                debug("Reward chance hit (" + roll + " <= " + chance + ")");
                List<String> commands = new ArrayList<>();
                Object cmds = reward.get("commands");
                if (cmds instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> cmdList = (List<String>) cmds;
                    commands = cmdList;
                } else if (cmds instanceof String) {
                    commands.add((String) cmds);
                }

                for (String cmd : commands) {
                    if (cmd == null)
                        continue;
                    String parsed = cmd.replace("%killer%", killer.getName())
                            .replace("%victim%", victim.getName())
                            .replace("%player%", killer.getName());
                    debug("Executing command: " + parsed);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
                }
            } else {
                debug("Reward chance missed (" + roll + " > " + chance + ")");
            }
        }
    }

    private String getIp(Player p) {
        if (p.getAddress() != null) {
            return p.getAddress().getAddress().getHostAddress();
        }
        return null;
    }

    private void debug(String msg) {
        plugin.debug(getId(), msg);
    }
}
