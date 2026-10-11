package com.senne.starktech.arsenal;

import com.senne.starktech.config.StarkConfig;
import com.senne.starktech.energy.StarkGrid;
import com.senne.starktech.registry.ModBlockEntities;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import java.util.Comparator;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De "binnenkant" van een drone-dock: welke drone uit je arsenaal hier staat, en hoe heel hij is.
 *
 * Een dock pakt zelf een drone uit het arsenaal van zijn eigenaar die nog geen plek heeft, en
 * houdt die plek vast. Staat de drone "klaar" (in het arsenaal: ORBIT), dan staat hij echt
 * hier, en repareert de dock hem langzaam met stroom van het net. Is hij op pad, dan laat de
 * dock een doorschijnend hologram van hem zien: zijn plek blijft vrij tot hij terugkomt.
 *
 * Van wie is een dock? Van wie hem neerzet. Een dock in een Stark Tower (neergezet door de
 * toren zelf, zonder eigenaar) is van wie de toren gehackt heeft.
 *
 * Elke halve seconde kijkt hij in het arsenaal van zijn eigenaar. Is die niet online, dan
 * blijft alles staan zoals het was: je drones blijven in je hangar staan als je weg bent.
 */
public class DroneDockBlockEntity extends BlockEntity {
    private static final int CHECK_TICKS = 10;

    /** Wie hem neerzette (null bij een dock van de toren: dan de eigenaar van de toren). */
    private @Nullable UUID owner;
    /** Welke drone hier zijn plek heeft, en van wie die drone is. */
    private @Nullable UUID drone;
    private @Nullable UUID droneOwner;
    /** Hoe heel die drone is (0 tot 1), is hij hier (niet op pad), en wordt hij gerepareerd? */
    private float health = 1.0f;
    private boolean present;
    private boolean repairing;

    /** Alleen in jouw spel: wanneer (speltijd) de drone vertrok of aankwam, voor het animeren. */
    private long movedAt = Long.MIN_VALUE;
    private boolean synced;

