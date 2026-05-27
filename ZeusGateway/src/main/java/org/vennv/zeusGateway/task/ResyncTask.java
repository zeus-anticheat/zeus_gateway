package org.vennv.zeusGateway.task;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Periodically sends the current state of all online players to the Zeus proxy.
 * This ensures that if the proxy restarts, it will eventually sync with the
 * actual state of players already online.
 */
public final class ResyncTask implements Runnable {

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerStateSnapshotService.sendResyncSnapshot(player);
        }
    }

    /**
     * Converts Bukkit GameMode to Minecraft protocol gamemode ID.
     * Kept for older call sites and tests.
     */
    public static int gameModeToProtocolId(GameMode mode) {
        return PlayerStateSnapshotService.gameModeToProtocolId(mode);
    }
}
