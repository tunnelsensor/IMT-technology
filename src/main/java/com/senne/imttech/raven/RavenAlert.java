package com.senne.imttech.raven;

/**
 * Hoe alert een bewaker is (hoofdstuk 60, jouw wens: bewakers zoals in Hitman). Een Raven-soldaat (en sinds stap 6
 * ook een robot of drone) zit altijd in één van deze toestanden; je spel krijgt hem mee om het "?" of "!" boven zijn
 * hoofd te tekenen en het oogje op je bril (allebei in hud/DetectionWidget).
 */
public enum RavenAlert {
    /** Niets aan de hand: hij loopt zijn route of staat op zijn post. */
    CALM,
    /** Hij hoorde iets, of zijn meter is halfvol: hij komt kijken ("?"). */
    SUSPICIOUS,
    /** Hij grijpt naar zijn radio en roept versterking (stap 4): één seconde pakken, dan praten ("!"). */
    CALLING,
    /** Hij is je kwijt en zoekt rond de plek waar hij je voor het laatst zag ("?"). */
    SEARCHING,
    /** Hij heeft je: het gevecht (RavenCombatGoal), "!". */
    COMBAT;

    private static final RavenAlert[] VALUES = values();

    /** De toestand met dit nummer (zoals je spel hem binnenkrijgt). */
    public static RavenAlert byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : CALM;
    }

    /** Is hij op zijn hoede (iets anders dan rustig)? */
    public boolean alerted() {
        return this != CALM;
    }
}
