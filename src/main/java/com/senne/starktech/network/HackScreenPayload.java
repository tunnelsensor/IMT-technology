package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server naar speler: open het hack-scherm van de laptop voor de beveiligingskern op {@code pos}
 * ({@code open} = true), of sluit het ({@code open} = false: afgebroken, bv. te ver weg of dood).
 */
public record HackScreenPayload(BlockPos pos, boolean open) implements CustomPacketPayload {
    public static final Type<HackScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "hack_screen"));

    public static final StreamCodec<ByteBuf, HackScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HackScreenPayload::pos,
            ByteBufCodecs.BOOL, HackScreenPayload::open,
            HackScreenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
