package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van speler naar server (hoofdstuk 126): een stukje van een alarmgeluid dat je uploadt.
 *
 * Een bericht van een speler naar de server mag in Minecraft hooguit 32 kB zijn, en een geluid van 30 seconden is zo'n
 * 330 kB (al klein gemaakt, zie Adpcm). Daarom gaat het in stukjes van hooguit {@link #CHUNK_BYTES}, genummerd vanaf 0. Het
 * eerste stukje begint de upload; de server zet ze achter elkaar en kijkt bij het laatste of alles klopt (zie
 * AlarmUploads).
 *
 * @param pos      op welke speaker het geluid komt
 * @param uploadId een nummer dat je spel voor deze upload kiest (een nieuwe upload begint opnieuw)
 * @param chunk    welk stukje (0, 1, 2...)
 * @param chunks   hoeveel stukjes er in totaal komen
 * @param samples  hoeveel monsters het hele geluid heeft (bij AlarmAudio.RATE per seconde)
 * @param fileName de naam van je bestand ("sirene.mp4"), om in het scherm te tonen
 * @param data     de bytes van dit stukje
 */
public record AlarmUploadPayload(BlockPos pos, int uploadId, int chunk, int chunks, int samples, String fileName, byte[] data)
        implements CustomPacketPayload {
    /** Zo groot is een stukje hooguit (met de rest van het bericht blijft het onder de 32 kB). */
    public static final int CHUNK_BYTES = 24 * 1024;

    public static final Type<AlarmUploadPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "alarm_upload"));

    public static final StreamCodec<ByteBuf, AlarmUploadPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AlarmUploadPayload decode(ByteBuf buffer) {
            BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
            int uploadId = ByteBufCodecs.VAR_INT.decode(buffer);
            int chunk = ByteBufCodecs.VAR_INT.decode(buffer);
            int chunks = ByteBufCodecs.VAR_INT.decode(buffer);
            int samples = ByteBufCodecs.VAR_INT.decode(buffer);
            String fileName = ByteBufCodecs.stringUtf8(128).decode(buffer);
            byte[] data = ByteBufCodecs.byteArray(CHUNK_BYTES).decode(buffer);
            return new AlarmUploadPayload(pos, uploadId, chunk, chunks, samples, fileName, data);
        }

        @Override
        public void encode(ByteBuf buffer, AlarmUploadPayload payload) {
            BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.uploadId());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.chunk());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.chunks());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.samples());
            ByteBufCodecs.stringUtf8(128).encode(buffer, payload.fileName());
            ByteBufCodecs.byteArray(CHUNK_BYTES).encode(buffer, payload.data());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
