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
    private static final Pattern MINECRAFT_NICKNAME_PATTERN = Pattern.compile("^[A-Za-z0-9_]{3,16}$");

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
        return isMinecraftStyleNickname(raw);
    }

    boolean isMinecraftStyleNickname(String raw) {
        if (raw == null) {
            return false;
        }
        String trimmed = raw.trim();
        return MINECRAFT_NICKNAME_PATTERN.matcher(trimmed).matches();
    }

    boolean containsWhitespace(String raw) {
        return raw != null && WHITESPACE_PATTERN.matcher(raw).find();
    }

    String withTrailingReset(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim();
    }

    String sanitizeStoredNickname(String raw) {
        if (!isMinecraftStyleNickname(raw)) {
            return null;
        }
        return raw.trim();
    }
}
