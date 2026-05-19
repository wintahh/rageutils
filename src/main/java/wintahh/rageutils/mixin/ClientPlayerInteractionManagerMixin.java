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

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    @Unique
    private BlockState rageutils$oldBreakState;

    // at = @At("HEAD")) on le capte avant qu'il soit envoyer au serv.
    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        HitResult hit = mc.crosshairTarget;
        rageutils$oldBreakState = null;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockState oldState = mc.world == null ? null : mc.world.getBlockState(pos);
        rageutils$oldBreakState = oldState;
        Direction face = ((BlockHitResult) hit).getSide();
        RageUtils.RATE_HUD.onBlockBroken(pos, face); // ordering is important. rate_hud needs to be before clientside_blast
        RageUtils.CLIENTSIDE_BLAST.onBreakBlock(pos, face);
    }

    @Inject(method = "breakBlock", at = @At("RETURN"))
    private void rageutils$onBreakBlockReturn(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            rageutils$oldBreakState = null;
            return;
        }

        RageUtils.GHOST_BLOCK_FIX.onBlockBroken(pos, rageutils$oldBreakState);
        RageUtils.SNAPPY_INPUT.onBlockBreakPredicted(pos, rageutils$oldBreakState);
        rageutils$oldBreakState = null;
    }
}
