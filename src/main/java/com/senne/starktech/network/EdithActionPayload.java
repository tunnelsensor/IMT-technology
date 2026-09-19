package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: "E.D.I.T.H., doe deze actie".
 * We sturen alleen het nummer van de actie (zie EdithAction).
 */
public record EdithActionPayload(int action) implements CustomPacketPayload {
    public static final Type<EdithActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "edith_action"));

    public static final StreamCodec<ByteBuf, EdithActionPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(EdithActionPayload::new, EdithActionPayload::action);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
