package me.mtynnn.valerinutils.modules.codes;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.BaseModule;
import me.mtynnn.valerinutils.utils.SignMenuFactory;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class CodesModule extends BaseModule implements CommandExecutor, TabCompleter {
    private SignMenuFactory signMenuFactory;

    public CodesModule(ValerinUtils plugin) {
        super(plugin);
    }

    @Override
    public String getId() {
        return "codes";
    }

    @Override
    protected void onEnableModule() {
        FileConfiguration cfg = getConfig();
        if (cfg == null || !cfg.getBoolean("enabled", true))
            return;

        this.signMenuFactory = new SignMenuFactory(plugin);
        plugin.debug(getId(), "Módulo habilitado. Sistema de códigos activo.");

        registerCommand("code", this, this);
    }

    @Override
    protected void onDisableModule() {
        plugin.debug(getId(), "Módulo deshabilitado. Comando /code liberado.");
    }

    private FileConfiguration getConfig() {
        return cfg();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.getMessage("only-players"));
            return true;
        }

        Player player = (Player) sender;
        FileConfiguration cfg = getConfig();
        if (cfg == null || !cfg.getBoolean("enabled", true)) {
            player.sendMessage(plugin.messages().module(getId(), "disabled", "%prefix%<red>El sistema de códigos está deshabilitado."));
            plugin.debug(getId(), "Intento de /code con módulo deshabilitado por " + player.getName());
            return true;
        }

        // Si se pasa el código como argumento (/code SUNDIR), procesarlo directamente.
        // Útil para jugadores de Bedrock que no pueden usar el cartel.
        if (args.length >= 1) {
            String codeArg = args[0].trim();
            if (!codeArg.isEmpty()) {
                processCode(player, codeArg);
                return true;
            }
        }

        // Sin argumentos: abrir el cartel (comportamiento original)
        openCodeInput(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            FileConfiguration cfg = getConfig();
            if (cfg == null)
                return Collections.emptyList();
            ConfigurationSection codesSection = cfg.getConfigurationSection("codes");
            if (codesSection == null)
                return Collections.emptyList();
            String partial = args[0].toUpperCase();
            return codesSection.getKeys(false).stream()
                    .filter(k -> k.toUpperCase().startsWith(partial))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    private void openCodeInput(Player player) {
        FileConfiguration cfg = getConfig();
        List<String> linesList = cfg.getStringList("code-sign.lines");
        String[] lines = new String[4];
        for (int i = 0; i < 4; i++) {
            lines[i] = i < linesList.size() ? plugin.translateColors(linesList.get(i)) : "";
        }

        signMenuFactory.openSign(player, lines, resultLines -> {
            String input = extractCodeInput(resultLines, cfg);
            if (input.isEmpty())
                return;

            Bukkit.getScheduler().runTask(plugin, () -> processCode(player, input));
        });
    }

    private String extractCodeInput(String[] resultLines, FileConfiguration cfg) {
        if (resultLines == null || resultLines.length == 0) {
            return "";
        }

        ConfigurationSection codesSection = cfg != null ? cfg.getConfigurationSection("codes") : null;
        if (codesSection != null) {
            java.util.Set<String> validCodes = codesSection.getKeys(false).stream()
                    .map(String::toUpperCase)
                    .collect(java.util.stream.Collectors.toSet());

            for (String line : resultLines) {
                if (line == null) {
                    continue;
                }
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String upper = trimmed.toUpperCase();
                if (validCodes.contains(upper)) {
                    return trimmed;
                }

                java.util.regex.Matcher prefixed = java.util.regex.Pattern
                        .compile("(?i)^(?:codigo|código|code)\\s*[:\\-]*\\s*(\\S+)$")
                        .matcher(trimmed);
                if (prefixed.matches()) {
                    String candidate = prefixed.group(1).trim();
                    if (validCodes.contains(candidate.toUpperCase())) {
                        return candidate;
                    }
                }

                for (String token : trimmed.split("[\\s,:;|]+")) {
                    if (token.isBlank()) {
                        continue;
                    }
                    if (validCodes.contains(token.toUpperCase())) {
                        return token;
                    }
                }
            }
        }

        for (String line : resultLines) {
            if (line == null) {
                continue;
            }
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                if (trimmed.matches("^[↑↓→←\\-_=~.]+$")) {
                    continue;
                }
                return trimmed;
            }
        }
        return "";
    }

    private void processCode(Player player, String code) {
        FileConfiguration cfg = getConfig();
        String upperCode = code.toUpperCase();
        ConfigurationSection codeSec = cfg.getConfigurationSection("codes." + upperCode);

        if (codeSec == null) {
            plugin.debug(getId(), "Código inválido '" + upperCode + "' por " + player.getName());
            player.sendMessage(plugin.messages().legacy(cfg.getString("messages.invalid-code", "<red>Código inválido.")
                    .replace("%code%", code)));
            return;
        }

        boolean once = codeSec.getBoolean("once", true);
        if (once && plugin.getDatabaseManager().hasUsedCode(player.getUniqueId().toString(), upperCode)) {
            plugin.debug(getId(), "Código ya usado '" + upperCode + "' por " + player.getName());
            player.sendMessage(plugin.messages().legacy(cfg.getString("messages.already-used",
                    "<red>Ya has usado este código.")));
            return;
        }

        // Ejecutar recompensas
        List<String> commands = codeSec.getStringList("commands");
        for (String cmd : commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
        }
        plugin.debug(getId(), "Código '" + upperCode + "' reclamado por " + player.getName() + " | comandos="
                + commands.size());

        // Marcar como usado si es de un solo uso
        if (once) {
            plugin.getDatabaseManager().markCodeUsed(player.getUniqueId().toString(), upperCode);
        }

        player.sendMessage(plugin.messages().legacy(cfg.getString("messages.success", "<green>¡Código reclamado!")
                .replace("%code%", code)));
    }
}
