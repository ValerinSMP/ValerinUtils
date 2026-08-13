package me.mtynnn.valerinutils.modules.vipslots;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

public final class VipSlotsModule implements Listener {
    private final ValerinUtils plugin;
    private boolean enabled;

    public VipSlotsModule(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    public void reload(FileConfiguration settings) {
        enabled = settings != null && settings.getBoolean("vvipslots.enabled", true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onLogin(PlayerLoginEvent event) {
        if (!enabled || !shouldBypass(event.getResult(),
                event.getPlayer().hasPermission("vvipslots.bypass"))) return;
        event.allow();
        plugin.getLogger().info(event.getPlayer().getName()
                + " bypassed full server via vvipslots.bypass");
    }

    static boolean shouldBypass(PlayerLoginEvent.Result result, boolean permission) {
        return result == PlayerLoginEvent.Result.KICK_FULL && permission;
    }
}
