package org.vennv.utils;

public class Enchantment {
    private final String name;
    private final byte level;

    public Enchantment(String name, byte level) {
        this.name = name;
        this.level = level;
    }

    public String getName() {
        return name;
    }

    public byte getLevel() {
        return level;
    }
}
