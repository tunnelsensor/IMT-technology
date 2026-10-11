package com.senne.starktech.transport;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.drone.EdithDrones;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithServer;
import com.senne.starktech.edith.EdithTargeting;
import com.senne.starktech.nav.EdithNavigation;
import com.senne.starktech.nav.EdithWaypoints;
import com.senne.starktech.network.EdithMessagePayload;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Drones als vervoer. Er zijn vier soorten ritten:
 *
 * 1. ZELF VLIEGEN (rol CARRIER, zonder bestemming): een drone pakt je op en jij stuurt.
 *    Je vliegt waar je KIJKT met W/S/A/D, spatie geeft extra lift, sprinten is sneller,
 *    en met shift (sluipen) stop je: de drone zet je netjes neer.
 * 2. AUTOPILOOT (rol CARRIER, met bestemming): de drone brengt je naar je huidige
 *    bestemming uit de navigatie (waypoint of sterfplek) en landt daar zelf.
 * 3. VRACHTDRONE (rol CARGO): haalt de spullen uit de kist waar je naar kijkt en brengt ze
 *    naar de kist bij je bestemming (past er niets in, dan legt hij ze op de grond).
 * 4. IEMAND VERVOEREN (rol GRABBER): pakt een speler, dier of monster op en zet het bij je
 *    bestemming neer (of bij jou, als je geen bestemming hebt).
 *
 * Alles wat "onthouden" moet worden zit in de drone zelf (bestemming, vracht, wie hij
 * vasthoudt). Deze klasse start en stopt de ritten en levert af.
 */
public final class EdithTransport {
    private EdithTransport() {}

    /** Zo ver kijken we naar beneden naar vaste grond; daarboven is het "geen grond". */
    public static final int NO_GROUND = 64;
    /** Hoe snel je zakt als de drone je neerzet (blokken per tick). */
    private static final double SOFT_LANDING_SPEED = 0.35;
    /** Na zo lang stoppen we met zacht landen, ook als je nog niet staat (vangnet). */
    private static final int SOFT_LANDING_MAX_TICKS = 300;

    // ================================================================ Starten

    /** De transportdrone waar deze speler op dit moment onder hangt, of null. */
    public static @Nullable StarkDroneEntity carrierOf(Player player) {
        return player.getVehicle() instanceof StarkDroneEntity drone && drone.isTransport() ? drone : null;
    }

    /**
     * Zelf vliegen: een drone pakt je op. Hangt er al een aan je, dan stopt de rit.
     *
     * @param autopilot true = de drone brengt je zelf naar je bestemming uit de navigatie
     */
    public static void startRide(ServerPlayer owner, ItemStack glasses, boolean autopilot) {
        StarkDroneEntity current = carrierOf(owner);
        if (current != null) {
            land(owner, current, true);
            return;
        }
        BlockPos destination = null;
        if (autopilot) {
            GlobalPos target = navigationTarget(owner);
            if (target == null) {
                EdithServer.send(owner, Component.translatable("edith.starktech.msg.transport_no_destination"),
                        EdithMessagePayload.Level.WARNING);
                return;
            }
            if (target.dimension() != owner.level().dimension()) {
                EdithServer.send(owner, Component.translatable("edith.starktech.msg.transport_other_world"),
                        EdithMessagePayload.Level.WARNING);
                return;
            }
            destination = target.pos();
        }
        StarkDroneEntity carrier = EdithDrones.launchCarrier(owner, destination);
        if (carrier == null) {
            return;
        }
        owner.startRiding(carrier, true);
        // Twee drones vliegen mee, puur omdat het er goed uitziet (en ze houden je in het oog).
        EdithDrones.launchTransportEscort(owner, 2);
        EdithServer.send(owner, autopilot
                ? Component.translatable("edith.starktech.msg.transport_autopilot")
                : Component.translatable("edith.starktech.msg.transport_manual"), EdithMessagePayload.Level.INFO);
    }

