package com.senne.starktech.logic.program;

import java.util.ArrayDeque;
import org.jetbrains.annotations.Nullable;

/**
 * De tolk (hoofdstuk 20): één "vingertje" dat de blokjes van een diagram afloopt, vanaf de eerste
 * stap onder een when.
 *
 * De tolk weet zelf hoe een diagram loopt: bij een if kiezen tussen yes en no en daarna verder bij
 * "after", bij een eigen blok (CALL) eerst de stappen van die def en dan terug, bij {@code wait}
 * even pauzeren. Wat een blokje <b>echt doet</b> (een relais schakelen, E.D.I.T.H. laten praten, en
 * straks een robot laten lopen), weet de tolk niet: dat vraagt hij aan zijn {@link Host}. Zo kunnen de
 * server, robots, drones en het Training End-blok allemaal dezelfde tolk gebruiken.
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

        /** Doe deze actie. (Een if, wait of eigen blok doet de tolk zelf; die komen hier niet.) */
        void act(Interpreter run, LogicGraph.Node node);

        /** Er klopt iets niet aan het diagram zelf (een def die er niet is): zeg het. */
        void report(String message);
    }

    /** Waar het verder gaat als een tak van een if, of een eigen blok, klaar is. */
    private record Frame(int next) {}

    private final LogicGraph graph;
    private int current;
    private final ArrayDeque<Frame> frames = new ArrayDeque<>();
    private long wakeAt;
    private int steps;

    /** Een nieuwe run die begint bij blokje {@code first} (het eerste blokje onder een when). */
    public Interpreter(LogicGraph graph, int first) {
        this.graph = graph;
        this.current = first;
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
                current = frames.pop().next();
                continue;
            }
            LogicGraph.Node node = graph.node(current);
            if (node == null || node.op.isRoot()) {
                current = -1;
                continue;
            }
            if (node.op == Op.CALL) {
                // Een eigen blok: eerst zijn stappen, en daarna verder met wat na de CALL komt.
                LogicGraph.Node def = findDef(node.text);
                if (def == null || frames.size() >= MAX_DEPTH) {
                    host.report(def == null ? "there is no def " + node.text : node.text + "() goes too deep");
                    current = node.next;
                    continue;
                }
                if (node.next >= 0) {
                    frames.push(new Frame(node.next));
                }
                current = def.next;
                continue;
            }
            if (node.op == Op.IF) {
                boolean yes = host.test(this, node);
                if (node.next >= 0) {
                    frames.push(new Frame(node.next));
                }
                current = yes ? node.yes : node.no;
                continue;
            }
            current = node.next;
            if (node.op == Op.WAIT) {
                wakeAt = now + Math.max(1, Math.round(node.number * 20));
                return false;
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
