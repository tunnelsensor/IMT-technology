package com.senne.imttech.alarm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wat een Speaker meeneemt als je hem afbreekt (hoofdstuk 126): zijn instellingen, op het item. Sinds hoofdstuk 136 heeft
 * een speaker geen eigen geluid meer (de geluiden staan in Files van de kern); het geluid op een oud item gaat bij het
 * neerzetten naar /sounds van zijn kern. De velden blijven, zodat oude items gewoon blijven werken.
 *
 * Alleen de naam van het geluid (de vingerafdruk) staat op het item, nooit het geluid zelf: dat blijft in de map van de
 * wereld (zie AlarmSounds). Wat op een item staat, gaat naar het spel van wie het vasthoudt en wordt in kisten bewaard.
 *
 * @param soundId      de vingerafdruk van het geluid, of "" voor de ingebouwde sirene
 * @param soundName    de naam van het bestand ("sirene.mp4")
 * @param soundSeconds hoe lang het geluid duurt
 * @param volume       hoe hard (0,1 tot 1)
 * @param range        tot hoe ver je hem hoort (blokken)
 * @param alarmSeconds (van vóór hoofdstuk 136, niet meer gebruikt)
 */
public record AlarmSettings(String soundId, String soundName, float soundSeconds, float volume, int range, int alarmSeconds) {
    /** "Hoe lang" van vóór hoofdstuk 136 (bleef in het formaat van het item, zodat oude items gelijk blijven). */
    static final int OLD_ALARM_SECONDS = 30;

    public static final Codec<AlarmSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("sound", "").forGetter(AlarmSettings::soundId),
            Codec.STRING.optionalFieldOf("sound_name", "").forGetter(AlarmSettings::soundName),
            Codec.FLOAT.optionalFieldOf("sound_seconds", 0f).forGetter(AlarmSettings::soundSeconds),
            Codec.FLOAT.optionalFieldOf("volume", 1f).forGetter(AlarmSettings::volume),
            Codec.INT.optionalFieldOf("range", AlarmSpeakerBlockEntity.DEFAULT_RANGE).forGetter(AlarmSettings::range),
            Codec.INT.optionalFieldOf("alarm_seconds", OLD_ALARM_SECONDS).forGetter(AlarmSettings::alarmSeconds)
    ).apply(instance, AlarmSettings::new));

    public static final StreamCodec<ByteBuf, AlarmSettings> STREAM_CODEC = StreamCodec.composite(
            // Geen krappe grens hier (gevonden bij de nalezing): een item met een te lange tekst (bijvoorbeeld via /give)
            // zou anders bij het versturen een fout geven en de speler eruit gooien. Het blok kort het zelf in (clean).
            ByteBufCodecs.STRING_UTF8, AlarmSettings::soundId,
            ByteBufCodecs.STRING_UTF8, AlarmSettings::soundName,
            ByteBufCodecs.FLOAT, AlarmSettings::soundSeconds,
            ByteBufCodecs.FLOAT, AlarmSettings::volume,
            ByteBufCodecs.VAR_INT, AlarmSettings::range,
            ByteBufCodecs.VAR_INT, AlarmSettings::alarmSeconds,
            AlarmSettings::new);

    /** Een nieuwe speaker: vol volume, 32 blokken. */
    public static final AlarmSettings DEFAULT = new AlarmSettings("", "", 0f, 1f, AlarmSpeakerBlockEntity.DEFAULT_RANGE,
            OLD_ALARM_SECONDS);
}
