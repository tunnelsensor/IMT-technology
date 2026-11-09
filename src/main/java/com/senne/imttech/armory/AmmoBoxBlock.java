package com.senne.imttech.armory;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * De Ammo Box (hoofdstuk 67, jouw wens: "ammoboxen waar je mee kunt interacten om ammo in en uit te doen zonder een
 * GUI, dus door te klikken of shift-klikken"). Een stalen munitiekist op de grond, met een etiket op de voorkant: het
 * plaatje van wat erin zit en hoeveel (zie client/armory/AmmoBoxRenderer). Wat elke klik doet staat in
 * {@link AmmoBoxes}; wat erin zit in {@link AmmoBoxBlockEntity}.
 */
public class AmmoBoxBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<AmmoBoxBlock> CODEC = simpleCodec(AmmoBoxBlock::new);

    /** De kist ligt met zijn lange kant naar voren: 12 breed, 8 diep, 9 hoog (pixels). */
    private static final VoxelShape NORTH_SOUTH = Block.box(2, 0, 4, 14, 9, 12);
    private static final VoxelShape EAST_WEST = Block.box(4, 0, 2, 12, 9, 14);

    public AmmoBoxBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Het etiket naar jou toe. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? NORTH_SOUTH : EAST_WEST;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AmmoBoxBlockEntity(pos, state);
    }

    /** Rechtsklik met iets in je hand (zie {@link AmmoBoxes#useFor}). In jouw spel beslissen we alleen of je arm zwaait. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND || stack.isEmpty() || !(level.getBlockEntity(pos) instanceof AmmoBoxBlockEntity box)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        AmmoBoxes.Use use = AmmoBoxes.useFor(player, stack, box);
        if (use == AmmoBoxes.Use.NONE) {
            // Niets met de doos te maken: het item doet wat het altijd doet (een blok neerzetten, richten...).
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            AmmoBoxes.use(serverPlayer, pos, box, stack);
        }
        boolean swing = use == AmmoBoxes.Use.INSERT || use == AmmoBoxes.Use.INSERT_ALL;
        return swing ? ItemInteractionResult.sidedSuccess(level.isClientSide) : ItemInteractionResult.CONSUME;
    }

    /** Rechtsklik met een lege hand: een stapel eruit, met sneak één. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof AmmoBoxBlockEntity box)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            AmmoBoxes.take(serverPlayer, box, player.isSecondaryUseActive());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** De tooltip van de kist als item: wat erin zit (zoals bij een shulkerdoos). */
    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ItemContainerContents contents = stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        int count = 0;
        ItemStack sort = ItemStack.EMPTY;
        float rounds = 0;
        for (ItemStack inside : contents.nonEmptyItems()) {
            if (sort.isEmpty()) {
                sort = inside;
            }
            count += inside.getCount();
            rounds += AmmoBoxBlockEntity.fullness(inside);
        }
        if (count == 0) {
            tooltip.add(Component.translatable("armory.imttech.box.tooltip_empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("armory.imttech.box.tooltip", count, sort.getHoverName(),
                AmmoBoxBlockEntity.capacity(sort)).withStyle(ChatFormatting.GOLD));
        if (!AmmoBoxBlockEntity.loose(sort)) {
            String key = sort.is(com.senne.imttech.registry.ModItems.ENERGY_CELL.get())
                    ? "armory.imttech.box.tooltip_charge" : "armory.imttech.box.tooltip_rounds";
            float shown = sort.is(com.senne.imttech.registry.ModItems.ENERGY_CELL.get()) ? rounds / count : rounds;
            tooltip.add(Component.translatable(key, Math.round(shown)).withStyle(ChatFormatting.GRAY));
        }
    }
}
