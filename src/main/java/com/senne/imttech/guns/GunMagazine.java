package com.senne.imttech.guns;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Het magazijn dat in een vuurwapen zit (hoofdstuk 52): hoeveel patronen erin zitten, en (hoofdstuk 103) of het het
 * lange magazijn is (de "extended mag": jouw keuze, een eigen groter magazijn). Een data component mag geen losse
 * ItemStack zijn; welk magazijn het is, weet het wapen zelf: dat van zijn soort. Zit dit component niet aan het wapen,
 * dan zit er geen magazijn in. Bij een wapen met een buis (de Remington 870) staat hier hoeveel patronen er in de buis
 * zitten.
 */
public record GunMagazine(int rounds, boolean extended) {
    public static final Codec<GunMagazine> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("rounds", 0).forGetter(GunMagazine::rounds),
            Codec.BOOL.optionalFieldOf("extended", false).forGetter(GunMagazine::extended)
    ).apply(instance, GunMagazine::new));

    public static final StreamCodec<ByteBuf, GunMagazine> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GunMagazine::rounds,
            ByteBufCodecs.BOOL, GunMagazine::extended,
            GunMagazine::new);

    /** Een gewoon magazijn met zoveel patronen. */
    public GunMagazine(int rounds) {
        this(rounds, false);
    }

    /** Hetzelfde magazijn met een ander aantal patronen. */
    public GunMagazine withRounds(int count) {
        return new GunMagazine(count, extended);
    }
}
