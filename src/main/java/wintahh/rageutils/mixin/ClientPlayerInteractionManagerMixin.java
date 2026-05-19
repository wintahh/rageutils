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
    @Unique
    private BlockPos rageutils$attackedPos;
    @Unique
    private Direction rageutils$attackedFace;

    // Capture the face at mining-start so the blast plane survives crosshair drift.
    @Inject(method = "attackBlock", at = @At("HEAD"))
    private void rageutils$onAttackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        rageutils$attackedPos = pos.toImmutable();
        rageutils$attackedFace = direction;
    }

    // at = @At("HEAD")) on le capte avant qu'il soit envoyer au serv.
    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        rageutils$oldBreakState = null;
        rageutils$plannedBlastBreaks = Collections.emptyList();

        Direction face = null;
        if (rageutils$attackedPos != null && rageutils$attackedPos.equals(pos) && rageutils$attackedFace != null) {
            face = rageutils$attackedFace;
        } else {
            HitResult hit = mc.crosshairTarget;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                face = ((BlockHitResult) hit).getSide();
            }
        }
        if (face == null) return;

        BlockState oldState = mc.world == null ? null : mc.world.getBlockState(pos);
        rageutils$oldBreakState = oldState;
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
        rageutils$attackedPos = null;
        rageutils$attackedFace = null;
    }
}
