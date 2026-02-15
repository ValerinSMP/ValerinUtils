package me.mtynnn.valerinutils.modules.utility;

import net.kyori.adventure.text.Component;
import me.mtynnn.valerinutils.core.PlayerData;
import org.bukkit.Material;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.attribute.Attribute;
import java.util.stream.Collectors;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.Listener;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.event.Listener;

public class UtilityModule implements Module, CommandExecutor, Listener {

    private final ValerinUtils plugin;
    private final Map<Material, Material> condenseMap = new HashMap<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

    private static class DisposalHolder implements InventoryHolder {
        @Override
        public @NotNull Inventory getInventory() {
            return null;
        }
    }

    public UtilityModule(ValerinUtils plugin) {
        this.plugin = plugin;
        setupCondenseMap();
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

    @Override
    public String getId() {
        return "utility";
    }

    @Override
    public void enable() {
        FileConfiguration cfg = getConfig();
        if (cfg == null || !cfg.getBoolean("enabled", true))
            return;

        String[] cmds = {
                "craft", "enderchest", "anvil", "smithingtable",
                "cartographytable", "grindstone", "loom", "stonecutter",
                "disposal",
                "hat", "condense", "seen", "clear", "gmc", "gms", "gmsp", "gma", "ping",
                "fly", "speed", "broadcast", "heal", "feed", "repair", "nick", "skull", "suicide", "near", "top"
        };
        for (String c : cmds)
            registerCommand(c);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void registerCommand(String name) {
        if (plugin.getCommand(name) != null) {
            plugin.getCommand(name).setExecutor(this);
        }
    }

    @Override
    public void disable() {
        HandlerList.unregisterAll(this);
    }

    private FileConfiguration getConfig() {
        return plugin.getConfigManager().getConfig("utilities");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        String cmdName = command.getName().toLowerCase();

        if (!(sender instanceof Player)) {
            if (cmdName.equals("seen") && args.length > 0)
                handleSeen(sender, args[0]);
            else
                sender.sendMessage(getMessage("only-players"));
            return true;
        }

        Player player = (Player) sender;
        if (cmdName.startsWith("gm")) {
            handleGameMode(player, cmdName);
            return true;
        }

        switch (cmdName) {
            case "craft", "workbench", "wv" -> openUI(player, "craft");
            case "enderchest", "ec" -> openUI(player, "enderchest");
            case "anvil" -> openUI(player, "anvil");
            case "smithingtable", "st" -> openUI(player, "smithing");
            case "cartographytable", "ct" -> openUI(player, "cartography");
            case "grindstone" -> openUI(player, "grindstone");
            case "loom" -> openUI(player, "loom");
            case "stonecutter" -> openUI(player, "stonecutter");
            case "disposal", "trash", "basurero", "diposal" -> openDisposal(player);
            case "hat" -> handleHat(player);
            case "condense" -> handleCondense(player);
            case "seen" -> {
                if (args.length > 0)
                    handleSeen(player, args[0]);
                else
                    player.sendMessage(plugin.translateColors("%prefix%&7Uso: &e/seen <jugador>"));
            }
            case "clear" -> handleClear(player, args);
            case "ping" -> handlePing(player, args);
            case "fly" -> handleFly(player, args);
            case "speed" -> handleSpeed(player, args);
            case "broadcast" -> handleBroadcast(player, args);
            case "heal" -> handleHeal(player, args);
            case "feed" -> handleFeed(player, args);
            case "repair" -> handleRepair(player, args);
            case "nick" -> handleNick(player, args);
            case "skull" -> handleSkull(player, args);
            case "suicide" -> handleSuicide(player, args);
            case "near" -> handleNear(player, args);
            case "top" -> handleTop(player, args);
        }
        return true;
    }

    private void openDisposal(Player player) {
        if (!checkStatus(player, "disposal"))
            return;

        FileConfiguration cfg = getConfig();
        String title = cfg != null ? cfg.getString("messages.disposal-title", "&8Basurero") : "&8Basurero";
        Inventory inv = Bukkit.createInventory(new DisposalHolder(), 54, plugin.translateColors(title));
        player.openInventory(inv);
        playSound(player, "disposal");
    }

    @EventHandler
    public void onDisposalClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof DisposalHolder) {
            event.getInventory().clear();
        }
    }

