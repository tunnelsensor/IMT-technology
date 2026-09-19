package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar de hacker: "deze speler stuurde (of kreeg) dit privébericht".
 * Jouw spel zet de tekst in een ballonnetje boven zijn hoofd.
 *
 * @param entityId  het nummer van de speler die je hackt
 * @param text      wat er gezegd werd (met "naar wie" of "van wie" erbij)
 * @param outgoing  true = hij stuurde het zelf, false = hij kreeg het van iemand
 */
public record HackMessagePayload(int entityId, Component text, boolean outgoing) implements CustomPacketPayload {
    public static final Type<HackMessagePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "hack_message"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HackMessagePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, HackMessagePayload::entityId,
            ComponentSerialization.STREAM_CODEC, HackMessagePayload::text,
            ByteBufCodecs.BOOL, HackMessagePayload::outgoing,
            HackMessagePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
