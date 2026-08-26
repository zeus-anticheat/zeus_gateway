package org.vennv.zeusGateway.listener.packets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.retrooper.packetevents.protocol.world.Direction;
import com.github.retrooper.packetevents.resources.ResourceLocation;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.vennv.packets.PacketChunkData;
import org.vennv.packets.PacketPlayerExternalForce;
import org.vennv.packets.PacketPlayerPosition;
import org.vennv.packets.PacketPlayerSwingHand;
import org.vennv.packets.PacketUpdateAttributes;
import org.vennv.utils.ExternalForceFlags;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.platform.SchedulerAdapter;
import org.vennv.zeusGateway.platform.ServerIdentity;
import org.vennv.zeusGateway.provider.PacketQueue;

class PacketConversionTest {
    @Test
    void packetEventsMapsCurrentProtocolNames() {
        assertEquals("26.1", ServerIdentity.clientVersion(775));
        assertEquals("26.2", ServerIdentity.clientVersion(776));
    }

    @Test
    void updateAttributesKeepsRawGravityModifierWithoutMovementSpeed() {
        UUID modifierId = UUID.fromString("12345678-1234-5678-9abc-def012345678");
        ResourceLocation modifierName = mock(ResourceLocation.class);
        WrapperPlayServerUpdateAttributes.PropertyModifier modifier =
                mock(WrapperPlayServerUpdateAttributes.PropertyModifier.class);
        WrapperPlayServerUpdateAttributes.Property gravity =
                mock(WrapperPlayServerUpdateAttributes.Property.class);
        when(modifierName.toString()).thenReturn("plugin:gravity_boost");
        when(modifier.getName()).thenReturn(modifierName);
        when(modifier.getUUID()).thenReturn(modifierId);
        when(modifier.getAmount()).thenReturn(0.25);
        when(modifier.getOperation()).thenReturn(
                WrapperPlayServerUpdateAttributes.PropertyModifier.Operation.MULTIPLY_BASE);
        when(gravity.getKey()).thenReturn("minecraft:gravity");
        when(gravity.getValue()).thenReturn(0.08);
        when(gravity.getModifiers()).thenReturn(Collections.singletonList(modifier));

        List<PacketUpdateAttributes.Property> converted =
                PacketUpdateAttributesListener.convertProperties(Collections.singletonList(gravity));

        assertEquals(1, converted.size());
        assertEquals("minecraft:gravity", converted.get(0).getKey());
        assertEquals(0.08, converted.get(0).getBaseValue());
        assertEquals(1, converted.get(0).getModifiers().size());
        PacketUpdateAttributes.Modifier raw = converted.get(0).getModifiers().get(0);
        assertEquals(modifierId.toString(), raw.getStableId());
        assertEquals("plugin:gravity_boost", raw.getName());
        assertEquals(0.25, raw.getAmount());
        assertEquals(PacketUpdateAttributes.Operation.MULTIPLY_BASE, raw.getOperation());
    }

    @Test
    void packetBlockChangesNormalizePacketEventsAirAndRequireCurrentWindow() {
        assertEquals("minecraft:air", PacketBlockChangeListener.normalizeBlockType(
                true, "minecraft:cave_air"));
        assertEquals("minecraft:stone", PacketBlockChangeListener.normalizeBlockType(
                false, "minecraft:stone"));
        assertEquals(false, PacketBlockChangeListener.shouldEmit(
                UUID.randomUUID(), UUID.randomUUID(), 0, 64, 0));
    }


    @Test
    void horseTelemetryRejectsInvalidValues() {
        assertEquals(PacketVehicleMoveListener.HorseTelemetry.UNKNOWN.movementSpeed,
                PacketVehicleMoveListener.horseTelemetry(null).movementSpeed);
    }

    @Test
    void horseJumpChargeIsValidatedAndOptional() {
        org.vennv.packets.PacketServerBoundPlayerCommand packet =
                new org.vennv.packets.PacketServerBoundPlayerCommand(
                        1L, "u", "n",
                        org.vennv.utils.ServerBoundPlayerCommandActions.START_RIDING_JUMP,
                        90);
        assertEquals(Integer.valueOf(90), packet.getHorseJumpCharge());
        org.vennv.packets.PacketServerBoundPlayerCommand invalid =
                new org.vennv.packets.PacketServerBoundPlayerCommand(
                        1L, "u", "n",
                        org.vennv.utils.ServerBoundPlayerCommandActions.START_RIDING_JUMP,
                        101);
        assertNull(invalid.getHorseJumpCharge());
    }

    @Test
    void shulkerBlockActionCaptureUsesAnimationIdOnly() {
        assertTrue(PacketShulkerBoxActionListener.shouldCaptureAction(1));
        assertEquals(false, PacketShulkerBoxActionListener.shouldCaptureAction(0));
        assertEquals(false, PacketShulkerBoxActionListener.shouldCaptureAction(2));
    }

