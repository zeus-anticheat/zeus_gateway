package org.vennv.zeusGateway.platform;

/**
 * Holds resolved server combat settings for adaptive detection.
 * Initialized once during plugin load, used by EventListener and ResyncTask
 * to send PacketServerConfig to sv_core.
 *
 * <p>Thread-safe: all fields are volatile and set once at startup.</p>
 */
public final class ServerCombatSettings {

    private static volatile float serverReach = 3.0f;
    private static volatile float attackCooldownTicks = 10.0f;
    private static volatile byte maxCps = 0;

    private ServerCombatSettings() {}

    /**
     * Initialize server combat settings. Call once during plugin load.
     *
     * @param reach    base melee reach in blocks (vanilla = 3.0)
     * @param cooldown attack cooldown in ticks (vanilla 1.9+ = 10.0, 1.8 = 0.0)
     * @param cps      server-enforced max CPS (0 = unlimited)
     */
    public static void init(float reach, float cooldown, byte cps) {
        serverReach = reach;
        attackCooldownTicks = cooldown;
        maxCps = cps;
    }

    public static float getServerReach() { return serverReach; }
    public static float getAttackCooldownTicks() { return attackCooldownTicks; }
    public static byte getMaxCps() { return maxCps; }
}
