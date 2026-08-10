package me.mtynnn.valerinutils.crimson;

import org.bukkit.configuration.ConfigurationSection;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

record CrimsonProtectionSettings(
        boolean enabled,
        Set<String> worlds,
        Set<String> allowedBreakIds,
        boolean denyPlace,
        String bypassPermission) {

    CrimsonProtectionSettings {
        worlds = normalize(worlds);
        allowedBreakIds = normalize(allowedBreakIds);
        bypassPermission = bypassPermission == null ? "" : bypassPermission.trim();
    }

    static Optional<CrimsonProtectionSettings> parse(ConfigurationSection root) {
        if (root == null
                || !(root.get("enabled") instanceof Boolean enabled)
                || !(root.get("worlds") instanceof List<?> worlds)
                || !(root.get("allowed-break-ids") instanceof List<?> ids)
                || !(root.get("deny-place") instanceof Boolean denyPlace)
                || !(root.get("bypass-permission") instanceof String permission)
                || permission.isBlank()) {
            return Optional.empty();
        }

        Set<String> normalizedWorlds = strings(worlds);
        Set<String> normalizedIds = strings(ids);
        if (normalizedWorlds == null || normalizedIds == null) {
            return Optional.empty();
        }
        return Optional.of(new CrimsonProtectionSettings(
                enabled, normalizedWorlds, normalizedIds, denyPlace, permission));
    }

    boolean shouldCancelBreak(String world, boolean flagAllowed, boolean bypass, String nexoId) {
        return applies(world, flagAllowed, bypass)
                && (nexoId == null || !allowedBreakIds.contains(normalize(nexoId)));
    }

    boolean shouldCancelPlace(String world, boolean flagAllowed, boolean bypass) {
        return denyPlace && applies(world, flagAllowed, bypass);
    }

    boolean inScope(String world, boolean bypass) {
        return enabled && !bypass && worlds.contains(normalize(world));
    }

    private boolean applies(String world, boolean flagAllowed, boolean bypass) {
        return flagAllowed && inScope(world, bypass);
    }

    private static Set<String> strings(List<?> values) {
        Set<String> result = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String text) || text.isBlank()) {
                return null;
            }
            result.add(normalize(text));
        }
        return Set.copyOf(result);
    }

    private static Set<String> normalize(Set<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            normalized.add(normalize(value));
        }
        return Set.copyOf(normalized);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}

final class CrimsonProtectionSnapshot {
    private volatile CrimsonProtectionSettings current;

    CrimsonProtectionSnapshot(CrimsonProtectionSettings initial) {
        current = initial;
    }

    boolean reload(ConfigurationSection root) {
        Optional<CrimsonProtectionSettings> parsed = CrimsonProtectionSettings.parse(root);
        if (parsed.isEmpty()) {
            return false;
        }
        current = parsed.get();
        return true;
    }

    CrimsonProtectionSettings current() {
        return current;
    }
}
