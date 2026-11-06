package com.senne.imttech.guns;

import com.senne.imttech.registry.ModMenus;
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
 * Het menu van de Gunsmith Table (hoofdstuk 52). Geen vakjes om in te klikken (jouw wens: slepen op een echte tafel):
 * alle vakjes zijn er wel, maar onzichtbaar, alleen om de inhoud met de server te delen (het pistool op de tafel en je
 * inventaris). Wat je sleept, gaat als "knop van een menu" naar de server:
 * <ul>
 *   <li>{@link #TAKE_GUN}: het pistool meenemen;</li>
 *   <li>{@link #INSTALL} + vakje: dat onderdeel uit je inventaris in het pistool;</li>
 *   <li>{@link #REMOVE} + onderdeel: dat onderdeel uit het pistool;</li>
 *   <li>{@link #PUT_GUN} + vakje: dat pistool uit je inventaris op de tafel.</li>
 * </ul>
 */
public class GunsmithMenu extends AbstractContainerMenu {
    public static final int TAKE_GUN = 1;
    public static final int INSTALL = 100;
    public static final int REMOVE = 200;
    public static final int PUT_GUN = 300;

    private final Container items;
    private final @Nullable GunsmithTableBlockEntity table;

    public GunsmithMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(containerId, inventory, new SimpleContainer(GunsmithTableBlockEntity.SIZE), null);
    }

    public GunsmithMenu(int containerId, Inventory inventory, Container items, @Nullable GunsmithTableBlockEntity table) {
        super(ModMenus.GUNSMITH_TABLE.get(), containerId);
        checkContainerSize(items, GunsmithTableBlockEntity.SIZE);
        this.items = items;
        this.table = table;
        items.startOpen(inventory.player);
        addSlot(new HiddenSlot(items, 0));
        // Je inventaris (36 vakjes), ook onzichtbaar: zo blijft hij gelijk met de server terwijl je sleept.
        for (int slot = 0; slot < 36; slot++) {
            addSlot(new HiddenSlot(inventory, slot));
        }
    }

    /** Een vakje dat niet getekend wordt en waar je niet in kunt klikken. */
    private static final class HiddenSlot extends Slot {
        HiddenSlot(Container container, int index) {
            super(container, index, -1000, -1000);
        }

        @Override
        public boolean isActive() {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    /** Het pistool op de tafel (in jouw spel een kopie). */
    public ItemStack gun() {
        return items.getItem(0);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (table == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id == TAKE_GUN) {
            return table.takeGun(serverPlayer);
        }
        if (id >= PUT_GUN) {
            return table.putGun(serverPlayer, id - PUT_GUN);
        }
        if (id >= REMOVE) {
            int part = id - REMOVE;
            return part < GunPart.values().length && table.remove(serverPlayer, GunPart.values()[part]);
        }
        if (id >= INSTALL) {
            return table.install(serverPlayer, id - INSTALL);
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return items.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        items.stopOpen(player);
    }
}
