package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler (hoofdstuk 136): je dubbelklikte in Files op een geluid; dat komt nu in stukjes
 * (AlarmSoundDataPayload) en je spel speelt het alleen voor jou af (zie AlarmSpeakers.preview).
 *
 * @param id      welk geluid (de vingerafdruk)
 * @param samples hoeveel monsters het heeft
 */
public record AlarmPreviewPayload(String id, int samples) implements CustomPacketPayload {
    public static final Type<AlarmPreviewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "alarm_preview"));

    public static final StreamCodec<ByteBuf, AlarmPreviewPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), AlarmPreviewPayload::id,
            ByteBufCodecs.VAR_INT, AlarmPreviewPayload::samples,
            AlarmPreviewPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
