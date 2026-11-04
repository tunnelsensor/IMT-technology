package com.senne.imttech.security.os;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.network.ModNetwork;
import com.senne.imttech.network.PhonePromptPayload;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.security.PhoneItem;
import com.senne.imttech.security.PhoneLink;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.net.CoreNetworkData;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De I.M.T Phone (hoofdstuk 50, 4 november). Jouw antwoorden: de apps Status (met de code), Logs, Cameras, Devices,
 * Requests (accepteren of weigeren) en Messages; verbinden "zoals de laptop" (IP en wachtwoord); een gestolen telefoon:
 * een PIN, en de eigenaar kan hem op afstand blokkeren.
 *
 * Zo gaat het: rechtsklik met de telefoon. Hangt hij nog aan geen kern, dan vraagt hij het IP en het wachtwoord (zoals
 * de laptop, zie SecurityOs.connect); lukt dat, dan hangt hij aan die kern (PhoneLink op het item, en de kern onthoudt
 * de telefoon) en kies je een PIN van vier cijfers. Daarna vraagt hij elke keer die PIN; drie keer fout (instelbaar)
 * en hij zit even op slot, en de eigenaar hoort dat. De desktop die dan opengaat is er een op afstand, met alleen de
 * apps van de telefoon (DesktopRights.PHONE); hij gaat dicht zodra de telefoon niet meer in je rugzak zit, of de
 * eigenaar hem blokkeert (de app Access).
 */
public final class Phones {
    private Phones() {}

    /** Per speler: waar hij in het verbinden zit (welke telefoon, en of hij een PIN kiest of hem intypt). */
    private record Pending(@Nullable UUID phone, boolean choosing) {}

    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    /** Per telefoon: hoe vaak de PIN al fout was, en tot wanneer (speltijd) hij op slot zit. */
    private static final Map<UUID, Integer> WRONG_PIN = new HashMap<>();
    private static final Map<UUID, Long> LOCKED_UNTIL = new HashMap<>();

    // ================================================================ Rechtsklik

    /** Rechtsklik met de telefoon: verbinden (de eerste keer) of de PIN vragen. */
    public static void use(ServerPlayer player, ItemStack stack) {
        PhoneLink link = PhoneItem.link(stack);
        if (link == null) {
            PENDING.put(player.getUUID(), new Pending(null, false));
            ModNetwork.sendTo(player, new PhonePromptPayload(PhonePromptPayload.LOGIN, "", ""));
            return;
        }
        ServerLevel level = player.serverLevel();
        SecurityCoreBlockEntity core = coreByIp(level, link.ip());
        if (core == null) {
            tell(player, Component.translatable("phone.imttech.msg.no_system", link.host(), link.ip()), ChatFormatting.RED);
            return;
        }
        SecurityCoreBlockEntity.Phone phone = core.phone(link.id());
        if (phone == null) {
            tell(player, Component.translatable("phone.imttech.msg.forgotten", core.hostname()), ChatFormatting.RED);
            return;
        }
        if (phone.blocked()) {
            tell(player, Component.translatable("phone.imttech.msg.blocked", core.hostname()), ChatFormatting.RED);
            return;
        }
        int locked = lockedSeconds(level, link.id());
        if (locked > 0) {
            tell(player, Component.translatable("phone.imttech.msg.locked", locked), ChatFormatting.RED);
            return;
        }
        if (phone.pin().isEmpty()) {
            // Verbonden, maar nog geen PIN gekozen (de speler sloot het scherm): nu alsnog.
            PENDING.put(player.getUUID(), new Pending(link.id(), true));
            ModNetwork.sendTo(player, new PhonePromptPayload(PhonePromptPayload.SET_PIN, core.hostname(), ""));
            return;
        }
        PENDING.put(player.getUUID(), new Pending(link.id(), false));
        ModNetwork.sendTo(player, new PhonePromptPayload(PhonePromptPayload.PIN, core.hostname(), ""));
    }

    // ================================================================ Verbinden

    /** Het IP en het wachtwoord (het scherm van de telefoon): de telefoon aan die kern hangen, en dan een PIN vragen. */
    public static void login(ServerPlayer player, String ip, String password) {
        ItemStack stack = phoneInHand(player);
        if (stack.isEmpty()) {
            reply(player, "Hold the phone while you connect it.");
            return;
        }
        if (PhoneItem.link(stack) != null) {
            reply(player, "This phone is connected already.");
            return;
        }
        SecurityCoreBlockEntity core = SecurityOs.connect(player, ip, password, message -> reply(player, message));
        if (core == null) {
            return;
        }
        UUID id = UUID.randomUUID();
        String name = player.getGameProfile().getName();
        stack.set(ModDataComponents.PHONE.get(), new PhoneLink(core.ip(), core.hostname(), id));
        core.linkPhone(id, name);
        core.logEvent(EventLog.Type.LOGIN, name + " connected a phone");
        PENDING.put(player.getUUID(), new Pending(id, true));
        ModNetwork.sendTo(player, new PhonePromptPayload(PhonePromptPayload.SET_PIN, core.hostname(), ""));
    }

