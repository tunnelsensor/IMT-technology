package com.senne.imttech.security;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.energy.PowerConnectable;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * De bewakingscamera (jouw wens, 28 september): een kleine camera aan de muur, aan het plafond of
 * op de vloer. Zijn beeld zie je op de bewakingsmuur van de Security OS-desktop (de app Cameras).
 * <ul>
 *   <li>Hij hoort bij de beveiligingskern in wiens gebied hij hangt.</li>
 *   <li>Hij heeft een beetje stroom nodig (een kabel of iets dat stroom geeft, ernaast). Zonder
 *       stroom, of als de eigenaar de camera's op de desktop uitzette, is zijn beeld "NO SIGNAL"
 *       en is zijn lampje uit (ONLINE = false).</li>
 *   <li>Hij kijkt van de muur af (of, aan het plafond, de kant op waar jij keek toen je hem
 *       ophing), een beetje omlaag. Precies waarheen stel je in op de desktop (zie
 *       CameraBlockEntity: pan en tilt).</li>
 *   <li>Geef je hem in een aambeeld of op de desktop (Rename, bij het grote beeld) een naam
 *       ("Lobby"), dan staat die naam bij zijn beeld. Breek je hem af, dan houdt het item die naam.</li>
 * </ul>
 */
public class CameraBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock, PowerConnectable {
    public static final MapCodec<CameraBlock> CODEC = simpleCodec(CameraBlock::new);

    /** Werkt hij (stroom, en de camera's van het netwerk staan aan)? Dan brandt zijn lampje. */
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");

    /** De vorm (voor het aanklikken) per bevestiging en richting. */
    private static final Map<AttachFace, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(AttachFace.class);

    static {
        // Voor een camera die naar het noorden kijkt; de andere richtingen draaien we.
        SHAPES.put(AttachFace.WALL, turned(5, 7, 3, 11, 15, 16));
        SHAPES.put(AttachFace.CEILING, turned(5, 6, 3, 11, 16, 13));
        SHAPES.put(AttachFace.FLOOR, turned(5, 0, 3, 11, 9, 13));
    }

    public CameraBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.WALL)
                .setValue(FACING, Direction.NORTH).setValue(ONLINE, false));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, ONLINE);
    }

    // ================================================================ Vorm

    /** Een doosje voor een camera die naar het noorden kijkt, en dezelfde doos gedraaid naar elke richting. */
    private static Map<Direction, VoxelShape> turned(double x0, double y0, double z0, double x1, double y1, double z1) {
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        shapes.put(Direction.NORTH, Block.box(x0, y0, z0, x1, y1, z1));
        shapes.put(Direction.SOUTH, Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0));
        shapes.put(Direction.EAST, Block.box(16 - z1, y0, x0, 16 - z0, y1, x1));
        shapes.put(Direction.WEST, Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0));
        return shapes;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACE)).get(state.getValue(FACING));
    }

    // ================================================================ Neerzetten en bekijken

    /** Had het item een naam (uit een aambeeld)? Dan heet de camera zo. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (stack.has(DataComponents.CUSTOM_NAME) && level.getBlockEntity(pos) instanceof CameraBlockEntity camera) {
            camera.setName(stack.getHoverName().getString());
        }
    }

    /** Rechtsklik: hoe staat hij ervoor (stroom, en bij welk netwerk hij hoort)? */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof CameraBlockEntity camera) {
            SecurityCoreBlockEntity core = SecurityCores.coreAt(serverLevel, pos);
            Component status = state.getValue(ONLINE)
                    ? Component.translatable("security.imttech.camera.online").withStyle(ChatFormatting.GREEN)
                    : Component.translatable(core != null && !core.camerasEnabled()
                            ? "security.imttech.camera.off" : "security.imttech.camera.no_power").withStyle(ChatFormatting.RED);
            Component network = core == null
                    ? Component.translatable("security.imttech.camera.no_network").withStyle(ChatFormatting.YELLOW)
                    : Component.translatable("security.imttech.camera.network", core.hostname()).withStyle(ChatFormatting.AQUA);
            String name = camera.name().isEmpty() ? "Security Camera" : camera.name();
            player.displayClientMessage(Component.translatable("security.imttech.camera.status", name, status, network), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ================================================================ Binnenkant

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CameraBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof CameraBlockEntity camera && tickLevel instanceof ServerLevel serverLevel) {
                camera.serverTick(serverLevel, pos, tickState);
            }
        };
    }
}
