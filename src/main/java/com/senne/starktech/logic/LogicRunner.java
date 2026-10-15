package com.senne.starktech.logic;

import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.logic.program.Expr;
import com.senne.starktech.logic.program.ExprCache;
import com.senne.starktech.logic.program.Interpreter;
import com.senne.starktech.logic.program.LogicGraph;
import com.senne.starktech.logic.program.LogicProgram;
import com.senne.starktech.logic.program.Op;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.registry.ModSounds;
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
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/**
 * Het brein van de server (jouw wens, 7 oktober): hier draaien de diagrammen.
 *
 * Als er iets gebeurt (een kaart tegen een lezer, een code op een paneel, een relais dat aan gaat,
 * een timer), kijkt de server in al zijn diagrammen welke "when" daarop past. Elk passend when-blok
 * wordt een "run": een vingertje dat de blokjes afloopt.
 * <ul>
 *   <li>Een run begint pas na de <b>reactietijd</b> van de server: hoe meer CPU's, hoe sneller.</li>
 *   <li>Er kunnen er maar zoveel tegelijk bezig zijn als je <b>RAM</b> toelaat (een run die
 *       {@code wait(5)} doet, telt mee zolang hij wacht). De rest wacht in een rij.</li>
 *   <li>Alleen diagrammen die op je <b>SSD's</b> passen draaien (de bovenste in de lijst eerst).</li>
 * </ul>
 * Een run loopt de blokjes af: een actie doen, bij een if kiezen tussen yes en no (en daarna verder
 * bij "after"), bij een eigen blok (CALL) eerst de stappen van die def en dan verder, bij wait even
 * pauzeren. Alles wat hij doet komt in het logboek.
 *
 * Wat er bezig is, onthouden we niet als de wereld wordt opgeslagen: na het laden begint alles opnieuw.
 */
public final class LogicRunner {
    private LogicRunner() {}

    /** Zoveel runs mogen er hooguit in de rij staan (daarna valt de oudste af: "out of memory"). */
    private static final int MAX_QUEUE = 32;
    /** Zoveel triggers per seconde neemt één server hooguit aan (tegen eindeloze kettingreacties). */
    private static final int MAX_TRIGGERS_PER_SECOND = 40;
    /**
     * Zoveel blokjes loopt een run hooguit af in één tick (zie Interpreter). Wat dan nog over is, doet
     * hij de volgende tick: zo kan een lang of druk diagram de server nooit laten vastlopen.
     */
    private static final int STEPS_PER_TICK = 64;
    /** Zo lang (ticks) jaagt de beveiliging op wie een alarm() veroorzaakte. */
    private static final int ALARM_HUNT = 600;
    /** Om de zoveel ticks neemt de kern de namen van de apparaten over van de eigenaar. */
    private static final int REFRESH_DEVICES = 100;

    /**
     * Wat er gebeurde toen een run startte: welke trigger, welk apparaat (de lezer of het paneel),
     * welke speler, en de kaart of code.
     */
    public record Context(Op trigger, String source, @Nullable BlockPos sourcePos, @Nullable UUID player, String playerName,
                          boolean trusted, boolean owner, @Nullable KeycardData card, boolean cardValid, String code) {
        /** Een trigger zonder speler (een relais, een timer). */
        static Context plain(Op trigger, String source) {
            return new Context(trigger, source, null, null, "", false, false, null, false, "");
        }
    }

    /** Eén run: een vingertje (de tolk, zie Interpreter) dat de blokjes van één when-blok afloopt. */
    private static final class Run {
        final LogicProgram program;
        final Context context;
        final long startAt;
        final Interpreter machine;

        Run(LogicProgram program, int first, Context context, long startAt) {
            this.program = program;
            this.context = context;
            this.startAt = startAt;
            this.machine = new Interpreter(program.graph(), first);
        }
    }

    /** Wat een blokje op de server écht doet (de tolk vraagt het hier): relais, lampjes, E.D.I.T.H., het logboek. */
    private record ServerHost(ServerLevel level, SecurityCoreBlockEntity core, Runtime runtime, Run run)
            implements Interpreter.Host {
        @Override
        public boolean test(Interpreter machine, LogicGraph.Node node) {
            return check(level, core, runtime, run, node);
        }

        @Override
        public void act(Interpreter machine, LogicGraph.Node node) {
            LogicRunner.act(level, core, runtime, run, node);
        }

        @Override
        public void report(String message) {
            LogicRunner.report(level, core, runtime, EventLog.Type.LOGIC, "\"" + run.program.name() + "\": " + message);
        }
    }

