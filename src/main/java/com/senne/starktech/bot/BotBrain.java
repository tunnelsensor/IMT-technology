package com.senne.starktech.bot;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.logic.LogicDevices;
import com.senne.starktech.logic.LogicRunner;
import com.senne.starktech.logic.program.Expr;
import com.senne.starktech.logic.program.ExprCache;
import com.senne.starktech.logic.program.Interpreter;
import com.senne.starktech.logic.program.LogicGraph;
import com.senne.starktech.logic.program.LogicProgram;
import com.senne.starktech.logic.program.Op;
import com.senne.starktech.robot.GuardRobotEntity;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import com.senne.starktech.security.os.EventLog;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Het brein van een robot (hoofdstuk 23): hier draait zijn programma, op de robot zelf (je server bewaart het
 * alleen). Het is dezelfde tolk als die van je server ({@link Interpreter}); dit brein is zijn "host": het weet
 * wat {@code forward(3)} of {@code robot.battery} op een robot betekent.
 * <ul>
 *   <li>Staat hij aan, heeft hij stroom en een programma, dan begint elke {@code when started:} (stapt hij net
 *       uit zijn dock, dan pas als hij eruit is). Uit, een lege batterij of een nieuw programma: alles stopt, en
 *       de volgende keer begint hij weer bij {@code when started:} met verse namen, zoals een computer die
 *       opnieuw opstart.</li>
 *   <li>{@code when every(10):} gaat elke 10 seconden af, ook als een andere when nog bezig is: zijn whens lopen
 *       naast elkaar, net als op de server. Elke when doet hooguit {@code STEPS_PER_TICK} blokjes per tick, dus
 *       een lus zonder wachten laat de wereld niet hangen.</li>
 *   <li>Zijn lijf ({@link BotMove}) doet één beweging tegelijk. Wil een when lopen terwijl een andere loopt, dan
 *       wacht hij zijn beurt af: wie het eerst vroeg, is eerst.</li>
 * </ul>
 * Met verbinding (zie BotLink) praat hij met zijn server: {@code say}, {@code log}, {@code alarm}, de relais
 * ({@code on}, {@code off}, {@code toggle}, {@code pulse}, {@code is_on}) en {@code send("intruder")}, waarop
 * {@code when message("intruder"):} van de server begint. Zonder verbinding gebeurt dat gewoon niet, maar loopt
 * hij wel door. Wat niet klopt (een def die er niet is, goto zonder Pathfinding-module), komt in het logboek van
 * de core in wiens gebied hij staat. Zijn modules ({@code read} en {@code write}) komen in hoofdstuk 24.
 */
public final class BotBrain {
    private final GuardRobotEntity robot;
    private final BotMove body;
    private @Nullable LogicProgram program;
    /** De namen van links ({@code scanPin = pin(1)}), zolang hij aan staat. */
    private Map<String, Object> globals = new HashMap<>();
    private final List<Run> runs = new ArrayList<>();
    /** Wie wacht om te mogen bewegen (in de volgorde waarin ze het vroegen), en wie nu beweegt. */
    private final ArrayDeque<Run> queue = new ArrayDeque<>();
    private @Nullable Run moving;
    /** Per {@code every}-blokje (zijn nummer) wanneer hij weer afgaat. */
    private final Map<Integer, Long> timers = new HashMap<>();
    private boolean started;
    /** {@code stop()}: na deze tick zet hij zichzelf uit. */
    private boolean stopRequested;
    /** Meldingen die al in het logboek staan (niet elke tick dezelfde fout), en wanneer we dat vergeten. */
    private final Set<String> reported = new HashSet<>();
    private long reportedReset;

    public BotBrain(GuardRobotEntity robot) {
        this.robot = robot;
        this.body = new BotMove(robot);
    }

    /** Eén when die bezig is: een vingertje van de tolk, met deze robot als host. */
    private final class Run implements Interpreter.Host {
        final Op trigger;
        final Interpreter machine;
        /** De beweging waar hij op wacht (zolang hij in de rij staat). */
        @Nullable BotMove.Plan plan;

        Run(LogicProgram program, Op trigger, int first) {
            this.trigger = trigger;
            this.machine = new Interpreter(program.graph(), first, globals);
        }

        @Override
        public boolean test(Interpreter machine, LogicGraph.Node node) {
            Expr condition = ExprCache.get(node.text, trigger);
            if (condition == null) {
                BotBrain.this.report("the check \"" + node.text + "\" is not valid, so it counts as no");
                return false;
            }
            return ExprCache.test(condition, BotBrain.this.facts(this));
        }

        @Override
        public void act(Interpreter machine, LogicGraph.Node node) {
            BotBrain.this.act(this, node);
        }

        @Override
        public Expr.Facts facts(Interpreter machine) {
            return BotBrain.this.facts(this);
        }

        @Override
        public void report(String message) {
            BotBrain.this.report(message);
        }
    }

    // ================================================================ Zijn programma

    public @Nullable LogicProgram program() {
        return program;
    }

    public boolean hasProgram() {
        return program != null;
    }

