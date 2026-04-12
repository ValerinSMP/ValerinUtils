package me.mtynnn.valerinutils.modules.kits;

import me.mtynnn.valerinutils.ValerinUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.ShulkerBox;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

final class KitsCommandHandler implements CommandExecutor, TabCompleter {
    private static final String COOLDOWN_FILE = "kits-cooldowns.yml";
    private static final int MAX_SHULKERS = 6;
    private static final int MAX_TOTAL_ITEMS = 100;
    private final ValerinUtils plugin;
    private final KitsModule module;
    private final KitsAutoKitService autoKitService;
    private final Map<UUID, String> selectedByPlayer = new HashMap<>();
    private final Map<UUID, Integer> previewReturnPage = new HashMap<>();
    private final Map<UUID, BukkitTask> menuRefreshTasks = new HashMap<>();
    private final Map<UUID, String> editorSessions = new HashMap<>();  // UUID -> kitName
    private final File file;
    private final FileConfiguration data;

    KitsCommandHandler(ValerinUtils plugin, KitsModule module, KitsAutoKitService autoKitService) {
        this.plugin = plugin;
        this.module = module;
        this.autoKitService = autoKitService;
        this.file = new File(plugin.getDataFolder(), COOLDOWN_FILE);
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    void saveState() {
        saveData();
        cleanupForReload();
    }

    void cleanupForReload() {
        // Cancel all refresh tasks
        for (BukkitTask task : menuRefreshTasks.values()) {
            if (task != null) {
                try {
                    task.cancel();
                } catch (Exception ignored) {}
            }
        }
        menuRefreshTasks.clear();
        
        // Close all open menus
        try {
            for (org.bukkit.entity.Player player : Bukkit.getOnlinePlayers()) {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof KitsMenuHolder ||
                    player.getOpenInventory().getTopInventory().getHolder() instanceof KitsPreviewHolder ||
                    player.getOpenInventory().getTopInventory().getHolder() instanceof KitsEditorHolder) {
                    player.closeInventory();
                }
            }
        } catch (Exception ignored) {}
        
        // Clear all tracking maps
        selectedByPlayer.clear();
        previewReturnPage.clear();
        editorSessions.clear();
    }

