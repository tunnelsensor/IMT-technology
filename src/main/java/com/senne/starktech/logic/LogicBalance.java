package com.senne.starktech.logic;

import com.senne.starktech.edith.Tunable;

/**
 * Alle getallen van de server en zijn redstone-programma's (jouw wens, 7 oktober), en van het
 * datacenter eromheen (8 oktober: GPU's, fans, warmte, het brein). Met {@link Tunable} erboven
 * staan ze ook in config/starktech-balance.toml, onder [logic].
 */
public final class LogicBalance {
    private LogicBalance() {}

    /** Zoveel vakjes heeft één serverrack: twee kolommen van drie (jouw wens, 8 oktober). */
    public static final int RACK_SLOTS = 6;
    /** Zoveel vakjes staan er onder elkaar in één kolom. */
    public static final int RACK_ROWS = 3;

    @Tunable(section = "logic", comment = "Grid power one server rack uses every two seconds (without it, its parts do not count)")
    public static int RACK_UPKEEP = 3;

    @Tunable(section = "logic", comment = "How long a server with Logic power 1 (one CPU) waits before it reacts to a trigger (ticks); twice the power = half the time, never below 1 tick")
    public static int REACTION_TICKS = 10;
    @Tunable(section = "logic", comment = "How much one GPU counts for Logic (a CPU counts 1)")
    public static double GPU_LOGIC_POWER = 0.5;
    @Tunable(section = "logic", comment = "How much one GPU counts for E.D.I.T.H.'s brain (a CPU counts 1)")
    public static double GPU_BRAIN_POWER = 2.0;
    @Tunable(section = "logic", comment = "How long E.D.I.T.H. thinks extra on a datacenter with brain power 1 (one CPU), in ticks; twice the power = half the time")
    public static int BRAIN_DELAY_TICKS = 160;
    @Tunable(section = "logic", comment = "A brain delay this short or shorter (ticks) falls away completely: the datacenter is strong enough")
    public static int BRAIN_DELAY_GONE_TICKS = 10;
    @Tunable(section = "logic", comment = "How many diagrams can be busy at the same time per RAM stick")
    public static int RUNS_PER_RAM = 2;
    @Tunable(section = "logic", comment = "How many blocks (triggers, ifs, actions) one SSD can hold")
    public static int BLOCKS_PER_SSD = 24;

    // ---------------------------------------------------------------- Warmte (8 oktober)

    @Tunable(section = "logic", comment = "Heat (watts) one CPU makes when it does nothing")
    public static double CPU_HEAT = 2.0;
    @Tunable(section = "logic", comment = "Extra heat (watts) one CPU makes when it works as hard as it can")
    public static double CPU_BUSY_HEAT = 2.0;
    @Tunable(section = "logic", comment = "Heat (watts) one GPU makes when it does nothing")
    public static double GPU_HEAT = 3.0;
    @Tunable(section = "logic", comment = "Extra heat (watts) one GPU makes when it works as hard as it can")
    public static double GPU_BUSY_HEAT = 4.0;
    @Tunable(section = "logic", comment = "Heat (watts) one RAM stick or SSD makes")
    public static double MEMORY_HEAT = 0.5;
    @Tunable(section = "logic", comment = "Heat (watts) one fan takes away")
    public static double FAN_COOLING = 6.0;
    @Tunable(section = "logic", comment = "Heat (watts) every server rack takes away by itself (air)")
    public static double RACK_COOLING = 2.0;
    @Tunable(section = "logic", comment = "Temperature (degrees) of a datacenter that makes no more heat than it cools away")
    public static double AMBIENT_TEMPERATURE = 25.0;
    @Tunable(section = "logic", comment = "Degrees warmer for every watt of heat that is not cooled away")
    public static double DEGREES_PER_WATT = 8.0;
    @Tunable(section = "logic", comment = "How fast the temperature follows the heat, every two seconds (0.08 = about a minute to get there)")
    public static double HEAT_RATE = 0.08;
    @Tunable(section = "logic", comment = "From this temperature the datacenter is warm: it works at half speed and E.D.I.T.H. warns you")
    public static double WARM_TEMPERATURE = 70.0;
    @Tunable(section = "logic", comment = "From this temperature the datacenter is hot: its parts wear out and break")
    public static double HOT_TEMPERATURE = 90.0;
    @Tunable(section = "logic", comment = "How much slower a warm datacenter works (2 = half speed)")
    public static double WARM_SLOWDOWN = 2.0;

    /** Onder deze temperatuur is een datacenter weer "koel" (zo waarschuwt E.D.I.T.H. niet steeds opnieuw). */
    public static double coolAgainTemperature() {
        return WARM_TEMPERATURE - 5.0;
    }

    /** Hoeveel één onderdeel telt voor Logic (je redstone-diagrammen): een CPU 1, een GPU minder. */
    public static double logicPower(ServerPartItem.Kind kind) {
        return switch (kind) {
            case CPU -> 1.0;
            case GPU -> GPU_LOGIC_POWER;
            default -> 0.0;
        };
    }

    /** Hoeveel één onderdeel telt voor het brein van E.D.I.T.H.: een CPU 1, een GPU meer. */
    public static double brainPower(ServerPartItem.Kind kind) {
        return switch (kind) {
            case CPU -> 1.0;
            case GPU -> GPU_BRAIN_POWER;
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
        return Math.max(1, (int) Math.round(REACTION_TICKS / logicPower));
    }

    /**
     * Hoeveel ticks E.D.I.T.H. extra nadenkt op een datacenter met zoveel breinkracht: met kracht 1
     * BRAIN_DELAY_TICKS (8 seconden), met twee keer zoveel kracht de helft. Is het
     * BRAIN_DELAY_GONE_TICKS of minder, dan is het datacenter sterk genoeg: 0. Zonder kracht -1
     * (dan kan ze niet denken).
     */
    public static int brainDelayTicks(double brainPower) {
        if (brainPower <= 0) {
            return -1;
        }
        int ticks = (int) Math.round(BRAIN_DELAY_TICKS / brainPower);
        return ticks <= BRAIN_DELAY_GONE_TICKS ? 0 : ticks;
    }
}
