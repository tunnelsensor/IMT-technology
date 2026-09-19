package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Bericht van speler naar server: "markeer (of ontmarkeer) dit wezen als doelwit".
 * We sturen het nummer van het wezen; de server controleert zelf of dat mag.
 */
public record MarkTargetPayload(int entityId) implements CustomPacketPayload {
    public static final Type<MarkTargetPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(StarkTech.MODID, "mark_target"));

    public static final StreamCodec<ByteBuf, MarkTargetPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(MarkTargetPayload::new, MarkTargetPayload::entityId);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
