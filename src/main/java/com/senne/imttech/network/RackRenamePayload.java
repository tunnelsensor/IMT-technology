package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: "geef dit serverrack deze naam" (Save in het naamscherm, hoofdstuk 64). Onder die naam
 * staat het rack in de app Monitor. De server kijkt opnieuw of je dat mag en maakt de naam netjes (zie logic/ServerRacks).
 *
 * @param pos  welk rack
 * @param name de nieuwe naam, zoals je hem typte
 */
public record RackRenamePayload(BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<RackRenamePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "rack_rename"));

    public static final StreamCodec<ByteBuf, RackRenamePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, RackRenamePayload::pos,
            ByteBufCodecs.stringUtf8(64), RackRenamePayload::name,
            RackRenamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
