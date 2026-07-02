package me.mtynnn.valerinutils.modules.vouchers;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.BaseModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.query.QueryOptions;
import net.luckperms.api.track.Track;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;

import java.net.URL;
import java.util.Base64;
import java.util.UUID;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class VouchersModule extends BaseModule implements Listener, CommandExecutor, TabCompleter {

    private final NamespacedKey voucherKey;
    private final NamespacedKey guiActionKey;
    private final NamespacedKey guiTypeKey;

    private final Map<String, VoucherType> voucherTypes = new HashMap<>();
    private String guiTitle = "<dark_gray>Confirmar canje";
    private int guiSize = 27;
    private int confirmSlot = 11;
    private int cancelSlot = 15;
    private GuiButton confirmButton;
    private GuiButton cancelButton;
    private LuckPerms luckPerms;

    public VouchersModule(ValerinUtils plugin) {
        super(plugin);
        this.voucherKey = new NamespacedKey(plugin, "voucher_type");
        this.guiActionKey = new NamespacedKey(plugin, "voucher_gui_action");
        this.guiTypeKey = new NamespacedKey(plugin, "voucher_gui_type");
    }

    @Override
    public String getId() {
        return "vouchers";
    }

    @Override
    protected void onEnableModule() {
        if (!isEnabledInConfig()) {
            return;
        }
        loadConfigData();
        if (Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                luckPerms = LuckPermsProvider.get();
            } catch (Throwable ignored) {
                luckPerms = null;
            }
        }
        registerListener(this);
        registerCommand("voucher", this, this);
        plugin.getLogger().info("[Vouchers] Modulo habilitado con " + voucherTypes.size() + " tipos.");
    }

    @Override
    protected void onDisableModule() {
        voucherTypes.clear();
        luckPerms = null;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        ItemStack item = event.getItem();
        String voucherType = getVoucherType(item);
        if (voucherType == null) {
            return;
        }

        VoucherType type = voucherTypes.get(voucherType);
        if (type == null) {
            return;
        }

        event.setCancelled(true);
        openConfirmGui(event.getPlayer(), type);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) {
            return;
        }
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String action = pdc.get(guiActionKey, PersistentDataType.STRING);
        if (action == null) {
            return;
        }
        String voucherType = pdc.get(guiTypeKey, PersistentDataType.STRING);
        if (voucherType == null) {
            return;
        }
        event.setCancelled(true);

        VoucherType type = voucherTypes.get(voucherType);
        if (type == null) {
            player.closeInventory();
            player.sendMessage(comp(msg("messages.invalid-voucher-type",
                    "%prefix%<red>Este voucher ya no es valido.")));
            return;
        }

        if ("confirm".equals(action)) {
            player.closeInventory();
            redeemVoucher(player, type, voucherType);
            return;
        }

        player.closeInventory();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(comp(msg("messages.usage",
                    "%prefix%<gray>Uso: <yellow>/voucher give <jugador> <tipo> [cantidad] <gray>| <yellow>/voucher reload")));
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            if (!hasPermission(sender, "valerinutils.vouchers.reload")) {
                sender.sendMessage(comp(msg("messages.no-permission", "%prefix%<red>No tienes permiso.")));
                return true;
            }
            if (!plugin.getConfigManager().reloadConfig("vouchers")) {
                sender.sendMessage(comp(msg("messages.reload-failed",
                        "%prefix%<red>No se pudo recargar vouchers.yml")));
                return true;
            }
            loadConfigData();
            sender.sendMessage(comp(msg("messages.reload-ok",
                    "%prefix%<green>Vouchers recargado correctamente.")));
            return true;
        }

        if ("give".equalsIgnoreCase(args[0])) {
            if (!hasPermission(sender, "valerinutils.vouchers.give")) {
                sender.sendMessage(comp(msg("messages.no-permission", "%prefix%<red>No tienes permiso.")));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(comp(msg("messages.give-usage",
                        "%prefix%<gray>Uso: <yellow>/voucher give <jugador> <tipo> [cantidad]")));
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(comp(msg("messages.player-not-found",
                        "%prefix%<red>Jugador no encontrado.")));
                return true;
            }

            String typeId = args[2].toLowerCase(Locale.ROOT);
            VoucherType type = voucherTypes.get(typeId);
            if (type == null) {
                sender.sendMessage(comp(msg("messages.unknown-type",
                        "%prefix%<red>Tipo de voucher desconocido: <white>%type%").replace("%type%", typeId)));
                return true;
            }

            int amount = 1;
            if (args.length >= 4) {
                try {
                    amount = Math.max(1, Integer.parseInt(args[3]));
                } catch (NumberFormatException ignored) {
                    amount = 1;
                }
            }

            ItemStack stack = buildVoucherItem(type, amount);
            Map<Integer, ItemStack> leftovers = target.getInventory().addItem(stack);
            if (!leftovers.isEmpty()) {
                leftovers.values().forEach(left -> target.getWorld().dropItemNaturally(target.getLocation(), left));
            }

            sender.sendMessage(comp(msg("messages.give-ok-sender",
                    "%prefix%<green>Entregado <white>%amount%x %voucher% <green>a <white>%player%")
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%voucher%", type.displayName)
                    .replace("%player%", target.getName())));
            target.sendMessage(comp(msg("messages.give-ok-target",
                    "%prefix%<green>Has recibido <white>%amount%x %voucher%")
                    .replace("%amount%", String.valueOf(amount))
                    .replace("%voucher%", type.displayName)));
            return true;
        }

        sender.sendMessage(comp(msg("messages.usage",
                "%prefix%<gray>Uso: <yellow>/voucher give <jugador> <tipo> [cantidad] <gray>| <yellow>/voucher reload")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>();
            if (hasPermission(sender, "valerinutils.vouchers.give")) {
                base.add("give");
            }
            if (hasPermission(sender, "valerinutils.vouchers.reload")) {
                base.add("reload");
            }
            String q = args[0].toLowerCase(Locale.ROOT);
            return base.stream().filter(s -> s.startsWith(q)).toList();
        }
        if (args.length == 2 && "give".equalsIgnoreCase(args[0])) {
            String q = args[1].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(q))
                    .sorted()
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && "give".equalsIgnoreCase(args[0])) {
            String q = args[2].toLowerCase(Locale.ROOT);
            return voucherTypes.keySet().stream()
                    .filter(id -> id.startsWith(q))
                    .sorted()
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void openConfirmGui(Player player, VoucherType type) {
        Inventory inv = Bukkit.createInventory(player, guiSize, comp(guiTitle.replace("%voucher%", type.displayName)));
        inv.setItem(confirmSlot, buildGuiButton(confirmButton, "confirm", type.id));
        inv.setItem(cancelSlot, buildGuiButton(cancelButton, "cancel", type.id));
        player.openInventory(inv);
    }

    private ItemStack buildGuiButton(GuiButton button, String action, String voucherType) {
        Material material = Material.matchMaterial(button.material);
        if (material == null) {
            material = Material.STONE;
        }
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(noItalic(comp(button.name)));
            List<Component> lore = button.lore.stream().map(this::comp).map(this::noItalic).toList();
            meta.lore(lore);
            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(guiActionKey, PersistentDataType.STRING, action);
            pdc.set(guiTypeKey, PersistentDataType.STRING, voucherType);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getVoucherType(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(voucherKey, PersistentDataType.STRING);
    }

    private boolean consumeOneVoucher(Player player, String voucherType) {
        ItemStack inHand = player.getInventory().getItemInMainHand();
        String handType = getVoucherType(inHand);
        if (voucherType.equals(handType)) {
            decrementStack(player.getInventory().getHeldItemSlot(), player);
            return true;
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            String type = getVoucherType(contents[i]);
            if (voucherType.equals(type)) {
                decrementStack(i, player);
                return true;
            }
        }
        return false;
    }

    private void decrementStack(int slot, Player player) {
        ItemStack stack = player.getInventory().getItem(slot);
        if (stack == null) {
            return;
        }
        if (stack.getAmount() <= 1) {
            player.getInventory().setItem(slot, null);
        } else {
            stack.setAmount(stack.getAmount() - 1);
            player.getInventory().setItem(slot, stack);
        }
        player.updateInventory();
    }

    private ItemStack buildVoucherItem(VoucherType type, int amount) {
        Material material = Material.matchMaterial(type.material);
        if (material == null) {
            material = Material.PAPER;
        }
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta instanceof SkullMeta skullMeta && material == Material.PLAYER_HEAD) {
                if (type.headTexture != null && !type.headTexture.isBlank()) {
                    applyHeadTexture(skullMeta, type.headTexture);
                } else if (type.headOwner != null && !type.headOwner.isBlank()) {
                    skullMeta.setOwner(type.headOwner);
                }
                meta = skullMeta;
            }
            if (type.customModelData > 0) {
                meta.setCustomModelData(type.customModelData);
            }
            meta.displayName(noItalic(comp(type.displayName)));
            List<Component> lore = type.lore.stream().map(this::comp).map(this::noItalic).toList();
            meta.lore(lore);
            meta.getPersistentDataContainer().set(voucherKey, PersistentDataType.STRING, type.id);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void applyHeadTexture(SkullMeta meta, String textureBase64) {
        try {
            String decoded = new String(Base64.getDecoder().decode(textureBase64));
            int urlStart = decoded.indexOf("\"url\":\"") + 7;
            int urlEnd = decoded.indexOf('"', urlStart);
            if (urlStart < 7 || urlEnd <= urlStart) return;
            URL skinUrl = new URL(decoded.substring(urlStart, urlEnd));
            PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID());
            profile.getTextures().setSkin(skinUrl);
            meta.setOwnerProfile(profile);
        } catch (Exception ignored) {}
    }

    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }

    private String formatHoursMinutes(long totalSeconds) {
        long safe = Math.max(0L, totalSeconds);
        long hours = safe / 3600L;
        long minutes = (safe % 3600L) / 60L;
        return hours + "h " + minutes + "m";
    }

    private void redeemVoucher(Player player, VoucherType type, String voucherTypeId) {
        if (type.lpStackingEnabled) {
            if (luckPerms == null) {
                player.sendMessage(comp(msg("messages.redeem-failed",
                        "%prefix%<red>No se pudo canjear el voucher.")));
                return;
            }
            CompletableFuture.runAsync(() -> {
                try {
                    User user = luckPerms.getUserManager().loadUser(player.getUniqueId()).join();
                    if (user == null) {
                        throw new IllegalStateException("User LP null");
                    }

                    if (type.lpTrack != null && !type.lpTrack.isBlank() && isGroupPermission(type.lpPermission)) {
                        String targetGroup = type.lpPermission.substring("group.".length());
                        int block = trackBlockReason(user, type.lpTrack, targetGroup, type.lpServer);
                        if (block != 0) {
                            String msgKey = block > 0 ? "messages.rank-too-high" : "messages.already-has-rank";
                            String def = block > 0
                                    ? "%prefix%<red>Ya tienes un rango superior a <white>%voucher%<red>."
                                    : "%prefix%<red>Ya tienes el rango <white>%voucher%<red>.";
                            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(comp(
                                    msg(msgKey, def).replace("%voucher%", type.displayName))));
                            return;
                        }
                    }

                    long now = System.currentTimeMillis() / 1000L;
                    long currentExpiry = findCurrentExpiry(user, type.lpPermission, type.lpServer, now);
                    long base = Math.max(now, currentExpiry);
                    long newExpiry = base + Math.max(1L, type.lpDurationSeconds);

                    removeTempNodes(user, type.lpPermission, type.lpServer);

                    Node node = buildLpNode(type.lpPermission, type.lpServer, newExpiry);
                    user.data().add(node);
                    luckPerms.getUserManager().saveUser(user);

                    long remainingSeconds = newExpiry - now;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!consumeOneVoucher(player, voucherTypeId)) {
                            // LP already updated — log warning but don't crash
                            plugin.getLogger().warning("[Vouchers] No se pudo consumir voucher " + voucherTypeId + " de " + player.getName() + " post-LP");
                        }
                        player.sendMessage(comp(
                                msg("messages.redeemed-extended",
                                        "%prefix%<green>Voucher canjeado: <white>%voucher% <green>(tiempo total: <white>%remaining%<green>)")
                                        .replace("%voucher%", type.displayName)
                                        .replace("%remaining%", formatHoursMinutes(remainingSeconds))
                        ));
                    });
                } catch (Throwable ex) {
                    Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(comp(
                            msg("messages.redeem-failed", "%prefix%<red>No se pudo canjear el voucher.")
                    )));
                }
            });
            return;
        }

        if (!consumeOneVoucher(player, voucherTypeId)) {
            player.sendMessage(comp(msg("messages.voucher-not-found",
                    "%prefix%<red>No tienes ese voucher en la mano.")));
            return;
        }
        String command = type.redeemCommand.replace("%player%", player.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        player.sendMessage(comp(msg("messages.redeemed",
                "%prefix%<green>Voucher canjeado: <white>%voucher%")
                .replace("%voucher%", type.displayName)));
    }

    private boolean isGroupPermission(String permission) {
        return permission != null && permission.toLowerCase(Locale.ROOT).startsWith("group.");
    }

    private Node buildLpNode(String permission, String server, long expiryEpoch) {
        java.time.Instant expiry = java.time.Instant.ofEpochSecond(expiryEpoch);
        if (isGroupPermission(permission)) {
            String groupName = permission.substring("group.".length());
            InheritanceNode.Builder b = InheritanceNode.builder(groupName).value(true).expiry(expiry);
            if (server != null && !server.isBlank()) b.withContext("server", server);
            return b.build();
        }
        PermissionNode.Builder b = PermissionNode.builder(permission).value(true).expiry(expiry);
        if (server != null && !server.isBlank()) b.withContext("server", server);
        return b.build();
    }

    private long findCurrentExpiry(User user, String permission, String server, long nowSeconds) {
        boolean isGroup = isGroupPermission(permission);
        String key = isGroup ? permission.substring("group.".length()) : permission;
        @SuppressWarnings("unchecked")
        var nodeType = (NodeType<? extends Node>) (isGroup ? NodeType.INHERITANCE : NodeType.PERMISSION);
        OptionalLong max = user.getNodes(nodeType).stream()
                .filter(n -> n.getKey().equalsIgnoreCase(isGroup ? "group." + key : key))
                .filter(Node::hasExpiry)
                .filter(n -> !n.hasExpired())
                .filter(n -> server == null || server.isBlank() || server.equalsIgnoreCase(n.getContexts().getAnyValue("server").orElse(null)))
                .mapToLong(n -> nowSeconds + n.getExpiryDuration().toSeconds())
                .max();
        return max.orElse(0L);
    }

    private void removeTempNodes(User user, String permission, String server) {
        boolean isGroup = isGroupPermission(permission);
        @SuppressWarnings("unchecked")
        var nodeType = (NodeType<? extends Node>) (isGroup ? NodeType.INHERITANCE : NodeType.PERMISSION);
        var toRemove = user.getNodes(nodeType).stream()
                .filter(n -> n.getKey().equalsIgnoreCase(permission))
                .filter(Node::hasExpiry)
                .filter(n -> server == null || server.isBlank() || server.equalsIgnoreCase(n.getContexts().getAnyValue("server").orElse(null)))
                .toList();
        toRemove.forEach(user.data()::remove);
    }

    /**
     * Returns 0 if the player can redeem, -1 if they already have the exact group,
     * +1 if they have a higher group on the track.
     */
    private int trackBlockReason(User user, String trackName, String targetGroup, String server) {
        Track track = luckPerms.getTrackManager().getTrack(trackName);
        if (track == null) return 0;
        List<String> groups = track.getGroups();
        int targetPos = groups.indexOf(targetGroup.toLowerCase(Locale.ROOT));
        if (targetPos < 0) return 0;

        int playerPos = user.getNodes(NodeType.INHERITANCE).stream()
                .filter(n -> server == null || server.isBlank()
                        || server.equalsIgnoreCase(n.getContexts().getAnyValue("server").orElse(null)))
                .mapToInt(n -> groups.indexOf(n.getGroupName().toLowerCase(Locale.ROOT)))
                .filter(pos -> pos >= 0)
                .max()
                .orElse(-1);

        if (playerPos == targetPos) return -1;
        if (playerPos > targetPos) return 1;
        return 0;
    }

    private boolean hasPermission(CommandSender sender, String node) {
        return sender.hasPermission("valerinutils.admin") || sender.hasPermission(node);
    }

    private void loadConfigData() {
        voucherTypes.clear();
        ConfigurationSection root = cfg();
        if (root == null) {
            return;
        }

        guiTitle = root.getString("gui.title", "<dark_gray>Confirmar canje");
        guiSize = normalizeGuiSize(root.getInt("gui.size", 27));
        confirmSlot = clampSlot(root.getInt("gui.confirm.slot", 11), guiSize);
        cancelSlot = clampSlot(root.getInt("gui.cancel.slot", 15), guiSize);
        confirmButton = new GuiButton(
                root.getString("gui.confirm.material", "GREEN_STAINED_GLASS_PANE"),
                root.getString("gui.confirm.name", "<green>SI, canjear"),
                root.getStringList("gui.confirm.lore"));
        cancelButton = new GuiButton(
                root.getString("gui.cancel.material", "RED_STAINED_GLASS_PANE"),
                root.getString("gui.cancel.name", "<red>NO, cancelar"),
                root.getStringList("gui.cancel.lore"));

        ConfigurationSection sec = root.getConfigurationSection("types");
        if (sec == null) {
            return;
        }

        for (String id : sec.getKeys(false)) {
            ConfigurationSection t = sec.getConfigurationSection(id);
            if (t == null) {
                continue;
            }
            String command = t.getString("redeem-command", "").trim();
            if (command.isEmpty()) {
                plugin.getLogger().warning("[Vouchers] redeem-command vacio para tipo: " + id);
                continue;
            }
            VoucherType type = new VoucherType(
                    id.toLowerCase(Locale.ROOT),
                    t.getString("item.material", "PAPER"),
                    t.getString("item.head-owner", ""),
                    t.getString("item.head-texture", ""),
                    t.getInt("item.custom-model-data", 0),
                    t.getString("item.name", "<white>Voucher"),
                    t.getStringList("item.lore"),
                    command,
                    t.getBoolean("luckperms-stacking.enabled", false),
                    t.getString("luckperms-stacking.permission", ""),
                    t.getString("luckperms-stacking.server", "survival"),
                    Math.max(1L, t.getLong("luckperms-stacking.duration-seconds", 3600L)),
                    t.getString("luckperms-stacking.track", "")
            );
            voucherTypes.put(type.id, type);
        }
    }

    private int normalizeGuiSize(int raw) {
        int size = Math.max(9, raw);
        int rem = size % 9;
        if (rem != 0) {
            size += (9 - rem);
        }
        return Math.min(54, size);
    }

    private int clampSlot(int slot, int size) {
        if (slot < 0) {
            return 0;
        }
        if (slot >= size) {
            return size - 1;
        }
        return slot;
    }

    private record VoucherType(String id, String material, String headOwner, String headTexture,
                               int customModelData, String displayName, List<String> lore,
                               String redeemCommand, boolean lpStackingEnabled, String lpPermission, String lpServer,
                               long lpDurationSeconds, String lpTrack) {
    }

    private record GuiButton(String material, String name, List<String> lore) {
    }
}
