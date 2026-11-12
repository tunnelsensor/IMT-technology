package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server (hoofdstuk 94): "deze camerabeelden staan nu op mijn scherm". Je spel stuurt het een paar
 * keer per seconde zolang er beelden te zien zijn (de app Cameras, een schermmuur, het instelscherm van een schermmuur), en
 * één keer leeg als er geen meer zijn. De server kijkt per beeld na of je het mag zien (zie security/feed/FeedServer), en
 * stuurt dan de wereld rond die camera's en wie daar rondloopt.
 *
 * @param cameras  de plekken van de camerablokken ({@code BlockPos.asLong})
 * @param robots   de nummers (in de wereld) van robots en drones met een Camera-module
 * @param distance hoe ver de beelden kijken (blokken, uit config/imttech-client.toml); de server houdt een grens aan
 */
public record CameraWatchPayload(List<Long> cameras, List<Integer> robots, int distance) implements CustomPacketPayload {
    public static final Type<CameraWatchPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "camera_watch"));

    /** Zoveel beelden tegelijk hooguit (een schermmuur van 8 x 6 heeft er 48). */
    public static final int MAX = 64;

    public static final StreamCodec<ByteBuf, CameraWatchPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(MAX)), CameraWatchPayload::cameras,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(MAX)), CameraWatchPayload::robots,
            ByteBufCodecs.VAR_INT, CameraWatchPayload::distance,
            CameraWatchPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
