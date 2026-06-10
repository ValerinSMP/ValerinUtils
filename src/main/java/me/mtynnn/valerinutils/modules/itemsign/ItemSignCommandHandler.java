package me.mtynnn.valerinutils.modules.itemsign;

import me.mtynnn.valerinutils.ValerinUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class ItemSignCommandHandler implements CommandExecutor {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.getDefault());
    private static final String SIGN_KEY = "signed_by_itemsign";
    private static final String SIGNATURES_KEY = "itemsign_signatures";

    private final ValerinUtils plugin;
    private final NamespacedKey signedKey;
    private final NamespacedKey signaturesKey;

    ItemSignCommandHandler(ValerinUtils plugin) {
        this.plugin = plugin;
        this.signedKey = new NamespacedKey(plugin, SIGN_KEY);
        this.signaturesKey = new NamespacedKey(plugin, SIGNATURES_KEY);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("sign")) {
            return handleSign(sender, args);
        }
        if (command.getName().equalsIgnoreCase("itemsign")) {
            return handleItemSign(sender, args);
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
        ItemMeta meta = getEditableMeta(hand, player);
        if (meta == null) {
            return true;
        }

        PersistentDataContainer data = meta.getPersistentDataContainer();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        List<SignatureRecord> records = loadRecords(data, lore);

        FileConfiguration cfg = config();
        int maxTotalChars = Math.max(1, cfg.getInt("limits.max-total-chars", 120));
        int maxLineChars = Math.max(1, cfg.getInt("limits.max-line-chars", 30));
        int maxLines = Math.max(1, cfg.getInt("limits.max-lines", 4));

        String dedication = String.join(" ", args).trim();
        if (dedication.isEmpty()) {
            player.sendMessage(msg("usage-sign", "%prefix%&7Uso: &e/sign <texto...>"));
            return true;
        }

        String plainDedication = plainText(plugin.parseComponent(dedication));
        if (plainDedication.length() > maxTotalChars) {
            player.sendMessage(formatted("too-long", "%prefix%&cTexto demasiado largo. Maximo: &e%max% &cchars.", "%max%", String.valueOf(maxTotalChars)));
            return true;
        }

        WrapResult wrapped = wrapDedication(dedication, plainDedication, maxLineChars, maxLines);
        if (wrapped.overflow || wrapped.lines.isEmpty()) {
            player.sendMessage(formatted("too-many-lines", "%prefix%&cTexto genera demasiadas lineas. Maximo: &e%max%&c.", "%max%", String.valueOf(maxLines)));
            return true;
        }

        int addedLines = 1 + wrapped.lines.size();
        List<Component> signatureLore = new ArrayList<>(addedLines);
        int existingIndex = findRecordIndexByOwner(records, player.getUniqueId(), player.getName());
        int number = existingIndex >= 0 ? existingIndex + 1 : records.size() + 1;
        signatureLore.add(noItalic(plugin.parseComponent(buildSignatureLine(player, number))));
        for (String line : wrapped.lines) {
            signatureLore.add(renderDedicationLine(line));
        }

        SignatureRecord newRecord = new SignatureRecord(player.getUniqueId(), player.getName(), addedLines, Instant.now().getEpochSecond());
        if (existingIndex >= 0) {
            if (!replaceRecordLore(lore, records, existingIndex, signatureLore)) {
                player.sendMessage(msg("signature-not-found", "%prefix%&cNo se encontro esa firma."));
                return true;
            }
            records.set(existingIndex, newRecord);
            player.sendMessage(msg("updated", "%prefix%&aTu firma fue actualizada."));
        } else {
            lore.addAll(signatureLore);
            records.add(newRecord);
            player.sendMessage(msg("signed", "%prefix%&aItem firmado."));
        }

        saveRecords(data, records);
        data.set(signedKey, PersistentDataType.BYTE, (byte) 1);

        meta.lore(lore);
        hand.setItemMeta(meta);
        player.getInventory().setItemInMainHand(hand);
        player.updateInventory();
        return true;
    }

    private boolean handleItemSign(CommandSender sender, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("remove")) {
            sender.sendMessage(msg("usage-remove", "%prefix%&7Uso: &e/itemsign remove &7o &e/itemsign remove <numero|nombre>"));
            return true;
        }

        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(msg("only-player", "%prefix%&cSolo jugadores pueden usar este comando."));
                return true;
            }
            return removeOwnSignature(player);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(msg("only-player", "%prefix%&cSolo jugadores pueden usar este comando."));
            return true;
        }
        String selector = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        RemovalResult result = removeSelectedSignature(player, selector, player.getUniqueId());
        sendRemovalFeedback(player, result);
        return true;
    }

    private boolean removeOwnSignature(Player player) {
        RemovalResult result = removeSelectedSignature(player, null, player.getUniqueId());
        if (result == RemovalResult.REMOVED) {
            player.sendMessage(msg("removed-own", "%prefix%&aTu firma fue removida."));
            return true;
        }
        if (result == RemovalResult.NOT_SIGNED_BY_YOU) {
            player.sendMessage(msg("not-signed-by-you", "%prefix%&cEste item no tiene una firma tuya."));
            return true;
        }
        player.sendMessage(msg("not-signed", "%prefix%&cEse item no tiene firma."));
        return true;
    }

    private void sendRemovalFeedback(Player player, RemovalResult result) {
        switch (result) {
            case REMOVED -> player.sendMessage(msg("removed", "%prefix%&aFirma removida."));
            case NOT_FOUND -> player.sendMessage(msg("signature-not-found", "%prefix%&cNo se encontro esa firma."));
            case NO_PERMISSION -> player.sendMessage(msg("no-permission", "%prefix%&cNo tienes permiso para quitar esa firma."));
            default -> player.sendMessage(msg("not-signed", "%prefix%&cEse item no tiene firma."));
        }
    }

    private RemovalResult removeSelectedSignature(Player target, String selector, UUID requesterUuid) {
        ItemStack hand = target.getInventory().getItemInMainHand();
        ItemMeta meta = hand == null || hand.getType().isAir() ? null : hand.getItemMeta();
        if (meta == null) {
            return RemovalResult.NO_SIGNATURES;
        }

        PersistentDataContainer data = meta.getPersistentDataContainer();
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        List<SignatureRecord> records = loadRecords(data, lore);
        if (records.isEmpty()) {
            return RemovalResult.NO_SIGNATURES;
        }

        int recordIndex = selector == null || selector.isBlank()
                ? findRecordIndexByOwner(records, requesterUuid, target.getName())
                : findRecordIndex(records, selector, null);
        if (recordIndex < 0) {
            return selector == null || selector.isBlank() ? RemovalResult.NOT_SIGNED_BY_YOU : RemovalResult.NOT_FOUND;
        }

        SignatureRecord record = records.get(recordIndex);
        if (!record.uuid.equals(requesterUuid) && !target.hasPermission("valerinutils.itemsign.admin")) {
            return RemovalResult.NO_PERMISSION;
        }

        if (!removeRecordLore(lore, records, recordIndex)) {
            return RemovalResult.NOT_FOUND;
        }

        records.remove(recordIndex);
        if (lore.isEmpty()) {
            meta.lore(null);
        } else {
            meta.lore(lore);
        }

        if (records.isEmpty()) {
            data.remove(signedKey);
            data.remove(signaturesKey);
        } else {
            saveRecords(data, records);
            data.set(signedKey, PersistentDataType.BYTE, (byte) 1);
        }

        hand.setItemMeta(meta);
        target.getInventory().setItemInMainHand(hand);
        target.updateInventory();
        return RemovalResult.REMOVED;
    }

    private int findRecordIndex(List<SignatureRecord> records, String selector, UUID ownerOnly) {
        if (ownerOnly != null) {
            for (int i = records.size() - 1; i >= 0; i--) {
                if (records.get(i).uuid.equals(ownerOnly)) {
                    return i;
                }
            }
            return -1;
        }

        if (selector == null || selector.isBlank()) {
            return -1;
        }
        try {
            int number = Integer.parseInt(selector);
            int index = number - 1;
            return index >= 0 && index < records.size() ? index : -1;
        } catch (NumberFormatException ignored) {
        }

        String normalized = selector.toLowerCase(Locale.ROOT);
        for (int i = records.size() - 1; i >= 0; i--) {
            if (records.get(i).name.toLowerCase(Locale.ROOT).equals(normalized)) {
                return i;
            }
        }
        return -1;
    }

    private int findRecordIndexByOwner(List<SignatureRecord> records, UUID uuid, String name) {
        for (int i = records.size() - 1; i >= 0; i--) {
            SignatureRecord record = records.get(i);
            if (record.uuid.equals(uuid) || record.name.equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    private boolean removeRecordLore(List<Component> lore, List<SignatureRecord> records, int recordIndex) {
        int totalSignatureLines = records.stream().mapToInt(SignatureRecord::lineCount).sum();
        int signaturesStart = lore.size() - totalSignatureLines;
        if (signaturesStart < 0) {
            return false;
        }

        int start = signaturesStart;
        for (int i = 0; i < recordIndex; i++) {
            start += records.get(i).lineCount;
        }
        int end = start + records.get(recordIndex).lineCount;
        if (start < 0 || end > lore.size() || start >= end) {
            return false;
        }
        lore.subList(start, end).clear();
        return true;
    }

    private boolean replaceRecordLore(List<Component> lore, List<SignatureRecord> records, int recordIndex, List<Component> replacement) {
        int totalSignatureLines = records.stream().mapToInt(SignatureRecord::lineCount).sum();
        int signaturesStart = lore.size() - totalSignatureLines;
        if (signaturesStart < 0) {
            return false;
        }

        int start = signaturesStart;
        for (int i = 0; i < recordIndex; i++) {
            start += records.get(i).lineCount;
        }
        int end = start + records.get(recordIndex).lineCount;
        if (start < 0 || end > lore.size() || start >= end) {
            return false;
        }
        lore.subList(start, end).clear();
        lore.addAll(start, replacement);
        return true;
    }

    private ItemMeta getEditableMeta(ItemStack item, Player player) {
        if (item == null || item.getType().isAir()) {
            player.sendMessage(msg("item-required", "%prefix%&cDebes tener un item en la mano."));
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            player.sendMessage(msg("item-required", "%prefix%&cEse item no se puede firmar."));
            return null;
        }
        return meta;
    }

    private WrapResult wrapDedication(String raw, String plain, int maxLineChars, int maxLines) {
        if (raw == null || raw.isBlank() || plain == null || plain.isBlank()) {
            return new WrapResult(List.of(), false);
        }

        if (containsFormatting(raw)) {
            boolean overflow = plain.length() > maxLineChars || maxLines < 1;
            return new WrapResult(List.of(raw), overflow);
        }

        return wrapPlainText(raw, maxLineChars, maxLines);
    }

    private WrapResult wrapPlainText(String text, int maxLineChars, int maxLines) {
        List<String> lines = new ArrayList<>();
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
            } else if (current.length() + 1 + word.length() <= maxLineChars) {
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

    private Component renderDedicationLine(String line) {
        if (containsFormatting(line)) {
            return noItalic(plugin.parseComponent(line));
        }
        return Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    private boolean containsFormatting(String text) {
        return text.contains("<") && text.contains(">") || text.contains("&");
    }

    private String buildSignatureLine(Player player, int number) {
        FileConfiguration cfg = config();
        String format = cfg != null ? cfg.getString("signature.format", "&8Firma #%number% por: &f%luckperms_prefix%%player% &8| &7%date%") : "&8Firma #%number% por: &f%luckperms_prefix%%player% &8| &7%date%";
        String date = LocalDateTime.now().format(DATE_FORMAT);
        String result = format
                .replace("%player%", player.getName())
                .replace("%date%", date)
                .replace("%number%", String.valueOf(number));
        return resolvePapiPlaceholders(player, result);
    }

    private String resolvePapiPlaceholders(Player player, String input) {
        if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            return input;
        }

        String current = input;
        for (int i = 0; i < 3; i++) {
            String resolved = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, current);
            if (resolved.equals(current)) {
                break;
            }
            current = resolved;
        }
        return current;
    }

    private List<SignatureRecord> loadRecords(PersistentDataContainer data, List<Component> lore) {
        String raw = data.get(signaturesKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            List<SignatureRecord> legacy = loadLegacyRecords(data, lore);
            if (!legacy.isEmpty()) {
                saveRecords(data, legacy);
                data.set(signedKey, PersistentDataType.BYTE, (byte) 1);
            }
            return legacy;
        }

        List<SignatureRecord> records = new ArrayList<>();
        String[] parts = raw.split(";");
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            String[] fields = part.split("\\|", -1);
            if (fields.length != 4) {
                continue;
            }
            try {
                UUID uuid = UUID.fromString(fields[0]);
                String name = decode(fields[1]);
                int lineCount = Math.max(1, Integer.parseInt(fields[2]));
                long signedAt = Long.parseLong(fields[3]);
                records.add(new SignatureRecord(uuid, name, lineCount, signedAt));
            } catch (RuntimeException ignored) {
            }
        }
        return records;
    }

    private List<SignatureRecord> loadLegacyRecords(PersistentDataContainer data, List<Component> lore) {
        if (!data.has(signedKey, PersistentDataType.BYTE) || lore == null || lore.isEmpty()) {
            return new ArrayList<>();
        }

        List<Integer> signatureStarts = new ArrayList<>();
        for (int i = 0; i < lore.size(); i++) {
            String plain = plainText(lore.get(i));
            if (plain.startsWith("Firmado por:") || plain.startsWith("Firma #")) {
                signatureStarts.add(i);
            }
        }
        if (signatureStarts.isEmpty()) {
            return new ArrayList<>();
        }

        List<SignatureRecord> records = new ArrayList<>();
        for (int i = 0; i < signatureStarts.size(); i++) {
            int start = signatureStarts.get(i);
            int end = i + 1 < signatureStarts.size() ? signatureStarts.get(i + 1) : lore.size();
            int lineCount = Math.max(1, end - start);
            String name = parseLegacySignerName(plainText(lore.get(start)));
            UUID uuid = Bukkit.getOfflinePlayer(name).getUniqueId();
            records.add(new SignatureRecord(uuid, name, lineCount, Instant.now().getEpochSecond()));
        }
        return records;
    }

    private String parseLegacySignerName(String signatureLine) {
        if (signatureLine == null || signatureLine.isBlank()) {
            return "unknown";
        }
        String withoutDate = signatureLine;
        int pipe = withoutDate.indexOf('|');
        if (pipe >= 0) {
            withoutDate = withoutDate.substring(0, pipe);
        }
        int colon = withoutDate.indexOf(':');
        if (colon >= 0) {
            withoutDate = withoutDate.substring(colon + 1);
        }
        String[] parts = withoutDate.trim().split("\\s+");
        if (parts.length == 0 || parts[parts.length - 1].isBlank()) {
            return "unknown";
        }
        return parts[parts.length - 1].trim();
    }

    private void saveRecords(PersistentDataContainer data, List<SignatureRecord> records) {
        StringBuilder out = new StringBuilder();
        for (SignatureRecord record : records) {
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(record.uuid)
                    .append('|').append(encode(record.name))
                    .append('|').append(record.lineCount)
                    .append('|').append(record.signedAt);
        }
        data.set(signaturesKey, PersistentDataType.STRING, out.toString());
    }

    private String encode(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private String plainText(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
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

    private record SignatureRecord(UUID uuid, String name, int lineCount, long signedAt) {
    }

    private record WrapResult(List<String> lines, boolean overflow) {
    }

    private enum RemovalResult {
        REMOVED,
        NO_SIGNATURES,
        NOT_SIGNED_BY_YOU,
        NOT_FOUND,
        NO_PERMISSION
    }
}
