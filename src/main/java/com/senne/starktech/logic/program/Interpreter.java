package com.senne.starktech.logic.program;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * De tolk (hoofdstuk 20): één "vingertje" dat de blokjes van een diagram afloopt, vanaf de eerste
 * stap onder een when.
 *
 * De tolk weet zelf hoe een diagram loopt: bij een if kiezen tussen yes en no en daarna verder bij
 * "after", bij een eigen blok (CALL) eerst de stappen van die def en dan terug, bij {@code wait}
 * even pauzeren, en {@code count = count + 1} (een naam een waarde geven). Wat een blokje <b>echt
 * doet</b> (een relais schakelen, E.D.I.T.H. laten praten, en straks een robot laten lopen), weet de
 * tolk niet: dat vraagt hij aan zijn {@link Host}. Zo kunnen de server, robots, drones en het Training
 * End-blok allemaal dezelfde tolk gebruiken.
 *
 * <b>Namen</b> (variabelen): een naam helemaal links ({@code count = 0}) is van het hele programma
 * ({@code globals}: die krijgt de tolk mee, zodat ze tussen de runs blijven bestaan). Een naam die in
 * een when of def een waarde krijgt, is alleen van die run; een def krijgt zijn eigen namen (de
 * namen van de when die hem gebruikt, ziet hij niet), net als een functie in Python.
 *
 * Er zit geen Minecraft in deze klasse, zodat hij ook in een test zonder wereld werkt.
 */
public final class Interpreter {
    /** Zo diep mogen ifs en eigen blokken in elkaar zitten (een def die zichzelf gebruikt, mag al niet). */
    public static final int MAX_DEPTH = 64;

    /** Wat er écht gebeurt: dat weet de host (de server, een robot...), niet de tolk. */
    public interface Host {
        /** Is de voorwaarde van dit IF-blokje nu waar? */
        boolean test(Interpreter run, LogicGraph.Node node);

        /** Doe deze actie. (Een if, wait, eigen blok of naam = waarde doet de tolk zelf; die komen hier niet.) */
        void act(Interpreter run, LogicGraph.Node node);

        /** De gegevens van nu (card.level, is_night...), om een waarde uit te rekenen. */
        Expr.Facts facts(Interpreter run);

        /** Er klopt iets niet aan het diagram zelf (een def die er niet is): zeg het. */
        void report(String message);
    }

    /**
     * Waar het verder gaat als een tak van een if, of een eigen blok, klaar is. Bij een eigen blok
     * onthouden we ook de namen van wie hem gebruikte ({@code locals}), om die daarna terug te zetten.
     */
    private record Frame(int next, @Nullable Map<String, Object> locals) {}

    private final LogicGraph graph;
    private final Map<String, Object> globals;
    private Map<String, Object> locals = new HashMap<>();
    private int current;
    private final ArrayDeque<Frame> frames = new ArrayDeque<>();
    private long wakeAt;
    private int steps;

    /** Een nieuwe run die begint bij blokje {@code first}, zonder namen van links. */
    public Interpreter(LogicGraph graph, int first) {
        this(graph, first, new HashMap<>());
    }

    /**
     * Een nieuwe run die begint bij blokje {@code first} (het eerste blokje onder een when).
     * {@code globals} zijn de namen van links van dit programma (zie {@link #initGlobals}); de run
     * verandert ze echt, zodat de volgende run de nieuwe waarde ziet.
     */
    public Interpreter(LogicGraph graph, int first, Map<String, Object> globals) {
        this.graph = graph;
        this.current = first;
        this.globals = globals;
    }

