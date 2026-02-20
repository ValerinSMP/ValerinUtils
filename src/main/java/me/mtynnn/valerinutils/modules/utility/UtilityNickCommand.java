package me.mtynnn.valerinutils.modules.utility;

import me.mtynnn.valerinutils.core.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

final class UtilityNickCommand {

    private final UtilityModule module;
    private final UtilityNickManager nickManager;

    UtilityNickCommand(UtilityModule module) {
        this.module = module;
        this.nickManager = new UtilityNickManager();
    }

    void execute(Player player, String[] args) {
        if (!module.checkStatus(player, "nick")) {
            return;
        }
        if (args.length == 0) {
            player.sendMessage(module.getMessage("nick-usage"));
            return;
        }

        PlayerData playerData = module.plugin().getPlayerData(player.getUniqueId());
        if (playerData == null) {
            return;
        }

        if (args[0].equalsIgnoreCase("off")) {
            playerData.setNickname(null);
            player.displayName(Component.text(player.getName()));
            player.playerListName(Component.text(player.getName()));
            player.sendMessage(module.getMessage("nick-off"));
            return;
        }

        String nickRaw = String.join(" ", args);
        UtilityNickManager.NickTier tier = nickManager.resolveTier(player);
        if (!nickManager.isFormatAllowed(nickRaw, tier)) {
            player.sendMessage(module.getMessage("nick-format-not-allowed")
                    .replace("%tier%", tier.asConfigValue()));
            return;
        }

        playerData.setNickname(nickRaw);
        Component nickComponent = module.plugin().parseComponent(nickRaw);
        player.displayName(nickComponent);
        player.playerListName(nickComponent);
        player.sendMessage(module.getMessage("nick-success").replace("%nick%", module.plugin().translateColors(nickRaw)));
    }
}
