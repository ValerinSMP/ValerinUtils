package me.Mtynnn.valerinUtils.modules.externalplaceholders.providers;

import com.willfp.eco.core.data.PlayerProfile;
import com.willfp.eco.core.data.keys.PersistentDataKey;
import com.willfp.eco.core.data.keys.PersistentDataKeyType;
import me.Mtynnn.valerinUtils.ValerinUtils;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Provider de placeholders para EcoSkills.
 * Usa la API de eco-core para leer datos persistentes del jugador.
 * 
 * Placeholders disponibles:
 * - actionbar_enabled: true si el jugador tiene la actionbar persistente activada
 * - xpgainsound_enabled: true si el jugador tiene el sonido de XP activado
 */
public class EcoSkillsProvider implements PlaceholderProvider {

    private final ValerinUtils plugin;
    
    // Claves de EcoSkills (definidas en su código fuente)
    private final PersistentDataKey<Boolean> actionBarEnabledKey;
    private final PersistentDataKey<Boolean> xpGainSoundEnabledKey;

    public EcoSkillsProvider(ValerinUtils plugin) {
        this.plugin = plugin;
        
        // Recrear las claves exactas que usa EcoSkills
        this.actionBarEnabledKey = new PersistentDataKey<>(
            new NamespacedKey("ecoskills", "actionbar_enabled"),
            PersistentDataKeyType.BOOLEAN,
            true // default = habilitado
        );
        
        this.xpGainSoundEnabledKey = new PersistentDataKey<>(
            new NamespacedKey("ecoskills", "gain_sound_enabled"),
            PersistentDataKeyType.BOOLEAN,
            true // default = habilitado
        );
    }

    @Override
    public @NotNull String getId() {
        return "ecoskills";
    }

    @Override
    public @NotNull String getPluginName() {
        return "EcoSkills";
    }

    @Override
    public @Nullable String onPlaceholderRequest(@NotNull Player player, @NotNull String params) {
        // Obtener el perfil del jugador usando eco-core
        PlayerProfile profile = PlayerProfile.load(player);
        
        return switch (params.toLowerCase()) {
            case "actionbar_enabled" -> {
                boolean enabled = profile.read(actionBarEnabledKey);
                yield String.valueOf(enabled);
            }
            case "xpgainsound_enabled" -> {
                boolean enabled = profile.read(xpGainSoundEnabledKey);
                yield String.valueOf(enabled);
            }
            default -> null;
        };
    }

    @Override
    public void reload() {
        plugin.getLogger().info("[EcoSkillsProvider] Reloaded");
    }
}
