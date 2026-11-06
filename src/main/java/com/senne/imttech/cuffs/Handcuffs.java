package com.senne.imttech.cuffs;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.imttech.bot.BotEntity;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.raven.RavenSoldierEntity;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.Emp;
import com.senne.imttech.weapons.Flashbang;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Handboeien (hoofdstuk 48, 4 november). Jouw antwoorden: rechtsklik ongeveer twee seconden vasthouden (meteen als hij
 * verblind is door een flashbang, slaapt of bijna dood is); geboeid = niets gebruiken, niet bouwen, geen kisten, en
 * traag; los met een Handcuff Key of door 30 seconden sluipen (instelbaar); vastpakken en meetrekken zoals aan een lijn
 * (tot 3 blokken); ook Raven-soldaten en guard robots.
 *
 * Wie geboeid is, draagt dat als attachment ({@link ModAttachments#CUFFS}, {@link State}): opgeslagen met de speler of
 * het wezen (na opnieuw inloggen ben je nog geboeid) en naar iedereen gestuurd die hem ziet (voor het touw, zie
 * client/cuffs/CuffRenderer). De server houdt bij wie er allemaal geboeid rondlopen ({@link #CUFFED}) en doet elke tick
 * het trekken, het worstelen en de meldingen. Een geboeide speler is op de server geblokkeerd voor alles wat met zijn
 * handen moet: rechtsklikken, slaan, hakken, bouwen, kisten (gaan meteen weer dicht), eten en dingen weggooien.
 */
public final class Handcuffs {
    private Handcuffs() {}

    /** Wat er over iemand met handboeien bewaard wordt: dat hij geboeid is, en wie hem vasthoudt (als iemand dat doet). */
    public record State(boolean cuffed, Optional<UUID> holder) {
        public static final State FREE = new State(false, Optional.empty());

        public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.BOOL.optionalFieldOf("cuffed", false).forGetter(State::cuffed),
                UUIDUtil.CODEC.optionalFieldOf("holder").forGetter(State::holder)).apply(instance, State::new));

        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, State::cuffed,
                ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), State::holder,
                State::new);
    }

    /** De rem op het lopen van wie geboeid is (zie {@link #slow}). */
    private static final ResourceLocation SLOW_ID = ResourceLocation.fromNamespaceAndPath("imttech", "handcuffed");

    /** Per wereld: wie er geboeid is (de server loopt die elke tick langs). Wie uit beeld raakt, komt via onJoin terug. */
    private static final Map<ResourceKey<Level>, Set<UUID>> CUFFED = new HashMap<>();
    /** Hoe ver iemand is met worstelen (ticks sluipen; een Raven-soldaat: ticks zonder dat iemand hem vasthoudt). */
    private static final Map<UUID, Integer> STRUGGLE = new HashMap<>();
    /** Wanneer een geboeide speler voor het laatst te horen kreeg dat hij niets kan (niet elke klik opnieuw). */
    private static final Map<UUID, Long> LAST_HINT = new HashMap<>();

    // ================================================================ Lezen

    public static State state(Entity entity) {
        return entity.hasData(ModAttachments.CUFFS) ? entity.getData(ModAttachments.CUFFS) : State.FREE;
    }

    /** Heeft hij handboeien om? (Ook in je spel: de toestand wordt meegestuurd.) */
    public static boolean isCuffed(Entity entity) {
        return state(entity).cuffed();
    }

    /** Wie hem vasthoudt (meetrekt), of null. */
    public static @Nullable UUID holderOf(Entity entity) {
        return state(entity).holder().orElse(null);
    }

    /** Zoveel ticks sluipen maakt een speler los (uit de config). */
    public static int struggleTicks() {
        return Math.max(1, Math.round(ImtConfig.Handcuffs.STRUGGLE_SECONDS * 20));
    }

    /** Hoe ver iemand is met worstelen (ticks). */
    public static int struggle(Entity entity) {
        return STRUGGLE.getOrDefault(entity.getUUID(), 0);
    }

    /** Wie je kunt boeien: spelers (niet in creative of spectator), Raven-soldaten en guard robots. */
    public static boolean canBeCuffed(Entity target) {
        if (target instanceof Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        return target instanceof BotEntity || target instanceof RavenSoldierEntity;
    }

    /** Waarom {@code captor} {@code target} nu niet kan boeien, of null als het kan. */
    public static @Nullable Component cannotCuff(Player captor, Entity target) {
        if (target == captor) {
            return Component.translatable("cuffs.imttech.cannot.self");
        }
        if (isCuffed(captor)) {
            return Component.translatable("cuffs.imttech.cannot.cuffed_yourself");
        }
        if (target instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return Component.translatable("cuffs.imttech.cannot.creative", player.getDisplayName());
        }
        if (!(target instanceof LivingEntity living) || !canBeCuffed(living) || !living.isAlive()) {
            return Component.translatable("cuffs.imttech.cannot.kind");
        }
        if (captor.distanceTo(target) > ImtConfig.Handcuffs.REACH) {
            return Component.translatable("cuffs.imttech.cannot.far");
        }
        if (!behind(captor, living)) {
            return Component.translatable("cuffs.imttech.cannot.front");
        }
        return null;
    }

    /**
     * Staat de vanger achter hem (hoofdstuk 54, jouw notitie: "iemand enkel via de rug kunt boeien")? Achter = aan de kant
     * van zijn rug, ruim genomen (de halve cirkel achter hem, en een beetje meer).
     */
    public static boolean behind(Entity captor, LivingEntity target) {
        float rad = target.getYRot() * Mth.DEG_TO_RAD;
        Vec3 facing = new Vec3(-Mth.sin(rad), 0, Mth.cos(rad));
        Vec3 toCaptor = captor.position().subtract(target.position());
        toCaptor = new Vec3(toCaptor.x, 0, toCaptor.z);
        if (toCaptor.lengthSqr() < 0.01) {
            return true;
        }
        return facing.dot(toCaptor.normalize()) < 0.2;
    }

    /**
     * Gaan de boeien meteen om, zonder twee seconden vasthouden? Een speler die verblind is door een flashbang, slaapt of
     * bijna dood is; een robot die neerligt (Ion Blaster of EMP); een Raven-soldaat die bijna dood is.
     */
    public static boolean instant(LivingEntity target) {
        if (target instanceof ServerPlayer player) {
            return Flashbang.isBlinded(player) || player.isSleeping() || weak(player);
        }
        if (target instanceof BotEntity bot) {
            return bot.isDown() || Emp.isJammed(bot);
        }
        return weak(target);
    }

    private static boolean weak(LivingEntity target) {
        return target.getHealth() <= target.getMaxHealth() * ImtConfig.Handcuffs.WEAK_HEALTH;
    }

    // ================================================================ Boeien, vasthouden, losmaken

    /** De boeien gaan om: {@code captor} houdt hem meteen vast. */
    public static void cuff(ServerPlayer captor, LivingEntity target) {
        set(target, new State(true, Optional.of(captor.getUUID())));
        STRUGGLE.remove(target.getUUID());
        register(target);
        slow(target);
        hold(target);
        target.level().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
        captor.displayClientMessage(Component.translatable("cuffs.imttech.done", target.getDisplayName()).withStyle(ChatFormatting.AQUA), false);
        if (target instanceof ServerPlayer victim) {
            victim.displayClientMessage(Component.translatable("cuffs.imttech.cuffed_by", captor.getDisplayName(),
                    Math.round(ImtConfig.Handcuffs.STRUGGLE_SECONDS)).withStyle(ChatFormatting.RED), false);
        }
    }

    /** Rechtsklik met de boeien op wie al geboeid is: loslaten als jij hem vasthoudt, anders vastpakken. */
    public static void toggleHold(ServerPlayer captor, LivingEntity target) {
        State state = state(target);
        if (!state.cuffed()) {
            return;
        }
        if (state.holder().isPresent() && state.holder().get().equals(captor.getUUID())) {
            set(target, new State(true, Optional.empty()));
            captor.displayClientMessage(Component.translatable("cuffs.imttech.let_go", target.getDisplayName()).withStyle(ChatFormatting.GRAY), true);
        } else {
            set(target, new State(true, Optional.of(captor.getUUID())));
            captor.displayClientMessage(Component.translatable("cuffs.imttech.hold", target.getDisplayName())
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    /** Hij is weer los (met de sleutel: {@code by} krijgt de boeien terug; anders breken ze). Geeft true als hij geboeid was. */
    public static boolean release(LivingEntity target, @Nullable ServerPlayer by, boolean giveBack) {
        if (!isCuffed(target)) {
            return false;
        }
        set(target, State.FREE);
        STRUGGLE.remove(target.getUUID());
        unregister(target);
        unslow(target);
        if (target instanceof BotEntity bot) {
            bot.onReleased();
        }
        if (giveBack) {
            ItemStack cuffs = new ItemStack(ModItems.HANDCUFFS.get());
            if (by != null) {
                by.getInventory().placeItemBackInInventory(cuffs);
                by.displayClientMessage(Component.translatable("cuffs.imttech.released_by_you", target.getDisplayName()).withStyle(ChatFormatting.AQUA), true);
            } else {
                target.spawnAtLocation(cuffs);
            }
        }
        if (target.level() instanceof ServerLevel level) {
            level.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 1.0f, 1.2f);
        }
        if (target instanceof ServerPlayer victim && by != null) {
            victim.displayClientMessage(Component.translatable("cuffs.imttech.released", by.getDisplayName()).withStyle(ChatFormatting.GREEN), false);
        }
        return true;
    }

    /** Losgeworsteld: de boeien zijn kapot (niemand krijgt ze terug). */
    private static void breakFree(ServerLevel level, LivingEntity target) {
        ServerPlayer holder = holderPlayer(level, target);
        release(target, null, false);
        if (target instanceof ServerPlayer victim) {
            victim.displayClientMessage(Component.translatable("cuffs.imttech.free").withStyle(ChatFormatting.GREEN), false);
        }
        if (holder != null) {
            holder.displayClientMessage(Component.translatable("cuffs.imttech.escaped", target.getDisplayName()).withStyle(ChatFormatting.RED), false);
        }
    }

    /** Wie hem vasthoudt, als die speler online en in dezelfde wereld is. */
    private static @Nullable ServerPlayer holderPlayer(ServerLevel level, LivingEntity target) {
        UUID id = holderOf(target);
        ServerPlayer holder = id == null ? null : level.getServer().getPlayerList().getPlayer(id);
        return holder != null && holder.level() == level ? holder : null;
    }

    private static void set(Entity entity, State state) {
        if (state.cuffed()) {
            entity.setData(ModAttachments.CUFFS, state);
        } else if (entity.hasData(ModAttachments.CUFFS)) {
            entity.removeData(ModAttachments.CUFFS);
        }
    }

    private static void register(Entity entity) {
        CUFFED.computeIfAbsent(entity.level().dimension(), key -> new HashSet<>()).add(entity.getUUID());
    }

    private static void unregister(Entity entity) {
        Set<UUID> ids = CUFFED.get(entity.level().dimension());
        if (ids != null) {
            ids.remove(entity.getUUID());
        }
    }

    /** Geboeid loop je op halve snelheid (de rem blijft staan tot de boeien af zijn; na opnieuw laden zet de tick hem terug). */
    private static void slow(LivingEntity target) {
        AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(SLOW_ID)) {
            speed.addTransientModifier(new AttributeModifier(SLOW_ID, ImtConfig.Handcuffs.SLOW - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    private static void unslow(LivingEntity target) {
        AttributeInstance speed = target.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.removeModifier(SLOW_ID);
        }
    }

    /** Een wezen dat net geboeid is, staat stil: een robot stopt zijn programma, een soldaat laat zijn doel los. */
    private static void hold(LivingEntity target) {
        if (target instanceof BotEntity bot) {
            bot.onCuffed();
        } else if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
            mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
        }
    }

    // ================================================================ Elke tick

    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            Set<UUID> ids = CUFFED.get(level.dimension());
            if (ids == null || ids.isEmpty()) {
                continue;
            }
            for (UUID id : new ArrayList<>(ids)) {
                Entity entity = level.getEntity(id);
                if (entity == null || entity.isRemoved() || !(entity instanceof LivingEntity living) || !isCuffed(living)) {
                    ids.remove(id); // weg, of zijn stuk wereld is niet geladen (dan komt hij via onJoin terug)
                    continue;
                }
                tick(level, living);
            }
        }
    }

    private static void tick(ServerLevel level, LivingEntity living) {
        slow(living);
        int needed = struggleTicks();
        if (living instanceof ServerPlayer player) {
            player.setSprinting(false);
            if (player.containerMenu != player.inventoryMenu) {
                player.closeContainer(); // geen kisten: hij gaat meteen weer dicht
            }
            int struggle = player.isShiftKeyDown() ? struggle(player) + 1 : Math.max(0, struggle(player) - 2);
            if (struggle >= needed) {
                breakFree(level, player);
                return;
            }
            STRUGGLE.put(player.getUUID(), struggle);
            if (level.getGameTime() % 20 == 0) {
                if (struggle > 0 && player.isShiftKeyDown()) {
                    player.displayClientMessage(Component.translatable("cuffs.imttech.struggling", (needed - struggle + 19) / 20)
                            .withStyle(ChatFormatting.YELLOW), true);
                } else {
                    player.displayClientMessage(Component.translatable("cuffs.imttech.hint", Math.round(ImtConfig.Handcuffs.STRUGGLE_SECONDS))
                            .withStyle(ChatFormatting.RED), true);
                }
            }
        } else if (living instanceof RavenSoldierEntity soldier) {
            // Een soldaat wurmt zich los als niemand hem vasthoudt (even lang als een speler erover doet).
            int struggle = holderOf(soldier) == null ? struggle(soldier) + 1 : 0;
            if (struggle >= needed) {
                breakFree(level, soldier);
                return;
            }
            STRUGGLE.put(soldier.getUUID(), struggle);
        }
        UUID holderId = holderOf(living);
        if (holderId == null) {
            return;
        }
        ServerPlayer holder = holderPlayer(level, living);
        if (holder == null || !holder.isAlive() || holder.isSpectator() || isCuffed(holder)) {
            set(living, new State(true, Optional.empty()));
            return;
        }
        double distance = holder.distanceTo(living);
        if (distance > ImtConfig.Handcuffs.LET_GO) {
            set(living, new State(true, Optional.empty()));
            holder.displayClientMessage(Component.translatable("cuffs.imttech.lost", living.getDisplayName()).withStyle(ChatFormatting.RED), true);
            return;
        }
        hold(living, holder);
    }

    /**
     * Echt vast (jouw notitie van 4 november: "niet echt een lead, maar dat je de speler precies vast hebt"; sinds
     * hoofdstuk 54, jouw notitie van 6 november: "de geboeide staat altijd recht voor de persoon die hem boeit, met zijn
     * gezicht naar voren"): hij staat elke tick vlak vóór je ({@code [handcuffs] holdDistance}), met zijn rug naar je toe,
     * en kijkt dezelfde kant op als jij. Een klein verschil duwen we weg met een zetje; is hij toch ver weg geraakt (een
     * klap, een val), dan staat hij in één keer weer voor je.
     */
    private static void hold(LivingEntity living, ServerPlayer holder) {
        float yawDegrees = holder.getYRot();
        float yaw = yawDegrees * Mth.DEG_TO_RAD;
        Vec3 ahead = new Vec3(-Mth.sin(yaw), 0, Mth.cos(yaw)).scale(ImtConfig.Handcuffs.HOLD_DISTANCE);
        Vec3 target = holder.position().add(ahead);
        Vec3 to = target.subtract(living.position());
        double flat = Math.sqrt(to.x * to.x + to.z * to.z);
        if (flat > 2.5 || Math.abs(to.y) > 2.5) {
            living.teleportTo(target.x, target.y, target.z);
            living.setDeltaMovement(Vec3.ZERO);
        } else if (flat > 0.15) {
            double strength = Math.min(0.5, 0.35 * flat);
            Vec3 v = living.getDeltaMovement();
            double up = living.onGround() && to.y > 0.3 ? 0.42 : v.y;
            living.setDeltaMovement(to.x / flat * strength, up, to.z / flat * strength);
            living.hurtMarked = true; // ook een speler krijgt de zet (zijn spel hoort het zo)
        }
        if (living instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        face(living, yawDegrees);
    }

    /** Dezelfde kant op kijken als wie hem vasthoudt: zijn gezicht naar voren, zijn rug naar de vanger. */
    private static void face(LivingEntity living, float yaw) {
        if (living instanceof ServerPlayer player) {
            // Een speler draait zijn eigen hoofd; alleen de server kan hem omdraaien, met een "teleport" op zijn plek.
            if (Math.abs(Mth.wrapDegrees(player.getYRot() - yaw)) > 3) {
                player.connection.teleport(0, 0, 0, yaw, 0, java.util.EnumSet.of(net.minecraft.world.entity.RelativeMovement.X,
                        net.minecraft.world.entity.RelativeMovement.Y, net.minecraft.world.entity.RelativeMovement.Z,
                        net.minecraft.world.entity.RelativeMovement.X_ROT));
            }
            return;
        }
        living.setYRot(yaw);
        living.setYHeadRot(yaw);
        living.yBodyRot = yaw;
        living.yHeadRot = yaw;
    }

    // ================================================================ Gebeurtenissen

    /** Een geboeid wezen (of speler) komt (weer) in de wereld: weer meedoen in de tick, met zijn rem. */
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof LivingEntity living && isCuffed(living)) {
            register(living);
            slow(living);
            hold(living);
        }
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        STRUGGLE.remove(event.getEntity().getUUID());
        LAST_HINT.remove(event.getEntity().getUUID());
    }

    /** Dood: de boeien vallen op de grond. */
    public static void onDeath(LivingDeathEvent event) {
        if (!event.getEntity().level().isClientSide && isCuffed(event.getEntity())) {
            release(event.getEntity(), null, true);
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        CUFFED.clear();
        STRUGGLE.clear();
        LAST_HINT.clear();
    }

    /** Een geboeide speler kan niets met zijn handen: de actie gaat niet door, en hij hoort waarom (om de seconde). */
    private static boolean blocked(@Nullable Entity entity) {
        if (!(entity instanceof Player player) || !isCuffed(player)) {
            return false;
        }
        if (player instanceof ServerPlayer server) {
            long now = server.level().getGameTime();
            Long last = LAST_HINT.get(server.getUUID());
            if (last == null || now - last >= 20) {
                LAST_HINT.put(server.getUUID(), now);
                server.displayClientMessage(Component.translatable("cuffs.imttech.blocked").withStyle(ChatFormatting.RED), true);
            }
        }
        return true;
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (blocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (blocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (blocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (blocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (blocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static void onBreak(BlockEvent.BreakEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCanceled(true);
        }
    }

    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (blocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static void onAttack(AttackEntityEvent event) {
        if (blocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    public static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (blocked(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Weggooien mag ook niet: het item gaat terug in zijn rugzak. */
    public static void onToss(ItemTossEvent event) {
        if (blocked(event.getPlayer())) {
            event.setCanceled(true);
            event.getPlayer().getInventory().placeItemBackInInventory(event.getEntity().getItem());
        }
    }
}
