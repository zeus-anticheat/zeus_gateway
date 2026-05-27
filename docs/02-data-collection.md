# Data Collection -- What Physical Processing zeus_plugins Does

This document describes the physical data processing that occurs inside
zeus_plugins before data reaches zeus_proxy. Understanding these
transformations is critical for anyone implementing a compatible collector.

## Overview of Responsibilities

zeus_plugins is responsible for:

1. Intercepting raw game events and packets from the Minecraft server.
2. Extracting the relevant numeric and string fields.
3. Mapping Minecraft-internal values to the Zeus protocol's normalised enums.
4. Computing derived values that the server does not provide directly.
5. Encoding everything into binary and transmitting via UDP.

zeus_proxy (Rust) receives the raw bytes and can assume all mapping and
computation has already been done on the Java side.

---

## Per-Packet Processing Details

### Position (0x03)

**Source**: ProtocolLib `POSITION` / `POSITION_LOOK` packets; or Fabric
per-tick polling.

**Processing**:
- Extracts `x`, `y`, `z` directly from the protocol packet (feet position).
- Computes `eye_x`, `eye_y`, `eye_z` from the player entity: eye position
  is `(x, y + eyeHeight, z)` where `eyeHeight` comes from the entity model.
- Reads `yaw`, `pitch` from the packet.
- Reads `height` from the player's bounding box.
- Reads `on_ground` from the packet's ground flag.
- `cancelled` is always `false` unless the packet event was cancelled
  (ProtocolLib only).

**What zeus_proxy receives**: A complete snapshot of the player's spatial
state at packet-receive time. The proxy does not need to compute eye position
or height.

### Surrounding Blocks (0x13)

**Source**: Sent alongside every position packet.

**Processing**:
- Scans a 3x5x3 grid of blocks centered on the player's feet.
- The grid spans `dx = [-1, +1]`, `dy = [-2, +2]`, `dz = [-1, +1]`.
- Each block is represented by its relative offset and its full Minecraft
  block-state string (e.g. `minecraft:stone`, `minecraft:oak_stairs[facing=north,half=bottom]`).
- Total: 45 blocks per position update.

**What zeus_proxy receives**: A flat list of 45 `(dx, dy, dz, block_state)`
tuples. The proxy can use these to determine terrain context (ground type,
nearby liquids, slippery blocks, etc.) without needing access to the world.

### Keep Alive (0x04)

**Source**: ProtocolLib `KEEP_ALIVE` packet (Bukkit); or Fabric per-tick
polling (every 20 ticks).

**Processing**:
- On Bukkit: reads the `KEEP_ALIVE` client packet and queries `player.getPing()`.
- On Fabric: reads `networkHandler.getLatency()` every second.

**What zeus_proxy receives**: The player's current latency in milliseconds.

### Attack Entity (0x09)

**Source**: Paper `PrePlayerAttackEntityEvent`; Spigot
`EntityDamageByEntityEvent`; Fabric `AttackEntityCallback`.

**Processing**:
- Identifies the attacker as the player.
- Builds an `EntityState` for the attacked entity, including:
  - Entity UUID.
  - Position (feet x, y, z).
  - Eye position. For `LivingEntity`, uses `getEyeHeight()`. For non-living
    entities, approximates as `entityY + height * 0.85`.
  - Yaw, pitch, bounding-box height, on-ground flag.

**What zeus_proxy receives**: Complete spatial state of the target at the
moment of attack. The proxy uses this for reach/aim analysis.

### Attacked By Entity (0x10) and Attacked By Player (0x21)

**Source**: `EntityDamageByEntityEvent` (Bukkit); `ALLOW_DAMAGE` listener
(Fabric).

**Processing**:
- Determines whether the damage source is a player or non-player entity.
- Sends `0x21` if the attacker is a `Player`; `0x10` otherwise.
- Builds an `EntityState` for the attacker (not the victim).

**What zeus_proxy receives**: Which entity hit the player, its full spatial
state, and whether it was a player or mob. The proxy uses this to distinguish
knockback from player combat versus mob knockback.

### Player Death (0x1F)

**Source**: `PlayerDeathEvent` (Bukkit); `ServerLivingEntityEvents.AFTER_DEATH` (Fabric).

**Processing**:
- Captured when the player dies.

**What zeus_proxy receives**: The exact moment the player died.

