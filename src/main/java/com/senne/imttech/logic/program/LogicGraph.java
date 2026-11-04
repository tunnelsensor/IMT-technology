package com.senne.imttech.logic.program;

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
        /**
         * Een waarde als nette code: rechts van {@code count = ...} (SET en GLOBAL, hoofdstuk 20), een berekend
         * bericht of getal, of (hoofdstuk 23) het rijtje waarden van goto, write en order; anders leeg.
         */
        public String expr = "";
        /**
         * Het soort-woord van een naam (hoofdstuk 43, SET en GLOBAL): {@code int}, {@code float}, {@code string} of
         * {@code bool}; leeg als er geen staat.
         */
        public String type = "";
        /**
         * De naam van het apparaat of de robot als code (hoofdstuk 43): {@code pulse(door, 3)} met {@code door} een
         * parameter. Leeg als het gewone tekst is (dan staat die in {@code text}).
         */
        public String textExpr = "";
        /**
         * Komt dit blokje uit een global script (hoofdstuk 43)? Alleen in een samengevoegd diagram (zie
         * {@link #withLibrary}): zo'n def of naam hoort bij het andere script en wordt niet mee opgeslagen of getoond.
         */
        public boolean shared;
        /** Waar het staat op het vlak van de blokjes-editor. */
        public int x;
        public int y;
        public int next = -1;
        public int yes = -1;
        public int no = -1;

        Node(int id, Op op) {
            this.id = id;
            this.op = op;
            if (op.numberArg() == Op.Arg.NUMBER || op.numberArg() == Op.Arg.OPTIONAL_NUMBER) {
                number = op == Op.EVERY ? 10 : op.defaultNumber();
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

        /**
         * Welke uitgangen dit blokje heeft. Een lus heeft YES ("do": de stappen erin) en NEXT ("after");
         * een naam helemaal links (GLOBAL) heeft er geen.
         */
        public List<Port> ports() {
            if (op == Op.IF) {
                return List.of(Port.YES, Port.NO, Port.NEXT);
            }
            if (op.kind() == Op.Kind.LOOP) {
                return List.of(Port.YES, Port.NEXT);
            }
            return op == Op.GLOBAL ? List.of() : List.of(Port.NEXT);
        }

        Node copy() {
            Node copy = new Node(id, op);
            copy.text = text;
            copy.number = number;
            copy.expr = expr;
            copy.type = type;
            copy.textExpr = textExpr;
            copy.shared = shared;
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
    /**
     * Is dit een global script (hoofdstuk 43: de code begint met {@code global})? Dan zijn zijn defs en namen van links
     * voor alle diagrammen van de kern (zie Library), en heeft hij zelf geen whens.
     */
    private boolean global;

    public Collection<Node> nodes() {
        return nodes.values();
    }

    public boolean global() {
        return global;
    }

    public void setGlobal(boolean global) {
        this.global = global;
    }

    /**
     * Dit diagram met de defs en gedeelde namen van de global scripts erbij (hoofdstuk 43), zodat de tolk een def uit
     * een global script gewoon vindt. De blokjes uit de andere scripts krijgen nieuwe nummers (achter de onze) en staan
     * als {@code shared} gemarkeerd: ze tellen niet mee voor de code, het opslaan of de namen van links van dit programma.
     */
    public LogicGraph withLibrary(List<LogicGraph> globals) {
        LogicGraph merged = copy();
        for (LogicGraph other : globals) {
            if (other == this || !other.global) {
                continue;
            }
            Set<Integer> wanted = other.reachable();
            Map<Integer, Integer> renumber = new java.util.HashMap<>();
            for (Node node : other.nodes.values()) {
                if (wanted.contains(node.id) && !node.op.isTrigger()) {
                    renumber.put(node.id, merged.nextId++);
                }
            }
            for (Node node : other.nodes.values()) {
                Integer id = renumber.get(node.id);
                if (id == null) {
                    continue;
                }
                Node copy = new Node(id, node.op);
                copy.text = node.text;
                copy.number = node.number;
                copy.expr = node.expr;
                copy.type = node.type;
                copy.textExpr = node.textExpr;
                copy.shared = true;
                copy.next = renumber.getOrDefault(node.next, -1);
                copy.yes = renumber.getOrDefault(node.yes, -1);
                copy.no = renumber.getOrDefault(node.no, -1);
                merged.nodes.put(id, copy);
            }
        }
        return merged;
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
        return problems(ProgramKind.SERVER);
    }

    /** Hetzelfde, voor deze soort programma (hoofdstuk 23). */
    public List<Problem> problems(ProgramKind kind) {
        return problems(kind, Library.EMPTY);
    }

    /** Hetzelfde, met de defs en gedeelde namen van de global scripts erbij (hoofdstuk 43). */
    public List<Problem> problems(ProgramKind kind, Library library) {
        List<Problem> problems = new ArrayList<>();
        Set<Integer> reachable = reachable();
        Set<Integer> wrong = new HashSet<>();
        for (Node node : nodes.values()) {
            if (!reachable.contains(node.id) || node.shared) {
                continue;
            }
            // Onder een def weten we niet welke when hem gebruikt: dat controleert ScriptChecker.
            Node root = rootOf(node.id);
            Op triggerOp = root == null || root.op == Op.DEF ? null : root.op;
            String problem;
            if (node.op == Op.IF) {
                // Hier alleen of het te lezen is; welke namen en gegevens er mogen, kijkt ScriptChecker
                // hieronder na (die kent de volgorde van de blokjes).
                try {
                    Expr.parseLenient(node.text, true);
                    problem = null;
                } catch (LogicException e) {
                    problem = e.getMessage();
                }
            } else if (node.op.kind() == Op.Kind.LOOP) {
                problem = loopProblem(node);
            } else if (node.op.kind() == Op.Kind.SET) {
                problem = Checks.variableProblem(node.text, Set.of());
                if (problem == null && node.expr.isBlank()) {
                    problem = "Give " + node.text + " a value, like " + node.text + " = 0";
                }
                if (problem == null && !node.type.isEmpty() && VarType.byWord(node.type) == null) {
                    problem = "I don't know the type \"" + node.type + "\": use " + VarType.words();
                }
                if (problem == null) {
                    try {
                        Expr.parseLenient(node.expr, false);
                    } catch (LogicException e) {
                        problem = e.getMessage();
                    }
                }
            } else {
                String text = node.op.textArg() == Op.Arg.NONE ? null : node.text;
                Double number = node.op.numberArg() == Op.Arg.NONE ? null : node.number;
                problem = Checks.argsProblem(node.op, text, number, node.expr, node.textExpr);
                if (problem == null) {
                    // Een berekend bericht, berekende seconden, of een rijtje waarden (goto): is het te lezen?
                    problem = Checks.exprProblem(node.op, node.expr);
                }
                if (problem == null && !node.op.isRoot()) {
                    problem = Checks.contextProblem(node.op, triggerOp);
                }
            }
            if (problem != null) {
                problems.add(new Problem(node.id, problem));
                wrong.add(node.id);
            }
        }
        // Een global script (hoofdstuk 43) met een WHEN-blok erin: dat hoort in een gewoon diagram.
        if (global) {
            for (Node node : nodes.values()) {
                if (node.op.isTrigger() && wrong.add(node.id)) {
                    problems.add(new Problem(node.id, "A global script holds only DEF blocks and names at start:"
                            + " put this WHEN in another diagram (or remove the word global from the code)"));
                }
            }
        }
        // Je eigen blokken: dezelfde controles als in de code (het "ref" is hier het blokje).
        for (ScriptChecker.Problem problem : ScriptChecker.check(GraphConverter.toScript(this), kind, library)) {
            if (wrong.add(problem.ref())) {
                problems.add(new Problem(problem.ref(), problem.message()));
            }
        }
        return problems;
    }

    /**
     * Een def uit een ouder diagram die nu een woord van de taal heet ({@code repeat}, {@code while},
     * {@code forever}, {@code break}, {@code set}, {@code var}, sinds hoofdstuk 23 ook {@code forward},
     * {@code stop}, {@code send}, {@code read}..., en sinds 16 oktober {@code for}, {@code in} en {@code range}),
     * krijgt een andere naam ({@code repeat_1}); de blokjes die hem gebruiken gaan mee. Geeft true als er iets
     * veranderde.
     */
    public boolean renameReservedDefs() {
        Set<String> used = new HashSet<>();
        for (Node node : nodes.values()) {
            if (node.op == Op.DEF) {
                used.add(node.text);
            }
        }
        boolean changed = false;
        for (Node def : nodes.values()) {
            if (def.op != Op.DEF || !Checks.isWord(def.text)) {
                continue;
            }
            String old = def.text;
            String name = old + "_1";
            for (int number = 2; used.contains(name); number++) {
                name = old + "_" + number;
            }
            used.add(name);
            for (Node call : nodes.values()) {
                if (call.op == Op.CALL && call.text.equals(old)) {
                    call.text = name;
                }
            }
            def.text = name;
            changed = true;
        }
        return changed;
    }

    /** Kan de lus zijn naam en bereik (for) of zijn voorwaarde (while) lezen? Null als het klopt. */
    public static @Nullable String loopProblem(Node node) {
        try {
            if (node.op == Op.FOR) {
                String problem = Checks.variableProblem(node.text, Set.of());
                if (problem != null) {
                    return node.text.isBlank() ? "for needs a name that counts, like i" : problem;
                }
                if (node.expr.isBlank()) {
                    return "for needs a range, like 3 (0, 1, 2) or 1, 4 (1, 2, 3)";
                }
                return Checks.rangeProblem(Expr.parseLenientList(node.expr));
            }
            if (node.op == Op.WHILE) {
                Expr.parseLenient(node.text, true);
            }
            return null;
        } catch (LogicException e) {
            return e.getMessage();
        }
    }

    // ================================================================ Oude lussen omzetten (16 oktober)

    /** Kwamen er bij het laden oude lussen in voor (repeat of forever), die nu een for of while true zijn? */
    private boolean convertedLoops;

    /**
     * Had dit diagram bij het laden nog een {@code repeat(3):} of {@code forever:}? Die zijn omgezet (zie
     * {@link #load}), dus de bewaarde code past niet meer: LogicProgram schrijft hem opnieuw uit de blokjes.
     */
    public boolean convertedLoops() {
        return convertedLoops;
    }

    /**
     * Een {@code repeat(3):} van vroeger is nu {@code for i in range(3):}. Elke omgezette lus krijgt de eerste naam
     * uit i, j, k, ... die het programma nog nergens gebruikt en die geen lus eromheen al heeft; een lus in een lus
     * krijgt zo j, dan k (jouw notitie van 16 oktober). Zo verandert er niets aan wat het programma doet.
     */
    private void nameConvertedLoops(List<Node> converted) {
        if (converted.isEmpty()) {
            return;
        }
        Set<String> used = namesInUse();
        // Eerst de buitenste lussen: dan weet een lus erin welke namen er om hem heen al tellen.
        List<Node> order = new ArrayList<>(converted);
        order.sort(java.util.Comparator.comparingInt(node -> enclosingLoops(node).size()));
        for (Node loop : order) {
            Set<String> taken = new HashSet<>(used);
            for (Node outer : enclosingLoops(loop)) {
                if (outer.op == Op.FOR) {
                    taken.add(outer.text);
                }
            }
            loop.text = freeLoopName(taken);
        }
    }

    /** De eerste naam uit i, j, k, l, m, n (en dan i2, j2...) die niet in {@code taken} zit. */
    private static String freeLoopName(Set<String> taken) {
        for (int round = 1; ; round++) {
            for (String letter : List.of("i", "j", "k", "l", "m", "n")) {
                String name = round == 1 ? letter : letter + round;
                if (!taken.contains(name)) {
                    return name;
                }
            }
        }
    }

    /** De lussen waar dit blokje in zit (via hun uitgang "do"), van binnen naar buiten. */
    private List<Node> enclosingLoops(Node node) {
        List<Node> loops = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        Node child = node;
        Node parent = parentOf(child.id);
        while (parent != null && seen.add(parent.id)) {
            if (parent.op.kind() == Op.Kind.LOOP && parent.yes == child.id) {
                loops.add(parent);
            }
            child = parent;
            parent = parentOf(child.id);
        }
        return loops;
    }

    /**
     * Alle namen die het programma al gebruikt: die een waarde krijgen (SET, links, een for) en elk woord in een
     * voorwaarde, waarde of rijtje waarden. (Te veel is niet erg: dan kiezen we gewoon een andere letter.)
     */
    private Set<String> namesInUse() {
        Set<String> names = new HashSet<>();
        for (Node node : nodes.values()) {
            if (node.op.kind() == Op.Kind.SET || node.op == Op.FOR) {
                names.add(node.text);
            }
            if (node.op == Op.IF || node.op == Op.WHILE) {
                words(node.text, names);
            }
            words(node.expr, names);
            words(node.textExpr, names);
        }
        return names;
    }

    /** De woorden (namen) in een stuk code, zoals de code ze leest. */
    private static void words(String text, Set<String> out) {
        if (text.isBlank()) {
            return;
        }
        try {
            for (Lexer.Token token : Lexer.tokens(text)) {
                if (token.type() == Lexer.Type.NAME) {
                    out.add(token.text());
                } else if (token.type() == Lexer.Type.TEMPLATE) {
                    // $"Round {i}": de namen in de code tussen { en } tellen ook (19 oktober).
                    for (String code : Lexer.templateCode(token.text())) {
                        words(code, out);
                    }
                }
            }
        } catch (LogicException e) {
            // Niet te lezen: dan staan er ook geen namen in die we kunnen raken.
        }
    }

    public LogicGraph copy() {
        LogicGraph copy = new LogicGraph();
        for (Node node : nodes.values()) {
            copy.nodes.put(node.id, node.copy());
        }
        copy.nextId = nextId;
        copy.global = global;
        return copy;
    }

    // ================================================================ Opslaan

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (global) {
            tag.putBoolean("Global", true);
        }
        ListTag list = new ListTag();
        for (Node node : nodes.values()) {
            if (node.shared) {
                continue; // uit een global script: dat script bewaart het zelf
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("i", node.id);
            entry.putString("o", node.op.name());
            if (!node.text.isEmpty()) {
                entry.putString("t", node.text);
            }
            if (node.number != 0) {
                entry.putDouble("n", node.number);
            }
            if (!node.expr.isEmpty()) {
                entry.putString("e", node.expr);
            }
            if (!node.type.isEmpty()) {
                entry.putString("ty", node.type);
            }
            if (!node.textExpr.isEmpty()) {
                entry.putString("de", node.textExpr);
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
        graph.global = tag.getBoolean("Global");
        List<Node> repeats = new ArrayList<>();
        for (Tag element : tag.getList("Nodes", Tag.TAG_COMPOUND)) {
            if (graph.nodes.size() >= MAX_NODES) {
                break;
            }
            CompoundTag entry = (CompoundTag) element;
            String opName = entry.getString("o");
            // Lussen van vóór 16 oktober: repeat(3) wordt een for (zijn naam komt hieronder), forever een while true.
            boolean repeat = opName.equals("REPEAT");
            boolean forever = opName.equals("FOREVER");
            Op op = repeat ? Op.FOR : forever ? Op.WHILE : Op.byName(opName);
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
            String expr = entry.getString("e"); // een diagram van vóór hoofdstuk 20 heeft hem niet: leeg
            node.expr = expr.length() > Op.MAX_TEXT * 3 ? expr.substring(0, Op.MAX_TEXT * 3) : expr;
            // Hoofdstuk 43: het soort-woord van een naam, en een apparaat als code (van vóór die tijd: leeg).
            node.type = VarType.byWord(entry.getString("ty")) == null ? "" : entry.getString("ty");
            String textExpr = entry.getString("de");
            node.textExpr = textExpr.length() > Op.MAX_TEXT * 3 ? textExpr.substring(0, Op.MAX_TEXT * 3) : textExpr;
            if (repeat) {
                node.expr = node.text.isBlank() ? "0" : node.text; // het aantal wordt het bereik
                node.text = "";
                repeats.add(node);
            } else if (forever) {
                node.text = "true";
            }
            graph.convertedLoops |= repeat || forever;
            node.x = entry.getInt("x");
            node.y = entry.getInt("y");
            node.next = entry.getInt("nx");
            node.yes = entry.getInt("ys");
            node.no = entry.getInt("no");
            graph.nodes.put(id, node);
            graph.nextId = Math.max(graph.nextId, id + 1);
        }
        graph.repair();
        graph.nameConvertedLoops(repeats);
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
