package com.senne.starktech.logic.program;

import org.jetbrains.annotations.Nullable;

/**
 * Controles die de code (ScriptParser) en de blokjes (LogicGraph) allebei doen, zodat ze precies
 * dezelfde regels en dezelfde foutmeldingen hebben.
 */
public final class Checks {
    private Checks() {}

    /**
     * Klopt wat er bij dit blokje is ingevuld? {@code text} is de naam of het bericht (of null als er
     * niets staat), {@code number} het getal (of null). Geeft de fout in begrijpelijk Engels, of null.
     */
    public static @Nullable String argsProblem(Op op, @Nullable String text, @Nullable Double number) {
        switch (op.textArg()) {
            case DEVICE -> {
                if (text == null || text.isBlank()) {
                    return op.code() + " needs the name of a device between quotes, like "
                            + op.code() + "(\"Lab door\")" + (op.numberArg() == Op.Arg.OPTIONAL_SECONDS ? " or " + op.code() + "(\"Lab door\", 3)" : "");
                }
            }
            case TEXT -> {
                if (text == null) {
                    return op.code() + " needs a message between quotes, like " + op.code() + "(\"Someone is at the door\")";
                }
            }
            case NAME -> {
                String problem = nameProblem(text);
                if (problem != null) {
                    return problem;
                }
            }
            case VARIABLE -> {
                String problem = variableProblem(text, java.util.Set.of());
                if (problem != null) {
                    return problem;
                }
            }
            default -> {
                if (text != null) {
                    return op.code() + " does not take a name";
                }
            }
        }
        if (text != null && text.length() > Op.MAX_TEXT) {
            return "That text is too long (at most " + Op.MAX_TEXT + " letters)";
        }
        switch (op.numberArg()) {
            case SECONDS, OPTIONAL_SECONDS -> {
                if (number == null) {
                    if (op.numberArg() == Op.Arg.OPTIONAL_SECONDS) {
                        return null;
                    }
                    return op.code() + " needs a number of seconds, like " + op.code() + "(" + (op == Op.EVERY ? "10" : "2") + ")";
                }
                if (number < op.minSeconds() || number > op.maxSeconds()) {
                    return op.code() + " takes " + Expr.number(op.minSeconds()) + " to " + Expr.number(op.maxSeconds())
                            + " seconds, not " + Expr.number(number);
                }
            }
            default -> {
                if (number != null) {
                    return op.code() + " does not take a number";
                }
            }
        }
        return null;
    }

    /**
     * Zoals {@link #argsProblem(Op, String, Double)}, maar een vakje kan ook een berekening zijn
     * ({@code expr}, hoofdstuk 20): dan is het bericht of het aantal seconden er wel, alleen pas bij het
     * uitvoeren bekend.
     */
    public static @Nullable String argsProblem(Op op, @Nullable String text, @Nullable Double number, String expr) {
        if (!expr.isEmpty()) {
            if (exprIsText(op)) {
                text = text == null ? "" : text;
            } else {
                number = op.defaultSeconds();
            }
        }
        return argsProblem(op, text, number);
    }

    /** Is de berekening van dit blokje het bericht (say, log, alarm)? Anders zijn het de seconden (wait, pulse). */
    public static boolean exprIsText(Op op) {
        return op.textArg() == Op.Arg.TEXT;
    }

    /** Zo lang mag de naam van een def hooguit zijn. */
    public static final int MAX_NAME = 24;

    /** Woorden die de taal zelf al gebruikt (een def mag niet zo heten). */
    private static final java.util.Set<String> RESERVED = java.util.Set.of("when", "if", "elif", "else", "pass", "and",
            "or", "not", "def", "true", "false", "True", "False", "return", "is_on", "code", "is_night", "is_day", "call");

    /**
     * Mag een def zo heten? Net als in Python: letters, cijfers en _ (niet met een cijfer beginnen),
     * en niet een woord dat de taal zelf al gebruikt. Geeft de fout, of null.
     */
    public static @Nullable String nameProblem(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return "Your own block needs a name, like def open_door():";
        }
        if (name.length() > MAX_NAME) {
            return "That name is too long (at most " + MAX_NAME + " letters)";
        }
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return "\"" + name + "\": a name may only have letters, digits and _ (and must not start with a digit), like open_door";
        }
        if (RESERVED.contains(name) || Op.byCode(name) != null) {
            return "\"" + name + "\" is already a word of the language: pick another name, like my_" + name;
        }
        return null;
    }

    /**
     * Mag een naam (een variabele, hoofdstuk 20) zo heten? Net als een def: letters, cijfers en _ (niet
     * met een cijfer beginnen), geen woord van de taal, geen gegeven van E.D.I.T.H. (zoals
     * {@code card.level}), en niet de naam van een van je eigen blokken. Geeft de fout, of null.
     */
    public static @Nullable String variableProblem(@Nullable String name, java.util.Collection<String> defNames) {
        if (name == null || name.isBlank()) {
            return "A name needs letters, like count";
        }
        if (name.contains(".")) {
            return Expr.Variable.byName(name) != null
                    ? name + " is information from E.D.I.T.H.: you can read it, but not give it a value"
                    : "\"" + name + "\": a name may not have a dot (only E.D.I.T.H.'s own information has one, like card.level)";
        }
        if (name.length() > MAX_NAME) {
            return "That name is too long (at most " + MAX_NAME + " letters)";
        }
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return "\"" + name + "\": a name may only have letters, digits and _ (and must not start with a digit), like count";
        }
        if (RESERVED.contains(name) || Op.byCode(name) != null || Expr.Variable.byName(name) != null) {
            return "\"" + name + "\" is already a word of the language: pick another name, like my_" + name;
        }
        if (defNames.contains(name)) {
            return "\"" + name + "\" is one of your own blocks (def): pick another name";
        }
        return null;
    }

    /** Mag deze actie onder deze trigger? accept() en deny() alleen na een lezer of paneel. */
    public static @Nullable String contextProblem(Op action, @Nullable Op trigger) {
        if ((action == Op.ACCEPT || action == Op.DENY) && trigger != null && !trigger.hasSourceDevice()) {
            return action.code() + "() only works inside when keycard(...) or when codepanel(...)";
        }
        return null;
    }

    /**
     * Een naam in de code die op een bekend blokje of een van je eigen defs lijkt (voor "Did you mean
     * on?"), of null.
     */
    public static @Nullable String suggest(String word, java.util.Collection<String> ownNames) {
        java.util.List<String> known = new java.util.ArrayList<>(ownNames);
        for (Op op : Op.values()) {
            // Blokjes die je niet als naam(...) typt, raden we niet.
            if (op != Op.DEF && op != Op.CALL && op != Op.IF && op.kind() != Op.Kind.SET) {
                known.add(op.code());
            }
        }
        return closest(word, known);
    }

    /** De naam uit {@code names} die het meest op {@code word} lijkt (hooguit 2 letters anders), of null. */
    public static @Nullable String closest(String word, java.util.Collection<String> names) {
        String best = null;
        int bestDistance = 3;
        for (String name : names) {
            int distance = distance(word.toLowerCase(java.util.Locale.ROOT), name.toLowerCase(java.util.Locale.ROOT));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = name;
            }
        }
        return best;
    }

    /** Hoeveel letters je moet veranderen om van a naar b te komen (de "Levenshtein-afstand"). */
    private static int distance(String a, String b) {
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
