package me.mtynnn.valerinutils.modules.kits;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

final class KitsAutoKitService {

    private static final int PLAYER_INV_SIZE = 36;
    private static final long INITIAL_KIT_COOLDOWN_MS = 5L * 60L * 1000L;

    private final ValerinUtils plugin;
    private final KitsModule module;
    private final Map<UUID, Long> initialKitNextUseAtMs = new ConcurrentHashMap<>();
    private final Map<UUID, String> lastDeathWorldByPlayer = new ConcurrentHashMap<>();

    KitsAutoKitService(ValerinUtils plugin, KitsModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    void clear() {
        initialKitNextUseAtMs.clear();
        lastDeathWorldByPlayer.clear();
    }

    void claimInitialKit(Player player) {
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
        module.debug("Kit inicial manual entregado a " + player.getName());
    }

    void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        FileConfiguration cfg = module.getConfig();
        if (!cfg.getBoolean("settings.starter_kit_enabled", true)) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            var data = plugin.getPlayerData(player.getUniqueId());
            if (data == null) {
                return;
            }

            if (!data.isStarterKitReceived()) {
                giveAutoKit(player, false);
                data.setStarterKitReceived(true);
                module.debug("Starter kit auto entregado en join a " + player.getName());
            }
        }, 10L);
    }

    void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        initialKitNextUseAtMs.remove(uuid);
        lastDeathWorldByPlayer.remove(uuid);
    }

    void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (victim.getWorld() != null) {
            lastDeathWorldByPlayer.put(victim.getUniqueId(), victim.getWorld().getName().toLowerCase());
        }
    }

    void onRespawn(PlayerRespawnEvent event) {
        FileConfiguration cfg = module.getConfig();
        if (!cfg.getBoolean("settings.respawn_kit_enabled", true)) {
            return;
        }

        String deathWorld = lastDeathWorldByPlayer.remove(event.getPlayer().getUniqueId());
        if (deathWorld != null) {
            List<String> disabledWorlds = cfg.getStringList("settings.respawn_kit_disabled_worlds");
            for (String world : disabledWorlds) {
                if (world != null && deathWorld.equalsIgnoreCase(world.trim())) {
                    module.debug("Respawn kit omitido para " + event.getPlayer().getName()
                            + ": mundo deshabilitado (" + deathWorld + ").");
                    return;
                }
            }
        }

        boolean onlyOnDeath = cfg.getBoolean("settings.respawn_kit_only_on_death", true);
        if (onlyOnDeath) {
            try {
                if (event.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH) {
                    module.debug("Respawn kit omitido para " + event.getPlayer().getName()
                            + ": razón de respawn no es DEATH.");
                    return;
                }
            } catch (Throwable ignored) {
            }
        }

        boolean overwrite = cfg.getBoolean("settings.respawn_kit_overwrite", false);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                giveAutoKit(event.getPlayer(), overwrite);
                module.debug("Respawn kit entregado a " + event.getPlayer().getName() + " overwrite=" + overwrite);
            }
        }, 5L);
    }

    private void giveAutoKit(Player player, boolean overwriteExisting) {
        FileConfiguration cfg = module.getConfig();
        ConfigurationSection auto = cfg.getConfigurationSection("auto_kit");
        if (auto == null) {
            return;
        }

        ConfigurationSection armor = auto.getConfigurationSection("armor");
        if (armor != null) {
            setArmor(player, armor, overwriteExisting);
        }

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

    private ItemStack getItem(String materialName) {
        if (materialName == null || materialName.isEmpty()) {
            return null;
        }
        try {
            return new ItemStack(Material.valueOf(materialName.toUpperCase()));
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }
}
