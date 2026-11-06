package com.senne.imttech.structures;

import com.senne.imttech.ImtTech;
import com.senne.imttech.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Eén stuk van een toren uit kamers (hoofdstuk 51): een bestand uit data/imttech/structure/tower/ (de lobby, een
 * tussenverdieping of de top), gedraaid zoals de hele toren. Alle stukken van één toren delen dezelfde hoek en dezelfde
 * x en z; alleen de hoogte verschilt (zie TowerLayout.baseOf).
 */
public class TowerPiece extends TemplateStructurePiece {
    public TowerPiece(StructureTemplateManager manager, String room, BlockPos origin, Rotation rotation) {
        super(ModStructures.TOWER_PIECE.get(), 0, manager, template(room), template(room).toString(), settings(rotation), origin);
    }

    /** Terug inladen uit een opgeslagen wereld. */
    public TowerPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(ModStructures.TOWER_PIECE.get(), tag, manager, id -> settings(Rotation.valueOf(tag.getString("Rot"))));
    }

    public static ResourceLocation template(String room) {
        return ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, "tower/" + room);
    }

    private static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", placeSettings.getRotation().name());
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box) {
        // Biebia's drones komen via de docks in de hangar (zie TowerDrones); sinds hoofdstuk 54 staan er wel soldaten:
        // gewone (zoals in de bunker) en bewakers met een keycard van de toren op zak (zie TowerGuards).
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        if (com.senne.imttech.raven.BiebiaBunkerPiece.SOLDIER.equals(name)) {
            com.senne.imttech.raven.BiebiaBunkerPiece.spawnSoldier(level, pos, random);
        } else if (TowerGuards.GUARD.equals(name)) {
            TowerGuards.spawnGuard(level, pos, random);
        }
    }
}
