package me.mtynnn.valerinutils.utils;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Utilidad simple para abrir un editor de cartel al jugador y recibir el texto.
 */
public class SignMenuFactory {

    private final ValerinUtils plugin;
    private final Map<UUID, Consumer<String[]>> inputHandlers = new HashMap<>();

    public SignMenuFactory(ValerinUtils plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(new SignListener(), plugin);
    }

    public void openSign(Player player, String[] lines, Consumer<String[]> response) {
        // Encontrar una ubicación cercana estable
        Location loc = player.getLocation().clone().add(0, 3, 0);
        Block block = loc.getBlock();
        Material oldType = block.getType();

        // Colocar el cartel físicamente
        block.setType(Material.OAK_SIGN);
        Sign sign = (Sign) block.getState();

        // Configurar texto en AMBOS lados para máxima compatibilidad
        try {
            for (org.bukkit.block.sign.Side side : org.bukkit.block.sign.Side.values()) {
                org.bukkit.block.sign.SignSide sideData = sign.getSide(side);
                for (int i = 0; i < 4; i++) {
                    sideData.setLine(i, lines[i]);
                }
            }
        } catch (Throwable ignored) {
            // Fallback si la API de lados no existe (versiones muy antiguas)
            for (int i = 0; i < 4; i++) {
                sign.setLine(i, lines[i]);
            }
        }

        sign.setEditable(true);
        sign.update(true, false);

        inputHandlers.put(player.getUniqueId(), response);

        // Delay para asegurar que el cliente procesó el bloque antes de abrir el editor
        new BukkitRunnable() {
            @Override
            public void run() {
                if (player.isOnline()) {
                    player.openSign(sign);
                }
            }
        }.runTaskLater(plugin, 2L);

        // Limpiar después de 5 segundos si algo falla
        new BukkitRunnable() {
            @Override
            public void run() {
                if (block.getType() == Material.OAK_SIGN) {
                    block.setType(oldType);
                }
            }
        }.runTaskLater(plugin, 100L);
    }

    private class SignListener implements Listener {
        @EventHandler
        public void onSignChange(SignChangeEvent event) {
            UUID uuid = event.getPlayer().getUniqueId();
            if (inputHandlers.containsKey(uuid)) {
                Consumer<String[]> handler = inputHandlers.remove(uuid);
                handler.accept(event.getLines());

                // Cancelar el evento para que no se guarde el cartel en el mundo real
                event.setCancelled(true);

                // Restaurar el bloque inmediatamente
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        event.getBlock().setType(Material.AIR);
                    }
                }.runTask(plugin);
            }
        }
    }
}
