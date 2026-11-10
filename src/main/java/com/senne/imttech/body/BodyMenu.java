package com.senne.imttech.body;

import com.senne.imttech.backpack.BackpackRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.payload.AdvancedOpenScreenPayload;

/**
 * Een lichaam doorzoeken (hoofdstuk 72, stap 4): bovenaan alles wat erin zit, in 6 rijen van 9, eronder je inventaris
 * (zoals een dubbele kist). Iedereen mag eruit halen; erin leggen kan niet (het is een lichaam, geen kist). De eigenaar
 * heeft rechtsboven de knop "Everything back" (PlayerBodies.startRestore).
 * <pre>
 *  rij 0   harnas (hoofd, borst, benen, voeten), andere hand, holster, granaatpouch, magazijnpouch, geweer op je rug
 *  rij 1-3 je inventaris
 *  rij 4   je werkbalk
 *  rij 5   geweer voor je borst, en wat andere mods lieten vallen
 * </pre>
 */
public class BodyMenu extends AbstractContainerMenu {
    public static final int TOP = 18;

    private final Container container;
    private final PlayerBodyEntity body;
    private final boolean owner;

    /** Op de client: of jij de eigenaar bent komt van de server mee. */
    public BodyMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(containerId, inventory, new SimpleContainer(PlayerBodyEntity.SIZE), null, extra.readBoolean());
    }

    public BodyMenu(int containerId, Inventory inventory, Container container, PlayerBodyEntity body, boolean owner) {
        super(BackpackRegistry.BODY_MENU.get(), containerId);
        this.container = container;
        this.body = body;
        this.owner = owner;
        container.startOpen(inventory.player);
        // Rij 0: het harnas (hoofd eerst), de andere hand, de holster, de pouches en het geweer op je rug.
        int[] top = {PlayerBodyEntity.ARMOR + 3, PlayerBodyEntity.ARMOR + 2, PlayerBodyEntity.ARMOR + 1, PlayerBodyEntity.ARMOR,
                PlayerBodyEntity.OFFHAND, PlayerBodyEntity.GEAR, PlayerBodyEntity.GEAR + 1, PlayerBodyEntity.GEAR + 2, PlayerBodyEntity.GEAR + 3};
        for (int i = 0; i < 9; i++) {
            addSlot(new LootSlot(container, top[i], 8 + i * 18, TOP));
        }
        // Rij 1-3: de inventaris; rij 4: de werkbalk.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new LootSlot(container, 9 + row * 9 + column, 8 + column * 18, TOP + (1 + row) * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new LootSlot(container, column, 8 + column * 18, TOP + 4 * 18));
        }
        // Rij 5: het geweer voor je borst en wat andere mods lieten vallen.
        addSlot(new LootSlot(container, PlayerBodyEntity.GEAR + 4, 8, TOP + 5 * 18));
        for (int i = 0; i < 8; i++) {
            addSlot(new LootSlot(container, PlayerBodyEntity.EXTRA + i, 8 + (1 + i) * 18, TOP + 5 * 18));
        }
        int inventoryY = TOP + 6 * 18 + 13;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, inventoryY + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, inventoryY + 58));
        }
    }

    /** Het scherm openen (rechtsklik op een lichaam). */
    public static void open(ServerPlayer player, PlayerBodyEntity body) {
        boolean owner = body.isOwner(player);
        SimpleMenuProvider provider = new SimpleMenuProvider((id, inventory, p) -> new BodyMenu(id, inventory, body.loot(), body, owner),
                Component.translatable("body.imttech.title", body.ownerName()));
        if (player.connection != null && player.connection.hasChannel(AdvancedOpenScreenPayload.TYPE)) {
            player.openMenu(provider, buf -> buf.writeBoolean(owner));
        } else {
            player.openMenu(provider);
        }
    }

    /** Ben jij de eigenaar (dan staat de knop "Everything back" er)? */
    public boolean owner() {
        return owner;
    }

    /** Het lichaam (op de server; null in jouw spel). */
    public PlayerBodyEntity body() {
        return body;
    }

    /** Een vakje van het lichaam: eruit halen mag, erin leggen niet. */
    private static final class LootSlot extends Slot {
        LootSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index >= PlayerBodyEntity.SIZE) {
            return ItemStack.EMPTY; // uit je inventaris kan niets in het lichaam
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (!moveItemStackTo(stack, PlayerBodyEntity.SIZE, slots.size(), true)) {
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
        return body == null || (body.isAlive() && !body.isRemoved() && player.distanceToSqr(body) <= 36);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }
}
