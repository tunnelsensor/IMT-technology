package com.senne.imttech.station;

import com.senne.imttech.config.ImtConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;

/**
 * Hoeveel zon er op een plek valt (gedeeld door het zonnepaneel van het net en, sinds hoofdstuk 24, de Solar
 * Panel-module van een robot). Van 0 tot 1:
 * <ul>
 *   <li>0 als die plek de lucht niet ziet (er zit iets boven), of 's nachts;</li>
 *   <li>hoe hoger de zon, hoe meer: 's middags 1;</li>
 *   <li>bij regen en onweer minder (SOLAR_RAIN en SOLAR_THUNDER in de config).</li>
 * </ul>
 */
public final class SolarMath {
    private SolarMath() {}

    /** De zon op deze plek, van 0 tot 1. */
    public static float sunlight(ServerLevel level, BlockPos pos) {
        if (!level.canSeeSky(pos) || !level.isDay()) {
            return 0;
        }
        // Hoe hoger de zon staat, hoe meer (middags het meest).
        float height = Math.max(0.0f, Mth.cos(level.getSunAngle(1.0f)));
        float weather = level.isThundering() ? ImtConfig.Power.SOLAR_THUNDER
                : level.isRaining() ? ImtConfig.Power.SOLAR_RAIN : 1.0f;
        return height * weather;
    }
}
