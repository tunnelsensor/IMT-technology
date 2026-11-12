package com.senne.imttech.guns;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModSounds;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;

/**
 * De vuurwapens (hoofdstuk 52 en 103). Eerst was er één: de <b>Glock 19</b>. Met de grote update (hoofdstuk 103, jouw
 * wens: "meer guns, deze werken net zoals de Glock") komen erbij: de <b>MP5</b>, de AK-47, de AWP en de Remington 870.
 * De getallen staan in de config onder [guns], elk rijtje in deze volgorde. Wat hier vast staat, is wat het wapen
 * <i>is</i>: zijn kaliber, hoe het doorlaadt, welke vuurstanden het heeft, en of het een lang wapen is.
 */
public enum FirearmType {
    /** Het pistool: 9mm, zelfladend, één schot per klik. Geen schakelaar (een echte Glock heeft geen veiligheidspal). */
    GLOCK_19("glock_19", Caliber.MM9, Action.SEMI, false, 1.15f, FireMode.SEMI),
    /** De MP5 (hoofdstuk 103): 9mm, zelfladend, met een schakelaar: safe, één schot, drie schoten of automatisch. */
    MP5("mp5", Caliber.MM9, Action.SEMI, true, 1.25f, FireMode.SAFE, FireMode.SEMI, FireMode.BURST, FireMode.AUTO);

    /**
     * Hoe een wapen de volgende patroon in de kamer krijgt (jouw keuze, hoofdstuk 103: "je moet na elk schot op R drukken
     * om terug een round in de chamber te doen bij wapens die niet semi-auto zijn en zelf herladen door de recoil").
     */
    public enum Action {
        /** Zelfladend: de terugslag werpt de huls uit en laadt door (de Glock, de MP5, de AK-47). */
        SEMI,
        /** Een grendel die je zelf bedient met R (de AWP). */
        BOLT,
        /** Een pomp die je zelf bedient met R (de Remington 870). */
        PUMP
    }

    /** De standen van de keuzeschakelaar (jouw keuze: "schakelaar met ook safe"). */
    public enum FireMode {
        /** Het wapen schiet niet. */
        SAFE("safe"),
        /** Eén schot per klik. */
        SEMI("semi"),
        /** Drie schoten per klik. */
        BURST("burst"),
        /** Knop inhouden = blijven schieten. */
        AUTO("auto");

        public final String id;

        FireMode(String id) {
            this.id = id;
        }

        public Component text() {
            return Component.translatable("firemode.imttech." + id);
        }
    }

    /** De naam van het item (en van zijn modellen, zie models/firearm/). */
    public final String id;
    /** Het magazijn dat erin past. */
    public final String magazineId;
    /** Welke patroon het schiet. */
    public final Caliber caliber;
    /** Hoe het doorlaadt. */
    public final Action action;
    /**
     * Een lang wapen: het past niet in de holster; met een Gun Sling eraan (op de Gunsmith Table) hangt het in een
     * sling-vakje, op je rug of voor je borst.
     */
    public final boolean longGun;
    private final float zoom;
    private final FireMode[] modes;