### Velocity (0x22)

**Source**: `PlayerVelocityEvent` (Gateway); Fabric adapter velocity/event state capture.

**Processing**:
- Captures the server-calculated velocity vector pushed to the player.

**What zeus_proxy receives**: The x, y, and z components of the velocity applied to the player.

### Inventory Transaction (0x26)

**Source**: ProtocolLib/event snapshots (Gateway); `ClickSlotC2SPacket`
post-handler snapshot (Fabric exact-version adapter).

**What zeus_proxy receives**: Window/state ID, click input, cursor stack and
the changed slot map without altering the existing `PacketPlayerClickWindow`
payload.

### External Force (0x27)

**Source**: Damage/velocity/piston environment capture in each platform adapter.

**What zeus_proxy receives**: Existing classified force type, direction,
velocity, strength, duration and flags; representation is frozen by protocol
fixtures and is not normalised between source APIs.

### Got Damage (0x0C)

**Source**: `EntityDamageEvent` (Bukkit); `ALLOW_DAMAGE` (Fabric).

**Processing**:
- Maps Minecraft's `DamageCause` enum (which has platform-specific values)
  to the Zeus `DamageCause` enum (17 values).
- Bukkit mapping: uses a `switch` expression on `EntityDamageEvent.DamageCause`.
  `ENTITY_SWEEP_ATTACK` is folded into `ENTITY_ATTACK`. `MAGIC`, `POISON`,
  and `WITHER` are all mapped to `MAGIC`.
- Fabric mapping: uses `DamageSource.getName()` string matching because
  Fabric does not expose the same enum.

**What zeus_proxy receives**: A normalised integer cause code.

### Potion Effects (0x0B)

**Source**: `EntityPotionEffectEvent` (Bukkit); per-tick polling on Fabric.

**Processing**:
- On Bukkit: catches add/modify/remove events directly.
- On Fabric: diffs the current effect list against the previous tick.
  Tracks adds, removals, and modifications (amplifier change or duration
  jump > 5 ticks).
- Effect type is resolved by key name (`PotionEffectType.getKey().getKey()`)
  and mapped to the Zeus `EffectType` enum. Numeric IDs are deprecated.
- Flags: 0=ADD, 1=MODIFY, 2=REMOVE.

**What zeus_proxy receives**: Individual effect events with type ID,
amplifier, duration, and operation flag.

### TPS (0x12)

**Source**: Scheduled task (Bukkit); `END_SERVER_TICK` callback (Fabric).

**Processing**:
- On Paper/Spigot 1.19+: reads `Server.getTPS()[0]` (1-minute average)
  via reflection.
- Fallback: measures wall-clock time between task runs and computes
  `TPS = tickPeriod * 1000 / elapsedMs`, then applies an exponential
  moving average (alpha=0.2).
- Result is clamped to `[0, 20]`.

**Special encoding**: This packet has NO common header. It is just
`[0x12][8-byte f64 tps]`. zeus_proxy must handle this as a special case.

### Held Item (0x14)

**Source**: ProtocolLib `HELD_ITEM_SLOT` packet; `PlayerItemHeldEvent`
(Bukkit fallback); per-tick polling (Fabric).

**Processing**:
- Reads the new slot index.
- Looks up the `ItemStack` in that inventory slot.
- Extracts: stable namespaced item key (e.g. `minecraft:diamond_sword`),
  custom display name, platform-supported `meta`, and stack count.
- Wraps into an `Item(name, customName, ItemStack(id, meta, count))`.

**What zeus_proxy receives**: Full item identity for the held item.

### Click Window (0x18)

**Source**: ProtocolLib `WINDOW_CLICK` packet; `InventoryClickEvent`
(Bukkit fallback); `ClickSlotC2SPacket` interception (Fabric).

**Processing**:
- On Fabric, reads `syncId`, slot, button, and action type from the incoming
  packet before the screen handler applies the click.
- Snapshots the pre-click item in the addressed slot, or an empty stack for
  clicks outside a valid slot.
- Uses `transactionId = 0` on modern Fabric, where confirm-transaction packet
  semantics no longer exist.

**What zeus_proxy receives**: Inventory click intent and the pre-click item
encoded with the stable item-key wire format.

### Armor Equipment (0x15)

