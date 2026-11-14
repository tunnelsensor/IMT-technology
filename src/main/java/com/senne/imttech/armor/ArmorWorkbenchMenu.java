package com.senne.imttech.armor;

import com.senne.imttech.backpack.BackpackDyeRecipe;
import com.senne.imttech.backpack.BackpackRegistry;
import com.senne.imttech.guns.GunsmithRecipe;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * Het scherm van de Armor Workbench (hoofdstuk 111). Drie tabbladen (jouw keuzes):
 * <ul>
 *   <li><b>Vests</b> en <b>Plates</b>: tegels met wat het kost; Make haalt de materialen uit je inventaris en geeft je het
 *       vest of de plate (zoals Make parts van de Gunsmith Table). Vesten en plates maak je alleen hier, niet meer in de
 *       crafting table.</li>
 *   <li>Op <b>Plates</b> ook <b>herstellen</b> (jouw keuze, in plaats van het aambeeld): leg een plate in het vakje en Repair
 *       neemt zijn eigen materiaal uit je inventaris, elk stuk een kwart, tot hij heel is.</li>
 *   <li><b>Customize</b>: leg je vest in het vakje. De <b>tekst op je rug</b> en zijn kleur pas je alleen hier aan (jouw wens),
 *       gratis. <b>Verven</b> (jouw keuze, in plaats van de crafting table): klik een kleur en er gaat een kleurstof van die
 *       kleur uit je inventaris (op een nieuw vest wordt het die kleur, daarna mengt het zoals leer); <b>Camo</b> kost een
 *       bruine, een lichtgroene en een groene.</li>
 * </ul>
 * De werkbank bewaart niets: wat je in de twee vakjes legt, krijg je terug als je het scherm sluit (zoals een crafting
 * table). Een vakje is alleen te zien (en te vullen) op zijn eigen tabblad.
 */
public class ArmorWorkbenchMenu extends AbstractContainerMenu {
    public static final int TAB_VESTS = 0;
    public static final int TAB_PLATES = 1;
    public static final int TAB_CUSTOMIZE = 2;

    /** De knoppen (clickMenuButton): een tabblad, herstellen, camo, verven met kleur {@code DYE + nummer}, recept {@code MAKE + nummer}. */
    public static final int TAB = 0;
    public static final int REPAIR = 10;
    public static final int CAMO = 11;
    public static final int DYE = 20;
    public static final int MAKE = 1000;

    /** De twee vakjes: het vest dat je aanpast, en de plate die je herstelt. */
    public static final int VEST_SLOT = 0;
    public static final int PLATE_SLOT = 1;

    /** Waar alles staat (pixels in het scherm). */
    public static final int WIDTH = 230;
    public static final int VEST_X = 12;
    public static final int VEST_Y = 34;
    public static final int PLATE_X = 12;
    public static final int PLATE_Y = 92;
    public static final int INVENTORY_X = 35;
    public static final int INVENTORY_Y = 140;
    public static final int HEIGHT = INVENTORY_Y + 82;

    /** Zoveel van de duurzaamheid (van de 1000 stapjes) herstelt één stuk materiaal: een kwart, zoals een aambeeld. */
    public static final int REPAIR_PER_MATERIAL = PlateItem.UNITS / 4;

    private final Container items = new SimpleContainer(2) {
        @Override
        public void setChanged() {
            super.setChanged();
            slotsChanged(this);
        }
    };
    private final ContainerLevelAccess access;
    private final Player player;
    private int tab = TAB_VESTS;

