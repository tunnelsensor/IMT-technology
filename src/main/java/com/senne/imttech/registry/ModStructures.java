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
 * Het satellietwrak heeft dat niet nodig: dat staat gewoon op de grond, en dat regelen de JSON-bestanden in
 * data/imttech/worldgen. De Biebia's Industries-bunker moet IN een berg, met de ingang in een rotswand; zo'n plek
 * zoeken kan alleen met code (BiebiaBunkerStructure). Sinds hoofdstuk 51 (4 november) is ook de Biebia's Industries
 * Tower code (BiebiaTowerStructure): hij wordt uit kamers gestapeld, in een willekeurige volgorde.
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

    /** Het soort structuur: "imttech:biebia_tower" in worldgen/structure/biebia_tower.json (hoofdstuk 51). */
    public static final DeferredHolder<StructureType<?>, StructureType<com.senne.imttech.structures.BiebiaTowerStructure>> BIEBIA_TOWER =
            TYPES.register("biebia_tower", () -> () -> com.senne.imttech.structures.BiebiaTowerStructure.CODEC);

    /** Een stuk van de toren (een kamer, de lobby of de top), zodat de wereld het kan opslaan. */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> TOWER_PIECE =
            PIECES.register("tower_piece", () -> (StructurePieceType.StructureTemplateType) com.senne.imttech.structures.TowerPiece::new);

    /** Een stuk van de bunker (vroeger het hele .nbt-bestand, nu een zaal), zodat de wereld het kan opslaan. */
    public static final DeferredHolder<StructurePieceType, StructurePieceType> BIEBIA_BUNKER_PIECE =
            PIECES.register("biebia_bunker", () -> (StructurePieceType.StructureTemplateType) BiebiaBunkerPiece::new);
}
