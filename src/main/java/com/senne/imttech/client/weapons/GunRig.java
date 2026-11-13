package com.senne.imttech.client.weapons;

import com.senne.imttech.weapons.GunType;
import org.joml.Vector3f;

/**
 * Hoe elk lasergeweer in je eigen beeld hangt en beweegt (hoofdstuk 35). Alle punten zijn in de maten van het model (zie
 * tools/generate_weapon_models.py: 16 = één blok, de loop wijst naar -z en ligt op x = 8):
 * <ul>
 *   <li>{@code grip}: waar je rechterhand de greep vasthoudt;</li>
 *   <li>{@code support}: waar je linkerhand het geweer ondersteunt (de handbescherming, of je andere hand);</li>
 *   <li>{@code sight}: het punt waar je doorheen kijkt als je richt (het vizier); dat komt recht voor je oog;</li>
 *   <li>{@code muzzle}: waar de laser uit de loop komt;</li>
 *   <li>{@code cell}: waar de cel zit (daar gaat je linkerhand heen bij herladen);</li>
 *   <li>{@code display}: het lichtbalkje dat laat zien hoe vol de cel is (links op het geweer).</li>
 * </ul>
 * En een paar getallen voor de animaties: hoe groot het geweer in beeld is, hoe ver het bewegende deel (slede, grendel,
 * pomp) terugschiet, en hoe hard het schot je zicht omhoog duwt.
 */
public final class GunRig {
    /** Hoe groot het geweer in beeld is (1 = 16 pixels van het model per blok). */
    public final float scale;
    public final Vector3f grip;
    public final Vector3f support;
    public final Vector3f sight;
    public final Vector3f muzzle;
    public final Vector3f cell;
    /** Het lichtbalkje: het begin (achteraan) en hoe lang het is (naar voren, in -z). */
    public final Vector3f display;
    public final float displayLength;
    /** Zit het balkje op het bewegende deel (de slede van het pistool)? */
    public final boolean displayMoves;
    /** Waar de greep in je beeld zit als je vanuit de heup schiet (blokken: rechts, omhoog, naar achteren). */
    public final Vector3f hip;
    /** Zo ver voor je oog zit het vizier als je richt (blokken). */
    public final float sightDistance;
    /** Zo ver schuift het bewegende deel terug bij een schot (pixels van het model)... */
    public final float cycle;
    /** ...en zo lang duurt dat (ticks). */
    public final float cycleTicks;
    /** Zo hard duwt een schot je zicht omhoog (graden). */
    public final float cameraKick;
    /** Zo ver springt het geweer naar achteren en omhoog bij een schot. */
    public final float kickBack;
    public final float kickUp;

    /**
     * Hoofdstuk 103, stap 7: waar de voet van een kijker (een Red Dot Sight of de Sniper Scope) op dit geweer staat: op
     * de bovenkant, waar zijn eigen vizier zat (dat tekenen we dan niet). {@link #sightOnSlide}: hij staat op het
     * bewegende deel (de slede van het pistool). {@link #scopeScale}: zo groot is de Sniper Scope hier (de Laser Sniper
     * is kleiner dan de AWP). {@link #underMount}: waar een greep onder de loop hangt, of null.
     */
    public Vector3f sightMount = new Vector3f(8, 13, 8);
    public boolean sightOnSlide;
    public float scopeScale = 1.0f;
    public @org.jetbrains.annotations.Nullable Vector3f underMount;

    private GunRig addOns(Vector3f sightMount, boolean onSlide, float scopeScale, @org.jetbrains.annotations.Nullable Vector3f underMount) {
        this.sightMount = sightMount;
        this.sightOnSlide = onSlide;
        this.scopeScale = scopeScale;
        this.underMount = underMount;
        return this;
    }

    /** De plek van deze kijker op dit geweer (voor client/guns/Optics). */
    public com.senne.imttech.client.guns.Optics.Seat seat(com.senne.imttech.guns.Attachment optic) {
        return new com.senne.imttech.client.guns.Optics.Seat(new Vector3f(sightMount), 0, sightOnSlide,
                optic == com.senne.imttech.guns.Attachment.SNIPER_SCOPE ? scopeScale : 1.0f);
    }

