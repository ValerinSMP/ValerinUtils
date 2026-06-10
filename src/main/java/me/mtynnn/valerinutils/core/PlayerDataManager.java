package me.mtynnn.valerinutils.core;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerDataManager implements Listener {

    private final ValerinUtils plugin;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    public PlayerData get(UUID uuid) {
        return cache.get(uuid);
    }

    @EventHandler
    public void onAsyncLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        String name = event.getName();
        try {
            PlayerData data = loadFromDB(uuid);
            if (data == null) {
                data = new PlayerData(uuid, name);
            } else {
                data.setName(name);
            }
            cache.put(uuid, data);
        } catch (Exception e) {
            plugin.getLogger().severe("Error loading data for " + name);
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        PlayerData data = cache.get(uuid);
        if (data == null || !data.isDirty()) {
            cache.remove(uuid);
            return;
        }
        CompletableFuture.runAsync(() -> saveToDB(data))
                .thenRun(() -> cache.remove(uuid))
                .exceptionally(ex -> {
                    plugin.getLogger().severe("Error al guardar datos de " + data.getName()
                            + ", mantenidos en cache para reintento");
                    ex.printStackTrace();
                    return null;
                });
    }

    public void saveAllAndClear() {
        for (PlayerData data : cache.values()) {
            if (data != null && data.isDirty()) {
                saveToDB(data);
            }
        }
        cache.clear();
    }

    public void reloadOnlinePlayers() {
        if (Bukkit.getOnlinePlayers().isEmpty()) return;
        plugin.getLogger().info("Loading data for " + Bukkit.getOnlinePlayers().size() + " online players...");
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                UUID uuid = p.getUniqueId();
                PlayerData data = loadFromDB(uuid);
                if (data == null) {
                    data = new PlayerData(uuid, p.getName());
                } else {
                    data.setName(p.getName());
                }
                cache.put(uuid, data);
            } catch (SQLException e) {
                plugin.getLogger().severe("Failed to load data for " + p.getName() + " during reload.");
                e.printStackTrace();
            }
        }
    }

    public void removeStaleEntries() {
        int before = cache.size();
        cache.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        int removed = before - cache.size();
        if (removed > 0) {
            plugin.debug("core", "Cleaned " + removed + " stale PlayerData entries from cache");
        }
    }

    private PlayerData loadFromDB(UUID uuid) throws SQLException {
        String sql = "SELECT * FROM player_data WHERE uuid = ?";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    PlayerData pd = new PlayerData(uuid, rs.getString("name"));
                    pd.setKills(rs.getInt("kills"));
                    pd.setDeaths(rs.getInt("deaths"));
                    pd.setDailyRewardsCount(rs.getInt("daily_kills"));
                    pd.setLastDailyReset(rs.getLong("last_daily_reset"));
                    pd.setMenuDisabled(rs.getBoolean("menu_disabled"));
                    pd.setRoyalPayDisabled(rs.getBoolean("royal_pay_disabled"));
                    pd.setDeathMessagesDisabled(rs.getBoolean("death_messages_disabled"));
                    pd.setStarterKitReceived(rs.getBoolean("starter_kit_received"));
                    pd.setNickname(rs.getString("nickname"));
                    pd.setDirty(false);
                    return pd;
                }
            }
        }
        return null;
    }

    private void saveToDB(PlayerData data) {
        if (data == null) return;
        String sql = "INSERT INTO player_data (uuid, name, kills, deaths, daily_kills, last_daily_reset, menu_disabled, "
                + "royal_pay_disabled, death_messages_disabled, starter_kit_received, nickname) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT(uuid) DO UPDATE SET "
                + "name=excluded.name, kills=excluded.kills, "
                + "deaths=excluded.deaths, daily_kills=excluded.daily_kills, "
                + "last_daily_reset=excluded.last_daily_reset, "
                + "menu_disabled=excluded.menu_disabled, "
                + "royal_pay_disabled=excluded.royal_pay_disabled, "
                + "death_messages_disabled=excluded.death_messages_disabled, "
                + "starter_kit_received=excluded.starter_kit_received, "
                + "nickname=excluded.nickname";
        try (PreparedStatement ps = plugin.getDatabaseManager().getConnection().prepareStatement(sql)) {
            ps.setString(1, data.getUuid().toString());
            ps.setString(2, data.getName());
            ps.setInt(3, data.getKills());
            ps.setInt(4, data.getDeaths());
            ps.setInt(5, data.getDailyRewardsCount());
            ps.setLong(6, data.getLastDailyReset());
            ps.setBoolean(7, data.isMenuDisabled());
            ps.setBoolean(8, data.isRoyalPayDisabled());
            ps.setBoolean(9, data.isDeathMessagesDisabled());
            ps.setBoolean(10, data.isStarterKitReceived());
            ps.setString(11, data.getNickname());
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not save data for " + data.getName());
            e.printStackTrace();
        }
    }
}