    FirearmType(String id, Caliber caliber, Action action, boolean longGun, float zoom, FireMode... modes) {
        this.id = id;
        this.magazineId = id + "_magazine";
        this.caliber = caliber;
        this.action = action;
        this.longGun = longGun;
        this.zoom = zoom;
        this.modes = modes;
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

    // ================================================================ De werking

    /** Moet je zelf doorladen met R (een grendel of een pomp)? */
    public boolean manual() {
        return action != Action.SEMI;
    }

    /** Heeft dit wapen een buis onder de loop in plaats van een los magazijn (de Remington 870)? */
    public boolean tube() {
        return action == Action.PUMP;
    }

    /** Heeft dit wapen een keuzeschakelaar? */
    public boolean selectable() {
        return modes.length > 1;
    }

    /** Hoeveel standen de schakelaar heeft. */
    public int modes() {
        return modes.length;
    }

    /** De stand met dit nummer (een onbekend nummer: de stand waarin een nieuw wapen staat). */
    public FireMode mode(int index) {
        return index < 0 || index >= modes.length ? modes[defaultMode()] : modes[index];
    }

    /** In welke stand een nieuw wapen staat: de eerste die schiet (één schot). */
    public int defaultMode() {
        for (int i = 0; i < modes.length; i++) {
            if (modes[i] != FireMode.SAFE) {
                return i;
            }
        }
        return 0;
    }

    // ================================================================ De getallen (config [guns])

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

    /** Zoveel patronen gaan er in een gewoon magazijn (of in de buis). */
    public int capacity() {
        return Math.max(1, Math.round(of(ImtConfig.Guns.MAGAZINE_CAPACITY)));
    }

    /** Zoveel patronen gaan er in dit magazijn: het gewone of het lange (de "extended mag", een eigen item). */
    public int capacity(boolean extended) {
        return extended ? Math.max(1, Math.round(of(ImtConfig.Guns.EXTENDED_CAPACITY))) : capacity();
    }

    /** Hoe ver een schot van het midden af kan gaan (graden): minder als je richt. */
    public float spread(boolean aiming) {
        return aiming ? of(ImtConfig.Guns.SPREAD_AIMING) : of(ImtConfig.Guns.SPREAD);
    }

    /** Zo hard duwt een schot je zicht omhoog (graden); minder als je richt. */
    public float recoil(boolean aiming) {
        return aiming ? of(ImtConfig.Guns.RECOIL_AIMING) : of(ImtConfig.Guns.RECOIL);
    }

    /** Zoveel kogels of korrels per schot (hagel uit een shotgun; de rest één). */
    public int pellets() {
        return Math.max(1, Math.round(of(ImtConfig.Guns.PELLETS)));
    }

    /** Zo lang duurt doorladen (ticks): de slede loslaten, de spanhendel, de grendel of de pomp. */
    public float cycleTicks() {
        return this == GLOCK_19 ? ImtConfig.Guns.SLIDE_TICKS : of(ImtConfig.Guns.CYCLE_TICKS);
    }

    /** Tot zo ver horen bewakers een schot (blokken). */
    public float noise() {
        return this == GLOCK_19 ? ImtConfig.Guards.NOISE_GLOCK : of(ImtConfig.Guns.NOISE);
    }

    /** Zo ver zoom je in als je over het vizier richt (1 = niet). */
    public float zoom() {
        return zoom;
    }

    // ================================================================ De geluiden

    /** Het schot van dichtbij. */
    public SoundEvent fireSound() {
        return switch (this) {
            case GLOCK_19 -> ModSounds.GLOCK_FIRE.get();
            case MP5 -> ModSounds.MP5_FIRE.get();
        };
    }

    /** Het schot van ver weg (dof, met galm). */
    public SoundEvent fireFarSound() {
        return switch (this) {
            case GLOCK_19 -> ModSounds.GLOCK_FIRE_FAR.get();
            case MP5 -> ModSounds.MP5_FIRE_FAR.get();
        };
    }

    /** Doorladen: de slede (een pistool), de spanhendel, de grendel of de pomp. */
    public SoundEvent rackSound() {
        return switch (this) {
            case GLOCK_19 -> ModSounds.GLOCK_SLIDE.get();
            case MP5 -> ModSounds.MP5_CHARGE.get();
        };
    }

    /** De slede of de grendel die naar voren klapt nadat er een magazijn in ging. */
    public SoundEvent releaseSound() {
        return switch (this) {
            case GLOCK_19 -> ModSounds.GLOCK_SLIDE_RELEASE.get();
            case MP5 -> ModSounds.MP5_CHARGE.get();
        };
    }

    public SoundEvent magOutSound() {
        return longGun ? ModSounds.RIFLE_MAG_OUT.get() : ModSounds.GLOCK_MAG_OUT.get();
    }

    public SoundEvent magInSound() {
        return longGun ? ModSounds.RIFLE_MAG_IN.get() : ModSounds.GLOCK_MAG_IN.get();
    }
}
