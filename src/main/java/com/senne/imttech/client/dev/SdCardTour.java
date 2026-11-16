package com.senne.imttech.client.dev;

import com.senne.imttech.ImtTech;
import com.senne.imttech.client.os.SecurityOsScreen;
import com.senne.imttech.computer.ComputerBlock;
import com.senne.imttech.computer.ComputerBlockEntity;
import com.senne.imttech.computer.MonitorBlock;
import com.senne.imttech.logic.ServerRackBlockEntity;
import com.senne.imttech.logic.program.GraphConverter;
import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.network.OsActionPayload;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.SecurityCoreBlock;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.SecurityOs;
import com.senne.imttech.security.os.SystemDefaults;
import com.senne.imttech.station.ChargerBlockEntity;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Hoofdstuk 130 in de rondleiding met screenshots ({@code gradlew runVisualCheck -Ponly=sdcard}): een SD-kaart in een kern
 * en in een computer (het kaartje vooraan), en op de desktop: het lijstje SD in Logic, de pagina van de kaart (Copy en Copy
 * locked), een open en een vergrendeld programma op de kaart, een programma op slot op je server, de map sd in Files (met
 * het menu "Copy to SD card"), en de tooltip van de kaart. Screenshots sc01 tot sc10.
 * <p>
 * Met {@code -Ponly=howtopics:sdcard} maakt hij ook de plaatjes voor How it works (sd_logic_card, sd_locked, sd_files): dat
 * verandert bestanden van de mod.
 */
final class SdCardTour {
    private SdCardTour() {}

    /** Ver van de andere stukken van de rondleiding: een kern met een server, en een computer ernaast. */
    private static final BlockPos CORE = new BlockPos(262, -60, -140);
    private static final BlockPos RACK = new BlockPos(258, -60, -138);
    private static final BlockPos PC = new BlockPos(265, -60, -140);

    /** De map van de speler van de rondleiding in /home (pas bekend op de server). */
    private static volatile String home = "/home/dev";

    private static final String MINE_CAR = """
            # Strip mine: two blocks high, 16 long
            def dig():
                for i in range(16):
                    mine()
                    mine_up()
                    forward(1)
            """;
    private static final String LIGHTS = """
            when every(5):
                toggle("Hall light")
            """;
    private static final String DOOR_BELL = """
            # Ring when someone presses the bell
            when button("Bell"):
                pulse("Chime", 2)
                log("Someone at the door")
            """;
    private static final String GATE = """
            when keycard("Gate reader"):
                if card.in_group("staff"):
                    pulse("Gate", 5)
            """;

