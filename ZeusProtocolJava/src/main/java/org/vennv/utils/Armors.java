package org.vennv.utils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Represents a complete set of armor pieces
 */
public class Armors {

    private Armor helmet;
    private Armor chestplate;
    private Armor leggings;
    private Armor boots;

    public Armors() {
        this.helmet = null;
        this.chestplate = null;
        this.leggings = null;
        this.boots = null;
    }

    public Armors(Armor helmet, Armor chestplate, Armor leggings, Armor boots) {
        this.helmet = helmet;
        this.chestplate = chestplate;
        this.leggings = leggings;
        this.boots = boots;
    }

    public Armor getHelmet() {
        return helmet;
    }

    public void setHelmet(Armor helmet) {
        this.helmet = helmet;
    }

    public Armor getChestplate() {
        return chestplate;
    }

    public void setChestplate(Armor chestplate) {
        this.chestplate = chestplate;
    }

    public Armor getLeggings() {
        return leggings;
    }

    public void setLeggings(Armor leggings) {
        this.leggings = leggings;
    }

    public Armor getBoots() {
        return boots;
    }

    public void setBoots(Armor boots) {
        this.boots = boots;
    }

    /**
     * Encode the armor set to a byte array output stream.
     * Format: 1 byte presence flags (bit 0=helmet, 1=chestplate, 2=leggings, 3=boots),
     * followed by each present armor piece encoded via {@link Armor#encode}.
     */
    public void encode(ByteArrayOutputStream out) throws IOException {
        // Write presence flags (1 byte: 4 bits for each armor piece)
        byte flags = 0;
        if (helmet != null) flags |= 0x01;
        if (chestplate != null) flags |= 0x02;
        if (leggings != null) flags |= 0x04;
        if (boots != null) flags |= 0x08;
        out.write(flags);

        // Write each armor piece if present
        if (helmet != null) {
            helmet.encode(out);
        }
        if (chestplate != null) {
            chestplate.encode(out);
        }
        if (leggings != null) {
            leggings.encode(out);
        }
        if (boots != null) {
            boots.encode(out);
        }
    }

    /**
     * Decode an armor set from a byte buffer
     */
    public static Armors decode(ByteBuffer buf) {
        // Read presence flags
        byte flags = buf.get();
        boolean hasHelmet = (flags & 0x01) != 0;
        boolean hasChestplate = (flags & 0x02) != 0;
        boolean hasLeggings = (flags & 0x04) != 0;
        boolean hasBoots = (flags & 0x08) != 0;

        // Read each armor piece if present
        Armor helmet = hasHelmet ? Armor.decode(buf) : null;
        Armor chestplate = hasChestplate ? Armor.decode(buf) : null;
        Armor leggings = hasLeggings ? Armor.decode(buf) : null;
        Armor boots = hasBoots ? Armor.decode(buf) : null;

        return new Armors(helmet, chestplate, leggings, boots);
    }

    @Override
    public String toString() {
        return (
            "Armors{" +
            "helmet=" +
            helmet +
            ", chestplate=" +
            chestplate +
            ", leggings=" +
            leggings +
            ", boots=" +
            boots +
            '}'
        );
    }
}
