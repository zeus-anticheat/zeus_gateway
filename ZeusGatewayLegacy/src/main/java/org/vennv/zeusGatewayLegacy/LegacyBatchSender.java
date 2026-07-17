package org.vennv.zeusGatewayLegacy;

import org.vennv.PacketEncode;

final class LegacyBatchSender implements Runnable {
    private final PacketSender sender;
    private final int maxBatchSize;
    private volatile boolean running = true;

    LegacyBatchSender(final LegacyProxyClient client, int maxBatchSize) {
        this(new PacketSender() {
            @Override
            public boolean send(PacketEncode packet) {
                return client.send(packet);
            }
        }, maxBatchSize);
    }

    LegacyBatchSender(PacketSender sender, int maxBatchSize) {
        this.sender = sender;
        this.maxBatchSize = Math.max(1, maxBatchSize);
    }

    @Override
    public void run() {
        while (running) {
            int sent = 0;
            LegacyPacketQueue.PacketGroup group;
            while (running && sent < maxBatchSize && (group = LegacyPacketQueue.pollGroup()) != null) {
                boolean complete = true;
                for (PacketEncode packet : group.packets()) {
                    if (!sender.send(packet)) {
                        complete = false;
                        break;
                    }
                }
                if (!complete) LegacyPacketQueue.sendFailed(group);
                sent += group.size();
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

    interface PacketSender {
        boolean send(PacketEncode packet);
    }
}
