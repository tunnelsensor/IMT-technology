package com.senne.starktech.training;

import com.senne.starktech.bot.BotEntity;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;

/**
 * Robots en drones in een training (hoofdstuk 26). Gaat er een neer (de Ion Blaster van de trainee), dan blijft hij
 * liggen tot de training voorbij is; dan staat hij op en gaat hij terug naar zijn dock (jouw antwoord: "robots staan
 * weer op tenzij ze echt vernietigd zijn").
 */
public final class TrainingBots {
    private TrainingBots() {}

    /** De training is voorbij: wie neerging, staat op en gaat naar zijn dock. */
    static void standUp(ServerLevel level, Training.Session session) {
        for (UUID id : session.downed()) {
            if (level.getEntity(id) instanceof BotEntity bot && bot.isAlive()) {
                bot.setActive(false);
                bot.standUp();
            }
        }
    }
}
