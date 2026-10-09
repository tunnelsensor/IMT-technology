package com.senne.starktech.security;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Het etiket op een ingepakte beveiligingskern: een kern die je weghakte (of die opgeblazen werd) en
 * die je nu als item hebt (jouw wens, 9 oktober: "zorg dat al de config bewaard wordt in de security
 * block, zodat als je die hakt niet alles weg is").
 *
 * De instellingen zelf (wachtwoord, bestanden, keycards, diagrammen...) staan NIET op het item, maar
 * op de server (zie {@link PackedCores}). Waarom? Alles wat op een item staat, gaat ook naar het spel
 * van wie het vasthoudt, en het wachtwoord mag nooit naar iemands spel (zie SystemPassword). Op het
 * item staat dus alleen het nummer waaronder de server alles bewaart, en wat je in de tooltip ziet.
 *
 * @param id    het nummer in PackedCores
 * @param owner van wie de kern is
 * @param host  de naam van het systeem ("senne-sec")
 * @param ip    het IP-adres dat hij terugkrijgt als je hem neerzet (als het nog vrij is)
 */
public record PackedCore(UUID id, String owner, String host, String ip) {
    public static final Codec<PackedCore> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(PackedCore::id),
            Codec.STRING.optionalFieldOf("owner", "").forGetter(PackedCore::owner),
            Codec.STRING.optionalFieldOf("host", "").forGetter(PackedCore::host),
            Codec.STRING.optionalFieldOf("ip", "").forGetter(PackedCore::ip)
    ).apply(instance, PackedCore::new));

    public static final StreamCodec<ByteBuf, PackedCore> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PackedCore::id,
            ByteBufCodecs.STRING_UTF8, PackedCore::owner,
            ByteBufCodecs.STRING_UTF8, PackedCore::host,
            ByteBufCodecs.STRING_UTF8, PackedCore::ip,
            PackedCore::new);
}
