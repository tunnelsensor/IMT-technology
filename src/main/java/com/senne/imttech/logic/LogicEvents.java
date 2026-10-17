package com.senne.imttech.logic;

import com.senne.imttech.logic.program.Op;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.os.EventLog;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;

/**
 * Hier komt binnen wat er in de wereld gebeurt en een diagram kan starten: een kaart tegen een
 * keycardlezer, een code op een codepaneel. We kijken bij welke beveiligingskern het hoort, of
 * zijn server draait, en geven het dan door.
 */
public final class LogicEvents {
    private LogicEvents() {}

    /** Zo lang (ticks) moet een speler wachten voor hij dezelfde lezer of hetzelfde paneel weer gebruikt. */
    private static final int COOLDOWN = 10;
    private static final Map<UUID, Long> LAST_USE = new HashMap<>();

    /**
     * Iemand houdt een kaart tegen een keycardlezer. {@code card} is null bij een lege kaart. Ook
     * ongeldige kaarten (van een ander netwerk, geblokkeerd) gaan naar de server: het diagram beslist
     * wat ermee gebeurt.
     */
    public static void keycard(ServerLevel level, BlockPos reader, ServerPlayer player, @Nullable KeycardData card) {
        if (!(level.getBlockEntity(reader) instanceof InputDeviceBlockEntity device) || tooSoon(level, player)) {
            return;
        }
        SecurityCoreBlockEntity core = serverFor(level, reader, player);
        if (core == null) {
            return;
        }
        read(level, reader, device, core, player, card, core.logic().isValid(core.ip(), card));
    }

    /**
     * Een gehackte keycardlezer (hoofdstuk 12): de Bypass Kit laat hem een nagemaakte kaart lezen. Die is
     * geldig, van het niveau dat de hacker koos, met een verzonnen nummer dat niet op jouw lijst staat en
     * zonder naam. Verder gaat alles zoals bij een echte kaart: het logboek, het lampje en je diagram.
     * (In het logboek zie je zo een nummer dat je in de app Keycards niet terugvindt.)
     */
    public static void forgedKeycard(ServerLevel level, BlockPos reader, ServerPlayer player, int cardLevel) {
        if (!(level.getBlockEntity(reader) instanceof InputDeviceBlockEntity device)) {
            return;
        }
        SecurityCoreBlockEntity core = serverFor(level, reader, player);
        if (core == null) {
            return;
        }
        KeycardData card = new KeycardData(core.ip(), core.hostname(), fakeNumber(level, core), cardLevel, "");
        read(level, reader, device, core, player, card, true);
    }

    /** De lezer heeft een kaart gelezen: oranje lampje, een regel in het logboek, en de server aan het werk. */
    private static void read(ServerLevel level, BlockPos reader, InputDeviceBlockEntity device, SecurityCoreBlockEntity core,
                             ServerPlayer player, @Nullable KeycardData card, boolean valid) {
        InputDeviceBlock.flash(level, reader, InputLight.BUSY);
        level.playSound(null, reader, ModSounds.IMT_BLIP.get(), SoundSource.BLOCKS, 0.5f, 1.4f);
        String what = card == null ? "a blank card"
                : "card #" + card.number() + " (level " + card.level() + (valid ? "" : ", not valid here") + ")";
        core.logEvent(EventLog.Type.ENTRY, player.getGameProfile().getName() + " used " + what + " at " + device.name());
        LogicRunner.fire(level, core, Op.KEYCARD, device.name(), new LogicRunner.Context(Op.KEYCARD, device.name(),
                reader.immutable(), player.getUUID(), player.getGameProfile().getName(),
                SecurityCores.isFriend(level, core, player), player.getUUID().equals(core.ownerId()), card, valid, ""));
    }

    /** Een kaartnummer dat niet op de lijst van dit netwerk staat (#2000 tot #9999). */
    private static int fakeNumber(ServerLevel level, SecurityCoreBlockEntity core) {
        for (int attempt = 0; attempt < 100; attempt++) {
            int number = 2000 + level.random.nextInt(8000);
            if (core.logic().cards().stream().noneMatch(card -> card.number() == number)) {
                return number;
            }
        }
        return 9999;
    }

