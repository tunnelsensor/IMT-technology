package com.senne.imttech.registry;

import com.senne.imttech.ImtTech;
import com.senne.imttech.hydra.BibieBunkerPiece;
import com.senne.imttech.hydra.BibieBunkerStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Structuren met een eigen stukje Java.
 *
 * De Bibie's Industries Tower en het satellietwrak hebben dat niet nodig: die staan gewoon op de grond, en
 * dat regelen de JSON-bestanden in data/imttech/worldgen. De Bibie's Industries-bunker moet IN een berg,
 * met de ingang in een rotswand; zo'n plek zoeken kan alleen met code (BibieBunkerStructure).
 */
public final class ModStructures {
    private ModStructures() {}

    public static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, ImtTech.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, ImtTech.MODID);

    /** Het soort structuur: "imttech:bibie_bunker" in worldgen/structure/bibie_bunker.json. */
    public static final DeferredHolder<StructureType<?>, StructureType<BibieBunkerStructure>> BIBIE_BUNKER =
            TYPES.register("bibie_bunker", () -> () -> BibieBunkerStructure.CODEC);

    /** Het ene stuk waaruit de bunker bestaat (het .nbt-bestand), zodat de wereld het kan opslaan. */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> BIBIE_BUNKER_PIECE =
            PIECES.register("bibie_bunker", () -> (StructurePieceType.StructureTemplateType) BibieBunkerPiece::new);
}
