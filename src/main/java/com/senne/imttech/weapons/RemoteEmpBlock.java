package com.senne.imttech.weapons;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.c4.DetonatorData;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * De Remote EMP (hoofdstuk 35, jouw wens: "een remote getriggerde EMP, deze ziet er uit als een op de muur geplaatste
 * EMP maar dan trigger je die met een item in de inventory"). Een halve bol met een antenne die je tegen een vloer, muur
 * of plafond plakt. Hij doet niets tot iemand op zijn EMP-trigger drukt (zie {@link EmpTriggerItem}): dan geeft hij een
 * gewone EMP-puls (zoals een granaat) en is hij weg. Zo zet je bijvoorbeeld de kabels van een stroomnet uit wanneer jij
 * wilt, van ver weg.
 *
 * Plak je hem met een EMP-trigger in je inventaris, dan hoort hij meteen bij die trigger; anders koppel je hem met een
 * rechtsklik met de trigger. Iedereen kan hem er gewoon aftrekken (breken): dan valt hij eruit.
 */
public class RemoteEmpBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<RemoteEmpBlock> CODEC = simpleCodec(RemoteEmpBlock::new);

    /** De vorm (voor het aanklikken), per kant waar hij tegenaan plakt: een halve bol van 8 x 8, 5 hoog. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.DOWN, Block.box(4, 0, 4, 12, 5, 12));
        SHAPES.put(Direction.UP, Block.box(4, 11, 4, 12, 16, 12));
        SHAPES.put(Direction.NORTH, Block.box(4, 4, 0, 12, 12, 5));
        SHAPES.put(Direction.SOUTH, Block.box(4, 4, 11, 12, 12, 16));
        SHAPES.put(Direction.WEST, Block.box(0, 4, 4, 5, 12, 12));
        SHAPES.put(Direction.EAST, Block.box(11, 4, 4, 16, 12, 12));
    }

    public RemoteEmpBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.WALL).setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    /** De kant die van het blok af wijst (de bolle kant). */
    public static Direction outward(BlockState state) {
        return getConnectedDirection(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(outward(state).getOpposite());
    }

    /** Plakkerig: hij blijft zitten, ook als het blok erachter verdwijnt. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return state;
    }

    /** Een EMP-trigger erop: koppelen (als hij nog nergens bij hoort, of al bij deze). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!(stack.getItem() instanceof EmpTriggerItem)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof RemoteEmpBlockEntity emp) {
            DetonatorData data = EmpTriggerItem.ensure(stack);
            Component message;
            if (emp.trigger() != null && !emp.trigger().equals(data.id())) {
                message = Component.translatable("item.imttech.remote_emp.taken").withStyle(ChatFormatting.YELLOW);
            } else if (EmpTriggerItem.link(serverLevel, pos, emp, stack)) {
                message = Component.translatable("item.imttech.remote_emp.linked", EmpTriggerItem.ensure(stack).charges().size())
                        .withStyle(ChatFormatting.AQUA);
            } else {
                message = Component.translatable("item.imttech.remote_emp.full", DetonatorData.MAX_CHARGES)
                        .withStyle(ChatFormatting.YELLOW);
            }
            player.displayClientMessage(message, true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RemoteEmpBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof RemoteEmpBlockEntity emp && tickLevel instanceof ServerLevel serverLevel) {
                emp.serverTick(serverLevel, pos, tickState);
            }
        };
    }
}
