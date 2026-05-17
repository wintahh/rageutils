package wintahh.rageutils.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import wintahh.rageutils.RageUtils;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayerInteractionManagerMixin {
    // at = @At("HEAD")) on le capte avant qu'il soit envoyer au serv.
    @Inject(method = "breakBlock", at = @At("HEAD"))
    private void onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        HitResult hit = MinecraftClient.getInstance().crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        Direction face = ((BlockHitResult) hit).getSide();
        RageUtils.RATE_HUD.onBlockBroken(pos, face); // ordering is important. rate_hud needs to be before clientside_blast
        RageUtils.CLIENTSIDE_BLAST.onBreakBlock(pos, face);
    }
}