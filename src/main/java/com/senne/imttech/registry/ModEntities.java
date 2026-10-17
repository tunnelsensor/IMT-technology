package com.senne.imttech.registry;

import com.senne.imttech.ImtTech;
import com.senne.imttech.drone.DroneEntity;
import com.senne.imttech.raven.RavenSoldierEntity;
import com.senne.imttech.raid.RaidDroneEntity;
import com.senne.imttech.robot.GuardRobotEntity;
import com.senne.imttech.suit.MiniRocketEntity;
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
            DeferredRegister.create(Registries.ENTITY_TYPE, ImtTech.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<DroneEntity>> DRONE = ENTITIES.register(
            "drone", () -> EntityType.Builder.of(DroneEntity::new, MobCategory.MISC)
                    .sized(0.7f, 0.4f)         // breedte en hoogte in blokken
                    .eyeHeight(0.2f)
                    .clientTrackingRange(8)    // tot 8 chunks ver zichtbaar
                    .fireImmune()
                    .noSave()                  // drones worden niet opgeslagen: I.M.T roept ze opnieuw op
                    .noSummon()                // niet met /summon, want een drone zonder eigenaar vliegt meteen weg
                    .build("drone"));

    /** Fase 15: de vijandige drones van een raid. */
    public static final DeferredHolder<EntityType<?>, EntityType<RaidDroneEntity>> RAID_DRONE = ENTITIES.register(
            "raid_drone", () -> EntityType.Builder.of(RaidDroneEntity::new, MobCategory.MONSTER)
                    .sized(0.8f, 0.5f)
                    .eyeHeight(0.25f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build("raid_drone"));

    /** De soldaten van Raven, die de bunker bewaken. */
    public static final DeferredHolder<EntityType<?>, EntityType<RavenSoldierEntity>> RAVEN_SOLDIER = ENTITIES.register(
            "raven_soldier", () -> EntityType.Builder.of(RavenSoldierEntity::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f)        // zo groot als een speler
                    .eyeHeight(1.62f)
                    .clientTrackingRange(8)
                    .build("raven_soldier"));

    /** Een beveiligingsdrone van de Biebia's Industries Tower (hoort bij een beveiligingskern). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.senne.imttech.security.SecurityDroneEntity>> SECURITY_DRONE =
            ENTITIES.register("security_drone", () -> EntityType.Builder
                    .of(com.senne.imttech.security.SecurityDroneEntity::new, MobCategory.MISC)
                    .sized(0.7f, 0.4f)
                    .eyeHeight(0.2f)
                    .clientTrackingRange(8)
                    .fireImmune()
                    .build("security_drone"));

    /**
     * Hoofdstuk 21: een guard robot die je zelf samenstelt. MISC en geen MONSTER: je torentjes laten hem
     * met rust, en hij verdwijnt nooit vanzelf.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<GuardRobotEntity>> GUARD_ROBOT = ENTITIES.register(
            "guard_robot", () -> EntityType.Builder.<GuardRobotEntity>of(GuardRobotEntity::new, MobCategory.MISC)
                    .sized(0.6f, 1.9f)
                    .eyeHeight(1.6f)
                    .clientTrackingRange(10)
                    .build("guard_robot"));

    /**
     * Hoofdstuk 25: een security drone die je zelf bouwt en programmeert (een vliegende guard robot). Zo klein dat
     * hij in één blok past: hij vliegt blok voor blok.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<com.senne.imttech.robot.GuardDroneEntity>> GUARD_DRONE =
            ENTITIES.register("guard_drone", () -> EntityType.Builder
                    .<com.senne.imttech.robot.GuardDroneEntity>of(com.senne.imttech.robot.GuardDroneEntity::new, MobCategory.MISC)
                    .sized(0.8f, 0.5f)
                    .eyeHeight(0.25f)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build("guard_drone"));

    /** Een mini-raket uit het raketpakket van het harnas. */
    public static final DeferredHolder<EntityType<?>, EntityType<MiniRocketEntity>> MINI_ROCKET = ENTITIES.register(
            "mini_rocket", () -> EntityType.Builder.<MiniRocketEntity>of(MiniRocketEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(8)
                    .updateInterval(1)         // elke tick bijwerken: hij stuurt steeds bij
                    .noSave()
                    .build("mini_rocket"));

    /** Een raket van de satelliet: valt uit de ruimte op zijn doel (zie OrbitalMissileEntity). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.senne.imttech.satellite.OrbitalMissileEntity>> ORBITAL_MISSILE =
            ENTITIES.register("orbital_missile", () -> EntityType.Builder.<com.senne.imttech.satellite.OrbitalMissileEntity>of(
                            com.senne.imttech.satellite.OrbitalMissileEntity::new, MobCategory.MISC)
                    .sized(0.6f, 0.6f)
                    .clientTrackingRange(20)   // 320 blokken: je ziet hem van ver vallen
                    .updateInterval(1)
                    .fireImmune()
                    .noSave()
                    .build("orbital_missile"));

    /** Levenspunten, snelheid enz. van elk wezen. */
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(DRONE.get(), DroneEntity.createAttributes().build());
        event.put(RAID_DRONE.get(), RaidDroneEntity.createAttributes().build());
        event.put(RAVEN_SOLDIER.get(), RavenSoldierEntity.createAttributes().build());
        event.put(SECURITY_DRONE.get(), com.senne.imttech.security.SecurityDroneEntity.createAttributes().build());
        event.put(GUARD_ROBOT.get(), GuardRobotEntity.createAttributes().build());
        event.put(GUARD_DRONE.get(), com.senne.imttech.robot.GuardDroneEntity.createAttributes().build());
    }
}
