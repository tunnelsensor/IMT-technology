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
 * Wat er aan één Pegboard-blok hangt (hoofdstuk 67): elk wapen met zijn gaatje linksonder op dit paneel (0 tot 7 naar
 * rechts en naar boven) en hoe het hangt. Een wapen mag over de panelen rechts en boven uitsteken (zie {@link Pegboard}).
 * Het item blijft precies zoals het was: onderdelen, slijtage, magazijn of cel, sling.
 *
 * Hoofdstuk 70: in een hoek hangen er twee panelen in dit blok; {@code side} zegt dat een item op het tweede hangt. Een
 * item kan vastgezet zijn ({@code locked}); pak je het, dan blijft het als schaduw ({@code shadow}) op zijn plek: het
 * item is dan alleen nog een voorbeeld (welk soort, hoe groot), er hangt niets echts.
 */
public class PegboardBlockEntity extends BlockEntity {
    /** Eén wapen (of de schaduw van een vastgezet wapen) op het paneel. */
    public record Hung(ItemStack stack, int x, int y, Pegboard.Turn turn, boolean side, boolean locked, boolean shadow) {
        Hung moved(int x, int y, Pegboard.Turn turn) {
            return new Hung(stack, x, y, turn, side, locked, shadow);
        }
    }

    private final List<Hung> hung = new ArrayList<>();

