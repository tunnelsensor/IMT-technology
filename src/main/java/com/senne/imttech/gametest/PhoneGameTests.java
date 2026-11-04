package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.logic.AuthRequests;
import com.senne.imttech.logic.InputDeviceBlockEntity;
import com.senne.imttech.logic.program.GraphConverter;
import com.senne.imttech.logic.program.ScriptParser;
import com.senne.imttech.network.OsActionPayload;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.PhoneItem;
import com.senne.imttech.security.PhoneLink;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.DesktopRights;
import com.senne.imttech.security.os.Phones;
import com.senne.imttech.security.os.SecurityOs;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Tests voor de I.M.T Phone (hoofdstuk 50, 4 november): verbinden met IP en wachtwoord, een PIN kiezen, de telefoon openen
 * met de PIN (drie keer fout = op slot, en de eigenaar hoort het), de kleine desktop (wel Status en Requests, geen
 * Logic), en blokkeren door de eigenaar.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class PhoneGameTests {
    private PhoneGameTests() {}

    private static final String ARENA = "empty";
    private static final String PASSWORD = "abc123";

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    private record Setup(ServerPlayer owner, SecurityCoreBlockEntity core, BlockPos coreAbs, ItemStack phone, BlockPos rack) {}

    /** Een eigenaar met een kern (met wachtwoord en een draaiende server) en een nieuwe telefoon in zijn hand. */
    private static Setup setup(GameTestHelper helper) {
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(6, 3, 6), "phone-owner");
        BlockPos corePos = new BlockPos(2, 3, 2);
        SecurityCoreBlockEntity core = ImtTechGameTests.placeCore(helper, corePos, owner);
        ServerLevel level = helper.getLevel();
        core.ensureSystem(level);
        core.setPassword(PASSWORD, level.random);
        BlockPos rack = new BlockPos(12, 3, 12);
        ImtTechGameTests.placeServer(helper, rack, core);
        ImtTechGameTests.startServer(helper, rack, core);
        ItemStack phone = new ItemStack(ModItems.PHONE.get());
        owner.setItemInHand(InteractionHand.MAIN_HAND, phone);
        return new Setup(owner, core, helper.absolutePos(corePos), phone, rack);
    }

    /** Verbinden en een PIN kiezen, zoals de schermen van de telefoon dat doen. */
    private static void link(Setup setup, String pin) {
        Phones.use(setup.owner(), setup.phone());
        Phones.login(setup.owner(), setup.core().ip(), PASSWORD);
        Phones.pin(setup.owner(), pin);
    }

    private static boolean hasMessage(SecurityCoreBlockEntity core, String part) {
        return core.messages().stream().anyMatch(message -> message.text().contains(part));
    }

    /**
     * Een nieuwe telefoon: een fout wachtwoord verbindt niet; het goede wel (de telefoon kent het IP, de kern kent de
     * telefoon). Een PIN van twee cijfers mag niet, vier wel: dan gaat de kleine desktop open (Status en Requests, geen
     * Logic). Daarna: drie keer een foute PIN zet de telefoon op slot en waarschuwt de eigenaar; na de slottijd werkt de
     * goede PIN weer.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "phone")
    public static void phone_links_sets_pin_and_opens_small_desktop(GameTestHelper helper) {
        float lockBefore = ImtConfig.Security.PHONE_PIN_LOCK_SECONDS;
        ImtConfig.Security.PHONE_PIN_LOCK_SECONDS = 1.0f;
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper);
        ServerPlayer owner = setup.owner();
        SecurityCoreBlockEntity core = setup.core();
        Phones.use(owner, setup.phone());
        Phones.login(owner, core.ip(), "wrong1");
        check(PhoneItem.link(setup.phone()) == null, "Met een fout wachtwoord zou de telefoon niet verbonden mogen zijn");
        Phones.login(owner, core.ip(), PASSWORD);
        PhoneLink link = PhoneItem.link(setup.phone());
        check(link != null && link.ip().equals(core.ip()), "De telefoon zou aan de kern moeten hangen");
        check(core.phones().size() == 1 && core.phones().getFirst().id().equals(link.id()), "De kern zou de telefoon moeten kennen");
        Phones.pin(owner, "12");
        check(core.phone(link.id()).pin().isEmpty() && !SecurityOs.hasDesktop(owner), "Een PIN van twee cijfers mag niet");
        Phones.pin(owner, "1234");
        check(core.phone(link.id()).pin().equals("1234"), "De PIN zou bij de kern moeten staan");
        check(SecurityOs.hasDesktop(owner), "Na de PIN zou de desktop van de telefoon open moeten zijn");
        SecurityOs.Session session = SecurityOs.session(owner);
        check(session != null && link.id().equals(session.phone()), "De sessie zou van de telefoon moeten zijn");
        DesktopRights rights = session.rights(level);
        check(rights.shows("STATUS") && rights.mayUse("REQUESTS") && rights.mayUse("MESSAGES") && !rights.shows("LOGIC")
                && !rights.shows("SETTINGS"), "De telefoon zou Status, Requests en Messages moeten hebben, maar geen Logic of Settings");
        SecurityOs.close(owner);
        // Openen met de PIN: drie keer fout = op slot, en de eigenaar hoort het.
        Phones.use(owner, setup.phone());
        for (int i = 0; i < 3; i++) {
            Phones.pin(owner, "0000");
        }
        check(!SecurityOs.hasDesktop(owner), "Met een foute PIN zou de desktop dicht moeten blijven");
        check(Phones.lockedSeconds(level, link.id()) > 0, "Na drie foute PINs zou de telefoon op slot moeten zijn");
        check(hasMessage(core, "wrong PIN"), "De eigenaar zou een bericht over de foute PIN moeten hebben");
        Phones.pin(owner, "1234");
        check(!SecurityOs.hasDesktop(owner), "Op slot zou ook de goede PIN niet mogen werken");
        helper.runAfterDelay(30, () -> {
            check(Phones.lockedSeconds(level, link.id()) == 0, "Na de slottijd zou de telefoon weer vrij moeten zijn");
            Phones.use(owner, setup.phone());
            Phones.pin(owner, "1234");
            check(SecurityOs.hasDesktop(owner), "Na de slottijd zou de goede PIN de desktop moeten openen");
            ImtConfig.Security.PHONE_PIN_LOCK_SECONDS = lockBefore;
            helper.succeed();
        });
    }

    /**
     * Op de telefoon kun je een toegangsverzoek beantwoorden (Requests) en een bericht van I.M.T lezen (Messages), maar geen
     * diagram bewaren (geen Logic). Blokkeert de eigenaar de telefoon in Access, dan gaat de desktop dicht en opent de
     * telefoon niet meer; na deblokkeren wel. Zonder de telefoon in je rugzak gaat de desktop ook dicht.
     */
    @GameTest(template = ARENA, timeoutTicks = 200, batch = "phone")
    public static void phone_answers_requests_and_can_be_blocked(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Setup setup = setup(helper);
        ServerPlayer owner = setup.owner();
        SecurityCoreBlockEntity core = setup.core();
        ServerPlayer visitor = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(9, 3, 9), "phone-visitor");
        BlockPos console = new BlockPos(8, 3, 8);
        helper.setBlock(console, ModBlocks.AUTH_CONSOLE.get());
        ((InputDeviceBlockEntity) helper.getBlockEntity(console)).setName("Gate");
        BlockPos consoleAbs = helper.absolutePos(console);
        // De console kwam na de server: die moet zijn apparaten opnieuw bekijken (anders kent hij de console niet).
        com.senne.imttech.security.SecurityDevices.forget(level, core);
        AuthRequests.clear();
        link(setup, "4321");
        check(SecurityOs.hasDesktop(owner), "De desktop van de telefoon zou open moeten zijn");
        PhoneLink link = PhoneItem.link(setup.phone());
        // Geen Logic op de telefoon: een diagram bewaren wordt geweigerd.
        CompoundTag args = new CompoundTag();
        args.putString("old", "");
        args.putString("name", "Nope");
        String code = "when every(10):\n    pass\n";
        args.put("graph", GraphConverter.toGraph(ScriptParser.parse(code), null).save());
        args.putString("source", code);
        SecurityOs.handleAction(owner, new OsActionPayload(setup.coreAbs(), "logic_save", args));
        check(core.logic().program("Nope") == null, "Vanaf de telefoon zou je geen diagram mogen bewaren");
        // Wel een verzoek beantwoorden, en een bericht lezen.
        // De server (nog eens) starten nu de console er staat, zoals de tests van hoofdstuk 49 dat doen.
        helper.runAfterDelay(2, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), core);
            com.senne.imttech.security.SecurityDevices.forget(level, core);
        });
        helper.runAfterDelay(10, () -> {
            check(AuthRequests.ask(level, consoleAbs, visitor), "Het verzoek zou moeten lopen (kern bij de console: "
                    + (com.senne.imttech.security.SecurityCores.deviceCore(level, consoleAbs) != null) + ", server online: "
                    + com.senne.imttech.logic.LogicServer.stats(level, core).online() + ", zonder stroom: "
                    + com.senne.imttech.lan.DevicePower.dead(level, consoleAbs, visitor) + ")");
            check(hasMessage(core, "asks for access at Gate"), "Het verzoek zou in Messages moeten staan");
            CompoundTag answer = new CompoundTag();
            answer.putInt("id", AuthRequests.pendingAt(level, core).getFirst().id());
            answer.putBoolean("ok", true);
            SecurityOs.handleAction(owner, new OsActionPayload(setup.coreAbs(), "req_answer", answer));
            check(AuthRequests.pendingAt(level, core).isEmpty(), "Vanaf de telefoon zou je het verzoek moeten kunnen beantwoorden");
            CompoundTag snapshot = SecurityOs.snapshot(core, level, SecurityOs.session(owner), true);
            check(snapshot.getBoolean("phone") && !snapshot.getList("messages", 10).isEmpty(),
                    "De desktop van de telefoon zou de berichten moeten meekrijgen");
            // De eigenaar blokkeert de telefoon op zijn echte desktop.
            SecurityOs.close(owner);
            SecurityOs.open(owner, setup.coreAbs());
            check(SecurityOs.hasDesktop(owner) && SecurityOs.session(owner).phone() == null, "De desktop op de kern zelf zou open moeten zijn");
            CompoundTag block = new CompoundTag();
            block.putString("id", link.id().toString());
            block.putBoolean("on", true);
            SecurityOs.handleAction(owner, new OsActionPayload(setup.coreAbs(), "phone_block", block));
            check(core.phoneBlocked(link.id()), "De telefoon zou geblokkeerd moeten zijn");
            SecurityOs.close(owner);
            Phones.use(owner, setup.phone());
            Phones.pin(owner, "4321");
            check(!SecurityOs.hasDesktop(owner), "Een geblokkeerde telefoon zou niet open mogen gaan");
            block.putBoolean("on", false);
            SecurityOs.open(owner, setup.coreAbs());
            SecurityOs.handleAction(owner, new OsActionPayload(setup.coreAbs(), "phone_block", block));
            SecurityOs.close(owner);
            check(!core.phoneBlocked(link.id()), "De telefoon zou weer vrij moeten zijn");
            Phones.use(owner, setup.phone());
            Phones.pin(owner, "4321");
            check(SecurityOs.hasDesktop(owner) && SecurityOs.session(owner).phone() != null, "Na deblokkeren zou de telefoon weer open moeten gaan");
            // De telefoon weg uit de rugzak: de desktop gaat vanzelf dicht (de tick van de speler; een testspeler tikt
            // niet vanzelf, dus die doen we hier zelf, twintig keer, zodat er een seconde-tick bij zit).
            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        for (int tick = 10; tick <= 31; tick++) {
            helper.runAfterDelay(tick, () -> SecurityOs.onPlayerTick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(owner)));
        }
        helper.runAfterDelay(40, () -> {
            check(!SecurityOs.hasDesktop(owner), "Zonder de telefoon zou de desktop dicht moeten gaan");
            helper.succeed();
        });
    }
}
