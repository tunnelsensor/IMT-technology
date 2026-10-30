package com.senne.imttech.lan;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.energy.Cables;
import com.senne.imttech.energy.PowerGrid;
import com.senne.imttech.network.CableTrafficPayload;
import com.senne.imttech.network.ModNetwork;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Wat er over de kabels loopt, om naar te kijken (hoofdstuk 38, jouw tabel van 29 oktober):
 * <ul>
 *   <li>"een signaal wordt gestuurd = helder wit vakje dat de kabel volgt tot aan destination" (groen over een PoE-kabel):
 *       bij een kaart, een code, een knop of hendel (van het apparaat naar de kern), en bij accept() en deny() en een
 *       schakelaar op de desktop (van de kern naar het apparaat);</li>
 *   <li>"als power erdoor stroomt = geel met animatie": een apparaat dat stroom haalt, laat (hooguit om de
 *       {@code flowSeconds}) gele streepjes lopen van zijn leverancier naar zich toe.</li>
 * </ul>
 * Het is alleen om naar te kijken: de logica wacht er niet op. De server zoekt de weg (LanGrid.path, PowerGrid.path) en
 * stuurt hem naar de spelers die dichtbij genoeg zijn ({@code signalRange}); hun spel tekent hem (client/CableTraffic).
 */
public final class CableTraffic {
    private CableTraffic() {}

    /** Per wereld en apparaat: wanneer (speltijd) het voor het laatst zijn stroom liet lopen. */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Long>> LAST_FLOW = new HashMap<>();
    /** Na zo veel ticks vergeten we wanneer een apparaat zijn stroom liet zien (tegen een steeds langere lijst). */
    private static final int FORGET_TICKS = 1200;

    /**
     * Een signaal tussen dit apparaat en zijn kern, over het netwerk: {@code toCore} = van het apparaat naar de kern (een
     * kaart gelezen), anders van de kern naar het apparaat (accept(), een schakelaar op de desktop). Niets als het
     * apparaat niet aan een netwerk hangt, of met het oude gedrag zonder netwerkkabels.
     */
    public static void signal(ServerLevel level, BlockPos device, boolean toCore) {
        double range = ImtConfig.Network.SIGNAL_RANGE;
        if (range <= 0 || !LanGrid.required() || level.players().isEmpty()) {
            return;
        }
        List<BlockPos> path = LanGrid.path(level, device);
        if (path.size() < 2) {
            return;
        }
        if (!toCore) {
            path = new ArrayList<>(path);
            java.util.Collections.reverse(path);
        }
        send(level, path, new CableTrafficPayload(false, trim(path), 0), range);
    }

    /**
     * Dit apparaat haalde net stroom (zie PowerGrid.draw): laat de stroom van zijn leverancier naar hem toe lopen, hooguit
     * om de {@code flowSeconds}, en alleen als er een speler in de buurt is en er een stroom- of PoE-kabel tussen ligt
     * (een generator er direct tegenaan heeft niets om op te tekenen).
     */
    public static void power(ServerLevel level, BlockPos device) {
        double range = ImtConfig.Network.SIGNAL_RANGE;
        int interval = Math.round(ImtConfig.Network.FLOW_SECONDS * 20);
        if (range <= 0 || interval <= 0) {
            return;
        }
        long now = level.getGameTime();
        Map<BlockPos, Long> last = LAST_FLOW.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        Long before = last.get(device);
        if (before != null && now - before < interval && now >= before) {
            return;
        }
        if (last.size() > 4096) {
            last.values().removeIf(time -> now - time > FORGET_TICKS || time > now);
        }
        last.put(device.immutable(), now);
        if (!anyoneNear(level, device, range)) {
            return;
        }
        List<BlockPos> path = PowerGrid.path(level, device);
        if (path.size() < 2 || path.stream().noneMatch(pos -> Cables.carriesPower(level, pos))) {
            return;
        }
        // Wat langer dan de tijd tot het volgende pakketje: zo lopen de streepjes zonder haperen door.
        send(level, path, new CableTrafficPayload(true, trim(path), interval + 10), range);
    }

    /** Naar elke speler in deze wereld die dichtbij een van de twee einden van de weg is. */
    private static void send(ServerLevel level, List<BlockPos> path, CableTrafficPayload payload, double range) {
        Vec3 start = Vec3.atCenterOf(path.getFirst());
        Vec3 end = Vec3.atCenterOf(path.getLast());
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(start) <= range * range || player.distanceToSqr(end) <= range * range) {
                ModNetwork.sendTo(player, payload);
            }
        }
    }

    /** Is er een speler binnen {@code range} blokken van deze plek? */
    private static boolean anyoneNear(ServerLevel level, BlockPos pos, double range) {
        Vec3 centre = Vec3.atCenterOf(pos);
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(centre) <= range * range) {
                return true;
            }
        }
        return false;
    }

    /** Hooguit MAX_PATH plekken (de eerste). */
    private static List<BlockPos> trim(List<BlockPos> path) {
        return path.size() <= CableTrafficPayload.MAX_PATH ? List.copyOf(path) : List.copyOf(path.subList(0, CableTrafficPayload.MAX_PATH));
    }

    /** Wanneer liet dit apparaat zijn stroom voor het laatst zien (speltijd), of -1. Voor de tests. */
    public static long lastFlow(Level level, BlockPos device) {
        Long time = LAST_FLOW.getOrDefault(level.dimension(), Map.of()).get(device);
        return time == null ? -1 : time;
    }

    /** Bij het afsluiten van de wereld: alles vergeten. */
    public static void clear() {
        LAST_FLOW.clear();
    }
}
