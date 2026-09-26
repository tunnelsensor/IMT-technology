package com.senne.starktech.hydra;

import com.mojang.serialization.MapCodec;
import com.senne.starktech.registry.ModStructures;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * De Hydra-bunker: een geheime basis, uitgehakt in een berg.
 *
 * Gewone structuren (zoals het satellietwrak) zet Minecraft gewoon op de grond. Deze moet in
 * een berg, met de ingang in een rotswand. Daarom zoeken we zelf een goede plek:
 *
 * 1. We nemen het midden van het stuk wereld en kijken in alle vier de richtingen.
 * 2. Een richting is goed als de grond daar VOOR ons wegzakt (daar komt de ingang, met open
 *    lucht ervoor) en ACHTER ons hoog oprijst: over de hele lengte van de bunker moet de berg
 *    minstens COVER blokken boven de vloer uitkomen, anders steekt de bunker uit de berg.
 * 3. Dan draaien we de bunker zo dat de ingang die open kant op kijkt.
 *
 * Lukt het in geen enkele richting (vlak land, of een te kleine heuvel), dan komt hier geen
 * bunker. Daarom proberen we het vaak (zie structure_set/hydra_bunkers.json): de meeste plekken
 * in de bergen vallen af, een paar zijn goed.
 */
public class HydraBunkerStructure extends Structure {
    public static final MapCodec<HydraBunkerStructure> CODEC = simpleCodec(HydraBunkerStructure::new);

    /** Afmetingen van het bestand (zie tools/generate_bunker.py). */
    private static final int WIDTH = 35;
    private static final int DEPTH = 44;
    /** Waar in het bestand de ingang zit: het midden van de voorkant, op de vloer. */
    private static final BlockPos ENTRANCE = new BlockPos(17, 0, 0);
    /** Zoveel blokken moet de berg boven de vloer uitkomen, verder dan de ingang (de bunker is 8 hoog). */
    private static final int COVER = 9;
    /** Zoveel meetpunten (van de 25) mogen iets lager liggen: een echte berg is niet overal even hoog. */
    private static final int COVER_MISSES = 2;
    /** De afstand tussen de negen plekken die we per stuk wereld proberen. */
    private static final int SPOT_STEP = 5;
    /**
     * Tot hoever de grond voor de ingang hoger mag liggen dan de vloer: één blok kun je op
     * springen, meer niet (dan zit de deur ingegraven).
     */
    private static final int FRONT_MAX_RISE = 1;
    /** Zover voor de ingang kijken we of de weg naar de deur vrij is. */
    private static final int FRONT_CLEAR = 6;
    /** Tot zover voor de ingang moet het uitzicht open zijn: niet meer dan FRONT_VIEW_RISE hoger. */
    private static final int FRONT_VIEW = 16;
    private static final int FRONT_VIEW_RISE = 4;

