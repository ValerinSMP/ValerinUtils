package me.mtynnn.valerinutils.core;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.regex.Pattern;

public class ConfigManager {
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Map<String, Object> UTILITIES_DEFAULTS = buildUtilitiesDefaults();

    private final ValerinUtils plugin;
    private final Map<String, FileConfiguration> configs = new HashMap<>();
    private final Map<String, File> files = new HashMap<>();

    public ConfigManager(ValerinUtils plugin) {
        this.plugin = plugin;
        setupModulesFolder();
    }

    private void setupModulesFolder() {
        File modulesFolder = new File(plugin.getDataFolder(), "modules");
        if (!modulesFolder.exists()) {
            modulesFolder.mkdirs();
        }
    }

    public void loadAll() {
        // 1. Initialize all configs (Create defaults from JAR if missing)
        registerConfig("settings", "settings.yml");
        registerConfig("debug", "debug.yml");
        registerConfig("killrewards", "modules/killrewards.yml");
        registerConfig("joinquit", "modules/joinquit.yml");
        registerConfig("vote40", "modules/vote40.yml");
        registerConfig("menuitem", "modules/menuitem.yml");
        registerConfig("deathmessages", "modules/deathmessages.yml");
        registerConfig("geodes", "modules/geodes.yml");
        registerConfig("votetracking", "modules/votetracking.yml");
        registerConfig("kits", "modules/kits.yml");
        registerConfig("codes", "modules/codes.yml");
        registerConfig("utilities", "modules/utilities.yml");
        registerConfig("itemeditor", "modules/itemeditor.yml");
        registerConfig("pvpmina", "modules/pvpmina.yml");
        registerConfig("sellprice", "modules/sellprice.yml");

        backupConfigsOnVersionChange();
        // 2. Check for migration (Will merge legacy values into the just-created
        // defaults)
        migrateLegacyConfig(); // Legacy config.yml -> new structure

        // 3. Update Settings (Add missing keys for updates)
        updateSettingsConfig();

        // 4. Update Module Configs (Add missing keys from new versions)
        updateModuleConfigs();
        updateDebugConfig();

        // 5. One-way migration: legacy color codes -> MiniMessage in all configs
        migrateLegacyFormattingToMiniMessage();
        applyAestheticThemeToAllMessages();
    }

    private void updateSettingsConfig() {
        FileConfiguration settings = getConfig("settings");
        if (settings == null)
            return;

        boolean changed = false;

        // Verify menuitem messages
        if (!settings.contains("messages.menuitem-cooldown")) {
            settings.set("messages.menuitem-cooldown",
                    "%prefix%<red>Debes esperar <yellow>%time%s <red>para volver a usar esto.");
            changed = true;
        }

        // Setup missing menuitem keys if needed
        if (!settings.contains("messages.menuitem-usage")) {
            settings.set("messages.menuitem-usage", "%prefix%<gray>Uso: <yellow>/menu item <on|off|toggle>");
            changed = true;
        }
        if (!settings.contains("messages.menuitem-on")) {
            settings.set("messages.menuitem-on", "%prefix%<green>Item de menú activado.");
            changed = true;
        }
        if (!settings.contains("messages.menuitem-off")) {
            settings.set("messages.menuitem-off", "%prefix%<red>Item de menú desactivado.");
            changed = true;
        }
        if (!settings.contains("messages.menuitem-toggled-on")) {
            settings.set("messages.menuitem-toggled-on", "%prefix%<green>Item de menú activado.");
            changed = true;
        }
        if (!settings.contains("messages.menuitem-toggled-off")) {
            settings.set("messages.menuitem-toggled-off", "%prefix%<red>Item de menú desactivado.");
            changed = true;
        }
        if (!settings.contains("messages.menuitem-slot-occupied")) {
            settings.set("messages.menuitem-slot-occupied",
                    "%prefix%<red>El slot está ocupado, no se puede dar el item.");
            changed = true;
        }

        if (changed) {
            saveConfig("settings");
            plugin.getLogger().info("settings.yml updated with new keys.");
        }
    }

    /**
     * Auto-update module configs with new keys from defaults.
     * This ensures users coming from older versions get new features.
     */
    private void updateModuleConfigs() {
        // JoinQuit - Add MOTD section if missing
        updateJoinQuitConfig();
        updateKitsConfig();
        updateUtilitiesConfig();
        updateItemEditorConfig();
        updateDeathMessagesConfig();
        updateCodesConfig();
    }

    private void updateJoinQuitConfig() {
        FileConfiguration config = getConfig("joinquit");
        if (config == null)
            return;

        boolean changed = false;

        // Add MOTD section keys individually (so partial configs get updated too)
        if (!config.contains("join.motd.enabled")) {
            config.set("join.motd.enabled", false);
            changed = true;
        }
        if (!config.contains("join.motd.clear-chat")) {
            config.set("join.motd.clear-chat", true);
            changed = true;
        }
        if (!config.contains("join.motd.lines")) {
            config.set("join.motd.lines", java.util.Arrays.asList(
                    "",
                    "<gold>╔══════════════════════════════════════╗",
                    "<gold>║  <aqua><bold>BIENVENIDO A TU SERVIDOR  <gold>║",
                    "<gold>╠══════════════════════════════════════╣",
                    "<gold>║  <gray>❯ <white>Jugadores Online: <green>%server_online%/%server_max_players%",
                    "<gold>║  <gray>❯ <white>Tu Rango: <green>%luckperms_prefix%",
                    "<gold>╚══════════════════════════════════════╝",
                    ""));
            changed = true;
        }

        if (changed) {
            saveConfig("joinquit");
            plugin.getLogger().info("[JoinQuit] Config updated with new keys.");
        }
    }

