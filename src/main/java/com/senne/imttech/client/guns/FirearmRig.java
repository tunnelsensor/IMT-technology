package com.senne.imttech.client.guns;

import com.senne.imttech.guns.FirearmType;
import com.senne.imttech.guns.GunPart;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

/**
 * Hoe een pistool in je eigen beeld hangt en beweegt (hoofdstuk 52), in de maten van het model (zie
 * tools/generate_gun_models.py: 16 = één blok, de loop wijst naar -z en ligt op x = 8). Zoals GunRig voor de
 * lasergeweren: de greep (je rechterhand), de steun (je linkerhand), het vizier (voor je oog als je richt), de loop,
 * waar het magazijn uit de greep komt, en een paar getallen voor de animaties.
 */
public final class FirearmRig {
    /** Hoe groot het pistool in beeld is (1 = 16 pixels van het model per blok). */
    public final float scale;
    public final Vector3f grip;
    public final Vector3f support;
    public final Vector3f sight;
    public final Vector3f muzzle;
    /** Waar het magazijn in de greep zit (zijn bovenkant), en de richting waarin het eruit schuift. */
    public final Vector3f magazine;
    public final Vector3f magazineAxis;
    /** Waar de uitwerpopening zit (daar steekt een klemzittende huls uit). */
    public final Vector3f port;
    /** Waar de greep in je beeld zit als je vanuit de heup schiet (blokken: rechts, omhoog, naar achteren). */
    public final Vector3f hip;
    /** Zo ver voor je oog zit het vizier als je richt (blokken). */
    public final float sightDistance;
    /** Zo ver schuift de slede terug bij een schot (pixels van het model), en zo lang duurt dat (ticks). */
    public final float cycle;
    public final float cycleTicks;
    /** Zo ver springt het pistool naar achteren en omhoog bij een schot. */
    public final float kickBack;
    public final float kickUp;

    private FirearmRig(float scale, Vector3f grip, Vector3f support, Vector3f sight, Vector3f muzzle, Vector3f magazine,
                       Vector3f magazineAxis, Vector3f port, Vector3f hip, float sightDistance, float cycle, float cycleTicks,
                       float kickBack, float kickUp) {
        this.scale = scale;
        this.grip = grip;
        this.support = support;
        this.sight = sight;
        this.muzzle = muzzle;
        this.magazine = magazine;
        this.magazineAxis = magazineAxis;
        this.port = port;
        this.hip = hip;
        this.sightDistance = sightDistance;
        this.cycle = cycle;
        this.cycleTicks = cycleTicks;
        this.kickBack = kickBack;
        this.kickUp = kickUp;
    }

    private static final float GRIP_ANGLE = (float) Math.toRadians(22.5);

    private static final FirearmRig GLOCK_19 = new FirearmRig(0.44f,
            new Vector3f(8, 6.2f, 12.6f), new Vector3f(7.0f, 5.0f, 11.8f), new Vector3f(8, 13.55f, 13.3f),
            new Vector3f(8, 11.75f, 0.3f), new Vector3f(8, 3.3f, 12.0f),
            new Vector3f(0, -(float) Math.cos(GRIP_ANGLE), (float) Math.sin(GRIP_ANGLE)),
            new Vector3f(9.2f, 12.4f, 4.0f),
            new Vector3f(0.21f, -0.21f, -0.42f), 0.46f, 2.6f, 2, 0.055f, 6.5f);

    public static FirearmRig of(FirearmType type) {
        return GLOCK_19;
    }

    /**
     * Waar elk onderdeel in het model zit (voor de Gunsmith Table: waar je het neerlegt), en hoe ver het uit elkaar
     * geschoven ligt als het er nog niet in zit ("exploded view").
     */
    public static AABB bounds(GunPart part) {
        return switch (part) {
            case FRAME -> new AABB(6.9, 2.4, 0.9, 9.1, 10.6, 15.6);
            case SLIDE -> new AABB(6.8, 10.5, 0.7, 9.2, 13.8, 14.6);
            case BARREL -> new AABB(7.5, 11.2, 0.3, 9.3, 12.9, 9.6);
            case SPRINGS -> new AABB(7.6, 10.7, 0.4, 8.4, 11.3, 8.0);
            case STRIKER -> new AABB(7.7, 11.4, 9.7, 8.3, 11.9, 14.7);
            case TRIGGER -> new AABB(7.6, 7.8, 8.5, 8.4, 9.3, 9.1);
        };
    }

    /**
     * Hoe ver een onderdeel uit elkaar geschoven ligt op de Gunsmith Table als het er niet in zit (model-pixels). Sinds
     * hoofdstuk 54 (jouw notitie: "je kunt niet goed aan elk onderdeel") liggen ze verder uit elkaar, elk in zijn eigen
     * hoek: de slede erboven, de loop en de veren ervoor, de slagpin erachter, de trekker eronder.
     */
    public static Vector3f exploded(GunPart part) {
        return switch (part) {
            case FRAME -> new Vector3f(0, 0, 0);
            case SLIDE -> new Vector3f(0, 6.0f, 0);
            case BARREL -> new Vector3f(0, 2.6f, -5.5f);
            case SPRINGS -> new Vector3f(0, -2.2f, -7.0f);
            case STRIKER -> new Vector3f(0, 4.0f, 4.5f);
            case TRIGGER -> new Vector3f(0, -8.0f, -3.0f);
        };
    }
}
