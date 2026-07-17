package org.vennv.zeusFabric.provider;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

public final class CaptureIdentity {
    private static final byte[] PROCESS_SALT = new byte[32];

    static {
        new SecureRandom().nextBytes(PROCESS_SALT);
    }

    private CaptureIdentity() {}

    public static boolean hasSharedSalt() {
        String salt = System.getenv("ZEUS_CAPTURE_SUBJECT_SALT");
        return salt != null && !salt.isBlank();
    }

    public static String captureSubjectId(String playerUuid) {
        String salt = System.getenv("ZEUS_CAPTURE_SUBJECT_SALT");
        return salt == null || salt.isBlank() ? "" : captureSubjectId(playerUuid, salt);
    }

    public static long playerHash(String playerUuid) {
        return playerHash(playerUuid, PROCESS_SALT);
    }

    static String captureSubjectId(String playerUuid, String sessionSalt) {
        long hash = 0xcbf29ce484222325L;
        for (byte value : (sessionSalt + ":" + playerUuid).getBytes(StandardCharsets.UTF_8)) {
            hash ^= value & 0xffL;
            hash *= 0x100000001b3L;
        }
        return "subject-" + String.format(java.util.Locale.ROOT, "%016x", hash);
    }

    static long playerHash(String playerUuid, byte[] processSalt) {
        return ByteBuffer.wrap(digest(processSalt, playerUuid)).getLong();
    }

    private static byte[] digest(byte[] salt, String playerUuid) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            digest.update((byte) 0);
            return digest.digest(playerUuid.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
