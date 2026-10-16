package com.senne.starktech.bot;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.robot.GuardDroneEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Het lijf van een security drone (hoofdstuk 25): hoe zijn programma hem laat vliegen. Net als bij een robot
 * (BotMove) altijd één beweging tegelijk ({@link BotMove.Plan}); zijn programma wacht tot die klaar is.
 * <ul>
 *   <li><b>Walk</b> ({@code forward(3)}, {@code back(1)}): blok voor blok vooruit of achteruit, op dezelfde
 *       hoogte, precies van het midden van het ene blok naar het midden van het volgende. Past hij niet in het
 *       volgende blok, of houdt iets hem langer dan {@code STUCK_SECONDS} tegen, dan stopt hij: dan is
 *       {@code drone.blocked} waar en gaat hij terug naar het midden van het blok waar hij was.</li>
 *   <li><b>Climb</b> ({@code up(2)}, {@code down(1)}): hetzelfde, maar recht omhoog of omlaag: niet door een
 *       plafond, en niet door de grond.</li>
 *   <li><b>Turn</b> ({@code turn_left()}, {@code turn_right()}): een kwartslag, in een paar ticks.</li>
 *   <li><b>GoTo</b> ({@code goto(x, y, z)}): met de padzoeker voor vliegers naar die plek (de Pathfinding-module
 *       kijkt het brein na), om muren heen, het laatste stukje precies naar het midden.</li>
 *   <li><b>Charge</b> ({@code charge(80)}): naar zijn Drone Dock vliegen en erop landen (zoals als hij uitgezet
 *       wordt), laden tot zoveel procent, opstijgen, en terug naar waar hij was.</li>
 * </ul>
 * Hij hangt altijd in het midden van een blok, een stukje boven de bodem ervan (zie GuardDroneEntity.hoverSpot),
 * en kijkt recht een windrichting op ({@link #facing}): dat is waar "vooruit" heen gaat.
 */
public final class DroneMove implements BotBody {
    /** Zolang mag laden duren zonder dat de batterij voller wordt (geen stroom op het dock?): dan geeft hij het op. */
    private static final int CHARGE_STALL_TICKS = 200;

    private final GuardDroneEntity drone;
    private @Nullable Direction facing;
    private boolean blocked;
    private BotMove.@Nullable Plan plan;
    /** Is de beweging al klaar (meteen geblokkeerd)? Dan zegt de volgende tick dat hij klaar is. */
    private boolean finished;
    private int ticks;

    // Een stap (vooruit, achteruit, omhoog of omlaag): nog zoveel blokken, waar hij vandaan komt en waar hij heen gaat.
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

    public DroneMove(GuardDroneEntity drone) {
        this.drone = drone;
    }

    // ================================================================ Wat het brein vraagt

    @Override
    public Direction facing() {
        if (facing == null) {
            facing = Direction.fromYRot(drone.getYRot());
        }
        return facing;
    }

    @Override
    public void setFacing(@Nullable Direction facing) {
        this.facing = facing == null || facing.getAxis().isVertical() ? null : facing;
    }

    @Override
    public boolean blocked() {
        return blocked;
    }

    @Override
    public void markBlocked() {
        blocked = true;
    }

    @Override
    public boolean goingSomewhere() {
        return plan instanceof BotMove.GoTo && !finished;
    }

    @Override
    public boolean busy() {
        return plan != null;
    }

    @Override
    public boolean wantsDock() {
        return wantsDock;
    }

    @Override
    public void start(BotMove.Plan plan) {
        stop();
        this.plan = plan;
        blocked = false;
        finished = false;
        ticks = 0;
        switch (plan) {
            case BotMove.Walk walk -> {
                stepsLeft = Math.abs(walk.blocks());
                nextStep();
            }
            case BotMove.Climb climb -> {
                stepsLeft = Math.abs(climb.blocks());
                nextStep();
            }
            case BotMove.Turn turn -> {
                turnFrom = facing().toYRot();
                facing = turn.right() ? facing().getClockWise() : facing().getCounterClockWise();
                turnTo = turnFrom + (turn.right() ? 90 : -90);
            }
            case BotMove.GoTo goTo -> {
                pathAsked = false;
                if (!drone.fits(goTo.target())) {
                    finishBlocked(); // daar past hij niet (in een muur, of te krap)
                }
            }
            case BotMove.Charge charge -> {
                returnPos = drone.blockPosition();
                returnFacing = facing();
                lastBattery = drone.battery();
                stallTicks = 0;
                phase = Phase.TO_DOCK;
                // Al vol genoeg? Dan hoeft hij niet te vliegen.
                wantsDock = drone.battery() < drone.maxBattery() * charge.percent() / 100.0 - 1.0e-6;
                finished = !wantsDock;
            }
        }
    }

    /**
     * Een opdracht van de server onderbreekt deze beweging. Geeft terug wat er nog over is, om na de opdracht verder
     * te doen (of null): bij een stap de blokken die nog over zijn, bij goto en charge dezelfde opdracht opnieuw. Een
     * draai is meteen af.
     */
    @Override
    public BotMove.@Nullable Plan interrupt() {
        BotMove.Plan rest = switch (plan) {
            case null -> null;
            case BotMove.Walk walk -> retreating || finished || stepsLeft <= 0 ? null
                    : new BotMove.Walk(walk.blocks() < 0 ? -stepsLeft : stepsLeft);
            case BotMove.Climb climb -> retreating || finished || stepsLeft <= 0 ? null
                    : new BotMove.Climb(climb.blocks() < 0 ? -stepsLeft : stepsLeft);
            case BotMove.Turn turn -> null;
            case BotMove.GoTo goTo -> finished ? null : goTo;
            case BotMove.Charge charge -> finished ? null : charge;
        };
        if (plan instanceof BotMove.Turn) {
            drone.setYaw(facing().toYRot()); // de draai meteen af
        }
        stop();
        return rest;
    }

    @Override
    public void stop() {
        if (plan instanceof BotMove.GoTo || plan instanceof BotMove.Charge) {
            drone.getNavigation().stop();
        }
        plan = null;
        target = null;
        wantsDock = false;
    }

    @Override
    public boolean tick(ServerLevel level) {
        if (plan == null) {
            keepFacing();
            return false;
        }
        ticks++;
        boolean done = finished || switch (plan) {
            case BotMove.Walk walk -> tickStep();
            case BotMove.Climb climb -> tickStep();
            case BotMove.Turn turn -> tickTurn();
            case BotMove.GoTo goTo -> tickGoTo(goTo.target());
            case BotMove.Charge charge -> tickCharge(charge.percent());
        };
        if (done) {
            plan = null;
            wantsDock = false;
            keepFacing();
        }
        return done;
    }

    // ================================================================ Een stap: vooruit, achteruit, omhoog, omlaag

    /** Het volgende blok: past hij daar? Anders is hij geblokkeerd. */
    private void nextStep() {
        if (stepsLeft <= 0) {
            target = null;
            return;
        }
        Direction way = switch (plan) {
            case BotMove.Walk walk -> walk.blocks() < 0 ? facing().getOpposite() : facing();
            case BotMove.Climb climb -> climb.blocks() < 0 ? Direction.DOWN : Direction.UP;
            case null, default -> facing();
        };
        from = drone.blockPosition();
        BlockPos next = from.relative(way);
        target = drone.fits(next) ? next : null;
        retreating = false;
        stepTicks = 0;
        if (target == null) {
            blocked = true;
            stepsLeft = 0;
        }
    }

    private boolean tickStep() {
        if (target == null) {
            return true;
        }
        stepTicks++;
        keepFacing();
        if (drone.flyTo(drone.hoverSpot(target), speed())) {
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
                target = null; // ook terug lukt niet: dan blijft hij hangen waar hij is
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

    private static double speed() {
        return StarkConfig.Robots.DRONE_SPEED;
    }

    // ================================================================ Draaien

    private boolean tickTurn() {
        int total = Math.max(1, Math.round(StarkConfig.Robots.TURN_SECONDS * 20));
        float t = Math.min(1, ticks / (float) total);
        drone.setYaw(Mth.lerp(t, turnFrom, turnTo));
        return ticks >= total;
    }

    // ================================================================ Naar een plek (goto)

    private boolean tickGoTo(BlockPos goal) {
        if (target != null) {
            // Het laatste stukje: precies naar het midden.
            if (drone.flyTo(drone.hoverSpot(target), speed()) || ticks > maxGoToTicks()) {
                facing = Direction.fromYRot(drone.getYRot());
                return true;
            }
            return false;
        }
        if (ticks > maxGoToTicks()) {
            finishBlocked();
            return true;
        }
        // Voor de padzoeker is een blok ernaast al "aangekomen": het laatste stukje vliegen we zelf (hierboven).
        if (drone.position().distanceTo(drone.hoverSpot(goal)) < 1.6 && drone.getNavigation().isDone()) {
            target = goal;
        } else if (drone.getNavigation().isDone() && (!pathAsked || ticks % 20 == 0)) {
            pathAsked = true;
            var spot = drone.hoverSpot(goal);
            if (!drone.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0)) {
                finishBlocked(); // geen weg te vinden
                return true;
            }
        }
        return false;
    }

    private static int maxGoToTicks() {
        return Math.max(20, Math.round(StarkConfig.Robots.GOTO_SECONDS * 20));
    }

    // ================================================================ Laden (charge)

    private boolean tickCharge(double percent) {
        switch (phase) {
            case TO_DOCK -> {
                // Vliegen en landen doet hij zoals wanneer hij uitgezet wordt (zie GuardDroneEntity.dockTick).
                if (drone.home() == null || ticks > maxGoToTicks()) {
                    blocked = true;
                    wantsDock = false;
                    phase = Phase.BACK;
                    ticks = 0;
                    pathAsked = false;
                } else if (drone.isSettled()) {
                    phase = Phase.CHARGING;
                    lastBattery = drone.battery();
                    stallTicks = 0;
                }
            }
            case CHARGING -> {
                if (drone.battery() >= drone.maxBattery() * percent / 100.0 - 1.0e-6) {
                    leaveDock();
                } else if (drone.battery() > lastBattery) {
                    lastBattery = drone.battery();
                    stallTicks = 0;
                } else if (++stallTicks > CHARGE_STALL_TICKS) {
                    blocked = true; // het dock geeft geen stroom
                    leaveDock();
                }
            }
            case LEAVE -> {
                if (!drone.isLeavingDock()) {
                    phase = Phase.BACK;
                    ticks = 0;
                    pathAsked = false;
                }
            }
            case BACK -> {
                if (returnPos == null || drone.position().distanceTo(drone.hoverSpot(returnPos)) < 1.6) {
                    phase = Phase.FINAL;
                    drone.getNavigation().stop();
                } else if (ticks > maxGoToTicks()) {
                    blocked = true;
                    return true;
                } else if (drone.getNavigation().isDone() && (!pathAsked || ticks % 20 == 0)) {
                    pathAsked = true;
                    var spot = drone.hoverSpot(returnPos);
                    if (!drone.getNavigation().moveTo(spot.x, spot.y, spot.z, 1.0)) {
                        drone.flyTo(spot, speed()); // geen weg: dan maar rechtdoor
                    }
                }
            }
            case FINAL -> {
                if (returnPos == null || drone.flyTo(drone.hoverSpot(returnPos), speed()) || ticks > maxGoToTicks()) {
                    facing = returnFacing;
                    return true;
                }
            }
        }
        return false;
    }

    /** Klaar met laden: opstijgen (als hij niet op het dock zelf moest blijven). */
    private void leaveDock() {
        wantsDock = false;
        if (returnPos != null && returnPos.equals(drone.home())) {
            returnPos = null; // hij laadde vanaf zijn dock: daar blijft hij
            phase = Phase.FINAL;
            return;
        }
        drone.takeOff();
        phase = Phase.LEAVE;
    }

    // ================================================================ Hulpjes

    /** Het lukt niet (geen weg, te lang onderweg, past daar niet): geblokkeerd, en klaar. */
    private void finishBlocked() {
        blocked = true;
        drone.getNavigation().stop();
        target = null;
        finished = true;
    }

    /** Zijn neus precies naar zijn windrichting (behalve als de padzoeker hem stuurt: dan kijkt hij waar hij heen vliegt). */
    private void keepFacing() {
        if (plan instanceof BotMove.GoTo && target == null || plan instanceof BotMove.Charge && phase != Phase.FINAL) {
            return;
        }
        drone.setYaw(facing().toYRot());
    }
}
