package com.senne.imttech.armory;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.guns.Caliber;
import com.senne.imttech.guns.MagazineItem;
import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.weapons.EnergyCellItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wat er in een Ammo Box zit (hoofdstuk 67, jouw keuze: "één soort per doos"). Wat je er als eerste in doet, bepaalt de
 * soort: 9mm-patronen, hulzen, kogelpunten, Glock-magazijnen of energiecellen. Is de doos weer leeg, dan mag er een
 * andere soort in.
 *
 * <b>Los</b> (patronen, hulzen, kogelpunten) liggen als stapels van 64 in {@link #items}, tot {@code [armory]
 * ammoBoxLoose} stuks. <b>Stukken</b> (magazijnen en cellen stapelen niet: elk houdt zijn eigen patronen of lading)
 * liggen er elk apart in, tot {@code [armory] ammoBoxPieces}.
 *
 * Breek je de doos af, dan blijft alles erin (zoals een shulkerdoos): de inhoud gaat mee als het gewone
 * {@code minecraft:container} van het item (zie de loot table: copy_components), en terug als je hem neerzet.
 */
public class AmmoBoxBlockEntity extends BlockEntity {
    /** De stapels in de doos: allemaal van dezelfde soort. */
    private final List<ItemStack> items = new ArrayList<>();

