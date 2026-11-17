package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server (hoofdstuk 126): Save in het scherm van een Speaker. De server kijkt opnieuw na of je hem
 * mag instellen, en houdt alles binnen de grenzen (zie AlarmSpeakers.handleSave).
 *
 * @param pos    welke speaker
 * @param name   de naam (in je diagrammen: {@code playsound("siren", "Lab speaker")})
 * @param volume hoe hard (0,1 tot 1)
 * @param range  tot hoe ver je hem hoort (blokken)
 */
public record AlarmSavePayload(BlockPos pos, String name, float volume, int range) implements CustomPacketPayload {
    public static final Type<AlarmSavePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "alarm_save"));

    public static final StreamCodec<ByteBuf, AlarmSavePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AlarmSavePayload::pos,
            ByteBufCodecs.stringUtf8(64), AlarmSavePayload::name,
            ByteBufCodecs.FLOAT, AlarmSavePayload::volume,
            ByteBufCodecs.VAR_INT, AlarmSavePayload::range,
            AlarmSavePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
