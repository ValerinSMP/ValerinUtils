package me.mtynnn.valerinutils.modules.itemsign;

import me.mtynnn.valerinutils.ValerinUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

final class ItemSignCommandHandler implements CommandExecutor {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault());
    private static final String SIGN_KEY = "signed_by_itemsign";

    private final ValerinUtils plugin;
    private final NamespacedKey signedKey;

    ItemSignCommandHandler(ValerinUtils plugin) {
        this.plugin = plugin;
        this.signedKey = new NamespacedKey(plugin, SIGN_KEY);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("sign")) {
            return handleSign(sender, args);
        }
        if (command.getName().equalsIgnoreCase("itemsign")) {
            return handleAdmin(sender, args);
        }
        return false;
    }

    private boolean handleSign(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("only-player", "%prefix%&cSolo jugadores pueden usar este comando."));
            return true;
        }
        if (!player.hasPermission("valerinutils.itemsign.use")) {
            player.sendMessage(msg("no-permission", "%prefix%&cNo tienes permiso para usar este comando."));
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(msg("usage-sign", "%prefix%&7Uso: &e/sign <texto...>"));
            return true;
        }

        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage(msg("item-required", "%prefix%&cDebes tener un item en la mano."));
            return true;
        }

        ItemMeta meta = hand.getItemMeta();
        if (meta == null) {
            player.sendMessage(msg("item-required", "%prefix%&cEse item no se puede firmar."));
            return true;
        }

        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (data.has(signedKey, PersistentDataType.BYTE)) {
            player.sendMessage(msg("already-signed", "%prefix%&cEste item ya fue firmado. Usa &e/itemsign remove &cpara quitarle la firma."));
            return true;
        }

        FileConfiguration cfg = config();
        int maxTotalChars = Math.max(1, cfg.getInt("limits.max-total-chars", 120));
        int maxLineChars = Math.max(1, cfg.getInt("limits.max-line-chars", 30));
        int maxLines = Math.max(1, cfg.getInt("limits.max-lines", 4));

        String dedication = String.join(" ", args).trim();
        if (dedication.isEmpty()) {
            player.sendMessage(msg("usage-sign", "%prefix%&7Uso: &e/sign <texto...>"));
            return true;
        }
        if (dedication.length() > maxTotalChars) {
            player.sendMessage(formatted("too-long", "%prefix%&cTexto demasiado largo. Máximo: &e%max% &cchars.", "%max%", String.valueOf(maxTotalChars)));
            return true;
        }

        WrapResult wrapped = wrapText(dedication, maxLineChars, maxLines);
        if (wrapped.overflow || wrapped.lines.isEmpty()) {
            player.sendMessage(formatted("too-many-lines", "%prefix%&cTexto genera demasiadas líneas. Máximo: &e%max%&c.", "%max%", String.valueOf(maxLines)));
            return true;
        }

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        
        // Add signature first, then dedication text below
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        lore.add(plugin.parseComponent(buildSignatureLine(player)).decoration(TextDecoration.ITALIC, false));
        
        // Add custom text with explicit gray color
        for (String line : wrapped.lines) {
            // Prepend gray color code to ensure text is gray, not purple
            String coloredLine = "&7" + line;
            lore.add(plugin.parseComponent(coloredLine).decoration(TextDecoration.ITALIC, false));
        }

        data.set(signedKey, PersistentDataType.BYTE, (byte) 1);
        meta.lore(lore);
        hand.setItemMeta(meta);
        player.getInventory().setItemInMainHand(hand);
        player.updateInventory();
        player.sendMessage(msg("signed", "%prefix%&aItem firmado."));
        return true;
    }

    private boolean handleAdmin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("valerinutils.itemsign.admin")) {
            sender.sendMessage(msg("no-permission", "%prefix%&cNo tienes permiso para usar este comando."));
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("remove")) {
            sender.sendMessage(msg("usage-remove", "%prefix%&7Uso: &e/itemsign remove <usuario>"));
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(msg("player-not-found", "%prefix%&cJugador no encontrado."));
            return true;
        }

        ItemStack hand = target.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            sender.sendMessage(msg("item-required", "%prefix%&cEse jugador no tiene item en la mano."));
            return true;
        }

        ItemMeta meta = hand.getItemMeta();
        if (meta == null) {
            sender.sendMessage(msg("item-required", "%prefix%&cEse item no se puede limpiar."));
            return true;
        }

        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (!data.has(signedKey, PersistentDataType.BYTE)) {
            sender.sendMessage(msg("not-signed", "%prefix%&cEse item no tiene firma."));
            return true;
        }

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        removeSignatureLore(lore);
        if (lore.isEmpty()) {
            meta.lore(null);
        } else {
            meta.lore(lore);
        }
        data.remove(signedKey);
        hand.setItemMeta(meta);
        target.getInventory().setItemInMainHand(hand);
        target.updateInventory();
        sender.sendMessage(formatted("removed", "%prefix%&aFirma removida de &f%player%&a.", "%player%", target.getName()));
        target.sendMessage(msg("removed-target", "%prefix%&cUn admin quitó la firma de tu item."));
        return true;
    }

    private void removeSignatureLore(List<Component> lore) {
        // Remove from end until we find the signature line
        boolean foundSignature = false;
        while (!lore.isEmpty()) {
            Component last = lore.get(lore.size() - 1);
            String plain = plainText(last);
            
            // Remove everything after finding the signature line
            lore.remove(lore.size() - 1);
            
            if (plain.startsWith("Firmado por:")) {
                foundSignature = true;
                // Also remove the blank line before signature if exists
                if (!lore.isEmpty() && isEmptyLine(lore.get(lore.size() - 1))) {
                    lore.remove(lore.size() - 1);
                }
                break;
            }
        }
    }

    private boolean isEmptyLine(Component component) {
        return plainText(component).isBlank();
    }

    private String plainText(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }


    private String buildSignatureLine(Player player) {
        FileConfiguration cfg = config();
        String format = cfg != null ? cfg.getString("signature.format", "&8Firmado por: &f%luckperms_prefix%%player% &8| &7%date%") : "&8Firmado por: &f%luckperms_prefix%%player% &8| &7%date%";
        String date = LocalDateTime.now().format(DATE_FORMAT);
        String result = format.replace("%player%", player.getName()).replace("%date%", date);

        return resolvePapiPlaceholders(player, result);
    }

    private String resolvePapiPlaceholders(Player player, String input) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return input;
        }

        String current = input;
        // Run multiple passes to resolve chained placeholders (e.g. %luckperms_prefix% -> %nexo_owner%)
        for (int i = 0; i < 3; i++) {
            String resolved = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, current);
            if (resolved.equals(current)) {
                break;
            }
            current = resolved;
        }
        return current;
    }
    private WrapResult wrapText(String text, int maxLineChars, int maxLines) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return new WrapResult(lines, false);
        }

        List<String> words = Arrays.asList(text.trim().split("\\s+"));
        StringBuilder current = new StringBuilder();
        boolean overflow = false;

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            if (word.length() > maxLineChars) {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    current.setLength(0);
                }
                int index = 0;
                while (index < word.length()) {
                    if (lines.size() >= maxLines) {
                        overflow = true;
                        break;
                    }
                    int end = Math.min(index + maxLineChars, word.length());
                    lines.add(word.substring(index, end));
                    index = end;
                }
                if (overflow) {
                    break;
                }
                continue;
            }

            if (current.length() == 0) {
                current.append(word);
                continue;
            }

            if (current.length() + 1 + word.length() <= maxLineChars) {
                current.append(' ').append(word);
            } else {
                lines.add(current.toString());
                if (lines.size() >= maxLines) {
                    overflow = true;
                    break;
                }
                current.setLength(0);
                current.append(word);
            }
        }

        if (!overflow && current.length() > 0) {
            if (lines.size() >= maxLines) {
                overflow = true;
            } else {
                lines.add(current.toString());
            }
        }

        return new WrapResult(lines, overflow);
    }

    private static final class WrapResult {
        private final List<String> lines;
        private final boolean overflow;

        private WrapResult(List<String> lines, boolean overflow) {
            this.lines = lines;
            this.overflow = overflow;
        }
    }

    private FileConfiguration config() {
        return plugin.getConfigManager().getConfig("itemsign");
    }

    private Component msg(String path, String fallback) {
        FileConfiguration cfg = config();
        String raw = cfg != null ? cfg.getString("messages." + path, fallback) : fallback;
        return plugin.parseComponent(raw);
    }

    private Component formatted(String path, String fallback, String token, String replacement) {
        FileConfiguration cfg = config();
        String raw = cfg != null ? cfg.getString("messages." + path, fallback) : fallback;
        return plugin.parseComponent(raw.replace(token, replacement));
    }
}
