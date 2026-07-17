# Platform Differences

This document describes how data capture differs between platform adapters.
Published target status is defined only by `support-matrix.json`.

## ZeusGateway with ProtocolLib (Best Coverage)

When ProtocolLib is available, the following packets are captured at the raw
protocol level, providing the highest timing precision:

| Packet                  | ProtocolLib Source Packet  |
|-------------------------|---------------------------|
| Position                | POSITION, POSITION_LOOK   |
| AttackEntity            | USE_ENTITY (attack action) |
| SwingHand               | ARM_ANIMATION             |
| KeepAlive               | KEEP_ALIVE                |
| PlaceBlock              | USE_ITEM                  |
| DiggingBlock            | BLOCK_DIG                 |
| BlockFace               | BLOCK_DIG (direction)     |
| HeldItem                | HELD_ITEM_SLOT            |
| ClickWindow             | WINDOW_CLICK              |
| UseItem                 | USE_ITEM                  |
| SteerVehicle            | STEER_VEHICLE             |
| VehicleMove             | VEHICLE_MOVE              |
| PlayerCommand           | ENTITY_ACTION             |

Raw listeners capture packet primitives and timestamps before the server
processes the action. For captures requiring Bukkit state (attack entity
resolution, position environment/surrounding blocks, held-item state and
validated commands), state lookup and emission are scheduled on the
main/entity region thread while retaining that receive timestamp.

## ZeusGateway without ProtocolLib

Without ProtocolLib, the following changes apply:

- **Position**: Not captured at all. There is no Bukkit equivalent for raw
  position packets. This is a significant gap; zeus_proxy will not receive
  continuous position updates.
- **SwingHand**: Falls back to `PlayerAnimationEvent` (main-hand only).
- **KeepAlive**: Falls back to reading `Player.getPing()` from the
  keep-alive listener, but the raw keep-alive packet timing is lost.
- **PlaceBlock**: Falls back to `BlockPlaceEvent`.
- **DiggingBlock**: Falls back to `BlockBreakEvent`.
- **BlockFace**: Falls back to `PlayerInteractEvent.getBlockFace()`.
- **HeldItem**: Falls back to `PlayerItemHeldEvent`.
- **ClickWindow**: Falls back to `InventoryClickEvent`.
- **UseItem**: Falls back to `PlayerInteractEvent` (right-click).
- **SteerVehicle**: No fallback. Not captured without ProtocolLib.
- **VehicleMove**: Falls back to `VehicleMoveEvent`.
- **Velocity**: Falls back to `PlayerVelocityEvent`.
- **Death**: Captured via `PlayerDeathEvent`.
- **AttackedByPlayer**: Captured via `EntityDamageByEntityEvent`.
- **PlayerCommand**: Falls back to toggle sneak/sprint/flight events.
- **AttackEntity**: Falls back to Bukkit damage events, which observe server
  handling rather than initial raw intent.

## ZeusGateway -- Paper vs Spigot

| Feature                  | Paper                          | Spigot                       |
|--------------------------|--------------------------------|------------------------------|
| Attack detection         | PacketEvents, then EntityDamageByEntityEvent fallback | PacketEvents, then EntityDamageByEntityEvent fallback |
| Armor change detection   | Slot change + inventory-close snapshot | Slot change + inventory-close snapshot |
| Event precision          | Raw packet intent where available | Raw packet intent where available |
| State Resynchronization  | ResyncTask runs every 200 ticks | ResyncTask runs every 200 ticks |

ZeusGateway includes a `ResyncTask` that periodically (every 10 seconds)
sends a full state snapshot (join, effects, gamemode, commands) for all 
players to ensure the proxy stays in sync even after a restart.

## ZeusFabric

Fabric has a fundamentally different capture model:

- **Event-driven packets**: Join, leave, attack entity, use block, use item,
  block break, damage, death, attacked by player. These use Fabric API callbacks and fire
  immediately when the action occurs.
- **Velocity and external force**: Captured from Fabric event/tick logic when
  the mapped adapter can identify the server-applied change/source.

- **Tick-polled packets**: Position, keep-alive, held item, armor, game mode,
  effects, sneaking/sprinting/gliding/riptide, vehicle state, screen handler,
  block ray trace, enchantments/reach. These are checked every server tick (50 ms) by iterating
  all online players.
- **Raw inventory click packet**: `ClickSlotC2SPacket` is intercepted before
  the screen handler mutates slots, allowing `PacketPlayerClickWindow` to
  carry the pre-click item snapshot and `PacketPlayerInventoryTransaction`
  to carry the post-handler changed-slot/cursor state. Modern Fabric sends
  transaction ID `0`.

### Tick Polling Throttling

Not all tick-polled data is sent every tick:

| Data          | Frequency        | Condition                                   |
|---------------|-----------------|---------------------------------------------|
| Position      | Every tick       | Only if position changed (distance > 0.001) |
| Keep-alive    | Every 20 ticks   | Counter modulo check                        |
| Held item     | Every tick       | Only if slot changed; sole held-item source |
| Armor         | Every tick       | Only if armor hash changed                  |
| Game mode     | Every tick       | Only if mode ordinal changed                |
| Effects       | Every tick       | Only if effect list differs (with tolerance) |
| Commands      | Every tick       | Only on state transitions                   |
| Vehicle       | Every tick       | Only if position changed                    |
| Screen handler| Every tick       | Only if syncId changed                      |
| Block ray     | Every 4 ticks    | Counter modulo check                        |
| Enchantments  | Every tick       | Only if list or reach attribute changed     |


### Teleport Detection

Fabric does not have a dedicated teleport event. Instead, the tick-polling
code detects teleports by checking if the squared distance between the
current and previous positions exceeds 64 (i.e. 8 blocks). When detected,
both a `PacketPlayerTeleport` and a `PacketPlayerPosition` are sent.
ZeusGateway detects teleports via `PlayerTeleportEvent`. Both platforms
emit `PacketCollisionWindow` (0x30) for terrain context.

### World State Source

ZeusGateway emits block deltas (`0x2B`), bounded chunk batches (`0x2D`),
and collision terrain windows (`0x30`).
`0x13` is reserved and has no producer or decoder.

### DamageCause Mapping Differences

- Bukkit: Uses `EntityDamageEvent.DamageCause` enum with a switch expression.
- Fabric: Uses `DamageSource.getName()` string matching. Some damage types
  use different internal names (e.g. `"inWall"` for suffocation, `"outOfWorld"`
  for void).

Both map to the same Zeus `DamageCause` integer values, so zeus_proxy sees
consistent data regardless of source platform.

### Effect Type Resolution

- Bukkit: Uses `PotionEffectType.getKey().getKey()` and maps through
  `EffectType.fromKey()`.
- Fabric: Uses `Registries.STATUS_EFFECT.getRawId()` to get a numeric ID.

The resulting `effectId` byte may differ between platforms for effects added
in newer versions, but the core 30+ effects share consistent IDs.
