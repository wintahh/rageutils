package wintahh.rageutils.module;
 
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import wintahh.rageutils.RageUtils;
 
import java.util.Locale;
import java.util.*;
 
public class RateHUD extends Module {
 
    // -------------------------------------------------------------------------
    // Resource Type
    // -------------------------------------------------------------------------
 
    public static class ResourceType {
        public final String name;
        public final int color;
        public final long compressedValue;
        public final long superCompressedValue;
        public final long ultraCompressedValue;
        public long totalUnits = 0;
 
        public ResourceType(String name, int color, long compressed, long superCompressed, long ultraCompressed) {
            this.name = name;
            this.color = color;
            this.compressedValue = compressed;
            this.superCompressedValue = superCompressed;
            this.ultraCompressedValue = ultraCompressed;
        }
 
        public long getValueForItemName(String displayName, int count) {
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
            return "Comp";
        }
 
        public long getMaxCompressionValue() {
            if (ultraCompressedValue > 0) return ultraCompressedValue;
            if (superCompressedValue > 0) return superCompressedValue;
            return compressedValue;
        }
    }

    public enum Anchor {
        TOP_LEFT("top-left"),
        TOP_RIGHT("top-right"),
        BOTTOM_LEFT("bottom-left"),
        BOTTOM_RIGHT("bottom-right");
 
        public final String label;
        Anchor(String label) { this.label = label; }
    }
 
    private static final long WINDOW_MS = 30_000;
 
    private boolean miningEnabled = false;
    private Anchor anchor = Anchor.TOP_LEFT;
    private boolean screenOpenLastTick = false;
 
    private final Map<String, Long> previousBaseUnits = new HashMap<>();
    private final List<ResourceType> resources = new ArrayList<>();
 
    // Rolling window data
    private final Deque<long[]> blockBreakEvents = new ArrayDeque<>();           // [timestamp, blockCount]
    private final Map<String, Deque<long[]>> resourceTimestamps = new HashMap<>(); // [timestamp, units]
 
    // Auto-swap: always show most recently collected resource
    private ResourceType lastCollectedResource = null;
 
    // Timing
    private long startTime = 0;
    private long lastBreakTime = 0;
 
    // HUD display cache (updated every tick)
    private ResourceType displayResource = null;
    private String displayBlocksPerHour = "0";
    private String displayCompressionPrefix = "Comp";
    private double displayCompressionPerHour = 0;
    private double displayCompressionMined = 0;
    private boolean displayPaused = false;
 
