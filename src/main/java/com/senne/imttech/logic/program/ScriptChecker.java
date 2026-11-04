package com.senne.imttech.logic.program;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Controles op een heel diagram, voor alles wat met je eigen blokken (def) te maken heeft. De code
 * (ScriptParser) en de blokjes (LogicGraph.problems) gebruiken allebei deze controles, zodat ze
 * dezelfde fouten met dezelfde woorden geven.
 * <ul>
 *   <li>twee defs met dezelfde naam (ook: dezelfde naam als een def uit een global script);</li>
 *   <li>een def gebruiken die niet bestaat ("Did you mean open_lab?");</li>
 *   <li>een def die (via andere defs) zichzelf gebruikt: die zou nooit stoppen;</li>
 *   <li>een def die iets gebruikt dat niet past bij de when die hem gebruikt: {@code card.valid}
 *       alleen vanuit {@code when keycard(...)}, {@code code} alleen vanuit {@code when codepanel(...)},
 *       {@code accept()} en {@code deny()} alleen vanuit een lezer of paneel, {@code player...} ook vanuit een
 *       netwerkknop of -hendel (hoofdstuk 38), en {@code lever.on} alleen vanuit {@code when lever(...)};</li>
 *   <li>hoofdstuk 43: een def aanroepen met te weinig of de verkeerde waarden, een naam met een soort-woord die iets
 *       anders krijgt ({@code count: int = 2.5}), een gedeelde naam die een diagram zelf nog eens maakt, en een global
 *       script met een when erin.</li>
 * </ul>
 * Een fout hoort bij {@code ref}: een regel in de code, of een blokje in de blokjes.
 */
public final class ScriptChecker {
    private ScriptChecker() {}

    /** Wat er mis is, en waar (regel of blokje). */
    public record Problem(int ref, String message) {}

    /**
     * Past elk blokje en elk gegeven bij de soort programma? {@code keycard()} kent alleen de server; straks kent
     * alleen een robot {@code forward()} of {@code robot.battery}.
     */
    private static void checkKind(Script script, ProgramKind kind, List<Problem> problems) {
        for (Script.Top item : script.items()) {
            if (item instanceof Script.Handler handler && !handler.trigger().allowedIn(kind)) {
                problems.add(new Problem(handler.ref(), wrongKind(triggerWord(handler.trigger()), kind, handler.trigger()::allowedIn)));
            }
            if (item instanceof Script.Global global) {
                checkKind(global.value(), false, global.ref(), kind, problems);
            }
            checkKind(item.body(), kind, problems);
        }
    }

    private static void checkKind(List<Script.Stmt> body, ProgramKind kind, List<Problem> problems) {
        for (Script.Stmt statement : body) {
            switch (statement) {
                case Script.If branch -> {
                    checkKind(branch.condition(), true, branch.ref(), kind, problems);
                    checkKind(branch.yes(), kind, problems);
                    checkKind(branch.no(), kind, problems);
                }
                case Script.Loop loop -> {
                    checkKind(loop.expr(), loop.op() == Op.WHILE, loop.ref(), kind, problems);
                    checkKind(loop.body(), kind, problems);
                }
                case Script.Assign assign -> checkKind(assign.value(), false, assign.ref(), kind, problems);
                case Script.Action action -> {
                    if (!action.op().allowedIn(kind)) {
                        problems.add(new Problem(action.ref(), wrongKind(action.op().code() + "()", kind, action.op()::allowedIn)));
                    }
                    checkKind(action.expr(), false, action.ref(), kind, problems);
                    checkKind(action.textExpr(), false, action.ref(), kind, problems);
                }
                default -> { }
            }
        }
    }

    /**
     * De gegevens en functies in een voorwaarde of waarde (of een rijtje waarden, zoals bij goto): passen ze bij
     * de soort programma?
     */
    private static void checkKind(String text, boolean condition, int ref, ProgramKind kind, List<Problem> problems) {
        List<Expr> exprs = lenient(text, condition);
        for (Expr expr : exprs) {
            for (String name : Expr.builtins(expr)) {
                Expr.Variable variable = Expr.Variable.byName(name);
                if (variable != null && !variable.allowedIn(kind)) {
                    problems.add(new Problem(ref, wrongKind(name, kind, variable::allowedIn)));
                    return;
                }
            }
            for (Expr.Function function : Expr.functions(expr)) {
                if (!function.allowedIn(kind)) {
                    problems.add(new Problem(ref, wrongKind(function.code() + "(...)", kind, function::allowedIn)));
                    return;
                }
            }
        }
    }

