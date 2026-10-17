package com.senne.imttech.suit;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.imt.ImtCooldown;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.imt.ImtTargeting;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.workbench.SuitUpgrades;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Mini-raketten uit het harnas.
 *
 * Met het hele harnas aan, een raketpakket op de borstplaat en de bril op, schiet je een
 * salvo van zes raketten uit de buizen op je rug (meer met raketmagazijnen, harder met
 * raketkoppen: upgrades aan de harnas-werkbank). Ze gaan om de beurt links en rechts weg en
 * verdelen zich over je gemarkeerde doelwitten (toets R). Heb je niets gemarkeerd, dan gaan
 * ze allemaal op het wezen waar je naar kijkt; en kijk je naar niets, dan vliegen ze naar de
 * plek waar je naar kijkt.
 *
 * Een salvo kost bril-energie en daarna moet het pakket herladen (een wachttijd). Munitie is
 * er niet: het pakket maakt ze zelf.
 */
public final class SuitRockets {
    private SuitRockets() {}

    /** Een salvo dat nog bezig is: welke doelwitten, en hoeveel raketten er al weg zijn. */
    private static final class Salvo {
        final List<LivingEntity> targets;
        final @Nullable Vec3 aimPoint;
        /** Hoeveel raketten dit salvo telt, en hoeveel harder ze aankomen (de upgrades). */
        final int size;
        final float damageScale;
        int fired;
        long nextTick;

        Salvo(List<LivingEntity> targets, @Nullable Vec3 aimPoint, int size, float damageScale, long now) {
            this.targets = targets;
            this.aimPoint = aimPoint;
            this.size = size;
            this.damageScale = damageScale;
            this.nextTick = now;
        }
    }

    /** Hoeveel raketten een salvo van deze speler telt: 6, plus 2 per raketmagazijn. */
    public static int salvoSize(Player player) {
        return Math.round(ImtConfig.Suit.ROCKET_SALVO) + SuitUpgrades.magazinesOf(player) * Math.round(ImtConfig.Workbench.MAGAZINE_ROCKETS);
    }

    /** Hoeveel harder de raketten van deze speler aankomen: +40% per raketkop. */
    public static float damageScale(Player player) {
        return (float) (1.0 + SuitUpgrades.warheadsOf(player) * ImtConfig.Workbench.WARHEAD_DAMAGE);
    }

    private static final Map<UUID, Salvo> SALVOS = new HashMap<>();

    /** Toets (of menu, of stem): vuur een salvo af, als dat mag. */
    public static void fire(ServerPlayer player) {
        if (!TechSuitItem.fullSet(player)) {
            ImtServer.send(player, Component.translatable("suit.imttech.msg.rockets_need_suit"),
                    ImtMessagePayload.Level.WARNING);
            return;
        }
        if (!RocketPackItem.worn(player)) {
            ImtServer.send(player, Component.translatable("suit.imttech.msg.no_rocket_pack"),
                    ImtMessagePayload.Level.WARNING);
            return;
        }
        ItemStack glasses = ImtData.getWornGlasses(player);
        if (glasses.isEmpty() || !ImtData.isAuthorized(glasses, player) || SALVOS.containsKey(player.getUUID())) {
            return;
        }
        if (!ImtServer.isReady(player, ImtCooldown.ROCKETS)) {
            ImtServer.send(player, Component.translatable("suit.imttech.msg.rockets_reloading"),
                    ImtMessagePayload.Level.WARNING);
            return;
        }
        if (!ImtData.tryUseEnergy(glasses, ImtConfig.Suit.ROCKET_COST)) {
            ImtServer.send(player, Component.translatable("imt.imttech.msg.not_enough_energy",
                    com.senne.imttech.config.Numbers.text(ImtConfig.Suit.ROCKET_COST)), ImtMessagePayload.Level.WARNING);
            return;
        }

        List<LivingEntity> targets = pickTargets(player);
        Vec3 aim = targets.isEmpty() ? lookPoint(player) : null;
        int size = salvoSize(player);
        SALVOS.put(player.getUUID(), new Salvo(targets, aim, size, damageScale(player), player.level().getGameTime()));
        ImtServer.startCooldown(player, ImtCooldown.ROCKETS, Math.round(ImtConfig.Suit.ROCKET_COOLDOWN));
        ImtServer.send(player, targets.isEmpty()
                        ? Component.translatable("suit.imttech.msg.rockets_away_free", size)
                        : Component.translatable("suit.imttech.msg.rockets_away", size, targets.size()),
                ImtMessagePayload.Level.INFO);
    }

