package org.vennv.zeusFabric.listener;

public final class AuthoritativeTeleportDedupeTest {
    public static void main(String[] args) {
        AuthoritativeTeleportDedupe dedupe = new AuthoritativeTeleportDedupe();
        require(dedupe.shouldEmit("u", "overworld", 1.0, 2.0, 3.0, 10L,
                AuthoritativeTeleportDedupe.Source.OUTBOUND, 7L), "first outbound teleport suppressed");
        require(!dedupe.shouldEmit("u", "overworld", 1.0, 2.0, 3.0, 10L,
                AuthoritativeTeleportDedupe.Source.OUTBOUND, 7L), "outbound retry emitted twice");
        require(!dedupe.shouldEmit("u", "overworld", 1.0, 2.0, 3.0, 11L,
                AuthoritativeTeleportDedupe.Source.WORLD_CHANGE, 11L), "cross-world counterpart emitted twice");
        require(!dedupe.shouldEmit("u", "overworld", 1.0, 2.0, 3.0, 12L,
                AuthoritativeTeleportDedupe.Source.WORLD_CHANGE, 12L), "world-change retry emitted twice");
        require(dedupe.shouldEmit("u", "overworld", 1.0, 2.0, 3.0, 12L,
                AuthoritativeTeleportDedupe.Source.OUTBOUND, 8L), "distinct teleport ID suppressed");
        require(dedupe.shouldEmit("u", "overworld", 4.0, 2.0, 3.0, 12L,
                AuthoritativeTeleportDedupe.Source.OUTBOUND, 8L), "distinct destination suppressed");
        require(dedupe.shouldEmit("u", "the_nether", 4.0, 2.0, 3.0, 13L,
                AuthoritativeTeleportDedupe.Source.WORLD_CHANGE, 13L), "distinct world suppressed");
        require(dedupe.shouldEmit("v", "overworld", 1.0, 2.0, 3.0, 13L,
                AuthoritativeTeleportDedupe.Source.OUTBOUND, 7L), "different player suppressed");
        dedupe.remove("u");
        require(dedupe.shouldEmit("u", "overworld", 1.0, 2.0, 3.0, 14L,
                AuthoritativeTeleportDedupe.Source.OUTBOUND, 7L), "removed player state survived");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
