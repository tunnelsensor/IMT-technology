package com.senne.imttech.storage;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Het scherm van een Storage Interface aan de kant van de vakjes (hoofdstuk 113). Drie tabbladen:
 * <ul>
 *   <li><b>Order</b>: wat er in je opslag zit (getekend door het scherm zelf, het zijn geen vakjes), en bestellen;</li>
 *   <li><b>Store</b>: de negen IN-vakjes: leg er spullen in en druk op Store, dan bergt een auto ze op;</li>
 *   <li><b>Cars</b>: de auto's koppelen en losmaken.</li>
 * </ul>
 * Op elk tabblad staan onderaan de negen afhaalvakjes (PICK-UP: wat een auto bracht) en je eigen inventaris. Wat er in
 * de opslag zit, de bestellingen en de auto's komen in een eigen bericht (StorageStatePayload): dat stuurt dit menu zodra
 * er iets verandert. Het kijkt elke tick (jouw regel voor schermen met getallen: ze verversen elke tick).
 */
public class StorageMenu extends AbstractContainerMenu {
    public static final int TAB_ORDER = 0;
    public static final int TAB_STORE = 1;
    public static final int TAB_CARS = 2;

    /** Waar alles staat (pixels in het scherm). */
    public static final int WIDTH = 300;
    public static final int HEIGHT = 222;
    /** Het vlak links onder de titel: de lijst van de opslag, de IN-vakjes, of de auto's. */
    public static final int AREA_X = 8;
    public static final int AREA_Y = 36;
    public static final int AREA_W = 162;
    public static final int AREA_H = 72;
    public static final int IN_Y = 46;
    public static final int OUT_Y = 120;
    public static final int INVENTORY_Y = 142;
    public static final int HOTBAR_Y = 200;
    /** De kolom rechts: wat je koos, en de bestellingen. */
    public static final int SIDE_X = 178;
    public static final int SIDE_W = WIDTH - SIDE_X - 8;

    private static final int FIRST_OUT = StorageInterfaceBlockEntity.IN_SLOTS;
    private static final int FIRST_INVENTORY = FIRST_OUT + StorageInterfaceBlockEntity.OUT_SLOTS;

    /** De interface (alleen op de server). */
    private final @Nullable StorageInterfaceBlockEntity station;
    private final Container in;
    private final Container out;
    private final Player player;
    private int tab = TAB_ORDER;
    /** In jouw spel: wat de server het laatst stuurde. */
    private StorageStatePayload state = StorageStatePayload.EMPTY;
    /** Op de server: wat we het laatst stuurden. */
    private long sentSignature = Long.MIN_VALUE;

    /** In jouw spel. */
    public StorageMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(containerId, inventory, new SimpleContainer(StorageInterfaceBlockEntity.IN_SLOTS),
                new SimpleContainer(StorageInterfaceBlockEntity.OUT_SLOTS), null);
        extra.readBlockPos();
    }

    /** Op de server. */
    public StorageMenu(int containerId, Inventory inventory, StorageInterfaceBlockEntity station) {
        this(containerId, inventory, station.in(), station.out(), station);
    }

    private StorageMenu(int containerId, Inventory inventory, Container in, Container out, @Nullable StorageInterfaceBlockEntity station) {
        super(StorageRegistry.STORAGE_MENU.get(), containerId);
        this.station = station;
        this.in = in;
        this.out = out;
        this.player = inventory.player;
        for (int column = 0; column < StorageInterfaceBlockEntity.IN_SLOTS; column++) {
            addSlot(new InSlot(in, column, AREA_X + 1 + column * 18, IN_Y));
        }
        for (int column = 0; column < StorageInterfaceBlockEntity.OUT_SLOTS; column++) {
            addSlot(new OutSlot(out, column, AREA_X + 1 + column * 18, OUT_Y));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, AREA_X + 1 + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, AREA_X + 1 + column * 18, HOTBAR_Y));
        }
    }

    /** De interface van dit scherm (alleen op de server; in jouw spel null). */
    public @Nullable StorageInterfaceBlockEntity station() {
        return station;
    }

    public int tab() {
        return tab;
    }

    /** In jouw spel: een ander tabblad (de IN-vakjes zijn alleen op Store te zien). */
    public void setTab(int tab) {
        this.tab = Math.max(TAB_ORDER, Math.min(TAB_CARS, tab));
    }

    /** In jouw spel: wat de server het laatst stuurde. */
    public StorageStatePayload state() {
        return state;
    }

    public void setState(StorageStatePayload state) {
        this.state = state;
    }

    /** Een IN-vakje: alleen op het tabblad Store te zien. Wat je erin legt, bergt een auto op. */
    private final class InSlot extends Slot {
        InSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return tab == TAB_STORE;
        }
    }

    /** Een afhaalvakje: je pakt eruit wat een auto bracht, maar legt er zelf niets in. */
    private static final class OutSlot extends Slot {
        OutSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /**
     * Elke tick op de server (zolang het scherm open staat): veranderde er iets aan de opslag, de bestellingen of de
     * auto's, dan krijgt het scherm een nieuw bericht.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (station == null || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        long signature = Storages.signature(serverPlayer, station);
        if (signature != sentSignature) {
            sentSignature = signature;
            Storages.send(serverPlayer, station);
        }
    }

    /** Het scherm wisselt van tabblad: de server weet het dan ook (voor shift-klik, zie quickMoveStack). */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        setTab(id);
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (station == null) {
            return true;
        }
        BlockPos pos = station.getBlockPos();
        return !station.isRemoved() && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }

    /**
     * Shift-klik: uit de IN- of afhaalvakjes naar je inventaris; uit je inventaris naar de IN-vakjes (om op te bergen),
     * maar alleen op het tabblad Store: daar zie je ze.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack before = stack.copy();
        if (index < FIRST_INVENTORY) {
            if (!moveItemStackTo(stack, FIRST_INVENTORY, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (tab != TAB_STORE || !moveItemStackTo(stack, 0, FIRST_OUT, false)) {
            return ItemStack.EMPTY; // alleen op het tabblad Store ga je met shift-klik de IN-vakjes in
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stack.getCount() == before.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stack);
        return before;
    }
}
