package org.vennv.zeusGateway.listener.packets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class NettyPingTrackerTest {

    @Test
    void modernAndLegacyPingStagingAndCompletion() {
        NettyPingTracker tracker = new NettyPingTracker();
        UUID player = UUID.randomUUID();

        assertNull(tracker.getLastRtt(player));

        // Modern: ID should be positive and >= 0x40000000
        int modernId = tracker.stage(player, true);
        assertTrue(modernId > 0, "Modern ping ID should be positive");
        assertTrue((modernId & 0x40000000) != 0, "Modern ping ID should have bit 30 set");

        long rttModern = tracker.complete(player, modernId);
        assertTrue(rttModern >= 0, "Measured RTT should be non-negative");
        assertEquals(rttModern, tracker.getLastRtt(player));

        // Legacy: ID should be negative short
        int legacyId = tracker.stage(player, false);
        assertTrue(legacyId < 0, "Legacy ping ID should be negative");
        assertTrue(legacyId >= Short.MIN_VALUE && legacyId <= Short.MAX_VALUE,
                "Legacy ping ID should fit in a short");

        long rttLegacy = tracker.complete(player, legacyId);
        assertTrue(rttLegacy >= 0, "Measured legacy RTT should be non-negative");
        assertEquals(rttLegacy, tracker.getLastRtt(player));

        // Completing unknown ID returns -1
        assertEquals(-1, tracker.complete(player, 999999));

        // Clearing player resets state
        tracker.clearPlayer(player);
        assertNull(tracker.getLastRtt(player));
        assertEquals(-1, tracker.complete(player, modernId));
    }
}
