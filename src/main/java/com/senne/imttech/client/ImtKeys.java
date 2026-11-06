package com.senne.imttech.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.senne.imttech.ImtTech;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

/**
 * De sneltoetsen van I.M.T. Je kunt ze in het spel veranderen via
 * Opties > Besturing > Toetsen, onder het kopje "I.M.T".
 *
 * Let op: de standaardtoetsen zijn plekken op het toetsenbord (QWERTY-namen).
 * Op een AZERTY-toetsenbord staan ze dus op dezelfde plek, niet op dezelfde letter.
 */
public final class ImtKeys {
    private ImtKeys() {}

    /** De naam van het kopje in Opties > Besturing (zie de taalbestanden). */
    public static final String CATEGORY = "key.categories.imttech";

    /**
     * Tikken = I.M.T-menu linksboven openen (met tabbladen), nog eens tikken = sluiten.
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
    /**
     * De satelliet: elke aanval een eigen toets die meteen vuurt (geen standen meer).
     * K = precisieraket, P = bombardement, C = schuivende laser (1x = begin, 2x = eind).
     * P en C doen in gewoon Minecraft ook iets kleins (sociale interacties, creative-werkbalk
     * opslaan); met een werkende bril op zijn ze van I.M.T (zie ImtClientEvents).
     */
    public static final KeyMapping SATELLITE = key("satellite", InputConstants.KEY_K);
    public static final KeyMapping SATELLITE_BOMBARDMENT = key("satellite_bombardment", InputConstants.KEY_P);
    public static final KeyMapping SATELLITE_SWEEP = key("satellite_sweep", InputConstants.KEY_C);
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
    // Fase 9
    /** Het redstone-scherm openen (al je relais, per groep). */
    public static final KeyMapping REDSTONE = key("redstone", InputConstants.KEY_APOSTROPHE);
    // Fase 10
    /** De speler waar je naar kijkt hacken (of de hack stoppen). */
    public static final KeyMapping HACK = key("hack", InputConstants.KEY_SEMICOLON);

    /** Fase 15: een laserkanonschot uit je handpalm (je moet de borstplaat dragen). */
    public static final KeyMapping LASER_CANNON = key("laser_cannon", InputConstants.KEY_I);
    /** Een salvo mini-raketten (hele harnas + raketpakket op de borstplaat). */
    public static final KeyMapping ROCKETS = key("rockets", InputConstants.KEY_X);
    // Fase 4
    /** Ingedrukt houden = I.M.T luistert naar een (Engels) spraakcommando; loslaten = uitvoeren. */
    public static final KeyMapping PUSH_TO_TALK = key("push_to_talk", InputConstants.KEY_Z);
    /**
     * Kabel-xray aan/uit (verborgen kabels door muren heen zien). Standaard zit hij op GEEN toets
     * (bijna alle letters zijn al bezet); kies er zelf een in Opties > Besturing, of gebruik G > Information.
     */
    public static final KeyMapping CABLE_XRAY = key("cable_xray", InputConstants.UNKNOWN.getValue());
    /**
     * De rand van je zones aan/uit (hoofdstuk 10). Ook standaard op geen toets; kies er zelf een, of
     * gebruik G > Information. Alleen met je eigen bril op.
     */
    public static final KeyMapping ZONE_BORDER = key("zone_border", InputConstants.UNKNOWN.getValue());

    /**
     * Hoofdstuk 35: herladen (R) en het geweer bekijken (H), zoals in TACZ. Ze doen alleen iets met een lasergeweer in je
     * hand; dan doen R en H niet ook nog wat ze anders doen (markeren en de dreigingsscan, zie ImtClientEvents). Daarom
     * staan ze in Opties > Besturing niet in het rood, ook al delen ze een toets.
     */
    public static final KeyMapping GUN_RELOAD = new KeyMapping("key.imttech.gun_reload", GunKeys.CONTEXT, Type.KEYSYM,
            InputConstants.KEY_R, CATEGORY);
    public static final KeyMapping GUN_INSPECT = new KeyMapping("key.imttech.gun_inspect", GunKeys.CONTEXT, Type.KEYSYM,
            InputConstants.KEY_H, CATEGORY);

    /** Wanneer de toetsen van een geweer werken: in het spel (geen scherm open) en met een lasergeweer, een pistool of een magazijn (hoofdstuk 52) in je hand. */
    private static final class GunKeys implements net.neoforged.neoforge.client.settings.IKeyConflictContext {
        static final GunKeys CONTEXT = new GunKeys();

        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive() && (com.senne.imttech.client.weapons.GunControl.held() != null
                    || com.senne.imttech.client.guns.FirearmControl.held() != null
                    || com.senne.imttech.client.guns.FirearmControl.heldMagazine());
        }

        @Override
        public boolean conflicts(net.neoforged.neoforge.client.settings.IKeyConflictContext other) {
            return other == this;
        }
    }

    private static KeyMapping key(String name, int defaultKey) {
        // KeyConflictContext.IN_GAME: de toets doet alleen iets als er geen scherm open is.
        return new KeyMapping("key.imttech." + name, KeyConflictContext.IN_GAME, Type.KEYSYM, defaultKey, CATEGORY);
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
        event.register(SATELLITE_BOMBARDMENT);
        event.register(SATELLITE_SWEEP);
        event.register(DRONE_CAMERA);
        event.register(NAVIGATION);
        event.register(PING);
        event.register(COMMS);
        event.register(PUSH_TO_TALK);
        event.register(DRONE_STRIKE);
        event.register(SPY_DRONE);
        event.register(STAND_DOWN);
        event.register(REDSTONE);
        event.register(HACK);
        event.register(LASER_CANNON);
        event.register(ROCKETS);
        event.register(CABLE_XRAY);
        event.register(ZONE_BORDER);
        event.register(GUN_RELOAD);
        event.register(GUN_INSPECT);
    }
}
