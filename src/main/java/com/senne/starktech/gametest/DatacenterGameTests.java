package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.logic.Datacenter;
import com.senne.starktech.logic.LogicBalance;
import com.senne.starktech.logic.LogicServer;
import com.senne.starktech.logic.ServerRackBlockEntity;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.os.EventLog;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor het datacenter (hoofdstuk 9 van het plan, 8 oktober): zes vakjes per rack, de GPU
 * en de fan, warmte, slijten en brand, het gebruik, backups en het brein van E.D.I.T.H.
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
            int oneCpu = LogicBalance.reactionTicks(1.0);
            if (gpu.reactionTicks() != oneCpu * 2) {
                throw new GameTestAssertException("Een GPU zou twee keer zo traag moeten reageren als een CPU ("
                        + oneCpu * 2 + " ticks), niet " + gpu.reactionTicks());
            }
            int cpuDelay = LogicBalance.brainDelayTicks(1.0);
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
            if (LogicBalance.brainDelayTicks(16.0) != 0 || LogicBalance.brainDelayTicks(0.0) != -1) {
                throw new GameTestAssertException("Vertraging bij kracht 16 of 0 klopt niet: " + LogicBalance.brainDelayTicks(16.0)
                        + " / " + LogicBalance.brainDelayTicks(0.0));
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

    /**
     * Warmte: vier CPU's in één rack zonder fan maken 9 W warmte, en het rack koelt er maar 2 W weg.
     * Het wordt 81°: alles werkt op halve snelheid, het staat in het logboek, en het rack zegt dat er
     * fans bij moeten. Twee CPU's eruit en twee fans erin: het koelt weer af. De kern onthoudt de
     * temperatuur (opslaan en laden).
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "datacenter")
    public static void datacenter_heat_and_fans(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = datacenter(helper);
        SecurityCoreBlockEntity core = setup.core();
        ServerRackBlockEntity rack = setup.rackEntity(helper);
        for (int slot = 3; slot < 6; slot++) {
            rack.parts().setItem(slot, new ItemStack(ModItems.SERVER_CPU.get()));
        }
        helper.runAfterDelay(2, () -> {
            StarkTechGameTests.startServer(helper, setup.rack(), core);
            Datacenter datacenter = core.datacenter();
            settle(level, core, 120);
            double heat = 4 * LogicBalance.CPU_HEAT + 2 * LogicBalance.MEMORY_HEAT;
            double expected = Datacenter.target(heat, LogicBalance.RACK_COOLING);
            if (expected < LogicBalance.WARM_TEMPERATURE || Math.abs(datacenter.temperature() - expected) > 1.0) {
                throw new GameTestAssertException("Vier CPU's zonder fan zouden " + Datacenter.degrees(expected)
                        + " moeten worden, niet " + Datacenter.degrees(datacenter.temperature()));
            }
            LogicServer.Stats hot = LogicServer.stats(level, core);
            if (!hot.warm() || hot.reactionTicks() != LogicBalance.reactionTicks(4.0 / LogicBalance.WARM_SLOWDOWN)) {
                throw new GameTestAssertException("Warm zou half zo snel moeten zijn: " + hot.reactionTicks() + " ticks");
            }
            if (!logged(core, EventLog.Type.HEAT, "running hot")) {
                throw new GameTestAssertException("Het logboek zegt niet dat het datacenter warm is: " + core.log().entries());
            }
            if (Datacenter.fansNeeded(datacenter.fullHeat(), datacenter.cooling()) < 1) {
                throw new GameTestAssertException("Het rack zou moeten zeggen dat er fans bij moeten");
            }
            // De kern onthoudt hoe warm het is.
            Datacenter loaded = Datacenter.load(datacenter.save());
            if (Math.abs(loaded.temperature() - datacenter.temperature()) > 0.01) {
                throw new GameTestAssertException("Na opslaan en laden is de temperatuur anders: " + loaded.temperature());
            }
            // Twee CPU's eruit, twee fans erin: 5 W warmte, 14 W koeling.
            rack.parts().setItem(4, new ItemStack(ModItems.SERVER_FAN.get()));
            rack.parts().setItem(5, new ItemStack(ModItems.SERVER_FAN.get()));
            settle(level, core, 120);
            if (datacenter.temperature() >= LogicBalance.coolAgainTemperature() || LogicServer.stats(level, core).warm()) {
                throw new GameTestAssertException("Met twee fans zou het weer koel moeten worden, niet "
                        + Datacenter.degrees(datacenter.temperature()));
            }
            if (!logged(core, EventLog.Type.HEAT, "cool again")) {
                throw new GameTestAssertException("Het logboek zegt niet dat het datacenter weer koel is");
            }
            helper.succeed();
        });
    }
}
