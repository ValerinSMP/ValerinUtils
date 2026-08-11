package me.mtynnn.valerinutils.core;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerDefaultsTest {
    @Test
    void missingCrimsonDefaultsAreAddedWithoutOverwritingCustomization() {
        YamlConfiguration target = new YamlConfiguration();
        target.set("crimson-protection.worlds", List.of("custom_world"));

        YamlConfiguration defaults = new YamlConfiguration();
        defaults.set("crimson-protection.enabled", true);
        defaults.set("crimson-protection.worlds", List.of("world_crimson"));
        defaults.set("crimson-protection.allowed-break-ids", List.of("crimson_ore"));
        defaults.set("crimson-protection.deny-place", true);
        defaults.set("crimson-protection.bypass-permission", "valerinutils.crimsonprotection.bypass");

        assertTrue(ConfigManager.mergeSectionMissing(target, defaults, ""));
        assertEquals(List.of("custom_world"), target.getStringList("crimson-protection.worlds"));
        assertEquals(List.of("crimson_ore"), target.getStringList("crimson-protection.allowed-break-ids"));
        assertTrue(target.getBoolean("crimson-protection.deny-place"));
    }

    @Test
    void bundledResourcesExposeTheCrimsonContract() throws Exception {
        try (var settingsStream = getClass().getResourceAsStream("/settings.yml");
             var pluginStream = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(settingsStream);
            assertNotNull(pluginStream);
            YamlConfiguration settings = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(settingsStream, StandardCharsets.UTF_8));
            YamlConfiguration descriptor = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(pluginStream, StandardCharsets.UTF_8));

            assertTrue(settings.getBoolean("crimson-protection.enabled"));
            assertEquals(List.of("world_crimson"), settings.getStringList("crimson-protection.worlds"));
            assertEquals(List.of("crimson_ore"), settings.getStringList("crimson-protection.allowed-break-ids"));
            assertTrue(descriptor.getStringList("softdepend").contains("Nexo"));
            assertEquals("op", descriptor.getString("permissions.valerinutils.crimsonprotection.bypass.default"));
        }
    }

    @Test
    void prefixMigrationUpdatesTheBundledDefaultWithoutOverwritingCustomValues() {
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("messages.palette-version", 1);
        bundled.set("messages.prefix",
                "<dark_gray>[<#FFD166>ᴠᴀʟᴇʀɪɴ</#FFD166><dark_gray>]</dark_gray> ");

        assertTrue(ConfigManager.migratePrefix(bundled));
        assertEquals(2, bundled.getInt("messages.palette-version"));
        assertEquals("<dark_gray>[<#FFD166>ᴠᴀʟᴇʀɪɴ</#FFD166>]</dark_gray> <reset>",
                bundled.getString("messages.prefix"));

        YamlConfiguration custom = new YamlConfiguration();
        custom.set("messages.palette-version", 1);
        custom.set("messages.prefix", "<blue>[Custom]</blue> ");

        assertTrue(ConfigManager.migratePrefix(custom));
        assertEquals("<blue>[Custom]</blue> ", custom.getString("messages.prefix"));
    }
}
