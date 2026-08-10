package org.vennv.zeusGateway.listener.packets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.vennv.Effect;
import org.vennv.packets.PacketPlayerEffect;
import org.vennv.utils.EffectFlags;
import org.vennv.zeusGateway.provider.PacketQueue;

class PacketEffectListenerTest {
    @Test
    void infiniteEffectDurationIsAccepted() {
        assertTrue(PacketEffectListener.isValidDuration(-1));
        assertTrue(PacketEffectListener.isValidDuration(0));
        assertFalse(PacketEffectListener.isValidDuration(-2));
    }

    @Test
    void effectIsQueuedOnlyAfterMatchingClientAcknowledgement() {
        while (PacketQueue.poll() != null) {
        }
        ClientAcknowledgementTracker tracker = new ClientAcknowledgementTracker();
        UUID player = UUID.randomUUID();
        Effect effect = new Effect((byte) 1, (byte) 2, 120, EffectFlags.ADD);
        PacketEffectListener.PendingEffect pending =
                new PacketEffectListener.PendingEffect(42L, player.toString(), "name", effect);

        int id = PacketEffectListener.stage(tracker, player, true, pending);

        assertNull(PacketQueue.poll());
        assertFalse(tracker.acknowledge(player, 123));
        assertNull(PacketQueue.poll());
        assertTrue(tracker.acknowledge(player, id));
        PacketPlayerEffect packet = (PacketPlayerEffect) PacketQueue.poll();
        assertEquals(42L, packet.getTimestamp());
        assertSame(effect, packet.getEffect());
        assertNull(PacketQueue.poll());
        assertFalse(tracker.acknowledge(player, id));
    }
}