    /** Op de client. */
    public ArmorWorkbenchMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(containerId, inventory, ContainerLevelAccess.NULL);
    }

    public ArmorWorkbenchMenu(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(ArmorRegistry.ARMOR_WORKBENCH_MENU.get(), containerId);
        this.access = access;
        this.player = inventory.player;
        addSlot(new TabSlot(items, VEST_SLOT, VEST_X, VEST_Y, TAB_CUSTOMIZE));
        addSlot(new TabSlot(items, PLATE_SLOT, PLATE_X, PLATE_Y, TAB_PLATES));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, INVENTORY_Y + 58));
        }
    }

    public int tab() {
        return tab;
    }

    /** In jouw spel: het tabblad meteen wisselen (de knop gaat ook naar de server, zie clickMenuButton). */
    public void setTab(int tab) {
        this.tab = Math.max(TAB_VESTS, Math.min(TAB_CUSTOMIZE, tab));
    }

    /** Het vest in het vakje van Customize (een kopie), of leeg. */
    public ItemStack vest() {
        return items.getItem(VEST_SLOT).copy();
    }

    /** De plate in het vakje van Plates (een kopie), of leeg. */
    public ItemStack plate() {
        return items.getItem(PLATE_SLOT).copy();
    }

    /** Een vakje dat alleen op zijn eigen tabblad te zien is: het vest op Customize, de plate op Plates. */
    private final class TabSlot extends Slot {
        private final int on;

        TabSlot(Container container, int index, int x, int y, int on) {
            super(container, index, x, y);
            this.on = on;
        }

        @Override
        public boolean isActive() {
            return tab == on;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return tab == on && (on == TAB_CUSTOMIZE ? VestItem.isVest(stack) : PlateItem.tierOf(stack) != null);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    // ================================================================ De knoppen

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= TAB && id <= TAB + TAB_CUSTOMIZE) {
            setTab(id - TAB);
            return true;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id >= MAKE) {
            return make(serverPlayer, id - MAKE);
        }
        if (id == REPAIR) {
            return repair(serverPlayer);
        }
        if (id == CAMO) {
            return camo(serverPlayer);
        }
        if (id >= DYE && id < DYE + DyeColor.values().length) {
            return dye(serverPlayer, DyeColor.byId(id - DYE));
        }
        return false;
    }

    /** Het recept met dit nummer (zie ArmorRecipe.allArmor) maken, met materiaal uit je inventaris. */
    public boolean make(ServerPlayer player, int recipeIndex) {
        RecipeHolder<ArmorRecipe> holder = ArmorRecipe.byIndexOf(player.serverLevel().getRecipeManager(), recipeIndex);
        if (holder == null) {
            return false;
        }
        ArmorRecipe recipe = holder.value();
        GunsmithRecipe.Carried carried = GunsmithRecipe.Carried.of(player.getInventory());
        List<ItemStack> leftovers = recipe.take(carried);
        if (leftovers == null) {
            tell(player, Component.translatable("workbench.imttech.armor.missing"));
            return false;
        }
        player.getInventory().setChanged();
        ItemStack made = recipe.assemble(carried, player.registryAccess());
        // Zoals de crafting table: het telt in je statistieken ("gemaakt") en voor advancements.
        made.onCraftedBy(player.level(), player, made.getCount());
        player.triggerRecipeCrafted(holder, carried.stacks());
        player.getInventory().placeItemBackInInventory(made);
        for (ItemStack leftover : leftovers) {
            player.getInventory().placeItemBackInInventory(leftover);
        }
        sound(player, SoundEvents.ARMOR_EQUIP_LEATHER.value(), 0.8f);
        broadcastChanges();
        syncOffhand(player);
        return true;
    }

    /**
     * De plate in het vakje herstellen met zijn eigen materiaal: elk stuk een kwart, zoveel als nodig is (en als je hebt).
     * Ook een gebarsten plate (die houdt daarna weer tegen).
     */
    public boolean repair(ServerPlayer player) {
        ItemStack plate = items.getItem(PLATE_SLOT);
        PlateTier tier = PlateItem.tierOf(plate);
        if (tier == null || !plate.isDamaged()) {
            tell(player, Component.translatable(tier == null ? "workbench.imttech.armor.no_plate" : "workbench.imttech.armor.whole"));
            return false;
        }
        int needed = repairCost(plate);
        int used = 0;
        Inventory inventory = player.getInventory();
        for (int pass = 0; pass < 2 && used < needed; pass++) {
            List<ItemStack> stacks = pass == 0 ? inventory.items : inventory.offhand;
            for (ItemStack stack : stacks) {
                if (used >= needed) {
                    break;
                }
                if (stack.is(tier.repairItem())) {
                    int take = Math.min(needed - used, stack.getCount());
                    stack.shrink(take);
                    used += take;
                }
            }
        }
        if (used == 0) {
            tell(player, Component.translatable("workbench.imttech.armor.need", tier.repairItem().getDescription()));
            return false;
        }
        ItemStack repaired = plate.copy();
        repaired.setDamageValue(Math.max(0, repaired.getDamageValue() - used * REPAIR_PER_MATERIAL));
        items.setItem(PLATE_SLOT, repaired);
        inventory.setChanged();
        sound(player, SoundEvents.ANVIL_USE, 0.4f);
        broadcastChanges();
        syncOffhand(player);
        return true;
    }

    /** Zoveel stukken materiaal maken deze plate helemaal heel (0 als hij al heel is). */
    public static int repairCost(ItemStack plate) {
        return PlateItem.tierOf(plate) == null ? 0 : (plate.getDamageValue() + REPAIR_PER_MATERIAL - 1) / REPAIR_PER_MATERIAL;
    }

    /**
     * Het vest in het vakje verven met een kleurstof van deze kleur uit je inventaris (jouw keuze: verven op de werkbank).
     * Op een nieuw vest (of camo) wordt het precies die kleur; daarna mengt elke kleurstof erbij, zoals leer.
     */
    public boolean dye(ServerPlayer player, DyeColor colour) {
        ItemStack vest = items.getItem(VEST_SLOT);
        if (!VestItem.isVest(vest)) {
            tell(player, Component.translatable("workbench.imttech.armor.no_vest"));
            return false;
        }
        ItemStack dye = findDye(player.getInventory(), colour);
        if (dye.isEmpty()) {
            tell(player, Component.translatable("workbench.imttech.armor.need", DyeItem.byColor(colour).getDescription()));
            return false;
        }
        dye.shrink(1);
        ItemStack out = vest.copy();
        // Zonder kleur (of op camo) mengt de kleurstof met niets: dan is het precies die kleur.
        DyedItemColor current = VestItem.camo(out) ? null : out.get(DataComponents.DYED_COLOR);
        int rgb = BackpackDyeRecipe.mix(current, List.of(DyeItem.byColor(colour)));
        out.remove(BackpackRegistry.CAMO.get());
        out.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb, true));
        items.setItem(VEST_SLOT, out);
        player.getInventory().setChanged();
        sound(player, SoundEvents.DYE_USE, 0.8f);
        broadcastChanges();
        syncOffhand(player);
        return true;
    }

    /** Camo (jouw keuze: zoals de rugzakken): kost een bruine, een lichtgroene en een groene kleurstof. */
    public boolean camo(ServerPlayer player) {
        ItemStack vest = items.getItem(VEST_SLOT);
        if (!VestItem.isVest(vest)) {
            tell(player, Component.translatable("workbench.imttech.armor.no_vest"));
            return false;
        }
        Inventory inventory = player.getInventory();
        ItemStack brown = findDye(inventory, DyeColor.BROWN);
        ItemStack lime = findDye(inventory, DyeColor.LIME);
        ItemStack green = findDye(inventory, DyeColor.GREEN);
        if (brown.isEmpty() || lime.isEmpty() || green.isEmpty()) {
            tell(player, Component.translatable("workbench.imttech.armor.camo_needs"));
            return false;
        }
        brown.shrink(1);
        lime.shrink(1);
        green.shrink(1);
        ItemStack out = vest.copy();
        out.remove(DataComponents.DYED_COLOR);
        out.set(BackpackRegistry.CAMO.get(), true);
        items.setItem(VEST_SLOT, out);
        inventory.setChanged();
        sound(player, SoundEvents.DYE_USE, 0.8f);
        broadcastChanges();
        syncOffhand(player);
        return true;
    }

    /**
     * Je tweede hand heeft geen vakje in dit scherm, dus wat Make, Repair, een kleur of Camo daaruit haalt, kreeg jouw spel
     * pas te zien als je het scherm sloot (de knoppen en de kosten bleven staan alsof je het nog had). Daarom sturen we hem
     * zelf (-2: een vakje van je inventaris, ook als er een ander scherm open is).
     */
    private static void syncOffhand(ServerPlayer player) {
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, Inventory.SLOT_OFFHAND, player.getOffhandItem().copy()));
    }

    /** Van VestTextPayload: de tekst op de rug van het vest in het vakje, en zijn kleur (gratis). */
    public void setText(String text, int colour) {
        ItemStack vest = items.getItem(VEST_SLOT);
        if (player.level().isClientSide || !VestItem.isVest(vest)) {
            return;
        }
        VestData data = VestItem.data(vest);
        VestData changed = data.withText(text, colour);
        if (!changed.equals(data)) {
            items.setItem(VEST_SLOT, VestItem.with(vest, changed));
            broadcastChanges();
        }
    }

    /** Een kleurstof van deze kleur in je inventaris (de echte stapel), of leeg. */
    public static ItemStack findDye(Inventory inventory, DyeColor colour) {
        for (List<ItemStack> stacks : List.of(inventory.items, inventory.offhand)) {
            for (ItemStack stack : stacks) {
                if (stack.getItem() instanceof DyeItem dye && dye.getDyeColor() == colour) {
                    return stack;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    // ================================================================ Sluiten, shift-klik

    @Override
    public void removed(Player player) {
        super.removed(player);
        access.execute((level, pos) -> clearContainer(player, items));
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ArmorRegistry.ARMOR_WORKBENCH.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index <= PLATE_SLOT) {
            if (!moveItemStackTo(stack, PLATE_SLOT + 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, VEST_SLOT, PLATE_SLOT + 1, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    private static void tell(ServerPlayer player, Component text) {
        player.displayClientMessage(text.copy().withStyle(ChatFormatting.RED), true);
    }

    private static void sound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume) {
        player.serverLevel().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.BLOCKS, volume, 1.0f);
    }
}
