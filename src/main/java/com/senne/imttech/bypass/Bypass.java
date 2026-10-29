package com.senne.imttech.bypass;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.logic.CodePanelBlock;
import com.senne.imttech.logic.InputDeviceBlockEntity;
import com.senne.imttech.logic.KeycardReaderBlock;
import com.senne.imttech.logic.LogicEvents;
import com.senne.imttech.logic.LogicServer;
import com.senne.imttech.logic.PanelCodes;
import com.senne.imttech.network.BypassPayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.registry.ModDamageTypes;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.shield.LaserEmitterBlock;
import com.senne.imttech.shield.LaserEmitterBlockEntity;
import com.senne.imttech.shield.LaserEmitters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Hacken met de Bypass Kit (hoofdstuk 12): de server-kant. Hier staat wie wat aan het hacken is, en
 * hier wordt nagekeken of het echt gelukt is. Het scherm (de minigame) draait in jouw spel; de server
 * vertrouwt dat niet zomaar:
 * <ul>
 *   <li><b>Een keycardlezer</b>: de server kiest een getal ({@code seed}) en stuurt het naar je spel.
 *       Uit dat getal maken jouw spel en de server precies hetzelfde stroomcircuit (CircuitPuzzle). Je
 *       kiest een groep van dat netwerk (25 oktober; dan begint de klok, ook op de server), en als het
 *       signaal bij ACCEPT komt, stuurt je spel hoe alle stukjes staan. De server rekent zelf na of dat
 *       klopt en of je op tijd was; pas dan leest de lezer een nagemaakte kaart van die groep.</li>
 *   <li><b>Een codepaneel</b>: de server zoekt de code in je diagrammen (PanelCodes) en stuurt alleen
 *       de vingerafdrukken: welke cijfers erin zitten en hoe vaak, en hoe lang de code is. Elke volgorde
 *       die je probeert, gaat naar de server; die zegt hoeveel cijfers op de goede plek staan. De code
 *       zelf krijg je pas als je hem geraden hebt; dan typt de kit hem ook echt in.</li>
 *   <li><b>Een laserzender</b> (hoofdstuk 33): de server kiest een getal, en jouw spel en de server maken daaruit
 *       dezelfde kluwen van draden (WireTangle; meer zenders in de poort = meer draden en meer tijd). De klok loopt
 *       meteen. Je spel stuurt alleen welke draad je doorknipt; de server kijkt na of dat de draad naar de LENS is.
 *       Goed: de hele poort valt uit tot iemand de draden herstelt (stil, zonder alarm). Fout: een schok, de poort
 *       gaat 5 minuten op slot (voor iedereen), en het alarm gaat af. Ook een zender zonder kern mag je hacken.</li>
 * </ul>
 * Mislukt het (ALARM, de tijd is om, of geen pogingen meer), dan gaat het alarm af en laat dat apparaat jou een minuut
 * niet meer proberen. Esc of weglopen stopt de hack zonder alarm.
 */
public final class Bypass {
    private Bypass() {}

    /** Wat er gehackt wordt. */
    public enum Kind { READER, PANEL, LASER }

    /** Zoveel ticks speling op de klok (een bericht onderweg doet er even over). */
    private static final int CLOCK_SLACK = 40;
    /** Zo lang mag je over het kiezen van een groep doen, dan gaat het scherm dicht (zonder alarm). */
    private static final int CHOOSE_TIMEOUT = 2400;
    /** Zo lang mag een codepaneel-hack hooguit duren (5 minuten), dan gaat het scherm dicht (zonder alarm). */
    private static final int PANEL_TIMEOUT = 6000;

    /** Eén hack die bezig is. */
    static final class Session {
        final Kind kind;
        final ResourceKey<Level> dimension;
        final BlockPos pos;
        final long seed;
        final long openedAt;
        /** De groep van de kaart die de hacker namaakt (lezer, 25 oktober); "" = een kaart zonder groep. */
        String group = "";
        /** Wanneer de klok begon te lopen (game-tijd), of -1. */
        long startedAt = -1;
        /** De code die de kit moet kraken (paneel), en hoeveel pogingen je nog hebt. */
        String code = "";
        int triesLeft;
        /** Een laserzender: hoeveel draden de kluwen heeft, en hoeveel ticks je krijgt. */
        int wires;
        int ticks;

        Session(Kind kind, ResourceKey<Level> dimension, BlockPos pos, long seed, long openedAt) {
            this.kind = kind;
            this.dimension = dimension;
            this.pos = pos;
            this.seed = seed;
            this.openedAt = openedAt;
        }
    }

