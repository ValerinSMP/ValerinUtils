package me.mtynnn.valerinutils.commands;

import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HelpConfigurationTest {
    @Test
    void runtimeEntryUsesRealAdventureEventsWithoutMarkupText() {
        Component component = ValerinUtilsHelp.renderEntry(
                "/menuitem <on|off|toggle>",
                "Gestionar el ítem del menú",
                "/menuitem ");

        String plain = PlainTextComponentSerializer.plainText().serialize(component);
        assertTrue(plain.contains("/menuitem <on|off|toggle>"));
        assertTrue(plain.contains("Gestionar el ítem del menú"));
        assertFalse(plain.contains("<hover:"));
        assertFalse(plain.contains("<click:"));
        assertTrue(hasClick(component));
        assertTrue(hasHover(component));
    }

    @Test
    void helpContainsEveryPlayerFacingCommandFamily() throws IOException {
        String yaml;
        try (var stream = getClass().getResourceAsStream("/settings.yml")) {
            assertNotNull(stream);
            yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        for (String command : List.of(
                "menuitem", "code", "grace", "craft", "anvil", "smithingtable",
                "cartographytable", "grindstone", "loom", "stonecutter", "disposal",
                "hat", "condense", "seen", "clear", "ping", "fly", "speed",
                "broadcast", "helpop", "heal", "feed", "repair", "nick", "skull",
                "suicide", "near", "ptime", "pweather", "sell", "vtop", "sign",
                "itemsign", "voucher")) {
            assertTrue(yaml.contains("command: \"/" + command), command);
        }
    }

    @Test
    void everyConfiguredHelpEntryProducesValidMiniMessage() throws IOException {
        YamlConfiguration config = load("/settings.yml");

        for (Map<?, ?> entry : config.getMapList("messages.help.entries")) {
            String command = String.valueOf(entry.get("command"));
            String description = String.valueOf(entry.get("description"));
            String suggest = String.valueOf(entry.get("suggest")).replace("'", "");
            Component component = ValerinUtilsHelp.renderEntry(command, description, suggest);
            String plain = PlainTextComponentSerializer.plainText().serialize(component);
            assertFalse(plain.contains("<hover:"), String.valueOf(entry.get("command")));
            assertTrue(hasClick(component), String.valueOf(entry.get("command")));
            assertTrue(hasHover(component), String.valueOf(entry.get("command")));
        }
    }

    @Test
    void everyBundledMessageUsesValidMiniMessage() throws IOException {
        for (String resource : List.of(
                "/settings.yml",
                "/modules/codes.yml",
                "/modules/deathspawn.yml",
                "/modules/grace.yml",
                "/modules/itemsign.yml",
                "/modules/killrewards.yml",
                "/modules/menuitem.yml",
                "/modules/utilities.yml",
                "/modules/vouchers.yml")) {
            YamlConfiguration config = load(resource);
            ConfigurationSection messages = config.getConfigurationSection("messages");
            if (messages != null) {
                assertMessageSection(messages, resource);
            }
        }
    }

    private YamlConfiguration load(String resource) throws IOException {
        try (var stream = getClass().getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private void assertMessageSection(ConfigurationSection section, String resource) {
        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                assertMessageSection(child, resource);
            } else if (value instanceof String text) {
                assertValid(text, resource + ":" + section.getCurrentPath() + "." + key);
            } else if (value instanceof List<?> list) {
                for (int index = 0; index < list.size(); index++) {
                    if (list.get(index) instanceof String text) {
                        assertValid(text, resource + ":" + section.getCurrentPath() + "." + key + "[" + index + "]");
                    }
                }
            }
        }
    }

    private void assertValid(String text, String location) {
        assertFalse(text.contains("<hover:"), location + " must use CommandHelpRenderer");
        assertFalse(text.contains("<click:suggest_command:"), location + " must use CommandHelpRenderer");
        assertFalse(text.contains("<click:run_command:"), location + " must use CommandHelpRenderer");
        String rendered = text.replace("%prefix%", "<dark_gray>[<#FFD166>VALERIN</#FFD166>]</dark_gray> ");
        assertDoesNotThrow(() -> MiniMessage.miniMessage().deserialize(rendered), location);
    }

    private boolean hasClick(Component component) {
        return component.clickEvent() != null || component.children().stream().anyMatch(this::hasClick);
    }

    private boolean hasHover(Component component) {
        return component.hoverEvent() != null || component.children().stream().anyMatch(this::hasHover);
    }
}
