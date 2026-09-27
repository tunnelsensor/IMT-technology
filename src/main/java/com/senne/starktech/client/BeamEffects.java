package com.senne.starktech.client;

import com.senne.starktech.client.suit.SuitEffects;
import com.senne.starktech.network.BeamEffectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Tekent laserstralen, de richtstraal en de laser van de satelliet, en de inslag van een
 * satellietraket, met deeltjes (particles).
 */
public final class BeamEffects {
    private BeamEffects() {}

    /**
     * Zet een kleur als 0xRRGGBB om in de drie getallen (rood, groen, blauw van 0 tot 1)
     * die de deeltjes van Minecraft 1.21.1 verwachten.
     */
    private static Vector3f color(int rgb) {
        return new Vector3f(((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f);
    }

    private static final DustParticleOptions LASER = new DustParticleOptions(color(0x7FE8FF), 0.6f);
    private static final DustParticleOptions TARGETING = new DustParticleOptions(color(0xFF3030), 1.0f);
    private static final DustParticleOptions SWEEP = new DustParticleOptions(color(0xFF7A1F), 2.0f);
    private static final DustParticleOptions HYDRA = new DustParticleOptions(color(0xFF2A1A), 0.7f);

    public static void play(BeamEffectPayload payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        switch (payload.kind()) {
            case DRONE_LASER -> droneLaser(level, payload.from(), payload.to());
            case SATELLITE_CHARGE -> satelliteCharge(level, payload.from(), payload.to());
            case SATELLITE_FIRE -> {
                satelliteFire(level, payload.from(), payload.to());
                shakeNear(payload.to(), 4.0f, 900);
            }
            case SATELLITE_PRECISION -> {
                satellitePrecision(level, payload.from(), payload.to());
                shakeNear(payload.to(), 1.5f, 500);
            }
            case REPULSOR -> repulsor(level, payload.from(), payload.to());
            case HYDRA_LASER -> hydraLaser(level, payload.from(), payload.to());
            case SATELLITE_SWEEP -> {
                satelliteSweep(level, payload.from(), payload.to());
                shakeNear(payload.to(), 0.6f, 300);
            }
        }
    }

    /**
     * Een repulsorstoot: zelf getekend licht (zie SuitEffects), geen deeltjes.
     *
     * De stoot begint bij de rechterhand van de schutter: een armlengte vooruit, iets naar rechts
     * en iets omlaag. Vanaf zijn ogen getekend zag de schutter zelf alleen nog licht voor zijn neus.
     */
    private static void repulsor(ClientLevel level, Vec3 eye, Vec3 to) {
        Vec3 dir = to.subtract(eye).normalize();
        Vec3 right = dir.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : right.normalize();
        Vec3 up = right.cross(dir).normalize();
        Vec3 hand = eye.add(dir.scale(0.8)).add(right.scale(0.32)).add(up.scale(-0.22));
        SuitEffects.addShot(eye, hand, to);
    }

    /** Het lasergeweer van een Hydra-soldaat: een rode straal met vonkjes en een rookje waar hij raakt. */
    private static void hydraLaser(ClientLevel level, Vec3 from, Vec3 to) {
        line(level, from, to, 0.22, HYDRA, false);
        RandomSource random = level.getRandom();
        for (int i = 0; i < 4; i++) {
            level.addParticle(ParticleTypes.SMALL_FLAME, to.x, to.y, to.z,
                    (random.nextDouble() - 0.5) * 0.08, random.nextDouble() * 0.06, (random.nextDouble() - 0.5) * 0.08);
        }
        level.addParticle(ParticleTypes.SMOKE, to.x, to.y, to.z, 0, 0.03, 0);
    }

    /** Dunne blauwe straal met vonkjes waar hij raakt. */
    private static void droneLaser(ClientLevel level, Vec3 from, Vec3 to) {
        line(level, from, to, 0.25, LASER, false);
        RandomSource random = level.getRandom();
        for (int i = 0; i < 5; i++) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, to.x, to.y, to.z,
                    (random.nextDouble() - 0.5) * 0.4, random.nextDouble() * 0.3, (random.nextDouble() - 0.5) * 0.4);
        }
    }

    /** Rode richtstraal uit de lucht en een draaiende ring op de grond. */
    private static void satelliteCharge(ClientLevel level, Vec3 top, Vec3 target) {
        line(level, top, target, 1.2, TARGETING, true);
        double spin = (level.getGameTime() % 360) * 0.15;
        for (int i = 0; i < 20; i++) {
            double angle = spin + i * (Math.PI * 2 / 20);
            level.addAlwaysVisibleParticle(TARGETING, true,
                    target.x + Math.cos(angle) * 3.0, target.y + 0.2, target.z + Math.sin(angle) * 3.0, 0, 0, 0);
        }
    }