    /**
     * Vrachtdrone: haalt alles uit de kist (of oven, vat...) waar je naar kijkt en brengt het
     * naar de kist bij je bestemming.
     */
    public static void sendCargo(ServerPlayer owner, ItemStack glasses) {
        GlobalPos target = navigationTarget(owner);
        if (target == null || target.dimension() != owner.level().dimension()) {
            EdithServer.send(owner, Component.translatable("edith.starktech.msg.transport_no_destination"),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        Container container = lookedAtContainer(owner);
        List<ItemStack> cargo = new ArrayList<>();
        if (container != null) {
            for (int slot = 0; slot < container.getContainerSize() && cargo.size() < StarkConfig.Transport.CARGO_SLOTS; slot++) {
                ItemStack stack = container.getItem(slot);
                if (!stack.isEmpty()) {
                    cargo.add(stack.copy());
                    container.setItem(slot, ItemStack.EMPTY);
                }
            }
            container.setChanged();
        } else {
            // Geen kist in zicht? Dan neemt hij wat je in je hand hebt.
            ItemStack held = owner.getMainHandItem();
            if (!held.isEmpty()) {
                cargo.add(held.copy());
                owner.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            }
        }
        if (cargo.isEmpty()) {
            EdithServer.send(owner, Component.translatable("edith.starktech.msg.cargo_empty"), EdithMessagePayload.Level.WARNING);
            return;
        }
        StarkDroneEntity drone = EdithDrones.launchCargo(owner, cargo, target.pos());
        if (drone == null) {
            // Niet gelukt: spullen niet laten verdwijnen.
            cargo.forEach(owner::addItem);
            return;
        }
        EdithServer.send(owner, Component.translatable("edith.starktech.msg.cargo_sent", cargo.size()),
                EdithMessagePayload.Level.INFO);
    }

    /**
     * Iemand (of iets) oppakken en ergens anders neerzetten: bij je bestemming, of bij jou als
     * je geen bestemming hebt. Spelers mogen alleen als PvP aan staat en ze niet in je team zitten.
     */
    public static void carry(ServerPlayer owner, LivingEntity target) {
        if (target instanceof Player player && !EdithTargeting.canTarget(owner, player)) {
            EdithServer.send(owner, Component.translatable("edith.starktech.msg.carry_not_allowed", player.getDisplayName()),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        if (target.isPassenger() || target.isVehicle()) {
            EdithServer.send(owner, Component.translatable("edith.starktech.msg.carry_busy", target.getDisplayName()),
                    EdithMessagePayload.Level.WARNING);
            return;
        }
        GlobalPos destination = navigationTarget(owner);
        BlockPos pos = destination != null && destination.dimension() == owner.level().dimension()
                ? destination.pos()
                : owner.blockPosition();
        StarkDroneEntity drone = EdithDrones.launchGrabber(owner, target, pos);
        if (drone == null) {
            return;
        }
        target.startRiding(drone, true);
        EdithServer.send(owner, Component.translatable("edith.starktech.msg.carry_started", target.getDisplayName(),
                pos.getX(), pos.getY(), pos.getZ()), EdithMessagePayload.Level.INFO);
    }

    // ================================================================ Landen en afleveren

    /**
     * Zet de speler neer en stuurt de drones weg.
     *
     * @param gentle true = met zweefval als je hoog hangt (zodat je je niet te grazen valt)
     */
    public static void land(ServerPlayer owner, StarkDroneEntity carrier, boolean gentle) {
        owner.stopRiding();
        if (gentle) {
            setDown(owner);
        }
        carrier.recall(owner.serverLevel());
        EdithDrones.recallAll(owner, StarkDroneEntity.Mode.TRANSPORT);
        EdithServer.send(owner, Component.translatable("edith.starktech.msg.transport_landed"), EdithMessagePayload.Level.INFO);
    }

    /**
     * Sluipen terwijl je onder een drone hangt betekent: landen.
     *
     * Minecraft laat je dan los, en daar valt niet tegenin te gaan: jouw eigen spel stapt ook
     * af, dus je begint sowieso te vallen. In plaats van dat tegen te houden vangen we het op:
     * vanaf dat moment zák je (rustig en zonder schade) in plaats van te vallen, en de drone
     * zakt met je mee tot je staat. Zie tickSoftLanding.
     */
    public static void onDismount(EntityMountEvent event) {
        if (!event.isDismounting() || event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntityBeingMounted() instanceof StarkDroneEntity drone)
                || !(event.getEntityMounting() instanceof ServerPlayer player)
                || !drone.isTransport()
                || drone.isLanding()) {
            return; // de drone zet je zelf af: dat mag natuurlijk wel
        }
        double drop = distanceToGround(player);
        if (drop <= 1.0 || drop >= NO_GROUND) {
            return; // je staat al zo goed als op de grond (of er is geen grond)
        }
        beginSoftLanding(player);
        drone.beginLanding();
        EdithServer.send(player, Component.translatable("edith.starktech.msg.transport_landing"),
                EdithMessagePayload.Level.INFO);
    }

    /** Spelers die op dit moment zacht aan het landen zijn, met wanneer dat begon. */
    private static final Map<UUID, Long> SOFT_LANDING = new HashMap<>();

    /** Vanaf nu zak je rustig naar beneden in plaats van te vallen. */
    public static void beginSoftLanding(ServerPlayer player) {
        SOFT_LANDING.put(player.getUUID(), player.level().getGameTime());
    }

    /** Ben je nu aan het zakken? (Dan telt vallen niet als schade.) */
    public static boolean isSoftLanding(Player player) {
        return SOFT_LANDING.containsKey(player.getUUID());
    }

    /**
     * Elke tick zolang je zakt: we houden je snelheid laag en je valafstand op nul.
     *
     * Dat laatste is belangrijk - anders krijg je bij het neerkomen alsnog de klap te
     * verwerken van de hele hoogte waar de drone je losliet.
     */
    public static void tickSoftLanding(ServerPlayer player) {
        Long since = SOFT_LANDING.get(player.getUUID());
        if (since == null) {
            return;
        }
        boolean done = player.onGround()
                || carrierOf(player) != null
                || player.isSpectator()
                || player.level().getGameTime() - since > SOFT_LANDING_MAX_TICKS;
        if (done) {
            SOFT_LANDING.remove(player.getUUID());
            player.resetFallDistance();
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (motion.y < -SOFT_LANDING_SPEED) {
            // Afremmen tot daalsnelheid, en zijwaarts ook rustig aan.
            player.setDeltaMovement(motion.x * 0.7, -SOFT_LANDING_SPEED, motion.z * 0.7);
            player.connection.send(new ClientboundSetEntityMotionPacket(player));
        }
        player.resetFallDistance();
    }

    /** Wie zacht landt, komt zonder kleerscheuren neer. */
    public static void onFallDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().is(DamageTypeTags.IS_FALL)
                && isSoftLanding(player)) {
            event.setCanceled(true);
        }
    }

    public static void forgetSoftLandings() {
        SOFT_LANDING.clear();
    }

    /**
     * De drone zet je neer: hangt er nog lucht onder je, dan zakt hij je rustig op de grond.
     * Geen zweefval meer - je staat gewoon, en je houdt geen effect over aan de rit.
     *
     * Normaal is dit maar een klein stukje, want de drone daalt al met je mee (zie
     * StarkDroneEntity.descend). Dit is het laatste zetje, en het vangnet voor als de rit
     * halverwege stopt (batterij leeg, of je zet hem uit terwijl je hoog hangt).
     */
    private static void setDown(ServerPlayer player) {
        double drop = distanceToGround(player);
        if (drop <= 0.2 || drop >= NO_GROUND) {
            return; // je staat al (of er is geen grond onder je)
        }
        // Zakken in plaats van vallen: rustig omlaag, en geen klap bij het neerkomen.
        beginSoftLanding(player);
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x * 0.5, -SOFT_LANDING_SPEED, motion.z * 0.5);
        player.resetFallDistance();
        player.connection.send(new ClientboundSetEntityMotionPacket(player));
    }

    /** Hoe ver het van hier naar beneden is tot vaste grond, of NO_GROUND als daar niets is. */
    public static double distanceToGround(Entity entity) {
        ServerLevel level = (ServerLevel) entity.level();
        BlockPos.MutableBlockPos pos = entity.blockPosition().mutable();
        for (int i = 1; i <= NO_GROUND; i++) {
            pos.setY(entity.blockPosition().getY() - i);
            if (pos.getY() <= level.getMinBuildHeight()) {
                return NO_GROUND;
            }
            VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                // Je komt op de bovenkant van dat blok te staan (een trap is lager dan een kubus).
                return entity.getY() - (pos.getY() + shape.max(Direction.Axis.Y));
            }
        }
        return NO_GROUND;
    }

