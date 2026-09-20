package com.senne.starktech.shield;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.edith.EdithBalance;
import com.senne.starktech.registry.ModDamageTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * De laserwand zelf: het rode energieveld tussen twee laserzenders (zoals de "ray shields"
 * uit Star Wars).
 *
 * Je kunt er niet door en hij doet pijn als je hem aanraakt. Je kunt hem ook niet weghalen:
 * hij verschijnt en verdwijnt met de zenders (zie LaserEmitterBlock).
 */
public class LaserWallBlock extends Block {
    public static final MapCodec<LaserWallBlock> CODEC = simpleCodec(LaserWallBlock::new);

    /** In welke richting de wand loopt (voor de texture). */
    public static final DirectionProperty AXIS_FACING = BlockStateProperties.FACING;
    /** Welke kleur het veld heeft; de zender bepaalt hem. */
    public static final EnumProperty<ShieldColor> COLOR = EnumProperty.create("color", ShieldColor.class);

    public LaserWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AXIS_FACING, net.minecraft.core.Direction.NORTH)
                .setValue(COLOR, ShieldColor.BLUE));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS_FACING, COLOR);
    }

    /**
     * Een dunne plaat midden in het blok, net als een glasplaat - maar er komt niemand door.
     * Hij staat dwars op de doorgang, dus de kant waar de zender naartoe kijkt bepaalt hoe
     * de plaat ligt.
     */
    private static final VoxelShape SHAPE_NS = Block.box(0, 0, 7, 16, 16, 9);
    private static final VoxelShape SHAPE_EW = Block.box(7, 0, 0, 9, 16, 16);
    private static final VoxelShape SHAPE_FLAT = Block.box(0, 7, 0, 16, 9, 16);

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS_FACING).getAxis()) {
            case Z -> SHAPE_NS;
            case X -> SHAPE_EW;
            case Y -> SHAPE_FLAT;
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Twee velden die tegen elkaar aan liggen hoeven hun raakvlak niet te tekenen. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbour, net.minecraft.core.Direction side) {
        return neighbour.is(this) && neighbour.getValue(AXIS_FACING) == state.getValue(AXIS_FACING);
    }

    /** Wie erin staat, krijgt schade (behalve items en dingen die niet leven). */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel serverLevel && entity instanceof LivingEntity living && living.hurtTime <= 0) {
            living.hurt(ModDamageTypes.laserWall(serverLevel), EdithBalance.LASER_WALL_DAMAGE);
        }
        // Items en pijlen die erin belanden verbranden.
        if (entity instanceof ItemEntity || entity instanceof net.minecraft.world.entity.projectile.Projectile) {
            entity.discard();
        }
    }

    /** Je kunt hem niet oprapen of breken: hij hoort bij de zenders. */
    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }
}
