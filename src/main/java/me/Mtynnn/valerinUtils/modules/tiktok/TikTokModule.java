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
public class TikTokModule extends Command implements Module {

    private final ValerinUtils plugin;

    public TikTokModule(ValerinUtils plugin) {
        // Since we need config to get command name, effectively we need to lazy load or
        // load here
        // We'll trust ConfigManager is loaded before modules init.
        super("tiktok"); // Fallback name, can't easily change registration dynamically without reload,
                         // relying on default or migrated
        // Actually, let's try to get it from config if available

        this.plugin = plugin;

        // Note: super() must be first. If we wanted dynamic name from config, we'd need
        // to fetch config in super call.
        // But java doesn't allow instance access there.
        // For v2.0 optimization, let's hardcode default "tiktok" or accept that init
        // order implies "tiktok"
        // If we want dynamic command name, we should have used a separate
        // CommandExecutor registration or aliases.
        // For now, retaining structure but utilizing config manager for runtime
        // settings.

        FileConfiguration cfg = plugin.getConfigManager().getConfig("tiktok");
        if (cfg != null) {
            String name = cfg.getString("tiktok.command-name");
            if (name != null && !name.equalsIgnoreCase("tiktok")) {
                // Changing the name of this object won't change registration easily if we
                // already called super("tiktok")
                // In a perfect refactor we'd use setValid() or something but Command name is
                // final-ish.
                // We'll stick to "tiktok" as base usage, update aliases/usage from settings.
            }
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

        try {
            Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            CommandMap commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());

            // To support custom command name from config, we *could* rename here or
            // register under alias
            String cmdName = cfg.getString("command-name", "tiktok");
            if (!cmdName.equalsIgnoreCase(getName())) {
                if (!this.setName(cmdName)) {
                    // If setName fails (it returns boolean), register as alias?
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
        // Nothing to save locally, data is in DB
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

        if (data != null && data.isTikTokClaimed()) {
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
