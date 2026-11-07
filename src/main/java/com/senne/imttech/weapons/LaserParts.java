package com.senne.imttech.weapons;

import com.mojang.serialization.Codec;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.GunMaterial;
import com.senne.imttech.guns.PartState;
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
 * Wat er in een lasergeweer zit (hoofdstuk 55), per {@link LaserPart} een {@link PartState}: zoals GunParts voor de
 * Glock. De kern (de Nether Star) en de sling staan er ook in, met een "materiaal" dat er niet toe doet (titanium) en
 * altijd 100: ze slijten niet. Een geweer zonder de zes onderdelen en de kern is "incomplete" en schiet niet; de sling
 * is los erbij. Dit zit als data component aan het geweer (LASER_PARTS); het is onveranderlijk: elke verandering geeft
 * een nieuwe LaserParts.
 */
public record LaserParts(Map<LaserPart, PartState> parts) {
    public static final LaserParts EMPTY = new LaserParts(Map.of());

    public static final Codec<LaserParts> CODEC = Codec.unboundedMap(LaserPart.CODEC, PartState.CODEC)
            .xmap(LaserParts::new, LaserParts::parts);

    public static final StreamCodec<ByteBuf, LaserParts> STREAM_CODEC = ByteBufCodecs
            .map(i -> new EnumMap<LaserPart, PartState>(LaserPart.class), LaserPart.STREAM_CODEC, PartState.STREAM_CODEC)
            .map(LaserParts::new, LaserParts::enumMap);

    public LaserParts {
        EnumMap<LaserPart, PartState> copy = new EnumMap<>(LaserPart.class);
        copy.putAll(parts);
        parts = Collections.unmodifiableMap(copy);
    }

    private EnumMap<LaserPart, PartState> enumMap() {
        EnumMap<LaserPart, PartState> copy = new EnumMap<>(LaserPart.class);
        copy.putAll(parts);
        return copy;
    }

    /** De "toestand" van de kern of de sling: geen materiaal dat ertoe doet, altijd nieuw. */
    public static PartState extra() {
        return PartState.fresh(GunMaterial.TITANIUM);
    }

    /** Een compleet nieuw geweer: de zes onderdelen van dit materiaal en een kern erin (geen sling). */
    public static LaserParts complete(GunMaterial material) {
        Map<LaserPart, PartState> all = new EnumMap<>(LaserPart.class);
        GunMaterial shown = LaserPart.MATERIALS.contains(material) ? material : GunMaterial.TITANIUM;
        for (LaserPart part : LaserPart.wearing()) {
            all.put(part, PartState.fresh(shown));
        }
        all.put(LaserPart.CORE, extra());
        return new LaserParts(all);
    }

    public @Nullable PartState get(LaserPart part) {
        return parts.get(part);
    }

    public boolean has(LaserPart part) {
        return parts.containsKey(part);
    }

    public boolean hasCore() {
        return has(LaserPart.CORE);
    }

    public boolean hasSling() {
        return has(LaserPart.SLING);
    }

    public LaserParts with(LaserPart part, PartState state) {
        EnumMap<LaserPart, PartState> copy = enumMap();
        copy.put(part, state);
        return new LaserParts(copy);
    }

    public LaserParts without(LaserPart part) {
        EnumMap<LaserPart, PartState> copy = enumMap();
        copy.remove(part);
        return new LaserParts(copy);
    }

    /** Zitten de zes onderdelen en de kern erin? */
    public boolean complete() {
        return missing().isEmpty();
    }

    /** Wat er nog ontbreekt om te kunnen schieten (de sling telt niet). */
    public List<LaserPart> missing() {
        List<LaserPart> missing = new ArrayList<>();
        for (LaserPart part : LaserPart.values()) {
            if (part.required() && !parts.containsKey(part)) {
                missing.add(part);
            }
        }
        return missing;
    }

    /** Welke onderdelen kapot zijn (conditie 0). */
    public List<LaserPart> broken() {
        List<LaserPart> broken = new ArrayList<>();
        for (Map.Entry<LaserPart, PartState> entry : parts.entrySet()) {
            if (entry.getKey().wears() && entry.getValue().broken()) {
                broken.add(entry.getKey());
            }
        }
        return broken;
    }

    /** Het materiaal van dit onderdeel, of titanium als het er niet in zit (voor de rekensommen). */
    public GunMaterial materialOf(LaserPart part) {
        PartState state = parts.get(part);
        return state == null ? GunMaterial.TITANIUM : state.material();
    }

    /**
     * Hoeveel slechter dit versleten onderdeel zijn eigen cijfer maakt (mijn invulling, want een laser heeft geen
     * storingen): 0 als het heel is, oplopend tot {@code [guns] laserWornPenalty} (0,5 = 50% erger) vlak voor het kapot
     * is. Zie LaserGuns: de emitter doet minder schade, de optics strooien meer, de capacitor kost meer lading, de trigger
     * schiet trager, de receiver geeft meer terugslag en herlaadt trager, het frame trekt trager.
     */
    public float penalty(LaserPart part) {
        PartState state = parts.get(part);
        if (state == null || !part.wears()) {
            return 0;
        }
        float worn = state.wornFactor() / Math.max(0.01f, ImtConfig.Guns.WORN_JAM_FACTOR);
        return Math.min(1, worn) * Math.max(0, ImtConfig.Guns.LASER_WORN_PENALTY);
    }

    /** De laagste conditie van de onderdelen die slijten (100 als er geen in zitten). */
    public int worstCondition() {
        int worst = PartState.NEW;
        for (Map.Entry<LaserPart, PartState> entry : parts.entrySet()) {
            if (entry.getKey().wears()) {
                worst = Math.min(worst, entry.getValue().condition());
            }
        }
        return worst;
    }
}
