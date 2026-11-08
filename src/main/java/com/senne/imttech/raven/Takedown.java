package com.senne.imttech.raven;

import com.senne.imttech.config.ImtConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Een sluipaanval met een zwaard (8 november, jouw wens: "zorg dat je met een zwaard een guard kunt neersteken langs
 * achter zodat je die stealth kunt killen"), zoals in Hitman.
 *
 * Sla je een Raven-soldaat met een <b>zwaard</b> terwijl je <b>achter hem</b> staat (in de zone achter zijn rug,
 * {@code [guards] takedownAngle}, gemeten vanaf waar zijn hoofd heen kijkt) en hij nog niet met iemand vecht of via de
 * radio roept, dan grijp je hem. Sinds je tweede opmerking ("een leuke animatie, zodat het geen instakill is maar een
 * beetje tijd kost") is dat een korte <b>worsteling</b> van {@code takedownSeconds} (2 seconden):
 * <ol>
 *   <li>je trekt hem naar je toe, vlak voor je met zijn rug naar je toe, en je staat zolang stil (je benen zijn op slot);</li>
 *   <li>hij worstelt: zijn handen naar je arm om zijn nek, zijn hoofd schudt, hij trapt (de animatie in je spel, zie
 *       TakedownPose; het beeld schudt een beetje mee);</li>
 *   <li>hij wordt slap, zakt door zijn knieën en valt achterover;</li>
 *   <li>een laatste steek, en hij is neer. Zijn lichaam blijft liggen zoals altijd, met zijn buit erin.</li>
 * </ol>
 * Het is stil: de andere bewakers horen niets (er gaat geen lawaai naar {@link Noises}). Maar wie het ziet, ziet een
 * "lichaam" (Bodies) en komt kijken. Laat je hem los voor het af is (je wordt weggeduwd, je teleporteert, je logt uit),
 * dan komt hij los: hij draait zich om, heeft je meteen, en roept versterking als dat kan.
 *
 * Kijkt hij naar je, of heeft hij al een doelwit, dan is het een gewone slag. Onder je vizier staat "Takedown" zodra het
 * kan, en tijdens de worsteling een balkje (GuardLayer); dat werkt ook zonder bril.
 */
public final class Takedown {
    private Takedown() {}

    /** Wie nu wie vastheeft: de speler en zijn soldaat (alleen op de server). */
    private static final Map<UUID, RavenSoldierEntity> ACTIVE = new HashMap<>();
    /** Het slot op je benen tijdens een sluipaanval. */
    private static final ResourceLocation LOCK_ID = ResourceLocation.fromNamespaceAndPath("imttech", "takedown");
    /** Zo ver voor je houd je hem vast (blokken): dichterbij vult zijn rug je hele scherm, zo zie je zijn hoofd en armen. */
    private static final double HOLD_DISTANCE = 0.95;
    /** Verder dan dit uit elkaar (een duw, een teleport) en hij is los. */
    private static final double BREAK_DISTANCE = 3.0;
    /** Tot dit deel van de sluipaanval worstelt hij (daarna wordt hij slap, zie TakedownPose). */
    private static final float STRUGGLE_UNTIL = 0.55f;

    // ================================================================ Wanneer het kan

    /** Een zwaard (ook een zwaard uit een andere mod, als die het in de tag {@code minecraft:swords} zet). */
    public static boolean isBlade(ItemStack stack) {
        return stack.is(ItemTags.SWORDS) || stack.getItem() instanceof SwordItem;
    }

    /**
     * Staat {@code attacker} in de zone achter de rug van {@code guard}? Recht achter hem is 180 graden van waar hij
     * heen kijkt; met de standaardzone van 180 graden telt alles achter zijn schouders.
     */
    public static boolean behind(Entity attacker, LivingEntity guard) {
        double zone = ImtConfig.Guards.TAKEDOWN_ANGLE;
        if (zone <= 0) {
            return false;
        }
        Vec3 facing = Vec3.directionFromRotation(0, guard.getYHeadRot());
        double angle = RavenSenses.flatAngle(facing, attacker.position().subtract(guard.position()));
        return angle >= 180.0 - zone / 2.0;
    }

    /**
     * Kan {@code player} deze soldaat nu grijpen? {@code state} is wat de soldaat doet: op de server zijn echte toestand,
     * in je spel wat de server meestuurt (zo klopt de hint onder je vizier met wat er gebeurt).
     */
    public static boolean possible(Player player, RavenSoldierEntity guard, RavenAlert state) {
        return guard.isAlive() && !guard.isBeingTakenDown() && state != RavenAlert.COMBAT && state != RavenAlert.CALLING
                && isBlade(player.getMainHandItem()) && behind(player, guard);
    }

    /** Houdt deze speler nu iemand vast voor een sluipaanval? (alleen op de server) */
    public static boolean busy(Player player) {
        RavenSoldierEntity guard = ACTIVE.get(player.getUUID());
        return guard != null && guard.isAlive() && !guard.isRemoved() && player.getUUID().equals(guard.takedownHolder());
    }

    // ================================================================ Gebeurtenissen

    /** Een speler slaat een wezen: is het een soldaat die hem niet ziet, en heeft hij een zwaard? Dan grijpt hij hem. */
    public static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (busy(player)) {
            event.setCanceled(true); // je handen zijn bezet
            return;
        }
        if (!(event.getTarget() instanceof RavenSoldierEntity guard) || player.isSpectator()) {
            return;
        }
        if (guard.getTarget() != null || !possible(player, guard, guard.senses().state())) {
            return;
        }
        event.setCanceled(true); // geen gewone slag (en dus ook geen zwaai die een tweede soldaat raakt)
        start(level, player, guard);
    }

    /** Je benen zitten op slot zonder dat je iemand vasthoudt (iets ging mis)? Dan gaat het slot eraf. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) {
            return;
        }
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && speed.hasModifier(LOCK_ID) && !busy(player)) {
            ACTIVE.remove(player.getUUID());
            lock(player, false);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    // ================================================================ De sluipaanval

    /** Je grijpt hem: naar je toe, met zijn rug naar je toe, en je staat stil. Bij 0 seconden is hij meteen neer (zoals eerst). */
    static void start(ServerLevel level, Player player, RavenSoldierEntity guard) {
        int length = Math.round(ImtConfig.Guards.TAKEDOWN_SECONDS * 20);
        if (length <= 0) {
            finish(level, player, guard);
            return;
        }
        Vec3 away = flatDirection(player, guard);
        Vec3 hold = new Vec3(player.getX() + away.x * HOLD_DISTANCE, guard.getY(), player.getZ() + away.z * HOLD_DISTANCE);
        if (level.noCollision(guard, guard.getBoundingBox().move(hold.subtract(guard.position())))) {
            guard.moveTo(hold.x, hold.y, hold.z, guard.getYRot(), 0.0f); // je spel laat hem er in een paar ticks heen glijden: een ruk
        }
        face(guard, away);
        guard.grab(player, length);
        ACTIVE.put(player.getUUID(), guard);
        lock(player, true);
        player.swing(InteractionHand.MAIN_HAND, true);
        sound(level, guard, SoundEvents.ARMOR_EQUIP_LEATHER, 0.9f, 0.8f);
        sound(level, guard, SoundEvents.PLAYER_HURT, 0.3f, 0.65f); // een gesmoorde kreet
    }

    /** Elke tick van de soldaat die vastgehouden wordt (RavenSoldierEntity.aiStep). */
    static void tick(ServerLevel level, RavenSoldierEntity guard) {
        UUID holder = guard.takedownHolder();
        Player player = holder == null ? null : level.getPlayerByUUID(holder);
        if (player == null || !player.isAlive() || player.isSpectator() || ACTIVE.get(holder) != guard) {
            abort(level, guard, null); // je bent weg (uitgelogd, dood, andere wereld): hij is weer vrij
            return;
        }
        if (player.distanceTo(guard) > BREAK_DISTANCE) {
            abort(level, guard, player); // je liet hem los: hij heeft je gezien
            return;
        }
        // Hij blijft voor je staan, met zijn rug naar je toe (een duw of een stroming verandert daar niets aan).
        guard.setDeltaMovement(0.0, Math.min(0.0, guard.getDeltaMovement().y), 0.0);
        face(guard, flatDirection(player, guard));
        int ticks = guard.nextTakedownTick();
        int length = Math.max(1, guard.takedownLength());
        float progress = (float) ticks / length;
        if (progress < STRUGGLE_UNTIL && ticks % 6 == 0) {
            sound(level, guard, SoundEvents.ARMOR_EQUIP_LEATHER, 0.45f, 0.8f + guard.getRandom().nextFloat() * 0.3f);
        }
        if (progress < STRUGGLE_UNTIL && ticks % 13 == 0) {
            sound(level, guard, SoundEvents.PLAYER_HURT, 0.15f, 0.6f);
        }
        if (ticks == Math.round(length * STRUGGLE_UNTIL)) {
            sound(level, guard, SoundEvents.PLAYER_BREATH, 0.5f, 0.6f); // hij wordt slap
        }
        if (ticks >= length) {
            finish(level, player, guard);
        }
    }

    /** Af: een laatste steek, en hij is neer (stil). Het zwaard slijt zoals bij een gewone slag. */
    static void finish(ServerLevel level, Player player, RavenSoldierEntity guard) {
        ACTIVE.remove(player.getUUID());
        lock(player, false);
        guard.releaseTakedown();
        Vec3 back = guard.position().add(0, 0.5, 0).add(flatDirection(guard, player).scale(0.3));
        level.sendParticles(ParticleTypes.CRIT, back.x, back.y, back.z, 10, 0.2, 0.15, 0.2, 0.15);
        sound(level, guard, SoundEvents.PLAYER_ATTACK_CRIT, 0.45f, 0.7f);
        sound(level, guard, SoundEvents.PLAYER_SMALL_FALL, 0.6f, 0.7f); // hij komt op de grond
        player.swing(InteractionHand.MAIN_HAND, true);
        guard.silenceDeath();
        guard.hurt(level.damageSources().playerAttack(player), Math.max(1000.0f, guard.getMaxHealth() * 50.0f));
        ItemStack blade = player.getMainHandItem();
        if (isBlade(blade)) {
            blade.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        player.resetAttackStrengthTicker();
    }

    /**
     * Hij komt los voor het af is. Met {@code seen}: hij draait zich om naar die speler en heeft hem meteen (en roept dus
     * versterking als dat kan); zonder (de speler is weg): hij is gewoon weer vrij.
     */
    static void abort(ServerLevel level, RavenSoldierEntity guard, @Nullable Player seen) {
        UUID holder = guard.takedownHolder();
        if (holder != null) {
            ACTIVE.remove(holder);
            Player player = level.getServer().getPlayerList().getPlayer(holder);
            if (player != null) {
                lock(player, false);
            }
        }
        guard.releaseTakedown();
        if (seen != null && seen.isAlive() && !seen.isCreative() && !seen.isSpectator()) {
            guard.senses().escaped(level, seen);
        }
    }

    /** De soldaat verdwijnt terwijl iemand hem vastheeft (RavenSoldierEntity.remove): die speler is weer vrij. */
    static void released(ServerLevel level, RavenSoldierEntity guard) {
        abort(level, guard, null);
    }

    // ================================================================ Hulpjes

    /** Je benen op slot (of weer los): niet lopen en niet springen. Het slot gaat niet mee in je opgeslagen wereld. */
    private static void lock(Player player, boolean on) {
        for (Holder<Attribute> attribute : List.of(Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH)) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            if (!on) {
                instance.removeModifier(LOCK_ID);
            } else if (!instance.hasModifier(LOCK_ID)) {
                instance.addTransientModifier(new AttributeModifier(LOCK_ID, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            }
        }
    }

    /** De platte richting van {@code from} naar {@code to} (lengte 1). */
    private static Vec3 flatDirection(Entity from, Entity to) {
        Vec3 flat = new Vec3(to.getX() - from.getX(), 0.0, to.getZ() - from.getZ());
        return flat.lengthSqr() < 1.0e-4 ? Vec3.directionFromRotation(0, from.getYRot()) : flat.normalize();
    }

    /** Hij kijkt deze kant op (zijn lijf en zijn hoofd). */
    private static void face(RavenSoldierEntity guard, Vec3 direction) {
        float yaw = (float) (Mth.atan2(direction.z, direction.x) * Mth.RAD_TO_DEG) - 90.0f;
        guard.setYRot(yaw);
        guard.setYBodyRot(yaw);
        guard.setYHeadRot(yaw);
    }

    private static void sound(ServerLevel level, Entity at, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, at.getX(), at.getEyeY(), at.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    private static void sound(ServerLevel level, Entity at, Holder<SoundEvent> sound, float volume, float pitch) {
        sound(level, at, sound.value(), volume, pitch);
    }
}
