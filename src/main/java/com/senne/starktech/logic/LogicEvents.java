package com.senne.starktech.logic;

import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import com.senne.starktech.security.os.EventLog;
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
        boolean valid = core.logic().isValid(core.ip(), card);
        InputDeviceBlock.flash(level, reader, InputLight.BUSY);
        level.playSound(null, reader, ModSounds.EDITH_BLIP.get(), SoundSource.BLOCKS, 0.5f, 1.4f);
        String what = card == null ? "a blank card"
                : "card #" + card.number() + " (level " + card.level() + (valid ? "" : ", not valid here") + ")";
        core.logEvent(EventLog.Type.ENTRY, player.getGameProfile().getName() + " used " + what + " at " + device.name());
    }

    /**
     * De beveiligingskern waar deze lezer of dit paneel bij hoort, als zijn server draait. Zo niet,
     * dan wordt het lampje rood, hoort de speler waarom, en geven we null.
     */
    private static @Nullable SecurityCoreBlockEntity serverFor(ServerLevel level, BlockPos device, ServerPlayer player) {
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, device);
        String problem = core == null ? "logic.starktech.input.no_network"
                : !LogicServer.stats(level, core).online() ? "logic.starktech.input.no_response" : null;
        if (problem == null) {
            return core;
        }
        InputDeviceBlock.flash(level, device, InputLight.DENIED);
        level.playSound(null, device, ModSounds.EDITH_DENIED.get(), SoundSource.BLOCKS, 0.6f, 1.0f);
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
