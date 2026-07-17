package org.vennv.zeusGateway;

final class RuntimeSelector {
    private RuntimeSelector() {}

    static boolean useLegacy(String bukkitVersion) {
        if (bukkitVersion == null) return true;
        String[] parts = bukkitVersion.split("[-.]");
        if (parts.length < 2) return true;
        try {
            return Integer.parseInt(parts[0]) == 1 && Integer.parseInt(parts[1]) <= 13;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }
}
