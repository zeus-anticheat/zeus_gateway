package org.vennv.zeusGateway.debug;

import org.vennv.zeusGateway.ZeusGateway;

/**
 * Loaded only on Paper/Folia so Bukkit/Spigot does not resolve Paper Command API classes.
 */
public final class PaperDebugCommandRegistrar {
    private PaperDebugCommandRegistrar() {}

    public static void register(ZeusGateway plugin, ZeusDebugCommand delegate) {
        plugin.registerCommand("zeusdebug", new PaperZeusDebugCommand(delegate));
    }
}
