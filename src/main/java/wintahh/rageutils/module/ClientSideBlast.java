package wintahh.rageutils.module;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
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

public class ClientSideBlast extends Module {
    public ClientSideBlast() {
        super("ClientSideBlast", "/ru csb");
    }

    public boolean shouldBlast(BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return false;

        ItemStack held = mc.player.getMainHandStack();

        // if held isnt a tool that can have blast
        if (!held.isIn(ItemTags.PICKAXES) &&
            !held.isIn(ItemTags.AXES) &&
            !held.isIn(ItemTags.SHOVELS) &&
            !held.isIn(ItemTags.HOES) &&
            held.getItem() != Items.SHEARS) {
            return false;
        }

        // check for blast enchant in the lore
        LoreComponent lore = held.get(DataComponentTypes.LORE);
        if (lore == null) return false;
        boolean hasBlastEnchant = lore.lines().stream()
            .anyMatch(line -> line.getString().equals("Blast I"));
        if (!hasBlastEnchant) return false;

        BlockState state = mc.world.getBlockState(pos);

        // for some reason shears dont work with isSuitable so we're calling them early 
        if (held.getItem() == Items.SHEARS) { 
            if (state.isIn(BlockTags.WOOL) ||
                state.isIn(BlockTags.LEAVES)) return true;
        }
        // mushroom technically isnt suitable for hoes, call it early
        if (state.isOf(Blocks.MUSHROOM_STEM)) {
            if (held.isIn(ItemTags.HOES)) return true;
            if (held.isIn(ItemTags.AXES)) return false; // axe is suitable for mushroom stem, but not in ragemines, call it early
        }

        // unsuitable, others
        if (!held.isSuitableFor(state)) return false;

        return true;
    }

    public boolean canBlastTarget(BlockPos target, BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null) return false;

        BlockState targetState = mc.world.getBlockState(target);
        BlockState posState = mc.world.getBlockState(pos);

        return targetState.isOf(posState.getBlock()); // you only blast the same block as you mine, this also skips air
    }

    public void onBreakBlock(BlockPos pos, Direction face) {
        if (!isEnabled()) return;
        if (!shouldBlast(pos)) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        // get axes perpendicular to hit face
        Direction.Axis faceAxis = face.getAxis();

        Direction.Axis axisA = null;
        Direction.Axis axisB = null;
        for (Direction.Axis axis : Direction.Axis.VALUES) {
            if (axis == faceAxis) continue;
            if (axisA == null) axisA = axis;
            else axisB = axis;
        }

        // iterate to form the 3x3 of blast
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j++) {
                if (i == 0 && j == 0) continue; // dont set air the block broken by player
                BlockPos target = offset(pos, axisA, i, axisB, j);
                if (!canBlastTarget(target, pos)) continue;
                mc.world.setBlockState(target, Blocks.AIR.getDefaultState());
            }
        }
    }

    private BlockPos offset(BlockPos origin, Direction.Axis axisA, int a, Direction.Axis axisB, int b) {
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();

        if (axisA == Direction.Axis.X) x += a; else if (axisA == Direction.Axis.Y) y += a; else z += a;
        if (axisB == Direction.Axis.X) x += b; else if (axisB == Direction.Axis.Y) y += b; else z += b;

        return new BlockPos(x, y, z);
    }
}
