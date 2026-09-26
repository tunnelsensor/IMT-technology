package com.senne.starktech.suit;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.energy.PowerConnectable;
import com.senne.starktech.energy.StarkGrid;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Het midden van een landingsplatform: een verlichte ring in de vloer. Land je erop met het
 * harnas, dan ben je thuis: je bril laadt op en je harnas wordt gerepareerd (met stroom van het
 * net), en tijdens het vliegen wijst de HUD je de weg ernaartoe. Zie LandingPadBlockEntity.
 */
public class LandingPadBlock extends BaseEntityBlock implements PowerConnectable {
    public static final MapCodec<LandingPadBlock> CODEC = simpleCodec(LandingPadBlock::new);

    public LandingPadBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LandingPadBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof LandingPadBlockEntity pad && tickLevel instanceof ServerLevel serverLevel) {
                pad.serverTick(serverLevel, pos);
            }
        };
    }

    /** Wie hem neerzet, is de eigenaar. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof LandingPadBlockEntity pad) {
            pad.setOwner(player.getUUID());
        }
    }

    /** Rechtsklik: van wie is dit platform, en hoeveel stroom heeft het? (Boven je hotbar.) */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof LandingPadBlockEntity pad) {
            UUID owner = pad.ownerId(serverLevel);
            Component text = owner == null ? Component.translatable("block.starktech.landing_pad.no_owner")
                    : !owner.equals(player.getUUID()) ? Component.translatable("block.starktech.landing_pad.not_yours")
                    : Component.translatable("block.starktech.landing_pad.yours", StarkGrid.available(level, pos));
            player.displayClientMessage(text, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Afgebroken: van de lijst van zijn eigenaar af. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof LandingPadBlockEntity pad) {
            pad.onBroken();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
