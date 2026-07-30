package me.mtynnn.valerinutils.modules.deathspawn;

import me.clip.placeholderapi.PlaceholderAPI;
import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.BaseModule;
import me.mtynnn.valerinutils.core.CommandHelpRenderer;
import me.mtynnn.valerinutils.core.PlaceholderCondition;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

public final class DeathSpawnModule extends BaseModule implements Listener {
    private static final Pattern RULE_ID = Pattern.compile("[a-z0-9_-]{1,32}");
    private final Map<UUID, Location> deathLocations = new HashMap<>();

    public DeathSpawnModule(ValerinUtils plugin) {
        super(plugin);
    }

    @Override
    public String getId() {
        return "deathspawn";
    }

    @Override
    protected void onEnableModule() {
        registerListener(this);
        debug("Modulo habilitado.");
    }

    @Override
    protected void onDisableModule() {
        deathLocations.clear();
        debug("Modulo deshabilitado.");
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        deathLocations.put(event.getPlayer().getUniqueId(), event.getPlayer().getLocation().clone());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Location deathLocation = deathLocations.remove(player.getUniqueId());
        if (deathLocation == null || deathLocation.getWorld() == null) {
            return;
        }
        String deathWorld = deathLocation.getWorld().getName();

        FileConfiguration config = cfg();
        if (config == null) {
            return;
        }

        for (Map<?, ?> rule : config.getMapList("rules")) {
            if (!booleanValue(rule.get("enabled"), true) || !matchesWorld(rule.get("death-worlds"), deathWorld)) {
                continue;
            }
            if (!matchesWorldGuardRegion(deathLocation, rule.get("worldguard-region"))) {
                continue;
            }
            if (!matchesCondition(player, rule.get("condition"))) {
                continue;
            }

            Location destination = resolveDestination(rule.get("destination"));
            if (destination == null) {
                plugin.getLogger().warning("[DeathSpawn] Destino invalido para la regla '" + rule.get("id") + "'.");
                continue;
            }
            event.setRespawnLocation(destination);
            runPostRespawnCommand(player, rule.get("post-respawn-command"));
            debug("Regla " + rule.get("id") + " aplicada a " + player.getName() + " desde " + deathWorld + ".");
            return;
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        deathLocations.remove(event.getPlayer().getUniqueId());
    }

    public boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendAdminHelp(sender);
            return true;
        }

        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "set" -> handleSet(sender, args);
            case "list" -> handleList(sender);
            case "enable" -> handleState(sender, args, true);
            case "disable" -> handleState(sender, args, false);
            case "remove" -> handleRemove(sender, args);
            default -> {
                sendAdminHelp(sender);
                yield true;
            }
        };
    }

    private void sendAdminHelp(CommandSender sender) {
        CommandHelpRenderer.send(sender, "DeathSpawn", List.of(
                CommandHelpRenderer.Entry.of(
                        "/vutilsadmin deathspawn set (id) (región)",
                        "Crear una regla en tu ubicación",
                        "/vutilsadmin deathspawn set "),
                CommandHelpRenderer.Entry.of(
                        "/vutilsadmin deathspawn list",
                        "Listar las reglas configuradas",
                        "/vutilsadmin deathspawn list"),
                CommandHelpRenderer.Entry.of(
                        "/vutilsadmin deathspawn enable (id)",
                        "Activar una regla",
                        "/vutilsadmin deathspawn enable "),
                CommandHelpRenderer.Entry.of(
                        "/vutilsadmin deathspawn disable (id)",
                        "Desactivar una regla",
                        "/vutilsadmin deathspawn disable "),
                CommandHelpRenderer.Entry.of(
                        "/vutilsadmin deathspawn remove (id)",
                        "Eliminar una regla",
                        "/vutilsadmin deathspawn remove ")));
    }

    public List<String> tabCompleteAdmin(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filter(List.of("set", "list", "enable", "disable", "remove"), args[0]);
        }
        if (args.length == 2 && Set.of("enable", "disable", "remove")
                .contains(args[0].toLowerCase(Locale.ROOT))) {
            return filter(ruleIds(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            return filter(ruleIds(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("set") && sender instanceof Player player) {
            return filter(worldGuardRegionIds(player), args[2]);
        }
        return Collections.emptyList();
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            send(sender, "admin-player-only",
                    "%prefix%<red>Debes ejecutar este comando dentro del juego.");
            return true;
        }
        if (args.length < 3) {
            send(sender, "admin-set-usage",
                    "%prefix%<gray>Uso: <yellow>/valerinutilsadmin deathspawn set <id> <region>");
            return true;
        }

        String id = args[1].toLowerCase(Locale.ROOT);
        if (!RULE_ID.matcher(id).matches()) {
            send(sender, "admin-invalid-id",
                    "%prefix%<red>El ID solo puede usar letras minusculas, numeros, guion y guion bajo.");
            return true;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            send(sender, "admin-worldguard-required",
                    "%prefix%<red>WorldGuard debe estar instalado y activado.");
            return true;
        }

        String region = args[2];
        Set<String> availableRegions = worldGuardRegionIds(player);
        String canonicalRegion = availableRegions.stream()
                .filter(region::equalsIgnoreCase)
                .findFirst()
                .orElse(null);
        if (canonicalRegion == null) {
            send(sender, "admin-unknown-region",
                    "%prefix%<red>La region <yellow>%region%</yellow> no existe en este mundo.",
                    "%region%", region);
            return true;
        }

        Location location = player.getLocation();
        List<Map<String, Object>> rules = mutableRules();
        Map<String, Object> rule = findRule(rules, id);
        if (rule == null) {
            rule = new LinkedHashMap<>();
            rules.add(rule);
        }
        rule.put("id", id);
        rule.put("enabled", true);
        rule.put("death-worlds", List.of(location.getWorld().getName()));
        rule.put("worldguard-region", canonicalRegion);

        Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("type", "location");
        destination.put("world", location.getWorld().getName());
        destination.put("x", location.getX());
        destination.put("y", location.getY());
        destination.put("z", location.getZ());
        destination.put("yaw", location.getYaw());
        destination.put("pitch", location.getPitch());
        rule.put("destination", destination);
        saveRules(rules);

        send(sender, "admin-set",
                "%prefix%<green>Regla <yellow>%id%</yellow> guardada para <aqua>%region%</aqua> en tu ubicacion.",
                "%id%", id, "%region%", canonicalRegion);
        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<Map<String, Object>> rules = mutableRules();
        if (rules.isEmpty()) {
            send(sender, "admin-list-empty", "%prefix%<gray>No hay reglas de DeathSpawn.");
            return true;
        }

        send(sender, "admin-list-header", "%prefix%<aqua>Reglas de DeathSpawn:</aqua>");
        for (Map<String, Object> rule : rules) {
            String id = stringValue(rule.get("id"));
            String region = stringValue(rule.get("worldguard-region"));
            boolean enabled = booleanValue(rule.get("enabled"), true);
            send(sender, "admin-list-entry",
                    "<gray>• <yellow>%id%</yellow> <dark_gray>→</dark_gray> <aqua>%region%</aqua> %state%",
                    "%id%", id, "%region%", region.isBlank() ? "-" : region,
                    "%state%", enabled ? "<green>ON</green>" : "<red>OFF</red>");
        }
        return true;
    }

    private boolean handleState(CommandSender sender, String[] args, boolean enabled) {
        if (args.length < 2) {
            send(sender, "admin-state-usage",
                    "%prefix%<gray>Uso: <yellow>/valerinutilsadmin deathspawn <enable|disable> <id>");
            return true;
        }
        List<Map<String, Object>> rules = mutableRules();
        Map<String, Object> rule = findRule(rules, args[1]);
        if (rule == null) {
            unknownRule(sender, args[1]);
            return true;
        }
        rule.put("enabled", enabled);
        saveRules(rules);
        send(sender, "admin-state",
                "%prefix%<gray>Regla <yellow>%id%</yellow>: %state%",
                "%id%", stringValue(rule.get("id")),
                "%state%", enabled ? "<green>ACTIVADA</green>" : "<red>DESACTIVADA</red>");
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "admin-remove-usage",
                    "%prefix%<gray>Uso: <yellow>/valerinutilsadmin deathspawn remove <id>");
            return true;
        }
        List<Map<String, Object>> rules = mutableRules();
        Map<String, Object> rule = findRule(rules, args[1]);
        if (rule == null) {
            unknownRule(sender, args[1]);
            return true;
        }
        String id = stringValue(rule.get("id"));
        rules.remove(rule);
        saveRules(rules);
        send(sender, "admin-removed",
                "%prefix%<green>Regla <yellow>%id%</yellow> eliminada.", "%id%", id);
        return true;
    }

    private List<Map<String, Object>> mutableRules() {
        FileConfiguration config = cfg();
        if (config == null) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<?, ?> source : config.getMapList("rules")) {
            Map<String, Object> copy = new LinkedHashMap<>();
            source.forEach((key, value) -> copy.put(String.valueOf(key), value));
            result.add(copy);
        }
        return result;
    }

    private Map<String, Object> findRule(List<Map<String, Object>> rules, String id) {
        return rules.stream()
                .filter(rule -> stringValue(rule.get("id")).equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    private List<String> ruleIds() {
        return mutableRules().stream()
                .map(rule -> stringValue(rule.get("id")))
                .filter(id -> !id.isBlank())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private Set<String> worldGuardRegionIds(Player player) {
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return Set.of();
        }
        try {
            return WorldGuardRegionResolver.regionIds(player.getWorld());
        } catch (LinkageError error) {
            return Set.of();
        }
    }

    private void saveRules(List<Map<String, Object>> rules) {
        FileConfiguration config = cfg();
        if (config == null) {
            return;
        }
        config.set("rules", rules);
        plugin.getConfigManager().saveConfig(getId());
    }

    private void unknownRule(CommandSender sender, String id) {
        send(sender, "admin-unknown-rule",
                "%prefix%<red>No existe la regla <yellow>%id%</yellow>.", "%id%", id);
    }

    private List<String> filter(Iterable<String> candidates, String partial) {
        String normalized = partial.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(candidate);
            }
        }
        return result;
    }

    private void send(CommandSender sender, String key, String fallback, String... replacements) {
        Object configured = cfg() == null ? null : cfg().get("messages." + key);
        if (configured instanceof List<?> lines) {
            for (Object line : lines) {
                if (!(line instanceof String text)) {
                    continue;
                }
                for (int index = 0; index + 1 < replacements.length; index += 2) {
                    text = text.replace(replacements[index], replacements[index + 1]);
                }
                sender.sendMessage(comp(text));
            }
            return;
        }
        String message = msg(key, fallback);
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }
        sender.sendMessage(comp(message));
    }

    private boolean matchesWorld(Object value, String deathWorld) {
        if (!(value instanceof List<?> worlds) || worlds.isEmpty()) {
            return false;
        }
        return worlds.stream().map(Object::toString)
                .anyMatch(world -> world.equals("*") || world.equalsIgnoreCase(deathWorld));
    }

    private boolean matchesCondition(Player player, Object value) {
        if (!(value instanceof Map<?, ?> condition)) {
            return true;
        }
        String placeholder = stringValue(condition.get("placeholder"));
        String resolved = placeholder;
        if (!placeholder.isBlank() && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            resolved = PlaceholderAPI.setPlaceholders(player, placeholder);
        }
        return PlaceholderCondition.matches(placeholder, resolved,
                stringValue(condition.get("operator")), stringValue(condition.get("value")));
    }

    private boolean matchesWorldGuardRegion(Location deathLocation, Object value) {
        String expectedRegion = stringValue(value);
        if (expectedRegion.isBlank()) {
            return true;
        }
        if (!Bukkit.getPluginManager().isPluginEnabled("WorldGuard")) {
            return false;
        }
        try {
            return WorldGuardRegionResolver.isInside(deathLocation, expectedRegion);
        } catch (LinkageError error) {
            plugin.getLogger().warning("[DeathSpawn] WorldGuard no esta disponible: " + error.getMessage());
            return false;
        }
    }

    private Location resolveDestination(Object value) {
        if (!(value instanceof Map<?, ?> destination)) {
            return null;
        }
        String worldName = stringValue(destination.get("world"));
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        if ("world-spawn".equalsIgnoreCase(stringValue(destination.get("type")))) {
            return world.getSpawnLocation();
        }
        try {
            double x = doubleValue(destination.get("x"));
            double y = doubleValue(destination.get("y"));
            double z = doubleValue(destination.get("z"));
            float yaw = (float) doubleValue(destination.get("yaw"));
            float pitch = (float) doubleValue(destination.get("pitch"));
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void runPostRespawnCommand(Player player, Object value) {
        String command = stringValue(value);
        if (command.isBlank()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && isActive()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("%player%", player.getName()));
            }
        });
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString();
    }

    private boolean booleanValue(Object value, boolean defaultValue) {
        return value == null ? defaultValue : Boolean.parseBoolean(value.toString());
    }

    private double doubleValue(Object value) {
        return value == null ? 0 : Double.parseDouble(value.toString());
    }
}
