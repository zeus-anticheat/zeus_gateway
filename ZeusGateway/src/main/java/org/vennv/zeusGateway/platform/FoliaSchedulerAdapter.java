package org.vennv.zeusGateway.platform;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler adapter for Folia servers.
 * Uses reflection to call Folia's region-based scheduling APIs
 * so this compiles even without Folia on the classpath.
 */
public final class FoliaSchedulerAdapter implements SchedulerAdapter {

    private static final Runnable RETIRED_TASK = () -> {};

    private final Object globalRegionScheduler;
    private final Object regionScheduler;
    private final Object asyncScheduler;
    private final Method globalRunMethod;
    private final Method globalRunDelayedMethod;
    private final Method globalRunAtFixedRateMethod;
    private final Method asyncRunMethod;
    private final Method asyncRunDelayedMethod;
    private final Method asyncRunAtFixedRateMethod;

    public FoliaSchedulerAdapter() {
        try {
            // Get Folia's GlobalRegionScheduler
            Method getGlobalRegionScheduler = Bukkit.getServer().getClass()
                    .getMethod("getGlobalRegionScheduler");
            globalRegionScheduler = getGlobalRegionScheduler.invoke(Bukkit.getServer());

            Method getRegionScheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler");
            regionScheduler = getRegionScheduler.invoke(Bukkit.getServer());

            // Get Folia's AsyncScheduler
            Method getAsyncScheduler = Bukkit.getServer().getClass()
                    .getMethod("getAsyncScheduler");
            asyncScheduler = getAsyncScheduler.invoke(Bukkit.getServer());

            // Resolve GlobalRegionScheduler methods
            Class<?> globalSchedulerClass = globalRegionScheduler.getClass();
            globalRunMethod = findMethod(globalSchedulerClass, "run",
                    org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class);
            globalRunDelayedMethod = findMethod(globalSchedulerClass, "runDelayed",
                    org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, long.class);
            globalRunAtFixedRateMethod = findMethod(globalSchedulerClass, "runAtFixedRate",
                    org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, long.class, long.class);

            // Resolve AsyncScheduler methods
            Class<?> asyncSchedulerClass = asyncScheduler.getClass();
            asyncRunMethod = findMethod(asyncSchedulerClass, "runNow",
                    org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class);
            asyncRunDelayedMethod = findMethod(asyncSchedulerClass, "runDelayed",
                    org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, long.class, TimeUnit.class);
            asyncRunAtFixedRateMethod = findMethod(asyncSchedulerClass, "runAtFixedRate",
                    org.bukkit.plugin.Plugin.class, java.util.function.Consumer.class, long.class, long.class, TimeUnit.class);

        } catch (Exception e) {
            throw new RuntimeException("[ZeusGateway] Failed to initialize Folia scheduler adapter", e);
        }
    }

