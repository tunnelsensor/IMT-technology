package com.senne.imttech.weapons;

import com.senne.imttech.guns.Firearms;
import com.senne.imttech.guns.GunsmithMenu;
import com.senne.imttech.guns.PartState;
import com.senne.imttech.guns.PartsTable;
import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModMenus;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van de Laser Workbench (hoofdstuk 55): één vakje, het lasergeweer dat op de tafel ligt. De regels,
 * zoals bij de Gunsmith Table:
 * <ul>
 *   <li><b>een onderdeel erin</b> ({@link #install}): sleep een onderdeel uit je inventaris op zijn plek. Het frame is
 *       de basis: ligt er nog geen geweer, dan maakt een frame er een (het frame zegt welk geweer het wordt). Een
 *       onderdeel van een ander geweer past niet. Een plek die al bezet is, blijft bezet (eerst het oude eruit).</li>
 *   <li><b>de kern</b>: een Nether Star in het vak "core" (jouw keuze: zichtbaar insteken, en er ook weer uit te halen).
 *       Zonder kern schiet het geweer niet.</li>
 *   <li><b>de sling</b>: een Gun Sling in het vak "sling", alleen bij de rifle, de sniper en de shotgun.</li>
 *   <li><b>een onderdeel eruit</b> ({@link #remove}): sleep het van het geweer af, het komt terug in je inventaris met
 *       zijn conditie (de kern en de sling als zichzelf). Het frame kan er pas uit als de rest eruit is; dan is het
 *       geweer weg (een frame is geen geweer), en de cel die erin zat krijg je terug.</li>
 *   <li><b>een geweer op de tafel</b> ({@link #putGun}) en <b>weer meenemen</b> ({@link #takeGun}).</li>
 * </ul>
 * Alles gebeurt op de server, via de knoppen van het menu (zie GunsmithMenu en PartsTable).
 */
public class LaserWorkbenchBlockEntity extends BlockEntity implements MenuProvider, PartsTable {
    public static final int SIZE = 1;

    private final SimpleContainer items = new SimpleContainer(SIZE) {
        @Override
        public void setChanged() {
            super.setChanged();
            LaserWorkbenchBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(LaserWorkbenchBlockEntity.this, player);
        }
    };

    public LaserWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LASER_WORKBENCH.get(), pos, state);
    }

    public SimpleContainer items() {
        return items;
    }

    @Override
    public ItemStack gun() {
        return items.getItem(0);
    }

    // ================================================================ De regels

    /** Welk vak dit item vult: een los onderdeel, een Nether Star (de kern) of een Gun Sling; anders null. */
    public static @Nullable LaserPart partOf(ItemStack stack) {
        if (stack.is(Items.NETHER_STAR)) {
            return LaserPart.CORE;
        }
        if (stack.getItem() instanceof GunSlingItem) {
            return LaserPart.SLING;
        }
        return LaserPartItem.partOf(stack);
    }

    @Override
    public boolean install(ServerPlayer player, int inventorySlot) {
        Inventory inventory = player.getInventory();
        if (inventorySlot < 0 || inventorySlot >= inventory.items.size()) {
            return false;
        }
        ItemStack stack = inventory.getItem(inventorySlot);
        LaserPart part = partOf(stack);
        if (part == null) {
            return false;
        }
        ItemStack gun = gun();
        if (gun.isEmpty()) {
            GunType type = LaserPartItem.gunOf(stack);
            if (part != LaserPart.FRAME || type == null) {
                tell(player, Component.translatable("laserbench.imttech.frame_first"));
                return false;
            }
            items.setItem(0, LaserGunItem.withParts(type, LaserParts.EMPTY.with(part, LaserPartItem.stateOf(stack))));
        } else {
            GunType type = LaserGunItem.typeOf(gun);
            GunType partGun = LaserPartItem.gunOf(stack);
            if (type == null) {
                return false;
            }
            if (partGun != null && partGun != type) {
                tell(player, Component.translatable("laserbench.imttech.other_gun", Component.translatable("item.imttech." + partGun.id)));
                return false;
            }
            if (!part.fitsOn(type)) {
                tell(player, Component.translatable("laserbench.imttech.no_sling_on_pistol"));
                return false;
            }
            LaserParts parts = LaserGunItem.parts(gun);
            if (parts.has(part)) {
                tell(player, Component.translatable("laserbench.imttech.occupied", part.text()));
                return false;
            }
            PartState state = part.wears() ? LaserPartItem.stateOf(stack) : LaserParts.extra();
            LaserGunItem.setParts(gun, parts.with(part, state));
            items.setChanged();
        }
        stack.shrink(1);
        click(player);
        return true;
    }

    @Override
    public boolean remove(ServerPlayer player, int partIndex) {
        return partIndex >= 0 && partIndex < LaserPart.values().length && remove(player, LaserPart.values()[partIndex]);
    }

    /** Dit onderdeel uit het geweer, terug in je inventaris. */
    public boolean remove(ServerPlayer player, LaserPart part) {
        ItemStack gun = gun();
        GunType type = LaserGunItem.typeOf(gun);
        if (type == null) {
            return false;
        }
        LaserParts parts = LaserGunItem.parts(gun);
        PartState state = parts.get(part);
        if (state == null) {
            return false;
        }
        if (part == LaserPart.FRAME && parts.parts().size() > 1) {
            tell(player, Component.translatable("laserbench.imttech.frame_last"));
            return false;
        }
        LaserParts rest = parts.without(part);
        Firearms.give(player, switch (part) {
            case CORE -> new ItemStack(Items.NETHER_STAR);
            case SLING -> new ItemStack(ModItems.GUN_SLING.get());
            default -> LaserPartItem.of(type, part, state.material(), state.condition());
        });
        if (rest.parts().isEmpty()) {
            // Het laatste onderdeel (het frame) eruit: het geweer bestaat niet meer; de cel die erin zat krijg je terug.
            float cell = LaserGunItem.cell(gun);
            if (cell >= 0) {
                Firearms.give(player, EnergyCellItem.withCharge(cell));
            }
            items.setItem(0, ItemStack.EMPTY);
        } else {
            LaserGunItem.setParts(gun, rest);
            items.setChanged();
        }
        click(player);
        return true;
    }

    @Override
    public boolean putGun(ServerPlayer player, int inventorySlot) {
        Inventory inventory = player.getInventory();
        if (!gun().isEmpty() || inventorySlot < 0 || inventorySlot >= inventory.items.size()) {
            return false;
        }
        ItemStack stack = inventory.getItem(inventorySlot);
        if (LaserGunItem.typeOf(stack) == null) {
            return false;
        }
        items.setItem(0, stack.copy());
        inventory.setItem(inventorySlot, ItemStack.EMPTY);
        click(player);
        return true;
    }

    @Override
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
            level.playSound(null, worldPosition, ModSounds.LASER_GUN_CELL_IN.get(), SoundSource.BLOCKS, 0.5f, 1.2f);
        }
    }

    private static void tell(ServerPlayer player, Component text) {
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.RED), true);
    }

    // ================================================================ Het menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.imttech.laser_workbench");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new GunsmithMenu(containerId, inventory, items, this, ModMenus.LASER_WORKBENCH.get());
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
