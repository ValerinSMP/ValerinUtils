package me.mtynnn.valerinutils.modules.votetracking;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class VoteTrackingModule implements Module, Listener {

    private final ValerinUtils plugin;

    public VoteTrackingModule(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "votetracking";
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        org.bukkit.event.HandlerList.unregisterAll(this);
    }

    @EventHandler
    public void onVotifierEvent(VotifierEvent event) {
        Vote vote = event.getVote();
        String username = vote.getUsername();
        String service = vote.getServiceName();
        long timestamp = System.currentTimeMillis();

        plugin.getLogger().info("[VoteTracking] Voto recibido de " + username + " (Servicio: " + service + ")");

        CompletableFuture.runAsync(() -> {
            OfflinePlayer player = Bukkit.getOfflinePlayer(username);
            if (player == null || !player.hasPlayedBefore()) {
                Player online = Bukkit.getPlayerExact(username);
                if (online != null) {
                    recordVote(online, service, timestamp);
                } else {
                    if (player != null && player.getUniqueId() != null) {
                        recordVoteOffline(player.getUniqueId(), service, timestamp);
                    } else {
                        plugin.getLogger().warning("[VoteTracking] Could not resolve UUID for voter: " + username);
                    }
                }
            } else {
                Player online = Bukkit.getPlayer(player.getUniqueId());
                if (online != null) {
                    recordVote(online, service, timestamp);
                } else {
                    recordVoteOffline(player.getUniqueId(), service, timestamp);
                }
            }
        });
    }

    private void recordVote(Player player, String service, long timestamp) {
        recordVoteOffline(player.getUniqueId(), service, timestamp);

        // Handle Online Feedback & Global Broadcast
        Bukkit.getScheduler().runTask(plugin, () -> {
            handleFeedback(player);
            handleBroadcast(player);
        });
    }

    private void recordVoteOffline(UUID uuid, String service, long timestamp) {
        plugin.getDatabaseManager().addVote(uuid.toString(), service, timestamp);
        plugin.debug(getId(), "Vote recorded for " + uuid + " from " + service);
    }

    private void handleFeedback(Player player) {
        FileConfiguration config = plugin.getConfigManager().getConfig("votetracking");
        if (config == null || !config.getBoolean("feedback.enabled", false))
            return;

        ConfigurationSection feedback = config.getConfigurationSection("feedback");
        if (feedback == null)
            return;

        // 1. Commands
        List<String> commands = feedback.getStringList("commands");
        for (String cmd : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
        }

        // 2. Messages
        String chatMsg = feedback.getString("chat-message");
        if (chatMsg != null && !chatMsg.isEmpty()) {
            player.sendMessage(plugin.translateColors(chatMsg));
        }

        String actionBar = feedback.getString("action-bar");
        if (actionBar != null && !actionBar.isEmpty()) {
            player.sendActionBar(plugin.parseComponent(actionBar));
        }

        // 3. Sounds
        List<Map<?, ?>> sounds = feedback.getMapList("sounds");
        for (Map<?, ?> sMap : sounds) {
            try {
                String name = (String) sMap.get("sound");
                Object volObj = sMap.get("vol");
                float vol = (volObj instanceof Number) ? ((Number) volObj).floatValue() : 1.0f;
                Object pitchObj = sMap.get("pitch");
                float pitch = (pitchObj instanceof Number) ? ((Number) pitchObj).floatValue() : 1.0f;
                if (name != null) {
                    player.playSound(player.getLocation(), Sound.valueOf(name), vol, pitch);
                }
            } catch (Exception ignored) {
            }
        }

        // 4. Particles
        if (feedback.getBoolean("particles.enabled", true)) {
            try {
                String type = feedback.getString("particles.type", "TOTEM_OF_UNDYING");
                int amount = feedback.getInt("particles.amount", 30);
                double offset = feedback.getDouble("particles.offset", 0.5);
                double speed = feedback.getDouble("particles.speed", 0.5);
                player.getWorld().spawnParticle(Particle.valueOf(type), player.getLocation().add(0, 1, 0), amount,
                        offset, offset, offset, speed);
            } catch (Exception ignored) {
            }
        }
    }

    private void handleBroadcast(Player player) {
        FileConfiguration config = plugin.getConfigManager().getConfig("votetracking");
        if (config == null || !config.getBoolean("broadcast.enabled", false))
            return;

        List<String> lines = config.getStringList("broadcast.chat-message");
        if (lines == null || lines.isEmpty())
            return;

        for (String line : lines) {
            Bukkit.broadcast(plugin.parseComponent(line.replace("%player%", player.getName())));
        }
    }
}
