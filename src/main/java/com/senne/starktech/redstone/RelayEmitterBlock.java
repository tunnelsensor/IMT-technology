package com.senne.starktech.redstone;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * De relay emitter (13 oktober: "een block die relay emitter is zodat je ook relays kunt triggeren door
 * redstone te gebruiken").
 *
 * Het omgekeerde van een relais: een relais GEEFT redstone als E.D.I.T.H. het aanzet, de emitter KRIJGT
 * redstone (een knop, hendel, drukplaat, daglichtsensor...) en zet dan jouw relais om. In zijn scherm
 * typ je welke: de naam van één relais, of een tag ("base" = alles wat erin zit, ook in tags eronder).
 * En je kiest wat hij doet:
 * <ul>
 *   <li><b>Follow</b> (standaard): redstone aan = relais aan, redstone uit = relais uit.</li>
 *   <li><b>Toggle</b>: elke keer dat de redstone aangaat, wisselen de relais (aan wordt uit).</li>
 *   <li><b>Pulse</b>: elke keer dat de redstone aangaat, geven de relais een korte puls.</li>
 * </ul>
 * Hij werkt altijd en kost niets, ook als jij offline bent: hij onthoudt zelf welke relais erbij horen
 * (zie RelayEmitterBlockEntity). Je hebt er geen bril voor nodig.
 *
 * Hij reageert één tick later dan de redstone (net als een repeater). Zo kan een relais dat naast zijn
 * eigen emitter staat nooit in een eindeloze kring raken die het spel laat vastlopen.
 */
public class RelayEmitterBlock extends BaseEntityBlock {
    public static final MapCodec<RelayEmitterBlock> CODEC = simpleCodec(RelayEmitterBlock::new);

    /** Krijgt hij nu redstone? Dan gloeit zijn lampje rood. */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    /** Een plat kastje van 12 x 3 x 12 pixels, net als het relais (het antennetje telt niet mee). */
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 3, 14);

    public RelayEmitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RelayEmitterBlockEntity(pos, state);
    }

    /** Alleen op de server: af en toe de lijst van zijn relais bijwerken (zie RelayEmitterBlockEntity). */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof RelayEmitterBlockEntity emitter && tickLevel instanceof ServerLevel serverLevel) {
                emitter.serverTick(serverLevel);
            }
        };
    }

    // ---------------------------------------------------------------- Redstone erin

    /** Redstonestof naast de emitter wijst ernaartoe (anders zou hij er gewoon langs lopen). */
    @Override
    public boolean canConnectRedstone(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return true;
    }

    /** Er veranderde iets naast hem: is de redstone anders dan hij dacht? Dan over één tick kijken. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && level.hasNeighborSignal(pos) != state.getValue(POWERED)
                && !level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    /** Net neergezet (misschien al op redstone): ook even kijken. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide && !oldState.is(this) && level.hasNeighborSignal(pos) != state.getValue(POWERED)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    /** De tick daarna: ging de redstone aan of uit? Dan schakelt hij (zie RelayEmitterBlockEntity). */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean powered = level.hasNeighborSignal(pos);
        if (powered == state.getValue(POWERED)) {
            return;
        }
        level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        if (level.getBlockEntity(pos) instanceof RelayEmitterBlockEntity emitter) {
            emitter.signalChanged(level, powered);
        }
    }

    // ---------------------------------------------------------------- Neerzetten en gebruiken

    /** Wie hem neerzet, is de eigenaar: hij schakelt de relais uit JOUW lijst. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof RelayEmitterBlockEntity emitter) {
            emitter.setOwner(player);
        }
    }

    /** Rechtsklik: zijn scherm (welke relais, en wat hij doet). Geen bril nodig. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            RelayEmitters.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.starktech.relay_emitter").withStyle(ChatFormatting.GRAY));
    }
}
