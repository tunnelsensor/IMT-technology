package com.senne.starktech.holo;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.energy.PowerConnectable;
import com.senne.starktech.shield.ShieldColor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * De holotafel: een lage tafel die een hologram boven zich projecteert.
 *
 * Wat je ermee doet:
 * - **rechtsklik met een lege hand**: het instelscherm (stand, kleur, bereik, grootte...)
 * - **rechtsklik met een voorwerp**: laat het recept van dat voorwerp zien
 * - **sluipen + rechtsklik**: meteen de volgende kleur, zonder scherm
 *
 * Je hebt de bril op nodig: zonder E.D.I.T.H. is het een dure tafel.
 */
public class HoloTableBlock extends BaseEntityBlock implements PowerConnectable {
    public static final MapCodec<HoloTableBlock> CODEC = simpleCodec(HoloTableBlock::new);

    /** De kleur van het hologram. Dezelfde rij kleuren als de rayshields. */
    public static final EnumProperty<ShieldColor> COLOR = EnumProperty.create("color", ShieldColor.class);

    /** Een lage tafel: je kijkt eroverheen naar het hologram. */
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 12, 16);

    public HoloTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(getStateDefinition().any().setValue(COLOR, ShieldColor.CYAN));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Het blok zelf is een gewoon model; het hologram tekent de HoloTableRenderer erbovenop. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new HoloTableBlockEntity(pos, state);
    }

    /** Op de server tikt de tafel: zo betaalt hij zijn stroom (zie HoloTableBlockEntity). */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof HoloTableBlockEntity table && tickLevel instanceof ServerLevel serverLevel) {
                table.serverTick(serverLevel, pos);
            }
        };
    }

    // ---------------------------------------------------------------- Bedienen

    /** Met een voorwerp in je hand: laat daarvan het recept zien. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // Minecraft roept dit óók aan met een lege hand (een "lege stapel"). Zonder deze regel
        // legden we dan "lucht" op tafel en ging het instelscherm nooit open.
        if (stack.isEmpty() || player.isShiftKeyDown()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!hasGlasses(player)) {
            tellNoGlasses(level, player);
            return ItemInteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof HoloTableBlockEntity table) {
            table.setBlueprint(stack);
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("holo.starktech.blueprint", stack.getHoverName()), true);
                level.playSound(null, pos, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 0.5f, 1.8f);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Lege hand: volgende stand. Sluipen: volgende kleur. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!hasGlasses(player)) {
            tellNoGlasses(level, player);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ShieldColor colour = state.getValue(COLOR).next();
                level.setBlock(pos, state.setValue(COLOR, colour), Block.UPDATE_ALL);
                level.playSound(null, pos, SoundEvents.DYE_USE, SoundSource.BLOCKS, 0.6f, 1.4f);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        // Lege hand: het instelscherm. De server beslist of je het mag openen.
        if (player instanceof ServerPlayer serverPlayer) {
            HoloTables.openSettings(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static boolean hasGlasses(Player player) {
        ItemStack glasses = EdithData.getWornGlasses(player);
        return !glasses.isEmpty() && EdithData.isAuthorized(glasses, player);
    }

    private static void tellNoGlasses(Level level, Player player) {
        if (!level.isClientSide) {
            player.displayClientMessage(Component.translatable("holo.starktech.need_glasses"), true);
        }
    }
}
