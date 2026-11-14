package com.senne.imttech.lan;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerConnectable;
import com.senne.imttech.energy.PowerGrid;
import java.util.EnumMap;
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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * De Router (hoofdstuk 112, jouw wens van 14 november: "een nieuw item, een router: hier kun je elk ding draadloos tot op
 * 32 blokken op connecten ... elke router kan max 10 devices en kan ook verbinden met andere routers, die horen dan ook
 * bij één van de devices, dus dan is het één router + negen andere devices").
 *
 * Een plat kastje met twee antennes. Het hangt zelf aan het netwerk (met een netwerk- of PoE-kabel, of draadloos aan een
 * andere router), en geeft dat netwerk door aan wat er draadloos mee verbonden is: relais, Chest Sensors, camera's,
 * laserpoorten... Die apparaten hebben dan geen netwerkkabel nodig; stroom nog wel, als ze die altijd al nodig hadden
 * (jouw wens: "gates, die hebben dan wel nog steeds power nodig maar ze verbinden dan via de router").
 * <ul>
 *   <li><b>Verbinden</b> gaat vanzelf: zet je een apparaat binnen bereik van een router met een vrije plek, dan hangt het
 *       eraan; zet je een router neer, dan pakt hij de losse apparaten in zijn buurt. In zijn scherm (rechtsklik) zie je
 *       wat eraan hangt en wat er in de buurt staat, en verbind je met de hand (zie {@link Routers}).</li>
 *   <li><b>Stroom</b>: een beetje ({@code routerUpkeep} onder [power]), van een stroomkabel, een generator ertegen of een
 *       PoE-kabel. Zonder stroom (of in een EMP-puls) geeft hij niets door: alles wat alleen via hem aan het netwerk
 *       hangt, ligt dan plat. De lampjes op de voorkant laten zien of hij stroom heeft.</li>
 * </ul>
 */
public class RouterBlock extends BaseEntityBlock implements PowerConnectable, NetworkConnectable, DevicePower.Powered {
    public static final MapCodec<RouterBlock> CODEC = simpleCodec(RouterBlock::new);

    /** De kant van de lampjes. */
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    /** Heeft hij stroom (branden de lampjes)? Zie RouterBlockEntity.updateOnline. */
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");

    /** Het kastje (12 x 3 x 8 pixels) met zijn twee antennes achteraan, per kant waar de lampjes heen wijzen. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            SHAPES.put(facing, Shapes.or(turned(facing, 2, 0, 4, 14, 3, 12), turned(facing, 3, 3, 10, 4, 11, 11),
                    turned(facing, 12, 3, 10, 13, 11, 11)));
        }
    }

    /** Een doos die voor "lampjes naar het noorden" getekend is, gedraaid naar deze kant. */
    private static VoxelShape turned(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    public RouterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ONLINE, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ONLINE);
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RouterBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof RouterBlockEntity entity && tickLevel instanceof ServerLevel serverLevel) {
                entity.serverTick(serverLevel, pos);
            }
        };
    }

    /** Stroom die de router elke twee seconden gebruikt (uit [power]). */
    @Override
    public float upkeep() {
        return ImtConfig.Power.ROUTER_UPKEEP;
    }

    /** Geeft deze router het netwerk door? Hij moet stroom hebben (ONLINE) en niet uit liggen door een EMP-puls. */
    public static boolean passes(Level level, BlockPos pos, BlockState state) {
        return state.getValue(ONLINE) && !com.senne.imttech.weapons.Emp.isOut(level, pos);
    }

    // ================================================================ Neerzetten, gebruiken, weghalen

    /**
     * Wie hem neerzet, is de eigenaar; hij krijgt een naam ("Router 1", of de naam uit een aambeeld), en pakt meteen de
     * losse apparaten in zijn buurt (zie Routers.autoLink).
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof RouterBlockEntity entity)) {
            return;
        }
        ServerPlayer player = placer instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        if (player != null) {
            entity.setOwner(player);
        }
        if (stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
            entity.setName(stack.getHoverName().getString());
        }
        RouterLinks.get(serverLevel).addRouter(pos);
        if (entity.name().isEmpty()) {
            entity.setName(Routers.freeName(serverLevel, pos));
        }
        entity.updateOnline(serverLevel);
        Routers.autoLink(serverLevel, pos, player);
    }

    /** Neergezet (ook zonder speler: een commando, een gebouw): hij meldt zich aan, en de netten lopen nu anders. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            if (level instanceof ServerLevel serverLevel) {
                RouterLinks.get(serverLevel).addRouter(pos);
            }
            PowerGrid.changed(); // ook het netwerk
        }
    }

    /** Weg: al zijn draadloze verbindingen ook. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level instanceof ServerLevel serverLevel) {
                RouterLinks.get(serverLevel).removeRouter(pos);
            }
            PowerGrid.changed();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Rechtsklik: zijn scherm (wat eraan hangt, en wat er in de buurt staat). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            Routers.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.router").withStyle(ChatFormatting.GRAY));
    }
}
