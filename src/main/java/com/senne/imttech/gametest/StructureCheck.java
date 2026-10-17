package com.senne.imttech.gametest;

import com.mojang.datafixers.util.Pair;
import com.senne.imttech.ImtTech;
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
 * 1. zoeken we het dichtstbijzijnde satellietwrak en de dichtstbijzijnde Bibie's Industries Tower;
 * 2. laten we die stukken wereld helemaal aanmaken (dan wordt de structuur neergezet);
 * 3. schrijven we in het log waar de kisten liggen, en hoe hoog dat is ten opzichte van de grond
 *    in het midden ("[structures]"-regels);
 * 4. en stopt de server weer.
 *
 * In een gewoon spel doet deze klasse niets (alleen met -Dimttech.structurecheck=true).
 */
public final class StructureCheck {
    private StructureCheck() {}

    private static final boolean ENABLED = Boolean.getBoolean("imttech.structurecheck");

    public static void registerIfEnabled() {
        if (ENABLED) {
            NeoForge.EVENT_BUS.addListener(StructureCheck::onServerStarted);
        }
    }

    private static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        for (String name : List.of("crashed_satellite", "bibie_tower", "bibie_bunker")) {
            check(level, name);
        }
        ImtTech.LOGGER.info("[structures] klaar, server stopt");
        event.getServer().halt(false);
    }

    private static void check(ServerLevel level, String name) {
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE,
                ResourceLocation.fromNamespaceAndPath(ImtTech.MODID, name));
        Holder<Structure> structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getHolderOrThrow(key);
        Pair<BlockPos, Holder<Structure>> found = level.getChunkSource().getGenerator()
                .findNearestMapStructure(level, HolderSet.direct(structure), BlockPos.ZERO, 100, false);
        if (found == null) {
            ImtTech.LOGGER.warn("[structures] {}: niet gevonden binnen 1600 blokken", name);
            return;
        }
        BlockPos near = found.getFirst();
        // Het stuk wereld aanmaken waar hij begint: daar staat het "begin" van de structuur in,
        // en daarmee weten we hoe groot hij is.
        StructureStart start = level.getChunk(near).getStartForStructure(structure.value());
        if (start == null || !start.isValid()) {
            ImtTech.LOGGER.warn("[structures] {}: gevonden bij {}, maar geen geldig begin", name, near);
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
        ImtTech.LOGGER.info("[structures] {}: bij {} (vak {} tot {}), grond in het midden op y {}, biome {}",
                name, near, box.minY(), box.maxY(), ground,
                level.getBiome(centre).unwrapKey().map(k -> k.location().toString()).orElse("?"));
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                // Per plek opvragen: van een net gebouwde structuur maakt Minecraft de kisten pas
                // echt aan als iemand erom vraagt (daarvoor staan ze nog "in de wacht").
                for (BlockPos pos : level.getChunk(cx, cz).getBlockEntitiesPos()) {
                    if (box.isInside(pos) && level.getBlockEntity(pos) instanceof RandomizableContainerBlockEntity container
                            && container.getLootTable() != null) {
                        ImtTech.LOGGER.info("[structures] {}:   kist {} op {} ({} t.o.v. de grond)", name,
                                container.getLootTable().location(), pos, pos.getY() - ground);
                    }
                }
            }
        }
        if (name.equals("bibie_bunker")) {
            bunkerDetails(level, box);
        }
        ImtTech.LOGGER.info("[structures] {}: teleporteer ernaartoe met /tp {} {} {}", name,
                centre.getX(), ground + 2, centre.getZ());
    }

    /** Van de bunker ook: hoeveel soldaten, torentjes en laserzenders er echt staan, en waar de ingang is. */
    private static void bunkerDetails(ServerLevel level, BoundingBox box) {
        net.minecraft.world.phys.AABB area = net.minecraft.world.phys.AABB.of(box).inflate(2);
        int soldiers = level.getEntitiesOfClass(com.senne.imttech.hydra.HydraSoldierEntity.class, area).size();
        int turrets = 0;
        int emitters = 0;
        int markers = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ())) {
            var state = level.getBlockState(pos);
            if (state.is(com.senne.imttech.registry.ModBlocks.HYDRA_TURRET.get())) {
                turrets++;
            } else if (state.is(com.senne.imttech.registry.ModBlocks.LASER_EMITTER.get())) {
                emitters++;
            } else if (state.is(net.minecraft.world.level.block.Blocks.STRUCTURE_BLOCK)) {
                markers++;
            }
        }
        ImtTech.LOGGER.info("[structures] bibie_bunker:   {} soldaten, {} Hydra-torentjes, {} laserzenders, "
                + "{} overgebleven structuurblokken", soldiers, turrets, emitters, markers);
    }
}
