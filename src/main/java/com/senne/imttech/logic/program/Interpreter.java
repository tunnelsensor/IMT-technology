package com.senne.imttech.logic.program;

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
 * doet</b> (een relais schakelen, I.M.T laten praten, en straks een robot laten lopen), weet de
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

        /**
         * Doe deze actie. (Een if, wait, eigen blok of naam = waarde doet de tolk zelf; die komen hier niet.)
         * Moet de run wachten tot de actie klaar is (een robot die loopt), dan roept de host {@link #pause()} aan.
         */
        void act(Interpreter run, LogicGraph.Node node);

        /** De gegevens van nu (card.number, is_night...), om een waarde uit te rekenen. */
        Expr.Facts facts(Interpreter run);

        /** Er klopt iets niet aan het diagram zelf (een def die er niet is): zeg het. */
        void report(String message);
    }

    /** Wat er op de stapel ligt: na een if, na een eigen blok, of een lus die nog bezig is. */
    private enum FrameKind { AFTER, DEF, LOOP }

    /**
     * Eén plek op de stapel.
     * <ul>
     *   <li>AFTER: na een tak van een if verder bij blokje {@code node};</li>
     *   <li>DEF: na een eigen blok verder bij {@code node}, met de namen van wie hem gebruikte
     *       ({@code locals}), die we dan terugzetten;</li>
     *   <li>LOOP: de lus {@code node} is bezig; bij een for {@code remaining} rondjes over en {@code value} het getal
     *       van deze ronde (zo telt hij door, ook als je de naam in de lus verandert, zoals in Python); een while -1.</li>
     * </ul>
     */
    private record Frame(FrameKind kind, int node, @Nullable Map<String, Object> locals, long remaining, long value) {
        Frame(FrameKind kind, int node, @Nullable Map<String, Object> locals, long remaining) {
            this(kind, node, locals, remaining, 0);
        }
    }

    private final LogicGraph graph;
    private final Map<String, Object> globals;
    /** De gedeelde namen van de kern (hoofdstuk 43, uit de global scripts): elk diagram leest en verandert dezelfde. */
    private final Map<String, Object> shared;
    /** De soort-woorden van de namen (hoofdstuk 43): een int rondt af naar een heel getal als hij een waarde krijgt. */
    private final Map<String, VarType> types = new HashMap<>();
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
        this(graph, first, globals, new HashMap<>());
    }

    /**
     * Zoals hierboven, met de gedeelde namen van de kern erbij ({@code shared}, hoofdstuk 43): die komen uit de global
     * scripts, en een waarde die de run eraan geeft, ziet elk ander diagram meteen. Het diagram is dan meestal
     * samengevoegd met de global scripts (zie LogicGraph.withLibrary), zodat hun defs te vinden zijn.
     */
    public Interpreter(LogicGraph graph, int first, Map<String, Object> globals, Map<String, Object> shared) {
        this.graph = graph;
        this.current = first;
        this.globals = globals;
        this.shared = shared;
        // De soort-woorden: van namen van links, van een naam die er een krijgt in een when, en van parameters.
        for (LogicGraph.Node node : graph.nodes()) {
            if (node.op.kind() == Op.Kind.SET) {
                VarType type = VarType.byWord(node.type);
                if (type != null) {
                    types.putIfAbsent(node.text, type);
                }
            } else if (node.op == Op.DEF) {
                try {
                    for (Script.Param param : Checks.parseParams(node.expr)) {
                        if (param.varType() != null) {
                            types.putIfAbsent(param.name(), param.varType());
                        }
                    }
                } catch (LogicException e) {
                    // Parameters die niet te lezen zijn: dat zegt de controle al.
                }
            }
        }
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
                switch (frame.kind()) {
                    case AFTER -> current = frame.node();
                    case DEF -> {
                        locals = frame.locals(); // terug uit een eigen blok: de namen van de gebruiker weer
                        current = frame.node();
                    }
                    case LOOP -> current = again(host, frame); // de stappen van de lus zijn klaar
                }
                continue;
            }
            LogicGraph.Node node = graph.node(current);
            if (node == null || node.op.isRoot()) {
                current = -1;
                continue;
            }
            if (node.op == Op.CALL) {
                // Een eigen blok: eerst zijn stappen (met zijn eigen namen), en daarna verder met wat na
                // de CALL komt. Hoofdstuk 43: de waarden tussen de haakjes worden zijn parameters.
                LogicGraph.Node def = findDef(node.text);
                if (def == null || frames.size() >= MAX_DEPTH) {
                    host.report(def == null ? "there is no def " + node.text : node.text + "() goes too deep");
                    current = node.next;
                    continue;
                }
                Map<String, Object> params = params(host, def, node);
                frames.push(new Frame(FrameKind.DEF, node.next, locals, -1));
                locals = params;
                current = def.next;
                continue;
            }
            if (node.op == Op.IF) {
                boolean yes = host.test(this, node);
                if (node.next >= 0) {
                    frames.push(new Frame(FrameKind.AFTER, node.next, null, -1));
                }
                current = yes ? node.yes : node.no;
                continue;
            }
            if (node.op.kind() == Op.Kind.LOOP) {
                current = enter(host, node);
                continue;
            }
            if (node.op == Op.BREAK) {
                current = leaveLoop();
                continue;
            }
            current = node.next;
            if (node.op == Op.WAIT) {
                // Berekende seconden (wait(delay * 2)) houden we tussen 0,05 s en 10 minuten.
                double seconds = node.expr.isEmpty() ? node.number
                        : Math.max(Op.WAIT.minNumber(), Math.min(Op.WAIT.maxNumber(), number(host, node.expr)));
                wakeAt = now + Math.max(1, Math.round(seconds * 20));
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
            if (wakeAt > now) {
                return false; // de host laat de run wachten (een robot die eerst moet lopen, zie BotBrain)
            }
        }
        return false;
    }

    // ================================================================ Lussen

    /**
     * Een lus begint: de eerste ronde (de stappen aan "do"), of meteen verder na de lus als er niets te
     * doen is ({@code range(0)}, of een while die meteen niet klopt). Geeft het volgende blokje.
     * Een lus zonder wachten loopt niet eindeloos in één tick: elke run doet hooguit zijn budget aan
     * blokjes per tick (zie {@link #step}), de rest volgt de volgende tick.
     */
    private int enter(Host host, LogicGraph.Node loop) {
        if (loop.op == Op.FOR) {
            // for i in range(3): i is 0, dan 1, dan 2. range(1, 4): 1, 2, 3. Het bereik rekenen we één keer uit.
            long[] range = range(host, loop.expr);
            if (range[0] >= range[1]) {
                return loop.next;
            }
            set(loop.text, (double) range[0]);
            frames.push(new Frame(FrameKind.LOOP, loop.id, null, range[1] - range[0] - 1, range[0]));
            return loop.yes;
        }
        if (!host.test(this, loop)) {
            return loop.next; // een while die meteen niet klopt
        }
        frames.push(new Frame(FrameKind.LOOP, loop.id, null, -1));
        return loop.yes;
    }

    /** De stappen van een lus zijn klaar: nog een ronde, of verder na de lus. */
    private int again(Host host, Frame frame) {
        LogicGraph.Node loop = graph.node(frame.node());
        if (loop == null) {
            return -1;
        }
        if (loop.op == Op.FOR) {
            if (frame.remaining() <= 0) {
                return loop.next;
            }
            long value = frame.value() + 1;
            set(loop.text, (double) value);
            frames.push(new Frame(FrameKind.LOOP, loop.id, null, frame.remaining() - 1, value));
            return loop.yes;
        }
        if (!host.test(this, loop)) {
            return loop.next;
        }
        frames.push(frame);
        return loop.yes;
    }

    /**
     * Het bereik van een for: {begin, einde}, waarbij het einde niet meer meetelt (zoals in Python). {@code range(3)} is
     * {0, 3}, {@code range(1, 4)} is {1, 4}. Een getal met een komma wordt afgerond.
     */
    private long[] range(Host host, String text) {
        List<Expr> bounds = ExprCache.values(text);
        if (bounds == null || bounds.isEmpty() || bounds.size() > 2) {
            host.report("the range \"" + text + "\" is not valid, so the for loop does nothing");
            return new long[] {0, 0};
        }
        long first = bound(host, bounds.getFirst());
        return bounds.size() == 1 ? new long[] {0, first} : new long[] {first, bound(host, bounds.get(1))};
    }

    /** Eén getal van een bereik, afgerond en binnen een miljard (dan rekent het aftrekken nooit verkeerd). */
    private long bound(Host host, Expr expr) {
        double value = ExprCache.eval(expr, host.facts(this)) instanceof Double number ? number : 0;
        return Math.max(-MAX_BOUND, Math.min(MAX_BOUND, Math.round(value)));
    }

    /** Zo groot mag een getal van {@code range} hooguit zijn: een miljard rondjes duurt toch al dagen. */
    private static final long MAX_BOUND = 1_000_000_000L;

    /**
     * {@code break}: alles op de stapel weg tot en met de binnenste lus (ook de ifs erin), en verder na die
     * lus. Staat hij niet in een lus (dat laat de controle niet toe), dan stopt het eigen blok of de run.
     */
    private int leaveLoop() {
        while (!frames.isEmpty()) {
            Frame frame = frames.pop();
            if (frame.kind() == FrameKind.LOOP) {
                LogicGraph.Node loop = graph.node(frame.node());
                return loop == null ? -1 : loop.next;
            }
            if (frame.kind() == FrameKind.DEF) {
                locals = frame.locals();
                return frame.node();
            }
        }
        return -1;
    }

    /** Een getal uitrekenen (de seconden van wait); 0 als het geen getal is. */
    private double number(Host host, String text) {
        Expr expr = ExprCache.value(text);
        if (expr == null) {
            host.report("the number \"" + text + "\" is not valid, so it counts as 0");
            return 0;
        }
        return ExprCache.eval(expr, host.facts(this)) instanceof Double value ? value : 0;
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

    /**
     * De parameters van een def (hoofdstuk 43) met de waarden van de aanroep: {@code open_door("Lab door", 3)} bij
     * {@code def open_door(string door, int times):} geeft door = "Lab door" en times = 3. De waarden rekenen we uit
     * met de namen van wie aanroept (die zijn er nu nog). Te weinig waarden: de rest krijgt de lege waarde van zijn
     * soort (dat laat de controle niet toe, maar een oud of vreemd diagram kan het hebben).
     */
    private Map<String, Object> params(Host host, LogicGraph.Node def, LogicGraph.Node call) {
        Map<String, Object> params = new HashMap<>();
        List<Script.Param> names;
        try {
            names = Checks.parseParams(def.expr);
        } catch (LogicException e) {
            host.report("the parameters of def " + def.text + " (" + def.expr + ") are not valid");
            return params;
        }
        if (names.isEmpty()) {
            return params;
        }
        List<Expr> args = call.expr.isBlank() ? List.of() : ExprCache.values(call.expr);
        if (args == null) {
            host.report("the values for " + call.text + "(" + call.expr + ") are not valid");
            args = List.of();
        }
        Expr.Facts facts = host.facts(this);
        for (int i = 0; i < names.size(); i++) {
            Script.Param param = names.get(i);
            VarType type = param.varType();
            Object value = i < args.size() ? ExprCache.eval(args.get(i), facts) : (type == null ? 0.0 : type.empty());
            params.put(param.name(), type == null ? value : type.coerce(value));
        }
        return params;
    }

    /**
     * De waarde van een naam: eerst die van deze run, dan die van links, dan (hoofdstuk 43) de gedeelde namen van de
     * kern. Null als hij er geen heeft.
     */
    public @Nullable Object variable(String name) {
        Object value = locals.get(name);
        if (value == null) {
            value = globals.get(name);
        }
        return value != null ? value : shared.get(name);
    }

    /**
     * Geeft een naam een waarde: een naam van links verandert voor het hele programma, een gedeelde naam (hoofdstuk 43)
     * voor alle diagrammen van de kern, anders voor deze run. Een int houdt alleen het hele getal.
     */
    public void set(String name, Object value) {
        VarType type = types.get(name);
        Object kept = type == null ? value : type.coerce(value);
        if (locals.containsKey(name)) {
            locals.put(name, kept);
        } else if (globals.containsKey(name)) {
            globals.put(name, kept);
        } else if (shared.containsKey(name)) {
            shared.put(name, kept);
        } else {
            locals.put(name, kept);
        }
    }

    /** De gedeelde namen waar deze run mee werkt (voor de tests). */
    public Map<String, Object> shared() {
        return shared;
    }

    /**
     * De namen van links van dit programma met hun eerste waarde ({@code count = 0}), van boven naar
     * beneden. Een naam van links mag de namen erboven gebruiken.
     */
    public static Map<String, Object> initGlobals(LogicGraph graph, Expr.Facts facts) {
        Map<String, Object> globals = new LinkedHashMap<>();
        List<LogicGraph.Node> nodes = new ArrayList<>();
        for (LogicGraph.Node node : graph.nodes()) {
            // Een naam uit een global script (shared, hoofdstuk 43) is van de kern, niet van dit programma.
            if (node.op == Op.GLOBAL && !node.shared) {
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
                // Een naam bovenaan mag een gedeelde naam van een global script gebruiken (Check vindt dat goed):
                // building = "North" en dan door = f"{building} lab door". Die vraagt hij aan facts (hoofdstuk 135, nalezing:
                // eerst werd dat "0 lab door").
                Object own = globals.get(name);
                return own != null ? own : facts.variable(name);
            }
        };
        for (LogicGraph.Node node : nodes) {
            Expr value = ExprCache.value(node.expr);
            Object first = value == null ? 0.0 : ExprCache.eval(value, withGlobals);
            VarType type = VarType.byWord(node.type);
            globals.put(node.text, type == null ? first : type.coerce(first));
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

    /**
     * Laat de run wachten tot iemand hem weer wekt met {@link #wakeAt(long)}: een robot doet {@code forward(3)}
     * en zijn programma gaat pas verder als hij er is (hoofdstuk 23).
     */
    public void pause() {
        wakeAt = Long.MAX_VALUE;
    }

    /** Hoeveel blokjes de tolk afliep sinds de vorige keer dat je het vroeg (voor de CPU-meter), en begint weer bij 0. */
    public int takeSteps() {
        int taken = steps;
        steps = 0;
        return taken;
    }
}
