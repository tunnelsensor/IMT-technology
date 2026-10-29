package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.energy.CableGeometry;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.energy.CableSpoolItem;
import com.senne.imttech.energy.Cables;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.station.ChargerBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de netwerkkabels (hoofdstuk 38, jouw wens van 29 oktober): "voeg netwerkkabels toe, net zoals de powercables
 * maar dan voor al de security items", met een switch, PoE-kabels (stroom en netwerk samen) en een netwerkhendel en
 * -knop. Stap 1: drie soorten kabels die naast elkaar kunnen liggen zonder elkaar te raken.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class NetworkGameTests {
    private NetworkGameTests() {}

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

    // ================================================================ Stap 1: drie soorten kabels

    /**
     * Een stroomkabel en een netwerkkabel kunnen op dezelfde plekken liggen zonder elkaar te raken: over een netwerkkabel
     * gaat geen stroom, haal je de netwerkkabel weg dan blijft de stroomkabel liggen (en andersom). Op één plek ligt de
     * stroomkabel onderop en de netwerkkabel erboven (de banen).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "lan")
    public static void cable_kinds_share_a_cell_but_stay_apart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        generator(helper, new BlockPos(1, 2, 6));
        for (int x = 2; x <= 6; x++) {
            Cables.add(CableKind.NETWORK, level, helper.absolutePos(new BlockPos(x, 2, 6)));
        }
        BlockPos device = helper.absolutePos(new BlockPos(7, 2, 6));
        check(PowerGrid.available(level, device) == 0, "Over een netwerkkabel gaat stroom");
        for (int x = 2; x <= 6; x++) {
            Cables.add(CableKind.POWER, level, helper.absolutePos(new BlockPos(x, 2, 6)));
        }
        check(PowerGrid.available(level, device) > 0, "Een stroomkabel naast (op) een netwerkkabel geeft geen stroom door");
        BlockPos middle = helper.absolutePos(new BlockPos(4, 2, 6));
        check(Cables.at(level, middle).equals(List.of(CableKind.POWER, CableKind.NETWORK)),
                "Op één plek liggen niet allebei de soorten: " + Cables.at(level, middle));
        check(CableGeometry.lane(level, middle, CableKind.POWER) == 0 && CableGeometry.lane(level, middle, CableKind.NETWORK) == 1,
                "De stroomkabel ligt niet onderop en de netwerkkabel er niet boven");
        // De netwerkkabel weg: de stroomkabel blijft, en de stroom ook.
        Cables.remove(CableKind.NETWORK, level, middle);
        check(Cables.has(CableKind.POWER, level, middle) && !Cables.has(CableKind.NETWORK, level, middle),
                "Weghalen van de netwerkkabel haalde ook de stroomkabel weg (of liet de netwerkkabel liggen)");
        check(CableGeometry.lane(level, middle, CableKind.POWER) == 0, "De stroomkabel ligt niet meer onderop");
        check(PowerGrid.available(level, device) > 0, "Zonder netwerkkabel gaat de stroom niet meer door");
        // De stroomkabel daar ook weg: nu is de stroom onderbroken, de rest van de netwerkkabel ligt er nog.
        Cables.remove(CableKind.POWER, level, middle);
        check(PowerGrid.available(level, device) == 0, "De stroom sprong over het gat in de stroomkabel");
        check(Cables.has(CableKind.NETWORK, level, helper.absolutePos(new BlockPos(3, 2, 6))),
                "De netwerkkabel ernaast is ook weg");
        helper.succeed();
    }

    /**
     * Een rol netwerkkabel tekent netwerkkabel (geen stroomkabel) en kost een kabel per plek. Shift + rechtsklik met de rol
     * raapt alleen zijn eigen soort op: ligt er ook een stroomkabel op die plek, dan blijft die liggen. Je krijgt de
     * netwerkkabel terug.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "lan")
    public static void cable_spools_draw_and_pick_up_their_own_kind(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 4, 8), "lan-spool");
        // Een testspeler mag nog bouwen zoals in creative (dan kost een kabel niets): hier echt survival.
        player.getAbilities().instabuild = false;
        BlockPos floor = new BlockPos(8, 2, 8);
        helper.setBlock(floor, Blocks.STONE);
        BlockPos cell = helper.absolutePos(floor.above());
        ZoneGameTests.lookDownAt(helper, player, floor);
        CableSpoolItem networkSpool = (CableSpoolItem) ModItems.NETWORK_CABLE.get();
        check(networkSpool.kind() == CableKind.NETWORK, "De rol netwerkkabel is geen netwerkkabel");
        ItemStack cables = new ItemStack(ModItems.NETWORK_CABLE.get(), 16);
        player.setItemInHand(InteractionHand.MAIN_HAND, cables);
        networkSpool.onUseTick(level, player, cables, 0);
        networkSpool.releaseUsing(cables, level, player, 0);
        check(Cables.has(CableKind.NETWORK, level, cell), "De rol netwerkkabel legde geen netwerkkabel");
        check(!Cables.has(CableKind.POWER, level, cell) && !Cables.has(CableKind.POE, level, cell),
                "De rol netwerkkabel legde ook een andere soort");
        check(cables.getCount() == 15, "Het stukje netwerkkabel kostte geen kabel van de rol (nog " + cables.getCount() + ")");
        // Een stroomkabel op dezelfde plek, en dan oprapen met de netwerkrol.
        Cables.add(CableKind.POWER, level, cell);
        player.setShiftKeyDown(true);
        networkSpool.use(level, player, InteractionHand.MAIN_HAND);
        check(!Cables.has(CableKind.NETWORK, level, cell), "Shift + rechtsklik raapte de netwerkkabel niet op");
        check(Cables.has(CableKind.POWER, level, cell), "De netwerkrol raapte ook de stroomkabel op");
        check(player.getInventory().countItem(ModItems.NETWORK_CABLE.get()) == 16,
                "Je kreeg de netwerkkabel niet terug (" + player.getInventory().countItem(ModItems.NETWORK_CABLE.get()) + ")");
        // Met de rol stroomkabel raap je de stroomkabel wel op.
        ItemStack power = new ItemStack(ModItems.CABLE.get(), 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, power);
        ((CableSpoolItem) ModItems.CABLE.get()).use(level, player, InteractionHand.MAIN_HAND);
        check(!Cables.has(CableKind.POWER, level, cell), "De rol stroomkabel raapte de stroomkabel niet op");
        helper.succeed();
    }

    /**
     * Een schaar knipt per klik één kabel door, ook als er twee soorten op dezelfde plek liggen. Er valt een stukje van die
     * soort uit (een netwerkkabel geeft een rol netwerkkabel, geen stroomkabel).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "lan")
    public static void shears_cut_one_cable_at_a_time(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(12, 4, 8), "lan-shears");
        BlockPos floor = new BlockPos(12, 2, 8);
        helper.setBlock(floor, Blocks.STONE);
        BlockPos cell = helper.absolutePos(floor.above());
        Cables.add(CableKind.POWER, level, cell);
        Cables.add(CableKind.NETWORK, level, cell);
        ZoneGameTests.lookDownAt(helper, player, floor);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        CableSpoolItem.onRightClickItem(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
        check(Cables.at(level, cell).size() == 1, "Eén knip haalde niet precies één kabel weg: " + Cables.at(level, cell));
        CableKind left = Cables.at(level, cell).getFirst();
        CableKind cut = left == CableKind.POWER ? CableKind.NETWORK : CableKind.POWER;
        AABB around = new AABB(cell).inflate(1.5);
        check(!level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around,
                        item -> item.getItem().is(cut.item())).isEmpty(),
                "Er viel geen stukje " + cut + "-kabel uit");
        CableSpoolItem.onRightClickItem(new PlayerInteractEvent.RightClickItem(player, InteractionHand.MAIN_HAND));
        check(!Cables.any(level, cell), "De tweede knip haalde de andere kabel niet weg");
        helper.succeed();
    }

    /**
     * De gebouwen leggen hun kabels met onzichtbare markers (zie tools/generate_structure.py): een marker met het labeltje
     * van een netwerk- en een PoE-kabel wordt die twee kabels (en geen stroomkabel), en de marker zelf komt de wereld niet in.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "lan")
    public static void structure_markers_lay_every_kind_of_cable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(4, 2, 12));
        Marker marker = EntityType.MARKER.create(level);
        check(marker != null, "Kon geen marker maken");
        CompoundTag tag = marker.saveWithoutId(new CompoundTag());
        CompoundTag data = new CompoundTag();
        data.putInt(CableKind.NETWORK.marker(), 1);
        data.putInt(CableKind.POE.marker(), 1);
        tag.put("data", data);
        marker.load(tag);
        marker.moveTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        level.addFreshEntity(marker);
        check(Cables.has(CableKind.NETWORK, level, pos) && Cables.has(CableKind.POE, level, pos),
                "De marker werd geen netwerk- en PoE-kabel: " + Cables.at(level, pos));
        check(!Cables.has(CableKind.POWER, level, pos), "De marker werd ook een stroomkabel");
        check(level.getEntitiesOfClass(Marker.class, new AABB(pos).inflate(1)).isEmpty(), "De marker staat nog in de wereld");
        helper.succeed();
    }
}
