package org.vennv.packets;

import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;
import org.vennv.utils.Armors;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Packet for tracking the armor equipment worn by the player
 */
public final class PacketPlayerArmorsEquipment extends PacketBaseInfo {

    private final Armors armors;

    public PacketPlayerArmorsEquipment(long timestamp, String uid, String username, Armors armors) {
        super(timestamp, uid, username);
        this.armors = armors;
    }

    public Armors getArmors() {
        return armors;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_PLAYER_ARMORS_EQUIPMENT;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        armors.encode(out);
    }

    @Override
    public String toString() {
        return "PacketPlayerArmorsEquipment{" +
                "timestamp=" + getTimestamp() +
                ", uid='" + getUid() + '\'' +
                ", username='" + getUsername() + '\'' +
                ", armors=" + armors +
                '}';
    }
}
