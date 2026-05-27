# Zeus Plugins

Zeus Anti-Cheat data collector plugins for Minecraft servers. These plugins capture player behavior data and stream it via UDP to the Zeus anti-cheat analysis server.

## Architecture

```
zeus_plugins/
├── ZeusProtocolJava/      # Shared packet codec library (platform-agnostic)
├── ZeusGateway/           # ZeusGateway-modern: Paper/Spigot/Folia, Java 8+ bytecode, 1.14+
├── ZeusGatewayLegacy/     # ZeusGateway-legacy: Spigot/Paper, Java 8 bytecode, 1.8-1.13.x
├── ZeusFabric/            # Exact-version Fabric artifact build
├── scenarios/             # Mineflayer-based core scenario smoke driver
├── scripts/               # Support-matrix render/verify and smoke runners
├── verification/evidence/ # Per-target build/smoke evidence consumed by the gate
├── docs/                  # Detailed documentation
├── pom.xml                # Parent Maven POM
├── build.cmd              # Windows build script
└── README.md
```

## Support Status

[`support-matrix.json`](support-matrix.json) is the release source of truth. Its rendered table is in
[`docs/generated/support-matrix.md`](docs/generated/support-matrix.md), and current gate readiness is in
[`docs/generated/support-readiness.md`](docs/generated/support-readiness.md). A server target is published as
`supported` only when its artifact build, protocol fixtures, server-startup smoke, and core-scenario smoke
gates all pass on real servers; the smoke driver lives in `scenarios/` and exercises attack, velocity,
inventory transaction, surrounding-blocks and external-force packet paths against an offline-mode server.

ZeusGateway ships as **two artifacts** — a single JAR cannot cover the full announced range:

- `ZeusGateway-legacy` (Java 8 bytecode, Spigot/Paper, 1.8 – 1.13.x).
- `ZeusGateway-modern` (Java 8 bytecode, Paper / Spigot / Folia, 1.14 – 1.21.x).

Targets that have passed all gates and are currently `supported`:

| Target | Artifact | Java |
|--------|----------|------|
| spigot-1.8.8 | `ZeusGateway-legacy` | 8 |
| spigot-1.13.2 | `ZeusGateway-legacy` | 8 |
| spigot-1.14.4 | `ZeusGateway-modern` | 8+ |
| paper-1.21.7 | `ZeusGateway-modern` | 21 |
| ZeusFabric-1.21.2 through 1.21.11 | exact-version Fabric | 21 |

`ZeusFabric` 1.21 and 1.21.1 remain `adapter-required`: those releases used a different `ActionResult` shape, `BlockPos.iterate(Box)` overload, and `EntityAttributes` registry that the current shared source cannot reach via reflection alone. They need a per-version source set or branch-specific adapter before promotion.

### ZeusGateway Modern (Paper / Spigot / Folia)

The modern artifact (Java 8 bytecode, runs on Java 8 through 21) auto-detects platform/runtime capabilities:

- **Paper**: Modern adapter uses Paper-exclusive events (`PrePlayerAttackEntityEvent`, `PlayerArmorChangeEvent`) alongside ProtocolLib packet listeners and standard Bukkit events.
- **Spigot**: Modern adapter falls back to Bukkit events (`EntityDamageByEntityEvent`, inventory-close armor polling) when Paper APIs are unavailable.
- **Folia**: Modern adapter uses a scheduler wrapper for region/entity tasks; supported target status is still governed by the manifest.

**ProtocolLib** is optional. When present and its required packet capability is available, it enables raw
packet-level listeners. Missing capabilities are logged and use event fallbacks where they exist; movement
and vehicle-input capture are degraded when no raw equivalent is available.

### ZeusGateway Legacy (Spigot 1.8 – 1.13.x)

The legacy artifact targets servers running Java 8 on Spigot/Paper 1.8 through 1.13.x. It uses the same
`ZeusProtocolJava` codec and emits identical wire packets as the modern artifact. Differences:

- No Paper-exclusive events (no `PrePlayerAttackEntityEvent`, no `PlayerArmorChangeEvent`).
- No ProtocolLib dependency — all capture is Bukkit-event-only.
- Entity dimensions, block collision, and potion effects resolved via NMS reflection or material-based fallback.
- `InventoryView` accessed via reflection to avoid class/interface mismatch across API versions.

