package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: "open het scherm van deze relay emitter" (13 oktober).
 *
 * @param pos    welke emitter
 * @param target de naam of tag die hij nu schakelt
 * @param mode   wat hij doet (zie RelayEmitterBlockEntity.Mode)
 * @param owner  de naam van de eigenaar
 * @param mine   ben jij de eigenaar? Anders kun je alleen kijken.
 * @param found  hoeveel apparaten hij nu schakelt
 */
public record EmitterOpenPayload(BlockPos pos, String target, int mode, String owner, boolean mine, int found)
        implements CustomPacketPayload {
    public static final Type<EmitterOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "emitter_open"));

    public static final StreamCodec<ByteBuf, EmitterOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, EmitterOpenPayload::pos,
            ByteBufCodecs.stringUtf8(64), EmitterOpenPayload::target,
            ByteBufCodecs.VAR_INT, EmitterOpenPayload::mode,
            ByteBufCodecs.stringUtf8(64), EmitterOpenPayload::owner,
            ByteBufCodecs.BOOL, EmitterOpenPayload::mine,
            ByteBufCodecs.VAR_INT, EmitterOpenPayload::found,
            EmitterOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
