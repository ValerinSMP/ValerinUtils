package me.mtynnn.valerinutils.modules.codes;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.Module;
import me.mtynnn.valerinutils.utils.SignMenuFactory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CodesModule implements Module, CommandExecutor {

    private final ValerinUtils plugin;
    private SignMenuFactory signMenuFactory;

    public CodesModule(ValerinUtils plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getId() {
        return "codes";
    }

    @Override
    public void enable() {
        FileConfiguration cfg = getConfig();
        if (cfg == null || !cfg.getBoolean("enabled", true))
            return;

        this.signMenuFactory = new SignMenuFactory(plugin);

        if (plugin.getCommand("code") != null) {
            plugin.getCommand("code").setExecutor(this);
        }
    }

    @Override
    public void disable() {
        // Nothing specific to cleanup here besides what's handled by ValerinUtils
    }

    private FileConfiguration getConfig() {
        return plugin.getConfigManager().getConfig("codes");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§cSolo jugadores pueden usar este comando.");
            return true;
        }

        Player player = (Player) sender;
        FileConfiguration cfg = getConfig();
        if (cfg == null || !cfg.getBoolean("enabled", true)) {
            player.sendMessage(plugin.translateColors("%prefix%&cEl sistema de códigos está deshabilitado."));
            return true;
        }

        openCodeInput(player);
        return true;
    }

    private void openCodeInput(Player player) {
        FileConfiguration cfg = getConfig();
        List<String> linesList = cfg.getStringList("code-sign.lines");
        String[] lines = new String[4];
        for (int i = 0; i < 4; i++) {
            lines[i] = i < linesList.size() ? plugin.translateColors(linesList.get(i)) : "";
        }

        signMenuFactory.openSign(player, lines, resultLines -> {
            String input = resultLines[0].trim();
            if (input.isEmpty())
                return;

            Bukkit.getScheduler().runTask(plugin, () -> processCode(player, input));
        });
    }

    private void processCode(Player player, String code) {
        FileConfiguration cfg = getConfig();
        String upperCode = code.toUpperCase();
        ConfigurationSection codeSec = cfg.getConfigurationSection("codes." + upperCode);

        if (codeSec == null) {
            player.sendMessage(plugin.translateColors(cfg.getString("messages.invalid-code", "&cCódigo inválido.")
                    .replace("%code%", code)));
            return;
        }

        boolean once = codeSec.getBoolean("once", true);
        if (once && plugin.getDatabaseManager().hasUsedCode(player.getUniqueId().toString(), upperCode)) {
            player.sendMessage(
                    plugin.translateColors(cfg.getString("messages.already-used", "&cYa has usado este código.")));
            return;
        }

        // Ejecutar recompensas
        List<String> commands = codeSec.getStringList("commands");
        for (String cmd : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
        }

        // Marcar como usado si es de un solo uso
        if (once) {
            plugin.getDatabaseManager().markCodeUsed(player.getUniqueId().toString(), upperCode);
        }

        player.sendMessage(plugin.translateColors(cfg.getString("messages.success", "&a¡Código reclamado!")
                .replace("%code%", code)));
    }
}
