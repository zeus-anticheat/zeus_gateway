package org.vennv.zeusGatewayLegacy;

import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.vennv.packets.PacketServerConfig;

final class LegacyServerIdentity {
    private static String platform = "unknown";
    private static String physicsFingerprint = "unattested";

    private LegacyServerIdentity() {}

    static void configure(JavaPlugin plugin) {
        String brand = serverBrand();
        platform = platformForBrand(brand, valueOrDefault(
                System.getenv("ZEUS_PLATFORM"), plugin.getConfig().getString("identity.platform", "auto")));
        physicsFingerprint = physicsFingerprint(System.getenv("ZEUS_PHYSICS_FINGERPRINT"),
                plugin.getConfig().getString("identity.physics-fingerprint", "unattested"));
    }

    static PacketServerConfig serverConfig(Player player, long timestamp) {
        String version = serverVersion();
        int protocol = serverProtocol();
        int clientProtocol = clientProtocol(player);
        float movementSpeed;
        try {
            movementSpeed = player.getWalkSpeed() / 2.0f;
        } catch (Throwable ignored) {
            movementSpeed = 0.1f;
        }
        // Combat reach, cooldown, and CPS are resolved from live player
        // attributes/core policy; never publish legacy fallback settings.
        return new PacketServerConfig(timestamp, player.getUniqueId().toString(), player.getName(),
                movementSpeed, protocol, version,
                serverBrand(), platform, physicsFingerprint, clientProtocol,
                clientVersion(clientProtocol), "", "legacy");
    }

    static float cooldownTicks(int protocol, float override) {
        return override >= 0.0f ? override : protocol <= 47 ? 0.0f : 10.0f;
    }

    static int serverProtocol() {
        try {
            com.github.retrooper.packetevents.manager.server.ServerVersion version =
                    com.github.retrooper.packetevents.PacketEvents.getAPI().getServerManager().getVersion();
            if (version != null && version.getProtocolVersion() > 0) return version.getProtocolVersion();
        } catch (RuntimeException | LinkageError ignored) {
        }
        return serverProtocol(serverVersion());
    }

    static int clientProtocol(Player player) {
        try {
            com.github.retrooper.packetevents.protocol.player.ClientVersion version =
                    com.github.retrooper.packetevents.PacketEvents.getAPI()
                            .getPlayerManager().getClientVersion(player);
            if (version != null && version.getProtocolVersion() > 0) return version.getProtocolVersion();
        } catch (RuntimeException | LinkageError ignored) {
        }
        return serverProtocol();
    }

    private static String clientVersion(int protocol) {
        com.github.retrooper.packetevents.protocol.player.ClientVersion version =
                com.github.retrooper.packetevents.protocol.player.ClientVersion.getById(protocol);
        return version == null ? "unknown" : version.getReleaseName();
    }

    private static String serverBrand() {
        String name = Bukkit.getName();
        return name == null ? "unknown" : name.toLowerCase(Locale.ROOT);
    }

    static String platformForBrand(String brand, String override) {
        if (override != null && !override.trim().isEmpty()
                && !"auto".equalsIgnoreCase(override.trim())) {
            return override.trim().toLowerCase(Locale.ROOT);
        }
        String normalized = brand == null ? "" : brand.toLowerCase(Locale.ROOT);
        if (normalized.contains("paper")) return "paper";
        if (normalized.contains("spigot")) return "spigot";
        if (normalized.contains("craftbukkit") || normalized.contains("bukkit")) return "bukkit";
        return normalized.isEmpty() ? "unknown" : normalized;
    }

    static String physicsFingerprint(String environment, String configured) {
        return valueOrDefault(environment, valueOrDefault(configured, "unattested"));
    }

    private static String serverVersion() {
        String version = Bukkit.getVersion();
        if (version == null) return "1.8";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?:MC: |minecraft server version )?(1\\.\\d+(?:\\.\\d+)?)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(version);
        return matcher.find() ? matcher.group(1) : "1.8";
    }

    static int serverProtocol(String version) {
        if (version.startsWith("1.13.2")) return 404;
        if (version.startsWith("1.13.1")) return 401;
        if (version.startsWith("1.13")) return 393;
        if (version.startsWith("1.12.2")) return 340;
        if (version.startsWith("1.12.1")) return 338;
        if (version.startsWith("1.12")) return 335;
        if (version.startsWith("1.11.1") || version.startsWith("1.11.2")) return 316;
        if (version.startsWith("1.11")) return 315;
        if (version.startsWith("1.10")) return 210;
        if (version.startsWith("1.9.4") || version.startsWith("1.9.3")) return 110;
        if (version.startsWith("1.9.2")) return 109;
        if (version.startsWith("1.9.1")) return 108;
        if (version.startsWith("1.9")) return 107;
        return 47;
    }

    private static String valueOrDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
