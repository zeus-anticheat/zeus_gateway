# Binary Protocol Specification

This document describes the exact binary format that zeus_plugins encodes into
each UDP datagram before sending to zeus_proxy. The Rust server must decode
these bytes in exactly the same order and widths.

## Byte Order

All multi-byte values are encoded in **big-endian** (network byte order). This
is the default of Java's `DataOutputStream`.

## Common Header

Every packet (except `PacketTPSServer`) begins with a common header produced
by `PacketBaseInfo.encodePlayerInfo()`:

```
Offset  Size   Type    Field
------  -----  ------  ------------------
0       1      u8      packet_id
1       8      i64     timestamp (epoch ms)
9       2      u16     uid_length
11      var    utf8    uid (UUID string, typically 36 bytes)
var     2      u16     username_length
var     var    utf8    username
```

- `packet_id` identifies which packet type follows.
- `timestamp` is `System.currentTimeMillis()`.
- `uid` is the Minecraft player UUID as a string (e.g. "550e8400-e29b-41d4-a716-446655440000").
- `username` is the in-game player name.

Strings are length-prefixed with a 2-byte unsigned short (`u16`).

## Packet Definitions

### 0x01 -- PacketPlayerJoin

```
[common header]
(no additional fields)
```

### 0x02 -- PacketPlayerLeave

```
[common header]
(no additional fields)
```

### 0x03 -- PacketPlayerPosition

```
[common header]
+0    1    u8      cancelled (0 or 1)
+1    8    f64     x  (feet position)
+9    8    f64     y
+17   8    f64     z
+25   8    f64     eye_x
+33   8    f64     eye_y
+41   8    f64     eye_z
+49   4    f32     yaw
+53   4    f32     pitch
+57   4    f32     height (bounding box height)
+61   1    u8      on_ground (0 or 1)
```

### 0x04 -- PacketPlayerKeepAlive

```
[common header]
+0    8    i64     ping (milliseconds)
```

### 0x05 -- PacketPlayerChangeMode

```
[common header]
+0    4    i32     gamemode ordinal
```

Gamemode values: 0=SURVIVAL, 1=CREATIVE, 2=ADVENTURE, 3=SPECTATOR.

### 0x06 -- PacketPlayerSwingHand

```
[common header]
+0    1    u8      cancelled (0 or 1)
```

### 0x07 -- PacketPlayerPlaceBlock

```
[common header]
+0    1    u8      cancelled (0 or 1)
+1    8    f64     block_x
+9    8    f64     block_y
+17   8    f64     block_z
```

### 0x08 -- PacketPlayerDiggingBlock

```
[common header]
+0    1    u8      cancelled (0 or 1)
+1    8    f64     block_x
+9    8    f64     block_y
+17   8    f64     block_z
```

### 0x09 -- PacketPlayerAttackEntity

```
[common header]
+0    ...  EntityState   target entity state
```

See "EntityState Encoding" below.

### 0x0A -- PacketPlayerTeleport

```
[common header]
+0    8    f64     destination_x
+8    8    f64     destination_y
+16   8    f64     destination_z
```

### 0x0B -- PacketPlayerEffect

```
[common header]
+0    ...  Effect       effect data
```

See "Effect Encoding" below.

### 0x0C -- PacketPlayerGotDamage

```
[common header]
+0    4    u32     damage_cause (big-endian)
```

See "DamageCause Values" below.

### 0x0D -- PacketPlayerBlockFace

```
[common header]
+0    1    u8      face
```

Face values: 0=DOWN, 1=UP, 2=NORTH, 3=SOUTH, 4=WEST, 5=EAST.

### 0x0E -- PacketPlayerBlockRayTrace

```
[common header]
+0    1    u8      hit_block (0 or 1)
+1    4    i32     block_x
+5    4    i32     block_y
+9    4    i32     block_z
+13   4    f32     hit_x (world-space)
+17   4    f32     hit_y
+21   4    f32     hit_z
+25   1    u8      action (0=INTERACT, 1=DIG, 2=PLACE; omitted=INTERACT for legacy senders)
```

### 0x0F -- PacketPlayerBlockChangeAck

```
[common header]
(no additional fields)
```

### 0x10 -- PacketPlayerAttackedByEntity

```
[common header]
+0    ...  EntityState   attacker entity state
```

### 0x11 -- PacketPlayerEntityInteraction

```
[common header]
+0    ...  EntityState   interacted entity state
```

### 0x12 -- PacketTPSServer

This packet does NOT have the common header. It contains only:

```
+0    1    u8      packet_id (0x12)
+1    8    f64     tps value
```

Note: The TPS packet has no timestamp, uid, or username. zeus_proxy must
handle this as a special case.

### 0x13 -- Reserved

This byte is an intentional protocol gap and has no decoder or producer.

