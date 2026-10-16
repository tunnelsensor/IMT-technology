package com.senne.starktech.bot;

import com.senne.starktech.robot.GuardRobotEntity;
import java.util.List;
import net.minecraft.world.entity.player.Player;

/**
 * De pinnen van een robot in zijn programma (hoofdstuk 24): {@code read(pin)} en {@code write(pin, ...)}. Wat een
 * pin doet, hangt af van de module die erop gesoldeerd is:
 * <ul>
 *   <li><b>Scan</b>: {@code read} = true als hij een speler ziet (zie BotSense);</li>
 *   <li><b>Distance</b>: {@code read} = hoeveel blokken tot wat recht voor hem staat (99 = niets);</li>
 *   <li><b>Pathfinding</b>: {@code read} = true zolang hij met {@code goto} onderweg is;</li>
 *   <li>een lege pin, of een verbrande module: {@code read} = false.</li>
 * </ul>
 * Een module die niets met {@code write} doet, zegt dat in het logboek (één keer per minuut).
 */
public final class BotPins {
    private final GuardRobotEntity robot;
    private final BotBrain brain;
    /** Wie hij zag, en op welke tick (zodat een programma dat vaak kijkt, niet elke keer opnieuw rekent). */
    private long seenTick = Long.MIN_VALUE;
    private List<Player> seen = List.of();

    BotPins(GuardRobotEntity robot, BotBrain brain) {
        this.robot = robot;
        this.brain = brain;
    }

    /** Wat de module op deze pin nu zegt. */
    public Object read(int pin) {
        BotModule module = robot.board().module(pin);
        if (module == null) {
            return false;
        }
        return switch (module) {
            case SCAN -> !seen().isEmpty();
            case DISTANCE -> BotSense.distance(robot);
            case PATHFINDING -> brain.body().goingSomewhere();
            default -> false;
        };
    }

    /** Vertel de module op deze pin iets. */
    public void write(int pin, Object value) {
        BotModule module = robot.board().module(pin);
        brain.report("pin " + pin + (module == null ? " is empty" : " (" + module.title() + ")") + ": write(...) does nothing there");
    }

    /** Wie hij nu ziet, de dichtste eerst (één keer per tick uitgerekend). */
    List<Player> seen() {
        long now = robot.level().getGameTime();
        if (now != seenTick) {
            seenTick = now;
            seen = BotSense.seen(robot);
        }
        return seen;
    }
}
