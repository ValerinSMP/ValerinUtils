package me.mtynnn.valerinutils.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlaceholderConditionTest {
    @Test
    void detectsResolvedAndUnresolvedPlaceholders() {
        assertTrue(PlaceholderCondition.matches("%koth_active%", "true", "resolved", ""));
        assertTrue(PlaceholderCondition.matches("%koth_active%", "%koth_active%", "unresolved", ""));
    }

    @Test
    void supportsBooleanAndTextOperators() {
        assertTrue(PlaceholderCondition.matches("", "Sí", "truthy", ""));
        assertTrue(PlaceholderCondition.matches("", "Arena_KOTH", "contains", "koth"));
        assertFalse(PlaceholderCondition.matches("", "inactive", "equals", "active"));
    }

    @Test
    void invalidRegexDoesNotBreakCommandSelection() {
        assertFalse(PlaceholderCondition.matches("", "value", "regex", "["));
    }
}
