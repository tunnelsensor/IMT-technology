package com.senne.imttech.shield;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.PowerConnectable;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.registry.ModBlocks;
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
import net.minecraft.world.level.block.Blocks;
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
 * De laserzender: een energiewand die uit de lens schuift (zoals de "ray shields" uit Star
 * Wars). Handig als deur die je niet ziet aankomen, of als val in een gang. Eén zender is
 * genoeg; zet je er een tweede tegenover, dan sluit die de wand netjes af.
 *
 * Een volledig blok met een lens aan de voorkant. Aan en uit gaat met redstone: een hendel,
 * een drukplaat, of een I.M.T-relais ernaast (dan kan I.M.T hem toch schakelen).
 *
 * Gewoon rechtsklikken doet niets (13 oktober: zo leg je makkelijk een kabel tegen hem aan). Sneak +
 * rechtsklik met een lege hand opent zijn instelscherm, maar alleen voor wie hem neerzette of wie in deze
 * basis vertrouwd is (zie LaserEmitters). Daarin kies je de kleur, of hij omgekeerd werkt (redstone aan =
 * laser uit) en of de wand staat of ligt. Dat geldt voor de hele laserpoort.
 *
 * Hij heeft STROOM nodig: een stroomkabel naar een zonnepaneel of warmtegenerator (of er
 * direct tegenaan). Zolang de wand aan staat, betaalt hij elke seconde LASER_UPKEEP per tick.
 * Is de stroom op, dan valt de wand weg; komt de stroom terug terwijl de redstone nog aan
 * staat, dan gaat hij vanzelf weer aan.
 *
 * De wand loopt in de richting waarin de zender kijkt, tot maximaal LASER_MAX_LENGTH blokken,
 * en stopt bij de tegenoverliggende zender (of bij het eerste blok dat in de weg staat).
 *
 * Zenders die tegen elkaar aan zitten, zijn samen ÉÉN laserpoort (jouw wens, 27 september):
 * redstone op één ervan zet ze allemaal aan, en de stroom van een kabel loopt door de hele rij.
 * Zo bouw je een wand van 3 x 3 met één hendel en één kabel.
 */
public class LaserEmitterBlock extends Block implements PowerConnectable, EntityBlock {
    public static final MapCodec<LaserEmitterBlock> CODEC = simpleCodec(LaserEmitterBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    /** De kleur van het veld dat deze zender maakt. Kies je in zijn scherm. */
    public static final EnumProperty<ShieldColor> COLOR = EnumProperty.create("color", ShieldColor.class);
    /** Omgekeerd: redstone aan = laser uit, en zonder redstone staat hij aan (13 oktober). */
    public static final BooleanProperty INVERTED = BlockStateProperties.INVERTED;
    /**
     * Het veld een kwartslag gedraaid (13 oktober: "kiezen of de laser horizontaal is of verticaal").
     * Een wand die opzij loopt, ligt dan plat (zoals een vloer) in plaats van te staan; een wand die
     * omhoog of omlaag loopt (zenders in vloer en plafond), kijkt dan naar het westen in plaats van naar
     * het zuiden (hij loopt dan van noord naar zuid).
     */
    public static final BooleanProperty TURNED = BooleanProperty.create("turned");

    public LaserEmitterBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(POWERED, false)
                .setValue(COLOR, ShieldColor.BLUE)
                .setValue(INVERTED, false)
                .setValue(TURNED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, COLOR, INVERTED, TURNED);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        // Hij kijkt de kant op waar je hem tegenaan zet (dus de wand loopt van je af).
        Direction facing = context.getNearestLookingDirection().getOpposite();
        BlockState placed = defaultBlockState().setValue(FACING, facing);
        // In het plafond of de vloer (13 oktober: "facing west of south, zodat het er vanaf boven ook mooi
        // uitziet"): de wand komt dwars voor je te staan, zoals je in de gang kijkt. Kijk je naar het westen
        // of oosten, dan kijkt de wand naar het westen (TURNED); anders naar het zuiden.
        if (facing.getAxis() == Direction.Axis.Y) {
            placed = placed.setValue(TURNED, context.getHorizontalDirection().getAxis() == Direction.Axis.X);
        }
        // Zet je hem tegen een laserpoort aan, dan hoort hij erbij: dan neemt hij haar kleur en omkeren over
        // (anders ging een omgekeerde poort half aan en half uit), en liggend/staand als hij dezelfde kant op
        // schiet (een wand die omhoog loopt, draait anders dan een die opzij loopt).
        for (Direction direction : Direction.values()) {
            BlockState next = context.getLevel().getBlockState(context.getClickedPos().relative(direction));
            if (next.is(this)) {
                placed = placed.setValue(COLOR, next.getValue(COLOR)).setValue(INVERTED, next.getValue(INVERTED));
                if (next.getValue(FACING).getAxis() == facing.getAxis()) {
                    placed = placed.setValue(TURNED, next.getValue(TURNED));
                }
                return placed;
            }
        }
        return placed;
    }