    void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        selectedByPlayer.remove(uuid);
        previewReturnPage.remove(uuid);
        onMenuClosed(player);
    }

    void onMenuClick(Player player, int rawSlot, int topSize, KitsMenuHolder holder, ClickType click) {
        if (rawSlot < 0 || rawSlot >= topSize) return;
        FileConfiguration cfg = module.getConfig();
        String menuRoot = menuRoot(cfg);
        playConfiguredSound(player, cfg, "sounds.menu-click", "UI_BUTTON_CLICK", 1.0f, 1.1f);
        List<String> kits = listKits(cfg, player);
        List<Integer> kitSlots = getKitSlots(cfg);
        int perPage = Math.max(1, kitSlots.size());
        int maxPage = Math.max(1, (int) Math.ceil(kits.size() / (double) perPage));
        int page = Math.max(1, Math.min(maxPage, holder.getPage()));

        int back = cfg.getInt(menuRoot + ".items.back.slot", 45);
        int prev = cfg.getInt(menuRoot + ".items.previous.slot", 48);
        int close = cfg.getInt(menuRoot + ".items.close.slot", 49);
        int next = cfg.getInt(menuRoot + ".items.next.slot", 50);
        if (rawSlot == close) { playConfiguredSound(player, cfg, "sounds.menu-close", "BLOCK_CHEST_CLOSE", 1.0f, 1.0f); player.closeInventory(); return; }
        if (rawSlot == back) {
            playConfiguredSound(player, cfg, "sounds.menu-back", "BLOCK_NOTE_BLOCK_BASS", 1.0f, 1.0f);
            player.closeInventory();
            String commandToRun = cfg.getString(menuRoot + ".items.back.command", "menu");
            String commandMode = cfg.getString(menuRoot + ".items.back.command-mode", "console");
            if (commandToRun != null && !commandToRun.isBlank()) {
                String parsed = commandToRun
                        .replace("%player%", player.getName())
                        .replaceFirst("^/", "");
                if ("player".equalsIgnoreCase(commandMode)) {
                    player.performCommand(parsed);
                } else {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
                }
            }
            return;
        }
        if (rawSlot == prev) { if (page > 1) { playConfiguredSound(player, cfg, "sounds.menu-page", "ITEM_BOOK_PAGE_TURN", 1.0f, 1.0f); openMenu(player, page - 1); } return; }
        if (rawSlot == next) { if (page < maxPage) { playConfiguredSound(player, cfg, "sounds.menu-page", "ITEM_BOOK_PAGE_TURN", 1.0f, 1.0f); openMenu(player, page + 1); } return; }

        int slotIndex = kitSlots.indexOf(rawSlot);
        if (slotIndex < 0) return;
        int kitIndex = (page - 1) * perPage + slotIndex;
        if (kitIndex < 0 || kitIndex >= kits.size()) return;
        String kitName = kits.get(kitIndex);
        selectedByPlayer.put(player.getUniqueId(), kitName);
        if (click.isRightClick()) {
            playConfiguredSound(player, cfg, "sounds.preview-open", "BLOCK_ENDER_CHEST_OPEN", 1.0f, 1.2f);
            previewReturnPage.put(player.getUniqueId(), page);
            previewKit(player, kitName);
            return;
        }
        if (giveKit(player, player, kitName, true, false)) {
            playConfiguredSound(player, cfg, "sounds.claim-success", "ENTITY_PLAYER_LEVELUP", 0.8f, 1.3f);
            runClaimEffects(player, cfg);
            player.closeInventory();
        } else {
            playConfiguredSound(player, cfg, "sounds.claim-fail", "BLOCK_NOTE_BLOCK_BASS", 1.0f, 0.8f);
        }
    }

    void onPreviewClosed(Player player) {
        Integer page = previewReturnPage.remove(player.getUniqueId());
        if (page == null) {
            return;
        }
        if (!player.isOnline()) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> openMenu(player, page));
    }

    void onMenuClosed(Player player) {
        BukkitTask task = menuRefreshTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) openMenu(p, 1); else sendHelp(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "menu" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(plugin.translateColors("%prefix%&cEste subcomando solo funciona en juego.")); return true; }
                int page = 1; if (args.length >= 2) try { page = Integer.parseInt(args[1]); } catch (Exception ignored) {}
                openMenu(p, page);
            }
            case "create" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(plugin.translateColors("%prefix%&cEste subcomando solo funciona en juego.")); return true; }
                if (!p.hasPermission("valerinutils.kits.admin")) return noPerms(p);
                if (args.length < 2) { p.sendMessage(plugin.translateColors("%prefix%&cUso: /vukits create <nombre> [permiso] [cooldown_dias]")); return true; }
                createKit(p, args);
            }
            case "preview" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(plugin.translateColors("%prefix%&cEste subcomando solo funciona en juego.")); return true; }
                if (args.length < 2) { p.sendMessage(plugin.translateColors("%prefix%&cUso: /vukits preview <nombre>")); return true; }
                previewKit(p, args[1]);
            }
            case "give" -> {
                if (!sender.hasPermission("valerinutils.kits.admin")) { sender.sendMessage(plugin.translateColors(plugin.getConfigManager().getConfig("settings").getString("messages.no-permission", "&cNo tienes permiso."))); return true; }
                if (args.length < 3) { sender.sendMessage(plugin.translateColors("%prefix%&cUso: /vukits give <jugador> <nombre>")); return true; }
                Player target = Bukkit.getPlayer(args[1]); if (target == null) { sender.sendMessage(plugin.translateColors("%prefix%&cJugador no encontrado o no está online.")); return true; }
                giveKit(sender, target, args[2], false, true);
            }
            case "delete" -> { if (!sender.hasPermission("valerinutils.kits.admin")) { sender.sendMessage(plugin.translateColors(plugin.getConfigManager().getConfig("settings").getString("messages.no-permission", "&cNo tienes permiso."))); return true; } if (args.length < 2) { sender.sendMessage(plugin.translateColors("%prefix%&cUso: /vukits delete <nombre>")); return true; } deleteKit(sender, args[1]); }
            case "reset" -> { if (!sender.hasPermission("valerinutils.kits.admin")) { sender.sendMessage(plugin.translateColors(plugin.getConfigManager().getConfig("settings").getString("messages.no-permission", "&cNo tienes permiso."))); return true; } if (args.length < 2) { sender.sendMessage(plugin.translateColors("%prefix%&cUso: /vukits reset <jugador>")); return true; } resetStarter(sender, args[1]); }
            case "editor" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage(plugin.translateColors("%prefix%&cEste subcomando solo funciona en juego.")); return true; }
                if (!p.hasPermission("valerinutils.kits.admin")) return noPerms(p);
                if (args.length < 2) { p.sendMessage(plugin.translateColors("%prefix%&cUso: /vukits editor <nombre>")); return true; }
                openKitEditor(p, args[1]);
            }
            case "list" -> list(sender);
            case "inicial" -> { if (!(sender instanceof Player p)) { sender.sendMessage(plugin.translateColors("%prefix%&cEste subcomando solo funciona en juego.")); return true; } autoKitService.claimInitialKit(p); }
            case "order" -> {
                if (!sender.hasPermission("valerinutils.kits.admin")) {
                    if (sender instanceof Player p) return noPerms(p);
                    sender.sendMessage(plugin.translateColors("%prefix%&cNo tienes permiso."));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.translateColors("%prefix%&cUso: /vukits order <kit> <posicion>"));
                    return true;
                }
                orderKit(sender, args[1], args[2]);
            }
            case "debug" -> {
                if (!sender.hasPermission("valerinutils.kits.admin")) { if (sender instanceof Player p) return noPerms(p); sender.sendMessage(plugin.translateColors("%prefix%&cNo tienes permiso.")); return true; }
                boolean enabled = plugin.toggleModuleDebug(module.getId());
                sender.sendMessage(plugin.translateColors("%prefix%&7Debug de kits: " + (enabled ? "&aACTIVADO" : "&cDESACTIVADO")));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void openMenu(Player player, int page) {
        FileConfiguration cfg = module.getConfig();
        String menuRoot = menuRoot(cfg);
        ConfigurationSection menu = cfg.getConfigurationSection(menuRoot);
        if (menu == null) { player.sendMessage(plugin.translateColors("%prefix%&cNo existe configuración de GUI en kits.yml")); return; }
        int rows = Math.max(1, Math.min(6, menu.getInt("rows", 6)));
        int size = rows * 9;
        List<String> kits = listKits(cfg, player);
        List<Integer> kitSlots = getKitSlots(cfg);
        if (kitSlots.isEmpty()) { player.sendMessage(plugin.translateColors("%prefix%&cNo hay slots para kits en la GUI.")); return; }
        int perPage = Math.max(1, kitSlots.size());
        int maxPage = Math.max(1, (int) Math.ceil(kits.size() / (double) perPage));
        int p = Math.max(1, Math.min(maxPage, page));

        String title = withInfo(menu.getString("title", "&8sᴇʟᴇᴄᴄɪᴏɴᴀ ᴋɪᴛ"), player, p, maxPage);
        Inventory inv = Bukkit.createInventory(new KitsMenuHolder(p), size, plugin.translateColors(title));
        ItemStack filler = configuredItem(cfg, menuRoot + ".items.filler", player, p, maxPage, "BLACK_STAINED_GLASS_PANE", " ");
        for (int slot : fillerSlots(cfg)) if (slot >= 0 && slot < size) inv.setItem(slot, filler);

        int from = (p - 1) * perPage;
        for (int i = 0; i < perPage; i++) {
            int slot = kitSlots.get(i); if (slot < 0 || slot >= size) continue;
            int kitIndex = from + i;
            inv.setItem(slot, kitIndex < kits.size() ? kitIcon(player, cfg, kits.get(kitIndex)) : new ItemStack(Material.AIR));
        }
        int infoSlot = cfg.getInt(menuRoot + ".items.info.slot", 4);
        int backSlot = cfg.getInt(menuRoot + ".items.back.slot", 45);
        int prevSlot = cfg.getInt(menuRoot + ".items.previous.slot", 48);
        int closeSlot = cfg.getInt(menuRoot + ".items.close.slot", 49);
        int nextSlot = cfg.getInt(menuRoot + ".items.next.slot", 50);
        set(inv, infoSlot, configuredItem(cfg, menuRoot + ".items.info", player, p, maxPage, "BOOK", "&eInfo"));
        set(inv, backSlot, configuredItem(cfg, menuRoot + ".items.back", player, p, maxPage, "ARROW", "&cAtrás"));
        set(inv, closeSlot, configuredItem(cfg, menuRoot + ".items.close", player, p, maxPage, "BARRIER", "&cCerrar"));
        set(inv, prevSlot, p > 1 ? configuredItem(cfg, menuRoot + ".items.previous", player, p, maxPage, "PAPER", "&eAnterior") : filler);
        set(inv, nextSlot, p < maxPage ? configuredItem(cfg, menuRoot + ".items.next", player, p, maxPage, "LIME_STAINED_GLASS_PANE", "&aSiguiente") : filler);
        playConfiguredSound(player, cfg, "sounds.menu-open", "BLOCK_CHEST_OPEN", 1.0f, 1.0f);
        player.openInventory(inv);
        startMenuRefresh(player);
    }

    private void startMenuRefresh(Player player) {
        onMenuClosed(player);
        UUID uuid = player.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline()) {
                onMenuClosed(player);
                return;
            }
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof KitsMenuHolder holder)) {
                onMenuClosed(player);
                return;
            }
            refreshOpenMenu(player, holder.getPage());
        }, 20L, 20L);
        menuRefreshTasks.put(uuid, task);
    }

    private void refreshOpenMenu(Player player, int page) {
        FileConfiguration cfg = module.getConfig();
        String menuRoot = menuRoot(cfg);
        Inventory inv = player.getOpenInventory().getTopInventory();
        List<String> kits = listKits(cfg, player);
        List<Integer> kitSlots = getKitSlots(cfg);
        int perPage = Math.max(1, kitSlots.size());
        int maxPage = Math.max(1, (int) Math.ceil(kits.size() / (double) perPage));
        int p = Math.max(1, Math.min(maxPage, page));

        ItemStack filler = configuredItem(cfg, menuRoot + ".items.filler", player, p, maxPage, "BLACK_STAINED_GLASS_PANE", " ");
        for (int slot : fillerSlots(cfg)) if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, filler);

        int from = (p - 1) * perPage;
        for (int i = 0; i < perPage; i++) {
            int slot = kitSlots.get(i);
            if (slot < 0 || slot >= inv.getSize()) continue;
            int kitIndex = from + i;
            inv.setItem(slot, kitIndex < kits.size() ? kitIcon(player, cfg, kits.get(kitIndex)) : new ItemStack(Material.AIR));
        }

        int infoSlot = cfg.getInt(menuRoot + ".items.info.slot", 4);
        int backSlot = cfg.getInt(menuRoot + ".items.back.slot", 45);
        int prevSlot = cfg.getInt(menuRoot + ".items.previous.slot", 48);
        int closeSlot = cfg.getInt(menuRoot + ".items.close.slot", 49);
        int nextSlot = cfg.getInt(menuRoot + ".items.next.slot", 50);
        set(inv, infoSlot, configuredItem(cfg, menuRoot + ".items.info", player, p, maxPage, "BOOK", "&eInfo"));
        set(inv, backSlot, configuredItem(cfg, menuRoot + ".items.back", player, p, maxPage, "ARROW", "&cAtrás"));
        set(inv, closeSlot, configuredItem(cfg, menuRoot + ".items.close", player, p, maxPage, "BARRIER", "&cCerrar"));
        set(inv, prevSlot, p > 1 ? configuredItem(cfg, menuRoot + ".items.previous", player, p, maxPage, "PAPER", "&eAnterior") : filler);
        set(inv, nextSlot, p < maxPage ? configuredItem(cfg, menuRoot + ".items.next", player, p, maxPage, "LIME_STAINED_GLASS_PANE", "&aSiguiente") : filler);
    }

    private ItemStack configuredItem(FileConfiguration cfg, String path, Player player, int page, int maxPage, String fallbackMat, String fallbackName) {
        Material mat = Material.matchMaterial(cfg.getString(path + ".material", fallbackMat)); if (mat == null) mat = Material.BARRIER;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        Component displayName = plugin.parseComponent(withInfo(cfg.getString(path + ".name", fallbackName), player, page, maxPage))
                .decoration(TextDecoration.ITALIC, false);
        meta.displayName(displayName);
        List<String> lore = cfg.getStringList(path + ".lore");
        if (!lore.isEmpty()) {
            meta.lore(lore.stream()
                    .map(s -> plugin.parseComponent(withInfo(s, player, page, maxPage)).decoration(TextDecoration.ITALIC, false))
                    .collect(Collectors.toList()));
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<ItemStack> loadKitItems(FileConfiguration cfg, String path) {
        List<ItemStack> result = new ArrayList<>();
        
        // Try loading shulker_items list
        if (cfg.contains(path + ".shulker_items")) {
            try {
                List<?> itemsList = cfg.getList(path + ".shulker_items");
                if (itemsList != null) {
                    for (Object obj : itemsList) {
                        if (obj instanceof ItemStack) {
                            ItemStack item = (ItemStack) obj;
                            if (!item.getType().isAir()) {
                                result.add(item);
                            }
                        }
                    }
                }
                if (!result.isEmpty()) return result;
            } catch (Exception e) {
                // Log the error for debugging
                e.printStackTrace();
            }
        }
        
        // Fallback to single shulker_item
        try {
            ItemStack single = cfg.getItemStack(path + ".shulker_item");
            return single != null ? List.of(single) : Collections.emptyList();
        } catch (Exception e) {
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private List<ItemStack> packIntoShulkers(List<ItemStack> items) {
        return packIntoShulkers(items, Material.SHULKER_BOX, null);
    }

    private List<ItemStack> packIntoShulkers(List<ItemStack> items, Material shulkerMaterial) {
        return packIntoShulkers(items, shulkerMaterial, null);
    }

    private List<ItemStack> packIntoShulkers(List<ItemStack> items, Material shulkerMaterial, String displayName) {
        List<ItemStack> result = new ArrayList<>();
        if (items.isEmpty()) return result;
        
        // Validate shulker material
        if (!shulkerMaterial.name().contains("SHULKER_BOX")) {
            shulkerMaterial = Material.SHULKER_BOX;
        }
        
        ItemStack shulkerItem = new ItemStack(shulkerMaterial);
        BlockStateMeta bsm = (BlockStateMeta) shulkerItem.getItemMeta();
        ShulkerBox box = (ShulkerBox) bsm.getBlockState();
        
        // Apply display name if provided
        if (displayName != null && !displayName.isEmpty()) {
            bsm.setDisplayName(displayName);
        }
        
        int slot = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            if (slot >= 27) {
                bsm.setBlockState(box);
                shulkerItem.setItemMeta(bsm);
                result.add(shulkerItem.clone());
                shulkerItem = new ItemStack(shulkerMaterial);
                bsm = (BlockStateMeta) shulkerItem.getItemMeta();
                box = (ShulkerBox) bsm.getBlockState();
                
                // Apply display name to next shulker if provided
                if (displayName != null && !displayName.isEmpty()) {
                    bsm.setDisplayName(displayName);
                }
                
                slot = 0;
            }
            box.getInventory().setItem(slot++, item.clone());
        }
        if (slot > 0) {
            bsm.setBlockState(box);
            shulkerItem.setItemMeta(bsm);
            result.add(shulkerItem.clone());
        }
        return result;
    }

    private ItemStack kitIcon(Player player, FileConfiguration cfg, String kitName) {
        String path = "kits." + kitName;
        List<ItemStack> kitItems = loadKitItems(cfg, path);
        ItemStack base = kitItems.isEmpty() ? new ItemStack(Material.SHULKER_BOX) : kitItems.get(0);
        ItemStack icon = base.clone();
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(plugin.parseComponent(cfg.getString(path + ".display_name", kitName))
                .decoration(TextDecoration.ITALIC, false));
        int items = kitItems.stream().mapToInt(this::shulkerAmount).sum();
        int days = Math.max(0, cfg.getInt(path + ".cooldown-days", cfg.getInt("settings.default_claim_cooldown_days", 15)));
        String perm = cfg.getString(path + ".required-permission", "").trim();
        boolean allowed = perm.isEmpty() || player.hasPermission(perm);
        long now = System.currentTimeMillis(), next = nextClaim(player.getUniqueId(), kitName);
        String status = !allowed ? "Sin permiso" : (next > now ? "En " + duration(next - now) : "Disponible");
        List<String> lore = cfg.getStringList(menuRoot(cfg) + ".kit-lore");
        if (lore.isEmpty()) lore = List.of("&8• &fItems: &#FF6961%items%", "&8• &fCooldown: &#FF6961%days%d", "&8• &fPermiso: &#FF6961%perm%", "&8• &fEstado: &#FF6961%status%");
        meta.lore(lore.stream()
                .map(s -> s.replace("%kit_name%", kitName)
                        .replace("%items%", String.valueOf(items))
                        .replace("%days%", String.valueOf(days))
                        .replace("%perm%", perm.isBlank() ? "Ninguno" : perm)
                        .replace("%status%", status))
                .map(text -> plugin.parseComponent(text).decoration(TextDecoration.ITALIC, false))
                .collect(Collectors.toList()));
        icon.setItemMeta(meta);
        return icon;
    }

    private boolean giveKit(CommandSender sender, Player target, String kitName, boolean respectCooldown, boolean silentTarget) {
        FileConfiguration cfg = module.getConfig();
        String path = "kits." + kitName;
        if (!cfg.contains(path)) { sender.sendMessage(plugin.translateColors("%prefix%&cEl kit &e" + kitName + " &cno existe.")); return false; }
        String perm = cfg.getString(path + ".required-permission", "").trim();
        if (!perm.isEmpty() && !target.hasPermission(perm)) { target.sendMessage(plugin.translateColors(cfg.getString("messages.kit-no-permission", "%prefix%&cNo cumples el permiso requerido.").replace("%permission%", perm))); return false; }
        int days = Math.max(0, cfg.getInt(path + ".cooldown-days", cfg.getInt("settings.default_claim_cooldown_days", 15)));
        boolean bypassCooldown = target.isOp() || target.hasPermission("valerinutils.kits.cooldown.bypass");
        if (!bypassCooldown && respectCooldown && days > 0) {
            long now = System.currentTimeMillis(), next = nextClaim(target.getUniqueId(), kitName);
            if (next > now) { target.sendMessage(plugin.translateColors(cfg.getString("messages.kit-on-cooldown", "%prefix%&cDebes esperar &e%time% &cpara este kit.").replace("%time%", duration(next - now)))); return false; }
            setNextClaim(target.getUniqueId(), kitName, now + days * 24L * 60L * 60L * 1000L);
        }
        List<ItemStack> kitItems = loadKitItems(cfg, path);
        if (kitItems.isEmpty()) { sender.sendMessage(plugin.translateColors("%prefix%&cError al cargar el item del kit.")); return false; }
        boolean droppedAny = false;
        for (ItemStack shulker : kitItems) {
            ItemStack give = shulker.clone();
            if (target.getInventory().firstEmpty() == -1) {
                target.getWorld().dropItemNaturally(target.getLocation(), give);
                droppedAny = true;
            } else {
                target.getInventory().addItem(give);
            }
        }
        if (droppedAny) target.sendMessage(plugin.translateColors("%prefix%&eTu inventario estaba lleno, parte del kit se soltó al suelo."));
        if (!sender.getName().equalsIgnoreCase(target.getName())) sender.sendMessage(plugin.translateColors("%prefix%&aHas dado el kit &e" + kitName + " &aa &f" + target.getName()));
        if (!silentTarget) target.sendMessage(plugin.translateColors(cfg.getString("messages.kit-claimed", "%prefix%&aHas reclamado el kit: &f%kit%").replace("%kit%", cfg.getString(path + ".display_name", kitName))));
        module.logDebug("Kit entregado: " + kitName + " -> " + target.getName() + " (sender=" + sender.getName() + ")");
        return true;
    }

    private void createKit(Player player, String[] args) {
        String name = args[1];
        FileConfiguration cfg = module.getConfig();

        // Scan inventory slots 0-35 (main inventory, no armor/offhand)
        List<ItemStack> shulkers = new ArrayList<>();
        List<ItemStack> regularItems = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType().isAir()) continue;
            if (item.getType().name().contains("SHULKER_BOX")) {
                shulkers.add(item.clone());
            } else {
                regularItems.add(item.clone());
            }
        }
        if (shulkers.isEmpty() && regularItems.isEmpty()) {
            player.sendMessage(plugin.translateColors("%prefix%&cTu inventario está vacío. Llenalo con los items del kit."));
            return;
        }

        // Pack regular items into shulkers (27 per shulker)
        List<ItemStack> packedShulkers = packIntoShulkers(regularItems);

        // Final kit item list: explicit shulkers first, then packed shulkers
        List<ItemStack> kitItems = new ArrayList<>(shulkers);
        kitItems.addAll(packedShulkers);

        String displayName = "&8Kit " + name;
        cfg.set("kits." + name + ".display_name", displayName);
        cfg.set("kits." + name + ".shulker_items", kitItems);
        cfg.set("kits." + name + ".shulker_item", null); // remove legacy key if present

        String defaultPermPattern = cfg.getString("settings.default_required_permission_pattern", "kits.%kit%");
        String defaultPerm = defaultPermPattern == null ? "" : defaultPermPattern.replace("%kit%", name.toLowerCase(Locale.ROOT));
        String finalPerm = args.length >= 3 ? args[2] : defaultPerm;
        int finalCooldown = cfg.getInt("settings.default_claim_cooldown_days", 15);
        if (args.length >= 4) {
            try {
                finalCooldown = Math.max(0, Integer.parseInt(args[3]));
            } catch (NumberFormatException ignored) {
            }
        }
        cfg.set("kits." + name + ".required-permission", finalPerm == null ? "" : finalPerm);
        cfg.set("kits." + name + ".cooldown-days", finalCooldown);
        cfg.set("kits." + name + ".order", getNextKitOrder(cfg));
        plugin.getConfigManager().saveConfig("kits");
        int totalShulkers = kitItems.size();
        player.sendMessage(plugin.translateColors("%prefix%&aKit &e" + name + " &acreado con &f" + totalShulkers + " &ashulker(s). &7Perm: &f"
                + (finalPerm == null || finalPerm.isBlank() ? "ninguno" : finalPerm) + " &8| &7CD: &f" + finalCooldown + "d"));
    }

    private void previewKit(Player player, String name) {
        FileConfiguration cfg = module.getConfig();
        if (!cfg.contains("kits." + name)) { player.sendMessage(plugin.translateColors("%prefix%&cEl kit &e" + name + " &cno existe.")); return; }
        List<ItemStack> kitItems = loadKitItems(cfg, "kits." + name);
        if (kitItems.isEmpty()) { player.sendMessage(plugin.translateColors("%prefix%&cError al cargar el contenido del kit.")); return; }
        String displayName = cfg.getString("kits." + name + ".display_name", "&8Preview: " + name);
        if (kitItems.size() == 1) {
            // Single shulker: show its contents
            ItemStack shulker = kitItems.get(0);
            if (shulker.getItemMeta() instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox box) {
                Inventory preview = Bukkit.createInventory(new KitsPreviewHolder(), 27, plugin.translateColors(displayName));
                preview.setContents(box.getInventory().getContents());
                player.openInventory(preview);
            } else {
                player.sendMessage(plugin.translateColors("%prefix%&cError al cargar el contenido del kit."));
            }
        } else {
            // Multiple shulkers: show the shulker items themselves
            int rows = Math.min(6, (int) Math.ceil(kitItems.size() / 9.0));
            int size = Math.max(9, rows * 9);
            Inventory preview = Bukkit.createInventory(new KitsPreviewHolder(), size, plugin.translateColors(displayName));
            for (int i = 0; i < kitItems.size() && i < size; i++) {
                preview.setItem(i, kitItems.get(i).clone());
            }
            player.openInventory(preview);
        }
    }

    private void openKitEditor(Player player, String kitName) {
        FileConfiguration cfg = module.getConfig();
        String path = "kits." + kitName;
        
        if (!cfg.contains(path)) {
            player.sendMessage(plugin.translateColors("%prefix%&cEl kit &e" + kitName + " &cno existe."));
            return;
        }
        
        List<ItemStack> kitItems = loadKitItems(cfg, path);
        
        // Create 6-row inventory for editor (54 slots total)
        String displayName = cfg.getString(path + ".display_name", "&8Editor: " + kitName);
        Inventory editor = Bukkit.createInventory(new KitsEditorHolder(kitName), 54, 
                plugin.translateColors(displayName));
        
        // Load items into inventory, respecting MAX_TOTAL_ITEMS limit
        int itemCount = 0;
        for (ItemStack shulker : kitItems) {
            if (itemCount >= MAX_TOTAL_ITEMS) break;
            
            if (shulker == null || shulker.getType().isAir()) continue;
            
            // If it's a shulker box, extract its contents
            if (shulker.getType().name().contains("SHULKER_BOX")) {
                ItemMeta meta = shulker.getItemMeta();
                if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox box) {
                    for (ItemStack item : box.getInventory().getContents()) {
                        if (item == null || item.getType().isAir()) continue;
                        if (itemCount >= MAX_TOTAL_ITEMS) break;
                        
                        int nextSlot = editor.firstEmpty();
                        if (nextSlot >= 0 && nextSlot < 48) { // Reserve last 6 slots for buttons
                            editor.setItem(nextSlot, item.clone());
                            itemCount++;
                        }
                    }
                } else {
                    // Not a proper shulker, add as regular item
                    int nextSlot = editor.firstEmpty();
                    if (nextSlot >= 0 && nextSlot < 48) {
                        editor.setItem(nextSlot, shulker.clone());
                        itemCount++;
                    }
                }
            } else {
                // Regular item
                int nextSlot = editor.firstEmpty();
                if (nextSlot >= 0 && nextSlot < 48) {
                    editor.setItem(nextSlot, shulker.clone());
                    itemCount++;
                }
            }
        }
        
        // Add Save button at slot 48 (green checkmark)
        ItemStack saveButton = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta saveMeta = saveButton.getItemMeta();
        saveMeta.displayName(plugin.parseComponent("<#77DD77>✓ <bold>Guardar</bold>").decoration(TextDecoration.ITALIC, false));
        saveButton.setItemMeta(saveMeta);
        editor.setItem(48, saveButton);
        
        // Add Cancel button at slot 49 (red X)
        ItemStack cancelButton = new ItemStack(Material.RED_CONCRETE);
        ItemMeta cancelMeta = cancelButton.getItemMeta();
        cancelMeta.displayName(plugin.parseComponent("<#FF5555>✕ <bold>Cancelar</bold>").decoration(TextDecoration.ITALIC, false));
        cancelButton.setItemMeta(cancelMeta);
        editor.setItem(49, cancelButton);
        
        // Add filler blocks in remaining slots (50-53)
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false));
        filler.setItemMeta(fillerMeta);
        for (int i = 50; i < 54; i++) {
            editor.setItem(i, filler.clone());
        }
        
        // Track editor session
        editorSessions.put(player.getUniqueId(), kitName);
        
        // Open inventory
        player.openInventory(editor);
        player.sendMessage(plugin.translateColors("%prefix%&aEditando kit &e" + kitName + "&a. Añade/quita items y haz clic en &fGuardar&a."));
    }

    private void saveKitFromEditor(Player player, String kitName) {
        FileConfiguration cfg = module.getConfig();
        String path = "kits." + kitName;
        
        Inventory editorInv = player.getOpenInventory().getTopInventory();
        
        // Extract items from editor inventory (slots 0-47, excluding save/cancel buttons)
        List<ItemStack> extractedItems = new ArrayList<>();
        for (int i = 0; i < 48; i++) {
            ItemStack item = editorInv.getItem(i);
            if (item != null && !item.getType().isAir()) {
                extractedItems.add(item.clone());
            }
        }
        
        if (extractedItems.isEmpty()) {
            player.sendMessage(plugin.translateColors("%prefix%&cNo puedes guardar un kit vacío."));
            return;
        }
        
        // Get original shulker color and name to preserve them
        Material preferredShulkerMaterial = Material.SHULKER_BOX; // default black
        String preferredShulkerName = null;
        
        List<ItemStack> originalKitItems = loadKitItems(cfg, path);
        if (!originalKitItems.isEmpty() && originalKitItems.get(0) != null) {
            ItemStack originalShulker = originalKitItems.get(0);
            
            // Get color
            Material originalType = originalShulker.getType();
            if (originalType.name().contains("SHULKER_BOX")) {
                preferredShulkerMaterial = originalType;
            }
            
            // Get display name
            if (originalShulker.hasItemMeta() && originalShulker.getItemMeta().hasDisplayName()) {
                preferredShulkerName = originalShulker.getItemMeta().getDisplayName();
            }
        }
        
        // Pack items into shulkers, preserving original color and name
        List<ItemStack> packedShulkers = packIntoShulkers(extractedItems, preferredShulkerMaterial, preferredShulkerName);
        
        // Validate shulker count
        if (packedShulkers.size() > MAX_SHULKERS) {
            player.sendMessage(plugin.translateColors("%prefix%&cEl kit tiene demasiados items. Máximo: &e" + (MAX_SHULKERS * 27) + " &citems (en &e" + MAX_SHULKERS + " &cshulkers)."));
            return;
        }
        
        // Save to config
        cfg.set(path + ".shulker_items", packedShulkers);
        plugin.getConfigManager().saveConfig("kits");
        
        // Remove from editor sessions
        editorSessions.remove(player.getUniqueId());
        
        // Close and notify
        player.closeInventory();
        player.sendMessage(plugin.translateColors("%prefix%&aKit &e" + kitName + " &aguardado correctamente con &f" + extractedItems.size() + " &aitem(s)."));
        module.logDebug("Kit editado: " + kitName + " -> " + player.getName() + " (items=" + extractedItems.size() + ", shulkers=" + packedShulkers.size() + ")");
    }

    void onEditorClick(Player player, int rawSlot, KitsEditorHolder holder) {
        String kitName = holder.getKitName();
        
        if (rawSlot == 48) {
            // Save button clicked
            saveKitFromEditor(player, kitName);
            return;
        }
        
        if (rawSlot == 49) {
            // Cancel button clicked
            editorSessions.remove(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(plugin.translateColors("%prefix%&cEdición cancelada."));
            return;
        }
        
        if (rawSlot >= 50) {
            // Prevent clicking on filler items
            return;
        }
        
        // Allow clicking on item slots (0-47) for editing
        // Items can be removed by shift+click or by picking them up and dropping
    }

    void onEditorClosed(Player player) {
        UUID uuid = player.getUniqueId();
        String kitName = editorSessions.get(uuid);
        
        if (kitName != null) {
            // Editor was closed without saving - remove session
            editorSessions.remove(uuid);
            // No message needed - player knows what they did
        }
    }

    private void deleteKit(CommandSender sender, String name) { FileConfiguration cfg = module.getConfig(); if (!cfg.contains("kits." + name)) { sender.sendMessage(plugin.translateColors("%prefix%&cEl kit &e" + name + " &cno existe.")); return; } cfg.set("kits." + name, null); plugin.getConfigManager().saveConfig("kits"); sender.sendMessage(plugin.translateColors("%prefix%&cKit &e" + name + " &celiminado.")); }
    private void resetStarter(CommandSender sender, String targetName) { Player t = Bukkit.getPlayer(targetName); if (t == null) { sender.sendMessage(plugin.translateColors("%prefix%&cJugador no encontrado o no está online.")); return; } var d = plugin.getPlayerData(t.getUniqueId()); if (d != null) { d.setStarterKitReceived(false); sender.sendMessage(plugin.translateColors("%prefix%&aFlag de kit inicial reseteado para &f" + t.getName())); } }
    private void list(CommandSender sender) {
        FileConfiguration cfg = module.getConfig();
        List<String> visible = listKits(cfg, sender instanceof Player p ? p : null);
        if (visible.isEmpty()) {
            sender.sendMessage(plugin.translateColors("%prefix%&7No hay kits disponibles."));
            return;
        }
        sender.sendMessage(plugin.translateColors("%prefix%&eKits disponibles (&f" + visible.size() + "&e):"));
        for (String key : visible) {
            String display = cfg.getString("kits." + key + ".display_name", key);
            String perm = cfg.getString("kits." + key + ".required-permission", "");
            int cd = cfg.getInt("kits." + key + ".cooldown-days", cfg.getInt("settings.default_claim_cooldown_days", 15));
            int order = cfg.getInt("kits." + key + ".order", Integer.MAX_VALUE);
            String orderDisplay = order == Integer.MAX_VALUE ? "-" : String.valueOf(order);
            sender.sendMessage(plugin.translateColors(" &8- &f" + key + " &7(" + display + "&7) &8| &7Ord: &f" + orderDisplay + " &8| &7Perm: &f" + (perm.isBlank() ? "ninguno" : perm) + " &8| &7CD: &f" + cd + "d"));
        }
    }

    private void orderKit(CommandSender sender, String kitName, String positionRaw) {
        FileConfiguration cfg = module.getConfig();
        ConfigurationSection kits = cfg.getConfigurationSection("kits");
        if (kits == null || kits.getKeys(false).isEmpty()) {
            sender.sendMessage(plugin.translateColors("%prefix%&cNo hay kits para ordenar."));
            return;
        }
        if (!cfg.contains("kits." + kitName)) {
            sender.sendMessage(plugin.translateColors("%prefix%&cEl kit &e" + kitName + " &cno existe."));
            return;
        }

        int position;
        try {
            position = Integer.parseInt(positionRaw);
        } catch (NumberFormatException ex) {
            sender.sendMessage(plugin.translateColors("%prefix%&cLa posición debe ser un número."));
            return;
        }

        List<String> ordered = listKits(cfg, null);
        String found = ordered.stream().filter(k -> k.equalsIgnoreCase(kitName)).findFirst().orElse(kitName);
        ordered.removeIf(k -> k.equalsIgnoreCase(found));
        int maxPosition = ordered.size() + 1;
        int clamped = Math.max(1, Math.min(position, maxPosition));
        ordered.add(clamped - 1, found);

        for (int i = 0; i < ordered.size(); i++) {
            cfg.set("kits." + ordered.get(i) + ".order", i + 1);
        }
        plugin.getConfigManager().saveConfig("kits");
        sender.sendMessage(plugin.translateColors("%prefix%&aKit &e" + found + " &aordenado en posición &f" + clamped + "&a."));
    }

    private List<String> listKits(FileConfiguration cfg, Player viewer) {
        ConfigurationSection s = cfg.getConfigurationSection("kits");
        if (s == null) return Collections.emptyList();
        return s.getKeys(false).stream()
                .sorted((a, b) -> {
                    int ao = cfg.getInt("kits." + a + ".order", Integer.MAX_VALUE);
                    int bo = cfg.getInt("kits." + b + ".order", Integer.MAX_VALUE);
                    if (ao != bo) return Integer.compare(ao, bo);
                    return String.CASE_INSENSITIVE_ORDER.compare(a, b);
                })
                .collect(Collectors.toList());
    }

    private int getNextKitOrder(FileConfiguration cfg) {
        ConfigurationSection s = cfg.getConfigurationSection("kits");
        if (s == null || s.getKeys(false).isEmpty()) {
            return 1;
        }
        int max = 0;
        for (String kit : s.getKeys(false)) {
            max = Math.max(max, cfg.getInt("kits." + kit + ".order", 0));
        }
        return max + 1;
    }
    private List<Integer> getKitSlots(FileConfiguration cfg) { List<Integer> list = cfg.getIntegerList(menuRoot(cfg) + ".kit-slots"); if (list == null || list.isEmpty()) return Collections.emptyList(); return list.stream().filter(i -> i >= 0 && i < 54).distinct().collect(Collectors.toList()); }
    private Set<Integer> fillerSlots(FileConfiguration cfg) { Set<Integer> out = new LinkedHashSet<>(); for (String token : cfg.getStringList(menuRoot(cfg) + ".filler-slots")) parseSlots(token, out); return out; }
    private void parseSlots(String token, Set<Integer> out) { if (token == null || token.isBlank()) return; if (token.contains("-")) { String[] p = token.split("-", 2); try { int a = Integer.parseInt(p[0].trim()), b = Integer.parseInt(p[1].trim()); if (a > b) { int t = a; a = b; b = t; } for (int i = a; i <= b; i++) if (i >= 0 && i < 54) out.add(i); } catch (Exception ignored) {} } else { try { int s = Integer.parseInt(token.trim()); if (s >= 0 && s < 54) out.add(s); } catch (Exception ignored) {} } }
    private void set(Inventory inv, int slot, ItemStack item) { if (slot >= 0 && slot < inv.getSize()) inv.setItem(slot, item); }
    private String withInfo(String text, Player player, int page, int maxPage) { if (text == null) return ""; return text.replace("%selected_kit%", selectedByPlayer.getOrDefault(player.getUniqueId(), "Ninguno")).replace("%preset_slot%", "N/A").replace("%page%", String.valueOf(page)).replace("%max_page%", String.valueOf(maxPage)); }
    private int shulkerAmount(ItemStack stack) { ItemMeta m = stack.getItemMeta(); if (!(m instanceof BlockStateMeta b) || !(b.getBlockState() instanceof ShulkerBox box)) return 0; int c = 0; for (ItemStack it : box.getInventory().getContents()) if (it != null && it.getType() != Material.AIR && it.getAmount() > 0) c += it.getAmount(); return c; }
    private long nextClaim(UUID uuid, String kit) { return data.getLong("players." + uuid + "." + kit.toLowerCase(Locale.ROOT), 0L); }
    private void setNextClaim(UUID uuid, String kit, long ts) { data.set("players." + uuid + "." + kit.toLowerCase(Locale.ROOT), ts); saveData(); }
    private void saveData() { try { data.save(file); } catch (IOException e) { plugin.getLogger().warning("[Kits] No se pudo guardar " + COOLDOWN_FILE + ": " + e.getMessage()); } }
    private String duration(long ms) { long s = Math.max(0L, ms / 1000L), d = s / 86400L, h = (s % 86400L) / 3600L, m = (s % 3600L) / 60L; if (d > 0) return d + "d " + h + "h " + m + "m"; if (h > 0) return h + "h " + m + "m"; return m + "m"; }

    private void runClaimEffects(Player player, FileConfiguration cfg) {
        if (!cfg.getBoolean("claim-effects.enabled", true)) return;
        if (cfg.getBoolean("claim-effects.particles.enabled", true)) {
            try {
                Particle particle = Particle.valueOf(cfg.getString("claim-effects.particles.type", "TOTEM_OF_UNDYING"));
                int amount = Math.max(1, cfg.getInt("claim-effects.particles.amount", 25));
                double offset = Math.max(0.0, cfg.getDouble("claim-effects.particles.offset", 0.35));
                double speed = Math.max(0.0, cfg.getDouble("claim-effects.particles.speed", 0.05));
                player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), amount, offset, offset, offset, speed);
            } catch (Exception ignored) {
            }
        }
        if (cfg.getBoolean("claim-effects.title.enabled", true)) {
            String title = cfg.getString("claim-effects.title.title", "&#77DD77ᴋɪᴛ ʀᴇᴄʟᴀᴍᴀᴅᴏ");
            String subtitle = cfg.getString("claim-effects.title.subtitle", "&fDisfruta tu recompensa");
            int in = Math.max(0, cfg.getInt("claim-effects.title.fade-in", 5));
            int stay = Math.max(0, cfg.getInt("claim-effects.title.stay", 30));
            int out = Math.max(0, cfg.getInt("claim-effects.title.fade-out", 8));
            player.sendTitle(plugin.translateColors(title), plugin.translateColors(subtitle), in, stay, out);
        }
        String actionbar = cfg.getString("claim-effects.actionbar", "");
        if (actionbar != null && !actionbar.isBlank()) {
            player.sendActionBar(plugin.parseComponent(actionbar));
        }
        if (cfg.getBoolean("claim-effects.extra-sound.enabled", true)) {
            String extraName = cfg.getString("claim-effects.extra-sound.name", "BLOCK_AMETHYST_BLOCK_CHIME");
            float extraVolume = (float) cfg.getDouble("claim-effects.extra-sound.volume", 0.8);
            float extraPitch = (float) cfg.getDouble("claim-effects.extra-sound.pitch", 1.4);
            Bukkit.getScheduler().runTaskLater(plugin, () ->
                    playRawSound(player, extraName, extraVolume, extraPitch), 4L);
        }
    }

    private void playConfiguredSound(Player player, FileConfiguration cfg, String path, String fallback, float fallbackVolume, float fallbackPitch) {
        String soundName = cfg.getString(path, fallback);
        float volume = (float) cfg.getDouble(path + ".volume", fallbackVolume);
        float pitch = (float) cfg.getDouble(path + ".pitch", fallbackPitch);
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception ignored) {
        }
    }

    private void playRawSound(Player player, String soundName, float volume, float pitch) {
        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (Exception ignored) {
        }
    }

    private String menuRoot(FileConfiguration cfg) {
        if (cfg != null && cfg.contains("menu")) {
            return "menu";
        }
        return "casual-kit-menu";
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(plugin.translateColors("&8&m       &r &#FFEE8Cᴋɪᴛꜱ ꜱʏꜱᴛᴇᴍ &8&m       "));
        sender.sendMessage(plugin.translateColors(" &e/kits &7- Abrir menú de kits"));
        sender.sendMessage(plugin.translateColors(" &e/vukits preview <nombre> &7- Ver contenido"));
        sender.sendMessage(plugin.translateColors(" &e/vukits list &7- Ver lista de kits"));
        sender.sendMessage(plugin.translateColors(" &e/kit inicial &7- Reclamar kit inicial (5min)"));
        if (sender.hasPermission("valerinutils.kits.admin")) {
            sender.sendMessage(plugin.translateColors(" &e/vukits create <nombre> [permiso] [cooldown] &7- Crear desde la mano"));
            sender.sendMessage(plugin.translateColors(" &e/vukits editor <nombre> &7- Editar contenido del kit"));
            sender.sendMessage(plugin.translateColors(" &e/vukits order <nombre> <posicion> &7- Ordenar kit en la GUI"));
            sender.sendMessage(plugin.translateColors(" &e/vukits give <jugador> <nombre> &7- Dar kit"));
            sender.sendMessage(plugin.translateColors(" &e/vukits reset <jugador> &7- Reset flag inicial"));
            sender.sendMessage(plugin.translateColors(" &e/vukits delete <nombre> &7- Eliminar kit"));
            sender.sendMessage(plugin.translateColors(" &e/vukits debug &7- Toggle debug del módulo"));
        }
    }

    private boolean noPerms(Player player) { player.sendMessage(plugin.translateColors(plugin.getConfigManager().getConfig("settings").getString("messages.no-permission", "&cNo tienes permiso."))); return true; }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("menu", "preview", "list", "inicial"));
            if (sender.hasPermission("valerinutils.kits.admin")) subs.addAll(List.of("create", "give", "delete", "reset", "editor", "order", "debug"));
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("preview") || args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("editor") || args[0].equalsIgnoreCase("order")) {
                ConfigurationSection kits = module.getConfig().getConfigurationSection("kits");
                if (kits != null) return kits.getKeys(false).stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset")) {
                String p = args[1].toLowerCase(Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n != null && n.toLowerCase(Locale.ROOT).startsWith(p)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("order")) {
            ConfigurationSection kits = module.getConfig().getConfigurationSection("kits");
            int size = kits == null ? 1 : Math.max(1, kits.getKeys(false).size());
            List<String> suggestions = new ArrayList<>();
            for (int i = 1; i <= size; i++) {
                suggestions.add(String.valueOf(i));
            }
            return suggestions.stream().filter(s -> s.startsWith(args[2])).collect(Collectors.toList());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            ConfigurationSection kits = module.getConfig().getConfigurationSection("kits");
            if (kits != null) return kits.getKeys(false).stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
