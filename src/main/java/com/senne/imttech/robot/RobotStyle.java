package com.senne.imttech.robot;

import java.util.Locale;

/**
 * De vier stijlen waarin je een guard robot samenstelt (hoofdstuk 21, jouw keuze: zoals in Star Wars).
 * In het spel heten ze zonder "droid", want dat woord is een merk van Lucasfilm; de vormen zijn eigen
 * ontwerpen in die sfeer (zie client/robot/RobotModel en robot_textures() in tools/generate_textures.py).
 *
 * De volgorde telt: de lijsten in de config ({@code [robots]}) hebben één getal per stijl, in deze
 * volgorde.
 */
public enum RobotStyle {
    /** Zoals een commando droid: donkergrijs en gepantserd, met één rond rood oog. */
    COMMANDO("Commando"),
    /** Zoals een trainingsdroid van Kamino: wit en rond, met lichtblauwe lampjes. */
    TRAINER("Trainer"),
    /** Zoals een battle droid: mager en zandkleurig, met een lang smal hoofd. */
    BATTLE("Battle"),
    /** Zoals een super battle droid: donker blauwgrijs, een enorme borst met het hoofd erin, dikke onderarmen. */
    SUPER_BATTLE("Super Battle");

    private final String title;

    RobotStyle(String title) {
        this.title = title;
    }

    /** De naam in het spel (Engels): "Super Battle". */
    public String title() {
        return title;
    }

    /** De naam in bestandsnamen en items: "super_battle". */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** De stijl met dit nummer (zoals bewaard); een vreemd nummer wordt een geldige stijl. */
    public static RobotStyle byIndex(int index) {
        return values()[Math.floorMod(index, values().length)];
    }
}
