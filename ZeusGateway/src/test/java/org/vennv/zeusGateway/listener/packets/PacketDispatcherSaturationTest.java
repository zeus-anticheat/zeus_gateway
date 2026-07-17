package org.vennv.zeusGateway.listener.packets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.player.User;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.platform.SchedulerAdapter;

class PacketDispatcherSaturationTest {
    @Test
    void modernRegistrarDoesNotConstructOrRegisterPacketChunkListener() throws IOException {
        String source = source("PacketEventsListenerRegistrar.java");

        assertFalse(source.contains("register(\"PacketChunkListener\""));
        assertFalse(source.contains("new PacketChunkListener("));
    }

    @Test
    void blockChangeCaptureDoesNotBlockMovement() throws IOException {
        String source = source("PacketBlockChangeListener.java");

        assertTrue(source.contains(
                "dispatcher.submit(event,"));
    }

    @Test
    void worldExecutorSaturationLeavesMovementLaneUsable() throws Exception {
        ZeusGateway plugin = mock(ZeusGateway.class);
        SchedulerAdapter scheduler = mock(SchedulerAdapter.class);
        Player player = mock(Player.class);
        PacketSendEvent event = mock(PacketSendEvent.class);
        User user = mock(User.class);
        UUID uuid = UUID.randomUUID();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        AtomicInteger playerTasks = new AtomicInteger();

        when(plugin.getSchedulerAdapter()).thenReturn(scheduler);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("test"));
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(event.getUser()).thenReturn(user);
        when(event.clone()).thenReturn(event);
        when(user.getUUID()).thenReturn(uuid);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(2)).run();
            return null;
        }).when(scheduler).runEntityTask(any(), any(), any());

        OrderedPlayerPacketDispatcher playerDispatcher =
                new OrderedPlayerPacketDispatcher(plugin);
        OrderedWorldPacketDispatcher worldDispatcher =
                new OrderedWorldPacketDispatcher(plugin, playerDispatcher);
        try {
            java.util.function.Consumer<PacketSendEvent> handler = ignored -> {
                if (first.compareAndSet(true, false)) {
                    started.countDown();
                    try {
                        release.await();
                    } catch (InterruptedException error) {
                        Thread.currentThread().interrupt();
                    }
                }
            };
            worldDispatcher.submit(event, handler, false);
            assertTrue(started.await(2, TimeUnit.SECONDS));
            for (int index = 0; index < 17; index++) {
                worldDispatcher.submit(event, handler, false);
            }

            assertTrue(playerDispatcher.submit(player, playerTasks::incrementAndGet));
            assertTrue(playerTasks.get() > 0);
        } finally {
            release.countDown();
            worldDispatcher.close();
            playerDispatcher.close();
        }
    }

    private static String source(String name) throws IOException {
        Path path = Paths.get("src/main/java/org/vennv/zeusGateway/listener/packets").resolve(name);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
