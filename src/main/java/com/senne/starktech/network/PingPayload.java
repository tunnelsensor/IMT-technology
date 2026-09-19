package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Bericht van speler naar server: "plaats een ping op deze plek".
 */
public record PingPayload(Vec3 pos) implements CustomPacketPayload {
    public static final Type<PingPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "ping"));

    public static final StreamCodec<ByteBuf, PingPayload> STREAM_CODEC = ModCodecs.VEC3.map(PingPayload::new, PingPayload::pos);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
