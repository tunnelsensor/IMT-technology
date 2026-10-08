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
 * meerdere losse groepjes, dan is de grootste groep de server (bij gelijke grootte die het dichtst
 * bij de kern staat); de rest draait niet mee ("not connected"). Zo weet je altijd welke racks
 * tellen: die met knipperende lampjes.
 *
 * Wat de server kan, hangt af van de onderdelen in die racks (alleen racks met stroom tellen):
 * CPU's en GPU's (rekenkracht: sneller reageren, en het brein van E.D.I.T.H.), RAM (meer
 * tegelijk), SSD's (meer blokjes) en fans (koeling). Zonder rekenkracht, RAM of SSD draait hij niet.
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
     * @param gpus       GPU's in racks die meedraaien
     * @param ram        RAM-latjes in racks die meedraaien
     * @param ssd        SSD's in racks die meedraaien
     * @param fans       fans in racks die meedraaien
     * @param coreOnline heeft de kern zelf stroom?
     * @param cluster    waar de racks van de server staan
     */
    public record Stats(int racks, int cpus, int gpus, int ram, int ssd, int fans, boolean coreOnline,
                        Set<BlockPos> cluster, long time) {
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
            if (cpus == 0 && gpus == 0 && ram == 0 && ssd == 0 && fans == 0) {
                return "The server racks are empty or have no power";
            }
            if (cpus == 0 && gpus == 0) {
                return "The server needs a CPU or GPU";
            }
            if (ram == 0) {
                return "The server needs RAM";
            }
            if (ssd == 0) {
                return "The server needs an SSD";
            }
            return null;
        }

        /** Rekenkracht voor Logic (je diagrammen): een CPU telt 1, een GPU ½. */
        public double logicPower() {
            return cpus * LogicBalance.logicPower(ServerPartItem.Kind.CPU) + gpus * LogicBalance.logicPower(ServerPartItem.Kind.GPU);
        }

        /** Rekenkracht voor het brein van E.D.I.T.H.: een CPU telt 1, een GPU 2. */
        public double brainPower() {
            return cpus * LogicBalance.brainPower(ServerPartItem.Kind.CPU) + gpus * LogicBalance.brainPower(ServerPartItem.Kind.GPU);
        }

        /** Ticks tussen "er gebeurt iets" en "het diagram begint". */
        public int reactionTicks() {
            return LogicBalance.reactionTicks(logicPower());
        }

        /** Hoeveel ticks E.D.I.T.H. extra nadenkt op dit datacenter (0 = sterk genoeg, -1 = draait niet). */
        public int brainDelayTicks() {
            return online() ? LogicBalance.brainDelayTicks(brainPower()) : -1;
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
        Set<BlockPos> cluster = mainCluster(core.getBlockPos(), SecurityDevices.scan(level, core).racks());
        int[] counts = new int[ServerPartItem.Kind.values().length];
        for (BlockPos pos : cluster) {
            BlockState state = level.getBlockState(pos);
            if (!(level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack)
                    || !state.hasProperty(ServerRackBlock.ONLINE) || !state.getValue(ServerRackBlock.ONLINE)) {
                continue;
            }
            for (ServerPartItem.Kind kind : ServerPartItem.Kind.values()) {
                counts[kind.ordinal()] += rack.count(kind);
            }
        }
        Stats stats = new Stats(cluster.size(), counts[ServerPartItem.Kind.CPU.ordinal()], counts[ServerPartItem.Kind.GPU.ordinal()],
                counts[ServerPartItem.Kind.RAM.ordinal()], counts[ServerPartItem.Kind.SSD.ordinal()],
                counts[ServerPartItem.Kind.FAN.ordinal()], core.isOnline(), Set.copyOf(cluster), now);
        CACHE.put(key, stats);
        return stats;
    }

    /** Hoort het rack op {@code pos} bij de server van deze kern (de grootste groep)? */
    public static boolean inMainCluster(ServerLevel level, SecurityCoreBlockEntity core, BlockPos pos) {
        return stats(level, core).cluster().contains(pos);
    }

    /**
     * De grootste groep racks die elkaar raken. Zijn twee groepen even groot, dan wint de groep die het
     * dichtst bij de kern staat, en zijn ze ook even dichtbij, die met het laagste rack (zodat het nooit
     * heen en weer springt).
     */
    public static Set<BlockPos> mainCluster(BlockPos core, List<BlockPos> racks) {
        Set<BlockPos> left = new HashSet<>(racks);
        Set<BlockPos> best = Set.of();
        BlockPos bestFirst = null;
        double bestDistance = Double.MAX_VALUE;
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
            double distance = Double.MAX_VALUE;
            while (!todo.isEmpty()) {
                BlockPos pos = todo.poll();
                group.add(pos);
                if (pos.compareTo(first) < 0) {
                    first = pos;
                }
                distance = Math.min(distance, pos.distSqr(core));
                for (Direction direction : Direction.values()) {
                    BlockPos next = pos.relative(direction);
                    if (left.remove(next)) {
                        todo.add(next);
                    }
                }
            }
            boolean better = group.size() > best.size()
                    || group.size() == best.size() && (distance < bestDistance
                    || distance == bestDistance && bestFirst != null && first.compareTo(bestFirst) < 0);
            if (better) {
                best = group;
                bestFirst = first;
                bestDistance = distance;
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
        tag.putInt("gpu", stats.gpus());
        tag.putInt("ram", stats.ram());
        tag.putInt("ssd", stats.ssd());
        tag.putInt("fan", stats.fans());
        tag.putBoolean("online", stats.online());
        String problem = stats.problem();
        if (problem != null) {
            tag.putString("problem", problem);
        }
        tag.putInt("react", stats.online() ? stats.reactionTicks() : 0);
        tag.putInt("brainDelay", stats.brainDelayTicks());
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