    /** Een ander programma (of geen): wat bezig was stopt, en (als hij aan staat) begint het nieuwe bij when started. */
    public void setProgram(@Nullable LogicProgram program) {
        stop();
        this.program = program;
    }

    /** Zijn lijf: de kant waar hij naar kijkt, en of hij geblokkeerd was. */
    public BotMove body() {
        return body;
    }

    /** Wil hij nu naar zijn dock (om te laden, zie BotMove)? */
    public boolean wantsDock() {
        return body.wantsDock();
    }

    // ================================================================ Elke tick

    /** Elke tick terwijl hij aan staat, stroom heeft en een programma. */
    public void tick(ServerLevel level) {
        if (program == null) {
            return;
        }
        long now = level.getGameTime();
        if (!started) {
            if (robot.isSteppingOut()) {
                return; // eerst uit zijn dock
            }
            started = true;
            globals = Interpreter.initGlobals(program.graph(), facts(null));
            for (LogicGraph.Node node : program.graph().nodes()) {
                if (node.op == Op.STARTED) {
                    start(Op.STARTED, node.next);
                }
            }
        }
        timers(now);
        // Is de beweging klaar, dan gaat die when (nog deze tick) verder.
        if (body.tick(level) && moving != null) {
            moving.machine.wakeAt(now);
            moving = null;
        }
        int budget = Math.max(1, Math.round(StarkConfig.Robots.STEPS_PER_TICK));
        Iterator<Run> it = runs.iterator();
        while (it.hasNext() && !stopRequested) {
            Run run = it.next();
            if (run.machine.wakeAt() <= now && run.machine.step(run, now, budget)) {
                it.remove();
            }
        }
        if (stopRequested) {
            stopRequested = false;
            robot.setActive(false); // stop(): hij zet zichzelf uit, en dit brein stopt de volgende tick
            return;
        }
        // Wie het eerst vroeg, mag nu bewegen.
        if (moving == null && !queue.isEmpty()) {
            moving = queue.poll();
            body.start(moving.plan);
            moving.plan = null;
        }
    }

    /** De {@code every}-blokjes die nu afgaan (de eerste keer na één keer wachten, zoals op de server). */
    private void timers(long now) {
        for (LogicGraph.Node node : program.graph().nodes()) {
            if (node.op != Op.EVERY) {
                continue;
            }
            long interval = Math.max(20, Math.round(node.number * 20));
            Long next = timers.get(node.id);
            if (next == null) {
                timers.put(node.id, now + interval);
            } else if (now >= next) {
                timers.put(node.id, now + interval);
                start(Op.EVERY, node.next);
            }
        }
    }

    /** Een when begint (als er nog plek is: hooguit {@code MAX_RUNS} tegelijk). */
    private void start(Op trigger, int first) {
        if (runs.size() >= Math.max(1, Math.round(StarkConfig.Robots.MAX_RUNS))) {
            report("too many whens at once: when " + trigger.code() + " had to be skipped");
            return;
        }
        runs.add(new Run(program, trigger, first));
    }

    /** Alles stopt (uit, lege batterij, nieuw programma). De volgende keer begint hij weer bij when started. */
    public void stop() {
        if (!started && runs.isEmpty() && !body.busy()) {
            return;
        }
        runs.clear();
        queue.clear();
        moving = null;
        timers.clear();
        started = false;
        stopRequested = false;
        body.stop();
    }

    // ================================================================ Wat een blokje doet

    private void act(Run run, LogicGraph.Node node) {
        switch (node.op) {
            case FORWARD, BACK -> {
                int blocks = (int) Math.round(Math.max(node.op.minNumber(), Math.min(node.op.maxNumber(), number(run, node))));
                move(run, new BotMove.Walk(node.op == Op.FORWARD ? blocks : -blocks));
            }
            case TURN_LEFT, TURN_RIGHT -> move(run, new BotMove.Turn(node.op == Op.TURN_RIGHT));
            case GOTO -> {
                List<Object> values = values(run, node);
                if (!robot.board().has(BotModule.PATHFINDING)) {
                    report("goto(...) needs the Pathfinding module on one of its pins");
                    body.markBlocked();
                } else if (values.size() >= 3) {
                    move(run, new BotMove.GoTo(BlockPos.containing(toNumber(values.get(0)), toNumber(values.get(1)),
                            toNumber(values.get(2)))));
                }
            }
            case CHARGE -> {
                if (robot.home() == null) {
                    report("charge(): it has no Robot Dock to charge on");
                    body.markBlocked();
                } else {
                    move(run, new BotMove.Charge(Math.max(1, Math.min(100, number(run, node)))));
                }
            }
            case STOP -> {
                stopRequested = true;
                run.machine.pause();
            }
            case SAY, LOG, ALARM, ON, OFF, TOGGLE, PULSE, SEND -> server(run, node);
            // write naar een module: hoofdstuk 24.
            default -> { }
        }
    }