    private void handlePing(Player player, String[] args) {
        Player target = player;
        if (args.length > 0) {
            if (!checkStatus(player, "ping-others"))
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

        boolean novel = !target.getAllowFlight();
        target.setAllowFlight(novel);
        if (novel)
            target.setFlying(true);

        String state = novel ? "Activado" : "Desactivado";
        if (target == player) {
            player.sendMessage(getMessage(novel ? "fly-enabled" : "fly-disabled"));
        } else {
            player.sendMessage(getMessage("fly-others")
                    .replace("%player%", target.getName())
                    .replace("%state%", state));
            target.sendMessage(getMessage(novel ? "fly-enabled" : "fly-disabled"));
        }
    }

    private void handleSpeed(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage(plugin.translateColors("%prefix%&7Uso: &e/speed <1-10> [jugador]"));
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

        // Standard speed is 0.2 walk, 0.1 fly. Range 1-10.
        // Formula: speed / 10.0
        float finalSpeed = speed / 10.0f;
        boolean isFlying = target.isFlying() || target.getAllowFlight();
        String type = isFlying ? "vuelo" : "caminata";

        if (isFlying)
            target.setFlySpeed(finalSpeed);
        else
            target.setWalkSpeed(finalSpeed);

        String msgKey = (target == player) ? "speed-success" : "speed-others";
        player.sendMessage(getMessage(msgKey)
                .replace("%player%", target.getName())
                .replace("%type%", type)
                .replace("%speed%", String.valueOf((int) speed)));
    }

    private void handleBroadcast(Player player, String[] args) {
        if (!checkStatus(player, "broadcast"))
            return;
        if (args.length == 0) {
            player.sendMessage(plugin.translateColors("%prefix%&7Uso: &e/broadcast <mensaje>"));
            return;
        }

        String message = String.join(" ", args);
        String formatted = getMessage("broadcast-format").replace("%message%", message);
        Bukkit.broadcast(plugin.parseComponent(formatted));
        for (Player p : Bukkit.getOnlinePlayers()) {
            playSound(p, "broadcast");
        }
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
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType().isAir() || item.getType().getMaxDurability() <= 0) {
            player.sendMessage(getMessage("repair-error"));
            return;
        }

        org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) item.getItemMeta();
        damageable.setDamage(0);
        item.setItemMeta(damageable);
        player.sendMessage(getMessage("repair-success"));
        playSound(player, "condense"); // Reuse sound
    }

    private void handleNick(Player player, String[] args) {
        if (!checkStatus(player, "nick"))
            return;
        if (args.length == 0) {
            player.sendMessage(plugin.translateColors("%prefix%&7Uso: &e/nick <apodo|off>"));
            return;
        }

        PlayerData pd = plugin.getPlayerData(player.getUniqueId());
        if (pd == null)
            return;

        if (args[0].equalsIgnoreCase("off")) {
            pd.setNickname(null);
            player.displayName(Component.text(player.getName()));
            player.playerListName(Component.text(player.getName()));
            player.sendMessage(getMessage("nick-off"));
        } else {
            String nickRaw = args[0];
            pd.setNickname(nickRaw); // Save raw with & codes
            Component nickComp = plugin.parseComponent(nickRaw);
            player.displayName(nickComp);
            player.playerListName(nickComp);
            player.sendMessage(getMessage("nick-success").replace("%nick%", plugin.translateColors(nickRaw)));
        }
    }

    private void handleSkull(Player player, String[] args) {
        if (!checkStatus(player, "skull"))
            return;
        String name = args.length > 0 ? args[0] : player.getName();

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(name));
        meta.displayName(plugin.parseComponent("&eCabeza de " + name));
        skull.setItemMeta(meta);

        player.getInventory().addItem(skull);
        player.sendMessage(getMessage("skull-success").replace("%player%", name));
    }

    private void handleSuicide(Player player, String[] args) {
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
                .filter(e -> e instanceof Player)
                .map(e -> ((Player) e).getName() + " (&e" + (int) e.getLocation().distance(player.getLocation())
                        + "m&f)")
                .collect(Collectors.joining("&7, &f"));

        if (players.isEmpty()) {
            player.sendMessage(getMessage("near-none"));
        } else {
            player.sendMessage(plugin.translateColors(getMessage("near-format")
                    .replace("%radius%", String.valueOf(r))
                    .replace("%players%", players)));
        }
    }

    private void handleTop(Player player, String[] args) {
        if (!checkStatus(player, "top"))
            return;
        player.teleport(player.getWorld().getHighestBlockAt(player.getLocation()).getLocation().add(0, 1, 0));
        player.sendMessage(getMessage("top-success"));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        PlayerData pd = plugin.getPlayerData(player.getUniqueId());
        if (pd != null && pd.getNickname() != null) {
            Component nickComp = plugin.parseComponent(pd.getNickname());
            player.displayName(nickComp);
            player.playerListName(nickComp);
        }
    }

    private void openUI(Player player, String configKey) {
        if (!checkStatus(player, configKey))
            return;

        playSound(player, configKey);
        switch (configKey) {
            case "craft":
                player.openWorkbench(null, true);
                break;
            case "enderchest":
                player.openInventory(player.getEnderChest());
                break;
            case "anvil":
                player.openAnvil(null, true);
                break;
            case "smithing":
                player.openSmithingTable(null, true);
                break;
            case "cartography":
                player.openCartographyTable(null, true);
                break;
            case "grindstone":
                player.openGrindstone(null, true);
                break;
            case "loom":
                player.openLoom(null, true);
                break;
            case "stonecutter":
                player.openStonecutter(null, true);
                break;
        }
    }

    private void handleGameMode(Player player, String cmd) {
        if (!checkStatus(player, "gamemode"))
            return;
        GameMode mode = null;
        switch (cmd) {
            case "gmc":
                mode = GameMode.CREATIVE;
                break;
            case "gms":
                mode = GameMode.SURVIVAL;
                break;
            case "gmsp":
                mode = GameMode.SPECTATOR;
                break;
            case "gma":
                mode = GameMode.ADVENTURE;
                break;
        }
        if (mode != null) {
            player.setGameMode(mode);
            playSound(player, "gamemode-change");
            player.sendMessage(getMessage("gamemode-success").replace("%mode%", mode.name()));
        }
    }

    private void handleHat(Player player) {
        if (!checkStatus(player, "hat"))
            return;
        PlayerInventory inv = player.getInventory();
        ItemStack hand = inv.getItemInMainHand();
        if (hand.getType() == Material.AIR)
            return;

        ItemStack head = inv.getHelmet();
        inv.setHelmet(hand);
        inv.setItemInMainHand(head);
        playSound(player, "hat-equip");
        player.sendMessage(getMessage("hat-success"));
    }

    private void handleCondense(Player player) {
        if (!checkStatus(player, "condense"))
            return;
        PlayerInventory inv = player.getInventory();
        int condensedCount = 0;

        for (Material source : condenseMap.keySet()) {
            int amount = 0;
            for (ItemStack is : inv.getContents()) {
                if (is != null && is.getType() == source)
                    amount += is.getAmount();
            }

            if (amount >= 9) {
                int toCondense = (amount / 9) * 9;
                int resultAmount = amount / 9;
                Material resultMat = condenseMap.get(source);

                inv.remove(source);
                int left = amount - toCondense;
                if (left > 0)
                    inv.addItem(new ItemStack(source, left));
                inv.addItem(new ItemStack(resultMat, resultAmount));
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
            if (!checkStatus(player, "clear-others"))
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
        if (target != player)
            player.sendMessage(getMessage("clear-success").replace("%player%", target.getName()));
        else
            player.sendMessage(getMessage("clear-success-self"));
    }

    private void handleSeen(CommandSender sender, String name) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (!op.hasPlayedBefore() && !op.isOnline()) {
            sender.sendMessage(getMessage("player-not-found"));
            return;
        }

        FileConfiguration cfg = getConfig();
        List<String> format = cfg.getStringList("messages.seen-format");
        if (format.isEmpty())
            return;

        Player p = op.isOnline() ? op.getPlayer() : null;
        String status = op.isOnline() ? getMessage("seen-online")
                : getMessage("seen-offline").replace("%time%",
                        formatTime(System.currentTimeMillis() - op.getLastPlayed()));

        for (String line : format) {
            String processed = line.replace("%player%", op.getName() != null ? op.getName() : "Desconocido")
                    .replace("%status%", status)
                    .replace("%uuid%", op.getUniqueId().toString())
                    .replace("%ip%",
                            (p != null && sender.hasPermission("valerinutils.utility.seen.ip"))
                                    ? p.getAddress().getAddress().getHostAddress()
                                    : "Oculta")
                    .replace("%first_join%", dateFormat.format(new Date(op.getFirstPlayed())))
                    .replace("%last_seen%", dateFormat.format(new Date(op.getLastPlayed())))
                    .replace("%world%",
                            p != null ? p.getWorld().getName()
                                    : (op.getLocation() != null ? op.getLocation().getWorld().getName() : "N/A"))
                    .replace("%x%",
                            String.valueOf(p != null ? p.getLocation().getBlockX()
                                    : (op.getLocation() != null ? op.getLocation().getBlockX() : 0)))
                    .replace("%y%",
                            String.valueOf(p != null ? p.getLocation().getBlockY()
                                    : (op.getLocation() != null ? op.getLocation().getBlockY() : 0)))
                    .replace("%z%",
                            String.valueOf(p != null ? p.getLocation().getBlockZ()
                                    : (op.getLocation() != null ? op.getLocation().getBlockZ() : 0)))
                    .replace("%health%", String.valueOf(p != null ? (int) p.getHealth() : 0))
                    .replace("%hunger%", String.valueOf(p != null ? p.getFoodLevel() : 0))
                    .replace("%xp%", String.valueOf(p != null ? p.getLevel() : 0))
                    .replace("%gamemode%", p != null ? p.getGameMode().name() : "OFFLINE")
                    .replace("%fly%", p != null ? (p.getAllowFlight() ? "&aSí" : "&cNo") : "OFFLINE");

            sender.sendMessage(ChatColor.translateAlternateColorCodes('&', processed));
        }
    }

    private String formatTime(long ms) {
        long seconds = ms / 1000 % 60;
        long minutes = ms / (1000 * 60) % 60;
        long hours = ms / (1000 * 60 * 60) % 24;
        long days = ms / (1000 * 60 * 60 * 24);
        if (days > 0)
            return days + "d " + hours + "h";
        if (hours > 0)
            return hours + "h " + minutes + "m";
        if (minutes > 0)
            return minutes + "m " + seconds + "s";
        return seconds + "s";
    }

    private boolean checkStatus(Player player, String key) {
        FileConfiguration cfg = getConfig();
        if (cfg == null || !cfg.getBoolean("enabled", true))
            return false;
        if (!cfg.getBoolean("commands." + key + ".enabled", true)) {
            player.sendMessage(getMessage("module-disabled"));
            return false;
        }
        String perm = cfg.getString("commands." + key + ".permission", "valerinutils.utility." + key);
        if (!player.hasPermission(perm)) {
            player.sendMessage(getMessage("no-permission"));
            return false;
        }
        return true;
    }

    private void playSound(Player player, String key) {
        FileConfiguration cfg = getConfig();
        if (cfg == null)
            return;
        String soundName = cfg.getString("sounds." + key);
        if (soundName != null) {
            try {
                player.playSound(player.getLocation(), Sound.valueOf(soundName.toUpperCase()), 1f, 1f);
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid sound in utilities.yml: " + soundName);
            }
        }
    }

    private String getMessage(String key) {
        FileConfiguration cfg = getConfig();
        if (cfg == null)
            return "";
        return plugin.translateColors(cfg.getString("messages." + key, ""));
    }
}
