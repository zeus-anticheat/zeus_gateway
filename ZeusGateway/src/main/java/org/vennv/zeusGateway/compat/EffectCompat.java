package org.vennv.zeusGateway.compat;

import org.bukkit.potion.PotionEffectType;
import org.vennv.zeusGateway.platform.ServerVersion;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Version-compatible accessor for potion effect key strings.
 * <p>
 * On 1.18+: {@code PotionEffectType.getKey().getKey()} returns lowercase
 * namespaced keys like {@code "speed"}, {@code "jump_boost"}.
 * <p>
 * On 1.8–1.17: {@code PotionEffectType.getName()} returns legacy uppercase
 * names like {@code "SPEED"}, {@code "INCREASE_DAMAGE"}, {@code "SLOW"}.
 * This class maps those legacy names to the modern keys so that
 * {@link org.vennv.utils.EffectType#fromKey(String)} always receives
 * consistent input.
 */
public final class EffectCompat {

    private EffectCompat() {}

    /**
     * Map of legacy {@code PotionEffectType.getName()} values that differ
     * from the modern {@code getKey().getKey()} equivalent.
     * <p>
     * Names that lowercase-match their modern keys (e.g. "SPEED" → "speed")
     * need no explicit mapping.
     */
    private static final Map<String, String> LEGACY_NAME_MAP;

    static {
        LEGACY_NAME_MAP = new HashMap<>();
        LEGACY_NAME_MAP.put("SLOW",              "slowness");
        LEGACY_NAME_MAP.put("FAST_DIGGING",      "haste");
        LEGACY_NAME_MAP.put("SLOW_DIGGING",      "mining_fatigue");
        LEGACY_NAME_MAP.put("INCREASE_DAMAGE",   "strength");
        LEGACY_NAME_MAP.put("HEAL",              "instant_health");
        LEGACY_NAME_MAP.put("HARM",              "instant_damage");
        LEGACY_NAME_MAP.put("JUMP",              "jump_boost");
        LEGACY_NAME_MAP.put("CONFUSION",         "nausea");
        LEGACY_NAME_MAP.put("DAMAGE_RESISTANCE", "resistance");
        LEGACY_NAME_MAP.put("UNLUCK",            "unluck");
    }

    /**
     * Returns the effect key string in modern lowercase format compatible
     * with {@link org.vennv.utils.EffectType#fromKey(String)}.
     *
     * <ul>
     *   <li>1.18+: delegates to {@code type.getKey().getKey()}</li>
     *   <li>1.8–1.17: uses {@code type.getName()} + legacy-to-modern mapping</li>
     * </ul>
     *
     * @param type the potion effect type (never {@code null})
     * @return a lowercase key such as {@code "speed"}, {@code "jump_boost"}, etc.
     */
    @SuppressWarnings("deprecation")
    public static String getEffectKey(PotionEffectType type) {
        if (ServerVersion.HAS_POTION_KEY) {
            try {
                return type.getKey().getKey();
            } catch (NoSuchMethodError | NoClassDefFoundError e) {
                // Fall through to legacy path
            }
        }

        // Pre-1.18 fallback: getName() returns uppercase legacy names
        String legacyName = type.getName();
        String mapped = LEGACY_NAME_MAP.get(legacyName);
        if (mapped != null) {
            return mapped;
        }
        // For names that match directly when lowercased
        return legacyName.toLowerCase(Locale.ROOT);
    }
}