    /**
     * Een raket van het bombardement slaat in (de explosie zelf tekent Minecraft al): een flits,
     * een vuurzuil, vonken die alle kanten op vliegen en een ring van rook over de grond.
     */
    private static void satelliteFire(ClientLevel level, Vec3 top, Vec3 target) {
        RandomSource random = level.getRandom();
        level.addAlwaysVisibleParticle(ParticleTypes.FLASH, true, target.x, target.y + 1, target.z, 0, 0, 0);
        for (int i = 0; i < 30; i++) {
            level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, target.x, target.y + 0.5, target.z,
                    (random.nextDouble() - 0.5) * 0.5, random.nextDouble() * 0.9, (random.nextDouble() - 0.5) * 0.5);
        }
        for (int i = 0; i < 24; i++) {
            level.addAlwaysVisibleParticle(ParticleTypes.LAVA, true, target.x, target.y + 0.5, target.z, 0, 0, 0);
        }
        smokeRing(level, target, 36, 0.45);
    }

    /**
     * De precisieraket slaat in: geen krater, maar wel een felle witte flits, een bol vuur en
     * vonken, en een ring van rook.
     */
    private static void satellitePrecision(ClientLevel level, Vec3 top, Vec3 target) {
        RandomSource random = level.getRandom();
        level.addAlwaysVisibleParticle(ParticleTypes.FLASH, true, target.x, target.y + 0.5, target.z, 0, 0, 0);
        level.addAlwaysVisibleParticle(ParticleTypes.EXPLOSION_EMITTER, true, target.x, target.y + 0.5, target.z, 0, 0, 0);
        for (int i = 0; i < 40; i++) {
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, target.x, target.y + 0.4, target.z,
                    (random.nextDouble() - 0.5) * 0.9, random.nextDouble() * 0.7, (random.nextDouble() - 0.5) * 0.9);
        }
        for (int i = 0; i < 20; i++) {
            level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, target.x, target.y + 0.4, target.z,
                    (random.nextDouble() - 0.5) * 0.6, random.nextDouble() * 0.5, (random.nextDouble() - 0.5) * 0.6);
        }
        smokeRing(level, target, 28, 0.35);
    }

    /** Rook die in een ring over de grond wegrolt (de drukgolf van een inslag). */
    private static void smokeRing(ClientLevel level, Vec3 center, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double angle = i * (Math.PI * 2 / count);
            level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, center.x, center.y + 0.3, center.z,
                    Math.cos(angle) * speed, 0.02, Math.sin(angle) * speed);
        }
    }

    /** De schuivende laser: een brede oranje streep die over de grond kruipt. */
    private static void satelliteSweep(ClientLevel level, Vec3 top, Vec3 target) {
        line(level, top, target, 0.8, SWEEP, true);
        RandomSource random = level.getRandom();
        for (int i = 0; i < 12; i++) {
            level.addAlwaysVisibleParticle(SWEEP, true,
                    target.x + (random.nextDouble() - 0.5) * 4.0, target.y + 0.2,
                    target.z + (random.nextDouble() - 0.5) * 4.0, 0, 0.02, 0);
        }
        for (int i = 0; i < 6; i++) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, target.x, target.y + 0.3, target.z,
                    (random.nextDouble() - 0.5) * 0.2, 0.05, (random.nextDouble() - 0.5) * 0.2);
        }
    }

    /** Laat het beeld schudden, harder als de inslag dichtbij is. */
    private static void shakeNear(Vec3 impact, float power, long millis) {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        double distance = Math.sqrt(player.distanceToSqr(impact));
        // Verder dan 60 blokken voel je niets meer.
        float fade = (float) Math.max(0.0, 1.0 - distance / 60.0);
        if (fade > 0.01f) {
            CameraShake.shake(power * fade, millis);
        }
    }

    /** Zet deeltjes op een rechte lijn, elke "step" blokken één. */
    private static void line(ClientLevel level, Vec3 from, Vec3 to, double step, DustParticleOptions particle, boolean alwaysVisible) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        int count = Math.max(1, (int) (length / step));
        for (int i = 0; i <= count; i++) {
            Vec3 p = from.add(delta.scale((double) i / count));
            if (alwaysVisible) {
                level.addAlwaysVisibleParticle(particle, true, p.x, p.y, p.z, 0, 0, 0);
            } else {
                level.addParticle(particle, p.x, p.y, p.z, 0, 0, 0);
            }
        }
    }
}