    /** De PIN (het scherm van de telefoon): gekozen (de eerste keer) of ingetypt om de telefoon te openen. */
    public static void pin(ServerPlayer player, String pin) {
        Pending pending = PENDING.get(player.getUUID());
        ItemStack stack = phoneInHand(player);
        PhoneLink link = PhoneItem.link(stack);
        if (pending == null || link == null || !link.id().equals(pending.phone())) {
            reply(player, "Hold the phone you were using.");
            return;
        }
        ServerLevel level = player.serverLevel();
        SecurityCoreBlockEntity core = coreByIp(level, link.ip());
        SecurityCoreBlockEntity.Phone phone = core == null ? null : core.phone(link.id());
        if (core == null || phone == null) {
            reply(player, "No system answers at " + link.ip() + " anymore.");
            return;
        }
        if (!pin.matches("[0-9]{4}")) {
            reply(player, "A PIN is exactly 4 digits.");
            return;
        }
        String name = player.getGameProfile().getName();
        if (pending.choosing()) {
            core.setPhonePin(link.id(), pin);
            core.logEvent(EventLog.Type.SYSTEM, name + " chose the PIN of a phone");
            PENDING.remove(player.getUUID());
            open(player, core, link.id());
            return;
        }
        if (phone.blocked()) {
            reply(player, "This phone was blocked by the owner of " + core.hostname() + ".");
            return;
        }
        int locked = lockedSeconds(level, link.id());
        if (locked > 0) {
            reply(player, "Wrong PIN too often: the phone is locked for " + locked + " s.");
            return;
        }
        if (!phone.pin().equals(pin)) {
            int wrong = WRONG_PIN.merge(link.id(), 1, Integer::sum);
            int attempts = Math.max(1, Math.round(ImtConfig.Security.PHONE_PIN_ATTEMPTS));
            core.logEvent(EventLog.Type.LOGIN_FAIL, name + " typed a wrong PIN on the phone of " + phone.name());
            core.addMessage(name + " typed a wrong PIN on the phone of " + phone.name(), true);
            ServerPlayer owner = core.ownerId() == null ? null : level.getServer().getPlayerList().getPlayer(core.ownerId());
            if (owner != null && owner != player) {
                ImtServer.send(owner, Component.translatable("phone.imttech.msg.wrong_pin_alert", name, phone.name(), core.hostname()),
                        ImtMessagePayload.Level.ALERT);
            }
            if (wrong >= attempts) {
                WRONG_PIN.remove(link.id());
                int seconds = Math.max(1, Math.round(ImtConfig.Security.PHONE_PIN_LOCK_SECONDS));
                LOCKED_UNTIL.put(link.id(), level.getGameTime() + seconds * 20L);
                reply(player, "Wrong PIN. The phone is locked for " + seconds + " s, and the owner was told.");
            } else {
                reply(player, "Wrong PIN (" + wrong + " of " + attempts + ").");
            }
            return;
        }
        WRONG_PIN.remove(link.id());
        PENDING.remove(player.getUUID());
        open(player, core, link.id());
    }

    private static void open(ServerPlayer player, SecurityCoreBlockEntity core, UUID phone) {
        if (!SecurityOs.openPhone(player, core, phone)) {
            reply(player, "Connection refused.");
            return;
        }
        core.logEvent(EventLog.Type.LOGIN, player.getGameProfile().getName() + " opened a phone");
    }

    // ================================================================ Helpers

    /** Hoeveel seconden deze telefoon nog op slot zit (0 = niet). */
    public static int lockedSeconds(ServerLevel level, UUID phone) {
        Long until = LOCKED_UNTIL.get(phone);
        if (until == null) {
            return 0;
        }
        long left = until - level.getGameTime();
        if (left <= 0) {
            LOCKED_UNTIL.remove(phone);
            return 0;
        }
        return (int) ((left + 19) / 20);
    }

    /** Heeft deze speler de telefoon met dit nummer nog bij zich? */
    public static boolean hasPhone(ServerPlayer player, UUID phone) {
        return player.getInventory().contains(stack -> {
            PhoneLink link = PhoneItem.link(stack);
            return link != null && link.id().equals(phone);
        });
    }

    /** De telefoon in zijn hand (hoofd- of andere hand), of leeg. */
    private static ItemStack phoneInHand(ServerPlayer player) {
        if (player.getMainHandItem().getItem() instanceof PhoneItem) {
            return player.getMainHandItem();
        }
        if (player.getOffhandItem().getItem() instanceof PhoneItem) {
            return player.getOffhandItem();
        }
        return ItemStack.EMPTY;
    }

    /** De kern met dit IP, als hij in deze wereld staat (zo nodig wordt zijn stuk wereld geladen). */
    static @Nullable SecurityCoreBlockEntity coreByIp(ServerLevel level, String ip) {
        GlobalPos where = CoreNetworkData.get(level).find(ip);
        if (where == null || !where.dimension().equals(level.dimension())) {
            return null;
        }
        BlockPos pos = where.pos();
        level.getChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
        return level.getBlockEntity(pos) instanceof SecurityCoreBlockEntity core ? core : null;
    }

    /** Een antwoord voor het scherm van de telefoon (waarom het niet lukte). */
    private static void reply(ServerPlayer player, String message) {
        ModNetwork.sendTo(player, PhonePromptPayload.reply(message));
    }

    private static void tell(ServerPlayer player, Component message, ChatFormatting colour) {
        player.displayClientMessage(message.copy().withStyle(colour), true);
    }

    /** Een bericht sturen vanaf de telefoon (de app Messages): naar één speler, of leeg = naar iedereen met I.M.T. */
    public static void sendMessage(ServerPlayer player, String to, String text) {
        com.senne.imttech.comms.ImtComms.sendFromPhone(player, to.strip(), text);
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
        WRONG_PIN.clear();
        LOCKED_UNTIL.clear();
    }
}