### Version Compatibility Layer

ZeusGateway-modern includes a built-in **compatibility layer** (`compat/` package) that abstracts version-sensitive APIs:

| Component | 1.14+ (Tier 1) | 1.8–1.13 (Tier 2) |
|-----------|----------------|--------------------|
| Entity dimensions | `Entity.getBoundingBox()` | NMS reflection (`Entity.width`, `Entity.length`) |
| Block collision | `Block.getBoundingBox()` | Material-based shape estimation (15+ block categories) |
| Block data string | `BlockData.getAsString()` | Synthetic `minecraft:` + material name |
| Air detection | `Material.isAir()` | Name matching (AIR, CAVE_AIR, VOID_AIR) |
| Potion effect key | `PotionEffectType.getKey()` | `getName()` + legacy-to-modern mapping |
| Player height | `Entity.getHeight()` | Sneaking detection fallback (1.8f / 1.5f) |

All compat methods use a **triple-layer safety model**: version flag check → API call → `catch (NoSuchMethodError | NoClassDefFoundError)` fallback.

### ZeusFabric

Fabric artifacts are exact-version builds because Yarn/NMS and mixin targets are not a cross-version
compatibility contract. Build-verifiable Fabric adapters are listed in the generated support matrix; each
artifact's `fabric.mod.json` requires its exact Minecraft version.

---

## Modules

### ZeusProtocolJava

The shared, **platform-agnostic** packet codec library. Contains:

- Packet ID constants (`PacketId`)
- Base classes (`PacketBase`, `PacketBaseInfo`, `PacketEncode`, `PacketDecode`)
- Binary serialization utilities (`ByteBufferUtil`)
- All protocol packets through ID `0x27`
- Shared data types (`EntityState`, `Effect`, `Item`, `ItemStack`, `Armor`, `Armors`, `Enchantment`, etc.)
- Enums (`Hand`, `DamageCause`, `EffectType`, `EffectFlags`, `ServerBoundPlayerCommandActions`)

This library has **zero** external dependencies — no Bukkit, no Fabric, no Minecraft classes. It compiles with just the JDK.

### Protocol Packets

| # | Packet ID | Class | Description |
|---|-----------|-------|-------------|
| 1 | `0x01` | `PacketPlayerJoin` | Player connected to the server |
| 2 | `0x02` | `PacketPlayerLeave` | Player disconnected from the server |
| 3 | `0x03` | `PacketPlayerPosition` | Player movement (position + rotation + eye + onGround) |
| 4 | `0x04` | `PacketPlayerKeepAlive` | Keep-alive heartbeat with ping |
| 5 | `0x05` | `PacketPlayerChangeMode` | Game mode changed (survival, creative, etc.) |
| 6 | `0x06` | `PacketPlayerSwingHand` | Arm animation / hand swing |
| 7 | `0x07` | `PacketPlayerPlaceBlock` | Block placed at position |
| 8 | `0x08` | `PacketPlayerDiggingBlock` | Block digging started/finished |
| 9 | `0x09` | `PacketPlayerAttackEntity` | Player attacked an entity |
| 10 | `0x0A` | `PacketPlayerTeleport` | Player teleported to position |
| 11 | `0x0B` | `PacketPlayerEffect` | Potion effect added/modified/removed |
| 12 | `0x0C` | `PacketPlayerGotDamage` | Player took damage (with cause) |
| 13 | `0x0D` | `PacketPlayerBlockFace` | Block face direction during interaction |
| 14 | `0x0E` | `PacketPlayerBlockRayTrace` | Block ray trace hit result |
| 15 | `0x0F` | `PacketPlayerBlockChangeAck` | Block change acknowledgement |
| 16 | `0x10` | `PacketPlayerAttackedByEntity` | Player was attacked by an entity |
| 17 | `0x11` | `PacketPlayerEntityInteraction` | Player interacted with an entity |
| 18 | `0x12` | `PacketTPSServer` | Server TPS measurement |
| 19 | `0x13` | `PacketPlayerSurroundingBlocks` | 3×5×3 block grid around the player |
| 20 | `0x14` | `PacketPlayerHeldItem` | Held item slot changed |
| 21 | `0x15` | `PacketPlayerArmorsEquipment` | Armor equipment state (helmet, chest, legs, boots) |
| 22 | `0x16` | `PacketPlayerConfirmTransaction` | Inventory transaction confirmation |
| 23 | `0x17` | `PacketPlayerOpenWindow` | Inventory/container window opened |
| 24 | `0x18` | `PacketPlayerClickWindow` | Inventory slot clicked |
| 25 | `0x19` | `PacketPlayerCloseWindow` | Inventory/container window closed |
| 26 | `0x1A` | `PacketPlayerUseItem` | Item used (right-click with item) |
| 27 | `0x1B` | `PacketPlayerReleaseUseItem` | Item use released (e.g. bow release) |
| 28 | `0x1C` | `PacketPlayerSteerVehicle` | Vehicle steering input |
| 29 | `0x1D` | `PacketPlayerVehicleMove` | Vehicle position changed |
| 30 | `0x1E` | `PacketServerBoundPlayerCommand` | Player command actions (sneak, sprint, elytra, etc.) |
| 31 | `0x1F` | `PacketPlayerDeath` | Player died |
| 32 | `0x20` | `PacketPlayerCustomFeature` | Custom feature value mapped from client/plugin |
| 33 | `0x21` | `PacketPlayerAttackedByPlayer` | Player was attacked by another player |
| 34 | `0x22` | `PacketPlayerVelocity` | Player velocity changed |
| 35 | `0x23` | `PacketPlayerEnchantments` | Player's current item enchantments and reach attribute |
| 36 | `0x24` | `PacketPlayerRespawn` | Player respawn event |
| 37 | `0x25` | `PacketServerConfig` | Server combat configuration |
| 38 | `0x26` | `PacketPlayerInventoryTransaction` | Inventory state transaction detail |
| 39 | `0x27` | `PacketPlayerExternalForce` | Classified external movement force |

