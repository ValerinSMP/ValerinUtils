package me.mtynnn.valerinutils.modules.tiktok;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import me.mtynnn.valerinutils.core.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.List;

@SuppressWarnings("deprecation") // Sound.valueOf is deprecated but required for compatibility
public class TikTokModule extends Command implements Module, org.bukkit.command.CommandExecutor {

    private final ValerinUtils plugin;
    private boolean isDynamic = false;

    public TikTokModule(ValerinUtils plugin) {
        // ... (constructor remains similar, just cleanup comments if needed)
        super("tiktok");
        this.plugin = plugin;

        FileConfiguration cfg = plugin.getConfigManager().getConfig("tiktok");
        if (cfg != null) {
            String name = cfg.getString("tiktok.command-name");
            // ... (name checking logic)
        }

        this.setDescription("Reclama recompensa única");
        this.setUsage("/tiktok");
        this.setPermission("valerinutils.tiktok");
    }

    @Override
    public String getId() {
        return "tiktok";
    }

    private FileConfiguration getConfig() {
        return plugin.getConfigManager().getConfig("tiktok");
    }

    @Override
    public void enable() {
        FileConfiguration cfg = getConfig();
        if (cfg == null || !cfg.getBoolean("enabled", false)) {
            return;
        }

        String configuredName = cfg.getString("command-name", "tiktok");
        if (configuredName.equalsIgnoreCase("tiktok")) {
            // Static registration (Preferred)
            isDynamic = false;
            org.bukkit.command.PluginCommand cmd = plugin.getCommand("tiktok");
            if (cmd != null) {
                cmd.setExecutor(this);
            }
            plugin.getLogger().info("TikTok command registered via plugin.yml");
            return;
        }

        // Dynamic Registration (Fallback for custom names)
        isDynamic = true;
        try {
            Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());

            if (!configuredName.equalsIgnoreCase(getName())) {
                if (!this.setName(configuredName)) {
                    // ignore
                }
            }

            commandMap.register(plugin.getName(), this);
            plugin.getLogger().info("Registered dynamic command: /" + getName());
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to register dynamic command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void disable() {
        if (!isDynamic) {
            org.bukkit.command.PluginCommand cmd = plugin.getCommand("tiktok");
            if (cmd != null) {
                cmd.setExecutor(null);
            }
            return;
        }

        try {
            Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());

            // Get knownCommands map via reflection to remove entries
            // SimpleCommandMap (CraftBukkit) uses 'knownCommands'
            Field knownCommandsField;
            try {
                knownCommandsField = commandMap.getClass().getDeclaredField("knownCommands");
                knownCommandsField.setAccessible(true);
            } catch (NoSuchFieldException e) {
                return;
            }

            @SuppressWarnings("unchecked")
            java.util.Map<String, Command> knownCommands = (java.util.Map<String, Command>) knownCommandsField
                    .get(commandMap);

            // Remove main command
            knownCommands.remove(getName());
            knownCommands.remove(plugin.getName().toLowerCase() + ":" + getName());

            // Remove aliases
            for (String alias : getAliases()) {
                knownCommands.remove(alias);
                knownCommands.remove(plugin.getName().toLowerCase() + ":" + alias);
            }

            // Standard unregister
            this.unregister(commandMap);

            plugin.getLogger().info("Unregistered dynamic command: /" + getName());

        } catch (Exception e) {
            plugin.getLogger().warning("Failed to unregister command /" + getName() + ": " + e.getMessage());
        }
    }

    // Bridge for CommandExecutor
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        return execute(sender, label, args);
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        FileConfiguration cfg = getConfig();
        if (cfg == null || !cfg.getBoolean("enabled", false)) {
            sender.sendMessage("§cEste comando está deshabilitado actualmente.");
            return true;
        }

        if (!(sender instanceof Player)) {
            // Using legacy getMessage for now, or adapt to manual message
            sender.sendMessage(getMessage("only-players"));
            return true;
        }

        Player player = (Player) sender;
        PlayerData data = plugin.getPlayerData(player.getUniqueId());

        if (data == null) {
            player.sendMessage(plugin.translateColors("&cError cargando tus datos. Por favor intenta reloguear."));
            return true;
        }

        if (data.isTikTokClaimed()) {
            player.sendMessage(getMessage("reward-already-claimed"));
            return true;
        }

        // Chequear espacio en inventario
        int requiredSlots = cfg.getInt("required-slots", 1);
        int freeSlots = getFreeSlots(player);

        if (freeSlots < requiredSlots) {
            String msg = getMessage("reward-inventory-full");
            msg = msg.replace("%slots%", String.valueOf(requiredSlots));
            player.sendMessage(msg);
            return true;
        }

        executionReward(player);

        // Send success message (support list)
        List<String> messages = getMessageList("reward-success");
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

    public void executionReward(Player player) {
        FileConfiguration cfg = getConfig();
        if (cfg == null)
            return;

        List<String> commands = cfg.getStringList("commands");
        for (String cmd : commands) {
            String commandToRun = cmd.replace("%player%", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), commandToRun);
        }

        // Effects and Sounds
        playRewardSound(player);
        playRewardEffect(player);

        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data != null) {
            data.setTikTokClaimed(true); // Helper saves async eventually
        }
    }

    private void playRewardSound(Player player) {
        FileConfiguration cfg = getConfig();
        if (cfg == null || !cfg.getBoolean("sound.enabled", false))
            return;

        String soundName = cfg.getString("sound.name", "ENTITY_PLAYER_LEVELUP");
        float vol = (float) cfg.getDouble("sound.volume", 1.0);
        float pitch = (float) cfg.getDouble("sound.pitch", 1.0);

        try {
            Sound sound = Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, vol, pitch);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid reward sound: " + soundName);
        }
    }

    private void playRewardEffect(Player player) {
        FileConfiguration cfg = getConfig();
        if (cfg == null || !cfg.getBoolean("effect.enabled", false))
            return;

        String effectName = cfg.getString("effect.type", "TOTEM");
        int count = cfg.getInt("effect.count", 50);

        try {
            Particle particle = Particle.valueOf(effectName);
            player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), count, 0.5, 0.5, 0.5, 0.1);
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid reward effect: " + effectName);
            plugin.getLogger().warning("Invalid reward effect: " + effectName);
        }
    }

    private String getMessage(String key) {
        FileConfiguration cfg = getConfig();
        if (cfg == null)
            return "";
        String val = cfg.getString("messages." + key);
        if (val == null)
            return "Message missing: " + key;

        // Use ValerinUtils translate helper but with our local string
        // Need to manually handle prefix relative to Global settings if we want
        // consistency?
        // TikTok config doesn't have a prefix defined usually.
        // Let's assume raw or simple color usage.
        return plugin.translateColors(val);
    }

    private List<String> getMessageList(String key) {
        FileConfiguration cfg = getConfig();
        if (cfg == null)
            return List.of();

        if (cfg.isList("messages." + key)) {
            List<String> raw = cfg.getStringList("messages." + key);
            return raw.stream().map(plugin::translateColors).toList();
        }
        return List.of(getMessage(key));
    }
}
