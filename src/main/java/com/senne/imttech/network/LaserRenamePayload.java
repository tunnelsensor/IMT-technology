package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server (hoofdstuk 39): "geef deze laserpoort (of laserbrug) deze naam in mijn I.M.T-lijst",
 * vanuit het instelscherm. De server kijkt na of je hem mag instellen (zie shield/LaserEmitters en LaserBridges).
 */
public record LaserRenamePayload(BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<LaserRenamePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "laser_rename"));

    public static final StreamCodec<ByteBuf, LaserRenamePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, LaserRenamePayload::pos,
            ByteBufCodecs.stringUtf8(64), LaserRenamePayload::name,
            LaserRenamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
