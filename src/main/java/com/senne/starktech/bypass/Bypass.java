package com.senne.starktech.bypass;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.logic.CodePanelBlock;
import com.senne.starktech.logic.InputDeviceBlockEntity;
import com.senne.starktech.logic.KeycardReaderBlock;
import com.senne.starktech.logic.LogicEvents;
import com.senne.starktech.logic.LogicServer;
import com.senne.starktech.logic.PanelCodes;
import com.senne.starktech.network.BypassPayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
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
 *       kiest een niveau (dan begint de klok, ook op de server), en als het signaal bij ACCEPT komt,
 *       stuurt je spel hoe alle stukjes staan. De server rekent zelf na of dat klopt en of je op tijd
 *       was; pas dan leest de lezer een nagemaakte kaart van dat niveau.</li>
 *   <li><b>Een codepaneel</b>: de server zoekt de code in je diagrammen (PanelCodes) en stuurt alleen
 *       de vingerafdrukken: welke cijfers erin zitten en hoe vaak, en hoe lang de code is. Elke volgorde
 *       die je probeert, gaat naar de server; die zegt hoeveel cijfers op de goede plek staan. De code
 *       zelf krijg je pas als je hem geraden hebt; dan typt de kit hem ook echt in.</li>
 * </ul>
 * Mislukt het (ALARM, de tijd is om, of geen pogingen meer), dan gaat het alarm af en laat dat apparaat jou een minuut
 * niet meer proberen. Esc of weglopen stopt de hack zonder alarm.
 */
public final class Bypass {
    private Bypass() {}

    /** Wat er gehackt wordt. */
    public enum Kind { READER, PANEL }

    /** Zoveel ticks speling op de klok (een bericht onderweg doet er even over). */
    private static final int CLOCK_SLACK = 40;
    /** Zo lang mag je over het kiezen van een niveau doen, dan gaat het scherm dicht (zonder alarm). */
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
        /** Het niveau van de kaart (lezer); 0 = nog niet gekozen. */
        int level;
        /** Wanneer de klok begon te lopen (game-tijd), of -1. */
        long startedAt = -1;
        /** De code die de kit moet kraken (paneel), en hoeveel pogingen je nog hebt. */
        String code = "";
        int triesLeft;

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
        if (!(level.getBlockEntity(pos) instanceof InputDeviceBlockEntity device)) {
            return;
        }
        Kind kind = level.getBlockState(pos).getBlock() instanceof KeycardReaderBlock ? Kind.READER
                : level.getBlockState(pos).getBlock() instanceof CodePanelBlock ? Kind.PANEL : null;
        if (kind == null) {
            return;
        }
        if (player.distanceToSqr(pos.getCenter()) > StarkConfig.Bypass.REACH * StarkConfig.Bypass.REACH) {
            return;
        }
        long wait = lockedFor(player, pos);
        if (wait > 0) {
            player.displayClientMessage(Component.translatable("bypass.starktech.msg.locked", device.name(),
                    (wait + 19) / 20).withStyle(ChatFormatting.RED), true);
            return;
        }
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, pos);
        if (core == null || !LogicServer.stats(level, core).online()) {
            player.displayClientMessage(Component.translatable("bypass.starktech.msg.no_server")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        // Een paneel: welke code opent het? Staat er geen in je diagrammen, dan valt er niets te kraken.
        List<String> codes = kind == Kind.PANEL ? PanelCodes.forPanel(core, device.name()) : List.of();
        if (kind == Kind.PANEL && codes.isEmpty()) {
            player.displayClientMessage(Component.translatable("bypass.starktech.msg.no_code")
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
            ModNetwork.sendTo(player, new BypassPayload(pos, "reader", args));
        } else {
            // De eerste code uit je diagrammen. Naar jouw spel gaan alleen de afdrukken en de lengte.
            session.code = codes.getFirst();
            session.triesLeft = session.code.length() + StarkConfig.Bypass.ORDER_EXTRA_TRIES;
            level.playSound(null, pos, SoundEvents.BRUSH_GENERIC, SoundSource.BLOCKS, 0.4f, 1.2f);
            args.putInt("length", session.code.length());
            args.putIntArray("prints", PanelOrders.prints(session.code));
            args.putInt("tries", session.triesLeft);
            ModNetwork.sendTo(player, new BypassPayload(pos, "panel", args));
        }
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
            case "level" -> {
                if (session.kind == Kind.READER && session.startedAt < 0) {
                    session.level = CircuitPuzzle.clampLevel(payload.args().getInt("level"));
                    session.startedAt = now;
                }
            }
            case "solve" -> {
                if (session.kind != Kind.READER || session.startedAt < 0) {
                    return;
                }
                if (now - session.startedAt > CircuitPuzzle.circuitSeconds(session.level) * 20L + CLOCK_SLACK) {
                    fail(player, session, "time");
                    return;
                }
                CircuitPuzzle puzzle = CircuitPuzzle.generate(session.seed, session.level);
                int[] rotations = puzzle.readRotations(payload.args().getByteArray("rot"));
                if (rotations == null || !puzzle.signal(rotations).solved()) {
                    // Jouw spel dacht dat het klopte, de server niet: gewoon verder puzzelen.
                    reply(player, session, "retry");
                    return;
                }
                SESSIONS.remove(player.getUUID());
                reply(player, session, "ok");
                LogicEvents.forgedKeycard(level, session.pos, player, session.level);
            }
            case "guess" -> {
                if (session.kind == Kind.PANEL) {
                    guess(player, session, payload.args().getString("code"));
                }
            }
            case "fail" -> {
                if (session.kind == Kind.READER && session.startedAt >= 0) {
                    String why = payload.args().getString("why");
                    fail(player, session, why.equals("alarm") ? "alarm" : "time");
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

    /** Mislukt: alarm (zoals {@code alarm()} in een diagram), rood lampje, en een minuut niet meer proberen. */
    private static void fail(ServerPlayer player, Session session, String why) {
        SESSIONS.remove(player.getUUID());
        reply(player, session, why);
        ServerLevel level = player.serverLevel();
        LOCKOUTS.computeIfAbsent(player.getUUID(), id -> new HashMap<>())
                .put(GlobalPos.of(session.dimension, session.pos), level.getGameTime() + StarkConfig.Bypass.LOCKOUT_SECONDS * 20L);
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
        boolean gone = !player.isAlive() || session.dimension != level.dimension()
                || player.distanceToSqr(session.pos.getCenter()) > StarkConfig.Bypass.REACH * StarkConfig.Bypass.REACH
                || !(level.getBlockEntity(session.pos) instanceof InputDeviceBlockEntity);
        boolean tooLong = session.kind == Kind.PANEL ? now - session.openedAt > PANEL_TIMEOUT
                : session.startedAt < 0 && now - session.openedAt > CHOOSE_TIMEOUT;
        if (gone || tooLong) {
            stop(player, true);
        } else if (session.startedAt >= 0 && session.kind == Kind.READER
                && now - session.startedAt > CircuitPuzzle.circuitSeconds(session.level) * 20L + CLOCK_SLACK * 2) {
            // Je spel zei niets meer (te laat): dan is de tijd gewoon om.
            fail(player, session, "time");
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

    /** Hoeveel pogingen deze speler nog heeft bij het paneel dat hij kraakt (voor de tests), of -1. */
    public static int triesLeftOf(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        return session == null || session.kind != Kind.PANEL ? -1 : session.triesLeft;
    }

    /** Is deze speler aan het hacken? */
    public static boolean isHacking(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
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