    /** Per speler zijn hack van dit moment. */
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Na een mislukte hack: speler -> (apparaat -> tot wanneer hij daar niet mag). */
    private static final Map<UUID, Map<GlobalPos, Long>> LOCKOUTS = new HashMap<>();

    // ================================================================ Beginnen

    /**
     * Rechtsklik met de kit op een lezer of paneel. Hangt hij niet aan een draaiende server, of laat hij
     * jou nog even niet proberen, dan gebeurt er niets (en kost het niets). Anders kost het één gebruik
     * van de kit en opent het scherm.
     */
    public static void start(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        ServerLevel level = player.serverLevel();
        if (level.getBlockEntity(pos) instanceof LaserEmitterBlockEntity) {
            startLaser(player, pos, hand);
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof InputDeviceBlockEntity device)) {
            return;
        }
        Kind kind = level.getBlockState(pos).getBlock() instanceof KeycardReaderBlock ? Kind.READER
                : level.getBlockState(pos).getBlock() instanceof CodePanelBlock ? Kind.PANEL : null;
        if (kind == null) {
            return;
        }
        if (player.distanceToSqr(pos.getCenter()) > ImtConfig.Bypass.REACH * ImtConfig.Bypass.REACH) {
            return;
        }
        long wait = lockedFor(player, pos);
        if (wait > 0) {
            player.displayClientMessage(Component.translatable("bypass.imttech.msg.locked", device.name(),
                    (wait + 19) / 20).withStyle(ChatFormatting.RED), true);
            return;
        }
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
        if (core == null || !LogicServer.stats(level, core).online()) {
            player.displayClientMessage(Component.translatable("bypass.imttech.msg.no_server")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        // Zonder stroom (hoofdstuk 38) is er niets om voor de gek te houden: het apparaat is dood (het kost niets).
        if (com.senne.imttech.lan.DevicePower.dead(level, pos, player)) {
            return;
        }
        // Een paneel: welke code opent het? Staat er geen in je diagrammen, dan valt er niets te kraken.
        List<String> codes = kind == Kind.PANEL ? PanelCodes.forPanel(core, device.name()) : List.of();
        if (kind == Kind.PANEL && codes.isEmpty()) {
            player.displayClientMessage(Component.translatable("bypass.imttech.msg.no_code")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        // Eén gebruik van de kit, ook als je straks stopt (in creative niet: dat regelt hurtAndBreak zelf).
        ItemStack kit = player.getItemInHand(hand);
        kit.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        stop(player, false);
        Session session = new Session(kind, level.dimension(), pos.immutable(), level.random.nextLong(),
                level.getGameTime());
        SESSIONS.put(player.getUUID(), session);
        CompoundTag args = new CompoundTag();
        args.putString("name", device.name());
        if (kind == Kind.READER) {
            // Het geluid van een lezer die je openschroeft.
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.4f, 1.6f);
            args.putLong("seed", session.seed);
            // De groepen van dit netwerk (25 oktober): daaruit kiest de hacker welke kaart hij namaakt. Als een boom:
            // per groep zijn naam en hoe diep hij zit ("night shift" in "security" in "staff" = 2).
            args.put("groups", groupTree(core.logic()));
            ModNetwork.sendTo(player, new BypassPayload(pos, "reader", args));
        } else {
            // De eerste code uit je diagrammen. Naar jouw spel gaan alleen de afdrukken en de lengte.
            session.code = codes.getFirst();
            session.triesLeft = session.code.length() + Math.round(ImtConfig.Bypass.ORDER_EXTRA_TRIES);
            level.playSound(null, pos, SoundEvents.BRUSH_GENERIC, SoundSource.BLOCKS, 0.4f, 1.2f);
            args.putInt("length", session.code.length());
            args.putIntArray("prints", PanelOrders.prints(session.code));
            args.putInt("tries", session.triesLeft);
            ModNetwork.sendTo(player, new BypassPayload(pos, "panel", args));
        }
    }

    /**
     * Rechtsklik met de kit op een laserzender (hoofdstuk 33). Elke zender mag, ook een zonder kern of je eigen (om je
     * beveiliging te testen). Zijn de draden van de poort al doorgeknipt, of zit hij op slot na een verkeerde knip, dan
     * gebeurt er niets (en kost het niets). Anders kost het één gebruik van de kit, begint de klok, en opent je spel de
     * kluwen.
     */
    private static void startLaser(ServerPlayer player, BlockPos pos, InteractionHand hand) {
        ServerLevel level = player.serverLevel();
        if (player.distanceToSqr(pos.getCenter()) > ImtConfig.Bypass.REACH * ImtConfig.Bypass.REACH) {
            return;
        }
        List<BlockPos> gate = LaserEmitterBlock.group(level, pos);
        if (LaserEmitterBlock.isCut(level, gate)) {
            player.displayClientMessage(Component.translatable("bypass.imttech.msg.laser_cut")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long locked = LaserEmitters.lockedFor(level, gate);
        if (locked > 0) {
            long seconds = (locked + 19) / 20;
            player.displayClientMessage(Component.translatable("bypass.imttech.msg.laser_locked",
                    (seconds / 60) + ":" + String.format(java.util.Locale.ROOT, "%02d", seconds % 60))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        ItemStack kit = player.getItemInHand(hand);
        kit.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        stop(player, false);
        long now = level.getGameTime();
        Session session = new Session(Kind.LASER, level.dimension(), pos.immutable(), level.random.nextLong(), now);
        session.wires = WireTangle.wiresFor(gate.size());
        session.ticks = WireTangle.ticksFor(session.wires);
        session.startedAt = now;
        SESSIONS.put(player.getUUID(), session);
        // Het geluid van een zender die je openschroeft.
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.4f, 1.8f);
        CompoundTag args = new CompoundTag();
        args.putString("name", gate.size() == 1 ? "Laser emitter" : "Laser gate");
        args.putLong("seed", session.seed);
        args.putInt("wires", session.wires);
        args.putInt("ticks", session.ticks);
        args.putInt("lock", Math.round(ImtConfig.Bypass.LASER_LOCK_SECONDS));
        ModNetwork.sendTo(player, new BypassPayload(pos, "laser", args));
    }

    // ================================================================ Wat het scherm stuurt

    /** Een bericht van het scherm van een speler. */
    public static void handle(ServerPlayer player, BypassPayload payload) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null || !session.pos.equals(payload.pos()) || session.dimension != player.level().dimension()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        switch (payload.op()) {
            case "group" -> {
                if (session.kind == Kind.READER && session.startedAt < 0) {
                    // Een groep die bestaat; heeft het netwerk geen groepen, dan een kaart zonder groep.
                    SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, session.pos);
                    String chosen = core == null ? null : core.logic().spelling(payload.args().getString("group"));
                    if (chosen == null && core != null && !core.logic().groups().isEmpty()) {
                        return;
                    }
                    session.group = chosen == null ? "" : chosen;
                    session.startedAt = now;
                }
            }
            case "solve" -> {
                if (session.kind != Kind.READER || session.startedAt < 0) {
                    return;
                }
                if (now - session.startedAt > CircuitPuzzle.circuitTicks() + CLOCK_SLACK) {
                    fail(player, session, "time");
                    return;
                }
                CircuitPuzzle puzzle = CircuitPuzzle.generate(session.seed);
                int[] rotations = puzzle.readRotations(payload.args().getByteArray("rot"));
                if (rotations == null || !puzzle.signal(rotations).solved()) {
                    // Jouw spel dacht dat het klopte, de server niet: gewoon verder puzzelen.
                    reply(player, session, "retry");
                    return;
                }
                SESSIONS.remove(player.getUUID());
                reply(player, session, "ok");
                LogicEvents.forgedKeycard(level, session.pos, player, session.group);
            }
            case "guess" -> {
                if (session.kind == Kind.PANEL) {
                    guess(player, session, payload.args().getString("code"));
                }
            }
            case "cut" -> {
                if (session.kind == Kind.LASER) {
                    cut(player, session, payload.args().getInt("wire"));
                }
            }
            case "fail" -> {
                if (session.kind == Kind.READER && session.startedAt >= 0) {
                    String why = payload.args().getString("why");
                    fail(player, session, why.equals("alarm") ? "alarm" : "time");
                } else if (session.kind == Kind.LASER) {
                    // Bij een laserzender kan je spel alleen "de tijd is om" zeggen; knippen gaat via "cut".
                    fail(player, session, "time");
                }
            }
            case "cancel" -> SESSIONS.remove(player.getUUID());
            default -> { }
        }
    }

    /**
     * Een volgorde voor een codepaneel. Klopt hij niet (verkeerde lengte, of een cijfer zonder
     * afdrukken), dan telt hij niet. Anders: hoeveel cijfers staan goed? Alles goed = gekraakt: de kit
     * typt de code in en je ziet hem. Geen pogingen meer = het paneel slaat alarm.
     */
    private static void guess(ServerPlayer player, Session session, String guess) {
        int[] prints = PanelOrders.prints(session.code);
        boolean valid = guess.length() == session.code.length() && CodePanelBlock.isValidCode(guess)
                && guess.chars().allMatch(c -> prints[c - '0'] > 0);
        if (!valid) {
            return;
        }
        int right = PanelOrders.rightPlaces(guess, session.code);
        if (right == session.code.length()) {
            SESSIONS.remove(player.getUUID());
            CompoundTag args = new CompoundTag();
            args.putString("result", "cracked");
            args.putString("code", session.code);
            ModNetwork.sendTo(player, new BypassPayload(session.pos, "result", args));
            LogicEvents.crackedCode(player.serverLevel(), session.pos, player, session.code);
            return;
        }
        session.triesLeft--;
        if (session.triesLeft <= 0) {
            fail(player, session, "tries");
            return;
        }
        CompoundTag args = new CompoundTag();
        args.putString("code", guess);
        args.putInt("right", right);
        args.putInt("tries", session.triesLeft);
        ModNetwork.sendTo(player, new BypassPayload(session.pos, "guess", args));
    }

    /**
     * Een draad van een laserzender doorgeknipt (hoofdstuk 33). Je kunt maar één keer knippen. Was je te laat, dan
     * telt het als mislukt (zonder schok: de klok was al om). De draad naar de LENS: de hele poort valt uit, stil.
     * Een andere draad: een schok, en mislukt (de poort op slot en het alarm).
     */
    private static void cut(ServerPlayer player, Session session, int wire) {
        if (wire < 0 || wire >= session.wires) {
            return; // geen draad van deze kluwen: telt niet
        }
        ServerLevel level = player.serverLevel();
        if (level.getGameTime() - session.startedAt > session.ticks + CLOCK_SLACK) {
            fail(player, session, "time");
            return;
        }
        if (wire == WireTangle.generate(session.seed, session.wires).lensStart()) {
            SESSIONS.remove(player.getUUID());
            reply(player, session, "ok");
            LaserEmitters.cutWires(level, session.pos);
            return;
        }
        shock(player, session.pos);
        fail(player, session, "wrong");
    }

    /** De verkeerde draad: een schok voor de hacker (schade, vonken en een knetter van de laser). */
    private static void shock(ServerPlayer player, BlockPos emitter) {
        ServerLevel level = player.serverLevel();
        float damage = ImtConfig.Bypass.LASER_SHOCK_DAMAGE;
        if (damage > 0) {
            com.senne.imttech.training.Shots.hit(level, player, ModDamageTypes.laserWall(level), damage, "Laser emitter");
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, emitter.getX() + 0.5, emitter.getY() + 0.5, emitter.getZ() + 0.5,
                30, 0.5, 0.5, 0.5, 0.25);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1.0, player.getZ(),
                12, 0.3, 0.5, 0.3, 0.1);
        level.playSound(null, emitter, ModSounds.RAYSHIELD_HIT.get(), SoundSource.BLOCKS, 1.0f, 1.4f);
    }

    /**
     * Mislukt: alarm (zoals {@code alarm()} in een diagram), rood lampje, en een minuut niet meer proberen. Bij een
     * laserzender gaat de hele poort op slot (voor iedereen, 5 minuten), en is er alleen alarm als hij bij een kern hoort.
     */
    private static void fail(ServerPlayer player, Session session, String why) {
        SESSIONS.remove(player.getUUID());
        reply(player, session, why);
        ServerLevel level = player.serverLevel();
        if (session.kind == Kind.LASER) {
            LaserEmitters.lock(level, session.pos, level.getGameTime() + Math.round(ImtConfig.Bypass.LASER_LOCK_SECONDS * 20));
            LogicEvents.tamperedLaser(level, session.pos, player);
            return;
        }
        LOCKOUTS.computeIfAbsent(player.getUUID(), id -> new HashMap<>())
                .put(GlobalPos.of(session.dimension, session.pos), level.getGameTime() + Math.round(ImtConfig.Bypass.LOCKOUT_SECONDS * 20));
        LogicEvents.tampered(level, session.pos, player);
    }

    private static void reply(ServerPlayer player, Session session, String result) {
        CompoundTag args = new CompoundTag();
        args.putString("result", result);
        ModNetwork.sendTo(player, new BypassPayload(session.pos, "result", args));
    }

    // ================================================================ Bijhouden

    /** Elke vijf ticks: weggelopen, dood, het apparaat weg, of te lang? Dan stopt de hack. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 5 != 0) {
            return;
        }
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        boolean deviceThere = session.kind == Kind.LASER ? level.getBlockEntity(session.pos) instanceof LaserEmitterBlockEntity
                : level.getBlockEntity(session.pos) instanceof InputDeviceBlockEntity;
        boolean gone = !player.isAlive() || session.dimension != level.dimension()
                || player.distanceToSqr(session.pos.getCenter()) > ImtConfig.Bypass.REACH * ImtConfig.Bypass.REACH
                || !deviceThere;
        boolean tooLong = session.kind == Kind.PANEL ? now - session.openedAt > PANEL_TIMEOUT
                : session.startedAt < 0 && now - session.openedAt > CHOOSE_TIMEOUT;
        if (gone || tooLong) {
            stop(player, true);
        } else if (session.startedAt >= 0 && session.kind != Kind.PANEL
                && now - session.startedAt > clockTicks(session) + CLOCK_SLACK * 2) {
            // Je spel zei niets meer (te laat): dan is de tijd gewoon om.
            fail(player, session, "time");
        }
    }

    /** Hoe lang de klok van deze hack loopt (ticks): het stroomcircuit, of de kluwen van een laserzender. */
    private static int clockTicks(Session session) {
        return session.kind == Kind.LASER ? session.ticks : CircuitPuzzle.circuitTicks();
    }

    /**
     * De groepen van een netwerk als een boom, van boven naar beneden: eerst een groep, dan wat erin zit (met elk
     * zijn naam "n" en hoe diep hij zit "d"). Zo ziet de hacker ze zoals de eigenaar in zijn app Keycards.
     */
    static ListTag groupTree(com.senne.imttech.logic.LogicStore store) {
        ListTag rows = new ListTag();
        addGroups(store, "", 0, rows);
        return rows;
    }

    private static void addGroups(com.senne.imttech.logic.LogicStore store, String parent, int depth, ListTag rows) {
        for (String group : store.childrenOf(parent)) {
            CompoundTag row = new CompoundTag();
            row.putString("n", group);
            row.putInt("d", depth);
            rows.add(row);
            addGroups(store, group, depth + 1, rows);
        }
    }

    /** Stopt de hack van deze speler, zonder alarm. {@code tell}: zijn scherm moet ook dicht. */
    public static void stop(ServerPlayer player, boolean tell) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session != null && tell) {
            ModNetwork.sendTo(player, BypassPayload.of(session.pos, "close"));
        }
    }

