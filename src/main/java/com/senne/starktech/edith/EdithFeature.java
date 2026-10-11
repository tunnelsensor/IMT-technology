package com.senne.starktech.edith;

import com.senne.starktech.config.StarkConfig;

/**
 * Functies van de bril die je aan en uit kunt zetten (via het menu of een sneltoets).
 * Welke functies aan staan wordt in de bril zelf opgeslagen, als een getal waarin
 * elke functie één "bit" is (bitmask).
 */
public enum EdithFeature {
    // Het verbruik staat er als "() -> getal": zo lezen we het elke keer opnieuw, en telt een
    // ander getal uit het configbestand (starktech.toml) meteen mee.
    NIGHT_VISION("night_vision", () -> StarkConfig.Glasses.DRAIN_NIGHT_VISION, false),
    THREAT_SCAN("threat_scan", () -> StarkConfig.Glasses.DRAIN_THREAT_SCAN, false),
    TARGET_ANALYSIS("target_analysis", () -> StarkConfig.Glasses.DRAIN_TARGET_ANALYSIS, true),
    // Fase 2. Nieuwe functies altijd ONDERAAN toevoegen, anders kloppen opgeslagen brillen niet meer.
    GUARD_DRONES("guard_drones", () -> StarkConfig.Drones.GUARD_DRAIN, false),
    // Fase 11: het persoonlijke rayshield.
    SHIELD("shield", () -> StarkConfig.Shields.PERSONAL_DRAIN, false);

    private final String id;
    private final java.util.function.IntSupplier drainPerSecond;
    private final boolean enabledByDefault;

    EdithFeature(String id, java.util.function.IntSupplier drainPerSecond, boolean enabledByDefault) {
        this.id = id;
        this.drainPerSecond = drainPerSecond;
        this.enabledByDefault = enabledByDefault;
    }

    public int bit() {
        return 1 << ordinal();
    }

    public int drainPerSecond() {
        return drainPerSecond.getAsInt();
    }

    /** Sleutel voor de vertaling, bv. "edith.starktech.feature.night_vision". */
    public String translationKey() {
        return "edith.starktech.feature." + id;
    }

    public static int defaultMask() {
        int mask = 0;
        for (EdithFeature feature : values()) {
            if (feature.enabledByDefault) {
                mask |= feature.bit();
            }
        }
        return mask;
    }
}
