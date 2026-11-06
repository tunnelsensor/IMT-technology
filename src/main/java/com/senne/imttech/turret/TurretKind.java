package com.senne.imttech.turret;

import com.mojang.serialization.Codec;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.BeamEffectPayload;
import java.util.Locale;
import net.minecraft.util.StringRepresentable;

/**
 * De twee soorten torentjes (hoofdstuk 54, jouw notitie: "er mogen maar 2 types turrets zijn: laser turret en rocket
 * turret, elke building gebruikt deze, ze worden aangestuurd via een security OS"). Ze hebben dezelfde kop en hetzelfde
 * brein (TurretBlockEntity), maar verschillen in wat ze nodig hebben en op wie ze schieten (zie TurretTargeting).
 *
 * - ROCKET:   raketten als munitie, en een laser. Hangt hij met een netwerkkabel aan jouw kern, dan volgt hij de regels
 *             van die kern (de app Access); los werkt hij op zichzelf: monsters, en spelers alleen met PvP.
 * - SECURITY: de <b>Laser Turret</b>: alleen een laser. Hij hoort altijd bij een beveiligingskern (via zijn netwerkkabel)
 *             en schiet op wie die kern een indringer noemt: in de Biebia's Industries Tower en de bunker is dat iedereen
 *             (de kern is van niemand tot je hem hackt), bij jou wat je in de app Access instelt.
 *
 * Het oude Raven-torentje van de bunker is sinds hoofdstuk 54 ook een laserturret (het blok raven_turret bestaat nog
 * voor oude werelden en gedraagt zich precies zo).
 *
 * ALLE torentjes hebben stroom van het net nodig (jouw wens, 27 september): zonder stroom
 * hangt de loop omlaag en doen ze niets, en elk schot kost stroom. Knip je de kabel naar een
 * torentje door (of zet je de schakelaar in de stroomkast uit), dan is hij uit.
 */
public enum TurretKind implements StringRepresentable {
    ROCKET,
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
        return this == ROCKET ? ImtConfig.Turrets.RANGE : ImtConfig.Turrets.SECURITY_RANGE;
    }

    public float laserDamage() {
        return this == ROCKET ? ImtConfig.Turrets.LASER_DAMAGE : ImtConfig.Turrets.SECURITY_LASER_DAMAGE;
    }

    public int laserInterval() {
        return Math.round(this == ROCKET ? ImtConfig.Turrets.LASER_INTERVAL : ImtConfig.Turrets.SECURITY_LASER_INTERVAL);
    }

    /** Blauw: I.M.T-tech. */
    public BeamEffectPayload.Kind beam() {
        return BeamEffectPayload.Kind.DRONE_LASER;
    }

    /** Hoe hoog het schot klinkt. */
    public float pitch() {
        return 1.2f;
    }
}
