package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler: "open het scherm van deze stroomkast". De server beslist of
 * het mag (dichtbij genoeg) en vertelt of jij de eigenaar bent (dan mag je hem bij E.D.I.T.H.
 * aanmelden).
 */
public record CabinetOpenPayload(BlockPos pos, boolean owner) implements CustomPacketPayload {
    public static final Type<CabinetOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "cabinet_open"));

    public static final StreamCodec<ByteBuf, CabinetOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CabinetOpenPayload::pos,
            ByteBufCodecs.BOOL, CabinetOpenPayload::owner,
            CabinetOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
