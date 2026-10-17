package com.senne.imttech.client.os;

import com.senne.imttech.client.hud.HudStyle;
import com.senne.imttech.logic.program.Expr;
import com.senne.imttech.logic.program.Op;
import com.senne.imttech.logic.program.ProgramKind;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;

/**
 * Wat je op de plek van de cursor kunt typen (jouw wens: "zorg dat Ctrl + spatie alle opties toont
 * die je kunt typen"). De code-editor vraagt het hier op als je Ctrl+Spatie drukt.
 * <ul>
 *   <li>een lege regel aan de linkerkant: een when (met elke trigger) of een def;</li>
 *   <li>na {@code when }: de triggers;</li>
 *   <li>een ingesprongen regel: de acties, if/elif/else/pass, en je eigen defs;</li>
 *   <li>na {@code if}, {@code and}, {@code or}, {@code not} of een vergelijking: de gegevens die bij
 *       de trigger van dat blok passen ({@code card.level} alleen onder {@code when keycard});</li>
 *   <li>tussen aanhalingstekens: de namen van je lezers, panelen of apparaten, afhankelijk van wat er
 *       voor de haakjes staat ({@code keycard("}, {@code codepanel("}, {@code on("}...).</li>
 * </ul>
 * Wat je al getypt hebt (het stukje woord voor de cursor) maakt de lijst korter.
 */
public final class CodeCompletion {
    private CodeCompletion() {}

    /** De namen die de server stuurde: je apparaten, lezers, panelen en (hoofdstuk 23) robots. */
    public record Data(List<String> outputs, List<String> readers, List<String> panels, ProgramKind kind, List<String> robots) {
        /** Voor een diagram op de server. */
        public Data(List<String> outputs, List<String> readers, List<String> panels) {
            this(outputs, readers, panels, ProgramKind.SERVER, List.of());
        }

