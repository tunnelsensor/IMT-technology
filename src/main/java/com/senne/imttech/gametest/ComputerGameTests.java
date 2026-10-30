package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.os.EventLog;
import com.senne.imttech.security.os.SecurityOs;
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
 * Tests voor hoofdstuk 40 (jouw wens van 30 oktober): de Hacking Laptop als laptop voor op afstand (inloggen met IP en
 * wachtwoord, zoals SSH), en de Computer met Monitor waarvan je op de kern instelt wat erop mag.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class ComputerGameTests {
    private ComputerGameTests() {}

    private static final String ARENA = "empty";

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
}