    /**
     * Een mislukte hack (hoofdstuk 12): het lampje wordt rood en het alarm gaat af, net alsof je diagram
     * {@code alarm("Someone is tampering with Lab door")} zei: een rode regel in het logboek (met de
     * naam van de hacker erachter), I.M.T waarschuwt de eigenaar, en de beveiliging jaagt op hem.
     */
    public static void tampered(ServerLevel level, BlockPos device, ServerPlayer player) {
        String name = level.getBlockEntity(device) instanceof InputDeviceBlockEntity input ? input.name() : "a reader";
        InputDeviceBlock.flash(level, device, InputLight.DENIED);
        level.playSound(null, device, ModSounds.IMT_DENIED.get(), SoundSource.BLOCKS, 0.8f, 0.8f);
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, device);
        if (core == null) {
            return;
        }
        Op trigger = level.getBlockState(device).getBlock() instanceof CodePanelBlock ? Op.CODEPANEL : Op.KEYCARD;
        LogicRunner.alarm(level, core, new LogicRunner.Context(trigger, name, device.immutable(), player.getUUID(),
                player.getGameProfile().getName(), SecurityCores.isFriend(level, core, player),
                player.getUUID().equals(core.ownerId()), null, false, ""), "Someone is tampering with " + name);
    }

    /**
     * Iemand typte een code op een codepaneel. We controleren dat het echt een code is (alleen
     * cijfers), dat hij er dichtbij staat, en geven hem door. In het logboek komt NIET welke code:
     * wie je logboek leest (een hacker) moet de code niet zomaar kunnen zien.
     */
    public static void codePanel(ServerLevel level, BlockPos panel, ServerPlayer player, String code) {
        if (!(level.getBlockEntity(panel) instanceof InputDeviceBlockEntity device)
                || !(level.getBlockState(panel).getBlock() instanceof CodePanelBlock)
                || !CodePanelBlock.isValidCode(code)
                || player.distanceToSqr(panel.getCenter()) > CodePanelBlock.REACH * CodePanelBlock.REACH
                || tooSoon(level, player)) {
            return;
        }
        SecurityCoreBlockEntity core = serverFor(level, panel, player);
        if (core == null) {
            return;
        }
        enter(level, panel, device, core, player, code);
    }

    /**
     * Een gekraakte code (hoofdstuk 12): de Bypass Kit typt hem in op het paneel. Verder precies alsof
     * de speler hem zelf intypte: dezelfde regel in het logboek ("... entered a code at ..."), en je
     * diagram beslist.
     */
    public static void crackedCode(ServerLevel level, BlockPos panel, ServerPlayer player, String code) {
        if (!(level.getBlockEntity(panel) instanceof InputDeviceBlockEntity device)
                || !(level.getBlockState(panel).getBlock() instanceof CodePanelBlock)
                || !CodePanelBlock.isValidCode(code)) {
            return;
        }
        SecurityCoreBlockEntity core = serverFor(level, panel, player);
        if (core == null) {
            return;
        }
        enter(level, panel, device, core, player, code);
    }

    /** Er is een code ingetypt: oranje lampje, een regel in het logboek (zonder de code), en de server aan het werk. */
    private static void enter(ServerLevel level, BlockPos panel, InputDeviceBlockEntity device, SecurityCoreBlockEntity core,
                              ServerPlayer player, String code) {
        InputDeviceBlock.flash(level, panel, InputLight.BUSY);
        level.playSound(null, panel, ModSounds.IMT_BLIP.get(), SoundSource.BLOCKS, 0.5f, 1.4f);
        core.logEvent(EventLog.Type.ENTRY, player.getGameProfile().getName() + " entered a code at " + device.name());
        LogicRunner.fire(level, core, Op.CODEPANEL, device.name(), new LogicRunner.Context(Op.CODEPANEL, device.name(),
                panel.immutable(), player.getUUID(), player.getGameProfile().getName(),
                SecurityCores.isFriend(level, core, player), player.getUUID().equals(core.ownerId()), null, false, code));
    }

    /**
     * De beveiligingskern waar deze lezer of dit paneel bij hoort, als zijn server draait. Zo niet,
     * dan wordt het lampje rood, hoort de speler waarom, en geven we null.
     */
    private static @Nullable SecurityCoreBlockEntity serverFor(ServerLevel level, BlockPos device, ServerPlayer player) {
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, device);
        String problem = core == null ? "logic.imttech.input.no_network"
                : !LogicServer.stats(level, core).online() ? "logic.imttech.input.no_response" : null;
        if (problem == null) {
            return core;
        }
        InputDeviceBlock.flash(level, device, InputLight.DENIED);
        level.playSound(null, device, ModSounds.IMT_DENIED.get(), SoundSource.BLOCKS, 0.6f, 1.0f);
        player.displayClientMessage(Component.translatable(problem).withStyle(ChatFormatting.RED), true);
        return null;
    }

    /** Heeft deze speler net nog iets gebruikt? (Zo kun je een lezer niet honderd keer per seconde laten lezen.) */
    private static boolean tooSoon(ServerLevel level, ServerPlayer player) {
        long now = level.getGameTime();
        Long last = LAST_USE.get(player.getUUID());
        if (last != null && now - last < COOLDOWN && now >= last) {
            return true;
        }
        LAST_USE.put(player.getUUID(), now);
        return false;
    }

    public static void clear() {
        LAST_USE.clear();
    }
}
