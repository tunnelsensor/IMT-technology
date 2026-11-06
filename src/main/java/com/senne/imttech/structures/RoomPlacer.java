package com.senne.imttech.structures;

import com.senne.imttech.raven.BiebiaBunkerPiece;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Een toren of een bunker uit kamers neerzetten in een wereld die er al is (hoofdstuk 51): voor de showcase, de
 * screenshots en de tests. Bij het bouwen van de wereld gaat het via de structuren zelf (BiebiaTowerStructure en
 * BiebiaBunkerStructure); dit doet hetzelfde stapelen, maar dan meteen.
 */
public final class RoomPlacer {
    private RoomPlacer() {}

    /** Zet een willekeurige toren neer met zijn plein op {@code foot}; geeft de indeling terug (onder naar boven). */
    public static List<String> placeTower(ServerLevel level, BlockPos foot, Rotation rotation, Random random) {
        List<String> layout = TowerLayout.random(random);
        for (int i = 0; i < layout.size(); i++) {
            place(level, TowerPiece.template(layout.get(i)), foot.above(TowerLayout.baseOf(i)), rotation);
        }
        return layout;
    }

    /** Zet een willekeurige bunker neer met zijn ingang op {@code entrance}, de berg in naar {@code rotation}. */
    public static List<String> placeBunker(ServerLevel level, BlockPos entrance, Rotation rotation, Random random) {
        List<String> layout = BunkerLayout.random(random);
        BlockPos origin = com.senne.imttech.raven.BiebiaBunkerStructure.originFor(entrance, rotation);
        for (int i = 0; i < layout.size(); i++) {
            BlockPos shift = StructureTemplate.transform(new BlockPos(0, 0, BunkerLayout.offsetOf(layout, i)), Mirror.NONE, rotation,
                    BlockPos.ZERO);
            place(level, BiebiaBunkerPiece.template(layout.get(i)), origin.offset(shift), rotation);
        }
        return layout;
    }

    /** Eén kamer van de toren neerzetten (voor de screenshots van hoofdstuk 54: de generatorkamer alleen). */
    public static void placeRoom(ServerLevel level, String room, BlockPos at, Rotation rotation) {
        place(level, TowerPiece.template(room), at, rotation);
    }

    /** Eén stuk neerzetten, en de soldaten uit zijn markeringen erbij (zoals BiebiaBunkerPiece dat bij het bouwen doet). */
    private static void place(ServerLevel level, ResourceLocation template, BlockPos at, Rotation rotation) {
        StructureTemplate structure = level.getStructureManager().getOrCreate(template);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setMirror(Mirror.NONE);
        structure.placeInWorld(level, at, at, settings, level.random, Block.UPDATE_CLIENTS);
        for (StructureTemplate.StructureBlockInfo info : structure.filterBlocks(at, settings, Blocks.STRUCTURE_BLOCK, true)) {
            CompoundTag nbt = info.nbt();
            if (nbt == null) {
                continue;
            }
            if (BiebiaBunkerPiece.SOLDIER.equals(nbt.getString("metadata"))) {
                level.setBlock(info.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                BiebiaBunkerPiece.spawnSoldier(level, info.pos(), level.random);
            } else if (TowerGuards.GUARD.equals(nbt.getString("metadata"))) {
                level.setBlock(info.pos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                TowerGuards.spawnGuard(level, info.pos(), level.random);
            }
        }
    }
}
