package com.senne.starktech.logic.program;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Zet een diagram om van blokjes naar code en terug (jouw wens: "je kunt van blokken naar code en
 * code naar blokken").
 * <ul>
 *   <li><b>Blokjes naar code</b> ({@link #toScript}): elke trigger wordt een "when", in de volgorde
 *       waarin je ze maakte. Losse blokjes (zonder draadje naar een trigger) doen niets en vallen weg.</li>
 *   <li><b>Code naar blokjes</b> ({@link #toGraph}): de blokjes komen netjes op een rij: elke stap een
 *       kolom naar rechts, bij een if de "yes"-tak op dezelfde rij en de "no"-tak eronder, en wat na
 *       de if komt rechts van allebei. Een when-blok dat je in de code niet veranderde, houdt de plek
 *       die je zijn blokjes gaf.</li>
 * </ul>
 */
public final class GraphConverter {
    private GraphConverter() {}

    /** Zo ver uit elkaar staan de kolommen en rijen van de automatische indeling (pixels in de editor). */
    public static final int COL_W = 112;
    public static final int ROW_H = 34;
    private static final int MARGIN = 8;

    // ================================================================ Blokjes naar code

    public static Script toScript(LogicGraph graph) {
        List<LogicGraph.Node> triggers = new ArrayList<>();
        for (LogicGraph.Node node : graph.nodes()) {
            if (node.op.isTrigger()) {
                triggers.add(node);
            }
        }
        triggers.sort(Comparator.comparingInt(node -> node.id));
        List<Script.Handler> handlers = new ArrayList<>();
        for (LogicGraph.Node trigger : triggers) {
            handlers.add(new Script.Handler(trigger.op, trigger.text, trigger.number,
                    chain(graph, trigger.next, new HashSet<>()), trigger.id));
        }
        return new Script(handlers);
    }

    /** De rij blokjes vanaf {@code id} (via NEXT), met de takken van elke if erin. */
    private static List<Script.Stmt> chain(LogicGraph graph, int id, Set<Integer> seen) {
        List<Script.Stmt> statements = new ArrayList<>();
        while (id >= 0 && seen.add(id)) {
            LogicGraph.Node node = graph.node(id);
            if (node == null || node.op.isTrigger()) {
                break;
            }
            if (node.op == Op.IF) {
                statements.add(new Script.If(cleanCondition(node.text), chain(graph, node.yes, seen),
                        chain(graph, node.no, seen), node.id));
            } else {
                statements.add(new Script.Action(node.op, node.op.textArg() == Op.Arg.NONE ? "" : node.text,
                        node.op.numberArg() == Op.Arg.NONE ? 0 : node.number, node.id));
            }
            id = node.next;
        }
        return statements;
    }

    /** Een voorwaarde zoals de code hem schrijft ("card.level>=3" wordt "card.level >= 3"), als hij klopt. */
    private static String cleanCondition(String text) {
        try {
            return Expr.print(Expr.parse(text, null));
        } catch (LogicException e) {
            return text.isBlank() ? "true" : text;
        }
    }

    // ================================================================ Code naar blokjes

    /**
     * Maakt blokjes van de code. {@code old} is wat er in de blokjes-editor stond (of null): een
     * when-blok dat precies hetzelfde gebleven is, houdt de plekken van zijn blokjes.
     */
    public static LogicGraph toGraph(Script script, @Nullable LogicGraph old) {
        // Hoe de when-blokken er in de oude blokjes uitzagen: per blok elk pad -> soort en plek.
        List<Map<String, LogicGraph.Node>> oldShapes = new ArrayList<>();
        if (old != null) {
            List<LogicGraph.Node> triggers = new ArrayList<>();
            for (LogicGraph.Node node : old.nodes()) {
                if (node.op.isTrigger()) {
                    triggers.add(node);
                }
            }
            triggers.sort(Comparator.comparingInt(node -> node.id));
            for (LogicGraph.Node trigger : triggers) {
                Map<String, LogicGraph.Node> shape = new HashMap<>();
                shape.put("w", trigger);
                paths(old, trigger.next, "w", shape, new HashSet<>());
                oldShapes.add(shape);
            }
        }
        LogicGraph graph = new LogicGraph();
        List<Runnable> later = new ArrayList<>();
        int[] bottom = {MARGIN};
        for (int h = 0; h < script.handlers().size(); h++) {
            Script.Handler handler = script.handlers().get(h);
            Map<String, LogicGraph.Node> shape = h < oldShapes.size() ? oldShapes.get(h) : null;
            Builder builder = new Builder(graph);
            builder.handler(handler);
            if (shape != null && builder.sameShape(shape)) {
                builder.placeLike(shape);
                bottom[0] = Math.max(bottom[0], builder.lowest() + ROW_H);
            } else {
                // Nieuw of veranderd: straks onder alles wat zijn plek hield.
                later.add(() -> {
                    int rows = builder.layout(MARGIN, bottom[0]);
                    bottom[0] += Math.max(1, rows) * ROW_H + ROW_H / 2;
                });
            }
        }
        for (Runnable layout : later) {
            layout.run();
        }
        return graph;
    }

    /** Elk blokje in de oude blokjes een "pad" geven: w.0, w.1.y.0, ... (zie Builder). */
    private static void paths(LogicGraph graph, int id, String prefix, Map<String, LogicGraph.Node> out, Set<Integer> seen) {
        int index = 0;
        while (id >= 0 && seen.add(id)) {
            LogicGraph.Node node = graph.node(id);
            if (node == null || node.op.isTrigger()) {
                return;
            }
            String path = prefix + "." + index++;
            out.put(path, node);
            if (node.op == Op.IF) {
                paths(graph, node.yes, path + ".y", out, seen);
                paths(graph, node.no, path + ".n", out, seen);
            }
            id = node.next;
        }
    }

    /**
     * Maakt de blokjes van één when-blok. Elk blokje krijgt een pad (de trigger "w", de eerste regel
     * eronder "w.0", in de yes-tak van een if "w.1.y.0"), en een kolom en rij voor de automatische
     * indeling.
     */
    private static final class Builder {
        private final LogicGraph graph;
        private final Map<String, LogicGraph.Node> byPath = new HashMap<>();
        private final Map<LogicGraph.Node, int[]> cells = new HashMap<>();

        Builder(LogicGraph graph) {
            this.graph = graph;
        }

        void handler(Script.Handler handler) {
            LogicGraph.Node trigger = graph.add(handler.trigger(), 0, 0);
            trigger.text = handler.text();
            trigger.number = handler.number();
            byPath.put("w", trigger);
            cells.put(trigger, new int[] {0, 0});
            int[] body = statements(handler.body(), "w", 1, 0);
            trigger.next = body[0];
        }

        /** Maakt een rij blokjes vanaf kolom/rij; geeft {eerste blokje, kolommen, rijen} terug. */
        private int[] statements(List<Script.Stmt> statements, String prefix, int col, int row) {
            int first = -1;
            LogicGraph.Node previous = null;
            int c = col;
            int rows = 1;
            int index = 0;
            for (Script.Stmt statement : statements) {
                String path = prefix + "." + index++;
                LogicGraph.Node node;
                if (statement instanceof Script.If branch) {
                    node = graph.add(Op.IF, 0, 0);
                    node.text = branch.condition();
                    cells.put(node, new int[] {c, row});
                    int[] yes = statements(branch.yes(), path + ".y", c + 1, row);
                    int[] no = statements(branch.no(), path + ".n", c + 1, row + Math.max(1, yes[2]));
                    node.yes = yes[0];
                    node.no = no[0];
                    rows = Math.max(rows, Math.max(1, yes[2]) + Math.max(1, no[2]));
                    c += 1 + Math.max(1, Math.max(yes[1], no[1]));
                } else {
                    Script.Action action = (Script.Action) statement;
                    node = graph.add(action.op(), 0, 0);
                    node.text = action.text();
                    node.number = action.number();
                    cells.put(node, new int[] {c, row});
                    c++;
                }
                byPath.put(path, node);
                if (previous == null) {
                    first = node.id;
                } else {
                    previous.next = node.id;
                }
                previous = node;
            }
            return new int[] {first, c - col, statements.isEmpty() ? 0 : rows};
        }

        /** Hebben de oude blokjes van dit when-blok precies dezelfde vorm (zelfde paden, zelfde soorten)? */
        boolean sameShape(Map<String, LogicGraph.Node> shape) {
            if (shape.size() != byPath.size()) {
                return false;
            }
            for (Map.Entry<String, LogicGraph.Node> entry : byPath.entrySet()) {
                LogicGraph.Node old = shape.get(entry.getKey());
                if (old == null || old.op != entry.getValue().op) {
                    return false;
                }
            }
            return true;
        }

        /** Elk blokje op de plek van zijn oude evenknie. */
        void placeLike(Map<String, LogicGraph.Node> shape) {
            for (Map.Entry<String, LogicGraph.Node> entry : byPath.entrySet()) {
                LogicGraph.Node old = shape.get(entry.getKey());
                entry.getValue().x = old.x;
                entry.getValue().y = old.y;
            }
        }

        int lowest() {
            int lowest = 0;
            for (LogicGraph.Node node : byPath.values()) {
                lowest = Math.max(lowest, node.y);
            }
            return lowest;
        }

        /** De automatische indeling vanaf (x, y). Geeft terug hoeveel rijen het blok gebruikt. */
        int layout(int x, int y) {
            int rows = 0;
            for (Map.Entry<LogicGraph.Node, int[]> entry : cells.entrySet()) {
                int[] cell = entry.getValue();
                entry.getKey().x = x + cell[0] * COL_W;
                entry.getKey().y = y + cell[1] * ROW_H;
                rows = Math.max(rows, cell[1] + 1);
            }
            return rows;
        }
    }
}
