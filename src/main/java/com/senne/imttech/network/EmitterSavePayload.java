package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server: "zo moet deze relay emitter het doen" (13 oktober).
 *
 * @param pos    welke emitter
 * @param target de naam van een relais, of een tag
 * @param mode   wat hij doet (zie RelayEmitterBlockEntity.Mode)
 */
public record EmitterSavePayload(BlockPos pos, String target, int mode) implements CustomPacketPayload {
    public static final Type<EmitterSavePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "emitter_save"));

    public static final StreamCodec<ByteBuf, EmitterSavePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, EmitterSavePayload::pos,
            ByteBufCodecs.stringUtf8(64), EmitterSavePayload::target,
            ByteBufCodecs.VAR_INT, EmitterSavePayload::mode,
            EmitterSavePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
