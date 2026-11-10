package com.senne.imttech.backpack;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Het scherm van een open rugzak (hoofdstuk 72): bovenaan zijn vakjes (2, 3 of 4 rijen van 9), eronder je inventaris,
 * zoals een kist. Je opent het pas na de rits (BackpackZip). Zolang het open is loop je traag (Backpacks.onPlayerTick),
 * en er is een sorteerknop (BackpackSortPayload).
 *
 * Er mag geen rugzak, shulkerdoos of andere doos in (BackpackItem.mayHold). Open je de rugzak in je hand, dan zit dat
 * vakje van je werkbalk op slot.
 *
 * Stap 2: rechts van de vakjes een strook met de MOLLE-plekken (2, 3 of 4, onder elkaar): daar hang je een MOLLE-pouch
 * aan de rugzak (met zijn inhoud), of haal je hem eraf. Shift-klik met een pouch zet hem eerst op een vrije plek.
 */
public class BackpackMenu extends AbstractContainerMenu {
    /** Waar de vakjes van de rugzak beginnen (linksboven), zoals het scherm ze tekent. */
    public static final int PACK_X = 8;
    public static final int PACK_Y = 18;
    /** Stap 2: waar de MOLLE-plekken staan (rechts naast de rugzak, onder elkaar). */
    public static final int MOLLE_X = 184;

    private final BackpackItem.Size size;
    private final Container container;
    private final int lockedSlot;
    private final Player player;

    /** Op de client: de maat en het vakje op slot komen van de server mee. */
    public BackpackMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(containerId, inventory, BackpackItem.Size.values()[Math.floorMod(extra.readVarInt(), BackpackItem.Size.values().length)],
                null, extra.readVarInt());
    }

    /** Op de server: met de echte (gedeelde) inhoud. */
    public BackpackMenu(int containerId, Inventory inventory, BackpackItem.Size size, BackpackInventory open, int lockedSlot) {
        super(BackpackRegistry.BACKPACK_MENU.get(), containerId);
        this.size = size;
        this.container = open == null ? new SimpleContainer(size.slots() + size.molle) : open;
        this.lockedSlot = lockedSlot;
        this.player = inventory.player;
        container.startOpen(player);
        for (int i = 0; i < size.slots(); i++) {
            addSlot(new PackSlot(container, i, PACK_X + (i % 9) * 18, PACK_Y + (i / 9) * 18));
        }
        for (int i = 0; i < size.molle; i++) {
            addSlot(new MolleSlot(container, size.slots() + i, MOLLE_X, PACK_Y + i * 18));
        }
        int inventoryY = inventoryY(size);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, inventoryY + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new HotbarSlot(inventory, column, 8 + column * 18, inventoryY + 58));
        }
    }

    /** Waar je inventaris begint (zoals bij een kist: onder de rijen van de rugzak). */
    public static int inventoryY(BackpackItem.Size size) {
        return PACK_Y + size.rows * 18 + 13;
    }

    public BackpackItem.Size size() {
        return size;
    }

    /** Het eerste vakje van je inventaris in dit menu (na de rugzak en zijn MOLLE-plekken). */
    public int inventoryStart() {
        return size.slots() + size.molle;
    }

    /** De inhoud op de server (of null in jouw spel). */
    public BackpackInventory open() {
        return container instanceof BackpackInventory inventory ? inventory : null;
    }

    /** Een vakje van de rugzak: alles wat mag (geen dozen in dozen). */
    private static final class PackSlot extends Slot {
        PackSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return BackpackItem.mayHold(stack);
        }
    }

    /** Stap 2: een MOLLE-plek: alleen een MOLLE-pouch, één per plek. */
    private static final class MolleSlot extends Slot {
        MolleSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.getItem() instanceof MolleItem;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    /** Je werkbalk: het vakje met de open rugzak erin zit op slot. */
    private final class HotbarSlot extends Slot {
        HotbarSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return getContainerSlot() != lockedSlot;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return getContainerSlot() != lockedSlot;
        }
    }

    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType, Player player) {
        // Met een cijfertoets een vakje wisselen mag niet met het vakje op slot (daar zit de open rugzak).
        if (clickType == net.minecraft.world.inventory.ClickType.SWAP && button == lockedSlot) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int pack = size.slots();
        int inventory = inventoryStart();
        if (index < inventory) {
            // Uit de rugzak (of van een MOLLE-plek) naar je inventaris.
            if (!moveItemStackTo(stack, inventory, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (stack.getItem() instanceof MolleItem) {
            // Een pouch: eerst op een vrije MOLLE-plek, anders (leeg) in de rugzak.
            if (!moveItemStackTo(stack, pack, inventory, false)
                    && (!BackpackItem.mayHold(stack) || !moveItemStackTo(stack, 0, pack, false))) {
                return ItemStack.EMPTY;
            }
        } else if (!BackpackItem.mayHold(stack) || !moveItemStackTo(stack, 0, pack, false)) {
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
        if (!(container instanceof BackpackInventory open)) {
            return true;
        }
        return player instanceof ServerPlayer serverPlayer && open.valid() && open.place().reachable(serverPlayer);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
