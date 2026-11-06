package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van jouw spel naar de server: "ik haal de trekker over" van mijn pistool (hoofdstuk 52), met waar je precies
 * naar keek en of je door het vizier richtte. De server beslist wat er gebeurt: een schot, een klik, een storing (zie
 * guns/Firearms).
 */
public record FirearmFirePayload(float yaw, float pitch, boolean aiming) implements CustomPacketPayload {
    public static final Type<FirearmFirePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "firearm_fire"));

    public static final StreamCodec<ByteBuf, FirearmFirePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, FirearmFirePayload::yaw,
            ByteBufCodecs.FLOAT, FirearmFirePayload::pitch,
            ByteBufCodecs.BOOL, FirearmFirePayload::aiming,
            FirearmFirePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
