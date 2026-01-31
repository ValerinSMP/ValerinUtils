package me.mtynnn.valerinutils.modules.joinquit;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Comparator;

@SuppressWarnings("deprecation") // Legacy Bukkit API (setJoinMessage, Sound.valueOf) required for compatibility
public class JoinQuitModule implements Module, Listener {

    private final ValerinUtils plugin;
    private int uniquePlayerCount;
    private final Random random = new Random();

    private static final String UNIQUE_PLAYERS_KEY = "unique_players";

    public JoinQuitModule(ValerinUtils plugin) {
        this.plugin = plugin;
        loadData();
    }

    @Override
    public String getId() {
        return "joinquit";
    }

    private FileConfiguration getConfig() {
        return plugin.getConfigManager().getConfig("joinquit");
    }

    @Override
    public void enable() {
        // Solo registrar eventos si LuckPerms está disponible
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
            plugin.getLogger().info("[JoinQuit] LuckPerms hooked, events enabled");
        } else {
            plugin.getLogger().warning("[JoinQuit] LuckPerms not found, module will not work properly");
        }
    }

    private void loadData() {
        // Load from database
        int defaultCount = Bukkit.getOfflinePlayers().length;
        uniquePlayerCount = plugin.getDatabaseManager().getServerInt(UNIQUE_PLAYERS_KEY, defaultCount);
    }

    private void saveData() {
        plugin.getDatabaseManager().setServerInt(UNIQUE_PLAYERS_KEY, uniquePlayerCount);
    }

    private boolean isWorldDisabled(String worldName) {
        // En el nuevo YAML, la ruta es root (ej: "disabled-worlds")
        // En config v1 era "joinquit.disabled-worlds".
        // ConfigManager debería haber migrado "modules.joinquit" -> root de
        // joinquit.yml
        // PERO JoinQuitModule structure en config antigua era:
        // modules:
        // joinquit:
        // join: ...
        // quit: ...

        FileConfiguration cfg = getConfig();
        if (cfg == null)
            return false;

        List<String> disabled = cfg.getStringList("disabled-worlds");
        return disabled.contains(worldName);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (plugin.isDebug()) {
            plugin.getLogger().info("[Debug] Join Event - Metadata keys for " + player.getName() + ": "
                    + player.getMetadata("vanished").toString());
        }

        if (isWorldDisabled(player.getWorld().getName()))
            return;

        // Check for vanish (Robust check)
        boolean isVanished = player.hasMetadata("vanished") || player.hasMetadata("CMI_Vanish");

        if (isVanished) {
            if (plugin.isDebug())
                plugin.getLogger().info("Player is vanished (metadata valid), silencing join.");
            event.setJoinMessage(null);
            return;
        }

        event.setJoinMessage(null); // Deshabilitar mensaje por defecto

        FileConfiguration config = getConfig();
        if (config == null || !config.getBoolean("enabled", true))
            return;

        // Incrementar contador si es nuevo (o confiar en hasPlayedBefore)
        boolean firstJoin = !player.hasPlayedBefore();

        if (firstJoin) {
            uniquePlayerCount++;
            saveData();
            handleFirstJoin(player, config.getConfigurationSection("first-join"));
        } else {
            handleJoin(player, config.getConfigurationSection("join"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isWorldDisabled(player.getWorld().getName()))
            return;

        // Check for vanish
        boolean isVanished = player.hasMetadata("vanished") || player.hasMetadata("CMI_Vanish");

        if (isVanished) {
            event.setQuitMessage(null);
            return;
        }

        event.setQuitMessage(null);

        FileConfiguration config = getConfig();
        if (config != null) {
            handleQuit(player, config.getConfigurationSection("quit"));
        }
    }

    private void handleFirstJoin(Player player, ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", false))
            return;

        // Broadcast Message
        List<String> messages = section.getStringList("broadcast-message");
        for (String line : messages) {
            broadcast(processPlaceholders(player, line));
        }

        // Title
        if (section.getBoolean("title.enabled", false)) {
            Component title = processPlaceholders(player, section.getString("title.title", ""));
            Component subtitle = processPlaceholders(player, section.getString("title.subtitle", ""));

            Title.Times times = Title.Times.times(Duration.ofMillis(section.getInt("title.fade-in", 10) * 50L),
                    Duration.ofMillis(section.getInt("title.stay", 60) * 50L),
                    Duration.ofMillis(section.getInt("title.fade-out", 10) * 50L));

            player.showTitle(Title.title(title, subtitle, times));
        }

        // Sound
        playSound(player, section.getConfigurationSection("sound"));
    }

    private void handleJoin(Player player, ConfigurationSection section) {
        if (section == null)
            return;

        // 1. Group Logic High Priority
        ConfigurationSection groups = section.getConfigurationSection("groups");
        if (groups != null) {
            Set<String> keys = groups.getKeys(false);
            // Ordenar por prioridad (mayor a menor)
            List<String> sortedGroups = keys.stream()
                    .sorted(Comparator.comparingInt(k -> -groups.getInt(k + ".priority", 0)))
                    .collect(Collectors.toList());

            for (String groupKey : sortedGroups) {
                // Verificar permiso:
                // 1. "permission" custom en config
                // 2. Default: group.<nombre> (común en LuckPerms)
                String permNode = groups.getString(groupKey + ".permission");
                if (permNode == null || permNode.isEmpty()) {
                    permNode = "group." + groupKey;
                }

                boolean has = hasGroup(player, groupKey) || player.hasPermission(permNode);

                if (plugin.isDebug()) {
                    plugin.getLogger().info("[Debug] Checking group: " + groupKey + " | Perm: " + permNode + " | Has: "
                            + has + " | Priority: " + groups.getInt(groupKey + ".priority"));
                }

                if (has) {
                    ConfigurationSection groupSection = groups.getConfigurationSection(groupKey);

                    // Broadcast
                    List<String> msgs = groupSection.getStringList("messages");
                    if (!msgs.isEmpty()) {
                        String msg = msgs.get(random.nextInt(msgs.size()));
                        broadcast(processPlaceholders(player, msg));
                    }

                    // Sound
                    playSound(player, groupSection.getConfigurationSection("sound"));

                    // Title
                    if (groupSection.getBoolean("title.enabled", false)) {
                        showTitle(player, groupSection.getConfigurationSection("title"));
                    }

                    // MOTD
                    if (groupSection.getBoolean("motd.enabled", false)) {
                        showMotd(player, groupSection.getConfigurationSection("motd"));
                    } else if (section.getBoolean("motd.enabled")) {
                        showMotd(player, section.getConfigurationSection("motd"));
                    }

                    return; // Detener aquí, grupo encontrado.
                }
            }
        }

        // 2. Default Logic
        // Broadcast Message (Random)
        List<String> messages = section.getStringList("messages");
        if (!messages.isEmpty()) {
            String msg = messages.get(random.nextInt(messages.size()));
            broadcast(processPlaceholders(player, msg));
        }

        // Title
        showTitle(player, section.getConfigurationSection("title"));

        // Sound
        playSound(player, section.getConfigurationSection("sound"));

        // MOTD
        showMotd(player, section.getConfigurationSection("motd"));
    }

    private void showTitle(Player player, ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", false))
            return;

        Component title = processPlaceholders(player, section.getString("title", ""));
        Component subtitle = processPlaceholders(player, section.getString("subtitle", ""));

        Title.Times times = Title.Times.times(Duration.ofMillis(section.getInt("fade-in", 10) * 50L),
                Duration.ofMillis(section.getInt("stay", 40) * 50L),
                Duration.ofMillis(section.getInt("fade-out", 10) * 50L));

        player.showTitle(Title.title(title, subtitle, times));
    }

    private void showMotd(Player player, ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", false))
            return;
        for (String line : section.getStringList("lines")) {
            player.sendMessage(processPlaceholders(player, line));
        }
    }

    private void handleQuit(Player player, ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled", false))
            return;

        // Broadcast Message (Random)
        List<String> messages = section.getStringList("messages");
        if (!messages.isEmpty()) {
            String msg = messages.get(random.nextInt(messages.size()));
            broadcast(processPlaceholders(player, msg));
        }

        // Sound
        if (section.getBoolean("sound.enabled")) {
            String soundName = section.getString("sound.name", "BLOCK_WOODEN_DOOR_CLOSE");
            float vol = (float) section.getDouble("sound.volume", 1.0);
            float pitch = (float) section.getDouble("sound.pitch", 1.0);
            try {
                Sound sound = Sound.valueOf(soundName);
                // Play to world at location
                player.getWorld().playSound(player.getLocation(), sound, vol, pitch);
            } catch (Exception ignored) {
            }
        }
    }

    private void playSound(Player player, ConfigurationSection section) {
        if (section == null || !section.getBoolean("enabled"))
            return;
        String soundName = section.getString("name");
        float vol = (float) section.getDouble("volume", 1.0);
        float pitch = (float) section.getDouble("pitch", 1.0);
        try {
            Sound sound = Sound.valueOf(soundName);
            // Play to ALL online players
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), sound, vol, pitch);
            }
        } catch (Exception ignored) {
            plugin.getLogger().warning("Sonido invalido: " + soundName);
        }
    }

    private Component processPlaceholders(Player player, String message) {

        // Calcular jugadores online excluyendo vanish
        long onlineCount = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.hasMetadata("vanished")) {
                onlineCount++;
            }
        }

        String processed = message.replace("%player%", player.getName()).replace("%player_name%", player.getName()) // Alias
                                                                                                                    // para
                                                                                                                    // %player%
                .replace("%player_number%", String.valueOf(uniquePlayerCount))
                .replace("%online%", String.valueOf(onlineCount))
                .replace("%max%", String.valueOf(Bukkit.getMaxPlayers()));

        // PAPI support if available
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            processed = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, processed);
        }

        return plugin.parseComponent(processed);
    }

    private void broadcast(Component message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
        Bukkit.getConsoleSender().sendMessage(message);
    }

    public int getUniquePlayerCount() {
        return uniquePlayerCount;
    }

    // Logic extracted to LuckPermsHelper
    private boolean hasGroup(Player player, String groupName) {
        // return me.mtynnn.valerinutils.utils.LuckPermsHelper.hasGroup(player,
        // groupName, plugin);
        // Temporarily disabled due to external dependencies check or simpler logic
        return false;
    }

    @Override
    public void disable() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        // unique-players data logic remains in flat file joinquit_data.yml for now
        // as migrating it to global player DB implies tracking every single offline
        // player ever joined?
        // SQLite is capable, but "unique-players" is a global counter.
        // It's fine to keep it in a small YAML or a settings table.
        // For now, leaving it as is (optimized enough, only 1 loaded int).
        saveData();
    }
}
