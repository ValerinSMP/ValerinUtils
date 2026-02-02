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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DeathMessagesModule implements Module, Listener, CommandExecutor {

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
    }

    @Override
    public void disable() {
        org.bukkit.event.HandlerList.unregisterAll(this);
        if (plugin.getCommand("deathmsg") != null) {
            plugin.getCommand("deathmsg").setExecutor(null);
        }
    }

    private FileConfiguration getConfig() {
        return plugin.getConfigManager().getConfig("deathmessages");
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

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
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
}
