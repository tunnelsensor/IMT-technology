package com.senne.starktech.training;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import com.senne.starktech.security.os.EventLog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

/**
 * Een training zoals Domino squad (hoofdstuk 26, jouw wens: "programma's die gecoördineerde golven op de speler
 * sturen, die non-lethal of lethal schieten"). Eén training tegelijk per beveiligingskern, met één trainee: wie op
 * het Training Start-blok klikte.
 * <ul>
 *   <li>E.D.I.T.H. telt af ({@code countdownSeconds}), dan begint hij: je diagrammen horen
 *       {@code when training_started("Kamino"):}.</li>
 *   <li>Elk schot van je beveiliging dat de trainee raakt, loopt via Shots. Met <b>trainingsschoten</b> doet het geen
 *       schade: een blauwe flits, een seconde trager, en een leven eraf. <b>Lethal</b> doet echte schade (en telt
 *       ook). Na een treffer telt een tweede even niet mee ({@code hitCooldown}): één schot is één leven.</li>
 *   <li>Hij stopt als de levens op zijn, de tijd om is, de trainee het doel haalt (het Training Goal-blok), dood gaat,
 *       uitlogt of het gebied van de kern verlaat, of als iemand op Stop drukt. Je diagrammen horen
 *       {@code when training_ended("Kamino"):}, en E.D.I.T.H. geeft een rapport (dat de kern ook bewaart, voor de app
 *       Training).</li>
 * </ul>
 * Een training leeft alleen in het geheugen van de server: stopt de server, dan is hij weg.
 */
public final class Training {
    private Training() {}

    /** Hoe er geschoten wordt: trainingsschoten (geen schade, een leven eraf) of echt. */
    public enum Mode {
        TRAINING_SHOTS("Training shots"),
        LETHAL("Lethal");

        private final String title;

        Mode(String title) {
            this.title = title;
        }

        /** Zoals in het spel (Engels). */
        public String title() {
            return title;
        }

        public static Mode byName(String name) {
            for (Mode mode : values()) {
                if (mode.name().equalsIgnoreCase(name)) {
                    return mode;
                }
            }
            return TRAINING_SHOTS;
        }
    }

    /**
     * Wat je instelt in het Training Start-blok.
     *
     * @param name    de naam van de training (zo heet hij in je diagrammen: {@code when training_started("Kamino"):})
     * @param mode    trainingsschoten of echt
     * @param lives   hoeveel treffers de trainee mag krijgen (1-10)
     * @param seconds hoe lang hij hooguit duurt (0 = geen limiet)
     */
    public record Settings(String name, Mode mode, int lives, int seconds) {
        /** Zo staat een nieuw Training Start-blok. */
        public static final Settings DEFAULT = new Settings("Kamino", Mode.TRAINING_SHOTS, 1, 0);
        /** Zo lang mag de naam zijn. */
        public static final int MAX_NAME = 24;
        public static final int MAX_LIVES = 10;
        /** Hooguit een uur. */
        public static final int MAX_SECONDS = 3600;

        /** Dezelfde instellingen, maar binnen de grenzen (een naam zonder aanhalingstekens, 1-10 levens...). */
        public Settings clean() {
            String clean = name.strip().replace("\"", "");
            if (clean.length() > MAX_NAME) {
                clean = clean.substring(0, MAX_NAME);
            }
            return new Settings(clean.isEmpty() ? DEFAULT.name : clean, mode, Math.max(1, Math.min(MAX_LIVES, lives)),
                    Math.max(0, Math.min(MAX_SECONDS, seconds)));
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", name);
            tag.putString("Mode", mode.name());
            tag.putInt("Lives", lives);
            tag.putInt("Seconds", seconds);
            return tag;
        }

        public static Settings load(CompoundTag tag) {
            if (!tag.contains("Name")) {
                return DEFAULT;
            }
            return new Settings(tag.getString("Name"), Mode.byName(tag.getString("Mode")), tag.getInt("Lives"),
                    tag.getInt("Seconds")).clean();
        }
    }

    /** Hoe een training afliep. */
    public enum Result {
        GOAL("reached the goal", true),
        SURVIVED("survived until the time was up", true),
        OUT_OF_LIVES("ran out of lives", false),
        TIME_UP("ran out of time", false),
        DIED("died", false),
        LEFT("left the training", false),
        STOPPED("was stopped", false);

