package com.senne.starktech.satellite;

import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.edith.EdithTargeting;
import com.senne.starktech.network.BeamEffectPayload;
import com.senne.starktech.network.ModNetwork;
import com.senne.starktech.registry.ModDamageTypes;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.shield.DomeSettings;
import com.senne.starktech.shield.EdithDomes;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * De satelliet ("orbital strike"). Houdt de aanvallen bij die aan het opladen zijn en laat ze
 * los als de tijd om is.
 *
 * Nieuw sinds fase 8:
 * - hij kan een BEWEGEND doelwit volgen (lock-on): de straal blijft op het wezen gericht;
 * - drie standen (zie SatelliteMode): precisie, bombardement en een schuivende laser;
 * - een schokgolf die wezens wegblaast, en het beeld schudt even bij de inslag.
 */
public final class SatelliteStrikes {
    private SatelliteStrikes() {}

    /** Hoe hoog boven het doel de straal begint (puur voor het effect). */
    private static final double BEAM_HEIGHT = 120.0;

    public static final class Strike {
        final ResourceKey<Level> dimension;
        /** Waar de straal nu op gericht staat (bij lock-on verandert dit elke tick). */
        Vec3 target;
        final UUID ownerId;
        final SatelliteMode mode;
        /** Het wezen dat gevolgd wordt (lock-on), of null bij een vaste plek. */
        final @Nullable UUID lockedId;
        /**
         * Schuivende laser: waar hij naartoe trekt (null = hij blijft op zijn plek), en waar hij
         * begon met vuren (dat onthouden we bij de eerste stap).
         */
        final @Nullable Vec3 sweepTo;
        @Nullable Vec3 sweepFrom;
        /** Ticks tot de inslag. */
        int ticksLeft;
        /** Ticks dat hij nog aan het vuren is (bombardement en sweep duren even). */
        int fireTicksLeft;
        /** Hoe lang hij in totaal vuurt; zo weten we welke inslag de eerste is. */
        final int fireTicks;
        /** De upgrades van de eigenaar op het moment van richten (zie SatelliteStats). */
        final SatelliteUpgrades upgrades;

        Strike(ResourceKey<Level> dimension, Vec3 target, UUID ownerId, SatelliteMode mode,
               @Nullable UUID lockedId, @Nullable Vec3 sweepTo, SatelliteUpgrades upgrades) {
            this.dimension = dimension;
            this.target = target;
            this.ownerId = ownerId;
            this.mode = mode;
            this.lockedId = lockedId;
            this.sweepTo = sweepTo;
            this.upgrades = upgrades;
            this.ticksLeft = SatelliteStats.chargeTicks(upgrades.targeting());
            this.fireTicks = switch (mode) {
                case PRECISION -> 1;
                case BOMBARDMENT -> SatelliteStats.bombardmentShots(upgrades.bombardment()) * EdithBalance.BOMBARDMENT_INTERVAL;
                case SWEEP -> SatelliteStats.sweepTicks(upgrades.sweep());
            };
            this.fireTicksLeft = fireTicks;
        }

        public Vec3 target() {
            return target;
        }

        public int ticksLeft() {
            return ticksLeft;
        }

        public SatelliteMode mode() {
            return mode;
        }
    }

    private static final List<Strike> STRIKES = new ArrayList<>();

