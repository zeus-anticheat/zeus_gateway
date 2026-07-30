package org.vennv.zeusGateway.platform;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class ServerIdentity {
    private ServerIdentity() {}

    public static int serverProtocol() {
        try {
            com.github.retrooper.packetevents.manager.server.ServerVersion version =
                    PacketEvents.getAPI().getServerManager().getVersion();
            if (version != null && version.getProtocolVersion() > 0) {
                return version.getProtocolVersion();
            }
        } catch (RuntimeException | LinkageError ignored) {
        }
        if (ServerVersion.major() == 1 && ServerVersion.minor() == 20) {
            return ServerVersion.patch() >= 5 ? 766 : ServerVersion.patch() >= 3 ? 765 : 764;
        }
        if (ServerVersion.major() == 1 && ServerVersion.minor() == 21) {
            if (ServerVersion.patch() >= 11) return 774;
            if (ServerVersion.patch() >= 9) return 773;
            if (ServerVersion.patch() >= 7) return 772;
            if (ServerVersion.patch() >= 6) return 771;
            if (ServerVersion.patch() >= 5) return 770;
            if (ServerVersion.patch() >= 4) return 769;
            if (ServerVersion.patch() >= 2) return 768;
            return 767;
        }
        return 0;
    }

    public static int clientProtocol(Player player) {
        int fallback = serverProtocol();
        ClientVersion version = PacketEvents.getAPI().getPlayerManager().getClientVersion(player);
        if (version != null && version.getProtocolVersion() > 0) {
            fallback = version.getProtocolVersion();
        }
        return clientProtocol(player.getUniqueId(), fallback);
    }

    public static int clientProtocol(UUID uuid, int fallback) {
        try {
            Class<?> via = Class.forName("com.viaversion.viaversion.api.Via");
            Object api = via.getMethod("getAPI").invoke(null);
            Object value = api.getClass().getMethod("getPlayerVersion", UUID.class)
                    .invoke(api, uuid);
            if (value instanceof Number && ((Number) value).intValue() > 0) {
                return ((Number) value).intValue();
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    public static String clientVersion(int clientProtocol) {
        if (clientProtocol <= 0) return "unknown";
        ClientVersion version = ClientVersion.getById(clientProtocol);
        if (version == null || version.getProtocolVersion() != clientProtocol) {
            return "protocol-" + clientProtocol;
        }
        String releaseName = version.getReleaseName();
        return releaseName == null || releaseName.isEmpty()
                ? "protocol-" + clientProtocol
                : releaseName;
    }

    public static String serverVersion() {
        return ServerVersion.major() + "." + ServerVersion.minor() + "." + ServerVersion.patch();
    }

    public static String serverBrand() {
        try {
            return Bukkit.getName().toLowerCase(java.util.Locale.ROOT);
        } catch (Throwable ignored) {
            return "unknown";
        }
    }

    public static String platform() {
        String value = System.getenv("ZEUS_PLATFORM");
        if (value != null && !value.isEmpty()) return value;
        return serverBrand().contains("spigot") ? "spigot" : "paper";
    }

    public static String physicsFingerprint() {
        String value = System.getenv("ZEUS_PHYSICS_FINGERPRINT");
        return value == null || value.isEmpty() ? "vanilla" : value;
    }

    public static String translationBehaviorFingerprint(UUID uuid, int clientProtocol) {
        String configured = System.getenv("ZEUS_TRANSLATION_BEHAVIOR_FINGERPRINT");
        return configured == null || configured.isEmpty() ? "" : configured;
    }
}