    static void tour(DevScript t) {
        prepare(t);
        // sc01: het kaartje vooraan in de kern en in de computer.
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.stand(5, CORE.getX() + 2.0, CORE.getY(), CORE.getZ() + 3.2, 180, 18);
        t.shot(20, "sc01_card_in_core_and_pc");
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        openDesktop(t);
        // sc02: Logic, met onder je programma's het lijstje SD.
        t.step(0, () -> os(screen -> screen.tourLogicOpen("Lights")));
        t.shot(10, "sc02_logic_sd_list");
        // sc03: de pagina van de kaart.
        t.step(0, () -> os(SecurityOsScreen::tourLogicCard));
        t.shot(10, "sc03_logic_card_page");
        // sc04: een open programma op de kaart (met zijn code om te lezen).
        t.step(0, () -> os(screen -> screen.tourLogicCardProgram("Door bell")));
        t.shot(15, "sc04_card_program_open");
        // sc05: een programma op slot op de kaart.
        t.step(0, () -> os(screen -> screen.tourLogicCardProgram("Mine car")));
        t.shot(10, "sc05_card_program_locked");
        // sc06: een programma op slot op je server (geïnstalleerd van de kaart).
        t.step(0, () -> os(screen -> {
            screen.tourLogicEditor();
            screen.tourLogicOpen("Gate");
        }));
        t.shot(10, "sc06_locked_on_server");
        // sc07 tot sc09: Files met de map sd, op de kaart, en het menu "Copy to SD card".
        t.step(0, () -> os(screen -> {
            screen.tourCloseAll();
            screen.tourFiles("/", "sd:/", false);
        }));
        t.shot(10, "sc07_files_sd_folder");
        t.step(0, () -> os(screen -> screen.tourFiles("sd:/", "sd:/readme.txt", false)));
        t.shot(10, "sc08_files_on_the_card");
        t.step(0, () -> os(screen -> screen.tourFiles(home, home + "/plan.txt", true)));
        t.shot(10, "sc09_files_copy_menu");
        t.close(10);
        // sc10: de tooltip van de kaart (in survival: in creative opent het creative-menu).
        t.command(0, "gamemode survival @a");
        t.onServer(0, player -> {
            ItemStack stack = ModItems.SD_CARD_32GB.get().getDefaultInstance();
            if (player.serverLevel().getBlockEntity(CORE) instanceof SecurityCoreBlockEntity core && !core.sdCard().isEmpty()) {
                stack = core.sdCard().copy(); // de kaart met de samenvatting van wat erop staat
            }
            player.getInventory().setItem(0, stack);
            player.getInventory().selected = 4;
        });
        t.open(10, () -> new InventoryScreen(Minecraft.getInstance().player));
        t.step(5, () -> {
            if (Minecraft.getInstance().screen instanceof InventoryScreen screen) {
                HowToTour.mouseTo(screen.getGuiLeft() + 16, screen.getGuiTop() + 150);
            }
        });
        t.shot(6, "sc10_item_tooltip");
        t.close(5);
        t.onServer(0, player -> player.getInventory().setItem(0, ItemStack.EMPTY));
        t.command(0, "gamemode creative @a");
    }

    /** De plaatjes voor How it works (hoofdstuk 130): de pagina van de kaart, een programma op slot, en Files. */
    static void pictures(DevScript t) {
        prepare(t);
        openDesktop(t);
        t.step(0, () -> os(SecurityOsScreen::tourLogicCard));
        t.step(10, () -> capture("sd_logic_card"));
        t.step(0, () -> os(screen -> {
            screen.tourLogicEditor();
            screen.tourLogicOpen("Gate");
        }));
        t.step(10, () -> capture("sd_locked"));
        t.step(0, () -> os(screen -> {
            screen.tourCloseAll();
            screen.tourFiles("/", "sd:/", false);
        }));
        t.step(10, () -> capture("sd_files"));
        t.close(10);
    }

    /** De plek klaarzetten, en de kaart vullen zoals een speler dat op de desktop doet. */
    private static void prepare(DevScript t) {
        t.stand(0, CORE.getX() + 0.5, CORE.getY(), CORE.getZ() + 2.5, 180, 20);
        t.waitUntil(200, () -> VisualCheck.raidLeaveTicking(CORE));
        t.command(0, "time set noon");
        t.onServer(10, SdCardTour::stage);
        // De server moet draaien om te installeren: even wachten tot hij zijn racks telde.
        t.onServer(60, player -> SecurityOs.open(player, CORE));
        t.onServer(5, SdCardTour::fill);
        t.onServer(5, SecurityOs::close);
    }

    private static void openDesktop(DevScript t) {
        t.stand(0, CORE.getX() + 0.5, CORE.getY(), CORE.getZ() + 2.5, 180, 20);
        t.onServer(10, player -> SecurityOs.open(player, CORE));
        t.waitUntil(60, () -> Minecraft.getInstance().screen instanceof SecurityOsScreen);
        t.step(5, () -> HowToTour.mouseTo(1, 1));
        t.step(5, () -> os(SecurityOsScreen::tourCloseAll));
        t.step(0, () -> Minecraft.getInstance().getToasts().clear());
    }

