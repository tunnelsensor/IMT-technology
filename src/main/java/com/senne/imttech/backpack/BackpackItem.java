package com.senne.imttech.backpack;

import com.senne.imttech.config.ImtConfig;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Een militaire rugzak (hoofdstuk 72, jouw wens: "backpacks zoals Sophisticated Backpacks, maar minder OP, militaire
 * tactical backpacks"). Drie maten (jouw keuze):
 * <ul>
 *   <li><b>Assault Pack</b>: klein, 18 vakjes (2 rijen), 2 plekken voor MOLLE-pouches;</li>
 *   <li><b>Patrol Pack</b>: 27 vakjes (3 rijen, zo groot als een kist), 3 MOLLE-plekken;</li>
 *   <li><b>Rucksack</b>: 36 vakjes (4 rijen) op een titanium frame, 4 MOLLE-plekken.</li>
 * </ul>
 * Minder OP dan Sophisticated Backpacks (jouw keuzes): <b>openen kost tijd</b> (de rits, 1,5 s stilstaan, zie
 * BackpackZip; met de rugzak open loop je traag), <b>geen gekke upgrades</b> (geen magneet, compacting, void of
 * stack-upgrades: gewone stapels), er past <b>geen rugzak, shulkerdoos of andere doos</b> in, en (stap 3) een ander kan
 * hem <b>van achteren</b> openen.
 *
 * Je draagt hem in het vakje "Back" naast je harnas (of het Curios-slot "back"), zie Gear.back. Open je hem op je rug met
 * de rugzaktoets of het wiel, in je hand met rechtsklik. Sneak + rechtsklik op de grond zet hem neer als blok
 * (BackpackBlock); dan opent iedereen hem met rechtsklik (jouw keuze).
 *
 * Wat erin zit staat in het data component minecraft:container, zoals bij een shulkerdoos. De kleur in
 * minecraft:dyed_color (verven zoals leer, zie BackpackDyeRecipe), camo in imttech:camo.
 */
public class BackpackItem extends Item {
    public enum Size {
        ASSAULT("assault_pack", 2, 2),
        PATROL("patrol_pack", 3, 3),
        RUCKSACK("rucksack", 4, 4);

        public final String id;
        /** Rijen van 9 vakjes. */
        public final int rows;
        /** Zoveel MOLLE-pouches passen aan de buitenkant (stap 2). */
        public final int molle;

        Size(String id, int rows, int molle) {
            this.id = id;
            this.rows = rows;
            this.molle = molle;
        }

        public int slots() {
            return rows * 9;
        }
    }

    /** De kleur van een nieuwe, ongeverfde rugzak: olijfgroen (olive drab), zoals een legerrugzak. */
    public static final int OLIVE = 0x5E6B3A;

    private final Size size;

    public BackpackItem(Properties properties, Size size) {
        super(properties.stacksTo(1));
        this.size = size;
    }

    public Size size() {
        return size;
    }

    /** De maat van deze rugzak, of null als het geen rugzak is. */
    public static Size sizeOf(ItemStack stack) {
        return stack.getItem() instanceof BackpackItem item ? item.size : null;
    }

    public static boolean isBackpack(ItemStack stack) {
        return stack.getItem() instanceof BackpackItem;
    }

    // ================================================================ Wat erin mag

    /**
     * Mag dit in een rugzak? Geen rugzak, geen shulkerdoos, en niets anders dat zelf spullen bewaart (een bundel, een
     * Ammo Box): anders stop je dozen in dozen in dozen, en dat is precies wat te OP is (jouw keuze). Een holster of pouch
     * mag wel: die zijn klein.
     */
    public static boolean mayHold(ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        if (isBackpack(stack) || !stack.getItem().canFitInsideContainerItems()) {
            return false;
        }
        if (Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock) {
            return false;
        }
        return !stack.has(DataComponents.CONTAINER) && !stack.has(DataComponents.BUNDLE_CONTENTS);
    }

    /** Een rugzak zelf past niet in een shulkerdoos of bundel (Minecraft vraagt dat hiermee). */
    @Override
    public boolean canFitInsideContainerItems() {
        return false;
    }

    // ================================================================ De inhoud

    /** Wat erin zit: precies {@code slots()} vakjes (kopieën). */
    public static List<ItemStack> contents(ItemStack pack) {
        Size size = sizeOf(pack);
        if (size == null) {
            return List.of();
        }
        NonNullList<ItemStack> list = NonNullList.withSize(size.slots(), ItemStack.EMPTY);
        pack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(list);
        return new ArrayList<>(list);
    }

    public static void setContents(ItemStack pack, List<ItemStack> contents) {
        Size size = sizeOf(pack);
        if (size == null) {
            return;
        }
        List<ItemStack> sized = new ArrayList<>(size.slots());
        boolean any = false;
        for (int i = 0; i < size.slots(); i++) {
            ItemStack stack = i < contents.size() ? contents.get(i).copy() : ItemStack.EMPTY;
            any |= !stack.isEmpty();
            sized.add(stack);
        }
        if (any) {
            pack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(sized));
        } else {
            pack.remove(DataComponents.CONTAINER);
        }
    }

    /** Zoveel vakjes zijn bezet. */
    public static int filled(ItemStack pack) {
        int count = 0;
        for (ItemStack stack : contents(pack)) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    /** De kleur (zonder camo): wat je erop verfde, of olijfgroen. */
    public static int colour(ItemStack pack) {
        return DyedItemColor.getOrDefault(pack, OLIVE);
    }

    public static boolean camo(ItemStack pack) {
        return Boolean.TRUE.equals(pack.get(BackpackRegistry.CAMO.get()));
    }

    // ================================================================ Rechtsklik

    /** Rechtsklik met de rugzak in je hand (in de lucht): openritsen. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            BackpackZip.start(serverPlayer, BackpackPlace.hand(serverPlayer, hand));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    /**
     * Rechtsklik op een blok: met sneak zet je hem neer (jouw keuze), anders rits je hem open (dan doet Minecraft daarna
     * {@link #use}).
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        BlockPlaceContext place = new BlockPlaceContext(context);
        if (!place.canPlace()) {
            return InteractionResult.FAIL;
        }
        BlockState state = BackpackRegistry.BACKPACK_BLOCK.get().getStateForPlacement(place);
        Level level = context.getLevel();
        BlockPos pos = place.getClickedPos();
        if (state == null || !state.canSurvive(level, pos)) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide) {
            ItemStack stack = context.getItemInHand();
            level.setBlock(pos, state.setValue(BackpackBlock.SIZE, BackpackBlock.sizeProperty(size)), Block.UPDATE_ALL_IMMEDIATE);
            if (level.getBlockEntity(pos) instanceof BackpackBlockEntity backpack) {
                backpack.setPack(stack.copyWithCount(1));
            }
            stack.consume(1, player);
            level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.BLOCKS, 0.9f, 0.8f);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ================================================================ De tooltip

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.backpack.size", size.slots(), size.molle).withStyle(ChatFormatting.GRAY));
        if (camo(stack)) {
            tooltip.add(Component.translatable("tooltip.imttech.backpack.camo").withStyle(ChatFormatting.DARK_GREEN));
        }
        List<ItemStack> contents = contents(stack);
        int shown = 0;
        int more = 0;
        for (ItemStack inside : contents) {
            if (inside.isEmpty()) {
                continue;
            }
            if (shown < 5) {
                tooltip.add(Component.literal(inside.getCount() + "x ").append(inside.getHoverName()).withStyle(ChatFormatting.GOLD));
                shown++;
            } else {
                more++;
            }
        }
        if (more > 0) {
            tooltip.add(Component.translatable("tooltip.imttech.backpack.more", more).withStyle(ChatFormatting.GOLD));
        }
        if (shown == 0) {
            tooltip.add(Component.translatable("tooltip.imttech.backpack.empty").withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.imttech.backpack.how", Math.round(ImtConfig.Backpacks.ZIP_SECONDS * 10) / 10.0f)
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
