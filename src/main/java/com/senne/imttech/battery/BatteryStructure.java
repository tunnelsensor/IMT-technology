package com.senne.imttech.battery;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * De vorm van een Power Battery (hoofdstuk 47, jouw keuze: "zoals Mekanism"): een dichte doos van <b>Battery
 * Casing</b> (de buitenkant; een vlak mag ook <b>Battery Glass</b> zijn) met <b>Battery Cells</b> erin (elke cel geeft
 * opslag; lucht erin mag ook) en minstens één <b>Battery Port</b> in de wand (daar sluiten de kabels aan, en daar zit het
 * brein, zie BatteryPortBlockEntity). Van 3 x 3 x 3 tot {@link ImtConfig.Power#BATTERY_MAX_SIZE} (18) per kant.
 *
 * Zoeken gaat vanaf een poort (of een casing): alle aaneengesloten wandblokken samen bepalen de doos; dan kijken we of
 * de hele wand wandblok is en alles erin cel of lucht.
 */
public final class BatteryStructure {
    private BatteryStructure() {}

    /** Zoveel wandblokken volgen we hooguit (een doos van 18 x 18 x 18 heeft er 1736). */
    private static final int MAX_SHELL = 4000;

    /** Wat de poort zegt als er geen cellen in de doos zitten (Batteries.stats zet er de inhoud van een cel achter). */
    public static final String NO_CELLS = "No Battery Cells inside";

    /**
     * Een gevormde batterij.
     *
     * @param min   de kleinste hoek (een wandblok)
     * @param max   de grootste hoek
     * @param cells hoeveel Battery Cells erin zitten
     * @param ports alle poorten, de laagste (op volgorde) eerst: die is de baas
     */
    public record Structure(BlockPos min, BlockPos max, int cells, List<BlockPos> ports) {
        public BlockPos master() {
            return ports.getFirst();
        }

        public int sizeX() {
            return max.getX() - min.getX() + 1;
        }

        public int sizeY() {
            return max.getY() - min.getY() + 1;
        }

        public int sizeZ() {
            return max.getZ() - min.getZ() + 1;
        }

        /** "5 x 4 x 5". */
        public String size() {
            return sizeX() + " x " + sizeY() + " x " + sizeZ();
        }

        public boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }

        /** Zoveel FE past erin. */
        public long capacity() {
            return (long) cells * Math.max(1, Math.round(ImtConfig.Power.BATTERY_CELL_CAPACITY));
        }
    }

    /** Wat het zoeken opleverde: de batterij, of waarom het (nog) geen batterij is (in het Engels, voor het scherm). */
    public record Result(@Nullable Structure structure, String problem) {
        public boolean formed() {
            return structure != null;
        }
    }

    /** Is dit een blok van de wand: casing, glas of een poort? */
    public static boolean isShell(BlockState state) {
        return state.is(ModBlocks.BATTERY_CASING.get()) || state.is(ModBlocks.BATTERY_GLASS.get()) || state.is(ModBlocks.BATTERY_PORT.get());
    }

    /** Is dit een blok van een batterij (wand of cel)? */
    public static boolean isBatteryBlock(BlockState state) {
        return isShell(state) || state.is(ModBlocks.BATTERY_CELL.get());
    }

    /** Zoekt de batterij waar het wandblok op {@code start} bij hoort. */
    public static Result find(Level level, BlockPos start) {
        if (!isShell(level.getBlockState(start))) {
            return new Result(null, "This is not a battery wall block");
        }
        Set<BlockPos> shell = connected(level, start, true);
        if (shell.size() > MAX_SHELL) {
            return new Result(null, "Too big: a battery is at most " + maxSize() + " blocks per side");
        }
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : shell) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        BlockPos min = new BlockPos(minX, minY, minZ);
        BlockPos max = new BlockPos(maxX, maxY, maxZ);
        int sx = maxX - minX + 1;
        int sy = maxY - minY + 1;
        int sz = maxZ - minZ + 1;
        if (sx < 3 || sy < 3 || sz < 3) {
            return new Result(null, "Too small: a battery is at least 3 x 3 x 3 (a closed box of casing with cells inside)");
        }
        if (sx > maxSize() || sy > maxSize() || sz > maxSize()) {
            return new Result(null, "Too big: a battery is at most " + maxSize() + " blocks per side (this one is " + sx + " x " + sy + " x " + sz + ")");
        }
        int cells = 0;
        List<BlockPos> ports = new ArrayList<>();
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    probe.set(x, y, z);
                    BlockState state = level.getBlockState(probe);
                    boolean wall = x == minX || x == maxX || y == minY || y == maxY || z == minZ || z == maxZ;
                    if (wall) {
                        if (!isShell(state)) {
                            return new Result(null, "The wall is not closed: " + name(state) + " at " + x + " " + y + " " + z
                                    + " (use Battery Casing, Battery Glass or a Battery Port)");
                        }
                        if (state.is(ModBlocks.BATTERY_PORT.get())) {
                            ports.add(probe.immutable());
                        }
                    } else if (state.is(ModBlocks.BATTERY_CELL.get())) {
                        cells++;
                    } else if (!state.isAir()) {
                        return new Result(null, "Something else inside: " + name(state) + " at " + x + " " + y + " " + z
                                + " (inside go Battery Cells, or nothing)");
                    }
                }
            }
        }
        if (ports.isEmpty()) {
            return new Result(null, "No Battery Port in the wall: that is where the cables connect");
        }
        if (cells == 0) {
            // Hoeveel een cel houdt, komt er pas bij als het naar een scherm gaat: in de stijl van wie kijkt (Batteries.stats).
            return new Result(null, NO_CELLS);
        }
        ports.sort(null);
        return new Result(new Structure(min, max, cells, List.copyOf(ports)), "");
    }

    private static String name(BlockState state) {
        return state.getBlock().getName().getString();
    }

    public static int maxSize() {
        return Math.max(3, Math.min(64, ImtConfig.Power.BATTERY_MAX_SIZE));
    }

    /**
     * Alle batterijblokken die (via zes buren) aan {@code start} vastzitten: alleen de wand ({@code shellOnly}), of ook
     * de cellen. Stopt bij MAX_SHELL + 1 (dan is het hoe dan ook te groot).
     */
    static Set<BlockPos> connected(Level level, BlockPos start, boolean shellOnly) {
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        if (!(shellOnly ? isShell(level.getBlockState(start)) : isBatteryBlock(level.getBlockState(start)))) {
            return seen;
        }
        seen.add(start.immutable());
        todo.add(start.immutable());
        while (!todo.isEmpty() && seen.size() <= MAX_SHELL) {
            BlockPos pos = todo.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (seen.contains(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState state = level.getBlockState(next);
                if (shellOnly ? isShell(state) : isBatteryBlock(state)) {
                    seen.add(next);
                    todo.add(next);
                }
            }
        }
        return seen;
    }

    /** De poorten die bij de batterijblokken rond {@code pos} horen (ook als {@code pos} net leeg werd). */
    public static List<BlockPos> portsAround(Level level, BlockPos pos) {
        Set<BlockPos> all = new HashSet<>(connected(level, pos, false));
        for (Direction direction : Direction.values()) {
            BlockPos next = pos.relative(direction);
            if (!all.contains(next)) {
                all.addAll(connected(level, next, false));
            }
        }
        List<BlockPos> ports = new ArrayList<>();
        for (BlockPos block : all) {
            if (level.getBlockState(block).is(ModBlocks.BATTERY_PORT.get())) {
                ports.add(block);
            }
        }
        return ports;
    }
}
