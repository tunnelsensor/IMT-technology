package com.senne.imttech.logic;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wat er op een beschreven keycard staat: een eigen nummer, een naam en een kleur.
 *
 * Een kaart wordt geschreven op de desktop van een beveiligingskern (de app Keycards). Daarom
 * staat erop van welk netwerk hij komt (het IP en de naam van de kern): een kaart van een ander
 * netwerk is bij jou niet geldig. Het nummer is uniek binnen dat netwerk; zo kun je één verloren of
 * gestolen kaart blokkeren zonder de rest.
 *
 * In welke groepen de kaart zit, staat NIET op de kaart maar in de kern (25 oktober, zie LogicStore):
 * zet je hem in een andere groep, dan geldt dat meteen, ook als iemand anders hem al bij zich heeft.
 * Tot 25 oktober had een kaart een niveau (1-5); een oude kaart houdt de kleur van zijn niveau.
 *
 * @param ip     het IP van de kern die hem schreef
 * @param host   de naam van die kern (alleen om te laten zien)
 * @param number het eigen nummer van de kaart (1001, 1002, ...)
 * @param colour de kleur van de kaart, 1 tot en met 8 (zie {@link #COLOURS})
 * @param label  de naam die de eigenaar hem gaf ("Lab access")
 */
public record KeycardData(String ip, String host, int number, int colour, String label) {
    /** Zo lang mag de naam van een kaart hooguit zijn. */
    public static final int MAX_LABEL = 24;

    /**
     * De kleuren die je kunt kiezen (25 oktober: "kleur kies je per kaart"). De eerste vijf zijn de kleuren
     * van de oude niveaus 1 tot 5, zodat een oude kaart er hetzelfde uit blijft zien. Kleur 1 is plek 0.
     */
    public static final int[] COLOURS = {0xE8EEF2, 0x5BE37D, 0x4FA8FF, 0xC27BFF, 0xFFC93C, 0xFF544C, 0xFF9632, 0x30343C};
    /** Hun namen (voor de tooltip in de app). */
    public static final String[] COLOUR_NAMES = {"white", "green", "blue", "purple", "gold", "red", "orange", "black"};

    public KeycardData {
        colour = clampColour(colour);
    }

    /** Een kleur die bestaat: 1 tot en met 8 (iets anders wordt wit). */
    public static int clampColour(int colour) {
        return colour >= 1 && colour <= COLOURS.length ? colour : 1;
    }

    /** De kleur (als 0xRRGGBB) van kleur {@code colour} (1-8). */
    public static int rgb(int colour) {
        return COLOURS[clampColour(colour) - 1];
    }

    /**
     * Opslaan in het item. Een kaart van voor 25 oktober heeft geen "colour" maar een "level" (1-5): dan wordt dat
     * zijn kleur (dezelfde kleur als hij altijd had). Nieuwe kaarten krijgen alleen "colour".
     */
    public static final Codec<KeycardData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("ip").forGetter(KeycardData::ip),
            Codec.STRING.optionalFieldOf("host", "").forGetter(KeycardData::host),
            Codec.INT.fieldOf("number").forGetter(KeycardData::number),
            Codec.INT.optionalFieldOf("colour").forGetter(data -> Optional.of(data.colour())),
            Codec.INT.optionalFieldOf("level").forGetter(data -> Optional.empty()),
            Codec.STRING.optionalFieldOf("label", "").forGetter(KeycardData::label)
    ).apply(instance, (ip, host, number, colour, level, label) ->
            new KeycardData(ip, host, number, colour.orElse(level.orElse(1)), label)));

    public static final StreamCodec<ByteBuf, KeycardData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, KeycardData::ip,
            ByteBufCodecs.STRING_UTF8, KeycardData::host,
            ByteBufCodecs.VAR_INT, KeycardData::number,
            ByteBufCodecs.VAR_INT, KeycardData::colour,
            ByteBufCodecs.STRING_UTF8, KeycardData::label,
            KeycardData::new);
}
