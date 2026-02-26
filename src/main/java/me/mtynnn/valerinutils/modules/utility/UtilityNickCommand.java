package me.mtynnn.valerinutils.modules.utility;

import me.mtynnn.valerinutils.core.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

final class UtilityNickCommand {

    private final UtilityModule module;
    private final UtilityNickManager nickManager;

    UtilityNickCommand(UtilityModule module) {
        this.module = module;
        this.nickManager = new UtilityNickManager();
    }

    void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(module.getMessage("nick-usage"));
            return;
        }

        Player senderPlayer = sender instanceof Player player ? player : null;
        boolean othersMode = args.length >= 2;

        if (othersMode) {
            if (!hasOthersAccess(sender)) {
                return;
            }
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(module.getMessage("player-not-found"));
                return;
            }
            applyNick(sender, target, args, 1);
            return;
        }

        if (senderPlayer == null) {
            sender.sendMessage(module.getMessage("nick-usage-others"));
            return;
        }

        if (!module.checkStatus(senderPlayer, "nick")) {
            return;
        }
        applyNick(sender, senderPlayer, args, 0);
    }

    private void applyNick(CommandSender sender, Player target, String[] args, int startIndex) {
        PlayerData playerData = module.plugin().getPlayerData(target.getUniqueId());
        if (playerData == null) {
            sender.sendMessage(module.getMessage("player-not-found"));
            return;
        }

        if (args[startIndex].equalsIgnoreCase("off")) {
            playerData.setNickname(null);
            target.displayName(Component.text(target.getName()));
            target.playerListName(Component.text(target.getName()));
            if (sender.getName().equalsIgnoreCase(target.getName())) {
                target.sendMessage(module.getMessage("nick-off"));
            } else {
                sender.sendMessage(module.getMessage("nick-off-others").replace("%player%", target.getName()));
                target.sendMessage(module.getMessage("nick-off"));
            }
            return;
        }

        String nickRaw = String.join(" ", java.util.Arrays.copyOfRange(args, startIndex, args.length));
        if (nickManager.containsWhitespace(nickRaw)) {
            sender.sendMessage(module.getMessage("nick-no-spaces"));
            return;
        }

        UtilityNickManager.NickTier tier = nickManager.resolveTier(target);
        if (!nickManager.isFormatAllowed(nickRaw, tier)) {
            sender.sendMessage(module.getMessage("nick-format-not-allowed")
                    .replace("%tier%", tier.asConfigValue()));
            return;
        }

        String nickFinal = nickManager.withTrailingReset(nickRaw);
        playerData.setNickname(nickFinal);
        Component nickComponent = module.plugin().parseComponent(nickFinal);
        target.displayName(nickComponent);
        target.playerListName(nickComponent);

        if (sender.getName().equalsIgnoreCase(target.getName())) {
            target.sendMessage(module.getMessage("nick-success").replace("%nick%", module.plugin().translateColors(nickFinal)));
        } else {
            sender.sendMessage(module.getMessage("nick-success-others")
                    .replace("%player%", target.getName())
                    .replace("%nick%", module.plugin().translateColors(nickFinal)));
            target.sendMessage(module.getMessage("nick-success").replace("%nick%", module.plugin().translateColors(nickFinal)));
        }
    }

    private boolean hasOthersAccess(CommandSender sender) {
        FileConfiguration cfg = module.getConfig();
        if (cfg == null || !cfg.getBoolean("enabled", true)) {
            return false;
        }
        if (!cfg.getBoolean("commands.nick.enabled", true)) {
            sender.sendMessage(module.getMessage("module-disabled"));
            return false;
        }
        if (!cfg.getBoolean("commands.nick.others-enabled", true)) {
            sender.sendMessage(module.getMessage("module-disabled"));
            return false;
        }
        if (!sender.hasPermission("valerinutils.utility.nick.others")) {
            sender.sendMessage(module.getMessage("no-permission"));
            return false;
        }
        return true;
    }
}
