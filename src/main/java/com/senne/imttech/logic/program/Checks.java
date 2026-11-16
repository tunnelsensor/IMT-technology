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
            // goto(x, y, z): alles staat in expr. Of het er genoeg zijn, zegt valuesProblem. (aim() mag leeg.)
            return expr.isBlank() && op.requiredValues() > 0 ? op.code() + " needs " + valuesCount(op) + " between the brackets, like " + op.valuesExample()
                    : null;
        }
        if (op == Op.DEF || op == Op.CALL) {
            // Hoofdstuk 43: bij een def staan de parameters in expr, bij een aanroep de waarden. Zie exprProblem.
            return argsProblem(op, text, number);
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

    /**
     * Zoals {@link #argsProblem(Op, String, Double, String)}, maar het apparaat (of de robot) mag ook code zijn
     * ({@code textExpr}, hoofdstuk 43: {@code pulse(door, 3)}): dan is de naam er wel, alleen pas bij het uitvoeren
     * bekend.
     */
    public static @Nullable String argsProblem(Op op, @Nullable String text, @Nullable Double number, String expr, String textExpr) {
        if (!textExpr.isBlank() && (op.textArg() == Op.Arg.DEVICE || op.textArg() == Op.Arg.ROBOT)) {
            String problem = textExprProblem(op, textExpr);
            if (problem != null) {
                return problem;
            }
            return argsProblem(op, textExpr, number, expr);
        }
        return argsProblem(op, text, number, expr);
    }

    /** Is de berekening van dit blokje het bericht (say, log, alarm)? Anders zijn het de seconden (wait, pulse). */
    public static boolean exprIsText(Op op) {
        return op.textArg() == Op.Arg.TEXT;
    }

    /**
     * Klopt het rijtje waarden van een VALUES-blokje (hoofdstuk 23)? Genoeg waarden ({@code goto} 3,
     * {@code write} 2, {@code order} 2 of 5, {@code aim} 0 of 3), en van de goede soort (een plek zijn getallen, een robot en een
     * opdracht zijn tekst). Een naam waarvan we de soort nog niet weten (ANY) mag altijd.
     */
    public static @Nullable String valuesProblem(Op op, java.util.List<Expr> values) {
        String[] names = op.valueNames();
        Expr.Type[] types = op.valueTypes();
        if (!op.acceptsValues(values.size())) {
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
        if (op == Op.TAKE || op == Op.PUT) {
            return "at most " + all + " values (" + String.join(", ", op.valueNames()) + ")"; // hoofdstuk 113: 0, 1 of 2
        }
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
            if (op == Op.DEF) {
                // Hoofdstuk 43: de parameters van een def (sinds hoofdstuk 114: string door, int times).
                return paramsProblem(parseParams(expr), java.util.Set.of());
            }
            if (op == Op.CALL) {
                Expr.parseLenientList(expr); // de waarden voor de parameters; of het er genoeg zijn, zegt ScriptChecker
                return null;
            }
            Expr.parseLenient(expr, false);
            return null;
        } catch (LogicException e) {
            return e.getMessage();
        }
    }

    /**
     * Is de naam van het apparaat of de robot als code te lezen ({@code textExpr}, hoofdstuk 43: {@code pulse(door, 3)}
     * met {@code door} een parameter)? Geeft de fout, of null.
     */
    public static @Nullable String textExprProblem(Op op, String textExpr) {
        if (textExpr.isBlank()) {
            return null;
        }
        try {
            Expr.Type type = Expr.typeOf(Expr.parseLenient(textExpr, false));
            if (type != Expr.Type.TEXT && type != Expr.Type.ANY) {
                return op.code() + " needs the name of a " + (op.textArg() == Op.Arg.ROBOT ? "robot" : "device")
                        + " (text), not " + Expr.describe(type);
            }
            return null;
        } catch (LogicException e) {
            return e.getMessage();
        }
    }

    /** Zo lang mag de naam van een def hooguit zijn. */
    public static final int MAX_NAME = 24;

    /**
     * Woorden die de taal zelf al gebruikt (een def mag niet zo heten). Sinds 16 oktober ook {@code in} en {@code range}
     * (van {@code for i in range(3):}), en {@code repeat} en {@code forever}: die bestaan niet meer, maar zo zegt de code
     * wat je nu schrijft in plaats van "I don't know repeat".
     */
    private static final java.util.Set<String> RESERVED = java.util.Set.of("when", "if", "elif", "else", "pass", "and",
            "or", "not", "def", "true", "false", "True", "False", "return", "is_on", "code", "is_night", "is_day", "call",
            "in", "range", "repeat", "forever",
            // Hoofdstuk 43: het woord global, en de soort-woorden.
            "global", "int", "float", "string", "bool");

    // ================================================================ Parameters en aanroepen (hoofdstuk 43)

    /**
     * Leest de parameters van een def zoals ze in het vak Parameters van een DEF-blokje staan: {@code string door, int times}
     * (of alleen {@code door}). Gooit een LogicException met uitleg als het niet klopt.
     */
    public static java.util.List<Script.Param> parseParams(String text) {
        if (text.isBlank()) {
            return new java.util.ArrayList<>();
        }
        java.util.List<Lexer.Token> tokens = Lexer.tokens(text);
        return parseParams(tokens, 0, tokens.size() - 1, "open_door");
    }

    /**
     * Leest de parameters tussen {@code from} en {@code to} (de {@code )} of het einde, niet meegeteld). Sinds hoofdstuk 114
     * zoals in C# (jouw wens): eerst het soort-woord, dan de naam, zonder dubbele punt: {@code string door, int times}. Het
     * soort-woord mag weg ({@code door}): dan past elke waarde. {@code def} is de naam van de def, voor de voorbeelden in
     * de uitleg. Gooit een LogicException (zonder regelnummer) als het niet klopt.
     */
    static java.util.List<Script.Param> parseParams(java.util.List<Lexer.Token> tokens, int from, int to, String def) {
        java.util.List<Script.Param> params = new java.util.ArrayList<>();
        int i = from;
        while (i < to) {
            Lexer.Token first = tokens.get(i);
            if (first.type() != Lexer.Type.NAME) {
                throw new LogicException(first.is(",") ? "A parameter is missing before the comma"
                        : "A parameter needs a name, like def " + def + "(string door): (not " + first + ")");
            }
            String type = "";
            Lexer.Token name = first;
            Lexer.Token next = tokens.get(i + 1);
            if (VarType.byWord(first.text()) != null) {
                // string door: het soort-woord, dan de naam.
                if (next.type() != Lexer.Type.NAME) {
                    throw new LogicException("After " + first.text() + " comes the name of the parameter, like def " + def + "("
                            + first.text() + " " + exampleName(VarType.byWord(first.text())) + "):");
                }
                type = first.text();
                name = next;
                i += 2;
            } else if (next.is(":")) {
                // door: string, zoals vóór hoofdstuk 114 (en in Python): zeg hoe het nu moet.
                Lexer.Token word = tokens.get(i + 2);
                VarType known = word.type() == Lexer.Type.NAME ? VarType.byWord(word.text()) : null;
                throw new LogicException(known != null
                        ? "Write the type before the name, like in C#: def " + def + "(" + known.word() + " " + first.text() + "):"
                        : "Write the type before the name, without a colon, like def " + def + "(string " + first.text()
                        + "): the types are " + VarType.words());
            } else if (next.type() == Lexer.Type.NAME) {
                // strng door: twee woorden, maar het eerste is geen soort.
                throw new LogicException("I don't know the type \"" + first.text() + "\": use " + VarType.words() + ", like def "
                        + def + "(string " + next.text() + "):");
            } else {
                i++;
            }
            if (i < to && tokens.get(i).is("=")) {
                Script.Param example = new Script.Param(name.text(), type.isEmpty() ? "string" : type);
                throw new LogicException("A parameter gets its value when you use the def, not here: write def " + def + "("
                        + example.code() + "): and then " + def + "(" + exampleValue(example) + ")");
            }
            params.add(new Script.Param(name.text(), type));
            if (i == to) {
                break;
            }
            if (!tokens.get(i).is(",")) {
                throw new LogicException("Separate the parameters with a comma, like def " + def + "(string door, int times):");
            }
            i++;
            if (i == to) {
                throw new LogicException("A parameter is missing after the comma");
            }
        }
        return params;
    }

    /** Een voorbeeldnaam voor een parameter van deze soort, voor de uitleg: {@code int times}, {@code string door}. */
    private static String exampleName(VarType type) {
        return switch (type) {
            case INT -> "times";
            case FLOAT -> "secs";
            case STRING -> "door";
            case BOOL -> "open";
        };
    }

    /** Mogen deze parameters zo heten (zoals een naam), en staat er geen naam twee keer? Geeft de fout, of null. */
    public static @Nullable String paramsProblem(java.util.List<Script.Param> params, java.util.Collection<String> defNames) {
        return paramsProblem(params, defNames, null);
    }

    /** Zoals hierboven, voor deze soort programma (zie {@link #isWord(String, ProgramKind)}). */
    public static @Nullable String paramsProblem(java.util.List<Script.Param> params, java.util.Collection<String> defNames,
                                                 @Nullable ProgramKind kind) {
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (Script.Param param : params) {
            String problem = variableProblem(param.name(), defNames, kind);
            if (problem != null) {
                return problem;
            }
            if (!seen.add(param.name())) {
                return "The parameter " + param.name() + " is there twice";
            }
        }
        return null;
    }

    /** Een voorbeeldwaarde voor een parameter van deze soort, voor de foutmeldingen. */
    static String exampleValue(Script.Param param) {
        VarType type = param.varType();
        if (type == null) {
            return "1";
        }
        return switch (type) {
            case INT -> "3";
            case FLOAT -> "1.5";
            case STRING -> "\"Lab door\"";
            case BOOL -> "true";
        };
    }

    /** Hoe je deze def aanroept, voor de foutmeldingen: {@code open_door("Lab door", 3)}. */
    public static String callExample(Script.Function function) {
        StringBuilder example = new StringBuilder(function.name()).append('(');
        for (Script.Param param : function.params()) {
            example.append(example.charAt(example.length() - 1) == '(' ? "" : ", ").append(exampleValue(param));
        }
        return example.append(')').toString();
    }

    /**
     * Kloppen de waarden waarmee je een def aanroept: evenveel als hij parameters heeft, en van de goede soort? Een
     * waarde waarvan we de soort nog niet weten (ANY) mag altijd. Geeft de fout, of null.
     */
    public static @Nullable String callProblem(Script.Function function, java.util.List<Expr> args) {
        java.util.List<Script.Param> params = function.params();
        if (args.size() != params.size()) {
            if (params.isEmpty()) {
                return function.name() + "() takes nothing between the brackets";
            }
            String count = params.size() == 1 ? "1 value" : params.size() + " values";
            return function.name() + " needs " + count + " (" + function.paramsCode() + "), like " + callExample(function);
        }
        for (int i = 0; i < args.size(); i++) {
            VarType type = params.get(i).varType();
            if (type == null) {
                continue;
            }
            Expr arg = args.get(i);
            Double literal = arg instanceof Expr.Num num ? num.value() : null;
            if (!type.accepts(Expr.typeOf(arg), literal)) {
                return function.name() + ": " + params.get(i).name() + " must be " + describeType(type) + ", like "
                        + callExample(function);
            }
        }
        return null;
    }

    /** Een soort-woord in begrijpelijk Engels: "a whole number (int)", "text (string)". */
    public static String describeType(VarType type) {
        return switch (type) {
            case INT -> "a whole number (int)";
            case FLOAT -> "a number (float)";
            case STRING -> "text (string)";
            case BOOL -> "true or false (bool)";
        };
    }

    /**
     * Klopt het bereik van een for-lus (16 oktober)? Eén of twee getallen: {@code range(3)} telt 0, 1, 2 en
     * {@code range(1, 4)} telt 1, 2, 3. Een naam waarvan we de soort nog niet weten (ANY) mag. Geeft de fout, of null.
     */
    public static @Nullable String rangeProblem(java.util.List<Expr> bounds) {
        if (bounds.isEmpty() || bounds.size() > 2) {
            return "range takes one or two numbers: range(3) counts 0, 1, 2 and range(1, 4) counts 1, 2, 3";
        }
        for (Expr bound : bounds) {
            Expr.Type type = Expr.typeOf(bound);
            if (type != Expr.Type.NUMBER && type != Expr.Type.ANY) {
                return "range needs numbers, like range(3) or range(1, count + 1)";
            }
        }
        return null;
    }

    /**
     * Een leeg vakje na een komma (16 november): {@code pulse("Lamp", )}, {@code range(1, )} of {@code read(1, )}. Tab vult
     * {@code pulse("", )} in zonder voorbeeldgetal; laat je dat vakje leeg, dan zegt Check dit (en niet "I did not expect )
     * here").
     */
    public static String emptyAfterComma(String name) {
        return "Something is missing after the comma in " + name + "(...): fill it in, or remove the comma";
    }

    /**
     * Mag een def zo heten? Net als in Python: letters, cijfers en _ (niet met een cijfer beginnen),
     * en niet een woord dat de taal zelf al gebruikt. Geeft de fout, of null.
     */
    public static @Nullable String nameProblem(@Nullable String name) {
        return nameProblem(name, null);
    }

    /** Zoals hierboven, voor deze soort programma (zie {@link #isWord(String, ProgramKind)}). */
    public static @Nullable String nameProblem(@Nullable String name, @Nullable ProgramKind kind) {
        if (name == null || name.isBlank()) {
            return "Your own block needs a name, like def open_door():";
        }
        if (name.length() > MAX_NAME) {
            return "That name is too long (at most " + MAX_NAME + " letters)";
        }
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return "\"" + name + "\": a name may only have letters, digits and _ (and must not start with a digit), like open_door";
        }
        if (isWord(name, kind)) {
            return "\"" + name + "\" is already a word of the language: pick another name, like my_" + name;
        }
        return null;
    }

    /**
     * Is dit in elke soort programma al een woord van de taal: een sleutelwoord, een blokje ({@code forward}) of een functie
     * ({@code read})? De woorden die alleen een RC Car kent ({@code take}, {@code put}, {@code check}..., hoofdstuk 113)
     * tellen hier niet mee: zie {@link #isWord(String, ProgramKind)}.
     */
    public static boolean isWord(String name) {
        return isWord(name, null);
    }

    /**
     * Is dit in deze soort programma al een woord van de taal? Hoofdstuk 113: de nieuwe woorden van de RC Car zijn nergens
     * bezet (zie Op.reservedIn), zodat een programma van vóór die update met {@code def check():} of een naam {@code item}
     * blijft kloppen, ook het programma van een auto. {@code kind} blijft erbij voor woorden die maar in één soort bezet
     * zouden zijn; null = alleen wat overal bezet is.
     */
    public static boolean isWord(String name, @Nullable ProgramKind kind) {
        if (RESERVED.contains(name)) {
            return true;
        }
        Op op = Op.byCode(name);
        if (op != null && op.reservedIn(kind)) {
            return true;
        }
        Expr.Function function = Expr.Function.byName(name);
        return function != null && function.reserved();
    }

    /**
     * Mag een naam (een variabele, hoofdstuk 20) zo heten? Net als een def: letters, cijfers en _ (niet
     * met een cijfer beginnen), geen woord van de taal, geen gegeven van I.M.T (zoals
     * {@code card.number}), en niet de naam van een van je eigen blokken. Geeft de fout, of null.
     */
    public static @Nullable String variableProblem(@Nullable String name, java.util.Collection<String> defNames) {
        return variableProblem(name, defNames, null);
    }

    /** Zoals hierboven, voor deze soort programma (zie {@link #isWord(String, ProgramKind)}). */
    public static @Nullable String variableProblem(@Nullable String name, java.util.Collection<String> defNames,
                                                   @Nullable ProgramKind kind) {
        if (name == null || name.isBlank()) {
            return "A name needs letters, like count";
        }
        if (name.contains(".")) {
            return Expr.Variable.byName(name) != null
                    ? name + " is built-in information: you can read it, but not give it a value"
                    : "\"" + name + "\": a name may not have a dot (only built-in information has one, like card.number)";
        }
        if (name.length() > MAX_NAME) {
            return "That name is too long (at most " + MAX_NAME + " letters)";
        }
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            return "\"" + name + "\": a name may only have letters, digits and _ (and must not start with a digit), like count";
        }
        if (isWord(name, kind) || Expr.Variable.byName(name) != null) {
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
