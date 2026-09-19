package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Eigen soorten schade. De instellingen staan in data/starktech/damage_type/,
 * de doodsberichten in de taalbestanden ("death.attack.starktech...").
 */
public final class ModDamageTypes {
    private ModDamageTypes() {}

    public static final ResourceKey<DamageType> DRONE_LASER =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(StarkTech.MODID, "drone_laser"));

    public static final ResourceKey<DamageType> ORBITAL_LASER =
            ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(StarkTech.MODID, "orbital_laser"));

    /** Schade van de satelliet (precisie-straal en schuivende laser). */
    public static DamageSource orbitalLaser(ServerLevel level, @Nullable Entity owner) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(ORBITAL_LASER), owner, owner);
    }

    /** Laserschade van een drone; de eigenaar telt als "aanvaller" (voor PvP-regels en buit). */
    public static DamageSource droneLaser(ServerLevel level, Entity drone, @Nullable Entity owner) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DRONE_LASER), drone, owner);
    }
}
