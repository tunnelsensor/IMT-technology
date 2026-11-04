package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server naar speler (hoofdstuk 50): een scherm van de telefoon openen ({@code mode} 0 = verbinden met IP en wachtwoord,
 * 1 = de PIN intypen, 2 = een PIN kiezen; {@code label} is het IP van de vorige keer of de naam van het systeem), of,
 * als zo'n scherm al open staat, zeggen waarom het niet lukte ({@code mode} -1 met {@code message}).
 */
public record PhonePromptPayload(int mode, String label, String message) implements CustomPacketPayload {
    public static final Type<PhonePromptPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "phone_prompt"));

    public static final int LOGIN = 0;
    public static final int PIN = 1;
    public static final int SET_PIN = 2;

    public static final StreamCodec<ByteBuf, PhonePromptPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, PhonePromptPayload::mode,
            ByteBufCodecs.stringUtf8(64), PhonePromptPayload::label,
            ByteBufCodecs.stringUtf8(256), PhonePromptPayload::message,
            PhonePromptPayload::new);

    /** Een antwoord voor het telefoonscherm dat al open staat. */
    public static PhonePromptPayload reply(String message) {
        return new PhonePromptPayload(-1, "", message);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
