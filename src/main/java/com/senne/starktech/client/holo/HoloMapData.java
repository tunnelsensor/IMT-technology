package com.senne.starktech.client.holo;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jetbrains.annotations.Nullable;

/**
 * De hoogtekaart voor het hologram, en hoe vaak hij ververst wordt.
 *
 * Een kaart van 32 bij 32 vakjes betekent ruim duizend opzoekingen in de wereld. Dat elke
 * frame doen zou zonde zijn, dus rekenen we hem hooguit eens per paar seconden opnieuw uit
 * en gebruiken we daartussen dezelfde gegevens. Dat je een berg een seconde later ziet
 * verdwijnen als je hem afgraaft, valt niemand op.
 */
public class HoloMapData {
    /** Hoeveel vakjes de kaart breed en diep is. */
    public static final int CELLS = 32;
    /** Zo vaak rekenen we de kaart opnieuw uit (in ticks). 60 ticks = 3 seconden. */
    private static final long REFRESH_TICKS = 60;
    /**
     * Hoogteverschil (in blokken) dat overeenkomt met de volle hoogte van het hologram.
     * Wat kleiner dan echt, zodat ook een heuvel van een paar blokken te zien is.
     */
    private static final float HEIGHT_SCALE = 20.0f;

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
        int originX = table.getX() - radius;
        int originZ = table.getZ() - radius;
        // Hoe groter het bereik, hoe meer blokken één vakje beslaat. Met kommagetallen: bij een
        // bereik van 24 is een vakje anderhalf blok, en met hele getallen werd dat 1, waardoor
        // de kaart kleiner uitviel dan de plek waar de spelers en waypoints op stonden.
        double blocksPerCell = (radius * 2.0) / CELLS;
        for (int cx = 0; cx < CELLS; cx++) {
            for (int cz = 0; cz < CELLS; cz++) {
                int x = (int) Math.floor(originX + (cx + 0.5) * blocksPerCell);
                int z = (int) Math.floor(originZ + (cz + 0.5) * blocksPerCell);
                // Niet geladen stukken slaan we over: daar weten we de hoogte niet van.
                if (!level.hasChunkAt(new BlockPos(x, table.getY(), z))) {
                    heights[cx * CELLS + cz] = Float.NaN;
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                heights[cx * CELLS + cz] = Mth.clamp((top - table.getY()) / HEIGHT_SCALE, -1.0f, 1.0f);
            }
        }
        return new Snapshot(table, originX, originZ, radius, blocksPerCell, heights);
    }

    /** Eén uitgerekende kaart. */
    public record Snapshot(BlockPos table, int originX, int originZ, int radius, double blocksPerCell,
                           float[] heights) {

        /** Hoe hoog het vakje is, van -1 (diep) tot 1 (hoog), of NaN als het niet geladen is. */
        public float relativeHeight(int cellX, int cellZ) {
            if (cellX < 0 || cellX >= CELLS || cellZ < 0 || cellZ >= CELLS) {
                return Float.NaN;
            }
            return heights[cellX * CELLS + cellZ];
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