---

## ZeusGateway Data Sources

Each packet is captured through one or more data sources, depending on platform and available dependencies:

| Packet | ProtocolLib Listener | Bukkit Event | Paper Event |
|--------|---------------------|-------------|-------------|
| Join / Leave | — | `PlayerJoinEvent` / `PlayerQuitEvent` | — |
| Position | `POSITION`, `POSITION_LOOK` | — | — |
| KeepAlive | `KEEP_ALIVE` | — | — |
| SwingHand | `ARM_ANIMATION`, `USE_ENTITY` | — | — |
| PlaceBlock | `USE_ITEM` | `BlockPlaceEvent` | — |
| DiggingBlock | `BLOCK_DIG` | `BlockBreakEvent` | — |
| AttackEntity | — | `EntityDamageByEntityEvent` | `PrePlayerAttackEntityEvent` |
| Teleport | — | `PlayerTeleportEvent` | — |
| Effect | — | `EntityPotionEffectEvent` | — |
| GotDamage | — | `EntityDamageEvent` | — |
| BlockFace | `BLOCK_DIG` (direction) | `PlayerInteractEvent` | — |
| BlockRayTrace | — | `PlayerInteractEvent` + rayTrace | — |
| BlockChangeAck | — | `PlayerInteractEvent` | — |
| AttackedByEntity | — | `EntityDamageByEntityEvent` | — |
| EntityInteraction | — | `PlayerInteractEntityEvent` | — |
| TPS | — | Scheduled task (BukkitScheduler / Folia) | — |
| SurroundingBlocks | (with Position) | — | — |
| HeldItem | `HELD_ITEM_SLOT` | `PlayerItemHeldEvent` | — |
| ArmorsEquipment | — | Inventory close polling | `PlayerArmorChangeEvent` |
| ConfirmTransaction | — | `InventoryClickEvent` | — |
| OpenWindow | — | `InventoryOpenEvent` | — |
| ClickWindow | `WINDOW_CLICK` | `InventoryClickEvent` | — |
| CloseWindow | — | `InventoryCloseEvent` | — |
| UseItem | `USE_ITEM` | `PlayerInteractEvent` | — |
| ReleaseUseItem | — | `PlayerItemConsumeEvent` | — |
| SteerVehicle | `STEER_VEHICLE` | — | — |
| VehicleMove | `VEHICLE_MOVE` | `VehicleMoveEvent` | — |
| PlayerCommand | `ENTITY_ACTION` | Toggle sneak/sprint/flight events | — |
| Death | — | `PlayerDeathEvent` | — |
| AttackedByPlayer | — | `EntityDamageByEntityEvent` | — |
| Velocity | — | `PlayerVelocityEvent` | — |

