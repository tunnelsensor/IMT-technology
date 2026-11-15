package com.senne.imttech.logic.program;

import java.util.Locale;

/**
 * Wat voor programma het is (hoofdstuk 23). Je maakt ze allemaal in de app Logic, in code of in blokjes,
 * maar ze draaien op een andere plek en kennen dus andere woorden:
 * <ul>
 *   <li><b>SERVER</b>: een diagram op je server (de serverracks), zoals altijd. Het hoort wat er in je basis
 *       gebeurt (een kaart, een code, een relais) en schakelt je relais.</li>
 *   <li><b>ROBOT</b>: het programma van een guard robot. Het staat op je server (het telt mee voor je SSD's),
 *       maar draait op de robot zelf: lopen, draaien, scannen, en via de verbinding ook je relais.</li>
 *   <li><b>DRONE</b> (hoofdstuk 25): het programma van een security drone. Hetzelfde als een robotprogramma,
 *       maar hij vliegt, dus hij kan ook omhoog en omlaag ({@code up(3)}, {@code down(1)}).</li>
 *   <li><b>TRAINING_END</b> (hoofdstuk 26): het programmaatje in een Training End-blok. Alleen {@code when trainee_hit:} (de
 *       trainee is geraakt), met {@code trainee}, {@code lives} en {@code hits}; het draait op je server.</li>
 *   <li><b>CAR</b> (hoofdstuk 108): het programma van een RC Car, het autootje dat voor je mijnt en bouwt; in het spel
 *       heet het een "RC program". Sinds hoofdstuk 119 (jouw wens: "zorg dat je de rc car code op de server moet
 *       schrijven, voeg een extra script toe 'RC program', dit neemt evenveel in als een server diagram of robot/drone
 *       program") schrijf je het in de app Logic, zoals een robotprogramma: het staat op je server (en telt mee voor je
 *       SSD's en de 16 diagrammen), en de auto krijgt een kopie (de knop Program op de auto, of de app Robots). Het kent
 *       rijden ({@code forward(3)}, {@code turn_left()}), breken ({@code mine()}, {@code mine_up()}), plaatsen
 *       ({@code place()}), oprapen en lossen ({@code collect()}, {@code unload()}), en {@code car.battery},
 *       {@code block()}... Elke def zonder parameters is een functie op de RC Controller.</li>
 * </ul>
 * Welke woorden in welke soort mogen, staat bij elk blokje ({@link Op#allowedIn}) en elk gegeven
 * ({@code Expr.Variable}); ScriptChecker kijkt het na.
 */
public enum ProgramKind {
    SERVER("Server diagram", "server diagram", "a"),
    ROBOT("Robot program", "robot program", "a"),
    DRONE("Drone program", "drone program", "a"),
    TRAINING_END("Training End program", "Training End program", "a"),
    CAR("RC program", "RC program", "an");

    private final String title;
    private final String noun;
    /** Het lidwoord in een zin: "a robot program", maar "an RC program" (je zegt "ar-see"). */
    private final String article;

    ProgramKind(String title, String noun, String article) {
        this.title = title;
        this.noun = noun;
        this.article = article;
    }

    /** De naam in het spel: "Robot program". */
    public String title() {
        return title;
    }

    /** Zoals in een zin, zonder lidwoord: "robot program". */
    public String noun() {
        return noun;
    }

    /** Zoals in een zin, met het lidwoord: "a robot program", "an RC program". */
    public String aNoun() {
        return article + " " + noun;
    }

    /** Draait het op een robot of een drone (een "bot")? Dan kent het lopen, pinnen en opdrachten. */
    public boolean onBot() {
        return this == ROBOT || this == DRONE;
    }

    /** De soort met deze naam (zoals bewaard); onbekend = SERVER (oude diagrammen). */
    public static ProgramKind byName(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return SERVER;
        }
    }
}