    /**
     * Soepel gelezen: een voorwaarde, of een waarde (of een rijtje waarden met komma's, zoals bij goto). Leeg
     * als hij niet te lezen is: dat zeggen de andere controles al.
     */
    private static List<Expr> lenient(String text, boolean condition) {
        if (text.isBlank()) {
            return List.of();
        }
        try {
            return condition ? List.of(Expr.parseLenient(text, true)) : Expr.parseLenientList(text);
        } catch (LogicException e) {
            return List.of();
        }
    }

    /** Hoe we een trigger in een foutmelding noemen: "keycard(...)", of "when started:" (zonder haakjes). */
    static String triggerWord(Op trigger) {
        return trigger.withoutBrackets() ? "when " + trigger.code() + ":" : trigger.code() + "(...)";
    }

    /**
     * "keycard(...) only works in a server diagram, not in a robot program", of (hoofdstuk 25) "forward() only works
     * in a robot program or a drone program, not in a server diagram". {@code allowed} zegt in welke soorten het wel werkt.
     */
    static String wrongKind(String what, ProgramKind kind, java.util.function.Predicate<ProgramKind> allowed) {
        List<String> where = new ArrayList<>();
        for (ProgramKind other : ProgramKind.values()) {
            if (other != kind && allowed.test(other)) {
                where.add("a " + other.noun());
            }
        }
        return what + " only works in " + (where.isEmpty() ? "another kind of program" : String.join(" or ", where))
                + ", not in a " + kind.noun();
    }

    /** Iets dat een def gebruikt maar niet overal werkt: wat het is, en waar het wel werkt. */
    private record Need(String what, String where) {}

    /** De controles voor een diagram op de server. */
    public static List<Problem> check(Script script) {
        return check(script, ProgramKind.SERVER);
    }

    /** De controles, voor deze soort programma (hoofdstuk 23: een robot kent andere woorden dan de server). */
    public static List<Problem> check(Script script, ProgramKind kind) {
        return check(script, kind, Library.EMPTY);
    }

