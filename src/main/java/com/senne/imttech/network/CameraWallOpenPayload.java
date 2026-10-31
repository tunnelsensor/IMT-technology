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
 * Bericht van server naar speler (19 oktober): "open het instelscherm van deze schermmuur". De server beslist of het mag, en
 * geeft de monitor linksboven, de grootte, en in {@code data} de camera's en de tegels mee (zie CameraWall.snapshot).
 */
public record CameraWallOpenPayload(BlockPos pos, int width, int height, CompoundTag data) implements CustomPacketPayload {
    public static final Type<CameraWallOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "camera_wall_open"));

    public static final StreamCodec<ByteBuf, CameraWallOpenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CameraWallOpenPayload::pos,
            ByteBufCodecs.VAR_INT, CameraWallOpenPayload::width,
            ByteBufCodecs.VAR_INT, CameraWallOpenPayload::height,
            ByteBufCodecs.COMPOUND_TAG, CameraWallOpenPayload::data,
            CameraWallOpenPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
