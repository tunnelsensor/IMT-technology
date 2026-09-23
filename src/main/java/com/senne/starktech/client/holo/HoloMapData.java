package com.senne.starktech.client.holo;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * De hoogtekaart voor het hologram, en hoe vaak hij ververst wordt.
 *
 * Een kaart van 24 bij 24 vakjes met 16 metingen per vakje betekent zo'n negenduizend
 * opzoekingen in de wereld. Dat elke frame doen zou zonde zijn, dus rekenen we hem hooguit
 * eens per paar seconden opnieuw uit en gebruiken we daartussen dezelfde gegevens. Dat je een
 * berg een seconde later ziet verdwijnen als je hem afgraaft, valt niemand op.
 */
public class HoloMapData {
    /**
     * Hoeveel vakjes de kaart breed en diep is.
     *
     * Het waren er 32: dan lagen de lijnen op het hologram zo dicht op elkaar dat je van een
     * paar blokken afstand één dichte blauwe plaat zag in plaats van een landschap.
     */
    public static final int CELLS = 24;
    /** Zoveel metingen per vakje in elke richting (dus 4 x 4 = 16 per vakje). */
    private static final int SAMPLES = 4;
    /** Zo vaak rekenen we de kaart opnieuw uit (in ticks). 60 ticks = 3 seconden. */
    private static final long REFRESH_TICKS = 60;
    /**
     * Hoogteverschil (in blokken) dat overeenkomt met de volle hoogte van het hologram.
     * Veel kleiner dan echt, zodat ook een heuvel van een paar blokken te zien is.
     */
    private static final float HEIGHT_SCALE = 16.0f;

    private @Nullable Snapshot snapshot;
    private long builtAt = Long.MIN_VALUE;
    private @Nullable BlockPos builtFor;
    private int builtRadius = -1;

    /**
     * De kaart van nu; wordt zo nodig opnieuw uitgerekend.
     *
     * @param radius hoe ver de kaart reikt (in blokken); komt uit de instellingen van de tafel
     */
    public Snapshot get(Level level, BlockPos table, int radius) {
        long now = level.getGameTime();
        boolean stale = snapshot == null || !table.equals(builtFor) || radius != builtRadius
                || now - builtAt >= REFRESH_TICKS;
        if (stale) {
            snapshot = build(level, table, radius);
            builtAt = now;
            builtFor = table;
            builtRadius = radius;
        }
        return snapshot;
    }

    private static Snapshot build(Level level, BlockPos table, int radius) {
        float[] heights = new float[CELLS * CELLS];
        boolean[] water = new boolean[CELLS * CELLS];
        int originX = table.getX() - radius;
        int originZ = table.getZ() - radius;
        // Hoe groter het bereik, hoe meer blokken één vakje beslaat. Met kommagetallen: bij een
        // bereik van 24 is een vakje twee blokken, en met afgeronde getallen viel de kaart
        // kleiner uit dan de plek waar de spelers en waypoints op stonden.
        double blocksPerCell = (radius * 2.0) / CELLS;
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int cx = 0; cx < CELLS; cx++) {
            for (int cz = 0; cz < CELLS; cz++) {
                // Het hoogste punt binnen het vakje, niet alleen het midden: anders valt een
                // smalle toren of boom tussen de metingen door en zie je hem nooit.
                int highest = Integer.MIN_VALUE;
                int wet = 0;
                int measured = 0;
                for (int sx = 0; sx < SAMPLES; sx++) {
                    for (int sz = 0; sz < SAMPLES; sz++) {
                        int x = (int) Math.floor(originX + (cx + (sx + 0.5) / SAMPLES) * blocksPerCell);
                        int z = (int) Math.floor(originZ + (cz + (sz + 0.5) / SAMPLES) * blocksPerCell);
                        probe.set(x, table.getY(), z);
                        // Niet geladen stukken slaan we over: daar weten we de hoogte niet van.
                        if (!level.hasChunkAt(probe)) {
                            continue;
                        }
                        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                        highest = Math.max(highest, top);
                        measured++;
                        probe.setY(top - 1);
                        if (level.getFluidState(probe).is(FluidTags.WATER)) {
                            wet++;
                        }
                    }
                }
                int index = cx * CELLS + cz;
                if (measured == 0) {
                    heights[index] = Float.NaN;
                    continue;
                }
                heights[index] = Mth.clamp((highest - table.getY()) / HEIGHT_SCALE, -1.0f, 1.0f);
                // Meer dan de helft water: dan tekenen we het vakje als water.
                water[index] = wet * 2 > measured;
            }
        }
        return new Snapshot(table, originX, originZ, radius, blocksPerCell, heights, water);
    }

    /** Eén uitgerekende kaart. */
    public record Snapshot(BlockPos table, int originX, int originZ, int radius, double blocksPerCell,
                           float[] heights, boolean[] water) {

        /** Hoe hoog het vakje is, van -1 (diep) tot 1 (hoog), of NaN als het niet geladen is. */
        public float relativeHeight(int cellX, int cellZ) {
            if (cellX < 0 || cellX >= CELLS || cellZ < 0 || cellZ >= CELLS) {
                return Float.NaN;
            }
            return heights[cellX * CELLS + cellZ];
        }

        /** Staat dit vakje grotendeels onder water? */
        public boolean isWater(int cellX, int cellZ) {
            if (cellX < 0 || cellX >= CELLS || cellZ < 0 || cellZ >= CELLS) {
                return false;
            }
            return water[cellX * CELLS + cellZ];
        }

        /** Idem, maar voor een plek in de wereld. */
        public float relativeHeightAt(double worldX, double worldZ) {
            int cellX = (int) Math.floor((worldX - originX) / blocksPerCell);
            int cellZ = (int) Math.floor((worldZ - originZ) / blocksPerCell);
            return relativeHeight(cellX, cellZ);
        }

        /**
         * Rekent een plek in de wereld om naar een plek in het hologram.
         *
         * @return de x en z binnen het hologram, of null als die plek buiten de kaart valt
         */
        public float @Nullable [] project(double worldX, double worldZ, float size) {
            double fx = (worldX - originX) / (radius * 2.0);
            double fz = (worldZ - originZ) / (radius * 2.0);
            if (fx < 0 || fx > 1 || fz < 0 || fz > 1) {
                return null;
            }
            return new float[]{(float) (fx - 0.5) * size, (float) (fz - 0.5) * size};
        }
    }
}
