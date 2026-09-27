package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Speler naar server: "dit commando typte ik in de terminal van de laptop op {@code pos}". De
 * laptop onthoudt het (zo ben je je voortgang niet kwijt), en de server speelt alles na om te zien
 * of de toren nu van jou is.
 */
public record HackCommandPayload(BlockPos pos, String command) implements CustomPacketPayload {
    /** Langer kan een commando in de terminal niet zijn (zie HackScreen). */
    public static final int MAX_LENGTH = 64;

    public static final Type<HackCommandPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "hack_command"));

    public static final StreamCodec<ByteBuf, HackCommandPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, HackCommandPayload::pos,
            ByteBufCodecs.stringUtf8(MAX_LENGTH), HackCommandPayload::command,
            HackCommandPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
