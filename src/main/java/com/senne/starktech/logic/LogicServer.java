package com.senne.starktech.logic;

import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityDevices;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De server van een beveiligingskern: alle serverracks in zijn gebied die elkaar raken (jouw
 * wens, 7 oktober: "een multiblock die je op elkaar of naast elkaar kunt stacken").
 *
 * Racks die tegen elkaar staan (boven, onder, opzij) zijn samen één server. Staan er in het gebied
 * meerdere losse groepjes, dan is de grootste groep de server; de rest draait niet mee ("not
 * connected"). Zo weet je altijd welke racks tellen: die met knipperende lampjes.
 *
 * Wat de server kan, hangt af van de onderdelen in die racks (alleen racks met stroom tellen):
 * CPU's (sneller reageren), RAM (meer tegelijk) en SSD's (meer blokjes). Zonder één van elk
 * draait hij niet.
 */
public final class LogicServer {
    private LogicServer() {}

    /** Zo lang (ticks) onthouden we wat de server kan, voor we opnieuw tellen. */
    private static final int RECOUNT = 40;

    /**
     * Wat de server van een kern kan.
     *
     * @param racks      hoeveel racks in de server (de grootste groep), met of zonder stroom
     * @param cpus       CPU's in racks die meedraaien
     * @param ram        RAM-latjes in racks die meedraaien
     * @param ssd        SSD's in racks die meedraaien
     * @param coreOnline heeft de kern zelf stroom?
     * @param cluster    waar de racks van de server staan
     */
    public record Stats(int racks, int cpus, int ram, int ssd, boolean coreOnline, Set<BlockPos> cluster, long time) {
        /** Draait de server? (De kern heeft stroom, en er is minstens één CPU, RAM en SSD.) */
        public boolean online() {
            return problem() == null;
        }

        /** Waarom hij niet draait (in het Engels: je ziet het in het spel), of null. */
        public @Nullable String problem() {
            if (racks == 0) {
                return "No server racks in this network";
            }
            if (!coreOnline) {
                return "The security core has no power";
            }
            if (cpus == 0 && ram == 0 && ssd == 0) {
                return "The server racks are empty or have no power";
            }
            if (cpus == 0) {
                return "The server needs a CPU";
            }
            if (ram == 0) {
                return "The server needs RAM";
            }
            if (ssd == 0) {
                return "The server needs an SSD";
            }
            return null;
        }

        /** Ticks tussen "er gebeurt iets" en "het diagram begint". */
        public int reactionTicks() {
            return LogicBalance.reactionTicks(cpus);
        }

        /** Zoveel diagrammen kunnen tegelijk bezig zijn. */
        public int maxRuns() {
            return ram * LogicBalance.RUNS_PER_RAM;
        }

        /** Zoveel blokjes passen er in totaal in de diagrammen. */
        public int capacity() {
            return ssd * LogicBalance.BLOCKS_PER_SSD;
        }
    }

    private static final Map<GlobalPos, Stats> CACHE = new HashMap<>();

    /** Wat de server van deze kern nu kan (hooguit RECOUNT ticks oud). */
    public static Stats stats(ServerLevel level, SecurityCoreBlockEntity core) {
        GlobalPos key = GlobalPos.of(level.dimension(), core.getBlockPos());
        Stats cached = CACHE.get(key);
        long now = level.getGameTime();
        if (cached != null && now - cached.time() < RECOUNT && now >= cached.time()) {
            return cached;
        }
        Set<BlockPos> cluster = mainCluster(SecurityDevices.scan(level, core).racks());
        int cpus = 0;
        int ram = 0;
        int ssd = 0;
        for (BlockPos pos : cluster) {
            BlockState state = level.getBlockState(pos);
            if (!(level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack)
                    || !state.hasProperty(ServerRackBlock.ONLINE) || !state.getValue(ServerRackBlock.ONLINE)) {
                continue;
            }
            cpus += rack.count(ServerPartItem.Kind.CPU);
            ram += rack.count(ServerPartItem.Kind.RAM);
            ssd += rack.count(ServerPartItem.Kind.SSD);
        }
        Stats stats = new Stats(cluster.size(), cpus, ram, ssd, core.isOnline(), Set.copyOf(cluster), now);
        CACHE.put(key, stats);
        return stats;
    }

    /** Hoort het rack op {@code pos} bij de server van deze kern (de grootste groep)? */
    public static boolean inMainCluster(ServerLevel level, SecurityCoreBlockEntity core, BlockPos pos) {
        return stats(level, core).cluster().contains(pos);
    }

    /**
     * De grootste groep racks die elkaar raken. Zijn twee groepen even groot, dan wint de groep met
     * het laagste rack (zodat het niet heen en weer springt).
     */
    public static Set<BlockPos> mainCluster(List<BlockPos> racks) {
        Set<BlockPos> left = new HashSet<>(racks);
        Set<BlockPos> best = Set.of();
        BlockPos bestFirst = null;
        for (BlockPos start : racks) {
            if (!left.contains(start)) {
                continue;
            }
            // Alles wat via buren aan dit rack vastzit (zoals water dat zich verspreidt).
            Set<BlockPos> group = new HashSet<>();
            ArrayDeque<BlockPos> todo = new ArrayDeque<>();
            todo.add(start);
            left.remove(start);
            BlockPos first = start;
            while (!todo.isEmpty()) {
                BlockPos pos = todo.poll();
                group.add(pos);
                if (pos.compareTo(first) < 0) {
                    first = pos;
                }
                for (Direction direction : Direction.values()) {
                    BlockPos next = pos.relative(direction);
                    if (left.remove(next)) {
                        todo.add(next);
                    }
                }
            }
            if (group.size() > best.size() || group.size() == best.size() && bestFirst != null && first.compareTo(bestFirst) < 0) {
                best = group;
                bestFirst = first;
            }
        }
        return best;
    }

    /** Hoe de server ervoor staat, voor de desktop (de app Status, en straks Logic). */
    public static net.minecraft.nbt.CompoundTag snapshot(ServerLevel level, SecurityCoreBlockEntity core) {
        Stats stats = stats(level, core);
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("racks", stats.racks());
        tag.putInt("cpu", stats.cpus());
        tag.putInt("ram", stats.ram());
        tag.putInt("ssd", stats.ssd());
        tag.putBoolean("online", stats.online());
        String problem = stats.problem();
        if (problem != null) {
            tag.putString("problem", problem);
        }
        tag.putInt("react", stats.online() ? stats.reactionTicks() : 0);
        tag.putInt("runs", stats.maxRuns());
        tag.putInt("cap", stats.capacity());
        return tag;
    }

    /** Een rack veranderde (onderdeel erin of eruit, stroom, neergezet, afgebroken): opnieuw tellen. */
    public static void forgetAll() {
        CACHE.clear();
    }

    public static void clear() {
        CACHE.clear();
    }
}
