package me.mtynnn.valerinutils.modules.pvpmina;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class PvpMinaRulesListener implements Listener {

    private final ValerinUtils plugin;
    private final PvpMinaRuntime runtime;

    PvpMinaRulesListener(ValerinUtils plugin, PvpMinaRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        checkBlockedItem(event.getPlayer(), event.getItem(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            checkBlockedItem(player, player.getInventory().getItemInMainHand(), event);
            checkBlockedItem(player, player.getInventory().getItemInOffHand(), event);
            return;
        }

        if (event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            if (projectile instanceof org.bukkit.entity.Trident
                    && runtime.getCurrentBlockedItems().contains(Material.TRIDENT)) {
                event.setCancelled(true);
                sendBlockedMessage(player);
            } else if ((projectile instanceof org.bukkit.entity.Arrow
                    || projectile instanceof org.bukkit.entity.SpectralArrow)
                    && (runtime.getCurrentBlockedItems().contains(Material.BOW)
                            || runtime.getCurrentBlockedItems().contains(Material.CROSSBOW))) {
                event.setCancelled(true);
                sendBlockedMessage(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileLaunch(org.bukkit.event.entity.ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof Player player) {
            checkBlockedItem(player, player.getInventory().getItemInMainHand(), event);
            checkBlockedItem(player, player.getInventory().getItemInOffHand(), event);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRiptide(org.bukkit.event.player.PlayerRiptideEvent event) {
        checkBlockedItem(event.getPlayer(), event.getItem(), null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onGlide(org.bukkit.event.entity.EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player) || !event.isGliding()) {
            return;
        }
        if (!runtime.getTargetWorldName().equals(player.getWorld().getName())) {
            return;
        }
        if (runtime.getCurrentBlockedItems().contains(Material.ELYTRA)
                && !player.hasPermission("valerinutils.pvpmina.bypass")) {
            event.setCancelled(true);
            sendBlockedMessage(player);
            player.setCooldown(Material.ELYTRA, 100);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemHeld(org.bukkit.event.player.PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!runtime.getTargetWorldName().equals(player.getWorld().getName())) {
            return;
        }

        var newItem = player.getInventory().getItem(event.getNewSlot());
        if (newItem != null && runtime.getCurrentBlockedItems().contains(newItem.getType())
                && !player.hasPermission("valerinutils.pvpmina.bypass")) {
            player.setCooldown(newItem.getType(), 2400);
            sendBlockedMessage(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHandSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!runtime.getTargetWorldName().equals(player.getWorld().getName())) {
            return;
        }

        var offhandItem = event.getOffHandItem();
        if (offhandItem != null && runtime.getCurrentBlockedItems().contains(offhandItem.getType())
                && !player.hasPermission("valerinutils.pvpmina.bypass")) {
            event.setCancelled(true);
            player.setCooldown(offhandItem.getType(), 200);
            sendBlockedMessage(player);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        if (!runtime.getTargetWorldName().equals(event.getPlayer().getWorld().getName())
                && runtime.getActiveBossBar() != null) {
            runtime.getActiveBossBar().removePlayer(event.getPlayer());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (runtime.getActiveBossBar() != null) {
            runtime.getActiveBossBar().removePlayer(event.getPlayer());
        }
    }

    private void checkBlockedItem(Player player, org.bukkit.inventory.ItemStack item, org.bukkit.event.Cancellable event) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        if (!runtime.getTargetWorldName().equals(player.getWorld().getName())) {
            return;
        }

        boolean debug = plugin.isModuleDebugEnabled("pvpmina");
        if (player.hasPermission("valerinutils.pvpmina.bypass")) {
            if (debug) {
                plugin.debug("pvpmina", player.getName() + " ignored because of bypass permission.");
            }
            return;
        }

        if (!runtime.getCurrentBlockedItems().contains(item.getType())) {
            if (debug) {
                plugin.debug("pvpmina", "Allowed item " + player.getName() + ": " + item.getType());
            }
            return;
        }

        if (debug) {
            plugin.debug("pvpmina", "Blocked item used by " + player.getName() + ": " + item.getType());
        }

        if (event != null) {
            event.setCancelled(true);
        }
        if (!player.hasMetadata("pvpmina_msg_cooldown")) {
            sendBlockedMessage(player);
            player.setMetadata("pvpmina_msg_cooldown", new org.bukkit.metadata.FixedMetadataValue(plugin, true));
            Bukkit.getScheduler().runTaskLater(plugin, () -> player.removeMetadata("pvpmina_msg_cooldown", plugin), 20L);
        }
    }

    private void sendBlockedMessage(Player player) {
        FileConfiguration cfg = runtime.getConfig();
        String msg = cfg.getString("messages.item-blocked", "&cBlocked!")
                .replace("%mode%", runtime.getModeDisplayName(runtime.getCurrentMode()));
        player.sendMessage(runtime.parseComponent(msg));
    }
}
