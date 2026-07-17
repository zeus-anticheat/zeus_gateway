package org.vennv.zeusGateway.task;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.vennv.zeusGateway.ZeusGateway;

/**
 * Periodically sends the current state of all online players to the Zeus proxy.
 * This ensures that if the proxy restarts, it will eventually sync with the
 * actual state of players already online.
 */
public final class ResyncTask implements Runnable {

    private final ZeusGateway plugin;
    private final ChunkSyncTask chunkSyncTask;

    public ResyncTask(ZeusGateway plugin) {
        this.plugin = plugin;
        this.chunkSyncTask = new ChunkSyncTask(plugin);
    }

    @Override
    public void run() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            plugin.getSchedulerAdapter().runEntityTask(plugin, player, () -> {
                PlayerStateSnapshotService.sendResyncSnapshot(player);
                chunkSyncTask.syncPlayer(player);
            });
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
