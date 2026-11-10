package com.senne.imttech.backpack;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.guns.Gear;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Een neergezette rugzak (hoofdstuk 72, jouw keuze: "met sneak + rechtsklik op de grond zet je hem neer als blok, en dan
 * opent rechtsklik hem"). Hij staat rechtop, de MOLLE-banden naar je toe. De rugzak zelf (met alles erin, zijn kleur en
 * zijn pouches) zit in de block entity; de mod tekent hem zelf (client/backpack/BackpackRenderer).
 * <ul>
 *   <li><b>rechtsklik</b>: openritsen (1,5 s stilstaan); <b>iedereen</b> mag erin (jouw keuze);</li>
 *   <li><b>sneak + rechtsklik met een lege hand</b>: oppakken: op je rug als daar plek is, anders in je hand of je
 *       inventaris;</li>
 *   <li><b>kapotmaken</b>: hij valt eraf als item, met alles erin (zoals een shulkerdoos), ook in creative.</li>
 * </ul>
 */
public class BackpackBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<BackpackBlock> CODEC = simpleCodec(BackpackBlock::new);

    /** De maat (voor de vorm van het blok). */
    public enum SizeProperty implements StringRepresentable {
        ASSAULT, PATROL, RUCKSACK;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<SizeProperty> SIZE = EnumProperty.create("size", SizeProperty.class);

    /**
     * De vorm per maat als hij naar het noorden kijkt (x0, z0, x1, z1, hoogte in pixels; gemeten aan de modellen van
     * tools/generate_backpack_models.py, met zijn zakken en banden erbij), en voor de andere kanten gedraaid zoals
     * BackpackRenderer hem draait.
     */
    private static final double[][] NORTH = {{3.4, 4.3, 12.6, 10.9, 11.4}, {2, 3.7, 14, 11.4, 13.5}, {1.4, 2.8, 14.6, 12.2, 16}};
    private static final VoxelShape[][] SHAPES = new VoxelShape[3][4];

    static {
        for (int size = 0; size < 3; size++) {
            double[] b = NORTH[size];
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                double x0, z0, x1, z1;
                switch (facing) {
                    case SOUTH -> { x0 = 16 - b[2]; z0 = 16 - b[3]; x1 = 16 - b[0]; z1 = 16 - b[1]; }
                    case EAST -> { x0 = 16 - b[3]; z0 = b[0]; x1 = 16 - b[1]; z1 = b[2]; }
                    case WEST -> { x0 = b[1]; z0 = 16 - b[2]; x1 = b[3]; z1 = 16 - b[0]; }
                    default -> { x0 = b[0]; z0 = b[1]; x1 = b[2]; z1 = b[3]; }
                }
                SHAPES[size][facing.get2DDataValue()] = Block.box(x0, 0, z0, x1, b[4], z1);
            }
        }
    }

    public BackpackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SIZE, SizeProperty.ASSAULT));
    }

    public static SizeProperty sizeProperty(BackpackItem.Size size) {
        return SizeProperty.values()[size.ordinal()];
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIZE);
    }

    /** De MOLLE-banden naar jou toe. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Alleen op iets stevigs. */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, net.minecraft.world.level.LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        if (direction == Direction.DOWN && !state.canSurvive(level, pos)) {
            return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(SIZE).ordinal()][state.getValue(FACING).get2DDataValue()];
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BackpackBlockEntity(pos, state);
    }

    /** Rechtsklik: openritsen; sneak + lege hand: oppakken. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BackpackBlockEntity backpack) || backpack.pack().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            if (player.isSecondaryUseActive()) {
                pickUp(serverPlayer, serverLevel, pos, backpack);
            } else {
                BackpackZip.start(serverPlayer, BackpackPlace.block(serverLevel, pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Oppakken: op je rug als die leeg is, anders in je hand of je inventaris (of op de grond als alles vol is). */
    public static void pickUp(ServerPlayer player, ServerLevel level, BlockPos pos, BackpackBlockEntity backpack) {
        ItemStack pack = backpack.pack().copy();
        backpack.setPack(ItemStack.EMPTY);
        level.removeBlock(pos, false);
        if (Gear.back(player).isEmpty() && Gear.setBack(player, pack)) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable("backpack.imttech.on_back", pack.getHoverName())
                    .withStyle(net.minecraft.ChatFormatting.GRAY), true);
        } else if (!player.getInventory().add(pack)) {
            player.drop(pack, false);
        }
        level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_LEATHER.value(), SoundSource.PLAYERS, 0.9f, 1.0f);
    }

    /** Kapot: de rugzak valt eraf zoals hij was (met alles erin). */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        BlockEntity entity = params.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (entity instanceof BackpackBlockEntity backpack && !backpack.pack().isEmpty()) {
            return List.of(backpack.pack().copy());
        }
        return List.of();
    }

    /** Ook in creative valt hij eraf (anders ben je hem met alles erin kwijt), zoals een volle shulkerdoos. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && player.isCreative() && level.getBlockEntity(pos) instanceof BackpackBlockEntity backpack
                && !backpack.pack().isEmpty()) {
            ItemStack pack = backpack.pack().copy();
            backpack.setPack(ItemStack.EMPTY);
            Block.popResource(level, pos, pack);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        if (level.getBlockEntity(pos) instanceof BackpackBlockEntity backpack && !backpack.pack().isEmpty()) {
            return new ItemStack(backpack.pack().getItem());
        }
        return new ItemStack(BackpackRegistry.item(BackpackItem.Size.values()[state.getValue(SIZE).ordinal()]));
    }
}
