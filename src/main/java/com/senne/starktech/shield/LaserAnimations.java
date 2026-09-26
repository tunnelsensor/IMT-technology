package com.senne.starktech.shield;

import com.senne.starktech.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

/**
 * Het open- en dichtschuiven van een rayshield.
 *
 * Een rayshield klapt niet in één keer aan: het veld schuift uit de zender naar voren tot het
 * eind, en schuift bij het uitgaan weer terug de zender in. Dat geeft je net genoeg tijd om er
 * nog doorheen te duiken. (Vroeger groeide het van twee kanten naar het midden, maar zonder
 * tweede zender aan de overkant kwam er dan een wand uit het niets.)
 *
 * Per stap zetten we één blok neer (of halen we er één weg), om de paar ticks.
 */
public final class LaserAnimations {
    private LaserAnimations() {}

    /** Hoeveel ticks tussen twee stappen. Lager = sneller dicht. */
    private static final int TICKS_PER_STEP = 2;

    /** Eén wand die op dit moment open- of dichtschuift. */
    private static final class Sliding {
        private final ResourceKey<Level> dimension;
        private final List<BlockPos> cells;
        private final BlockState wall;
        private final ShieldColor color;
        private final boolean closing;
        private int step;
        private int cooldown;

        private Sliding(ResourceKey<Level> dimension, List<BlockPos> cells, BlockState wall,
                        ShieldColor color, boolean closing) {
            this.dimension = dimension;
            this.cells = cells;
            this.wall = wall;
            this.color = color;
            this.closing = closing;
        }
    }

    private static final List<Sliding> RUNNING = new ArrayList<>();

    /**
     * Begin met dichtschuiven (of openschuiven) van deze wand.
     * Een wand die al bezig was op dezelfde plek, laten we los: de nieuwe opdracht wint.
     */
    public static void start(ServerLevel level, List<BlockPos> cells, BlockState wall, ShieldColor color, boolean closing) {
        if (cells.isEmpty()) {
            return;
        }
        RUNNING.removeIf(running -> running.dimension == level.dimension() && running.cells.equals(cells));
        RUNNING.add(new Sliding(level.dimension(), List.copyOf(cells), wall, color, closing));
    }

    /** Stopt de animatie voor deze plekken (bv. als de zender gesloopt wordt). */
    public static void forget(ServerLevel level, List<BlockPos> cells) {
        RUNNING.removeIf(running -> running.dimension == level.dimension() && running.cells.equals(cells));
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (RUNNING.isEmpty()) {
            return;
        }
        // We lopen door een kopie van de lijst: een blok neerzetten laat de buren reageren, en een
        // zender ernaast kan dan zelf een wand starten of stoppen (en dus RUNNING veranderen).
        // Door de echte lijst lopen terwijl die verandert, liet de server crashen.
        for (Sliding sliding : List.copyOf(RUNNING)) {
            if (!RUNNING.contains(sliding)) {
                continue; // intussen vervangen of gestopt
            }
            if (--sliding.cooldown > 0) {
                continue;
            }
            sliding.cooldown = TICKS_PER_STEP;
            ServerLevel level = event.getServer().getLevel(sliding.dimension);
            if (level == null || sliding.step >= sliding.cells.size()) {
                RUNNING.remove(sliding);
                continue;
            }
            BlockPos target = nextCell(sliding);
            sliding.step++;
            if (!level.hasChunkAt(target)) {
                continue;
            }
            if (sliding.closing) {
                if (level.getBlockState(target).isAir()) {
                    level.setBlock(target, sliding.wall, Block.UPDATE_ALL);
                    spark(level, target, sliding.color);
                }
            } else if (level.getBlockState(target).is(ModBlocks.LASER_WALL.get())) {
                level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                spark(level, target, sliding.color);
            }
        }
    }

    /**
     * Welke plek is nu aan de beurt? De plekken staan op volgorde vanaf de zender. Dichtschuiven
     * gaat van de zender naar het eind, openschuiven van het eind terug naar de zender.
     */
    private static BlockPos nextCell(Sliding sliding) {
        int size = sliding.cells.size();
        return sliding.closing ? sliding.cells.get(sliding.step) : sliding.cells.get(size - 1 - sliding.step);
    }

    /** Een vonkje in de kleur van het veld, op de plek die net veranderde. */
    private static void spark(ServerLevel level, BlockPos pos, ShieldColor color) {
        float r = ((color.rgb() >> 16) & 0xFF) / 255.0f;
        float g = ((color.rgb() >> 8) & 0xFF) / 255.0f;
        float b = (color.rgb() & 0xFF) / 255.0f;
        level.sendParticles(new DustParticleOptions(new Vector3f(r, g, b), 1.0f),
                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 3, 0.25, 0.25, 0.25, 0.0);
    }

    /** Wereld gesloten: niets meer te schuiven. */
    public static void clear() {
        RUNNING.clear();
    }
}
