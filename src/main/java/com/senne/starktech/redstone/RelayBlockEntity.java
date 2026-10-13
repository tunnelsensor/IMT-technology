package com.senne.starktech.redstone;

import com.senne.starktech.registry.ModBlockEntities;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Wat een E.D.I.T.H.-relais onthoudt: van wie het is (13 oktober: "zorg dat de relay die je zelf plaatst,
 * jij de enige bent die die kan bewerken").
 *
 * Wie het neerzet, is de eigenaar. Alleen de eigenaar kan het in zijn redstone-lijst zetten, een naam en
 * tags geven, omkeren en schakelen (zie EdithRedstone.otherOwner). Een relais van vóór 13 oktober heeft
 * nog geen eigenaar: dat wordt van wie het als eerste gebruikt (meestal wie het al in zijn lijst had).
 *
 * Omdat het relais nu een blok-entiteit heeft, kan een zuiger het niet meer wegduwen (zoals een kist). Zo
 * kan niemand het verschuiven en de eigenaar kwijtraken.
 */
public class RelayBlockEntity extends BlockEntity {
    private @Nullable UUID owner;
    private String ownerName = "";

    public RelayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RELAY.get(), pos, state);
    }

    public @Nullable UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

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
