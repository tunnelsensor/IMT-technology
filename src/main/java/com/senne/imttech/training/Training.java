package com.senne.imttech.training;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.os.EventLog;
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
 * Een training zoals Rookie squad (hoofdstuk 26, jouw wens: "programma's die gecoördineerde golven op de speler
 * sturen, die non-lethal of lethal schieten"). Eén training tegelijk per beveiligingskern, met één trainee: wie op
 * het Training Start-blok klikte.
 * <ul>
 *   <li>I.M.T telt af ({@code countdownSeconds}), dan begint hij: je diagrammen horen
 *       {@code when training_started("Bootcamp"):}.</li>
 *   <li>Elk schot van je beveiliging dat de trainee raakt, loopt via Shots. Met <b>trainingsschoten</b> doet het geen
 *       schade: een blauwe flits, een seconde trager, en een leven eraf. <b>Lethal</b> doet echte schade (en telt
 *       ook). Na een treffer telt een tweede even niet mee ({@code hitCooldown}): één schot is één leven.</li>
 *   <li>Hij stopt als de levens op zijn, de tijd om is, de trainee het doel haalt (het Training Goal-blok), dood gaat,
 *       uitlogt of het gebied van de kern verlaat, of als iemand op Stop drukt. Je diagrammen horen
 *       {@code when training_ended("Bootcamp"):}, en I.M.T geeft een rapport (dat de kern ook bewaart, voor de app
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
     * Wat voor training (hoofdstuk 27). Een gewone training (hoofdstuk 26), of een oefening waarin je hele basis de
     * trainee als indringer behandelt, ook als hij de eigenaar is (jouw wens: "test hoe moeilijk het is om tot bij de
     * security core te geraken en hem te hacken, of hoe moeilijk het is om uit je prison uit te breken"):
     * <ul>
     *   <li><b>Break-in</b>: geslaagd als hij de kern hackt (de Hacking Laptop), of een Training Goal-blok haalt;</li>
     *   <li><b>Break-out</b>: geslaagd als hij het Training Goal-blok buiten zijn cel haalt.</li>
     * </ul>
     * Een oefening kan alleen in survival.
     */
    public enum Kind {
        TRAINING("Training"),
        BREAK_IN("Break-in drill"),
        BREAK_OUT("Break-out drill");

        private final String title;

        Kind(String title) {
            this.title = title;
        }

        /** Zoals in het spel (Engels): "Break-in drill". */
        public String title() {
            return title;
        }

        /** Is het een oefening (de basis behandelt de trainee als indringer)? */
        public boolean drill() {
            return this != TRAINING;
        }

        public static Kind byName(String name) {
            for (Kind kind : values()) {
                if (kind.name().equalsIgnoreCase(name)) {
                    return kind;
                }
            }
            return TRAINING;
        }
    }

    /**
     * Wat je instelt in het Training Start-blok.
     *
     * @param name    de naam van de training (zo heet hij in je diagrammen: {@code when training_started("Bootcamp"):})
     * @param mode    trainingsschoten of echt
     * @param lives   hoeveel treffers de trainee mag krijgen (1-10)
     * @param seconds hoe lang hij hooguit duurt (0 = geen limiet)
     * @param kind    een gewone training, of (hoofdstuk 27) een inbraak- of uitbraakoefening
     */
    public record Settings(String name, Mode mode, int lives, int seconds, Kind kind) {
        /** Zo staat een nieuw Training Start-blok. */
        public static final Settings DEFAULT = new Settings("Bootcamp", Mode.TRAINING_SHOTS, 1, 0, Kind.TRAINING);
        /** Zo lang mag de naam zijn. */
        public static final int MAX_NAME = 24;
        public static final int MAX_LIVES = 10;
        /** Hooguit een uur. */
        public static final int MAX_SECONDS = 3600;

        /** Een gewone training (zoals vóór hoofdstuk 27). */
        public Settings(String name, Mode mode, int lives, int seconds) {
            this(name, mode, lives, seconds, Kind.TRAINING);
        }

        /** Dezelfde instellingen, maar binnen de grenzen (een naam zonder aanhalingstekens, 1-10 levens...). */
        public Settings clean() {
            String clean = name.strip().replace("\"", "");
            if (clean.length() > MAX_NAME) {
                clean = clean.substring(0, MAX_NAME);
            }
            return new Settings(clean.isEmpty() ? DEFAULT.name : clean, mode, Math.max(1, Math.min(MAX_LIVES, lives)),
                    Math.max(0, Math.min(MAX_SECONDS, seconds)), kind == null ? Kind.TRAINING : kind);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", name);
            tag.putString("Mode", mode.name());
            tag.putInt("Lives", lives);
            tag.putInt("Seconds", seconds);
            tag.putString("Kind", kind.name());
            return tag;
        }

        public static Settings load(CompoundTag tag) {
            if (!tag.contains("Name")) {
                return DEFAULT;
            }
            return new Settings(tag.getString("Name"), Mode.byName(tag.getString("Mode")), tag.getInt("Lives"),
                    tag.getInt("Seconds"), Kind.byName(tag.getString("Kind"))).clean();
        }
    }

    /** Hoe een training afliep. */
    public enum Result {
        GOAL("reached the goal", true),
        /** Een inbraakoefening (hoofdstuk 27): hij hackte de kern met de Hacking Laptop. */
        HACKED("hacked the core", true),
        SURVIVED("survived until the time was up", true),
        OUT_OF_LIVES("ran out of lives", false),
        TIME_UP("ran out of time", false),
        DIED("died", false),
        LEFT("left", false),
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
        /** Een oefening (hoofdstuk 27): hoe dicht de trainee bij de kern kwam (blokken), voor het rapport. */
        private double closest = Double.MAX_VALUE;
        /** Een oefening: de keycards die hij bij het begin bij zich had (hun nummer); die werken nu niet. */
        private final Set<Integer> cardsAtStart = new HashSet<>();

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

        /** Is dit een oefening (hoofdstuk 27) die echt bezig is? Dan is de trainee een indringer in zijn basis. */
        public boolean drilling() {
            return running && settings.kind().drill();
        }

        /** Hoe dicht hij bij de kern kwam (hele blokken), of -1 als dat niet bijgehouden werd. */
        public int closest() {
            return closest == Double.MAX_VALUE ? -1 : (int) Math.floor(closest);
        }

        public Info info(boolean passed) {
            return new Info(settings.name(), traineeName, lives, hits, passed);
        }
    }

    private static final Map<GlobalPos, Session> SESSIONS = new LinkedHashMap<>();

    // ================================================================ Indringer in je eigen basis (hoofdstuk 27)

    /**
     * Is deze speler nu de trainee van een oefening bij deze kern (een inbraak of uitbraak die bezig is, niet meer aan
     * het aftellen)? Dan behandelt de hele basis hem als indringer, ook als het zijn eigen basis is: de torentjes, drones,
     * robots en laserwanden (SecurityCores.isFriend en isIntruder), de zones (ZoneData), zijn apparaten (BaseTrust), de
     * desktop (SecurityOs), zijn relais op afstand (ImtRedstone) en de kaarten die hij bij het begin had (LogicEvents).
     * Dit is de ene plek waar dat allemaal naar vraagt.
     */
    public static boolean isDrillTrainee(@Nullable GlobalPos core, @Nullable UUID player) {
        if (core == null || player == null || SESSIONS.isEmpty()) {
            return false;
        }
        Session session = SESSIONS.get(core);
        return session != null && session.drilling() && session.trainee.equals(player);
    }

    /** Hetzelfde, voor de kern op deze plek in deze wereld. */
    public static boolean isDrillTrainee(ServerLevel level, @Nullable BlockPos core, @Nullable UUID player) {
        // (Dit vragen torentjes en drones heel vaak: zonder training meteen nee.)
        return core != null && !SESSIONS.isEmpty() && isDrillTrainee(GlobalPos.of(level.dimension(), core), player);
    }

    /** Is deze speler de trainee van een oefening bij de kern in wiens gebied deze plek ligt? */
    public static boolean isDrillTraineeAt(ServerLevel level, BlockPos pos, UUID player) {
        if (SESSIONS.isEmpty()) {
            return false;
        }
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, pos);
        return core != null && isDrillTrainee(level, core.getBlockPos(), player);
    }

    /** Had de trainee van een oefening bij deze kern deze keycard al bij zich toen ze begon? Dan werkt hij niet. */
    public static boolean isCardFromBefore(ServerLevel level, BlockPos core, UUID player, int cardNumber) {
        Session session = SESSIONS.get(GlobalPos.of(level.dimension(), core));
        return session != null && session.drilling() && session.trainee.equals(player) && session.cardsAtStart.contains(cardNumber);
    }

    /** De nummers van de keycards in de rugzak van deze speler. */
    private static Set<Integer> keycards(ServerPlayer player) {
        Set<Integer> numbers = new HashSet<>();
        for (net.minecraft.world.item.ItemStack stack : player.getInventory().items) {
            com.senne.imttech.logic.KeycardData card = com.senne.imttech.logic.KeycardItem.data(stack);
            if (card != null) {
                numbers.add(card.number());
            }
        }
        net.minecraft.world.item.ItemStack offhand = player.getOffhandItem();
        com.senne.imttech.logic.KeycardData card = com.senne.imttech.logic.KeycardItem.data(offhand);
        if (card != null) {
            numbers.add(card.number());
        }
        return numbers;
    }

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
     * Begin een training bij deze kern, met deze speler als trainee. I.M.T telt eerst af. Wie dat mag (de
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
        // Een oefening (hoofdstuk 27): alleen in survival, en een uitbraak heeft een doel buiten de cel nodig.
        if (clean.kind().drill() && (trainee.isCreative() || trainee.isSpectator())) {
            return "A drill only works in survival";
        }
        if (clean.kind() == Kind.BREAK_OUT && !TrainingGoals.anyNear(level, core.getBlockPos())) {
            return "A break-out drill needs a Training Goal block outside the cell";
        }
        long countdown = Math.round(ImtConfig.Training.COUNTDOWN_SECONDS * 20);
        Session session = new Session(key, clean, trainee.getUUID(), trainee.getGameProfile().getName(),
                startBlock == null ? null : startBlock.immutable(), level.getGameTime() + countdown);
        if (clean.kind().drill()) {
            session.cardsAtStart.addAll(keycards(trainee));
        }
        SESSIONS.put(key, session);
        core.logEvent(EventLog.Type.TRAINING, clean.kind().title() + " " + clean.name() + " for " + session.traineeName + " ("
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
            Kind kind = session.settings.kind();
            String go = switch (kind) {
                case TRAINING -> "Training " + session.settings.name() + ": go!";
                case BREAK_IN -> kind.title() + " " + session.settings.name() + ": go! Your base treats you as an intruder now."
                        + " Hack the core with a Hacking Laptop";
                case BREAK_OUT -> kind.title() + " " + session.settings.name() + ": go! Your base treats you as an intruder now."
                        + " Reach the goal outside your cell";
            };
            ImtServer.send(trainee, Component.literal(go), ImtMessagePayload.Level.INFO);
            level.playSound(null, trainee.blockPosition(), SoundEvents.NOTE_BLOCK_PLING.value(), SoundSource.PLAYERS, 1.0f, 2.0f);
            if (kind.drill()) {
                // Kaarten die hij tijdens het aftellen nog pakte, tellen ook als "van het begin"; en zijn desktop gaat dicht.
                session.cardsAtStart.addAll(keycards(trainee));
                com.senne.imttech.security.os.SecurityOs.close(trainee);
            }
        }
        com.senne.imttech.logic.LogicRunner.training(level, core, com.senne.imttech.logic.program.Op.TRAINING_STARTED,
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
        if (session.lastHit != Long.MIN_VALUE && now - session.lastHit < Math.round(ImtConfig.Training.HIT_COOLDOWN)) {
            return false;
        }
        session.lastHit = now;
        session.hits++;
        session.lives = Math.max(0, session.lives - 1);
        session.hitsBy.merge(shooter, 1, Integer::sum);
        if (session.settings.mode() == Mode.TRAINING_SHOTS) {
            // Een blauwe flits rond zijn lijf en een zacht "zap", en even trager. (De flits blijft onder zijn ogen: in de
            // eerste persoon zat hij anders vlak voor je camera, en dan zag de trainee een seconde lang bijna niets.)
            level.sendParticles(new DustParticleOptions(new Vector3f(0.25f, 0.65f, 1.0f), 1.0f), trainee.getX(),
                    trainee.getY() + 0.7, trainee.getZ(), 18, 0.3, 0.35, 0.3, 0.0);
            level.playSound(null, trainee.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.PLAYERS, 1.0f, 1.6f);
            int slow = Math.round(ImtConfig.Training.SLOW_TICKS);
            if (slow > 0) {
                trainee.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, slow, 1, false, false, true));
            }
        }
        trainee.displayClientMessage(Component.literal("Hit by " + shooter + ": " + session.lives
                + (session.lives == 1 ? " life left" : " lives left")), true);
        SecurityCoreBlockEntity core = core(level, session);
        if (core != null) {
            com.senne.imttech.logic.LogicRunner.training(level, core, com.senne.imttech.logic.program.Op.TRAINING_HIT,
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
        if (session.running && session.settings.kind().drill()) {
            // Een oefening is alleen in survival: wie naar creative gaat, stopt ermee.
            if (trainee.isCreative() || trainee.isSpectator()) {
                end(level, session, Result.LEFT);
                return;
            }
            // Hoe dicht kwam hij bij de kern (voor het rapport van een inbraak)?
            session.closest = Math.min(session.closest, Math.sqrt(trainee.distanceToSqr(core.getBlockPos().getCenter())));
        }
        if (!session.running) {
            long left = session.countdownEnd - now;
            if (left <= 0) {
                begin(level, core, session);
            } else if (left % 20 == 0) {
                ImtServer.send(trainee, Component.literal("Training " + session.settings.name() + " starts in " + left / 20),
                        ImtMessagePayload.Level.INFO);
                level.playSound(null, trainee.blockPosition(), SoundEvents.NOTE_BLOCK_HAT.value(), SoundSource.PLAYERS, 0.8f, 1.2f);
            }
            return;
        }
        int seconds = session.settings.seconds();
        if (seconds > 0 && session.ticks(now) >= seconds * 20L) {
            // Een oefening haal je alleen met de hack of het doel: de tijd uitzitten is niet genoeg.
            boolean mustReach = session.settings.kind().drill() || TrainingGoals.anyNear(level, core.getBlockPos());
            end(level, session, mustReach ? Result.TIME_UP : Result.SURVIVED);
        }
    }

    /** De kern van deze training (als zijn stuk wereld geladen is). */
    private static @Nullable SecurityCoreBlockEntity core(ServerLevel level, Session session) {
        return level.isLoaded(session.core.pos()) && level.getBlockEntity(session.core.pos()) instanceof SecurityCoreBlockEntity core
                ? core : null;
    }

    // ================================================================ Einde

    /**
     * De training is voorbij. Je diagrammen horen {@code when training_ended("Bootcamp"):}, robots en drones die neergingen
     * staan op (en gaan naar hun dock), en I.M.T geeft het rapport aan de trainee (en de eigenaar), dat de kern
     * ook bewaart.
     */
    public static void end(ServerLevel level, Session session, Result result) {
        if (SESSIONS.remove(session.core) == null) {
            return;
        }
        long now = level.getGameTime();
        SecurityCoreBlockEntity core = core(level, session);
        Kind kind = session.settings.kind();
        TrainingReport report = new TrainingReport(session.settings.name(), session.traineeName, session.settings.mode(),
                result, session.ticks(now), session.hits, session.lives, hitsByText(session), session.downed.size(), now,
                kind, kind == Kind.BREAK_IN ? session.closest() : -1);
        if (kind.drill()) {
            returnLaptop(level, session);
        }
        if (core != null) {
            if (session.running) {
                com.senne.imttech.logic.LogicRunner.training(level, core, com.senne.imttech.logic.program.Op.TRAINING_ENDED,
                        session.info(result.passed()), session.trainee);
            }
            core.addTrainingReport(report);
            core.logEvent(EventLog.Type.TRAINING, report.line());
        }
        TrainingBots.standUp(level, session);
        ServerPlayer trainee = level.getServer().getPlayerList().getPlayer(session.trainee);
        Component text = Component.literal(report.line());
        ImtMessagePayload.Level tone = result.passed() ? ImtMessagePayload.Level.INFO : ImtMessagePayload.Level.WARNING;
        if (trainee != null) {
            ImtServer.send(trainee, text, tone);
            level.playSound(null, trainee.blockPosition(), result.passed() ? SoundEvents.PLAYER_LEVELUP : SoundEvents.NOTE_BLOCK_BASS.value(),
                    SoundSource.PLAYERS, 0.8f, result.passed() ? 1.2f : 0.6f);
        }
        ServerPlayer owner = core == null || core.ownerId() == null ? null : level.getServer().getPlayerList().getPlayer(core.ownerId());
        if (owner != null && owner != trainee) {
            ImtServer.send(owner, text, tone);
        }
    }

    /**
     * Een oefening is voorbij: staat de Hacking Laptop van de trainee nog op de kern (hij was bezig met de hack), dan gaat
     * hij terug in zijn rugzak (of valt hij bij de kern, als hij er niet meer is). Anders hackte hij daarna zijn eigen kern.
     */
    private static void returnLaptop(ServerLevel level, Session session) {
        BlockPos top = session.core.pos().above();
        if (!level.isLoaded(top) || !(level.getBlockEntity(top) instanceof com.senne.imttech.security.HackingLaptopBlockEntity laptop)
                || !laptop.isOwner(session.trainee)) {
            return;
        }
        level.removeBlock(top, false);
        net.minecraft.world.item.ItemStack back = new net.minecraft.world.item.ItemStack(com.senne.imttech.registry.ModItems.HACKING_LAPTOP.get());
        ServerPlayer trainee = level.getServer().getPlayerList().getPlayer(session.trainee);
        if (trainee == null || !trainee.getInventory().add(back)) {
            net.minecraft.world.level.block.Block.popResource(level, top, back);
        }
    }

    // ================================================================ De hack (hoofdstuk 27)

    /**
     * De trainee van een inbraakoefening hackte de kern met de Hacking Laptop (zie TowerHack): geslaagd, en de kern blijft
     * van wie hij was. Geeft true als dit een oefening was (dan neemt TowerHack de kern niet over).
     */
    public static boolean hacked(ServerLevel level, BlockPos core, ServerPlayer player) {
        Session session = SESSIONS.get(GlobalPos.of(level.dimension(), core));
        if (session == null || !session.drilling() || !session.trainee.equals(player.getUUID())) {
            return false;
        }
        session.closest = 0;
        end(level, session, Result.HACKED);
        return true;
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
