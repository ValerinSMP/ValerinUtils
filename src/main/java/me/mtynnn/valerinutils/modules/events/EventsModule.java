package me.mtynnn.valerinutils.modules.events;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.network.CrossServerService;
import me.mtynnn.valerinutils.network.NetworkEnvelope;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class EventsModule implements Listener, CommandExecutor, TabCompleter {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final ValerinUtils plugin;
    private final Map<String, EventDef> definitions = new LinkedHashMap<>();
    private final Map<String, ActiveEvent> active = new HashMap<>();
    private final Map<String, Long> nextActivation = new HashMap<>();
    private final Set<String> pendingClaims = ConcurrentHashMap.newKeySet();
    private BukkitTask ticker;
    private VEventsExpansion expansion;
    private boolean enabled;
    private CrossServerService crossServer;

    public EventsModule(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    public void start(FileConfiguration settings) {
        crossServer = plugin.getCrossServerService();
        if (crossServer != null && crossServer.enabled()) crossServer.listen(this::onNetworkEvent);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getCommandRegistry().declare("vevents", java.util.Set.of("vevent"));
        plugin.getCommandRegistry().bind("vevents", "vevent", this, this);
        migrateLegacyFiles();
        reload(settings);
    }

    public void registerPlaceholders() {
        if (expansion == null) {
            expansion = new VEventsExpansion(plugin, this);
            expansion.register();
        }
    }

    public void reload(FileConfiguration settings) {
        boolean requested = settings != null && settings.getBoolean("vevents.enabled", true);
        if (!requested) {
            enabled = false;
            stopScheduler();
            return;
        }
        enabled = true;
        reloadDefinitions();
    }

    public boolean reloadDefinitions() {
        Map<String, EventDef> loaded = readDefinitions();
        if (loaded == null) return false;
        stopScheduler();
        definitions.clear();
        definitions.putAll(loaded);
        loadData();
        if (crossServer != null && crossServer.enabled()) {
            for (EventDef definition : definitions.values()) {
                long proposed = nextActivation.getOrDefault(definition.id(), computeNext(definition, System.currentTimeMillis()));
                crossServer.syncEvent(definition.id(), proposed, this::applyGlobalState);
            }
        }
        startScheduler();
        plugin.getLogger().info("[vEvents] Loaded " + definitions.size() + " event(s).");
        return true;
    }

    public void stop() {
        enabled = false;
        stopScheduler();
        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }
    }

    private Map<String, EventDef> readDefinitions() {
        File directory = eventsDirectory();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            plugin.getLogger().warning("[vEvents] Could not create " + directory);
            return null;
        }
        File[] files = directory.listFiles((ignored, name) -> name.endsWith(".yml") && !name.equals("data.yml"));
        Map<String, EventDef> loaded = new LinkedHashMap<>();
        if (files == null) return loaded;
        java.util.Arrays.sort(files, java.util.Comparator.comparing(File::getName));
        for (File file : files) {
            try {
                EventDef definition = parse(file);
                loaded.put(definition.id(), definition);
            } catch (RuntimeException error) {
                plugin.getLogger().warning("[vEvents] Invalid event '" + file.getName()
                        + "'; keeping the previous snapshot: " + error.getMessage());
                return null;
            }
        }
        return loaded;
    }

    private EventDef parse(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String id = file.getName().substring(0, file.getName().length() - 4);
        int intervalMin = config.getInt("schedule.interval-days", 1);
        int intervalMax = config.getInt("schedule.interval-days-max", 1);
        int durationMin = config.getInt("duration.min-minutes", 30);
        int durationMax = config.getInt("duration.max-minutes", 60);
        if (intervalMin < 0 || intervalMax < intervalMin || durationMin < 1 || durationMax < durationMin) {
            throw new IllegalArgumentException("invalid interval or duration range");
        }
        return new EventDef(id, config.getString("name", id), config.getBoolean("enabled", true),
                LocalTime.parse(config.getString("schedule.window-start", "12:00")),
                LocalTime.parse(config.getString("schedule.window-end", "20:00")),
                intervalMin, intervalMax, durationMin, durationMax,
                List.copyOf(config.getStringList("start-commands")),
                List.copyOf(config.getStringList("end-commands")),
                config.getBoolean("bossbar.enabled", false),
                config.getString("bossbar.text", "{remaining}"),
                config.getString("bossbar.color", "WHITE"),
                config.getString("bossbar.style", "SOLID"));
    }

    private void migrateLegacyFiles() {
        Path target = eventsDirectory().toPath();
        try {
            Files.createDirectories(target);
            File legacyRoot = new File(plugin.getDataFolder().getParentFile(), "vEvents");
            File legacyEvents = new File(legacyRoot, "events");
            File[] files = legacyEvents.listFiles((ignored, name) -> name.endsWith(".yml"));
            if (files != null) {
                for (File file : files) copyIfMissing(file.toPath(), target.resolve(file.getName()));
            }
            copyIfMissing(new File(legacyRoot, "data.yml").toPath(), target.resolve("data.yml"));
            File[] definitions = eventsDirectory().listFiles((ignored, name) -> name.endsWith(".yml") && !name.equals("data.yml"));
            if (definitions == null || definitions.length == 0) plugin.saveResource("events/xp_boost.yml", false);
        } catch (IOException error) {
            plugin.getLogger().warning("[vEvents] Legacy migration failed: " + error.getMessage());
        }
    }

    static boolean copyIfMissing(Path source, Path target) throws IOException {
        if (!Files.isRegularFile(source) || Files.exists(target)) return false;
        Files.copy(source, target);
        return true;
    }

    private void loadData() {
        nextActivation.clear();
        File file = dataFile();
        if (file.isFile()) {
            YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
            for (String id : data.getKeys(false)) nextActivation.put(id, data.getLong(id));
        }
        long now = System.currentTimeMillis();
        for (EventDef definition : definitions.values()) {
            if (definition.enabled() && !nextActivation.containsKey(definition.id())) {
                nextActivation.put(definition.id(), computeNext(definition, now));
            }
        }
        saveData();
    }

    private void saveData() {
        YamlConfiguration data = new YamlConfiguration();
        nextActivation.forEach(data::set);
        try {
            data.save(dataFile());
        } catch (IOException error) {
            plugin.getLogger().warning("[vEvents] Could not save data.yml: " + error.getMessage());
        }
    }

    private long computeNext(EventDef definition, long afterMs) {
        int days = ThreadLocalRandom.current().nextInt(definition.intervalDaysMin(), definition.intervalDaysMax() + 1);
        LocalDateTime base = LocalDateTime.ofInstant(Instant.ofEpochMilli(afterMs), ZoneId.systemDefault())
                .toLocalDate().plusDays(days).atStartOfDay();
        long windowSeconds = definition.windowEnd().toSecondOfDay() - definition.windowStart().toSecondOfDay();
        long offset = windowSeconds > 0 ? ThreadLocalRandom.current().nextLong(windowSeconds) : 0;
        return base.with(definition.windowStart()).plusSeconds(offset)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private void startScheduler() {
        if (!enabled || ticker != null) return;
        ticker = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    private void stopScheduler() {
        boolean wasRunning = ticker != null || !active.isEmpty();
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
        long now = System.currentTimeMillis();
        for (String id : new ArrayList<>(active.keySet())) {
            ActiveEvent event = active.remove(id);
            hide(event.bar());
            nextActivation.put(id, computeNext(event.definition(), now));
        }
        if (wasRunning) saveData();
    }

    private void tick() {
        if (crossServer != null && crossServer.enabled()) {
            tickGlobal();
            return;
        }
        long now = System.currentTimeMillis();
        for (EventDef definition : definitions.values()) {
            if (!definition.enabled() || active.containsKey(definition.id())) continue;
            Long next = nextActivation.get(definition.id());
            if (next != null && now >= next) startEvent(definition.id());
        }
        for (String id : new ArrayList<>(active.keySet())) {
            ActiveEvent event = active.get(id);
            if (now >= event.endMs()) stopEvent(id, true);
            else updateBossbar(event, now);
        }
    }

    public boolean startEvent(String id) {
        EventDef definition = definitions.get(id);
        if (!enabled || definition == null || active.containsKey(id)) return false;
        if (crossServer != null && crossServer.enabled()) {
            claimGlobalStart(definition, true);
            return true;
        }
        int durationSeconds = ThreadLocalRandom.current()
                .nextInt(definition.durationMinMinutes(), definition.durationMaxMinutes() + 1) * 60;
        long now = System.currentTimeMillis();
        String duration = formatDuration(durationSeconds);
        BossBar bar = null;
        if (definition.bossbarEnabled()) {
            bar = BossBar.bossBar(plugin.parseComponent(definition.bossbarText().replace("{remaining}", duration)),
                    1F, color(definition.bossbarColor()), overlay(definition.bossbarStyle()));
            BossBar shown = bar;
            Bukkit.getOnlinePlayers().forEach(player -> player.showBossBar(shown));
        }
        active.put(id, new ActiveEvent(definition, UUID.randomUUID(), now, now + durationSeconds * 1000L, bar));
        for (String command : definition.startCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replace(command, durationSeconds, duration));
        }
        plugin.getLogger().info("[vEvents] '" + id + "' started for " + duration);
        return true;
    }

    public boolean stopEvent(String id, boolean runEndCommands) {
        if (crossServer != null && crossServer.enabled()) {
            ActiveEvent running = active.get(id);
            if (running == null || !pendingClaims.add("stop:" + id)) return false;
            crossServer.claimEventStop(id, running.runId(), computeNext(running.definition(), System.currentTimeMillis()), state -> {
                pendingClaims.remove("stop:" + id);
                if (state != null) applyGlobalState(state);
            });
            return true;
        }
        ActiveEvent event = active.remove(id);
        if (event == null) return false;
        hide(event.bar());
        if (runEndCommands) {
            for (String command : event.definition().endCommands()) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replace(command, 0, "0m"));
            }
        }
        nextActivation.put(id, computeNext(event.definition(), System.currentTimeMillis()));
        saveData();
        plugin.getLogger().info("[vEvents] '" + id + "' stopped.");
        return true;
    }

    private void tickGlobal() {
        long now = System.currentTimeMillis();
        for (EventDef definition : definitions.values()) {
            ActiveEvent running = active.get(definition.id());
            if (running != null) {
                if (now >= running.endMs() && pendingClaims.add("stop:" + definition.id())) {
                    crossServer.claimEventStop(definition.id(), running.runId(), computeNext(definition, now), state -> {
                        pendingClaims.remove("stop:" + definition.id());
                        if (state != null) applyGlobalState(state);
                    });
                } else updateBossbar(running, now);
                continue;
            }
            Long next = nextActivation.get(definition.id());
            if (definition.enabled() && next != null && now >= next) claimGlobalStart(definition, false);
        }
    }

    private void claimGlobalStart(EventDef definition, boolean manual) {
        if (!pendingClaims.add("start:" + definition.id())) return;
        int duration = ThreadLocalRandom.current()
                .nextInt(definition.durationMinMinutes(), definition.durationMaxMinutes() + 1) * 60;
        long next = computeNext(definition, System.currentTimeMillis() + duration * 1000L);
        crossServer.claimEventStart(definition.id(), duration, next, manual, state -> {
            pendingClaims.remove("start:" + definition.id());
            if (state == null) {
                plugin.getLogger().warning("[vEvents] Global start skipped: Redis unavailable or claim failed.");
            } else applyGlobalState(state);
        });
    }

    private void onNetworkEvent(NetworkEnvelope envelope) {
        if (!envelope.type().equals("EVENT_START") && !envelope.type().equals("EVENT_STOP")) return;
        CrossServerService.GlobalEvent state = CrossServerService.parseEventPayload(envelope.payload());
        if (state != null) applyGlobalState(state);
    }

    private void applyGlobalState(CrossServerService.GlobalEvent state) {
        nextActivation.put(state.id(), state.nextMs());
        EventDef definition = definitions.get(state.id());
        if (definition == null) return;
        if (state.active()) {
            ActiveEvent existing = active.get(state.id());
            if (existing != null && existing.runId().equals(state.runId())) return;
            if (existing != null) hide(existing.bar());
            String remaining = formatDuration(Math.max(0, (state.endMs() - System.currentTimeMillis()) / 1000));
            BossBar bar = null;
            if (definition.bossbarEnabled()) {
                bar = BossBar.bossBar(plugin.parseComponent(definition.bossbarText().replace("{remaining}", remaining)),
                        1F, color(definition.bossbarColor()), overlay(definition.bossbarStyle()));
                BossBar shown = bar;
                Bukkit.getOnlinePlayers().forEach(player -> player.showBossBar(shown));
            }
            active.put(state.id(), new ActiveEvent(definition, state.runId(), state.startMs(), state.endMs(), bar));
            int seconds = (int) Math.max(0, (state.endMs() - state.startMs()) / 1000);
            crossServer.claimEventApplication(state.runId(), "START", claimed -> {
                if (claimed) for (String command : definition.startCommands()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replace(command, seconds, formatDuration(seconds)));
                }
            });
        } else {
            ActiveEvent removed = active.remove(state.id());
            if (removed == null || !removed.runId().equals(state.runId())) return;
            hide(removed.bar());
            crossServer.claimEventApplication(state.runId(), "STOP", claimed -> {
                if (claimed) for (String command : definition.endCommands()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replace(command, 0, "0m"));
                }
            });
        }
    }

    private void updateBossbar(ActiveEvent event, long now) {
        if (event.bar() == null) return;
        long total = event.endMs() - event.startMs();
        long remaining = Math.max(0, event.endMs() - now);
        event.bar().progress((float) Math.max(0, Math.min(1, (double) remaining / total)));
        event.bar().name(plugin.parseComponent(event.definition().bossbarText()
                .replace("{remaining}", formatDuration(remaining / 1000))));
    }

    private void hide(BossBar bar) {
        if (bar != null) Bukkit.getOnlinePlayers().forEach(player -> player.hideBossBar(bar));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!enabled) return;
        for (ActiveEvent activeEvent : active.values()) {
            if (activeEvent.bar() != null) event.getPlayer().showBossBar(activeEvent.bar());
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String @NotNull [] args) {
        if (!enabled) {
            send(sender, "<#FFC43B>El módulo vEvents está desactivado.");
            return true;
        }
        if (args.length == 0) {
            send(sender, "<yellow>Uso: /vevent <start|stop|list|reload> [id]");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "start" -> {
                if (args.length < 2) send(sender, "<yellow>Uso: /vevent start <id>");
                else send(sender, startEvent(args[1]) ? "<green>Evento iniciado." : "<red>Evento inexistente o activo.");
            }
            case "stop" -> {
                if (args.length < 2) send(sender, "<yellow>Uso: /vevent stop <id>");
                else send(sender, stopEvent(args[1], true) ? "<green>Evento detenido." : "<red>Evento inexistente o inactivo.");
            }
            case "list" -> list(sender);
            case "reload" -> send(sender, reloadDefinitions()
                    ? "<green>vEvents recargado." : "<red>Configuración inválida; se conserva la anterior.");
            default -> send(sender, "<red>Subcomando desconocido. Usa /vevent list.");
        }
        return true;
    }

    private void list(CommandSender sender) {
        send(sender, "<#FFC43B>Eventos configurados:");
        for (EventDef definition : definitions.values()) {
            ActiveEvent running = active.get(definition.id());
            String status;
            if (running != null) {
                status = "<green>[ACTIVE] <white>" + formatDuration(Math.max(0,
                        (running.endMs() - System.currentTimeMillis()) / 1000));
            } else {
                Long next = nextActivation.get(definition.id());
                status = "<gray>next: " + (next == null ? "unknown"
                        : DATE_FORMAT.format(Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault())));
            }
            send(sender, "<yellow>" + definition.id() + " <gray>(" + definition.name() + ") — " + status);
        }
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(plugin.parseComponent("%prefix%" + message));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String @NotNull [] args) {
        if (args.length == 1) return List.of("list", "start", "stop", "reload").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 2 && (args[0].equalsIgnoreCase("start") || args[0].equalsIgnoreCase("stop"))) {
            return definitions.keySet().stream().filter(value -> value.startsWith(args[1])).toList();
        }
        return Collections.emptyList();
    }

    String placeholder(String params) {
        if (params.startsWith("remaining_seconds_")) {
            ActiveEvent event = active.get(params.substring("remaining_seconds_".length()));
            return event == null ? "0" : String.valueOf(Math.max(0,
                    (event.endMs() - System.currentTimeMillis()) / 1000));
        }
        if (params.startsWith("remaining_")) {
            ActiveEvent event = active.get(params.substring("remaining_".length()));
            return event == null ? "0" : formatDuration(Math.max(0,
                    (event.endMs() - System.currentTimeMillis()) / 1000));
        }
        if (params.startsWith("active_")) {
            return String.valueOf(active.containsKey(params.substring("active_".length())));
        }
        if (params.startsWith("next_")) {
            Long next = nextActivation.get(params.substring("next_".length()));
            if (next == null) return "unknown";
            long seconds = (next - System.currentTimeMillis()) / 1000;
            return seconds <= 0 ? "soon" : "en " + formatDuration(seconds);
        }
        return null;
    }

    public static String formatDuration(long seconds) {
        if (seconds <= 0) return "0m";
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long remainder = seconds % 60;
        StringBuilder result = new StringBuilder();
        if (hours > 0) result.append(hours).append("h ");
        if (minutes > 0) result.append(minutes).append("m ");
        if (remainder > 0 && hours == 0) result.append(remainder).append("s");
        return result.toString().trim();
    }

    private static String replace(String command, int seconds, String duration) {
        return command.replace("{duration}", String.valueOf(seconds))
                .replace("{duration_human}", duration).replace("{remaining}", duration);
    }

    private static BossBar.Color color(String value) {
        try {
            return BossBar.Color.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return BossBar.Color.WHITE;
        }
    }

    private static BossBar.Overlay overlay(String value) {
        return switch (value.toUpperCase(Locale.ROOT)) {
            case "SEGMENTED_6" -> BossBar.Overlay.NOTCHED_6;
            case "SEGMENTED_10" -> BossBar.Overlay.NOTCHED_10;
            case "SEGMENTED_12" -> BossBar.Overlay.NOTCHED_12;
            case "SEGMENTED_20" -> BossBar.Overlay.NOTCHED_20;
            default -> BossBar.Overlay.PROGRESS;
        };
    }

    private File eventsDirectory() {
        return new File(plugin.getDataFolder(), "events");
    }

    private File dataFile() {
        return new File(eventsDirectory(), "data.yml");
    }

    private record EventDef(String id, String name, boolean enabled, LocalTime windowStart,
                            LocalTime windowEnd, int intervalDaysMin, int intervalDaysMax,
                            int durationMinMinutes, int durationMaxMinutes, List<String> startCommands,
                            List<String> endCommands, boolean bossbarEnabled, String bossbarText,
                            String bossbarColor, String bossbarStyle) {
    }

    private record ActiveEvent(EventDef definition, UUID runId, long startMs, long endMs, BossBar bar) {
    }
}
