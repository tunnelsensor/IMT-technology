package com.senne.imttech.lan;

import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.logic.InputDeviceBlockEntity;
import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.security.BaseTrust;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van een Router (zie RouterBlock): zijn naam ("Router 1"), wie hem neerzette, en om de twee seconden: heeft
 * hij stroom? Dat staat dan in het blok (ONLINE), zodat de lampjes branden en het netwerk erdoor kan. Wie er draadloos aan
 * hangt, staat niet hier maar in {@link RouterLinks} (één lijst voor de hele wereld).
 */
public class RouterBlockEntity extends BlockEntity implements BaseTrust.Placed, NamedDevice, DevicePower.Idle {
    private String name = "";
    private @Nullable UUID owner;
    private String ownerName = "";

    public RouterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROUTER.get(), pos, state);
    }

    // ================================================================ Naam en eigenaar

    @Override
    public String name() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = InputDeviceBlockEntity.cleanName(name);
        setChanged();
    }

    @Override
    public @Nullable UUID owner() {
        return owner;
    }

    @Override
    public String ownerName() {
        return ownerName;
    }

    @Override
    public void setOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        setChanged();
    }

    // ================================================================ Stroom

    public void serverTick(ServerLevel level, BlockPos pos) {
        if ((level.getGameTime() + pos.hashCode()) % 40 == 0) {
            updateOnline(level);
        }
    }

    /**
     * Heeft hij stroom? Hij betaalt zijn stroom (van een stroomkabel, generator of PoE-kabel ernaast) en zet in het blok of
     * dat lukte. Met {@code [network] required = false} (het oude gedrag) heeft hij geen stroom nodig. Verandert het, dan
     * loopt het netwerk anders: alle netten opnieuw zoeken.
     */
    public void updateOnline(ServerLevel level) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof RouterBlock block)) {
            return;
        }
        boolean online = !LanGrid.required() || block.upkeep() <= 0 || PowerGrid.pay(level, worldPosition, block.upkeep());
        if (online != state.getValue(RouterBlock.ONLINE)) {
            // Hoofdstuk 124: zonder vormupdates naar de buren (zie CameraBlockEntity.updateOnline).
            level.setBlock(worldPosition, state.setValue(RouterBlock.ONLINE, online),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            LanGrid.changed();
        }
    }

    /**
     * Hoofdstuk 124: draait zijn stuk wereld niet (aan het eind van een lange kabel, dat NetworkKeeper alleen geladen houdt),
     * dan kijkt zijn kern om de twee seconden of hij aan kan (zie DevicePower.refreshIdle). Anders liet een router die ooit
     * geen stroom had, zijn apparaten nooit meer op het net. Alleen aan, nooit uit (zie DevicePower.Idle).
     */
    @Override
    public void refreshIdle(ServerLevel level) {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof RouterBlock && !state.getValue(RouterBlock.ONLINE)) {
            updateOnline(level);
        }
    }

    // ================================================================ Bewaren

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Name", name);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        name = tag.getString("Name");
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
    }

    /** Breek je hem af, dan krijgt het item zijn naam mee (zie de loot table: copy_components). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!name.isEmpty()) {
            components.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        }
    }

    @Override
    public void removeComponentsFromTag(CompoundTag tag) {
        super.removeComponentsFromTag(tag);
        tag.remove("Name");
    }
}
