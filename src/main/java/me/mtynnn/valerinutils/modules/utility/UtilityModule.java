package me.mtynnn.valerinutils.modules.utility;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import me.mtynnn.valerinutils.core.PlayerData;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class UtilityModule implements Module, CommandExecutor, Listener {

    private final ValerinUtils plugin;
    private final UtilityWorkbenchCommands workbenchCommands;
    private final UtilityBroadcastCommand broadcastCommand;
    private final UtilitySeenCommand seenCommand;
    private final UtilityNickCommand nickCommand;
    private final UtilitySellCommand sellCommand;
    private final UtilityPersonalWorldCommands personalWorldCommands;

    private final Map<Material, Material> condenseMap = new HashMap<>();

    private static final String[] REGISTERED_COMMANDS = {
            "craft", "enderchest", "anvil", "smithingtable",
            "cartographytable", "grindstone", "loom", "stonecutter",
            "disposal",
            "hat", "condense", "seen", "clear", "gmc", "gms", "gmsp", "gma", "ping",
            "fly", "speed", "broadcast", "vubroadcast", "heal", "feed", "repair", "nick", "skull", "suicide",
            "near", "vtop",
            "ptime", "pweather", "sell"
    };

    private static final Map<String, String> DEFAULT_MESSAGES = Map.ofEntries(
            Map.entry("only-players", "%prefix%<red>Solo jugadores pueden usar este comando."),
            Map.entry("no-permission", "%prefix%<red>No tienes permiso para usar este comando."),
            Map.entry("module-disabled", "%prefix%<red>Este comando está deshabilitado."),
            Map.entry("player-not-found", "%prefix%<red>Jugador no encontrado."),
            Map.entry("seen-usage", "%prefix%<gray>Uso: <yellow>/seen <jugador>"),
            Map.entry("seen-online", "<green>Online"),
            Map.entry("seen-offline", "<gray>Offline hace %time%"),
            Map.entry("clear-success", "%prefix%<green>Inventario de <white>%player% <green>limpiado."),
            Map.entry("clear-success-self", "%prefix%<green>Tu inventario ha sido limpiado."),
            Map.entry("ping-self", "%prefix%<gray>Tu ping es de: <yellow>%ping%ms"),
            Map.entry("ping-other", "%prefix%<gray>El ping de <white>%player% <gray>es de: <yellow>%ping%ms"),
            Map.entry("fly-enabled", "%prefix%<gray>Modo vuelo: <green>Activado"),
            Map.entry("fly-disabled", "%prefix%<gray>Modo vuelo: <red>Desactivado"),
            Map.entry("fly-others", "%prefix%<gray>Modo vuelo de <white>%player%<gray>: <yellow>%state%"),
            Map.entry("speed-invalid", "%prefix%<red>La velocidad debe estar entre 1 y 10."),
            Map.entry("speed-usage", "%prefix%<gray>Uso: <yellow>/speed <1-10> [jugador]"),
            Map.entry("speed-success", "%prefix%<gray>Tu velocidad de <yellow>%type% <gray>ha sido ajustada a <green>%speed%<gray>."),
            Map.entry("speed-others", "%prefix%<gray>Velocidad de <yellow>%type% <gray>de <white>%player% <gray>ajustada a <green>%speed%<gray>."),
            Map.entry("nick-usage", "%prefix%<gray>Uso: <yellow>/nick <apodo|off>"),
            Map.entry("nick-success", "%prefix%<gray>Tu apodo ahora es: <white>%nick%"),
            Map.entry("nick-off", "%prefix%<red>Has desactivado tu apodo."),
            Map.entry("nick-format-not-allowed", "%prefix%<red>No puedes usar ese formato en el nick. Nivel: <yellow>%tier%"),
            Map.entry("gamemode-success", "%prefix%<green>Modo de juego cambiado a <yellow>%mode%<green>."),
            Map.entry("hat-success", "%prefix%<green>¡Nuevo sombrero equipado!"),
            Map.entry("condense-success", "%prefix%<green>Se han condensado <white>%count% <green>items en bloques."),
            Map.entry("condense-nothing", "%prefix%<gray>No hay nada que condensar en tu inventario."),
            Map.entry("heal-success", "%prefix%<green>Has sido curado."),
            Map.entry("heal-others", "%prefix%<green>Has curado a <white>%player%<green>."),
            Map.entry("feed-success", "%prefix%<green>Tu hambre ha sido saciada."),
            Map.entry("feed-others", "%prefix%<green>Has alimentado a <white>%player%<green>."),
            Map.entry("repair-usage", "%prefix%<gray>Uso: <yellow>/fix hand"),
            Map.entry("repair-success", "%prefix%<green>Item reparado con éxito."),
            Map.entry("repair-error", "%prefix%<red>Este item no se puede reparar."),
            Map.entry("skull-success", "%prefix%<green>Has recibido la cabeza de <white>%player%<green>."),
            Map.entry("suicide-msg", "%prefix%<gray>Has decidido terminar con todo..."),
            Map.entry("near-format", "%prefix%<gray>Jugadores cercanos en <yellow>%radius%m<gray>: <white>%players%"),
            Map.entry("near-none", "%prefix%<gray>No hay jugadores cerca."),
            Map.entry("top-success", "%prefix%<green>Teletransportado a la superficie."),
            Map.entry("ptime-usage", "%prefix%<gray>Uso: <yellow>/ptime <day|night|reset|ticks>"),
            Map.entry("ptime-set", "%prefix%<green>Tiempo personal cambiado a <yellow>%value%<green>."),
            Map.entry("ptime-reset", "%prefix%<green>Tiempo personal reseteado."),
            Map.entry("pweather-usage", "%prefix%<gray>Uso: <yellow>/pweather <clear|rain|reset>"),
            Map.entry("pweather-set", "%prefix%<green>Clima personal cambiado a <yellow>%value%<green>."),
            Map.entry("pweather-reset", "%prefix%<green>Clima personal reseteado."),
            Map.entry("sell-usage", "%prefix%<gray>Uso: <yellow>/sell <hand|inventory>"),
            Map.entry("sell-disabled", "%prefix%<red>El sistema de venta está deshabilitado."),
            Map.entry("sell-economy-missing", "%prefix%<red>No se detectó economía (Vault)."),
            Map.entry("sell-nothing", "%prefix%<gray>No tienes items vendibles."),
            Map.entry("sell-success", "%prefix%<green>Vendiste <white>%items% <green>items por <yellow>$%amount%<green>."),
            Map.entry("broadcast-usage", "%prefix%<gray>Uso: <yellow>/broadcast <mensaje> <gray>o <yellow>/vubroadcast <mensaje>"));

    public UtilityModule(ValerinUtils plugin) {
        this.plugin = plugin;
        this.workbenchCommands = new UtilityWorkbenchCommands(this);
        this.broadcastCommand = new UtilityBroadcastCommand(this);
        this.seenCommand = new UtilitySeenCommand(this);
        this.nickCommand = new UtilityNickCommand(this);
        this.sellCommand = new UtilitySellCommand(this);
        this.personalWorldCommands = new UtilityPersonalWorldCommands(this);
        setupCondenseMap();
    }

    @Override
    public String getId() {
        return "utility";
    }

    @Override
    public void enable() {
        FileConfiguration cfg = getConfig();
        if (cfg == null || !cfg.getBoolean("enabled", true))
            return;

        for (String command : REGISTERED_COMMANDS) {
            registerCommand(command);
        }

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
        for (String cmdName : REGISTERED_COMMANDS) {
            PluginCommand cmd = plugin.getCommand(cmdName);
            if (cmd != null) {
                cmd.setExecutor(null);
            }
        }
    }

    private void registerCommand(String name) {
        PluginCommand command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(this);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        String cmdName = command.getName().toLowerCase(Locale.ROOT);

        if (!(sender instanceof Player player)) {
            if (cmdName.equals("seen") && args.length > 0) {
                seenCommand.execute(sender, args[0]);
            } else if (cmdName.equals("broadcast") || cmdName.equals("vubroadcast")) {
                broadcastCommand.execute(sender, args);
            } else {
                sender.sendMessage(getMessage("only-players"));
            }
            return true;
        }

        if (cmdName.startsWith("gm")) {
            handleGameMode(player, cmdName);
            return true;
        }

        switch (cmdName) {
            case "craft", "workbench", "wv" -> workbenchCommands.openStandardUi(player, "craft");
            case "enderchest", "ec" -> workbenchCommands.openStandardUi(player, "enderchest");
            case "anvil" -> workbenchCommands.openStandardUi(player, "anvil");
            case "smithingtable", "st" -> workbenchCommands.openStandardUi(player, "smithing");
            case "cartographytable", "ct" -> workbenchCommands.openStandardUi(player, "cartography");
            case "grindstone" -> workbenchCommands.openStandardUi(player, "grindstone");
            case "loom" -> workbenchCommands.openStandardUi(player, "loom");
            case "stonecutter" -> workbenchCommands.openStandardUi(player, "stonecutter");
            case "disposal", "trash", "basurero", "diposal" -> workbenchCommands.openDisposal(player);
            case "hat" -> handleHat(player);
            case "condense" -> handleCondense(player);
            case "seen" -> {
                if (args.length > 0) {
                    seenCommand.execute(player, args[0]);
                } else {
                    player.sendMessage(getMessage("seen-usage"));
                }
            }
            case "clear" -> handleClear(player, args);
            case "ping" -> handlePing(player, args);
            case "fly" -> handleFly(player, args);
            case "speed" -> handleSpeed(player, args);
            case "broadcast", "vubroadcast" -> broadcastCommand.execute(player, args);
            case "heal" -> handleHeal(player, args);
            case "feed" -> handleFeed(player, args);
            case "repair" -> handleRepair(player, args);
            case "nick" -> nickCommand.execute(player, args);
            case "skull" -> handleSkull(player, args);
            case "suicide" -> handleSuicide(player);
            case "near" -> handleNear(player, args);
            case "vtop" -> handleTop(player);
            case "ptime" -> personalWorldCommands.handlePlayerTime(player, args);
            case "pweather" -> personalWorldCommands.handlePlayerWeather(player, args);
            case "sell" -> sellCommand.execute(player, args);
            default -> {
            }
        }
        return true;
    }

    @EventHandler
    public void onDisposalClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof UtilityDisposalHolder) {
            event.getInventory().clear();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData playerData = plugin.getPlayerData(player.getUniqueId());
        if (playerData != null && playerData.getNickname() != null) {
            Component nickComp = plugin.parseComponent(playerData.getNickname());
            player.displayName(nickComp);
            player.playerListName(nickComp);
        }
    }

    private void handlePing(Player player, String[] args) {
        Player target = player;
        if (args.length > 0) {
            if (!checkStatus(player, "ping.others"))
                return;
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(getMessage("player-not-found"));
                return;
            }
        } else {
            if (!checkStatus(player, "ping"))
                return;
        }

        int ping = target.getPing();
        String msgKey = (target == player) ? "ping-self" : "ping-other";
        player.sendMessage(getMessage(msgKey)
                .replace("%player%", target.getName())
                .replace("%ping%", String.valueOf(ping)));
    }

    private void handleFly(Player player, String[] args) {
        Player target = player;
        if (args.length > 0) {
            if (!checkStatus(player, "fly.others"))
                return;
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(getMessage("player-not-found"));
                return;
            }
        } else {
            if (!checkStatus(player, "fly"))
                return;
        }

        boolean enabled = !target.getAllowFlight();
        target.setAllowFlight(enabled);
        if (enabled)
            target.setFlying(true);

        if (target == player) {
            player.sendMessage(getMessage(enabled ? "fly-enabled" : "fly-disabled"));
        } else {
            String state = enabled ? "Activado" : "Desactivado";
            player.sendMessage(getMessage("fly-others")
                    .replace("%player%", target.getName())
                    .replace("%state%", state));
            target.sendMessage(getMessage(enabled ? "fly-enabled" : "fly-disabled"));
        }
    }

    private void handleSpeed(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(getMessage("speed-usage"));
            return;
        }

        float speed;
        try {
            speed = Float.parseFloat(args[0]);
        } catch (NumberFormatException e) {
            player.sendMessage(getMessage("speed-invalid"));
            return;
        }

        if (speed < 1 || speed > 10) {
            player.sendMessage(getMessage("speed-invalid"));
            return;
        }

        Player target = player;
        if (args.length > 1) {
            if (!checkStatus(player, "speed.others"))
                return;
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                player.sendMessage(getMessage("player-not-found"));
                return;
            }
        } else {
            if (!checkStatus(player, "speed"))
                return;
        }

        float finalSpeed = speed / 10.0f;
        boolean isFlying = target.isFlying() || target.getAllowFlight();
        String type = isFlying ? "vuelo" : "caminata";

        if (isFlying) {
            target.setFlySpeed(finalSpeed);
        } else {
            target.setWalkSpeed(finalSpeed);
        }

        String msgKey = (target == player) ? "speed-success" : "speed-others";
        player.sendMessage(getMessage(msgKey)
                .replace("%player%", target.getName())
                .replace("%type%", type)
                .replace("%speed%", String.valueOf((int) speed)));
    }

    private void handleHeal(Player player, String[] args) {
        Player target = player;
        if (args.length > 0) {
            if (!checkStatus(player, "heal.others"))
                return;
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(getMessage("player-not-found"));
                return;
            }
        } else {
            if (!checkStatus(player, "heal"))
                return;
        }

        target.setHealth(target.getAttribute(Attribute.MAX_HEALTH).getValue());
        target.setFoodLevel(20);
        target.setFireTicks(0);
        for (org.bukkit.potion.PotionEffect effect : target.getActivePotionEffects()) {
            target.removePotionEffect(effect.getType());
        }

        playSound(target, "heal");
        if (target == player) {
            player.sendMessage(getMessage("heal-success"));
        } else {
            player.sendMessage(getMessage("heal-others").replace("%player%", target.getName()));
            target.sendMessage(getMessage("heal-success"));
        }
    }

    private void handleFeed(Player player, String[] args) {
        Player target = player;
        if (args.length > 0) {
            if (!checkStatus(player, "feed.others"))
                return;
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(getMessage("player-not-found"));
                return;
            }
        } else {
            if (!checkStatus(player, "feed"))
                return;
        }

        target.setFoodLevel(20);
        target.setSaturation(10);
        if (target == player) {
            player.sendMessage(getMessage("feed-success"));
        } else {
            player.sendMessage(getMessage("feed-others").replace("%player%", target.getName()));
            target.sendMessage(getMessage("feed-success"));
        }
    }

    private void handleRepair(Player player, String[] args) {
        if (!checkStatus(player, "repair"))
            return;
        if (args.length > 0 && !args[0].equalsIgnoreCase("hand")) {
            player.sendMessage(getMessage("repair-usage"));
            return;
        }
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir() || item.getType().getMaxDurability() <= 0) {
            player.sendMessage(getMessage("repair-error"));
            return;
        }

        org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) item.getItemMeta();
        damageable.setDamage(0);
        item.setItemMeta(damageable);
        player.sendMessage(getMessage("repair-success"));
        playSound(player, "condense");
    }

    private void handleSkull(Player player, String[] args) {
        if (!checkStatus(player, "skull"))
            return;
        String name = args.length > 0 ? args[0] : player.getName();

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(name));
        meta.displayName(plugin.parseComponent("<yellow>Cabeza de " + name));
        skull.setItemMeta(meta);

        player.getInventory().addItem(skull);
        player.sendMessage(getMessage("skull-success").replace("%player%", name));
    }

    private void handleSuicide(Player player) {
        if (!checkStatus(player, "suicide"))
            return;
        player.sendMessage(getMessage("suicide-msg"));
        player.setHealth(0);
    }

    private void handleNear(Player player, String[] args) {
        if (!checkStatus(player, "near"))
            return;
        int radius = 100;
        if (args.length > 0) {
            try {
                radius = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        final int r = radius;
        String players = player.getNearbyEntities(r, r, r).stream()
                .filter(entity -> entity instanceof Player)
                .map(entity -> ((Player) entity).getName()
                        + " (<yellow>" + (int) entity.getLocation().distance(player.getLocation()) + "m<white>)")
                .collect(Collectors.joining("<gray>, <white>"));

        if (players.isEmpty()) {
            player.sendMessage(getMessage("near-none"));
        } else {
            player.sendMessage(getMessage("near-format")
                    .replace("%radius%", String.valueOf(r))
                    .replace("%players%", players));
        }
    }

    private void handleTop(Player player) {
        if (!checkStatus(player, "top"))
            return;
        player.teleport(player.getWorld().getHighestBlockAt(player.getLocation()).getLocation().add(0, 1, 0));
        player.sendMessage(getMessage("top-success"));
    }

    private void handleGameMode(Player player, String cmd) {
        if (!checkStatus(player, "gamemode"))
            return;
        GameMode mode = switch (cmd) {
            case "gmc" -> GameMode.CREATIVE;
            case "gms" -> GameMode.SURVIVAL;
            case "gmsp" -> GameMode.SPECTATOR;
            case "gma" -> GameMode.ADVENTURE;
            default -> null;
        };
        if (mode != null) {
            player.setGameMode(mode);
            playSound(player, "gamemode-change");
            player.sendMessage(getMessage("gamemode-success").replace("%mode%", mode.name()));
        }
    }

    private void handleHat(Player player) {
        if (!checkStatus(player, "hat"))
            return;
        PlayerInventory inventory = player.getInventory();
        ItemStack hand = inventory.getItemInMainHand();
        if (hand.getType() == Material.AIR)
            return;

        ItemStack head = inventory.getHelmet();
        inventory.setHelmet(hand);
        inventory.setItemInMainHand(head);
        playSound(player, "hat-equip");
        player.sendMessage(getMessage("hat-success"));
    }

    private void handleCondense(Player player) {
        if (!checkStatus(player, "condense"))
            return;
        PlayerInventory inventory = player.getInventory();
        int condensedCount = 0;

        for (Material source : condenseMap.keySet()) {
            int amount = 0;
            for (ItemStack is : inventory.getContents()) {
                if (is != null && is.getType() == source) {
                    amount += is.getAmount();
                }
            }

            if (amount >= 9) {
                int toCondense = (amount / 9) * 9;
                int resultAmount = amount / 9;
                Material resultMat = condenseMap.get(source);

                inventory.remove(source);
                int left = amount - toCondense;
                if (left > 0) {
                    inventory.addItem(new ItemStack(source, left));
                }
                inventory.addItem(new ItemStack(resultMat, resultAmount));
                condensedCount += toCondense;
            }
        }

        if (condensedCount > 0) {
            playSound(player, "condense");
            player.sendMessage(getMessage("condense-success").replace("%count%", String.valueOf(condensedCount)));
        } else {
            player.sendMessage(getMessage("condense-nothing"));
        }
    }

    private void handleClear(Player player, String[] args) {
        Player target = player;
        if (args.length > 0) {
            if (!checkStatus(player, "clear.others"))
                return;
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                player.sendMessage(getMessage("player-not-found"));
                return;
            }
        } else {
            if (!checkStatus(player, "clear"))
                return;
        }

        target.getInventory().clear();
        playSound(player, "clear-inv");
        if (target != player) {
            player.sendMessage(getMessage("clear-success").replace("%player%", target.getName()));
        } else {
            player.sendMessage(getMessage("clear-success-self"));
        }
    }

    ValerinUtils plugin() {
        return plugin;
    }

    FileConfiguration getConfig() {
        return plugin.getConfigManager().getConfig("utilities");
    }

    boolean isBroadcastEnabled() {
        FileConfiguration cfg = getConfig();
        return cfg != null && cfg.getBoolean("enabled", true) && cfg.getBoolean("commands.broadcast.enabled", true);
    }

    boolean checkStatus(Player player, String key) {
        FileConfiguration cfg = getConfig();
        if (cfg == null || !cfg.getBoolean("enabled", true))
            return false;

        String baseKey = key;
        boolean others = false;
        if (key.endsWith(".others")) {
            others = true;
            baseKey = key.substring(0, key.indexOf(".others"));
        }

        if (!cfg.getBoolean("commands." + baseKey + ".enabled", true)) {
            player.sendMessage(getMessage("module-disabled"));
            return false;
        }
        if (others && !cfg.getBoolean("commands." + baseKey + ".others-enabled", true)) {
            player.sendMessage(getMessage("module-disabled"));
            return false;
        }

        String perm = others ? "valerinutils.utility." + baseKey + ".others" : "valerinutils.utility." + baseKey;
        if (!player.hasPermission(perm)) {
            player.sendMessage(getMessage("no-permission"));
            return false;
        }
        return true;
    }

    void playSound(Player player, String key) {
        FileConfiguration cfg = getConfig();
        if (cfg == null)
            return;
        String soundName = cfg.getString("sounds." + key);
        if (soundName != null) {
            try {
                player.playSound(player.getLocation(), Sound.valueOf(soundName.toUpperCase(Locale.ROOT)), 1f, 1f);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid sound in utilities.yml: " + soundName);
            }
        }
    }

    String getMessage(String key) {
        String fallback = DEFAULT_MESSAGES.getOrDefault(key, "%prefix%<red>Mensaje faltante: " + key);
        String resolved = plugin.messages().module(getId(), key, fallback);
        if (resolved == null || resolved.isBlank()) {
            plugin.debug(getId(), "Mensaje vacío detectado para key='" + key + "', usando fallback.");
            return plugin.translateColors(fallback);
        }
        return resolved;
    }

    private void setupCondenseMap() {
        condenseMap.put(Material.DIAMOND, Material.DIAMOND_BLOCK);
        condenseMap.put(Material.IRON_INGOT, Material.IRON_BLOCK);
        condenseMap.put(Material.GOLD_INGOT, Material.GOLD_BLOCK);
        condenseMap.put(Material.EMERALD, Material.EMERALD_BLOCK);
        condenseMap.put(Material.REDSTONE, Material.REDSTONE_BLOCK);
        condenseMap.put(Material.LAPIS_LAZULI, Material.LAPIS_BLOCK);
        condenseMap.put(Material.COAL, Material.COAL_BLOCK);
        condenseMap.put(Material.COPPER_INGOT, Material.COPPER_BLOCK);
        condenseMap.put(Material.RAW_IRON, Material.RAW_IRON_BLOCK);
        condenseMap.put(Material.RAW_GOLD, Material.RAW_GOLD_BLOCK);
        condenseMap.put(Material.RAW_COPPER, Material.RAW_COPPER_BLOCK);
        condenseMap.put(Material.SLIME_BALL, Material.SLIME_BLOCK);
        condenseMap.put(Material.WHEAT, Material.HAY_BLOCK);
        condenseMap.put(Material.IRON_NUGGET, Material.IRON_INGOT);
        condenseMap.put(Material.GOLD_NUGGET, Material.GOLD_INGOT);
    }
}
