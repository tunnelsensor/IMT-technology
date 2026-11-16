package com.senne.imttech.sdcard;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wat er op een SD Card-item staat (hoofdstuk 130): alleen zijn vaste nummer en een samenvatting voor de tooltip.
 * <p>
 * De programma's en bestanden zelf staan <b>niet</b> op het item, maar op de server (zie {@link SdCards}). Alles wat op een
 * item staat, gaat namelijk ook naar het spel van wie het vasthoudt (en van iedereen in de buurt die het in je hand ziet):
 * een programma op slot zou je dan toch kunnen lezen met een mod. En een kaart vol code is te groot om mee te sturen.
 *
 * @param id       het vaste nummer van de kaart: daaronder staat wat erop staat (op de server)
 * @param programs hoeveel programma's erop staan
 * @param files    hoeveel bestanden erop staan (mappen tellen niet)
 * @param locked   hoeveel van die programma's op slot zijn
 */
public record SdCardData(UUID id, int programs, int files, int locked) {
    public SdCardData {
        // Nooit iets geks (een item van /give): de tooltip toont het gewoon.
        programs = Math.max(0, Math.min(programs, 9999));
        files = Math.max(0, Math.min(files, 9999));
        locked = Math.max(0, Math.min(locked, programs));
    }

    public static final Codec<SdCardData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(SdCardData::id),
            Codec.INT.optionalFieldOf("programs", 0).forGetter(SdCardData::programs),
            Codec.INT.optionalFieldOf("files", 0).forGetter(SdCardData::files),
            Codec.INT.optionalFieldOf("locked", 0).forGetter(SdCardData::locked)
    ).apply(instance, SdCardData::new));

    public static final StreamCodec<ByteBuf, SdCardData> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SdCardData::id,
            ByteBufCodecs.VAR_INT, SdCardData::programs,
            ByteBufCodecs.VAR_INT, SdCardData::files,
            ByteBufCodecs.VAR_INT, SdCardData::locked,
            SdCardData::new);
}
