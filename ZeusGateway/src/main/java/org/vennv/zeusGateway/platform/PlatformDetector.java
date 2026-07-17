package org.vennv.zeusGateway.platform;

import java.util.logging.Logger;

public final class PlatformDetector {

    private static PlatformType cachedType = null;

    private PlatformDetector() {
    }

    public static PlatformType detect() {
        if (cachedType != null) {
            return cachedType;
        }

        if (isFolia()) {
            cachedType = PlatformType.FOLIA;
        } else if (isPaper()) {
            cachedType = PlatformType.PAPER;
        } else {
            cachedType = PlatformType.SPIGOT;
        }

        return cachedType;
    }

    public static PlatformType detect(Logger logger) {
        PlatformType type = detect();
        logger.info("[ZeusGateway] Detected platform: " + type);

        // Initialize version detection and feature flags
        ServerVersion.init(logger);

        return type;
    }

    public static boolean isFolia() {
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    public static boolean isPaper() {
        for (String marker : new String[] {
                "io.papermc.paper.ServerBuildInfo",
                "com.destroystokyo.paper.PaperConfig"
        }) {
            try {
                Class.forName(marker);
                return true;
            } catch (ClassNotFoundException ignored) {
            }
        }
        return false;
    }

    public static boolean isSpigot() {
        return !isPaper() && !isFolia();
    }

    public static PlatformType getCachedType() {
        if (cachedType == null) {
            return detect();
        }
        return cachedType;
    }
}
