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
 * Wat een laserzender onthoudt: van wie hij is (13 oktober: "enkel de persoon die het heeft geplaatst (of
 * trusted players in zone) kunnen hier op sneak klikken en dan een GUI openen").
 *
 * Wie hem neerzet, is de eigenaar. De kleur, het omkeren en liggend/staand zitten in het blok zelf (zie
 * LaserEmitterBlock), want die moet iedereen zien; alleen de eigenaar staat hier.
 *
 * Een zender van vóór 13 oktober, of een die in een gebouw stond (de Biebia's Industries Tower, de Biebia's Industries-bunker), heeft
 * nog geen eigenaar. Wie dan mag, staat in security/BaseTrust (dezelfde regel als bij de keycardlezer).
 *
 * Omdat de zender nu een blok-entiteit heeft, kan een zuiger hem niet meer wegduwen (zoals een kist).
 */
public class LaserEmitterBlockEntity extends BlockEntity implements BaseTrust.Placed {
    private @Nullable UUID owner;
    private String ownerName = "";

    public LaserEmitterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_EMITTER.get(), pos, state);
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
