package me.mtynnn.valerinutils.modules.utility;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.BaseModule;
import me.mtynnn.valerinutils.core.CommandHelpRenderer;
import me.mtynnn.valerinutils.core.PlayerData;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class UtilityModule extends BaseModule implements CommandExecutor, Listener {

    private final UtilityNickManager nickManager;
    private final UtilityWorkbenchCommands workbenchCommands;
    private final UtilityBroadcastCommand broadcastCommand;
    private final UtilityHelpOpCommand helpOpCommand;
    private final UtilitySeenCommand seenCommand;
    private final UtilityNickCommand nickCommand;
    private final UtilitySellCommand sellCommand;
    private final UtilityPersonalWorldCommands personalWorldCommands;

    private final Map<Material, Material> condenseMap = new HashMap<>();
    private Method nexoIdFromItemMethod;
    private boolean nexoLookupInitialized;

    private final Map<UUID, Long> healCooldowns = new HashMap<>();
    private final Map<UUID, Long> feedCooldowns = new HashMap<>();
    private final Map<UUID, Long> repairCooldowns = new HashMap<>();

    private static final String BYPASS_HEAL_COOLDOWN = "valerinutils.utility.heal.bypasscooldown";
    private static final String BYPASS_FEED_COOLDOWN = "valerinutils.utility.feed.bypasscooldown";
    private static final String BYPASS_REPAIR_COOLDOWN = "valerinutils.utility.repair.bypasscooldown";

    private static final String[] REGISTERED_COMMANDS = {
            "craft", "anvil", "smithingtable",
            "cartographytable", "grindstone", "loom", "stonecutter",
            "disposal",
            "hat", "condense", "seen", "clear", "gmc", "gms", "gmsp", "gma", "ping",
            "fly", "speed", "broadcast", "vubroadcast", "helpop", "heal", "feed", "repair", "nick", "skull", "suicide",
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
            Map.entry("nick-usage-others", "%prefix%<gray>Uso: <yellow>/nick <jugador> <apodo> <gray>o <yellow>/nick off <jugador>"),
            Map.entry("nick-success", "%prefix%<gray>Tu apodo ahora es: <white>%nick%"),
            Map.entry("nick-success-others", "%prefix%<gray>Apodo de <white>%player% <gray>actualizado a: <white>%nick%"),
            Map.entry("nick-off", "%prefix%<red>Has desactivado tu apodo."),
            Map.entry("nick-off-others", "%prefix%<gray>Has quitado el apodo de <white>%player%<gray>."),
            Map.entry("nick-no-spaces", "%prefix%<red>El nick no puede contener espacios."),
            Map.entry("nick-too-long", "%prefix%<red>El nick no puede tener más de <yellow>16 <red>caracteres visibles."),
            Map.entry("nick-too-short", "%prefix%<red>El nick debe tener al menos <yellow>%min% <red>caracteres visibles."),
            Map.entry("nick-impersonation", "%prefix%<red>No puedes usar el nombre de otro jugador conectado."),
            Map.entry("nick-format-not-allowed", "%prefix%<red>No puedes usar ese formato en el nick. <gray>Tu nivel es <yellow>%tier%<gray>. Permitido: <yellow>%allowed%<gray>. Ejemplos: <yellow>&aNombre <gray>o <yellow><red>Nombre<gray>. <red>No se permite texto obfuscado para evitar nombres ilegibles."),
            Map.entry("gamemode-success", "%prefix%<green>Modo de juego cambiado a <yellow>%mode%<green>."),
            Map.entry("gamemode-success-others", "%prefix%<green>Modo de juego de <white>%player% <green>cambiado a <yellow>%mode%<green>."),
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
            Map.entry("sell-enchanted", "%prefix%<red>No puedes vender items encantados."),
            Map.entry("sell-damaged", "%prefix%<red>No puedes vender items dañados. Deben estar en perfecto estado."),
            Map.entry("sell-custom", "%prefix%<red>No puedes vender items personalizados (custom)."),
            Map.entry("broadcast-usage", "%prefix%<gray>Uso: <yellow>/broadcast <mensaje> <gray>o <yellow>/vubroadcast <mensaje>"),
            Map.entry("helpop-usage", "%prefix%<gray>Uso: <yellow>/helpop <mensaje>"),
            Map.entry("helpop-sent", "%prefix%<green>Tu reporte fue enviado al staff <gray>(<white>%staff%<gray>)."),
            Map.entry("helpop-no-staff", "%prefix%<yellow>No hay staff conectado ahora. Tu mensaje fue enviado a consola."),
            Map.entry("helpop-cooldown", "%prefix%<red>Espera <yellow>%time%s <red>antes de volver a usar /helpop."),
            Map.entry("heal-cooldown", "%prefix%<red>Espera <yellow>%time%s <red>antes de volver a usar /heal."),
            Map.entry("feed-cooldown", "%prefix%<red>Espera <yellow>%time%s <red>antes de volver a usar /feed."),
            Map.entry("repair-cooldown", "%prefix%<red>Espera <yellow>%time%s <red>antes de volver a usar /fix."));

    public UtilityModule(ValerinUtils plugin) {
        super(plugin);
        this.nickManager = new UtilityNickManager();
        this.workbenchCommands = new UtilityWorkbenchCommands(this);
        this.broadcastCommand = new UtilityBroadcastCommand(this);
        this.helpOpCommand = new UtilityHelpOpCommand(this);
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
    public Set<String> getCommandNames() {
        return Set.of(REGISTERED_COMMANDS);
    }

    @Override
    protected void onEnableModule() {
        FileConfiguration cfg = getConfig();
        if (cfg == null || !cfg.getBoolean("enabled", true))
            return;

        for (String command : REGISTERED_COMMANDS) {
            // Skip disabled commands
            if (!isCommandEnabled(command)) {
                plugin.getLogger().info("[Utility] Skipped disabled command: " + command);
                continue;
            }
            registerCommand(command, this);
        }

        registerListener(this);

        for (Player player : Bukkit.getOnlinePlayers()) {
            applyStoredNickname(player);
        }
    }

    @Override
    protected void onDisableModule() {
        try {
            HandlerList.unregisterAll(this);
        } catch (Exception ignored) {}
        
        // Clear cooldown maps
        healCooldowns.clear();
        feedCooldowns.clear();
        repairCooldowns.clear();
        helpOpCommand.clearAllCooldowns();

        // Clear condense map
        condenseMap.clear();
        
        // Close any open disposal inventories
        try {
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof UtilityDisposalHolder) {
                    player.closeInventory();
                }
            }
        } catch (Exception ignored) {}
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
            } else if (cmdName.startsWith("gm")) {
                handleGameMode(sender, null, cmdName, args);
            } else if (cmdName.equals("nick")) {
                nickCommand.execute(sender, args);
            } else {
                sender.sendMessage(getMessage("only-players"));
            }
            return true;
        }

        if (cmdName.startsWith("gm")) {
            handleGameMode(player, player, cmdName, args);
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
            sendMessageLines(player, "seen-usage");
                }
            }
            case "clear" -> handleClear(player, args);
            case "ping" -> handlePing(player, args);
            case "fly" -> handleFly(player, args);
            case "speed" -> handleSpeed(player, args);
            case "broadcast", "vubroadcast" -> broadcastCommand.execute(player, args);
            case "helpop" -> helpOpCommand.execute(player, args);
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
        applyStoredNickname(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        healCooldowns.remove(uuid);
        feedCooldowns.remove(uuid);
        repairCooldowns.remove(uuid);
        helpOpCommand.clearCooldown(uuid);
    }

    private void applyStoredNickname(Player player) {
        PlayerData playerData = plugin.getPlayerData(player.getUniqueId());
        if (playerData == null || playerData.getNickname() == null) {
            return;
        }

        String sanitized = nickManager.sanitizeStoredNickname(playerData.getNickname());
        UtilityNickManager.NickTier tier = nickManager.resolveTier(player);
        if (sanitized == null || !nickManager.isFormatAllowed(sanitized, tier)) {
            playerData.setNickname(null);
            player.displayName(Component.text(player.getName()));
            player.playerListName(Component.text(player.getName()));
            return;
        }

        if (!sanitized.equals(playerData.getNickname())) {
            playerData.setNickname(sanitized);
        }

        Component nickComp = plugin.parseComponent(sanitized);
        player.displayName(nickComp);
        player.playerListName(nickComp);
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
            sendMessageLines(player, "speed-usage");
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
        if (!isCommandEnabled("heal")) {
            // Silently ignore disabled command - let other plugins handle it
            return;
        }

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
            int cd = Math.max(0, getConfig().getInt("commands.heal.cooldown-seconds", 0));
            if (cd > 0 && !player.hasPermission(BYPASS_HEAL_COOLDOWN)) {
                long now = System.currentTimeMillis();
                Long nextUse = healCooldowns.get(player.getUniqueId());
                if (nextUse != null && nextUse > now) {
                    long left = Math.max(1L, (nextUse - now + 999L) / 1000L);
                    player.sendMessage(getMessage("heal-cooldown").replace("%time%", String.valueOf(left)));
                    return;
                }
                healCooldowns.put(player.getUniqueId(), now + (cd * 1000L));
            }
        }

        target.setHealth(target.getAttribute(Attribute.MAX_HEALTH).getValue());
        target.setFoodLevel(20);
        target.setFireTicks(0);

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
            int cd = Math.max(0, getConfig().getInt("commands.feed.cooldown-seconds", 0));
            if (cd > 0 && !player.hasPermission(BYPASS_FEED_COOLDOWN)) {
                long now = System.currentTimeMillis();
                Long nextUse = feedCooldowns.get(player.getUniqueId());
                if (nextUse != null && nextUse > now) {
                    long left = Math.max(1L, (nextUse - now + 999L) / 1000L);
                    player.sendMessage(getMessage("feed-cooldown").replace("%time%", String.valueOf(left)));
                    return;
                }
                feedCooldowns.put(player.getUniqueId(), now + (cd * 1000L));
            }
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
        if (!isCommandEnabled("repair")) {
            // Silently ignore disabled command - let other plugins handle it
            return;
        }

        if (!checkStatus(player, "repair"))
            return;
        if (args.length > 0 && !args[0].equalsIgnoreCase("hand")) {
            sendMessageLines(player, "repair-usage");
            return;
        }
        int cd = Math.max(0, getConfig().getInt("commands.repair.cooldown-seconds", 0));
        if (cd > 0 && !player.hasPermission(BYPASS_REPAIR_COOLDOWN)) {
            long now = System.currentTimeMillis();
            Long nextUse = repairCooldowns.get(player.getUniqueId());
            if (nextUse != null && nextUse > now) {
                long left = Math.max(1L, (nextUse - now + 999L) / 1000L);
                player.sendMessage(getMessage("repair-cooldown").replace("%time%", String.valueOf(left)));
                return;
            }
            repairCooldowns.put(player.getUniqueId(), now + (cd * 1000L));
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
            .map(entity -> (Player) entity)
            .filter(target -> isVisibleForNear(player, target))
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

    private boolean isVisibleForNear(Player viewer, Player target) {
        if (target == null || !target.isOnline() || target.equals(viewer)) {
            return false;
        }
        if (target.getGameMode() == GameMode.SPECTATOR) {
            return false;
        }
        // Respect vanish/hidden state managed by other plugins.
        return viewer.canSee(target);
    }

    private void handleTop(Player player) {
        if (!checkStatus(player, "top"))
            return;
        player.teleport(player.getWorld().getHighestBlockAt(player.getLocation()).getLocation().add(0, 1, 0));
        player.sendMessage(getMessage("top-success"));
    }

    private void handleGameMode(CommandSender sender, Player selfPlayer, String cmd, String[] args) {
        GameMode mode = switch (cmd) {
            case "gmc" -> GameMode.CREATIVE;
            case "gms" -> GameMode.SURVIVAL;
            case "gmsp" -> GameMode.SPECTATOR;
            case "gma" -> GameMode.ADVENTURE;
            default -> null;
        };
        if (mode == null) {
            return;
        }

        Player target = selfPlayer;
        boolean others = args.length > 0;
        if (selfPlayer == null && !others) {
            sender.sendMessage(getMessage("player-not-found"));
            return;
        }
        if (others) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(getMessage("player-not-found"));
                return;
            }
        }

        if (selfPlayer != null && (!others || target == selfPlayer)) {
            if (!checkStatus(selfPlayer, "gamemode")) {
                return;
            }
        } else {
            if (!checkStatusSender(sender, "gamemode", true)) {
                return;
            }
        }

        target.setGameMode(mode);
        playSound(target, "gamemode-change");

        if (selfPlayer != null && target == selfPlayer) {
            selfPlayer.sendMessage(getMessage("gamemode-success").replace("%mode%", mode.name()));
        } else {
            sender.sendMessage(getMessage("gamemode-success-others")
                    .replace("%player%", target.getName())
                    .replace("%mode%", mode.name()));
            target.sendMessage(getMessage("gamemode-success").replace("%mode%", mode.name()));
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
            int eligibleAmount = 0;
            List<Integer> eligibleSlots = new ArrayList<>();
            ItemStack[] contents = inventory.getContents();

            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack stack = contents[slot];
                if (stack != null && stack.getType() == source && !isNexoCustomItem(stack)) {
                    eligibleAmount += stack.getAmount();
                    eligibleSlots.add(slot);
                }
            }

            if (eligibleAmount > 0) {
                debug("condense: " + source + " eligibleAmount=" + eligibleAmount + " (necesita >=9)");
            }

            if (eligibleAmount >= 9) {
                int toCondense = (eligibleAmount / 9) * 9;
                int resultAmount = toCondense / 9;
                Material resultMat = condenseMap.get(source);
                int remainingToConsume = toCondense;

                for (int slot : eligibleSlots) {
                    if (remainingToConsume <= 0) {
                        break;
                    }

                    ItemStack stack = contents[slot];
                    if (stack == null) {
                        continue;
                    }

                    int stackAmount = stack.getAmount();
                    if (stackAmount <= remainingToConsume) {
                        contents[slot] = null;
                        remainingToConsume -= stackAmount;
                    } else {
                        stack.setAmount(stackAmount - remainingToConsume);
                        remainingToConsume = 0;
                    }
                }

                inventory.setContents(contents);
                Map<Integer, ItemStack> leftover = inventory.addItem(new ItemStack(resultMat, resultAmount));
                for (ItemStack overflow : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), overflow);
                }
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

    private boolean isNexoCustomItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }

        initializeNexoLookup();
        if (nexoIdFromItemMethod == null) {
            return false;
        }

        try {
            Object id = nexoIdFromItemMethod.invoke(null, stack);
            boolean custom = id instanceof String stringId && !stringId.isBlank();
            if (custom) {
                debug("condense: excluido " + stack.getType() + " x" + stack.getAmount()
                        + " por ser item Nexo (id=" + id + ")");
            }
            return custom;
        } catch (Throwable t) {
            debug("condense: fallo lookup Nexo para " + stack.getType() + ": " + t.getMessage());
            return false;
        }
    }

    private void initializeNexoLookup() {
        if (nexoLookupInitialized) {
            return;
        }
        nexoLookupInitialized = true;

        if (!Bukkit.getPluginManager().isPluginEnabled("Nexo")) {
            return;
        }

        try {
            Class<?> nexoItems = Class.forName("com.nexomc.nexo.api.NexoItems");
            nexoIdFromItemMethod = nexoItems.getMethod("idFromItem", ItemStack.class);
            debug("Nexo hook detectado para proteger items custom en /condense.");
        } catch (Throwable throwable) {
            nexoIdFromItemMethod = null;
            debug("No se pudo inicializar hook de Nexo para /condense: " + throwable.getMessage());
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
        return plugin.getConfigManager().getConfig(getId());
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

        if (!hasUtilityPermission(player, baseKey, others)) {
            player.sendMessage(getMessage("no-permission"));
            debug("Permiso denegado para " + player.getName() + " en " + baseKey
                    + " (others=" + others + ").");
            return false;
        }
        return true;
    }

    boolean checkStatusSender(CommandSender sender, String key, boolean others) {
        FileConfiguration cfg = getConfig();
        if (cfg == null || !cfg.getBoolean("enabled", true)) {
            return false;
        }

        if (!cfg.getBoolean("commands." + key + ".enabled", true)) {
            sender.sendMessage(getMessage("module-disabled"));
            return false;
        }
        if (others && !cfg.getBoolean("commands." + key + ".others-enabled", true)) {
            sender.sendMessage(getMessage("module-disabled"));
            return false;
        }

        if (!hasUtilityPermission(sender, key, others)) {
            sender.sendMessage(getMessage("no-permission"));
            return false;
        }
        return true;
    }

    private boolean hasUtilityPermission(CommandSender sender, String key, boolean others) {
        String suffix = others ? ".others" : "";
        String permissionNode = "valerinutils.utility." + key + suffix;
        return sender.hasPermission(permissionNode);
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

    void sendMessageLines(CommandSender sender, String key) {
        if (sendInteractiveUsage(sender, key)) {
            return;
        }
        FileConfiguration config = getConfig();
        List<String> lines = config == null
                ? List.of()
                : config.getStringList("messages." + key);
        if (lines.isEmpty()) {
            sender.sendMessage(getMessage(key));
            return;
        }
        for (String line : lines) {
            sender.sendMessage(comp(line));
        }
    }

    private boolean sendInteractiveUsage(CommandSender sender, String key) {
        List<CommandHelpRenderer.Entry> entries;
        String title;
        Component footer = null;
        switch (key) {
            case "speed-usage" -> {
                title = "Velocidad";
                entries = List.of(
                        help("/speed (1-10)", "Ajustar tu velocidad", "/speed "),
                        help("/speed (1-10) (jugador)", "Ajustar velocidad ajena", "/speed "));
            }
            case "broadcast-usage" -> {
                title = "Anuncios";
                entries = List.of(
                        help("/broadcast (mensaje)", "Enviar un anuncio global", "/broadcast "),
                        help("/vubroadcast (mensaje)", "Usar el comando alternativo", "/vubroadcast "));
            }
            case "helpop-usage" -> {
                title = "Ayuda";
                entries = List.of(help(
                        "/helpop (mensaje)", "Contactar al staff", "/helpop "));
            }
            case "repair-usage" -> {
                title = "Reparación";
                entries = List.of(help(
                        "/fix hand", "Reparar el objeto de tu mano", "/fix hand"));
            }
            case "nick-usage" -> {
                title = "Apodo";
                entries = List.of(
                        help("/nick (apodo)", "Cambiar tu apodo", "/nick "),
                        help("/nick off", "Quitar tu apodo", "/nick off"));
            }
            case "nick-usage-others" -> {
                title = "Apodo administrativo";
                entries = List.of(
                        help("/nick (jugador) (apodo)", "Cambiar un apodo ajeno", "/nick "),
                        help("/nick (jugador) off", "Quitar un apodo ajeno", "/nick "));
            }
            case "ptime-usage" -> {
                title = "Tiempo personal";
                entries = List.of(
                        help("/ptime day", "Establecer el día", "/ptime day"),
                        help("/ptime night", "Establecer la noche", "/ptime night"),
                        help("/ptime reset", "Restaurar el tiempo del servidor", "/ptime reset"));
            }
            case "pweather-usage" -> {
                title = "Clima personal";
                entries = List.of(
                        help("/pweather clear", "Establecer clima despejado", "/pweather clear"),
                        help("/pweather rain", "Establecer lluvia", "/pweather rain"),
                        help("/pweather reset", "Restaurar el clima del servidor", "/pweather reset"));
            }
            case "sell-usage" -> {
                title = "Ventas";
                entries = List.of(
                        help("/sell hand", "Vender el objeto de tu mano", "/sell hand"),
                        help("/sell inventory", "Vender tu inventario", "/sell inventory"));
                footer = Component.text(
                        "Solo se venden objetos vanilla sin encantamientos, daño ni personalización.",
                        TextColor.fromHexString("#FFC43B"));
            }
            case "seen-usage" -> {
                title = "Información";
                entries = List.of(help(
                        "/seen (jugador)", "Consultar la última conexión", "/seen "));
            }
            default -> {
                return false;
            }
        }
        CommandHelpRenderer.send(sender, title, entries, footer);
        return true;
    }

    private CommandHelpRenderer.Entry help(String command, String description, String suggestion) {
        return CommandHelpRenderer.Entry.of(command, description, suggestion);
    }

    public boolean isCommandEnabled(String commandName) {
        FileConfiguration cfg = getConfig();
        if (cfg == null) return true;
        return cfg.getBoolean("commands." + commandSettingKey(commandName) + ".enabled", true);
    }

    static String commandSettingKey(String commandName) {
        return switch (commandName.toLowerCase(Locale.ROOT)) {
            case "smithingtable" -> "smithing";
            case "cartographytable" -> "cartography";
            case "gmc", "gms", "gmsp", "gma" -> "gamemode";
            case "vubroadcast" -> "broadcast";
            case "vtop" -> "top";
            default -> commandName.toLowerCase(Locale.ROOT);
        };
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
