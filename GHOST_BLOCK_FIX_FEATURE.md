# GhostBlockFix + SnappyInput — client-side ghost-block fixing & high-ping input masking

## Context

Two tightly-related problems on laggy or anticheat-heavy servers:

1. **Ghost blocks.** Client and server occasionally disagree about whether a block exists at a coordinate. After mining, the client renders air but the server still has the block (or vice-versa). The player walks through a hole that isn't actually open, slams into an invisible wall, or gets rubberbanded back. Vanilla recovery is `F3 + A` (full chunk reload) or a relog.
2. **Input feel on high ping.** Mining and placing feel mushy when round-trip is high, because the visible feedback waits on server confirmation.

These are linked: aggressive client-side prediction makes input feel snappy but creates ghosts whenever the server disagrees. The dial is *prediction vs. authority*. The two modules in this doc let the player pick a point on that dial:

- **GhostBlockFix** — silently re-syncs the client when desync is detected (reactive) or shortly after every break (proactive). Fires a `PlayerActionC2SPacket(START_DESTROY_BLOCK)` immediately followed by `PlayerActionC2SPacket(ABORT_DESTROY_BLOCK)` at the suspect coordinate. Vanilla servers respond with an authoritative `BlockUpdateS2CPacket` — the resync we want, with no visible side effect (no item use, no chat, no animation beyond one tick of "starting to mine").
- **SnappyInput** — masks ping by triggering local visual/audio feedback for actions on the very first input tick instead of waiting for server ack. Cosmetic only; can't make the server respond faster, just hides the wait.

Both modules are **client-only** — no server mod, no Fabric API server hooks, no protocol changes. SnappyInput is best paired with GhostBlockFix's proactive mode so that the snappier prediction doesn't leave ghosts behind.

## Activation requirements (important)

Both modules are **disabled by default**. They only run after the player explicitly enables them in-game. Three toggles, all independent:

| Command | Effect |
| --- | --- |
| `/ru gbf` | Toggles GhostBlockFix's reactive mode (collision + rubberband detection). |
| `/ru gbf proactive` | Toggles GhostBlockFix's proactive verification (re-verify every mined block ~300 ms later). Has no effect unless the main `gbf` toggle is also ON. |
| `/ru snap` | Toggles SnappyInput (cosmetic input prediction). Independent of GhostBlockFix but pairs well with `gbf proactive`. |

While disabled, each module's hook methods early-return before any work:
- `GhostBlockFix.onBlockBroken` / `onClientTick` / `onRubberband` early-return when `!isEnabled()`.
- `GhostBlockFix` scheduled proactive verifications early-return when `!proactiveEnabled`.
- `SnappyInput` mixin handlers early-return when `!isEnabled()`.
- No packets are ever sent and no animations are forced.

Toggle state lives in memory only — it resets to OFF on every client launch (intentional; no persistence). The standard rageutils actionbar feedback (`[RageUtils] GhostBlockFix: §aON` / `§cOFF`, etc.) confirms each toggle, and both modules show up in `/ru`'s help listing automatically via `ModuleRegistry`. The proactive sub-toggle shows its own actionbar line (`[RageUtils] GhostBlockFix proactive: §aON`).

## Target project

Adding to the existing **rageutils** mod (not a new project).

