package com.senne.starktech.energy;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.shield.EdithSwitchable;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * De stroomkast: een stalen kast waar kabels van alle kanten op aansluiten, met achter het deurtje
 * een schakelaar per kant (jouw wens, 27 september).
 *
 * Elke kant is een eigen "groep" van het stroomnet. Staat de schakelaar van een kant AAN, dan hoort
 * die kant bij de kast; alles wat aan de kanten hangt die aan staan, zit met elkaar verbonden. Zet
 * je een kant UIT, dan krijgt wat daar aan hangt geen stroom meer uit de kast (zie StarkGrid).
 * Voorbeeld: de generatoren hangen aan de achterkant, de torentjes aan links, de lasers aan rechts:
 * met de schakelaar "links" zet je alle torentjes in één keer uit.
 *
 * Rechtsklik opent het deurtje (zie PowerCabinets en PowerCabinetScreen). Wie ervoor staat, kan
 * schakelen; op afstand (het redstone-tabblad van E.D.I.T.H.) alleen de eigenaar.
 */
public class PowerCabinetBlock extends BaseEntityBlock implements PowerConnectable, EdithSwitchable {
    public static final MapCodec<PowerCabinetBlock> CODEC = simpleCodec(PowerCabinetBlock::new);

    /** Waar het deurtje zit. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Per kant: staat de schakelaar aan? (Dezelfde namen als bij een kabel: north, east, ...) */
    public static final Map<Direction, BooleanProperty> SWITCHES = PipeBlock.PROPERTY_BY_DIRECTION;

    public PowerCabinetBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH);
        for (BooleanProperty side : SWITCHES.values()) {
            state = state.setValue(side, true);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
        SWITCHES.values().forEach(builder::add);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerCabinetBlockEntity(pos, state);
    }

    /** Staat de schakelaar van deze kant aan? */
    public static boolean isOn(BlockState state, Direction side) {
        return state.getValue(SWITCHES.get(side));
    }

    /** Wie hem neerzet, is de eigenaar (in een Stark Tower: wie de toren gehackt heeft). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof PowerCabinetBlockEntity cabinet) {
            cabinet.setOwner(player.getUUID());
        }
        StarkGrid.changed();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            StarkGrid.changed();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            StarkGrid.changed();
            if (level instanceof ServerLevel serverLevel) {
                com.senne.starktech.redstone.EdithRedstone.forget(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Rechtsklik: het deurtje open (het scherm met de schakelaars). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            PowerCabinets.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ================================================================ E.D.I.T.H.

    /** E.D.I.T.H. zet de hele kast aan of uit: alle schakelaars tegelijk. */
    @Override
    public void setSwitched(ServerLevel level, BlockPos pos, BlockState state, boolean on) {
        for (Direction side : Direction.values()) {
            PowerCabinets.setSwitch(level, pos, side, on);
        }
    }

    /** Staat de kast "aan"? Ja, als minstens één schakelaar aan staat. */
    @Override
    public boolean isSwitchedOn(BlockState state) {
        for (BooleanProperty side : SWITCHES.values()) {
            if (state.getValue(side)) {
                return true;
            }
        }
        return false;
    }
}
