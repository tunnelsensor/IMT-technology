package com.senne.imttech.backpack;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wat er in een neergezette rugzak zit (hoofdstuk 72): de rugzak zelf, als item, met alles erin (zijn inhoud, kleur,
 * camo en pouches). Zo komt hij er bij het oppakken precies zo weer uit. Jouw spel krijgt hem ook (om hem te tekenen
 * in zijn kleur, met zijn pouches).
 */
public class BackpackBlockEntity extends BlockEntity {
    private ItemStack pack = ItemStack.EMPTY;

    public BackpackBlockEntity(BlockPos pos, BlockState state) {
        super(BackpackRegistry.BACKPACK_BLOCK_ENTITY.get(), pos, state);
    }

    /** De rugzak (het echte item; niet veranderen, gebruik {@link #setPack}). */
    public ItemStack pack() {
        return pack;
    }

    public void setPack(ItemStack pack) {
        this.pack = pack.copy();
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!pack.isEmpty()) {
            tag.put("Pack", pack.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pack = tag.contains("Pack") ? ItemStack.parseOptional(registries, tag.getCompound("Pack")) : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
