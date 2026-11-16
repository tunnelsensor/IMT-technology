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

    /** De weg van een apparaat naar zijn kern: bij welke stand van het netwerk, en wanneer, we hem zochten (zie route). */
    private record Route(int version, long gameTime, List<BlockPos> path) {}

    /** Per wereld en apparaat: de laatste weg naar zijn kern (hoofdstuk 124). */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Route>> ROUTES = new HashMap<>();
    /** Zo lang (ticks) gebruiken we een gevonden weg opnieuw, zolang er aan het netwerk niets veranderde. */
    private static final int ROUTE_TICKS = 40;
    /** Hooguit zo veel plekken krijgt een speler van een lange weg (zie part). */
    private static final int PART_MAX = 100;
    /** Je spel tekent een weg alleen als zijn midden zo dichtbij is (blokken; zie RANGE in client/CableTraffic). */
    private static final double CLIENT_RANGE = 64;

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
        List<BlockPos> path = route(level, device);
        if (path.size() < 2) {
            return;
        }
        if (!toCore) {
            path = new ArrayList<>(path);
            java.util.Collections.reverse(path);
        }
        send(level, path, false, 0, range);
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
        send(level, path, true, interval + 10, range);
    }

    /**
     * De weg van dit apparaat naar zijn kern (zie LanGrid.path), even onthouden (hoofdstuk 124): op een lange kabel kost dat
     * zoeken veel meer dan vroeger (tot 8192 stukjes in plaats van 512), en een diagram kan in één tick tientallen keren
     * accept() doen. Zolang er aan het netwerk niets verandert, gebruiken we de weg dus {@link #ROUTE_TICKS} ticks opnieuw.
     */
    private static List<BlockPos> route(ServerLevel level, BlockPos device) {
        Map<BlockPos, Route> perLevel = ROUTES.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        long now = level.getGameTime();
        Route route = perLevel.get(device);
        if (route == null || route.version() != LanGrid.version() || now - route.gameTime() > ROUTE_TICKS
                || now < route.gameTime()) {
            if (perLevel.size() > 4096) {
                perLevel.clear(); // apparaten waar al lang geen signaal meer van kwam
            }
            route = new Route(LanGrid.version(), now, List.copyOf(LanGrid.path(level, device)));
            perLevel.put(device.immutable(), route);
        }
        return route.path();
    }

    /**
     * Naar elke speler in deze wereld die dichtbij een van de twee einden van de weg is.
     *
     * Hoofdstuk 124: je spel tekent een weg alleen als zijn midden dichtbij is (64 blokken, zie client/CableTraffic). Dan
     * krijgt een speler de hele weg, zoals vroeger; anders het stuk bij het einde waar hij staat (zie {@link #part}): zo zie
     * je ook op een lange kabel het vakje vertrekken of aankomen, en de streepjes bij jouw apparaat. Het bericht zelf bleef
     * hetzelfde, dus een speler met een oudere versie van de mod kan er gewoon tegen.
     */
    private static void send(ServerLevel level, List<BlockPos> path, boolean power, int ticks, double range) {
        Vec3 start = Vec3.atCenterOf(path.getFirst());
        Vec3 end = Vec3.atCenterOf(path.getLast());
        Vec3 middle = Vec3.atCenterOf(path.get(path.size() / 2));
        for (ServerPlayer player : level.players()) {
            boolean nearStart = player.distanceToSqr(start) <= range * range;
            if (!nearStart && player.distanceToSqr(end) > range * range) {
                continue;
            }
            boolean whole = path.size() <= CableTrafficPayload.MAX_PATH
                    && player.distanceToSqr(middle) < CLIENT_RANGE * CLIENT_RANGE;
            ModNetwork.sendTo(player, new CableTrafficPayload(power, whole ? List.copyOf(path) : part(path, nearStart, range), ticks));
        }
    }

    /**
     * Het stuk van een weg bij één einde (hoofdstuk 124): de eerste plekken ({@code nearStart}) of de laatste, net iets meer
     * dan hoe ver een speler signalen ziet (hooguit {@link #PART_MAX}). Bij het begin vertrekt het vakje dan bij jou, bij het
     * eind komt het binnen een paar seconden aan (het loopt 10 blokken per seconde). Een weg die korter is, helemaal.
     * Public voor de tests.
     */
    public static List<BlockPos> part(List<BlockPos> path, boolean nearStart, double range) {
        int size = Math.min(PART_MAX, (int) Math.ceil(range) + 16);
        if (path.size() <= size) {
            return List.copyOf(path);
        }
        return List.copyOf(nearStart ? path.subList(0, size) : path.subList(path.size() - size, path.size()));
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

    /** Wanneer liet dit apparaat zijn stroom voor het laatst zien (speltijd), of -1. Voor de tests. */
    public static long lastFlow(Level level, BlockPos device) {
        Long time = LAST_FLOW.getOrDefault(level.dimension(), Map.of()).get(device);
        return time == null ? -1 : time;
    }

    /** Bij het afsluiten van de wereld: alles vergeten. */
    public static void clear() {
        LAST_FLOW.clear();
        ROUTES.clear();
    }
}
