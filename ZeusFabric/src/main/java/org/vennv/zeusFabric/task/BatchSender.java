package org.vennv.zeusFabric.task;

import org.vennv.PacketEncode;
import org.vennv.zeusFabric.provider.PacketQueue;
import org.vennv.zeusFabric.network.ProxyClient;

public final class BatchSender implements Runnable {

    private final ProxyClient client;
    private volatile boolean running = true;

    public BatchSender(ProxyClient client, int maxBatchSize) {
        this.client = client;
        // maxBatchSize is now ignored as we send immediately
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void run() {
        while (running) {
            try {
                if (client.isClosed()) {
                    Thread.sleep(100); // Sleep briefly if closed before checking again
                    continue;
                }
                
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
