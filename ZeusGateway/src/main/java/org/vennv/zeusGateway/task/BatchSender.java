package org.vennv.zeusGateway.task;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.vennv.PacketEncode;
import org.vennv.zeusGateway.network.ProxyClient;
import org.vennv.zeusGateway.provider.PacketQueue;

public final class BatchSender implements Runnable {

    private static final AtomicLong SEND_FAILURE_COUNT = new AtomicLong();
    private final Predicate<PacketEncode> send;
    private final Consumer<String> recover;

    public BatchSender(ProxyClient client, int maxBatchSize) {
        this(Objects.requireNonNull(client, "client")::send, maxBatchSize, ignored -> {});
    }

    public BatchSender(
            ProxyClient client,
            int maxBatchSize,
            Consumer<String> recover) {
        this(Objects.requireNonNull(client, "client")::send, maxBatchSize, recover);
    }

    BatchSender(Predicate<PacketEncode> send, int maxBatchSize) {
        this(send, maxBatchSize, ignored -> {});
    }

    BatchSender(Predicate<PacketEncode> send, int maxBatchSize, Consumer<String> recover) {
        this.send = Objects.requireNonNull(send, "send");
        this.recover = Objects.requireNonNull(recover, "recover");
    }

    public static long getSendFailureCount() {
        return SEND_FAILURE_COUNT.get();
    }

    private static void incrementSendFailureCount() {
        long current;
        do {
            current = SEND_FAILURE_COUNT.get();
            if (current == Long.MAX_VALUE) return;
        } while (!SEND_FAILURE_COUNT.compareAndSet(current, current + 1));
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                PacketQueue.PacketGroup group = PacketQueue.takeGroup();
                if (sendGroup(group)) PacketQueue.markSent(group);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private boolean sendGroup(PacketQueue.PacketGroup group) {
        try {
            for (PacketEncode packet : group.packets()) {
                if (!send.test(packet)) {
                    failGroup(group);
                    return false;
                }
            }
            return true;
        } catch (RuntimeException failure) {
            failGroup(group);
            return false;
        }
    }

    private void failGroup(PacketQueue.PacketGroup group) {
        incrementSendFailureCount();
        if (group.uid() == null) return;
        PacketQueue.markDiscontinuity(group.uid());
        recover.accept(group.uid());
    }
}
