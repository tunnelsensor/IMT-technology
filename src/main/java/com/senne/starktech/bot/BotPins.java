package com.senne.starktech.bot;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.logic.program.Expr;
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
 *   <li><b>Laser</b>: {@code read} = true als hij kan schieten (zijn armen zijn klaar en zijn batterij is vol
 *       genoeg), {@code write(laserPin, true)} = één schot (zie BotShot; kost batterij);</li>
 *   <li><b>Gun</b>: {@code read} = hoeveel kogels hij nog heeft, {@code write(gunPin, true)} = één schot (kost één
 *       Robot Ammo; rechtsklik hem met Robot Ammo om te laden);</li>
 *   <li><b>Solar Panel</b>: {@code read} = hoeveel zon hij nu vangt, van 0 tot 100 (hij laadt er zelf mee, ook
 *       als hij uit staat);</li>
 *   <li><b>Camera</b>: {@code read} = true als iemand op de desktop door zijn ogen kijkt (de app Cameras),
 *       {@code write(cameraPin, false)} zet de camera uit, {@code true} weer aan;</li>
 *   <li>een lege pin, of een verbrande module: {@code read} = false.</li>
 * </ul>
 * Een module die niets met {@code write} doet, zegt dat in het logboek (één keer per minuut).
 */
public final class BotPins {
    private final Bot bot;
    private final BotBrain brain;
    /** Wie hij zag, en op welke tick (zodat een programma dat vaak kijkt, niet elke keer opnieuw rekent). */
    private long seenTick = Long.MIN_VALUE;
    private List<Player> seen = List.of();
    /** Het filter van zijn Face Recognition (kleine letters, of een spelernaam). */
    private String filter = EVERYONE;
    /** Wanneer (speltijd) zijn laser en zijn geweer weer mogen schieten. */
    private long nextLaser;
    private long nextGun;
    /** Staat zijn camera aan (write(cameraPin, false) zet hem uit)? */
    private boolean cameraOn = true;
    /** Tot wanneer (speltijd) er iemand door zijn camera kijkt (de app Cameras zegt het om de seconde). */
    private long watchedUntil = Long.MIN_VALUE;

    public static final String EVERYONE = "everyone";
    public static final String STRANGERS = "strangers";
    public static final String TRUSTED = "trusted";
    public static final String TRAINEE = "trainee";

    BotPins(Bot bot, BotBrain brain) {
        this.bot = bot;
        this.brain = brain;
    }

    /** Wat de module op deze pin nu zegt. */
    public Object read(int pin) {
        BotModule module = bot.board().module(pin);
        if (module == null) {
            return false;
        }
        return switch (module) {
            case SCAN -> !targets().isEmpty();
            case FACE_RECOGNITION -> targets().isEmpty() ? "" : targets().getFirst().getGameProfile().getName();
            case DISTANCE -> BotSense.distance(bot);
            case PATHFINDING -> brain.body().goingSomewhere();
            case LASER -> laserReady();
            case GUN -> (double) bot.ammo();
            case SOLAR_PANEL -> bot.level() instanceof ServerLevel level ? (double) Math.round(bot.sunlight(level) * 100) : 0.0;
            case CAMERA -> watched();
            default -> false;
        };
    }

    /** Kan de laser nu schieten? Zijn armen moeten klaar zijn, en zijn batterij vol genoeg voor een schot. */
    private boolean laserReady() {
        return bot.level().getGameTime() >= nextLaser && bot.battery() >= StarkConfig.Robots.LASER_ENERGY;
    }

    /** Zoveel ticks tussen twee schoten: van zijn armen. */
    private long fireInterval() {
        return Math.max(1, Math.round(bot.fireInterval()));
    }

    /**
     * Wie hij nu als doelwit kiest: de dichtste die zijn Scan ziet en door het filter komt. Zonder Scan-module richt
     * hij niet (dan schiet hij recht vooruit).
     */
    private @Nullable Player target() {
        if (!bot.board().has(BotModule.SCAN)) {
            return null;
        }
        List<Player> targets = targets();
        return targets.isEmpty() ? null : targets.getFirst();
    }

    /** Vertel de module op deze pin iets. */
    public void write(int pin, Object value) {
        BotModule module = bot.board().module(pin);
        if (module == BotModule.FACE_RECOGNITION) {
            String text = Expr.text(value).strip();
            String lower = text.toLowerCase(Locale.ROOT);
            filter = List.of(EVERYONE, STRANGERS, TRUSTED, TRAINEE).contains(lower) ? lower : text;
            return;
        }
        if (module == BotModule.CAMERA) {
            cameraOn = Boolean.TRUE.equals(value);
            return;
        }
        if (module == BotModule.LASER || module == BotModule.GUN) {
            // Alleen write(pin, true) schiet; niet klaar (of geen kogels): dan gebeurt er niets.
            if (!Boolean.TRUE.equals(value)) {
                return;
            }
            long now = bot.level().getGameTime();
            if (module == BotModule.LASER && laserReady()) {
                nextLaser = now + fireInterval();
                bot.setBattery(bot.battery() - StarkConfig.Robots.LASER_ENERGY);
                BotShot.fire(bot, BotShot.Weapon.LASER, target());
            } else if (module == BotModule.GUN && now >= nextGun && bot.ammo() > 0) {
                nextGun = now + fireInterval();
                bot.setAmmo(bot.ammo() - 1);
                BotShot.fire(bot, BotShot.Weapon.GUN, target());
            }
            return;
        }
        brain.report("pin " + pin + (module == null ? " is empty" : " (" + module.title() + ")") + ": write(...) does nothing there");
    }

    /** Het filter van zijn Face Recognition. */
    public String filter() {
        return filter;
    }

    /** Opnieuw opstarten: het filter weer op iedereen, de camera weer aan. */
    void reset() {
        filter = EVERYONE;
        cameraOn = true;
    }

    // ================================================================ De camera

    /** Kan de app Cameras door zijn ogen kijken? (Een Camera-module, en zijn programma zette hem niet uit.) */
    public boolean cameraOn() {
        return cameraOn && bot.board().has(BotModule.CAMERA);
    }

    /** Iemand kijkt nu door zijn camera (de app Cameras zegt het om de seconde); dat geldt 2 seconden. */
    public void watch() {
        watchedUntil = bot.level().getGameTime() + 40;
    }

    /** Kijkt er nu iemand door zijn camera? */
    public boolean watched() {
        return bot.level().getGameTime() < watchedUntil;
    }

    /** Wie hij ziet en die door het filter komt, de dichtste eerst. */
    List<Player> targets() {
        if (filter.equals(EVERYONE)) {
            return seen();
        }
        SecurityCoreBlockEntity core = BotLink.core(bot);
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
            case STRANGERS -> core != null && bot.level() instanceof ServerLevel level && !SecurityCores.isFriend(level, core, player);
            case TRUSTED -> core != null && bot.level() instanceof ServerLevel level && SecurityCores.isFriend(level, core, player);
            case TRAINEE -> false; // een training komt in hoofdstuk 26
            default -> player.getGameProfile().getName().equalsIgnoreCase(filter);
        };
    }

    /** Wie hij nu ziet, de dichtste eerst (één keer per tick uitgerekend). */
    List<Player> seen() {
        long now = bot.level().getGameTime();
        if (now != seenTick) {
            seenTick = now;
            seen = BotSense.seen(bot);
        }
        return seen;
    }
}
