package org.vennv.zeusGateway.listener.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.vennv.zeusGateway.listener.RawCaptureCapability;

class EventListenerCapabilityTest {
    @Test
    void successfulRawListenerDisablesOnlyItsMatchingFallback() {
        EventListener listener = new EventListener(
                EnumSet.of(RawCaptureCapability.CLICK_WINDOW, RawCaptureCapability.ATTACK_ENTITY));

        assertFalse(listener.isFallbackEnabled(RawCaptureCapability.CLICK_WINDOW));
        assertFalse(listener.isFallbackEnabled(RawCaptureCapability.ATTACK_ENTITY));
        assertTrue(listener.isFallbackEnabled(RawCaptureCapability.HELD_ITEM));
    }

    @Test
    void rawPlayerCommandControlsToggleFallbacksIndependently() {
        EventListener withRawCommand = new EventListener(
                EnumSet.of(RawCaptureCapability.PLAYER_COMMAND));
        EventListener withoutRawCommand = new EventListener();

        assertFalse(withRawCommand.isFallbackEnabled(RawCaptureCapability.PLAYER_COMMAND));
        assertTrue(withoutRawCommand.isFallbackEnabled(RawCaptureCapability.PLAYER_COMMAND));
    }

    @Test
    void lifecycleAndRecoveryTriggersKeepRequiredOrdering() throws IOException {
        String listener = source("listener/event/EventListener.java");
        assertTrue(listener.contains("@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)\n"
                + "    public void onPlayerTeleport"));
        assertOrder(section(listener, "public void onPlayerJoin", "public void onPlayerLeave"),
                "ChunkSyncTask.invalidate(player)",
                "PlayerStateSnapshotService.sendFullSnapshot(player)",
                "scheduleFullCollisionSnapshot(player)");
        assertOrder(section(listener, "public void onPlayerLeave", "public void onPlayerVelocity"),
                "PacketQueue.push(packet)",
                "ChunkSyncTask.remove(player)",
                "PlayerStateSnapshotService.clear(player)");
        assertOrder(section(listener, "public void onPlayerRespawn", "public void onEntityDamageByEntity"),
                "PacketQueue.push(packet)",
                "ChunkSyncTask.invalidate(player)",
                "scheduleFullCollisionSnapshot(player)");
        assertOrder(section(listener, "public void onPlayerTeleport", "public void onPlayerChangedWorld"),
                "PacketQueue.push(packet)",
                "ChunkSyncTask.invalidate(player)",
                "scheduleFullCollisionSnapshot(player)");
        assertOrder(section(listener, "public void onPlayerChangedWorld", "public void onPotionEffect"),
                "PacketQueue.push(packet)",
                "ChunkSyncTask.invalidate(player)",
                "scheduleFullCollisionSnapshot(player)");
        assertTrue(listener.contains("runEntityTaskLater(plugin, player"));
        assertTrue(listener.contains("chunkSyncTask.forceFull(player)"));

        String resync = source("task/ResyncTask.java");
        assertOrder(resync,
                "PlayerStateSnapshotService.sendResyncSnapshot(player)",
                "chunkSyncTask.syncPlayer(player)");

        String loader = source("init/ZeusLoader.java");
        assertOrder(loader,
                "ChunkSyncTask.invalidate(player)",
                "PlayerStateSnapshotService.sendFullSnapshot(player)",
                "chunkSyncTask.forceFull(player)");

        String session = source("ModernGatewaySession.java");
        assertTrue(section(session, "public void close()", "private void registerDebugCommand")
                .contains("ChunkSyncTask.clearAll()"));
    }

    @Test
    void blockChangesNormalizeAirAndRequireCurrentWindow() {
        assertEquals("minecraft:air", EventListener.normalizeBlockType(true, "minecraft:cave_air"));
        assertEquals("minecraft:stone", EventListener.normalizeBlockType(false, "minecraft:stone"));
        assertFalse(EventListener.shouldEmitBlockChange(
                UUID.randomUUID(), UUID.randomUUID(), 0, 64, 0));
    }

    @Test
    void fallbackVehicleMovementRecentersCollisionBeforeQueueingPacket() throws IOException {
        String listener = source("listener/event/EventListener.java");
        String section = section(listener, "public void onVehicleMove", "// ──────────────── Use / Release Use Item");
        assertOrder(section,
                "PacketVehicleMoveListener.vehicleType(vehicle)",
                "vehicle.getEntityId()",
                "PacketVehicleMoveListener.vehicleFlags(vehicle)",
                "chunkSyncTask.onMovement(player, to.getX(), to.getY(), to.getZ())",
                "PacketQueue.push(packet)");
    }

    private static String source(String relativePath) throws IOException {
        Path path = Paths.get("src/main/java/org/vennv/zeusGateway").resolve(relativePath);
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static String section(String source, String startMarker, String endMarker) {
        int start = source.indexOf(startMarker);
        int end = source.indexOf(endMarker, start + startMarker.length());
        assertTrue(start >= 0, startMarker);
        assertTrue(end > start, endMarker);
        return source.substring(start, end);
    }

    private static void assertOrder(String source, String... markers) {
        int cursor = 0;
        for (String marker : markers) {
            int index = source.indexOf(marker, cursor);
            assertTrue(index >= cursor, marker);
            cursor = index + marker.length();
        }
    }
}
