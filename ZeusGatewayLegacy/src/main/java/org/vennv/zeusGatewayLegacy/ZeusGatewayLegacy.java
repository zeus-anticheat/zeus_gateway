package org.vennv.zeusGatewayLegacy;

import org.bukkit.plugin.java.JavaPlugin;

public final class ZeusGatewayLegacy extends JavaPlugin {
    private LegacyGatewaySession runtime;

    @Override
    public void onEnable() {
        runtime = LegacyGatewaySession.start(this);
        getLogger().info("[ZeusGatewayLegacy] Plugin enabled.");
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.close();
            runtime = null;
        }
    }
}