---

## Custom Packets

The `PacketPlayerCustomFeature` allows developers to send custom behavior metrics (e.g., CPS, aim accuracy, custom macros) to the Zeus analysis server with near-zero overhead. It uses an integer ID via `CustomFeatureCategory` to save bandwidth and prevent typos.

```java
import org.vennv.packets.PacketPlayerCustomFeature;
import org.vennv.utils.CustomFeatureCategory;
// For Paper/Spigot/Folia:
import org.vennv.zeusGateway.provider.PacketQueue;
// For Fabric:
// import org.vennv.zeusFabric.provider.PacketQueue;

public void onPlayerClick(String uid, String username, double clickSpeed) {
    long timestamp = System.currentTimeMillis();
    PacketPlayerCustomFeature packet = new PacketPlayerCustomFeature(
        timestamp, uid, username,
        CustomFeatureCategory.COMBAT, // Numeric Category ID
        0, // Numeric Feature ID (Dynamic index 0, 1, 2... automatically scales feature vector size)
        clickSpeed
    );
    PacketQueue.push(packet);
}
```

Predefined categories: `COMBAT(1)`, `MOVEMENT(2)`, `INTERACT(3)`, `TRANSACTION(4)`, `OTHER(5)`.

---

## Platform & Version Detection

ZeusGateway automatically detects the runtime platform and Minecraft version on startup:

```
[ZeusGateway] Detected platform: PAPER
[ZeusGateway] Server version: 1.21.11 (NMS: mojang-mapped)
[ZeusGateway] Feature flags: BoundingBox=true, BlockData=true, RayTrace=true, MaterialIsAir=true, CustomModelData=true, PotionKey=true, EntityHeight=true, EntityPose=true
[ZeusGateway] ProtocolLib v5.3.0 detected and hooked successfully.
[ZeusGateway] Using scheduler adapter for platform: PAPER
[ZeusGateway] Registered 12 ProtocolLib packet listeners.
[ZeusGateway] Registered Bukkit event listeners.
[ZeusGateway] Registered Paper-exclusive event listeners.
[ZeusGateway] TPS monitor and Resync task started via Bukkit scheduler.
[ZeusGateway] Plugin enabled successfully on PAPER!
```

The planned legacy adapter would report capabilities like the following on a `1.8.8` smoke target; this is not a published support result:

```
[ZeusGateway] Detected platform: SPIGOT
[ZeusGateway] Server version: 1.8.8 (NMS: v1_8_R3)
[ZeusGateway] Feature flags: BoundingBox=false, BlockData=false, RayTrace=false, MaterialIsAir=false, CustomModelData=false, PotionKey=false, EntityHeight=false, EntityPose=false
[ZeusGateway] ProtocolLib v4.8.0 detected and hooked successfully.
```

### Platform Detection Order
1. **Folia** — checks for `io.papermc.paper.threadedregions.RegionizedServer`
2. **Paper** — checks for `io.papermc.paper.event.player.PrePlayerAttackEntityEvent`
3. **Spigot** — fallback if neither Folia nor Paper classes are found

### Version Detection (`ServerVersion`)
Parses `Bukkit.getBukkitVersion()` to extract major/minor/patch, then resolves 9 boolean feature flags using **both** version comparison and runtime reflection checks. This dual approach ensures correctness even on non-standard server forks.

---

## Building

### Prerequisites

- **JDK 21+** to build current modules; `ZeusProtocolJava` itself emits Java 8 bytecode
- **Maven 3.6+** (for ZeusProtocolJava and ZeusGateway)
- **Gradle 8+** (for ZeusFabric, optional)
- **Python 3** (release build scripts verify generated support-matrix documentation and claim gates)

### Quick Build (Windows)

```bat
build.cmd
```

