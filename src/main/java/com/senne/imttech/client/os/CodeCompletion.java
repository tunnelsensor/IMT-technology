package com.senne.imttech.client.os;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.logic.program.Checks;
import com.senne.imttech.logic.program.Expr;
import com.senne.imttech.logic.program.LogicException;
import com.senne.imttech.logic.program.Op;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.logic.program.Script;
import com.senne.imttech.logic.program.VarType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Wat je op de plek van de cursor kunt typen (jouw wens: "zorg dat Ctrl + spatie alle opties toont
 * die je kunt typen"). De code-editor vraagt het hier op als je Ctrl+Spatie drukt.
 * <ul>
 *   <li>een lege regel aan de linkerkant: een when (met elke trigger), een def, een naam, of (hoofdstuk 43)
 *       {@code global};</li>
 *   <li>na {@code when }: de triggers;</li>
 *   <li>een ingesprongen regel: de acties, if/elif/else/pass, en je eigen defs (met hun parameters), ook die uit je
 *       global scripts;</li>
 *   <li>na {@code if}, {@code and}, {@code or}, {@code not} of een vergelijking: de gegevens die bij
 *       de trigger van dat blok passen ({@code card.valid} alleen onder {@code when keycard}), je eigen namen en de
 *       gedeelde namen;</li>
 *   <li>na {@code naam:} (hoofdstuk 43): de soort-woorden int, float, string en bool;</li>
 *   <li>tussen aanhalingstekens: de namen van je lezers, panelen of apparaten, afhankelijk van wat er
 *       voor de haakjes staat ({@code keycard("}, {@code codepanel("}, {@code on("}...).</li>
 * </ul>
 * Wat je al getypt hebt (het stukje woord voor de cursor) maakt de lijst korter.
 */
public final class CodeCompletion {
    private CodeCompletion() {}

    /**
     * De namen die de server stuurde: je apparaten, lezers, panelen, (hoofdstuk 23) robots, (25 oktober) je
     * keycard-groepen, (hoofdstuk 38) je netwerkknoppen en -hendels, en (hoofdstuk 43) de gedeelde namen en de defs
     * (naam -> parameters als code) uit je global scripts.
     */
    public record Data(List<String> outputs, List<String> readers, List<String> panels, ProgramKind kind, List<String> robots,
                       List<String> cardGroups, List<String> buttons, List<String> levers, List<String> shared,
                       Map<String, String> globalDefs, List<String> consoles) {
        /** Zonder global scripts. */
        public Data(List<String> outputs, List<String> readers, List<String> panels, ProgramKind kind, List<String> robots,
                    List<String> cardGroups, List<String> buttons, List<String> levers) {
            this(outputs, readers, panels, kind, robots, cardGroups, buttons, levers, List.of(), Map.of(), List.of());
        }

        /** Zonder Auth Consoles (hoofdstuk 49). */
        public Data(List<String> outputs, List<String> readers, List<String> panels, ProgramKind kind, List<String> robots,
                    List<String> cardGroups, List<String> buttons, List<String> levers, List<String> shared,
                    Map<String, String> globalDefs) {
            this(outputs, readers, panels, kind, robots, cardGroups, buttons, levers, shared, globalDefs, List.of());
        }

        /** Zonder netwerkknoppen en -hendels. */
        public Data(List<String> outputs, List<String> readers, List<String> panels, ProgramKind kind, List<String> robots,
                    List<String> cardGroups) {
            this(outputs, readers, panels, kind, robots, cardGroups, List.of(), List.of());
        }

        /** Voor een diagram op de server. */
        public Data(List<String> outputs, List<String> readers, List<String> panels) {
            this(outputs, readers, panels, ProgramKind.SERVER, List.of());
        }

        /** Zonder robots. */
        public Data(List<String> outputs, List<String> readers, List<String> panels, ProgramKind kind) {
            this(outputs, readers, panels, kind, List.of());
        }

        /** Zonder keycard-groepen. */
        public Data(List<String> outputs, List<String> readers, List<String> panels, ProgramKind kind, List<String> robots) {
            this(outputs, readers, panels, kind, robots, List.of());
        }
    }

    /**
     * Eén suggestie: wat je ziet, wat er ingevuld wordt, waar de cursor daarna staat ({@code caret}
     * tekens na het begin, of -1 = aan het einde), uitleg, en de kleur.
     */
    public record Item(String label, String insert, int caret, int select, String detail, int colour) {
        /** Zonder iets te selecteren na het invullen. */
        Item(String label, String insert, int caret, String detail, int colour) {
            this(label, insert, caret, 0, detail, colour);
        }

        /** Komt de cursor tussen aanhalingstekens? Dan openen we meteen de namen. */
        boolean opensString() {
            return caret > 0 && caret <= insert.length() && insert.charAt(caret - 1) == '"';
        }
    }

    /**
     * Wat er past: de suggesties, vanaf welke kolom ze het getypte stukje vervangen, en of we in een
     * tekst tussen aanhalingstekens staan (dan springt de cursor na het invullen over de sluitende ").
     */
    public record Result(int replaceFrom, List<Item> items, boolean inString) {}

    private static final int KEYWORD = 0xFF79C6;
    private static final int TRIGGER = HudStyle.CYAN;
    private static final int ACTION = 0x9ED8FF;
    private static final int VARIABLE = 0xF1E87C;
    private static final int NAME = 0x6BE88B;
    /** {@code def naam(parameters)} (hoofdstuk 43: met wat er tussen de haakjes staat). */
    private static final Pattern DEF = Pattern.compile("^def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(([^)]*)\\)?");
    /** {@code when keycard(...)} of (hoofdstuk 23) {@code when started:} zonder haakjes. */
    private static final Pattern WHEN = Pattern.compile("^when\\s+([A-Za-z_]+)\\s*[(:]");
    /** {@code count = ...} (één =, geen ==) of (hoofdstuk 43) {@code count: int = ...}: een naam die een waarde krijgt. */
    private static final Pattern ASSIGN = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*(?::\\s*[A-Za-z]+\\s*)?=(?!=)");
    /** {@code for i in range(...)} (16 oktober): de naam die telt, is ook een van je eigen namen. */
    private static final Pattern FOR = Pattern.compile("^\\s*for\\s+([A-Za-z_][A-Za-z0-9_]*)\\s+in\\b");
    /** {@code naam:} vlak voor de cursor (hoofdstuk 43): daar komt een soort-woord. */
    private static final Pattern TYPE_SLOT = Pattern.compile("(?:^|[(,\\s])([A-Za-z_][A-Za-z0-9_]*)\\s*:$");

    public static Result complete(List<String> lines, int line, int col, Data data) {
        String text = lines.get(line);
        String before = text.substring(0, Math.min(col, text.length()));
        // In een tekst tussen aanhalingstekens? (Een oneven aantal " ervoor.)
        int quote = openQuote(before);
        if (quote >= 0) {
            String prefix = before.substring(quote + 1);
            String function = wordBefore(before.substring(0, quote));
            List<Item> names = new ArrayList<>();
            List<String> source = switch (function) {
                case "keycard" -> data.readers();
                case "codepanel" -> data.panels();
                case "button" -> data.buttons();
                case "lever" -> data.levers();
                case "request" -> data.consoles();
                case "on", "off", "toggle", "pulse", "turned_on", "turned_off" -> data.outputs();
                // is_on kent ook je netwerkhendels (hoofdstuk 38).
                case "is_on" -> concat(data.outputs(), data.levers());
                case "order", "activate", "deactivate" -> data.robots();
                case "in_group" -> data.cardGroups();
                default -> List.of();
            };
            String what = function.equals("keycard") ? "a keycard reader" : function.equals("codepanel") ? "a code panel"
                    : function.equals("button") ? "a network button" : function.equals("lever") ? "a network lever"
                    : data.robots() == source ? "a guard robot in this network's area"
                    : function.equals("in_group") ? "a keycard group"
                    : "a relay, laser, dome, turret or cabinet switch";
            for (String name : source) {
                String kind = function.equals("is_on") && data.levers().contains(name) && !data.outputs().contains(name)
                        ? "a network lever" : what;
                names.add(new Item(name, name, -1, kind + " in this network", NAME));
            }
            return new Result(quote + 1, filter(names, prefix), true);
        }
        // Anders: het stukje woord voor de cursor (letters, cijfers, _ en .).
        int start = col;
        while (start > 0 && (Character.isLetterOrDigit(before.charAt(start - 1)) || before.charAt(start - 1) == '_'
                || before.charAt(start - 1) == '.')) {
            start--;
        }
        String prefix = before.substring(start);
        String head = before.substring(0, start);
        String trimmed = head.strip();
        List<Item> items = new ArrayList<>();
        if (isTypeSlot(trimmed)) {
            types(items);
        } else if (trimmed.isEmpty() && head.isEmpty()) {
            topLevel(items, lines, data);
        } else if (trimmed.isEmpty()) {
            statements(items, lines, data);
        } else if (trimmed.equals("when")) {
            triggers(items, data);
        } else if (trimmed.equals("def")) {
            return new Result(start, List.of(), false); // een nieuwe naam: die verzin je zelf
        } else if (isConditionContext(trimmed)) {
            conditions(items, lines, triggerAbove(lines, line), data);
        } else {
            statements(items, lines, data);
            conditions(items, lines, triggerAbove(lines, line), data);
        }
        return new Result(start, filter(items, prefix), false);
    }

    // ================================================================ Per plek

    /** Aan de linkerkant: een when met elke trigger, een def, een naam, of global. */
    private static void topLevel(List<Item> items, List<String> lines, Data data) {
        String reader = first(data.readers(), "Reader 1");
        String panel = first(data.panels(), "Panel 1");
        String button = first(data.buttons(), "Button 1");
        String lever = first(data.levers(), "Lever 1");
        String console = first(data.consoles(), "Console 1");
        String device = first(data.outputs(), "Lamp");
        boolean global = isGlobalScript(lines);
        // Elke trigger die er is (zie Op): een nieuwe trigger staat hier vanzelf bij. (Niet in een global script.)
        if (!global) {
            for (Op op : Op.values()) {
                if (!op.isTrigger() || !op.allowedIn(data.kind())) {
                    continue;
                }
                boolean named = op.textArg() != Op.Arg.NONE;
                String name = op == Op.KEYCARD ? reader : op == Op.CODEPANEL ? panel : op == Op.BUTTON ? button
                        : op == Op.LEVER ? lever : op == Op.REQUEST ? console
                        // Hoofdstuk 113: een RC Car krijgt "fetch" en "store" van zijn Storage Interface.
                        : op == Op.ORDERED && data.kind() == ProgramKind.CAR ? "fetch"
                        : op.textArg() == Op.Arg.TEXT ? op.textExample() : device;
                String label = "when " + (named ? op.code() + "(...)" : op.example()) + ":";
                String call = named ? op.code() + "(\"" + name + "\")" : op.example();
                items.add(new Item(label, "when " + call + ":\n    ", -1, op.help(), TRIGGER));
            }
            if (data.kind() == ProgramKind.CAR) {
                items.add(new Item("when ordered(\"store\"):", "when ordered(\"store\"):\n    ", -1,
                        "Someone put items into the Storage Interface of this car to store them (order.item, order.count)", TRIGGER));
            }
        }
        // De naam is meteen geselecteerd: typen vervangt hem.
        items.add(new Item("def name():", "def my_block():\n    ", 4, 8, Op.DEF.help(), CodeEditor.FUNCTION));
        items.add(new Item("def name(value: type):", "def my_block(door: string):\n    ", 4, 8,
                "Your own block with values it needs: use it with my_block(\"Lab door\")", CodeEditor.FUNCTION));
        items.add(new Item("name = value", "count = 0", 0, 5, Op.GLOBAL.help(), VARIABLE));
        items.add(new Item("name: type = value", "lockdown: bool = false", 0, 8,
                global ? "A shared name: every diagram of this core can read and change it"
                        : "A name with a type (" + VarType.words() + "): it keeps that type", VARIABLE));
        if (!global && data.kind() == ProgramKind.SERVER && lines.stream().allMatch(String::isBlank)) {
            items.add(new Item("global", "global\n\n", -1,
                    "The first line of a global script: only defs and shared names, for all your diagrams", KEYWORD));
        }
    }

    /** Na "when ": de triggers (de cursor komt tussen de aanhalingstekens). */
    private static void triggers(List<Item> items, Data data) {
        for (Op op : Op.values()) {
            if (op.isTrigger() && op.allowedIn(data.kind())) {
                items.add(item(op, ":", TRIGGER));
            }
        }
    }

    /** Na {@code naam:} (hoofdstuk 43): de soort-woorden. */
    private static void types(List<Item> items) {
        for (VarType type : VarType.values()) {
            items.add(new Item(type.word(), type.word() + " ", -1, type.help(), CodeEditor.TYPE));
        }
    }

    /**
     * Een blokje uit Op als suggestie, met wat erachter moet ({@code suffix}, bijvoorbeeld de ":" van
     * een trigger). De cursor komt waar de | in het voorbeeld staat (zie {@link Op#example}).
     */
    private static Item item(Op op, String suffix, int colour) {
        String example = op.example();
        return new Item(example.replace("|", "...") + suffix, example.replace("|", "") + suffix,
                example.indexOf('|'), op.help(), colour);
    }

    /** Een ingesprongen regel: acties, if/elif/else/pass en je eigen defs (ook die uit je global scripts). */
    private static void statements(List<Item> items, List<String> lines, Data data) {
        // Elke actie die er is (zie Op): een nieuwe actie staat hier vanzelf bij. Een eigen blok (CALL)
        // staat hieronder bij de namen van je defs.
        for (Op op : Op.values()) {
            if (op.kind() == Op.Kind.ACTION && op != Op.CALL && op != Op.BREAK && op.allowedIn(data.kind())) {
                items.add(item(op, "", ACTION));
            }
        }
        items.add(new Item("name = value", "count = count + 1", 0, 5, Op.SET.help(), VARIABLE));
        items.add(new Item("name: type = value", "count: int = 0", 0, 5,
                "Give a name a value and a type (" + VarType.words() + "): it keeps that type", VARIABLE));
        items.add(new Item("if ...:", "if :", 3, Op.IF.help(), KEYWORD));
        // De lussen (16 oktober: for en while). Het bereik van for is meteen geselecteerd: typen vervangt het.
        items.add(new Item("for i in range(3):", "for i in range(3):", 15, 1,
                Op.FOR.help() + ". i is 0, 1, 2; range(1, 4) counts 1, 2, 3", KEYWORD));
        items.add(new Item("while ...:", "while :", 6, Op.WHILE.help(), KEYWORD));
        items.add(new Item("while true:", "while true:", -1, "Do the steps inside again and again, until break", KEYWORD));
        items.add(new Item("break", "break", -1, Op.BREAK.help(), KEYWORD));
        items.add(new Item("elif ...:", "elif :", 5, "Another check, when the if above was not true", KEYWORD));
        items.add(new Item("else:", "else:", -1, "What happens when the if above is not true", KEYWORD));
        items.add(new Item("pass", "pass", -1, "Do nothing (for an empty block)", KEYWORD));
        ownBlocks(lines).forEach((name, params) -> items.add(call(name, params, "One of your own blocks: does all its steps here")));
        data.globalDefs().forEach((name, params) -> {
            if (!ownBlocks(lines).containsKey(name)) {
                items.add(call(name, params, "A def from your global script: does all its steps here"));
            }
        });
    }

    /** Een def aanroepen: {@code open_door("Lab door")}; met parameters komt de cursor tussen de haakjes. */
    private static Item call(String name, String params, String detail) {
        boolean takes = !params.isBlank();
        String label = name + "(" + params + ")";
        return new Item(label, name + "()", takes ? name.length() + 1 : -1, detail + (takes ? " (needs: " + params + ")" : ""),
                CodeEditor.FUNCTION);
    }

    /** Na if/and/or/not of een vergelijking: de gegevens die onder deze trigger werken, je eigen namen en de gedeelde. */
    private static void conditions(List<Item> items, List<String> lines, @Nullable Op trigger, Data data) {
        for (Expr.Variable variable : Expr.Variable.values()) {
            if (variable.allowedAfter(trigger) && variable.allowedIn(data.kind())) {
                items.add(new Item(variable.code(), variable.code(), -1, variable.help(), VARIABLE));
            }
        }
        for (String name : ownNames(lines)) {
            items.add(new Item(name, name, -1, "One of your own names (it got a value with " + name + " = ...)", VARIABLE));
        }
        for (String name : data.shared()) {
            items.add(new Item(name, name, -1, "A shared name from your global script: every diagram sees the same value", VARIABLE));
        }
        // pin(1) en read(scanPin) (hoofdstuk 23): de cursor komt tussen de haakjes. card.in_group("") (25 oktober): tussen
        // de aanhalingstekens, en dan komen meteen je groepen.
        for (Expr.Function function : Expr.Function.values()) {
            if (function.allowedIn(data.kind()) && function.allowedAfter(trigger)) {
                if (function == Expr.Function.CARD_IN_GROUP || function.takesItem()) {
                    // Ook item("..."), chest.count("...") en trunk.count("...") (hoofdstuk 113): de naam van een item.
                    items.add(new Item(function.code() + "(\"...\")", function.code() + "(\"\")", function.code().length() + 2,
                            function.help(), VARIABLE));
                } else if (function.withoutValues()) {
                    // block() (hoofdstuk 108): niets tussen de haakjes, dus de cursor komt erachter.
                    items.add(new Item(function.code() + "()", function.code() + "()", -1, function.help(), VARIABLE));
                } else {
                    items.add(new Item(function.code() + "(...)", function.code() + "()", function.code().length() + 1,
                            function.help(), VARIABLE));
                }
            }
        }
        if (data.kind() != ProgramKind.CAR) { // een RC Car heeft geen server, dus geen apparaten (hoofdstuk 108)
            items.add(new Item("is_on(\"...\")", "is_on(\"\")", 7, "Is that device switched on right now?", VARIABLE));
        } else {
            // Hoofdstuk 113: ligt dat item in de kist vóór de auto, of in zijn kofferbak?
            items.add(new Item("in chest()", "in chest()", -1,
                    "Is that item in the chest in front of the car? Like: item(\"iron_ingot\") in chest()", KEYWORD));
            items.add(new Item("in trunk()", "in trunk()", -1,
                    "Is that item in the trunk of the car? Like: item(\"iron_ingot\") in trunk()", KEYWORD));
        }
        items.add(new Item("not", "not ", -1, "The opposite: not is_night", KEYWORD));
        items.add(new Item("and", "and ", -1, "Both must be true", KEYWORD));
        items.add(new Item("or", "or ", -1, "One of them must be true", KEYWORD));
        items.add(new Item("true", "true", -1, "Always yes", 0xC09CFF));
        items.add(new Item("false", "false", -1, "Always no", 0xC09CFF));
    }

    // ================================================================ Hulpjes

    /** Staat er {@code naam:} vlak voor de cursor (en geen if/while...)? Dan komt er een soort-woord (hoofdstuk 43). */
    private static boolean isTypeSlot(String head) {
        Matcher slot = TYPE_SLOT.matcher(head);
        if (!slot.find()) {
            return false;
        }
        String firstWord = head.split("[\\s(:]+", 2)[0].toLowerCase(Locale.ROOT);
        return !List.of("if", "elif", "while", "for", "when", "else").contains(firstWord);
    }

    /** Begint de code met {@code global} (hoofdstuk 43)? */
    static boolean isGlobalScript(List<String> lines) {
        for (String text : lines) {
            String line = text.strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            return line.equals("global");
        }
        return false;
    }

    /** Staan we in een voorwaarde (na if, elif, and, or, not, of een vergelijkingsteken)? */
    private static boolean isConditionContext(String head) {
        String lower = head.toLowerCase(Locale.ROOT);
        if (lower.startsWith("if ") || lower.equals("if") || lower.startsWith("elif ") || lower.equals("elif")
                || lower.startsWith("while ") || lower.equals("while") || lower.startsWith("for ") && lower.contains("range(")) {
            return true;
        }
        // ({ is de code in $"Round {i}", 19 oktober: daar staat een naam of een berekening.)
        for (String end : List.of(" and", " or", " not", "(", "{", "==", "!=", "<", ">", "<=", ">=", "+", "-", "*", "/", ",")) {
            if (lower.endsWith(end)) {
                return true;
            }
        }
        return false;
    }

    /** De trigger van het blok waar deze regel in staat (null in een def, of als er geen is). */
    static @Nullable Op triggerAbove(List<String> lines, int line) {
        for (int i = line; i >= 0; i--) {
            String text = lines.get(i);
            if (text.isEmpty() || text.charAt(0) == ' ' || text.charAt(0) == '#') {
                continue;
            }
            Matcher when = WHEN.matcher(text);
            if (when.find()) {
                Op op = Op.byCode(when.group(1));
                return op != null && op.isTrigger() ? op : null;
            }
            return null; // een def (of iets anders aan de linkerkant): alles mag
        }
        return null;
    }

    /**
     * De namen die ergens in de code een waarde krijgen ({@code count = ...}, {@code count: int = ...}), de namen die
     * tellen in een for, en (hoofdstuk 43) de parameters van je defs, in de volgorde van de code.
     */
    static Set<String> ownNames(List<String> lines) {
        Set<String> names = new LinkedHashSet<>();
        for (String text : lines) {
            Matcher name = ASSIGN.matcher(text);
            if (name.find() && Op.byCode(name.group(1)) == null) {
                names.add(name.group(1));
            }
            Matcher counter = FOR.matcher(text);
            if (counter.find()) {
                names.add(counter.group(1));
            }
            Matcher def = DEF.matcher(text);
            if (def.find()) {
                try {
                    for (Script.Param param : Checks.parseParams(def.group(2))) {
                        names.add(param.name());
                    }
                } catch (LogicException e) {
                    // Parameters die (nog) niet kloppen: dan geen namen.
                }
            }
        }
        return names;
    }

    /** De namen van alle defs in de code, met hun parameters als code (leeg zonder). */
    static Map<String, String> ownBlocks(List<String> lines) {
        Map<String, String> names = new LinkedHashMap<>();
        for (String text : lines) {
            Matcher def = DEF.matcher(text);
            if (def.find()) {
                names.put(def.group(1), def.group(2).strip());
            }
        }
        return names;
    }

    /**
     * Waar de tekst begint waar de cursor in staat (de " ervoor), of -1 als we niet in een tekst staan. In de code van
     * {@code $"Round {i}"} (19 oktober) staan we niet in een tekst: daar stellen we namen voor, zoals overal in code.
     */
    private static int openQuote(String before) {
        int open = -1;
        boolean template = false;
        int depth = 0;
        char inner = 0;
        for (int i = 0; i < before.length(); i++) {
            char c = before.charAt(i);
            if (open < 0) {
                if (c == '#') {
                    return -1; // commentaar
                }
                if (c == '"' || c == '\'') {
                    open = i;
                    template = i > 0 && before.charAt(i - 1) == '$';
                    depth = 0;
                }
            } else if (depth > 0) {
                // In de code tussen { en } van $"...": tot de } die erbij hoort (tekst erin telt niet mee).
                if (inner != 0) {
                    if (c == '\\') {
                        i++;
                    } else if (c == inner) {
                        inner = 0;
                    }
                } else if (c == '"' || c == '\'') {
                    inner = c;
                } else if (c == '{') {
                    depth++;
                } else if (c == '}') {
                    depth--;
                }
            } else if (c == '\\') {
                i++;
            } else if (c == before.charAt(open)) {
                open = -1;
            } else if (template && c == '{') {
                if (i + 1 < before.length() && before.charAt(i + 1) == '{') {
                    i++; // {{ is een gewone { in de tekst
                } else {
                    depth = 1;
                }
            }
        }
        return open >= 0 && depth == 0 ? open : -1;
    }

    /** Het woord vlak voor de haakjes: in {@code pulse(} is dat "pulse". */
    private static String wordBefore(String text) {
        String trimmed = text.stripTrailing();
        if (!trimmed.endsWith("(")) {
            return "";
        }
        int end = trimmed.length() - 1;
        int start = end;
        while (start > 0 && (Character.isLetterOrDigit(trimmed.charAt(start - 1)) || trimmed.charAt(start - 1) == '_')) {
            start--;
        }
        return trimmed.substring(start, end);
    }

    private static String first(List<String> names, String fallback) {
        return names.isEmpty() ? fallback : names.getFirst();
    }

    /** Twee lijstjes namen achter elkaar, zonder dubbele (hoofdletters maken niet uit). */
    private static List<String> concat(List<String> first, List<String> second) {
        List<String> all = new ArrayList<>(first);
        for (String name : second) {
            if (all.stream().noneMatch(name::equalsIgnoreCase)) {
                all.add(name);
            }
        }
        return all;
    }

    /**
     * Alleen wat bij het getypte stukje past (hoofdletters maken niet uit): eerst wat ermee begint,
     * dan wat het ergens bevat.
     */
    private static List<Item> filter(List<Item> items, String prefix) {
        if (prefix.isEmpty()) {
            return items;
        }
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<Item> starts = new ArrayList<>();
        List<Item> contains = new ArrayList<>();
        for (Item item : items) {
            String label = item.label().toLowerCase(Locale.ROOT);
            if (label.startsWith(lower)) {
                starts.add(item);
            } else if (label.contains(lower)) {
                contains.add(item);
            }
        }
        starts.addAll(contains);
        return starts;
    }
}
