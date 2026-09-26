package com.senne.starktech.workbench;

import com.senne.starktech.edith.Tunable;

/** De getallen van de harnas-werkbank en de upgrades (ook in config/starktech-balance.toml). */
public final class WorkbenchBalance {
    private WorkbenchBalance() {}

    @Tunable(section = "workbench", comment = "How long building a suit piece takes (ticks)")
    public static int BUILD_TICKS = 200;
    @Tunable(section = "workbench", comment = "Grid energy per tick while building a suit piece")
    public static int BUILD_ENERGY = 10;
    @Tunable(section = "workbench", comment = "How long installing an upgrade takes (ticks)")
    public static int INSTALL_TICKS = 100;
    @Tunable(section = "workbench", comment = "Grid energy per tick while installing an upgrade")
    public static int INSTALL_ENERGY = 8;

    @Tunable(section = "workbench", comment = "Extra flight speed per thruster upgrade (0.12 = 12%)")
    public static double THRUSTER_SPEED = 0.12;
    @Tunable(section = "workbench", comment = "Less flight energy per thruster upgrade (0.15 = 15%)")
    public static double THRUSTER_SAVING = 0.15;
    @Tunable(section = "workbench", comment = "Extra share of damage the powered suit stops per armor plating")
    public static double PLATING_REDUCTION = 0.05;
    @Tunable(section = "workbench", comment = "Extra repulsor damage per capacitor (0.35 = 35%)")
    public static double CAPACITOR_DAMAGE = 0.35;

    /** Hoeveel van elke upgrade er hooguit in één stuk past. */
    public static final int MAX_THRUSTERS = 3;
    public static final int MAX_PLATING = 2;
    public static final int MAX_CAPACITORS = 2;
}
