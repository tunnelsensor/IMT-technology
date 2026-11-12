package com.senne.imttech.logic;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import com.senne.imttech.security.SecurityDevices;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
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
 * CPU's en GPU's (rekenkracht: meer apparaten in je diagrammen zonder te vertragen, en het brein van I.M.T), RAM (meer
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
     * @param coreOnline  heeft de kern zelf stroom?
     * @param cluster     waar de racks van de server staan
     * @param temperature hoe warm het heetste rack is (graden, zie Datacenter)
     * @param devices     hoeveel apparaten de diagrammen die draaien gebruiken (hoofdstuk 86, zie LogicDevices.used)
     * @param revision    welke versie van de diagrammen toen geteld werd (opnieuw bewaard: opnieuw tellen)
     */
    public record Stats(int racks, int cpus, int gpus, int ram, int ssd, int fans, boolean coreOnline,
                        Set<BlockPos> cluster, long time, double temperature, int devices, int revision) {
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

        /** CPU's en GPU's samen (zij hebben het RAM nodig). */
        public int processors() {
            return cpus + gpus;
        }

        /**
         * Welk deel van de CPU's en GPU's meedraait (hoofdstuk 64): 1 als er per 10 genoeg RAM (64 GB) is, anders zoveel als
         * het RAM toelaat (zie LogicStats.fed). De SSD's zijn alleen opslag.
         */
        public double fed() {
            return LogicStats.fed(processors(), ram);
        }

        /** Rekenkracht voor Logic (je diagrammen): een CPU telt 1, een GPU ½, maal wat het geheugen voedt. */
        public double logicPower() {
            return (cpus * LogicStats.logicPower(ServerPartItem.Kind.CPU) + gpus * LogicStats.logicPower(ServerPartItem.Kind.GPU))
                    * fed();
        }

        /** Rekenkracht voor het brein van I.M.T: een CPU telt 1, een GPU 2, maal wat het geheugen voedt. */
        public double brainPower() {
            return (cpus * LogicStats.brainPower(ServerPartItem.Kind.CPU) + gpus * LogicStats.brainPower(ServerPartItem.Kind.GPU))
                    * fed();
        }

        /** Zoveel MB RAM heeft de server (alle latjes in racks die meedraaien). */
        public double ramMb() {
            return ram * LogicStats.ramStickMb();
        }

        /** Zoveel MB SSD heeft de server. */
        public double ssdMb() {
            return ssd * LogicStats.ssdMb();
        }

        /** Zoveel MB RAM hebben de CPU's en GPU's nodig om op volle snelheid te draaien. */
        public double ramNeededMb() {
            return LogicStats.ramNeededMb(processors());
        }

        /**
         * Is een rack warm (vanaf WARM_TEMPERATURE)? Dan werkt de hele server op halve snelheid: alles
         * werkt samen, dus één warm rack remt de rest af.
         */
        public boolean warm() {
            return temperature >= ImtConfig.Logic.WARM_TEMPERATURE;
        }

        /** Hoeveel trager alles gaat: 1 als het koel is, WARM_SLOWDOWN als het warm is. */
        public double slowdown() {
            return warm() ? Math.max(1.0, ImtConfig.Logic.WARM_SLOWDOWN) : 1.0;
        }

        /**
         * Zoveel apparaten kunnen de diagrammen gebruiken zonder trager te worden (hoofdstuk 86): 10 per CPU, 5 per GPU,
         * maal wat het RAM voedt, en warm de helft.
         */
        public double deviceCapacity() {
            return LogicStats.deviceCapacity(logicPower() / slowdown());
        }

        /** Hoeveel trager de diagrammen zijn: 0 = meteen, 0,1 per apparaat te veel, 1 = zo traag als het kan. */
        public double slower() {
            return LogicStats.slower(logicPower() / slowdown(), devices);
        }

        /**
         * Ticks tussen "er gebeurt iets" en "het diagram begint": meteen zolang de apparaten passen, per apparaat te veel
         * 10% van 6 s erbij (warm: de helft van de apparaten past).
         */
        public int reactionTicks() {
            return LogicStats.reactionTicks(logicPower() / slowdown(), devices);
        }

        /** Hoeveel ticks I.M.T extra nadenkt op dit datacenter (0 = sterk genoeg, -1 = draait niet). */
        public int brainDelayTicks() {
            return online() ? LogicStats.brainDelayTicks(brainPower() / slowdown()) : -1;
        }

        /** Zoveel diagrammen kunnen tegelijk bezig zijn (een bezig diagram neemt RUN_MB van het RAM). */
        public int maxRuns() {
            return LogicStats.maxRuns(ram);
        }

        /** Zoveel blokjes passen er in totaal in de diagrammen (een blokje neemt BLOCK_MB van de SSD's). */
        public int capacity() {
            return LogicStats.blockCapacity(ssd);
        }
    }

    private static final Map<GlobalPos, Stats> CACHE = new HashMap<>();

    /** Wat de server van deze kern nu kan (hooguit RECOUNT ticks oud). */
    public static Stats stats(ServerLevel level, SecurityCoreBlockEntity core) {
        GlobalPos key = GlobalPos.of(level.dimension(), core.getBlockPos());
        Stats cached = CACHE.get(key);
        long now = level.getGameTime();
        if (cached != null && now - cached.time() < RECOUNT && now >= cached.time() && cached.revision() == core.logic().revision()) {
            return cached;
        }
        // Alleen de racks die bij DEZE kern horen: liggen er twee gebieden (of netwerken) over elkaar, dan hoort een rack
        // bij de dichtstbijzijnde kern (zie SecurityCores.deviceCore), niet bij allebei.
        List<BlockPos> racks = new java.util.ArrayList<>();
        for (BlockPos pos : SecurityDevices.scan(level, core).racks()) {
            if (com.senne.imttech.security.SecurityCores.deviceCore(level, pos) == core) {
                racks.add(pos);
            }
        }
        // Hoofdstuk 54 (jouw notitie: "meerdere racks aan elkaar zonder dat ze fysiek aan elkaar hangen, via een stroom- en
        // netwerkkabel of PoE"): met de netwerk-eis aan hoort elk rack aan zijn kabel bij de kern, dus dan tellen ze
        // allemaal mee, waar ze ook staan. Zonder die eis (een oude wereld) de oude regel: de grootste groep die elkaar raakt.
        Set<BlockPos> cluster = com.senne.imttech.lan.LanGrid.required() ? Set.copyOf(racks) : mainCluster(core.getBlockPos(), racks);
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
        // Hoofdstuk 86: hoeveel apparaten de diagrammen gebruiken die op de SSD's passen.
        int ssds = counts[ServerPartItem.Kind.SSD.ordinal()];
        int devices = core.logic().programs().isEmpty() ? 0 : LogicDevices.used(level, core, LogicStats.blockCapacity(ssds));
        Stats stats = new Stats(cluster.size(), counts[ServerPartItem.Kind.CPU.ordinal()], counts[ServerPartItem.Kind.GPU.ordinal()],
                counts[ServerPartItem.Kind.RAM.ordinal()], ssds,
                counts[ServerPartItem.Kind.FAN.ordinal()], core.isOnline(), Set.copyOf(cluster), now,
                core.datacenter().temperature(), devices, core.logic().revision());
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
        // Hoofdstuk 86: hoeveel apparaten de diagrammen gebruiken, hoeveel er zonder vertragen passen, en hoeveel trager.
        tag.putInt("devices", stats.devices());
        tag.putDouble("deviceCap", stats.deviceCapacity());
        tag.putDouble("slower", stats.slower());
        tag.putInt("brainDelay", stats.brainDelayTicks());
        // Hoofdstuk 64: het geheugen in MB (de app Monitor maakt er GB en TB van), wat de CPU's en GPU's ervan nodig
        // hebben, en welk deel van hen daardoor meedraait.
        tag.putDouble("ramMb", stats.ramMb());
        tag.putDouble("ssdMb", stats.ssdMb());
        tag.putDouble("ramNeedMb", stats.ramNeededMb());
        tag.putDouble("fed", stats.fed());
        int[] busy = LogicRunner.load(level, core);
        tag.putDouble("ramUsedMb", Datacenter.ramUsedMb(stats, busy[0],
                Math.max(core.datacenter().cpuLoad(), core.datacenter().brainLoad())));
        tag.putDouble("ssdUsedMb", Datacenter.ssdUsedMb(stats, core.logic().blocks(),
                Datacenters.get(level).storedAt(GlobalPos.of(level.dimension(), core.getBlockPos()))));
        // Het datacenter: hoe warm, en waarom (zie Datacenter).
        Datacenter datacenter = core.datacenter();
        tag.putDouble("temp", datacenter.temperature());
        // Een rack zonder fan wordt steeds warmer: dan is er geen "waar hij naartoe gaat".
        boolean rising = Double.isInfinite(datacenter.target());
        tag.putBoolean("rising", rising);
        tag.putDouble("target", rising ? datacenter.temperature() : datacenter.target());
        tag.putDouble("heat", datacenter.heat());
        tag.putDouble("fullHeat", datacenter.fullHeat());
        tag.putDouble("cooling", datacenter.cooling());
        // Het gebruik (8 oktober, de app Monitor): nu, en de laatste minuut.
        tag.putInt("cpuUse", (int) Math.round(datacenter.cpuLoad() * 100));
        tag.putInt("brainUse", (int) Math.round(datacenter.brainLoad() * 100));
        tag.putInt("ramUse", (int) Math.round(datacenter.ramLoad() * 100));
        tag.putInt("used", core.logic().blocks());
        int[] load = LogicRunner.load(level, core);
        tag.putInt("running", load[0]);
        // Hoofdstuk 64: wat nog op de reactietijd van de server wacht ("starting"), en wat echt op RAM wacht ("waiting").
        tag.putInt("starting", load[1] - load[2]);
        tag.putInt("waiting", load[2]);
        tag.putDouble("logicPower", stats.logicPower());
        tag.putDouble("brainPower", stats.brainPower());
        tag.putBoolean("warm", stats.warm());
        java.util.List<int[]> history = datacenter.history();
        int[][] columns = new int[4][history.size()];
        for (int i = 0; i < history.size(); i++) {
            for (int c = 0; c < 4; c++) {
                columns[c][i] = history.get(i)[c];
            }
        }
        tag.putIntArray("hCpu", columns[0]);
        tag.putIntArray("hBrain", columns[1]);
        tag.putIntArray("hRam", columns[2]);
        tag.putIntArray("hTemp", columns[3]);
        // Wat er bezig is: de diagrammen (de app Monitor laat ze zien zoals Taakbeheer).
        net.minecraft.nbt.ListTag procs = new net.minecraft.nbt.ListTag();
        for (String name : LogicRunner.runningNames(level, core)) {
            procs.add(net.minecraft.nbt.StringTag.valueOf(name));
        }
        tag.put("procs", procs);
        net.minecraft.nbt.ListTag brains = new net.minecraft.nbt.ListTag();
        for (String name : DatacenterBrain.thinkingOn(level, GlobalPos.of(level.dimension(), core.getBlockPos()))) {
            brains.add(net.minecraft.nbt.StringTag.valueOf(name));
        }
        tag.put("brainProcs", brains);
        // Elk rack van de server: waar, of het meedraait, wat erin zit (en hoe versleten), hoe warm, en brand.
        net.minecraft.nbt.ListTag racks = new net.minecraft.nbt.ListTag();
        java.util.List<BlockPos> sorted = new java.util.ArrayList<>(stats.cluster());
        sorted.sort(null);
        for (BlockPos pos : sorted) {
            if (!(level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack)) {
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putInt("x", pos.getX());
            entry.putInt("y", pos.getY());
            entry.putInt("z", pos.getZ());
            // Hoofdstuk 64: de naam die je het rack gaf (sneak + rechtsklik), of leeg.
            entry.putString("name", rack.name());
            entry.putString("status", rack.status().name());
            entry.putInt("burn", rack.burnSecondsLeft(level.getGameTime()));
            // Elk rack zijn eigen temperatuur, warmte en koeling (zijn fans).
            entry.putDouble("t", rack.temperature());
            entry.putDouble("heat", rack.heat());
            entry.putDouble("full", rack.fullHeat());
            entry.putDouble("cool", rack.cooling());
            net.minecraft.nbt.ListTag parts = new net.minecraft.nbt.ListTag();
            for (int slot = 0; slot < rack.parts().getContainerSize(); slot++) {
                net.minecraft.world.item.ItemStack stack = rack.parts().getItem(slot);
                if (stack.getItem() instanceof ServerPartItem part) {
                    CompoundTag p = new CompoundTag();
                    p.putString("k", part.kind().name());
                    p.putInt("w", Math.min(100, ServerPartItem.wear(stack) * 100 / Math.max(1, Math.round(ImtConfig.Logic.MAX_WEAR))));
                    p.putInt("s", slot); // in welk vakje (voor de app Monitor: zo ziet het rack eruit)
                    parts.add(p);
                }
            }
            entry.put("parts", parts);
            racks.add(entry);
        }
        tag.put("racksInfo", racks);
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
