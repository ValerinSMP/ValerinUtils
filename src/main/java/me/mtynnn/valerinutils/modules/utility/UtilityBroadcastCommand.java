package me.mtynnn.valerinutils.modules.utility;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.List;

final class UtilityBroadcastCommand {

    private final UtilityModule module;

    UtilityBroadcastCommand(UtilityModule module) {
        this.module = module;
    }

    void execute(CommandSender sender, String[] args) {
        if (!module.isBroadcastEnabled()) {
            sender.sendMessage(module.getMessage("module-disabled"));
            module.plugin().debug(module.getId(), "Broadcast cancelado: comando deshabilitado en config.");
            return;
        }
        if (!sender.hasPermission("valerinutils.utility.broadcast")) {
            sender.sendMessage(module.getMessage("no-permission"));
            module.plugin().debug(module.getId(), "Broadcast cancelado: sin permiso (" + sender.getName() + ").");
            return;
        }
        if (args.length == 0) {
            sender.sendMessage(module.getMessage("broadcast-usage"));
            module.plugin().debug(module.getId(), "Broadcast cancelado: sin argumentos (" + sender.getName() + ").");
            return;
        }

        FileConfiguration cfg = module.getConfig();
        if (cfg == null) {
            sender.sendMessage(module.getMessage("module-disabled"));
            module.plugin().debug(module.getId(), "Broadcast cancelado: utilities.yml no cargado.");
            return;
        }

        String message = String.join(" ", args);
        List<String> formattedLines = cfg.getStringList("messages.broadcast-format");
        if (formattedLines.isEmpty()) {
            String single = cfg.getString("messages.broadcast-format");
            if (single != null && !single.isBlank()) {
                formattedLines = List.of(single);
            } else {
                formattedLines = List.of("%message%");
            }
        }

        int sentLines = 0;
        for (String line : formattedLines) {
            String formatted = line.replace("%message%", message);
            Bukkit.broadcast(module.plugin().parseComponent(formatted));
            sentLines++;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            module.playSound(online, "broadcast");
        }
        module.plugin().debug(module.getId(), "Broadcast enviado por " + sender.getName() + " con " + sentLines + " linea(s).");
    }
}