    /**
     * De drone is bij zijn bestemming: afleveren.
     * Wordt door de drone zelf aangeroepen (zie StarkDroneEntity).
     */
    public static void arrive(ServerLevel level, StarkDroneEntity drone, @Nullable ServerPlayer owner) {
        switch (drone.transportRole()) {
            case CARRIER -> {
                if (owner != null && drone.getFirstPassenger() == owner) {
                    land(owner, drone, true);
                    if (owner.level() == level) {
                        level.playSound(null, owner.getX(), owner.getY(), owner.getZ(), ModSounds.DRONE_RECALL.get(),
                                SoundSource.PLAYERS, 0.7f, 1.0f);
                    }
                } else {
                    drone.recall(level);
                }
            }
            case CARGO -> {
                deliverCargo(level, drone, owner);
                drone.recall(level);
            }
            case GRABBER -> {
                Entity carried = drone.getFirstPassenger();
                if (carried != null) {
                    carried.stopRiding();
                    if (carried instanceof ServerPlayer player) {
                        setDown(player);
                    }
                }
                if (owner != null) {
                    EdithServer.send(owner, Component.translatable("edith.starktech.msg.carry_done"),
                            EdithMessagePayload.Level.INFO);
                }
                drone.recall(level);
            }
            case FOLLOWER -> drone.recall(level);
        }
    }

