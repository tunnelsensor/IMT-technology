package com.senne.imttech.imt;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Met welke security core deze I.M.T-bril verbonden is (hoofdstuk 123, jouw wens van 15 november: "zorg dat de bril
 * connectie niet aan de speler hangt maar echt aan de bril"). Het staat op de bril zelf, als een stukje data op het item,
 * net als bij de I.M.T Phone: geef je de bril door, dan blijft hij verbonden. Verbinden doe je in het G-menu (Systems,
 * Datacenter) met het IP en het wachtwoord van de kern, zie logic/GlassesUplink.
 *
 * @param ip    het IP van de kern (zo vinden we hem terug, ook als hij verhuisde: een kern houdt zijn IP)
 * @param host  de naam van de kern toen je verbond ("dev-sec"), voor de tooltip en als de kern niet meer antwoordt
 * @param stamp welk wachtwoord de kern had toen je verbond: het willekeurige stukje van dat wachtwoord (de "salt", zie
 *              SystemPassword.stamp), waaruit je het wachtwoord zelf niet kunt halen. Zet de eigenaar een nieuw wachtwoord,
 *              dan klopt het niet meer, en moet je opnieuw verbinden.
 */
public record ImtUplink(String ip, String host, String stamp) {
    /** Hoe dit in de wereld bewaard wordt (op het item). */
    public static final Codec<ImtUplink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("ip").forGetter(ImtUplink::ip),
            Codec.STRING.optionalFieldOf("host", "").forGetter(ImtUplink::host),
            Codec.STRING.optionalFieldOf("stamp", "").forGetter(ImtUplink::stamp)
    ).apply(instance, ImtUplink::new));

    /** Hoe dit over het netwerk naar spelers gaat (het item in je inventaris). */
    public static final StreamCodec<ByteBuf, ImtUplink> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), ImtUplink::ip,
            ByteBufCodecs.stringUtf8(64), ImtUplink::host,
            ByteBufCodecs.stringUtf8(64), ImtUplink::stamp,
            ImtUplink::new);
}