    private void updateKitsConfig() {
        FileConfiguration config = getConfig("kits");
        if (config == null)
            return;

        boolean changed = false;

        if (!config.contains("settings.debug_command_spam")) {
            config.set("settings.debug_command_spam", false);
            changed = true;
        }
        if (!config.contains("settings.respawn_kit_only_on_death")) {
            config.set("settings.respawn_kit_only_on_death", true);
            changed = true;
        }
        if (!config.contains("settings.respawn_kit_overwrite")) {
            config.set("settings.respawn_kit_overwrite", false);
            changed = true;
        }
        if (!config.contains("settings.respawn_kit_disabled_worlds")) {
            config.set("settings.respawn_kit_disabled_worlds", java.util.Collections.emptyList());
            changed = true;
        }
        if (!config.contains("settings.default_claim_cooldown_days")) {
            config.set("settings.default_claim_cooldown_days", 15);
            changed = true;
        }
        if (!config.contains("settings.default_required_permission_pattern")) {
            config.set("settings.default_required_permission_pattern", "kits.%kit%");
            changed = true;
        }
        if (!config.contains("messages.kit-no-permission")) {
            config.set("messages.kit-no-permission",
                    "%prefix%<red>No cumples el rango/permiso requerido para este kit.");
            changed = true;
        }
        if (!config.contains("messages.kit-on-cooldown")) {
            config.set("messages.kit-on-cooldown",
                    "%prefix%<red>Debes esperar <yellow>%time% <red>para volver a reclamar este kit.");
            changed = true;
        }
        if (!config.contains("messages.kit-claimed")) {
            config.set("messages.kit-claimed",
                    "%prefix%<green>Has reclamado el kit: <white>%kit%");
            changed = true;
        }
        if (!config.contains("sounds.menu-open")) {
            config.set("sounds.menu-open", "BLOCK_CHEST_OPEN");
            config.set("sounds.menu-click", "UI_BUTTON_CLICK");
            config.set("sounds.menu-page", "ITEM_BOOK_PAGE_TURN");
            config.set("sounds.menu-close", "BLOCK_CHEST_CLOSE");
            config.set("sounds.menu-back", "BLOCK_NOTE_BLOCK_BASS");
            config.set("sounds.preview-open", "BLOCK_ENDER_CHEST_OPEN");
            config.set("sounds.claim-success", "ENTITY_PLAYER_LEVELUP");
            config.set("sounds.claim-fail", "BLOCK_NOTE_BLOCK_BASS");
            changed = true;
        }
        if (!config.contains("claim-effects.enabled")) {
            config.set("claim-effects.enabled", true);
            config.set("claim-effects.particles.enabled", true);
            config.set("claim-effects.particles.type", "TOTEM_OF_UNDYING");
            config.set("claim-effects.particles.amount", 25);
            config.set("claim-effects.particles.offset", 0.35);
            config.set("claim-effects.particles.speed", 0.05);
            config.set("claim-effects.title.enabled", true);
            config.set("claim-effects.title.title", "&#77DD77ᴋɪᴛ ʀᴇᴄʟᴀᴍᴀᴅᴏ");
            config.set("claim-effects.title.subtitle", "&fDisfruta tu recompensa");
            config.set("claim-effects.title.fade-in", 5);
            config.set("claim-effects.title.stay", 30);
            config.set("claim-effects.title.fade-out", 8);
            config.set("claim-effects.actionbar", "&#77DD77+ Kit recibido");
            config.set("claim-effects.extra-sound.enabled", true);
            config.set("claim-effects.extra-sound.name", "BLOCK_AMETHYST_BLOCK_CHIME");
            config.set("claim-effects.extra-sound.volume", 0.8);
            config.set("claim-effects.extra-sound.pitch", 1.4);
            changed = true;
        }
        if (!config.contains("menu") && config.contains("casual-kit-menu")) {
            config.set("menu", config.get("casual-kit-menu"));
            changed = true;
        }
        if (!config.contains("menu.title")) {
            config.set("menu.title", "&8sᴇʟᴇᴄᴄɪᴏɴᴀ ᴋɪᴛ ᴄᴀsᴜᴀʟ");
            changed = true;
        }
        if (!config.contains("menu.rows")) {
            config.set("menu.rows", 6);
            changed = true;
        }
        if (!config.contains("menu.kit-slots")) {
            config.set("menu.kit-slots", java.util.Arrays.asList(
                    10, 11, 12, 13, 14, 15, 16,
                    19, 20, 21, 22, 23, 24, 25,
                    28, 29, 30, 31, 32, 33, 34));
            changed = true;
        }
        if (!config.contains("menu.filler-slots")) {
            config.set("menu.filler-slots", java.util.Arrays.asList(
                    "0-3", "5-9", "17-18", "26-27", "35-36", "44", "46-47", "51-53"));
            changed = true;
        }
        if (!config.contains("menu.kit-lore")) {
            config.set("menu.kit-lore", java.util.Arrays.asList(
                    "&8• &fItems dentro: &#FF6961%items%",
                    "&8• &fCooldown: &#FF6961%days%d",
                    "&8• &fPermiso: &#FF6961%perm%",
                    "&8• &fEstado: &#FF6961%status%",
                    "&8• &fClick izq: &#77DD77Reclamar",
                    "&8• &fClick der: &#FFB347Preview"));
            changed = true;
        }
        if (!config.contains("menu.items.filler.material")) {
            config.set("menu.items.filler.material", "BLACK_STAINED_GLASS_PANE");
            config.set("menu.items.filler.name", " ");
            config.set("menu.items.filler.lore", java.util.Collections.emptyList());
            changed = true;
        }
        if (!config.contains("menu.items.info.slot")) {
            config.set("menu.items.info.slot", 4);
            config.set("menu.items.info.material", "GLOBE_BANNER_PATTERN");
            config.set("menu.items.info.name", "&#FF6961ɪɴғᴏ");
            config.set("menu.items.info.lore", java.util.Arrays.asList(
                    "&fAquí puedes reclamar kits por rango.",
                    "&fCada kit tiene su propio cooldown.",
                    "&fClick izquierdo: &#77DD77Reclamar kit",
                    "&fClick derecho: &#FFB347Ver contenido",
                    "&fPágina: &#FF6961%page%&7/&#FF6961%max_page%"));
            changed = true;
        }
        if (!config.contains("menu.items.back.slot")) {
            config.set("menu.items.back.slot", 45);
            config.set("menu.items.back.material", "ARROW");
            config.set("menu.items.back.name", "&#FF6961ᴀᴛʀᴀs");
            config.set("menu.items.back.lore", java.util.List.of("&fVolver al menú principal"));
            config.set("menu.items.back.command-mode", "console");
            config.set("menu.items.back.command", "dm open menu %player%");
            changed = true;
        }
        if (!config.contains("menu.items.back.command-mode")) {
            config.set("menu.items.back.command-mode", "console");
            changed = true;
        }
        if (!config.contains("menu.items.previous.slot")) {
            config.set("menu.items.previous.slot", 48);
            config.set("menu.items.previous.material", "YELLOW_STAINED_GLASS_PANE");
            config.set("menu.items.previous.name", "&#FFB347ᴘᴀɢɪɴᴀ ᴀɴᴛᴇʀɪᴏʀ");
            config.set("menu.items.previous.lore", java.util.List.of("&fVer página anterior"));
            changed = true;
        }
        if (!config.contains("menu.items.close.slot")) {
            config.set("menu.items.close.slot", 49);
            config.set("menu.items.close.material", "BARRIER");
            config.set("menu.items.close.name", "&#FF6961ᴄᴇʀʀᴀʀ");
            config.set("menu.items.close.lore", java.util.List.of("&fCerrar este menú"));
            changed = true;
        }
        if (!config.contains("menu.items.next.slot")) {
            config.set("menu.items.next.slot", 50);
            config.set("menu.items.next.material", "LIME_STAINED_GLASS_PANE");
            config.set("menu.items.next.name", "&#77DD77sɪɢᴜɪᴇɴᴛᴇ");
            config.set("menu.items.next.lore", java.util.List.of("&fVer siguiente página"));
            changed = true;
        }

        ConfigurationSection legacyNestedMessages = config.getConfigurationSection("messages.messages");
        if (legacyNestedMessages != null) {
            for (String key : legacyNestedMessages.getKeys(false)) {
                if (!config.contains("messages." + key)) {
                    config.set("messages." + key, legacyNestedMessages.get(key));
                }
            }
            config.set("messages.messages", null);
            changed = true;
        }

        ConfigurationSection kitsSection = config.getConfigurationSection("kits");
        if (kitsSection != null) {
            for (String kitId : kitsSection.getKeys(false)) {
                String displayPath = "kits." + kitId + ".display_name";
                String display = config.getString(displayPath, "");
                if (display != null && display.contains("&x<")) {
                    ItemStack shulker = config.getItemStack("kits." + kitId + ".shulker_item");
                    if (shulker != null) {
                        ItemMeta meta = shulker.getItemMeta();
                        if (meta != null && meta.hasDisplayName()) {
                            config.set(displayPath, meta.getDisplayName());
                            changed = true;
                        }
                    }
                }
            }
        }

        if (changed) {
            saveConfig("kits");
            plugin.getLogger().info("[Kits] Config updated with new keys.");
        }
    }

