package me.mtynnn.valerinutils.modules.deathmessages;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import me.mtynnn.valerinutils.core.PlayerData;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DeathMessagesModule implements Module, Listener, CommandExecutor, TabCompleter {

    private final ValerinUtils plugin;

    public DeathMessagesModule(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "deathmessages";
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // Registrar comando si está definido en plugin.yml
        if (plugin.getCommand("deathmsg") != null) {
            plugin.getCommand("deathmsg").setExecutor(this);
        }
        if (plugin.getCommand("vuspawn") != null) {
            plugin.getCommand("vuspawn").setExecutor(this);
            plugin.getCommand("vuspawn").setTabCompleter(this);
        }
    }

    @Override
    public void disable() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        if (plugin.getCommand("deathmsg") != null) {
            plugin.getCommand("deathmsg").setExecutor(null);
        }
        if (plugin.getCommand("vuspawn") != null) {
            plugin.getCommand("vuspawn").setExecutor(null);
            plugin.getCommand("vuspawn").setTabCompleter(null);
        }
    }

    private FileConfiguration getConfig() {
        return plugin.getConfigManager().getConfig("deathmessages");
    }

    @EventHandler
    public void onFirstJoinSpawn(PlayerJoinEvent event) {
        FileConfiguration config = getConfig();
        if (config == null || !config.getBoolean("enabled", true)) {
            return;
        }

        if (event.getPlayer().hasPlayedBefore()) {
            return;
        }

        ConfigurationSection section = config.getConfigurationSection("spawn.first-join");
        if (section == null || !section.getBoolean("enabled", false)) {
            return;
        }

        Location loc = parseLocation(section.getString("location"));
        if (loc == null) {
            return;
        }

        int delay = Math.max(0, section.getInt("delay-ticks", 1));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                event.getPlayer().teleport(loc);
            }
        }, delay);
    }

    @EventHandler
    public void onSpawnOnDeath(PlayerRespawnEvent event) {
        FileConfiguration config = getConfig();
        if (config == null || !config.getBoolean("enabled", true)) {
            return;
        }

        ConfigurationSection section = config.getConfigurationSection("spawn.on-death");
        if (section == null || !section.getBoolean("enabled", false)) {
            return;
        }

        Location loc = parseLocation(section.getString("location"));
        if (loc == null) {
            return;
        }

        event.setRespawnLocation(loc);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        FileConfiguration config = getConfig();
        if (config == null || !config.getBoolean("enabled", true)) {
            return;
        }

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Silenciar mensaje vanilla
        event.setDeathMessage(null);

        String path = (killer != null && !killer.equals(victim)) ? "messages.killed.module" : "messages.died.module";
        ConfigurationSection section = config.getConfigurationSection(path);

        if (section == null)
            return;

        // Preparar placeholders
        String victimName = victim.getName();
        String killerName = (killer != null) ? killer.getName() : "N/A";

        // Enviar mensajes a todos los jugadores
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (shouldReceiveMessage(online)) {
                sendCustomMessage(online, section, victimName, killerName);
            }
        }

        // Consola siempre recibe
        sendCustomMessageToConsole(section, victimName, killerName);
    }

    private boolean shouldReceiveMessage(Player player) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        return data == null || !data.isDeathMessagesDisabled();
    }

    private void sendCustomMessage(Player player, ConfigurationSection section, String victim, String killer) {
        // Chat
        if (section.getBoolean("chat.enabled")) {
            List<String> messages = section.getStringList("chat.message");
            for (String msg : messages) {
                player.sendMessage(format(msg, victim, killer));
            }
        }

        // Actionbar
        if (section.getBoolean("actionbar.enabled")) {
            List<String> messages = section.getStringList("actionbar.message");
            for (String msg : messages) {
                if (!msg.isEmpty()) {
                    player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(format(msg, victim, killer)));
                }
            }
        }

        // Title
        if (section.getBoolean("title.enabled")) {
            String title = "";
            String subtitle = "";

            // Get first line of list if available
            List<String> tList = section.getStringList("title.message");
            if (!tList.isEmpty())
                title = tList.get(0);

            List<String> sList = section.getStringList("subtitle.message");
            if (!sList.isEmpty())
                subtitle = sList.get(0);

            if (!title.isEmpty() || !subtitle.isEmpty()) {
                player.sendTitle(format(title, victim, killer), format(subtitle, victim, killer), 10, 70, 20);
            }
        }
    }

    private void sendCustomMessageToConsole(ConfigurationSection section, String victim, String killer) {
        if (section.getBoolean("chat.enabled")) {
            List<String> messages = section.getStringList("chat.message");
            for (String msg : messages) {
                Bukkit.getConsoleSender().sendMessage(format(msg, victim, killer));
            }
        }
    }

    private String format(String text, String victim, String killer) {
        return plugin.translateColors(text)
                .replace("%victim%", victim)
                .replace("%attacker%", killer);
    }

    private Location parseLocation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }

        String[] parts = raw.trim().split("[;,]");
        if (parts.length < 4) {
            return null;
        }

        String worldName = parts[0].trim();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getLogger().warning("[DeathMessages] Mundo no encontrado para spawn: " + worldName);
            return null;
        }

        try {
            double x = Double.parseDouble(parts[1].trim());
            double y = Double.parseDouble(parts[2].trim());
            double z = Double.parseDouble(parts[3].trim());
            float yaw = parts.length >= 5 ? Float.parseFloat(parts[4].trim()) : 0f;
            float pitch = parts.length >= 6 ? Float.parseFloat(parts[5].trim()) : 0f;
            return new Location(world, x, y, z, yaw, pitch);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("[DeathMessages] Location inválida en config: " + raw);
            return null;
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("vuspawn")) {
            return handleSpawnCommand(sender, args);
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("valerinutils.admin")) {
                sendConfigMessage(sender, "messages.no_permission.module");
                return true;
            }
            plugin.getConfigManager().reloadConfigs();
            sendConfigMessage(sender, "messages.reload_success.module");
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.translateColors("&cSolo jugadores pueden hacer toggle."));
            return true;
        }

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null) {
            sender.sendMessage(plugin.translateColors("&cError cargando tus datos."));
            return true;
        }

        boolean newState = !data.isDeathMessagesDisabled();
        data.setDeathMessagesDisabled(newState);

        // newState = true (disabled) -> toggle_disabled
        // newState = false (enabled) -> toggle_enabled

        // Wait, logic check:
        // isDeathMessagesDisabled() = true means DISABLED.
        // If I was false (enabled), newState becomes true (disabled).
        // So newState == true means I just DISABLED them.

        String key = newState ? "messages.toggle_disabled.module" : "messages.toggle_enabled.module";
        sendConfigMessage(player, key);

        return true;
    }

    private boolean handleSpawnCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("valerinutils.admin")) {
            sender.sendMessage(plugin.translateColors("%prefix%&cNo tienes permiso."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(plugin.translateColors(
                    "%prefix%&7Uso: &e/vuspawn set <firstjoin|death|both> &7| &e/vuspawn off <firstjoin|death|both> &7| &e/vuspawn info"));
            return true;
        }

        String sub = args[0].toLowerCase();
        FileConfiguration cfg = getConfig();
        if (cfg == null) {
            sender.sendMessage(plugin.translateColors("%prefix%&cConfig no cargada."));
            return true;
        }

        switch (sub) {
            case "info" -> {
                sender.sendMessage(plugin.translateColors("%prefix%&eSpawn settings (deathmessages):"));
                sender.sendMessage(plugin.translateColors(" &7first-join: &f"
                        + cfg.getBoolean("spawn.first-join.enabled", false) + " &8| &7loc: &f"
                        + cfg.getString("spawn.first-join.location", "N/A")));
                sender.sendMessage(plugin.translateColors(" &7on-death: &f"
                        + cfg.getBoolean("spawn.on-death.enabled", false) + " &8| &7loc: &f"
                        + cfg.getString("spawn.on-death.location", "N/A")));
                return true;
            }
            case "off" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.translateColors("%prefix%&cUso: /vuspawn off <firstjoin|death|both>"));
                    return true;
                }
                String target = args[1].toLowerCase();
                if (target.equals("firstjoin") || target.equals("both")) {
                    cfg.set("spawn.first-join.enabled", false);
                }
                if (target.equals("death") || target.equals("both")) {
                    cfg.set("spawn.on-death.enabled", false);
                }
                plugin.getConfigManager().saveConfig("deathmessages");
                sender.sendMessage(plugin.translateColors("%prefix%&aSpawn desactivado para: &e" + target));
                return true;
            }
            case "set" -> {
                if (args.length < 2) {
                    sender.sendMessage(plugin.translateColors("%prefix%&cUso: /vuspawn set <firstjoin|death|both>"));
                    return true;
                }
                String target = args[1].toLowerCase();

                String locString;
                if (args.length >= 3) {
                    locString = args[2];
                } else {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(plugin.translateColors(
                                "%prefix%&cDesde consola debes indicar location: world;x;y;z;yaw;pitch"));
                        return true;
                    }
                    locString = locationToString(player.getLocation());
                }

                if (parseLocation(locString) == null) {
                    sender.sendMessage(plugin.translateColors("%prefix%&cLocation inválida: &f" + locString));
                    return true;
                }

                if (target.equals("firstjoin") || target.equals("both")) {
                    cfg.set("spawn.first-join.location", locString);
                    cfg.set("spawn.first-join.enabled", true);
                }
                if (target.equals("death") || target.equals("both")) {
                    cfg.set("spawn.on-death.location", locString);
                    cfg.set("spawn.on-death.enabled", true);
                }

                plugin.getConfigManager().saveConfig("deathmessages");
                sender.sendMessage(plugin.translateColors("%prefix%&aSpawn seteado (&e" + target + "&a): &f" + locString));
                return true;
            }
            default -> {
                sender.sendMessage(plugin.translateColors(
                        "%prefix%&7Uso: &e/vuspawn set <firstjoin|death|both> &7| &e/vuspawn off <firstjoin|death|both> &7| &e/vuspawn info"));
                return true;
            }
        }
    }

    private String locationToString(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return "world;0;0;0;0;0";
        }
        return loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ() + ";"
                + loc.getYaw() + ";" + loc.getPitch();
    }

    private void sendConfigMessage(CommandSender sender, String path) {
        FileConfiguration config = getConfig();
        if (config == null)
            return;
        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null)
            return;

        if (section.getBoolean("chat.enabled")) {
            List<String> messages = section.getStringList("chat.message");
            for (String msg : messages) {
                sender.sendMessage(plugin.translateColors(msg));
            }
        }
        // Actionbar/Title support for command messages could be added similarly if
        // needed
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        if (!command.getName().equalsIgnoreCase("vuspawn")) {
            return Collections.emptyList();
        }

        if (!sender.hasPermission("valerinutils.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subs = List.of("set", "off", "info");
            String partial = args[0].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String s : subs) {
                if (s.startsWith(partial)) {
                    out.add(s);
                }
            }
            return out;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("off"))) {
            List<String> targets = List.of("firstjoin", "death", "both");
            String partial = args[1].toLowerCase();
            List<String> out = new ArrayList<>();
            for (String t : targets) {
                if (t.startsWith(partial)) {
                    out.add(t);
                }
            }
            return out;
        }

        return Collections.emptyList();
    }
}