    /** Hoe lang (ticks) deze speler dit apparaat nog niet mag hacken (0 = mag). */
    public static long lockedFor(ServerPlayer player, BlockPos pos) {
        Map<GlobalPos, Long> mine = LOCKOUTS.get(player.getUUID());
        if (mine == null) {
            return 0;
        }
        long now = player.serverLevel().getGameTime();
        mine.values().removeIf(until -> until <= now);
        Long until = mine.get(GlobalPos.of(player.level().dimension(), pos));
        return until == null ? 0 : until - now;
    }

    /** Het getal achter het raster van de hack van deze speler (voor de tests), of null. */
    public static @Nullable Long seedOf(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null ? null : session.seed;
    }

    /** Hoeveel draden de kluwen heeft van de laserzender die deze speler hackt (voor de tests), of -1. */
    public static int wiresOf(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null || session.kind != Kind.LASER ? -1 : session.wires;
    }

    /** Hoeveel pogingen deze speler nog heeft bij het paneel dat hij kraakt (voor de tests), of -1. */
    public static int triesLeftOf(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null || session.kind != Kind.PANEL ? -1 : session.triesLeft;
    }

    /** Is deze speler aan het hacken? */
    public static boolean isHacking(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    /** Loopt de klok van de lezer die deze speler hackt al (heeft hij een groep gekozen)? Voor de tests. */
    public static boolean clockRuns(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session != null && session.kind == Kind.READER && session.startedAt >= 0;
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        clear();
    }

    /** Alles vergeten (bij het stoppen van de server, en in de tests). */
    public static void clear() {
        SESSIONS.clear();
        LOCKOUTS.clear();
    }
}