    private void updateUtilitiesConfig() {
        FileConfiguration config = getConfig("utilities");
        if (config == null)
            return;

        boolean changed = applyMissingDefaults(config, UTILITIES_DEFAULTS);
        if (changed) {
            saveConfig("utilities");
            plugin.getLogger().info("[Utility] Config updated with new keys.");
        }
    }

    private void updateItemEditorConfig() {
        FileConfiguration config = getConfig("itemeditor");
        if (config == null) {
            return;
        }

        boolean changed = false;
        if (!config.contains("enabled")) {
            config.set("enabled", true);
            changed = true;
        }
        if (!config.contains("settings.max-lore-lines")) {
            config.set("settings.max-lore-lines", 20);
            changed = true;
        }
        if (!config.contains("messages.usage")) {
            config.set("messages.usage", "%prefix%<gray>Uso: <yellow>/itemedit <name|lore> ...");
            changed = true;
        }
        if (!config.contains("messages.no-permission")) {
            config.set("messages.no-permission", "%prefix%<red>No tienes permisos.");
            changed = true;
        }
        if (!config.contains("messages.only-players")) {
            config.set("messages.only-players", "%prefix%<red>Solo jugadores pueden usar este comando.");
            changed = true;
        }

        if (changed) {
            saveConfig("itemeditor");
            plugin.getLogger().info("[ItemEditor] Config updated with new keys.");
        }
    }