    /** Wat er op één server bezig is. */
    private static final class Runtime {
        final List<Run> running = new ArrayList<>();
        final ArrayDeque<Run> queue = new ArrayDeque<>();
        /** Per timer ("diagramnaam#blokje") wanneer hij weer afgaat. */
        final Map<String, Long> timers = new HashMap<>();
        int timerRevision = -1;
        long windowStart;
        int windowCount;
        /** Meldingen die al in het logboek staan (niet elke seconde dezelfde fout), en wanneer we dat vergeten. */
        final Set<String> reported = new HashSet<>();
        long reportedReset;
        boolean wasOnline = true;
        /** Hoeveel werk (blokjes en triggers) de server deed sinds de laatste meting (zie Datacenter). */
        int steps;
    }

    private static final Map<GlobalPos, Runtime> RUNTIMES = new HashMap<>();

    private static Runtime runtime(ServerLevel level, SecurityCoreBlockEntity core) {
        return RUNTIMES.computeIfAbsent(GlobalPos.of(level.dimension(), core.getBlockPos()), key -> new Runtime());
    }

    // ================================================================ Iets gebeurt

    /**
     * Er gebeurde iets dat een diagram kan starten. Geeft terug hoeveel when-blokken erop pasten (0 =
     * geen enkel diagram doet hier iets mee). Draait de server niet, dan gebeurt er niets.
     */
    public static int fire(ServerLevel level, SecurityCoreBlockEntity core, Op trigger, String name, Context context) {
        LogicServer.Stats stats = LogicServer.stats(level, core);
        if (!stats.online()) {
            return 0;
        }
        Runtime runtime = runtime(level, core);
        long now = level.getGameTime();
        int matched = 0;
        for (LogicProgram program : runnable(core, stats)) {
            for (LogicGraph.Node node : program.graph().nodes()) {
                if (node.op == trigger && node.text.equalsIgnoreCase(name)) {
                    if (!allowTrigger(level, core, runtime, now)) {
                        return matched;
                    }
                    enqueue(level, core, runtime, new Run(program, node.next, context, now + stats.reactionTicks()));
                    matched++;
                }
            }
        }
        return matched;
    }

