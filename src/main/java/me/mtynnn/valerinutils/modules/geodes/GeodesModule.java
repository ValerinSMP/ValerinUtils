package me.mtynnn.valerinutils.modules.geodes;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Random;

public class GeodesModule implements Module, CommandExecutor {

    private final ValerinUtils plugin;
    private final Random random = new Random();

    public GeodesModule(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "geodes";
    }

    @Override
    public void enable() {
        if (plugin.getCommand("geode") != null) {
            plugin.getCommand("geode").setExecutor(this);
        }
    }

    @Override
    public void disable() {
        if (plugin.getCommand("geode") != null) {
            plugin.getCommand("geode").setExecutor(null);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!sender.hasPermission("valerinutils.geodes.admin")) {
            sender.sendMessage(plugin.getMessage("no-permission"));
            return true;
        }

        // Usage: /geode open <type> <player>
        if (args.length < 3 || !args[0].equalsIgnoreCase("open")) {
            sender.sendMessage("§cUsage: /geode open <type> <player>");
            return true;
        }

        String type = args[1].toLowerCase();
        Player target = Bukkit.getPlayer(args[2]);

        if (target == null) {
            sender.sendMessage("§cPlayer not found.");
            return true;
        }

        FileConfiguration cfg = plugin.getConfigManager().getConfig("geodes");
        if (cfg == null || !cfg.getBoolean("enabled", true)) {
            sender.sendMessage("§cGeodes module is disabled.");
            return true;
        }

        ConfigurationSection geodeCfg = cfg.getConfigurationSection("geodes." + type);
        if (geodeCfg == null) {
            sender.sendMessage("§cGeode type '" + type + "' not found in config.");
            return true;
        }

        openGeode(target, geodeCfg);
        return true;
    }

    private void openGeode(Player player, ConfigurationSection config) {
        FileConfiguration rootCfg = plugin.getConfigManager().getConfig("geodes");
        double roll = random.nextDouble() * 100.0;
        List<Map<?, ?>> rewards = config.getMapList("rewards");
        boolean rewarded = false;

        // 1. Play "Opening" sounds
        if (rootCfg != null && rootCfg.getBoolean("sounds.open.enabled", true)) {
            List<Map<?, ?>> openSounds = rootCfg.getMapList("sounds.open.list");
            for (Map<?, ?> sMap : openSounds) {
                playSound(player, sMap);
            }
        }

        // Spawn particles
        player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, player.getLocation().add(0, 1.2, 0), 15, 0.3, 0.3, 0.3,
                0.1);

        for (Map<?, ?> reward : rewards) {
            Object chanceObj = reward.get("chance");
            double chance = (chanceObj instanceof Number) ? ((Number) chanceObj).doubleValue() : 0.0;

            if (roll <= chance) {
                // Win!
                String cmd = (String) reward.get("command");
                String msg = (String) reward.get("message");

                if (cmd != null && !cmd.isEmpty()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
                }

                if (msg != null && !msg.isEmpty()) {
                    player.sendMessage(plugin.translateColors(msg));
                }

                // Play Success sound
                if (rootCfg != null && rootCfg.getBoolean("sounds.success.enabled", true)) {
                    playSound(player, rootCfg.getConfigurationSection("sounds.success"));
                }

                rewarded = true;
                break; // Stop at the first hit (sequential logic)
            }
        }

        if (!rewarded) {
            String failMsg = config.getString("fail-message");
            if (failMsg != null && !failMsg.isEmpty()) {
                player.sendActionBar(plugin.parseComponent(failMsg));
            }

            // Play Fail sound
            if (rootCfg != null && rootCfg.getBoolean("sounds.fail.enabled", true)) {
                playSound(player, rootCfg.getConfigurationSection("sounds.fail"));
            }
        }
    }

    private void playSound(Player player, Map<?, ?> data) {
        if (data == null)
            return;
        try {
            String name = (String) data.get("sound");
            Object volObj = data.get("vol");
            float vol = (volObj instanceof Number) ? ((Number) volObj).floatValue() : 1.0f;
            Object pitchObj = data.get("pitch");
            float pitch = (pitchObj instanceof Number) ? ((Number) pitchObj).floatValue() : 1.0f;

            if (name != null) {
                player.playSound(player.getLocation(), Sound.valueOf(name), vol, pitch);
            }
        } catch (Exception ignored) {
        }
    }

    private void playSound(Player player, ConfigurationSection section) {
        if (section == null)
            return;
        try {
            String name = section.getString("sound");
            float vol = (float) section.getDouble("vol", 1.0);
            float pitch = (float) section.getDouble("pitch", 1.0);
            if (name != null) {
                player.playSound(player.getLocation(), Sound.valueOf(name), vol, pitch);
            }
        } catch (Exception ignored) {
        }
    }
}