This builds:
1. `ZeusProtocolJava` → `ZeusProtocolJava/target/ZeusProtocolJava-1.0-SNAPSHOT.jar`
2. `ZeusGateway-modern` → `ZeusGateway/target/ZeusGateway-modern-1.0-SNAPSHOT.jar`
3. `ZeusGateway-legacy` → `ZeusGatewayLegacy/target/ZeusGateway-legacy-1.0-SNAPSHOT.jar`
4. All build-verifiable Fabric targets from `support-matrix.json` → `ZeusFabric/build/libs/ZeusFabric-<mc>-1.0-SNAPSHOT.jar` (if Gradle is available)

### Manual Build

```bash
# Build shared library
mvn clean install -pl ZeusProtocolJava

# Build modern Bukkit plugin (Paper/Spigot/Folia adapter)
mvn clean package -pl ZeusGateway -am

# Build Java 8 legacy Bukkit plugin foundation
mvn clean package -pl ZeusGatewayLegacy -am

# Build exact-version Fabric adapters listed by support-matrix.json
cd ZeusFabric
for target in $(python3 ../scripts/list_fabric_build_targets.py); do
  ./gradlew build -PmcTarget="$target"
done
```

For the current release gate, run:

```bash
bash scripts/verify_release_gate.sh
```

This checks generated docs, support claims, protocol fixtures, Gateway/Fabric builds, artifact metadata,
and the Rust protocol/network decode path when the parent workspace is present.

To include real startup smoke tests, provide pre-provisioned server directories:

```bash
ZEUS_SMOKE_ACCEPT_EULA=true \
ZEUS_GATEWAY_SMOKE_DIR=/path/to/paper-server \
ZEUS_GATEWAY_SMOKE_COMMAND="java -Xmx1G -jar paper.jar nogui" \
bash scripts/verify_release_gate.sh
```

The same hook exists for Fabric via `ZEUS_FABRIC_SMOKE_DIR`,
`ZEUS_FABRIC_SMOKE_COMMAND`, and optional `ZEUS_FABRIC_SMOKE_TARGET`.
Gateway smoke defaults to `ZeusGateway-modern`; set `ZEUS_GATEWAY_SMOKE_TARGET`
or `ZEUS_GATEWAY_CORE_SMOKE_TARGET` to `ZeusGateway-legacy` for legacy fixtures.

Core scenario smoke can be included by setting `ZEUS_GATEWAY_CORE_SMOKE_DIR`,
`ZEUS_GATEWAY_CORE_SMOKE_COMMAND`, and optional `ZEUS_GATEWAY_SCENARIO_COMMAND`.
Fabric uses the same pattern with the `ZEUS_FABRIC_CORE_*` variables. The
default core profile is `compatibility-core`, which requires packet IDs `0x09`,
`0x13`, `0x22`, `0x26`, and `0x27`.

### Output JARs

| Artifact | Deploy to | Platforms |
|----------|----------|-----------|
| `ZeusGateway-modern-1.0-SNAPSHOT.jar` | `plugins/` folder | Modern Gateway adapter; see manifest status |
| `ZeusGateway-legacy-1.0-SNAPSHOT.jar` | `plugins/` folder | Legacy Gateway adapter foundation; see manifest status |
| `ZeusFabric-<mc>-1.0-SNAPSHOT.jar` | `mods/` folder | Exact-version Fabric artifact; see manifest status |

---

## Configuration

### ZeusGateway (`plugins/ZeusGateway/config.yml`)

```yaml
proxy-ac:
  host: 0.0.0.0
  port: 9999

packets:
  batch-size: 100

# --- Server Combat Configuration (Fallback) ---
# Zeus Plugins AUTO-DETECTS combat changes (Reach/Cooldown) via Attributes in MC 1.9+ and 1.21.2+.
# These config values are ONLY used as fallbacks for:
# 1. Legacy Custom Jars (Minecraft 1.8) where Attributes do not exist but Reach is increased via NMS.
# 2. Server-enforced max CPS rules.
server-combat:
  server_reach: 3.0
  attack_cooldown_ticks: 10.0
  max_cps: 0
```

### ZeusFabric (`config/zeusfabric.properties`)

```properties
proxy-host=0.0.0.0
proxy-port=9999
batch-size=100
server-reach=3.0
attack-cooldown-ticks=10.0
max-cps=0
```