**Source**: Paper `PlayerArmorChangeEvent`; Bukkit inventory-close polling;
per-tick-with-hash-check polling (Fabric).

**Processing**:
- Reads all four armor slots (helmet, chestplate, leggings, boots).
- For each: extracts name, custom name, custom model data.
- On Fabric: computes a hash of all four slots and only sends when it changes.

**What zeus_proxy receives**: The complete armor loadout.

### Block Face (0x0D)

**Source**: ProtocolLib `BLOCK_DIG` direction field; `PlayerInteractEvent`
block face (Bukkit); `AttackBlockCallback` direction (Fabric).

**Processing**:
- Maps the platform-specific direction enum to a byte:
  0=DOWN, 1=UP, 2=NORTH, 3=SOUTH, 4=WEST, 5=EAST.

### Block Ray Trace (0x0E)

**Source**: `PlayerInteractEvent` with `rayTraceBlocks(5.0)` (Bukkit);
per-4-tick raycasting (Fabric).

**Processing**:
- Casts a ray from the player's eye position along their look direction (5
  blocks max).
- If a block is hit: reports the block position (integers) and the exact
  world-space hit position (floats).
- If no hit: reports hit_block=false and zero coordinates.

### Vehicle Steering (0x1C)

**Source**: ProtocolLib `STEER_VEHICLE` packet (Bukkit); derived from
movement deltas on Fabric.

**Processing**:
- On Bukkit (ProtocolLib): reads `sideway`, `forward` (floats), `jump`,
  `unmount` (booleans) directly from the packet.
- On Fabric: projects the vehicle's world-space movement delta onto the
  player's local forward/strafe axes using yaw rotation, then clamps to
  [-1, 1].

### Player Commands (0x1E)

**Source**: ProtocolLib `ENTITY_ACTION` packet (Bukkit); toggle events for
sneak/sprint/flight (Bukkit fallback); per-tick state comparison (Fabric).

**Processing**:
- On Bukkit (ProtocolLib): maps `EnumWrappers.PlayerAction` to the Zeus
  `ServerBoundPlayerCommandActions` enum. Falls back to NMS action ordinals
  if the enum wrapper fails.
- On Bukkit (events): `PlayerToggleSneakEvent`, `PlayerToggleSprintEvent`,
  `PlayerToggleFlightEvent`.
- Vehicle enter/exit: `VehicleEnterEvent`/`VehicleExitEvent` mapped to
  START/STOP_RIDING_BOAT or START/STOP_RIDING_VEHICLE based on vehicle type.
- On Fabric: polls `isSneaking()`, `isSprinting()`, `isGliding()`,
  `isUsingRiptide()` each tick and emits start/stop transitions.

### Custom Feature (0x20)

**Source**: Direct API call by third-party code.

**Processing**:
- Accepts a category ID, feature ID, and feature value.
- No transformation -- the caller provides the final values.
- The category ID maps to an analysis module on the proxy side (combat,
  movement, interact, transaction, other).

### PacketPlayerEnchantments (0x23)

**Source**: per-tick polling (Fabric).

**Processing**:
- Iterates through all equipped items (hand, offhand, armor).
- Extracts enchantments and their levels using the Minecraft component model.
- Reads the `ENTITY_INTERACTION_RANGE` attribute (introduced in 1.21.2).
- Only sends when the hash of the enchantment list or the reach attribute changes.

**What zeus_proxy receives**: A list of active enchantments and the player's 
current maximum interaction range. Used to detect reach/hitbox cheats that 
bypass standard limits.

---

## Summary of Computed/Derived Values

These are values that zeus_plugins computes rather than reads directly:

| Value                 | Computation                                    |
|-----------------------|------------------------------------------------|
| Eye position          | Player feet Y + entity eye height              |
| Entity eye position   | Entity Y + eyeHeight (living) or height*0.85   |
| Vehicle steer inputs  | World delta projected onto player-local axes   |
| TPS (fallback)        | Wall-clock timing with EMA smoothing           |
| Surrounding blocks    | 3x5x3 grid scan around player feet             |
| Block ray trace       | Server-side raycasting from eye along look dir  |
| Effect diffs (Fabric) | Per-tick list diff with 5-tick duration tolerance|
| Armor hash (Fabric)   | Combined hash of 4 armor slots for change detect|
| Teleport detection    | Fabric: distance > 8 blocks between ticks      |
