package wintahh.rageutils.module;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;

public class SnappyInput extends Module {
    private boolean attackPressedLastTick = false;

    public SnappyInput() {
        super("SnappyInput", "/ru snap");
    }

    @Override
    public void toggle() {
        setEnabled(!isEnabled());
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(
                Text.literal("[RageUtils] SnappyInput beta feature: " + (isEnabled() ? "\u00a7aON" : "\u00a7cOFF")),
                true
            );
        }
    }

    public void onInputTick(MinecraftClient mc) {
        if (!isEnabled()) {
            attackPressedLastTick = false;
            return;
        }
        if (mc.player == null || mc.world == null) {
            attackPressedLastTick = false;
            return;
        }

        boolean attackPressed = mc.options.attackKey.isPressed();
        if (attackPressed && !attackPressedLastTick && !mc.player.handSwinging) {
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        attackPressedLastTick = attackPressed;
    }

    public void onBlockBreakPredicted(BlockPos pos, BlockState oldState) {
        if (!isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || oldState == null || oldState.isAir()) return;

        mc.world.addBlockBreakParticles(pos, oldState);
        BlockSoundGroup soundGroup = oldState.getSoundGroup();
        mc.world.playSoundClient(
            pos.getX() + 0.5,
            pos.getY() + 0.5,
            pos.getZ() + 0.5,
            soundGroup.getBreakSound(),
            SoundCategory.BLOCKS,
            (soundGroup.getVolume() + 1.0F) / 2.0F,
            soundGroup.getPitch() * 0.8F,
            false
        );
    }

    public void onBlockPlacePredicted(BlockPos pos, ItemStack stack) {
        if (!isEnabled()) return;
    }
}
