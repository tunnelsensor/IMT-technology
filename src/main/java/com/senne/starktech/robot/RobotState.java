package com.senne.starktech.robot;

/**
 * Hoe het met een guard robot gaat (hoofdstuk 21). Je spel krijgt dit mee, om zijn lichtjes aan of uit te
 * doen, en de schermen laten het zien.
 */
public enum RobotState {
    /** Aan: hij doet zijn werk (zijn programma komt in hoofdstuk 23). */
    ACTIVE("Active"),
    /** Uit: hij loopt naar zijn dock (of staat er al in) en laadt daar. */
    STANDBY("Standby"),
    /** Zijn batterij is leeg: hij staat stil tot hij weer op zijn dock laadt. */
    NO_POWER("No power"),
    /**
     * Neergeschoten met een Ion Blaster (hoofdstuk 24): hij ligt op de grond, zijn programma staat stil, en na een
     * tijdje (ionDownSeconds) staat hij weer op. Kapot is hij niet.
     */
    DOWN("Down");

    private final String title;

    RobotState(String title) {
        this.title = title;
    }

    /** De naam in het spel (Engels). */
    public String title() {
        return title;
    }

    public static RobotState byIndex(int index) {
        return values()[Math.floorMod(index, values().length)];
    }
}