    @Test
    void modernClickWindowDoesNotReuseButtonAsTransactionId() {
        assertEquals((short) 0,
                PacketClickWindowListener.transactionIdForVersion(true, (short) 4));
    }

    @Test
    void legacyClickWindowKeepsTheActionNumber() {
        assertEquals((short) 37,
                PacketClickWindowListener.transactionIdForVersion(false, (short) 37));
    }

    @Test
    void nullOrInvalidBlockFaceIsNotConvertedToDown() {
        assertNull(PacketBlockFaceListener.mapDirectionToFace(null));
        assertNull(PacketBlockFaceListener.validFace(7));
        assertEquals((byte) 0,
                PacketBlockFaceListener.mapDirectionToFace(Direction.DOWN));
    }

    @Test
    void allSequencedDigActionsReachInteractionStream() {
        assertTrue(PacketBlockFaceListener.isBlockDigAction(
                com.github.retrooper.packetevents.protocol.player.DiggingAction.START_DIGGING));
        assertTrue(PacketBlockFaceListener.isBlockDigAction(
                com.github.retrooper.packetevents.protocol.player.DiggingAction.FINISHED_DIGGING));
        assertTrue(PacketBlockFaceListener.isBlockDigAction(
                com.github.retrooper.packetevents.protocol.player.DiggingAction.CANCELLED_DIGGING));
        assertEquals(org.vennv.packets.PacketPlayerBlockRayTrace.DIG_PHASE_START,
                PacketBlockFaceListener.digPhase(
                        com.github.retrooper.packetevents.protocol.player.DiggingAction.START_DIGGING));
        assertEquals(org.vennv.packets.PacketPlayerBlockRayTrace.DIG_PHASE_FINISH,
                PacketBlockFaceListener.digPhase(
                        com.github.retrooper.packetevents.protocol.player.DiggingAction.FINISHED_DIGGING));
        assertEquals(org.vennv.packets.PacketPlayerBlockRayTrace.DIG_PHASE_CANCEL,
                PacketBlockFaceListener.digPhase(
                        com.github.retrooper.packetevents.protocol.player.DiggingAction.CANCELLED_DIGGING));
    }

    @Test
    void modernVelocityAcknowledgementsDoNotUseGrimTransactionNamespace() {
        AtomicInteger counter = new AtomicInteger();

        for (int i = 0; i < 1024; i++) {
            int id = PacketVelocityListener.nextAcknowledgementId(counter, true);
            assertTrue(id != (short) id);
        }
    }

    @Test
    void legacyVelocityAcknowledgementsRemainWindowTransactionIds() {
        AtomicInteger counter = new AtomicInteger();

        int id = PacketVelocityListener.nextAcknowledgementId(counter, false);

        assertEquals(-1, id);
        assertEquals((short) -1, (short) id);
    }

    @Test
    void velocityAcknowledgementsKeepOneForceIdentityAcrossStages() {
        PacketVelocityListener.PendingVelocity velocity =
                new PacketVelocityListener.PendingVelocity(42L, "u", "n", 0.1, 0.2, 0.3, null);
        PacketPlayerExternalForce first =
                new PacketVelocityListener.Acknowledgement(velocity, false).toPacket();
        PacketPlayerExternalForce required =
                new PacketVelocityListener.Acknowledgement(velocity, true).toPacket();

        assertEquals(42L, first.getTimestamp());
        assertEquals(42L, required.getTimestamp());
        assertTrue((first.getFlags() & ExternalForceFlags.VELOCITY_FIRST_BREAD) != 0);
        assertTrue((first.getFlags() & ExternalForceFlags.VELOCITY_REQUIRED) == 0);
        assertTrue((required.getFlags() & ExternalForceFlags.VELOCITY_REQUIRED) != 0);
        assertTrue((required.getFlags() & ExternalForceFlags.VELOCITY_FIRST_BREAD) == 0);
    }

    @Test
    void windChargeExplosionKeepsItsTypedKnockbackAcrossStages() {
        PacketVelocityListener.PendingExplosion explosion =
                new PacketVelocityListener.PendingExplosion(
                        org.vennv.utils.ExternalForceType.WIND_CHARGE,
                        1.0, 2.0, 3.0, 0.1, 0.8, -0.2);
        PacketVelocityListener.PendingVelocity velocity =
                new PacketVelocityListener.PendingVelocity(42L, "u", "n", 0.1, 0.8, -0.2, explosion);
        PacketPlayerExternalForce first =
                new PacketVelocityListener.Acknowledgement(velocity, false).toPacket();
        PacketPlayerExternalForce required =
                new PacketVelocityListener.Acknowledgement(velocity, true).toPacket();

        assertEquals(org.vennv.utils.ExternalForceType.WIND_CHARGE, first.getForceType());
        assertEquals(org.vennv.utils.ExternalForceType.WIND_CHARGE, required.getForceType());
        assertEquals(1.0, first.getSourceX());
        assertEquals(2.0, first.getSourceY());
        assertEquals(3.0, first.getSourceZ());
        assertEquals(0.8, first.getVelocityY());
        assertTrue((first.getFlags() & ExternalForceFlags.VELOCITY_FIRST_BREAD) != 0);
        assertTrue((required.getFlags() & ExternalForceFlags.VELOCITY_REQUIRED) != 0);
    }

