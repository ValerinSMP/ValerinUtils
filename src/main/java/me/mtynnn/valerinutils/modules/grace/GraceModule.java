package me.mtynnn.valerinutils.modules.grace;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.BaseModule;
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
        sender.sendMessage(comp(msg("messages.usage", "%prefix%<gray>Uso: <yellow>/grace remove <jugador>")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return List.of("remove");
        if (args.length == 2 && "remove".equalsIgnoreCase(args[0])) {
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
