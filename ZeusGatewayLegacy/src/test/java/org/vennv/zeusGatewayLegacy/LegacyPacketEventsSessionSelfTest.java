package org.vennv.zeusGatewayLegacy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.vennv.EntityState;
import org.vennv.PacketEncode;
import org.vennv.PacketId;
import org.vennv.packets.PacketBlockChangeEvent;
import org.vennv.packets.PacketChunkData;
import org.vennv.packets.PacketPlayerAttackEntity;
import org.vennv.packets.PacketPlayerBlockRayTrace;
import org.vennv.packets.PacketPlayerClickWindow;
import org.vennv.packets.PacketPlayerDeath;
import org.vennv.packets.PacketPlayerHeldItem;
import org.vennv.packets.PacketPlayerInventoryTransaction;
import org.vennv.packets.PacketPlayerJoin;
import org.vennv.packets.PacketPlayerRespawn;
import org.vennv.packets.PacketPlayerTeleport;
import org.vennv.packets.PacketShulkerBoxAction;
import org.vennv.packets.PacketServerConfig;
import org.vennv.utils.ItemStack;
import org.vennv.utils.ServerBoundPlayerCommandActions;

final class LegacyPacketEventsSessionSelfTest {
    private static final String UID = "00000000-0000-0000-0000-000000000000";
    private static final String NAME = "player";

    @AfterEach
    void clearQueue() {
        LegacyPacketQueue.setOverflowHandler(null);
        LegacyPacketQueue.clear();
    }

    @Test
    void emptyFullChunkEmitsReset() {
        List<LegacyPacketEventsSession.ChunkBatch> batches =
                LegacyPacketEventsSession.partitionChunkBlocks(
                        UID, NAME, true, Collections.<PacketChunkData.BlockData>emptyList());
        assertEquals(1, batches.size());
        assertTrue(batches.get(0).reset);
        assertTrue(batches.get(0).blocks.isEmpty());
    }

    @Test
    void emptyPartialChunkEmitsNothing() {
        List<LegacyPacketEventsSession.ChunkBatch> batches =
                LegacyPacketEventsSession.partitionChunkBlocks(
                        UID, NAME, false, Collections.<PacketChunkData.BlockData>emptyList());
        assertTrue(batches.isEmpty());
    }

    @Test
    void oversizedBatchSplitsExactly() {
        List<PacketChunkData.BlockData> blocks = new ArrayList<PacketChunkData.BlockData>();
        StringBuilder state = new StringBuilder("minecraft:test[");
        for (int i = 0; i < 1000; i++) state.append('x');
        state.append(']');
        for (int i = 0; i < 200; i++) {
            blocks.add(new PacketChunkData.BlockData(
                    (byte) (i & 15), i, (byte) ((i >> 4) & 15), state.toString() + i));
        }

        List<LegacyPacketEventsSession.ChunkBatch> batches =
                LegacyPacketEventsSession.partitionChunkBlocks(UID, NAME, true, blocks);
        assertTrue(batches.size() > 1);
        int offset = 0;
        for (int batchIndex = 0; batchIndex < batches.size(); batchIndex++) {
            LegacyPacketEventsSession.ChunkBatch batch = batches.get(batchIndex);
            assertEquals(batchIndex == 0, batch.reset);
            int encodedSize = PacketChunkData.encodedBaseSize(UID, NAME);
            for (PacketChunkData.BlockData block : batch.blocks) {
                assertSame(blocks.get(offset++), block);
                encodedSize += PacketChunkData.encodedBlockSize(block.blockType);
            }
            assertTrue(encodedSize <= PacketChunkData.MAX_UDP_PAYLOAD);
        }
        assertEquals(blocks.size(), offset);
    }

    @Test
    void worldPacketsUseBoundedRecovery() {
        assertEquals(16, LegacyPacketEventsSession.MAX_WORLD_PENDING);
        assertEquals(4096, LegacyPacketEventsSession.MAX_PENDING);
        assertFalse(LegacyPacketEventsSession.canRecoverWorld(4L, 4L, true));
        assertFalse(LegacyPacketEventsSession.canRecoverWorld(4L, 5L, false));
        assertTrue(LegacyPacketEventsSession.canRecoverWorld(4L, 5L, true));
    }

