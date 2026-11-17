package com.senne.imttech.client.dev;

import com.senne.imttech.ImtTech;
import com.senne.imttech.client.os.SecurityOsScreen;
import com.senne.imttech.logic.ServerRackBlockEntity;
import com.senne.imttech.logic.program.GraphConverter;
import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.SecurityCoreBlock;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.EventLog;
import com.senne.imttech.security.os.SecurityOs;
import com.senne.imttech.station.ChargerBlockEntity;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Hoofdstuk 138 in de rondleiding met screenshots ({@code gradlew runVisualCheck -Ponly=logwatch}): Logs met lange regels
 * die afbreken (de tijd in een eigen kolom) en gemarkeerde regels in het rood, Logs met alleen de gemarkeerde regels (de
 * knop Flagged), en de Help van een serverdiagram bij WATCH YOUR LOG. Screenshots lw01 tot lw03.
 */
final class LogWatchTour {
    private LogWatchTour() {}

    /** Ver van de andere stukken van de rondleiding. */
    private static final BlockPos CORE = new BlockPos(262, -60, -230);
    private static final BlockPos RACK = new BlockPos(258, -60, -228);

    private static final String WATCH = """
            when logged:
                if log.kind == "login_fail":
                    flag(f"Wrong password: {log.text}")
                elif log.kind == "trap":
                    flag("Trap opened")
            """;

    static void tour(DevScript t) {
        t.stand(0, CORE.getX() + 0.5, CORE.getY(), CORE.getZ() + 2.5, 180, 20);
        t.waitUntil(200, () -> VisualCheck.raidLeaveTicking(CORE));
        t.command(0, "time set noon");
        t.onServer(10, LogWatchTour::stage);
        // De server moet draaien (hij telt zijn racks), dan de regels: het diagram Watch kijkt ze na.
        t.onServer(60, LogWatchTour::lines);
        t.onServer(40, player -> SecurityOs.open(player, CORE));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof SecurityOsScreen);
        t.step(5, () -> HowToTour.mouseTo(1, 1));
        t.step(5, () -> os(SecurityOsScreen::tourCloseAll));
        t.step(0, () -> Minecraft.getInstance().getToasts().clear());
        // lw01: Logs, met lange regels die afbreken en twee gemarkeerde regels.
        t.step(0, () -> os(screen -> screen.tourLogsFlagged(false)));
        t.shot(10, "lw01_logs_wrapped_flagged");
        // lw02: alleen de gemarkeerde regels.
        t.step(0, () -> os(screen -> screen.tourLogsFlagged(true)));
        t.shot(5, "lw02_logs_flagged_only");
        // lw03: de Help van een serverdiagram bij WATCH YOUR LOG.
        t.step(0, () -> os(screen -> {
            screen.tourCloseAll();
            screen.tourLogicNew(ProgramKind.SERVER);
            screen.tourLogic("Watch 2", WATCH, true);
            screen.tourLogicHelpAt("WATCH YOUR LOG");
        }));
        t.shot(10, "lw03_help_watch_your_log");
        t.close(10);
    }

    /** Een kern van jou met een rack en het diagram Watch. */
    private static void stage(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : new BlockPos[] {CORE, RACK}) {
            if (level.getBlockEntity(pos) instanceof com.senne.imttech.sdcard.SdCardHolder holder) {
                holder.takeSdCard();
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(CORE, ModBlocks.SECURITY_CORE.get().defaultBlockState()
                .setValue(SecurityCoreBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(CORE) instanceof SecurityCoreBlockEntity core) {
            core.setOwner(player);
            core.ensureSystem(level);
            core.setPassword("bitech1", level.random);
            core.logic().wipe();
            core.log().clear();
            core.logic().put(null, new LogicProgram("Watch", true,
                    GraphConverter.toGraph(ScriptParser.parse(WATCH, ProgramKind.SERVER), null), WATCH, ProgramKind.SERVER));
        }
        for (BlockPos generator : new BlockPos[] {CORE.below(), RACK.below()}) {
            level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof ChargerBlockEntity charger) {
                charger.fill(200_000);
            }
        }
        level.setBlockAndUpdate(RACK, ModBlocks.SERVER_RACK.get().defaultBlockState());
        if (level.getBlockEntity(RACK) instanceof ServerRackBlockEntity rack) {
            rack.insert(new ItemStack(ModItems.SERVER_CPU.get()));
            rack.insert(new ItemStack(ModItems.SERVER_RAM.get()));
            rack.insert(new ItemStack(ModItems.SERVER_SSD.get()));
            rack.insert(new ItemStack(ModItems.SERVER_FAN.get()));
        }
    }

    /** Regels zoals ze in een echt logboek staan, met een paar lange (zoals de echte teksten van de mod). */
    private static void lines(ServerPlayer player) {
        if (player.serverLevel().getBlockEntity(CORE) instanceof SecurityCoreBlockEntity core) {
            core.logEvent(EventLog.Type.LOGIN, player.getGameProfile().getName() + " logged in");
            core.logEvent(EventLog.Type.ENTRY, "Someone used a keycard at Lab door");
            core.logEvent(EventLog.Type.LOGIN_FAIL, "Wrong password from LongPlayerName42 (remote)");
            core.logEvent(EventLog.Type.LOGIC, "\"Lights\": there is no device called \"Hall light\" in this network");
            core.logEvent(EventLog.Type.TRAP, "LongPlayerName42 opened the trap file /home/dev/passwords.txt");
            core.logEvent(EventLog.Type.DEVICE, "LongPlayerName42 renamed \"Reader 1\" to \"Lab door reader\"");
        }
    }

    private static void os(Consumer<SecurityOsScreen> action) {
        if (Minecraft.getInstance().screen instanceof SecurityOsScreen screen) {
            action.accept(screen);
        } else {
            ImtTech.LOGGER.warn("[visual check] de desktop staat niet open (maar {})", Minecraft.getInstance().screen);
        }
    }
}
