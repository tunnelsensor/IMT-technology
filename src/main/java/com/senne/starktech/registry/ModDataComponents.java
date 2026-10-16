package com.senne.starktech.registry;

import com.mojang.serialization.Codec;
import com.senne.starktech.StarkTech;
import com.senne.starktech.edith.EdithOwner;
import com.senne.starktech.edith.EdithUpgrades;
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
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, StarkTech.MODID);

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

    /** Hoe versleten een onderdeel van een serverrack is (8 oktober: te heet = slijten); 0 of niet = nieuw. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WEAR =
            COMPONENTS.registerComponentType("wear", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Wat er op een beschreven keycard staat (netwerk, nummer, niveau, naam). Een lege kaart heeft dit niet. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.starktech.logic.KeycardData>> KEYCARD =
            COMPONENTS.registerComponentType("keycard", builder -> builder
                    .persistent(com.senne.starktech.logic.KeycardData.CODEC)
                    .networkSynchronized(com.senne.starktech.logic.KeycardData.STREAM_CODEC));

    /** De twee hoeken die je met de Zone Marker aanklikte (hoofdstuk 10, zie ZoneSelection). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.starktech.security.zone.ZoneSelection>> ZONE_SELECTION =
            COMPONENTS.registerComponentType("zone_selection", builder -> builder
                    .persistent(com.senne.starktech.security.zone.ZoneSelection.CODEC)
                    .networkSynchronized(com.senne.starktech.security.zone.ZoneSelection.STREAM_CODEC));

    /** De tijd op een C4 (Timer), in seconden (hoofdstuk 10). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> C4_TIMER =
            COMPONENTS.registerComponentType("c4_timer", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT));

    /** Wat een detonator onthoudt: zijn nummer en waar zijn ladingen zitten (hoofdstuk 10). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.starktech.c4.DetonatorData>> DETONATOR =
            COMPONENTS.registerComponentType("detonator", builder -> builder
                    .persistent(com.senne.starktech.c4.DetonatorData.CODEC)
                    .networkSynchronized(com.senne.starktech.c4.DetonatorData.STREAM_CODEC));

    /**
     * Het etiket op een weggehakte beveiligingskern: onder welk nummer de server zijn instellingen
     * bewaart, en van wie hij is (zie PackedCore). Een nieuwe kern heeft dit niet.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.starktech.security.PackedCore>> PACKED_CORE =
            COMPONENTS.registerComponentType("packed_core", builder -> builder
                    .persistent(com.senne.starktech.security.PackedCore.CODEC)
                    .networkSynchronized(com.senne.starktech.security.PackedCore.STREAM_CODEC));

    /** Van welk blok een gebarsten blok of poeder komt (hoofdstuk 11, zie FromBlockItem). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<net.minecraft.world.level.block.Block>> FROM_BLOCK =
            COMPONENTS.registerComponentType("from_block", builder -> builder
                    .persistent(net.minecraft.core.registries.BuiltInRegistries.BLOCK.byNameCodec())
                    .networkSynchronized(ByteBufCodecs.registry(Registries.BLOCK)));

    /** Van wie de bril is. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EdithOwner>> OWNER =
            COMPONENTS.registerComponentType("owner", builder -> builder
                    .persistent(EdithOwner.CODEC)
                    .networkSynchronized(EdithOwner.STREAM_CODEC));

    /** Welke functies aan staan (zie EdithFeature). */
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
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.starktech.workbench.SuitUpgrades>> SUIT_UPGRADES =
            COMPONENTS.registerComponentType("suit_upgrades", builder -> builder
                    .persistent(com.senne.starktech.workbench.SuitUpgrades.CODEC)
                    .networkSynchronized(com.senne.starktech.workbench.SuitUpgrades.STREAM_CODEC));

    /** Welke upgrades er in de bril zitten. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EdithUpgrades>> UPGRADES =
            COMPONENTS.registerComponentType("upgrades", builder -> builder
                    .persistent(EdithUpgrades.CODEC)
                    .networkSynchronized(EdithUpgrades.STREAM_CODEC));

    /**
     * Hoofdstuk 21: de printplaat van een robot of drone, met zijn 8 pinnen (zie BotBoard). Een nieuwe printplaat
     * heeft dit nog niet: die is leeg.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.starktech.bot.BotBoard>> BOT_BOARD =
            COMPONENTS.registerComponentType("bot_board", builder -> builder
                    .persistent(com.senne.starktech.bot.BotBoard.CODEC)
                    .networkSynchronized(com.senne.starktech.bot.BotBoard.STREAM_CODEC));

    /** Hoofdstuk 21: wat er in een Guard Robot-item zit (onderdelen, printplaat, naam; zie RobotData). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.starktech.robot.RobotData>> ROBOT_DATA =
            COMPONENTS.registerComponentType("robot_data", builder -> builder
                    .persistent(com.senne.starktech.robot.RobotData.CODEC)
                    .networkSynchronized(com.senne.starktech.robot.RobotData.STREAM_CODEC));

    /** Hoofdstuk 25: wat er in een Security Drone-item zit (printplaat, naam, programma; zie DroneData). */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.starktech.robot.DroneData>> DRONE_DATA =
            COMPONENTS.registerComponentType("drone_data", builder -> builder
                    .persistent(com.senne.starktech.robot.DroneData.CODEC)
                    .networkSynchronized(com.senne.starktech.robot.DroneData.STREAM_CODEC));
}
