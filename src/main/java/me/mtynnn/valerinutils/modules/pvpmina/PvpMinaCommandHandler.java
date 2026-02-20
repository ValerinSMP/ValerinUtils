package me.mtynnn.valerinutils.modules.pvpmina;

import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

final class PvpMinaCommandHandler implements CommandExecutor {

    private final ValerinUtils plugin;
    private final PvpMinaRuntime runtime;

    PvpMinaCommandHandler(ValerinUtils plugin, PvpMinaRuntime runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {

        if (args.length > 0 && args[0].equalsIgnoreCase("bossbar")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(runtime.parseComponent("<red>Only players can toggle the BossBar."));
                return true;
            }
            toggleBossBar(player);
            return true;
        }

        if (!sender.hasPermission("valerinutils.pvpmina.admin")) {
            sender.sendMessage(runtime.parseComponent(runtime.getConfig().getString("messages.no-permission", "&cNo permission.")));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(runtime.parseComponent("<yellow>/" + label + " force <mode> | reload | bossbar"));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            runtime.reloadConfigCache();
            runtime.rotateMode(false);
            sender.sendMessage(runtime.parseComponent("<green>PvpMina reloaded."));
            return true;
        }

        if (args[0].equalsIgnoreCase("force") && args.length > 1) {
            String targetMode = args[1].toLowerCase();
            if (!runtime.forceMode(targetMode)) {
                sender.sendMessage(runtime.parseComponent("<red>Invalid mode. Available: " +
                        String.join(", ", runtime.getModeKeys())));
                return true;
            }
            sender.sendMessage(runtime.parseComponent("<green>Forced mode to: " + targetMode));
            return true;
        }

        return true;
    }

    private void toggleBossBar(Player player) {
        NamespacedKey key = new NamespacedKey(plugin, "pvpmina_bossbar_hidden");
        PersistentDataContainer data = player.getPersistentDataContainer();
        if (data.has(key, PersistentDataType.BYTE)) {
            data.remove(key);
            player.sendMessage(runtime.parseComponent(runtime.getConfig().getString("messages.bossbar-toggle-on", "<green>BossBar ON")));
            runtime.updateBossBar();
            return;
        }

        data.set(key, PersistentDataType.BYTE, (byte) 1);
        player.sendMessage(runtime.parseComponent(runtime.getConfig().getString("messages.bossbar-toggle-off", "<red>BossBar OFF")));
        if (runtime.getActiveBossBar() != null) {
            runtime.getActiveBossBar().removePlayer(player);
        }
    }
}
