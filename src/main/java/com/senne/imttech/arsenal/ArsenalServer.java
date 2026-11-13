package com.senne.imttech.arsenal;

import com.senne.imttech.drone.ImtDrones;
import com.senne.imttech.drone.DroneEntity;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtServer;
import com.senne.imttech.imt.ImtAdvancements;
import com.senne.imttech.network.ArsenalActionPayload;
import com.senne.imttech.network.ImtMessagePayload;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Het arsenaal aan de serverkant: hier worden drones bijgeschreven, uitgeleend en afgeschreven.
 *
 * De regel is simpel: een drone die in de wereld vliegt, staat in het arsenaal als DEPLOYED.
 * Komt hij terug, dan schrijven we zijn schade weg en staat hij weer klaar. Gaat hij kapot,
 * dan is hij uit het arsenaal verdwenen en valt er een beetje schroot op de grond.
 */
public final class ArsenalServer {
    private ArsenalServer() {}

    public static ImtArsenal get(ServerPlayer player) {
        return player.getData(ModAttachments.ARSENAL);
    }

    private static void set(ServerPlayer player, ImtArsenal arsenal) {
        player.setData(ModAttachments.ARSENAL, arsenal);
    }

    // ---------------------------------------------------------------- Bijbouwen

    /**
     * Een nieuwe drone bijschrijven; geeft zijn nummer terug. Hij hangt daarna klaar: in zijn baan, of (hoofdstuk 106) bij
     * jou als je hem in een wereld zonder baan lanceert (de Nether of het End, zie {@link Orbit}). Zo neem je ook drones
     * mee: als item in je rugzak, en daar lanceren.
     */
    public static UUID add(ServerPlayer player) {
        UUID id = UUID.randomUUID();
        ArsenalDrone fresh = ArsenalDrone.fresh(id).withState(ArsenalDrone.home(orbitHere(player)));
        set(player, get(player).with(fresh));
        return id;
    }

    // ---------------------------------------------------------------- Is er hier een baan? (hoofdstuk 106)

    /** Is deze speler in een wereld met een baan boven zich? Niet in de Nether en het End (zie {@link Orbit}). */
    public static boolean orbitHere(ServerPlayer player) {
        return Orbit.reachable(player.level());
    }

    /**
     * Hoeveel drones deze speler HIER kan sturen: wat in zijn baan hangt, of in een wereld zonder baan wat met hem
     * meekwam en bij hem wacht.
     */
    public static int readyCount(ServerPlayer player) {
        return get(player).readyCount(orbitHere(player));
    }

    /**
     * Er is hier geen drone om te sturen: zeg waarom. In een wereld zonder baan is dat meestal omdat je drones in hun baan
     * er niet bij kunnen; anders is je arsenaal gewoon leeg (of alles is onderweg).
     */
    public static void tellNoneReady(ServerPlayer player) {
        ImtArsenal arsenal = get(player);
        if (!orbitHere(player) && arsenal.orbitCount() > 0) {
            message(player, Component.translatable("arsenal.imttech.msg.no_orbit_here", arsenal.orbitCount()),
                    ImtMessagePayload.Level.WARNING);
        } else {
            message(player, Component.translatable("arsenal.imttech.msg.empty"), ImtMessagePayload.Level.WARNING);
        }
    }

    // ---------------------------------------------------------------- Uitlenen en terugbrengen

    /**
     * Zoveel drones uit hun baan halen als er klaar hangen (maximaal "wanted").
     * Ze staan daarna als DEPLOYED geboekt; de beller zet ze in de wereld.
     *
     * Hoofdstuk 106: in een wereld zonder baan (de Nether, het End) komen ze uit wat bij je wacht ("With you"), niet
     * uit je baan: daar kunnen ze niet bij.
     */
    public static List<ArsenalDrone> checkOut(ServerPlayer player, int wanted) {
        if (wanted <= 0) {
            return List.of();
        }
        ImtArsenal arsenal = get(player);
        List<ArsenalDrone> picked = arsenal.pickReady(wanted, orbitHere(player));
        List<ArsenalDrone> out = new ArrayList<>(picked.size());
        for (ArsenalDrone drone : picked) {
            ArsenalDrone deployed = drone.withState(ArsenalDrone.State.DEPLOYED);
            arsenal = arsenal.replace(deployed);
            out.add(deployed);
        }
        set(player, arsenal);
        return out;
    }

