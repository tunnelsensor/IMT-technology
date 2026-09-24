package com.senne.starktech.satellite;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;

/**
 * Hoe ver jouw satelliet is opgewaardeerd: één niveau (0 tot 10) per onderdeel, op de
 * volgorde van {@link SatelliteUpgrade}.
 *
 * Hoort bij de speler (net als het arsenaal): je satelliet is van jou, welke bril je ook opzet.
 *
 * @param levels het niveau van elk onderdeel
 */
public record SatelliteUpgrades(List<Integer> levels) {
    /** Een nieuwe satelliet: alleen de precisie-straal werkt. */
    public static final SatelliteUpgrades START = new SatelliteUpgrades(defaults());

    public static final MapCodec<SatelliteUpgrades> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.listOf().fieldOf("levels").forGetter(SatelliteUpgrades::levels)
    ).apply(instance, SatelliteUpgrades::fixed));

    public static final StreamCodec<ByteBuf, SatelliteUpgrades> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()).map(SatelliteUpgrades::fixed, SatelliteUpgrades::levels);

    private static List<Integer> defaults() {
        List<Integer> levels = new ArrayList<>();
        for (SatelliteUpgrade upgrade : SatelliteUpgrade.values()) {
            levels.add(upgrade.startLevel());
        }
        return List.copyOf(levels);
    }

    /**
     * Maakt een geldige lijst van wat er opgeslagen was: te kort (er kwam later een onderdeel
     * bij) wordt aangevuld met de beginwaarden, en alles blijft tussen 0 en 10.
     */
    private static SatelliteUpgrades fixed(List<Integer> stored) {
        List<Integer> levels = new ArrayList<>(defaults());
        for (int i = 0; i < Math.min(stored.size(), levels.size()); i++) {
            levels.set(i, Mth.clamp(stored.get(i), 0, SatelliteUpgrade.MAX_LEVEL));
        }
        return new SatelliteUpgrades(List.copyOf(levels));
    }

    public int level(SatelliteUpgrade upgrade) {
        return levels.get(upgrade.ordinal());
    }

    public SatelliteUpgrades with(SatelliteUpgrade upgrade, int level) {
        List<Integer> changed = new ArrayList<>(levels);
        changed.set(upgrade.ordinal(), Mth.clamp(level, 0, SatelliteUpgrade.MAX_LEVEL));
        return new SatelliteUpgrades(List.copyOf(changed));
    }

    /** Is deze schietstand vrijgespeeld? */
    public boolean unlocked(SatelliteMode mode) {
        return level(SatelliteUpgrade.of(mode)) > 0;
    }

    /** De eerstvolgende vrijgespeelde stand na {@code from} (voor het doorbladeren in het G-menu). */
    public SatelliteMode nextUnlocked(SatelliteMode from) {
        SatelliteMode mode = from;
        for (int i = 0; i < SatelliteMode.values().length; i++) {
            mode = mode.next();
            if (unlocked(mode)) {
                return mode;
            }
        }
        return SatelliteMode.PRECISION;
    }

    // Gemakjes voor de rest van de mod.
    public int precision() {
        return level(SatelliteUpgrade.PRECISION);
    }

    public int bombardment() {
        return level(SatelliteUpgrade.BOMBARDMENT);
    }

    public int sweep() {
        return level(SatelliteUpgrade.SWEEP);
    }

    public int recharge() {
        return level(SatelliteUpgrade.RECHARGE);
    }

    public int power() {
        return level(SatelliteUpgrade.POWER);
    }

    public int targeting() {
        return level(SatelliteUpgrade.TARGETING);
    }
}
