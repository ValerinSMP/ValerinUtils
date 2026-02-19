package me.mtynnn.valerinutils.modules.pvpmina;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public class PvpMinaModule implements Module, Listener, CommandExecutor {

    private final ValerinUtils plugin;
    private final Random random = new Random();

    private String currentMode;
    private Instant nextRotationTime;
    private BukkitTask rotationTask;

    // Use Bukkit BossBar instead of Adventure to avoid compilation issues
    private BossBar activeBossBar;

    // Cache
    private Set<Material> currentBlockedItems = new HashSet<>();
    private long intervalMinutes;
    private String targetWorldName;

    public PvpMinaModule(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "pvpmina";
    }

    @Override
    public void enable() {
        FileConfiguration cfg = getConfig();
        if (cfg == null || !cfg.getBoolean("enabled", true))
            return;

        reloadConfigCache();

        // Initial setup
        if (currentMode == null) {
            rotateMode(true); // Force first rotation
        } else {
            // Restore capabilities if reloaded (re-read blocked items)
            loadModeSettings(currentMode);
        }

        // Register listeners and command
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PluginCommand cmd = plugin.getCommand("pvpmina");
        if (cmd != null) {
            cmd.setExecutor(this);
        }

        // Start task
        startTask();
    }

    @Override
    public void disable() {
        if (rotationTask != null && !rotationTask.isCancelled()) {
            rotationTask.cancel();
        }
        org.bukkit.event.HandlerList.unregisterAll(this);
        PluginCommand cmd = plugin.getCommand("pvpmina");
        if (cmd != null) {
            cmd.setExecutor(null);
        }

        // Hide bossbar
        if (activeBossBar != null) {
            activeBossBar.removeAll();
        }
    }

    private FileConfiguration getConfig() {
        return plugin.getConfigManager().getConfig("pvpmina");
    }

    private void reloadConfigCache() {
        FileConfiguration cfg = getConfig();
        this.targetWorldName = cfg.getString("world", "world_minapvp");
        this.intervalMinutes = cfg.getLong("interval-minutes", 120);

        // Initialize BossBar style
        try {
            BarColor color = BarColor.valueOf(cfg.getString("messages.bossbar-color", "RED").toUpperCase());
            BarStyle style = BarStyle.valueOf(cfg.getString("messages.bossbar-style", "SOLID").toUpperCase());

            if (activeBossBar == null) {
                activeBossBar = Bukkit.createBossBar("", color, style);
            } else {
                activeBossBar.setColor(color);
                activeBossBar.setStyle(style);
            }
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("[PvpMina] Invalid BossBar color/style in config. Using defaults.");
            if (activeBossBar == null) {
                activeBossBar = Bukkit.createBossBar("", BarColor.RED, BarStyle.SOLID);
            }
        }
    }

    private void startTask() {
        rotationTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Instant now = Instant.now();

            // 1. Check Rotation
            if (nextRotationTime == null || now.isAfter(nextRotationTime)) {
                rotateMode(false);
            }

            // 2. Update BossBar
            updateBossBar();

        }, 20L, 20L); // Every second
    }

    private void rotateMode(boolean firstRun) {
        FileConfiguration cfg = getConfig();
        ConfigurationSection modesSec = cfg.getConfigurationSection("modes");
        if (modesSec == null)
            return;

        List<String> modes = new ArrayList<>(modesSec.getKeys(false));
        if (modes.isEmpty())
            return;

        // Run End Commands for current mode
        if (currentMode != null && !firstRun) {
            List<String> endCmds = modesSec.getStringList(currentMode + ".commands-on-end");
            dispatchConsoleCommands(endCmds);
        }

        // Select new mode (random)
        String newMode;
        if (modes.size() > 1 && currentMode != null) {
            do {
                newMode = modes.get(random.nextInt(modes.size()));
            } while (newMode.equals(currentMode)); // Avoid repetition if possible
        } else {
            newMode = modes.get(random.nextInt(modes.size()));
        }

        currentMode = newMode;
        nextRotationTime = Instant.now().plus(Duration.ofMinutes(intervalMinutes));

        // Load settings and Run Start Commands
        loadModeSettings(newMode);

        List<String> startCmds = modesSec.getStringList(newMode + ".commands-on-start");
        dispatchConsoleCommands(startCmds);

        // Broadcast change
        if (!firstRun) {
            List<String> messages = cfg.getStringList("messages.mode-change");
            if (messages.isEmpty()) {
                String singleMsg = cfg.getString("messages.mode-change");
                if (singleMsg != null)
                    messages.add(singleMsg);
            }

            String soundName = cfg.getString("messages.change-sound", "ENTITY_WITHER_SPAWN");
            org.bukkit.Sound sound;
            try {
                sound = org.bukkit.Sound.valueOf(soundName.toUpperCase());
            } catch (IllegalArgumentException e) {
                sound = org.bukkit.Sound.ENTITY_WITHER_SPAWN;
            }

            String displayName = getModeDisplayName(newMode);

            for (Player p : Bukkit.getOnlinePlayers()) {
                for (String line : messages) {
                    String processed = line.replace("%mode%", displayName)
                            .replace("{nombredelmodo}", displayName);
                    p.sendMessage(parseComponent(processed));
                }
                p.playSound(p.getLocation(), sound, 1f, 1f);
            }
        }
    }

    private void loadModeSettings(String mode) {
        FileConfiguration cfg = getConfig();
        currentBlockedItems.clear();

        List<String> blockedNames = cfg.getStringList("modes." + mode + ".blocked-items");
        for (String name : blockedNames) {
            try {
                currentBlockedItems.add(Material.valueOf(name.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[PvpMina] Invalid material in blocked-items: " + name);
            }
        }
    }

    private String getModeDisplayName(String mode) {
        FileConfiguration cfg = getConfig();
        return cfg.getString("modes." + mode + ".display-name", mode);
    }

    private void dispatchConsoleCommands(List<String> cmds) {
        if (cmds == null || cmds.isEmpty())
            return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            CommandSender console = Bukkit.getConsoleSender();
            for (String cmd : cmds) {
                Bukkit.dispatchCommand(console, cmd);
            }
        });
    }

    private void updateBossBar() {
        if (activeBossBar == null || nextRotationTime == null)
            return;

        World world = Bukkit.getWorld(targetWorldName);
        if (world == null)
            return;

        // Calculate progress
        long totalSeconds = intervalMinutes * 60;
        long remainingSeconds = Duration.between(Instant.now(), nextRotationTime).getSeconds();

        if (remainingSeconds < 0)
            remainingSeconds = 0;

        float progress = (float) remainingSeconds / totalSeconds;
        activeBossBar.setProgress(Math.max(0f, Math.min(1f, progress)));

        // Update Title
        String timeStr = String.format("%02d:%02d:%02d",
                remainingSeconds / 3600,
                (remainingSeconds % 3600) / 60,
                remainingSeconds % 60);

        String titleRaw = getConfig().getString("messages.bossbar-title", "Mode: %mode% (%time_left%)")
                .replace("%mode%", getModeDisplayName(currentMode))
                .replace("%time_left%", timeStr);

        // Convert Component to Legacy String for Bukkit BossBar
        Component titleComp = parseComponent(titleRaw);
        String legacyTitle = LegacyComponentSerializer.legacySection().serialize(titleComp);
        activeBossBar.setTitle(legacyTitle);

        // Manage viewers (Only players in target world)
        org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "pvpmina_bossbar_hidden");
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean isHidden = p.getPersistentDataContainer().has(key, org.bukkit.persistence.PersistentDataType.BYTE);

            if (!isHidden && p.getWorld().getName().equals(targetWorldName)) {
                if (!activeBossBar.getPlayers().contains(p)) {
                    activeBossBar.addPlayer(p);
                }
            } else {
                if (activeBossBar.getPlayers().contains(p)) {
                    activeBossBar.removePlayer(p);
                }
            }
        }
    }

    // Helper to parse MiniMessage with Legacy fallback
    private Component parseComponent(String message) {
        if (message == null)
            return Component.empty();
        return plugin.parseComponent(message);
    }

    // --- Listeners ---

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        checkBlockedItem(event.getPlayer(), event.getItem(), event);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        // Melee Attack (Player)
        if (event.getDamager() instanceof Player) {
            Player p = (Player) event.getDamager();
            // Check Main Hand
            checkBlockedItem(p, p.getInventory().getItemInMainHand(), event);
            // Check Off Hand incase of MACE/Shield/etc
            checkBlockedItem(p, p.getInventory().getItemInOffHand(), event);
        }

        // Projectile Attack (Trident, Arrow, etc.)
        else if (event.getDamager() instanceof org.bukkit.entity.Projectile) {
            org.bukkit.entity.Projectile proj = (org.bukkit.entity.Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                Player p = (Player) proj.getShooter();

                // Check if the projectile itself corresponds to a blocked item
                if (proj instanceof org.bukkit.entity.Trident) {
                    if (currentBlockedItems.contains(Material.TRIDENT)) {
                        event.setCancelled(true);
                        p.sendMessage(parseComponent(getConfig().getString("messages.item-blocked", "&cBlocked!")
                                .replace("%mode%", getModeDisplayName(currentMode))));
                    }
                } else if (proj instanceof org.bukkit.entity.Arrow || proj instanceof org.bukkit.entity.SpectralArrow) {
                    // Check for Bow/Crossbow/Shooter item held
                    if (currentBlockedItems.contains(Material.BOW) || currentBlockedItems.contains(Material.CROSSBOW)) {
                        event.setCancelled(true);
                        p.sendMessage(parseComponent(getConfig().getString("messages.item-blocked", "&cBlocked!")
                                .replace("%mode%", getModeDisplayName(currentMode))));
                    }
                }
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onProjectileLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player) {
            Player p = (Player) event.getEntity().getShooter();
            // Check for Trident throw or Bow shoot
            org.bukkit.inventory.ItemStack item = p.getInventory().getItemInMainHand();
            checkBlockedItem(p, item, event);

            item = p.getInventory().getItemInOffHand();
            checkBlockedItem(p, item, event);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onRiptide(org.bukkit.event.player.PlayerRiptideEvent event) {
        checkBlockedItem(event.getPlayer(), event.getItem(), null);
    }

    // Block Elytra Gliding if Elytra is blocked
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onGlide(org.bukkit.event.entity.EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player && event.isGliding()) {
            Player p = (Player) event.getEntity();
            if (p.getWorld().getName().equals(targetWorldName)) {
                if (currentBlockedItems.contains(Material.ELYTRA)) {
                    if (!p.hasPermission("valerinutils.pvpmina.bypass")) {
                        event.setCancelled(true);
                        p.sendMessage(parseComponent(getConfig().getString("messages.item-blocked", "&cBlocked!")
                                .replace("%mode%", getModeDisplayName(currentMode))));
                        p.setCooldown(Material.ELYTRA, 100); // Cooldown to stop spam
                    }
                }
            }
        }
    }

    // Cooldown logic for switching items
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onItemHeld(org.bukkit.event.player.PlayerItemHeldEvent event) {
        Player p = event.getPlayer();
        if (!p.getWorld().getName().equals(targetWorldName))
            return;

        org.bukkit.inventory.ItemStack newItem = p.getInventory().getItem(event.getNewSlot());
        if (newItem != null && currentBlockedItems.contains(newItem.getType())) {
            if (!p.hasPermission("valerinutils.pvpmina.bypass")) {
                // Apply cooldown to visually indicate block and prevent right-click usage
                p.setCooldown(newItem.getType(), 2400); // 2 minutes cooldown
                p.sendMessage(parseComponent(getConfig().getString("messages.item-blocked", "&cBlocked!")
                        .replace("%mode%", getModeDisplayName(currentMode))));
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onHandSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        Player p = event.getPlayer();
        if (!p.getWorld().getName().equals(targetWorldName))
            return;

        org.bukkit.inventory.ItemStack newItem = event.getOffHandItem(); // Item moving to offhand? or main?
        // Check both just to be safe
        if (newItem != null && currentBlockedItems.contains(newItem.getType())) {
            if (!p.hasPermission("valerinutils.pvpmina.bypass")) {
                event.setCancelled(true);
                p.setCooldown(newItem.getType(), 200);
                p.sendMessage(parseComponent(getConfig().getString("messages.item-blocked", "&cBlocked!")
                        .replace("%mode%", getModeDisplayName(currentMode))));
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player p = event.getPlayer();

        if (p.getWorld().getName().equals(targetWorldName)) {
            // Entered world: will be added in next tick update
        } else {
            // Left world: hide immediately
            if (activeBossBar != null)
                activeBossBar.removePlayer(p);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Clean up if needed
    }

    private void checkBlockedItem(Player p, org.bukkit.inventory.ItemStack item, org.bukkit.event.Cancellable event) {
        if (item == null || item.getType().isAir())
            return;

        // Debug
        boolean debug = plugin.isModuleDebugEnabled(getId());

        if (!p.getWorld().getName().equals(targetWorldName)) {
            return;
        }

        if (p.hasPermission("valerinutils.pvpmina.bypass")) {
            if (debug)
                plugin.debug(getId(), p.getName() + " ignored because of bypass permission.");
            return;
        }

        if (currentBlockedItems.contains(item.getType())) {
            if (debug)
                plugin.debug(getId(), "Blocked item used by " + p.getName() + ": " + item.getType());

            if (event != null)
                event.setCancelled(true);

            // Prevent spamming messages
            if (!p.hasMetadata("pvpmina_msg_cooldown")) {
                String msg = getConfig().getString("messages.item-blocked", "&cBlocked!")
                        .replace("%mode%", getModeDisplayName(currentMode));
                p.sendMessage(parseComponent(msg));
                p.setMetadata("pvpmina_msg_cooldown", new org.bukkit.metadata.FixedMetadataValue(plugin, true));

                Bukkit.getScheduler().runTaskLater(plugin, () -> p.removeMetadata("pvpmina_msg_cooldown", plugin), 20L);
            }
        } else {
            if (debug && item.getType() != Material.AIR) {
                plugin.debug(getId(), "Allowed item " + p.getName() + ": " + item.getType());
            }
        }
    }

    // --- Commands ---

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        // 1. Player-only command: /pvpmina bossbar
        if (args.length > 0 && args[0].equalsIgnoreCase("bossbar")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(parseComponent("<red>Only players can toggle the BossBar."));
                return true;
            }
            Player p = (Player) sender;

            // Toggle Logic
            org.bukkit.NamespacedKey key = new org.bukkit.NamespacedKey(plugin, "pvpmina_bossbar_hidden");
            org.bukkit.persistence.PersistentDataContainer data = p.getPersistentDataContainer();

            if (data.has(key, org.bukkit.persistence.PersistentDataType.BYTE)) {
                // Was hidden, now show
                data.remove(key);
                p.sendMessage(parseComponent(getConfig().getString("messages.bossbar-toggle-on", "<green>BossBar ON")));
                updateBossBar(); // Force update to show it immediately if applicable
            } else {
                // Was shown, now hide
                data.set(key, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                p.sendMessage(parseComponent(getConfig().getString("messages.bossbar-toggle-off", "<red>BossBar OFF")));
                if (activeBossBar != null)
                    activeBossBar.removePlayer(p);
            }
            return true;
        }

        // 2. Admin commands
        if (!sender.hasPermission("valerinutils.pvpmina.admin")) {
            sender.sendMessage(parseComponent(getConfig().getString("messages.no-permission", "&cNo permission.")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(parseComponent("<yellow>/" + label + " force <mode> | reload | bossbar"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfigCache();
            rotateMode(false); // Re-apply current or new settings
            sender.sendMessage(parseComponent("<green>PvpMina reloaded."));
            return true;
        }

        if (args[0].equalsIgnoreCase("force") && args.length > 1) {
            String targetMode = args[1].toLowerCase();
            if (getConfig().getConfigurationSection("modes." + targetMode) == null) {
                sender.sendMessage(parseComponent("<red>Invalid mode. Available: " +
                        String.join(", ", getConfig().getConfigurationSection("modes").getKeys(false))));
                return true;
            }

            // Force switch
            if (currentMode != null) {
                List<String> endCmds = getConfig().getStringList("modes." + currentMode + ".commands-on-end");
                dispatchConsoleCommands(endCmds);
            }

            currentMode = targetMode;
            nextRotationTime = Instant.now().plus(Duration.ofMinutes(intervalMinutes));

            loadModeSettings(currentMode);
            List<String> startCmds = getConfig().getStringList("modes." + currentMode + ".commands-on-start");
            dispatchConsoleCommands(startCmds);

            sender.sendMessage(parseComponent("<green>Forced mode to: " + targetMode));
            updateBossBar(); // Instant update
            return true;
        }

        return true;
    }
}
