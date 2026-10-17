package com.senne.imttech.logic;

import com.senne.imttech.config.ImtConfig;

/**
 * Rekenhulp voor de server en het datacenter: hoe de getallen uit {@link ImtConfig.Logic} samen
 * uitkomen (hoe snel een server reageert, hoe lang I.M.T nadenkt). De getallen zelf pas je daar aan.
 */
public final class LogicStats {
    private LogicStats() {}

    /** Onder deze temperatuur is een datacenter weer "koel" (zo waarschuwt I.M.T niet steeds opnieuw). */
    public static double coolAgainTemperature() {
        return ImtConfig.Logic.WARM_TEMPERATURE - 5.0;
    }

    /** Hoeveel één onderdeel telt voor Logic (je redstone-diagrammen): een CPU 1, een GPU minder. */
    public static double logicPower(ServerPartItem.Kind kind) {
        return switch (kind) {
            case CPU -> 1.0;
            case GPU -> ImtConfig.Logic.GPU_LOGIC_POWER;
            default -> 0.0;
        };
    }

    /** Hoeveel één onderdeel telt voor het brein van I.M.T: een CPU 1, een GPU meer. */
    public static double brainPower(ServerPartItem.Kind kind) {
        return switch (kind) {
            case CPU -> 1.0;
            case GPU -> ImtConfig.Logic.GPU_BRAIN_POWER;
            default -> 0.0;
        };
    }

    /**
     * Hoe snel een server reageert (ticks tussen "er gebeurt iets" en "het diagram begint") met
     * zoveel Logic-kracht: met kracht 1 (één CPU) REACTION_TICKS (een halve seconde), met twee keer
     * zoveel kracht de helft, nooit sneller dan 1 tick. Eén GPU alleen (kracht ½) duurt dus twee
     * keer zo lang als één CPU.
     */
    public static int reactionTicks(double logicPower) {
        if (logicPower <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, (int) Math.round(ImtConfig.Logic.REACTION_TICKS / logicPower));
    }

    /**
     * Hoeveel ticks I.M.T extra nadenkt op een datacenter met zoveel breinkracht: met kracht 1
     * BRAIN_DELAY_TICKS (8 seconden), met twee keer zoveel kracht de helft. Is het
     * BRAIN_DELAY_GONE_TICKS of minder, dan is het datacenter sterk genoeg: 0. Zonder kracht -1
     * (dan kan ze niet denken).
     */
    public static int brainDelayTicks(double brainPower) {
        if (brainPower <= 0) {
            return -1;
        }
        int ticks = (int) Math.round(ImtConfig.Logic.BRAIN_DELAY_TICKS / brainPower);
        return ticks <= Math.round(ImtConfig.Logic.BRAIN_DELAY_GONE_TICKS) ? 0 : ticks;
    }
}
