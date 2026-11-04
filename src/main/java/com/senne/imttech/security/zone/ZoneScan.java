package com.senne.imttech.security.zone;

import com.senne.imttech.config.ImtConfig;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * De Autoscan van een zone (hoofdstuk 45, jouw wens: "autoscan zodat je makkelijk je bunker kunt scannen en protecten,
 * doet altijd 3 blokken in de muur zodat eventuele redstone ook wordt meegenomen; als het geen bunker is, is het
 * moeilijker, maar zoek daar ook iets goeds voor").
 * <ul>
 *   <li><b>Een bunker</b> (dicht, deuren toe): vanaf het startpunt vullen we alle kamers die open met elkaar verbonden
 *       zijn (lucht en alles zonder botsing: een open deur, tapijt, een knop), en daar komt de muur bij: elk vast blok
 *       binnen {@link ImtConfig.Security#ZONE_SCAN_WALL} blokken van een kamer. Dat wordt een stel kubussen.</li>
 *   <li><b>Een open basis</b> (het vullen loopt weg naar buiten, of je klikt een blok van het gebouw aan): we volgen alle
 *       aaneengesloten gebouwde blokken (alles wat geen natuur is, zie de tag {@code imttech:natural}) en leggen er
 *       één doos omheen met {@code ZONE_SCAN_WALL} blokken marge (jouw keuze).</li>
 * </ul>
 * Alles blijft binnen het bereik van de kern ({@code limit}). Meer dan {@link ImtConfig.Security#ZONE_SCAN_MAX} blokken
 * vullen we niet: dan is het geen bunker (en bij een gebouw: te groot).
 */
public final class ZoneScan {
    private ZoneScan() {}

    /** Blokken die de natuur maakt (steen, aarde, zand, erts, bomen...): die horen niet bij een gebouw. */
    public static final TagKey<Block> NATURAL = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("imttech", "natural"));

    /**
     * Wat de scan vond.
     *
     * @param boxes    de kubussen (leeg als er niets te vinden was)
     * @param enclosed true: een dichte bunker (kamers plus muur); false: een open basis (een doos om het gebouw)
     * @param cells    hoeveel blokken de kamers (of het gebouw) telden
     * @param problem  waarom het niet lukte (in het Engels, voor in het spel), of null
     */
    public record Result(List<ZoneBox> boxes, boolean enclosed, int cells, String problem) {
        static Result failed(String problem) {
            return new Result(List.of(), false, 0, problem);
        }

        public boolean ok() {
            return problem == null;
        }
    }

    /**
     * Scant vanaf {@code start}: een blok in een kamer (lucht), of een blok van het gebouw. {@code limit} is hoe ver de
     * zone mag komen (het bereik van de kern); {@code maxBoxes} hoeveel kubussen er hooguit uit mogen komen.
     */
    public static Result scan(ServerLevel level, BlockPos start, ZoneBox limit, int maxBoxes) {
        int wall = Math.max(0, Math.round(ImtConfig.Security.ZONE_SCAN_WALL));
        int max = Math.max(1000, Math.round(ImtConfig.Security.ZONE_SCAN_MAX));
        if (!limit.contains(start)) {
            return Result.failed("That is outside the reach of your core");
        }
        BlockState state = level.getBlockState(start);
        if (passable(level, start, state)) {
            LongOpenHashSet rooms = fill(level, start, limit, max, (pos, s) -> passable(level, pos, s));
            if (rooms != null) {
                // Een dichte bunker: de kamers plus de muur eromheen.
                LongOpenHashSet cells = new LongOpenHashSet(rooms);
                BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
                for (long room : rooms) {
                    int rx = BlockPos.getX(room);
                    int ry = BlockPos.getY(room);
                    int rz = BlockPos.getZ(room);
                    for (int dx = -wall; dx <= wall; dx++) {
                        for (int dy = -wall; dy <= wall; dy++) {
                            for (int dz = -wall; dz <= wall; dz++) {
                                probe.set(rx + dx, ry + dy, rz + dz);
                                if (limit.contains(probe) && !cells.contains(probe.asLong()) && !passable(level, probe, level.getBlockState(probe))) {
                                    cells.add(probe.asLong());
                                }
                            }
                        }
                    }
                }
                return new Result(boxes(cells, maxBoxes), true, rooms.size(), null);
            }
            // Het vullen liep weg naar buiten: dan een gebouw, als er een vast blok naast het startpunt staat.
            BlockPos solid = null;
            for (Direction direction : Direction.values()) {
                BlockPos next = start.relative(direction);
                if (isBuilt(level.getBlockState(next))) {
                    solid = next;
                    break;
                }
            }
            if (solid == null) {
                return Result.failed("This is not a closed room (close the doors, or click a block of your building)");
            }
            start = solid;
        } else if (!isBuilt(state)) {
            return Result.failed("That is a natural block: click a block of your building, or stand inside a closed room");
        }
        // Een open basis: alle gebouwde blokken die aan elkaar vastzitten, met een doos eromheen.
        LongOpenHashSet built = fill(level, start, limit, max, (pos, s) -> isBuilt(s));
        if (built == null) {
            return Result.failed("Your building is too big to scan in one go (more than " + max + " blocks): scan a part with the Zone Marker");
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long cell : built) {
            minX = Math.min(minX, BlockPos.getX(cell));
            minY = Math.min(minY, BlockPos.getY(cell));
            minZ = Math.min(minZ, BlockPos.getZ(cell));
            maxX = Math.max(maxX, BlockPos.getX(cell));
            maxY = Math.max(maxY, BlockPos.getY(cell));
            maxZ = Math.max(maxZ, BlockPos.getZ(cell));
        }
        ZoneBox box = ZoneBox.of(new BlockPos(minX - wall, minY - wall, minZ - wall), new BlockPos(maxX + wall, maxY + wall, maxZ + wall))
                .clamp(limit.min(), limit.max());
        return new Result(box == null ? List.of() : List.of(box), false, built.size(), null);
    }

    /** Kun je hier staan of doorheen: lucht, of een blok zonder botsing (tapijt, een knop, een open deur)? */
    static boolean passable(ServerLevel level, BlockPos pos, BlockState state) {
        return state.isAir() || !(state.getBlock() instanceof LiquidBlock) && state.getCollisionShape(level, pos).isEmpty();
    }

    /** Hoort dit blok bij een gebouw: vast, en niet iets dat de natuur maakt? */
    static boolean isBuilt(BlockState state) {
        return !state.isAir() && !(state.getBlock() instanceof LiquidBlock) && !state.is(NATURAL);
    }

    private interface Cell {
        boolean matches(BlockPos pos, BlockState state);
    }

    /**
     * Vult vanaf {@code start} alle buren (zes kanten) waar {@code cell} ja op zegt, binnen {@code limit}. Geeft null als
     * het er meer dan {@code max} worden, of als het vullen tegen de rand van {@code limit} komt (dan is het niet dicht).
     */
    private static LongOpenHashSet fill(ServerLevel level, BlockPos start, ZoneBox limit, int max, Cell cell) {
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue todo = new LongArrayFIFOQueue();
        seen.add(start.asLong());
        todo.enqueue(start.asLong());
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        while (!todo.isEmpty()) {
            long here = todo.dequeueLong();
            int x = BlockPos.getX(here);
            int y = BlockPos.getY(here);
            int z = BlockPos.getZ(here);
            for (Direction direction : Direction.values()) {
                probe.set(x + direction.getStepX(), y + direction.getStepY(), z + direction.getStepZ());
                if (!limit.contains(probe)) {
                    return null; // tot aan het bereik: dit is niet dicht (of te groot)
                }
                if (probe.getY() < level.getMinBuildHeight() || probe.getY() >= level.getMaxBuildHeight()) {
                    continue;
                }
                long key = probe.asLong();
                if (seen.contains(key) || !cell.matches(probe, level.getBlockState(probe))) {
                    continue;
                }
                seen.add(key);
                if (seen.size() > max) {
                    return null;
                }
                todo.enqueue(key);
            }
        }
        return seen;
    }

    // ================================================================ Blokken naar kubussen

    /**
     * Maakt kubussen van een stel blokken: steeds vanaf een blok dat nog nergens in zit een zo groot mogelijke kubus
     * (eerst naar het oosten, dan naar het zuiden, dan omhoog). Komen er meer dan {@code maxBoxes}, dan voegen we
     * kubussen samen (de twee die samen het minste lucht erbij nemen), tot het past. Een kubus mag iets te groot zijn:
     * een zone die een blok meer beschermt, is geen ramp.
     */
    static List<ZoneBox> boxes(LongOpenHashSet cells, int maxBoxes) {
        List<ZoneBox> boxes = new ArrayList<>();
        LongOpenHashSet left = new LongOpenHashSet(cells);
        long[] sorted = left.toLongArray();
        it.unimi.dsi.fastutil.longs.LongArrays.quickSort(sorted, (a, b) -> {
            int c = Integer.compare(BlockPos.getY(a), BlockPos.getY(b));
            if (c == 0) {
                c = Integer.compare(BlockPos.getZ(a), BlockPos.getZ(b));
            }
            return c == 0 ? Integer.compare(BlockPos.getX(a), BlockPos.getX(b)) : c;
        });
        for (long seed : sorted) {
            if (!left.contains(seed)) {
                continue;
            }
            int x0 = BlockPos.getX(seed);
            int y0 = BlockPos.getY(seed);
            int z0 = BlockPos.getZ(seed);
            int x1 = x0;
            while (left.contains(BlockPos.asLong(x1 + 1, y0, z0))) {
                x1++;
            }
            int z1 = z0;
            while (rowFree(left, x0, x1, y0, z1 + 1)) {
                z1++;
            }
            int y1 = y0;
            while (slabFree(left, x0, x1, y1 + 1, z0, z1)) {
                y1++;
            }
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        left.remove(BlockPos.asLong(x, y, z));
                    }
                }
            }
            boxes.add(ZoneBox.of(new BlockPos(x0, y0, z0), new BlockPos(x1, y1, z1)));
        }
        while (boxes.size() > Math.max(1, maxBoxes)) {
            mergeClosest(boxes);
        }
        return boxes;
    }

    private static boolean rowFree(LongOpenHashSet left, int x0, int x1, int y, int z) {
        for (int x = x0; x <= x1; x++) {
            if (!left.contains(BlockPos.asLong(x, y, z))) {
                return false;
            }
        }
        return true;
    }

    private static boolean slabFree(LongOpenHashSet left, int x0, int x1, int y, int z0, int z1) {
        for (int z = z0; z <= z1; z++) {
            if (!rowFree(left, x0, x1, y, z)) {
                return false;
            }
        }
        return true;
    }

    /** Voegt de twee kubussen samen waarvan de doos eromheen het minste erbij neemt. */
    private static void mergeClosest(List<ZoneBox> boxes) {
        int bestA = 0;
        int bestB = 1;
        long bestExtra = Long.MAX_VALUE;
        for (int a = 0; a < boxes.size(); a++) {
            for (int b = a + 1; b < boxes.size(); b++) {
                ZoneBox union = union(boxes.get(a), boxes.get(b));
                long extra = union.volume() - boxes.get(a).volume() - boxes.get(b).volume();
                if (extra < bestExtra) {
                    bestExtra = extra;
                    bestA = a;
                    bestB = b;
                }
            }
        }
        ZoneBox merged = union(boxes.get(bestA), boxes.get(bestB));
        boxes.remove(bestB);
        boxes.set(bestA, merged);
    }

    /** De kleinste kubus om twee kubussen heen. */
    static ZoneBox union(ZoneBox a, ZoneBox b) {
        return ZoneBox.of(new BlockPos(Math.min(a.min().getX(), b.min().getX()), Math.min(a.min().getY(), b.min().getY()),
                Math.min(a.min().getZ(), b.min().getZ())), new BlockPos(Math.max(a.max().getX(), b.max().getX()),
                Math.max(a.max().getY(), b.max().getY()), Math.max(a.max().getZ(), b.max().getZ())));
    }

    // ================================================================ In de zone zetten

    /**
     * Zet de gevonden kubussen in de zone van {@code core}: een kubus die al helemaal in de zone ligt slaan we over, een
     * oude kubus die helemaal in een nieuwe ligt gaat weg (zo kun je gerust nog eens scannen), en een kubus die een
     * andere kern in de weg zit komt er niet in. Geeft terug wat er gebeurde, als tekst voor onderaan het scherm.
     */
    public static String apply(com.senne.imttech.security.SecurityCoreBlockEntity core, Result result, int maxBoxes) {
        List<ZoneBox> boxes = new ArrayList<>(core.zoneBoxes());
        int added = 0;
        int blocked = 0;
        int replaced = 0;
        for (ZoneBox found : result.boxes()) {
            ZoneBox fitted = found.clamp(core.zoneLow(), core.zoneHigh());
            if (fitted == null || boxes.stream().anyMatch(old -> old.contains(fitted))) {
                continue;
            }
            if (core.zoneBoxBlocked(fitted)) {
                blocked++;
                continue;
            }
            int before = boxes.size();
            boxes.removeIf(fitted::contains);
            replaced += before - boxes.size();
            boxes.add(fitted);
            added++;
        }
        while (boxes.size() > maxBoxes) {
            mergeClosest(boxes);
        }
        List<ZoneBox> fitted = core.setZoneBoxes(boxes);
        long volume = 0;
        for (ZoneBox box : fitted) {
            volume += box.volume();
        }
        StringBuilder text = new StringBuilder("Autoscan");
        text.append(result.enclosed() ? " (closed rooms + " + Math.round(ImtConfig.Security.ZONE_SCAN_WALL) + " blocks of wall): "
                : " (open base: a box around " + result.cells() + " built blocks + " + Math.round(ImtConfig.Security.ZONE_SCAN_WALL) + "): ");
        text.append(added == 0 ? "nothing new, your zone already covers it" : added + (added == 1 ? " box added" : " boxes added"));
        if (replaced > 0) {
            text.append(", " + replaced + " old " + (replaced == 1 ? "box" : "boxes") + " replaced");
        }
        if (blocked > 0) {
            text.append(", " + blocked + " skipped (too close to another security network)");
        }
        text.append(". Zone: " + fitted.size() + (fitted.size() == 1 ? " box, " : " boxes, ") + String.format("%,d", volume) + " blocks");
        core.logEvent(com.senne.imttech.security.os.EventLog.Type.ZONE, "Zone: autoscan, " + added + " added, " + replaced + " replaced");
        return text.toString();
    }
}
