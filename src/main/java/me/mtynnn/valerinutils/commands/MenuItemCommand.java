package me.mtynnn.valerinutils.commands;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.CommandHelpRenderer;
import me.mtynnn.valerinutils.modules.menuitem.MenuItemModule;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.*;

public class MenuItemCommand implements CommandExecutor, TabCompleter, Listener {

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
            sendMessage(sender, "only-players", "%prefix%<#FF3300>Solo jugadores.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
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
                    sendMessage(sender, "on", "%prefix%<#00FB9A>Item de menú activado.");
                } else {
                    sendMessage(sender, "slot-occupied", "%prefix%<#FF3300>El slot está ocupado.");
                }
            }
            case "off" -> {
                module.setDisabled(player, true);
                sendMessage(sender, "off", "%prefix%<#00FB9A>Item de menú desactivado.");
            }
            case "toggle" -> {
                boolean disabled = module.isDisabled(player);
                boolean success = module.setDisabled(player, !disabled);
                if (success) {
                    sendMessage(sender, !disabled ? "toggled-off" : "toggled-on",
                            "%prefix%<#00FB9A>Estado del item de menú actualizado.");
                } else {
                    sendMessage(sender, "slot-occupied", "%prefix%<#FF3300>El slot está ocupado.");
                }
            }
            default -> sendUsage(sender);
        }

        return true;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cooldowns.remove(event.getPlayer().getUniqueId());
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
            sendMessage(player, "cooldown", "%prefix%<#FF3300>Espera <#FFD166>%time%s</#FFD166>.",
                    "%time%", String.format("%.1f", remaining));
            return false;
        }

        cooldowns.put(uuid, now);
        return true;
    }

    private void sendUsage(CommandSender sender) {
        CommandHelpRenderer.send(sender, "Item de menú", List.of(
                CommandHelpRenderer.Entry.of(
                        "/menuitem on", "Activar el item del menú", "/menuitem on"),
                CommandHelpRenderer.Entry.of(
                        "/menuitem off", "Desactivar el item del menú", "/menuitem off"),
                CommandHelpRenderer.Entry.of(
                        "/menuitem toggle", "Alternar el estado actual", "/menuitem toggle")));
    }

    private void sendMessage(CommandSender sender, String key, String fallback, String... replacements) {
        FileConfiguration config = plugin.getConfigManager().getConfig("menuitem");
        String raw = config == null ? fallback : config.getString("messages." + key, fallback);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            raw = raw.replace(replacements[index], replacements[index + 1]);
        }
        sender.sendMessage(plugin.parseComponent(raw));
    }
}
