package org.vennv.zeusGateway.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

class FoliaSchedulerAdapterTest {
    @Test
    void delayedEntityTaskUsesEntitySchedulerAndClampsDelay() throws Exception {
        RecordingEntityScheduler scheduler = new RecordingEntityScheduler();
        AtomicBoolean ran = new AtomicBoolean();

        FoliaSchedulerAdapter.invokeEntityTaskLater(
                scheduler, mock(JavaPlugin.class), () -> ran.set(true), 0L);

        assertEquals(1L, scheduler.delayTicks);
        assertNotNull(scheduler.retired);
        assertFalse(ran.get());
        scheduler.task.accept(new Object());
        assertTrue(ran.get());
    }

    @Test
    void regionTaskUsesChunkCoordinates() throws Exception {
        RecordingRegionScheduler scheduler = new RecordingRegionScheduler();
        AtomicBoolean ran = new AtomicBoolean();
        World world = mock(World.class);

        FoliaSchedulerAdapter.invokeRegionTask(
                scheduler, mock(JavaPlugin.class), world, -3, 30, () -> ran.set(true));

        assertEquals(world, scheduler.world);
        assertEquals(-3, scheduler.chunkX);
        assertEquals(30, scheduler.chunkZ);
        assertTrue(ran.get());
    }

    public static final class RecordingEntityScheduler {
        private Consumer<Object> task;
        private Runnable retired;
        private long delayTicks;

        public void runDelayed(Plugin plugin, Consumer<Object> task, Runnable retired, long delayTicks) {
            this.task = task;
            this.retired = retired;
            this.delayTicks = delayTicks;
        }
    }

    public static final class RecordingRegionScheduler {
        private World world;
        private int chunkX;
        private int chunkZ;

        public void execute(
                Plugin plugin, World world, int chunkX, int chunkZ, Runnable task) {
            this.world = world;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            task.run();
        }
    }
}