    private boolean applyMissingDefaults(FileConfiguration config, Map<String, Object> defaults) {
        boolean changed = false;
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            if (config.contains(entry.getKey())) {
                continue;
            }
            config.set(entry.getKey(), entry.getValue());
            changed = true;
        }
        return changed;
    }

    private static Map<String, Object> buildUtilitiesDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();

        defaults.put("enabled", true);

        defaults.put("commands.craft.enabled", true);
        defaults.put("commands.enderchest.enabled", true);
        defaults.put("commands.anvil.enabled", true);
        defaults.put("commands.smithing.enabled", true);
        defaults.put("commands.cartography.enabled", true);
        defaults.put("commands.grindstone.enabled", true);
        defaults.put("commands.loom.enabled", true);
        defaults.put("commands.stonecutter.enabled", true);
        defaults.put("commands.disposal.enabled", true);
        defaults.put("commands.hat.enabled", true);
        defaults.put("commands.condense.enabled", true);
        defaults.put("commands.seen.enabled", true);
        defaults.put("commands.seen.others-enabled", true);
        defaults.put("commands.gamemode.enabled", true);
        defaults.put("commands.clear.enabled", true);
        defaults.put("commands.clear.others-enabled", true);
        defaults.put("commands.ping.enabled", true);
        defaults.put("commands.ping.others-enabled", true);
        defaults.put("commands.fly.enabled", true);
        defaults.put("commands.fly.others-enabled", true);
        defaults.put("commands.speed.enabled", true);
        defaults.put("commands.speed.others-enabled", true);
        defaults.put("commands.broadcast.enabled", true);
        defaults.put("commands.heal.enabled", true);
        defaults.put("commands.heal.others-enabled", true);
        defaults.put("commands.feed.enabled", true);
        defaults.put("commands.feed.others-enabled", true);
        defaults.put("commands.repair.enabled", true);
        defaults.put("commands.nick.enabled", true);
        defaults.put("commands.skull.enabled", true);
        defaults.put("commands.suicide.enabled", true);
        defaults.put("commands.near.enabled", true);
        defaults.put("commands.top.enabled", true);
        defaults.put("commands.ptime.enabled", true);
        defaults.put("commands.pweather.enabled", true);
        defaults.put("commands.sell.enabled", true);

        defaults.put("sounds.craft", "BLOCK_CHEST_OPEN");
        defaults.put("sounds.enderchest", "BLOCK_ENDER_CHEST_OPEN");
        defaults.put("sounds.anvil", "BLOCK_ANVIL_USE");
        defaults.put("sounds.smithing", "BLOCK_SMITHING_TABLE_USE");
        defaults.put("sounds.cartography", "UI_CARTOGRAPHY_TABLE_TAKE_RESULT");
        defaults.put("sounds.grindstone", "BLOCK_GRINDSTONE_USE");
        defaults.put("sounds.loom", "UI_LOOM_TAKE_RESULT");
        defaults.put("sounds.stonecutter", "UI_STONECUTTER_TAKE_RESULT");
        defaults.put("sounds.disposal", "BLOCK_BARREL_OPEN");
        defaults.put("sounds.hat-equip", "ITEM_ARMOR_EQUIP_GENERIC");
        defaults.put("sounds.condense", "BLOCK_ANVIL_USE");
        defaults.put("sounds.clear-inv", "ENTITY_ITEM_PICKUP");
        defaults.put("sounds.gamemode-change", "BLOCK_NOTE_BLOCK_CHIME");
        defaults.put("sounds.heal", "ENTITY_PLAYER_LEVELUP");
        defaults.put("sounds.broadcast", "BLOCK_NOTE_BLOCK_PLING");

        defaults.put("messages.no-permission", "%prefix%<red>No tienes permiso para usar este comando.");
        defaults.put("messages.only-players", "%prefix%<red>Solo jugadores pueden usar este comando.");
        defaults.put("messages.module-disabled", "%prefix%<red>Este comando está deshabilitado.");
        defaults.put("messages.player-not-found", "%prefix%<red>Jugador no encontrado.");
        defaults.put("messages.disposal-title", "<dark_gray>Basurero");
        defaults.put("messages.hat-success", "%prefix%<green>¡Nuevo sombrero equipado!");
        defaults.put("messages.clear-success", "%prefix%<green>Inventario de <white>%player% <green>limpiado.");
        defaults.put("messages.clear-success-self", "%prefix%<green>Tu inventario ha sido limpiado.");
        defaults.put("messages.condense-success", "%prefix%<green>Se han condensado <white>%count% <green>items en bloques.");
        defaults.put("messages.condense-nothing", "%prefix%<gray>No hay nada que condensar en tu inventario.");
        defaults.put("messages.gamemode-success", "%prefix%<green>Modo de juego cambiado a <yellow>%mode%<green>.");
        defaults.put("messages.ping-self", "%prefix%<gray>Tu ping es de: <yellow>%ping%ms");
        defaults.put("messages.ping-other", "%prefix%<gray>El ping de <white>%player% <gray>es de: <yellow>%ping%ms");
        defaults.put("messages.fly-enabled", "%prefix%<gray>Modo vuelo: <green>Activado");
        defaults.put("messages.fly-disabled", "%prefix%<gray>Modo vuelo: <red>Desactivado");
        defaults.put("messages.fly-others", "%prefix%<gray>Modo vuelo de <white>%player%<gray>: <yellow>%state%");
        defaults.put("messages.speed-invalid", "%prefix%<red>La velocidad debe estar entre 1 y 10.");
        defaults.put("messages.speed-usage", "%prefix%<gray>Uso: <yellow>/speed <1-10> [jugador]");
        defaults.put("messages.speed-success", "%prefix%<gray>Tu velocidad de <yellow>%type% <gray>ha sido ajustada a <green>%speed%<gray>.");
        defaults.put("messages.speed-others", "%prefix%<gray>Velocidad de <yellow>%type% <gray>de <white>%player% <gray>ajustada a <green>%speed%<gray>.");
        defaults.put("messages.heal-success", "%prefix%<green>Has sido curado.");
        defaults.put("messages.heal-others", "%prefix%<green>Has curado a <white>%player%<green>.");
        defaults.put("messages.feed-success", "%prefix%<green>Tu hambre ha sido saciada.");
        defaults.put("messages.feed-others", "%prefix%<green>Has alimentado a <white>%player%<green>.");
        defaults.put("messages.broadcast-usage", "%prefix%<gray>Uso: <yellow>/broadcast <mensaje> <gray>o <yellow>/vubroadcast <mensaje>");
        defaults.put("messages.broadcast-format", List.of(
                "",
                "<dark_gray>[<color:#B6E7B5><bold>ᴀ<color:#ABE29C><bold>ɴ<color:#9FDC82><bold>ᴜ<color:#94D769><bold>ɴ<color:#88D150><bold>ᴄ<color:#7DCC36><bold>ɪ<color:#71C61D><bold>ᴏ<dark_gray>] <reset>%message%",
                ""));
        defaults.put("messages.repair-success", "%prefix%<green>Item reparado con éxito.");
        defaults.put("messages.repair-usage", "%prefix%<gray>Uso: <yellow>/fix hand");
        defaults.put("messages.repair-error", "%prefix%<red>Este item no se puede reparar.");
        defaults.put("messages.nick-usage", "%prefix%<gray>Uso: <yellow>/nick <apodo|off>");
        defaults.put("messages.nick-format-not-allowed", "%prefix%<red>No puedes usar ese formato en el nick. Nivel: <yellow>%tier%");
        defaults.put("messages.nick-success", "%prefix%<gray>Tu apodo ahora es: <white>%nick%");
        defaults.put("messages.nick-off", "%prefix%<red>Has desactivado tu apodo.");
        defaults.put("messages.skull-success", "%prefix%<green>Has recibido la cabeza de <white>%player%<green>.");
        defaults.put("messages.suicide-msg", "%prefix%<gray>Has decidido terminar con todo...");
        defaults.put("messages.top-success", "%prefix%<green>Teletransportado a la superficie.");
        defaults.put("messages.ptime-usage", "%prefix%<gray>Uso: <yellow>/ptime <day|night|reset|ticks>");
        defaults.put("messages.ptime-set", "%prefix%<green>Tiempo personal cambiado a <yellow>%value%<green>.");
        defaults.put("messages.ptime-reset", "%prefix%<green>Tiempo personal reseteado.");
        defaults.put("messages.pweather-usage", "%prefix%<gray>Uso: <yellow>/pweather <clear|rain|reset>");
        defaults.put("messages.pweather-set", "%prefix%<green>Clima personal cambiado a <yellow>%value%<green>.");
        defaults.put("messages.pweather-reset", "%prefix%<green>Clima personal reseteado.");
        defaults.put("messages.sell-usage", "%prefix%<gray>Uso: <yellow>/sell <hand|inventory>");
        defaults.put("messages.sell-disabled", "%prefix%<red>El sistema de venta está deshabilitado.");
        defaults.put("messages.sell-economy-missing", "%prefix%<red>No se detectó economía (Vault).");
        defaults.put("messages.sell-nothing", "%prefix%<gray>No tienes items vendibles.");
        defaults.put("messages.sell-success", "%prefix%<green>Vendiste <white>%items% <green>items por <yellow>$%amount%<green>.");
        defaults.put("messages.near-format", "%prefix%<gray>Jugadores cercanos en <yellow>%radius%m<gray>: <white>%players%");
        defaults.put("messages.near-none", "%prefix%<gray>No hay jugadores cerca.");
        defaults.put("messages.seen-online", "<green>Online");
        defaults.put("messages.seen-offline", "<gray>Offline hace %time%");
        defaults.put("messages.seen-usage", "%prefix%<gray>Uso: <yellow>/seen <jugador>");
        defaults.put("messages.seen-format", List.of(
                "<gray><strikethrough>----------------------------------------",
                "  <gold><bold>Información del Jugador: <white>%player%",
                "  <dark_gray>> <gray>Estado: <yellow>%status%",
                "  <dark_gray>> <gray>UUID: <white>%uuid%",
                "  <dark_gray>> <gray>IP: <white>%ip%",
                "  <dark_gray>> <gray>Ingreso inicial: <white>%first_join%",
                "  <dark_gray>> <gray>Última sesión: <white>%last_seen%",
                "",
                "  <gold><bold>Estado Actual:",
                "  <dark_gray>> <gray>Ubicación: <white>%world% <dark_gray>(<yellow>%x%, %y%, %z%<dark_gray>)",
                "  <dark_gray>> <gray>Salud: <red>%health%❤ <gray>| Hambre: <gold>%hunger%🍖",
                "  <dark_gray>> <gray>XP: <green>Nivel %xp% <gray>| Gamemode: <white>%gamemode%",
                "  <dark_gray>> <gray>Fly: <white>%fly%",
                "<gray><strikethrough>----------------------------------------"));

        return defaults;
    }

    private void backupConfigsOnVersionChange() {
        FileConfiguration settings = getConfig("settings");
        if (settings == null) {
            return;
        }
        String currentVersion = plugin.getPluginMeta().getVersion();
        String previousVersion = settings.getString("meta.last-plugin-version", "");
        if (previousVersion.isEmpty() || previousVersion.equals(currentVersion)) {
            settings.set("meta.last-plugin-version", currentVersion);
            saveConfig("settings");
            return;
        }

        for (File file : files.values()) {
            if (file == null || !file.exists()) {
                continue;
            }
            File backup = new File(file.getParentFile(), file.getName() + ".back");
            try {
                Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo crear backup de config: " + file.getName());
            }
        }

        settings.set("meta.last-plugin-version", currentVersion);
        saveConfig("settings");
        plugin.getLogger().info("Version change detected (" + previousVersion + " -> " + currentVersion
                + "). Config backups (*.back) created.");
    }

    private void updateDeathMessagesConfig() {
        FileConfiguration config = getConfig("deathmessages");
        if (config == null)
            return;

        boolean changed = false;

        if (!config.contains("spawn.first-join.enabled")) {
            config.set("spawn.first-join.enabled", false);
            changed = true;
        }
        if (!config.contains("spawn.first-join.location")) {
            config.set("spawn.first-join.location", "world_lobby;-4;141;107;0;0");
            changed = true;
        }
        if (!config.contains("spawn.first-join.delay-ticks")) {
            config.set("spawn.first-join.delay-ticks", 1);
            changed = true;
        }

        if (!config.contains("spawn.on-death.enabled")) {
            config.set("spawn.on-death.enabled", false);
            changed = true;
        }
        if (!config.contains("spawn.on-death.location")) {
            config.set("spawn.on-death.location", "world_lobby;-4;141;107;0;0");
            changed = true;
        }

        if (changed) {
            saveConfig("deathmessages");
            plugin.getLogger().info("[DeathMessages] Config updated with new keys.");
        }
    }

    private void updateCodesConfig() {
        FileConfiguration config = getConfig("codes");
        if (config == null) {
            return;
        }

        boolean changed = false;
        if (!config.contains("messages.disabled")) {
            config.set("messages.disabled", "%prefix%<red>El sistema de códigos está deshabilitado.");
            changed = true;
        }

        if (changed) {
            saveConfig("codes");
            plugin.getLogger().info("[Codes] Config updated with new keys.");
        }
    }

    public FileConfiguration getConfig(String name) {
        return configs.get(name);
    }

    public void reloadConfigs() {
        for (String id : files.keySet()) {
            File file = files.get(id);
            if (file.exists()) {
                configs.put(id, YamlConfiguration.loadConfiguration(file));
            }
        }
        // Re-run auto-updater after reload to ensure defaults are there
        updateSettingsConfig();
        updateModuleConfigs();
        updateDebugConfig();
        migrateLegacyFormattingToMiniMessage();
        applyAestheticThemeToAllMessages();
        plugin.getLogger().info("All configuration files reloaded.");
    }

    private void applyAestheticThemeToAllMessages() {
        FileConfiguration settings = getConfig("settings");
        if (settings == null) {
            return;
        }

        if (!settings.contains("messages.aesthetic-theme-enabled")) {
            settings.set("messages.aesthetic-theme-enabled", true);
            saveConfig("settings");
        }

        if (!settings.getBoolean("messages.aesthetic-theme-enabled", true)) {
            return;
        }

        boolean anyChanged = false;
        for (String id : configs.keySet()) {
            FileConfiguration cfg = configs.get(id);
            if (cfg == null || !cfg.contains("messages")) {
                continue;
            }
            if (applyAestheticThemeToMessages(cfg, cfg, "messages")) {
                saveConfig(id);
                anyChanged = true;
            }
        }

        if (anyChanged) {
            plugin.getLogger().info("Aesthetic theme applied to module messages.");
        }
    }

    private boolean applyAestheticThemeToMessages(FileConfiguration root, ConfigurationSection section, String path) {
        boolean changed = false;
        for (String key : section.getKeys(false)) {
            String currentPath = path + "." + key;
            Object value = section.get(key);

            if (value instanceof ConfigurationSection child) {
                if (applyAestheticThemeToMessages(root, child, currentPath)) {
                    changed = true;
                }
                continue;
            }

            if ("messages.prefix".equals(currentPath)) {
                continue;
            }

            if (value instanceof String text) {
                String styled = applyAestheticPalette(text);
                if (!styled.equals(text)) {
                    root.set(currentPath, styled);
                    changed = true;
                }
                continue;
            }

            if (value instanceof List<?> list && !list.isEmpty()) {
                boolean listChanged = false;
                List<Object> styledList = new ArrayList<>(list.size());
                for (Object item : list) {
                    if (item instanceof String text) {
                        String styled = applyAestheticPalette(text);
                        styledList.add(styled);
                        if (!styled.equals(text)) {
                            listChanged = true;
                        }
                    } else {
                        styledList.add(item);
                    }
                }
                if (listChanged) {
                    root.set(currentPath, styledList);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private String applyAestheticPalette(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        if (!input.contains("%prefix%")) {
            return input;
        }

        String out = input;
        out = out.replace("<dark_red>", "<color:#FF6961>");
        out = out.replace("<red>", "<color:#FF6961>");
        out = out.replace("<dark_green>", "<color:#77DD77>");
        out = out.replace("<green>", "<color:#77DD77>");
        out = out.replace("<gold>", "<color:#FFB347>");
        out = out.replace("<yellow>", "<color:#FFB347>");
        out = out.replace("<dark_gray>", "<color:#B8B8B8>");
        out = out.replace("<gray>", "<color:#D0D0D0>");
        out = out.replace("<aqua>", "<color:#9FE2FF>");
        out = out.replace("<blue>", "<color:#8AB4FF>");
        return out;
    }

    private void updateDebugConfig() {
        FileConfiguration debug = getConfig("debug");
        if (debug == null) {
            return;
        }

        boolean changed = false;
        String[] modules = {
                "menuitem", "joinquit", "votetracking",
                "killrewards", "codes", "deathmessages", "geodes", "kits", "utility", "pvpmina", "itemeditor"
        };
        for (String moduleId : modules) {
            String path = "modules." + moduleId + ".enabled";
            if (!debug.contains(path)) {
                debug.set(path, false);
                changed = true;
            }
        }

        if (changed) {
            saveConfig("debug");
            plugin.getLogger().info("debug.yml updated with module debug keys.");
        }
    }

    public void saveConfig(String name) {
        if (!configs.containsKey(name) || !files.containsKey(name))
            return;
        try {
            configs.get(name).save(files.get(name));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config: " + name, e);
        }
    }

    private void registerConfig(String id, String path) {
        File file = new File(plugin.getDataFolder(), path);

        if (!file.exists()) {
            try {
                if (plugin.getResource(path) != null) {
                    plugin.saveResource(path, false);
                } else {
                    file.createNewFile();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        files.put(id, file);
        configs.put(id, YamlConfiguration.loadConfiguration(file));
    }

    private void migrateLegacyConfig() {
        File oldConfig = new File(plugin.getDataFolder(), "config.yml");
        if (!oldConfig.exists())
            return; // No migration needed or already migrated

        plugin.getLogger().info("Detected legacy config.yml. Starting migration to ValerinUtils 2.0 structure...");
        FileConfiguration legacy = YamlConfiguration.loadConfiguration(oldConfig);

        // Migrate Settings
        File settingsFile = new File(plugin.getDataFolder(), "settings.yml");
        FileConfiguration settings = YamlConfiguration.loadConfiguration(settingsFile);
        settings.set("debug", legacy.getBoolean("debug", false));
        settings.set("database.type", "sqlite"); // Default
        try {
            settings.save(settingsFile);
            // Refresh loaded config
            configs.put("settings", settings);
        } catch (Exception e) {
        }

        // Migrate Modules
        migrateModule(legacy, "modules.killrewards", "modules/killrewards.yml", "killrewards");
        migrateModule(legacy, "modules.vote40", "modules/vote40.yml", "vote40");
        migrateModule(legacy, "modules.joinquit", "modules/joinquit.yml", "joinquit");
        migrateModule(legacy, "joinquit", "modules/joinquit.yml", "joinquit");
        migrateModule(legacy, "modules.menuitem", "modules/menuitem.yml", "menuitem");
        migrateModule(legacy, "menuitem", "modules/menuitem.yml", "menuitem");
        migrateModule(legacy, "votetracking", "modules/votetracking.yml", "votetracking");
        migrateModule(legacy, "modules.votetracking", "modules/votetracking.yml", "votetracking");

        // Rename old config
        File backup = new File(plugin.getDataFolder(), "config.yml.old");
        oldConfig.renameTo(backup);
        plugin.getLogger().info("Migration complete. config.yml renamed to config.yml.old");
    }

    private void migrateModule(FileConfiguration legacy, String legacyPath, String newPath, String configKey) {
        if (!legacy.contains(legacyPath))
            return;

        File newFile = new File(plugin.getDataFolder(), newPath);
        FileConfiguration newConfig = YamlConfiguration.loadConfiguration(newFile);

        ConfigurationSection section = legacy.getConfigurationSection(legacyPath);
        if (section != null) {
            for (String key : section.getKeys(true)) {
                newConfig.set(key, section.get(key));
            }
        } else {
            Object val = legacy.get(legacyPath);
            if (val instanceof Boolean) {
                newConfig.set("enabled", val);
            }
        }

        try {
            newConfig.save(newFile);
            configs.put(configKey, newConfig);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to migrate " + legacyPath);
        }
    }

    private void migrateLegacyFormattingToMiniMessage() {
        for (String id : configs.keySet()) {
            FileConfiguration cfg = configs.get(id);
            if (cfg == null) {
                continue;
            }

            boolean changed = migrateSectionStrings(cfg, cfg);
            if (changed) {
                saveConfig(id);
                plugin.getLogger().info("[" + id + "] migrated legacy formatting to MiniMessage.");
            }
        }
    }

    private boolean migrateSectionStrings(FileConfiguration root, ConfigurationSection section) {
        boolean changed = false;

        for (String key : section.getKeys(false)) {
            Object value = section.get(key);
            if (value instanceof ConfigurationSection child) {
                if (migrateSectionStrings(root, child)) {
                    changed = true;
                }
                continue;
            }

            String path = section.getCurrentPath() == null ? key : section.getCurrentPath() + "." + key;
            if (value instanceof String text) {
                if (shouldSkipLegacyMigration(path)) {
                    continue;
                }
                String converted = legacyToMiniMessage(text);
                if (!converted.equals(text)) {
                    root.set(path, converted);
                    changed = true;
                }
                continue;
            }

            if (value instanceof List<?> list && !list.isEmpty()) {
                boolean listChanged = false;
                List<Object> migrated = new ArrayList<>(list.size());
                for (Object obj : list) {
                    if (obj instanceof String text) {
                        if (shouldSkipLegacyMigration(path)) {
                            migrated.add(text);
                            continue;
                        }
                        String converted = legacyToMiniMessage(text);
                        migrated.add(converted);
                        if (!converted.equals(text)) {
                            listChanged = true;
                        }
                    } else {
                        migrated.add(obj);
                    }
                }

                if (listChanged) {
                    root.set(path, migrated);
                    changed = true;
                }
            }
        }

        return changed;
    }

    private boolean shouldSkipLegacyMigration(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("kits.") && path.endsWith(".display_name");
    }

    private String legacyToMiniMessage(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        String input = text.replace('§', '&');
        if (input.indexOf('&') < 0) {
            return text;
        }

        String withHex = LEGACY_HEX_PATTERN.matcher(input).replaceAll("<color:#$1>");
        StringBuilder output = new StringBuilder(withHex.length() + 16);

        for (int index = 0; index < withHex.length(); index++) {
            char ch = withHex.charAt(index);
            if (ch == '&' && index + 1 < withHex.length()) {
                char code = Character.toLowerCase(withHex.charAt(index + 1));
                String replacement = switch (code) {
                    case '0' -> "<black>";
                    case '1' -> "<dark_blue>";
                    case '2' -> "<dark_green>";
                    case '3' -> "<dark_aqua>";
                    case '4' -> "<dark_red>";
                    case '5' -> "<dark_purple>";
                    case '6' -> "<gold>";
                    case '7' -> "<gray>";
                    case '8' -> "<dark_gray>";
                    case '9' -> "<blue>";
                    case 'a' -> "<green>";
                    case 'b' -> "<aqua>";
                    case 'c' -> "<red>";
                    case 'd' -> "<light_purple>";
                    case 'e' -> "<yellow>";
                    case 'f' -> "<white>";
                    case 'k' -> "<obfuscated>";
                    case 'l' -> "<bold>";
                    case 'm' -> "<strikethrough>";
                    case 'n' -> "<underlined>";
                    case 'o' -> "<italic>";
                    case 'r' -> "<reset>";
                    default -> null;
                };

                if (replacement != null) {
                    output.append(replacement);
                    index++;
                    continue;
                }
            }
            output.append(ch);
        }

        return output.toString();
    }

}
