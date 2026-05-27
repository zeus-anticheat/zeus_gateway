package org.vennv.zeusGateway.debug;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.vennv.PacketEncode;

/**
 * Adds producer-side debug context while preserving the exact transmitted wire payload.
 */
public final class PacketDebugEnvelope implements PacketEncode {
    private final PacketEncode packet;
    private final String producerDetails;

    public PacketDebugEnvelope(PacketEncode packet, String producerDetails) {
        this.packet = packet;
        this.producerDetails = producerDetails == null ? "" : producerDetails;
    }

    public PacketEncode getPacket() {
        return packet;
    }

    public String getProducerDetails() {
        return producerDetails;
    }

    @Override
    public void encode(ByteArrayOutputStream out) throws IOException {
        packet.encode(out);
    }
}
