package org.vennv.packets;

import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;
import org.vennv.utils.ServerBoundPlayerCommandActions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Packet for server-bound player command actions
 * This replaces the old separate packets for sneaking, sprinting, etc.
 */
public final class PacketServerBoundPlayerCommand extends PacketBaseInfo {

    private final ServerBoundPlayerCommandActions action;

    public PacketServerBoundPlayerCommand(long timestamp, String uid, String username,
                                         ServerBoundPlayerCommandActions action) {
        super(timestamp, uid, username);
        this.action = action;
    }

    public ServerBoundPlayerCommandActions getAction() {
        return action;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_SERVER_BOUND_PLAYER_COMMAND;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);

        // Write action (1 byte)
        out.write(action.getValue());
    }

    @Override
    public String toString() {
        return "PacketServerBoundPlayerCommand{" +
                "timestamp=" + getTimestamp() +
                ", uid='" + getUid() + '\'' +
                ", username='" + getUsername() + '\'' +
                ", action=" + action +
                '}';
    }
}
