package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.bot.BotBoard;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.energy.Cables;
import com.senne.imttech.lan.LanGrid;
import com.senne.imttech.network.CameraWallSavePayload;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.robot.GuardRobotEntity;
import com.senne.imttech.robot.RobotStyle;
import com.senne.imttech.security.CameraBlock;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.wall.CameraMonitorBlock;
import com.senne.imttech.security.wall.CameraMonitorBlockEntity;
import com.senne.imttech.security.wall.CameraWall;
import com.senne.imttech.station.ChargerBlockEntity;
import com.senne.imttech.weapons.Emp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de schermmuur (19 oktober, jouw notitie: "ik wil fysieke cameraschermen, dit is een multiblock"): monitoren worden
 * samen één scherm als het een rechthoek is, hebben een netwerkkabel en stroom nodig, en de server kijkt elke indeling zelf na.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class CameraWallGameTests {
    private CameraWallGameTests() {}

    private static final String ARENA = "empty";

    /** Vóór de tests van deze batch: beveiligingsapparaten hebben een netwerkkabel nodig (zoals in een echte wereld). */
    @net.minecraft.gametest.framework.BeforeBatch(batch = "camera_wall")
    public static void networkOn(ServerLevel level) {
        LanGrid.overrideRequired(true);
    }

    /** Daarna weer de oude regel, voor de andere tests. */
    @net.minecraft.gametest.framework.AfterBatch(batch = "camera_wall")
    public static void networkOff(ServerLevel level) {
        LanGrid.overrideRequired(null);
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Een monitor op deze plek, die deze kant op kijkt. */
    private static void monitor(GameTestHelper helper, BlockPos pos, Direction facing) {
        helper.setBlock(pos, ModBlocks.CAMERA_MONITOR.get().defaultBlockState().setValue(CameraMonitorBlock.FACING, facing));
    }

    private static CameraWall.Shape shape(GameTestHelper helper, BlockPos pos) {
        return CameraWall.of(helper.getLevel(), helper.absolutePos(pos));
    }

    /** Een volle warmtegenerator op deze plek (stroom voor wat ertegenaan staat). */
    private static void generator(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(pos)).fill(20_000);
    }

    /** Een kern met stroom en een systeem, van deze eigenaar. */
    private static SecurityCoreBlockEntity poweredCore(GameTestHelper helper, BlockPos pos, ServerPlayer owner) {
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, pos, owner);
        core.ensureSystem(helper.getLevel());
        core.powerTick(helper.getLevel());
        return core;
    }

    /** Een netwerkkabel langs deze plekken. */
    private static void cable(GameTestHelper helper, CableKind kind, BlockPos... line) {
        for (BlockPos pos : line) {
            Cables.add(kind, helper.getLevel(), helper.absolutePos(pos));
        }
    }

    private static CameraMonitorBlockEntity entity(GameTestHelper helper, BlockPos pos) {
        return (CameraMonitorBlockEntity) helper.getBlockEntity(pos);
    }

    private static boolean on(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(CameraMonitorBlock.ON);
    }

    /** Eén tegel voor een indeling: kolom, rij, breedte, hoogte en de plek van de camera. */
    private static CompoundTag tile(int column, int row, int width, int height, BlockPos camera) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("c", column);
        tag.putInt("r", row);
        tag.putInt("w", width);
        tag.putInt("h", height);
        tag.putLong("cam", camera.asLong());
        return tag;
    }

    /**
     * Eén monitor is 1 x 1; ernaast 2 x 1; twee rijen 2 x 2, met linksboven de monitor die je linksboven ziet als je ervoor
     * staat. Een monitor erbij buiten de rechthoek: geen scherm meer. Een monitor die de andere kant op kijkt, hoort er niet
     * bij. Breder dan 8: te groot. De randjes (het blok) volgen wie ernaast staat.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "camera_wall_shape")
    public static void camera_monitors_merge_into_a_rectangle(GameTestHelper helper) {
        Direction facing = Direction.SOUTH; // rechts is dan oost: van x klein naar x groot
        BlockPos start = new BlockPos(4, 3, 4);
        monitor(helper, start, facing);
        check(shape(helper, start).width() == 1 && shape(helper, start).height() == 1 && shape(helper, start).valid(),
                "Eén monitor is geen 1 x 1 scherm: " + shape(helper, start));
        monitor(helper, start.east(), facing);
        CameraWall.Shape two = shape(helper, start);
        check(two.width() == 2 && two.height() == 1 && two.valid(), "Twee naast elkaar zijn geen 2 x 1: " + two);
        check(helper.getBlockState(start).getValue(CameraMonitorBlock.RIGHT), "Het randje rechts ging niet weg");
        check(!helper.getBlockState(start).getValue(CameraMonitorBlock.LEFT), "Er is een randje links verdwenen zonder buur");
        monitor(helper, start.above(), facing);
        CameraWall.Shape ell = shape(helper, start);
        check(!ell.valid() && !ell.rectangle(), "Een L-vorm telt als scherm: " + ell);
        monitor(helper, start.east().above(), facing);
        CameraWall.Shape four = shape(helper, start.east());
        check(four.width() == 2 && four.height() == 2 && four.valid(), "Vier zijn geen 2 x 2: " + four);
        check(four.anchor().equals(helper.absolutePos(start.above())), "Linksboven is niet de monitor linksboven: " + four.anchor());
        check(four.at(1, 1).equals(helper.absolutePos(start.east())), "Rechtsonder is niet de monitor rechtsonder");
        check(four.columnOf(helper.absolutePos(start.east())) == 1 && four.rowOf(helper.absolutePos(start.east())) == 1,
                "De kolom en rij van rechtsonder kloppen niet");
        // De andere kant op: hoort er niet bij (en zijn buren niet bij hem).
        monitor(helper, start.west(), Direction.NORTH);
        check(shape(helper, start).width() == 2, "Een monitor die de andere kant op kijkt, hoort erbij");
        check(shape(helper, start.west()).width() == 1, "De monitor die de andere kant op kijkt, zit aan het scherm vast");
        helper.setBlock(start.west(), Blocks.AIR);
        // Bij een monitor die naar het oosten kijkt, is rechts het noorden.
        BlockPos side = new BlockPos(14, 3, 8);
        monitor(helper, side, Direction.EAST);
        monitor(helper, side.north(), Direction.EAST);
        CameraWall.Shape east = shape(helper, side);
        check(east.width() == 2 && east.anchor().equals(helper.absolutePos(side)) && east.right() == Direction.NORTH,
                "Naar het oosten klopt links en rechts niet: " + east);
        // Te groot: negen op een rij.
        BlockPos row = new BlockPos(2, 3, 12);
        for (int x = 0; x < 9; x++) {
            monitor(helper, row.east(x), facing);
        }
        CameraWall.Shape long9 = shape(helper, row);
        check(long9.tooBig() && !long9.valid(), "Negen op een rij is niet te groot: " + long9);
        helper.setBlock(row.east(8), Blocks.AIR);
        CameraWall.Shape long8 = shape(helper, row);
        check(long8.width() == 8 && long8.valid(), "Acht op een rij is geen goed scherm: " + long8);
        helper.succeed();
    }

    /**
     * Zonder netwerkkabel blijft het scherm zwart, ook met stroom; met een kabel aan één monitor en een generator tegen een
     * andere werkt het hele scherm. Een monitor erbij buiten de rechthoek: alles uit. Een EMP-puls legt het even plat.
     */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "camera_wall")
    public static void camera_wall_needs_network_and_power(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 6), "wall-owner");
        generator(helper, new BlockPos(2, 2, 2));
        poweredCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos left = new BlockPos(5, 3, 8);
        BlockPos right = left.east();
        monitor(helper, left, Direction.SOUTH);
        monitor(helper, right, Direction.SOUTH);
        ServerLevel level = helper.getLevel();
        helper.runAfterDelay(2, () -> {
            entity(helper, left).updateOn(level);
            check(!on(helper, left), "Zonder netwerkkabel ging de monitor aan");
            // De kabel naar één monitor (door de andere gaat het netwerk verder), nog zonder stroom.
            cable(helper, CableKind.NETWORK, new BlockPos(3, 3, 2), new BlockPos(4, 3, 2), new BlockPos(5, 3, 2), new BlockPos(5, 3, 3),
                    new BlockPos(5, 3, 4), new BlockPos(5, 3, 5), new BlockPos(5, 3, 6), new BlockPos(5, 3, 7));
        });
        helper.runAfterDelay(4, () -> {
            entity(helper, right).updateOn(level);
            check(!on(helper, right), "Zonder stroom ging de monitor aan");
            generator(helper, left.west());
        });
        helper.runAfterDelay(6, () -> {
            entity(helper, left).updateOn(level);
            entity(helper, right).updateOn(level);
            check(on(helper, left), "De monitor met de kabel en de generator ging niet aan");
            check(on(helper, right), "De monitor ernaast kreeg geen netwerk en stroom door de eerste");
            Emp.blast(level, helper.absoluteVec(Vec3.atCenterOf(left)), 4, 10, null);
            check(!on(helper, left), "Een EMP-puls legde het scherm niet plat");
        });
        helper.runAfterDelay(25, () -> {
            entity(helper, left).updateOn(level);
            check(on(helper, left), "Na de EMP-puls ging de monitor niet weer aan");
            // Een monitor erbij buiten de rechthoek: geen goed scherm meer, dus alles uit.
            monitor(helper, left.above(), Direction.SOUTH);
        });
        helper.runAfterDelay(27, () -> {
            entity(helper, left).updateOn(level);
            check(!on(helper, left), "Een monitor die geen rechthoek maakt, liet het scherm aan");
            helper.setBlock(left.above(), Blocks.AIR);
        });
        helper.runAfterDelay(29, () -> {
            entity(helper, left).updateOn(level);
            check(on(helper, left), "Rechthoek hersteld, maar het scherm ging niet weer aan");
            helper.succeed();
        });
    }

    /**
     * De indeling: een tegel staat in zijn linkerbovenhoek, ook na opslaan en laden, en hij heeft beeld als de camera dat heeft.
     * De server laat niets door wat niet klopt: buiten het scherm, over een andere tegel, of een camera die niet op dit net hangt.
     * Een vreemde mag niets veranderen, de eigenaar wel. Haal je een monitor weg, dan blijft de rest staan.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "camera_wall")
    public static void camera_wall_layout_is_checked_by_the_server(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 6), "layout-owner");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 8), "layout-stranger");
        generator(helper, new BlockPos(2, 2, 2));
        SecurityCoreBlockEntity core = poweredCore(helper, new BlockPos(2, 3, 2), owner);
        ServerLevel level = helper.getLevel();
        // Een scherm van 3 x 2, naar het zuiden; een PoE-kabel geeft netwerk en stroom (de kern voedt hem).
        BlockPos bottomLeft = new BlockPos(5, 3, 8);
        for (int x = 0; x < 3; x++) {
            monitor(helper, bottomLeft.east(x), Direction.SOUTH);
            monitor(helper, bottomLeft.east(x).above(), Direction.SOUTH);
        }
        cable(helper, CableKind.POE, new BlockPos(3, 3, 2), new BlockPos(4, 3, 2), new BlockPos(5, 3, 2), new BlockPos(5, 3, 3),
                new BlockPos(5, 3, 4), new BlockPos(5, 3, 5), new BlockPos(5, 3, 6), new BlockPos(5, 3, 7));
        // Een camera op dat net, en een camera die nergens aan hangt.
        BlockPos lens = new BlockPos(4, 3, 3);
        helper.setBlock(lens, ModBlocks.SECURITY_CAMERA.get().defaultBlockState().setValue(CameraBlock.FACE,
                net.minecraft.world.level.block.state.properties.AttachFace.FLOOR).setValue(CameraBlock.FACING, Direction.SOUTH));
        BlockPos loose = new BlockPos(18, 3, 18);
        helper.setBlock(loose, ModBlocks.SECURITY_CAMERA.get().defaultBlockState().setValue(CameraBlock.FACE,
                net.minecraft.world.level.block.state.properties.AttachFace.FLOOR).setValue(CameraBlock.FACING, Direction.SOUTH));
        BlockPos camera = helper.absolutePos(lens);
        BlockPos topLeft = bottomLeft.above();
        helper.runAfterDelay(2, () -> {
            com.senne.imttech.security.SecurityDevices.forget(level, core);
            ((com.senne.imttech.security.CameraBlockEntity) helper.getBlockEntity(lens)).updateOnline(level, camera, level.getBlockState(camera));
            entity(helper, topLeft).updateOn(level);
            CameraWall.Shape wall = shape(helper, topLeft);
            check(wall.valid() && wall.width() == 3 && wall.height() == 2, "Het scherm is geen 3 x 2: " + wall);
            ListTag tiles = new ListTag();
            tiles.add(tile(0, 0, 2, 2, camera));                         // goed: links, 2 x 2
            tiles.add(tile(1, 1, 2, 1, camera));                         // ligt over de eerste
            tiles.add(tile(2, 0, 2, 1, camera));                         // valt buiten het scherm
            tiles.add(tile(2, 1, 1, 1, helper.absolutePos(loose)));      // een camera die niet op dit net hangt
            tiles.add(tile(2, 0, 1, 1, camera));                         // goed: rechtsboven
            CompoundTag args = new CompoundTag();
            args.put("tiles", tiles);
            CameraWall.handleSave(stranger, new CameraWallSavePayload(wall.anchor(), args));
            check(entity(helper, topLeft).tile() == null, "Een vreemde veranderde het scherm");
            CameraWall.handleSave(owner, new CameraWallSavePayload(wall.anchor(), args));
            CameraMonitorBlockEntity.Tile first = entity(helper, topLeft).tile();
            check(first != null && first.width() == 2 && first.height() == 2 && camera.equals(first.camera()),
                    "De goede tegel staat er niet: " + first);
            check(entity(helper, topLeft.east(2)).tile() != null, "De tegel rechtsboven staat er niet");
            check(entity(helper, topLeft.east()).tile() == null && entity(helper, bottomLeft.east()).tile() == null,
                    "Een tegel over de eerste kwam erdoor");
            check(entity(helper, bottomLeft.east(2)).tile() == null, "De camera die niet op dit net hangt, kwam erdoor");
            check(entity(helper, topLeft).live(), "De tegel heeft geen beeld, terwijl de camera en de monitor werken");
            check(entity(helper, topLeft).label().startsWith("Camera"), "De tegel heeft niet de naam van de camera: " + entity(helper, topLeft).label());
            // Opslaan en laden.
            var registries = level.registryAccess();
            BlockState state = helper.getBlockState(topLeft);
            CameraMonitorBlockEntity copy = new CameraMonitorBlockEntity(helper.absolutePos(topLeft), state);
            copy.loadWithComponents(entity(helper, topLeft).saveWithFullMetadata(registries), registries);
            check(first.equals(copy.tile()) && copy.live(), "Na opslaan en laden was de tegel anders: " + copy.tile());
            // Een monitor van een andere tegel weg: de eerste tegel staat er nog, het scherm is alleen geen rechthoek meer.
            helper.setBlock(topLeft.east(2), Blocks.AIR);
            check(entity(helper, topLeft).tile() != null, "De eerste tegel verdween mee met een andere monitor");
            check(!shape(helper, topLeft).valid(), "Met een gat erin telt het nog als scherm");
            // De camera weg: geen beeld meer (bij de volgende controle).
            helper.setBlock(topLeft.east(2), ModBlocks.CAMERA_MONITOR.get().defaultBlockState().setValue(CameraMonitorBlock.FACING, Direction.SOUTH));
            helper.setBlock(lens, Blocks.AIR);
            com.senne.imttech.security.SecurityDevices.forget(level, core);
            entity(helper, topLeft).updateOn(level);
            check(!entity(helper, topLeft).live(), "De camera is weg, maar de tegel heeft nog beeld");
            helper.succeed();
        });
    }

    /**
     * Ook een robot met een Camera-module kan op het scherm, als hij bij deze kern hoort; zijn tegel heeft beeld zolang zijn
     * camera aan staat. Staat er iemand bij het scherm, dan weet de robot dat er meegekeken wordt ({@code read(cameraPin)}).
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "camera_wall")
    public static void camera_wall_shows_a_robot(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 6), "wall-robot-owner");
        generator(helper, new BlockPos(2, 2, 2));
        SecurityCoreBlockEntity core = poweredCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos rack = new BlockPos(1, 3, 2); // tegen de kern: zo hangt hij zonder kabel aan het netwerk
        ImtTechGameTests.placeServer(helper, rack, core);
        BlockPos at = new BlockPos(5, 3, 8);
        monitor(helper, at, Direction.SOUTH);
        cable(helper, CableKind.POE, new BlockPos(3, 3, 2), new BlockPos(4, 3, 2), new BlockPos(5, 3, 2), new BlockPos(5, 3, 3),
                new BlockPos(5, 3, 4), new BlockPos(5, 3, 5), new BlockPos(5, 3, 6), new BlockPos(5, 3, 7));
        GuardRobotEntity robot = BotSenseGameTests.robot(helper, new BlockPos(9, 2, 12), RobotStyle.COMMANDO,
                BotBoard.EMPTY.with(6, "camera"), "when ordered(\"shy\"):\n    write(pin(6), false)\n");
        GuardRobotEntity blind = BotSenseGameTests.robot(helper, new BlockPos(12, 2, 12), RobotStyle.COMMANDO, BotBoard.EMPTY,
                "when started:\n    pass\n");
        robot.setOwner(owner);
        blind.setOwner(owner);
        robot.setCustomName(net.minecraft.network.chat.Component.literal("Guard 7"));
        ServerLevel level = helper.getLevel();
        helper.runAfterDelay(5, () -> {
            ImtTechGameTests.startServer(helper, rack, core);
            entity(helper, at).updateOn(level);
            check(on(helper, at), "De monitor ging niet aan");
            CameraWall.Shape wall = shape(helper, at);
            // Een robot zonder camera kan er niet op.
            CompoundTag noCamera = new CompoundTag();
            noCamera.putInt("w", 1);
            noCamera.putInt("h", 1);
            noCamera.putString("id", blind.getUUID().toString());
            ListTag first = new ListTag();
            first.add(noCamera);
            CameraWall.setLayout(level, core, wall, first);
            check(entity(helper, at).tile() == null, "Een robot zonder Camera-module kwam op het scherm");
            CompoundTag tile = new CompoundTag();
            tile.putInt("w", 1);
            tile.putInt("h", 1);
            tile.putString("id", robot.getUUID().toString());
            ListTag second = new ListTag();
            second.add(tile);
            CameraWall.setLayout(level, core, wall, second);
            CameraMonitorBlockEntity entity = entity(helper, at);
            check(entity.tile() != null && robot.getUUID().equals(entity.tile().robot()), "De robot kwam niet op het scherm");
            check(entity.live() && entity.robotEntity() == robot.getId() && entity.label().equals("Guard 7"),
                    "De tegel van de robot klopt niet: " + entity.live() + ", " + entity.robotEntity() + ", " + entity.label());
            check(Boolean.TRUE.equals(robot.brain().pins().read(6)), "De robot weet niet dat er meegekeken wordt");
            robot.brain().order("shy", null);
        });
        helper.runAfterDelay(10, () -> {
            entity(helper, at).updateOn(level);
            check(!entity(helper, at).live(), "De camera van de robot staat uit, maar de tegel heeft beeld");
            helper.succeed();
        });
    }

    // ================================================================ Hoofdstuk 93: hernoemen met sneak + rechtsklik

    /** Sneak + rechtsklik met een lege hand op de camera (of gewoon klikken, zonder sneak). */
    private static void click(GameTestHelper helper, ServerPlayer player, BlockPos pos, boolean sneak) {
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
        player.setShiftKeyDown(sneak);
        BlockPos abs = helper.absolutePos(pos);
        helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false));
        player.setShiftKeyDown(false);
    }

    private static com.senne.imttech.security.CameraBlockEntity camera(GameTestHelper helper, BlockPos pos) {
        return (com.senne.imttech.security.CameraBlockEntity) helper.getBlockEntity(pos);
    }

    /** Save in het naamscherm van de camera, zoals jouw spel het stuurt. */
    private static void renameInWorld(GameTestHelper helper, ServerPlayer player, BlockPos pos, String name) {
        com.senne.imttech.security.CameraNames.handleRename(player,
                new com.senne.imttech.network.CameraRenamePayload(helper.absolutePos(pos), name));
    }

    /**
     * Hoofdstuk 93, jouw wens: "Kun je ook zorgen dat je cam kunt renamen door shift klik zoals bij een keycardreader".
     * Wie de camera neerzet, is zijn eigenaar en hernoemt hem (de naam wordt netjes, en ook zijn naam in de I.M.T-lijst
     * verandert mee; leeg mag niet, van ver weg ook niet); een vreemde niet. Een camera zonder eigenaar (van vóór vandaag)
     * wordt van wie er als eerste op sneak-klikt; gewoon klikken doet dat niet. (Een eigen batch met een klein bereik van
     * de zones, zoals bij de lezer.)
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "camera_rename")
    public static void camera_rename_by_sneak_click(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 2, 1), "camera-owner");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 2, 1), "camera-stranger");
        BlockPos wall = new BlockPos(3, 3, 4);
        helper.setBlock(wall, Blocks.STONE);
        ImtTechGameTests.withReach(4, () -> {
            // Neergezet met de muis, tegen de noordkant van een steen: dan is de eigenaar wie hem neerzette.
            check(ZoneGameTests.place(helper, owner, com.senne.imttech.registry.ModItems.SECURITY_CAMERA.get(), wall, Direction.NORTH),
                    "Neerzetten lukte niet");
            BlockPos cam = wall.north();
            BlockPos abs = helper.absolutePos(cam);
            check(helper.getBlockState(cam).getBlock() instanceof CameraBlock, "Er hangt geen camera");
            check(owner.getUUID().equals(camera(helper, cam).owner()), "Wie hem neerzette, is niet de eigenaar");
            check(camera(helper, cam).ownerName().equals("camera-owner"), "De naam van de eigenaar klopt niet");
            String first = camera(helper, cam).name();
            check(!first.isEmpty(), "De camera kreeg bij het neerzetten geen naam");
            check(first.equals(com.senne.imttech.redstone.DeviceNames.nameOf(owner, java.util.List.of(abs))),
                    "De camera heet anders dan in de I.M.T-lijst: " + first);

            // Een vreemde: sneak + klik neemt niets over, en Save doet niets.
            click(helper, stranger, cam, true);
            check(owner.getUUID().equals(camera(helper, cam).owner()), "Een vreemde nam de camera over");
            renameInWorld(helper, stranger, cam, "Hacked");
            check(camera(helper, cam).name().equals(first), "Een vreemde kon de camera hernoemen");

            // De eigenaar: de naam wordt netjes (spaties eromheen weg, aanhalingstekens eruit), ook in zijn I.M.T-lijst.
            click(helper, owner, cam, true);
            renameInWorld(helper, owner, cam, "  Lobby \"east\"  ");
            check(camera(helper, cam).name().equals("Lobby east"), "Hernoemen werkte niet: " + camera(helper, cam).name());
            check(com.senne.imttech.redstone.DeviceNames.nameOf(owner, java.util.List.of(abs)).equals("Lobby east"),
                    "De I.M.T-lijst kreeg de nieuwe naam niet: " + com.senne.imttech.redstone.DeviceNames.nameOf(owner, java.util.List.of(abs)));
            renameInWorld(helper, owner, cam, "   ");
            check(camera(helper, cam).name().equals("Lobby east"), "Een lege naam werd toch bewaard");
            // Van ver weg (meer dan 6 blokken) niet.
            Vec3 near = owner.position();
            Vec3 far = helper.absoluteVec(new Vec3(3.5, 2, -8.5));
            owner.moveTo(far.x, far.y, far.z);
            renameInWorld(helper, owner, cam, "Far away");
            owner.moveTo(near.x, near.y, near.z);
            check(camera(helper, cam).name().equals("Lobby east"), "Van ver weg kon je de camera hernoemen");
            // Gewoon klikken zegt nog steeds hoe hij ervoor staat (en verandert niets).
            click(helper, owner, cam, false);
            check(camera(helper, cam).name().equals("Lobby east"), "Gewoon klikken veranderde de naam");

            // Een camera zonder eigenaar: gewoon klikken neemt hem niet over, sneak + klik wel.
            BlockPos old = new BlockPos(1, 3, 3);
            helper.setBlock(old, ModBlocks.SECURITY_CAMERA.get());
            check(camera(helper, old).owner() == null, "Een camera zonder neerzetter heeft toch een eigenaar");
            click(helper, stranger, old, false);
            check(camera(helper, old).owner() == null, "Gewoon klikken maakte de klikker eigenaar");
            click(helper, stranger, old, true);
            check(stranger.getUUID().equals(camera(helper, old).owner()), "Wie als eerste sneak-klikte, werd niet de eigenaar");
            renameInWorld(helper, owner, old, "Not mine");
            check(!camera(helper, old).name().equals("Not mine"), "Daarna mocht een ander hem nog hernoemen");
            // Opslaan en laden: de eigenaar blijft.
            CompoundTag saved = camera(helper, old).saveWithoutMetadata(helper.getLevel().registryAccess());
            com.senne.imttech.security.CameraBlockEntity copy = new com.senne.imttech.security.CameraBlockEntity(
                    helper.absolutePos(old), helper.getBlockState(old));
            copy.loadWithComponents(saved, helper.getLevel().registryAccess());
            check(stranger.getUUID().equals(copy.owner()) && copy.ownerName().equals("camera-stranger"),
                    "Na opslaan en laden was de eigenaar weg");
            return null;
        });
        helper.succeed();
    }

    // ================================================================ Hoofdstuk 94: camerabeelden van overal

    /** Het licht (0-15) op deze plek in een laag van 2048 bytes (twee blokken per byte, zoals Minecraft het bewaart). */
    private static int nibble(byte[] data, BlockPos pos) {
        int index = (pos.getY() & 15) << 8 | (pos.getZ() & 15) << 4 | (pos.getX() & 15);
        return (data[index >> 1] >> ((index & 1) << 2)) & 15;
    }

    /**
     * Hoofdstuk 94, het formaat: een stukje wereld gaat ingepakt heen en komt precies zo terug (de blokken, een kist met
     * zijn gegevens, het licht, en wat je spel mag vergeten); een wezen ook (de soort, de plek, en hoe het eruitziet).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "camera_wall")
    public static void camera_feed_format_round_trip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos chest = new BlockPos(3, 2, 3);
        BlockPos lamp = new BlockPos(5, 2, 3);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(lamp, Blocks.GLOWSTONE);
        helper.setBlock(new BlockPos(4, 2, 5), Blocks.OAK_LEAVES);
        net.minecraft.world.entity.animal.Cow cow = helper.spawn(net.minecraft.world.entity.EntityType.COW, new BlockPos(2, 2, 6));
        helper.runAfterDelay(4, () -> {
            BlockPos abs = helper.absolutePos(chest);
            long key = net.minecraft.core.SectionPos.asLong(abs);
            net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            buf.writeVarInt(com.senne.imttech.security.feed.FeedFormat.VERSION);
            buf.writeVarInt(1);
            buf.writeLong(12345L);
            buf.writeVarInt(1);
            com.senne.imttech.security.feed.FeedFormat.writeSection(buf, level, level.getChunkAt(abs), key);
            byte[] packed = com.senne.imttech.security.feed.FeedFormat.pack(buf);
            net.minecraft.network.FriendlyByteBuf back = com.senne.imttech.security.feed.FeedFormat.unpack(packed);
            check(back != null, "Uitpakken lukte niet");
            com.senne.imttech.security.feed.FeedFormat.World world = com.senne.imttech.security.feed.FeedFormat.readWorld(back,
                    level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME));
            check(world.forget().length == 1 && world.forget()[0] == 12345L, "Wat vergeten moet, kwam niet goed terug");
            check(world.sections().size() == 1, "Er kwamen " + world.sections().size() + " stukjes terug in plaats van 1");
            com.senne.imttech.security.feed.FeedFormat.Section section = world.sections().get(0);
            check(section.key() == key && section.blocks() != null, "Het stukje kwam leeg of op een andere plek terug");
            for (int x = 0; x < 8; x++) {
                for (int y = 1; y < 5; y++) {
                    for (int z = 0; z < 8; z++) {
                        BlockPos at = helper.absolutePos(new BlockPos(x, y, z));
                        if (net.minecraft.core.SectionPos.asLong(at) != key) {
                            continue;
                        }
                        BlockState sent = section.blocks().getBlockState(at.getX() & 15, at.getY() & 15, at.getZ() & 15);
                        check(sent == level.getBlockState(at), "Op " + at + " kwam " + sent + " terug in plaats van " + level.getBlockState(at));
                        check(nibble(section.sky(), at) == level.getBrightness(net.minecraft.world.level.LightLayer.SKY, at)
                                && nibble(section.block(), at) == level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, at),
                                "Het licht op " + at + " kwam anders terug");
                    }
                }
            }
            check(section.blockEntities().stream().anyMatch(entity -> entity.pos().equals(abs)
                    && com.senne.imttech.security.feed.FeedFormat.blockEntityType(entity.type()) == net.minecraft.world.level.block.entity.BlockEntityType.CHEST),
                    "De kist kwam niet mee");

            // Een wezen: met en zonder hoe het eruitziet.
            net.minecraft.network.RegistryFriendlyByteBuf feed = new net.minecraft.network.RegistryFriendlyByteBuf(
                    io.netty.buffer.Unpooled.buffer(), level.registryAccess());
            feed.writeVarInt(1);
            com.senne.imttech.security.feed.FeedEntities.writeSource(feed, false, abs.asLong(),
                    com.senne.imttech.security.feed.FeedEntities.OK, Vec3.atCenterOf(abs), 90f, 20f);
            feed.writeVarInt(2);
            check(com.senne.imttech.security.feed.FeedEntities.writeEntity(feed, cow, true), "De koe ging niet mee");
            check(com.senne.imttech.security.feed.FeedEntities.writeEntity(feed, cow, false), "De koe (kort) ging niet mee");
            com.senne.imttech.security.feed.FeedEntities.Feed read = com.senne.imttech.security.feed.FeedEntities.read(feed);
            check(read.sources().size() == 1 && read.sources().get(0).yaw() == 90f && read.sources().get(0).eye() != null,
                    "De camera kwam niet goed terug");
            check(read.entities().size() == 2, "Er kwamen " + read.entities().size() + " wezens terug in plaats van 2");
            com.senne.imttech.security.feed.FeedEntities.EntityState full = read.entities().get(0);
            check(full.id() == cow.getId() && full.pos().distanceTo(cow.position()) < 1e-6
                    && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.byId(full.type()) == net.minecraft.world.entity.EntityType.COW,
                    "De koe kwam niet goed terug");
            check(full.look() != null && full.look().equipment() != null, "Hoe de koe eruitziet, kwam niet mee");
            check(read.entities().get(1).look() == null, "De korte beschrijving had toch alles");
            helper.succeed();
        });
    }

    /**
     * Hoofdstuk 94, jouw wens: "camera range moet evenveel zijn als de cabel lang is dus een verbonde camera moet altijd zicht
     * hebben". De server stuurt de wereld rond een camera naar wie hem mag zien: de eigenaar met zijn desktop open (zonder
     * desktop niet), of iemand bij een schermmuur waar die camera op staat; een vreemde zonder scherm niet. Een blok dat
     * verandert, gaat opnieuw; een lege lijst vergeet alles.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "camera_wall")
    public static void camera_feed_is_sent_to_who_may_watch(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 6), "feed-owner");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 8), "feed-stranger");
        generator(helper, new BlockPos(2, 2, 2));
        SecurityCoreBlockEntity core = poweredCore(helper, new BlockPos(2, 3, 2), owner);
        ServerLevel level = helper.getLevel();
        // Een PoE-kabel van de kern naar een camera op de vloer en verder naar een monitor.
        cable(helper, CableKind.POE, new BlockPos(3, 3, 2), new BlockPos(4, 3, 2), new BlockPos(5, 3, 2), new BlockPos(5, 3, 3),
                new BlockPos(5, 3, 4), new BlockPos(5, 3, 5), new BlockPos(5, 3, 6), new BlockPos(5, 3, 7));
        BlockPos lens = new BlockPos(4, 3, 3);
        helper.setBlock(lens, ModBlocks.SECURITY_CAMERA.get().defaultBlockState().setValue(CameraBlock.FACE,
                net.minecraft.world.level.block.state.properties.AttachFace.FLOOR).setValue(CameraBlock.FACING, Direction.SOUTH));
        BlockPos screen = new BlockPos(5, 3, 8);
        monitor(helper, screen, Direction.SOUTH);
        BlockPos camera = helper.absolutePos(lens);
        com.senne.imttech.network.CameraWatchPayload watch = new com.senne.imttech.network.CameraWatchPayload(
                java.util.List.of(camera.asLong()), java.util.List.of(), 16);
        helper.runAfterDelay(2, () -> {
            com.senne.imttech.security.SecurityDevices.forget(level, core);
            ((com.senne.imttech.security.CameraBlockEntity) helper.getBlockEntity(lens)).updateOnline(level, camera, level.getBlockState(camera));
            check(level.getBlockState(camera).getValue(CameraBlock.ONLINE), "De camera heeft geen beeld (netwerk of stroom)");
            // Zonder desktop en zonder schermmuur: niets.
            com.senne.imttech.security.feed.FeedServer.handleWatch(owner, watch);
            com.senne.imttech.security.feed.FeedServer.tickFor(owner);
            check(com.senne.imttech.security.feed.FeedServer.status(owner, camera) == com.senne.imttech.security.feed.FeedEntities.NO_ACCESS,
                    "Zonder desktop mocht de eigenaar toch kijken: " + com.senne.imttech.security.feed.FeedServer.status(owner, camera));
            check(com.senne.imttech.security.feed.FeedServer.sentCount(owner) == 0, "Zonder toegang ging er toch wereld mee");
            // Met de desktop open: wel, het dichtstbijzijnde eerst.
            check(com.senne.imttech.security.os.SecurityOs.openFor(owner, core, false), "De desktop ging niet open");
            com.senne.imttech.security.feed.FeedServer.handleWatch(owner, new com.senne.imttech.network.CameraWatchPayload(
                    watch.cameras(), watch.robots(), 17));
            com.senne.imttech.security.feed.FeedServer.tickFor(owner);
            check(com.senne.imttech.security.feed.FeedServer.status(owner, camera) == com.senne.imttech.security.feed.FeedEntities.OK,
                    "Met de desktop open mocht de eigenaar niet kijken: " + com.senne.imttech.security.feed.FeedServer.status(owner, camera));
            check(com.senne.imttech.security.feed.FeedServer.sentCount(owner) > 0, "Er ging geen wereld naar de eigenaar");
            check(com.senne.imttech.security.feed.FeedServer.neededCount(owner) >= com.senne.imttech.security.feed.FeedServer.sentCount(owner),
                    "Er ging meer weg dan nodig");
            // Een vreemde zonder desktop en zonder scherm: niets.
            com.senne.imttech.security.feed.FeedServer.handleWatch(stranger, watch);
            com.senne.imttech.security.feed.FeedServer.tickFor(stranger);
            check(com.senne.imttech.security.feed.FeedServer.status(stranger, camera) == com.senne.imttech.security.feed.FeedEntities.NO_ACCESS
                    && com.senne.imttech.security.feed.FeedServer.sentCount(stranger) == 0, "Een vreemde kreeg het camerabeeld");
        });
        helper.runAfterDelay(30, () -> {
            // Intussen (de server tikt door, en houdt de wereld rond de camera geladen) is alles verstuurd.
            com.senne.imttech.security.feed.FeedServer.handleWatch(owner, new com.senne.imttech.network.CameraWatchPayload(
                    watch.cameras(), watch.robots(), 17));
            check(com.senne.imttech.security.feed.FeedServer.sentCount(owner) == com.senne.imttech.security.feed.FeedServer.neededCount(owner),
                    "Niet alles ging mee: " + com.senne.imttech.security.feed.FeedServer.sentCount(owner) + " van "
                            + com.senne.imttech.security.feed.FeedServer.neededCount(owner));
            // Een blok verandert voor de camera: dat stukje gaat opnieuw.
            int before = com.senne.imttech.security.feed.FeedServer.totalSent(owner);
            helper.setBlock(lens.south(2), Blocks.GLOWSTONE);
            com.senne.imttech.security.feed.FeedServer.tickFor(owner);
            check(com.senne.imttech.security.feed.FeedServer.totalSent(owner) > before, "Een veranderd blok ging niet opnieuw mee");
            // De vreemde staat bij een schermmuur waar deze camera op staat: dan wel.
            entity(helper, screen).updateOn(level);
            ListTag tiles = new ListTag();
            tiles.add(tile(0, 0, 1, 1, camera));
            CameraWall.setLayout(level, core, shape(helper, screen), tiles);
            check(entity(helper, screen).tile() != null, "De camera kwam niet op het scherm");
            com.senne.imttech.security.feed.FeedServer.handleWatch(stranger, new com.senne.imttech.network.CameraWatchPayload(
                    watch.cameras(), watch.robots(), 17));
            com.senne.imttech.security.feed.FeedServer.tickFor(stranger);
            check(com.senne.imttech.security.feed.FeedServer.status(stranger, camera) == com.senne.imttech.security.feed.FeedEntities.OK,
                    "Bij de schermmuur mocht de vreemde niet kijken: " + com.senne.imttech.security.feed.FeedServer.status(stranger, camera));
            // Niets meer op je scherm: alles vergeten.
            com.senne.imttech.security.feed.FeedServer.handleWatch(owner, new com.senne.imttech.network.CameraWatchPayload(
                    java.util.List.of(), java.util.List.of(), 16));
            com.senne.imttech.security.feed.FeedServer.handleWatch(stranger, new com.senne.imttech.network.CameraWatchPayload(
                    java.util.List.of(), java.util.List.of(), 16));
            check(com.senne.imttech.security.feed.FeedServer.sentCount(owner) == 0, "Na een lege lijst bleef er wereld staan");
            com.senne.imttech.security.os.SecurityOs.close(owner);
            helper.succeed();
        });
    }

    /**
     * Hoofdstuk 94: "camera range moet evenveel zijn als de cabel lang is". Een net van 1200 stukjes kabel (een vlak van 20
     * x 20, drie hoog) tussen de kern en een camera in de verste hoek: vroeger zocht het net hooguit 512 stukjes ver, en
     * hing de camera er niet meer aan. Nu vindt de kern hem, weet de camera bij welke kern hij hoort, en onthoudt de kern in
     * welke stukken wereld zijn net ligt (ook na opslaan en laden), zodat NetworkKeeper die geladen kan houden.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "camera_wall")
    public static void a_camera_far_along_a_big_network_stays_connected(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(0, 2, 0), "bignet-owner");
        generator(helper, new BlockPos(1, 2, 2));
        SecurityCoreBlockEntity core = poweredCore(helper, new BlockPos(1, 3, 2), owner);
        ServerLevel level = helper.getLevel();
        for (int x = 2; x <= 21; x++) {
            for (int y = 2; y <= 4; y++) {
                for (int z = 2; z <= 21; z++) {
                    Cables.add(CableKind.NETWORK, level, helper.absolutePos(new BlockPos(x, y, z)));
                }
            }
        }
        BlockPos lens = new BlockPos(22, 3, 21);
        helper.setBlock(lens, ModBlocks.SECURITY_CAMERA.get().defaultBlockState().setValue(CameraBlock.FACE,
                net.minecraft.world.level.block.state.properties.AttachFace.FLOOR).setValue(CameraBlock.FACING, Direction.SOUTH));
        BlockPos camera = helper.absolutePos(lens);
        helper.runAfterDelay(2, () -> {
            com.senne.imttech.security.SecurityDevices.forget(level, core);
            check(com.senne.imttech.security.SecurityDevices.scan(level, core).cameras().contains(camera),
                    "De kern vond de camera achter 1200 stukjes kabel niet (apparaten: "
                            + LanGrid.devicesOf(level, core.getBlockPos()) + ", kabel naast de camera: "
                            + Cables.has(CableKind.NETWORK, level, helper.absolutePos(new BlockPos(21, 3, 21))) + ", kern van de camera: "
                            + LanGrid.coreOf(level, camera) + ")");
            check(com.senne.imttech.security.SecurityCores.deviceCore(level, camera) == core,
                    "De camera weet niet dat hij bij de kern hoort");
            long chunk = net.minecraft.world.level.ChunkPos.asLong(camera.getX() >> 4, camera.getZ() >> 4);
            check(java.util.Arrays.stream(core.networkChunks()).anyMatch(c -> c == chunk),
                    "De kern onthoudt niet dat zijn net tot bij de camera loopt");
            // Opslaan en laden: de kern weet het nog.
            var registries = level.registryAccess();
            SecurityCoreBlockEntity copy = new SecurityCoreBlockEntity(core.getBlockPos(), core.getBlockState());
            copy.loadWithComponents(core.saveWithoutMetadata(registries), registries);
            check(java.util.Arrays.equals(copy.networkChunks(), core.networkChunks()),
                    "Na opslaan en laden wist de kern niet meer waar zijn net ligt");
            helper.succeed();
        });
    }
}
