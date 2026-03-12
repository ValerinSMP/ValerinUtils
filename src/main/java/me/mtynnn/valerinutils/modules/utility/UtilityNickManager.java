package me.mtynnn.valerinutils.modules.utility;

import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UtilityNickManager {

    private static final Pattern LEGACY_CODE_PATTERN = Pattern.compile("(?i)[&§]([0-9a-fk-or])");
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("(?i)&#[0-9a-f]{6}|&x(?:&[0-9a-f]){6}");
    private static final Pattern MINI_TAG_PATTERN = Pattern.compile("<\\s*/?\\s*([a-zA-Z0-9_:#-]+)[^>]*>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s");

    private static final Set<String> BASIC_MINI_TAGS = Set.of(
            "black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple", "gold", "gray",
        "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "reset");
    private static final Set<String> FORMAT_TAGS = Set.of(
        "bold", "italic", "underlined", "strikethrough");

    enum NickTier {
        NONE, BASIC, FORMAT, HEX;

        String asConfigValue() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    NickTier resolveTier(Player player) {
        if (player.hasPermission("valerinutils.utility.nick.color.hex")) {
            return NickTier.HEX;
        }
        if (player.hasPermission("valerinutils.utility.nick.color.format")) {
            return NickTier.FORMAT;
        }
        if (player.hasPermission("valerinutils.utility.nick.color.basic")) {
            return NickTier.BASIC;
        }
        return NickTier.NONE;
    }

    boolean isFormatAllowed(String raw, NickTier tier) {
        if (raw == null || raw.isBlank()) {
            return false;
        }

        if (containsWhitespace(raw)) {
            return false;
        }

        if (hasMalformedMiniMessageSyntax(raw)) {
            return false;
        }

        if (tier != NickTier.HEX && LEGACY_HEX_PATTERN.matcher(raw).find()) {
            return false;
        }

        Matcher legacyMatcher = LEGACY_CODE_PATTERN.matcher(raw);
        while (legacyMatcher.find()) {
            char code = Character.toLowerCase(legacyMatcher.group(1).charAt(0));
            boolean isColor = (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r';
            boolean isFormat = "lmno".indexOf(code) >= 0;

            // Never allow obfuscated (&k / §k), even for highest tier.
            if (code == 'k') {
                return false;
            }
            if (!isColor && !isFormat) {
                return false;
            }
            if (isColor && tier == NickTier.NONE) {
                return false;
            }
            if (isFormat && tier != NickTier.FORMAT && tier != NickTier.HEX) {
                return false;
            }
        }

        Matcher miniMatcher = MINI_TAG_PATTERN.matcher(raw);
        while (miniMatcher.find()) {
            String token = miniMatcher.group(1).toLowerCase(Locale.ROOT);
            if (token.startsWith("/")) {
                token = token.substring(1);
            }

            if (token.equals("obfuscated")) {
                return false;
            }

            // Closing tag </gradient> is parsed as token "gradient".
            if (token.equals("gradient")) {
                if (tier == NickTier.HEX) {
                    continue;
                }
                return false;
            }

            // Closing tag </color> is parsed as token "color".
            if (token.equals("color")) {
                if (tier != NickTier.NONE) {
                    continue;
                }
                return false;
            }

            if (token.startsWith("#") || token.startsWith("color:#") || token.startsWith("gradient:")) {
                if (tier == NickTier.HEX) {
                    continue;
                }
                return false;
            }

            if (token.startsWith("color:")) {
                String colorToken = token.substring("color:".length());
                if (colorToken.startsWith("#")) {
                    if (tier == NickTier.HEX) {
                        continue;
                    }
                    return false;
                }
                if (BASIC_MINI_TAGS.contains(colorToken)) {
                    if (tier == NickTier.NONE) {
                        return false;
                    }
                    continue;
                }
                return false;
            }

            if (BASIC_MINI_TAGS.contains(token)) {
                if (tier == NickTier.NONE) {
                    return false;
                }
                continue;
            }

            if (FORMAT_TAGS.contains(token)) {
                if (tier == NickTier.FORMAT || tier == NickTier.HEX) {
                    continue;
                }
                return false;
            }

            return false;
        }

        if (tier == NickTier.NONE) {
            return !raw.contains("&") && !raw.contains("§") && !raw.contains("<") && !raw.contains(">");
        }

        return true;
    }

    private boolean hasMalformedMiniMessageSyntax(String raw) {
        if (raw == null || raw.isEmpty()) {
            return false;
        }

        int openCount = 0;
        int closeCount = 0;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '<') {
                openCount++;
            } else if (ch == '>') {
                closeCount++;
            }
        }

        if (openCount != closeCount) {
            return true;
        }

        // If brackets exist, every segment must be a proper MiniMessage-like tag.
        if (openCount > 0) {
            String stripped = MINI_TAG_PATTERN.matcher(raw).replaceAll("");
            return stripped.indexOf('<') >= 0 || stripped.indexOf('>') >= 0;
        }

        return false;
    }

    boolean containsWhitespace(String raw) {
        return raw != null && WHITESPACE_PATTERN.matcher(raw).find();
    }

    String withTrailingReset(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.endsWith("<reset>") || trimmed.endsWith("&r") || trimmed.endsWith("§r")) {
            return trimmed;
        }
        return trimmed + "<reset>";
    }

    String sanitizeStoredNickname(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        // Stored nicks containing obfuscated formats are forcibly dropped.
        String lowered = trimmed.toLowerCase(Locale.ROOT);
        if (lowered.contains("&k") || lowered.contains("§k") || lowered.contains("<obfuscated>")) {
            return null;
        }
        if (containsWhitespace(trimmed)) {
            return null;
        }
        return withTrailingReset(trimmed);
    }
}
