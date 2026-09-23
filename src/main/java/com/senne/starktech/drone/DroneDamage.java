package com.senne.starktech.drone;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Hoe kapot een drone is, en wat je daarvan ziet: rook, vonken en brandende brokjes.
 *
 * Geldt voor je eigen drones én de vijandige raiddrones (dezelfde machine). Het model zelf
 * (kromme rotors, ontbrekende rotors, een hangende camera) staat in client/drone/DroneModel;
 * de roetvlekken in client/drone/DroneRenderer.
 *
 * Dit staat niet in de client-map omdat de drones zelf het aanroepen. Het doet alleen iets in
 * jouw eigen spel (deeltjes bestaan niet op de server).
 */
public final class DroneDamage {
    private DroneDamage() {}

    /** De stappen van heel tot bijna kapot. */
    public enum Stage {
        /** Minder dan een kwart kwijt: je ziet nog niets. */
        FINE,
        /** Een kwart kwijt: roetvlekken, een kromme rotor, af en toe een rookwolkje. */
        SCRATCHED,
        /** De helft kwijt: een rotor weg, rook en vonken. */
        BROKEN,
        /** Driekwart kwijt: twee rotors weg, zware rook, brandende brokjes, flikkerende lampjes. */
        WRECKED;

        public boolean atLeast(Stage other) {
            return ordinal() >= other.ordinal();
        }
    }

    /** Hoe kapot: 0 = heel, 1 = op. */
    public static float of(LivingEntity drone) {
        float max = drone.getMaxHealth();
        return max <= 0 ? 0 : 1.0f - drone.getHealth() / max;
    }

    public static Stage stage(float damage) {
        if (damage >= 0.75f) {
            return Stage.WRECKED;
        }
        if (damage >= 0.5f) {
            return Stage.BROKEN;
        }
        if (damage >= 0.25f) {
            return Stage.SCRATCHED;
        }
        return Stage.FINE;
    }

    /**
     * Elke tick, in jouw eigen spel: rook, vonken en brokjes, meer naarmate de drone kapotter is.
     * Een onzichtbare drone (spion, cloakingdrone) rookt niet: dan zou je hem zo vinden.
     */
    public static void particles(LivingEntity drone) {
        Level level = drone.level();
        if (!level.isClientSide || drone.isInvisible() || !drone.isAlive()) {
            return;
        }
        float damage = of(drone);
        Stage stage = stage(damage);
        if (stage == Stage.FINE) {
            return;
        }
        RandomSource random = drone.getRandom();
        double x = drone.getX() + (random.nextDouble() - 0.5) * 0.3;
        double y = drone.getY() + drone.getBbHeight() * 0.6;
        double z = drone.getZ() + (random.nextDouble() - 0.5) * 0.3;

        // Rook: eerst een dun sliertje, later ook dikke zwarte wolken. Niet te veel: met elke
        // tick een grote wolk zag je de drone zelf niet meer door de rook.
        float thin = stage == Stage.SCRATCHED ? 0.1f : 0.2f;
        float thick = stage == Stage.BROKEN ? 0.07f : stage == Stage.WRECKED ? 0.22f : 0.0f;
        if (random.nextFloat() < thin) {
            level.addParticle(ParticleTypes.SMOKE, x, y, z, 0.0, 0.03, 0.0);
        }
        if (random.nextFloat() < thick) {
            level.addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, 0.0, 0.02, 0.0);
        }
        // Vonken uit de kapotte rotor.
        if (stage.atLeast(Stage.BROKEN) && random.nextFloat() < 0.18f) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y - 0.1, z,
                    (random.nextDouble() - 0.5) * 0.3, random.nextDouble() * 0.2, (random.nextDouble() - 0.5) * 0.3);
        }
        // Brandende brokjes die eraf vallen.
        if (stage == Stage.WRECKED && random.nextFloat() < 0.06f) {
            level.addParticle(ParticleTypes.LAVA, x, y, z, 0.0, 0.0, 0.0);
        }
        if (stage == Stage.WRECKED && random.nextFloat() < 0.15f) {
            level.addParticle(ParticleTypes.SMALL_FLAME, x, y - 0.1, z, 0.0, 0.01, 0.0);
        }
    }

    /**
     * Knipperen de lampjes van een drone die bijna op is nu even uit? Een beetje onregelmatig,
     * zoals een kapot contactje.
     */
    public static boolean lightsFlickerOff(float damage, float ageInTicks, int seed) {
        if (stage(damage) != Stage.WRECKED) {
            return false;
        }
        float wave = (float) Math.sin(ageInTicks * 1.9f + seed) + (float) Math.sin(ageInTicks * 0.7f + seed * 0.3f);
        return wave > 1.1f;
    }
}
