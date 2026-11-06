package com.senne.imttech.turret;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerConnectable;
import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.shield.ImtSwitchable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Een automatisch torentje.
 *
 * Het ROCKET-torentje zet je bij je basis. Het schiet op monsters en raiddrones die het kan zien
 * (zie TurretTargeting): met een laser die stroom van het net haalt (een stroomkabel naar een
 * zonnepaneel of warmtegenerator, of er direct tegenaan) en met raketten als je er munitie in
 * doet (rechtsklik met Rocket Ammo, of een trechter erboven). Aan en uit zet je het op zijn naam (hoofdstuk 39:
 * redstone doet niets meer): het staat in je I.M.T-lijst ("Turret 3"), dus met je bril, je stem, een diagram
 * ({@code off("Turret 3")}) of de knop in Devices op de desktop.
 *
 * De LASERTURRET (SECURITY) staat in de Biebia's Industries Tower en de bunker (van niemand: hij schiet op iedereen tot
 * je de kern hackt), of bij jou: hij hoort via zijn netwerkkabel bij een beveiligingskern en volgt haar regels.
 *
 * Het blok zelf is alleen de voet; de draaiende kop tekent TurretRenderer.
 */
public class TurretBlock extends BaseEntityBlock implements ImtSwitchable, PowerConnectable,
        com.senne.imttech.lan.NetworkConnectable {
    public static final MapCodec<TurretBlock> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TurretKind.CODEC.fieldOf("kind").forGetter(TurretBlock::kind),
            propertiesCodec()).apply(instance, TurretBlock::new));

    /** Staat het aan? (Uitzetten op zijn naam: je I.M.T-lijst, je stem, een diagram of de desktop.) */
    public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;
    /**
     * Heeft hij stroom van het net? Zonder stroom doet hij niets en hangt de loop omlaag. Dit
     * staat in het blok (niet alleen op de server), zodat jouw spel het ook weet.
     */
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");

    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 15, 14);

    private final TurretKind kind;

    public TurretBlock(TurretKind kind, BlockBehaviour.Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(ENABLED, true).setValue(ONLINE, false));
    }

    public TurretKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ENABLED, ONLINE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TurretBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide) {
            return (tickLevel, pos, tickState, blockEntity) -> {
                if (blockEntity instanceof TurretBlockEntity turret) {
                    turret.clientTick(tickLevel, tickState);
                }
            };
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof TurretBlockEntity turret && tickLevel instanceof ServerLevel serverLevel) {
                turret.serverTick(serverLevel, pos, tickState);
            }
        };
    }

    // ================================================================ Aan en uit (op zijn naam)

    @Override
    public void setSwitched(ServerLevel level, BlockPos pos, BlockState state, boolean on) {
        level.setBlock(pos, state.setValue(ENABLED, on), Block.UPDATE_ALL);
    }

    @Override
    public boolean isSwitchedOn(BlockState state) {
        return state.getValue(ENABLED);
    }

    // ================================================================ Neerzetten en gebruiken

    /**
     * Een Biebia's Industries-torentje is van wie het neerzet. Wie een raketturretje of (hoofdstuk 39) een
     * beveiligingstorentje neerzet, krijgt het in zijn I.M.T-lijst ("Turret 3"): zo schakel je het op zijn naam.
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(placer instanceof ServerPlayer player) || !(level.getBlockEntity(pos) instanceof TurretBlockEntity turret)) {
            return;
        }
        if (kind == TurretKind.ROCKET) {
            turret.setOwner(player);
        }
        ImtRedstone.register(player, pos, "imt.imttech.turret.default_name");
    }

    /** Rechtsklik met raketten: laad ze in het torentje. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.ROCKET_AMMO.get()) || !kind.usesAmmo()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TurretBlockEntity turret) {
            ItemStack rest = turret.ammo().insertItem(0, stack.copy(), false);
            int loaded = stack.getCount() - rest.getCount();
            if (!player.hasInfiniteMaterials()) {
                stack.shrink(loaded);
            }
            if (loaded > 0) {
                level.playSound(null, pos, SoundEvents.CROSSBOW_LOADING_END.value(), SoundSource.BLOCKS, 0.8f, 1.2f);
            }
            status(player, state, turret);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Rechtsklik met een lege hand: hoe staat het ervoor? Met shift: de raketten eruit halen. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof TurretBlockEntity turret) {
            boolean mayTake = turret.ownerId() == null || turret.ownerId().equals(player.getUUID());
            if (player.isShiftKeyDown() && mayTake && turret.ammoCount() > 0) {
                player.getInventory().placeItemBackInInventory(turret.ammo().extractItem(0, Math.round(ImtConfig.Turrets.MAX_AMMO), false));
            }
            status(player, state, turret);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Eén regel boven je werkbalk: aan of uit, stroom, raketten. */
    private void status(Player player, BlockState state, TurretBlockEntity turret) {
        Component onOff = TurretBlockEntity.isActive(state)
                ? Component.translatable("turret.imttech.status.online").withStyle(ChatFormatting.GREEN)
                : Component.translatable(!state.getValue(ENABLED) ? "turret.imttech.status.off_imt"
                        : "turret.imttech.status.off_power").withStyle(ChatFormatting.RED);
        if (kind == TurretKind.SECURITY) {
            // Bij welke kern hij hoort: die aan het andere eind van zijn netwerkkabel (hoofdstuk 38).
            var core = com.senne.imttech.security.SecurityCores.deviceCore(player.level(), turret.getBlockPos());
            Component tower = core == null && com.senne.imttech.lan.LanGrid.required()
                    ? Component.translatable("turret.imttech.status.no_cable").withStyle(ChatFormatting.YELLOW)
                    : core == null || core.ownerId() == null
                    ? Component.translatable("turret.imttech.status.unclaimed").withStyle(ChatFormatting.RED)
                    : Component.translatable("turret.imttech.status.owned", core.ownerName()).withStyle(ChatFormatting.AQUA);
            player.displayClientMessage(Component.translatable("turret.imttech.status.security", onOff, tower), true);
            return;
        }
        boolean power = com.senne.imttech.energy.PowerGrid.available(player.level(), turret.getBlockPos())
                >= ImtConfig.Power.TURRET_LASER_SHOT;
        Component laser = Component.translatable(power ? "turret.imttech.status.power" : "turret.imttech.status.no_power")
                .withStyle(power ? ChatFormatting.AQUA : ChatFormatting.YELLOW);
        player.displayClientMessage(Component.translatable("turret.imttech.status.rocket", onOff, laser,
                turret.ammoCount()), true);
    }

    /** Neergezet (ook zonder speler): het netwerk van de kern is anders, dus Devices kijkt meteen opnieuw (hoofdstuk 38). */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            com.senne.imttech.lan.LanGrid.changed();
        }
    }

    /** Afgebroken: de raketten vallen eruit, en het netwerk van de kern is anders. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof TurretBlockEntity turret) {
            Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), turret.ammo().getStackInSlot(0).copy());
        }
        if (!state.is(newState.getBlock())) {
            com.senne.imttech.lan.LanGrid.changed();
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
