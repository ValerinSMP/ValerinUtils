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
    private final ValerinUtils plugin;
    private final KitsModule module;
    private final KitsAutoKitService autoKitService;
    private final Map<UUID, String> selectedByPlayer = new HashMap<>();
    private final Map<UUID, Integer> previewReturnPage = new HashMap<>();
    private final Map<UUID, BukkitTask> menuRefreshTasks = new HashMap<>();
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
        for (BukkitTask task : menuRefreshTasks.values()) {
            if (task != null) task.cancel();
        }
        menuRefreshTasks.clear();
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
        List<String> kits = listKits(cfg);
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
            case "list" -> list(sender);
            case "inicial" -> { if (!(sender instanceof Player p)) { sender.sendMessage(plugin.translateColors("%prefix%&cEste subcomando solo funciona en juego.")); return true; } autoKitService.claimInitialKit(p); }
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
        List<String> kits = listKits(cfg);
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
        List<String> kits = listKits(cfg);
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

    private ItemStack kitIcon(Player player, FileConfiguration cfg, String kitName) {
        String path = "kits." + kitName;
        ItemStack base = cfg.getItemStack(path + ".shulker_item"); if (base == null) base = new ItemStack(Material.SHULKER_BOX);
        ItemStack icon = base.clone();
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(plugin.parseComponent(cfg.getString(path + ".display_name", kitName))
                .decoration(TextDecoration.ITALIC, false));
        int items = shulkerAmount(icon);
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
        if (respectCooldown && days > 0) {
            long now = System.currentTimeMillis(), next = nextClaim(target.getUniqueId(), kitName);
            if (next > now) { target.sendMessage(plugin.translateColors(cfg.getString("messages.kit-on-cooldown", "%prefix%&cDebes esperar &e%time% &cpara este kit.").replace("%time%", duration(next - now)))); return false; }
            setNextClaim(target.getUniqueId(), kitName, now + days * 24L * 60L * 60L * 1000L);
        }
        ItemStack shulker = cfg.getItemStack(path + ".shulker_item");
        if (shulker == null) { sender.sendMessage(plugin.translateColors("%prefix%&cError al cargar el item del kit.")); return false; }
        ItemStack give = shulker.clone();
        if (target.getInventory().firstEmpty() == -1) { target.getWorld().dropItemNaturally(target.getLocation(), give); target.sendMessage(plugin.translateColors("%prefix%&eTu inventario estaba lleno, el kit se soltó al suelo.")); }
        else target.getInventory().addItem(give);
        if (!sender.getName().equalsIgnoreCase(target.getName())) sender.sendMessage(plugin.translateColors("%prefix%&aHas dado el kit &e" + kitName + " &aa &f" + target.getName()));
        if (!silentTarget) target.sendMessage(plugin.translateColors(cfg.getString("messages.kit-claimed", "%prefix%&aHas reclamado el kit: &f%kit%").replace("%kit%", cfg.getString(path + ".display_name", kitName))));
        module.debug("Kit entregado: " + kitName + " -> " + target.getName() + " (sender=" + sender.getName() + ")");
        return true;
    }

    private void createKit(Player player, String[] args) {
        String name = args[1];
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() == Material.AIR || !item.getType().name().contains("SHULKER_BOX")) { player.sendMessage(plugin.translateColors("%prefix%&cDebes sostener una Shulker Box en la mano.")); return; }
        ItemMeta meta = item.getItemMeta();
        FileConfiguration cfg = module.getConfig();
        String displayName = "&8Shulker de " + name;
        if (meta != null) {
            Component display = meta.displayName();
            if (display != null) {
                displayName = LegacyComponentSerializer.legacyAmpersand().serialize(display);
            } else if (meta.hasDisplayName()) {
                displayName = meta.getDisplayName().replace('§', '&');
            }
        }
        cfg.set("kits." + name + ".display_name", displayName);
        cfg.set("kits." + name + ".shulker_item", item);
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
        plugin.getConfigManager().saveConfig("kits");
        player.sendMessage(plugin.translateColors("%prefix%&aKit &e" + name + " &acreado correctamente. &7Perm: &f"
                + (finalPerm == null || finalPerm.isBlank() ? "ninguno" : finalPerm) + " &8| &7CD: &f" + finalCooldown + "d"));
    }

    private void previewKit(Player player, String name) {
        FileConfiguration cfg = module.getConfig();
        if (!cfg.contains("kits." + name)) { player.sendMessage(plugin.translateColors("%prefix%&cEl kit &e" + name + " &cno existe.")); return; }
        ItemStack shulker = cfg.getItemStack("kits." + name + ".shulker_item");
        if (shulker == null || !(shulker.getItemMeta() instanceof BlockStateMeta bsm) || !(bsm.getBlockState() instanceof ShulkerBox box)) { player.sendMessage(plugin.translateColors("%prefix%&cError al cargar el contenido del kit.")); return; }
        Inventory preview = Bukkit.createInventory(new KitsPreviewHolder(), 27, plugin.translateColors(cfg.getString("kits." + name + ".display_name", "&8Preview: " + name)));
        preview.setContents(box.getInventory().getContents());
        player.openInventory(preview);
    }

    private void deleteKit(CommandSender sender, String name) { FileConfiguration cfg = module.getConfig(); if (!cfg.contains("kits." + name)) { sender.sendMessage(plugin.translateColors("%prefix%&cEl kit &e" + name + " &cno existe.")); return; } cfg.set("kits." + name, null); plugin.getConfigManager().saveConfig("kits"); sender.sendMessage(plugin.translateColors("%prefix%&cKit &e" + name + " &celiminado.")); }
    private void resetStarter(CommandSender sender, String targetName) { Player t = Bukkit.getPlayer(targetName); if (t == null) { sender.sendMessage(plugin.translateColors("%prefix%&cJugador no encontrado o no está online.")); return; } var d = plugin.getPlayerData(t.getUniqueId()); if (d != null) { d.setStarterKitReceived(false); sender.sendMessage(plugin.translateColors("%prefix%&aFlag de kit inicial reseteado para &f" + t.getName())); } }
    private void list(CommandSender sender) { FileConfiguration cfg = module.getConfig(); ConfigurationSection kits = cfg.getConfigurationSection("kits"); if (kits == null || kits.getKeys(false).isEmpty()) { sender.sendMessage(plugin.translateColors("%prefix%&7No hay kits creados.")); return; } sender.sendMessage(plugin.translateColors("%prefix%&eKits disponibles (&f" + kits.getKeys(false).size() + "&e):")); for (String key : kits.getKeys(false)) { String display = cfg.getString("kits." + key + ".display_name", key); String perm = cfg.getString("kits." + key + ".required-permission", ""); int cd = cfg.getInt("kits." + key + ".cooldown-days", cfg.getInt("settings.default_claim_cooldown_days", 15)); sender.sendMessage(plugin.translateColors(" &8- &f" + key + " &7(" + display + "&7) &8| &7Perm: &f" + (perm.isBlank() ? "ninguno" : perm) + " &8| &7CD: &f" + cd + "d")); } }

    private List<String> listKits(FileConfiguration cfg) { ConfigurationSection s = cfg.getConfigurationSection("kits"); if (s == null) return Collections.emptyList(); return s.getKeys(false).stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList()); }
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
            if (sender.hasPermission("valerinutils.kits.admin")) subs.addAll(List.of("create", "give", "delete", "reset", "debug"));
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("preview") || args[0].equalsIgnoreCase("delete")) {
                ConfigurationSection kits = module.getConfig().getConfigurationSection("kits");
                if (kits != null) return kits.getKeys(false).stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
            }
            if (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("reset")) {
                String p = args[1].toLowerCase(Locale.ROOT);
                return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n != null && n.toLowerCase(Locale.ROOT).startsWith(p)).sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            ConfigurationSection kits = module.getConfig().getConfigurationSection("kits");
            if (kits != null) return kits.getKeys(false).stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[2].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
