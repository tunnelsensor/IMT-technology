package com.senne.starktech.nav;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Een opgeslagen plek: een naam, een wereld (overworld, nether, end...), een positie en een kleur.
 */
public record Waypoint(String name, ResourceKey<Level> dimension, BlockPos pos, int color) {
    /** De kleuren die je kunt kiezen (0xRRGGBB). */
    public static final int[] COLORS = {0x3FD8FF, 0x5CFF9D, 0xFFE14A, 0xFFA93F, 0xFF4545, 0xD02BFF, 0xFFFFFF};

    public static final Codec<Waypoint> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(Waypoint::name),
            Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(Waypoint::dimension),
            BlockPos.CODEC.fieldOf("pos").forGetter(Waypoint::pos),
            Codec.INT.optionalFieldOf("color", COLORS[0]).forGetter(Waypoint::color)
    ).apply(instance, Waypoint::new));

    public static final StreamCodec<ByteBuf, Waypoint> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Waypoint::name,
            ResourceKey.streamCodec(Registries.DIMENSION), Waypoint::dimension,
            BlockPos.STREAM_CODEC, Waypoint::pos,
            ByteBufCodecs.INT, Waypoint::color,
            Waypoint::new);
}
