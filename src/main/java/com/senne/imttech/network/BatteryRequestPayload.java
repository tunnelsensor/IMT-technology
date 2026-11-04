package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Bericht van speler naar server (hoofdstuk 47): het scherm van een Power Battery wil nieuwe cijfers (elke seconde). */
public record BatteryRequestPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<BatteryRequestPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "battery_request"));

    public static final StreamCodec<ByteBuf, BatteryRequestPayload> STREAM_CODEC = BlockPos.STREAM_CODEC
            .map(BatteryRequestPayload::new, BatteryRequestPayload::pos);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
