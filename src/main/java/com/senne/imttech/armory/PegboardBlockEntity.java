package com.senne.imttech.armory;

import com.senne.imttech.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Wat er aan één Pegboard-paneel hangt (hoofdstuk 67): elk wapen met zijn gaatje linksonder op dit paneel (0 tot 7 naar
 * rechts en naar boven) en hoe het hangt. Een wapen mag over de panelen rechts en boven uitsteken (zie {@link Pegboard}).
 * Het item blijft precies zoals het was: onderdelen, slijtage, magazijn of cel, sling.
 */
public class PegboardBlockEntity extends BlockEntity {
    /** Eén wapen op het paneel. */
    public record Hung(ItemStack stack, int x, int y, Pegboard.Turn turn) {}

    private final List<Hung> hung = new ArrayList<>();

    public PegboardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PEGBOARD.get(), pos, state);
    }

    public List<Hung> hung() {
        return Collections.unmodifiableList(hung);
    }

    public Direction facing() {
        return getBlockState().getValue(PegboardBlock.FACING);
    }

    /** Waar wapen nummer {@code index} op de wand hangt (in gaatjes over de hele wand). */
    public Pegboard.Spot spot(int index) {
        Hung one = hung.get(index);
        int[] size = Pegboard.size(one.stack(), one.turn());
        int u = Pegboard.column(worldPosition, facing()) * Pegboard.CELLS + one.x();
        int v = worldPosition.getY() * Pegboard.CELLS + one.y();
        return new Pegboard.Spot(this, index, u, v, size[0], size[1]);
    }

    /** Hang dit item (één stuk) met zijn gaatje linksonder op (U, V) van de wand; dat gaatje moet op dit paneel zitten. */
    public void add(ItemStack stack, int u, int v, Pegboard.Turn turn) {
        int x = u - Pegboard.column(worldPosition, facing()) * Pegboard.CELLS;
        int y = v - worldPosition.getY() * Pegboard.CELLS;
        hung.add(new Hung(stack.copyWithCount(1), x, y, turn));
        changed();
    }

    /** Haal wapen nummer {@code index} eraf (geeft het item). */
    public ItemStack remove(int index) {
        ItemStack stack = hung.remove(index).stack();
        changed();
        return stack;
    }

    /** Het midden van een wapen in de wereld (daar valt het als het eraf valt). */
    public Vec3 centre(int index) {
        Pegboard.Spot spot = spot(index);
        Direction facing = facing();
        Direction right = Pegboard.right(facing);
        double du = (spot.u() + spot.w() / 2.0) / Pegboard.CELLS - Pegboard.column(worldPosition, facing);
        double dv = (spot.v() + spot.h() / 2.0) / Pegboard.CELLS - worldPosition.getY();
        // De linkerkant van dit paneel (zoals jij ervoor staat), en 3 pixels van de muur.
        Vec3 corner = Vec3.atLowerCornerOf(worldPosition);
        if (right.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            corner = corner.add(-right.getStepX(), 0, -right.getStepZ());
        }
        if (facing.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            corner = corner.add(-facing.getStepX(), 0, -facing.getStepZ());
        }
        return corner.add(right.getStepX() * du, dv, right.getStepZ() * du)
                .add(facing.getStepX() * 3 / 16.0, 0, facing.getStepZ() * 3 / 16.0);
    }

    /** Laat alles vallen dat niet meer helemaal op panelen hangt (er is er een weggehaald). */
    public void dropLoose(Level level) {
        for (int i = hung.size() - 1; i >= 0; i--) {
            Pegboard.Spot spot = spot(i);
            if (!Pegboard.fitsBoards(level, worldPosition, facing(), spot.u(), spot.v(), spot.w(), spot.h())) {
                Vec3 at = centre(i);
                Containers.dropItemStack(level, at.x, at.y, at.z, remove(i));
            }
        }
    }

    /** Laat alles vallen (het paneel zelf gaat weg). */
    public void dropAll(Level level) {
        for (int i = hung.size() - 1; i >= 0; i--) {
            Vec3 at = centre(i);
            Containers.dropItemStack(level, at.x, at.y, at.z, hung.get(i).stack().copy());
        }
        hung.clear();
    }

    /** Hang wapen nummer {@code index} anders (draaien), op een nieuwe plek; het gaatje linksonder blijft op dit paneel of verhuist. */
    public void turn(int index, int u, int v, Pegboard.Turn turn) {
        Hung old = hung.get(index);
        hung.set(index, new Hung(old.stack(), u - Pegboard.column(worldPosition, facing()) * Pegboard.CELLS,
                v - worldPosition.getY() * Pegboard.CELLS, turn));
        changed();
    }

    // ================================================================ Bewaren en naar je spel sturen

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Hung one : hung) {
            CompoundTag entry = new CompoundTag();
            entry.put("Item", one.stack().save(registries));
            entry.putInt("X", one.x());
            entry.putInt("Y", one.y());
            entry.putInt("Turn", one.turn().ordinal());
            list.add(entry);
        }
        tag.put("Hung", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        hung.clear();
        ListTag list = tag.getList("Hung", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            ItemStack stack = ItemStack.parseOptional(registries, entry.getCompound("Item"));
            if (!stack.isEmpty()) {
                hung.add(new Hung(stack, entry.getInt("X"), entry.getInt("Y"), Pegboard.Turn.byId(entry.getInt("Turn"))));
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
