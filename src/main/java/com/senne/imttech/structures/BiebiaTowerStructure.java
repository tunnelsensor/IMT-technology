package com.senne.imttech.structures;

import com.mojang.serialization.MapCodec;
import com.senne.imttech.registry.ModStructures;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * De Biebia's Industries Tower, gebouwd uit kamers (hoofdstuk 51, 4 november). Tot nu toe was de toren één bestand
 * (biebia_tower.nbt, dat blijft bestaan voor de showcase en de tests); in de wereld komt hij nu uit losse stukken die
 * op elkaar gestapeld worden in een willekeurige volgorde (zie {@link TowerLayout}): elke toren is anders.
 *
 * De toren staat op het oppervlak, zoals vroeger (de hoogtekaart in het midden van het stuk wereld); de stukken delen
 * één plek en één draaiing, en verschillen alleen in hoogte.
 */
public class BiebiaTowerStructure extends Structure {
    public static final MapCodec<BiebiaTowerStructure> CODEC = simpleCodec(BiebiaTowerStructure::new);

    public BiebiaTowerStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public StructureType<?> type() {
        return ModStructures.BIEBIA_TOWER.get();
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        Random random = new Random(context.random().nextLong());
        Rotation rotation = Rotation.getRandom(context.random());
        return onTopOfChunkCenter(context, Heightmap.Types.WORLD_SURFACE_WG,
                builder -> addPieces(builder, context.structureTemplateManager(), context.chunkPos().getMiddleBlockPosition(0), rotation, random));
    }

    /**
     * De stukken van één toren: de lobby op {@code foot} (het plein ligt op de grond), daarboven de kamers, bovenaan de
     * top. {@code foot} krijgt zijn hoogte van onTopOfChunkCenter (de y van het oppervlak).
     */
    static void addPieces(StructurePiecesBuilder builder, StructureTemplateManager manager, BlockPos foot, Rotation rotation,
                          Random random) {
        List<String> layout = TowerLayout.random(random);
        for (int i = 0; i < layout.size(); i++) {
            builder.addPiece(new TowerPiece(manager, layout.get(i), foot.above(TowerLayout.baseOf(i)), rotation));
        }
    }
}
