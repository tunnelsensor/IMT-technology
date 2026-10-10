package com.senne.starktech.light;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.energy.PowerConnectable;
import com.senne.starktech.energy.StarkGrid;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.shield.EdithSwitchable;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * De lichtschakelaar (jouw wens, 9 oktober: "een lightswitch die je tussen een cable kunt zetten om zo de
 * lichten te besturen"). Een plaatje met een wipschakelaar, aan de muur, op de vloer of aan het plafond.
 *
 * Zo werkt hij: zet hem óp een Stark-kabel (of tussen twee stukken kabel in). Staat hij AAN, dan gaat de
 * stroom erdoorheen, alsof er gewoon kabel ligt. Staat hij UIT, dan houdt de stroom daar op: alles wat
 * achter de schakelaar hangt (je Arc Lamps, maar ook al het andere) krijgt geen stroom meer uit wat ervoor
 * hangt. Zie StarkGrid: daar telt de schakelaar als een stukje kabel dat aan of uit kan.
 *
 * Rechtsklik zet hem om. E.D.I.T.H. kan hem ook schakelen: wie hem neerzet, krijgt hem in het
 * redstone-tabblad ("Light switch 1"), net als een relais.
 */
public class LightSwitchBlock extends FaceAttachedHorizontalDirectionalBlock implements PowerConnectable, EdithSwitchable {
    public static final MapCodec<LightSwitchBlock> CODEC = simpleCodec(LightSwitchBlock::new);

    /** Staat hij aan (gaat de stroom erdoor)? */
    public static final BooleanProperty ON = BooleanProperty.create("on");

    /** De vorm (voor het aanklikken) per bevestiging en richting. */
    private static final Map<AttachFace, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(AttachFace.class);

    static {
        // Voor een schakelaar die naar het noorden kijkt (de muur zit aan de zuidkant); de rest draaien we.
        SHAPES.put(AttachFace.WALL, turned(5, 3, 14, 11, 13, 16));
        SHAPES.put(AttachFace.FLOOR, turned(5, 0, 3, 11, 2, 13));
        SHAPES.put(AttachFace.CEILING, turned(5, 14, 3, 11, 16, 13));
    }

    public LightSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.WALL)
                .setValue(FACING, Direction.NORTH).setValue(ON, true));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, ON);
    }

    /** Staat deze schakelaar aan? (Voor StarkGrid.) */
    public static boolean isOn(BlockState state) {
        return state.getValue(ON);
    }

    // ================================================================ Vorm

    /** Een doosje voor een schakelaar die naar het noorden kijkt, en dezelfde doos naar elke richting gedraaid. */
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

    // ================================================================ Neerzetten, omzetten, weghalen

    /** Neergezet: het net is anders (hier loopt nu een schakelaar), en E.D.I.T.H. kan hem schakelen. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player) {
            EdithRedstone.register(player, pos, "edith.starktech.light_switch.default_name");
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            StarkGrid.changed();
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            StarkGrid.changed();
            if (level instanceof ServerLevel serverLevel) {
                EdithRedstone.forget(serverLevel, pos);
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Rechtsklik: om. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) {
            set(serverLevel, pos, !state.getValue(ON));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Zet de schakelaar aan of uit (ook E.D.I.T.H. doet het zo). */
    public static void set(ServerLevel level, BlockPos pos, boolean on) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof LightSwitchBlock) || state.getValue(ON) == on) {
            return;
        }
        level.setBlock(pos, state.setValue(ON, on), Block.UPDATE_ALL);
        StarkGrid.changed();
        // Het tikje van een lichtknop: een hoog hendelklikje.
        level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, on ? 1.7f : 1.5f);
        if (on) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    3, 0.1, 0.1, 0.1, 0.05);
        }
    }

    // ================================================================ E.D.I.T.H.

    @Override
    public void setSwitched(ServerLevel level, BlockPos pos, BlockState state, boolean on) {
        set(level, pos, on);
    }

    @Override
    public boolean isSwitchedOn(BlockState state) {
        return state.getValue(ON);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.starktech.light_switch").withStyle(ChatFormatting.GRAY));
    }
}
