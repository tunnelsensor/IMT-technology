package com.senne.imttech.registry;

import com.senne.imttech.ImtTech;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Geluiden van I.M.T. Welk geluid er echt afspeelt staat in
 * assets/imttech/sounds.json. Nu verwijzen die naar bestaande Minecraft-geluiden;
 * je kunt daar later je eigen .ogg-bestanden neerzetten.
 */
public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ImtTech.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> IMT_BOOT = register("imt.boot");
    /** Een piepje bij elke regel van de systeemcheck als de bril opstart. */
    public static final DeferredHolder<SoundEvent, SoundEvent> IMT_BLIP = register("imt.blip");
    /** "Identiteit bevestigd" na de irisscan. */
    public static final DeferredHolder<SoundEvent, SoundEvent> IMT_CONFIRM = register("imt.confirm");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMT_NOTIFY = register("imt.notify");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMT_ALERT = register("imt.alert");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMT_DENIED = register("imt.denied");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMT_SCAN = register("imt.scan");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMT_MENU_OPEN = register("imt.menu_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> IMT_SELECT = register("imt.select");
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
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_CANNON_ARM = register("suit.laser_cannon_arm");
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_CANNON_DISARM = register("suit.laser_cannon_disarm");
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_CANNON_FIRE = register("suit.laser_cannon_fire");
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

    // Hoofdstuk 10: C4
    /** Het piepje van een C4 (Timer) die aftelt. */
    public static final DeferredHolder<SoundEvent, SoundEvent> C4_BEEP = register("c4.beep");
    /** Een C4 wordt geplakt en gaat lopen (klik, dubbel piepje). */
    public static final DeferredHolder<SoundEvent, SoundEvent> C4_ARM = register("c4.arm");
    /** Iemand knipt de draad van een C4 door. */
    public static final DeferredHolder<SoundEvent, SoundEvent> C4_DEFUSE = register("c4.defuse");
    /** De knop van de detonator (een klik en een radiotoontje). */
    public static final DeferredHolder<SoundEvent, SoundEvent> DETONATOR = register("c4.detonator");

    // ---------------------------------------------------------------- Guard robots (hoofdstuk 21)
    /** Een stap: een servo-ritje en een metalen tikje. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_STEP = register("robot.step");
    /** Aanzetten: de servo's lopen op, twee piepjes. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_POWER_ON = register("robot.power_on");
    /** Uitzetten (of een lege batterij): de zoem zakt weg, een doffe klonk. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_POWER_OFF = register("robot.power_off");
    /** Geraakt: een metalen klang en vonkjes. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_HURT = register("robot.hurt");
    /** Laden op zijn dock: een heel zachte lus (zie client/robot/RobotChargeHum). */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_CHARGE_HUM = register("robot.charge_hum");
    /** Het laserschot van een robot (hoofdstuk 24): een volle "pew" met een zoemende staart. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_LASER = register("robot.laser");
    /** Het geweer van een robot: een droge knal. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_GUN = register("robot.gun");
    /** De Ion Blaster: een knetterende "fzzzt" die snel wegzakt. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ION_BLASTER_FIRE = register("ion_blaster.fire");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, name)));
    }
}
