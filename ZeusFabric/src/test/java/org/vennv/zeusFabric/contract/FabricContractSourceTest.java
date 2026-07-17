package org.vennv.zeusFabric.contract;

import org.vennv.PacketId;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FabricContractSourceTest {
    public static void main(String[] args) throws IOException {
        require(Byte.toUnsignedInt(PacketId.PACKET_PLAYER_VELOCITY) == 0x22, "velocity ID changed");
        require(Byte.toUnsignedInt(PacketId.PACKET_SERVER_CONFIG) == 0x25, "server config ID changed");
        require(Byte.toUnsignedInt(PacketId.PACKET_ENTITY_SPAWN) == 0x28, "entity spawn ID changed");
        require(Byte.toUnsignedInt(PacketId.PACKET_ENTITY_MOVE) == 0x29, "entity move ID changed");
        require(Byte.toUnsignedInt(PacketId.PACKET_ENTITY_DESTROY) == 0x2A, "entity destroy ID changed");
        require(Byte.toUnsignedInt(PacketId.PACKET_BLOCK_CHANGE_EVENT) == 0x2B, "block change ID changed");
        require(Byte.toUnsignedInt(PacketId.PACKET_PLAYER_INPUT) == 0x2C, "input ID changed");
        require(Byte.toUnsignedInt(PacketId.PACKET_CHUNK_DATA) == 0x2D, "chunk ID changed");
        require(Byte.toUnsignedInt(PacketId.PACKET_UPDATE_ATTRIBUTES) == 0x2E, "attributes ID changed");
        require(Byte.toUnsignedInt(PacketId.PACKET_PHYSICS_CAPTURE_SAMPLE) == 0x2F, "capture ID changed");
        require(Byte.toUnsignedInt(PacketId.PACKET_COLLISION_WINDOW) == 0x30, "collision window ID changed");
        Path source = Path.of("src/main/java/org/vennv/zeusFabric");
        String snapshot = Files.readString(source.resolve("task/PlayerStateSnapshotService.java"));
        String semantics = Files.readString(source.resolve("task/ChunkSnapshotSemantics.java"));
        String commonMixin = Files.readString(source.resolve("mixins/ServerCommonNetworkHandlerMixin.java"));
        String playMixin = Files.readString(source.resolve("mixins/ServerPlayNetworkHandlerMixin.java"));
        String listeners = Files.readString(source.resolve("listener/ZeusEventListeners.java"));
        String teleportDedupe = Files.readString(source.resolve("listener/AuthoritativeTeleportDedupe.java"));
        String resync = Files.readString(source.resolve("task/ResyncTask.java"));
        String mod = Files.readString(source.resolve("ZeusFabricMod.java"));
        String captureIdentity = Files.readString(source.resolve("provider/CaptureIdentity.java"));

        require(snapshot.contains("new PacketServerConfig("), "0x25 producer missing");
        require(commonMixin.contains("packet instanceof BundleS2CPacket"), "bundled tracking spawn handling missing");
        require(commonMixin.contains("packet instanceof EntitySpawnS2CPacket"), "0x28 observing-recipient producer missing");
        require(commonMixin.contains("packet instanceof EntityS2CPacket"), "0x29 observing-recipient producer missing");
        require(commonMixin.contains("packet instanceof MoveMinecartAlongTrackS2CPacket"), "minecart movement producer missing");
        require(commonMixin.contains("packet instanceof EntitiesDestroyS2CPacket"), "0x2A observing-recipient producer missing");
        require(commonMixin.contains("new PacketBlockChangeEvent("), "0x2B authoritative producer missing");
        require(!listeners.contains("new PacketBlockChangeEvent("), "listener 0x2B producer restored");
        require(playMixin.contains("new PacketPlayerInput("), "0x2C raw producer missing");
        require(snapshot.contains("CollisionWindowUpdate.full("), "0x30 full snapshot producer missing");
        require(snapshot.contains("CollisionWindowUpdate.delta("), "0x30 delta producer missing");
        require(snapshot.contains("PacketQueue.pushCollisionWindow("), "atomic collision queue producer missing");
        require(snapshot.contains("PacketQueue.removeCollisionWindows("), "collision invalidation purge missing");
        require(snapshot.contains("world.isChunkLoaded("), "unloaded collision semantics missing");
        require(snapshot.contains("Cell.knownAir()"), "known-air collision semantics missing");
        require(snapshot.contains("Cell.knownBlock(state.toString())"), "exact block-state semantics missing");
        require(snapshot.contains("public static void onMovement("), "movement collision producer missing");
        require(snapshot.contains("center.equals(previous.center())"), "integer-center movement gate missing");
        require(snapshot.contains("public static void invalidate("), "collision invalidation API missing");
        require(snapshot.contains("public static void remove("), "collision remove API missing");
        require(snapshot.contains("public static void clearAll("), "collision clear-all API missing");
        require(snapshot.contains("public static boolean contains("), "collision containment API missing");
        assertOrder(section(playMixin,
                "private void zeus$emitAcceptedMovement",
                "private CaptureFrameV3 zeus$captureFrame"),
                "PacketQueue.pushAll(List.of(position, zeus$captureFrame(packet)))",
                "PacketQueue.push(position)",
                "if (context.hasPosition())",
                "PlayerStateSnapshotService.onMovement(player, x, y, z)");
        assertOrder(section(listeners,
                "private static void registerJoinLeave",
                "public static boolean isCaptureActive"),
                "PlayerStateSnapshotService.sendFullSnapshot(player)",
                "PacketQueue.push(new PacketPlayerLeave(timestamp, uid, name))",
                "PlayerStateSnapshotService.remove(uid)",
                "PlayerStateSnapshotService.clear(uid)",
                "clearTracking(uid)");
        assertOrder(section(listeners,
                "private static void registerWorldChange",
                "private static void registerAttackEntity"),
                "new PacketPlayerTeleport(",
                "PlayerStateSnapshotService.invalidate(player)",
                "PlayerStateSnapshotService.sendResyncSnapshot(player)");
        assertOrder(section(listeners,
                "private static void registerRespawn",
                "private static void registerTickListeners"),
                "new PacketPlayerRespawn(",
                "PlayerStateSnapshotService.invalidate(newPlayer)",
                "PlayerStateSnapshotService.sendResyncSnapshot(newPlayer)");
        String resyncSnapshot = section(snapshot,
                "public static void sendResyncSnapshot",
                "public static void sendMutableStateSnapshot");
        assertOrder(resyncSnapshot,
                "clearMutableState(player.getUuidAsString())",
                "sendSnapshot(player, true, PacketPlayerPosition.SOURCE_RESYNC)");
        require(!resyncSnapshot.contains("invalidate("), "periodic resync must retain current generation");
        require(resync.contains("PlayerStateSnapshotService.sendResyncSnapshot(player)"),
                "periodic forced full resync missing");
        require(!resync.contains("PlayerStateSnapshotService.invalidate("),
                "periodic resync invalidates current generation");
        require(section(mod, "private void onServerStarted", "private void onServerStopping")
                .contains("PlayerStateSnapshotService.sendFullSnapshot(player)"),
                "late server-start full snapshot missing");
        assertOrder(section(mod, "private void onEndServerTick", "private void loadConfig"),
                "PacketQueue.recoverFromDiscontinuity",
                "PlayerStateSnapshotService.invalidate(player)",
                "PlayerStateSnapshotService.sendResyncSnapshot(player)");
        require(section(mod, "private void onServerStopping", "private void onEndServerTick")
                .contains("PlayerStateSnapshotService.clearAll()"),
                "server-stop collision state cleanup missing");
        require(commonMixin.contains("packet instanceof PlayerPositionLookS2CPacket"),
                "outbound authoritative teleport hook missing");
        require(commonMixin.contains("ZeusEventListeners.authoritativeTeleport("),
                "outbound teleport did not use centralized handler");
        require(section(listeners, "private static void registerWorldChange", "private static void registerAttackEntity")
                .contains("authoritativeTeleport(player, AuthoritativeTeleportDedupe.Source.WORLD_CHANGE"),
                "world-change teleport did not use centralized handler");
        assertOrder(section(listeners, "private static void authoritativeTeleport", "private static void registerAttackEntity"),
                "new PacketPlayerTeleport(",
                "PlayerStateSnapshotService.invalidate(player)",
                "PlayerStateSnapshotService.sendResyncSnapshot(player)");
        require(teleportDedupe.contains("previous.lifecycleKey() == lifecycleKey"),
                "stable teleport lifecycle dedupe missing");
        require(commonMixin.contains("state.isAir() ? \"minecraft:air\" : state.toString()"),
                "air block-change normalization missing");
        assertOrder(section(commonMixin,
                "private static void zeus$blockChange",
                "\n    }\n}"),
                "PlayerStateSnapshotService.contains(handler.player, pos.getX(), pos.getY(), pos.getZ())",
                "new PacketBlockChangeEvent(");
        require(!playMixin.contains("new PacketChunkData("), "movement legacy chunk producer restored");
        require(!commonMixin.contains("new PacketChunkData("), "block-change legacy chunk producer restored");
        require(!listeners.contains("new PacketChunkData("), "lifecycle legacy chunk producer restored");
        require(!resync.contains("new PacketChunkData("), "resync legacy chunk producer restored");
        require(!mod.contains("new PacketChunkData("), "mod lifecycle legacy chunk producer restored");
        int snapshotPosition = snapshot.indexOf("PacketQueue.push(new PacketPlayerPosition(");
        int snapshotCollision = snapshot.indexOf(
                "sendCollisionWindow(player, ChunkSnapshotSemantics.Center.floor(pos.x", snapshotPosition);
        require(snapshotPosition >= 0 && snapshotCollision > snapshotPosition,
                "position snapshot must enqueue before collision update");
        require(!snapshot.contains("PacketChunkData"), "legacy chunk snapshot producer restored");
        require(!snapshot.contains("SENT_CHUNKS"), "unbounded sent-chunk cache restored");
        require(!snapshot.contains("CHUNK_RADIUS"), "chunk-radius scan restored");
        require(!snapshot.contains("sendChunkData"), "legacy chunk snapshot method restored");
        require(!semantics.contains("PacketChunkData"), "pure collision semantics depend on legacy chunk packets");
        require(!semantics.contains("SENT_CHUNKS"), "pure collision semantics contain sent-chunk state");
        require(!semantics.contains("CHUNK_RADIUS"), "pure collision semantics contain chunk-radius scan");
        require(snapshot.contains("new PacketUpdateAttributes("), "0x2E snapshot producer missing");
        require(commonMixin.contains("new PacketUpdateAttributes("), "0x2E authoritative producer missing");
        require(playMixin.contains("zeus$captureFrame(packet)"), "0x2F movement producer missing");
        require(commonMixin.contains("packet instanceof EntityVelocityUpdateS2CPacket"), "0x22 authoritative event gate missing");
        require(commonMixin.contains("velocityPacket.getEntityId() == handler.player.getId()"), "0x22 player gate missing");
        require(!commonMixin.contains("handler.player.getVelocity()"), "0x22 state fallback restored");
        require(playMixin.contains("MovementSemantics.rawPacketInputFlags(flags)"), "raw input trust gate missing");
        require(playMixin.contains("frame.movementSequence = context.sequence()"), "0x2F sequence reuse missing");
        require(playMixin.contains("frame.inclusionFlags = context.inclusionFlags()"), "0x2F inclusion reuse missing");
        require(playMixin.contains("getAttributeBaseValue(EntityAttributes.MOVEMENT_SPEED)"), "capture effective speed restored");
        require(snapshot.contains("getAttributeBaseValue(EntityAttributes.MOVEMENT_SPEED)"), "server config effective speed restored");
        require(!snapshot.contains("getAttributeValue(EntityAttributes.MOVEMENT_SPEED)"), "effective movement speed producer restored");
        require(playMixin.contains("CaptureIdentity.captureSubjectId(player.getUuidAsString())"), "session-salted subject missing");
        require(playMixin.contains("CaptureIdentity.playerHash(player.getUuidAsString())"), "process-salted hash missing");
        require(captureIdentity.contains("new SecureRandom().nextBytes(PROCESS_SALT)"), "process salt not random");
        require(!captureIdentity.contains("zeus-capture-subject-v1"), "public capture salt restored");
        require(listeners.contains("if (!CaptureIdentity.hasSharedSalt())"), "capture does not fail closed");
        require(listeners.contains("PollingPolicy.shouldSendKeepAlive(player.age)"), "per-player keepalive clock missing");
        require(!listeners.contains("keepAliveCounter"), "global keepalive counter restored");
        require(listeners.contains("CONTROL_GENERATION.incrementAndGet()"), "poller generation invalidation missing");
        require(listeners.contains("PollingPolicy.isCurrentGeneration(generation, CONTROL_GENERATION.get())"), "stale poller publication gate missing");
        require(listeners.contains("connection.disconnect()"), "poller connection cleanup missing");
        require(listeners.contains("poller.join(3500L)"), "poller stop join missing");
        require(!listeners.contains("tickPhysicsCapture("), "physics polling restored");
        require(!listeners.contains("tickVelocity("), "velocity polling restored");
        require(!listeners.contains("tickBlockRayTrace("), "ray scan polling restored");
        require(!listeners.contains("tickEnchantments("), "enchantment polling restored");
        require(!listeners.contains("tickPlayerCommands("), "command polling restored");
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        require(start >= 0, startMarker);
        require(end > start, endMarker);
        return source.substring(start, end);
    }

    private static void assertOrder(String source, String... markers) {
        int cursor = 0;
        for (String marker : markers) {
            int index = source.indexOf(marker, cursor);
            require(index >= cursor, marker);
            cursor = index + marker.length();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
