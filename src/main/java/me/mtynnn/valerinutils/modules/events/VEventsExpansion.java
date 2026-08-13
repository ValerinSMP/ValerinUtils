package me.mtynnn.valerinutils.modules.events;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.mtynnn.valerinutils.ValerinUtils;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

final class VEventsExpansion extends PlaceholderExpansion {
    private final ValerinUtils plugin;
    private final EventsModule module;

    VEventsExpansion(ValerinUtils plugin, EventsModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @Override public @NotNull String getIdentifier() { return "vevents"; }
    @Override public @NotNull String getAuthor() { return "VALERIN"; }
    @Override public @NotNull String getVersion() { return plugin.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        return module.placeholder(params);
    }
}
