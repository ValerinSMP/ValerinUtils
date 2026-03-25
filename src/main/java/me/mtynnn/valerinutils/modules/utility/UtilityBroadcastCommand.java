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
            module.getMessageLines("broadcast-usage").forEach(sender::sendMessage);
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
            // Apply center formatting if line contains {center}
            formatted = applyCenter(formatted);
            Bukkit.broadcast(module.plugin().parseComponent(formatted));
            sentLines++;
        }
        for (Player online : Bukkit.getOnlinePlayers()) {
            module.playSound(online, "broadcast");
        }
        module.plugin().debug(module.getId(), "Broadcast enviado por " + sender.getName() + " con " + sentLines + " linea(s).");
    }

    /**
     * Centers text by replacing {center} tags with appropriate spacing.
     * Minecraft chat is approximately 50 characters wide.
     * Emoji and wide characters count as ~2 width units.
     */
    private String applyCenter(String line) {
        if (!line.contains("{center}")) {
            return line;
        }

        String[] parts = line.split("\\{center\\}");
        if (parts.length < 2) {
            return line;
        }

        String beforeCenter = parts[0];
        String textToCenter = parts[1];

        // Extract visible text length (remove MiniMessage tags)
        String visibleText = textToCenter.replaceAll("<[^>]*>", "");
        
        // Calculate visual width considering emoji and wide characters
        int visualWidth = calculateVisualWidth(visibleText);
        
        // Minecraft chat width is approximately 50 characters
        int chatWidth = 50;
        int padding = Math.max(0, (chatWidth - visualWidth) / 2);
        String spaces = " ".repeat(padding);

        return beforeCenter + spaces + textToCenter;
    }

    /**
     * Calculates the visual width of text in Minecraft.
     * Emoji and certain unicode ranges count as 2 width units.
     */
    private int calculateVisualWidth(String text) {
        int width = 0;
        for (char ch : text.toCharArray()) {
            // Emoji ranges and wide characters
            if ((ch >= 0x1F000 && ch <= 0x1F9FF) ||  // Emoji range
                (ch >= 0x2600 && ch <= 0x26FF) ||    // Miscellaneous Symbols
                (ch >= 0x2700 && ch <= 0x27BF) ||    // Dingbats
                Character.isSupplementaryCodePoint(ch)) {
                width += 2;  // Emoji take ~2 width units
            } else if (Character.isUpperCase(ch)) {
                width += 1;
            } else if (ch == ' ') {
                width += 1;
            } else {
                width += 1;  // Regular characters
            }
        }
        return width;
    }
}
