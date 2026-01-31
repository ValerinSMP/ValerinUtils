package me.mtynnn.valerinutils.modules.externalplaceholders.providers;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provider de placeholders para RoyaleEconomy.
 * Mantiene el estado de pay_enabled sincronizado con la base de datos de
 * ValerinUtils.
 * 
 * Placeholders disponibles:
 * - pay_enabled: true si el jugador puede recibir pagos, false si los tiene
 * desactivados
 */
public class RoyalEconomyProvider implements PlaceholderProvider, Listener {

    private final ValerinUtils plugin;

    public RoyalEconomyProvider(ValerinUtils plugin) {
        this.plugin = plugin;

        // Registrar listener para interceptar comandos
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public @NotNull String getId() {
        return "royaleconomy";
    }

    @Override
    public @NotNull String getPluginName() {
        return "RoyaleEconomy";
    }

    @Override
    public @Nullable String onPlaceholderRequest(@NotNull Player player, @NotNull String params) {
        return switch (params.toLowerCase()) {
            case "pay_enabled" -> {
                PlayerData data = plugin.getPlayerData(player.getUniqueId());
                if (data == null) {
                    yield "true"; // Default: enabled
                }
                boolean isDisabled = data.isRoyalPayDisabled();
                yield String.valueOf(!isDisabled);
            }
            default -> null;
        };
    }

    @Override
    public void reload() {
        plugin.getLogger().info("[RoyalEconomyProvider] Data reloaded (using database)");
    }

    /**
     * Intercepta el comando /pay toggle para actualizar nuestro cache en tiempo
     * real
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (event.isCancelled())
            return;

        String message = event.getMessage().toLowerCase();

        // Detectar variantes del comando de toggle
        // /pay toggle, /pagar toggle, etc.
        if (isPayToggleCommand(message)) {
            Player player = event.getPlayer();

            // Esperar un tick para que RoyaleEconomy procese el comando primero
            new BukkitRunnable() {
                @Override
                public void run() {
                    togglePayStatus(player);
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    /**
     * Detecta si el comando es un toggle de pay
     */
    private boolean isPayToggleCommand(String command) {
        // Remover el / inicial
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        // Comandos conocidos de RoyaleEconomy para pay toggle
        String[] payCommands = { "pay", "pagar" };

        for (String payCmd : payCommands) {
            if (command.equals(payCmd + " toggle") || command.startsWith(payCmd + " toggle ")) {
                return true;
            }
        }

        return false;
    }

    /**
     * Cambia el estado de pay del jugador en PlayerData
     */
    private void togglePayStatus(Player player) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null)
            return;

        boolean currentlyDisabled = data.isRoyalPayDisabled();
        data.setRoyalPayDisabled(!currentlyDisabled);
    }

    /**
     * Verifica si un jugador tiene el pay desactivado
     */
    public boolean isPayDisabled(Player player) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null)
            return false;
        return data.isRoyalPayDisabled();
    }

    /**
     * Establece el estado de pay de un jugador
     */
    public void setPayDisabled(Player player, boolean disabled) {
        PlayerData data = plugin.getPlayerData(player.getUniqueId());
        if (data == null)
            return;
        data.setRoyalPayDisabled(disabled);
    }
}
