package org.vennv.utils;

public final class ExternalForceFlags {
    private ExternalForceFlags() {}

    public static final int HAS_SLIME = 1 << 0;
    public static final int HAS_HONEY = 1 << 1;
    public static final int DIRECT_INTERSECT = 1 << 2;
    public static final int RETRACTING = 1 << 3;
    public static final int SERVER_VELOCITY_PACKET = 1 << 4;
    public static final int DAMAGE_BACKED = 1 << 5;
    public static final int ENVIRONMENT_BACKED = 1 << 6;
    public static final int VELOCITY_FIRST_BREAD = 1 << 7;
    public static final int VELOCITY_REQUIRED = 1 << 8;
}
