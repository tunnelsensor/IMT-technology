package com.senne.imttech.turret;

import com.mojang.serialization.Codec;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.BeamEffectPayload;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * De soorten torentjes. Ze hebben dezelfde kop en hetzelfde brein (TurretBlockEntity), maar
 * verschillen in wat ze nodig hebben en op wie ze schieten (zie TurretTargeting).
 *
 * - ROCKET:    jouw torentje. Raketten als munitie. Schiet op monsters (en spelers alleen met
 *             PvP, nooit jou of je team).
 * - RAVEN:    in de Biebia's Industries-bunker (stroom van de reactor). Schiet op spelers en drones.
 * - SECURITY: in de Biebia's Industries Tower (stroom van de krachtcentrale). Schiet op indringers, en wie
 *             dat is, zegt de beveiligingskern van de toren (zie SecurityCores).
 *
 * ALLE torentjes hebben stroom van het net nodig (jouw wens, 27 september): zonder stroom
 * hangt de loop omlaag en doen ze niets, en elk schot kost stroom. Knip je de kabel naar een
 * torentje door (of zet je de schakelaar in de stroomkast uit), dan is hij uit.
 */
public enum TurretKind implements StringRepresentable {
    ROCKET,
    RAVEN,
    SECURITY;

    public static final Codec<TurretKind> CODEC = StringRepresentable.fromEnum(TurretKind::values);

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Kun je er raketten in doen? (En heeft hij een eigenaar die hem neerzette?) */
    public boolean usesAmmo() {
        return this == ROCKET;
    }

    public double range() {
        return switch (this) {
            case ROCKET -> ImtConfig.Turrets.RANGE;
            case RAVEN -> ImtConfig.Turrets.RAVEN_RANGE;
            case SECURITY -> ImtConfig.Turrets.SECURITY_RANGE;
        };
    }

    public float laserDamage() {
        return switch (this) {
            case ROCKET -> ImtConfig.Turrets.LASER_DAMAGE;
            case RAVEN -> ImtConfig.Turrets.RAVEN_LASER_DAMAGE;
            case SECURITY -> ImtConfig.Turrets.SECURITY_LASER_DAMAGE;
        };
    }

    public int laserInterval() {
        return switch (this) {
            case ROCKET -> Math.round(ImtConfig.Turrets.LASER_INTERVAL);
            case RAVEN -> Math.round(ImtConfig.Turrets.RAVEN_LASER_INTERVAL);
            case SECURITY -> Math.round(ImtConfig.Turrets.SECURITY_LASER_INTERVAL);
        };
    }

    /** Blauw voor I.M.T-tech, rood voor Raven. */
    public BeamEffectPayload.Kind beam() {
        return this == RAVEN ? BeamEffectPayload.Kind.RAVEN_LASER : BeamEffectPayload.Kind.DRONE_LASER;
    }

    /** Hoe hoog het schot klinkt (Raven lager en grommiger). */
    public float pitch() {
        return this == RAVEN ? 0.7f : 1.2f;
    }
}
