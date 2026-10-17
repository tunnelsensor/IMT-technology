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
 * </ul>
 * Welke woorden in welke soort mogen, staat bij elk blokje ({@link Op#allowedIn}) en elk gegeven
 * ({@code Expr.Variable}); ScriptChecker kijkt het na.
 */
public enum ProgramKind {
    SERVER("Server diagram", "server diagram"),
    ROBOT("Robot program", "robot program"),
    DRONE("Drone program", "drone program"),
    TRAINING_END("Training End program", "Training End program");

    private final String title;
    private final String noun;

    ProgramKind(String title, String noun) {
        this.title = title;
        this.noun = noun;
    }

    /** De naam in het spel: "Robot program". */
    public String title() {
        return title;
    }

    /** Zoals in een zin: "only works in a robot program". */
    public String noun() {
        return noun;
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
