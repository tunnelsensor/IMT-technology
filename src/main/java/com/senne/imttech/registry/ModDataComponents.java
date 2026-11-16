package com.senne.imttech.registry;

import com.mojang.serialization.Codec;
import com.senne.imttech.ImtTech;
import com.senne.imttech.imt.ImtOwner;
import com.senne.imttech.imt.ImtUpgrades;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * "Data components" zijn stukjes informatie die aan een item vastzitten, zoals de energie
 * of de eigenaar van een bril. Ze worden automatisch opgeslagen en naar spelers gestuurd.
 */
public final class ModDataComponents {
    private ModDataComponents() {}

    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ImtTech.MODID);

    static {
        // De detonator van C4 is nu de Frag Trigger (26 oktober): wat hij onthield, blijft.
        COMPONENTS.addAlias(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "detonator"),
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "frag_trigger"));
    }

    /**
     * Hoeveel energie (SE) er in de bril zit. Een kommagetal, zodat ook 0.5 per seconde verbruiken werkt.
     * (Een bril uit een oude wereld, met een heel getal erin, leest hij gewoon.)
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Double>> ENERGY =
            COMPONENTS.registerComponentType("energy", builder -> builder
                    .persistent(Codec.DOUBLE)
                    .networkSynchronized(ByteBufCodecs.DOUBLE)
                    // Voorkomt dat het item in je hand "wiebelt" telkens als de energie verandert.
                    );

    /**
     * In welke eenheid de energie van dit item staat (hoofdstuk 46): 2 = Forge Energy (sinds 4 november, tien keer zo
     * groot). Ontbreekt hij, dan is het een item van voor die tijd: de energie wordt bij het eerste lezen maal tien.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY_UNITS =
            COMPONENTS.registerComponentType("energy_units", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Hoe versleten een onderdeel van een serverrack is (8 oktober: te heet = slijten); 0 of niet = nieuw. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WEAR =
            COMPONENTS.registerComponentType("wear", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Wat er op een beschreven keycard staat (netwerk, nummer, niveau, naam). Een lege kaart heeft dit niet. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.logic.KeycardData>> KEYCARD =
            COMPONENTS.registerComponentType("keycard", builder -> builder
                    .persistent(com.senne.imttech.logic.KeycardData.CODEC)
                    .networkSynchronized(com.senne.imttech.logic.KeycardData.STREAM_CODEC));

    /** De twee hoeken die je met de Zone Marker aanklikte (hoofdstuk 10, zie ZoneSelection). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.security.zone.ZoneSelection>> ZONE_SELECTION =
            COMPONENTS.registerComponentType("zone_selection", builder -> builder
                    .persistent(com.senne.imttech.security.zone.ZoneSelection.CODEC)
                    .networkSynchronized(com.senne.imttech.security.zone.ZoneSelection.STREAM_CODEC));

    /**
     * Het etiket op een weggehakte beveiligingskern: onder welk nummer de server zijn instellingen
     * bewaart, en van wie hij is (zie PackedCore). Een nieuwe kern heeft dit niet.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.security.PackedCore>> PACKED_CORE =
            COMPONENTS.registerComponentType("packed_core", builder -> builder
                    .persistent(com.senne.imttech.security.PackedCore.CODEC)
                    .networkSynchronized(com.senne.imttech.security.PackedCore.STREAM_CODEC));

    /**
     * Een laserzender met doorgeknipte draden (hoofdstuk 33, jouw wens: "onthouden dat die laser kapot is"): hak je
     * hem weg, dan blijft hij kapot, ook als je hem weer neerzet. Alleen Repair wires maakt hem weer heel.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> LASER_CUT =
            COMPONENTS.registerComponentType("laser_cut", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    /**
     * Hoofdstuk 40: het IP waar je de vorige keer met deze Hacking Laptop op inlogde, zodat het inlogscherm het al
     * invult. Het wachtwoord onthoudt de laptop nooit.
     */
    /** Hoofdstuk 50: aan welke kern een I.M.T Phone hangt (het IP, de naam en het nummer van de telefoon). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.security.PhoneLink>> PHONE =
            COMPONENTS.registerComponentType("phone", builder -> builder
                    .persistent(com.senne.imttech.security.PhoneLink.CODEC)
                    .networkSynchronized(com.senne.imttech.security.PhoneLink.STREAM_CODEC));

    /**
     * Hoofdstuk 123: met welke security core een I.M.T-bril verbonden is (het IP, de naam, en welk wachtwoord), zie
     * imt/ImtUplink. Het staat op de bril zelf: geef je hem door, dan blijft hij verbonden.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.imt.ImtUplink>> GLASSES_UPLINK =
            COMPONENTS.registerComponentType("glasses_uplink", builder -> builder
                    .persistent(com.senne.imttech.imt.ImtUplink.CODEC)
                    .networkSynchronized(com.senne.imttech.imt.ImtUplink.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> LAST_IP =
            COMPONENTS.registerComponentType("last_ip", builder -> builder
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    /** Van welk blok een gebarsten blok of poeder komt (hoofdstuk 11, zie FromBlockItem). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.world.level.block.Block>> FROM_BLOCK =
            COMPONENTS.registerComponentType("from_block", builder -> builder
                    .persistent(net.minecraft.core.registries.BuiltInRegistries.BLOCK.byNameCodec())
                    .networkSynchronized(ByteBufCodecs.registry(Registries.BLOCK)));

    /** Van wie de bril is. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ImtOwner>> OWNER =
            COMPONENTS.registerComponentType("owner", builder -> builder
                    .persistent(ImtOwner.CODEC)
                    .networkSynchronized(ImtOwner.STREAM_CODEC));

    /** Welke functies aan staan (zie ImtFeature). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FEATURES =
            COMPONENTS.registerComponentType("features", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** In welke stand de satelliet staat (0 = precisie, 1 = bombardement, 2 = schuivende laser). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> SATELLITE_MODE =
            COMPONENTS.registerComponentType("satellite_mode", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** In welke vorm de escortedrones vliegen (zie DroneFormation; 0 = rondcirkelen). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ESCORT_FORMATION =
            COMPONENTS.registerComponentType("escort_formation", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Zit er een raketpakket op deze borstplaat? (Zie RocketPackItem.) */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> ROCKET_PACK =
            COMPONENTS.registerComponentType("rocket_pack", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    /** Welke upgrades er in een stuk harnas zitten (zie SuitUpgrades; ingebouwd aan de werkbank). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.workbench.SuitUpgrades>> SUIT_UPGRADES =
            COMPONENTS.registerComponentType("suit_upgrades", builder -> builder
                    .persistent(com.senne.imttech.workbench.SuitUpgrades.CODEC)
                    .networkSynchronized(com.senne.imttech.workbench.SuitUpgrades.STREAM_CODEC));

    /** Welke upgrades er in de bril zitten. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ImtUpgrades>> UPGRADES =
            COMPONENTS.registerComponentType("upgrades", builder -> builder
                    .persistent(ImtUpgrades.CODEC)
                    .networkSynchronized(ImtUpgrades.STREAM_CODEC));

    /**
     * Hoofdstuk 21: de printplaat van een robot of drone, met zijn 8 pinnen (zie BotBoard). Een nieuwe printplaat
     * heeft dit nog niet: die is leeg.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.bot.BotBoard>> BOT_BOARD =
            COMPONENTS.registerComponentType("bot_board", builder -> builder
                    .persistent(com.senne.imttech.bot.BotBoard.CODEC)
                    .networkSynchronized(com.senne.imttech.bot.BotBoard.STREAM_CODEC));

    /** Hoofdstuk 21: wat er in een Guard Robot-item zit (onderdelen, printplaat, naam; zie RobotData). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.robot.RobotData>> ROBOT_DATA =
            COMPONENTS.registerComponentType("robot_data", builder -> builder
                    .persistent(com.senne.imttech.robot.RobotData.CODEC)
                    .networkSynchronized(com.senne.imttech.robot.RobotData.STREAM_CODEC));

    /** Hoofdstuk 25: wat er in een Security Drone-item zit (printplaat, naam, programma; zie DroneData). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.robot.DroneData>> DRONE_DATA =
            COMPONENTS.registerComponentType("drone_data", builder -> builder
                    .persistent(com.senne.imttech.robot.DroneData.CODEC)
                    .networkSynchronized(com.senne.imttech.robot.DroneData.STREAM_CODEC));

    /** Hoofdstuk 108: wat er in een RC Car-item zit (naam, programma, energiecel, kofferbak; zie RcCarData). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.car.RcCarData>> RC_CAR_DATA =
            COMPONENTS.registerComponentType("rc_car_data", builder -> builder
                    .persistent(com.senne.imttech.car.RcCarData.CODEC)
                    .networkSynchronized(com.senne.imttech.car.RcCarData.STREAM_CODEC));

    /** Hoofdstuk 108: met welke RC Car een RC Controller gekoppeld is (zie RcLink). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.car.RcLink>> RC_LINK =
            COMPONENTS.registerComponentType("rc_link", builder -> builder
                    .persistent(com.senne.imttech.car.RcLink.CODEC)
                    .networkSynchronized(com.senne.imttech.car.RcLink.STREAM_CODEC));

    /**
     * Hoofdstuk 35: wat een EMP Trigger onthoudt (zijn nummer en waar zijn Remote EMP's zitten). Hetzelfde soort gegevens
     * als bij de Frag Trigger, maar apart, zodat een EMP Trigger nooit een Remote Frag laat afgaan en andersom.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.weapons.TriggerData>> EMP_TRIGGER =
            COMPONENTS.registerComponentType("emp_trigger", builder -> builder
                    .persistent(com.senne.imttech.weapons.TriggerData.CODEC)
                    .networkSynchronized(com.senne.imttech.weapons.TriggerData.STREAM_CODEC));

    /** Hoofdstuk 35: wat een Frag Trigger onthoudt (zijn nummer en waar zijn Remote Frags zitten). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.weapons.TriggerData>> FRAG_TRIGGER =
            COMPONENTS.registerComponentType("frag_trigger", builder -> builder
                    .persistent(com.senne.imttech.weapons.TriggerData.CODEC)
                    .networkSynchronized(com.senne.imttech.weapons.TriggerData.STREAM_CODEC));

    /** Hoofdstuk 35: hoe vol een energiecel is (0 tot 100). Geen = leeg. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> CELL_CHARGE =
            COMPONENTS.registerComponentType("cell_charge", builder -> builder
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT));

    /** Hoofdstuk 35: hoe vol de energiecel in een lasergeweer is (0 tot 100). Geen = er zit geen cel in. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Float>> GUN_CELL =
            COMPONENTS.registerComponentType("gun_cell", builder -> builder
                    .persistent(Codec.FLOAT)
                    .networkSynchronized(ByteBufCodecs.FLOAT));

    /**
     * Hoofdstuk 35: de kleur van de rook van een Smoke Grenade (jouw keuze: "kleur met kleurstof"). Geen = grijs. Je
     * zet hem met een recept: een smokebom en een kleurstof (zie data/imttech/recipe/smoke_grenade_*.json).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.world.item.DyeColor>> SMOKE_COLOR =
            COMPONENTS.registerComponentType("smoke_color", builder -> builder
                    .persistent(net.minecraft.world.item.DyeColor.CODEC)
                    .networkSynchronized(net.minecraft.world.item.DyeColor.STREAM_CODEC));

    /**
     * Hoofdstuk 69: een granaat die je opraapte terwijl hij al op scherp stond (jouw keuze: "de lont loopt door"). Het getal
     * is de speltijd (ticks, zie Level.getGameTime) waarop hij afgaat; geen = een gewone granaat. Zie weapons/GrenadeItem.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Long>> LIVE_FUSE =
            COMPONENTS.registerComponentType("live_fuse", builder -> builder
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG));

    /**
     * Hoofdstuk 69: een Remote EMP of Remote Frag die iemand met sneak + lege hand van de muur haalde. Hij blijft scherp en
     * van zijn eigenaar, aan diens trigger (jouw keuze). Zie weapons/LiveCharge.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.weapons.LiveCharge>> LIVE_CHARGE =
            COMPONENTS.registerComponentType("live_charge", builder -> builder
                    .persistent(com.senne.imttech.weapons.LiveCharge.CODEC)
                    .networkSynchronized(com.senne.imttech.weapons.LiveCharge.STREAM_CODEC));

    // ================================================================ Vuurwapens (hoofdstuk 52)

    /** Van welk materiaal een los onderdeel van een pistool is (polymeer, staal, titanium). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.guns.GunMaterial>> GUN_MATERIAL =
            COMPONENTS.registerComponentType("gun_material", builder -> builder
                    .persistent(com.senne.imttech.guns.GunMaterial.CODEC)
                    .networkSynchronized(com.senne.imttech.guns.GunMaterial.STREAM_CODEC));

    /** Hoe heel een los onderdeel nog is (0 tot 100); geen = nieuw. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PART_CONDITION =
            COMPONENTS.registerComponentType("part_condition", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Welke onderdelen er in een pistool zitten, en hoe heel (zie GunParts). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.guns.GunParts>> GUN_PARTS =
            COMPONENTS.registerComponentType("gun_parts", builder -> builder
                    .persistent(com.senne.imttech.guns.GunParts.CODEC)
                    .networkSynchronized(com.senne.imttech.guns.GunParts.STREAM_CODEC));

    /** Hoofdstuk 55: welke onderdelen er in een lasergeweer zitten, en hoe heel (zie weapons/LaserParts): ook de kern en de sling. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.weapons.LaserParts>> LASER_PARTS =
            COMPONENTS.registerComponentType("laser_parts", builder -> builder
                    .persistent(com.senne.imttech.weapons.LaserParts.CODEC)
                    .networkSynchronized(com.senne.imttech.weapons.LaserParts.STREAM_CODEC));

    /** Het magazijn dat in een pistool zit (hoeveel patronen, zie GunMagazine); geen = geen magazijn. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.guns.GunMagazine>> GUN_MAGAZINE =
            COMPONENTS.registerComponentType("gun_magazine", builder -> builder
                    .persistent(com.senne.imttech.guns.GunMagazine.CODEC)
                    .networkSynchronized(com.senne.imttech.guns.GunMagazine.STREAM_CODEC));

    /** Zit er een patroon in de kamer (15 + 1)? */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> GUN_CHAMBERED =
            COMPONENTS.registerComponentType("gun_chambered", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    /** Een storing: 1 = een huls die klem zit, 2 = een ketser (zie FirearmItem). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> GUN_JAM =
            COMPONENTS.registerComponentType("gun_jam", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Hoofdstuk 103: de stand van de keuzeschakelaar van een vuurwapen (het nummer in de rij van dat wapen, zie FirearmType). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> GUN_FIRE_MODE =
            COMPONENTS.registerComponentType("gun_fire_mode", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Hoofdstuk 103: een lege huls in de kamer van een wapen dat je zelf doorlaadt (een grendel of een pomp). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> GUN_SPENT =
            COMPONENTS.registerComponentType("gun_spent", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    /** Hoofdstuk 103: hangt er een Gun Sling aan een lang vuurwapen (dan past het in een sling-vakje)? */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> GUN_SLING =
            COMPONENTS.registerComponentType("gun_sling", builder -> builder
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL));

    /** Hoofdstuk 103, stap 5: de attachments op een vuurwapen (een kijker...), zie GunAttachments. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.guns.GunAttachments>> GUN_ATTACHMENTS =
            COMPONENTS.registerComponentType("gun_attachments", builder -> builder
                    .persistent(com.senne.imttech.guns.GunAttachments.CODEC)
                    .networkSynchronized(com.senne.imttech.guns.GunAttachments.STREAM_CODEC));

    /** Zoveel patronen zitten er in een magazijn. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MAG_ROUNDS =
            COMPONENTS.registerComponentType("mag_rounds", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Hoofdstuk 52 (stap 2): wat er in een holster of pouch zit (zie GearContents). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.guns.GearContents>> GEAR_CONTENTS =
            COMPONENTS.registerComponentType("gear_contents", builder -> builder
                    .persistent(com.senne.imttech.guns.GearContents.CODEC)
                    .networkSynchronized(com.senne.imttech.guns.GearContents.STREAM_CODEC));

    /** Hoofdstuk 126: het geluid en de instellingen van een afgebroken Alarm Speaker (nooit het geluid zelf, alleen zijn naam). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.alarm.AlarmSettings>> ALARM_SETTINGS =
            COMPONENTS.registerComponentType("alarm_settings", builder -> builder
                    .persistent(com.senne.imttech.alarm.AlarmSettings.CODEC)
                    .networkSynchronized(com.senne.imttech.alarm.AlarmSettings.STREAM_CODEC));

    /**
     * Hoofdstuk 130: het nummer van een SD-kaart en een samenvatting voor de tooltip. Nooit wat erop staat: dat staat op
     * de server (SdCards), want alles op een item gaat ook naar het spel van wie het vasthoudt.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.imttech.sdcard.SdCardData>> SD_CARD =
            COMPONENTS.registerComponentType("sd_card", builder -> builder
                    .persistent(com.senne.imttech.sdcard.SdCardData.CODEC)
                    .networkSynchronized(com.senne.imttech.sdcard.SdCardData.STREAM_CODEC));
}
