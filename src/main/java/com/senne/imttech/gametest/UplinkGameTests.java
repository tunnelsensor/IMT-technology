package com.senne.imttech.gametest;

import com.senne.imttech.ImtTech;
import com.senne.imttech.arsenal.ArsenalDrone;
import com.senne.imttech.arsenal.ArsenalServer;
import com.senne.imttech.arsenal.ImtArsenal;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.drone.DroneEntity;
import com.senne.imttech.drone.ImtDrones;
import com.senne.imttech.imt.ImtAction;
import com.senne.imttech.imt.ImtCooldown;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtFeature;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.logic.DatacenterGlasses;
import com.senne.imttech.logic.Datacenters;
import com.senne.imttech.logic.LogicServer;
import com.senne.imttech.logic.LogicStats;
import com.senne.imttech.nav.ImtNavigation;
import com.senne.imttech.nav.ImtWaypoints;
import com.senne.imttech.nav.Waypoint;
import com.senne.imttech.network.WaypointActionPayload;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.security.SecurityCoreBlockEntity;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Hoofdstuk 106 (jouw wens van 13 november: "IMT is te OP: scan cooldown omhoog, drones kun je niet in nether of end
 * gebruiken tenzij je met een bepaalde drone, vb escort drones, naar daar gaat ..., je hebt een datacenter nodig voor de
 * IMT glasses zijn data ...").
 *
 * Wat elke test hier vastlegt:
 * - de ertsen-scan wacht langer dan de ertsen zichtbaar blijven (je ziet ze niet meer altijd);
 * - in de Nether heb je alleen de drones die met je meekwamen door het portaal, en die blijven daar klaar voor elke
 *   drone-actie; terug in de gewone wereld hangt alles weer in zijn baan;
 * - zonder datacenter doet de bril niets dat nadenkt of iets nieuws bewaart (wat je al bewaarde, blijft); in creative
 *   heb je er geen nodig;
 * - met een datacenter gebruikt elk verzoek er een stuk van (rekenkracht, RAM, SSD), en is het vol, dan wordt een
 *   nieuw verzoek geweigerd zonder dat het iets kost.
 */
@GameTestHolder(ImtTech.MODID)
@PrefixGameTestTemplate(false)
public final class UplinkGameTests {
    private UplinkGameTests() {}

    private static final String ARENA = "empty";

    /**
     * Vóór de tests van deze batch: de bril heeft een datacenter nodig (zoals in een echte wereld). Voor alle andere
     * tests staat die regel uit, zodat de tests van de bril van vóór hoofdstuk 106 geen datacenter hoeven te bouwen.
     */
    @net.minecraft.gametest.framework.BeforeBatch(batch = "uplink")
    public static void uplinkOn(ServerLevel level) {
        DatacenterGlasses.overrideRequired(true);
        // Een verzoek houdt het datacenter in deze tests maar een seconde bezig (in het spel 5 tot 10 seconden), zodat de
        // test niet lang hoeft te wachten tot het weer vrij is.
        oldScanTicks = ImtConfig.Uplink.SCAN_TICKS;
        oldSatelliteTicks = ImtConfig.Uplink.SATELLITE_TICKS;
        ImtConfig.Uplink.SCAN_TICKS = BUSY_TICKS;
        ImtConfig.Uplink.SATELLITE_TICKS = BUSY_TICKS;
    }

    /** Daarna weer uit, voor de andere tests. */
    @net.minecraft.gametest.framework.AfterBatch(batch = "uplink")
    public static void uplinkOff(ServerLevel level) {
        DatacenterGlasses.overrideRequired(null);
        ImtConfig.Uplink.SCAN_TICKS = oldScanTicks;
        ImtConfig.Uplink.SATELLITE_TICKS = oldSatelliteTicks;
    }

    /** Zo lang (ticks) is het datacenter in de tests van deze batch met een scan of een satellietaanval bezig. */
    private static final int BUSY_TICKS = 20;
    private static float oldScanTicks = ImtConfig.Uplink.SCAN_TICKS;
    private static float oldSatelliteTicks = ImtConfig.Uplink.SATELLITE_TICKS;

    private static void check(boolean ok, String message) {
        if (!ok) {
            throw new GameTestAssertException(message);
        }
    }

    private static boolean same(double a, double b) {
        return Math.abs(a - b) < 1.0e-4;
    }

    private static void waypoint(ServerPlayer player, WaypointActionPayload.Op op, int index, String name) {
        ImtNavigation.handleWaypointAction(player, new WaypointActionPayload(op, index, name, Waypoint.COLORS[0]));
    }

    private static ImtWaypoints waypoints(ServerPlayer player) {
        return player.getData(ModAttachments.WAYPOINTS);
    }

    // ================================================================ De ertsen-scan

    /**
     * Jouw wens: "scan cooldown omhoog". De wachttijd is nu langer dan de ertsen zichtbaar blijven (vroeger korter: je zag
     * ze dus altijd), en na een scan kun je niet meteen opnieuw: de tweede scan kost niets en doet niets.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "ore_scan_wait")
    public static void ore_scan_waits_longer_than_the_ores_stay(GameTestHelper helper) {
        check(ImtConfig.Scanner.ORE_SCAN_WAIT > ImtConfig.Scanner.ORE_SCAN_DURATION,
                "De scan wacht " + ImtConfig.Scanner.ORE_SCAN_WAIT + " ticks, maar de ertsen blijven "
                        + ImtConfig.Scanner.ORE_SCAN_DURATION + " ticks zichtbaar: je ziet ze dan altijd");
        ServerPlayer player = ImtTechGameTests.makeSwitchablePlayer(helper, new BlockPos(1, 3, 1), "scan-waiter", () -> true);
        ItemStack glasses = ImtData.getWornGlasses(player);
        double full = ImtData.getExactEnergy(glasses);
        ImtServer.handleAction(player, ImtAction.ORE_SCAN.ordinal());
        double afterOne = ImtData.getExactEnergy(glasses);
        check(same(full - afterOne, ImtConfig.Scanner.ORE_SCAN_COST), "De eerste scan kostte " + (full - afterOne));
        check(!ImtServer.isReady(player, ImtCooldown.ORE_SCAN), "Na een scan zou de wachttijd moeten lopen");
        ImtServer.handleAction(player, ImtAction.ORE_SCAN.ordinal());
        check(same(ImtData.getExactEnergy(glasses), afterOne), "Een tweede scan tijdens de wachttijd kostte toch energie");
        helper.succeed();
    }

    // ================================================================ Drones in de Nether

    /** Haalt een drone uit je arsenaal en zet hem op deze plek in de wereld (zonder dat hij eerst uit de lucht komt). */
    private static DroneEntity launch(ServerPlayer owner, ServerLevel level, DroneEntity.Mode mode, int slot, Vec3 pos) {
        ArsenalDrone picked = ArsenalServer.checkOutOne(owner);
        check(picked != null, "Er hangt geen drone klaar in het arsenaal");
        DroneEntity drone = ModEntities.DRONE.get().create(level);
        check(drone != null, "Kon geen drone maken");
        drone.setup(owner, mode, slot, -1);
        drone.setArsenalId(picked.id());
        drone.moveTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);
        check(level.addFreshEntity(drone), "De drone kwam niet in de wereld");
        return drone;
    }

    /**
     * Doet alsof een testspeler in een andere wereld is, zoals na een portaal: voor de mod telt alleen in welke wereld hij
     * zegt te zijn ({@code player.level()}) en waar hij staat.
     *
     * We melden hem daar NIET echt aan, om twee redenen:
     * - met teleportTo stuurt NeoForge een speler zijn gegevens opnieuw, en een testspeler (zonder echt spel erachter)
     *   kan die niet ontvangen ("Payload neoforge:sync_attachments may not be sent to the client");
     * - een speler die echt in een wereld staat, laat Minecraft daar alles om hem heen maken, zo ver als de server laat
     *   kijken (honderden stukken Nether of End). De testserver tikt zo snel als hij kan, maar wereld maken kost echte
     *   tijd: dat werk op de achtergrond liet andere tests omvallen die op een geladen stuk wereld wachten (een raiddrone
     *   "begon 102 blokken hoog", de toren miste een robot, en de Nether-test hieronder begon nooit).
     * Het echte portaal (met een echte speler) is gezien in de rondleiding: gradlew runVisualCheck -Ponly=uplink.
     *
     * Gevolg voor een test: een drone in die wereld vindt de testspeler daar niet (hij staat niet in de lijst van die
     * wereld) en vliegt bij zijn eerste tick terug. Boeken gaat wel goed: dat zoekt de speler op de hele server.
     */
    private static void toWorld(ServerPlayer player, ServerLevel level, Vec3 pos) {
        player.setServerLevel(level);
        player.moveTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);
        check(player.level() == level, "De testspeler kwam niet in de andere wereld");
    }

    /**
     * Laat in deze wereld elk stuk los dat een eerdere, mislukte ronde vastgehouden liet (de testwereld blijft tussen de
     * rondes bestaan). In de Nether en het End van de testwereld houden alleen tests iets vast, en elke test in zijn
     * eigen batch, dus dit raakt niemand anders.
     */
    private static void releaseLeftovers(ServerLevel level) {
        for (long key : new java.util.ArrayList<>(level.getForcedChunks())) {
            level.setChunkForced(net.minecraft.world.level.ChunkPos.getX(key), net.minecraft.world.level.ChunkPos.getZ(key), false);
        }
    }

    /**
     * Jouw wens (hoofdstuk 106): "drones kun je niet in nether of end gebruiken tenzij je met een bepaalde drone, vb escort
     * drones, naar daar gaat; dan blijft het aantal drones dat je meenam voor elke andere drone actie beschikbaar". En
     * hoofdstuk 107: "zorg dat de satelliet niet in nether en end werkt".
     *
     * Je staat in de gewone wereld met twee escortedrones om je heen en dertig drones in je baan, en gaat naar de Nether:
     * de twee gaan mee; de dertig in je baan kunnen er niet bij (een verkenner vertrekt niet, en Recall weigert). Zijn de
     * twee daar klaar, dan wachten ze bij jou ("With you"), en daarmee kun je daar wel een verkenner sturen. Terug in de
     * gewone wereld hangen ze alle tweeëndertig weer in hun baan. Daarna de satelliet (zie satelliteStaysInOrbit).
     *
     * Alles gebeurt in één tick, en zonder dat er in de Nether of het End een stuk wereld geladen wordt. De testserver
     * tikt zo snel als hij kan, en wachten op wereld die nog gemaakt moet worden maakte deze test wisselvallig (en het
     * maken zelf stoorde andere tests). De drones in de Nether vinden we daarom in de lijst van het arsenaal
     * (ArsenalServer.onAJob), niet in de wereld, en "klaar" spelen we na door ze weg te halen: dat boekt hetzelfde als
     * wegvliegen (ArsenalServer.returnHome). Het echte portaal is gezien in de rondleiding (-Ponly=uplink).
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "nether_drones")
    public static void drones_in_the_nether_are_the_ones_you_brought(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel();
        ServerLevel nether = overworld.getServer().getLevel(Level.NETHER);
        ServerLevel end = overworld.getServer().getLevel(Level.END);
        check(nether != null && end != null, "Deze testserver heeft geen Nether of geen End");
        releaseLeftovers(nether);
        ServerPlayer owner = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 3, 1), "nether-owner");
        ItemStack glasses = ImtData.getWornGlasses(owner);
        Vec3 home = owner.position();
        // "In de Nether" staat hij op dezelfde x en z, hoog boven de test (zie toWorld).
        Vec3 away = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 0, 1)).atY(100));

        // De regels van het arsenaal zelf, zonder wereld: wat onderweg stond, wacht in een wereld zonder baan bij jou; in
        // een wereld met een baan hangt alles weer in zijn baan, ook wat bij je wachtte.
        UUID sample = UUID.randomUUID();
        ImtArsenal busy = ImtArsenal.EMPTY.with(ArsenalDrone.fresh(sample).withState(ArsenalDrone.State.DEPLOYED));
        check(busy.groundDeployed(false).find(sample).state() == ArsenalDrone.State.STANDBY
                        && busy.groundDeployed(true).find(sample).state() == ArsenalDrone.State.ORBIT,
                "Inloggen: wat onderweg stond hoort zonder baan bij je te wachten, en met een baan in zijn baan te hangen");
        ImtArsenal waiting = busy.groundDeployed(false);
        check(waiting.readyCount(false) == 1 && waiting.readyCount(true) == 0 && waiting.availableCount() == 0
                        && waiting.standbyToOrbit().availableCount() == 1,
                "Een drone die bij je wacht is alleen klaar in een wereld zonder baan, en hangt daarna weer in zijn baan");

        // Je escorte staat aan: twee drones om je heen.
        int all = ArsenalServer.get(owner).size();
        if (!ImtData.isEnabled(glasses, ImtFeature.GUARD_DRONES)) {
            ImtData.toggle(glasses, ImtFeature.GUARD_DRONES);
        }
        DroneEntity first = launch(owner, overworld, DroneEntity.Mode.GUARD, 0, home.add(1.5, 2, 0));
        DroneEntity second = launch(owner, overworld, DroneEntity.Mode.GUARD, 1, home.add(-1.5, 2, 0));
        ArsenalServer.follow(owner); // hier sta je nu
        check(ArsenalServer.readyCount(owner) == all - 2, "In de gewone wereld hangen er " + ArsenalServer.readyCount(owner)
                + " klaar in plaats van " + (all - 2));

        // Door het portaal.
        toWorld(owner, nether, away);
        ArsenalServer.follow(owner);
        check(first.isRemoved() && second.isRemoved(), "De escortedrones bleven in de gewone wereld");
        List<DroneEntity> guards = ArsenalServer.onAJob(owner).stream()
                .filter(drone -> drone.level() == nether && drone.getMode() == DroneEntity.Mode.GUARD).toList();
        check(guards.size() == 2 && ArsenalServer.flying(owner, DroneEntity.Mode.GUARD) == 2,
                "In de Nether vliegen er " + guards.size() + " escortedrones in plaats van 2");
        ImtArsenal arrived = ArsenalServer.get(owner);
        check(arrived.deployedCount() == 2 && arrived.orbitCount() == all - 2,
                "Na het portaal: " + arrived.deployedCount() + " onderweg en " + arrived.orbitCount() + " in een baan");
        // Wat in je baan hangt, kan hier niet bij: er vertrekt niets.
        check(ArsenalServer.readyCount(owner) == 0, "Zonder baan zijn er toch " + ArsenalServer.readyCount(owner) + " klaar");
        check(ImtDrones.launchScout(owner) == null, "Er vertrok een verkenner uit je baan, in de Nether");
        check(!ArsenalServer.recall(owner, arrived.pickAvailable(1).getFirst().id()), "Een drone uit je baan landde in de Nether");

        // De escorte gaat uit en de twee zijn klaar: ze wachten bij jou, niet in hun baan.
        ImtData.toggle(glasses, ImtFeature.GUARD_DRONES);
        guards.forEach(DroneEntity::discard);
        ImtArsenal standing = ArsenalServer.get(owner);
        check(standing.deployedCount() == 0 && standing.readyCount(false) == 2 && standing.orbitCount() == all - 2,
                "De escorte is klaar: er wachten " + standing.readyCount(false) + " drones bij je, " + standing.deployedCount()
                        + " zijn nog onderweg en " + standing.orbitCount() + " hangen in je baan");
        // Met wat je meenam kan elke andere drone-actie: hier een verkenner.
        DroneEntity scout = ImtDrones.launchScout(owner);
        check(scout != null && scout.level() == nether, "Met twee drones bij je vertrok er geen verkenner in de Nether");
        check(ArsenalServer.readyCount(owner) == 1, "Na de verkenner wachten er " + ArsenalServer.readyCount(owner));
        scout.discard(); // klaar: hij wacht weer bij je (niet in zijn baan)
        check(ArsenalServer.readyCount(owner) == 2 && ArsenalServer.get(owner).orbitCount() == all - 2,
                "De verkenner ging naar zijn baan in plaats van bij je te wachten");

        // Terug naar de gewone wereld: alles hangt weer in zijn baan.
        toWorld(owner, overworld, home);
        ArsenalServer.follow(owner);
        ImtArsenal back = ArsenalServer.get(owner);
        check(back.availableCount() == all && back.readyCount(false) == 0,
                "Terug in de gewone wereld hangen er " + back.availableCount() + " van de " + all + " in hun baan");

        satelliteStaysInOrbit(helper, owner, nether, end, home, away);
        helper.succeed();
    }

    // ================================================================ De satelliet in de Nether en het End

    /**
     * Hoofdstuk 107, jouw wens: "zorg dat de satelliet niet in nether en end werkt". Je satelliet hangt in dezelfde baan
     * als je drones, om de gewone wereld: daar vuurt de precisieraket op wat je aankijkt; in de Nether en het End doet
     * geen van de drie aanvallen iets (geen aanval onderweg, geen energie weg, geen wachttijd); en terug in de gewone
     * wereld werkt hij weer.
     *
     * Ook zonder deze regel hield de mod een aanval daar tegen, maar om een andere reden ("het doel moet open lucht
     * zien": die werelden hebben geen hemellicht). Dat leggen we hier ook vast, zodat we het merken als dat ooit
     * verandert.
     *
     * Dit is een stuk van de test hierboven en geen eigen test: een test erbij (en zeker een batch erbij) zet de andere
     * tests op een andere plek in de testwereld, en een paar bestaande tests kunnen daar niet tegen (zie PLAN, hoofdstuk
     * 107).
     */
    private static void satelliteStaysInOrbit(GameTestHelper helper, ServerPlayer owner, ServerLevel nether, ServerLevel end,
                                              Vec3 home, Vec3 away) {
        ServerLevel overworld = helper.getLevel();
        // De regel zelf, en zijn schakelaar in de config.
        check(com.senne.imttech.arsenal.Orbit.reachable(overworld) && !com.senne.imttech.arsenal.Orbit.reachable(nether)
                && !com.senne.imttech.arsenal.Orbit.reachable(end), "Er hoort alleen boven de gewone wereld een baan te hangen");
        boolean rule = ImtConfig.Drones.NO_ORBIT_IN_NETHER_AND_END;
        try {
            ImtConfig.Drones.NO_ORBIT_IN_NETHER_AND_END = false;
            check(com.senne.imttech.arsenal.Orbit.reachable(nether) && com.senne.imttech.arsenal.Orbit.reachable(end),
                    "Met noOrbitInNetherAndEnd = false hoort de oude regel te gelden");
        } finally {
            ImtConfig.Drones.NO_ORBIT_IN_NETHER_AND_END = rule;
        }

        ItemStack glasses = ImtData.getWornGlasses(owner);
        Husk target = helper.spawn(EntityType.HUSK, new BlockPos(12, 3, 12));
        target.setNoAi(true);
        target.setNoGravity(true);
        com.senne.imttech.satellite.SatelliteWorkshop.install(owner, com.senne.imttech.satellite.SatelliteUpgrade.BOMBARDMENT, 1);
        com.senne.imttech.satellite.SatelliteWorkshop.install(owner, com.senne.imttech.satellite.SatelliteUpgrade.SWEEP, 1);
        UUID id = owner.getUUID();

        // In de gewone wereld: de precisieraket vuurt op wat je aankijkt.
        owner.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.position());
        double full = ImtData.getExactEnergy(glasses);
        ImtServer.handleAction(owner, ImtAction.STRIKE_PRECISION.ordinal());
        check(com.senne.imttech.satellite.SatelliteStrikes.pendingFor(id) != null
                        && same(full - ImtData.getExactEnergy(glasses), ImtConfig.Satellite.COST_PRECISION),
                "In de gewone wereld vuurde de precisieraket niet");
        com.senne.imttech.satellite.SatelliteStrikes.cancel(id);
        ImtServer.resetForShowcase(owner); // de wachttijd weg, zodat hieronder alleen de wereld het verschil maakt

        // In de Nether en het End: geen van de drie aanvallen doet iets.
        double before = ImtData.getExactEnergy(glasses);
        for (ServerLevel world : List.of(nether, end)) {
            toWorld(owner, world, away); // geen stuk wereld laden of maken: de regel kijkt alleen in welke wereld je bent
            for (ImtAction strike : List.of(ImtAction.STRIKE_PRECISION, ImtAction.SATELLITE_STRIKE,
                    ImtAction.STRIKE_BOMBARDMENT, ImtAction.STRIKE_SWEEP)) {
                check(strike.isSatelliteStrike(), strike + " hoort een satellietaanval te zijn");
                ImtServer.handleAction(owner, strike.ordinal());
                check(com.senne.imttech.satellite.SatelliteStrikes.pendingFor(id) == null,
                        strike + " begon een aanval in " + world.dimension().location());
                check(same(ImtData.getExactEnergy(glasses), before) && ImtServer.isReady(owner, ImtCooldown.SATELLITE),
                        strike + " kostte energie of begon een wachttijd in " + world.dimension().location());
            }
            // Waarom het vroeger ook al niet kon: geen hemellicht, dus nergens "open lucht", ook niet helemaal bovenaan.
            check(!world.canSeeSky(BlockPos.containing(away).atY(world.getMaxBuildHeight() - 2)),
                    "In " + world.dimension().location() + " is er wel open lucht: dan hield vroeger niets een aanval tegen");
        }

        // Terug in de gewone wereld werkt hij weer.
        toWorld(owner, overworld, home);
        ArsenalServer.follow(owner);
        owner.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target.position());
        ImtServer.handleAction(owner, ImtAction.STRIKE_PRECISION.ordinal());
        check(com.senne.imttech.satellite.SatelliteStrikes.pendingFor(id) != null,
                "Terug in de gewone wereld vuurde de precisieraket niet meer");
        com.senne.imttech.satellite.SatelliteStrikes.cancel(id);
    }

    // ================================================================ De bril zonder datacenter

    /**
     * Jouw wens: "je hebt een datacenter nodig voor de IMT glasses zijn data ... De dingen die spelers nu al in IMT
     * glasses hebben bewaard zonder datacenter blijven, nieuwe dingen moeten dit toepassen. Voor bijvoorbeeld satelliet
     * gebruik moet dit al toegepast worden."
     *
     * Zonder datacenter: een scan, een markering, een nieuw waypoint en navigeren worden geweigerd, en dat kost niets (geen
     * energie, geen wachttijd). Een waypoint dat je al had, blijft staan; je kunt het nog hernoemen en weghalen. In
     * creative heb je geen datacenter nodig.
     */
    @GameTest(template = ARENA, timeoutTicks = 40, batch = "uplink")
    public static void glasses_without_a_datacenter_think_and_store_nothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = ImtTechGameTests.makeSurvivalOwner(helper, new BlockPos(1, 3, 1), "uplink-nobody");
        ItemStack glasses = ImtData.getWornGlasses(player);
        check(DatacenterGlasses.link(player) == DatacenterGlasses.Link.NONE,
                "Zonder datacenter is de bril " + DatacenterGlasses.link(player));
        double full = ImtData.getExactEnergy(glasses);

        // Nadenken: geweigerd, en het kost niets.
        ImtServer.handleAction(player, ImtAction.ORE_SCAN.ordinal());
        check(same(ImtData.getExactEnergy(glasses), full) && ImtServer.isReady(player, ImtCooldown.ORE_SCAN),
                "Een scan zonder datacenter kostte energie of begon een wachttijd");
        for (DatacenterGlasses.Kind kind : DatacenterGlasses.Kind.values()) {
            check(!DatacenterGlasses.ready(player, kind), "Zonder datacenter mocht " + kind + " toch");
        }
        ImtServer.handleAction(player, ImtAction.TOGGLE_THREAT_SCAN.ordinal());
        check(!ImtData.isEnabled(glasses, ImtFeature.THREAT_SCAN), "De dreigingsscan ging aan zonder datacenter");
        Husk husk = helper.spawn(EntityType.HUSK, new BlockPos(3, 3, 1));
        husk.setNoAi(true);
        ImtServer.handleMark(player, husk.getId());
        check(ImtServer.allMarkedTargets(player).isEmpty(), "Een markering lukte zonder datacenter");

        // Bewaren: wat je al had blijft, iets nieuws kan niet.
        player.setData(ModAttachments.WAYPOINTS, ImtWaypoints.EMPTY.with(
                new Waypoint("Old base", level.dimension(), player.blockPosition(), Waypoint.COLORS[0])));
        waypoint(player, WaypointActionPayload.Op.ADD, 0, "New base");
        check(waypoints(player).waypoints().size() == 1, "Er kwam een waypoint bij zonder datacenter");
        waypoint(player, WaypointActionPayload.Op.NAVIGATE, 0, "");
        check(waypoints(player).active() == ImtWaypoints.NONE, "Navigeren begon zonder datacenter");
        waypoint(player, WaypointActionPayload.Op.RENAME, 0, "Older base");
        check(waypoints(player).waypoints().getFirst().name().equals("Older base"), "Een oud waypoint hernoemen lukte niet");
        ImtServer.reportToDatacenter(player);
        check(same(Datacenters.get(level).glassesData(player.getUUID()), DatacenterGlasses.waypointsMb(1)),
                "Wat de bril al bewaart (1 waypoint) werd niet onthouden: " + Datacenters.get(level).glassesData(player.getUUID()));
        waypoint(player, WaypointActionPayload.Op.REMOVE, 0, "");
        check(waypoints(player).waypoints().isEmpty(), "Een oud waypoint weghalen lukte niet");
        ImtServer.reportToDatacenter(player);

        // Uitzetten kan altijd: de doelwit-analyse staat standaard aan.
        check(ImtData.isEnabled(glasses, ImtFeature.TARGET_ANALYSIS), "De doelwit-analyse hoort standaard aan te staan");
        ImtServer.handleAction(player, ImtAction.TOGGLE_TARGET_ANALYSIS.ordinal());
        check(!ImtData.isEnabled(glasses, ImtFeature.TARGET_ANALYSIS), "De doelwit-analyse uitzetten lukte niet zonder datacenter");
        ImtServer.handleAction(player, ImtAction.TOGGLE_NIGHT_VISION.ordinal());
        check(ImtData.isEnabled(glasses, ImtFeature.NIGHT_VISION), "Nachtzicht hoort zonder datacenter te werken");

        // In creative heb je geen datacenter nodig.
        ServerPlayer creative = ImtTechGameTests.makeSwitchablePlayer(helper, new BlockPos(5, 3, 1), "uplink-creative", () -> true);
        ItemStack creativeGlasses = ImtData.getWornGlasses(creative);
        check(DatacenterGlasses.link(creative) == DatacenterGlasses.Link.NOT_NEEDED, "In creative: " + DatacenterGlasses.link(creative));
        double creativeFull = ImtData.getExactEnergy(creativeGlasses);
        ImtServer.handleAction(creative, ImtAction.ORE_SCAN.ordinal());
        check(same(creativeFull - ImtData.getExactEnergy(creativeGlasses), ImtConfig.Scanner.ORE_SCAN_COST),
                "In creative zou de scan zonder datacenter moeten werken");
        waypoint(creative, WaypointActionPayload.Op.ADD, 0, "Anywhere");
        check(waypoints(creative).waypoints().size() == 1, "In creative zou een waypoint zonder datacenter moeten kunnen");
        helper.succeed();
    }

    // ================================================================ De bril op een datacenter

    /**
     * Jouw wens: "Elke satelliet request, waypoint gebruikt wat van de server: waypoint neemt enkel ssd en ram, cpu en/of
     * gpu als je navigeert. Andere dingen die soort van nadenken gebruiken dus altijd cpu en/of gpu en ram. Data opslag
     * moet op een ssd."
     *
     * Een datacenter met één CPU, één latje RAM en één SSD (kracht 1). Een scan gaat door en houdt het datacenter bezig
     * (rekenkracht en RAM, te zien in de app Monitor); er past nog een satellietaanval bij, en daarna is het vol: een
     * volgend verzoek wordt geweigerd en kost niets. Een waypoint staat op de SSD, en een tweede past er niet meer bij als
     * de SSD vol is. Zijn de verzoeken klaar, dan kun je navigeren (dat neemt rekenkracht zolang het loopt), en valt het
     * datacenter uit, dan stopt de navigatie.
     */
    @GameTest(template = ARENA, timeoutTicks = 100, batch = "uplink")
    public static void glasses_use_the_cpu_ram_and_ssd_of_your_datacenter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        DatacenterGameTests.Setup setup = DatacenterGameTests.datacenter(helper);
        SecurityCoreBlockEntity core = setup.core();
        ServerPlayer owner = setup.owner();
        ItemStack glasses = ImtData.getWornGlasses(owner);
        GlobalPos here = GlobalPos.of(level.dimension(), core.getBlockPos());
        // De doelwit-analyse staat standaard aan; hier uit, zodat de getallen alleen van deze test komen.
        ImtData.toggle(glasses, ImtFeature.TARGET_ANALYSIS);
        double scan = DatacenterGlasses.Kind.SCAN.power();
        double strike = DatacenterGlasses.Kind.SATELLITE.power();
        AtomicBoolean busyChecked = new AtomicBoolean();

        helper.runAfterDelay(3, () -> {
            ImtTechGameTests.startServer(helper, setup.rack(), core);
            DatacenterGameTests.settle(level, core, 1);
            check(DatacenterGlasses.link(owner) == DatacenterGlasses.Link.OK, "Met een datacenter is de bril " + DatacenterGlasses.link(owner));
            double power = com.senne.imttech.logic.Datacenter.glassesPower(LogicServer.stats(level, core));
            check(scan + strike <= power + 1.0e-6 && scan + strike + DatacenterGlasses.Kind.ORDER.power() > power + 1.0e-6,
                    "Deze test rekent erop dat een scan en een satellietaanval samen één CPU vullen: kracht " + power
                            + ", scan " + scan + ", satelliet " + strike + " (config [uplink] en [logic])");

            // Een scan: hij gaat door, kost energie, en het datacenter is ermee bezig.
            double full = ImtData.getExactEnergy(glasses);
            ImtServer.handleAction(owner, ImtAction.ORE_SCAN.ordinal());
            check(same(full - ImtData.getExactEnergy(glasses), ImtConfig.Scanner.ORE_SCAN_COST), "De scan ging niet door");
            check(same(DatacenterGlasses.usedPower(server, here), scan),
                    "Na een scan gebruikt de bril " + DatacenterGlasses.usedPower(server, here) + " kracht in plaats van " + scan);
            check(same(DatacenterGlasses.usedRamMb(server, here), DatacenterGlasses.Kind.SCAN.ramMb()),
                    "Na een scan gebruikt de bril " + DatacenterGlasses.usedRamMb(server, here) + " MB RAM");
            check(DatacenterGlasses.loadPercent(owner) == (int) Math.round(scan / power * 100),
                    "De HUD zou " + Math.round(scan / power * 100) + "% zeggen, niet " + DatacenterGlasses.loadPercent(owner));

            // Er past nog een satellietaanval bij; daarna is het datacenter vol.
            check(DatacenterGlasses.ready(owner, DatacenterGlasses.Kind.SATELLITE), "Een satellietaanval paste er niet bij");
            DatacenterGlasses.started(owner, DatacenterGlasses.Kind.SATELLITE);
            check(!DatacenterGlasses.ready(owner, DatacenterGlasses.Kind.ORDER), "Het datacenter is vol, maar een opdracht mocht toch");
            check(!DatacenterGlasses.ready(owner, DatacenterGlasses.Kind.NAVIGATION), "Het datacenter is vol, maar navigeren mocht toch");
            ImtServer.handleAction(owner, ImtAction.TOGGLE_THREAT_SCAN.ordinal());
            check(!ImtData.isEnabled(glasses, ImtFeature.THREAT_SCAN), "De dreigingsscan ging aan op een vol datacenter");
            double before = ImtData.getExactEnergy(glasses);
            ImtServer.handleAction(owner, ImtAction.LAUNCH_DRONES.ordinal());
            check(same(ImtData.getExactEnergy(glasses), before) && ImtServer.isReady(owner, ImtCooldown.SWARM)
                            && ArsenalServer.get(owner).deployedCount() == 0,
                    "Een zwerm op een vol datacenter kostte energie, begon een wachttijd of vertrok");

            // In de app Monitor: I.M.T werkt op 100%, je ziet wat er loopt, en het RAM is in gebruik.
            core.datacenter().sample(level, core);
            CompoundTag monitor = LogicServer.snapshot(level, core);
            check(monitor.getInt("brainUse") == 100, "De tegel BRAIN zegt " + monitor.getInt("brainUse") + "% in plaats van 100%");
            check(monitor.getList("glassesProcs", Tag.TAG_STRING).size() == 2,
                    "In RUNNING staan " + monitor.getList("glassesProcs", Tag.TAG_STRING).size() + " regels van de bril in plaats van 2");
            double ram = DatacenterGlasses.Kind.SCAN.ramMb() + DatacenterGlasses.Kind.SATELLITE.ramMb();
            check(monitor.getDouble("glassesRamMb") >= ram - 1.0e-4 && monitor.getDouble("ramUsedMb") >= ram - 1.0e-4,
                    "De bril zou " + ram + " MB RAM gebruiken, de Monitor zegt " + monitor.getDouble("ramUsedMb"));

            // Bewaren: een waypoint staat op de SSD. Is die vol, dan kan er geen bij (het oude blijft).
            waypoint(owner, WaypointActionPayload.Op.ADD, 0, "Base");
            check(waypoints(owner).waypoints().size() == 1, "Met een datacenter kwam er geen waypoint bij");
            ImtServer.reportToDatacenter(owner);
            check(same(Datacenters.get(level).glassesData(owner.getUUID()), DatacenterGlasses.waypointsMb(1)),
                    "Het waypoint staat niet op de SSD: " + Datacenters.get(level).glassesData(owner.getUUID()) + " MB");
            check(same(LogicServer.snapshot(level, core).getDouble("glassesSsdMb"), DatacenterGlasses.waypointsMb(1)),
                    "De Monitor telt het waypoint niet mee op de SSD");
            float oldSize = ImtConfig.Uplink.WAYPOINT_MB;
            try {
                // Elk waypoint ruim de helft van de SSD: het eerste staat er, een tweede past er niet bij.
                ImtConfig.Uplink.WAYPOINT_MB = (float) (LogicStats.ssdMb() * 0.6);
                waypoint(owner, WaypointActionPayload.Op.ADD, 0, "Too much");
                check(waypoints(owner).waypoints().size() == 1, "Er kwam een waypoint bij op een volle SSD");
            } finally {
                ImtConfig.Uplink.WAYPOINT_MB = oldSize;
            }
            busyChecked.set(true);
        });

        // Zijn de scan en de satellietaanval klaar, dan is het datacenter weer vrij.
        int wait = 3 + BUSY_TICKS + 5;
        helper.runAfterDelay(wait, () -> {
            check(busyChecked.get(), "Het eerste deel van de test liep niet");
            check(same(DatacenterGlasses.usedPower(server, here), 0.0),
                    "Na afloop gebruikt de bril nog " + DatacenterGlasses.usedPower(server, here) + " kracht");
            // Navigeren: neemt rekenkracht en RAM zolang het loopt, plus een beetje RAM per waypoint.
            waypoint(owner, WaypointActionPayload.Op.NAVIGATE, 0, "");
            check(waypoints(owner).active() == 0, "Navigeren begon niet op een vrij datacenter");
            ImtServer.reportToDatacenter(owner);
            check(same(DatacenterGlasses.usedPower(server, here), DatacenterGlasses.Kind.NAVIGATION.power()),
                    "Navigeren gebruikt " + DatacenterGlasses.usedPower(server, here) + " kracht");
            check(same(DatacenterGlasses.usedRamMb(server, here),
                            DatacenterGlasses.Kind.NAVIGATION.ramMb() + ImtConfig.Uplink.WAYPOINT_RAM_MB),
                    "Navigeren met één waypoint gebruikt " + DatacenterGlasses.usedRamMb(server, here) + " MB RAM");
            // Nog eens aanzetten wat al loopt, telt niet dubbel.
            check(DatacenterGlasses.ready(owner, DatacenterGlasses.Kind.NAVIGATION), "Wat al loopt, telde dubbel");

            // Het datacenter valt uit: de navigatie stopt, en er kan niets meer.
            Datacenters.Summary summary = Datacenters.get(level).summary(here);
            check(summary != null, "Het datacenter staat niet in de lijst");
            Datacenters.get(level).report(here, new Datacenters.Summary(summary.owner(), summary.host(), summary.ip(), false,
                    summary.capacity(), summary.ownBlocks(), -1, 0.0, summary.ramMb()));
            check(DatacenterGlasses.link(owner) == DatacenterGlasses.Link.OFFLINE,
                    "Een datacenter dat niet draait: de bril is " + DatacenterGlasses.link(owner));
            ImtServer.reportToDatacenter(owner);
            check(waypoints(owner).active() == ImtWaypoints.NONE, "De navigatie liep door zonder datacenter");
            check(waypoints(owner).waypoints().size() == 1, "Het waypoint verdween toen het datacenter uitviel");
            check(same(DatacenterGlasses.usedPower(server, here), 0.0), "Een datacenter dat niet draait, rekent nog");
            helper.succeed();
        });
    }
}
