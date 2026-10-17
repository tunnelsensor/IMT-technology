package com.senne.imttech.security.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * De twee hoeken die je met de Zone Marker aanklikt (linksklik = hoek 1, rechtsklik = hoek 2). Ze
 * zitten op het item zelf, zodat je spel de oranje doos kan tekenen en de server weet welke kubus
 * je wilt toevoegen. Een hoek die nog niet gekozen is, is leeg.
 */
public record ZoneSelection(Optional<BlockPos> first, Optional<BlockPos> second) {
    public static final ZoneSelection EMPTY = new ZoneSelection(Optional.empty(), Optional.empty());

    public static final Codec<ZoneSelection> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.optionalFieldOf("first").forGetter(ZoneSelection::first),
            BlockPos.CODEC.optionalFieldOf("second").forGetter(ZoneSelection::second)
    ).apply(instance, ZoneSelection::new));

    public static final StreamCodec<ByteBuf, ZoneSelection> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), ZoneSelection::first,
            ByteBufCodecs.optional(BlockPos.STREAM_CODEC), ZoneSelection::second,
            ZoneSelection::new);

    public ZoneSelection withFirst(BlockPos pos) {
        return new ZoneSelection(Optional.of(pos.immutable()), second);
    }

    public ZoneSelection withSecond(BlockPos pos) {
        return new ZoneSelection(first, Optional.of(pos.immutable()));
    }

    /** De kubus tussen de twee hoeken, of null als er nog een hoek ontbreekt. */
    public @Nullable ZoneBox box() {
        return first.isPresent() && second.isPresent() ? ZoneBox.of(first.get(), second.get()) : null;
    }
}
