package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: "open het instelscherm van deze schildkoepel". De server
 * beslist of het mag (jouw koepel, dichtbij genoeg); jouw spel opent het dan pas.
 */
public record DomeOpenPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<DomeOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "dome_open"));

    public static final StreamCodec<ByteBuf, DomeOpenPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(DomeOpenPayload::new, DomeOpenPayload::pos);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
