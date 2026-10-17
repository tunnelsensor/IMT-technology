package com.senne.imttech.security;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * De hacklaptop, opengeklapt bovenop een beveiligingskern: daar loopt de hack op. Rechtsklik
 * erop opent de terminal (alleen voor wie hem neerzette). Hij onthoudt je voortgang (zie
 * HackingLaptopBlockEntity), dus sluit je het scherm of loop je even weg, dan ga je later verder
 * waar je was. Hij kan alleen op een kern staan: gaat de kern weg, dan valt hij eraf.
 *
 * Je zet hem niet neer als een gewoon blok, maar door met de laptop op de kern te klikken (zie
 * HackingLaptopItem en TowerHack).
 */
public class HackingLaptopBlock extends BaseEntityBlock {
    public static final MapCodec<HackingLaptopBlock> CODEC = simpleCodec(HackingLaptopBlock::new);
    /** De kant waar het scherm naartoe kijkt (naar wie hem neerzette). */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Plat onderstuk en het opengeklapte scherm erachter. */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 3, 15, 1, 13), Block.box(1, 1, 11, 15, 10, 13));

    public HackingLaptopBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> rotate(SHAPE, 2);
            case WEST -> rotate(SHAPE, 3);
            case EAST -> rotate(SHAPE, 1);
            default -> SHAPE;
        };
    }

    /** De vorm een aantal kwartslagen met de klok mee draaien (om het midden van het blok). */
    private static VoxelShape rotate(VoxelShape shape, int turns) {
        VoxelShape result = shape;
        for (int i = 0; i < turns; i++) {
            VoxelShape[] turned = {Shapes.empty()};
            result.forAllBoxes((x0, y0, z0, x1, y1, z1) -> turned[0] = Shapes.or(turned[0],
                    Shapes.box(1 - z1, y0, x0, 1 - z0, y1, x1)));
            result = turned[0];
        }
        return result;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HackingLaptopBlockEntity(pos, state);
    }

    /** Alleen bovenop een beveiligingskern. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).getBlock() instanceof SecurityCoreBlock;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
            return Blocks.AIR.defaultBlockState(); // de kern is weg: de laptop valt eraf
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            TowerHack.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Weg (afgebroken, of de kern is weg): wie de terminal open had, krijgt hem dicht. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            TowerHack.laptopGone(level, pos);
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Middelste muisknop: de laptop zelf. */
    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.HACKING_LAPTOP.get());
    }
}
