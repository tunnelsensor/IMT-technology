package com.senne.starktech.bot;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/**
 * Hoe een Bot beweegt (hoofdstuk 25): een robot loopt (BotMove), een drone vliegt (DroneMove). Het brein geeft zijn
 * lijf één beweging tegelijk (een {@link BotMove.Plan}) en wacht tot die klaar is.
 */
public interface BotBody {
    /** De kant waar hij naar kijkt (en waar vooruit heen gaat). */
    Direction facing();

    void setFacing(@Nullable Direction facing);

    /** Stond er bij zijn laatste beweging iets in de weg? ({@code robot.blocked}) */
    boolean blocked();

    /** Iets lukte niet zonder dat hij bewoog: geblokkeerd. */
    void markBlocked();

    /** Is hij met een beweging bezig? */
    boolean busy();

    /** Wil hij nu naar zijn dock (om te laden)? */
    boolean wantsDock();

    /** Is hij met {@code goto} onderweg? (Wat de Pathfinding-module zegt.) */
    boolean goingSomewhere();

    /** Begin aan deze beweging. */
    void start(BotMove.Plan plan);

    /** Stop meteen met wat hij deed. */
    void stop();

    /** Een opdracht van de server onderbreekt deze beweging: wat er nog over is (om daarna verder te doen), of null. */
    @Nullable BotMove.Plan interrupt();

    /** Elke tick terwijl zijn programma draait. Geeft true op de tick dat de beweging klaar is. */
    boolean tick(ServerLevel level);
}
