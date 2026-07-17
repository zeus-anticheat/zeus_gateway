# Graph Report - ZeusFabric  (2026-07-17)

## Corpus Check
- 38 files · ~17,077 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 570 nodes · 1357 edges · 24 communities (21 shown, 3 thin omitted)
- Extraction: 81% EXTRACTED · 19% INFERRED · 0% AMBIGUOUS · INFERRED: 262 edges (avg confidence: 0.8)
- Token cost: 0 input · 0 output

## Graph Freshness
- Built from commit: `6eed20f4`
- Run `git rev-parse HEAD` and compare to check if the graph is stale.
- Run `graphify update .` after code changes (no API cost).

## Community Hubs (Navigation)
- [[_COMMUNITY_Community 0|Community 0]]
- [[_COMMUNITY_Community 1|Community 1]]
- [[_COMMUNITY_Community 2|Community 2]]
- [[_COMMUNITY_Community 3|Community 3]]
- [[_COMMUNITY_Community 4|Community 4]]
- [[_COMMUNITY_Community 5|Community 5]]
- [[_COMMUNITY_Community 6|Community 6]]
- [[_COMMUNITY_Community 7|Community 7]]
- [[_COMMUNITY_Community 8|Community 8]]
- [[_COMMUNITY_Community 9|Community 9]]
- [[_COMMUNITY_Community 10|Community 10]]
- [[_COMMUNITY_Community 11|Community 11]]
- [[_COMMUNITY_Community 12|Community 12]]
- [[_COMMUNITY_Community 13|Community 13]]
- [[_COMMUNITY_Community 14|Community 14]]
- [[_COMMUNITY_Community 15|Community 15]]
- [[_COMMUNITY_Community 16|Community 16]]
- [[_COMMUNITY_Community 17|Community 17]]
- [[_COMMUNITY_Community 18|Community 18]]
- [[_COMMUNITY_Community 19|Community 19]]
- [[_COMMUNITY_Community 20|Community 20]]
- [[_COMMUNITY_Community 21|Community 21]]
- [[_COMMUNITY_Community 23|Community 23]]

## God Nodes (most connected - your core abstractions)
1. `PlayerStateSnapshotService` - 41 edges
2. `ZeusEventListeners` - 34 edges
3. `ServerPlayerEntity` - 32 edges
4. `ServerPlayNetworkHandlerMixin` - 19 edges
5. `PacketQueue` - 18 edges
6. `String` - 18 edges
7. `PacketQueueTest` - 15 edges
8. `ChunkSnapshotSemantics` - 14 edges
9. `MinecraftCompat` - 13 edges
10. `ChunkSnapshotSemanticsTest` - 13 edges

## Surprising Connections (you probably didn't know these)
- `BatchSender` --implements--> `Runnable`  [EXTRACTED]
  src/main/java/org/vennv/zeusFabric/task/BatchSender.java → src/main/java/org/vennv/zeusFabric/provider/PacketQueue.java

## Communities (24 total, 3 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.08
Nodes (21): CaptureFrameV3, ClientCommandC2SPacket, ServerPlayNetworkHandlerMixin, Optional, PlayerInputC2SPacket, PlayerMoveC2SPacket, EmissionGate, inclusionFlags() (+13 more)

### Community 1 - "Community 1"
Cohesion: 0.17
Nodes (12): Batch, PacketQueue, PacketQueueTest, QueuedPacket, Collection, List, PacketCollisionWindow, PacketEncode (+4 more)

### Community 2 - "Community 2"
Cohesion: 0.10
Nodes (15): Accessor, DamageCause, DamageSource, Direction, Effect, EntityState, ExternalForceType, ZeusEventListeners (+7 more)

### Community 3 - "Community 3"
Cohesion: 0.09
Nodes (15): Armors, Enchantment, PacketServerConfig, ServerBoundPlayerCommandActions, Cell, Center, Integer, ItemStack (+7 more)

### Community 4 - "Community 4"
Cohesion: 0.23
Nodes (13): Collection, FabricLoaderSelectionTest, DependencyOverrides, InputStream, JarEntry, JarFile, LoaderModMetadata, ModCandidateImpl (+5 more)

