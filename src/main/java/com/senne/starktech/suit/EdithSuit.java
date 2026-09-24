package com.senne.starktech.suit;

import com.senne.starktech.edith.EdithCooldown;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.edith.StarkAdvancements;
import com.senne.starktech.edith.EdithTargeting;
import com.senne.starktech.network.BeamEffectPayload;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.network.SuitFlightPayload;
import com.senne.starktech.registry.ModDamageTypes;
import com.senne.starktech.registry.ModSounds;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Wat het Stark-harnas doet zolang je de bril op hebt.
 *
 * Alles staat hier bij elkaar, want het harnas werkt als één geheel: hoeveel stukken je
 * draagt bepaalt hoeveel schade eraf gaat, en pas met alle drie kun je vliegen.
 *
 * Alles kost energie uit de bril. Raakt die leeg, dan val je gewoon uit de lucht - dus hou
 * je batterij in de gaten.
 */
public final class EdithSuit {
    private EdithSuit() {}

    /**
     * Wie wij hebben laten vliegen.
     *
     * We onthouden dat, want we mogen het vliegen alleen weer afnemen van mensen aan wie wij
     * het gegeven hebben. Zou een andere mod (of creative) iemand laten vliegen, dan blijft
     * dat zo.
     */
    private static final Set<UUID> FLYING_BY_SUIT = new HashSet<>();

    /**
     * Wie zijn stuwraketten aan heeft staan. Het vliegen zelf (stuwkracht, vaart, zwaartekracht)
     * rekent het spel van de speler uit, zie client/SuitFlight; de server betaalt de energie,
     * vangt de val op en laat iedereen de straalvlammen zien.
     */
    private static final Set<UUID> ENGAGED = new HashSet<>();

