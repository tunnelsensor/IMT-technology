package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.NumberStyle;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server (hoofdstuk 66): zo wil ik grote getallen zien (5000, 5 000, 5.000, 5,000 of 5k). Je spel
 * stuurt het bij het inloggen en bij elke wissel, zodat ook de meldingen van de server ("Required: 5 000 FE") je keuze
 * volgen. Een onbekend nummer (een nieuwere versie met meer stijlen) wordt de standaard.
 */
public record NumberStylePayload(NumberStyle style) implements CustomPacketPayload {
    public static final Type<NumberStylePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "number_style"));

    public static final StreamCodec<ByteBuf, NumberStylePayload> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
            index -> new NumberStylePayload(index >= 0 && index < NumberStyle.values().length
                    ? NumberStyle.values()[index] : NumberStyle.DEFAULT),
            payload -> payload.style().ordinal());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