- Root: `C:\Users\banip\rageutils\`
- MC `1.21.11`, Yarn `1.21.11+build.5`, Fabric Loader `0.19.2`, Fabric API `0.140.0+1.21.11`, Java 21. No new dependencies needed.
- Existing package layout (mirror it):
  - `wintahh.rageutils` — `RageUtils.java` (ClientModInitializer)
  - `wintahh.rageutils.module` — `Module` base, `ModuleRegistry`, existing `ClientSideBlast`, `RateHUD`
  - `wintahh.rageutils.mixin` — existing `ClientPlayerInteractionManagerMixin`
  - `wintahh.rageutils.command` — `RageUtilsCommand` (Brigadier client commands)

## Files to create

### 1. `src/main/java/wintahh/rageutils/module/GhostBlockFix.java`

New module extending `wintahh.rageutils.module.Module` (`src/main/java/wintahh/rageutils/module/Module.java`). Constructor: `super("GhostBlockFix", "/ru gbf")`. The base class already defaults `enabled = false`, so the default-off requirement is satisfied by extending it.

State (all private, instance-level):

- `Map<BlockPos, Long> recentlyMined` — bounded LinkedHashMap keyed by immutable `BlockPos`, value = `System.currentTimeMillis()` at break. Max entries 128, evicted in insertion order, also pruned by TTL.
- `Map<BlockPos, Long> lastVerifiedAt` — per-position cooldown to avoid spamming the server.
- `Map<BlockPos, Long> pendingProactiveVerify` — positions to verify proactively, value = absolute `System.currentTimeMillis()` at which to fire. Populated by `onBlockBroken` when `proactiveEnabled` is true; drained by `onClientTick`.
- `private boolean proactiveEnabled = false` — independent sub-toggle for proactive verification. Toggled via `toggleProactive()` from the command tree.
- Constants: `MEMORY_TTL_MS = 10_000`, `VERIFY_COOLDOWN_MS = 1_500`, `SEARCH_RADIUS = 2` (blocks), `MAX_VERIFICATIONS_PER_TICK = 2`, `PROACTIVE_DELAY_MS = 300`, `MAX_PROACTIVE_PER_TICK = 4`.

Public methods (every one must check `isEnabled()` first and early-return if false):

- `void onBlockBroken(BlockPos pos)` — called from the existing break mixin; clones the pos with `pos.toImmutable()` and inserts into `recentlyMined`. Prunes expired entries on insert. **If `proactiveEnabled` is true, also schedule `pendingProactiveVerify.put(pos.toImmutable(), now + PROACTIVE_DELAY_MS)`.**
- `void onClientTick(MinecraftClient mc)` — registered as a `ClientTickEvents.END_CLIENT_TICK` listener in `RageUtils.onInitializeClient()`. Body:
  1. Bail if `!isEnabled()`, `mc.player == null`, `mc.world == null`, or `mc.getNetworkHandler() == null`.
  2. Prune `recentlyMined` of entries older than `MEMORY_TTL_MS`.
  3. If `mc.player.horizontalCollision || mc.player.verticalCollision`, call `verifyNearby(mc, mc.player.getBlockPos())` (reactive path).
  4. **Drain due proactive entries**: iterate `pendingProactiveVerify`, and for any whose scheduled time `<= now`, call `sendVerificationPacket(mc, pos)` and remove the entry. Cap at `MAX_PROACTIVE_PER_TICK` per tick to keep the burst small after a mining streak; leftovers stay queued for next tick.
- `void onRubberband(BlockPos before, Vec3d after)` — called from the rubberband mixin (below). Calls `verifyNearby` at both `before` and `BlockPos.ofFloored(after)`.
- `void toggleProactive()` — flips `proactiveEnabled` and sends an actionbar line `[RageUtils] GhostBlockFix proactive: §aON` / `§cOFF`. Mirrors the look/feel of `Module.toggle()` but for the sub-flag. Add it on `GhostBlockFix`, not on the base `Module` class.
- `private void verifyNearby(MinecraftClient mc, BlockPos center)` — iterates `recentlyMined` keys; for each within `SEARCH_RADIUS` chebyshev distance of `center`, call `sendVerificationPacket(mc, pos)`. Cap calls per invocation at `MAX_VERIFICATIONS_PER_TICK`.
- `private void sendVerificationPacket(MinecraftClient mc, BlockPos pos)` — cooldown check against `lastVerifiedAt`; on success:
  ```java
  ClientPlayNetworkHandler net = mc.getNetworkHandler();
  Direction face = Direction.UP; // arbitrary; server ignores face for ABORT
  net.sendPacket(new PlayerActionC2SPacket(
      PlayerActionC2SPacket.Action.START_DESTROY_BLOCK, pos, face));
  net.sendPacket(new PlayerActionC2SPacket(
      PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, pos, face));
  lastVerifiedAt.put(pos.toImmutable(), now);
  ```
  The two-packet pair is what vanilla sends when a player taps and releases mine. Servers respond with a `BlockUpdateS2CPacket` if their state differs from what the client just claimed by starting to mine — exactly the resync we want. No item is consumed, no animation persists.

### 2. `src/main/java/wintahh/rageutils/mixin/ClientPlayNetworkHandlerMixin.java`

New mixin to detect server-forced position corrections (rubberbanding).

```java
@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onPlayerPositionLook", at = @At("HEAD"))
    private void rageutils$onRubberband(PlayerPositionLookS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;
        BlockPos before = mc.player.getBlockPos();
        Vec3d after = packet.change().position();
        RageUtils.GHOST_BLOCK_FIX.onRubberband(before, after);
    }
}
```

Yarn mapping note for the executor: in 1.21.11, the S2C packet exposes a `PlayerPosition` record via `packet.change()`; verify the exact accessor name with the Yarn jar (could be `change()` or `playerPosition()`). The class lives at `net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket`. If the method name `onPlayerPositionLook` has changed in the active mappings, grep `ClientPlayNetworkHandler` in the decompiled Minecraft jar for the `PlayerPositionLookS2CPacket` consumer and use that exact name.

### 3. Update `src/main/resources/rageutils.mixins.json`

Add `"ClientPlayNetworkHandlerMixin"` to the `mixins` array next to the existing `ClientPlayerInteractionManagerMixin`. Also add `"SnappyInputMixin"` (described below).

### 4. `src/main/java/wintahh/rageutils/module/SnappyInput.java`

New module extending `wintahh.rageutils.module.Module`. Constructor: `super("SnappyInput", "/ru snap")`. Defaults off (from base class).

Purpose: hide perceived input delay on high-ping connections by triggering local visual/audio feedback **on the input tick** instead of waiting for server acknowledgement. This is purely cosmetic — it cannot reduce actual round-trip time, and it cannot make actions complete faster. It only fixes the *feel*.

Concrete behaviors (each guarded by `if (!isEnabled()) return;`):

1. **Instant swing animation on attack input.** Already mostly vanilla, but on some servers/anticheats the swing visual waits for hit confirmation. Force `mc.player.swingHand(Hand.MAIN_HAND)` on the input tick if a left-click was registered and vanilla hasn't already swung.
2. **Instant block-break particle + sound at break completion.** When the client predicts a finished mine (vanilla calls `ClientPlayerInteractionManager.breakBlock` → returns true), immediately spawn the break particle (`mc.particleManager.addBlockBreakParticles(pos, state)`) and play the block break sound locally. Vanilla already does this in most cases, but on some servers the chunk update suppresses it.
3. **Predicted block placement render.** When right-clicking with a `BlockItem`, set the client-side block state to the predicted placement *one tick* before the vanilla prediction would. Use the same `World.setBlockState(..., Block.NOTIFY_ALL | Block.FORCE_STATE)` path vanilla uses, with the *predicted* sequence id so the vanilla rollback machinery still works if the server rejects.

Implementation entry points:

- `void onInputTick(MinecraftClient mc)` — called from a new mixin on `ClientPlayerEntity.tick` (HEAD or TAIL), routes to the helpers above based on `mc.options.attackKey.wasPressed()` / `useKey.wasPressed()` if necessary. Keep mixin minimal — most logic stays in the module.
- `void onBlockBreakPredicted(BlockPos pos, BlockState oldState)` — call from inside the existing `ClientPlayerInteractionManagerMixin` on `breakBlock` HEAD (we already have that hook). Spawns particles + sound if vanilla didn't.
- `void onBlockPlacePredicted(BlockPos pos, ItemStack stack)` — called from a new mixin on `ClientPlayerInteractionManager.interactBlock` HEAD; do nothing more than what vanilla already does today *unless* a future need arises. For the v1 of this module, **leave place-prediction as a no-op stub** and only ship the swing + break-feedback behaviors. (Marked as a follow-up below.)

### 5. `src/main/java/wintahh/rageutils/mixin/SnappyInputMixin.java`

Tiny mixin into `ClientPlayerEntity` to give SnappyInput a per-tick callback:

```java
@Mixin(ClientPlayerEntity.class)
public class SnappyInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void rageutils$onTick(CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        RageUtils.SNAPPY_INPUT.onInputTick(mc);
    }
}
```

No additional mixin classes needed for v1 — break-feedback piggybacks on the existing `ClientPlayerInteractionManagerMixin` (one extra call alongside the GhostBlockFix one).

## Files to modify

### `src/main/java/wintahh/rageutils/RageUtils.java`

- Add:
  ```java
  public static final GhostBlockFix GHOST_BLOCK_FIX = new GhostBlockFix();
  public static final SnappyInput SNAPPY_INPUT = new SnappyInput();
  ```
- In `onInitializeClient()`:
  - `ModuleRegistry.register(GHOST_BLOCK_FIX);`
  - `ModuleRegistry.register(SNAPPY_INPUT);`
  - Register tick listener:
    ```java
    ClientTickEvents.END_CLIENT_TICK.register(GHOST_BLOCK_FIX::onClientTick);
    ```

### `src/main/java/wintahh/rageutils/mixin/ClientPlayerInteractionManagerMixin.java`

After the existing `RageUtils.CLIENTSIDE_BLAST.onBreakBlock(pos, face);` line, add:

```java
RageUtils.GHOST_BLOCK_FIX.onBlockBroken(pos);
BlockState oldState = MinecraftClient.getInstance().world.getBlockState(pos);
RageUtils.SNAPPY_INPUT.onBlockBreakPredicted(pos, oldState);
```

Capture `oldState` *before* anything else mutates the world so the particles match the destroyed block. Keep ordering with existing comment intent — `RATE_HUD` first, then `CLIENTSIDE_BLAST`, then `GHOST_BLOCK_FIX`, then `SNAPPY_INPUT`.

### `src/main/java/wintahh/rageutils/command/RageUtilsCommand.java`

Add a `gbf` literal under the main `rageutils`/`ru` command (mirroring the `rh` nested-toggle structure at lines 52–62, not the flat `csb` one, because `gbf` needs a sub-toggle):

```java
.then(ClientCommandManager.literal("gbf")
    .executes(ctx -> {
        RageUtils.GHOST_BLOCK_FIX.toggle();
        return 1;
    })
    .then(ClientCommandManager.literal("proactive")
        .executes(ctx -> {
            RageUtils.GHOST_BLOCK_FIX.toggleProactive();
            return 1;
        })))
