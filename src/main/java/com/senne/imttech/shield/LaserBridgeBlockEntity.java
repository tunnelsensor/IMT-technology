package com.senne.imttech.shield;

import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.security.BaseTrust;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Wat een brugzender onthoudt (hoofdstuk 34): van wie hij is. Wie hem neerzet, is de eigenaar; alleen die (en wie in
 * deze basis vertrouwd is) opent zijn instelscherm, net als bij de laserzender (zie security/BaseTrust).
 *
 * De kleur, het omkeren en of hij aan staat, zitten in het blok zelf (zie LaserBridgeBlock), want die moet iedereen
 * zien. Doorgeknipte draden en een slot, zoals bij de laserzender, heeft een brug niet: de Bypass Kit werkt er niet op
 * (jouw keuze).
 */
public class LaserBridgeBlockEntity extends BlockEntity implements BaseTrust.Placed {
    private @Nullable UUID owner;
    private String ownerName = "";

    public LaserBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_BRIDGE.get(), pos, state);
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putString("OwnerName", ownerName);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
    }
}
