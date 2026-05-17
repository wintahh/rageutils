package wintahh.rageutils;

import net.fabricmc.api.ClientModInitializer;
import wintahh.rageutils.command.RageUtilsCommand;
import wintahh.rageutils.module.ModuleRegistry;

import wintahh.rageutils.module.ClientSideBlast;
import wintahh.rageutils.module.RateHUD;

public class RageUtils implements ClientModInitializer {

    public static final ClientSideBlast CLIENTSIDE_BLAST = new ClientSideBlast();
    public static final RateHUD RATE_HUD = new RateHUD();

    @Override
    public void onInitializeClient() {
        RageUtilsCommand.register();

        ModuleRegistry.register(CLIENTSIDE_BLAST);

        ModuleRegistry.register(RATE_HUD);
        RATE_HUD.registerEvents();
    }
}