package com.senne.starktech.bypass;

import com.senne.starktech.config.StarkConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.jetbrains.annotations.Nullable;

/**
 * Het stroomcircuit in een keycardlezer (hoofdstuk 12, jouw keuze): een raster met stukjes draad.
 * Links komt het signaal van de kaart binnen (CARD), rechts gaat het naar de server (ACCEPT). Je
 * draait de stukjes tot het signaal van CARD naar ACCEPT loopt, zonder ALARM te raken.
 *
 * <pre>
 *        ALARM
 *   CARD =+  |  +=  ==+
 *         +==+  |    +== ACCEPT
 * </pre>
 *
 * Deze klasse is alleen de puzzel (geen scherm), zodat de server en je spel PRECIES hetzelfde raster
 * maken uit hetzelfde getal ({@code seed}) en de tests hem kunnen oplossen. De server stuurt alleen
 * dat getal; als jij klaar bent stuur je terug hoe je elk stukje gedraaid hebt, en de server rekent
 * zelf na of het signaal echt bij ACCEPT komt.
 *
 * <b>Richtingen als bitjes.</b> Elk stukje heeft verbindingen naar boven (N), rechts (E), onder (S) en
 * links (W). Een recht stuk is {@code N|S}, een bocht {@code N|E}. Draaien = de bitjes één plek
 * opschuiven (N wordt E, E wordt S, ...).
 */
public final class CircuitPuzzle {
    public static final int N = 1;
    public static final int E = 2;
    public static final int S = 4;
    public static final int W = 8;
    /** De vier kanten, in de volgorde waarin we ze aflopen. */
    public static final int[] SIDES = {N, E, S, W};

    /** De vormen, in de stand "niet gedraaid". */
    public static final int STRAIGHT = N | S;
    public static final int CORNER = N | E;
    public static final int TEE = N | E | S;
    public static final int CROSS = N | E | S | W;

    /** De kans dat het pad in een kolom omhoog of omlaag gaat (anders gaat het rechtdoor). */
    private static final float TURN_CHANCE = 0.65f;
    /** Zoveel vakjes gaat het pad per keer omhoog of omlaag. */
    private static final int[] STEPS = {-2, -1, 1, 2};

    /** Een ALARM-punt: aan welke kant ({@code side}, naar buiten) van welk vakje ({@code cell}) het zit. */
    public record Terminal(int cell, int side) {}

    /**
     * Waar het signaal nu komt: welke vakjes oplichten, of het bij ACCEPT komt, en welke ALARM-punten
     * het raakt.
     */
    public record Signal(boolean[] lit, boolean accepted, List<Terminal> alarms) {
        /** Klaar: het signaal komt bij ACCEPT en raakt geen ALARM. */
        public boolean solved() {
            return accepted && alarms.isEmpty();
        }
    }

    private final int columns;
    private final int rows;
    private final int cardRow;
    private final int acceptRow;
    /** De vorm van elk vakje (bitjes, niet gedraaid). Vakje = rij * kolommen + kolom. */
    private final int[] shapes;
    /** Hoe vaak elk stukje aan het begin rechtsom gedraaid is (0-3). */
    private final int[] start;
    /** Eén manier om het op te lossen: hoe elk stukje op het pad moet staan (voor de tests en de rondleiding). */
    private final int[] solution;
    /** Welke vakjes op dat pad liggen. */
    private final boolean[] path;
    private final List<Terminal> alarms;

    private CircuitPuzzle(int columns, int rows, int cardRow, int acceptRow, int[] shapes, int[] start, int[] solution,
                          boolean[] path, List<Terminal> alarms) {
        this.columns = columns;
        this.rows = rows;
        this.cardRow = cardRow;
        this.acceptRow = acceptRow;
        this.shapes = shapes;
        this.start = start;
        this.solution = solution;
        this.path = path;
        this.alarms = List.copyOf(alarms);
    }

    // ================================================================ Maken

    /**
     * Maakt het raster voor een kaart van dit niveau (1-5). Hetzelfde getal en niveau geven altijd
     * hetzelfde raster (java.util.Random rekent overal hetzelfde).
     *
     * Hoe: eerst een pad van CARD naar ACCEPT, kolom voor kolom (in elke kolom misschien een stukje
     * omhoog of omlaag). Op dat pad komen precies de rechte stukken en bochten die nodig zijn. De rest
     * van het raster vullen we met willekeurige stukjes: die zijn om je op het verkeerde been te zetten
     * (en soms vormen ze een andere goede weg; die telt ook). De ALARM-punten komen bij voorkeur naast
     * het pad aan de rand: draai je daar een stukje de verkeerde kant op, dan raak je ALARM. Tot slot
     * draaien we alle stukjes willekeurig, zodat het niet al klaar is.
     */
    /** Een kaartniveau tussen 1 en 5. */
    public static int clampLevel(int level) {
        return Math.max(1, Math.min(StarkConfig.Bypass.CIRCUIT_COLUMNS.length, level));
    }

