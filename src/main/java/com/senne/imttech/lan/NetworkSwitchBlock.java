package com.senne.imttech.lan;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerConnectable;
import com.senne.imttech.energy.PowerGrid;
import java.util.List;
import java.util.Map;
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
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.RenderShape;
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
 * De Network Switch (hoofdstuk 38, jouw wens: "een block zoals de powercable block waar de kabels samenkomen, maar dan
 * voor netwerkkabels"). Jouw keuze: zoals de stroomkast, met eigen stroom.
 *
 * Netwerk- en PoE-kabels sluiten van alle zes de kanten aan; elke kant is een poort met een eigen aan/uit-schakelaar.
 * <ul>
 *   <li>Staat een poort AAN, dan gaat het netwerk door die kant; staat hij UIT, dan hangt wat daar zit niet meer aan het
 *       netwerk (zie LanGrid).</li>
 *   <li>Een switch heeft zelf een beetje stroom nodig ({@code networkSwitchUpkeep} onder [power]): van een stroomkabel,
 *       een generator ertegen of een PoE-kabel. Zonder stroom (of in een EMP-puls) gaat er geen netwerk doorheen: alles
 *       erachter ligt plat. De lampjes op de voorkant laten zien of hij stroom heeft.</li>
 *   <li>PoE-stroom gaat door de switch heen, van een PoE-kabel naar de PoE-kabels aan de andere poorten die aan staan (jouw
 *       keuze: "PoE mag ook via een switch"). Zo voedt een PoE-kabel van de desktop een hele switch vol camera's.</li>
 * </ul>
 * Rechtsklik opent het deurtje met de zes schakelaars (wie ervoor staat, kan schakelen, net als bij de stroomkast); op
 * afstand schakel je de poorten in de app Devices op de desktop van de kern waar hij aan hangt.
 */
public class NetworkSwitchBlock extends BaseEntityBlock implements PowerConnectable, NetworkConnectable, DevicePower.Powered {
    public static final MapCodec<NetworkSwitchBlock> CODEC = simpleCodec(NetworkSwitchBlock::new);

    /** Waar het deurtje (met de lampjes) zit. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Heeft hij stroom (branden de lampjes)? Zie NetworkSwitchBlockEntity.updateOnline. */
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");
    /** Per kant: staat die poort aan? (Dezelfde namen als bij de stroomkast: north, east, ...) */
    public static final Map<Direction, BooleanProperty> PORTS = PipeBlock.PROPERTY_BY_DIRECTION;

    public NetworkSwitchBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ONLINE, false);
        for (BooleanProperty port : PORTS.values()) {
            state = state.setValue(port, true);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ONLINE);
        PORTS.values().forEach(builder::add);
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
        return new NetworkSwitchBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof NetworkSwitchBlockEntity entity && tickLevel instanceof ServerLevel serverLevel) {
                entity.serverTick(serverLevel, pos);
            }
        };
    }

    /** Stroom die de switch elke twee seconden gebruikt (uit [power]). */
    @Override
    public float upkeep() {
        return ImtConfig.Power.NETWORK_SWITCH_UPKEEP;
    }

    // ================================================================ Opvragen

    /** Staat de poort aan deze kant aan? */
    public static boolean isOn(BlockState state, Direction side) {
        return state.getValue(PORTS.get(side));
    }

    /** Gaat er netwerk door deze switch? Hij moet stroom hebben (ONLINE) en niet uit liggen door een EMP-puls. */
    public static boolean passes(Level level, BlockPos pos, BlockState state) {
        return state.getValue(ONLINE) && !com.senne.imttech.weapons.Emp.isOut(level, pos);
    }

    // ================================================================ Neerzetten, gebruiken, weghalen

    /** Wie hem neerzet, is de eigenaar; hij krijgt een naam ("Switch 1", of de naam uit een aambeeld). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof NetworkSwitchBlockEntity entity)) {
            return;
        }
        if (placer instanceof ServerPlayer player) {
            entity.setOwner(player);
        }
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            entity.setName(stack.getHoverName().getString());
        }
        if (entity.name().isEmpty()) {
            entity.setName(NetworkSwitches.freeName(serverLevel, pos));
        }
        entity.updateOnline(serverLevel);
    }

    /** Neergezet of weggehaald: de netten (stroom via PoE, en het netwerk) lopen nu anders. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            PowerGrid.changed();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            PowerGrid.changed();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Rechtsklik: het deurtje open (de zes poorten). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkSwitches.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.network_switch").withStyle(ChatFormatting.GRAY));
    }
}
