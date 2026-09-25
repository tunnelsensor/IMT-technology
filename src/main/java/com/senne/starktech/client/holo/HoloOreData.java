package com.senne.starktech.client.holo;

import com.senne.starktech.client.OreType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * De ertsen onder de holotafel, voor de stand "Ores".
 *
 * We kijken in een blok van RADIUS blokken naar alle kanten en van net boven de tafel tot DEPTH
 * blokken diep. Dat zijn zo'n zestigduizend blokken, dus net als bij de kaart doen we dat niet
 * elke frame maar hooguit eens per paar seconden (REFRESH_TICKS).
 *
 * Welke blokken erts zijn en welke kleur ze krijgen, bepaalt OreType: dezelfde soorten en
 * kleuren als de ertsscanner van de bril.
 */
public class HoloOreData {
    /** Hoe ver naar opzij we kijken (blokken). */
    public static final int RADIUS = 16;
    /** Hoe diep we kijken (blokken onder de tafel), en hoe ver erboven. */
    public static final int DEPTH = 48;
    public static final int ABOVE = 8;
    /** Zo vaak kijken we opnieuw (ticks). 100 ticks = 5 seconden. */
    private static final long REFRESH_TICKS = 100;

    /** Eén erts: waar het zit ten opzichte van de tafel, en welke soort. */
    public record Ore(int dx, int dy, int dz, OreType type) {}

    /** Wat we de vorige keer vonden: de ertsen, en hoe diep de grond daar echt gaat. */
    public record Snapshot(List<Ore> ores, int bottom) {}

    private @Nullable Snapshot snapshot;
    private long builtAt = Long.MIN_VALUE;
    private @Nullable BlockPos builtFor;

    public Snapshot get(Level level, BlockPos table) {
        long now = level.getGameTime();
        if (snapshot == null || !table.equals(builtFor) || now - builtAt >= REFRESH_TICKS) {
            snapshot = scan(level, table);
            builtAt = now;
            builtFor = table;
        }
        return snapshot;
    }

    private static Snapshot scan(Level level, BlockPos table) {
        List<Ore> ores = new ArrayList<>();
        // Niet dieper dan de bodem van de wereld.
        int bottom = Math.max(-DEPTH, level.getMinBuildHeight() - table.getY());
        BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                probe.set(table.getX() + dx, table.getY(), table.getZ() + dz);
                if (!level.hasChunkAt(probe)) {
                    continue;
                }
                for (int dy = bottom; dy <= ABOVE; dy++) {
                    probe.setY(table.getY() + dy);
                    OreType type = OreType.of(level.getBlockState(probe));
                    if (type != null) {
                        ores.add(new Ore(dx, dy, dz, type));
                    }
                }
            }
        }
        return new Snapshot(ores, bottom);
    }
}
