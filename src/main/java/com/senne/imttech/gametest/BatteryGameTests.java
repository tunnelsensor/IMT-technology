package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.battery.BatteryPortBlockEntity;
import com.senne.imttech.battery.BatteryStructure;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.station.ChargerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de Power Battery (hoofdstuk 47, 4 november): een dichte doos van casing met cellen erin en een poort in
 * de wand vormt een batterij die laadt uit de generatoren en apparaten voedt; breek je de doos, dan is het geen batterij
 * meer (de stroom blijft bewaard tot ze weer klopt).
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class BatteryGameTests {
    private BatteryGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Een doos van 4 x 3 x 3 (binnen 2 x 1 x 1: twee cellen) met de poort in de westwand, op {@code base}. */
    private static void build(GameTestHelper helper, BlockPos base, BlockPos port) {
        for (BlockPos pos : BlockPos.betweenClosed(base, base.offset(3, 2, 2))) {
            boolean wall = pos.getX() == base.getX() || pos.getX() == base.getX() + 3 || pos.getY() == base.getY()
                    || pos.getY() == base.getY() + 2 || pos.getZ() == base.getZ() || pos.getZ() == base.getZ() + 2;
            helper.setBlock(pos.immutable(), wall ? ModBlocks.BATTERY_CASING.get() : ModBlocks.BATTERY_CELL.get());
        }
        helper.setBlock(port, ModBlocks.BATTERY_PORT.get());
    }

    private static BatteryPortBlockEntity port(GameTestHelper helper, BlockPos port) {
        return (BatteryPortBlockEntity) helper.getBlockEntity(port);
    }

    /**
     * De doos vormt een batterij met twee cellen (capaciteit 2 x batteryCellCapacity); een vol zonnepaneel tegen de poort
     * laadt hem (wat het paneel boven zijn reserve heeft, tot batteryChargeRate per tick); een camera tegen de poort haalt
     * er stroom uit; een glazen wandblok mag; een gat in de wand maakt het geen batterij meer, en dichtmaken brengt hem
     * terug met zijn stroom.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "battery")
    public static void battery_forms_charges_and_supplies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = new BlockPos(4, 3, 4);
        BlockPos portPos = base.offset(0, 1, 1);
        build(helper, base, portPos);
        BlockPos solar = portPos.west();
        helper.setBlock(solar, ModBlocks.SOLAR_PANEL.get());
        ChargerBlockEntity panel = (ChargerBlockEntity) helper.getBlockEntity(solar);
        panel.fill(50_000);
        // Een tweede poort in de oostwand, met de camera ertegen: die hangt zo aan dezelfde batterij.
        BlockPos portB = base.offset(3, 1, 1);
        helper.setBlock(portB, ModBlocks.BATTERY_PORT.get());
        BlockPos camera = portB.east();
        helper.setBlock(camera, ModBlocks.SECURITY_CAMERA.get());
        helper.runAfterDelay(5, () -> {
            BatteryPortBlockEntity port = port(helper, portPos);
            check(port.formed(), "De doos zou een batterij moeten zijn: " + port.problem());
            BatteryStructure.Structure structure = port.structure();
            check(structure != null && structure.cells() == 2 && structure.sizeX() == 4 && structure.sizeY() == 3 && structure.sizeZ() == 3,
                    "De vorm klopt niet: " + structure);
            check(port.capacity() == 2 * Math.round(ImtConfig.Power.BATTERY_CELL_CAPACITY), "Twee cellen zouden " + 2 * ImtConfig.Power.BATTERY_CELL_CAPACITY
                    + " FE moeten kunnen hebben, niet " + port.capacity());
            check(port.isMaster(), "De enige poort zou de baas moeten zijn");
        });
        // Het paneel heeft 50.000 en houdt 50% achter: 25.000 kan naar de batterij, 2.000 per tick, dus na 20 ticks ongeveer 25.000.
        helper.runAfterDelay(40, () -> {
            BatteryPortBlockEntity port = port(helper, portPos);
            long stored = port.stored();
            int reserve = Math.round(ImtConfig.Power.SOLAR_CAPACITY * ImtConfig.Power.STATION_GRID_RESERVE);
            check(stored > 0, "De batterij laadde niet uit het paneel");
            check(panel.energy() >= reserve - 100, "Het paneel zou zijn reserve moeten houden (" + reserve + "), niet " + panel.energy());
            // De camera tegen de poort haalt uit de batterij (die heeft het meest, dus eerst).
            int got = PowerGrid.draw(level, helper.absolutePos(camera), 1_000);
            check(got == 1_000, "De camera kreeg geen 1000 FE uit het net: " + got);
            check(port.stored() == stored - 1_000, "De batterij zou 1000 FE kwijt moeten zijn: " + port.stored() + " in plaats van " + (stored - 1_000));
            // Glas in de wand mag.
            helper.setBlock(base.offset(3, 2, 1), ModBlocks.BATTERY_GLASS.get());
        });
        helper.runAfterDelay(50, () -> check(port(helper, portPos).formed(), "Met een glazen wandblok zou het nog een batterij moeten zijn"));
        // Een gat in de wand: geen batterij meer, maar de stroom blijft bewaard.
        helper.runAfterDelay(55, () -> helper.setBlock(base.offset(1, 2, 1), Blocks.AIR));
        helper.runAfterDelay(60, () -> {
            BatteryPortBlockEntity port = port(helper, portPos);
            check(!port.formed(), "Met een gat in de wand zou het geen batterij mogen zijn");
            check(port.problem().contains("wall is not closed"), "De poort zou moeten zeggen dat de wand niet dicht is: " + port.problem());
            check(port.energy() == 0 && port.capacity() == 0, "Zonder doos zou er geen stroom te halen zijn");
            helper.setBlock(base.offset(1, 2, 1), ModBlocks.BATTERY_CASING.get());
        });
        helper.runAfterDelay(65, () -> {
            BatteryPortBlockEntity port = port(helper, portPos);
            check(port.formed(), "Na het dichtmaken zou het weer een batterij moeten zijn: " + port.problem());
            check(port.stored() > 0, "Na het dichtmaken zou de stroom er nog moeten zijn");
            helper.succeed();
        });
    }

    /** Een poort zonder doos, en een doos zonder cellen: dat zegt de poort. Twee poorten: de laagste is de baas. */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "battery")
    public static void battery_explains_what_is_missing(GameTestHelper helper) {
        BlockPos lone = new BlockPos(2, 3, 12);
        helper.setBlock(lone, ModBlocks.BATTERY_PORT.get());
        BlockPos base = new BlockPos(8, 3, 8);
        for (BlockPos pos : BlockPos.betweenClosed(base, base.offset(2, 2, 2))) {
            boolean wall = pos.getX() == base.getX() || pos.getX() == base.getX() + 2 || pos.getY() == base.getY()
                    || pos.getY() == base.getY() + 2 || pos.getZ() == base.getZ() || pos.getZ() == base.getZ() + 2;
            helper.setBlock(pos.immutable(), wall ? ModBlocks.BATTERY_CASING.get() : Blocks.AIR);
        }
        BlockPos portA = base.offset(0, 1, 1);
        BlockPos portB = base.offset(2, 1, 1);
        helper.setBlock(portA, ModBlocks.BATTERY_PORT.get());
        helper.setBlock(portB, ModBlocks.BATTERY_PORT.get());
        helper.runAfterDelay(5, () -> {
            check(!port(helper, lone).formed() && port(helper, lone).problem().contains("Too small"),
                    "Een losse poort zou 'too small' moeten zeggen: " + port(helper, lone).problem());
            check(!port(helper, portA).formed() && port(helper, portA).problem().contains("No Battery Cells"),
                    "Een lege doos zou om cellen moeten vragen: " + port(helper, portA).problem());
            helper.setBlock(base.offset(1, 1, 1), ModBlocks.BATTERY_CELL.get());
        });
        helper.runAfterDelay(10, () -> {
            BatteryPortBlockEntity a = port(helper, portA);
            BatteryPortBlockEntity b = port(helper, portB);
            check(a.formed() && b.formed(), "Met een cel zou het een batterij moeten zijn: " + a.problem());
            check(a.isMaster() && !b.isMaster() && b.master() == a, "De laagste poort (west) zou de baas moeten zijn");
            a.fill(5_000);
            check(b.energy() == 5_000 && b.drain(2_000) == 2_000 && a.stored() == 3_000,
                    "Een kabel aan de andere poort zou bij dezelfde stroom moeten komen: " + a.stored());
            helper.succeed();
        });
    }

    /**
     * Battery Glass hangt aan elkaar zoals het framed glass van Create (hoofdstuk 54, jouw notitie): elk blok weet aan
     * welke kanten er ook Battery Glass zit (dan tekent het model daar geen rand), ook als het met een commando of in
     * een structuur neergezet is, en het vergeet een buur die weggaat.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "battery")
    public static void battery_glass_links_to_its_neighbours(GameTestHelper helper) {
        BlockPos a = new BlockPos(2, 3, 2);
        BlockPos b = a.east();
        BlockPos c = a.above();
        helper.setBlock(a, ModBlocks.BATTERY_GLASS.get());
        helper.setBlock(b, ModBlocks.BATTERY_GLASS.get());
        helper.setBlock(c, ModBlocks.BATTERY_GLASS.get());
        helper.setBlock(a.west(), Blocks.STONE);
        helper.runAfterDelay(2, () -> {
            check(helper.getBlockState(a).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST),
                    "Het glas weet niet dat er rechts glas zit");
            check(helper.getBlockState(a).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.UP),
                    "Het glas weet niet dat er boven glas zit");
            check(!helper.getBlockState(a).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST),
                    "Steen telt als glas");
            check(helper.getBlockState(b).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WEST),
                    "Het rechterglas weet niet dat er links glas zit");
            helper.setBlock(b, Blocks.AIR);
            helper.runAfterDelay(2, () -> {
                check(!helper.getBlockState(a).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.EAST),
                        "Het glas vergeet een buur niet die weg is");
                helper.succeed();
            });
        });
    }

    /**
     * Hoofdstuk 81 (jouw melding van 11 november: "Als je iets van de battery breekt zoals een casing, glass, cel... dan
     * krijg je dat niet terug, ook duurt het heel lang om te breken"): de vier blokken van de batterij vallen terug als je
     * ze met een pickaxe breekt, en die pickaxe is ook echt het goede gereedschap (anders breek je ze even traag als met
     * je hand).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "battery")
    public static void battery_blocks_drop_themselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(2, 3, 2));
        net.minecraft.world.item.ItemStack pickaxe = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
        for (net.minecraft.world.level.block.Block block : java.util.List.of(ModBlocks.BATTERY_CASING.get(),
                ModBlocks.BATTERY_GLASS.get(), ModBlocks.BATTERY_CELL.get(), ModBlocks.BATTERY_PORT.get())) {
            net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
            String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).getPath();
            check(state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE), name + " staat niet bij de pickaxe-blokken");
            check(pickaxe.isCorrectToolForDrops(state), "Een ijzeren pickaxe is niet het goede gereedschap voor " + name);
            java.util.List<net.minecraft.world.item.ItemStack> drops =
                    net.minecraft.world.level.block.Block.getDrops(state, level, pos, null, null, pickaxe);
            check(drops.size() == 1 && drops.getFirst().is(block.asItem()) && drops.getFirst().getCount() == 1,
                    name + " valt niet terug als één blok, maar als " + drops);
        }
        helper.succeed();
    }

    /**
     * Dezelfde fout mag bij geen enkel blok van de mod terugkomen: elk blok dat je kunt breken heeft een loot table (dus
     * je krijgt er iets voor terug), en wie het goede gereedschap nodig heeft, staat bij een gereedschap (pickaxe, bijl,
     * schop of schoffel). Alleen onbreekbare blokken (de laserwand van een gate) slaan we over.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "battery")
    public static void every_block_has_drops_and_a_tool(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        java.util.List<String> problems = new java.util.ArrayList<>();
        for (var entry : ModBlocks.BLOCKS.getEntries()) {
            net.minecraft.world.level.block.Block block = entry.get();
            net.minecraft.world.level.block.state.BlockState state = block.defaultBlockState();
            String name = entry.getId().getPath();
            if (state.getDestroySpeed(level, BlockPos.ZERO) < 0) {
                continue;
            }
            var lootKey = block.getLootTable();
            if (lootKey == net.minecraft.world.level.storage.loot.BuiltInLootTables.EMPTY
                    || level.getServer().reloadableRegistries().getLootTable(lootKey)
                            == net.minecraft.world.level.storage.loot.LootTable.EMPTY) {
                problems.add(name + " heeft geen loot table");
            }
            if (state.requiresCorrectToolForDrops() && !state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE)
                    && !state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_AXE)
                    && !state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL)
                    && !state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_HOE)) {
                problems.add(name + " vraagt het goede gereedschap maar staat bij geen enkel gereedschap");
            }
        }
        check(problems.isEmpty(), String.join("; ", problems));
        helper.succeed();
    }
}