    @Test
    void worldSaturationPausesSimulationButStillEmitsAttackClickAndTransactionInOrder() throws Exception {
        UUID owner = UUID.fromString(UID);
        LegacyPacketEventsSession.PendingTasks tasks =
                new LegacyPacketEventsSession.PendingTasks(LegacyPacketEventsSession.MAX_PENDING);
        List<PacketEncode> emitted = new ArrayList<PacketEncode>();
        PacketEncode movement = LegacyGatewaySession.joinPacket(1L, UID, NAME, 47);
        PacketEncode attack = new PacketPlayerAttackEntity(
                2L, UID, NAME, new EntityState(
                        "target", 0.0, 64.0, 0.0, 0.0, 65.62, 0.0,
                        0.0f, 0.0f, 1.8f, 0.6f, true));
        ItemStack empty = new ItemStack(ItemStack.EMPTY_ID, 0, (byte) 0);
        PacketEncode click = new PacketPlayerClickWindow(
                3L, UID, NAME, (byte) 1, (short) 2, (byte) 0, (short) 0, empty, (short) 4);
        PacketEncode transaction = LegacyGatewaySession.inventoryTransaction(
                4L, UID, NAME, (byte) 1, (short) 2, (byte) 0, (short) 0,
                (short) 4, empty, Collections.<PacketPlayerInventoryTransaction.ChangedSlot>emptyList());

        tasks.block(owner);
        tasks.fail(owner);
        assertFalse(tasks.offer(owner, new EmitTask(emitted, movement), true));
        assertTrue(tasks.offer(owner, new EmitTask(emitted, attack), false));
        assertTrue(tasks.offer(owner, new EmitTask(emitted, click), false));
        assertTrue(tasks.offer(owner, new EmitTask(emitted, transaction), false));
        while (tasks.hasRunnable()) tasks.poll().task.run();

        assertEquals(Arrays.asList(
                PacketId.PACKET_PLAYER_ATTACK_ENTITY,
                PacketId.PACKET_PLAYER_CLICK_WINDOW,
                PacketId.PACKET_PLAYER_INVENTORY_TRANSACTION), packetIds(emitted));
        tasks.unblock(owner);
        tasks.recover(owner);
        assertTrue(tasks.offer(owner, new EmitTask(emitted, movement), true));
        tasks.poll().task.run();
        assertSame(movement, emitted.get(3));
    }

    @Test
    void movementDiscontinuityLeavesSequenceGapForCaptureAlignment() {
        AtomicLong sequence = new AtomicLong();
        assertEquals(1L, LegacyPacketEventsSession.nextMovementSequence(sequence, false));
        assertEquals(3L, LegacyPacketEventsSession.nextMovementSequence(sequence, true));
        assertEquals(4L, LegacyPacketEventsSession.nextMovementSequence(sequence, false));
    }

    @Test
    void criticalPacketsExposeRequiredWireIdsAndProtocol() {
        PacketPlayerJoin join = LegacyGatewaySession.joinPacket(1L, UID, NAME, 404);
        PacketServerConfig config = new PacketServerConfig(
                1L, UID, NAME, 3.0f, 10.0f, (byte) 0, 0.1f);
        PacketChunkData chunk = new PacketChunkData(
                1L, UID, NAME, 0, 0, true,
                Collections.<PacketChunkData.BlockData>emptyList());
        PacketBlockChangeEvent block = new PacketBlockChangeEvent(
                1L, UID, NAME, 0, 64, 0, "minecraft:stone", (byte) 0);
        PacketShulkerBoxAction shulker = new PacketShulkerBoxAction(
                1L, UID, NAME, 0, 64, 0, (byte) 1, (byte) 1);

        assertEquals(PacketId.PACKET_PLAYER_JOIN, join.packetId());
        assertEquals(404, join.getProtocolVersion());
        assertEquals(PacketId.PACKET_SERVER_CONFIG, config.packetId());
        assertEquals(PacketId.PACKET_CHUNK_DATA, chunk.packetId());
        assertEquals(PacketId.PACKET_BLOCK_CHANGE_EVENT, block.packetId());
        assertEquals(PacketId.PACKET_SHULKER_BOX_ACTION, shulker.packetId());
    }

