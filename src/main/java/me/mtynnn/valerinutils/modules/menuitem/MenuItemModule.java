package me.mtynnn.valerinutils.modules.menuitem;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import me.mtynnn.valerinutils.core.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Sound;
import java.util.Locale;

@SuppressWarnings("deprecation") // Legacy ItemMeta API still required for older server compatibility
public class MenuItemModule implements Module, Listener {

    private final ValerinUtils plugin;
    private final NamespacedKey menuItemKey;

    // Cached values for performance
    private final Set<String> disabledWorlds = new HashSet<>();
    private ItemStack cachedMenuItem = null;
    private int cachedSlot = -1;

    public MenuItemModule(ValerinUtils plugin) {
        this.plugin = plugin;
        this.menuItemKey = new NamespacedKey(plugin, "menuitem");
        loadConfigSettings();
    }

    @Override
    public String getId() {
        return "menuitem";
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Invalidate cache so fresh config values are used
        invalidateCache();
        // Give/refresh items for all online players
        refreshAllPlayers();
        debug("Módulo habilitado.");
    }

    @Override
    public void disable() {
        // Unregister all event handlers
        org.bukkit.event.HandlerList.unregisterAll(this);

        // Remove menu items from all online players
        for (org.bukkit.entity.Player player : plugin.getServer().getOnlinePlayers()) {
            clearMenuItem(player);
        }

        // Invalidate cache
        invalidateCache();
        debug("Módulo deshabilitado.");
    }

    // ================== Persistencia de desactivados (Ahora via PlayerData)
    // ==================

