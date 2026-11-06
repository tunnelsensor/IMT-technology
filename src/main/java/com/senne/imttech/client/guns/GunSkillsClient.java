package com.senne.imttech.client.guns;

import com.senne.imttech.guns.FirearmType;

/**
 * De kant van jouw spel van de skills (hoofdstuk 52, stap 3): wat je geleerd hebt, voor de terugslag en het kruisje.
 * Zolang de skills er nog niet zijn: alles gewoon.
 */
public final class GunSkillsClient {
    private GunSkillsClient() {}

    /** De terugslag, maal dit (1 = gewoon). */
    public static float recoilFactor(FirearmType type) {
        return 1;
    }

    /** De spreiding van het kruisje, maal dit. */
    public static float spreadFactor(FirearmType type) {
        return 1;
    }
}
