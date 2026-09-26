package com.senne.starktech.shield;

import com.senne.starktech.edith.Tunable;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * De getallen van de schildkoepel, op één plek. Aanpassen? Verander ze hier en bouw opnieuw.
 *
 * Een koepel heeft twee knoppen in zijn scherm:
 * - het BEREIK (hoe groot, in blokken);
 * - de STERKTE (1 tot 5): hoeveel "schildkracht" hij heeft en hoe snel die bijlaadt.
 * Allebei maken ze hem duurder: groter en sterker kost meer stroom per tick.
 *
 * Elke treffer die de koepel tegenhoudt, kost schildkracht. Is die op, dan valt de koepel
 * even uit (DOWN_TICKS) en komt daarna vanzelf terug.
 */
public final class DomeSettings {
    private DomeSettings() {}

    /** De stralen die je kunt kiezen (in blokken). */
    public static final int[] RADII = {8, 12, 16, 24, 32};
    public static final int DEFAULT_RADIUS = 2;

    public static final int MIN_STRENGTH = 1;
    public static final int MAX_STRENGTH = 5;
    public static final int DEFAULT_STRENGTH = 2;

    /** Hoe lang hij uitvalt als zijn schildkracht op is (ticks). 200 ticks = 10 seconden. */
    @Tunable(section = "shields", comment = "How long a broken dome stays down before it comes back (ticks)")
    public static int DOWN_TICKS = 200;
    /** Na het uitvallen komt hij terug met dit deel van zijn schildkracht. */
    public static final float COMEBACK_SHIELD = 0.25f;

    // ---------------------------------------------------------------- Wat een treffer kost
    /** Een pijl, sneeuwbal, drietand of ander klein projectiel. */
    @Tunable(section = "shields", comment = "Dome strength lost per arrow or projectile it stops")
    public static int COST_PROJECTILE = 15;
    /** Een vuurbal (ghast, blaze, wither-schedel). */
    @Tunable(section = "shields", comment = "Dome strength lost per fireball it stops")
    public static int COST_FIREBALL = 60;
    /** Een monster of raiddrone naar buiten duwen (elke halve seconde). */
    public static final int COST_PUSH = 5;
    /** Een laserschot van een raiddrone of van de drone van iemand anders. */
    @Tunable(section = "shields", comment = "Dome strength lost per laser shot it stops")
    public static int COST_LASER = 30;
    /** Een precisiestraal van een satelliet. */
    @Tunable(section = "shields", comment = "Dome strength lost when it stops a satellite precision beam")
    public static int COST_SATELLITE_BEAM = 350;
    /** Eén inslag van een satellietbombardement. */
    @Tunable(section = "shields", comment = "Dome strength lost per bombardment impact it stops")
    public static int COST_SATELLITE_IMPACT = 150;
    /** Eén treffer van de schuivende satellietlaser. */
    public static final int COST_SATELLITE_SWEEP = 40;

    public static int clampRadius(int index) {
        return Mth.clamp(index, 0, RADII.length - 1);
    }

    public static int clampStrength(int strength) {
        return Mth.clamp(strength, MIN_STRENGTH, MAX_STRENGTH);
    }

    public static int radius(int index) {
        return RADII[clampRadius(index)];
    }

    /**
     * Stroom per tick: een kwart van de straal, maal de sterkte. Een koepel van 16 blokken op
     * sterkte 2 kost 8 per tick (twee zonnepanelen overdag); de grootste en sterkste 40.
     */
    public static int upkeep(int radiusIndex, int strength) {
        return Math.max(1, radius(radiusIndex) / 4 * clampStrength(strength));
    }

    /** Hoeveel schildkracht hij heeft: 400 per sterkte. */
    public static int maxShield(int strength) {
        return 400 * clampStrength(strength);
    }

    /** Hoeveel schildkracht er per tick bijkomt: 2 per sterkte. */
    public static int recharge(int strength) {
        return 2 * clampStrength(strength);
    }

    public static Component radiusLabel(int index) {
        return Component.translatable("screen.starktech.dome.blocks", radius(index));
    }
}
