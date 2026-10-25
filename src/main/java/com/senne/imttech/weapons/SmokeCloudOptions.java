package com.senne.imttech.weapons;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.imttech.registry.ModParticles;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Eén rookdeeltje van de smokebom (hoofdstuk 35), met zijn kleur (0xRRGGBB): grijs, of de kleur van de kleurstof
 * waarmee je de granaat maakte. Het deeltje zelf staat in client/weapons/SmokeCloudParticle.
 */
public record SmokeCloudOptions(int rgb) implements ParticleOptions {
    public static final MapCodec<SmokeCloudOptions> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("color").forGetter(SmokeCloudOptions::rgb)).apply(instance, SmokeCloudOptions::new));
    public static final StreamCodec<ByteBuf, SmokeCloudOptions> STREAM_CODEC =
            ByteBufCodecs.INT.map(SmokeCloudOptions::new, SmokeCloudOptions::rgb);

    @Override
    public ParticleType<?> getType() {
        return ModParticles.SMOKE_CLOUD.get();
    }
}
