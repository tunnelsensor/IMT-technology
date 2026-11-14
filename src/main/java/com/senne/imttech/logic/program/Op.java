package com.senne.imttech.logic.program;

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
    /** {@code when button("Doorbell"):} (hoofdstuk 38): iemand drukt op een netwerkknop. */
    BUTTON(Kind.TRIGGER, "button", Arg.DEVICE, Arg.NONE, "Someone presses this network button"),
    /** {@code when lever("Night mode"):} (hoofdstuk 38): iemand zet een netwerkhendel om; lever.on zegt hoe hij nu staat. */
    LEVER(Kind.TRIGGER, "lever", Arg.DEVICE, Arg.NONE, "Someone flips this network lever (lever.on: is it on now?)"),
    /** {@code when request("Gate"):} (hoofdstuk 49): een toegangsverzoek bij een Auth Console kreeg zijn antwoord. */
    REQUEST(Kind.TRIGGER, "request", Arg.DEVICE, Arg.NONE, "Someone asked for access at this Auth Console and got an answer "
            + "(request.accepted: yes or no)"),
    TURNED_ON(Kind.TRIGGER, "turned_on", Arg.DEVICE, Arg.NONE, "This relay (or laser, dome, turret) is switched on"),
    TURNED_OFF(Kind.TRIGGER, "turned_off", Arg.DEVICE, Arg.NONE, "This relay (or laser, dome, turret) is switched off"),
    EVERY(Kind.TRIGGER, "every", Arg.NONE, Arg.NUMBER, "Every so many seconds"),
    /** {@code when started:} (hoofdstuk 23): een robotprogramma begint. Zonder haakjes: er hoort niets bij. */
    STARTED(Kind.TRIGGER, "started", Arg.NONE, Arg.NONE, "The robot (or drone) is switched on (or gets this program). An RC car: you press Start"),
    /** {@code when ordered("attack"):} (hoofdstuk 23): de server geeft deze robot een opdracht met order(...). */
    ORDERED(Kind.TRIGGER, "ordered", Arg.TEXT, Arg.NONE, "The server gives this robot (or drone) an order with order(...)"),
    /** Hoofdstuk 60: de zintuigen van een bewaker, voor je eigen robots en drones (zie bot/BotAlert). */
    HEARD(Kind.TRIGGER, "heard", Arg.NONE, Arg.NONE, "The robot (or drone) hears a noise: a shot, an explosion, a block, running, a door or a fall (sound.x/y/z, sound.kind)"),
    SPOTTED(Kind.TRIGGER, "spotted", Arg.NONE, Arg.NONE, "The robot (or drone) watched a player long enough to be sure: its meter is full (target.name, target.x/y/z)"),
    BODY_SEEN(Kind.TRIGGER, "body_seen", Arg.NONE, Arg.NONE, "The robot (or drone) sees a body: a fallen soldier, a handcuffed one or a downed robot (body.x/y/z)"),
    /** {@code when message("intruder"):} (hoofdstuk 23): een robot stuurt de server een bericht met send(...). */
    MESSAGE(Kind.TRIGGER, "message", Arg.TEXT, Arg.NONE, "One of your robots or drones sends this message with send(...)"),
    /** {@code when training_started("Bootcamp"):} (hoofdstuk 26): een training begint (na het aftellen). */
    TRAINING_STARTED(Kind.TRIGGER, "training_started", Arg.TEXT, Arg.NONE, "This training starts (after the countdown)"),
    /** {@code when training_hit("Bootcamp"):}: de trainee is geraakt (lives is nu een minder). */
    TRAINING_HIT(Kind.TRIGGER, "training_hit", Arg.TEXT, Arg.NONE, "The trainee of this training is hit"),
    /** {@code when training_ended("Bootcamp"):}: de training is voorbij (passed zegt of hij geslaagd is). */
    TRAINING_ENDED(Kind.TRIGGER, "training_ended", Arg.TEXT, Arg.NONE, "This training is over (passed or not)"),
    /**
     * {@code when trainee_hit:} (hoofdstuk 26): in een Training End-blok, de trainee is geraakt. (Niet gewoon "hit": dat
     * woord gebruik je misschien al als naam in je programma's.)
     */
    HIT(Kind.TRIGGER, "trainee_hit", Arg.NONE, Arg.NONE, "The trainee is hit (only in a Training End block)"),
    /** {@code when squad_down("Wave 1"):} (hoofdstuk 26): elke robot en drone van die squad ligt neer (of is kapot). */
    SQUAD_DOWN(Kind.TRIGGER, "squad_down", Arg.TEXT, Arg.NONE, "Every robot and drone of this squad is down, out of power or destroyed"),

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
    SAY(Kind.ACTION, "say", Arg.TEXT, Arg.NONE, "I.M.T tells the owner something"),
    ALARM(Kind.ACTION, "alarm", Arg.TEXT, Arg.NONE, "Alarm: I.M.T warns the owner and the security hunts the player"),
    LOG(Kind.ACTION, "log", Arg.TEXT, Arg.NONE, "Write a line in the security log"),

    // ---------------------------------------------------------------- Een robot (hoofdstuk 23) of drone (hoofdstuk 25)
    /** {@code forward(3)}: zoveel blokken vooruit, blok voor blok. */
    FORWARD(Kind.ACTION, "forward", Arg.NONE, Arg.NUMBER, "Walk (a drone: fly, a car: drive) this many blocks forward"),
    /** {@code back(1)}: zoveel blokken achteruit (hij blijft dezelfde kant op kijken). */
    BACK(Kind.ACTION, "back", Arg.NONE, Arg.NUMBER, "Walk (or fly, or drive) this many blocks backwards (still looking the same way)"),
    TURN_LEFT(Kind.ACTION, "turn_left", Arg.NONE, Arg.NONE, "Turn a quarter to the left"),
    TURN_RIGHT(Kind.ACTION, "turn_right", Arg.NONE, Arg.NONE, "Turn a quarter to the right"),
    /** {@code up(3)} (hoofdstuk 25): een drone vliegt zoveel blokken omhoog. */
    UP(Kind.ACTION, "up", Arg.NONE, Arg.NUMBER, "Fly this many blocks up (only a drone can)"),
    /** {@code down(1)}: zoveel blokken omlaag, niet door de grond. */
    DOWN(Kind.ACTION, "down", Arg.NONE, Arg.NUMBER, "Fly this many blocks down (not through the ground)"),
    /** {@code goto(x, y, z)}: naar een plek lopen (alleen met de Pathfinding-module). De waarden staan in expr. */
    GOTO(Kind.ACTION, "goto", Arg.VALUES, Arg.NONE, "Walk (or fly) to a place: goto(x, y, z). Needs the Pathfinding module"),
    /** {@code charge()} of {@code charge(80)}: naar het dock, laden tot zoveel procent, en terug. */
    CHARGE(Kind.ACTION, "charge", Arg.NONE, Arg.OPTIONAL_NUMBER, "Go to the dock, charge (to 100%, or charge(80)) and come back"),
    /** {@code stop()}: zichzelf uitzetten; hij gaat naar zijn dock en blijft daar. */
    STOP(Kind.ACTION, "stop", Arg.NONE, Arg.NONE, "Switch yourself off: go back to the dock and stay there (an RC car: stop its program here)"),
    /** {@code write(laserPin, true)}: een pin aan of uit zetten (of een filter geven). De waarden (pin, waarde) staan in expr. */
    WRITE(Kind.ACTION, "write", Arg.VALUES, Arg.NONE, "Switch the module on a pin on or off: write(laserPin, true)"),
    /**
     * {@code aim(x, y, z)}: de wapens richten op die plek, of {@code aim()}: weer op wie de Scan ziet (jouw antwoord van 19
     * oktober: "richten op wat ik kies"). De waarden staan in expr.
     */
    AIM(Kind.ACTION, "aim", Arg.VALUES, Arg.NONE, "Point its weapons at a place: aim(x, y, z). aim() points them at whom the Scan sees again"),
    /** {@code send("intruder")}: een bericht naar de server; daar begint {@code when message("intruder"):}. */
    SEND(Kind.ACTION, "send", Arg.TEXT, Arg.NONE, "Send the server a message: its when message(...) starts"),

    // ---------------------------------------------------------------- Een RC Car (hoofdstuk 108)
    /**
     * {@code mine()}: het blok vóór de auto breken (jouw wens: "breek voor jou, stap naar voor, breek boven jou").
     * {@code mine(2)}: de twee blokken voor hem, het dichtste eerst (zijn boor reikt hooguit {@link #maxNumber} ver).
     * Wat eruit valt, gaat in zijn kofferbak.
     */
    MINE(Kind.ACTION, "mine", Arg.NONE, Arg.OPTIONAL_NUMBER, "Break the block in front of the car: it goes into its trunk. mine(2): two blocks"),
    /** {@code mine_up()}: het blok boven de auto; {@code mine_up(2)}: de twee blokken boven hem (een gang van 3 hoog). */
    MINE_UP(Kind.ACTION, "mine_up", Arg.NONE, Arg.OPTIONAL_NUMBER, "Break the block above the car. mine_up(2): the two above it (a tunnel 3 high)"),
    /** {@code mine_down()}: het blok onder de auto: hij zakt dan in het gat. */
    MINE_DOWN(Kind.ACTION, "mine_down", Arg.NONE, Arg.OPTIONAL_NUMBER, "Break the block under the car: it drops into the hole"),
    /**
     * {@code place()}: een blok uit zijn kofferbak vóór de auto zetten (het eerste dat hij vindt), of
     * {@code place("cobblestone")}: dat soort blok. De waarde staat in expr.
     */
    PLACE(Kind.ACTION, "place", Arg.VALUES, Arg.NONE, "Put a block from its trunk in front of the car. place(\"dirt\") takes that kind"),
    /** {@code place_up()}: een blok boven de auto zetten (een plafond). */
    PLACE_UP(Kind.ACTION, "place_up", Arg.VALUES, Arg.NONE, "Put a block from its trunk above the car"),
    /** {@code place_down()}: een blok onder zichzelf zetten: de auto komt één blok hoger te staan (zoals jij een pilaar bouwt). */
    PLACE_DOWN(Kind.ACTION, "place_down", Arg.VALUES, Arg.NONE, "Put a block under the car: it ends one block higher (a pillar)"),
    /** {@code collect()}: wat er rond de auto op de grond ligt, gaat in zijn kofferbak. */
    COLLECT(Kind.ACTION, "collect", Arg.NONE, Arg.NONE, "Pick up the items that lie around the car"),
    /** {@code unload()}: de kofferbak leegmaken in de kist vóór de auto (geen kist: op de grond voor hem). */
    UNLOAD(Kind.ACTION, "unload", Arg.NONE, Arg.NONE, "Empty the trunk into the chest in front of the car (no chest: onto the ground)"),

    // ---------------------------------------------------------------- De server geeft robots opdrachten (hoofdstuk 23)
    /**
     * {@code order("Guard 1", "attack")} of {@code order("Guard 1", "attack", x, y, z)}: de robot begint zijn
     * {@code when ordered("attack"):}. De waarden staan in expr.
     */
    ORDER(Kind.ACTION, "order", Arg.VALUES, Arg.NONE, "Give a robot (or drone) an order: its when ordered(...) starts"),
    ACTIVATE(Kind.ACTION, "activate", Arg.ROBOT, Arg.NONE, "Switch a robot, drone or squad on: it leaves its dock"),
    DEACTIVATE(Kind.ACTION, "deactivate", Arg.ROBOT, Arg.NONE, "Switch a robot, drone or squad off: it goes back to its dock"),

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

    // ---------------------------------------------------------------- Lussen (hoofdstuk 20, de for-lus: 16 oktober)
    /**
     * {@code for i in range(3):} (jouw wens van 16 oktober: "zoals een for loop, zodat je je variable i ook kunt
     * gebruiken"): de stappen erin één keer voor elk getal van het bereik, met dat getal in de naam. De tekst is de
     * naam ({@code i}), {@code expr} het bereik als code: {@code 3} (0, 1, 2) of {@code 1, 4} (1, 2, 3). Uitgang YES =
     * "do", NEXT = "after". Hij kwam in de plaats van {@code repeat(3):}.
     */
    FOR(Kind.LOOP, "for", Arg.NONE, Arg.NONE, "Count: do the steps inside once for every number, like for i in range(3):"),
    /**
     * {@code while count < 3:} de stappen erin zolang de voorwaarde klopt. De tekst is de voorwaarde. {@code while true:}
     * gaat door tot een break (dat was vroeger {@code forever:}).
     */
    WHILE(Kind.LOOP, "while", Arg.NONE, Arg.NONE, "Do the steps inside as long as the check is true (while true: until break)"),
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

    /** Wat het getal van dit blokje telt: "seconds" (wait, pulse, every), "blocks" (forward, back, up, down, mine) of "percent" (charge). */
    public String unit() {
        return switch (this) {
            case FORWARD, BACK, UP, DOWN, MINE, MINE_UP, MINE_DOWN -> "blocks";
            case CHARGE -> "percent";
            default -> "seconds";
        };
    }

    /** Het kleinste en grootste getal dat dit blokje aanneemt. */
    public double minNumber() {
        return switch (this) {
            case EVERY, FORWARD, BACK, UP, DOWN, CHARGE, MINE, MINE_UP, MINE_DOWN -> 1;
            default -> 0.05;
        };
    }

    public double maxNumber() {
        return switch (this) {
            case EVERY -> 3600;
            case WAIT -> 600;
            case FORWARD, BACK, UP, DOWN -> 64;
            // Zo ver reikt de boor van een RC Car (hoofdstuk 108): mine_up(2) maakt een gang van 3 hoog.
            case MINE, MINE_UP, MINE_DOWN -> 3;
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
            case UP -> 2;
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
            case TRAINING_STARTED, TRAINING_HIT, TRAINING_ENDED -> "Bootcamp";
            case SQUAD_DOWN -> "Wave 1";
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
        if (this == MINE || this == MINE_UP || this == MINE_DOWN) {
            return code + "()"; // meestal één blok: het getal (mine(2)) mag erbij
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
            case GOTO, AIM -> new String[] {"x", "y", "z"};
            case WRITE -> new String[] {"pin", "value"};
            case ORDER -> new String[] {"robot", "order", "x", "y", "z"};
            case PLACE, PLACE_UP, PLACE_DOWN -> new String[] {"block"};
            default -> new String[0];
        };
    }

    /** Wat elke waarde moet zijn (ANY = alles mag: write(pin, true) en write(pin, "strangers")). */
    public Expr.Type[] valueTypes() {
        return switch (this) {
            case GOTO, AIM -> new Expr.Type[] {Expr.Type.NUMBER, Expr.Type.NUMBER, Expr.Type.NUMBER};
            case WRITE -> new Expr.Type[] {Expr.Type.NUMBER, Expr.Type.ANY};
            case ORDER -> new Expr.Type[] {Expr.Type.TEXT, Expr.Type.TEXT, Expr.Type.NUMBER, Expr.Type.NUMBER, Expr.Type.NUMBER};
            case PLACE, PLACE_UP, PLACE_DOWN -> new Expr.Type[] {Expr.Type.TEXT};
            default -> new Expr.Type[0];
        };
    }

    /** Hoeveel waarden er minstens moeten zijn; de rest mag weg (bij order: de plek; bij aim en place: alles). */
    public int requiredValues() {
        return switch (this) {
            case ORDER -> 2;
            case AIM, PLACE, PLACE_UP, PLACE_DOWN -> 0;
            default -> valueNames().length;
        };
    }

    /** Hoe je het schrijft, voor de foutmeldingen: {@code goto(10, 64, -20)}. */
    public String valuesExample() {
        return switch (this) {
            case GOTO -> "goto(10, 64, -20)";
            case WRITE -> "write(laserPin, true)";
            case AIM -> "aim(10, 64, -20) or aim()";
            case ORDER -> "order(\"Guard 1\", \"attack\") or order(\"Guard 1\", \"attack\", 10, 64, -20)";
            case PLACE, PLACE_UP, PLACE_DOWN -> code + "() or " + code + "(\"cobblestone\")";
            default -> code + "()";
        };
    }

    /**
     * Mag dit blokje in deze soort programma (hoofdstuk 23)? Wat met lezers, panelen, relais-triggers en het
     * besturen van robots te maken heeft, hoort bij de server; lopen, pinnen en berichten sturen bij een robot
     * of een drone; omhoog en omlaag alleen bij een drone (hoofdstuk 25). De rest werkt overal (een robot schakelt
     * relais via de server). Een RC Car (hoofdstuk 108) rijdt, breekt en plaatst; hij heeft geen server, dus geen relais,
     * geen logboek en geen alarm, en geen {@code every}: hij doet alleen wat jij start.
     */
    public boolean allowedIn(ProgramKind kind) {
        return switch (this) {
            case KEYCARD, CODEPANEL, BUTTON, LEVER, REQUEST, TURNED_ON, TURNED_OFF, ACCEPT, DENY, MESSAGE,
                 TRAINING_STARTED, TRAINING_HIT, TRAINING_ENDED, SQUAD_DOWN -> kind == ProgramKind.SERVER;
            // Robots en squads aansturen kan ook een Training End-blok (hoofdstuk 26: deactivate("Wave 1")).
            case ORDER, ACTIVATE, DEACTIVATE -> kind == ProgramKind.SERVER || kind == ProgramKind.TRAINING_END;
            case HIT -> kind == ProgramKind.TRAINING_END;
            // Een Training End-blok doet alleen iets bij een treffer; een RC Car alleen wat jij start.
            case EVERY -> kind != ProgramKind.TRAINING_END && kind != ProgramKind.CAR;
            case STARTED, FORWARD, BACK, TURN_LEFT, TURN_RIGHT, STOP -> kind.onBot() || kind == ProgramKind.CAR;
            case ORDERED, HEARD, SPOTTED, BODY_SEEN, GOTO, CHARGE, WRITE, AIM, SEND -> kind.onBot();
            case UP, DOWN -> kind == ProgramKind.DRONE;
            case MINE, MINE_UP, MINE_DOWN, PLACE, PLACE_UP, PLACE_DOWN, COLLECT, UNLOAD -> kind == ProgramKind.CAR;
            // Wat via je server gaat (relais, het logboek, het alarm): een RC Car heeft geen server.
            case ON, OFF, TOGGLE, PULSE, ALARM, LOG -> kind != ProgramKind.CAR;
            default -> true;
        };
    }

    /** Werken {@code accept()} en {@code deny()} hier? Alleen na een keycardlezer of codepaneel. */
    public boolean hasSourceDevice() {
        return this == KEYCARD || this == CODEPANEL || this == REQUEST;
    }

    /**
     * Begint deze trigger bij een apparaat dat een speler gebruikt: een lezer, een paneel of (hoofdstuk 38) een
     * netwerkknop of -hendel? Dan is de naam die van zo'n apparaat (niet van een relais), en weet je wie het deed
     * ({@code player.name}).
     */
    public boolean fromInput() {
        return this == KEYCARD || this == CODEPANEL || this == BUTTON || this == LEVER || this == REQUEST;
    }
}
