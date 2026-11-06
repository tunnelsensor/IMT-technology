package com.senne.imttech.guns;

import com.senne.imttech.config.ImtConfig;
import net.minecraft.network.chat.Component;

/**
 * De vuurwapens (hoofdstuk 52). Voorlopig één: de <b>Glock 19</b> (jouw keuze: "ik wil als eerste glock de GLOCK 19
 * zoals deze van de Airsoftwinkel"). De getallen staan in de config onder [guns], elk rijtje in deze volgorde; zo kan
 * er later een tweede pistool bij zonder nieuwe code.
 */
public enum FirearmType {
    GLOCK_19("glock_19", "glock_19_magazine");

    /** De naam van het item (en van zijn modellen, zie models/firearm/). */
    public final String id;
    /** Het magazijn dat erin past. */
    public final String magazineId;

    FirearmType(String id, String magazineId) {
        this.id = id;
        this.magazineId = magazineId;
    }

    public static FirearmType byId(int id) {
        FirearmType[] all = values();
        return all[Math.floorMod(id, all.length)];
    }

    public Component text() {
        return Component.translatable("item.imttech." + id);
    }

    private float of(float[] values) {
        return values[Math.min(ordinal(), values.length - 1)];
    }

    /** Schade van één kogel van dichtbij (de loop en zijn materiaal doen er nog wat bij of af). */
    public float damage() {
        return of(ImtConfig.Guns.DAMAGE);
    }

    /** Zoveel ticks minstens tussen twee schoten (een Glock schiet zo snel als je klikt, maar niet sneller dan dit). */
    public int interval() {
        return Math.max(1, Math.round(of(ImtConfig.Guns.INTERVAL)));
    }

    /** Tot hier doet een kogel zijn volle schade (blokken)... */
    public double fullRange() {
        return of(ImtConfig.Guns.FULL_RANGE);
    }

    /** ...en zo ver vliegt hij hoogstens. */
    public double range() {
        return of(ImtConfig.Guns.RANGE);
    }

    /** Hoe snel de kogel vliegt (blokken per tick). */
    public double bulletSpeed() {
        return of(ImtConfig.Guns.BULLET_SPEED);
    }

    /** Zoveel patronen gaan er in een magazijn. */
    public int capacity() {
        return Math.max(1, Math.round(of(ImtConfig.Guns.MAGAZINE_CAPACITY)));
    }

    /** Hoe ver een schot van het midden af kan gaan (graden): minder als je richt. */
    public float spread(boolean aiming) {
        return aiming ? of(ImtConfig.Guns.SPREAD_AIMING) : of(ImtConfig.Guns.SPREAD);
    }

    /** Zo hard duwt een schot je zicht omhoog (graden); minder als je richt. */
    public float recoil(boolean aiming) {
        return aiming ? of(ImtConfig.Guns.RECOIL_AIMING) : of(ImtConfig.Guns.RECOIL);
    }

    /** Zo ver zoom je in als je richt (1 = niet). */
    public float zoom() {
        return 1.15f;
    }
}
