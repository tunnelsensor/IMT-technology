package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.energy.PowerCables;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.light.ArcLampBlock;
import com.senne.imttech.light.ArcLampBlockEntity;
import com.senne.imttech.light.ArcLampLight;
import com.senne.imttech.light.ArcLamps;
import com.senne.imttech.light.LightSwitchBlock;
import com.senne.imttech.network.LampActionPayload;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.station.ChargerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de Arc Lamp en de lichtschakelaar (9 oktober). Jouw wens: "een lamp die op stroom werkt die
 * nog veel helderder kan als lightlevel 15, ik wil een van 100 ... stroom is aan, geen stroom is uit", en
 * "een lightswitch die je tussen een cable kunt zetten om zo de lichten te besturen".
 */
@GameTestHolder(ImtTech.MODID)
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
            PowerCables.add(level, helper.absolutePos(new BlockPos(x, 2, 6)));
        }
        BlockPos switchPos = new BlockPos(6, 2, 6);
        helper.setBlock(switchPos, lightSwitch(true));
        BlockPos device = helper.absolutePos(new BlockPos(10, 2, 6));
        check(PowerGrid.available(level, device) > 0, "Een schakelaar die aan staat, houdt de stroom tegen");
        LightSwitchBlock.set(level, helper.absolutePos(switchPos), false);
        check(PowerGrid.available(level, device) == 0, "De schakelaar staat uit, maar de stroom loopt door de kabel eronder door");
        LightSwitchBlock.set(level, helper.absolutePos(switchPos), true);
        check(PowerGrid.available(level, device) > 0, "De schakelaar staat weer aan, maar er komt geen stroom");
        // Zonder kabel onder de schakelaar: hij verbindt de twee kabeleinden zelf.
        PowerCables.remove(level, helper.absolutePos(switchPos));
        check(PowerGrid.available(level, device) > 0, "Tussen twee kabeleinden geeft de schakelaar (aan) geen stroom door");
        LightSwitchBlock.set(level, helper.absolutePos(switchPos), false);
        check(PowerGrid.available(level, device) == 0, "Tussen twee kabeleinden houdt de schakelaar (uit) de stroom niet tegen");
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
            PowerCables.add(level, helper.absolutePos(new BlockPos(x, 2, 12)));
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

    /**
     * De helderheid instellen (10 oktober: "zorg dat je de brightness kunt instellen"). Op lichtniveau 40 is
     * het tot 25 blokken vol licht (40 min 15), op 30 blokken 10, op 40 donker. Op niveau 10 is het een
     * gewone, zwakke lamp: het blok geeft zelf 10, op 5 blokken is het 5, en er zijn geen onzichtbare
     * lichtbronnen meer. Het scherm (via de server) werkt alleen als je dichtbij staat, en een lamp op 50
     * gebruikt de helft van de stroom.
     *
     * Een eigen batch: dit licht reikt over de andere tests heen.
     */
    @GameTest(template = ARENA, timeoutTicks = 900, batch = "arc_lamp_dim")
    @SuppressWarnings("removal")
    public static void arc_lamp_brightness_can_be_set(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lamp = new BlockPos(6, 2, 12);
        BlockPos generator = lamp.west();
        ChunkPos first = new ChunkPos(helper.absolutePos(lamp));
        ChunkPos last = new ChunkPos(helper.absolutePos(lamp.east(60)));
        for (int x = first.x; x <= last.x; x++) {
            level.setChunkForced(x, first.z, true);
        }
        for (int dx = 1; dx <= 55; dx++) {
            level.setBlockAndUpdate(helper.absolutePos(lamp.east(dx)), Blocks.AIR.defaultBlockState());
        }
        helper.setBlock(generator, ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(generator)).fill(20_000);
        helper.setBlock(lamp, ModBlocks.ARC_LAMP.get());
        ArcLampBlockEntity entity = (ArcLampBlockEntity) helper.getBlockEntity(lamp);
        check(entity.brightness() == 100, "Een nieuwe lamp zou op lichtniveau 100 moeten staan, niet " + entity.brightness());

        // Via het scherm: alleen wie dichtbij staat.
        ServerPlayer far = helper.makeMockServerPlayerInLevel();
        far.moveTo(helper.absolutePos(lamp).getX() + 30.5, helper.absolutePos(lamp).getY(), helper.absolutePos(lamp).getZ() + 0.5);
        ArcLamps.handleAction(far, new LampActionPayload(helper.absolutePos(lamp), LampActionPayload.Op.BRIGHTNESS, 70));
        check(entity.brightness() == 100, "Iemand op 30 blokken afstand zette de lamp op " + entity.brightness());
        ServerPlayer near = helper.makeMockServerPlayerInLevel();
        near.moveTo(helper.absolutePos(lamp).getX() + 2.5, helper.absolutePos(lamp).getY(), helper.absolutePos(lamp).getZ() + 0.5);
        ArcLamps.handleAction(near, new LampActionPayload(helper.absolutePos(lamp), LampActionPayload.Op.BRIGHTNESS, 40));
        check(entity.brightness() == 40, "Het scherm zette de lamp niet op 40, maar op " + entity.brightness());
        check(Math.abs(ArcLampBlockEntity.powerPerTick(50) - ArcLampBlockEntity.powerPerTick(100) / 2) < 1.0E-9,
                "Een lamp op 50 zou de helft moeten gebruiken van een lamp op 100");

        helper.startSequence()
                .thenWaitUntil(slowly(() -> {
                    check(lit(helper, lamp), "De lamp brandt niet, terwijl hij tegen een volle generator staat");
                    check(light(helper, lamp.east(20)) == 15, "Op niveau 40 zou het op 20 blokken vol licht moeten zijn, niet "
                            + light(helper, lamp.east(20)));
                    check(light(helper, lamp.east(30)) == 10, "Op niveau 40 zou het op 30 blokken 10 moeten zijn, niet "
                            + light(helper, lamp.east(30)));
                    check(light(helper, lamp.east(40)) == 0, "Op niveau 40 zou het op 40 blokken donker moeten zijn, niet "
                            + light(helper, lamp.east(40)));
                    check(ArcLampLight.settled(level), "De lamp is nog bezig");
                }))
                .thenExecute(() -> entity.setBrightness(10))
                .thenWaitUntil(slowly(() -> {
                    check(helper.getBlockState(lamp).getValue(ArcLampBlock.LIGHT) == 10,
                            "Op niveau 10 zou het blok zelf 10 licht moeten geven");
                    check(light(helper, lamp.east(5)) == 5, "Op niveau 10 zou het op 5 blokken 5 moeten zijn, niet "
                            + light(helper, lamp.east(5)));
                    check(light(helper, lamp.east(20)) == 0, "Op niveau 10 zou het op 20 blokken donker moeten zijn, niet "
                            + light(helper, lamp.east(20)));
                    check(!ArcLampLight.isLit(level, helper.absolutePos(lamp.east(5))),
                            "Een lamp tot 15 heeft geen onzichtbare lichtbronnen nodig");
                    check(ArcLampLight.settled(level), "De lamp is nog bezig");
                }))
                .thenExecute(() -> {
                    helper.setBlock(lamp, Blocks.AIR);
                    for (int x = first.x; x <= last.x; x++) {
                        level.setChunkForced(x, first.z, false);
                    }
                })
                .thenSucceed();
    }

    /**
     * Licht per kamer (13 oktober: "zorg dat het licht niet door muren kan, dat je echt licht per kamer moet
     * hebben"). Twee stenen kamers naast elkaar, met een deur in de muur ertussen. De lamp zit in het
     * plafond van de linkerkamer (de generator staat erbovenop, op het dak).
     * <ul>
     *   <li>Deur dicht: de hele linkerkamer vol licht, tot in de hoeken en langs het plafond; in de
     *       rechterkamer geen enkele plek van de lamp. Daar komt alleen wat gewoon licht onder de deur door
     *       (zoals bij een fakkel), dus achterin is het niet meer dan 10.</li>
     *   <li>Deur open: door de deuropening valt een bundel, maar naast de deur en in de verre hoek blijft het
     *       donker.</li>
     *   <li>Deur weer dicht: de bundel is weg.</li>
     * </ul>
     * Een eigen batch: zo stoort deze lamp de andere lamp-tests niet.
     */
    @GameTest(template = ARENA, timeoutTicks = 900, batch = "arc_lamp_rooms")
    public static void arc_lamp_light_stays_in_its_room(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Linkerkamer: binnen x 2-6, y 3-5, z 2-6. Rechterkamer: binnen x 8-12. De muur ertussen: x 7.
        hollowBox(helper, new BlockPos(1, 2, 1), new BlockPos(7, 6, 7));
        hollowBox(helper, new BlockPos(7, 2, 1), new BlockPos(13, 6, 7));
        BlockPos doorLow = new BlockPos(7, 3, 4);
        door(helper, doorLow, false);
        BlockPos lamp = new BlockPos(4, 6, 4);
        BlockPos generator = lamp.above();
        helper.setBlock(generator, ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(generator)).fill(20_000);
        helper.setBlock(lamp, ModBlocks.ARC_LAMP.get());
        BlockPos[] leftCorners = {new BlockPos(2, 3, 2), new BlockPos(6, 3, 6), new BlockPos(2, 5, 6), new BlockPos(6, 5, 2)};
        BlockPos beam = new BlockPos(10, 3, 4);       // recht door de deuropening
        BlockPos besideDoor = new BlockPos(8, 3, 6);  // vlak achter de muur, naast de deur
        BlockPos farCorner = new BlockPos(12, 5, 2);  // de verre hoek, uit de bundel
        BlockPos backOfRight = new BlockPos(12, 3, 6);
        helper.startSequence()
                .thenWaitUntil(slowly(() -> {
                    check(lit(helper, lamp), "De lamp brandt niet, terwijl er een volle generator op staat");
                    for (BlockPos corner : leftCorners) {
                        check(ArcLampLight.isLit(level, helper.absolutePos(corner)), "De lamp verlicht zijn eigen kamer niet in de hoek " + corner);
                    }
                    check(light(helper, leftCorners[0]) == 15, "In de hoek van de eigen kamer zou het 15 moeten zijn, niet "
                            + light(helper, leftCorners[0]));
                    check(ArcLampLight.settled(level), "De lamp is nog bezig");
                }))
                .thenExecute(() -> {
                    for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(8, 3, 2), new BlockPos(12, 5, 6))) {
                        check(!ArcLampLight.isLit(level, helper.absolutePos(pos)),
                                "Het licht van de lamp komt door de dichte deur, in de andere kamer op " + pos.immutable());
                    }
                    check(light(helper, backOfRight) <= 10, "Achterin de andere kamer is het nog " + light(helper, backOfRight));
                    door(helper, doorLow, true);
                })
                // Deur open: een bundel door de opening, maar niet de hele kamer.
                .thenWaitUntil(slowly(() -> {
                    check(ArcLampLight.isLit(level, helper.absolutePos(beam)), "Door de open deur valt geen licht");
                    check(light(helper, beam) == 15, "In de bundel door de deur zou het 15 moeten zijn, niet " + light(helper, beam));
                    check(ArcLampLight.settled(level), "De lamp is nog bezig");
                }))
                .thenExecute(() -> {
                    check(!ArcLampLight.isLit(level, helper.absolutePos(besideDoor)), "Het licht gaat om de hoek van de deur");
                    check(!ArcLampLight.isLit(level, helper.absolutePos(farCorner)), "Het licht vult de hele andere kamer");
                    door(helper, doorLow, false);
                })
                // Deur weer dicht: de bundel is weg.
                .thenWaitUntil(slowly(() -> {
                    check(!ArcLampLight.isLit(level, helper.absolutePos(beam)), "De deur is dicht, maar de bundel is er nog");
                    check(light(helper, backOfRight) <= 10, "De deur is dicht, maar achterin is het nog " + light(helper, backOfRight));
                    check(ArcLampLight.settled(level), "De lamp is nog bezig");
                }))
                .thenExecute(() -> {
                    helper.setBlock(lamp, Blocks.AIR);
                    for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(1, 2, 1), new BlockPos(13, 7, 7))) {
                        helper.getLevel().setBlock(helper.absolutePos(pos), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                })
                .thenSucceed();
    }

    // ================================================================ Hulpjes

    /** Een holle doos van steen van {@code from} tot en met {@code to} (vloer, plafond en vier muren). */
    private static void hollowBox(GameTestHelper helper, BlockPos from, BlockPos to) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            boolean wall = pos.getX() == from.getX() || pos.getX() == to.getX() || pos.getY() == from.getY()
                    || pos.getY() == to.getY() || pos.getZ() == from.getZ() || pos.getZ() == to.getZ();
            helper.getLevel().setBlock(helper.absolutePos(pos),
                    (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Een eikenhouten deur (twee blokken hoog, onderkant op {@code low}), open of dicht. */
    private static void door(GameTestHelper helper, BlockPos low, boolean open) {
        BlockState state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST)
                .setValue(DoorBlock.OPEN, open);
        helper.getLevel().setBlock(helper.absolutePos(low), state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
        helper.getLevel().setBlock(helper.absolutePos(low.above()), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
    }

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