.then(ClientCommandManager.literal("snap")
    .executes(ctx -> {
        RageUtils.SNAPPY_INPUT.toggle();
        return 1;
    }))
```

The existing listing loop at line 36–41 already auto-discovers registered modules via `ModuleRegistry.getAll()`, so no extra wiring needed for the help output. The `proactive` sub-toggle is intentionally undocumented in the auto-listing — power-user feature; `/ru gbf` is what's surfaced.

## Reused code

- `Module` base class (`module/Module.java`) — gives us `toggle()`, `isEnabled()`, `getName()`, `getCommand()`, default `enabled = false`, and the standard `[RageUtils] GhostBlockFix: ON/OFF` actionbar feedback. Do not duplicate this logic.
- `ModuleRegistry` — registration + help listing already works generically.
- Existing `ClientPlayerInteractionManagerMixin` — extend, don't replace. The HEAD inject on `breakBlock(BlockPos)` is already the correct hook point for tracking mined positions.
- `RageUtilsCommand` Brigadier setup — append a new literal, don't restructure.

## Verification

Manual smoke tests in a dev runClient:

1. **Compile & launch**: `gradlew runClient` from `C:\Users\banip\rageutils\`. No mixin apply errors in `logs/latest.log` (look for `mixin apply failed`).
2. **Default off**: join a world, mine some blocks, walk around. Confirm no extra packets are sent (temp log in `sendVerificationPacket` should never fire) and no forced swing animations. `/ru` should list both `GhostBlockFix : /ru gbf` and `SnappyInput : /ru snap`.
3. **GhostBlockFix toggle**: `/ru gbf` — actionbar shows `[RageUtils] GhostBlockFix: §aON`. Toggle off: `§cOFF`. Confirm OFF really disables.
4. **GhostBlockFix proactive toggle**: with main `gbf` ON, run `/ru gbf proactive` — actionbar shows `[RageUtils] GhostBlockFix proactive: §aON`. Mine a block; confirm a verification packet fires ~300 ms later (temp log). Then toggle proactive off; mine again; confirm no proactive packet (reactive collision path still works).
5. **SnappyInput toggle**: `/ru snap` — actionbar shows `[RageUtils] SnappyInput: §aON`. With high simulated latency (use Clumsy or a `tc` netem rule on Linux to add 200 ms RTT) and SnappyInput off, mining feels mushy: swing and break particle wait on round-trip. Toggle SnappyInput on; swing animation and break particles should fire on the input tick regardless of latency. Toggle off; mushiness returns.
6. **Tracking sanity check** (temporarily add a `LOGGER.info` in `onBlockBroken` while testing, remove before final): with GhostBlockFix ON, break blocks, confirm positions logged; confirm entries expire after 10 s.
7. **Synthetic ghost block test**: on a local LAN server, fake a ghost by setting a position to AIR client-side without the server agreeing (dev console / debug). With GhostBlockFix off, you stay stuck. With it on (`/ru gbf`), the client should resync within one tick of contact and the wall disappears. With `gbf proactive` also on, the ghost should resync ~300 ms after the original break, before you ever touch it.
8. **Rubberband test**: trigger a server-side movement reset (e.g. `/tp` from a second account). With GhostBlockFix ON, the mixin fires and any tracked ghost block near both the pre- and post-teleport positions re-verifies. Confirm via packet log (`-Dmixin.debug=true` or a temporary `LOGGER.debug` inside `sendVerificationPacket`).
9. **Cooldown**: with GhostBlockFix ON, rapidly pulse against a wall in the same spot; confirm `sendVerificationPacket` fires at most once per `VERIFY_COOLDOWN_MS` per position (temporary log).
10. **Proactive cap**: turn on `gbf proactive` and mine 20 blocks in rapid succession. Confirm `MAX_PROACTIVE_PER_TICK` caps the burst (queue drains over several ticks, no packet flood).
11. **SnappyInput safety**: with `snap` ON, confirm no item duplication, no server-side rejection chat (`Server is too far behind` etc.). The mod must never *call* an action; it only forces the local feedback for actions the player already initiated.
12. **Off-server safety**: in singleplayer (integrated server), confirm all packets are well-formed and the integrated server handles them identically — no crashes, no inventory or block side effects, even with all three toggles ON.

Remove all temporary logging before considering the task done.

## Out of scope

- No new GUI / config screen — toggle via `/ru gbf`, `/ru gbf proactive`, and `/ru snap` only.
- No persistence across sessions — all state is in-memory and toggles reset to OFF on launch.
- No fallback to right-click probing if `ABORT_DESTROY` doesn't resync — revisit if real-world testing shows certain servers ignore the abort.
- No predicted block placement in SnappyInput v1 — the `onBlockPlacePredicted` hook is a stub. Add only if v1 testing shows place lag is still painful on high ping after the swing/break improvements.
- No instrumentation/metrics — keep both modules quiet by default.
