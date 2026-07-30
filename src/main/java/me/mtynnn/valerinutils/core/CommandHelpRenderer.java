package me.mtynnn.valerinutils.core;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

import java.util.List;

public final class CommandHelpRenderer {
    private static final TextColor PRIMARY = TextColor.fromHexString("#FFD166");

    private CommandHelpRenderer() {
    }

    public static void send(CommandSender sender, String title, List<Entry> entries) {
        send(sender, title, entries, null);
    }

    public static void send(CommandSender sender, String title, List<Entry> entries, Component footer) {
        sender.sendMessage(Component.empty());
        sender.sendMessage(Component.text()
                .append(Component.text(title, PRIMARY, TextDecoration.BOLD))
                .append(Component.text(" | Comandos:", PRIMARY))
                .build());
        sender.sendMessage(Component.empty());
        for (Entry entry : entries) {
            sender.sendMessage(render(entry));
        }
        if (footer != null) {
            sender.sendMessage(Component.empty());
            sender.sendMessage(footer);
        }
        sender.sendMessage(Component.empty());
    }

    public static Component render(Entry entry) {
        Component hover = Component.text()
                .append(Component.text(entry.command(), PRIMARY))
                .append(Component.newline())
                .append(Component.text(entry.description(), NamedTextColor.GRAY))
                .build();
        Component command = Component.text(entry.command(), PRIMARY, TextDecoration.ITALIC)
                .hoverEvent(HoverEvent.showText(hover))
                .clickEvent(ClickEvent.suggestCommand(entry.suggestion()));
        Component description = Component.text(entry.description(), NamedTextColor.GRAY)
                .hoverEvent(HoverEvent.showText(Component.text(entry.description(), NamedTextColor.GRAY)))
                .clickEvent(ClickEvent.suggestCommand(entry.suggestion()));
        return Component.text()
                .append(command)
                .append(Component.text("   "))
                .append(description)
                .build();
    }

    public record Entry(String command, String description, String suggestion) {
        public static Entry of(String command, String description, String suggestion) {
            return new Entry(command, description, suggestion);
        }
    }
}
