package com.senne.imttech.armor;

import net.minecraft.world.damagesource.DamageSource;

/**
 * Een treffer waarvan we weten of hij in het hoofd zat (hoofdstuk 109): een kogel (BulletEntity) of de straal van een
 * lasergeweer (LaserGuns) rekent dat al uit voor de headshot. Jouw keuze: een headshot gaat langs de plates, dus de plates
 * moeten dat ook weten. Verder is het precies dezelfde schade als altijd (zelfde soort, schutter en plek).
 */
public class HitSource extends DamageSource {
    private final boolean head;

    private HitSource(DamageSource base, boolean head) {
        super(base.typeHolder(), base.getDirectEntity(), base.getEntity(), base.sourcePositionRaw());
        this.head = head;
    }

    /** Dezelfde schade, met erbij of hij in het hoofd zat. */
    public static DamageSource of(DamageSource base, boolean head) {
        return new HitSource(base, head);
    }

    /** Zat deze treffer in het hoofd? */
    public boolean head() {
        return head;
    }
}
