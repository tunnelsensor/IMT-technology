package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler (hoofdstuk 94): een stuk wereld rond de camera's die je bekijkt. Stukjes van 16 x 16 x 16
 * blokken ("secties"): welke blokken er staan, hoe licht het er is, en blokken met een eigen model (kisten, de koppen van
 * torentjes). Ook welke stukjes je spel mag vergeten (geen camera kijkt er meer naar).
 *
 * Wat erin staat, is ingepakt (zoals een zip) en wordt geschreven en gelezen door security/feed/FeedFormat; dit bericht
 * draagt alleen de bytes.
 *
 * @param data de ingepakte stukjes wereld
 */
public record CameraWorldPayload(byte[] data) implements CustomPacketPayload {
    public static final Type<CameraWorldPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "camera_world"));

    /** Zo groot mag één bericht hooguit zijn (bytes, ingepakt). De server stuurt per tick veel minder. */
    public static final int MAX_BYTES = 1 << 20;

    public static final StreamCodec<ByteBuf, CameraWorldPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.byteArray(MAX_BYTES), CameraWorldPayload::data,
            CameraWorldPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
