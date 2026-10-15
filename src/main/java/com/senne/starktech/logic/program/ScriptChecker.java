package com.senne.starktech.logic.program;

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
 *   <li>twee defs met dezelfde naam;</li>
 *   <li>een def gebruiken die niet bestaat ("Did you mean open_lab?");</li>
 *   <li>een def die (via andere defs) zichzelf gebruikt: die zou nooit stoppen;</li>
 *   <li>een def die iets gebruikt dat niet past bij de when die hem gebruikt: {@code card.level}
 *       alleen vanuit {@code when keycard(...)}, {@code code} alleen vanuit {@code when codepanel(...)},
 *       {@code player...}, {@code accept()} en {@code deny()} alleen vanuit een lezer of paneel.</li>
 * </ul>
 * Een fout hoort bij {@code ref}: een regel in de code, of een blokje in de blokjes.
 */
public final class ScriptChecker {
    private ScriptChecker() {}

    /** Wat er mis is, en waar (regel of blokje). */
    public record Problem(int ref, String message) {}

    /** Iets dat een def gebruikt maar niet overal werkt: wat het is, en waar het wel werkt. */
    private record Need(String what, String where) {}

    public static List<Problem> check(Script script) {
        List<Problem> problems = new ArrayList<>();
        Map<String, Script.Function> functions = new LinkedHashMap<>();
        for (Script.Function function : script.functions()) {
            if (functions.containsKey(function.name())) {
                problems.add(new Problem(function.ref(), "There already is a def " + function.name()
                        + ": give this one another name"));
            } else {
                functions.put(function.name(), function);
            }
        }
        // Een def gebruiken die er niet is.
        for (Script.Top item : script.items()) {
            forEachCall(item.body(), call -> {
                if (!functions.containsKey(call.text())) {
                    String guess = Checks.suggest(call.text(), functions.keySet());
                    problems.add(new Problem(call.ref(), "I don't know \"" + call.text() + "\""
                            + (guess != null ? ". Did you mean " + guess + "?" : ": make it with def " + call.text() + "():")));
                }
            });
        }
        // Een def die zichzelf gebruikt (ook via andere defs): die zou nooit stoppen.
        for (Script.Function function : functions.values()) {
            List<String> loop = loop(function.name(), function.name(), functions, new HashSet<>(), new ArrayList<>());
            if (loop != null) {
                problems.add(new Problem(function.ref(), function.name() + "() uses itself ("
                        + String.join(" > ", loop) + "): that would never stop"));
            }
        }
        // Namen (variabelen) en de soort van elke waarde, en elke voorwaarde streng nagekeken.
        checkNames(script, functions.keySet(), problems);
        // break alleen in een lus (in een def: in een lus van die def zelf).
        for (Script.Top item : script.items()) {
            checkBreaks(item.body(), false, problems);
        }
        // Past elke def bij de when die hem gebruikt?
        for (Script.Handler handler : script.handlers()) {
            forEachCall(handler.body(), call -> {
                if (functions.containsKey(call.text())) {
                    Need need = need(call.text(), handler.trigger(), functions, new HashSet<>());
                    if (need != null) {
                        problems.add(new Problem(call.ref(), call.text() + "() uses " + need.what()
                                + ", which only works inside " + need.where()));
                    }
                }
            });
        }
        problems.sort(Comparator.comparingInt(Problem::ref));
        return problems;
    }

    // ================================================================ Namen (hoofdstuk 20)

