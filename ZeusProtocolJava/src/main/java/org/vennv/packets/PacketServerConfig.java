package org.vennv.packets;

import org.vennv.ByteBufferUtil;
import org.vennv.PacketBaseInfo;
import org.vennv.PacketId;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Sends server combat configuration to Zeus sv_core.
 * Sent once on player join and periodically via ResyncTask.
 *
 * Wire format:
 *   PacketBase (packetId + timestamp + uid + username)
 *   server_reach          (f32) — base melee reach in blocks (vanilla = 3.0)
 *   attack_cooldown_ticks (f32) — cooldown ticks (1.9+ = 10.0, 1.8 = 0.0)
 *   max_cps               (u8)  — server max CPS limit (0 = unlimited)
 */
public final class PacketServerConfig extends PacketBaseInfo {

    private final float serverReach;
    private final float attackCooldownTicks;
    private final byte maxCps;

    public PacketServerConfig(long timestamp, String uid, String username,
                              float serverReach, float attackCooldownTicks, byte maxCps) {
        super(timestamp, uid, username);
        this.serverReach = serverReach;
        this.attackCooldownTicks = attackCooldownTicks;
        this.maxCps = maxCps;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_SERVER_CONFIG;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        ByteBufferUtil.putFloat(out, serverReach);
        ByteBufferUtil.putFloat(out, attackCooldownTicks);
        ByteBufferUtil.putByte(out, maxCps);
    }

    public float getServerReach() {
        return serverReach;
    }

    public float getAttackCooldownTicks() {
        return attackCooldownTicks;
    }

    public byte getMaxCps() {
        return maxCps;
    }
}
