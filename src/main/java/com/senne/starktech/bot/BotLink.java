package com.senne.starktech.bot;

import com.senne.starktech.logic.LogicServer;
import com.senne.starktech.robot.GuardRobotEntity;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * De verbinding van een robot met zijn server (hoofdstuk 23). Een robot is verbonden als:
 * <ul>
 *   <li>hij in het gebied van een beveiligingskern staat;</li>
 *   <li>hij bij die basis hoort: zijn eigenaar is daar de eigenaar, of vertrouwd;</li>
 *   <li>en die kern stroom heeft en de server (de serverracks) draait.</li>
 * </ul>
 * Alleen met verbinding krijgt hij een nieuw programma, en praat hij met de server (alarm, relais, berichten).
 * Zijn eigen code (lopen, laden) werkt ook zonder: die draait op de robot zelf.
 */
public final class BotLink {
    private BotLink() {}

    /** De kern waarmee deze robot nu verbonden is, of null. */
    public static @Nullable SecurityCoreBlockEntity core(GuardRobotEntity robot) {
        if (!(robot.level() instanceof ServerLevel level) || robot.owner() == null) {
            return null;
        }
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, robot.blockPosition());
        if (core == null || !belongs(core, robot.owner())) {
            return null;
        }
        return LogicServer.stats(level, core).online() ? core : null;
    }

    /** Is deze robot nu verbonden met een server? ({@code robot.connected}) */
    public static boolean connected(GuardRobotEntity robot) {
        return core(robot) != null;
    }

    /** Hoort een robot van deze eigenaar bij deze basis? */
    private static boolean belongs(SecurityCoreBlockEntity core, UUID owner) {
        return owner.equals(core.ownerId()) || core.isTrusted(owner);
    }
}
