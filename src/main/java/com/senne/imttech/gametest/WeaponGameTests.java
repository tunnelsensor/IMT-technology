package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.energy.PowerCables;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.raid.RaidDroneEntity;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.robot.GuardRobotEntity;
import com.senne.imttech.robot.RobotParts;
import com.senne.imttech.security.CameraBlock;
import com.senne.imttech.security.CameraBlockEntity;
import com.senne.imttech.shield.DomeGeneratorBlockEntity;
import com.senne.imttech.station.ChargerBlockEntity;
import com.senne.imttech.suit.TechSuitItem;
import com.senne.imttech.turret.TurretBlock;
import com.senne.imttech.turret.TurretBlockEntity;
import com.senne.imttech.weapons.Emp;
import com.senne.imttech.weapons.RollingEmpGrenade;
import com.senne.imttech.weapons.StickyEmpGrenade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de wapens (hoofdstuk 35, 25 oktober). Jouw wens: "zo'n robot is super OP dus om dit te counteren zijn er
 * droid poppers zoals in Star Wars. Deze schakelen al de elektrische dingen uit zoals robots maar ook turrets, drones,
 * openliggende elektrische wires", plus lasergeweren. Jouw antwoorden: de EMP Grenade (een bol die rolt) en de Sticky
 * EMP Grenade (een halve bol die plakt); alles is tijdelijk uit; ook camera's, lezers, panelen, het harnas en de bril;
 * niet de lasers, schilden en lampen.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class WeaponGameTests {
    private WeaponGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Een volle warmtegenerator op deze plek (stroom voor wat ernaast staat). */
    private static void generator(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(pos)).fill(20_000);
    }

    /** Een EMP-puls van {@code ticks} lang, met het midden van dit blok als middelpunt. */
    private static void blast(GameTestHelper helper, BlockPos at, double radius, int ticks) {
        Emp.blast(helper.getLevel(), helper.absoluteVec(Vec3.atCenterOf(at)), radius, ticks, null);
    }

    // ================================================================ Stap 1: de puls

    /**
     * Een guard robot en een raiddrone in de bol vallen uit (de robot ligt neer, de drone zakt naar de grond); een
     * robot net buiten de bol merkt niets. Na de puls staat de drone weer op.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "weapons")
    public static void emp_knocks_out_robots_and_drones(GameTestHelper helper) {
        GuardRobotEntity inside = RobotGameTests.robot(helper, new BlockPos(8, 2, 8), RobotParts.DEFAULT);
        GuardRobotEntity outside = RobotGameTests.robot(helper, new BlockPos(8, 2, 15), RobotParts.DEFAULT);
        RaidDroneEntity drone = helper.spawn(ModEntities.RAID_DRONE.get(), new BlockPos(10, 6, 8));
        double startY = drone.getY();
        blast(helper, new BlockPos(8, 3, 9), 4.5, 60);
        check(inside.isDown(), "De robot in de bol ligt niet neer");
        check(!outside.isDown(), "De robot buiten de bol ligt ook neer");
        check(Emp.isJammed(drone), "De raiddrone in de bol is niet uitgeschakeld");
        check(!Emp.isJammed(outside), "De robot buiten de bol telt als uitgeschakeld");
        helper.startSequence()
                .thenIdle(30)
                .thenExecute(() -> {
                    check(drone.getY() < startY - 2, "De raiddrone zakte niet naar de grond (van " + startY + " naar "
                            + drone.getY() + ")");
                    check(drone.isAlive() && drone.getHealth() == drone.getMaxHealth(), "De raiddrone kreeg schade");
                })
                .thenWaitUntil(() -> check(!Emp.isJammed(drone), "De raiddrone bleef uit"))
                .thenWaitUntil(() -> check(!inside.isDown(), "De robot stond niet meer op"))
                .thenSucceed();
    }

    /**
     * Een turret en een camera met stroom gaan meteen uit (de turret laat zijn loop hangen, de camera geeft geen beeld),
     * en na de puls weer aan. Een turret buiten de bol blijft aan.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "weapons")
    public static void emp_switches_off_turrets_and_cameras(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos turret = new BlockPos(6, 3, 6);
        helper.setBlock(turret, ModBlocks.ROCKET_TURRET.get());
        generator(helper, turret.below());
        BlockPos far = new BlockPos(18, 3, 6);
        helper.setBlock(far, ModBlocks.ROCKET_TURRET.get());
        generator(helper, far.below());
        BlockPos wall = new BlockPos(8, 4, 8);
        helper.setBlock(wall, Blocks.STONE);
        BlockPos camera = wall.south();
        helper.setBlock(camera, ModBlocks.SECURITY_CAMERA.get().defaultBlockState()
                .setValue(CameraBlock.FACE, AttachFace.WALL).setValue(CameraBlock.FACING, Direction.SOUTH));
        generator(helper, camera.below());
        TurretBlockEntity near = (TurretBlockEntity) helper.getBlockEntity(turret);
        TurretBlockEntity other = (TurretBlockEntity) helper.getBlockEntity(far);
        CameraBlockEntity cam = (CameraBlockEntity) helper.getBlockEntity(camera);
        BlockPos cameraAbs = helper.absolutePos(camera);
        helper.startSequence()
                .thenExecute(() -> {
                    near.refreshOnline(level);
                    other.refreshOnline(level);
                    cam.updateOnline(level, cameraAbs, level.getBlockState(cameraAbs));
                    helper.assertBlockProperty(turret, TurretBlock.ONLINE, true);
                    helper.assertBlockProperty(camera, CameraBlock.ONLINE, true);
                    blast(helper, new BlockPos(7, 3, 7), 4.0, 40);
                    helper.assertBlockProperty(turret, TurretBlock.ONLINE, false);
                    helper.assertBlockProperty(camera, CameraBlock.ONLINE, false);
                    helper.assertBlockProperty(far, TurretBlock.ONLINE, true);
                    check(!TurretBlockEntity.isActive(level.getBlockState(helper.absolutePos(turret))),
                            "De turret telt nog als aan");
                })
                .thenIdle(45)
                .thenExecute(() -> {
                    near.refreshOnline(level);
                    cam.updateOnline(level, cameraAbs, level.getBlockState(cameraAbs));
                    helper.assertBlockProperty(turret, TurretBlock.ONLINE, true);
                    helper.assertBlockProperty(camera, CameraBlock.ONLINE, true);
                })
                .thenSucceed();
    }

    /**
     * Een open kabel in de bol geeft geen stroom meer door (alles erachter is zonder stroom), een kabel die in een muur
     * zit wel. Na de puls werkt de open kabel weer.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "weapons")
    public static void emp_cuts_open_cables_but_not_walled_ones(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Twee kabels van 8 lang naast elkaar: de eerste ligt open, de tweede zit in een muur van stenen.
        for (int row : new int[] {3, 9}) {
            generator(helper, new BlockPos(1, 3, row));
            for (int x = 2; x <= 9; x++) {
                BlockPos cell = new BlockPos(x, 3, row);
                if (row == 9) {
                    helper.setBlock(cell, Blocks.STONE_BRICKS);
                }
                PowerCables.add(level, helper.absolutePos(cell));
            }
        }
        BlockPos openDevice = helper.absolutePos(new BlockPos(10, 3, 3));
        BlockPos walledDevice = helper.absolutePos(new BlockPos(10, 3, 9));
        check(PowerGrid.available(level, openDevice) > 0, "Over de open kabel komt geen stroom");
        check(PowerGrid.available(level, walledDevice) > 0, "Over de kabel in de muur komt geen stroom");
        // Midden tussen de twee kabels: een bol die allebei raakt (de generators en apparaten liggen erbuiten).
        blast(helper, new BlockPos(5, 3, 6), 3.6, 30);
        check(PowerGrid.available(level, openDevice) == 0, "De open kabel geeft na de EMP nog stroom door");
        check(PowerGrid.available(level, walledDevice) > 0, "De kabel in de muur is ook doorgebrand");
        helper.startSequence()
                .thenIdle(35)
                .thenExecute(() -> check(PowerGrid.available(level, openDevice) > 0,
                        "Na de EMP komt er nog steeds geen stroom over de open kabel"))
                .thenSucceed();
    }

    /**
     * Een speler met het harnas en de bril in de bol: het harnas doet niets meer (niet vliegen, geen bescherming) en de
     * bril werkt niet. Na de puls werkt alles weer.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "weapons")
    public static void emp_shuts_down_suit_and_glasses(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 2, 6), "emp-suit");
        owner.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.TECH_CHESTPLATE.get()));
        owner.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.TECH_LEGGINGS.get()));
        owner.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.TECH_BOOTS.get()));
        check(TechSuitItem.isPowered(owner) && ImtData.isOperational(owner), "Het harnas of de bril werkt niet");
        blast(helper, new BlockPos(7, 2, 6), 4.0, 30);
        check(Emp.isJammed(owner), "De speler telt niet als geraakt");
        check(!TechSuitItem.isPowered(owner), "Het harnas werkt nog na de EMP");
        check(!ImtData.isOperational(owner), "De bril werkt nog na de EMP");
        helper.startSequence()
                .thenIdle(35)
                .thenExecute(() -> check(TechSuitItem.isPowered(owner) && ImtData.isOperational(owner),
                        "Na de EMP werken het harnas en de bril niet meer"))
                .thenSucceed();
    }

    /** Een keycardlezer en een codepaneel in de bol doen niets; een lezer buiten de bol wel. */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "weapons")
    public static void emp_kills_readers_and_panels(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos reader = new BlockPos(6, 3, 6);
        BlockPos panel = new BlockPos(7, 3, 6);
        BlockPos farReader = new BlockPos(16, 3, 6);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        helper.setBlock(panel, ModBlocks.CODE_PANEL.get());
        helper.setBlock(farReader, ModBlocks.KEYCARD_READER.get());
        blast(helper, new BlockPos(6, 3, 7), 4.0, 30);
        check(Emp.deadDevice(level, helper.absolutePos(reader), null), "De lezer in de bol werkt nog");
        check(Emp.deadDevice(level, helper.absolutePos(panel), null), "Het paneel in de bol werkt nog");
        check(!Emp.deadDevice(level, helper.absolutePos(farReader), null), "De lezer buiten de bol is ook dood");
        helper.startSequence()
                .thenIdle(35)
                .thenExecute(() -> check(!Emp.deadDevice(level, helper.absolutePos(reader), null),
                        "Na de EMP is de lezer nog steeds dood"))
                .thenSucceed();
    }

    /**
     * Een schildkoepel beschermt wat erin staat tegen een granaat die buiten de koepel afgaat: de turret binnen blijft
     * aan, de turret ernaast (buiten de koepel, even dichtbij) gaat uit.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "weapons")
    public static void emp_does_not_pass_a_dome(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos domePos = new BlockPos(12, 3, 12);
        helper.setBlock(domePos, ModBlocks.DOME_GENERATOR.get());
        generator(helper, domePos.east());
        DomeGeneratorBlockEntity dome = (DomeGeneratorBlockEntity) helper.getBlockEntity(domePos);
        dome.setRadius(0);
        BlockPos inside = new BlockPos(12, 3, 18);
        BlockPos outside = new BlockPos(16, 3, 22);
        for (BlockPos turret : new BlockPos[] {inside, outside}) {
            helper.setBlock(turret, ModBlocks.ROCKET_TURRET.get());
            generator(helper, turret.below());
        }
        helper.startSequence()
                .thenWaitUntil(() -> check(dome.isUp(), "De koepel ging niet op: " + dome.status()))
                .thenExecute(() -> {
                    Vec3 blastAt = helper.absoluteVec(new Vec3(12.5, 3.5, 22.5));
                    check(!dome.contains(blastAt), "De granaat ligt in de koepel (de test klopt niet)");
                    check(dome.contains(helper.absoluteVec(Vec3.atCenterOf(inside))),
                            "De turret ligt niet in de koepel (de test klopt niet)");
                    Emp.blast(level, blastAt, 5.0, 40, null);
                    helper.assertBlockProperty(outside, TurretBlock.ONLINE, false);
                    ((TurretBlockEntity) helper.getBlockEntity(inside)).refreshOnline(level);
                    helper.assertBlockProperty(inside, TurretBlock.ONLINE, true);
                })
                .thenSucceed();
    }

    // ================================================================ Stap 2: de granaten

    /**
     * Een Sticky EMP Grenade vliegt tegen een muur, plakt vast met zijn bolle kant naar buiten, en gaat 2 seconden later
     * af: de turret ernaast valt uit en de granaat is weg.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "weapons")
    public static void sticky_grenade_sticks_to_a_wall_and_goes_off(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int y = 2; y <= 5; y++) {
            for (int z = 4; z <= 8; z++) {
                helper.setBlock(new BlockPos(12, y, z), Blocks.STONE);
            }
        }
        BlockPos turret = new BlockPos(10, 2, 8);
        helper.setBlock(turret, ModBlocks.ROCKET_TURRET.get());
        generator(helper, turret.below());
        ((TurretBlockEntity) helper.getBlockEntity(turret)).refreshOnline(level);
        StickyEmpGrenade grenade = new StickyEmpGrenade(ModEntities.STICKY_EMP_GRENADE.get(), level);
        Vec3 start = helper.absoluteVec(new Vec3(4.5, 3.5, 6.5));
        grenade.setPos(start.x, start.y, start.z);
        grenade.shoot(1, 0.05, 0, 1.0f, 0);
        level.addFreshEntity(grenade);
        double wallX = helper.absoluteVec(new Vec3(12, 0, 0)).x;
        helper.startSequence()
                .thenWaitUntil(() -> check(grenade.isStuck(), "De granaat plakte niet vast"))
                .thenExecute(() -> {
                    check(grenade.normal().x < -0.9, "De bolle kant wijst niet van de muur af: " + grenade.normal());
                    check(Math.abs(grenade.getX() - wallX) < 0.1, "De granaat zit niet tegen de muur: x = " + grenade.getX());
                    check(grenade.isArmed() && grenade.ticksLeft() > 30, "De lont liep niet, of was al bijna op");
                    helper.assertBlockProperty(turret, TurretBlock.ONLINE, true);
                })
                .thenWaitUntil(() -> check(grenade.isRemoved(), "De granaat ging niet af"))
                .thenExecute(() -> helper.assertBlockProperty(turret, TurretBlock.ONLINE, false))
                .thenSucceed();
    }

    /**
     * Een Sticky EMP Grenade op een robot gaat met de robot mee als die verplaatst wordt, en legt hem neer als hij
     * afgaat.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "weapons")
    public static void sticky_grenade_rides_along_on_a_robot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GuardRobotEntity robot = RobotGameTests.robot(helper, new BlockPos(10, 2, 6), RobotParts.DEFAULT);
        StickyEmpGrenade grenade = new StickyEmpGrenade(ModEntities.STICKY_EMP_GRENADE.get(), level);
        Vec3 start = helper.absoluteVec(new Vec3(4.5, 3.2, 6.5));
        grenade.setPos(start.x, start.y, start.z);
        grenade.shoot(1, 0.05, 0, 1.0f, 0);
        level.addFreshEntity(grenade);
        helper.startSequence()
                .thenWaitUntil(() -> check(grenade.host() == robot, "De granaat plakte niet op de robot"))
                .thenExecute(() -> robot.teleportTo(robot.getX(), robot.getY(), robot.getZ() + 3))
                .thenIdle(2)
                .thenExecute(() -> check(grenade.distanceTo(robot) < 1.2,
                        "De granaat ging niet mee met de robot (afstand " + grenade.distanceTo(robot) + ")"))
                .thenWaitUntil(() -> check(grenade.isRemoved(), "De granaat ging niet af"))
                .thenExecute(() -> check(robot.isDown(), "De robot ligt niet neer na de granaat"))
                .thenSucceed();
    }

    /**
     * De EMP Grenade (de bol) stuitert terug tegen een muur, blijft daarna op de vloer liggen (niet erdoor), en gaat 3
     * seconden na het gooien af, ook als hij stil ligt.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "weapons")
    public static void rolling_grenade_bounces_and_goes_off(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int y = 2; y <= 5; y++) {
            for (int z = 3; z <= 9; z++) {
                helper.setBlock(new BlockPos(9, y, z), Blocks.STONE);
            }
        }
        GuardRobotEntity robot = RobotGameTests.robot(helper, new BlockPos(5, 2, 9), RobotParts.DEFAULT);
        RollingEmpGrenade grenade = new RollingEmpGrenade(ModEntities.ROLLING_EMP_GRENADE.get(), level);
        Vec3 start = helper.absoluteVec(new Vec3(4.5, 2.6, 6.5));
        grenade.setPos(start.x, start.y, start.z);
        grenade.arm(60);
        grenade.shoot(1, 0.1, 0, 0.8f, 0);
        level.addFreshEntity(grenade);
        double floor = helper.absoluteVec(new Vec3(0, 2, 0)).y;
        double wallX = helper.absoluteVec(new Vec3(9, 0, 0)).x;
        boolean[] bounced = new boolean[1];
        helper.onEachTick(() -> {
            if (!grenade.isRemoved()) {
                check(grenade.getBoundingBox().maxX < wallX + 0.01, "De bol ging door de muur");
                check(grenade.getY() > floor - 0.01, "De bol zakte door de vloer");
                if (grenade.getDeltaMovement().x < -0.02) {
                    bounced[0] = true;
                }
            }
        });
        helper.startSequence()
                .thenIdle(45)
                .thenExecute(() -> {
                    check(bounced[0], "De bol kaatste niet terug tegen de muur");
                    check(grenade.onGround() && grenade.getDeltaMovement().horizontalDistance() < 0.05,
                            "De bol ligt niet stil op de vloer: " + grenade.getDeltaMovement());
                    check(!grenade.isRemoved(), "De bol ging te vroeg af");
                })
                .thenWaitUntil(() -> check(grenade.isRemoved(), "De bol ging niet af"))
                .thenExecute(() -> check(robot.isDown(), "De robot naast de bol ligt niet neer"))
                .thenSucceed();
    }

    /** Gooien: rechtsklik maakt een granaat (met jou als gooier), en er gaat er een uit je stapel. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "weapons")
    public static void throwing_a_grenade_uses_one(GameTestHelper helper) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 4), "emp-thrower");
        player.getAbilities().instabuild = false; // de testspeler mag anders alles gratis (zoals in creative)
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STICKY_EMP_GRENADE.get(), 3));
        ModItems.STICKY_EMP_GRENADE.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        check(player.getMainHandItem().getCount() == 2, "Er ging geen granaat uit de stapel");
        java.util.List<StickyEmpGrenade> thrown = helper.getLevel().getEntitiesOfClass(StickyEmpGrenade.class,
                player.getBoundingBox().inflate(4));
        check(thrown.size() == 1 && thrown.get(0).getOwner() == player, "Er vloog geen granaat van de speler");
        check(new ItemStack(ModItems.EMP_GRENADE.get()).getMaxStackSize() == 16, "Granaten stapelen niet tot 16");
        helper.succeed();
    }
}
