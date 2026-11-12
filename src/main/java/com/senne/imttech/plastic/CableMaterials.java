package com.senne.imttech.plastic;

import com.senne.imttech.config.ImtConfig;

/**
 * De grondstoffen van de kabels (hoofdstuk 85, jouw wens van 12 november):
 * <pre>
 *   koperstaaf  --Crusher-------------------&gt;  koperdraad (3)
 *   kool + water --Polymerizer---------------&gt;  polymeer (2)
 *   polymeer    --oven-----------------------&gt;  plastic
 *
 *   koperdraad + plastic                     =  8 stroomkabels
 *   koperdraad + stroomkabel + plastic       =  4 netwerkkabels
 *   stroomkabel + netwerkkabel + plastic     =  2 PoE-kabels
 * </pre>
 * De recepten zelf staan in data/imttech/recipe (cable.json, network_cable.json, poe_cable.json, plastic.json). De getallen
 * van de machines staan bij [materials] in de config; hier worden ze afgerond tot hele getallen die op een stapel passen.
 */
public final class CableMaterials {
    private CableMaterials() {}

    /** Een emmer water, in mB. */
    public static final int BUCKET = 1000;

    /** Zoveel koperdraad maakt de Crusher van één koperstaaf (1 tot 64). */
    public static int wirePerIngot() {
        return Math.max(1, Math.min(64, Math.round(ImtConfig.Materials.COPPER_WIRE_PER_INGOT)));
    }

    /** Zoveel polymeer maakt de Polymerizer van één kool (1 tot 64). */
    public static int polymerPerCoal() {
        return Math.max(1, Math.min(64, Math.round(ImtConfig.Materials.POLYMER_PER_COAL)));
    }

    /** Zoveel water (mB) gaat er per kool uit de tank (nooit meer dan er in de tank past). */
    public static int waterPerCoal() {
        return Math.max(1, Math.min(tank(), Math.round(ImtConfig.Materials.WATER_PER_COAL)));
    }

    /** Zoveel ticks doet de Polymerizer over één kool. */
    public static int ticks() {
        return Math.max(1, Math.round(ImtConfig.Materials.POLYMERIZER_TICKS));
    }

    /**
     * Zoveel water (mB) past er in de tank: minstens één emmer, en hooguit 32000 (het scherm krijgt het getal als een
     * "short" door, en daar past niet meer in).
     */
    public static int tank() {
        return Math.max(BUCKET, Math.min(32000, Math.round(ImtConfig.Materials.POLYMERIZER_TANK)));
    }
}
