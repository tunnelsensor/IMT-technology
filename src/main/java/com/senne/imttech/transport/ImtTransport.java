package com.senne.imttech.transport;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.drone.ImtDrones;
import com.senne.imttech.drone.DroneEntity;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.imt.ImtTargeting;
import com.senne.imttech.nav.ImtNavigation;
import com.senne.imttech.nav.ImtWaypoints;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
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
 *
 * LANDEN gaat altijd op dezelfde manier, hoe de rit ook eindigt (sluipen, "Land", lege batterij,
 * bril af, tijd om, EMP, aangekomen): de drone zakt met je mee en laat je pas los als je voeten
 * vlak boven de grond zijn (DroneEntity.descend, dan setDown hieronder). Er is geen zweefval
 * meer: een server zag dat langzame zakken als vliegen en gooide je eraf.
 */
public final class ImtTransport {
    private ImtTransport() {}

    /** Zo ver kijken we naar beneden naar vaste grond; daarboven is het "geen grond". */
    public static final int NO_GROUND = 64;
    /** Zo hoog hangen je voeten boven de grond als de drone je loslaat (blokken). */
    public static final double RELEASE_HEIGHT = 0.3;

    // ================================================================ Starten

    /** De transportdrone waar deze speler op dit moment onder hangt, of null. */
    public static @Nullable DroneEntity carrierOf(Player player) {
        return player.getVehicle() instanceof DroneEntity drone && drone.isTransport() ? drone : null;
    }

    /**
     * Zelf vliegen: een drone pakt je op. Hangt er al een aan je, dan is dit "Land": de drone
     * zakt met je mee en zet je neer.
     *
     * @param autopilot true = de drone brengt je zelf naar je bestemming uit de navigatie
     */
    public static void startRide(ServerPlayer owner, ItemStack glasses, boolean autopilot) {
        DroneEntity current = carrierOf(owner);
        if (current != null) {
            current.landPassenger("imt.imttech.msg.transport_landing", ImtMessagePayload.Level.INFO);
            return;
        }
        BlockPos destination = null;
        if (autopilot) {
            GlobalPos target = navigationTarget(owner);
            if (target == null) {
                ImtServer.send(owner, Component.translatable("imt.imttech.msg.transport_no_destination"),
                        ImtMessagePayload.Level.WARNING);
                return;
            }
            if (target.dimension() != owner.level().dimension()) {
                ImtServer.send(owner, Component.translatable("imt.imttech.msg.transport_other_world"),
                        ImtMessagePayload.Level.WARNING);
                return;
            }
            destination = target.pos();
        }
        DroneEntity carrier = ImtDrones.launchCarrier(owner, destination);
        if (carrier == null) {
            return;
        }
        owner.startRiding(carrier, true);
        // Twee drones vliegen mee, puur omdat het er goed uitziet (en ze houden je in het oog).
        ImtDrones.launchTransportEscort(owner, 2);
        ImtServer.send(owner, autopilot
                ? Component.translatable("imt.imttech.msg.transport_autopilot")
                : Component.translatable("imt.imttech.msg.transport_manual"), ImtMessagePayload.Level.INFO);
    }