    public PegboardBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PEGBOARD.get(), pos, state);
    }

    public List<Hung> hung() {
        return Collections.unmodifiableList(hung);
    }

    /** De kant op die het (eerste) paneel kijkt. */
    public Direction facing() {
        return getBlockState().getValue(PegboardBlock.FACING);
    }

    /** De muur waar wapen nummer {@code index} aan hangt: het eerste paneel, of in een hoek misschien het tweede. */
    public Direction wall(int index) {
        return wallOf(hung.get(index));
    }

    private Direction wallOf(Hung one) {
        Direction side = one.side() ? Pegboard.sideFacing(getBlockState()) : null;
        return side != null ? side : facing();
    }

    /** Waar wapen nummer {@code index} op de wand hangt (in gaatjes over de hele wand). */
    public Pegboard.Spot spot(int index) {
        Hung one = hung.get(index);
        Direction wall = wallOf(one);
        int[] size = Pegboard.size(one.stack(), one.turn());
        int u = Pegboard.column(worldPosition, wall) * Pegboard.CELLS + one.x();
        int v = worldPosition.getY() * Pegboard.CELLS + one.y();
        return new Pegboard.Spot(this, index, wall, u, v, size[0], size[1], one.shadow());
    }

    /** Hang dit item (één stuk) op het eerste paneel; zie hieronder. */
    public void add(ItemStack stack, int u, int v, Pegboard.Turn turn) {
        add(stack, facing(), u, v, turn, false);
    }

    /**
     * Hang dit item (één stuk) aan muur {@code wall} van dit blok, met zijn gaatje linksonder op (U, V) van die wand; dat
     * gaatje moet op dit paneel zitten.
     */
    public void add(ItemStack stack, Direction wall, int u, int v, Pegboard.Turn turn, boolean locked) {
        int x = u - Pegboard.column(worldPosition, wall) * Pegboard.CELLS;
        int y = v - worldPosition.getY() * Pegboard.CELLS;
        hung.add(new Hung(stack.copyWithCount(1), x, y, turn, wall != facing(), locked, false));
        changed();
    }

    /** Haal wapen nummer {@code index} er helemaal af (ook een schaduw); geeft het item. */
    public ItemStack remove(int index) {
        ItemStack stack = hung.remove(index).stack();
        changed();
        return stack;
    }

    /**
     * Pak wapen nummer {@code index}: je krijgt het item. Was het vastgezet, dan blijft zijn schaduw op de plek (met een
     * kopie als voorbeeld), anders is de plek weer vrij.
     */
    public ItemStack take(int index) {
        Hung one = hung.get(index);
        if (!one.locked()) {
            return remove(index);
        }
        hung.set(index, new Hung(one.stack().copy(), one.x(), one.y(), one.turn(), one.side(), true, true));
        changed();
        return one.stack();
    }

    /** Hang {@code stack} (één stuk) op de schaduw van nummer {@code index}: precies daar, zoals het hing, en weer vastgezet. */
    public void fill(int index, ItemStack stack) {
        Hung one = hung.get(index);
        hung.set(index, new Hung(stack.copyWithCount(1), one.x(), one.y(), one.turn(), one.side(), true, false));
        changed();
    }

    /** Zet wapen nummer {@code index} vast of los. */
    public void setLocked(int index, boolean locked) {
        Hung one = hung.get(index);
        hung.set(index, new Hung(one.stack(), one.x(), one.y(), one.turn(), one.side(), locked, one.shadow()));
        changed();
    }

    /** Het midden van een wapen in de wereld (daar valt het als het eraf valt). */
    public Vec3 centre(int index) {
        Pegboard.Spot spot = spot(index);
        Direction wall = spot.wall();
        Direction right = Pegboard.right(wall);
        double du = (spot.u() + spot.w() / 2.0) / Pegboard.CELLS - Pegboard.column(worldPosition, wall);
        double dv = (spot.v() + spot.h() / 2.0) / Pegboard.CELLS - worldPosition.getY();
        // De linkerkant van dit paneel (zoals jij ervoor staat), en 3 pixels van de muur.
        Vec3 corner = Vec3.atLowerCornerOf(worldPosition);
        if (right.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            corner = corner.add(-right.getStepX(), 0, -right.getStepZ());
        }
        if (wall.getAxisDirection() == Direction.AxisDirection.NEGATIVE) {
            corner = corner.add(-wall.getStepX(), 0, -wall.getStepZ());
        }
        return corner.add(right.getStepX() * du, dv, right.getStepZ() * du)
                .add(wall.getStepX() * 3 / 16.0, 0, wall.getStepZ() * 3 / 16.0);
    }

    /** Laat alles vallen dat niet meer helemaal op panelen hangt (er is er een weggehaald); een schaduw verdwijnt gewoon. */
    public void dropLoose(Level level) {
        for (int i = hung.size() - 1; i >= 0; i--) {
            Pegboard.Spot spot = spot(i);
            if (!Pegboard.fitsBoards(level, worldPosition, spot.wall(), spot.u(), spot.v(), spot.w(), spot.h())) {
                Vec3 at = centre(i);
                boolean shadow = spot.shadow();
                ItemStack stack = remove(i);
                if (!shadow) {
                    Containers.dropItemStack(level, at.x, at.y, at.z, stack);
                }
            }
        }
    }

    /** Laat alles vallen (het paneel zelf gaat weg); schaduwen zijn geen echte items. */
    public void dropAll(Level level) {
        for (int i = hung.size() - 1; i >= 0; i--) {
            if (!hung.get(i).shadow()) {
                Vec3 at = centre(i);
                Containers.dropItemStack(level, at.x, at.y, at.z, hung.get(i).stack().copy());
            }
        }
        hung.clear();
    }

    /** Hang wapen nummer {@code index} anders (draaien), op een nieuwe plek; het gaatje linksonder blijft op dit paneel. */
    public void turn(int index, int u, int v, Pegboard.Turn turn) {
        Hung old = hung.get(index);
        Direction wall = wallOf(old);
        hung.set(index, old.moved(u - Pegboard.column(worldPosition, wall) * Pegboard.CELLS,
                v - worldPosition.getY() * Pegboard.CELLS, turn));
        changed();
    }

    /** Draaien waarbij het gaatje linksonder op een ander paneel van dezelfde wand komt: het verhuist, vastgezet of niet. */
    public void moveTo(int index, PegboardBlockEntity other, int u, int v, Pegboard.Turn turn) {
        Hung old = hung.get(index);
        Direction wall = wallOf(old);
        hung.remove(index);
        changed();
        other.hung.add(new Hung(old.stack(), u - Pegboard.column(other.worldPosition, wall) * Pegboard.CELLS,
                v - other.worldPosition.getY() * Pegboard.CELLS, turn, wall != other.facing(), old.locked(), old.shadow()));
        other.changed();
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
            // Hoofdstuk 70. Alleen als ze aan staan: een oude wereld heeft ze niet, en dan zijn ze uit.
            if (one.side()) {
                entry.putBoolean("Side", true);
            }
            if (one.locked()) {
                entry.putBoolean("Locked", true);
            }
            if (one.shadow()) {
                entry.putBoolean("Shadow", true);
            }
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
                boolean shadow = entry.getBoolean("Shadow");
                hung.add(new Hung(stack, entry.getInt("X"), entry.getInt("Y"), Pegboard.Turn.byId(entry.getInt("Turn")),
                        entry.getBoolean("Side"), entry.getBoolean("Locked") || shadow, shadow));
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
