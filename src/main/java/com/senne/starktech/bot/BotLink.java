package com.senne.starktech.bot;

import com.senne.starktech.logic.LogicServer;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
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

    /** {@code order("Guard 1", "attack", x, y, z)}: de robot begint zijn {@code when ordered("attack"):}. */
    public static Result order(ServerLevel level, SecurityCoreBlockEntity core, String name, String order, @Nullable BlockPos place) {
        BotNetwork.Entry entry = find(level, core, name);
        if (entry == null) {
            return Result.UNKNOWN;
        }
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

    /** {@code activate("Guard 1")} of {@code deactivate("Guard 1")}: zet de robot aan of uit. */
    public static Result switchOn(ServerLevel level, SecurityCoreBlockEntity core, String name, boolean on) {
        BotNetwork.Entry entry = find(level, core, name);
        return entry == null ? Result.UNKNOWN : switchOn(level, core, entry, on);
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