    /**
     * Wat via de server gaat (alleen met verbinding; zonder gebeurt het niet, en loopt zijn programma gewoon door):
     * iets zeggen, een alarm, een relais schakelen, of de server een bericht sturen.
     */
    private void server(Run run, LogicGraph.Node node) {
        SecurityCoreBlockEntity core = BotLink.core(robot);
        if (core == null || !(robot.level() instanceof ServerLevel level)) {
            return;
        }
        String name = robot.getName().getString();
        switch (node.op) {
            case SAY, LOG, ALARM -> LogicRunner.robotMessage(level, core, name, node.op, message(run, node));
            case SEND -> LogicRunner.robotSend(level, core, name, robot.blockPosition(), message(run, node));
            default -> {
                double seconds = node.op == Op.PULSE ? number(run, node) : 0;
                if (!LogicRunner.robotSwitch(level, core, node.op, node.text, seconds)) {
                    report("there is no device called \"" + node.text + "\" in this network");
                }
            }
        }
    }

    /** Het bericht van say, log, alarm of send: de tekst, of wat de berekening nu oplevert. */
    private String message(Run run, LogicGraph.Node node) {
        if (node.expr.isEmpty()) {
            return node.text;
        }
        Expr expr = ExprCache.value(node.expr);
        return expr == null ? node.expr : Expr.text(ExprCache.eval(expr, facts(run)));
    }

    /** Deze when wil bewegen: hij wacht (in de rij) tot zijn lijf het gedaan heeft. */
    private void move(Run run, BotMove.Plan plan) {
        run.plan = plan;
        queue.add(run);
        run.machine.pause();
    }

    /** Het getal van een blokje: gewoon, of uitgerekend ({@code forward(steps * 2)}). */
    private double number(Run run, LogicGraph.Node node) {
        if (node.expr.isEmpty()) {
            return node.number;
        }
        Expr expr = ExprCache.value(node.expr);
        return expr == null ? 0 : toNumber(ExprCache.eval(expr, facts(run)));
    }

    /** De waarden van een blokje met een rijtje ({@code goto(x, y, z)}), uitgerekend. */
    private List<Object> values(Run run, LogicGraph.Node node) {
        List<Expr> exprs = ExprCache.values(node.expr);
        List<Object> values = new ArrayList<>();
        if (exprs == null) {
            report("the values \"" + node.expr + "\" are not valid");
            return values;
        }
        for (Expr expr : exprs) {
            values.add(ExprCache.eval(expr, facts(run)));
        }
        return values;
    }

    private static double toNumber(Object value) {
        return value instanceof Double number ? number : 0;
    }

    // ================================================================ Wat hij weet

    /** {@code robot.battery}, {@code robot.x}, is_night... en de namen van deze when. */
    private Expr.Facts facts(@Nullable Run run) {
        return new Expr.Facts() {
            @Override
            public Object value(String variable) {
                BlockPos pos = robot.blockPosition();
                return switch (variable) {
                    case "robot.battery" -> Math.floor(robot.battery() / Math.max(1.0, robot.maxBattery()) * 100);
                    case "robot.x" -> (double) pos.getX();
                    case "robot.y" -> (double) pos.getY();
                    case "robot.z" -> (double) pos.getZ();
                    case "robot.facing" -> body.facing().getName();
                    case "robot.name" -> robot.getName().getString();
                    case "robot.blocked" -> body.blocked();
                    case "robot.connected" -> BotLink.connected(robot);
                    case "is_night" -> robot.level().isNight();
                    case "is_day" -> !robot.level().isNight();
                    // order.* (stap 6): nog niets.
                    default -> false;
                };
            }

            /** {@code is_on("Door")}: dat vraagt hij aan de server (zonder verbinding: nee). */
            @Override
            public boolean isOn(String device) {
                SecurityCoreBlockEntity core = BotLink.core(robot);
                return core != null && robot.level() instanceof ServerLevel level && LogicDevices.isOn(level, core, device);
            }

            @Override
            public @Nullable Object variable(String name) {
                return run == null ? globals.get(name) : run.machine.variable(name);
            }
        };
    }

    /**
     * Er klopt iets niet aan zijn programma: een regel in het logboek van de core in wiens gebied hij staat
     * (dezelfde melding hooguit één keer per minuut).
     */
    private void report(String message) {
        if (!(robot.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (now >= reportedReset) {
            reported.clear();
            reportedReset = now + 1200;
        }
        if (!reported.add(message)) {
            return;
        }
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, robot.blockPosition());
        if (core != null) {
            core.logEvent(EventLog.Type.LOGIC, robot.getName().getString() + ": " + message);
        }
    }

    // ================================================================ Voor de tests

    /** De waarde van een naam van links ({@code count = 0}), of null. */
    public @Nullable Object global(String name) {
        return globals.get(name);
    }

    /** Is zijn programma begonnen, en is alles klaar (geen when meer bezig, geen beweging)? */
    public boolean idle() {
        return started && runs.isEmpty() && !body.busy();
    }

    /** Is zijn programma begonnen (when started is gedaan)? */
    public boolean started() {
        return started;
    }

    /** Hoeveel whens er nu bezig zijn. */
    public int running() {
        return runs.size();
    }
}