    /**
     * Loopt het diagram af in de volgorde van de code en houdt bij welke namen er op elke plek een
     * waarde hebben, en van welke soort (getal, tekst, waar/onwaar). Zo kijken we elke if en elke
     * {@code naam = waarde} streng na:
     * <ul>
     *   <li>een naam helemaal links ({@code count = 0}) geldt overal; hij mag alleen getallen, tekst en
     *       andere namen van links gebruiken (geen {@code card.level}: er is dan nog geen kaart);</li>
     *   <li>in een when of def bestaat een naam vanaf de regel waar hij zijn waarde krijgt; een def
     *       ziet de namen van de when die hem gebruikt niet (zoals een functie in Python);</li>
     *   <li>een naam houdt zijn soort: wie een getal is, kan geen tekst worden.</li>
     * </ul>
     */
    private static void checkNames(Script script, Set<String> defNames, List<Problem> problems) {
        Map<String, Expr.Type> globals = new LinkedHashMap<>();
        for (Script.Global global : script.globals()) {
            String problem = Checks.variableProblem(global.name(), defNames);
            if (problem == null && globals.containsKey(global.name())) {
                problem = "There already is a " + global.name() + " at the left edge: give it its value only once";
            }
            if (problem == null) {
                try {
                    Expr value = Expr.parseValue(global.value(), null, globals);
                    if (!Expr.variables(value).isEmpty()) {
                        problem = "At the left edge you can only use numbers, text and other names from the left edge"
                                + " (like count = 0): " + Expr.variables(value).iterator().next() + " only exists inside a when";
                    } else {
                        globals.put(global.name(), Expr.typeOf(value));
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
                checkNames(handler.body(), handler.trigger(), globals, new java.util.HashMap<>(), defNames, problems);
            } else if (item instanceof Script.Function function) {
                checkNames(function.body(), null, globals, new java.util.HashMap<>(), defNames, problems);
            }
        }
    }

    private static void checkNames(List<Script.Stmt> statements, @Nullable Op trigger, Map<String, Expr.Type> globals,
                                   Map<String, Expr.Type> locals, Set<String> defNames, List<Problem> problems) {
        for (Script.Stmt statement : statements) {
            if (statement instanceof Script.If branch) {
                try {
                    Expr.parseCondition(branch.condition(), trigger, scope(globals, locals));
                } catch (LogicException e) {
                    problems.add(new Problem(branch.ref(), e.getMessage()));
                }
                // Een naam die in een tak een waarde krijgt, bestaat daarna ook (zoals in Python).
                checkNames(branch.yes(), trigger, globals, locals, defNames, problems);
                checkNames(branch.no(), trigger, globals, locals, defNames, problems);
            } else if (statement instanceof Script.Loop loop) {
                try {
                    if (loop.op() == Op.WHILE) {
                        Expr.parseCondition(loop.expr(), trigger, scope(globals, locals));
                    } else if (loop.op() == Op.REPEAT) {
                        Expr.Type type = Expr.typeOf(Expr.parseValue(loop.expr(), trigger, scope(globals, locals)));
                        if (type != Expr.Type.NUMBER && type != Expr.Type.ANY) {
                            throw new LogicException("repeat needs a number, like repeat(3):");
                        }
                    }
                } catch (LogicException e) {
                    problems.add(new Problem(loop.ref(), e.getMessage()));
                }
                checkNames(loop.body(), trigger, globals, locals, defNames, problems);
            } else if (statement instanceof Script.Assign assign) {
                String problem = assignProblem(assign, trigger, globals, locals, defNames);
                if (problem != null) {
                    problems.add(new Problem(assign.ref(), problem));
                }
            }
        }
    }

    /** Wat er mis is met {@code naam = waarde} op deze plek (en anders: de naam bestaat vanaf nu). */
    private static @Nullable String assignProblem(Script.Assign assign, @Nullable Op trigger, Map<String, Expr.Type> globals,
                                                  Map<String, Expr.Type> locals, Set<String> defNames) {
        String name = assign.name();
        String problem = Checks.variableProblem(name, defNames);
        if (problem != null) {
            return problem;
        }
        Expr.Type type;
        try {
            type = Expr.typeOf(Expr.parseValue(assign.value(), trigger, scope(globals, locals)));
        } catch (LogicException e) {
            return e.getMessage();
        }
        Expr.Type old = globals.containsKey(name) ? globals.get(name) : locals.get(name);
        if (old == null) {
            locals.put(name, type);
        } else if (old != type && old != Expr.Type.ANY && type != Expr.Type.ANY) {
            return name + " holds " + Expr.describe(old) + ", so you cannot give it " + Expr.describe(type);
        }
        return null;
    }

    /** De namen met een waarde op deze plek: die van links, en die van dit blok. */
    private static Map<String, Expr.Type> scope(Map<String, Expr.Type> globals, Map<String, Expr.Type> locals) {
        Map<String, Expr.Type> scope = new java.util.HashMap<>(globals);
        scope.putAll(locals);
        return scope;
    }

    /** Een break buiten een lus: die zou nergens heen kunnen. */
    private static void checkBreaks(List<Script.Stmt> statements, boolean inLoop, List<Problem> problems) {
        for (Script.Stmt statement : statements) {
            if (statement instanceof Script.Action action && action.op() == Op.BREAK && !inLoop) {
                problems.add(new Problem(action.ref(), "break only works inside forever, repeat or while"));
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

    /** Gebruikt deze voorwaarde of waarde iets dat niet werkt onder deze trigger (zoals card.level na every)? */
    private static @Nullable Need expressionNeed(String text, boolean condition, Op trigger) {
        Expr expr;
        try {
            expr = Expr.parseLenient(text, condition);
        } catch (LogicException e) {
            return null; // dat meldt een andere controle al
        }
        for (String name : Expr.variables(expr)) {
            Expr.Variable variable = Expr.Variable.byName(name);
            if (variable != null && !variable.allowedAfter(trigger)) {
                String where = variable.only != null ? "when " + variable.only.code() + "(...)"
                        : "when keycard(...) or when codepanel(...)";
                return new Need(name, where);
            }
        }
        return null;
    }
}
