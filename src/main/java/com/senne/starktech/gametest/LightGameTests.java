package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.energy.StarkCables;
import com.senne.starktech.energy.StarkGrid;
import com.senne.starktech.light.ArcLampBlock;
import com.senne.starktech.light.ArcLampLight;
import com.senne.starktech.light.LightSwitchBlock;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.station.ChargerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de Arc Lamp en de lichtschakelaar (9 oktober). Jouw wens: "een lamp die op stroom werkt die
 * nog veel helderder kan als lightlevel 15, ik wil een van 100 ... stroom is aan, geen stroom is uit", en
 * "een lightswitch die je tussen een cable kunt zetten om zo de lichten te besturen".
 */
@GameTestHolder(StarkTech.MODID)
@PrefixGameTestTemplate(false)
public final class LightGameTests {
    private LightGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Een lichtschakelaar op de vloer, aan of uit. */
    private static BlockState lightSwitch(boolean on) {
        return ModBlocks.LIGHT_SWITCH.get().defaultBlockState()
                .setValue(LightSwitchBlock.FACE, AttachFace.FLOOR).setValue(LightSwitchBlock.ON, on);
    }

    /**
     * De lichtschakelaar in een kabel: aan gaat de stroom erdoor, uit niet. Ook als er een getekende kabel
     * dwars door de schakelaar loopt, en ook als hij tussen twee kabeleinden staat (zonder kabel eronder).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "cables")
    public static void light_switch_cuts_and_restores_power(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos generator = new BlockPos(2, 2, 6);
        helper.setBlock(generator, ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(generator)).fill(5_000);
        for (int x = 3; x <= 9; x++) {
            StarkCables.add(level, helper.absolutePos(new BlockPos(x, 2, 6)));
        }
        BlockPos switchPos = new BlockPos(6, 2, 6);
        helper.setBlock(switchPos, lightSwitch(true));
        BlockPos device = helper.absolutePos(new BlockPos(10, 2, 6));
        check(StarkGrid.available(level, device) > 0, "Een schakelaar die aan staat, houdt de stroom tegen");
        LightSwitchBlock.set(level, helper.absolutePos(switchPos), false);
        check(StarkGrid.available(level, device) == 0, "De schakelaar staat uit, maar de stroom loopt door de kabel eronder door");
        LightSwitchBlock.set(level, helper.absolutePos(switchPos), true);
        check(StarkGrid.available(level, device) > 0, "De schakelaar staat weer aan, maar er komt geen stroom");
        // Zonder kabel onder de schakelaar: hij verbindt de twee kabeleinden zelf.
        StarkCables.remove(level, helper.absolutePos(switchPos));
        check(StarkGrid.available(level, device) > 0, "Tussen twee kabeleinden geeft de schakelaar (aan) geen stroom door");
        LightSwitchBlock.set(level, helper.absolutePos(switchPos), false);
        check(StarkGrid.available(level, device) == 0, "Tussen twee kabeleinden houdt de schakelaar (uit) de stroom niet tegen");
        helper.succeed();
    }

    /**
     * Stroom = aan, geen stroom = uit: een lamp achter een schakelaar die uit staat, blijft uit; zet je hem
     * aan, dan brandt de lamp; weer uit, dan meteen uit. Een generator zonder stroom laat hem ook uit.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "cables")
    public static void arc_lamp_burns_only_with_power(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos generator = new BlockPos(3, 2, 12);
        BlockPos switchPos = new BlockPos(6, 2, 12);
        BlockPos lamp = new BlockPos(9, 2, 12);
        helper.setBlock(generator, ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(generator)).fill(5_000);
        for (int x = 4; x <= 8; x++) {
            StarkCables.add(level, helper.absolutePos(new BlockPos(x, 2, 12)));
        }
        helper.setBlock(switchPos, lightSwitch(false));
        helper.setBlock(lamp, ModBlocks.ARC_LAMP.get());
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    check(!lit(helper, lamp), "De lamp brandt, maar de schakelaar staat uit");
                    LightSwitchBlock.set(level, helper.absolutePos(switchPos), true);
                })
                .thenWaitUntil(() -> check(lit(helper, lamp), "De schakelaar staat aan, maar de lamp brandt niet"))
                .thenExecute(() -> LightSwitchBlock.set(level, helper.absolutePos(switchPos), false))
                .thenExecuteAfter(2, () -> check(!lit(helper, lamp), "De schakelaar staat uit, maar de lamp brandt nog"))
                .thenExecute(() -> {
                    // Weer aan, maar de generator is leeg (en brandstof heeft hij niet): dan ook uit.
                    helper.setBlock(generator, Blocks.AIR);
                    helper.setBlock(generator, ModBlocks.HEAT_GENERATOR.get());
                    LightSwitchBlock.set(level, helper.absolutePos(switchPos), true);
                })
                .thenExecuteAfter(10, () -> check(!lit(helper, lamp), "De lamp brandt zonder stroom"))
                .thenExecute(() -> helper.setBlock(lamp, Blocks.AIR))
                .thenSucceed();
    }

    /**
     * Het licht zelf, lichtniveau 100: op 60 blokken nog vol licht (15), op 95 blokken 5 (het dooft uit
     * zoals gewoon licht), op 105 niets. Door glas gaat het, door een dichte stenen doos niet. Bouw je in
     * het licht een doos dicht, dan wordt het daarbinnen donker. Gaat de stroom eraf, dan is alles weer donker.
     *
     * Een eigen batch: dit licht reikt ver over de andere tests heen.
     */
    @GameTest(template = ARENA, timeoutTicks = 900, batch = "arc_lamp")
    public static void arc_lamp_lights_a_hundred_blocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lamp = new BlockPos(6, 2, 12);
        BlockPos generator = lamp.west();
        BlockPos far = lamp.east(60);
        BlockPos fading = lamp.east(95);
        BlockPos dark = lamp.east(105);
        // De dozen staan naast de meetlijn (4 blokken opzij), zodat het licht er niet omheen hoeft.
        BlockPos inGlass = lamp.east(20).south(4);
        BlockPos inStone = lamp.east(35).south(4);
        BlockPos laterInStone = lamp.east(50).north(4);
        // Alle chunks langs de meetlijn laden (en geladen houden), anders ziet de lamp ze niet.
        ChunkPos first = new ChunkPos(helper.absolutePos(lamp));
        ChunkPos last = new ChunkPos(helper.absolutePos(dark.east(8)));
        for (int x = first.x; x <= last.x; x++) {
            level.setChunkForced(x, first.z, true);
        }
        // Buiten de arena kan nog iets staan van een andere test: de meetlijn zelf vrijmaken. (Andere
        // dingen eromheen geven het licht alleen een langere weg; de rechte lijn blijft de kortste.)
        for (int dx = 1; dx <= 110; dx++) {
            level.setBlockAndUpdate(helper.absolutePos(lamp.east(dx)), Blocks.AIR.defaultBlockState());
        }
        box(helper, inGlass, Blocks.GLASS);
        box(helper, inStone, Blocks.STONE);
        helper.setBlock(generator, ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(generator)).fill(20_000);
        helper.setBlock(lamp, ModBlocks.ARC_LAMP.get());
        helper.startSequence()
                .thenWaitUntil(slowly(() -> {
                    check(lit(helper, lamp), "De lamp brandt niet, terwijl hij tegen een volle generator staat");
                    check(light(helper, far) == 15, "60 blokken ver zou het vol licht moeten zijn, niet " + light(helper, far));
                    check(light(helper, fading) == 5, "95 blokken ver zou het licht 5 moeten zijn, niet " + light(helper, fading));
                    check(light(helper, dark) == 0, "105 blokken ver zou het donker moeten zijn, niet " + light(helper, dark));
                    check(light(helper, inGlass) == 15, "Het licht gaat niet door glas: " + light(helper, inGlass));
                    check(light(helper, inStone) == 0, "Het licht gaat door een stenen doos heen: " + light(helper, inStone));
                    check(ArcLampLight.isLit(level, helper.absolutePos(far)), "De lamp weet niet dat hij 60 blokken ver schijnt");
                }))
                // Een doos dichtbouwen in het licht: daarbinnen wordt het donker (de lamp meet opnieuw).
                .thenExecute(() -> {
                    check(light(helper, laterInStone) == 15, "Op 50 blokken zou het vol licht moeten zijn");
                    box(helper, laterInStone, Blocks.STONE);
                })
                .thenWaitUntil(slowly(() -> check(light(helper, laterInStone) == 0,
                        "In de nieuwe stenen doos blijft het licht: " + light(helper, laterInStone))))
                // Stroom eraf: alles weer donker.
                .thenExecute(() -> helper.setBlock(generator, Blocks.AIR))
                .thenWaitUntil(slowly(() -> {
                    check(!lit(helper, lamp), "Zonder generator brandt de lamp nog");
                    check(light(helper, far) == 0, "Zonder stroom is het 60 blokken ver nog licht: " + light(helper, far));
                    check(light(helper, fading) == 0, "Zonder stroom is het 95 blokken ver nog licht: " + light(helper, fading));
                    check(light(helper, inGlass) == 0, "Zonder stroom is het in de glazen doos nog licht");
                    check(ArcLampLight.settled(level), "De lamp is nog bezig");
                }))
                .thenExecute(() -> {
                    helper.setBlock(lamp, Blocks.AIR);
                    for (BlockPos centre : new BlockPos[] {inGlass, inStone, laterInStone}) {
                        clearBox(helper, centre);
                    }
                    for (int x = first.x; x <= last.x; x++) {
                        level.setChunkForced(x, first.z, false);
                    }
                })
                .thenSucceed();
    }

    // ================================================================ Hulpjes

    /**
     * Wachten op iets dat op een andere thread gebeurt (de meting van de lamp, en het licht-rekenwerk van het
     * spel). De testserver draait zijn ticks zo snel als hij kan, soms honderden per seconde; die andere
     * threads rekenen in echte tijd. Lukt het nog niet, dan wacht deze test daarom elke tick even 10 ms,
     * zodat zijn tijdslimiet in ticks ook genoeg echte tijd is.
     */
    private static Runnable slowly(Runnable check) {
        return () -> {
            try {
                check.run();
            } catch (GameTestAssertException notYet) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                throw notYet;
            }
        };
    }

    private static boolean lit(GameTestHelper helper, BlockPos lamp) {
        BlockState state = helper.getBlockState(lamp);
        return state.is(ModBlocks.ARC_LAMP.get()) && state.getValue(ArcLampBlock.LIT);
    }

    /** Het licht van blokken (niet van de zon) op deze plek. */
    private static int light(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getBrightness(LightLayer.BLOCK, helper.absolutePos(pos));
    }

    /** Een dichte doos van 3 bij 3 bij 3 met {@code centre} als holle binnenkant. */
    private static void box(GameTestHelper helper, BlockPos centre, Block block) {
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, -1, -1), centre.offset(1, 1, 1))) {
            if (!pos.equals(centre)) {
                helper.getLevel().setBlockAndUpdate(helper.absolutePos(pos), block.defaultBlockState());
            }
        }
    }

    private static void clearBox(GameTestHelper helper, BlockPos centre) {
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-1, -1, -1), centre.offset(1, 1, 1))) {
            helper.getLevel().setBlockAndUpdate(helper.absolutePos(pos), Blocks.AIR.defaultBlockState());
        }
    }
}
