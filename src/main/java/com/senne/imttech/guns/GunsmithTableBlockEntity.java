package com.senne.imttech.guns;

import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van de Gunsmith Table (hoofdstuk 52): één vakje, het pistool dat op de tafel ligt. De regels:
 * <ul>
 *   <li><b>een onderdeel erin</b> ({@link #install}): sleep een onderdeel uit je inventaris op zijn plek. Het frame is
 *       de basis: ligt er nog geen pistool, dan maakt een frame er een. Een plek die al bezet is, blijft bezet (eerst
 *       het oude eruit).</li>
 *   <li><b>een onderdeel eruit</b> ({@link #remove}): sleep het van het pistool af, het komt terug in je inventaris met
 *       zijn conditie. Het frame kan er pas uit als de rest eruit is; dan is het pistool weg (een frame is geen
 *       pistool). Een magazijn en een patroon in de kamer komen ook mee terug als het pistool uit elkaar gaat.</li>
 *   <li><b>een pistool op de tafel</b> ({@link #putGun}) en <b>weer meenemen</b> ({@link #takeGun}).</li>
 * </ul>
 * Alles gebeurt op de server, via de knoppen van het menu (zie GunsmithMenu).
 */
public class GunsmithTableBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SIZE = 1;

    private final SimpleContainer items = new SimpleContainer(SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            GunsmithTableBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(GunsmithTableBlockEntity.this, player);
        }
    };

    public GunsmithTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GUNSMITH_TABLE.get(), pos, state);
    }

    public SimpleContainer items() {
        return items;
    }

    /** Het pistool op de tafel (leeg als er geen ligt). */
    public ItemStack gun() {
        return items.getItem(0);
    }

    // ================================================================ De regels

    /** Een onderdeel uit dit vakje van je inventaris in het pistool. */
    public boolean install(ServerPlayer player, int inventorySlot) {
        Inventory inventory = player.getInventory();
        if (inventorySlot < 0 || inventorySlot >= inventory.items.size()) {
            return false;
        }
        ItemStack stack = inventory.getItem(inventorySlot);
        GunPart part = GunPartItem.partOf(stack);
        if (part == null) {
            return false;
        }
        ItemStack gun = gun();
        if (gun.isEmpty()) {
            if (part != GunPart.FRAME) {
                tell(player, Component.translatable("gunsmith.imttech.frame_first"));
                return false;
            }
            gun = FirearmItem.withParts(FirearmType.GLOCK_19, GunParts.EMPTY.with(part, GunPartItem.stateOf(stack)));
            items.setItem(0, gun);
        } else {
            GunParts parts = FirearmItem.parts(gun);
            if (parts.has(part)) {
                tell(player, Component.translatable("gunsmith.imttech.occupied", part.text()));
                return false;
            }
            if (underSlide(part, parts)) {
                tell(player, Component.translatable("gunsmith.imttech.under_slide"));
                return false;
            }
            FirearmItem.setParts(gun, parts.with(part, GunPartItem.stateOf(stack)));
            items.setChanged();
        }
        stack.shrink(1);
        click(player);
        return true;
    }

    /**
     * De loop, de veren en de slagpin zitten onder de slede (hoofdstuk 54): zit de slede erop, dan kun je er niet bij,
     * net als bij een echte Glock. Eerst de slede eraf.
     */
    public static boolean underSlide(GunPart part, GunParts parts) {
        return (part == GunPart.BARREL || part == GunPart.SPRINGS || part == GunPart.STRIKER) && parts.has(GunPart.SLIDE);
    }

    /** Dit onderdeel uit het pistool, terug in je inventaris. */
    public boolean remove(ServerPlayer player, GunPart part) {
        ItemStack gun = gun();
        if (gun.isEmpty()) {
            return false;
        }
        GunParts parts = FirearmItem.parts(gun);
        PartState state = parts.get(part);
        if (state == null) {
            return false;
        }
        if (part == GunPart.FRAME && parts.parts().size() > 1) {
            tell(player, Component.translatable("gunsmith.imttech.frame_last"));
            return false;
        }
        if (underSlide(part, parts)) {
            tell(player, Component.translatable("gunsmith.imttech.under_slide"));
            return false;
        }
        GunParts rest = parts.without(part);
        Firearms.give(player, GunPartItem.of(part, state.material(), state.condition()));
        if (rest.parts().isEmpty()) {
            // Het laatste onderdeel (het frame) eruit: het pistool bestaat niet meer; wat erin zat, krijg je terug.
            ItemStack magazine = FirearmItem.magazine(gun);
            if (!magazine.isEmpty()) {
                Firearms.give(player, magazine);
            }
            if (FirearmItem.chambered(gun)) {
                Firearms.give(player, new ItemStack(com.senne.imttech.registry.ModItems.ROUND_9MM.get()));
            }
            items.setItem(0, ItemStack.EMPTY);
        } else {
            FirearmItem.setParts(gun, rest);
            items.setChanged();
        }
        click(player);
        return true;
    }

    /** Een pistool uit dit vakje van je inventaris op de tafel leggen (als die leeg is). */
    public boolean putGun(ServerPlayer player, int inventorySlot) {
        Inventory inventory = player.getInventory();
        if (!gun().isEmpty() || inventorySlot < 0 || inventorySlot >= inventory.items.size()) {
            return false;
        }
        ItemStack stack = inventory.getItem(inventorySlot);
        if (FirearmItem.typeOf(stack) == null) {
            return false;
        }
        items.setItem(0, stack.copy());
        inventory.setItem(inventorySlot, ItemStack.EMPTY);
        click(player);
        return true;
    }

    /** Het pistool van de tafel meenemen. */
    public boolean takeGun(ServerPlayer player) {
        ItemStack gun = gun();
        if (gun.isEmpty()) {
            return false;
        }
        items.setItem(0, ItemStack.EMPTY);
        Firearms.give(player, gun);
        click(player);
        return true;
    }

    private void click(ServerPlayer player) {
        if (level != null) {
            level.playSound(null, worldPosition, ModSounds.GLOCK_ROUND_LOAD.get(), SoundSource.BLOCKS, 0.6f, 1.1f);
        }
    }

    private static void tell(ServerPlayer player, Component text) {
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.RED), true);
    }

    // ================================================================ Het menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.imttech.gunsmith_table");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GunsmithMenu(containerId, inventory, items, this);
    }

    // ================================================================ Opslaan

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        list.set(0, items.getItem(0));
        ContainerHelper.saveAllItems(tag, list, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list, registries);
        items.setItem(0, list.get(0));
    }
}
