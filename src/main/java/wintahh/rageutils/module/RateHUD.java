package wintahh.rageutils.module;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import wintahh.rageutils.RageUtils;

import java.util.*;

public class RateHUD extends Module {

    public static class ResourceType {
        public final String name;
        public final int color;
        public final long compressedValue;
        public final long superCompressedValue; // 0 = unavailable
        public final long ultraCompressedValue; // 0 = unavailable
        public long totalUnits = 0;

        public ResourceType(String name, int color, long compressed, long superCompressed, long ultraCompressed) {
            this.name = name;
            this.color = color;
            this.compressedValue = compressed;
            this.superCompressedValue = superCompressed;
            this.ultraCompressedValue = ultraCompressed;
        }

        // Returns how many base units the given item name + count represents
        public long getValueForItemName(String displayName, int count) {
            // Check most compressed first to avoid substring false-matches
            if (ultraCompressedValue > 0 && displayName.equals("Ultra Compressed " + name))
                return ultraCompressedValue * count;
            if (superCompressedValue > 0 && displayName.equals("Super Compressed " + name))
                return superCompressedValue * count;
            if (displayName.equals("Compressed " + name))
                return compressedValue * count;
            if (displayName.equals(name))
                return count;
            return 0;
        }

        public void reset() { totalUnits = 0; }

        public Text getDisplayName() {
            return Text.literal(name).styled(s -> s.withBold(true).withColor(color));
        }

        public String getMaxCompressionName() {
            if (ultraCompressedValue > 0) return "UC";
            if (superCompressedValue > 0) return "SC";
            return "Comp.";
        }

        public long getMaxCompressionValue() {
            if (ultraCompressedValue > 0) return ultraCompressedValue;
            if (superCompressedValue > 0) return superCompressedValue;
            return compressedValue;
        }
    }

    private boolean miningEnabled = false;
    private final Map<String, Long> previousBaseUnits = new HashMap<>();
    private final List<ResourceType> resources = new ArrayList<>();

    private long totalBlocksBroken = 0;
    private long startTime = 0;
    private long lastBreakTime = 0;

    private long pausedDuration = 0;
    private long pauseStart = 0;

    public RateHUD() {
        super("RateHUD", "/ru rh");
        initResources();
    }