    /** Zo ver voor je oog zit het richtpunt van deze kijker als je richt (blokken). */
    public float eyeDistance(com.senne.imttech.guns.Attachment optic) {
        if (optic == com.senne.imttech.guns.Attachment.SNIPER_SCOPE) {
            return 0.055f;
        }
        return sightOnSlide ? 0.24f : 0.16f;
    }

    private GunRig(float scale, Vector3f grip, Vector3f support, Vector3f sight, Vector3f muzzle, Vector3f cell,
                   Vector3f display, float displayLength, boolean displayMoves, Vector3f hip, float sightDistance,
                   float cycle, float cycleTicks, float cameraKick, float kickBack, float kickUp) {
        this.scale = scale;
        this.grip = grip;
        this.support = support;
        this.sight = sight;
        this.muzzle = muzzle;
        this.cell = cell;
        this.display = display;
        this.displayLength = displayLength;
        this.displayMoves = displayMoves;
        this.hip = hip;
        this.sightDistance = sightDistance;
        this.cycle = cycle;
        this.cycleTicks = cycleTicks;
        this.cameraKick = cameraKick;
        this.kickBack = kickBack;
        this.kickUp = kickUp;
    }

    private static final GunRig PISTOL = new GunRig(0.42f,
            new Vector3f(8, 6.5f, 11.4f), new Vector3f(7.2f, 5.2f, 10.4f), new Vector3f(8, 13.55f, 12.0f),
            new Vector3f(8, 11, 0.9f), new Vector3f(8, 2.2f, 11.2f),
            new Vector3f(6.36f, 11.6f, 11.6f), 6.5f, true,
            new Vector3f(0.20f, -0.20f, -0.42f), 0.5f, 2.4f, 3, 1.6f, 0.06f, 7)
            .addOns(new Vector3f(8, 13.0f, 9.5f), true, 1.0f, null);
    private static final GunRig RIFLE = new GunRig(0.36f,
            new Vector3f(8, 7.2f, 11.3f), new Vector3f(8, 9.0f, -1.5f), new Vector3f(8, 14.65f, 6.8f),
            new Vector3f(8, 10.9f, -9.6f), new Vector3f(8, 6.0f, 5.5f),
            new Vector3f(6.45f, 11.4f, 12.2f), 8.5f, false,
            new Vector3f(0.21f, -0.22f, -0.40f), 0.42f, 1.0f, 2, 0.9f, 0.035f, 3)
            .addOns(new Vector3f(8, 13.0f, 8.0f), false, 1.0f, new Vector3f(8, 8.85f, -3.5f));
    private static final GunRig SNIPER = new GunRig(0.3f,
            new Vector3f(8, 8.2f, 14.4f), new Vector3f(8, 9.2f, -4.0f), new Vector3f(8, 14.5f, 12.5f),
            new Vector3f(8, 10.8f, -14.1f), new Vector3f(8, 7.5f, 7.7f),
            new Vector3f(6.45f, 11.3f, 13.2f), 8.0f, false,
            new Vector3f(0.22f, -0.24f, -0.36f), 0.30f, 1.8f, 10, 5.0f, 0.09f, 12)
            .addOns(new Vector3f(8, 12.5f, 6.0f), false, 0.75f, new Vector3f(8, 9.15f, -4.5f));
    private static final GunRig SHOTGUN = new GunRig(0.36f,
            new Vector3f(8, 7.0f, 11.3f), new Vector3f(8, 8.9f, -2.3f), new Vector3f(8, 13.6f, 10.0f),
            new Vector3f(8, 10.8f, -6.7f), new Vector3f(8, 7.3f, 5.7f),
            new Vector3f(6.08f, 11.2f, 11.6f), 8.0f, false,
            new Vector3f(0.21f, -0.22f, -0.40f), 0.40f, 3.6f, 9, 4.0f, 0.08f, 10)
            .addOns(new Vector3f(8, 13.25f, 7.0f), false, 1.0f, null);

    public static GunRig of(GunType type) {
        return switch (type) {
            case PISTOL -> PISTOL;
            case RIFLE -> RIFLE;
            case SNIPER -> SNIPER;
            case SHOTGUN -> SHOTGUN;
        };
    }
}
