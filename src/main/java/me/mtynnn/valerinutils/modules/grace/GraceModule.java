package me.mtynnn.valerinutils.modules.grace;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.BaseModule;
import me.mtynnn.valerinutils.core.CommandHelpRenderer;
import me.mtynnn.valerinutils.core.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class GraceModule extends BaseModule implements Listener, CommandExecutor, TabCompleter {

    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private BukkitTask tickTask;
    private int tickCount = 0;

    private long graceTicks;
    private Set<String> protectedWorlds;

    public GraceModule(ValerinUtils plugin) {
        super(plugin);
    }

    @Override
    public String getId() {
        return "grace";
    }

    @Override
    public Set<String> getCommandNames() {
        return Set.of("grace");
    }

    @Override
    protected void onEnableModule() {
        if (!isEnabledInConfig()) return;
        loadConfig();
        registerListener(this);
        registerCommand("grace", this, this);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        for (Player p : Bukkit.getOnlinePlayers()) {
            initPlayer(p);
        }
    }

    @Override
    protected void onDisableModule() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (Map.Entry<UUID, BossBar> entry : bossBars.entrySet()) {
            Player p = Bukkit.getPlayer(entry.getKey());
            if (p != null) entry.getValue().removePlayer(p);
        }
        bossBars.clear();
    }

    private void loadConfig() {
        var cfg = cfg();
        graceTicks = cfg != null ? cfg.getLong("grace-playtime-ticks", 864000L) : 864000L;
        protectedWorlds = cfg != null
                ? cfg.getStringList("protected-worlds").stream().map(String::toLowerCase).collect(Collectors.toSet())
                : Set.of("world", "world_nether", "world_the_end");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        initPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeBossBar(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event);
        if (attacker == null) return;

        boolean inProtectedWorld = protectedWorlds.contains(victim.getWorld().getName().toLowerCase(Locale.ROOT));

        // Victim has grace → cancel damage in protected world
        if (inProtectedWorld && isGraceActive(victim)) {
            event.setCancelled(true);
            attacker.sendMessage(comp(msg("messages.target-has-grace",
                    "%prefix%<yellow>Este jugador tiene inmunidad PvP temporal.")));
            return;
        }

        // Attacker has grace → handle attack in protected world
        boolean attackerInProtected = protectedWorlds.contains(attacker.getWorld().getName().toLowerCase(Locale.ROOT));
        if (attackerInProtected && isGraceActive(attacker)) {
            PlayerData data = plugin.getPlayerData(attacker.getUniqueId());
            if (data == null) return;
            event.setCancelled(true);
            if (!data.isGracePvpWarned()) {
                data.setGracePvpWarned(true);
                attacker.sendMessage(comp(msg("messages.grace-warning",
                        "%prefix%<red>⚠ Si atacas a otro jugador nuevamente, <bold>perderás tu inmunidad PvP</bold>.")));
            } else {
                removeGrace(attacker, data);
                attacker.sendMessage(comp(msg("messages.grace-removed-pvp",
                        "%prefix%<red>Tu inmunidad PvP fue eliminada por atacar a otro jugador.")));
                // Allow the damage on grace removal — recalculate and apply
                event.setCancelled(false);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 2 && "remove".equalsIgnoreCase(args[0])) {
            if (!hasPermission(sender, "valerinutils.grace.admin")) {
                sender.sendMessage(comp(msg("messages.no-permission", "%prefix%<red>No tienes permiso.")));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                if (plugin.routeRemoteCommand(args[1], "grace " + String.join(" ", args))) {
                    sender.sendMessage(comp(msg("messages.network-forwarded",
                            "%prefix%<green>Accion enviada a <white>%player% <green>en su servidor.")
                            .replace("%player%", args[1])));
                    return true;
                }
                sender.sendMessage(comp(msg("messages.player-not-found", "%prefix%<red>Jugador no encontrado.")));
                return true;
            }
            PlayerData data = plugin.getPlayerData(target.getUniqueId());
            if (data == null || data.getGraceExpiresAt() == 0 || !isGraceActive(target)) {
                sender.sendMessage(comp(msg("messages.no-grace",
                        "%prefix%<yellow>%player% no tiene inmunidad activa.").replace("%player%", target.getName())));
                return true;
            }
            removeGrace(target, data);
            target.sendMessage(comp(msg("messages.grace-removed-admin",
                    "%prefix%<red>Un administrador eliminó tu inmunidad PvP.")));
            sender.sendMessage(comp(msg("messages.grace-removed-admin-sender",
                    "%prefix%<green>Inmunidad de <white>%player% <green>eliminada.")
                    .replace("%player%", target.getName())));
            return true;
        }

        if (args.length >= 3 && "add".equalsIgnoreCase(args[0])) {
            if (!hasPermission(sender, "valerinutils.grace.admin")) {
                sender.sendMessage(comp(msg("messages.no-permission", "%prefix%<red>No tienes permiso.")));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                if (plugin.routeRemoteCommand(args[1], "grace " + String.join(" ", args))) {
                    sender.sendMessage(comp(msg("messages.network-forwarded",
                            "%prefix%<green>Accion enviada a <white>%player% <green>en su servidor.")
                            .replace("%player%", args[1])));
                    return true;
                }
                sender.sendMessage(comp(msg("messages.player-not-found", "%prefix%<red>Jugador no encontrado.")));
                return true;
            }
            double hours;
            try {
                hours = Double.parseDouble(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(comp(msg("messages.usage-add", "%prefix%<gray>Uso: <yellow>/grace add <jugador> <horas>")));
                return true;
            }
            if (hours <= 0) {
                sender.sendMessage(comp(msg("messages.usage-add", "%prefix%<gray>Uso: <yellow>/grace add <jugador> <horas>")));
                return true;
            }
            PlayerData data = plugin.getPlayerData(target.getUniqueId());
            if (data == null) {
                sender.sendMessage(comp(msg("messages.player-not-found", "%prefix%<red>Jugador no encontrado.")));
                return true;
            }
            long addTicks = (long) (hours * 3600L * 20L);
            long playtime = target.getStatistic(Statistic.PLAY_ONE_MINUTE);
            long base = isGraceActive(target) ? data.getGraceExpiresAt() : playtime;
            data.setGraceExpiresAt(base + addTicks);
            data.setGracePvpWarned(false);
            showBossBar(target);
            target.sendMessage(comp(msg("messages.grace-added",
                    "%prefix%<green>Se te otorgaron <white>%hours%h <green>de inmunidad PvP.")
                    .replace("%hours%", String.valueOf(hours))));
            sender.sendMessage(comp(msg("messages.grace-added-sender",
                    "%prefix%<green>Se otorgaron <white>%hours%h <green>de inmunidad a <white>%player%<green>.")
                    .replace("%hours%", String.valueOf(hours))
                    .replace("%player%", target.getName())));
            return true;
        }

        if (args.length >= 1 && "check".equalsIgnoreCase(args[0])) {
            Player target;
            if (args.length >= 2) {
                if (!hasPermission(sender, "valerinutils.grace.admin") && !(sender instanceof Player self
                        && self.getName().equalsIgnoreCase(args[1]))) {
                    sender.sendMessage(comp(msg("messages.no-permission", "%prefix%<red>No tienes permiso.")));
                    return true;
                }
                target = Bukkit.getPlayerExact(args[1]);
            } else if (sender instanceof Player self) {
                target = self;
            } else {
                sender.sendMessage(comp(msg("messages.usage-check", "%prefix%<gray>Uso: <yellow>/grace check <jugador>")));
                return true;
            }
            if (target == null) {
                sender.sendMessage(comp(msg("messages.player-not-found", "%prefix%<red>Jugador no encontrado.")));
                return true;
            }
            if (!isGraceActive(target)) {
                sender.sendMessage(comp(msg("messages.no-grace",
                        "%prefix%<yellow>%player% no tiene inmunidad activa.").replace("%player%", target.getName())));
                return true;
            }
            PlayerData data = plugin.getPlayerData(target.getUniqueId());
            long remaining = data.getGraceExpiresAt() - target.getStatistic(Statistic.PLAY_ONE_MINUTE);
            sender.sendMessage(comp(msg("messages.grace-check",
                    "%prefix%<white>%player% <gray>tiene <green>%time% <gray>de inmunidad restante.")
                    .replace("%player%", target.getName())
                    .replace("%time%", formatTicks(Math.max(0, remaining)))));
            return true;
        }

        if (args.length >= 1 && "list".equalsIgnoreCase(args[0])) {
            if (!hasPermission(sender, "valerinutils.grace.admin")) {
                sender.sendMessage(comp(msg("messages.no-permission", "%prefix%<red>No tienes permiso.")));
                return true;
            }
            List<String> active = Bukkit.getOnlinePlayers().stream()
                    .filter(this::isGraceActive)
                    .map(p -> {
                        PlayerData data = plugin.getPlayerData(p.getUniqueId());
                        long remaining = data.getGraceExpiresAt() - p.getStatistic(Statistic.PLAY_ONE_MINUTE);
                        return p.getName() + " (" + formatTicks(Math.max(0, remaining)) + ")";
                    })
                    .collect(Collectors.toList());
            if (active.isEmpty()) {
                sender.sendMessage(comp(msg("messages.list-empty", "%prefix%<gray>Nadie tiene inmunidad activa ahora.")));
            } else {
                sender.sendMessage(comp(msg("messages.list-header", "%prefix%<gray>Jugadores con inmunidad activa:")));
                for (String line : active) {
                    sender.sendMessage(comp("<gray> - <white>" + line));
                }
            }
            return true;
        }

        sendGraceHelp(sender);
        return true;
    }

    private void sendGraceHelp(CommandSender sender) {
        CommandHelpRenderer.send(sender, "Inmunidad PvP", List.of(
                CommandHelpRenderer.Entry.of(
                        "/grace check [jugador]", "Consultar la inmunidad PvP", "/grace check "),
                CommandHelpRenderer.Entry.of(
                        "/grace add (jugador) (horas)", "Otorgar inmunidad", "/grace add "),
                CommandHelpRenderer.Entry.of(
                        "/grace remove (jugador)", "Retirar inmunidad", "/grace remove "),
                CommandHelpRenderer.Entry.of(
                        "/grace list", "Listar inmunidades activas", "/grace list")));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String q = args[0].toLowerCase(Locale.ROOT);
            return List.of("remove", "add", "check", "list").stream()
                    .filter(s -> s.startsWith(q))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && Set.of("remove", "add", "check").contains(args[0].toLowerCase(Locale.ROOT))) {
            String q = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(q))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    // ---- Internal ----

    private void initPlayer(Player player) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) return;

        // Grant grace only to brand new players (graceExpiresAt == 0 means never set)
        if (data.getGraceExpiresAt() == 0) {
            long playtime = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            if (playtime == 0) {
                data.setGraceExpiresAt(graceTicks);
                player.sendMessage(comp(msg("messages.grace-granted",
                        "%prefix%<green>Bienvenido! Tienes <white>12h <green>de inmunidad PvP mientras exploras el servidor.")));
            } else {
                // Player has playtime but no grace set → mark as ineligible
                data.setGraceExpiresAt(-1L);
            }
        }

        if (isGraceActive(player)) {
            showBossBar(player);
        }
    }

    private void tick() {
        tickCount++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData data = plugin.getPlayerData(p.getUniqueId());
            if (data == null || !isGraceActive(p)) {
                removeBossBar(p);
                continue;
            }
            long remaining = data.getGraceExpiresAt() - p.getStatistic(Statistic.PLAY_ONE_MINUTE);
            if (remaining <= 0) {
                removeGrace(p, data);
                p.sendMessage(comp(msg("messages.grace-expired",
                        "%prefix%<yellow>Tu inmunidad PvP ha expirado. Ya puedes ser atacado.")));
            } else if (shouldUpdateBar(remaining)) {
                updateBossBar(p, remaining);
            }
        }
    }

    // Throttle bossbar text refresh based on remaining time:
    // >1h → every 60s | >1min → every 10s | <1min → every second
    private boolean shouldUpdateBar(long remaining) {
        if (remaining > 72000L) return tickCount % 1200 == 0; // every 60s
        if (remaining > 1200L) return tickCount % 200 == 0;   // every 10s
        return true;                                            // every second
    }

    private boolean isGraceActive(Player player) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null || data.getGraceExpiresAt() <= 0) return false;
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE) < data.getGraceExpiresAt();
    }

    private void removeGrace(Player player, PlayerData data) {
        data.setGraceExpiresAt(-1L);
        removeBossBar(player);
    }

    private void showBossBar(Player player) {
        if (bossBars.containsKey(player.getUniqueId())) return;
        BossBar bar = Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SOLID);
        bar.addPlayer(player);
        bossBars.put(player.getUniqueId(), bar);
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null) {
            long remaining = data.getGraceExpiresAt() - player.getStatistic(Statistic.PLAY_ONE_MINUTE);
            updateBossBar(player, Math.max(0, remaining));
        }
    }

    private void updateBossBar(Player player, long remainingTicks) {
        BossBar bar = bossBars.get(player.getUniqueId());
        if (bar == null) {
            showBossBar(player);
            return;
        }
        String time = formatTicks(remainingTicks);
        bar.setTitle(msg("bossbar.title", "⚔ Inmunidad PvP: %time%").replace("%time%", time));
        bar.setProgress(Math.min(1.0, Math.max(0.0, (double) remainingTicks / graceTicks)));
    }

    private void removeBossBar(Player player) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) bar.removePlayer(player);
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player p) return p;
        if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }

    private String formatTicks(long ticks) {
        long s = ticks / 20;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format("%dh %02dm", h, m);
        if (m > 0) return String.format("%dm %02ds", m, sec);
        return String.format("%ds", sec);
    }

    private boolean hasPermission(CommandSender sender, String node) {
        return sender.hasPermission("valerinutils.admin") || sender.hasPermission(node);
    }
}
