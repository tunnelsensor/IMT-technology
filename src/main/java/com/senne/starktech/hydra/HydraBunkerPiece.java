package com.senne.starktech.hydra;

import com.senne.starktech.StarkTech;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * De bunker zelf: het bestand hydra_bunker.nbt (gemaakt door tools/generate_bunker.py),
 * gedraaid zodat de ingang de berg uit kijkt.
 *
 * In het bestand staan de soldaten als "data-markeringen": een structuurblok met de tekst
 * hydra_soldier. Als Minecraft zo'n blok neerzet, roept het handleDataMarker aan; daar halen
 * we het blok weg en zetten we er een Hydra-soldaat neer, met geweer, die nooit verdwijnt.
 */
public class HydraBunkerPiece extends TemplateStructurePiece {
    public static final ResourceLocation TEMPLATE = ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, "hydra_bunker");
    /** De tekst in de data-markeringen voor een soldaat (zie generate_bunker.py). */
    public static final String SOLDIER = "hydra_soldier";

    public HydraBunkerPiece(StructureTemplateManager manager, BlockPos origin, Rotation rotation) {
        super(ModStructures.HYDRA_BUNKER_PIECE.get(), 0, manager, TEMPLATE, TEMPLATE.toString(), settings(rotation), origin);
    }

    /** Terug inladen uit een opgeslagen wereld. */
    public HydraBunkerPiece(StructureTemplateManager manager, CompoundTag tag) {
        super(ModStructures.HYDRA_BUNKER_PIECE.get(), tag, manager,
                id -> settings(Rotation.valueOf(tag.getString("Rot"))));
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
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random,
                                    BoundingBox box) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (SOLDIER.equals(name)) {
            spawnSoldier(level, pos, random);
        }
    }

    /** Een soldaat die hier op wacht staat: met geweer, en hij verdwijnt niet als je weggaat. */
    public static void spawnSoldier(ServerLevelAccessor level, BlockPos pos, RandomSource random) {
        ServerLevel serverLevel = level.getLevel();
        HydraSoldierEntity soldier = ModEntities.HYDRA_SOLDIER.get().create(serverLevel);
        if (soldier == null) {
            return;
        }
        soldier.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, random.nextFloat() * 360, 0);
        soldier.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.STRUCTURE, null);
        soldier.setPersistenceRequired();
        level.addFreshEntityWithPassengers(soldier);
    }
}
