package me.mtynnn.valerinutils.placeholders;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.modules.externalplaceholders.ExternalPlaceholdersModule;
import me.mtynnn.valerinutils.modules.externalplaceholders.providers.PlaceholderProvider;
import me.mtynnn.valerinutils.modules.menuitem.MenuItemModule;
import me.mtynnn.valerinutils.modules.joinquit.JoinQuitModule;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ValerinUtilsExpansion extends PlaceholderExpansion {

    private final ValerinUtils plugin;

    public ValerinUtilsExpansion(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        // %valerinutils_*%
        return "valerinutils";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Valerin";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        // para que no se desregistre en /papi reload
        return true;
    }

    @Override
    public boolean canRegister() {
        return true;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {

        if (player == null) {
            return "";
        }

        // %valerinutils_menuitem_enabled%
        if (params.equalsIgnoreCase("menuitem_enabled")) {
            MenuItemModule module = plugin.getMenuItemModule();
            if (module == null) {
                return "false";
            }

            boolean enabled = !module.isDisabled(player);
            return String.valueOf(enabled);
        }

        // %valerinutils_player_number%
        // %valerinutils_total_players%
        if (params.equals("player_number") || params.equals("total_players")) {
            JoinQuitModule jq = plugin.getJoinQuitModule();
            if (jq != null) {
                return String.valueOf(jq.getUniquePlayerCount());
            }
            return String.valueOf(Bukkit.getOfflinePlayers().length);
        }

        // %valerinutils_first_join_date%
        if (params.equals("first_join_date")) {
            return new java.text.SimpleDateFormat("dd/MM/yyyy HH:mm")
                    .format(new java.util.Date(player.getFirstPlayed()));
        }

        // ========== Placeholders externos ==========
        // Formato: %valerinutils_<plugin>_<parametro>%
        // Ejemplo: %valerinutils_royaleconomy_pay_enabled%

        ExternalPlaceholdersModule extModule = plugin.getExternalPlaceholdersModule();
        if (extModule != null) {
            // Buscar si el params empieza con algún provider conocido
            for (var entry : extModule.getProviders().entrySet()) {
                String providerId = entry.getKey();
                PlaceholderProvider provider = entry.getValue();

                String prefix = providerId + "_";
                if (params.toLowerCase().startsWith(prefix)) {
                    // Extraer la parte después del prefijo del provider
                    String subParams = params.substring(prefix.length());
                    String result = provider.onPlaceholderRequest(player, subParams);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }

        // si no es un placeholder conocido -> null
        return null;
    }
}