    /**
     * De controles, met de defs en gedeelde namen van de global scripts erbij ({@code library}, hoofdstuk 43): die mag
     * dit diagram gebruiken, maar niet zelf nog eens maken.
     */
    public static List<Problem> check(Script script, ProgramKind kind, Library library) {
        List<Problem> problems = new ArrayList<>();
        checkKind(script, kind, problems);
        // Een global script (hoofdstuk 43): alleen defs en gedeelde namen.
        if (script.global()) {
            for (Script.Handler handler : script.handlers()) {
                problems.add(new Problem(handler.ref(), "A global script holds only defs and shared names (like lockdown: bool = false):"
                        + " put this when in another diagram"));
            }
            if (kind != ProgramKind.SERVER) {
                problems.add(new Problem(script.items().isEmpty() ? 0 : script.items().getFirst().ref(),
                        "A global script is a server diagram: choose Server diagram for it"));
            }
        }
        Map<String, Script.Function> functions = new LinkedHashMap<>();
        for (Script.Function function : script.functions()) {
            if (functions.containsKey(function.name())) {
                problems.add(new Problem(function.ref(), "There already is a def " + function.name()
                        + ": give this one another name"));
            } else if (library.def(function.name()) != null) {
                problems.add(new Problem(function.ref(), "There already is a def " + function.name() + " in your global script "
                        + library.def(function.name()).script() + ": give this one another name"));
            } else {
                functions.put(function.name(), function);
            }
            String problem = Checks.paramsProblem(function.params(), functions.keySet());
            if (problem != null) {
                problems.add(new Problem(function.ref(), problem));
            }
        }
        // Alle defs die dit diagram kan aanroepen: zijn eigen, en die van de global scripts.
        Map<String, Script.Function> all = new LinkedHashMap<>(library.functions());
        all.putAll(functions);
        // Een def gebruiken die er niet is.
        for (Script.Top item : script.items()) {
            forEachCall(item.body(), call -> {
                if (!all.containsKey(call.text())) {
                    String guess = Checks.suggest(call.text(), all.keySet());
                    problems.add(new Problem(call.ref(), "I don't know \"" + call.text() + "\""
                            + (guess != null ? ". Did you mean " + guess + "?" : ": make it with def " + call.text() + "():")));
                }
            });
        }
        // Een def die zichzelf gebruikt (ook via andere defs): die zou nooit stoppen.
        for (Script.Function function : functions.values()) {
            List<String> loop = loop(function.name(), function.name(), all, new HashSet<>(), new ArrayList<>());
            if (loop != null) {
                problems.add(new Problem(function.ref(), function.name() + "() uses itself ("
                        + String.join(" > ", loop) + "): that would never stop"));
            }
        }
        // Namen (variabelen) en de soort van elke waarde, en elke voorwaarde streng nagekeken.
        checkNames(script, all, kind, library, problems);
        // break alleen in een lus (in een def: in een lus van die def zelf).
        for (Script.Top item : script.items()) {
            checkBreaks(item.body(), false, problems);
        }
        // Past elke def bij de when die hem gebruikt?
        for (Script.Handler handler : script.handlers()) {
            forEachCall(handler.body(), call -> {
                if (all.containsKey(call.text())) {
                    Need need = need(call.text(), handler.trigger(), all, new HashSet<>());
                    if (need != null) {
                        problems.add(new Problem(call.ref(), call.text() + "() uses " + need.what()
                                + ", which only works inside " + need.where()));
                    }
                }
            });
        }
        // Een def uit een global script in een robot- of droneprogramma (hoofdstuk 43): past alles erin bij die soort?
        if (kind != ProgramKind.SERVER) {
            for (Script.Top item : script.items()) {
                forEachCall(item.body(), call -> {
                    Library.Def def = library.def(call.text());
                    if (def != null) {
                        List<Problem> inside = new ArrayList<>();
                        checkKind(def.function().body(), kind, inside);
                        if (!inside.isEmpty()) {
                            problems.add(new Problem(call.ref(), call.text() + "() (global script " + def.script() + ") uses "
                                    + inside.getFirst().message()));
                        }
                    }
                });
            }
        }
        problems.sort(Comparator.comparingInt(Problem::ref));
        return problems;
    }

    // ================================================================ Namen (hoofdstuk 20)

    /** Welke namen hier een waarde hebben: hun soort voor de uitdrukkingen, en (hoofdstuk 43) hun soort-woord als dat er is. */
    private record Scope(Map<String, Expr.Type> types, Map<String, VarType> declared) {
        Scope() {
            this(new LinkedHashMap<>(), new LinkedHashMap<>());
        }

        Scope copy() {
            return new Scope(new LinkedHashMap<>(types), new LinkedHashMap<>(declared));
        }

        boolean has(String name) {
            return types.containsKey(name);
        }

        void put(String name, Expr.Type type, @Nullable VarType declared) {
            types.put(name, type);
            if (declared != null) {
                this.declared.put(name, declared);
            }
        }
    }

