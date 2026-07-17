package org.vennv.zeusFabric.provider;

public final class PollingPolicy {
    private PollingPolicy() {}

    public static boolean shouldSendKeepAlive(int playerAge) {
        return Math.floorMod(playerAge, 20) == 0;
    }

    public static boolean isCurrentGeneration(long expected, long current) {
        return expected == current;
    }
}
