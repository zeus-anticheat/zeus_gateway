package org.vennv;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Objects;

public final class Effect {

    private final byte effectId;
    private final byte amplifier;
    private final int duration;
    private final byte flags;

    public Effect(byte effectId, byte amplifier, int duration, byte flags) {
        this.effectId = effectId;
        this.amplifier = amplifier;
        this.duration = duration;
        this.flags = flags;
    }

    public void encode(ByteArrayOutputStream out) throws IOException {
        ByteBufferUtil.putByte(out, effectId);
        ByteBufferUtil.putByte(out, amplifier);
        ByteBufferUtil.putInt(out, duration);
        ByteBufferUtil.putByte(out, flags);
    }

    public byte getEffectId() {
        return effectId;
    }

    public byte getAmplifier() {
        return amplifier;
    }

    public int getDuration() {
        return duration;
    }

    public byte getFlags() {
        return flags;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Effect)) {
            return false;
        }
        Effect effect = (Effect) other;
        return effectId == effect.effectId
                && amplifier == effect.amplifier
                && duration == effect.duration
                && flags == effect.flags;
    }

    @Override
    public int hashCode() {
        return Objects.hash(effectId, amplifier, duration, flags);
    }
}
