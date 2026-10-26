package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.bot.BotBoard;
import com.senne.imttech.bot.BotModule;
import com.senne.imttech.bot.BotModuleItem;
import com.senne.imttech.bot.BotNetwork;
import com.senne.imttech.bot.SolderingIronItem;
import com.senne.imttech.bot.CircuitBoardItem;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.network.OsActionPayload;
import com.senne.imttech.network.RobotActionPayload;
import com.senne.imttech.network.SolderPayload;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.robot.GuardRobotEntity;
import com.senne.imttech.robot.GuardRobotItem;
import com.senne.imttech.robot.RobotAssemblerBlockEntity;
import com.senne.imttech.robot.RobotData;
import com.senne.imttech.robot.RobotDockBlock;
import com.senne.imttech.robot.RobotDockBlockEntity;
import com.senne.imttech.robot.RobotPartItem;
import com.senne.imttech.robot.RobotParts;
import com.senne.imttech.robot.RobotSlot;
import com.senne.imttech.robot.RobotState;
import com.senne.imttech.robot.RobotStyle;
import com.senne.imttech.robot.Robots;
import com.senne.imttech.security.BaseTrust;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.SecurityOs;
import com.senne.imttech.station.ChargerBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de guard robots (hoofdstuk 21): onderdelen, wat ze doen, opslaan, en wie hem mag instellen.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class RobotGameTests {
    private RobotGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Een robot met deze onderdelen, stil op de testvloer. */
    static GuardRobotEntity robot(GameTestHelper helper, BlockPos pos, RobotParts parts) {
        GuardRobotEntity robot = helper.spawnWithNoFreeWill(ModEntities.GUARD_ROBOT.get(), pos);
        robot.setParts(parts);
        robot.setHealth(robot.getMaxHealth());
        return robot;
    }

    private static boolean near(double a, double b) {
        return Math.abs(a - b) < 1.0e-4;
    }

    /**
     * Elke mix van vier onderdelen past in één getal en komt er hetzelfde uit; {@code with} verandert alleen
     * het ene onderdeel.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void robot_parts_pack_every_mix(GameTestHelper helper) {
        for (int bits = 0; bits < 256; bits++) {
            RobotParts parts = RobotParts.unpack(bits);
            check(parts.pack() == bits, "Onderdelen " + bits + " kwamen terug als " + parts.pack());
            check(RobotParts.unpack(parts.pack()).equals(parts), "Uitpakken gaf andere onderdelen: " + parts);
        }
        RobotParts mixed = RobotParts.DEFAULT.with(RobotSlot.HEAD, RobotStyle.COMMANDO).with(RobotSlot.LEGS, RobotStyle.TRAINER);
        check(mixed.head() == RobotStyle.COMMANDO && mixed.body() == RobotStyle.BATTLE
                && mixed.arms() == RobotStyle.BATTLE && mixed.legs() == RobotStyle.TRAINER, "with() veranderde het verkeerde onderdeel: " + mixed);
        check(mixed.get(RobotSlot.HEAD) == RobotStyle.COMMANDO && mixed.get(RobotSlot.BODY) == RobotStyle.BATTLE,
                "get() gaf het verkeerde onderdeel");
        helper.succeed();
    }

    /**
     * Het lijf geeft leven en pantser, de benen de snelheid (getallen uit de config). Een ander lijf houdt
     * hem even vol als hij was: halfvol blijft halfvol.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void robot_parts_give_health_armor_and_speed(GameTestHelper helper) {
        RobotParts heavy = new RobotParts(RobotStyle.BATTLE, RobotStyle.SUPER_BATTLE, RobotStyle.BATTLE, RobotStyle.COMMANDO);
        GuardRobotEntity robot = robot(helper, new BlockPos(2, 2, 2), heavy);
        int superBattle = RobotStyle.SUPER_BATTLE.ordinal();
        check(near(robot.getMaxHealth(), ImtConfig.Robots.BODY_HEALTH[superBattle]),
                "Leven " + robot.getMaxHealth() + " is niet dat van een Super Battle-lijf");
        check(near(robot.getAttributeValue(Attributes.ARMOR), ImtConfig.Robots.BODY_ARMOR[superBattle]),
                "Pantser " + robot.getAttributeValue(Attributes.ARMOR) + " is niet dat van een Super Battle-lijf");
        check(near(robot.getAttributeBaseValue(Attributes.MOVEMENT_SPEED), ImtConfig.Robots.LEGS_SPEED[RobotStyle.COMMANDO.ordinal()]),
                "Snelheid komt niet van de Commando-benen");
        check(near(robot.getHealth(), robot.getMaxHealth()), "Hij is niet vol");

        robot.setHealth(robot.getMaxHealth() / 2);
        robot.setParts(heavy.with(RobotSlot.BODY, RobotStyle.BATTLE));
        float battle = ImtConfig.Robots.BODY_HEALTH[RobotStyle.BATTLE.ordinal()];
        check(near(robot.getMaxHealth(), battle), "Na een Battle-lijf is zijn leven " + robot.getMaxHealth());
        check(near(robot.getHealth(), battle / 2), "Halfvol werd " + robot.getHealth() + " van " + robot.getMaxHealth());
        helper.succeed();
    }

    /**
     * Opslaan en laden: zijn onderdelen, zijn eigenaar en zijn leven blijven. Staat er intussen een ander getal
     * in de config, dan geldt na het laden het nieuwe.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "robot_config")
    public static void robot_saves_parts_and_owner(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 1), "robot-owner");
        RobotParts parts = new RobotParts(RobotStyle.TRAINER, RobotStyle.COMMANDO, RobotStyle.SUPER_BATTLE, RobotStyle.BATTLE);
        GuardRobotEntity robot = robot(helper, new BlockPos(3, 2, 3), parts);
        robot.setOwner(owner);
        robot.setHealth(10.0f);
        CompoundTag saved = robot.saveWithoutId(new CompoundTag());

        int commando = RobotStyle.COMMANDO.ordinal();
        float before = ImtConfig.Robots.BODY_HEALTH[commando];
        ImtConfig.Robots.BODY_HEALTH[commando] = 50.0f;
        try {
            GuardRobotEntity loaded = ModEntities.GUARD_ROBOT.get().create(helper.getLevel());
            check(loaded != null, "Geen robot gemaakt");
            loaded.load(saved);
            check(loaded.parts().equals(parts), "Onderdelen na laden: " + loaded.parts());
            check(owner.getUUID().equals(loaded.owner()) && "robot-owner".equals(loaded.ownerName()),
                    "Eigenaar na laden: " + loaded.ownerName());
            check(near(loaded.getHealth(), 10.0), "Leven na laden: " + loaded.getHealth());
            check(near(loaded.getMaxHealth(), 50.0), "Na laden geldt de nieuwe config niet: " + loaded.getMaxHealth());
            loaded.discard();
        } finally {
            ImtConfig.Robots.BODY_HEALTH[commando] = before;
        }
        helper.succeed();
    }

    /** Wie hem neerzette, mag hem instellen; een vreemde buiten elke basis niet (dezelfde regel als de laserzender). */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void robot_only_owner_may_edit(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 1), "robot-boss");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 2, 5), "robot-stranger");
        GuardRobotEntity robot = robot(helper, new BlockPos(3, 2, 3), RobotParts.DEFAULT);
        robot.setOwner(owner);
        check(BaseTrust.mayEdit(owner, robot), "De eigenaar mag zijn robot niet instellen");
        check(!BaseTrust.mayEdit(stranger, robot), "Een vreemde mag de robot instellen");
        check(owner.getUUID().equals(robot.owner()), "De robot werd van een ander");
        helper.succeed();
    }

    /**
     * Alle 16 onderdelen bestaan als item ("commando_head" tot "super_battle_legs"), elk met een recept. Ze stapelen
     * niet (16 oktober: "maak al de robot part non stackble").
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void robot_parts_are_items_with_recipes(GameTestHelper helper) {
        for (RobotSlot slot : RobotSlot.values()) {
            for (RobotStyle style : RobotStyle.values()) {
                RobotPartItem item = ModItems.robotPart(slot, style);
                String id = style.id() + "_" + slot.id();
                check(BuiltInRegistries.ITEM.getKey(item).equals(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, id)),
                        "Het onderdeel " + id + " heet " + BuiltInRegistries.ITEM.getKey(item));
                check(item.slot() == slot && item.style() == style, id + " weet niet wat het is");
                check(new ItemStack(item).getMaxStackSize() == 1, id + " stapelt tot " + new ItemStack(item).getMaxStackSize());
                ItemStack made = helper.getLevel().getRecipeManager()
                        .byKey(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, id))
                        .map(recipe -> recipe.value().getResultItem(helper.getLevel().registryAccess()))
                        .orElse(ItemStack.EMPTY);
                check(made.is(item), "Geen recept dat " + id + " maakt");
            }
        }
        helper.succeed();
    }

    /**
     * Een robot die echt kapot gaat, laat zijn onderdelen vallen met de kans uit de config: bij 1 alle vier,
     * bij 0 niets.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "robot_config")
    public static void robot_destroyed_drops_parts(GameTestHelper helper) {
        float before = ImtConfig.Robots.PART_SURVIVE_CHANCE;
        try {
            RobotParts parts = new RobotParts(RobotStyle.COMMANDO, RobotStyle.TRAINER, RobotStyle.BATTLE, RobotStyle.SUPER_BATTLE);
            ImtConfig.Robots.PART_SURVIVE_CHANCE = 1.0f;
            robot(helper, new BlockPos(2, 2, 2), parts).kill();
            for (RobotSlot slot : RobotSlot.values()) {
                RobotPartItem part = ModItems.robotPart(slot, parts.get(slot));
                check(dropped(helper, new BlockPos(2, 2, 2), part) == 1, "Na kapot lag er geen " + part + " op de grond");
            }
            ImtConfig.Robots.PART_SURVIVE_CHANCE = 0.0f;
            robot(helper, new BlockPos(8, 2, 8), parts).kill();
            for (RobotSlot slot : RobotSlot.values()) {
                check(dropped(helper, new BlockPos(8, 2, 8), ModItems.robotPart(slot, parts.get(slot))) == 0,
                        "Bij kans 0 viel er toch een onderdeel");
            }
        } finally {
            ImtConfig.Robots.PART_SURVIVE_CHANCE = before;
        }
        helper.succeed();
    }

    /**
     * Een printplaat heeft altijd 8 pinnen (een nieuwe is leeg), wordt goed bewaard (met wat erop zit en de
     * soldeerbruggen), en heeft een recept.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void robot_circuit_board_has_8_pins(GameTestHelper helper) {
        ItemStack fresh = new ItemStack(ModItems.ROBOT_CIRCUIT_BOARD.get());
        BotBoard empty = CircuitBoardItem.board(fresh);
        check(empty.pins().size() == BotBoard.PINS && empty.used() == 0 && empty.bridges() == 0, "Een nieuwe printplaat is niet leeg");
        check(empty.pin(1).isEmpty() && empty.pin(8).isEmpty() && empty.pin(9).isEmpty() && empty.pin(0).isEmpty(),
                "Pinnen buiten 1-8 geven iets");

        BotBoard soldered = new BotBoard(List.of("scan", "", "laser"), 0b10);
        check(soldered.pins().size() == BotBoard.PINS, "Een korte lijst werd geen 8 pinnen");
        check(soldered.pin(1).equals("scan") && soldered.pin(3).equals("laser") && soldered.used() == 2, "Pinnen kloppen niet");
        Tag saved = BotBoard.CODEC.encodeStart(NbtOps.INSTANCE, soldered).getOrThrow();
        BotBoard loaded = BotBoard.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        check(loaded.equals(soldered), "Na bewaren en laden: " + loaded);
        fresh.set(ModDataComponents.BOT_BOARD.get(), soldered);
        check(CircuitBoardItem.board(fresh).equals(soldered), "De printplaat op het item is niet wat we erop zetten");

        ItemStack made = helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "robot_circuit_board"))
                .map(recipe -> recipe.value().getResultItem(helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
        check(made.is(ModItems.ROBOT_CIRCUIT_BOARD.get()), "Geen recept voor de printplaat");
        helper.succeed();
    }

    /**
     * De Robot Assembler: zonder printplaat lukt Assemble niet en zegt hij wat er ontbreekt; met alles erin
     * maakt hij een robot-item met die onderdelen en die printplaat (er gaat er van elk één af). Take apart
     * kan pas als de andere vakjes leeg zijn, en geeft alles terug (de printplaat met wat erop zit).
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void robot_assembler_builds_and_takes_apart(GameTestHelper helper) {
        BlockPos pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos, ModBlocks.ROBOT_ASSEMBLER.get());
        RobotAssemblerBlockEntity assembler = (RobotAssemblerBlockEntity) helper.getBlockEntity(pos);
        SimpleContainer items = assembler.items();
        check(!assembler.assemble(), "Een robot uit niets");

        RobotParts parts = new RobotParts(RobotStyle.COMMANDO, RobotStyle.SUPER_BATTLE, RobotStyle.TRAINER, RobotStyle.BATTLE);
        for (RobotSlot slot : RobotSlot.values()) {
            items.setItem(slot.ordinal(), new ItemStack(ModItems.robotPart(slot, parts.get(slot))));
        }
        check(RobotAssemblerBlockEntity.missing(items).equals(List.of("board")),
                "Zonder printplaat ontbreekt: " + RobotAssemblerBlockEntity.missing(items));
        check(!assembler.assemble(), "Een robot zonder printplaat");
        check(!RobotAssemblerBlockEntity.fits(RobotSlot.HEAD.ordinal(), new ItemStack(ModItems.robotPart(RobotSlot.LEGS, RobotStyle.BATTLE))),
                "Benen passen in het vakje van het hoofd");

        BotBoard soldered = new BotBoard(List.of("scan"), 0);
        ItemStack board = new ItemStack(ModItems.ROBOT_CIRCUIT_BOARD.get());
        board.set(ModDataComponents.BOT_BOARD.get(), soldered);
        items.setItem(RobotAssemblerBlockEntity.BOARD, board);
        check(assembler.assemble(), "Assemble lukte niet met alles erin");
        ItemStack robot = items.getItem(RobotAssemblerBlockEntity.ROBOT);
        check(robot.is(ModItems.GUARD_ROBOT.get()), "Er ligt geen robot in het robotvakje");
        check(GuardRobotItem.data(robot).robotParts().equals(parts), "De robot heeft andere onderdelen: " + GuardRobotItem.data(robot));
        check(GuardRobotItem.data(robot).board().equals(soldered), "De robot heeft een andere printplaat");
        check(items.getItem(RobotSlot.ARMS.ordinal()).isEmpty() && items.getItem(RobotSlot.HEAD.ordinal()).isEmpty()
                && items.getItem(RobotAssemblerBlockEntity.BOARD).isEmpty(), "Er ging niet van elk onderdeel één af");

        // Onderdelen stapelen niet (16 oktober): een tweede paar armen leg je er los bij.
        items.setItem(RobotSlot.ARMS.ordinal(), new ItemStack(ModItems.robotPart(RobotSlot.ARMS, RobotStyle.TRAINER)));
        check(!assembler.assemble(), "Een tweede robot terwijl het robotvakje vol is");
        check(!assembler.takeApart(), "Uit elkaar terwijl er nog armen in hun vakje liggen");
        items.setItem(RobotSlot.ARMS.ordinal(), ItemStack.EMPTY);
        check(assembler.takeApart(), "Take apart lukte niet");
        for (RobotSlot slot : RobotSlot.values()) {
            check(items.getItem(slot.ordinal()).is(ModItems.robotPart(slot, parts.get(slot))), "Na uit elkaar halen mist: " + slot);
        }
        check(CircuitBoardItem.board(items.getItem(RobotAssemblerBlockEntity.BOARD)).equals(soldered),
                "De printplaat kwam terug zonder zijn module");
        check(items.getItem(RobotAssemblerBlockEntity.ROBOT).isEmpty(), "De robot ligt er nog");
        helper.succeed();
    }

    /**
     * Een Guard Robot-item op een blok: de robot staat erbovenop, met de onderdelen, printplaat en naam uit het
     * item, en wie hem neerzette is zijn eigenaar. In survival is het item dan op.
     */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void robot_item_places_robot(GameTestHelper helper) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 1), "robot-placer");
        player.getAbilities().instabuild = false; // een testspeler mag anders alles houden, zoals in creative
        BlockPos floor = new BlockPos(3, 2, 3);
        helper.setBlock(floor, Blocks.STONE);
        RobotParts parts = new RobotParts(RobotStyle.TRAINER, RobotStyle.TRAINER, RobotStyle.COMMANDO, RobotStyle.SUPER_BATTLE);
        BotBoard soldered = new BotBoard(List.of("", "laser"), 0);
        ItemStack stack = GuardRobotItem.create(new RobotData(parts, soldered, "Guard 7"));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = helper.absolutePos(floor);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(Direction.UP, 0.5), Direction.UP, abs, false);
        check(stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "Neerzetten lukte niet");

        List<GuardRobotEntity> robots = helper.getEntities(ModEntities.GUARD_ROBOT.get(), floor.above(), 1.0);
        check(robots.size() == 1, "Er staan " + robots.size() + " robots op het blok");
        GuardRobotEntity robot = robots.get(0);
        check(robot.blockPosition().equals(abs.above()), "De robot staat niet bovenop het blok");
        check(robot.parts().equals(parts) && robot.board().equals(soldered), "De robot is niet wat er in het item zat");
        check(robot.hasCustomName() && "Guard 7".equals(robot.getCustomName().getString()), "Zijn naam ging verloren");
        check(player.getUUID().equals(robot.owner()), "Wie hem neerzette is niet de eigenaar");
        check(robot.toData().equals(new RobotData(parts, soldered, "Guard 7")), "Terug naar een item geeft iets anders");
        check(player.getMainHandItem().isEmpty(), "In survival is het item niet op");
        helper.succeed();
    }

    // ================================================================ Stap 6: het Robot Dock en de batterij

    /**
     * Een robot op zijn dock (neergezet met het item, dus gekoppeld): op een dock met stroom laadt hij, op een
     * dock zonder stroom niet. Zijn batterij leeg = NO_POWER; geladen = weer STANDBY (hij staat uit).
     */
    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void robot_dock_charges_from_the_grid(GameTestHelper helper) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 1, 12), "dock-owner");
        BlockPos powered = new BlockPos(4, 1, 4);
        BlockPos dark = new BlockPos(12, 1, 4);
        helper.setBlock(powered.west(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(powered.west())).fill(5_000);
        GuardRobotEntity charging = dockedRobot(helper, player, powered, RobotParts.DEFAULT);
        GuardRobotEntity waiting = dockedRobot(helper, player, dark, RobotParts.DEFAULT);
        check(charging.home() != null && charging.home().equals(helper.absolutePos(powered)) && charging.isOnDock(),
                "De robot is niet thuis op het dock waar hij op gezet werd");
        check(!charging.isActive() && charging.state() == RobotState.STANDBY, "Een nieuwe robot staat aan: " + charging.state());
        charging.setBattery(0);
        waiting.setBattery(0);
        check(charging.state() == RobotState.NO_POWER, "Een lege batterij is niet NO_POWER");
        helper.runAfterDelay(40, () -> {
            check(charging.battery() >= 30 * ImtConfig.Robots.DOCK_CHARGE_RATE,
                    "Op een dock met stroom laadde hij maar tot " + charging.battery());
            check(charging.state() == RobotState.STANDBY, "Geladen is hij niet weer STANDBY maar " + charging.state());
            check(waiting.battery() == 0 && waiting.state() == RobotState.NO_POWER, "Zonder stroom laadde hij toch");
            check(charging.isCharging() && !waiting.isCharging(), "Laadt hij nu (dan zoemt hij)? Klopt niet");
            helper.succeed();
        });
    }

    /**
     * Aan in zijn dock: hij stapt er naar voren uit. Uit: hij loopt terug, gaat precies in het midden staan met
     * zijn gezicht naar de open kant, en je kunt hem daar niet wegduwen.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void robot_on_steps_out_and_off_walks_home(GameTestHelper helper) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 1, 20), "dock-walker");
        BlockPos dock = new BlockPos(8, 1, 12);
        GuardRobotEntity robot = dockedRobot(helper, player, dock, RobotParts.DEFAULT);
        BlockPos home = helper.absolutePos(dock);
        robot.setActive(true);
        check(robot.state() == RobotState.ACTIVE, "Aangezet is hij niet ACTIVE");
        helper.runAfterDelay(80, () -> {
            check(!robot.isOnDock(), "Aangezet bleef hij in zijn dock staan");
            Direction front = helper.getLevel().getBlockState(home).getValue(RobotDockBlock.FACING);
            BlockPos expected = home.relative(front, Math.round(ImtConfig.Robots.STEP_OUT));
            check(robot.blockPosition().distManhattan(expected) <= 1, "Hij stapte niet naar voren uit: hij staat op "
                    + robot.blockPosition() + ", verwacht " + expected);
            robot.setActive(false);
            helper.succeedWhen(() -> {
                check(robot.isOnDock(), "Uitgezet is hij (nog) niet terug in zijn dock");
                check(Math.abs(robot.getX() - (home.getX() + 0.5)) < 0.01 && Math.abs(robot.getZ() - (home.getZ() + 0.5)) < 0.01,
                        "Hij staat niet in het midden van zijn dock");
                check(Math.abs(Mth.wrapDegrees(robot.getYRot() - front.toYRot())) < 1,
                        "Hij kijkt niet naar de open kant van zijn dock");
                check(!robot.isPushable(), "In zijn dock kun je hem nog wegduwen");
            });
        });
    }

    /** Aan en zonder dock: zijn batterij loopt leeg, en leeg staat hij stil (NO_POWER). */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void robot_runs_out_of_battery(GameTestHelper helper) {
        GuardRobotEntity robot = robot(helper, new BlockPos(4, 1, 4), RobotParts.DEFAULT);
        robot.setActive(true);
        robot.setBattery(4 * ImtConfig.Robots.DRAIN_ON + 0.01);
        helper.runAfterDelay(10, () -> {
            check(robot.battery() == 0 && robot.state() == RobotState.NO_POWER,
                    "Na 10 ticks aan zou zijn batterij leeg moeten zijn: " + robot.battery() + " " + robot.state());
            check(robot.toData().battery() == 0.0f, "Opgepakt zou hij een lege batterij hebben");
            helper.succeed();
        });
    }

    // ================================================================ Stap 7: BotNetwork en het robotscherm

    /**
     * Twee robots van dezelfde eigenaar heten "Guard 1" en "Guard 2"; BotNetwork kent ze (eigenaar, dock), en
     * onthoudt ze na opslaan en laden. Opgepakt: het item heeft zijn naam, zijn dock is weer vrij en hij is weg
     * uit BotNetwork. Kapot: ook weg.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "robot_network")
    public static void robot_network_names_and_remembers(GameTestHelper helper) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 1, 8), "net-owner");
        player.getAbilities().instabuild = false;
        GuardRobotEntity first = dockedRobot(helper, player, new BlockPos(4, 1, 4), RobotParts.DEFAULT);
        GuardRobotEntity second = dockedRobot(helper, player, new BlockPos(8, 1, 4), RobotParts.DEFAULT);
        check("Guard 1".equals(first.getCustomName().getString()) && "Guard 2".equals(second.getCustomName().getString()),
                "De namen zijn " + first.getCustomName().getString() + " en " + second.getCustomName().getString());
        BotNetwork network = BotNetwork.get(helper.getLevel());
        BotNetwork.Entry entry = network.get(first.getUUID());
        check(entry != null && "Guard 1".equals(entry.name()) && player.getUUID().equals(entry.owner())
                && helper.absolutePos(new BlockPos(4, 1, 4)).equals(entry.home()), "BotNetwork kent de eerste robot niet goed: " + entry);
        check(network.ownedBy(player.getUUID()).size() == 2, "BotNetwork heeft niet twee robots van deze speler");
        check(network.nameTaken(player.getUUID(), "guard 2", first.getUUID()), "Guard 2 is niet bezet (hoofdletters tellen niet)");

        BotNetwork loaded = BotNetwork.load(network.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        check(entry.equals(loaded.get(first.getUUID())), "Na opslaan en laden: " + loaded.get(first.getUUID()));

        Robots.pickUp(player, first);
        check(network.get(first.getUUID()) == null, "Opgepakt staat hij nog in BotNetwork");
        check(((RobotDockBlockEntity) helper.getBlockEntity(new BlockPos(4, 1, 4))).robot() == null, "Zijn dock is niet vrij");
        boolean inInventory = false;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(ModItems.GUARD_ROBOT.get()) && "Guard 1".equals(GuardRobotItem.data(stack).name())) {
                inInventory = true;
            }
        }
        check(inInventory, "Guard 1 zit niet als item in de rugzak");
        second.kill();
        check(network.get(second.getUUID()) == null, "Kapot staat hij nog in BotNetwork");
        helper.succeed();
    }

    /**
     * Het robotscherm: een vreemde kan niets (aanzetten, hernoemen); de eigenaar wel. Een naam die een andere robot
     * van hem al heeft, mag niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "robot_network")
    public static void robot_screen_only_for_owner(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 1, 8), "screen-owner");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(7, 1, 8), "screen-stranger");
        GuardRobotEntity first = dockedRobot(helper, owner, new BlockPos(4, 1, 4), RobotParts.DEFAULT);
        GuardRobotEntity second = dockedRobot(helper, owner, new BlockPos(8, 1, 4), RobotParts.DEFAULT);

        Robots.handleAction(stranger, new RobotActionPayload(first.getId(), RobotActionPayload.Op.TURN_ON, ""));
        Robots.handleAction(stranger, new RobotActionPayload(first.getId(), RobotActionPayload.Op.RENAME, "Mine now"));
        check(!first.isActive() && "Guard 1".equals(first.getCustomName().getString()), "Een vreemde kon de robot instellen");

        Robots.handleAction(owner, new RobotActionPayload(first.getId(), RobotActionPayload.Op.TURN_ON, ""));
        check(first.isActive(), "De eigenaar kon hem niet aanzetten");
        Robots.handleAction(owner, new RobotActionPayload(first.getId(), RobotActionPayload.Op.RENAME, "guard 2"));
        check("Guard 1".equals(first.getCustomName().getString()), "Hij kreeg de naam van een andere robot");
        Robots.handleAction(owner, new RobotActionPayload(first.getId(), RobotActionPayload.Op.RENAME, "  Door guard  "));
        check("Door guard".equals(first.getCustomName().getString()), "Hernoemen lukte niet: " + first.getCustomName().getString());
        check("Door guard".equals(BotNetwork.get(helper.getLevel()).get(first.getUUID()).name()), "BotNetwork kent de nieuwe naam niet");
        Robots.handleAction(owner, new RobotActionPayload(second.getId(), RobotActionPayload.Op.RENAME, ""));
        check("Guard 2".equals(second.getCustomName().getString()), "Een lege naam werd aanvaard");
        helper.succeed();
    }

    // ================================================================ Stap 8: de app Robots

    /**
     * De app Robots: een robot in het gebied van de kern staat in de lijst (naam, toestand, batterij, dock); van
     * de desktop zet je hem aan en geef je hem een andere naam. Een rare robot-id doet niets.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "robot_desktop")
    public static void robot_desktop_app(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "desk-owner");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos coreAbs = helper.absolutePos(new BlockPos(2, 3, 2));
        GuardRobotEntity robot = dockedRobot(helper, owner, new BlockPos(8, 1, 8), RobotParts.DEFAULT);

        // De kern meldt zich pas een tick na het neerzetten aan; de robot meldt zich om de seconde opnieuw.
        helper.runAfterDelay(25, () -> {
            // Op zijn nummer zoeken: robots van eerdere tests blijven in de testwereld staan, en die horen bij de
            // dichtstbijzijnde kern (soms deze).
            CompoundTag row = row(helper, core, robot);
            check(row != null, "De app ziet de robot niet");
            check("Guard 1".equals(row.getString("n")) && row.getBoolean("loaded") && row.getBoolean("dock")
                    && !row.getBoolean("on") && "standby".equals(row.getString("st")) && row.getInt("bat") == 100,
                    "De rij van de robot klopt niet: " + row);

            SecurityOs.open(owner, coreAbs);
            CompoundTag on = new CompoundTag();
            on.putString("id", robot.getUUID().toString());
            on.putBoolean("on", true);
            SecurityOs.handleAction(owner, new OsActionPayload(coreAbs, "robot_switch", on));
            check(robot.isActive(), "Van de desktop aangezet staat hij niet aan");
            CompoundTag rename = new CompoundTag();
            rename.putString("id", robot.getUUID().toString());
            rename.putString("name", "Lab guard");
            SecurityOs.handleAction(owner, new OsActionPayload(coreAbs, "robot_rename", rename));
            check("Lab guard".equals(robot.getCustomName().getString()), "Van de desktop hernoemd heet hij " + robot.getCustomName().getString());
            check(row(helper, core, robot).getBoolean("on"), "De app ziet niet dat hij aan staat");

            CompoundTag bad = new CompoundTag();
            bad.putString("id", "not-a-robot");
            bad.putBoolean("on", false);
            SecurityOs.handleAction(owner, new OsActionPayload(coreAbs, "robot_switch", bad));
            check(robot.isActive(), "Een rare id deed toch iets");
            SecurityOs.close(owner);
            helper.succeed();
        });
    }

    // ================================================================ 18 oktober: robots die er niet meer zijn

    /**
     * Oppakken met de knop Pick up van het robotscherm (zoals in het spel): hij is weg uit BotNetwork, en zet je hem
     * weer neer, dan heet hij weer "Guard 1". Vroeger bleef er een spookrobot "Guard 1" in de lijst staan, en werd de
     * robot die je weer neerzette "Guard 2". Afbranden (kortsluiting, aangezet met de knop) laat ook niets achter.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "robot_config")
    public static void robot_pick_up_leaves_no_ghost(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 1, 8), "ghost-owner");
        owner.getAbilities().instabuild = false;
        BotNetwork network = BotNetwork.get(helper.getLevel());
        GuardRobotEntity robot = dockedRobot(helper, owner, new BlockPos(4, 1, 4), RobotParts.DEFAULT);
        check("Guard 1".equals(robot.getCustomName().getString()), "De robot heet " + robot.getCustomName().getString());
        Robots.handleAction(owner, new RobotActionPayload(robot.getId(), RobotActionPayload.Op.PICK_UP, ""));
        check(!robot.isAlive(), "Opgepakt staat hij nog in de wereld");
        check(network.get(robot.getUUID()) == null && network.ownedBy(owner.getUUID()).isEmpty(),
                "Opgepakt met de knop staat hij nog in BotNetwork: " + network.ownedBy(owner.getUUID()));

        // Weer neerzetten met het item uit de rugzak: hij heet nog steeds Guard 1.
        ItemStack item = ItemStack.EMPTY;
        for (ItemStack stack : owner.getInventory().items) {
            if (stack.is(ModItems.GUARD_ROBOT.get())) {
                item = stack;
            }
        }
        check(!item.isEmpty(), "Het item zit niet in de rugzak");
        GuardRobotEntity again = placeOnDock(helper, owner, new BlockPos(4, 1, 4), item);
        check("Guard 1".equals(again.getCustomName().getString()), "Weer neergezet heet hij " + again.getCustomName().getString());

        float before = ImtConfig.Robots.BURN_CHANCE;
        try {
            ImtConfig.Robots.BURN_CHANCE = 1.0f;
            again.setBoard(BotBoard.EMPTY.with(5, "gun").withBridge(4));
            Robots.handleAction(owner, new RobotActionPayload(again.getId(), RobotActionPayload.Op.TURN_ON, ""));
            check(again.isDeadOrDying(), "Met brandkans 1 brandde hij niet af");
            check(network.get(again.getUUID()) == null, "Afgebrand staat hij nog in BotNetwork");
        } finally {
            ImtConfig.Robots.BURN_CHANCE = before;
        }
        helper.succeed();
    }

    /**
     * Een robot die er niet meer is (hier: weg zonder zich af te melden, zoals bij een crash) gaat vanzelf uit
     * BotNetwork zodra hij 3 seconden stil is terwijl zijn stuk wereld geladen is, en het logboek van de kern zegt het.
     * Een robot in een stuk wereld dat niet geladen is, blijft staan; een robot die er gewoon is ook.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "robot_sweep")
    public static void robot_network_forgets_robots_that_are_gone(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "sweep-owner");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        GuardRobotEntity stays = dockedRobot(helper, owner, new BlockPos(4, 1, 4), RobotParts.DEFAULT);
        GuardRobotEntity lost = dockedRobot(helper, owner, new BlockPos(8, 1, 4), RobotParts.DEFAULT);
        BotNetwork network = BotNetwork.get(helper.getLevel());
        // Ver weg, in een stuk wereld dat niet geladen is: hij staat alleen in BotNetwork (nooit in de wereld gezet).
        GuardRobotEntity far = ModEntities.GUARD_ROBOT.get().create(helper.getLevel());
        BlockPos farPos = helper.absolutePos(new BlockPos(4, 1, 4)).offset(20_000, 0, 20_000);
        far.moveTo(farPos.getX() + 0.5, farPos.getY(), farPos.getZ() + 0.5);
        far.setOwner(owner);
        far.setCustomName(net.minecraft.network.chat.Component.literal("Far guard"));
        network.update(far);
        check(network.get(far.getUUID()) != null, "De robot ver weg kwam niet in BotNetwork");
        helper.runAfterDelay(25, () -> {
            // Weg zonder af te melden; zijn stuk wereld blijft geladen.
            lost.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            check(network.get(lost.getUUID()) != null, "Hij ging meteen al uit BotNetwork");
        });
        helper.runAfterDelay(130, () -> {
            check(network.get(lost.getUUID()) == null, "De verdwenen robot staat na 5 seconden nog in BotNetwork");
            check(network.get(stays.getUUID()) != null, "De robot die er wel is, ging uit BotNetwork");
            check(network.get(far.getUUID()) != null, "De robot ver weg (niet geladen) ging uit BotNetwork");
            check(core.log().entries().stream().anyMatch(entry -> entry.text().equals("Guard 2 no longer exists, so it was taken off the list")),
                    "Het logboek zegt niet dat Guard 2 weg is");
            // Opruimen: de testwereld laat robots staan, en die zouden zich bij de kern van een volgende test melden.
            network.remove(far.getUUID());
            stays.discard();
            helper.succeed();
        });
    }

    /**
     * Delete in de app Robots (18 oktober): een robot die de desktop niet kan bereiken, gaat uit de lijst en het logboek
     * zegt wie dat deed; een robot die er gewoon is niet. Bestaat hij toch nog, dan komt hij terug zodra hij weer in de
     * wereld is, en heeft een andere robot intussen zijn naam, dan krijgt hij een nieuwe.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "robot_delete")
    public static void robot_delete_from_the_desktop(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "delete-owner");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos coreAbs = helper.absolutePos(new BlockPos(2, 3, 2));
        GuardRobotEntity here = dockedRobot(helper, owner, new BlockPos(8, 1, 8), RobotParts.DEFAULT);
        GuardRobotEntity away = dockedRobot(helper, owner, new BlockPos(4, 1, 8), RobotParts.DEFAULT);
        helper.runAfterDelay(25, () -> {
            BotNetwork network = BotNetwork.get(helper.getLevel());
            // "Ver weg": uit de wereld (zoals in een stuk wereld dat niet geladen is), maar nog in de lijst.
            CompoundTag saved = new CompoundTag();
            away.saveWithoutId(saved);
            away.remove(net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_TO_CHUNK);
            CompoundTag hereRow = row(helper, core, here);
            CompoundTag awayRow = row(helper, core, away);
            check(hereRow != null && hereRow.getBoolean("loaded") && awayRow != null && !awayRow.getBoolean("loaded"),
                    "De app ziet de robots niet goed: " + hereRow + " en " + awayRow);

            SecurityOs.open(owner, coreAbs);
            CompoundTag deleteHere = new CompoundTag();
            deleteHere.putString("id", here.getUUID().toString());
            SecurityOs.handleAction(owner, new OsActionPayload(coreAbs, "robot_delete", deleteHere));
            check(network.get(here.getUUID()) != null && here.isAlive(), "Delete haalde een robot die er gewoon is weg");
            CompoundTag deleteAway = new CompoundTag();
            deleteAway.putString("id", away.getUUID().toString());
            SecurityOs.handleAction(owner, new OsActionPayload(coreAbs, "robot_delete", deleteAway));
            check(network.get(away.getUUID()) == null, "Delete haalde de robot ver weg niet uit de lijst");
            check(row(helper, core, away) == null && row(helper, core, here) != null, "De app ziet de weggehaalde robot nog");
            check(core.log().entries().stream().anyMatch(entry -> entry.text().equals("delete-owner deleted Guard 2 from the list")),
                    "Het logboek zegt niet wie Guard 2 weghaalde");
            SecurityOs.close(owner);

            // Een nieuwe robot krijgt de vrije naam Guard 2. Dan komt de oude toch terug: hij krijgt Guard 3.
            GuardRobotEntity newer = dockedRobot(helper, owner, new BlockPos(4, 1, 4), RobotParts.DEFAULT);
            check("Guard 2".equals(newer.getCustomName().getString()), "De nieuwe robot heet " + newer.getCustomName().getString());
            GuardRobotEntity back = ModEntities.GUARD_ROBOT.get().create(helper.getLevel());
            back.load(saved);
            helper.getLevel().addFreshEntity(back);
            helper.runAfterDelay(25, () -> {
                BotNetwork.Entry entry = network.get(back.getUUID());
                check(entry != null, "Terug staat hij niet weer in de lijst");
                check("Guard 3".equals(entry.name()) && "Guard 3".equals(back.getCustomName().getString()),
                        "Terug heet hij " + back.getCustomName().getString() + " (in de lijst: " + entry.name() + ")");
                // Opruimen: de testwereld laat robots staan, en die zouden zich bij de kern van een volgende test melden.
                here.discard();
                newer.discard();
                back.discard();
                helper.succeed();
            });
        });
    }

    // ================================================================ Hoofdstuk 22: modules en solderen

    /** De negen modules bestaan als item ("scan_module" ...); acht hebben een recept, de verbrande niet. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void robot_modules_are_items(GameTestHelper helper) {
        for (BotModule module : BotModule.values()) {
            BotModuleItem item = ModItems.robotModule(module);
            check(item.module() == module, module + " weet niet wat het is");
            check(BuiltInRegistries.ITEM.getKey(item).equals(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, module.id() + "_module")),
                    "De module " + module + " heet " + BuiltInRegistries.ITEM.getKey(item));
            check(BotModule.byId(module.id()) == module, "byId vindt " + module + " niet terug");
            boolean recipe = helper.getLevel().getRecipeManager()
                    .byKey(ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, module.id() + "_module")).isPresent();
            check(recipe == (module != BotModule.BURNT), module + (recipe ? " heeft een recept" : " heeft geen recept"));
        }
        check(BotModule.byId("nonsense") == null, "byId vond iets dat niet bestaat");
        helper.succeed();
    }

    /**
     * Solderen (de server beslist): te kort pakt niet (de module blijft bij jou, de bout slijt wel), in het groene
     * vak zit de module erop, te lang geeft een brug naar een buurpin. Lossolderen geeft de module terug en haalt
     * de brug weg. Aan staat: geweigerd. Zonder soldeerbout: geweigerd. Een vreemde: niets.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "robot_config")
    public static void robot_solder_and_desolder(GameTestHelper helper) {
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 1, 8), "solder-owner");
        player.getAbilities().instabuild = false;
        GuardRobotEntity robot = dockedRobot(helper, player, new BlockPos(4, 1, 4), RobotParts.DEFAULT);
        float good = ImtConfig.Robots.BRIDGE_CHANCE_GOOD;
        float hot = ImtConfig.Robots.BRIDGE_CHANCE_HOT;
        ImtConfig.Robots.BRIDGE_CHANCE_GOOD = 0.0f;
        ImtConfig.Robots.BRIDGE_CHANCE_HOT = 1.0f;
        try {
            int full = Math.round(ImtConfig.Robots.SOLDER_FULL_TICKS);
            int inGreen = Math.round(full * (ImtConfig.Robots.SOLDER_COLD + ImtConfig.Robots.SOLDER_HOT) / 2);
            // Zonder soldeerbout: niets.
            player.getInventory().add(new ItemStack(ModItems.robotModule(BotModule.LASER), 2));
            Robots.handleSolder(player, new SolderPayload(robot.getId(), false, 3, "laser", inGreen));
            check(robot.board().pin(3).isEmpty(), "Zonder soldeerbout kon hij toch solderen");
            ItemStack iron = new ItemStack(ModItems.SOLDERING_IRON.get());
            player.getInventory().add(iron);
            ItemStack ironInBag = SolderingIronItem.find(player);
            // Te kort: het pakt niet.
            Robots.handleSolder(player, new SolderPayload(robot.getId(), false, 3, "laser", 1));
            check(robot.board().pin(3).isEmpty() && count(player, ModItems.robotModule(BotModule.LASER)) == 2,
                    "Te kort vastgehouden zat de module er toch op");
            check(ironInBag != null && ironInBag.getDamageValue() == 1, "De soldeerbout sleet niet");
            // In het groene vak: erop, geen brug.
            Robots.handleSolder(player, new SolderPayload(robot.getId(), false, 3, "laser", inGreen));
            check(robot.board().module(3) == BotModule.LASER && robot.board().bridges() == 0, "Goed gesoldeerd: " + robot.board());
            check(count(player, ModItems.robotModule(BotModule.LASER)) == 1, "De module ging niet uit de rugzak");
            // Op een pin met een module: geweigerd.
            Robots.handleSolder(player, new SolderPayload(robot.getId(), false, 3, "laser", inGreen));
            check(count(player, ModItems.robotModule(BotModule.LASER)) == 1, "Op een volle pin solderen kostte een module");
            // Te lang: een brug naar pin 4 (pin 8 heeft maar één buur).
            Robots.handleSolder(player, new SolderPayload(robot.getId(), false, 8, "laser", full * 2));
            check(robot.board().module(8) == BotModule.LASER && robot.board().bridge(7), "Te lang gaf geen brug: " + robot.board());
            check(robot.board().shorts().equals(List.of(7)), "De brug zou kortsluiting geven: " + robot.board().shorts());
            // Lossolderen: de module terug, de brug weg.
            Robots.handleSolder(player, new SolderPayload(robot.getId(), true, 8, "", 0));
            check(robot.board().pin(8).isEmpty() && robot.board().bridges() == 0, "Lossolderen liet iets achter: " + robot.board());
            check(count(player, ModItems.robotModule(BotModule.LASER)) == 1, "Lossolderen gaf de module niet terug");
            // Aan: geweigerd. Een vreemde: niets.
            robot.setActive(true);
            Robots.handleSolder(player, new SolderPayload(robot.getId(), true, 3, "", 0));
            check(robot.board().module(3) == BotModule.LASER, "Aan kon je toch lossolderen");
            robot.setActive(false);
            ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(7, 1, 8), "solder-stranger");
            stranger.getInventory().add(new ItemStack(ModItems.SOLDERING_IRON.get()));
            Robots.handleSolder(stranger, new SolderPayload(robot.getId(), true, 3, "", 0));
            check(robot.board().module(3) == BotModule.LASER, "Een vreemde kon lossolderen");
        } finally {
            ImtConfig.Robots.BRIDGE_CHANCE_GOOD = good;
            ImtConfig.Robots.BRIDGE_CHANCE_HOT = hot;
        }
        helper.succeed();
    }

    /** Het bord zelf: een brug tussen twee lege pinnen geeft geen kortsluiting; lossolderen van een lege pin haalt de brug weg. */
    @GameTest(template = ARENA, timeoutTicks = 20)
    public static void robot_board_bridges(GameTestHelper helper) {
        BotBoard board = BotBoard.EMPTY.withBridge(1).withBridge(5).with(6, "scan");
        check(board.bridge(1) && board.bridge(5) && !board.bridge(2), "Bruggen op de verkeerde plek: " + board);
        check(board.shorts().equals(List.of(5)), "Een brug tussen lege pinnen gaf toch kortsluiting: " + board.shorts());
        check(board.modules() == 1 << BotModule.SCAN.ordinal(), "Welke modules erop zitten klopt niet");
        BotBoard cleaned = board.desolder(1);
        check(!cleaned.bridge(1) && cleaned.bridge(5), "Lossolderen van pin 1 haalde de verkeerde brug weg");
        check(!board.desolder(6).bridge(5) && board.desolder(6).bridge(1) && board.desolder(6).pin(6).isEmpty(),
                "Lossolderen van pin 6 deed iets anders: " + board.desolder(6));
        check(!BotBoard.EMPTY.bridge(0) && !BotBoard.EMPTY.bridge(8), "Een brug buiten het bord?");
        helper.succeed();
    }

    /**
     * Aanzetten met een brug aan een module: kortsluiting. Hij blijft uit, de module op die pin wordt een Burnt
     * Module, de brug is weg, en een module zonder brug blijft heel; daarna gaat hij gewoon aan. Een brug tussen
     * twee lege pinnen doet niets. Met brandkans 1 brandt de hele robot af.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "robot_config")
    public static void robot_short_circuit(GameTestHelper helper) {
        float before = ImtConfig.Robots.BURN_CHANCE;
        try {
            ImtConfig.Robots.BURN_CHANCE = 0.0f;
            GuardRobotEntity robot = robot(helper, new BlockPos(4, 1, 4), RobotParts.DEFAULT);
            robot.setBoard(BotBoard.EMPTY.with(1, "scan").with(3, "laser").withBridge(3).withBridge(6));
            check(!robot.setActive(true) && !robot.isActive(), "Met een brug aan een module ging hij toch aan");
            BotBoard after = robot.board();
            check(after.module(3) == BotModule.BURNT && after.pin(4).isEmpty() && after.module(1) == BotModule.SCAN,
                    "Na de kortsluiting: " + after);
            check(!after.bridge(3) && after.bridge(6), "De verbrande brug is er nog, of de onschuldige brug is weg: " + after);
            check(robot.isAlive(), "Met brandkans 0 brandde hij toch af");
            check(robot.setActive(true) && robot.isActive(), "Zonder gevaarlijke brug gaat hij niet aan");

            ImtConfig.Robots.BURN_CHANCE = 1.0f;
            GuardRobotEntity doomed = robot(helper, new BlockPos(8, 1, 8), RobotParts.DEFAULT);
            doomed.setBoard(BotBoard.EMPTY.with(5, "gun").withBridge(4));
            check(!doomed.setActive(true), "Met een brug ging hij toch aan");
            check(doomed.isDeadOrDying(), "Met brandkans 1 brandde hij niet af");
        } finally {
            ImtConfig.Robots.BURN_CHANCE = before;
        }
        helper.succeed();
    }

    // ================================================================ 26 oktober: alleen ijzer repareert

    /**
     * Een robot herstelt niet vanzelf, ook niet terwijl hij op zijn dock laadt (jouw wens: "drones mogen niet zelf
     * herstellen, dit kan enkel met repair van iron"). Rechtsklik met ijzer wel: elke staaf REPAIR_PER_INGOT leven, en
     * er gaan er niet meer uit je hand dan nodig. Een vreemde mag het niet, en een hele robot kost geen ijzer.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "robot_repair")
    public static void robot_repairs_only_with_iron(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 1, 12), "repair-owner");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(2, 1, 12), "repair-stranger");
        owner.getAbilities().instabuild = false; // testspelers houden anders creative-handen: dan raakt de stapel niet op
        stranger.getAbilities().instabuild = false;
        BlockPos dock = new BlockPos(4, 1, 4);
        helper.setBlock(dock.west(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(dock.west())).fill(5_000);
        GuardRobotEntity robot = dockedRobot(helper, owner, dock, RobotParts.DEFAULT);
        float max = robot.getMaxHealth();
        robot.setHealth(max / 4);
        robot.setBattery(robot.maxBattery() / 2); // zodat hij laadt
        helper.runAfterDelay(60, () -> {
            check(robot.isCharging(), "Hij laadt niet op zijn dock");
            check(near(robot.getHealth(), max / 4), "Hij herstelde vanzelf: " + robot.getHealth() + " van " + max);

            stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 5));
            stranger.interactOn(robot, InteractionHand.MAIN_HAND);
            check(near(robot.getHealth(), max / 4) && stranger.getMainHandItem().getCount() == 5, "Een vreemde kon hem repareren");

            float perIngot = ImtConfig.Robots.REPAIR_PER_INGOT;
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 1));
            owner.interactOn(robot, InteractionHand.MAIN_HAND);
            check(near(robot.getHealth(), max / 4 + perIngot) && owner.getMainHandItem().isEmpty(),
                    "Eén staaf gaf " + robot.getHealth() + " leven, verwacht " + (max / 4 + perIngot));

            int needed = (int) Math.ceil((max - max / 4 - perIngot) / perIngot);
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_INGOT, 64));
            owner.interactOn(robot, InteractionHand.MAIN_HAND);
            check(robot.getHealth() == max, "Met een stapel ijzer is hij niet heel: " + robot.getHealth() + " van " + max);
            check(owner.getMainHandItem().getCount() == 64 - needed,
                    "Er gingen " + (64 - owner.getMainHandItem().getCount()) + " staven op, nodig waren er " + needed);
            owner.interactOn(robot, InteractionHand.MAIN_HAND);
            check(owner.getMainHandItem().getCount() == 64 - needed, "Een hele robot kostte toch ijzer");
            helper.succeed();
        });
    }

    /**
     * Oppakken en weer neerzetten maakt een robot niet heel: zijn item onthoudt hoe heel hij was. In de Robot Assembler
     * gaat hij zo niet uit elkaar (anders zette je hem daarna heel weer in elkaar); eerst repareren. Een robot-item van
     * vóór 26 oktober (zonder "health") is heel.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "robot_repair")
    public static void robot_keeps_its_damage_when_picked_up(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 1, 12), "damage-owner");
        owner.getAbilities().instabuild = false;
        BlockPos dock = new BlockPos(4, 1, 4);
        RobotParts parts = new RobotParts(RobotStyle.BATTLE, RobotStyle.SUPER_BATTLE, RobotStyle.BATTLE, RobotStyle.BATTLE);
        GuardRobotEntity robot = dockedRobot(helper, owner, dock, parts);
        robot.setHealth(robot.getMaxHealth() * 0.3f);
        Robots.pickUp(owner, robot);
        ItemStack stack = ItemStack.EMPTY;
        for (ItemStack item : owner.getInventory().items) {
            if (item.is(ModItems.GUARD_ROBOT.get())) {
                stack = item;
            }
        }
        check(!stack.isEmpty(), "De robot zit niet als item in de rugzak");
        RobotData data = GuardRobotItem.data(stack);
        check(data.damaged() && near(data.health(), 0.3), "Het item onthield zijn schade niet: " + data.health());

        BlockPos at = new BlockPos(8, 1, 4);
        helper.setBlock(at, ModBlocks.ROBOT_ASSEMBLER.get());
        RobotAssemblerBlockEntity assembler = (RobotAssemblerBlockEntity) helper.getBlockEntity(at);
        assembler.items().setItem(RobotAssemblerBlockEntity.ROBOT, stack.copy());
        check(!assembler.takeApart(), "Een beschadigde robot ging uit elkaar");
        check(RobotAssemblerBlockEntity.problem(assembler.items()).contains("damaged"),
                "De assembler zegt niet waarom: " + RobotAssemblerBlockEntity.problem(assembler.items()));
        assembler.items().setItem(RobotAssemblerBlockEntity.ROBOT, ItemStack.EMPTY);

        GuardRobotEntity again = placeOnDock(helper, owner, dock, stack);
        check(near(again.getHealth(), again.getMaxHealth() * 0.3f),
                "Weer neergezet heeft hij " + again.getHealth() + " van " + again.getMaxHealth() + " leven");

        Tag old = RobotData.CODEC.encodeStart(NbtOps.INSTANCE, new RobotData(parts, BotBoard.EMPTY, "Old")).getOrThrow();
        ((CompoundTag) old).remove("health");
        check(RobotData.CODEC.parse(NbtOps.INSTANCE, old).getOrThrow().health() == 1.0f, "Een oud robot-item is niet heel");
        helper.succeed();
    }

    /** Hoeveel van dit item er in de rugzak zit. */
    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** De rij van deze robot in de app Robots van deze kern, of null als hij er niet in staat. */
    private static @org.jetbrains.annotations.Nullable CompoundTag row(GameTestHelper helper, SecurityCoreBlockEntity core,
                                                                       net.minecraft.world.entity.Entity robot) {
        for (Tag element : Robots.snapshot(helper.getLevel(), core)) {
            if (((CompoundTag) element).getString("id").equals(robot.getUUID().toString())) {
                return (CompoundTag) element;
            }
        }
        return null;
    }

    /** Een robot die je met het item op een dock zet (gekoppeld aan dat dock). */
    private static GuardRobotEntity dockedRobot(GameTestHelper helper, ServerPlayer player, BlockPos dock, RobotParts parts) {
        helper.setBlock(dock, ModBlocks.ROBOT_DOCK.get());
        return placeOnDock(helper, player, dock, GuardRobotItem.create(new RobotData(parts, BotBoard.EMPTY, "")));
    }

    /** Dit robot-item op het dock dat er al staat. */
    private static GuardRobotEntity placeOnDock(GameTestHelper helper, ServerPlayer player, BlockPos dock, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = helper.absolutePos(dock);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs).relative(Direction.UP, 0.5), Direction.UP, abs, false);
        check(stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit)).consumesAction(), "Neerzetten op het dock lukte niet");
        List<GuardRobotEntity> robots = helper.getEntities(ModEntities.GUARD_ROBOT.get(), dock, 1.0);
        check(robots.size() == 1, "Er staan " + robots.size() + " robots op het dock");
        return robots.get(0);
    }

    /** Hoeveel van dit item er vlak bij deze plek op de grond ligt. */
    private static int dropped(GameTestHelper helper, BlockPos pos, Item item) {
        int count = 0;
        for (ItemEntity entity : helper.getEntities(EntityType.ITEM, pos, 2.0)) {
            if (entity.getItem().is(item)) {
                count += entity.getItem().getCount();
            }
        }
        return count;
    }
}
