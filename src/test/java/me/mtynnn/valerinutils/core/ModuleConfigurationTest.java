package me.mtynnn.valerinutils.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ModuleConfigurationTest {
    @Test
    void everyRegisteredModuleHasItsOwnEnabledFlag() throws IOException {
        for (String file : List.of(
                "codes.yml", "deathspawn.yml", "grace.yml", "itemsign.yml",
                "killrewards.yml", "menuitem.yml", "utilities.yml", "vouchers.yml")) {
            try (var stream = getClass().getResourceAsStream("/modules/" + file)) {
                assertNotNull(stream, file);
                String yaml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(yaml.matches("(?s)^\\s*enabled:\\s*(true|false)\\s.*"), file);
            }
        }
    }
}
