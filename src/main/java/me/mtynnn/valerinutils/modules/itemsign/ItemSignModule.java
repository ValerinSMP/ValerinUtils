package me.mtynnn.valerinutils.modules.itemsign;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.BaseModule;
import java.util.Set;

public final class ItemSignModule extends BaseModule {

    private final ItemSignCommandHandler commandHandler;

    public ItemSignModule(ValerinUtils plugin) {
        super(plugin);
        this.commandHandler = new ItemSignCommandHandler(plugin);
    }

    @Override
    public String getId() {
        return "itemsign";
    }

    @Override
    public Set<String> getCommandNames() {
        return Set.of("sign", "itemsign");
    }

    @Override
    protected void onEnableModule() {
        if (!isEnabledInConfig()) {
            return;
        }
        registerCommand("sign", commandHandler);
        registerCommand("itemsign", commandHandler);
    }
}
