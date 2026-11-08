package com.senne.imttech.logic;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerConnectable;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDevices;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Het serverrack (jouw wens, 7 oktober): een zwarte kast met vakjes voor CPU's, GPU's, RAM, SSD's en fans.
 *
 * Racks die tegen elkaar staan (naast of op elkaar), of aan de netwerkkabel van een beveiligingskern hangen, vormen samen
 * de server van die kern; op die server draaien je redstone-diagrammen en het brein van I.M.T. Hij heeft stroom nodig,
 * en geeft die door aan het rack ernaast: een kast vol racks heeft genoeg aan één kabel (zie PowerGrid).
 *
 * Hoofdstuk 64 (jouw wens van 8 november: "niet via een GUI maar echt klikken op het onderdeel zoals bij een boekenkast"):
 * het rack heeft geen scherm meer. De acht vakjes zitten op de voorkant (zie {@link RackBay}) en je ziet wat erin zit:
 * <ul>
 *   <li>rechtsklik met een onderdeel op een leeg vakje: het onderdeel gaat erin (als het daar mag: boven een CPU of GPU,
 *       dan RAM of een SSD, onderaan fans);</li>
 *   <li>rechtsklik op een vol vakje (met een lege hand of een onderdeel): het onderdeel komt eruit, in je rugzak;</li>
 *   <li>sneak + rechtsklik met een lege hand: het rack een naam geven (bijvoorbeeld "rack1"), als jij hem neerzette of
 *       in deze basis vertrouwd bent; onder die naam zie je hem in de app Monitor, met zijn eigen cijfers.</li>
 * </ul>
 * Met iets anders in je hand (een kabel) doet een klik wat dat voorwerp doet.
 */
public class ServerRackBlock extends HorizontalDirectionalBlock implements EntityBlock, PowerConnectable,
        com.senne.imttech.lan.NetworkConnectable {
    public static final MapCodec<ServerRackBlock> CODEC = simpleCodec(ServerRackBlock::new);

    /** Draait hij mee in de server (stroom, en hij hoort bij de server)? Dan knipperen zijn lampjes en draaien zijn fans. */
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");

    /** Wat er in elk van de acht vakjes zit (bay_0 tot bay_7, zie RackBay), zodat Minecraft het tekent. */
    public static final List<EnumProperty<RackBay>> BAYS = bays();

    private static List<EnumProperty<RackBay>> bays() {
        List<EnumProperty<RackBay>> bays = new ArrayList<>();
        for (int slot = 0; slot < ImtConfig.Logic.RACK_SLOTS; slot++) {
            bays.add(EnumProperty.create("bay_" + slot, RackBay.class, RackBay.allowed(slot)));
        }
        return List.copyOf(bays);
    }

    public ServerRackBlock(Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ONLINE, false);
        for (EnumProperty<RackBay> bay : BAYS) {
            state = state.setValue(bay, RackBay.NONE);
        }
        registerDefaultState(state);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ONLINE);
        for (EnumProperty<RackBay> bay : BAYS) {
            builder.add(bay);
        }
    }

    /** De voorkant (met de vakjes) kijkt naar wie hem neerzet. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /** Wie hem neerzet, mag hem een naam geven (zie security/BaseTrust). */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack) {
            rack.setOwner(player);
        }
    }

    // ================================================================ Klikken op een vakje

    /**
     * Op welk vakje van de voorkant deze klik viel (0 tot 7), of -1 als je op een andere kant klikte.
     */
    public static int bayAt(BlockState state, BlockHitResult hit) {
        Direction facing = state.getValue(FACING);
        if (hit.getDirection() != facing) {
            return -1;
        }
        BlockPos pos = hit.getBlockPos();
        Vec3 local = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        // Van links naar rechts zoals je voor het rack staat (zoals de boekenkast van Minecraft het rekent).
        double across = switch (facing) {
            case NORTH -> 1.0 - local.x;
            case SOUTH -> local.x;
            case WEST -> local.z;
            case EAST -> 1.0 - local.z;
            default -> 0.5;
        };
        return RackBay.slotAt(across * 16.0, (1.0 - local.y) * 16.0);
    }

    /** Wat er volgens de blokstaat in dit vakje zit. */
    public static RackBay bay(BlockState state, int slot) {
        return state.getValue(BAYS.get(slot));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        // Sneak met twee lege handen (anders komt Minecraft hier niet eens): de naam van het rack.
        if (player.isSecondaryUseActive()) {
            if (player instanceof ServerPlayer serverPlayer) {
                ServerRacks.openName(serverPlayer, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // een lege hand: zie useWithoutItem
        }
        if (!(stack.getItem() instanceof ServerPartItem part)) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION; // iets anders (een kabel): dat voorwerp beslist
        }
        int slot = bayAt(state, hit);
        if (slot < 0) {
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        }
        if (bay(state, slot) != RackBay.NONE) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION; // vol: eruit halen (zoals bij de boekenkast)
        }
        if (!RackBay.accepts(slot, part.kind())) {
            if (!level.isClientSide) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "block.imttech.server_rack.wrong_bay", stack.getHoverName(), RackBay.rowText(slot)), true);
            }
            return ItemInteractionResult.CONSUME;
        }
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack) {
            ServerRacks.insert(serverLevel, rack, slot, stack, player);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Een lege hand: een vol vakje geeft zijn onderdeel terug; een leeg vakje zegt wat erin mag. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int slot = bayAt(state, hit);
        if (slot < 0) {
            return InteractionResult.PASS;
        }
        if (bay(state, slot) == RackBay.NONE) {
            if (!level.isClientSide) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "block.imttech.server_rack.empty_bay", RackBay.rowText(slot)), true);
            }
            return InteractionResult.CONSUME;
        }
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack) {
            ServerRacks.takeOut(serverLevel, rack, slot, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ================================================================ Neerzetten en afbreken

    /** Een nieuw rack: de server telt meteen opnieuw (anders duurt het een paar seconden). */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!state.is(oldState.getBlock()) && level instanceof ServerLevel serverLevel) {
            recount(serverLevel, pos);
        }
    }

    /** Afgebroken: de onderdelen vallen eruit, en de server telt opnieuw. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack) {
                Containers.dropContents(level, pos, rack.parts());
                rack.dropOverflow(level);
            }
            super.onRemove(state, level, pos, newState, movedByPiston);
            if (level instanceof ServerLevel serverLevel) {
                recount(serverLevel, pos);
            }
            return;
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Laat de kern en het stroomnet opnieuw kijken welke racks er staan. */
    private static void recount(ServerLevel level, BlockPos pos) {
        PowerGrid.changed();
        LogicServer.forgetAll();
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
        if (core != null) {
            SecurityDevices.forget(level, core);
        }
    }

    // ================================================================ Binnenkant

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ServerRackBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ServerRackBlockEntity rack && tickLevel instanceof ServerLevel serverLevel) {
                rack.serverTick(serverLevel, pos, tickState);
            }
        };
    }
}
