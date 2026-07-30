package org.vennv.packets;

import org.vennv.ByteBufferUtil;
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
    private final Integer horseJumpCharge;

    public PacketServerBoundPlayerCommand(long timestamp, String uid, String username,
                                         ServerBoundPlayerCommandActions action) {
        this(timestamp, uid, username, action, null);
    }

    public PacketServerBoundPlayerCommand(long timestamp, String uid, String username,
                                         ServerBoundPlayerCommandActions action,
                                         Integer horseJumpCharge) {
        super(timestamp, uid, username);
        this.action = action;
        this.horseJumpCharge = validHorseJumpCharge(horseJumpCharge) ? horseJumpCharge : null;
    }

    private static boolean validHorseJumpCharge(Integer charge) {
        return charge != null && charge >= 0 && charge <= 100;
    }

    public ServerBoundPlayerCommandActions getAction() {
        return action;
    }

    public Integer getHorseJumpCharge() {
        return horseJumpCharge;
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
        if (horseJumpCharge != null) {
            ByteBufferUtil.putInt(out, horseJumpCharge);
        }
    }

    @Override
    public String toString() {
        return "PacketServerBoundPlayerCommand{" +
                "timestamp=" + getTimestamp() +
                ", uid='" + getUid() + '\'' +
                ", username='" + getUsername() + '\'' +
                ", action=" + action +
                ", horseJumpCharge=" + horseJumpCharge +
                '}';
    }
}
