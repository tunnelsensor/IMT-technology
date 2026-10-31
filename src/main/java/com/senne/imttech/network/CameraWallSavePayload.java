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
 * Bericht van speler naar server (19 oktober): de nieuwe indeling van deze schermmuur, een lijst tegels onder "tiles". De
 * server kijkt alles zelf na (zie CameraWall.handleSave).
 */
public record CameraWallSavePayload(BlockPos pos, CompoundTag tiles) implements CustomPacketPayload {
    public static final Type<CameraWallSavePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "camera_wall_save"));

    public static final StreamCodec<ByteBuf, CameraWallSavePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CameraWallSavePayload::pos,
            ByteBufCodecs.COMPOUND_TAG, CameraWallSavePayload::tiles,
            CameraWallSavePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
