package me.mtynnn.valerinutils.core;

import me.mtynnn.valerinutils.ValerinUtils;
import me.qKing12.RoyaleEconomy.API.Events.CoinsAddToPurseEvent;
import me.qKing12.RoyaleEconomy.MultiCurrency.MultiCurrencyHandler;
import me.qKing12.RoyaleEconomy.API.Currency;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EarningsTracker implements Listener {

    private final ValerinUtils plugin;
    private final Map<UUID, Double> lastKnownShards = new ConcurrentHashMap<>();
    private Currency shardsCurrency;
    private BukkitTask pollTask;

    public EarningsTracker(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    public void start() {
        try {
            shardsCurrency = MultiCurrencyHandler.findCurrencyById("shards");
            if (shardsCurrency == null) {
                plugin.getLogger().warning("[EarningsTracker] Moneda 'shards' no encontrada en RoyaleEconomy.");
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[EarningsTracker] RoyaleEconomy no disponible, shards no rastreados.");
            shardsCurrency = null;
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Init lastKnownShards for already-online players (reload scenario)
        for (Player p : Bukkit.getOnlinePlayers()) {
            initShards(p);
        }

        // Poll every 60 ticks (3s)
        pollTask = Bukkit.getScheduler().runTaskTimer(plugin, this::pollAllShards, 60L, 60L);
    }

    public void stop() {
        if (pollTask != null) {
            pollTask.cancel();
            pollTask = null;
        }
        // Final flush for all online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            flushShards(p);
        }
        lastKnownShards.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCoinsAdd(CoinsAddToPurseEvent event) {
        double added = event.getAddedCoins();
        if (added <= 0) return;
        Player player = Bukkit.getPlayerExact(event.getPlayerString());
        if (player == null) return;
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null) data.addMoneyEarned(added);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        initShards(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        flushShards(event.getPlayer());
        lastKnownShards.remove(event.getPlayer().getUniqueId());
    }

    private void initShards(Player player) {
        if (shardsCurrency == null) return;
        lastKnownShards.put(player.getUniqueId(), shardsCurrency.getAmount(player.getUniqueId().toString()));
    }

    private void pollAllShards() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            flushShards(p);
        }
    }

    private void flushShards(Player player) {
        if (shardsCurrency == null) return;
        UUID uuid = player.getUniqueId();
        double current = shardsCurrency.getAmount(player.getUniqueId().toString());
        double last = lastKnownShards.getOrDefault(uuid, current);
        double delta = current - last;
        if (delta > 0) {
            PlayerData data = plugin.getPlayerData(uuid);
            if (data != null) data.addShardsEarned(delta);
        }
        lastKnownShards.put(uuid, current);
    }
}
