package com.senne.imttech.security;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * De beveiligingskern van de Biebia's Industries Tower: een serverkast op de beveiligingsverdieping.
 *
 * Hij beschermt de hele toren (zie SecurityCores): de beveiligingstorentjes en -drones vragen hem
 * wie een indringer is, en hij houdt de drones in de lucht. Zolang niemand hem gehackt heeft,
 * brandt zijn lampje rood en is iedereen een indringer; daarna groen, en werkt alles voor de
 * nieuwe eigenaar. Hij is heel sterk: slopen kan, maar duurt lang (en de drones schieten).
 *
 * Je kunt er ook zelf een bouwen voor je eigen basis: die is meteen van jou (zie setPlacedBy).
 */
public class SecurityCoreBlock extends BaseEntityBlock implements com.senne.imttech.energy.PowerConnectable,
        com.senne.imttech.lan.NetworkConnectable {
    public static final MapCodec<SecurityCoreBlock> CODEC = simpleCodec(SecurityCoreBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** Heeft iemand de toren overgenomen? (Groen lampje.) */
    public static final BooleanProperty CLAIMED = BooleanProperty.create("claimed");
    /**
     * Hoofdstuk 130: zit er een SD-kaart in? Dan zie je vooraan een kaartje in de gleuf. Dit staat in de blokstand (en
     * niet in iets dat het blok naar je spel stuurt): de kern bewaart je wachtwoord, je bestanden en je code, en daarvan
     * mag niets naar iemands spel.
     */
    public static final BooleanProperty CARD = BooleanProperty.create("card");

    public SecurityCoreBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CLAIMED, false)
                .setValue(CARD, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CLAIMED, CARD);
    }

    /**
     * Neerzetten. Vlak naast een andere kern mag (hoofdstuk 117); in de zone van een andere kern (ook zonder stroom) of
     * vlak bij een Biebia's Industries Tower lukt het niet (zie SecurityCores.placementProblem): je krijgt een melding en
     * houdt het item. Dat controleert alleen de server, die alle kernen en zones van de wereld kent.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Component problem = context.getLevel() instanceof ServerLevel level
                ? SecurityCores.placementProblem(level, context.getClickedPos()) : null;
        if (problem != null) {
            if (context.getPlayer() instanceof ServerPlayer player) {
                player.displayClientMessage(problem.copy().withStyle(ChatFormatting.RED), true);
                // Je spel dacht al dat het item weg was: laat het weer zien.
                player.inventoryMenu.sendAllDataToRemote();
            }
            return null;
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Zelf neergezet (jouw wens, 28 september: je eigen beveiliging bouwen): de kern is van jou,
     * krijgt meteen een systeem (IP, mappen) en vraagt je een wachtwoord te zetten.
     *
     * Was het een weggehakte kern (met een etiket, zie PackedCore), dan krijgt hij al zijn
     * instellingen terug, en blijft hij van wie hij was (zie SecurityCoreBlockEntity.unpack).
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel serverLevel) || !(level.getBlockEntity(pos) instanceof SecurityCoreBlockEntity core)) {
            return;
        }
        PackedCore packed = stack.get(ModDataComponents.PACKED_CORE.get());
        if (packed != null) {
            // Het etiket gaat eraf: de instellingen staan nu in de kern (in creative houd je dus een gewone kern over).
            stack.remove(ModDataComponents.PACKED_CORE.get());
            CompoundTag config = PackedCores.get(serverLevel).take(packed.id());
            if (config != null) {
                String placedBy = placer instanceof Player who ? who.getGameProfile().getName() : "?";
                core.unpack(serverLevel, config, placedBy);
                if (placer instanceof ServerPlayer player) {
                    boolean yours = player.getUUID().equals(core.ownerId());
                    player.sendSystemMessage(Component.translatable(yours ? "security.imttech.msg.core_restored"
                                    : "security.imttech.msg.core_not_yours", yours ? core.hostname() : core.ownerName(), core.ip())
                            .withStyle(yours ? ChatFormatting.AQUA : ChatFormatting.GOLD));
                }
                return;
            }
        }
        if (placer instanceof ServerPlayer player) {
            core.setOwner(player);
            core.ensureSystem(serverLevel);
            core.logEvent(com.senne.imttech.security.os.EventLog.Type.OWNER,
                    player.getGameProfile().getName() + " installed this core");
            player.sendSystemMessage(Component.translatable("security.imttech.msg.core_placed", core.ip())
                    .withStyle(ChatFormatting.AQUA));
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SecurityCoreBlockEntity(pos, state);
    }

    /**
     * Rechtsklik met een lege hand: de eigenaar opent de Security OS-desktop (zie SecurityOs). Wie de
     * beveiliging niet bezit, krijgt "access denied"; die neemt hem over met een Hacking Laptop
     * (HackingLaptopItem vangt het rechtsklikken mét laptop af vóór dit).
     */
    /**
     * Hoofdstuk 130: rechtsklik met een SD-kaart steekt hem erin; sneak met een lege hand haalt hem eruit (zie
     * SdCardSlots). Al de rest gaat zoals altijd: een lege hand (of iets anders in je hand) opent de desktop.
     * <p>
     * Minecraft roept dit ook aan met een lege hand, en in je eigen spel tegen een blok zonder gegevens: daar kijken we
     * dus alleen naar de blokstand ({@link #CARD}).
     */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                                  BlockPos pos, Player player,
                                                                  net.minecraft.world.InteractionHand hand,
                                                                  BlockHitResult hit) {
        if (stack.isEmpty()) {
            if (player.isSecondaryUseActive() && state.getValue(CARD)) {
                if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof SecurityCoreBlockEntity core) {
                    com.senne.imttech.sdcard.SdCardSlots.takeOut(serverPlayer, pos, core,
                            com.senne.imttech.sdcard.SdCardSlots.mayTakeOut(serverPlayer, core));
                }
                return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (hand != net.minecraft.world.InteractionHand.MAIN_HAND || !com.senne.imttech.sdcard.SdCardItem.isCard(stack)) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit); // zoals altijd: de desktop
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof SecurityCoreBlockEntity core) {
            com.senne.imttech.sdcard.SdCardSlots.insert(serverPlayer, pos, core, stack,
                    com.senne.imttech.sdcard.SdCardSlots.mayUse(serverPlayer, core));
        }
        return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer) {
            com.senne.imttech.security.os.SecurityOs.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Wat er uitvalt als de kern kapotgaat (weggehakt of opgeblazen): de kern, met alles erin (jouw
     * wens, 9 oktober: "zorg dat al de config bewaard wordt in de security block, zodat als je die
     * hakt niet alles weg is"). De loot table geeft de kern; hier krijgt hij zijn etiket (zie pack).
     */
    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        List<ItemStack> drops = super.getDrops(state, params);
        if (params.getOptionalParameter(LootContextParams.BLOCK_ENTITY) instanceof SecurityCoreBlockEntity core) {
            for (ItemStack drop : drops) {
                if (drop.is(asItem())) {
                    PackedCore packed = core.pack(params.getLevel());
                    if (packed != null) {
                        drop.set(ModDataComponents.PACKED_CORE.get(), packed);
                    }
                    break;
                }
            }
        }
        return drops;
    }

    /**
     * In creative valt er normaal niets uit als je een blok breekt. Heeft de kern instellingen, dan
     * toch (net als een volle shulkerdoos): anders was alles met één klik weg.
     */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (level instanceof ServerLevel serverLevel && player.isCreative()
                && level.getBlockEntity(pos) instanceof SecurityCoreBlockEntity core) {
            PackedCore packed = core.pack(serverLevel);
            if (packed != null) {
                ItemStack stack = new ItemStack(this);
                stack.set(ModDataComponents.PACKED_CORE.get(), packed);
                ItemEntity drop = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                drop.setDefaultPickUpDelay();
                level.addFreshEntity(drop);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** Neergezet: de netwerken in de buurt kunnen nu bij deze kern uitkomen (hoofdstuk 38). */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            com.senne.imttech.lan.LanGrid.changed();
        }
    }

    /** Afgebroken (niet alleen een ander lampje): het IP van de kern is weer vrij, en zijn netwerk hangt nergens meer aan. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof SecurityCoreBlockEntity core) {
            core.releaseSystem(serverLevel);
            com.senne.imttech.logic.LogicRunner.stop(serverLevel, pos);
            // Hoofdstuk 130: de SD-kaart valt eruit (ook in creative, bij een ontploffing en met /setblock: één keer).
            com.senne.imttech.sdcard.SdCardSlots.dropOnBreak(level, pos, core);
        }
        if (!state.is(newState.getBlock())) {
            com.senne.imttech.lan.LanGrid.changed();
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                            BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof SecurityCoreBlockEntity core && tickLevel instanceof ServerLevel serverLevel) {
                core.serverTick(serverLevel, pos);
            }
        };
    }
}
