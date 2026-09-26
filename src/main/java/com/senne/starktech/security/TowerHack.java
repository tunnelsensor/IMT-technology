package com.senne.starktech.security;

import com.senne.starktech.network.HackResultPayload;
import com.senne.starktech.network.HackScreenPayload;
import com.senne.starktech.network.ModNetwork;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * De Stark Tower overnemen met een laptop.
 *
 * 1. Je klikt met de laptop op de beveiligingskern: de hack begint. Het alarm gaat af, en alle
 *    beveiligingsdrones van de toren komen op je af.
 * 2. In je spel opent het hack-scherm (client/HackScreen) met de minigame. Het spel loopt door:
 *    terwijl jij hackt, schieten de torentjes en drones gewoon op je.
 * 3. Ben je klaar, dan zegt je spel dat tegen de server. Die kijkt of het klopt: sta je nog bij
 *    de kern, leef je nog, en duurde het niet onmogelijk kort? Dan is de toren van jou.
 *
 * Loop je weg (verder dan MAX_DISTANCE), ga je dood, of sluit je het scherm, dan is de hack
 * afgebroken en moet je opnieuw beginnen.
 */
public final class TowerHack {
    private TowerHack() {}

    /** Zo dicht bij de kern moet je blijven (in blokken). */
    public static final double MAX_DISTANCE = 6.0;
    /** Sneller dan dit kan niemand de minigame uitspelen (in ticks): anders klopt er iets niet. */
    public static final int MIN_TICKS = 80;

    private record Session(BlockPos core, long startedAt) {}

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    /** Met de laptop op de kern geklikt. */
    public static void start(ServerPlayer player, BlockPos corePos) {
        ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(corePos) instanceof SecurityCoreBlockEntity core)) {
            return;
        }
        if (SecurityCores.isFriend(level, core, player)) {
            player.displayClientMessage(Component.translatable("security.starktech.msg.already_yours")
                    .withStyle(ChatFormatting.AQUA), true);
            return;
        }
        SESSIONS.put(player.getUUID(), new Session(corePos.immutable(), level.getGameTime()));
        ModNetwork.sendTo(player, new HackScreenPayload(corePos, true));
        // Alarm! De drones van deze kern komen allemaal op je af.
        level.playSound(null, corePos, SoundEvents.BELL_BLOCK, SoundSource.BLOCKS, 2.0f, 0.6f);
        for (SecurityDroneEntity drone : level.getEntitiesOfClass(SecurityDroneEntity.class, core.area(),
                drone -> corePos.equals(drone.home()))) {
            if (SecurityCores.isIntruder(level, core, player)) {
                drone.setTarget(player);
            }
        }
        player.displayClientMessage(Component.translatable("security.starktech.msg.hack_started")
                .withStyle(ChatFormatting.RED), true);
    }

    /** Je spel meldt: minigame klaar, of afgebroken. */
    public static void handleResult(ServerPlayer player, HackResultPayload payload) {
        Session session = SESSIONS.remove(player.getUUID());
        if (session == null || !session.core().equals(payload.pos()) || !payload.done()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        long took = level.getGameTime() - session.startedAt();
        if (took < MIN_TICKS || !player.isAlive() || !closeEnough(player, session.core())
                || !(level.getBlockEntity(session.core()) instanceof SecurityCoreBlockEntity core)) {
            player.displayClientMessage(Component.translatable("security.starktech.msg.hack_failed")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        core.setOwner(player);
        level.playSound(null, session.core(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0f, 1.4f);
        player.displayClientMessage(Component.translatable("security.starktech.msg.hack_done")
                .withStyle(ChatFormatting.GREEN), true);
        player.sendSystemMessage(Component.translatable("security.starktech.msg.tower_yours")
                .withStyle(ChatFormatting.AQUA));
        // Wie jou nu nog aanvalt, stopt daarmee.
        for (SecurityDroneEntity drone : level.getEntitiesOfClass(SecurityDroneEntity.class, core.area(),
                drone -> session.core().equals(drone.home()))) {
            drone.setTarget(null);
        }
    }

    /** Elke seconde: sta je nog bij de kern, en leef je nog? Zo niet, dan stopt de hack. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            check(player);
        }
    }

    /** Sta je nog bij de kern, en leef je nog? Zo niet, dan stopt de hack (en sluit je scherm). */
    public static void check(ServerPlayer player) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        if (!player.isAlive() || !closeEnough(player, session.core())) {
            SESSIONS.remove(player.getUUID());
            ModNetwork.sendTo(player, new HackScreenPayload(session.core(), false));
            player.displayClientMessage(Component.translatable("security.starktech.msg.hack_broken")
                    .withStyle(ChatFormatting.RED), true);
        }
    }

    private static boolean closeEnough(ServerPlayer player, BlockPos core) {
        return player.position().distanceTo(Vec3.atCenterOf(core)) <= MAX_DISTANCE;
    }

    /** Is deze speler nu aan het hacken? (Voor de test.) */
    public static boolean isHacking(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SESSIONS.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SESSIONS.clear();
    }
}
