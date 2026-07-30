package me.mtynnn.valerinutils.commands;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.CommandHelpRenderer;
import me.mtynnn.valerinutils.modules.utility.UtilityModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class ValerinUtilsHelp {
    private static final TextColor PRIMARY = TextColor.fromHexString("#FFD166");
    private static final TextColor ERROR = TextColor.fromHexString("#FF3300");

    private final ValerinUtils plugin;

    ValerinUtilsHelp(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    void send(CommandSender sender, String rawPage) {
        int requestedPage;
        try {
            requestedPage = rawPage == null || rawPage.isBlank() ? 1 : Integer.parseInt(rawPage);
        } catch (NumberFormatException ignored) {
            sendInvalidPage(sender, totalPages(sender));
            return;
        }

        List<HelpEntry> entries = visibleEntries(sender);
        FileConfiguration settings = plugin.getConfigManager().getConfig("settings");
        int pageSize = settings == null ? 9 : settings.getInt("messages.help.entries-per-page", 9);
        HelpPaginator.Page<HelpEntry> page = HelpPaginator.page(entries, requestedPage, pageSize);
        if (!page.valid()) {
            sendInvalidPage(sender, page.totalPages());
            return;
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text()
                .append(Component.text("ValerinUtils", PRIMARY, TextDecoration.BOLD))
                .append(Component.text(" | Comandos:", PRIMARY))
                .append(Component.text(" (" + page.number() + "/" + page.totalPages() + ")",
                        NamedTextColor.GRAY))
                .build());
        sender.sendMessage(Component.empty());

        for (HelpEntry entry : page.entries()) {
            sender.sendMessage(renderEntry(entry.command(), entry.description(), entry.suggest()));
        }

        sender.sendMessage(Component.empty());
        sender.sendMessage(navigation(page.number(), page.totalPages()));
        sender.sendMessage(Component.empty());
    }

    int totalPages(CommandSender sender) {
        FileConfiguration settings = plugin.getConfigManager().getConfig("settings");
        int pageSize = settings == null ? 9 : settings.getInt("messages.help.entries-per-page", 9);
        int safePageSize = Math.max(1, pageSize);
        return Math.max(1, (visibleEntries(sender).size() + safePageSize - 1) / safePageSize);
    }

    private List<HelpEntry> visibleEntries(CommandSender sender) {
        FileConfiguration settings = plugin.getConfigManager().getConfig("settings");
        if (settings == null) {
            return List.of();
        }

        List<HelpEntry> entries = new ArrayList<>();
        for (Map<?, ?> raw : settings.getMapList("messages.help.entries")) {
            String permission = value(raw.get("permission"));
            if (!permission.isBlank() && !sender.hasPermission(permission)) {
                continue;
            }
            String command = value(raw.get("command"));
            String description = value(raw.get("description"));
            String suggest = value(raw.get("suggest"));
            if (command.isBlank() || description.isBlank()) {
                continue;
            }
            String moduleId = moduleFor(command);
            if (moduleId != null && !plugin.getModuleManager().isModuleEnabled(moduleId)) {
                continue;
            }
            if ("utility".equals(moduleId)
                    && plugin.getModuleManager().getModule("utility") instanceof UtilityModule utility
                    && !utility.isCommandEnabled(commandRoot(command))) {
                continue;
            }
            entries.add(new HelpEntry(command, description,
                    suggest.isBlank() ? defaultSuggestion(command) : suggest));
        }
        return entries;
    }

    private String defaultSuggestion(String command) {
        int separator = command.indexOf(' ');
        return command.substring(0, separator > 0 ? separator : command.length()) + " ";
    }

    private String moduleFor(String command) {
        return switch (commandRoot(command)) {
            case "menuitem" -> "menuitem";
            case "code" -> "codes";
            case "grace" -> "grace";
            case "sign", "itemsign" -> "itemsign";
            case "voucher" -> "vouchers";
            case "craft", "anvil", "smithingtable", "cartographytable", "grindstone",
                    "loom", "stonecutter", "disposal", "hat", "condense", "seen",
                    "clear", "gmc", "gms", "gmsp", "gma", "ping", "fly", "speed",
                    "broadcast", "vubroadcast", "helpop", "heal", "feed", "repair",
                    "nick", "skull", "suicide", "near", "vtop", "ptime", "pweather",
                    "sell" -> "utility";
            case "vutilsadmin" -> command.toLowerCase().contains(" deathspawn")
                    ? "deathspawn"
                    : null;
            default -> null;
        };
    }

    private String commandRoot(String command) {
        String normalized = command.startsWith("/") ? command.substring(1) : command;
        int separator = normalized.indexOf(' ');
        return (separator < 0 ? normalized : normalized.substring(0, separator)).toLowerCase();
    }

    static Component renderEntry(String command, String description, String suggest) {
        return CommandHelpRenderer.render(
                CommandHelpRenderer.Entry.of(command, description, suggest));
    }

    private Component navigation(int page, int totalPages) {
        Component previous = page > 1
                ? Component.text("« Anterior", PRIMARY)
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Ir a la página anterior", NamedTextColor.GRAY)))
                        .clickEvent(ClickEvent.runCommand("/valerinutils help " + (page - 1)))
                : Component.text("« Anterior", NamedTextColor.DARK_GRAY);
        Component next = page < totalPages
                ? Component.text("Siguiente »", PRIMARY)
                        .hoverEvent(HoverEvent.showText(
                                Component.text("Ir a la página siguiente", NamedTextColor.GRAY)))
                        .clickEvent(ClickEvent.runCommand("/valerinutils help " + (page + 1)))
                : Component.text("Siguiente »", NamedTextColor.DARK_GRAY);
        return Component.text()
                .append(previous)
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Página " + page + "/" + totalPages, NamedTextColor.GRAY))
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(next)
                .build();
    }

    private void sendInvalidPage(CommandSender sender, int totalPages) {
        sender.sendMessage(Component.text()
                .append(Component.text("Página inválida. Usa ", ERROR))
                .append(Component.text("/valerinutils help <1-" + totalPages + ">",
                        NamedTextColor.WHITE))
                .append(Component.text(".", ERROR))
                .build());
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private record HelpEntry(String command, String description, String suggest) {
    }
}
