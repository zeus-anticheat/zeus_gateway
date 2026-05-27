package org.vennv.zeusGatewayLegacy;

import org.vennv.PacketEncode;

final class LegacyBatchSender implements Runnable {
    private final LegacyProxyClient client;
    private final int maxBatchSize;
    private volatile boolean running = true;

    LegacyBatchSender(LegacyProxyClient client, int maxBatchSize) {
        this.client = client;
        this.maxBatchSize = Math.max(1, maxBatchSize);
    }

    @Override
    public void run() {
        while (running) {
            int sent = 0;
            PacketEncode packet;
            while (sent < maxBatchSize && (packet = LegacyPacketQueue.poll()) != null) {
                client.send(packet);
                sent++;
            }
            try {
                Thread.sleep(5L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                running = false;
            }
        }
    }

    void shutdown() {
        running = false;
    }
}