    public void onBlockBroken(BlockPos pos, Direction face) {
        if (!miningEnabled) return;
        if (startTime == 0) startTime = System.currentTimeMillis();
        lastBreakTime = System.currentTimeMillis();

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;

        // always count the directly broken block
        totalBlocksBroken++;

        // if blast would trigger
        if (face != null && RageUtils.CLIENTSIDE_BLAST.shouldBlast(pos)) {
            Direction.Axis faceAxis = face.getAxis();
            Direction.Axis axisA = null;
            Direction.Axis axisB = null;
            for (Direction.Axis axis : Direction.Axis.VALUES) {
                if (axis == faceAxis) continue;
                if (axisA == null) axisA = axis;
                else axisB = axis;
            }
            // iterate over surrounding blocks to see which ones arent air
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    if (i == 0 && j == 0) continue; // center already counted above
                    BlockPos target = offsetPos(pos, axisA, i, axisB, j);
                    if (!mc.world.getBlockState(target).isAir()) {
                        totalBlocksBroken++;
                    }
                }
            }
        }
    }

    private BlockPos offsetPos(BlockPos origin, Direction.Axis axisA, int a, Direction.Axis axisB, int b) {
        int x = origin.getX(), y = origin.getY(), z = origin.getZ();
        if (axisA == Direction.Axis.X) x += a; else if (axisA == Direction.Axis.Y) y += a; else z += a;
        if (axisB == Direction.Axis.X) x += b; else if (axisB == Direction.Axis.Y) y += b; else z += b;
        return new BlockPos(x, y, z);
    }

    private void initResources() {
        // --- Group 1: compressed=64, super=4096 ---
        r("Oak Wood",       0x9e512c, 64,  0,     0);
        r("Loam Soil",      0x4b3d38, 64,  0,     0);
        r("Peat",           0x5f3a1f, 64,  0,     0);
        r("Dirt",           0x8d6d49, 96,  0,     0);
        r("Ryegrass",       0xc57d59, 64,  0,     0);
        r("Lemongrass",     0xa1b45b, 64,  0,     0);
        r("Oak Leaves",     0x6b8835, 64,  0,     0);
        r("Silvergrass",    0x4b794b, 64,  4096,  0);
        r("Stone",          0x7e7e7e, 64,  4096,  0);
        r("Moss",           0x69992f, 64,  4096,  0);
        r("Coal",           0x443f3f, 64,  4096,  0);
        r("Iron",           0xd5d5d5, 64,  4096,  0);
        r("Dripstone",      0x966b3e, 64,  4096,  0);
        r("Gold",           0xf6e94d, 64,  4096,  0);
        r("Emerald",        0x00d639, 64,  4096,  0);
        r("Diamond",        0x32e8fc, 64,  4096,  0);
        r("Amethyst",       0x985bc4, 64,  0,     0);
        r("Cherry Wood",    0x70424f, 64,  4096,  0);
        r("Rose Stone",     0xe87582, 64,  4096,  0);
        r("Cherry Leaves",  0xd98a99, 64,  4096,  0);
        r("Bloom Clay",     0x9e4d4d, 64,  4096,  0);
        r("Cherry Sand",    0xeda3a8, 64,  4096,  0);
        r("Spirit Clay",    0x9c5b75, 64,  4096,  0);
        r("Pink Petal",     0xfc949e, 64,  4096,  0);

        // --- Group 2: compressed=128, super=16384 ---
        r("Snow",             0xf4f4f4, 128, 16384, 0);
        r("Snowpack",         0xeaeaea, 128, 16384, 0);
        r("Ice Wool",         0x39add6, 128, 16384, 0);
        r("Icestone",         0x6ea8d3, 128, 16384, 0);
        r("Cryoblood",        0x495b89, 128, 16384, 0);
        r("Blue Ice",         0x73a5ca, 128, 16384, 0);
        r("Glacierstone",     0x2b2e8d, 128, 16384, 0);
        r("Frostgene",        0x2e6d8a, 128, 16384, 0);
        r("Crimson Stem",     0x891e1e, 128, 16384, 0);
        r("Magma",            0xfc6900, 128, 16384, 0);
        r("Firewart",         0x9f1212, 128, 16384, 0);
        r("Ashrock",          0x6d2a2a, 128, 16384, 0);
        r("Infernal Quartz",  0xe3c7b3, 128, 16384, 0);
        r("Ancient Debris",   0x39291f, 128, 16384, 0);
        r("Hellplate",        0x8f4747, 128, 16384, 0);
        r("Bloodplate",       0xbe382a, 128, 16384, 0);

        // --- Group 3: compressed=64, super=4096, ultra=262144 ---
        r("Mycelium",         0xe5ded3, 64,  4096,  0);
        r("Aether Wood",      0xe3d0a1, 64,  4096,  262144);
        r("Celestone",        0xf1eee9, 64,  4096,  262144);
        r("Luminium",         0xcdcdcf, 64,  4096,  262144);
        r("Aether Tile",      0xf6f6f6, 64,  4096,  262144);
        r("Aurum",            0xe3b64b, 64,  4096,  262144);
        r("Solar Fiber",      0xefd556, 64,  4096,  262144);
        r("Solaris",          0xd6a240, 64,  4096,  262144);
        r("Faded",            0x8c7c6d, 64,  4096,  262144);
        r("Dusk",             0x7b7b7b, 64,  4096,  262144);
        r("Void",             0x0d1c24, 64,  4096,  262144);
        r("Blackstone",       0x29232a, 64,  4096,  262144);
        r("Obsidian",         0x432a6d, 64,  4096,  262144);
        r("Crying Obsidian",  0x31076d, 64,  4096,  262144);
        r("Purpur",           0xa57aa5, 64,  4096,  262144);
        r("Dark Matter",      0x6c2f96, 64,  4096,  262144);
        r("Void Sensor",      0x0d1c24, 64,  4096,  262144);
        r("Tidewood",         0x0d7276, 64,  4096,  262144);
        r("Azure",            0x3c56fc, 64,  4096,  262144);
        r("Seastone",         0x62c5b0, 64,  4096,  262144);
        r("Deepstone",        0x274d4a, 64,  4096,  262144);
        r("Aqualith",         0x68cee4, 64,  4096,  262144);

        // --- Group 4: compressed=128, super=16384, ultra=2097152 ---
        r("Sandstone",  0xd6c68c, 128, 16384, 2097152);
        r("Straw",      0xc7a027, 128, 16384, 2097152);
        r("Amber",      0xdd6001, 128, 16384, 2097152);
        r("Rust",       0xa6491f, 128, 16384, 2097152);
        r("Relic",      0x6a1d0c, 128, 16384, 2097152);
        r("Flareon",    0xd6683b, 128, 16384, 2097152);
    }

    private void r(String name, int color, long c, long s, long u) {
        resources.add(new ResourceType(name, color, c, s, u));
    }

    public boolean isMiningEnabled() { return miningEnabled; }

    public void toggleMining() {
        miningEnabled = !miningEnabled;
        resetCounters();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(
                Text.literal("[RageUtils] RateHUD Mining: " + (miningEnabled ? "§aON" : "§cOFF")),
                true
            );
        }
    }

    public void resetCounters() {
        resources.forEach(ResourceType::reset);
        totalBlocksBroken = 0;
        startTime = 0;
        pausedDuration = 0;
        pauseStart = 0;
        lastBreakTime = System.currentTimeMillis();
        previousBaseUnits.clear();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            previousBaseUnits.putAll(getBaseUnitSnapshot(mc));
        }
    }

    // inventory tracking

    private Map<String, Long> getBaseUnitSnapshot(MinecraftClient mc) {
        Map<String, Long> snapshot = new HashMap<>();
        for (int i = 0; i < mc.player.getInventory().size(); i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isEmpty()) continue;
            String displayName = stack.getName().getString();
            for (ResourceType resource : resources) {
                long value = resource.getValueForItemName(displayName, stack.getCount());
                if (value > 0) {
                    snapshot.merge(resource.name, value, Long::sum);
                    break;
                }
            }
        }
        return snapshot;
    }

    private void processDelta(Map<String, Long> current) { // handle compression
        for (ResourceType resource : resources) {
            long currentUnits = current.getOrDefault(resource.name, 0L);
            long previousUnits = previousBaseUnits.getOrDefault(resource.name, 0L);
            long delta = currentUnits - previousUnits;
            if (delta > 0) resource.totalUnits += delta;
        }
    }

    private ResourceType getTopResource() {
        return resources.stream()
            .filter(r -> r.totalUnits > 0)
            .max(Comparator.comparingLong(r -> r.totalUnits))
            .orElse(null);
    }

    // main loop

    public void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!miningEnabled || client.player == null || client.world == null) return;

            Map<String, Long> current = getBaseUnitSnapshot(client);
            processDelta(current);
            previousBaseUnits.clear();
            previousBaseUnits.putAll(current);

            ResourceType top = getTopResource();
            if (top == null) return;
            if (startTime == 0) return;

            long now = System.currentTimeMillis();
            boolean isPaused = (now - lastBreakTime) > 3000;

            if (isPaused) {
                if (pauseStart == 0) pauseStart = lastBreakTime + 3000;
            } else {
                if (pauseStart > 0) {
                    pausedDuration += now - pauseStart;
                    pauseStart = 0;
                }
            }

            long activeDuration = now - startTime - pausedDuration - (isPaused ? now - pauseStart : 0);
            double elapsedHours = activeDuration / 3600000.0;

            double blocksPerHour = elapsedHours > 0 ? totalBlocksBroken / elapsedHours : 0;
            String blocksPerHourStr;
            if (blocksPerHour >= 100_000) {
                blocksPerHourStr = String.format("%.0fk", blocksPerHour / 1000);
            } else {
                blocksPerHourStr = String.format("%.0f", blocksPerHour);
            }
            double maxCompCount = (double) top.totalUnits / top.getMaxCompressionValue();
            double maxCompPerHour = elapsedHours > 0 ? maxCompCount / elapsedHours : 0;
            String maxPrefix = top.getMaxCompressionName();

            String stats = String.format(
                " §7| §fBlocks/h: §a%s §7| §f%s/h: §a%.2f §7| §f%s mined: §a%.2f%s",
                blocksPerHourStr, maxPrefix, maxCompPerHour, maxPrefix, maxCompCount,
                isPaused ? " §7[PAUSED]" : ""
            );

            client.player.sendMessage(
                top.getDisplayName().copy().append(Text.literal(stats)),
                true
            );
        });
    }
}