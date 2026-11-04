package com.senne.imttech.logic;

import com.senne.imttech.logic.program.Op;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.os.EventLog;
import java.util.HashMap;
import java.util.List;
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
 * keycardlezer, een code op een codepaneel, (hoofdstuk 38) een netwerkknop of -hendel. We kijken bij
 * welke beveiligingskern het hoort, of zijn server draait, en geven het dan door.
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
        if (!(level.getBlockEntity(reader) instanceof InputDeviceBlockEntity device) || tooSoon(level, player)
                || com.senne.imttech.weapons.Emp.deadDevice(level, reader, player)
                || com.senne.imttech.lan.DevicePower.dead(level, reader, player)) {
            return;
        }
        SecurityCoreBlockEntity core = serverFor(level, reader, player);
        if (core == null) {
            return;
        }
        // Tijdens een oefening (hoofdstuk 27) werken de kaarten die de trainee bij het begin al had niet: een indringer heeft
        // ze ook niet. Een kaart die hij onderweg vond of stal, wel.
        boolean fromBefore = card != null && com.senne.imttech.training.Training.isCardFromBefore(level, core.getBlockPos(),
                player.getUUID(), card.number());
        boolean valid = core.logic().isValid(core.ip(), card) && !fromBefore;
        // De groepen staan in de kern (25 oktober). Een ongeldige kaart zit nergens in.
        LogicStore.Card listed = valid ? core.logic().card(card.number()) : null;
        read(level, reader, device, core, player, card, valid, listed == null ? List.of() : listed.groups());
    }

    /**
     * Een gehackte keycardlezer (hoofdstuk 12): de Bypass Kit laat hem een nagemaakte kaart lezen. Die is
     * geldig, zit in de groep die de hacker koos (25 oktober; "" = in geen groep), heeft een verzonnen
     * nummer dat niet op jouw lijst staat en geen naam. Verder gaat alles zoals bij een echte kaart: het
     * logboek, het lampje en je diagram. (In het logboek zie je zo een nummer dat je in de app Keycards
     * niet terugvindt.)
     */
    public static void forgedKeycard(ServerLevel level, BlockPos reader, ServerPlayer player, String group) {
        if (!(level.getBlockEntity(reader) instanceof InputDeviceBlockEntity device)
                || com.senne.imttech.weapons.Emp.deadDevice(level, reader, player)
                || com.senne.imttech.lan.DevicePower.dead(level, reader, player)) {
            return;
        }
        SecurityCoreBlockEntity core = serverFor(level, reader, player);
        if (core == null) {
            return;
        }
        KeycardData card = new KeycardData(core.ip(), core.hostname(), fakeNumber(level, core), 1, "");
        String own = core.logic().spelling(group);
        read(level, reader, device, core, player, card, true, own == null ? List.of() : List.of(own));
    }

    /**
     * De lezer heeft een kaart gelezen: oranje lampje, een regel in het logboek ("Senne used card #1001 (security,
     * lab) at Lab door"), en de server aan het werk. {@code groups} zijn de groepen waar de kaart zelf in zit.
     */
    private static void read(ServerLevel level, BlockPos reader, InputDeviceBlockEntity device, SecurityCoreBlockEntity core,
                             ServerPlayer player, @Nullable KeycardData card, boolean valid, List<String> groups) {
        InputDeviceBlock.flash(level, reader, InputLight.BUSY);
        com.senne.imttech.lan.CableTraffic.signal(level, reader, true); // het witte vakje naar de kern
        level.playSound(null, reader, ModSounds.IMT_BLIP.get(), SoundSource.BLOCKS, 0.5f, 1.4f);
        String what = card == null ? "a blank card"
                : "card #" + card.number() + " (" + (!valid ? "not valid here" : groups.isEmpty() ? "no group" : String.join(", ", groups)) + ")";
        core.logEvent(EventLog.Type.ENTRY, player.getGameProfile().getName() + " used " + what + " at " + device.name());
        LogicRunner.fire(level, core, Op.KEYCARD, device.name(), new LogicRunner.Context(Op.KEYCARD, device.name(),
                reader.immutable(), player.getUUID(), player.getGameProfile().getName(),
                SecurityCores.isFriend(level, core, player), isOwner(level, core, player), card, valid, "", null, groups));
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
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, device);
        if (core == null) {
            return;
        }
        Op trigger = level.getBlockState(device).getBlock() instanceof InputDeviceBlock input ? input.trigger() : Op.KEYCARD;
        LogicRunner.alarm(level, core, new LogicRunner.Context(trigger, name, device.immutable(), player.getUUID(),
                player.getGameProfile().getName(), SecurityCores.isFriend(level, core, player),
                isOwner(level, core, player), null, false, ""), "Someone is tampering with " + name);
    }

    /**
     * Een mislukte hack van een laserzender (hoofdstuk 33: de verkeerde draad, of te laat). Hoort hij bij een
     * beveiligingskern, dan gaat het alarm af zoals bij een lezer: "Someone is tampering with a laser gate at 12 64 -30"
     * (met de naam van de hacker erachter), I.M.T waarschuwt de eigenaar en de beveiliging jaagt op hem. Hoort hij nergens
     * bij, dan is er niemand om te waarschuwen: alleen het geluid.
     */
    public static void tamperedLaser(ServerLevel level, BlockPos emitter, ServerPlayer player) {
        level.playSound(null, emitter, ModSounds.IMT_DENIED.get(), SoundSource.BLOCKS, 0.8f, 0.8f);
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, emitter);
        if (core == null) {
            return;
        }
        String name = "a laser gate at " + emitter.getX() + " " + emitter.getY() + " " + emitter.getZ();
        LogicRunner.alarm(level, core, new LogicRunner.Context(Op.TURNED_OFF, "Laser gate", emitter.immutable(), player.getUUID(),
                player.getGameProfile().getName(), SecurityCores.isFriend(level, core, player),
                isOwner(level, core, player), null, false, ""), "Someone is tampering with " + name);
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
                || tooSoon(level, player)
                || com.senne.imttech.weapons.Emp.deadDevice(level, panel, player)
                || com.senne.imttech.lan.DevicePower.dead(level, panel, player)) {
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
                || !CodePanelBlock.isValidCode(code)
                || com.senne.imttech.weapons.Emp.deadDevice(level, panel, player)
                || com.senne.imttech.lan.DevicePower.dead(level, panel, player)) {
            return;
        }
        SecurityCoreBlockEntity core = serverFor(level, panel, player);
        if (core == null) {
            return;
        }
        enter(level, panel, device, core, player, code);
    }

    /**
     * Iemand drukte op een netwerkknop (hoofdstuk 38): een regel in het logboek ("Senne pressed Doorbell") en de server
     * aan het werk ({@code when button("Doorbell"):}). Zonder stroom, netwerkkabel of draaiende server hoort de speler
     * waarom er niets gebeurt. (Geen wachttijd zoals bij een lezer: de knop blijft zelf even ingedrukt.)
     */
    public static void button(ServerLevel level, BlockPos button, ServerPlayer player) {
        if (!(level.getBlockEntity(button) instanceof InputDeviceBlockEntity device)
                || com.senne.imttech.weapons.Emp.deadDevice(level, button, player)
                || com.senne.imttech.lan.DevicePower.dead(level, button, player)) {
            return;
        }
        SecurityCoreBlockEntity core = serverFor(level, button, player);
        if (core == null) {
            return;
        }
        com.senne.imttech.lan.CableTraffic.signal(level, button, true);
        core.logEvent(EventLog.Type.ENTRY, player.getGameProfile().getName() + " pressed " + device.name());
        LogicRunner.fire(level, core, Op.BUTTON, device.name(), new LogicRunner.Context(Op.BUTTON, device.name(),
                button.immutable(), player.getUUID(), player.getGameProfile().getName(),
                SecurityCores.isFriend(level, core, player), isOwner(level, core, player), null, false, ""));
    }

    /**
     * Iemand zette een netwerkhendel om (hoofdstuk 38): een regel in het logboek ("Senne switched Night mode on") en de
     * server aan het werk ({@code when lever("Night mode"):}, met {@code lever.on}). Elke keer omzetten telt (geen
     * wachttijd), anders wist de server niet meer hoe de hendel staat.
     */
    public static void lever(ServerLevel level, BlockPos lever, ServerPlayer player, boolean on) {
        if (!(level.getBlockEntity(lever) instanceof InputDeviceBlockEntity device)
                || com.senne.imttech.weapons.Emp.deadDevice(level, lever, player)
                || com.senne.imttech.lan.DevicePower.dead(level, lever, player)) {
            return;
        }
        SecurityCoreBlockEntity core = serverFor(level, lever, player);
        if (core == null) {
            return;
        }
        com.senne.imttech.lan.CableTraffic.signal(level, lever, true);
        core.logEvent(EventLog.Type.ENTRY, player.getGameProfile().getName() + " switched " + device.name() + (on ? " on" : " off"));
        LogicRunner.fire(level, core, Op.LEVER, device.name(), new LogicRunner.Context(Op.LEVER, device.name(),
                lever.immutable(), player.getUUID(), player.getGameProfile().getName(),
                SecurityCores.isFriend(level, core, player), isOwner(level, core, player), null, false, "", null, List.of(), on));
    }

    /** Er is een code ingetypt: oranje lampje, een regel in het logboek (zonder de code), en de server aan het werk. */
    private static void enter(ServerLevel level, BlockPos panel, InputDeviceBlockEntity device, SecurityCoreBlockEntity core,
                              ServerPlayer player, String code) {
        InputDeviceBlock.flash(level, panel, InputLight.BUSY);
        com.senne.imttech.lan.CableTraffic.signal(level, panel, true);
        level.playSound(null, panel, ModSounds.IMT_BLIP.get(), SoundSource.BLOCKS, 0.5f, 1.4f);
        String name = player.getGameProfile().getName();
        core.logEvent(EventLog.Type.ENTRY, name + " entered a code at " + device.name());
        // De autorisatiecode (hoofdstuk 49): de vorige code komt altijd in het logboek, ook als hij nog mag.
        SecurityCoreBlockEntity.AuthCheck auth = core.checkAuthCode(code);
        if (auth.old()) {
            core.logEvent(EventLog.Type.ENTRY, name + " used the previous authorization code at " + device.name()
                    + (auth.valid() ? " (still allowed)" : " (not allowed anymore)"));
        }
        LogicRunner.fire(level, core, Op.CODEPANEL, device.name(), new LogicRunner.Context(Op.CODEPANEL, device.name(),
                panel.immutable(), player.getUUID(), name, SecurityCores.isFriend(level, core, player),
                isOwner(level, core, player), null, false, code).withAuth(auth.valid(), auth.old(), core.authCode()));
    }

    /**
     * De beveiligingskern waar deze lezer of dit paneel bij hoort (via zijn netwerkkabel, hoofdstuk 38), als zijn server
     * draait. Zo niet, dan wordt het lampje rood, hoort de speler waarom, en geven we null.
     */
    static @Nullable SecurityCoreBlockEntity serverFor(ServerLevel level, BlockPos device, ServerPlayer player) {
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, device);
        String problem = core == null ? (com.senne.imttech.lan.LanGrid.required() ? "logic.imttech.input.no_cable"
                : "logic.imttech.input.no_network")
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

    /**
     * Is deze speler de eigenaar van de kern ({@code player.owner} in je diagram)? Niet als hij de trainee is van een
     * oefening bij die kern (hoofdstuk 27): dan is hij voor zijn eigen basis een indringer.
     */
    static boolean isOwner(ServerLevel level, SecurityCoreBlockEntity core, ServerPlayer player) {
        return player.getUUID().equals(core.ownerId())
                && !com.senne.imttech.training.Training.isDrillTrainee(level, core.getBlockPos(), player.getUUID());
    }
}
