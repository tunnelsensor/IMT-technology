package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server (hoofdstuk 26): "bewaar dit in het trainingsblok" (de instellingen van een Training
 * Start-blok, of het programma van een Training End-blok). De server kijkt opnieuw na of je het mag.
 *
 * @param pos  het blok
 * @param data wat er bewaard moet worden
 */
public record TrainingActionPayload(BlockPos pos, CompoundTag data) implements CustomPacketPayload {
    public static final Type<TrainingActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "training_action"));

    public static final StreamCodec<ByteBuf, TrainingActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, TrainingActionPayload::pos,
            ByteBufCodecs.COMPOUND_TAG, TrainingActionPayload::data,
            TrainingActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
