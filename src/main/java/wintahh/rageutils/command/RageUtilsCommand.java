package wintahh.rageutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import wintahh.rageutils.RageUtils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class RageUtilsCommand {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
            registerCommands(dispatcher)
        );
    }

    private static void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
    for (String name : new String[]{"rageutils", "ru"}) {
        dispatcher.register(
            ClientCommandManager.literal(name)
                .executes(ctx -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (mc.player != null) {
                        mc.player.sendMessage(
                            Text.literal("§c[RageUtils] §rHi " + mc.player.getName().getString() + "! Welcome to RageUtils! The current modules are:"),
                            false
                        );
                        mc.player.sendMessage(
                            Text.literal("§c[RageUtils] §rClientSideBlast §7: §e/ru csb"),
                            false
                        );
                    }
                    return 1;
                })
                .then(ClientCommandManager.literal("clientsideblast")
                    .executes(ctx -> {
                        RageUtils.CLIENTSIDE_BLAST.toggle();
                        return 1;
                    }))
                .then(ClientCommandManager.literal("csb")
                    .executes(ctx -> {
                        RageUtils.CLIENTSIDE_BLAST.toggle();
                        return 1;
                    }))
        );
    }
}
}