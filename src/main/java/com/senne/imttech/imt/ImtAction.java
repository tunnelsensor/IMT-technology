package com.senne.imttech.imt;

import org.jetbrains.annotations.Nullable;

/**
 * Alles wat je I.M.T kunt vragen te doen. De client (jouw spel) stuurt een actie naar
 * de server, en de server controleert of het mag (draag je de bril, ben je de eigenaar,
 * is er genoeg energie?) en voert het dan uit.
 *
 * De volgorde is ook het nummer dat over het netwerk gaat (zie ImtActionPayload).
 */
public enum ImtAction {
    TOGGLE_NIGHT_VISION("toggle_night_vision", ImtFeature.NIGHT_VISION),
    TOGGLE_THREAT_SCAN("toggle_threat_scan", ImtFeature.THREAT_SCAN),
    TOGGLE_TARGET_ANALYSIS("toggle_target_analysis", ImtFeature.TARGET_ANALYSIS),
    ORE_SCAN("ore_scan", null),
    AUTHORIZE_TRANSFER("authorize_transfer", null),
    // Fase 2
    LAUNCH_DRONES("launch_drones", null),
    TOGGLE_GUARD_DRONES("toggle_guard_drones", ImtFeature.GUARD_DRONES),
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
    // Fase 10
    /** De speler waar je naar kijkt hacken (of de hack stoppen). */
    HACK_PLAYER("hack_player", null),
    /** Alle hacks stoppen. */
    HACK_STOP("hack_stop", null),
    // Fase 11
    /** Persoonlijk schild aan/uit. */
    TOGGLE_SHIELD("toggle_shield", ImtFeature.SHIELD),
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
    /** Een laserkanonschot uit je handpalm. */
    LASER_CANNON("laser_cannon", null),
    // Meer tech
    /** De volgende vorm voor je escortedrones (zie DroneFormation). */
    DRONE_FORMATION("drone_formation", null),
    /** Een salvo mini-raketten uit het harnas (zie SuitRockets). */
    ROCKETS("rockets", null),
    // De drie aanvallen van de satelliet, elk met een eigen toets (K, P en C) en een eigen optie in
    // het G-menu. Er zijn geen "standen" meer: kiezen = meteen vuren. SATELLITE_STRIKE (hierboven,
    // bv. "nuke it" met je stem) is de precisieraket.
    STRIKE_PRECISION("strike_precision", null),
    STRIKE_BOMBARDMENT("strike_bombardment", null),
    STRIKE_SWEEP("strike_sweep", null),
    // Het brein op het datacenter (8 oktober): je spel zegt wanneer ze nadenkt, zodat je datacenter
    // dan hard werkt (zie DatacenterBrain).
    BRAIN_THINKING("brain_thinking", null),
    BRAIN_DONE("brain_done", null);

    private final String id;
    private final @Nullable ImtFeature toggles;

    ImtAction(String id, @Nullable ImtFeature toggles) {
        this.id = id;
        this.toggles = toggles;
    }

    /** De functie die deze actie aan/uit zet, of null als het een eenmalige actie is. */
    public @Nullable ImtFeature toggles() {
        return toggles;
    }

    public String translationKey() {
        return "imt.imttech.action." + id;
    }

    public static @Nullable ImtAction byId(int ordinal) {
        ImtAction[] all = values();
        return ordinal >= 0 && ordinal < all.length ? all[ordinal] : null;
    }
}
