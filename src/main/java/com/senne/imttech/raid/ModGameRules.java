package com.senne.imttech.raid;

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
     * {@code /gamerule imttechRaidGriefing false}.
     */
    public static final GameRules.Key<GameRules.BooleanValue> RAID_GRIEFING =
            GameRules.register("imttechRaidGriefing", GameRules.Category.MOBS,
                    GameRules.BooleanValue.create(true));

    /**
     * Hoofdstuk 75 (jouw wens: "maak ook een gamerule die deze raiddrones aan en uit laat togglen"): komen er raids?
     *
     * Standaard aan. Met {@code /gamerule imttechRaidDrones false} komt er geen golf meer (niet vanzelf, niet bij de
     * toren en niet bij een wrak), en elke raiddrone die al vliegt, stijgt op en vertrekt (zoals na je dood). Weer aan:
     * de klok begint opnieuw, dus er komt niet meteen een golf. De config ({@code rules.raidsEnabled}) moet ook aan staan.
     */
    public static final GameRules.Key<GameRules.BooleanValue> RAID_DRONES =
            GameRules.register("imttechRaidDrones", GameRules.Category.MOBS,
                    GameRules.BooleanValue.create(true));

    /** Mogen er raids zijn: de config en de gamerule allebei aan? */
    public static boolean raidsOn(net.minecraft.server.level.ServerLevel level) {
        return com.senne.imttech.config.ImtConfig.Rules.RAIDS_ENABLED && level.getGameRules().getBoolean(RAID_DRONES);
    }

    /**
     * Zorgt dat deze klasse geladen wordt (en de regels dus aangemeld worden).
     * Spelregels moeten bestaan voordat er een wereld geladen wordt.
     */
    public static void register() {
        // Het aanmelden gebeurt hierboven, zodra de klasse geladen wordt.
    }
}
