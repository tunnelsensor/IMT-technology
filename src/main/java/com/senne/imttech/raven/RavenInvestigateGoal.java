package com.senne.imttech.raven;

import com.senne.imttech.config.ImtConfig;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Een achterdochtige of zoekende Raven-soldaat (hoofdstuk 60, zoals in Hitman). Hij loopt naar de plek die zijn
 * zintuigen hem geven ({@link RavenSenses#lookAt()}): waar hij iets hoorde, waar de speler stond die zijn meter halfvol
 * maakte, of waar hij je kwijtraakte. Daar kijkt hij {@code [guards] lookSeconds} lang om zich heen (elke seconde een
 * andere kant op). Dan:
 * <ul>
 *   <li><b>achterdochtig</b>: hij vraagt zijn zintuigen of het klaar is ({@link RavenSenses#doneLooking()}): ziet hij nog
 *       steeds iemand, dan gaat hij daar kijken, anders is hij weer rustig en loopt hij zijn route verder;</li>
 *   <li><b>zoekend</b>: hij kiest een nieuwe bereikbare plek binnen {@code searchRadius} van je laatste plek (ook door
 *       deuren: die doet hij open) en kijkt daar, tot zijn zoektijd om is.</li>
 * </ul>
 * Na een half radiogesprek van een maat (stap 4) komt hij op zijn gemak ({@link RavenSenses#slow()}); zoekend loopt hij
 * juist stevig door.
 */
public class RavenInvestigateGoal extends Goal {
    /** Om de zoveel ticks kijkt hij een andere kant op. */
    private static final int TURN_TICKS = 20;
    /** Zo vaak probeert hij opnieuw een pad als hij nog niet aankwam. */
    private static final int REPATH_TICKS = 30;
    /** Zoveel plekken probeert hij voor een nieuwe zoekplek. */
    private static final int SEARCH_TRIES = 10;

    private final RavenSoldierEntity soldier;
    private @Nullable Vec3 destination;
    /** -1 = nog onderweg; anders hoe lang hij al rondkijkt (ticks). */
    private int looking = -1;
    private int repath;

    public RavenInvestigateGoal(RavenSoldierEntity soldier) {
        this.soldier = soldier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        RavenSenses senses = soldier.senses();
        RavenAlert state = senses.state();
        return (state == RavenAlert.SUSPICIOUS || state == RavenAlert.SEARCHING) && senses.lookAt() != null
                && soldier.getTarget() == null && !com.senne.imttech.cuffs.Handcuffs.isCuffed(soldier);
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        destination = null;
        looking = -1;
        repath = 0;
    }

    @Override
    public void stop() {
        destination = null;
        looking = -1;
        soldier.getNavigation().stop();
    }

    @Override
    public void tick() {
        RavenSenses senses = soldier.senses();
        Vec3 want = senses.lookAt();
        if (want == null) {
            return;
        }
        // Een nieuwe plek (of, achterdochtig, de speler die een eind verder liep): daarheen.
        if (destination == null || (senses.state() == RavenAlert.SUSPICIOUS && want.distanceTo(destination) > 2.0)) {
            destination = want;
            looking = -1;
            walkTo(destination);
        }
        if (looking < 0) {
            boolean arrived = soldier.position().distanceTo(destination) < 1.6 || soldier.getNavigation().isDone();
            if (arrived) {
                looking = 0;
                soldier.getNavigation().stop();
            } else if (--repath <= 0) {
                repath = REPATH_TICKS;
                walkTo(destination);
            }
            return;
        }
        // Rondkijken: om de seconde een andere kant op.
        if (looking % TURN_TICKS == 0) {
            double yaw = soldier.getRandom().nextDouble() * Math.PI * 2;
            soldier.getLookControl().setLookAt(soldier.getX() + Math.cos(yaw) * 4, soldier.getEyeY(), soldier.getZ() + Math.sin(yaw) * 4);
        }
        looking++;
        if (looking < Math.round(ImtConfig.Guards.LOOK_SECONDS * 20)) {
            return;
        }
        if (senses.state() == RavenAlert.SEARCHING) {
            Vec3 spot = searchSpot(senses.lookAt());
            if (spot != null) {
                destination = spot;
                looking = -1;
                walkTo(spot);
            } else {
                looking = 0;
            }
        } else {
            // Achterdochtig: klaar met kijken. Zijn zintuigen zeggen of hij weer rustig is, of waar hij nu heen moet.
            senses.doneLooking();
            destination = null;
            looking = -1;
        }
    }

    /** Erheen: zoekend stevig door, na een half radiogesprek op zijn gemak, anders gewoon. */
    private void walkTo(Vec3 spot) {
        RavenSenses senses = soldier.senses();
        double speed = senses.state() == RavenAlert.SEARCHING ? 1.0 : senses.slow() ? 0.6 : 0.9;
        soldier.getNavigation().moveTo(spot.x, spot.y, spot.z, speed);
    }

    /** Een nieuwe plek om te zoeken: bereikbaar, binnen {@code searchRadius} van het midden, niet waar hij nu staat. */
    private @Nullable Vec3 searchSpot(Vec3 centre) {
        double radius = ImtConfig.Guards.SEARCH_RADIUS;
        for (int tried = 0; tried < SEARCH_TRIES; tried++) {
            double angle = soldier.getRandom().nextDouble() * Math.PI * 2;
            double distance = 2 + soldier.getRandom().nextDouble() * (radius - 2);
            BlockPos around = BlockPos.containing(centre.x + Math.cos(angle) * distance, centre.y, centre.z + Math.sin(angle) * distance);
            BlockPos stand = RavenCombatGoal.standingSpot(soldier.level(), around);
            if (stand == null || stand.distSqr(soldier.blockPosition()) < 4) {
                continue;
            }
            Path path = soldier.getNavigation().createPath(stand, 0);
            if (path != null && path.canReach()) {
                return Vec3.atBottomCenterOf(stand);
            }
        }
        return null;
    }
}
