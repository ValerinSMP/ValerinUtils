package me.Mtynnn.valerinUtils.modules.vote40;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import me.Mtynnn.valerinUtils.ValerinUtils;
import me.Mtynnn.valerinUtils.core.Module;
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
        // Listeners are automatically unregistered by Bukkit on plugin disable
    }

    @EventHandler
    public void onVotifierEvent(VotifierEvent event) {
        Vote vote = event.getVote();
        String serviceName = vote.getServiceName();
        String username = vote.getUsername();

        FileConfiguration config = plugin.getConfig();
        String targetService = config.getString("modules.vote40.service-name", "40Servidores");

        if (serviceName.equalsIgnoreCase(targetService)) {
            long delaySeconds = config.getLong("modules.vote40.delay-seconds", 30);
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
        } else if (plugin.isDebug()) {
            plugin.getLogger().info("[Vote40] Ignorado voto de servicio " + serviceName + " (esperado "
                    + targetService + ")");
        }
    }
}
