package com.senne.starktech.robot;

import com.senne.starktech.bot.BotBoard;
import com.senne.starktech.bot.CircuitBoardItem;
import com.senne.starktech.registry.ModBlockEntities;
import com.senne.starktech.registry.ModDataComponents;
import com.senne.starktech.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De binnenkant van de Robot Assembler (hoofdstuk 21): zes vakjes. Vier voor de onderdelen (hoofd, lijf,
 * armen, benen, in de volgorde van RobotSlot), één voor de printplaat, en één voor de robot zelf.
 *
 * <ul>
 *   <li><b>Assemble</b>: liggen alle vier de onderdelen en een printplaat erin, en is het robotvakje leeg,
 *       dan maken we er een Guard Robot-item van (elk onderdeel en de printplaat gaan er één af).</li>
 *   <li><b>Take apart</b>: ligt er een robot in het robotvakje en zijn de andere vakjes leeg, dan komen
 *       zijn onderdelen en printplaat (met alles wat erop gesoldeerd is) terug in hun vakjes.</li>
 * </ul>
 * De regels staan hier als losse methodes ({@link #problem}), zodat het scherm dezelfde uitleg geeft als
 * wat de server doet.
 */
public class RobotAssemblerBlockEntity extends BlockEntity implements MenuProvider {
    /** De vakjes: 0-3 de onderdelen (RobotSlot.ordinal()), dan de printplaat en de robot. */
    public static final int BOARD = 4;
    public static final int ROBOT = 5;
    public static final int SIZE = 6;

    private final SimpleContainer items = new SimpleContainer(SIZE) {
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return fits(slot, stack);
        }

        @Override
        public void setChanged() {
            super.setChanged();
            RobotAssemblerBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return Container.stillValidBlockEntity(RobotAssemblerBlockEntity.this, player);
        }
    };

    public RobotAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ROBOT_ASSEMBLER.get(), pos, state);
    }

    public SimpleContainer items() {
        return items;
    }

    /** Mag dit in dat vakje? Een onderdeel alleen in zijn eigen vakje, een printplaat en een robot ook. */
    public static boolean fits(int slot, ItemStack stack) {
        if (slot < BOARD) {
            return stack.getItem() instanceof RobotPartItem part && part.slot().ordinal() == slot;
        }
        if (slot == BOARD) {
            return stack.getItem() instanceof CircuitBoardItem;
        }
        return slot == ROBOT && stack.getItem() instanceof GuardRobotItem;
    }

    // ================================================================ De regels

    /** Wat er ontbreekt om in elkaar te zetten ("head", "board" voor de printplaat...); leeg = het kan. */
    public static List<String> missing(Container items) {
        List<String> missing = new ArrayList<>();
        for (RobotSlot slot : RobotSlot.values()) {
            if (!fits(slot.ordinal(), items.getItem(slot.ordinal()))) {
                missing.add(slot.title().toLowerCase(Locale.ROOT));
            }
        }
        if (!fits(BOARD, items.getItem(BOARD))) {
            missing.add("board");
        }
        return missing;
    }

    /** Kan Assemble nu? */
    public static boolean canAssemble(Container items) {
        return items.getItem(ROBOT).isEmpty() && missing(items).isEmpty();
    }

    /** Kan Take apart nu? */
    public static boolean canTakeApart(Container items) {
        if (!fits(ROBOT, items.getItem(ROBOT))) {
            return false;
        }
        for (int i = 0; i < ROBOT; i++) {
            if (!items.getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Wat het scherm onder de knoppen zegt (Engels, zoals alles in het spel). */
    public static String problem(Container items) {
        if (!items.getItem(ROBOT).isEmpty()) {
            return canTakeApart(items) ? "Ready to take apart." : "Empty the part slots to take it apart.";
        }
        List<String> missing = missing(items);
        return missing.isEmpty() ? "Ready: press Assemble." : "Missing: " + String.join(", ", missing) + ".";
    }

    /** De onderdelen zoals ze nu in de vakjes liggen (een leeg vakje telt als Battle; zie ook {@link #shownSlots}). */
    public static RobotParts partsIn(Container items) {
        if (items.getItem(ROBOT).getItem() instanceof GuardRobotItem) {
            return GuardRobotItem.data(items.getItem(ROBOT)).robotParts();
        }
        RobotParts parts = RobotParts.DEFAULT;
        for (RobotSlot slot : RobotSlot.values()) {
            if (items.getItem(slot.ordinal()).getItem() instanceof RobotPartItem part) {
                parts = parts.with(slot, part.style());
            }
        }
        return parts;
    }

    /** Welke onderdelen er liggen (bit per RobotSlot), voor het voorbeeld; een robot heeft ze allemaal. */
    public static int shownSlots(Container items) {
        if (items.getItem(ROBOT).getItem() instanceof GuardRobotItem) {
            return 0b1111;
        }
        int mask = 0;
        for (RobotSlot slot : RobotSlot.values()) {
            if (fits(slot.ordinal(), items.getItem(slot.ordinal()))) {
                mask |= 1 << slot.ordinal();
            }
        }
        return mask;
    }

    // ================================================================ De knoppen

    /** Zet een robot in elkaar (zie de uitleg bovenaan). Lukte het? */
    public boolean assemble() {
        if (!canAssemble(items)) {
            return false;
        }
        RobotParts parts = partsIn(items);
        BotBoard board = CircuitBoardItem.board(items.getItem(BOARD));
        for (int i = 0; i <= BOARD; i++) {
            items.removeItem(i, 1);
        }
        items.setItem(ROBOT, GuardRobotItem.create(new RobotData(parts, board, "")));
        sound(1.2f);
        return true;
    }

    /** Haal de robot uit elkaar (zie de uitleg bovenaan). Lukte het? */
    public boolean takeApart() {
        if (!canTakeApart(items)) {
            return false;
        }
        RobotData data = GuardRobotItem.data(items.getItem(ROBOT));
        RobotParts parts = data.robotParts();
        for (RobotSlot slot : RobotSlot.values()) {
            items.setItem(slot.ordinal(), new ItemStack(ModItems.robotPart(slot, parts.get(slot))));
        }
        ItemStack board = new ItemStack(ModItems.ROBOT_CIRCUIT_BOARD.get());
        if (!data.board().equals(BotBoard.EMPTY)) {
            board.set(ModDataComponents.BOT_BOARD.get(), data.board());
        }
        items.setItem(BOARD, board);
        items.setItem(ROBOT, ItemStack.EMPTY);
        sound(0.8f);
        return true;
    }

    private void sound(float pitch) {
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.IRON_GOLEM_REPAIR, SoundSource.BLOCKS, 0.7f, pitch);
        }
    }

    // ================================================================ Het scherm

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.starktech.robot_assembler");
    }

    @Override
    public @Nullable AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new RobotAssemblerMenu(containerId, inventory, items, this);
    }

    // ================================================================ Opslaan

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        for (int i = 0; i < SIZE; i++) {
            list.set(i, items.getItem(i));
        }
        ContainerHelper.saveAllItems(tag, list, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        NonNullList<ItemStack> list = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, list, registries);
        for (int i = 0; i < SIZE; i++) {
            items.setItem(i, list.get(i));
        }
    }
}