        /** Zonder robots. */
        public Data(List<String> outputs, List<String> readers, List<String> panels, ProgramKind kind) {
            this(outputs, readers, panels, kind, List.of());
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
    private static final Pattern DEF = Pattern.compile("^def\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    /** {@code when keycard(...)} of (hoofdstuk 23) {@code when started:} zonder haakjes. */
    private static final Pattern WHEN = Pattern.compile("^when\\s+([A-Za-z_]+)\\s*[(:]");
    /** {@code count = ...} (één =, geen ==): een naam die een waarde krijgt. */
    private static final Pattern ASSIGN = Pattern.compile("^\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*=(?!=)");

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
                case "on", "off", "toggle", "pulse", "turned_on", "turned_off", "is_on" -> data.outputs();
                case "order", "activate", "deactivate" -> data.robots();
                default -> List.of();
            };
            String what = function.equals("keycard") ? "a keycard reader" : function.equals("codepanel") ? "a code panel"
                    : data.robots() == source ? "a guard robot in this network's area"
                    : "a relay, laser, dome, turret or cabinet switch";
            for (String name : source) {
                names.add(new Item(name, name, -1, what + " in this network", NAME));
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
        if (trimmed.isEmpty() && head.isEmpty()) {
            topLevel(items, data);
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

    /** Aan de linkerkant: een when met elke trigger, of een def. */
    private static void topLevel(List<Item> items, Data data) {
        String reader = first(data.readers(), "Reader 1");
        String panel = first(data.panels(), "Panel 1");
        String device = first(data.outputs(), "Lamp");
        // Elke trigger die er is (zie Op): een nieuwe trigger staat hier vanzelf bij.
        for (Op op : Op.values()) {
            if (!op.isTrigger() || !op.allowedIn(data.kind())) {
                continue;
            }
            boolean named = op.textArg() != Op.Arg.NONE;
            String name = op == Op.KEYCARD ? reader : op == Op.CODEPANEL ? panel
                    : op.textArg() == Op.Arg.TEXT ? op.textExample() : device;
            String label = "when " + (named ? op.code() + "(...)" : op.example()) + ":";
            String call = named ? op.code() + "(\"" + name + "\")" : op.example();
            items.add(new Item(label, "when " + call + ":\n    ", -1, op.help(), TRIGGER));
        }
        // De naam is meteen geselecteerd: typen vervangt hem.
        items.add(new Item("def name():", "def my_block():\n    ", 4, 8, Op.DEF.help(), CodeEditor.FUNCTION));
        items.add(new Item("name = value", "count = 0", 0, 5, Op.GLOBAL.help(), VARIABLE));
    }

    /** Na "when ": de triggers (de cursor komt tussen de aanhalingstekens). */
    private static void triggers(List<Item> items, Data data) {
        for (Op op : Op.values()) {
            if (op.isTrigger() && op.allowedIn(data.kind())) {
                items.add(item(op, ":", TRIGGER));
            }
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

    /** Een ingesprongen regel: acties, if/elif/else/pass en je eigen defs. */
    private static void statements(List<Item> items, List<String> lines, Data data) {
        // Elke actie die er is (zie Op): een nieuwe actie staat hier vanzelf bij. Een eigen blok (CALL)
        // staat hieronder bij de namen van je defs.
        for (Op op : Op.values()) {
            if (op.kind() == Op.Kind.ACTION && op != Op.CALL && op != Op.BREAK && op.allowedIn(data.kind())) {
                items.add(item(op, "", ACTION));
            }
        }
        items.add(new Item("name = value", "count = count + 1", 0, 5, Op.SET.help(), VARIABLE));
        items.add(new Item("if ...:", "if :", 3, Op.IF.help(), KEYWORD));
        // De lussen (het aantal van repeat is meteen geselecteerd: typen vervangt het).
        items.add(new Item("repeat(3):", "repeat(3):", 7, 1, Op.REPEAT.help(), KEYWORD));
        items.add(new Item("while ...:", "while :", 6, Op.WHILE.help(), KEYWORD));
        items.add(new Item("forever:", "forever:", -1, Op.FOREVER.help(), KEYWORD));
        items.add(new Item("break", "break", -1, Op.BREAK.help(), KEYWORD));
        items.add(new Item("elif ...:", "elif :", 5, "Another check, when the if above was not true", KEYWORD));
        items.add(new Item("else:", "else:", -1, "What happens when the if above is not true", KEYWORD));
        items.add(new Item("pass", "pass", -1, "Do nothing (for an empty block)", KEYWORD));
        for (String name : ownBlocks(lines)) {
            items.add(new Item(name + "()", name + "()", -1, "One of your own blocks: does all its steps here",
                    CodeEditor.FUNCTION));
        }
    }

    /** Na if/and/or/not of een vergelijking: de gegevens die onder deze trigger werken, en je eigen namen. */
    private static void conditions(List<Item> items, List<String> lines, @Nullable Op trigger, Data data) {
        for (Expr.Variable variable : Expr.Variable.values()) {
            if (variable.allowedAfter(trigger) && variable.allowedIn(data.kind())) {
                items.add(new Item(variable.code(), variable.code(), -1, variable.help(), VARIABLE));
            }
        }
        for (String name : ownNames(lines)) {
            items.add(new Item(name, name, -1, "One of your own names (it got a value with " + name + " = ...)", VARIABLE));
        }
        // pin(1) en read(scanPin) (hoofdstuk 23): de cursor komt tussen de haakjes.
        for (Expr.Function function : Expr.Function.values()) {
            if (function.allowedIn(data.kind())) {
                items.add(new Item(function.code() + "(...)", function.code() + "()", function.code().length() + 1,
                        function.help(), VARIABLE));
            }
        }
        items.add(new Item("is_on(\"...\")", "is_on(\"\")", 7, "Is that device switched on right now?", VARIABLE));
        items.add(new Item("not", "not ", -1, "The opposite: not is_night", KEYWORD));
        items.add(new Item("and", "and ", -1, "Both must be true", KEYWORD));
        items.add(new Item("or", "or ", -1, "One of them must be true", KEYWORD));
        items.add(new Item("true", "true", -1, "Always yes", 0xC09CFF));
        items.add(new Item("false", "false", -1, "Always no", 0xC09CFF));
    }

    // ================================================================ Hulpjes

    /** Staan we in een voorwaarde (na if, elif, and, or, not, of een vergelijkingsteken)? */
    private static boolean isConditionContext(String head) {
        String lower = head.toLowerCase(Locale.ROOT);
        if (lower.startsWith("if ") || lower.equals("if") || lower.startsWith("elif ") || lower.equals("elif")
                || lower.startsWith("while ") || lower.equals("while") || lower.startsWith("repeat(")) {
            return true;
        }
        for (String end : List.of(" and", " or", " not", "(", "==", "!=", "<", ">", "<=", ">=", "+", "-", "*", "/")) {
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

    /** De namen die ergens in de code een waarde krijgen ({@code count = ...}), in de volgorde van de code. */
    static Set<String> ownNames(List<String> lines) {
        Set<String> names = new LinkedHashSet<>();
        for (String text : lines) {
            Matcher name = ASSIGN.matcher(text);
            if (name.find() && Op.byCode(name.group(1)) == null) {
                names.add(name.group(1));
            }
        }
        return names;
    }

    /** De namen van alle defs in de code. */
    static Set<String> ownBlocks(List<String> lines) {
        Set<String> names = new LinkedHashSet<>();
        for (String text : lines) {
            Matcher def = DEF.matcher(text);
            if (def.find()) {
                names.add(def.group(1));
            }
        }
        return names;
    }

    /** Waar de tekst begint waar de cursor in staat (de " ervoor), of -1 als we niet in een tekst staan. */
    private static int openQuote(String before) {
        int open = -1;
        for (int i = 0; i < before.length(); i++) {
            char c = before.charAt(i);
            if (c == '#' && open < 0) {
                return -1; // commentaar
            }
            if (c == '\\' && open >= 0) {
                i++;
            } else if (c == '"' || c == '\'') {
                open = open < 0 ? i : -1;
            }
        }
        return open;
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
