package com.senne.starktech.gametest;

import com.mojang.datafixers.util.Pair;
import com.senne.starktech.StarkTech;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Controle van de wereldgeneratie: komen onze structuren echt in een gewone wereld, en op de
 * goede hoogte? De automatische tests kunnen dat niet: die zetten een structuur zelf neer.
 *
 * Starten met {@code gradlew runStructureCheck}. Dat start een server met een gewone wereld (in
 * de map run-structures), en dan:
 * 1. zoeken we het dichtstbijzijnde satellietwrak en de dichtstbijzijnde Stark Tower;
 * 2. laten we die stukken wereld helemaal aanmaken (dan wordt de structuur neergezet);
 * 3. schrijven we in het log waar de kisten liggen, en hoe hoog dat is ten opzichte van de grond
 *    in het midden ("[structures]"-regels);
 * 4. en stopt de server weer.
 *
 * In een gewoon spel doet deze klasse niets (alleen met -Dstarktech.structurecheck=true).
 */
public final class StructureCheck {
    private StructureCheck() {}

    private static final boolean ENABLED = Boolean.getBoolean("starktech.structurecheck");

    public static void registerIfEnabled() {
        if (ENABLED) {
            NeoForge.EVENT_BUS.addListener(StructureCheck::onServerStarted);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        for (String name : List.of("crashed_satellite", "stark_tower")) {
            check(level, name);
        }
        StarkTech.LOGGER.info("[structures] klaar, server stopt");
        event.getServer().halt(false);
    }

    private static void check(ServerLevel level, String name) {
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE,
                ResourceLocation.fromNamespaceAndPath(StarkTech.MODID, name));
        Holder<Structure> structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(key);
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(structure), BlockPos.ZERO, 100, false);
        if (found == null) {
            StarkTech.LOGGER.warn("[structures] {}: niet gevonden binnen 1600 blokken", name);
            return;
        }
        BlockPos near = found.getFirst();
        // Het stuk wereld aanmaken waar hij begint: daar staat het "begin" van de structuur in,
        // en daarmee weten we hoe groot hij is.
        StructureStart start = level.getChunk(near).getStartForStructure(structure.value());
        if (start == null || !start.isValid()) {
            StarkTech.LOGGER.warn("[structures] {}: gevonden bij {}, maar geen geldig begin", name, near);
            return;
        }
        BoundingBox box = start.getBoundingBox();
        // Alle stukken wereld eronder helemaal aanmaken: dan staat de structuur er echt.
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        BlockPos centre = box.getCenter();
        int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, centre.getX(), centre.getZ());
        StarkTech.LOGGER.info("[structures] {}: bij {} (vak {} tot {}), grond in het midden op y {}, biome {}",
                name, near, box.minY(), box.maxY(), ground,
                level.getBiome(centre).unwrapKey().map(k -> k.location().toString()).orElse("?"));
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                // Per plek opvragen: van een net gebouwde structuur maakt Minecraft de kisten pas
                // echt aan als iemand erom vraagt (daarvoor staan ze nog "in de wacht").
                for (BlockPos pos : level.getChunk(cx, cz).getBlockEntitiesPos()) {
                    if (box.isInside(pos) && level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container
                            && container.getLootTable() != null) {
                        StarkTech.LOGGER.info("[structures] {}:   kist {} op {} ({} t.o.v. de grond)", name,
                                container.getLootTable().location(), pos, pos.getY() - ground);
                    }
                }
            }
        }
        StarkTech.LOGGER.info("[structures] {}: teleporteer ernaartoe met /tp {} {} {}", name,
                centre.getX(), ground + 2, centre.getZ());
    }
}
