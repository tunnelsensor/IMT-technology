package com.senne.starktech.energy;

import com.senne.starktech.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Het stroomnet van Stark Tech: zonnepanelen en warmtegeneratoren leveren, en alles wat via
 * Stark-kabels (of er direct tegenaan) met ze verbonden is, kan die stroom gebruiken. Kabels zijn
 * getekende lijnen (zie StarkCables): een apparaat hangt eraan als er een kabel naast ligt, of
 * als het er bovenop gezet is.
 *
 * Hoe het werkt:
 * - Een apparaat dat stroom wil, vraagt {@link #draw}. Wij zoeken vanaf dat apparaat alle
 *   leveranciers op: we kijken naar zijn zes buren, en volgen elke kabel die we tegenkomen
 *   (net als water dat door buizen loopt). Drone-docks geven de stroom ook door, zodat een rij
 *   docks in een hangar aan één kabel genoeg heeft; laserzenders ook, zodat een laserpoort van
 *   meerdere zenders aan één kabel genoeg heeft.
 * - We halen de stroom eerst bij de leverancier die het meest heeft, zodat ze ongeveer even vol
 *   blijven.
 * - Het zoeken kost wat werk, dus we onthouden het resultaat per apparaat. Komt er ergens een
 *   kabel of leverancier bij of gaat er een weg, dan verhogen we {@link #changed()} en zoeken we
 *   de volgende keer opnieuw.
 */
public final class StarkGrid {
    private StarkGrid() {}

    /** Zoveel kabels volgen we hooguit vanaf één apparaat (tegen eindeloos lange netten). */
    public static final int MAX_CABLES = 512;
    /** Na zo veel ticks zoeken we sowieso opnieuw (bijvoorbeeld als er een stuk wereld laadde). */
    private static final int CACHE_TICKS = 200;

    /** Telt elke verandering aan een net; staat hij anders dan bij het zoeken, dan zoeken we opnieuw. */
    private static int version;

    private record Found(int version, long gameTime, List<BlockPos> sources) {}

    private static final Map<ResourceKey<Level>, Map<BlockPos, Found>> CACHE = new HashMap<>();

    /** Er kwam een kabel of leverancier bij, of er ging er een weg: alle netten opnieuw zoeken. */
    public static void changed() {
        version++;
    }

    /** Alle leveranciers die met dit apparaat verbonden zijn. */
    public static List<EnergySource> sources(Level level, BlockPos device) {
        List<EnergySource> result = new ArrayList<>();
        for (BlockPos pos : sourcePositions(level, device)) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof EnergySource source) {
                result.add(source);
            }
        }
        return result;
    }

    /** Hoeveel stroom er in totaal in de leveranciers van dit apparaat zit. */
    public static int available(Level level, BlockPos device) {
        long total = 0;
        for (EnergySource source : sources(level, device)) {
            total += source.energy();
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /**
     * Haalt maximaal {@code amount} stroom uit het net van dit apparaat, bij de voortste
     * leverancier eerst. Geeft terug hoeveel het werd (kan minder zijn, of 0).
     */
    public static int draw(Level level, BlockPos device, int amount) {
        if (amount <= 0) {
            return 0;
        }
        List<EnergySource> sources = sources(level, device);
        sources.sort(Comparator.comparingInt(EnergySource::energy).reversed());
        int taken = 0;
        for (EnergySource source : sources) {
            taken += source.drain(amount - taken);
            if (taken >= amount) {
                break;
            }
        }
        return taken;
    }

    /** Waar de leveranciers van dit apparaat staan (uit het geheugen als dat nog klopt). */
    private static List<BlockPos> sourcePositions(Level level, BlockPos device) {
        Map<BlockPos, Found> perLevel = CACHE.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        Found found = perLevel.get(device);
        long now = level.getGameTime();
        if (found == null || found.version != version || now - found.gameTime > CACHE_TICKS) {
            found = new Found(version, now, search(level, device));
            perLevel.put(device.immutable(), found);
        }
        return found.sources;
    }

    /**
     * Zoekt de leveranciers: eerst de zes buren van het apparaat, en dan langs elke kabel verder.
     * (Door een leverancier of een ander apparaat heen lopen we niet: alleen kabels, drone-docks
     * en laserzenders geleiden. Staat een apparaat op een kabel, dan loopt de kabel eronder door.)
     */
    private static List<BlockPos> search(Level level, BlockPos device) {
        List<BlockPos> sources = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> cables = new ArrayDeque<>();
        seen.add(device);
        // Staat het apparaat zelf op een kabel (erbovenop gebouwd)? Dan hangt het daar ook aan.
        if (StarkCables.has(level, device)) {
            cables.add(device);
        }
        look(level, device, sources, seen, cables);
        int followed = 0;
        while (!cables.isEmpty() && followed < MAX_CABLES) {
            look(level, cables.poll(), sources, seen, cables);
            followed++;
        }
        return List.copyOf(sources);
    }

    /** Bekijkt de zes buren van {@code from}: een leverancier onthouden we, een kabel volgen we. */
    private static void look(Level level, BlockPos from, List<BlockPos> sources, Set<BlockPos> seen,
                             ArrayDeque<BlockPos> cables) {
        BlockState fromState = level.getBlockState(from);
        boolean fromCabinet = fromState.getBlock() instanceof PowerCabinetBlock;
        for (Direction direction : Direction.values()) {
            // Uit een stroomkast gaat de stroom alleen naar de kanten waarvan de schakelaar aan staat.
            if (fromCabinet && !PowerCabinetBlock.isOn(fromState, direction)) {
                continue;
            }
            BlockPos next = from.relative(direction);
            if (seen.contains(next) || !level.isLoaded(next)) {
                continue;
            }
            BlockState state = level.getBlockState(next);
            // In een stroomkast kom je alleen via een kant die aan staat.
            if (state.getBlock() instanceof PowerCabinetBlock) {
                if (PowerCabinetBlock.isOn(state, direction.getOpposite())) {
                    seen.add(next);
                    cables.add(next);
                }
                continue;
            }
            seen.add(next);
            // Een leverancier kan ook OP een kabel staan: dan is de plek allebei.
            BlockEntity entity = level.getBlockEntity(next);
            if (entity instanceof EnergySource) {
                sources.add(next);
            }
            if (StarkCables.has(level, next) || state.is(ModBlocks.DRONE_DOCK.get())
                    || state.is(ModBlocks.LASER_EMITTER.get())) {
                cables.add(next);
            }
        }
    }

    /** Bij het afsluiten van de wereld: alles vergeten. */
    public static void clear() {
        CACHE.clear();
    }
}
