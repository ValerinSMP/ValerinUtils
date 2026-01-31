package me.mtynnn.valerinutils.modules.tiktok;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TikTokModule extends Command implements Module {

    private final ValerinUtils plugin;
    private final File dataFile;
    private List<String> claimedPlayers;

    public TikTokModule(ValerinUtils plugin) {
        super(plugin.getConfig().getString("tiktok.command-name", "tiktok"));
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "tiktok_data.yml");
        this.claimedPlayers = new ArrayList<>();
        loadData();

        // Settings for command
        this.setDescription("Reclama recompensa única");
        this.setUsage("/" + getName());
        this.setPermission("valerinutils.tiktok"); // Optional permission
    }

    @Override
    public String getId() {
        return "tiktok";
    }

    @Override
    public void enable() {
        if (!plugin.getConfig().getBoolean("tiktok.enabled", false)) {
            return;
        }

        try {
            Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());
            commandMap.register(plugin.getName(), this);
            plugin.getLogger().info("Registered dynamic command: /" + getName());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register dynamic command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void disable() {
        saveData();
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!plugin.getConfig().getBoolean("tiktok.enabled", false)) {
            sender.sendMessage("§cEste comando está deshabilitado actualmente.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessage("messages.only-players"));
            return true;
        }

        Player player = (Player) sender;

        if (hasClaimed(player.getUniqueId())) {
            player.sendMessage(plugin.getMessage("reward-already-claimed"));
            return true;
        }

        // Chequear espacio en inventario
        int requiredSlots = plugin.getConfig().getInt("tiktok.required-slots", 1);
        int freeSlots = getFreeSlots(player);

        if (freeSlots < requiredSlots) {
            String msg = plugin.getMessage("reward-inventory-full");
            msg = msg.replace("%slots%", String.valueOf(requiredSlots));
            player.sendMessage(msg);
            return true;
        }

        executionReward(player);

        // Send success message (support list)
        List<String> messages = plugin.getMessageList("reward-success");
        for (String msg : messages) {
            player.sendMessage(msg);
        }

        return true;
    }

    private int getFreeSlots(Player player) {
        int free = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (item == null || item.getType() == Material.AIR) {
                free++;
            }
        }
        return free;
    }

    private void loadData() {
        if (!dataFile.exists()) {
            saveData();
        }
        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        claimedPlayers = data.getStringList("claimed");
        if (claimedPlayers == null) {
            claimedPlayers = new ArrayList<>();
        }
    }

    private void saveData() {
        FileConfiguration data = YamlConfiguration.loadConfiguration(dataFile);
        data.set("claimed", claimedPlayers);
        try {
            data.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().warning("No se pudo guardar tiktok_data.yml");
        }
    }

    public boolean hasClaimed(UUID uuid) {
        return claimedPlayers.contains(uuid.toString());
    }

    public void setClaimed(UUID uuid) {
        if (!hasClaimed(uuid)) {
            claimedPlayers.add(uuid.toString());
            saveData();
        }
    }

    public void executionReward(Player player) {
        List<String> commands = plugin.getConfig().getStringList("tiktok.commands");
        for (String cmd : commands) {
            String commandToRun = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun);
        }

        // Effects and Sounds
        playRewardSound(player);
        playRewardEffect(player);

        setClaimed(player.getUniqueId());
    }

    private void playRewardSound(Player player) {
        if (!plugin.getConfig().getBoolean("tiktok.sound.enabled", false))
            return;

        String soundName = plugin.getConfig().getString("tiktok.sound.name", "ENTITY_PLAYER_LEVELUP");
        float vol = (float) plugin.getConfig().getDouble("tiktok.sound.volume", 1.0);
        float pitch = (float) plugin.getConfig().getDouble("tiktok.sound.pitch", 1.0);

        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, vol, pitch);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid reward sound: " + soundName);
        }
    }

    private void playRewardEffect(Player player) {
        if (!plugin.getConfig().getBoolean("tiktok.effect.enabled", false))
            return;

        String effectName = plugin.getConfig().getString("tiktok.effect.type", "TOTEM");
        int count = plugin.getConfig().getInt("tiktok.effect.count", 50);

        try {
            Particle particle = Particle.valueOf(effectName);
            player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), count, 0.5, 0.5, 0.5, 0.1);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid reward effect: " + effectName);
        }
    }
}