    /** Hoeveel seconden je hebt voor een kaart van dit niveau (1-5): StarkConfig.Bypass.CIRCUIT_SECONDS. */
    public static int circuitSeconds(int level) {
        return StarkConfig.Bypass.CIRCUIT_SECONDS[clampLevel(level) - 1];
    }

    public static CircuitPuzzle generate(long seed, int level) {
        int index = CircuitPuzzle.clampLevel(level) - 1;
        int columns = StarkConfig.Bypass.CIRCUIT_COLUMNS[index];
        int rows = StarkConfig.Bypass.CIRCUIT_ROWS[index];
        Random random = new Random(seed * 31L + level);
        int cardRow = random.nextInt(rows);
        int acceptRow = random.nextInt(rows);

        // Het pad: per vakje langs welke kant het signaal binnenkomt en weer weggaat (0 = niet op het pad).
        int[] in = new int[columns * rows];
        int[] out = new int[columns * rows];
        int row = cardRow;
        for (int col = 0; col < columns; col++) {
            int target = row;
            if (col == columns - 1) {
                target = acceptRow;
            } else if (random.nextFloat() < TURN_CHANCE) {
                while (target == row) {
                    target = Math.max(0, Math.min(rows - 1, row + STEPS[random.nextInt(STEPS.length)]));
                }
            }
            int step = Integer.signum(target - row);
            int from = W;
            for (int y = row; ; y += step) {
                int cell = y * columns + col;
                in[cell] = from;
                out[cell] = y == target ? E : step > 0 ? S : N;
                from = opposite(out[cell]);
                if (y == target) {
                    break;
                }
            }
            row = target;
        }
        int[] need = new int[columns * rows];
        for (int cell = 0; cell < need.length; cell++) {
            need[cell] = in[cell] | out[cell];
        }

        // De stukjes: op het pad precies wat nodig is, de rest willekeurig.
        int[] shapes = new int[columns * rows];
        int[] solution = new int[columns * rows];
        boolean[] path = new boolean[columns * rows];
        for (int cell = 0; cell < shapes.length; cell++) {
            path[cell] = need[cell] != 0;
            if (path[cell]) {
                boolean straight = need[cell] == (N | S) || need[cell] == (E | W);
                shapes[cell] = straight ? STRAIGHT : CORNER;
                solution[cell] = turnsTo(shapes[cell], need[cell]);
            } else {
                int roll = random.nextInt(100);
                shapes[cell] = roll < 30 ? STRAIGHT : roll < 70 ? CORNER : roll < 95 ? TEE : CROSS;
            }
        }

        // De ALARM-punten: aan de rand, nooit op de plek van CARD of ACCEPT. Het liefst naast een bocht van
        // het pad die je met één verkeerde draai precies naar ALARM draait (de bocht houdt de kant waar het
        // signaal binnenkomt, en wijst dan naar buiten); anders gewoon naast het pad.
        List<Terminal> candidates = new ArrayList<>();
        for (int x = 0; x < columns; x++) {
            candidates.add(new Terminal(x, N));
            candidates.add(new Terminal((rows - 1) * columns + x, S));
        }
        for (int y = 0; y < rows; y++) {
            if (y != cardRow) {
                candidates.add(new Terminal(y * columns, W));
            }
            if (y != acceptRow) {
                candidates.add(new Terminal(y * columns + columns - 1, E));
            }
        }
        Collections.shuffle(candidates, random);
        candidates.sort(Comparator.comparingInt(terminal -> {
            int cell = terminal.cell();
            if (need[cell] == 0) {
                return 2;
            }
            boolean wrongTurn = shapes[cell] == CORNER && terminal.side() != out[cell] && terminal.side() != opposite(in[cell]);
            return wrongTurn ? 0 : 1;
        }));
        int alarmCount = Math.min(candidates.size(), StarkConfig.Bypass.CIRCUIT_ALARMS[index]);
        List<Terminal> alarms = new ArrayList<>(candidates.subList(0, alarmCount));

        // Alles door elkaar draaien, tot het niet al klaar is en ALARM niet al geraakt wordt.
        CircuitPuzzle puzzle = new CircuitPuzzle(columns, rows, cardRow, acceptRow, shapes, new int[shapes.length],
                solution, path, alarms);
        for (int attempt = 0; attempt < 100; attempt++) {
            for (int cell = 0; cell < shapes.length; cell++) {
                puzzle.start[cell] = random.nextInt(4);
            }
            Signal signal = puzzle.signal(puzzle.start);
            if (!signal.accepted() && signal.alarms().isEmpty()) {
                break;
            }
        }
        return puzzle;
    }

    /** Hoe vaak je deze vorm rechtsom moet draaien om precies deze verbindingen te krijgen. */
    private static int turnsTo(int shape, int wanted) {
        for (int turns = 0; turns < 4; turns++) {
            if (rotate(shape, turns) == wanted) {
                return turns;
            }
        }
        return 0;
    }

    // ================================================================ Rekenen