    /**
     * Loopt het diagram af in de volgorde van de code en houdt bij welke namen er op elke plek een
     * waarde hebben, en van welke soort (getal, tekst, waar/onwaar). Zo kijken we elke if en elke
     * {@code naam = waarde} streng na:
     * <ul>
     *   <li>een naam helemaal links ({@code count = 0}) geldt overal; hij mag alleen getallen, tekst en
     *       andere namen van links gebruiken (geen {@code card.valid}: er is dan nog geen kaart);</li>
     *   <li>in een when of def bestaat een naam vanaf de regel waar hij zijn waarde krijgt; een def
     *       ziet de namen van de when die hem gebruikt niet (zoals een functie in Python), wel zijn parameters;</li>
     *   <li>een naam houdt zijn soort: wie een getal is, kan geen tekst worden; een int blijft een heel getal;</li>
     *   <li>de gedeelde namen van de global scripts (hoofdstuk 43) bestaan overal, en maak je niet nog eens.</li>
     * </ul>
     */
    private static void checkNames(Script script, Map<String, Script.Function> functions, ProgramKind kind, Library library,
                                   List<Problem> problems) {
        Set<String> defNames = functions.keySet();
        Scope globals = new Scope();
        // Eerst de gedeelde namen van de global scripts: die zijn er al.
        library.shared().forEach((name, shared) -> globals.put(name, shared.type().exprType(), shared.type()));
        for (Script.Global global : script.globals()) {
            String problem = Checks.variableProblem(global.name(), defNames);
            if (problem == null && library.sharedName(global.name()) != null && !script.global()) {
                Library.Shared shared = library.sharedName(global.name());
                problem = global.name() + " is a shared name from your global script " + shared.script()
                        + ": give it a value inside a when (" + global.name() + " = ...), or pick another name here";
            } else if (problem == null && globals.has(global.name())) {
                problem = "There already is a " + global.name() + " at the left edge: give it its value only once";
            }
            if (problem == null) {
                try {
                    Expr value = Expr.parseValue(global.value(), null, globals.types());
                    if (!Expr.variables(value).isEmpty()) {
                        problem = "At the left edge you can only use numbers, text and other names from the left edge"
                                + " (like count = 0): " + Expr.variables(value).iterator().next() + " only exists inside a when";
                    } else {
                        problem = typeProblem(global.name(), global.type(), value, null);
                        if (problem == null) {
                            VarType declared = VarType.byWord(global.type());
                            globals.put(global.name(), declared != null ? declared.exprType() : Expr.typeOf(value), declared);
                        }
                    }
                } catch (LogicException e) {
                    problem = e.getMessage();
                }
            }
            if (problem != null) {
                problems.add(new Problem(global.ref(), problem));
            }
        }
        for (Script.Top item : script.items()) {
            if (item instanceof Script.Handler handler) {
                checkNames(handler.body(), handler.trigger(), globals, new Scope(), functions, kind, problems);
            } else if (item instanceof Script.Function function) {
                // De parameters van een def (hoofdstuk 43) bestaan in zijn stappen, met hun soort (of ANY).
                Scope locals = new Scope();
                for (Script.Param param : function.params()) {
                    locals.put(param.name(), param.exprType(), param.varType());
                }
                checkNames(function.body(), null, globals, locals, functions, kind, problems);
            }
        }
    }

    /**
     * Past deze waarde bij het soort-woord (hoofdstuk 43)? {@code count: int = 2.5} niet, {@code lockdown: bool = 0}
     * niet. {@code old} is de soort die de naam al had (of null): een soort-woord mag die niet veranderen. Geeft de
     * fout, of null.
     */
    private static @Nullable String typeProblem(String name, String typeWord, Expr value, @Nullable Expr.Type old) {
        VarType declared = VarType.byWord(typeWord);
        if (declared == null) {
            return null;
        }
        if (old != null && old != Expr.Type.ANY && old != declared.exprType()) {
            return name + " already holds " + Expr.describe(old) + ": you cannot make it " + Checks.describeType(declared);
        }
        Double literal = value instanceof Expr.Num num ? num.value() : null;
        if (!declared.accepts(Expr.typeOf(value), literal)) {
            return name + " is " + Checks.describeType(declared) + ": you cannot give it "
                    + (literal != null && declared == VarType.INT ? Expr.number(literal) : Expr.describe(Expr.typeOf(value)))
                    + ", like " + name + ": " + typeWord + " = " + declared.emptyCode();
        }
        return null;
    }

    private static void checkNames(List<Script.Stmt> statements, @Nullable Op trigger, Scope globals, Scope locals,
                                   Map<String, Script.Function> functions, ProgramKind kind, List<Problem> problems) {
        for (Script.Stmt statement : statements) {
            if (statement instanceof Script.If branch) {
                try {
                    Expr.parseCondition(branch.condition(), trigger, scope(globals, locals), kind);
                } catch (LogicException e) {
                    problems.add(new Problem(branch.ref(), e.getMessage()));
                }
                // Een naam die in een tak een waarde krijgt, bestaat daarna ook (zoals in Python).
                checkNames(branch.yes(), trigger, globals, locals, functions, kind, problems);
                checkNames(branch.no(), trigger, globals, locals, functions, kind, problems);
            } else if (statement instanceof Script.Loop loop) {
                try {
                    if (loop.op() == Op.WHILE) {
                        Expr.parseCondition(loop.expr(), trigger, scope(globals, locals), kind);
                    } else if (loop.op() == Op.FOR) {
                        String problem = forProblem(loop, trigger, globals, locals, functions.keySet(), kind);
                        if (problem != null) {
                            throw new LogicException(problem);
                        }
                    }
                } catch (LogicException e) {
                    problems.add(new Problem(loop.ref(), e.getMessage()));
                }
                checkNames(loop.body(), trigger, globals, locals, functions, kind, problems);
            } else if (statement instanceof Script.Assign assign) {
                String problem = assignProblem(assign, trigger, globals, locals, functions.keySet(), kind);
                if (problem != null) {
                    problems.add(new Problem(assign.ref(), problem));
                }
            } else if (statement instanceof Script.Action action) {
                String problem = action.op() == Op.CALL ? callProblem(action, trigger, scope(globals, locals), functions, kind)
                        : actionProblem(action, trigger, scope(globals, locals), kind);
                if (problem != null) {
                    problems.add(new Problem(action.ref(), problem));
                }
            }
        }
    }

