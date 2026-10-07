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

    /** De soorten waarden: een getal, tekst, of waar/onwaar. */
    enum Type { NUMBER, TEXT, BOOL }

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

        /** Mag dit gegeven na deze trigger? (player.* alleen na een lezer of paneel.) */
        boolean allowedAfter(@Nullable Op trigger) {
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
     * Leest een voorwaarde uit tekst. {@code trigger} is de trigger waar hij onder hangt (null = nog
     * onbekend, bijvoorbeeld een los blokje): dan weten we welke gegevens er zijn. Gooit een
     * LogicException met uitleg als hij niet klopt.
     */
    static Expr parse(String text, @Nullable Op trigger) {
        List<Lexer.Token> tokens = Lexer.tokens(text);
        Parser parser = new Parser(tokens, 0, trigger);
        Expr expr = parser.parse();
        if (parser.peek().type() != Lexer.Type.END) {
            throw new LogicException("I did not expect " + parser.peek() + " here");
        }
        return expr;
    }

    /** Leest een voorwaarde uit een stuk van een regel (na "if", tot de ":"). */
    static Expr parse(List<Lexer.Token> tokens, int start, @Nullable Op trigger, int[] end) {
        Parser parser = new Parser(tokens, start, trigger);
        Expr expr = parser.parse();
        end[0] = parser.pos;
        return expr;
    }

    /** Een kleine "recursive descent" parser: elke voorrangsregel een eigen methode. */
    final class Parser {
        private final List<Lexer.Token> tokens;
        private int pos;
        private final @Nullable Op trigger;

        Parser(List<Lexer.Token> tokens, int start, @Nullable Op trigger) {
            this.tokens = tokens;
            this.pos = start;
            this.trigger = trigger;
        }

        Lexer.Token peek() {
            return tokens.get(Math.min(pos, tokens.size() - 1));
        }

        private Lexer.Token next() {
            Lexer.Token token = peek();
            pos++;
            return token;
        }

        Expr parse() {
            if (peek().type() == Lexer.Type.END || peek().is(":")) {
                throw new LogicException("The if is missing what to check, like: if card.level >= 3:");
            }
            Expr expr = or();
            requireType(expr, Type.BOOL, "The check after if must be true or false");
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
            Expr left = primary();
            Lexer.Token token = peek();
            if (token.type() == Lexer.Type.SYMBOL && List.of("==", "!=", "<", "<=", ">", ">=").contains(token.text())) {
                next();
                Expr right = primary();
                checkCompare(left, token.text(), right);
                return new Compare(left, token.text(), right);
            }
            return left;
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
            if (variable == null) {
                throw new LogicException("I don't know \"" + word + "\". You can use: " + known());
            }
            if (!variable.allowedAfter(trigger)) {
                String where = variable.only != null ? "when " + variable.only.code() + "(...)"
                        : "when keycard(...) or when codepanel(...)";
                throw new LogicException(word + " only works inside " + where);
            }
            return new Var(word);
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
            if (a != b) {
                throw new LogicException("You cannot compare " + describe(a) + " with " + describe(b));
            }
            if (!op.equals("==") && !op.equals("!=") && a != Type.NUMBER) {
                throw new LogicException(op + " only works with numbers");
            }
        }

        private static void requireType(Expr expr, Type wanted, String message) {
            if (typeOf(expr) != wanted) {
                throw new LogicException(message);
            }
        }

        private static String describe(Type type) {
            return switch (type) {
                case NUMBER -> "a number";
                case TEXT -> "text";
                case BOOL -> "true/false";
            };
        }
    }

    /** De soort van deze (deel)voorwaarde. */
    static Type typeOf(Expr expr) {
        return switch (expr) {
            case Num n -> Type.NUMBER;
            case Str s -> Type.TEXT;
            case Var v -> Variable.byName(v.name()) == null ? Type.BOOL : Variable.byName(v.name()).type;
            default -> Type.BOOL;
        };
    }

    // ================================================================ Terugschrijven

    /** De voorwaarde als nette tekst, met alleen de haakjes die nodig zijn. */
    static String print(Expr expr) {
        return print(expr, 0);
    }

    /** Hoe sterk iets bindt: or 1, and 2, not 3, vergelijken 4, de rest 5. */
    private static int strength(Expr expr) {
        return switch (expr) {
            case Or o -> 1;
            case And a -> 2;
            case Not n -> 3;
            case Compare c -> 4;
            default -> 5;
        };
    }

    private static String print(Expr expr, int needed) {
        String text = switch (expr) {
            case Or o -> print(o.left(), 1) + " or " + print(o.right(), 2);
            case And a -> print(a.left(), 2) + " and " + print(a.right(), 3);
            case Not n -> "not " + print(n.inner(), 3);
            case Compare c -> print(c.left(), 5) + " " + c.op() + " " + print(c.right(), 5);
            case Num n -> number(n.value());
            case Str s -> quote(s.value());
            case Bool b -> b.value() ? "true" : "false";
            case Var v -> v.name();
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
    }

    /** Is deze voorwaarde nu waar? */
    static boolean test(Expr expr, Facts facts) {
        return Boolean.TRUE.equals(eval(expr, facts));
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
        };
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
