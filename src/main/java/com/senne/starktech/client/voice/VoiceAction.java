package com.senne.starktech.client.voice;

import java.util.Locale;

/**
 * Alle acties die een spraakcommando kan uitvoeren. In het woordenboek (en in het scherm
 * "Spraakcommando's") koppel je zinnen aan een of meer van deze acties.
 *
 * Sommige acties hebben een waarde nodig (needsValue): de naam van een plek, een bericht...
 * Die waarde staat vast in het commando, of komt uit de zin zelf via een invulplek, bv.
 * "breng me naar {plek}".
 *
 * Een nieuwe actie toevoegen? Zet hem hier, geef hem een naam in de taalbestanden
 * ("voice.starktech.action.(naam)") en voer hem uit in VoiceControl.execute().
 */
public enum VoiceAction {
    // Gevecht
    LAUNCH_DRONES(Group.COMBAT, false),
    ESCORT_ON(Group.COMBAT, false),
    ESCORT_OFF(Group.COMBAT, false),
    ESCORT_TOGGLE(Group.COMBAT, false),
    SATELLITE_STRIKE(Group.COMBAT, false),
    /** Fase 8: de standen van de satelliet. */
    SATELLITE_PRECISION(Group.COMBAT, false),
    SATELLITE_BOMBARDMENT(Group.COMBAT, false),
    SATELLITE_SWEEP(Group.COMBAT, false),
    MARK_TARGET(Group.COMBAT, false),
    CLEAR_MARKS(Group.COMBAT, false),
    SCOUT_DRONE(Group.COMBAT, false),
    SCOUT_RETURN(Group.COMBAT, false),
    /** Fase 5: een losse drone op het doelwit waar je naar kijkt. */
    DRONE_STRIKE(Group.COMBAT, false),
    /** Fase 6: alles afbreken (zwerm, losse drone, escorte, satelliet). */
    STAND_DOWN(Group.COMBAT, false),
    /** Fase 6: spionagedrone sturen of terugroepen. */
    SPY_DRONE(Group.COMBAT, false),
    /** Fase 6: live meekijken door de spionagedrone. */
    SPY_VIEW(Group.COMBAT, false),
    /** Fase 6: het rapport van de spionagedrone openen. */
    SPY_REPORT(Group.COMBAT, false),
    /** Fase 7: laat je oppikken en vlieg zelf. */
    TRANSPORT_RIDE(Group.NAVIGATION, false),
    /** Fase 7: laat je naar je bestemming vliegen. */
    TRANSPORT_AUTOPILOT(Group.NAVIGATION, false),
    /** Fase 7: stuur spullen naar je bestemming. */
    CARGO_DRONE(Group.NAVIGATION, false),
    /** Fase 7: pak iemand op en zet hem elders neer. */
    CARRY_ENTITY(Group.NAVIGATION, false),
    // Scanners
    ORE_SCAN(Group.SCANNER, false),
    THREAT_SCAN_ON(Group.SCANNER, false),
    THREAT_SCAN_OFF(Group.SCANNER, false),
    THREAT_SCAN_TOGGLE(Group.SCANNER, false),
    NIGHT_VISION_ON(Group.SCANNER, false),
    NIGHT_VISION_OFF(Group.SCANNER, false),
    NIGHT_VISION_TOGGLE(Group.SCANNER, false),
    TARGET_ANALYSIS_ON(Group.SCANNER, false),
    TARGET_ANALYSIS_OFF(Group.SCANNER, false),
    TARGET_ANALYSIS_TOGGLE(Group.SCANNER, false),
    // Fase 9: redstone aansturen (met de naam van een relais of groep)
    REDSTONE_ON(Group.TECH, true),
    REDSTONE_OFF(Group.TECH, true),
    REDSTONE_TOGGLE(Group.TECH, true),
    REDSTONE_PULSE(Group.TECH, true),
    // Navigatie en berichten
    PING(Group.NAVIGATION, false),
    NAVIGATE_TO(Group.NAVIGATION, true),
    NAVIGATE_HOME(Group.NAVIGATION, false),
    NAVIGATE_DEATH(Group.NAVIGATION, false),
    NEXT_DESTINATION(Group.NAVIGATION, false),
    STOP_NAVIGATION(Group.NAVIGATION, false),
    SAVE_PLACE(Group.NAVIGATION, true),
    SEND_MESSAGE(Group.NAVIGATION, true),
    OPEN_NAVIGATION(Group.NAVIGATION, false),
    OPEN_COMMS(Group.NAVIGATION, false),
    // Vragen (E.D.I.T.H. antwoordt)
    SAY_TIME(Group.INFO, false),
    SAY_WEATHER(Group.INFO, false),
    SAY_BATTERY(Group.INFO, false),
    SAY_HEALTH(Group.INFO, false),
    SAY_ENEMIES(Group.INFO, false),
    SAY_POSITION(Group.INFO, false),
    SAY_TARGET(Group.INFO, false),
    SAY_DESTINATION(Group.INFO, false),
    SAY_COOLDOWNS(Group.INFO, false),
    SAY_STATUS(Group.INFO, false);

    /** Groepen, zodat het keuzescherm overzichtelijk blijft. */
    public enum Group {
        COMBAT, SCANNER, NAVIGATION, INFO,
        /** Techniek in je wereld: redstone (en later schild en muziek). */
        TECH
    }

    private final Group group;
    private final boolean needsValue;

    VoiceAction(Group group, boolean needsValue) {
        this.group = group;
        this.needsValue = needsValue;
    }

    public Group group() {
        return group;
    }

    /** Heeft deze actie een waarde nodig (plek, naam, bericht)? */
    public boolean needsValue() {
        return needsValue;
    }

    /** De naam in het woordenboek, bv. "launch_drones". */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Sleutel voor de naam in de taalbestanden, bv. "voice.starktech.action.launch_drones". */
    public String translationKey() {
        return "voice.starktech.action." + id();
    }

    public static VoiceAction byId(String id) {
        for (VoiceAction action : values()) {
            if (action.id().equals(id)) {
                return action;
            }
        }
        return null;
    }
}
