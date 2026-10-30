package com.senne.imttech.logic;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Wat de netwerkknop en de netwerkhendel samen hebben (hoofdstuk 38, jouw wens: "netwerk lever en buttons, deze hangen
 * ook aan een kabel (power en netwerk) en kunnen net zoals een redstone button of lever worden gebruikt maar dan via de
 * logic in de desktop").
 *
 * Net als een keycardlezer (zie InputDeviceBlock): een naam ("Button 1", hernoemen met sneak + rechtsklik met een lege
 * hand, of in Devices), een eigenaar, een netwerkkabel naar de desktop van een kern en een beetje stroom. En net als een
 * gewone knop of hendel: aan de muur, op de vloer of aan het plafond. Ze geven geen redstone: wat er gebeurt, staat in je
 * diagram ({@code when button("Doorbell"):}, {@code when lever("Night mode"):}).
 *
 * Zonder netwerkkabel, stroom of draaiende server beweegt hij wel (het is een echte knop), maar hoort de server niets: de
 * speler hoort waarom (zie LogicEvents).
 */
public abstract class NetworkControlBlock extends InputDeviceBlock {
    /** Waar hij aan vastzit: de muur, de vloer of het plafond. */
    public static final EnumProperty<AttachFace> FACE = BlockStateProperties.ATTACH_FACE;

    /** De vorm per bevestiging en richting. */
    private final Map<AttachFace, Map<Direction, VoxelShape>> shapes = new EnumMap<>(AttachFace.class);

    /**
     * @param wall    de vorm aan de muur ({x0, y0, z0, x1, y1, z1}) als hij naar het noorden kijkt (de muur zit dan aan
     *                de zuidkant); de andere richtingen draaien we
     * @param floor   hetzelfde op de vloer
     * @param ceiling hetzelfde aan het plafond
     */
    protected NetworkControlBlock(Properties properties, double[] wall, double[] floor, double[] ceiling) {
        super(properties, wall[0], wall[1], wall[2], wall[3], wall[4], wall[5]);
        shapes.put(AttachFace.WALL, turned(wall));
        shapes.put(AttachFace.FLOOR, turned(floor));
        shapes.put(AttachFace.CEILING, turned(ceiling));
        registerDefaultState(defaultBlockState().setValue(FACE, AttachFace.WALL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACE);
    }

    /** Een doosje voor een blok dat naar het noorden kijkt, en dezelfde doos naar elke richting gedraaid. */
    private static Map<Direction, VoxelShape> turned(double[] box) {
        double x0 = box[0], y0 = box[1], z0 = box[2], x1 = box[3], y1 = box[4], z1 = box[5];
        Map<Direction, VoxelShape> turned = new EnumMap<>(Direction.class);
        turned.put(Direction.NORTH, Block.box(x0, y0, z0, x1, y1, z1));
        turned.put(Direction.SOUTH, Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0));
        turned.put(Direction.EAST, Block.box(16 - z1, y0, x0, 16 - z0, y1, x1));
        turned.put(Direction.WEST, Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0));
        return turned;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACE)).get(state.getValue(FACING));
    }

    /**
     * Op de vloer of tegen het plafond geklikt: daar, met de kant waar jij heen kijkt (zoals een gewone hendel). Op een
     * muur: met de voorkant van die muur af.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face == Direction.UP) {
            return defaultBlockState().setValue(FACE, AttachFace.FLOOR).setValue(FACING, context.getHorizontalDirection());
        }
        if (face == Direction.DOWN) {
            return defaultBlockState().setValue(FACE, AttachFace.CEILING).setValue(FACING, context.getHorizontalDirection());
        }
        return defaultBlockState().setValue(FACE, AttachFace.WALL).setValue(FACING, face);
    }

    // ================================================================ Gebruiken

    /**
     * Rechtsklik: indrukken of omzetten ({@link #use}). Sneak + rechtsklik met een lege hand: het naamscherm, als je hem
     * mag hernoemen (zie InputDevices); mag dat niet, dan gebruik je hem gewoon: iedereen kan op een knop drukken.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer
                && !(player.isSecondaryUseActive() && InputDevices.tryOpen(serverPlayer, pos))) {
            use(serverPlayer.serverLevel(), pos, state, serverPlayer);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Met iets in je hand druk je ook gewoon (zoals op een gewone knop), ook met de Bypass Kit: aan een knop valt niets
     * te hacken. (Een lezer laat de kit juist zijn werk doen, zie InputDeviceBlock.)
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** Indrukken (de knop) of omzetten (de hendel), en de server laten weten (LogicEvents). */
    protected abstract void use(ServerLevel level, BlockPos pos, BlockState state, ServerPlayer player);

    /** De uitleg onder de naam in je rugzak (tooltip.imttech.network_button, ...). */
    protected abstract String tooltipKey();

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(tooltipKey()).withStyle(ChatFormatting.GRAY));
    }
}
