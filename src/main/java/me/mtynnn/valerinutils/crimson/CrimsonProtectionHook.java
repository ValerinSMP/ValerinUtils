package me.mtynnn.valerinutils.crimson;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.Listener;

public interface CrimsonProtectionHook extends Listener {
    void reload(FileConfiguration settings);
}
