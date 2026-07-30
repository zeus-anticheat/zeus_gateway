package org.vennv.zeusFabric.provider;

import org.vennv.packets.PacketPlayerInput;

import java.util.Optional;

public final class MovementSemantics {
    public record PacketContext(long sequence, boolean hasPosition, boolean hasLook) {}

    public static final class EmissionGate {
        private long sequence;
        private boolean eligible;
        private boolean accepted;
        private boolean hasPosition;
        private boolean hasLook;

        public boolean begin(
                boolean serverThread,
                boolean hasPosition,
                boolean hasLook,
                double x,
                double y,
                double z,
                float yaw,
                float pitch) {
            this.hasPosition = hasPosition;
            this.hasLook = hasLook;
            this.eligible = serverThread
                    && (!hasPosition || Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z))
                    && (!hasLook || Float.isFinite(yaw) && Float.isFinite(pitch));
            this.accepted = false;
            return eligible;
        }

        public void accept() {
            accepted = eligible;
        }

        public Optional<PacketContext> complete() {
            if (!accepted) {
                eligible = false;
                return Optional.empty();
            }
            accepted = false;
            eligible = false;
            return Optional.of(packetContext(++sequence, hasPosition, hasLook));
        }
    }

    private MovementSemantics() {}

    public static PacketContext packetContext(long sequence, boolean hasPosition, boolean hasLook) {
        if (sequence <= 0L) {
            throw new IllegalArgumentException("movement sequence must be positive");
        }
        return new PacketContext(sequence, hasPosition, hasLook);
    }

    public static double eyeX(double x) {
        return x;
    }

    public static double eyeY(double y, double eyeHeight) {
        return y + eyeHeight;
    }

    public static double eyeZ(double z) {
        return z;
    }

    public static byte rawPacketInputFlags(int directionalFlags) {
        return (byte) ((directionalFlags & 0x7F) | PacketPlayerInput.TRUSTED_CAPTURE);
    }
}
