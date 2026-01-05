package me.Mtynnn.valerinUtils.modules.menuitem;

import me.Mtynnn.valerinUtils.ValerinUtils;
import me.Mtynnn.valerinUtils.core.Module;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Sound;
import java.util.Locale;

public class MenuItemModule implements Module, Listener {

    private final ValerinUtils plugin;
    private final NamespacedKey menuItemKey;

    // data.yml para guardar quién tiene desactivado el item
    private final File dataFile;
    private final FileConfiguration dataConfig;
    private final Set<UUID> disabledPlayers = new HashSet<>();
    private final Set<String> disabledWorlds = new HashSet<>();

    public MenuItemModule(ValerinUtils plugin) {
        this.plugin = plugin;
        this.menuItemKey = new NamespacedKey(plugin, "menuitem");

        // init data.yml
        File folder = plugin.getDataFolder();
        if (!folder.exists()) {
            folder.mkdirs();
        }
        this.dataFile = new File(folder, "menuitem_data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("No se pudo crear menuitem_data.yml: " + e.getMessage());
            }
        }
        this.dataConfig = YamlConfiguration.loadConfiguration(dataFile);
        loadDisabledPlayers();
        loadConfigSettings();
    }

    @Override
    public String getId() {
        return "menuitem";
    }

    @Override
    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void disable() {
        // Bukkit limpia listeners al deshabilitar el plugin
        saveDisabledPlayers();
    }

    // ================== Persistencia de desactivados ==================

    private void loadDisabledPlayers() {
        disabledPlayers.clear();
        for (String s : dataConfig.getStringList("menuitem-disabled")) {
            try {
                disabledPlayers.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveDisabledPlayers() {
        // Recargar antes de escribir para no pisar datos externos si el archivo se toca a mano
        FileConfiguration fresh = YamlConfiguration.loadConfiguration(dataFile);
        var list = disabledPlayers.stream()
                .map(UUID::toString)
                .toList();
        fresh.set("menuitem-disabled", list);
        try {
            fresh.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo guardar menuitem_data.yml: " + e.getMessage());
        }
    }

    public boolean isDisabled(Player player) {
        return disabledPlayers.contains(player.getUniqueId());
    }

    public boolean setDisabled(Player player, boolean disabled) {
        UUID uuid = player.getUniqueId();
        if (disabled) {
            if (disabledPlayers.add(uuid)) {
                saveDisabledPlayers();
            }
            clearMenuItem(player);
            return true;
        } else {
            // Verificar si el slot configurado está ocupado
            int slot = getConfiguredSlot();
            ItemStack itemInSlot = player.getInventory().getItem(slot);
            if (itemInSlot != null && !itemInSlot.getType().isAir()) {
                return false; // Slot ocupado, no se puede activar
            }
            
            if (disabledPlayers.remove(uuid)) {
                saveDisabledPlayers();
            }
            giveMenuItem(player);
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
        return plugin.getConfig().getConfigurationSection("menuitem");
    }

    private int getConfiguredSlot() {
        ConfigurationSection section = getSection();
        if (section == null)
            return 0;

        int slot = section.getInt("slot", 0);
        if (slot < 0)
            slot = 0;
        if (slot > 35)
            slot = 35;
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
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
            lore.replaceAll(line -> ChatColor.translateAlternateColorCodes('&', line));
            meta.setLore(lore);

            if (customModelData >= 0) {
                meta.setCustomModelData(customModelData);
            }

            // marcar como menuitem
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(menuItemKey, PersistentDataType.BYTE, (byte) 1);

            item.setItemMeta(meta);
        }

        return item;
    }

    private boolean isMenuItem(ItemStack stack) {
        if (stack == null || stack.getType().isAir())
            return false;
        ItemMeta meta = stack.getItemMeta();
        if (meta == null)
            return false;
        PersistentDataContainer data = meta.getPersistentDataContainer();
        Byte flag = data.get(menuItemKey, PersistentDataType.BYTE);
        return flag != null && flag == (byte) 1;
    }

    private void clearMenuItem(Player player) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] contents = inv.getContents();
        boolean changed = false;

        for (int i = 0; i < contents.length; i++) {
            if (isMenuItem(contents[i])) {
                contents[i] = null;
                changed = true;
            }
        }

        if (changed) {
            inv.setContents(contents);
            player.updateInventory();
        }
    }

    private void giveMenuItem(Player player) {
        if (isDisabled(player)) {
            clearMenuItem(player);
            return;
        }

        if (isDisabledWorld(player.getWorld().getName())) {
            clearMenuItem(player);
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
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (isDisabled(player))
            return;

        if (event.getClick() == ClickType.NUMBER_KEY) {
            int button = event.getHotbarButton();
            ItemStack itemInHotbar = player.getInventory().getItem(button);
            if (isMenuItem(itemInHotbar)) {
                event.setCancelled(true);
                player.updateInventory();
                return;
            }
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if (isMenuItem(current) || isMenuItem(cursor)) {
            event.setCancelled(true);
            player.updateInventory();
            runMenuCommand(player);
        }
    }

    // Drag en inventario
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;
        if (isDisabled(player))
            return;

        ItemStack oldCursor = event.getOldCursor();
        if (isMenuItem(oldCursor)) {
            event.setCancelled(true);
            player.updateInventory();
            runMenuCommand(player);
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
        if (action == Action.RIGHT_CLICK_AIR ||
                action == Action.RIGHT_CLICK_BLOCK ||
                action == Action.LEFT_CLICK_AIR ||
                action == Action.LEFT_CLICK_BLOCK) {

            event.setCancelled(true);
            runMenuCommand(player);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (isDisabled(player))
            return;

        if (isMenuItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (isDisabled(player))
            return;

        if (isMenuItem(event.getMainHandItem()) || isMenuItem(event.getOffHandItem())) {
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isMenuItem);
    }
}