    /** Legt de vracht in de kist bij de bestemming, of op de grond als die vol/weg is. */
    public static void deliverCargo(ServerLevel level, StarkDroneEntity drone, @Nullable ServerPlayer owner) {
        List<ItemStack> cargo = drone.takeCargo();
        if (cargo.isEmpty()) {
            return;
        }
        BlockPos destination = drone.destination() == null ? drone.blockPosition() : drone.destination();
        Container container = containerNear(level, destination);
        int delivered = 0;
        for (ItemStack stack : cargo) {
            ItemStack rest = container == null ? stack : putIn(container, stack);
            if (rest.isEmpty()) {
                delivered++;
            } else {
                // Past niet (of geen kist): netjes op de grond leggen.
                ItemEntity item = new ItemEntity(level, destination.getX() + 0.5, destination.getY() + 1.0,
                        destination.getZ() + 0.5, rest);
                item.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(item);
            }
        }
        if (container != null) {
            container.setChanged();
        }
        if (owner != null) {
            EdithServer.send(owner, Component.translatable("edith.starktech.msg.cargo_delivered", cargo.size(), delivered),
                    EdithMessagePayload.Level.INFO);
        }
    }

    /** Probeert een stapel in een kist te leggen. Geeft terug wat er niet in paste. */
    private static ItemStack putIn(Container container, ItemStack stack) {
        // Eerst bij dezelfde items, dan in een leeg vakje.
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            ItemStack inSlot = container.getItem(slot);
            if (!inSlot.isEmpty() && ItemStack.isSameItemSameComponents(inSlot, stack)) {
                int room = Math.min(inSlot.getMaxStackSize(), container.getMaxStackSize()) - inSlot.getCount();
                int moved = Math.min(room, stack.getCount());
                if (moved > 0) {
                    inSlot.grow(moved);
                    stack.shrink(moved);
                }
            }
        }
        for (int slot = 0; slot < container.getContainerSize() && !stack.isEmpty(); slot++) {
            if (container.getItem(slot).isEmpty()) {
                container.setItem(slot, stack.copy());
                stack.setCount(0);
            }
        }
        return stack;
    }

    /** Een kist (of ander opslagblok) op of naast de bestemming. */
    private static @Nullable Container containerNear(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.withinManhattan(center, 2, 2, 2)) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof Container container) {
                return container;
            }
        }
        return null;
    }

    /** De kist/oven/vat waar de speler naar kijkt (tot 6 blokken ver). */
    private static @Nullable Container lookedAtContainer(ServerPlayer owner) {
        var hit = owner.pick(6.0, 1.0f, false);
        if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit
                && owner.level().getBlockEntity(blockHit.getBlockPos()) instanceof Container container) {
            return container;
        }
        return null;
    }

    /** Waar de speler op dit moment naartoe navigeert, of null. */
    private static @Nullable GlobalPos navigationTarget(ServerPlayer owner) {
        EdithWaypoints data = owner.getData(ModAttachments.WAYPOINTS);
        if (data.active() == EdithWaypoints.NONE) {
            return null;
        }
        Optional<GlobalPos> target = EdithNavigation.targetPos(owner, data);
        return target.orElse(null);
    }

    // ================================================================ Veiligheid bij afsluiten

    /**
     * De server gaat uit: vrachtdrones laten hun spullen vallen. Drones worden namelijk niet
     * opgeslagen, dus zonder dit zouden de items verdwijnen.
     */
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (StarkDroneEntity drone : level.getEntitiesOfClass(StarkDroneEntity.class,
                    net.minecraft.world.phys.AABB.INFINITE, StarkDroneEntity::hasCargo)) {
                for (ItemStack stack : drone.takeCargo()) {
                    level.addFreshEntity(new ItemEntity(level, drone.getX(), drone.getY(), drone.getZ(), stack));
                }
            }
        }
    }

    /** Energie: zolang je vliegt kost het per seconde. Is de bril leeg, dan land je. */
    public static void tickRider(ServerPlayer owner, ItemStack glasses) {
        StarkDroneEntity carrier = carrierOf(owner);
        if (carrier == null) {
            return;
        }
        if (!EdithData.tryUseEnergy(glasses, StarkConfig.Transport.DRAIN)) {
            EdithServer.send(owner, Component.translatable("edith.starktech.msg.transport_no_energy"),
                    EdithMessagePayload.Level.ALERT);
            land(owner, carrier, true);
        }
    }
}
