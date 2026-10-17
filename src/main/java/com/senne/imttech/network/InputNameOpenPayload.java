package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: "open het naamscherm van deze keycardlezer of dit codepaneel" (13 oktober).
 *
 * De server beslist of je het mag openen (jij zette hem neer, of je bent vertrouwd in de basis; zie
 * logic/InputDevices), en jouw spel opent het dan pas.
 *
 * @param pos   welke lezer of welk paneel
 * @param name  hoe hij nu heet ("Reader 1", "Panel 2")
 * @param owner de naam van wie hem neerzette (leeg als niemand: van vóór 13 oktober)
 */
public record InputNameOpenPayload(BlockPos pos, String name, String owner) implements CustomPacketPayload {
    public static final Type<InputNameOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "input_name_open"));

    public static final StreamCodec<ByteBuf, InputNameOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, InputNameOpenPayload::pos,
            ByteBufCodecs.stringUtf8(64), InputNameOpenPayload::name,
            ByteBufCodecs.stringUtf8(64), InputNameOpenPayload::owner,
            InputNameOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
