package com.senne.imttech.guns;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Het magazijn dat in een pistool zit (hoofdstuk 52): hoeveel patronen erin zitten. (Een data component mag geen losse
 * ItemStack zijn; welk magazijn het is, weet het pistool zelf: dat van zijn soort.) Zit dit component niet aan het
 * pistool, dan zit er geen magazijn in.
 */
public record GunMagazine(int rounds) {
    public static final Codec<GunMagazine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("rounds", 0).forGetter(GunMagazine::rounds)
    ).apply(instance, GunMagazine::new));

    public static final StreamCodec<ByteBuf, GunMagazine> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(GunMagazine::new, GunMagazine::rounds);
}