    /** Eén drone uitlenen, of null als er geen klaar hangt. */
    public static @Nullable ArsenalDrone checkOutOne(ServerPlayer player) {
        List<ArsenalDrone> one = checkOut(player, 1);
        return one.isEmpty() ? null : one.getFirst();
    }

    /** Een drone die heelhuids terugkomt: schade bijwerken en weer in een baan zetten. */
    public static void returnToOrbit(ServerPlayer player, UUID id, float health) {
        ImtArsenal arsenal = get(player);
        ArsenalDrone drone = arsenal.find(id);
        if (drone == null) {
            return;
        }
        set(player, arsenal.replace(drone.withState(ArsenalDrone.State.ORBIT).withHealth(health)));
    }

    /**
     * Hoofdstuk 106: een drone is klaar met zijn rit in de wereld {@code where}: schade bijwerken, en terug naar waar hij
     * klaar hangt. Dat is zijn baan. Behalve als hij in een wereld zonder baan is (de Nether of het End) en jij daar ook
     * bent: dan kan hij niet omhoog en wacht hij bij jou ("With you"), klaar voor je volgende drone-actie daar. Ben jij
     * intussen ergens anders, dan vloog hij achter je aan door het portaal en hangt hij weer in zijn baan.
     */
    public static void returnHome(ServerPlayer player, UUID id, float health, Level where) {
        ImtArsenal arsenal = get(player);
        ArsenalDrone drone = arsenal.find(id);
        if (drone == null) {
            return;
        }
        boolean orbit = Orbit.reachable(where) || player.level() != where;
        set(player, arsenal.replace(drone.withState(ArsenalDrone.home(orbit)).withHealth(health)));
    }

    /** Boekt een drone als "onderweg", bijvoorbeeld zolang hij naar boven klimt. */
    public static void markDeployed(ServerPlayer player, UUID id) {
        ImtArsenal arsenal = get(player);
        ArsenalDrone drone = arsenal.find(id);
        if (drone != null && drone.state() != ArsenalDrone.State.DEPLOYED) {
            set(player, arsenal.replace(drone.withState(ArsenalDrone.State.DEPLOYED)));
        }
    }

    /** Schrijft de schade van een drone weg zonder verder iets te veranderen. */
    public static void setHealth(ServerPlayer player, UUID id, float health) {
        ImtArsenal arsenal = get(player);
        ArsenalDrone drone = arsenal.find(id);
        if (drone != null) {
            set(player, arsenal.replace(drone.withHealth(health)));
        }
    }

    // Een drone herstelt nergens vanzelf, ook niet in zijn baan (26 oktober, jouw wens: "drones mogen niet zelf
    // herstellen, dit kan enkel met repair van iron"). Repareren doe je met ijzer: roep hem terug en rechtsklik op hem
    // (zie DroneEntity.mobInteract).

    /**
     * Een drone is neergehaald: hij gaat uit het arsenaal en een klein deel van de
     * bouwkosten valt als schroot op de plek waar hij uit elkaar viel.
     */
    public static void destroyed(ServerPlayer player, UUID id, ServerLevel level, Vec3 where) {
        ImtArsenal arsenal = get(player);
        if (arsenal.find(id) == null) {
            return;
        }
        set(player, arsenal.without(id));
        for (ItemStack scrap : ArsenalCosts.wreckage(level.getRandom())) {
            ItemEntity item = new ItemEntity(level, where.x, where.y, where.z, scrap);
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, where.x, where.y, where.z, 12, 0.3, 0.3, 0.3, 0.02);
        message(player, Component.translatable("arsenal.imttech.msg.lost", arsenal.size() - 1),
                ImtMessagePayload.Level.ALERT);
        ImtAdvancements.award(player, ImtAdvancements.DRONE_DOWN);
    }

    // ---------------------------------------------------------------- Terugroepen, repareren, lanceren

