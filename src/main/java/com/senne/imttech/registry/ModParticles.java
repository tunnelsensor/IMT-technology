package com.senne.imttech.registry;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.ImtTech;
import com.senne.imttech.weapons.SmokeCloudOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** De eigen deeltjes (particles) van de mod. Hoe ze eruitzien, staat in client/ (zie ImtTechClient). */
public final class ModParticles {
    private ModParticles() {}

    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, ImtTech.MODID);

    /** Hoofdstuk 35: een dikke pluk rook van de smokebom, in een kleur. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<SmokeCloudOptions>> SMOKE_CLOUD = PARTICLES.register(
            "smoke_cloud", () -> new ParticleType<SmokeCloudOptions>(true) {
                @Override
                public MapCodec<SmokeCloudOptions> codec() {
                    return SmokeCloudOptions.CODEC;
                }

                @Override
                public StreamCodec<? super RegistryFriendlyByteBuf, SmokeCloudOptions> streamCodec() {
                    return SmokeCloudOptions.STREAM_CODEC;
                }
            });
}
