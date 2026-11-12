package com.senne.imttech.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.senne.imttech.ImtTech;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;

/**
 * De sneltoetsen van I.M.T. Je kunt ze in het spel veranderen via
 * Opties > Besturing > Toetsen, onder het kopje "I.M.T".
 *
 * Let op: de standaardtoetsen zijn plekken op het toetsenbord (QWERTY-namen).
 * Op een AZERTY-toetsenbord staan ze dus op dezelfde plek, niet op dezelfde letter.
 *
 * Sinds 2026-11-10 (jouw wens): de snelacties van de bril (ore scan, satelliet, drones, ping, comms...) staan standaard
 * op GEEN toets. Ze staan allemaal ook in het G-menu, en zo blijven er letters vrij voor andere dingen (de rugzak staat nu
 * gewoon op B). Wie ze toch op een toets wil, kiest er zelf een in Opties > Besturing > I.M.T. Spellen die nog de oude
 * toetsen hadden, zet {@link #migrateOnce()} één keer om.
 */
public final class ImtKeys {
    private ImtKeys() {}

    /** De naam van het kopje in Opties > Besturing (zie de taalbestanden). */
    public static final String CATEGORY = "key.categories.imttech";

    /** "Geen toets" (standaard voor de snelacties hieronder). */
    private static final int NONE = InputConstants.UNKNOWN.getValue();

    /**
     * Tikken = I.M.T-menu linksboven openen (met tabbladen), nog eens tikken = sluiten.
     * Ingedrukt houden, een optie aanwijzen en loslaten kiest die optie meteen.
     */
    public static final KeyMapping MENU = key("menu", InputConstants.KEY_G);
    /** Ingedrukt houden = inzoomen (vasthouden kan niet vanuit een menu, dus die houdt zijn toets). */
    public static final KeyMapping ZOOM = key("zoom", InputConstants.KEY_V);
    // De snelacties: standaard geen toets (G-menu, zie boven). Tussen haakjes de oude toets.
    /** G > Systems (was N). */
    public static final KeyMapping NIGHT_VISION = key("night_vision", NONE);
    /** G > Information (was B). */
    public static final KeyMapping ORE_SCAN = key("ore_scan", NONE);
    /** G > Information (was H). */
    public static final KeyMapping THREAT_SCAN = key("threat_scan", NONE);
    // Fase 2
    /** Markeer (of ontmarkeer) het wezen waar je naar kijkt. G > Drones (was R). */
    public static final KeyMapping MARK_TARGET = key("mark_target", NONE);
    /** G > Drones (was J). */
    public static final KeyMapping LAUNCH_DRONES = key("launch_drones", NONE);
    /**
     * De satelliet: elke aanval een eigen toets die meteen vuurt (geen standen meer). G > Satellite & Arsenal (was K =
     * precisieraket, P = bombardement, C = schuivende laser, 1x = begin, 2x = eind). Zet je het bombardement zelf op P,
     * dan is P met een werkende bril op van I.M.T en niet van Minecraft (zie ImtClientEvents).
     */
    public static final KeyMapping SATELLITE = key("satellite", NONE);
    public static final KeyMapping SATELLITE_BOMBARDMENT = key("satellite_bombardment", NONE);
    public static final KeyMapping SATELLITE_SWEEP = key("satellite_sweep", NONE);
    // Fase 3
    /** G > Recon drones (was Y). */
    public static final KeyMapping DRONE_CAMERA = key("drone_camera", NONE);
    /** G > Navigation (was M). */
    public static final KeyMapping NAVIGATION = key("navigation", NONE);
    /** G > Navigation (was U). */
    public static final KeyMapping PING = key("ping", NONE);
    /** G > Navigation (was O). */
    public static final KeyMapping COMMS = key("comms", NONE);
    // Fase 5
    /** Stuur EEN drone op het doelwit waar je naar kijkt. G > Drones (was L). */
    public static final KeyMapping DRONE_STRIKE = key("drone_strike", NONE);
    /** Stuur een spionagedrone op wie je aankijkt (of roep hem terug). G > Recon drones (was punt). */
    public static final KeyMapping SPY_DRONE = key("spy_drone", NONE);
    /** Alles afbreken: drones terug, satelliet uit, escorte uit, markeringen weg. G > Drones (was komma). */
    public static final KeyMapping STAND_DOWN = key("stand_down", NONE);
    // Fase 9
    /** Het redstone-scherm openen (al je relais, per groep). G > Tech (was apostrof). */
    public static final KeyMapping REDSTONE = key("redstone", NONE);
    // Fase 10
    /** De speler waar je naar kijkt hacken (of de hack stoppen). G > Tech (was puntkomma). */
    public static final KeyMapping HACK = key("hack", NONE);

