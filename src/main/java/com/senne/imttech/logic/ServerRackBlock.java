package com.senne.imttech.logic;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.energy.PowerConnectable;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDevices;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Het serverrack (jouw wens, 7 oktober): een zwarte kast met vier vakjes voor een CPU, RAM of SSD.
 *
 * Racks die tegen elkaar staan (naast of op elkaar) vormen samen de server van de
 * beveiligingskern in wiens gebied ze staan; op die server draaien je redstone-diagrammen. Hij
 * heeft stroom nodig, en geeft die door aan het rack ernaast: een kast vol racks heeft genoeg aan
 * één kabel (zie PowerGrid).
 *
 * Rechtsklik opent zijn scherm: de vier vakjes, en wat de hele server kan.
 */
public class ServerRackBlock extends HorizontalDirectionalBlock implements EntityBlock, PowerConnectable {
    public static final MapCodec<ServerRackBlock> CODEC = simpleCodec(ServerRackBlock::new);

    /** Draait hij mee in de server (stroom, en hij hoort bij de grootste groep)? Dan knipperen zijn lampjes. */
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");

    public ServerRackBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(ONLINE, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ONLINE);
    }

    /** De voorkant (met de lampjes) kijkt naar wie hem neerzet. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // ================================================================ Gebruiken

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack) {
            serverPlayer.openMenu(rack, buf -> buf.writeBlockPos(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ================================================================ Neerzetten en afbreken

    /** Een nieuw rack: de server telt meteen opnieuw (anders duurt het een paar seconden). */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock()) && level instanceof ServerLevel serverLevel) {
            recount(serverLevel, pos);
        }
    }

    /** Afgebroken: de onderdelen vallen eruit, en de server telt opnieuw. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack) {
                Containers.dropContents(level, pos, rack.parts());
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
            if (level instanceof ServerLevel serverLevel) {
                recount(serverLevel, pos);
            }
            return;
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Laat de kern en het stroomnet opnieuw kijken welke racks er staan. */
    private static void recount(ServerLevel level, BlockPos pos) {
        PowerGrid.changed();
        LogicServer.forgetAll();
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, pos);
        if (core != null) {
            SecurityDevices.forget(level, core);
        }
    }

    // ================================================================ Binnenkant

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ServerRackBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ServerRackBlockEntity rack && tickLevel instanceof ServerLevel serverLevel) {
                rack.serverTick(serverLevel, pos, tickState);
            }
        };
    }
}
