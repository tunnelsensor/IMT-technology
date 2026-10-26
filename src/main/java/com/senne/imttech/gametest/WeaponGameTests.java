package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
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
import com.senne.imttech.weapons.RollingGrenade;
import com.senne.imttech.weapons.StickyGrenade;
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
     * af: de turret ernaast valt uit en de granaat is weg. De muur blijft heel (jouw keuze: de EMP zelf doet geen schade
     * aan de map).
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
        StickyGrenade grenade = new StickyGrenade(ModEntities.STICKY_EMP_GRENADE.get(), level);
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
                .thenExecute(() -> {
                    helper.assertBlockProperty(turret, TurretBlock.ONLINE, false);
                    for (int y = 2; y <= 5; y++) {
                        for (int z = 4; z <= 8; z++) {
                            helper.assertBlockPresent(Blocks.STONE, new BlockPos(12, y, z));
                        }
                    }
                })
                .thenSucceed();
    }

    /**
     * Een Sticky EMP Grenade op een robot gaat met de robot mee als die verplaatst wordt, en legt hem neer als hij
     * afgaat (zonder hem kapot te maken).
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "weapons")
    public static void sticky_grenade_rides_along_on_a_robot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GuardRobotEntity robot = RobotGameTests.robot(helper, new BlockPos(10, 2, 6), RobotParts.DEFAULT);
        StickyGrenade grenade = new StickyGrenade(ModEntities.STICKY_EMP_GRENADE.get(), level);
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
                .thenExecute(() -> check(robot.isAlive() && robot.isDown(), "De robot ligt niet neer na de granaat"))
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
        RollingGrenade grenade = new RollingGrenade(ModEntities.ROLLING_EMP_GRENADE.get(), level);
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
                .thenExecute(() -> check(robot.isAlive() && robot.isDown(), "De robot naast de bol ligt niet neer"))
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
        java.util.List<StickyGrenade> thrown = helper.getLevel().getEntitiesOfClass(StickyGrenade.class,
                player.getBoundingBox().inflate(4));
        check(thrown.size() == 1 && thrown.get(0).getOwner() == player, "Er vloog geen granaat van de speler");
        check(new ItemStack(ModItems.EMP_GRENADE.get()).getMaxStackSize() == 16, "Granaten stapelen niet tot 16");
        helper.succeed();
    }

    /**
     * Jouw wens: een kopie van de EMP-granaten als explosief, "zoals de C4 maar net iets minder sterk". Een Sticky Frag
     * Grenade op een stenen muur blaast het blok waar hij op plakt eruit, maar een stenen blok twee hoger of lager blijft
     * staan (de oude C4 haalde dat er wel uit) en obsidiaan ernaast ook. Een koe vlakbij krijgt de klap. Het is geen EMP: de
     * turret ernaast blijft aan.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "weapons")
    public static void frag_grenade_blows_a_block_out_of_a_wall(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int y = 2; y <= 5; y++) {
            for (int z = 4; z <= 8; z++) {
                helper.setBlock(new BlockPos(12, y, z), z == 7 && y == 3 ? Blocks.OBSIDIAN : Blocks.STONE);
            }
        }
        // De koe staat opzij (niet in de weg van de granaat), maar wel dichtbij de plek waar hij ontploft.
        net.minecraft.world.entity.animal.Cow cow = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW,
                new BlockPos(10, 2, 9));
        BlockPos turret = new BlockPos(9, 2, 3);
        helper.setBlock(turret, ModBlocks.ROCKET_TURRET.get());
        generator(helper, turret.below());
        ((TurretBlockEntity) helper.getBlockEntity(turret)).refreshOnline(level);
        StickyGrenade grenade = new StickyGrenade(ModEntities.STICKY_FRAG_GRENADE.get(), level);
        check(grenade.isFrag(), "Een Sticky Frag Grenade weet niet dat hij een frag is");
        Vec3 start = helper.absoluteVec(new Vec3(4.5, 3.4, 6.5));
        grenade.setPos(start.x, start.y, start.z);
        grenade.shoot(1, 0.04, 0, 1.0f, 0);
        level.addFreshEntity(grenade);
        BlockPos[] hit = new BlockPos[1];
        helper.startSequence()
                .thenWaitUntil(() -> check(grenade.isStuck(), "De granaat plakte niet vast"))
                .thenExecute(() -> hit[0] = BlockPos.containing(grenade.position().subtract(grenade.normal().scale(0.3))))
                .thenWaitUntil(() -> check(grenade.isRemoved(), "De granaat ging niet af"))
                .thenExecute(() -> {
                    check(level.getBlockState(hit[0]).isAir(), "Het blok waar hij op plakte is er niet uit: " + hit[0]);
                    BlockPos two = hit[0].offset(0, hit[0].getY() >= helper.absolutePos(new BlockPos(0, 4, 0)).getY() ? -2 : 2, 0);
                    check(level.getBlockState(two).is(Blocks.STONE), "Een blok twee hoger of lager ging ook kapot (te sterk)");
                    helper.assertBlockPresent(Blocks.OBSIDIAN, new BlockPos(12, 3, 7));
                    check(!cow.isAlive() || cow.getHealth() < cow.getMaxHealth(), "De koe vlakbij kreeg geen klap");
                    ((TurretBlockEntity) helper.getBlockEntity(turret)).refreshOnline(level);
                    helper.assertBlockProperty(turret, TurretBlock.ONLINE, true);
                })
                .thenSucceed();
    }

    /** De Frag Grenade (de bol) is een kopie van de EMP-bol: hij stuitert en piept net zo, maar ontploft. */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "weapons")
    public static void frag_ball_explodes_instead_of_emp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // De robot staat binnen de straal van een EMP (5 blokken), maar ver genoeg om de klap te overleven.
        GuardRobotEntity robot = RobotGameTests.robot(helper, new BlockPos(10, 2, 8), RobotParts.DEFAULT);
        RollingGrenade grenade = new RollingGrenade(ModEntities.FRAG_GRENADE.get(), level);
        check(grenade.kind() == RollingGrenade.Kind.FRAG && grenade.beeps(), "De frag-bol is geen frag, of piept niet");
        Vec3 start = helper.absoluteVec(new Vec3(6.5, 2.5, 6.5));
        grenade.setPos(start.x, start.y, start.z);
        grenade.arm(20);
        level.addFreshEntity(grenade);
        net.minecraft.world.entity.animal.Cow cow = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW,
                new BlockPos(8, 2, 6));
        helper.startSequence()
                .thenWaitUntil(() -> check(grenade.isRemoved(), "De frag-bol ging niet af"))
                .thenExecute(() -> {
                    check(!cow.isAlive() || cow.getHealth() < cow.getMaxHealth(), "De koe naast de frag-bol kreeg geen klap");
                    check(!robot.isAlive() || !Emp.isJammed(robot) && !robot.isDown(), "De frag-bol gaf een EMP");
                })
                .thenSucceed();
    }

    // ================================================================ Stap 2c: de smokebom en de flashbang

    /**
     * De rekensom van de rook: een lijn dwars door een wolk is geblokkeerd, een lijn ernaast niet, en van heel dichtbij
     * (minder dan SMOKE_SEE_THROUGH rook ertussen) zie je elkaar nog. Een lijn telt cel voor cel: dwars door één cel met
     * rook is precies één blok rook.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "weapons")
    public static void smoke_blocks_lines_through_the_cloud(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 middle = helper.absoluteVec(new Vec3(10, 3, 10));
        com.senne.imttech.weapons.Smoke.Cloud cloud = com.senne.imttech.weapons.Smoke.add(level, middle, 200);
        com.senne.imttech.weapons.Smoke.Volume volume = cloud.volume();
        double r = ImtConfig.Weapons.SMOKE_RADIUS;
        Vec3 cell = Vec3.atLowerCornerOf(helper.absolutePos(new BlockPos(10, 3, 10)));
        check(Math.abs(volume.inside(r, cell.add(0, 0.5, 0.5), cell.add(1, 0.5, 0.5)) - 1) < 1e-6,
                "Dwars door één cel met rook is niet één blok rook");
        check(Math.abs(volume.inside(r, cell.add(-3, 0.5, 0.5), cell.add(4, 0.5, 0.5)) - 7) < 1e-6,
                "Zeven cellen met rook op een rij tellen niet als zeven blokken");
        check(volume.inside(0, cell.add(-3, 0.5, 0.5), cell.add(4, 0.5, 0.5)) == 0, "Een wolk zonder grootte heeft toch rook");
        helper.startSequence()
                .thenIdle(com.senne.imttech.weapons.Smoke.GROW_TICKS + 1)
                .thenExecute(() -> {
                    check(Math.abs(cloud.radius(level.getGameTime()) - r) < 1e-6, "De wolk groeide niet tot zijn grootte");
                    check(com.senne.imttech.weapons.Smoke.blocks(level, middle.add(-r - 3, 0, 0), middle.add(r + 3, 0, 0)),
                            "Dwars door de wolk kijk je er toch doorheen");
                    check(!com.senne.imttech.weapons.Smoke.blocks(level, middle.add(-r - 3, r + 1, 0),
                            middle.add(r + 3, r + 1, 0)), "Over de wolk heen kijk je er niet langs");
                    check(!com.senne.imttech.weapons.Smoke.blocks(level, middle, middle.add(1, 0, 0)),
                            "Van heel dichtbij in de rook zie je elkaar niet");
                })
                .thenSucceed();
    }

    /**
     * Jouw wens (26 oktober): de rook gaat niet door blokken. Een wolk vlak voor een stenen muur vult de plekken voor de
     * muur, maar niet erachter (ook al ligt dat binnen de grootte van de wolk), en een lijn achter de muur langs is vrij.
     * Een dichte deur in de muur houdt de rook ook tegen; door een open deur stroomt hij naar de andere kant.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "weapons")
    public static void smoke_does_not_go_through_blocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int y = 2; y <= 9; y++) {
            for (int z = 3; z <= 17; z++) {
                helper.setBlock(new BlockPos(12, y, z), Blocks.STONE);
            }
        }
        // Een deur in de muur, dicht (de deur draait om een as langs de muur: dicht ligt het blad dwars op x).
        BlockPos door = new BlockPos(12, 2, 10);
        net.minecraft.world.level.block.state.BlockState closed = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.EAST);
        helper.setBlock(door, closed);
        helper.setBlock(door.above(), closed.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        double r = ImtConfig.Weapons.SMOKE_RADIUS;
        Vec3 middle = helper.absoluteVec(new Vec3(10.5, 2.5, 10.5));
        com.senne.imttech.weapons.Smoke.Cloud cloud = com.senne.imttech.weapons.Smoke.add(level, middle, 200);
        com.senne.imttech.weapons.Smoke.Volume volume = cloud.volume();
        check(volume.has(helper.absolutePos(new BlockPos(11, 2, 10)), r), "Voor de muur hangt geen rook");
        check(volume.has(helper.absolutePos(new BlockPos(10, 6, 10)), r), "Boven de granaat hangt geen rook");
        for (BlockPos behind : new BlockPos[] {new BlockPos(13, 2, 10), new BlockPos(13, 3, 10), new BlockPos(14, 2, 11)}) {
            check(!volume.has(helper.absolutePos(behind), r), "Achter de muur (of de dichte deur) hangt toch rook: " + behind);
        }
        check(!volume.has(helper.absolutePos(new BlockPos(12, 4, 10)), r), "In de muur zelf hangt rook");
        Vec3 behindFrom = helper.absoluteVec(new Vec3(13.5, 2.5, 4.5));
        Vec3 behindTo = helper.absoluteVec(new Vec3(13.5, 2.5, 16.5));
        Vec3 frontFrom = helper.absoluteVec(new Vec3(10.5, 2.5, 4.5));
        Vec3 frontTo = helper.absoluteVec(new Vec3(10.5, 2.5, 16.5));
        check(!com.senne.imttech.weapons.Smoke.blocks(level, behindFrom, behindTo), "Achter de muur langs kijk je niet door");
        // De deur open: de rook stroomt erdoor.
        helper.setBlock(door, closed.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true));
        helper.setBlock(door.above(), closed.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true)
                .setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                        net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
        com.senne.imttech.weapons.Smoke.Volume open = com.senne.imttech.weapons.Smoke.Volume.fill(level, middle);
        check(open.has(helper.absolutePos(new BlockPos(13, 2, 10)), r), "Door de open deur stroomt geen rook");
        check(!open.has(helper.absolutePos(new BlockPos(12, 4, 10)), r), "De rook zit in de muur boven de deur");
        helper.startSequence()
                .thenIdle(com.senne.imttech.weapons.Smoke.GROW_TICKS + 1)
                .thenExecute(() -> {
                    check(com.senne.imttech.weapons.Smoke.blocks(level, frontFrom, frontTo), "Voor de muur kijk je door de rook");
                    check(!com.senne.imttech.weapons.Smoke.blocks(level, behindFrom, behindTo),
                            "Achter de muur langs is de rook toch in de weg");
                })
                .thenSucceed();
    }

    /**
     * Jouw keuze: de rook blokkeert het zicht van alles wat kijkt. Een robot ziet een speler wel, maar niet door een
     * wolk heen; een turret schiet niet op een monster achter de rook, en wel als de rook weg is.
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "weapons")
    public static void smoke_blinds_robots_and_turrets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GuardRobotEntity robot = RobotGameTests.robot(helper, new BlockPos(3, 2, 4), RobotParts.DEFAULT);
        net.minecraft.world.entity.animal.Cow cow = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW,
                new BlockPos(17, 2, 4));
        BlockPos turret = new BlockPos(3, 2, 14);
        helper.setBlock(turret, ModBlocks.ROCKET_TURRET.get());
        generator(helper, turret.below());
        check(robot.hasLineOfSight(cow), "De robot ziet de koe niet eens zonder rook");
        // Twee wolken: midden tussen de robot en de koe, en tussen de turret en waar het monster komt. Na 5 seconden
        // zijn ze weg.
        com.senne.imttech.weapons.Smoke.add(level, helper.absoluteVec(new Vec3(10, 3, 4.5)), 100);
        com.senne.imttech.weapons.Smoke.add(level, helper.absoluteVec(new Vec3(8, 3, 14.5)), 100);
        net.minecraft.world.entity.monster.Husk[] husk = new net.minecraft.world.entity.monster.Husk[1];
        helper.startSequence()
                .thenIdle(com.senne.imttech.weapons.Smoke.GROW_TICKS + 2)
                .thenExecute(() -> {
                    ((TurretBlockEntity) helper.getBlockEntity(turret)).refreshOnline(level);
                    check(!robot.hasLineOfSight(cow), "De robot kijkt door de rook heen");
                    // Het monster pas nu (als de rook er hangt): anders schoot de turret al voordat de wolk groot was.
                    husk[0] = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK, new BlockPos(13, 2, 14));
                })
                .thenIdle(30)
                .thenExecute(() -> check(husk[0].getHealth() == husk[0].getMaxHealth(), "De turret schoot door de rook heen"))
                .thenWaitUntil(() -> check(husk[0].getHealth() < husk[0].getMaxHealth() || !husk[0].isAlive(),
                        "Zonder rook schoot de turret niet"))
                // De twee wolken trekken niet op precies dezelfde tick op: even wachten tot de robot weer kijkt.
                .thenWaitUntil(() -> check(robot.hasLineOfSight(cow), "Na de rook ziet de robot de koe niet meer"))
                .thenSucceed();
    }

    /**
     * Een Smoke Grenade stuitert, begint na de lont te roken (de wolk blokkeert het zicht), blijft liggen zolang hij
     * rookt, en is weg als de rook op is. Hij gaat niet af als een bom (geen gat, geen EMP).
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "weapons")
    public static void smoke_grenade_smokes_and_goes_away(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        float before = ImtConfig.Weapons.SMOKE_SECONDS;
        ImtConfig.Weapons.SMOKE_SECONDS = 4;
        GuardRobotEntity robot = RobotGameTests.robot(helper, new BlockPos(8, 2, 8), RobotParts.DEFAULT);
        RollingGrenade grenade = new RollingGrenade(ModEntities.SMOKE_GRENADE.get(), level);
        Vec3 start = helper.absoluteVec(new Vec3(6.5, 2.5, 6.5));
        grenade.setPos(start.x, start.y, start.z);
        grenade.setSmokeColor(net.minecraft.world.item.DyeColor.RED);
        grenade.arm(10);
        level.addFreshEntity(grenade);
        check(grenade.kind() == RollingGrenade.Kind.SMOKE && !grenade.beeps(), "Een smokebom is geen smokebom, of piept");
        helper.startSequence()
                .thenWaitUntil(() -> check(grenade.isSmoking(), "De smokebom begon niet te roken"))
                .thenIdle(com.senne.imttech.weapons.Smoke.GROW_TICKS)
                .thenExecute(() -> {
                    Vec3 at = grenade.position().add(0, 0.5, 0);
                    check(com.senne.imttech.weapons.Smoke.blocks(level, at.add(-6, 0, 0), at.add(6, 0, 0)),
                            "De rook blokkeert het zicht niet");
                    check(!grenade.isRemoved(), "De smokebom verdween meteen");
                    check(!Emp.isJammed(robot) && !robot.isDown(), "Een smokebom gaf toch een EMP");
                    check(grenade.smokeColor() == net.minecraft.world.item.DyeColor.RED, "De rook is niet rood");
                })
                .thenWaitUntil(() -> check(grenade.isRemoved(), "De smokebom bleef liggen na de rook"))
                .thenExecute(() -> ImtConfig.Weapons.SMOKE_SECONDS = before)
                .thenSucceed();
    }

    /**
     * Jouw keuze: de flashbang "zoals in CS". Wie ernaar kijkt is helemaal verblind, wie wegkijkt een beetje, wie achter
     * een muur staat niets. Een robot merkt niets (alleen spelers).
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "weapons")
    public static void flashbang_blinds_who_looks_at_it(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 flash = helper.absoluteVec(new Vec3(10.5, 2.5, 10.5));
        ServerPlayer looking = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(7, 2, 10), "flash-looker");
        ServerPlayer away = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(13, 2, 10), "flash-away");
        ServerPlayer hidden = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(10, 2, 16), "flash-hidden");
        for (int x = 8; x <= 12; x++) {
            for (int y = 2; y <= 4; y++) {
                helper.setBlock(new BlockPos(x, y, 14), Blocks.STONE);
            }
        }
        looking.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, flash);
        away.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, flash.add(20, 0, 0));
        hidden.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, flash);
        GuardRobotEntity robot = RobotGameTests.robot(helper, new BlockPos(11, 2, 8), RobotParts.DEFAULT);
        java.util.Map<ServerPlayer, Float> blinded = com.senne.imttech.weapons.Flashbang.bang(level, flash, null);
        check(blinded.getOrDefault(looking, 0f) > 0.95f, "Wie ernaar keek is niet helemaal verblind: " + blinded.get(looking));
        float turned = blinded.getOrDefault(away, 0f);
        check(turned > 0.05f && turned < 0.5f, "Wie wegkeek is te veel of helemaal niet verblind: " + turned);
        check(!blinded.containsKey(hidden), "Wie achter een muur stond is toch verblind");
        check(!robot.isDown() && !Emp.isJammed(robot), "De robot merkte iets van de flashbang");
        helper.succeed();
    }

    /** Een gegooide Flashbang stuitert, gaat na zijn lont af en is dan weg (geen gat in de vloer, geen schade). */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "weapons")
    public static void flashbang_goes_off_without_harm(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        net.minecraft.world.entity.animal.Cow cow = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW,
                new BlockPos(8, 2, 8));
        net.minecraft.world.level.block.state.BlockState floor = helper.getBlockState(new BlockPos(6, 1, 6));
        RollingGrenade grenade = new RollingGrenade(ModEntities.FLASHBANG.get(), level);
        Vec3 start = helper.absoluteVec(new Vec3(6.5, 2.5, 6.5));
        grenade.setPos(start.x, start.y, start.z);
        grenade.arm(20);
        level.addFreshEntity(grenade);
        helper.startSequence()
                .thenWaitUntil(() -> check(grenade.isRemoved(), "De flashbang ging niet af"))
                .thenExecute(() -> {
                    check(cow.isAlive() && cow.getHealth() == cow.getMaxHealth(), "De flashbang deed schade");
                    check(helper.getBlockState(new BlockPos(6, 1, 6)) == floor, "De flashbang maakte een gat in de vloer");
                })
                .thenSucceed();
    }

    /** Jouw keuze: gekleurde rook met een kleurstof. Een smokebom en rode kleurstof geven een rode smokebom. */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "weapons")
    public static void dye_makes_coloured_smoke(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var input = net.minecraft.world.item.crafting.CraftingInput.of(2, 1, java.util.List.of(
                new ItemStack(ModItems.SMOKE_GRENADE.get()), new ItemStack(net.minecraft.world.item.Items.RED_DYE)));
        var recipe = level.getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, input, level);
        check(recipe.isPresent(), "Er is geen recept voor een smokebom met kleurstof");
        ItemStack result = recipe.get().value().assemble(input, level.registryAccess());
        check(result.is(ModItems.SMOKE_GRENADE.get())
                        && result.get(com.senne.imttech.registry.ModDataComponents.SMOKE_COLOR.get()) == net.minecraft.world.item.DyeColor.RED,
                "Het recept geeft geen rode smokebom: " + result);
        check(result.getHoverName().getString().contains("Smoke Grenade"), "De naam klopt niet: " + result.getHoverName().getString());
        helper.succeed();
    }

    // ================================================================ Stap 3: energiecellen en de geweren

    /** Een speler (survival) met dit geweer in zijn hand, op deze plek, die naar {@code look} kijkt. */
    private static ServerPlayer shooter(GameTestHelper helper, String name, BlockPos at, com.senne.imttech.weapons.GunType type,
                                        float cell, Vec3 look) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, at, name);
        player.getAbilities().instabuild = false;
        player.getInventory().selected = 0;
        ItemStack gun = new ItemStack(ModItems.laserGun(type));
        if (cell >= 0) {
            com.senne.imttech.weapons.LaserGunItem.setCell(gun, cell);
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, gun);
        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, look);
        com.senne.imttech.weapons.LaserGuns.forgetCooldown(player);
        return player;
    }

    private static com.senne.imttech.weapons.LaserGuns.Result fire(ServerPlayer player, boolean aiming) {
        return com.senne.imttech.weapons.LaserGuns.fire(player, player.getYRot(), player.getXRot(), aiming);
    }

    /**
     * Jouw keuze: energiecellen als magazijn, en ze stapelen niet. Een nieuw geweer is leeg (schieten = klik); herladen
     * pakt de volste cel uit je inventaris en de oude cel komt op zijn plek terug; met alleen legere cellen herlaad je
     * niet. Elk schot kost een stukje van de cel.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "weapons")
    public static void laser_gun_reloads_with_the_fullest_cell(GameTestHelper helper) {
        ServerPlayer player = shooter(helper, "gun-reloader", new BlockPos(4, 2, 4), com.senne.imttech.weapons.GunType.RIFLE, -1,
                helper.absoluteVec(new Vec3(4.5, 2.5, 14.5)));
        check(new ItemStack(ModItems.ENERGY_CELL.get()).getMaxStackSize() == 1, "Energiecellen stapelen");
        check(fire(player, false) == com.senne.imttech.weapons.LaserGuns.Result.EMPTY, "Zonder cel schoot het geweer toch");
        player.getInventory().setItem(5, com.senne.imttech.weapons.EnergyCellItem.withCharge(60));
        player.getInventory().setItem(6, com.senne.imttech.weapons.EnergyCellItem.withCharge(90));
        check(com.senne.imttech.weapons.LaserGuns.startReload(player), "Herladen begon niet");
        check(fire(player, false) == com.senne.imttech.weapons.LaserGuns.Result.RELOADING, "Tijdens herladen kon je schieten");
        helper.startSequence()
                .thenWaitUntil(() -> check(!com.senne.imttech.weapons.LaserGuns.isReloading(player), "Herladen werd niet klaar"))
                .thenExecute(() -> {
                    ItemStack gun = player.getMainHandItem();
                    check(Math.abs(com.senne.imttech.weapons.LaserGunItem.cell(gun) - 90) < 1e-3,
                            "Het geweer kreeg niet de volste cel: " + com.senne.imttech.weapons.LaserGunItem.cell(gun));
                    check(player.getInventory().getItem(6).isEmpty(), "De cel bleef ook in je inventaris");
                    check(Math.abs(com.senne.imttech.weapons.EnergyCellItem.charge(player.getInventory().getItem(5)) - 60) < 1e-3,
                            "De andere cel veranderde");
                    // Alleen nog een legere cel: niet herladen.
                    check(!com.senne.imttech.weapons.LaserGuns.startReload(player), "Herladen met een legere cel");
                    com.senne.imttech.weapons.LaserGuns.forgetCooldown(player);
                    check(fire(player, false) == com.senne.imttech.weapons.LaserGuns.Result.FIRED, "Het geweer schoot niet");
                    float cost = com.senne.imttech.weapons.GunType.RIFLE.cost();
                    check(Math.abs(com.senne.imttech.weapons.LaserGunItem.cell(gun) - (90 - cost)) < 1e-3,
                            "Een schot kostte niet " + cost + "%");
                    check(fire(player, false) == com.senne.imttech.weapons.LaserGuns.Result.TOO_SOON, "Twee schoten in één tick");
                })
                .thenSucceed();
    }

    /**
     * Een schot raakt wat je in je vizier hebt: een koe in haar lijf krijgt de schade van het pistool, een koe in haar
     * hoofd 1,5 keer zoveel (headshot). Een koe achter een muur krijgt niets.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "weapons")
    public static void laser_gun_hits_and_headshots(GameTestHelper helper) {
        net.minecraft.world.entity.animal.Cow body = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW,
                new BlockPos(4, 2, 10));
        net.minecraft.world.entity.animal.Cow head = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW,
                new BlockPos(10, 2, 10));
        net.minecraft.world.entity.animal.Cow walled = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.COW,
                new BlockPos(16, 2, 10));
        for (int y = 2; y <= 4; y++) {
            helper.setBlock(new BlockPos(16, y, 7), Blocks.STONE);
        }
        float damage = com.senne.imttech.weapons.GunType.PISTOL.damage();
        ServerPlayer a = shooter(helper, "gun-body", new BlockPos(4, 2, 4), com.senne.imttech.weapons.GunType.PISTOL, 100,
                body.position().add(0, 0.5, 0));
        ServerPlayer b = shooter(helper, "gun-head", new BlockPos(10, 2, 4), com.senne.imttech.weapons.GunType.PISTOL, 100,
                head.getEyePosition());
        ServerPlayer c = shooter(helper, "gun-wall", new BlockPos(16, 2, 4), com.senne.imttech.weapons.GunType.PISTOL, 100,
                walled.position().add(0, 0.5, 0));
        check(fire(a, true) == com.senne.imttech.weapons.LaserGuns.Result.FIRED, "Het pistool schoot niet");
        fire(b, true);
        fire(c, true);
        check(Math.abs(body.getMaxHealth() - body.getHealth() - Math.min(body.getMaxHealth(), damage)) < 0.01,
                "Een schot in het lijf deed niet " + damage + " schade: " + (body.getMaxHealth() - body.getHealth()));
        float headshot = Math.min(head.getMaxHealth(), damage * ImtConfig.Weapons.HEADSHOT);
        check(!head.isAlive() || Math.abs(head.getMaxHealth() - head.getHealth() - headshot) < 0.01,
                "Een headshot deed niet " + headshot + " schade: " + (head.getMaxHealth() - head.getHealth()));
        check(walled.getHealth() == walled.getMaxHealth(), "Het schot ging door de muur");
        helper.succeed();
    }

    /**
     * De laser-shotgun schiet een waaier van stralen; van dichtbij raken de meeste hetzelfde doelwit, en de schade
     * telt op (in één klap).
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "weapons")
    public static void laser_shotgun_spreads_its_rays(GameTestHelper helper) {
        net.minecraft.world.entity.monster.Husk husk = helper.spawnWithNoFreeWill(net.minecraft.world.entity.EntityType.HUSK,
                new BlockPos(6, 2, 7));
        husk.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(200);
        husk.setHealth(200);
        ServerPlayer player = shooter(helper, "gun-shotgun", new BlockPos(6, 2, 4), com.senne.imttech.weapons.GunType.SHOTGUN,
                100, husk.position().add(0, 1.0, 0));
        check(fire(player, false) == com.senne.imttech.weapons.LaserGuns.Result.FIRED, "De shotgun schoot niet");
        float lost = husk.getMaxHealth() - husk.getHealth();
        check(lost > com.senne.imttech.weapons.GunType.SHOTGUN.damage() * 2,
                "Van dichtbij raakten te weinig stralen: " + lost + " schade");
        check(com.senne.imttech.weapons.GunType.SHOTGUN.rays() > 1, "De shotgun heeft maar één straal");
        helper.succeed();
    }

    /** Het Charging Station laadt een lege energiecel op (in het vak van de bril). */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "weapons")
    public static void charging_station_charges_a_cell(GameTestHelper helper) {
        BlockPos at = new BlockPos(5, 2, 5);
        helper.setBlock(at, ModBlocks.CHARGING_STATION.get());
        var station = (com.senne.imttech.station.ChargingStationBlockEntity) helper.getBlockEntity(at);
        ItemStack cell = new ItemStack(ModItems.ENERGY_CELL.get());
        check(station.canPlaceItem(com.senne.imttech.station.ChargingStationBlockEntity.SLOT_GLASSES, cell),
                "Een energiecel past niet in het station");
        station.setItem(com.senne.imttech.station.ChargingStationBlockEntity.SLOT_GLASSES, cell);
        station.setItem(com.senne.imttech.station.ChargingStationBlockEntity.SLOT_FUEL,
                new ItemStack(net.minecraft.world.item.Items.REDSTONE_BLOCK, 64));
        helper.startSequence()
                .thenWaitUntil(() -> check(com.senne.imttech.weapons.EnergyCellItem.charge(
                                station.getItem(com.senne.imttech.station.ChargingStationBlockEntity.SLOT_GLASSES)) > 10,
                        "De cel laadt niet op"))
                .thenSucceed();
    }

    // ================================================================ De Remote EMP

    /**
     * Jouw wens: "een remote getriggerde EMP ... die trigger je met een item in de inventory". Plak je een Remote EMP
     * tegen een muur met een EMP-trigger bij je, dan hoort hij bij die trigger. Rechtsklik met de trigger: hij gaat af
     * (een gewone EMP-puls: de robot ligt neer, de turret staat uit), de muur blijft heel, en hij is weg (eenmalig). Een
     * Remote EMP van een andere trigger blijft gewoon zitten.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "weapons")
    public static void remote_emp_goes_off_with_its_trigger(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        for (int y = 2; y <= 4; y++) {
            for (int z = 3; z <= 12; z++) {
                helper.setBlock(new BlockPos(12, y, z), Blocks.STONE);
            }
        }
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 2, 6), "emp-remote");
        player.getAbilities().instabuild = false;
        ItemStack trigger = new ItemStack(ModItems.EMP_TRIGGER.get());
        player.getInventory().setItem(8, trigger);
        // De Remote EMP plakken tegen de westkant van de muur (zoals een speler: rechtsklik op die kant).
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMOTE_EMP.get(), 2));
        BlockPos wallBlock = helper.absolutePos(new BlockPos(12, 3, 6));
        Vec3 hitAt = Vec3.atCenterOf(wallBlock).add(-0.5, 0, 0);
        ModItems.REMOTE_EMP.get().useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND,
                new net.minecraft.world.phys.BlockHitResult(hitAt, Direction.WEST, wallBlock, false)));
        BlockPos empPos = new BlockPos(11, 3, 6);
        check(helper.getBlockState(empPos).is(ModBlocks.REMOTE_EMP.get()), "De Remote EMP plakte niet tegen de muur");
        check(com.senne.imttech.weapons.RemoteChargeBlock.outward(helper.getBlockState(empPos)) == Direction.WEST,
                "De Remote EMP wijst niet van de muur af");
        var emp = (com.senne.imttech.weapons.RemoteChargeBlockEntity) helper.getBlockEntity(empPos);
        check(emp.trigger() != null && emp.trigger().equals(com.senne.imttech.weapons.TriggerItem.data(trigger).id()),
                "De Remote EMP hoort niet bij de trigger in je inventaris");
        // Een tweede Remote EMP, van een andere trigger.
        BlockPos otherPos = new BlockPos(11, 3, 11);
        helper.setBlock(otherPos, ModBlocks.REMOTE_EMP.get().defaultBlockState()
                .setValue(com.senne.imttech.weapons.RemoteChargeBlock.FACING, Direction.WEST));
        ItemStack otherTrigger = new ItemStack(ModItems.EMP_TRIGGER.get());
        com.senne.imttech.weapons.TriggerItem.link(level, helper.absolutePos(otherPos),
                (com.senne.imttech.weapons.RemoteChargeBlockEntity) helper.getBlockEntity(otherPos), otherTrigger);
        // Een Remote Frag van een Frag Trigger ernaast: een EMP Trigger laat die niet afgaan.
        BlockPos fragPos = new BlockPos(11, 4, 9);
        helper.setBlock(fragPos, ModBlocks.REMOTE_FRAG.get().defaultBlockState()
                .setValue(com.senne.imttech.weapons.RemoteChargeBlock.FACING, Direction.WEST));
        GuardRobotEntity robot = RobotGameTests.robot(helper, new BlockPos(9, 2, 7), RobotParts.DEFAULT);
        BlockPos turret = new BlockPos(8, 2, 4);
        helper.setBlock(turret, ModBlocks.ROCKET_TURRET.get());
        generator(helper, turret.below());
        ((TurretBlockEntity) helper.getBlockEntity(turret)).refreshOnline(level);
        helper.assertBlockProperty(turret, TurretBlock.ONLINE, true);
        check(!robot.isDown(), "De robot lag al neer");
        check(com.senne.imttech.weapons.TriggerItem.fire(level, player, trigger).fired() == 1, "De trigger liet niet één EMP afgaan");
        helper.startSequence()
                .thenWaitUntil(() -> check(helper.getBlockState(empPos).isAir(), "De Remote EMP ging niet af"))
                .thenExecute(() -> {
                    check(robot.isDown(), "De robot ligt niet neer na de Remote EMP");
                    helper.assertBlockProperty(turret, TurretBlock.ONLINE, false);
                    helper.assertBlockPresent(Blocks.STONE, new BlockPos(12, 3, 6));
                    check(helper.getBlockState(otherPos).is(ModBlocks.REMOTE_EMP.get()), "De Remote EMP van de andere trigger ging ook af");
                    check(helper.getBlockState(fragPos).is(ModBlocks.REMOTE_FRAG.get()), "De EMP liet een Remote Frag afgaan");
                    check(com.senne.imttech.weapons.TriggerItem.data(trigger).charges().isEmpty(),
                            "De trigger onthoudt een EMP die al afging");
                })
                .thenSucceed();
    }
}
