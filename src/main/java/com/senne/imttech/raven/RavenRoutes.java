package com.senne.imttech.raven;

import com.senne.imttech.structures.TowerGuards;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * De routes van de bewakers (hoofdstuk 60, jouw antwoord 1: "per kamer getekend in de kamerbestanden").
 *
 * Een soldaat staat in een kamerbestand als een markering (een structure block in de stand DATA) met de tekst
 * {@code raven_soldier}. Sinds hoofdstuk 60 mag daar zijn route achter staan: verschuivingen vanaf zijn eigen plek,
 * gescheiden door puntkomma's, zoals {@code raven_soldier;2,0,0;2,0,5;-3,0,5}. De generatoren schrijven die
 * ({@code soldier(x, y, z, route=[(x, z), ...])} in tools/generate_bunker.py en generate_bunker_rooms.py). Omdat het
 * verschuivingen zijn, schuiven ze mee als een zaal opgeschoven wordt, en hier draaien we ze mee met het stuk (een
 * bunker ligt in elke richting de berg in). Een bewaker van de toren ({@code raven_guard}, zie TowerGuards) kan
 * dezelfde route-tekst krijgen.
 */
public final class RavenRoutes {
    private RavenRoutes() {}

    /** Het woord voor de eerste puntkomma: "raven_soldier" of "raven_guard". */
    public static String kindOf(String metadata) {
        int cut = metadata.indexOf(';');
        return (cut < 0 ? metadata : metadata.substring(0, cut)).trim();
    }

    /** Een gewone soldaat (met of zonder route)? */
    public static boolean isSoldier(String metadata) {
        return BiebiaBunkerPiece.SOLDIER.equals(kindOf(metadata));
    }

    /** Een bewaker van de toren met een staff-keycard (hoofdstuk 54), met of zonder route? */
    public static boolean isGuard(String metadata) {
        return TowerGuards.GUARD.equals(kindOf(metadata));
    }

    /**
     * De route uit de markering: elke verschuiving gedraaid zoals het stuk, opgeteld bij de plek van de markering (die is
     * al gedraaid). Leeg als er geen route achter staat; een verschuiving die geen drie getallen is, slaan we over.
     */
    public static List<BlockPos> route(String metadata, BlockPos at, Rotation rotation) {
        List<BlockPos> route = new ArrayList<>();
        String[] parts = metadata.split(";");
        for (int i = 1; i < parts.length; i++) {
            String[] numbers = parts[i].trim().split(",");
            if (numbers.length != 3) {
                continue;
            }
            try {
                BlockPos offset = new BlockPos(Integer.parseInt(numbers[0].trim()), Integer.parseInt(numbers[1].trim()),
                        Integer.parseInt(numbers[2].trim()));
                route.add(at.offset(StructureTemplate.transform(offset, Mirror.NONE, rotation, BlockPos.ZERO)));
            } catch (NumberFormatException ignored) {
                // Geen getal: die plek slaan we over.
            }
        }
        return route;
    }
}
