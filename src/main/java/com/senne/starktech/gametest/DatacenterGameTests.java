package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.logic.Backups;
import com.senne.starktech.logic.Datacenter;
import com.senne.starktech.logic.DatacenterBrain;
import com.senne.starktech.logic.Datacenters;
import com.senne.starktech.logic.LogicServer;
import com.senne.starktech.logic.LogicStats;
import com.senne.starktech.logic.ServerPartItem;
import com.senne.starktech.logic.ServerRackBlockEntity;
import com.senne.starktech.logic.program.GraphConverter;
import com.senne.starktech.logic.program.LogicProgram;
import com.senne.starktech.logic.program.ScriptParser;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.os.EventLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor het datacenter (hoofdstuk 9 van het plan, 8 oktober): zes vakjes per rack, de GPU
 * en de fan, warmte (elk rack zijn eigen temperatuur), slijten en brand, het gebruik, backups en het
 * brein van E.D.I.T.H.
 */
@GameTestHolder(StarkTech.MODID)
@PrefixGameTestTemplate(false)
public final class DatacenterGameTests {
    private DatacenterGameTests() {}

    private static final String ARENA = "empty";

    /** Een kern met een eigenaar en één rack met een CPU, RAM en SSD (zie StarkTechGameTests.placeServer). */
    record Setup(ServerPlayer owner, SecurityCoreBlockEntity core, BlockPos rack) {
        ServerRackBlockEntity rackEntity(GameTestHelper helper) {
            return (ServerRackBlockEntity) helper.getBlockEntity(rack);
        }
    }

