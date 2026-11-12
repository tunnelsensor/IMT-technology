package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler (hoofdstuk 94), tien keer per seconde zolang je camerabeelden bekijkt: per camera of hij
 * beeld mag geven, waar zijn lens zit en waar hij heen kijkt, en wie er rondloopt in de buurt van die camera's (spelers,
 * dieren, monsters, robots): waar, welke kant op, en af en toe hoe ze eruitzien (hun gegevens en wat ze vasthouden of
 * dragen). Je spel tekent ze daarmee ook als ze te ver weg zijn om ze zelf te kennen.
 *
 * Geschreven en gelezen door security/feed/FeedFormat; dit bericht draagt alleen de bytes.
 *
 * @param data de beschrijving van de camera's en de wezens
 */
public record CameraFeedPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<CameraFeedPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "camera_feed"));

    public static final StreamCodec<ByteBuf, CameraFeedPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.byteArray(CameraWorldPayload.MAX_BYTES), CameraFeedPayload::data,
            CameraFeedPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