        private final String text;
        private final boolean passed;

        Result(String text, boolean passed) {
            this.text = text;
            this.passed = passed;
        }

        /** In een zin: "Senne reached the goal". */
        public String text() {
            return text;
        }

        public boolean passed() {
            return passed;
        }
    }

    /** Wat een diagram over een training weet: {@code trainee}, {@code lives}, {@code hits}, {@code passed}. */
    public record Info(String name, String trainee, int lives, int hits, boolean passed) {}

    /** Eén training die bezig is (of aftelt). */
    public static final class Session {
        private final GlobalPos core;
        private final Settings settings;
        private final UUID trainee;
        private final String traineeName;
        private final @Nullable BlockPos startBlock;
        private final long countdownEnd;
        private long startTick;
        private boolean running;
        private int lives;
        private int hits;
        private long lastHit = Long.MIN_VALUE;
        /** Wie hoe vaak raakte: "Guard 2" -> 3. */
        private final Map<String, Integer> hitsBy = new LinkedHashMap<>();
        /** De robots en drones die tijdens de training neergingen (de Ion Blaster): die staan pas op als hij voorbij is. */
        private final Set<UUID> downed = new HashSet<>();

        private Session(GlobalPos core, Settings settings, UUID trainee, String traineeName, @Nullable BlockPos startBlock,
                        long countdownEnd) {
            this.core = core;
            this.settings = settings;
            this.trainee = trainee;
            this.traineeName = traineeName;
            this.startBlock = startBlock;
            this.countdownEnd = countdownEnd;
            this.lives = settings.lives();
        }

        public GlobalPos core() {
            return core;
        }

        public Settings settings() {
            return settings;
        }

        public UUID trainee() {
            return trainee;
        }

        public String traineeName() {
            return traineeName;
        }

        /** Is hij echt bezig (niet meer aan het aftellen)? */
        public boolean running() {
            return running;
        }

        public int lives() {
            return lives;
        }

        public int hits() {
            return hits;
        }

        /** Hoe lang hij al bezig is (ticks), of 0 als hij nog aftelt. */
        public long ticks(long now) {
            return running ? now - startTick : 0;
        }

        /** Wie hoe vaak raakte (voor het rapport). */
        public Map<String, Integer> hitsBy() {
            return hitsBy;
        }

        /** Hoeveel robots en drones er neergingen. */
        public int downedCount() {
            return downed.size();
        }

        /** Een robot of drone ging neer tijdens deze training (hij staat pas weer op als ze voorbij is). */
        public void addDowned(UUID bot) {
            downed.add(bot);
        }

        public Set<UUID> downed() {
            return downed;
        }

        public Info info(boolean passed) {
            return new Info(settings.name(), traineeName, lives, hits, passed);
        }
    }

    private static final Map<GlobalPos, Session> SESSIONS = new LinkedHashMap<>();

    // ================================================================ Opvragen

    /** De training bij deze kern, of null. */
    public static @Nullable Session at(ServerLevel level, BlockPos core) {
        return SESSIONS.get(GlobalPos.of(level.dimension(), core));
    }

    /** De training waarin deze speler de trainee is (bezig of aan het aftellen), of null. */
    public static @Nullable Session of(UUID player) {
        for (Session session : SESSIONS.values()) {
            if (session.trainee.equals(player)) {
                return session;
            }
        }
        return null;
    }

    /** Is deze speler nu de trainee van een training die bezig is? */
    public static boolean isTrainee(Player player) {
        Session session = of(player.getUUID());
        return session != null && session.running;
    }

    /** Alle trainingen (voor de tests en het opruimen). */
    public static List<Session> all() {
        return List.copyOf(SESSIONS.values());
    }

    // ================================================================ Beginnen