    /**
     * Haalt een drone uit zijn baan en laat hem naast je landen.
     *
     * Hij komt echt uit de lucht vallen en blijft dan naast je staan wachten. Repareren doe je
     * met een rechtsklik op de drone zelf; shift-rechtsklik stuurt hem weer omhoog.
     */
    public static boolean recall(ServerPlayer player, UUID id) {
        ImtArsenal arsenal = get(player);
        ArsenalDrone drone = arsenal.find(id);
        if (drone == null) {
            return false;
        }
        // Hoofdstuk 106: in een wereld zonder baan kun je alleen een drone laten landen die bij je wacht.
        boolean orbit = orbitHere(player);
        if (drone.state() != ArsenalDrone.home(orbit)) {
            if (!orbit && drone.state() == ArsenalDrone.State.ORBIT) {
                message(player, Component.translatable("arsenal.imttech.msg.out_of_reach"), ImtMessagePayload.Level.WARNING);
            }
            return false;
        }
        ArsenalDrone grounded = drone.withState(ArsenalDrone.State.GROUNDED);
        set(player, arsenal.replace(grounded));
        if (ImtDrones.landRecalled(player, grounded) == null) {
            // Er kon geen drone neergezet worden: dan blijft hij gewoon in zijn baan hangen.
            set(player, get(player).replace(drone));
            return false;
        }
        message(player, Component.translatable("arsenal.imttech.msg.coming_down"),
                ImtMessagePayload.Level.INFO);
        return true;
    }

    /**
     * Stuurt een drone die aan de grond staat weer de ruimte in, vanuit het arsenaal-scherm.
     * De drone die naast je staat stijgt dan echt op.
     */
    public static boolean launch(ServerPlayer player, UUID id) {
        ArsenalDrone drone = get(player).find(id);
        if (drone == null || drone.state() != ArsenalDrone.State.GROUNDED) {
            return false;
        }
        DroneEntity landed = ImtDrones.findGrounded(player, id);
        if (landed != null) {
            landed.departToOrbit(player.serverLevel());
            return true;
        }
        // Zijn drone is er niet meer (te ver weg gelopen, wereld opnieuw geladen): dan zetten
        // we hem gewoon administratief terug in zijn baan. (Altijd zijn baan, ook als jij in de Nether of het End
        // bent: anders kon je drones in de gewone wereld laten landen en ze van daaruit "bij je" laten komen.)
        set(player, get(player).replace(drone.withState(ArsenalDrone.State.ORBIT)));
        return true;
    }

    /** Wat het arsenaal-scherm vraagt: terugroepen, repareren of weer lanceren. */
    public static void handleAction(ServerPlayer player, ArsenalActionPayload payload) {
        // Zonder bril geen arsenaal: I.M.T houdt je drones in hun baan.
        if (ImtServer.authorizedGlasses(player) == null) {
            return;
        }
        switch (payload.op()) {
            case RECALL -> recall(player, payload.drone());
            case LAUNCH -> launch(player, payload.drone());
        }
    }

    // ---------------------------------------------------------------- Wie is er echt nog onderweg?

    /**
     * De drones die nu in de wereld een opdracht doen, per drone uit een arsenaal. Een drone meldt zich hier elke tick
     * (DroneEntity.tick) zolang hij in je arsenaal op "On a job" staat. Zo zien we een drone die stilvalt of verdwijnt
     * zonder zich terug te melden.
     */
    private static final Map<UUID, DroneEntity> IN_THE_WORLD = new HashMap<>();

    /** Zo lang (in ticks) mag een drone op een opdracht niets doen voor we zeggen dat hij stilstaat: 2 seconden. */
    private static final int STANDING_STILL_TICKS = 40;

    /** Deze drone is bezig met een opdracht (zie DroneEntity.tick). */
    public static void seen(DroneEntity drone) {
        if (drone.getArsenalId() != null) {
            IN_THE_WORLD.put(drone.getArsenalId(), drone);
        }
    }

