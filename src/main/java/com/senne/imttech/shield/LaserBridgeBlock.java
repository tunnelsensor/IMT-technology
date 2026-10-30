package com.senne.imttech.shield;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerConnectable;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * De laserbrug (hoofdstuk 34, 25 oktober): een zender die een vloertje van licht uitschuift waar je over loopt.
 * Jouw wens: "dit is basically hetzelfde als een rayshield, maar het doet geen damage en is enkel horizontaal".
 *
 * Zo gebruik je hem: zet de zender IN de rand van de vloer, met je gezicht naar het gat. De brug gaat de kant op
 * waar jij keek toen je hem neerzette, en altijd opzij (noord, oost, zuid of west; nooit omhoog). De plaat ligt een
 * klein beetje onder de bovenkant van het blok (jouw keuze: "tussen rand en midden, dichter bij de rand"), even
 * hoog als zielenzand: je loopt er vanaf de vloer gewoon op en weer af. Zie LaserBridgePlateBlock.
 *
 * Verder werkt hij net als de laserzender van de rayshield (LaserEmitterBlock):
 * <ul>
 *   <li>aan en uit met redstone (of omgekeerd: redstone aan = brug weg);</li>
 *   <li>hij heeft STROOM nodig (LASER_BRIDGE_UPKEEP per tick zolang de brug er ligt); is de stroom op, dan valt
 *       de brug weg, en komt hij vanzelf terug als er weer stroom is;</li>
 *   <li>zenders die tegen elkaar aan zitten, zijn samen één brug: drie naast elkaar = een brug van drie breed, met
 *       één hendel en één kabel;</li>
 *   <li>de brug schuift uit de lens naar voren, tot LASER_BRIDGE_MAX_LENGTH blokken of tot er iets in de weg staat;
 *       een brugzender aan de overkant die terugkijkt, sluit hem netjes af;</li>
 *   <li>gewoon rechtsklikken doet niets (zo leg je makkelijk een kabel); sneak + rechtsklik met een lege hand opent
 *       het instelscherm (de kleur en omkeren), voor wie hem neerzette of wie in deze basis vertrouwd is.</li>
 * </ul>
 *
 * Anders dan de rayshield (jouw keuzes): de brug doet geen pijn, iedereen kan erop lopen (spelers, dieren, monsters),
 * en hij hoort niet bij de beveiliging: de schakelaar "Lasers" van de beveiligingskern laat hem met rust, en de
 * Bypass Kit werkt er niet op. Een val maak je zelf: zet hem uit (redstone) terwijl er iemand op staat.
 *
 * Hoofdstuk 38: met een netwerkkabel naar de desktop van een kern staat de brug in Devices (de groep LASER BRIDGES).
 * Zonder kabel werkt hij gewoon los, op stroom (jouw keuze). Brugzenders die tegen elkaar zitten, geven het netwerk aan
 * elkaar door: één kabel is genoeg voor de hele brug.
 */
public class LaserBridgeBlock extends Block implements PowerConnectable, com.senne.imttech.lan.NetworkConnectable, EntityBlock {
    public static final MapCodec<LaserBridgeBlock> CODEC = simpleCodec(LaserBridgeBlock::new);

    /** Waar de lens heen kijkt, en dus waar de brug heen loopt: altijd opzij (jouw wens: "enkel horizontaal"). */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /**
     * De kleur van de brug: dezelfde zes als bij de rayshield. Het is hetzelfde "etiket" als bij de laserzender, zodat
     * het instelscherm (client/shield/LaserEmitterScreen) hem bij allebei op dezelfde manier leest.
     */
    public static final EnumProperty<ShieldColor> COLOR = LaserEmitterBlock.COLOR;
    /** Omgekeerd: redstone aan = brug weg, en zonder redstone ligt hij er. */
    public static final BooleanProperty INVERTED = BlockStateProperties.INVERTED;