    public DroneDockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRONE_DOCK.get(), pos, state);
    }

    /** Neergezet door een speler: dan is hij van hem. */
    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    // ================================================================ Elke tick (server)

    public void serverTick(ServerLevel level, BlockPos pos) {
        if ((level.getGameTime() + pos.asLong()) % CHECK_TICKS != 0) {
            return;
        }
        UUID ownerId = ownerId(level);
        if (ownerId == null) {
            if (drone != null) {
                release(level);
                sync();
            }
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(ownerId);
        if (player == null) {
            return; // niet online: alles blijft zoals het was
        }
        EdithArsenal arsenal = ArsenalServer.get(player);
        ArsenalDrone current = drone == null ? null : arsenal.find(drone);
        if (drone != null && (current == null || !ownerId.equals(droneOwner) || !DroneDocks.holds(drone, here()))) {
            // Neergehaald, van een vorige eigenaar van de toren, of een andere dock heeft hem.
            release(level);
            current = null;
        }
        if (current == null) {
            current = pickFreeDrone(arsenal);
            if (current == null || !DroneDocks.claim(current.id(), here())) {
                if (health != 1.0f || present || repairing) {
                    health = 1.0f;
                    present = false;
                    repairing = false;
                    sync();
                }
                return;
            }
            drone = current.id();
            droneOwner = ownerId;
            present = current.state() == ArsenalDrone.State.ORBIT; // meteen hier, zonder aankomst
            health = current.healthFraction();
            sync();
        }
        if (!current.docked()) {
            ArsenalServer.setDocked(player, current.id(), true);
        }

        boolean nowPresent = current.state() == ArsenalDrone.State.ORBIT;
        if (nowPresent != present) {
            movedEffects(level, pos, nowPresent);
        }
        boolean nowRepairing = false;
        float nowHealth = current.health();
        if (nowPresent && !current.isFullyRepaired()) {
            // Per halve seconde de helft van wat hij per seconde repareert.
            double repair = StarkConfig.Power.DOCK_REPAIR_PER_SECOND * CHECK_TICKS / 20.0;
            int cost = (int) Math.ceil(repair * StarkConfig.Power.DOCK_ENERGY_PER_HEALTH);
            if (StarkGrid.available(level, pos) >= cost && StarkGrid.draw(level, pos, cost) >= cost) {
                nowHealth = (float) Math.min(StarkConfig.Drones.HEALTH, nowHealth + repair);
                ArsenalServer.setHealth(player, current.id(), nowHealth);
                nowRepairing = true;
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                        2, 0.2, 0.1, 0.2, 0.05);
            }
        }
        float fraction = nowHealth / (float) StarkConfig.Drones.HEALTH;
        if (nowPresent != present || nowRepairing != repairing || Math.abs(fraction - health) > 0.001f) {
            present = nowPresent;
            repairing = nowRepairing;
            health = fraction;
            sync();
        }
    }

    /** Van wie deze dock nu is: wie hem neerzette, of anders wie de toren eromheen gehackt heeft. */
    public @Nullable UUID ownerId(ServerLevel level) {
        if (owner != null) {
            return owner;
        }
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, worldPosition);
        return core == null ? null : core.ownerId();
    }

    /**
     * Een drone uit het arsenaal die nog geen plek heeft. Liefst een die klaarstaat (dan staat hij
     * meteen op de dock), anders een die op pad is (die komt hier terug).
     */
    private static @Nullable ArsenalDrone pickFreeDrone(EdithArsenal arsenal) {
        return arsenal.drones().stream()
                .filter(d -> d.health() > 0 && !DroneDocks.isClaimed(d.id()))
                .filter(d -> d.state() != ArsenalDrone.State.GROUNDED)
                .min(Comparator.comparing((ArsenalDrone d) -> d.state() != ArsenalDrone.State.ORBIT)
                        .thenComparing(d -> d.id().toString()))
                .orElse(null);
    }

    /** Wolkjes en geluid als een drone opstijgt of weer landt. */
    private static void movedEffects(ServerLevel level, BlockPos pos, boolean arrived) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.8;
        double z = pos.getZ() + 0.5;
        if (arrived) {
            level.sendParticles(ParticleTypes.END_ROD, x, y + 1.0, z, 8, 0.3, 0.6, 0.3, 0.02);
            level.playSound(null, pos, ModSounds.DRONE_RECALL.get(), SoundSource.BLOCKS, 0.6f, 1.2f);
        } else {
            level.sendParticles(ParticleTypes.CLOUD, x, y, z, 10, 0.3, 0.1, 0.3, 0.05);
            level.playSound(null, pos, ModSounds.DRONE_DEPLOY.get(), SoundSource.BLOCKS, 0.7f, 1.0f);
            level.playSound(null, pos, SoundEvents.FIRECHARGE_USE, SoundSource.BLOCKS, 0.3f, 1.6f);
        }
    }

    private GlobalPos here() {
        return GlobalPos.of(level.dimension(), worldPosition);
    }

    /** De plek van de drone vrijgeven, en in zijn arsenaal zeggen dat hij niet meer in de hangar staat. */
    private void release(ServerLevel level) {
        if (drone == null) {
            return;
        }
        DroneDocks.release(drone, here());
        ServerPlayer player = droneOwner == null ? null : level.getServer().getPlayerList().getPlayer(droneOwner);
        if (player != null) {
            ArsenalServer.setDocked(player, drone, false);
        }
        drone = null;
        droneOwner = null;
        health = 1.0f;
        present = false;
        repairing = false;
    }

    /** Afgebroken: de drone heeft geen plek meer in de hangar (hij hangt weer gewoon in zijn baan). */
    public void onBroken() {
        if (level instanceof ServerLevel serverLevel) {
            release(serverLevel);
        }
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ================================================================ Aanmelden

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide && drone != null && !DroneDocks.claim(drone, here())) {
            // Intussen staat hij op een andere dock: dan zoeken we een nieuwe.
            drone = null;
            droneOwner = null;
            present = false;
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide && drone != null) {
            DroneDocks.release(drone, here());
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide && drone != null) {
            DroneDocks.release(drone, here());
        }
    }

    // ================================================================ Voor het tekenen en het statusbericht

    public boolean hasDrone() {
        return drone != null;
    }

    public boolean present() {
        return present;
    }

    public boolean repairing() {
        return repairing;
    }

    public float health() {
        return health;
    }

    /** Een getal dat per drone anders is (voor zijn schade en zijn ritme). */
    public int droneSeed() {
        return drone == null ? 0 : drone.hashCode();
    }

    /** Wanneer (speltijd, in jouw spel) de drone vertrok of aankwam; zie DroneDockRenderer. */
    public long movedAt() {
        return movedAt;
    }

    // ================================================================ Opslaan en doorsturen

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        if (drone != null) {
            tag.putUUID("Drone", drone);
        }
        if (droneOwner != null) {
            tag.putUUID("DroneOwner", droneOwner);
        }
        tag.putFloat("Health", health);
        tag.putBoolean("Present", present);
        tag.putBoolean("Repairing", repairing);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boolean wasPresent = present;
        boolean hadDrone = drone != null;
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        drone = tag.hasUUID("Drone") ? tag.getUUID("Drone") : null;
        droneOwner = tag.hasUUID("DroneOwner") ? tag.getUUID("DroneOwner") : null;
        health = tag.contains("Health") ? tag.getFloat("Health") : 1.0f;
        present = tag.getBoolean("Present");
        repairing = tag.getBoolean("Repairing");
        // In jouw spel: vertrok of landde de drone net? Dan laat de renderer dat zien. (Niet bij
        // het allereerste bericht: dan kom je gewoon aanlopen en staat hij er al.)
        if (level != null && level.isClientSide) {
            if (synced && hadDrone && drone != null && wasPresent != present) {
                movedAt = level.getGameTime();
            }
            synced = true;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
