package com.senne.imttech.logic.program;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Een voorwaarde in een diagram: wat na {@code if} komt, zoals
 * {@code card.in_group("security")} of {@code code == "4321"} of {@code is_on("Lamp")}.
 *
 * Een voorwaarde is een boompje: {@code a and b} is een And met links a en rechts b. Dat boompje
 * kunnen we:
 * <ul>
 *   <li>lezen uit tekst ({@link #parse}), met een begrijpelijke fout als het niet klopt;</li>
 *   <li>netjes terugschrijven als tekst ({@link #print});</li>
 *   <li>uitrekenen op de server ({@link #test}), met de gegevens van wat er net gebeurde.</li>
 * </ul>
 * Sinds hoofdstuk 20 is het ook een waarde, zoals rechts van {@code count = count + 1}: rekenen
 * ({@link Arith}) en je eigen namen ({@link Ref}).
 */
public sealed interface Expr {
    record Or(Expr left, Expr right) implements Expr {}

    record And(Expr left, Expr right) implements Expr {}

    record Not(Expr inner) implements Expr {}

    record Compare(Expr left, String op, Expr right) implements Expr {}

    record Num(double value) implements Expr {}

    record Str(String value) implements Expr {}

    record Bool(boolean value) implements Expr {}

    /** Een gegeven van wat er gebeurde: {@code card.number}, {@code code}, {@code is_night}... */
    record Var(String name) implements Expr {}

    /**
     * {@code is_on("Lamp")}: staat dat apparaat nu aan? Sinds hoofdstuk 131 mag de naam ook berekend zijn, net als bij
     * {@code on(...)}: {@code is_on(f"gate {i}")}, {@code is_on("gate " + i)}, {@code is_on(door)} met een parameter.
     */
    record IsOn(Expr device) implements Expr {}

    /** {@code -card.number}: het tegengestelde getal. (Een los getal zoals -20 wordt meteen een Num.) */
    record Neg(Expr inner) implements Expr {}

    /**
     * Rekenen (hoofdstuk 20): {@code a + b}, {@code a - b}, {@code a * b}, {@code a / b}. Met
     * {@code +} plak je ook tekst aan elkaar: {@code "Card " + card.number} wordt "Card 1001".
     */
    record Arith(Expr left, char op, Expr right) implements Expr {}

    /**
     * Een naam die je zelf een waarde gaf (een variabele, hoofdstuk 20): {@code count}. {@code type}
     * is wat erin zit; ANY als we dat pas bij het uitvoeren weten.
     */
    record Ref(String name, Type type) implements Expr {}

    /**
     * Een ingebouwde functie die een waarde geeft (hoofdstuk 23): {@code pin(1)} of {@code read(scanPin)}.
     */
    record Call(Function function, List<Expr> args) implements Expr {}

    /**
     * Tekst met namen erin (19 oktober, jouw notitie: {@code $"dit is de {aantal} test"}): {@code $"Round {i} of
     * {count}"}. De stukjes staan op volgorde: gewone tekst is een {@link Str}, wat tussen { en } stond is de
     * uitdrukking zelf. Uitgerekend worden ze aan elkaar geplakt, zoals met + ("Round 2 of 3").
     */
    record Template(List<Expr> parts) implements Expr {}

    /**
     * {@code item("iron_ingot") in chest()} (hoofdstuk 113, jouw wens: "kijken of het item in die chest zit doe je
     * gewoon door if item("hetitem") in chest():"): ligt er minstens één van dat item in de kist vóór de auto, of
     * ({@code in trunk()}) in zijn kofferbak? Links staat de naam van het item (tekst).
     */
    record In(Expr item, Storage where) implements Expr {}

    /** Waar {@link In} kijkt: de kist vóór de auto (op de hoogte van zijn arm), of zijn eigen kofferbak. */
    enum Storage {
        CHEST("chest"), TRUNK("trunk");

        final String code;

        Storage(String code) {
            this.code = code;
        }

        public String code() {
            return code;
        }

        static @Nullable Storage byCode(String code) {
            for (Storage storage : values()) {
                if (storage.code.equals(code)) {
                    return storage;
                }
            }
            return null;
        }
    }

    /**
     * De soorten waarden: een getal, tekst, of waar/onwaar. ANY = "pas bij het uitvoeren bekend" (zoals
     * een variabele als we de code soepel lezen): die mag overal, de echte controle komt elders.
     */
    enum Type { NUMBER, TEXT, BOOL, ANY }

    /**
     * Alle gegevens die je in een voorwaarde kunt gebruiken, met hun soort, en bij welke trigger ze
     * horen (null = altijd).
     */
    enum Variable {
        CARD_NUMBER("card.number", Type.NUMBER, "the number of the card (like 1001)", Op.KEYCARD),
        CARD_VALID("card.valid", Type.BOOL, "true if this network wrote the card and it is not blocked", Op.KEYCARD),
        CARD_NAME("card.name", Type.TEXT, "the name written on the card", Op.KEYCARD),
        CODE("code", Type.TEXT, "the code that was typed, as text (use quotes: code == \"4321\")", Op.CODEPANEL),
        // Na when lever(...) (hoofdstuk 38): hoe de hendel nu staat.
        LEVER_ON("lever.on", Type.BOOL, "true if the lever was just switched on, false if it was switched off", Op.LEVER),
        // De autorisatiecode van de kern (hoofdstuk 49): elke paar minuten een nieuwe (zie Status en Settings).
        AUTH_VALID("auth.valid", Type.BOOL, "true if the code typed is the authorization code of this core right now (or the "
                + "previous one, when Settings allows old codes)", Op.CODEPANEL),
        AUTH_OLD("auth.old", Type.BOOL, "true if the code typed is the previous authorization code (that is always written in the log)", Op.CODEPANEL),
        AUTH_CODE("auth.code", Type.TEXT, "the authorization code of this core right now, as text (it changes every few minutes)", null),
        // Na when request(...) (hoofdstuk 49): het antwoord op het toegangsverzoek bij een Auth Console.
        REQUEST_ACCEPTED("request.accepted", Type.BOOL, "true if the request at the Auth Console was accepted (on the desktop, the laptop "
                + "or the phone); false if it was denied or nobody answered in time", Op.REQUEST),
        // Hoofdstuk 115: niet na een keycard (de lezer weet niet wie de kaart vasthoudt, zie Op.knowsPlayer).
        PLAYER_NAME("player.name", Type.TEXT, "the name of the player (not after a keycard: a reader only knows the card)", null),
        PLAYER_TRUSTED("player.trusted", Type.BOOL, "true for the owner, their team and trusted players (not after a keycard)", null),
        PLAYER_OWNER("player.owner", Type.BOOL, "true if the player owns this network (not after a keycard)", null),
        IS_NIGHT("is_night", Type.BOOL, "true at night", null),
        IS_DAY("is_day", Type.BOOL, "true during the day", null),
        // Een robot (hoofdstuk 23): over zichzelf.
        ROBOT_BATTERY("robot.battery", Type.NUMBER, "how full the battery of the robot is (0-100)", null),
        ROBOT_X("robot.x", Type.NUMBER, "where the robot stands: x", null),
        ROBOT_Y("robot.y", Type.NUMBER, "where the robot stands: y (the height)", null),
        ROBOT_Z("robot.z", Type.NUMBER, "where the robot stands: z", null),
        ROBOT_FACING("robot.facing", Type.TEXT, "where the robot looks: \"north\", \"east\", \"south\" or \"west\"", null),
        ROBOT_NAME("robot.name", Type.TEXT, "the name of the robot", null),
        ROBOT_BLOCKED("robot.blocked", Type.BOOL, "true if something stood in the way at its last step", null),
        ROBOT_CONNECTED("robot.connected", Type.BOOL, "true if the robot can talk to the server (in the area of the core, with power)", null),
        // Een drone (hoofdstuk 25): hetzelfde, en hoe hoog hij vliegt.
        DRONE_BATTERY("drone.battery", Type.NUMBER, "how full the battery of the drone is (0-100)", null),
        DRONE_X("drone.x", Type.NUMBER, "where the drone flies: x", null),
        DRONE_Y("drone.y", Type.NUMBER, "where the drone flies: y (the height)", null),
        DRONE_Z("drone.z", Type.NUMBER, "where the drone flies: z", null),
        DRONE_HEIGHT("drone.height", Type.NUMBER, "how many blocks above the ground the drone flies (0 = it stands on something)", null),
        DRONE_FACING("drone.facing", Type.TEXT, "where the drone looks: \"north\", \"east\", \"south\" or \"west\"", null),
        DRONE_NAME("drone.name", Type.TEXT, "the name of the drone", null),
        DRONE_BLOCKED("drone.blocked", Type.BOOL, "true if something stood in the way at its last move", null),
        DRONE_CONNECTED("drone.connected", Type.BOOL, "true if the drone can talk to the server (in the area of the core, with power)", null),
        // Een RC Car (hoofdstuk 108): over zichzelf, zijn energiecel en zijn kofferbak.
        CAR_BATTERY("car.battery", Type.NUMBER, "how full the energy cell of the car is (0-100; 0 without a cell)", null),
        CAR_X("car.x", Type.NUMBER, "where the car stands: x", null),
        CAR_Y("car.y", Type.NUMBER, "where the car stands: y (the height)", null),
        CAR_Z("car.z", Type.NUMBER, "where the car stands: z", null),
        CAR_FACING("car.facing", Type.TEXT, "where the car looks: \"north\", \"east\", \"south\" or \"west\"", null),
        CAR_NAME("car.name", Type.TEXT, "the name of the car", null),
        CAR_BLOCKED("car.blocked", Type.BOOL, "true if its last step, mine or place did not work (something in the way, a block it cannot break, an empty trunk)", null),
        CAR_FULL("car.full", Type.BOOL, "true if the trunk of the car has no empty slot left", null),
        CAR_ITEMS("car.items", Type.NUMBER, "how many items are in the trunk of the car", null),
        CAR_BLOCKS("car.blocks", Type.NUMBER, "how many blocks the car can still place (the blocks in its trunk)", null),
        // Hoofdstuk 113: de arm van de auto, en de kist vóór hem (op de hoogte van zijn arm). Sinds hoofdstuk 121 tot 5 hoog.
        CAR_ARM("car.arm", Type.NUMBER, "how high the arm of the car is: 0 (the block in front of it) up to 5 blocks higher", null),
        CHEST_SLOTS("chest.slots", Type.NUMBER, "how many slots the chest in front of the car has (0 = there is no chest there)", null),
        CHEST_FREE("chest.free", Type.NUMBER, "how many slots of the chest in front of the car are empty", null),
        CHEST_ITEMS("chest.items", Type.NUMBER, "how many items are in the chest in front of the car", null),
        // Na when ordered(...): de opdracht van de server (een robot of drone), of van de Storage Interface (een RC Car).
        ORDER_NAME("order.name", Type.TEXT, "the order that was given, like \"attack\" (an RC car: \"fetch\" or \"store\")", Op.ORDERED),
        ORDER_X("order.x", Type.NUMBER, "the place that came with the order: x (an RC car: the nearest storage its Storage Interface "
                + "knows with that item)", Op.ORDERED),
        ORDER_Y("order.y", Type.NUMBER, "the place that came with the order: y", Op.ORDERED),
        ORDER_Z("order.z", Type.NUMBER, "the place that came with the order: z", Op.ORDERED),
        // Hoofdstuk 113: de bestelling van een Storage Interface (alleen een RC Car).
        ORDER_ITEM("order.item", Type.TEXT, "the item to fetch or to store, as text: \"iron_ingot\"", Op.ORDERED),
        ORDER_COUNT("order.count", Type.NUMBER, "how many of that item the order is for", Op.ORDERED),
        ORDER_LEFT("order.left", Type.NUMBER, "how many are still to do: when fetching, how many are not in the trunk yet; when "
                + "storing, how many are not put away yet", Op.ORDERED),
        ORDER_KNOWN("order.known", Type.BOOL, "true if the Storage Interface knows a storage with that item (then order.x, order.y "
                + "and order.z say where)", Op.ORDERED),
        // Hoofdstuk 60: de zintuigen van een bewaker (bot/BotAlert). Na when heard: het lawaai...
        SOUND_X("sound.x", Type.NUMBER, "where the noise came from: x", Op.HEARD),
        SOUND_Y("sound.y", Type.NUMBER, "where the noise came from: y", Op.HEARD),
        SOUND_Z("sound.z", Type.NUMBER, "where the noise came from: z", Op.HEARD),
        SOUND_KIND("sound.kind", Type.TEXT, "what it was: \"shot\", \"explosion\", \"block\", \"steps\", \"door\" or \"fall\"", Op.HEARD),
        // ...na when spotted: wie, en waar...
        TARGET_NAME("target.name", Type.TEXT, "the player the robot spotted", Op.SPOTTED),
        TARGET_X("target.x", Type.NUMBER, "where that player stood: x", Op.SPOTTED),
        TARGET_Y("target.y", Type.NUMBER, "where that player stood: y", Op.SPOTTED),
        TARGET_Z("target.z", Type.NUMBER, "where that player stood: z", Op.SPOTTED),
        // ...na when body_seen: waar het lichaam ligt...
        BODY_X("body.x", Type.NUMBER, "where the body lies: x", Op.BODY_SEEN),
        BODY_Y("body.y", Type.NUMBER, "where the body lies: y", Op.BODY_SEEN),
        BODY_Z("body.z", Type.NUMBER, "where the body lies: z", Op.BODY_SEEN),
        // ...en altijd: hoe ver zijn meter is.
        ROBOT_ALERT("robot.alert", Type.NUMBER, "how far the robot is with noticing a player (0-100; at 100 when spotted: starts)", null),
        DRONE_ALERT("drone.alert", Type.NUMBER, "how far the drone is with noticing a player (0-100; at 100 when spotted: starts)", null),
        // Na when message(...) in een serverdiagram: de robot die het bericht stuurde.
        SENDER_NAME("sender.name", Type.TEXT, "the robot that sent the message", Op.MESSAGE),
        SENDER_X("sender.x", Type.NUMBER, "where that robot stood: x", Op.MESSAGE),
        SENDER_Y("sender.y", Type.NUMBER, "where that robot stood: y", Op.MESSAGE),
        SENDER_Z("sender.z", Type.NUMBER, "where that robot stood: z", Op.MESSAGE),
        // Na when training_started/training_hit/training_ended(...) (hoofdstuk 26): de training.
        TRAINEE("trainee", Type.TEXT, "the name of the player in the training", Op.TRAINING_HIT),
        LIVES("lives", Type.NUMBER, "how many lives the trainee has left", Op.TRAINING_HIT),
        HITS("hits", Type.NUMBER, "how often the trainee was hit", Op.TRAINING_HIT),
        PASSED("passed", Type.BOOL, "true if the trainee passed (reached the goal, or survived the time)", Op.TRAINING_ENDED),
        // Na when logged: (hoofdstuk 138) de nieuwe regel in je logboek.
        LOG_TEXT("log.text", Type.TEXT, "the text of the new log line", Op.LOGGED),
        LOG_KIND("log.kind", Type.TEXT, "its kind: login_fail, login, trap, entry, device ...", Op.LOGGED);

        final String name;
        final Type type;
        final String help;
        final @Nullable Op only;

        Variable(String name, Type type, String help, @Nullable Op only) {
            this.name = name;
            this.type = type;
            this.help = help;
            this.only = only;
        }

        public String code() {
            return name;
        }

        public String help() {
            return help;
        }

        /**
         * Mag dit gegeven in deze soort programma (hoofdstuk 23)? Kaarten, codes, spelers en wie een bericht stuurde
         * kent alleen de server; {@code robot.*} alleen een robot, {@code drone.*} alleen een drone (hoofdstuk 25), en
         * {@code order.*} allebei. is_night en is_day kent iedereen.
         */
        public boolean allowedIn(ProgramKind kind) {
            if (name.startsWith("robot.")) {
                return kind == ProgramKind.ROBOT;
            }
            if (name.startsWith("drone.")) {
                return kind == ProgramKind.DRONE;
            }
            if (name.startsWith("car.") || name.startsWith("chest.")) {
                return kind == ProgramKind.CAR; // hoofdstuk 108, en 113: de kist vóór hem
            }
            if (this == ORDER_ITEM || this == ORDER_COUNT || this == ORDER_LEFT || this == ORDER_KNOWN) {
                return kind == ProgramKind.CAR; // hoofdstuk 113: de bestelling van een Storage Interface
            }
            if (name.startsWith("order.")) {
                return kind.onBot() || kind == ProgramKind.CAR;
            }
            if (name.startsWith("sound.") || name.startsWith("target.") || name.startsWith("body.")) {
                return kind.onBot();
            }
            if (this == TRAINEE || this == LIVES || this == HITS) {
                return kind == ProgramKind.SERVER || kind == ProgramKind.TRAINING_END;
            }
            return switch (this) {
                case IS_NIGHT, IS_DAY -> true;
                default -> kind == ProgramKind.SERVER;
            };
        }

        /**
         * Mag dit gegeven na deze trigger? (player.* alleen na een paneel, knop, hendel of verzoek, niet na een keycardlezer:
         * zie Op.knowsPlayer; trainee, lives en hits na elke trainingstrigger; null = in een def: alles.)
         */
        public boolean allowedAfter(@Nullable Op trigger) {
            if (name.startsWith("player.")) {
                return trigger == null || trigger.knowsPlayer();
            }
            if (this == TRAINEE || this == LIVES || this == HITS) {
                return trigger == null || trigger == Op.TRAINING_STARTED || trigger == Op.TRAINING_HIT || trigger == Op.TRAINING_ENDED
                        || trigger == Op.HIT;
            }
            return only == null || trigger == null || only == trigger;
        }

        /** Waar dit gegeven wel werkt, voor de foutmeldingen: "when keycard(...)". Null = overal. */
        public @Nullable String where() {
            if (name.startsWith("player.")) {
                return "when codepanel(...), button(...), lever(...) or request(...)";
            }
            if (this == TRAINEE || this == LIVES || this == HITS) {
                return "when training_started(...), training_hit(...), training_ended(...) or (in a Training End block) when trainee_hit:";
            }
            // Een trigger zonder haakjes (when logged:, when started:) schrijven we ook zo in de melding.
            return only == null ? null : only.withoutBrackets() ? "when " + only.code() + ":" : "when " + only.code() + "(...)";
        }

        /**
         * De foutmelding als dit gegeven na deze trigger niet mag, zonder het woord zelf ervoor: "only works inside when ...".
         * Na een keycard zeggen we waarom player.* daar niet bestaat (hoofdstuk 115, jouw wens): de lezer weet niet wie de
         * kaart vasthoudt, dus een gestolen kaart werkt net zo goed als de kaart van de eigenaar.
         */
        public String notAllowedAfter(@Nullable Op trigger) {
            if (name.startsWith("player.") && trigger == Op.KEYCARD) {
                return "does not work after a keycard: the reader only knows the card, not who holds it. Use card.in_group(\"...\")";
            }
            String where = where();
            return "only works inside " + (where != null ? where : "when keycard(...) or when codepanel(...)");
        }

        static @Nullable Variable byName(String name) {
            for (Variable variable : values()) {
                if (variable.name.equals(name)) {
                    return variable;
                }
            }
            return null;
        }
    }

    /**
     * De ingebouwde functies die een waarde geven (hoofdstuk 23), allebei voor een robot:
     * <ul>
     *   <li>{@code pin(1)} is gewoon het getal 1, maar zo kun je een pin een naam geven, zoals in C++:
     *       {@code scanPin = pin(1)};</li>
     *   <li>{@code read(scanPin)} is wat de module op die pin nu zegt (een scan: true of false; een
     *       afstand: een getal). Wat dat is, weten we pas als het programma draait (ANY).</li>
     * </ul>
     */
    enum Function {
        PIN("pin", Type.NUMBER, "the number of a pin (1-8), so you can give it a name: scanPin = pin(1)", null),
        READ("read", Type.ANY, "what the module on that pin says right now, like read(scanPin)", null),
        /** {@code active("Wave 1")} (hoofdstuk 26): hoeveel robots en drones van die squad (of met die naam) aan staan. */
        ACTIVE("active", Type.NUMBER, "how many robots and drones of this squad (or with this name) are on and standing, like active(\"Wave 1\")", null),
        /**
         * {@code card.in_group("security")} (25 oktober): zit de kaart in die groep, zelf of via een groep die erin zit?
         * Alleen een geldige kaart zit in een groep, dus {@code if card.in_group("security"): accept()} is al veilig.
         */
        CARD_IN_GROUP("card.in_group", Type.BOOL, "true if the card is valid here and in this group (or in a group inside it), like card.in_group(\"security\")", Op.KEYCARD),
        /**
         * {@code block()}, {@code block_up()} en {@code block_down()} (hoofdstuk 108): welk blok er voor, boven of onder
         * een RC Car zit, als tekst: "stone", "diamond_ore", "air", "water". Zo kan zijn programma kijken voor het breekt:
         * {@code if block() == "diamond_ore":}, of doorgaan tot het vrij is: {@code while block() != "air":}.
         */
        BLOCK("block", Type.TEXT, "the block in front of the car, as text: \"stone\", \"diamond_ore\", \"air\"...", null),
        BLOCK_UP("block_up", Type.TEXT, "the block above the car, as text (\"air\" if there is none)", null),
        BLOCK_DOWN("block_down", Type.TEXT, "the block under the car, as text", null),
        /**
         * {@code item("iron_ingot")} (hoofdstuk 113, jouw wens: {@code if item("hetitem") in chest():}): de naam van een
         * item, netjes geschreven ("Iron Ingot" wordt "iron_ingot"). Zo lees je meteen dat het om een item gaat.
         */
        ITEM("item", Type.TEXT, "the name of an item, like item(\"iron_ingot\"): use it with in chest() or in trunk()", null),
        /** {@code chest.count("iron_ingot")}: hoeveel er daarvan in de kist vóór de auto liggen (op de hoogte van zijn arm). */
        CHEST_COUNT("chest.count", Type.NUMBER, "how many of that item are in the chest in front of the car, like chest.count(\"iron_ingot\")", null),
        /** {@code trunk.count("iron_ingot")}: hoeveel er daarvan in zijn kofferbak liggen. */
        TRUNK_COUNT("trunk.count", Type.NUMBER, "how many of that item are in the trunk of the car, like trunk.count(\"iron_ingot\")", null);

        /** Zoveel pinnen heeft de printplaat van een robot (zoals BotBoard.PINS). */
        public static final int PINS = 8;

        final String name;
        final Type type;
        final String help;
        /** Bij welke trigger hij hoort (zoals de card.-gegevens bij when keycard), of null = overal. */
        final @Nullable Op only;

        Function(String name, Type type, String help, @Nullable Op only) {
            this.name = name;
            this.type = type;
            this.help = help;
            this.only = only;
        }

        public String code() {
            return name;
        }

        public String help() {
            return help;
        }

        /**
         * Pinnen heeft alleen een robot of een drone; active() kent de server (die weet wie er staat), kaarten alleen de
         * server; block() alleen een RC Car (hoofdstuk 108).
         */
        public boolean allowedIn(ProgramKind kind) {
            return switch (this) {
                case ACTIVE -> kind == ProgramKind.SERVER || kind == ProgramKind.TRAINING_END;
                case CARD_IN_GROUP -> kind == ProgramKind.SERVER;
                case BLOCK, BLOCK_UP, BLOCK_DOWN, ITEM, CHEST_COUNT, TRUNK_COUNT -> kind == ProgramKind.CAR;
                default -> kind.onBot();
            };
        }

        /** Neemt deze functie de naam van een item tussen de haakjes ({@code item("iron_ingot")}, hoofdstuk 113)? */
        public boolean takesItem() {
            return this == ITEM || this == CHEST_COUNT || this == TRUNK_COUNT;
        }

        /**
         * Is dit woord bezet als naam (je mag er geen def of eigen naam zo noemen)? {@code item} niet: het is een gewoon
         * woord, en een diagram van vóór hoofdstuk 113 met een naam {@code item} moet blijven werken. Als functie herkennen
         * we het aan het haakje erachter.
         */
        public boolean reserved() {
            return this != ITEM;
        }

        /** Neemt deze functie niets tussen de haakjes ({@code block()})? */
        public boolean withoutValues() {
            return this == BLOCK || this == BLOCK_UP || this == BLOCK_DOWN;
        }

        /** Hoe je hem gebruikt, voor de foutmeldingen: {@code scanPin = pin(1)}, {@code if block() == "stone":}. */
        public String example() {
            return switch (this) {
                case PIN -> "scanPin = pin(1)";
                case READ -> "if read(scanPin):";
                case ACTIVE -> "if active(\"Wave 1\") == 0:";
                case CARD_IN_GROUP -> "if card.in_group(\"security\"):";
                case BLOCK, BLOCK_UP, BLOCK_DOWN -> "if " + name + "() == \"stone\":";
                case ITEM -> "if item(\"iron_ingot\") in chest():";
                case CHEST_COUNT, TRUNK_COUNT -> "if " + name + "(\"iron_ingot\") >= 8:";
            };
        }

        /** Mag deze functie na deze trigger? (null = in een def: alles.) */
        public boolean allowedAfter(@Nullable Op trigger) {
            return only == null || trigger == null || only == trigger;
        }

        /** Waar deze functie wel werkt, voor de foutmeldingen: "when keycard(...)". Null = overal. */
        public @Nullable String where() {
            return only == null ? null : "when " + only.code() + "(...)";
        }

        public static @Nullable Function byName(String name) {
            for (Function function : values()) {
                if (function.name.equals(name)) {
                    return function;
                }
            }
            return null;
        }
    }

    // ================================================================ Lezen

    /**
     * Leest een voorwaarde uit tekst, zonder variabelen. {@code trigger} is de trigger waar hij onder
     * hangt (null = nog onbekend, bijvoorbeeld een los blokje): dan weten we welke gegevens er zijn.
     * Gooit een LogicException met uitleg als hij niet klopt.
     */
    static Expr parse(String text, @Nullable Op trigger) {
        return parseCondition(text, trigger, java.util.Map.of());
    }

    /**
     * Streng lezen (voor de controle, zie ScriptChecker): een voorwaarde met de namen die op die plek
     * een waarde hebben ({@code scope}: naam en soort). Een gegeven dat niet bij de trigger hoort
     * ({@code code} onder {@code when keycard}) of een naam zonder waarde geeft een fout.
     */
    public static Expr parseCondition(String text, @Nullable Op trigger, java.util.Map<String, Type> scope) {
        return parseCondition(text, trigger, scope, null);
    }

    /**
     * Zoals hierboven, voor deze soort programma ({@code kind}, hoofdstuk 23; null = elke soort): bij een
     * tikfout raden we dan alleen gegevens die daar bestaan ("Did you mean robot.battery?").
     */
    public static Expr parseCondition(String text, @Nullable Op trigger, java.util.Map<String, Type> scope,
                                      @Nullable ProgramKind kind) {
        return whole(text, new Parser(Lexer.tokens(text), 0, trigger, scope, kind), true);
    }

    /** Streng lezen, zoals {@link #parseCondition}, maar een waarde van elke soort (rechts van een =). */
    public static Expr parseValue(String text, @Nullable Op trigger, java.util.Map<String, Type> scope) {
        return parseValue(text, trigger, scope, null);
    }

    public static Expr parseValue(String text, @Nullable Op trigger, java.util.Map<String, Type> scope,
                                  @Nullable ProgramKind kind) {
        return whole(text, new Parser(Lexer.tokens(text), 0, trigger, scope, kind), false);
    }

    /**
     * Streng lezen: een rijtje waarden met komma's ertussen (hoofdstuk 23), zoals wat tussen de haakjes van
     * {@code goto(x, y + 1, -20)} staat. Eén waarde is een rijtje van één.
     */
    public static List<Expr> parseValues(String text, @Nullable Op trigger, java.util.Map<String, Type> scope,
                                         @Nullable ProgramKind kind) {
        return list(new Parser(Lexer.tokens(text), 0, trigger, scope, kind));
    }

    /** Soepel lezen (zie {@link #parseLenient}): een rijtje waarden met komma's ertussen. */
    public static List<Expr> parseLenientList(String text) {
        return list(new Parser(Lexer.tokens(text), 0, null, null, null));
    }

    private static List<Expr> list(Parser parser) {
        List<Expr> values = new ArrayList<>();
        while (true) {
            values.add(parser.parse(false));
            if (parser.peek().is(",")) {
                parser.next();
                continue;
            }
            if (parser.peek().type() != Lexer.Type.END) {
                throw new LogicException("I did not expect " + parser.peek() + " here");
            }
            return values;
        }
    }

    /**
     * Soepel lezen: een naam die we niet kennen (zonder punt) is een variabele waarvan we de soort nog
     * niet weten, en of een gegeven bij de trigger hoort, controleren we niet. Zo lezen de parser en de
     * server een diagram; de strenge controle doet ScriptChecker. {@code condition}: moet het waar of
     * onwaar zijn (een if)?
     */
    public static Expr parseLenient(String text, boolean condition) {
        return whole(text, new Parser(Lexer.tokens(text), 0, null, null, null), condition);
    }

    private static Expr whole(String text, Parser parser, boolean condition) {
        Expr expr = parser.parse(condition);
        if (parser.peek().type() != Lexer.Type.END) {
            throw new LogicException("I did not expect " + parser.peek() + " here");
        }
        return expr;
    }

    /** Leest een voorwaarde (soepel) uit een stuk van een regel (na "if", tot de ":"). */
    static Expr parse(List<Lexer.Token> tokens, int start, @Nullable Op trigger, int[] end) {
        Parser parser = new Parser(tokens, start, trigger, null, null);
        Expr expr = parser.parse(true);
        end[0] = parser.pos;
        return expr;
    }

    /** Leest een waarde (soepel) uit een stuk van een regel: wat rechts van {@code naam =} staat. */
    static Expr parseValue(List<Lexer.Token> tokens, int start, int[] end) {
        Parser parser = new Parser(tokens, start, null, null, null);
        Expr expr = parser.parse(false);
        end[0] = parser.pos;
        return expr;
    }

    /** Een kleine "recursive descent" parser: elke voorrangsregel een eigen methode. */
    final class Parser {
        private final List<Lexer.Token> tokens;
        private int pos;
        private final @Nullable Op trigger;
        /** De variabelen die hier een waarde hebben (naam en soort), of null = soepel lezen. */
        private final java.util.@Nullable Map<String, Type> scope;
        /** Wat voor programma (hoofdstuk 23), voor de suggesties bij een tikfout; null = elke soort. */
        private final @Nullable ProgramKind kind;

        Parser(List<Lexer.Token> tokens, int start, @Nullable Op trigger, java.util.@Nullable Map<String, Type> scope,
               @Nullable ProgramKind kind) {
            this.tokens = tokens;
            this.pos = start;
            this.trigger = trigger;
            this.scope = scope;
            this.kind = kind;
        }

        Lexer.Token peek() {
            return tokens.get(Math.min(pos, tokens.size() - 1));
        }

        Lexer.Token next() {
            Lexer.Token token = peek();
            pos++;
            return token;
        }

        /** Het geheel: een voorwaarde ({@code condition}: waar of onwaar) of een waarde (van elke soort). */
        Expr parse(boolean condition) {
            if (peek().type() == Lexer.Type.END || peek().is(":")) {
                throw new LogicException(condition ? "The if is missing what to check, like: if card.valid:"
                        : "Something is missing here: a number, a text or a name, like 0");
            }
            Expr expr = or();
            if (condition) {
                requireType(expr, Type.BOOL, "The check must be true or false, like count >= 3");
            }
            return expr;
        }

        private Expr or() {
            Expr left = and();
            while (peek().is("or")) {
                next();
                Expr right = and();
                requireType(left, Type.BOOL, "Both sides of or must be true or false");
                requireType(right, Type.BOOL, "Both sides of or must be true or false");
                left = new Or(left, right);
            }
            return left;
        }

        private Expr and() {
            Expr left = not();
            while (peek().is("and")) {
                next();
                Expr right = not();
                requireType(left, Type.BOOL, "Both sides of and must be true or false");
                requireType(right, Type.BOOL, "Both sides of and must be true or false");
                left = new And(left, right);
            }
            return left;
        }

        private Expr not() {
            if (peek().is("not")) {
                next();
                Expr inner = not();
                requireType(inner, Type.BOOL, "not only works on something that is true or false");
                return new Not(inner);
            }
            return compare();
        }

        private Expr compare() {
            Expr left = sum();
            if (peek().is("=")) {
                throw new LogicException("Use == to compare (one = is not allowed here)");
            }
            Lexer.Token token = peek();
            if (token.type() == Lexer.Type.SYMBOL && List.of("==", "!=", "<", "<=", ">", ">=").contains(token.text())) {
                next();
                Expr right = sum();
                checkCompare(left, token.text(), right);
                return new Compare(left, token.text(), right);
            }
            if (token.is("in")) {
                next();
                return in(left);
            }
            return left;
        }

        /**
         * {@code item("iron_ingot") in chest()} (hoofdstuk 113): na {@code in} komt {@code chest()} of {@code trunk()},
         * en links staat de naam van een item (tekst).
         */
        private Expr in(Expr item) {
            String hint = "After in comes chest() or trunk(), like if item(\"iron_ingot\") in chest():";
            Lexer.Token where = next();
            Storage storage = where.type() == Lexer.Type.NAME ? Storage.byCode(where.text()) : null;
            if (storage == null) {
                throw new LogicException(hint);
            }
            if (!next().is("(") || !next().is(")")) {
                throw new LogicException(storage.code + " needs empty brackets here: in " + storage.code + "()");
            }
            if (typeOf(item) != Type.TEXT && typeOf(item) != Type.ANY) {
                throw new LogicException("Before in comes the name of an item (text), like item(\"iron_ingot\") in " + storage.code + "()");
            }
            return new In(item, storage);
        }

        /** Optellen en aftrekken, van links naar rechts: {@code a + b - c}. Met + plak je ook tekst. */
        private Expr sum() {
            Expr left = product();
            while (peek().is("+") || peek().is("-")) {
                char op = next().text().charAt(0);
                left = arith(left, op, product());
            }
            return left;
        }

        /** Keer en delen gaan voor (zoals op school): {@code 2 + 3 * 4} is 14. */
        private Expr product() {
            Expr left = unary();
            while (peek().is("*") || peek().is("/")) {
                char op = next().text().charAt(0);
                left = arith(left, op, unary());
            }
            return left;
        }

        /** Een minteken ervoor: {@code -20}, {@code -card.number}. */
        private Expr unary() {
            if (peek().is("-")) {
                next();
                Expr inner = unary();
                if (typeOf(inner) != Type.NUMBER && typeOf(inner) != Type.ANY) {
                    throw new LogicException("A - in front only works with a number, like -20");
                }
                return inner instanceof Num num ? new Num(-num.value()) : new Neg(inner);
            }
            return primary();
        }

        /**
         * Kloppen de soorten aan beide kanten? Rekenen gaat met getallen; met + mag ook tekst (dan
         * wordt de andere kant ook tekst: "Level " + 3 is "Level 3").
         */
        private static Expr arith(Expr left, char op, Expr right) {
            Type a = typeOf(left);
            Type b = typeOf(right);
            if (op == '+' && (a == Type.TEXT || b == Type.TEXT || a == Type.ANY || b == Type.ANY)) {
                return new Arith(left, op, right);
            }
            if (a != Type.NUMBER && a != Type.ANY || b != Type.NUMBER && b != Type.ANY) {
                throw new LogicException(switch (op) {
                    case '+' -> "You can only add numbers (or glue text together with +)";
                    case '-' -> "You can only subtract numbers";
                    case '*' -> "You can only multiply numbers";
                    default -> "You can only divide numbers";
                });
            }
            return new Arith(left, op, right);
        }

        private Expr primary() {
            Lexer.Token token = next();
            switch (token.type()) {
                case NUMBER -> {
                    return new Num(token.number());
                }
                case STRING -> {
                    return new Str(token.text());
                }
                case TEMPLATE -> {
                    return template(token.text());
                }
                case NAME -> {
                    return name(token);
                }
                case SYMBOL -> {
                    if (token.is("(")) {
                        Expr inner = or();
                        if (!next().is(")")) {
                            throw new LogicException("A ( is missing its closing )");
                        }
                        return inner;
                    }
                    throw new LogicException("I did not expect " + token + " here");
                }
                default -> throw new LogicException("The check stops too early: something is missing at the end");
            }
        }

        /**
         * {@code $"Round {i} of {count}"}: de tekst tussen de aanhalingstekens, in stukjes. Tussen { en } staat code (een
         * naam of een berekening), die we lezen zoals elke waarde, met dezelfde namen en gegevens als de rest van de
         * regel; {@code {{} en {@code }}} zijn gewone haakjes. Zonder code is het gewoon tekst.
         */
        private Expr template(String raw) {
            List<Expr> parts = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            int i = 0;
            while (i < raw.length()) {
                char c = raw.charAt(i);
                if (c == '\\' && i + 1 < raw.length()) {
                    text.append(raw.charAt(i + 1));
                    i += 2;
                } else if ((c == '{' || c == '}') && i + 1 < raw.length() && raw.charAt(i + 1) == c) {
                    text.append(c);
                    i += 2;
                } else if (c == '}') {
                    throw new LogicException("A } in $\"...\" needs a { before it (write }} for a } in the text)");
                } else if (c == '{') {
                    int end = Lexer.closingBrace(raw, i);
                    String code = raw.substring(i + 1, end);
                    if (code.isBlank()) {
                        throw new LogicException("Something is missing between { and } in $\"...\", like {count}");
                    }
                    Parser inner = new Parser(Lexer.tokens(code), 0, trigger, scope, kind);
                    Expr value = inner.parse(false);
                    if (inner.peek().type() != Lexer.Type.END) {
                        throw new LogicException("I did not expect " + inner.peek() + " in {" + code.strip() + "}");
                    }
                    if (value instanceof Str str) {
                        text.append(str.value()); // {"x"} is gewoon tekst
                    } else {
                        if (!text.isEmpty()) {
                            parts.add(new Str(text.toString()));
                            text.setLength(0);
                        }
                        parts.add(value);
                    }
                    i = end + 1;
                } else {
                    text.append(c);
                    i++;
                }
            }
            if (parts.isEmpty()) {
                return new Str(text.toString());
            }
            if (!text.isEmpty()) {
                parts.add(new Str(text.toString()));
            }
            return new Template(List.copyOf(parts));
        }

        private Expr name(Lexer.Token token) {
            String word = token.text();
            switch (word) {
                case "true", "True" -> {
                    return new Bool(true);
                }
                case "false", "False" -> {
                    return new Bool(false);
                }
                case "is_on" -> {
                    if (!next().is("(")) {
                        throw new LogicException("is_on needs a device between brackets, like is_on(\"Lamp\")");
                    }
                    Lexer.Token first = peek();
                    if (first.is(")")) {
                        throw new LogicException("is_on needs the name of a device between quotes, like is_on(\"Lamp\")");
                    }
                    // Een losse naam die nergens een waarde heeft, is bijna zeker een apparaat zonder aanhalingstekens.
                    // (Soepel gelezen weten we de namen nog niet: dan zegt ScriptChecker het, met deze zelfde zin.)
                    if (scope != null && first.type() == Lexer.Type.NAME && !scope.containsKey(first.text())
                            && Variable.byName(first.text()) == null && Function.byName(first.text()) == null
                            && tokens.get(Math.min(pos + 1, tokens.size() - 1)).is(")")) {
                        throw new LogicException("Put names between quotes: is_on(\"" + first.text() + "\")");
                    }
                    // Hoofdstuk 131: de naam mag ook berekend zijn, zoals bij on(...): is_on(f"gate {i}").
                    Expr device = or();
                    if (!next().is(")")) {
                        throw new LogicException("is_on(...) is missing its closing )");
                    }
                    if (typeOf(device) != Type.TEXT && typeOf(device) != Type.ANY) {
                        throw new LogicException("is_on needs the name of a device (text), like is_on(\"Lamp\") or is_on(f\"gate {i}\")");
                    }
                    return new IsOn(device);
                }
                case "and", "or", "not", "in" -> throw new LogicException("Something is missing before " + word);
                default -> {
                }
            }
            if (word.equals("card.level") || word.equals("card.group") || word.equals("card.groups")) {
                // 25 oktober: een kaart heeft geen niveau meer, maar groepen (en kan in meer dan één groep zitten).
                throw new LogicException(word.equals("card.level")
                        ? "card.level is gone: keycards are in groups now. Use card.in_group(\"security\")"
                        : "A card can be in more than one group: use card.in_group(\"security\")");
            }
            Function function = Function.byName(word);
            // item is alleen een functie met een haakje erachter: zonder is het een van je eigen namen (zie Function.reserved).
            if (function == Function.ITEM && !peek().is("(")) {
                function = null;
            }
            if (function != null) {
                if (scope != null && !function.allowedAfter(trigger)) {
                    throw new LogicException(word + "(...) only works inside " + function.where());
                }
                return call(function);
            }
            Variable variable = Variable.byName(word);
            if (variable == null && !word.contains(".")) {
                return reference(word);
            }
            if (variable == null) {
                // Een tikfout in een gegeven met een punt (robot.batery)? Dan raden we.
                List<String> names = new ArrayList<>();
                for (Variable known : Variable.values()) {
                    if (known.allowedAfter(trigger) && (kind == null || known.allowedIn(kind))) {
                        names.add(known.name);
                    }
                }
                String guess = Checks.closest(word, names);
                throw new LogicException("I don't know \"" + word + "\"" + (guess != null ? ". Did you mean " + guess + "?"
                        : ". You can use: " + known()));
            }
            if (scope != null && !variable.allowedAfter(trigger)) {
                throw new LogicException(word + " " + variable.notAllowedAfter(trigger));
            }
            return new Var(word);
        }

        /**
         * {@code pin(1)} of {@code read(scanPin)} (hoofdstuk 23): de functie, haakjes, en één getal ertussen.
         * Een vast pinnummer moet 1 tot 8 zijn.
         */
        private Expr call(Function function) {
            if (!next().is("(")) {
                throw new LogicException(function.name + " needs brackets, like " + function.name + "(1)");
            }
            List<Expr> args = new ArrayList<>();
            if (!peek().is(")")) {
                while (true) {
                    args.add(or());
                    if (!peek().is(",")) {
                        break;
                    }
                    next();
                    if (peek().is(")")) {
                        throw new LogicException(Checks.emptyAfterComma(function.name)); // read(1, ) (16 november)
                    }
                }
            }
            if (!next().is(")")) {
                throw new LogicException(function.name + "( is missing its closing )");
            }
            if (function == Function.ACTIVE) {
                // active("Wave 1"): één naam (tekst) tussen de haakjes.
                if (args.size() != 1 || typeOf(args.getFirst()) != Type.TEXT && typeOf(args.getFirst()) != Type.ANY) {
                    throw new LogicException("active needs the name of a squad or robot between the brackets, like active(\"Wave 1\")");
                }
                return new Call(function, List.copyOf(args));
            }
            if (function == Function.CARD_IN_GROUP) {
                // card.in_group("security"): één groep (tekst) tussen de haakjes.
                if (args.size() != 1 || typeOf(args.getFirst()) != Type.TEXT && typeOf(args.getFirst()) != Type.ANY) {
                    throw new LogicException("card.in_group needs the name of a keycard group between quotes, like card.in_group(\"security\")");
                }
                return new Call(function, List.copyOf(args));
            }
            if (function.withoutValues()) {
                // block(), block_up(), block_down() (hoofdstuk 108): niets tussen de haakjes.
                if (!args.isEmpty()) {
                    throw new LogicException(function.name + "() takes nothing between the brackets, like " + function.example());
                }
                return new Call(function, List.of());
            }
            if (function.takesItem()) {
                // item("iron_ingot"), chest.count("iron_ingot"), trunk.count("iron_ingot") (hoofdstuk 113): één item (tekst).
                if (args.size() != 1 || typeOf(args.getFirst()) != Type.TEXT && typeOf(args.getFirst()) != Type.ANY) {
                    throw new LogicException(function.name + " needs the name of an item between quotes, like " + function.name
                            + "(\"iron_ingot\")");
                }
                return new Call(function, List.copyOf(args));
            }
            String example = function == Function.PIN ? "pin(1)" : "read(scanPin)";
            if (args.size() != 1) {
                throw new LogicException(function.name + " needs one pin between the brackets, like " + example);
            }
            Expr pin = args.getFirst();
            if (typeOf(pin) != Type.NUMBER && typeOf(pin) != Type.ANY) {
                throw new LogicException("A pin is a number, like " + example);
            }
            if (pin instanceof Num num && (num.value() < 1 || num.value() > Function.PINS || num.value() != Math.rint(num.value()))) {
                throw new LogicException("A robot has " + Function.PINS + " pins: pin(1) to pin(" + Function.PINS + ")");
            }
            return new Call(function, List.copyOf(args));
        }

        /**
         * Een naam zonder punt die geen gegeven van I.M.T is: een variabele. Soepel gelezen weten we
         * zijn soort nog niet (ANY); streng moet hij hier al een waarde hebben.
         */
        private Expr reference(String word) {
            if (scope == null) {
                return new Ref(word, Type.ANY);
            }
            Type type = scope.get(word);
            if (type != null) {
                return new Ref(word, type);
            }
            // Een tikfout in een gegeven of in een van je eigen namen? Dan zeggen we dat.
            List<String> names = new java.util.ArrayList<>(scope.keySet());
            for (Variable variable : Variable.values()) {
                if (variable.allowedAfter(trigger) && (kind == null || variable.allowedIn(kind))) {
                    names.add(variable.name);
                }
            }
            String guess = Checks.closest(word, names);
            if (guess != null) {
                throw new LogicException("I don't know \"" + word + "\". Did you mean " + guess + "?");
            }
            throw new LogicException(word + " has no value yet: give it one first, like " + word + " = 0");
        }

        /** Welke gegevens je hier kunt gebruiken (voor de foutmelding). */
        private String known() {
            StringBuilder list = new StringBuilder();
            for (Variable variable : Variable.values()) {
                if (variable.allowedAfter(trigger) && (kind == null || variable.allowedIn(kind))) {
                    list.append(list.isEmpty() ? "" : ", ").append(variable.name);
                }
            }
            return list.append(", is_on(\"name\")").toString();
        }

        private void checkCompare(Expr left, String op, Expr right) {
            Type a = typeOf(left);
            Type b = typeOf(right);
            if (left instanceof Var var && var.name().equals("code") && b == Type.NUMBER
                    || right instanceof Var var2 && var2.name().equals("code") && a == Type.NUMBER) {
                throw new LogicException("code is text: put the code between quotes, like code == \"4321\"");
            }
            if (a == Type.ANY || b == Type.ANY) {
                return; // pas bij het uitvoeren bekend
            }
            if (a != b) {
                throw new LogicException("You cannot compare " + describe(a) + " with " + describe(b));
            }
            if (!op.equals("==") && !op.equals("!=") && a != Type.NUMBER) {
                throw new LogicException(op + " only works with numbers");
            }
        }

        private static void requireType(Expr expr, Type wanted, String message) {
            Type type = typeOf(expr);
            if (type != wanted && type != Type.ANY) {
                throw new LogicException(message);
            }
        }
    }

    /** Een soort in begrijpelijk Engels: "a number", "text", "true/false". */
    static String describe(Type type) {
        return switch (type) {
            case NUMBER -> "a number";
            case TEXT -> "text";
            case BOOL -> "true/false";
            case ANY -> "a value";
        };
    }

    /**
     * Kan dit al uitgerekend worden voor er iets draait (een when-regel, hoofdstuk 135)? Alleen vaste waarden en namen uit
     * {@code known} (de namen van bovenaan het programma, met hun beginwaarde). Geeft het eerste dat dat tegenhoudt: een
     * naam zonder vaste beginwaarde ({@code i}), een gegeven ({@code card.name}) of een functie ({@code is_on(...)}); of
     * null als het kan.
     */
    static @Nullable String notFixed(Expr expr, java.util.Set<String> known) {
        return switch (expr) {
            case Num n -> null;
            case Str s -> null;
            case Bool b -> null;
            case Ref r -> known.contains(r.name()) ? null : r.name();
            case Var v -> v.name();
            case IsOn i -> "is_on(...)";
            case Call c -> c.function().name + "(...)";
            case In i -> "in " + i.where().code + "()";
            case Neg n -> notFixed(n.inner(), known);
            case Not n -> notFixed(n.inner(), known);
            case Arith a -> firstNotFixed(known, List.of(a.left(), a.right()));
            case Or o -> firstNotFixed(known, List.of(o.left(), o.right()));
            case And a -> firstNotFixed(known, List.of(a.left(), a.right()));
            case Compare c -> firstNotFixed(known, List.of(c.left(), c.right()));
            case Template t -> firstNotFixed(known, t.parts());
        };
    }

    private static @Nullable String firstNotFixed(java.util.Set<String> known, List<Expr> parts) {
        for (Expr part : parts) {
            String problem = notFixed(part, known);
            if (problem != null) {
                return problem;
            }
        }
        return null;
    }

    /**
     * Rekent een vaste waarde uit (zie {@link #notFixed}): een naam krijgt zijn beginwaarde uit {@code values}. Gegevens en
     * functies komen hier niet voor (notFixed liet ze niet door).
     */
    static Object fixedValue(Expr expr, java.util.Map<String, Object> values) {
        return eval(expr, new Facts() {
            @Override
            public Object value(String variable) {
                return false;
            }

            @Override
            public boolean isOn(String device) {
                return false;
            }

            @Override
            public @Nullable Object variable(String name) {
                return values.get(name);
            }
        });
    }

    /** Alle gegevens (card.valid, code, player.name...) die in deze voorwaarde voorkomen. */
    static java.util.Set<String> variables(Expr expr) {
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        collectVariables(expr, names);
        return names;
    }

    private static void collectVariables(Expr expr, java.util.Set<String> names) {
        switch (expr) {
            case Or o -> {
                collectVariables(o.left(), names);
                collectVariables(o.right(), names);
            }
            case And a -> {
                collectVariables(a.left(), names);
                collectVariables(a.right(), names);
            }
            case Not n -> collectVariables(n.inner(), names);
            case Compare c -> {
                collectVariables(c.left(), names);
                collectVariables(c.right(), names);
            }
            case Var v -> names.add(v.name());
            case Neg n -> collectVariables(n.inner(), names);
            case Arith a -> {
                collectVariables(a.left(), names);
                collectVariables(a.right(), names);
            }
            case Call c -> c.args().forEach(arg -> collectVariables(arg, names));
            case Template t -> t.parts().forEach(part -> collectVariables(part, names));
            case In i -> collectVariables(i.item(), names);
            case IsOn i -> collectVariables(i.device(), names);
            default -> { }
        }
    }

    /** De soort van deze (deel)voorwaarde. */
    static Type typeOf(Expr expr) {
        return switch (expr) {
            case Num n -> Type.NUMBER;
            case Str s -> Type.TEXT;
            case Template t -> Type.TEXT;
            case Var v -> Variable.byName(v.name()) == null ? Type.BOOL : Variable.byName(v.name()).type;
            case Neg n -> Type.NUMBER;
            case Arith a -> arithType(a);
            case Ref r -> r.type();
            case Call c -> c.function().type;
            default -> Type.BOOL;
        };
    }

    /** + met tekst is tekst; + met iets onbekends is onbekend; de rest is een getal. */
    private static Type arithType(Arith a) {
        Type left = typeOf(a.left());
        Type right = typeOf(a.right());
        if (a.op() == '+' && (left == Type.TEXT || right == Type.TEXT)) {
            return Type.TEXT;
        }
        return a.op() == '+' && (left == Type.ANY || right == Type.ANY) ? Type.ANY : Type.NUMBER;
    }

    // ================================================================ Terugschrijven

    /** De voorwaarde als nette tekst, met alleen de haakjes die nodig zijn. */
    static String print(Expr expr) {
        return print(expr, 0);
    }

    /** Hoe sterk iets bindt: or 1, and 2, not 3, vergelijken 4, + en - 5, * en / 6, een minteken 7, de rest 8. */
    private static int strength(Expr expr) {
        return switch (expr) {
            case Or o -> 1;
            case And a -> 2;
            case Not n -> 3;
            case Compare c -> 4;
            case In i -> 4;
            case Arith a -> a.op() == '+' || a.op() == '-' ? 5 : 6;
            case Neg n -> 7;
            default -> 8;
        };
    }

    private static String print(Expr expr, int needed) {
        String text = switch (expr) {
            case Or o -> print(o.left(), 1) + " or " + print(o.right(), 2);
            case And a -> print(a.left(), 2) + " and " + print(a.right(), 3);
            case Not n -> "not " + print(n.inner(), 3);
            case Compare c -> print(c.left(), 5) + " " + c.op() + " " + print(c.right(), 5);
            case In i -> print(i.item(), 5) + " in " + i.where().code + "()";
            // Van links naar rechts: rechts haakjes als hij even sterk bindt (a - (b - c)).
            case Arith a -> print(a.left(), strength(a)) + " " + a.op() + " " + print(a.right(), strength(a) + 1);
            case Neg n -> "-" + print(n.inner(), 7);
            case Num n -> number(n.value());
            case Str s -> quote(s.value());
            case Bool b -> b.value() ? "true" : "false";
            case Var v -> v.name();
            case Ref r -> r.name();
            case IsOn i -> "is_on(" + print(i.device()) + ")";
            case Call c -> c.function().name + "(" + printList(c.args()) + ")";
            case Template t -> template(t);
        };
        return strength(expr) < needed ? "(" + text + ")" : text;
    }

    /** {@code $"Round {i} of {count}"}: de tekst met { en } eromheen waar code stond (en {{ en }} voor gewone haakjes). */
    private static String template(Template template) {
        StringBuilder text = new StringBuilder("$\"");
        for (Expr part : template.parts()) {
            if (part instanceof Str str) {
                text.append(str.value().replace("\\", "\\\\").replace("\"", "\\\"").replace("{", "{{").replace("}", "}}"));
            } else {
                text.append('{').append(print(part)).append('}');
            }
        }
        return text.append('"').toString();
    }

    /** Een rijtje waarden met komma's ertussen: {@code 10, y + 1, -20}. */
    public static String printList(List<Expr> values) {
        StringBuilder text = new StringBuilder();
        for (Expr value : values) {
            text.append(text.isEmpty() ? "" : ", ").append(print(value));
        }
        return text.toString();
    }

    /** Een getal zonder overbodige nullen: 3, 0.5, 1.25. */
    static String number(double value) {
        if (value == Math.rint(value) && Math.abs(value) < 1e9) {
            return String.valueOf((long) value);
        }
        String text = String.format(Locale.ROOT, "%.2f", value);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    /** Tekst tussen aanhalingstekens (met \" en \\ waar nodig). */
    static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ================================================================ Uitrekenen

    /** Waar de server de gegevens vandaan haalt als hij een voorwaarde uitrekent. */
    interface Facts {
        /** De waarde van een gegeven (Double, String of Boolean). */
        Object value(String variable);

        /** Staat het apparaat met deze naam aan? */
        boolean isOn(String device);

        /** De waarde van een van je eigen namen (variabelen), of null als hij (nog) geen waarde heeft. */
        default @Nullable Object variable(String name) {
            return null;
        }

        /** Wat {@code read(pin)} nu geeft (hoofdstuk 23: dat weet de robot). Standaard: niets (false). */
        default Object read(int pin) {
            return false;
        }

        /** Wat {@code active("Wave 1")} nu geeft (hoofdstuk 26: dat weet de server). Standaard: 0. */
        default Object active(String name) {
            return 0.0;
        }

        /** Zit de kaart van deze run in die groep ({@code card.in_group("security")}, 25 oktober)? Standaard: nee. */
        default boolean inGroup(String group) {
            return false;
        }

        /**
         * Welk blok er bij een RC Car zit (hoofdstuk 108): {@code direction} 0 = voor hem ({@code block()}), 1 = boven hem
         * ({@code block_up()}), -1 = onder hem ({@code block_down()}). Dat weet de auto; standaard: lucht.
         */
        default Object block(int direction) {
            return "air";
        }

        /**
         * Hoeveel er van dit item liggen (hoofdstuk 113): in de kist vóór een RC Car (op de hoogte van zijn arm), of
         * ({@code trunk}) in zijn kofferbak. {@code item} is de naam zoals {@link Expr#itemName} hem schrijft. Dat weet de
         * auto; standaard: 0.
         */
        default int count(boolean trunk, String item) {
            return 0;
        }
    }

    /**
     * De naam van een item zoals de taal hem schrijft (hoofdstuk 113): kleine letters, een _ waar een spatie stond, en
     * zonder "minecraft:" ervoor. "Iron Ingot" wordt "iron_ingot"; een item van een mod houdt zijn voorvoegsel
     * ("imttech:polymer"). Zo vergelijk je namen zonder op hoofdletters te letten.
     */
    public static String itemName(String name) {
        String clean = name.strip().toLowerCase(Locale.ROOT).replace(' ', '_');
        return clean.startsWith("minecraft:") ? clean.substring("minecraft:".length()) : clean;
    }

    /** Staat er ergens in deze uitdrukking {@code ... in chest()} of {@code ... in trunk()} (hoofdstuk 113)? */
    static boolean usesStorage(Expr expr) {
        return switch (expr) {
            case In i -> true;
            case Or o -> usesStorage(o.left()) || usesStorage(o.right());
            case And a -> usesStorage(a.left()) || usesStorage(a.right());
            case Not n -> usesStorage(n.inner());
            case Compare c -> usesStorage(c.left()) || usesStorage(c.right());
            case Neg n -> usesStorage(n.inner());
            case Arith a -> usesStorage(a.left()) || usesStorage(a.right());
            case Call c -> c.args().stream().anyMatch(Expr::usesStorage);
            case Template t -> t.parts().stream().anyMatch(Expr::usesStorage);
            case IsOn i -> usesStorage(i.device());
            default -> false;
        };
    }

    /** De ingebouwde gegevens ({@code card.number}, {@code robot.battery}...) die in deze uitdrukking staan. */
    static List<String> builtins(Expr expr) {
        List<String> names = new ArrayList<>();
        collect(expr, names);
        return names;
    }

    private static void collect(Expr expr, List<String> names) {
        switch (expr) {
            case Or o -> {
                collect(o.left(), names);
                collect(o.right(), names);
            }
            case And a -> {
                collect(a.left(), names);
                collect(a.right(), names);
            }
            case Not n -> collect(n.inner(), names);
            case Compare c -> {
                collect(c.left(), names);
                collect(c.right(), names);
            }
            case Neg n -> collect(n.inner(), names);
            case Arith a -> {
                collect(a.left(), names);
                collect(a.right(), names);
            }
            case Var v -> names.add(v.name());
            case Call c -> c.args().forEach(arg -> collect(arg, names));
            case Template t -> t.parts().forEach(part -> collect(part, names));
            case In i -> collect(i.item(), names);
            case IsOn i -> collect(i.device(), names);
            default -> { }
        }
    }

    /** De ingebouwde functies ({@code pin}, {@code read}) die in deze uitdrukking staan. */
    static List<Function> functions(Expr expr) {
        List<Function> found = new ArrayList<>();
        collectFunctions(expr, found);
        return found;
    }

    private static void collectFunctions(Expr expr, List<Function> found) {
        switch (expr) {
            case Or o -> {
                collectFunctions(o.left(), found);
                collectFunctions(o.right(), found);
            }
            case And a -> {
                collectFunctions(a.left(), found);
                collectFunctions(a.right(), found);
            }
            case Not n -> collectFunctions(n.inner(), found);
            case Compare c -> {
                collectFunctions(c.left(), found);
                collectFunctions(c.right(), found);
            }
            case Neg n -> collectFunctions(n.inner(), found);
            case Arith a -> {
                collectFunctions(a.left(), found);
                collectFunctions(a.right(), found);
            }
            case Call c -> {
                found.add(c.function());
                c.args().forEach(arg -> collectFunctions(arg, found));
            }
            case Template t -> t.parts().forEach(part -> collectFunctions(part, found));
            case In i -> collectFunctions(i.item(), found);
            case IsOn i -> collectFunctions(i.device(), found);
            default -> { }
        }
    }

    /** Is deze voorwaarde nu waar? */
    static boolean test(Expr expr, Facts facts) {
        return Boolean.TRUE.equals(eval(expr, facts));
    }

    /** Wat deze uitdrukking nu waard is: een Double, een String of een Boolean. */
    static Object value(Expr expr, Facts facts) {
        return eval(expr, facts);
    }

    private static Object eval(Expr expr, Facts facts) {
        return switch (expr) {
            case Or o -> test(o.left(), facts) || test(o.right(), facts);
            case And a -> test(a.left(), facts) && test(a.right(), facts);
            case Not n -> !test(n.inner(), facts);
            case Compare c -> compare(eval(c.left(), facts), c.op(), eval(c.right(), facts));
            case Num n -> n.value();
            case Str s -> s.value();
            case Bool b -> b.value();
            case Var v -> facts.value(v.name());
            case IsOn i -> facts.isOn(text(eval(i.device(), facts)));
            case Neg n -> -toNumber(eval(n.inner(), facts));
            case Arith a -> arith(eval(a.left(), facts), a.op(), eval(a.right(), facts));
            case Ref r -> {
                Object value = facts.variable(r.name());
                // Nog geen waarde (kan alleen bij vreemde blokjes): dan de "lege" waarde van zijn soort.
                yield value != null ? value : r.type() == Type.TEXT ? "" : r.type() == Type.BOOL ? (Object) false : (Object) 0.0;
            }
            case Call c -> {
                if (c.function() == Function.ACTIVE) {
                    yield facts.active(text(eval(c.args().getFirst(), facts)));
                }
                if (c.function() == Function.CARD_IN_GROUP) {
                    yield facts.inGroup(text(eval(c.args().getFirst(), facts)));
                }
                if (c.function().withoutValues()) {
                    // block(), block_up(), block_down() (hoofdstuk 108): dat vraagt hij aan de auto.
                    yield facts.block(c.function() == Function.BLOCK ? 0 : c.function() == Function.BLOCK_UP ? 1 : -1);
                }
                if (c.function().takesItem()) {
                    // item("Iron Ingot") is gewoon de naam, netjes geschreven; tellen vraagt hij aan de auto (hoofdstuk 113).
                    String item = itemName(text(eval(c.args().getFirst(), facts)));
                    yield c.function() == Function.ITEM ? (Object) item
                            : (Object) (double) facts.count(c.function() == Function.TRUNK_COUNT, item);
                }
                // pin(1) is gewoon het getal; read(pin) vraagt het aan de robot.
                double pin = Math.rint(toNumber(eval(c.args().getFirst(), facts)));
                yield c.function() == Function.PIN ? (Object) pin : facts.read((int) pin);
            }
            case In i -> facts.count(i.where() == Storage.TRUNK, itemName(text(eval(i.item(), facts)))) > 0;
            case Template t -> {
                // Aan elkaar geplakt, zoals met +: een getal zonder overbodige nullen, true of false.
                StringBuilder text = new StringBuilder();
                for (Expr part : t.parts()) {
                    text.append(text(eval(part, facts)));
                }
                yield text.toString();
            }
        };
    }

    /**
     * Rekent het uit. Staat er aan een kant van + tekst, dan worden ze aan elkaar geplakt. Delen door 0
     * geeft 0, zodat een diagram nooit vastloopt op een rare uitkomst.
     */
    private static Object arith(Object a, char op, Object b) {
        if (op == '+' && (a instanceof String || b instanceof String)) {
            return text(a) + text(b);
        }
        double x = toNumber(a);
        double y = toNumber(b);
        return switch (op) {
            case '+' -> x + y;
            case '-' -> x - y;
            case '*' -> x * y;
            default -> y == 0 ? 0.0 : x / y;
        };
    }

    private static double toNumber(Object value) {
        return value instanceof Double d ? d : 0.0;
    }

    /** Een waarde als tekst: een getal zonder overbodige nullen (3, 0.5), en true of false. */
    public static String text(Object value) {
        return value instanceof Double d ? number(d) : String.valueOf(value);
    }

    private static boolean compare(Object a, String op, Object b) {
        if (a instanceof Double x && b instanceof Double y) {
            return switch (op) {
                case "==" -> x.doubleValue() == y.doubleValue();
                case "!=" -> x.doubleValue() != y.doubleValue();
                case "<" -> x < y;
                case "<=" -> x <= y;
                case ">" -> x > y;
                default -> x >= y;
            };
        }
        boolean same = a.equals(b);
        return op.equals("!=") != same;
    }
}
