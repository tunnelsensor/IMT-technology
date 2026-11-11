package com.senne.imttech.armory;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Het Pegboard (hoofdstuk 67, jouw wens: "armory items zodat je al de guns mooi op de muren kunt hangen"). Een dun
 * stalen paneel met gaatjes tegen een muur. Panelen naast en boven elkaar worden één wand.
 * <ul>
 *   <li><b>Rechtsklik met een wapen</b> (of een magazijn, cel, granaat, holster, pouch, sling): het hangt waar je klikt,
 *       vastgeklikt op het raster; past het daar niet, dan op het dichtstbijzijnde vrije plekje. Klik je op de schaduw
 *       van een vastgezet item van hetzelfde soort, dan komt het precies daar (hoofdstuk 70).</li>
 *   <li><b>Rechtsklik met een lege hand</b> op iets dat hangt: je pakt het (in je hand).</li>
 *   <li><b>Sneak + rechtsklik met een lege hand</b>: het draait (loop naar links, rechts, beneden, boven).</li>
 *   <li><b>Sneak + linksklik</b> op iets dat hangt (hoofdstuk 70): vastzetten of weer los (hoofdstuk 71: zijn omtrek
 *       wordt grijs op het paneel geverfd). Op een schaduw: die veeg je weg. Dat doet je spel
 *       (client/armory/PegboardLock), en de server via {@link #handleLock}.</li>
 * </ul>
 * Iedereen mag eraan (jouw keuze, zoals een item frame), maar alleen van dichtbij: maximaal 2 blokken ervan (hoofdstuk
 * 71, {@link Pegboard#inReach}). Wat erop hangt staat in {@link PegboardBlockEntity}, de rekensommen in {@link Pegboard}.
 *
 * <b>Een hoek</b> (hoofdstuk 70): rechtsklik met een Pegboard op de andere muur van een binnenhoek, waar al een paneel
 * hangt: dan komt het tweede paneel in hetzelfde blok ({@link #CORNER}). Afbreken haalt ze allebei weg (2 Pegboards).
 */
public class PegboardBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final MapCodec<PegboardBlock> CODEC = simpleCodec(PegboardBlock::new);

    /** In een hoek: het tweede paneel hangt tegen de muur links of rechts (zoals jij voor het eerste staat). */
    public enum Corner implements StringRepresentable {
        NONE, LEFT, RIGHT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Corner> CORNER = EnumProperty.create("corner", Corner.class);

    /** Het paneel ligt tegen de muur achter zich: 1 pixel dik. Per kant die het op kijkt. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Map.of(
            Direction.SOUTH, Block.box(0, 0, 0, 16, 16, Pegboard.THICKNESS),
            Direction.NORTH, Block.box(0, 0, 16 - Pegboard.THICKNESS, 16, 16, 16),
            Direction.EAST, Block.box(0, 0, 0, Pegboard.THICKNESS, 16, 16),
            Direction.WEST, Block.box(16 - Pegboard.THICKNESS, 0, 0, 16, 16, 16)));

    public PegboardBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CORNER, Corner.NONE));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CORNER);
    }

    // ================================================================ Plaatsen, ook in een hoek

    /** Welke hoek een tweede paneel maakt dat naar {@code newFacing} kijkt, bij een paneel dat naar {@code facing} kijkt. */
    public static Corner cornerFor(Direction facing, Direction newFacing) {
        if (newFacing == Pegboard.right(facing)) {
            return Corner.LEFT;
        }
        if (newFacing == Pegboard.right(facing).getOpposite()) {
            return Corner.RIGHT;
        }
        return Corner.NONE;
    }

    /**
     * Tegen de muur waar je op klikt; klik je op een vloer of plafond, dan naar jou toe. Hangt er in dit blok al een
     * paneel tegen de andere muur van een hoek, dan komt dit erbij (hoofdstuk 70).
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockState here = context.getLevel().getBlockState(context.getClickedPos());
        if (here.is(this)) {
            Corner corner = cornerFor(here.getValue(FACING), face);
            if (corner == Corner.NONE || here.getValue(CORNER) != Corner.NONE) {
                return null;
            }
            BlockState joined = here.setValue(CORNER, corner);
            if (!Pegboard.cornerFree(context.getLevel(), context.getClickedPos(), joined)) {
                if (context.getPlayer() instanceof ServerPlayer player) {
                    player.displayClientMessage(Component.translatable("armory.imttech.pegboard.corner_blocked")
                            .withStyle(ChatFormatting.RED), true);
                }
                return null;
            }
            return joined;
        }
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    /**
     * Mag een tweede Pegboard in dit blok? Alleen als je met een Pegboard op de andere muur van de hoek klikt (dan is
     * "hier" dit blok, zoals bij een halve plaat). Klik je op het paneel zelf, dan komt het nieuwe ervoor, zoals altijd.
     */
    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        if (!context.getItemInHand().is(asItem()) || state.getValue(CORNER) != Corner.NONE) {
            return false;
        }
        // Op de muur ernaast geklikt: de plek waar het nieuwe paneel komt is precies dit blok.
        if (context.getLevel().getBlockState(context.getClickedPos()) != state) {
            return false;
        }
        return cornerFor(state.getValue(FACING), context.getClickedFace()) != Corner.NONE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = SHAPES.get(state.getValue(FACING));
        Direction side = Pegboard.sideFacing(state);
        return side == null ? shape : Shapes.or(shape, SHAPES.get(side));
    }

    /** Gespiegeld (een structuur): de tweede muur van een hoek komt aan de andere kant. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        BlockState turned = super.mirror(state, mirror);
        if (mirror == Mirror.NONE) {
            return turned;
        }
        return switch (state.getValue(CORNER)) {
            case LEFT -> turned.setValue(CORNER, Corner.RIGHT);
            case RIGHT -> turned.setValue(CORNER, Corner.LEFT);
            case NONE -> turned;
        };
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PegboardBlockEntity(pos, state);
    }

    // ================================================================ Welke muur, en dichtbij genoeg

    /** Op welk paneel je klikte: in een hoek het eerste of het tweede (welk vlak je raakte, of anders het dichtstbijzijnde). */
    public static Direction wallOf(BlockState state, BlockHitResult hit) {
        Direction facing = state.getValue(FACING);
        Direction side = Pegboard.sideFacing(state);
        if (side == null || hit.getDirection() == facing) {
            return facing;
        }
        if (hit.getDirection() == side) {
            return side;
        }
        Vec3 local = hit.getLocation().subtract(Vec3.atLowerCornerOf(hit.getBlockPos()));
        return fromWall(local, facing) <= fromWall(local, side) ? facing : side;
    }

    /** Hoe ver dit punt (0 tot 1 in het blok) van de muur ligt waar een paneel dat naar {@code wall} kijkt tegenaan hangt. */
    private static double fromWall(Vec3 local, Direction wall) {
        return switch (wall) {
            case SOUTH -> local.z;
            case NORTH -> 1 - local.z;
            case EAST -> local.x;
            default -> 1 - local.x;
        };
    }

    private static void tooFar(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.displayClientMessage(Component.translatable("armory.imttech.pegboard.too_far")
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    // ================================================================ Klikken

    /**
     * Rechtsklik met iets in je hand: ophangen als het mag (en je dichtbij genoeg staat), anders doet het item wat het
     * altijd doet. Te ver weg met iets dat erop mag: een melding, en verder niets (geen granaat tegen je eigen wand).
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // lege hand: zie useWithoutItem
        }
        if (hand != InteractionHand.MAIN_HAND || !Pegboard.hangable(stack)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (!Pegboard.inReach(player, hit.getLocation())) {
            tooFar(player);
            return ItemInteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            hang(serverPlayer, pos, wallOf(state, hit), hit, stack);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Rechtsklik met een lege hand: pakken, of met sneak draaien. Op een schaduw: zeggen wat het is. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Direction wall = wallOf(state, hit);
        double[] cell = Pegboard.aim(player, pos, wall, hit.getLocation(), Pegboard.BACK + 1.5);
        Pegboard.Spot spot = Pegboard.itemAt(level, pos, wall, cell[0], cell[1]);
        if (spot == null) {
            return InteractionResult.PASS;
        }
        if (!Pegboard.inReach(player, hit.getLocation())) {
            tooFar(player);
            return InteractionResult.CONSUME;
        }
        if (spot.shadow()) {
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.displayClientMessage(Component.translatable("armory.imttech.pegboard.shadow_hint",
                        spot.hung().stack().getHoverName()).withStyle(ChatFormatting.GRAY), true);
            }
            return InteractionResult.CONSUME;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            if (player.isSecondaryUseActive()) {
                turn(serverPlayer, pos, wall, spot);
            } else {
                take(serverPlayer, spot);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Ophangen: één stuk uit je hand, met zijn midden op de klik (of het dichtstbijzijnde vrije plekje). */
    public static boolean hang(ServerPlayer player, BlockPos pos, Direction wall, BlockHitResult hit, ItemStack stack) {
        Level level = player.level();
        double[] cell = Pegboard.aim(player, pos, wall, hit.getLocation(), Pegboard.BACK);
        return hangAt(player, level, pos, wall, cell[0], cell[1], stack, Pegboard.Turn.LEFT);
    }

    /**
     * Ophangen op gaatje (U, V) van de wand (ook voor de tests). Ligt daar de schaduw van een vastgezet item van hetzelfde
     * soort, dan komt het precies daar en zoals het hing (hoofdstuk 70). Geeft false (met een melding) als er geen plek is.
     */
    public static boolean hangAt(@Nullable ServerPlayer player, Level level, BlockPos pos, Direction wall, double cu, double cv,
                                 ItemStack stack, Pegboard.Turn turn) {
        Pegboard.Spot shadow = Pegboard.shadowFor(level, pos, wall, cu, cv, stack);
        if (shadow != null) {
            shadow.board().fill(shadow.index(), stack);
            used(player, stack);
            level.playSound(null, shadow.board().getBlockPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8f, 0.8f);
            return true;
        }
        int[] size = Pegboard.size(stack, turn);
        int[] at = Pegboard.place(level, pos, wall, cu, cv, size[0], size[1], null);
        if (at == null) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("armory.imttech.pegboard.no_room")
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        BlockPos anchor = Pegboard.boardAt(pos, wall, at[0], at[1]);
        if (!(level.getBlockEntity(anchor) instanceof PegboardBlockEntity board)) {
            return false;
        }
        board.add(stack, wall, at[0], at[1], turn, false);
        used(player, stack);
        level.playSound(null, anchor, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8f, 0.8f);
        return true;
    }

    /** Eén stuk uit je hand (in creative blijft het). */
    private static void used(@Nullable ServerPlayer player, ItemStack stack) {
        if (player == null || !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
    }

    /** Pakken: in je (lege) hand. Een vastgezet item laat zijn schaduw achter. */
    private static void take(ServerPlayer player, Pegboard.Spot spot) {
        PegboardBlockEntity board = spot.board();
        ItemStack stack = board.take(spot.index());
        if (player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        } else {
            AmmoBoxes.giveBesideHand(player, stack);
        }
        player.level().playSound(null, board.getBlockPos(), SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8f, 0.8f);
    }

    /** Draaien: de volgende stand, met hetzelfde midden (of zo dicht mogelijk erbij). */
    public static boolean turn(@Nullable ServerPlayer player, BlockPos pos, Direction wall, Pegboard.Spot spot) {
        Level level = spot.board().getLevel();
        if (level == null || spot.shadow()) {
            return false;
        }
        PegboardBlockEntity.Hung hung = spot.hung();
        double cu = spot.u() + spot.w() / 2.0;
        double cv = spot.v() + spot.h() / 2.0;
        // De volgende stand die past: een sniper op één rij panelen (10 gaatjes hoog past niet) gaat dan van links naar
        // rechts en weer terug, zonder melding.
        Pegboard.Turn next = hung.turn();
        int[] at = null;
        for (int tries = 0; tries < Pegboard.Turn.values().length - 1 && at == null; tries++) {
            next = next.next();
            int[] size = Pegboard.size(hung.stack(), next);
            at = Pegboard.place(level, pos, wall, cu, cv, size[0], size[1], spot);
        }
        if (at == null) {
            if (player != null) {
                player.displayClientMessage(Component.translatable("armory.imttech.pegboard.no_room_turn")
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        BlockPos anchor = Pegboard.boardAt(pos, wall, at[0], at[1]);
        if (anchor.equals(spot.board().getBlockPos())) {
            spot.board().turn(spot.index(), at[0], at[1], next);
        } else if (level.getBlockEntity(anchor) instanceof PegboardBlockEntity other) {
            // Het gaatje linksonder ligt nu op een ander paneel: daar hoort het wapen voortaan bij.
            spot.board().moveTo(spot.index(), other, at[0], at[1], next);
        } else {
            return false;
        }
        level.playSound(null, anchor, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.8f, 1.0f);
        return true;
    }

    // ================================================================ Vastzetten (hoofdstuk 70)

    /**
     * Sneak + linksklik uit je spel (client/armory/PegboardLock): je keek naar gaatje (U, V) op muur {@code wall} van dit
     * blok. De server kijkt zelf na of daar een Pegboard hangt en of je dichtbij genoeg bent.
     */
    public static void handleLock(ServerPlayer player, BlockPos pos, Direction wall, double cu, double cv) {
        Level level = player.level();
        if (!level.isLoaded(pos) || !Pegboard.isBoard(level, pos, wall)
                || !Pegboard.inReach(player, Pegboard.pointOf(pos, wall, cu, cv, Pegboard.BACK))) {
            return;
        }
        toggleLock(player, level, pos, wall, cu, cv);
    }

    /**
     * Vastzetten of los (jouw keuze: sneak + linksklik). Van een vastgezet item wordt de omtrek grijs op het paneel
     * geverfd (hoofdstuk 71, was een slotje); pak je het, dan blijft zijn schaduw op de plek en kan er alleen hetzelfde
     * soort op. Op een schaduw: weg ermee (de plek is weer vrij).
     * {@code player} mag null zijn (de tests): dan zonder melding.
     */
    public static boolean toggleLock(@Nullable ServerPlayer player, Level level, BlockPos pos, Direction wall, double cu, double cv) {
        Pegboard.Spot spot = Pegboard.itemAt(level, pos, wall, cu, cv);
        if (spot == null) {
            return false;
        }
        PegboardBlockEntity board = spot.board();
        PegboardBlockEntity.Hung hung = spot.hung();
        Component name = hung.stack().getHoverName();
        String message;
        boolean locking;
        if (hung.shadow()) {
            board.remove(spot.index());
            message = "armory.imttech.pegboard.shadow_removed";
            locking = false;
        } else {
            locking = !hung.locked();
            board.setLocked(spot.index(), locking);
            message = locking ? "armory.imttech.pegboard.locked" : "armory.imttech.pegboard.unlocked";
        }
        if (player != null) {
            player.displayClientMessage(Component.translatable(message, name)
                    .withStyle(locking ? ChatFormatting.GOLD : ChatFormatting.GRAY), true);
        }
        level.playSound(null, board.getBlockPos(), locking ? SoundEvents.IRON_TRAPDOOR_CLOSE : SoundEvents.IRON_TRAPDOOR_OPEN,
                SoundSource.BLOCKS, 0.5f, 1.7f);
        return true;
    }

    // ================================================================ Weghalen

    /** Weg: wat erop hing valt eraf (schaduwen verdwijnen), en ook wat van de buren over dit paneel hing. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof PegboardBlockEntity board) {
                board.dropAll(level);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
            if (!level.isClientSide) {
                Direction side = Pegboard.sideFacing(state);
                for (Direction wall : side == null ? new Direction[]{state.getValue(FACING)}
                        : new Direction[]{state.getValue(FACING), side}) {
                    for (int du = -Pegboard.REACH; du <= 0; du++) {
                        for (int dv = -Pegboard.REACH; dv <= 0; dv++) {
                            BlockPos other = Pegboard.offset(pos, wall, du, dv);
                            if ((du != 0 || dv != 0) && Pegboard.isBoard(level, other, wall)
                                    && level.getBlockEntity(other) instanceof PegboardBlockEntity neighbour) {
                                neighbour.dropLoose(level);
                            }
                        }
                    }
                }
            }
            return;
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
