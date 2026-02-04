package me.mtynnn.valerinutils.modules.votetracking;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

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

        CompletableFuture.runAsync(() -> {
            // Need UUID. If player is online, easy. If offline, need to fetch.
            // Votifier usually provides username.
            OfflinePlayer player = Bukkit.getOfflinePlayer(username);
            if (player == null || !player.hasPlayedBefore()) {
                // Try to find if online
                org.bukkit.entity.Player online = Bukkit.getPlayerExact(username);
                if (online != null) {
                    recordVote(online.getUniqueId(), service, timestamp);
                } else {
                    // Best effort UUID fetch or ignore?
                    // If player never played, we can't really track stats for them effectively in
                    // our DB keyed by UUID?
                    // But we can try to get UUID.
                    if (player != null && player.getUniqueId() != null) {
                        recordVote(player.getUniqueId(), service, timestamp);
                    } else {
                        plugin.getLogger().warning("[VoteTracking] Could not resolve UUID for voter: " + username);
                    }
                }
            } else {
                recordVote(player.getUniqueId(), service, timestamp);
            }
        });
    }

    private void recordVote(UUID uuid, String service, long timestamp) {
        plugin.getDatabaseManager().addVote(uuid.toString(), service, timestamp);
        if (plugin.isDebug()) {
            plugin.getLogger().info("[VoteTracking] Vote recorded for " + uuid + " from " + service);
        }
    }
}
