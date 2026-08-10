package org.vennv.zeusGateway.listener.packets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClientAcknowledgementTrackerTest {
    @Test
    void laterAcknowledgementFlushesPendingStateInSendOrderOnce() {
        ClientAcknowledgementTracker tracker = new ClientAcknowledgementTracker();
        UUID player = UUID.randomUUID();
        List<String> applied = new ArrayList<>();
        int first = tracker.stage(player, true, () -> applied.add("add"));
        int second = tracker.stage(player, true, () -> applied.add("remove"));

        assertEquals(Arrays.asList(), applied);
        assertFalse(tracker.acknowledge(player, 123));
        assertEquals(Arrays.asList(), applied);

        assertTrue(tracker.acknowledge(player, second));
        assertEquals(Arrays.asList("add", "remove"), applied);
        assertFalse(tracker.acknowledge(player, first));
        assertEquals(Arrays.asList("add", "remove"), applied);
    }
}