    /** Fase 15: een laserkanonschot uit je handpalm (je moet de borstplaat dragen). G > Combat (was I). */
    public static final KeyMapping LASER_CANNON = key("laser_cannon", NONE);
    /** Een salvo mini-raketten (hele harnas + raketpakket op de borstplaat). G > Combat (was X). */
    public static final KeyMapping ROCKETS = key("rockets", NONE);
    // Fase 4
    /** Ingedrukt houden = I.M.T luistert naar een (Engels) spraakcommando; loslaten = uitvoeren. */
    public static final KeyMapping PUSH_TO_TALK = key("push_to_talk", InputConstants.KEY_Z);
    /**
     * Kabel-xray aan/uit (verborgen kabels door muren heen zien). Standaard zit hij op GEEN toets, net als de
     * snelacties hierboven; kies er zelf een in Opties > Besturing, of gebruik G > Information.
     */
    public static final KeyMapping CABLE_XRAY = key("cable_xray", InputConstants.UNKNOWN.getValue());
    /**
     * De rand van je zones aan/uit (hoofdstuk 10). Ook standaard op geen toets; kies er zelf een, of
     * gebruik G > Information. Alleen met je eigen bril op.
     */
    public static final KeyMapping ZONE_BORDER = key("zone_border", InputConstants.UNKNOWN.getValue());
    /**
     * Je coördinaten in de bril aan/uit (hoofdstuk 82). Ook standaard op geen toets; kies er zelf een, of gebruik
     * G > Navigation > Coordinates.
     */
    public static final KeyMapping COORDINATES = key("coordinates", NONE);

    /**
     * Hoofdstuk 35: herladen (R) en het geweer bekijken (H), zoals in TACZ. Ze doen alleen iets met een lasergeweer in je
     * hand; dan doen R en H niet ook nog wat ze anders doen (markeren en de dreigingsscan, zie ImtClientEvents). Daarom
     * staan ze in Opties > Besturing niet in het rood, ook al delen ze een toets.
     */
    /**
     * Hoofdstuk 52 (stap 2): het wiel om snel te trekken. Linker Alt ingedrukt houden, aanwijzen, loslaten; sinds
     * hoofdstuk 56 alleen zolang je hem vasthoudt (geen tik-stand), en je loopt ondertussen door. Alt doet in Minecraft
     * zelf niets.
     */
    public static final KeyMapping GEAR_WHEEL = key("gear_wheel", InputConstants.KEY_LALT);
    /**
     * Hoofdstuk 52 (stap 3): de skilltree van je vuurwapens. Sinds hoofdstuk 56 alleen zo te openen (jouw keuze: niet in
     * het wiel en niet in het G-menu); standaard F6, want elke letter is al bezet.
     */
    public static final KeyMapping SKILL_TREE = key("skill_tree", InputConstants.KEY_F6);

