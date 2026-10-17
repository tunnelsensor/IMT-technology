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
        LaserEmitters.handleAction(owner, new LaserActionPayload(helper.absolutePos(top), LaserActionPayload.Op.INVERT, 1));

        // Een derde tegen de poort aan: die is meteen rood en omgekeerd, net als de rest.
        check(ZoneGameTests.place(helper, owner, ModItems.LASER_EMITTER.get(), top, Direction.UP), "De derde zender neerzetten lukte niet");
        BlockPos third = top.above();
        check(colour(helper, third) == ShieldColor.RED && helper.getBlockState(third).getValue(LaserEmitterBlock.INVERTED),
                "Een zender tegen de poort nam haar instellingen niet over");
        helper.succeed();
    }

    /**
     * Omkeren: zonder redstone staat de wand aan, met redstone gaat hij uit. Liggend: de wand die er staat,
     * draait mee en is een plat vloertje.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "laser_settings")
    public static void laser_inverted_and_lying_flat(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 2, 6), "laser-owner");
        BlockPos pos = new BlockPos(1, 3, 3);
        helper.setBlock(pos, ModBlocks.LASER_EMITTER.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.EAST));
        ModBlocks.LASER_EMITTER.get().setPlacedBy(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), owner, ItemStack.EMPTY);
        helper.setBlock(pos.above(), ModBlocks.HEAT_GENERATOR.get());
        ((ChargerBlockEntity) helper.getBlockEntity(pos.above())).fill(5_000);
        BlockPos abs = helper.absolutePos(pos);
        check(!helper.getBlockState(pos).getValue(LaserEmitterBlock.POWERED), "Zonder redstone stond hij al aan");

        helper.startSequence()
                .thenExecute(() -> LaserEmitters.handleAction(owner, new LaserActionPayload(abs, LaserActionPayload.Op.INVERT, 1)))
                .thenWaitUntil(() -> check(wallLength(helper, pos, 4) == 4, "Omgekeerd en zonder redstone ging de wand niet aan"))
                .thenExecute(() -> helper.setBlock(pos.below(), Blocks.REDSTONE_BLOCK))
                .thenWaitUntil(() -> check(wallLength(helper, pos, 4) == 0, "Omgekeerd en met redstone ging de wand niet uit"))
                .thenExecute(() -> helper.setBlock(pos.below(), Blocks.AIR))
                .thenWaitUntil(() -> check(wallLength(helper, pos, 4) == 4, "Zonder redstone kwam de wand niet terug"))
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
                // En terug naar gewoon: redstone aan = wand aan.
                .thenExecute(() -> LaserEmitters.handleAction(owner, new LaserActionPayload(abs, LaserActionPayload.Op.INVERT, 0)))
                .thenWaitUntil(() -> check(wallLength(helper, pos, 4) == 0, "Niet meer omgekeerd, zonder redstone, en de wand bleef staan"))
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
