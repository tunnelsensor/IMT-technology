package com.senne.imttech.imt;

import com.senne.imttech.config.ImtConfig;

/**
 * Functies van de bril die je aan en uit kunt zetten (via het menu of een sneltoets).
 * Welke functies aan staan wordt in de bril zelf opgeslagen, als een getal waarin
 * elke functie één "bit" is (bitmask).
 */
public enum ImtFeature {
    // Het verbruik staat er als "() -> getal": zo lezen we het elke keer opnieuw, en telt een
    // ander getal uit het configbestand (imttech.toml) meteen mee.
    NIGHT_VISION("night_vision", () -> ImtConfig.Glasses.DRAIN_NIGHT_VISION, false),
    THREAT_SCAN("threat_scan", () -> ImtConfig.Glasses.DRAIN_THREAT_SCAN, false),
    TARGET_ANALYSIS("target_analysis", () -> ImtConfig.Glasses.DRAIN_TARGET_ANALYSIS, true),
    // Fase 2. Nieuwe functies altijd ONDERAAN toevoegen, anders kloppen opgeslagen brillen niet meer.
    GUARD_DRONES("guard_drones", () -> ImtConfig.Drones.GUARD_DRAIN, false),
    // Fase 11: het persoonlijke rayshield.
    SHIELD("shield", () -> ImtConfig.Shields.PERSONAL_DRAIN, false);

    private final String id;
    private final java.util.function.DoubleSupplier drainPerSecond;
    private final boolean enabledByDefault;

    ImtFeature(String id, java.util.function.DoubleSupplier drainPerSecond, boolean enabledByDefault) {
        this.id = id;
        this.drainPerSecond = drainPerSecond;
        this.enabledByDefault = enabledByDefault;
    }

    public int bit() {
        return 1 << ordinal();
    }

    /** Wat deze functie per seconde kost (mag een kommagetal zijn, bv. 0.5). */
    public double drainPerSecond() {
        return drainPerSecond.getAsDouble();
    }

    /** Sleutel voor de vertaling, bv. "imt.imttech.feature.night_vision". */
    public String translationKey() {
        return "imt.imttech.feature." + id;
    }

    public static int defaultMask() {
        int mask = 0;
        for (ImtFeature feature : values()) {
            if (feature.enabledByDefault) {
                mask |= feature.bit();
            }
        }
        return mask;
    }
}
