package com.senne.starktech.edith;

import org.jetbrains.annotations.Nullable;

/**
 * Alles wat je E.D.I.T.H. kunt vragen te doen. De client (jouw spel) stuurt een actie naar
 * de server, en de server controleert of het mag (draag je de bril, ben je de eigenaar,
 * is er genoeg energie?) en voert het dan uit.
 *
 * De volgorde is ook het nummer dat over het netwerk gaat (zie EdithActionPayload).
 */
public enum EdithAction {
    TOGGLE_NIGHT_VISION("toggle_night_vision", EdithFeature.NIGHT_VISION),
    TOGGLE_THREAT_SCAN("toggle_threat_scan", EdithFeature.THREAT_SCAN),
    TOGGLE_TARGET_ANALYSIS("toggle_target_analysis", EdithFeature.TARGET_ANALYSIS),
    ORE_SCAN("ore_scan", null),
    AUTHORIZE_TRANSFER("authorize_transfer", null),
    // Fase 2
    LAUNCH_DRONES("launch_drones", null),
    TOGGLE_GUARD_DRONES("toggle_guard_drones", EdithFeature.GUARD_DRONES),
    SATELLITE_STRIKE("satellite_strike", null),
    CLEAR_MARKS("clear_marks", null),
    // Fase 3
    DRONE_CAMERA("drone_camera", null),
    // Fase 5
    /** Een losse drone op een doelwit afsturen. */
    DRONE_STRIKE("drone_strike", null),
    // Fase 6
    /** Spionagedrone starten of terugroepen. */
    SPY_DRONE("spy_drone", null),
    /** Live meekijken door de spionagedrone (of stoppen). */
    SPY_VIEW("spy_view", null),
    /** Het rapport van de spionagedrone openen. */
    SPY_REPORT("spy_report", null),
    /** Alles afbreken: zwerm, losse drone, escorte en satelliet stoppen. */
    STAND_DOWN("stand_down", null),
    // Fase 7
    /** Laat je oppikken en vlieg zelf (of zet je weer neer). */
    TRANSPORT_RIDE("transport_ride", null),
    /** Laat je naar je bestemming vliegen. */
    TRANSPORT_AUTOPILOT("transport_autopilot", null),
    /** Stuur spullen met een vrachtdrone naar je bestemming. */
    CARGO_DRONE("cargo_drone", null),
    /** Pak iemand of iets op en zet het bij je bestemming neer. */
    CARRY_ENTITY("carry_entity", null),
    // Fase 8: de standen van de satelliet
    /** Volgende stand van de satelliet. */
    SATELLITE_MODE("satellite_mode", null),
    SATELLITE_PRECISION("satellite_precision", null),
    SATELLITE_BOMBARDMENT("satellite_bombardment", null),
    SATELLITE_SWEEP("satellite_sweep", null),
    // Fase 10
    /** De speler waar je naar kijkt hacken (of de hack stoppen). */
    HACK_PLAYER("hack_player", null),
    /** Alle hacks stoppen. */
    HACK_STOP("hack_stop", null),
    // Fase 11
    /** Persoonlijk schild aan/uit. */
    TOGGLE_SHIELD("toggle_shield", EdithFeature.SHIELD),
    // Fase 12: illusies
    /** Valse muur projecteren (van het blok in je hand). */
    ILLUSION_WALL("illusion_wall", null),
    /** Nep-leger projecteren (van het spawn-ei in je hand). */
    ILLUSION_ARMY("illusion_army", null),
    /** Alles in het veld onzichtbaar maken. */
    ILLUSION_CLOAK("illusion_cloak", null),
    /** Alle illusies stoppen. */
    ILLUSION_STOP("illusion_stop", null),
    // Fase 15: het harnas
    /** Een repulsorstoot uit je handpalm. */
    REPULSOR("repulsor", null);

    private final String id;
    private final @Nullable EdithFeature toggles;

    EdithAction(String id, @Nullable EdithFeature toggles) {
        this.id = id;
        this.toggles = toggles;
    }

    /** De functie die deze actie aan/uit zet, of null als het een eenmalige actie is. */
    public @Nullable EdithFeature toggles() {
        return toggles;
    }

    public String translationKey() {
        return "edith.starktech.action." + id;
    }

    public static @Nullable EdithAction byId(int ordinal) {
        EdithAction[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }
}
