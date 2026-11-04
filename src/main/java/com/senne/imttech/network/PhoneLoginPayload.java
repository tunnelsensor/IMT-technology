package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Speler naar server (hoofdstuk 50): "verbind mijn telefoon met de kern met dit IP, met dit wachtwoord". De server kijkt
 * het na zoals bij de laptop, hangt de telefoon aan die kern en vraagt dan om een PIN (PhonePromptPayload).
 */
public record PhoneLoginPayload(String ip, String password) implements CustomPacketPayload {
    public static final Type<PhoneLoginPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "phone_login"));

    public static final StreamCodec<ByteBuf, PhoneLoginPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), PhoneLoginPayload::ip,
            ByteBufCodecs.stringUtf8(32), PhoneLoginPayload::password,
            PhoneLoginPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
