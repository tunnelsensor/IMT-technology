package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Bericht van server naar speler: het (eind)rapport van de spionagedrone.
 *
 * @param targetName wie of wat er gevolgd werd
 * @param lines      het logboek, regel voor regel (met de tijd erbij)
 * @param finished   true = de opdracht is voorbij (dan opent het rapport meteen)
 */
public record SpyReportPayload(Component targetName, List<Component> lines, boolean finished) implements CustomPacketPayload {
    public static final Type<SpyReportPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StarkTech.MODID, "spy_report"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SpyReportPayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, SpyReportPayload::targetName,
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(128)), SpyReportPayload::lines,
            ByteBufCodecs.BOOL, SpyReportPayload::finished,
            SpyReportPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
