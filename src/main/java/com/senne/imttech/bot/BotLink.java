package com.senne.imttech.bot;

import com.senne.imttech.logic.LogicServer;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * De verbinding van een robot met zijn server (hoofdstuk 23). Een robot is verbonden als:
 * <ul>
 *   <li>hij in het gebied van een beveiligingskern staat;</li>
 *   <li>hij bij die basis hoort: zijn eigenaar is daar de eigenaar, of vertrouwd;</li>
 *   <li>en die kern stroom heeft en de server (de serverracks) draait.</li>
 * </ul>
 * Alleen met verbinding krijgt hij een nieuw programma, praat hij met de server (alarm, relais, berichten), en
 * krijgt hij opdrachten van de server ({@code order}, {@code activate}, {@code deactivate}). Zijn eigen code
 * (lopen, laden) werkt ook zonder: die draait op de robot zelf. Een robot die niet geladen is, krijgt zijn
 * opdracht als hij terug is en verbinding heeft (hij wacht in BotNetwork).
 */
public final class BotLink {
    private BotLink() {}

    /** De kern waarmee deze robot nu verbonden is, of null. */
    public static @Nullable SecurityCoreBlockEntity core(Bot bot) {
        if (!(bot.level() instanceof ServerLevel level) || bot.owner() == null) {
            return null;
        }
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, bot.blockPosition());
        if (core == null || !belongs(core, bot.owner())) {
            return null;
        }
        return LogicServer.stats(level, core).online() ? core : null;
    }

    /** Is deze robot nu verbonden met een server? ({@code robot.connected}) */
    public static boolean connected(Bot bot) {
        return core(bot) != null;
    }

    /** Hoort een robot van deze eigenaar bij deze basis? */
    private static boolean belongs(SecurityCoreBlockEntity core, UUID owner) {
        return owner.equals(core.ownerId()) || core.isTrusted(owner);
    }

    // ================================================================ De server stuurt een robot aan (hoofdstuk 23)

    /** Hoe het ging: gedaan, wacht tot hij terug is, geen robot met die naam, of hij hoort hier niet (geen verbinding). */
    public enum Result { DONE, WAITING, UNKNOWN, NOT_CONNECTED }

    /** De robot met deze naam (hoofdletters tellen niet) in het gebied van deze kern, of null. */
    public static BotNetwork.@Nullable Entry find(ServerLevel level, SecurityCoreBlockEntity core, String name) {
        GlobalPos here = GlobalPos.of(level.dimension(), core.getBlockPos());
        for (BotNetwork.Entry entry : BotNetwork.get(level).atCore(here)) {
            if (entry.name().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return entry;
            }
        }
        return null;
    }

    /**
     * De robots en drones met deze naam, of (hoofdstuk 26) van de squad met deze naam, in het gebied van deze kern.
     * Een robot met die naam gaat voor.
     */
    public static List<BotNetwork.Entry> named(ServerLevel level, SecurityCoreBlockEntity core, String name) {
        BotNetwork.Entry one = find(level, core, name);
        if (one != null) {
            return List.of(one);
        }
        return BotNetwork.get(level).squad(GlobalPos.of(level.dimension(), core.getBlockPos()), name);
    }

    /** Hoeveel robots en drones met deze naam (of van deze squad) staan nu aan en recht ({@code active("Wave 1")}). */
    public static int active(ServerLevel level, SecurityCoreBlockEntity core, String name) {
        int count = 0;
        for (BotNetwork.Entry entry : named(level, core, name)) {
            if (entry.state() == com.senne.imttech.robot.RobotState.ACTIVE) {
                count++;
            }
        }
        return count;
    }

    /**
     * {@code order("Guard 1", "attack", x, y, z)}: de robot begint zijn {@code when ordered("attack"):}. Is "Guard 1"
     * een squad (hoofdstuk 26), dan krijgen ze hem allemaal.
     */
    public static Result order(ServerLevel level, SecurityCoreBlockEntity core, String name, String order, @Nullable BlockPos place) {
        List<BotNetwork.Entry> entries = named(level, core, name);
        if (entries.isEmpty()) {
            return Result.UNKNOWN;
        }
        Result result = Result.NOT_CONNECTED;
        for (BotNetwork.Entry entry : entries) {
            result = best(result, order(level, core, entry, order, place));
        }
        return result;
    }

    /** De beste uitkomst van twee (voor een squad: gelukt bij één is gelukt). */
    private static Result best(Result a, Result b) {
        return a == Result.DONE || b == Result.DONE ? Result.DONE : a == Result.WAITING || b == Result.WAITING ? Result.WAITING : b;
    }

    private static Result order(ServerLevel level, SecurityCoreBlockEntity core, BotNetwork.Entry entry, String order,
                                @Nullable BlockPos place) {
        if (!(level.getEntity(entry.id()) instanceof Bot bot) || !bot.isAlive()) {
            BotNetwork.get(level).setPendingOrder(entry.id(), order, place);
            return Result.WAITING;
        }
        if (core(bot) != core) {
            return Result.NOT_CONNECTED;
        }
        bot.brain().order(order, place);
        return Result.DONE;
    }

    /**
     * {@code activate("Guard 1")} of {@code deactivate("Guard 1")}: zet de robot aan of uit. Is "Guard 1" een squad
     * (hoofdstuk 26, "Wave 1"), dan allemaal tegelijk.
     */
    public static Result switchOn(ServerLevel level, SecurityCoreBlockEntity core, String name, boolean on) {
        List<BotNetwork.Entry> entries = named(level, core, name);
        if (entries.isEmpty()) {
            return Result.UNKNOWN;
        }
        Result result = Result.NOT_CONNECTED;
        for (BotNetwork.Entry entry : entries) {
            result = best(result, switchOn(level, core, entry, on));
        }
        return result;
    }

    /** Hetzelfde, voor deze robot of drone uit BotNetwork (hoofdstuk 25: de schakelaar "Drones" van de kern). */
    public static Result switchOn(ServerLevel level, SecurityCoreBlockEntity core, BotNetwork.Entry entry, boolean on) {
        if (!(level.getEntity(entry.id()) instanceof Bot bot) || !bot.isAlive()) {
            BotNetwork.get(level).setPendingSwitch(entry.id(), on);
            return Result.WAITING;
        }
        if (core(bot) != core) {
            return Result.NOT_CONNECTED;
        }
        bot.setActive(on);
        BotNetwork.get(level).update(bot);
        return Result.DONE;
    }
}
