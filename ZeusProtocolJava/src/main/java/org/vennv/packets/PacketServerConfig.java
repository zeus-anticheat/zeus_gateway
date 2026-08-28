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
 */
public final class PacketServerConfig extends PacketBaseInfo {

    private final float movementSpeed;
    private final int serverProtocol;
    private final String serverVersion;
    private final String serverBrand;
    private final String platform;
    private final String modPluginFingerprint;
    private final int clientProtocol;
    private final String clientVersion;
    private final String translationBehaviorFingerprint;
    private final String identitySource;

    public PacketServerConfig(long timestamp, String uid, String username,
                              float movementSpeed) {
        super(timestamp, uid, username);
        this.movementSpeed = movementSpeed;
        this.serverProtocol = 0;
        this.serverVersion = null;
        this.serverBrand = null;
        this.platform = null;
        this.modPluginFingerprint = null;
        this.clientProtocol = 0;
        this.clientVersion = null;
        this.translationBehaviorFingerprint = null;
        this.identitySource = null;
    }

    /** Extended constructor used by adapters to publish identity before the
     * first movement prediction.  The trailing wire extension is optional. */
    public PacketServerConfig(long timestamp, String uid, String username,
                              float movementSpeed, int serverProtocol, String serverVersion,
                              String serverBrand, String platform, String modPluginFingerprint,
                              int clientProtocol, String clientVersion,
                              String translationBehaviorFingerprint, String identitySource) {
        super(timestamp, uid, username);
        this.movementSpeed = movementSpeed;
        this.serverProtocol = serverProtocol;
        this.serverVersion = serverVersion;
        this.serverBrand = serverBrand;
        this.platform = platform;
        this.modPluginFingerprint = modPluginFingerprint;
        this.clientProtocol = clientProtocol;
        this.clientVersion = clientVersion;
        this.translationBehaviorFingerprint = translationBehaviorFingerprint;
        this.identitySource = identitySource;
    }

    @Override
    public byte packetId() {
        return PacketId.PACKET_SERVER_CONFIG;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        encodePlayerInfo(out);
        ByteBufferUtil.putFloat(out, movementSpeed);
        if (serverVersion != null) {
            ByteBufferUtil.putByte(out, (byte) 1);
            ByteBufferUtil.putShort(out, (short) serverProtocol);
            ByteBufferUtil.putString(out, serverVersion);
            ByteBufferUtil.putString(out, serverBrand == null ? "unknown" : serverBrand);
            ByteBufferUtil.putString(out, platform == null ? "unknown" : platform);
            ByteBufferUtil.putString(out, modPluginFingerprint == null ? "vanilla" : modPluginFingerprint);
            ByteBufferUtil.putShort(out, (short) clientProtocol);
            ByteBufferUtil.putString(out, clientVersion == null ? "unknown" : clientVersion);
            ByteBufferUtil.putString(out, translationBehaviorFingerprint == null ? "" : translationBehaviorFingerprint);
            ByteBufferUtil.putString(out, identitySource == null ? "unknown" : identitySource);
        }
    }

    public float getMovementSpeed() {
        return movementSpeed;
    }
}
