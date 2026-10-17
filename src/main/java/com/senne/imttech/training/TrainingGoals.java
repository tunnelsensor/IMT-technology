package com.senne.imttech.training;

import com.senne.imttech.security.SecurityCores;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Waar de Training Goal-blokken staan die nu geladen zijn (hoofdstuk 26). Een training met een tijdslimiet in een basis
 * mét een doel is mislukt als de tijd om is (je moest het doel halen); zonder doel ben je geslaagd als je het volhield.
 */
public final class TrainingGoals {
    private TrainingGoals() {}

    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED = new HashMap<>();

    /** Een Goal-blok is geladen (of neergezet). */
    public static void add(Level level, BlockPos pos) {
        LOADED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.immutable());
    }

    /** Een Goal-blok is weg (afgebroken of niet meer geladen). */
    public static void remove(Level level, BlockPos pos) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set != null) {
            set.remove(pos);
        }
    }

    /** Staat er een Goal-blok in het gebied van deze kern? */
    public static boolean anyNear(ServerLevel level, BlockPos core) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set == null) {
            return false;
        }
        for (BlockPos goal : List.copyOf(set)) {
            if (SecurityCores.inArea(core, goal)) {
                return true;
            }
        }
        return false;
    }

    public static void clear() {
        LOADED.clear();
    }
}
