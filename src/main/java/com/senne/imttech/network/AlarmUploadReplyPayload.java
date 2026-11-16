package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler (hoofdstuk 126): hoe je upload afliep. Het scherm van de speaker toont het (en sluit bij
 * succes); stond het scherm al dicht, dan zie je de zin boven je hotbar.
 *
 * @param pos      op welke speaker
 * @param uploadId welke upload (het nummer dat je spel koos)
 * @param ok       is het geluid nu op de speaker gezet?
 * @param message  de vertaalsleutel van de zin ("alarm.imttech.upload.done")
 * @param argument wat er in de zin komt (de naam van het bestand, het maximum aantal seconden), of ""
 */
public record AlarmUploadReplyPayload(BlockPos pos, int uploadId, boolean ok, String message, String argument)
        implements CustomPacketPayload {
    public static final Type<AlarmUploadReplyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "alarm_upload_reply"));

    public static final StreamCodec<ByteBuf, AlarmUploadReplyPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, AlarmUploadReplyPayload::pos,
            ByteBufCodecs.VAR_INT, AlarmUploadReplyPayload::uploadId,
            ByteBufCodecs.BOOL, AlarmUploadReplyPayload::ok,
            ByteBufCodecs.stringUtf8(128), AlarmUploadReplyPayload::message,
            ByteBufCodecs.stringUtf8(128), AlarmUploadReplyPayload::argument,
            AlarmUploadReplyPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
