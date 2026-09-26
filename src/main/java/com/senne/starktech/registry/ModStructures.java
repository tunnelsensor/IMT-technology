package com.senne.starktech.registry;

import com.senne.starktech.StarkTech;
import com.senne.starktech.hydra.HydraBunkerPiece;
import com.senne.starktech.hydra.HydraBunkerStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Structuren met een eigen stukje Java.
 *
 * De Stark Tower en het satellietwrak hebben dat niet nodig: die staan gewoon op de grond, en
 * dat regelen de JSON-bestanden in data/starktech/worldgen. De Hydra-bunker moet IN een berg,
 * met de ingang in een rotswand; zo'n plek zoeken kan alleen met code (HydraBunkerStructure).
 */
public final class ModStructures {
    private ModStructures() {}

    public static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, StarkTech.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, StarkTech.MODID);

    /** Het soort structuur: "starktech:hydra_bunker" in worldgen/structure/hydra_bunker.json. */
    public static final DeferredHolder<StructureType<?>, StructureType<HydraBunkerStructure>> HYDRA_BUNKER =
            TYPES.register("hydra_bunker", () -> () -> HydraBunkerStructure.CODEC);

    /** Het ene stuk waaruit de bunker bestaat (het .nbt-bestand), zodat de wereld het kan opslaan. */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> HYDRA_BUNKER_PIECE =
            PIECES.register("hydra_bunker", () -> (StructurePieceType.StructureTemplateType) HydraBunkerPiece::new);
}
