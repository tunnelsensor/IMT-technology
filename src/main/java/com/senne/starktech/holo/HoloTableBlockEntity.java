package com.senne.starktech.holo;

import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.energy.StarkGrid;
import com.senne.starktech.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
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
    /** Hoe ver de kaart reikt; een nummer uit HoloSettings.RANGES. */
    private int range = HoloSettings.DEFAULT_RANGE;
    /** Hoe groot het hologram is; een nummer uit HoloSettings.SCALES. */
    private int scale = HoloSettings.DEFAULT_SCALE;
    /** Draait het hologram rond? Sommigen kijken liever naar een stilstaand beeld. */
    private boolean spin = true;
    /**
     * Heeft de tafel stroom (van een Stark-kabel of een generator ertegenaan)? Zonder stroom
     * geen hologram. De server kijkt het elke seconde na (zie serverTick) en stuurt het mee.
     */
    private boolean powered;

    public HoloTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOLO_TABLE.get(), pos, state);
    }

    public HoloMode mode() {
        return mode;
    }

    public ItemStack blueprint() {
        return blueprint;
    }

    public int range() {
        return range;
    }

    public int scale() {
        return scale;
    }

    public boolean powered() {
        return powered;
    }

    /**
     * Elke seconde (alleen op de server): een seconde stroom betalen. Lukt dat niet, dan gaat het
     * hologram uit; komt de stroom terug, dan weer aan.
     */
    public void serverTick(ServerLevel level, BlockPos pos) {
        if (Math.floorMod(level.getGameTime() + pos.hashCode(), 20) != 0) {
            return;
        }
        int cost = EdithBalance.HOLO_UPKEEP * 20;
        boolean now = StarkGrid.available(level, pos) >= cost;
        if (now) {
            StarkGrid.draw(level, pos, cost);
        }
        if (now != powered) {
            powered = now;
            sync();
        }
    }

    public boolean spin() {
        return spin;
    }

    /** Hoe ver de kaart reikt, in blokken. */
    public int rangeBlocks() {
        return HoloSettings.rangeBlocks(range);
    }

    /** Hoe groot het hologram is (1.0 = normaal). */
    public float scaleFactor() {
        return HoloSettings.scale(scale);
    }

    public void setRange(int index) {
        this.range = HoloSettings.clampRange(index);
        sync();
    }

    public void setScale(int index) {
        this.scale = HoloSettings.clampScale(index);
        sync();
    }

    public void setSpin(boolean spinning) {
        this.spin = spinning;
        sync();
    }

    /** Haalt het voorwerp van tafel; de bouwtekening is dan leeg. */
    public void clearBlueprint() {
        this.blueprint = ItemStack.EMPTY;
        sync();
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
        tag.putByte("Range", (byte) range);
        tag.putByte("Scale", (byte) scale);
        tag.putBoolean("Spin", spin);
        tag.putBoolean("Powered", powered);
        if (!blueprint.isEmpty()) {
            tag.put("Blueprint", blueprint.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        mode = HoloMode.byId(tag.getByte("Mode"));
        range = HoloSettings.clampRange(tag.contains("Range") ? tag.getByte("Range") : HoloSettings.DEFAULT_RANGE);
        scale = HoloSettings.clampScale(tag.contains("Scale") ? tag.getByte("Scale") : HoloSettings.DEFAULT_SCALE);
        spin = !tag.contains("Spin") || tag.getBoolean("Spin");
        powered = tag.getBoolean("Powered");
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