    /**
     * Een eigen blok aanroepen (hoofdstuk 43): de waarden streng gelezen, en dan of het er genoeg zijn en van de soort
     * die zijn parameters willen.
     */
    private static @Nullable String callProblem(Script.Action call, @Nullable Op trigger, Map<String, Expr.Type> scope,
                                                Map<String, Script.Function> functions, ProgramKind kind) {
        Script.Function function = functions.get(call.text());
        if (function == null) {
            return null; // dat zegt check() al
        }
        try {
            List<Expr> args = call.expr().isBlank() ? List.of() : Expr.parseValues(call.expr(), trigger, scope, kind);
            return Checks.callProblem(function, args);
        } catch (LogicException e) {
            return e.getMessage();
        }
    }

    /**
     * Een berekend bericht ({@code say("Count: " + count)}), berekende seconden ({@code wait(delay)}), of (hoofdstuk 43)
     * een apparaat als code ({@code pulse(door, 3)}): bestaan de namen, en zijn seconden een getal?
     */
    private static @Nullable String actionProblem(Script.Action action, @Nullable Op trigger, Map<String, Expr.Type> scope,
                                                  ProgramKind kind) {
        if (!action.textExpr().isEmpty()) {
            try {
                Expr.Type type = Expr.typeOf(Expr.parseValue(action.textExpr(), trigger, scope, kind));
                if (type != Expr.Type.TEXT && type != Expr.Type.ANY) {
                    return action.op().code() + " needs the name of a " + (action.op().textArg() == Op.Arg.ROBOT ? "robot" : "device")
                            + " (text), not " + Expr.describe(type);
                }
            } catch (LogicException e) {
                // on(Lamp): waarschijnlijk bedoelde je een apparaat.
                if (action.textExpr().matches("[A-Za-z_][A-Za-z0-9_]*") && e.getMessage().contains("has no value yet")) {
                    return e.getMessage() + ". If you meant a " + (action.op().textArg() == Op.Arg.ROBOT ? "robot" : "device")
                            + ", put it between quotes: " + action.op().code() + "(\"" + action.textExpr() + "\")";
                }
                return e.getMessage();
            }
        }
        if (action.expr().isEmpty()) {
            return null;
        }
        try {
            if (action.op().textArg() == Op.Arg.VALUES) {
                // goto(x, y, z): elke waarde streng gelezen, en dan of het er genoeg zijn en van de goede soort.
                return Checks.valuesProblem(action.op(), action.expr().isBlank() ? List.of()
                        : Expr.parseValues(action.expr(), trigger, scope, kind));
            }
            Expr.Type type = Expr.typeOf(Expr.parseValue(action.expr(), trigger, scope, kind));
            if (!Checks.exprIsText(action.op()) && type != Expr.Type.NUMBER && type != Expr.Type.ANY) {
                return action.op().code() + " needs a number of " + action.op().unit() + ", like " + action.op().code() + "("
                        + action.op().exampleNumber() + ")";
            }
            return null;
        } catch (LogicException e) {
            // say(Hello): waarschijnlijk bedoelde je tekst.
            if (Checks.exprIsText(action.op()) && action.expr().matches("[A-Za-z_][A-Za-z0-9_]*")
                    && e.getMessage().contains("has no value yet")) {
                return e.getMessage() + ". If you meant text, put it between quotes: " + action.op().code()
                        + "(\"" + action.expr() + "\")";
            }
            return e.getMessage();
        }
    }

