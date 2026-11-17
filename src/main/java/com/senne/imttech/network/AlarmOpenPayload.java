package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler (hoofdstuk 126): open het scherm van een Speaker, of werk het bij (zie
 * AlarmSpeakers.openPayload). Sinds hoofdstuk 136 alleen de naam en hoe hard en hoe ver: een eigen geluid heeft een speaker
 * niet meer (de geluiden staan in Files van de kern).
 *
 * @param pos      welke speaker
 * @param name     zijn naam
 * @param volume   hoe hard (0,1 tot 1)
 * @param range    tot hoe ver je hem hoort (blokken)
 * @param status   {@link #ONLINE}, {@link #NO_POWER}, {@link #NO_NETWORK} of {@link #EMP}
 * @param sounding speelt hij nu iets?
 * @param open     het scherm openen (true) of alleen bijwerken als het al open staat (false)
 */
public record AlarmOpenPayload(BlockPos pos, String name, float volume, int range, int status, boolean sounding, boolean open)
        implements CustomPacketPayload {
    public static final int ONLINE = 0;
    public static final int NO_POWER = 1;
    public static final int NO_NETWORK = 2;
    public static final int EMP = 3;

    public static final Type<AlarmOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "alarm_open"));

    /** Met de hand (StreamCodec.composite kan hooguit zes velden). */
    public static final StreamCodec<ByteBuf, AlarmOpenPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
                ByteBufCodecs.stringUtf8(64).encode(buffer, payload.name());
                ByteBufCodecs.FLOAT.encode(buffer, payload.volume());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.range());
                ByteBufCodecs.VAR_INT.encode(buffer, payload.status());
                ByteBufCodecs.BOOL.encode(buffer, payload.sounding());
                ByteBufCodecs.BOOL.encode(buffer, payload.open());
            },
            buffer -> new AlarmOpenPayload(BlockPos.STREAM_CODEC.decode(buffer), ByteBufCodecs.stringUtf8(64).decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer), ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer), ByteBufCodecs.BOOL.decode(buffer)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
