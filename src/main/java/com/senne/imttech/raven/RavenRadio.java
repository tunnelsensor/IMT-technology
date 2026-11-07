package com.senne.imttech.raven;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Wat je door de coms hoort (hoofdstuk 60, stap 4, jouw antwoord 4: "je hoort dan door de coms 'bb-2358 come in'").
 * Elke soldaat heeft een radio op zijn rug en een roepnaam (BB-xxxx, zie RavenSoldierEntity.callsign); wie binnen
 * {@code [guards] radioRange} blokken van het gesprek staat, hoort het geknetter en leest de zin in grijs cursief.
 */
public final class RavenRadio {
    private RavenRadio() {}

    /** Hij grijpt naar zijn radio (de eerste seconde van het gesprek): een klik en geknetter bij hem. */
    public static void grab(ServerLevel level, RavenSoldierEntity caller) {
        level.playSound(null, caller.getX(), caller.getEyeY(), caller.getZ(), ModSounds.RAVEN_RADIO.get(), SoundSource.HOSTILE, 0.8f, 1.1f);
    }

    /** Het gesprek is afgemaakt: "Contact!" (of "Man down!", stap 5), en de centrale antwoordt. */
    public static void contact(ServerLevel level, RavenSoldierEntity caller, boolean body) {
        say(level, caller.position(), Component.translatable(body ? "raven.imttech.radio.man_down" : "raven.imttech.radio.contact",
                caller.callsign()), true);
    }

    /** Er komt een team aan. */
    public static void backupOnTheWay(ServerLevel level, RavenSoldierEntity caller) {
        say(level, caller.position(), Component.translatable("raven.imttech.radio.backup", caller.callsign()), false);
    }

    /** Een half gesprek: de centrale probeert hem terug te krijgen. */
    public static void comeIn(ServerLevel level, RavenSoldierEntity caller) {
        say(level, caller.position(), Component.translatable("raven.imttech.radio.come_in", caller.callsign(), caller.callsign()), true);
    }

    /** Het team komt binnen (bij de ingang). */
    public static void teamEntering(ServerLevel level, BlockPos entry) {
        say(level, Vec3.atCenterOf(entry), Component.translatable("raven.imttech.radio.team"), true);
    }

    /** Het alarm is voorbij. */
    public static void standDown(ServerLevel level, BlockPos entry) {
        say(level, Vec3.atCenterOf(entry), Component.translatable("raven.imttech.radio.stand_down"), false);
    }

    /** Naar de spelers in de buurt: de zin, en (als {@code crackle}) het geknetter van hun kant van de radio. */
    private static void say(ServerLevel level, Vec3 at, Component line, boolean crackle) {
        double range = Math.max(8.0, ImtConfig.Guards.RADIO_RANGE);
        Component styled = line.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(at) > range * range) {
                continue;
            }
            player.displayClientMessage(styled, false);
            if (crackle) {
                level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), ModSounds.RAVEN_RADIO.get(), SoundSource.HOSTILE, 0.35f, 0.9f);
            }
        }
    }
}
