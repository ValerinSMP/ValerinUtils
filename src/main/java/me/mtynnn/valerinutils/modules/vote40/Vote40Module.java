package me.mtynnn.valerinutils.modules.vote40;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class Vote40Module implements Module, Listener {

    private final ValerinUtils plugin;

    public Vote40Module(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "vote40";
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
        String serviceName = vote.getServiceName();
        String username = vote.getUsername();

        FileConfiguration config = plugin.getConfigManager().getConfig("vote40");
        String targetService = config.getString("service-name", "40Servidores");

        if (serviceName.equalsIgnoreCase(targetService)) {
            long delaySeconds = config.getLong("delay-seconds", 30);
            long delayTicks = delaySeconds * 20L;

            plugin.getLogger().info("Voto recibido de " + username + " en " + serviceName + ". Ejecutando /vote40 en "
                    + delaySeconds + " segundos.");

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Player player = Bukkit.getPlayerExact(username);
                if (player != null && player.isOnline()) {
                    player.performCommand("vote40");
                    plugin.getLogger().info("Ejecutado /vote40 para " + username);
                }
            }, delayTicks);
        } else {
            plugin.debug(getId(), "Ignorado voto de servicio " + serviceName + " (esperado " + targetService + ")");
        }
    }
}
