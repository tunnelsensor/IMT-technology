package com.senne.starktech.bot;

import com.senne.starktech.logic.program.Expr;
import com.senne.starktech.robot.GuardRobotEntity;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * De pinnen van een robot in zijn programma (hoofdstuk 24): {@code read(pin)} en {@code write(pin, ...)}. Wat een
 * pin doet, hangt af van de module die erop gesoldeerd is:
 * <ul>
 *   <li><b>Scan</b>: {@code read} = true als hij een speler ziet (zie BotSense) die door het filter komt;</li>
 *   <li><b>Distance</b>: {@code read} = hoeveel blokken tot wat recht voor hem staat (99 = niets);</li>
 *   <li><b>Face Recognition</b>: {@code read} = de naam van wie hij ziet ("" = niemand), {@code write} = het
 *       filter: "everyone" (iedereen, zo begint hij), "strangers" (wie niet bij de basis hoort), "trusted"
 *       (de eigenaar, zijn team en vertrouwde spelers), "trainee" (hoofdstuk 26) of de naam van een speler.
 *       Wie bij de basis hoort, vraagt hij aan de server: zonder verbinding ziet hij met "strangers" en "trusted"
 *       niemand (zo schiet hij nooit op een vriend omdat hij het niet weet);</li>
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
    /** Het filter van zijn Face Recognition (kleine letters, of een spelernaam). */
    private String filter = EVERYONE;

    public static final String EVERYONE = "everyone";
    public static final String STRANGERS = "strangers";
    public static final String TRUSTED = "trusted";
    public static final String TRAINEE = "trainee";

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
            case SCAN -> !targets().isEmpty();
            case FACE_RECOGNITION -> targets().isEmpty() ? "" : targets().getFirst().getGameProfile().getName();
            case DISTANCE -> BotSense.distance(robot);
            case PATHFINDING -> brain.body().goingSomewhere();
            default -> false;
        };
    }

    /** Vertel de module op deze pin iets. */
    public void write(int pin, Object value) {
        BotModule module = robot.board().module(pin);
        if (module == BotModule.FACE_RECOGNITION) {
            String text = Expr.text(value).strip();
            String lower = text.toLowerCase(Locale.ROOT);
            filter = List.of(EVERYONE, STRANGERS, TRUSTED, TRAINEE).contains(lower) ? lower : text;
            return;
        }
        brain.report("pin " + pin + (module == null ? " is empty" : " (" + module.title() + ")") + ": write(...) does nothing there");
    }

    /** Het filter van zijn Face Recognition. */
    public String filter() {
        return filter;
    }

    /** Opnieuw opstarten: het filter weer op iedereen. */
    void reset() {
        filter = EVERYONE;
    }

    /** Wie hij ziet en die door het filter komt, de dichtste eerst. */
    List<Player> targets() {
        if (filter.equals(EVERYONE)) {
            return seen();
        }
        SecurityCoreBlockEntity core = BotLink.core(robot);
        List<Player> targets = new ArrayList<>();
        for (Player player : seen()) {
            if (matches(player, core)) {
                targets.add(player);
            }
        }
        return targets;
    }

    /** Komt deze speler door het filter? (Vriend of niet vraagt hij aan zijn server: zonder verbinding niemand.) */
    private boolean matches(Player player, @Nullable SecurityCoreBlockEntity core) {
        return switch (filter) {
            case STRANGERS -> core != null && robot.level() instanceof ServerLevel level && !SecurityCores.isFriend(level, core, player);
            case TRUSTED -> core != null && robot.level() instanceof ServerLevel level && SecurityCores.isFriend(level, core, player);
            case TRAINEE -> false; // een training komt in hoofdstuk 26
            default -> player.getGameProfile().getName().equalsIgnoreCase(filter);
        };
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
