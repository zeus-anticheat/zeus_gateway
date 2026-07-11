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
    private final int captureSchema;
    private final String adapterId;
    private final String adapterVersion;
    private final long adapterCapabilityBitmap;
    private final String behaviorBundleHash;
    private final String behaviorRegistryHash;

    public PacketServerConfig(long timestamp, String uid, String username,
                              float serverReach, float attackCooldownTicks, byte maxCps,
                              float movementSpeed) {
        super(timestamp, uid, username);
        this.serverReach = serverReach;
        this.attackCooldownTicks = attackCooldownTicks;
        this.maxCps = maxCps;
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
        this.captureSchema = 0;
        this.adapterId = null;
        this.adapterVersion = null;
        this.adapterCapabilityBitmap = 0L;
        this.behaviorBundleHash = null;
        this.behaviorRegistryHash = null;
    }

    /** Extended constructor used by adapters to publish identity before the
     * first movement prediction.  The trailing wire extension is optional. */
    public PacketServerConfig(long timestamp, String uid, String username,
                              float serverReach, float attackCooldownTicks, byte maxCps,
                              float movementSpeed, int serverProtocol, String serverVersion,
                              String serverBrand, String platform, String modPluginFingerprint,
                              int clientProtocol, String clientVersion,
                              String translationBehaviorFingerprint, String identitySource) {
        super(timestamp, uid, username);
        this.serverReach = serverReach;
        this.attackCooldownTicks = attackCooldownTicks;
        this.maxCps = maxCps;
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
        this.captureSchema = CaptureFrameV3.CAPTURE_SCHEMA_VERSION;
        this.adapterId = identitySource == null || identitySource.isEmpty() ? "unknown" : identitySource;
        this.adapterVersion = CaptureFrameV3.configuredAdapterVersion();
        this.adapterCapabilityBitmap = CaptureFrameV3.configuredCapabilityBitmap(identitySource);
        this.behaviorBundleHash = CaptureFrameV3.configuredBehaviorBundleHash(serverVersion);
        this.behaviorRegistryHash = CaptureFrameV3.BEHAVIOR_REGISTRY_HASH;
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
        ByteBufferUtil.putFloat(out, movementSpeed);
        if (serverVersion != null) {
            // Extension v2 is the coordinated CaptureFrameV3 handshake.
            ByteBufferUtil.putByte(out, (byte) 2);
            ByteBufferUtil.putShort(out, (short) serverProtocol);
            ByteBufferUtil.putString(out, serverVersion);
            ByteBufferUtil.putString(out, serverBrand == null ? "unknown" : serverBrand);
            ByteBufferUtil.putString(out, platform == null ? "unknown" : platform);
            ByteBufferUtil.putString(out, modPluginFingerprint == null ? "vanilla" : modPluginFingerprint);
            ByteBufferUtil.putShort(out, (short) clientProtocol);
            ByteBufferUtil.putString(out, clientVersion == null ? "unknown" : clientVersion);
            ByteBufferUtil.putString(out, translationBehaviorFingerprint == null ? "" : translationBehaviorFingerprint);
            ByteBufferUtil.putString(out, identitySource == null ? "unknown" : identitySource);
            ByteBufferUtil.putByte(out, (byte) captureSchema);
            ByteBufferUtil.putString(out, adapterId == null ? "" : adapterId);
            ByteBufferUtil.putString(out, adapterVersion == null ? "" : adapterVersion);
            ByteBufferUtil.putLong(out, adapterCapabilityBitmap);
            ByteBufferUtil.putString(out, behaviorBundleHash == null ? "" : behaviorBundleHash);
            ByteBufferUtil.putString(out, behaviorRegistryHash == null ? "" : behaviorRegistryHash);
        }
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

    public float getMovementSpeed() {
        return movementSpeed;
    }
}
