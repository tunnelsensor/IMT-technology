package com.senne.imttech.registry;

import com.senne.imttech.ImtTech;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Eigen soorten schade. De instellingen staan in data/imttech/damage_type/,
 * de doodsberichten in de taalbestanden ("death.attack.imttech...").
 */
public final class ModDamageTypes {
    private ModDamageTypes() {}

    public static final ResourceKey<DamageType> DRONE_LASER =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "drone_laser"));

    public static final ResourceKey<DamageType> ORBITAL_LASER =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "orbital_laser"));

    /** Schade van de satelliet (precisie-straal en schuivende laser). */
    public static DamageSource orbitalLaser(ServerLevel level, @Nullable Entity owner) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ORBITAL_LASER), owner, owner);
    }

    public static final ResourceKey<DamageType> LASER_WALL =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "laser_wall"));

    /** Schade van een laserwand (die heeft geen "aanvaller"). */
    public static DamageSource laserWall(ServerLevel level) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(LASER_WALL));
    }

    public static final ResourceKey<DamageType> TURRET_LASER =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "turret_laser"));

    /** Laserschade van een torentje; de eigenaar (als hij online is) telt als aanvaller. */
    public static DamageSource turretLaser(ServerLevel level, @Nullable Entity owner) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(TURRET_LASER), owner, owner);
    }

    public static final ResourceKey<DamageType> ROBOT_LASER =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "robot_laser"));

    public static final ResourceKey<DamageType> ROBOT_GUN =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "robot_gun"));

    /** Een schot van een guard robot (hoofdstuk 24): de robot is de aanvaller ("was lasered by Guard 1"). */
    public static DamageSource robotShot(ServerLevel level, Entity robot, boolean laser) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(laser ? ROBOT_LASER : ROBOT_GUN),
                robot, robot);
    }

    /** Laserschade van een drone; de eigenaar telt als "aanvaller" (voor PvP-regels en buit). */
    public static DamageSource droneLaser(ServerLevel level, Entity drone, @Nullable Entity owner) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DRONE_LASER), drone, owner);
    }
}