    public LaserBridgeBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(COLOR, ShieldColor.CYAN)
                .setValue(INVERTED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, COLOR, INVERTED);
    }

    /**
     * De brug gaat de kant op waar jij kijkt als je hem neerzet (alleen opzij: kijk je schuin naar beneden, dan telt
     * de kant waar je naartoe kijkt). Zo zet je hem in de rand van de vloer met je gezicht naar het gat, en loopt de
     * brug het gat over. (De rayshieldzender kijkt juist naar jou toe: die zet je tegen een muur.)
     *
     * Zet je hem tegen een brugzender aan, dan hoort hij bij die brug (zie group): dan neemt hij haar kleur en
     * omkeren over, anders ging een omgekeerde brug half aan en half uit.
     */
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState placed = defaultBlockState().setValue(FACING, context.getHorizontalDirection());
        for (Direction direction : Direction.values()) {
            BlockState next = context.getLevel().getBlockState(context.getClickedPos().relative(direction));
            if (next.is(this)) {
                return placed.setValue(COLOR, next.getValue(COLOR)).setValue(INVERTED, next.getValue(INVERTED));
            }
        }
        return placed;
    }

    /** Wie hem neerzet, is de eigenaar (zie LaserBridgeBlockEntity). */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserBridgeBlockEntity(pos, state);
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // ---------------------------------------------------------------- Aan en uit

    /** De brugzenders die aan elkaar vastzitten (in welke richting ook): samen één brug. */
    public static List<BlockPos> group(Level level, BlockPos start) {
        return LaserEmitterBlock.touching(level, start, ModBlocks.LASER_BRIDGE.get());
    }

    /**
     * Moet deze brug er liggen? Er moet redstone op staan (of juist niet, als hij omgekeerd is), bij deze zender of
     * bij een zender van dezelfde brug. Anders dan de rayshield kijkt hij niet naar de beveiligingskern (jouw keuze):
     * de schakelaar "Lasers" laat bruggen met rust, anders haalde "lasers uit" ook je bruggen weg.
     */
    private static boolean wanted(Level level, BlockPos pos, BlockState state) {
        boolean signal = false;
        for (BlockPos member : group(level, pos)) {
            signal |= level.hasNeighborSignal(member);
        }
        return signal != state.getValue(INVERTED);
    }

    /**
     * Wil de brug van deze zender er liggen (ook als hij er door een stroomtekort niet ligt)? Voor de desktop: een brug
     * die aan wil maar uit staat, heeft geen stroom.
     */
    public static boolean wantsOn(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof LaserBridgeBlock && wanted(level, pos, state);
    }

    /**
     * Redstone erop of eraf (bij deze zender of een zender van dezelfde brug)? Dan de brug uitschuiven (als er stroom
     * is) of terugtrekken. Verandert deze zender, dan horen zijn buren dat vanzelf: zo gaat de hele brug mee.
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean movedByPiston) {
        if (level instanceof ServerLevel serverLevel) {
            update(serverLevel, pos, state, false);
        }
    }

    /** Net neergezet: wie dat deed, is de eigenaar. En als er al redstone op staat, meteen aan. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof LaserBridgeBlockEntity bridge) {
            bridge.setOwner(player);
        }
        if (level instanceof ServerLevel serverLevel) {
            update(serverLevel, pos, state, false);
        }
    }

    /** In de wereld gezet: over een seconde kijken of hij aan moet, of (stond hij al aan) zijn stroom betalen. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, UPKEEP_INTERVAL);
            PowerGrid.changed(); // brugzenders geven stroom aan elkaar door
        }
    }

    /** Zo vaak (ticks) betaalt hij zijn stroom, en kijkt hij of die er (weer) is. */
    private static final int UPKEEP_INTERVAL = 20;

    /**
     * Moet de brug er liggen of niet? Aan als er redstone op staat én er genoeg stroom is. Zonder stroom blijft hij
     * gewoon uit en kijkt hij elke twee seconden stil of er weer stroom is (net als de laserzender).
     *
     * @param retry true als dit zo'n stille herhaling is. Alleen als je de redstone net aanzet, klikt de zender waar
     *              de redstone op zit één keer ("geen stroom").
     */
    private void update(ServerLevel level, BlockPos pos, BlockState state, boolean retry) {
        boolean wanted = wanted(level, pos, state);
        boolean on = state.getValue(POWERED);
        if (!wanted && on) {
            setActive(level, pos, state, false);
        } else if (wanted && !on) {
            boolean enough = canStart(level, pos);
            if (!enough) {
                // Te weinig stroom om aan te gaan: dat telt als tekort op het net (twee seconden, tot de volgende
                // poging), zodat het scherm van een generator zegt dat er te weinig stroom is.
                PowerGrid.missed(level, pos, ImtConfig.Power.LASER_BRIDGE_UPKEEP * UPKEEP_INTERVAL * 2.0);
            }
            if (enough && pay(level, pos)) {
                setActive(level, pos, state, true);
            } else {
                if (!retry && level.hasNeighborSignal(pos) != state.getValue(INVERTED)
                        && !level.getBlockTicks().hasScheduledTick(pos, this)) {
                    noPower(level, pos);
                }
                if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
                    level.scheduleTick(pos, this, UPKEEP_INTERVAL * 2);
                }
            }
        }
    }

    /** Heeft het net genoeg stroom om de brug een paar seconden te laten liggen? */
    private static boolean canStart(ServerLevel level, BlockPos pos) {
        return PowerGrid.available(level, pos)
                >= ImtConfig.Power.LASER_BRIDGE_UPKEEP * UPKEEP_INTERVAL * ImtConfig.Power.LASER_BRIDGE_START_SECONDS;
    }

    /** Eén seconde stroom betalen. Lukt het niet, dan halen we niets weg. */
    private static boolean pay(ServerLevel level, BlockPos pos) {
        return PowerGrid.pay(level, pos, (double) ImtConfig.Power.LASER_BRIDGE_UPKEEP * UPKEEP_INTERVAL);
    }

    /** Geen stroom: een zacht droog klikje en een rookpluimpje bij de lens. */
    private static void noPower(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.3f, 1.6f);
        smoke(level, pos);
    }

    private static void smoke(ServerLevel level, BlockPos pos) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.2, 0.05, 0.2, 0.01);
    }

    /**
     * Elke seconde terwijl hij aan staat: stroom betalen, anders valt de brug weg. Staat hij uit terwijl er wel
     * redstone op staat (geen stroom), dan proberen we hem weer aan te zetten.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.getValue(POWERED)) {
            if (!wanted(level, pos, state)) {
                setActive(level, pos, state, false);
            } else if (pay(level, pos)) {
                level.scheduleTick(pos, this, UPKEEP_INTERVAL);
            } else {
                // De stroom is op: de brug trekt zich terug (dat geluid is genoeg), en daarna wacht hij stil.
                setActive(level, pos, state, false);
                smoke(level, pos);
                level.scheduleTick(pos, this, UPKEEP_INTERVAL * 2);
            }
        } else {
            update(level, pos, state, true);
        }
    }

    /** Vangnet, net als bij de laserzender: een zender zonder geplande tik krijgt er af en toe weer een. */
    @Override
    protected boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            tick(state, level, pos, random);
        }
    }

    /** Weggehaald: de brug verdwijnt mee. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && state.getValue(POWERED) && !newState.is(this)) {
            clearBridge((ServerLevel) level, pos, state);
        }
        if (!newState.is(this)) {
            PowerGrid.changed();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Gewoon rechtsklikken doet niets (zoals bij de laserzender), zodat je met een kabelrol in je hand gewoon een
     * kabel tegen de zender aan legt. Sneak + rechtsklik met een lege hand: het instelscherm, als je mag (zie
     * LaserBridges).
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            LaserBridges.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---------------------------------------------------------------- Instellen (uit het scherm)

    /** Een andere kleur: de zenders en de brug die er al ligt kleuren mee. */
    public static void applyColour(ServerLevel level, List<BlockPos> members, ShieldColor colour) {
        apply(level, members, state -> state.setValue(COLOR, colour), false);
        if (!members.isEmpty()) {
            level.playSound(null, members.get(0), SoundEvents.DYE_USE, SoundSource.BLOCKS, 0.6f, 1.2f);
        }
    }

    /** Omkeren (of terug): daarna gaan de zenders meteen aan of uit als dat nu anders moet. */
    public static void applyInverted(ServerLevel level, List<BlockPos> members, boolean inverted) {
        apply(level, members, state -> state.setValue(INVERTED, inverted), true);
    }

    /**
     * Verandert elke zender van de lijst, en de brug die hij nu maakt. Met {@code recheck} kijken we daarna pas of ze
     * aan of uit moeten: als alle zenders goed staan, zodat een brug niet half omschakelt.
     */
    private static void apply(ServerLevel level, List<BlockPos> members,
                              java.util.function.UnaryOperator<BlockState> change, boolean recheck) {
        for (BlockPos member : members) {
            BlockState state = level.getBlockState(member);
            if (!state.is(ModBlocks.LASER_BRIDGE.get())) {
                continue;
            }
            BlockState changed = change.apply(state);
            if (changed != state) {
                level.setBlock(member, changed, Block.UPDATE_ALL);
                if (changed.getValue(POWERED)) {
                    recolourPlates(level, member, changed);
                }
            }
        }
        if (recheck) {
            for (BlockPos member : members) {
                BlockState state = level.getBlockState(member);
                if (state.getBlock() instanceof LaserBridgeBlock bridge) {
                    bridge.update(level, member, state, false);
                }
            }
        }
    }

    /** De platen van deze zender krijgen zijn kleur. */
    private static void recolourPlates(ServerLevel level, BlockPos pos, BlockState emitter) {
        for (BlockPos target : platePositions(level, pos, emitter.getValue(FACING))) {
            BlockState plate = level.getBlockState(target);
            if (plate.is(ModBlocks.LASER_BRIDGE_PLATE.get())) {
                level.setBlock(target, plate.setValue(LaserBridgePlateBlock.COLOR, emitter.getValue(COLOR)), Block.UPDATE_ALL);
            }
        }
    }

    // ---------------------------------------------------------------- De brug maken en weghalen

    /** Zet de zender aan of uit en schuift de brug uit (of trekt hem terug). */
    public static void setActive(ServerLevel level, BlockPos pos, BlockState state, boolean on) {
        level.setBlock(pos, state.setValue(POWERED, on), Block.UPDATE_ALL);
        if (on) {
            buildBridge(level, pos, state);
            level.scheduleTick(pos, state.getBlock(), UPKEEP_INTERVAL);
        } else {
            clearBridge(level, pos, state);
        }
        // Hetzelfde "vwoem" als de rayshield, een tikje hoger: een brug is lichter dan een wand.
        level.playSound(null, pos, on ? ModSounds.RAYSHIELD_ON.get() : ModSounds.RAYSHIELD_OFF.get(),
                SoundSource.BLOCKS, 0.8f, 1.15f);
    }

    /**
     * De brug schuift uit de lens naar voren, één plaat per tick (zie LaserAnimations). Ligt er aan de overkant een
     * brugzender die terugkijkt en al aan staat, dan heeft die de brug al gemaakt: dan doen we niets (anders schoven
     * er twee bruggen naar elkaar toe).
     */
    private static void buildBridge(ServerLevel level, BlockPos pos, BlockState emitter) {
        Direction facing = emitter.getValue(FACING);
        if (partnerIsOn(level, pos, facing)) {
            return;
        }
        BlockState plate = ModBlocks.LASER_BRIDGE_PLATE.get().defaultBlockState()
                .setValue(LaserBridgePlateBlock.COLOR, emitter.getValue(COLOR))
                .setValue(LaserBridgePlateBlock.AXIS, facing.getAxis());
        LaserAnimations.start(level, platePositions(level, pos, facing), plate, emitter.getValue(COLOR), true);
    }

    /**
     * En weer weg: de brug trekt zich terug de zender in, van het eind naar de zender (wie erop staat, valt). Houdt
     * de zender aan de overkant hem nog aan, dan blijft hij liggen (hij is van hen allebei).
     */
    private static void clearBridge(ServerLevel level, BlockPos pos, BlockState emitter) {
        Direction facing = emitter.getValue(FACING);
        if (partnerIsOn(level, pos, facing)) {
            return;
        }
        LaserAnimations.start(level, platePositions(level, pos, facing),
                ModBlocks.LASER_BRIDGE_PLATE.get().defaultBlockState(), emitter.getValue(COLOR), false);
    }

    /** Staat er aan het eind van de brug een brugzender die terugkijkt en die zelf aan staat? Dan delen ze de brug. */
    private static boolean partnerIsOn(ServerLevel level, BlockPos pos, Direction facing) {
        BlockPos end = pos.relative(facing, platePositions(level, pos, facing).size() + 1);
        BlockState partner = level.getBlockState(end);
        return partner.is(ModBlocks.LASER_BRIDGE.get()) && partner.getValue(FACING) == facing.getOpposite()
                && partner.getValue(POWERED);
    }

    /**
     * Welke plekken horen bij de brug van deze zender? We lopen vooruit zolang er lucht is, of een brugplaat die
     * dezelfde kant op loopt (dan ligt de brug er al). Al het andere houdt hem tegen: een blok, een brugzender aan de
     * overkant, of een andere brug die dwars loopt. Over zo'n kruising loopt hij dus niet heen: anders haalde hij bij
     * het uitgaan een stuk van die andere brug mee weg.
     */
    private static List<BlockPos> platePositions(ServerLevel level, BlockPos pos, Direction facing) {
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 1; i <= Math.round(ImtConfig.Shields.LASER_BRIDGE_MAX_LENGTH); i++) {
            BlockPos target = pos.relative(facing, i);
            BlockState state = level.getBlockState(target);
            boolean ours = state.is(ModBlocks.LASER_BRIDGE_PLATE.get())
                    && state.getValue(LaserBridgePlateBlock.AXIS) == facing.getAxis();
            if (!state.isAir() && !ours) {
                return positions;
            }
            positions.add(target);
        }
        return positions;
    }
}
