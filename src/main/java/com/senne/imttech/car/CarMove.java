package com.senne.imttech.car;

import com.senne.imttech.config.ImtConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Het lijf van een RC Car (hoofdstuk 108): hoe zijn programma hem laat rijden, draaien, breken en plaatsen. Altijd één
 * ding tegelijk (een {@link Plan}); zijn programma wacht tot dat klaar is. Het rijden werkt zoals het lopen van een
 * guard robot (bot/BotMove): blok voor blok, precies van het midden van het ene blok naar het midden van het volgende.
 * <ul>
 *   <li><b>Drive</b> ({@code forward(3)}, {@code back(1)}): hij rijdt één blok op of af; een muur, een gat van meer dan
 *       één blok, of iets dat hem te lang tegenhoudt, en hij stopt: dan is {@code car.blocked} waar en gaat hij terug
 *       naar het midden van het blok waar hij was. Achteruit blijft hij dezelfde kant op kijken. Elk blok kost stroom.</li>
 *   <li><b>Turn</b> ({@code turn_left()}, {@code turn_right()}): een kwartslag, in een paar ticks.</li>
 *   <li><b>Mine</b> ({@code mine()}, {@code mine_up()}, {@code mine_down()}): het blok vóór hem (op de hoogte van zijn
 *       arm), boven of onder hem. Hoe lang breken duurt, hangt af van zijn boorkop (met netherite zo lang als bij jou
 *       met ijzeren gereedschap, met de ijzeren kop vier keer zo lang). Valt er grind of zand in het gat,
 *       dan wacht hij tot het ligt en breekt hij dat ook: na {@code mine()} is de plek dus echt vrij. Een blok dat hij
 *       niet mag of kan breken (bedrock, obsidiaan met een te zwakke kop, een beschermde zone): {@code car.blocked}.
 *       Boven en onder zich boort hij alleen met een RC Robot Arm (hoofdstuk 118); zonder arm: {@code car.blocked}.</li>
 *   <li><b>Place</b> ({@code place()}, {@code place_up()}, {@code place_down()}): een blok uit zijn kofferbak.</li>
 *   <li><b>Arm</b> ({@code arm_up()}, {@code arm_down()}, hoofdstuk 113): zijn arm met de boor gaat een blok omhoog of
 *       omlaag (hooguit vijf blokken boven het blok vóór hem, sinds hoofdstuk 121; daarvoor twee). Alles wat hij vóór
 *       zich doet (breken, plaatsen, kijken,
 *       een kist) doet hij op die hoogte: zie {@link #front()}. Alleen met een RC Robot Arm (hoofdstuk 118).</li>
 *   <li><b>Chest</b> ({@code take()}, {@code put()}, {@code check()}, hoofdstuk 113): uit de kist vóór hem nemen, erin
 *       leggen, of erin kijken. Dat duurt even (de arm reikt erin).</li>
 * </ul>
 * Vóór elk van die dingen wacht hij tot hij op de grond staat (na {@code mine_down()} valt hij eerst) en gaat hij precies
 * in het midden van zijn blok staan. Hij kijkt altijd recht een windrichting op ({@link #facing}): daar gaat "vooruit"
 * heen.
 */
public final class CarMove {
    /** Wat zijn programma van zijn lijf vraagt. */
    public sealed interface Plan permits Drive, Turn, Mine, Place, Arm, Chest {}

    /** {@code forward(n)} (blocks positief) of {@code back(n)} (negatief). */
    public record Drive(int blocks) implements Plan {}

    /** Een kwartslag naar rechts ({@code right}) of links. */
    public record Turn(boolean right) implements Plan {}

    /** Breken: {@code way} 0 = vóór hem (op de hoogte van zijn arm), 1 = boven hem, -1 = onder hem. */
    public record Mine(int way) implements Plan {}

    /** De arm zoveel blokken omhoog ({@code levels} positief) of omlaag (negatief). */
    public record Arm(int levels) implements Plan {}

    /** Wat hij bij een kist doet. */
    public enum ChestJob { TAKE, PUT, CHECK }

    /**
     * Bij de kist vóór hem (op de hoogte van zijn arm): nemen, leggen of kijken. {@code item} is de naam van het item
     * ("iron_ingot"), of leeg voor alles; {@code count} hoeveel, of -1 voor zoveel als er is.
     */
    public record Chest(ChestJob job, String item, int count) implements Plan {}

    /**
     * Plaatsen: {@code way} 0 = vóór hem, 1 = boven hem, -1 = onder hem (dan komt hij zelf een blok hoger);
     * {@code kind} is het soort blok ("cobblestone"), of leeg voor het eerste blok in zijn kofferbak.
     */
    public record Place(int way, String kind) implements Plan {}

    /** Zo dicht (blokken) bij het midden van een blok telt als "aangekomen". */
    private static final double ARRIVED = 0.12;
    /** Zo lang (ticks) mag hij erover doen om op de grond en in het midden van zijn blok te komen. */
    private static final int SETTLE_TICKS = 200;
    /** Zo lang (ticks) wachten we hooguit op grind of zand dat nog valt, en zo vaak kijken we hooguit opnieuw. */
    private static final int FALL_TICKS = 80;
    private static final int MAX_RESCANS = 64;
    /** Zo lang (ticks) duurt het om de arm één blok te bewegen, en om iets uit een kist te nemen of erin te leggen. */
    private static final int ARM_TICKS = 6;
    private static final int CHEST_TICKS = 8;

    private final RcCarEntity car;
    private @Nullable Direction facing;
    private boolean blocked;
    private @Nullable Plan plan;
    /** Is het al klaar (meteen geblokkeerd, of plaatsen: dat gaat in één keer)? */
    private boolean finished;
    /** Staat hij al klaar voor dit plan (op de grond, in het midden van zijn blok)? */
    private boolean ready;
    private int ticks;
    /** Zijn energiecel was leeg toen hij iets wilde doen: zijn brein stopt dan het programma. */
    private boolean powerOut;

    // Rijden: nog zoveel blokken, het blok waar hij vandaan komt en waar hij heen gaat.
    private int stepsLeft;
    private @Nullable BlockPos from;
    private @Nullable BlockPos target;
    private boolean retreating;
    private int stepTicks;

    // Draaien: van welke hoek naar welke.
    private float turnFrom;
    private float turnTo;

    // Breken: welke blokken, bij welke hij is, hoe ver dat blok is, en of hij wacht op wat er nog valt.
    private final List<BlockPos> mineTargets = new ArrayList<>();
    private int mineIndex;
    private float mineProgress;
    private int mineTicks;
    private int fallTicks;
    private int rescans;
    /** Boort hij nu (voor het model en het geluid in je spel)? */
    private boolean drilling;
    /** De arm of een kist: zo lang (ticks) duurt dit plan nog te doen. */
    private int waitTicks;

    public CarMove(RcCarEntity car) {
        this.car = car;
    }

    // ================================================================ Wat het brein vraagt

    /** De kant waar hij naar kijkt (en waar vooruit heen gaat). Nog niet bekend: de windrichting van zijn neus. */
    public Direction facing() {
        if (facing == null) {
            facing = Direction.fromYRot(car.getYRot());
        }
        return facing;
    }

    public void setFacing(@Nullable Direction facing) {
        this.facing = facing == null || facing.getAxis().isVertical() ? null : facing;
    }

    /**
     * Het blok waar de auto in staat: het blok van zijn lijf. Dat is niet altijd het blok van zijn voeten: op een vloer
     * die net geen heel blok hoog is (een pad of akkerland: 15 pixels, soul sand of modder: 14) staan zijn voeten nog
     * ín het vloerblok, en dan zou "het blok voor hem" de vloer voor hem zijn. Daarom kijken we een half blok hoger. Op
     * een halve plaat of een tapijt (tot een half blok hoog) blijft het het blok van die plaat zelf: daar rekent
     * {@link #floorOf} mee.
     */
    public BlockPos cell() {
        return BlockPos.containing(car.getX(), car.getY() + 0.4999, car.getZ());
    }

    /**
     * Het blok waar zijn arm bij kan (hoofdstuk 113): het blok vóór de auto, zoveel hoger als zijn arm staat (0 tot 5).
     * Daar breekt hij met {@code mine()}, plaatst hij met {@code place()}, kijkt hij met {@code block()}, en daar staat
     * de kist van {@code take()}, {@code put()}, {@code check()} en {@code in chest()}.
     */
    public BlockPos front() {
        return cell().relative(facing()).above(car.arm());
    }

    /** Lukte zijn laatste stap, breken of plaatsen niet? ({@code car.blocked}) */
    public boolean blocked() {
        return blocked;
    }

    /** Is hij met iets bezig? */
    public boolean busy() {
        return plan != null;
    }

    /** Boort hij nu? (Dan draait zijn boor in je spel.) */
    public boolean drilling() {
        return drilling;
    }

    /**
     * Waar zijn boor nu naar wijst (hoofdstuk 118): 1 als hij het blok boven zich wegboort, -1 onder zich, anders 0. Met
     * de RC Robot Arm kantelt zijn boor dan in je spel naar boven of onder (zie RcCarModel).
     */
    public int drillWay() {
        return drilling && plan instanceof Mine mine ? mine.way() : 0;
    }

    /**
     * Was zijn energiecel leeg toen hij iets wilde doen? Het brein vraagt dit één keer (daarna is het weer onwaar) en
     * stopt dan het programma.
     */
    public boolean takePowerOut() {
        boolean out = powerOut;
        powerOut = false;
        return out;
    }

    /** Begin hieraan (zodra hij op de grond en in het midden van zijn blok staat). */
    public void start(Plan plan) {
        stop();
        this.plan = plan;
        blocked = false;
        finished = false;
        ready = false;
        ticks = 0;
    }

    /** Stop meteen met wat hij deed. */
    public void stop() {
        if (plan instanceof Mine) {
            clearCracks();
        }
        plan = null;
        target = null;
        drilling = false;
        mineTargets.clear();
    }

    /**
     * Elke tick. Geeft true op de tick dat hij klaar is met wat hij deed (dan gaat zijn programma verder).
     */
    public boolean tick(ServerLevel level) {
        if (plan == null) {
            keepFacing();
            return false;
        }
        ticks++;
        if (!ready) {
            if (!settle(level)) {
                if (ticks > SETTLE_TICKS) {
                    blocked = true; // hij komt niet tot stilstand (hij valt nog, of zit klem)
                    return finish();
                }
                return false;
            }
            ready = true;
            ticks = 0;
            begin(level);
        }
        boolean done = finished || switch (plan) {
            case Drive drive -> tickDrive(level);
            case Turn turn -> tickTurn();
            case Mine mine -> tickMine(level, mine);
            case Place place -> true;
            case Arm arm -> ticks >= waitTicks;
            case Chest chest -> ticks >= waitTicks;
        };
        return done && finish();
    }

    /** Klaar: het plan is weg. Geeft altijd true. */
    private boolean finish() {
        stop();
        keepFacing();
        return true;
    }

    /**
     * Staat hij klaar: op de grond, en precies in het midden van zijn blok? Zo niet, dan gaat hij erheen (of valt hij
     * verder). Zit hij vast in een blok dat op hem viel (grind, zand), dan breekt hij zich eerst vrij.
     */
    private boolean settle(ServerLevel level) {
        if (!car.onGround()) {
            car.setDeltaMovement(0, car.getDeltaMovement().y, 0); // hij valt nog (ook in water zakt hij naar de bodem)
            return false;
        }
        CarWork.freeItself(level, car);
        return approach(cell());
    }

    /** Het plan begint echt (hij staat klaar). */
    private void begin(ServerLevel level) {
        switch (plan) {
            case Drive drive -> {
                stepsLeft = Math.abs(drive.blocks());
                nextStep(level);
            }
            case Turn turn -> {
                turnFrom = facing().toYRot();
                facing = turn.right() ? facing().getClockWise() : facing().getCounterClockWise();
                turnTo = turnFrom + (turn.right() ? 90 : -90);
            }
            case Mine mine when mine.way() != 0 && !car.hasRobotArm() -> {
                // Hoofdstuk 118 (jouw wens): boven of onder zich minen kan alleen met een RC Robot Arm. Zonder arm zit
                // zijn boor vast op zijn neus. Net als een blok dat te hard is: car.blocked, en zijn scherm zegt waarom.
                car.brain().report(mine.way() > 0 ? "it needs an RC Robot Arm to mine above itself"
                        : "it needs an RC Robot Arm to mine below itself");
                blocked = true;
                finished = true;
            }
            case Mine mine -> {
                mineTargets.clear();
                // Vóór hem: op de hoogte van zijn arm. Boven of onder hem: het blok tegen de auto zelf.
                mineTargets.add(mine.way() == 0 ? front() : cell().relative(way(mine.way())));
                mineIndex = 0;
                mineProgress = 0;
                mineTicks = 0;
                fallTicks = 0;
                rescans = 0;
            }
            case Place place -> {
                CarWork.Result result = CarWork.place(level, car, place.way(), way(place.way()), place.kind());
                blocked = result != CarWork.Result.OK;
                powerOut |= result == CarWork.Result.NO_POWER;
                finished = true;
            }
            case Arm arm when !car.hasRobotArm() -> {
                // Hoofdstuk 118: zonder RC Robot Arm heeft hij geen arm om op te tillen.
                car.brain().report("it has no RC Robot Arm to raise or lower");
                blocked = true;
                finished = true;
            }
            case Arm arm -> {
                // Hooguit Op.MAX_ARM blokken omhoog, en niet lager dan het blok vóór hem. Elk blok kost wat stroom.
                int to = Math.max(0, Math.min(com.senne.imttech.logic.program.Op.MAX_ARM, car.arm() + arm.levels()));
                int moved = Math.abs(to - car.arm());
                if (moved == 0) {
                    blocked = true; // hij staat al helemaal boven (of beneden)
                    finished = true;
                } else if (!car.spend(ImtConfig.RcCar.ARM_COST * moved)) {
                    powerOut = true;
                    blocked = true;
                    finished = true;
                } else {
                    blocked = moved < Math.abs(arm.levels()); // arm_up(2) vanaf 4: hij kwam maar één blok hoger (hooguit 5)
                    car.setArm(to);
                    car.playSound(com.senne.imttech.registry.ModSounds.ROBOT_STEP.get(), 0.2f, 1.2f);
                    waitTicks = ARM_TICKS * moved;
                }
            }
            case Chest chest -> {
                CarWork.Result result = switch (chest.job()) {
                    case TAKE -> CarWork.take(level, car, chest.item(), chest.count());
                    case PUT -> CarWork.put(level, car, chest.item(), chest.count());
                    case CHECK -> CarWork.check(level, car);
                };
                blocked = result != CarWork.Result.OK;
                powerOut |= result == CarWork.Result.NO_POWER;
                // Lukte het, dan duurt het even (de arm reikt in de kist); anders gaat zijn programma meteen verder.
                finished = blocked;
                waitTicks = CHEST_TICKS;
            }
            case null -> { }
        }
    }

    /** De richting van 0 (vóór hem), 1 (boven) of -1 (onder). */
    private Direction way(int way) {
        return way == 0 ? facing() : way > 0 ? Direction.UP : Direction.DOWN;
    }

    // ================================================================ Rijden

    /** Het volgende blok: kan hij daar staan (gelijk, één op of één af)? Anders is hij geblokkeerd. */
    private void nextStep(ServerLevel level) {
        if (stepsLeft <= 0) {
            target = null;
            return;
        }
        Direction way = plan instanceof Drive drive && drive.blocks() < 0 ? facing().getOpposite() : facing();
        from = cell();
        target = standOn(from.relative(way));
        retreating = false;
        stepTicks = 0;
        if (target == null) {
            blocked = true;
            stepsLeft = 0;
        } else if (!car.spend(ImtConfig.RcCar.MOVE_COST)) {
            // Geen stroom voor deze stap: hij blijft staan, en zijn brein stopt het programma.
            powerOut = true;
            blocked = true;
            stepsLeft = 0;
            target = null;
        }
    }

    /**
     * Waar zijn wielen komen als hij naar dit blok rijdt: hetzelfde blok, één hoger (oprijden) of één lager (afrijden);
     * null als hij daar niet past of als er een gat is.
     */
    private @Nullable BlockPos standOn(BlockPos next) {
        if (fits(next) && (hasGround(next) || fits(next.below()) && hasGround(next.below()))) {
            return hasGround(next) ? next : next.below();
        }
        // Oprijden: boven hem en boven het volgende blok moet plek zijn.
        if (fits(next.above()) && hasGround(next.above()) && fits(cell().above())) {
            return next.above();
        }
        return null;
    }

    /** Past hij op dit blok, zonder ergens tegenaan te zitten? */
    boolean fits(BlockPos wheels) {
        AABB box = car.getDimensions(car.getPose()).makeBoundingBox(wheels.getX() + 0.5, wheels.getY() + floorOf(wheels), wheels.getZ() + 0.5)
                .deflate(0.02);
        return car.level().noCollision(car, box);
    }

    /** Hoe hoog de vloer in dit blok zelf is (een plaat: een stukje; meestal 0). */
    private double floorOf(BlockPos wheels) {
        double top = car.level().getBlockState(wheels).getCollisionShape(car.level(), wheels).max(Direction.Axis.Y);
        return top > 0 && top <= 0.5 ? top : 0;
    }

    /** Staat er iets onder dit blok (of is het blok zelf een lage vloer, zoals een plaat)? */
    private boolean hasGround(BlockPos wheels) {
        if (floorOf(wheels) > 0) {
            return true;
        }
        BlockPos below = wheels.below();
        return !car.level().getBlockState(below).getCollisionShape(car.level(), below).isEmpty();
    }

    private boolean tickDrive(ServerLevel level) {
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
            nextStep(level);
            return target == null;
        }
        if (stepTicks > Math.max(5, Math.round(ImtConfig.Robots.STUCK_SECONDS * 20))) {
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
     * Eén tick richting het midden van dit blok (zelf geduwd, zodat hij precies rijdt). Geeft true als hij er is: dan
     * staat hij precies in het midden.
     */
    private boolean approach(BlockPos block) {
        double cx = block.getX() + 0.5;
        double cz = block.getZ() + 0.5;
        double dx = cx - car.getX();
        double dz = cz - car.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < ARRIVED && car.onGround()) {
            car.setPos(cx, car.getY(), cz);
            car.setDeltaMovement(0, car.getDeltaMovement().y, 0);
            return true;
        }
        double speed = Math.min(distance, ImtConfig.RcCar.SPEED / 20.0);
        Vec3 motion = car.getDeltaMovement();
        car.setDeltaMovement(distance < 1.0e-4 ? 0 : dx / distance * speed, motion.y, distance < 1.0e-4 ? 0 : dz / distance * speed);
        return false;
    }

    // ================================================================ Draaien

    private boolean tickTurn() {
        int total = Math.max(1, Math.round(ImtConfig.Robots.TURN_SECONDS * 20));
        float t = Math.min(1, ticks / (float) total);
        setYaw(Mth.lerp(t, turnFrom, turnTo));
        return ticks >= total;
    }

    // ================================================================ Breken

    /**
     * Eén tick boren. De blokken gaan van dichtbij naar ver; lucht en water slaat hij over. Na een blok waar grind of zand
     * boven hing, wacht hij tot dat ligt en kijkt hij opnieuw vanaf het dichtste blok (het kan tot in zijn eigen blok
     * vallen: dan breekt hij zich eerst vrij).
     */
    private boolean tickMine(ServerLevel level, Mine mine) {
        if (fallTicks > 0) {
            drilling = false;
            fallTicks++;
            // Even wachten tot het begint te vallen (2 ticks), dan tot er niets meer valt.
            if (fallTicks < 6 || fallTicks < FALL_TICKS && somethingFalls(level)) {
                return false;
            }
            fallTicks = 0;
            mineIndex = 0; // opnieuw kijken vanaf het dichtste blok
        }
        // Viel er iets tot in zijn eigen blok? Dan breekt hij zich eerst vrij; wat daar nog bovenop lag, valt dan ook.
        if (CarWork.freeItself(level, car)) {
            return !watchFall() && doneDrilling();
        }
        while (mineIndex < mineTargets.size()) {
            BlockPos pos = mineTargets.get(mineIndex);
            BlockState state = level.getBlockState(pos);
            CarWork.Result result = CarWork.canMine(level, car, pos, state);
            if (result == CarWork.Result.NOTHING) {
                mineIndex++; // lucht of water: daar valt niets te breken
                mineProgress = 0;
                continue;
            }
            if (result != CarWork.Result.OK) {
                // Dit blok kan of mag hij niet breken (of zijn cel is leeg): wat erachter zit, bereikt hij dan ook niet.
                blocked = true;
                powerOut |= result == CarWork.Result.NO_POWER;
                drilling = false;
                return true;
            }
            drilling = true;
            mineProgress += CarWork.progressPerTick(level, car, pos, state);
            if (mineTicks++ % 4 == 0) {
                CarWork.drillSound(level, car, pos, state);
            }
            if (mineProgress < 1.0f) {
                level.destroyBlockProgress(car.getId(), pos, Math.min(9, (int) (mineProgress * 10.0f)));
                return false;
            }
            level.destroyBlockProgress(car.getId(), pos, -1);
            CarWork.breakBlock(level, car, pos, state);
            mineProgress = 0;
            // Hing er grind of zand boven? Dat valt nu in het gat (bij mine_up tot op de auto zelf).
            if (mine.way() >= 0 && level.getBlockState(pos.above()).getBlock() instanceof Fallable && watchFall()) {
                return false;
            }
            mineIndex++;
            return false; // de volgende tick het volgende blok (of klaar)
        }
        // Alles is weg. Valt er toch nog iets (grind dat onder zijn boor vandaan viel), dan wacht hij daar nog op.
        if (somethingFalls(level) && watchFall()) {
            return false;
        }
        return doneDrilling();
    }

    /**
     * Wacht op wat er nog valt, en kijk daarna opnieuw. Geeft false als hij dat al te vaak deed (een eindeloze stroom
     * zand): dan houdt hij ermee op.
     */
    private boolean watchFall() {
        if (rescans++ >= MAX_RESCANS) {
            return false;
        }
        fallTicks = 1;
        drilling = false;
        return true;
    }

    private boolean doneDrilling() {
        drilling = false;
        return true;
    }

    /** Valt er nog een blok (grind, zand) in de buurt van waar hij boort? */
    private boolean somethingFalls(ServerLevel level) {
        AABB area = car.getBoundingBox().inflate(0.6, 0.2, 0.6);
        for (BlockPos pos : mineTargets) {
            area = area.minmax(new AABB(pos).inflate(0.3, 2.0, 0.3));
        }
        return !level.getEntitiesOfClass(FallingBlockEntity.class, area).isEmpty();
    }

    /** De barstjes op de blokken waar hij aan boorde, gaan weg. */
    private void clearCracks() {
        if (car.level() instanceof ServerLevel level) {
            for (BlockPos pos : mineTargets) {
                level.destroyBlockProgress(car.getId(), pos, -1);
            }
        }
    }

    // ================================================================ Hulpjes

    /** Zijn neus (en lijf) precies naar zijn windrichting. */
    private void keepFacing() {
        if (plan instanceof Turn && ready) {
            return; // hij is aan het draaien
        }
        setYaw(facing().toYRot());
    }

    private void setYaw(float yaw) {
        car.setYRot(yaw);
        car.setYHeadRot(yaw);
        car.setYBodyRot(yaw);
    }
}
