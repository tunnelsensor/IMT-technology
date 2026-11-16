package com.senne.imttech.computer;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerConnectable;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.lan.DevicePower;
import com.senne.imttech.lan.NetworkConnectable;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * De Computer (hoofdstuk 40, jouw wens: "een desktop met monitor die je via de security core configureert", bijvoorbeeld
 * voor de receptie van je basis). Jouw keuze: twee blokken, deze kast en een Monitor die ertegen staat (zie MonitorBlock).
 *
 * <ul>
 *   <li>De kast hangt met een <b>netwerkkabel</b> (of PoE-kabel) aan de desktop van een Security Core, en heeft een beetje
 *       <b>stroom</b> nodig ({@code computerUpkeep} onder [power]), zoals een keycardlezer.</li>
 *   <li>Werkt hij (kabel, stroom, en de kern heeft zelf stroom), dan brandt de ring rond de aan-knop ({@link #ON}) en
 *       licht het scherm van elke monitor ertegen op.</li>
 *   <li>Wat je op die pc mag (welke apps, kijken of ook bedienen, wie, een code) stel je in op de kern, in de app
 *       Computers. Dat staat in de kast zelf (zie ComputerBlockEntity): hak je hem weg, dan begint hij opnieuw.</li>
 * </ul>
 * Rechtsklik op de kast zegt hoe hij ervoor staat; je opent hem met een rechtsklik op de monitor.
 */
public class ComputerBlock extends BaseEntityBlock implements PowerConnectable, NetworkConnectable, DevicePower.Powered {
    public static final MapCodec<ComputerBlock> CODEC = simpleCodec(ComputerBlock::new);

    /** Waar de voorkant (met de aan-knop) naartoe kijkt. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Werkt hij (brandt de ring, licht de monitor op)? Zie ComputerBlockEntity.updateOn. */
    public static final BooleanProperty ON = BooleanProperty.create("on");
    /** Hoofdstuk 130: zit er een SD-kaart in de kast? Dan steekt er vooraan een kaartje uit de gleuf. */
    public static final BooleanProperty CARD = BooleanProperty.create("card");

    public ComputerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ON, false).setValue(CARD, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ON, CARD);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ComputerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ComputerBlockEntity entity && tickLevel instanceof ServerLevel serverLevel) {
                entity.serverTick(serverLevel, pos);
            }
        };
    }

    /** Stroom die de computer elke twee seconden gebruikt (uit [power]). */
    @Override
    public float upkeep() {
        return ImtConfig.Power.COMPUTER_UPKEEP;
    }

    // ================================================================ Neerzetten, gebruiken, weghalen

    /** Wie hem neerzet, is de eigenaar; hij krijgt een naam ("Computer 1", of de naam uit een aambeeld). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof ComputerBlockEntity entity)) {
            return;
        }
        if (placer instanceof ServerPlayer player) {
            entity.setOwner(player);
        }
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            entity.setName(stack.getHoverName().getString());
        }
        if (entity.name().isEmpty()) {
            entity.setName(Computers.freeName(serverLevel, pos));
        }
        entity.updateOn(serverLevel);
    }

    /** Neergezet of weggehaald: de netten (stroom en netwerk) lopen nu anders. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            PowerGrid.changed();
        }
    }

    /** Weggehaald: de netten lopen anders, en wie deze pc open had, krijgt hem dicht. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            PowerGrid.changed();
            if (level instanceof ServerLevel serverLevel) {
                Computers.gone(serverLevel, pos);
                // Hoofdstuk 130: de SD-kaart valt eruit.
                if (level.getBlockEntity(pos) instanceof ComputerBlockEntity entity) {
                    com.senne.imttech.sdcard.SdCardSlots.dropOnBreak(level, pos, entity);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Hoofdstuk 130: rechtsklik met een SD-kaart steekt hem in de kast; sneak met een lege hand haalt hem eruit (zie
     * SdCardSlots). Al de rest zoals altijd: hoe hij ervoor staat.
     */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                                                  Player player, net.minecraft.world.InteractionHand hand,
                                                                  BlockHitResult hit) {
        if (stack.isEmpty()) {
            if (player.isSecondaryUseActive() && state.getValue(CARD)) {
                if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof ComputerBlockEntity entity) {
                    com.senne.imttech.sdcard.SdCardSlots.takeOut(serverPlayer, pos, entity,
                            com.senne.imttech.sdcard.SdCardSlots.mayTakeOut(serverPlayer, entity));
                }
                return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND || !com.senne.imttech.sdcard.SdCardItem.isCard(stack)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof ComputerBlockEntity entity) {
            com.senne.imttech.sdcard.SdCardSlots.insert(serverPlayer, pos, entity, stack,
                    com.senne.imttech.sdcard.SdCardSlots.mayUse(serverPlayer, entity));
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Rechtsklik op de kast: hoe hij ervoor staat (openen doe je op de monitor). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            Computers.status(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.computer").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.computer.use").withStyle(ChatFormatting.DARK_AQUA));
    }
}
