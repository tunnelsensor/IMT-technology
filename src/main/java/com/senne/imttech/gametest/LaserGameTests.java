package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.station.ChargerBlockEntity;
import com.senne.imttech.network.LaserActionPayload;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.shield.LaserEmitterBlock;
import com.senne.imttech.shield.LaserEmitterBlockEntity;
import com.senne.imttech.shield.LaserEmitters;
import com.senne.imttech.shield.LaserWallBlock;
import com.senne.imttech.shield.ShieldColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor het instelscherm van de laserzender (13 oktober). Jouw wens: "zorg dat je gewoon op ze kunt
 * klikken zonder dat er iets gebeurt (makkelijker om cables te leggen). Enkel de persoon die het heeft
 * geplaatst (of trusted players in zone) kunnen hier op sneak klikken en dan een GUI openen. In deze GUI
 * kun je de kleur kiezen, signaal inverteren zodat redstone aan laser uit is en kiezen of de laser
 * horizontaal is of verticaal."
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class LaserGameTests {
    private LaserGameTests() {}

    private static final String ARENA = "empty";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Een klik op de bovenkant van het blok op {@code pos}. */
    private static BlockHitResult hit(GameTestHelper helper, BlockPos pos) {
        BlockPos abs = helper.absolutePos(pos);
        return new BlockHitResult(Vec3.atCenterOf(abs).relative(Direction.UP, 0.5), Direction.UP, abs, false);
    }

    private static LaserEmitterBlockEntity emitter(GameTestHelper helper, BlockPos pos) {
        return (LaserEmitterBlockEntity) helper.getBlockEntity(pos);
    }

    private static ShieldColor colour(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos).getValue(LaserEmitterBlock.COLOR);
    }

    /** Hoeveel blokken laserwand er oostelijk van {@code emitter} staan (tot {@code length} ver). */
    private static int wallLength(GameTestHelper helper, BlockPos emitter, int length) {
        int count = 0;
        for (int i = 1; i <= length; i++) {
            if (helper.getBlockState(emitter.east(i)).is(ModBlocks.LASER_WALL.get())) {
                count++;
            }
        }
        return count;
    }

    /**
     * Gewoon klikken doet niets (ook met een kabelrol in je hand); sneak + klik van een vreemde ook niet. Wie de
     * zender neerzette, is de eigenaar; alleen hij verandert de kleur, en dat voor de hele poort. Een zender die
     * je tegen de poort zet, neemt haar instellingen over.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "laser_settings")
    public static void laser_click_does_nothing_and_only_owner_edits(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 3), "laser-owner");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 2, 3), "laser-stranger");
        // Neergezet met de muis, op een steen: dan is de eigenaar wie hem neerzette.
        BlockPos floor = new BlockPos(3, 2, 3);
        helper.setBlock(floor, Blocks.STONE);
        check(ZoneGameTests.place(helper, owner, ModItems.LASER_EMITTER.get(), floor, Direction.UP), "Neerzetten lukte niet");
        BlockPos bottom = floor.above();
        check(helper.getBlockState(bottom).is(ModBlocks.LASER_EMITTER.get()), "Er staat geen zender");
        check(owner.getUUID().equals(emitter(helper, bottom).owner()), "Wie hem neerzette, is niet de eigenaar");
        ShieldColor start = colour(helper, bottom);

        // Gewoon klikken: niets (de klik gaat door naar wat je vasthoudt, zoals een kabelrol).
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        InteractionResult plain = helper.getBlockState(bottom).useWithoutItem(helper.getLevel(), owner, hit(helper, bottom));
        check(plain == InteractionResult.PASS && colour(helper, bottom) == start, "Gewoon klikken deed toch iets");
        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.CABLE.get()));
        InteractionResult withCable = owner.gameMode.useItemOn(owner, helper.getLevel(), owner.getMainHandItem(),
                InteractionHand.MAIN_HAND, hit(helper, bottom));
        check(!withCable.consumesAction() && colour(helper, bottom) == start, "Met een kabelrol klikken deed iets met de zender");

        // Een vreemde: sneak + klik en de knoppen van het scherm doen niets.
        stranger.setShiftKeyDown(true);
        helper.getBlockState(bottom).useWithoutItem(helper.getLevel(), stranger, hit(helper, bottom));
        stranger.setShiftKeyDown(false);
        check(!LaserEmitters.mayEdit(stranger, emitter(helper, bottom)), "Een vreemde mag de zender instellen");
        LaserEmitters.handleAction(stranger, new LaserActionPayload(helper.absolutePos(bottom), LaserActionPayload.Op.COLOR,
                ShieldColor.RED.ordinal()));
        check(colour(helper, bottom) == start, "Een vreemde kon de kleur veranderen");

        // De eigenaar zet er een tweede op: samen één poort. Rood geldt voor allebei.
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        check(ZoneGameTests.place(helper, owner, ModItems.LASER_EMITTER.get(), bottom, Direction.UP), "De tweede zender neerzetten lukte niet");
        BlockPos top = bottom.above();
        LaserEmitters.handleAction(owner, new LaserActionPayload(helper.absolutePos(bottom), LaserActionPayload.Op.COLOR,
                ShieldColor.RED.ordinal()));
        check(colour(helper, bottom) == ShieldColor.RED && colour(helper, top) == ShieldColor.RED,
                "Rood ging niet naar de hele poort: " + colour(helper, bottom) + ", " + colour(helper, top));
        LaserEmitters.handleAction(owner, new LaserActionPayload(helper.absolutePos(top), LaserActionPayload.Op.SWITCH, 0));

        // Een derde tegen de poort aan: die is meteen rood en uit, net als de rest. En de poort staat één keer in de
        // I.M.T-lijst van de eigenaar (hoofdstuk 39), als Gate 1.
        check(ZoneGameTests.place(helper, owner, ModItems.LASER_EMITTER.get(), top, Direction.UP), "De derde zender neerzetten lukte niet");
        BlockPos third = top.above();
        check(colour(helper, third) == ShieldColor.RED && !helper.getBlockState(third).getValue(LaserEmitterBlock.SWITCHED_ON),
                "Een zender tegen de poort nam haar instellingen niet over");
        var relays = owner.getData(com.senne.imttech.registry.ModAttachments.RELAYS);
        check(relays.relays().size() == 1 && relays.relays().getFirst().name().equals("Gate 1"),
                "De poort staat niet één keer, als Gate 1, in de I.M.T-lijst: " + relays.relays());
        helper.succeed();
    }

    /**
     * Hoofdstuk 39 (jouw wens: "niet meer met redstone, maar allemaal met stroom ... on("poort1"), poort1 is dan de naam die
     * je in de GUI van de rayshield hebt ingesteld"): een laserpoort met stroom staat meteen aan, en staat als Gate 1 in de
     * I.M.T-lijst van wie hem neerzette. In zijn scherm krijgt hij de naam poort1; op die naam gaat hij uit (zoals I.M.T,
     * je stem of een diagram het doen), en redstone doet niets. Switch in het scherm zet hem weer aan. Liggend: de wand die
     * er staat, draait mee en is een plat vloertje. Weghakken van de zender met de naam: de naam schuift naar de zender
     * ernaast; de laatste weg, en de naam is weg.
     */
    @GameTest(template = ARENA, timeoutTicks = 240, batch = "laser_settings")
    public static void laser_switched_by_name_and_lying_flat(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 6), "laser-owner");
        BlockPos pos = new BlockPos(1, 3, 3);
        helper.setBlock(pos, ModBlocks.LASER_EMITTER.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.EAST));
        ModBlocks.LASER_EMITTER.get().setPlacedBy(level, helper.absolutePos(pos), helper.getBlockState(pos), owner, ItemStack.EMPTY);
        helper.setBlock(pos.above(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(pos.above())).fill(5_000);
        BlockPos abs = helper.absolutePos(pos);
        var relays = owner.getData(com.senne.imttech.registry.ModAttachments.RELAYS);
        check(relays.at(level.dimension(), abs) != null && relays.at(level.dimension(), abs).name().equals("Gate 1"),
                "De poort staat niet als Gate 1 in de I.M.T-lijst: " + relays.relays());

        helper.startSequence()
                .thenWaitUntil(() -> check(wallLength(helper, pos, 4) == 4, "Met stroom ging de wand niet vanzelf aan"))
                // Een andere naam in het scherm, en dan uit op die naam.
                .thenExecute(() -> LaserEmitters.handleRename(owner, new com.senne.imttech.network.LaserRenamePayload(abs, "poort1")))
                .thenExecute(() -> {
                    var named = owner.getData(com.senne.imttech.registry.ModAttachments.RELAYS).named("poort1");
                    check(named.size() == 1, "De naam poort1 uit het scherm staat niet in de I.M.T-lijst");
                    check(com.senne.imttech.redstone.ImtRedstone.switchAt(level, named.getFirst().pos(), named.getFirst().channel(),
                            com.senne.imttech.redstone.ImtRedstone.Op.OFF, 0), "Uitzetten op de naam poort1 lukte niet");
                })
                .thenWaitUntil(() -> check(wallLength(helper, pos, 4) == 0, "Op zijn naam uitgezet, en de wand bleef staan"))
                // Redstone doet niets meer.
                .thenExecute(() -> helper.setBlock(pos.below(), Blocks.REDSTONE_BLOCK))
                .thenIdle(30)
                .thenExecute(() -> check(wallLength(helper, pos, 4) == 0 && !helper.getBlockState(pos).getValue(LaserEmitterBlock.SWITCHED_ON),
                        "Redstone zette de poort toch aan"))
                // Switch in het scherm: weer aan.
                .thenExecute(() -> LaserEmitters.handleAction(owner, new LaserActionPayload(abs, LaserActionPayload.Op.SWITCH, 1)))
                .thenWaitUntil(() -> check(wallLength(helper, pos, 4) == 4, "Switch in het scherm zette de wand niet aan"))
                .thenExecute(() -> LaserEmitters.handleAction(owner, new LaserActionPayload(abs, LaserActionPayload.Op.TURN, 1)))
                .thenExecute(() -> {
                    for (int i = 1; i <= 4; i++) {
                        BlockPos cell = pos.east(i);
                        check(helper.getBlockState(cell).getValue(LaserWallBlock.TURNED), "Blok " + i + " van de wand draaide niet mee");
                        AABB box = helper.getBlockState(cell).getCollisionShape(helper.getLevel(), helper.absolutePos(cell)).bounds();
                        check(box.minY > 0.4 && box.maxY < 0.6 && box.minZ == 0 && box.maxZ == 1,
                                "De liggende wand is geen plat vloertje: " + box);
                    }
                })
                // Een tweede zender eronder (dezelfde poort), en dan de zender met de naam weghakken: de naam schuift mee.
                .thenExecute(() -> {
                    helper.setBlock(pos.below(), ModBlocks.LASER_EMITTER.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.EAST));
                    ModBlocks.LASER_EMITTER.get().setPlacedBy(level, helper.absolutePos(pos.below()), helper.getBlockState(pos.below()),
                            owner, ItemStack.EMPTY);
                    check(owner.getData(com.senne.imttech.registry.ModAttachments.RELAYS).relays().size() == 1,
                            "Een zender tegen de poort gaf een tweede regel in de I.M.T-lijst");
                    helper.setBlock(pos, Blocks.AIR);
                    var moved = owner.getData(com.senne.imttech.registry.ModAttachments.RELAYS).named("poort1");
                    check(moved.size() == 1 && moved.getFirst().pos().equals(helper.absolutePos(pos.below())),
                            "De naam schoof niet mee naar de zender die er nog staat: " + moved);
                    helper.setBlock(pos.below(), Blocks.AIR);
                    check(owner.getData(com.senne.imttech.registry.ModAttachments.RELAYS).named("poort1").isEmpty(),
                            "De laatste zender is weg, maar de naam staat nog in de lijst");
                })
                .thenSucceed();
    }

    /**
     * In een zone: de vertrouwde spelers van de kern mogen de zender instellen (ook een die een ander neerzette),
     * een vreemde niet. Een zender zonder eigenaar in iemands basis kan een vreemde niet overnemen.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "zones")
    public static void laser_trusted_players_in_zone(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 3, 6), "zone-owner");
        ServerPlayer friend = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(9, 3, 6), "zone-friend");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(10, 3, 6), "zone-outsider");
        SecurityCoreBlockEntity core = ZoneGameTests.zoneCore(helper, owner);
        core.addTrusted(friend.getUUID(), friend.getGameProfile().getName());

        BlockPos placed = new BlockPos(8, 3, 8);
        helper.setBlock(placed, ModBlocks.LASER_EMITTER.get());
        ModBlocks.LASER_EMITTER.get().setPlacedBy(helper.getLevel(), helper.absolutePos(placed), helper.getBlockState(placed), owner, ItemStack.EMPTY);
        check(LaserEmitters.mayEdit(friend, emitter(helper, placed)), "Een vertrouwde speler in de zone mag de zender niet instellen");
        check(!LaserEmitters.mayEdit(stranger, emitter(helper, placed)), "Een vreemde in de zone mag de zender instellen");
        LaserEmitters.handleAction(friend, new LaserActionPayload(helper.absolutePos(placed), LaserActionPayload.Op.COLOR,
                ShieldColor.GREEN.ordinal()));
        check(colour(helper, placed) == ShieldColor.GREEN, "De vertrouwde speler kon de kleur niet veranderen");

        // Zonder eigenaar (zoals in een oude wereld): een vreemde neemt hem niet over, een vriend mag erbij.
        BlockPos old = new BlockPos(10, 3, 10);
        helper.setBlock(old, ModBlocks.LASER_EMITTER.get());
        check(!LaserEmitters.mayEdit(stranger, emitter(helper, old)) && emitter(helper, old).owner() == null,
                "Een vreemde kon een zender zonder eigenaar in iemands zone overnemen");
        check(LaserEmitters.mayEdit(friend, emitter(helper, old)), "Een vriend mag een zender zonder eigenaar in de zone niet instellen");
        ZoneGameTests.finish(helper, core);
    }

    /**
     * Een zender zonder eigenaar buiten elke basis (van vóór 13 oktober) wordt van wie hem als eerste opent;
     * daarna mag een ander niet meer. (Alleen in zijn batch: met een kleiner bereik van de kernen, zodat de
     * kernen van andere tests hier niet meetellen.)
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "laser_claim")
    public static void laser_old_emitter_goes_to_first_opener(GameTestHelper helper) {
        ServerPlayer first = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(2, 2, 3), "laser-first");
        ServerPlayer second = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 2, 3), "laser-second");
        BlockPos pos = new BlockPos(3, 3, 3);
        helper.setBlock(pos, ModBlocks.LASER_EMITTER.get());
        check(emitter(helper, pos).owner() == null, "Een zender zonder neerzetter heeft toch een eigenaar");
        ImtTechGameTests.withReach(4, () -> {
            first.setShiftKeyDown(true);
            helper.getBlockState(pos).useWithoutItem(helper.getLevel(), first, hit(helper, pos));
            first.setShiftKeyDown(false);
            check(first.getUUID().equals(emitter(helper, pos).owner()), "Wie hem als eerste opende, werd niet de eigenaar");
            check(!LaserEmitters.mayEdit(second, emitter(helper, pos)), "Daarna mocht een ander hem nog instellen");
            return null;
        });
        helper.succeed();
    }
}
