package com.senne.imttech.bot;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * De modules die je op een pin van de printplaat soldeert (hoofdstuk 22). Elk is een item
 * ("scan_module" ...). In je code gebruik je een module via zijn pin: {@code scanPin = pin(1)} en dan
 * {@code read(scanPin)} (hoofdstuk 23 en 24).
 *
 * De volgorde telt: je spel krijgt per robot een bitje per module mee (welke er op zitten), om ze op
 * het model te tekenen. Zet een nieuwe module dus achteraan.
 */
public enum BotModule {
    /** Ziet hij een speler voor zich? */
    SCAN("Scan", "Sees players in front of the robot"),
    /** Hoeveel blokken tot wat er voor hem staat. */
    DISTANCE("Distance", "Measures how far it is to what stands in front"),
    /** Wie de scan ziet, en wie er telt (vreemden, vertrouwden...). */
    FACE_RECOGNITION("Face Recognition", "Tells who the scan sees, and who counts"),
    /** Naar een plek lopen die je hem geeft: goto(x, y, z). */
    PATHFINDING("Pathfinding", "Lets the robot walk to any spot: goto(x, y, z)"),
    /** Een laser op zijn arm. */
    LASER("Laser", "A laser on its arm"),
    /** Een geweer (met Robot Ammo). */
    GUN("Gun", "A gun that fires Robot Ammo"),
    /** Laadt zijn batterij in de zon. */
    SOLAR_PANEL("Solar Panel", "Charges the battery in the sun"),
    /** Door zijn ogen kijken vanaf de desktop. */
    CAMERA("Camera", "Look through its eyes from the desktop"),
    /** Verbrand bij een kortsluiting: doet niets meer. */
    BURNT("Burnt Module", "Burnt out in a short circuit: it does nothing anymore");

    private final String title;
    private final String description;

    BotModule(String title, String description) {
        this.title = title;
        this.description = description;
    }

    /** De naam in het spel (Engels). */
    public String title() {
        return title;
    }

    /** Wat hij doet (Engels, voor de tooltip). */
    public String description() {
        return description;
    }

    /** De naam op een pin en in bestanden: "face_recognition". */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** De module met deze naam (zoals op een pin), of null. */
    /** Een naam voor zijn pin in je code, zoals de Help van de app Logic hem voorstelt: {@code scanPin = pin(1)}. */
    public String pinName() {
        return switch (this) {
            case SCAN -> "scanPin";
            case DISTANCE -> "distancePin";
            case FACE_RECOGNITION -> "facePin";
            case PATHFINDING -> "pathPin";
            case LASER -> "laserPin";
            case GUN -> "gunPin";
            case SOLAR_PANEL -> "solarPin";
            case CAMERA -> "cameraPin";
            case BURNT -> "burntPin";
        };
    }

    public static @Nullable BotModule byId(String id) {
        for (BotModule module : values()) {
            if (module.id().equals(id)) {
                return module;
            }
        }
        return null;
    }
}