    @Test
    void shulkerBlockActionRequiresVanillaAnimationAndShulkerBlock() {
        assertTrue(LegacyPacketEventsSession.isVanillaShulkerAction(
                "minecraft:shulker_box", 1));
        assertTrue(LegacyPacketEventsSession.isVanillaShulkerAction(
                "minecraft:red_shulker_box", 1));
        assertFalse(LegacyPacketEventsSession.isVanillaShulkerAction(
                "minecraft:chest", 1));
        assertFalse(LegacyPacketEventsSession.isVanillaShulkerAction(
                "other:shulker_box", 1));
        assertFalse(LegacyPacketEventsSession.isVanillaShulkerAction(
                "minecraft:modded_shulker_box", 1));
        assertFalse(LegacyPacketEventsSession.isVanillaShulkerAction(
                "minecraft:shulker_box", 2));
    }

    @Test
    void inventoryMutationPacketCarriesLegacyClickIdentity() {
        ItemStack cursor = new ItemStack("minecraft:stone", 0, (byte) 1);
        List<PacketPlayerInventoryTransaction.ChangedSlot> changed =
                Collections.singletonList(new PacketPlayerInventoryTransaction.ChangedSlot(
                        (short) 7, cursor));
        PacketPlayerInventoryTransaction transaction = LegacyGatewaySession.inventoryTransaction(
                2L, UID, NAME, (byte) 3, (short) 7, (byte) 1, (short) 2,
                (short) 19, cursor, changed);

        assertEquals(PacketId.PACKET_PLAYER_INVENTORY_TRANSACTION, transaction.packetId());
        assertEquals(3, transaction.getWindowId());
        assertEquals(-1, transaction.getStateId());
        assertEquals(7, transaction.getClickedSlot());
        assertEquals(1, transaction.getButton());
        assertEquals(2, transaction.getMode());
        assertEquals(19, transaction.getTransactionId());
        assertEquals(1, transaction.getChangedSlots().size());
    }

    @Test
    void heldItemProtocolMappingHandlesEmptyAndNamedStacks() {
        org.vennv.utils.Item empty = LegacyGatewaySession.protocolItem(null);
        assertEquals("", empty.getName());
        assertEquals("", empty.getCustomName());
        assertTrue(empty.getItemStack().isEmpty());

        PacketPlayerHeldItem packet = new PacketPlayerHeldItem(
                1L, UID, NAME, empty);
        assertEquals(PacketId.PACKET_PLAYER_HELD_ITEM, packet.packetId());
    }

    @Test
    void legacyDiggingActionsKeepExactPhase() {
        assertTrue(LegacyPacketEventsSession.isBlockDigAction(
                com.github.retrooper.packetevents.protocol.player.DiggingAction.START_DIGGING));
        assertEquals(PacketPlayerBlockRayTrace.DIG_PHASE_START,
                LegacyPacketEventsSession.digPhase(
                        com.github.retrooper.packetevents.protocol.player.DiggingAction.START_DIGGING));
        assertEquals(PacketPlayerBlockRayTrace.DIG_PHASE_FINISH,
                LegacyPacketEventsSession.digPhase(
                        com.github.retrooper.packetevents.protocol.player.DiggingAction.FINISHED_DIGGING));
        assertEquals(PacketPlayerBlockRayTrace.DIG_PHASE_CANCEL,
                LegacyPacketEventsSession.digPhase(
                        com.github.retrooper.packetevents.protocol.player.DiggingAction.CANCELLED_DIGGING));
    }

