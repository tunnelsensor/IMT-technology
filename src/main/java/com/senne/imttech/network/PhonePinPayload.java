package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Speler naar server (hoofdstuk 50): de PIN van de telefoon, net gekozen (de eerste keer) of ingetypt om hem te openen.
 */
public record PhonePinPayload(String pin) implements CustomPacketPayload {
    public static final Type<PhonePinPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "phone_pin"));

    public static final StreamCodec<ByteBuf, PhonePinPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(8), PhonePinPayload::pin,
            PhonePinPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
