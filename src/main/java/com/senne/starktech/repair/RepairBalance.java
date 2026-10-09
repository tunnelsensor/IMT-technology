package com.senne.starktech.repair;

import com.senne.starktech.edith.Tunable;

/**
 * De getallen van de Crusher en de Compactor (hoofdstuk 11, barsten herstellen). Met {@link Tunable}
 * erboven staan ze ook in config/starktech-balance.toml, onder [repair]. (Hoeveel zwakker plamuren een
 * blok maakt, staat bij C4 onder [c4].)
 */
public final class RepairBalance {
    private RepairBalance() {}

    @Tunable(section = "repair", comment = "How long the Crusher and the Compactor take for one item (ticks; 100 = 5 seconds)")
    public static int MACHINE_TICKS = 100;
    @Tunable(section = "repair", comment = "Power the Crusher and the Compactor use per tick while they work (through Stark cables)")
    public static int MACHINE_POWER = 4;
}