    public HydraBunkerStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.HYDRA_BUNKER.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        java.util.Random random = new java.util.Random(context.random().nextLong());
        // Heel goedkoop eerst: is dit stuk wereld ergens in de hoogte een berg-biome? Zo niet
        // (vlaktes, zee, bos), dan hoeven we geen enkele hoogte uit te rekenen.
        if (!mountainsNearby(context)) {
            return Optional.empty();
        }
        // Niet alleen het midden van het stuk wereld proberen, maar negen plekken eromheen: een
        // goede rotswand ligt zelden precies in het midden.
        List<int[]> spots = new ArrayList<>();
        for (int dx = -SPOT_STEP; dx <= SPOT_STEP; dx += SPOT_STEP) {
            for (int dz = -SPOT_STEP; dz <= SPOT_STEP; dz += SPOT_STEP) {
                spots.add(new int[] {context.chunkPos().getMiddleBlockX() + dx, context.chunkPos().getMiddleBlockZ() + dz});
            }
        }
        Collections.shuffle(spots, random);
        for (int[] spot : spots) {
            Placement placement = tryAt(context, spot[0], spot[1], random);
            if (placement != null) {
                return Optional.of(new GenerationStub(placement.entrance().above(), builder -> builder.addPiece(
                        new HydraBunkerPiece(context.structureTemplateManager(), placement.origin(), placement.rotation()))));
            }
        }
        return Optional.empty();
    }

    /** Is het midden van dit stuk wereld op een van een paar hoogtes een biome waar een bunker mag staan? */
    private static boolean mountainsNearby(GenerationContext context) {
        int x = QuartPos.fromBlock(context.chunkPos().getMiddleBlockX());
        int z = QuartPos.fromBlock(context.chunkPos().getMiddleBlockZ());
        for (int y : new int[] {80, 110, 150, 190}) {
            if (context.validBiome().test(context.biomeSource().getNoiseBiome(x, QuartPos.fromBlock(y), z,
                    context.randomState().sampler()))) {
                return true;
            }
        }
        return false;
    }

    /** Past de bunker met zijn ingang op deze plek, in een van de vier richtingen? */
    private static @Nullable Placement tryAt(GenerationContext context, int x, int z, java.util.Random random) {
        // Eerst wat goedkoop is: staan we in de bergen, en niet in het water? Dat kost één
        // hoogte-berekening; de rest (tientallen hoogtes) doen we alleen als dit klopt. Zonder
        // deze stap duurt /locate (en een verkenningskaart) veel te lang.
        int floor = height(context, x, z);
        if (floor != oceanFloor(context, x, z)) {
            return null;
        }
        Holder<Biome> biome = context.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(floor + 1),
                QuartPos.fromBlock(z), context.randomState().sampler());
        if (!context.validBiome().test(biome)) {
            return null;
        }
        List<Direction> directions = new ArrayList<>(Direction.Plane.HORIZONTAL.stream().toList());
        Collections.shuffle(directions, random);
        for (Direction out : directions) {
            Placement placement = tryPlace(context, x, z, floor, out);
            if (placement != null) {
                return placement;
            }
        }
        return null;
    }

    private record Placement(BlockPos origin, Rotation rotation, BlockPos entrance) {}

    /**
     * Past de bunker hier, met de ingang naar {@code out}? Zo ja: waar het bestand begint en hoe
     * het gedraaid is.
     */
    private static @Nullable Placement tryPlace(GenerationContext context, int x, int z, int floor, Direction out) {
        Rotation rotation = rotationFacing(out);
        BlockPos entranceWorld = new BlockPos(x, floor, z);
        BlockPos origin = originFor(entranceWorld, rotation);

        // Snel afwijzen: midden in de bunker moet de berg hoog genoeg zijn. Meestal is hij dat
        // niet (verkeerde kant op), en dan hoeven we de rest niet uit te rekenen.
        if (!covered(context, origin, rotation, new BlockPos(17, 0, 24), floor)) {
            return null;
        }

        // Voor de ingang: elk blok tot FRONT_CLEAR ver weg mag hooguit één hoger liggen dan de
        // vloer, anders zit de deur ingegraven.
        for (int distance = 1; distance <= FRONT_CLEAR; distance++) {
            BlockPos front = entranceWorld.relative(out, distance);
            if (height(context, front.getX(), front.getZ()) > floor + FRONT_MAX_RISE) {
                return null;
            }
        }
        // Verder voor de ingang moet het uitzicht open blijven: anders zit de deur onderin een
        // kuil of een grot, met aan de overkant weer een berg.
        for (int distance = FRONT_CLEAR + 2; distance <= FRONT_VIEW; distance += 4) {
            BlockPos front = entranceWorld.relative(out, distance);
            if (height(context, front.getX(), front.getZ()) > floor + FRONT_VIEW_RISE) {
                return null;
            }
        }
        // Achter de ingang: overal berg boven het plafond, over de hele breedte en lengte.
        int misses = 0;
        for (int depth = 12; depth < DEPTH; depth += 6) {
            for (int side = 2; side < WIDTH; side += 8) {
                if (!covered(context, origin, rotation, new BlockPos(side, 0, depth), floor) && ++misses > COVER_MISSES) {
                    return null;
                }
            }
        }
        return new Placement(origin, rotation, entranceWorld);
    }

    /** Ligt de berg boven deze plek in het bestand minstens COVER blokken boven de vloer? */
    private static boolean covered(GenerationContext context, BlockPos origin, Rotation rotation, BlockPos local,
                                   int floor) {
        BlockPos world = origin.offset(StructureTemplate.transform(local, Mirror.NONE, rotation, BlockPos.ZERO));
        return height(context, world.getX(), world.getZ()) >= floor + COVER;
    }

    /** Waar het bestand moet beginnen om de ingang op {@code entrance} te krijgen (ook voor de showcase). */
    public static BlockPos originFor(BlockPos entrance, Rotation rotation) {
        return entrance.subtract(StructureTemplate.transform(ENTRANCE, Mirror.NONE, rotation, BlockPos.ZERO));
    }

    /** In het bestand kijkt de ingang naar het noorden; zo draai je hem naar {@code out}. */
    public static Rotation rotationFacing(Direction out) {
        return switch (out) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** Het bovenste vaste blok hier (zonder bomen: die komen pas later). */
    private static int height(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                context.heightAccessor(), context.randomState());
    }

    private static int oceanFloor(GenerationContext context, int x, int z) {
        return context.chunkGenerator().getFirstOccupiedHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG,
                context.heightAccessor(), context.randomState());
    }
}