    @Test
    void packetQueueOrdersEqualPriorityPacketsByInsertionOrder() {
        while (PacketQueue.poll() != null) {
        }
        PacketPlayerPosition newer = positionAt(200);
        PacketPlayerPosition older = positionAt(100);

        PacketQueue.push(newer);
        PacketQueue.push(older);

        assertSame(newer, PacketQueue.poll());
        assertSame(older, PacketQueue.poll());
        assertNull(PacketQueue.poll());
    }

    @Test
    void packetQueuePreservesInsertionOrderAcrossPacketTypes() {
        while (PacketQueue.poll() != null) {
        }
        PacketPlayerSwingHand swing = new PacketPlayerSwingHand(100, "u", "n", false);
        PacketPlayerPosition movement = positionAt(200);

        PacketQueue.push(swing);
        PacketQueue.push(movement);

        assertSame(swing, PacketQueue.poll());
        assertSame(movement, PacketQueue.poll());
        assertNull(PacketQueue.poll());
    }

    @Test
    void packetQueuePrioritizesMovementOverChunkBacklog() {
        while (PacketQueue.poll() != null) {
        }
        PacketChunkData chunk = new PacketChunkData(
                100, "u", "n", 0, 0, true, Collections.emptyList());
        PacketPlayerPosition movement = positionAt(200);

        PacketQueue.push(chunk);
        PacketQueue.push(movement);

        assertSame(chunk, PacketQueue.poll());
        assertSame(movement, PacketQueue.poll());
        assertNull(PacketQueue.poll());
    }

    @Test
    void orderedDispatcherBlocksAndRecoversWithoutReordering() {
        ZeusGateway plugin = mock(ZeusGateway.class);
        SchedulerAdapter scheduler = mock(SchedulerAdapter.class);
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        List<Runnable> scheduled = new ArrayList<>();
        List<Integer> emitted = new ArrayList<>();
        when(plugin.getSchedulerAdapter()).thenReturn(scheduler);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        doAnswer(invocation -> {
            scheduled.add(invocation.getArgument(2));
            return null;
        }).when(scheduler).runEntityTask(eq(plugin), eq(player), any(Runnable.class));
        OrderedPlayerPacketDispatcher dispatcher = new OrderedPlayerPacketDispatcher(plugin);

        dispatcher.block(uuid);
        dispatcher.submit(player, () -> emitted.add(1));
        assertEquals(0, scheduled.size());
        dispatcher.unblock(uuid);
        assertEquals(1, scheduled.size());
        scheduled.remove(0).run();
        assertEquals(Arrays.asList(1), emitted);

        dispatcher.fail(uuid);
        assertEquals(false, dispatcher.submit(player, () -> emitted.add(2)));
        dispatcher.recover(uuid);
        assertEquals(true, dispatcher.submit(player, () -> emitted.add(3)));
        scheduled.remove(0).run();
        assertEquals(Arrays.asList(1, 3), emitted);
        dispatcher.close();
    }

    @Test
    void orderedDispatcherPreservesOnePlayerArrivalOrder() {
        ZeusGateway plugin = mock(ZeusGateway.class);
        SchedulerAdapter scheduler = mock(SchedulerAdapter.class);
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        List<Runnable> scheduled = new ArrayList<>();
        List<Integer> emitted = new ArrayList<>();
        when(plugin.getSchedulerAdapter()).thenReturn(scheduler);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        doAnswer(invocation -> {
            scheduled.add(invocation.getArgument(2));
            return null;
        }).when(scheduler).runEntityTask(eq(plugin), eq(player), any(Runnable.class));
        OrderedPlayerPacketDispatcher dispatcher = new OrderedPlayerPacketDispatcher(plugin);

        dispatcher.submit(player, () -> emitted.add(1));
        dispatcher.submit(player, () -> emitted.add(2));
        dispatcher.submit(player, () -> emitted.add(3));

        assertEquals(1, scheduled.size());
        assertEquals(Arrays.asList(), emitted);
        scheduled.get(0).run();
        assertEquals(Arrays.asList(1, 2, 3), emitted);
        dispatcher.close();
    }

    private static PacketPlayerPosition positionAt(long timestamp) {
        return new PacketPlayerPosition(
                timestamp, "u", "n", false,
                1.0, 2.0, 3.0,
                1.0, 3.62, 3.0,
                0.0f, 0.0f, 1.8f, true);
    }
}
