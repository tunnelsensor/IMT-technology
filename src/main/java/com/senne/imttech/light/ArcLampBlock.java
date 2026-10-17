package com.senne.imttech.light;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerConnectable;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * De Arc Lamp (jouw wens, 9 oktober): een lamp op netstroom met lichtniveau 100 in plaats van 15.
 * <ul>
 *   <li>Hij werkt alleen op stroom: stroom = aan, geen stroom = uit. Een eigen aan/uit-knop heeft hij niet;
 *       je schakelt hem met een lichtschakelaar in de kabel (zie LightSwitchBlock), of door de stroom weg
 *       te halen.</li>
 *   <li>Zijn licht: op niveau 100 tot 85 blokken ver vol licht, daarna dooft het uit tot 100 blokken (zie
 *       ArcLampLight). Het gaat in rechte lijnen, niet door muren of dichte deuren en niet om hoeken: elke
 *       kamer heeft zijn eigen lamp nodig (13 oktober). Monsters spawnen er niet.</li>
 *   <li>Rechtsklik opent zijn instelscherm (10 oktober: "zorg dat je de brightness kunt instellen"): hoe
 *       fel hij is, lichtniveau 1 tot 100 (zie ArcLampScreen).</li>
 * </ul>
 * Een booglamp ("arc lamp") is een echte, extreem felle lamp: zo werkten de eerste straatlantaarns en
 * de zoeklichten van stadions.
 */
public class ArcLampBlock extends BaseEntityBlock implements PowerConnectable {
    public static final MapCodec<ArcLampBlock> CODEC = simpleCodec(ArcLampBlock::new);

    /** Brandt hij? Dan geeft het blok zelf licht (zie LIGHT), en gloeit zijn glas. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /**
     * Hoeveel licht het blok zelf geeft als hij brandt: zijn lichtniveau, maar nooit meer dan 15 (meer kan
     * een blok niet). Wat er boven de 15 bij komt, doet ArcLampLight. De lamp zet dit zelf goed.
     */
    public static final IntegerProperty LIGHT = IntegerProperty.create("light", 1, 15);

    public ArcLampBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(LIGHT, 15));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, LIGHT);
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

    /** Rechtsklik: het instelscherm (de server kijkt of je dichtbij genoeg staat, zie ArcLamps). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            ArcLamps.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.arc_lamp", Math.round(ImtConfig.Lights.ARC_LAMP_MAX_LEVEL), ArcLampLight.maxReach())
                .withStyle(ChatFormatting.GRAY));
    }
}
