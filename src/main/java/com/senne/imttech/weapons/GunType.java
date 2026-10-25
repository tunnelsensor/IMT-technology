package com.senne.imttech.weapons;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModSounds;
import net.minecraft.sounds.SoundEvent;

/**
 * De vier lasergeweren (hoofdstuk 35, jouw keuze: alle vier). Hun getallen staan in de config ([weapons], elk rijtje in
 * deze volgorde: pistool, geweer, sniper, shotgun); hier staat wat vast is: hoe je schiet en hoe ver je inzoomt.
 */
public enum GunType {
    /** Klein en snel: één klik = één schot. */
    PISTOL("laser_pistol", false, 1.25f),
    /** Het hoofdwapen: knop inhouden = automatisch. */
    RIFLE("laser_rifle", true, 1.4f),
    /** Eén zware straal die heel ver gaat, met een vizier dat 4 keer inzoomt. */
    SNIPER("laser_sniper", false, 4.0f),
    /** Een waaier van stralen, sterk van dichtbij. */
    SHOTGUN("laser_shotgun", false, 1.2f);

    /** De naam van het item (en van zijn modellen, zie models/weapon/). */
    public final String id;
    /** Knop inhouden = blijven schieten? */
    public final boolean automatic;
    /** Zo ver zoom je in als je richt (1 = niet). */
    public final float zoom;

    GunType(String id, boolean automatic, float zoom) {
        this.id = id;
        this.automatic = automatic;
        this.zoom = zoom;
    }

    public static GunType byId(int id) {
        GunType[] all = values();
        return all[Math.floorMod(id, all.length)];
    }

    /** Schade van één schot (bij de shotgun: van één straal). */
    public float damage() {
        return ImtConfig.Weapons.GUN_DAMAGE[ordinal()];
    }

    /** Zoveel ticks minstens tussen twee schoten. */
    public int interval() {
        return Math.max(1, Math.round(ImtConfig.Weapons.GUN_INTERVAL[ordinal()]));
    }

    /** Zoveel procent van een cel kost één schot. */
    public float cost() {
        return ImtConfig.Weapons.GUN_COST[ordinal()];
    }

    /** Zo ver reikt een schot (blokken). */
    public double range() {
        return ImtConfig.Weapons.GUN_RANGE[ordinal()];
    }

    /** Zo lang duurt herladen (ticks). */
    public int reloadTicks() {
        return Math.max(5, Math.round(ImtConfig.Weapons.GUN_RELOAD[ordinal()]));
    }

    /** Hoe ver een schot van het midden af kan gaan (graden): minder als je door het vizier richt. */
    public float spread(boolean aiming) {
        return aiming ? ImtConfig.Weapons.GUN_SPREAD_AIMING[ordinal()] : ImtConfig.Weapons.GUN_SPREAD[ordinal()];
    }

    /** Zoveel stralen per schot (de shotgun een waaier, de rest één). */
    public int rays() {
        return this == SHOTGUN ? Math.max(1, Math.round(ImtConfig.Weapons.SHOTGUN_RAYS)) : 1;
    }

    /** Zoveel schoten gaan er in een volle cel. */
    public int shotsPerCell() {
        return (int) Math.floor(EnergyCellItem.FULL / Math.max(0.01f, cost()) + 1.0E-4);
    }

    /** Het geluid van een schot. */
    public SoundEvent fireSound() {
        return switch (this) {
            case PISTOL -> ModSounds.LASER_PISTOL_FIRE.get();
            case RIFLE -> ModSounds.LASER_RIFLE_FIRE.get();
            case SNIPER -> ModSounds.LASER_SNIPER_FIRE.get();
            case SHOTGUN -> ModSounds.LASER_SHOTGUN_FIRE.get();
        };
    }
}
