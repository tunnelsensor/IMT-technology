package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: "geef deze keycardlezer (of dit codepaneel) deze naam" (Save in het
 * naamscherm, 13 oktober).
 * De server kijkt opnieuw of je dat mag en maakt de naam netjes (zie logic/InputDevices).
 *
 * @param pos  welke lezer of welk paneel
 * @param name de nieuwe naam, zoals je hem typte
 */
public record InputRenamePayload(BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<InputRenamePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "input_rename"));

    public static final StreamCodec<ByteBuf, InputRenamePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, InputRenamePayload::pos,
            ByteBufCodecs.stringUtf8(64), InputRenamePayload::name,
            InputRenamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
