package com.senne.starktech.logic.program;

import java.util.List;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Een voorwaarde in een diagram: wat na {@code if} komt, zoals
 * {@code card.valid and card.level >= 3} of {@code code == "4321"} of {@code is_on("Lamp")}.
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

    /** Een gegeven van wat er gebeurde: {@code card.level}, {@code code}, {@code is_night}... */
    record Var(String name) implements Expr {}

    /** {@code is_on("Lamp")}: staat dat apparaat nu aan? */
    record IsOn(String device) implements Expr {}

    /** {@code -card.level}: het tegengestelde getal. (Een los getal zoals -20 wordt meteen een Num.) */
    record Neg(Expr inner) implements Expr {}

    /**
     * Rekenen (hoofdstuk 20): {@code a + b}, {@code a - b}, {@code a * b}, {@code a / b}. Met
     * {@code +} plak je ook tekst aan elkaar: {@code "Level " + card.level} wordt "Level 3".
     */
    record Arith(Expr left, char op, Expr right) implements Expr {}

    /**
     * Een naam die je zelf een waarde gaf (een variabele, hoofdstuk 20): {@code count}. {@code type}
     * is wat erin zit; ANY als we dat pas bij het uitvoeren weten.
     */
    record Ref(String name, Type type) implements Expr {}

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
        CARD_LEVEL("card.level", Type.NUMBER, "the level of the card (1-5, or 0 for a blank card)", Op.KEYCARD),
        CARD_NUMBER("card.number", Type.NUMBER, "the number of the card (like 1001)", Op.KEYCARD),
        CARD_VALID("card.valid", Type.BOOL, "true if this network wrote the card and it is not blocked", Op.KEYCARD),
        CARD_NAME("card.name", Type.TEXT, "the name written on the card", Op.KEYCARD),
        CODE("code", Type.TEXT, "the code that was typed, as text (use quotes: code == \"4321\")", Op.CODEPANEL),
        PLAYER_NAME("player.name", Type.TEXT, "the name of the player", null),
        PLAYER_TRUSTED("player.trusted", Type.BOOL, "true for the owner, their team and trusted players", null),
        PLAYER_OWNER("player.owner", Type.BOOL, "true if the player owns this network", null),
        IS_NIGHT("is_night", Type.BOOL, "true at night", null),
        IS_DAY("is_day", Type.BOOL, "true during the day", null);

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

        /** Mag dit gegeven na deze trigger? (player.* alleen na een lezer of paneel; null = in een def: alles.) */
        public boolean allowedAfter(@Nullable Op trigger) {
            if (name.startsWith("player.")) {
                return trigger == null || trigger.hasSourceDevice();
            }
            return only == null || trigger == null || only == trigger;
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
        return whole(text, new Parser(Lexer.tokens(text), 0, trigger, scope), true);
    }

    /** Streng lezen, zoals {@link #parseCondition}, maar een waarde van elke soort (rechts van een =). */
    public static Expr parseValue(String text, @Nullable Op trigger, java.util.Map<String, Type> scope) {
        return whole(text, new Parser(Lexer.tokens(text), 0, trigger, scope), false);
    }

    /**
     * Soepel lezen: een naam die we niet kennen (zonder punt) is een variabele waarvan we de soort nog
     * niet weten, en of een gegeven bij de trigger hoort, controleren we niet. Zo lezen de parser en de
     * server een diagram; de strenge controle doet ScriptChecker. {@code condition}: moet het waar of
     * onwaar zijn (een if)?
     */
    public static Expr parseLenient(String text, boolean condition) {
        return whole(text, new Parser(Lexer.tokens(text), 0, null, null), condition);
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
        Parser parser = new Parser(tokens, start, trigger, null);
        Expr expr = parser.parse(true);
        end[0] = parser.pos;
        return expr;
    }

    /** Leest een waarde (soepel) uit een stuk van een regel: wat rechts van {@code naam =} staat. */
    static Expr parseValue(List<Lexer.Token> tokens, int start, int[] end) {
        Parser parser = new Parser(tokens, start, null, null);
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

        Parser(List<Lexer.Token> tokens, int start, @Nullable Op trigger, java.util.@Nullable Map<String, Type> scope) {
            this.tokens = tokens;
            this.pos = start;
            this.trigger = trigger;
            this.scope = scope;
        }

        Lexer.Token peek() {
            return tokens.get(Math.min(pos, tokens.size() - 1));
        }

        private Lexer.Token next() {
            Lexer.Token token = peek();
            pos++;
            return token;
        }

        /** Het geheel: een voorwaarde ({@code condition}: waar of onwaar) of een waarde (van elke soort). */
        Expr parse(boolean condition) {
            if (peek().type() == Lexer.Type.END || peek().is(":")) {
                throw new LogicException(condition ? "The if is missing what to check, like: if card.level >= 3:"
                        : "Something is missing here: a number, a text or a name, like 0");
            }
            Expr expr = or();
            if (condition) {
                requireType(expr, Type.BOOL, "The check must be true or false, like card.level >= 3");
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
            return left;
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

        /** Een minteken ervoor: {@code -20}, {@code -card.level}. */
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
                    Lexer.Token device = next();
                    if (device.type() != Lexer.Type.STRING) {
                        throw new LogicException("is_on needs the name of a device between quotes, like is_on(\"Lamp\")");
                    }
                    if (!next().is(")")) {
                        throw new LogicException("is_on(...) is missing its closing )");
                    }
                    return new IsOn(device.text());
                }
                case "and", "or", "not" -> throw new LogicException("Something is missing before " + word);
                default -> {
                }
            }
            Variable variable = Variable.byName(word);
            if (variable == null && !word.contains(".")) {
                return reference(word);
            }
            if (variable == null) {
                throw new LogicException("I don't know \"" + word + "\". You can use: " + known());
            }
            if (scope != null && !variable.allowedAfter(trigger)) {
                String where = variable.only != null ? "when " + variable.only.code() + "(...)"
                        : "when keycard(...) or when codepanel(...)";
                throw new LogicException(word + " only works inside " + where);
            }
            return new Var(word);
        }

        /**
         * Een naam zonder punt die geen gegeven van E.D.I.T.H. is: een variabele. Soepel gelezen weten we
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
                if (variable.allowedAfter(trigger)) {
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
                if (variable.allowedAfter(trigger)) {
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

    /** Alle gegevens (card.level, code, player.name...) die in deze voorwaarde voorkomen. */
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
            default -> { }
        }
    }

    /** De soort van deze (deel)voorwaarde. */
    static Type typeOf(Expr expr) {
        return switch (expr) {
            case Num n -> Type.NUMBER;
            case Str s -> Type.TEXT;
            case Var v -> Variable.byName(v.name()) == null ? Type.BOOL : Variable.byName(v.name()).type;
            case Neg n -> Type.NUMBER;
            case Arith a -> arithType(a);
            case Ref r -> r.type();
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
            // Van links naar rechts: rechts haakjes als hij even sterk bindt (a - (b - c)).
            case Arith a -> print(a.left(), strength(a)) + " " + a.op() + " " + print(a.right(), strength(a) + 1);
            case Neg n -> "-" + print(n.inner(), 7);
            case Num n -> number(n.value());
            case Str s -> quote(s.value());
            case Bool b -> b.value() ? "true" : "false";
            case Var v -> v.name();
            case Ref r -> r.name();
            case IsOn i -> "is_on(" + quote(i.device()) + ")";
        };
        return strength(expr) < needed ? "(" + text + ")" : text;
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
            case IsOn i -> facts.isOn(i.device());
            case Neg n -> -toNumber(eval(n.inner(), facts));
            case Arith a -> arith(eval(a.left(), facts), a.op(), eval(a.right(), facts));
            case Ref r -> {
                Object value = facts.variable(r.name());
                // Nog geen waarde (kan alleen bij vreemde blokjes): dan de "lege" waarde van zijn soort.
                yield value != null ? value : r.type() == Type.TEXT ? "" : r.type() == Type.BOOL ? (Object) false : (Object) 0.0;
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
    static String text(Object value) {
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
