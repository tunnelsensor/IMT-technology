package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.energy.CableCells;
import com.senne.imttech.energy.CableGeometry;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.energy.CableSpoolItem;
import com.senne.imttech.energy.Cables;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.lan.LanGrid;
import com.senne.imttech.lan.RouterBlock;
import com.senne.imttech.lan.RouterBlockEntity;
import com.senne.imttech.lan.RouterLinks;
import com.senne.imttech.lan.Routers;
import com.senne.imttech.network.RelayActionPayload;
import com.senne.imttech.network.RouterActionPayload;
import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.redstone.RelayBlock;
import com.senne.imttech.redstone.RelayBlockEntity;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.logic.InputDeviceBlock;
import com.senne.imttech.logic.InputLight;
import com.senne.imttech.logic.LogicEvents;
import com.senne.imttech.logic.ServerRackBlockEntity;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.CameraBlock;
import com.senne.imttech.security.CameraBlockEntity;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDevices;
import com.senne.imttech.shield.LaserBridgeBlock;
import com.senne.imttech.shield.LaserBridgeBlockEntity;
import com.senne.imttech.shield.LaserEmitterBlock;
import com.senne.imttech.station.ChargerBlockEntity;
import com.senne.imttech.turret.TurretBlock;
import com.senne.imttech.turret.TurretBlockEntity;
import com.senne.imttech.weapons.Emp;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.Vec3;
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
 * -knop. Stap 1: drie soorten kabels die naast elkaar kunnen liggen zonder elkaar te raken. Stap 2: zonder netwerkkabel
 * naar de desktop van een kern doet een beveiligingsapparaat niets.
 *
 * De andere tests draaien met de oude regel (apparaten horen bij de kern in wiens gebied ze staan, zie
 * LanGrid.required); deze batch zet het netwerk aan, net als in een echte wereld.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class NetworkGameTests {
    private NetworkGameTests() {}

    private static final String ARENA = "empty";

    /** Vóór de tests van deze batch: beveiligingsapparaten hebben een netwerkkabel nodig (zoals in een echte wereld). */
    @net.minecraft.gametest.framework.BeforeBatch(batch = "lan")
    public static void networkOn(ServerLevel level) {
        LanGrid.overrideRequired(true);
    }

    /** Daarna weer de oude regel, voor de andere tests. */
    @net.minecraft.gametest.framework.AfterBatch(batch = "lan")
    public static void networkOff(ServerLevel level) {
        LanGrid.overrideRequired(null);
    }

    /** Doet {@code action} even met (true) of zonder (false) netwerk-eis, en zet het daarna terug. */
    static <T> T withNetwork(boolean required, java.util.function.Supplier<T> action) {
        LanGrid.overrideRequired(required);
        try {
            return action.get();
        } finally {
            LanGrid.overrideRequired(true);
        }
    }

    /** Legt een kabel van deze soort langs een rechte lijn (eerst x, dan z, dan y), van {@code from} tot en met {@code to}. */
    private static void line(GameTestHelper helper, CableKind kind, BlockPos from, BlockPos to) {
        BlockPos.MutableBlockPos at = from.mutable();
        Cables.add(kind, helper.getLevel(), helper.absolutePos(at));
        while (at.getX() != to.getX()) {
            at.move(Integer.signum(to.getX() - at.getX()), 0, 0);
            Cables.add(kind, helper.getLevel(), helper.absolutePos(at));
        }
        while (at.getZ() != to.getZ()) {
            at.move(0, 0, Integer.signum(to.getZ() - at.getZ()));
            Cables.add(kind, helper.getLevel(), helper.absolutePos(at));
        }
        while (at.getY() != to.getY()) {
            at.move(0, Integer.signum(to.getY() - at.getY()), 0);
            Cables.add(kind, helper.getLevel(), helper.absolutePos(at));
        }
    }

    /** De kern van het apparaat op deze (relatieve) plek, zoals alles in de mod hem opzoekt. */
    private static @org.jetbrains.annotations.Nullable SecurityCoreBlockEntity coreOf(GameTestHelper helper, BlockPos device) {
        return SecurityCores.deviceCore(helper.getLevel(), helper.absolutePos(device));
    }

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
     * Hoofdstuk 92: elk stuk kabel onthoudt wie het legde. Met de rol is het van jou; legt een ander zijn rol over jouw
     * kabel, dan blijft hij van jou; een kabel zonder speler (een gebouw) is van niemand. Dat blijft zo na opslaan en laden
     * (ook een oude wereld, met alleen de plekken, laadt nog: die kabels zijn van niemand) en komt zo bij je spel aan.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "lan")
    public static void cables_remember_who_laid_them(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer alice = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 4, 12), "lan-owner-a");
        ServerPlayer bob = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 4, 12), "lan-owner-b");
        BlockPos floor = new BlockPos(8, 2, 12);
        helper.setBlock(floor, Blocks.STONE);
        BlockPos cell = helper.absolutePos(floor.above());
        CableSpoolItem spool = (CableSpoolItem) ModItems.NETWORK_CABLE.get();
        for (ServerPlayer player : List.of(alice, bob)) {
            ZoneGameTests.lookDownAt(helper, player, floor);
            ItemStack cables = new ItemStack(ModItems.NETWORK_CABLE.get(), 4);
            player.setItemInHand(InteractionHand.MAIN_HAND, cables);
            spool.onUseTick(level, player, cables, 0);
            spool.releaseUsing(cables, level, player, 0);
        }
        check(Cables.has(CableKind.NETWORK, level, cell), "De rol legde geen netwerkkabel");
        check(alice.getUUID().equals(Cables.owner(CableKind.NETWORK, level, cell)),
                "De kabel is niet van wie hem legde (maar van " + Cables.owner(CableKind.NETWORK, level, cell) + ")");
        // Een stroomkabel zonder speler op dezelfde plek: die is van niemand, de netwerkkabel blijft van Alice.
        Cables.add(CableKind.POWER, level, cell);
        check(Cables.owner(CableKind.POWER, level, cell) == null, "Een kabel zonder speler heeft toch een eigenaar");
        check(alice.getUUID().equals(Cables.owner(CableKind.NETWORK, level, cell)), "De netwerkkabel veranderde van eigenaar");
        // Weg en opnieuw gelegd door Bob: nu van Bob.
        Cables.remove(CableKind.NETWORK, level, cell);
        check(Cables.owner(CableKind.NETWORK, level, cell) == null, "Een weggehaalde kabel heeft nog een eigenaar");
        Cables.add(CableKind.NETWORK, level, cell, bob.getUUID());
        check(bob.getUUID().equals(Cables.owner(CableKind.NETWORK, level, cell)), "De nieuwe kabel is niet van Bob");

        // Opslaan en laden (zoals met de chunk), en naar je spel sturen.
        BlockPos a = new BlockPos(1, 2, 3);
        BlockPos b = new BlockPos(-4, 70, 9);
        BlockPos c = new BlockPos(5, -60, -2);
        CableCells cells = CableCells.EMPTY.with(a, alice.getUUID()).with(b).with(c, bob.getUUID());
        net.minecraft.nbt.Tag saved = CableCells.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, cells).getOrThrow();
        CableCells loaded = CableCells.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, saved).getOrThrow();
        checkOwners(loaded, a, b, c, alice, bob, "na opslaan en laden");
        io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
        CableCells.STREAM_CODEC.encode(buf, cells);
        CableCells sent = CableCells.STREAM_CODEC.decode(buf);
        checkOwners(sent, a, b, c, alice, bob, "na het versturen");
        check(!buf.isReadable(), "Na het versturen bleef er iets over in het bericht");
        // Een oude wereld: alleen de plekken (een rij getallen).
        net.minecraft.nbt.Tag old = new net.minecraft.nbt.LongArrayTag(new long[] {a.asLong(), b.asLong()});
        CableCells fromOld = CableCells.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, old).getOrThrow();
        check(fromOld.size() == 2 && fromOld.contains(a) && fromOld.contains(b), "Een oude wereld laadt zijn kabels niet");
        check(fromOld.owner(a) == null && fromOld.owner(b) == null, "Een kabel uit een oude wereld heeft toch een eigenaar");
        check(cells.without(a).owner(a) == null && cells.without(a).owner(c) != null, "Weghalen nam de verkeerde eigenaar mee");
        helper.succeed();
    }

    /** Liggen de drie plekken er, met Alice, niemand en Bob als eigenaar? */
    private static void checkOwners(CableCells cells, BlockPos a, BlockPos b, BlockPos c, ServerPlayer alice, ServerPlayer bob,
                                    String when) {
        check(cells.size() == 3 && cells.contains(a) && cells.contains(b) && cells.contains(c),
                "De kabels kloppen niet meer " + when + " (" + cells.size() + ")");
        check(alice.getUUID().equals(cells.owner(a)) && cells.owner(b) == null && bob.getUUID().equals(cells.owner(c)),
                "De eigenaars kloppen niet meer " + when);
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
        check(Cables.owner(CableKind.NETWORK, level, pos) == null && Cables.owner(CableKind.POE, level, pos) == null,
                "Een kabel uit een gebouw is van iemand (hoofdstuk 92: van niemand, dus niet door de muur te zien)");
        check(level.getEntitiesOfClass(Marker.class, new AABB(pos).inflate(1)).isEmpty(), "De marker staat nog in de wereld");
        helper.succeed();
    }

    // ================================================================ Stap 2: het netwerk en de kern

    /**
     * Jouw wens: "je hangt een witte netwerkkabel aan de desktop, verbindt die met een keycardreader en dan pas komt die bij
     * de desktop devices". Zonder kabel hoort de lezer bij geen kern (hij staat niet in Devices, en een kaart geeft een rood
     * lampje), met een kabel wel, en na het doorknippen weer niet. Een stroomkabel telt niet als netwerk.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void reader_needs_a_network_cable_to_the_desktop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 3, 8), "lan-reader");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos reader = new BlockPos(9, 3, 2);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        generator(helper, reader.above()); // stroom (stap 4): zonder stroom gaat er geen lampje aan
        check(coreOf(helper, reader) == null, "Een lezer zonder netwerkkabel hoort bij een kern");
        check(!SecurityDevices.scan(level, core).inputs().contains(helper.absolutePos(reader)),
                "Een lezer zonder netwerkkabel staat in Devices");
        // Een stroomkabel ertussen is geen netwerk.
        line(helper, CableKind.POWER, new BlockPos(3, 3, 2), new BlockPos(8, 3, 2));
        check(coreOf(helper, reader) == null, "Een stroomkabel bracht de lezer op het netwerk");
        // Een kaart tegen een lezer zonder netwerk: rood lampje.
        LogicEvents.clear();
        LogicEvents.keycard(level, helper.absolutePos(reader), owner, null);
        helper.assertBlockProperty(reader, InputDeviceBlock.LIGHT, InputLight.DENIED);
        // De netwerkkabel van de kern naar de lezer (naast de stroomkabel, een rij verder, en dan terug).
        line(helper, CableKind.NETWORK, new BlockPos(2, 3, 3), new BlockPos(9, 3, 3));
        check(coreOf(helper, reader) == core, "Met een netwerkkabel hoort de lezer niet bij de kern");
        SecurityDevices.forget(level, core);
        check(SecurityDevices.scan(level, core).inputs().contains(helper.absolutePos(reader)),
                "Met een netwerkkabel staat de lezer niet in Devices");
        // Doorknippen: weer van niemand.
        Cables.remove(CableKind.NETWORK, level, helper.absolutePos(new BlockPos(6, 3, 3)));
        check(coreOf(helper, reader) == null, "Na het doorknippen hoort de lezer nog bij de kern");
        SecurityDevices.forget(level, core);
        check(!SecurityDevices.scan(level, core).inputs().contains(helper.absolutePos(reader)),
                "Na het doorknippen staat de lezer nog in Devices");
        helper.succeed();
    }

    /**
     * Zoals bij stroom: een apparaat dat direct tegen de kern staat, heeft geen kabel nodig. En een kabel die je in een muur
     * wegwerkt, werkt nog steeds.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void network_reaches_through_walls_and_by_touch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), null);
        BlockPos touching = new BlockPos(3, 3, 2);
        helper.setBlock(touching, ModBlocks.CODE_PANEL.get());
        check(coreOf(helper, touching) == core, "Een paneel tegen de kern aan hoort er niet bij");
        BlockPos far = new BlockPos(2, 3, 10);
        helper.setBlock(far, ModBlocks.KEYCARD_READER.get());
        line(helper, CableKind.NETWORK, new BlockPos(2, 3, 3), new BlockPos(2, 3, 9));
        for (int z = 3; z <= 9; z++) {
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(2, 3, z)), Blocks.STONE_BRICKS.defaultBlockState());
        }
        check(coreOf(helper, far) == core, "Een netwerkkabel in een muur werkt niet meer");
        helper.succeed();
    }

    /** Raakt een net twee kernen, dan hoort een apparaat bij de kern die het dichtst bij is langs de kabel. */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void the_nearest_core_along_the_cable_wins(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SecurityCoreBlockEntity west = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 12), null);
        SecurityCoreBlockEntity east = ImtTechGameTests.placeCore(helper, new BlockPos(20, 3, 12), null);
        line(helper, CableKind.NETWORK, new BlockPos(3, 3, 12), new BlockPos(19, 3, 12));
        BlockPos reader = new BlockPos(6, 4, 12);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        check(coreOf(helper, reader) == west, "De lezer hoort niet bij de kern die het dichtst bij is langs de kabel");
        SecurityDevices.forget(level, west);
        SecurityDevices.forget(level, east);
        check(SecurityDevices.scan(level, west).inputs().contains(helper.absolutePos(reader)), "De dichtste kern ziet de lezer niet");
        check(!SecurityDevices.scan(level, east).inputs().contains(helper.absolutePos(reader)), "De verre kern ziet de lezer ook");
        helper.succeed();
    }

    /**
     * Met {@code [network] required = false} (een oude wereld) geldt de oude regel: een lezer zonder kabel hoort bij de kern
     * in wiens gebied hij staat. (Een kern meldt zich een tick na het neerzetten aan, vandaar het wachten.)
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void without_the_network_rule_the_area_counts(GameTestHelper helper) {
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 18), null);
        BlockPos reader = new BlockPos(10, 3, 18);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        helper.runAfterDelay(2, () -> {
            check(coreOf(helper, reader) == null, "Met netwerk-eis hoort een lezer zonder kabel bij een kern");
            check(withNetwork(false, () -> coreOf(helper, reader)) == core,
                    "Zonder netwerk-eis hoort de lezer niet bij de kern in wiens gebied hij staat");
            helper.succeed();
        });
    }

    /**
     * Een camera, een beveiligingstorentje en een serverrek met stroom maar zonder netwerkkabel: de camera geeft geen
     * beeld, het torentje staat uit (hij weet niet wie een vriend is), het rek zegt "geen netwerk". Met een netwerkkabel
     * naar de kern werkt alles, en kan de desktop het torentje uitzetten.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "lan")
    public static void camera_turret_and_rack_need_the_network(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(12, 3, 9), "lan-devices");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 6), owner);
        // De camera op een steen (de kabel komt straks in die steen), met stroom ernaast.
        BlockPos stone = new BlockPos(8, 3, 6);
        helper.setBlock(stone, Blocks.STONE);
        BlockPos camera = stone.above();
        helper.setBlock(camera, ModBlocks.SECURITY_CAMERA.get().defaultBlockState()
                .setValue(CameraBlock.FACE, AttachFace.FLOOR).setValue(CameraBlock.FACING, Direction.SOUTH));
        generator(helper, camera.east());
        // Het beveiligingstorentje en het rek bovenop de plek van de kabel, met stroom ernaast.
        BlockPos turret = new BlockPos(12, 4, 6);
        helper.setBlock(turret, ModBlocks.SECURITY_TURRET.get());
        generator(helper, turret.east());
        BlockPos rack = new BlockPos(5, 4, 6);
        helper.setBlock(rack, ModBlocks.SERVER_RACK.get());
        generator(helper, rack.above());
        ServerRackBlockEntity rackEntity = (ServerRackBlockEntity) helper.getBlockEntity(rack);
        rackEntity.parts().setItem(0, new ItemStack(ModItems.SERVER_CPU.get()));
        CameraBlockEntity cam = (CameraBlockEntity) helper.getBlockEntity(camera);
        TurretBlockEntity gun = (TurretBlockEntity) helper.getBlockEntity(turret);
        BlockPos cameraAbs = helper.absolutePos(camera);
        cam.updateOnline(level, cameraAbs, level.getBlockState(cameraAbs));
        gun.refreshOnline(level);
        ImtTechGameTests.startServer(helper, rack, core);
        helper.assertBlockProperty(camera, CameraBlock.ONLINE, false);
        helper.assertBlockProperty(turret, TurretBlock.ONLINE, false);
        check(rackEntity.status() == ServerRackBlockEntity.Status.NO_NETWORK,
                "Een rek zonder netwerkkabel zegt niet \"geen netwerk\" maar " + rackEntity.status());
        check(SecurityDevices.switchTurret(level, core, helper.absolutePos(turret), false) != null,
                "De desktop kan een torentje zonder netwerkkabel uitzetten");
        // De netwerkkabel: van de kern onder alle drie door.
        line(helper, CableKind.NETWORK, new BlockPos(3, 3, 6), new BlockPos(12, 3, 6));
        cam.updateOnline(level, cameraAbs, level.getBlockState(cameraAbs));
        gun.refreshOnline(level);
        ImtTechGameTests.startServer(helper, rack, core);
        helper.assertBlockProperty(camera, CameraBlock.ONLINE, true);
        helper.assertBlockProperty(turret, TurretBlock.ONLINE, true);
        check(rackEntity.status() == ServerRackBlockEntity.Status.ONLINE,
                "Een rek met netwerkkabel en stroom draait niet mee: " + rackEntity.status());
        check(SecurityDevices.switchTurret(level, core, helper.absolutePos(turret), false) == null,
                "De desktop kan een bekabeld torentje niet uitzetten");
        helper.succeed();
    }

    /**
     * Jouw keuze: je eigen raketturretje en een laserpoort werken ook zonder netwerkkabel, gewoon op stroom (de kabel is
     * alleen voor de desktop). Een kern in de buurt maakt dat niet anders.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "lan")
    public static void rocket_turret_and_laser_gate_work_without_network(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 22), null);
        BlockPos turret = new BlockPos(8, 3, 22);
        helper.setBlock(turret, ModBlocks.ROCKET_TURRET.get());
        generator(helper, turret.below());
        ((TurretBlockEntity) helper.getBlockEntity(turret)).refreshOnline(level);
        helper.assertBlockProperty(turret, TurretBlock.ONLINE, true);
        BlockPos emitter = new BlockPos(16, 3, 22);
        helper.setBlock(emitter, ModBlocks.LASER_EMITTER.get());
        generator(helper, emitter.below());
        helper.succeedWhen(() -> helper.assertBlockProperty(emitter, LaserEmitterBlock.POWERED, true));
    }

    // ================================================================ Stap 4: stroom voor lezer en paneel

    /**
     * Jouw wens: "deze dingen hebben allemaal stroom nodig, dus de keycardreader heeft een netwerkkabel én een powerkabel".
     * Een lezer en een paneel aan het netwerk maar zonder stroom doen niets (geen lampje), en Devices zegt "no power". Met
     * stroom ernaast werken ze (zonder draaiende server: een rood lampje). De kern betaalt hun stroom om de twee seconden,
     * van hun eigen stroomnet.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void reader_and_panel_need_power(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 4), "lan-power");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos reader = new BlockPos(5, 3, 2);
        BlockPos panel = new BlockPos(9, 3, 2);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        helper.setBlock(panel, ModBlocks.CODE_PANEL.get());
        line(helper, CableKind.NETWORK, new BlockPos(3, 3, 3), new BlockPos(9, 3, 3));
        line(helper, CableKind.NETWORK, new BlockPos(2, 3, 3), new BlockPos(2, 3, 3));
        check(coreOf(helper, reader) == core && coreOf(helper, panel) == core, "De lezer en het paneel hangen niet aan de kern");
        BlockPos readerAbs = helper.absolutePos(reader);
        BlockPos panelAbs = helper.absolutePos(panel);
        // Zonder stroom: niets.
        LogicEvents.clear();
        LogicEvents.keycard(level, readerAbs, owner, null);
        helper.assertBlockProperty(reader, InputDeviceBlock.LIGHT, InputLight.OFF);
        LogicEvents.clear();
        LogicEvents.codePanel(level, panelAbs, owner, "1234");
        helper.assertBlockProperty(panel, InputDeviceBlock.LIGHT, InputLight.OFF);
        SecurityDevices.forget(level, core);
        check(statusOf(level, core, readerAbs).equals("no power"),
                "Devices zegt bij een lezer zonder stroom: " + statusOf(level, core, readerAbs));
        // Met stroom ernaast (een generator boven elk apparaat): rood (er draait geen server), dus hij deed iets.
        generator(helper, reader.above());
        generator(helper, panel.above());
        LogicEvents.clear();
        LogicEvents.keycard(level, readerAbs, owner, null);
        helper.assertBlockProperty(reader, InputDeviceBlock.LIGHT, InputLight.DENIED);
        LogicEvents.clear();
        LogicEvents.codePanel(level, panelAbs, owner, "1234");
        helper.assertBlockProperty(panel, InputDeviceBlock.LIGHT, InputLight.DENIED);
        check(statusOf(level, core, readerAbs).equals("no server"),
                "Devices zegt bij een lezer met stroom (zonder server): " + statusOf(level, core, readerAbs));
        // De kern betaalt hun stroom: de generator van de lezer raakt READER_UPKEEP kwijt.
        ChargerBlockEntity readerPower = (ChargerBlockEntity) helper.getBlockEntity(reader.above());
        int before = readerPower.energy();
        core.powerTick(level);
        check(before - readerPower.energy() >= Math.round(com.senne.imttech.config.ImtConfig.Power.READER_UPKEEP),
                "De kern betaalde de stroom van de lezer niet (" + before + " -> " + readerPower.energy() + ")");
        helper.succeed();
    }

    // ================================================================ Stap 5: PoE

    /**
     * Jouw wens: "POE-kabels nemen power en ethernet samen; dit werkt als je een power cable aan de desktop hebt hangen, en
     * dan vertrekt die van de desktop". Een camera die alleen met een PoE-kabel aan de kern hangt, heeft beeld: netwerk én
     * stroom komen over die ene kabel, de stroom van de generator onder de kern. Heeft de kern zelf geen stroom, dan heeft de
     * camera die ook niet (het netwerk werkt wel).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void poe_brings_power_and_network_from_the_desktop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Een kern met stroom (een generator eronder) en een PoE-kabel naar een camera op een steen.
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), null);
        line(helper, CableKind.POE, new BlockPos(3, 3, 2), new BlockPos(9, 3, 2));
        BlockPos camera = placeCamera(helper, new BlockPos(9, 4, 2));
        CameraBlockEntity cam = (CameraBlockEntity) helper.getBlockEntity(camera);
        BlockPos cameraAbs = helper.absolutePos(camera);
        check(coreOf(helper, camera) == core, "Over de PoE-kabel komt geen netwerk");
        check(PowerGrid.available(level, cameraAbs) > 0, "Over de PoE-kabel komt geen stroom van de desktop");
        cam.updateOnline(level, cameraAbs, level.getBlockState(cameraAbs));
        helper.assertBlockProperty(camera, CameraBlock.ONLINE, true);
        // Een kern zonder stroom: dan geeft de PoE-kabel ook geen stroom (wel netwerk).
        BlockPos bareCore = new BlockPos(2, 3, 12);
        helper.setBlock(bareCore, ModBlocks.SECURITY_CORE.get());
        line(helper, CableKind.POE, new BlockPos(3, 3, 12), new BlockPos(9, 3, 12));
        BlockPos dark = placeCamera(helper, new BlockPos(9, 4, 12));
        BlockPos darkAbs = helper.absolutePos(dark);
        check(coreOf(helper, dark) == helper.getBlockEntity(bareCore), "Over de PoE-kabel komt geen netwerk");
        check(PowerGrid.available(level, darkAbs) == 0, "Een kern zonder stroom geeft toch stroom over de PoE-kabel");
        ((CameraBlockEntity) helper.getBlockEntity(dark)).updateOnline(level, darkAbs, level.getBlockState(darkAbs));
        helper.assertBlockProperty(dark, CameraBlock.ONLINE, false);
        helper.succeed();
    }

    /**
     * Hoofdstuk 85 (jouw wens: "zorg ook dat power cable, netwerk en poe niet connecten aan elkaar"): een PoE-kabel zit niet
     * meer vast aan een stroomkabel of een netwerkkabel ernaast. Een lezer aan het eind van een netwerkkabel die tegen de
     * PoE-kabel ligt, hangt niet aan de kern; een lezer aan het eind van een stroomkabel die tegen de PoE-kabel ligt, krijgt
     * er geen stroom van. Van de ene soort naar de andere gaat het alleen via een blok: een netwerkkabel aan de kern zelf
     * werkt wel. Ook een netwerkkabel en een stroomkabel tegen elkaar geven niets door.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void power_network_and_poe_cables_do_not_join(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 18), null);
        line(helper, CableKind.POE, new BlockPos(3, 3, 18), new BlockPos(8, 3, 18));
        // Een netwerkkabel verder naar een lezer, en een stroomkabel de andere kant op naar een tweede lezer.
        line(helper, CableKind.NETWORK, new BlockPos(8, 3, 19), new BlockPos(8, 3, 22));
        BlockPos reader = new BlockPos(8, 3, 23);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        line(helper, CableKind.POWER, new BlockPos(9, 3, 18), new BlockPos(14, 3, 18));
        BlockPos powered = new BlockPos(15, 3, 18);
        helper.setBlock(powered, ModBlocks.KEYCARD_READER.get());
        check(coreOf(helper, reader) == null, "Een netwerkkabel tegen een PoE-kabel geeft het netwerk toch door");
        check(PowerGrid.available(level, helper.absolutePos(powered)) == 0,
                "Een stroomkabel tegen een PoE-kabel geeft de stroom van de desktop toch door");
        // Via de kern zelf wel: een netwerkkabel van de kern naar een lezer.
        line(helper, CableKind.NETWORK, new BlockPos(2, 3, 19), new BlockPos(2, 3, 21));
        BlockPos direct = new BlockPos(2, 3, 22);
        helper.setBlock(direct, ModBlocks.KEYCARD_READER.get());
        check(coreOf(helper, direct) == core, "Een netwerkkabel aan de kern zelf geeft geen netwerk");
        // Een stroomkabel van een generator tegen een netwerkkabel naar een lezer: geen stroom voor de lezer.
        generator(helper, new BlockPos(12, 3, 2));
        line(helper, CableKind.POWER, new BlockPos(12, 3, 3), new BlockPos(12, 3, 5));
        line(helper, CableKind.NETWORK, new BlockPos(13, 3, 5), new BlockPos(15, 3, 5));
        BlockPos far = new BlockPos(16, 3, 5);
        helper.setBlock(far, ModBlocks.KEYCARD_READER.get());
        check(PowerGrid.available(level, helper.absolutePos(far)) == 0, "Over een netwerkkabel tegen een stroomkabel kwam stroom");
        helper.succeed();
    }

    /**
     * Hoofdstuk 85 (jouw wens: "zorg dat al de blokken die niet in een netwerk horen, denk aan een crusher, geen power kunnen
     * krijgen via een POE"): een Crusher aan een PoE-kabel van een kern met stroom krijgt geen stroom, ook niet als hij op de
     * kabel staat; een lezer aan dezelfde kabel wel (de PoE-kabel loopt onder de Crusher door). Met een stroomkabel krijgt de
     * Crusher wel stroom. En een generator tegen een PoE-kabel voedt die niet: een generator is geen netwerkblok, alleen de
     * kern voedt PoE.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void poe_only_powers_network_devices(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), null);
        line(helper, CableKind.POE, new BlockPos(3, 3, 2), new BlockPos(9, 3, 2));
        BlockPos onCable = new BlockPos(6, 3, 2);
        helper.setBlock(onCable, ModBlocks.CRUSHER.get());
        BlockPos crusher = new BlockPos(10, 3, 2);
        helper.setBlock(crusher, ModBlocks.CRUSHER.get());
        BlockPos reader = new BlockPos(9, 3, 3);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        check(PowerGrid.available(level, helper.absolutePos(reader)) > 0, "Een lezer aan de PoE-kabel krijgt geen stroom");
        check(PowerGrid.available(level, helper.absolutePos(crusher)) == 0, "Een Crusher kreeg stroom over een PoE-kabel");
        check(PowerGrid.available(level, helper.absolutePos(onCable)) == 0, "Een Crusher op een PoE-kabel kreeg er stroom van");
        // Een stroomkabel van een generator naar de Crusher: dan wel.
        generator(helper, new BlockPos(10, 3, 6));
        line(helper, CableKind.POWER, new BlockPos(10, 3, 3), new BlockPos(10, 3, 5));
        check(PowerGrid.available(level, helper.absolutePos(crusher)) > 0, "Een Crusher aan een stroomkabel krijgt geen stroom");
        // Een generator tegen een PoE-kabel zonder kern: de camera aan het eind krijgt niets.
        generator(helper, new BlockPos(2, 3, 12));
        line(helper, CableKind.POE, new BlockPos(3, 3, 12), new BlockPos(6, 3, 12));
        BlockPos camera = placeCamera(helper, new BlockPos(6, 4, 12));
        check(PowerGrid.available(level, helper.absolutePos(camera)) == 0, "Een generator voedde een PoE-kabel zonder kern");
        helper.succeed();
    }

    // ================================================================ Stap 6: de Network Switch

    /**
     * Jouw wens: "een switch waar de netwerkkabels samenkomen", zoals de stroomkast (jouw keuze: met een schakelaar per
     * kant, en met eigen stroom). Een lezer achter een switch hangt aan de kern; zet je de poort naar de lezer uit, dan niet
     * meer; zet je de poort naar de kern uit, dan hangt ook de switch zelf los. Zonder stroom laat de switch geen netwerk
     * door (alles erachter ligt plat), maar staat hij zelf nog wel in Devices.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void switch_ports_and_power_decide_the_network(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), null);
        line(helper, CableKind.NETWORK, new BlockPos(3, 3, 2), new BlockPos(5, 3, 2));
        BlockPos sw = new BlockPos(6, 3, 2);
        helper.setBlock(sw, ModBlocks.NETWORK_SWITCH.get());
        generator(helper, sw.above());
        com.senne.imttech.lan.NetworkSwitchBlockEntity switchEntity =
                (com.senne.imttech.lan.NetworkSwitchBlockEntity) helper.getBlockEntity(sw);
        switchEntity.updateOnline(level);
        helper.assertBlockProperty(sw, com.senne.imttech.lan.NetworkSwitchBlock.ONLINE, true);
        line(helper, CableKind.NETWORK, new BlockPos(7, 3, 2), new BlockPos(9, 3, 2));
        BlockPos reader = new BlockPos(10, 3, 2);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        BlockPos swAbs = helper.absolutePos(sw);
        check(coreOf(helper, reader) == core, "Een lezer achter een switch hangt niet aan de kern");
        check(coreOf(helper, sw) == core, "De switch zelf hangt niet aan de kern");
        // De poort naar de lezer (oost) uit, en weer aan.
        com.senne.imttech.lan.NetworkSwitches.setPort(level, swAbs, Direction.EAST, false);
        check(coreOf(helper, reader) == null, "Met de poort uit hangt de lezer nog aan de kern");
        check(coreOf(helper, sw) == core, "Een poort naar de lezer uit, en de switch zelf hangt los");
        com.senne.imttech.lan.NetworkSwitches.setPort(level, swAbs, Direction.EAST, true);
        check(coreOf(helper, reader) == core, "Met de poort weer aan hangt de lezer niet aan de kern");
        // De poort naar de kern (west) uit: alles erachter, ook de switch, hangt los.
        com.senne.imttech.lan.NetworkSwitches.setPort(level, swAbs, Direction.WEST, false);
        check(coreOf(helper, reader) == null && coreOf(helper, sw) == null,
                "Met de poort naar de kern uit hangt er nog iets aan de kern");
        com.senne.imttech.lan.NetworkSwitches.setPort(level, swAbs, Direction.WEST, true);
        // Geen stroom meer: niets erdoor, maar de switch staat nog in Devices ("no power").
        helper.setBlock(sw.above(), Blocks.AIR);
        switchEntity.updateOnline(level);
        helper.assertBlockProperty(sw, com.senne.imttech.lan.NetworkSwitchBlock.ONLINE, false);
        check(coreOf(helper, reader) == null, "Een switch zonder stroom laat het netwerk door");
        SecurityDevices.forget(level, core);
        check(SecurityDevices.scan(level, core).switches().contains(swAbs), "Een switch zonder stroom staat niet in Devices");
        check(statusOf(level, core, swAbs).equals("no power"), "Devices zegt bij een switch zonder stroom: "
                + statusOf(level, core, swAbs));
        helper.succeed();
    }

    /**
     * Jouw keuze: PoE-stroom mag ook via een switch. Een switch zonder eigen stroom krijgt die van de PoE-kabel van de
     * desktop, en geeft netwerk en stroom door naar een camera aan een PoE-kabel achter hem. Zet je die poort uit, dan heeft
     * de camera geen stroom en geen netwerk meer.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void poe_power_passes_through_a_switch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 12), null);
        line(helper, CableKind.POE, new BlockPos(3, 3, 12), new BlockPos(5, 3, 12));
        BlockPos sw = new BlockPos(6, 3, 12);
        helper.setBlock(sw, ModBlocks.NETWORK_SWITCH.get());
        ((com.senne.imttech.lan.NetworkSwitchBlockEntity) helper.getBlockEntity(sw)).updateOnline(level);
        helper.assertBlockProperty(sw, com.senne.imttech.lan.NetworkSwitchBlock.ONLINE, true);
        line(helper, CableKind.POE, new BlockPos(7, 3, 12), new BlockPos(9, 3, 12));
        BlockPos camera = placeCamera(helper, new BlockPos(9, 4, 12));
        BlockPos cameraAbs = helper.absolutePos(camera);
        CameraBlockEntity cam = (CameraBlockEntity) helper.getBlockEntity(camera);
        check(coreOf(helper, camera) == core, "Over PoE door een switch komt geen netwerk");
        check(PowerGrid.available(level, cameraAbs) > 0, "Over PoE door een switch komt geen stroom");
        cam.updateOnline(level, cameraAbs, level.getBlockState(cameraAbs));
        helper.assertBlockProperty(camera, CameraBlock.ONLINE, true);
        com.senne.imttech.lan.NetworkSwitches.setPort(level, helper.absolutePos(sw), Direction.EAST, false);
        check(coreOf(helper, camera) == null, "Met de poort uit komt er nog netwerk bij de camera");
        check(PowerGrid.available(level, cameraAbs) == 0, "Met de poort uit komt er nog stroom bij de camera");
        // Een gewone stroomkabel tegen de switch krijgt geen PoE-stroom (alleen PoE gaat erdoor).
        line(helper, CableKind.POWER, new BlockPos(6, 3, 13), new BlockPos(6, 3, 15));
        check(PowerGrid.available(level, helper.absolutePos(new BlockPos(6, 3, 16))) == 0,
                "Een stroomkabel tegen de switch kreeg PoE-stroom");
        helper.succeed();
    }

    /**
     * De poorten van een switch schakel je ook vanaf de desktop (de knoppen onder een switch in Devices): alleen als de
     * switch aan het netwerk van die kern hangt. Devices toont de switch met zijn zes poorten.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void desktop_switches_switch_ports(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 3, 20), "lan-desk");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 20), owner);
        BlockPos sw = new BlockPos(3, 3, 20); // tegen de kern aan
        helper.setBlock(sw, ModBlocks.NETWORK_SWITCH.get());
        BlockPos swAbs = helper.absolutePos(sw);
        BlockPos coreAbs = helper.absolutePos(new BlockPos(2, 3, 20));
        helper.runAfterDelay(2, () -> {
            com.senne.imttech.security.os.SecurityOs.open(owner, coreAbs);
            CompoundTag args = new CompoundTag();
            args.putInt("x", swAbs.getX());
            args.putInt("y", swAbs.getY());
            args.putInt("z", swAbs.getZ());
            args.putInt("side", Direction.UP.get3DDataValue());
            args.putBoolean("on", false);
            com.senne.imttech.security.os.SecurityOs.handleAction(owner,
                    new com.senne.imttech.network.OsActionPayload(coreAbs, "sw_port", args));
            check(!com.senne.imttech.lan.NetworkSwitchBlock.isOn(level.getBlockState(swAbs), Direction.UP),
                    "De desktop zette de poort van de switch niet uit");
            SecurityDevices.forget(level, core);
            CompoundTag row = null;
            for (net.minecraft.nbt.Tag tag : SecurityDevices.snapshot(level, core)) {
                if (((CompoundTag) tag).getString("k").equals("switch")) {
                    row = (CompoundTag) tag;
                }
            }
            check(row != null && row.getList("ports", net.minecraft.nbt.Tag.TAG_COMPOUND).size() == 6,
                    "Devices toont de switch niet met zes poorten: " + row);
            // Het deurtje van de switch: een knop voor de switch zelf (je staat ervoor) zet de poort weer aan; een knop met
            // een plek heel ver weg doet niets en laadt geen stuk wereld (gevonden bij hoofdstuk 113).
            com.senne.imttech.lan.NetworkSwitches.handleAction(owner,
                    new com.senne.imttech.network.SwitchActionPayload(swAbs, Direction.UP.get3DDataValue()));
            check(com.senne.imttech.lan.NetworkSwitchBlock.isOn(level.getBlockState(swAbs), Direction.UP),
                    "De knop van het deurtje zette de poort niet weer aan");
            BlockPos farAway = swAbs.offset(20_000, 0, 20_000);
            com.senne.imttech.lan.NetworkSwitches.handleAction(owner,
                    new com.senne.imttech.network.SwitchActionPayload(farAway, Direction.UP.get3DDataValue()));
            check(!level.isLoaded(farAway), "Een knop van het switchscherm laadde een stuk wereld ver weg");
            helper.succeed();
        });
    }

    // ================================================================ Stap 7: de laserbrug op het netwerk

    /**
     * Jouw keuze: een laserbrug met een netwerkkabel naar de desktop staat in Devices (de groep LASER BRIDGES), met één
     * regel per brug, ook als hij uit twee zenders bestaat (die geven het netwerk aan elkaar door: één kabel is genoeg). Een
     * brug van iemand anders staat er niet in, ook niet als hij aan de kabel hangt, en een brug zonder kabel ook niet; die
     * werkt gewoon los, op stroom.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "lan")
    public static void laser_bridges_join_the_network(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(2, 3, 7), "lan-bridge-owner");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(3, 3, 7), "lan-bridge-stranger");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        line(helper, CableKind.NETWORK, new BlockPos(3, 3, 2), new BlockPos(5, 3, 2));
        // Een brug van twee zenders breed: de kabel komt alleen bij de eerste.
        BlockPos first = new BlockPos(6, 3, 2);
        BlockPos second = first.south();
        for (BlockPos emitter : List.of(first, second)) {
            placeBridge(helper, emitter, owner);
            helper.setBlock(emitter.east(5), Blocks.STONE);
        }
        generator(helper, first.below());
        // Een brug van een ander aan dezelfde kabel, en een brug van de eigenaar zonder kabel (met stroom: hij staat aan).
        placeBridge(helper, new BlockPos(4, 3, 1), stranger);
        BlockPos loose = new BlockPos(6, 3, 12);
        placeBridge(helper, loose, owner);
        helper.setBlock(loose.east(5), Blocks.STONE);
        generator(helper, loose.below());
        helper.succeedWhen(() -> {
            check(helper.getBlockState(first).getValue(LaserBridgeBlock.POWERED), "De bekabelde brug ging niet aan");
            check(helper.getBlockState(loose).getValue(LaserBridgeBlock.POWERED), "Een brug zonder netwerkkabel werkt niet los");
            check(coreOf(helper, second) == core, "De tweede zender hangt niet aan het netwerk (via de eerste)");
            SecurityDevices.forget(level, core);
            List<CompoundTag> rows = new java.util.ArrayList<>();
            for (net.minecraft.nbt.Tag tag : SecurityDevices.snapshot(level, core)) {
                if (((CompoundTag) tag).getString("k").equals("bridge")) {
                    rows.add((CompoundTag) tag);
                }
            }
            check(rows.size() == 1, "Devices toont niet precies één brug (de bekabelde van de eigenaar): " + rows);
            CompoundTag row = rows.get(0);
            BlockPos firstAbs = helper.absolutePos(first);
            check(row.getInt("x") == firstAbs.getX() && row.getInt("y") == firstAbs.getY() && row.getInt("z") == firstAbs.getZ(),
                    "De brug staat niet onder zijn eerste zender in Devices: " + row);
            check(row.getString("name").equals("Laser bridge (2 emitters)") && row.getString("st").equals("active"),
                    "De regel van de brug klopt niet: " + row);
        });
    }

    // ================================================================ Stap 8: de netwerkknop en -hendel

    /**
     * Jouw wens: "netwerk lever en buttons ... kunnen net zoals een redstone button of lever worden gebruikt maar dan via
     * de logic in de desktop". Een hendel met een netwerkkabel en stroom start {@code when lever(...)} met {@code lever.on};
     * een knop start {@code when button(...)} (met player.name, en {@code is_on("Night mode")} leest de hendel). Een
     * hendel zonder stroom of een knop zonder kabel gaat wel om (het zijn echte knoppen), maar de server hoort niets. De
     * knop komt na een seconde vanzelf terug. In Devices staan ze bij BUTTONS en LEVERS.
     */
    @GameTest(template = ARENA, timeoutTicks = 140, batch = "lan")
    public static void network_button_and_lever_start_diagrams(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 8), "lan-ctl-owner");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos rack = new BlockPos(2, 3, 3); // tegen de kern aan: op het netwerk zonder kabel
        ImtTechGameTests.placeServer(helper, rack, core);
        line(helper, CableKind.NETWORK, new BlockPos(3, 3, 2), new BlockPos(9, 3, 2));
        BlockPos doorbell = placeControl(helper, ModBlocks.NETWORK_BUTTON.get(), new BlockPos(5, 3, 3), "Doorbell");
        generator(helper, doorbell.south());
        BlockPos night = placeControl(helper, ModBlocks.NETWORK_LEVER.get(), new BlockPos(7, 3, 3), "Night mode");
        generator(helper, night.south());
        BlockPos dark = placeControl(helper, ModBlocks.NETWORK_LEVER.get(), new BlockPos(9, 3, 3), "Dark lever"); // geen stroom
        BlockPos far = placeControl(helper, ModBlocks.NETWORK_BUTTON.get(), new BlockPos(13, 3, 8), "Far button"); // geen kabel
        generator(helper, far.south());
        BlockPos coreAbs = helper.absolutePos(new BlockPos(2, 3, 2));
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, rack, core);
            com.senne.imttech.security.os.SecurityOs.open(owner, coreAbs);
            String code = """
                    when button("Doorbell"):
                        log("bell by " + player.name)
                        if is_on("Night mode"):
                            log("bell at night")

                    when lever("Night mode"):
                        if lever.on:
                            log("night on")
                        else:
                            log("night off")

                    when lever("Dark lever"):
                        log("dark lever")

                    when button("Far button"):
                        log("far button")
                    """;
            CompoundTag args = new CompoundTag();
            args.putString("old", "");
            args.putString("name", "Controls");
            args.put("graph", com.senne.imttech.logic.program.GraphConverter.toGraph(
                    com.senne.imttech.logic.program.ScriptParser.parse(code), null).save());
            args.putString("source", code);
            com.senne.imttech.security.os.SecurityOs.handleAction(owner,
                    new com.senne.imttech.network.OsActionPayload(coreAbs, "logic_save", args));
            check(core.logic().program("Controls") != null, "Het diagram met knop en hendel werd niet bewaard");
            use(helper, owner, night);
            use(helper, owner, dark);
            use(helper, owner, far);
            helper.assertBlockProperty(night, com.senne.imttech.logic.NetworkLeverBlock.ON, true);
            helper.assertBlockProperty(dark, com.senne.imttech.logic.NetworkLeverBlock.ON, true);
            helper.assertBlockProperty(far, com.senne.imttech.logic.NetworkButtonBlock.PRESSED, true);
        });
        helper.runAfterDelay(25, () -> {
            check(logged(core, "night on"), "De hendel startte when lever(...) niet (of lever.on klopte niet)");
            check(!logged(core, "dark lever"), "Een hendel zonder stroom werd toch gehoord");
            check(!logged(core, "far button"), "Een knop zonder netwerkkabel werd toch gehoord");
            use(helper, owner, doorbell);
            helper.assertBlockProperty(doorbell, com.senne.imttech.logic.NetworkButtonBlock.PRESSED, true);
        });
        helper.runAfterDelay(50, () -> {
            check(logged(core, "bell by lan-ctl-owner"), "De knop startte when button(...) niet (met player.name)");
            check(logged(core, "bell at night"), "is_on(\"Night mode\") las de hendel niet");
            helper.assertBlockProperty(doorbell, com.senne.imttech.logic.NetworkButtonBlock.PRESSED, false);
            use(helper, owner, night);
            helper.assertBlockProperty(night, com.senne.imttech.logic.NetworkLeverBlock.ON, false);
        });
        helper.runAfterDelay(75, () -> {
            check(logged(core, "night off"), "Terugzetten gaf geen lever.on = false");
            SecurityDevices.forget(level, core);
            String kinds = "";
            for (net.minecraft.nbt.Tag tag : SecurityDevices.snapshot(level, core)) {
                CompoundTag device = (CompoundTag) tag;
                if (device.getString("name").equals("Doorbell") || device.getString("name").equals("Night mode")) {
                    kinds += device.getString("k") + " ";
                }
            }
            check(kinds.contains("button") && kinds.contains("lever"), "Devices toont de knop en de hendel niet: " + kinds);
            helper.succeed();
        });
    }

    // ================================================================ Stap 9: signalen en stroom die je ziet lopen

    /**
     * De wegen die je over de kabels ziet lopen (het vakje van een signaal, de gele streepjes van de stroom). Een signaal
     * van een lezer gaat langs de netwerkkabel door de switch naar de kern; met de poort van de switch uit is er geen weg.
     * De stroom van een lezer loopt van zijn generator langs de stroomkabel naar hem toe; die van een camera aan een
     * PoE-kabel van de generator onder de kern, door de kern en over de PoE-kabel. Elke plek ligt naast de vorige. En een
     * apparaat laat zijn stroom hooguit om de {@code flowSeconds} zien.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "lan")
    public static void signals_and_power_follow_the_cables(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), null);
        line(helper, CableKind.NETWORK, new BlockPos(3, 3, 2), new BlockPos(4, 3, 2));
        BlockPos sw = new BlockPos(5, 3, 2);
        helper.setBlock(sw, ModBlocks.NETWORK_SWITCH.get());
        generator(helper, sw.above());
        ((com.senne.imttech.lan.NetworkSwitchBlockEntity) helper.getBlockEntity(sw)).updateOnline(level);
        line(helper, CableKind.NETWORK, new BlockPos(6, 3, 2), new BlockPos(8, 3, 2));
        BlockPos reader = new BlockPos(9, 3, 2);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        // De stroom van de lezer: een generator verderop en een stroomkabel ernaartoe.
        line(helper, CableKind.POWER, new BlockPos(9, 3, 3), new BlockPos(9, 3, 5));
        BlockPos readerGenerator = new BlockPos(9, 3, 6);
        generator(helper, readerGenerator);
        // Een camera aan een PoE-kabel van de kern (de generator staat onder de kern, zie placeCore).
        line(helper, CableKind.POE, new BlockPos(2, 3, 3), new BlockPos(2, 3, 5));
        BlockPos camera = placeCamera(helper, new BlockPos(2, 3, 6));

        List<BlockPos> signal = LanGrid.path(level, helper.absolutePos(reader));
        check(signal.size() == 8 && signal.getFirst().equals(helper.absolutePos(reader))
                        && signal.getLast().equals(helper.absolutePos(new BlockPos(2, 3, 2)))
                        && signal.contains(helper.absolutePos(sw)) && neighbours(signal),
                "De weg van het signaal van de lezer naar de kern klopt niet: " + signal);
        com.senne.imttech.lan.NetworkSwitches.setPort(level, helper.absolutePos(sw), Direction.EAST, false);
        check(LanGrid.path(level, helper.absolutePos(reader)).isEmpty(), "Met de poort uit is er nog een weg naar de kern");
        com.senne.imttech.lan.NetworkSwitches.setPort(level, helper.absolutePos(sw), Direction.EAST, true);

        List<BlockPos> power = PowerGrid.path(level, helper.absolutePos(reader));
        check(power.size() == 5 && power.getFirst().equals(helper.absolutePos(readerGenerator))
                        && power.getLast().equals(helper.absolutePos(reader)) && neighbours(power),
                "De weg van de stroom naar de lezer klopt niet: " + power);
        List<BlockPos> poe = PowerGrid.path(level, helper.absolutePos(camera));
        check(poe.size() == 6 && poe.getFirst().equals(helper.absolutePos(new BlockPos(2, 2, 2)))
                        && poe.get(1).equals(helper.absolutePos(new BlockPos(2, 3, 2)))
                        && poe.getLast().equals(helper.absolutePos(camera)) && neighbours(poe),
                "De weg van de stroom over de PoE-kabel klopt niet: " + poe);

        // Hooguit om de flowSeconds: de tweede keer meteen erna telt niet.
        BlockPos readerAbs = helper.absolutePos(reader);
        com.senne.imttech.lan.CableTraffic.power(level, readerAbs);
        long first = com.senne.imttech.lan.CableTraffic.lastFlow(level, readerAbs);
        int interval = Math.round(com.senne.imttech.config.ImtConfig.Network.FLOW_SECONDS * 20);
        helper.runAfterDelay(1, () -> {
            com.senne.imttech.lan.CableTraffic.power(level, readerAbs);
            check(first >= 0 && com.senne.imttech.lan.CableTraffic.lastFlow(level, readerAbs) == first,
                    "De stroom werd vaker dan om de flowSeconds getoond");
        });
        helper.runAfterDelay(interval + 2, () -> {
            com.senne.imttech.lan.CableTraffic.power(level, readerAbs);
            check(com.senne.imttech.lan.CableTraffic.lastFlow(level, readerAbs) > first,
                    "Na de flowSeconds werd de stroom niet opnieuw getoond");
            helper.succeed();
        });
    }

    /** Ligt elke plek van de weg naast de vorige? */
    private static boolean neighbours(List<BlockPos> path) {
        for (int i = 1; i < path.size(); i++) {
            if (path.get(i).distManhattan(path.get(i - 1)) != 1) {
                return false;
            }
        }
        return true;
    }

    // ================================================================ Hoofdstuk 39: apparaten op hun naam

    /**
     * Jouw voorbeeld: {@code on("poort1")}, met poort1 de naam die je in het scherm van de rayshield instelde. Een poort met
     * een netwerkkabel naar de kern (van de eigenaar van de kern) heet in zijn scherm poort1; een diagram zet hem uit en
     * weer aan (met een netwerkhendel als trigger). In Devices staat hij onder die naam, en de knop daar (Turn off) zet hem
     * ook uit.
     */
    @GameTest(template = ARENA, timeoutTicks = 300, batch = "lan")
    public static void laser_gate_switched_by_name_from_a_diagram(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 5), "lan-gate-owner");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos rack = new BlockPos(2, 3, 3);
        ImtTechGameTests.placeServer(helper, rack, core);
        line(helper, CableKind.NETWORK, new BlockPos(3, 3, 2), new BlockPos(5, 3, 2));
        BlockPos gate = new BlockPos(6, 3, 2);
        BlockPos gateAbs = helper.absolutePos(gate);
        helper.setBlock(gate, ModBlocks.LASER_EMITTER.get().defaultBlockState().setValue(LaserEmitterBlock.FACING, Direction.EAST));
        ModBlocks.LASER_EMITTER.get().setPlacedBy(level, gateAbs, helper.getBlockState(gate), owner, ItemStack.EMPTY);
        generator(helper, gate.above());
        helper.setBlock(gate.east(5), Blocks.STONE);
        BlockPos lever = placeControl(helper, ModBlocks.NETWORK_LEVER.get(), new BlockPos(4, 3, 3), "Gate lever");
        generator(helper, lever.south());
        com.senne.imttech.shield.LaserEmitters.handleRename(owner, new com.senne.imttech.network.LaserRenamePayload(gateAbs, "poort1"));
        BlockPos coreAbs = helper.absolutePos(new BlockPos(2, 3, 2));
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, rack, core);
            com.senne.imttech.security.os.SecurityOs.open(owner, coreAbs);
            String code = """
                    when lever("Gate lever"):
                        if lever.on:
                            off("poort1")
                        else:
                            on("poort1")
                    """;
            CompoundTag args = new CompoundTag();
            args.putString("old", "");
            args.putString("name", "Gate");
            args.put("graph", com.senne.imttech.logic.program.GraphConverter.toGraph(
                    com.senne.imttech.logic.program.ScriptParser.parse(code), null).save());
            args.putString("source", code);
            com.senne.imttech.security.os.SecurityOs.handleAction(owner,
                    new com.senne.imttech.network.OsActionPayload(coreAbs, "logic_save", args));
            check(core.logic().program("Gate") != null, "Het diagram met poort1 werd niet bewaard");
            check(!core.logic().devices("poort1").isEmpty(), "De server kent de naam poort1 niet");
        });
        helper.startSequence()
                .thenWaitUntil(() -> check(wall(helper, gate) == 4, "De poort met stroom ging niet vanzelf aan"))
                .thenExecute(() -> use(helper, owner, lever))
                .thenWaitUntil(() -> check(wall(helper, gate) == 0, "off(\"poort1\") in het diagram zette de poort niet uit"))
                .thenExecute(() -> {
                    SecurityDevices.forget(level, core);
                    CompoundTag row = null;
                    for (net.minecraft.nbt.Tag tag : SecurityDevices.snapshot(level, core)) {
                        if (((CompoundTag) tag).getString("k").equals("laser")) {
                            row = (CompoundTag) tag;
                        }
                    }
                    check(row != null && row.getString("name").equals("poort1") && !row.getBoolean("on")
                                    && row.getString("st").equals("open"), "Devices toont de poort niet als poort1 (uit): " + row);
                })
                .thenExecute(() -> use(helper, owner, lever))
                .thenWaitUntil(() -> check(wall(helper, gate) == 4, "on(\"poort1\") in het diagram zette de poort niet aan"))
                // De knop Turn off in Devices.
                .thenExecute(() -> {
                    CompoundTag off = new CompoundTag();
                    off.putInt("x", gateAbs.getX());
                    off.putInt("y", gateAbs.getY());
                    off.putInt("z", gateAbs.getZ());
                    off.putBoolean("on", false);
                    com.senne.imttech.security.os.SecurityOs.handleAction(owner,
                            new com.senne.imttech.network.OsActionPayload(coreAbs, "dev_toggle", off));
                })
                .thenWaitUntil(() -> check(wall(helper, gate) == 0, "Turn off in Devices zette de poort niet uit"))
                .thenSucceed();
    }

    /**
     * Hoofdstuk 39: een camera die je neerzet, staat in je I.M.T-lijst ("Camera 1") en heet zelf ook zo. Op zijn naam gaat
     * hij uit (geen beeld); de knop in Devices zet hem weer aan. Rename in Devices geeft een beveiligingstorentje (dat van
     * niemand is) een naam in jouw lijst, en dan kent de server die naam voor je diagrammen en staat hij zo in Devices.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "lan")
    public static void cameras_and_turrets_switch_and_rename_by_name(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "lan-cam-owner");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos coreAbs = helper.absolutePos(new BlockPos(2, 3, 2));
        line(helper, CableKind.POE, new BlockPos(2, 3, 3), new BlockPos(2, 3, 5));
        BlockPos camera = placeCamera(helper, new BlockPos(2, 3, 6));
        BlockPos cameraAbs = helper.absolutePos(camera);
        ModBlocks.SECURITY_CAMERA.get().setPlacedBy(level, cameraAbs, helper.getBlockState(camera), owner, ItemStack.EMPTY);
        CameraBlockEntity cam = (CameraBlockEntity) helper.getBlockEntity(camera);
        check(cam.name().equals("Camera 1")
                        && com.senne.imttech.redstone.DeviceNames.nameOf(owner, List.of(cameraAbs)).equals("Camera 1"),
                "De camera heet niet Camera 1 (zelf en in de I.M.T-lijst): " + cam.name());
        cam.updateOnline(level, cameraAbs, helper.getBlockState(camera));
        helper.assertBlockProperty(camera, CameraBlock.ONLINE, true);
        com.senne.imttech.redstone.ImtRedstone.switchAt(level, cameraAbs, -1, com.senne.imttech.redstone.ImtRedstone.Op.OFF, 0);
        helper.assertBlockProperty(camera, CameraBlock.ENABLED, false);
        helper.assertBlockProperty(camera, CameraBlock.ONLINE, false);
        // Devices: Turn on.
        com.senne.imttech.security.os.SecurityOs.open(owner, coreAbs);
        CompoundTag on = new CompoundTag();
        on.putInt("x", cameraAbs.getX());
        on.putInt("y", cameraAbs.getY());
        on.putInt("z", cameraAbs.getZ());
        on.putBoolean("on", true);
        com.senne.imttech.security.os.SecurityOs.handleAction(owner, new com.senne.imttech.network.OsActionPayload(coreAbs, "dev_toggle", on));
        helper.assertBlockProperty(camera, CameraBlock.ENABLED, true);
        helper.assertBlockProperty(camera, CameraBlock.ONLINE, true);
        // Een beveiligingstorentje tegen de kern (van niemand): Rename in Devices.
        BlockPos turret = new BlockPos(1, 3, 2);
        helper.setBlock(turret, ModBlocks.SECURITY_TURRET.get());
        BlockPos turretAbs = helper.absolutePos(turret);
        CompoundTag rename = new CompoundTag();
        rename.putInt("x", turretAbs.getX());
        rename.putInt("y", turretAbs.getY());
        rename.putInt("z", turretAbs.getZ());
        rename.putString("name", "Hall gun");
        com.senne.imttech.security.os.SecurityOs.handleAction(owner, new com.senne.imttech.network.OsActionPayload(coreAbs, "dev_rename", rename));
        check(com.senne.imttech.redstone.DeviceNames.nameOf(owner, List.of(turretAbs)).equals("Hall gun"),
                "Rename in Devices zette het torentje niet als Hall gun in de I.M.T-lijst");
        check(!core.logic().devices("Hall gun").isEmpty() && !core.logic().devices("Camera 1").isEmpty(),
                "De server kent de namen Hall gun en Camera 1 niet");
        SecurityDevices.forget(level, core);
        check(statusOf(level, core, turretAbs).equals("online") || statusOf(level, core, turretAbs).equals("no power"),
                "Het torentje staat niet in Devices");
        boolean named = false;
        for (net.minecraft.nbt.Tag tag : SecurityDevices.snapshot(level, core)) {
            named |= ((CompoundTag) tag).getString("name").equals("Hall gun");
        }
        check(named, "Devices toont het torentje niet als Hall gun");
        helper.succeed();
    }

    /** Hoeveel blokken laserwand er oostelijk van deze zender staan (tot 4 ver). */
    private static int wall(GameTestHelper helper, BlockPos emitter) {
        int count = 0;
        for (int i = 1; i <= 4 && helper.getBlockState(emitter.east(i)).is(ModBlocks.LASER_WALL.get()); i++) {
            count++;
        }
        return count;
    }

    /** Een netwerkknop of -hendel op de vloer bij {@code pos} (van niemand), met deze naam. */
    private static BlockPos placeControl(GameTestHelper helper, net.minecraft.world.level.block.Block block, BlockPos pos,
                                         String name) {
        helper.setBlock(pos, block.defaultBlockState().setValue(com.senne.imttech.logic.NetworkControlBlock.FACE, AttachFace.FLOOR));
        ((com.senne.imttech.logic.InputDeviceBlockEntity) helper.getBlockEntity(pos)).setName(name);
        return pos;
    }

    /** Rechtsklikken met een lege hand (zonder sneak). */
    private static void use(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        BlockPos abs = helper.absolutePos(pos);
        helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
    }

    /** Staat deze tekst als regel van een diagram in het logboek van de kern? */
    private static boolean logged(SecurityCoreBlockEntity core, String text) {
        return core.log().entries().stream().anyMatch(entry -> entry.type() == com.senne.imttech.security.os.EventLog.Type.LOGIC
                && entry.text().equals(text));
    }

    /** Een brugzender die naar het oosten kijkt, van deze speler. */
    private static void placeBridge(GameTestHelper helper, BlockPos pos, ServerPlayer owner) {
        helper.setBlock(pos, ModBlocks.LASER_BRIDGE.get().defaultBlockState().setValue(LaserBridgeBlock.FACING, Direction.EAST));
        ((LaserBridgeBlockEntity) helper.getBlockEntity(pos)).setOwner(owner);
    }

    /** Een camera op de vloer bij {@code pos} (er komt een steen onder), die naar het zuiden kijkt. */
    private static BlockPos placeCamera(GameTestHelper helper, BlockPos pos) {
        if (helper.getBlockState(pos.below()).isAir()) {
            helper.setBlock(pos.below(), Blocks.STONE);
        }
        helper.setBlock(pos, ModBlocks.SECURITY_CAMERA.get().defaultBlockState()
                .setValue(CameraBlock.FACE, AttachFace.FLOOR).setValue(CameraBlock.FACING, Direction.SOUTH));
        return pos;
    }

    /** De toestand van dit apparaat in de Devices-app ("st"), of "" als het er niet in staat. */
    private static String statusOf(ServerLevel level, SecurityCoreBlockEntity core, BlockPos pos) {
        for (net.minecraft.nbt.Tag tag : SecurityDevices.snapshot(level, core)) {
            net.minecraft.nbt.CompoundTag device = (net.minecraft.nbt.CompoundTag) tag;
            if (device.getInt("x") == pos.getX() && device.getInt("y") == pos.getY() && device.getInt("z") == pos.getZ()) {
                return device.getString("st");
            }
        }
        return "";
    }

    // ================================================================ Stap 3: de toren bekabeld

    /**
     * De Biebia's Industries Tower: elke stroomkabel in de muren heeft een netwerkkabel ernaast (zie
     * tools/generate_structure.py), dus al zijn beveiligingstorentjes, camera's en laserzenders hangen met een netwerkkabel
     * aan zijn kern (ook de lasers achter de stroomkast, via het stukje kabel om de kast heen). In Devices staan de negen
     * torentjes en de zes camera's. (De "serverkasten" van de toren zijn decoratie: geen echte racks.)
     */
    @GameTest(template = "biebia_tower", timeoutTicks = 200, batch = "lan")
    public static void biebia_tower_is_wired_with_network_cables(GameTestHelper helper) {
        helper.runAfterDelay(40, () -> {
            ServerLevel level = helper.getLevel();
            List<BlockPos> turrets = new java.util.ArrayList<>();
            List<BlockPos> cameras = new java.util.ArrayList<>();
            List<BlockPos> emitters = new java.util.ArrayList<>();
            BlockPos core = null;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = 0; x < 41; x++) {
                for (int z = 0; z < 55; z++) {
                    for (int y = 0; y < 100; y++) {
                        var state = helper.getBlockState(pos.set(x, y, z));
                        if (state.is(ModBlocks.SECURITY_TURRET.get())) {
                            turrets.add(pos.immutable());
                        } else if (state.is(ModBlocks.SECURITY_CAMERA.get())) {
                            cameras.add(pos.immutable());
                        } else if (state.is(ModBlocks.LASER_EMITTER.get())) {
                            emitters.add(pos.immutable());
                        } else if (state.is(ModBlocks.SECURITY_CORE.get())) {
                            core = pos.immutable();
                        }
                    }
                }
            }
            check(core != null && turrets.size() == 9 && cameras.size() == 6 && !emitters.isEmpty(),
                    "De toren heeft niet wat hij moet hebben: " + turrets.size() + " torentjes, " + cameras.size()
                            + " camera's, " + emitters.size() + " zenders, kern " + core);
            SecurityCoreBlockEntity coreEntity = (SecurityCoreBlockEntity) helper.getBlockEntity(core);
            List<BlockPos> loose = new java.util.ArrayList<>();
            for (List<BlockPos> devices : List.of(turrets, cameras, emitters)) {
                for (BlockPos device : devices) {
                    if (SecurityCores.deviceCore(level, helper.absolutePos(device)) != coreEntity) {
                        loose.add(device);
                    }
                }
            }
            check(loose.isEmpty(), "Deze apparaten van de toren hangen niet met een netwerkkabel aan de kern: " + loose);
            SecurityDevices.forget(level, coreEntity);
            var found = SecurityDevices.scan(level, coreEntity);
            check(found.turrets().size() == 9 && found.cameras().size() == 6,
                    "Devices ziet " + found.turrets().size() + " torentjes en " + found.cameras().size() + " camera's");
            helper.succeed();
        });
    }

    /**
     * Een EMP-puls legt een open netwerkkabel even plat (het apparaat erachter hoort dan bij geen kern), een netwerkkabel in
     * een muur niet. Na de puls werkt de open kabel weer.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "lan")
    public static void emp_cuts_open_network_cables(GameTestHelper helper) {
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(14, 3, 14), null);
        // Twee kabels naar twee lezers: één open, één in een muur.
        BlockPos openReader = new BlockPos(14, 3, 21);
        BlockPos walledReader = new BlockPos(21, 3, 14);
        helper.setBlock(openReader, ModBlocks.KEYCARD_READER.get());
        helper.setBlock(walledReader, ModBlocks.KEYCARD_READER.get());
        line(helper, CableKind.NETWORK, new BlockPos(14, 3, 15), new BlockPos(14, 3, 20));
        line(helper, CableKind.NETWORK, new BlockPos(15, 3, 14), new BlockPos(20, 3, 14));
        for (int x = 15; x <= 20; x++) {
            helper.setBlock(new BlockPos(x, 3, 14), Blocks.STONE_BRICKS);
        }
        check(coreOf(helper, openReader) == core && coreOf(helper, walledReader) == core,
                "De lezers hangen niet aan de kern");
        // Een puls over het midden van allebei de kabels (niet over de kern of de lezers).
        Emp.blast(helper.getLevel(), helper.absoluteVec(new Vec3(17.5, 3.5, 17.5)), 3.9, 40, null);
        check(coreOf(helper, openReader) == null, "Een open netwerkkabel in een EMP-puls geeft het netwerk nog door");
        check(coreOf(helper, walledReader) == core, "Een netwerkkabel in een muur werd ook doorgebrand");
        helper.startSequence()
                .thenIdle(45)
                .thenExecute(() -> check(coreOf(helper, openReader) == core, "Na de EMP-puls werkt de open netwerkkabel niet"))
                .thenSucceed();
    }

    // ================================================================ Hoofdstuk 112: de Router

    /** Een Router op deze plek met stroom (een volle generator eronder): aangemeld, en zijn lampjes branden. */
    private static BlockPos router(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.ROUTER.get());
        generator(helper, pos.below());
        ((RouterBlockEntity) helper.getBlockEntity(pos)).updateOnline(helper.getLevel());
        helper.assertBlockProperty(pos, RouterBlock.ONLINE, true);
        return helper.absolutePos(pos);
    }

    /** Doet {@code action} met een ander bereik en een ander aantal plekken voor de routers, en zet het daarna terug. */
    private static void withRouterLimits(float range, float devices, Runnable action) {
        float oldRange = ImtConfig.Network.ROUTER_RANGE;
        float oldDevices = ImtConfig.Network.ROUTER_DEVICES;
        ImtConfig.Network.ROUTER_RANGE = range;
        ImtConfig.Network.ROUTER_DEVICES = devices;
        LanGrid.changed();
        try {
            action.run();
        } finally {
            ImtConfig.Network.ROUTER_RANGE = oldRange;
            ImtConfig.Network.ROUTER_DEVICES = oldDevices;
            LanGrid.changed();
        }
    }

    /**
     * Jouw wens: "een router: hier kun je elk ding draadloos tot op 32 blokken op connecten". Een lezer zonder kabel hoort
     * bij geen kern; verbind je hem met een router die met een kabel aan de kern hangt, dan wel (en staat hij in Devices).
     * De weg van de lezer naar de kern loopt via de router. Zonder stroom geeft de router niets door (hij hangt zelf nog
     * wel aan de kern); maak je de lezer los, dan is hij weer van niemand. Buiten het bereik verbindt hij niet, en een
     * verbinding die buiten het bereik raakt (de config veranderde) telt niet meer.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void router_links_a_device_without_a_cable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), null);
        line(helper, CableKind.NETWORK, new BlockPos(3, 3, 2), new BlockPos(5, 3, 2));
        BlockPos routerPos = new BlockPos(6, 3, 2);
        BlockPos router = router(helper, routerPos);
        check(coreOf(helper, routerPos) == core, "De router hangt niet met zijn kabel aan de kern");
        BlockPos reader = new BlockPos(14, 3, 8);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        BlockPos readerAbs = helper.absolutePos(reader);
        check(coreOf(helper, reader) == null, "Een lezer zonder kabel en zonder router hoort bij een kern");
        check(Routers.link(level, router, readerAbs), "De router verbond de lezer niet: " + Routers.whyNot(level, router, readerAbs));
        check(coreOf(helper, reader) == core, "Een lezer aan een router hoort niet bij de kern van die router");
        SecurityDevices.forget(level, core);
        check(SecurityDevices.scan(level, core).inputs().contains(readerAbs), "Een lezer aan een router staat niet in Devices");
        List<BlockPos> path = LanGrid.path(level, readerAbs);
        check(path.size() >= 3 && path.getFirst().equals(readerAbs) && path.get(1).equals(router)
                && path.getLast().equals(core.getBlockPos()), "De weg van de lezer naar de kern loopt niet via de router: " + path);
        check(LanGrid.networkOf(level, readerAbs).containsAll(List.of(readerAbs, router, core.getBlockPos())),
                "De lezer, de router en de kern zitten niet in hetzelfde net: " + LanGrid.networkOf(level, readerAbs));
        check("screen.imttech.router.already".equals(Routers.whyNot(level, router, readerAbs)),
                "Twee keer dezelfde verbinding kan: " + Routers.whyNot(level, router, readerAbs));
        // Geen stroom: de router hangt zelf nog aan de kern, maar geeft niets door.
        RouterBlockEntity entity = (RouterBlockEntity) helper.getBlockEntity(routerPos);
        helper.setBlock(routerPos.below(), Blocks.AIR);
        entity.updateOnline(level);
        helper.assertBlockProperty(routerPos, RouterBlock.ONLINE, false);
        check(coreOf(helper, reader) == null, "Een router zonder stroom geeft het netwerk nog door");
        check(coreOf(helper, routerPos) == core, "Een router zonder stroom hangt zelf niet meer aan de kern");
        generator(helper, routerPos.below());
        entity.updateOnline(level);
        check(coreOf(helper, reader) == core, "Met stroom terug geeft de router het netwerk niet weer door");
        // Het bereik: buiten bereik verbindt hij niet, en wat buiten bereik raakt, telt niet meer.
        BlockPos panel = new BlockPos(9, 3, 2);
        helper.setBlock(panel, ModBlocks.CODE_PANEL.get());
        withRouterLimits(5, 10, () -> {
            check(coreOf(helper, reader) == null, "Een lezer buiten het (nieuwe) bereik hangt nog aan de router");
            BlockPos far = new BlockPos(20, 3, 2);
            helper.setBlock(far, ModBlocks.KEYCARD_READER.get());
            check("screen.imttech.router.too_far".equals(Routers.whyNot(level, router, helper.absolutePos(far))),
                    "Te ver weg geeft: " + Routers.whyNot(level, router, helper.absolutePos(far)));
            check(!Routers.link(level, router, helper.absolutePos(far)), "De router verbond een lezer buiten zijn bereik");
            check(Routers.link(level, router, helper.absolutePos(panel)) && coreOf(helper, panel) == core,
                    "Een paneel binnen het bereik hangt niet aan de router");
        });
        check(coreOf(helper, reader) == core, "Terug in bereik hangt de lezer niet weer aan de router");
        // Losmaken, en geen netwerkblok.
        check(Routers.unlink(level, router, readerAbs) && coreOf(helper, reader) == null, "Losgemaakt hoort de lezer nog bij de kern");
        helper.setBlock(new BlockPos(8, 3, 6), Blocks.STONE);
        check("screen.imttech.router.not_a_device".equals(Routers.whyNot(level, router, helper.absolutePos(new BlockPos(8, 3, 6)))),
                "Een steen kun je verbinden");
        helper.succeed();
    }

    /**
     * Jouw wens: "elke router kan max 10 devices en kan ook verbinden met andere routers, die horen dan ook bij één van de
     * devices". Een tweede router zonder kabel hangt draadloos aan de eerste; een lezer aan de tweede hoort zo bij de kern
     * (twee sprongen door de lucht). De verbinding tussen de routers kost bij allebei één plek. Is een router vol, dan
     * verbindt hij niets meer, ook niet met een router; haal je een router weg, dan zijn al zijn verbindingen weg.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void routers_chain_and_have_ten_places(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RouterLinks links = RouterLinks.get(level);
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 12), null);
        line(helper, CableKind.NETWORK, new BlockPos(3, 3, 12), new BlockPos(5, 3, 12));
        BlockPos first = router(helper, new BlockPos(6, 3, 12));
        BlockPos secondPos = new BlockPos(16, 3, 12);
        BlockPos second = router(helper, secondPos);
        check(links.isRouter(first) && links.isRouter(second), "Een router meldt zich niet aan als hij neergezet wordt");
        check(coreOf(helper, secondPos) == null, "Een router zonder kabel en zonder verbinding hangt aan een kern");
        check(Routers.link(level, first, second), "De routers verbonden niet: " + Routers.whyNot(level, first, second));
        check(links.count(first) == 1 && links.count(second) == 1, "De verbinding tussen twee routers kost niet bij allebei één "
                + "plek: " + links.count(first) + " en " + links.count(second));
        check(coreOf(helper, secondPos) == core, "De tweede router hangt niet via de eerste aan de kern");
        BlockPos reader = new BlockPos(20, 3, 16);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        BlockPos readerAbs = helper.absolutePos(reader);
        check(Routers.link(level, second, readerAbs) && coreOf(helper, reader) == core,
                "Een lezer aan de tweede router hoort niet bij de kern");
        check(first.equals(links.routerOf(readerAbs)) == false && second.equals(links.routerOf(readerAbs)),
                "De lezer hangt niet aan de tweede router");
        // Een apparaat hangt aan hooguit één router: verbindt de eerste hem, dan verhuist hij.
        check(Routers.link(level, first, readerAbs) && first.equals(links.routerOf(readerAbs)) && links.count(second) == 1,
                "Een lezer die een andere router verbindt, verhuisde niet");
        check(Routers.link(level, second, readerAbs), "De lezer kon niet terug naar de tweede router");
        // Vol: de tweede router heeft nu de eerste router en de lezer. Met twee plekken past er niets meer bij.
        BlockPos panel = new BlockPos(18, 3, 10);
        helper.setBlock(panel, ModBlocks.CODE_PANEL.get());
        BlockPos third = router(helper, new BlockPos(12, 3, 18));
        withRouterLimits(32, 2, () -> {
            check("screen.imttech.router.full".equals(Routers.whyNot(level, second, helper.absolutePos(panel))),
                    "Een volle router zegt: " + Routers.whyNot(level, second, helper.absolutePos(panel)));
            check("screen.imttech.router.other_full".equals(Routers.whyNot(level, third, second)),
                    "Verbinden met een volle router zegt: " + Routers.whyNot(level, third, second));
            check(!Routers.link(level, second, helper.absolutePos(panel)) && !Routers.link(level, third, second),
                    "Een volle router verbond toch nog iets");
        });
        check(Routers.link(level, second, helper.absolutePos(panel)), "Met tien plekken past het paneel er niet bij");
        // De tweede router weg: al zijn verbindingen ook, en de lezer is weer van niemand.
        helper.setBlock(secondPos, Blocks.AIR);
        check(!links.isRouter(second) && links.count(first) == 0 && links.routerOf(readerAbs) == null,
                "Na het weghalen van een router bestaan zijn verbindingen nog");
        check(coreOf(helper, reader) == null, "Zonder zijn router hoort de lezer nog bij de kern");
        helper.succeed();
    }

    /**
     * Vanzelf: een router die je neerzet, zoekt eerst zijn eigen weg naar het netwerk (hier: de kern in de buurt) en pakt
     * dan de losse apparaten binnen zijn bereik. Wat al aan een netwerk hangt (een lezer tegen de kern aan) laat hij met
     * rust, en van een laserpoort van twee zenders verbindt hij er maar één: de andere hangt eraan vast. Een apparaat dat
     * je daarna neerzet, gaat zelf aan de router, tot die vol is. En alleen aan een router die je mag instellen.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void routers_and_devices_find_each_other(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RouterLinks links = RouterLinks.get(level);
        ServerPlayer alice = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(10, 3, 12), "lan-router-own");
        ServerPlayer bob = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(10, 3, 12), "lan-router-other");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), null);
        BlockPos touching = new BlockPos(3, 3, 2);
        helper.setBlock(touching, ModBlocks.KEYCARD_READER.get());
        BlockPos loose = new BlockPos(12, 3, 10);
        helper.setBlock(loose, ModBlocks.KEYCARD_READER.get());
        BlockPos panel = new BlockPos(10, 3, 14);
        helper.setBlock(panel, ModBlocks.CODE_PANEL.get());
        BlockPos gateLow = new BlockPos(14, 3, 6);
        helper.setBlock(gateLow, ModBlocks.LASER_EMITTER.get());
        helper.setBlock(gateLow.above(), ModBlocks.LASER_EMITTER.get());
        BlockPos routerPos = new BlockPos(10, 3, 10);
        BlockPos router = router(helper, routerPos);
        int linked = Routers.autoLink(level, router, null);
        List<BlockPos> now = links.linked(router);
        check(now.contains(core.getBlockPos()), "De router zocht niet zelf de kern in de buurt: " + now);
        check(now.contains(helper.absolutePos(loose)) && now.contains(helper.absolutePos(panel)),
                "De router pakte de losse lezer en het paneel niet: " + now);
        check(!now.contains(helper.absolutePos(touching)), "De router pakte een lezer die al tegen de kern aan staat");
        boolean low = now.contains(helper.absolutePos(gateLow));
        boolean high = now.contains(helper.absolutePos(gateLow.above()));
        check(low != high, "Van een laserpoort van twee zenders hoort er precies één aan de router: " + now);
        check(linked == 4 && links.count(router) == 4, "De router verbond er " + linked + " (en telt " + links.count(router) + ")");
        check(coreOf(helper, gateLow) == core && coreOf(helper, gateLow.above()) == core, "De hele laserpoort hangt niet aan de kern");
        // Een nieuw apparaat gaat zelf aan de router (tot die vol is).
        BlockPos fresh = new BlockPos(8, 3, 8);
        helper.setBlock(fresh, ModBlocks.KEYCARD_READER.get());
        check(router.equals(Routers.autoJoin(level, helper.absolutePos(fresh), null)) && coreOf(helper, fresh) == core,
                "Een nieuwe lezer ging niet zelf aan de router");
        check(Routers.autoJoin(level, helper.absolutePos(fresh), null) == null, "Een lezer die al verbonden is, verbond nog eens");
        BlockPos extra = new BlockPos(8, 3, 12);
        helper.setBlock(extra, ModBlocks.KEYCARD_READER.get());
        // Bereik 8 (hoofdstuk 124): de arena's liggen 29 blokken uit elkaar, en met 32 blokken nam de lezer soms de router van
        // een test ernaast (die niet vol is). Elke test die erbij komt, schuift de arena's op.
        withRouterLimits(8, 5, () -> {
            BlockPos joined = Routers.autoJoin(level, helper.absolutePos(extra), null);
            check(joined == null && links.routerOf(helper.absolutePos(extra)) == null,
                    "Een lezer ging aan een volle router (hij ging aan " + joined + ", deze router: " + router + ")");
        });
        // Alleen aan een router die je mag instellen: die van Alice neemt wat Bob neerzet niet, wat Alice neerzet wel.
        ((RouterBlockEntity) helper.getBlockEntity(routerPos)).setOwner(alice);
        check(Routers.autoJoin(level, helper.absolutePos(extra), bob) == null, "De lezer van een ander ging aan de router van Alice");
        check(router.equals(Routers.autoJoin(level, helper.absolutePos(extra), alice)), "De lezer van Alice ging niet aan haar router");
        helper.succeed();
    }

    /**
     * Het scherm van de router: alleen wie hem mag instellen (wie hem neerzette) verbindt en maakt los, en alleen met
     * apparaten die hij zelf mag instellen. Van te ver weg doet een knop niets.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void router_screen_links_only_what_you_may_change(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RouterLinks links = RouterLinks.get(level);
        ServerPlayer alice = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "lan-screen-a");
        ServerPlayer bob = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "lan-screen-b");
        BlockPos routerPos = new BlockPos(6, 3, 4);
        BlockPos router = router(helper, routerPos);
        ((RouterBlockEntity) helper.getBlockEntity(routerPos)).setOwner(alice);
        BlockPos reader = new BlockPos(10, 3, 4);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        ((com.senne.imttech.logic.InputDeviceBlockEntity) helper.getBlockEntity(reader)).setOwner(alice);
        BlockPos readerAbs = helper.absolutePos(reader);
        Routers.handleAction(bob, new RouterActionPayload(router, RouterActionPayload.LINK, readerAbs));
        check(!links.linked(router, readerAbs), "Bob verbond iets aan de router van Alice");
        Routers.handleAction(alice, new RouterActionPayload(router, RouterActionPayload.LINK, readerAbs));
        check(links.linked(router, readerAbs), "Alice kon haar lezer niet aan haar router hangen");
        Routers.handleAction(bob, new RouterActionPayload(router, RouterActionPayload.UNLINK, readerAbs));
        check(links.linked(router, readerAbs), "Bob maakte iets los van de router van Alice");
        // Van te ver weg doet een knop niets.
        alice.teleportTo(alice.getX() + 12, alice.getY(), alice.getZ());
        Routers.handleAction(alice, new RouterActionPayload(router, RouterActionPayload.UNLINK, readerAbs));
        check(links.linked(router, readerAbs), "Een knop van te ver weg werkte");
        alice.teleportTo(alice.getX() - 12, alice.getY(), alice.getZ());
        Routers.handleAction(alice, new RouterActionPayload(router, RouterActionPayload.UNLINK, readerAbs));
        check(!links.linked(router, readerAbs), "Alice kon haar lezer niet losmaken");
        // Bob met zijn eigen router: de lezer van Alice mag hij niet instellen, dus ook niet verbinden.
        BlockPos bobRouterPos = new BlockPos(6, 3, 8);
        BlockPos bobRouter = router(helper, bobRouterPos);
        ((RouterBlockEntity) helper.getBlockEntity(bobRouterPos)).setOwner(bob);
        Routers.handleAction(bob, new RouterActionPayload(bobRouter, RouterActionPayload.LINK, readerAbs));
        check(!links.linked(bobRouter, readerAbs), "Bob hing de lezer van Alice aan zijn eigen router");
        BlockPos own = new BlockPos(10, 3, 8);
        helper.setBlock(own, ModBlocks.KEYCARD_READER.get());
        ((com.senne.imttech.logic.InputDeviceBlockEntity) helper.getBlockEntity(own)).setOwner(bob);
        Routers.handleAction(bob, new RouterActionPayload(bobRouter, RouterActionPayload.LINK, helper.absolutePos(own)));
        check(links.linked(bobRouter, helper.absolutePos(own)), "Bob kon zijn eigen lezer niet aan zijn eigen router hangen");
        // Vanzelf verbinden neemt niets over: een relais en een lezer van niemand blijven van niemand (gevonden door de
        // nalezing: een router neerzetten maakte ze van jou, tot 32 blokken ver).
        BlockPos nobodysRelay = new BlockPos(12, 3, 10);
        helper.setBlock(nobodysRelay, ModBlocks.RELAY.get());
        BlockPos nobodysReader = new BlockPos(12, 3, 12);
        helper.setBlock(nobodysReader, ModBlocks.KEYCARD_READER.get());
        Routers.autoLink(level, bobRouter, bob);
        Routers.autoJoin(level, helper.absolutePos(nobodysReader), alice);
        check(ImtRedstone.relayOwner(level, helper.absolutePos(nobodysRelay)) == null,
                "Een router neerzetten nam een relais van niemand over");
        check(((com.senne.imttech.logic.InputDeviceBlockEntity) helper.getBlockEntity(nobodysReader)).owner() == null,
                "Vanzelf verbinden nam een lezer van niemand over");
        check(bob.getUUID().equals(((RouterBlockEntity) helper.getBlockEntity(bobRouterPos)).owner()),
                "Een apparaat neerzetten nam de router van een ander over");
        // Een knop met een plek heel ver weg (een stuk wereld dat niet geladen is) doet niets, en laadt niets.
        BlockPos farAway = router.offset(20_000, 0, 20_000);
        Routers.handleAction(alice, new RouterActionPayload(farAway, RouterActionPayload.REFRESH, farAway));
        Routers.handleAction(alice, new RouterActionPayload(router, RouterActionPayload.LINK, farAway));
        check(!level.isLoaded(farAway), "Een knop van het routerscherm laadde een stuk wereld ver weg");
        helper.succeed();
    }

    /**
     * Gevonden door de nalezing: een tweede router bij de kern pakte de kern af van de eerste, en dan had alles aan de
     * eerste router geen netwerk meer. De kern mag aan meer routers tegelijk hangen (bij elke router één plek); een gewoon
     * apparaat verhuist nog steeds. Maak je één router los van de kern, dan houdt de andere hem; is de kern weg, dan komen
     * zijn plekken bij allebei de routers weer vrij.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void two_routers_share_the_core(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RouterLinks links = RouterLinks.get(level);
        BlockPos corePos = new BlockPos(12, 3, 12);
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, corePos, null);
        BlockPos coreAbs = core.getBlockPos();
        BlockPos first = router(helper, new BlockPos(5, 3, 12));
        BlockPos reader = new BlockPos(3, 3, 8);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        BlockPos readerAbs = helper.absolutePos(reader);
        check(Routers.link(level, first, coreAbs) && Routers.link(level, first, readerAbs) && coreOf(helper, reader) == core,
                "De lezer hangt niet via de eerste router aan de kern");
        BlockPos second = router(helper, new BlockPos(19, 3, 12));
        BlockPos panel = new BlockPos(21, 3, 8);
        helper.setBlock(panel, ModBlocks.CODE_PANEL.get());
        BlockPos panelAbs = helper.absolutePos(panel);
        check(Routers.link(level, second, coreAbs), "De tweede router verbond de kern niet: " + Routers.whyNot(level, second, coreAbs));
        check(links.linked(first, coreAbs) && links.linked(second, coreAbs),
                "De tweede router pakte de kern af van de eerste: " + links.routersOf(coreAbs));
        check(coreOf(helper, reader) == core, "De lezer aan de eerste router verloor zijn kern toen de tweede router de kern verbond");
        check(Routers.link(level, second, panelAbs) && coreOf(helper, panel) == core, "Het paneel hangt niet via de tweede router aan de kern");
        check(links.count(first) == 2 && links.count(second) == 2, "De kern kost niet bij elke router één plek: "
                + links.count(first) + " en " + links.count(second));
        SecurityDevices.forget(level, core);
        check(SecurityDevices.scan(level, core).inputs().containsAll(List.of(readerAbs, panelAbs)),
                "De kern ziet niet de apparaten van allebei zijn routers");
        // Een gewoon apparaat verhuist nog steeds.
        check(Routers.link(level, second, readerAbs) && second.equals(links.routerOf(readerAbs)) && !links.linked(first, readerAbs)
                && links.routersOf(readerAbs).size() == 1, "Een gewoon apparaat hangt aan twee routers: " + links.routersOf(readerAbs));
        check(Routers.link(level, first, readerAbs), "De lezer kon niet terug naar de eerste router");
        // Eén router los van de kern: de andere houdt hem.
        check(Routers.unlink(level, first, coreAbs) && links.linked(second, coreAbs) && !links.linked(first, coreAbs),
                "Losmaken van de ene router maakte de kern ook los van de andere");
        check(coreOf(helper, reader) == null && coreOf(helper, panel) == core, "Na het losmaken klopt niet meer wie de kern heeft");
        check(Routers.link(level, first, coreAbs) && coreOf(helper, reader) == core, "De eerste router kon de kern niet opnieuw verbinden");
        // De kern weg: bij allebei de routers komt zijn plek weer vrij (zodra het netwerk er langs zoekt).
        helper.setBlock(corePos, Blocks.AIR);
        check(coreOf(helper, reader) == null && coreOf(helper, panel) == null, "Zonder kern hebben de apparaten nog een kern");
        check(links.routersOf(coreAbs).isEmpty() && links.count(first) == 1 && links.count(second) == 1,
                "Een kern die weg is, houdt nog een plek bezet: " + links.linked(first) + " en " + links.linked(second));
        helper.succeed();
    }

    /**
     * De lijst van de routers overleeft bewaren en weer lezen (data/imttech_routers.dat): wat er aan welke router hangt (in
     * dezelfde volgorde), twee routers aan elkaar, en een kern aan twee routers tegelijk. En daarna werkt hij nog: haal je
     * een router weg, dan blijft de kern aan de andere hangen.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void router_links_survive_a_save(GameTestHelper helper) {
        RouterLinks links = new RouterLinks();
        BlockPos first = new BlockPos(100, 64, 100);
        BlockPos second = new BlockPos(120, 64, 100);
        BlockPos core = new BlockPos(110, 64, 110);
        BlockPos reader = new BlockPos(101, 64, 105);
        BlockPos relay = new BlockPos(125, -20, 96);
        links.addRouter(first);
        links.addRouter(second);
        check(links.link(first, second) && links.link(first, core, true) && links.link(second, core, true)
                && links.link(first, reader) && links.link(second, relay), "De lijst nam een verbinding niet aan");
        check(!links.link(first, second) && !links.link(second, first) && !links.link(reader, relay) && !links.link(first, first),
                "De lijst nam een verbinding aan die niet kan (dubbel, geen router, of met zichzelf)");
        var registries = helper.getLevel().registryAccess();
        RouterLinks loaded = RouterLinks.load(links.save(new CompoundTag(), registries), registries);
        check(loaded.routers().equals(java.util.Set.of(first, second)), "Na het lezen zijn dit de routers: " + loaded.routers());
        check(loaded.linked(first).equals(List.of(second, core, reader)) && loaded.linked(second).equals(List.of(first, core, relay)),
                "Na het lezen hangt er iets anders aan de routers: " + loaded.linked(first) + " en " + loaded.linked(second));
        check(java.util.Set.copyOf(loaded.routersOf(core)).equals(java.util.Set.of(first, second)),
                "Na het lezen hangt de kern niet meer aan allebei de routers: " + loaded.routersOf(core));
        check(first.equals(loaded.routerOf(reader)) && second.equals(loaded.routerOf(relay)) && loaded.routerOf(first) == null,
                "Na het lezen klopt niet meer aan welke router een apparaat hangt");
        check(loaded.partners(reader).equals(List.of(first)) && java.util.Set.copyOf(loaded.partners(core)).equals(java.util.Set.of(first, second))
                && loaded.partners(first).equals(List.of(second, core, reader)), "Na het lezen kloppen de draadloze buren niet");
        loaded.removeRouter(first);
        check(loaded.routersOf(core).equals(List.of(second)) && loaded.routerOf(reader) == null && loaded.linked(second).equals(List.of(core, relay))
                && !loaded.isRouter(first), "Na het weghalen van een router klopt de gelezen lijst niet: " + loaded.linked(second));
        loaded.forget(core);
        check(loaded.routersOf(core).isEmpty() && loaded.linked(second).equals(List.of(relay)), "Een kern vergeten laat iets achter");
        helper.succeed();
    }

    /**
     * Gevonden door de nalezing: (1) het torentje van een ander kon je aan je eigen router hangen, en (2) het scherm van
     * een router toonde elk netwerkapparaat binnen 32 blokken aan iedereen die hem opende, ook wat een ander achter een
     * muur heeft staan. Nu krijgt alleen wie de router mag instellen zijn scherm, staat onder IN RANGE alleen wat je zelf
     * mag verbinden, en verbindt een router (met de hand of vanzelf) nooit het torentje, het relais of de lezer van een
     * ander. En wie heel snel achter elkaar op de knoppen drukt, komt niet verder dan acht keer.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void router_screen_keeps_other_devices_secret(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RouterLinks links = RouterLinks.get(level);
        ServerPlayer alice = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "lan-secret-a");
        ServerPlayer bob = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "lan-secret-b");
        BlockPos aliceRouterPos = new BlockPos(6, 3, 4);
        BlockPos aliceRouter = router(helper, aliceRouterPos);
        ((RouterBlockEntity) helper.getBlockEntity(aliceRouterPos)).setOwner(alice);
        BlockPos bobRouterPos = new BlockPos(6, 3, 8);
        BlockPos bobRouter = router(helper, bobRouterPos);
        ((RouterBlockEntity) helper.getBlockEntity(bobRouterPos)).setOwner(bob);
        // Van Alice: een lezer, een torentje en een relais. Van Bob: een lezer.
        BlockPos reader = new BlockPos(10, 3, 4);
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        ((com.senne.imttech.logic.InputDeviceBlockEntity) helper.getBlockEntity(reader)).setOwner(alice);
        BlockPos turret = new BlockPos(10, 3, 6);
        helper.setBlock(turret, ModBlocks.SECURITY_TURRET.get());
        ((TurretBlockEntity) helper.getBlockEntity(turret)).setOwner(alice);
        BlockPos relay = new BlockPos(10, 3, 8);
        helper.setBlock(relay, ModBlocks.RELAY.get());
        ((RelayBlockEntity) helper.getBlockEntity(relay)).setOwner(alice);
        BlockPos own = new BlockPos(11, 3, 10);
        helper.setBlock(own, ModBlocks.KEYCARD_READER.get());
        ((com.senne.imttech.logic.InputDeviceBlockEntity) helper.getBlockEntity(own)).setOwner(bob);
        BlockPos readerAbs = helper.absolutePos(reader);
        BlockPos turretAbs = helper.absolutePos(turret);
        BlockPos relayAbs = helper.absolutePos(relay);
        BlockPos ownAbs = helper.absolutePos(own);
        // Het scherm: alleen voor wie de router mag instellen.
        check(Routers.screenData(bob, aliceRouter, true) == null, "Bob kreeg het scherm van de router van Alice");
        com.senne.imttech.network.RouterOpenPayload forAlice = Routers.screenData(alice, aliceRouter, true);
        com.senne.imttech.network.RouterOpenPayload forBob = Routers.screenData(bob, bobRouter, true);
        check(forAlice != null && forBob != null, "Wie zijn eigen router opent, krijgt geen scherm");
        List<BlockPos> aliceSees = forAlice.nearby().stream().map(com.senne.imttech.network.RouterOpenPayload.Entry::pos).toList();
        List<BlockPos> bobSees = forBob.nearby().stream().map(com.senne.imttech.network.RouterOpenPayload.Entry::pos).toList();
        check(aliceSees.containsAll(List.of(readerAbs, turretAbs, relayAbs)), "Alice ziet haar eigen apparaten niet onder IN RANGE: " + aliceSees);
        check(!aliceSees.contains(ownAbs) && !aliceSees.contains(bobRouter), "Alice ziet de lezer of de router van Bob: " + aliceSees);
        check(bobSees.contains(ownAbs), "Bob ziet zijn eigen lezer niet onder IN RANGE: " + bobSees);
        check(!bobSees.contains(readerAbs) && !bobSees.contains(turretAbs) && !bobSees.contains(relayAbs) && !bobSees.contains(aliceRouter),
                "Het scherm van Bobs router verraadt de apparaten van Alice: " + bobSees);
        // Verbinden: met de hand niet, en vanzelf ook niet (hier met een kort bereik, zodat de router in dit veld blijft).
        Routers.handleAction(bob, new RouterActionPayload(bobRouter, RouterActionPayload.LINK, turretAbs));
        Routers.handleAction(bob, new RouterActionPayload(bobRouter, RouterActionPayload.LINK, relayAbs));
        Routers.handleAction(bob, new RouterActionPayload(bobRouter, RouterActionPayload.LINK, aliceRouter));
        withRouterLimits(8, 10, () -> Routers.autoLink(level, bobRouter, bob));
        check(!links.linked(bobRouter, turretAbs) && links.routerOf(turretAbs) == null, "Bob hing het torentje van Alice aan zijn router");
        check(!links.linked(bobRouter, relayAbs) && !links.linked(bobRouter, readerAbs) && !links.linked(bobRouter, aliceRouter),
                "Bob hing het relais, de lezer of de router van Alice aan zijn router: " + links.linked(bobRouter));
        check(links.linked(bobRouter, ownAbs), "De router van Bob verbond zijn eigen lezer niet vanzelf: " + links.linked(bobRouter));
        Routers.handleAction(alice, new RouterActionPayload(aliceRouter, RouterActionPayload.LINK, turretAbs));
        Routers.handleAction(alice, new RouterActionPayload(aliceRouter, RouterActionPayload.LINK, relayAbs));
        check(links.linked(aliceRouter, turretAbs) && links.linked(aliceRouter, relayAbs), "Alice kon haar eigen torentje en relais niet verbinden");
        // Wat eraan hangt, staat onder LINKED en niet meer onder IN RANGE.
        com.senne.imttech.network.RouterOpenPayload after = Routers.screenData(alice, aliceRouter, false);
        check(after != null && after.linked().stream().anyMatch(entry -> entry.pos().equals(turretAbs))
                && after.nearby().stream().noneMatch(entry -> entry.pos().equals(turretAbs)), "Het torentje staat niet onder LINKED");
        // Acht knoppen kort na elkaar gaan; de negende niet (het netwerk zoekt na elke verbinding opnieuw).
        Routers.clear();
        for (int i = 0; i < 4; i++) {
            Routers.handleAction(alice, new RouterActionPayload(aliceRouter, RouterActionPayload.LINK, readerAbs));
            Routers.handleAction(alice, new RouterActionPayload(aliceRouter, RouterActionPayload.UNLINK, readerAbs));
        }
        check(!links.linked(aliceRouter, readerAbs), "Acht knoppen na elkaar werkten niet allemaal");
        Routers.handleAction(alice, new RouterActionPayload(aliceRouter, RouterActionPayload.LINK, readerAbs));
        check(!links.linked(aliceRouter, readerAbs), "Een negende knop in dezelfde tick werkte nog");
        Routers.clear();
        Routers.handleAction(alice, new RouterActionPayload(aliceRouter, RouterActionPayload.LINK, readerAbs));
        check(links.linked(aliceRouter, readerAbs), "Na een pauze werkt de knop niet weer");
        helper.succeed();
    }

    /**
     * Jouw wens: "relay's moeten hier ook aanhangen maar hebben nog steeds geen stroom nodig ... als je geen router gebruikt
     * dan heeft een relay wel een netwerkkabel nodig". Een relais van vóór de Router (hier: neergezet zonder speler) werkt
     * zoals vroeger. Een nieuw relais luistert niet zonder netwerk: niet naar een diagram (switchAt), niet naar je bril;
     * alleen zijn eigen knop (je staat ernaast, zonder bril) schakelt het. Met een netwerkkabel naar de kern luistert het,
     * en na het doorknippen weer niet. Aan een router met stroom ook, zonder stroom niet. In het telefoonboek van de kern
     * staat een nieuw relais alleen als het aan zijn netwerk hangt. En met oldRelaysNeedNetwork heeft ook een oud relais
     * een netwerk nodig.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void new_relays_need_a_network(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 5), "lan-relay");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        // Een oud relais (twee blokken van de kern, dus niet er tegenaan).
        BlockPos old = new BlockPos(2, 3, 4);
        helper.setBlock(old, ModBlocks.RELAY.get());
        BlockPos oldAbs = helper.absolutePos(old);
        check(!ImtRedstone.needsNetwork(level, oldAbs) && !ImtRedstone.offline(level, oldAbs), "Een oud relais heeft een netwerk nodig");
        check(ImtRedstone.switchAt(level, oldAbs, -1, ImtRedstone.Op.ON, 20) && helper.getBlockState(old).getValue(RelayBlock.POWERED),
                "Een oud relais schakelt niet meer zonder netwerk");
        // Een nieuw relais (zoals een speler het neerzet).
        BlockPos fresh = new BlockPos(8, 3, 2);
        helper.setBlock(fresh, ModBlocks.RELAY.get());
        BlockPos freshAbs = helper.absolutePos(fresh);
        RelayBlockEntity relay = (RelayBlockEntity) helper.getBlockEntity(fresh);
        relay.setOwner(owner);
        relay.setNetworked(true);
        check(ImtRedstone.needsNetwork(level, freshAbs) && ImtRedstone.offline(level, freshAbs), "Een nieuw relais zonder kabel telt als verbonden");
        check(!ImtRedstone.switchAt(level, freshAbs, -1, ImtRedstone.Op.ON, 20) && !helper.getBlockState(fresh).getValue(RelayBlock.POWERED),
                "Een nieuw relais zonder netwerk schakelde van op afstand");
        check(withNetwork(false, () -> !ImtRedstone.needsNetwork(level, freshAbs)), "Zonder netwerk-eis heeft een relais toch een netwerk nodig");
        // Met je bril (op afstand): niets. Zonder bril, ernaast, met zijn eigen knop: wel.
        ImtRedstone.register(owner, freshAbs);
        ImtRedstone.register(owner, oldAbs);
        int index = owner.getData(ModAttachments.RELAYS).indexAt(level.dimension(), freshAbs);
        int oldIndex = owner.getData(ModAttachments.RELAYS).indexAt(level.dimension(), oldAbs);
        check(index >= 0 && oldIndex >= 0, "De relais kwamen niet in de lijst");
        ImtRedstone.handleAction(owner, RelayActionPayload.rename(index, "Fresh", ""));
        ImtRedstone.handleAction(owner, RelayActionPayload.rename(oldIndex, "Old", ""));
        // Van ver (meer dan 6 blokken), met je bril: niets.
        owner.teleportTo(owner.getX() + 14, owner.getY(), owner.getZ());
        ImtRedstone.handleAction(owner, RelayActionPayload.switchOne(index, ImtRedstone.Op.ON));
        check(!helper.getBlockState(fresh).getValue(RelayBlock.POWERED), "Je bril schakelde van ver een relais zonder netwerk");
        // Ernaast is het zijn eigen knop: die werkt, ook met je bril op (gevonden door de nalezing)...
        owner.teleportTo(owner.getX() - 14, owner.getY(), owner.getZ());
        ImtRedstone.handleAction(owner, RelayActionPayload.switchOne(index, ImtRedstone.Op.ON));
        check(helper.getBlockState(fresh).getValue(RelayBlock.POWERED),
                "De eigen knop van een relais zonder netwerk werkt niet met je bril op");
        ImtRedstone.handleAction(owner, RelayActionPayload.switchOne(index, ImtRedstone.Op.OFF));
        // ...en zonder bril.
        ItemStack glasses = owner.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD);
        owner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
        ImtRedstone.handleAction(owner, RelayActionPayload.switchOne(index, ImtRedstone.Op.ON));
        check(helper.getBlockState(fresh).getValue(RelayBlock.POWERED), "De eigen knop van een relais zonder netwerk werkt niet");
        ImtRedstone.handleAction(owner, RelayActionPayload.switchOne(index, ImtRedstone.Op.OFF));
        owner.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, glasses);
        // Het telefoonboek van de kern: het oude relais wel (het staat in zijn gebied), het nieuwe zonder netwerk niet.
        com.senne.imttech.logic.LogicDevices.refresh(level, core, owner);
        check(!core.logic().devices("Old").isEmpty(), "Het oude relais staat niet in het telefoonboek van de kern");
        check(core.logic().devices("Fresh").isEmpty(), "Een nieuw relais zonder netwerk staat in het telefoonboek van de kern");
        // Een netwerkkabel van de kern naar het relais.
        line(helper, CableKind.NETWORK, new BlockPos(3, 3, 2), new BlockPos(7, 3, 2));
        check(!ImtRedstone.offline(level, freshAbs) && relay.linked(), "Met een netwerkkabel heeft het relais geen netwerk");
        check(ImtRedstone.switchAt(level, freshAbs, -1, ImtRedstone.Op.ON, 20) && helper.getBlockState(fresh).getValue(RelayBlock.POWERED),
                "Met een netwerkkabel schakelt het relais niet");
        ImtRedstone.handleAction(owner, RelayActionPayload.switchOne(index, ImtRedstone.Op.OFF));
        check(!helper.getBlockState(fresh).getValue(RelayBlock.POWERED), "Met een netwerkkabel schakelt je bril het relais niet");
        com.senne.imttech.logic.LogicDevices.refresh(level, core, owner);
        check(!core.logic().devices("Fresh").isEmpty(), "Een relais aan het netwerk staat niet in het telefoonboek van de kern");
        // Doorgeknipt: weer niets (en het relais weet dat).
        Cables.remove(CableKind.NETWORK, level, helper.absolutePos(new BlockPos(5, 3, 2)));
        check(ImtRedstone.offline(level, freshAbs) && !relay.linked(), "Na het doorknippen heeft het relais nog een netwerk");
        check(!ImtRedstone.switchAt(level, freshAbs, -1, ImtRedstone.Op.ON, 20), "Na het doorknippen schakelt het relais nog");
        // Een router (zelf draadloos aan de kern) met stroom: het relais luistert. Zonder stroom niet.
        BlockPos routerPos = new BlockPos(8, 3, 8);
        BlockPos router = router(helper, routerPos);
        check(Routers.link(level, router, core.getBlockPos()) && Routers.link(level, router, freshAbs),
                "De router verbond de kern en het relais niet");
        check(!ImtRedstone.offline(level, freshAbs) && ImtRedstone.switchAt(level, freshAbs, -1, ImtRedstone.Op.ON, 20),
                "Aan een router met stroom luistert het relais niet");
        helper.setBlock(routerPos.below(), Blocks.AIR);
        ((RouterBlockEntity) helper.getBlockEntity(routerPos)).updateOnline(level);
        check(ImtRedstone.offline(level, freshAbs) && !ImtRedstone.switchAt(level, freshAbs, -1, ImtRedstone.Op.OFF, 20),
                "Aan een router zonder stroom luistert het relais nog");
        // Met de config-knop hebben ook oude relais een netwerk nodig.
        boolean before = ImtConfig.Network.OLD_RELAYS_NEED_NETWORK;
        ImtConfig.Network.OLD_RELAYS_NEED_NETWORK = true;
        try {
            check(ImtRedstone.needsNetwork(level, oldAbs) && ImtRedstone.offline(level, oldAbs),
                    "Met oldRelaysNeedNetwork heeft een oud relais geen netwerk nodig");
        } finally {
            ImtConfig.Network.OLD_RELAYS_NEED_NETWORK = before;
        }
        helper.succeed();
    }

    // ================================================================ Hoofdstuk 124: lange kabels

    /**
     * Een lange kabel als slang door de arena: lagen op hoogte 2, 4, 6 ... (met een lege laag ertussen), in elke laag rijen
     * van x = 1 tot 22 op z = 1, 3, 5 ... 21 (met een lege rij ertussen), aan de uiteinden met één stukje aan elkaar. Zo
     * raakt geen stuk een ander dan zijn twee buren: het is één kabel van 252 stukjes per laag, zonder kortere weg. Geeft
     * alle plekken op volgorde, vanaf (1, 2, 1).
     */
    private static List<BlockPos> snake(int layers) {
        List<BlockPos> path = new java.util.ArrayList<>();
        int x = 1;
        int z = 1;
        for (int layer = 0; layer < layers; layer++) {
            int y = 2 + 2 * layer;
            if (layer > 0) {
                path.add(new BlockPos(x, y - 1, z)); // omhoog naar de volgende laag
            }
            int dz = layer % 2 == 0 ? 2 : -2;
            for (int row = 0; row < 11; row++) {
                if (row > 0) {
                    path.add(new BlockPos(x, y, z + dz / 2)); // naar de volgende rij
                    z += dz;
                }
                int toX = x == 1 ? 22 : 1;
                path.add(new BlockPos(x, y, z));
                while (x != toX) {
                    x += Integer.signum(toX - x);
                    path.add(new BlockPos(x, y, z));
                }
            }
        }
        return path;
    }

    /**
     * Hoofdstuk 124, jouw melding: "mijn poe kabel is connected en is heel lang hierdoor denk ik dat die niet verbind terwijl
     * die wel vast hangt". Een PoE-kabel van 758 stukjes (een slang door de arena) van een kern met stroom naar een camera
     * en een keycardlezer. Het netwerk vond ze al (de kern zoekt 8192 stukjes ver, hoofdstuk 94), maar de stroom zocht
     * vanaf het apparaat maar 512 stukjes ver: de camera stond in Devices met "no power". Nu hebben ze allebei stroom, en
     * zoekt de lezer het net niet opnieuw af (de camera deed dat al). Een Crusher aan dezelfde kabel krijgt nog steeds niets
     * (PoE voedt alleen netwerkblokken). Knip je de kabel door, dan is alles weg; leg je het stukje terug, dan is het er weer.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "lan")
    public static void a_long_poe_cable_brings_power_and_network(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(0, 2, 1), null);
        List<BlockPos> path = snake(3);
        for (BlockPos pos : path) {
            Cables.add(CableKind.POE, level, helper.absolutePos(pos));
        }
        BlockPos end = path.getLast();
        BlockPos camera = placeCamera(helper, end.east());
        BlockPos reader = end.west().above();
        helper.setBlock(reader, ModBlocks.KEYCARD_READER.get());
        BlockPos crusher = end.above();
        helper.setBlock(crusher, ModBlocks.CRUSHER.get());
        BlockPos cameraAbs = helper.absolutePos(camera);
        BlockPos readerAbs = helper.absolutePos(reader);
        helper.runAfterDelay(2, () -> {
            check(path.size() > PowerGrid.MAX_CABLES + 200, "De slang is te kort om iets te bewijzen: " + path.size() + " stukjes");
            check(path.getFirst().equals(new BlockPos(1, 2, 1)) && end.equals(new BlockPos(22, 6, 21)),
                    "De slang loopt anders dan gedacht: " + path.getFirst() + " tot " + end);
            // Elke plek van de slang raakt alleen zijn twee buren (anders is het een net met een kortere weg).
            java.util.Set<BlockPos> cells = new java.util.HashSet<>(path);
            for (int i = 0; i < path.size(); i++) {
                int touching = 0;
                for (Direction direction : Direction.values()) {
                    if (cells.contains(path.get(i).relative(direction))) {
                        touching++;
                    }
                }
                check(touching == (i == 0 || i == path.size() - 1 ? 1 : 2), "De slang raakt zichzelf bij " + path.get(i));
            }
            // De stroom: de camera vindt hem (het hele net één keer afgezocht), de lezer daarna zonder opnieuw te zoeken.
            PowerGrid.changed();
            int searches = PowerGrid.gridSearches();
            long started = System.nanoTime();
            int forCamera = PowerGrid.available(level, cameraAbs);
            long micros = (System.nanoTime() - started) / 1000;
            ImtTech.LOGGER.info("Hoofdstuk 124: stroom voor een camera aan {} stukjes PoE-kabel gezocht in {} microseconden",
                    path.size(), micros);
            check(forCamera > 0, "De camera aan " + path.size() + " stukjes PoE-kabel krijgt geen stroom");
            check(PowerGrid.gridSearches() == searches + 1,
                    "Voor de camera werd het hele net " + (PowerGrid.gridSearches() - searches) + " keer afgezocht");
            check(PowerGrid.available(level, readerAbs) > 0, "De lezer aan het eind van de PoE-kabel krijgt geen stroom");
            check(PowerGrid.gridSearches() == searches + 1, "De lezer zocht het hele net opnieuw af, terwijl de camera dat al deed");
            check(PowerGrid.available(level, helper.absolutePos(crusher)) == 0, "Een Crusher aan een lange PoE-kabel krijgt er stroom van");
            check(com.senne.imttech.lan.DevicePower.hasPower(level, readerAbs), "De lezer aan de lange PoE-kabel heeft geen stroom");
            // Het netwerk: de camera en de lezer horen bij de kern, en de camera heeft beeld.
            check(coreOf(helper, camera) == core, "De camera aan de lange PoE-kabel hangt niet aan de kern");
            check(coreOf(helper, reader) == core, "De lezer aan de lange PoE-kabel hangt niet aan de kern");
            CameraBlockEntity lens = (CameraBlockEntity) helper.getBlockEntity(camera);
            lens.updateOnline(level, cameraAbs, level.getBlockState(cameraAbs));
            check(level.getBlockState(cameraAbs).getValue(CameraBlock.ONLINE), "De camera aan de lange PoE-kabel heeft geen beeld");
            SecurityDevices.forget(level, core);
            check(statusOf(level, core, cameraAbs).equals("online"), "Devices zegt bij de camera: " + statusOf(level, core, cameraAbs));
            String readerStatus = statusOf(level, core, readerAbs);
            check(!readerStatus.isEmpty() && !readerStatus.equals("no power"), "Devices zegt bij de lezer: '" + readerStatus + "'");
            // Het signaal vindt zijn weg ook over de hele kabel (vroeger hooguit 512 stukjes: op een lange kabel liep er nooit
            // iets, en leek hij niet aangesloten).
            List<BlockPos> signal = LanGrid.path(level, readerAbs);
            check(signal.size() > LanGrid.MAX_NODES && signal.getFirst().equals(readerAbs)
                            && signal.getLast().equals(core.getBlockPos()),
                    "De weg van het signaal van de lezer naar de kern klopt niet (" + signal.size() + " plekken)");
            // Wat een speler ervan krijgt (zie CableTraffic.part): bij de lezer de eerste plekken (daar vertrekt het vakje), bij
            // de kern de laatste. Je spel tekent alleen een weg waarvan het midden dichtbij is; een korte weg helemaal.
            List<BlockPos> atReader = com.senne.imttech.lan.CableTraffic.part(signal, true, 48);
            List<BlockPos> atCore = com.senne.imttech.lan.CableTraffic.part(signal, false, 48);
            check(atReader.size() == 64 && atReader.getFirst().equals(readerAbs) && atCore.size() == 64
                            && atCore.getLast().equals(core.getBlockPos())
                            && com.senne.imttech.lan.CableTraffic.part(signal.subList(0, 10), true, 48).size() == 10,
                    "Het stuk van de weg per speler klopt niet (" + atReader.size() + " en " + atCore.size() + " plekken)");
            // De gele streepjes zoeken bewust maar 512 stukjes ver (de nalezing: anders zocht elk apparaat aan het eind van een
            // lange kabel om de twee seconden de hele kabel af): op deze kabel dus geen.
            check(PowerGrid.path(level, cameraAbs).isEmpty(), "De gele streepjes zochten verder dan 512 stukjes");
            // Doorknippen in het midden: geen stroom en geen netwerk meer.
            BlockPos middle = helper.absolutePos(path.get(path.size() / 2));
            Cables.remove(CableKind.POE, level, middle);
            check(PowerGrid.available(level, cameraAbs) == 0, "Een doorgeknipte PoE-kabel geeft nog stroom");
            check(coreOf(helper, camera) == null, "Een doorgeknipte PoE-kabel geeft nog netwerk");
            lens.updateOnline(level, cameraAbs, level.getBlockState(cameraAbs));
            check(!level.getBlockState(cameraAbs).getValue(CameraBlock.ONLINE), "De camera heeft nog beeld na het doorknippen");
            // Het stukje terug: alles weer.
            Cables.add(CableKind.POE, level, middle);
            check(PowerGrid.available(level, cameraAbs) > 0, "Met het stukje terug krijgt de camera geen stroom");
            check(coreOf(helper, camera) == core, "Met het stukje terug hangt de camera niet aan de kern");
            helper.succeed();
        });
    }

    /**
     * Hoofdstuk 124, gevonden door de nalezing: een Network Switch die bovenop een getekende PoE-kabel staat, zocht zijn eigen
     * stroom over het stukje kabel onder zich, en dat gaat alle kanten op, ook door een poort die uit staat. Dat antwoord
     * kwam in het gedeelde geheugen van het hele net, en dan kreeg een camera achter de dichte poort toch stroom. Nu niet:
     * de switch heeft zijn stroom (van de kant van de kern), de camera achter de poort die uit staat niet; zet je de poort
     * aan, dan wel. De switch staat 200 stukjes van de kern, zodat er achter hem nog meer dan 512 liggen (anders zoekt de
     * camera alles zelf af, en gebruikt hij het gedeelde geheugen niet).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "lan")
    public static void a_switch_on_a_long_cable_keeps_its_port_shut(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ImtTechGameTests.placeCore(helper, new BlockPos(0, 2, 1), null);
        List<BlockPos> path = snake(3);
        for (BlockPos pos : path) {
            Cables.add(CableKind.POE, level, helper.absolutePos(pos));
        }
        int at = 200;
        BlockPos sw = path.get(at);
        BlockPos swAbs = helper.absolutePos(sw);
        Direction toFar = Direction.fromDelta(path.get(at + 1).getX() - sw.getX(), path.get(at + 1).getY() - sw.getY(),
                path.get(at + 1).getZ() - sw.getZ());
        helper.setBlock(sw, ModBlocks.NETWORK_SWITCH.get()); // bovenop de kabel: die blijft eronder liggen
        check(Cables.has(CableKind.POE, level, swAbs), "De PoE-kabel onder de switch is weg");
        com.senne.imttech.lan.NetworkSwitches.setPort(level, swAbs, toFar, false);
        BlockPos cameraAbs = helper.absolutePos(placeCamera(helper, path.getLast().east()));
        check(path.size() - at - 1 > PowerGrid.MAX_CABLES, "Achter de switch ligt te weinig kabel om iets te bewijzen");
        PowerGrid.changed();
        check(PowerGrid.available(level, swAbs) > 0, "De switch krijgt zelf geen stroom van de kant van de kern");
        check(PowerGrid.available(level, cameraAbs) == 0,
                "Achter een poort die uit staat, kreeg de camera toch stroom (via het stukje kabel onder de switch)");
        com.senne.imttech.lan.NetworkSwitches.setPort(level, swAbs, toFar, true);
        check(PowerGrid.available(level, cameraAbs) > 0, "Met de poort aan krijgt de camera geen stroom");
        helper.succeed();
    }

    /**
     * Hoofdstuk 124: hetzelfde voor een gewone stroomkabel. Een Crusher aan het eind van een stroomkabel van 758 stukjes
     * krijgt de stroom van de generator aan het begin (vroeger zocht hij maar 512 stukjes ver); doorgeknipt niet meer.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "lan")
    public static void a_long_power_cable_brings_power(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        generator(helper, new BlockPos(0, 2, 1));
        List<BlockPos> path = snake(3);
        for (BlockPos pos : path) {
            Cables.add(CableKind.POWER, level, helper.absolutePos(pos));
        }
        BlockPos crusherAbs = helper.absolutePos(path.getLast().east());
        helper.setBlock(path.getLast().east(), ModBlocks.CRUSHER.get());
        check(PowerGrid.available(level, crusherAbs) > 0, "Een Crusher aan " + path.size() + " stukjes stroomkabel krijgt geen stroom");
        Cables.remove(CableKind.POWER, level, helper.absolutePos(path.get(path.size() / 2)));
        check(PowerGrid.available(level, crusherAbs) == 0, "Een doorgeknipte stroomkabel geeft nog stroom");
        helper.succeed();
    }

    /**
     * Hoofdstuk 124: een lange kabel leg je met veel rollen. Raakte je rol leeg terwijl je trok, dan liet Minecraft de knop
     * los zonder dat de rol het merkte: met een nieuwe rol begon de lijn dan bij de plek waar je kéék toen hij leeg raakte
     * (niet waar de kabel ophield), en legde hij een kabel van daar naar waar je nu kijkt, met een gat ertussen. Nu begint
     * elke keer dat je de knop indrukt een nieuwe lijn waar je kijkt, en kun je de kabel gewoon afmaken.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "lan")
    public static void an_empty_spool_leaves_no_stray_cable(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 4, 8), "lan-spool-empty");
        player.getAbilities().instabuild = false; // echt survival: elk stukje kost een kabel
        for (int x = 4; x <= 14; x++) {
            helper.setBlock(new BlockPos(x, 2, 8), Blocks.STONE);
        }
        CableSpoolItem spool = (CableSpoolItem) ModItems.NETWORK_CABLE.get();
        java.util.function.IntPredicate laid = x -> Cables.has(CableKind.NETWORK, level, helper.absolutePos(new BlockPos(x, 3, 8)));
        // Twee kabels: één op x = 6, dan kijk je naar x = 9: hij legt x = 7 en is dan leeg.
        ItemStack two = new ItemStack(ModItems.NETWORK_CABLE.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, two);
        spool.use(level, player, InteractionHand.MAIN_HAND);
        ZoneGameTests.lookDownAt(helper, player, new BlockPos(6, 2, 8));
        spool.onUseTick(level, player, two, 0);
        ZoneGameTests.lookDownAt(helper, player, new BlockPos(9, 2, 8));
        spool.onUseTick(level, player, two, 0);
        check(two.isEmpty() && laid.test(6) && laid.test(7) && !laid.test(8) && !laid.test(9),
                "De eerste rol legde niet precies x = 6 en 7 (nog " + two.getCount() + " over)");
        // Zoals in het spel: met een lege hand houdt het trekken op zonder releaseUsing. Een nieuwe rol, en je kijkt naar
        // x = 12: alleen daar komt een stukje, geen kabel van x = 9 naar x = 12.
        ItemStack more = new ItemStack(ModItems.NETWORK_CABLE.get(), 16);
        player.setItemInHand(InteractionHand.MAIN_HAND, more);
        spool.use(level, player, InteractionHand.MAIN_HAND);
        ZoneGameTests.lookDownAt(helper, player, new BlockPos(12, 2, 8));
        spool.onUseTick(level, player, more, 0);
        check(laid.test(12) && !laid.test(9) && !laid.test(10) && !laid.test(11),
                "De nieuwe rol legde een kabel vanaf een oude plek (9: " + laid.test(9) + ", 10: " + laid.test(10) + ", 11: "
                        + laid.test(11) + ")");
        // Dezelfde lijn terug naar x = 8: nu ligt de kabel van 6 tot 12 zonder gat, voor precies 5 kabels.
        ZoneGameTests.lookDownAt(helper, player, new BlockPos(8, 2, 8));
        spool.onUseTick(level, player, more, 0);
        spool.releaseUsing(more, level, player, 0);
        for (int x = 6; x <= 12; x++) {
            check(laid.test(x), "Er zit een gat in de kabel op x = " + x);
        }
        check(more.getCount() == 11, "De nieuwe rol gebruikte " + (16 - more.getCount()) + " kabels in plaats van 5");
        helper.succeed();
    }

    /**
     * Hoofdstuk 132: "crazy" kabels dwars door de muur, en je hele rol in één keer op. Hield je de knop vast terwijl je even
     * nergens naar keek (de lucht, of verder dan 6 blokken) en doorliep, of werd je geteleporteerd, dan trok de rol bij de
     * volgende plek waar je keek een rechte lijn van het eind van je kabel tot daar: eerst langs x, dan z, dan y, door alles
     * heen, voor een kabel per stukje. Nu begint hij een nieuwe lijn als dat eind meer dan 24 blokken ver ligt, en sluit hij
     * dichtbij nog gewoon aan (langs de vloer, zonder gat).
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "lan")
    public static void a_far_cable_end_starts_a_new_line(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(2, 4, 2), "lan-spool-far");
        player.getAbilities().instabuild = false; // echt survival: elk stukje kost een kabel
        // Een stenen vloer onder de kabel: bij het begin (x = 2 tot 5 op z = 2) en ver weg (x = 20, z = 17 tot 20).
        for (int x = 2; x <= 5; x++) {
            helper.setBlock(new BlockPos(x, 2, 2), Blocks.STONE);
        }
        for (int z = 17; z <= 20; z++) {
            helper.setBlock(new BlockPos(20, 2, z), Blocks.STONE);
        }
        java.util.function.BiPredicate<Integer, Integer> laid =
                (x, z) -> Cables.has(CableKind.POE, level, helper.absolutePos(new BlockPos(x, 3, z)));
        CableSpoolItem spool = (CableSpoolItem) ModItems.POE_CABLE.get();
        ItemStack cables = new ItemStack(ModItems.POE_CABLE.get(), 64);
        player.setItemInHand(InteractionHand.MAIN_HAND, cables);
        spool.use(level, player, InteractionHand.MAIN_HAND);
        // Dichtbij: een stukje op x = 2, dan kijk je naar x = 5: hij sluit aan langs de vloer (x = 3, 4 en 5).
        ZoneGameTests.lookDownAt(helper, player, new BlockPos(2, 2, 2));
        spool.onUseTick(level, player, cables, 0);
        ZoneGameTests.lookDownAt(helper, player, new BlockPos(5, 2, 2));
        spool.onUseTick(level, player, cables, 0);
        for (int x = 2; x <= 5; x++) {
            check(laid.test(x, 2), "Dichtbij sloot de kabel niet aan: een gat op x = " + x);
        }
        check(cables.getCount() == 60, "Het begin kostte " + (64 - cables.getCount()) + " kabels in plaats van 4");
        // Zonder de knop los te laten ver weg (33 blokken langs de assen, alsof je even in de lucht keek en doorliep):
        // alleen daar een stukje, geen lijn van x = 5 naar x = 20 en dan naar z = 20 (zo liep de rechte lijn).
        ZoneGameTests.lookDownAt(helper, player, new BlockPos(20, 2, 20));
        spool.onUseTick(level, player, cables, 0);
        check(laid.test(20, 20), "Ver weg legde de rol geen kabel waar je keek");
        for (int x = 6; x <= 20; x++) {
            check(!laid.test(x, 2), "De rol trok een lange kabel vanaf het oude eind (langs x, op x = " + x + ")");
        }
        for (int z = 3; z <= 19; z++) {
            check(!laid.test(20, z), "De rol trok een lange kabel vanaf het oude eind (langs z, op z = " + z + ")");
        }
        check(cables.getCount() == 59, "Ver weg kostte " + (60 - cables.getCount()) + " kabels in plaats van 1");
        // Vanaf de nieuwe lijn sluit hij weer gewoon aan: naar z = 17 legt hij z = 19, 18 en 17.
        ZoneGameTests.lookDownAt(helper, player, new BlockPos(20, 2, 17));
        spool.onUseTick(level, player, cables, 0);
        spool.releaseUsing(cables, level, player, 0);
        for (int z = 17; z <= 20; z++) {
            check(laid.test(20, z), "De nieuwe lijn sloot niet aan: een gat op z = " + z);
        }
        check(cables.getCount() == 56, "De nieuwe lijn kostte " + (59 - cables.getCount()) + " kabels in plaats van 3");
        // De route zelf: verder dan 24 blokken is er geen (null), dichtbij een aaneengesloten stuk.
        BlockPos start = helper.absolutePos(new BlockPos(2, 3, 2));
        check(com.senne.imttech.energy.PowerCables.route(level, start, helper.absolutePos(new BlockPos(20, 3, 20))) == null,
                "PowerCables.route gaf nog een route naar een plek 36 blokken verder");
        List<BlockPos> near = com.senne.imttech.energy.PowerCables.route(level, start, helper.absolutePos(new BlockPos(5, 3, 2)));
        check(near != null && near.size() == 4, "Dichtbij gaf PowerCables.route geen route van 4 plekken: " + near);
        helper.succeed();
    }

    /** Houdt een stuk wereld alleen geladen (niet draaiend), zoals NetworkKeeper dat met het net van een kern doet. */
    private static final net.minecraft.server.level.TicketType<net.minecraft.world.level.ChunkPos> STILL =
            net.minecraft.server.level.TicketType.create("imttech_test_still",
                    java.util.Comparator.comparingLong(net.minecraft.world.level.ChunkPos::toLong), 600);

    /**
     * Hoofdstuk 124: aan het eind van een lange kabel staat een apparaat vaak in een stuk wereld dat NetworkKeeper alleen
     * geladen houdt. Daar tikken geen blokken, dus een camera of switch werkte zijn stand nooit meer bij: een camera die één
     * keer "no power" had, bleef zo in Devices staan, en een switch zonder stroom liet het net erachter nooit meer door. Nu
     * doet de kern het om de twee seconden. In een stuk wereld 448 blokken hiervandaan, waar niets draait, zet hij eerst de
     * switch aan en de ronde daarna de camera erachter. In de arena (die wel draait) laat hij de camera met rust: die doet
     * het zelf.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "lan")
    public static void the_core_updates_devices_in_a_chunk_that_stands_still(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SecurityCoreBlockEntity nearCore = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), null);
        line(helper, CableKind.POE, new BlockPos(3, 3, 2), new BlockPos(5, 3, 2));
        BlockPos nearCamera = placeCamera(helper, new BlockPos(6, 3, 2));
        BlockPos nearCameraAbs = helper.absolutePos(nearCamera);
        // Ver weg: 448 blokken ten westen van deze test (de tests liggen in rijen naar het oosten, dus daar staat geen
        // arena): een kern met een generator, een PoE-kabel naar een switch en verder naar een camera, allebei nog uit.
        BlockPos here = helper.absolutePos(new BlockPos(2, 3, 2));
        net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos((here.getX() - 448) >> 4, here.getZ() >> 4);
        BlockPos corePos = new BlockPos(chunk.getMinBlockX() + 2, here.getY(), chunk.getMinBlockZ() + 8);
        BlockPos switchPos = corePos.east(4);
        BlockPos cameraPos = corePos.east(8);
        level.getChunkSource().addRegionTicket(STILL, chunk, 0, chunk);
        helper.startSequence()
                .thenWaitUntil(() -> check(level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null,
                        "Het stuk wereld ver weg laadt niet"))
                .thenExecute(() -> {
                    try {
                        // De arena draait: daar werkt de kern de camera niet bij (hij heeft wel stroom en netwerk). Zijn eigen
                        // tick kan hem intussen al aan gezet hebben: eerst weer uit, en dan in dezelfde tick kijken.
                        check(level.shouldTickBlocksAt(nearCameraAbs), "De arena draait niet: dan bewijst dit deel niets");
                        level.setBlock(nearCameraAbs, level.getBlockState(nearCameraAbs).setValue(CameraBlock.ONLINE, false),
                                net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                        com.senne.imttech.lan.DevicePower.refreshIdle(level, nearCore.getBlockPos());
                        check(!level.getBlockState(nearCameraAbs).getValue(CameraBlock.ONLINE),
                                "De kern werkte een camera bij die zelf tikt (dubbel)");
                        ((CameraBlockEntity) helper.getBlockEntity(nearCamera))
                                .updateOnline(level, nearCameraAbs, level.getBlockState(nearCameraAbs));
                        check(level.getBlockState(nearCameraAbs).getValue(CameraBlock.ONLINE),
                                "De camera in de arena heeft geen stroom of netwerk");
                        // Ver weg, waar niets draait.
                        check(!level.shouldTickBlocksAt(cameraPos), "Het stuk wereld ver weg draait toch: dan bewijst deze test niets");
                        level.setBlockAndUpdate(corePos.below(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                        ((ChargerBlockEntity) level.getBlockEntity(corePos.below())).fill(20_000);
                        level.setBlockAndUpdate(corePos, ModBlocks.SECURITY_CORE.get().defaultBlockState());
                        SecurityCoreBlockEntity core = (SecurityCoreBlockEntity) level.getBlockEntity(corePos);
                        for (int i = 1; i <= 7; i++) {
                            if (i != 4) {
                                Cables.add(CableKind.POE, level, corePos.east(i));
                            }
                        }
                        level.setBlockAndUpdate(switchPos, ModBlocks.NETWORK_SWITCH.get().defaultBlockState());
                        level.setBlockAndUpdate(cameraPos.below(), Blocks.STONE.defaultBlockState());
                        level.setBlockAndUpdate(cameraPos, ModBlocks.SECURITY_CAMERA.get().defaultBlockState()
                                .setValue(CameraBlock.FACE, AttachFace.FLOOR).setValue(CameraBlock.FACING, Direction.SOUTH));
                        check(!level.getBlockState(switchPos).getValue(com.senne.imttech.lan.NetworkSwitchBlock.ONLINE)
                                && !level.getBlockState(cameraPos).getValue(CameraBlock.ONLINE), "De switch of de camera stond al aan");
                        // Eerste ronde van de kern: de switch krijgt stroom (de camera erachter hangt dan nog niet aan het net).
                        core.powerTick(level);
                        check(level.getBlockState(switchPos).getValue(com.senne.imttech.lan.NetworkSwitchBlock.ONLINE),
                                "De kern zette de switch in het stilstaande stuk wereld niet aan");
                        // De ronde daarna: ook de camera achter de switch.
                        core.powerTick(level);
                        check(level.getBlockState(cameraPos).getValue(CameraBlock.ONLINE),
                                "De kern zette de camera in het stilstaande stuk wereld niet aan");
                        SecurityDevices.forget(level, core);
                        check(statusOf(level, core, cameraPos).equals("online"),
                                "Devices zegt bij de camera ver weg: '" + statusOf(level, core, cameraPos) + "'");
                        // De kern zet een apparaat ver weg alleen aan, nooit uit (de nalezing: daar ziet hij een batterij in het
                        // stuk ernaast niet). Zonder generator blijven de switch en de camera dus aan tot hun eigen tick.
                        level.setBlockAndUpdate(corePos.below(), Blocks.AIR.defaultBlockState());
                        core.powerTick(level);
                        check(level.getBlockState(switchPos).getValue(com.senne.imttech.lan.NetworkSwitchBlock.ONLINE)
                                        && level.getBlockState(cameraPos).getValue(CameraBlock.ONLINE),
                                "De kern zette een apparaat in het stilstaande stuk wereld uit");
                        // De kern vergeet de verre stukken van zijn net niet als zijn zoektocht niet alles zag: een netwerkkabel
                        // naar de rand van zijn stuk wereld, waarachter een stuk ligt dat niet geladen is.
                        long known = net.minecraft.world.level.ChunkPos.asLong(chunk.x + 40, chunk.z);
                        core.rememberNetwork(new long[] {chunk.toLong(), known}, false);
                        for (int x = corePos.getX() - 1; x >= chunk.getMinBlockX(); x--) {
                            Cables.add(CableKind.NETWORK, level, new BlockPos(x, corePos.getY(), corePos.getZ()));
                        }
                        check(!level.isLoaded(new BlockPos(chunk.getMinBlockX() - 1, corePos.getY(), corePos.getZ())),
                                "Het stuk wereld naast het verre stuk is toch geladen: dan bewijst dit deel niets");
                        LanGrid.changed();
                        LanGrid.devicesOf(level, corePos);
                        check(java.util.Arrays.stream(core.networkChunks()).anyMatch(c -> c == known),
                                "Na een zoektocht die niet alles zag, vergat de kern een stuk van zijn net");
                        // Zonder die kabel ziet de zoektocht alles: dan klopt de lijst precies, en valt het oude stuk eruit.
                        for (int x = corePos.getX() - 1; x >= chunk.getMinBlockX(); x--) {
                            Cables.remove(CableKind.NETWORK, level, new BlockPos(x, corePos.getY(), corePos.getZ()));
                        }
                        LanGrid.changed();
                        LanGrid.devicesOf(level, corePos);
                        check(java.util.Arrays.stream(core.networkChunks()).noneMatch(c -> c == known),
                                "Na een zoektocht die alles zag, bleef een stuk staan waar het net niet meer komt");
                    } finally {
                        for (int x = corePos.getX() - 1; x >= chunk.getMinBlockX(); x--) {
                            Cables.remove(CableKind.NETWORK, level, new BlockPos(x, corePos.getY(), corePos.getZ()));
                        }
                        for (int i = 1; i <= 7; i++) {
                            Cables.remove(CableKind.POE, level, corePos.east(i));
                        }
                        for (BlockPos pos : List.of(cameraPos, cameraPos.below(), switchPos, corePos, corePos.below())) {
                            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                        }
                        level.getChunkSource().removeRegionTicket(STILL, chunk, 0, chunk);
                    }
                })
                .thenSucceed();
    }
}