    // ================================================================ Elke tick

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            // Daar bemoeien we ons niet mee; wel onthouden dat het vliegen niet van ons is.
            FLYING_BY_SUIT.remove(player.getUUID());
            return;
        }
        boolean mayFly = mayFly(player);
        Abilities abilities = player.getAbilities();

        if (mayFly) {
            // "mayfly" zetten we aan om twee redenen: een server gooit je er anders uit omdat je
            // "te lang zweeft", en dubbel op spatie tikken wordt zo het teken om de stuwraketten
            // aan te zetten. Het gewone creative-vliegen zelf zetten we meteen weer uit.
            if (!abilities.mayfly) {
                abilities.mayfly = true;
                FLYING_BY_SUIT.add(player.getUUID());
                player.onUpdateAbilities();
                EdithServer.send(player, Component.translatable("suit.starktech.msg.flight_ready"),
                        EdithMessagePayload.Level.INFO);
                StarkAdvancements.award(player, StarkAdvancements.SUIT);
            }
            if (abilities.flying) {
                abilities.flying = false;
                player.onUpdateAbilities();
            }
            if (ENGAGED.contains(player.getUUID())) {
                thrusters(player);
            }
            return;
        }
        // Geen (volledig) harnas meer, bril af of batterij leeg: het vliegen stopt.
        boolean wasEngaged = ENGAGED.remove(player.getUUID());
        if (wasEngaged) {
            ModNetwork.sendTo(player, new SuitFlightPayload(false));
        }
        if (FLYING_BY_SUIT.remove(player.getUUID())) {
            abilities.mayfly = false;
            abilities.flying = false;
            player.onUpdateAbilities();
            if (wasEngaged && !player.onGround()) {
                EdithServer.send(player, Component.translatable("suit.starktech.msg.flight_lost"),
                        EdithMessagePayload.Level.ALERT);
            }
        }
    }

    /** Mag deze speler nu met het harnas vliegen? Hele harnas, eigen bril, energie. */
    private static boolean mayFly(ServerPlayer player) {
        return StarkSuitItem.fullSet(player) && StarkSuitItem.isPowered(player);
    }

    /**
     * Jouw spel zet de stuwraketten aan (dubbel spatie) of uit (geland, of nog eens dubbel spatie).
     * Mag het niet, dan zeggen we terug dat ze uit blijven.
     */
    public static void setEngaged(ServerPlayer player, boolean engaged) {
        if (engaged && !player.isCreative() && !player.isSpectator() && mayFly(player)) {
            ENGAGED.add(player.getUUID());
            player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.5f, 1.6f);
            return;
        }
        ENGAGED.remove(player.getUUID());
        if (engaged) {
            ModNetwork.sendTo(player, new SuitFlightPayload(false));
        }
    }

    public static boolean isEngaged(ServerPlayer player) {
        return ENGAGED.contains(player.getUUID());
    }

    /**
     * Zolang de stuwraketten aan staan: geen valschade (je remt zelf af), energie betalen en
     * straalvlammen onder je laarzen die iedereen ziet.
     */
    private static void thrusters(ServerPlayer player) {
        player.resetFallDistance();
        if (player.onGround()) {
            return; // op de grond kosten ze niets; je spel zet ze zo meteen zelf uit
        }
        ServerLevel level = player.serverLevel();
        // Hoe snel je gaat weet de server uit wat jouw spel doorstuurt, niet uit zijn eigen
        // "deltaMovement": die houdt hij voor spelers niet bij.
        double speed = player.getKnownMovement().length();
        if (player.tickCount % 2 == 0) {
            jets(level, player, speed);
        }
        // Eén keer per seconde afrekenen, niet elke tick.
        if (player.tickCount % 20 != 0) {
            return;
        }
        int cost = speed > SuitBalance.BOOST_SPEED ? SuitBalance.DRAIN_BOOST
                : speed > SuitBalance.CRUISE_SPEED ? SuitBalance.DRAIN_FLY : SuitBalance.DRAIN_HOVER;
        ItemStack glasses = EdithData.getWornGlasses(player);
        if (glasses.isEmpty() || !EdithData.tryUseEnergy(glasses, cost)) {
            return; // de volgende tick merkt dat de batterij leeg is en zet het vliegen uit
        }
        StarkAdvancements.award(player, StarkAdvancements.FLY);
    }

    /** Een blauwe straalvlam onder elke laars, en een vonkje. Harder vliegen = meer vuur. */
    private static void jets(ServerLevel level, ServerPlayer player, double speed) {
        double yaw = Math.toRadians(player.yBodyRot);
        // Een halve voet opzij, links en rechts van het midden van je lichaam.
        double sideX = Math.cos(yaw) * 0.14;
        double sideZ = Math.sin(yaw) * 0.14;
        int flames = speed > SuitBalance.CRUISE_SPEED ? 2 : 1;
        for (int side = -1; side <= 1; side += 2) {
            double x = player.getX() + sideX * side;
            double z = player.getZ() + sideZ * side;
            for (int i = 0; i < flames; i++) {
                // Met aantal 0 is (0, -1, 0) de richting en 0.25 de snelheid: recht naar beneden.
                level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, player.getY() - 0.05, z,
                        0, 0.0, -1.0, 0.0, 0.25);
            }
        }
        if (level.random.nextInt(4) == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() - 0.1, player.getZ(),
                    1, 0.1, 0.05, 0.1, 0.02);
        }
    }

    // ================================================================ Schade opvangen

    /**
     * Het harnas neemt een deel van elke klap weg zolang het stroom heeft: 20% per gedragen
     * stuk, dus 60% met het hele pak. Dat deel betaalt de bril.
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int pieces = StarkSuitItem.wornPieces(player);
        if (pieces == 0 || !StarkSuitItem.isPowered(player)) {
            return;
        }
        DamageSource source = event.getSource();
        // Wat door alles heen gaat (de leegte, /kill) houdt een harnas ook niet tegen.
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        float absorbed = event.getAmount() * SuitBalance.POWERED_REDUCTION_PER_PIECE * pieces;
        int cost = (int) Math.ceil(absorbed * SuitBalance.REDUCTION_COST_PER_DAMAGE);
        ItemStack glasses = EdithData.getWornGlasses(player);
        if (glasses.isEmpty() || !EdithData.tryUseEnergy(glasses, cost)) {
            return; // geen energie: de klap komt gewoon aan
        }
        event.setAmount(event.getAmount() - absorbed);
        player.serverLevel().sendParticles(ParticleTypes.ELECTRIC_SPARK,
                player.getX(), player.getY() + player.getBbHeight() / 2, player.getZ(), 6, 0.4, 0.5, 0.4, 0.05);
    }

    /** De laarzen vangen de landing op: tot 40 blokken val je zonder schade. */
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!hasBoots(player) || !StarkSuitItem.isPowered(player)) {
            return;
        }
        if (event.getDistance() > SuitBalance.SAFE_FALL) {
            return; // van zo hoog helpt zelfs een repulsor niet meer
        }
        ItemStack glasses = EdithData.getWornGlasses(player);
        if (glasses.isEmpty() || !EdithData.tryUseEnergy(glasses, SuitBalance.LANDING_COST)) {
            return;
        }
        event.setCanceled(true);
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY(), player.getZ(), 12, 0.4, 0.1, 0.4, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BEACON_DEACTIVATE,
                SoundSource.PLAYERS, 0.4f, 1.8f);
    }

    private static boolean hasBoots(ServerPlayer player) {
        return player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).getItem() instanceof StarkSuitItem;
    }

    // ================================================================ Repulsor

    /**
     * Een repulsorstoot uit je handpalm: een rechte straal die alles op zijn weg raakt en
     * wegduwt.
     *
     * Je hebt de borstplaat nodig (daar zit de reactor) en energie in je bril. Je eigen
     * drones en huisdieren raakt hij niet; daarvoor kijken we net als de drones naar
     * EdithTargeting.
     */
    public static void repulsor(ServerPlayer player) {
        if (!(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST)
                .getItem() instanceof StarkSuitItem)) {
            EdithServer.send(player, Component.translatable("suit.starktech.msg.no_chest"),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        ItemStack glasses = EdithData.getWornGlasses(player);
        if (glasses.isEmpty() || !EdithData.isAuthorized(glasses, player)) {
            return;
        }
        // Je houdt de aanvalsknop ingedrukt, dus dit komt vaak binnen: stil negeren tot de
        // repulsor weer geladen is. (Je eigen spel wacht ook al, maar de server beslist.)
        if (!EdithServer.isReady(player, EdithCooldown.REPULSOR)) {
            return;
        }
        if (!EdithData.tryUseEnergy(glasses, SuitBalance.REPULSOR_COST)) {
            EdithServer.send(player, Component.translatable("edith.starktech.msg.not_enough_energy",
                    SuitBalance.REPULSOR_COST), EdithMessagePayload.Level.WARNING);
            return;
        }

        ServerLevel level = player.serverLevel();
        Vec3 from = player.getEyePosition().subtract(0, 0.2, 0);
        Vec3 to = from.add(player.getViewVector(1.0f).scale(SuitBalance.REPULSOR_RANGE));

        int hits = 0;
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(from, to).inflate(SuitBalance.REPULSOR_WIDTH),
                entity -> entity != player && entity.isAlive() && EdithTargeting.canTarget(player, entity))) {
            // Alleen wat echt dicht bij de straal staat, niet alles in het blok eromheen.
            if (target.getBoundingBox().inflate(SuitBalance.REPULSOR_WIDTH).clip(from, to).isEmpty()) {
                continue;
            }
            target.hurt(ModDamageTypes.droneLaser(level, player, player), SuitBalance.REPULSOR_DAMAGE);
            Vec3 push = target.position().subtract(player.position()).normalize()
                    .scale(SuitBalance.REPULSOR_KNOCKBACK);
            target.push(push.x, 0.15, push.z);
            target.hurtMarked = true;
            hits++;
        }

        EdithServer.startCooldown(player, EdithCooldown.REPULSOR, SuitBalance.REPULSOR_COOLDOWN);
        ModNetwork.sendNear(level, player.position(), 64,
                new BeamEffectPayload(from, to, BeamEffectPayload.Kind.REPULSOR));
        // Iedereen in de buurt hoort het schot, een beetje anders van toon elke keer.
        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.REPULSOR_FIRE.get(),
                SoundSource.PLAYERS, 1.0f, 0.95f + level.random.nextFloat() * 0.1f);
        // Een klein zetje naar achteren: een repulsor duwt jou ook een beetje.
        Vec3 back = player.getViewVector(1.0f).scale(-SuitBalance.REPULSOR_RECOIL);
        player.push(back.x, 0.0, back.z);
        player.hurtMarked = true;
        // Geen melding of stem bij een treffer: het schot zelf (geluid en straal) zegt genoeg.
        if (hits > 0) {
            StarkAdvancements.award(player, StarkAdvancements.REPULSOR);
        }
    }

    // ================================================================ Opruimen

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        FLYING_BY_SUIT.remove(event.getEntity().getUUID());
        ENGAGED.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        FLYING_BY_SUIT.clear();
        ENGAGED.clear();
    }
}
