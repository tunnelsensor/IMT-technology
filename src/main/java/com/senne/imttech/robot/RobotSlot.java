package com.senne.imttech.robot;

import java.util.Locale;

/**
 * De vier onderdelen van een guard robot (hoofdstuk 21). Elk onderdeel kies je los, in een van de vier
 * stijlen (zie RobotStyle), en elk doet iets anders (zie RobotParts):
 * <ul>
 *   <li>het hoofd: hoe ver en hoe breed hij kijkt;</li>
 *   <li>het lijf: zijn leven, zijn pantser en hoe groot zijn batterij is;</li>
 *   <li>de armen: hoe snel hij schiet;</li>
 *   <li>de benen: hoe snel hij loopt.</li>
 * </ul>
 */
public enum RobotSlot {
    HEAD("Head"),
    BODY("Body"),
    ARMS("Arms"),
    LEGS("Legs");

    private final String title;

    RobotSlot(String title) {
        this.title = title;
    }

    /** De naam in het spel (Engels). */
    public String title() {
        return title;
    }

    /** De naam in bestandsnamen en items: "legs". */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