    /**
     * Loopt blokjes af tot de run moet wachten ({@code wait}), klaar is, of {@code budget} blokjes
     * gedaan heeft (dan gaat hij de volgende tick verder). Geeft true als de run klaar is.
     * {@code now} is de speltijd in ticks.
     */
    public boolean step(Host host, long now, int budget) {
        for (int i = 0; i < budget; i++) {
            steps++;
            if (current < 0) {
                if (frames.isEmpty()) {
                    return true;
                }
                Frame frame = frames.pop();
                if (frame.locals() != null) {
                    locals = frame.locals(); // terug uit een eigen blok: de namen van de gebruiker weer
                }
                current = frame.next();
                continue;
            }
            LogicGraph.Node node = graph.node(current);
            if (node == null || node.op.isRoot()) {
                current = -1;
                continue;
            }
            if (node.op == Op.CALL) {
                // Een eigen blok: eerst zijn stappen (met zijn eigen namen), en daarna verder met wat na
                // de CALL komt.
                LogicGraph.Node def = findDef(node.text);
                if (def == null || frames.size() >= MAX_DEPTH) {
                    host.report(def == null ? "there is no def " + node.text : node.text + "() goes too deep");
                    current = node.next;
                    continue;
                }
                frames.push(new Frame(node.next, locals));
                locals = new HashMap<>();
                current = def.next;
                continue;
            }
            if (node.op == Op.IF) {
                boolean yes = host.test(this, node);
                if (node.next >= 0) {
                    frames.push(new Frame(node.next, null));
                }
                current = yes ? node.yes : node.no;
                continue;
            }
            current = node.next;
            if (node.op == Op.WAIT) {
                wakeAt = now + Math.max(1, Math.round(node.number * 20));
                return false;
            }
            if (node.op == Op.SET) {
                Expr value = ExprCache.value(node.expr);
                if (value == null) {
                    host.report("the value of " + node.text + " (" + node.expr + ") is not valid");
                } else {
                    set(node.text, ExprCache.eval(value, host.facts(this)));
                }
                continue;
            }
            host.act(this, node);
        }
        return false;
    }

    /** Het DEF-blok met deze naam, of null. */
    private @Nullable LogicGraph.Node findDef(String name) {
        for (LogicGraph.Node node : graph.nodes()) {
            if (node.op == Op.DEF && node.text.equals(name)) {
                return node;
            }
        }
        return null;
    }

    // ================================================================ Namen

    /** De waarde van een naam: eerst die van deze run, dan die van links. Null als hij er geen heeft. */
    public @Nullable Object variable(String name) {
        Object value = locals.get(name);
        return value != null ? value : globals.get(name);
    }

    /** Geeft een naam een waarde: een naam van links verandert voor het hele programma, anders voor deze run. */
    public void set(String name, Object value) {
        if (globals.containsKey(name)) {
            globals.put(name, value);
        } else {
            locals.put(name, value);
        }
    }

    /**
     * De namen van links van dit programma met hun eerste waarde ({@code count = 0}), van boven naar
     * beneden. Een naam van links mag de namen erboven gebruiken.
     */
    public static Map<String, Object> initGlobals(LogicGraph graph, Expr.Facts facts) {
        Map<String, Object> globals = new LinkedHashMap<>();
        List<LogicGraph.Node> nodes = new ArrayList<>();
        for (LogicGraph.Node node : graph.nodes()) {
            if (node.op == Op.GLOBAL) {
                nodes.add(node);
            }
        }
        nodes.sort(Comparator.comparingInt(node -> node.id));
        Expr.Facts withGlobals = new Expr.Facts() {
            @Override
            public Object value(String variable) {
                return facts.value(variable);
            }

            @Override
            public boolean isOn(String device) {
                return facts.isOn(device);
            }

            @Override
            public @Nullable Object variable(String name) {
                return globals.get(name);
            }
        };
        for (LogicGraph.Node node : nodes) {
            Expr value = ExprCache.value(node.expr);
            globals.put(node.text, value == null ? 0.0 : ExprCache.eval(value, withGlobals));
        }
        return globals;
    }

    // ================================================================ Wachten en meten

    /** Tot welke speltijd deze run wacht (na een {@code wait}). */
    public long wakeAt() {
        return wakeAt;
    }

    /** Laat de run (opnieuw) beginnen op deze speltijd. */
    public void wakeAt(long time) {
        wakeAt = time;
    }

    /** Hoeveel blokjes de tolk afliep sinds de vorige keer dat je het vroeg (voor de CPU-meter), en begint weer bij 0. */
    public int takeSteps() {
        int taken = steps;
        steps = 0;
        return taken;
    }
}
