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
            "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white", "color", "reset");
    private static final Set<String> FORMAT_TAGS = Set.of(
            "bold", "italic", "underlined", "strikethrough", "obfuscated");

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
        if (tier == NickTier.HEX) {
            return true;
        }
        if (LEGACY_HEX_PATTERN.matcher(raw).find()) {
            return false;
        }

        Matcher legacyMatcher = LEGACY_CODE_PATTERN.matcher(raw);
        while (legacyMatcher.find()) {
            char code = Character.toLowerCase(legacyMatcher.group(1).charAt(0));
            boolean isColor = (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f') || code == 'r';
            boolean isFormat = "klmno".indexOf(code) >= 0;
            if (isFormat && tier == NickTier.BASIC) {
                return false;
            }
            if (!isColor && !isFormat) {
                return false;
            }
        }

        Matcher miniMessageMatcher = MINI_TAG_PATTERN.matcher(raw);
        while (miniMessageMatcher.find()) {
            String token = miniMessageMatcher.group(1).toLowerCase(Locale.ROOT);
            if (token.startsWith("/")) {
                token = token.substring(1);
            }
            if (token.startsWith("#") || token.startsWith("color:#")) {
                return false;
            }
            if (BASIC_MINI_TAGS.contains(token)) {
                continue;
            }
            if (tier == NickTier.FORMAT && FORMAT_TAGS.contains(token)) {
                continue;
            }
            if (!token.equals("reset")) {
                return false;
            }
        }

        return tier != NickTier.NONE || (!raw.contains("&") && !raw.contains("§") && !raw.contains("<"));
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
        String noSpaces = WHITESPACE_PATTERN.matcher(trimmed).replaceAll("");
        if (noSpaces.isEmpty()) {
            return null;
        }
        return withTrailingReset(noSpaces);
    }
}