    /** Het hele scherm als plaatje voor How it works (de desktop vult het hele venster). */
    private static void capture(String name) {
        Minecraft mc = Minecraft.getInstance();
        HowToTour.capture(name, 0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    /**
     * Een kern van jou met een wachtwoord en een rack, een computer met monitor ernaast, je programma's op de server, een
     * notitie in je map, en een lege kaart van 32 GB in de kern en een van 8 GB in de computer.
     */
    private static void stage(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : new BlockPos[] {CORE, RACK, PC, PC.east(), PC.above()}) {
            if (level.getBlockEntity(pos) instanceof com.senne.imttech.sdcard.SdCardHolder holder) {
                holder.takeSdCard(); // een vorige rondleiding: geen kaart laten vallen
            }
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(CORE, ModBlocks.SECURITY_CORE.get().defaultBlockState()
                .setValue(SecurityCoreBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(CORE) instanceof SecurityCoreBlockEntity core) {
            core.setOwner(player);
            core.ensureSystem(level);
            core.setPassword("bitech1", level.random);
            home = "/home/" + SystemDefaults.homeName(player.getGameProfile().getName());
            core.fs().mkdirs(home);
            core.fs().write(home + "/plan.txt", "Sell the strip miner for 10 diamonds.");
            core.logic().wipe();
            put(core, "Lights", LIGHTS, ProgramKind.SERVER);
            put(core, "Mine car", MINE_CAR, ProgramKind.CAR);
            put(core, "Door bell", DOOR_BELL, ProgramKind.SERVER);
            put(core, "Gate", GATE, ProgramKind.SERVER);
            core.putSdCard(new ItemStack(ModItems.SD_CARD_32GB.get()), player.getUUID());
        }
        for (BlockPos generator : new BlockPos[] {CORE.below(), RACK.below(), PC.below()}) {
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
        level.setBlockAndUpdate(PC, ModBlocks.COMPUTER.get().defaultBlockState().setValue(ComputerBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(PC.above(), ModBlocks.MONITOR.get().defaultBlockState().setValue(MonitorBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(PC) instanceof ComputerBlockEntity computer) {
            computer.setName("Reception");
            computer.setOwner(player);
            computer.putSdCard(new ItemStack(ModItems.SD_CARD_8GB.get()), player.getUUID());
            computer.updateOn(level);
        }
    }

    /** Een programma direct op de server zetten (zoals Save in Logic, maar zonder de desktop). */
    private static void put(SecurityCoreBlockEntity core, String name, String code, ProgramKind kind) {
        core.logic().put(null, new LogicProgram(name, true, GraphConverter.toGraph(ScriptParser.parse(code, kind), null), code, kind));
    }

    /**
     * De kaart vullen zoals een speler dat doet (met de opdrachten van de desktop): Mine car op slot, Door bell open, Gate
     * op slot en daarna van de kaart geïnstalleerd (zo staat er een programma op slot op je server), een notitie en een map.
     */
    private static void fill(ServerPlayer player) {
        op(player, "sd_copy", "name", "Mine car", true);
        op(player, "sd_copy", "name", "Door bell", false);
        op(player, "sd_copy", "name", "Gate", true);
        op(player, "logic_delete", "name", "Gate", false);
        op(player, "sd_install", "name", "Gate", false);
        CompoundTag args = new CompoundTag();
        args.putString("from", home + "/plan.txt");
        args.putString("to", "");
        SecurityOs.handleAction(player, new OsActionPayload(CORE, "fs_copy", args));
        args = new CompoundTag();
        args.putString("path", "sd:/readme.txt");
        args.putString("text", "Mine car: load it onto your RC Car and call dig() with the RC Controller.");
        SecurityOs.handleAction(player, new OsActionPayload(CORE, "fs_touch", args));
        SecurityOs.handleAction(player, new OsActionPayload(CORE, "fs_save", args));
        args = new CompoundTag();
        args.putString("path", "sd:/notes");
        SecurityOs.handleAction(player, new OsActionPayload(CORE, "fs_mkdir", args));
    }

    private static void op(ServerPlayer player, String op, String key, String value, boolean locked) {
        CompoundTag args = new CompoundTag();
        args.putString(key, value);
        args.putBoolean("locked", locked);
        SecurityOs.handleAction(player, new OsActionPayload(CORE, op, args));
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
