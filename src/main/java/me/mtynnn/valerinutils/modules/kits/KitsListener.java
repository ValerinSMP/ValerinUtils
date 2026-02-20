package me.mtynnn.valerinutils.modules.kits;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.server.ServerCommandEvent;

import java.util.ArrayList;
import java.util.List;

final class KitsListener implements Listener {

    private final ValerinUtils plugin;
    private final KitsModule module;
    private final KitsAutoKitService autoKitService;

    private long lastConsoleNoArgsWarnAtMs = 0L;
    private long lastConsoleUnknownWarnAtMs = 0L;
    private long lastConsolePlayerOnlyWarnAtMs = 0L;
    private long lastConsoleTraceAtMs = 0L;

    KitsListener(ValerinUtils plugin, KitsModule module, KitsAutoKitService autoKitService) {
        this.plugin = plugin;
        this.module = module;
        this.autoKitService = autoKitService;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        autoKitService.onJoin(event);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        module.commandHandler().onPlayerQuit(event.getPlayer());
        autoKitService.onQuit(event);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        autoKitService.onDeath(event);
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        autoKitService.onRespawn(event);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Object holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof KitsPreviewHolder || holder instanceof KitsMenuHolder) {
            event.setCancelled(true);
            if (holder instanceof KitsMenuHolder menuHolder && event.getWhoClicked() instanceof org.bukkit.entity.Player player) {
                module.commandHandler().onMenuClick(player, event.getRawSlot(), event.getView().getTopInventory().getSize(),
                        menuHolder, event.getClick());
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Object holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof KitsPreviewHolder || holder instanceof KitsMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryCreative(InventoryCreativeEvent event) {
        Object holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof KitsPreviewHolder || holder instanceof KitsMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Object holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof KitsPreviewHolder || holder instanceof KitsMenuHolder) {
            event.getView().getTopInventory().clear();
        }
        if (holder instanceof KitsMenuHolder && event.getPlayer() instanceof org.bukkit.entity.Player player) {
            module.commandHandler().onMenuClosed(player);
        }
        if (holder instanceof KitsPreviewHolder && event.getPlayer() instanceof org.bukkit.entity.Player player) {
            module.commandHandler().onPreviewClosed(player);
        }
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        String raw = event.getCommand();
        if (raw == null) {
            return;
        }

        String lowered = raw.toLowerCase().trim();
        if (!(lowered.startsWith("kit") || lowered.startsWith("kits") || lowered.startsWith("vukits"))) {
            return;
        }

        if (!module.isDebugEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastConsoleTraceAtMs < 60000L) {
            return;
        }
        lastConsoleTraceAtMs = now;

        CommandSender sender = event.getSender();
        String senderInfo = sender != null ? sender.getClass().getSimpleName() : "null";
        if (sender instanceof BlockCommandSender bcs) {
            senderInfo = "BlockCommandSender@" + bcs.getBlock().getLocation();
        } else if (sender instanceof ConsoleCommandSender) {
            senderInfo = "Console";
        }

        plugin.getLogger().warning("[Kits] Detectado comando desde servidor: /" + raw + " (sender=" + senderInfo + ")");
    }

    void warnConsoleNoArgs(String commandName) {
        long now = System.currentTimeMillis();
        if (now - lastConsoleNoArgsWarnAtMs < 60000L) {
            return;
        }
        lastConsoleNoArgsWarnAtMs = now;
        plugin.getLogger().warning("[Kits] Se está ejecutando /" + commandName
                + " desde consola sin argumentos. Probablemente un command block o otro plugin lo está disparando.");
        maybeLogCallerTrace("no-args", "/" + commandName);
    }

    void warnConsoleUnknownSub(String commandName, String sub) {
        long now = System.currentTimeMillis();
        if (now - lastConsoleUnknownWarnAtMs < 60000L) {
            return;
        }
        lastConsoleUnknownWarnAtMs = now;
        plugin.getLogger().warning("[Kits] Se está ejecutando /" + commandName + " " + sub
                + " desde consola. Probablemente un command block o algún plugin lo está disparando.");
        maybeLogCallerTrace("unknown-sub", "/" + commandName + " " + sub);
    }

    void warnConsolePlayerOnlySub(String commandName, String sub, String[] args, CommandSender sender) {
        long now = System.currentTimeMillis();
        if (now - lastConsolePlayerOnlyWarnAtMs < 60000L) {
            return;
        }
        lastConsolePlayerOnlyWarnAtMs = now;

        String senderInfo = sender != null ? sender.getClass().getSimpleName() : "null";
        if (sender instanceof BlockCommandSender bcs) {
            senderInfo = "BlockCommandSender@" + bcs.getBlock().getLocation();
        } else if (sender instanceof ConsoleCommandSender) {
            senderInfo = "Console";
        }

        String commandLine = "/" + commandName + " " + String.join(" ", args);
        plugin.getLogger().warning("[Kits] Subcomando solo-jugador ejecutado desde servidor: " + commandLine
                + " (sender=" + senderInfo + ").");
        maybeLogCallerTrace("player-only", commandLine);
    }

    private void maybeLogCallerTrace(String reason, String commandLine) {
        if (!module.isDebugEnabled()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastConsoleTraceAtMs < 60000L) {
            return;
        }
        lastConsoleTraceAtMs = now;

        StackTraceElement[] trace = new Exception("Command trace").getStackTrace();
        List<String> lines = new ArrayList<>();
        for (StackTraceElement el : trace) {
            String cls = el.getClassName();
            if (cls.startsWith("me.mtynnn.valerinutils.")) {
                continue;
            }
            if (cls.startsWith("org.bukkit.") || cls.startsWith("io.papermc.") || cls.startsWith("com.destroystokyo.")
                    || cls.startsWith("net.minecraft.") || cls.startsWith("java.") || cls.startsWith("javax.")
                    || cls.startsWith("sun.") || cls.startsWith("jdk.")) {
                continue;
            }
            lines.add("  at " + el);
            if (lines.size() >= 15) {
                break;
            }
        }

        plugin.getLogger().warning("[Kits] debug_command_spam=true (" + reason + "): " + commandLine);
        if (lines.isEmpty()) {
            plugin.getLogger().warning("[Kits] No se pudo determinar el origen (solo stack interno/NMS).");
        } else {
            for (String line : lines) {
                plugin.getLogger().warning(line);
            }
        }
    }
}
