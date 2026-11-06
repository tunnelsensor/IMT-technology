package com.senne.imttech.guns;

import com.senne.imttech.registry.ModMenus;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Het scherm van een holster of pouch (hoofdstuk 54, jouw notitie: "nu kun je geen nieuwe volle magazijnen in de pouch
 * steken en kun je ook geen lege er uit halen"): bovenaan zijn vakjes (1 voor de holster, 4 voor granaten, 3 voor
 * magazijnen), eronder je inventaris. Je opent het uit het wiel (linker Alt), met een eigen toets (Opties > Besturing),
 * of met rechtsklik op de holster of pouch in je hand (dan vul je hem voor je hem aantrekt).
 *
 * Elke verandering in de vakjes gaat meteen terug naar het ding zelf: gedragen ({@link #WORN}) via {@link Gear}, in je
 * hand via het item. Het vakje van je werkbalk met het open ding erin zit op slot zolang het scherm open is.
 */
public class GearPouchMenu extends AbstractContainerMenu {
    /** Waar het open ding zit: gedragen, of in je hoofdhand of andere hand. */
    public static final int WORN = -1;
    public static final int MAIN_HAND = 0;
    public static final int OFF_HAND = 1;
    /** Waar de vakjes staan (de bovenkant), zoals het scherm ze tekent. */
    public static final int SLOT_Y = 34;

    private final GearItem.Kind kind;
    private final int where;
    private final Player player;
    private final PouchContainer container;

    /** Op de client: wat voor ding en waar het zit komen van de server mee. */
    public GearPouchMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(containerId, inventory, GearItem.Kind.values()[Math.floorMod(extra.readVarInt(), GearItem.Kind.values().length)],
                extra.readVarInt());
    }

    public GearPouchMenu(int containerId, Inventory inventory, GearItem.Kind kind, int where) {
        super(ModMenus.GEAR_POUCH.get(), containerId);
        this.kind = kind;
        this.where = where;
        this.player = inventory.player;
        List<ItemStack> contents = GearItem.contents(gear());
        container = new PouchContainer(contents.toArray(new ItemStack[0]));
        int count = kind.size();
        int x0 = 88 - count * 9;
        for (int i = 0; i < count; i++) {
            addSlot(new PouchSlot(container, i, x0 + i * 18 + 1, SLOT_Y + 1));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new HotbarSlot(inventory, column, 8 + column * 18, 142));
        }
    }

    public GearItem.Kind kind() {
        return kind;
    }

    /** Het ding dat open is (gedragen of in je hand), of leeg als het weg is. */
    public ItemStack gear() {
        if (where == WORN) {
            return Gear.get(player, kind);
        }
        ItemStack held = player.getItemInHand(where == MAIN_HAND ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND);
        return GearItem.kindOf(held) == kind ? held : ItemStack.EMPTY;
    }

    /** De vakjes: alles wat erin verandert, gaat meteen naar het ding zelf (alleen op de server). */
    private final class PouchContainer extends SimpleContainer {
        PouchContainer(ItemStack... items) {
            super(items);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            if (player.level().isClientSide) {
                return;
            }
            List<ItemStack> contents = new ArrayList<>();
            for (int i = 0; i < getContainerSize(); i++) {
                contents.add(getItem(i).copy());
            }
            if (where == WORN) {
                Gear.setContents(player, kind, contents);
            } else {
                ItemStack held = gear();
                if (!held.isEmpty()) {
                    GearItem.setContents(held, contents);
                }
            }
        }
    }

    /** Een vakje van de pouch: alleen wat erin past (pistolen, granaten of magazijnen). */
    private final class PouchSlot extends Slot {
        PouchSlot(SimpleContainer container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return kind.accepts(stack);
        }
    }

    /** Je werkbalk: het vakje met het open ding erin zit op slot. */
    private final class HotbarSlot extends Slot {
        HotbarSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        private boolean locked() {
            return where == MAIN_HAND && getContainerSlot() == player.getInventory().selected;
        }

        @Override
        public boolean mayPickup(Player player) {
            return !locked();
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !locked();
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int pouch = kind.size();
        if (index < pouch) {
            if (!moveItemStackTo(stack, pouch, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!kind.accepts(stack) || !moveItemStackTo(stack, 0, pouch, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return !gear().isEmpty();
    }
}
