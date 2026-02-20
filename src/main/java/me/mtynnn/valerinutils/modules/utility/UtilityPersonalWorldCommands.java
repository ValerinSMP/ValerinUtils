package me.mtynnn.valerinutils.modules.utility;

import org.bukkit.WeatherType;
import org.bukkit.entity.Player;

final class UtilityPersonalWorldCommands {

    private final UtilityModule module;

    UtilityPersonalWorldCommands(UtilityModule module) {
        this.module = module;
    }

    void handlePlayerTime(Player player, String[] args) {
        if (!module.checkStatus(player, "ptime")) {
            return;
        }
        if (args.length == 0) {
            player.sendMessage(module.getMessage("ptime-usage"));
            return;
        }
        String mode = args[0].toLowerCase();
        switch (mode) {
            case "day" -> {
                player.setPlayerTime(1000L, false);
                player.sendMessage(module.getMessage("ptime-set").replace("%value%", "day"));
            }
            case "night" -> {
                player.setPlayerTime(13000L, false);
                player.sendMessage(module.getMessage("ptime-set").replace("%value%", "night"));
            }
            case "reset", "off", "server" -> {
                player.resetPlayerTime();
                player.sendMessage(module.getMessage("ptime-reset"));
            }
            default -> {
                try {
                    long ticks = Long.parseLong(mode);
                    player.setPlayerTime(ticks, false);
                    player.sendMessage(module.getMessage("ptime-set").replace("%value%", String.valueOf(ticks)));
                } catch (NumberFormatException ex) {
                    player.sendMessage(module.getMessage("ptime-usage"));
                }
            }
        }
    }

    void handlePlayerWeather(Player player, String[] args) {
        if (!module.checkStatus(player, "pweather")) {
            return;
        }
        if (args.length == 0) {
            player.sendMessage(module.getMessage("pweather-usage"));
            return;
        }
        String mode = args[0].toLowerCase();
        switch (mode) {
            case "clear", "sun" -> {
                player.setPlayerWeather(WeatherType.CLEAR);
                player.sendMessage(module.getMessage("pweather-set").replace("%value%", "clear"));
            }
            case "rain", "storm" -> {
                player.setPlayerWeather(WeatherType.DOWNFALL);
                player.sendMessage(module.getMessage("pweather-set").replace("%value%", "rain"));
            }
            case "reset", "off", "server" -> {
                player.resetPlayerWeather();
                player.sendMessage(module.getMessage("pweather-reset"));
            }
            default -> player.sendMessage(module.getMessage("pweather-usage"));
        }
    }
}
