package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van jouw spel naar de server: "ik schiet met mijn lasergeweer" (hoofdstuk 35), met waar je precies naar
 * keek ({@code yaw}, {@code pitch}: zo raakt het schot wat jij in je vizier had) en of je door het vizier richtte. De
 * server controleert de rest (zie weapons/LaserGuns).
 */
public record GunFirePayload(float yaw, float pitch, boolean aiming) implements CustomPacketPayload {
    public static final Type<GunFirePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "gun_fire"));

    public static final StreamCodec<ByteBuf, GunFirePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, GunFirePayload::yaw,
            ByteBufCodecs.FLOAT, GunFirePayload::pitch,
            ByteBufCodecs.BOOL, GunFirePayload::aiming,
            GunFirePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
