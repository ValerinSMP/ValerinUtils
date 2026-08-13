package me.mtynnn.valerinutils.modules.events;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class EventsModuleTest {
    @Test
    void formatsLegacyDurations() {
        assertEquals("0m", EventsModule.formatDuration(0));
        assertEquals("1m 5s", EventsModule.formatDuration(65));
        assertEquals("1h 1m", EventsModule.formatDuration(3665));
    }

    @Test
    void migrationCopiesOnlyMissingFiles(@TempDir Path directory) throws Exception {
        Path source = Files.writeString(directory.resolve("source.yml"), "new");
        Path target = Files.writeString(directory.resolve("target.yml"), "custom");
        assertFalse(EventsModule.copyIfMissing(source, target));
        assertEquals("custom", Files.readString(target));
        Path missing = directory.resolve("missing.yml");
        assertTrue(EventsModule.copyIfMissing(source, missing));
        assertEquals("new", Files.readString(missing));
    }
}