    /**
     * Wat er mis is met {@code for i in range(...):} op deze plek (16 oktober): het bereik streng gelezen (één of twee
     * getallen), en de naam die telt. Die krijgt een getal, dus een naam die al tekst of waar/onwaar is, kan het niet.
     * Anders bestaat de naam vanaf hier (ook na de lus, zoals in Python).
     */
    private static @Nullable String forProblem(Script.Loop loop, @Nullable Op trigger, Scope globals, Scope locals,
                                               Set<String> defNames, ProgramKind kind) {
        String problem = Checks.variableProblem(loop.var(), defNames);
        if (problem != null) {
            return problem;
        }
        try {
            problem = Checks.rangeProblem(Expr.parseValues(loop.expr(), trigger, scope(globals, locals), kind));
        } catch (LogicException e) {
            return e.getMessage();
        }
        if (problem != null) {
            return problem;
        }
        Expr.Type old = globals.has(loop.var()) ? globals.types().get(loop.var()) : locals.types().get(loop.var());
        if (old == null) {
            locals.put(loop.var(), Expr.Type.NUMBER, VarType.INT);
        } else if (old != Expr.Type.NUMBER && old != Expr.Type.ANY) {
            return loop.var() + " holds " + Expr.describe(old) + ", so it cannot count: pick another name, like for i in range(3):";
        }
        return null;
    }

    /** Wat er mis is met {@code naam = waarde} (of {@code naam: soort = waarde}) op deze plek (en anders: de naam bestaat vanaf nu). */
    private static @Nullable String assignProblem(Script.Assign assign, @Nullable Op trigger, Scope globals, Scope locals,
                                                  Set<String> defNames, ProgramKind kind) {
        String name = assign.name();
        String problem = Checks.variableProblem(name, defNames);
        if (problem != null) {
            return problem;
        }
        Expr value;
        try {
            value = Expr.parseValue(assign.value(), trigger, scope(globals, locals), kind);
        } catch (LogicException e) {
            return e.getMessage();
        }
        Expr.Type type = Expr.typeOf(value);
        Scope owner = globals.has(name) ? globals : locals.has(name) ? locals : null;
        Expr.Type old = owner == null ? null : owner.types().get(name);
        problem = typeProblem(name, assign.type(), value, old);
        if (problem != null) {
            return problem;
        }
        VarType declared = VarType.byWord(assign.type());
        if (old == null) {
            locals.put(name, declared != null ? declared.exprType() : type, declared);
        } else if (old != type && old != Expr.Type.ANY && type != Expr.Type.ANY) {
            return name + " holds " + Expr.describe(old) + ", so you cannot give it " + Expr.describe(type);
        } else {
            // Een int blijft een heel getal (hoofdstuk 43): 2.5 past er niet in.
            VarType kept = owner.declared().get(name);
            Double literal = value instanceof Expr.Num num ? num.value() : null;
            if (kept == VarType.INT && literal != null && literal != Math.rint(literal)) {
                return name + " is a whole number (int): you cannot give it " + Expr.number(literal);
            }
        }
        return null;
    }

    /** De namen met een waarde op deze plek: die van links (en de gedeelde), en die van dit blok. */
    private static Map<String, Expr.Type> scope(Scope globals, Scope locals) {
        Map<String, Expr.Type> scope = new java.util.HashMap<>(globals.types());
        scope.putAll(locals.types());
        return scope;
    }

    /** Een break buiten een lus: die zou nergens heen kunnen. */
    private static void checkBreaks(List<Script.Stmt> statements, boolean inLoop, List<Problem> problems) {
        for (Script.Stmt statement : statements) {
            if (statement instanceof Script.Action action && action.op() == Op.BREAK && !inLoop) {
                problems.add(new Problem(action.ref(), "break only works inside for or while"));
            } else if (statement instanceof Script.If branch) {
                checkBreaks(branch.yes(), inLoop, problems);
                checkBreaks(branch.no(), inLoop, problems);
            } else if (statement instanceof Script.Loop loop) {
                checkBreaks(loop.body(), true, problems);
            }
        }
    }

