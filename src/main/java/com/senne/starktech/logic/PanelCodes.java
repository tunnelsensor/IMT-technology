package com.senne.starktech.logic;

import com.senne.starktech.logic.program.Expr;
import com.senne.starktech.logic.program.ExprCache;
import com.senne.starktech.logic.program.LogicGraph;
import com.senne.starktech.logic.program.LogicProgram;
import com.senne.starktech.logic.program.Op;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Welke codes een codepaneel opent (hoofdstuk 12). Het paneel zelf weet dat niet: de code staat in je
 * diagram, bijvoorbeeld
 * <pre>
 * when codepanel("Front door"):
 *     if code == "4321":
 *         accept()
 * </pre>
 * De Bypass Kit kraakt zo'n code. Daarvoor zoeken we hier in de diagrammen die aan staan naar elke
 * {@code code == "..."} (of {@code !=}) onder een {@code when codepanel(...)} met de naam van dit paneel,
 * ook in de defs die daar gebruikt worden. Alleen echte codes tellen (1 tot 8 cijfers).
 */
public final class PanelCodes {
    private PanelCodes() {}

    /** De codes voor het paneel met deze naam, in de volgorde waarin ze in je diagrammen staan (nooit dubbel). */
    public static List<String> forPanel(SecurityCoreBlockEntity core, String panelName) {
        List<String> codes = new ArrayList<>();
        for (LogicProgram program : core.logic().programs()) {
            if (!program.enabled()) {
                continue;
            }
            LogicGraph graph = program.graph();
            for (LogicGraph.Node node : graph.nodes()) {
                if (node.op == Op.CODEPANEL && node.text.equalsIgnoreCase(panelName)) {
                    walk(graph, node.next, new HashSet<>(), codes);
                }
            }
        }
        return codes;
    }

    /**
     * Loopt de blokjes af zoals de server ze zou uitvoeren: bij een if de voorwaarde, dan de yes-tak, de
     * no-tak en wat erna komt; bij een use def eerst de stappen van de def. Elk blokje hooguit één keer.
     */
    private static void walk(LogicGraph graph, int id, Set<Integer> seen, List<String> codes) {
        while (id >= 0 && seen.add(id)) {
            LogicGraph.Node node = graph.node(id);
            if (node == null || node.op.isRoot()) {
                return;
            }
            if (node.op == Op.IF) {
                Expr condition = ExprCache.get(node.text, Op.CODEPANEL);
                if (condition != null) {
                    collect(condition, codes);
                }
                walk(graph, node.yes, seen, codes);
                walk(graph, node.no, seen, codes);
            } else if (node.op.kind() == Op.Kind.LOOP) {
                // Een lus (hoofdstuk 20): een while kan ook een code controleren; dan de stappen erin.
                Expr condition = node.op == Op.WHILE ? ExprCache.get(node.text, Op.CODEPANEL) : null;
                if (condition != null) {
                    collect(condition, codes);
                }
                walk(graph, node.yes, seen, codes);
            } else if (node.op == Op.CALL) {
                for (LogicGraph.Node def : graph.nodes()) {
                    if (def.op == Op.DEF && def.text.equals(node.text)) {
                        walk(graph, def.next, seen, codes);
                    }
                }
            }
            id = node.next;
        }
    }

    /** Zoekt in een voorwaarde naar {@code code == "1234"} (ook andersom, en met !=). */
    private static void collect(Expr expr, List<String> codes) {
        switch (expr) {
            case Expr.Or or -> {
                collect(or.left(), codes);
                collect(or.right(), codes);
            }
            case Expr.And and -> {
                collect(and.left(), codes);
                collect(and.right(), codes);
            }
            case Expr.Not not -> collect(not.inner(), codes);
            case Expr.Compare compare -> {
                if (compare.op().equals("==") || compare.op().equals("!=")) {
                    String text = codeText(compare.left(), compare.right());
                    if (text == null) {
                        text = codeText(compare.right(), compare.left());
                    }
                    if (text != null && CodePanelBlock.isValidCode(text) && !codes.contains(text)) {
                        codes.add(text);
                    }
                }
            }
            default -> { }
        }
    }

    /** De tekst als {@code variable} het gegeven "code" is en {@code value} een tekst; anders null. */
    private static String codeText(Expr variable, Expr value) {
        if (variable instanceof Expr.Var var && var.name().equals("code") && value instanceof Expr.Str str) {
            return str.value();
        }
        return null;
    }
}
