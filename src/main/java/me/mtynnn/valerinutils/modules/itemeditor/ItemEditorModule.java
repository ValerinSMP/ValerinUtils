package me.mtynnn.valerinutils.modules.itemeditor;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.BaseModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class ItemEditorModule extends BaseModule implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT_SUBS = List.of("name", "lore");
    private static final List<String> LORE_SUBS = List.of("add", "set", "remove", "clear");

    public ItemEditorModule(ValerinUtils plugin) {
        super(plugin);
    }

    @Override
    public String getId() {
        return "itemeditor";
    }

    @Override
    protected void onEnableModule() {
        registerCommand("itemedit", this, this);
        debug("Módulo habilitado.");
    }

    @Override
    protected void onDisableModule() {
        debug("Módulo deshabilitado.");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("only-players", "%prefix%<red>Solo jugadores pueden usar este comando."));
            return true;
        }
        if (!player.hasPermission("valerinutils.itemeditor.use")) {
            player.sendMessage(msg("no-permission", "%prefix%<red>No tienes permisos."));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(msg("usage", "%prefix%<gray>Uso: <yellow>/itemedit <name|lore> ..."));
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() == Material.AIR) {
            player.sendMessage(msg("no-item-in-hand", "%prefix%<red>Debes tener un ítem en la mano."));
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        return switch (sub) {
            case "name" -> handleName(player, hand, args);
            case "lore" -> handleLore(player, hand, args);
            default -> {
                player.sendMessage(msg("usage", "%prefix%<gray>Uso: <yellow>/itemedit <name|lore> ..."));
                yield true;
            }
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return ROOT_SUBS.stream()
                    .filter(entry -> entry.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && "lore".equalsIgnoreCase(args[0])) {
            return LORE_SUBS.stream()
                    .filter(entry -> entry.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private boolean handleName(Player player, ItemStack hand, String[] args) {
        if (args.length < 2) {
            player.sendMessage(msg("usage-name", "%prefix%<gray>Uso: <yellow>/itemedit name <texto|off>"));
            return true;
        }

        ItemMeta meta = hand.getItemMeta();
        if ("off".equalsIgnoreCase(args[1]) || "clear".equalsIgnoreCase(args[1])) {
            meta.displayName(null);
            hand.setItemMeta(meta);
            player.sendMessage(msg("name-cleared", "%prefix%<green>Nombre del ítem limpiado."));
            return true;
        }

        String raw = joinArgs(args, 1);
        Component parsed = plugin.parseComponent(raw).decoration(TextDecoration.ITALIC, false);
        meta.displayName(parsed);
        hand.setItemMeta(meta);
        player.sendMessage(msg("name-updated", "%prefix%<green>Nombre del ítem actualizado."));
        debug("Nombre editado por " + player.getName() + " | raw=" + raw);
        return true;
    }

    private boolean handleLore(Player player, ItemStack hand, String[] args) {
        if (args.length < 2) {
            player.sendMessage(msg("usage-lore",
                    "%prefix%<gray>Uso: <yellow>/itemedit lore <add|set|remove|clear> ..."));
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        ItemMeta meta = hand.getItemMeta();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        int maxLines = cfg() != null ? Math.max(1, cfg().getInt("settings.max-lore-lines", 20)) : 20;

        switch (action) {
            case "add" -> {
                if (args.length < 3) {
                    player.sendMessage(msg("usage-lore-add", "%prefix%<gray>Uso: <yellow>/itemedit lore add <texto>"));
                    return true;
                }
                if (lore.size() >= maxLines) {
                    player.sendMessage(msg("lore-max-lines", "%prefix%<red>Tu ítem ya alcanzó el máximo de líneas."));
                    return true;
                }
                String raw = joinArgs(args, 2);
                lore.add(plugin.parseComponent(raw).decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                hand.setItemMeta(meta);
                player.sendMessage(msg("lore-added", "%prefix%<green>Línea de lore agregada."));
                debug("Lore add por " + player.getName() + " | raw=" + raw);
                return true;
            }
            case "set" -> {
                if (args.length < 4) {
                    player.sendMessage(
                            msg("usage-lore-set", "%prefix%<gray>Uso: <yellow>/itemedit lore set <línea> <texto>"));
                    return true;
                }
                int lineIndex = parseLine(args[2]);
                if (lineIndex < 0 || lineIndex >= lore.size()) {
                    player.sendMessage(msg("line-out-of-range", "%prefix%<red>Línea inválida."));
                    return true;
                }
                String raw = joinArgs(args, 3);
                lore.set(lineIndex, plugin.parseComponent(raw).decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                hand.setItemMeta(meta);
                player.sendMessage(msg("lore-updated", "%prefix%<green>Línea de lore actualizada."));
                debug("Lore set por " + player.getName() + " | line=" + (lineIndex + 1) + " | raw=" + raw);
                return true;
            }
            case "remove" -> {
                if (args.length < 3) {
                    player.sendMessage(
                            msg("usage-lore-remove", "%prefix%<gray>Uso: <yellow>/itemedit lore remove <línea>"));
                    return true;
                }
                int lineIndex = parseLine(args[2]);
                if (lineIndex < 0 || lineIndex >= lore.size()) {
                    player.sendMessage(msg("line-out-of-range", "%prefix%<red>Línea inválida."));
                    return true;
                }
                lore.remove(lineIndex);
                if (lore.isEmpty()) {
                    meta.lore(null);
                } else {
                    meta.lore(lore);
                }
                hand.setItemMeta(meta);
                player.sendMessage(msg("lore-removed", "%prefix%<green>Línea de lore eliminada."));
                debug("Lore remove por " + player.getName() + " | line=" + (lineIndex + 1));
                return true;
            }
            case "clear" -> {
                meta.lore(null);
                hand.setItemMeta(meta);
                player.sendMessage(msg("lore-cleared", "%prefix%<green>Lore del ítem limpiado."));
                debug("Lore clear por " + player.getName());
                return true;
            }
            default -> {
                player.sendMessage(msg("usage-lore",
                        "%prefix%<gray>Uso: <yellow>/itemedit lore <add|set|remove|clear> ..."));
                return true;
            }
        }
    }

    private int parseLine(String raw) {
        try {
            int line = Integer.parseInt(raw);
            return line - 1;
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private String joinArgs(String[] args, int from) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (i > from) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString();
    }
}
