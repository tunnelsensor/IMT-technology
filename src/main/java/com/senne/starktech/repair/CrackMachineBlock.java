package com.senne.starktech.repair;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.starktech.energy.PowerConnectable;
import com.senne.starktech.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Een machine voor gebarsten blokken (hoofdstuk 11, jouw keuze: "twee machines op stroom"). Elk heeft
 * een vakje erin, een vakje eruit en een balkje hoe ver hij is (zie {@link CrackMachineBlockEntity}):
 * <ul>
 *   <li>de <b>Crusher</b> maakt van een gebarsten blok poeder;</li>
 *   <li>de <b>Compactor</b> perst dat poeder tot een volledig nieuw, sterk blok (jouw wens: "die kun
 *       je compacten tot een volledig nieuwe sterke blok").</li>
 * </ul>
 * Telkens één op één: 1 gebarsten blok = 1 poeder = 1 nieuw blok (mijn invulling).
 * Ze werken op Stark-stroom (via kabels, of direct tegen een generator aan) en trechters kunnen erin
 * doen (van boven of opzij) en eruit halen (van onder), zoals bij een oven. De voorkant kijkt naar wie
 * hem neerzet, en verandert als hij werkt.
 */
public class CrackMachineBlock extends HorizontalDirectionalBlock implements EntityBlock, PowerConnectable {
    public static final MapCodec<CrackMachineBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            StringRepresentable.fromEnum(Kind::values).fieldOf("kind").forGetter(CrackMachineBlock::kind),
            propertiesCodec()).apply(instance, CrackMachineBlock::new));

    /** Is hij nu bezig? Dan draait zijn voorkant (zie de blockstate). */
    public static final BooleanProperty WORKING = BooleanProperty.create("working");

    /** Welke machine het is, en wat hij van wat maakt. */
    public enum Kind implements StringRepresentable {
        /** Gebarsten blok -> poeder van hetzelfde blok. */
        CRUSHER("crusher"),
        /** Poeder -> een nieuw blok (zonder barsten, nooit geplamuurd). */
        COMPACTOR("compactor");

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** Wat deze machine maakt van één {@code input}, of EMPTY als hij er niets mee kan. */
        public ItemStack convert(ItemStack input) {
            Block block = FromBlockItem.block(input);
            if (block == null) {
                return ItemStack.EMPTY;
            }
            return switch (this) {
                case CRUSHER -> input.is(ModItems.CRACKED_BLOCK.get())
                        ? FromBlockItem.of(ModItems.BLOCK_POWDER.get(), block, 1) : ItemStack.EMPTY;
                case COMPACTOR -> input.is(ModItems.BLOCK_POWDER.get()) ? new ItemStack(block) : ItemStack.EMPTY;
            };
        }

        /** Het stille geluidje terwijl hij werkt (elke seconde): een slijpsteen, of een zuiger. */
        public SoundEvent workSound() {
            return switch (this) {
                case CRUSHER -> SoundEvents.GRINDSTONE_USE;
                case COMPACTOR -> SoundEvents.PISTON_EXTEND;
            };
        }

        /**
         * Het geluid als er een voorwerp van {@code block} klaar is: het blok dat breekt (Crusher) of dat
         * neergezet wordt (Compactor). Zo klinkt glas anders dan steen.
         */
        public SoundEvent doneSound(Block block) {
            net.minecraft.world.level.block.SoundType sound = block.defaultBlockState().getSoundType();
            return switch (this) {
                case CRUSHER -> sound.getBreakSound();
                case COMPACTOR -> sound.getPlaceSound();
            };
        }

        /** Een voorbeeld van wat erin hoort (vaag in het lege vakje van het scherm). */
        public ItemStack example() {
            return switch (this) {
                case CRUSHER -> FromBlockItem.of(ModItems.CRACKED_BLOCK.get(), Blocks.STONE_BRICKS, 1);
                case COMPACTOR -> FromBlockItem.of(ModItems.BLOCK_POWDER.get(), Blocks.STONE_BRICKS, 1);
            };
        }
    }

    private final Kind kind;

    public CrackMachineBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH).setValue(WORKING, false));
    }

    public Kind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WORKING);
    }

    /** De voorkant kijkt naar wie hem neerzet. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // ================================================================ Gebruiken

    /** Rechtsklik: zijn scherm (met welke machine het is erbij, voor het voorbeeld in het lege vakje). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof CrackMachineBlockEntity machine) {
            serverPlayer.openMenu(machine, buf -> buf.writeVarInt(kind.ordinal()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Afgebroken: wat erin zit, valt eruit. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof CrackMachineBlockEntity machine) {
            Containers.dropContents(level, pos, machine);
            level.updateNeighbourForOutputSignal(pos, this);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Een comparator ernaast leest hoe vol hij zit (zoals bij een oven). */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return net.minecraft.world.inventory.AbstractContainerMenu.getRedstoneSignalFromBlockEntity(level.getBlockEntity(pos));
    }

    // ================================================================ Binnenkant

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CrackMachineBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof CrackMachineBlockEntity machine && tickLevel instanceof ServerLevel serverLevel) {
                machine.serverTick(serverLevel, pos, tickState);
            }
        };
    }

    /** Hoort dit voorwerp in deze machine? (Voor de trechters en het vakje.) */
    public static boolean accepts(Kind kind, ItemStack stack) {
        return !kind.convert(stack).isEmpty();
    }
}
