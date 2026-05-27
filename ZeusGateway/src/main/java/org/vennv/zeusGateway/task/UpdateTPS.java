package org.vennv.zeusGateway.task;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;
import org.vennv.packets.PacketTPSServer;
import org.vennv.zeusGateway.provider.PacketQueue;

/**
 * Periodically samples the server TPS and pushes a {@link PacketTPSServer}
 * into the packet queue.
 * <p>
 * Works on Paper, Spigot (1.19+), and Folia. On older Spigot builds that
 * lack {@code Bukkit.getServer().getTPS()}, the task falls back to its own
 * tick-timing EMA estimate.
 */
public class UpdateTPS implements Runnable {

    /**
     * How many server ticks between each invocation of this task.
     * Must match the period passed to {@code runTaskTimer(…, TICK_PERIOD, TICK_PERIOD)}.
     */
    private static final long TICK_PERIOD = 20L;

    private long lastRun = 0;
    private double emaTps = 20.0;

    /** Cached reflective handle – {@code null} means "not yet resolved". */
    private static volatile Boolean hasBukkitTps = null;
    private static Method getTpsMethod = null;

    @Override
    public void run() {
        // Prefer the server-reported TPS when available (Paper & Spigot 1.19+)
        double serverTps = getServerTps();
        if (serverTps > 0) {
            PacketQueue.push(new PacketTPSServer(Math.min(20.0, serverTps)));
            return;
        }

        // ── Fallback: estimate TPS from wall-clock timing ──
        long now = System.nanoTime();

        if (lastRun == 0) {
            lastRun = now;
            return;
        }

        double elapsedMs = (now - lastRun) / 1_000_000.0;
        lastRun = now;

        // Prevent division by zero / absurd spikes
        if (elapsedMs <= 0) {
            elapsedMs = TICK_PERIOD * 50.0; // expected interval
        }

        // This task runs every TICK_PERIOD ticks.
        // If TICK_PERIOD ticks took `elapsedMs` ms, then one tick took elapsedMs/TICK_PERIOD ms,
        // so TPS = 1000 / (elapsedMs / TICK_PERIOD) = TICK_PERIOD * 1000 / elapsedMs.
        double rawTps = Math.min(20.0, TICK_PERIOD * 1000.0 / elapsedMs);

        // Exponential moving average – smooths out momentary hiccups
        emaTps = 0.2 * rawTps + 0.8 * emaTps;

        double tps = Math.max(0.0, Math.min(20.0, emaTps));

        PacketQueue.push(new PacketTPSServer(tps));
    }

    /**
     * Attempts to read the server's own TPS value.
     *
     * @return the 1-minute TPS reported by the server, or {@code -1} if unavailable
     */
    private double getServerTps() {
        // Fast path – we already know whether the method exists
        if (hasBukkitTps != null && !hasBukkitTps) {
            return -1;
        }

        try {
            if (hasBukkitTps == null) {
                // One-time reflective probe so we never call a missing method twice
                try {
                    getTpsMethod = Bukkit.getServer()
                        .getClass()
                        .getMethod("getTPS");
                    hasBukkitTps = true;
                } catch (NoSuchMethodException e) {
                    hasBukkitTps = false;
                    return -1;
                }
            }

            if (getTpsMethod != null) {
                double[] tpsArray = (double[]) getTpsMethod.invoke(
                    Bukkit.getServer()
                );
                if (tpsArray != null && tpsArray.length > 0) {
                    return tpsArray[0]; // 1-minute average
                }
            }
        } catch (Exception ignored) {
            // Reflection call failed – disable for future invocations
            hasBukkitTps = false;
        }

        return -1;
    }
}
