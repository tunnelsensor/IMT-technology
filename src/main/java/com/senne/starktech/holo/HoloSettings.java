package com.senne.starktech.holo;

import com.senne.starktech.config.StarkConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * De instelbare getallen van de holotafel, op één plek zodat het scherm, de tafel en de
 * tekenkant het over dezelfde dingen hebben.
 *
 * Alles wordt opgeslagen als een nummer uit deze rijtjes, niet als het getal zelf: zo blijft
 * een opgeslagen tafel kloppen als we hier later een stand bij zetten.
 */
public final class HoloSettings {
    private HoloSettings() {}

    /** Hoe groot het hologram boven de tafel is (1.0 = normaal). */
    public static final float[] SCALES = {0.7f, 1.0f, 1.5f};

    public static final int DEFAULT_RANGE = 1;
    public static final int DEFAULT_SCALE = 1;

    public static int clampRange(int index) {
        return Mth.clamp(index, 0, StarkConfig.Holo.RANGES.length - 1);
    }

    public static int clampScale(int index) {
        return Mth.clamp(index, 0, SCALES.length - 1);
    }

    public static int rangeBlocks(int index) {
        return StarkConfig.Holo.RANGES[clampRange(index)];
    }

    public static float scale(int index) {
        return SCALES[clampScale(index)];
    }

    public static Component rangeLabel(int index) {
        return Component.translatable("screen.starktech.holo.range_value", rangeBlocks(index));
    }

    public static Component scaleLabel(int index) {
        return Component.translatable("screen.starktech.holo.scale." + clampScale(index));
    }
}