    public static final KeyMapping GUN_RELOAD = new KeyMapping("key.imttech.gun_reload", GunKeys.CONTEXT, Type.KEYSYM,
            InputConstants.KEY_R, CATEGORY);
    public static final KeyMapping GUN_INSPECT = new KeyMapping("key.imttech.gun_inspect", GunKeys.CONTEXT, Type.KEYSYM,
            InputConstants.KEY_H, CATEGORY);
    /**
     * Hoofdstuk 54 (jouw wens): U = unload. Met een pistool: het magazijn eruit (zonder magazijn: de patroon uit de kamer);
     * met een magazijn in je hand: één patroon eruit; sinds hoofdstuk 57 met een lasergeweer: de cel eruit. Met zo een
     * wapen in je hand doet U dan niet ook de ping.
     */
    public static final KeyMapping GUN_UNLOAD = new KeyMapping("key.imttech.gun_unload", GunKeys.CONTEXT, Type.KEYSYM,
            InputConstants.KEY_U, CATEGORY);
    /**
     * Hoofdstuk 54 (jouw notitie): een eigen toets voor elke quickdraw en om elke pouch te openen (magazijnen en granaten
     * erin stoppen of eruit halen). Standaard op geen toets (bijna alle letters zijn bezet); kies ze in Opties > Besturing.
     * Het wiel (linker Alt) kan het allemaal ook.
     */
    public static final KeyMapping DRAW_GUN = key("draw_gun", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping DRAW_GRENADE = key("draw_grenade", InputConstants.UNKNOWN.getValue());
    /** Hoofdstuk 55: het geweer op je rug of voor je borst trekken (ook via het wiel). */
    public static final KeyMapping DRAW_SLING_BACK = key("draw_sling_back", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping DRAW_SLING_CHEST = key("draw_sling_chest", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping OPEN_HOLSTER = key("open_holster", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping OPEN_GRENADE_POUCH = key("open_grenade_pouch", InputConstants.UNKNOWN.getValue());
    public static final KeyMapping OPEN_MAG_POUCH = key("open_mag_pouch", InputConstants.UNKNOWN.getValue());
    /** Hoofdstuk 100: de keycard aan je nek trekken (over wat je vasthoudt; nog eens = terug). Standaard geen toets. */
    public static final KeyMapping DRAW_KEYCARD = key("draw_keycard", InputConstants.UNKNOWN.getValue());

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

    /**
     * Hoofdstuk 63: "How it works". Houd deze toets vast terwijl je muis op een item staat (in je inventaris, een kist,
     * het creative-menu of JEI), en de animatie van dat item gaat open. Standaard W, zoals Ponder van Create. Werkt alleen
     * in een scherm (KeyConflictContext.GUI), dus lopen met W botst er niet mee.
     */
    public static final KeyMapping HOW_IT_WORKS = new KeyMapping("key.imttech.how_it_works", KeyConflictContext.GUI,
            Type.KEYSYM, InputConstants.KEY_W, CATEGORY);

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
        event.register(COORDINATES);
        event.register(GUN_RELOAD);
        event.register(GEAR_WHEEL);
        event.register(SKILL_TREE);
        event.register(GUN_INSPECT);
        event.register(GUN_UNLOAD);
        event.register(DRAW_GUN);
        event.register(DRAW_GRENADE);
        event.register(DRAW_SLING_BACK);
        event.register(DRAW_SLING_CHEST);
        event.register(OPEN_HOLSTER);
        event.register(OPEN_GRENADE_POUCH);
        event.register(OPEN_MAG_POUCH);
        event.register(DRAW_KEYCARD);
        event.register(HOW_IT_WORKS);
    }

    // ---------------------------------------------------------------- De oude toetsen omzetten (2026-11-10)

    /** Al gekeken in deze speelsessie? */
    private static boolean migrated;

    /**
     * Eén keer per spel: zet de toetsen van vóór 2026-11-10 om naar de nieuwe indeling. Minecraft onthoudt elke toets in
     * options.txt, dus wie de mod al had, hield anders de oude letters (B bleef de ore scan, de rugzak Shift + B). Alleen een
     * toets die nog op zijn OUDE standaard staat gaat om; heb je er zelf een andere letter op gezet, dan blijft die. Daarna
     * staat in imttech-client.toml {@code keyLayout = 2}, zodat het niet nog eens gebeurt (zet je daarna zelf B op de ore
     * scan, dan blijft dat zo). Wordt elke tick aangeroepen (ImtClientEvents), maar doet maar één keer iets.
     */
    public static void migrateOnce() {
        if (migrated || !ImtClientConfig.SPEC.isLoaded()) {
            return;
        }
        migrated = true;
        if (ImtClientConfig.KEY_LAYOUT.get() >= 2) {
            return;
        }
        // De snelacties en hun oude standaardtoets.
        Map<KeyMapping, Integer> old = new LinkedHashMap<>();
        old.put(NIGHT_VISION, InputConstants.KEY_N);
        old.put(ORE_SCAN, InputConstants.KEY_B);
        old.put(THREAT_SCAN, InputConstants.KEY_H);
        old.put(MARK_TARGET, InputConstants.KEY_R);
        old.put(LAUNCH_DRONES, InputConstants.KEY_J);
        old.put(SATELLITE, InputConstants.KEY_K);
        old.put(SATELLITE_BOMBARDMENT, InputConstants.KEY_P);
        old.put(SATELLITE_SWEEP, InputConstants.KEY_C);
        old.put(DRONE_CAMERA, InputConstants.KEY_Y);
        old.put(NAVIGATION, InputConstants.KEY_M);
        old.put(PING, InputConstants.KEY_U);
        old.put(COMMS, InputConstants.KEY_O);
        old.put(DRONE_STRIKE, InputConstants.KEY_L);
        old.put(SPY_DRONE, InputConstants.KEY_PERIOD);
        old.put(STAND_DOWN, InputConstants.KEY_COMMA);
        old.put(REDSTONE, InputConstants.KEY_APOSTROPHE);
        old.put(HACK, InputConstants.KEY_SEMICOLON);
        old.put(LASER_CANNON, InputConstants.KEY_I);
        old.put(ROCKETS, InputConstants.KEY_X);
        int changed = 0;
        for (Map.Entry<KeyMapping, Integer> entry : old.entrySet()) {
            KeyMapping mapping = entry.getKey();
            if (on(mapping, KeyModifier.NONE, entry.getValue())) {
                mapping.setKeyModifierAndCode(KeyModifier.NONE, InputConstants.UNKNOWN);
                changed++;
            }
        }
        // De rugzak: van Shift + B naar gewoon B.
        KeyMapping backpack = com.senne.imttech.client.backpack.BackpackClient.OPEN_BACKPACK;
        if (on(backpack, KeyModifier.SHIFT, InputConstants.KEY_B)) {
            backpack.setKeyModifierAndCode(KeyModifier.NONE, Type.KEYSYM.getOrCreate(InputConstants.KEY_B));
            changed++;
        }
        if (changed > 0) {
            // Minecraft zoekt toetsen op in een eigen lijst: die opnieuw opbouwen, en options.txt meteen bewaren.
            KeyMapping.resetMapping();
            Minecraft.getInstance().options.save();
            ImtTech.LOGGER.info("[keys] {} I.M.T-toetsen naar de nieuwe indeling gezet (snelacties zonder toets, rugzak op B)", changed);
        }
        ImtClientConfig.KEY_LAYOUT.set(2);
        ImtClientConfig.SPEC.save();
    }

    /** Staat deze toets op precies deze toets (met deze Shift/Ctrl/Alt erbij)? */
    private static boolean on(KeyMapping mapping, KeyModifier modifier, int key) {
        return mapping.getKeyModifier() == modifier && mapping.getKey().equals(Type.KEYSYM.getOrCreate(key));
    }
}
