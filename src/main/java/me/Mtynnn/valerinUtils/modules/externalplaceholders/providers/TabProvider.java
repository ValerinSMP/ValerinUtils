package me.Mtynnn.valerinUtils.modules.externalplaceholders.providers;

import me.Mtynnn.valerinUtils.ValerinUtils;
import me.neznamy.tab.api.TabAPI;
import me.neznamy.tab.api.TabPlayer;
import me.neznamy.tab.api.bossbar.BossBarManager;
import me.neznamy.tab.api.nametag.NameTagManager;
import me.neznamy.tab.api.scoreboard.ScoreboardManager;
import org.bukkit.entity.Player;

public class TabProvider implements PlaceholderProvider {

    private final ValerinUtils plugin;

    public TabProvider(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "tab";
    }

    @Override
    public String getPluginName() {
        return "TAB";
    }

    @Override
    public String onPlaceholderRequest(Player player, String identifier) {
        TabAPI api = TabAPI.getInstance();
        if (api == null) {
            return null;
        }
        
        TabPlayer tabPlayer = api.getPlayer(player.getUniqueId());
        if (tabPlayer == null) {
            return null;
        }
        
        switch (identifier.toLowerCase()) {
            case "scoreboard_enabled":
                ScoreboardManager scoreboardManager = api.getScoreboardManager();
                if (scoreboardManager == null) {
                    return null; // Scoreboard feature not enabled in TAB config
                }
                return scoreboardManager.hasScoreboardVisible(tabPlayer) ? "true" : "false";
                
            case "bossbar_enabled":
                BossBarManager bossBarManager = api.getBossBarManager();
                if (bossBarManager == null) {
                    return null; // BossBar feature not enabled in TAB config
                }
                return bossBarManager.hasBossBarVisible(tabPlayer) ? "true" : "false";
                
            case "nametag_enabled":
                NameTagManager nameTagManager = api.getNameTagManager();
                if (nameTagManager == null) {
                    return null; // Nametag feature not enabled in TAB config
                }
                // hasHiddenNameTagVisibilityView returns TRUE if HIDDEN, so we invert it
                return !nameTagManager.hasHiddenNameTagVisibilityView(tabPlayer) ? "true" : "false";
                
            default:
                return null;
        }
    }

    @Override
    public void reload() {
        // No local data to reload - reads directly from TAB API
    }
}
