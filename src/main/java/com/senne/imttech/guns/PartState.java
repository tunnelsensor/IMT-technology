package com.senne.imttech.guns;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.imttech.config.ImtConfig;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Eén onderdeel zoals het in een pistool zit (hoofdstuk 52): van welk materiaal, en hoe heel het nog is
 * ({@code condition}: 100 = nieuw, 0 = kapot). Elk schot heeft per onderdeel een kans (van het materiaal, zie
 * {@link GunMaterial#wearChance}) om één punt te slijten. Onder {@code [guns] wornBelow} (40) is het versleten: dan
 * geeft het steeds vaker storingen. Op 0 is het kapot en doet het pistool niets meer tot je het vervangt op de
 * Gunsmith Table.
 */
public record PartState(GunMaterial material, int condition) {
    public static final int NEW = 100;

    public static final Codec<PartState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            GunMaterial.CODEC.fieldOf("material").forGetter(PartState::material),
            Codec.INT.optionalFieldOf("condition", NEW).forGetter(PartState::condition)
    ).apply(instance, PartState::new));

    public static final StreamCodec<ByteBuf, PartState> STREAM_CODEC = StreamCodec.composite(
            GunMaterial.STREAM_CODEC, PartState::material,
            ByteBufCodecs.VAR_INT, PartState::condition,
            PartState::new);

    public static PartState fresh(GunMaterial material) {
        return new PartState(material, NEW);
    }

    public boolean broken() {
        return condition <= 0;
    }

    /** Versleten: onder de grens uit de config (dan komen de storingen). */
    public boolean worn() {
        return condition < ImtConfig.Guns.WORN_BELOW;
    }

    /**
     * Hoeveel extra storingen dit onderdeel geeft: 0 als het niet versleten is, oplopend tot {@code [guns] wornJamFactor}
     * (3) keer zo veel als het bijna kapot is.
     */
    public float wornFactor() {
        float below = Math.max(1, ImtConfig.Guns.WORN_BELOW);
        if (condition >= below) {
            return 0;
        }
        return (below - condition) / below * ImtConfig.Guns.WORN_JAM_FACTOR;
    }

    /** Eén punt versleten (nooit onder 0). */
    public PartState worn1() {
        return new PartState(material, Math.max(0, condition - 1));
    }
}
