package com.senne.imttech.computer;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * De Monitor (hoofdstuk 40): een scherm op een voetje met een toetsenbord ervoor. Hij heeft zelf geen stroom of kabel
 * nodig: hij gebruikt de Computer waar hij tegenaan staat (aan welke kant maakt niet uit; een computer mag meer monitoren
 * hebben). Werkt die computer, dan licht het scherm op ({@link #ON}) en geeft het een beetje licht.
 *
 * Rechtsklik op de monitor opent de desktop van die computer, met wat de kern daar toestaat (zie Computers.use).
 */
public class MonitorBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<MonitorBlock> CODEC = simpleCodec(MonitorBlock::new);
    /** Toont het scherm de desktop (de computer ertegen werkt)? */
    public static final BooleanProperty ON = ComputerBlock.ON;

    /** Naar het noorden: het voetje en het scherm achteraan, het toetsenbord vooraan. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(1, 3, 11, 15, 13, 12.5),   // het scherm
            Block.box(7, 1, 12.5, 9, 6, 13.5),   // het steeltje
            Block.box(5, 0, 10, 11, 1, 14),      // het voetje
            Block.box(2, 0, 2, 14, 1, 7));       // het toetsenbord

    public MonitorBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON);
    }

    /** Het scherm kijkt naar wie hem neerzet; staat hij tegen een werkende computer, dan meteen aan. */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(ON, Computers.lit(context.getLevel(), context.getClickedPos()));
    }

    /** Er veranderde iets naast de monitor (een computer ging aan of uit, of is weg): het scherm volgt. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(ON, Computers.lit(level, pos));
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

    /** De vorm een aantal kwartslagen met de klok mee draaien (om het midden van het blok), zoals bij de hacklaptop. */
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

    /** Rechtsklik: de desktop van de computer ertegen (of waarom dat niet gaat). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            Computers.use(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.monitor").withStyle(ChatFormatting.GRAY));
    }
}
