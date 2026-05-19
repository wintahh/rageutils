package wintahh.rageutils;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import wintahh.rageutils.command.RageUtilsCommand;
import wintahh.rageutils.module.ModuleRegistry;

import wintahh.rageutils.module.ClientSideBlast;
import wintahh.rageutils.module.GhostBlockFix;
import wintahh.rageutils.module.RateHUD;
import wintahh.rageutils.module.SnappyInput;

public class RageUtils implements ClientModInitializer {

    public static final ClientSideBlast CLIENTSIDE_BLAST = new ClientSideBlast();
    public static final RateHUD RATE_HUD = new RateHUD();
    public static final GhostBlockFix GHOST_BLOCK_FIX = new GhostBlockFix();
    public static final SnappyInput SNAPPY_INPUT = new SnappyInput();

    @Override
    public void onInitializeClient() {
        RageUtilsCommand.register();

        ModuleRegistry.register(CLIENTSIDE_BLAST);

        ModuleRegistry.register(RATE_HUD);
        RATE_HUD.registerEvents();

        ModuleRegistry.register(GHOST_BLOCK_FIX);
        ModuleRegistry.register(SNAPPY_INPUT);
        ClientTickEvents.END_CLIENT_TICK.register(GHOST_BLOCK_FIX::onClientTick);
    }
}
