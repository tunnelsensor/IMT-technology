package com.senne.starktech.light;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.energy.PowerConnectable;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
import org.jetbrains.annotations.Nullable;

/**
 * De Arc Lamp (jouw wens, 9 oktober): een lamp op Stark-stroom met lichtniveau 100 in plaats van 15.
 * <ul>
 *   <li>Hij werkt alleen op stroom: stroom = aan, geen stroom = uit. Een eigen knop heeft hij niet; je
 *       schakelt hem met een lichtschakelaar in de kabel (zie LightSwitchBlock), of door de stroom weg te
 *       halen.</li>
 *   <li>Zijn licht: tot 85 blokken ver vol licht, daarna dooft het uit tot 100 blokken (zie ArcLampLight).
 *       Het gaat niet door muren, en monsters spawnen er niet.</li>
 *   <li>Rechtsklik: hoe staat hij ervoor (aan, aan het opwarmen, of geen stroom).</li>
 * </ul>
 * Een booglamp ("arc lamp") is een echte, extreem felle lamp: zo werkten de eerste straatlantaarns en
 * de zoeklichten van stadions. En "arc" past bij Stark (de arc-reactor).
 */
public class ArcLampBlock extends BaseEntityBlock implements PowerConnectable {
    public static final MapCodec<ArcLampBlock> CODEC = simpleCodec(ArcLampBlock::new);

    /** Brandt hij? Dan geeft het blok zelf ook licht (15), en gloeit zijn glas. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public ArcLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcLampBlockEntity(pos, state);
    }

    /** Op de server: stroom halen en aan/uit; in je eigen spel: bijhouden of hij brandt (zie ArcLampLight). */
    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return (tickLevel, pos, tickState, blockEntity) -> {
                if (blockEntity instanceof ArcLampBlockEntity lamp) {
                    lamp.clientTick(tickLevel, pos, tickState);
                }
            };
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ArcLampBlockEntity lamp && tickLevel instanceof ServerLevel serverLevel) {
                lamp.serverTick(serverLevel, pos, tickState);
            }
        };
    }

    /** Afgebroken (of opgeblazen): zijn licht gaat mee. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            ArcLampLight.removed(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Rechtsklik: een regel onderin je beeld met hoe hij ervoor staat. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide) {
            Component status;
            if (!state.getValue(LIT)) {
                status = Component.translatable("light.starktech.lamp.off").withStyle(ChatFormatting.RED);
            } else if (ArcLampLight.measuring(level, pos)) {
                status = Component.translatable("light.starktech.lamp.warming", LightBalance.ARC_LAMP_LEVEL)
                        .withStyle(ChatFormatting.YELLOW);
            } else {
                status = Component.translatable("light.starktech.lamp.on", LightBalance.ARC_LAMP_LEVEL, ArcLampLight.reach())
                        .withStyle(ChatFormatting.AQUA);
            }
            player.displayClientMessage(status, true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.starktech.arc_lamp", LightBalance.ARC_LAMP_LEVEL, ArcLampLight.reach())
                .withStyle(ChatFormatting.GRAY));
    }
}
