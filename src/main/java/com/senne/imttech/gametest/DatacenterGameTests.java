package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.logic.Backups;
import com.senne.imttech.logic.Datacenter;
import com.senne.imttech.logic.DatacenterBrain;
import com.senne.imttech.logic.Datacenters;
import com.senne.imttech.logic.LogicServer;
import com.senne.imttech.logic.LogicStats;
import com.senne.imttech.logic.RackBay;
import com.senne.imttech.logic.ServerPartItem;
import com.senne.imttech.logic.ServerRackBlock;
import com.senne.imttech.logic.ServerRackBlockEntity;
import com.senne.imttech.logic.ServerRacks;
import com.senne.imttech.network.RackRenamePayload;
import com.senne.imttech.logic.program.GraphConverter;
import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.EventLog;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor het datacenter (hoofdstuk 9 van het plan, 8 oktober): de GPU en de fan, warmte (elk rack zijn eigen
 * temperatuur), slijten en brand, het gebruik, backups en het brein van I.M.T. Sinds hoofdstuk 64 (8 november): acht
 * vakjes waar je de onderdelen in klikt, de rechte lijn naar 30 en 120 CPU's, RAM en SSD per 10 CPU's of GPU's, en de
 * naam van een rack.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class DatacenterGameTests {
    private DatacenterGameTests() {}

    private static final String ARENA = "empty";

    /** Een kern met een eigenaar en één rack met een CPU, RAM en SSD (zie ImtTechGameTests.placeServer). */
    record Setup(ServerPlayer owner, SecurityCoreBlockEntity core, BlockPos rack) {
        ServerRackBlockEntity rackEntity(GameTestHelper helper) {
            return (ServerRackBlockEntity) helper.getBlockEntity(rack);
        }
    }

    static Setup datacenter(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 3, 5));
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos rack = new BlockPos(6, 3, 6);
        ImtTechGameTests.placeServer(helper, rack, core);
        return new Setup(owner, core, rack);
    }

    /**
     * Hoofdstuk 64, de rechte lijn met jouw getallen: diagrammen 1 s met 25 CPU's en meteen vanaf 30 CPU's (of 60 GPU's),
     * I.M.T 2 s met 100 CPU's en meteen vanaf 120 CPU's (of 60 GPU's); met één CPU 5,8 en 11,9 seconden. Een GPU telt voor
     * de diagrammen half en voor I.M.T dubbel; een CPU en een GPU samen zijn 1,5 en 3. Zonder CPU of GPU draait de server
     * niet, en zegt hij waarom.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "datacenter")
    public static void datacenter_straight_line_to_30_and_120(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = datacenter(helper);
        ServerRackBlockEntity rack = setup.rackEntity(helper);
        check(rack.parts().getContainerSize() == 8, "Een rack zou 8 vakjes moeten hebben, niet " + rack.parts().getContainerSize());
        // De CPU eruit, een GPU erin, en een fan.
        rack.parts().setItem(0, ItemStack.EMPTY);
        rack.insert(new ItemStack(ModItems.SERVER_GPU.get()));
        rack.insert(new ItemStack(ModItems.SERVER_FAN.get()));
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), setup.core());
            // De echte schaal (in de tests wachten diagrammen anders hooguit 10 ticks), alleen in dit stukje van één tick.
            LogicStats.overrideRealScale(true);
            try {
                check(LogicStats.reactionTicks(25) == 20 && LogicStats.reactionTicks(30) == 1 && LogicStats.reactionTicks(60 * 0.5) == 1
                                && LogicStats.reactionTicks(1) == 116 && LogicStats.reactionTicks(0) == Integer.MAX_VALUE,
                        "Diagrammen: 25 CPU's 20 ticks, 30 CPU's of 60 GPU's 1 tick, 1 CPU 116 ticks; niet " + LogicStats.reactionTicks(25)
                                + " / " + LogicStats.reactionTicks(30) + " / " + LogicStats.reactionTicks(1));
                check(LogicStats.brainDelayTicks(100) == 40 && LogicStats.brainDelayTicks(120) == 0 && LogicStats.brainDelayTicks(60 * 2.0) == 0
                                && LogicStats.brainDelayTicks(1) == 238 && LogicStats.brainDelayTicks(0) == -1,
                        "I.M.T: 100 CPU's 40 ticks, 120 CPU's of 60 GPU's 0, 1 CPU 238, niets -1; niet " + LogicStats.brainDelayTicks(100)
                                + " / " + LogicStats.brainDelayTicks(120) + " / " + LogicStats.brainDelayTicks(1));
                LogicServer.Stats gpu = LogicServer.stats(level, setup.core());
                check(gpu.online() && gpu.cpus() == 0 && gpu.gpus() == 1 && gpu.fans() == 1,
                        "De server met een GPU telt verkeerd: " + gpu + " (" + gpu.problem() + ")");
                check(Math.abs(gpu.logicPower() - 0.5) < 1.0e-9 && Math.abs(gpu.brainPower() - 2.0) < 1.0e-9,
                        "Een GPU zou 0,5 Logic en 2 brein moeten zijn: " + gpu.logicPower() + " / " + gpu.brainPower());
                check(gpu.reactionTicks() == LogicStats.reactionTicks(0.5) && gpu.reactionTicks() > LogicStats.reactionTicks(1.0)
                                && gpu.brainDelayTicks() == LogicStats.brainDelayTicks(2.0) && gpu.brainDelayTicks() < LogicStats.brainDelayTicks(1.0),
                        "Een GPU zou trager moeten reageren en sneller denken dan een CPU: " + gpu.reactionTicks() + " / " + gpu.brainDelayTicks());
            } finally {
                LogicStats.overrideRealScale(null);
            }
            // Een CPU erbij: samen 1,5 voor Logic en 3 voor het brein, maal wat het ene latje RAM van twee chips kan voeden.
            rack.insert(new ItemStack(ModItems.SERVER_CPU.get()));
            LogicServer.Stats both = LogicServer.stats(level, setup.core());
            double fedBoth = LogicStats.fed(2, 1);
            check(Math.abs(both.logicPower() - 1.5 * fedBoth) < 1.0e-9 && Math.abs(both.brainPower() - 3.0 * fedBoth) < 1.0e-9,
                    "CPU + GPU zou 1.5 Logic en 3 brein moeten zijn (maal " + fedBoth + " voor het RAM): " + both.logicPower()
                            + " / " + both.brainPower());
            // In de automatische tests wacht een diagram hooguit TEST_LOGIC_MAX_DELAY_TICKS (zie LogicStats.logicMaxDelayTicks).
            check(both.reactionTicks() <= LogicStats.TEST_LOGIC_MAX_DELAY_TICKS, "In de tests zou een server snel moeten reageren: "
                    + both.reactionTicks());
            // Geen CPU en geen GPU: de server zegt wat er mist.
            rack.parts().setItem(0, ItemStack.EMPTY);
            rack.parts().setItem(1, ItemStack.EMPTY);
            LogicServer.Stats none = LogicServer.stats(level, setup.core());
            check(!none.online() && "The server needs a CPU or GPU".equals(none.problem()),
                    "Zonder CPU of GPU zou de server dat moeten zeggen, niet: " + none.problem());
            helper.succeed();
        });
    }

    /**
     * Hoofdstuk 64: "voor elke 10 cpu's of gpu's ... ramsticks", in MB, GB en TB. De maten staan in de config (een latje van
     * ramStickGb, ramGbPerTen per 10 CPU's of GPU's), dus de test rekent daarmee: met de helft van het RAM draait maar de
     * helft van de CPU's mee. In de wereld: twee CPU's met één latje (standaard 8 GB van de 12,8 GB die ze nodig hebben),
     * een tweede latje erbij (genoeg). Een SSD is alleen opslag ("zorg ook dat de ssd geen invloed heeft op de speed"): een
     * SSD erbij maakt niets sneller. De oude grenzen (2 diagrammen per latje, 24 blokjes per SSD) zijn weg.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "datacenter")
    public static void datacenter_ram_and_ssd_feed_the_processors(GameTestHelper helper) {
        // De rekenregel: de helft van het RAM = de helft van de CPU's; genoeg latjes = alles; geen CPU's = niets te voeden.
        int enough = (int) Math.ceil(LogicStats.ramNeededMb(10) / LogicStats.ramStickMb());
        check(LogicStats.fed(10, enough) == 1.0 && LogicStats.fed(0, 0) == 1.0
                        && Math.abs(LogicStats.fed(20, 1) - LogicStats.fed(10, 1) / 2.0) < 1.0e-9,
                "Met genoeg latjes zou alles moeten draaien, en met twee keer zoveel CPU's per latje de helft");
        // MB, GB en TB (1000 MB = 1 GB, zoals op een doos).
        check("16 GB".equals(LogicStats.bytes(16_000)) && "1 TB".equals(LogicStats.bytes(1_000_000))
                        && "200 GB".equals(LogicStats.bytes(200_000)) && "76.8 GB".equals(LogicStats.bytes(76_800))
                        && "1.5 TB".equals(LogicStats.bytes(1_500_000)) && "512 MB".equals(LogicStats.bytes(512)),
                "MB, GB en TB kloppen niet: " + LogicStats.bytes(76_800));
        // De oude grenzen zijn weg: een latje houdt minstens honderd bezige diagrammen, een SSD duizenden blokjes.
        check(LogicStats.maxRuns(1) >= 100 && LogicStats.blockCapacity(1) >= 10_000,
                "Een latje zou minstens 100 diagrammen en een SSD minstens 10 000 blokjes moeten houden: " + LogicStats.maxRuns(1)
                        + " / " + LogicStats.blockCapacity(1));
        ServerLevel level = helper.getLevel();
        Setup setup = datacenter(helper);
        SecurityCoreBlockEntity core = setup.core();
        ServerRackBlockEntity a = setup.rackEntity(helper);
        a.insert(new ItemStack(ModItems.SERVER_CPU.get()));
        a.insert(new ItemStack(ModItems.SERVER_FAN.get()));
        BlockPos bPos = setup.rack().east();
        helper.setBlock(bPos, ModBlocks.SERVER_RACK.get());
        ServerRackBlockEntity b = (ServerRackBlockEntity) helper.getBlockEntity(bPos);
        b.insert(new ItemStack(ModItems.SERVER_FAN.get()));
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), core);
            ImtTechGameTests.startServer(helper, bPos, core);
            // Twee CPU's en één latje.
            LogicServer.Stats one = LogicServer.stats(level, core);
            double expected = Math.min(1.0, LogicStats.ramStickMb() / LogicStats.ramNeededMb(2));
            check(one.cpus() == 2 && one.ram() == 1 && one.ssd() == 1 && Math.abs(one.fed() - expected) < 1.0e-9
                            && Math.abs(one.logicPower() - 2.0 * expected) < 1.0e-9,
                    "Twee CPU's met één latje zouden voor " + Math.round(expected * 100) + "% moeten draaien: " + one + ", " + one.fed());
            // Een tweede latje.
            b.insert(new ItemStack(ModItems.SERVER_RAM.get()));
            LogicServer.Stats two = LogicServer.stats(level, core);
            double expectedTwo = Math.min(1.0, 2.0 * LogicStats.ramStickMb() / LogicStats.ramNeededMb(2));
            check(Math.abs(two.fed() - expectedTwo) < 1.0e-9 && (expected >= 1.0 || two.logicPower() > one.logicPower()),
                    "Met een tweede latje zou het voor " + Math.round(expectedTwo * 100) + "% moeten draaien: " + two.fed());
            // Een SSD erbij: alleen opslag, niets sneller.
            b.insert(new ItemStack(ModItems.SERVER_SSD.get()));
            LogicServer.Stats more = LogicServer.stats(level, core);
            check(more.ssd() == 2 && Math.abs(more.logicPower() - two.logicPower()) < 1.0e-9 && more.reactionTicks() == two.reactionTicks(),
                    "Een SSD erbij zou niets sneller mogen maken: " + more.logicPower() + " / " + two.logicPower());
            CompoundTag snapshot = LogicServer.snapshot(level, core);
            check(snapshot.getDouble("ramMb") == 2 * LogicStats.ramStickMb() && snapshot.getDouble("ramNeedMb") == LogicStats.ramNeededMb(2)
                            && snapshot.getDouble("ssdMb") == 2 * LogicStats.ssdMb() && !snapshot.contains("ssdNeedMb"),
                    "De app Monitor krijgt het geheugen verkeerd: " + snapshot.getDouble("ramMb") + " / " + snapshot.getDouble("ramNeedMb"));
            helper.succeed();
        });
    }

    /**
     * Jouw vraag "kun je de config value eens nakijken en kijken of dat in de tooltips ook klopt": elke tooltip van een
     * onderdeel toont de getallen uit de config. Een RAM-latje: hoeveel GB erop zit en hoeveel elke 10 CPU's of GPU's nodig
     * hebben; een SSD: hoeveel erop past en hoe groot een blokje is; een CPU en een GPU: hun kracht, en vanaf hoeveel het
     * meteen gaat; en de warmte en koeling. Verander je een getal (zoals in het configbestand), dan verandert de tooltip mee.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "datacenter")
    public static void datacenter_part_tooltips_follow_the_config(GameTestHelper helper) {
        checkTooltips(helper);
        float stick = ImtConfig.Logic.RAM_STICK_GB;
        float perTen = ImtConfig.Logic.RAM_GB_PER_TEN;
        float ssd = ImtConfig.Logic.SSD_GB;
        float block = ImtConfig.Logic.BLOCK_MB;
        float logicInstant = ImtConfig.Logic.LOGIC_INSTANT_POWER;
        double fan = ImtConfig.Logic.FAN_COOLING;
        try {
            // Andere getallen, zoals in het configbestand: de tooltips volgen.
            ImtConfig.Logic.RAM_STICK_GB = 32.0f;
            ImtConfig.Logic.RAM_GB_PER_TEN = 48.0f;
            ImtConfig.Logic.SSD_GB = 4000.0f;
            ImtConfig.Logic.BLOCK_MB = 0.5f;
            ImtConfig.Logic.LOGIC_INSTANT_POWER = 45.0f;
            ImtConfig.Logic.FAN_COOLING = 7.5;
            checkTooltips(helper);
            check(tooltipArgs(helper, ModItems.SERVER_RAM.get(), 0).equals(List.of("32 GB", "48 GB"))
                            && tooltipArgs(helper, ModItems.SERVER_SSD.get(), 0).equals(List.of("4 TB", "0.5 MB"))
                            && tooltipArgs(helper, ModItems.SERVER_CPU.get(), 0).get(1).equals("45")
                            && tooltipArgs(helper, ModItems.SERVER_FAN.get(), 1).equals(List.of("7.5")),
                    "De tooltips zouden de nieuwe getallen moeten tonen: " + tooltipArgs(helper, ModItems.SERVER_RAM.get(), 0)
                            + " " + tooltipArgs(helper, ModItems.SERVER_SSD.get(), 0));
        } finally {
            ImtConfig.Logic.RAM_STICK_GB = stick;
            ImtConfig.Logic.RAM_GB_PER_TEN = perTen;
            ImtConfig.Logic.SSD_GB = ssd;
            ImtConfig.Logic.BLOCK_MB = block;
            ImtConfig.Logic.LOGIC_INSTANT_POWER = logicInstant;
            ImtConfig.Logic.FAN_COOLING = fan;
        }
        helper.succeed();
    }

    /** Kloppen de tooltips van alle onderdelen met de getallen die nu in de config staan? */
    private static void checkTooltips(GameTestHelper helper) {
        String gpuLogic = plain(ImtConfig.Logic.GPU_LOGIC_POWER);
        String gpuBrain = plain(ImtConfig.Logic.GPU_BRAIN_POWER);
        String instant = plain(ImtConfig.Logic.LOGIC_INSTANT_POWER);
        String brainInstant = plain(ImtConfig.Logic.BRAIN_INSTANT_POWER);
        check(tooltipArgs(helper, ModItems.SERVER_RAM.get(), 0).equals(List.of(LogicStats.bytes(LogicStats.ramStickMb()),
                        LogicStats.bytes(LogicStats.ramNeededMb(10)))),
                "De tooltip van het RAM klopt niet met de config: " + tooltipArgs(helper, ModItems.SERVER_RAM.get(), 0));
        check(tooltipArgs(helper, ModItems.SERVER_SSD.get(), 0).equals(List.of(LogicStats.bytes(LogicStats.ssdMb()),
                        LogicStats.bytes(LogicStats.blocksMb(1)))),
                "De tooltip van de SSD klopt niet met de config: " + tooltipArgs(helper, ModItems.SERVER_SSD.get(), 0));
        check(tooltipArgs(helper, ModItems.SERVER_CPU.get(), 0).equals(List.of("1", instant, "1", brainInstant)),
                "De tooltip van de CPU klopt niet met de config: " + tooltipArgs(helper, ModItems.SERVER_CPU.get(), 0));
        check(tooltipArgs(helper, ModItems.SERVER_GPU.get(), 0).equals(List.of(gpuLogic, instant, gpuBrain, brainInstant)),
                "De tooltip van de GPU klopt niet met de config: " + tooltipArgs(helper, ModItems.SERVER_GPU.get(), 0));
        check(tooltipArgs(helper, ModItems.SERVER_CPU.get(), 1).equals(List.of(plain(ImtConfig.Logic.CPU_HEAT),
                        plain(ImtConfig.Logic.CPU_HEAT + ImtConfig.Logic.CPU_BUSY_HEAT)))
                        && tooltipArgs(helper, ModItems.SERVER_GPU.get(), 1).equals(List.of(plain(ImtConfig.Logic.GPU_HEAT),
                        plain(ImtConfig.Logic.GPU_HEAT + ImtConfig.Logic.GPU_BUSY_HEAT)))
                        && tooltipArgs(helper, ModItems.SERVER_RAM.get(), 1).equals(List.of(plain(ImtConfig.Logic.MEMORY_HEAT)))
                        && tooltipArgs(helper, ModItems.SERVER_FAN.get(), 1).equals(List.of(plain(ImtConfig.Logic.FAN_COOLING))),
                "De warmte of koeling in de tooltips klopt niet met de config");
    }

    /** De getallen (de %s) in regel {@code line} van de tooltip van dit onderdeel, als tekst. */
    private static List<String> tooltipArgs(GameTestHelper helper, net.minecraft.world.item.Item item, int line) {
        List<net.minecraft.network.chat.Component> tooltip = new java.util.ArrayList<>();
        ItemStack stack = new ItemStack(item);
        item.appendHoverText(stack, net.minecraft.world.item.Item.TooltipContext.of(helper.getLevel()), tooltip,
                net.minecraft.world.item.TooltipFlag.NORMAL);
        if (line >= tooltip.size()
                || !(tooltip.get(line).getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text)) {
            return List.of();
        }
        List<String> args = new java.util.ArrayList<>();
        for (Object arg : text.getArgs()) {
            args.add(arg instanceof net.minecraft.network.chat.Component component ? component.getString() : String.valueOf(arg));
        }
        return args;
    }

    /** Zoals ServerPartItem een getal laat zien: 1 blijft "1", 0.5 wordt "0.5", 7.5 blijft "7.5". */
    private static String plain(double value) {
        double rounded = Math.round(value * 100) / 100.0;
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }

    /**
     * Hoofdstuk 64: geen scherm meer, je klikt de onderdelen in de vakjes op de voorkant (zoals bij de boekenkast). Een CPU
     * past niet in een vakje voor RAM (hij blijft in je hand), wel bovenaan; RAM in de middelste rij, een fan alleen onderaan;
     * de blokstaat toont wat erin zit. Een klik met een lege hand op een vol vakje geeft het onderdeel terug. Een naam
     * (sneak + rechtsklik): de eigenaar van de basis mag, een vreemde niet; de naam blijft na opslaan en laden, en staat in de
     * momentopname voor de app Monitor.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "datacenter")
    public static void datacenter_click_parts_into_bays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 3, 5));
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 3, 8));
        BlockPos rackPos = new BlockPos(6, 3, 6);
        helper.setBlock(rackPos, ModBlocks.SERVER_RACK.get());
        ImtTechGameTests.powerTurret(helper, rackPos.below());
        helper.runAfterDelay(2, () -> {
            ServerRackBlockEntity rack = (ServerRackBlockEntity) helper.getBlockEntity(rackPos);
            owner.getAbilities().instabuild = false; // een testspeler heeft anders "oneindig materiaal", zoals in creative
            owner.getInventory().clearContent();
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SERVER_CPU.get(), 2));
            clickBay(helper, owner, rackPos, 2);
            check(rack.parts().getItem(2).isEmpty() && owner.getMainHandItem().getCount() == 2,
                    "Een CPU zou niet in een vakje voor RAM mogen");
            clickBay(helper, owner, rackPos, 0);
            check(rack.count(ServerPartItem.Kind.CPU) == 1 && owner.getMainHandItem().getCount() == 1
                    && bayOf(helper, rackPos, 0) == RackBay.CPU, "De CPU zou in het vakje linksboven moeten zitten (en te zien zijn)");
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SERVER_RAM.get()));
            clickBay(helper, owner, rackPos, 3);
            check(bayOf(helper, rackPos, 3) == RackBay.RAM && owner.getMainHandItem().isEmpty(), "Het RAM zou in het middelste vakje rechts moeten zitten");
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SERVER_FAN.get()));
            clickBay(helper, owner, rackPos, 1);
            check(bayOf(helper, rackPos, 1) == RackBay.NONE, "Een fan zou niet in een vakje voor een CPU mogen");
            clickBay(helper, owner, rackPos, 7);
            check(bayOf(helper, rackPos, 7) == RackBay.FAN, "De fan zou in het vakje rechtsonder moeten zitten");
            // Een lege hand op het volle vakje: de CPU komt terug in je rugzak.
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            int before = owner.getInventory().countItem(ModItems.SERVER_CPU.get());
            clickBay(helper, owner, rackPos, 0);
            check(bayOf(helper, rackPos, 0) == RackBay.NONE && rack.count(ServerPartItem.Kind.CPU) == 0
                    && owner.getInventory().countItem(ModItems.SERVER_CPU.get()) == before + 1, "De CPU zou terug in de rugzak moeten zitten");
            // De naam: een vreemde mag niet, de eigenaar van de basis wel.
            BlockPos abs = helper.absolutePos(rackPos);
            ServerRacks.handleRename(stranger, new RackRenamePayload(abs, "mine"));
            check(rack.name().isEmpty(), "Een vreemde mocht het rack hernoemen");
            ServerRacks.handleRename(owner, new RackRenamePayload(abs, "rack1"));
            check("rack1".equals(rack.name()), "De eigenaar van de basis zou het rack moeten kunnen hernoemen: " + rack.name());
            ServerRackBlockEntity loaded = new ServerRackBlockEntity(rack.getBlockPos(), rack.getBlockState());
            loaded.loadWithComponents(rack.saveWithoutMetadata(level.registryAccess()), level.registryAccess());
            check("rack1".equals(loaded.name()) && loaded.parts().getItem(3).is(ModItems.SERVER_RAM.get())
                    && loaded.parts().getItem(7).is(ModItems.SERVER_FAN.get()), "Na opslaan en laden zijn de naam of de vakjes anders");
            // In de app Monitor: het rack met zijn naam (en in welk vakje elk onderdeel zit).
            ImtTechGameTests.startServer(helper, rackPos, core);
            net.minecraft.nbt.ListTag racks = LogicServer.snapshot(level, core).getList("racksInfo", 10);
            check(racks.size() == 1 && "rack1".equals(racks.getCompound(0).getString("name"))
                    && racks.getCompound(0).getList("parts", 10).getCompound(0).getInt("s") == 3,
                    "De app Monitor krijgt het rack niet met zijn naam: " + racks);
            helper.succeed();
        });
    }

    /**
     * Een rack van voor 8 november (zes vakjes, alles mocht overal) wordt bij het laden netjes ingedeeld: de GPU en een CPU
     * bovenaan, RAM en SSD in het midden, de fan onderaan; wat niet meer past (een tweede CPU, er zijn maar twee vakjes voor
     * CPU's en GPU's) valt eruit.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "datacenter")
    public static void datacenter_old_rack_is_sorted_into_bays(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rackPos = new BlockPos(6, 3, 6);
        helper.setBlock(rackPos, ModBlocks.SERVER_RACK.get());
        ServerRackBlockEntity rack = (ServerRackBlockEntity) helper.getBlockEntity(rackPos);
        net.minecraft.core.NonNullList<ItemStack> old = net.minecraft.core.NonNullList.withSize(6, ItemStack.EMPTY);
        old.set(0, new ItemStack(ModItems.SERVER_RAM.get()));
        old.set(1, new ItemStack(ModItems.SERVER_CPU.get()));
        old.set(2, new ItemStack(ModItems.SERVER_FAN.get()));
        old.set(3, new ItemStack(ModItems.SERVER_GPU.get()));
        old.set(4, new ItemStack(ModItems.SERVER_SSD.get()));
        old.set(5, new ItemStack(ModItems.SERVER_CPU.get()));
        CompoundTag tag = new CompoundTag();
        net.minecraft.world.ContainerHelper.saveAllItems(tag, old, level.registryAccess());
        tag.putString("Status", "NO_NETWORK");
        rack.loadWithComponents(tag, level.registryAccess());
        helper.runAfterDelay(3, () -> {
            check(rack.count(ServerPartItem.Kind.CPU) == 1 && rack.count(ServerPartItem.Kind.GPU) == 1
                    && rack.count(ServerPartItem.Kind.RAM) == 1 && rack.count(ServerPartItem.Kind.SSD) == 1
                    && rack.count(ServerPartItem.Kind.FAN) == 1, "Het oude rack is verkeerd ingedeeld");
            for (int slot = 0; slot < rack.parts().getContainerSize(); slot++) {
                ItemStack stack = rack.parts().getItem(slot);
                check(stack.isEmpty() || stack.getItem() instanceof ServerPartItem part && RackBay.accepts(slot, part.kind()),
                        "Vakje " + slot + " heeft " + stack);
            }
            check(bayOf(helper, rackPos, 0) != RackBay.NONE && bayOf(helper, rackPos, 4) == RackBay.FAN,
                    "De voorkant laat de onderdelen niet zien");
            List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(rackPos)).inflate(3),
                    item -> item.getItem().is(ModItems.SERVER_CPU.get()));
            check(dropped.size() == 1, "De CPU die niet meer past, zou eruit moeten vallen, niet " + dropped.size());
            helper.succeed();
        });
    }

    /** Een rechtsklik op het vakje {@code slot} van een rack dat naar het noorden kijkt, zoals Minecraft het doet. */
    static void clickBay(GameTestHelper helper, ServerPlayer player, BlockPos rackPos, int slot) {
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(rackPos);
        BlockState state = level.getBlockState(abs);
        double across = (slot % 2 == 0 ? 5.0 : 11.0) / 16.0;
        int[] pixels = RackBay.ROW_PIXELS[RackBay.row(slot)];
        double fromTop = (pixels[0] + pixels[1]) / 2.0 / 16.0;
        // Kijkt hij naar het noorden, dan is links (zoals je ervoor staat) x = 1.
        Vec3 at = new Vec3(abs.getX() + 1.0 - across, abs.getY() + 1.0 - fromTop, abs.getZ());
        BlockHitResult hit = new BlockHitResult(at, Direction.NORTH, abs, false);
        ItemInteractionResult result = state.useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND, hit);
        if (result == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            state.useWithoutItem(level, player, hit);
        }
    }

    /** Wat er volgens de blokstaat in dit vakje van het rack zit. */
    static RackBay bayOf(GameTestHelper helper, BlockPos rackPos, int slot) {
        return ServerRackBlock.bay(helper.getBlockState(rackPos), slot);
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Laat de temperatuur zoveel stappen (van twee seconden) opschuiven, telkens met verse stats. */
    static void settle(ServerLevel level, SecurityCoreBlockEntity core, int steps) {
        for (int i = 0; i < steps; i++) {
            LogicServer.forgetAll();
            core.datacenter().update(level, core);
        }
        LogicServer.forgetAll();
    }

    /** Staat er een regel van deze soort met deze tekst in het logboek van de kern? */
    static boolean logged(SecurityCoreBlockEntity core, EventLog.Type type, String text) {
        return core.log().entries().stream().anyMatch(entry -> entry.type() == type && entry.text().contains(text));
    }

    /** Laat de temperatuur opschuiven (stappen van twee seconden) tot {@code done} klopt, hooguit {@code max} stappen. */
    static int settleUntil(ServerLevel level, SecurityCoreBlockEntity core, java.util.function.BooleanSupplier done, int max) {
        int steps = 0;
        while (!done.getAsBoolean() && steps < max) {
            settle(level, core, 1);
            steps++;
        }
        return steps;
    }

    /**
     * Elk rack zijn eigen temperatuur (jouw voorbeeld, 8 oktober): rack A heeft een CPU, RAM, een SSD
     * en een fan; rack B ernaast alleen RAM en geen fan. Samen zijn ze één server (2 RAM), maar de fan
     * van A helpt B niet: B wordt steeds warmer, A blijft koel. Is B warm, dan werkt de hele server op
     * halve snelheid, staat B in het logboek, en zegt B dat er een fan in moet. B onthoudt zijn
     * temperatuur (opslaan en laden). Een fan in B: weer koel.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "datacenter")
    public static void datacenter_every_rack_needs_a_fan(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = datacenter(helper);
        SecurityCoreBlockEntity core = setup.core();
        ServerRackBlockEntity a = setup.rackEntity(helper);
        a.insert(new ItemStack(ModItems.SERVER_FAN.get()));
        BlockPos bPos = setup.rack().east();
        helper.setBlock(bPos, ModBlocks.SERVER_RACK.get());
        ServerRackBlockEntity b = (ServerRackBlockEntity) helper.getBlockEntity(bPos);
        b.insert(new ItemStack(ModItems.SERVER_RAM.get()));
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), core);
            ImtTechGameTests.startServer(helper, bPos, core);
            LogicServer.Stats stats = LogicServer.stats(level, core);
            if (!stats.online() || stats.racks() != 2 || stats.ram() != 2 || stats.fans() != 1
                    || b.status() != ServerRackBlockEntity.Status.ONLINE) {
                throw new GameTestAssertException("De twee racks zouden samen één server moeten zijn (2 RAM, 1 fan): " + stats
                        + ", rack B " + b.status());
            }
            // De rekenregels: een rack met warmte maar zonder fan wordt steeds warmer en heeft er minstens één
            // nodig; een leeg rack niet; een fan koelt alleen zijn eigen rack.
            if (!Double.isInfinite(Datacenter.target(ImtConfig.Logic.MEMORY_HEAT, 0)) || Datacenter.fansNeeded(ImtConfig.Logic.MEMORY_HEAT, 0) != 1
                    || Datacenter.fansNeeded(0, 0) != 0 || Datacenter.target(3.0, ImtConfig.Logic.FAN_COOLING) != ImtConfig.Logic.AMBIENT_TEMPERATURE) {
                throw new GameTestAssertException("De rekenregels voor één rack kloppen niet");
            }
            int steps = settleUntil(level, core, () -> b.temperature() >= ImtConfig.Logic.WARM_TEMPERATURE, 1000);
            if (b.temperature() < ImtConfig.Logic.WARM_TEMPERATURE) {
                throw new GameTestAssertException("Rack B (alleen RAM, geen fan) zou warm moeten worden, maar is na " + steps
                        + " stappen " + Datacenter.degrees(b.temperature()));
            }
            if (a.temperature() >= LogicStats.coolAgainTemperature()) {
                throw new GameTestAssertException("Rack A (met fan) zou koel moeten blijven, niet " + Datacenter.degrees(a.temperature()));
            }
            if (Math.abs(core.datacenter().temperature() - b.temperature()) > 0.01) {
                throw new GameTestAssertException("Het datacenter zou de temperatuur van het heetste rack moeten tonen: "
                        + Datacenter.degrees(core.datacenter().temperature()));
            }
            LogicServer.Stats hot = LogicServer.stats(level, core);
            if (!hot.warm() || hot.reactionTicks() != LogicStats.reactionTicks(1.0 / ImtConfig.Logic.WARM_SLOWDOWN)) {
                throw new GameTestAssertException("Eén warm rack zou de hele server half zo snel moeten maken: " + hot.reactionTicks() + " ticks");
            }
            BlockPos bAbs = helper.absolutePos(bPos);
            if (!logged(core, EventLog.Type.HEAT, "Rack at " + bAbs.getX() + " " + bAbs.getY() + " " + bAbs.getZ() + " running hot")) {
                throw new GameTestAssertException("Het logboek zegt niet dat rack B warm is: " + core.log().entries());
            }
            if (b.fansNeeded() != 1 || a.fansNeeded() != 0) {
                throw new GameTestAssertException("Rack B zou 1 fan moeten vragen en rack A geen: " + b.fansNeeded() + " / " + a.fansNeeded());
            }
            // Rack B onthoudt hoe warm het is.
            ServerRackBlockEntity loaded = new ServerRackBlockEntity(b.getBlockPos(), b.getBlockState());
            loaded.loadWithComponents(b.saveWithoutMetadata(level.registryAccess()), level.registryAccess());
            if (Math.abs(loaded.temperature() - b.temperature()) > 0.01) {
                throw new GameTestAssertException("Na opslaan en laden is de temperatuur van rack B anders: " + loaded.temperature());
            }
            // Een fan in rack B: het koelt weer af.
            b.insert(new ItemStack(ModItems.SERVER_FAN.get()));
            settleUntil(level, core, () -> b.temperature() < LogicStats.coolAgainTemperature() - 1, 200);
            settle(level, core, 1);
            if (b.temperature() >= LogicStats.coolAgainTemperature() || LogicServer.stats(level, core).warm()) {
                throw new GameTestAssertException("Met een fan zou rack B weer koel moeten worden, niet " + Datacenter.degrees(b.temperature()));
            }
            if (!logged(core, EventLog.Type.HEAT, "cool again")) {
                throw new GameTestAssertException("Het logboek zegt niet dat het datacenter weer koel is");
            }
            helper.succeed();
        });
    }

    /** Zet een diagram en een keycard op de server (om te zien of ze bij een brand weg zijn). */
    static void putData(SecurityCoreBlockEntity core) {
        String code = """
                when every(10):
                    log("tick")
                """;
        core.logic().put(null, new LogicProgram("Clock", true, GraphConverter.toGraph(ScriptParser.parse(code), null), code));
        core.logic().issue("Guest", 1, "test", "");
    }

    /**
     * Slijten: boven 90 graden slijten de onderdelen (een balkje op het item) en gaan ze een voor een
     * kapot. Gaat er een SSD kapot, dan zijn alle diagrammen en keycards van de server weg.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "datacenter")
    public static void datacenter_wear_and_ssd_data_loss(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = datacenter(helper);
        SecurityCoreBlockEntity core = setup.core();
        ServerRackBlockEntity rack = setup.rackEntity(helper);
        putData(core);
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), core);
            boolean sawWear = false;
            for (int i = 0; i < 120 && rack.count(ServerPartItem.Kind.SSD) > 0; i++) {
                rack.setTemperature(100);
                LogicServer.forgetAll();
                core.datacenter().update(level, core);
                for (int slot = 0; slot < rack.parts().getContainerSize(); slot++) {
                    ItemStack stack = rack.parts().getItem(slot);
                    if (ServerPartItem.wear(stack) > 0 && stack.isBarVisible()) {
                        sawWear = true;
                    }
                }
            }
            if (!sawWear) {
                throw new GameTestAssertException("Boven 90 graden zou een onderdeel moeten slijten (met een balkje)");
            }
            if (rack.count(ServerPartItem.Kind.SSD) > 0) {
                throw new GameTestAssertException("Na 120 keer boven 90 graden zou de SSD kapot moeten zijn");
            }
            if (!core.logic().programs().isEmpty() || !core.logic().cards().isEmpty()) {
                throw new GameTestAssertException("Met een kapotte SSD zouden de diagrammen en keycards weg moeten zijn: "
                        + core.logic().programs().size() + " diagrammen, " + core.logic().cards().size() + " kaarten");
            }
            if (!logged(core, EventLog.Type.ALARM, "SSD burned") || !logged(core, EventLog.Type.HEAT, "burned out")) {
                throw new GameTestAssertException("Het logboek zegt niet dat er iets kapot ging: " + core.log().entries());
            }
            // Een nieuwe kaart krijgt nooit het nummer van een verloren kaart.
            var card = core.logic().issue("New", 1, "test", "");
            if (card == null || card.number() == 1001) {
                throw new GameTestAssertException("Kaartnummers mogen niet terugkomen na een brand: " + card);
            }
            helper.succeed();
        });
    }

    /**
     * Brand: blijft een rack lang boven 110 graden, dan vat het vlam (hier het rack met twee GPU's,
     * niet zijn buur met de CPU: elk rack heeft zijn eigen temperatuur), met vuur erop. Daarna brandt
     * het af: het rack en wat erin zat zijn weg, en het logboek zegt het.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "datacenter")
    public static void datacenter_fire_burns_hot_rack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = datacenter(helper);
        SecurityCoreBlockEntity core = setup.core();
        BlockPos gpuRack = setup.rack().east();
        helper.setBlock(gpuRack, ModBlocks.SERVER_RACK.get());
        ServerRackBlockEntity hot = (ServerRackBlockEntity) helper.getBlockEntity(gpuRack);
        for (int i = 0; i < 2; i++) {
            hot.insert(new ItemStack(ModItems.SERVER_GPU.get()));
        }
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), core);
            ImtTechGameTests.startServer(helper, gpuRack, core);
            if (LogicServer.stats(level, core).racks() != 2) {
                throw new GameTestAssertException("De twee racks zouden samen de server moeten zijn");
            }
            int updates = 0;
            while (!hot.isBurning() && updates < 10) {
                hot.setTemperature(120);
                LogicServer.forgetAll();
                core.datacenter().update(level, core);
                updates++;
            }
            if (!hot.isBurning() || setup.rackEntity(helper).isBurning()
                    || setup.rackEntity(helper).temperature() >= ImtConfig.Logic.HOT_TEMPERATURE) {
                throw new GameTestAssertException("Het rack met de GPU's zou in brand moeten staan (na " + updates + " keer), niet het andere");
            }
            if (updates * 40 < Math.round(ImtConfig.Logic.FIRE_DELAY_TICKS)) {
                throw new GameTestAssertException("Het rack vatte te snel vlam: na " + updates * 40 + " ticks");
            }
            helper.assertBlockPresent(net.minecraft.world.level.block.Blocks.FIRE, gpuRack.above());
            if (!logged(core, EventLog.Type.ALARM, "on fire")) {
                throw new GameTestAssertException("Het logboek zegt niet dat er brand is");
            }
            hot.burnDown(level);
            if (helper.getBlockState(gpuRack).is(ModBlocks.SERVER_RACK.get())) {
                throw new GameTestAssertException("Het rack zou afgebrand moeten zijn");
            }
            if (!logged(core, EventLog.Type.ALARM, "burned down")) {
                throw new GameTestAssertException("Het logboek zegt niet dat het rack afbrandde");
            }
            // Er valt niets uit een afgebrand rack.
            if (!level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(gpuRack)).inflate(2)).isEmpty()) {
                throw new GameTestAssertException("Uit een afgebrand rack zou niets mogen vallen");
            }
            helper.succeed();
        });
    }

    /**
     * Gebruik meten (de app Monitor): een timer die elke seconde iets doet en dan 5 seconden wacht,
     * laat de CPU werken en neemt RAM in (sinds hoofdstuk 64: elk bezig diagram RUN_MB, en de CPU zijn deel zo ver als hij
     * aan het werk is). De kern meet dat elke seconde en onthoudt de laatste minuut; de momentopname voor de desktop heeft
     * alles erin, ook de racks, en wat er van het RAM en de SSD in gebruik is (op de SSD: precies de diagrammen, BLOCK_MB per
     * blokje).
     */
    @GameTest(template = ARENA, timeoutTicks = 160, batch = "datacenter")
    public static void datacenter_usage_history(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = datacenter(helper);
        SecurityCoreBlockEntity core = setup.core();
        String code = """
                when every(1):
                    log("busy")
                    wait(5)
                    log("done")
                """;
        core.logic().put(null, new LogicProgram("Busy", true, GraphConverter.toGraph(ScriptParser.parse(code), null), code));
        helper.runAfterDelay(2, () -> ImtTechGameTests.startServer(helper, setup.rack(), core));
        helper.runAfterDelay(140, () -> {
            java.util.List<int[]> history = core.datacenter().history();
            if (history.size() < 5) {
                throw new GameTestAssertException("De kern zou elke seconde moeten meten, maar heeft " + history.size() + " metingen");
            }
            boolean cpu = history.stream().anyMatch(sample -> sample[0] > 0);
            boolean ram = history.stream().anyMatch(sample -> sample[2] > 0);
            if (!cpu || !ram) {
                throw new GameTestAssertException("De metingen zouden CPU- en RAM-gebruik moeten tonen: cpu " + cpu + ", ram " + ram);
            }
            var snapshot = LogicServer.snapshot(level, core);
            LogicServer.Stats now = LogicServer.stats(level, core);
            if (snapshot.getDouble("ramUsedMb") <= 0 || snapshot.getDouble("ramUsedMb") > now.ramMb()
                    || snapshot.getDouble("ssdUsedMb") != LogicStats.blocksMb(core.logic().blocks())) {
                throw new GameTestAssertException("In gebruik klopt niet: RAM " + snapshot.getDouble("ramUsedMb") + " MB van "
                        + now.ramMb() + ", SSD " + snapshot.getDouble("ssdUsedMb") + " MB voor " + core.logic().blocks() + " blokjes");
            }
            // Doet niets (geen diagram bezig, CPU en brein stil): dan is er ook geen RAM in gebruik.
            if (Datacenter.ramUsedMb(now, 0, 0.0) != 0.0 || Datacenter.ramUsedMb(now, 0, 1.0) != Math.min(now.ramMb(), now.ramNeededMb())) {
                throw new GameTestAssertException("Stil zou 0 MB RAM moeten zijn, en hard werken het deel van de CPU's");
            }
            if (snapshot.getInt("used") != core.logic().blocks() || snapshot.getIntArray("hCpu").length != history.size()
                    || snapshot.getList("racksInfo", 10).size() != 1
                    || snapshot.getList("racksInfo", 10).getCompound(0).getList("parts", 10).size() != 3
                    || !snapshot.getList("racksInfo", 10).getCompound(0).contains("t")) {
                throw new GameTestAssertException("De momentopname voor de Monitor klopt niet: " + snapshot);
            }
            // Hard werken maakt warmer.
            ServerRackBlockEntity rack = setup.rackEntity(helper);
            if (Datacenter.heat(rack, 1.0) <= Datacenter.heat(rack, 0.0)) {
                throw new GameTestAssertException("Een CPU die hard werkt zou meer warmte moeten maken");
            }
            helper.succeed();
        });
    }

    /** Een opdracht van de app Backup, zoals de desktop hem stuurt. */
    static Backups.Result backupAction(ServerPlayer player, SecurityCoreBlockEntity core, String op, String key, int value) {
        CompoundTag args = new CompoundTag();
        args.putString("key", key);
        args.putInt("id", value);
        args.putInt("minutes", value);
        return Backups.handle(player, core, op, args);
    }

    /**
     * Backups: twee datacenters van dezelfde speler. Een backup van A gaat naar B en neemt daar plek;
     * na een brand op A (alles weg) zet Restore de diagrammen en keycards terug. Er blijven er hooguit 3
     * per netwerk staan. B maakt automatisch een backup naar A. Brandt een SSD op B, dan zijn de backups
     * die op B stonden ook weg, en kan A er niet meer naartoe (B draait niet meer).
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "datacenter")
    public static void datacenter_backup_and_restore(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = datacenter(helper);
        ServerPlayer owner = setup.owner();
        SecurityCoreBlockEntity a = setup.core();
        SecurityCoreBlockEntity b = ImtTechGameTests.placeCore(helper, new BlockPos(14, 3, 14), owner);
        BlockPos rackB = new BlockPos(12, 3, 12);
        ImtTechGameTests.placeServer(helper, rackB, b);
        putData(a);
        helper.runAfterDelay(3, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), a);
            ImtTechGameTests.startServer(helper, rackB, b);
            a.datacenter().update(level, a);
            b.datacenter().update(level, b);
            GlobalPos atA = GlobalPos.of(level.dimension(), a.getBlockPos());
            GlobalPos atB = GlobalPos.of(level.dimension(), b.getBlockPos());
            Datacenters datacenters = Datacenters.get(level);
            if (datacenters.summary(atB) == null || !datacenters.summary(atB).online()) {
                throw new GameTestAssertException("Datacenter B zou zich gemeld moeten hebben en draaien: " + datacenters.summary(atB));
            }
            // Zonder doel: een backup kan niet.
            if (!backupAction(owner, a, "backup_now", "", 0).error()) {
                throw new GameTestAssertException("Zonder gekozen datacenter zou Backup now moeten mislukken");
            }
            Backups.Result chosen = backupAction(owner, a, "backup_target", Datacenters.key(atB), 0);
            Backups.Result made = backupAction(owner, a, "backup_now", "", 0);
            if (chosen.error() || made.error()) {
                throw new GameTestAssertException("Backup naar B mislukte: " + chosen.text() + " / " + made.text());
            }
            int size = Backups.size(a.logic());
            if (datacenters.free(atB, null, Math.round(ImtConfig.Logic.BACKUPS_KEPT)) != LogicStats.blockCapacity(1) - size) {
                throw new GameTestAssertException("De backup zou " + size + " blokjes op B moeten innemen, er is nog "
                        + datacenters.free(atB, null, Math.round(ImtConfig.Logic.BACKUPS_KEPT)) + " vrij");
            }
            // Brand op A: alles weg. Restore zet het terug.
            a.logic().wipe();
            Datacenters.Backup backup = Backups.newest(level, owner.getUUID());
            Backups.Result restored = backupAction(owner, a, "backup_restore", "", backup == null ? -1 : backup.id());
            if (restored.error() || a.logic().programs().size() != 1 || a.logic().cards().size() != 1) {
                throw new GameTestAssertException("Restore zette niet alles terug: " + restored.text() + ", "
                        + a.logic().programs().size() + " diagrammen, " + a.logic().cards().size() + " kaarten");
            }
            // Hooguit 3 per netwerk op een datacenter.
            for (int i = 0; i < 4; i++) {
                backupAction(owner, a, "backup_now", "", 0);
            }
            long ofA = datacenters.backupsOf(owner.getUUID()).stream().filter(item -> item.source().equals(atA)).count();
            if (ofA != Math.round(ImtConfig.Logic.BACKUPS_KEPT)) {
                throw new GameTestAssertException("Er zouden " + Math.round(ImtConfig.Logic.BACKUPS_KEPT) + " backups van A moeten staan, niet " + ofA);
            }
            // B maakt automatisch een backup naar A (de eerste meteen).
            backupAction(owner, b, "backup_target", Datacenters.key(atA), 0);
            backupAction(owner, b, "backup_auto", "", 5);
            LogicServer.forgetAll();
            b.datacenter().update(level, b);
            boolean auto = datacenters.backupsOf(owner.getUUID()).stream()
                    .anyMatch(item -> item.source().equals(atB) && item.target().equals(atA) && item.auto());
            if (!auto) {
                throw new GameTestAssertException("B zou automatisch een backup naar A moeten maken");
            }
            // Een SSD op B verbrandt: de backups op B zijn weg, en B draait niet meer.
            ServerRackBlockEntity burning = (ServerRackBlockEntity) helper.getBlockEntity(rackB);
            burning.burnDown(level);
            LogicServer.forgetAll();
            b.datacenter().update(level, b);
            if (datacenters.storedAt(atB) != 0) {
                throw new GameTestAssertException("Na de brand op B zouden de backups op B weg moeten zijn");
            }
            if (!backupAction(owner, a, "backup_now", "", 0).error()) {
                throw new GameTestAssertException("Een backup naar een datacenter dat niet draait zou moeten mislukken");
            }
            helper.succeed();
        });
    }

    /**
     * Het brein op het datacenter: zonder datacenter geen brein (-1), in creative geen datacenter nodig
     * (0), en met een datacenter zijn vertraging (1 CPU: 8 seconden). Een GPU erbij maakt het sneller,
     * warm maakt het trager. Terwijl ze nadenkt, werkt het datacenter hard (brein 100% in de Monitor,
     * met de naam van wie het is).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "datacenter")
    public static void datacenter_runs_the_brain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = datacenter(helper);
        SecurityCoreBlockEntity core = setup.core();
        ServerPlayer owner = setup.owner();
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(9, 3, 9));
        @SuppressWarnings("removal")
        ServerPlayer creative = helper.makeMockServerPlayerInLevel();
        helper.runAfterDelay(3, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), core);
            settle(level, core, 1);
            double cpu = LogicStats.brainPower(com.senne.imttech.logic.ServerPartItem.Kind.CPU);
            if (DatacenterBrain.delayFor(owner) != LogicStats.brainDelayTicks(cpu)) {
                throw new GameTestAssertException("Met 1 CPU zou het brein " + LogicStats.brainDelayTicks(cpu)
                        + " ticks moeten wachten, niet " + DatacenterBrain.delayFor(owner));
            }
            if (!DatacenterBrain.sourceFor(owner).equals(core.hostname())) {
                throw new GameTestAssertException("Het brein zou op " + core.hostname() + " moeten draaien, niet " + DatacenterBrain.sourceFor(owner));
            }
            if (DatacenterBrain.delayFor(stranger) != -1 || DatacenterBrain.delayFor(creative) != 0) {
                throw new GameTestAssertException("Zonder datacenter -1 en in creative 0, niet " + DatacenterBrain.delayFor(stranger)
                        + " / " + DatacenterBrain.delayFor(creative));
            }
            // Een GPU erbij: kracht 1 + 2 = 3, maal wat het ene latje RAM van twee chips kan voeden (hoofdstuk 64).
            double fed = LogicStats.fed(2, 1);
            setup.rackEntity(helper).insert(new ItemStack(ModItems.SERVER_GPU.get()));
            settle(level, core, 1);
            if (DatacenterBrain.delayFor(owner) != LogicStats.brainDelayTicks(3.0 * cpu * fed)) {
                throw new GameTestAssertException("Met een CPU en een GPU zou het " + LogicStats.brainDelayTicks(3.0 * cpu * fed)
                        + " ticks moeten zijn, niet " + DatacenterBrain.delayFor(owner));
            }
            // Warm: half zo snel.
            setup.rackEntity(helper).setTemperature(ImtConfig.Logic.WARM_TEMPERATURE + 5);
            LogicServer.forgetAll();
            core.datacenter().update(level, core);
            setup.rackEntity(helper).setTemperature(ImtConfig.Logic.WARM_TEMPERATURE + 5);
            LogicServer.forgetAll();
            core.datacenter().update(level, core);
            if (DatacenterBrain.delayFor(owner) != LogicStats.brainDelayTicks(3.0 * cpu * fed / ImtConfig.Logic.WARM_SLOWDOWN)) {
                throw new GameTestAssertException("Warm zou het brein half zo snel moeten zijn: " + DatacenterBrain.delayFor(owner));
            }
            // Nadenken: het datacenter werkt hard, en de Monitor zegt voor wie.
            DatacenterBrain.start(owner);
            core.datacenter().sample(level, core);
            var snapshot = LogicServer.snapshot(level, core);
            if (core.datacenter().brainLoad() < 1.0 || snapshot.getList("brainProcs", 8).size() != 1) {
                throw new GameTestAssertException("Terwijl ze nadenkt zou het brein 100% moeten zijn: " + core.datacenter().brainLoad());
            }
            DatacenterBrain.stop(owner);
            core.datacenter().sample(level, core);
            if (core.datacenter().brainLoad() > 0) {
                throw new GameTestAssertException("Klaar met nadenken: het brein zou weer 0% moeten zijn");
            }
            helper.succeed();
        });
    }
}
