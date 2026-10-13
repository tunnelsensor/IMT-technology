package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: "open het instelscherm van deze laserzender" (13 oktober).
 *
 * De server beslist of je het mag openen (jij zette hem neer, of je bent vertrouwd in de zone; zie
 * shield/LaserEmitters), en jouw spel opent het dan pas. De kleur, het omkeren en liggend/staand leest het
 * scherm gewoon uit het blok (dat kent jouw spel al).
 *
 * @param pos   welke zender
 * @param owner de naam van wie hem neerzette (leeg als niemand: een oude zender of een uit een gebouw)
 */
public record LaserOpenPayload(BlockPos pos, String owner) implements CustomPacketPayload {
    public static final Type<LaserOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "laser_open"));

    public static final StreamCodec<ByteBuf, LaserOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LaserOpenPayload::pos,
            ByteBufCodecs.stringUtf8(64), LaserOpenPayload::owner,
            LaserOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
