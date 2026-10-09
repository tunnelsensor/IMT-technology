package com.senne.starktech.c4;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Wat een detonator onthoudt (hoofdstuk 10): zijn eigen nummer (een remote C4 onthoudt bij welke
 * detonator hij hoort) en waar zijn ladingen zitten. Het zit op het item zelf, zodat je spel de rode
 * markeringen kan tekenen als je hem vasthoudt.
 *
 * @param id      het nummer van deze detonator
 * @param charges waar zijn ladingen zitten (hooguit {@link #MAX_CHARGES})
 */
public record DetonatorData(UUID id, List<GlobalPos> charges) {
    /** Zoveel ladingen onthoudt één detonator hooguit. */
    public static final int MAX_CHARGES = 32;

    public static final Codec<DetonatorData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(DetonatorData::id),
            GlobalPos.CODEC.listOf().optionalFieldOf("charges", List.of()).forGetter(DetonatorData::charges)
    ).apply(instance, DetonatorData::new));

    public static final StreamCodec<ByteBuf, DetonatorData> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, DetonatorData::id,
            GlobalPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CHARGES)), DetonatorData::charges,
            DetonatorData::new);

    public DetonatorData {
        charges = List.copyOf(charges);
    }

    /** Een lading erbij (als hij er nog niet in staat, en als er plek is). */
    public DetonatorData with(GlobalPos charge) {
        if (charges.contains(charge) || charges.size() >= MAX_CHARGES) {
            return this;
        }
        List<GlobalPos> more = new ArrayList<>(charges);
        more.add(charge);
        return new DetonatorData(id, more);
    }

    /** Alleen deze ladingen houden. */
    public DetonatorData withCharges(List<GlobalPos> kept) {
        return new DetonatorData(id, kept);
    }
}
