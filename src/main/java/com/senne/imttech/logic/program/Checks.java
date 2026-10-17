package com.senne.imttech.logic.program;

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
                            + op.code() + "(\"Lab door\")" + (op.numberArg() == Op.Arg.OPTIONAL_NUMBER ? " or " + op.code() + "(\"Lab door\", 3)" : "");
                }
            }
            case TEXT -> {
                if (text == null) {
                    String what = op == Op.ORDERED ? "an order" : "a message";
                    return op.code() + " needs " + what + " between quotes, like " + op.code() + "(\"" + op.textExample() + "\")";
                }
            }
            case ROBOT -> {
                if (text == null || text.isBlank()) {
                    return op.code() + " needs the name of a robot between quotes, like " + op.code() + "(\"" + op.textExample() + "\")";
                }
            }
            case VALUES -> {
                // Een rijtje waarden staat in expr: zie valuesProblem.
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
            case NUMBER, OPTIONAL_NUMBER -> {
                if (number == null) {
                    if (op.numberArg() == Op.Arg.OPTIONAL_NUMBER) {
                        return null;
                    }
                    return op.code() + " needs a number of " + op.unit() + ", like " + op.code() + "(" + op.exampleNumber() + ")";
                }
                if (number < op.minNumber() || number > op.maxNumber()) {
                    return op.code() + " takes " + Expr.number(op.minNumber()) + " to " + Expr.number(op.maxNumber())
                            + " " + op.unit() + ", not " + Expr.number(number);
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
        if (op.textArg() == Op.Arg.VALUES) {
            // goto(x, y, z): alles staat in expr. Of het er genoeg zijn, zegt valuesProblem.
            return expr.isBlank() ? op.code() + " needs " + valuesCount(op) + " between the brackets, like " + op.valuesExample()
                    : null;
        }
        if (!expr.isEmpty()) {
            if (exprIsText(op)) {
                text = text == null ? "" : text;
            } else {
                number = op.defaultNumber();
            }
        }
        return argsProblem(op, text, number);
    }

    /** Is de berekening van dit blokje het bericht (say, log, alarm)? Anders zijn het de seconden (wait, pulse). */
    public static boolean exprIsText(Op op) {
        return op.textArg() == Op.Arg.TEXT;
    }

    /**
     * Klopt het rijtje waarden van een VALUES-blokje (hoofdstuk 23)? Genoeg waarden ({@code goto} 3,
     * {@code write} 2, {@code order} 2 of 5), en van de goede soort (een plek zijn getallen, een robot en een
     * opdracht zijn tekst). Een naam waarvan we de soort nog niet weten (ANY) mag altijd.
     */
    public static @Nullable String valuesProblem(Op op, java.util.List<Expr> values) {
        String[] names = op.valueNames();
        Expr.Type[] types = op.valueTypes();
        if (values.size() != op.requiredValues() && values.size() != names.length) {
            return op.code() + " needs " + valuesCount(op) + " between the brackets, like " + op.valuesExample();
        }
        for (int i = 0; i < values.size(); i++) {
            Expr.Type type = Expr.typeOf(values.get(i));
            if (types[i] != Expr.Type.ANY && type != Expr.Type.ANY && type != types[i]) {
                return op.code() + ": the " + names[i] + " must be " + Expr.describe(types[i]) + ", like " + op.valuesExample();
            }
        }
        return null;
    }

    /** "3 values (x, y, z)", "2 or 5 values (robot, order, x, y, z)". */
    private static String valuesCount(Op op) {
        int all = op.valueNames().length;
        String count = op.requiredValues() == all ? String.valueOf(all) : op.requiredValues() + " or " + all;
        return count + " values (" + String.join(", ", op.valueNames()) + ")";
    }

    /**
     * Is de berekening van dit blokje te lezen ({@code expr})? Bij een VALUES-blokje een rijtje, dat ook
     * moet kloppen (zie {@link #valuesProblem}). Geeft de fout, of null. Voor de blokjes.
     */
    public static @Nullable String exprProblem(Op op, String expr) {
        if (expr.isBlank()) {
            return null;
        }
        try {
            if (op.textArg() == Op.Arg.VALUES) {
                return valuesProblem(op, Expr.parseLenientList(expr));
            }
            Expr.parseLenient(expr, false);
            return null;
        } catch (LogicException e) {
            return e.getMessage();
        }
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
        if (isWord(name)) {
            return "\"" + name + "\" is already a word of the language: pick another name, like my_" + name;
        }
        return null;
    }

    /** Is dit al een woord van de taal: een sleutelwoord, een blokje ({@code forward}) of een functie ({@code read})? */
    public static boolean isWord(String name) {
        return RESERVED.contains(name) || Op.byCode(name) != null || Expr.Function.byName(name) != null;
    }

    /**
     * Mag een naam (een variabele, hoofdstuk 20) zo heten? Net als een def: letters, cijfers en _ (niet
     * met een cijfer beginnen), geen woord van de taal, geen gegeven van I.M.T (zoals
     * {@code card.level}), en niet de naam van een van je eigen blokken. Geeft de fout, of null.
     */
    public static @Nullable String variableProblem(@Nullable String name, java.util.Collection<String> defNames) {
        if (name == null || name.isBlank()) {
            return "A name needs letters, like count";
        }
        if (name.contains(".")) {
            return Expr.Variable.byName(name) != null
                    ? name + " is built-in information: you can read it, but not give it a value"
                    : "\"" + name + "\": a name may not have a dot (only built-in information has one, like card.level)";
        }
        if (name.length() > MAX_NAME) {
            return "That name is too long (at most " + MAX_NAME + " letters)";
        }
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return "\"" + name + "\": a name may only have letters, digits and _ (and must not start with a digit), like count";
        }
        if (isWord(name) || Expr.Variable.byName(name) != null) {
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
