package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Geluiden van E.D.I.T.H. Welk geluid er echt afspeelt staat in
 * assets/starktech/sounds.json. Nu verwijzen die naar bestaande Minecraft-geluiden;
 * je kunt daar later je eigen .ogg-bestanden neerzetten.
 */
public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, StarkTech.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> EDITH_BOOT = register("edith.boot");
    /** Een piepje bij elke regel van de systeemcheck als de bril opstart. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EDITH_BLIP = register("edith.blip");
    /** "Identiteit bevestigd" na de irisscan. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EDITH_CONFIRM = register("edith.confirm");
    public static final DeferredHolder<SoundEvent, SoundEvent> EDITH_NOTIFY = register("edith.notify");
    public static final DeferredHolder<SoundEvent, SoundEvent> EDITH_ALERT = register("edith.alert");
    public static final DeferredHolder<SoundEvent, SoundEvent> EDITH_DENIED = register("edith.denied");
    public static final DeferredHolder<SoundEvent, SoundEvent> EDITH_SCAN = register("edith.scan");
    public static final DeferredHolder<SoundEvent, SoundEvent> EDITH_MENU_OPEN = register("edith.menu_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> EDITH_SELECT = register("edith.select");
    public static final DeferredHolder<SoundEvent, SoundEvent> STATION_UPGRADE = register("station.upgrade");
    // Fase 2
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_DEPLOY = register("drone.deploy");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_LASER = register("drone.laser");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_RECALL = register("drone.recall");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_HURT = register("drone.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_DESTROYED = register("drone.destroyed");
    public static final DeferredHolder<SoundEvent, SoundEvent> SATELLITE_LOCK = register("satellite.lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> SATELLITE_CHARGE = register("satellite.charge");
    /** Fase 8: het inslaan van de satelliet. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SATELLITE_FIRE = register("satellite.fire");
    /** Een raket van de satelliet valt (gefluit) en slaat in (dreun). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SATELLITE_MISSILE = register("satellite.missile");
    public static final DeferredHolder<SoundEvent, SoundEvent> SATELLITE_IMPACT = register("satellite.impact");
    // Fase 3
    public static final DeferredHolder<SoundEvent, SoundEvent> COMMS_MESSAGE = register("comms.message");
    // Het harnas: eigen geluiden (zie tools/generate_sounds.py)
    public static final DeferredHolder<SoundEvent, SoundEvent> REPULSOR_ARM = register("suit.repulsor_arm");
    public static final DeferredHolder<SoundEvent, SoundEvent> REPULSOR_DISARM = register("suit.repulsor_disarm");
    public static final DeferredHolder<SoundEvent, SoundEvent> REPULSOR_FIRE = register("suit.repulsor_fire");
    /** De stuwraketten, als lus zolang je vliegt. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SUIT_THRUSTER = register("suit.thruster");
    public static final DeferredHolder<SoundEvent, SoundEvent> COMMS_PING = register("comms.ping");
    // De rayshields, Star Wars-achtig (jouw wens, 7 oktober; zelf nagemaakt in tools/generate_sounds.py)
    /** Het zoemen van een laserwand of koepel, als lus (zie client/shield/RayshieldHum). */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAYSHIELD_HUM = register("shield.rayshield_hum");
    /** "Vwoem": een rayshield gaat aan. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAYSHIELD_ON = register("shield.rayshield_on");
    /** Een rayshield gaat uit: de zoemtoon zakt weg. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAYSHIELD_OFF = register("shield.rayshield_off");
    /** Iets raakt een rayshield (of loopt tegen een laserwand): een elektrische knetter. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAYSHIELD_HIT = register("shield.rayshield_hit");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, name)));
    }
}
