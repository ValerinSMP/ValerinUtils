package me.mtynnn.valerinutils.modules.utility;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

final class UtilitySeenCommand {

    private final UtilityModule module;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    UtilitySeenCommand(UtilityModule module) {
        this.module = module;
    }

    void execute(CommandSender sender, String name) {
        OfflinePlayer offlinePlayer = findOfflinePlayer(name);
        if (offlinePlayer == null || !isKnownPlayer(offlinePlayer)) {
            sender.sendMessage(module.getMessage("player-not-found"));
            return;
        }

        FileConfiguration cfg = module.getConfig();
        List<String> format = cfg.getStringList("messages.seen-format");
        if (format.isEmpty()) {
            return;
        }

        Player onlinePlayer = offlinePlayer.isOnline() ? offlinePlayer.getPlayer() : null;
        String status = offlinePlayer.isOnline() ? module.getMessage("seen-online")
                : module.getMessage("seen-offline")
                        .replace("%time%", formatTime(System.currentTimeMillis() - offlinePlayer.getLastPlayed()));

        for (String line : format) {
            String processed = line.replace("%player%", offlinePlayer.getName() != null ? offlinePlayer.getName() : "Desconocido")
                    .replace("%status%", status)
                    .replace("%uuid%", offlinePlayer.getUniqueId().toString())
                    .replace("%ip%",
                            (onlinePlayer != null && sender.hasPermission("valerinutils.utility.seen.ip"))
                                    ? onlinePlayer.getAddress().getAddress().getHostAddress()
                                    : "Oculta")
                    .replace("%first_join%", formatDateOrUnknown(offlinePlayer.getFirstPlayed()))
                    .replace("%last_seen%", formatDateOrUnknown(offlinePlayer.getLastPlayed()))
                    .replace("%world%",
                            onlinePlayer != null ? onlinePlayer.getWorld().getName()
                                    : (offlinePlayer.getLocation() != null
                                            ? offlinePlayer.getLocation().getWorld().getName()
                                            : "N/A"))
                    .replace("%x%",
                            String.valueOf(onlinePlayer != null ? onlinePlayer.getLocation().getBlockX()
                                    : (offlinePlayer.getLocation() != null ? offlinePlayer.getLocation().getBlockX() : 0)))
                    .replace("%y%",
                            String.valueOf(onlinePlayer != null ? onlinePlayer.getLocation().getBlockY()
                                    : (offlinePlayer.getLocation() != null ? offlinePlayer.getLocation().getBlockY() : 0)))
                    .replace("%z%",
                            String.valueOf(onlinePlayer != null ? onlinePlayer.getLocation().getBlockZ()
                                    : (offlinePlayer.getLocation() != null ? offlinePlayer.getLocation().getBlockZ() : 0)))
                    .replace("%health%", String.valueOf(onlinePlayer != null ? (int) onlinePlayer.getHealth() : 0))
                    .replace("%hunger%", String.valueOf(onlinePlayer != null ? onlinePlayer.getFoodLevel() : 0))
                    .replace("%xp%", String.valueOf(onlinePlayer != null ? onlinePlayer.getLevel() : 0))
                    .replace("%gamemode%", onlinePlayer != null ? onlinePlayer.getGameMode().name() : "OFFLINE")
                    .replace("%fly%", onlinePlayer != null ? (onlinePlayer.getAllowFlight() ? "<green>Sí" : "<red>No") : "OFFLINE");

            sender.sendMessage(module.plugin().parseComponent(processed));
        }
    }

    private OfflinePlayer findOfflinePlayer(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        Player online = Bukkit.getPlayerExact(input);
        if (online != null) {
            return online;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().equalsIgnoreCase(input)) {
                return player;
            }
        }

        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            String playerName = player.getName();
            if (playerName != null && playerName.equalsIgnoreCase(input)) {
                return player;
            }
        }

        return Bukkit.getOfflinePlayer(input);
    }

    private boolean isKnownPlayer(OfflinePlayer player) {
        return player.isOnline() || player.hasPlayedBefore() || player.getFirstPlayed() > 0L || player.getLastPlayed() > 0L;
    }

    private String formatDateOrUnknown(long epochMillis) {
        if (epochMillis <= 0L) {
            return "N/A";
        }
        return dateFormat.format(new Date(epochMillis));
    }

    private String formatTime(long ms) {
        long seconds = ms / 1000 % 60;
        long minutes = ms / (1000 * 60) % 60;
        long hours = ms / (1000 * 60 * 60) % 24;
        long days = ms / (1000 * 60 * 60 * 24);
        if (days > 0)
            return days + "d " + hours + "h";
        if (hours > 0)
            return hours + "h " + minutes + "m";
        if (minutes > 0)
            return minutes + "m " + seconds + "s";
        return seconds + "s";
    }
}
