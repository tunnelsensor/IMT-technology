package com.senne.starktech.bot;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.robot.GuardRobotEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Het lijf van een robot (hoofdstuk 23): hoe zijn programma hem laat bewegen. Altijd één beweging tegelijk
 * (een {@link Plan}); zijn programma wacht tot die klaar is.
 * <ul>
 *   <li><b>Walk</b> ({@code forward(3)}, {@code back(1)}): blok voor blok, precies van het midden van het ene
 *       blok naar het midden van het volgende. Hij stapt één blok op of af; een muur, een gat of iets dat hem
 *       langer dan {@code STUCK_SECONDS} tegenhoudt, en hij stopt: dan is {@code robot.blocked} waar en gaat
 *       hij terug naar het midden van het blok waar hij was. Achteruit blijft hij dezelfde kant op kijken.</li>
 *   <li><b>Turn</b> ({@code turn_left()}, {@code turn_right()}): een kwartslag, in een paar ticks.</li>
 *   <li><b>GoTo</b> ({@code goto(x, y, z)}): met de padzoeker van Minecraft naar die plek (de Pathfinding-module
 *       kijkt het brein na), het laatste stukje weer precies naar het midden.</li>
 *   <li><b>Charge</b> ({@code charge(80)}): naar zijn dock (zoals als hij uitgezet wordt), laden tot zoveel
 *       procent, het dock uit, en terug naar waar hij was, met zijn gezicht weer dezelfde kant op.</li>
 * </ul>
 * Hij kijkt altijd recht een windrichting op ({@link #facing}): dat is waar "vooruit" heen gaat.
 */
public final class BotMove {
    /** Wat zijn programma van zijn lijf vraagt. */
    public sealed interface Plan permits Walk, Turn, GoTo, Charge {}

    /** {@code forward(n)} (blocks positief) of {@code back(n)} (negatief). */
    public record Walk(int blocks) implements Plan {}

    /** Een kwartslag naar rechts ({@code right}) of links. */
    public record Turn(boolean right) implements Plan {}

    /** Naar deze plek lopen (de voeten op dit blok). */
    public record GoTo(BlockPos target) implements Plan {}

    /** Naar het dock, laden tot {@code percent} procent, en terug. */
    public record Charge(double percent) implements Plan {}

    /** Zo dicht (blokken) bij het midden van een blok telt als "aangekomen". */
    private static final double ARRIVED = 0.12;
    /** Zolang mag laden duren zonder dat de batterij voller wordt (geen stroom op het dock?): dan geeft hij het op. */
    private static final int CHARGE_STALL_TICKS = 200;

    private final GuardRobotEntity robot;
    private @Nullable Direction facing;
    private boolean blocked;
    private @Nullable Plan plan;
    /** Is de beweging al klaar (meteen geblokkeerd)? Dan zegt de volgende tick dat hij klaar is. */
    private boolean finished;
    private int ticks;

    // Lopen: nog zoveel blokken, het blok waar hij vandaan komt en waar hij heen gaat.
    private int stepsLeft;
    private @Nullable BlockPos from;
    private @Nullable BlockPos target;
    private boolean retreating;
    private int stepTicks;
    /** Vroegen we de padzoeker al om de weg (goto, en terug na het laden)? */
    private boolean pathAsked;

    // Draaien: van welke hoek naar welke.
    private float turnFrom;
    private float turnTo;

    // Laden: waar hij was, en hoe ver hij is.
    private enum Phase { TO_DOCK, CHARGING, LEAVE, BACK, FINAL }

    private Phase phase = Phase.TO_DOCK;
    private @Nullable BlockPos returnPos;
    private @Nullable Direction returnFacing;
    private double lastBattery;
    private int stallTicks;
    private boolean wantsDock;

    public BotMove(GuardRobotEntity robot) {
        this.robot = robot;
    }

    // ================================================================ Wat het brein vraagt

    /** De kant waar hij naar kijkt (en waar vooruit heen gaat). Nog niet bekend: de windrichting van zijn gezicht. */
    public Direction facing() {
        if (facing == null) {
            facing = Direction.fromYRot(robot.getYRot());
        }
        return facing;
    }

    public void setFacing(@Nullable Direction facing) {
        this.facing = facing == null || facing.getAxis().isVertical() ? null : facing;
    }

    /** Stond er bij zijn laatste beweging iets in de weg? ({@code robot.blocked}) */
    public boolean blocked() {
        return blocked;
    }

    /** Iets lukte niet zonder dat hij bewoog (goto zonder Pathfinding-module, charge zonder dock): geblokkeerd. */
    public void markBlocked() {
        blocked = true;
    }

    /** Is hij met {@code goto} onderweg? (Wat de Pathfinding-module zegt.) */
    public boolean goingSomewhere() {
        return plan instanceof GoTo && !finished;
    }

    /** Is hij met een beweging bezig? */
    public boolean busy() {
        return plan != null;
    }

    /** Wil hij nu naar zijn dock (om te laden)? Dan loopt hij erheen zoals wanneer hij uitgezet wordt. */
    public boolean wantsDock() {
        return wantsDock;
    }

    /** Begin aan deze beweging. */
    public void start(Plan plan) {
        stop();
        this.plan = plan;
        blocked = false;
        finished = false;
        ticks = 0;
        switch (plan) {
            case Walk walk -> {
                stepsLeft = Math.abs(walk.blocks());
                nextStep();
            }
            case Turn turn -> {
                turnFrom = facing().toYRot();
                facing = turn.right() ? facing().getClockWise() : facing().getCounterClockWise();
                turnTo = turnFrom + (turn.right() ? 90 : -90);
            }
            case GoTo goTo -> pathAsked = false; // de weg vragen we als hij op de grond staat (anders vindt de padzoeker niets)
            case Charge charge -> {
                returnPos = robot.blockPosition();
                returnFacing = facing();
                lastBattery = robot.battery();
                stallTicks = 0;
                phase = Phase.TO_DOCK;
                // Al vol genoeg? Dan hoeft hij niet te lopen.
                wantsDock = robot.battery() < robot.maxBattery() * charge.percent() / 100.0 - 1.0e-6;
                finished = !wantsDock;
            }
        }
    }

    /**
     * Een opdracht van de server onderbreekt deze beweging (hoofdstuk 23). Geeft terug wat er nog over is, om na de
     * opdracht verder te doen (of null): bij lopen de blokken die nog over zijn, bij goto en charge dezelfde
     * opdracht opnieuw. Een draai is meteen af.
     */
    public @Nullable Plan interrupt() {
        Plan rest = switch (plan) {
            case null -> null;
            case Walk walk -> retreating || finished || stepsLeft <= 0 ? null : new Walk(walk.blocks() < 0 ? -stepsLeft : stepsLeft);
            case Turn turn -> null;
            case GoTo goTo -> finished ? null : goTo;
            case Charge charge -> finished ? null : charge;
        };
        if (plan instanceof Turn) {
            setYaw(facing().toYRot()); // de draai meteen af
        }
        stop();
        return rest;
    }

    /** Stop meteen met wat hij deed (uitgezet, of een nieuwe beweging). */
    public void stop() {
        if (plan instanceof GoTo || plan instanceof Charge) {
            robot.getNavigation().stop();
        }
        plan = null;
        target = null;
        wantsDock = false;
    }

    /**
     * Elke tick terwijl zijn programma draait. Geeft true op de tick dat de beweging klaar is (dan gaat zijn
     * programma verder).
     */
    public boolean tick(ServerLevel level) {
        if (plan == null) {
            keepFacing();
            return false;
        }
        ticks++;
        boolean done = finished || switch (plan) {
            case Walk walk -> tickWalk(level, walk.blocks() < 0);
            case Turn turn -> tickTurn();
            case GoTo goTo -> tickGoTo(level, goTo.target());
            case Charge charge -> tickCharge(level, charge.percent());
        };
        if (done) {
            plan = null;
            wantsDock = false;
            keepFacing();
        }
        return done;
    }

    // ================================================================ Lopen

    /** Het volgende blok: kan hij daar staan (gelijk, één op of één af)? Anders is hij geblokkeerd. */
    private void nextStep() {
        if (stepsLeft <= 0) {
            target = null;
            return;
        }
        Direction way = plan instanceof Walk walk && walk.blocks() < 0 ? facing().getOpposite() : facing();
        from = robot.blockPosition();
        target = standOn(from.relative(way));
        retreating = false;
        stepTicks = 0;
        if (target == null) {
            blocked = true;
            stepsLeft = 0;
        }
    }

    /**
     * Waar zijn voeten komen als hij naar dit blok stapt: hetzelfde blok, één hoger (opstappen) of één lager
     * (afstappen); null als hij daar niet past of als er een gat is.
     */
    private @Nullable BlockPos standOn(BlockPos next) {
        if (fits(next) && (hasGround(next) || fits(next.below()) && hasGround(next.below()))) {
            return hasGround(next) ? next : next.below();
        }
        // Opstappen: boven hem en boven het volgende blok moet plek zijn.
        if (fits(next.above()) && hasGround(next.above()) && fits(robot.blockPosition().above())) {
            return next.above();
        }
        return null;
    }

    /** Past hij (zijn hele lijf) op dit blok, zonder ergens tegenaan te zitten? */
    private boolean fits(BlockPos feet) {
        AABB box = robot.getDimensions(robot.getPose()).makeBoundingBox(feet.getX() + 0.5, feet.getY() + floorOf(feet), feet.getZ() + 0.5)
                .deflate(0.02);
        return robot.level().noCollision(robot, box);
    }

    /** Hoe hoog de vloer in dit blok zelf is (een plaat of het dock: een stukje; meestal 0). */
    private double floorOf(BlockPos feet) {
        double top = robot.level().getBlockState(feet).getCollisionShape(robot.level(), feet).max(Direction.Axis.Y);
        return top > 0 && top <= 0.5 ? top : 0;
    }

    /** Staat er iets onder dit blok (of is het blok zelf een lage vloer, zoals een plaat)? */
    private boolean hasGround(BlockPos feet) {
        if (floorOf(feet) > 0) {
            return true;
        }
        BlockPos below = feet.below();
        return !robot.level().getBlockState(below).getCollisionShape(robot.level(), below).isEmpty();
    }

    private boolean tickWalk(ServerLevel level, boolean backwards) {
        if (target == null) {
            return true;
        }
        stepTicks++;
        keepFacing();
        if (approach(target)) {
            if (retreating) {
                target = null;
                return true;
            }
            stepsLeft--;
            nextStep();
            return target == null;
        }
        if (stepTicks > Math.max(5, Math.round(StarkConfig.Robots.STUCK_SECONDS * 20))) {
            if (retreating || from == null) {
                target = null; // ook terug lukt niet: dan blijft hij staan waar hij is
                return true;
            }
            // Iets houdt hem tegen: geblokkeerd, en terug naar het midden van het blok waar hij was.
            blocked = true;
            stepsLeft = 0;
            target = from;
            retreating = true;
            stepTicks = 0;
        }
        return false;
    }

    /**
     * Eén tick richting het midden van dit blok (zelf geduwd, zonder de padzoeker, zodat hij precies loopt).
     * Geeft true als hij er is: dan staat hij precies in het midden.
     */
    private boolean approach(BlockPos block) {
        double cx = block.getX() + 0.5;
        double cz = block.getZ() + 0.5;
        double dx = cx - robot.getX();
        double dz = cz - robot.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < ARRIVED && robot.onGround()) {
            robot.setPos(cx, robot.getY(), cz);
            robot.setDeltaMovement(0, robot.getDeltaMovement().y, 0);
            return true;
        }
        double speed = Math.min(distance, robot.getAttributeValue(Attributes.MOVEMENT_SPEED) * StarkConfig.Robots.WALK_FACTOR);
        Vec3 motion = robot.getDeltaMovement();
        robot.setDeltaMovement(distance < 1.0e-4 ? 0 : dx / distance * speed, motion.y, distance < 1.0e-4 ? 0 : dz / distance * speed);
        return false;
    }

    // ================================================================ Draaien

    private boolean tickTurn() {
        int total = Math.max(1, Math.round(StarkConfig.Robots.TURN_SECONDS * 20));
        float t = Math.min(1, ticks / (float) total);
        setYaw(Mth.lerp(t, turnFrom, turnTo));
        return ticks >= total;
    }

    // ================================================================ Naar een plek (goto)

    private boolean tickGoTo(ServerLevel level, BlockPos goal) {
        if (target != null) {
            // Het laatste stukje: precies naar het midden.
            if (approach(target) || ticks > maxGoToTicks()) {
                facing = Direction.fromYRot(robot.getYRot());
                return true;
            }
            return false;
        }
        if (ticks > maxGoToTicks()) {
            finishBlocked();
            return true;
        }
        // Voor de padzoeker is een blok ernaast al "aangekomen": het laatste stukje lopen we zelf (hierboven).
        if (closeTo(goal, 1.6) && robot.getNavigation().isDone()) {
            target = goal;
        } else if (robot.getNavigation().isDone() && robot.onGround() && (!pathAsked || ticks % 20 == 0)) {
            pathAsked = true;
            if (!robot.getNavigation().moveTo(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5, 1.0)) {
                finishBlocked(); // geen weg te vinden
                return true;
            }
        }
        return false;
    }

    private int maxGoToTicks() {
        return Math.max(20, Math.round(StarkConfig.Robots.GOTO_SECONDS * 20));
    }

    /** Staat hij (plat gemeten, en ongeveer op dezelfde hoogte) zo dicht bij het midden van dit blok? */
    private boolean closeTo(BlockPos block, double range) {
        double dx = robot.getX() - (block.getX() + 0.5);
        double dz = robot.getZ() - (block.getZ() + 0.5);
        return dx * dx + dz * dz <= range * range && Math.abs(robot.getY() - block.getY()) < 1.5;
    }

    // ================================================================ Laden (charge)

    private boolean tickCharge(ServerLevel level, double percent) {
        BlockPos home = robot.home();
        switch (phase) {
            case TO_DOCK -> {
                // Lopen doet hij zoals wanneer hij uitgezet wordt (ReturnToDockGoal); in zijn dock gaat hij netjes staan.
                if (home == null || ticks > maxGoToTicks()) {
                    blocked = true;
                    wantsDock = false;
                    phase = Phase.BACK;
                    ticks = 0;
                    pathAsked = false;
                } else if (robot.isOnDock() && robot.isSettled()) {
                    phase = Phase.CHARGING;
                    lastBattery = robot.battery();
                    stallTicks = 0;
                }
            }
            case CHARGING -> {
                if (robot.battery() >= robot.maxBattery() * percent / 100.0 - 1.0e-6) {
                    leaveDock();
                } else if (robot.battery() > lastBattery) {
                    lastBattery = robot.battery();
                    stallTicks = 0;
                } else if (++stallTicks > CHARGE_STALL_TICKS) {
                    blocked = true; // het dock geeft geen stroom
                    leaveDock();
                }
            }
            case LEAVE -> {
                if (!robot.isSteppingOut()) {
                    phase = Phase.BACK;
                    ticks = 0;
                    pathAsked = false;
                }
            }
            case BACK -> {
                if (returnPos == null || closeTo(returnPos, 1.6)) {
                    phase = Phase.FINAL;
                } else if (ticks > maxGoToTicks()) {
                    blocked = true;
                    return true;
                } else if (robot.getNavigation().isDone() && robot.onGround() && (!pathAsked || ticks % 20 == 0)) {
                    pathAsked = true;
                    if (!robot.getNavigation().moveTo(returnPos.getX() + 0.5, returnPos.getY(), returnPos.getZ() + 0.5, 1.0)) {
                        blocked = true; // geen weg terug
                        return true;
                    }
                }
            }
            case FINAL -> {
                robot.getNavigation().stop();
                if (returnPos == null || approach(returnPos) || ticks > maxGoToTicks()) {
                    facing = returnFacing;
                    return true;
                }
            }
        }
        return false;
    }

    /** Klaar met laden: het dock uit (als hij niet in het dock zelf moest blijven). */
    private void leaveDock() {
        wantsDock = false;
        if (returnPos != null && returnPos.equals(robot.home())) {
            returnPos = null; // hij laadde vanuit zijn dock: daar blijft hij
            phase = Phase.FINAL;
            return;
        }
        robot.stepOutOfDock();
        phase = Phase.LEAVE;
    }

    // ================================================================ Hulpjes

    /** Het lukt niet (geen weg, te lang onderweg): geblokkeerd, en klaar. */
    private void finishBlocked() {
        blocked = true;
        robot.getNavigation().stop();
        target = null;
        finished = true;
    }

    /** Zijn gezicht (en lijf) precies naar zijn windrichting. */
    private void keepFacing() {
        if (plan instanceof GoTo || plan instanceof Charge && phase != Phase.FINAL) {
            return; // dan kijkt hij waar hij heen loopt
        }
        setYaw(facing().toYRot());
    }

    private void setYaw(float yaw) {
        robot.setYRot(yaw);
        robot.setYHeadRot(yaw);
        robot.setYBodyRot(yaw);
    }
}