    @Test
    void legacyServerVersionsMapToExactProtocols() {
        assertEquals(47, LegacyServerIdentity.serverProtocol("1.8.8"));
        assertEquals(107, LegacyServerIdentity.serverProtocol("1.9"));
        assertEquals(340, LegacyServerIdentity.serverProtocol("1.12.2"));
        assertEquals(393, LegacyServerIdentity.serverProtocol("1.13"));
        assertEquals(404, LegacyServerIdentity.serverProtocol("1.13.2"));
        assertEquals(0.0f, LegacyServerIdentity.cooldownTicks(47, -1.0f));
        assertEquals(10.0f, LegacyServerIdentity.cooldownTicks(404, -1.0f));
        assertEquals(6.0f, LegacyServerIdentity.cooldownTicks(47, 6.0f));
    }

    @Test
    void outputQueueIsBoundedAndIdentifiesOverflowOwner() {
        UUID owner = UUID.fromString(UID);
        final List<UUID> affected = new ArrayList<UUID>();
        final List<Boolean> global = new ArrayList<Boolean>();
        LegacyPacketQueue.setOverflowHandler(new LegacyPacketQueue.OverflowHandler() {
            @Override
            public void onOverflow(UUID uuid, boolean allPlayers) {
                affected.add(uuid);
                global.add(allPlayers);
            }
        });
        PacketEncode packet = LegacyGatewaySession.joinPacket(1L, UID, NAME, 47);
        for (int i = 0; i < LegacyPacketQueue.CAPACITY; i++) {
            assertTrue(LegacyPacketQueue.push(owner, packet));
        }
        assertFalse(LegacyPacketQueue.push(owner, packet));
        assertEquals(0, LegacyPacketQueue.size());
        assertEquals(Collections.singletonList(owner), affected);
        assertEquals(Collections.singletonList(Boolean.FALSE), global);
    }

    @Test
    void unownedOverflowRequiresGlobalRecovery() {
        final List<Boolean> global = new ArrayList<Boolean>();
        LegacyPacketQueue.setOverflowHandler(new LegacyPacketQueue.OverflowHandler() {
            @Override
            public void onOverflow(UUID uuid, boolean allPlayers) {
                global.add(allPlayers);
            }
        });
        PacketEncode packet = LegacyGatewaySession.joinPacket(1L, UID, NAME, 47);
        for (int i = 0; i < LegacyPacketQueue.CAPACITY; i++) {
            assertTrue(LegacyPacketQueue.push(UUID.fromString(UID), packet));
        }
        assertFalse(LegacyPacketQueue.push(null, packet));
        assertEquals(Collections.singletonList(Boolean.TRUE), global);
    }

    @Test
    void periodicPacketsRemainFifoWithoutCoalescing() {
        UUID owner = UUID.fromString(UID);
        PacketEncode oldPacket = LegacyGatewaySession.joinPacket(1L, UID, NAME, 47);
        PacketEncode newPacket = LegacyGatewaySession.joinPacket(2L, UID, NAME, 404);
        assertTrue(LegacyPacketQueue.pushCoalescing(owner, "resync", oldPacket));
        assertTrue(LegacyPacketQueue.pushCoalescing(owner, "resync", newPacket));
        assertEquals(2, LegacyPacketQueue.size());
        assertSame(oldPacket, LegacyPacketQueue.poll());
        assertSame(newPacket, LegacyPacketQueue.poll());
    }

    @Test
    void lifecyclePacketsCoverDeathRespawnTeleportAndWorldChangeWireIds() {
        PacketPlayerDeath death = LegacyGatewaySession.deathPacket(1L, UID, NAME);
        PacketPlayerRespawn respawn = LegacyGatewaySession.respawnPacket(2L, UID, NAME);
        PacketPlayerTeleport teleport = LegacyGatewaySession.teleportPacket(
                3L, UID, NAME, 10.5, 64.0, -2.5);
        assertEquals(PacketId.PACKET_PLAYER_DEATH, death.packetId());
        assertEquals(PacketId.PACKET_PLAYER_RESPAWN, respawn.packetId());
        assertEquals(PacketId.PACKET_PLAYER_TELEPORT, teleport.packetId());
        assertEquals(10.5, teleport.getX());
        assertEquals(64.0, teleport.getY());
        assertEquals(-2.5, teleport.getZ());
        assertFalse(LegacyPacketEventsSession.canRecoverWorld(7L, 8L, false));
        assertTrue(LegacyPacketEventsSession.canRecoverWorld(7L, 8L, true));
    }