    /**
     * Start een aanval. De straal komt na het opladen los. Bij de schuivende laser trekt hij
     * daarna van {@code target} naar {@code sweepTo} (voor de andere standen: null).
     */
    public static void start(ServerLevel level, Vec3 target, UUID ownerId, SatelliteMode mode,
                             @Nullable UUID lockedId, @Nullable Vec3 sweepTo) {
        // Hoe sterk de aanval is, hangt af van de upgrades van de eigenaar (zie SatelliteStats).
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerId);
        SatelliteUpgrades upgrades = owner == null ? SatelliteUpgrades.START : SatelliteWorkshop.get(owner);
        STRIKES.add(new Strike(level.dimension(), target, ownerId, mode, lockedId, sweepTo, upgrades));
        level.playSound(null, target.x, target.y, target.z, ModSounds.SATELLITE_CHARGE.get(), SoundSource.PLAYERS, 3.0f, 0.8f);
    }

    /** De aanval van deze speler die nog aan het opladen is (voor de HUD), of null. */
    public static @Nullable Strike pendingFor(UUID ownerId) {
        for (Strike strike : STRIKES) {
            if (strike.ownerId.equals(ownerId)) {
                return strike;
            }
        }
        return null;
    }

    /** Breekt de aanval van deze speler af (voor "stand down"). Geeft true als er een aanval liep. */
    public static boolean cancel(UUID ownerId) {
        return STRIKES.removeIf(strike -> strike.ownerId.equals(ownerId));
    }

    /** Wereld gesloten: lopende aanvallen vergeten (anders slaan ze in de volgende wereld in). */
    public static void onServerStopped(ServerStoppedEvent event) {
        STRIKES.clear();
    }

    // ================================================================ Elke tick

    public static void onServerTick(ServerTickEvent.Post event) {
        if (STRIKES.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Strike> it = STRIKES.iterator();
        while (it.hasNext()) {
            Strike strike = it.next();
            ServerLevel level = server.getLevel(strike.dimension);
            if (level == null) {
                it.remove();
                continue;
            }
            // Lock-on: loopt het doelwit weg, dan gaat de straal mee.
            if (strike.lockedId != null && level.getEntity(strike.lockedId) instanceof LivingEntity locked && locked.isAlive()) {
                strike.target = locked.position();
            }

            // Opladen: rode richtstraal en een aftelpiepje in de laatste drie seconden.
            if (strike.ticksLeft > 0) {
                strike.ticksLeft--;
                if (strike.ticksLeft % 4 == 0) {
                    sendBeam(level, strike.target, BeamEffectPayload.Kind.SATELLITE_CHARGE);
                }
                if (strike.ticksLeft <= 60 && strike.ticksLeft % 20 == 0) {
                    level.playSound(null, strike.target.x, strike.target.y, strike.target.z,
                            ModSounds.SATELLITE_LOCK.get(), SoundSource.PLAYERS, 1.5f, 1.4f);
                }
                continue;
            }

            // Vuren.
            switch (strike.mode) {
                case PRECISION -> {
                    firePrecision(level, strike);
                    it.remove();
                }
                case BOMBARDMENT -> {
                    if (strike.fireTicksLeft % EdithBalance.BOMBARDMENT_INTERVAL == 0) {
                        fireBombardmentShot(level, strike);
                    }
                    if (--strike.fireTicksLeft <= 0) {
                        it.remove();
                    }
                }
                case SWEEP -> {
                    fireSweepStep(level, strike);
                    if (--strike.fireTicksLeft <= 0) {
                        it.remove();
                    }
                }
            }
        }
    }

    // ================================================================ De drie standen

    /** Precisie: één felle straal, veel schade op één plek, geen blokschade. */
    private static void firePrecision(ServerLevel level, Strike strike) {
        Vec3 pos = strike.target;
        // Onder de schildkoepel van iemand anders? Dan stuit de straal op de koepel.
        Vec3 shield = EdithDomes.blockedAt(level, pos, null, strike.ownerId, DomeSettings.COST_SATELLITE_BEAM);
        if (shield != null) {
            sendBeam(level, shield, BeamEffectPayload.Kind.SATELLITE_PRECISION);
            level.playSound(null, shield.x, shield.y, shield.z, ModSounds.SATELLITE_FIRE.get(), SoundSource.PLAYERS, 3.0f, 1.3f);
            return;
        }
        sendBeam(level, pos, BeamEffectPayload.Kind.SATELLITE_PRECISION);
        visualLightning(level, pos);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(strike.ownerId);
        DamageSource source = ModDamageTypes.orbitalLaser(level, owner);
        double radius = SatelliteStats.precisionRadius(strike.upgrades.precision());
        float damage = SatelliteStats.precisionDamage(strike.upgrades.precision(), strike.upgrades.power());
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(radius))) {
            if (mayHurt(owner, victim)) {
                victim.hurt(source, damage);
            }
        }
        level.playSound(null, pos.x, pos.y, pos.z, ModSounds.SATELLITE_FIRE.get(), SoundSource.PLAYERS, 3.0f, 1.3f);
    }

    /** Bombardement: meerdere inslagen rond het doel, met kraters en een schokgolf. */
    private static void fireBombardmentShot(ServerLevel level, Strike strike) {
        double spread = SatelliteStats.bombardmentSpread(strike.upgrades.bombardment());
        // De eerste inslag zit precies op het doel; daarna eromheen.
        boolean first = strike.fireTicksLeft == strike.fireTicks;
        Vec3 pos = first
                ? strike.target
                : strike.target.add((level.getRandom().nextDouble() - 0.5) * 2 * spread, 0, (level.getRandom().nextDouble() - 0.5) * 2 * spread);
        pos = onGround(level, pos);
        Vec3 shield = EdithDomes.blockedAt(level, pos, null, strike.ownerId, DomeSettings.COST_SATELLITE_IMPACT);
        if (shield != null) {
            sendBeam(level, shield, BeamEffectPayload.Kind.SATELLITE_FIRE);
            level.playSound(null, shield.x, shield.y, shield.z, ModSounds.SATELLITE_FIRE.get(), SoundSource.PLAYERS, 4.0f, 0.9f);
            return;
        }
        sendBeam(level, pos, BeamEffectPayload.Kind.SATELLITE_FIRE);
        visualLightning(level, pos);
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(strike.ownerId);
        Level.ExplosionInteraction interaction = EdithBalance.SATELLITE_BREAKS_BLOCKS
                ? Level.ExplosionInteraction.MOB   // volgt de spelregel mobGriefing
                : Level.ExplosionInteraction.NONE;
        level.explode(owner, pos.x, pos.y, pos.z,
                SatelliteStats.explosionPower(strike.upgrades.bombardment(), strike.upgrades.power()), false, interaction);
        shockwave(level, pos, owner);
        level.playSound(null, pos.x, pos.y, pos.z, ModSounds.SATELLITE_FIRE.get(), SoundSource.PLAYERS, 4.0f, 0.9f);
    }

    /**
     * Schuivende laser: hij kruipt over de grond van het beginpunt naar het eindpunt, gelijkmatig
     * verdeeld over de hele duur, en schroeit alles wat hij raakt.
     */
    private static void fireSweepStep(ServerLevel level, Strike strike) {
        if (strike.sweepFrom == null) {
            strike.sweepFrom = strike.target;
        }
        Vec3 to = strike.sweepTo != null ? strike.sweepTo : strike.sweepFrom;
        double progress = (strike.fireTicks - strike.fireTicksLeft + 1) / (double) strike.fireTicks;
        // Netjes op de grond blijven, ook als het eindpunt hoger of lager ligt.
        strike.target = onGround(level, strike.sweepFrom.lerp(to, progress));
        Vec3 pos = strike.target;
        // Over een schildkoepel heen? Dan schroeit hij over de koepel in plaats van over de grond
        // (om de tick kost dat schildkracht).
        int cost = strike.fireTicksLeft % 2 == 0 ? DomeSettings.COST_SATELLITE_SWEEP : 0;
        Vec3 shield = EdithDomes.blockedAt(level, pos, null, strike.ownerId, cost);
        if (shield != null) {
            if (cost > 0) {
                sendBeam(level, shield, BeamEffectPayload.Kind.SATELLITE_SWEEP);
            }
            return;
        }
        if (strike.fireTicksLeft % 2 == 0) {
            sendBeam(level, pos, BeamEffectPayload.Kind.SATELLITE_SWEEP);
        }
        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(strike.ownerId);
        DamageSource source = ModDamageTypes.orbitalLaser(level, owner);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(pos, pos).inflate(EdithBalance.SWEEP_RADIUS))) {
            // Niet elke tick schade: anders is het meteen voorbij.
            if (victim.hurtTime <= 0 && mayHurt(owner, victim)) {
                victim.hurt(source, SatelliteStats.sweepDamage(strike.upgrades.sweep(), strike.upgrades.power()));
            }
        }
        if (strike.fireTicksLeft % 10 == 0) {
            level.playSound(null, pos.x, pos.y, pos.z, ModSounds.SATELLITE_FIRE.get(), SoundSource.PLAYERS, 2.0f, 1.6f);
        }
    }

    // ================================================================ Hulpjes

    /** Mag de satelliet dit wezen raken? (Nooit de eigenaar zelf of zijn bondgenoten.) */
    private static boolean mayHurt(@Nullable ServerPlayer owner, LivingEntity victim) {
        if (owner == null) {
            return true;
        }
        return victim != owner && EdithTargeting.canTarget(owner, victim);
    }

    /** Duwt alles in de buurt weg van de inslag (en een beetje de lucht in). */
    private static void shockwave(ServerLevel level, Vec3 center, @Nullable ServerPlayer owner) {
        double radius = EdithBalance.SHOCKWAVE_RADIUS;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius))) {
            if (victim == owner) {
                continue;
            }
            Vec3 away = victim.position().subtract(center);
            double distance = Math.max(0.5, away.length());
            if (distance > radius) {
                continue;
            }
            // Dichterbij = harder weggeblazen.
            double power = EdithBalance.SHOCKWAVE_POWER * (1.0 - distance / radius);
            Vec3 push = away.normalize().scale(power).add(0, power * 0.6, 0);
            // push() zet de snelheid; de server stuurt die daarna zelf door naar de spelers.
            victim.push(push.x, push.y, push.z);
        }
    }

    /** Zet een punt netjes op de grond (voor het bombardement en de schuivende laser). */
    private static Vec3 onGround(ServerLevel level, Vec3 pos) {
        int x = (int) Math.floor(pos.x);
        int z = (int) Math.floor(pos.z);
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        // Nooit hoger dan waar we al waren + 3, anders springt de laser over een muur heen.
        double y = Math.min(ground, pos.y + 3);
        return new Vec3(pos.x, y, pos.z);
    }

    /** Een bliksemschicht als effect (doet zelf geen schade en steekt niets in brand). */
    private static void visualLightning(ServerLevel level, Vec3 pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt != null) {
            bolt.setVisualOnly(true);
            bolt.moveTo(pos);
            level.addFreshEntity(bolt);
        }
    }

    private static void sendBeam(ServerLevel level, Vec3 target, BeamEffectPayload.Kind kind) {
        Vec3 top = target.add(0, BEAM_HEIGHT, 0);
        ModNetwork.sendNear(level, target, 200, new BeamEffectPayload(top, target, kind));
    }

    /** Waar de satelliet het doel raakt als het op de grond ligt (voor de sterfteksten en tests). */
    public static BlockPos impactBlock(Strike strike) {
        return BlockPos.containing(strike.target);
    }
}
