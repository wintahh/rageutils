package wintahh.rageutils.module;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GhostBlockFix extends Module {
    private static final long MEMORY_TTL_MS = 15_000;
    private static final long VERIFY_COOLDOWN_MS = 700;
    private static final int SEARCH_RADIUS = 2;
    private static final int MAX_VERIFICATIONS_PER_TICK = 6;
    private static final long MIN_PROACTIVE_DELAY_MS = 650;
    private static final long MAX_PROACTIVE_DELAY_MS = 2_000;
    private static final long PROACTIVE_RETRY_DELAY_MS = 900;
    private static final int MAX_PROACTIVE_ATTEMPTS = 3;
    private static final int MAX_RECENTLY_MINED = 512;

    private final Map<BlockPos, MinedBlock> recentlyMined = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<BlockPos, MinedBlock> eldest) {
            return size() > MAX_RECENTLY_MINED;
        }
    };
    private final Map<BlockPos, Long> lastVerifiedAt = new LinkedHashMap<>();
    private final Map<BlockPos, ProactiveVerification> pendingProactiveVerify = new LinkedHashMap<>();
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
            lastVerifiedAt.clear();
            pendingProactiveVerify.clear();
        }
    }

    public void onBlockBroken(BlockPos pos) {
        onBlockBroken(pos, null);
    }

    public void onBlockBroken(BlockPos pos, BlockState oldState) {
        if (!isEnabled()) return;
        if (oldState != null && oldState.isAir()) return;

        long now = System.currentTimeMillis();
        pruneExpired(now);
        trackMinedBlock(pos, now);
    }

    public void onBlocksBroken(List<ClientSideBlast.PredictedBreak> breaks) {
        if (!isEnabled()) return;

        long now = System.currentTimeMillis();
        pruneExpired(now);
        for (ClientSideBlast.PredictedBreak predictedBreak : breaks) {
            if (predictedBreak.oldState() == null || predictedBreak.oldState().isAir()) continue;
            trackMinedBlock(predictedBreak.pos(), now);
        }
    }

    public void onClientTick(MinecraftClient mc) {
        if (!isEnabled()) return;
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

        long now = System.currentTimeMillis();
        pruneExpired(now);

        if (mc.player.horizontalCollision || mc.player.verticalCollision) {
            verifyNearby(mc, mc.player.getBlockPos());
        }

        drainProactiveEntries(mc, now);
    }

    public void onRubberband(BlockPos before, Vec3d after) {
        if (!isEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> {
            if (!isEnabled()) return;
            if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return;

            verifyNearby(mc, before);
            verifyNearby(mc, BlockPos.ofFloored(after));
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

    private void trackMinedBlock(BlockPos pos, long now) {
        BlockPos immutablePos = pos.toImmutable();
        recentlyMined.put(immutablePos, new MinedBlock(now));
        if (proactiveEnabled) {
            pendingProactiveVerify.put(immutablePos, new ProactiveVerification(now + getProactiveDelayMs(), 0));
        }
    }

    private void verifyNearby(MinecraftClient mc, BlockPos center) {
        int verified = 0;
        for (BlockPos pos : recentlyMined.keySet()) {
            if (chebyshevDistance(pos, center) > SEARCH_RADIUS) continue;

            if (sendVerificationPacket(mc, pos)) {
                verified++;
                if (verified >= MAX_VERIFICATIONS_PER_TICK) return;
            }
        }
    }

    private boolean sendVerificationPacket(MinecraftClient mc, BlockPos pos) {
        ClientPlayNetworkHandler net = mc.getNetworkHandler();
        if (net == null) return false;

        long now = System.currentTimeMillis();
        Long lastVerified = lastVerifiedAt.get(pos);
        if (lastVerified != null && now - lastVerified < VERIFY_COOLDOWN_MS) return false;

        Direction face = Direction.UP;
        net.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, face
        ));
        net.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, face
        ));
        lastVerifiedAt.put(pos.toImmutable(), now);
        return true;
    }

    private void drainProactiveEntries(MinecraftClient mc, long now) {
        if (!proactiveEnabled) return;

        int drained = 0;
        Iterator<Map.Entry<BlockPos, ProactiveVerification>> iterator = pendingProactiveVerify.entrySet().iterator();
        while (iterator.hasNext() && drained < MAX_VERIFICATIONS_PER_TICK) {
            Map.Entry<BlockPos, ProactiveVerification> entry = iterator.next();
            ProactiveVerification verification = entry.getValue();
            if (verification.nextAt > now) continue;

            sendVerificationPacket(mc, entry.getKey());
            int nextAttempt = verification.attempt + 1;
            if (nextAttempt >= MAX_PROACTIVE_ATTEMPTS || !recentlyMined.containsKey(entry.getKey())) {
                iterator.remove();
            } else {
                entry.setValue(new ProactiveVerification(now + PROACTIVE_RETRY_DELAY_MS, nextAttempt));
            }
            drained++;
        }
    }

    private long getProactiveDelayMs() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.getNetworkHandler() == null) return MIN_PROACTIVE_DELAY_MS;

        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        int latency = entry == null ? 0 : Math.max(0, entry.getLatency());
        long delay = latency * 2L + 350L;
        return Math.max(MIN_PROACTIVE_DELAY_MS, Math.min(MAX_PROACTIVE_DELAY_MS, delay));
    }

    private void pruneExpired(long now) {
        recentlyMined.entrySet().removeIf(entry -> now - entry.getValue().minedAt > MEMORY_TTL_MS);
        pendingProactiveVerify.keySet().removeIf(pos -> !recentlyMined.containsKey(pos));
        lastVerifiedAt.entrySet().removeIf(entry -> now - entry.getValue() > MEMORY_TTL_MS);
    }

    private int chebyshevDistance(BlockPos a, BlockPos b) {
        return Math.max(
            Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getY() - b.getY())),
            Math.abs(a.getZ() - b.getZ())
        );
    }

    private record MinedBlock(long minedAt) {
    }

    private record ProactiveVerification(long nextAt, int attempt) {
    }
}
