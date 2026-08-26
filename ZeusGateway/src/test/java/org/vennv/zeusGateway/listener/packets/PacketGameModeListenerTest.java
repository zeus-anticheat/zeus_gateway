package org.vennv.zeusGateway.listener.packets;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.retrooper.packetevents.protocol.player.GameMode;
import org.junit.jupiter.api.Test;

class PacketGameModeListenerTest {
    @Test
    void normalizesAllProtocolModesToStableIds() {
        assertEquals(0, PacketGameModeListener.modeId(GameMode.SURVIVAL));
        assertEquals(1, PacketGameModeListener.modeId(GameMode.CREATIVE));
        assertEquals(2, PacketGameModeListener.modeId(GameMode.ADVENTURE));
        assertEquals(3, PacketGameModeListener.modeId(GameMode.SPECTATOR));
        assertEquals(0, PacketGameModeListener.modeId(null));
    }
}
