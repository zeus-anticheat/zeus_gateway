package org.vennv.zeusFabric.task;

import org.vennv.PacketEncode;
import org.vennv.zeusFabric.ZeusFabricMod;
import org.vennv.zeusFabric.provider.PacketQueue;
import org.vennv.zeusFabric.network.ProxyClient;

import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class BatchSender implements Runnable {

    private final Predicate<PacketEncode> send;
    private final int maxBatchSize;
    private volatile boolean running = true;

    public BatchSender(ProxyClient client, int maxBatchSize) {
        this(client::send, maxBatchSize);
    }

    BatchSender(Predicate<PacketEncode> send, int maxBatchSize) {
        this.send = send;
        this.maxBatchSize = Math.max(1, maxBatchSize);
    }

    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public void run() {
        while (running || !PacketQueue.isEmpty()) {
            PacketQueue.Batch batch = null;
            int index = 0;
            try {
                batch = PacketQueue.pollBatch(maxBatchSize, 100, TimeUnit.MILLISECONDS);
                if (batch == null) {
                    continue;
                }
                if (!PacketQueue.beginSend(batch.generation())) {
                    PacketQueue.markDiscontinuity(batch.packets().size());
                    continue;
                }
                try {
                    for (; index < batch.packets().size(); index++) {
                        if (!send.test(batch.packets().get(index))) {
                            PacketQueue.markDiscontinuity(batch.packets().size() - index);
                            break;
                        }
                    }
                } finally {
                    PacketQueue.endSend();
                }
            } catch (InterruptedException e) {
                if (running) {
                    continue;
                }
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                ZeusFabricMod.LOGGER.error("[ZeusFabric] Batch sender failed", e);
                PacketQueue.markDiscontinuity(batch == null ? 0L : batch.packets().size() - index);
            }
        }
    }
}
