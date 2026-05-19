package wintahh.rageutils.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.LiteralCommandNode;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import wintahh.rageutils.RageUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

import wintahh.rageutils.module.Module;
import wintahh.rageutils.module.ModuleRegistry;

import com.mojang.brigadier.arguments.StringArgumentType;

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
                        for (Module m : ModuleRegistry.getAll()) {
                            mc.player.sendMessage(
                                Text.literal("§c[RageUtils] §r" + m.getName() + " §7: §e" + m.getCommand()),
                                false
                            );
                        }
                    }
                    return 1;
                })
                // clientsideblast
                .then(ClientCommandManager.literal("csb")
                    .executes(ctx -> {
                        RageUtils.CLIENTSIDE_BLAST.toggle();
                        return 1;
                    })
                    .then(ClientCommandManager.literal("sound")
                        .executes(ctx -> {
                            RageUtils.CLIENTSIDE_BLAST.toggleSound();
                            return 1;
                        })))
                // ratehud
                .then(ClientCommandManager.literal("rh")
                    .then(ClientCommandManager.literal("mine")
                        .executes(ctx -> {
                            RageUtils.RATE_HUD.toggleMining();
                            return 1;
                        }))
                    .then(ClientCommandManager.literal("reset")
                        .executes(ctx -> {
                            RageUtils.RATE_HUD.resetCounters();
                            return 1;
                        })))
                // ghostblockfix
                .then(ClientCommandManager.literal("gbf")
                    .executes(ctx -> {
                        sendBetaNotice("GhostBlockFix");
                        RageUtils.GHOST_BLOCK_FIX.toggle();
                        return 1;
                    })
                    .then(ClientCommandManager.literal("proactive")
                        .executes(ctx -> {
                            sendBetaNotice("GhostBlockFix proactive");
                            RageUtils.GHOST_BLOCK_FIX.toggleProactive();
                            return 1;
                        })))
                // snappyinput
                .then(ClientCommandManager.literal("snap")
                    .executes(ctx -> {
                        sendBetaNotice("SnappyInput");
                        RageUtils.SNAPPY_INPUT.toggle();
                        return 1;
                    }))
        );
    }
}

private static void sendBetaNotice(String featureName) {
    MinecraftClient mc = MinecraftClient.getInstance();
    if (mc.player != null) {
        mc.player.sendMessage(
            Text.literal("§c[RageUtils] §e" + featureName + " is a beta feature."),
            false
        );
    }
}
}
