package com.senne.imttech.bot;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.logic.program.Expr;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * De pinnen van een robot in zijn programma (hoofdstuk 24): {@code read(pin)} en {@code write(pin, ...)}. Jouw regel (19
 * oktober): "read(pin): je krijgt info van die pin als het kan; write(pin, true/false): je togglet die pin aan/uit als het
 * kan". Elke module heeft dus een aan/uit-stand ({@link #isOn}): sensoren beginnen aan, wapens uit. Opnieuw opstarten zet
 * alles weer zoals het begon. Wat een pin verder doet, hangt af van de module die erop gesoldeerd is:
 * <ul>
 *   <li><b>Scan</b>: {@code read} = true als hij een speler ziet (zie BotSense) die door het filter komt. Uit: hij ziet
 *       niemand;</li>
 *   <li><b>Distance</b>: {@code read} = hoeveel blokken tot wat recht voor hem staat (99 = niets, ook als hij uit
 *       staat);</li>
 *   <li><b>Face Recognition</b>: {@code read} = de naam van wie hij ziet ("" = niemand), {@code write} met tekst = het
 *       filter: "everyone" (iedereen, zo begint hij), "strangers" (wie niet bij de basis hoort), "trusted"
 *       (de eigenaar, zijn team en vertrouwde spelers), "trainee" (hoofdstuk 26) of de naam van een speler.
 *       Wie bij de basis hoort, vraagt hij aan de server: zonder verbinding ziet hij met "strangers" en "trusted"
 *       niemand (zo schiet hij nooit op een vriend omdat hij het niet weet). Uit: hij herkent niemand ({@code read} =
 *       ""), en zijn Scan telt iedereen die hij ziet;</li>
 *   <li><b>Pathfinding</b>: {@code read} = true zolang hij met {@code goto} onderweg is. Uit: een nieuwe {@code goto}
 *       lukt niet (hij is dan geblokkeerd);</li>
 *   <li><b>Laser</b>: {@code read} = true als hij kan schieten (zijn armen zijn klaar en zijn batterij is vol
 *       genoeg). Aan: hij schiet zodra hij klaar is (zie {@link #tick}; elk schot kost batterij) tot je hem uitzet;</li>
 *   <li><b>Gun</b>: {@code read} = hoeveel kogels hij nog heeft. Aan: hetzelfde, elk schot kost één Robot Ammo
 *       (rechtsklik hem met Robot Ammo om te laden);</li>
 *   <li><b>Solar Panel</b>: {@code read} = hoeveel zon hij nu vangt, van 0 tot 100 (hij laadt er zelf mee, ook
 *       als hij uit staat). Uit: geen zon, en hij laadt niet;</li>
 *   <li><b>Camera</b>: {@code read} = true als iemand op de desktop door zijn ogen kijkt (de app Cameras). Uit: geen
 *       beeld;</li>
 *   <li>een lege pin, of een verbrande module: {@code read} = false, en {@code write} doet niets.</li>
 * </ul>
 * Wat niet kan, zegt hij in het logboek (één keer per minuut).
 *
 * <h2>Waar hij op schiet</h2>
 * Op de plek van {@code aim(x, y, z)} als je die gaf (jouw antwoord: "richten op wat ik kies"; hij raakt wat op die lijn
 * staat), en anders op wie zijn Scan ziet, door het filter van zijn Face Recognition. Ziet die niemand, dan wacht hij: een
 * wapen dat aan staat, schiet niet in het wilde weg. Alleen zonder werkende Scan schiet hij recht vooruit.
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
    /** De pinnen die anders staan dan hun module begint: een sensor die uit staat, of een wapen dat aan staat (een bitje per pin). */
    private int flipped;
    /** Waar zijn wapens op richten met {@code aim(x, y, z)}, of null: op wie zijn Scan ziet. */
    private @Nullable Vec3 aim;
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
        boolean on = isOn(pin);
        return switch (module) {
            case SCAN -> on && !targets().isEmpty();
            case FACE_RECOGNITION -> !on || targets().isEmpty() ? "" : targets().getFirst().getGameProfile().getName();
            case DISTANCE -> on ? BotSense.distance(bot) : BotSense.NOTHING;
            case PATHFINDING -> brain.body().goingSomewhere();
            case LASER -> laserReady();
            case GUN -> (double) bot.ammo();
            case SOLAR_PANEL -> on && bot.level() instanceof ServerLevel level ? (double) Math.round(bot.sunlight(level) * 100) : 0.0;
            case CAMERA -> on && watched();
            default -> false;
        };
    }

    // ================================================================ Aan en uit

    /** Staat de module op deze pin aan? (Sensoren beginnen aan, wapens uit; een lege pin of een verbrande module nooit.) */
    public boolean isOn(int pin) {
        BotModule module = bot.board().module(pin);
        return module != null && module != BotModule.BURNT && startsOn(module) != ((flipped & 1 << pin) != 0);
    }

    private static boolean startsOn(BotModule module) {
        return module != BotModule.LASER && module != BotModule.GUN;
    }

    /** Staat er op een van zijn pinnen zo'n module aan? */
    public boolean moduleOn(BotModule module) {
        for (int pin = 1; pin <= BotBoard.PINS; pin++) {
            if (bot.board().module(pin) == module && isOn(pin)) {
                return true;
            }
        }
        return false;
    }

    // ================================================================ Schieten

    /** Kan de laser nu schieten? Zijn armen moeten klaar zijn, en zijn batterij vol genoeg voor een schot. */
    private boolean laserReady() {
        return bot.level().getGameTime() >= nextLaser && bot.battery() >= ImtConfig.Robots.LASER_ENERGY;
    }

    /** Zoveel ticks tussen twee schoten: van zijn armen. */
    private long fireInterval() {
        return Math.max(1, Math.round(bot.fireInterval()));
    }

    /** Kan het geweer nu schieten? Zijn armen moeten klaar zijn, en er moet een kogel in zitten. */
    private boolean gunReady() {
        return bot.level().getGameTime() >= nextGun && bot.ammo() > 0;
    }

    /**
     * Elke tick (na zijn programma): richt hij op een plek, dan kijkt hij daarheen, en een wapen dat aan staat, schiet
     * zodra het klaar is.
     */
    void tick() {
        if (aim != null) {
            lookAtAim();
        }
        if (flipped == 0) {
            return; // alles staat zoals het begon: geen wapen aan
        }
        tryShoot(BotModule.LASER);
        tryShoot(BotModule.GUN);
    }

    /** Dit wapen schiet, als het aan staat, klaar is en een doel heeft (zie de uitleg bovenaan). */
    private void tryShoot(BotModule weapon) {
        boolean laser = weapon == BotModule.LASER;
        if (!moduleOn(weapon) || !(laser ? laserReady() : gunReady())) {
            return;
        }
        Player target = null;
        if (aim == null && moduleOn(BotModule.SCAN)) {
            List<Player> targets = targets();
            if (targets.isEmpty()) {
                return; // zijn Scan ziet niemand: hij wacht
            }
            target = targets.getFirst();
        }
        long next = bot.level().getGameTime() + fireInterval();
        BotShot.Weapon kind = laser ? BotShot.Weapon.LASER : BotShot.Weapon.GUN;
        if (laser) {
            nextLaser = next;
            bot.setBattery(bot.battery() - ImtConfig.Robots.LASER_ENERGY);
        } else {
            nextGun = next;
            bot.setAmmo(bot.ammo() - 1);
        }
        if (aim != null) {
            BotShot.fireAt(bot, kind, aim);
        } else {
            BotShot.fire(bot, kind, target);
        }
    }

    /**
     * Vertel de module op deze pin iets: true of false zet hem aan of uit, tekst is het filter van een Face Recognition.
     * Een wapen dat je aanzet en dat klaar is, schiet meteen (niet pas de volgende tick).
     */
    public void write(int pin, Object value) {
        BotModule module = bot.board().module(pin);
        if (module == null || module == BotModule.BURNT) {
            brain.report("pin " + pin + (module == null ? " is empty" : " (" + module.title() + ")") + ": write(...) does nothing there");
            return;
        }
        if (value instanceof Boolean on) {
            if (isOn(pin) != on) {
                flipped ^= 1 << pin;
            }
            if (on && (module == BotModule.LASER || module == BotModule.GUN)) {
                tryShoot(module);
            }
            return;
        }
        if (module == BotModule.FACE_RECOGNITION) {
            String text = Expr.text(value).strip();
            String lower = text.toLowerCase(Locale.ROOT);
            filter = List.of(EVERYONE, STRANGERS, TRUSTED, TRAINEE).contains(lower) ? lower : text;
            if (!isOn(pin)) {
                flipped ^= 1 << pin; // een filter kiezen zet hem ook aan
            }
            return;
        }
        brain.report("pin " + pin + " (" + module.title() + "): write(...) takes true or false here");
    }

    // ================================================================ Richten

    /** {@code aim(x, y, z)}: zijn wapens richten op deze plek. {@code aim()} (null): weer op wie zijn Scan ziet. */
    public void aim(@Nullable Vec3 place) {
        if (place == null && aim != null) {
            bot.setXRot(0); // weer recht vooruit; zijn lijf draait zichzelf terug (zie BotBody)
        }
        aim = place;
    }

    /** Waar hij op richt, of null. */
    public @Nullable Vec3 aimPlace() {
        return aim;
    }

    /**
     * Hij kijkt naar de plek waar hij op richt. Zijn hoofd altijd (dat is ook waar zijn Scan kijkt); zijn lijf alleen als
     * hij stilstaat, want lopen doet hij recht vooruit (zie BotBody.facing).
     */
    private void lookAtAim() {
        Vec3 eye = bot.getEyePosition();
        double dx = aim.x - eye.x;
        double dz = aim.z - eye.z;
        double flat = Math.sqrt(dx * dx + dz * dz);
        if (flat < 0.01) {
            return; // recht boven of onder hem
        }
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        bot.setYHeadRot(yaw);
        if (!brain.body().busy()) {
            bot.setYRot(yaw);
            bot.setYBodyRot(yaw);
        }
        bot.setXRot((float) -Math.toDegrees(Math.atan2(aim.y - eye.y, flat)));
    }

    /** Het filter van zijn Face Recognition. */
    public String filter() {
        return filter;
    }

    /** Opnieuw opstarten: het filter weer op iedereen, alle modules zoals ze beginnen, en niet meer richten op een plek. */
    void reset() {
        filter = EVERYONE;
        flipped = 0;
        aim(null);
    }

    // ================================================================ De camera

    /** Kan de app Cameras door zijn ogen kijken? (Een Camera-module, en zijn programma zette hem niet uit.) */
    public boolean cameraOn() {
        return moduleOn(BotModule.CAMERA);
    }

    /** Iemand kijkt nu door zijn camera (de app Cameras zegt het om de seconde); dat geldt 2 seconden. */
    public void watch() {
        watchedUntil = bot.level().getGameTime() + 40;
    }

    /** Kijkt er nu iemand door zijn camera? */
    public boolean watched() {
        return bot.level().getGameTime() < watchedUntil;
    }

    /**
     * Wie hij ziet en die door het filter komt, de dichtste eerst. Staat zijn Scan uit, dan niemand; staat zijn Face
     * Recognition uit, dan telt het filter niet.
     */
    List<Player> targets() {
        if (bot.board().has(BotModule.SCAN) && !moduleOn(BotModule.SCAN)) {
            return List.of();
        }
        if (filter.equals(EVERYONE) || !moduleOn(BotModule.FACE_RECOGNITION)) {
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

    /**
     * Komt deze speler door het filter? (Vriend of niet vraagt hij aan zijn server: zonder verbinding niemand.) Een
     * bot van niemand (Biebia's drones in een Biebia's Industries Tower, hoofdstuk 25) vertrouwt niemand: voor hem is iedereen een
     * vreemde, ook zonder verbinding.
     */
    private boolean matches(Player player, @Nullable SecurityCoreBlockEntity core) {
        return switch (filter) {
            case STRANGERS -> bot.owner() == null
                    || core != null && bot.level() instanceof ServerLevel level && !SecurityCores.isFriend(level, core, player);
            case TRUSTED -> bot.owner() != null
                    && core != null && bot.level() instanceof ServerLevel level && SecurityCores.isFriend(level, core, player);
            // De trainee van de training bij zijn kern (hoofdstuk 26): dat vraagt hij aan de server.
            case TRAINEE -> core != null && isTrainee(player, core);
            default -> player.getGameProfile().getName().equalsIgnoreCase(filter);
        };
    }

    /** Is deze speler de trainee van de training die nu bij deze kern bezig is? */
    private static boolean isTrainee(Player player, SecurityCoreBlockEntity core) {
        com.senne.imttech.training.Training.Session session = com.senne.imttech.training.Training.of(player.getUUID());
        return session != null && session.running() && session.core().pos().equals(core.getBlockPos())
                && core.getLevel() != null && session.core().dimension() == core.getLevel().dimension();
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
