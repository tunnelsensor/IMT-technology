package com.senne.imttech.battery;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Battery Casing en Battery Glass (hoofdstuk 47): de wand van een Power Battery (zie BatteryStructure). Rechtsklik opent
 * het scherm van de batterij waar de wand bij hoort (via een poort erin). Erbij zetten of weghalen laat de poorten van
 * die batterij opnieuw kijken of de doos nog klopt.
 */
public class BatteryCasingBlock extends Block {
    private final boolean glass;

    public BatteryCasingBlock(Properties properties, boolean glass) {
        super(properties);
        this.glass = glass;
    }

    public boolean isGlass() {
        return glass;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            Batteries.open(level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            Batteries.changed(level, pos);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!newState.is(state.getBlock())) {
            Batteries.changed(level, pos);
        }
    }

    // Glas: doorzichtig, en de binnenkant van de doos (de cellen) is dan te zien.

    @Override
    protected VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return glass ? Shapes.empty() : super.getVisualShape(state, level, pos, context);
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return glass ? 1.0f : super.getShadeBrightness(state, level, pos);
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return glass || super.propagatesSkylightDown(state, level, pos);
    }

    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacent, net.minecraft.core.Direction direction) {
        return glass && adjacent.is(this) || super.skipRendering(state, adjacent, direction);
    }
}
