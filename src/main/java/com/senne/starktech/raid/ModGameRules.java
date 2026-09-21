package com.senne.starktech.raid;

import net.minecraft.world.level.GameRules;

/**
 * De spelregels die deze mod toevoegt. Je zet ze aan of uit met /gamerule, net als de regels
 * van Minecraft zelf.
 */
public final class ModGameRules {
    private ModGameRules() {}

    /**
     * Mogen raiddrones blokken stukmaken?
     *
     * Standaard aan: zonder dat kun je je achter drie blokken steen verstoppen en is een raid
     * geen raid meer. Vind je het te ver gaan voor je basis, dan zet je hem uit met
     * {@code /gamerule starktechRaidGriefing false}.
     */
    public static final GameRules.Key<GameRules.BooleanValue> RAID_GRIEFING =
            GameRules.register("starktechRaidGriefing", GameRules.Category.MOBS,
                    GameRules.BooleanValue.create(true));

    /**
     * Zorgt dat deze klasse geladen wordt (en de regels dus aangemeld worden).
     * Spelregels moeten bestaan voordat er een wereld geladen wordt.
     */
    public static void register() {
        // Het aanmelden gebeurt hierboven, zodra de klasse geladen wordt.
    }
}