    @Test
    void inventoryDiffReportsOnlyActualChangesAndCursorComparisonIsValueBased() {
        ItemStack empty = new ItemStack(ItemStack.EMPTY_ID, 0, (byte) 0);
        ItemStack stone = new ItemStack("minecraft:stone", 0, (byte) 1);
        ItemStack dirt = new ItemStack("minecraft:dirt", 0, (byte) 2);
        List<PacketPlayerInventoryTransaction.ChangedSlot> changed =
                LegacyGatewaySession.changedSlots(
                        Arrays.asList(empty, stone, dirt),
                        Arrays.asList(empty, stone, new ItemStack("minecraft:dirt", 0, (byte) 1)));
        assertEquals(1, changed.size());
        assertEquals(2, changed.get(0).slot());
        assertEquals(1, changed.get(0).itemStack().getCount());
        assertTrue(LegacyGatewaySession.sameItem(
                stone, new ItemStack("minecraft:stone", 0, (byte) 1)));
        assertFalse(LegacyGatewaySession.sameItem(stone, dirt));
    }

    @Test
    void missingActionNumberFallsBackWithoutDroppingClick() {
        assertEquals(37, LegacyPacketEventsSession.transactionId(Integer.valueOf(37)));
        assertEquals(0, LegacyPacketEventsSession.transactionId(null));
    }

    @Test
    void legacyEntityActionsPreserveSprintAndSneakState() {
        assertEquals(ServerBoundPlayerCommandActions.START_SPRINTING,
                LegacyPacketEventsSession.playerAction("START_SPRINTING"));
        assertEquals(ServerBoundPlayerCommandActions.STOP_SPRINTING,
                LegacyPacketEventsSession.playerAction("STOP_SPRINTING"));
        assertEquals(ServerBoundPlayerCommandActions.START_SNEAKING,
                LegacyPacketEventsSession.playerAction("START_SNEAKING"));
        assertEquals(ServerBoundPlayerCommandActions.STOP_SNEAKING,
                LegacyPacketEventsSession.playerAction("STOP_SNEAKING"));
        assertEquals(ServerBoundPlayerCommandActions.START_RIDING_JUMP,
                LegacyPacketEventsSession.playerAction("START_JUMPING_WITH_HORSE"));
        assertEquals(null, LegacyPacketEventsSession.playerAction("START_FLYING_WITH_ELYTRA"));
        assertEquals(null, LegacyPacketEventsSession.playerAction(null));
    }

    @Test
    void platformAndPhysicsIdentityAreTruthfulAndConfigurable() {
        assertEquals("paper", LegacyServerIdentity.platformForBrand("Paper", "auto"));
        assertEquals("spigot", LegacyServerIdentity.platformForBrand("Spigot", "auto"));
        assertEquals("bukkit", LegacyServerIdentity.platformForBrand("CraftBukkit", "auto"));
        assertEquals("custom", LegacyServerIdentity.platformForBrand("Paper", "custom"));
        assertEquals("unattested", LegacyServerIdentity.physicsFingerprint(null, null));
        assertEquals("operator-profile", LegacyServerIdentity.physicsFingerprint(
                null, "operator-profile"));
        assertEquals("environment-profile", LegacyServerIdentity.physicsFingerprint(
                "environment-profile", "operator-profile"));
    }

    private static List<Byte> packetIds(List<PacketEncode> packets) throws Exception {
        List<Byte> ids = new ArrayList<Byte>();
        for (PacketEncode packet : packets) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            packet.encode(out);
            ids.add(out.toByteArray()[0]);
        }
        return ids;
    }

    private static final class EmitTask implements Runnable {
        private final List<PacketEncode> emitted;
        private final PacketEncode packet;

        private EmitTask(List<PacketEncode> emitted, PacketEncode packet) {
            this.emitted = emitted;
            this.packet = packet;
        }

        @Override
        public void run() {
            emitted.add(packet);
        }
    }

}