    public AmmoBoxBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AMMO_BOX.get(), pos, state);
    }

    // ================================================================ Wat er in mag

    /** Mag dit in een Ammo Box (ongeacht wat er al in zit)? */
    public static boolean accepts(ItemStack stack) {
        // Hoofdstuk 103: de patronen, hulzen en kogelpunten van elk kaliber (elk kaliber is zijn eigen soort).
        return Caliber.ofRound(stack) != null || Caliber.ofCasing(stack) != null || Caliber.ofBullet(stack) != null
                || MagazineItem.typeOf(stack) != null || stack.is(ModItems.ENERGY_CELL.get());
    }

    /** Los (stapelt) of een stuk (een magazijn of een cel, elk met zijn eigen inhoud)? */
    public static boolean loose(ItemStack stack) {
        return stack.getMaxStackSize() > 1;
    }

    /** Zoveel van deze soort passen erin. */
    public static int capacity(ItemStack stack) {
        return Math.max(1, Math.round(loose(stack) ? ImtConfig.Armory.AMMO_BOX_LOOSE : ImtConfig.Armory.AMMO_BOX_PIECES));
    }

    /** Hoe vol een stuk is: patronen in een magazijn, of de lading van een cel (0 tot 100). */
    public static float fullness(ItemStack stack) {
        if (MagazineItem.typeOf(stack) != null) {
            return MagazineItem.rounds(stack);
        }
        return stack.is(ModItems.ENERGY_CELL.get()) ? EnergyCellItem.charge(stack) : 0;
    }

    // ================================================================ Wat er in zit

    /** De soort in de doos (één stuk, een kopie), of leeg als de doos leeg is. */
    public ItemStack sort() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return stack.copyWithCount(1);
            }
        }
        return ItemStack.EMPTY;
    }

    public boolean isEmpty() {
        return count() == 0;
    }

    /** Zoveel stuks liggen erin. */
    public int count() {
        int count = 0;
        for (ItemStack stack : items) {
            count += stack.getCount();
        }
        return count;
    }

    /** Zoveel kunnen er nog bij van deze soort (0 als het een andere soort is dan wat erin ligt). */
    public int room(ItemStack stack) {
        if (!fits(stack)) {
            return 0;
        }
        return Math.max(0, capacity(stack) - count());
    }

    /** Past dit bij wat erin ligt (of is de doos leeg)? */
    public boolean fits(ItemStack stack) {
        if (!accepts(stack)) {
            return false;
        }
        ItemStack sort = sort();
        return sort.isEmpty() || sort.is(stack.getItem());
    }

    /** Een kopie van alles wat erin ligt (voor de tooltip, de tests en het scherm). */
    public List<ItemStack> contents() {
        List<ItemStack> copy = new ArrayList<>(items.size());
        for (ItemStack stack : items) {
            copy.add(stack.copy());
        }
        return copy;
    }

    // ================================================================ Erin en eruit

    /**
     * Doet hooguit {@code max} stuks van deze stapel erin (de stapel zelf krimpt). Geeft hoeveel. Een magazijn of een cel
     * gaat er één voor één in, met zijn eigen inhoud.
     */
    public int insert(ItemStack stack, int max) {
        int amount = Math.min(Math.min(max, stack.getCount()), room(stack));
        if (amount <= 0) {
            return 0;
        }
        if (loose(stack)) {
            int left = amount;
            for (ItemStack inside : items) {
                if (left <= 0) {
                    break;
                }
                if (ItemStack.isSameItemSameComponents(inside, stack) && inside.getCount() < inside.getMaxStackSize()) {
                    int add = Math.min(left, inside.getMaxStackSize() - inside.getCount());
                    inside.grow(add);
                    left -= add;
                }
            }
            while (left > 0) {
                int add = Math.min(left, stack.getMaxStackSize());
                items.add(stack.copyWithCount(add));
                left -= add;
            }
        } else {
            for (int i = 0; i < amount; i++) {
                items.add(stack.copyWithCount(1));
            }
        }
        stack.shrink(amount);
        changed();
        return amount;
    }

    /** Hooguit {@code max} stuks eruit (los: van de laatste stapel; een stuk: het volste). Leeg als er niets in ligt. */
    public ItemStack take(int max) {
        if (max <= 0 || isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack sort = sort();
        if (!loose(sort)) {
            return takeFullest();
        }
        ItemStack taken = ItemStack.EMPTY;
        for (int i = items.size() - 1; i >= 0 && taken.getCount() < Math.min(max, sort.getMaxStackSize()); i--) {
            ItemStack inside = items.get(i);
            if (inside.isEmpty()) {
                items.remove(i);
                continue;
            }
            int want = Math.min(max, sort.getMaxStackSize()) - taken.getCount();
            int got = Math.min(want, inside.getCount());
            if (taken.isEmpty()) {
                taken = inside.copyWithCount(got);
            } else {
                taken.grow(got);
            }
            inside.shrink(got);
            if (inside.isEmpty()) {
                items.remove(i);
            }
        }
        changed();
        return taken;
    }

    /** Het volste magazijn of de volste cel eruit (leeg als er geen in ligt). */
    public ItemStack takeFullest() {
        int best = fullestIndex();
        if (best < 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = items.remove(best);
        changed();
        return taken;
    }

    /** Zo vol is het volste stuk erin (-1 als er geen stuk in ligt). */
    public float fullest() {
        int best = fullestIndex();
        return best < 0 ? -1 : fullness(items.get(best));
    }

    private int fullestIndex() {
        int best = -1;
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty() && !loose(stack) && (best < 0 || fullness(stack) > fullness(items.get(best)))) {
                best = i;
            }
        }
        return best;
    }

    // ================================================================ Bewaren en naar je spel sturen

    private void changed() {
        items.removeIf(ItemStack::isEmpty);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                list.add(stack.save(registries));
            }
        }
        tag.put("Items", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ListTag list = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ItemStack.parse(registries, list.getCompound(i)).ifPresent(items::add);
        }
    }

    /** Afbreken: de inhoud gaat mee op het item (zoals bij een shulkerdoos). */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        // Een lege kist krijgt niets mee: zo stapelt hij weer met nieuwe kisten.
        if (!isEmpty()) {
            components.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        }
    }

    /** Neerzetten: de inhoud van het item komt terug in de doos. */
    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        items.clear();
        input.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyItemsCopy().forEach(items::add);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove("Items");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ================================================================ Voor de tests

    /** Alles eruit (de tests beginnen zo met een lege doos). */
    public void clear() {
        items.clear();
        changed();
    }
}