    static Method findMethod(Class<?> clazz, String name, Class<?>... paramTypes) {
        // Try direct lookup first
        try {
            return clazz.getMethod(name, paramTypes);
        } catch (NoSuchMethodException ignored) {
        }

        // Search through all methods for a matching name and compatible parameter count
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == paramTypes.length) {
                return m;
            }
        }

        throw new RuntimeException("[ZeusGateway] Could not find method " + name
                + " on class " + clazz.getName());
    }

    /**
     * Wraps a Runnable into a Consumer<Object> (Consumer<ScheduledTask>)
     * so it can be passed to Folia's scheduling methods which expect Consumer<ScheduledTask>.
     */
    private java.util.function.Consumer<Object> wrapTask(Runnable task) {
        return scheduledTask -> task.run();
    }

    @Override
    public void runTask(JavaPlugin plugin, Runnable task) {
        try {
            globalRunMethod.invoke(globalRegionScheduler, plugin, wrapTask(task));
        } catch (Exception e) {
            throw new RuntimeException("[ZeusGateway] Failed to run task on Folia global scheduler", e);
        }
    }

    @Override
    public void runEntityTask(JavaPlugin plugin, Player player, Runnable task) {
        try {
            Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
            try {
                Method execute = scheduler.getClass().getMethod(
                        "execute",
                        org.bukkit.plugin.Plugin.class,
                        Runnable.class,
                        Runnable.class);
                execute.invoke(scheduler, plugin, task, null);
                return;
            } catch (NoSuchMethodException ignored) {
                Method run = findMethod(
                        scheduler.getClass(),
                        "run",
                        org.bukkit.plugin.Plugin.class,
                        java.util.function.Consumer.class,
                        Runnable.class);
                run.invoke(scheduler, plugin, wrapTask(task), null);
            }
        } catch (Exception e) {
            throw new RuntimeException("[ZeusGateway] Failed to run task on Folia entity scheduler", e);
        }
    }

    @Override
    public void runEntityTaskLater(JavaPlugin plugin, Player player, Runnable task, long delayTicks) {
        try {
            Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
            invokeEntityTaskLater(scheduler, plugin, task, delayTicks);
        } catch (Exception e) {
            throw new RuntimeException("[ZeusGateway] Failed to run delayed task on Folia entity scheduler", e);
        }
    }

    static void invokeEntityTaskLater(Object scheduler, JavaPlugin plugin, Runnable task, long delayTicks)
            throws Exception {
        Method runDelayed = findMethod(
                scheduler.getClass(),
                "runDelayed",
                org.bukkit.plugin.Plugin.class,
                java.util.function.Consumer.class,
                Runnable.class,
                long.class);
        runDelayed.invoke(scheduler, plugin, (java.util.function.Consumer<Object>) ignored -> task.run(), RETIRED_TASK,
                Math.max(1, delayTicks));
    }

    @Override
    public void runRegionTask(
            JavaPlugin plugin, World world, int chunkX, int chunkZ, Runnable task) {
        try {
            invokeRegionTask(regionScheduler, plugin, world, chunkX, chunkZ, task);
        } catch (Exception e) {
            throw new RuntimeException("[ZeusGateway] Failed to run task on Folia region scheduler", e);
        }
    }

    static void invokeRegionTask(
            Object scheduler,
            JavaPlugin plugin,
            World world,
            int chunkX,
            int chunkZ,
            Runnable task) throws Exception {
        Method execute = findMethod(
                scheduler.getClass(),
                "execute",
                org.bukkit.plugin.Plugin.class,
                World.class,
                int.class,
                int.class,
                Runnable.class);
        execute.invoke(scheduler, plugin, world, chunkX, chunkZ, task);
    }

    @Override
    public void runTaskAsync(JavaPlugin plugin, Runnable task) {
        try {
            asyncRunMethod.invoke(asyncScheduler, plugin, wrapTask(task));
        } catch (Exception e) {
            throw new RuntimeException("[ZeusGateway] Failed to run async task on Folia", e);
        }
    }

    @Override
    public void runTaskLater(JavaPlugin plugin, Runnable task, long delayTicks) {
        try {
            // Folia's runDelayed expects delay in ticks (minimum 1)
            long adjustedDelay = Math.max(1, delayTicks);
            globalRunDelayedMethod.invoke(globalRegionScheduler, plugin, wrapTask(task), adjustedDelay);
        } catch (Exception e) {
            throw new RuntimeException("[ZeusGateway] Failed to run delayed task on Folia", e);
        }
    }

    @Override
    public void runTaskLaterAsync(JavaPlugin plugin, Runnable task, long delayTicks) {
        try {
            // Convert ticks to milliseconds for async scheduler (1 tick = 50ms)
            long delayMs = Math.max(1, delayTicks) * 50L;
            asyncRunDelayedMethod.invoke(asyncScheduler, plugin, wrapTask(task), delayMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("[ZeusGateway] Failed to run delayed async task on Folia", e);
        }
    }

    @Override
    public void runTaskTimer(JavaPlugin plugin, Runnable task, long delayTicks, long periodTicks) {
        try {
            // Folia's runAtFixedRate expects initial delay and period in ticks (minimum 1)
            long adjustedDelay = Math.max(1, delayTicks);
            long adjustedPeriod = Math.max(1, periodTicks);
            globalRunAtFixedRateMethod.invoke(globalRegionScheduler, plugin, wrapTask(task),
                    adjustedDelay, adjustedPeriod);
        } catch (Exception e) {
            throw new RuntimeException("[ZeusGateway] Failed to run timer task on Folia", e);
        }
    }

    @Override
    public void runTaskTimerAsync(JavaPlugin plugin, Runnable task, long delayTicks, long periodTicks) {
        try {
            // Convert ticks to milliseconds for async scheduler
            long delayMs = Math.max(1, delayTicks) * 50L;
            long periodMs = Math.max(1, periodTicks) * 50L;
            asyncRunAtFixedRateMethod.invoke(asyncScheduler, plugin, wrapTask(task),
                    delayMs, periodMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            throw new RuntimeException("[ZeusGateway] Failed to run timer async task on Folia", e);
        }
    }
}
