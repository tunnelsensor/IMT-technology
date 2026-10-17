package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: "ik klik met links op dit blok, met de Zone Marker in mijn hand"
 * (hoek 1, of met sluipen: deze kubus weghalen). Je spel stuurt dit in plaats van te gaan hakken,
 * zie client/zone/ZoneMarkerControl en ZoneMarkerItem.leftClick.
 */
public record ZoneMarkerClickPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<ZoneMarkerClickPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "zone_marker_click"));

    public static final StreamCodec<ByteBuf, ZoneMarkerClickPayload> STREAM_CODEC =
            BlockPos.STREAM_CODEC.map(ZoneMarkerClickPayload::new, ZoneMarkerClickPayload::pos);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
