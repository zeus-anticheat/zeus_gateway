package org.vennv.zeusFabric.provider;

public final class PollingPolicyTest {
    public static void main(String[] args) {
        for (int player = 0; player < 4; player++) {
            require(PollingPolicy.shouldSendKeepAlive(20), "eligible player starved");
            require(!PollingPolicy.shouldSendKeepAlive(19), "early keepalive sent");
            require(!PollingPolicy.shouldSendKeepAlive(21), "late keepalive sent");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
