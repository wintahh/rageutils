package wintahh.rageutils.module;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import net.minecraft.block.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.block.Blocks;
import net.minecraft.item.Items;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;

import java.util.ArrayList;
import java.util.List;

public class ClientSideBlast extends Module {
    private boolean soundEnabled = false;

    public ClientSideBlast() {
        super("ClientSideBlast", "/ru csb");
    }

    public void toggleSound() {
        soundEnabled = !soundEnabled;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(
                Text.literal("[RageUtils] ClientSideBlast sound: " + (soundEnabled ? "\u00a7aON" : "\u00a7cOFF")),
                true
            );
        }
    }

    public boolean canBlastTarget(BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return false;

        BlockState state = mc.world.getBlockState(pos);
        if (state.isAir()) return false;

        ItemStack held = mc.player.getMainHandStack();

        if (!held.isIn(ItemTags.PICKAXES) &&
            !held.isIn(ItemTags.AXES) &&
            !held.isIn(ItemTags.SHOVELS) &&
            !held.isIn(ItemTags.HOES) &&
            held.getItem() != Items.SHEARS) {
            return false;
        }

        LoreComponent lore = held.get(DataComponentTypes.LORE);
        if (lore == null) return false;
        boolean hasBlastEnchant = lore.lines().stream()
            .anyMatch(line -> line.getString().equals("Blast I"));
        if (!hasBlastEnchant) return false;

        if (held.getItem() == Items.SHEARS) { 
            if (state.isIn(BlockTags.WOOL) ||
                state.isIn(BlockTags.LEAVES)) return true;
        }
        
        if (state.isOf(Blocks.MUSHROOM_STEM)) {
            if (held.isIn(ItemTags.HOES)) return true;
            if (held.isIn(ItemTags.AXES)) return false; 
        }

        return held.isSuitableFor(state);
    }

    // Now, shouldBlast uses the toggle state AND the condition
    public boolean shouldBlast(BlockPos pos) {
        if (!isEnabled()) return false;
        return canBlastTarget(pos);
    }

    public List<PredictedBreak> planBreaks(BlockPos pos, Direction face) {
        List<PredictedBreak> breaks = new ArrayList<>();
        if (!shouldBlast(pos)) return breaks;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return breaks;

        Direction.Axis faceAxis = face.getAxis();

        Direction.Axis axisA = null;
        Direction.Axis axisB = null;
        for (Direction.Axis axis : Direction.Axis.VALUES) {
            if (axis == faceAxis) continue;
            if (axisA == null) axisA = axis;
            else axisB = axis;
        }

        // Iterate to form the 3x3 blast
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                BlockPos target = offset(pos, axisA, i, axisB, j);
                if (!canBlastTarget(target)) continue;
                breaks.add(new PredictedBreak(target.toImmutable(), mc.world.getBlockState(target)));
            }
        }

        return breaks;
    }

    public void applyPredictedBreaks(List<PredictedBreak> breaks) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        boolean playedSound = false;
        for (PredictedBreak predictedBreak : breaks) {
            mc.world.setBlockState(predictedBreak.pos(), Blocks.AIR.getDefaultState(), 19);
            if (soundEnabled && !playedSound) {
                playBreakSound(mc, predictedBreak);
                playedSound = true;
            }
        }
    }

    private void playBreakSound(MinecraftClient mc, PredictedBreak predictedBreak) {
        BlockSoundGroup soundGroup = predictedBreak.oldState().getSoundGroup();
        BlockPos pos = predictedBreak.pos();
        SoundCompat.playClientSound(
            mc,
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

    private BlockPos offset(BlockPos origin, Direction.Axis axisA, int a, Direction.Axis axisB, int b) {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();

        if (axisA == Direction.Axis.X) x += a; else if (axisA == Direction.Axis.Y) y += a; else z += a;
        if (axisB == Direction.Axis.X) x += b; else if (axisB == Direction.Axis.Y) y += b; else z += b;

        return new BlockPos(x, y, z);
    }

    public record PredictedBreak(BlockPos pos, BlockState oldState) {
    }
}
