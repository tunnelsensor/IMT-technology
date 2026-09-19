package com.senne.starktech.client;

import com.senne.starktech.network.BeamEffectPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Tekent laserstralen en de satellietstraal met deeltjes (particles).
 */
public final class BeamEffects {
    private BeamEffects() {}

    private static final DustParticleOptions LASER = new DustParticleOptions(0x7FE8FF, 0.6f);
    private static final DustParticleOptions TARGETING = new DustParticleOptions(0xFF3030, 1.0f);
    private static final DustParticleOptions ORBITAL = new DustParticleOptions(0xDDF8FF, 2.5f);
    private static final DustParticleOptions PRECISION = new DustParticleOptions(0xFFFFFF, 1.2f);
    private static final DustParticleOptions SWEEP = new DustParticleOptions(0xFF7A1F, 2.0f);

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
            case SATELLITE_SWEEP -> {
                satelliteSweep(level, payload.from(), payload.to());
                shakeNear(payload.to(), 0.6f, 300);
            }
        }
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

    /** Dikke, felle straal: de satelliet vuurt. */
    private static void satelliteFire(ClientLevel level, Vec3 top, Vec3 target) {
        RandomSource random = level.getRandom();
        for (int ring = 0; ring < 4; ring++) {
            double ox = (random.nextDouble() - 0.5) * 1.2;
            double oz = (random.nextDouble() - 0.5) * 1.2;
            line(level, top.add(ox, 0, oz), target.add(ox, 0, oz), 0.6, ORBITAL, true);
        }
        for (int i = 0; i < 40; i++) {
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, target.x, target.y + 0.5, target.z,
                    (random.nextDouble() - 0.5) * 1.2, random.nextDouble() * 0.8, (random.nextDouble() - 0.5) * 1.2);
        }
    }

    /** Precisie-straal: één dunne, oogverblindend witte lijn met een flits op het doel. */
    private static void satellitePrecision(ClientLevel level, Vec3 top, Vec3 target) {
        line(level, top, target, 0.4, PRECISION, true);
        RandomSource random = level.getRandom();
        for (int i = 0; i < 30; i++) {
            level.addAlwaysVisibleParticle(ParticleTypes.END_ROD, true, target.x, target.y + 0.4, target.z,
                    (random.nextDouble() - 0.5) * 0.5, random.nextDouble() * 0.5, (random.nextDouble() - 0.5) * 0.5);
        }
        level.addAlwaysVisibleParticle(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFF), true,
                target.x, target.y + 0.5, target.z, 0, 0, 0);
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
