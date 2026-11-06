package com.senne.imttech.guns;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Een vast rijtje items (hoofdstuk 52, stap 2): wat er in een holster of pouch zit, en welke drie dingen een speler
 * draagt (holster, granaatpouch, magazijnpouch). Een data component en een attachment moeten {@code equals} hebben, en
 * een ItemStack heeft dat niet: daarom dit record, dat items vergelijkt op wat ze zijn.
 */
public record GearContents(List<ItemStack> stacks) {
    public static final GearContents EMPTY = new GearContents(List.of());

    public static final Codec<GearContents> CODEC = ItemStack.OPTIONAL_CODEC.listOf().xmap(GearContents::new, GearContents::stacks);
    public static final StreamCodec<RegistryFriendlyByteBuf, GearContents> STREAM_CODEC =
            ItemStack.OPTIONAL_LIST_STREAM_CODEC.map(GearContents::new, GearContents::stacks);

    public GearContents {
        List<ItemStack> copy = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            copy.add(stack.copy());
        }
        stacks = List.copyOf(copy);
    }

    /** Zoveel lege vakjes, of de inhoud aangevuld tot {@code size}. */
    public static GearContents sized(List<ItemStack> stacks, int size) {
        List<ItemStack> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(i < stacks.size() ? stacks.get(i) : ItemStack.EMPTY);
        }
        return new GearContents(list);
    }

    /** Het item in dit vakje (een kopie), of leeg. */
    public ItemStack get(int index) {
        return index >= 0 && index < stacks.size() ? stacks.get(index).copy() : ItemStack.EMPTY;
    }

    /** Hetzelfde rijtje met dit vakje vervangen (groeit als dat nodig is). */
    public GearContents with(int index, ItemStack stack) {
        List<ItemStack> list = new ArrayList<>(stacks);
        while (list.size() <= index) {
            list.add(ItemStack.EMPTY);
        }
        list.set(index, stack);
        return new GearContents(list);
    }

    /** Een veranderbare kopie. */
    public List<ItemStack> mutable() {
        List<ItemStack> list = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            list.add(stack.copy());
        }
        return list;
    }

    public boolean isEmpty() {
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public int size() {
        return stacks.size();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GearContents that) || that.stacks.size() != stacks.size()) {
            return false;
        }
        for (int i = 0; i < stacks.size(); i++) {
            if (!ItemStack.matches(stacks.get(i), that.stacks.get(i))) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (ItemStack stack : stacks) {
            hash = 31 * hash + ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount();
        }
        return hash;
    }
}
