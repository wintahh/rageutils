package wintahh.rageutils;

import net.fabricmc.api.ClientModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import wintahh.rageutils.command.RageUtilsCommand;
import wintahh.rageutils.module.ClientSideBlast;

public class RageUtils implements ClientModInitializer {

    public static final ClientSideBlast CLIENTSIDE_BLAST = new ClientSideBlast();

    @Override
    public void onInitializeClient() {
        RageUtilsCommand.register();
    }
}