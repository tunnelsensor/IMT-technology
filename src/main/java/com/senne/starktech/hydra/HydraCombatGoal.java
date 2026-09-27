package com.senne.starktech.hydra;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Hoe een Hydra-soldaat vecht. Vroeger liep hij gewoon op je af en schoot hij (zoals een skelet);
 * zat je achter een muur, dan bleef hij daar staan wachten. Nu werken ze samen, als een team:
 *
 * <ul>
 *   <li><b>Omsingelen.</b> De soldaten die op hetzelfde doelwit jagen, vormen een ploeg. Elke
 *       soldaat krijgt een eigen kant: de eerste blijft aan de voorkant, de anderen verdelen zich
 *       rond het doelwit, zodat je niet achter één muur tegen allemaal gedekt staat.</li>
 *   <li><b>Een schietplek zoeken.</b> Ziet hij je niet (muur, hoek, huis) of ben je te ver weg,
 *       dan zoekt hij een plek aan zijn kant van waaruit hij je WEL kan zien, waar hij echt kan
 *       komen, en niet op de plek van een maat. Die loopt hij op.</li>
 *   <li><b>Een weg naar binnen.</b> Is er nergens zo'n plek (je zit binnen), dan zoekt hij een weg
 *       naar je toe, en deuren doet hij gewoon open (zie HydraSoldierEntity).</li>
 *   <li><b>Elkaar waarschuwen.</b> Ziet één soldaat je, dan weten de soldaten in de buurt het
 *       ook, en ze kennen je laatste plek.</li>
 *   <li><b>Niet stilstaan.</b> Tijdens het schieten stapt hij af en toe opzij, en komt je te
 *       dichtbij, dan loopt hij wat achteruit.</li>
 * </ul>
 */
public class HydraCombatGoal extends Goal {
    /** Dichterbij dan dit komt hij niet vrijwillig (blokken). */
    private static final double MIN_RANGE = 6.0;
    /** Zo ver weg zoekt hij een schietplek (van het doelwit af). */
    private static final double[] POST_DISTANCES = {9.0, 13.0, 16.0};
    /** Zo vaak (ticks) kijkt hij opnieuw waar hij heen moet als hij je niet ziet. */
    private static final int REPLAN_TICKS = 25;
    /** Hoe ver de ploeg reikt: soldaten die zo dichtbij zijn en hetzelfde doelwit hebben. */
    private static final double SQUAD_RANGE = 32.0;
    /** Hoe ver een waarschuwing reikt. */
    private static final double ALERT_RANGE = 24.0;
    /** Twee schietplekken van maten liggen minstens zo ver uit elkaar. */
    private static final double SPREAD = 3.0;

    /** De gekozen schietplek van elke soldaat (zodat maten niet op dezelfde plek gaan staan). */
    private static final Map<HydraSoldierEntity, Vec3> POSTS = new WeakHashMap<>();

    private final HydraSoldierEntity soldier;
    private int seeTime;
    private int attackCooldown;
    private int replanCooldown;
    private int strafeTicks;
    private boolean strafeLeft;
    private @Nullable Vec3 post;
    private @Nullable Vec3 lastSeen;

    public HydraCombatGoal(HydraSoldierEntity soldier) {
        this.soldier = soldier;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = soldier.getTarget();
        return target != null && target.isAlive();
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
        attackCooldown = HydraBalance.FIRE_INTERVAL / 2 + soldier.getRandom().nextInt(10);
        replanCooldown = 0;
        alertSquad();
    }

    @Override
    public void stop() {
        post = null;
        POSTS.remove(soldier);
        seeTime = 0;
        soldier.getNavigation().stop();
    }

    @Override
    public void tick() {
        LivingEntity target = soldier.getTarget();
        if (target == null) {
            return;
        }
        boolean sees = soldier.getSensing().hasLineOfSight(target);
        if (sees) {
            seeTime++;
            lastSeen = target.position();
        } else {
            seeTime = 0;
        }
        double distance = soldier.distanceTo(target);
        boolean inRange = distance <= HydraBalance.ATTACK_RANGE;

        if (sees && inRange) {
            holdAndShoot(target, distance);
        } else {
            if (lastSeen != null) {
                soldier.getLookControl().setLookAt(lastSeen.x, lastSeen.y + 1.4, lastSeen.z);
            }
            if (--replanCooldown <= 0 || soldier.getNavigation().isDone()) {
                replanCooldown = REPLAN_TICKS;
                moveToBetterPlace(target);
            }
        }
    }

    // ================================================================ Schieten

    /** Hij ziet je en je bent dichtbij genoeg: blijf staan (of stap opzij) en schiet. */
    private void holdAndShoot(LivingEntity target, double distance) {
        soldier.getLookControl().setLookAt(target, 30.0f, 30.0f);
        // Op zijn schietplek (of hij had er geen): stoppen met lopen.
        if (post == null || soldier.position().distanceTo(post) < 1.5 || seeTime > 30) {
            soldier.getNavigation().stop();
            post = null;
            POSTS.remove(soldier);
        }
        if (distance < MIN_RANGE) {
            // Te dichtbij: een stap achteruit, weg van het doelwit.
            soldier.getMoveControl().strafe(-0.6f, 0.0f);
        } else if (soldier.getNavigation().isDone()) {
            // Af en toe opzij stappen, zodat hij geen stilstaand doelwit is.
            if (--strafeTicks <= 0) {
                strafeTicks = 20 + soldier.getRandom().nextInt(30);
                strafeLeft = soldier.getRandom().nextBoolean();
            }
            soldier.getMoveControl().strafe(0.0f, strafeTicks > 12 ? (strafeLeft ? 0.35f : -0.35f) : 0.0f);
        }
        if (--attackCooldown <= 0 && seeTime >= 5) {
            soldier.performRangedAttack(target, 1.0f);
            // Niet allemaal tegelijk: elke soldaat een beetje anders.
            attackCooldown = HydraBalance.FIRE_INTERVAL + soldier.getRandom().nextInt(12);
        }
    }