### Community 5 - "Community 5"
Cohesion: 0.19
Nodes (4): CaptureIdentity, CaptureIdentityTest, String, String

### Community 6 - "Community 6"
Cohesion: 0.15
Nodes (17): BlockPos, Box, EntityType, ServerCommonNetworkHandlerMixin, Packet, RelativeBlock, ServerPlayNetworkHandler, CallbackInfo (+9 more)

### Community 7 - "Community 7"
Cohesion: 0.13
Nodes (11): ClickSlotC2SPacket, Entity, EntityVelocityUpdateS2CPacket, Integer, ItemStack, List, ServerPlayerEntity, String (+3 more)

### Community 8 - "Community 8"
Cohesion: 0.13
Nodes (11): ClickSlotC2SPacket, Entity, EntityVelocityUpdateS2CPacket, Integer, ItemStack, List, ServerPlayerEntity, String (+3 more)

### Community 9 - "Community 9"
Cohesion: 0.13
Nodes (11): ClickSlotC2SPacket, Entity, EntityVelocityUpdateS2CPacket, Integer, ItemStack, List, ServerPlayerEntity, String (+3 more)

### Community 10 - "Community 10"
Cohesion: 0.13
Nodes (11): ClickSlotC2SPacket, Entity, EntityVelocityUpdateS2CPacket, Integer, ItemStack, List, ServerPlayerEntity, String (+3 more)

### Community 11 - "Community 11"
Cohesion: 0.13
Nodes (11): ClickSlotC2SPacket, Entity, EntityVelocityUpdateS2CPacket, Integer, ItemStack, List, ServerPlayerEntity, String (+3 more)

### Community 12 - "Community 12"
Cohesion: 0.10
Nodes (19): authors, contact, depends, fabric-api, fabricloader, java, minecraft, description (+11 more)

### Community 13 - "Community 13"
Cohesion: 0.38
Nodes (5): Armor, Item, ItemStack, String, ItemUtil

### Community 14 - "Community 14"
Cohesion: 0.18
Nodes (10): PacketEncode, EmptyPacket, ByteArrayOutputStream, List, Override, PacketCollisionWindow, ByteArrayOutputStream, Override (+2 more)

### Community 15 - "Community 15"
Cohesion: 0.27
Nodes (3): PollingPolicy, PollingPolicyTest, String

### Community 16 - "Community 16"
Cohesion: 0.11
Nodes (18): Map, BlockData, Cell, Center, Fragment, Integer, List, BlockData (+10 more)

### Community 17 - "Community 17"
Cohesion: 0.25
Nodes (7): client, compatibilityLevel, injectors, defaultRequire, mixins, package, required

### Community 18 - "Community 18"
Cohesion: 0.13
Nodes (11): ProxyClient, Predicate, Runnable, PacketEncode, String, Runnable, PacketEncode, ProxyClient (+3 more)

### Community 19 - "Community 19"
Cohesion: 0.16
Nodes (7): DedicatedServerModInitializer, MinecraftServer, MinecraftServer, Override, ProxyClient, ResyncTask, ZeusFabricMod

## Knowledge Gaps
- **54 isolated node(s):** `test`, `Override`, `DamageCause`, `String`, `Batch` (+49 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **3 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `PlayerStateSnapshotService` connect `Community 3` to `Community 16`?**
  _High betweenness centrality (0.045) - this node is a cross-community bridge._
- **Why does `ZeusEventListeners` connect `Community 2` to `Community 3`, `Community 5`?**
  _High betweenness centrality (0.038) - this node is a cross-community bridge._
- **What connects `test`, `Override`, `DamageCause` to the rest of the system?**
  _54 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.07922077922077922 - nodes in this community are weakly interconnected._
- **Should `Community 2` be split into smaller, more focused modules?**
  _Cohesion score 0.09663120567375887 - nodes in this community are weakly interconnected._
- **Should `Community 3` be split into smaller, more focused modules?**
  _Cohesion score 0.09126984126984126 - nodes in this community are weakly interconnected._
- **Should `Community 6` be split into smaller, more focused modules?**
  _Cohesion score 0.1455026455026455 - nodes in this community are weakly interconnected._