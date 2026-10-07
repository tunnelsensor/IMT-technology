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

    /** Elke keer dat een def gebruikt wordt (ook in de takken van een if). */
    static void forEachCall(List<Script.Stmt> statements, Consumer<Script.Action> action) {
        for (Script.Stmt statement : statements) {
            if (statement instanceof Script.Action call && call.op() == Op.CALL) {
                action.accept(call);
            } else if (statement instanceof Script.If branch) {
                forEachCall(branch.yes(), action);
                forEachCall(branch.no(), action);
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
            } else if (statement instanceof Script.If branch) {
                need = conditionNeed(branch.condition(), trigger);
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

    /** Gebruikt deze voorwaarde iets dat niet werkt onder deze trigger (zoals card.level na every)? */
    private static @Nullable Need conditionNeed(String condition, Op trigger) {
        Expr expr;
        try {
            expr = Expr.parse(condition, null);
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
