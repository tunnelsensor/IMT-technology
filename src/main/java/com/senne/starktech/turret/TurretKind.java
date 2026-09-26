package com.senne.starktech.turret;

import com.mojang.serialization.Codec;
import com.senne.starktech.network.BeamEffectPayload;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * De soorten torentjes. Ze hebben dezelfde kop en hetzelfde brein (TurretBlockEntity), maar
 * verschillen in wat ze nodig hebben en op wie ze schieten (zie TurretTargeting).
 *
 * - STARK:    jouw torentje. Laser op stroom van het net, raketten als munitie. Schiet op
 *             monsters (en spelers alleen met PvP, nooit jou of je team).
 * - HYDRA:    in de Hydra-bunker. Geen stroom of munitie nodig; schiet op spelers en drones.
 * - SECURITY: in de Stark Tower. Geen stroom of munitie nodig; schiet op indringers, en wie
 *             dat is, zegt de beveiligingskern van de toren (zie SecurityCores).
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

    /** Haalt de laser stroom van het net? */
    public boolean needsPower() {
        return this == STARK;
    }

    /** Kun je er raketten in doen? (En heeft hij een eigenaar die hem neerzette?) */
    public boolean usesAmmo() {
        return this == STARK;
    }

    public double range() {
        return switch (this) {
            case STARK -> TurretBalance.RANGE;
            case HYDRA -> TurretBalance.HYDRA_RANGE;
            case SECURITY -> TurretBalance.SECURITY_RANGE;
        };
    }

    public float laserDamage() {
        return switch (this) {
            case STARK -> TurretBalance.LASER_DAMAGE;
            case HYDRA -> TurretBalance.HYDRA_LASER_DAMAGE;
            case SECURITY -> TurretBalance.SECURITY_LASER_DAMAGE;
        };
    }

    public int laserInterval() {
        return switch (this) {
            case STARK -> TurretBalance.LASER_INTERVAL;
            case HYDRA -> TurretBalance.HYDRA_LASER_INTERVAL;
            case SECURITY -> TurretBalance.SECURITY_LASER_INTERVAL;
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
