package org.vennv.zeusGateway.listener.packets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.vennv.packets.PacketPlayerAbilities;
import org.vennv.zeusGateway.provider.PacketQueue;

class PacketAbilitiesListenerTest {
    @Test
    void serverStateWaitsForAckWhileClientClaimKeepsIndependentSequence() {
        while (PacketQueue.poll() != null) {
        }
        ClientAcknowledgementTracker tracker = new ClientAcknowledgementTracker();
        PacketAbilitiesListener listener = new PacketAbilitiesListener(tracker);
        UUID player = UUID.randomUUID();

        int ack = listener.stageServer(
                player, true, 42L, player.toString(), "name", true, false, 0.08f);
        listener.enqueueClient(player, 43L, player.toString(), "name", true);

        PacketPlayerAbilities client = (PacketPlayerAbilities) PacketQueue.poll();
        assertEquals(PacketPlayerAbilities.Source.CLIENT, client.getSource());
        assertEquals(1L, client.getSequence());
        assertNull(PacketQueue.poll());

        tracker.acknowledge(player, ack);
        PacketPlayerAbilities server = (PacketPlayerAbilities) PacketQueue.poll();
        assertEquals(PacketPlayerAbilities.Source.SERVER, server.getSource());
        assertEquals(1L, server.getSequence());
        assertEquals(0.08f, server.getFlySpeed());
        assertNull(PacketQueue.poll());
    }
}