> **Note on Combat Sync**: The `server-combat` fallback values are transmitted to `sv_core` via `PacketServerConfig (0x25)` only when native player attributes cannot be read. `Zeus Plugins` automatically intercepts vanilla attributes (like `GENERIC_ATTACK_SPEED` and `PLAYER_ENTITY_INTERACTION_RANGE`) and dynamically overrides these fallbacks per-player. This ensures the ML Inference engine adapts gracefully to modified server or minigame mechanics without requiring any model retraining or developer API calls!

---

## Dependencies

### ZeusGateway

| Dependency | Scope | Required? |
|-----------|-------|-----------|
| Paper API / Spigot API | provided | Yes (one of them) |
| ProtocolLib | provided | **Optional** — degrades gracefully |
| ZeusProtocolJava | shaded | Yes (included in JAR) |

### ZeusFabric

| Dependency | Scope | Required? |
|-----------|-------|-----------|
| Fabric Loader | provided | Yes |
| Fabric API | provided | Yes |
| ZeusProtocolJava | included | Yes (bundled via `include`) |

---

## Project Structure

### ZeusGateway

```
ZeusGateway/src/main/java/org/vennv/zeusGateway/
├── ZeusGateway.java                        # Main plugin class
├── compat/                                 # Compatibility helpers used by modern adapter
│   ├── EntityCompat.java                   # Entity dimensions (BB → NMS fallback)
│   ├── BlockCompat.java                    # Block bounds, air, block data strings
│   ├── EffectCompat.java                   # Potion effect key mapping
│   └── nms/
│       └── NmsEntityAccess.java            # Legacy fallback code; not a published legacy artifact
├── init/
│   └── ZeusLoader.java                     # Initializes proxy, listeners, tasks
├── listener/
│   ├── event/
│   │   ├── EventListener.java              # Cross-platform Bukkit event handlers
│   │   └── PaperEventListener.java         # Paper-exclusive event handlers
│   └── packets/
│       ├── PacketBlockFaceListener.java     # BLOCK_DIG direction
│       ├── PacketClickWindowListener.java   # WINDOW_CLICK
│       ├── PacketDiggingBlockListener.java  # BLOCK_DIG
│       ├── PacketHeldItemListener.java      # HELD_ITEM_SLOT
│       ├── PacketKeepAliveListener.java     # KEEP_ALIVE
│       ├── PacketPlaceBlockListener.java    # USE_ITEM (block placement)
│       ├── PacketPlayerCommandListener.java # ENTITY_ACTION
│       ├── PacketPositionListener.java      # POSITION, POSITION_LOOK
│       ├── PacketSteerVehicleListener.java  # STEER_VEHICLE
│       ├── PacketSwingHandListener.java     # ARM_ANIMATION
│       ├── PacketUseItemListener.java       # USE_ITEM
│       └── PacketVehicleMoveListener.java   # VEHICLE_MOVE
├── network/
│   └── ProxyClient.java                    # UDP client to Zeus server
├── platform/
│   ├── BukkitSchedulerAdapter.java         # Standard Bukkit scheduler
│   ├── FoliaSchedulerAdapter.java          # Folia region-based scheduler
│   ├── PlatformDetector.java               # Runtime platform detection
│   ├── PlatformType.java                   # PAPER, SPIGOT, FOLIA enum
│   ├── SchedulerAdapter.java               # Scheduler abstraction interface
│   └── ServerVersion.java                  # ★ Version detection + feature flags
├── provider/
│   └── PacketQueue.java                    # Thread-safe packet queue
├── task/
│   ├── BatchSender.java                    # Daemon thread: drains queue → UDP
│   ├── ResyncTask.java                     # Periodic state re-synchronisation
│   └── UpdateTPS.java                      # TPS sampling task
└── utils/
    ├── BlockUtil.java                      # Ground detection, surrounding blocks
    ├── BoxUtil.java                        # AABB overlap checks (raw doubles)
    └── MathUtil.java                       # Math helpers
```

### ZeusFabric

```
ZeusFabric/src/main/java/org/vennv/zeusFabric/
├── ZeusFabricMod.java                      # Mod entry point (server-side)
├── listener/
│   └── ZeusEventListeners.java             # All Fabric event callbacks + tick polling
├── network/
│   └── ProxyClient.java                    # UDP client
├── provider/
│   └── PacketQueue.java                    # Thread-safe packet queue
└── task/
    └── BatchSender.java                    # Daemon thread: drains queue → UDP
```