    /** Wie hem neerzet, is de eigenaar (zie LaserEmitterBlockEntity). */
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserEmitterBlockEntity(pos, state);
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

    /** Zo veel zenders tellen we hooguit mee in één laserpoort (tegen eindeloos zoeken). */
    private static final int MAX_GROUP = 64;

    /**
     * De zenders die aan elkaar vastzitten (in welke richting ook), te beginnen bij {@code start}:
     * samen één laserpoort.
     */
    public static List<BlockPos> group(Level level, BlockPos start) {
        List<BlockPos> members = new ArrayList<>();
        java.util.Set<BlockPos> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<BlockPos> todo = new java.util.ArrayDeque<>();
        todo.add(start.immutable());
        seen.add(start.immutable());
        while (!todo.isEmpty() && members.size() < MAX_GROUP) {
            BlockPos pos = todo.poll();
            members.add(pos);
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (seen.add(next) && level.isLoaded(next) && level.getBlockState(next).is(ModBlocks.LASER_EMITTER.get())) {
                    todo.add(next);
                }
            }
        }
        return members;
    }

    /**
     * Moet deze poort aan? Er moet redstone op staan (of juist niet, als hij omgekeerd is), en de
     * beveiligingskern van dit gebied (als die er is) moet de lasers aan hebben staan (de schakelaar
     * "Lasers" op de desktop).
     */
    private static boolean wanted(Level level, BlockPos pos, BlockState state) {
        if (groupHasSignal(level, pos) == state.getValue(INVERTED)) {
            return false;
        }
        var core = com.senne.imttech.security.SecurityCores.coreAt(level, pos);
        return core == null || core.lasersEnabled();
    }

    /** Staat er redstone op een van de zenders van deze laserpoort? */
    private static boolean groupHasSignal(Level level, BlockPos pos) {
        for (BlockPos member : group(level, pos)) {
            if (level.hasNeighborSignal(member)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Redstone erop of eraf (bij deze zender of een zender van dezelfde poort)? Dan de wand aan
     * (als er stroom is) of uit. Verandert deze zender, dan horen zijn buren dat vanzelf, en die
     * kijken dan zelf ook weer: zo gaat de hele poort mee.
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
        if (placer instanceof ServerPlayer player && level.getBlockEntity(pos) instanceof LaserEmitterBlockEntity emitter) {
            emitter.setOwner(player);
        }
        if (level instanceof ServerLevel serverLevel) {
            update(serverLevel, pos, state, false);
        }
    }

    /**
     * In de wereld gezet (ook door een gebouw, zoals de Bibie's Industries Tower): over een seconde kijken
     * of hij aan moet (redstone en stroom), of, als hij al aan stond, zijn stroom betalen.
     */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, UPKEEP_INTERVAL);
            PowerGrid.changed(); // zenders geven stroom aan elkaar door
        }
    }

    /** Zo vaak (ticks) betaalt hij zijn stroom, en kijkt hij of die er (weer) is. */
    private static final int UPKEEP_INTERVAL = 20;

    /**
     * Moet de wand aan of uit? Aan als er redstone op staat én er genoeg stroom is. Zonder stroom
     * blijft hij gewoon uit en kijkt hij elke twee seconden stil of er weer stroom is.
     *
     * @param retry true als dit zo'n stille herhaling is. Alleen als je de redstone net aanzet,
     *              klikt de zender waar de redstone op zit één keer ("geen stroom"). Bij een
     *              omgekeerde poort klikt een zender zonder redstone (die wil dan juist aan).
     */
    private void update(ServerLevel level, BlockPos pos, BlockState state, boolean retry) {
        boolean wanted = wanted(level, pos, state);
        boolean on = state.getValue(POWERED);
        if (!wanted && on) {
            setActive(level, pos, state, false);
        } else if (wanted && !on) {
            boolean enough = canStart(level, pos);
            if (!enough) {
                // Te weinig stroom om aan te gaan: dat telt als tekort op het net (twee seconden, tot de
                // volgende poging), zodat het scherm van een generator zegt dat er te weinig stroom is.
                PowerGrid.missed(level, pos, ImtConfig.Power.LASER_WALL_UPKEEP * UPKEEP_INTERVAL * 2.0);
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

    /** Heeft het net genoeg stroom om de wand een paar seconden aan te houden? */
    private static boolean canStart(ServerLevel level, BlockPos pos) {
        return PowerGrid.available(level, pos) >= ImtConfig.Power.LASER_WALL_UPKEEP * UPKEEP_INTERVAL * ImtConfig.Power.LASER_WALL_START_SECONDS;
    }

    /** Eén seconde stroom betalen. Lukt het niet, dan halen we niets weg. */
    private static boolean pay(ServerLevel level, BlockPos pos) {
        return PowerGrid.pay(level, pos, (double) ImtConfig.Power.LASER_WALL_UPKEEP * UPKEEP_INTERVAL);
    }

    /** Geen stroom: een zacht droog klikje en een rookpluimpje bij de lens. */
    private static void noPower(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, 0.3f, 1.6f);
        smoke(level, pos);
    }

    private static void smoke(ServerLevel level, BlockPos pos) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.01);
    }

    /**
     * Elke seconde terwijl hij aan staat: stroom betalen, anders gaat hij uit. Staat hij uit terwijl
     * er wel redstone op staat (geen stroom), dan proberen we hem weer aan te zetten.
     */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        if (state.getValue(POWERED)) {
            if (!wanted(level, pos, state)) {
                setActive(level, pos, state, false);
            } else if (pay(level, pos)) {
                level.scheduleTick(pos, this, UPKEEP_INTERVAL);
            } else {
                // De stroom is op: de wand gaat uit (dat geluid is genoeg), en daarna wacht hij stil.
                setActive(level, pos, state, false);
                smoke(level, pos);
                level.scheduleTick(pos, this, UPKEEP_INTERVAL * 2);
            }
        } else {
            update(level, pos, state, true);
        }
    }

    /**
     * Vangnet: een zender die bij het maken van de wereld in een gebouw is gezet (zoals in de
     * Bibie's Industries Tower), of die aan stond in een oude wereld, heeft nog geen geplande tik. Af en toe
     * (willekeurig, gemiddeld om de minuut) kijken we of hij er een heeft.
     */
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

    /** Weggehaald: de wand verdwijnt mee. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide && state.getValue(POWERED) && !newState.is(this)) {
            clearWall((ServerLevel) level, pos, state.getValue(FACING));
        }
        if (!newState.is(this)) {
            PowerGrid.changed();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /**
     * Gewoon rechtsklikken doet niets (13 oktober), zodat je met een kabelrol in je hand gewoon tegen de
     * zender aan een kabel legt. Sneak + rechtsklik met een lege hand: zijn instelscherm, als je mag (zie
     * LaserEmitters). Met iets in je hand en sneak slaat Minecraft dit zelf over: dan gebruik je dat ding.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            LaserEmitters.open(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // ---------------------------------------------------------------- Instellen (uit het scherm)

    /** Een andere kleur: de zenders en de wand die er al staat kleuren mee. */
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

    /** Liggend of staand: een wand die er al staat, draait meteen mee. */
    public static void applyTurned(ServerLevel level, List<BlockPos> members, boolean turned) {
        apply(level, members, state -> state.setValue(TURNED, turned), false);
    }

    /**
     * Verandert elke zender van de lijst, en de wand die hij nu maakt. Met {@code recheck} kijken we daarna
     * pas of ze aan of uit moeten: als alle zenders goed staan, zodat een poort niet half omschakelt.
     */
    private static void apply(ServerLevel level, List<BlockPos> members,
                              java.util.function.UnaryOperator<BlockState> change, boolean recheck) {
        for (BlockPos member : members) {
            BlockState state = level.getBlockState(member);
            if (!state.is(ModBlocks.LASER_EMITTER.get())) {
                continue;
            }
            BlockState changed = change.apply(state);
            if (changed != state) {
                level.setBlock(member, changed, Block.UPDATE_ALL);
                if (changed.getValue(POWERED)) {
                    restyleWall(level, member, changed);
                }
            }
        }
        if (recheck) {
            for (BlockPos member : members) {
                BlockState state = level.getBlockState(member);
                if (state.getBlock() instanceof LaserEmitterBlock emitter) {
                    emitter.update(level, member, state, false);
                }
            }
        }
    }

    /** De wand van deze zender krijgt zijn kleur en liggend/staand. */
    private static void restyleWall(ServerLevel level, BlockPos pos, BlockState emitter) {
        for (BlockPos target : wallPositions(level, pos, emitter.getValue(FACING))) {
            BlockState wall = level.getBlockState(target);
            if (wall.is(ModBlocks.LASER_WALL.get())) {
                level.setBlock(target, wall.setValue(LaserWallBlock.COLOR, emitter.getValue(COLOR))
                        .setValue(LaserWallBlock.TURNED, emitter.getValue(TURNED)), Block.UPDATE_ALL);
            }
        }
    }

    // ---------------------------------------------------------------- De wand maken en weghalen

    /** Zet de zender aan of uit en maakt (of ruimt) de wand op. */
    public static void setActive(ServerLevel level, BlockPos pos, BlockState state, boolean on) {
        Direction facing = state.getValue(FACING);
        level.setBlock(pos, state.setValue(POWERED, on), Block.UPDATE_ALL);
        if (on) {
            buildWall(level, pos, facing);
            level.scheduleTick(pos, state.getBlock(), UPKEEP_INTERVAL);
        } else {
            clearWall(level, pos, facing);
        }
        // Star Wars-achtig (jouw wens, 7 oktober): "vwoem" bij aan, wegzakkend zoemen bij uit.
        level.playSound(null, pos, on ? com.senne.imttech.registry.ModSounds.RAYSHIELD_ON.get()
                : com.senne.imttech.registry.ModSounds.RAYSHIELD_OFF.get(), SoundSource.BLOCKS, 0.8f, 1.0f);
    }

    /**
     * Het veld schuift uit de zender naar voren (zie LaserAnimations). Je kunt er dus nog net
     * doorheen duiken als je snel bent.
     *
     * Staan er twee zenders tegenover elkaar die allebei aan zijn, dan bouwt alleen de eerste de
     * wand: anders schoven er twee wanden naar elkaar toe, en moet het van één kant komen.
     */
    private static void buildWall(ServerLevel level, BlockPos pos, Direction facing) {
        if (partnerIsOn(level, pos, facing)) {
            return; // de zender aan de overkant heeft de wand al gemaakt
        }
        BlockState state = level.getBlockState(pos);
        ShieldColor colour = state.hasProperty(COLOR) ? state.getValue(COLOR) : ShieldColor.BLUE;
        BlockState wall = ModBlocks.LASER_WALL.get().defaultBlockState()
                .setValue(LaserWallBlock.AXIS_FACING, facing)
                .setValue(LaserWallBlock.COLOR, colour)
                .setValue(LaserWallBlock.TURNED, state.hasProperty(TURNED) && state.getValue(TURNED));
        LaserAnimations.start(level, wallPositions(level, pos, facing), wall, colour, true);
    }

    /**
     * En weer open: het veld schuift terug de zender in. Houdt de zender aan de overkant hem nog
     * aan, dan blijft de wand staan (hij is van hen allebei).
     */
    private static void clearWall(ServerLevel level, BlockPos pos, Direction facing) {
        if (partnerIsOn(level, pos, facing)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        ShieldColor colour = state.hasProperty(COLOR) ? state.getValue(COLOR) : ShieldColor.BLUE;
        LaserAnimations.start(level, wallPositions(level, pos, facing),
                Blocks.AIR.defaultBlockState(), colour, false);
    }

    /**
     * Staat er aan het eind van de wand een zender die terugkijkt en die zelf aan staat? Dan
     * delen ze de wand.
     */
    private static boolean partnerIsOn(ServerLevel level, BlockPos pos, Direction facing) {
        BlockPos end = pos.relative(facing, wallPositions(level, pos, facing).size() + 1);
        BlockState partner = level.getBlockState(end);
        return partner.is(ModBlocks.LASER_EMITTER.get()) && partner.getValue(FACING) == facing.getOpposite()
                && partner.getValue(POWERED);
    }

    /**
     * Welke plekken horen bij de wand van deze zender? We lopen vooruit tot we iets tegenkomen
     * dat geen lucht (of laserwand) is; is dat een andere zender die terugkijkt, dan is de wand
     * "gesloten".
     */
    private static List<BlockPos> wallPositions(ServerLevel level, BlockPos pos, Direction facing) {
        List<BlockPos> positions = new ArrayList<>();
        for (int i = 1; i <= Math.round(ImtConfig.Shields.LASER_MAX_LENGTH); i++) {
            BlockPos target = pos.relative(facing, i);
            BlockState state = level.getBlockState(target);
            if (state.isAir() || state.is(ModBlocks.LASER_WALL.get())) {
                positions.add(target);
                continue;
            }
            // Een tegenoverliggende zender sluit de wand netjes af.
            if (state.is(ModBlocks.LASER_EMITTER.get()) && state.getValue(FACING) == facing.getOpposite()) {
                return positions;
            }
            // Iets anders in de weg: de wand stopt hier.
            return positions;
        }
        return positions;
    }
}