### 0x14 -- PacketPlayerHeldItem

```
[common header]
+0    ...  Item         held item
```

See "Item Encoding" below.

### 0x15 -- PacketPlayerArmorsEquipment

```
[common header]
+0    ...  Armors       armor set (4 slots)
```

See "Armors Encoding" below.

### 0x16 -- PacketPlayerConfirmTransaction

```
[common header]
+0    1    u8      window_id
+1    2    u16     action_number
+3    1    u8      accepted (0 or 1)
```

### 0x17 -- PacketPlayerOpenWindow

```
[common header]
+0    1    u8      window_id
```

### 0x18 -- PacketPlayerClickWindow

```
[common header]
+0    1    u8      window_id
+1    2    i16     slot_id
+3    1    u8      button
+4    2    i16     mode
+6    2    i16     transaction_id
+8    ...  ItemStack    clicked item
```

See "ItemStack Encoding" below.

### 0x19 -- PacketPlayerCloseWindow

```
[common header]
+0    1    u8      window_id
```

### 0x1A -- PacketPlayerUseItem

```
[common header]
+0    1    u8      hand (0=MAIN, 1=OFF)
+1    1    u8      sequence
```

### 0x1B -- PacketPlayerReleaseUseItem

```
[common header]
+0    1    u8      hand (0=MAIN, 1=OFF)
```

### 0x1C -- PacketPlayerSteerVehicle

```
[common header]
+0    4    f32     sideway
+4    4    f32     forward
+8    1    u8      jump (0 or 1)
+9    1    u8      unmount (0 or 1)
```

### 0x1D -- PacketPlayerVehicleMove

```
[common header]
+0    8    f64     x
+8    8    f64     y
+16   8    f64     z
+24   4    f32     yaw
+28   4    f32     pitch
```

### 0x1E -- PacketServerBoundPlayerCommand

```
[common header]
+0    1    u8      action
```

Action values:

| Value | Constant              |
|-------|-----------------------|
| 1     | OPEN_INVENTORY        |
| 2     | PRESS_SHIFT_KEY       |
| 3     | RELEASE_SHIFT_KEY     |
| 4     | START_FALL_FLYING     |
| 5     | START_RIDING_JUMP     |
| 6     | STOP_RIDING_JUMP      |
| 7     | START_SPRINTING       |
| 8     | STOP_SPRINTING        |
| 9     | START_SNEAKING        |
| 10    | STOP_SNEAKING         |
| 11    | STOP_SLEEPING         |
| 12    | STOP_FALL_FLYING      |
| 13    | START_RIDING_BOAT     |
| 14    | STOP_RIDING_BOAT      |
| 15    | START_RIDING_VEHICLE  |
| 16    | STOP_RIDING_VEHICLE   |
| 17    | START_RIPTIDE         |
| 18    | STOP_RIPTIDE          |

### 0x1F -- PacketPlayerDeath

```
[common header]
(no additional fields)
```

### 0x20 -- PacketPlayerCustomFeature

```
[common header]
+0    4    i32     category_id
+4    4    i32     feature_id
+8    8    f64     feature_value
```

Category values: 1=COMBAT, 2=MOVEMENT, 3=INTERACT, 4=TRANSACTION, 5=OTHER.

### 0x21 -- PacketPlayerAttackedByPlayer

```
[common header]
+0    ...  EntityState   attacker player state
```

Structurally identical to `PacketPlayerAttackedByEntity` (0x10) but with a
different packet ID so zeus_proxy can distinguish player-on-player combat from
mob-on-player combat.

### 0x22 -- PacketPlayerVelocity

```
[common header]
+0    8    f64     x
+8    8    f64     y
+16   8    f64     z
```

### 0x23 -- PacketPlayerEnchantments

```
[common header]
+0    4    f32     entity_interaction_range (1.21.2+ attribute)
+4    2    u16     enchantments_count
For each enchantment:
  +0  2    u16     name_length
  +2  var  utf8    name (e.g. "minecraft:sharpness")
  var 1    u8      level
```

### 0x24 -- PacketPlayerRespawn

```
[common header]
(no additional fields)
```

### 0x25 -- PacketServerConfig

```
[common header]
+0    4    f32     server_reach
+4    4    f32     attack_cooldown_ticks
+8    1    u8      max_cps
```

Sent repeatedly by the game server during player joins and resync cycles. Allows `zeus_platform` to auto-adjust movement and combat analysis thresholds for non-vanilla server configurations.

### 0x26 -- PacketPlayerInventoryTransaction

```
[common header]
+0    1    u8      window_id
+1    4    i32     state_id
+5    2    i16     clicked_slot
+7    1    u8      button
+8    2    i16     mode
+10   2    i16     transaction_id
+12   ...  ItemStack cursor_stack
var   2    u16     changed_slot_count
For each changed slot:
  +0  2    i16     slot
  +2  ...  ItemStack item_stack
```

