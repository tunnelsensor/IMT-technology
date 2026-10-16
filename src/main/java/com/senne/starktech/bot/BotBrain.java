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
 * Een opdracht van de server ({@code order("Guard 1", "attack", x, y, z)}) onderbreekt wat hij doet: elke
 * {@code when ordered("attack"):} begint (met {@code order.name} en {@code order.x/y/z}), de andere whens wachten
 * zolang, en daarna gaan ze verder waar ze waren (ook een half gelopen {@code forward(5)}). Een nieuwe opdracht
 * vervangt de vorige. Heeft hij geen {@code when ordered} met die naam, dan negeert hij hem (en zegt dat in het
 * logboek).
 *
 * Met verbinding (zie BotLink) praat hij met zijn server: {@code say}, {@code log}, {@code alarm}, de relais
 * ({@code on}, {@code off}, {@code toggle}, {@code pulse}, {@code is_on}) en {@code send("intruder")}, waarop
 * {@code when message("intruder"):} van de server begint. Zonder verbinding gebeurt dat gewoon niet, maar loopt
 * hij wel door. Wat niet klopt (een def die er niet is, goto zonder Pathfinding-module), komt in het logboek van
 * de core in wiens gebied hij staat. Zijn modules ({@code read} en {@code write}) komen in hoofdstuk 24.
 */
public final class BotBrain {
    private final Bot bot;
    private final BotBody body;
    /** Zijn pinnen: read en write (hoofdstuk 24). */
    private final BotPins pins;
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
    /** Een opdracht die net binnenkwam en de volgende tick begint (na when started, als hij net opstart). */
    private @Nullable Order incoming;

    /** Een opdracht van de server: wat ({@code order.name}), en eventueel een plek ({@code order.x/y/z}). */
    public record Order(String name, @Nullable BlockPos place) {}
    /** Meldingen die al in het logboek staan (niet elke tick dezelfde fout), en wanneer we dat vergeten. */
    private final Set<String> reported = new HashSet<>();
    private long reportedReset;

    /** Het brein van deze Bot, met dit lijf (lopen of vliegen). */
    public BotBrain(Bot bot, BotBody body) {
        this.bot = bot;
        this.body = body;
        this.pins = new BotPins(bot, this);
    }

    /** Zijn pinnen (wat zijn modules zeggen en doen). */
    public BotPins pins() {
        return pins;
    }

    /** Eén when die bezig is: een vingertje van de tolk, met deze robot als host. */
    private final class Run implements Interpreter.Host {
        final Op trigger;
        final Interpreter machine;
        /** De opdracht als dit een {@code when ordered(...)} is, anders null. */
        final @Nullable Order order;
        /** De beweging waar hij op wacht (zolang hij in de rij staat). */
        @Nullable BotMove.Plan plan;

