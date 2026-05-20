package wintahh.rageutils.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import wintahh.rageutils.RageUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onPlayerPositionLook", at = @At("HEAD"))
    private void rageutils$onRubberband(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        BlockPos before = mc.player.getBlockPos();
        Vec3d after = rageutils$resolveAfterPosition(
            packet,
            new Vec3d(mc.player.getX(), mc.player.getY(), mc.player.getZ())
        );
        RageUtils.GHOST_BLOCK_FIX.onRubberband(before, after);
    }

    private static Vec3d rageutils$resolveAfterPosition(PlayerPositionLookS2CPacket packet, Vec3d current) {
        Vec3d modernPosition = rageutils$resolveModernPosition(packet, current);
        if (modernPosition != null) {
            return modernPosition;
        }

        Vec3d legacyPosition = rageutils$resolveLegacyPosition(packet, current);
        return legacyPosition == null ? current : legacyPosition;
    }

    @SuppressWarnings("unchecked")
    private static Vec3d rageutils$resolveModernPosition(PlayerPositionLookS2CPacket packet, Vec3d current) {
        try {
            Object change = rageutils$invoke(packet, "change", "comp_3228", "e");
            Vec3d changedPosition = (Vec3d) rageutils$invoke(change, "position", "comp_3148", "a");
            Set<PositionFlag> flags = (Set<PositionFlag>) rageutils$invoke(packet, "relatives", "comp_3229", "f");
            return rageutils$applyRelativePosition(current, changedPosition, flags);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Vec3d rageutils$resolveLegacyPosition(PlayerPositionLookS2CPacket packet, Vec3d current) {
        try {
            double x = ((Number) rageutils$invoke(packet, "getX", "method_11734", "b")).doubleValue();
            double y = ((Number) rageutils$invoke(packet, "getY", "method_11735", "e")).doubleValue();
            double z = ((Number) rageutils$invoke(packet, "getZ", "method_11738", "f")).doubleValue();
            Set<PositionFlag> flags = (Set<PositionFlag>) rageutils$invoke(packet, "getFlags", "method_11733", "j");
            return rageutils$applyRelativePosition(current, new Vec3d(x, y, z), flags);
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            return null;
        }
    }

    private static Vec3d rageutils$applyRelativePosition(Vec3d current, Vec3d change, Set<PositionFlag> flags) {
        return new Vec3d(
            flags.contains(PositionFlag.X) ? current.x + change.x : change.x,
            flags.contains(PositionFlag.Y) ? current.y + change.y : change.y,
            flags.contains(PositionFlag.Z) ? current.z + change.z : change.z
        );
    }

    private static Object rageutils$invoke(Object target, String... names)
        throws ReflectiveOperationException {
        ReflectiveOperationException failure = null;
        for (String name : names) {
            try {
                Method method = target.getClass().getMethod(name);
                return method.invoke(target);
            } catch (NoSuchMethodException e) {
                failure = e;
            } catch (IllegalAccessException e) {
                failure = e;
            } catch (InvocationTargetException e) {
                throw new ReflectiveOperationException(e.getCause());
            }
        }
        throw failure == null ? new NoSuchMethodException() : failure;
    }
}
