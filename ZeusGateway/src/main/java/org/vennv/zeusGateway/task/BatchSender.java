package org.vennv.zeusGateway.task;

import org.vennv.PacketEncode;
import org.vennv.zeusGateway.provider.PacketQueue;
import org.vennv.zeusGateway.network.ProxyClient;

public final class BatchSender implements Runnable {

    private final ProxyClient client;

    public BatchSender(ProxyClient client, int maxBatchSize) {
        this.client = client;
        // maxBatchSize is now ignored as we send immediately
    }

    @Override
    public void run() {
        while (true) {
            try {
                PacketEncode packet = PacketQueue.take(); // Blocks until a packet is available
                client.send(packet);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

