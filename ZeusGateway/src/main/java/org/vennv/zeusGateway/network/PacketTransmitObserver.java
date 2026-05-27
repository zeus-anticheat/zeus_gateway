package org.vennv.zeusGateway.network;

import org.vennv.PacketEncode;

@FunctionalInterface
public interface PacketTransmitObserver {
    PacketTransmitObserver NO_OP = packet -> {};

    void onPacketTransmitted(PacketEncode packet);
}
