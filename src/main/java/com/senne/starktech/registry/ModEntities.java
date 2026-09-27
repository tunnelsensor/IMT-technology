package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.hydra.HydraSoldierEntity;
import com.senne.starktech.raid.RaidDroneEntity;
import com.senne.starktech.suit.MiniRocketEntity;
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

    /** Fase 15: de vijandige drones van een raid. */
    public static final DeferredHolder<EntityType<?>, EntityType<RaidDroneEntity>> RAID_DRONE = ENTITIES.register(
            "raid_drone", () -> EntityType.Builder.of(RaidDroneEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 0.5f)
                    .eyeHeight(0.25f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build("raid_drone"));

    /** De soldaten van Hydra, die de bunker bewaken. */
    public static final DeferredHolder<EntityType<?>, EntityType<HydraSoldierEntity>> HYDRA_SOLDIER = ENTITIES.register(
            "hydra_soldier", () -> EntityType.Builder.of(HydraSoldierEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)        // zo groot als een speler
                    .eyeHeight(1.62f)
                    .clientTrackingRange(8)
                    .build("hydra_soldier"));

    /** Een beveiligingsdrone van de Stark Tower (hoort bij een beveiligingskern). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.senne.starktech.security.SecurityDroneEntity>> SECURITY_DRONE =
            ENTITIES.register("security_drone", () -> EntityType.Builder
                    .of(com.senne.starktech.security.SecurityDroneEntity::new, MobCategory.MISC)
                    .sized(0.7f, 0.4f)
                    .eyeHeight(0.2f)
                    .clientTrackingRange(8)
                    .fireImmune()
                    .build("security_drone"));

    /** Een mini-raket uit het raketpakket van het harnas. */
    public static final DeferredHolder<EntityType<?>, EntityType<MiniRocketEntity>> MINI_ROCKET = ENTITIES.register(
            "mini_rocket", () -> EntityType.Builder.<MiniRocketEntity>of(MiniRocketEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(8)
                    .updateInterval(1)         // elke tick bijwerken: hij stuurt steeds bij
                    .noSave()
                    .build("mini_rocket"));

    /** Een raket van de satelliet: valt uit de ruimte op zijn doel (zie OrbitalMissileEntity). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.senne.starktech.satellite.OrbitalMissileEntity>> ORBITAL_MISSILE =
            ENTITIES.register("orbital_missile", () -> EntityType.Builder.<com.senne.starktech.satellite.OrbitalMissileEntity>of(
                            com.senne.starktech.satellite.OrbitalMissileEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(20)   // 320 blokken: je ziet hem van ver vallen
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build("orbital_missile"));

    /** Levenspunten, snelheid enz. van elk wezen. */
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(STARK_DRONE.get(), StarkDroneEntity.createAttributes().build());
        event.put(RAID_DRONE.get(), RaidDroneEntity.createAttributes().build());
        event.put(HYDRA_SOLDIER.get(), HydraSoldierEntity.createAttributes().build());
        event.put(SECURITY_DRONE.get(), com.senne.starktech.security.SecurityDroneEntity.createAttributes().build());
    }
}
