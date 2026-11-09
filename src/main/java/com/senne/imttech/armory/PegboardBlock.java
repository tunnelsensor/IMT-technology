package com.senne.imttech.armory;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
 * Het Pegboard (hoofdstuk 67, jouw wens: "armory items zodat je al de guns mooi op de muren kunt hangen"). Een dun
 * stalen paneel met gaatjes tegen een muur. Panelen naast en boven elkaar worden één wand.
 * <ul>
 *   <li><b>Rechtsklik met een wapen</b> (of een magazijn, cel, granaat, holster, pouch, sling): het hangt waar je klikt,
 *       vastgeklikt op het raster; past het daar niet, dan op het dichtstbijzijnde vrije plekje.</li>
 *   <li><b>Rechtsklik met een lege hand</b> op iets dat hangt: je pakt het (in je hand).</li>
 *   <li><b>Sneak + rechtsklik met een lege hand</b>: het draait (loop naar links, rechts, beneden, boven).</li>
 * </ul>
 * Iedereen mag eraan (jouw keuze, zoals een item frame). Wat erop hangt staat in {@link PegboardBlockEntity}, de
 * rekensommen in {@link Pegboard}.
 */
public class PegboardBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<PegboardBlock> CODEC = simpleCodec(PegboardBlock::new);

    /** Het paneel ligt tegen de muur achter zich: 1 pixel dik. */
    private static final VoxelShape SOUTH = Block.box(0, 0, 0, 16, 16, Pegboard.THICKNESS);
    private static final VoxelShape NORTH = Block.box(0, 0, 16 - Pegboard.THICKNESS, 16, 16, 16);
    private static final VoxelShape EAST = Block.box(0, 0, 0, Pegboard.THICKNESS, 16, 16);
    private static final VoxelShape WEST = Block.box(16 - Pegboard.THICKNESS, 0, 0, 16, 16, 16);

    public PegboardBlock(Properties properties) {
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

    /** Tegen de muur waar je op klikt; klik je op een vloer of plafond, dan naar jou toe. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> SOUTH;
        };
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PegboardBlockEntity(pos, state);
    }

    // ================================================================ Klikken

    /** Rechtsklik met iets in je hand: ophangen als het mag, anders doet het item wat het altijd doet. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // lege hand: zie useWithoutItem
        }
        if (hand != InteractionHand.MAIN_HAND || !Pegboard.hangable(stack)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            hang(serverPlayer, pos, state.getValue(FACING), hit, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Rechtsklik met een lege hand: pakken, of met sneak draaien. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Direction facing = state.getValue(FACING);
        double[] cell = Pegboard.aim(player, pos, facing, hit.getLocation(), Pegboard.BACK + 1.5);
        Pegboard.Spot spot = Pegboard.itemAt(level, pos, facing, cell[0], cell[1]);
        if (spot == null) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isSecondaryUseActive()) {
                turn(serverPlayer, pos, facing, spot);
            } else {
                take(serverPlayer, spot);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Ophangen: één stuk uit je hand, met zijn midden op de klik (of het dichtstbijzijnde vrije plekje). */
    public static boolean hang(ServerPlayer player, BlockPos pos, Direction facing, BlockHitResult hit, ItemStack stack) {
        Level level = player.level();
        double[] cell = Pegboard.aim(player, pos, facing, hit.getLocation(), Pegboard.BACK);
        return hangAt(player, level, pos, facing, cell[0], cell[1], stack, Pegboard.Turn.LEFT);
    }

    /** Ophangen op gaatje (U, V) van de wand (ook voor de tests). Geeft false (met een melding) als er geen plek is. */
    public static boolean hangAt(@Nullable ServerPlayer player, Level level, BlockPos pos, Direction facing, double cu, double cv,
                                 ItemStack stack, Pegboard.Turn turn) {
        int[] size = Pegboard.size(stack, turn);
        int[] at = Pegboard.place(level, pos, facing, cu, cv, size[0], size[1], null);
        if (at == null) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("armory.imttech.pegboard.no_room")
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        BlockPos anchor = Pegboard.boardAt(pos, facing, at[0], at[1]);
        if (!(level.getBlockEntity(anchor) instanceof PegboardBlockEntity board)) {
            return false;
        }
        board.add(stack, at[0], at[1], turn);
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        level.playSound(null, anchor, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8f, 0.8f);
        return true;
    }

    /** Pakken: in je (lege) hand. */
    private static void take(ServerPlayer player, Pegboard.Spot spot) {
        PegboardBlockEntity board = spot.board();
        ItemStack stack = board.remove(spot.index());
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        } else {
            AmmoBoxes.giveBesideHand(player, stack);
        }
        player.level().playSound(null, board.getBlockPos(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8f, 0.8f);
    }

    /** Draaien: de volgende stand, met hetzelfde midden (of zo dicht mogelijk erbij). */
    public static boolean turn(@Nullable ServerPlayer player, BlockPos pos, Direction facing, Pegboard.Spot spot) {
        Level level = spot.board().getLevel();
        if (level == null) {
            return false;
        }
        PegboardBlockEntity.Hung hung = spot.board().hung().get(spot.index());
        double cu = spot.u() + spot.w() / 2.0;
        double cv = spot.v() + spot.h() / 2.0;
        // De volgende stand die past: een sniper op één rij panelen (10 gaatjes hoog past niet) gaat dan van links naar
        // rechts en weer terug, zonder melding.
        Pegboard.Turn next = hung.turn();
        int[] at = null;
        for (int tries = 0; tries < Pegboard.Turn.values().length - 1 && at == null; tries++) {
            next = next.next();
            int[] size = Pegboard.size(hung.stack(), next);
            at = Pegboard.place(level, pos, facing, cu, cv, size[0], size[1], spot);
        }
        if (at == null) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("armory.imttech.pegboard.no_room_turn")
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        BlockPos anchor = Pegboard.boardAt(pos, facing, at[0], at[1]);
        if (anchor.equals(spot.board().getBlockPos())) {
            spot.board().turn(spot.index(), at[0], at[1], next);
        } else if (level.getBlockEntity(anchor) instanceof PegboardBlockEntity other) {
            // Het gaatje linksonder ligt nu op een ander paneel: daar hoort het wapen voortaan bij.
            ItemStack stack = spot.board().remove(spot.index());
            other.add(stack, at[0], at[1], next);
        } else {
            return false;
        }
        level.playSound(null, anchor, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.8f, 1.0f);
        return true;
    }

    // ================================================================ Weghalen

    /** Weg: wat erop hing valt eraf, en ook wat van de buren over dit paneel hing. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof PegboardBlockEntity board) {
                board.dropAll(level);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
            if (!level.isClientSide) {
                Direction facing = state.getValue(FACING);
                for (int du = -Pegboard.REACH; du <= 0; du++) {
                    for (int dv = -Pegboard.REACH; dv <= 0; dv++) {
                        BlockPos other = Pegboard.offset(pos, facing, du, dv);
                        if ((du != 0 || dv != 0) && Pegboard.isBoard(level, other, facing)
                                && level.getBlockEntity(other) instanceof PegboardBlockEntity neighbour) {
                            neighbour.dropLoose(level);
                        }
                    }
                }
            }
            return;
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
