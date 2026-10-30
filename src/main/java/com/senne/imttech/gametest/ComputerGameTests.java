package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.computer.ComputerBlock;
import com.senne.imttech.computer.ComputerBlockEntity;
import com.senne.imttech.computer.Computers;
import com.senne.imttech.computer.MonitorBlock;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.CableKind;
import com.senne.imttech.energy.Cables;
import com.senne.imttech.lan.LanGrid;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityDevices;
import com.senne.imttech.security.os.EventLog;
import com.senne.imttech.security.os.SecurityOs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor hoofdstuk 40 (jouw wens van 30 oktober): de Hacking Laptop als laptop voor op afstand (inloggen met IP en
 * wachtwoord, zoals SSH), en de Computer met Monitor waarvan je op de kern instelt wat erop mag.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class ComputerGameTests {
    private ComputerGameTests() {}

    private static final String ARENA = "empty";

    /** Vóór de tests van deze batch: beveiligingsapparaten hebben een netwerkkabel nodig (zoals in een echte wereld). */
    @net.minecraft.gametest.framework.BeforeBatch(batch = "computers")
    public static void networkOn(ServerLevel level) {
        LanGrid.overrideRequired(true);
    }

    /** Daarna weer de oude regel, voor de andere tests. */
    @net.minecraft.gametest.framework.AfterBatch(batch = "computers")
    public static void networkOff(ServerLevel level) {
        LanGrid.overrideRequired(null);
    }

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    /** Legt een netwerkkabel langs een rechte lijn op de x-as, van {@code from} tot en met {@code to}. */
    private static void networkLine(GameTestHelper helper, BlockPos from, int toX) {
        for (int x = from.getX(); x <= toX; x++) {
            Cables.add(CableKind.NETWORK, helper.getLevel(), helper.absolutePos(new BlockPos(x, from.getY(), from.getZ())));
        }
    }

    /** Een volle warmtegenerator op deze plek (stroom voor wat ernaast staat). */
    private static void generator(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.HEAT_GENERATOR.get());
        ((com.senne.imttech.station.ChargerBlockEntity) helper.getBlockEntity(pos)).fill(20_000);
    }

    /** Een kern met stroom en een systeem (IP), van deze eigenaar, die weet dat hij stroom heeft. */
    private static SecurityCoreBlockEntity poweredCore(GameTestHelper helper, BlockPos pos, ServerPlayer owner) {
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, pos, owner);
        core.ensureSystem(helper.getLevel());
        core.powerTick(helper.getLevel());
        return core;
    }

    private static boolean hasLog(SecurityCoreBlockEntity core, EventLog.Type type, String text) {
        return core.log().entries().stream().anyMatch(entry -> entry.type() == type && entry.text().contains(text));
    }

    /**
     * Inloggen met de laptop (stap 1): zonder laptop, zonder wachtwoord op de kern, met een onbekend IP of een kern zonder
     * stroom lukt het niet. Een fout wachtwoord staat in het logboek; het goede opent de desktop "op afstand" en de laptop
     * onthoudt het IP. Iemand anders met het wachtwoord komt er ook in (en dat staat in het logboek); na te veel foute
     * wachtwoorden moet je wachten, ook met het goede.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "computers")
    public static void laptop_logs_in_with_ip_and_password(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(2, 3, 2), "laptop-owner");
        SecurityCoreBlockEntity core = poweredCore(helper, new BlockPos(12, 3, 12), owner);
        String ip = core.ip();

        // Zonder laptop: niets.
        SecurityOs.login(owner, ip, "abcd12");
        if (SecurityOs.hasDesktop(owner)) {
            throw new GameTestAssertException("Inloggen van ver lukte zonder Hacking Laptop");
        }
        ItemStack laptop = new ItemStack(ModItems.HACKING_LAPTOP.get());
        owner.getInventory().add(laptop);
        // Een kern zonder wachtwoord laat niemand van ver binnen, ook de eigenaar niet.
        SecurityOs.login(owner, ip, "abcd12");
        if (SecurityOs.hasDesktop(owner)) {
            throw new GameTestAssertException("Een kern zonder wachtwoord liet iemand van ver binnen");
        }
        core.setPassword("abcd12", level.random);
        // Een onbekend IP en een fout wachtwoord: niets, en de foute poging staat in het logboek.
        SecurityOs.login(owner, "10.0.0.0", "abcd12");
        SecurityOs.login(owner, ip, "wrong1");
        if (SecurityOs.hasDesktop(owner) || !hasLog(core, EventLog.Type.LOGIN_FAIL, "laptop-owner")) {
            throw new GameTestAssertException("Een fout wachtwoord of IP opende de desktop, of de poging staat niet in het logboek");
        }
        // Het goede wachtwoord: de desktop op afstand, en de laptop onthoudt het IP.
        SecurityOs.login(owner, ip, "abcd12");
        var session = SecurityOs.session(owner);
        if (session == null || !session.remote() || !session.core().equals(core.getBlockPos())) {
            throw new GameTestAssertException("Het goede wachtwoord opende de desktop op afstand niet");
        }
        String remembered = null;
        for (int i = 0; i < owner.getInventory().getContainerSize(); i++) {
            if (owner.getInventory().getItem(i).is(ModItems.HACKING_LAPTOP.get())) {
                remembered = owner.getInventory().getItem(i).get(ModDataComponents.LAST_IP.get());
            }
        }
        if (!ip.equals(remembered)) {
            throw new GameTestAssertException("De laptop onthield het IP niet, maar " + remembered);
        }
        SecurityOs.close(owner);

        // Iemand anders die het wachtwoord kent, komt er ook in (zo koos je het: wie het wachtwoord kent, mag erin).
        ServerPlayer friend = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(4, 3, 2), "laptop-friend");
        friend.getInventory().add(new ItemStack(ModItems.HACKING_LAPTOP.get()));
        SecurityOs.login(friend, ip, "abcd12");
        if (!SecurityOs.hasDesktop(friend) || !hasLog(core, EventLog.Type.LOGIN, "laptop-friend logged in remotely")) {
            throw new GameTestAssertException("Iemand met het wachtwoord kwam er niet in, of het staat niet in het logboek");
        }
        SecurityOs.close(friend);

        // Te veel foute wachtwoorden: daarna even wachten, ook met het goede.
        ServerPlayer guesser = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 2), "laptop-guesser");
        guesser.getInventory().add(new ItemStack(ModItems.HACKING_LAPTOP.get()));
        for (int i = 0; i < Math.round(ImtConfig.Security.PASSWORD_ATTEMPTS); i++) {
            SecurityOs.login(guesser, ip, "guess" + i);
        }
        SecurityOs.login(guesser, ip, "abcd12");
        if (SecurityOs.hasDesktop(guesser)) {
            throw new GameTestAssertException("Na te veel foute wachtwoorden mocht het meteen opnieuw");
        }

        // Een kern zonder stroom antwoordt niet.
        BlockPos darkPos = new BlockPos(20, 3, 20);
        helper.setBlock(darkPos, ModBlocks.SECURITY_CORE.get());
        SecurityCoreBlockEntity dark = (SecurityCoreBlockEntity) helper.getBlockEntity(darkPos);
        dark.setOwner(owner);
        dark.ensureSystem(level);
        dark.setPassword("abcd12", level.random);
        dark.powerTick(level);
        SecurityOs.login(owner, dark.ip(), "abcd12");
        if (dark.isOnline() || SecurityOs.hasDesktop(owner)) {
            throw new GameTestAssertException("Een kern zonder stroom liet iemand van ver binnen");
        }
        helper.succeed();
    }

    /**
     * De Computer en de Monitor (stap 2): zonder netwerkkabel naar de kern werkt de computer niet ("no network"), met kabel
     * maar zonder stroom ook niet ("no power"); met allebei brandt hij, licht de monitor ertegen op en staat hij in de lijst
     * van de kern. Een nieuwe computer heet "Computer 1". Haal je de computer weg, dan gaat het scherm uit.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "computers")
    public static void computer_needs_network_and_power_and_lights_its_monitor(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(2, 3, 6), "pc-owner");
        SecurityCoreBlockEntity core = poweredCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos computer = new BlockPos(6, 3, 2);
        BlockPos monitor = new BlockPos(7, 3, 2);
        helper.setBlock(computer, ModBlocks.COMPUTER.get());
        helper.setBlock(monitor, ModBlocks.MONITOR.get());
        BlockPos computerAbs = helper.absolutePos(computer);
        ComputerBlockEntity entity = (ComputerBlockEntity) helper.getBlockEntity(computer);
        check("Computer 1".equals(Computers.freeName(level, computerAbs)), "Een nieuwe computer heet niet Computer 1");

        // Zonder netwerkkabel: uit.
        entity.updateOn(level);
        helper.assertBlockProperty(computer, ComputerBlock.ON, false);
        check(Computers.problem(level, computerAbs) != null && Computers.problem(level, computerAbs).getString().contains("No network"),
                "Zonder kabel zegt de computer: " + Computers.problem(level, computerAbs));

        // Met netwerkkabel, zonder stroom: nog steeds uit.
        networkLine(helper, new BlockPos(3, 3, 2), 5);
        entity.updateOn(level);
        helper.assertBlockProperty(computer, ComputerBlock.ON, false);
        check(entity.core(level) == core, "De computer hangt niet aan de kern");
        check(Computers.problem(level, computerAbs) != null && Computers.problem(level, computerAbs).getString().contains("no power"),
                "Zonder stroom zegt de computer: " + Computers.problem(level, computerAbs));

        // Met stroom: aan, de monitor licht op, en de kern kent hem.
        generator(helper, computer.above());
        entity.updateOn(level);
        helper.assertBlockProperty(computer, ComputerBlock.ON, true);
        helper.assertBlockProperty(monitor, MonitorBlock.ON, true);
        check(Computers.problem(level, computerAbs) == null, "Een werkende computer heeft toch een probleem");
        SecurityDevices.forget(level, core);
        check(SecurityDevices.scan(level, core).computers().contains(computerAbs), "De kern kent zijn computer niet");

        // De computer weg: het scherm gaat uit.
        helper.setBlock(computer, Blocks.AIR);
        helper.assertBlockProperty(monitor, MonitorBlock.ON, false);
        helper.succeed();
    }

    /** Een opdracht van het scherm naar de server, alsof de speler op een knop klikte (de plek van een computer erbij). */
    private static void pcAction(ServerPlayer player, SecurityCoreBlockEntity core, BlockPos computer, String op,
                                 java.util.function.Consumer<net.minecraft.nbt.CompoundTag> extra) {
        net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
        args.putInt("x", computer.getX());
        args.putInt("y", computer.getY());
        args.putInt("z", computer.getZ());
        extra.accept(args);
        SecurityOs.handleAction(player, new com.senne.imttech.network.OsActionPayload(core.getBlockPos(), op, args));
    }

    /** Een kern met een werkende computer (netwerkkabel en stroom) en een monitor ertegen. Geeft de plek van de computer. */
    private static BlockPos workingComputer(GameTestHelper helper, BlockPos computer) {
        helper.setBlock(computer, ModBlocks.COMPUTER.get());
        helper.setBlock(computer.east(), ModBlocks.MONITOR.get());
        networkLine(helper, new BlockPos(3, computer.getY(), computer.getZ()), computer.getX() - 1);
        generator(helper, computer.above());
        ComputerBlockEntity entity = (ComputerBlockEntity) helper.getBlockEntity(computer);
        entity.setName("Computer 1");
        entity.updateOn(helper.getLevel());
        return helper.absolutePos(computer);
    }

    /**
     * De app Computers op de kern (stap 3): de computer staat in de lijst; per app Off, View of Use (Logs hooguit View);
     * wie (een keycard-groep kan pas als er een groep is); een code van 4-8 tekens (te kort lukt niet, leeg haalt hem weg);
     * een andere naam. Het staat in het logboek (de code niet), het blijft na opslaan en laden, en een computer van een
     * ander netwerk kun je hier niet instellen.
     */
    @GameTest(template = ARENA, timeoutTicks = 20, batch = "computers")
    public static void computers_app_sets_apps_who_and_code(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(2, 3, 6), "pc-config");
        SecurityCoreBlockEntity core = poweredCore(helper, new BlockPos(2, 3, 2), owner);
        BlockPos pc = workingComputer(helper, new BlockPos(6, 3, 2));
        SecurityDevices.forget(level, core); // de kern keek al vóór de computer er stond (hij kijkt om de 2 seconden)
        SecurityOs.open(owner, core.getBlockPos());
        check(SecurityOs.hasDesktop(owner), "De eigenaar kreeg zijn desktop niet");
        var snapshot = SecurityOs.snapshot(core, level, SecurityOs.session(owner), true);
        check(snapshot.getList("computers", 10).size() == 1, "De app Computers kent de computer niet: " + snapshot.get("computers"));
        ComputerBlockEntity entity = (ComputerBlockEntity) level.getBlockEntity(pc);
        check(entity.rights().empty() && entity.who() == ComputerBlockEntity.Who.TRUSTED && !entity.hasCode(),
                "Een nieuwe computer is niet leeg, op Trusted en zonder code");

        // De apps: Cameras View, Devices Use, Logs hooguit View.
        pcAction(owner, core, pc, "pc_app", args -> { args.putString("app", "CAMERAS"); args.putInt("access", 1); });
        pcAction(owner, core, pc, "pc_app", args -> { args.putString("app", "DEVICES"); args.putInt("access", 2); });
        pcAction(owner, core, pc, "pc_app", args -> { args.putString("app", "LOGS"); args.putInt("access", 2); });
        pcAction(owner, core, pc, "pc_app", args -> { args.putString("app", "SETTINGS"); args.putInt("access", 2); });
        var rights = entity.rights();
        check(rights.access("CAMERAS") == com.senne.imttech.security.os.DesktopRights.Access.VIEW
                        && rights.mayUse("DEVICES") && rights.access("LOGS") == com.senne.imttech.security.os.DesktopRights.Access.VIEW
                        && !rights.shows("SETTINGS") && !rights.shows("KEYCARDS"),
                "De apps staan niet goed: " + rights.save());

        // Wie: een keycard-groep kan pas als er een is.
        pcAction(owner, core, pc, "pc_who", args -> { args.putString("who", "GROUP"); args.putString("group", "staff"); });
        check(entity.who() == ComputerBlockEntity.Who.TRUSTED, "Een keycard-groep kon zonder groepen");
        core.logic().addGroup("staff", "");
        pcAction(owner, core, pc, "pc_who", args -> { args.putString("who", "GROUP"); args.putString("group", "STAFF"); });
        check(entity.who() == ComputerBlockEntity.Who.GROUP && entity.group().equals("staff"),
                "Wie staat niet op de groep staff: " + entity.who() + " " + entity.group());

        // De code: te kort lukt niet, 4-8 tekens wel, leeg haalt hem weg.
        pcAction(owner, core, pc, "pc_code", args -> args.putString("code", "ab"));
        check(!entity.hasCode(), "Een te korte code werd bewaard");
        pcAction(owner, core, pc, "pc_code", args -> args.putString("code", "desk42"));
        check(entity.hasCode() && entity.checkCode("desk42") && !entity.checkCode("desk43"), "De code klopt niet");
        check(core.log().entries().stream().noneMatch(entry -> entry.text().contains("desk42")), "De code staat in het logboek");

        // Een andere naam, en alles staat in het logboek.
        pcAction(owner, core, pc, "pc_rename", args -> args.putString("name", "Reception"));
        check(entity.name().equals("Reception"), "De naam is niet Reception maar " + entity.name());
        check(hasLog(core, EventLog.Type.DEVICE, "set Cameras on Computer 1 to view")
                        && hasLog(core, EventLog.Type.DEVICE, "set a code on Computer 1")
                        && hasLog(core, EventLog.Type.DEVICE, "renamed computer Computer 1 to Reception"),
                "Het logboek mist iets van de app Computers");

        // Opslaan en laden: alles blijft.
        var registries = level.registryAccess();
        var copy = new ComputerBlockEntity(pc, entity.getBlockState());
        copy.loadWithComponents(entity.saveWithFullMetadata(registries), registries);
        check(copy.rights().mayUse("DEVICES") && copy.who() == ComputerBlockEntity.Who.GROUP && copy.checkCode("desk42")
                        && !copy.checkCode("wrong1") && copy.name().equals("Reception"),
                "Na laden is de computer zijn instellingen kwijt");

        // Een computer die niet aan deze kern hangt, stel je hier niet in.
        BlockPos stray = new BlockPos(6, 3, 12);
        helper.setBlock(stray, ModBlocks.COMPUTER.get());
        BlockPos strayAbs = helper.absolutePos(stray);
        pcAction(owner, core, strayAbs, "pc_app", args -> { args.putString("app", "CAMERAS"); args.putInt("access", 2); });
        check(((ComputerBlockEntity) level.getBlockEntity(strayAbs)).rights().empty(),
                "Een computer van een ander netwerk werd ingesteld");
        SecurityOs.close(owner);
        helper.succeed();
    }
}
