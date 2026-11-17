package com.senne.imttech.alarm;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerConnectable;
import com.senne.imttech.lan.DevicePower;
import com.senne.imttech.lan.LanGrid;
import com.senne.imttech.lan.NetworkConnectable;
import com.senne.imttech.redstone.DeviceNames;
import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityCores;
import com.senne.imttech.security.SecurityDevices;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
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
 * De Speaker (hoofdstuk 126 als "Alarm Speaker", jouw wens van 16 november: "Voeg een nieuwe blok toe namelijk een alarm
 * waar je zelf een mp4 bestand naar kunt uploaden zodat je custom alarmen kunt hebben"; sinds hoofdstuk 136 "Speaker"): een
 * luidspreker aan de muur, aan het plafond of op de vloer, met een rood zwaailicht.
 *
 * <ul>
 *   <li>Hij hoort bij de Security Core aan het andere eind van zijn netwerkkabel (of PoE-kabel, of draadloos via een
 *       Router), net als een camera, en heeft een beetje stroom nodig. Zonder kern of zonder stroom blijft hij stil (zijn
 *       groene lampje is dan uit: ONLINE = false).</li>
 *   <li><b>Hij speelt alleen wat een script zegt</b> (hoofdstuk 128 en 136): {@code playsound("siren", "Speaker 1")}
 *       speelt een geluid uit de map sounds in Files van de kern ("default" = de ingebouwde sirene),
 *       {@code playtext("Alarm armed", "all")} zegt een zin met de stem van I.M.T. Aan, uit en pulse op zijn naam
 *       werken niet meer (jouw keuze): hij is geen schakelaar maar een luidspreker. Wie hem neerzet, heeft zijn naam wel in
 *       zijn I.M.T-lijst ("Speaker 1"), zodat namen niet botsen en hernoemen overal klopt.</li>
 *   <li>Sneak + rechtsklik met een lege hand: zijn scherm (naam, hoe hard, hoe ver, Test), als je hem mag instellen (wie
 *       hem neerzette, of wie vertrouwd is in de basis: zie AlarmSpeakers.open). Gewoon rechtsklikken: hoe hij ervoor
 *       staat.</li>
 *   <li>Breek je hem af, dan houdt het item zijn naam en zijn instellingen.</li>
 * </ul>
 *
 * Het geluid zelf speelt je spel af (client/alarm/AlarmSpeakerSounds): de blok-entiteit zegt wat hij speelt.
 */
public class AlarmSpeakerBlock extends FaceAttachedHorizontalDirectionalBlock implements EntityBlock, PowerConnectable,
        NetworkConnectable, DevicePower.Powered {
    public static final MapCodec<AlarmSpeakerBlock> CODEC = simpleCodec(AlarmSpeakerBlock::new);

    /** Werkt hij (stroom, een kern, geen EMP)? Dan brandt zijn groene lampje. */
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");
    /** Speelt hij iets (een geluid of een zin)? Dan draait zijn rode licht. */
    public static final BooleanProperty SOUNDING = BooleanProperty.create("sounding");

    /** De standaardnaam in de I.M.T-lijst: "Speaker 1" (de sleutel heet nog zoals in hoofdstuk 126). */
    public static final String DEFAULT_NAME = "imt.imttech.alarm_speaker.default_name";

    /** De vorm (voor het aanklikken) per bevestiging en richting. */
    private static final Map<AttachFace, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(AttachFace.class);

    static {
        // Getekend voor een speaker die naar het noorden kijkt (de muur aan de zuidkant); de andere richtingen draaien we.
        // Precies zo groot als het model (tools/generate_alarm_assets.py): de plaat, het kastje en het zwaailicht.
        SHAPES.put(AttachFace.WALL, turned(2, 2, 10.5, 14, 15, 16));
        SHAPES.put(AttachFace.CEILING, turned(2, 6, 2, 14, 16, 14));
        SHAPES.put(AttachFace.FLOOR, turned(2, 0, 2, 14, 11, 14));
    }

    public AlarmSpeakerBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACE, AttachFace.WALL).setValue(FACING, Direction.NORTH)
                .setValue(ONLINE, false).setValue(SOUNDING, false));
    }

    @Override
    protected MapCodec<? extends FaceAttachedHorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACE, FACING, ONLINE, SOUNDING);
    }

    /** Hoeveel stroom hij om de twee seconden gebruikt (uit [power]); hij betaalt het zelf. */
    @Override
    public float upkeep() {
        return ImtConfig.Power.ALARM_SPEAKER_UPKEEP;
    }

    // ================================================================ Vorm

    /** Een doos voor een speaker die naar het noorden kijkt, en dezelfde doos gedraaid naar elke richting. */
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

    // ================================================================ Neerzetten en weghalen

    /**
     * Wie hem neerzet, is zijn eigenaar en heeft hem in zijn I.M.T-lijst ("Speaker 1", of de naam van het item). De
     * instellingen van het item heeft de blok-entiteit al overgenomen (zie AlarmSettings).
     */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!(level instanceof ServerLevel server) || !(level.getBlockEntity(pos) instanceof AlarmSpeakerBlockEntity speaker)) {
            return;
        }
        if (stack.has(DataComponents.CUSTOM_NAME)) {
            speaker.setName(stack.getHoverName().getString());
        }
        if (placer instanceof ServerPlayer player) {
            speaker.setOwner(player);
            if (speaker.name().isEmpty()) {
                DeviceNames.register(player, List.of(pos), pos, DEFAULT_NAME);
                speaker.setName(DeviceNames.nameOf(player, List.of(pos)));
            } else {
                DeviceNames.rename(player, List.of(pos), pos, speaker.name(), DEFAULT_NAME);
            }
        }
        speaker.updateOnline(server);
        forgetDevices(server, pos);
    }

    /** Neergezet (ook zonder speler): het netwerk van de kern is anders. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            LanGrid.changed();
        }
    }

    /** Weggehaald: ook uit de I.M.T-lijst, en het netwerk van de kern is anders. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel server) {
            forgetDevices(server, pos);
            ImtRedstone.forget(server, pos);
            LanGrid.changed();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** De kern van deze speaker telt zijn apparaten opnieuw (zo staat hij meteen in Devices, of is hij er meteen uit). */
    private static void forgetDevices(ServerLevel level, BlockPos pos) {
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
        if (core != null) {
            SecurityDevices.forget(level, core);
        }
    }

    // ================================================================ Aanklikken

    /**
     * Rechtsklik: hoe staat hij ervoor (stroom, bij welke kern, wat hij speelt)? Sneak + rechtsklik met een lege hand: zijn
     * scherm, als je hem mag instellen (zie AlarmSpeakers.open). Met iets in je hand en sneak slaat Minecraft dit zelf over.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) {
            if (player instanceof ServerPlayer serverPlayer) {
                AlarmSpeakers.open(serverPlayer, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof AlarmSpeakerBlockEntity speaker) {
            player.displayClientMessage(status(serverLevel, pos, state, speaker), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** De zin boven je hotbar: "Speaker 1: online | part of senne-sec | silent: playsound(...) in a diagram". */
    public static Component status(ServerLevel level, BlockPos pos, BlockState state, AlarmSpeakerBlockEntity speaker) {
        SecurityCoreBlockEntity core = SecurityCores.deviceCore(level, pos);
        boolean cable = LanGrid.required();
        Component status;
        if (state.getValue(SOUNDING)) {
            status = Component.translatable("security.imttech.alarm_speaker.sounding").withStyle(ChatFormatting.GOLD);
        } else if (state.getValue(ONLINE)) {
            status = Component.translatable("security.imttech.alarm_speaker.online").withStyle(ChatFormatting.GREEN);
        } else {
            status = Component.translatable(core == null && cable ? "security.imttech.alarm_speaker.offline"
                    : com.senne.imttech.weapons.Emp.isOut(level, pos) ? "security.imttech.alarm_speaker.emp"
                    : "security.imttech.alarm_speaker.no_power").withStyle(ChatFormatting.RED);
        }
        Component network = core == null
                ? Component.translatable(cable ? "security.imttech.alarm_speaker.no_cable" : "security.imttech.alarm_speaker.no_network")
                        .withStyle(ChatFormatting.YELLOW)
                : Component.translatable("security.imttech.alarm_speaker.network", core.hostname()).withStyle(ChatFormatting.AQUA);
        long now = level.getGameTime();
        Component sound = speaker.speakingAt(now)
                ? Component.translatable("security.imttech.alarm_speaker.saying", speaker.speechText()).withStyle(ChatFormatting.GRAY)
                : speaker.playingAt(now)
                ? Component.translatable(speaker.playKind() == AlarmSpeakerBlockEntity.PLAY_DEFAULT
                        ? "security.imttech.alarm_speaker.playing_default" : "security.imttech.alarm_speaker.playing")
                        .withStyle(ChatFormatting.GRAY)
                : Component.translatable("security.imttech.alarm_speaker.silent").withStyle(ChatFormatting.GRAY);
        String name = speaker.name().isEmpty() ? Component.translatable("block.imttech.alarm_speaker").getString() : speaker.name();
        return Component.translatable("security.imttech.alarm_speaker.status", name, status, network, sound);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.imttech.alarm_speaker").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.imttech.alarm_speaker.upload").withStyle(ChatFormatting.DARK_GRAY));
    }

    // ================================================================ Binnenkant

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AlarmSpeakerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof AlarmSpeakerBlockEntity speaker && tickLevel instanceof ServerLevel serverLevel) {
                speaker.serverTick(serverLevel, pos, tickState);
            }
        };
    }
}
