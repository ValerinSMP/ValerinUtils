package me.mtynnn.valerinutils.core;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public final class PlaceholderCondition {
    private PlaceholderCondition() {
    }

    public static boolean matches(String rawPlaceholder, String resolvedValue, String operator, String expectedValue) {
        String raw = rawPlaceholder == null ? "" : rawPlaceholder;
        String actual = resolvedValue == null ? "" : resolvedValue.trim();
        String expected = expectedValue == null ? "" : expectedValue.trim();
        String normalizedOperator = operator == null ? "equals" : operator.toLowerCase(Locale.ROOT);

        return switch (normalizedOperator) {
            case "resolved" -> !actual.isEmpty() && !actual.equals(raw);
            case "unresolved" -> actual.isEmpty() || actual.equals(raw);
            case "not_equals", "not-equals" -> !actual.equalsIgnoreCase(expected);
            case "contains" -> actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
            case "not_contains", "not-contains" ->
                    !actual.toLowerCase(Locale.ROOT).contains(expected.toLowerCase(Locale.ROOT));
            case "regex" -> safeRegexMatch(expected, actual);
            case "truthy" -> isTruthy(actual);
            case "falsy" -> !isTruthy(actual);
            default -> actual.equalsIgnoreCase(expected);
        };
    }

    private static boolean safeRegexMatch(String regex, String actual) {
        try {
            return Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(actual).matches();
        } catch (PatternSyntaxException ignored) {
            return false;
        }
    }

    private static boolean isTruthy(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on", "enabled", "active", "1", "si", "sí" -> true;
            default -> false;
        };
    }
}