    /**
     * Begin een training bij deze kern, met deze speler als trainee. E.D.I.T.H. telt eerst af. Wie dat mag (de
     * eigenaar, zijn team, vertrouwde spelers) kijkt het Training Start-blok na. Geeft een foutmelding (Engels) of
     * null als hij begint.
     */
    public static @Nullable String start(ServerLevel level, SecurityCoreBlockEntity core, ServerPlayer trainee, Settings settings,
                                         @Nullable BlockPos startBlock) {
        GlobalPos key = GlobalPos.of(level.dimension(), core.getBlockPos());
        Session running = SESSIONS.get(key);
        if (running != null) {
            return "A training is already running here: " + running.settings.name() + " (" + running.traineeName + ")";
        }
        if (of(trainee.getUUID()) != null) {
            return "You are already in a training";
        }
        Settings clean = settings.clean();
        long countdown = Math.round(StarkConfig.Training.COUNTDOWN_SECONDS * 20);
        Session session = new Session(key, clean, trainee.getUUID(), trainee.getGameProfile().getName(),
                startBlock == null ? null : startBlock.immutable(), level.getGameTime() + countdown);
        SESSIONS.put(key, session);
        core.logEvent(EventLog.Type.TRAINING, "Training " + clean.name() + " for " + session.traineeName + " ("
                + clean.mode().title().toLowerCase(Locale.ROOT) + ", " + clean.lives() + (clean.lives() == 1 ? " life)" : " lives)"));
        if (countdown <= 0) {
            begin(level, core, session);
        }
        return null;
    }

    /** Het aftellen is voorbij: nu begint hij echt. */
    private static void begin(ServerLevel level, SecurityCoreBlockEntity core, Session session) {
        session.running = true;
        session.startTick = level.getGameTime();
        ServerPlayer trainee = level.getServer().getPlayerList().getPlayer(session.trainee);
        if (trainee != null) {
            EdithServer.send(trainee, Component.literal("Training " + session.settings.name() + ": go!"),
                    EdithMessagePayload.Level.INFO);
            level.playSound(null, trainee.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 2.0f);
        }
        com.senne.starktech.logic.LogicRunner.training(level, core, com.senne.starktech.logic.program.Op.TRAINING_STARTED,
                session.info(false), session.trainee);
    }

    // ================================================================ Een treffer

