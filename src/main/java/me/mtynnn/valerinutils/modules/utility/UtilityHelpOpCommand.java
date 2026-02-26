package me.mtynnn.valerinutils.modules.utility;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

final class UtilityHelpOpCommand {

    private static final String RECEIVE_PERMISSION = "valerinutils.utility.helpop.receive";
    private static final String BYPASS_COOLDOWN_PERMISSION = "valerinutils.utility.helpop.bypasscooldown";
    private final UtilityModule module;
    private final Map<UUID, Long> cooldownByPlayer = new HashMap<>();

    UtilityHelpOpCommand(UtilityModule module) {
        this.module = module;
    }

    void execute(Player sender, String[] args) {
        if (!module.checkStatus(sender, "helpop")) {
            return;
        }

        if (args.length == 0) {
            sender.sendMessage(module.getMessage("helpop-usage"));
            return;
        }

        String message = String.join(" ", args).trim();
        if (message.isBlank()) {
            sender.sendMessage(module.getMessage("helpop-usage"));
            return;
        }

        int cooldownSeconds = Math.max(0, module.getConfig().getInt("commands.helpop.cooldown-seconds", 20));
        if (cooldownSeconds > 0 && !sender.hasPermission(BYPASS_COOLDOWN_PERMISSION)) {
            long now = System.currentTimeMillis();
            Long nextUseAt = cooldownByPlayer.get(sender.getUniqueId());
            if (nextUseAt != null && nextUseAt > now) {
                long secondsLeft = Math.max(1L, (nextUseAt - now + 999L) / 1000L);
                sender.sendMessage(module.getMessage("helpop-cooldown")
                        .replace("%time%", String.valueOf(secondsLeft)));
                return;
            }
            cooldownByPlayer.put(sender.getUniqueId(), now + (cooldownSeconds * 1000L));
        }

        String format = module.getConfig().getString("messages.helpop-received",
                "<dark_gray>[<red>ʜᴇʟᴘᴏᴘ<dark_gray>] <white>%player% <gray>» <white>%message%");
        Component component = module.plugin().parseComponent(format
                .replace("%player%", sender.getName())
                .replace("%message%", message));

        int receivers = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (!target.hasPermission(RECEIVE_PERMISSION)) {
                continue;
            }
            target.sendMessage(component);
            module.playSound(target, "helpop-receive");
            receivers++;
        }

        if (module.getConfig().getBoolean("commands.helpop.send-to-console", true)) {
            Bukkit.getConsoleSender().sendMessage(component);
        }

        module.playSound(sender, "helpop-send");
        if (receivers == 0) {
            sender.sendMessage(module.getMessage("helpop-no-staff"));
            module.plugin().debug(module.getId(), "HelpOp sin staff online: " + sender.getName() + " -> " + message);
            return;
        }

        sender.sendMessage(module.getMessage("helpop-sent")
                .replace("%staff%", String.valueOf(receivers)));
        module.plugin().debug(module.getId(),
                "HelpOp enviado por " + sender.getName() + " a " + receivers + " staff: " + message);
    }
}
