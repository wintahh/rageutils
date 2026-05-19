package wintahh.rageutils.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.block.BlockState;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wintahh.rageutils.RageUtils;
import wintahh.rageutils.module.ClientSideBlast;

import java.util.Collections;
import java.util.List;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Unique
    private BlockState rageutils$oldBreakState;
    @Unique
    private List<ClientSideBlast.PredictedBreak> rageutils$plannedBlastBreaks = Collections.emptyList();

    // at = @At("HEAD")) on le capte avant qu'il soit envoyer au serv.
    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HitResult hit = mc.crosshairTarget;
        rageutils$oldBreakState = null;
        rageutils$plannedBlastBreaks = Collections.emptyList();
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockState oldState = mc.world == null ? null : mc.world.getBlockState(pos);
        rageutils$oldBreakState = oldState;
        Direction face = ((BlockHitResult) hit).getSide();
        rageutils$plannedBlastBreaks = RageUtils.CLIENTSIDE_BLAST.planBreaks(pos, face);
    }

    @Inject(method = "breakBlock", at = @At("RETURN"))
    private void rageutils$onBreakBlockReturn(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            rageutils$clearBreakPrediction();
            return;
        }

        RageUtils.RATE_HUD.onBlocksBroken(1 + rageutils$plannedBlastBreaks.size());
        RageUtils.CLIENTSIDE_BLAST.applyPredictedBreaks(rageutils$plannedBlastBreaks);
        RageUtils.GHOST_BLOCK_FIX.onBlockBroken(pos, rageutils$oldBreakState);
        RageUtils.GHOST_BLOCK_FIX.onBlocksBroken(rageutils$plannedBlastBreaks);
        RageUtils.SNAPPY_INPUT.onBlockBreakPredicted(pos, rageutils$oldBreakState);
        rageutils$clearBreakPrediction();
    }

    @Unique
    private void rageutils$clearBreakPrediction() {
        rageutils$oldBreakState = null;
        rageutils$plannedBlastBreaks = Collections.emptyList();
    }
}
