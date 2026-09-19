package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.drone.StarkDroneEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Wezens (entities) van de mod.
 */
public final class ModEntities {
    private ModEntities() {}

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, StarkTech.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<StarkDroneEntity>> STARK_DRONE = ENTITIES.register(
            "stark_drone", () -> EntityType.Builder.of(StarkDroneEntity::new, MobCategory.MISC)
                    .sized(0.7f, 0.4f)         // breedte en hoogte in blokken
                    .eyeHeight(0.2f)
                    .clientTrackingRange(8)    // tot 8 chunks ver zichtbaar
                    .fireImmune()
                    .noSave()                  // drones worden niet opgeslagen: E.D.I.T.H. roept ze opnieuw op
                    .noSummon()                // niet met /summon, want een drone zonder eigenaar vliegt meteen weg
                    .build("stark_drone"));

    /** Levenspunten, snelheid enz. van elk wezen. */
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(STARK_DRONE.get(), StarkDroneEntity.createAttributes().build());
    }
}
