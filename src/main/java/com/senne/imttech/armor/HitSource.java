package com.senne.imttech.armor;

import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;

/**
 * Een treffer waarvan we weten waar hij zat (hoofdstuk 109): een kogel (BulletEntity) of de stralen van een lasergeweer
 * (LaserGuns) rekenen dat al uit voor de headshot. Jouw keuze: een headshot gaat langs de plates, dus de plates moeten dat ook
 * weten. Verder is het precies dezelfde schade als altijd (zelfde soort, schutter en plek).
 *
 * {@code bodyShare}: welk deel van de schade onder het hoofd zat (1 = alles op het lijf, 0 = een headshot). Een laser shotgun
 * telt zijn acht stralen samen tot één klap; zitten er drie in het hoofd en vijf op het lijf, dan vangt de plate alleen het
 * deel van die vijf op.
 */
public class HitSource extends DamageSource {
    private final float bodyShare;

    private HitSource(DamageSource base, float bodyShare) {
        this(base, bodyShare, base.sourcePositionRaw());
    }

    private HitSource(DamageSource base, float bodyShare, @org.jetbrains.annotations.Nullable net.minecraft.world.phys.Vec3 from) {
        super(base.typeHolder(), base.getDirectEntity(), base.getEntity(), from);
        this.bodyShare = Mth.clamp(bodyShare, 0, 1);
    }

    /**
     * Dezelfde schade, met erbij of hij in het hoofd zat en van waar hij kwam (bij een kogel: een stukje terug langs zijn baan;
     * zo klopt de kant ook als de schutter intussen dood of weg is).
     */
    public static DamageSource of(DamageSource base, boolean head, net.minecraft.world.phys.Vec3 from) {
        return new HitSource(base, head ? 0 : 1, from);
    }

    /** Dezelfde schade, met erbij of hij in het hoofd zat. */
    public static DamageSource of(DamageSource base, boolean head) {
        return new HitSource(base, head ? 0 : 1);
    }

    /** Dezelfde schade, met erbij welk deel ervan op het lijf zat (0 tot 1). */
    public static DamageSource withBodyShare(DamageSource base, float bodyShare) {
        return new HitSource(base, bodyShare);
    }

    /** Zat deze treffer helemaal in het hoofd? */
    public boolean head() {
        return bodyShare <= 0;
    }

    /** Welk deel van de schade op het lijf zat (daar vangt een plate een stuk van op). */
    public float bodyShare() {
        return bodyShare;
    }
}