    /** Je gemarkeerde doelwitten die dichtbij genoeg zijn, of anders wat je aankijkt. */
    private static List<LivingEntity> pickTargets(ServerPlayer player) {
        double range = ImtConfig.Suit.ROCKET_RANGE;
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity marked : ImtServer.getMarkedTargets(player)) {
            if (marked.distanceToSqr(player) <= range * range && ImtTargeting.canTarget(player, marked)) {
                targets.add(marked);
            }
        }
        if (targets.isEmpty()) {
            LivingEntity looked = ImtServer.lookTarget(player, range);
            if (looked != null && ImtTargeting.canTarget(player, looked)) {
                targets.add(looked);
            }
        }
        return targets;
    }

    /** Het blok waar je naar kijkt (of een plek ver voor je, als je naar de lucht kijkt). */
    private static Vec3 lookPoint(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1.0f).scale(ImtConfig.Suit.ROCKET_RANGE));
        return player.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player)).getLocation();
    }

    /** Elke tick: schiet de volgende raket van een lopend salvo weg. */
    public static void tick(ServerPlayer player) {
        Salvo salvo = SALVOS.get(player.getUUID());
        if (salvo == null) {
            return;
        }
        long now = player.level().getGameTime();
        if (now < salvo.nextTick) {
            return;
        }
        // Harnas uit, of dood? Dan stopt het salvo (de energie is al betaald).
        if (!player.isAlive() || !RocketPackItem.worn(player)) {
            SALVOS.remove(player.getUUID());
            return;
        }
        launchOne(player, salvo);
        salvo.fired++;
        salvo.nextTick = now + Math.round(ImtConfig.Suit.ROCKET_INTERVAL);
        if (salvo.fired >= salvo.size) {
            SALVOS.remove(player.getUUID());
        }
    }

    /**
     * Eén raket uit de linker- of rechterbuis op je rug. Hij schiet schuin omhoog en naar
     * buiten weg; pas daarna draait hij naar zijn doelwit.
     */
    private static void launchOne(ServerPlayer player, Salvo salvo) {
        ServerLevel level = player.serverLevel();
        MiniRocketEntity rocket = ModEntities.MINI_ROCKET.get().create(level);
        if (rocket == null) {
            return;
        }
        // Waar je lichaam naartoe staat (niet je hoofd): daar zitten de buizen aan vast.
        double yaw = Math.toRadians(player.yBodyRot);
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-forward.z, 0, forward.x);
        double side = salvo.fired % 2 == 0 ? 1 : -1;
        Vec3 from = player.position().add(0, 1.45, 0)
                .add(forward.scale(-0.4))
                .add(right.scale(0.22 * side));
        // Omhoog, een beetje naar buiten en een beetje naar voren; elke raket net iets anders
        // (na vier paar begint het weer smal, anders waaiert een groot salvo te ver uit).
        double spread = 0.35 + 0.15 * ((salvo.fired / 2) % 4);
        Vec3 direction = new Vec3(0, 1, 0)
                .add(right.scale(spread * side))
                .add(player.getViewVector(1.0f).scale(0.6));

        LivingEntity target = null;
        if (!salvo.targets.isEmpty()) {
            target = salvo.targets.get(salvo.fired % salvo.targets.size());
            if (!target.isAlive()) {
                // Die is al neer: neem een ander doelwit uit het salvo dat nog leeft.
                target = salvo.targets.stream().filter(LivingEntity::isAlive).findFirst().orElse(null);
            }
        }
        Vec3 aim = target == null && !salvo.targets.isEmpty()
                ? salvo.targets.get(0).position()
                : salvo.aimPoint;
        rocket.launch(player, from, direction, target, aim);
        rocket.setDamageScale(salvo.damageScale);
        level.addFreshEntity(rocket);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS,
                0.8f, 1.3f + level.random.nextFloat() * 0.3f);
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        SALVOS.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        SALVOS.clear();
    }
}