    /**
     * Hoeveel drones van deze speler nu in zijn wereld met dit soort werk bezig zijn. Anders dan ImtDrones.find kijkt dit
     * niet in de wereld zelf, maar in onze eigen lijst: een drone die net met je mee door een portaal kwam (hoofdstuk
     * 106), laat Minecraft soms pas een tikje later "zien", en dan zou je escorte er anders eentje te veel bijroepen.
     */
    public static int flying(ServerPlayer owner, DroneEntity.Mode mode) {
        int count = 0;
        for (DroneEntity drone : IN_THE_WORLD.values()) {
            if (!drone.isRemoved() && drone.level() == owner.level() && drone.getMode() == mode
                    && owner.getUUID().equals(drone.getOwnerId())) {
                count++;
            }
        }
        return count;
    }

    /** Deze drone heeft zijn rit afgeboekt (of is een oude kopie): niet meer in de gaten houden. */
    public static void forget(DroneEntity drone) {
        if (drone.getArsenalId() != null) {
            IN_THE_WORLD.remove(drone.getArsenalId(), drone);
        }
    }

    /**
     * Elke seconde: klopt "On a job" nog met wat de drones echt doen?
     *
     * Normaal meldt elke drone zich zelf terug als hij klaar is. Dit is het vangnet voor wat daar tussendoor glipt:
     * een drone die al 2 seconden niets meer doet, staat stil in een stuk wereld waar Minecraft niets laat bewegen (of
     * kwam nooit echt in de wereld). Die maakt zijn rit af en gaat terug in zijn baan (DroneEntity.abandon). En een
     * drone die al uit de wereld is zonder zich terug te melden, wordt alsnog teruggeboekt.
     *
     * Vroeger bleven zulke drones in je arsenaal op "On a job" staan, ook als ze allang niets meer deden.
     */
    public static void onServerTick(ServerTickEvent.Post event) {
        if (IN_THE_WORLD.isEmpty() || event.getServer().getTickCount() % 20 != 0) {
            return;
        }
        for (DroneEntity drone : List.copyOf(IN_THE_WORLD.values())) {
            if (!(drone.level() instanceof ServerLevel level) || !drone.isOnAJob()) {
                forget(drone);
            } else if (drone.isRemoved()) {
                drone.handBack(level);
                forget(drone);
            } else if (level.getGameTime() - drone.lastActiveTick() > STANDING_STILL_TICKS) {
                drone.abandon(level);
                forget(drone);
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        IN_THE_WORLD.clear();
        LAST_PLACE.clear();
    }

    // ---------------------------------------------------------------- Door een portaal (hoofdstuk 106)

    /** Waar een speler de vorige tick was: in welke wereld, en op welke plek. */
    private record Place(ResourceKey<Level> dimension, Vec3 pos) {}

    private static final Map<UUID, Place> LAST_PLACE = new HashMap<>();

    /** Zo dicht (blokken) moet een drone bij je vliegen om mee te gaan door een portaal: zo ver vliegt hij van je weg. */
    private static final double WITH_YOU = 48.0;

    /**
     * Elke tick, voor elke speler met drones: is hij in een andere wereld dan de vorige tick (een portaal, een commando,
     * opnieuw beginnen na zijn dood)? Dan gaan zijn drones mee (zie {@link #changedWorld}).
     *
     * We kijken zelf elke tick in plaats van naar het portaal-bericht van NeoForge te luisteren: zo weten we ook waar je
     * net stond (daar vlogen je drones), en missen we geen enkele manier om van wereld te wisselen.
     */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            follow(player);
        }
    }

    /**
     * Eén tick van het volgen (zie onPlayerTick). Apart, zodat de automatische tests het zelf kunnen aanroepen: een
     * testspeler tikt niet vanzelf.
     */
    public static void follow(ServerPlayer player) {
        if (get(player).drones().isEmpty()) {
            LAST_PLACE.remove(player.getUUID());
            return;
        }
        Place now = new Place(player.level().dimension(), player.position());
        Place before = LAST_PLACE.put(player.getUUID(), now);
        if (before != null && before.dimension() != now.dimension()) {
            changedWorld(player, before);
        }
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_PLACE.remove(event.getEntity().getUUID());
    }