    static Setup datacenter(GameTestHelper helper) {
        ServerPlayer owner = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 3, 5));
        SecurityCoreBlockEntity core = StarkTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos rack = new BlockPos(6, 3, 6);
        StarkTechGameTests.placeServer(helper, rack, core);
        return new Setup(owner, core, rack);
    }

    /**
     * Een rack heeft zes vakjes. Een GPU is half zo snel voor Logic als een CPU (de server reageert
     * twee keer zo traag), maar dubbel zo goed voor het brein (de helft van de vertraging). Zonder
     * CPU of GPU draait de server niet, en zegt hij waarom.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "datacenter")
    public static void datacenter_gpu_power(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = datacenter(helper);
        ServerRackBlockEntity rack = setup.rackEntity(helper);
        if (rack.parts().getContainerSize() != 6) {
            throw new GameTestAssertException("Een rack zou 6 vakjes moeten hebben, niet " + rack.parts().getContainerSize());
        }
        // De CPU eruit, een GPU erin (in de rechter kolom), en een fan in het laatste vakje.
        rack.parts().setItem(0, ItemStack.EMPTY);
        rack.parts().setItem(3, new ItemStack(ModItems.SERVER_GPU.get()));
        rack.parts().setItem(5, new ItemStack(ModItems.SERVER_FAN.get()));
        helper.runAfterDelay(2, () -> {
            StarkTechGameTests.startServer(helper, setup.rack(), setup.core());
            LogicServer.Stats gpu = LogicServer.stats(level, setup.core());
            if (!gpu.online() || gpu.cpus() != 0 || gpu.gpus() != 1 || gpu.fans() != 1) {
                throw new GameTestAssertException("De server met een GPU telt verkeerd: " + gpu + " (" + gpu.problem() + ")");
            }
            int oneCpu = LogicStats.reactionTicks(1.0);
            if (gpu.reactionTicks() != oneCpu * 2) {
                throw new GameTestAssertException("Een GPU zou twee keer zo traag moeten reageren als een CPU ("
                        + oneCpu * 2 + " ticks), niet " + gpu.reactionTicks());
            }
            int cpuDelay = LogicStats.brainDelayTicks(1.0);
            if (gpu.brainDelayTicks() != Math.round(cpuDelay / 2.0f)) {
                throw new GameTestAssertException("Met een GPU zou het brein half zo lang moeten wachten als met een CPU ("
                        + cpuDelay / 2 + " ticks), niet " + gpu.brainDelayTicks());
            }
            // Een CPU erbij: samen 1,5 voor Logic en 3 voor het brein.
            rack.parts().setItem(0, new ItemStack(ModItems.SERVER_CPU.get()));
            LogicServer.Stats both = LogicServer.stats(level, setup.core());
            if (both.logicPower() != 1.5 || both.brainPower() != 3.0) {
                throw new GameTestAssertException("CPU + GPU zou 1.5 Logic en 3 brein moeten zijn: " + both.logicPower()
                        + " / " + both.brainPower());
            }
            // Een groot datacenter (8 GPU's = kracht 16): geen vertraging meer. Niets: kan niet denken.
            if (LogicStats.brainDelayTicks(16.0) != 0 || LogicStats.brainDelayTicks(0.0) != -1) {
                throw new GameTestAssertException("Vertraging bij kracht 16 of 0 klopt niet: " + LogicStats.brainDelayTicks(16.0)
                        + " / " + LogicStats.brainDelayTicks(0.0));
            }
            // Geen CPU en geen GPU: de server zegt wat er mist.
            rack.parts().setItem(0, ItemStack.EMPTY);
            rack.parts().setItem(3, ItemStack.EMPTY);
            LogicServer.Stats none = LogicServer.stats(level, setup.core());
            if (none.online() || !"The server needs a CPU or GPU".equals(none.problem())) {
                throw new GameTestAssertException("Zonder CPU of GPU zou de server dat moeten zeggen, niet: " + none.problem());
            }
            helper.succeed();
        });
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
        a.parts().setItem(5, new ItemStack(ModItems.SERVER_FAN.get()));
        BlockPos bPos = setup.rack().east();
        helper.setBlock(bPos, ModBlocks.SERVER_RACK.get());
        ServerRackBlockEntity b = (ServerRackBlockEntity) helper.getBlockEntity(bPos);
        b.parts().setItem(0, new ItemStack(ModItems.SERVER_RAM.get()));
        helper.runAfterDelay(2, () -> {
            StarkTechGameTests.startServer(helper, setup.rack(), core);
            StarkTechGameTests.startServer(helper, bPos, core);
            LogicServer.Stats stats = LogicServer.stats(level, core);
            if (!stats.online() || stats.racks() != 2 || stats.ram() != 2 || stats.fans() != 1
                    || b.status() != ServerRackBlockEntity.Status.ONLINE) {
                throw new GameTestAssertException("De twee racks zouden samen één server moeten zijn (2 RAM, 1 fan): " + stats
                        + ", rack B " + b.status());
            }
            // De rekenregels: een rack met warmte maar zonder fan wordt steeds warmer en heeft er minstens één
            // nodig; een leeg rack niet; een fan koelt alleen zijn eigen rack.
            if (!Double.isInfinite(Datacenter.target(StarkConfig.Logic.MEMORY_HEAT, 0)) || Datacenter.fansNeeded(StarkConfig.Logic.MEMORY_HEAT, 0) != 1
                    || Datacenter.fansNeeded(0, 0) != 0 || Datacenter.target(3.0, StarkConfig.Logic.FAN_COOLING) != StarkConfig.Logic.AMBIENT_TEMPERATURE) {
                throw new GameTestAssertException("De rekenregels voor één rack kloppen niet");
            }
            int steps = settleUntil(level, core, () -> b.temperature() >= StarkConfig.Logic.WARM_TEMPERATURE, 1000);
            if (b.temperature() < StarkConfig.Logic.WARM_TEMPERATURE) {
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
            if (!hot.warm() || hot.reactionTicks() != LogicStats.reactionTicks(1.0 / StarkConfig.Logic.WARM_SLOWDOWN)) {
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
            b.parts().setItem(5, new ItemStack(ModItems.SERVER_FAN.get()));
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
        core.logic().issue("Guest", 1, "test");
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
            StarkTechGameTests.startServer(helper, setup.rack(), core);
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
            var card = core.logic().issue("New", 1, "test");
            if (card == null || card.number() == 1001) {
                throw new GameTestAssertException("Kaartnummers mogen niet terugkomen na een brand: " + card);
            }
            helper.succeed();
        });
    }

    /**
     * Brand: blijft een rack lang boven 110 graden, dan vat het vlam (hier het rack met drie GPU's,
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
        for (int slot = 0; slot < 3; slot++) {
            hot.parts().setItem(slot, new ItemStack(ModItems.SERVER_GPU.get()));
        }
        helper.runAfterDelay(2, () -> {
            StarkTechGameTests.startServer(helper, setup.rack(), core);
            StarkTechGameTests.startServer(helper, gpuRack, core);
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
                    || setup.rackEntity(helper).temperature() >= StarkConfig.Logic.HOT_TEMPERATURE) {
                throw new GameTestAssertException("Het rack met de GPU's zou in brand moeten staan (na " + updates + " keer), niet het andere");
            }
            if (updates * 40 < StarkConfig.Logic.FIRE_DELAY_TICKS) {
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
     * laat de CPU werken en vult het RAM (1 latje = 2 tegelijk). De kern meet dat elke seconde en
     * onthoudt de laatste minuut; de momentopname voor de desktop heeft alles erin, ook de racks.
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
        helper.runAfterDelay(2, () -> StarkTechGameTests.startServer(helper, setup.rack(), core));
        helper.runAfterDelay(140, () -> {
            java.util.List<int[]> history = core.datacenter().history();
            if (history.size() < 5) {
                throw new GameTestAssertException("De kern zou elke seconde moeten meten, maar heeft " + history.size() + " metingen");
            }
            boolean cpu = history.stream().anyMatch(sample -> sample[0] > 0);
            boolean ram = history.stream().anyMatch(sample -> sample[2] >= 50);
            if (!cpu || !ram) {
                throw new GameTestAssertException("De metingen zouden CPU- en RAM-gebruik moeten tonen: cpu " + cpu + ", ram " + ram);
            }
            var snapshot = LogicServer.snapshot(level, core);
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
        SecurityCoreBlockEntity b = StarkTechGameTests.placeCore(helper, new BlockPos(14, 3, 14), owner);
        BlockPos rackB = new BlockPos(12, 3, 12);
        StarkTechGameTests.placeServer(helper, rackB, b);
        putData(a);
        helper.runAfterDelay(3, () -> {
            StarkTechGameTests.startServer(helper, setup.rack(), a);
            StarkTechGameTests.startServer(helper, rackB, b);
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
            if (datacenters.free(atB, null, StarkConfig.Logic.BACKUPS_KEPT) != StarkConfig.Logic.BLOCKS_PER_SSD - size) {
                throw new GameTestAssertException("De backup zou " + size + " blokjes op B moeten innemen, er is nog "
                        + datacenters.free(atB, null, StarkConfig.Logic.BACKUPS_KEPT) + " vrij");
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
            if (ofA != StarkConfig.Logic.BACKUPS_KEPT) {
                throw new GameTestAssertException("Er zouden " + StarkConfig.Logic.BACKUPS_KEPT + " backups van A moeten staan, niet " + ofA);
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
        ServerPlayer stranger = StarkTechGameTests.makeSurvivalOwner(helper, new BlockPos(9, 3, 9));
        @SuppressWarnings("removal")
        ServerPlayer creative = helper.makeMockServerPlayerInLevel();
        helper.runAfterDelay(3, () -> {
            StarkTechGameTests.startServer(helper, setup.rack(), core);
            settle(level, core, 1);
            if (DatacenterBrain.delayFor(owner) != LogicStats.brainDelayTicks(1.0)) {
                throw new GameTestAssertException("Met 1 CPU zou het brein " + LogicStats.brainDelayTicks(1.0)
                        + " ticks moeten wachten, niet " + DatacenterBrain.delayFor(owner));
            }
            if (!DatacenterBrain.sourceFor(owner).equals(core.hostname())) {
                throw new GameTestAssertException("Het brein zou op " + core.hostname() + " moeten draaien, niet " + DatacenterBrain.sourceFor(owner));
            }
            if (DatacenterBrain.delayFor(stranger) != -1 || DatacenterBrain.delayFor(creative) != 0) {
                throw new GameTestAssertException("Zonder datacenter -1 en in creative 0, niet " + DatacenterBrain.delayFor(stranger)
                        + " / " + DatacenterBrain.delayFor(creative));
            }
            // Een GPU erbij: kracht 1 + 2 = 3.
            setup.rackEntity(helper).parts().setItem(3, new ItemStack(ModItems.SERVER_GPU.get()));
            settle(level, core, 1);
            if (DatacenterBrain.delayFor(owner) != LogicStats.brainDelayTicks(3.0)) {
                throw new GameTestAssertException("Met een CPU en een GPU zou het " + LogicStats.brainDelayTicks(3.0)
                        + " ticks moeten zijn, niet " + DatacenterBrain.delayFor(owner));
            }
            // Warm: half zo snel.
            setup.rackEntity(helper).setTemperature(StarkConfig.Logic.WARM_TEMPERATURE + 5);
            LogicServer.forgetAll();
            core.datacenter().update(level, core);
            setup.rackEntity(helper).setTemperature(StarkConfig.Logic.WARM_TEMPERATURE + 5);
            LogicServer.forgetAll();
            core.datacenter().update(level, core);
            if (DatacenterBrain.delayFor(owner) != LogicStats.brainDelayTicks(3.0 / StarkConfig.Logic.WARM_SLOWDOWN)) {
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
