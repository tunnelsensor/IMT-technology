package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.energy.CableGeometry;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.energy.CableSpoolItem;
import com.senne.imttech.energy.Cables;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.lan.LanGrid;
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
        helper.setBlock(emitter.west(), Blocks.REDSTONE_BLOCK);
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
}
