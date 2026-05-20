package wintahh.rageutils.module;

import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class SoundCompat {
    private SoundCompat() {
    }

    static void playClientSound(
        MinecraftClient mc,
        double x,
        double y,
        double z,
        SoundEvent sound,
        SoundCategory category,
        float volume,
        float pitch,
        boolean useDistance
    ) {
        if (mc.world == null) return;

        try {
            invokePlaySound(mc.world, x, y, z, sound, category, volume, pitch, useDistance);
        } catch (ReflectiveOperationException ignored) {
            // Sound playback changed names across 1.21.x; if none match, skip only the prediction sound.
        }
    }

    private static void invokePlaySound(
        Object world,
        double x,
        double y,
        double z,
        SoundEvent sound,
        SoundCategory category,
        float volume,
        float pitch,
        boolean useDistance
    ) throws ReflectiveOperationException {
        ReflectiveOperationException failure = null;
        for (String methodName : new String[] {"playSoundClient", "playSound", "method_8486", "a"}) {
            try {
                Method method = world.getClass().getMethod(
                    methodName,
                    double.class,
                    double.class,
                    double.class,
                    SoundEvent.class,
                    SoundCategory.class,
                    float.class,
                    float.class,
                    boolean.class
                );
                method.invoke(world, x, y, z, sound, category, volume, pitch, useDistance);
                return;
            } catch (NoSuchMethodException | IllegalAccessException e) {
                failure = e;
            } catch (InvocationTargetException e) {
                throw new ReflectiveOperationException(e.getCause());
            }
        }
        throw failure == null ? new NoSuchMethodException() : failure;
    }
}
