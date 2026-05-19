package wintahh.rageutils.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wintahh.rageutils.RageUtils;

@Mixin(ClientPlayerEntity.class)
public class SnappyInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void rageutils$onTick(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        RageUtils.SNAPPY_INPUT.onInputTick(mc);
    }
}
