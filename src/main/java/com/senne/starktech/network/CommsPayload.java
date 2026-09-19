package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Bericht van speler naar server: stuur een E.D.I.T.H.-bericht.
 *
 * @param recipient naam van de ontvanger, of "" voor iedereen met een E.D.I.T.H.-bril
 * @param message   de tekst
 */
public record CommsPayload(String recipient, String message) implements CustomPacketPayload {
    public static final Type<CommsPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StarkTech.MODID, "comms"));

    public static final StreamCodec<ByteBuf, CommsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), CommsPayload::recipient,
            ByteBufCodecs.stringUtf8(256), CommsPayload::message,
            CommsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
