package com.senne.starktech.turret;

import com.mojang.serialization.Codec;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.network.BeamEffectPayload;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * De soorten torentjes. Ze hebben dezelfde kop en hetzelfde brein (TurretBlockEntity), maar
 * verschillen in wat ze nodig hebben en op wie ze schieten (zie TurretTargeting).
 *
 * - STARK:    jouw torentje. Raketten als munitie. Schiet op monsters (en spelers alleen met
 *             PvP, nooit jou of je team).
 * - HYDRA:    in de Hydra-bunker (stroom van de reactor). Schiet op spelers en drones.
 * - SECURITY: in de Stark Tower (stroom van de krachtcentrale). Schiet op indringers, en wie
 *             dat is, zegt de beveiligingskern van de toren (zie SecurityCores).
 *
 * ALLE torentjes hebben stroom van het net nodig (jouw wens, 27 september): zonder stroom
 * hangt de loop omlaag en doen ze niets, en elk schot kost stroom. Knip je de kabel naar een
 * torentje door (of zet je de schakelaar in de stroomkast uit), dan is hij uit.
 */
public enum TurretKind implements StringRepresentable {
    STARK,
    HYDRA,
    SECURITY;

    public static final Codec<TurretKind> CODEC = StringRepresentable.fromEnum(TurretKind::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Kun je er raketten in doen? (En heeft hij een eigenaar die hem neerzette?) */
    public boolean usesAmmo() {
        return this == STARK;
    }

    public double range() {
        return switch (this) {
            case STARK -> StarkConfig.Turrets.RANGE;
            case HYDRA -> StarkConfig.Turrets.HYDRA_RANGE;
            case SECURITY -> StarkConfig.Turrets.SECURITY_RANGE;
        };
    }

    public float laserDamage() {
        return switch (this) {
            case STARK -> StarkConfig.Turrets.LASER_DAMAGE;
            case HYDRA -> StarkConfig.Turrets.HYDRA_LASER_DAMAGE;
            case SECURITY -> StarkConfig.Turrets.SECURITY_LASER_DAMAGE;
        };
    }

    public int laserInterval() {
        return switch (this) {
            case STARK -> Math.round(StarkConfig.Turrets.LASER_INTERVAL);
            case HYDRA -> Math.round(StarkConfig.Turrets.HYDRA_LASER_INTERVAL);
            case SECURITY -> Math.round(StarkConfig.Turrets.SECURITY_LASER_INTERVAL);
        };
    }

    /** Blauw voor Stark-tech, rood voor Hydra. */
    public BeamEffectPayload.Kind beam() {
        return this == HYDRA ? BeamEffectPayload.Kind.HYDRA_LASER : BeamEffectPayload.Kind.DRONE_LASER;
    }

    /** Hoe hoog het schot klinkt (Hydra lager en grommiger). */
    public float pitch() {
        return this == HYDRA ? 0.7f : 1.2f;
    }
}
