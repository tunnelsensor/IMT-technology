package com.senne.imttech.car;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wat een RC Controller onthoudt (hoofdstuk 108): met welke RC Car hij gekoppeld is.
 *
 * @param car  het vaste nummer van de auto (dat blijft hetzelfde als je hem oppakt en weer neerzet)
 * @param name hoe de auto heette toen je koppelde (voor de tooltip)
 */
public record RcLink(UUID car, String name) {
    public static final Codec<RcLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("car").forGetter(RcLink::car),
            Codec.STRING.optionalFieldOf("name", "").forGetter(RcLink::name)
    ).apply(instance, RcLink::new));

    public static final StreamCodec<ByteBuf, RcLink> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RcLink::car,
            ByteBufCodecs.STRING_UTF8, RcLink::name,
            RcLink::new);
}
