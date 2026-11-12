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
    /** Hoofdstuk 96: de batterij van de bril is leeg en hij valt uit (zacht, zonder geknetter). */
    public static final DeferredHolder<SoundEvent, SoundEvent> IMT_SHUTDOWN = register("imt.shutdown");
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
    /** Hoofdstuk 60: de radio van een Raven-soldaat (een klik, geknetter, een piepje). */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAVEN_RADIO = register("raven.radio");
    // De rayshields, Star Wars-achtig (jouw wens, 7 oktober; zelf nagemaakt in tools/generate_sounds.py)
    /** Het zoemen van een laserwand of koepel, als lus (zie client/shield/RayshieldHum). */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAYSHIELD_HUM = register("shield.rayshield_hum");
    /** "Vwoem": een rayshield gaat aan. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAYSHIELD_ON = register("shield.rayshield_on");
    /** Een rayshield gaat uit: de zoemtoon zakt weg. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAYSHIELD_OFF = register("shield.rayshield_off");
    /** Iets raakt een rayshield (of loopt tegen een laserwand): een elektrische knetter. */
    public static final DeferredHolder<SoundEvent, SoundEvent> RAYSHIELD_HIT = register("shield.rayshield_hit");

    // Hoofdstuk 35: de Remote Frag en de triggers (eerst van C4)
    /** Iemand knipt de draad van een Remote Frag door. */
    public static final DeferredHolder<SoundEvent, SoundEvent> REMOTE_FRAG_DEFUSE = register("remote_frag.defuse");
    /** De knop van een trigger (een klik en een radiotoontje). */
    public static final DeferredHolder<SoundEvent, SoundEvent> TRIGGER_CLICK = register("trigger.click");

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
    /** De rotors van een security drone die vliegt: een heel zachte lus (zie client/robot/DroneRotorSound). */
    public static final DeferredHolder<SoundEvent, SoundEvent> DRONE_ROTOR = register("drone.rotor");
    /** Het laserschot van een robot (hoofdstuk 24): een volle "pew" met een zoemende staart. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_LASER = register("robot.laser");
    /** Het geweer van een robot: een droge knal. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROBOT_GUN = register("robot.gun");
    /** De Ion Blaster: een knetterende "fzzzt" die snel wegzakt. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ION_BLASTER_FIRE = register("ion_blaster.fire");
    /** Een EMP-granaat gaat af (hoofdstuk 35): een diepe elektrische dreun met geknetter. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EMP_BLAST = register("emp.blast");
    /** Een apparaat dat uitvalt door een EMP: een zoemtoon die wegzakt. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EMP_POWER_DOWN = register("emp.power_down");
    /** Het piepje van een EMP-granaat die gaat afgaan (steeds sneller). */
    public static final DeferredHolder<SoundEvent, SoundEvent> EMP_GRENADE_BEEP = register("emp_grenade.beep");
    /** Een Sticky EMP Grenade plakt vast: een doffe klets met een klikje. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EMP_GRENADE_STICK = register("emp_grenade.stick");
    /** De lasergeweren (hoofdstuk 35): elk zijn eigen schot. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_PISTOL_FIRE = register("laser_pistol.fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_RIFLE_FIRE = register("laser_rifle.fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_SNIPER_FIRE = register("laser_sniper.fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_SHOTGUN_FIRE = register("laser_shotgun.fire");
    /** Een leeg geweer: een droge klik met een zoempje dat wegvalt. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_GUN_EMPTY = register("laser_gun.empty");
    /** Herladen: de cel klikt los en schuift eruit... */
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_GUN_CELL_OUT = register("laser_gun.cell_out");
    /** ...en de nieuwe klikt erin, met een oplopende zoem (het geweer laadt op). */
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_GUN_CELL_IN = register("laser_gun.cell_in");
    /** De grendel van de sniper of de pomp van de shotgun na een schot. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_GUN_CYCLE = register("laser_gun.cycle");
    /** Richten: een zacht mechanisch klikje. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_GUN_AIM = register("laser_gun.aim");
    /** De smokebom begint te roken: een lang, luid gesis. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SMOKE_GRENADE_HISS = register("smoke_grenade.hiss");
    /** De flashbang: een snoeiharde knal. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FLASHBANG_BANG = register("flashbang.bang");
    /** De piep in je oren na een flashbang (alleen jij hoort hem). */
    public static final DeferredHolder<SoundEvent, SoundEvent> FLASHBANG_RING = register("flashbang.ring");
    /** De bol stuitert: een metalen tik. */
    public static final DeferredHolder<SoundEvent, SoundEvent> EMP_GRENADE_BOUNCE = register("emp_grenade.bounce");

    /** Hoofdstuk 52: de Glock 19. Een scherpe knal (sinds hoofdstuk 74 drie varianten, tot 32 blokken ver)... */
    public static final DeferredHolder<SoundEvent, SoundEvent> GLOCK_FIRE = register("glock.fire");
    /** ...hetzelfde schot van ver weg (hoofdstuk 74): dof, met galm, tot 128 blokken (zie Firearms.shotSound)... */
    public static final DeferredHolder<SoundEvent, SoundEvent> GLOCK_FIRE_FAR = register("glock.fire_far");
    /** ...een droge klik (leeg, een ketser, een storing)... */
    public static final DeferredHolder<SoundEvent, SoundEvent> GLOCK_DRY = register("glock.dry");
    /** ...de slede die naar achteren en weer naar voren gaat... */
    public static final DeferredHolder<SoundEvent, SoundEvent> GLOCK_SLIDE = register("glock.slide");
    /** ...de slide stop omlaag: de slede slaat naar voren (hoofdstuk 74)... */
    public static final DeferredHolder<SoundEvent, SoundEvent> GLOCK_SLIDE_RELEASE = register("glock.slide_release");
    /** ...het magazijn dat eruit valt en erin wordt geduwd... */
    public static final DeferredHolder<SoundEvent, SoundEvent> GLOCK_MAG_OUT = register("glock.mag_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> GLOCK_MAG_IN = register("glock.mag_in");
    /** ...een patroon die in een magazijn klikt... */
    public static final DeferredHolder<SoundEvent, SoundEvent> GLOCK_ROUND_LOAD = register("glock.round_load");
    /** ...een tap-rack (een klap op het magazijn en de slede)... */
    public static final DeferredHolder<SoundEvent, SoundEvent> TAP_RACK = register("glock.tap_rack");
    /** ...een huls die op de grond tinkelt, en een kogel die in een blok slaat. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CASING_TINK = register("casing.tink");
    public static final DeferredHolder<SoundEvent, SoundEvent> BULLET_IMPACT = register("bullet.impact");
    /** Hoofdstuk 103: de MP5. Het schot van dichtbij (drie varianten) en van ver weg... */
    public static final DeferredHolder<SoundEvent, SoundEvent> MP5_FIRE = register("mp5.fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> MP5_FIRE_FAR = register("mp5.fire_far");
    /** ...en de spanhendel: naar achteren en met een klap naar voren. */
    public static final DeferredHolder<SoundEvent, SoundEvent> MP5_CHARGE = register("mp5.charge");
    /** Hoofdstuk 103, stap 2: de AK-47. Een zwaardere knal dan de MP5, en zijn spanhendel (meer staal, een hardere klap). */
    public static final DeferredHolder<SoundEvent, SoundEvent> AK47_FIRE = register("ak47.fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> AK47_FIRE_FAR = register("ak47.fire_far");
    public static final DeferredHolder<SoundEvent, SoundEvent> AK47_CHARGE = register("ak47.charge");
    /** Hoofdstuk 103, stap 3: de AWP. Een zware knal die lang nagalmt, en de grendel: omhoog, achteruit, vooruit, omlaag. */
    public static final DeferredHolder<SoundEvent, SoundEvent> AWP_FIRE = register("awp.fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> AWP_FIRE_FAR = register("awp.fire_far");
    public static final DeferredHolder<SoundEvent, SoundEvent> AWP_BOLT = register("awp.bolt");
    /** Hoofdstuk 103, stap 4: de Remington 870. De knal van hagel, de pomp (achteruit en vooruit), en een patroon de buis in. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOTGUN_FIRE = register("shotgun.fire");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOTGUN_FIRE_FAR = register("shotgun.fire_far");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOTGUN_PUMP = register("shotgun.pump");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOTGUN_SHELL = register("shotgun.shell");
    /** Hoofdstuk 103: het magazijn van een lang wapen dat eruit komt en erin klikt (zwaarder dan dat van een pistool). */
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFLE_MAG_OUT = register("rifle.mag_out");
    public static final DeferredHolder<SoundEvent, SoundEvent> RIFLE_MAG_IN = register("rifle.mag_in");
    /** Hoofdstuk 103: de keuzeschakelaar (safe, semi, auto): een droge klik. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GUN_SELECTOR = register("gun.selector");
    /** Een pistool uit de holster trekken (stap 2). */
    public static final DeferredHolder<SoundEvent, SoundEvent> HOLSTER_DRAW = register("holster.draw");
    /** Hoofdstuk 72: een rugzak openritsen (de rits duurt 1,5 s, zie backpack/BackpackZip). */
    public static final DeferredHolder<SoundEvent, SoundEvent> BACKPACK_ZIP = register("backpack.zip");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, name)));
    }
}
