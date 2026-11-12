package com.senne.imttech.energy;

import com.senne.imttech.network.ZoneSyncPayload;
import com.senne.imttech.security.zone.ZoneBox;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

/**
 * Welke kabels je met de bril door een muur heen ziet (hoofdstuk 92, jouw wens van 12 november: "zorgen dat je enkel je
 * eigen dingen kunt x-rayen zoals kabels en signalen, omdat je anders makkelijk kunt x-rayen naar andere mensen hun
 * base"):
 * <ul>
 *   <li>de kabels die <b>jij</b> legde, waar ze ook liggen;</li>
 *   <li>alle kabels in een <b>zone waar jij mag bouwen</b>: je eigen zones en die van wie jou vertrouwt of in je team zit
 *       (dezelfde zones waarvan je de rand ziet). Daar zie je ook de kabels van je vrienden, oude kabels van niemand, en
 *       een kabel die een vreemde in je basis legde.</li>
 * </ul>
 * De rest (de kabels van een ander buiten jouw zones, en die in de gebouwen van deze mod) zie je alleen als je hem gewoon
 * ziet liggen, niet door een muur heen. Hetzelfde geldt voor de signalen en de stroom die erover lopen.
 *
 * Dit beslist je eigen spel (CableRenderer, CableTraffic): het kent de eigenaar van elke kabel (CableCells) en de zones
 * waar je mag bouwen (die stuurt de server al voor de rand, ZoneSync).
 */
public final class CableSight {
    private CableSight() {}

    /**
     * Mag {@code viewer} het stuk kabel op {@code cell} (gelegd door {@code owner}, null = van niemand) door een muur heen
     * zien? {@code buildZones}: de zones waar {@code viewer} mag bouwen.
     */
    public static boolean mayXray(@Nullable UUID owner, UUID viewer, List<ZoneSyncPayload.View> buildZones, BlockPos cell) {
        return viewer.equals(owner) || inZones(buildZones, cell);
    }

    /** Ligt deze plek in een van deze zones? */
    public static boolean inZones(List<ZoneSyncPayload.View> zones, BlockPos cell) {
        for (ZoneSyncPayload.View zone : zones) {
            for (ZoneBox box : zone.boxes()) {
                if (box.contains(cell)) {
                    return true;
                }
            }
        }
        return false;
    }
}
