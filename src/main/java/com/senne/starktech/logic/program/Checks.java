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

    /** Mag deze actie onder deze trigger? accept() en deny() alleen na een lezer of paneel. */
    public static @Nullable String contextProblem(Op action, @Nullable Op trigger) {
        if ((action == Op.ACCEPT || action == Op.DENY) && trigger != null && !trigger.hasSourceDevice()) {
            return action.code() + "() only works inside when keycard(...) or when codepanel(...)";
        }
        return null;
    }

    /** Een naam in de code die op een bekend blokje lijkt (voor "Did you mean on?"), of null. */
    public static @Nullable String suggest(String word) {
        String best = null;
        int bestDistance = 3;
        for (Op op : Op.values()) {
            int distance = distance(word.toLowerCase(java.util.Locale.ROOT), op.code());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = op.code();
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
