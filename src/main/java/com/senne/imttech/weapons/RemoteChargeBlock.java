package com.senne.imttech.weapons;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.breach.BlastAlarm;
import com.senne.imttech.registry.ModSounds;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Een lading die je tegen een vloer, muur of plafond plakt en van ver laat afgaan met een trigger (hoofdstuk 35). Een
 * halve bol met een antenne. Er zijn twee soorten:
 * <ul>
 *   <li>de <b>Remote EMP</b> (jouw wens: "een remote getriggerde EMP ... zo kun je een hele powergrid uitdoen van op
 *       afstand"): gaat af met een gewone EMP-puls (zoals een granaat) als iemand op zijn EMP Trigger drukt. Iedereen
 *       kan hem er gewoon aftrekken (breken): dan valt hij eruit;</li>
 *   <li>de <b>Remote Frag</b> (26 oktober, jouw wens: "haal de C4 weg ... de plak-C4 met remote moet zijn zoals een
 *       halve sticky frag, maar dan met remote zoals de Remote EMP"): gaat af met een klap zo sterk als een Sticky Frag
 *       als iemand op zijn Frag Trigger drukt. Jouw keuze: "alarm en schaar, zoals C4". Met je hand krijg je hem er
 *       niet af, ook niet als je hem zelf plakte: alleen met een schaar knip je de draad door (dan valt hij eruit). Plakt
 *       iemand hem op de zone van een ander, dan gaat het alarm af (zie breach/BlastAlarm). Een explosie maakt hem niet
 *       kapot: dan gaat hij zelf af (een kettingreactie).</li>
 * </ul>
 * Plak je hem met de juiste trigger in je inventaris, dan hoort hij meteen bij die trigger (zie {@link TriggerItem});
 * anders koppel je hem met een rechtsklik met de trigger. Hij blijft zitten, ook als het blok erachter weg is.
 */
public class RemoteChargeBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock {
    /** De twee soorten. */
    public enum Kind { EMP, FRAG }

    public static final MapCodec<RemoteChargeBlock> EMP_CODEC = simpleCodec(properties -> new RemoteChargeBlock(Kind.EMP, properties));
    public static final MapCodec<RemoteChargeBlock> FRAG_CODEC = simpleCodec(properties -> new RemoteChargeBlock(Kind.FRAG, properties));

    /** De vorm (voor het aanklikken), per kant waar hij tegenaan plakt: een halve bol van 8 x 8, 5 hoog. */
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        SHAPES.put(Direction.DOWN, Block.box(4, 0, 4, 12, 5, 12));
        SHAPES.put(Direction.UP, Block.box(4, 11, 4, 12, 16, 12));
        SHAPES.put(Direction.NORTH, Block.box(4, 4, 0, 12, 12, 5));
        SHAPES.put(Direction.SOUTH, Block.box(4, 4, 11, 12, 12, 16));
        SHAPES.put(Direction.WEST, Block.box(0, 4, 4, 5, 12, 12));
        SHAPES.put(Direction.EAST, Block.box(11, 4, 4, 16, 12, 12));
    }

    private final Kind kind;

    public RemoteChargeBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.WALL).setValue(FACING, Direction.NORTH));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return kind == Kind.EMP ? EMP_CODEC : FRAG_CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING);
    }

    /** De kant die van het blok af wijst (de bolle kant). */
    public static Direction outward(BlockState state) {
        return getConnectedDirection(state);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(outward(state).getOpposite());
    }

    /** Plakkerig: hij blijft zitten, ook als het blok erachter verdwijnt. */
    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level,
                                     BlockPos pos, BlockPos neighbourPos) {
        return state;
    }

    // ================================================================ Rechtsklik met iets in je hand

    /** Een schaar op een Remote Frag: onschadelijk maken. Een trigger van dezelfde soort: koppelen. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (kind == Kind.FRAG && stack.is(Items.SHEARS)) {
            if (level instanceof ServerLevel serverLevel && player instanceof ServerPlayer serverPlayer
                    && level.getBlockEntity(pos) instanceof RemoteChargeBlockEntity charge) {
                defuse(serverLevel, pos, charge, serverPlayer);
                stack.hurtAndBreak(1, serverPlayer, LivingEntity.getSlotForHand(hand));
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!(stack.getItem() instanceof TriggerItem trigger) || trigger.kind() != kind) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof RemoteChargeBlockEntity charge) {
            TriggerData data = TriggerItem.ensure(stack);
            Component message;
            if (charge.trigger() != null && !charge.trigger().equals(data.id())) {
                message = Component.translatable(key("taken")).withStyle(ChatFormatting.YELLOW);
            } else if (TriggerItem.link(serverLevel, pos, charge, stack)) {
                message = Component.translatable(key("linked"), TriggerItem.ensure(stack).charges().size())
                        .withStyle(trigger.colour());
            } else {
                message = Component.translatable(key("full"), TriggerData.MAX_CHARGES).withStyle(ChatFormatting.YELLOW);
            }
            player.displayClientMessage(message, true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** De naam van een tekst voor deze soort: "item.imttech.remote_emp.linked" of "item.imttech.remote_frag.linked". */
    public String key(String what) {
        return "item.imttech.remote_" + (kind == Kind.EMP ? "emp" : "frag") + "." + what;
    }

    /**
     * De draad doorknippen: de Remote Frag valt eruit (wie hem onschadelijk maakt, krijgt hem), de markering van het
     * alarm gaat weg en wie hem plakte hoort het.
     */
    public void defuse(ServerLevel level, BlockPos pos, RemoteChargeBlockEntity charge, ServerPlayer by) {
        BlastAlarm.defused(level, pos, charge, by);
        level.removeBlock(pos, false);
        Block.popResource(level, pos, new ItemStack(asItem()));
        level.playSound(null, pos, ModSounds.REMOTE_FRAG_DEFUSE.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        by.displayClientMessage(Component.translatable("item.imttech.remote_frag.defused").withStyle(ChatFormatting.GREEN), true);
    }

    /** Linksklik op een Remote Frag: met je hand krijg je hem er niet af. Dat zeggen we dan ook. */
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START
                && !event.getLevel().isClientSide && !event.getEntity().isCreative()
                && event.getLevel().getBlockState(event.getPos()).getBlock() instanceof RemoteChargeBlock charge
                && charge.kind() == Kind.FRAG) {
            event.getEntity().displayClientMessage(Component.translatable("item.imttech.remote_frag.use_shears")
                    .withStyle(ChatFormatting.YELLOW), true);
        }
    }

    // ================================================================ Binnenkant

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RemoteChargeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof RemoteChargeBlockEntity charge && tickLevel instanceof ServerLevel serverLevel) {
                charge.serverTick(serverLevel, pos, tickState);
            }
        };
    }
}