    // ================================================================ Een betere plek zoeken

    /**
     * Hij ziet je niet (of je bent te ver): zoek een schietplek aan zijn kant van waaruit hij je
     * wél ziet. Is die er niet, dan de kortste weg naar je toe (door deuren heen).
     */
    private void moveToBetterPlace(LivingEntity target) {
        Vec3 found = findPost(target);
        if (found != null) {
            post = found;
            POSTS.put(soldier, found);
            soldier.getNavigation().moveTo(found.x, found.y, found.z, 1.15);
            return;
        }
        post = null;
        POSTS.remove(soldier);
        // Geen plek met zicht: dan erheen, zo dichtbij als het kan. Kan hij er niet helemaal
        // komen, dan loopt hij in elk geval naar het dichtstbijzijnde punt (een deur, een gat).
        Path path = soldier.getNavigation().createPath(target, 1);
        if (path != null) {
            soldier.getNavigation().moveTo(path, 1.15);
        }
    }

    /**
     * Zoekt een schietplek: rond het doelwit, op "zijn" kant (zie sideAngle), op een paar afstanden
     * en hoeken. Een plek telt als hij er kan staan, het doelwit er kan zien, er niet al een maat
     * staat, en hij er echt kan komen. Van de goede plekken kiest hij de dichtstbijzijnde.
     */
    private @Nullable Vec3 findPost(LivingEntity target) {
        Level level = soldier.level();
        Vec3 eye = target.getEyePosition();
        double side = sideAngle(target);
        List<Vec3> candidates = new ArrayList<>();
        for (double distance : POST_DISTANCES) {
            for (int step : new int[] {0, 1, -1, 2, -2, 3, -3}) {
                double angle = side + step * Math.toRadians(30);
                double x = target.getX() + Math.cos(angle) * distance;
                double z = target.getZ() + Math.sin(angle) * distance;
                BlockPos stand = standingSpot(level, BlockPos.containing(x, target.getY(), z));
                if (stand == null) {
                    continue;
                }
                Vec3 spot = Vec3.atBottomCenterOf(stand);
                if (!canSee(level, spot.add(0, soldier.getEyeHeight(), 0), eye) || takenByMate(spot)) {
                    continue;
                }
                candidates.add(spot);
            }
        }
        // De dichtstbijzijnde eerst; hooguit een paar paden uitrekenen (dat is zwaar werk).
        candidates.sort(Comparator.comparingDouble(spot -> spot.distanceToSqr(soldier.position())));
        int tried = 0;
        for (Vec3 spot : candidates) {
            if (++tried > 6) {
                break;
            }
            Path path = soldier.getNavigation().createPath(BlockPos.containing(spot), 0);
            if (path != null && path.canReach()) {
                return spot;
            }
        }
        return null;
    }

    /**
     * Welke kant van het doelwit is van deze soldaat? De ploeg (soldaten met hetzelfde doelwit)
     * wordt op een vaste volgorde gezet; de eerste krijgt de kant waar hij nu staat, de anderen
     * verdelen zich gelijkmatig rondom.
     */
    private double sideAngle(LivingEntity target) {
        List<HydraSoldierEntity> squad = soldier.level().getEntitiesOfClass(HydraSoldierEntity.class,
                soldier.getBoundingBox().inflate(SQUAD_RANGE), mate -> mate.isAlive() && mate.getTarget() == target);
        if (!squad.contains(soldier)) {
            squad.add(soldier);
        }
        squad.sort(Comparator.comparing(HydraSoldierEntity::getUUID));
        HydraSoldierEntity leader = squad.getFirst();
        double base = Math.atan2(leader.getZ() - target.getZ(), leader.getX() - target.getX());
        int index = squad.indexOf(soldier);
        // 0, +1, -1, +2, -2...: om en om links en rechts van de leider.
        int slot = (index + 1) / 2 * (index % 2 == 1 ? 1 : -1);
        return base + slot * (2 * Math.PI / Math.max(3, squad.size()));
    }

    /** Staat er al een maat op (of vlak bij) deze plek? */
    private boolean takenByMate(Vec3 spot) {
        for (Map.Entry<HydraSoldierEntity, Vec3> entry : POSTS.entrySet()) {
            if (entry.getKey() != soldier && entry.getKey().isAlive() && entry.getValue().distanceTo(spot) < SPREAD) {
                return true;
            }
        }
        return false;
    }

    /** Een plek om te staan in de buurt van {@code around}: vaste grond, twee blokken lucht. */
    private static @Nullable BlockPos standingSpot(Level level, BlockPos around) {
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos feet = around.above(dy);
            if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                    && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                    && level.getFluidState(feet).isEmpty()) {
                return feet;
            }
        }
        return null;
    }

    /** Kan je van {@code from} naar {@code to} kijken zonder dat er een blok tussen zit? */
    private boolean canSee(Level level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, soldier))
                .getType() == HitResult.Type.MISS;
    }

    // ================================================================ Waarschuwen

    /** "Contact!": soldaten in de buurt die nog niets doen, krijgen hetzelfde doelwit. */
    private void alertSquad() {
        LivingEntity target = soldier.getTarget();
        if (target == null) {
            return;
        }
        for (HydraSoldierEntity mate : soldier.level().getEntitiesOfClass(HydraSoldierEntity.class,
                soldier.getBoundingBox().inflate(ALERT_RANGE), mate -> mate != soldier && mate.getTarget() == null)) {
            mate.setTarget(target);
        }
    }
}
