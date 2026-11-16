package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.car.RcCarData;
import com.senne.imttech.car.RcCarEntity;
import com.senne.imttech.car.RcCars;
import com.senne.imttech.computer.ComputerBlock;
import com.senne.imttech.computer.ComputerBlockEntity;
import com.senne.imttech.computer.Computers;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.energy.Cables;
import com.senne.imttech.logic.program.GraphConverter;
import com.senne.imttech.logic.program.LogicProgram;
import com.senne.imttech.logic.program.ProgramKind;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.network.OsActionPayload;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.robot.RobotData;
import com.senne.imttech.sdcard.SdCardData;
import com.senne.imttech.sdcard.SdCardItem;
import com.senne.imttech.sdcard.SdCards;
import com.senne.imttech.sdcard.SdDesktop;
import com.senne.imttech.security.SecurityCoreBlock;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.DesktopRights;
import com.senne.imttech.security.os.EventLog;
import com.senne.imttech.security.os.SecurityOs;
import com.senne.imttech.security.os.SystemDefaults;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * Hoofdstuk 130: de SD-kaart (jouw wens: "zorg dat je programs ook op een sd card kunt zetten, deze kun je in je security
 * core zetten of op een desktop aansluiten, zo kun je makklijk securiy files/programs doorgeven aan ander").
 * <p>
 * Alle tests staan in bestaande batches ("security_os", "computers" en "robot_upload"), dus er blijven 192 batchnamen.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class SdCardGameTests {
    private SdCardGameTests() {}

    private static final String ARENA = "empty";
    /** Een RC program om te verkopen (met een "geheim" stukje dat nooit naar het spel van de koper mag). */
    private static final String MINE_CODE = "# secret sauce 4711\ndef dig():\n    mine()\n    forward(1)\n";
    /** Een diagram op de server. */
    private static final String LIGHTS_CODE = "when every(5):\n    log(\"lights\")\n";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /**
     * Een programma bewaren zoals de app Logic dat doet. De blokjes zonder de controle (die doet de server, met de global
     * scripts van die kern: een programma dat een global def gebruikt, kent de app ook alleen via die scripts).
     */
    private static void save(ServerPlayer player, BlockPos coreAbs, String old, String name, String code, ProgramKind kind) {
        CompoundTag args = new CompoundTag();
        args.putString("old", old);
        args.putString("name", name);
        com.senne.imttech.logic.program.Library library = kind != ProgramKind.CAR
                && player.serverLevel().getBlockEntity(coreAbs) instanceof SecurityCoreBlockEntity core
                ? com.senne.imttech.logic.program.Library.of(core.logic().programs(), old.isEmpty() ? name : old)
                : com.senne.imttech.logic.program.Library.EMPTY;
        args.put("graph", GraphConverter.toGraph(ScriptParser.parseUnchecked(code, kind, library), null).save());
        args.putString("source", code);
        args.putString("kind", kind.name());
        SecurityOs.handleAction(player, new OsActionPayload(coreAbs, "logic_save", args));
    }

    /** Rechtsklik op dit blok met wat je vasthoudt, de echte weg (zoals het spel hem gaat, ook met sneaken). */
    private static void click(ServerPlayer player, BlockPos abs, boolean sneak) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.SOUTH, abs, false);
        player.setShiftKeyDown(sneak);
        player.gameMode.useItemOn(player, player.serverLevel(), player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        player.setShiftKeyDown(false);
    }

    /** Wat er op de kaart in dit blok staat (null = een lege kaart, of geen kaart). */
    private static @Nullable SdCards.Card card(ServerLevel level, com.senne.imttech.sdcard.SdCardHolder holder) {
        SdDesktop.View view = SdDesktop.view(level, holder);
        return view == null ? null : view.card();
    }

    // ================================================================ Erin en eruit

    /**
     * Rechtsklik met een kaart steekt hem in de kern (hij is uit je hand en de kern toont een kaartje). Een tweede kaart
     * past er niet bij; een vreemde haalt hem er niet uit en steekt er ook geen in; de eigenaar haalt hem eruit met sneak
     * en een lege hand (in zijn hand). Breekt de kern, dan valt de kaart er precies één keer uit.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "security_os")
    public static void sd_card_goes_in_and_out_of_a_core(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 3, 4), "sd-slot-owner");
        ServerPlayer stranger = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 3, 4), "sd-slot-stranger");
        BlockPos corePos = new BlockPos(2, 3, 2);
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, corePos, owner);
        BlockPos abs = helper.absolutePos(corePos);

        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SD_CARD_8GB.get()));
        click(owner, abs, false);
        check(SdCardItem.isCard(core.sdCard()), "De kaart zit niet in de kern");
        check(owner.getMainHandItem().isEmpty(), "De kaart is niet uit je hand");
        check(level.getBlockState(abs).getValue(SecurityCoreBlock.CARD), "De kern toont geen kaartje");
        check(!SecurityOs.hasDesktop(owner), "Met een kaart in je hand ging de desktop open");

        owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SD_CARD_32GB.get()));
        click(owner, abs, false);
        check(owner.getMainHandItem().is(ModItems.SD_CARD_32GB.get()) && core.sdCard().is(ModItems.SD_CARD_8GB.get()),
                "Er ging een tweede kaart in de kern");
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        click(stranger, abs, true);
        check(core.sdCard().is(ModItems.SD_CARD_8GB.get()), "Een vreemde haalde de kaart uit jouw kern");

        click(owner, abs, true);
        check(owner.getMainHandItem().is(ModItems.SD_CARD_8GB.get()), "De kaart kwam niet in je hand");
        check(core.sdCard().isEmpty() && !level.getBlockState(abs).getValue(SecurityCoreBlock.CARD), "De kern heeft hem nog");

        stranger.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SD_CARD_8GB.get()));
        click(stranger, abs, false);
        check(core.sdCard().isEmpty() && SdCardItem.isCard(stranger.getMainHandItem()), "Een vreemde stak een kaart in jouw kern");

        click(owner, abs, false);
        check(!core.sdCard().isEmpty(), "De kaart ging niet opnieuw in de kern");
        level.destroyBlock(abs, true);
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(3));
        long cards = drops.stream().filter(drop -> SdCardItem.isCard(drop.getItem())).count();
        check(cards == 1, "Na het breken van de kern lagen er " + cards + " kaarten in plaats van 1");
        drops.forEach(Entity::discard);
        helper.succeed();
    }

    // ================================================================ Programma's doorgeven

    /**
     * Een verkoper zet een RC program en een diagram op een kaart; de koper steekt de kaart in zijn kern en installeert ze.
     * Het RC program is precies hetzelfde (open: met zijn code), het diagram staat uit, en wie het nog eens installeert,
     * krijgt "Mine car 2". De samenvatting op de kaart klopt.
     */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "security_os")
    public static void sd_card_carries_programs_to_another_core(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer seller = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 3, 4), "sd-carry-seller");
        ServerPlayer buyer = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(18, 3, 18), "sd-carry-buyer");
        SecurityCoreBlockEntity coreA = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), seller);
        BlockPos rackA = new BlockPos(4, 3, 2);
        ImtTechGameTests.placeServer(helper, rackA, coreA);
        SecurityCoreBlockEntity coreB = ImtTechGameTests.placeCore(helper, new BlockPos(20, 3, 20), buyer);
        BlockPos rackB = new BlockPos(18, 3, 20);
        ImtTechGameTests.placeServer(helper, rackB, coreB);
        BlockPos absA = coreA.getBlockPos();
        BlockPos absB = coreB.getBlockPos();
        helper.runAfterDelay(3, () -> {
            ImtTechGameTests.startServer(helper, rackA, coreA);
            ImtTechGameTests.startServer(helper, rackB, coreB);
            SecurityOs.open(seller, absA);
            SecurityOs.open(buyer, absB);
            save(seller, absA, "", "Mine car", MINE_CODE, ProgramKind.CAR);
            save(seller, absA, "", "Lights", LIGHTS_CODE, ProgramKind.SERVER);
            check(coreA.logic().program("Mine car") != null && coreA.logic().program("Lights") != null,
                    "De programma's van de verkoper werden niet bewaard");
            coreA.putSdCard(new ItemStack(ModItems.SD_CARD_8GB.get()), seller.getUUID());
            ImtTechGameTests.osAction(seller, absA, "sd_copy", "name", "Mine car", "locked", "false");
            ImtTechGameTests.osAction(seller, absA, "sd_copy", "name", "Lights", "locked", "false");
            SdCards.Card onCard = card(level, coreA);
            check(onCard != null && onCard.programs().size() == 2, "Er staan geen 2 programma's op de kaart");
            SdCardData summary = SdCardItem.data(coreA.sdCard());
            check(summary != null && summary.programs() == 2 && summary.locked() == 0, "De samenvatting op de kaart klopt niet: " + summary);

            // De kaart gaat mee naar de koper.
            coreB.putSdCard(coreA.takeSdCard(), buyer.getUUID());
            ImtTechGameTests.osAction(buyer, absB, "sd_install", "name", "Mine car");
            LogicProgram car = coreB.logic().program("Mine car");
            check(car != null && car.kind() == ProgramKind.CAR && car.source().equals(MINE_CODE) && !car.locked(),
                    "Het RC program kwam niet goed op de server van de koper: " + car);
            ImtTechGameTests.osAction(buyer, absB, "sd_install", "name", "Lights");
            LogicProgram lights = coreB.logic().program("Lights");
            check(lights != null && !lights.enabled(), "Een geïnstalleerd diagram zou uit moeten staan: " + lights);
            ImtTechGameTests.osAction(buyer, absB, "sd_install", "name", "Mine car");
            check(coreB.logic().program("Mine car 2") != null, "Nog eens installeren gaf geen Mine car 2");
            check(coreB.log().entries().stream().anyMatch(entry -> entry.text().contains("installed the program \"Mine car\"")),
                    "Het logboek van de koper zegt niet dat er geïnstalleerd werd");
            helper.succeed();
        });
    }

    /**
     * Op slot (jouw wens: "je kunt de code niet zien anders zou je die kunnen overnemen"): de koper installeert het en kan
     * het aanzetten, maar zijn spel krijgt de code nooit (ook niet de blokjes), Save over het programma lukt niet, en het
     * kan niet weer op een kaart. De code staat wel op de server (die moet het uitvoeren). Een programma op slot in het
     * item van een robot gaat zonder code over het netwerk.
     */
    @GameTest(template = ARENA, timeoutTicks = 80, batch = "security_os")
    public static void locked_program_is_used_but_never_read(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer seller = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 3, 4), "sd-lock-seller");
        ServerPlayer buyer = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(18, 3, 18), "sd-lock-buyer");
        SecurityCoreBlockEntity coreA = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), seller);
        BlockPos rackA = new BlockPos(4, 3, 2);
        ImtTechGameTests.placeServer(helper, rackA, coreA);
        SecurityCoreBlockEntity coreB = ImtTechGameTests.placeCore(helper, new BlockPos(20, 3, 20), buyer);
        BlockPos rackB = new BlockPos(18, 3, 20);
        ImtTechGameTests.placeServer(helper, rackB, coreB);
        BlockPos absA = coreA.getBlockPos();
        BlockPos absB = coreB.getBlockPos();
        String gate = "# secret sauce 4711\nwhen every(5):\n    log(\"tick\")\n";
        helper.runAfterDelay(3, () -> {
            ImtTechGameTests.startServer(helper, rackA, coreA);
            ImtTechGameTests.startServer(helper, rackB, coreB);
            SecurityOs.open(seller, absA);
            SecurityOs.open(buyer, absB);
            save(seller, absA, "", "Gate", gate, ProgramKind.SERVER);
            coreA.putSdCard(new ItemStack(ModItems.SD_CARD_8GB.get()), seller.getUUID());
            ImtTechGameTests.osAction(seller, absA, "sd_copy", "name", "Gate", "locked", "true");
            SdCards.Card onCard = card(level, coreA);
            LogicProgram packed = onCard == null ? null : onCard.program("Gate");
            check(packed != null && packed.locked() && packed.author().equals("sd-lock-seller"),
                    "Het programma staat niet op slot op de kaart, met de naam van de verkoper: " + packed);
            // Wat het scherm van de kaart krijgt (de lijst van Logic en Files): nooit code.
            CompoundTag sellerView = SdDesktop.snapshot(SdDesktop.view(level, coreA));
            check(sellerView.getList("p", net.minecraft.nbt.Tag.TAG_COMPOUND).size() == 1, "De lijst van de kaart is leeg (de test bewijst dan niets)");
            check(!sellerView.toString().contains("secret sauce"), "De lijst van de kaart bevat code");

            coreB.putSdCard(coreA.takeSdCard(), buyer.getUUID());
            // Een programma op slot kun je niet lezen voor je het installeert (sd_view krijgt geen code).
            check(SdDesktop.code(SdDesktop.view(level, coreB), "Gate") == null,
                    "De code van een programma op slot zou te lezen zijn voor de koper");
            // Een programma op slot dat een def van een global script van de maker gebruikt: zonder dat global script past
            // het niet, en de melding noemt de def niet (die is geheim), wel het global script van de kaart (dat is open).
            save(seller, absA, "", "Utils", "global\n\ndef secret_helper():\n    log(\"x\")\n", ProgramKind.SERVER);
            save(seller, absA, "", "Patrol", "when started:\n    secret_helper()\n", ProgramKind.ROBOT);
            coreB.takeSdCard().copy();
            ItemStack card = new ItemStack(ModItems.SD_CARD_8GB.get());
            coreA.putSdCard(card, seller.getUUID());
            ImtTechGameTests.osAction(seller, absA, "sd_copy", "name", "Gate", "locked", "true");
            ImtTechGameTests.osAction(seller, absA, "sd_copy", "name", "Utils", "locked", "false");
            ImtTechGameTests.osAction(seller, absA, "sd_copy", "name", "Patrol", "locked", "true");
            coreB.putSdCard(coreA.takeSdCard(), buyer.getUUID());
            SdDesktop.Result refused = SdDesktop.install(buyer, coreB, SdDesktop.view(level, coreB), "Patrol");
            check(refused.error() && refused.text() != null && refused.text().contains("Utils") && !refused.text().contains("secret_helper"),
                    "De melding bij een programma op slot dat niet past, klopt niet: " + refused.text());
            ImtTechGameTests.osAction(buyer, absB, "sd_install", "name", "Utils");
            ImtTechGameTests.osAction(buyer, absB, "sd_install", "name", "Patrol");
            check(coreB.logic().program("Patrol") != null && coreB.logic().program("Patrol").locked(),
                    "Met het global script erbij kon het programma op slot nog altijd niet");
            ImtTechGameTests.osAction(buyer, absB, "sd_install", "name", "Gate");
            LogicProgram installed = coreB.logic().program("Gate");
            check(installed != null && installed.locked() && installed.source().contains("secret sauce"),
                    "Het programma op slot staat niet (met zijn code) op de server van de koper: " + installed);
            // Aanzetten mag.
            ImtTechGameTests.osAction(buyer, absB, "logic_enable", "name", "Gate", "on", "true");
            check(coreB.logic().program("Gate").enabled() && coreB.logic().program("Gate").locked(),
                    "Aanzetten lukte niet, of het programma is niet meer op slot");
            // Wat het scherm van de koper van zijn programma's krijgt (een ander programma erbij verandert de lijst, zodat de
            // volgende momentopname hem opnieuw stuurt: het aanzetten stuurde hem al met zijn melding).
            coreB.logic().put(null, new LogicProgram("Other", false,
                    GraphConverter.toGraph(ScriptParser.parse(LIGHTS_CODE), null), LIGHTS_CODE));
            CompoundTag buyerView = SecurityOs.snapshot(coreB, level, SecurityOs.session(buyer), true);
            check(buyerView.contains("logic"), "De koper kreeg de nieuwe lijst met programma's niet (de test bewijst dan niets)");
            String sent = buyerView.getCompound("logic").toString();
            check(sent.contains("Gate") && sent.contains("Locked"), "De koper ziet het programma op slot niet in zijn lijst");
            check(!sent.contains("secret sauce") && !sent.contains("tick"), "De code of de blokjes gingen naar het spel van de koper");
            // Save over het programma op slot: nee.
            save(buyer, absB, "Gate", "Gate", "when every(5):\n    log(\"mine now\")\n", ProgramKind.SERVER);
            check(coreB.logic().program("Gate").source().contains("secret sauce"), "Save veranderde een programma op slot");
            // En niet weer op een kaart.
            coreB.takeSdCard();
            coreB.putSdCard(new ItemStack(ModItems.SD_CARD_8GB.get()), buyer.getUUID());
            ImtTechGameTests.osAction(buyer, absB, "sd_copy", "name", "Gate", "locked", "false");
            check(card(level, coreB) == null, "Een programma op slot ging weer op een kaart");
            // Een robot met dit programma: zijn item draagt alleen een verwijzing (nooit de code, ook niet zoals het item
            // bewaard wordt: dat gaat soms ook naar anderen, bv. op een holotafel), en neergezet haalt de server het terug.
            CompoundTag onItem = com.senne.imttech.logic.LockedPrograms.forItem(level, installed.save());
            RobotData robot = new RobotData(RobotData.DEFAULT.parts(), RobotData.DEFAULT.board(), "Bob", 1.0f, onItem, 0, 1.0f);
            String stored = RobotData.CODEC.encodeStart(NbtOps.INSTANCE, robot).getOrThrow().toString();
            var buf = Unpooled.buffer();
            RobotData.STREAM_CODEC.encode(buf, robot);
            RobotData back = RobotData.STREAM_CODEC.decode(buf);
            check(back.program().getBoolean("Locked") && back.program().getString("Name").equals("Gate")
                    && !back.program().getString("Ref").isEmpty(), "De robot kent zijn programma op slot niet meer na het netwerk");
            check(!stored.contains("secret sauce") && !stored.contains("tick")
                    && !back.program().toString().contains("secret sauce") && !back.program().toString().contains("tick"),
                    "Het item van een robot draagt de code van een programma op slot");
            CompoundTag restored = com.senne.imttech.logic.LockedPrograms.fromItem(level, back.program());
            check(LogicProgram.load(restored).source().contains("secret sauce") && LogicProgram.load(restored).locked(),
                    "Neergezet kreeg de robot zijn programma op slot niet terug van de server");
            helper.succeed();
        });
    }

    // ================================================================ Bestanden

    /**
     * Files op de kaart: een notitie erop kopiëren (en een map), maar nooit security.conf; mappen en bestanden op de kaart
     * maken, bewaren en hernoemen; een valstrik op de kaart kan niet; terug naar je systeem kopiëren; de kaart van 8 GB is
     * vol na 8 bestanden. Iemand die niet de eigenaar is en een valstrik kopieert, laat hem afgaan.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "security_os")
    public static void sd_card_files_copy_without_the_secrets(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 3, 4), "sd-files-owner");
        ServerPlayer guest = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(5, 3, 4), "sd-files-guest");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        core.ensureSystem(level);
        core.powerTick(level);
        BlockPos abs = core.getBlockPos();
        String home = "/home/" + SystemDefaults.homeName("sd-files-owner");
        core.fs().mkdirs(home);
        check(core.fs().write(home + "/plan.txt", "the plan") == null, "De notitie van de test kon niet op het systeem");
        core.putSdCard(new ItemStack(ModItems.SD_CARD_8GB.get()), owner.getUUID());
        SecurityOs.open(owner, abs);

        // Een valstrik eerst: een ander die hem kopieert, laat hem afgaan (en hij komt niet op de kaart).
        check(core.fs().write(home + "/bait.txt", "x") == null && core.fs().setTrap(home + "/bait.txt", true) == null,
                "De valstrik van de test lukte niet");
        SecurityOs.openFor(guest, core, false);
        ImtTechGameTests.osAction(guest, abs, "fs_copy", "from", home + "/bait.txt", "to", "");
        check(!SecurityOs.hasDesktop(guest), "De valstrik ging niet af bij kopiëren");
        check(card(level, core) == null, "De valstrik kwam op de kaart");
        check(core.log().entries().stream().anyMatch(entry -> entry.type() == EventLog.Type.TRAP), "Geen valstrik in het logboek");

        ImtTechGameTests.osAction(owner, abs, "fs_copy", "from", home + "/plan.txt", "to", "");
        SdCards.Card onCard = card(level, core);
        check(onCard != null && onCard.files().get("/plan.txt") != null
                && onCard.files().get("/plan.txt").content().equals("the plan"), "De notitie kwam niet op de kaart");
        String conf = core.fs().systemFile();
        ImtTechGameTests.osAction(owner, abs, "fs_copy", "from", conf, "to", "");
        check(card(level, core).files().findByName("security.conf").isEmpty(), "security.conf kwam op de kaart");
        ImtTechGameTests.osAction(owner, abs, "fs_copy", "from", "/etc", "to", "");
        check(card(level, core).files().isDir("/etc") && card(level, core).files().findByName("security.conf").isEmpty(),
                "De map /etc kwam niet op de kaart, of security.conf kwam mee");

        // Op de kaart zelf.
        ImtTechGameTests.osAction(owner, abs, "fs_mkdir", "path", "sd:/notes");
        ImtTechGameTests.osAction(owner, abs, "fs_touch", "path", "sd:/notes/a.txt");
        ImtTechGameTests.osAction(owner, abs, "fs_save", "path", "sd:/notes/a.txt", "text", "hello");
        ImtTechGameTests.osAction(owner, abs, "fs_move", "from", "sd:/notes/a.txt", "to", "sd:/notes/b.txt");
        var files = card(level, core).files();
        check(files.get("/notes/b.txt") != null && files.get("/notes/b.txt").content().equals("hello") && files.get("/notes/a.txt") == null,
                "Maken, bewaren of hernoemen op de kaart lukte niet");
        ImtTechGameTests.osAction(owner, abs, "fs_trap", "path", "sd:/notes/b.txt", "on", "true");
        check(!card(level, core).files().get("/notes/b.txt").isTrap(), "Een bestand op de kaart werd een valstrik");
        check(core.fs().get(home + "/plan.txt") != null, "Iets op de kaart veranderde je systeem");

        // Terug naar je systeem.
        ImtTechGameTests.osAction(owner, abs, "fs_copy", "from", "sd:/notes/b.txt", "to", "/");
        check(core.fs().get("/b.txt") != null && core.fs().get("/b.txt").content().equals("hello"),
                "Van de kaart naar je systeem kopiëren lukte niet");

        // Vol: 8 bestanden op een kaart van 8 GB (plan.txt en b.txt staan er al).
        for (int i = 1; i <= 6; i++) {
            ImtTechGameTests.osAction(owner, abs, "fs_touch", "path", "sd:/f" + i + ".txt");
        }
        check(card(level, core).fileCount() == 8, "Er staan " + card(level, core).fileCount() + " bestanden op de kaart, niet 8");
        ImtTechGameTests.osAction(owner, abs, "fs_touch", "path", "sd:/f7.txt");
        check(card(level, core).fileCount() == 8, "Er ging een negende bestand op een kaart van 8 GB");
        SecurityOs.close(owner);
        helper.succeed();
    }

    // ================================================================ Hoeveel past erop, en bewaren

    /**
     * Een kaart van 8 GB neemt 4 programma's (de vijfde niet); alles (ook op slot en van wie) blijft bewaard na opnieuw
     * inlezen; het nummer van de kaart gaat heel over het netwerk en op de schijf; en een kaart waar niets meer op staat,
     * is weer een lege kaart.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "security_os")
    public static void sd_card_capacity_and_saving(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 3, 4), "sd-size-owner");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos rack = new BlockPos(4, 3, 2);
        ImtTechGameTests.placeServer(helper, rack, core);
        BlockPos abs = core.getBlockPos();
        helper.runAfterDelay(3, () -> {
            ImtTechGameTests.startServer(helper, rack, core);
            SecurityOs.open(owner, abs);
            for (int i = 1; i <= 5; i++) {
                save(owner, abs, "", "Clock " + i, "when every(" + (i + 4) + "):\n    log(\"c\")\n", ProgramKind.SERVER);
            }
            core.putSdCard(new ItemStack(ModItems.SD_CARD_8GB.get()), owner.getUUID());
            for (int i = 1; i <= 5; i++) {
                ImtTechGameTests.osAction(owner, abs, "sd_copy", "name", "Clock " + i, "locked", i == 1 ? "true" : "false");
            }
            SdCards.Card onCard = card(level, core);
            check(onCard != null && onCard.programs().size() == 4, "Er staan " + (onCard == null ? 0 : onCard.programs().size())
                    + " programma's op een kaart van 8 GB, niet 4");
            // Hetzelfde programma nog eens: dat vervangt (het past dus nog).
            ImtTechGameTests.osAction(owner, abs, "sd_copy", "name", "Clock 2", "locked", "false");
            check(card(level, core).programs().size() == 4, "Hetzelfde programma nog eens kwam er dubbel op");

            SdCardData data = SdCardItem.data(core.sdCard());
            check(data != null, "De kaart heeft geen nummer");
            SdCards reloaded = SdCards.reload(SdCards.get(level), level.registryAccess());
            SdCards.Card again = reloaded.card(data.id());
            check(again != null && again.programs().size() == 4 && again.program("Clock 1").locked()
                    && again.program("Clock 1").author().equals("sd-size-owner"), "Na opnieuw inlezen is de kaart niet meer hetzelfde");
            SdCardData parsed = SdCardData.CODEC.parse(NbtOps.INSTANCE, SdCardData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow())
                    .getOrThrow();
            var buf = Unpooled.buffer();
            SdCardData.STREAM_CODEC.encode(buf, data);
            check(parsed.equals(data) && SdCardData.STREAM_CODEC.decode(buf).equals(data), "Het nummer van de kaart ging niet heel mee");

            for (int i = 1; i <= 4; i++) {
                ImtTechGameTests.osAction(owner, abs, "sd_delete", "name", "Clock " + i);
            }
            check(SdCardItem.data(core.sdCard()) == null && SdCards.get(level).card(data.id()) == null,
                    "Een kaart zonder iets erop is niet weer leeg");
            SecurityOs.close(owner);
            helper.succeed();
        });
    }

    // ================================================================ In een computer

    private static void networkLine(GameTestHelper helper, BlockPos from, int toX) {
        for (int x = from.getX(); x <= toX; x++) {
            Cables.add(CableKind.NETWORK, helper.getLevel(), helper.absolutePos(new BlockPos(x, from.getY(), from.getZ())));
        }
    }

    /**
     * Een bezoeker steekt zijn kaart in een computer (iedereen mag die gebruiken; Logic alleen bekijken, Files bedienen).
     * Op die computer gebruikt de desktop de kaart in de kast, niet die in de kern. Programma's op de kaart zetten mag
     * niet (Logic is alleen kijken), een bestand kopiëren wel. Een andere bezoeker haalt de kaart er niet uit, wie hem
     * erin stak wel.
     */
    @GameTest(template = ARENA, timeoutTicks = 60, batch = "computers")
    public static void sd_card_in_a_computer(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(2, 3, 6), "sd-pc-boss");
        ServerPlayer visitor = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(7, 3, 4), "sd-pc-visitor");
        ServerPlayer other = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(8, 3, 4), "sd-pc-other");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        core.ensureSystem(level);
        core.powerTick(level);
        BlockPos pcRel = new BlockPos(6, 3, 2);
        helper.setBlock(pcRel, ModBlocks.COMPUTER.get());
        helper.setBlock(pcRel.east(), ModBlocks.MONITOR.get());
        networkLine(helper, new BlockPos(3, 3, 2), 5);
        helper.setBlock(pcRel.above(), ModBlocks.HEAT_GENERATOR.get());
        ((com.senne.imttech.station.ChargerBlockEntity) helper.getBlockEntity(pcRel.above())).fill(20_000);
        ComputerBlockEntity computer = (ComputerBlockEntity) helper.getBlockEntity(pcRel);
        computer.setName("Reception");
        computer.updateOn(level);
        computer.setRights(computer.rights().with("LOGIC", DesktopRights.Access.VIEW).with("FILES", DesktopRights.Access.USE)
                .with("NOTES", DesktopRights.Access.USE));
        computer.setWho(ComputerBlockEntity.Who.EVERYONE, "");
        com.senne.imttech.security.SecurityDevices.forget(level, core);
        BlockPos pc = helper.absolutePos(pcRel);

        visitor.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.SD_CARD_32GB.get()));
        click(visitor, pc, false);
        check(SdCardItem.isCard(computer.sdCard()) && visitor.getMainHandItem().isEmpty(), "De bezoeker kreeg zijn kaart niet in de computer");
        check(level.getBlockState(pc).getValue(ComputerBlock.CARD), "De computer toont geen kaartje");
        check(core.sdCard().isEmpty(), "De kaart kwam in de kern in plaats van in de computer");

        Computers.use(visitor, pc.east());
        var session = SecurityOs.session(visitor);
        check(session != null && pc.equals(session.computer()), "De bezoeker zit niet op de computer");
        core.logic().put(null, new LogicProgram("Lights", true,
                GraphConverter.toGraph(ScriptParser.parse(LIGHTS_CODE), null), LIGHTS_CODE));
        ImtTechGameTests.osAction(visitor, core.getBlockPos(), "sd_copy", "name", "Lights", "locked", "false");
        check(card(level, computer) == null, "Met Logic op View zette de bezoeker toch een programma op de kaart");
        check(core.fs().write("/readme.txt", "welcome") == null, "Het bestand van de test lukte niet");
        // Zonder Notes mag je de tekst van een bestand niet lezen: dan ook niet kopiëren naar je kaart.
        computer.setRights(computer.rights().with("NOTES", DesktopRights.Access.OFF));
        ImtTechGameTests.osAction(visitor, core.getBlockPos(), "fs_copy", "from", "/readme.txt", "to", "");
        check(card(level, computer) == null, "Zonder Notes kopieerde de bezoeker toch de tekst van een bestand naar zijn kaart");
        computer.setRights(computer.rights().with("NOTES", DesktopRights.Access.USE));
        ImtTechGameTests.osAction(visitor, core.getBlockPos(), "fs_copy", "from", "/readme.txt", "to", "");
        SdCards.Card onCard = card(level, computer);
        check(onCard != null && onCard.files().get("/readme.txt") != null, "Met Files op Use kon de bezoeker geen bestand kopiëren");
        check(core.sdCard().isEmpty(), "De kern kreeg plots een kaart");

        click(other, pc, true);
        check(SdCardItem.isCard(computer.sdCard()), "Een andere bezoeker haalde de kaart eruit");
        click(visitor, pc, true);
        check(SdCardItem.isCard(visitor.getMainHandItem()) && computer.sdCard().isEmpty(), "Wie hem erin stak, kreeg hem er niet uit");
        SecurityOs.close(visitor);
        helper.succeed();
    }

    // ================================================================ Op een RC Car

    /**
     * Een RC program op slot komt op een auto (met de knop Program op de auto) en draait daar; de auto weet dat het op
     * slot is. Opgepakt draagt zijn item de code (anders kon hij hem na neerzetten niet meer draaien), maar over het
     * netwerk gaat de code niet mee.
     */
    @GameTest(template = ARENA, timeoutTicks = 120, batch = "robot_upload")
    public static void locked_rc_program_runs_on_a_car_but_stays_secret(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "sd-car-owner");
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos rack = new BlockPos(12, 3, 12);
        ImtTechGameTests.placeServer(helper, rack, core);
        BlockPos abs = core.getBlockPos();
        RcCarEntity car = helper.spawn(ModEntities.RC_CAR.get(), new BlockPos(8, 2, 8));
        car.setOwner(owner);
        check(car.setSource("def nap():\n    wait(1)\n") == null, "De code van de test klopt niet");
        helper.runAfterDelay(25, () -> {
            ImtTechGameTests.startServer(helper, rack, core);
            SecurityOs.open(owner, abs);
            save(owner, abs, "", "Strip miner", MINE_CODE, ProgramKind.CAR);
            core.putSdCard(new ItemStack(ModItems.SD_CARD_8GB.get()), owner.getUUID());
            ImtTechGameTests.osAction(owner, abs, "sd_copy", "name", "Strip miner", "locked", "true");
            ImtTechGameTests.osAction(owner, abs, "logic_delete", "name", "Strip miner");
            ImtTechGameTests.osAction(owner, abs, "sd_install", "name", "Strip miner");
            LogicProgram installed = core.logic().program("Strip miner");
            check(installed != null && installed.locked() && installed.kind() == ProgramKind.CAR,
                    "Het RC program op slot werd niet geïnstalleerd: " + installed);
            RcCars.handleAction(owner, new com.senne.imttech.network.RcCarActionPayload(car.getId(),
                    com.senne.imttech.network.RcCarActionPayload.Op.LOAD, "Strip miner"));
            check(car.programName().equals("Strip miner") && car.programLocked() && car.functions().equals(List.of("dig")),
                    "Het RC program op slot kwam niet (op slot) op de auto: " + car.programName() + " " + car.functions());
            RcCarData data = car.toData();
            check(data.programLocked() && data.source().isEmpty(), "Het item van de auto draagt de code van een programma op slot");
            String stored = RcCarData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow().toString();
            check(!stored.contains("secret sauce") && !stored.contains("mine()"),
                    "Zoals het item van de auto bewaard wordt (en soms naar anderen gaat), staat de code erin");
            RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
            RcCarData.STREAM_CODEC.encode(buf, data);
            RcCarData back = RcCarData.STREAM_CODEC.decode(buf);
            check(back.source().isEmpty() && back.programLocked() && back.program().equals("Strip miner"),
                    "Het item van de auto stuurt de code van een programma op slot naar je spel");
            // Neergezet uit dat item (ook zoals het van je spel terugkomt, bv. in creative): hij draait het nog, op slot.
            RcCarEntity copy = ModEntities.RC_CAR.get().create(level);
            copy.applyData(back);
            check(copy.programLocked() && copy.functions().equals(List.of("dig")) && copy.source().contains("mine()"),
                    "Weer neergezet kent hij zijn programma op slot niet");
            SecurityOs.close(owner);
            helper.succeed();
        });
    }
}
