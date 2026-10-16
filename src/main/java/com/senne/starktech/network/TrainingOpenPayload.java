package com.senne.starktech.network;

import com.senne.starktech.StarkTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler (hoofdstuk 26): "open het scherm van dit trainingsblok". De server keek al na of je
 * het mag (wie het neerzette, of wie in de basis vertrouwd is).
 *
 * @param pos  het blok
 * @param end  false = de instellingen van een Training Start-blok, true = het programma van een Training End-blok
 * @param data wat erin staat (de instellingen, of het programma)
 */
public record TrainingOpenPayload(BlockPos pos, boolean end, CompoundTag data) implements CustomPacketPayload {
    public static final Type<TrainingOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "training_open"));

    public static final StreamCodec<ByteBuf, TrainingOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TrainingOpenPayload::pos,
            ByteBufCodecs.BOOL, TrainingOpenPayload::end,
            ByteBufCodecs.COMPOUND_TAG, TrainingOpenPayload::data,
            TrainingOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
