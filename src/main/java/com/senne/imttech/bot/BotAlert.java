package com.senne.imttech.bot;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.logic.program.Op;
import com.senne.imttech.raven.Bodies;
import com.senne.imttech.raven.Noises;
import com.senne.imttech.raven.RavenAlert;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * De meter van een robot of drone (hoofdstuk 60, stap 6, jouw antwoord 7: dezelfde nieuwe woorden voor je eigen robots).
 * Net als bij een Raven-soldaat (RavenSenses) loopt een meter (0-100, {@code robot.alert} / {@code drone.alert}) vol
 * zolang zijn Scan iemand ziet die door het filter van zijn Face Recognition komt: sneller dichtbij of rennend, trager
 * ver weg of sluipend (dezelfde getallen uit {@code [guards]}, met zijn eigen Scan-bereik als afstand). Is hij vol, dan
 * begint {@code when spotted:} één keer (met target.name en target.x/y/z), tot de meter weer leeg was. Verder:
 * <ul>
 *   <li>{@code when heard:} bij elk geluid binnen zijn bereik (Noises; sound.x/y/z en sound.kind);</li>
 *   <li>{@code when body_seen:} één keer per lichaam dat hij ziet (Bodies; body.x/y/z).</li>
 * </ul>
 * {@code read(scanPin)} blijft meteen waar: oude programma's veranderen niet. Boven zijn hoofd staat op je bril hetzelfde
 * "?" of "!" als bij een soldaat, en ook een robot staat in het oogje (zie Watcher in BotEntity en hud/DetectionWidget).
 */
public final class BotAlert {
    private final BotEntity bot;
    private float level;
    private @Nullable Player suspect;
    /** {@code when spotted:} is al begonnen voor deze keer dat hij iemand ziet. */
    private boolean spottedFired;
    /** De lichamen die hij al meldde. */
    private final Set<UUID> seenBodies = new HashSet<>();

    BotAlert(BotEntity bot) {
        this.bot = bot;
    }

    /** Zijn meter (0-100). */
    public double level() {
        return level;
    }

    public RavenAlert state() {
        return level >= 100.0f ? RavenAlert.COMBAT : level >= ImtConfig.Guards.SUSPICIOUS_AT ? RavenAlert.SUSPICIOUS : RavenAlert.CALM;
    }

    /** Elke tick terwijl hij aan staat, stroom heeft en zijn programma draait. */
    void tick(ServerLevel level) {
        List<Player> targets = bot.brain().pins().targets();
        if (targets.isEmpty()) {
            decay();
        } else {
            Player player = targets.getFirst();
            suspect = player;
            this.level = Math.min(100.0f, this.level + (float) rate(player));
            if (this.level >= 100.0f && !spottedFired) {
                spottedFired = true;
                bot.brain().event(Op.SPOTTED, player.blockPosition(), Map.of(
                        "target.name", player.getName().getString(),
                        "target.x", (double) player.blockPosition().getX(),
                        "target.y", (double) player.blockPosition().getY(),
                        "target.z", (double) player.blockPosition().getZ()));
            }
        }
        if (bot.tickCount % 10 == 0 && bot.brain().hasTrigger(Op.BODY_SEEN)) {
            Entity body = Bodies.visible(bot, bot.scanRange(), bot.scanAngle() / 2.0, b -> seenBodies.contains(b.getUUID()));
            if (body != null && seenBodies.add(body.getUUID())) {
                BlockPos at = body.blockPosition();
                bot.brain().event(Op.BODY_SEEN, at, Map.of("body.x", (double) at.getX(), "body.y", (double) at.getY(), "body.z", (double) at.getZ()));
            }
        }
        sync();
    }

    /** Uit, leeg, gevloerd of geboeid: de meter zakt. */
    void idle() {
        decay();
        sync();
    }

    private void decay() {
        if (level <= 0) {
            return;
        }
        level = Math.max(0.0f, level - 100.0f / (Math.max(0.5f, ImtConfig.Guards.DECAY_SECONDS) * 20.0f));
        if (level <= 0) {
            suspect = null;
            spottedFired = false;
        }
    }

    /** Hoeveel de meter per tick stijgt voor deze speler (zoals RavenSenses.rate, met zijn Scan-bereik als afstand). */
    private double rate(Player player) {
        double range = Math.max(1.0, bot.scanRange());
        double seconds = Mth.lerp(Mth.clamp(bot.distanceTo(player) / range, 0.0, 1.0),
                ImtConfig.Guards.SPOT_SECONDS_NEAR, ImtConfig.Guards.SPOT_SECONDS_FAR);
        double rate = 100.0 / (Math.max(0.1, seconds) * 20.0);
        if (player.isSprinting()) {
            rate *= ImtConfig.Guards.SPRINT_FACTOR;
        } else if (player.isShiftKeyDown() || player.isCrouching()) {
            rate *= ImtConfig.Guards.SNEAK_FACTOR;
        }
        return rate;
    }

    /** Lawaai (Noises): {@code when heard:} met de plek (als blok, zoals robot.x) en de soort. */
    public void hear(Vec3 at, Noises.Kind kind) {
        BlockPos block = BlockPos.containing(at);
        bot.brain().event(Op.HEARD, block, Map.of("sound.x", (double) block.getX(), "sound.y", (double) block.getY(),
                "sound.z", (double) block.getZ(), "sound.kind", kind.word()));
    }

    /** Opnieuw opstarten (uit en aan, een nieuw programma): alles vergeten. */
    void reset() {
        level = 0;
        suspect = null;
        spottedFired = false;
        seenBodies.clear();
        sync();
    }

    private void sync() {
        bot.syncAlert(state(), Math.round(level), suspect == null || level <= 0 ? -1 : suspect.getId());
    }
}
