package org.vennv.zeusFabric.provider;

import java.util.UUID;

public final class CaptureIdentityTest {
    public static void main(String[] args) {
        String uuid = "01234567-89ab-cdef-0123-456789abcdef";
        String first = CaptureIdentity.captureSubjectId(uuid, "session-one");
        require(first.equals("subject-67c0b4c3400089d0"), "core-compatible subject hash changed");
        require(first.equals(CaptureIdentity.captureSubjectId(uuid, "session-one")), "same-session subject changed");
        require(!first.equals(CaptureIdentity.captureSubjectId(uuid, "session-two")), "subject reused across sessions");
        long firstProcess = CaptureIdentity.playerHash(uuid, new byte[] {1});
        require(firstProcess == CaptureIdentity.playerHash(uuid, new byte[] {1}), "same-process hash changed");
        require(firstProcess != CaptureIdentity.playerHash(uuid, new byte[] {2}), "hash reused across processes");
        UUID raw = UUID.fromString(uuid);
        require(firstProcess != raw.getLeastSignificantBits(), "hash exposed raw UUID bits");
        require(firstProcess != raw.getMostSignificantBits(), "hash exposed raw UUID bits");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
