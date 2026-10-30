package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.bypass.Bypass;
import com.senne.imttech.network.LaserActionPayload;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.shield.LaserBridgeBlock;
import com.senne.imttech.shield.LaserBridgeBlockEntity;
import com.senne.imttech.shield.LaserBridgePlateBlock;
import com.senne.imttech.shield.LaserBridges;
import com.senne.imttech.shield.LaserEmitterBlock;
import com.senne.imttech.shield.ShieldColor;
import com.senne.imttech.station.ChargerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de laserbrug (hoofdstuk 34, 25 oktober). Jouw wens: "maak een nieuwe block laserbridge, dit is basically
 * hetzelfde als een rayshield maar het doet geen damage en is enkel horizontaal". Jouw antwoorden: de plaat ligt een
 * klein beetje onder de rand van het blok, iedereen kan erop lopen, hij hoort niet bij de beveiliging, en hij heeft
 * een eigen look.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class BridgeGameTests {
    private BridgeGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * Een brugzender die naar het oosten kijkt, met een volle warmtegenerator eronder (stroom). Hij staat nog uit
     * (hoofdstuk 39: anders schoof hij meteen uit); zet hem aan met {@link #switchBridge}.
     */
    private static void bridge(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, offBridge());
        helper.setBlock(pos.below(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(pos.below())).fill(5_000);
    }

    /** Een brugzender die naar het oosten kijkt en uit staat. */
    private static BlockState offBridge() {
        return ModBlocks.LASER_BRIDGE.get().defaultBlockState().setValue(LaserBridgeBlock.FACING, Direction.EAST)
                .setValue(LaserBridgeBlock.SWITCHED_ON, false);
    }

    /** De brug van deze zender aan of uit, zoals I.M.T (of een diagram) hem op zijn naam schakelt (hoofdstuk 39). */
    private static void switchBridge(GameTestHelper helper, BlockPos pos, boolean on) {
        check(com.senne.imttech.redstone.ImtRedstone.switchAt(helper.getLevel(), helper.absolutePos(pos), -1,
                on ? com.senne.imttech.redstone.ImtRedstone.Op.ON : com.senne.imttech.redstone.ImtRedstone.Op.OFF, 0),
                "Er staat geen brug om te schakelen");
    }

    /** Hoeveel brugplaten er oostelijk van {@code emitter} liggen (tot {@code length} ver). */
    private static int plates(GameTestHelper helper, BlockPos emitter, int length) {
        int count = 0;
        for (int i = 1; i <= length; i++) {
            if (helper.getBlockState(emitter.east(i)).is(ModBlocks.LASER_BRIDGE_PLATE.get())) {
                count++;
            }
        }
        return count;
    }

    private static ShieldColor colour(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(LaserBridgeBlock.COLOR);
    }

    /**
     * De brug schuift uit de zender naar voren (altijd een aaneengesloten stuk vanaf de zender) tot het eerste blok. De
     * plaat ligt 2 pixels onder de rand van het blok, over het hele blok; aan de zijkanten en aan het eind een rand, niet
     * aan de kant van de zender. Een armor stand blijft erop staan en raakt niet beschadigd (geen pijn); trekt de brug
     * zich terug, dan valt hij.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "laser_bridge")
    public static void laser_bridge_slides_out_and_carries(GameTestHelper helper) {
        BlockPos emitter = new BlockPos(1, 4, 2);
        bridge(helper, emitter);
        helper.setBlock(emitter.east(5), Blocks.STONE); // hier houdt de brug op
        switchBridge(helper, emitter, true);
        check(plates(helper, emitter, 4) < 4, "De brug lag er meteen helemaal (geen animatie)");
        helper.onEachTick(() -> {
            int count = plates(helper, emitter, 4);
            for (int i = 1; i <= count; i++) {
                check(helper.getBlockState(emitter.east(i)).is(ModBlocks.LASER_BRIDGE_PLATE.get()),
                        "De brug groeit niet vanaf de zender (gat op plek " + i + ")");
            }
        });
        ArmorStand[] stand = new ArmorStand[1];
        helper.startSequence()
                .thenWaitUntil(() -> check(plates(helper, emitter, 4) == 4, "De brug is niet uitgeschoven"))
                .thenExecute(() -> {
                    check(plates(helper, emitter, 6) == 4, "De brug liep door het blok heen");
                    for (int i = 1; i <= 4; i++) {
                        BlockPos cell = emitter.east(i);
                        BlockState plate = helper.getBlockState(cell);
                        AABB box = plate.getCollisionShape(helper.getLevel(), helper.absolutePos(cell)).bounds();
                        check(Math.abs(box.maxY - LaserBridgePlateBlock.TOP / 16.0) < 1e-6 && box.minY > 0.5
                                && box.minX == 0 && box.maxX == 1 && box.minZ == 0 && box.maxZ == 1,
                                "De plaat ligt niet net onder de rand, over het hele blok: " + box);
                        check(plate.getValue(LaserBridgePlateBlock.AXIS) == Direction.Axis.X, "De plaat weet niet dat de brug oost-west loopt");
                        // Opzij geen brug: daar komt een rand. Achter (westen) de zender of een plaat: geen rand.
                        check(!plate.getValue(LaserBridgePlateBlock.NORTH) && !plate.getValue(LaserBridgePlateBlock.SOUTH),
                                "Plaat " + i + " heeft opzij geen rand");
                        check(plate.getValue(LaserBridgePlateBlock.WEST), "Plaat " + i + " heeft een rand aan de kant van de zender");
                        check(plate.getValue(LaserBridgePlateBlock.EAST) == (i < 4),
                                "Plaat " + i + ": de rand aan het eind klopt niet");
                    }
                    check(helper.getBlockState(emitter.east(1)).getValue(LaserBridgePlateBlock.COLOR) == ShieldColor.CYAN,
                            "Een nieuwe brug is niet cyaan");
                    // Een armor stand midden op de brug.
                    stand[0] = helper.spawn(EntityType.ARMOR_STAND, Vec3.atBottomCenterOf(emitter.east(2).above()));
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    double feet = stand[0].getY() - helper.absolutePos(emitter.east(2)).getY();
                    check(stand[0].isAlive() && Math.abs(feet - LaserBridgePlateBlock.TOP / 16.0) < 0.01,
                            "De armor stand staat niet op de brug (voeten op " + feet + ")");
                    check(stand[0].getHealth() == stand[0].getMaxHealth(), "De brug deed pijn");
                    switchBridge(helper, emitter, false); // uit: de brug trekt zich terug
                })
                .thenWaitUntil(() -> check(plates(helper, emitter, 4) == 0, "De brug ging niet weg"))
                .thenWaitUntil(() -> check(stand[0].getY() < helper.absolutePos(emitter).getY() - 0.5,
                        "De armor stand viel niet toen de brug wegging"))
                .thenSucceed();
    }

    /**
     * Alleen opzij: de brug gaat de kant op waar je kijkt als je hem neerzet, ook als je schuin naar beneden kijkt. Wie
     * hem neerzette, is de eigenaar, en hij heeft hem in zijn I.M.T-lijst ("Bridge 1", hoofdstuk 39). Een zender ernaast
     * hoort bij de brug en neemt zijn kleur en aan/uit over (en geeft geen tweede regel in de lijst); een vreemde kan niets
     * veranderen, de eigenaar wel (voor de hele brug).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "laser_bridge")
    public static void laser_bridge_faces_where_you_look(GameTestHelper helper) {
        check(!LaserBridgeBlock.FACING.getPossibleValues().contains(Direction.UP)
                && !LaserBridgeBlock.FACING.getPossibleValues().contains(Direction.DOWN), "Een brug kan omhoog of omlaag kijken");
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 12), "bridge-owner");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 2, 12), "bridge-stranger");
        BlockPos floor = new BlockPos(3, 2, 10);
        helper.setBlock(floor, Blocks.STONE);
        helper.setBlock(floor.south(), Blocks.STONE);
        // Schuin naar beneden en naar het oosten kijken (yaw -90 = oost), en hem op de steen zetten.
        Vec3 spot = helper.absoluteVec(new Vec3(2.5, 3.5, 10.5));
        owner.moveTo(spot.x, spot.y, spot.z, -90.0f, 50.0f);
        check(ZoneGameTests.place(helper, owner, ModItems.LASER_BRIDGE.get(), floor, Direction.UP), "Neerzetten lukte niet");
        BlockPos first = floor.above();
        check(helper.getBlockState(first).is(ModBlocks.LASER_BRIDGE.get()), "Er staat geen brugzender");
        check(helper.getBlockState(first).getValue(LaserBridgeBlock.FACING) == Direction.EAST,
                "De brug kijkt niet naar waar je keek: " + helper.getBlockState(first).getValue(LaserBridgeBlock.FACING));
        var entity = (LaserBridgeBlockEntity) helper.getBlockEntity(first);
        check(owner.getUUID().equals(entity.owner()), "Wie hem neerzette, is niet de eigenaar");

        // Een vreemde: niets (geen kleur, niet aan of uit).
        LaserBridges.handleAction(stranger, new LaserActionPayload(helper.absolutePos(first), LaserActionPayload.Op.COLOR,
                ShieldColor.RED.ordinal()));
        check(!LaserBridges.mayEdit(stranger, entity) && colour(helper, first) == ShieldColor.CYAN,
                "Een vreemde kon de brug instellen");
        // De eigenaar: groen en uit.
        LaserBridges.handleAction(owner, new LaserActionPayload(helper.absolutePos(first), LaserActionPayload.Op.COLOR,
                ShieldColor.GREEN.ordinal()));
        LaserBridges.handleAction(owner, new LaserActionPayload(helper.absolutePos(first), LaserActionPayload.Op.SWITCH, 0));
        check(colour(helper, first) == ShieldColor.GREEN && !helper.getBlockState(first).getValue(LaserBridgeBlock.SWITCHED_ON),
                "De eigenaar kon de brug niet instellen");
        // Liggend/staand bestaat niet bij een brug: die knop doet niets (en geeft geen fout).
        LaserBridges.handleAction(owner, new LaserActionPayload(helper.absolutePos(first), LaserActionPayload.Op.TURN, 1));

        // Een tweede ernaast (ook als je een andere kant op kijkt): die hoort erbij en is meteen groen en uit.
        owner.moveTo(spot.x, spot.y, spot.z, 0.0f, 50.0f);
        check(ZoneGameTests.place(helper, owner, ModItems.LASER_BRIDGE.get(), floor.south(), Direction.UP), "De tweede neerzetten lukte niet");
        BlockPos second = first.south();
        check(colour(helper, second) == ShieldColor.GREEN && !helper.getBlockState(second).getValue(LaserBridgeBlock.SWITCHED_ON),
                "Een zender naast de brug nam haar instellingen niet over");
        var relays = owner.getData(com.senne.imttech.registry.ModAttachments.RELAYS);
        check(relays.relays().size() == 1 && relays.relays().getFirst().name().equals("Bridge 1"),
                "De brug staat niet één keer, als Bridge 1, in de I.M.T-lijst: " + relays.relays());
        check(LaserBridgeBlock.group(helper.getLevel(), helper.absolutePos(first)).size() == 2, "De twee zenders zijn niet één brug");
        // Rood geldt voor allebei.
        LaserBridges.handleAction(owner, new LaserActionPayload(helper.absolutePos(second), LaserActionPayload.Op.COLOR,
                ShieldColor.RED.ordinal()));
        check(colour(helper, first) == ShieldColor.RED && colour(helper, second) == ShieldColor.RED,
                "Rood ging niet naar de hele brug");
        helper.succeed();
    }

    /**
     * Drie zenders naast elkaar zijn één brug van drie breed: stroom alleen bij de noordelijkste, aan gezet via de
     * zuidelijkste, en toch schuiven ze alle drie uit. De middelste rij heeft opzij geen rand (daar ligt brug), de buitenste
     * alleen aan de buitenkant. Uit (weer via de zuidelijkste) gaan ze allemaal weer weg.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "laser_bridge")
    public static void laser_bridge_three_wide_one_cable(GameTestHelper helper) {
        BlockPos north = new BlockPos(12, 4, 2);
        for (int i = 0; i < 3; i++) {
            BlockPos emitter = north.south(i);
            helper.setBlock(emitter, offBridge());
            helper.setBlock(emitter.east(5), Blocks.STONE);
        }
        helper.setBlock(north.north(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(north.north())).fill(5_000);
        switchBridge(helper, north.south(2), true);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    for (int i = 0; i < 3; i++) {
                        check(plates(helper, north.south(i), 4) == 4, "Rij " + (i + 1) + " van de brug is niet uitgeschoven");
                    }
                })
                .thenExecute(() -> {
                    BlockState outerNorth = helper.getBlockState(north.east(2));
                    BlockState middle = helper.getBlockState(north.south().east(2));
                    BlockState outerSouth = helper.getBlockState(north.south(2).east(2));
                    check(middle.getValue(LaserBridgePlateBlock.NORTH) && middle.getValue(LaserBridgePlateBlock.SOUTH),
                            "De middelste rij heeft opzij een rand");
                    check(!outerNorth.getValue(LaserBridgePlateBlock.NORTH) && outerNorth.getValue(LaserBridgePlateBlock.SOUTH),
                            "De noordelijke rij heeft zijn rand aan de verkeerde kant");
                    check(outerSouth.getValue(LaserBridgePlateBlock.NORTH) && !outerSouth.getValue(LaserBridgePlateBlock.SOUTH),
                            "De zuidelijke rij heeft zijn rand aan de verkeerde kant");
                })
                .thenExecute(() -> switchBridge(helper, north.south(2), false))
                .thenWaitUntil(() -> {
                    for (int i = 0; i < 3; i++) {
                        check(plates(helper, north.south(i), 4) == 0, "Rij " + (i + 1) + " ging niet mee weg");
                    }
                })
                .thenSucceed();
    }

    /** Zonder stroom geen brug, ook niet als hij aan staat (en redstone doet niets meer, hoofdstuk 39). */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "laser_bridge")
    public static void laser_bridge_needs_power(GameTestHelper helper) {
        BlockPos emitter = new BlockPos(1, 4, 20);
        helper.setBlock(emitter, ModBlocks.LASER_BRIDGE.get().defaultBlockState().setValue(LaserBridgeBlock.FACING, Direction.EAST));
        helper.setBlock(emitter.west(), Blocks.REDSTONE_BLOCK);
        check(helper.getBlockState(emitter).getValue(LaserBridgeBlock.SWITCHED_ON), "Een nieuwe brug staat niet aan");
        helper.runAfterDelay(30, () -> {
            check(plates(helper, emitter, 4) == 0 && !helper.getBlockState(emitter).getValue(LaserBridgeBlock.POWERED),
                    "De brug ging aan zonder stroom");
            helper.succeed();
        });
    }

    /**
     * Los van de beveiliging (jouw keuze): staan de lasers van de beveiligingskern uit (de schakelaar "Lasers"), dan blijft
     * een laserzender ernaast uit (ook al staat hij zelf aan), maar de brug schuift gewoon uit. En de Bypass Kit doet niets op een brug (hij slijt
     * ook niet). (In een eigen batch: deze kern zet de lasers uit, dat mag geen andere test merken.)
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "laser_bridge_security")
    public static void laser_bridge_ignores_security(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 2, 3), "bridge-core-owner");
        ServerPlayer hacker = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(9, 4, 9), "bridge-hacker");
        hacker.getAbilities().instabuild = false; // anders slijt de kit niet (zoals in creative)
        hacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BYPASS_KIT.get()));
        SecurityCoreBlockEntity core = ZoneGameTests.zoneCore(helper, owner);
        core.setLasersEnabled(false);
        // Een brug en (ernaast) een laserzender, allebei met stroom en aan.
        BlockPos bridge = new BlockPos(6, 4, 8);
        bridge(helper, bridge);
        helper.setBlock(bridge.east(5), Blocks.STONE);
        BlockPos laser = new BlockPos(6, 4, 11);
        helper.setBlock(laser, ModBlocks.LASER_EMITTER.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.EAST));
        helper.setBlock(laser.east(5), Blocks.STONE); // gaat hij later toch aan, dan blijft zijn wand in deze test
        helper.setBlock(laser.below(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(laser.below())).fill(5_000);
        switchBridge(helper, bridge, true);
        helper.runAfterDelay(40, () -> {
            try {
                check(!helper.getBlockState(laser).getValue(LaserEmitterBlock.POWERED),
                        "De laserzender ging aan terwijl de lasers van de kern uit staan (dan zegt deze test niets)");
                check(plates(helper, bridge, 4) == 4, "De brug luisterde naar de schakelaar Lasers van de kern");
                Bypass.clear();
                Bypass.start(hacker, helper.absolutePos(bridge), InteractionHand.MAIN_HAND);
                check(!Bypass.isHacking(hacker) && hacker.getMainHandItem().getDamageValue() == 0,
                        "De Bypass Kit werkte op een brug");
            } finally {
                core.setLasersEnabled(true);
            }
            ZoneGameTests.finish(helper, core);
        });
    }
}
