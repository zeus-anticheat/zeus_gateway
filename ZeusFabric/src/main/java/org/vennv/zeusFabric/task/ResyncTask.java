package org.vennv.zeusFabric.task;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

public class ResyncTask {

    private int tickCounter = 0;

    public void tick(MinecraftServer server) {
        tickCounter++;
        // Run every 200 ticks (10 seconds)
        if (tickCounter >= 200) {
            tickCounter = 0;
            run(server);
        }
    }

    private void run(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerStateSnapshotService.sendResyncSnapshot(player);
        }
    }
}
