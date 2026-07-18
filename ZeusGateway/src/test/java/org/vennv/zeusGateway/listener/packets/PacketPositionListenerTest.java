package org.vennv.zeusGateway.listener.packets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.vennv.zeusGateway.ZeusGateway;
import org.vennv.zeusGateway.platform.SchedulerAdapter;
import org.vennv.zeusGateway.provider.PacketQueue;

class PacketPositionListenerTest {

    private ZeusGateway plugin;
    private SchedulerAdapter scheduler;

    @BeforeEach
    void setUp() throws Exception {
        org.bukkit.Server server = mock(org.bukkit.Server.class);
        when(server.getVersion()).thenReturn("git-Paper-20 (MC: 1.21.1)");
        
        java.lang.reflect.Field field = org.bukkit.Bukkit.class.getDeclaredField("server");
        field.setAccessible(true);
        field.set(null, server);

        plugin = mock(ZeusGateway.class);
        scheduler = mock(SchedulerAdapter.class);
        when(plugin.getSchedulerAdapter()).thenReturn(scheduler);
        while (PacketQueue.poll() != null) {}
    }

    @Test
    void testPositionListenerInitialization() {
        PacketPositionListener listener = new PacketPositionListener(plugin);
        assertNotNull(listener);
    }

    @Test
    void movementQueuesPositionBeforeConditionalCollisionTrigger() throws IOException {
        Path path = Paths.get("src/main/java/org/vennv/zeusGateway/listener/packets/PacketPositionListener.java");
        String source = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        int dispatch = source.indexOf("dispatcher.submit(player, () -> {");
        int condition = source.indexOf("if (hasPosition)", dispatch);
        int movement = source.indexOf("chunkSyncTask.onMovement(player, sendX, sendY, sendZ)", condition);
        int push = source.indexOf("PacketQueue.push(position)", movement);

        assertTrue(dispatch >= 0);
        assertTrue(condition > dispatch);
        assertTrue(movement > condition);
        assertTrue(push > movement);
    }
}
