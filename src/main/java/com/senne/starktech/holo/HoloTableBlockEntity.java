package com.senne.starktech.holo;

import com.senne.starktech.registry.ModBlockEntities;
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
 * Wat de holotafel op dit moment laat zien.
 *
 * De kleur zit niet hier maar in het blok zelf (een blockstate), net als bij de laserzenders:
 * zo zie je de kleur ook aan de tafel en hoeft de tekenkant er niets voor op te halen.
 *
 * Deze gegevens gaan naar iedereen die de tafel kan zien, want de projectie hangt in de wereld
 * en niet op jouw scherm: je vrienden kijken naar hetzelfde hologram.
 */
public class HoloTableBlockEntity extends BlockEntity {
    private HoloMode mode = HoloMode.MAP;
    /** Het voorwerp waarvan we het recept tonen (alleen in de stand BLUEPRINT). */
    private ItemStack blueprint = ItemStack.EMPTY;

    public HoloTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOLO_TABLE.get(), pos, state);
    }

    public HoloMode mode() {
        return mode;
    }

    public ItemStack blueprint() {
        return blueprint;
    }

    public void setMode(HoloMode newMode) {
        this.mode = newMode;
        sync();
    }

    /** Legt een voorwerp op de tafel en springt naar de bouwtekening. */
    public void setBlueprint(ItemStack stack) {
        this.blueprint = stack.copyWithCount(1);
        this.mode = HoloMode.BLUEPRINT;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_ALL);
        }
    }

    // ---------------------------------------------------------------- Opslaan en versturen

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putByte("Mode", (byte) mode.ordinal());
        if (!blueprint.isEmpty()) {
            tag.put("Blueprint", blueprint.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = HoloMode.byId(tag.getByte("Mode"));
        blueprint = tag.contains("Blueprint")
                ? ItemStack.parse(registries, tag.getCompound("Blueprint")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    /** Wat er naar de spelers in de buurt gaat als de tafel verandert. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
