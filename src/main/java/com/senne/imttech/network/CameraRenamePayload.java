package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server (hoofdstuk 93): "geef deze bewakingscamera deze naam" (Save in het naamscherm dat je opent
 * met sneak + rechtsklik op de camera). De server kijkt opnieuw na of je het mag (zie security/CameraNames).
 *
 * @param pos  welke camera
 * @param name de nieuwe naam, zoals je hem typte
 */
public record CameraRenamePayload(BlockPos pos, String name) implements CustomPacketPayload {
    public static final Type<CameraRenamePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "camera_rename"));

    public static final StreamCodec<ByteBuf, CameraRenamePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, CameraRenamePayload::pos,
            ByteBufCodecs.stringUtf8(64), CameraRenamePayload::name,
            CameraRenamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
