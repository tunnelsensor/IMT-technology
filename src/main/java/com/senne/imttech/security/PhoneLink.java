package com.senne.imttech.security;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wat een I.M.T Phone (hoofdstuk 50) onthoudt: aan welke kern hij hangt (het IP en de naam van het systeem) en zijn eigen
 * nummer. De PIN staat NIET op de telefoon maar bij de kern (zie SecurityCoreBlockEntity.Phone): wie een telefoon
 * steelt, vindt de PIN er niet in.
 */
public record PhoneLink(String ip, String host, UUID id) {
    public static final Codec<PhoneLink> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("ip").forGetter(PhoneLink::ip),
            Codec.STRING.optionalFieldOf("host", "").forGetter(PhoneLink::host),
            UUIDUtil.CODEC.fieldOf("id").forGetter(PhoneLink::id)).apply(instance, PhoneLink::new));

    public static final StreamCodec<ByteBuf, PhoneLink> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(32), PhoneLink::ip,
            ByteBufCodecs.stringUtf8(64), PhoneLink::host,
            UUIDUtil.STREAM_CODEC, PhoneLink::id,
            PhoneLink::new);
}
