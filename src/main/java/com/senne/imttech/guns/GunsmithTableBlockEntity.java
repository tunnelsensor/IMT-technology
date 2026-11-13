package com.senne.imttech.guns;

import com.senne.imttech.registry.ModBlockEntities;
import com.senne.imttech.registry.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
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
 *   <li><b>een onderdeel maken</b> ({@link #make}, hoofdstuk 78): op het tabblad "Make parts". Alle onderdelen (van de
 *       Glock en van de lasergeweren), de Gun Sling en het magazijn maak je alleen hier, niet in de crafting table (jouw
 *       wens). Het materiaal komt uit je inventaris (zie {@link GunsmithRecipe}).</li>
 * </ul>
 * Alles gebeurt op de server, via de knoppen van het menu (zie GunsmithMenu).
 */
public class GunsmithTableBlockEntity extends BlockEntity implements MenuProvider, PartsTable {
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

    /**
     * Welke plek dit item vult: een los onderdeel van een vuurwapen, (hoofdstuk 103) een Gun Sling of een attachment;
     * anders null.
     */
    public static @Nullable GunSlot slotOf(ItemStack stack) {
        if (stack.getItem() instanceof com.senne.imttech.weapons.GunSlingItem) {
            return GunSlot.SLING;
        }
        Attachment attachment = AttachmentItem.of(stack);
        if (attachment != null) {
            return GunSlot.of(attachment.slot);
        }
        GunPart part = GunPartItem.partOf(stack);
        return part == null ? null : GunSlot.of(part);
    }

    /** Een onderdeel (of een sling) uit dit vakje van je inventaris in het wapen. */
    public boolean install(ServerPlayer player, int inventorySlot) {
        Inventory inventory = player.getInventory();
        if (inventorySlot < 0 || inventorySlot >= inventory.items.size()) {
            return false;
        }
        ItemStack stack = inventory.getItem(inventorySlot);
        GunSlot slot = slotOf(stack);
        if (slot == null) {
            return false;
        }
        ItemStack gun = gun();
        if (gun.isEmpty()) {
            // Het frame is de basis, en het zegt welk wapen het wordt (hoofdstuk 103).
            FirearmType type = GunPartItem.typeOf(stack);
            if (slot != GunSlot.FRAME || type == null) {
                tell(player, Component.translatable("gunsmith.imttech.frame_first"));
                return false;
            }
            gun = FirearmItem.withParts(type, GunParts.EMPTY.with(GunPart.FRAME, GunPartItem.stateOf(stack)));
            items.setItem(0, gun);
        } else {
            FirearmType type = FirearmItem.typeOf(gun);
            if (type == null) {
                return false;
            }
            if (slot.attachment != null) {
                // Hoofdstuk 103, stap 5: een attachment (jouw keuze: alleen hier erop en eraf).
                Attachment attachment = AttachmentItem.of(stack);
                if (attachment == null || !attachment.fits(type)) {
                    tell(player, Component.translatable("gunsmith.imttech.no_fit", stack.getHoverName(), type.text()));
                    return false;
                }
                if (FirearmItem.attachment(gun, slot.attachment) != null) {
                    tell(player, Component.translatable("gunsmith.imttech.occupied", slot.text(type)));
                    return false;
                }
                GunPart carrier = slot.attachment.carrier(type);
                if (!FirearmItem.parts(gun).has(carrier)) {
                    // Een kijker staat op de slede van een pistool: zonder slede kan hij nergens op.
                    tell(player, Component.translatable("gunsmith.imttech.needs_part", carrier.text(type)));
                    return false;
                }
                FirearmItem.setAttachment(gun, attachment);
                items.setChanged();
            } else if (slot == GunSlot.SLING) {
                if (!type.longGun) {
                    tell(player, Component.translatable("gunsmith.imttech.no_sling", type.text()));
                    return false;
                }
                if (FirearmItem.hasSling(gun)) {
                    tell(player, Component.translatable("gunsmith.imttech.occupied", slot.text(type)));
                    return false;
                }
                FirearmItem.setSling(gun, true);
                items.setChanged();
            } else {
                GunPart part = slot.part;
                FirearmType partType = GunPartItem.typeOf(stack);
                if (partType != type) {
                    tell(player, Component.translatable("gunsmith.imttech.other_gun", partType == null ? "" : partType.text()));
                    return false;
                }
                GunParts parts = FirearmItem.parts(gun);
                if (parts.has(part)) {
                    tell(player, Component.translatable("gunsmith.imttech.occupied", part.text(type)));
                    return false;
                }
                if (underSlide(type, part, parts)) {
                    tell(player, Component.translatable("gunsmith.imttech.under_slide"));
                    return false;
                }
                FirearmItem.setParts(gun, parts.with(part, GunPartItem.stateOf(stack)));
                items.setChanged();
            }
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
        return underSlide(FirearmType.GLOCK_19, part, parts);
    }

    /** Hetzelfde voor elk wapen (hoofdstuk 103): bij een lang wapen steekt de loop gewoon uit, de veer en de slagpin niet. */
    public static boolean underSlide(FirearmType type, GunPart part, GunParts parts) {
        return part.underSlide(type) && parts.has(GunPart.SLIDE);
    }

    /**
     * Het attachment dat op dit onderdeel van het wapen staat (hoofdstuk 103, stap 5), of null. Zolang het er staat,
     * krijg je het onderdeel niet uit het wapen: eerst het attachment eraf.
     */
    public static @Nullable Attachment sittingOn(ItemStack gun, GunPart part) {
        FirearmType type = FirearmItem.typeOf(gun);
        if (type == null) {
            return null;
        }
        for (Attachment attachment : FirearmItem.attachments(gun).list()) {
            if (attachment.slot.carrier(type) == part) {
                return attachment;
            }
        }
        return null;
    }

    /** Het menu zegt een nummer (zie PartsTable): de plek in de volgorde van GunSlot (de zes onderdelen, dan de sling). */
    @Override
    public boolean remove(ServerPlayer player, int partIndex) {
        GunSlot slot = GunSlot.byId(partIndex);
        if (slot == null) {
            return false;
        }
        if (slot.attachment != null) {
            return removeAttachment(player, slot.attachment);
        }
        return slot.part == null ? removeSling(player) : remove(player, slot.part);
    }

    /** Het attachment op deze plek van het wapen af, terug in je inventaris. */
    public boolean removeAttachment(ServerPlayer player, AttachmentSlot slot) {
        ItemStack gun = gun();
        if (gun.isEmpty()) {
            return false;
        }
        Attachment was = FirearmItem.removeAttachment(gun, slot);
        if (was == null) {
            return false;
        }
        // Stap 6: zonder zijn verlengbuis passen er minder patronen in de buis: wat te veel is, krijg je terug.
        FirearmType type = FirearmItem.typeOf(gun);
        int over = FirearmItem.roundsInMagazine(gun) - FirearmItem.capacity(gun);
        if (type != null && type.tube() && over > 0) {
            FirearmItem.setRoundsInMagazine(gun, FirearmItem.capacity(gun));
            Firearms.give(player, new ItemStack(type.caliber.round(), over));
        }
        items.setChanged();
        Firearms.give(player, new ItemStack(com.senne.imttech.registry.ModItems.attachment(was)));
        click(player);
        return true;
    }

    /** De sling van het wapen af, terug in je inventaris. */
    public boolean removeSling(ServerPlayer player) {
        ItemStack gun = gun();
        if (gun.isEmpty() || !FirearmItem.hasSling(gun)) {
            return false;
        }
        FirearmItem.setSling(gun, false);
        items.setChanged();
        Firearms.give(player, new ItemStack(com.senne.imttech.registry.ModItems.GUN_SLING.get()));
        click(player);
        return true;
    }

    /** Dit onderdeel uit het wapen, terug in je inventaris. */
    public boolean remove(ServerPlayer player, GunPart part) {
        ItemStack gun = gun();
        FirearmType type = FirearmItem.typeOf(gun);
        if (type == null) {
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
        if (underSlide(type, part, parts)) {
            tell(player, Component.translatable("gunsmith.imttech.under_slide"));
            return false;
        }
        Attachment on = sittingOn(gun, part);
        if (on != null) {
            tell(player, Component.translatable("gunsmith.imttech.attachment_first", on.text()));
            return false;
        }
        GunParts rest = parts.without(part);
        Firearms.give(player, GunPartItem.of(type, part, state.material(), state.condition()));
        if (rest.parts().isEmpty()) {
            // Het laatste onderdeel (het frame) eruit: het wapen bestaat niet meer; wat erin zat, krijg je terug.
            ItemStack magazine = FirearmItem.magazine(gun);
            if (!magazine.isEmpty()) {
                Firearms.give(player, magazine);
            } else if (type.tube() && FirearmItem.roundsInMagazine(gun) > 0) {
                Firearms.give(player, new ItemStack(type.caliber.round(), FirearmItem.roundsInMagazine(gun)));
            }
            if (FirearmItem.chambered(gun)) {
                Firearms.give(player, new ItemStack(type.caliber.round()));
            }
            if (FirearmItem.hasSling(gun)) {
                Firearms.give(player, new ItemStack(com.senne.imttech.registry.ModItems.GUN_SLING.get()));
            }
            for (Attachment attachment : FirearmItem.attachments(gun).list()) {
                Firearms.give(player, new ItemStack(com.senne.imttech.registry.ModItems.attachment(attachment)));
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

    /**
     * Hoofdstuk 78: het recept met dit nummer maken (zie {@link GunsmithRecipe#all}). Heb je niet alles bij je, dan
     * gebeurt er niets (en zegt de tafel het). Ook in creative betaal je: zoals aan de crafting table.
     */
    @Override
    public boolean make(ServerPlayer player, int recipeIndex) {
        if (level == null) {
            return false;
        }
        RecipeHolder<GunsmithRecipe> holder = GunsmithRecipe.byIndex(level.getRecipeManager(), recipeIndex);
        if (holder == null) {
            return false;
        }
        GunsmithRecipe recipe = holder.value();
        GunsmithRecipe.Carried carried = GunsmithRecipe.Carried.of(player.getInventory());
        List<ItemStack> leftovers = recipe.take(carried);
        if (leftovers == null) {
            tell(player, Component.translatable("gunsmith.imttech.missing"));
            return false;
        }
        player.getInventory().setChanged();
        ItemStack made = recipe.assemble(carried, level.registryAccess());
        // Zoals de crafting table: het telt in je statistieken ("gemaakt") en voor advancements.
        made.onCraftedBy(level, player, made.getCount());
        player.triggerRecipeCrafted(holder, carried.stacks());
        Firearms.give(player, made);
        for (ItemStack leftover : leftovers) {
            Firearms.give(player, leftover);
        }
        level.playSound(null, worldPosition, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 0.7f, 1.1f);
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
        return new GunsmithMenu(containerId, inventory, items, this, com.senne.imttech.registry.ModMenus.GUNSMITH_TABLE.get());
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
