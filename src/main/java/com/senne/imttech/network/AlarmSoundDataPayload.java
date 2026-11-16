package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler (hoofdstuk 126): een stukje van een alarmgeluid dat je spel vroeg
 * (AlarmSoundRequestPayload). Een geluid van 30 seconden past in drie stukjes van {@link #CHUNK_BYTES}.
 *
 * Heeft de server het geluid niet (meer), dan komt er één bericht met {@code chunks == 0}: dan weet je spel dat het niet
 * hoeft te wachten, en laat de speaker de ingebouwde sirene horen. Vroeg je spel te veel tegelijk, dan komt er één bericht
 * met {@code chunks == }{@link #BUSY}: "even niet, vraag het straks opnieuw".
 *
 * @param id      welk geluid
 * @param chunk   welk stukje (0, 1, 2...)
 * @param chunks  hoeveel stukjes er in totaal komen (0 = het geluid bestaat niet, {@link #BUSY} = straks opnieuw)
 * @param samples hoeveel monsters het hele geluid heeft
 * @param data    de bytes van dit stukje (ADPCM)
 */
public record AlarmSoundDataPayload(String id, int chunk, int chunks, int samples, byte[] data) implements CustomPacketPayload {
    /** Zo groot is een stukje hooguit (een bericht naar de speler mag tot 1 MB zijn). */
    public static final int CHUNK_BYTES = 128 * 1024;
    /** {@code chunks} als de server "even niet" zegt (je spel vroeg te veel geluiden tegelijk). */
    public static final int BUSY = -1;

    public static final Type<AlarmSoundDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "alarm_sound_data"));

    public static final StreamCodec<ByteBuf, AlarmSoundDataPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), AlarmSoundDataPayload::id,
            ByteBufCodecs.VAR_INT, AlarmSoundDataPayload::chunk,
            ByteBufCodecs.VAR_INT, AlarmSoundDataPayload::chunks,
            ByteBufCodecs.VAR_INT, AlarmSoundDataPayload::samples,
            ByteBufCodecs.byteArray(CHUNK_BYTES), AlarmSoundDataPayload::data,
            AlarmSoundDataPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
