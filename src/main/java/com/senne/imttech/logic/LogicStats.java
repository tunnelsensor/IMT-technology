package com.senne.imttech.logic;

import com.senne.imttech.config.ImtConfig;
import java.util.Locale;
import net.minecraft.gametest.framework.GameTestServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Rekenhulp voor de server en het datacenter: hoe de getallen uit {@link ImtConfig.Logic} samen
 * uitkomen (hoe snel een server reageert, hoe lang I.M.T nadenkt, hoeveel RAM en SSD er nodig is).
 * De getallen zelf pas je daar aan.
 *
 * Hoofdstuk 64 (jouw wens van 8 november): "voor imt heb je 120 cpu's nodig of 60 gpu's en voor de diagrams 60 gpu's
 * of 30 cpu's", in een rechte lijn: zonder kracht wachten de diagrammen 6 s en I.M.T 12 s, en elke CPU haalt er
 * evenveel af tot het meteen gaat. En "voor elke 10 cpu's of gpu's 4 ramsticks": met minder RAM telt maar een deel van
 * je CPU's en GPU's mee ({@link #fed}). Een SSD is alleen opslag ("zorg ook dat de ssd geen invloed heeft op de speed").
 *
 * <pre>
 *   CPU's   I.M.T     diagrammen
 *     1     11,9 s     5,8 s
 *    10     11,0 s     4,0 s
 *    25      9,5 s     1,0 s
 *    30      9,0 s     meteen
 *   100      2,0 s     meteen
 *   120     meteen     meteen
 * </pre>
 */
public final class LogicStats {
    private LogicStats() {}

    /** Zoveel ticks wachten de diagrammen in de automatische tests hooguit (zie {@link #logicMaxDelayTicks}). */
    public static final int TEST_LOGIC_MAX_DELAY_TICKS = 10;

    /** Voor de tests: de echte schaal aan (true) of de snelle van de tests (false), of null = vanzelf. */
    private static @Nullable Boolean realScaleOverride;

    /** Onder deze temperatuur is een datacenter weer "koel" (zo waarschuwt I.M.T niet steeds opnieuw). */
    public static double coolAgainTemperature() {
        return ImtConfig.Logic.WARM_TEMPERATURE - 5.0;
    }

    // ================================================================ Kracht per onderdeel

    /** Hoeveel één onderdeel telt voor Logic (je redstone-diagrammen): een CPU 1, een GPU minder (½). */
    public static double logicPower(ServerPartItem.Kind kind) {
        return switch (kind) {
            case CPU -> 1.0;
            case GPU -> ImtConfig.Logic.GPU_LOGIC_POWER;
            default -> 0.0;
        };
    }

    /** Hoeveel één onderdeel telt voor het brein van I.M.T: een CPU 1, een GPU meer (2). */
    public static double brainPower(ServerPartItem.Kind kind) {
        return switch (kind) {
            case CPU -> 1.0;
            case GPU -> ImtConfig.Logic.GPU_BRAIN_POWER;
            default -> 0.0;
        };
    }

    // ================================================================ Geheugen: het RAM voedt de CPU's en GPU's, de SSD bewaart

    /** Zoveel MB zit er op één RAM-latje (16 GB = 16 000 MB). */
    public static double ramStickMb() {
        return Math.max(0.001, ImtConfig.Logic.RAM_STICK_GB) * 1000.0;
    }

    /** Zoveel MB past er op één SSD (1 TB = 1 000 000 MB). */
    public static double ssdMb() {
        return Math.max(0.001, ImtConfig.Logic.SSD_GB) * 1000.0;
    }

    /** Zoveel MB RAM hebben zoveel CPU's en GPU's samen nodig om op volle snelheid te draaien. */
    public static double ramNeededMb(int processors) {
        return processors * Math.max(0, ImtConfig.Logic.RAM_GB_PER_TEN) * 100.0; // per 10 in GB = per 1 in MB x 100
    }

    /**
     * Welk deel van de CPU's en GPU's echt meedraait: 1 als er genoeg RAM is (per 10 CPU's of GPU's 64 GB), anders zoveel
     * als het RAM toelaat. Met de helft van het RAM dat nodig is: 0,5. De SSD's tellen hier niet mee: die zijn alleen opslag.
     */
    public static double fed(int processors, int ramSticks) {
        if (processors <= 0) {
            return 1.0;
        }
        double ramNeed = ramNeededMb(processors);
        if (ramNeed <= 0) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, ramSticks * ramStickMb() / ramNeed));
    }

    /** Zoveel diagrammen kunnen tegelijk bezig zijn met zoveel RAM-latjes (een bezig diagram neemt RUN_MB in). */
    public static int maxRuns(int ramSticks) {
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(ramSticks * ramStickMb() / Math.max(0.001, ImtConfig.Logic.RUN_MB)));
    }

    /** Zoveel blokjes passen er op zoveel SSD's (een blokje neemt BLOCK_MB in). */
    public static int blockCapacity(int ssds) {
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(ssds * ssdMb() / Math.max(0.001, ImtConfig.Logic.BLOCK_MB)));
    }

    /** Zoveel MB nemen zoveel blokjes in (op de SSD's, of in een backup). */
    public static double blocksMb(int blocks) {
        return blocks * (double) Math.max(0.001, ImtConfig.Logic.BLOCK_MB);
    }

    // ================================================================ Vertraging: een rechte lijn

    /**
     * Hoe snel een server reageert (ticks tussen "er gebeurt iets" en "het diagram begint") met zoveel Logic-kracht: een
     * rechte lijn van LOGIC_MAX_DELAY_TICKS (bijna geen kracht) naar 1 tick bij LOGIC_INSTANT_POWER (30 CPU's). Zonder
     * kracht: nooit.
     */
    public static int reactionTicks(double logicPower) {
        if (logicPower <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, line(logicMaxDelayTicks(), logicPower, ImtConfig.Logic.LOGIC_INSTANT_POWER));
    }

    /**
     * Hoeveel ticks I.M.T extra nadenkt op een datacenter met zoveel breinkracht: een rechte lijn van
     * BRAIN_MAX_DELAY_TICKS (bijna geen kracht) naar 0 bij BRAIN_INSTANT_POWER (120 CPU's of 60 GPU's). Zonder kracht -1
     * (dan kan ze niet denken).
     */
    public static int brainDelayTicks(double brainPower) {
        if (brainPower <= 0) {
            return -1;
        }
        return Math.max(0, line(ImtConfig.Logic.BRAIN_MAX_DELAY_TICKS, brainPower, ImtConfig.Logic.BRAIN_INSTANT_POWER));
    }

    /** De rechte lijn zelf: {@code max} bij kracht 0, 0 bij {@code instant} en meer (afgerond op hele ticks). */
    public static int line(double max, double power, double instant) {
        double left = 1.0 - power / Math.max(0.1, instant);
        return (int) Math.round(Math.max(0.0, max) * Math.max(0.0, left));
    }

    /**
     * Zo lang wachten de diagrammen hooguit. Tijdens de automatische tests ({@code gradlew runGameTestServer}) maar
     * TEST_LOGIC_MAX_DELAY_TICKS (zoals vroeger een halve seconde met één CPU), zodat de honderd tests met een diagram niet
     * elk 6 seconden op hun server wachten; de tests van het datacenter zetten de echte schaal zelf aan (zoals het netwerk
     * in LanGrid.required).
     */
    public static double logicMaxDelayTicks() {
        Boolean real = realScaleOverride;
        if (real == null) {
            real = !(ServerLifecycleHooks.getCurrentServer() instanceof GameTestServer);
        }
        return real ? ImtConfig.Logic.LOGIC_MAX_DELAY_TICKS : TEST_LOGIC_MAX_DELAY_TICKS;
    }

    /** Alleen voor de tests: de echte schaal (true), die van de tests (false), of met null weer vanzelf. */
    public static void overrideRealScale(@Nullable Boolean real) {
        realScaleOverride = real;
        LogicServer.forgetAll();
    }

    // ================================================================ Tekst

    /** 512 wordt "512 MB", 16 000 "16 GB", 3 000 000 "3 TB", 1 500 000 "1.5 TB" (1000 MB = 1 GB, zoals op een doos). */
    public static String bytes(double mb) {
        if (mb >= 1_000_000) {
            return number(mb / 1_000_000) + " TB";
        }
        if (mb >= 1000) {
            return number(mb / 1000) + " GB";
        }
        return number(mb) + " MB";
    }

    /** 40 ticks wordt "2.0 s", 0 of 1 tick "instant". */
    public static String delay(int ticks) {
        return ticks <= 1 ? "instant" : String.format(Locale.ROOT, "%.1f s", ticks / 20.0);
    }

    /** 9.54 wordt "9.5", 8.0 wordt "8", 76.8 blijft "76.8". */
    private static String number(double value) {
        double rounded = Math.round(value * 10) / 10.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }
}