    public RateHUD() {
        super("RateHUD", "/ru rh");
        initResources();
    }

 
    public void onBlockBroken(BlockPos pos, Direction face) {
        if (!miningEnabled) return;
        long now = System.currentTimeMillis();
        if (startTime == 0) startTime = now;
        lastBreakTime = now;
 
        int count = 1;
 
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null && face != null) {
            Direction.Axis faceAxis = face.getAxis();
            Direction.Axis axisA = null, axisB = null;
            for (Direction.Axis axis : Direction.Axis.VALUES) {
                if (axis == faceAxis) continue;
                if (axisA == null) axisA = axis;
                else axisB = axis;
            }
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    if (i == 0 && j == 0) continue;
                    BlockPos target = offsetPos(pos, axisA, i, axisB, j);
                    if (RageUtils.CLIENTSIDE_BLAST.canBlastTarget(target)) count++;
                }
            }
        }
 
        blockBreakEvents.addLast(new long[]{now, count});
    }
 
    public void onBlocksBroken(int blockCount) {
        if (!miningEnabled || blockCount <= 0) return;
        long now = System.currentTimeMillis();
        if (startTime == 0) startTime = now;
        lastBreakTime = now;
        blockBreakEvents.addLast(new long[]{now, blockCount});
    }
 
    private BlockPos offsetPos(BlockPos origin, Direction.Axis axisA, int a, Direction.Axis axisB, int b) {
        int x = origin.getX(), y = origin.getY(), z = origin.getZ();
        if (axisA == Direction.Axis.X) x += a; else if (axisA == Direction.Axis.Y) y += a; else z += a;
        if (axisB == Direction.Axis.X) x += b; else if (axisB == Direction.Axis.Y) y += b; else z += b;
        return new BlockPos(x, y, z);
    }
 
    public boolean isMiningEnabled() { return miningEnabled; }
 
    public void toggleMining() {
        miningEnabled = !miningEnabled;
        resetCounters();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(
                Text.literal("[RageUtils] RateHUD Mining: " + (miningEnabled ? "§aON" : "§cOFF")),
                false
            );
        }
    }
 
    public void setAnchor(Anchor anchor) {
        this.anchor = anchor;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.player.sendMessage(
                Text.literal("[RageUtils] RateHUD anchor: §e" + anchor.label),
                true
            );
        }
    }
 
    public void resetCounters() {
        resources.forEach(ResourceType::reset);
        blockBreakEvents.clear();
        resourceTimestamps.clear();
        lastCollectedResource = null;
        startTime = 0;
        lastBreakTime = System.currentTimeMillis();
        screenOpenLastTick = false;
        previousBaseUnits.clear();
        clearDisplayStats();
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            previousBaseUnits.putAll(getBaseUnitSnapshot(mc));
        }
    }
 
    // -------------------------------------------------------------------------
    // Inventory Tracking
    // -------------------------------------------------------------------------
 
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
 
        // Include cursor stack to avoid false deltas from inventory rearrangement
        if (mc.player.currentScreenHandler != null) {
            ItemStack cursor = mc.player.currentScreenHandler.getCursorStack();
            if (!cursor.isEmpty()) {
                String displayName = cursor.getName().getString();
                for (ResourceType resource : resources) {
                    long value = resource.getValueForItemName(displayName, cursor.getCount());
                    if (value > 0) {
                        snapshot.merge(resource.name, value, Long::sum);
                        break;
                    }
                }
            }
        }
 
        return snapshot;
    }
 
    private void processDelta(Map<String, Long> current) {
        long now = System.currentTimeMillis();
        for (ResourceType resource : resources) {
            long currentUnits  = current.getOrDefault(resource.name, 0L);
            long previousUnits = previousBaseUnits.getOrDefault(resource.name, 0L);
            long delta = currentUnits - previousUnits;
            if (delta > 0) {
                resource.totalUnits += delta;
                // Auto-swap: if resource changed, reset the rolling window
                if (lastCollectedResource != resource) {
                    blockBreakEvents.clear();
                    resourceTimestamps.clear();
                    startTime = now;
                    lastBreakTime = now;
                }
                lastCollectedResource = resource;
                resourceTimestamps.computeIfAbsent(resource.name, k -> new ArrayDeque<>())
                    .addLast(new long[]{now, delta});
            }
        }
    }
 
    private ResourceType getTopResource() {
        return resources.stream()
            .filter(r -> r.totalUnits > 0)
            .max(Comparator.comparingLong(r -> r.totalUnits))
            .orElse(null);
    }
 
    // -------------------------------------------------------------------------
    // Resource Definitions
    // -------------------------------------------------------------------------
 
    private void initResources() {
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
 
    // -------------------------------------------------------------------------
    // Events
    // -------------------------------------------------------------------------
 
    public void registerEvents() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!miningEnabled || client.player == null || client.world == null) {
                clearDisplayStats();
                return;
            }
 
            if (client.currentScreen != null) {
                screenOpenLastTick = true;
                return;
            }
            if (screenOpenLastTick) {
                previousBaseUnits.clear();
                previousBaseUnits.putAll(getBaseUnitSnapshot(client));
                screenOpenLastTick = false;
                return;
            }
 
            Map<String, Long> current = getBaseUnitSnapshot(client);
            processDelta(current);
            previousBaseUnits.clear();
            previousBaseUnits.putAll(current);
 
            ResourceType top = lastCollectedResource != null ? lastCollectedResource : getTopResource();
            if (top == null || startTime == 0) {
                clearDisplayStats();
                return;
            }
 
            long now = System.currentTimeMillis();
            long windowStart = now - WINDOW_MS;
            boolean isPaused = (now - lastBreakTime) > 3000;
 
            // --- Blocks/h (rolling window) ---
            while (!blockBreakEvents.isEmpty() && blockBreakEvents.peekFirst()[0] < windowStart)
                blockBreakEvents.pollFirst();
            long blocksInWindow = 0;
            for (long[] e : blockBreakEvents) blocksInWindow += e[1];
 
            // --- UC/h (rolling window) ---
            Deque<long[]> resEvents = resourceTimestamps.get(top.name);
            long unitsInWindow = 0;
            if (resEvents != null) {
                while (!resEvents.isEmpty() && resEvents.peekFirst()[0] < windowStart)
                    resEvents.pollFirst();
                for (long[] e : resEvents) unitsInWindow += e[1];
            }
 
            // Use actual elapsed time so rates are accurate from the first block
            long actualWindowMs = Math.min(now - startTime, WINDOW_MS);
            if (actualWindowMs <= 0) {
                clearDisplayStats();
                return;
            }
            double windowSeconds = actualWindowMs / 1000.0;
 
            double blocksPerHour = blocksInWindow * (3600.0 / windowSeconds);
            double maxCompPerHour = (unitsInWindow / (double) top.getMaxCompressionValue()) * (3600.0 / windowSeconds);
            double maxCompMined = (double) top.totalUnits / top.getMaxCompressionValue();
 
            String blocksPerHourStr;
            if (blocksPerHour >= 100_000) {
                blocksPerHourStr = String.format(Locale.ROOT, "%.0fk", blocksPerHour / 1000);
            } else {
                blocksPerHourStr = String.format(Locale.ROOT, "%.0f", blocksPerHour);
            }
 
            displayResource = top;
            displayBlocksPerHour = blocksPerHourStr;
            displayCompressionPrefix = top.getMaxCompressionName();
            displayCompressionPerHour = maxCompPerHour;
            displayCompressionMined = maxCompMined;
            displayPaused = isPaused;
        });
    }
 
    // -------------------------------------------------------------------------
    // HUD Rendering
    // -------------------------------------------------------------------------
 
    public void renderHud(DrawContext context) {
        if (!miningEnabled) return;
 
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
 
        TextRenderer textRenderer = mc.textRenderer;
        int padding = 5;
        int lineHeight = 10;
        int titleYellow = 0xfffff04b;
        int grayBar = 0xffa8a8a8;
        int labelColor = 0xffffffff;
        int valueColor = 0xff55ff55;
        int mutedColor = 0xffa8a8a8;
 
        String resourceName = displayResource == null ? "Waiting for drops" : displayResource.name;
        int resourceColor = displayResource == null ? mutedColor : (0xff000000 | displayResource.color);
        
        String compRateLine = String.format(Locale.ROOT, "%.2f", displayCompressionPerHour);
        String compMinedLine = String.format(Locale.ROOT, "%.2f", displayCompressionMined);
        String statusLine = displayPaused ? "PAUSED" : "ACTIVE";
 
        String compRateLabel = displayCompressionPrefix + "/h";
        String compMinedLabel = displayCompressionPrefix + " Mined";
        
        String[] labels = {"Blocks/h", compRateLabel, compMinedLabel, "Status"};
        String[] values = {displayBlocksPerHour, compRateLine, compMinedLine, statusLine};
 
        int labelWidth = 0;
        int valueWidth = 0;
        for (String label : labels) labelWidth = Math.max(labelWidth, textRenderer.getWidth(label));
        for (String value : values) valueWidth = Math.max(valueWidth, textRenderer.getWidth(value));
 
        int gap = 12;
        
        // Dynamic box sizing calculations for split-rendering the header row safely
        int fullTitleWidth = textRenderer.getWidth("RateHUD ") + textRenderer.getWidth("| ") + textRenderer.getWidth(resourceName);
        int width = Math.max(fullTitleWidth, labelWidth + gap + valueWidth) + padding * 2;
        int height = padding * 2 + lineHeight * (labels.length + 1);
 
        int margin = 8;
        int screenWidth = context.getScaledWindowWidth();
        int screenHeight = context.getScaledWindowHeight();
        int x = switch (anchor) {
            case TOP_LEFT, BOTTOM_LEFT -> margin;
            case TOP_RIGHT, BOTTOM_RIGHT -> screenWidth - width - margin;
        };
        int y = switch (anchor) {
            case TOP_LEFT, TOP_RIGHT -> margin;
            case BOTTOM_LEFT, BOTTOM_RIGHT -> screenHeight - height - margin;
        };
 
        context.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0x66000000);
        context.fill(x, y, x + width, y + height, 0xaa101010);
        
        // Piece together the title securely line-by-line using individual color weights
        int curX = x + padding;
        context.drawTextWithShadow(textRenderer, "RateHUD ", curX, y + padding, titleYellow);
        curX += textRenderer.getWidth("RateHUD ");
        context.drawTextWithShadow(textRenderer, "| ", curX, y + padding, grayBar);
        curX += textRenderer.getWidth("| ");
        context.drawTextWithShadow(textRenderer, resourceName, curX, y + padding, resourceColor);
 
        int rowY = y + padding + lineHeight + 1;
        int valueX = x + padding + labelWidth + gap;
        
        drawRow(context, textRenderer, labels[0], values[0], x + padding, valueX, rowY, labelColor, valueColor);
        drawRow(context, textRenderer, labels[1], values[1], x + padding, valueX, rowY + lineHeight, labelColor, valueColor);
        drawRow(context, textRenderer, labels[2], values[2], x + padding, valueX, rowY + lineHeight * 2, labelColor, valueColor);
        drawRow(context, textRenderer, labels[3], values[3], x + padding, valueX, rowY + lineHeight * 3, labelColor, displayPaused ? 0xffff5555 : valueColor);
    }
 
    private void drawRow(DrawContext context, TextRenderer textRenderer, String label, String value,
                         int labelX, int valueX, int y, int labelColor, int valueColor) {
        context.drawTextWithShadow(textRenderer, label, labelX, y, labelColor);
        context.drawTextWithShadow(textRenderer, value, valueX, y, valueColor);
    }
 
    private void clearDisplayStats() {
        displayResource = null;
        displayBlocksPerHour = "0";
        displayCompressionPrefix = "Comp";
        displayCompressionPerHour = 0;
        displayCompressionMined = 0;
        displayPaused = false;
    }
}