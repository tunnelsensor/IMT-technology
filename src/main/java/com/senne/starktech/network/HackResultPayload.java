package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Speler naar server: de hack-minigame is klaar ({@code done} = true) of je hebt hem afgebroken
 * (false). De server controleert zelf of dat klopt (zie TowerHack).
 */
public record HackResultPayload(BlockPos pos, boolean done) implements CustomPacketPayload {
    public static final Type<HackResultPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "hack_result"));

    public static final StreamCodec<ByteBuf, HackResultPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HackResultPayload::pos,
            ByteBufCodecs.BOOL, HackResultPayload::done,
            HackResultPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
