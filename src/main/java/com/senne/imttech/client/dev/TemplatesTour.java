package com.senne.imttech.client.dev;

import com.senne.imttech.ImtTech;
import com.senne.imttech.client.os.SecurityOsScreen;
import com.senne.imttech.logic.ServerRackBlockEntity;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.SecurityOs;
import com.senne.imttech.station.ChargerBlockEntity;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Hoofdstuk 125 in de rondleiding met screenshots ({@code gradlew runVisualCheck -Ponly=templates}): een nieuw programma
 * begint leeg (met grijze uitleg die niet bewaard wordt), code bewaren met Templates, en in een nieuw leeg programma je
 * templates aanklikken om ze in te laden. Ook: een serverdiagram ziet de robottemplates niet, een nieuw global script heeft
 * alleen de regel global, en staat er al code, dan vraagt een klik op een template het eerst. Screenshots tp01 tot tp08.
 * <p>
 * De templates staan in {@code run-visual/imttech/script_templates.json}; die van de rondleiding gaan op het einde weer weg
 * (en ook aan het begin, als een vorige rondleiding halverwege stopte).
 */
final class TemplatesTour {
    private TemplatesTour() {}

    /** Ver van de andere stukken van de rondleiding: een kern met een server, op stroom. */
    private static final BlockPos CORE = new BlockPos(230, -60, -140);
    private static final BlockPos RACK = new BlockPos(226, -60, -138);

    private static final String PATROL = """
            scanPin = pin(1)

            when started:
                while true:
                    if robot.battery < 20:
                        charge()
                    forward(4)
                    turn_right()
                    if read(scanPin):
                        alarm("Intruder near " + robot.name)
            """;

    private static final String COME = """
            when ordered("come"):
                goto(order.x, order.y, order.z)
                send("arrived")
            """;

    static void tour(DevScript t) {
        t.onServer(0, TemplatesTour::stage);
        t.stand(0, CORE.getX() + 0.5, CORE.getY(), CORE.getZ() + 2.5, 180, 20);
        t.waitUntil(200, () -> VisualCheck.raidLeaveTicking(CORE));
        t.onServer(10, player -> SecurityOs.open(player, CORE));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof SecurityOsScreen);
        t.step(5, () -> HowToTour.mouseTo(1, 1));
        t.step(5, () -> os(SecurityOsScreen::tourCloseAll));
        t.step(0, () -> os(TemplatesTour::forget));
        // + > Robot program: leeg, met grijze uitleg (nog geen templates).
        t.step(0, () -> os(screen -> screen.tourLogicNewEmpty(ProgramKind.ROBOT)));
        t.shot(10, "tp01_new_robot_program_empty");
        // Code getypt (Patrol), Templates open met de naam, en dan Save as template.
        t.step(0, () -> os(screen -> {
            screen.tourLogic("Robot 1", PATROL, false);
            screen.tourLogicTemplates(true, "Patrol");
        }));
        t.shot(10, "tp02_templates_panel");
        t.step(0, () -> os(SecurityOsScreen::tourLogicSaveTemplate));
        t.shot(10, "tp03_template_saved");
        // Nog een template: Come when called.
        t.step(0, () -> os(screen -> {
            screen.tourLogic("Robot 1", COME, false);
            screen.tourLogicTemplates(true, "Come when called");
            screen.tourLogicSaveTemplate();
        }));
        // Een nieuw robotprogramma: leeg, met je templates om aan te klikken; dan Patrol aangeklikt.
        t.step(5, () -> os(screen -> screen.tourLogicNewEmpty(ProgramKind.ROBOT)));
        t.shot(10, "tp04_new_program_lists_templates");
        t.step(0, () -> os(screen -> screen.tourLogicLoadTemplate("Patrol")));
        t.shot(10, "tp05_template_loaded");
        // Een serverdiagram ziet de robottemplates niet; een nieuw global script heeft alleen de regel global.
        t.step(0, () -> os(screen -> screen.tourLogicNewEmpty(ProgramKind.SERVER)));
        t.shot(10, "tp06_new_server_diagram_empty");
        t.step(0, () -> os(SecurityOsScreen::tourLogicNewGlobal));
        t.shot(10, "tp07_new_global_script");
        // Code in de editor en Templates open: een klik op een andere template vraagt het eerst.
        t.step(0, () -> os(screen -> {
            screen.tourLogicNewEmpty(ProgramKind.ROBOT);
            screen.tourLogicLoadTemplate("Patrol");
            screen.tourLogicTemplates(true, null);
            screen.tourLogicLoadTemplate("Come when called");
        }));
        t.shot(10, "tp08_replace_asks_first");
        t.step(0, () -> os(TemplatesTour::forget));
        t.close(10);
    }

    /** De templates van de rondleiding weg (de rest van run-visual blijft). */
    private static void forget(SecurityOsScreen screen) {
        screen.tourLogicForgetTemplate(ProgramKind.ROBOT, "Patrol");
        screen.tourLogicForgetTemplate(ProgramKind.ROBOT, "Come when called");
    }

    /** Een kern van jou met een wachtwoord, en een rack met een CPU, RAM en een SSD, allebei op een volle generator. */
    private static void stage(net.minecraft.server.level.ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.setBlockAndUpdate(CORE, ModBlocks.SECURITY_CORE.get().defaultBlockState());
        if (level.getBlockEntity(CORE) instanceof SecurityCoreBlockEntity be) {
            be.setOwner(player);
            be.ensureSystem(level);
            be.setPassword("bitech1", level.random);
        }
        for (BlockPos generator : new BlockPos[] {CORE.below(), RACK.below()}) {
            level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof ChargerBlockEntity charger) {
                charger.fill(200_000);
            }
        }
        level.setBlockAndUpdate(RACK, ModBlocks.SERVER_RACK.get().defaultBlockState());
        if (level.getBlockEntity(RACK) instanceof ServerRackBlockEntity be) {
            be.insert(new ItemStack(ModItems.SERVER_CPU.get()));
            be.insert(new ItemStack(ModItems.SERVER_RAM.get()));
            be.insert(new ItemStack(ModItems.SERVER_SSD.get()));
            be.insert(new ItemStack(ModItems.SERVER_FAN.get()));
        }
    }

    /** Doe dit met de desktop, als die open staat. */
    private static void os(Consumer<SecurityOsScreen> action) {
        if (Minecraft.getInstance().screen instanceof SecurityOsScreen screen) {
            action.accept(screen);
        } else {
            ImtTech.LOGGER.warn("[visual check] de desktop staat niet open (maar {})", Minecraft.getInstance().screen);
        }
    }
}
