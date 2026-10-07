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

    /** Hoeveel energie (SE) er in de bril zit. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENERGY =
            COMPONENTS.registerComponentType("energy", builder -> builder
                    .persistent(Codec.INT)
                    .networkSynchronized(ByteBufCodecs.VAR_INT)
                    // Voorkomt dat het item in je hand "wiebelt" telkens als de energie verandert.
                    );

    /** Wat er op een beschreven keycard staat (netwerk, nummer, niveau, naam). Een lege kaart heeft dit niet. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<com.senne.starktech.logic.KeycardData>> KEYCARD =
            COMPONENTS.registerComponentType("keycard", builder -> builder
                    .persistent(com.senne.starktech.logic.KeycardData.CODEC)
                    .networkSynchronized(com.senne.starktech.logic.KeycardData.STREAM_CODEC));

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
}
