package com.senne.starktech.client;

import com.senne.starktech.config.StarkConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.Util;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Zoekt ertsen rond de speler. Dit gebeurt in jouw eigen spel, pas nadat de server
 * de scan heeft goedgekeurd en de energie heeft afgetrokken.
 */
public final class OreScanner {
    private OreScanner() {}

    /** Zoveel ertsen tonen we maximaal (de dichtstbijzijnde). */
    private static final int MAX_MARKERS = 300;

    /** Geeft het aantal gevonden ertsen terug. */
    public static int scan(int radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        Level level = mc.level;
        BlockPos center = mc.player.blockPosition();
        List<EdithClientState.OreMarker> found = new ArrayList<>();

        // Loop door elk blok in een kubus rond de speler.
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (pos.distSqr(center) > (double) radius * radius) {
                continue; // buiten de bol
            }
            BlockState state = level.getBlockState(pos);
            OreType type = OreType.of(state);
            if (type != null) {
                // betweenClosed hergebruikt hetzelfde BlockPos-object, dus we maken een kopie.
                found.add(new EdithClientState.OreMarker(pos.immutable(), type));
            }
        }

        // Dichtstbijzijnde eerst, en niet meer dan MAX_MARKERS.
        found.sort(Comparator.comparingDouble(marker -> marker.pos().distSqr(center)));
        if (found.size() > MAX_MARKERS) {
            found = new ArrayList<>(found.subList(0, MAX_MARKERS));
        }

        long now = Util.getMillis();
        EdithClientState.oreMarkers = found;
        EdithClientState.oreScanStartMillis = now;
        EdithClientState.oreScanEndMillis = now + StarkConfig.Scanner.ORE_SCAN_DURATION * 50L;
        return found.size();
    }
}
