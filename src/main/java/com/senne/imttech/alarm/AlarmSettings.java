package com.senne.imttech.alarm;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wat een Alarm Speaker meeneemt als je hem afbreekt (hoofdstuk 126): zijn geluid en zijn instellingen, op het item. Zet je
 * hem ergens anders neer, dan hoef je niet opnieuw te uploaden (zoals een kern zijn instellingen houdt).
 *
 * Alleen de naam van het geluid (de vingerafdruk) staat op het item, nooit het geluid zelf: dat blijft in de map van de
 * wereld (zie AlarmSounds). Wat op een item staat, gaat naar het spel van wie het vasthoudt en wordt in kisten bewaard.
 *
 * @param soundId      de vingerafdruk van het geluid, of "" voor de ingebouwde sirene
 * @param soundName    de naam van het bestand ("sirene.mp4")
 * @param soundSeconds hoe lang het geluid duurt
 * @param volume       hoe hard (0,1 tot 1)
 * @param range        tot hoe ver je hem hoort (blokken)
 * @param alarmSeconds zo lang loeit hij bij een alarm (0 = tot je hem uitzet)
 */
public record AlarmSettings(String soundId, String soundName, float soundSeconds, float volume, int range, int alarmSeconds) {
    public static final Codec<AlarmSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("sound", "").forGetter(AlarmSettings::soundId),
            Codec.STRING.optionalFieldOf("sound_name", "").forGetter(AlarmSettings::soundName),
            Codec.FLOAT.optionalFieldOf("sound_seconds", 0f).forGetter(AlarmSettings::soundSeconds),
            Codec.FLOAT.optionalFieldOf("volume", 1f).forGetter(AlarmSettings::volume),
            Codec.INT.optionalFieldOf("range", AlarmSpeakerBlockEntity.DEFAULT_RANGE).forGetter(AlarmSettings::range),
            Codec.INT.optionalFieldOf("alarm_seconds", AlarmSpeakerBlockEntity.DEFAULT_ALARM_SECONDS).forGetter(AlarmSettings::alarmSeconds)
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

    /** Een nieuwe speaker: de sirene, vol volume, 32 blokken, 30 seconden. */
    public static final AlarmSettings DEFAULT = new AlarmSettings("", "", 0f, 1f, AlarmSpeakerBlockEntity.DEFAULT_RANGE,
            AlarmSpeakerBlockEntity.DEFAULT_ALARM_SECONDS);
}