    /**
     * Deze speler is net in een andere wereld gekomen. Jouw wens: "drones kun je niet in nether of end gebruiken tenzij
     * je met een bepaalde drone, vb escort drones, naar daar gaat; dan blijft het aantal drones dat je meenam voor elke
     * andere drone actie beschikbaar".
     *
     * <ul>
     *   <li>De drones die om je heen vlogen (je escorte en je zwerm, binnen 48 blokken van waar je stond) gaan mee door
     *       het portaal en doen in de nieuwe wereld gewoon verder (zie DroneEntity.travelWith).</li>
     *   <li>Kom je in een wereld met een baan, dan hangt wat bij je wachtte ("With you") weer in zijn baan.</li>
     *   <li>Kom je in een wereld zonder baan, dan blijft wat bij je wachtte bij je, en zeggen we hoeveel drones je daar
     *       hebt.</li>
     * </ul>
     * Wat ergens anders mee bezig was (een vrachtdrone, een spion, een illusie), blijft achter, maakt zijn werk af of
     * merkt dat je weg bent, en vliegt dan naar zijn baan (zie returnHome).
     */
    private static void changedWorld(ServerPlayer player, Place before) {
        int travelled = 0;
        ServerLevel old = player.getServer() == null ? null : player.getServer().getLevel(before.dimension());
        if (old != null && player.isAlive() && !player.isSpectator()) {
            for (DroneEntity drone : List.copyOf(IN_THE_WORLD.values())) {
                if (drone.level() == old && player.getUUID().equals(drone.getOwnerId()) && drone.fliesWithOwner()
                        && drone.position().distanceToSqr(before.pos()) <= WITH_YOU * WITH_YOU
                        && drone.travelWith(player) != null) {
                    travelled++;
                }
            }
        }
        ImtArsenal arsenal = get(player);
        // Meldingen alleen voor wie zijn bril op heeft (en die werkt): zonder bril weet je niets van je arsenaal.
        boolean tell = ImtData.isOperational(player);
        if (orbitHere(player)) {
            int waiting = arsenal.readyCount(false);
            ImtArsenal home = arsenal.standbyToOrbit();
            if (home != arsenal) {
                set(player, home);
                if (tell && waiting > 0) {
                    message(player, Component.translatable("arsenal.imttech.msg.back_in_orbit", waiting),
                            ImtMessagePayload.Level.INFO);
                }
            }
        } else if (tell) {
            // Zonder baan: zeg één keer wat je hier hebt (wat meekwam, en wat hier al bij je wachtte).
            int here = travelled + arsenal.readyCount(false);
            message(player, here > 0
                            ? Component.translatable("arsenal.imttech.msg.no_orbit_arrived", here)
                            : Component.translatable("arsenal.imttech.msg.no_orbit_alone"),
                    here > 0 ? ImtMessagePayload.Level.INFO : ImtMessagePayload.Level.WARNING);
        }
    }

    // ---------------------------------------------------------------- Bij het inloggen

    /**
     * Je drones verdwijnen uit de wereld zodra jij weg bent. Wat nog als "bezig" geboekt
     * stond, hangt dus gewoon weer in een baan. (En een oude "in de hangar"-vlag gaat weg: drones
     * staan sinds hoofdstuk 25 niet meer op een dock.)
     *
     * Hing er toch nog een drone van jou ergens in de wereld (hij viel stil terwijl je weg was), dan is die vanaf nu een
     * oude kopie: komt hij weer in beweging, dan vliegt hij weg zonder iets te boeken (zie DroneEntity.stale).
     */
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ImtArsenal arsenal = get(player);
        // Hoofdstuk 106: log je in in een wereld zonder baan (de Nether, het End), dan was wat onderweg stond daar met je
        // mee: het wacht nu bij jou. In een wereld met een baan hangt alles weer in zijn baan, ook wat bij je wachtte.
        ImtArsenal cleaned = arsenal.groundDeployed(orbitHere(player)).undocked();
        if (cleaned != arsenal) {
            set(player, cleaned);
        }
        for (DroneEntity drone : List.copyOf(IN_THE_WORLD.values())) {
            if (player.getUUID().equals(drone.getOwnerId())) {
                drone.markStale();
            }
        }
    }

    private static void message(ServerPlayer player, Component text, ImtMessagePayload.Level level) {
        ImtServer.send(player, text, level);
    }

}
