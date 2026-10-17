package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: stuur een I.M.T-bericht.
 *
 * @param recipient naam van de ontvanger, of "" voor iedereen met een I.M.T-bril
 * @param message   de tekst
 */
public record CommsPayload(String recipient, String message) implements CustomPacketPayload {
    public static final Type<CommsPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "comms"));

    public static final StreamCodec<ByteBuf, CommsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), CommsPayload::recipient,
            ByteBufCodecs.stringUtf8(256), CommsPayload::message,
            CommsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
