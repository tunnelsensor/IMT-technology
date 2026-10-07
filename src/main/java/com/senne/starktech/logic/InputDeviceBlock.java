package com.senne.starktech.logic;

import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import com.senne.starktech.security.SecurityDevices;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Wat een keycardlezer en een codepaneel samen hebben: een plat kastje aan de muur met een lampje
 * (zie InputLight) en een naam (InputDeviceBlockEntity). Ze geven een "trigger" aan de server van
 * de beveiligingskern in wiens gebied ze hangen; wat er dan gebeurt, staat in je diagram.
 */
public abstract class InputDeviceBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final EnumProperty<InputLight> LIGHT = EnumProperty.create("light", InputLight.class);

    /** Zo lang (ticks) blijft het lampje groen, rood of oranje. */
    public static final int LIGHT_TICKS = 30;

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    /**
     * @param x0 ... z1 de vorm voor een kastje waarvan de voorkant naar het noorden kijkt (het hangt
     *                  dan aan de muur aan de zuidkant); de andere richtingen draaien we.
     */
    protected InputDeviceBlock(Properties properties, double x0, double y0, double z0, double x1, double y1, double z1) {
        super(properties);
        shapes.put(Direction.NORTH, Block.box(x0, y0, z0, x1, y1, z1));
        shapes.put(Direction.SOUTH, Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0));
        shapes.put(Direction.EAST, Block.box(16 - z1, y0, x0, 16 - z0, y1, x1));
        shapes.put(Direction.WEST, Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0));
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIGHT, InputLight.OFF));
    }

    /** Het begin van de standaardnaam: "Reader" of "Panel" (daarna komt een nummer). */
    protected abstract String defaultNamePrefix();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIGHT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    /** Op een muur geklikt: met de voorkant van die muur af. Anders: naar jou toe. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    // ================================================================ Naam

    /** Een naam uit een aambeeld, of anders "Reader 2" (het eerste vrije nummer in dit netwerk). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof InputDeviceBlockEntity device)) {
            return;
        }
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            device.setName(stack.getHoverName().getString());
        }
        if (device.name().isEmpty()) {
            device.setName(freeName(serverLevel, pos));
        }
        SecurityCoreBlockEntity core = SecurityCores.coreAt(serverLevel, pos);
        if (core != null) {
            SecurityDevices.forget(serverLevel, core);
        }
    }

    /** "Reader 1", "Reader 2", ...: het eerste nummer dat in dit netwerk nog niet gebruikt is. */
    private String freeName(ServerLevel level, BlockPos pos) {
        Set<String> used = new HashSet<>();
        SecurityCoreBlockEntity core = SecurityCores.coreAt(level, pos);
        if (core != null) {
            for (BlockPos other : SecurityDevices.scan(level, core).inputs()) {
                if (!other.equals(pos) && level.getBlockEntity(other) instanceof InputDeviceBlockEntity device) {
                    used.add(device.name());
                }
            }
        }
        int number = 1;
        while (used.contains(defaultNamePrefix() + " " + number)) {
            number++;
        }
        return defaultNamePrefix() + " " + number;
    }

    // ================================================================ Het lampje

    /** Zet het lampje even op groen, rood of oranje; na LIGHT_TICKS gaat het weer uit. */
    public static void flash(ServerLevel level, BlockPos pos, InputLight light) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof InputDeviceBlock block)) {
            return;
        }
        level.setBlock(pos, state.setValue(LIGHT, light), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, block, LIGHT_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(LIGHT) != InputLight.OFF) {
            level.setBlock(pos, state.setValue(LIGHT, InputLight.OFF), Block.UPDATE_CLIENTS);
        }
    }

    // ================================================================ Weg

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        super.onRemove(state, level, pos, newState, movedByPiston);
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            SecurityCoreBlockEntity core = SecurityCores.coreAt(serverLevel, pos);
            if (core != null) {
                SecurityDevices.forget(serverLevel, core);
            }
        }
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new InputDeviceBlockEntity(pos, state);
    }
}
