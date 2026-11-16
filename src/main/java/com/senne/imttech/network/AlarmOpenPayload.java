package com.senne.imttech.network;

import com.senne.imttech.ImtTech;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Bericht van server naar speler (hoofdstuk 126): wat het scherm van een Alarm Speaker toont. Alleen wie de speaker mag
 * instellen krijgt het (zie AlarmSpeakers.open). De server stuurt het als je hem opent (sneak + rechtsklik), en opnieuw
 * na elke knop en na een upload, zodat het scherm bijblijft.
 *
 * @param pos            waar de speaker hangt
 * @param name           zijn naam ("Alarm 1")
 * @param soundName      de naam van het bestand dat erop staat ("sirene.mp4"), of "" voor de ingebouwde sirene
 * @param soundSeconds   hoe lang dat geluid duurt
 * @param soundState     {@link #DEFAULT_SOUND}, {@link #CUSTOM_SOUND} of {@link #MISSING_SOUND}
 * @param volume         hoe hard (0,1 tot 1)
 * @param range          tot hoe ver je hem hoort (blokken)
 * @param alarmSeconds   zo lang loeit hij als een script hem aanzet met on("naam") (0 = tot je hem uitzet)
 * @param maxSeconds     zoveel seconden van een bestand neemt de server hooguit (uit de config)
 * @param status         {@link #ONLINE}, {@link #NO_POWER}, {@link #NO_NETWORK} of {@link #EMP}
 * @param uploadsAllowed mag je op deze server uploaden ({@code [alarm] uploadsAllowed})?
 * @param sounding       gaat hij nu af?
 * @param open           moet het scherm opengaan (true) of alleen bijgewerkt worden als het al open staat?
 */
public record AlarmOpenPayload(BlockPos pos, String name, String soundName, float soundSeconds, int soundState, float volume,
                               int range, int alarmSeconds, int maxSeconds, int status, boolean uploadsAllowed, boolean sounding,
                               boolean open) implements CustomPacketPayload {
    public static final int DEFAULT_SOUND = 0;
    public static final int CUSTOM_SOUND = 1;
    /** Er stond een eigen geluid op, maar de server heeft het niet meer (opgeruimd): je hoort de sirene. */
    public static final int MISSING_SOUND = 2;

    public static final int ONLINE = 0;
    public static final int NO_POWER = 1;
    public static final int NO_NETWORK = 2;
    public static final int EMP = 3;

    public static final Type<AlarmOpenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "alarm_open"));

    public static final StreamCodec<ByteBuf, AlarmOpenPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AlarmOpenPayload decode(ByteBuf buffer) {
            BlockPos pos = BlockPos.STREAM_CODEC.decode(buffer);
            String name = ByteBufCodecs.stringUtf8(64).decode(buffer);
            String soundName = ByteBufCodecs.stringUtf8(128).decode(buffer);
            float soundSeconds = buffer.readFloat();
            int soundState = ByteBufCodecs.VAR_INT.decode(buffer);
            float volume = buffer.readFloat();
            int range = ByteBufCodecs.VAR_INT.decode(buffer);
            int alarmSeconds = ByteBufCodecs.VAR_INT.decode(buffer);
            int maxSeconds = ByteBufCodecs.VAR_INT.decode(buffer);
            int status = ByteBufCodecs.VAR_INT.decode(buffer);
            int flags = buffer.readByte();
            return new AlarmOpenPayload(pos, name, soundName, soundSeconds, soundState, volume, range, alarmSeconds, maxSeconds,
                    status, (flags & 1) != 0, (flags & 2) != 0, (flags & 4) != 0);
        }

        @Override
        public void encode(ByteBuf buffer, AlarmOpenPayload payload) {
            BlockPos.STREAM_CODEC.encode(buffer, payload.pos());
            ByteBufCodecs.stringUtf8(64).encode(buffer, payload.name());
            ByteBufCodecs.stringUtf8(128).encode(buffer, payload.soundName());
            buffer.writeFloat(payload.soundSeconds());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.soundState());
            buffer.writeFloat(payload.volume());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.range());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.alarmSeconds());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.maxSeconds());
            ByteBufCodecs.VAR_INT.encode(buffer, payload.status());
            buffer.writeByte((payload.uploadsAllowed() ? 1 : 0) | (payload.sounding() ? 2 : 0) | (payload.open() ? 4 : 0));
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
