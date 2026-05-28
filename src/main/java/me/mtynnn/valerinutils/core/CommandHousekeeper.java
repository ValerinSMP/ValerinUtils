package me.mtynnn.valerinutils.core;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CommandHousekeeper {

    private final ValerinUtils plugin;

    public CommandHousekeeper(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    // ========================================================================
    // PUBLIC API
    // ========================================================================

    public void schedule() {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                purgeRegisteredCommands(false);
                adoptCurrentPluginCommands();
                repairBrigadierDispatcher();
                logCommandRegistrationState();
            } catch (Throwable t) {
                plugin.getLogger().warning("Command housekeeping failed: " + t.getClass().getSimpleName() + " - " + t.getMessage());
            }
        }, 20L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                syncCommandsSafe();
                plugin.getLogger().info("Command dispatcher synced.");
            } catch (Throwable t) {
                plugin.getLogger().warning("Command dispatcher sync failed: " + t.getClass().getSimpleName() + " - " + t.getMessage());
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    try {
                        syncCommandsSafe();
                        plugin.getLogger().info("Command dispatcher synced (retry).");
                    } catch (Throwable retry) {
                        plugin.getLogger().warning("Command dispatcher sync retry failed: " + retry.getClass().getSimpleName() + " - " + retry.getMessage());
                    }
                }, 20L);
            }
        }, 40L);
    }

    public void reinstateAll() {
        reinstatePluginCommands();
    }

    public void clearBindings() {
        clearCoreCommandBindings();
    }

    public void syncNow() {
        try {
            syncCommandsSafe();
        } catch (Throwable ignored) {
        }
    }

    // ========================================================================
    // COMMAND MAP INTERNALS
    // ========================================================================

    @SuppressWarnings("unchecked")
    private void purgeRegisteredCommands(boolean currentOnly) {
        try {
            Object commandMap = resolveCommandMap();
            if (commandMap == null) return;

            Map<String, Command> knownCommands = getKnownCommands(commandMap);
            if (knownCommands == null) return;

            int removed = 0;
            Set<String> keysToRemove = new HashSet<>();
            for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
                Command command = entry.getValue();
                if (!(command instanceof PluginCommand pluginCommand) || pluginCommand.getPlugin() == null) continue;
                if (!pluginCommand.getPlugin().getName().equalsIgnoreCase(plugin.getName())) continue;

                boolean shouldRemove = currentOnly
                        ? pluginCommand.getPlugin() == plugin
                        : pluginCommand.getPlugin() != plugin;
                if (shouldRemove) {
                    keysToRemove.add(entry.getKey());
                }
            }

            for (String key : keysToRemove) {
                if (knownCommands.remove(key) != null) removed++;
            }

            if (removed > 0) {
                String scope = currentOnly ? "current instance" : "stale instances";
                plugin.getLogger().info("Purged " + removed + " command map entries for " + plugin.getName() + " (" + scope + ").");
            }
        } catch (UnsupportedOperationException ignored) {
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not purge command map entries: " + t.getClass().getSimpleName() + " - " + t.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void adoptCurrentPluginCommands() {
        try {
            Object commandMap = resolveCommandMap();
            if (commandMap == null) return;

            Map<String, Command> knownCommands = getKnownCommands(commandMap);
            if (knownCommands == null) return;

            int replaced = 0;
            for (Map.Entry<String, Command> entry : knownCommands.entrySet()) {
                Command existing = entry.getValue();
                if (!(existing instanceof PluginCommand oldCmd) || oldCmd.getPlugin() == null) continue;
                if (!oldCmd.getPlugin().getName().equalsIgnoreCase(plugin.getName()) || oldCmd.getPlugin() == plugin) continue;

                PluginCommand current = plugin.getCommand(oldCmd.getName());
                if (current == null) {
                    String key = entry.getKey();
                    int ns = key.indexOf(':');
                    String raw = ns >= 0 && ns + 1 < key.length() ? key.substring(ns + 1) : key;
                    current = plugin.getCommand(raw);
                    if (current == null) {
                        String primary = resolvePrimaryCommandFromLabel(raw);
                        if (primary != null) current = plugin.getCommand(primary);
                    }
                }
                if (current == null) continue;

                entry.setValue(current);
                replaced++;
            }

            if (replaced > 0) {
                plugin.getLogger().info("Rebound " + replaced + " stale command aliases to current " + plugin.getName() + " instance.");
            }
        } catch (UnsupportedOperationException ignored) {
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not adopt command map entries: " + t.getClass().getSimpleName() + " - " + t.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void reinstatePluginCommands() {
        if (plugin.getDescription() == null || plugin.getDescription().getCommands() == null
                || plugin.getDescription().getCommands().isEmpty()) return;

        try {
            Object commandMap = resolveCommandMap();
            if (commandMap == null) return;

            Map<String, Command> knownCommands = getKnownCommands(commandMap);
            if (knownCommands == null) return;

            java.lang.reflect.Constructor<PluginCommand> ctor =
                    PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
            ctor.setAccessible(true);

            String namespace = plugin.getName().toLowerCase(Locale.ROOT);
            int reinstated = 0;

            for (String cmdName : plugin.getDescription().getCommands().keySet()) {
                String lower = cmdName.toLowerCase(Locale.ROOT);
                Command existing = knownCommands.get(lower);
                if (existing instanceof PluginCommand pc && pc.getPlugin() == plugin) continue;

                PluginCommand fresh = ctor.newInstance(lower, plugin);
                knownCommands.put(lower, fresh);
                knownCommands.put(namespace + ":" + lower, fresh);

                for (String alias : getAliasesForCommand(cmdName)) {
                    String aliasLower = alias.toLowerCase(Locale.ROOT);
                    Command aliasExisting = knownCommands.get(aliasLower);
                    if (aliasExisting == null || (aliasExisting instanceof PluginCommand pc && isStaleValerinCommand(pc))) {
                        knownCommands.put(aliasLower, fresh);
                        knownCommands.put(namespace + ":" + aliasLower, fresh);
                    }
                }
                reinstated++;
            }

            if (reinstated > 0) {
                plugin.getLogger().info("Reinstated " + reinstated + " stale command entries for reload.");
            }
        } catch (UnsupportedOperationException ignored) {
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not reinstate command entries: " + t.getClass().getSimpleName() + " - " + t.getMessage());
        }
    }

    private void repairBrigadierDispatcher() {
        try {
            Object commandMap = resolveCommandMap();
            if (commandMap == null) return;

            Map<String, Command> knownCommands = getKnownCommands(commandMap);
            if (knownCommands == null) return;

            int patched = 0;
            for (String label : getAllDeclaredCommandLabels()) {
                String lower = label.toLowerCase(Locale.ROOT);
                PluginCommand fresh = plugin.getCommand(lower);
                if (fresh == null) {
                    String primary = resolvePrimaryCommandFromLabel(lower);
                    if (primary != null) fresh = plugin.getCommand(primary);
                }
                if (fresh != null) {
                    Command existing = knownCommands.get(lower);
                    if (existing instanceof PluginCommand pc && isStaleValerinCommand(pc)) {
                        knownCommands.put(lower, fresh);
                        knownCommands.put(plugin.getName().toLowerCase(Locale.ROOT) + ":" + lower, fresh);
                        patched++;
                    }
                }
            }
            if (patched > 0) {
                plugin.getLogger().info("[BrigadierRepair] Patched " + patched + " stale command map entries.");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[BrigadierRepair] " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    // ========================================================================
    // REFLECTION HELPERS
    // ========================================================================

    private Object resolveCommandMap() {
        Object server = Bukkit.getServer();
        try {
            return server.getClass().getMethod("getCommandMap").invoke(server);
        } catch (Throwable ignored) { }
        try {
            java.lang.reflect.Field field = findField(server.getClass(), "commandMap");
            if (field != null) {
                field.setAccessible(true);
                return field.get(server);
            }
        } catch (Throwable ignored) { }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> getKnownCommands(Object commandMap) {
        try {
            java.lang.reflect.Field field = findField(commandMap.getClass(), "knownCommands");
            if (field == null) {
                for (java.lang.reflect.Field candidate : getAllFields(commandMap.getClass())) {
                    if (!Map.class.isAssignableFrom(candidate.getType())) continue;
                    candidate.setAccessible(true);
                    Object raw = candidate.get(commandMap);
                    if (raw instanceof Map<?, ?> rawMap && mapLooksLikeCommandMap(rawMap)) {
                        return (Map<String, Command>) rawMap;
                    }
                }
                return null;
            }
            field.setAccessible(true);
            Object rawKnown = field.get(commandMap);
            if (!(rawKnown instanceof Map<?, ?> rawMap)) return null;
            return unwrapIfUnmodifiable((Map<String, Command>) rawMap);
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> unwrapIfUnmodifiable(Map<String, Command> map) {
        if (map == null || !map.getClass().getName().contains("nmodifiable")) return map;
        for (java.lang.reflect.Field f : getAllFields(map.getClass())) {
            if (!Map.class.isAssignableFrom(f.getType())) continue;
            try {
                f.setAccessible(true);
                Object inner = f.get(map);
                if (inner instanceof Map<?, ?> innerMap && innerMap != map) {
                    return (Map<String, Command>) innerMap;
                }
            } catch (Throwable ignored) { }
        }
        return map;
    }

    private java.lang.reflect.Field findField(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private List<java.lang.reflect.Field> getAllFields(Class<?> type) {
        List<java.lang.reflect.Field> fields = new ArrayList<>();
        Class<?> current = type;
        while (current != null) {
            Collections.addAll(fields, current.getDeclaredFields());
            current = current.getSuperclass();
        }
        return fields;
    }

    private boolean mapLooksLikeCommandMap(Map<?, ?> map) {
        if (map.isEmpty()) return false;
        int inspected = 0;
        for (Object value : map.values()) {
            inspected++;
            if (value instanceof Command) return true;
            if (inspected >= 20) break;
        }
        return false;
    }

    // ========================================================================
    // UTILITY HELPERS
    // ========================================================================

    private void clearCoreCommandBindings() {
        clearCommandBinding("valerinutils");
        clearCommandBinding("menuitem");
    }

    private void clearCommandBinding(String name) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd == null) return;
        cmd.setExecutor(null);
        cmd.setTabCompleter(null);
    }

    private void syncCommandsSafe() {
        try {
            Object server = plugin.getServer();
            server.getClass().getMethod("syncCommands").invoke(server);
        } catch (NoSuchMethodException ignored) {
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private boolean isStaleValerinCommand(PluginCommand pc) {
        Plugin owner = pc.getPlugin();
        return owner != null && owner.getName().equalsIgnoreCase(plugin.getName()) && owner != plugin;
    }

    private String resolvePrimaryCommandFromLabel(String label) {
        if (label == null || label.isBlank() || plugin.getDescription() == null || plugin.getDescription().getCommands() == null) return null;

        String probe = label.toLowerCase(Locale.ROOT);
        if (plugin.getDescription().getCommands().containsKey(probe)) return probe;

        for (Map.Entry<String, Map<String, Object>> entry : plugin.getDescription().getCommands().entrySet()) {
            Object aliasesRaw = entry.getValue().get("aliases");
            if (aliasesRaw instanceof String aliasSingle) {
                if (aliasSingle.equalsIgnoreCase(probe)) return entry.getKey().toLowerCase(Locale.ROOT);
            } else if (aliasesRaw instanceof List<?> aliasList) {
                for (Object aliasObj : aliasList) {
                    if (aliasObj != null && probe.equalsIgnoreCase(aliasObj.toString())) return entry.getKey().toLowerCase(Locale.ROOT);
                }
            }
        }
        return null;
    }

    private List<String> getAliasesForCommand(String commandName) {
        if (plugin.getDescription() == null || plugin.getDescription().getCommands() == null) return Collections.emptyList();
        Map<String, Object> meta = plugin.getDescription().getCommands().get(commandName);
        if (meta == null) return Collections.emptyList();

        Object aliasesRaw = meta.get("aliases");
        if (aliasesRaw instanceof String aliasSingle) return List.of(aliasSingle);
        if (aliasesRaw instanceof List<?> aliasList) {
            List<String> out = new ArrayList<>();
            for (Object a : aliasList) {
                if (a != null) out.add(a.toString());
            }
            return out;
        }
        return Collections.emptyList();
    }

    private Set<String> getAllDeclaredCommandLabels() {
        Set<String> labels = new HashSet<>();
        if (plugin.getDescription() == null || plugin.getDescription().getCommands() == null) return labels;

        for (String cmdName : plugin.getDescription().getCommands().keySet()) {
            labels.add(cmdName.toLowerCase(Locale.ROOT));
            for (String alias : getAliasesForCommand(cmdName)) {
                labels.add(alias.toLowerCase(Locale.ROOT));
            }
        }
        return labels;
    }

    private void logCommandRegistrationState() {
        if (plugin.getDescription() == null || plugin.getDescription().getCommands() == null) return;

        int missing = 0;
        for (String name : plugin.getDescription().getCommands().keySet()) {
            if (plugin.getCommand(name) == null) {
                plugin.getLogger().warning("Command not registered: /" + name);
                missing++;
            }
        }
        if (missing == 0) {
            plugin.getLogger().info("Command registration check: OK (" + plugin.getDescription().getCommands().size() + " commands).");
        }
    }
}
