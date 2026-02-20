package me.mtynnn.valerinutils.commands;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.modules.menuitem.MenuItemModule;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.*;

public class MenuItemCommand implements CommandExecutor, TabCompleter {

    private final ValerinUtils plugin;
    private final MenuItemModule module;
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public MenuItemCommand(ValerinUtils plugin, MenuItemModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(getMessage("only-players", "&cSolo jugadores."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(getMessage("usage", "&7Uso: &e/menu item <on|off|toggle>"));
            return true;
        }

        // Check Cooldown
        if (!processCooldown(player)) {
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "on" -> {
                boolean success = module.setDisabled(player, false);
                if (success) {
                    sender.sendMessage(getMessage("on", "&aActivado."));
                } else {
                    sender.sendMessage(getMessage("slot-occupied", "&cSlot ocupado."));
                }
            }
            case "off" -> {
                module.setDisabled(player, true);
                sender.sendMessage(getMessage("off", "&cDesactivado."));
            }
            case "toggle" -> {
                boolean disabled = module.isDisabled(player);
                boolean success = module.setDisabled(player, !disabled);
                if (success) {
                    sender.sendMessage(!disabled
                            ? getMessage("toggled-off", "&cDesactivado.")
                            : getMessage("toggled-on", "&aActivado."));
                } else {
                    sender.sendMessage(getMessage("slot-occupied", "&cSlot ocupado."));
                }
            }
            default -> sender.sendMessage(getMessage("usage", "&7Uso: &e/menu item <on|off|toggle>"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = Arrays.asList("on", "off", "toggle");
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String s : base) {
                if (s.startsWith(prefix))
                    out.add(s);
            }
            return out;
        }
        return List.of();
    }

    private boolean processCooldown(Player player) {
        if (player.hasPermission("valerinutils.bypass.cooldown"))
            return true;

        FileConfiguration cfg = plugin.getConfigManager().getConfig("menuitem");
        if (cfg == null)
            return true;

        int cooldownSec = cfg.getInt("cooldown-seconds", 3);
        if (cooldownSec <= 0)
            return true;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long last = cooldowns.getOrDefault(uuid, 0L);
        long diff = now - last;
        long cooldownMillis = cooldownSec * 1000L;

        if (diff < cooldownMillis) {
            double remaining = (cooldownMillis - diff) / 1000.0;
            String msg = getMessage("cooldown", "&cEspera &e%time%s");
            msg = msg.replace("%time%", String.format("%.1f", remaining));
            player.sendMessage(msg);
            return false;
        }

        cooldowns.put(uuid, now);
        return true;
    }

    private String getMessage(String key, String def) {
        return plugin.messages().module("menuitem", key, def);
    }
}
