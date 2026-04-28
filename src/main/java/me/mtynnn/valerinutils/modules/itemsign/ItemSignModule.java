package me.mtynnn.valerinutils.modules.itemsign;

import me.mtynnn.valerinutils.ValerinUtils;
import me.mtynnn.valerinutils.core.BaseModule;

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
    protected void onEnableModule() {
        if (!isEnabledInConfig()) {
            return;
        }
        registerCommand("sign", commandHandler);
        registerCommand("itemsign", commandHandler);
    }
}
