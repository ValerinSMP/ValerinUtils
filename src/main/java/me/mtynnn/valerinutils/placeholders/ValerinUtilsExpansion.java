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
        return plugin.getPluginMeta().getVersion();
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

        // %valerinutils_deathmessages_enabled%
        if (params.equalsIgnoreCase("deathmessages_enabled")) {
            var data = plugin.getPlayerData(player.getUniqueId());
            if (data == null)
                return "true"; // Default
            return String.valueOf(!data.isDeathMessagesDisabled());
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

        // ================== Vote Tracking Placeholders ==================

        if (params.startsWith("votes_")) {
            String uuid = player.getUniqueId().toString();
            java.time.ZoneId zone = java.time.ZoneId.systemDefault();

            // %valerinutils_votes_total%
            if (params.equals("votes_total")) {
                return String.valueOf(plugin.getDatabaseManager().getTotalVotes(uuid));
            }

            // %valerinutils_votes_daily%
            if (params.equals("votes_daily")) {
                java.time.LocalDate today = java.time.LocalDate.now(zone);
                long start = today.atStartOfDay(zone).toInstant().toEpochMilli();
                long end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1;
                return String.valueOf(plugin.getDatabaseManager().getVotesBetween(uuid, start, end));
            }

            // %valerinutils_votes_monthly%
            if (params.equals("votes_monthly")) {
                java.time.YearMonth currentMonth = java.time.YearMonth.now(zone);
                long start = currentMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
                long end = currentMonth.atEndOfMonth().atTime(23, 59, 59, 999999999).atZone(zone).toInstant()
                        .toEpochMilli();
                return String.valueOf(plugin.getDatabaseManager().getVotesBetween(uuid, start, end));
            }

            // %valerinutils_votes_month_<1-12>_<year>% (Year optional, defaults to current)
            // Example: votes_month_1 (Jan current year)
            // Example: votes_month_1_2025
            if (params.startsWith("votes_month_")) {
                try {
                    String[] parts = params.split("_");
                    int month = Integer.parseInt(parts[2]);
                    int year = java.time.Year.now(zone).getValue();
                    if (parts.length > 3) {
                        year = Integer.parseInt(parts[3]);
                    }

                    if (month < 1 || month > 12)
                        return "0";

                    java.time.YearMonth target = java.time.YearMonth.of(year, month);
                    long start = target.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
                    long end = target.atEndOfMonth().atTime(23, 59, 59, 999999999).atZone(zone).toInstant()
                            .toEpochMilli();

                    return String.valueOf(plugin.getDatabaseManager().getVotesBetween(uuid, start, end));
                } catch (Exception e) {
                    return "0";
                }
            }

            // %valerinutils_votes_year_<year>%
            if (params.startsWith("votes_year_")) {
                try {
                    int year = Integer.parseInt(params.split("_")[2]);
                    long start = java.time.Year.of(year).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli();
                    long end = java.time.Year.of(year).atMonth(12).atEndOfMonth().atTime(23, 59, 59).atZone(zone)
                            .toInstant().toEpochMilli();

                    return String.valueOf(plugin.getDatabaseManager().getVotesBetween(uuid, start, end));
                } catch (Exception e) {
                    return "0";
                }
            }

            // %valerinutils_votes_quarter_<1-4>_<year>% (Year optional)
            if (params.startsWith("votes_quarter_")) {
                try {
                    String[] parts = params.split("_");
                    int q = Integer.parseInt(parts[2]);
                    int year = java.time.Year.now(zone).getValue();
                    if (parts.length > 3) {
                        year = Integer.parseInt(parts[3]);
                    }

                    if (q < 1 || q > 4)
                        return "0";

                    // Q1: 1, Q2: 4, Q3: 7, Q4: 10
                    int startMonth = (q - 1) * 3 + 1;
                    // End month: start + 2
                    int endMonth = startMonth + 2;

                    long start = java.time.YearMonth.of(year, startMonth).atDay(1).atStartOfDay(zone).toInstant()
                            .toEpochMilli();
                    long end = java.time.YearMonth.of(year, endMonth).atEndOfMonth().atTime(23, 59, 59).atZone(zone)
                            .toInstant().toEpochMilli();

                    return String.valueOf(plugin.getDatabaseManager().getVotesBetween(uuid, start, end));
                } catch (Exception e) {
                    return "0";
                }
            }
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
