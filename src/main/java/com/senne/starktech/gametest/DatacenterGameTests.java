package com.senne.starktech.gametest;

import com.senne.starktech.StarkTech;
import com.senne.starktech.logic.LogicBalance;
import com.senne.starktech.logic.LogicServer;
import com.senne.starktech.logic.ServerRackBlockEntity;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.security.SecurityCoreBlockEntity;
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
}