    /** Elke keer dat een def gebruikt wordt (ook in de takken van een if en in een lus). */
    static void forEachCall(List<Script.Stmt> statements, Consumer<Script.Action> action) {
        for (Script.Stmt statement : statements) {
            if (statement instanceof Script.Action call && call.op() == Op.CALL) {
                action.accept(call);
            } else if (statement instanceof Script.If branch) {
                forEachCall(branch.yes(), action);
                forEachCall(branch.no(), action);
            } else if (statement instanceof Script.Loop loop) {
                forEachCall(loop.body(), action);
            }
        }
    }

    /**
     * Komt {@code current} (via de defs die hij gebruikt) weer bij {@code start} uit? Dan het pad
     * ernaartoe ("a > b > a"), anders null.
     */
    private static @Nullable List<String> loop(String start, String current, Map<String, Script.Function> functions,
                                               Set<String> seen, List<String> path) {
        Script.Function function = functions.get(current);
        if (function == null) {
            return null;
        }
        path.add(current);
        List<String> callees = new ArrayList<>();
        forEachCall(function.body(), call -> callees.add(call.text()));
        for (String callee : callees) {
            if (callee.equals(start)) {
                List<String> found = new ArrayList<>(path);
                found.add(start);
                return found;
            }
            if (seen.add(callee)) {
                List<String> found = loop(start, callee, functions, seen, path);
                if (found != null) {
                    return found;
                }
            }
        }
        path.removeLast();
        return null;
    }

    /**
     * Het eerste dat deze def (of een def die hij gebruikt) nodig heeft maar dat niet werkt onder
     * deze trigger, of null als alles past.
     */
    private static @Nullable Need need(String name, Op trigger, Map<String, Script.Function> functions, Set<String> seen) {
        Script.Function function = functions.get(name);
        if (function == null || !seen.add(name)) {
            return null;
        }
        return need(function.body(), trigger, functions, seen);
    }

    private static @Nullable Need need(List<Script.Stmt> statements, Op trigger, Map<String, Script.Function> functions,
                                       Set<String> seen) {
        for (Script.Stmt statement : statements) {
            Need need = null;
            if (statement instanceof Script.Action action) {
                if ((action.op() == Op.ACCEPT || action.op() == Op.DENY) && !trigger.hasSourceDevice()) {
                    need = new Need(action.op().code() + "()", "when keycard(...) or when codepanel(...)");
                } else if (action.op() == Op.CALL) {
                    need = need(action.text(), trigger, functions, seen);
                    if (need == null && !action.expr().isEmpty()) {
                        need = expressionNeed(action.expr(), false, trigger);
                    }
                } else if (!action.expr().isEmpty()) {
                    need = expressionNeed(action.expr(), false, trigger);
                }
                if (need == null && !action.textExpr().isEmpty()) {
                    need = expressionNeed(action.textExpr(), false, trigger);
                }
            } else if (statement instanceof Script.Assign assign) {
                need = expressionNeed(assign.value(), false, trigger);
            } else if (statement instanceof Script.Loop loop) {
                if (!loop.expr().isEmpty()) {
                    need = expressionNeed(loop.expr(), loop.op() == Op.WHILE, trigger);
                }
                if (need == null) {
                    need = need(loop.body(), trigger, functions, seen);
                }
            } else if (statement instanceof Script.If branch) {
                need = expressionNeed(branch.condition(), true, trigger);
                if (need == null) {
                    need = need(branch.yes(), trigger, functions, seen);
                }
                if (need == null) {
                    need = need(branch.no(), trigger, functions, seen);
                }
            }
            if (need != null) {
                return need;
            }
        }
        return null;
    }

    /** Gebruikt deze voorwaarde of waarde iets dat niet werkt onder deze trigger (zoals card.valid na every)? */
    private static @Nullable Need expressionNeed(String text, boolean condition, Op trigger) {
        for (Expr expr : lenient(text, condition)) {
            for (String name : Expr.variables(expr)) {
                Expr.Variable variable = Expr.Variable.byName(name);
                if (variable != null && !variable.allowedAfter(trigger)) {
                    String where = variable.where() != null ? variable.where() : "when keycard(...) or when codepanel(...)";
                    return new Need(name, where);
                }
            }
            // card.in_group(...) (25 oktober) hoort ook bij when keycard.
            for (Expr.Function function : Expr.functions(expr)) {
                if (!function.allowedAfter(trigger)) {
                    return new Need(function.code() + "(...)", function.where());
                }
            }
        }
        return null;
    }
}
