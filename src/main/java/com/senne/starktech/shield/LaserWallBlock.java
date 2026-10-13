package com.senne.starktech.shield;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.config.StarkConfig;
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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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
    /** Een kwartslag gedraaid (liggend in plaats van staand); de zender bepaalt het (zie LaserEmitterBlock.TURNED). */
    public static final BooleanProperty TURNED = LaserEmitterBlock.TURNED;

    public LaserWallBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(AXIS_FACING, net.minecraft.core.Direction.NORTH)
                .setValue(COLOR, ShieldColor.BLUE)
                .setValue(TURNED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS_FACING, COLOR, TURNED);
    }

    /**
     * Een dunne plaat midden in het blok, net als een glasplaat - maar er komt niemand door.
     * De platen liggen IN de lijn van zender naar zender, zodat ze samen één doorlopende wand
     * vormen (---) en niet als losse plaatjes naast elkaar staan (| | |).
     * Loopt de wand van oost naar west (as X), dan is de plaat dun in de noord-zuidrichting.
     *
     * Gedraaid (TURNED, 13 oktober) ligt een wand die opzij loopt plat, als een vloertje (FLAT): je kunt
     * er dan niet doorheen springen of vallen. Een wand die omhoog loopt, staat dan noord-zuid.
     */
    private static final VoxelShape ALONG_X = Block.box(0, 0, 7, 16, 16, 9);
    private static final VoxelShape ALONG_Z = Block.box(7, 0, 0, 9, 16, 16);
    private static final VoxelShape FLAT = Block.box(0, 7, 0, 16, 9, 16);

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean turned = state.getValue(TURNED);
        return switch (state.getValue(AXIS_FACING).getAxis()) {
            case X -> turned ? FLAT : ALONG_X;
            case Z -> turned ? FLAT : ALONG_Z;
            // Een staande wand (zenders in vloer en plafond): een plaat van oost naar west, gedraaid noord-zuid.
            case Y -> turned ? ALONG_Z : ALONG_X;
        };
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** Twee velden die tegen elkaar aan liggen hoeven hun raakvlak niet te tekenen. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState neighbour, net.minecraft.core.Direction side) {
        return neighbour.is(this) && neighbour.getValue(AXIS_FACING) == state.getValue(AXIS_FACING)
                && neighbour.getValue(TURNED) == state.getValue(TURNED);
    }

    /** Wie erin staat, krijgt schade (behalve items en dingen die niet leven). */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level instanceof ServerLevel serverLevel && entity instanceof LivingEntity living && living.hurtTime <= 0
                && !friendOfTheTower(serverLevel, pos, living)) {
            if (living.hurt(ModDamageTypes.laserWall(serverLevel), StarkConfig.Shields.LASER_WALL_DAMAGE)) {
                // Een knetter, zoals tegen een Star Wars-laserpoort aanlopen.
                serverLevel.playSound(null, living.getX(), living.getY() + living.getBbHeight() / 2, living.getZ(),
                        com.senne.starktech.registry.ModSounds.RAYSHIELD_HIT.get(),
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.7f, 1.0f + serverLevel.random.nextFloat() * 0.2f);
            }
        }
        // Items en pijlen die erin belanden verbranden.
        if (entity instanceof ItemEntity || entity instanceof net.minecraft.world.entity.projectile.Projectile) {
            entity.discard();
        }
    }

    /**
     * Staat deze wand in een Stark Tower die van jou is (of van je team)? Dan loop je er gewoon
     * doorheen. Een toren van niemand, of een wand buiten een toren: dan brand je.
     */
    private static boolean friendOfTheTower(ServerLevel level, BlockPos pos, LivingEntity entity) {
        if (!(entity instanceof net.minecraft.world.entity.player.Player player)) {
            return false;
        }
        var core = com.senne.starktech.security.SecurityCores.coreAt(level, pos);
        return core != null && com.senne.starktech.security.SecurityCores.isFriend(level, core, player);
    }

    /** Je kunt hem niet oprapen of breken: hij hoort bij de zenders. */
    @Override
    public ItemStack getCloneItemStack(net.minecraft.world.level.LevelReader level, BlockPos pos, BlockState state) {
        return ItemStack.EMPTY;
    }
}