    /**
     * De trainee is geraakt door {@code shooter} (een torentje, een laserwand, een robot...). Telt mee als er even geen
     * andere treffer was: een leven eraf, en met trainingsschoten een blauwe flits en even trager. Bij 0 levens stopt
     * de training. Geeft true als de treffer meetelde.
     */
    public static boolean hit(ServerLevel level, ServerPlayer trainee, String shooter) {
        Session session = of(trainee.getUUID());
        if (session == null || !session.running) {
            return false;
        }
        long now = level.getGameTime();
        // (Nog geen treffer: lastHit is Long.MIN_VALUE, en now min dat zou overlopen.)
        if (session.lastHit != Long.MIN_VALUE && now - session.lastHit < Math.round(StarkConfig.Training.HIT_COOLDOWN)) {
            return false;
        }
        session.lastHit = now;
        session.hits++;
        session.lives = Math.max(0, session.lives - 1);
        session.hitsBy.merge(shooter, 1, Integer::sum);
        if (session.settings.mode() == Mode.TRAINING_SHOTS) {
            // Een blauwe flits en een zacht "zap", en even trager.
            level.sendParticles(new DustParticleOptions(new Vector3f(0.25f, 0.65f, 1.0f), 1.4f), trainee.getX(),
                    trainee.getY() + 1.0, trainee.getZ(), 30, 0.35, 0.6, 0.35, 0.0);
            level.playSound(null, trainee.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0f, 1.6f);
            int slow = Math.round(StarkConfig.Training.SLOW_TICKS);
            if (slow > 0) {
                trainee.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, 1, false, false, true));
            }
        }
        trainee.displayClientMessage(Component.literal("Hit by " + shooter + ": " + session.lives
                + (session.lives == 1 ? " life left" : " lives left")), true);
        SecurityCoreBlockEntity core = core(level, session);
        if (core != null) {
            com.senne.starktech.logic.LogicRunner.training(level, core, com.senne.starktech.logic.program.Op.TRAINING_HIT,
                    session.info(false), session.trainee);
            // De Training End-blokken van deze basis: een puls, en hun programma (when trainee_hit:).
            TrainingEnds.hit(level, core, session.info(false), session.trainee);
        }
        if (session.lives <= 0) {
            end(level, session, Result.OUT_OF_LIVES);
        }
        return true;
    }

    // ================================================================ Elke tick

    /** Elke tick op de server: het aftellen, de tijd, en of de trainee er nog is. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (SESSIONS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (Session session : List.copyOf(SESSIONS.values())) {
            ServerLevel level = server.getLevel(session.core.dimension());
            if (level == null) {
                SESSIONS.remove(session.core);
                continue;
            }
            tick(level, session);
        }
    }

    private static void tick(ServerLevel level, Session session) {
        long now = level.getGameTime();
        SecurityCoreBlockEntity core = core(level, session);
        ServerPlayer trainee = level.getServer().getPlayerList().getPlayer(session.trainee);
        if (core == null || trainee == null || trainee.serverLevel() != level) {
            end(level, session, Result.LEFT);
            return;
        }
        if (!trainee.isAlive()) {
            end(level, session, Result.DIED);
            return;
        }
        if (!SecurityCores.inArea(core.getBlockPos(), trainee.blockPosition())) {
            end(level, session, Result.LEFT);
            return;
        }
        if (!session.running) {
            long left = session.countdownEnd - now;
            if (left <= 0) {
                begin(level, core, session);
            } else if (left % 20 == 0) {
                EdithServer.send(trainee, Component.literal("Training " + session.settings.name() + " starts in " + left / 20),
                        EdithMessagePayload.Level.INFO);
                level.playSound(null, trainee.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
            }
            return;
        }
        int seconds = session.settings.seconds();
        if (seconds > 0 && session.ticks(now) >= seconds * 20L) {
            end(level, session, TrainingGoals.anyNear(level, core.getBlockPos()) ? Result.TIME_UP : Result.SURVIVED);
        }
    }

    /** De kern van deze training (als zijn stuk wereld geladen is). */
    private static @Nullable SecurityCoreBlockEntity core(ServerLevel level, Session session) {
        return level.isLoaded(session.core.pos()) && level.getBlockEntity(session.core.pos()) instanceof SecurityCoreBlockEntity core
                ? core : null;
    }

    // ================================================================ Einde

    /**
     * De training is voorbij. Je diagrammen horen {@code when training_ended("Kamino"):}, robots en drones die neergingen
     * staan op (en gaan naar hun dock), en E.D.I.T.H. geeft het rapport aan de trainee (en de eigenaar), dat de kern
     * ook bewaart.
     */
    public static void end(ServerLevel level, Session session, Result result) {
        if (SESSIONS.remove(session.core) == null) {
            return;
        }
        long now = level.getGameTime();
        SecurityCoreBlockEntity core = core(level, session);
        TrainingReport report = new TrainingReport(session.settings.name(), session.traineeName, session.settings.mode(),
                result, session.ticks(now), session.hits, session.lives, hitsByText(session), session.downed.size(), now);
        if (core != null) {
            if (session.running) {
                com.senne.starktech.logic.LogicRunner.training(level, core, com.senne.starktech.logic.program.Op.TRAINING_ENDED,
                        session.info(result.passed()), session.trainee);
            }
            core.addTrainingReport(report);
            core.logEvent(EventLog.Type.TRAINING, report.line());
        }
        TrainingBots.standUp(level, session);
        ServerPlayer trainee = level.getServer().getPlayerList().getPlayer(session.trainee);
        Component text = Component.literal(report.line());
        EdithMessagePayload.Level kind = result.passed() ? EdithMessagePayload.Level.INFO : EdithMessagePayload.Level.WARNING;
        if (trainee != null) {
            EdithServer.send(trainee, text, kind);
            level.playSound(null, trainee.blockPosition(), result.passed() ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.PLAYERS, 0.8f, result.passed() ? 1.2f : 0.6f);
        }
        ServerPlayer owner = core == null || core.ownerId() == null ? null : level.getServer().getPlayerList().getPlayer(core.ownerId());
        if (owner != null && owner != trainee) {
            EdithServer.send(owner, text, kind);
        }
    }

    /** Wie raakte, voor het rapport: "Guard 2 x3, Security turret x1". */
    private static String hitsByText(Session session) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : session.hitsBy.entrySet()) {
            parts.add(entry.getKey() + " x" + entry.getValue());
        }
        return String.join(", ", parts);
    }

    /** De server stopt: alle trainingen zijn weg. */
    public static void clear() {
        SESSIONS.clear();
    }
}
