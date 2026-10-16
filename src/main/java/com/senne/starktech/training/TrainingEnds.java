package com.senne.starktech.training;

import com.senne.starktech.security.SecurityCoreBlockEntity;
import com.senne.starktech.security.SecurityCores;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Waar de Training End-blokken staan die nu geladen zijn (hoofdstuk 26), zodat een treffer ze vindt: elk End-blok in
 * het gebied van de kern van de training geeft een puls en draait zijn programma.
 */
public final class TrainingEnds {
    private TrainingEnds() {}

    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED = new HashMap<>();

    public static void add(Level level, BlockPos pos) {
        LOADED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.immutable());
    }

    public static void remove(Level level, BlockPos pos) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set != null) {
            set.remove(pos);
        }
    }

    /** De trainee is geraakt: elk End-blok van deze basis geeft een puls en draait zijn programma ({@code when trainee_hit:}). */
    static void hit(ServerLevel level, SecurityCoreBlockEntity core, Training.Info info, UUID trainee) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set == null) {
            return;
        }
        for (BlockPos pos : List.copyOf(set)) {
            if (SecurityCores.coreAt(level, pos) != core || !(level.getBlockEntity(pos) instanceof TrainingEndBlockEntity end)) {
                continue;
            }
            TrainingEndBlock.pulse(level, pos);
            if (end.program() != null) {
                com.senne.starktech.logic.LogicRunner.trainingEnd(level, core, end.program(), info, trainee);
            }
        }
    }

    public static void clear() {
        LOADED.clear();
    }
}
