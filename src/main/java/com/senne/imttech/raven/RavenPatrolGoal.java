package com.senne.imttech.raven;

import com.senne.imttech.config.ImtConfig;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Een rustige Raven-soldaat loopt zijn route (hoofdstuk 60, jouw antwoord 1). Vroeger slenterde hij maar wat rond
 * (WaterAvoidingRandomStrollGoal); nu:
 * <ul>
 *   <li>heeft hij een <b>route</b> (de plekken uit zijn markering, zie RavenRoutes), dan loopt hij die rond op zijn
 *       gemak ({@code [guards] patrolSpeed}) en blijft hij bij elk punt {@code patrolWaitMin} tot {@code patrolWaitMax}
 *       seconden staan om rond te kijken (RandomLookAroundGoal draait zijn hoofd, want dit doel houdt alleen zijn benen
 *       bezig). Na een gevecht of een zoektocht pakt hij de draad op bij het punt dat het dichtst bij is;</li>
 *   <li>heeft hij <b>geen route</b>, dan heeft hij een <b>post</b> (waar hij verscheen): daar staat hij, en is hij er
 *       vandaan geraakt, dan loopt hij terug.</li>
 * </ul>
 * Een punt waar hij niet kan komen (een gesloten hek, een kist ervoor), slaat hij na een paar pogingen over.
 */
public class RavenPatrolGoal extends Goal {
    /** Zo vaak (ticks) probeert hij opnieuw als hij niet aankomt... */
    private static final int REPATH_TICKS = 40;
    /** ...en na zoveel pogingen slaat hij het punt over. */
    private static final int MAX_TRIES = 3;
    /** Zo dichtbij telt als aangekomen. */
    private static final double ARRIVED = 1.4;

    private final RavenSoldierEntity soldier;
    private int next;
    /** Hoeveel ticks hij nog bij een punt blijft staan (0 = onderweg). */
    private int waiting;
    private int repath;
    private int tries;
    private @Nullable BlockPos destination;

    public RavenPatrolGoal(RavenSoldierEntity soldier) {
        this.soldier = soldier;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (soldier.senses().state() != RavenAlert.CALM || soldier.getTarget() != null
                || com.senne.imttech.cuffs.Handcuffs.isCuffed(soldier)) {
            return false;
        }
        if (!soldier.route().isEmpty()) {
            return true;
        }
        BlockPos post = soldier.post();
        return post != null && !nearPost(post);
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
        waiting = 0;
        tries = 0;
        next = nearestPoint();
    }

    @Override
    public void stop() {
        destination = null;
        soldier.getNavigation().stop();
    }

    @Override
    public void tick() {
        List<BlockPos> route = soldier.route();
        if (route.isEmpty()) {
            // Geen route: terug naar zijn post.
            BlockPos post = soldier.post();
            if (post == null) {
                return;
            }
            if (destination == null || --repath <= 0) {
                destination = post;
                repath = REPATH_TICKS;
                walkTo(post);
            }
            return;
        }
        if (waiting > 0) {
            if (--waiting == 0) {
                next = (next + 1) % route.size();
                destination = null;
                tries = 0;
            }
            return;
        }
        if (next >= route.size()) {
            next = 0;
        }
        BlockPos point = route.get(next);
        if (destination == null) {
            destination = point;
            repath = REPATH_TICKS;
            walkTo(point);
        }
        if (soldier.position().distanceTo(Vec3.atBottomCenterOf(point)) < ARRIVED) {
            soldier.getNavigation().stop();
            waiting = waitTicks();
            return;
        }
        if (soldier.getNavigation().isDone() && --repath <= 0) {
            if (++tries >= MAX_TRIES) {
                // Hij komt er niet: het volgende punt dan maar.
                next = (next + 1) % route.size();
                destination = null;
                tries = 0;
                return;
            }
            repath = REPATH_TICKS;
            walkTo(point);
        }
    }

    private void walkTo(BlockPos point) {
        soldier.getNavigation().moveTo(point.getX() + 0.5, point.getY(), point.getZ() + 0.5, ImtConfig.Guards.PATROL_SPEED);
    }

    /** Hoe lang hij bij een punt blijft staan (ticks): ergens tussen de twee tijden uit de config. */
    private int waitTicks() {
        float min = Math.max(0.0f, ImtConfig.Guards.PATROL_WAIT_MIN);
        float max = Math.max(min, ImtConfig.Guards.PATROL_WAIT_MAX);
        return Math.max(1, Math.round((min + soldier.getRandom().nextFloat() * (max - min)) * 20));
    }

    /** Het punt van zijn route dat het dichtst bij is (na een gevecht pakt hij de draad daar op). */
    private int nearestPoint() {
        List<BlockPos> route = soldier.route();
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < route.size(); i++) {
            double distance = route.get(i).distSqr(soldier.blockPosition());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private boolean nearPost(BlockPos post) {
        return soldier.position().distanceTo(Vec3.atBottomCenterOf(post)) < 1.5;
    }
}
