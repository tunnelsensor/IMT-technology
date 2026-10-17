package com.senne.imttech.shield;

import com.senne.imttech.config.ImtConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Rekenhulp voor de schildkoepel: zoekt bij een maat en een sterkte de juiste getallen op. De getallen
 * zelf staan in {@link ImtConfig.Dome} (en wat hij aan stroom kost in ImtConfig.Power.DOME_UPKEEP).
 *
 * Een koepel heeft twee knoppen in zijn scherm:
 * - de MAAT (een nummer uit ImtConfig.Dome.RADII: hoe groot, in blokken);
 * - de STERKTE (1 tot 5): hoeveel "schildkracht" hij heeft en hoe snel die bijlaadt.
 * Allebei maken ze hem duurder: groter en sterker kost meer stroom per tick.
 *
 * Elke treffer die de koepel tegenhoudt, kost schildkracht. Is die op, dan valt de koepel
 * even uit (DOWN_TICKS) en komt daarna vanzelf terug.
 */
public final class DomeSettings {
    private DomeSettings() {}

    public static int clampRadius(int index) {
        return Mth.clamp(index, 0, ImtConfig.Dome.RADII.length - 1);
    }

    public static int clampStrength(int strength) {
        return Mth.clamp(strength, ImtConfig.Dome.MIN_STRENGTH, ImtConfig.Dome.MAX_STRENGTH);
    }

    public static int radius(int index) {
        return Math.round(ImtConfig.Dome.RADII[clampRadius(index)]);
    }

    /**
     * Stroom per tick: het getal voor deze maat, maal de sterkte. Een koepel van 16 blokken op
     * sterkte 2 kost 4 x 2 = 8 per tick (twee zonnepanelen overdag); de grootste en sterkste 40.
     */
    public static double upkeep(int radiusIndex, int strength) {
        float[] perStrength = ImtConfig.Power.DOME_UPKEEP;
        int size = Mth.clamp(radiusIndex, 0, perStrength.length - 1);
        return Math.max(0, perStrength[size] * clampStrength(strength));
    }

    /** Hoeveel schildkracht hij heeft bij deze sterkte. */
    public static int maxShield(int strength) {
        float[] shield = ImtConfig.Dome.MAX_SHIELD;
        return Math.round(shield[Mth.clamp(clampStrength(strength) - 1, 0, shield.length - 1)]);
    }

    /** Hoeveel schildkracht er per tick bijkomt bij deze sterkte. */
    public static int recharge(int strength) {
        float[] recharge = ImtConfig.Dome.RECHARGE;
        return Math.round(recharge[Mth.clamp(clampStrength(strength) - 1, 0, recharge.length - 1)]);
    }

    public static Component radiusLabel(int index) {
        return Component.translatable("screen.imttech.dome.blocks", radius(index));
    }
}
