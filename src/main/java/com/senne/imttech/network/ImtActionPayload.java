package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: "I.M.T, doe deze actie".
 * We sturen alleen het nummer van de actie (zie ImtAction).
 */
public record ImtActionPayload(int action) implements CustomPacketPayload {
    public static final Type<ImtActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "imt_action"));

    public static final StreamCodec<ByteBuf, ImtActionPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(ImtActionPayload::new, ImtActionPayload::action);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
