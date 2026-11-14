package com.senne.imttech.car;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.logic.program.Expr;
import com.senne.imttech.logic.program.ExprCache;
import com.senne.imttech.logic.program.Interpreter;
import com.senne.imttech.logic.program.LogicGraph;
import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.logic.program.Op;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Het brein van een RC Car (hoofdstuk 108): hier draait zijn programma. Het is dezelfde tolk als die van je server en
 * je robots ({@link Interpreter}); dit brein is zijn "host": het weet wat {@code forward(3)}, {@code mine()} of
 * {@code car.battery} op een auto betekent.
 *
 * Anders dan een guard robot staat een auto niet "aan" of "uit": hij doet niets tot jij iets start, en dan doet hij
 * dat ene ding tot het klaar is (een <b>klus</b>):
 * <ul>
 *   <li><b>Start</b>: het hoofdprogramma, elke {@code when started:} na elkaar;</li>
 *   <li><b>een functie</b>: de stappen van één {@code def} (jouw wens: "als je die kiest, voert hij de code uit voor
 *       die functie vanaf de plek waar hij staat");</li>
 *   <li><b>met de hand</b>: één stap van de pijltjes op zijn scherm (vooruit, achteruit, draaien, breken).</li>
 * </ul>
 * Is hij bezig, dan begint er niets nieuws: eerst Stop. Elke run doet hooguit {@code stepsPerTick} blokjes per tick
 * (het getal van de robots), dus een lus zonder wachten laat de wereld niet hangen. Is zijn energiecel leeg, dan
 * stopt de klus en hoort zijn eigenaar dat.
 *
 * De namen van links ({@code count = 0}) houden hun waarde tussen de klussen, tot hij een ander programma krijgt of de
 * wereld opnieuw laadt.
 */
public final class CarBrain {
    /**
     * Zo heet de klus van het hoofdprogramma, en die van een stap met de hand. Met een # ervoor: zo kan geen functie
     * heten, dus {@code def program():} en {@code def manual():} zijn gewone functies. (Gevonden door de review: zonder
     * dat # deed een functie "manual" niets.)
     */
    public static final String MAIN = "#program";
    public static final String MANUAL = "#manual";

    private final RcCarEntity car;
    private final CarMove body;
    private @Nullable LogicProgram program;
    /** De namen van links, zolang hij dit programma heeft. */
    private Map<String, Object> globals = new HashMap<>();
    private boolean globalsReady;
    /** De when of def die nu loopt, en de eerste stappen van wat daarna nog moet (bij Start: elke when started). */
    private @Nullable Run run;
    private final ArrayDeque<Integer> pending = new ArrayDeque<>();
    /** Wat hij nu doet: "" = niets, {@link #MAIN}, {@link #MANUAL}, of de naam van een functie. */
    private String job = "";
    /** {@code stop()} in zijn programma: na deze tick stopt de klus. */
    private boolean stopRequested;
    /** Meldingen die zijn eigenaar al kreeg (niet elke tick dezelfde), en wanneer we dat vergeten. */
    private final Set<String> reported = new HashSet<>();
    private long reportedReset;

    public CarBrain(RcCarEntity car, CarMove body) {
        this.car = car;
        this.body = body;
    }

    /** Eén when of def die loopt: een vingertje van de tolk, met deze auto als host. */
    private final class Run implements Interpreter.Host {
        final Interpreter machine;

        Run(LogicProgram program, int first) {
            this.machine = new Interpreter(program.graph(), first, globals);
        }

        @Override
        public boolean test(Interpreter machine, LogicGraph.Node node) {
            Expr condition = ExprCache.get(node.text, Op.STARTED);
            if (condition == null) {
                CarBrain.this.report("the check \"" + node.text + "\" is not valid, so it counts as no");
                return false;
            }
            return ExprCache.test(condition, CarBrain.this.facts(this));
        }

        @Override
        public void act(Interpreter machine, LogicGraph.Node node) {
            CarBrain.this.act(this, node);
        }

        @Override
        public Expr.Facts facts(Interpreter machine) {
            return CarBrain.this.facts(this);
        }

        @Override
        public void report(String message) {
            CarBrain.this.report(message);
        }
    }

    // ================================================================ Zijn programma

    public @Nullable LogicProgram program() {
        return program;
    }

    /** Een ander programma (of geen): wat bezig was stopt, en de namen van links beginnen opnieuw. */
    public void setProgram(@Nullable LogicProgram program) {
        stop();
        this.program = program;
        globals = new HashMap<>();
        globalsReady = false;
    }

    /** Wat hij nu doet: "" = niets, {@link #MAIN}, {@link #MANUAL}, of de naam van een functie. */
    public String job() {
        return job;
    }

    /** Is hij met een klus bezig? */
    public boolean busy() {
        return !job.isEmpty();
    }

    // ================================================================ Een klus beginnen

    /**
     * Start: het hoofdprogramma (elke {@code when started:}, na elkaar). Geeft false als hij al bezig is, geen
     * programma heeft, of geen {@code when started:}.
     */
    public boolean startMain() {
        return startMain(null);
    }

    /**
     * Zoals {@link #startMain()}; {@code by} is wie op de knop drukte. Namens die speler breekt en plaatst de auto tijdens
     * deze klus (zie RcCarEntity.actorId); null = namens zijn eigenaar.
     */
    public boolean startMain(@Nullable ServerPlayer by) {
        if (busy() || program == null) {
            return false;
        }
        List<LogicGraph.Node> nodes = new ArrayList<>(program.graph().nodes());
        nodes.sort(java.util.Comparator.comparingInt(node -> node.id));
        for (LogicGraph.Node node : nodes) {
            if (node.op == Op.STARTED) {
                pending.add(node.next);
            }
        }
        if (pending.isEmpty()) {
            return false;
        }
        begin(MAIN, by);
        return true;
    }

    /**
     * Een functie: de stappen van de def met deze naam (zonder parameters), vanaf de plek waar hij staat. Geeft false
     * als hij al bezig is of die functie niet heeft.
     */
    public boolean startFunction(String name) {
        return startFunction(name, null);
    }

    /** Zoals {@link #startFunction(String)}; {@code by} is wie op de knop drukte (zie {@link #startMain(ServerPlayer)}). */
    public boolean startFunction(String name, @Nullable ServerPlayer by) {
        if (busy() || program == null) {
            return false;
        }
        for (LogicGraph.Node node : program.graph().nodes()) {
            if (node.op == Op.DEF && node.text.equals(name) && node.expr.isBlank()) {
                pending.add(node.next);
                begin(name, by);
                return true;
            }
        }
        return false;
    }

    /** Eén stap met de hand (de pijltjes op zijn scherm). Geeft false als hij al bezig is. */
    public boolean startManual(CarMove.Plan plan) {
        return startManual(plan, null);
    }

    /** Zoals {@link #startManual(CarMove.Plan)}; {@code by} is wie op de knop drukte. */
    public boolean startManual(CarMove.Plan plan, @Nullable ServerPlayer by) {
        if (busy()) {
            return false;
        }
        begin(MANUAL, by);
        body.start(plan);
        return true;
    }

    private void begin(String name, @Nullable ServerPlayer by) {
        job = name;
        stopRequested = false;
        reported.clear();
        car.setDriver(by);
        car.setNote(""); // wat er bij de vorige klus niet lukte, staat niet meer op zijn scherm
    }

    /** Alles stopt (de knop Stop, een nieuw programma, oppakken). */
    public void stop() {
        run = null;
        pending.clear();
        job = "";
        stopRequested = false;
        body.stop();
    }

    // ================================================================ Elke tick

    /** Elke tick op de server. */
    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        boolean moved = body.tick(level); // true op de tick dat zijn lijf klaar is
        if (body.takePowerOut()) {
            // Zijn cel is leeg: de klus stopt hier, en zijn eigenaar hoort het.
            String was = job;
            stop();
            car.tell(Component.translatable(car.hasCell() ? "message.imttech.rc_car.cell_empty" : "message.imttech.rc_car.no_cell",
                    car.displayName(), jobTitle(was)), false);
            return;
        }
        if (job.isEmpty()) {
            return;
        }
        if (job.equals(MANUAL)) {
            if (!body.busy()) {
                job = "";
            }
            return;
        }
        if (program == null) {
            stop();
            return;
        }
        if (!globalsReady) {
            globalsReady = true;
            globals = Interpreter.initGlobals(program.graph(), facts(null));
        }
        if (run == null) {
            Integer first = pending.poll();
            if (first == null) {
                // De klus is af.
                String done = job;
                job = "";
                car.tell(Component.translatable("message.imttech.rc_car.done", car.displayName(), jobTitle(done)), true);
                return;
            }
            run = new Run(program, first);
        }
        if (moved) {
            run.machine.wakeAt(now); // zijn lijf is klaar: het programma gaat (nog deze tick) verder
        }
        if (!body.busy() && run.machine.wakeAt() <= now) {
            int budget = Math.max(1, Math.round(ImtConfig.Robots.STEPS_PER_TICK));
            if (run.machine.step(run, now, budget)) {
                run = null; // deze when of def is af: de volgende tick de volgende, of klaar
            }
        }
        if (stopRequested) {
            stop(); // stop() in zijn programma
        }
    }

    /** Hoe we een klus in een melding noemen: "strip_mine", of "its program". */
    private static String jobTitle(String job) {
        return job.equals(MAIN) || job.isEmpty() ? "its program" : job.equals(MANUAL) ? "that step" : job;
    }

    // ================================================================ Wat een blokje doet

    private void act(Run run, LogicGraph.Node node) {
        switch (node.op) {
            case FORWARD, BACK -> {
                int blocks = (int) Math.round(Math.max(node.op.minNumber(), Math.min(node.op.maxNumber(), number(run, node))));
                move(run, new CarMove.Drive(node.op == Op.FORWARD ? blocks : -blocks));
            }
            case TURN_LEFT, TURN_RIGHT -> move(run, new CarMove.Turn(node.op == Op.TURN_RIGHT));
            case MINE, MINE_UP, MINE_DOWN -> {
                int count = (int) Math.round(Math.max(node.op.minNumber(), Math.min(node.op.maxNumber(), number(run, node))));
                move(run, new CarMove.Mine(node.op == Op.MINE ? 0 : node.op == Op.MINE_UP ? 1 : -1, count));
            }
            case PLACE, PLACE_UP, PLACE_DOWN -> {
                List<Object> values = values(run, node);
                String kind = values.isEmpty() ? "" : Expr.text(values.getFirst());
                move(run, new CarMove.Place(node.op == Op.PLACE ? 0 : node.op == Op.PLACE_UP ? 1 : -1, kind));
            }
            case COLLECT -> {
                if (car.level() instanceof ServerLevel level) {
                    CarWork.collect(level, car);
                }
            }
            case UNLOAD -> {
                if (car.level() instanceof ServerLevel level) {
                    CarWork.unload(level, car, body.facing());
                }
            }
            case SAY -> car.tell(Component.literal("<" + car.displayName() + "> " + message(run, node)), false);
            case STOP -> {
                stopRequested = true;
                run.machine.pause();
            }
            default -> { }
        }
    }

    /** Dit blokje wil dat zijn lijf iets doet: het programma wacht tot dat klaar is. */
    private void move(Run run, CarMove.Plan plan) {
        body.start(plan);
        run.machine.pause();
    }

    /** Het bericht van say: de tekst, of wat de berekening nu oplevert. */
    private String message(Run run, LogicGraph.Node node) {
        if (node.expr.isEmpty()) {
            return node.text;
        }
        Expr expr = ExprCache.value(node.expr);
        return expr == null ? node.expr : Expr.text(ExprCache.eval(expr, facts(run)));
    }

    /** Het getal van een blokje: gewoon, of uitgerekend ({@code forward(steps * 2)}). */
    private double number(Run run, LogicGraph.Node node) {
        if (node.expr.isEmpty()) {
            return node.number;
        }
        Expr expr = ExprCache.value(node.expr);
        return expr == null ? 0 : ExprCache.eval(expr, facts(run)) instanceof Double number ? number : 0;
    }

    /** De waarden van een blokje met een rijtje ({@code place("cobblestone")}), uitgerekend. */
    private List<Object> values(Run run, LogicGraph.Node node) {
        List<Object> values = new ArrayList<>();
        if (node.expr.isBlank()) {
            return values; // place(): niets tussen de haakjes
        }
        List<Expr> exprs = ExprCache.values(node.expr);
        if (exprs == null) {
            report("the values \"" + node.expr + "\" are not valid");
            return values;
        }
        for (Expr expr : exprs) {
            values.add(ExprCache.eval(expr, facts(run)));
        }
        return values;
    }

    // ================================================================ Wat hij weet

    /** {@code car.battery}, {@code car.x}, {@code block()}, is_night... en de namen van deze run. */
    private Expr.Facts facts(@Nullable Run run) {
        return new Expr.Facts() {
            @Override
            public Object value(String variable) {
                BlockPos pos = body.cell();
                return switch (variable) {
                    case "car.battery" -> Math.floor(car.cellPercent());
                    case "car.x" -> (double) pos.getX();
                    case "car.y" -> (double) pos.getY();
                    case "car.z" -> (double) pos.getZ();
                    case "car.facing" -> body.facing().getName();
                    case "car.name" -> car.displayName();
                    case "car.blocked" -> body.blocked();
                    case "car.full" -> CarWork.full(car.trunk());
                    case "car.items" -> (double) CarWork.itemsIn(car.trunk());
                    case "car.blocks" -> (double) CarWork.blocksIn(car.trunk());
                    case "is_night" -> car.level().isNight();
                    case "is_day" -> !car.level().isNight();
                    default -> false;
                };
            }

            /** Een auto heeft geen server, dus geen apparaten: altijd nee. */
            @Override
            public boolean isOn(String device) {
                return false;
            }

            @Override
            public @Nullable Object variable(String name) {
                return run == null ? globals.get(name) : run.machine.variable(name);
            }

            /** {@code block()}, {@code block_up()}, {@code block_down()}: het blok voor, boven of onder hem. */
            @Override
            public Object block(int direction) {
                if (!(car.level() instanceof ServerLevel level)) {
                    return "air";
                }
                Direction way = direction == 0 ? body.facing() : direction > 0 ? Direction.UP : Direction.DOWN;
                return CarWork.blockName(level, body.cell().relative(way));
            }
        };
    }

    /**
     * Er lukt iets niet (een blok dat hij niet kan breken, een lege kofferbak), of er klopt iets niet aan zijn
     * programma: zijn eigenaar leest het in de chat, en het staat in zijn scherm. Dezelfde melding hooguit één keer per
     * klus (en hooguit één keer per minuut).
     */
    public void report(String message) {
        if (!(car.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (now >= reportedReset) {
            reported.clear();
            reportedReset = now + 1200;
        }
        car.setNote(message);
        if (reported.add(message)) {
            car.tell(Component.translatable("message.imttech.rc_car.report", car.displayName(), message), false);
        }
    }

    // ================================================================ Voor de tests

    /** De waarde van een naam van links ({@code count = 0}), of null. */
    public @Nullable Object global(String name) {
        return globals.get(name);
    }
}
