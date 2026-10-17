package com.senne.imttech.arsenal;

import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.security.BaseTrust;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De "binnenkant" van een Drone Dock (hoofdstuk 25): welke security drone hier thuis is, en van wie het dock is.
 * Het laden doet de drone zelf: die weet waar zijn dock staat en betaalt de stroom via het dock (zie
 * GuardDroneEntity, net als een robot op zijn Robot Dock).
 *
 * Vroeger stonden hier drones uit je arsenaal; jouw antwoord van 14 oktober: "arsenaal-drones gaan niet meer op
 * een dock, ze blijven in hun baan". Die herstellen zich nu daar (zie ArsenalServer.orbitRepair). Wat een oud dock
 * nog over zo'n drone bewaarde, lezen we niet meer.
 *
 * Van wie is een dock? Van wie hem neerzet. Een dock in een Bibie's Industries Tower (neergezet door de toren zelf, zonder
 * eigenaar) is van wie de toren gehackt heeft.
 */
public class DroneDockBlockEntity extends BlockEntity {
    /** Wie hem neerzette (null bij een dock van de toren: dan de eigenaar van de toren). */
    private @Nullable UUID owner;
    /** De security drone die hier thuis is, of null. */
    private @Nullable UUID bot;

    public DroneDockBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRONE_DOCK.get(), pos, state);
    }

    /** Neergezet door een speler: dan is hij van hem. */
    public void setOwner(UUID owner) {
        this.owner = owner;
        setChanged();
    }

    /** Van wie dit dock nu is: wie hem neerzette, of anders wie de toren eromheen gehackt heeft. */
    public @Nullable UUID ownerId(ServerLevel level) {
        if (owner != null) {
            return owner;
        }
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, worldPosition);
        return core == null ? null : core.ownerId();
    }

    /**
     * Mag deze speler hier een drone neerzetten? Wie het dock neerzette, of wie in die basis vertrouwd is (dezelfde
     * regel als bij een laserzender, zie BaseTrust). Een dock van niemand mag iedereen gebruiken.
     */
    public boolean mayUse(ServerPlayer player) {
        UUID id = ownerId(player.serverLevel());
        return id == null || id.equals(player.getUUID()) || BaseTrust.trustedHere(player.serverLevel(), worldPosition, player);
    }

    /** De security drone die hier thuis is, of null. */
    public @Nullable UUID bot() {
        return bot;
    }

    public void setBot(@Nullable UUID bot) {
        this.bot = bot;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        if (bot != null) {
            tag.putUUID("Bot", bot);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        bot = tag.hasUUID("Bot") ? tag.getUUID("Bot") : null;
    }
}
