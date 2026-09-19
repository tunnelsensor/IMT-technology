package com.senne.starktech.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.senne.starktech.StarkTech;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * De sneltoetsen van E.D.I.T.H. Je kunt ze in het spel veranderen via
 * Opties > Besturing > Toetsen, onder het kopje "E.D.I.T.H.".
 *
 * Let op: de standaardtoetsen zijn plekken op het toetsenbord (QWERTY-namen).
 * Op een AZERTY-toetsenbord staan ze dus op dezelfde plek, niet op dezelfde letter.
 */
public final class EdithKeys {
    private EdithKeys() {}

    /** De naam van het kopje in Opties > Besturing (zie de taalbestanden). */
    public static final String CATEGORY = "key.categories.starktech";

    /**
     * Tikken = E.D.I.T.H.-menu linksboven openen (met tabbladen), nog eens tikken = sluiten.
     * Ingedrukt houden, een optie aanwijzen en loslaten kiest die optie meteen.
     */
    public static final KeyMapping MENU = key("menu", InputConstants.KEY_G);
    /** Ingedrukt houden = inzoomen. */
    public static final KeyMapping ZOOM = key("zoom", InputConstants.KEY_V);
    public static final KeyMapping NIGHT_VISION = key("night_vision", InputConstants.KEY_N);
    public static final KeyMapping ORE_SCAN = key("ore_scan", InputConstants.KEY_B);
    public static final KeyMapping THREAT_SCAN = key("threat_scan", InputConstants.KEY_H);
    // Fase 2
    /** Markeer (of ontmarkeer) het wezen waar je naar kijkt. */
    public static final KeyMapping MARK_TARGET = key("mark_target", InputConstants.KEY_R);
    public static final KeyMapping LAUNCH_DRONES = key("launch_drones", InputConstants.KEY_J);
    public static final KeyMapping SATELLITE = key("satellite", InputConstants.KEY_K);
    // Fase 3
    public static final KeyMapping DRONE_CAMERA = key("drone_camera", InputConstants.KEY_Y);
    public static final KeyMapping NAVIGATION = key("navigation", InputConstants.KEY_M);
    public static final KeyMapping PING = key("ping", InputConstants.KEY_U);
    public static final KeyMapping COMMS = key("comms", InputConstants.KEY_O);
    // Fase 5
    /** Stuur EEN drone op het doelwit waar je naar kijkt. */
    public static final KeyMapping DRONE_STRIKE = key("drone_strike", InputConstants.KEY_L);
    /** Stuur een spionagedrone op wie je aankijkt (of roep hem terug). */
    public static final KeyMapping SPY_DRONE = key("spy_drone", InputConstants.KEY_PERIOD);
    /** Alles afbreken: drones terug, satelliet uit, escorte uit, markeringen weg. */
    public static final KeyMapping STAND_DOWN = key("stand_down", InputConstants.KEY_COMMA);
    // Fase 4
    /** Ingedrukt houden = E.D.I.T.H. luistert naar een (Engels) spraakcommando; loslaten = uitvoeren. */
    public static final KeyMapping PUSH_TO_TALK = key("push_to_talk", InputConstants.KEY_Z);

    private static KeyMapping key(String name, int defaultKey) {
        // KeyConflictContext.IN_GAME: de toets doet alleen iets als er geen scherm open is.
        return new KeyMapping("key.starktech." + name, KeyConflictContext.IN_GAME, Type.KEYSYM, defaultKey, CATEGORY);
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(MENU);
        event.register(ZOOM);
        event.register(NIGHT_VISION);
        event.register(ORE_SCAN);
        event.register(THREAT_SCAN);
        event.register(MARK_TARGET);
        event.register(LAUNCH_DRONES);
        event.register(SATELLITE);
        event.register(DRONE_CAMERA);
        event.register(NAVIGATION);
        event.register(PING);
        event.register(COMMS);
        event.register(PUSH_TO_TALK);
        event.register(DRONE_STRIKE);
        event.register(SPY_DRONE);
        event.register(STAND_DOWN);
    }
}