### 0x27 -- PacketPlayerExternalForce

```
[common header]
+0    1    u8      force_type
+1    8    f64     source_x
+9    8    f64     source_y
+17   8    f64     source_z
+25   8    f64     direction_x
+33   8    f64     direction_y
+41   8    f64     direction_z
+49   8    f64     velocity_x
+57   8    f64     velocity_y
+65   8    f64     velocity_z
+73   8    f64     strength
+81   2    i16     duration_ticks
+83   4    i32     flags
```

---

## Compound Type Encodings

### EntityState

```
+0    2    u16     eid_length
+2    var  utf8    eid (entity UUID string)
var   8    f64     eye_x
var   8    f64     eye_y
var   8    f64     eye_z
var   4    f32     yaw
var   4    f32     pitch
var   4    f32     height
var   4    f32     width
var   8    f64     packet_x (feet position x)
var   8    f64     packet_y
var   8    f64     packet_z
var   1    u8      on_ground (0 or 1)
```

Total fixed portion: 67 bytes + eid string.

### Effect

```
+0    1    u8      effect_id
+1    1    u8      amplifier
+2    4    i32     duration (ticks)
+6    1    u8      flags
```

Flags: 0=ADD, 1=MODIFY, 2=REMOVE.

### Item

```
+0    2    u16     item_name_length
+2    var  utf8    item_name (e.g. "DIAMOND_SWORD" or "minecraft:diamond_sword")
var   2    u16     custom_name_length
var   var  utf8    custom_name (display name, may be empty)
var   ...  ItemStack
```

### ItemStack

```
+0    2    u16     item_id_length
+2    var  utf8    item_id (stable key, e.g. "minecraft:diamond_sword"; empty if no item)
var   4    i32     meta (custom model data or legacy damage value)
var   1    u8      count
```

### Armor

```
+0    2    u16     armor_name_length
+2    var  utf8    armor_name
var   2    u16     custom_name_length
var   var  utf8    custom_name
var   4    i32     meta
```

### Armors

Encodes one presence bitmask followed by present armor slots in order:
helmet, chestplate, leggings, boots.

```
+0    1    u8      presence_flags (bit 0=helmet, 1=chestplate, 2=leggings, 3=boots)
+1    var  Armor   helmet if bit 0 set
var   var  Armor   chestplate if bit 1 set
var   var  Armor   leggings if bit 2 set
var   var  Armor   boots if bit 3 set
```

### RelativeBlock

```
+0    4    i32     dx
+4    4    i32     dy
+8    4    i32     dz
+12   2    u16     block_data_length
+14   var  utf8    block_data
```

---

## DamageCause Values

| Value | Name              |
|-------|-------------------|
| 0     | CONTACT           |
| 1     | ENTITY_ATTACK     |
| 2     | PROJECTILE        |
| 3     | SUFFOCATION       |
| 4     | FALL              |
| 5     | FIRE              |
| 6     | FIRE_TICK         |
| 7     | LAVA              |
| 8     | DROWNING          |
| 9     | BLOCK_EXPLOSION   |
| 10    | ENTITY_EXPLOSION  |
| 11    | VOID              |
| 12    | SUICIDE           |
| 13    | MAGIC             |
| 14    | CUSTOM            |
| 15    | STARVATION        |
| 16    | FALLING_BLOCK     |

## EffectType Values

| Value | Name               |
|-------|--------------------|
| 1     | SPEED              |
| 2     | SLOWNESS           |
| 3     | HASTE              |
| 4     | MINING_FATIGUE     |
| 5     | STRENGTH           |
| 6     | INSTANT_HEALTH     |
| 7     | INSTANT_DAMAGE     |
| 8     | JUMP_BOOST         |
| 9     | NAUSEA             |
| 10    | REGENERATION       |
| 11    | RESISTANCE         |
| 12    | FIRE_RESISTANCE    |
| 13    | WATER_BREATHING    |
| 14    | INVISIBILITY       |
| 15    | BLINDNESS          |
| 16    | NIGHT_VISION       |
| 17    | HUNGER             |
| 18    | WEAKNESS           |
| 19    | POISON             |
| 20    | WITHER             |
| 21    | HEALTH_BOOST       |
| 22    | ABSORPTION         |
| 23    | SATURATION         |
| 24    | GLOWING            |
| 25    | LEVITATION         |
| 26    | LUCK               |
| 27    | UNLUCK             |
| 28    | SLOW_FALLING       |
| 29    | CONDUIT_POWER      |
| 30    | DOLPHINS_GRACE     |
| 31    | BAD_OMEN           |
| 32    | HERO_OF_THE_VILLAGE|
| 255   | UNDEFINED          |
