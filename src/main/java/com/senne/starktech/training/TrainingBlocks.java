package com.senne.starktech.training;

import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.network.TrainingActionPayload;
import com.senne.starktech.network.TrainingOpenPayload;
import com.senne.starktech.security.BaseTrust;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * De trainingsblokken aan de kant van de server (hoofdstuk 26): klikken op het Training Start-blok (beginnen, stoppen
 * en de instellingen), op het Training Goal-blok stappen, en wat er uit hun schermen terugkomt.
 *
 * Wie mag een training beginnen? De eigenaar van de basis, zijn team en vertrouwde spelers (Access op de desktop), of
 * wie het Start-blok neerzette. Anders zou een inbreker jouw verdediging op trainingsschoten kunnen zetten.
 */
public final class TrainingBlocks {
    private TrainingBlocks() {}

    /** Zo dichtbij moet je staan (blokken). */
    private static final double REACH = 6.0;

    /** Een klik op het Training Start-blok: beginnen, stoppen, of (sneak) de instellingen. */
    public static void useStart(ServerPlayer player, BlockPos pos) {
        useStart(player, pos, false);
    }

    public static void useStart(ServerPlayer player, BlockPos pos, boolean sneak) {
        ServerLevel level = player.serverLevel();
        if (player.distanceToSqr(pos.getCenter()) > REACH * REACH
                || !(level.getBlockEntity(pos) instanceof TrainingStartBlockEntity start)) {
            return;
        }
        if (sneak) {
            if (!BaseTrust.mayEdit(player, start)) {
                player.displayClientMessage(Component.literal(refusal(level, start)), true);
                return;
            }
            ModNetwork.sendTo(player, new TrainingOpenPayload(pos, false, start.settings().save()));
            return;
        }
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, pos);
        if (core == null) {
            player.displayClientMessage(Component.literal("Put the Training Start block in the area of a security core"), true);
            return;
        }
        boolean trusted = BaseTrust.trustedHere(level, pos, player) || player.getUUID().equals(start.owner());
        Training.Session running = Training.at(level, core.getBlockPos());
        if (running != null) {
            if (trusted || running.trainee().equals(player.getUUID())) {
                Training.end(level, running, Training.Result.STOPPED);
                player.displayClientMessage(Component.literal("Training " + running.settings().name() + " stopped"), true);
            } else {
                player.displayClientMessage(Component.literal("A training is running here: " + running.settings().name()
                        + " (" + running.traineeName() + ")"), true);
            }
            return;
        }
        if (!trusted) {
            player.displayClientMessage(Component.literal("Only the owner of this base, their team and trusted players can start a training"),
                    true);
            return;
        }
        String error = Training.start(level, core, player, start.settings(), pos);
        if (error != null) {
            player.displayClientMessage(Component.literal(error), true);
            return;
        }
        player.displayClientMessage(Component.literal("Training " + start.settings().name() + ": get ready, you are the trainee"
                + (player.isCreative() ? " (in creative nobody shoots at you)" : "")), true);
    }

    /** De trainee stapte op een Training Goal-blok in het gebied van zijn training: geslaagd. */
    public static void goal(ServerPlayer player, BlockPos pos) {
        Training.Session session = Training.of(player.getUUID());
        if (session == null || !session.running()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, pos);
        if (core != null && session.core().pos().equals(core.getBlockPos()) && session.core().dimension() == level.dimension()) {
            Training.end(level, session, Training.Result.GOAL);
        }
    }

    /** Wat er uit het instelscherm van een Start-blok terugkomt (alleen voor wie hem mag instellen). */
    public static void handleAction(ServerPlayer player, TrainingActionPayload payload) {
        ServerLevel level = player.serverLevel();
        if (player.distanceToSqr(payload.pos().getCenter()) > REACH * REACH) {
            return;
        }
        if (level.getBlockEntity(payload.pos()) instanceof TrainingStartBlockEntity start && BaseTrust.mayEdit(player, start)) {
            start.setSettings(Training.Settings.load(payload.data()));
            player.displayClientMessage(Component.literal("Training " + start.settings().name() + " saved"), true);
        }
    }

    /** "This Training Start block belongs to Senne" (of: zit op slot). */
    private static String refusal(ServerLevel level, BaseTrust.Placed device) {
        String owner = BaseTrust.belongsTo(level, device);
        return owner.isEmpty() ? "This block is locked" : "This block belongs to " + owner;
    }
}
