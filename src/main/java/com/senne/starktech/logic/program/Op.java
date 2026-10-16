package com.senne.starktech.logic.program;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Alle blokjes die je in een diagram kunt gebruiken, met hun naam in de code (Python-achtig, jouw
 * diagram-taal van 7 oktober). Er zijn vijf soorten:
 * <ul>
 *   <li><b>triggers</b> ("when ..."): waar een diagram mee begint;</li>
 *   <li><b>def</b>: je eigen blok, een rijtje acties met een naam (jouw wens: "functions, zodat je
 *       meerdere triggers hetzelfde kunt laten doen"); je gebruikt het met {@code naam()} (CALL);</li>
 *   <li><b>if</b>: een keuze met een "yes"- en een "no"-uitgang;</li>
 *   <li><b>acties</b>: wat de server doet (een relais aan, wachten, een alarm...);</li>
 *   <li><b>namen</b> (variabelen, hoofdstuk 20): {@code count = count + 1}, of helemaal links
 *       {@code count = 0} voor het hele programma.</li>
 * </ul>
 * Elk blokje zegt welke gegevens het nodig heeft: een naam (tekst tussen aanhalingstekens) en/of
 * een getal (seconden, blokken of procent), of (hoofdstuk 23) een rijtje waarden zoals {@code goto(x, y, z)}.
 * Niet elk blokje werkt in elke soort programma: zie {@link #allowedIn}.
 */
public enum Op {
    // ---------------------------------------------------------------- Triggers
    KEYCARD(Kind.TRIGGER, "keycard", Arg.DEVICE, Arg.NONE, "A keycard is held against this reader"),
    CODEPANEL(Kind.TRIGGER, "codepanel", Arg.DEVICE, Arg.NONE, "Someone types a code on this panel"),
    TURNED_ON(Kind.TRIGGER, "turned_on", Arg.DEVICE, Arg.NONE, "This relay (or laser, dome, turret) is switched on"),
    TURNED_OFF(Kind.TRIGGER, "turned_off", Arg.DEVICE, Arg.NONE, "This relay (or laser, dome, turret) is switched off"),
    EVERY(Kind.TRIGGER, "every", Arg.NONE, Arg.NUMBER, "Every so many seconds"),
    /** {@code when started:} (hoofdstuk 23): een robotprogramma begint. Zonder haakjes: er hoort niets bij. */
    STARTED(Kind.TRIGGER, "started", Arg.NONE, Arg.NONE, "The robot is switched on (or gets this program)"),
    /** {@code when ordered("attack"):} (hoofdstuk 23): de server geeft deze robot een opdracht met order(...). */
    ORDERED(Kind.TRIGGER, "ordered", Arg.TEXT, Arg.NONE, "The server gives this robot an order with order(...)"),
    /** {@code when message("intruder"):} (hoofdstuk 23): een robot stuurt de server een bericht met send(...). */
    MESSAGE(Kind.TRIGGER, "message", Arg.TEXT, Arg.NONE, "One of your robots sends this message with send(...)"),

    // ---------------------------------------------------------------- Je eigen blokjes (functies)
    /** Een eigen blok: in de code {@code def open_lab():} met de acties eronder. De tekst is de naam. */
    DEF(Kind.DEF, "def", Arg.NAME, Arg.NONE, "Your own block: steps with a name. Use them in any when with name()"),
    /** Een eigen blok gebruiken: in de code {@code open_lab()}. De tekst is de naam van de def. */
    CALL(Kind.ACTION, "call", Arg.NAME, Arg.NONE, "Do all the steps of one of your defs here"),

    // ---------------------------------------------------------------- Keuze
    IF(Kind.IF, "if", Arg.NONE, Arg.NONE, "Check something: go on at yes or no"),

    // ---------------------------------------------------------------- Acties
    ON(Kind.ACTION, "on", Arg.DEVICE, Arg.NONE, "Switch a relay (or laser, dome, turret) on"),
    OFF(Kind.ACTION, "off", Arg.DEVICE, Arg.NONE, "Switch it off"),
    TOGGLE(Kind.ACTION, "toggle", Arg.DEVICE, Arg.NONE, "Switch it on if it is off, off if it is on"),
    PULSE(Kind.ACTION, "pulse", Arg.DEVICE, Arg.OPTIONAL_NUMBER, "Switch it on for a few seconds"),
    WAIT(Kind.ACTION, "wait", Arg.NONE, Arg.NUMBER, "Wait a few seconds before going on"),
    ACCEPT(Kind.ACTION, "accept", Arg.NONE, Arg.NONE, "Green light and a beep on the reader or panel"),
    DENY(Kind.ACTION, "deny", Arg.NONE, Arg.NONE, "Red light and a buzz on the reader or panel"),
    SAY(Kind.ACTION, "say", Arg.TEXT, Arg.NONE, "E.D.I.T.H. tells the owner something"),
    ALARM(Kind.ACTION, "alarm", Arg.TEXT, Arg.NONE, "Alarm: E.D.I.T.H. warns the owner and the security hunts the player"),
    LOG(Kind.ACTION, "log", Arg.TEXT, Arg.NONE, "Write a line in the security log"),

    // ---------------------------------------------------------------- Een robot (hoofdstuk 23)
    /** {@code forward(3)}: zoveel blokken vooruit, blok voor blok. */
    FORWARD(Kind.ACTION, "forward", Arg.NONE, Arg.NUMBER, "Walk this many blocks forward"),
    /** {@code back(1)}: zoveel blokken achteruit (hij blijft dezelfde kant op kijken). */
    BACK(Kind.ACTION, "back", Arg.NONE, Arg.NUMBER, "Walk this many blocks backwards (still looking the same way)"),
    TURN_LEFT(Kind.ACTION, "turn_left", Arg.NONE, Arg.NONE, "Turn a quarter to the left"),
    TURN_RIGHT(Kind.ACTION, "turn_right", Arg.NONE, Arg.NONE, "Turn a quarter to the right"),
    /** {@code goto(x, y, z)}: naar een plek lopen (alleen met de Pathfinding-module). De waarden staan in expr. */
    GOTO(Kind.ACTION, "goto", Arg.VALUES, Arg.NONE, "Walk to a place: goto(x, y, z). Needs the Pathfinding module"),
    /** {@code charge()} of {@code charge(80)}: naar het dock lopen, laden tot zoveel procent, en terug. */
    CHARGE(Kind.ACTION, "charge", Arg.NONE, Arg.OPTIONAL_NUMBER, "Walk to the dock, charge (to 100%, or charge(80)) and walk back"),
    /** {@code stop()}: zichzelf uitzetten; hij loopt naar zijn dock en blijft daar. */
    STOP(Kind.ACTION, "stop", Arg.NONE, Arg.NONE, "Switch yourself off: walk to the dock and stay there"),
    /** {@code write(laserPin, true)}: een pin iets laten doen. De waarden (pin, waarde) staan in expr. */
    WRITE(Kind.ACTION, "write", Arg.VALUES, Arg.NONE, "Tell the module on a pin what to do: write(laserPin, true)"),
    /** {@code send("intruder")}: een bericht naar de server; daar begint {@code when message("intruder"):}. */
    SEND(Kind.ACTION, "send", Arg.TEXT, Arg.NONE, "Send the server a message: its when message(...) starts"),

    // ---------------------------------------------------------------- De server geeft robots opdrachten (hoofdstuk 23)
    /**
     * {@code order("Guard 1", "attack")} of {@code order("Guard 1", "attack", x, y, z)}: de robot begint zijn
     * {@code when ordered("attack"):}. De waarden staan in expr.
     */
    ORDER(Kind.ACTION, "order", Arg.VALUES, Arg.NONE, "Give a robot an order: its when ordered(...) starts"),
    ACTIVATE(Kind.ACTION, "activate", Arg.ROBOT, Arg.NONE, "Switch a robot on: it walks out of its dock"),
    DEACTIVATE(Kind.ACTION, "deactivate", Arg.ROBOT, Arg.NONE, "Switch a robot off: it walks back to its dock"),

    // ---------------------------------------------------------------- Namen (variabelen, hoofdstuk 20)
    /**
     * {@code count = count + 1} in een when of def: een naam een waarde geven. De tekst is de naam,
     * {@code expr} van het blokje de waarde (als nette code).
     */
    SET(Kind.SET, "set", Arg.VARIABLE, Arg.NONE, "Give a name a value, like count = count + 1"),
    /**
     * {@code count = 0} helemaal links: een naam voor het hele programma (alle whens en defs). Hij
     * houdt zijn waarde tussen de runs, zolang de server draait. Een wortel zonder uitgangen.
     */
    GLOBAL(Kind.SET, "var", Arg.VARIABLE, Arg.NONE, "A name for the whole program, at the left edge, like count = 0"),

    // ---------------------------------------------------------------- Lussen (hoofdstuk 20)
    /** {@code forever:} de stappen erin steeds opnieuw (tot een break). Uitgang YES = "do", NEXT = "after". */
    FOREVER(Kind.LOOP, "forever", Arg.NONE, Arg.NONE, "Do the steps inside again and again (until break)"),
    /** {@code repeat(3):} de stappen erin zo vaak. De tekst is het aantal, als code (mag een naam zijn). */
    REPEAT(Kind.LOOP, "repeat", Arg.NONE, Arg.NONE, "Do the steps inside so many times, like repeat(3):"),
    /** {@code while count < 3:} de stappen erin zolang de voorwaarde klopt. De tekst is de voorwaarde. */
    WHILE(Kind.LOOP, "while", Arg.NONE, Arg.NONE, "Do the steps inside as long as the check is true"),
    /** {@code break}: meteen uit de binnenste lus, en verder met wat na die lus komt. */
    BREAK(Kind.ACTION, "break", Arg.NONE, Arg.NONE, "Stop the loop you are in, and go on after it");

    /** Wat voor blokje. SET = een naam een waarde geven (SET en GLOBAL); LOOP = een lus. */
    public enum Kind { TRIGGER, DEF, IF, ACTION, SET, LOOP }

    /**
     * Wat voor gegevens. DEVICE, TEXT, ROBOT (de naam van een robot) en NAME (van een def) zijn tekst,
     * NUMBER een getal (seconden, blokken of procent: zie {@link #unit}), VARIABLE de naam van een variabele
     * (de waarde staat dan apart, als code). VALUES (hoofdstuk 23) is een rijtje waarden met komma's ertussen,
     * zoals {@code goto(x, y, z)}: dat staat helemaal als code in {@code expr}.
     */
    public enum Arg { NONE, DEVICE, TEXT, ROBOT, NAME, NUMBER, OPTIONAL_NUMBER, VARIABLE, VALUES }

    /** Zo lang mag een tekst (say, alarm, log) of een naam hooguit zijn. */
    public static final int MAX_TEXT = 80;

    private final Kind kind;
    private final String code;
    private final Arg text;
    private final Arg number;
    private final String help;

    Op(Kind kind, String code, Arg text, Arg number, String help) {
        this.kind = kind;
        this.code = code;
        this.text = text;
        this.number = number;
        this.help = help;
    }

    public Kind kind() {
        return kind;
    }

    /** De naam in de code: {@code keycard}, {@code pulse}... */
    public String code() {
        return code;
    }

    /** Wat voor tekst dit blokje nodig heeft (een apparaat, een bericht), of NONE. */
    public Arg textArg() {
        return text;
    }

    /** Of dit blokje een getal heeft: NUMBER (moet), OPTIONAL_NUMBER (mag) of NONE. */
    public Arg numberArg() {
        return number;
    }

    /** Uitleg in het Engels (in het blokjes-palet en bij fouten). */
    public String help() {
        return help;
    }

    public boolean isTrigger() {
        return kind == Kind.TRIGGER;
    }

    /**
     * Begint hier een rij blokjes (een trigger of een def)? Daar komt nooit een draadje in, en een
     * rij die niet aan zo'n begin hangt, ligt los.
     */
    public boolean isRoot() {
        return kind == Kind.TRIGGER || kind == Kind.DEF || this == GLOBAL;
    }

    /** Het blokje met deze naam in de code, of null. */
    public static @Nullable Op byCode(String code) {
        for (Op op : values()) {
            if (op.code.equals(code)) {
                return op;
            }
        }
        return null;
    }

    /** Het blokje met deze naam zoals we hem opslaan (de enum-naam), of null. */
    public static @Nullable Op byName(String name) {
        try {
            return valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Wat het getal van dit blokje telt: "seconds" (wait, pulse, every), "blocks" (forward, back) of "percent" (charge). */
    public String unit() {
        return switch (this) {
            case FORWARD, BACK -> "blocks";
            case CHARGE -> "percent";
            default -> "seconds";
        };
    }

    /** Het kleinste en grootste getal dat dit blokje aanneemt. */
    public double minNumber() {
        return switch (this) {
            case EVERY, FORWARD, BACK, CHARGE -> 1;
            default -> 0.05;
        };
    }

    public double maxNumber() {
        return switch (this) {
            case EVERY -> 3600;
            case WAIT -> 600;
            case FORWARD, BACK -> 64;
            case CHARGE -> 100;
            default -> 60;
        };
    }

    /** Als het getal weggelaten mag worden (pulse, charge), is dit de standaard. */
    public double defaultNumber() {
        return this == CHARGE ? 100 : 1;
    }

    /** Een voorbeeldgetal voor in een lijstje met suggesties: every(10), wait(2), pulse("...", 3), forward(3). */
    public int exampleNumber() {
        return switch (this) {
            case EVERY -> 10;
            case WAIT -> 2;
            case PULSE, FORWARD -> 3;
            case CHARGE -> 80;
            default -> 1;
        };
    }

    /** Een voorbeeld van de tekst van dit blokje: een bericht, een opdracht, een robot. */
    public String textExample() {
        return switch (this) {
            case ALARM -> "Intruder!";
            case LOG -> "Door opened";
            case ORDERED -> "attack";
            case MESSAGE, SEND -> "intruder";
            case ACTIVATE, DEACTIVATE -> "Guard 1";
            default -> "Someone is at the door";
        };
    }

    /**
     * Een trigger zonder gegevens ({@code started}) schrijf je zonder haakjes: {@code when started:}.
     */
    public boolean withoutBrackets() {
        return isTrigger() && text == Arg.NONE && number == Arg.NONE;
    }

    /**
     * Een voorbeeld van hoe je dit blokje in de code schrijft. De {@code |} is waar de cursor komt
     * (tussen de aanhalingstekens): {@code on("|")}, {@code pulse("|", 3)}, {@code wait(2)},
     * {@code accept()}, {@code goto(|)}. De lijstjes van Ctrl+Spatie lezen dit, zodat een nieuw blokje daar
     * vanzelf in komt. Niet voor DEF, CALL en IF: die zien er anders uit.
     */
    public String example() {
        if (withoutBrackets()) {
            return code;
        }
        if (this == ORDER) {
            return "order(\"|\", \"attack\")";
        }
        if (text == Arg.VALUES) {
            return code + "(|)";
        }
        StringBuilder text = new StringBuilder(code).append('(');
        boolean quotes = this.text == Arg.DEVICE || this.text == Arg.TEXT || this.text == Arg.ROBOT;
        if (quotes) {
            text.append("\"|\"");
        }
        if (number == Arg.NUMBER || number == Arg.OPTIONAL_NUMBER) {
            text.append(quotes ? ", " : "").append(exampleNumber());
        }
        return text.append(')').toString();
    }

    // ================================================================ Een rijtje waarden (VALUES, hoofdstuk 23)

    /** De namen van de waarden van een VALUES-blokje, zoals in de uitleg: {"x", "y", "z"}. */
    public String[] valueNames() {
        return switch (this) {
            case GOTO -> new String[] {"x", "y", "z"};
            case WRITE -> new String[] {"pin", "value"};
            case ORDER -> new String[] {"robot", "order", "x", "y", "z"};
            default -> new String[0];
        };
    }

    /** Wat elke waarde moet zijn (ANY = alles mag: write(pin, true) en write(pin, "strangers")). */
    public Expr.Type[] valueTypes() {
        return switch (this) {
            case GOTO -> new Expr.Type[] {Expr.Type.NUMBER, Expr.Type.NUMBER, Expr.Type.NUMBER};
            case WRITE -> new Expr.Type[] {Expr.Type.NUMBER, Expr.Type.ANY};
            case ORDER -> new Expr.Type[] {Expr.Type.TEXT, Expr.Type.TEXT, Expr.Type.NUMBER, Expr.Type.NUMBER, Expr.Type.NUMBER};
            default -> new Expr.Type[0];
        };
    }

    /** Hoeveel waarden er minstens moeten zijn; de rest mag weg (bij order: de plek). */
    public int requiredValues() {
        return this == ORDER ? 2 : valueNames().length;
    }

    /** Hoe je het schrijft, voor de foutmeldingen: {@code goto(10, 64, -20)}. */
    public String valuesExample() {
        return switch (this) {
            case GOTO -> "goto(10, 64, -20)";
            case WRITE -> "write(laserPin, true)";
            case ORDER -> "order(\"Guard 1\", \"attack\") or order(\"Guard 1\", \"attack\", 10, 64, -20)";
            default -> code + "()";
        };
    }

    /**
     * Mag dit blokje in deze soort programma (hoofdstuk 23)? Wat met lezers, panelen, relais-triggers en het
     * besturen van robots te maken heeft, hoort bij de server; lopen, pinnen en berichten sturen bij een robot.
     * De rest werkt overal (een robot schakelt relais via de server).
     */
    public boolean allowedIn(ProgramKind kind) {
        return switch (this) {
            case KEYCARD, CODEPANEL, TURNED_ON, TURNED_OFF, ACCEPT, DENY, MESSAGE, ORDER, ACTIVATE, DEACTIVATE ->
                    kind == ProgramKind.SERVER;
            case STARTED, ORDERED, FORWARD, BACK, TURN_LEFT, TURN_RIGHT, GOTO, CHARGE, STOP, WRITE, SEND ->
                    kind == ProgramKind.ROBOT;
            default -> true;
        };
    }

    /** Werken {@code accept()} en {@code deny()} hier? Alleen na een keycardlezer of codepaneel. */
    public boolean hasSourceDevice() {
        return this == KEYCARD || this == CODEPANEL;
    }
}
