package wintahh.rageutils.module;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class GhostBlockFix extends Module {
    private static final long MEMORY_TTL_MS = 10_000;
    private static final long VERIFY_COOLDOWN_MS = 1_500;
    private static final int SEARCH_RADIUS = 2;
    private static final int MAX_REPAIRS_PER_TICK = 2;
    private static final long PROACTIVE_DELAY_MS = 300;
    private static final int MAX_PROACTIVE_PER_TICK = 4;
    private static final int MAX_RECENTLY_MINED = 128;

    private final Map<BlockPos, MinedBlock> recentlyMined = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, MinedBlock> eldest) {
            return size() > MAX_RECENTLY_MINED;
        }
    };
    private final Map<BlockPos, Long> lastRepairedAt = new LinkedHashMap<>();
    private final Map<BlockPos, Long> pendingProactiveVerify = new LinkedHashMap<>();
    private boolean proactiveEnabled = false;

    public GhostBlockFix() {
        super("GhostBlockFix", "/ru gbf");
    }

    @Override
    public void toggle() {
        setEnabled(!isEnabled());
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(
                Text.literal("[RageUtils] GhostBlockFix beta feature: " + (isEnabled() ? "\u00a7aON" : "\u00a7cOFF")),
                true
            );
        }
        if (!isEnabled()) {
            recentlyMined.clear();
            lastRepairedAt.clear();
            pendingProactiveVerify.clear();
        }
    }

    public void onBlockBroken(BlockPos pos) {
        onBlockBroken(pos, null);
    }

    public void onBlockBroken(BlockPos pos, BlockState oldState) {
        if (!isEnabled()) return;

        long now = System.currentTimeMillis();
        pruneExpired(now);

        BlockPos immutablePos = pos.toImmutable();
        recentlyMined.put(immutablePos, new MinedBlock(now, oldState));
        if (proactiveEnabled) {
            pendingProactiveVerify.put(immutablePos, now + PROACTIVE_DELAY_MS);
        }
    }

    public void onClientTick(MinecraftClient mc) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        long now = System.currentTimeMillis();
        pruneExpired(now);

        if (mc.player.horizontalCollision || mc.player.verticalCollision) {
            repairNearbySolidGhosts(mc, mc.player.getBlockPos());
        }

        drainProactiveEntries(mc, now);
    }

    public void onRubberband(BlockPos before, Vec3d after) {
        if (!isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (!isEnabled()) return;
            if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

            repairNearbyInvisibleGhosts(mc, before);
            repairNearbyInvisibleGhosts(mc, BlockPos.ofFloored(after));
        });
    }

    public void toggleProactive() {
        proactiveEnabled = !proactiveEnabled;
        if (!proactiveEnabled) {
            pendingProactiveVerify.clear();
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(
                Text.literal("[RageUtils] GhostBlockFix proactive beta feature: " + (proactiveEnabled ? "\u00a7aON" : "\u00a7cOFF")),
                true
            );
        }
    }

    private void repairNearbyInvisibleGhosts(MinecraftClient mc, BlockPos center) {
        int repairs = 0;
        for (Map.Entry<BlockPos, MinedBlock> entry : recentlyMined.entrySet()) {
            BlockPos pos = entry.getKey();
            if (chebyshevDistance(pos, center) > SEARCH_RADIUS) continue;

            if (repairInvisibleGhost(mc, pos, entry.getValue())) {
                repairs++;
                if (repairs >= MAX_REPAIRS_PER_TICK) return;
            }
        }
    }

    private void repairNearbySolidGhosts(MinecraftClient mc, BlockPos center) {
        int repairs = 0;
        for (Map.Entry<BlockPos, MinedBlock> entry : recentlyMined.entrySet()) {
            BlockPos pos = entry.getKey();
            if (chebyshevDistance(pos, center) > SEARCH_RADIUS) continue;

            if (repairSolidGhost(mc, pos, entry.getValue())) {
                repairs++;
                if (repairs >= MAX_REPAIRS_PER_TICK) return;
            }
        }
    }

    private boolean repairInvisibleGhost(MinecraftClient mc, BlockPos pos, MinedBlock minedBlock) {
        if (minedBlock.oldState == null || minedBlock.oldState.isAir()) return false;
        if (!mc.world.getBlockState(pos).isAir()) return false;
        if (!canRepair(pos)) return false;

        mc.world.setBlockState(pos, minedBlock.oldState, 19);
        lastRepairedAt.put(pos.toImmutable(), System.currentTimeMillis());
        return true;
    }

    private boolean repairSolidGhost(MinecraftClient mc, BlockPos pos, MinedBlock minedBlock) {
        if (minedBlock.oldState == null || minedBlock.oldState.isAir()) return false;
        BlockState currentState = mc.world.getBlockState(pos);
        if (currentState.isAir() || currentState != minedBlock.oldState) return false;
        if (!mc.player.collidesWithStateAtPos(pos, currentState)) return false;
        if (!canRepair(pos)) return false;

        mc.world.setBlockState(pos, Blocks.AIR.getDefaultState(), 19);
        lastRepairedAt.put(pos.toImmutable(), System.currentTimeMillis());
        return true;
    }

    private boolean canRepair(BlockPos pos) {
        long now = System.currentTimeMillis();
        Long lastRepaired = lastRepairedAt.get(pos);
        return lastRepaired == null || now - lastRepaired >= VERIFY_COOLDOWN_MS;
    }

    private void drainProactiveEntries(MinecraftClient mc, long now) {
        if (!proactiveEnabled) return;

        int drained = 0;
        Iterator<Map.Entry<BlockPos, Long>> iterator = pendingProactiveVerify.entrySet().iterator();
        while (iterator.hasNext() && drained < MAX_PROACTIVE_PER_TICK) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            if (entry.getValue() > now) continue;

            MinedBlock minedBlock = recentlyMined.get(entry.getKey());
            if (minedBlock != null) {
                repairSolidGhost(mc, entry.getKey(), minedBlock);
            }
            iterator.remove();
            drained++;
        }
    }

    private void pruneExpired(long now) {
        recentlyMined.entrySet().removeIf(entry -> now - entry.getValue().minedAt > MEMORY_TTL_MS);
        pendingProactiveVerify.keySet().removeIf(pos -> !recentlyMined.containsKey(pos));
        lastRepairedAt.entrySet().removeIf(entry -> now - entry.getValue() > MEMORY_TTL_MS);
    }

    private int chebyshevDistance(BlockPos a, BlockPos b) {
        return Math.max(
            Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())),
            Math.abs(a.getZ() - b.getZ())
        );
    }

    private record MinedBlock(long minedAt, BlockState oldState) {
    }
}
