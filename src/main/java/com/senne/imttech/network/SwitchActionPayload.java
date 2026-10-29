package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server (hoofdstuk 38): "zet de poort aan deze kant van de Network Switch om".
 *
 * @param pos  waar de switch staat
 * @param side welke kant (het nummer van een Direction: 0 = onder, 1 = boven, ...)
 */
public record SwitchActionPayload(BlockPos pos, int side) implements CustomPacketPayload {
    public static final Type<SwitchActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "switch_action"));

    public static final StreamCodec<ByteBuf, SwitchActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SwitchActionPayload::pos,
            ByteBufCodecs.VAR_INT, SwitchActionPayload::side,
            SwitchActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
