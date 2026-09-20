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
    /** Hoe ver de kaart reikt vanaf de tafel (in blokken). */
    public static final int RADIUS = 48;
    /** Hoeveel blokken één vakje beslaat. */
    private static final int BLOCKS_PER_CELL = (RADIUS * 2) / CELLS;
    /** Zo vaak rekenen we de kaart opnieuw uit (in ticks). 60 ticks = 3 seconden. */
    private static final long REFRESH_TICKS = 60;
    /** Hoogteverschil (in blokken) dat overeenkomt met de volle hoogte van het hologram. */
    private static final float HEIGHT_SCALE = 32.0f;

    private @Nullable Snapshot snapshot;
    private long builtAt = Long.MIN_VALUE;
    private @Nullable BlockPos builtFor;

    /** De kaart van nu; wordt zo nodig opnieuw uitgerekend. */
    public Snapshot get(Level level, BlockPos table) {
        long now = level.getGameTime();
        if (snapshot == null || !table.equals(builtFor) || now - builtAt >= REFRESH_TICKS) {
            snapshot = build(level, table);
            builtAt = now;
            builtFor = table;
        }
        return snapshot;
    }

    private static Snapshot build(Level level, BlockPos table) {
        float[] heights = new float[CELLS * CELLS];
        int originX = table.getX() - RADIUS;
        int originZ = table.getZ() - RADIUS;
        for (int cx = 0; cx < CELLS; cx++) {
            for (int cz = 0; cz < CELLS; cz++) {
                int x = originX + cx * BLOCKS_PER_CELL + BLOCKS_PER_CELL / 2;
                int z = originZ + cz * BLOCKS_PER_CELL + BLOCKS_PER_CELL / 2;
                // Niet geladen stukken slaan we over: daar weten we de hoogte niet van.
                if (!level.hasChunkAt(new BlockPos(x, table.getY(), z))) {
                    heights[cx * CELLS + cz] = Float.NaN;
                    continue;
                }
                int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                heights[cx * CELLS + cz] = Mth.clamp((top - table.getY()) / HEIGHT_SCALE, -1.0f, 1.0f);
            }
        }
        return new Snapshot(table, originX, originZ, heights);
    }

    /** Eén uitgerekende kaart. */
    public record Snapshot(BlockPos table, int originX, int originZ, float[] heights) {

        /** Hoe hoog het vakje is, van -1 (diep) tot 1 (hoog), of NaN als het niet geladen is. */
        public float relativeHeight(int cellX, int cellZ) {
            if (cellX < 0 || cellX >= CELLS || cellZ < 0 || cellZ >= CELLS) {
                return Float.NaN;
            }
            return heights[cellX * CELLS + cellZ];
        }

        /** Idem, maar voor een plek in de wereld. */
        public float relativeHeightAt(double worldX, double worldZ) {
            int cellX = (int) Math.floor((worldX - originX) / BLOCKS_PER_CELL);
            int cellZ = (int) Math.floor((worldZ - originZ) / BLOCKS_PER_CELL);
            return relativeHeight(cellX, cellZ);
        }

        /**
         * Rekent een plek in de wereld om naar een plek in het hologram.
         *
         * @return de x en z binnen het hologram, of null als die plek buiten de kaart valt
         */
        public float @Nullable [] project(double worldX, double worldZ, float size) {
            double fx = (worldX - originX) / (RADIUS * 2.0);
            double fz = (worldZ - originZ) / (RADIUS * 2.0);
            if (fx < 0 || fx > 1 || fz < 0 || fz > 1) {
                return null;
            }
            return new float[]{(float) (fx - 0.5) * size, (float) (fz - 0.5) * size};
        }
    }
}