    /**
     * Vrachtdrone: haalt alles uit de kist (of oven, vat...) waar je naar kijkt en brengt het
     * naar de kist bij je bestemming.
     */
    public static void sendCargo(ServerPlayer owner, ItemStack glasses) {
        GlobalPos target = navigationTarget(owner);
        if (target == null || target.dimension() != owner.level().dimension()) {
            ImtServer.send(owner, Component.translatable("imt.imttech.msg.transport_no_destination"),
                    ImtMessagePayload.Level.WARNING);
            return;
        }
        Container container = lookedAtContainer(owner);
        List<ItemStack> cargo = new ArrayList<>();
        if (container != null) {
            for (int slot = 0; slot < container.getContainerSize() && cargo.size() < ImtConfig.Transport.CARGO_SLOTS; slot++) {
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
            ImtServer.send(owner, Component.translatable("imt.imttech.msg.cargo_empty"), ImtMessagePayload.Level.WARNING);
            return;
        }
        DroneEntity drone = ImtDrones.launchCargo(owner, cargo, target.pos());
        if (drone == null) {
            // Niet gelukt: spullen niet laten verdwijnen.
            cargo.forEach(owner::addItem);
            return;
        }
        ImtServer.send(owner, Component.translatable("imt.imttech.msg.cargo_sent", cargo.size()),
                ImtMessagePayload.Level.INFO);
    }

    /**
     * Iemand (of iets) oppakken en ergens anders neerzetten: bij je bestemming, of bij jou als
     * je geen bestemming hebt. Spelers mogen alleen als PvP aan staat en ze niet in je team zitten.
     */
    public static void carry(ServerPlayer owner, LivingEntity target) {
        if (target instanceof Player player && !ImtTargeting.canTarget(owner, player)) {
            ImtServer.send(owner, Component.translatable("imt.imttech.msg.carry_not_allowed", player.getDisplayName()),
                    ImtMessagePayload.Level.WARNING);
            return;
        }
        if (target.isPassenger() || target.isVehicle()) {
            ImtServer.send(owner, Component.translatable("imt.imttech.msg.carry_busy", target.getDisplayName()),
                    ImtMessagePayload.Level.WARNING);
            return;
        }
        GlobalPos destination = navigationTarget(owner);
        BlockPos pos = destination != null && destination.dimension() == owner.level().dimension()
                ? destination.pos()
                : owner.blockPosition();
        DroneEntity drone = ImtDrones.launchGrabber(owner, target, pos);
        if (drone == null) {
            return;
        }
        target.startRiding(drone, true);
        ImtServer.send(owner, Component.translatable("imt.imttech.msg.carry_started", target.getDisplayName(),
                pos.getX(), pos.getY(), pos.getZ()), ImtMessagePayload.Level.INFO);
    }

    // ================================================================ Landen en afleveren

    /**
     * De drone hangt met zijn passagier vlak boven de grond (zie DroneEntity.descend): nu pas
     * loslaten. Je komt te staan waar je hing (DroneEntity.getDismountLocationForPassenger), dus
     * je valt hooguit een paar centimeter. Daarna vertrekt de drone, en je escortedrones zien
     * zelf dat je niet meer vliegt en gaan mee.
     *
     * (Vroeger riep dit alle transportdrones van de eigenaar terug, ook een vrachtdrone die nog
     * onderweg was: die liet zijn spullen dan uit de lucht vallen.)
     *
     * @param arrived true = aangekomen bij de bestemming (autopiloot, of iemand afgeleverd)
     */
    public static void setDown(ServerLevel level, DroneEntity drone, Entity passenger, boolean arrived) {
        Vec3 hanging = passenger.position();
        passenger.stopRiding();
        if (passenger instanceof ServerPlayer player && player.position().distanceToSqr(hanging) > 1.0E-4) {
            // Moest je een stukje hoger neergezet worden (je hing half in een blok)? Dan ook je
            // eigen spel bijwerken: dat weet alleen waar je hing.
            player.connection.teleport(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        }
        switch (drone.transportRole()) {
            case CARRIER -> {
                if (passenger instanceof ServerPlayer rider) {
                    if (arrived) {
                        level.playSound(null, rider.getX(), rider.getY(), rider.getZ(), ModSounds.DRONE_RECALL.get(),
                                SoundSource.PLAYERS, 0.7f, 1.0f);
                    }
                    ImtServer.send(rider, Component.translatable("imt.imttech.msg.transport_landed"),
                            ImtMessagePayload.Level.INFO);
                }
            }
            case GRABBER -> {
                if (arrived && drone.getOwnerId() != null
                        && level.getPlayerByUUID(drone.getOwnerId()) instanceof ServerPlayer owner) {
                    ImtServer.send(owner, Component.translatable("imt.imttech.msg.carry_done"),
                            ImtMessagePayload.Level.INFO);
                }
            }
            default -> { }
        }
        drone.recall(level);
    }

    /**
     * Hoe ver het van je voeten naar beneden is tot iets om op te staan (de bovenkant van een
     * blok, of het water), of NO_GROUND als dat verder is dan NO_GROUND blokken.
     *
     * Het blok waar je voeten in staan telt mee: een plaat of een trap is lager dan een kubus.
     * Sta (of hang) je ín iets, dan is het 0: je bent er al.
     */
    public static double distanceToGround(Entity entity) {
        Level level = entity.level();
        BlockPos.MutableBlockPos pos = entity.blockPosition().mutable();
        int feetY = pos.getY();
        for (int i = 0; i <= NO_GROUND; i++) {
            pos.setY(feetY - i);
            if (pos.getY() < level.getMinBuildHeight()) {
                return NO_GROUND;
            }
            double top = surfaceTop(level, pos);
            if (!Double.isNaN(top)) {
                return Math.max(0.0, entity.getY() - top);
            }
        }
        return NO_GROUND;
    }

    /**
     * Is er onder je iets veiligs om op te landen? Niet boven de leegte (het End) en niet boven
     * lava: daar begint de drone bij sluipen niet aan het landen.
     */
    public static boolean hasSafeGroundBelow(Entity entity) {
        Level level = entity.level();
        BlockPos.MutableBlockPos pos = entity.blockPosition().mutable();
        for (int y = pos.getY(); y >= level.getMinBuildHeight(); y--) {
            pos.setY(y);
            if (!Double.isNaN(surfaceTop(level, pos))) {
                return !level.getFluidState(pos).is(FluidTags.LAVA);
            }
        }
        return false;
    }

    /** De bovenkant van wat je op deze plek tegenhoudt (een blok of water), of NaN als hier niets is. */
    private static double surfaceTop(Level level, BlockPos pos) {
        VoxelShape shape = level.getBlockState(pos).getCollisionShape(level, pos);
        double top = shape.isEmpty() ? Double.NaN : pos.getY() + shape.max(Direction.Axis.Y);
        FluidState fluid = level.getFluidState(pos);
        if (!fluid.isEmpty()) {
            // In het water laat hij je los aan de oppervlakte: zwemmen kan je zelf.
            double surface = pos.getY() + fluid.getHeight(level, pos);
            top = Double.isNaN(top) ? surface : Math.max(top, surface);
        }
        return top;
    }

    /**
     * Een vrachtdrone is bij zijn bestemming: afleveren, en weer weg.
     * (Een drone die iemand draagt, landt daar gewoon; zie DroneEntity.flyToDestination.)
     */
    public static void arrive(ServerLevel level, DroneEntity drone, @Nullable ServerPlayer owner) {
        if (drone.transportRole() == DroneEntity.TransportRole.CARGO) {
            deliverCargo(level, drone, owner);
        }
        drone.recall(level);
    }

    /** Legt de vracht in de kist bij de bestemming, of op de grond als die vol/weg is. */
    public static void deliverCargo(ServerLevel level, DroneEntity drone, @Nullable ServerPlayer owner) {
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
            ImtServer.send(owner, Component.translatable("imt.imttech.msg.cargo_delivered", cargo.size(), delivered),
                    ImtMessagePayload.Level.INFO);
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
        ImtWaypoints data = owner.getData(ModAttachments.WAYPOINTS);
        if (data.active() == ImtWaypoints.NONE) {
            return null;
        }
        Optional<GlobalPos> target = ImtNavigation.targetPos(owner, data);
        return target.orElse(null);
    }

    // ================================================================ Veiligheid bij afsluiten

    /**
     * De server gaat uit: vrachtdrones laten hun spullen vallen. Drones worden namelijk niet
     * opgeslagen, dus zonder dit zouden de items verdwijnen.
     */
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) {
            for (DroneEntity drone : level.getEntitiesOfClass(DroneEntity.class,
                    net.minecraft.world.phys.AABB.INFINITE, DroneEntity::hasCargo)) {
                for (ItemStack stack : drone.takeCargo()) {
                    level.addFreshEntity(new ItemEntity(level, drone.getX(), drone.getY(), drone.getZ(), stack));
                }
            }
        }
    }

    /**
     * Energie: zolang je vliegt kost het per seconde. Is de bril leeg, dan land je; dat laatste
     * stuk (de drone zakt met je mee) is gratis.
     */
    public static void tickRider(ServerPlayer owner, ItemStack glasses) {
        DroneEntity carrier = carrierOf(owner);
        if (carrier == null || carrier.isLanding()) {
            return;
        }
        if (!ImtData.tryUseEnergy(glasses, ImtConfig.Transport.DRAIN) || ImtData.getEnergy(glasses) <= 0) {
            carrier.landPassenger("imt.imttech.msg.transport_no_energy", ImtMessagePayload.Level.ALERT);
        }
    }
}
