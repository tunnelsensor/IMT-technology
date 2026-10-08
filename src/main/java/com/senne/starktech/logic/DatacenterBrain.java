package com.senne.starktech.logic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Het brein van E.D.I.T.H. draait op je datacenter (jouw wens, 8 oktober: "aan de server moet het
 * E.D.I.T.H.-brein toegevoegd worden; met een GPU gaat dat dubbel zo snel").
 *
 * <ul>
 *   <li>Zonder datacenter werkt er géén brein, ook het woordenboek niet: dan is het gewoon een
 *       mooie bril. In creative heb je geen datacenter nodig (en is er geen vertraging).</li>
 *   <li>Het brein draait op het sterkste datacenter van jezelf dat draait (de kortste vertraging),
 *       waar het ook staat: zie Datacenters.</li>
 *   <li>Het model zelf (het woordenboek, het lokale model of Claude) werkt zoals altijd op jouw pc;
 *       het datacenter zet er alleen een vertraging bij zolang het nog niet sterk is (zie
 *       LogicBalance.brainDelayTicks). Die vertraging stuurt de server twee keer per seconde naar je
 *       spel, met de status van je bril (EdithStatusPayload).</li>
 *   <li>Terwijl ze nadenkt, zegt je spel dat tegen de server (BRAIN_THINKING en BRAIN_DONE): dan
 *       werken de CPU's en GPU's van dat datacenter hard (de app Monitor, en het wordt warmer).</li>
 * </ul>
 */
public final class DatacenterBrain {
    private DatacenterBrain() {}

    /** Langer dan dit (ticks) telt nadenken nooit, ook als het "klaar" nooit aankwam. */
    private static final int MAX_THINK_TICKS = 1200;

    /** Wie nu nadenkt: op welk datacenter, tot wanneer hooguit, en zijn naam (voor de Monitor). */
    private record Thinking(GlobalPos datacenter, long until, String name) {}

    private static final Map<UUID, Thinking> THINKING = new HashMap<>();

    /** Het sterkste datacenter van deze speler dat draait (de kortste vertraging), of null als hij er geen heeft. */
    public static @Nullable Map.Entry<GlobalPos, Datacenters.Summary> best(ServerPlayer player) {
        Map.Entry<GlobalPos, Datacenters.Summary> best = null;
        for (Map.Entry<GlobalPos, Datacenters.Summary> entry : Datacenters.get(player.serverLevel()).ownedBy(player.getUUID())) {
            Datacenters.Summary summary = entry.getValue();
            if (!summary.online() || summary.brainDelay() < 0) {
                continue;
            }
            if (best == null || summary.brainDelay() < best.getValue().brainDelay()) {
                best = entry;
            }
        }
        return best;
    }

    /** Hoeveel ticks het brein van deze speler extra nadenkt: 0 in creative, -1 = hij heeft geen datacenter (geen brein). */
    public static int delayFor(ServerPlayer player) {
        if (player.isCreative()) {
            return 0;
        }
        Map.Entry<GlobalPos, Datacenters.Summary> best = best(player);
        return best == null ? -1 : best.getValue().brainDelay();
    }

    /** Waar het brein van deze speler draait: de naam van het datacenter, "creative", of "" (nergens). */
    public static String sourceFor(ServerPlayer player) {
        if (player.isCreative()) {
            return "creative";
        }
        Map.Entry<GlobalPos, Datacenters.Summary> best = best(player);
        return best == null ? "" : best.getValue().host();
    }

    /** Het spel van deze speler zegt: ze denkt nu na. Dan werkt zijn sterkste datacenter hard. */
    public static void start(ServerPlayer player) {
        Map.Entry<GlobalPos, Datacenters.Summary> best = player.isCreative() ? null : best(player);
        if (best == null) {
            THINKING.remove(player.getUUID());
            return;
        }
        THINKING.put(player.getUUID(), new Thinking(best.getKey(), player.serverLevel().getGameTime() + MAX_THINK_TICKS,
                player.getGameProfile().getName()));
    }

    /** Ze is klaar met nadenken. */
    public static void stop(ServerPlayer player) {
        THINKING.remove(player.getUUID());
    }

    /** Wie er nu op dit datacenter nadenkt (hun namen). */
    public static List<String> thinkingOn(ServerLevel level, GlobalPos datacenter) {
        List<String> names = new ArrayList<>();
        long now = level.getGameTime();
        Iterator<Thinking> it = THINKING.values().iterator();
        while (it.hasNext()) {
            Thinking thinking = it.next();
            if (now > thinking.until() || now < thinking.until() - MAX_THINK_TICKS) {
                it.remove(); // te lang (of de tijd sprong): vergeten
            } else if (thinking.datacenter().equals(datacenter)) {
                names.add(thinking.name());
            }
        }
        return names;
    }

    public static void forget(UUID player) {
        THINKING.remove(player);
    }

    public static void clear() {
        THINKING.clear();
    }
}
