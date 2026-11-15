package com.senne.imttech.security;

import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.drone.DroneEntity;
import com.senne.imttech.security.zone.ZoneBox;
import com.senne.imttech.security.zone.ZoneData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Welke beveiligingskernen er geladen zijn, en wie ze als vriend zien.
 *
 * Een kern is de baas in zijn gebied (zijn "bereik"). Hoofdstuk 117 (jouw wens van 15 november: "de protected zone bepaalt
 * dus hoe ver je range van security core is"):
 * <ul>
 *   <li>Een kern MET zone reikt precies zo ver als zijn zone (de kubussen, tot ImtConfig.Security.ZONE_RANGE blokken van
 *       de kern; de kern zelf hoort er altijd bij). Een zone is van haar kern alleen: zones liggen nooit over elkaar.</li>
 *   <li>Een kern ZONDER zone reikt ImtConfig.Security.REACH_WITHOUT_ZONE blokken ver, zoals elke kern vroeger, maar
 *       nooit in de zone van een andere kern. Reiken twee kernen zonder zone tot dezelfde plek, dan hoort die bij de
 *       dichtstbijzijnde.</li>
 *   <li>Kernen mogen vlak bij elkaar staan, maar nooit in de zone van een andere kern (ook niet als die geen stroom
 *       heeft), en niet vlak bij een Biebia's Industries Tower: zie {@link #placementProblem}.</li>
 * </ul>
 * Robots, docks, training, laserwanden en wie er vertrouwd is, vragen hier bij welke kern een plek hoort. Torentjes en
 * beveiligingsdrones vragen hier: "is dit een indringer?"
 *
 * - Heeft de kern nog GEEN eigenaar (niemand heeft hem gehackt), dan is elke speler een
 *   indringer (behalve in creative), net als monsters.
 * - Heeft hij WEL een eigenaar, dan zijn de eigenaar, zijn team en de spelers die hij vertrouwt
 *   (de Access-app op de desktop) vrienden. Andere spelers zijn alleen een indringer als de
 *   eigenaar dat aanzette (en PvP aan staat), monsters alleen als "attack mobs" aan staat.
 */
public final class SecurityCores {
    private SecurityCores() {}

    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED = new HashMap<>();

    static void loaded(Level level, BlockPos pos) {
        LOADED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.immutable());
    }

    static void unloaded(Level level, BlockPos pos) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set != null) {
            set.remove(pos);
        }
    }

    /** De plekken van alle kernen in deze wereld die nu geladen zijn (hoofdstuk 94: lan/NetworkKeeper en LanGrid). */
    public static List<BlockPos> loadedCores(Level level) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        return set == null ? List.of() : List.copyOf(set);
    }

    /**
     * De kern in wiens gebied deze plek ligt (zie bovenaan), of null. Alleen een kern die nu geladen is telt (zoals altijd).
     * Eerst een gebouw van deze mod (dat hoort altijd bij zijn eigen kern), dan een zone (die hoort bij haar eigen kern, en
     * is die niet geladen, dan is het null en niet een andere kern), en anders de dichtstbijzijnde kern zonder zone die hier
     * reikt.
     */
    public static @Nullable SecurityCoreBlockEntity coreAt(Level level, BlockPos pos) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set == null || set.isEmpty() || !(level instanceof ServerLevel server)) {
            return null;
        }
        BlockPos building = buildingCoreAt(server, pos, null);
        if (building != null) {
            return level.getBlockEntity(building) instanceof SecurityCoreBlockEntity core ? core : null;
        }
        ZoneData.Zone zone = ZoneData.get(server).at(level.dimension(), pos);
        if (zone != null) {
            BlockPos corePos = zone.core().pos();
            return set.contains(corePos) && level.getBlockEntity(corePos) instanceof SecurityCoreBlockEntity core ? core : null;
        }
        // De kernen zonder zone die hier nog reiken. Reiken er twee tot hier, dan telt de dichtstbijzijnde, en staan ze even
        // ver, dan de laagste plek (zie closer): zo is het elke keer dezelfde, en dezelfde als bij ownedBy.
        SecurityCoreBlockEntity best = null;
        BlockPos bestPos = null;
        for (BlockPos corePos : List.copyOf(set)) {
            if ((bestPos == null || closer(corePos, bestPos, pos)) && reachesWithoutZone(server, corePos, pos)
                    && level.getBlockEntity(corePos) instanceof SecurityCoreBlockEntity core) {
                best = core;
                bestPos = corePos;
            }
        }
        return best;
    }

    /**
     * Ligt deze plek in het gebied van de kern op {@code corePos}? Zijn eigen gebouw (als hij in een toren of bunker van
     * Biebia's staat) en zijn zone horen erbij; nooit het gebouw of de zone van een andere kern. Een kern zonder zone reikt
     * REACH_WITHOUT_ZONE blokken ver in elke richting (de kern van een toren of bunker dus ook rond zijn gebouw: daar vliegen
     * zijn drones). (Of een andere kern zonder zone dichterbij staat, telt hier niet mee: zie {@link #ownedBy}.) De getallen
     * staan in de config; verandert er een, dan geldt het meteen.
     */
    public static boolean inArea(ServerLevel level, BlockPos corePos, BlockPos pos) {
        if (pos.equals(corePos)) {
            return true;
        }
        List<ZoneBox> building = buildingOf(level, corePos);
        if (building != null && inAny(building, pos)) {
            return true;
        }
        if (buildingCoreAt(level, pos, corePos) != null) {
            return false; // het gebouw van een andere kern
        }
        ZoneData zones = ZoneData.get(level);
        ZoneData.Zone own = zones.zone(GlobalPos.of(level.dimension(), corePos));
        if (own != null && own.hasBoxes()) {
            return own.contains(pos);
        }
        return withinReachWithoutZone(corePos, pos) && zones.at(level.dimension(), pos) == null;
    }

    /**
     * Reikt de kern op {@code corePos} hier zonder zone? Hij heeft geen zone, de plek ligt binnen REACH_WITHOUT_ZONE, en
     * niet in een zone. (Een gebouw van een kern heeft coreAt dan al gehad.)
     */
    private static boolean reachesWithoutZone(ServerLevel level, BlockPos corePos, BlockPos pos) {
        return withinReachWithoutZone(corePos, pos) && !hasZone(level, corePos)
                && ZoneData.get(level).at(level.dimension(), pos) == null;
    }

    /** Staat {@code a} dichter bij {@code pos} dan {@code b}? Even ver: de laagste plek (als getal) wint, altijd dezelfde. */
    private static boolean closer(BlockPos a, BlockPos b, BlockPos pos) {
        double da = a.distSqr(pos);
        double db = b.distSqr(pos);
        return da < db || da == db && a.asLong() < b.asLong();
    }

    /** Heeft de kern op deze plek een zone (minstens één kubus)? Dan reikt hij precies zo ver als die zone. */
    public static boolean hasZone(ServerLevel level, BlockPos corePos) {
        ZoneData.Zone zone = ZoneData.get(level).zone(GlobalPos.of(level.dimension(), corePos));
        return zone != null && zone.hasBoxes();
    }

    /** Ligt deze plek binnen REACH_WITHOUT_ZONE blokken van de kern, in elke richting (ook omhoog en omlaag)? */
    public static boolean withinReachWithoutZone(BlockPos corePos, BlockPos pos) {
        return withinCube(corePos, pos, Math.round(ImtConfig.Security.REACH_WITHOUT_ZONE));
    }

    /** Ligt deze plek binnen ZONE_RANGE blokken van de kern: zo ver mag zijn zone komen? */
    public static boolean withinZoneRange(BlockPos corePos, BlockPos pos) {
        return withinCube(corePos, pos, Math.round(ImtConfig.Security.ZONE_RANGE));
    }

    private static boolean withinCube(BlockPos centre, BlockPos pos, int reach) {
        return Math.abs(pos.getX() - centre.getX()) <= reach
                && Math.abs(pos.getY() - centre.getY()) <= reach
                && Math.abs(pos.getZ() - centre.getZ()) <= reach;
    }

    /** Waar de zone van de kern op {@code corePos} hooguit mag komen: ZONE_RANGE blokken in elke richting, als één kubus. */
    public static ZoneBox zoneRangeOf(BlockPos corePos) {
        int range = Math.round(ImtConfig.Security.ZONE_RANGE);
        return ZoneBox.of(corePos.offset(-range, -range, -range), corePos.offset(range, range, range));
    }

    /** Hoe ver een kern zonder zone reikt, als één kubus (dezelfde blokken als {@link #withinReachWithoutZone}). */
    public static ZoneBox reachWithoutZoneOf(BlockPos corePos) {
        int reach = Math.round(ImtConfig.Security.REACH_WITHOUT_ZONE);
        return ZoneBox.of(corePos.offset(-reach, -reach, -reach), corePos.offset(reach, reach, reach));
    }

    /**
     * Hoort het apparaat op {@code pos} (een camera, lezer, serverkast...) bij deze kern? Het ligt in zijn gebied
     * ({@link #inArea}). Zijn gebouw en zijn zone zijn van hem alleen; daarbuiten (een kern zonder zone) staat er ook geen
     * andere (geladen) kern zonder zone dichterbij die hier reikt (net als bij {@link #coreAt}, ook bij gelijke afstand).
     * (Niet gewoon {@code coreAt(...) == core}: een kern die net is neergezet, meldt zich pas een tick later aan.)
     */
    public static boolean ownedBy(Level level, SecurityCoreBlockEntity core, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        BlockPos own = core.getBlockPos();
        if (!inArea(server, own, pos)) {
            return false;
        }
        Set<BlockPos> set = LOADED.get(level.dimension());
        List<ZoneBox> building = buildingOf(server, own);
        if (set == null || hasZone(server, own) || building != null && inAny(building, pos)) {
            return true;
        }
        for (BlockPos other : List.copyOf(set)) {
            if (!other.equals(own) && closer(other, own, pos) && reachesWithoutZone(server, other, pos)
                    && level.getBlockEntity(other) instanceof SecurityCoreBlockEntity) {
                return false;
            }
        }
        return true;
    }

    // ================================================================ Gebouwen van deze mod (hoofdstuk 117)

    /** De gebouwen van deze mod waar een kern in hoort: de Biebia's Industries Tower en de bunker van Biebia's. */
    private static final java.util.Set<net.minecraft.resources.ResourceLocation> CORE_BUILDINGS = java.util.Set.of(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.senne.imttech.ImtTech.MODID, "biebia_tower"),
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.senne.imttech.ImtTech.MODID, "biebia_bunker"));

    /** Per kern: de stukken van het gebouw waar hij in staat (leeg als hij nergens in staat). Eén keer opgezocht. Niet bewaard. */
    private static final Map<GlobalPos, java.util.Optional<List<ZoneBox>>> BUILDINGS = new HashMap<>();

    /**
     * De stukken (kamers, verdiepingen) van de toren of bunker van Biebia's waar de kern op deze plek in staat, of null als
     * hij niet in zo'n gebouw staat. Zo'n gebouw hoort altijd bij zijn eigen kern, ook als je die gehackt hebt: daar komt
     * geen andere kern en geen zone van een ander (anders zette je er een eigen kern in of een zone overheen, en hoorden
     * zijn docks, laserwanden en kasten bij jou zonder te hacken). Rond het gebouw reikt zijn kern zoals elke kern zonder
     * zone (REACH_WITHOUT_ZONE, de dichtstbijzijnde kern wint, een zone gaat voor). Een gebouw verandert nooit, dus we zoeken het per kern één
     * keer op (dat kan zijn stuk wereld laden, alleen tot de gebouwen erin bekend zijn).
     */
    public static @Nullable List<ZoneBox> buildingOf(ServerLevel level, BlockPos corePos) {
        return BUILDINGS.computeIfAbsent(GlobalPos.of(level.dimension(), corePos), key -> {
            var start = level.structureManager().getStructureWithPieceAt(corePos,
                    holder -> holder.unwrapKey().map(id -> CORE_BUILDINGS.contains(id.location())).orElse(false));
            if (!start.isValid()) {
                return java.util.Optional.empty();
            }
            List<ZoneBox> pieces = new java.util.ArrayList<>();
            for (var piece : start.getPieces()) {
                var box = piece.getBoundingBox();
                pieces.add(ZoneBox.of(new BlockPos(box.minX(), box.minY(), box.minZ()), new BlockPos(box.maxX(), box.maxY(), box.maxZ())));
            }
            return java.util.Optional.of(List.copyOf(pieces));
        }).orElse(null);
    }

    /** Ligt deze plek in een van deze kubussen? */
    private static boolean inAny(List<ZoneBox> boxes, BlockPos pos) {
        for (ZoneBox box : boxes) {
            if (box.contains(pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * De (geladen) kern in wiens gebouw deze plek ligt, behalve {@code except}; null als hij in geen gebouw ligt. Liggen er
     * (bij uitzondering) twee gebouwen over de plek, dan de dichtstbijzijnde kern.
     */
    private static @Nullable BlockPos buildingCoreAt(ServerLevel level, BlockPos pos, @Nullable BlockPos except) {
        Set<BlockPos> set = LOADED.get(level.dimension());
        if (set == null) {
            return null;
        }
        BlockPos best = null;
        for (BlockPos corePos : List.copyOf(set)) {
            if (corePos.equals(except) || best != null && !closer(corePos, best, pos)) {
                continue;
            }
            List<ZoneBox> building = buildingOf(level, corePos);
            if (building != null && inAny(building, pos)) {
                best = corePos;
            }
        }
        return best;
    }

    /**
     * Een toren of bunker van Biebia's die pas nu opduikt (net gegenereerd, of voor het eerst geladen) binnen de zone van een
     * andere kern: de kubussen van die zone die het gebouw raken, gaan weg (dat had niet gemogen als het gebouw al bekend
     * was, zie {@link #zoneBlocked}). De kern zelf roept dit aan bij zijn eerste tick (SecurityCoreBlockEntity).
     */
    public static void freeBuilding(ServerLevel level, BlockPos corePos) {
        List<ZoneBox> building = buildingOf(level, corePos);
        if (building == null) {
            return;
        }
        ZoneData zones = ZoneData.get(level);
        for (ZoneData.Zone zone : List.copyOf(zones.in(level.dimension()))) {
            if (zone.core().pos().equals(corePos)) {
                continue;
            }
            List<ZoneBox> kept = new java.util.ArrayList<>();
            for (ZoneBox box : zone.boxes()) {
                if (!touchesAny(building, box)) {
                    kept.add(box);
                }
            }
            if (kept.size() == zone.boxes().size()) {
                continue;
            }
            int removed = zone.boxes().size() - kept.size();
            zones.setBoxes(zone.core(), kept);
            if (level.isLoaded(zone.core().pos()) && level.getBlockEntity(zone.core().pos()) instanceof SecurityCoreBlockEntity other) {
                other.logEvent(com.senne.imttech.security.os.EventLog.Type.ZONE, "Zone: " + removed + (removed == 1 ? " box" : " boxes")
                        + " removed: a Biebia's Industries building stands there");
            }
        }
    }

    /** Raakt deze kubus een van deze kubussen? */
    private static boolean touchesAny(List<ZoneBox> boxes, ZoneBox box) {
        for (ZoneBox piece : boxes) {
            if (piece.overlap(box) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Voor de tests: laat de kern op deze plek doen alsof hij in een gebouw met deze stukken staat, of zoek het weer gewoon
     * op ({@code null}). In de testwereld staat geen echte toren op de plek van een test.
     */
    public static void pretendBuildingForTests(ServerLevel level, BlockPos corePos, @Nullable List<ZoneBox> pieces) {
        GlobalPos here = GlobalPos.of(level.dimension(), corePos);
        if (pieces == null) {
            BUILDINGS.remove(here);
        } else {
            BUILDINGS.put(here, java.util.Optional.of(List.copyOf(pieces)));
        }
    }

    // ================================================================ Apparaten en hun kern (hoofdstuk 38)

    /**
     * De kern waar dit beveiligingsapparaat (keycardlezer, codepaneel, camera, torentje, serverrek, laserzender) bij
     * hoort: de kern aan het andere eind van zijn netwerkkabel (zie LanGrid). Met {@code [network] required = false} (een
     * oude wereld) de oude regel: de dichtstbijzijnde kern in wiens gebied het staat ({@link #coreAt}).
     *
     * Let op: {@link #coreAt} blijft "in wiens gebied staat dit", voor alles wat niet aan een kabel hangt (zones, robots,
     * docks, wie hier vertrouwd is). Een beveiligingstorentje met coreAt == null zou op iedereen schieten; daarom vragen
     * apparaten dit, en niet coreAt.
     */
    public static @Nullable SecurityCoreBlockEntity deviceCore(Level level, BlockPos device) {
        return com.senne.imttech.lan.LanGrid.required() ? com.senne.imttech.lan.LanGrid.coreOf(level, device)
                : coreAt(level, device);
    }

    /** Hoort dit beveiligingsapparaat bij deze kern (via zijn netwerkkabel; zonder netwerk-eis: in zijn gebied)? */
    public static boolean linkedTo(Level level, SecurityCoreBlockEntity core, BlockPos device) {
        return com.senne.imttech.lan.LanGrid.required() ? com.senne.imttech.lan.LanGrid.coreOf(level, device) == core
                : ownedBy(level, core, device);
    }

    /**
     * Hangt het blok op deze plek aan een netwerkkabel (een beveiligingsapparaat, en sinds hoofdstuk 112 een nieuw relais),
     * of hoort het bij een kern door waar het staat (een oud relais, koepel, stroomkast)? Een stukje wereld dat niet geladen is, laden we hiervoor niet in: dan
     * telt het als "door waar het staat".
     */
    private static boolean networked(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        // Hoofdstuk 112: een relais heeft een netwerkaansluiting, maar alleen een nieuw relais heeft het netwerk ook nodig;
        // een relais van daarvoor hoort bij een kern door waar het staat, zoals altijd.
        if (level.getBlockState(pos).getBlock() instanceof com.senne.imttech.redstone.RelayBlock) {
            return com.senne.imttech.redstone.ImtRedstone.needsNetwork(level, pos);
        }
        return level.getBlockState(pos).getBlock() instanceof com.senne.imttech.lan.NetworkConnectable;
    }

    /**
     * De kern die het ding op deze plek bestuurt (voor de diagrammen: "when turned_on", on("naam")): een
     * beveiligingsapparaat via zijn netwerkkabel ({@link #deviceCore}), al het andere via zijn gebied ({@link #coreAt}).
     */
    public static @Nullable SecurityCoreBlockEntity controllerOf(Level level, BlockPos pos) {
        return networked(level, pos) ? deviceCore(level, pos) : coreAt(level, pos);
    }

    /** Bestuurt deze kern het ding op deze plek? (Zie {@link #controllerOf}.) */
    public static boolean controls(Level level, SecurityCoreBlockEntity core, BlockPos pos) {
        return networked(level, pos) ? linkedTo(level, core, pos) : ownedBy(level, core, pos);
    }

    /**
     * Zit deze kubus voor de zone van de kern op {@code corePos} een ander in de weg? Een zone mag tot ZONE_RANGE blokken
     * van zijn kern liggen, maar (hoofdstuk 117):
     * <ul>
     *   <li>niet over de zone van een andere kern: zones liggen nooit over elkaar;</li>
     *   <li>niet om een andere kern heen: anders kon die zijn eigen kern niet eens meer weghakken;</li>
     *   <li>niet over een toren of bunker van Biebia's ({@link #buildingOf}): die hoort bij zijn eigen kern, anders waren
     *       zijn docks, laserwanden en kasten van jou zonder dat je hem hackte.</li>
     * </ul>
     * Het stuk rond een kern zonder zone telt niet (kernen mogen vlak bij elkaar staan): wie het eerst een zone tekent,
     * heeft die plek.
     */
    public static boolean zoneBlocked(ServerLevel level, BlockPos corePos, ZoneBox box) {
        return zoneObstacles(level, corePos).blocks(box);
    }

    /** Zo ver (blokken) reikt een toren of bunker van Biebia's hooguit vanaf zijn kern: verder zoeken we geen gebouwen op. */
    private static final int BUILDING_SIZE = 256;

    /**
     * Wat de zone van een kern in de weg kan zitten, een keer opgezocht: de zones van andere kernen, en de andere kernen
     * die dichtbij genoeg staan om in de weg te zitten (met hun gebouw). Wie veel kubussen na elkaar controleert (de
     * Autoscan), vraagt dit een keer en kijkt dan met {@link ZoneObstacles#blocks}.
     */
    public static ZoneObstacles zoneObstacles(ServerLevel level, BlockPos corePos) {
        List<ZoneData.Zone> zones = new java.util.ArrayList<>();
        for (ZoneData.Zone zone : ZoneData.get(level).in(level.dimension())) {
            if (!zone.core().pos().equals(corePos)) {
                zones.add(zone);
            }
        }
        int near = Math.round(ImtConfig.Security.ZONE_RANGE) + BUILDING_SIZE;
        List<BlockPos> cores = new java.util.ArrayList<>();
        for (BlockPos other : otherCores(level, corePos)) {
            if (withinCube(corePos, other, near)) {
                cores.add(other);
            }
        }
        return new ZoneObstacles(level, List.copyOf(zones), List.copyOf(cores));
    }

    /** Wat een zone in de weg zit (zie {@link #zoneObstacles}). */
    public static final class ZoneObstacles {
        private final ServerLevel level;
        private final List<ZoneData.Zone> zones;
        private final List<BlockPos> cores;

        private ZoneObstacles(ServerLevel level, List<ZoneData.Zone> zones, List<BlockPos> cores) {
            this.level = level;
            this.zones = zones;
            this.cores = cores;
        }

        /** Raakt deze kubus een andere zone of een gebouw van Biebia's, of staat er een andere kern in? */
        public boolean blocks(ZoneBox box) {
            for (ZoneData.Zone zone : zones) {
                if (zone.touches(box)) {
                    return true;
                }
            }
            for (BlockPos core : cores) {
                if (box.contains(core)) {
                    return true;
                }
            }
            // Alleen bij een kern die dicht genoeg bij de kubus staat, zoeken we op of hij in een gebouw staat.
            ZoneBox around = ZoneBox.of(box.min().offset(-BUILDING_SIZE, -BUILDING_SIZE, -BUILDING_SIZE),
                    box.max().offset(BUILDING_SIZE, BUILDING_SIZE, BUILDING_SIZE));
            for (BlockPos core : cores) {
                if (around.contains(core)) {
                    List<ZoneBox> building = buildingOf(level, core);
                    if (building != null && touchesAny(building, box)) {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /**
     * Alle andere kernen in deze dimensie: uit het netwerk van de wereld (ook in stukken wereld die niet geladen zijn) en
     * wat er geladen is (ook een kern die net is neergezet en nog geen IP heeft). Een kern die nog in het netwerk staat
     * maar in de wereld weg is (bijvoorbeeld met /setblock), ruimen we meteen op.
     */
    private static Set<BlockPos> otherCores(ServerLevel level, BlockPos corePos) {
        Set<BlockPos> cores = new HashSet<>();
        var network = com.senne.imttech.security.net.CoreNetworkData.get(level);
        for (GlobalPos other : List.copyOf(network.all().values())) {
            if (!other.dimension().equals(level.dimension()) || other.pos().equals(corePos)) {
                continue;
            }
            if (level.isLoaded(other.pos()) && !(level.getBlockEntity(other.pos()) instanceof SecurityCoreBlockEntity)) {
                network.release(other);
                continue;
            }
            cores.add(other.pos());
        }
        Set<BlockPos> loaded = LOADED.get(level.dimension());
        if (loaded != null) {
            cores.addAll(loaded);
        }
        cores.remove(corePos);
        return cores;
    }

    /**
     * Moet de beveiliging hierop schieten? {@code core} mag null zijn (een torentje zonder kern
     * gedraagt zich als een kern zonder eigenaar).
     */
    public static boolean isIntruder(ServerLevel level, @Nullable SecurityCoreBlockEntity core, LivingEntity entity) {
        if (!entity.isAlive() || entity.isInvulnerable() || entity instanceof SecurityDroneEntity) {
            return false;
        }
        boolean unclaimed = core == null || core.ownerId() == null;
        if (entity instanceof Player player) {
            if (player.isCreative() || player.isSpectator()) {
                return false;
            }
            // De trainee van een oefening (hoofdstuk 27) is een indringer, ook in zijn eigen basis en ook zonder PvP (hij
            // koos er zelf voor; met trainingsschoten kost een treffer alleen een leven).
            if (core != null && com.senne.imttech.training.Training.isDrillTrainee(level, core.getBlockPos(), player.getUUID())) {
                return true;
            }
            if (isFriend(level, core, player)) {
                return false;
            }
            // Van niemand: iedereen is een indringer. Van jou: andere spelers alleen als de
            // eigenaar dat op de desktop heeft aangezet, of als ze net een valstrik openden (en
            // dan nog alleen met PvP aan).
            return unclaimed || (core.attackPlayers() || core.isHunted(player.getUUID()))
                    && level.getServer().isPvpAllowed();
        }
        if (entity instanceof DroneEntity drone) {
            ServerPlayer owner = drone.getOwnerId() == null ? null
                    : level.getServer().getPlayerList().getPlayer(drone.getOwnerId());
            return owner != null && isIntruder(level, core, owner);
        }
        // Monsters: van niemand altijd, van jou alleen als "attack mobs" aan staat (standaard aan).
        return entity instanceof Enemy && (unclaimed || core.attackMobs());
    }

    /**
     * Is deze speler de eigenaar van de kern, zit hij in zijn team, of is hij vertrouwd? Niet tijdens een oefening waarin
     * hij de trainee is (hoofdstuk 27): dan is hij voor zijn eigen basis een vreemde.
     */
    public static boolean isFriend(ServerLevel level, @Nullable SecurityCoreBlockEntity core, Player player) {
        if (core == null || core.ownerId() == null
                || com.senne.imttech.training.Training.isDrillTrainee(level, core.getBlockPos(), player.getUUID())) {
            return false;
        }
        if (core.ownerId().equals(player.getUUID()) || core.isTrusted(player.getUUID())) {
            return true;
        }
        // Het scoreboard-team alleen als de config het zegt (hoofdstuk 110, zie TeamFriends).
        return TeamFriends.allied(level, core.ownerName(), player);
    }

    /**
     * Waarom er op deze plek geen kern mag komen, of null als het mag. Hoofdstuk 117 (jouw wens: "zorg dat je base security
     * core's altijd vlak bij elkaar kunt zetten als er geen protected zone is; het kan ook niet geplaatst worden in een zone
     * zonder power, anders kun je simpel de power weg doen en dan de base claimen"):
     * <ul>
     *   <li>Vlak naast een andere kern: mag (tot hoofdstuk 117 moesten twee kernen meer dan 400 blokken uit elkaar).</li>
     *   <li>In de zone van een andere kern: nooit, ook niet als die zone geen stroom heeft (of de kern niet geladen is).</li>
     *   <li>In een gebouw van deze mod (een toren of bunker van Biebia's, of een wrak): nooit. Zo'n gebouw hoort bij zijn
     *       eigen kern ({@link #buildingOf}); anders zette je er een eigen kern in, en hoorden zijn docks, laserwanden en
     *       kasten bij jou zonder dat je hem hackte.</li>
     * </ul>
     * We kijken in het overzicht van de wereld (ZoneData en het netwerk, CoreNetworkData), zodat ook zones en kernen
     * meetellen in stukken wereld die niet geladen zijn. Een kern die daar nog staat maar in de wereld weg is, ruimen we
     * meteen op.
     */
    public static @Nullable Component placementProblem(ServerLevel level, BlockPos pos) {
        ZoneData zones = ZoneData.get(level);
        for (ZoneData.Zone zone : List.copyOf(zones.in(level.dimension()))) {
            BlockPos core = zone.core().pos();
            if (core.equals(pos) || !zone.contains(pos)) {
                continue;
            }
            if (level.isLoaded(core) && !(level.getBlockEntity(core) instanceof SecurityCoreBlockEntity)) {
                zones.forget(zone.core()); // de kern is weg (bijvoorbeeld met /setblock): zijn zone ook
                continue;
            }
            return Component.translatable("security.imttech.msg.in_zone", zone.ownerName().isEmpty() ? "?" : zone.ownerName());
        }
        if (BaseTrust.inBuilding(level, pos)) {
            return Component.translatable("security.imttech.msg.in_building");
        }
        // Ook het gebouw van een kern die we al kennen (in de tests staat er zo een zonder echt gebouw).
        for (BlockPos other : otherCores(level, pos)) {
            if (withinCube(other, pos, BUILDING_SIZE)) {
                List<ZoneBox> building = buildingOf(level, other);
                if (building != null && inAny(building, pos)) {
                    return Component.translatable("security.imttech.msg.in_building");
                }
            }
        }
        return null;
    }

    /** Zou een kern hier mogen staan? (Zie {@link #placementProblem}.) */
    public static boolean mayPlace(ServerLevel level, BlockPos pos) {
        return placementProblem(level, pos) == null;
    }

    /** Bij het afsluiten van de server: alles vergeten. */
    public static void clear() {
        LOADED.clear();
        BUILDINGS.clear();
    }
}
