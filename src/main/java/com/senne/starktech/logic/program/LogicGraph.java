package com.senne.starktech.logic.program;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Een diagram als blokjes met draadjes (jouw wens, 7 oktober). Dit is wat de server bewaart en
 * uitvoert; de code (Script) is een andere manier om hetzelfde te laten zien (zie GraphConverter).
 *
 * Elk blokje ({@link Node}) heeft een plek op het vlak (x, y) en uitgangen ("poorten"):
 * <ul>
 *   <li>een trigger of actie heeft één uitgang, NEXT: wat erna komt;</li>
 *   <li>een IF heeft er drie: YES (als de voorwaarde klopt), NO (anders), en NEXT ("after"): wat er
 *       daarna komt, bij yes én bij no.</li>
 * </ul>
 * Elk blokje heeft hooguit één draadje dat erin komt (een trigger of DEF nooit), en draadjes mogen
 * niet in een rondje lopen. Zo is elk diagram een boompje dat precies past op de code met
 * inspringen. Een DEF-blok (je eigen blok) begint net als een trigger een rij; een CALL-blok voert
 * die rij uit.
 */
public final class LogicGraph {
    /** De uitgangen van een blokje. */
    public enum Port { NEXT, YES, NO }

    /** Zoveel blokjes mag één diagram hooguit hebben (de SSD's van je server zijn meestal eerder vol). */
    public static final int MAX_NODES = 200;

    /** Eén blokje. */
    public static final class Node {
        public final int id;
        public final Op op;
        /** De naam van het apparaat, het bericht, of (bij een IF) de voorwaarde. */
        public String text = "";
        /** Seconden (wait, pulse, every); anders 0. */
        public double number;
        /** Waar het staat op het vlak van de blokjes-editor. */
        public int x;
        public int y;
        public int next = -1;
        public int yes = -1;
        public int no = -1;

        Node(int id, Op op) {
            this.id = id;
            this.op = op;
            if (op.numberArg() == Op.Arg.SECONDS || op.numberArg() == Op.Arg.OPTIONAL_SECONDS) {
                number = op == Op.EVERY ? 10 : op.defaultSeconds();
            }
        }

        /** Waar deze uitgang heen gaat (een blokje-nummer), of -1. */
        public int link(Port port) {
            return switch (port) {
                case NEXT -> next;
                case YES -> yes;
                case NO -> no;
            };
        }

        void setLink(Port port, int target) {
            switch (port) {
                case NEXT -> next = target;
                case YES -> yes = target;
                case NO -> no = target;
            }
        }

        /** Welke uitgangen dit blokje heeft. */
        public List<Port> ports() {
            return op == Op.IF ? List.of(Port.YES, Port.NO, Port.NEXT) : List.of(Port.NEXT);
        }

        Node copy() {
            Node copy = new Node(id, op);
            copy.text = text;
            copy.number = number;
            copy.x = x;
            copy.y = y;
            copy.next = next;
            copy.yes = yes;
            copy.no = no;
            return copy;
        }
    }

    /** Een probleem met een blokje: welk blokje, en wat er mis is (in begrijpelijk Engels). */
    public record Problem(int node, String message) {}

    private final Map<Integer, Node> nodes = new LinkedHashMap<>();
    private int nextId = 1;

    public Collection<Node> nodes() {
        return nodes.values();
    }

    public @Nullable Node node(int id) {
        return nodes.get(id);
    }

    public int size() {
        return nodes.size();
    }

    /** Een nieuw blokje op deze plek. */
    public Node add(Op op, int x, int y) {
        Node node = new Node(nextId++, op);
        node.x = x;
        node.y = y;
        nodes.put(node.id, node);
        return node;
    }

    /** Haalt een blokje weg, met de draadjes die erin en eruit gingen (wat erachter hing, ligt dan los). */
    public void remove(int id) {
        if (nodes.remove(id) == null) {
            return;
        }
        for (Node node : nodes.values()) {
            for (Port port : Port.values()) {
                if (node.link(port) == id) {
                    node.setLink(port, -1);
                }
            }
        }
    }

