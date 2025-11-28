package me.Mtynnn.valerinUtils.modules.externalplaceholders.providers;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interfaz para proveedores de placeholders externos.
 * Cada provider representa un plugin externo del cual queremos leer datos.
 */
public interface PlaceholderProvider {

    /**
     * @return El id del provider (ej: "royaleconomy")
     */
    @NotNull
    String getId();

    /**
     * @return El nombre del plugin externo que este provider lee
     */
    @NotNull
    String getPluginName();

    /**
     * Procesa un placeholder para un jugador.
     * 
     * @param player El jugador para el que se solicita el placeholder
     * @param params Los parámetros del placeholder (ej: para %valerinutils_royaleconomy_pay_enabled%,
     *               params sería "pay_enabled")
     * @return El valor del placeholder, o null si no existe
     */
    @Nullable
    String onPlaceholderRequest(@NotNull Player player, @NotNull String params);

    /**
     * Recarga los datos del provider (ej: recargar archivos de configuración)
     */
    void reload();
}
