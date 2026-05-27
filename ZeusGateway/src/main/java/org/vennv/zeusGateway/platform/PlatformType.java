package org.vennv.zeusGateway.platform;

public enum PlatformType {
    PAPER,
    SPIGOT,
    FOLIA;

    @Override
    public String toString() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
