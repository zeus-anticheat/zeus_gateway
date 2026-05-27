package org.vennv.zeusGateway.platform;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;

/**
 * Abstraction layer for scheduling tasks across different platforms.
 * Paper and Spigot use BukkitScheduler, while Folia uses region-based scheduling.
 */
public interface SchedulerAdapter {

    /**
     * Runs a task on the main/region thread.
     *
     * @param plugin the owning plugin
     * @param task   the task to run
     */
    void runTask(JavaPlugin plugin, Runnable task);

    /**
     * Runs a task in the context of a player entity. This is required for Folia chat delivery.
     */
    void runEntityTask(JavaPlugin plugin, Player player, Runnable task);

    /**
     * Runs a task asynchronously.
     *
     * @param plugin the owning plugin
     * @param task   the task to run
     */
    void runTaskAsync(JavaPlugin plugin, Runnable task);

    /**
     * Runs a task after a delay (in ticks).
     *
     * @param plugin the owning plugin
     * @param task   the task to run
     * @param delayTicks delay in ticks before execution
     */
    void runTaskLater(JavaPlugin plugin, Runnable task, long delayTicks);

    /**
     * Runs a task asynchronously after a delay (in ticks).
     *
     * @param plugin the owning plugin
     * @param task   the task to run
     * @param delayTicks delay in ticks before execution
     */
    void runTaskLaterAsync(JavaPlugin plugin, Runnable task, long delayTicks);

    /**
     * Runs a repeating task on the main/region thread.
     *
     * @param plugin the owning plugin
     * @param task   the task to run
     * @param delayTicks  initial delay in ticks
     * @param periodTicks period in ticks between executions
     */
    void runTaskTimer(JavaPlugin plugin, Runnable task, long delayTicks, long periodTicks);

    /**
     * Runs a repeating task asynchronously.
     *
     * @param plugin the owning plugin
     * @param task   the task to run
     * @param delayTicks  initial delay in ticks
     * @param periodTicks period in ticks between executions
     */
    void runTaskTimerAsync(JavaPlugin plugin, Runnable task, long delayTicks, long periodTicks);

    /**
     * Creates the appropriate SchedulerAdapter for the detected platform.
     *
     * @return a SchedulerAdapter suitable for the current server platform
     */
    static SchedulerAdapter create() {
        PlatformType platform = PlatformDetector.getCachedType();
        if (platform == PlatformType.FOLIA) {
            return new FoliaSchedulerAdapter();
        }
        return new BukkitSchedulerAdapter();
    }
}