        Run(LogicProgram program, Op trigger, int first, @Nullable Order order) {
            this.trigger = trigger;
            this.machine = new Interpreter(program.graph(), first, globals);
            this.order = order;
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
    public BotBody body() {
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
            if (bot.isLeavingDock()) {
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
        if (incoming != null) {
            startOrder(incoming, now);
            incoming = null;
        }
        // Is de beweging klaar, dan gaat die when (nog deze tick) verder.
        if (body.tick(level) && moving != null) {
            moving.machine.wakeAt(now);
            moving = null;
        }
        // Zolang er een opdracht bezig is, wachten de andere whens.
        boolean ordering = runs.stream().anyMatch(run -> run.order != null);
        int budget = Math.max(1, Math.round(StarkConfig.Robots.STEPS_PER_TICK));
        Iterator<Run> it = runs.iterator();
        while (it.hasNext() && !stopRequested) {
            Run run = it.next();
            if ((!ordering || run.order != null) && run.machine.wakeAt() <= now && run.machine.step(run, now, budget)) {
                it.remove();
            }
        }
        if (stopRequested) {
            stopRequested = false;
            bot.setActive(false); // stop(): hij zet zichzelf uit, en dit brein stopt de volgende tick
            return;
        }
        // Wie het eerst vroeg, mag nu bewegen (een opdracht gaat voor).
        if (moving == null && !queue.isEmpty()) {
            Run next = queue.stream().filter(run -> run.order != null).findFirst().orElse(ordering ? null : queue.peek());
            if (next != null) {
                queue.remove(next);
                moving = next;
                body.start(next.plan);
                next.plan = null;
            }
        }
    }

    // ================================================================ Opdrachten van de server

    /**
     * De server geeft hem een opdracht (zie BotLink.order). Hij moet aan staan, een programma hebben en een
     * {@code when ordered(...)} met die naam; anders negeert hij hem, en dat staat in het logboek.
     */
    public void order(String name, @Nullable BlockPos place) {
        if (program == null || !bot.isActive() || bot.battery() <= 0) {
            report(program == null ? "it has no program, so it ignored the order \"" + name + "\""
                    : "it is switched off, so it ignored the order \"" + name + "\"");
            return;
        }
        boolean known = program.graph().nodes().stream().anyMatch(node -> node.op == Op.ORDERED && node.text.equalsIgnoreCase(name));
        if (!known) {
            report("it has no when ordered(\"" + name + "\"), so it ignored that order");
            return;
        }
        incoming = new Order(name, place == null ? null : place.immutable());
    }

    /**
     * Een opdracht begint: vorige opdrachten stoppen, en wat zijn lijf voor een gewone when deed, wordt onderbroken
     * (wat er nog over is, doet hij daarna). Elke {@code when ordered(...)} met deze naam begint.
     */
    private void startOrder(Order order, long now) {
        runs.removeIf(run -> run.order != null);
        queue.removeIf(run -> run.order != null);
        if (moving != null) {
            BotMove.Plan rest = body.interrupt();
            if (moving.order == null) {
                if (rest != null) {
                    moving.plan = rest;
                    queue.addFirst(moving);
                } else {
                    moving.machine.wakeAt(now); // zijn beweging is af: hij gaat verder na de opdracht
                }
            }
            moving = null;
        }
        for (LogicGraph.Node node : program.graph().nodes()) {
            if (node.op == Op.ORDERED && node.text.equalsIgnoreCase(order.name())) {
                runs.add(new Run(program, Op.ORDERED, node.next, order));
            }
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
        runs.add(new Run(program, trigger, first, null));
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
        incoming = null;
        pins.reset();
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
                if (!bot.board().has(BotModule.PATHFINDING)) {
                    report("goto(...) needs the Pathfinding module on one of its pins");
                    body.markBlocked();
                } else if (values.size() >= 3) {
                    move(run, new BotMove.GoTo(BlockPos.containing(toNumber(values.get(0)), toNumber(values.get(1)),
                            toNumber(values.get(2)))));
                }
            }
            case CHARGE -> {
                if (bot.home() == null) {
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
            case WRITE -> {
                List<Object> values = values(run, node);
                if (values.size() >= 2) {
                    pins.write((int) Math.rint(toNumber(values.get(0))), values.get(1));
                }
            }
            default -> { }
        }
    }

    /**
     * Wat via de server gaat (alleen met verbinding; zonder gebeurt het niet, en loopt zijn programma gewoon door):
     * iets zeggen, een alarm, een relais schakelen, of de server een bericht sturen.
     */
    private void server(Run run, LogicGraph.Node node) {
        SecurityCoreBlockEntity core = BotLink.core(bot);
        if (core == null || !(bot.level() instanceof ServerLevel level)) {
            return;
        }
        String name = bot.getName().getString();
        switch (node.op) {
            case SAY, LOG, ALARM -> LogicRunner.robotMessage(level, core, name, node.op, message(run, node));
            case SEND -> LogicRunner.robotSend(level, core, name, bot.blockPosition(), message(run, node));
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
                BlockPos pos = bot.blockPosition();
                return switch (variable) {
                    case "robot.battery" -> Math.floor(bot.battery() / Math.max(1.0, bot.maxBattery()) * 100);
                    case "robot.x" -> (double) pos.getX();
                    case "robot.y" -> (double) pos.getY();
                    case "robot.z" -> (double) pos.getZ();
                    case "robot.facing" -> body.facing().getName();
                    case "robot.name" -> bot.getName().getString();
                    case "robot.blocked" -> body.blocked();
                    case "robot.connected" -> BotLink.connected(bot);
                    case "is_night" -> bot.level().isNight();
                    case "is_day" -> !bot.level().isNight();
                    // Na when ordered(...): de opdracht van de server (zonder plek: 0).
                    case "order.name" -> run == null || run.order == null ? "" : run.order.name();
                    case "order.x" -> run == null || run.order == null || run.order.place() == null ? 0.0 : (double) run.order.place().getX();
                    case "order.y" -> run == null || run.order == null || run.order.place() == null ? 0.0 : (double) run.order.place().getY();
                    case "order.z" -> run == null || run.order == null || run.order.place() == null ? 0.0 : (double) run.order.place().getZ();
                    default -> false;
                };
            }

            /** {@code is_on("Door")}: dat vraagt hij aan de server (zonder verbinding: nee). */
            @Override
            public boolean isOn(String device) {
                SecurityCoreBlockEntity core = BotLink.core(bot);
                return core != null && bot.level() instanceof ServerLevel level && LogicDevices.isOn(level, core, device);
            }

            @Override
            public @Nullable Object variable(String name) {
                return run == null ? globals.get(name) : run.machine.variable(name);
            }

            /** {@code read(scanPin)}: wat de module op die pin zegt (hoofdstuk 24). */
            @Override
            public Object read(int pin) {
                return pins.read(pin);
            }
        };
    }

    /**
     * Er klopt iets niet aan zijn programma: een regel in het logboek van de core in wiens gebied hij staat
     * (dezelfde melding hooguit één keer per minuut).
     */
    void report(String message) {
        if (!(bot.level() instanceof ServerLevel level)) {
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
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, bot.blockPosition());
        if (core != null) {
            core.logEvent(EventLog.Type.LOGIC, bot.getName().getString() + ": " + message);
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