    /** Het blokje met een draadje naar {@code id}, of null. */
    public @Nullable Node parentOf(int id) {
        for (Node node : nodes.values()) {
            for (Port port : node.ports()) {
                if (node.link(port) == id) {
                    return node;
                }
            }
        }
        return null;
    }

    /**
     * Legt een draadje van de uitgang {@code port} van {@code from} naar {@code to}. Had {@code to} al
     * een draadje erin, dan gaat dat weg (elk blokje heeft er één). Geeft een fout terug als het niet
     * mag (naar een trigger, of in een rondje), anders null.
     */
    public @Nullable String connect(int from, Port port, int to) {
        Node source = nodes.get(from);
        Node target = nodes.get(to);
        if (source == null || target == null || !source.ports().contains(port)) {
            return "That connection does not exist";
        }
        if (target.op.isRoot()) {
            return "Nothing can go into a when or a def: they start a row of blocks";
        }
        if (from == to || leadsTo(to, from)) {
            return "That would make a loop";
        }
        Node oldParent = parentOf(to);
        if (oldParent != null) {
            for (Port oldPort : Port.values()) {
                if (oldParent.link(oldPort) == to) {
                    oldParent.setLink(oldPort, -1);
                }
            }
        }
        source.setLink(port, to);
        return null;
    }

    /** Haalt het draadje uit deze uitgang. */
    public void disconnect(int from, Port port) {
        Node source = nodes.get(from);
        if (source != null) {
            source.setLink(port, -1);
        }
    }

    /** Kun je via draadjes van {@code from} bij {@code to} komen? */
    private boolean leadsTo(int from, int to) {
        Set<Integer> seen = new HashSet<>();
        List<Integer> todo = new ArrayList<>(List.of(from));
        while (!todo.isEmpty()) {
            int id = todo.removeLast();
            if (id == to) {
                return true;
            }
            Node node = nodes.get(id);
            if (node == null || !seen.add(id)) {
                continue;
            }
            for (Port port : node.ports()) {
                if (node.link(port) >= 0) {
                    todo.add(node.link(port));
                }
            }
        }
        return false;
    }

    /** De trigger of def bovenaan de tak waar dit blokje in zit, of null als het los ligt. */
    public @Nullable Node rootOf(int id) {
        Set<Integer> seen = new HashSet<>();
        Node node = nodes.get(id);
        while (node != null && seen.add(node.id)) {
            if (node.op.isRoot()) {
                return node;
            }
            node = parentOf(node.id);
        }
        return null;
    }

    /** Alle blokjes die (via draadjes) aan een trigger of def hangen. De rest ligt los en doet niets. */
    public Set<Integer> reachable() {
        Set<Integer> seen = new HashSet<>();
        for (Node node : nodes.values()) {
            if (node.op.isRoot()) {
                List<Integer> todo = new ArrayList<>(List.of(node.id));
                while (!todo.isEmpty()) {
                    Node current = nodes.get(todo.removeLast());
                    if (current == null || !seen.add(current.id)) {
                        continue;
                    }
                    for (Port port : current.ports()) {
                        if (current.link(port) >= 0) {
                            todo.add(current.link(port));
                        }
                    }
                }
            }
        }
        return seen;
    }

    /**
     * Wat er mis is met de blokjes die aan een trigger of def hangen: een ontbrekende naam, een getal
     * dat te groot is, een voorwaarde die niet klopt, accept() onder de verkeerde trigger, en alles
     * met je eigen blokken (zie ScriptChecker: een def die niet bestaat, of zichzelf gebruikt). Losse
     * blokjes worden niet gecontroleerd (ze doen toch niets).
     */
    public List<Problem> problems() {
        List<Problem> problems = new ArrayList<>();
        Set<Integer> reachable = reachable();
        Set<Integer> wrong = new HashSet<>();
        for (Node node : nodes.values()) {
            if (!reachable.contains(node.id)) {
                continue;
            }
            // Onder een def weten we niet welke when hem gebruikt: dat controleert ScriptChecker.
            Node root = rootOf(node.id);
            Op triggerOp = root == null || root.op == Op.DEF ? null : root.op;
            String problem;
            if (node.op == Op.IF) {
                try {
                    Expr.parse(node.text, triggerOp);
                    problem = null;
                } catch (LogicException e) {
                    problem = e.getMessage();
                }
            } else {
                String text = node.op.textArg() == Op.Arg.NONE ? null : node.text;
                Double number = node.op.numberArg() == Op.Arg.NONE ? null : node.number;
                problem = Checks.argsProblem(node.op, text, number);
                if (problem == null && !node.op.isRoot()) {
                    problem = Checks.contextProblem(node.op, triggerOp);
                }
            }
            if (problem != null) {
                problems.add(new Problem(node.id, problem));
                wrong.add(node.id);
            }
        }
        // Je eigen blokken: dezelfde controles als in de code (het "ref" is hier het blokje).
        for (ScriptChecker.Problem problem : ScriptChecker.check(GraphConverter.toScript(this))) {
            if (wrong.add(problem.ref())) {
                problems.add(new Problem(problem.ref(), problem.message()));
            }
        }
        return problems;
    }

