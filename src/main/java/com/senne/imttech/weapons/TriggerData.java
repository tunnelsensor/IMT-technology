package com.senne.imttech.weapons;

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
 * Wat een trigger onthoudt (de EMP Trigger en de Frag Trigger, hoofdstuk 35; eerst de detonator van C4): zijn eigen
 * nummer (een Remote EMP of Remote Frag onthoudt bij welke trigger hij hoort) en waar zijn ladingen zitten. Het zit op
 * het item zelf, zodat je spel de bordjes boven je ladingen kan tekenen als je hem vasthoudt (zie
 * client/weapons/TriggerMarkers).
 *
 * @param id      het nummer van deze trigger
 * @param charges waar zijn ladingen zitten (hooguit {@link #MAX_CHARGES})
 */
public record TriggerData(UUID id, List<GlobalPos> charges) {
    /** Zoveel ladingen onthoudt één trigger hooguit. */
    public static final int MAX_CHARGES = 32;

    public static final Codec<TriggerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("id").forGetter(TriggerData::id),
            GlobalPos.CODEC.listOf().optionalFieldOf("charges", List.of()).forGetter(TriggerData::charges)
    ).apply(instance, TriggerData::new));

    public static final StreamCodec<ByteBuf, TriggerData> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, TriggerData::id,
            GlobalPos.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_CHARGES)), TriggerData::charges,
            TriggerData::new);

    public TriggerData {
        charges = List.copyOf(charges);
    }

    /** Een lading erbij (als hij er nog niet in staat, en als er plek is). */
    public TriggerData with(GlobalPos charge) {
        if (charges.contains(charge) || charges.size() >= MAX_CHARGES) {
            return this;
        }
        List<GlobalPos> more = new ArrayList<>(charges);
        more.add(charge);
        return new TriggerData(id, more);
    }

    /** Alleen deze ladingen houden. */
    public TriggerData withCharges(List<GlobalPos> kept) {
        return new TriggerData(id, kept);
    }
}
