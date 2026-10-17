package com.senne.imttech.registry;

import com.senne.imttech.ImtTech;
import com.senne.imttech.raven.BiebiaBunkerPiece;
import com.senne.imttech.raven.BiebiaBunkerStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Structuren met een eigen stukje Java.
 *
 * De Biebia's Industries Tower en het satellietwrak hebben dat niet nodig: die staan gewoon op de grond, en
 * dat regelen de JSON-bestanden in data/imttech/worldgen. De Biebia's Industries-bunker moet IN een berg,
 * met de ingang in een rotswand; zo'n plek zoeken kan alleen met code (BiebiaBunkerStructure).
 */
public final class ModStructures {
    private ModStructures() {}

    public static final DeferredRegister<StructureType<?>> TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, ImtTech.MODID);
    public static final DeferredRegister<StructurePieceType> PIECES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, ImtTech.MODID);

    /** Het soort structuur: "imttech:biebia_bunker" in worldgen/structure/biebia_bunker.json. */
    public static final DeferredHolder<StructureType<?>, StructureType<BiebiaBunkerStructure>> BIEBIA_BUNKER =
            TYPES.register("biebia_bunker", () -> () -> BiebiaBunkerStructure.CODEC);

    /** Het ene stuk waaruit de bunker bestaat (het .nbt-bestand), zodat de wereld het kan opslaan. */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> BIEBIA_BUNKER_PIECE =
            PIECES.register("biebia_bunker", () -> (StructurePieceType.StructureTemplateType) BiebiaBunkerPiece::new);
}