    /** De verbindingen van een vorm, zoveel keer een kwart rechtsom gedraaid (N wordt E, E wordt S...). */
    public static int rotate(int mask, int quarterTurns) {
        int result = mask & 15;
        for (int i = 0; i < Math.floorMod(quarterTurns, 4); i++) {
            result = ((result << 1) | (result >> 3)) & 15;
        }
        return result;
    }

    /** De kant tegenover: N bij S, E bij W. */
    public static int opposite(int side) {
        return rotate(side, 2);
    }

    /** Hoeveel kolommen en rijen een kant opschuift: N = (0, -1), E = (1, 0)... */
    public static int dx(int side) {
        return side == E ? 1 : side == W ? -1 : 0;
    }

    public static int dy(int side) {
        return side == S ? 1 : side == N ? -1 : 0;
    }

    /**
     * Waar het signaal komt als de stukjes zo gedraaid zijn. Het begint bij CARD (de linkerkant van het
     * eerste vakje in de rij van CARD) en loopt door elk stukje dat aansluit: twee buren zijn verbonden
     * als ze ALLEBEI een verbinding naar elkaar hebben.
     */
    public Signal signal(int[] rotations) {
        boolean[] lit = new boolean[shapes.length];
        List<Terminal> touched = new ArrayList<>();
        boolean accepted = false;
        ArrayDeque<Integer> todo = new ArrayDeque<>();
        int first = cardRow * columns;
        if ((connections(first, rotations) & W) != 0) {
            lit[first] = true;
            todo.add(first);
        }
        while (!todo.isEmpty()) {
            int cell = todo.poll();
            int x = cell % columns;
            int y = cell / columns;
            int mask = connections(cell, rotations);
            for (int side : SIDES) {
                if ((mask & side) == 0) {
                    continue;
                }
                int nx = x + dx(side);
                int ny = y + dy(side);
                if (nx >= 0 && nx < columns && ny >= 0 && ny < rows) {
                    int next = ny * columns + nx;
                    if (!lit[next] && (connections(next, rotations) & opposite(side)) != 0) {
                        lit[next] = true;
                        todo.add(next);
                    }
                    continue;
                }
                // Het signaal gaat hier het raster uit: naar ACCEPT, naar een ALARM, of nergens heen.
                if (x == columns - 1 && y == acceptRow && side == E) {
                    accepted = true;
                }
                Terminal exit = new Terminal(cell, side);
                if (alarms.contains(exit) && !touched.contains(exit)) {
                    touched.add(exit);
                }
            }
        }
        return new Signal(lit, accepted, touched);
    }

    /** De verbindingen van dit vakje als het zo gedraaid is. */
    public int connections(int cell, int[] rotations) {
        return rotate(shapes[cell], rotations[cell]);
    }

    /**
     * Leest de draaiingen die een speler terugstuurt: precies één getal 0-3 per vakje. Null als het niet
     * klopt (te kort, te lang, of rare getallen).
     */
    public @Nullable int[] readRotations(byte[] sent) {
        if (sent.length != shapes.length) {
            return null;
        }
        int[] rotations = new int[sent.length];
        for (int i = 0; i < sent.length; i++) {
            if (sent[i] < 0 || sent[i] > 3) {
                return null;
            }
            rotations[i] = sent[i];
        }
        return rotations;
    }

    // ================================================================ Lezen

    public int columns() {
        return columns;
    }

    public int rows() {
        return rows;
    }

    public int cells() {
        return shapes.length;
    }

    public int cardRow() {
        return cardRow;
    }

    public int acceptRow() {
        return acceptRow;
    }

    /** De vorm van een vakje (bitjes, niet gedraaid). */
    public int shape(int cell) {
        return shapes[cell];
    }

    /** Hoe de stukjes aan het begin gedraaid zijn (een kopie: daar mag je in draaien). */
    public int[] start() {
        return start.clone();
    }

    /**
     * Een draaiing die het oplost: de stukjes op het pad goed, de rest zoals aan het begin. (De stukjes
     * op het pad wijzen alleen naar elkaar, dus de rest kan het signaal dan nooit raken.)
     */
    public int[] solution() {
        int[] result = start.clone();
        for (int cell = 0; cell < shapes.length; cell++) {
            if (path[cell]) {
                result[cell] = solution[cell];
            }
        }
        return result;
    }

    /** Ligt dit vakje op het pad dat we maakten? (Voor de tests en de rondleiding: de speler ziet dat niet.) */
    public boolean isOnPath(int cell) {
        return path[cell];
    }

    /** De vakjes van het pad, in de volgorde van CARD naar ACCEPT (voor de rondleiding). */
    public List<Integer> pathOrder() {
        List<Integer> order = new ArrayList<>();
        int cell = cardRow * columns;
        int from = W;
        while (cell >= 0 && path[cell] && order.size() < shapes.length) {
            order.add(cell);
            int out = rotate(shapes[cell], solution[cell]) & ~from;
            int x = cell % columns + dx(out);
            int y = cell / columns + dy(out);
            from = opposite(out);
            cell = x >= 0 && x < columns && y >= 0 && y < rows ? y * columns + x : -1;
        }
        return order;
    }

    public List<Terminal> alarms() {
        return alarms;
    }
}
