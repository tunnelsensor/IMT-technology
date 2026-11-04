package com.senne.imttech.logic;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.logic.program.Op;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.os.EventLog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Toegangsverzoeken (hoofdstuk 49, 4 november). Jouw wens: "een blok waar de code requests binnenkomen, en dan kun je
 * ze accepteren of weigeren" (zoals in Star Wars: "should I hold them?" "no, leave them to me"). De {@link AuthConsoleBlock}
 * is dat blok: wie erop drukt, vraagt toegang. Het verzoek komt op de desktop (ook via de laptop en de GSM) in de app
 * Requests, met Accept en Deny; na {@code [security] authRequestSeconds} zonder antwoord is het geweigerd. Het antwoord
 * start je diagram: {@code when request("Gate"):} met {@code request.accepted} (en {@code player.name}).
 *
 * De verzoeken staan hier in het geheugen per kern (ze leven maar een halve minuut); de kern zelf doet elke tick
 * {@link #tick} (de tijd, en het oranje lampje van de console blijft branden zolang het verzoek loopt).
 */
public final class AuthRequests {
    private AuthRequests() {}

    /** Eén verzoek: van wie, bij welke console, en sinds wanneer (speltijd). */
    public record Request(int id, UUID player, String playerName, BlockPos console, String consoleName, long since) {}

    /** Per wereld, per kern: de verzoeken die nog op een antwoord wachten. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, List<Request>>> PENDING = new HashMap<>();
    private static int nextId = 1;

    /** Zoveel ticks wacht een verzoek op een antwoord (uit de config). */
    public static int timeoutTicks() {
        return Math.max(20, Math.round(ImtConfig.Security.AUTH_REQUEST_SECONDS * 20));
    }

    private static List<Request> pending(ServerLevel level, BlockPos core) {
        return PENDING.computeIfAbsent(level.dimension(), key -> new HashMap<>()).computeIfAbsent(core.immutable(), key -> new ArrayList<>());
    }

    /** De verzoeken die bij deze kern wachten (de oudste eerst). */
    public static List<Request> pendingAt(ServerLevel level, SecurityCoreBlockEntity core) {
        return List.copyOf(pending(level, core.getBlockPos()));
    }

    // ================================================================ Vragen

    /**
     * Iemand drukt op een Auth Console: zijn verzoek gaat naar de kern (als de console stroom, een netwerk en een draaiende
     * server heeft, net als een codepaneel). Eén open verzoek per speler per kern. Geeft true als het verzoek loopt.
     */
    public static boolean ask(ServerLevel level, BlockPos console, ServerPlayer player) {
        if (!(level.getBlockEntity(console) instanceof InputDeviceBlockEntity device)
                || !(level.getBlockState(console).getBlock() instanceof AuthConsoleBlock)
                || com.senne.imttech.weapons.Emp.deadDevice(level, console, player)
                || com.senne.imttech.lan.DevicePower.dead(level, console, player)) {
            return false;
        }
        SecurityCoreBlockEntity core = LogicEvents.serverFor(level, console, player);
        if (core == null) {
            return false;
        }
        long now = level.getGameTime();
        for (Request open : pending(level, core.getBlockPos())) {
            if (open.player().equals(player.getUUID())) {
                int left = (int) Math.max(1, (timeoutTicks() - (now - open.since()) + 19) / 20);
                player.displayClientMessage(Component.translatable("auth.imttech.console.waiting", left).withStyle(ChatFormatting.YELLOW), true);
                return false;
            }
        }
        String name = player.getGameProfile().getName();
        Request request = new Request(nextId++, player.getUUID(), name, console.immutable(), device.name(), now);
        pending(level, core.getBlockPos()).add(request);
        InputDeviceBlock.flash(level, console, InputLight.BUSY);
        com.senne.imttech.lan.CableTraffic.signal(level, console, true);
        level.playSound(null, console, ModSounds.IMT_NOTIFY.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
        core.logEvent(EventLog.Type.ENTRY, name + " asked for access at " + device.name());
        player.displayClientMessage(Component.translatable("auth.imttech.console.sent", Math.round(ImtConfig.Security.AUTH_REQUEST_SECONDS))
                .withStyle(ChatFormatting.AQUA), true);
        core.addMessage(name + " asks for access at " + device.name(), false);
        // De eigenaar hoort het meteen (ook zonder desktop open): in de app Requests kan hij antwoorden.
        ServerPlayer owner = core.ownerId() == null ? null : level.getServer().getPlayerList().getPlayer(core.ownerId());
        if (owner != null && owner != player) {
            owner.displayClientMessage(Component.translatable("auth.imttech.console.owner_notice", name, device.name(), core.hostname())
                    .withStyle(ChatFormatting.AQUA), false);
        }
        return true;
    }

    // ================================================================ Antwoorden

    /** Het antwoord van de desktop (of de laptop, of de GSM): accepteren of weigeren. Geeft false als het verzoek weg is. */
    public static boolean answer(ServerLevel level, SecurityCoreBlockEntity core, int id, boolean accepted, @Nullable ServerPlayer by) {
        List<Request> open = pending(level, core.getBlockPos());
        for (Request request : open) {
            if (request.id() == id) {
                open.remove(request);
                finish(level, core, request, accepted, by == null ? null : by.getGameProfile().getName());
                return true;
            }
        }
        return false;
    }

    /** Elke tick (vanuit de kern): te lang gewacht = geweigerd; intussen blijft het lampje van de console oranje. */
    public static void tick(ServerLevel level, SecurityCoreBlockEntity core) {
        Map<BlockPos, List<Request>> perCore = PENDING.get(level.dimension());
        List<Request> open = perCore == null ? null : perCore.get(core.getBlockPos());
        if (open == null || open.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        int timeout = timeoutTicks();
        for (Request request : new ArrayList<>(open)) {
            long waited = now - request.since();
            if (waited >= timeout) {
                open.remove(request);
                finish(level, core, request, false, null);
            } else if (waited % 20 == 0 && level.isLoaded(request.console())) {
                InputDeviceBlock.flash(level, request.console(), InputLight.BUSY);
            }
        }
    }

    /**
     * Het verzoek is beantwoord (of de tijd is om): het lampje groen of rood, een regel in het logboek, de vrager hoort
     * het, en je diagram gaat aan het werk met {@code when request("Gate"):} en {@code request.accepted}.
     */
    private static void finish(ServerLevel level, SecurityCoreBlockEntity core, Request request, boolean accepted, @Nullable String by) {
        if (level.isLoaded(request.console())) {
            InputDeviceBlock.flash(level, request.console(), accepted ? InputLight.OK : InputLight.DENIED);
            com.senne.imttech.lan.CableTraffic.signal(level, request.console(), false);
            level.playSound(null, request.console(), accepted ? ModSounds.IMT_CONFIRM.get() : ModSounds.IMT_DENIED.get(),
                    SoundSource.BLOCKS, 0.8f, 1.0f);
        }
        String verdict = by == null ? "Nobody answered " + request.playerName()
                : by + (accepted ? " accepted " : " denied ") + request.playerName();
        core.logEvent(EventLog.Type.ENTRY, verdict + " at " + request.consoleName());
        ServerPlayer asker = level.getServer().getPlayerList().getPlayer(request.player());
        if (asker != null) {
            asker.displayClientMessage(Component.translatable(by == null ? "auth.imttech.console.no_answer"
                    : accepted ? "auth.imttech.console.accepted" : "auth.imttech.console.denied", request.consoleName())
                    .withStyle(accepted ? ChatFormatting.GREEN : ChatFormatting.RED), true);
        }
        boolean trusted = asker != null && SecurityCores.isFriend(level, core, asker);
        boolean owner = asker != null && LogicEvents.isOwner(level, core, asker);
        LogicRunner.Context context = new LogicRunner.Context(Op.REQUEST, request.consoleName(), request.console(), request.player(),
                request.playerName(), trusted, owner, null, false, "").withRequest(accepted);
        LogicRunner.fire(level, core, Op.REQUEST, request.consoleName(), context);
    }

    // ================================================================ Voor de desktop

    /** De open verzoeken van deze kern voor de app Requests: per verzoek het nummer, wie, waar en hoeveel seconden nog. */
    public static ListTag snapshot(ServerLevel level, SecurityCoreBlockEntity core) {
        ListTag list = new ListTag();
        long now = level.getGameTime();
        for (Request request : pending(level, core.getBlockPos())) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("id", request.id());
            entry.putString("p", request.playerName());
            entry.putString("d", request.consoleName());
            entry.putInt("s", (int) Math.max(0, (timeoutTicks() - (now - request.since()) + 19) / 20));
            list.add(entry);
        }
        return list;
    }

    public static void clear() {
        PENDING.clear();
    }
}
