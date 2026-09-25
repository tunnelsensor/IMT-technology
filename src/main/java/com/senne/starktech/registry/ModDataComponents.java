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

    /** Welke upgrades er in de bril zitten. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<EdithUpgrades>> UPGRADES =
            COMPONENTS.registerComponentType("upgrades", builder -> builder
                    .persistent(EdithUpgrades.CODEC)
                    .networkSynchronized(EdithUpgrades.STREAM_CODEC));
}
