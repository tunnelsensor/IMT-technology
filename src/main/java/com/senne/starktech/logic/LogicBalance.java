package com.senne.starktech.logic;

import com.senne.starktech.edith.Tunable;

/**
 * Alle getallen van de server en zijn redstone-programma's (jouw wens, 7 oktober). Met
 * {@link Tunable} erboven staan ze ook in config/starktech-balance.toml, onder [logic].
 */
public final class LogicBalance {
    private LogicBalance() {}

    /** Zoveel vakjes heeft één serverrack (voor CPU's, RAM-latjes en SSD's). */
    public static final int RACK_SLOTS = 4;

    @Tunable(section = "logic", comment = "Grid power one server rack uses every two seconds (without it, its parts do not count)")
    public static int RACK_UPKEEP = 3;

    @Tunable(section = "logic", comment = "How long a server with one CPU waits before it reacts to a trigger (ticks)")
    public static int REACTION_TICKS = 10;
    @Tunable(section = "logic", comment = "How many ticks faster every extra CPU makes the server react (it never gets below 1 tick)")
    public static int CPU_SPEEDUP_TICKS = 3;
    @Tunable(section = "logic", comment = "How many diagrams can be busy at the same time per RAM stick")
    public static int RUNS_PER_RAM = 2;
    @Tunable(section = "logic", comment = "How many blocks (triggers, ifs, actions) one SSD can hold")
    public static int BLOCKS_PER_SSD = 24;

    /**
     * Hoe snel een server reageert (ticks tussen "er gebeurt iets" en "het diagram begint") met
     * zoveel CPU's: met één CPU REACTION_TICKS (een halve seconde), elke extra CPU een stuk sneller,
     * nooit sneller dan 1 tick (meteen).
     */
    public static int reactionTicks(int cpus) {
        if (cpus <= 0) {
            return Integer.MAX_VALUE;
        }
        return Math.max(1, REACTION_TICKS - (cpus - 1) * CPU_SPEEDUP_TICKS);
    }
}
