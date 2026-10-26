package com.senne.imttech.weapons;

import com.senne.imttech.config.ImtConfig;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * De rookwolken van de Smoke Grenade (hoofdstuk 35). Jouw keuze: de rook blokkeert het zicht van "alles wat kijkt":
 * robots, turrets en drones (en de Raven-soldaten) zien niemand in of achter de rook. Wat jij ziet, zijn de
 * rookdeeltjes in je spel (zie client/weapons/SmokeView); hier houdt de server bij waar de wolken hangen, zodat
 * machines er niet doorheen kijken (zie {@link #blocks}).
 *
 * Een wolk groeit in 2 seconden tot zijn volle grootte (SMOKE_RADIUS), blijft dan hangen, en trekt in de laatste 3
 * seconden weg. Jouw wens (26 oktober): de rook gaat niet door blokken. Hij vult alleen de plekken die hij kan bereiken
 * zonder door een blok te gaan (zie {@link Volume}): een muur, een dichte deur of een ruit houdt hem tegen, door een
 * open deur stroomt hij naar binnen.
 */
public final class Smoke {
    private Smoke() {}

    /** Zo lang (ticks) groeit een wolk, en zo lang trekt hij weg aan het eind. */
    public static final int GROW_TICKS = 40;
    public static final int FADE_TICKS = 60;

    /** Eén wolk: waar, van wanneer tot wanneer (speltijd), en welke plekken de rook kan bereiken. */
    public record Cloud(Vec3 center, long start, long end, Volume volume) {
        /** Hoe groot hij nu is (blokken). */
        public double radius(long now) {
            return Smoke.radius(now - start, end - start);
        }
    }

    private static final Map<ResourceKey<Level>, List<Cloud>> CLOUDS = new HashMap<>();

    /** Hoe groot een wolk is die {@code age} ticks oud is en {@code duration} ticks duurt (ook voor je spel). */
    public static double radius(long age, long duration) {
        double max = ImtConfig.Weapons.SMOKE_RADIUS;
        if (age < 0 || age >= duration) {
            return 0;
        }
        double grow = Math.min(1, age / (double) GROW_TICKS);
        double fade = Math.min(1, (duration - age) / (double) FADE_TICKS);
        return max * Math.sqrt(grow) * fade;
    }

    /** Een nieuwe wolk rond {@code center}, die {@code ticks} lang blijft. */
    public static Cloud add(ServerLevel level, Vec3 center, int ticks) {
        long now = level.getGameTime();
        Cloud cloud = new Cloud(center, now, now + ticks, Volume.fill(level, center));
        CLOUDS.computeIfAbsent(level.dimension(), key -> new ArrayList<>()).add(cloud);
        return cloud;
    }

    /**
     * Kijkt een lijn van {@code from} naar {@code to} door de rook? Dat is zo als hij in totaal meer dan SMOKE_SEE_THROUGH
     * blokken door rook gaat: van heel dichtbij zie je elkaar in de rook nog net. Alleen op de server (in je spel altijd
     * false: daar doen de deeltjes het werk).
     */
    public static boolean blocks(Level level, Vec3 from, Vec3 to) {
        List<Cloud> clouds = CLOUDS.get(level.dimension());
        if (clouds == null || clouds.isEmpty() || level.isClientSide) {
            return false;
        }
        long now = level.getGameTime();
        double through = 0;
        for (Cloud cloud : clouds) {
            through += cloud.volume().inside(cloud.radius(now), from, to);
        }
        return through > ImtConfig.Weapons.SMOKE_SEE_THROUGH;
    }

    /** Wolken die op zijn vergeten. */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (CLOUDS.isEmpty()) {
            return;
        }
        for (ServerLevel level : event.getServer().getAllLevels()) {
            List<Cloud> clouds = CLOUDS.get(level.dimension());
            if (clouds == null) {
                continue;
            }
            long now = level.getGameTime();
            for (Iterator<Cloud> it = clouds.iterator(); it.hasNext(); ) {
                if (it.next().end() <= now) {
                    it.remove();
                }
            }
            if (clouds.isEmpty()) {
                CLOUDS.remove(level.dimension());
            }
        }
    }

    /** Bij het afsluiten van de wereld: alles vergeten. */
    public static void clear() {
        CLOUDS.clear();
    }

    // ================================================================ Waar de rook kan komen

    /**
     * De plekken (blokken lucht, "cellen") die de rook van één wolk vult. De rook begint bij de granaat en stroomt van
     * cel naar cel (ook schuin), tot hij SMOKE_RADIUS blokken heeft afgelegd. Hij gaat niet door een blok: niet door een
     * muur, en ook niet door een dun blok dat de doorgang helemaal afsluit, zoals een dichte deur, een dicht luik, een
     * ruit of een plaat (zie {@link #sealedAxes}). Water en lava vult hij niet. Per cel onthouden we twee getallen:
     * <ul>
     *   <li><b>need</b>: hoe ver de rook moet stromen om er te komen, langs de kortste weg om blokken heen. Zo groot moet
     *       de wolk zijn voordat de rook er is. In de open lucht is dat gewoon de afstand (een bol); door een deur naar
     *       buiten of om een hoek is de weg langer, dus komt de rook daar minder ver;</li>
     *   <li><b>room</b>: hoeveel ruimte er rond die cel is tot het dichtstbijzijnde blok (0,5 naast een muur, vloer of
     *       plafond, dan 1,5, 2,5 ...). Daarnaar kiest je spel hoe groot een rookpluk mag zijn, zodat een pluk nooit
     *       door een muur heen steekt (zie client/weapons/SmokeCloudParticle).</li>
     * </ul>
     * Welke plekken het zijn, ligt vast als de rook begint (een deur die je daarna opendoet, laat er niets meer door).
     * De server en je spel rekenen het allebei zelf uit, met dezelfde blokken.
     */
    public static final class Volume {
        /** Zoveel ruimte (blokken) heeft een cel die ver van elk blok af ligt. */
        public static final float OPEN = 3.0f;

        private final Long2FloatOpenHashMap need = new Long2FloatOpenHashMap();
        private final Long2FloatOpenHashMap room = new Long2FloatOpenHashMap();
        /** De cellen op volgorde van need (de eerste zoveel hebben rook bij een kleine wolk), en hun need. */
        private final LongArrayList order = new LongArrayList();
        private final FloatArrayList orderNeed = new FloatArrayList();
        private AABB bounds = new AABB(0, 0, 0, 0, 0, 0);

        private Volume() {}

        /** Hoeveel cellen de rook kan vullen (als de wolk op zijn grootst is). */
        public int size() {
            return order.size();
        }

        /** Heeft deze cel rook, bij een wolk van deze grootte? */
        public boolean has(BlockPos cell, double radius) {
            return has(cell.asLong(), radius);
        }

        public boolean has(long cell, double radius) {
            return radius > 0 && need.getOrDefault(cell, Float.MAX_VALUE) <= radius;
        }

        /** Hoeveel ruimte er rond deze cel is tot het dichtstbijzijnde blok, of -1 als de rook er nooit komt. */
        public float room(long cell) {
            return need.containsKey(cell) ? room.get(cell) : -1;
        }

        /** Hoeveel cellen er rook hebben bij een wolk van deze grootte (de eerste zoveel van {@link #cell}). */
        public int count(double radius) {
            if (radius <= 0) {
                return 0;
            }
            // Zoeken door te halveren: de need loopt op.
            int low = 0;
            int high = orderNeed.size();
            while (low < high) {
                int middle = (low + high) >>> 1;
                if (orderNeed.getFloat(middle) <= radius) {
                    low = middle + 1;
                } else {
                    high = middle;
                }
            }
            return low;
        }

        /** Cel nummer {@code i} (van dichtbij naar ver). */
        public long cell(int i) {
            return order.getLong(i);
        }

        /**
         * Hoeveel blokken van de lijn {@code from}-{@code to} door rook gaan, bij een wolk van deze grootte. We lopen de
         * lijn cel voor cel af (alleen het stuk binnen de wolk) en tellen de stukken in cellen met rook.
         */
        public double inside(double radius, Vec3 from, Vec3 to) {
            if (radius <= 0 || order.isEmpty()) {
                return 0;
            }
            Vec3 d = to.subtract(from);
            double length = d.length();
            if (length < 1.0E-6) {
                return 0;
            }
            // Het stuk van de lijn binnen het kader van de wolk (t van 0 tot 1 is de hele lijn).
            double t0 = 0;
            double t1 = 1;
            for (Direction.Axis axis : Direction.Axis.values()) {
                double start = axis.choose(from.x, from.y, from.z);
                double step = axis.choose(d.x, d.y, d.z);
                double min = bounds.min(axis);
                double max = bounds.max(axis);
                if (Math.abs(step) < 1.0E-12) {
                    if (start < min || start > max) {
                        return 0;
                    }
                    continue;
                }
                double a = (min - start) / step;
                double b = (max - start) / step;
                t0 = Math.max(t0, Math.min(a, b));
                t1 = Math.min(t1, Math.max(a, b));
            }
            if (t0 >= t1) {
                return 0;
            }
            // Cel voor cel (zoals een straal door een raster): steeds naar de dichtstbijzijnde celrand.
            Vec3 p = from.add(d.scale(t0));
            int x = Mth.floor(p.x);
            int y = Mth.floor(p.y);
            int z = Mth.floor(p.z);
            int stepX = (int) Math.signum(d.x);
            int stepY = (int) Math.signum(d.y);
            int stepZ = (int) Math.signum(d.z);
            double nextX = edge(p.x, x, d.x, t0);
            double nextY = edge(p.y, y, d.y, t0);
            double nextZ = edge(p.z, z, d.z, t0);
            double deltaX = d.x == 0 ? Double.MAX_VALUE : 1 / Math.abs(d.x);
            double deltaY = d.y == 0 ? Double.MAX_VALUE : 1 / Math.abs(d.y);
            double deltaZ = d.z == 0 ? Double.MAX_VALUE : 1 / Math.abs(d.z);
            double t = t0;
            double through = 0;
            while (t < t1) {
                double next = Math.min(t1, Math.min(nextX, Math.min(nextY, nextZ)));
                if (has(BlockPos.asLong(x, y, z), radius)) {
                    through += (next - t) * length;
                }
                t = next;
                if (nextX <= nextY && nextX <= nextZ) {
                    x += stepX;
                    nextX += deltaX;
                } else if (nextY <= nextZ) {
                    y += stepY;
                    nextY += deltaY;
                } else {
                    z += stepZ;
                    nextZ += deltaZ;
                }
            }
            return through;
        }

        /** Bij welke t (langs de lijn) de straal de volgende celrand op deze as raakt. */
        private static double edge(double at, int cell, double step, double t) {
            if (step > 0) {
                return t + (cell + 1 - at) / step;
            }
            if (step < 0) {
                return t + (cell - at) / step;
            }
            return Double.MAX_VALUE;
        }

        /**
         * Rekent uit waar de rook van een wolk rond {@code center} kan komen. Eerst de cellen, van dichtbij naar ver: de
         * rook stroomt naar elk van de 26 buurcellen (recht 1 blok, schuin 1,41 of 1,73) waar geen blok tussen zit, zolang
         * hij in totaal niet meer dan SMOKE_RADIUS aflegt. Dan de ruimte per cel: 0,5 naast een blok, en per stap verder
         * één meer.
         */
        public static Volume fill(BlockGetter level, Vec3 center) {
            Volume volume = new Volume();
            double max = ImtConfig.Weapons.SMOKE_RADIUS;
            Long2IntOpenHashMap sealed = new Long2IntOpenHashMap();
            sealed.defaultReturnValue(-1);
            BlockPos start = BlockPos.containing(center);
            if ((sealedAxes(level, start, sealed) & ALL) == ALL) {
                // Het midden zit in een blok (de granaat ligt tegen een laag plafond): dan bij de granaat zelf.
                start = start.below();
                if ((sealedAxes(level, start, sealed) & ALL) == ALL) {
                    return volume;
                }
            }
            // Van dichtbij naar ver: steeds de cel met de kortste weg (zoals een routeplanner).
            PriorityQueue<Step> queue = new PriorityQueue<>((a, b) -> Float.compare(a.need(), b.need()));
            queue.add(new Step(start, 0));
            Long2FloatOpenHashMap best = new Long2FloatOpenHashMap();
            best.defaultReturnValue(Float.MAX_VALUE);
            double minX = start.getX();
            double minY = start.getY();
            double minZ = start.getZ();
            double maxX = minX + 1;
            double maxY = minY + 1;
            double maxZ = minZ + 1;
            while (!queue.isEmpty()) {
                Step step = queue.poll();
                long key = step.pos().asLong();
                if (volume.need.containsKey(key)) {
                    continue;
                }
                volume.need.put(key, step.need());
                volume.order.add(key);
                volume.orderNeed.add(step.need());
                minX = Math.min(minX, step.pos().getX());
                minY = Math.min(minY, step.pos().getY());
                minZ = Math.min(minZ, step.pos().getZ());
                maxX = Math.max(maxX, step.pos().getX() + 1);
                maxY = Math.max(maxY, step.pos().getY() + 1);
                maxZ = Math.max(maxZ, step.pos().getZ() + 1);
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            int axes = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                            if (axes == 0) {
                                continue;
                            }
                            BlockPos next = step.pos().offset(dx, dy, dz);
                            long nextKey = next.asLong();
                            float need = step.need() + STEP[axes];
                            if (need > max || need >= best.get(nextKey) || volume.need.containsKey(nextKey)
                                    || !flows(level, step.pos(), new int[] {dx, dy, dz}, 0, sealed)) {
                                continue;
                            }
                            best.put(nextKey, need);
                            queue.add(new Step(next, need));
                        }
                    }
                }
            }
            volume.bounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            volume.measureRoom(level, sealed);
            return volume;
        }

        /** Een cel die de rook nog moet vullen, en hoe ver de rook daarvoor stroomt. */
        private record Step(BlockPos pos, float need) {}

        /** Hoe ver een stap is: recht (één as), schuin over twee assen, schuin over drie assen. */
        private static final float[] STEP = {0, 1, (float) Math.sqrt(2), (float) Math.sqrt(3)};

        /**
         * Kan de rook van {@code at} een stap {@code steps} (x, y, z: elk -1, 0 of 1) zetten? Een schuine stap kan alleen
         * als hij ook in rechte stapjes kan (in een of andere volgorde), zonder door een blok te gaan: zo glipt de rook
         * nooit tussen twee blokken door die met hun hoeken tegen elkaar staan. {@code done} zijn de assen die al gezet
         * zijn (als bitjes).
         */
        private static boolean flows(BlockGetter level, BlockPos at, int[] steps, int done, Long2IntOpenHashMap sealed) {
            boolean left = false;
            for (int axis = 0; axis < 3; axis++) {
                int bit = 1 << axis;
                if (steps[axis] == 0 || (done & bit) != 0) {
                    continue;
                }
                left = true;
                BlockPos next = at.offset(axis == 0 ? steps[0] : 0, axis == 1 ? steps[1] : 0, axis == 2 ? steps[2] : 0);
                if ((sealedAxes(level, at, sealed) & bit) == 0 && (sealedAxes(level, next, sealed) & bit) == 0
                        && flows(level, next, steps, done | bit, sealed)) {
                    return true;
                }
            }
            return !left; // alle stapjes gezet: we zijn er
        }

        /**
         * De ruimte per cel: 0,5 als er een blok tegenaan of schuin ernaast staat (ook een half blok of een deur), en
         * daarvandaan per stap door de rook één meer, tot hooguit {@link #OPEN}.
         */
        private void measureRoom(BlockGetter level, Long2IntOpenHashMap sealed) {
            LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
            BlockPos.MutableBlockPos around = new BlockPos.MutableBlockPos();
            for (int i = 0; i < order.size(); i++) {
                long cell = order.getLong(i);
                room.put(cell, OPEN);
                int x = BlockPos.getX(cell);
                int y = BlockPos.getY(cell);
                int z = BlockPos.getZ(cell);
                boolean nearBlock = false;
                for (int dx = -1; dx <= 1 && !nearBlock; dx++) {
                    for (int dy = -1; dy <= 1 && !nearBlock; dy++) {
                        for (int dz = -1; dz <= 1 && !nearBlock; dz++) {
                            around.set(x + dx, y + dy, z + dz);
                            nearBlock = (sealedAxes(level, around, sealed) & SOMETHING) != 0;
                        }
                    }
                }
                if (nearBlock) {
                    room.put(cell, 0.5f);
                    queue.enqueue(cell);
                }
            }
            while (!queue.isEmpty()) {
                long cell = queue.dequeueLong();
                float further = room.get(cell) + 1;
                if (further >= OPEN) {
                    continue;
                }
                for (Direction direction : Direction.values()) {
                    long next = BlockPos.offset(cell, direction);
                    if (need.containsKey(next) && room.get(next) > further) {
                        room.put(next, further);
                        queue.enqueue(next);
                    }
                }
            }
        }
    }

    // ================================================================ Wat de rook tegenhoudt

    /** Alle drie de assen dicht: een vol blok (of water, lava). */
    private static final int ALL = 0b111;
    /** Er staat iets (ook iets kleins, zoals een hek of een half blok): daar geen dikke rookplukken tegenaan. */
    private static final int SOMETHING = 0b1000;

    /**
     * Langs welke assen dit blok de doorgang helemaal afsluit, als bitjes (1 = x, 2 = y, 4 = z). Een vol blok sluit
     * alles af. Een dun blok sluit af als het, bekeken langs die as, het hele vlak bedekt: een dichte deur (langs de as
     * dwars door de deur), een dicht luik (van boven naar beneden), een ruit (dwars erdoor), een halve plaat (van boven
     * naar beneden). Een hek, een fakkel of een bloem laat de rook door. Staat er iets met een vorm, dan komt er ook het
     * bitje {@link #SOMETHING} bij. We onthouden het per cel in {@code sealed}.
     */
    private static int sealedAxes(BlockGetter level, BlockPos pos, Long2IntOpenHashMap sealed) {
        long key = pos.asLong();
        int known = sealed.get(key);
        if (known >= 0) {
            return known;
        }
        BlockState state = level.getBlockState(pos);
        int axes = 0;
        if (!state.getFluidState().isEmpty()) {
            axes = ALL | SOMETHING; // geen rook onder water of in lava
        } else {
            VoxelShape shape = state.getCollisionShape(level, pos);
            if (!shape.isEmpty()) {
                axes = SOMETHING;
                for (Direction.Axis axis : Direction.Axis.values()) {
                    if (covers(shape, axis)) {
                        axes |= 1 << axis.ordinal();
                    }
                }
            }
        }
        sealed.put(key, axes);
        return axes;
    }

    /**
     * Bedekken de doosjes van dit blok, platgedrukt langs {@code axis}, samen het hele vierkant van 1 bij 1? We delen
     * het vierkant op langs alle randen van de doosjes en kijken of elk vakje onder een doosje valt.
     */
    private static boolean covers(VoxelShape shape, Direction.Axis axis) {
        Direction.Axis u = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        Direction.Axis v = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        List<AABB> boxes = shape.toAabbs();
        List<Double> us = new ArrayList<>(List.of(0.0, 1.0));
        List<Double> vs = new ArrayList<>(List.of(0.0, 1.0));
        for (AABB box : boxes) {
            us.add(Mth.clamp(box.min(u), 0, 1));
            us.add(Mth.clamp(box.max(u), 0, 1));
            vs.add(Mth.clamp(box.min(v), 0, 1));
            vs.add(Mth.clamp(box.max(v), 0, 1));
        }
        us.sort(null);
        vs.sort(null);
        for (int i = 0; i + 1 < us.size(); i++) {
            double u0 = us.get(i);
            double u1 = us.get(i + 1);
            if (u1 - u0 < 1.0E-6) {
                continue;
            }
            for (int j = 0; j + 1 < vs.size(); j++) {
                double v0 = vs.get(j);
                double v1 = vs.get(j + 1);
                if (v1 - v0 < 1.0E-6) {
                    continue;
                }
                double mu = (u0 + u1) / 2;
                double mv = (v0 + v1) / 2;
                boolean covered = false;
                for (AABB box : boxes) {
                    if (box.min(u) <= mu && mu <= box.max(u) && box.min(v) <= mv && mv <= box.max(v)) {
                        covered = true;
                        break;
                    }
                }
                if (!covered) {
                    return false;
                }
            }
        }
        return true;
    }
}