    /** Een relais (of laser, koepel, torentje) ging aan of uit: start "when turned_on/turned_off". */
    public static void switched(ServerLevel level, BlockPos pos, int channel, boolean on) {
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, pos);
        if (core == null || core.logic().programs().isEmpty()) {
            return;
        }
        Op trigger = on ? Op.TURNED_ON : Op.TURNED_OFF;
        for (String name : core.logic().namesAt(pos, channel)) {
            fire(level, core, trigger, name, Context.plain(trigger, name));
        }
    }

    /** Niet te veel triggers per seconde (anders zou een diagram dat zichzelf aanzet de server platleggen). */
    private static boolean allowTrigger(ServerLevel level, SecurityCoreBlockEntity core, Runtime runtime, long now) {
        if (now - runtime.windowStart >= 20 || now < runtime.windowStart) {
            runtime.windowStart = now;
            runtime.windowCount = 0;
        }
        if (++runtime.windowCount > MAX_TRIGGERS_PER_SECOND) {
            report(level, core, runtime, EventLog.Type.LOGIC, "Too many triggers at once: the server skipped some");
            return false;
        }
        return true;
    }

    private static void enqueue(ServerLevel level, SecurityCoreBlockEntity core, Runtime runtime, Run run) {
        runtime.steps++; // een trigger aannemen is ook werk
        runtime.queue.addLast(run);
        if (runtime.queue.size() > MAX_QUEUE) {
            Run dropped = runtime.queue.removeFirst();
            report(level, core, runtime, EventLog.Type.LOGIC, "Out of memory: \"" + dropped.program.name()
                    + "\" had to wait too long and was skipped. Add RAM.");
        }
    }

    /**
     * De diagrammen die nu mogen draaien: aan, en (van boven naar beneden) zolang ze op de SSD's
     * passen.
     */
    private static List<LogicProgram> runnable(SecurityCoreBlockEntity core, LogicServer.Stats stats) {
        List<LogicProgram> fits = new ArrayList<>();
        int used = 0;
        for (LogicProgram program : core.logic().programs()) {
            used += program.graph().size();
            if (used > stats.capacity()) {
                break;
            }
            if (program.enabled()) {
                fits.add(program);
            }
        }
        return fits;
    }

    // ================================================================ Elke tick

    /** Elke tick, vanuit de kern: timers, de rij, en de runs een stapje verder. */
    public static void tick(ServerLevel level, SecurityCoreBlockEntity core) {
        long now = level.getGameTime();
        if ((now + core.getBlockPos().hashCode()) % REFRESH_DEVICES == 0) {
            LogicDevices.refreshIfOwnerOnline(level, core);
        }
        boolean hasPrograms = !core.logic().programs().isEmpty();
        Runtime runtime = RUNTIMES.get(GlobalPos.of(level.dimension(), core.getBlockPos()));
        if (!hasPrograms && runtime == null) {
            return;
        }
        if (runtime == null) {
            runtime = runtime(level, core);
        }
        if (now >= runtime.reportedReset) {
            runtime.reported.clear();
            runtime.reportedReset = now + 1200;
        }
        LogicServer.Stats stats = LogicServer.stats(level, core);
        if (!stats.online()) {
            if (runtime.wasOnline && (!runtime.running.isEmpty() || !runtime.queue.isEmpty())) {
                core.logEvent(EventLog.Type.LOGIC, "Server offline: " + (runtime.running.size() + runtime.queue.size())
                        + " running diagrams stopped (" + stats.problem() + ")");
            }
            runtime.running.clear();
            runtime.queue.clear();
            runtime.wasOnline = false;
            return;
        }
        runtime.wasOnline = true;
        timers(level, core, runtime, stats, now);
        // Uit de rij: zoveel als het RAM toelaat, als hun reactietijd voorbij is.
        while (!runtime.queue.isEmpty() && runtime.running.size() < stats.maxRuns() && runtime.queue.peekFirst().startAt <= now) {
            Run run = runtime.queue.removeFirst();
            run.machine.wakeAt(now);
            runtime.running.add(run);
        }
        Iterator<Run> it = runtime.running.iterator();
        while (it.hasNext()) {
            Run run = it.next();
            if (run.machine.wakeAt() <= now && step(level, core, runtime, run, now)) {
                it.remove();
            }
        }
    }

    /** De timers ("when every(10):") die nu afgaan. */
    private static void timers(ServerLevel level, SecurityCoreBlockEntity core, Runtime runtime, LogicServer.Stats stats, long now) {
        if (runtime.timerRevision != core.logic().revision()) {
            runtime.timers.clear();
            runtime.timerRevision = core.logic().revision();
        }
        for (LogicProgram program : runnable(core, stats)) {
            for (LogicGraph.Node node : program.graph().nodes()) {
                if (node.op != Op.EVERY) {
                    continue;
                }
                String key = program.name() + "#" + node.id;
                long interval = Math.max(20, Math.round(node.number * 20));
                Long next = runtime.timers.get(key);
                if (next == null) {
                    runtime.timers.put(key, now + interval);
                } else if (now >= next) {
                    runtime.timers.put(key, now + interval);
                    if (allowTrigger(level, core, runtime, now)) {
                        enqueue(level, core, runtime, new Run(program, node.next, Context.plain(Op.EVERY, ""), now + stats.reactionTicks()));
                    }
                }
            }
        }
    }

    /**
     * Loopt de blokjes van een run af tot hij moet wachten of klaar is. Geeft true als hij klaar is.
     */
    private static boolean step(ServerLevel level, SecurityCoreBlockEntity core, Runtime runtime, Run run, long now) {
        boolean done = run.machine.step(new ServerHost(level, core, runtime, run), now, STEPS_PER_TICK);
        runtime.steps += run.machine.takeSteps(); // voor de CPU-meter van het datacenter
        return done;
    }

    // ================================================================ Een if

    private static boolean check(ServerLevel level, SecurityCoreBlockEntity core, Runtime runtime, Run run, LogicGraph.Node node) {
        Expr condition = ExprCache.get(node.text, run.context.trigger());
        if (condition == null) {
            report(level, core, runtime, EventLog.Type.LOGIC, "\"" + run.program.name() + "\": the check \"" + node.text
                    + "\" is not valid, so it counts as no");
            return false;
        }
        Context context = run.context;
        return ExprCache.test(condition, new Expr.Facts() {
            @Override
            public Object value(String variable) {
                return switch (variable) {
                    case "card.level" -> (double) (context.card() == null ? 0 : context.card().level());
                    case "card.number" -> (double) (context.card() == null ? 0 : context.card().number());
                    case "card.valid" -> context.cardValid();
                    case "card.name" -> context.card() == null ? "" : context.card().label();
                    case "code" -> context.code();
                    case "player.name" -> context.playerName();
                    case "player.trusted" -> context.trusted();
                    case "player.owner" -> context.owner();
                    case "is_night" -> level.isNight();
                    case "is_day" -> !level.isNight();
                    default -> false;
                };
            }

            @Override
            public boolean isOn(String device) {
                return LogicDevices.isOn(level, core, device);
            }
        });
    }

    // ================================================================ Een actie

    private static void act(ServerLevel level, SecurityCoreBlockEntity core, Runtime runtime, Run run, LogicGraph.Node node) {
        Context context = run.context;
        switch (node.op) {
            case ON, OFF, TOGGLE, PULSE -> {
                EdithRedstone.Op op = switch (node.op) {
                    case ON -> EdithRedstone.Op.ON;
                    case OFF -> EdithRedstone.Op.OFF;
                    case TOGGLE -> EdithRedstone.Op.TOGGLE;
                    default -> EdithRedstone.Op.PULSE;
                };
                int pulseTicks = (int) Math.max(1, Math.round(node.number * 20));
                if (LogicDevices.switchByName(level, core, node.text, op, pulseTicks) == 0) {
                    report(level, core, runtime, EventLog.Type.LOGIC, "\"" + run.program.name() + "\": there is no device called \""
                            + node.text + "\" in this network");
                }
            }
            case ACCEPT, DENY -> {
                if (context.sourcePos() != null) {
                    boolean ok = node.op == Op.ACCEPT;
                    InputDeviceBlock.flash(level, context.sourcePos(), ok ? InputLight.OK : InputLight.DENIED);
                    level.playSound(null, context.sourcePos(), ok ? ModSounds.EDITH_CONFIRM.get() : ModSounds.EDITH_DENIED.get(),
                            SoundSource.BLOCKS, 0.7f, 1.0f);
                }
            }
            case SAY -> {
                core.logEvent(EventLog.Type.LOGIC, node.text);
                ServerPlayer owner = owner(level, core);
                if (owner != null) {
                    EdithServer.send(owner, Component.literal(node.text), EdithMessagePayload.Level.INFO);
                }
            }
            case LOG -> core.logEvent(EventLog.Type.LOGIC, node.text);
            case ALARM -> alarm(level, core, context, node.text);
            default -> { }
        }
    }

    /**
     * Alarm: een rode regel in het logboek, E.D.I.T.H. waarschuwt de eigenaar (waar hij ook is), een
     * bel bij de kern, en de beveiliging jaagt een tijdje op de speler die het veroorzaakte (als dat
     * geen vriend is), ook als "attack players" uit staat.
     */
    static void alarm(ServerLevel level, SecurityCoreBlockEntity core, Context context, String text) {
        String who = context.player() != null ? " (" + context.playerName() + ")" : "";
        core.logEvent(EventLog.Type.ALARM, text + who);
        level.playSound(null, core.getBlockPos(), SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0f, 0.6f);
        ServerPlayer owner = owner(level, core);
        if (owner != null) {
            EdithServer.send(owner, Component.translatable("logic.starktech.alarm", core.hostname(), text + who),
                    EdithMessagePayload.Level.ALERT);
        }
        if (context.player() != null && !context.trusted()) {
            core.hunt(context.player(), level.getGameTime() + ALARM_HUNT);
        }
    }

    private static @Nullable ServerPlayer owner(ServerLevel level, SecurityCoreBlockEntity core) {
        return core.ownerId() == null || level.getServer() == null ? null : level.getServer().getPlayerList().getPlayer(core.ownerId());
    }

    /** Een melding in het logboek, maar dezelfde hooguit één keer per minuut. */
    private static void report(ServerLevel level, SecurityCoreBlockEntity core, Runtime runtime, EventLog.Type type, String text) {
        if (runtime.reported.add(text)) {
            core.logEvent(type, text);
        }
    }

    // ================================================================ Voor de desktop en de tests

    /** Hoeveel runs er nu bezig zijn en hoeveel er in de rij wachten. */
    public static int[] load(ServerLevel level, SecurityCoreBlockEntity core) {
        Runtime runtime = RUNTIMES.get(GlobalPos.of(level.dimension(), core.getBlockPos()));
        return runtime == null ? new int[] {0, 0} : new int[] {runtime.running.size(), runtime.queue.size()};
    }

    /**
     * Hoeveel werk (blokjes en triggers) deze server deed sinds de vorige keer dat je het vroeg, en
     * begint weer bij 0. Zo meet het datacenter hoe hard de CPU's werken.
     */
    public static int takeSteps(ServerLevel level, SecurityCoreBlockEntity core) {
        Runtime runtime = RUNTIMES.get(GlobalPos.of(level.dimension(), core.getBlockPos()));
        if (runtime == null) {
            return 0;
        }
        int steps = runtime.steps;
        runtime.steps = 0;
        return steps;
    }

    /** De namen van de diagrammen die nu bezig zijn (een diagram dat twee keer bezig is, staat er twee keer in). */
    public static List<String> runningNames(ServerLevel level, SecurityCoreBlockEntity core) {
        Runtime runtime = RUNTIMES.get(GlobalPos.of(level.dimension(), core.getBlockPos()));
        List<String> names = new ArrayList<>();
        if (runtime != null) {
            for (Run run : runtime.running) {
                names.add(run.program.name());
            }
        }
        return names;
    }

    /** Stopt alles wat op deze server bezig is (bij een overname, of als de kern weg is). */
    public static void stop(ServerLevel level, BlockPos core) {
        RUNTIMES.remove(GlobalPos.of(level.dimension(), core));
    }

    public static void clear() {
        RUNTIMES.clear();
        ExprCache.clear();
    }
}
