package com.senne.imttech.logic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wat er op een beschreven keycard staat (jouw keuze, 7 oktober: een niveau en een eigen nummer).
 *
 * Een kaart wordt geschreven op de desktop van een beveiligingskern (de app Keycards). Daarom
 * staat erop van welk netwerk hij komt (het IP en de naam van de kern): een kaart van een ander
 * netwerk is bij jou niet geldig. Het nummer is uniek binnen dat netwerk; zo kun je één verloren of
 * gestolen kaart blokkeren zonder de rest.
 *
 * @param ip     het IP van de kern die hem schreef
 * @param host   de naam van die kern (alleen om te laten zien)
 * @param number het eigen nummer van de kaart (1001, 1002, ...)
 * @param level  het niveau, 1 tot en met 5
 * @param label  de naam die de eigenaar hem gaf ("Lab access")
 */
public record KeycardData(String ip, String host, int number, int level, String label) {
    /** Het hoogste niveau. */
    public static final int MAX_LEVEL = 5;
    /** Zo lang mag de naam van een kaart hooguit zijn. */
    public static final int MAX_LABEL = 24;

    public static final Codec<KeycardData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("ip").forGetter(KeycardData::ip),
            Codec.STRING.optionalFieldOf("host", "").forGetter(KeycardData::host),
            Codec.INT.fieldOf("number").forGetter(KeycardData::number),
            Codec.INT.fieldOf("level").forGetter(KeycardData::level),
            Codec.STRING.optionalFieldOf("label", "").forGetter(KeycardData::label)
    ).apply(instance, KeycardData::new));

    public static final StreamCodec<ByteBuf, KeycardData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, KeycardData::ip,
            ByteBufCodecs.STRING_UTF8, KeycardData::host,
            ByteBufCodecs.VAR_INT, KeycardData::number,
            ByteBufCodecs.VAR_INT, KeycardData::level,
            ByteBufCodecs.STRING_UTF8, KeycardData::label,
            KeycardData::new);
}
