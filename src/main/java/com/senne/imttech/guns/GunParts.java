package com.senne.imttech.guns;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jetbrains.annotations.Nullable;

/**
 * Welke onderdelen er in een pistool zitten (hoofdstuk 52), per {@link GunPart} een {@link PartState}. Een pistool
 * zonder alle zes is "incomplete" en schiet niet. Dit zit als data component aan het pistool (GUN_PARTS); het is
 * onveranderlijk: elke verandering geeft een nieuwe GunParts.
 */
public record GunParts(Map<GunPart, PartState> parts) {
    public static final GunParts EMPTY = new GunParts(Map.of());

    public static final Codec<GunParts> CODEC = Codec.unboundedMap(GunPart.CODEC, PartState.CODEC)
            .xmap(GunParts::new, GunParts::parts);

    public static final StreamCodec<ByteBuf, GunParts> STREAM_CODEC = ByteBufCodecs
            .map(i -> new EnumMap<GunPart, PartState>(GunPart.class), GunPart.STREAM_CODEC, PartState.STREAM_CODEC)
            .map(GunParts::new, GunParts::enumMap);

    public GunParts {
        EnumMap<GunPart, PartState> copy = new EnumMap<>(GunPart.class);
        copy.putAll(parts);
        parts = Collections.unmodifiableMap(copy);
    }

    private EnumMap<GunPart, PartState> enumMap() {
        EnumMap<GunPart, PartState> copy = new EnumMap<>(GunPart.class);
        copy.putAll(parts);
        return copy;
    }

    /** Een compleet nieuw pistool, alle onderdelen van dit materiaal (polymeer waar dat mag, anders staal). */
    public static GunParts complete(GunMaterial material) {
        Map<GunPart, PartState> all = new EnumMap<>(GunPart.class);
        for (GunPart part : GunPart.values()) {
            all.put(part, PartState.fresh(part.accepts(material) ? material : GunMaterial.STEEL));
        }
        return new GunParts(all);
    }

    /** Zoals een echte Glock: een polymeer frame en trekker, de rest van staal. */
    public static GunParts classic() {
        return complete(GunMaterial.STEEL)
                .with(GunPart.FRAME, PartState.fresh(GunMaterial.POLYMER))
                .with(GunPart.TRIGGER, PartState.fresh(GunMaterial.POLYMER));
    }

    public @Nullable PartState get(GunPart part) {
        return parts.get(part);
    }

    public boolean has(GunPart part) {
        return parts.containsKey(part);
    }

    public GunParts with(GunPart part, PartState state) {
        EnumMap<GunPart, PartState> copy = enumMap();
        copy.put(part, state);
        return new GunParts(copy);
    }

    public GunParts without(GunPart part) {
        EnumMap<GunPart, PartState> copy = enumMap();
        copy.remove(part);
        return new GunParts(copy);
    }

    /** Zitten alle zes de onderdelen erin? */
    public boolean complete() {
        return parts.size() == GunPart.values().length;
    }

    /** Wat er nog ontbreekt. */
    public List<GunPart> missing() {
        List<GunPart> missing = new ArrayList<>();
        for (GunPart part : GunPart.values()) {
            if (!parts.containsKey(part)) {
                missing.add(part);
            }
        }
        return missing;
    }

    /** Welke onderdelen kapot zijn (conditie 0). */
    public List<GunPart> broken() {
        List<GunPart> broken = new ArrayList<>();
        for (Map.Entry<GunPart, PartState> entry : parts.entrySet()) {
            if (entry.getValue().broken()) {
                broken.add(entry.getKey());
            }
        }
        return broken;
    }

    /** Het materiaal van dit onderdeel, of staal als het er niet in zit (voor de rekensommen). */
    public GunMaterial materialOf(GunPart part) {
        PartState state = parts.get(part);
        return state == null ? GunMaterial.STEEL : state.material();
    }

    /** Hoeveel extra storingen dit onderdeel geeft (0 als het er niet in zit of niet versleten is). */
    public float wornFactor(GunPart part) {
        PartState state = parts.get(part);
        return state == null ? 0 : state.wornFactor();
    }

    /** De laagste conditie van alle onderdelen (100 als er geen in zitten). */
    public int worstCondition() {
        int worst = PartState.NEW;
        for (PartState state : parts.values()) {
            worst = Math.min(worst, state.condition());
        }
        return worst;
    }
}