    public LogicGraph copy() {
        LogicGraph copy = new LogicGraph();
        for (Node node : nodes.values()) {
            copy.nodes.put(node.id, node.copy());
        }
        copy.nextId = nextId;
        return copy;
    }

    // ================================================================ Opslaan

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Node node : nodes.values()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("i", node.id);
            entry.putString("o", node.op.name());
            if (!node.text.isEmpty()) {
                entry.putString("t", node.text);
            }
            if (node.number != 0) {
                entry.putDouble("n", node.number);
            }
            entry.putInt("x", node.x);
            entry.putInt("y", node.y);
            entry.putInt("nx", node.next);
            entry.putInt("ys", node.yes);
            entry.putInt("no", node.no);
            list.add(entry);
        }
        tag.put("Nodes", list);
        return tag;
    }

    /**
     * Leest een diagram terug. Wat niet klopt (een onbekend blokje, een draadje naar niets, te veel
     * blokjes) laten we weg: zo kan een kapot of vervalst diagram de server nooit laten vastlopen.
     */
    public static LogicGraph load(CompoundTag tag) {
        LogicGraph graph = new LogicGraph();
        for (Tag element : tag.getList("Nodes", Tag.TAG_COMPOUND)) {
            if (graph.nodes.size() >= MAX_NODES) {
                break;
            }
            CompoundTag entry = (CompoundTag) element;
            Op op = Op.byName(entry.getString("o"));
            int id = entry.getInt("i");
            if (op == null || id <= 0 || graph.nodes.containsKey(id)) {
                continue;
            }
            Node node = new Node(id, op);
            String text = entry.getString("t");
            node.text = text.length() > Op.MAX_TEXT * 3 ? text.substring(0, Op.MAX_TEXT * 3) : text;
            node.number = entry.contains("n") ? entry.getDouble("n") : 0;
            if (!Double.isFinite(node.number)) {
                node.number = 0;
            }
            node.x = entry.getInt("x");
            node.y = entry.getInt("y");
            node.next = entry.getInt("nx");
            node.yes = entry.getInt("ys");
            node.no = entry.getInt("no");
            graph.nodes.put(id, node);
            graph.nextId = Math.max(graph.nextId, id + 1);
        }
        graph.repair();
        return graph;
    }

    /**
     * Maakt draadjes die niet mogen ongedaan: naar een blokje dat er niet is, naar een trigger, een
     * tweede draadje in hetzelfde blokje, of een rondje.
     */
    private void repair() {
        Set<Integer> hasParent = new HashSet<>();
        for (Node node : nodes.values()) {
            for (Port port : Port.values()) {
                int target = node.link(port);
                if (target < 0) {
                    continue;
                }
                Node child = nodes.get(target);
                boolean ok = node.ports().contains(port) && child != null && !child.op.isRoot()
                        && target != node.id && hasParent.add(target);
                if (!ok) {
                    node.setLink(port, -1);
                }
            }
        }
        // Rondjes: een blokje dat via zijn eigen draadjes bij zichzelf uitkomt, verliest dat draadje.
        for (Node node : nodes.values()) {
            for (Port port : node.ports()) {
                int target = node.link(port);
                if (target >= 0 && leadsTo(target, node.id)) {
                    node.setLink(port, -1);
                }
            }
        }
    }
}