    public boolean isDisabled(Player player) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null)
            return false;
        return data.isMenuDisabled();
    }

    public boolean setDisabled(Player player, boolean disabled) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null)
            return false;

        if (disabled) {
            data.setMenuDisabled(true);
            clearMenuItem(player);
            debug("MenuItem desactivado para " + player.getName());
            return true;
        } else {
            // Verificar si el slot configurado está ocupado
            int slot = getConfiguredSlot();
            ItemStack itemInSlot = player.getInventory().getItem(slot);
            if (itemInSlot != null && !itemInSlot.getType().isAir()) {
                return false; // Slot ocupado, no se puede activar
            }

            data.setMenuDisabled(false);
            giveMenuItem(player);
            debug("MenuItem activado para " + player.getName());
            return true;
        }
    }

    // ================== Helpers de config ==================

    private void loadConfigSettings() {
        disabledWorlds.clear();
        ConfigurationSection section = getSection();
        if (section == null)
            return;

        List<String> worlds = section.getStringList("disabled-worlds");
        disabledWorlds.addAll(worlds);
    }

    private boolean isDisabledWorld(String worldName) {
        return disabledWorlds.contains(worldName);
    }

    private ConfigurationSection getSection() {
        return plugin.getConfigManager().getConfig("menuitem");
    }

    private int getConfiguredSlot() {
        // Return cached value if available
        if (cachedSlot >= 0) {
            return cachedSlot;
        }

        ConfigurationSection section = getSection();
        if (section == null) {
            cachedSlot = 0;
            return 0;
        }

        int slot = section.getInt("slot", 0);
        if (slot < 0)
            slot = 0;
        if (slot > 35)
            slot = 35;
        cachedSlot = slot;
        return slot;
    }

    private String getCommandTemplate() {
        ConfigurationSection section = getSection();
        if (section == null) {
            return "dm open menu-main %player%";
        }
        return section.getString("command", "dm open menu-main %player%");
    }

    private ItemStack createMenuItem() {
        // Return cached copy if available
        if (cachedMenuItem != null) {
            return cachedMenuItem.clone();
        }

        ConfigurationSection section = getSection();
        String materialName = "COMPASS";
        String name = "&aMenu";
        List<String> lore = List.of("&7Item de menú");
        int customModelData = -1;

        if (section != null) {
            materialName = section.getString("material", materialName);
            name = section.getString("name", name);
            lore = section.getStringList("lore");
            if (lore.isEmpty()) {
                lore = List.of("&7Item de menú");
            }
            customModelData = section.getInt("custom-model-data", -1);
        }

        Material material = Material.matchMaterial(materialName);
        if (material == null) {
            material = Material.BOOK;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            Component displayName = plugin.parseComponent(name).decoration(TextDecoration.ITALIC, false);
            meta.displayName(displayName);
            List<Component> loreComponents = lore.stream()
                    .map(line -> plugin.parseComponent(line).decoration(TextDecoration.ITALIC, false))
                    .toList();
            meta.lore(loreComponents);

            if (customModelData >= 0) {
                meta.setCustomModelData(customModelData);
            }

            // marcar como menuitem
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(menuItemKey, PersistentDataType.BYTE, (byte) 1);

            item.setItemMeta(meta);
        }

        cachedMenuItem = item.clone();
        return item;
    }

    /**
     * Invalidate cached values. Call this when config is reloaded.
     */
    public void invalidateCache() {
        cachedMenuItem = null;
        cachedSlot = -1;
    }

    private boolean isMenuItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir())
            return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null)
            return false;

        // 1. Check Permanent Data Container (Tags)
        PersistentDataContainer data = meta.getPersistentDataContainer();
        Byte flag = data.get(menuItemKey, PersistentDataType.BYTE);
        if (flag != null && flag == (byte) 1) {
            return true;
        }

        // 2. Fallback: Check Name & Material (For legacy/glitching items)
        ConfigurationSection section = getSection();
        if (section != null) {
            String cfgName = plugin.translateColors(section.getString("name", ""));
            String stackName = plugin.translateColors(meta.hasDisplayName() ? meta.getDisplayName() : "");
            if (!cfgName.isEmpty() && cfgName.equals(stackName)) {
                String materialName = section.getString("material", "COMPASS");
                if (stack.getType() == Material.matchMaterial(materialName)) {
                    // Update tag for future efficiency (optional, but lets just identifying it as
                    // menu)
                    return true;
                }
            }
        }

        return false;
    }

    public void clearMenuItem(Player player) {
        PlayerInventory inv = player.getInventory();
        boolean changed = false;

        // 1. Check main contents (includes hotbar, inventory, armor, offhand on some
        // versions)
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isMenuItem(contents[i])) {
                contents[i] = null;
                changed = true;
            }
        }
        if (changed)
            inv.setContents(contents);

        // 2. Double check off-hand just in case
        if (isMenuItem(inv.getItemInOffHand())) {
            inv.setItemInOffHand(null);
            changed = true;
        }

        // 3. Check cursor (important if player is moving the item during reload)
        if (isMenuItem(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            changed = true;
        }

        if (changed) {
            player.updateInventory();
            debug("MenuItem removido del inventario de " + player.getName());
        }
    }

    private void giveMenuItem(Player player) {
        if (isDisabled(player)) {
            clearMenuItem(player);
            debug("No se entrega MenuItem a " + player.getName() + ": desactivado por jugador.");
            return;
        }

        if (isDisabledWorld(player.getWorld().getName())) {
            clearMenuItem(player);
            debug("No se entrega MenuItem a " + player.getName() + ": mundo deshabilitado (" + player.getWorld().getName()
                    + ").");
            return;
        }

        ConfigurationSection section = getSection();
        if (section == null) {
            return;
        }

        int slot = getConfiguredSlot();
        PlayerInventory inv = player.getInventory();

        // borrar cualquier copia previa en el inventario
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isMenuItem(contents[i])) {
                contents[i] = null;
            }
        }
        inv.setContents(contents);

        // crear y poner el ítem en el slot configurado
        ItemStack menuItem = createMenuItem();
        inv.setItem(slot, menuItem);
        player.updateInventory();
        debug("MenuItem entregado a " + player.getName() + " en slot " + slot);
    }

    public void refreshAllPlayers() {
        loadConfigSettings();
        for (Player player : Bukkit.getOnlinePlayers()) {
            giveMenuItem(player);
        }
    }

    private void runMenuCommand(Player player) {
        // primero el sonido
        playMenuSound(player);

        // luego el comando
        String template = getCommandTemplate();
        if (template == null || template.isBlank()) {
            return;
        }
        String cmd = template.replace("%player%", player.getName());
        debug("Ejecutando comando de menú para " + player.getName() + ": " + cmd);
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }

    private void playMenuSound(Player player) {
        ConfigurationSection section = getSection();
        if (section == null)
            return;

        ConfigurationSection soundSec = section.getConfigurationSection("sound");
        if (soundSec == null)
            return;

        if (!soundSec.getBoolean("enabled", false)) {
            return;
        }

        String soundName = soundSec.getString("name", "UI_BUTTON_CLICK");
        double vol = soundSec.getDouble("volume", 1.0);
        double pit = soundSec.getDouble("pitch", 1.0);

        float volume = (float) vol;
        float pitch = (float) pit;

        try {
            Sound sound = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, volume, pitch);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("[MenuItem] Sonido inválido en config: " + soundName);
        }
    }

    // ================== Eventos ==================

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        giveMenuItem(player);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        giveMenuItem(event.getPlayer());
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> giveMenuItem(player));
    }

    // Click en inventario (incluye shift-click, números, etc.)
    @EventHandler(priority = EventPriority.HIGHEST) // Removed ignoreCancelled to ensure we always check
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (isDisabled(player))
            return;

        // Support for swapping with numbers
        if (event.getClick() == ClickType.NUMBER_KEY) {
            int button = event.getHotbarButton();
            ItemStack itemInHotbar = player.getInventory().getItem(button);
            if (isMenuItem(itemInHotbar)) {
                event.setCancelled(true);
                // Force update next tick to fix visual glitches
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                return;
            }
        }

        // Support for Swap Offhand in inventory
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            ItemStack offhand = player.getInventory().getItemInOffHand();
            ItemStack current = event.getCurrentItem();
            if (isMenuItem(offhand) || isMenuItem(current)) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(plugin, player::updateInventory);
                return;
            }
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (isMenuItem(current) || isMenuItem(cursor)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }
    }

    // Drag en inventario
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (isDisabled(player))
            return;

        ItemStack oldCursor = event.getOldCursor();
        if (isMenuItem(oldCursor)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }
    }

    // Click derecho/izquierdo en el mundo (con o sin shift)
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND)
            return;

        Player player = event.getPlayer();
        if (isDisabled(player))
            return;

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!isMenuItem(item)) {
            return;
        }

        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {

            event.setCancelled(true);
            runMenuCommand(player);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (isDisabled(player))
            return;

        if (isMenuItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (isDisabled(player))
            return;

        if (isMenuItem(event.getMainHandItem()) || isMenuItem(event.getOffHandItem())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isMenuItem);
    }

    private void debug(String message) {
        plugin.debug(getId(), message);
    }
}
