package me.mtynnn.valerinutils.modules.kits;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class KitsModule implements Module, Listener, CommandExecutor, TabCompleter {

    private final ValerinUtils plugin;
    private static final int PLAYER_INV_SIZE = 36;
    private static final long INITIAL_KIT_COOLDOWN_MS = 5L * 60L * 1000L;
    private final Map<UUID, Long> initialKitNextUseAtMs = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastDeathWorldByPlayer = new ConcurrentHashMap<>();
    private long lastConsoleNoArgsWarnAtMs = 0L;
    private long lastConsoleUnknownWarnAtMs = 0L;
    private long lastConsolePlayerOnlyWarnAtMs = 0L;
    private long lastConsoleTraceAtMs = 0L;

    public KitsModule(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    // Holder personalizado para identificar la GUI de preview
    private static class KitsPreviewHolder implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            return null;
        }
    }

    @Override
    public String getId() {
        return "kits";
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        registerCommand("vukits");
        registerCommand("kits");
    }

    private void registerCommand(String name) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd == null) {
            plugin.getLogger().severe("[Kits] El comando /" + name + " no está registrado en plugin.yml");
            return;
        }
        cmd.setExecutor(this);
        cmd.setTabCompleter(this);

        PluginCommand active = Bukkit.getPluginCommand(name);
        if (active != null && active.getPlugin() != plugin) {
            if (active.getPlugin().getName().equalsIgnoreCase(plugin.getName())) {
                plugin.getLogger().info("[Kits] /" + name + " estaba enlazado a una instancia anterior, se reasignó.");
            } else {
                plugin.getLogger().warning("[Kits] Conflicto de comando: /" + name + " pertenece a "
                        + active.getPlugin().getName() + ". Usa /" + plugin.getName().toLowerCase() + ":" + name
                        + " o cambia el alias.");
            }
        }
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        unregisterCommand("vukits");
        unregisterCommand("kits");
        initialKitNextUseAtMs.clear();
        lastDeathWorldByPlayer.clear();
    }

    private void unregisterCommand(String name) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd == null) {
            return;
        }
        cmd.setExecutor(null);
        cmd.setTabCompleter(null);
    }

    private FileConfiguration getConfig() {
        return plugin.getConfigManager().getConfig("kits");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                sendHelp(player);
            } else {
                maybeWarnConsoleNoArgs(command.getName());
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "create":
                if (!(sender instanceof Player player)) {
                    maybeWarnConsolePlayerOnlySub(command.getName(), sub, args, sender);
                    return true;
                }
                if (!player.hasPermission("valerinutils.kits.admin"))
                    return noPerms(player);
                if (args.length < 2) {
                    player.sendMessage(plugin.translateColors("%prefix%&cUso: /vukits create <nombre>"));
                    return true;
                }
                handleCreate(player, args[1]);
                break;
            case "preview":
                if (!(sender instanceof Player player)) {
                    maybeWarnConsolePlayerOnlySub(command.getName(), sub, args, sender);
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage(plugin.translateColors("%prefix%&cUso: /vukits preview <nombre>"));
                    return true;
                }
                handlePreview(player, args[1]);
                break;
            case "give":
                if (!sender.hasPermission("valerinutils.kits.admin")) {
                    sender.sendMessage(plugin.translateColors(plugin.getConfigManager().getConfig("settings")
                            .getString("messages.no-permission", "&cNo tienes permiso.")));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.translateColors("%prefix%&cUso: /vukits give <jugador> <nombre>"));
                    return true;
                }
                handleGive(sender, args[1], args[2]);
                break;
            case "delete":
                if (!sender.hasPermission("valerinutils.kits.admin")) {
                    sender.sendMessage(plugin.translateColors(plugin.getConfigManager().getConfig("settings")
                            .getString("messages.no-permission", "&cNo tienes permiso.")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.translateColors("%prefix%&cUso: /vukits delete <nombre>"));
                    return true;
                }
                handleDelete(sender, args[1]);
                break;
            case "reset":
                if (!sender.hasPermission("valerinutils.kits.admin")) {
                    sender.sendMessage(plugin.translateColors(plugin.getConfigManager().getConfig("settings")
                            .getString("messages.no-permission", "&cNo tienes permiso.")));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(plugin.translateColors("%prefix%&cUso: /vukits reset <jugador>"));
                    return true;
                }
                handleReset(sender, args[1]);
                break;
            case "list":
                handleList(sender);
                break;
            case "inicial":
                if (!(sender instanceof Player player)) {
                    maybeWarnConsolePlayerOnlySub(command.getName(), sub, args, sender);
                    return true;
                }
                handleInitialKit(player);
                break;
            case "debug":
                if (!sender.hasPermission("valerinutils.kits.admin")) {
                    if (sender instanceof Player player) {
                        return noPerms(player);
                    }
                    sender.sendMessage(plugin.translateColors(plugin.getConfigManager().getConfig("settings")
                            .getString("messages.no-permission", "&cNo tienes permiso.")));
                    return true;
                }
                boolean enabled = plugin.toggleModuleDebug(getId());
                sender.sendMessage(plugin.translateColors(
                        "%prefix%&7Debug de kits: " + (enabled ? "&aACTIVADO" : "&cDESACTIVADO")));
                break;
            default:
                if (sender instanceof Player player) {
                    sendHelp(player);
                } else {
                    maybeWarnConsoleUnknownSub(command.getName(), sub);
                }
                break;
        }

        return true;
    }

    private void handleCreate(Player player, String name) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || !item.getType().name().contains("SHULKER_BOX")) {
            player.sendMessage(plugin.translateColors("%prefix%&cDebes sostener una Shulker Box en la mano."));
            return;
        }

        ItemMeta meta = item.getItemMeta();
        String displayName = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "&8Shulker de " + name;

        FileConfiguration cfg = getConfig();
        cfg.set("kits." + name + ".display_name", displayName);
        cfg.set("kits." + name + ".shulker_item", item);

        plugin.getConfigManager().saveConfig("kits");
        player.sendMessage(plugin.translateColors(
                "%prefix%&aKit &e" + name + " &acreado correctamente usando el nombre: &f" + displayName));
    }

    private void handlePreview(Player player, String name) {
        FileConfiguration cfg = getConfig();
        if (!cfg.contains("kits." + name)) {
            player.sendMessage(plugin.translateColors("%prefix%&cEl kit &e" + name + " &cno existe."));
            return;
        }

        ItemStack shulker = cfg.getItemStack("kits." + name + ".shulker_item");
        String title = cfg.getString("kits." + name + ".display_name", "&8Preview: " + name);

        if (shulker == null || !(shulker.getItemMeta() instanceof BlockStateMeta bsm)) {
            player.sendMessage(plugin.translateColors("%prefix%&cError al cargar el contenido del kit."));
            return;
        }

        if (bsm.getBlockState() instanceof ShulkerBox box) {
            Inventory previewInv = Bukkit.createInventory(new KitsPreviewHolder(), 27, plugin.translateColors(title));
            previewInv.setContents(box.getInventory().getContents());
            player.openInventory(previewInv);
        }
    }

    private void handleGive(CommandSender sender, String targetName, String kitName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(plugin.translateColors("%prefix%&cJugador no encontrado o no está online."));
            return;
        }

        FileConfiguration cfg = getConfig();
        if (!cfg.contains("kits." + kitName)) {
            sender.sendMessage(plugin.translateColors("%prefix%&cEl kit &e" + kitName + " &cno existe."));
            return;
        }

        ItemStack shulker = cfg.getItemStack("kits." + kitName + ".shulker_item");
        if (shulker == null) {
            sender.sendMessage(plugin.translateColors("%prefix%&cError al cargar el item del kit."));
            return;
        }

        if (target.getInventory().firstEmpty() == -1) {
            target.getWorld().dropItemNaturally(target.getLocation(), shulker);
            target.sendMessage(
                    plugin.translateColors("%prefix%&eTu inventario estaba lleno, el kit se ha soltado al suelo."));
        } else {
            target.getInventory().addItem(shulker);
        }

        sender.sendMessage(
                plugin.translateColors("%prefix%&aHas dado el kit &e" + kitName + " &aa &f" + target.getName()));
        target.sendMessage(plugin.translateColors(
                "%prefix%&aHas recibido el kit: &f" + cfg.getString("kits." + kitName + ".display_name")));
    }

    private void handleDelete(CommandSender sender, String name) {
        FileConfiguration cfg = getConfig();
        if (!cfg.contains("kits." + name)) {
            sender.sendMessage(plugin.translateColors("%prefix%&cEl kit &e" + name + " &cno existe."));
            return;
        }

        cfg.set("kits." + name, null);
        plugin.getConfigManager().saveConfig("kits");
        sender.sendMessage(plugin.translateColors("%prefix%&cKit &e" + name + " &celiminado."));
    }

    private void handleReset(CommandSender sender, String targetName) {
        Player target = Bukkit.getPlayer(targetName);
        if (target == null) {
            sender.sendMessage(plugin.translateColors("%prefix%&cJugador no encontrado o no está online."));
            return;
        }

        me.mtynnn.valerinutils.core.PlayerData data = plugin.getPlayerData(target.getUniqueId());
        if (data != null) {
            data.setStarterKitReceived(false);
            sender.sendMessage(
                    plugin.translateColors("%prefix%&aFlag de kit inicial reseteado para &f" + target.getName()));
        }
    }

    private void handleList(CommandSender sender) {
        FileConfiguration cfg = getConfig();
        ConfigurationSection kits = cfg.getConfigurationSection("kits");
        if (kits == null || kits.getKeys(false).isEmpty()) {
            sender.sendMessage(plugin.translateColors("%prefix%&7No hay kits creados."));
            return;
        }

        sender.sendMessage(
                plugin.translateColors("%prefix%&eKits disponibles (&f" + kits.getKeys(false).size() + "&e):"));
        for (String key : kits.getKeys(false)) {
            String display = cfg.getString("kits." + key + ".display_name", key);
            sender.sendMessage(plugin.translateColors(" &8- &f" + key + " &7(" + display + "&7)"));
        }
    }

    private void handleInitialKit(Player player) {
        long now = System.currentTimeMillis();
        long nextAllowed = initialKitNextUseAtMs.getOrDefault(player.getUniqueId(), 0L);
        if (nextAllowed > now) {
            long remainingMs = nextAllowed - now;
            long remainingSec = (remainingMs + 999) / 1000;
            long min = remainingSec / 60;
            long sec = remainingSec % 60;
            player.sendMessage(plugin.translateColors(
                    "%prefix%&cDebes esperar &e" + min + "m " + sec + "s &cpara volver a usar &f/kit inicial&c."));
            return;
        }

        giveAutoKit(player, false);
        initialKitNextUseAtMs.put(player.getUniqueId(), now + INITIAL_KIT_COOLDOWN_MS);
        player.sendMessage(plugin.translateColors("%prefix%&aHas recibido el &eKit Inicial&a."));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        FileConfiguration cfg = getConfig();
        if (!cfg.getBoolean("settings.starter_kit_enabled", true))
            return;

        // Esperar un momento para asegurar que PlayerData esté cargado (aunque se carga
        // en AsyncLogin)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            me.mtynnn.valerinutils.core.PlayerData data = plugin.getPlayerData(player.getUniqueId());
            if (data == null) {
                return;
            }

            if (!data.isStarterKitReceived()) {
                giveAutoKit(player, false);
                data.setStarterKitReceived(true);
            }
        }, 10L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        initialKitNextUseAtMs.remove(event.getPlayer().getUniqueId());
        lastDeathWorldByPlayer.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (victim.getWorld() != null) {
            lastDeathWorldByPlayer.put(victim.getUniqueId(), victim.getWorld().getName().toLowerCase());
        }
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        String raw = event.getCommand();
        if (raw == null) {
            return;
        }

        String lowered = raw.toLowerCase().trim();
        if (!(lowered.startsWith("kit") || lowered.startsWith("kits") || lowered.startsWith("vukits"))) {
            return;
        }

        if (!isDebugEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastConsoleTraceAtMs < 60000L) {
            return;
        }
        lastConsoleTraceAtMs = now;

        CommandSender sender = event.getSender();
        String senderInfo = sender != null ? sender.getClass().getSimpleName() : "null";
        if (sender instanceof BlockCommandSender bcs) {
            senderInfo = "BlockCommandSender@" + bcs.getBlock().getLocation();
        } else if (sender instanceof ConsoleCommandSender) {
            senderInfo = "Console";
        }

        plugin.getLogger().warning("[Kits] Detectado comando desde servidor: /" + raw + " (sender=" + senderInfo + ")");
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        FileConfiguration cfg = getConfig();
        if (!cfg.getBoolean("settings.respawn_kit_enabled", true))
            return;

        String deathWorld = lastDeathWorldByPlayer.remove(event.getPlayer().getUniqueId());
        if (deathWorld != null) {
            List<String> disabledWorlds = cfg.getStringList("settings.respawn_kit_disabled_worlds");
            for (String world : disabledWorlds) {
                if (world != null && deathWorld.equalsIgnoreCase(world.trim())) {
                    return;
                }
            }
        }

        boolean onlyOnDeath = cfg.getBoolean("settings.respawn_kit_only_on_death", true);
        if (onlyOnDeath) {
            try {
                if (event.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH) {
                    return;
                }
            } catch (Throwable ignored) {
                // If server implementation doesn't support respawn reason, fall back to old behavior.
            }
        }

        boolean overwrite = cfg.getBoolean("settings.respawn_kit_overwrite", false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                giveAutoKit(event.getPlayer(), overwrite);
            }
        }, 5L);
    }

    private void giveAutoKit(Player player, boolean overwriteExisting) {
        FileConfiguration cfg = getConfig();
        ConfigurationSection auto = cfg.getConfigurationSection("auto_kit");
        if (auto == null)
            return;

        // Armor
        ConfigurationSection armor = auto.getConfigurationSection("armor");
        if (armor != null) {
            setArmor(player, armor, overwriteExisting);
        }

        // Items
        List<Map<?, ?>> items = auto.getMapList("items");
        for (Map<?, ?> entry : items) {
            ItemStack item = parseItem(entry);
            if (item == null) {
                continue;
            }

            int slot = parseInt(entry.get("slot"), -1);
            if (slot >= 0 && slot < PLAYER_INV_SIZE) {
                if (overwriteExisting || isEmpty(player.getInventory().getItem(slot))) {
                    player.getInventory().setItem(slot, item);
                } else {
                    player.getInventory().addItem(item);
                }
            } else {
                player.getInventory().addItem(item);
            }
        }
    }

    private void setArmor(Player player, ConfigurationSection armorCfg, boolean overwriteExisting) {
        ItemStack helmet = getItem(armorCfg.getString("helmet"));
        ItemStack chestplate = getItem(armorCfg.getString("chestplate"));
        ItemStack leggings = getItem(armorCfg.getString("leggings"));
        ItemStack boots = getItem(armorCfg.getString("boots"));

        if (overwriteExisting || isEmpty(player.getInventory().getHelmet())) {
            player.getInventory().setHelmet(helmet);
        }
        if (overwriteExisting || isEmpty(player.getInventory().getChestplate())) {
            player.getInventory().setChestplate(chestplate);
        }
        if (overwriteExisting || isEmpty(player.getInventory().getLeggings())) {
            player.getInventory().setLeggings(leggings);
        }
        if (overwriteExisting || isEmpty(player.getInventory().getBoots())) {
            player.getInventory().setBoots(boots);
        }
    }

    private ItemStack parseItem(Map<?, ?> entry) {
        Object materialRaw = entry.get("material");
        if (!(materialRaw instanceof String materialName) || materialName.isBlank()) {
            return null;
        }

        Material material = Material.matchMaterial(materialName.trim());
        if (material == null) {
            return null;
        }

        int amount = parseInt(entry.get("amount"), 1);
        if (amount < 1) {
            amount = 1;
        }

        return new ItemStack(material, amount);
    }

    private int parseInt(Object value, int def) {
        if (value == null) {
            return def;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    private ItemStack getItem(String materialName) {
        if (materialName == null || materialName.isEmpty())
            return null;
        try {
            return new ItemStack(Material.valueOf(materialName.toUpperCase()));
        } catch (Exception e) {
            return null;
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof KitsPreviewHolder) {
            event.setCancelled(true);
        }
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.translateColors("&8&m       &r &#FFEE8Cᴋɪᴛꜱ ꜱʏꜱᴛᴇᴍ &8&m       "));
        sender.sendMessage(plugin.translateColors(" &e/vukits preview <nombre> &7- Ver contenido"));
        sender.sendMessage(plugin.translateColors(" &e/vukits list &7- Ver lista de kits"));
        sender.sendMessage(plugin.translateColors(" &e/kit inicial &7- Reclamar kit inicial (5min)"));
        if (sender.hasPermission("valerinutils.kits.admin")) {
            sender.sendMessage(plugin.translateColors(" &e/vukits create <nombre> &7- Crear desde la mano"));
            sender.sendMessage(plugin.translateColors(" &e/vukits give <jugador> <nombre> &7- Dar kit"));
            sender.sendMessage(plugin.translateColors(" &e/vukits reset <jugador> &7- Reset flag inicial"));
            sender.sendMessage(plugin.translateColors(" &e/vukits delete <nombre> &7- Eliminar kit"));
            sender.sendMessage(plugin.translateColors(" &e/vukits debug &7- Toggle debug del módulo"));
        }
    }

    private void maybeWarnConsoleNoArgs(String commandName) {
        long now = System.currentTimeMillis();
        if (now - lastConsoleNoArgsWarnAtMs < 60000L) {
            return;
        }
        lastConsoleNoArgsWarnAtMs = now;
        plugin.getLogger().warning("[Kits] Se está ejecutando /" + commandName
                + " desde consola sin argumentos. Probablemente un command block o otro plugin lo está disparando.");
        maybeLogCallerTrace("no-args", "/" + commandName);
    }

    private void maybeWarnConsoleUnknownSub(String commandName, String sub) {
        long now = System.currentTimeMillis();
        if (now - lastConsoleUnknownWarnAtMs < 60000L) {
            return;
        }
        lastConsoleUnknownWarnAtMs = now;
        plugin.getLogger().warning("[Kits] Se está ejecutando /" + commandName + " " + sub
                + " desde consola. Probablemente un command block o algún plugin lo está disparando.");
        maybeLogCallerTrace("unknown-sub", "/" + commandName + " " + sub);
    }

    private void maybeWarnConsolePlayerOnlySub(String commandName, String sub, String[] args, CommandSender sender) {
        long now = System.currentTimeMillis();
        if (now - lastConsolePlayerOnlyWarnAtMs < 60000L) {
            return;
        }
        lastConsolePlayerOnlyWarnAtMs = now;

        String senderInfo = sender != null ? sender.getClass().getSimpleName() : "null";
        if (sender instanceof BlockCommandSender bcs) {
            senderInfo = "BlockCommandSender@" + bcs.getBlock().getLocation();
        } else if (sender instanceof ConsoleCommandSender) {
            senderInfo = "Console";
        }

        String commandLine = "/" + commandName + " " + String.join(" ", args);
        plugin.getLogger().warning("[Kits] Subcomando solo-jugador ejecutado desde servidor: " + commandLine
                + " (sender=" + senderInfo + ").");
        maybeLogCallerTrace("player-only", commandLine);
    }

    private void maybeLogCallerTrace(String reason, String commandLine) {
        FileConfiguration cfg = getConfig();
        if (!isDebugEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastConsoleTraceAtMs < 60000L) {
            return;
        }
        lastConsoleTraceAtMs = now;

        StackTraceElement[] trace = new Exception("Command trace").getStackTrace();
        List<String> lines = new ArrayList<>();
        for (StackTraceElement el : trace) {
            String cls = el.getClassName();
            if (cls.startsWith("me.mtynnn.valerinutils.")) {
                continue;
            }
            if (cls.startsWith("org.bukkit.") || cls.startsWith("io.papermc.") || cls.startsWith("com.destroystokyo.")
                    || cls.startsWith("net.minecraft.") || cls.startsWith("java.") || cls.startsWith("javax.")
                    || cls.startsWith("sun.") || cls.startsWith("jdk.")) {
                continue;
            }
            lines.add("  at " + el);
            if (lines.size() >= 15) {
                break;
            }
        }

        plugin.getLogger().warning("[Kits] debug_command_spam=true (" + reason + "): " + commandLine);
        if (lines.isEmpty()) {
            plugin.getLogger().warning("[Kits] No se pudo determinar el origen (solo stack interno/NMS).");
        } else {
            for (String line : lines) {
                plugin.getLogger().warning(line);
            }
        }
    }

    private boolean isDebugEnabled() {
        FileConfiguration cfg = getConfig();
        return plugin.isModuleDebugEnabled(getId()) || (cfg != null && cfg.getBoolean("settings.debug_command_spam", false));
    }

    private boolean noPerms(Player player) {
        player.sendMessage(plugin.translateColors(plugin.getConfigManager().getConfig("settings")
                .getString("messages.no-permission", "&cNo tienes permiso.")));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("preview", "list", "inicial"));
            if (sender.hasPermission("valerinutils.kits.admin")) {
                subs.add("create");
                subs.add("give");
                subs.add("delete");
                subs.add("reset");
                subs.add("debug");
            }
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("preview") || args[0].equalsIgnoreCase("delete")) {
                ConfigurationSection kits = getConfig().getConfigurationSection("kits");
                if (kits != null) {
                    return new ArrayList<>(kits.getKeys(false)).stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase())).collect(Collectors.toList());
                }
            }

            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset")) {
                String partial = args[1].toLowerCase();
                return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                        .filter(n -> n != null && n.toLowerCase().startsWith(partial))
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give")) {
                ConfigurationSection kits = getConfig().getConfigurationSection("kits");
                if (kits != null) {
                    return new ArrayList<>(kits.getKeys(false)).stream()
                            .filter(s -> s.startsWith(args[2].toLowerCase())).collect(Collectors.toList());
                }
            }
        }

        return Collections.emptyList();
    }
}
