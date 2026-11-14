package com.senne.imttech.client.dev;

import static com.senne.imttech.client.dev.ShowcaseScene.BLACK_BOX;
import static com.senne.imttech.client.dev.ShowcaseScene.CHARGING_STATION;
import static com.senne.imttech.client.dev.ShowcaseScene.CRASH_SITE;
import static com.senne.imttech.client.dev.ShowcaseScene.CHEST;
import static com.senne.imttech.client.dev.ShowcaseScene.COW;
import static com.senne.imttech.client.dev.ShowcaseScene.DOME;
import static com.senne.imttech.client.dev.ShowcaseScene.FAR_TARGET;
import static com.senne.imttech.client.dev.ShowcaseScene.FRIEND_SPOT;
import static com.senne.imttech.client.dev.ShowcaseScene.GRID_CUT;
import static com.senne.imttech.client.dev.ShowcaseScene.HEAT_GENERATOR;
import static com.senne.imttech.client.dev.ShowcaseScene.BIEBIA_BUNKER;
import static com.senne.imttech.client.dev.ShowcaseScene.HOLO_TABLE;
import static com.senne.imttech.client.dev.ShowcaseScene.HOME;
import static com.senne.imttech.client.dev.ShowcaseScene.HUT;
import static com.senne.imttech.client.dev.ShowcaseScene.LAMP;
import static com.senne.imttech.client.dev.ShowcaseScene.LASER_A;
import static com.senne.imttech.client.dev.ShowcaseScene.LASER_POWER;
import static com.senne.imttech.client.dev.ShowcaseScene.PYRAMID;
import static com.senne.imttech.client.dev.ShowcaseScene.SOLAR_PANEL;
import static com.senne.imttech.client.dev.ShowcaseScene.BIEBIA_TOWER;
import static com.senne.imttech.client.dev.ShowcaseScene.TARGETS;

import com.mojang.blaze3d.platform.InputConstants;
import com.senne.imttech.ImtTech;
import com.senne.imttech.arsenal.ArsenalDrone;
import com.senne.imttech.arsenal.ArsenalServer;
import com.senne.imttech.client.ArsenalScreen;
import com.senne.imttech.client.CommsScreen;
import com.senne.imttech.client.DomeScreen;
import com.senne.imttech.client.DroneCamera;
import com.senne.imttech.client.ImtClientEvents;
import com.senne.imttech.client.ImtKeys;
import com.senne.imttech.client.ImtMenuScreen;
import com.senne.imttech.client.MusicScreen;
import com.senne.imttech.client.NavigationScreen;
import com.senne.imttech.client.RelayEditScreen;
import com.senne.imttech.client.RelayListScreen;
import com.senne.imttech.client.LaserCannonControl;
import com.senne.imttech.client.SatelliteScreen;
import com.senne.imttech.client.SuitFlight;
import com.senne.imttech.client.WaypointEditScreen;
import com.senne.imttech.client.voice.VoiceCommandsScreen;
import com.senne.imttech.comms.ImtComms;
import com.senne.imttech.drone.DroneEntity;
import com.senne.imttech.imt.ImtAction;
import com.senne.imttech.imt.ImtData;
import com.senne.imttech.imt.ImtOwner;
import com.senne.imttech.raven.RavenSoldierEntity;
import com.senne.imttech.holo.HoloMode;
import com.senne.imttech.network.CommsPayload;
import com.senne.imttech.network.HoloActionPayload;
import com.senne.imttech.network.IllusionPlacePayload;
import com.senne.imttech.network.LaserActionPayload;
import com.senne.imttech.network.RelayActionPayload;
import com.senne.imttech.network.SatelliteUpgradePayload;
import com.senne.imttech.network.WaypointActionPayload;
import com.senne.imttech.raid.ImtRaids;
import com.senne.imttech.raid.RaidDroneEntity;
import com.senne.imttech.redstone.ImtRedstone;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModCreativeTabs;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.registry.ModSounds;
import com.senne.imttech.satellite.SatelliteUpgrade;
import com.senne.imttech.satellite.SatelliteUpgrades;
import com.senne.imttech.shield.ShieldColor;
import com.senne.imttech.station.ChargingStationBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.Nullable;

/**
 * Alle functies van de mod, in de volgorde waarin de showcase ze laat zien.
 *
 * Elke functie heeft:
 * - een titel (Engels, want die verschijnt in het spel),
 * - een uitleg voor in je terminal: waar je op moet letten (Nederlands, zie je niet in het spel),
 * - een spelmodus en een plek waar je begint,
 * - een draaiboek: wat er gebeurt. Waar het kan via precies dezelfde weg als jouw toetsen
 *   (ImtAction), zodat je de echte functie ziet en geen nagebootste.
 *
 * Een nieuwe functie gebouwd? Zet hem hier in de lijst, dan zit hij in de showcase.
 */
final class ShowcaseFeatures {
    private ShowcaseFeatures() {}

    /**
     * Eén functie in de showcase.
     *
     * @param title  de naam (Engels, in beeld in het spel)
     * @param watch  waar je op let (Nederlands, alleen in je terminal)
     * @param mode   creative of survival (vliegen, dreigingen en raids werken alleen echt in survival)
     * @param from   waar je begint
     * @param yaw    welke kant je op kijkt (0 = zuid, 90 = west, 180 = noord, -90 = oost)
     * @param pitch  omhoog (-) of omlaag (+)
     * @param demo   het draaiboek
     */
    record Feature(String title, String watch, GameType mode, Vec3 from, float yaw, float pitch,
                   Consumer<DevScript> demo) {}

    private static final List<Feature> ALL = new ArrayList<>();

    static List<Feature> all() {
        if (ALL.isEmpty()) {
            build();
        }
        return ALL;
    }

    private static void add(String title, String watch, GameType mode, Vec3 from, float yaw, float pitch,
                            Consumer<DevScript> demo) {
        ALL.add(new Feature(title, watch, mode, from, yaw, pitch, demo));
    }

    private static void add(String title, String watch, Vec3 from, float yaw, float pitch, Consumer<DevScript> demo) {
        add(title, watch, GameType.CREATIVE, from, yaw, pitch, demo);
    }

    // ================================================================ De lijst

    private static void build() {
        Vec3 start = at(ShowcaseScene.START);
        Vec3 range = new Vec3(0.5, -60, -8.5);

        // ---------------------------------------------------------------- De bril
        add("I.M.T glasses", "De opstart van de bril (ruim 5 seconden): groot I.M.T-logo in "
                + "reactorringen, een laadbalk met wat er geladen wordt (je echte drones en batterij), dan "
                + "scant hij je gezicht terwijl de balk op 90% wacht, IDENTITY CONFIRMED met je naam, en "
                + "I.M.T begroet je (met stem als die geïnstalleerd is, anders in beeld). Geluid: 'arc "
                + "reactor'. Daarna de HUD: energie links boven, kompas boven, meldingen in het midden.",
                start, 180, 0, s -> s
                .onServer(0, player -> player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY))
                .pause(40)
                .onServer(0, DevWorld::wearGlasses)
                .pause(200));

        add("I.M.T sounds", "Alle eigen geluiden van I.M.T, één voor één, met hun naam in beeld: "
                + "meldingen (info zoals 'aangekomen', waarschuwing, geweigerd), scan, menu, laadstation, "
                + "drones, satelliet, comms, laser cannon en het opstarten. Zeg bij een nummer welke je anders wilt.",
                start, 180, 0, ShowcaseFeatures::soundTour);

        add("Owner lock", "Een bril van iemand anders (Seppe): 'Access denied'. Alleen de eigenaar "
                + "kan hem gebruiken. Daarna weer je eigen bril.", start, 180, 0, s -> s
                .onServer(0, player -> {
                    ServerPlayer friend = ShowcaseScene.friend(player);
                    if (friend != null) {
                        player.setItemSlot(EquipmentSlot.HEAD, DevWorld.ownGlasses(friend));
                    }
                })
                .pause(140)
                .onServer(0, DevWorld::wearGlasses)
                .pause(120));

        add("Transfer glasses", "Een bril doorgeven. Eerst geef jij je eigen bril vrij (G > Systems > "
                + "'Transfer ownership'): I.M.T meldt dat de volgende die hem opzet de nieuwe eigenaar "
                + "wordt; nog eens drukken = annuleren. Daarna heeft Seppe zíjn bril vrijgegeven en zet jij "
                + "hem op: geen 'Access denied' zoals bij 'Owner lock', maar de opstart met jouw naam, want "
                + "nu is hij van jou.", start, 180, 0, s -> s
                .action(10, ImtAction.AUTHORIZE_TRANSFER)
                .pause(90)
                .action(0, ImtAction.AUTHORIZE_TRANSFER)
                .pause(70)
                .onServer(0, ShowcaseFeatures::wearFriendsReleasedGlasses)
                .pause(240));

        add("Target analysis", "Kijk naar een husk: rechts verschijnt de doelanalyse "
                + "(leven, pantser, afstand, dreigingsniveau) en hoekjes rond het doelwit.", range, 180, 0, s -> s
                .lookAt(10, mob(TARGETS.get(1)))
                .pause(80)
                .lookAt(0, mob(TARGETS.get(0)))
                .pause(60));

        add("Mark targets (R)", "Kijk naar een vijand en druk R: hij krijgt een markering. Drones en de "
                + "satelliet gaan straks achter gemarkeerde doelwitten aan.", range, 180, 0, s -> {
            markAll(s);
            s.lookAt(0, mob(TARGETS.get(1))).pause(80);
        });

        add("Threat scan (H)", "Twee husks in een dichte bunker willen naar je toe. Met de dreigingsscan "
                + "zie je ze door de muur heen, met hun afstand.", GameType.SURVIVAL,
                new Vec3(-23.5, -60, -9.5), 180, 0, s -> s
                .onServer(10, ShowcaseScene::aggroBunker)
                .action(10, ImtAction.TOGGLE_THREAT_SCAN)
                .onServer(40, ShowcaseScene::aggroBunker)
                .onServer(40, ShowcaseScene::aggroBunker)
                .onServer(40, ShowcaseScene::aggroBunker)
                .pause(40)
                .action(0, ImtAction.TOGGLE_THREAT_SCAN)
                .pause(20));

        add("Night vision (N)", "Het wordt nacht. Eerst zonder, dan met nachtzicht van de bril.",
                start, -90, 0, s -> s
                .command(0, "time set midnight")
                .lookAt(5, Vec3.atCenterOf(PYRAMID))
                .pause(70)
                .action(0, ImtAction.TOGGLE_NIGHT_VISION)
                .pause(100)
                .action(0, ImtAction.TOGGLE_NIGHT_VISION)
                .command(10, "time set noon"));

        add("Zoom (V)", "Inzoomen op de Biebia's Industries Tower in de verte, met een afstandsmeter naast het vizier.",
                start, -90, 0, s -> s
                .lookAt(5, towerCentre().add(0, 10, 0))
                .pause(30)
                .key(0, () -> ImtKeys.ZOOM, true)
                .pause(110)
                .key(0, () -> ImtKeys.ZOOM, false)
                .pause(20));

        add("Ore scanner (B)", "Een scan door de grond: ertsen onder je voeten worden gemarkeerd, "
                + "met hun soort en kleur.", start, 180, 55, s -> s
                .action(10, ImtAction.ORE_SCAN)
                .pause(160));

        add("I.M.T menu (G)", "Het ronde menu met alle functies, tabblad voor tabblad: Combat "
                + "(laser cannon, raketten, rayshield), Satellite & Arsenal (de drie satellietaanvallen apart, "
                + "het satellietscherm en het arsenaal), Drones, Recon drones (camera en spion), Transport, "
                + "Tech, Illusion, Information (ertsen, dreigingen, doelanalyse), Navigation en Systems "
                + "(nu met nachtzicht). Bij een stand die je nog niet vrijspeelde staat LOCKED.", start, 180, 0, s -> {
            s.open(10, ImtMenuScreen::new);
            for (int i = 0; i < com.senne.imttech.client.MenuTab.values().length; i++) {
                s.step(45, () -> press(InputConstants.KEY_RIGHT));
            }
            s.close(45);
        });

        // ---------------------------------------------------------------- Drones
        add("Drone swarm (J)", "Markeer de husks en stuur de zwerm: de drones duiken van heel hoog "
                + "uit hun baan naar beneden en vallen de doelwitten aan.", range, 180, 0, s -> {
            markAll(s);
            s.action(5, ImtAction.LAUNCH_DRONES)
                    .look(20, 180, -45)
                    .pause(100)
                    .lookAt(0, mob(TARGETS.get(1)))
                    .pause(220);
        });

        add("Single drone (L)", "Eén drone op één doelwit: goedkoper dan een zwerm, en hij jaagt "
                + "tot het doelwit dood is.", range, 180, 0, s -> s
                .lookAt(10, mob(TARGETS.get(0)))
                .action(5, ImtAction.DRONE_STRIKE)
                .pause(300));

        add("Escort drones", "Drones die om je heen vliegen en je beschermen, tot je ze uitzet.",
                start, 180, -10, s -> s
                .action(10, ImtAction.TOGGLE_GUARD_DRONES)
                .pause(140)
                .look(0, 90, -15)
                .pause(80)
                .look(0, 0, -15)
                .pause(80)
                .action(0, ImtAction.TOGGLE_GUARD_DRONES)
                .pause(60));

        add("Drone formations", "Je escorte in een vorm (G > Drones > Formation), van buitenaf bekeken. Eerst "
                + "Orbit (rondcirkelen, zoals altijd), dan Shield (een snel draaiende ring dicht om je heen: wat "
                + "op je af komt, raakt eerst een drone), Arrow (een V schuin boven en achter je; kijk hoe hij "
                + "meedraait als je je omdraait) en Circle (een wijde, trage ring). In Shield en Arrow blijven ze "
                + "in formatie als ze schieten.", start, 180, 15, s -> s
                .action(10, ImtAction.TOGGLE_GUARD_DRONES)
                .camera(0, CameraType.THIRD_PERSON_BACK)
                .pause(110)
                .action(0, ImtAction.DRONE_FORMATION)
                .pause(90)
                .action(0, ImtAction.DRONE_FORMATION)
                .pause(50)
                .look(0, 90, 15)
                .pause(50)
                .look(0, 180, 15)
                .pause(40)
                .action(0, ImtAction.DRONE_FORMATION)
                .pause(100)
                .action(0, ImtAction.DRONE_FORMATION)
                .camera(0, CameraType.FIRST_PERSON)
                .action(10, ImtAction.TOGGLE_GUARD_DRONES)
                .pause(20));

        add("Damaged drones", "Schade zie je aan de drone zelf: van links naar rechts heel, 70%, 45% en 15% "
                + "leven, en een raiddrone. Krassen en roet, een kromme rotor, een ontbrekende rotor, rook, "
                + "vonken, brandende brokjes en haperende lampjes.", new Vec3(4.5, -60, 1.5), 180, 5, s -> s
                .onServer(0, player -> DevWorld.damagedDroneRow(player.serverLevel(), new BlockPos(0, -59, -4), 0))
                .pause(120)
                .view(0, 4.5, -59.4, -1.8, 180, 10)
                .pause(140)
                .onServer(0, player -> player.serverLevel().getEntities((net.minecraft.world.entity.Entity) null,
                        new net.minecraft.world.phys.AABB(0, -62, -6, 12, -56, -2), e -> e instanceof net.minecraft.world.entity.Mob)
                        .forEach(net.minecraft.world.entity.Entity::discard)));

        add("Stand down (,)", "Een zwerm valt aan, en 'stand down' roept alles terug: de drones "
                + "vliegen weg en de markeringen verdwijnen.", range, 180, 0, s -> {
            markAll(s);
            s.action(5, ImtAction.LAUNCH_DRONES)
                    .pause(220)
                    .action(0, ImtAction.STAND_DOWN)
                    .look(10, 180, -40)
                    .pause(140);
        });

        add("Satellite: precision (K)", "De satelliet: eerst een rode richtstraal en een ring op de "
                + "grond, dan valt er een echte raket uit de ruimte (je hoort hem aankomen) die precies op nul "
                + "inslaat. Geen standen meer: K vuurt meteen. Richt op de verre husk.", range, 180, 0, s -> s
                .onServer(0, ShowcaseFeatures::unlockSatelliteModes)
                .lookAt(10, mob(FAR_TARGET))
                .action(10, ImtAction.STRIKE_PRECISION)
                .pause(220));

        add("Satellite: bombardment (P)", "Een eigen toets, P: een regen van raketten rond het doel, "
                + "elk met een krater.", range, 180, 0, s -> s
                .onServer(0, ShowcaseFeatures::unlockSatelliteModes)
                .lookAt(10, mob(TARGETS.get(1)))
                .action(10, ImtAction.STRIKE_BOMBARDMENT)
                .pause(260));

        add("Satellite: sweep (C)", "De schuivende laser op C, met een zelfgekozen begin en eind: kijk naar de "
                + "linker husk en druk C (het beginpunt krijgt een markering), kijk naar de rechter husk en druk "
                + "nog eens C. Na het opladen trekt de laser van links naar rechts over alle drie.",
                range, 180, 0, s -> s
                .onServer(0, ShowcaseFeatures::unlockSatelliteModes)
                .lookAt(10, mob(TARGETS.get(0)))
                .action(10, ImtAction.STRIKE_SWEEP)
                .pause(30)
                .lookAt(20, mob(TARGETS.get(2)))
                .action(10, ImtAction.STRIKE_SWEEP)
                .lookAt(10, mob(TARGETS.get(1)))
                .pause(240));

        add("Satellite upgrades", "Het satellietscherm (G > Satellite & Arsenal > Satellite): links de satelliet met al "
                + "zijn getallen, rechts de upgrades met hun kosten. Kijk hoe de satelliet groeit: niveau 1, 4, 7 "
                + "en tot slot de creative-knop 'Max all' (langere panelen, raketkokers, het kanon, een grotere "
                + "schotel, condensatoren, een grote reactor).", start, 180, 0, s -> s
                .onServer(0, player -> satelliteAt(player, 1))
                .open(10, SatelliteScreen::new)
                .pause(100)
                .onServer(0, player -> satelliteAt(player, 4))
                .pause(80)
                .onServer(0, player -> satelliteAt(player, 7))
                .pause(80)
                .step(0, () -> PacketDistributor.sendToServer(SatelliteUpgradePayload.everything()))
                .pause(140)
                .close(0));

        add("Drone camera (Y)", "Je kijkt door een verkenningsdrone en bestuurt hem zelf "
                + "(vooruit, omhoog). Daarna kijk je weer door je eigen ogen.", start, 180, 0, s -> s
                .action(10, ImtAction.DRONE_CAMERA)
                .pause(120)
                .key(0, key(() -> Minecraft.getInstance().options.keyUp), true)
                .key(60, () -> Minecraft.getInstance().options.keyJump, true)
                .key(30, () -> Minecraft.getInstance().options.keyJump, false)
                .key(40, () -> Minecraft.getInstance().options.keyUp, false)
                .pause(40)
                .action(0, ImtAction.DRONE_CAMERA)
                .pause(60));

        add("Spy drone (.)", "Een spionagedrone volgt Seppe in het geheim. Links zie je waar hij is; "
                + "daarna kijk je even live mee en krijg je het rapport.", new Vec3(0.5, -60, -2.5), 180, 0, s -> s
                .lookAt(10, eyes(FRIEND_SPOT))
                .action(10, ImtAction.SPY_DRONE)
                .pause(220)
                .action(0, ImtAction.SPY_VIEW)
                .pause(120)
                .action(0, ImtAction.SPY_VIEW)
                .action(10, ImtAction.SPY_REPORT)
                .pause(140)
                .close(0)
                .action(10, ImtAction.SPY_DRONE)
                .pause(40));

        add("Hack a player (;)", "Hack Seppe: zijn privéberichten (en de antwoorden) verschijnen "
                + "boven zijn hoofd, zolang jij naar hem kijkt.", new Vec3(2.5, -60, -3.5), 180, 0, s -> s
                .lookAt(10, eyes(FRIEND_SPOT))
                .action(10, ImtAction.HACK_PLAYER)
                .onServer(40, player -> friendSays(player, "msg " + player.getGameProfile().getName()
                        + " Did you see the tower?"))
                .onServer(60, player -> player.getServer().getCommands().performPrefixedCommand(
                        player.createCommandSourceStack(), "msg " + ShowcaseScene.FRIEND + " On my way."))
                .onServer(60, player -> friendSays(player, "msg " + player.getGameProfile().getName()
                        + " Bring diamonds."))
                .pause(120)
                .action(0, ImtAction.HACK_STOP)
                .pause(20));

        add("Comms and pings (O, U)", "Het comms-scherm, dan een I.M.T-bericht van Seppe, en "
                + "een ping op de grond die iedereen met een bril ziet.", start, 180, 0, s -> s
                .open(10, CommsScreen::new)
                .close(70)
                .onServer(10, player -> {
                    ServerPlayer friend = ShowcaseScene.friend(player);
                    if (friend != null) {
                        ImtComms.handleMessage(friend, new CommsPayload(player.getGameProfile().getName(),
                                "Meet me at the tower."));
                    }
                })
                .look(60, 180, 35)
                .step(10, ImtClientEvents::placePing)
                .look(20, 180, 10)
                .pause(100));

        add("Navigation (M)", "Het navigatiescherm met je waypoints, dan navigeren naar 'Home': "
                + "een marker in beeld en op het kompas, met de afstand.", start, 180, 0, s -> s
                .open(10, NavigationScreen::new)
                .close(80)
                .step(10, () -> navigate(0))
                .lookAt(10, Vec3.atCenterOf(HOME))
                .pause(120));

        add("Edit a waypoint", "Het potloodje naast een waypoint in het navigatiescherm: een andere naam "
                + "of kleur geven, of hem weggooien. De plek zelf blijft waar je hem opsloeg.", start, 180, 0, s -> s
                .open(10, NavigationScreen::new)
                .open(50, () -> new WaypointEditScreen(0))
                .pause(130)
                .close(0));

        // ---------------------------------------------------------------- Transport
        add("Fly with drones", "Drones pikken je op en je stuurt zelf: je vliegt waar je kijkt. "
                + "Daarna land je weer.", GameType.SURVIVAL, start, 180, 10, s -> s
                .action(10, ImtAction.TRANSPORT_RIDE)
                .pause(180)
                .key(0, () -> Minecraft.getInstance().options.keyUp, true)
                .look(40, -45, 5)
                .key(60, () -> Minecraft.getInstance().options.keyUp, false)
                .action(20, ImtAction.TRANSPORT_RIDE)
                .pause(140));

        add("Autopilot", "Navigeer naar 'Home' en laat de drones je er zelf heen brengen.",
                GameType.SURVIVAL, start, -45, 0, s -> s
                .step(5, () -> navigate(0))
                .action(10, ImtAction.TRANSPORT_AUTOPILOT)
                .pause(500));

        add("Cargo drone", "Kijk naar de kist: een vrachtdrone haalt de spullen op en brengt ze naar "
                + "de kist bij 'Home'.", start, 180, 0, s -> s
                .step(5, () -> navigate(0))
                .lookAt(10, Vec3.atCenterOf(CHEST))
                .action(10, ImtAction.CARGO_DRONE)
                .pause(120)
                .lookAt(0, Vec3.atCenterOf(HOME))
                .pause(260));

        add("Carry a creature", "Kijk naar de koe: drones pakken haar op en zetten haar bij 'Home' neer.",
                start, 180, 0, s -> s
                .step(5, () -> navigate(0))
                .lookAt(10, mob(COW))
                .action(10, ImtAction.CARRY_ENTITY)
                .pause(140)
                .lookAt(0, Vec3.atCenterOf(HOME))
                .pause(280));

        // ---------------------------------------------------------------- Tech
        add("Redstone relay (')", "Het relaisscherm, dan zet I.M.T de lamp op afstand aan en uit "
                + "(ook met je stem: 'turn on the lamp').", new Vec3(3.5, -60, 2.5), 0, 20, s -> s
                .open(10, RelayListScreen::new)
                .close(80)
                .lookAt(5, Vec3.atCenterOf(LAMP))
                .step(20, () -> relay(ImtRedstone.Op.TOGGLE))
                .step(40, () -> relay(ImtRedstone.Op.TOGGLE))
                .step(40, () -> relay(ImtRedstone.Op.TOGGLE))
                .step(40, () -> relay(ImtRedstone.Op.PULSE))
                .pause(60)
                .step(0, () -> relay(ImtRedstone.Op.OFF)));

        add("Relay: name and group", "Een relais een naam geven (rechtsklik op het relais, ook zonder bril, of "
                + "'rename' in het relaisscherm). Onder 'Group' staat waar het in zit ('No group' als het nergens in "
                + "zit); een groep kiezen doe je sinds 16 oktober in de app Groups op de desktop (dat zegt de regel "
                + "eronder). Alles in dezelfde groep zet je met één commando tegelijk aan of uit.",
                new Vec3(3.5, -60, 2.5), 0, 20, s -> s
                .open(10, () -> new RelayEditScreen(0))
                .pause(150)
                .close(0));

        add("Relay groups, invert and emitter", "13 en 14 oktober. Drie relais met een redstonelamp erachter: "
                + "'Door 1' en 'Door 2' in de groep 'security room' (in 'restricted', in 'base', jouw voorbeeld), en "
                + "'Garden light' in 'garden', omgekeerd (Invert, rode rand om zijn lampje): zijn lamp brandt terwijl "
                + "het relais UIT staat. Rechts een relay emitter met een hendel. De hendel gaat aan: de emitter (op "
                + "'base', Follow) zet beide deuren aan, en weer uit. Dan het redstone-scherm als boom (base > "
                + "restricted > security room > deuren; onderaan: groepen stel je in op de desktop, 16 oktober), het "
                + "scherm van deur 1 (zijn groep, alleen te lezen), het scherm van de tuinlamp (Invert: ON), het scherm "
                + "van de emitter, en tot slot de Security OS-desktop met de app Groups (dezelfde boom, met de relais "
                + "erin; slepen zet iets in een groep, en daar maak, hernoem en verwijder je groepen).",
                at(RELAY_YARD.offset(3, 0, 6)), 180, 30, s -> s
                .onServer(0, ShowcaseFeatures::buildRelayYard)
                .pause(60)
                .onServer(0, player -> relayLever(player, true))
                .pause(70)
                .onServer(0, player -> relayLever(player, false))
                .pause(50)
                .open(0, RelayListScreen::new)
                .pause(100)
                .open(0, () -> new RelayEditScreen(Minecraft.getInstance().player.getData(ModAttachments.RELAYS)
                        .indexAt(Minecraft.getInstance().level.dimension(), RELAY_YARD.offset(1, 0, 0))))
                .pause(90)
                .close(0)
                .open(0, () -> new RelayEditScreen(Minecraft.getInstance().player.getData(ModAttachments.RELAYS)
                        .indexAt(Minecraft.getInstance().level.dimension(), RELAY_YARD.offset(5, 0, 0))))
                .pause(90)
                .close(0)
                .onServer(0, player -> com.senne.imttech.redstone.RelayEmitters.open(player, RELAY_YARD.offset(6, 0, 2)))
                .pause(100)
                .close(0)
                .stand(0, RELAY_YARD.getX() + 0.5, RELAY_YARD.getY(), RELAY_YARD.getZ() + 5.5, 180, 30)
                .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, RELAY_YARD.offset(0, 0, 3)))
                .step(10, () -> desktop(os -> os.tourGroups("restricted")))
                .pause(140)
                .close(0)
                .stand(0, RELAY_YARD.getX() + 3.5, RELAY_YARD.getY(), RELAY_YARD.getZ() + 2.5, 180, 65)
                .pause(60));

        add("Rayshield: personal shield", "Een energieschild om je heen (van buitenaf bekeken): sinds hoofdstuk 54 zie je "
                + "het als een blauwe bol om je lijf, die zacht klopt (jouw notitie). Dan komen er drie pijlen van opzij op je "
                + "af: het schild vangt ze op (blauwe flits en een tik) en elke treffer kost wat energie van de bril (kijk "
                + "links boven).", GameType.SURVIVAL,
                start, 180, 0, s -> s
                .action(10, ImtAction.TOGGLE_SHIELD)
                .camera(10, CameraType.THIRD_PERSON_FRONT)
                .pause(60)
                // Normaal is de showcase-speler onkwetsbaar, maar dan komt een pijl niet eens bij
                // het schild aan. Even niet dus (het schild vangt alles op).
                .onServer(0, player -> invulnerable(player, false))
                .onServer(0, player -> shootArrowAt(player, new Vec3(-12, 0.5, 0)))
                .onServer(30, player -> shootArrowAt(player, new Vec3(12, 1, -2)))
                .onServer(30, player -> shootArrowAt(player, new Vec3(-12, 2, 3)))
                .pause(60)
                .onServer(0, player -> invulnerable(player, true))
                .camera(0, CameraType.FIRST_PERSON)
                .action(0, ImtAction.TOGGLE_SHIELD));

        add("Rayshield: laser gate", "Een laserzender (vol blok met een lens). Hoofdstuk 39: geen redstone meer; hij "
                + "staat aan zolang hij stroom heeft, en je schakelt hem op zijn naam (I.M.T, je stem, een diagram). Aan: "
                + "de energiewand schuift uit de lens naar de overkant, altijd van één kant (niet meer van twee kanten "
                + "naar het midden). Uit: hij schuift terug de zender in. Een tweede zender aan de overkant is niet "
                + "nodig. Hij draait op stroom van de warmtegenerator ernaast; zonder stroom gaat hij niet aan. Sneak + "
                + "rechtsklik op een zender = zijn instelscherm (de volgende functie).",
                new Vec3(13.5, -60, 4.5), 0, 5, s -> s
                .onServer(20, player -> ShowcaseScene.lasers(player.serverLevel(), true))
                .pause(120)
                .onServer(0, player -> ShowcaseScene.lasers(player.serverLevel(), false))
                .pause(80));

        add("Rayshield: laser settings", "13 oktober. Gewoon rechtsklikken op een zender doet niets meer (zo leg je "
                + "makkelijk een kabel); sneak + rechtsklik met een lege hand opent zijn scherm, alleen voor wie hem "
                + "neerzette of wie vertrouwd is in de zone. Rood; dan Switch ON (hoofdstuk 39: aan, zonder redstone): "
                + "de wand schuift uit; dan Horizontal: de wand ligt plat; dan Switch OFF: de wand schuift terug. In de "
                + "regel Name staat de naam van de poort in je I.M.T-lijst.",
                // Dichterbij dan bij de vorige functie: het scherm werkt tot 6 blokken van de zender.
                new Vec3(13.5, -60, 7.5), 0, 5, s -> s
                .onServer(0, player -> {
                    if (player.serverLevel().getBlockEntity(LASER_A) instanceof com.senne.imttech.shield.LaserEmitterBlockEntity emitter) {
                        emitter.setOwner(player);
                    }
                    com.senne.imttech.shield.LaserEmitters.open(player, LASER_A);
                })
                .pause(40)
                .onServer(0, player -> laser(player, LaserActionPayload.Op.COLOR, ShieldColor.RED.ordinal()))
                .pause(40)
                .onServer(0, player -> laser(player, LaserActionPayload.Op.SWITCH, 1))
                .pause(80)
                .onServer(0, player -> laser(player, LaserActionPayload.Op.TURN, 1))
                .pause(80)
                .onServer(0, player -> laser(player, LaserActionPayload.Op.SWITCH, 0))
                .pause(60)
                .close(0)
                .onServer(0, player -> {
                    laser(player, LaserActionPayload.Op.TURN, 0);
                    laser(player, LaserActionPayload.Op.COLOR, ShieldColor.BLUE.ordinal());
                }));

        add("Rayshield: dome", "De schildkoepel gaat aan (sinds hoofdstuk 54 een hele bol, die ook onder de generator doorloopt: "
                + "op een berg beschermt hij de hele berg): eerst een straal omhoog, dan vouwt hij bovenin open "
                + "en zakt hij in één vloeiende beweging als koepel naar beneden (zonder hapering). De straal uit "
                + "de generator blijft daarna staan, rustiger. Een pijl van buiten stuit op de rand (flits, en de "
                + "schildkracht zakt). Daarna het scherm: kleur, bereik, sterkte en wat het aan stroom kost. "
                + "Hij draait op de warmtegenerator ernaast.", new Vec3(-21.5, -60, -12.5), 0, -10, s -> s
                .onServer(20, player -> ShowcaseScene.dome(player).setEnabled(true))
                .pause(90)
                .onServer(0, ShowcaseScene::shootAtDome)
                .pause(50)
                .step(0, () -> Minecraft.getInstance().setScreen(new DomeScreen(DOME)))
                .pause(120)
                .close(0)
                .onServer(0, player -> ShowcaseScene.dome(player).setEnabled(false))
                .pause(40));

        add("Dome under attack", "Je staat onder de schildkoepel en drie raiddrones vallen aan. Ze komen "
                + "niet binnen: wie de rand raakt, wordt met vonken teruggeduwd, en hun laserschoten stuiten "
                + "op de koepel. Elke treffer kost schildkracht; is die bijna op, dan flakkert de koepel, en "
                + "leeg = 10 seconden uit.", GameType.SURVIVAL, new Vec3(-22.5, -60, 13.5), 180, -15, s -> s
                .onServer(10, player -> ShowcaseScene.dome(player).setEnabled(true))
                .pause(90)
                .onServer(0, ShowcaseFeatures::raidOutsideDome)
                .pause(220)
                .look(0, 150, -12)
                .pause(60)
                .look(0, -150, -12)
                .pause(60)
                .onServer(0, player -> ShowcaseScene.dome(player).setEnabled(false))
                .pause(20));

        // ---------------------------------------------------------------- Illusies
        add("Illusion: wall", "Een valse muur (van het blok in je andere hand). Eerst richten: je kijkt "
                + "door de drone, vliegt omhoog en ziet de muur als voorvertoning waar je naar kijkt (blauw "
                + "kader). Linkermuisknop zet hem neer. Alleen beeld: de echte wereld verandert niet.",
                start, 180, 12, s -> s
                .onServer(0, player -> player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BRICKS)))
                .action(20, ImtAction.ILLUSION_WALL)
                .key(20, () -> Minecraft.getInstance().options.keyJump, true)
                .key(12, () -> Minecraft.getInstance().options.keyJump, false)
                .look(5, 160, 22)
                .look(40, 200, 22)
                .look(40, 180, 25)
                .pause(30)
                .step(0, () -> placeIllusion(true))
                .pause(160)
                .action(0, ImtAction.ILLUSION_STOP));

        add("Illusion: army", "Een nep-leger (zombies, of wat er in het spawn-ei in je hand zit). Ook "
                + "richten met de drone: kijk naar de piramide, dan staan de zombies op de treden (niet "
                + "in de grond). Dan gaan ze op Seppe af: ze lopen de piramide af, gaan in een kring om hem "
                + "heen staan en slaan (armen vooruit, een tik), maar zonder schade: het is alleen beeld. Zo "
                + "leid je iemand af.", start, -90, 5, s -> s
                .action(10, ImtAction.ILLUSION_ARMY)
                .key(20, () -> Minecraft.getInstance().options.keyJump, true)
                .key(20, () -> Minecraft.getInstance().options.keyJump, false)
                .lookAt(5, Vec3.atCenterOf(PYRAMID))
                .pause(60)
                .step(0, () -> placeIllusion(true))
                .pause(80)
                .lookAt(20, eyes(FRIEND_SPOT))
                .pause(180)
                .action(0, ImtAction.ILLUSION_STOP));

        add("Illusion: cloak", "Alles in het veld verdwijnt uit beeld: kijk naar de koe en Seppe.",
                start, 180, 5, s -> s
                .lookAt(5, mob(COW))
                .action(10, ImtAction.ILLUSION_CLOAK)
                .pause(200)
                .action(0, ImtAction.ILLUSION_STOP));

        add("E.M.P.", "Eerst een valse muur, dan een E.M.P.-puls: alle illusies in de buurt vallen weg.",
                start, 180, 12, s -> s
                .action(10, ImtAction.ILLUSION_WALL)
                .step(30, () -> placeIllusion(true))
                .onServer(0, player -> player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.EMP.get())))
                .pause(140)
                .step(0, ShowcaseFeatures::useItem)
                .pause(120));

        // ---------------------------------------------------------------- Het harnas
        add("tech suit", "Het harnas: borstplaat, broek en laarzen (rood en goud). Een eigen 3D-model: "
                + "een reactor die uitsteekt en blauw gloeit (ook in het donker), borst- en buikplaten, een "
                + "rugplaat met straalopeningen, schouderplaten, onderarmbeschermers, kniestukken en "
                + "laarzen met een gouden neus en een zool. Samen met de bril neemt het schade weg.",
                start, 180, 0, s -> s
                .onServer(0, ShowcaseFeatures::wearSuit)
                .camera(10, CameraType.THIRD_PERSON_FRONT)
                .look(40, 150, 5)
                .look(40, -150, 5)
                .look(40, 180, 25)
                .pause(40)
                .camera(0, CameraType.FIRST_PERSON));

        add("Laser cannon (I, then attack)", "Toets I zet de laser cannon aan (geluid + ring rond het vizier): "
                + "je arm gaat omhoog, de hand geknikt met een zacht gloeiende handpalm. Linkermuisknop "
                + "vuurt: de palm flitst, je arm slaat terug, en er schiet een zelf getekende straal (witte "
                + "kern, blauwe gloed, drukgolfringen bij de hand, flits en ring bij de inslag). Na elk schot "
                + "laadt de ring weer op. Op het eind van buitenaf (F5): de arm vooruit en de palm gloeit.",
                range, 180, 0, s -> s
                .onServer(0, ShowcaseFeatures::wearSuit)
                .lookAt(20, mob(TARGETS.get(1)))
                .step(5, LaserCannonControl::toggle)
                .tap(40, () -> Minecraft.getInstance().options.keyAttack)
                .lookAt(30, mob(TARGETS.get(0)))
                .tap(5, () -> Minecraft.getInstance().options.keyAttack)
                .lookAt(30, mob(TARGETS.get(2)))
                .tap(5, () -> Minecraft.getInstance().options.keyAttack)
                .camera(30, CameraType.THIRD_PERSON_FRONT)
                .pause(30)
                .tap(0, () -> Minecraft.getInstance().options.keyAttack)
                .pause(40)
                .camera(0, CameraType.FIRST_PERSON)
                .step(10, LaserCannonControl::toggle)
                .pause(30));

        add("Mini rockets (X)", "Eerst het raketpakket inbouwen: met de borstplaat aan klik je rechts met "
                + "het pakket in je hand (onderin: Rocket pack installed; op je rug verschijnen twee rode "
                + "buizen). Dan markeer je twee doelwitten (R) en druk je X: zes raketten schieten om de beurt "
                + "uit de buizen, waaieren uit, draaien naar de doelwitten (drie per doelwit) en ontploffen. "
                + "Een salvo kost 900 energie en daarna 12 seconden herladen (links: ROCKETS). Zonder "
                + "markering gaan ze op wat je aankijkt, of naar de plek waar je kijkt.", GameType.SURVIVAL,
                range, 180, 0, s -> s
                .onServer(0, ShowcaseFeatures::wearSuit)
                .onServer(0, player -> player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(ModItems.ROCKET_PACK.get())))
                .step(20, () -> KeyMapping.click(Minecraft.getInstance().options.keyUse.getKey()))
                .lookAt(40, mob(TARGETS.get(0)))
                .step(5, ImtClientEvents::markLookTarget)
                .lookAt(20, mob(TARGETS.get(2)))
                .step(5, ImtClientEvents::markLookTarget)
                .look(15, 180, -5)
                .camera(0, CameraType.THIRD_PERSON_BACK)
                .step(10, () -> ImtClientEvents.sendAction(ImtAction.ROCKETS)) // geen standaardtoets meer (2026-11-10)
                .pause(70)
                .camera(0, CameraType.FIRST_PERSON)
                .pause(30));

        add("Suit workbench hologram", "Biebia's lab: boven de harnas-werkbank zweeft het harnas als hologram "
                + "(de werkbank heeft stroom van de warmtegenerator ernaast). Er is geen scherm meer. Kijk naar een "
                + "stuk: het licht op, en naast je vizier staat wat een klik doet en wat het kost (groen = genoeg bij "
                + "je, rood = te weinig). Rechtsklik op de borstplaat: de materialen gaan uit je rugzak, de borstplaat "
                + "bouwt zich plaatje voor plaatje op in het hologram en vliegt dan naar je toe (hij gaat meteen aan). "
                + "Het hologram toont één stuk tegelijk (hoofdstuk 62): een klik op de werkbank zelf gaat naar het "
                + "volgende stuk (borst, broek, laarzen, bril), sneak + klik naar het vorige. Twee klikken op de werkbank "
                + "= de laarzen; dan laarzen in je hand + klik op het hologram = ze liggen erop, en een Thruster Upgrade "
                + "in je hand + klik op de laarzen = inbouwen (de ring loopt vol).", GameType.SURVIVAL, start, 180, 20, s -> {
            BlockPos bench = ShowcaseScene.START.offset(0, 0, -3);
            // Hoofdstuk 62: elk stuk zweeft met zijn onderkant een halve blok boven de werkbank (y -58.5).
            Vec3 chest = new Vec3(0.5, -58.05, -2.5);
            Vec3 boots = new Vec3(0.5, -58.3, -2.5);
            Runnable click = () -> KeyMapping.click(Minecraft.getInstance().options.keyUse.getKey());
            s.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(bench, com.senne.imttech.registry.ModBlocks.SUIT_WORKBENCH.get().defaultBlockState());
                level.setBlockAndUpdate(bench.east(), com.senne.imttech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                if (level.getBlockEntity(bench.east()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
                    generator.fill(200_000);
                }
                if (level.getBlockEntity(bench) instanceof com.senne.imttech.workbench.SuitWorkbenchBlockEntity be) {
                    be.select(null, com.senne.imttech.workbench.HoloPiece.CHEST);
                }
                for (net.minecraft.world.entity.EquipmentSlot slot : net.minecraft.world.entity.EquipmentSlot.values()) {
                    if (slot.getType() == net.minecraft.world.entity.EquipmentSlot.Type.HUMANOID_ARMOR
                            && slot != net.minecraft.world.entity.EquipmentSlot.HEAD) {
                        player.setItemSlot(slot, ItemStack.EMPTY);
                    }
                }
                player.getInventory().setItem(0, ItemStack.EMPTY);
                player.getInventory().setItem(1, new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT, 8));
                player.getInventory().setItem(2, new ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD, 2));
                player.getInventory().setItem(3, new ItemStack(ModItems.COMPUTER_CHIP.get(), 4));
                player.getInventory().selected = 0;
            })
                    .lookAt(40, chest)
                    .step(40, click)
                    .camera(150, CameraType.THIRD_PERSON_BACK)
                    .pause(90)
                    .camera(0, CameraType.FIRST_PERSON)
                    .onServer(0, player -> player.getInventory().setItem(0, new ItemStack(ModItems.TECH_BOOTS.get())))
                    // Twee klikken op de werkbank zelf: borst -> broek -> laarzen (alleen dat stuk staat op het hologram).
                    .lookAt(20, Vec3.atCenterOf(bench))
                    .step(20, click)
                    .step(25, click)
                    .lookAt(20, boots)
                    .step(30, click)
                    .onServer(20, player -> player.getInventory().setItem(0, new ItemStack(ModItems.THRUSTER_UPGRADE.get())))
                    .step(30, click)
                    .pause(130)
                    .onServer(0, player -> player.getInventory().setItem(0, ItemStack.EMPTY))
                    .pause(40);
        });

        add("Suit flight", "Dubbel spatie: stuwraketten aan. Stijgen, stilhangen, vooruit met de "
                + "naverbrander (sprint), opzij (het beeld helt over), uitglijden en zelf landen. "
                + "Links van het vizier: stuwkracht, snelheid en hoogte. Op het eind van achteren: de "
                + "straalvlammen zijn zelf getekend en zitten vast onder de laarzen (geen spoor), harder "
                + "vliegen = langere, fellere vlammen. Andere spelers zien ze ook.", GameType.SURVIVAL,
                new Vec3(0.5, -60, 20.5), 180, -5, s -> s
                .onServer(0, ShowcaseFeatures::wearSuit)
                .key(40, () -> Minecraft.getInstance().options.keyJump, true)
                .key(2, () -> Minecraft.getInstance().options.keyJump, false)
                .key(2, () -> Minecraft.getInstance().options.keyJump, true)
                .key(25, () -> Minecraft.getInstance().options.keyJump, false)
                .pause(40)
                .key(0, () -> Minecraft.getInstance().options.keyUp, true)
                .key(0, () -> Minecraft.getInstance().options.keySprint, true)
                .key(50, () -> Minecraft.getInstance().options.keyLeft, true)
                .key(20, () -> Minecraft.getInstance().options.keyLeft, false)
                .key(0, () -> Minecraft.getInstance().options.keyUp, false)
                .key(0, () -> Minecraft.getInstance().options.keySprint, false)
                .camera(20, CameraType.THIRD_PERSON_BACK)
                .pause(40)
                .camera(0, CameraType.FIRST_PERSON)
                .key(0, () -> Minecraft.getInstance().options.keyShift, true)
                .key(100, () -> Minecraft.getInstance().options.keyShift, false)
                .pause(40));

        // ---------------------------------------------------------------- Basis
        Vec3 atTable = new Vec3(0.5, -60, 3.2);
        add("Holo table: map", "Een reliëfkaart van de omgeving als hologram: de piramide, de toren, "
                + "je waypoints (paaltjes), spelers (wit), dieren (groen) en vijanden (rood). De tafel draait "
                + "op stroom (kabel in de grond, zie 'Power grid').", atTable, 0, 22, s -> s
                .step(5, () -> holo(HoloActionPayload.Op.MODE, HoloMode.MAP.ordinal()))
                .pause(180));

        add("Holo table: blueprint", "Rechtsklik met een voorwerp: het hologram laat zien hoe je het "
                + "maakt (hier het laadstation).", atTable, 0, 22, s -> s
                .onServer(0, player -> player.setItemSlot(EquipmentSlot.MAINHAND,
                        new ItemStack(ModItems.CHARGING_STATION.get())))
                .step(10, () -> rightClick(HOLO_TABLE))
                .pause(160));

        add("Holo table: arsenal", "Je drones als kleine hologrammen van het echte drone-model, rond een "
                + "bolletje en met hun neus in de vliegrichting. De kleur is hun schade (groen heel, oranje "
                + "geraakt, rood bijna kapot); een drone die buiten is, knippert.", atTable, 0, 22, s -> s
                .step(5, () -> holo(HoloActionPayload.Op.MODE, HoloMode.ARSENAL.ordinal()))
                .pause(160)
                .step(0, () -> holo(HoloActionPayload.Op.MODE, HoloMode.MAP.ordinal())));

        add("Holo table: satellite", "De vierde stand: je satelliet groot als hologram, met de onderdelen "
                + "van zijn upgrades. De ring eronder heeft zes stukken (één per upgrade) met tien streepjes: "
                + "fel wat je hebt. Eerst de satelliet van het begin, dan alles op niveau 6, dan alles op 10 "
                + "(langere zonnepanelen, kanon, raketkokers, grotere schotel...).", atTable, 0, 22, s -> s
                .step(5, () -> holo(HoloActionPayload.Op.MODE, HoloMode.SATELLITE.ordinal()))
                .onServer(0, player -> player.setData(ModAttachments.SATELLITE, SatelliteUpgrades.START))
                .pause(100)
                .onServer(0, player -> satelliteAt(player, 6))
                .pause(90)
                .onServer(0, player -> satelliteAt(player, 10))
                .pause(90)
                .onServer(0, player -> player.setData(ModAttachments.SATELLITE, SatelliteUpgrades.START))
                .step(0, () -> holo(HoloActionPayload.Op.MODE, HoloMode.MAP.ordinal())));

        add("Holo table: ores", "De vijfde stand: de ertsen in de grond onder de tafel, als gekleurde blokjes "
                + "in een doorzichtig blok aarde (tot 16 blokken opzij en 48 diep). Dezelfde kleuren als de "
                + "ertsscanner; diamanten en smaragden knipperen, kolen en koper zijn zwakker. Het witte "
                + "puntje is de tafel, het rooster het maaiveld.", atTable, 0, 22, s -> s
                .step(5, () -> holo(HoloActionPayload.Op.MODE, HoloMode.ORES.ordinal()))
                .pause(160)
                .step(0, () -> holo(HoloActionPayload.Op.MODE, HoloMode.MAP.ordinal())));

        add("Holo table: settings", "Rechtsklik met een lege hand: het instelscherm. Kleur, bereik en "
                + "grootte veranderen meteen achter het scherm.", atTable, 0, 22, s -> s
                .step(10, () -> rightClick(HOLO_TABLE))
                .step(40, () -> holo(HoloActionPayload.Op.COLOUR, ShieldColor.PURPLE.ordinal()))
                .step(40, () -> holo(HoloActionPayload.Op.SCALE, 2))
                .step(40, () -> holo(HoloActionPayload.Op.RANGE, 3))
                .pause(60)
                .step(0, () -> holo(HoloActionPayload.Op.COLOUR, ShieldColor.CYAN.ordinal()))
                .step(0, () -> holo(HoloActionPayload.Op.SCALE, 1))
                .step(0, () -> holo(HoloActionPayload.Op.RANGE, 1))
                .close(20));

        add("Drone arsenal", "Het arsenaal-scherm: al je drones in hun baan, met hun schade.",
                start, 180, 0, s -> s
                .onServer(0, player -> {
                    // Een paar gehavende drones, zodat je de kleuren ziet.
                    List<ArsenalDrone> drones = ArsenalServer.get(player).drones();
                    for (int i = 1; i < drones.size(); i += 3) {
                        ArsenalServer.setHealth(player, drones.get(i).id(), drones.get(i).health() * (i % 2 == 0 ? 0.25f : 0.6f));
                    }
                })
                .open(10, ArsenalScreen::new)
                .pause(140)
                .close(0));

        add("Recall a drone", "Een drone komt uit zijn baan naar beneden en landt naast je, zodat je "
                + "hem kunt repareren (rechtsklik met ijzer).", start, 180, -30, s -> s
                .onServer(10, player -> {
                    List<ArsenalDrone> drones = ArsenalServer.get(player).drones();
                    if (!drones.isEmpty()) {
                        ArsenalServer.recall(player, drones.getFirst().id());
                    }
                })
                .pause(120)
                .look(0, 180, 30)
                .pause(200));

        add("Repair a drone", "Een gehavende drone (30% leven, rook en een kromme rotor) roep je terug; hij "
                + "landt naast je. Rechtsklik met ijzer in je rugzak: vonkjes, en hij is weer heel. Nog een "
                + "rechtsklik als hij heel is (of shift-rechtsklik, ook als hij nog stuk is): hij vliegt terug "
                + "naar zijn baan.", start, 180, 30, s -> s
                .onServer(0, ShowcaseFeatures::recallDamagedDrone)
                .waitUntil(300, ShowcaseFeatures::droneLandedNearYou)
                .pause(40)
                .step(0, ShowcaseFeatures::rightClickGroundedDrone)
                .pause(80)
                .step(0, ShowcaseFeatures::rightClickGroundedDrone)
                .pause(20)
                .look(10, 180, -60)
                .pause(100));

        add("Build a drone", "Een zelfgebouwde drone gebruiken: hij vliegt omhoog naar zijn baan en "
                + "komt in je arsenaal.", start, 180, -20, s -> s
                .onServer(0, player -> player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.DRONE.get())))
                .step(15, ShowcaseFeatures::useItem)
                .look(10, 180, -60)
                .pause(160));

        add("Charging station", "Het laadstation: alleen nog een bril (of een energiecel) laden (hoofdstuk 54, jouw "
                + "notitie: geen balk onder de bril, geen redstone-vakje en geen upgrade-pijl meer). Stroom komt van het net "
                + "(de kabel eronder), maar alleen als er een bril in ligt die nog niet vol is. Een bijna lege bril ligt "
                + "erin en loopt vol.",
                new Vec3(-2.5, -60, 3.5), 0, 20, s -> s
                .onServer(0, ShowcaseFeatures::fillStation)
                .step(10, () -> rightClick(CHARGING_STATION))
                .pause(120)
                .close(0));

        add("Glasses upgrades", "De bril upgraden gaat sinds hoofdstuk 54 aan de harnas-werkbank, net als het harnas: "
                + "de bril is het vierde stuk op het hologram (na de laarzen). Het hologram toont één stuk tegelijk "
                + "(hoofdstuk 62): een klik op de werkbank zelf gaat naar het volgende stuk, sneak + klik naar het vorige. Je "
                + "houdt een tweede bril vast en doet sneak + klik op de werkbank: van de borstplaat naar links, rond naar de "
                + "bril. Een klik op de bril die erboven zweeft legt hem erop; dan een batterij-upgrade in je hand en klik: "
                + "de ring loopt vol en de upgrade zit erin (dat zie je in de tooltip). Er zijn drie "
                + "soorten: batterij (meer energie), scanner (grotere ertsscan) en drones (meer drones in de zwerm).",
                GameType.SURVIVAL, new Vec3(0.5, -60, -0.5), 180, 20, s -> {
            BlockPos bench = ShowcaseScene.START.offset(0, 0, -3);
            Vec3 glassesBox = new Vec3(0.5, -58.38, -2.5);
            Runnable clickBench = () -> rightClick(bench);
            Runnable click = () -> KeyMapping.click(Minecraft.getInstance().options.keyUse.getKey());
            s.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(bench, com.senne.imttech.registry.ModBlocks.SUIT_WORKBENCH.get().defaultBlockState());
                level.setBlockAndUpdate(bench.east(), com.senne.imttech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                if (level.getBlockEntity(bench.east()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
                    generator.fill(200_000);
                }
                if (level.getBlockEntity(bench) instanceof com.senne.imttech.workbench.SuitWorkbenchBlockEntity be) {
                    be.select(null, com.senne.imttech.workbench.HoloPiece.CHEST);
                }
                player.getInventory().setItem(0, DevWorld.ownGlasses(player));
                player.getInventory().setItem(1, new ItemStack(ModItems.BATTERY_UPGRADE.get(), 2));
                player.getInventory().selected = 0;
            })
            .lookAt(10, Vec3.atCenterOf(bench))
            // Sneak + klik (naar links): van de borstplaat rond naar de bril.
            .key(0, () -> Minecraft.getInstance().options.keyShift, true)
            .step(10, clickBench)
            .key(5, () -> Minecraft.getInstance().options.keyShift, false)
            .pause(30)
            // De bril erop (klik op de bril die erboven zweeft), en de upgrade erin.
            .lookAt(15, glassesBox)
            .step(10, click)
            .pause(30)
            .step(0, () -> hotbar(1))
            .pause(15)
            .step(0, click)
            .pause(160)
            .onServer(0, player -> {
                player.getInventory().clearContent();
                if (player.serverLevel().getBlockEntity(bench) instanceof com.senne.imttech.workbench.SuitWorkbenchBlockEntity be) {
                    be.dropAll(player.serverLevel());
                }
                player.serverLevel().setBlockAndUpdate(bench, Blocks.AIR.defaultBlockState());
                player.serverLevel().setBlockAndUpdate(bench.east(), Blocks.AIR.defaultBlockState());
            });
        });

        add("Solar panel and heat generator", "Stroom maken: een zonnepaneel (een schuin paneel op een "
                + "paaltje) en een warmtegenerator op magma (een kast met koelribben, koperen pijpen achter "
                + "en voorin een rooster dat oranje gloeit zolang hij warmte opwekt). Daarna hun schermen.",
                new Vec3(-6, -60, 3.5), 0, 20, s -> s
                .step(10, () -> rightClick(SOLAR_PANEL))
                .close(90)
                .step(10, () -> rightClick(HEAT_GENERATOR))
                .close(90));

        add("Power grid (power cables)", "Het stroomnet: de stroomkabel in de grond verbindt de "
                + "warmtegenerator en het zonnepaneel met het laadstation en de holotafel (kabels sluiten "
                + "vanzelf aan). Dan wordt de kabel doorgeknipt: de holotafel valt uit (alleen een rood "
                + "ringetje). Kabel terug: hij gaat weer aan.", new Vec3(-2.5, -58.5, 1.5), 0, 40, s -> s
                .lookAt(10, new Vec3(-2.5, -61, 6.5))
                .pause(60)
                .onServer(0, player -> player.serverLevel().setBlockAndUpdate(GRID_CUT, Blocks.AIR.defaultBlockState()))
                .lookAt(20, Vec3.atCenterOf(HOLO_TABLE).add(0, 1, 0))
                .pause(80)
                .onServer(0, player -> player.serverLevel().setBlockAndUpdate(GRID_CUT,
                        com.senne.imttech.registry.ModBlocks.CABLE.get().defaultBlockState()))
                .pause(80));

        add("Arc Lamp and light switch", "9 oktober: een lamp met lichtniveau 100 (een gewone lamp stopt bij 15). "
                + "Het is nacht. Een generator, een kabel over het gras met halverwege een lichtschakelaar (uit, "
                + "rood lampje), en aan het eind een Arc Lamp op een paal: donker. Je klikt op de schakelaar (het "
                + "lampje wordt blauw, de wip klapt om): de lamp gaat aan, en het hele veld "
                + "wordt licht. Van bovenaf zie je hoe ver: een ruit van 85 blokken vol licht, met een zachte rand "
                + "tot 100 blokken. 10 oktober: dan het instelscherm van de lamp (rechtsklik). Het schuifje "
                + "'Brightness' zakt van lichtniveau 100 naar 40 (onder het schuifje: 'Full light up to 25 blocks, dark "
                + "after 40', en hij gebruikt minder stroom); van bovenaf is de ruit nu veel kleiner. Dan klik je de "
                + "schakelaar weer uit: alles meteen donker.",
                at(LAMP_YARD.offset(3, 0, 3)), 180, 50, s -> s
                .onServer(0, ShowcaseFeatures::buildLampYard)
                .command(0, "time set midnight")
                .lookAt(20, Vec3.atCenterOf(LAMP_YARD.east(3)).add(0, -0.45, 0))
                .pause(40)
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .lookAt(30, Vec3.atCenterOf(LAMP_YARD.east(7).above(4)))
                .pause(80)
                .view(0, LAMP_YARD.getX() + 7.5, LAMP_YARD.getY() + 60, LAMP_YARD.getZ() + 40.5, 180, 55)
                .pause(120)
                // Het instelscherm: de lamp zachter zetten, stapje voor stapje (alsof je het schuifje sleept).
                .stand(0, LAMP_YARD.getX() + 5.5, LAMP_YARD.getY(), LAMP_YARD.getZ() + 1.5, 160, -40)
                .onServer(5, player -> com.senne.imttech.light.ArcLamps.open(player, LAMP_YARD.east(7).above(4)))
                .pause(30)
                .step(0, () -> lampScreen(90))
                .step(6, () -> lampScreen(75))
                .step(6, () -> lampScreen(60))
                .step(6, () -> lampScreen(50))
                .step(6, () -> lampScreen(40))
                .pause(50)
                .close(0)
                .view(0, LAMP_YARD.getX() + 7.5, LAMP_YARD.getY() + 60, LAMP_YARD.getZ() + 40.5, 180, 55)
                .pause(100)
                .onServer(0, player -> {
                    if (player.serverLevel().getBlockEntity(LAMP_YARD.east(7).above(4))
                            instanceof com.senne.imttech.light.ArcLampBlockEntity lamp) {
                        lamp.setBrightness(100);
                    }
                })
                .stand(0, LAMP_YARD.getX() + 3.5, LAMP_YARD.getY(), LAMP_YARD.getZ() + 3.5, 180, 50)
                .lookAt(10, Vec3.atCenterOf(LAMP_YARD.east(3)).add(0, -0.45, 0))
                .pause(20)
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .lookAt(20, Vec3.atCenterOf(LAMP_YARD.east(7).above(4)))
                .pause(80)
                .command(0, "time set noon"));

        // ---------------------------------------------------------------- Vijanden en wereld
        add("Raid drones", "Vijandige drones (donker, oranje lampjes) duiken uit de lucht, cirkelen "
                + "om je heen en schieten. Ze komen vanzelf als je de bril draagt. Hoofdstuk 54: een raid begint altijd "
                + "buiten een basis (een beveiligingskern met haar zone), en in een beschermde zone breken ze niet zomaar "
                + "in: ze gooien sticky frags op je muren (het alarm gaat af) en EMP's op je torentjes en laserzenders. "
                + "Hoofdstuk 75: ze schieten tot 12 blokken en merken je op tot 24, en ga je dood, dan is de raid voorbij: "
                + "op het eind stijgen ze op en vliegen ze weg alsof je net stierf (hier zonder echt dood te gaan).",
                GameType.SURVIVAL,
                start, 180, -20, s -> s
                .onServer(10, player -> ImtRaids.startRaid(player, 3))
                .pause(120)
                .look(0, 0, -10)
                .pause(160)
                .onServer(0, ImtRaids::callOff)
                .look(0, 0, -45)
                .pause(80));

        add("Raven soldiers", "Nieuwe vijanden: Raven-soldaten, die de Biebia's Industries-bunker bewaken. Zwart tactisch "
                + "pak met vest, magazijntasjes, een radio op de rug en een vizier op de helm. Sinds hoofdstuk 54 dragen ze de "
                + "wapens van de mod: de helft een Glock 19 (echte kogels), de rest een laser rifle of laser pistol, en sommigen "
                + "gooien frag- of EMP-granaten als je je verstopt. Zien ze je, dan leggen ze aan (twee handen) en schieten ze. "
                + "Ze schieten ook op je drones, en raak je er één, dan komen de anderen ook. Soms laten ze een Raven-onderdeel "
                + "of hun wapen vallen.",
                GameType.SURVIVAL, start, 180, 0, s -> s
                .onServer(10, ShowcaseFeatures::spawnRavenSquad)
                .pause(80)
                .camera(0, CameraType.THIRD_PERSON_BACK)
                .pause(80)
                .camera(0, CameraType.FIRST_PERSON)
                .pause(60));

        add("Guards like in Hitman", "Hoofdstuk 60: een Raven-soldaat is een bewaker zoals in Hitman. Hij loopt zijn route "
                + "met zijn rug naar je toe en ziet je niet. Dan klinkt er lawaai vlak voor je: boven zijn hoofd verschijnt "
                + "een geel ? en hij komt kijken. Zodra hij je ziet, loopt het oog op je bril vol (wit, oranje, rood): hij "
                + "heeft je, een rood !, en hij grijpt eerst naar zijn radio (een seconde pakken, drie seconden praten: "
                + "\"[Radio] BB-xxxx: Contact!\"). Pas daarna schiet hij. Na een tijdje valt hij: zijn lichaam blijft liggen "
                + "met zijn buit erin (rechtsklik: doorzoeken, sneak + rechtsklik: meeslepen en verstoppen), want wie een "
                + "lichaam ziet, roept ook versterking. Sluipen is stil en maakt zijn meter half zo snel vol.",
                GameType.SURVIVAL, start, 180, 0, s -> s
                .onServer(10, ShowcaseFeatures::spawnHitmanGuard)
                .pause(90)
                .onServer(0, player -> com.senne.imttech.raven.Noises.make(player.serverLevel(), player.position().add(0, 0.5, -4), 12,
                        com.senne.imttech.raven.Noises.Kind.BLOCK, player))
                .pause(220)
                .onServer(0, ShowcaseFeatures::dropHitmanGuard)
                .camera(0, CameraType.THIRD_PERSON_BACK)
                .pause(100)
                .camera(0, CameraType.FIRST_PERSON)
                .pause(40));

        add("Automatic turrets", "Een Biebia's Industries-torentje (gemaakt met een Raven-onderdeel) met een warmtegenerator "
                + "ernaast: de kop kijkt rustig rond tot er monsters komen (eerst misschien een slijmpje), "
                + "draait dan naar het dichtstbijzijnde en blijft daarop schieten tot het neer is: een blauwe "
                + "laser (60 stroom per schot). Er zitten 16 raketten in (Rocket Ammo, met "
                + "rechtsklik of een trechter), dus om de paar seconden gaat er ook een raket in een boogje op "
                + "af. Jou raakt hij nooit. Dan zet I.M.T hem uit op zijn naam (hoofdstuk 39: redstone doet niets "
                + "meer; hij staat in je I.M.T-lijst): de loop hangt omlaag. Rechts een laserturret van Biebia's aan een kern van "
                + "niemand (hoofdstuk 54: er zijn nog maar twee soorten, de raketturret en de laserturret, allebei via een "
                + "security OS): die schiet op jou.",
                GameType.SURVIVAL, at(ShowcaseScene.TURRET.offset(-4, 0, 3)), 200, 10, s -> s
                .onServer(0, ShowcaseScene::buildTurrets)
                .pause(40)
                .onServer(0, ShowcaseScene::turretAttackers)
                .pause(240)
                .onServer(0, player -> com.senne.imttech.redstone.ImtRedstone.switchAt(player.serverLevel(), ShowcaseScene.TURRET, -1,
                        com.senne.imttech.redstone.ImtRedstone.Op.OFF, 0))
                .pause(50)
                .look(0, 150, 10)
                .pause(70));

        add("Raid drones: breaking in", "Verstop je in een dichte hut (stenen muren, glazen dak) en de "
                + "raiddrones zien je niet. Dan komen ze dichterbij en schieten ze zich naar binnen: om de "
                + "anderhalve seconde verdwijnt er een blok (laserstraal en vonken), tot ze je zien. Kijk "
                + "omhoog door het dak. Wie dat slopen niet wil: gamerule imttechRaidGriefing false.",
                GameType.SURVIVAL, at(HUT), 180, -55, s -> s
                .onServer(0, player -> ShowcaseScene.buildHut(player.serverLevel()))
                .command(0, "gamerule imttechRaidGriefing true")
                .onServer(20, player -> ImtRaids.startRaid(player, 3))
                .pause(150)
                .look(0, 0, -35)
                .pause(110)
                .look(0, 90, -10)
                .pause(110)
                .command(0, "gamerule imttechRaidGriefing false"));

        add("Biebia's Industries Tower", "Het gebouw waar je de bril vindt: rondvliegen om de Biebia's Industries Tower. Let op het "
                + "BIEBIA-logo van koperen lampen op de gevel, de lampjes langs elk terras, de kluis bovenin "
                + "(donker, maar met hetzelfde ritme als de rest) en de blauwe lichtstraal uit de kroon.",
                new Vec3(BIEBIA_TOWER.getX() - 30, -20, BIEBIA_TOWER.getZ() + 20), -90, 10, s -> {
            Vec3 centre = towerCentre();
            double[][] around = {{-30, 20}, {20, -45}, {70, 20}, {20, 85}};
            for (double[] spot : around) {
                double x = BIEBIA_TOWER.getX() + spot[0];
                double z = BIEBIA_TOWER.getZ() + spot[1];
                double y = -10;
                float yaw = (float) Math.toDegrees(Math.atan2(-(centre.x - x), centre.z - z));
                s.view(0, x, y, z, yaw, 5).pause(80);
            }
            s.view(0, centre.x, 60, centre.z - 60, 0, 30).pause(80);
        });

        add("Biebia's Industries Tower: laser corridor", "Binnen in de toren: de lasergang naar de kluis (acht rijen "
                + "rayshields). Ze draaien op stroom van een verborgen krachtcentrale een verdieping lager: "
                + "daarna zie je het dichtgemetselde blok in de hoek en de kabels in het plafond. Knip je "
                + "die door, dan gaan de lasers uit.", towerPoint(20.5, 68, 30.2), 180, 0, s -> s
                .pause(120)
                .view(0, BIEBIA_TOWER.getX() + 22.5, BIEBIA_TOWER.getY() + 57, BIEBIA_TOWER.getZ() + 18.5, 180, 0)
                .lookAt(10, towerPoint(27.5, 62, 12.5))
                .pause(80)
                .lookAt(10, towerPoint(21, 66.5, 14))
                .pause(80));

        add("Biebia's Industries Tower: floors", "Elke verdieping van binnen, van onder naar boven: lobby (receptie met "
                + "logowand, vitrines, zithoeken, de glazen liftkoker), lab (watertanks, pakstelling, "
                + "teslaspoel, werkbanken), archief, hangar (landingscirkel, dockingstations, hangardeur), "
                + "woonverdieping, beveiliging (serverkasten, schermenwand, holotafel), lasergang, kluis, "
                + "penthouse (bar, vleugel, holotafel) en het dak (zonnepanelen, schotel, kroon met baken).",
                towerPoint(35.5, 2, 35.5), 135, 12, s -> {
            String[] names = {"lobby", "lab", "archive", "hangar", "living", "security", "lasers", "vault",
                    "penthouse", "roof"};
            int[] floors = {1, 12, 23, 34, 45, 57, 67, 77, 89, 99};
            int[] insets = {2, 4, 5, 6, 7, 8, 9, 9, 10, 10};
            for (int i = 0; i < floors.length; i++) {
                double corner = 41 - 1 - insets[i] - 1.5;
                s.title(0, names[i], "").view(0, BIEBIA_TOWER.getX() + corner, BIEBIA_TOWER.getY() + floors[i] + 1,
                        BIEBIA_TOWER.getZ() + corner, 135, 12).pause(70);
            }
        });

        add("Biebia's Industries Tower: vault trap", "De val in de kluis: voor de kluiskist ligt een drukplaat, met in de "
                + "vloer eromheen vier laserzenders die omhoog kijken. Stap je erop, dan schieten er vier rode "
                + "laserzuilen tot aan het plafond: je staat in een kooi. Eruit door een zender uit de vloer "
                + "te hakken, of door de stroom af te snijden (ze hangen aan de krachtcentrale).",
                towerPoint(20.5, 79, 13.5), 0, 15, s -> s
                .pause(60)
                .stand(0, BIEBIA_TOWER.getX() + 20.5, BIEBIA_TOWER.getY() + 79.1, BIEBIA_TOWER.getZ() + 23.5, 0, 10)
                .pause(60)
                .look(30, 0, -55)
                .pause(60)
                .view(20, BIEBIA_TOWER.getX() + 20.5, BIEBIA_TOWER.getY() + 80, BIEBIA_TOWER.getZ() + 15.5, 0, 25)
                .pause(80));

        add("Biebia's Industries Tower: security and hack", "De beveiliging van de toren, in survival. Je staat op de "
                + "beveiligingsverdieping naast de Security Core (de serverkast met het rode lampje, in de rij "
                + "serverkasten). De kern stuurt vier beveiligingsdrones met rode lampjes de lucht in, en die en de "
                + "beveiligingstorentjes schieten op je (hier ben je onkwetsbaar). Dan klik je met de Hacking Laptop op "
                + "de kern: de laptop staat opengeklapt bovenop de kern, het alarm gaat af en de terminal gaat open "
                + "(hier speelt hij zichzelf): de open poort zoeken, het wachtwoord raden, Biebia's README lezen, en in "
                + "de console van het beveiligingsprogramma de waakhond uit en jezelf eigenaar. Halverwege gaat het "
                + "scherm even dicht (Esc) en weer open: je gaat verder waar je was. Daarna is de toren van jou: het "
                + "lampje wordt groen, de drones worden blauw en laten je met rust, en je krijgt de laptop terug.", GameType.SURVIVAL, towerPoint(12.5, 57, 20.5), 90, 12, s -> {
            BlockPos core = BIEBIA_TOWER.offset(10, 57, 20);
            s.onServer(0, player -> {
                if (player.serverLevel().getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    be.clearOwner();
                }
                // Een laptop van een eerdere keer eraf, zodat de hack echt opnieuw begint.
                player.serverLevel().removeBlock(core.above(), false);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(ModItems.HACKING_LAPTOP.get()));
            })
                    .pause(180)
                    .step(0, com.senne.imttech.client.HackScreen::startAutoPlay)
                    .onServer(0, player -> com.senne.imttech.security.TowerHack.placeLaptop(player, core,
                            player.getMainHandItem()))
                    .pause(260)
                    .close(40)
                    .onServer(0, player -> com.senne.imttech.security.TowerHack.open(player, core.above()))
                    .pause(700)
                    .step(0, () -> com.senne.imttech.client.HackScreen.autoPlay = false)
                    .pause(80);
        });

        add("Security OS desktop", "De Security OS-desktop op de beveiligingskern (hier is de toren van jou). "
                + "Rechtsklik met een lege hand op de kern opent hem: sleepbare vensters, icoontjes links en een "
                + "taakbalk. Achter elkaar: Files met de map /etc (security.conf is een systeembestand: SYS, "
                + "verplaatsen en hernoemen mag, weggooien niet), Biebia's map met het rechtsklik-menu (Open, "
                + "Rename, Delete, Mark as trap), zijn notitie in Notes, dan Devices (de torentjes, drones, "
                + "laserpoorten en camera's van de toren, met hun status en schakelaars), Access (monsters en "
                + "spelers aanvallen, vertrouwde spelers) en Logs (wat er op het systeem gebeurde). In Settings kies je sinds "
                + "hoofdstuk 54 welke apps op het bureaublad staan (een vinkje per app: installeren en verwijderen); alleen "
                + "Settings zelf blijft altijd.",
                towerPoint(12.5, 57, 20.5), 90, 12, s -> {
            BlockPos core = BIEBIA_TOWER.offset(10, 57, 20);
            s.onServer(0, player -> {
                claimTowerCore(player, core);
                com.senne.imttech.security.os.SecurityOs.open(player, core);
            })
                    .pause(60)
                    .step(0, () -> desktop(os -> os.tourFiles("/etc", "/etc/security.conf", false)))
                    .pause(90)
                    .step(0, () -> desktop(os -> os.tourFiles("/home/biebia", "/home/biebia/notes.txt", true)))
                    .pause(80)
                    .onServer(0, player -> {
                        net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
                        args.putString("path", "/home/biebia/notes.txt");
                        com.senne.imttech.security.os.SecurityOs.handleAction(player,
                                new com.senne.imttech.network.OsActionPayload(core, "fs_open", args));
                    })
                    .pause(90)
                    .step(0, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.DEVICES)))
                    .pause(110)
                    .step(0, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.ACCESS)))
                    .pause(80)
                    .step(0, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.LOGS)))
                    .pause(80)
                    .close(20);
        });

        add("Security cameras", "De bewakingsmuur van de toren (de app Cameras): zes camera's, drie op de "
                + "beveiligingsverdieping en een in de lobby, de lasergang en de kluis. Elk beeldje is live, met een "
                + "CRT-randje, de naam en een knipperend REC; de mod tekent ze zelf, een paar keer per seconde. Dan "
                + "het grote beeld van de lobby en van de kluis: die camera draait een stukje naar rechts en weer "
                + "terug (dat kan met de pijltjes of door het beeld te slepen). Daarna gaan de camera's uit "
                + "(Devices > Cameras): overal NO SIGNAL. Tot slot een camera van dichtbij, aan het plafond: een wit "
                + "huisje met een rode streep.", towerPoint(12.5, 57, 20.5), 90, 12, s -> {
            BlockPos core = BIEBIA_TOWER.offset(10, 57, 20);
            BlockPos lobby = BIEBIA_TOWER.offset(25, 7, 14);
            BlockPos vault = BIEBIA_TOWER.offset(20, 82, 10);
            s.onServer(0, player -> {
                claimTowerCore(player, core);
                networkSwitch(player, core, "cameras", true);
                com.senne.imttech.security.os.SecurityOs.open(player, core);
            })
                    .step(5, () -> desktop(os -> os.tourCameras(null)))
                    .pause(140)
                    .step(0, () -> desktop(os -> os.tourCameras(lobby)))
                    .pause(90)
                    .step(0, () -> desktop(os -> os.tourCameras(vault)))
                    .pause(70)
                    .onServer(0, player -> aimCamera(player, core, vault, 20f))
                    .pause(50)
                    .onServer(0, player -> aimCamera(player, core, vault, -20f))
                    .pause(50)
                    .step(0, () -> desktop(os -> os.tourCameras(null)))
                    .onServer(0, player -> networkSwitch(player, core, "cameras", false))
                    .pause(70)
                    .onServer(0, player -> networkSwitch(player, core, "cameras", true))
                    .close(20)
                    .lookAt(10, towerPoint(9.5, 65.3, 9.5))
                    .pause(80);
        });

        add("Server racks and Logic", "Hoofdstuk 8: je redstone programmeren. Een klein lab met een eigen "
                + "beveiligingskern en drie serverracks (lampjes knipperen: ze draaien mee; samen 2 CPU's, 2 RAM, "
                + "2 SSD's). In de muur een ijzeren deur, links een keycardlezer, rechts een codepaneel en een lamp. "
                + "Eerst de kaart Lab access (in de groep lab; het diagram vraagt card.in_group(\"lab\")) tegen de "
                + "lezer: oranje, dan groen, en de deur gaat 3 seconden open (het diagram doet pulse(\"Lab door lock\", "
                + "3)). Dan een kaart die geblokkeerd is: rood, ook al zit hij in lab. Dan de "
                + "code 4321 op het paneel: de lamp gaat aan. Dan sneak + rechtsklik op de lezer (13 oktober): zijn "
                + "naamscherm, met 'Lab entrance' getypt (eronder de regel voor je diagram, en in oranje dat je die "
                + "ook moet veranderen); Esc laat het zoals het was. Bij het codepaneel werkt dat net zo. Op de "
                + "desktop eerst de app Keycards (25 oktober): links je groepen als een boom (lab en guest zitten in "
                + "staff), rechts de kaarten van lab, met hun kleur en Remove en Block naast elkaar. Daarna de "
                + "app Logic: de code van het "
                + "diagram (met kleuren; de deur openen staat in een eigen blok, def open_door(), dat de kaart en "
                + "de code 7777 allebei gebruiken), en dezelfde code als blokjes met draadjes (de DEF in paars). "
                + "Luister ook naar de nieuwe "
                + "rayshield-geluiden bij de laserpoort en de koepel (eerder in de showcase).",
                Vec3.atBottomCenterOf(LOGIC_LAB.offset(3, 0, 11)), 180, 10, s -> {
            s.onServer(0, ShowcaseFeatures::buildLogicLab)
                    .lookAt(40, Vec3.atCenterOf(LOGIC_LAB.offset(3, 1, 6)))
                    .pause(40)
                    // De goede kaart: groen, de deur gaat open.
                    .onServer(0, player -> readCard(player, true))
                    .pause(90)
                    // Een geblokkeerde kaart: rood.
                    .onServer(0, player -> readCard(player, false))
                    .pause(60)
                    // Het codepaneel: de toetsen, dan de code, en de lamp gaat aan.
                    .step(0, () -> {
                        Minecraft.getInstance().setScreen(new com.senne.imttech.client.CodePanelScreen(
                                LOGIC_LAB.offset(5, 1, 7), "Lights panel"));
                        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.CodePanelScreen panel) {
                            panel.tourType("4321");
                        }
                    })
                    .pause(50)
                    .close(0)
                    .onServer(0, player -> {
                        com.senne.imttech.logic.LogicEvents.clear();
                        com.senne.imttech.logic.LogicEvents.codePanel(player.serverLevel(), LOGIC_LAB.offset(5, 1, 7),
                                player, "4321");
                    })
                    .pause(60)
                    // 13 oktober: sneak + rechtsklik op de lezer = zijn naamscherm (jij bent de eigenaar van de kern).
                    // "Lab entrance" getypt; Esc laat het zoals het was, anders vindt het diagram hem niet meer.
                    .onServer(0, player -> com.senne.imttech.logic.InputDevices.open(player, LOGIC_LAB.offset(1, 1, 7)))
                    .step(10, () -> {
                        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.InputNameScreen screen) {
                            screen.tourType("Lab entrance");
                        }
                    })
                    .pause(90)
                    .close(0)
                    // De racks van dichtbij, en dan de desktop bij de kern.
                    .stand(0, LOGIC_LAB.getX() + 3.5, LOGIC_LAB.getY(), LOGIC_LAB.getZ() + 3.5, 180, 15)
                    .pause(50)
                    .stand(0, LOGIC_LAB.getX() + 0.5, LOGIC_LAB.getY(), LOGIC_LAB.getZ() + 3.5, 180, 20)
                    .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, LOGIC_LAB))
                    // De app Keycards: de groepen van het lab, met "lab" gekozen.
                    .step(10, () -> desktop(os -> os.tourKeycardGroup("lab")))
                    .pause(110)
                    .step(0, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.LOGIC)))
                    .pause(120)
                    .step(0, () -> desktop(os -> os.tourLogicBlocks(null)))
                    .pause(120)
                    .step(0, () -> desktop(os -> os.tourLogicBlocks(com.senne.imttech.logic.program.Op.IF)))
                    .pause(80)
                    .close(20);
        });

        add("Logic: names, sums and loops", "Hoofdstuk 20: de taal groeit. Hetzelfde lab, met een tweede diagram "
                + "'Blink': count = 0 helemaal links (een naam voor het hele programma), en als de lamp aangaat telt "
                + "het één op, zegt I.M.T 'The lights went on 1 times' (een berekend bericht: tekst + een getal), "
                + "en gaat de deur drie keer even open (16 oktober: for i in range(1, 4), met i in het logboek: "
                + "'Blink 1 of 3'...; 19 oktober: dat bericht is tekst met een naam erin, log($\"Blink {i} of 3\")). Eerst "
                + "de code 4321 op het paneel: de lamp gaat aan, I.M.T zegt het, en de deur "
                + "gaat drie keer open en dicht. Dan de app Logic: de code (de nieuwe woorden roze, = en + ook), en "
                + "als blokjes: AT START en een geel SET-blokje, en het roze FOR-blokje met zijn uitgangen do "
                + "(rechtsboven, de stappen erin) en after (onderaan); daarna het FOR-blokje gekozen (rechts: de "
                + "naam die telt, en het bereik). Het palet rechts scrolt met het muiswiel (pijltje onderaan).",
                Vec3.atBottomCenterOf(LOGIC_LAB.offset(3, 0, 11)), 180, 10, s -> {
            s.onServer(0, ShowcaseFeatures::buildLogicLab)
                    .onServer(0, ShowcaseFeatures::addBlinkProgram)
                    .lookAt(40, Vec3.atCenterOf(LOGIC_LAB.offset(3, 1, 6)))
                    .pause(30)
                    // De code 4321: "Lab" zet de lamp aan, en "Blink" hoort dat (when turned_on).
                    .onServer(0, player -> {
                        com.senne.imttech.logic.LogicEvents.clear();
                        com.senne.imttech.logic.LogicEvents.codePanel(player.serverLevel(), LOGIC_LAB.offset(5, 1, 7),
                                player, "4321");
                    })
                    .pause(130)
                    .stand(0, LOGIC_LAB.getX() + 0.5, LOGIC_LAB.getY(), LOGIC_LAB.getZ() + 3.5, 180, 20)
                    .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, LOGIC_LAB))
                    .step(10, () -> desktop(os -> os.tourLogic("Blink", BLINK_CODE, false)))
                    .pause(120)
                    .step(0, () -> desktop(os -> os.tourLogicBlocks(null)))
                    .pause(100)
                    .step(0, () -> desktop(os -> os.tourLogicBlocks(com.senne.imttech.logic.program.Op.FOR)))
                    .pause(80)
                    .close(20);
        });

        add("The datacenter", "Hoofdstuk 9: het datacenter (in survival), sinds hoofdstuk 64 zonder scherm op het rack. Het lab "
                + "van hiervoor: drie racks (lab-1, lab-2 en lab-3, zo heten ze in de app Monitor), elk met zijn eigen temperatuur "
                + "en dus zijn eigen fan, maar lab-3 heeft nu twee GPU's bovenaan en geen fan meer. Eerst de app Monitor met "
                + "lab-3 gekozen: zijn acht vakjes in het klein, Temp 'rising' (zonder fan raakt de warmte er niet uit), warmte en "
                + "koeling en hoeveel fans erin moeten. Dan is dat rack te heet (96 graden): de hele server op halve snelheid, "
                + "I.M.T waarschuwt (met de naam van het rack), en de GPU's slijten (rood in de Monitor). Daarna vat het rack vlam: "
                + "vuur, rook, ON FIRE knippert in de Monitor, en na 8 seconden is het weg, met alles erin; de andere twee racks "
                + "blijven koel. Er zat een SSD in: I.M.T zegt dat alle diagrammen en keycards weg zijn. Dan de hele server in de "
                + "app Monitor (RAM en SSD in GB en TB, met wat de CPU's en GPU's nodig hebben) en de app Backup: er staat een "
                + "backup op een tweede datacenter ver weg (dev-vault), en Restore zet alles terug. Tot slot het scherm AI Brain "
                + "(rechtsboven: op welk datacenter het brein draait, en hoeveel vertraging) en 'night vision on': "
                + "eerst THINKING..., dan doet ze het.",
                GameType.SURVIVAL, Vec3.atBottomCenterOf(LOGIC_LAB.offset(3, 0, 4)), 180, 20, s -> {
            BlockPos hot = LOGIC_LAB.offset(4, 0, 0);
            s.onServer(0, ShowcaseFeatures::buildDatacenter)
                    // Een nieuwe kern meldt zich pas een tick later: daarna pas verbinden en de backup.
                    .onServer(30, ShowcaseFeatures::connectDatacenters)
                    .pause(40)
                    // Het rack zonder fan in de app Monitor: Temp "rising", "No fan: ... Add 1 fan."
                    .stand(0, LOGIC_LAB.getX() + 0.5, LOGIC_LAB.getY(), LOGIC_LAB.getZ() + 3.5, 180, 20)
                    .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, LOGIC_LAB))
                    .step(10, () -> desktop(os -> os.tourMonitorRack("lab-3")))
                    .pause(90)
                    .close(0)
                    // Te heet: halve snelheid, waarschuwing, slijten.
                    .onServer(0, player -> heatUp(player, hot, 96))
                    .pause(20)
                    .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, LOGIC_LAB))
                    .step(10, () -> desktop(os -> os.tourMonitorRack("lab-3")))
                    .pause(90)
                    .close(0)
                    // Brand: het te hete rack vat vlam en is na 8 seconden weg (de andere twee niet).
                    .stand(0, LOGIC_LAB.getX() + 3.5, LOGIC_LAB.getY(), LOGIC_LAB.getZ() + 4.5, 180, 20)
                    .onServer(0, player -> {
                        if (player.serverLevel().getBlockEntity(hot) instanceof com.senne.imttech.logic.ServerRackBlockEntity rack) {
                            rack.ignite(player.serverLevel(), player.serverLevel().getGameTime() + 160);
                        }
                    })
                    .pause(40)
                    .stand(0, LOGIC_LAB.getX() + 0.5, LOGIC_LAB.getY(), LOGIC_LAB.getZ() + 3.5, 180, 20)
                    .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, LOGIC_LAB))
                    .step(10, () -> desktop(os -> os.tourMonitorRack("lab-3")))
                    .pause(50)
                    .close(0)
                    .stand(0, LOGIC_LAB.getX() + 3.5, LOGIC_LAB.getY(), LOGIC_LAB.getZ() + 4.5, 180, 20)
                    .pause(90)
                    // De desktop: Monitor en Backup.
                    .stand(0, LOGIC_LAB.getX() + 0.5, LOGIC_LAB.getY(), LOGIC_LAB.getZ() + 3.5, 180, 20)
                    .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, LOGIC_LAB))
                    .step(10, () -> desktop(os -> os.tourMonitorRack(null)))
                    .pause(140)
                    .step(0, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.BACKUP)))
                    .pause(60)
                    // Restore: de backup van dev-vault zet de diagrammen en keycards terug.
                    .onServer(0, ShowcaseFeatures::restoreLab)
                    .pause(140)
                    .close(10)
                    // Het brein draait op een datacenter: het scherm AI Brain, en dan even nadenken.
                    .open(10, com.senne.imttech.client.BrainScreen::new)
                    .pause(90)
                    .close(0)
                    .step(10, () -> com.senne.imttech.client.voice.VoiceControl.hear("night vision on"))
                    .pause(2)
                    .waitUntil(600, () -> !com.senne.imttech.client.voice.ImtBrain.isThinking())
                    .pause(70);
        });

        add("Protected zones", "Hoofdstuk 10: zones (in survival). Een L-vormige basis met een torentje; de kern binnen "
                + "is van jou. Je ziet de blauwe rand: twee kubussen (waar ze tegen elkaar liggen, geen wand), met een "
                + "raster op elke blokrand. Dan de Zone Marker in je hand: een oranje doos (een schuurtje aan de "
                + "oostkant) en boven elke kubus zijn nummer; die doos wordt kubus 3. Daarna de app Zone op de desktop "
                + "(de kaart van bovenaf, de getallen, per kant - en +). De stroom valt uit: de rand wordt rood "
                + "(niemand beschermt nu), en weer blauw. Dan is de basis van iemand anders (Lotte): jij ziet de rand "
                + "niet meer. Je slaat op de muur: onderaan 'Protected zone' en een piepje, het blok breekt niet. Een "
                + "steen neerzetten: lukt niet. Een ladder: lukt wel (klimmen mag altijd, net als in het echt).",
                GameType.SURVIVAL, Vec3.atBottomCenterOf(ZONE_BASE.offset(-6, 0, -5)), -40, 8, s -> {
            s.onServer(0, ShowcaseFeatures::buildZoneBase)
                    .pause(90)
                    // De Zone Marker: een oranje doos, die kubus 3 wordt.
                    .onServer(0, player -> {
                        ItemStack marker = new ItemStack(ModItems.ZONE_MARKER.get());
                        marker.set(com.senne.imttech.registry.ModDataComponents.ZONE_SELECTION.get(),
                                com.senne.imttech.security.zone.ZoneSelection.EMPTY
                                        .withFirst(ZONE_BASE.offset(12, -1, 2)).withSecond(ZONE_BASE.offset(16, 4, 7)));
                        player.setItemInHand(InteractionHand.MAIN_HAND, marker);
                    })
                    .stand(0, ZONE_BASE.getX() + 22.5, ZONE_BASE.getY(), ZONE_BASE.getZ() - 4.5, 50, 10)
                    .pause(80)
                    .onServer(0, player -> {
                        com.senne.imttech.security.SecurityCoreBlockEntity core = zoneCore(player);
                        if (core != null) {
                            java.util.List<com.senne.imttech.security.zone.ZoneBox> boxes = new java.util.ArrayList<>(core.zoneBoxes());
                            boxes.add(com.senne.imttech.security.zone.ZoneBox.of(ZONE_BASE.offset(12, -1, 2), ZONE_BASE.offset(16, 4, 7)));
                            core.setZoneBoxes(boxes);
                        }
                        player.getMainHandItem().set(com.senne.imttech.registry.ModDataComponents.ZONE_SELECTION.get(),
                                com.senne.imttech.security.zone.ZoneSelection.EMPTY);
                    })
                    .pause(60)
                    // De app Zone op de desktop van de kern.
                    .onServer(0, player -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY))
                    .stand(0, ZONE_BASE.getX() + 6.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 6.5, 90, 20)
                    .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, ZONE_BASE.offset(4, 0, 5)))
                    .step(10, () -> desktop(os -> os.tourZone(2)))
                    .pause(110)
                    .close(10)
                    // Geen stroom: rood. Dan weer stroom: blauw.
                    .stand(0, ZONE_BASE.getX() - 5.5, ZONE_BASE.getY(), ZONE_BASE.getZ() - 4.5, -40, 8)
                    .onServer(0, player -> player.serverLevel().setBlockAndUpdate(ZONE_BASE.offset(4, -1, 5), Blocks.STONE.defaultBlockState()))
                    .pause(90)
                    .onServer(0, ShowcaseFeatures::powerZoneBase)
                    .pause(70)
                    // De basis is van Lotte: jij bent hier een vreemde.
                    .onServer(0, ShowcaseFeatures::giveZoneBaseToLotte)
                    .stand(0, ZONE_BASE.getX() + 4.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 12.5, 180, 0)
                    .pause(50)
                    .tap(0, () -> Minecraft.getInstance().options.keyAttack)
                    .pause(60)
                    .onServer(0, player -> player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COBBLESTONE, 16)))
                    .pause(20)
                    .tap(0, () -> Minecraft.getInstance().options.keyUse)
                    .pause(60)
                    .onServer(0, player -> player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LADDER, 16)))
                    .pause(20)
                    .tap(0, () -> Minecraft.getInstance().options.keyUse)
                    .pause(60)
                    .onServer(0, player -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        });

        add("Zone autoscan", "Hoofdstuk 45: de Autoscan (in survival). Naast de basis staat een dichte bunker van stenen "
                + "bakstenen. Je staat erin met een Zone Marker zonder hoeken en doet sluipen + rechtsklik: 'Autoscan "
                + "(closed rooms + 3 blocks of wall): 1 box added', en de blauwe rand ligt om de kamer en drie blokken "
                + "muur eromheen (de rand zie je met de marker in je hand). Dan naar de open basis (geen dak): de app "
                + "Zone op de kern, knop Autoscan: 'open base: a box around N built blocks + 3' en één doos om het hele "
                + "gebouw.",
                GameType.SURVIVAL, Vec3.atBottomCenterOf(ZONE_BASE.offset(-8, 0, 4)), 0, 10, s -> {
            BlockPos bunker = ZONE_BASE.offset(-12, 0, 0);
            s.onServer(0, ShowcaseFeatures::buildZoneBase)
                    .onServer(0, player -> {
                        ServerLevel level = player.serverLevel();
                        for (BlockPos pos : BlockPos.betweenClosed(bunker, bunker.offset(8, 6, 8))) {
                            boolean shell = pos.getX() == bunker.getX() || pos.getX() == bunker.getX() + 8 || pos.getY() == bunker.getY()
                                    || pos.getY() == bunker.getY() + 6 || pos.getZ() == bunker.getZ() || pos.getZ() == bunker.getZ() + 8;
                            level.setBlockAndUpdate(pos, shell ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        }
                        level.setBlockAndUpdate(bunker.offset(4, 5, 4), Blocks.GLOWSTONE.defaultBlockState());
                        // De zone van de basis zelf eerst weg: zo zie je wat de scan vindt.
                        if (level.getBlockEntity(ZONE_BASE.offset(4, 0, 5)) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
                            core.setZoneBoxes(java.util.List.of());
                        }
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ZONE_MARKER.get()));
                    })
                    .pause(40)
                    .stand(0, bunker.getX() + 4.5, bunker.getY() + 1, bunker.getZ() + 2.5, 0, 10)
                    .pause(30)
                    // Sluipen + rechtsklik zonder hoeken: de Autoscan vanuit de kamer.
                    .onServer(0, player -> com.senne.imttech.security.zone.ZoneMarkerItem.autoscan(player.serverLevel(), player,
                            bunker.offset(4, 1, 2), null))
                    .pause(80)
                    .stand(0, bunker.getX() + 14.5, bunker.getY() + 2, bunker.getZ() + 14.5, 135, 18)
                    .pause(60)
                    // Dan de open basis: de app Zone, knop Autoscan.
                    .stand(0, ZONE_BASE.getX() + 4.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 4.5, 180, 10)
                    .onServer(10, player -> com.senne.imttech.security.os.SecurityOs.open(player, ZONE_BASE.offset(4, 0, 5)))
                    .step(15, () -> {
                        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                            os.tourZone(0);
                        }
                    })
                    .pause(30)
                    .onServer(0, player -> {
                        if (player.serverLevel().getBlockEntity(ZONE_BASE.offset(4, 0, 5)) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
                            com.senne.imttech.security.zone.ZoneApp.handle(player, core, "zone_scan", new net.minecraft.nbt.CompoundTag(), false);
                        }
                    })
                    .pause(80)
                    .close(10)
                    .stand(0, ZONE_BASE.getX() + 6.5, ZONE_BASE.getY() + 3, ZONE_BASE.getZ() + 20.5, 180, 15)
                    .pause(80);
        });

        add("Remote Frag", "Hoofdstuk 35 (in plaats van C4): de Remote Frag (in survival). Eerst ben jij de eigenaar "
                + "van de basis: iemand plakt een Remote Frag op je muur. I.M.T zegt waar ('Remote Frag planted at your "
                + "zone', zonder naam) en je ziet een knipperend rood bordje 'FRAG!'. Met je hand krijg je hem er niet af; "
                + "met een schaar knip je de draad door: hij valt eruit, jij hebt hem nu. Dan ben je de inbreker (de basis "
                + "is van Lotte), met Remote Frags in je hand en de Frag Trigger in je andere hand (rode bordjes 'FRAG' boven "
                + "je ladingen). Je plakt er twee boven elkaar op de stenen muur, loopt achteruit en drukt: een gat van twee "
                + "hoog, met barsten eromheen. Daarna de obsidiaan muur aan de westkant: na de eerste, tweede en derde frag "
                + "staat het obsidiaan er nog (barsten zie je bijna niet op obsidiaan), na de vierde is er een gat.",
                GameType.SURVIVAL, Vec3.atBottomCenterOf(ZONE_BASE.offset(4, 0, 15)), 180, 5, s -> {
            BlockPos wall = ZONE_BASE.offset(3, 1, 9);
            BlockPos obsidian = ZONE_BASE.offset(2, 1, 5);
            s.onServer(0, ShowcaseFeatures::buildZoneBase)
                    .pause(40)
                    // Iemand plakt een Remote Frag op jouw muur: het alarm.
                    .onServer(0, player -> {
                        ServerLevel level = player.serverLevel();
                        BlockPos planted = wall.east().south();
                        level.setBlockAndUpdate(planted, ModBlocks.REMOTE_FRAG.get().defaultBlockState()
                                .setValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACING, Direction.SOUTH));
                        if (level.getBlockEntity(planted) instanceof com.senne.imttech.weapons.RemoteChargeBlockEntity frag) {
                            com.senne.imttech.breach.BlastAlarm.planted(level, planted, frag);
                        }
                    })
                    .pause(80)
                    // Jij knipt hem door.
                    .onServer(0, player -> player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS)))
                    .stand(0, ZONE_BASE.getX() + 4.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 12.5, 180, 3)
                    .pause(30)
                    .tap(0, () -> Minecraft.getInstance().options.keyUse)
                    .pause(60)
                    // Nu ben jij de inbreker: de basis is van Lotte. Remote Frags in je hand, de trigger in je andere hand.
                    .onServer(0, ShowcaseFeatures::giveZoneBaseToLotte)
                    .onServer(0, player -> {
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REMOTE_FRAG.get(), 6));
                        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(ModItems.FRAG_TRIGGER.get()));
                    })
                    .stand(0, ZONE_BASE.getX() + 4.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 12.5, 180, 0)
                    .pause(20)
                    .tap(0, () -> Minecraft.getInstance().options.keyUse)
                    .pause(20)
                    .stand(0, ZONE_BASE.getX() + 4.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 12.5, 180, -19)
                    .pause(10)
                    .tap(0, () -> Minecraft.getInstance().options.keyUse)
                    .pause(30)
                    // Achteruit, buiten de klap, en op de knop (je kijkt nergens naar: dan gaat de trigger).
                    .stand(0, ZONE_BASE.getX() + 4.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 21.5, 180, 3)
                    .pause(40)
                    .tap(0, () -> Minecraft.getInstance().options.keyUse)
                    .pause(90)
                    // De obsidiaan muur: vier keer een Remote Frag en de knop.
                    .stand(0, ZONE_BASE.getX() - 9.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 5.5, -90, 5)
                    .onServer(0, player -> stickRemote(player, obsidian))
                    .pause(50)
                    .tap(0, () -> Minecraft.getInstance().options.keyUse)
                    .pause(50)
                    .onServer(0, player -> stickRemote(player, obsidian))
                    .pause(30)
                    .tap(0, () -> Minecraft.getInstance().options.keyUse)
                    .pause(50)
                    .onServer(0, player -> stickRemote(player, obsidian))
                    .pause(30)
                    .tap(0, () -> Minecraft.getInstance().options.keyUse)
                    .pause(50)
                    .onServer(0, player -> stickRemote(player, obsidian))
                    .pause(30)
                    .tap(0, () -> Minecraft.getInstance().options.keyUse)
                    .pause(90)
                    .onServer(0, player -> {
                        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
                    });
        });

        add("Repairing cracks", "Hoofdstuk 11: barsten herstellen (in survival). Je basis kreeg klappen: een paar "
                + "stenen bakstenen in de zuidmuur hebben barsten (die genezen niet vanzelf). Je smeert Repair Paste "
                + "op het linker blok: de barsten zijn weg, maar onderaan staat dat het blok nog 85% sterk is. Het "
                + "blok in het midden hak je weg: je krijgt een 'Cracked Stone Bricks' (het blok met barsten erover). "
                + "Dat gaat in de Crusher achter je: in zijn scherm loopt de blauwe pijl vol ('Crushing...') en komt "
                + "er poeder uit, in de kleur van het blok. Dat poeder gaat in de Compactor ('Pressing...'): een "
                + "nieuw blok stenen bakstenen. Dat zet je terug in het gat: weer helemaal sterk.",
                GameType.SURVIVAL, Vec3.atBottomCenterOf(ZONE_BASE.offset(4, 0, 12)), 180, 8, s -> {
            BlockPos patched = ZONE_BASE.offset(3, 1, 9);
            BlockPos replaced = ZONE_BASE.offset(5, 1, 9);
            BlockPos crusher = ZONE_BASE.offset(4, 0, 15);
            BlockPos compactor = ZONE_BASE.offset(6, 0, 15);
            s.onServer(0, ShowcaseFeatures::buildZoneBase)
                    .onServer(0, player -> buildRepairMachines(player, crusher, compactor))
                    .onServer(0, player -> {
                        for (BlockPos pos : new BlockPos[] {patched, replaced, ZONE_BASE.offset(4, 2, 9),
                                ZONE_BASE.offset(6, 1, 9), ZONE_BASE.offset(3, 2, 9)}) {
                            dent(player.serverLevel(), pos);
                        }
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.REPAIR_PASTE.get(), 8));
                    })
                    .pause(70)
                    // Plamuren: de barsten van het linker blok zijn weg.
                    .stand(0, ZONE_BASE.getX() + 3.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 11.3, 180, 5)
                    .lookAt(10, Vec3.atCenterOf(patched).add(0, 0, 0.5))
                    .pause(20)
                    .tap(0, () -> Minecraft.getInstance().options.keyUse)
                    .pause(60)
                    // Weghakken: een gebarsten blok in je inventaris.
                    .onServer(0, player -> player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE)))
                    .stand(0, ZONE_BASE.getX() + 5.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 11.3, 180, 5)
                    .lookAt(10, Vec3.atCenterOf(replaced).add(0, 0, 0.5))
                    .key(10, () -> Minecraft.getInstance().options.keyAttack, true)
                    .key(15, () -> Minecraft.getInstance().options.keyAttack, false)
                    // Een stapje naar voren: zo raap je het gebarsten blok op.
                    .stand(10, ZONE_BASE.getX() + 5.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 10.6, 180, 5)
                    .pause(30)
                    // De Crusher en de Compactor achter je.
                    .stand(0, ZONE_BASE.getX() + 5.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 12.5, 0, 25)
                    .pause(20)
                    .onServer(0, player -> putInMachine(player, crusher))
                    .onServer(10, player -> openMachine(player, crusher))
                    .pause(115)
                    .onServer(0, player -> moveBetweenMachines(player, crusher, compactor))
                    .onServer(0, player -> openMachine(player, compactor))
                    .pause(115)
                    .onServer(0, player -> takeFromMachine(player, compactor))
                    .pause(20)
                    .close(10)
                    // Terugzetten: in het gat in de muur.
                    .stand(0, ZONE_BASE.getX() + 5.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 11.5, 180, 25)
                    .lookAt(10, new Vec3(replaced.getX() + 0.5, replaced.getY() + 0.02, replaced.getZ() + 0.5))
                    .pause(20)
                    .tap(0, () -> Minecraft.getInstance().options.keyUse)
                    .pause(80)
                    .onServer(0, player -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
        });

        add("Block Reinforcer", "Hoofdstuk 44: de Block Reinforcer (in survival). Twee muurtjes van kwarts voor de basis. "
                + "Je houdt de Block Reinforcer vast (met 16 Reinforcement Plates in je rugzak) en sluipt + klikt op het "
                + "linker muurtje: 'Reinforced 9 blocks (7 plates left)', en door je bril zie je een staalblauw randje om "
                + "elk verstevigd blok; het kwarts zelf ziet er precies zo uit. Dan krijgt elk muurtje dezelfde klap: "
                + "rechts (gewoon kwarts) vliegt het blok eruit, links (verstevigd, zo sterk als obsidiaan) krijgt het "
                + "alleen barsten.",
                GameType.SURVIVAL, Vec3.atBottomCenterOf(ZONE_BASE.offset(2, 0, 23)), 180, 6, s -> {
            BlockPos left = ZONE_BASE.offset(-2, 0, 17);
            BlockPos right = ZONE_BASE.offset(4, 0, 17);
            s.onServer(0, ShowcaseFeatures::buildZoneBase)
                    .onServer(0, player -> {
                        for (BlockPos base : new BlockPos[] {left, right}) {
                            for (int x = 0; x < 3; x++) {
                                for (int y = 0; y < 3; y++) {
                                    player.serverLevel().setBlockAndUpdate(base.offset(x, y, 0), Blocks.QUARTZ_BLOCK.defaultBlockState());
                                }
                            }
                        }
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BLOCK_REINFORCER.get()));
                        player.getInventory().setItem(9, new ItemStack(ModItems.REINFORCEMENT_PLATE.get(), 16));
                    })
                    .pause(50)
                    .stand(0, ZONE_BASE.getX() + 0.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 21.5, 180, 4)
                    .lookAt(10, Vec3.atCenterOf(left.offset(1, 1, 0)).add(0, 0, 0.5))
                    .pause(20)
                    // Sluipen + klik: de hele linker muur in één keer.
                    .onServer(0, player -> useReinforcer(player, left.offset(1, 1, 0), true))
                    .pause(70)
                    .stand(0, ZONE_BASE.getX() + 2.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 23.5, 180, 6)
                    .pause(30)
                    // Dezelfde klap op allebei: rechts breekt, links barst.
                    .onServer(0, player -> {
                        dent(player.serverLevel(), left.offset(1, 1, 0));
                        dent(player.serverLevel(), right.offset(1, 1, 0));
                    })
                    .pause(100);
        });

        add("Hacking and pickpocketing", "Hoofdstuk 12: inbreken (in survival). In het lab van hiervoor, met de "
                + "Bypass Kit in je hand. Eerst de keycardlezer: je kiest een kaart van de groep lab, en draait de stukjes "
                + "draad tot het blauwe signaal van CARD naar ACCEPT loopt (raakt het even een ALARM, dan knippert "
                + "alles rood). Het pad wordt groen, de lezer leest een nagemaakte kaart, en de deur gaat 3 seconden "
                + "open. Dan het codepaneel: je veegt poeder over de toetsen (lichtgele afdrukken op 1, 2, 3 en 4) en "
                + "raadt de volgorde; rechts zie je hoeveel er goed stonden en hoeveel volgordes er nog kunnen. Bij "
                + "de vierde poging: CODE CRACKED 4321, en de lamp gaat aan. Dan zakkenrollen bij Seppe: je sluipt "
                + "achter hem, en in zijn zak liggen drie kaarten tussen rommel. Je schuift de rommel opzij en trekt "
                + "een kaart naar boven (de meter NOTICED loopt wat op): hij draait om en je ziet welke het is, en hij "
                + "zit in je hotbar. De tweede keer draait Seppe zich om terwijl jij in zijn zak zit: CAUGHT!, en jij "
                + "licht op. Tot slot (hoofdstuk 33) een laserpoort van twee zenders in het lab: de kit opent de zender, "
                + "en binnenin is het donker met een kluwen van vijf draden. De zaklamp (je muis) volgt de draad van de "
                + "LENS terug naar zijn stekker, kiest die en knipt (CUT!): de wand schuift terug en de zenders vonken. "
                + "Dan als eigenaar het scherm van de zender: rood WIRES CUT, en Repair wires (1 chip) zet de draden "
                + "weer vast: de wand komt terug.",
                GameType.SURVIVAL, Vec3.atBottomCenterOf(LOGIC_LAB.offset(3, 0, 11)), 180, 10, s -> {
            BlockPos reader = LOGIC_LAB.offset(1, 1, 7);
            BlockPos panel = LOGIC_LAB.offset(5, 1, 7);
            s.onServer(0, ShowcaseFeatures::buildLogicLab)
                    .onServer(5, player -> {
                        wakeLabServer(player);
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BYPASS_KIT.get()));
                    })
                    // De keycardlezer: het stroomcircuit speelt zichzelf (een kaart van de groep lab), de deur gaat open.
                    .stand(0, LOGIC_LAB.getX() + 1.5, LOGIC_LAB.getY(), LOGIC_LAB.getZ() + 9.5, 180, 15)
                    .lookAt(10, Vec3.atCenterOf(reader))
                    .pause(30)
                    .step(0, () -> {
                        com.senne.imttech.client.bypass.CircuitScreen.autoGroup = "lab";
                        com.senne.imttech.client.bypass.CircuitScreen.autoPlay = true;
                    })
                    .onServer(0, player -> com.senne.imttech.bypass.Bypass.start(player, reader, InteractionHand.MAIN_HAND))
                    .pause(250)
                    .step(0, () -> com.senne.imttech.client.bypass.CircuitScreen.autoPlay = false)
                    .close(0)
                    .pause(30)
                    // Het codepaneel: stoffen, de volgorde raden, en de lamp gaat aan.
                    .stand(0, LOGIC_LAB.getX() + 5.5, LOGIC_LAB.getY(), LOGIC_LAB.getZ() + 9.5, 180, 15)
                    .lookAt(10, Vec3.atCenterOf(panel))
                    .pause(20)
                    .step(0, () -> com.senne.imttech.client.bypass.FingerprintScreen.autoPlay = true)
                    .onServer(0, player -> com.senne.imttech.bypass.Bypass.start(player, panel, InteractionHand.MAIN_HAND))
                    .pause(310)
                    .step(0, () -> com.senne.imttech.client.bypass.FingerprintScreen.autoPlay = false)
                    .close(0)
                    .pause(40)
                    // Zakkenrollen bij Seppe: sluipend achter hem, rommel opzij, een kaart eruit.
                    .onServer(0, ShowcaseFeatures::fillHappysPockets)
                    .stand(0, FRIEND_SPOT.getX() + 0.5, FRIEND_SPOT.getY(), FRIEND_SPOT.getZ() + 1.9, 180, 25)
                    .key(10, () -> Minecraft.getInstance().options.keyShift, true)
                    .pause(20)
                    .step(0, () -> com.senne.imttech.client.pickpocket.PocketScreen.autoPlay = true)
                    .onServer(0, ShowcaseFeatures::pickHappysPocket)
                    .pause(200)
                    .step(0, () -> com.senne.imttech.client.pickpocket.PocketScreen.autoPlay = false)
                    .close(0)
                    // Nog een keer, maar nu draait Seppe zich om: betrapt.
                    .onServer(30, player -> {
                        com.senne.imttech.pickpocket.Pickpocket.clear();
                        pickHappysPocket(player);
                    })
                    .pause(50)
                    .onServer(0, player -> turnHappy(player, 0))
                    .pause(90)
                    .close(0)
                    .key(0, () -> Minecraft.getInstance().options.keyShift, false)
                    .onServer(40, player -> turnHappy(player, 180))
                    // Hoofdstuk 33: een laserpoort in het lab. De kit opent de zender: de zaklamp volgt de draad van de
                    // LENS terug naar zijn stekker en knipt hem door, en de wand verdwijnt.
                    .onServer(10, ShowcaseFeatures::buildLabLaser)
                    .stand(0, LAB_LASER.getX() + 3.5, LAB_LASER.getY(), LAB_LASER.getZ() + 2.5, 135, 15)
                    .lookAt(10, Vec3.atCenterOf(LAB_LASER.above()).add(1.5, 0, 0))
                    .pause(50)
                    .step(0, () -> com.senne.imttech.client.bypass.WireScreen.autoPlay = true)
                    .onServer(0, player -> com.senne.imttech.bypass.Bypass.start(player, LAB_LASER, InteractionHand.MAIN_HAND))
                    .pause(175)
                    .step(0, () -> com.senne.imttech.client.bypass.WireScreen.autoPlay = false)
                    .close(0)
                    .pause(50)
                    // Als eigenaar (sneak + klik): het scherm zegt WIRES CUT, en Repair wires kost een Computer Chip.
                    .onServer(0, player -> com.senne.imttech.shield.LaserEmitters.open(player, LAB_LASER))
                    .pause(60)
                    .onServer(0, player -> com.senne.imttech.shield.LaserEmitters.handleAction(player,
                            new com.senne.imttech.network.LaserActionPayload(LAB_LASER,
                                    com.senne.imttech.network.LaserActionPayload.Op.REPAIR, 0)))
                    .pause(40)
                    .close(0)
                    .pause(50);
        });

        add("Guard robots", "Hoofdstuk 21: een guard robot bouwen. Een kleine robotwerkplaats: links de Robot Assembler, "
                + "rechts twee Robot Docks (laadstations) met een warmtegenerator ertussen, achteraan een "
                + "beveiligingskern. Eerst het scherm van de assembler: links vier onderdelen (een Commando-hoofd, "
                + "een Super Battle-lijf, Trainer-armen en Battle-benen), in het midden draait de robot zoals hij "
                + "wordt, rechts de printplaat; dan Assemble: de robot ligt rechts als item. Die zet je op het "
                + "linker dock (een gemengde robot, hij heet Guard 1); op het rechter staat een Commando met een "
                + "halflege batterij, die laadt: luister naar het heel zachte zoemen. Dan gaat Guard 1 aan: het "
                + "opstartgeluid (servo's en twee piepjes), zijn lichtjes gaan aan (het oranje oog) en hij stapt "
                + "twee blokken uit zijn dock (luister naar de servo-stapjes). Dan sneak + rechtsklik: zijn scherm "
                + "(naam, toestand, onderdelen, batterij, dock, Rename, Switch off, Pick up). Uit: hij loopt "
                + "terug, gaat precies in zijn dock staan en zijn lichtjes gaan uit. Tot slot de app Robots op de "
                + "desktop: beide robots met hun toestand en batterij. (18 oktober) Daaronder twee robots die er niet "
                + "meer zijn, grijs 'far away': Old guard krijgt Delete, dan 'Sure?', en weg is hij ('Old guard "
                + "deleted'); Lost guard verdwijnt een paar seconden later vanzelf, omdat zijn stuk wereld geladen is "
                + "en hij er niet staat.",
                Vec3.atBottomCenterOf(ROBOT_BAY.offset(5, 0, 9)), 180, 12, s -> {
            s.onServer(0, ShowcaseFeatures::buildRobotBay)
                    .pause(30)
                    // De assembler, vol: eerst kijken, dan Assemble.
                    .onServer(0, player -> {
                        if (player.serverLevel().getBlockEntity(ROBOT_BAY.offset(2, 0, 2))
                                instanceof com.senne.imttech.robot.RobotAssemblerBlockEntity be) {
                            player.openMenu(be, ROBOT_BAY.offset(2, 0, 2));
                        }
                    })
                    .pause(90)
                    .onServer(0, player -> {
                        if (player.serverLevel().getBlockEntity(ROBOT_BAY.offset(2, 0, 2))
                                instanceof com.senne.imttech.robot.RobotAssemblerBlockEntity be) {
                            be.assemble();
                        }
                    })
                    .pause(60)
                    .close(10)
                    // Guard 1 op het linker dock, de Commando (halfvol) op het rechter.
                    .onServer(0, player -> {
                        if (player.serverLevel().getBlockEntity(ROBOT_BAY.offset(2, 0, 2))
                                instanceof com.senne.imttech.robot.RobotAssemblerBlockEntity be) {
                            ItemStack built = be.items().getItem(com.senne.imttech.robot.RobotAssemblerBlockEntity.ROBOT).copy();
                            be.items().setItem(com.senne.imttech.robot.RobotAssemblerBlockEntity.ROBOT, ItemStack.EMPTY);
                            placeRobot(player, ROBOT_BAY.offset(6, 0, 2), built);
                        }
                        com.senne.imttech.robot.RobotStyle c = com.senne.imttech.robot.RobotStyle.COMMANDO;
                        placeRobot(player, ROBOT_BAY.offset(8, 0, 2), com.senne.imttech.robot.GuardRobotItem.create(
                                new com.senne.imttech.robot.RobotData(new com.senne.imttech.robot.RobotParts(c, c, c, c).pack(),
                                        com.senne.imttech.bot.BotBoard.EMPTY, "", 0.4f)));
                    })
                    .pause(80)
                    // Aan: hij stapt uit zijn dock.
                    .onServer(0, player -> robotAt(player, ROBOT_BAY.offset(6, 0, 2), robot -> robot.setActive(true)))
                    .pause(80)
                    .onServer(0, player -> robotNear(player, ROBOT_BAY.offset(6, 0, 4),
                            robot -> com.senne.imttech.robot.Robots.open(player, robot)))
                    .pause(110)
                    .close(10)
                    // Uit: hij loopt terug en gaat in zijn dock staan.
                    .onServer(0, player -> robotNear(player, ROBOT_BAY.offset(6, 0, 4), robot -> robot.setActive(false)))
                    .pause(100)
                    // De app Robots.
                    .stand(0, ROBOT_BAY.getX() + 10.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 8.5, 180, 20)
                    .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, ROBOT_BAY.offset(10, 0, 6)))
                    .step(10, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.ROBOTS)))
                    .pause(100)
                    // 18 oktober: twee robots die er niet meer zijn. Old guard haal je weg met Delete (eerst "Sure?");
                    // Lost guard ruimt de server zelf op (3 seconden stil terwijl zijn stuk wereld geladen is).
                    .onServer(0, player -> {
                        ghostRobot(player, ROBOT_BAY.offset(4, 0, 6), "Old guard");
                        ghostRobot(player, ROBOT_BAY.offset(3, 0, 7), "Lost guard");
                    })
                    .pause(15)
                    .step(0, () -> desktop(os -> os.tourRobotsAskDelete()))
                    .pause(25)
                    .onServer(0, player -> deleteRobot(player, ROBOT_BAY.offset(10, 0, 6), "Old guard"))
                    .pause(120)
                    .close(20);
        });

        add("Soldering modules", "Hoofdstuk 22: modules solderen. Dezelfde werkplaats; op de printplaat van de Commando "
                + "rechts zitten al een Scan (pin 1) en een Laser (pin 3), en er loopt een verstopte soldeerbrug van pin 3 "
                + "naar pin 4. Eerst het bordscherm (Board in het robotscherm): de hittebalk boven pin 2 (je houdt er "
                + "een Camera op; groen vak = goed). Dan Inspect: de brug wordt een roze balk, met 'switching on now "
                + "would short!'. Dan zet hij aan zonder dat iemand de brug weghaalde: kortsluiting! Vonken, rook, een "
                + "knal; de Laser op pin 3 is nu een Burnt Module (I.M.T zegt het, rood) en hij blijft uit. Daarna "
                + "het bord nog eens: op pin 3 de verbrande module, en de brug is weg. Tot slot de vier robots van "
                + "dichtbij met alle modules: een schoteltje op hun hoofd (Scan), een camera opzij, een cyaan lensstrook "
                + "(Face Recognition), een sensor op de borst (Distance), een laser rechts en een geweer links.",
                Vec3.atBottomCenterOf(ROBOT_BAY.offset(5, 0, 9)), 180, 12, s -> {
            s.onServer(0, ShowcaseFeatures::buildRobotBay)
                    .onServer(0, player -> {
                        com.senne.imttech.robot.RobotStyle c = com.senne.imttech.robot.RobotStyle.COMMANDO;
                        placeRobot(player, ROBOT_BAY.offset(8, 0, 2), com.senne.imttech.robot.GuardRobotItem.create(
                                new com.senne.imttech.robot.RobotData(new com.senne.imttech.robot.RobotParts(c, c, c, c).pack(),
                                        com.senne.imttech.bot.BotBoard.EMPTY.with(1, "scan").with(3, "laser").withBridge(3), "", 1.0f)));
                        player.getInventory().add(new ItemStack(ModItems.SOLDERING_IRON.get()));
                        player.getInventory().add(new ItemStack(ModItems.robotModule(com.senne.imttech.bot.BotModule.CAMERA)));
                    })
                    .pause(30)
                    .step(0, () -> showBoard(ROBOT_BAY.offset(8, 0, 2), board -> board.tourHeat(
                            com.senne.imttech.bot.BotModule.CAMERA, 2,
                            Math.round(com.senne.imttech.config.ImtConfig.Robots.SOLDER_FULL_TICKS * 0.6f))))
                    .pause(80)
                    .step(0, () -> {
                        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.robot.BoardScreen board) {
                            board.tourHeat(com.senne.imttech.bot.BotModule.CAMERA, -1, 0);
                            board.tourInspect(true);
                        }
                    })
                    .pause(80)
                    .close(10)
                    // Aan, met de brug: kortsluiting (voor de showcase brandt hij niet helemaal af).
                    .onServer(0, player -> robotAt(player, ROBOT_BAY.offset(8, 0, 2), robot -> {
                        float burn = com.senne.imttech.config.ImtConfig.Robots.BURN_CHANCE;
                        com.senne.imttech.config.ImtConfig.Robots.BURN_CHANCE = 0.0f;
                        robot.setActive(true);
                        com.senne.imttech.config.ImtConfig.Robots.BURN_CHANCE = burn;
                    }))
                    .pause(80)
                    .step(0, () -> showBoard(ROBOT_BAY.offset(8, 0, 2), board -> board.tourInspect(true)))
                    .pause(80)
                    .close(10)
                    // De vier stijlen met alle modules.
                    .onServer(0, player -> {
                        com.senne.imttech.bot.BotBoard all = com.senne.imttech.bot.BotBoard.EMPTY;
                        String[] ids = {"scan", "distance", "face_recognition", "pathfinding", "laser", "gun", "solar_panel", "camera"};
                        for (int i = 0; i < ids.length; i++) {
                            all = all.with(i + 1, ids[i]);
                        }
                        com.senne.imttech.robot.RobotStyle[] styles = com.senne.imttech.robot.RobotStyle.values();
                        for (int i = 0; i < styles.length; i++) {
                            com.senne.imttech.robot.RobotStyle st = styles[i];
                            if (DevWorld.spawnStill(player.serverLevel(), com.senne.imttech.registry.ModEntities.GUARD_ROBOT.get(),
                                    ROBOT_BAY.offset(1 + i * 3, 0, 7), 0) instanceof com.senne.imttech.robot.GuardRobotEntity robot) {
                                robot.setParts(new com.senne.imttech.robot.RobotParts(st, st, st, st));
                                robot.setBoard(all);
                                robot.setActive(true);
                            }
                        }
                    })
                    .stand(0, ROBOT_BAY.getX() + 5.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 10.5, 180, 8)
                    .pause(100);
        });

        add("Robot programs", "Hoofdstuk 23: robots programmeren. Dezelfde werkplaats, nu met een serverrack naast de "
                + "kern. Eerst de app Logic met het robotprogramma Patrol (geel R in de lijst): when started, dan steeds "
                + "3 vooruit, een kwartslag rechts en send(\"corner\"); en when ordered(\"wave\"): een paar keer links-rechts "
                + "draaien. Rechts de Help met de woorden van een robot. Dan de app Robots: de knop Program, je kiest Patrol, "
                + "en de taakbalk zegt 'Uploaded Patrol to Guard 1'. Dan gaat hij aan: hij stapt uit zijn dock en loopt "
                + "precies zijn vierkantje, blok voor blok (luister naar de stapjes). Op de server staat het diagram "
                + "Corners (when message(\"corner\")): na elke vier hoeken zegt I.M.T 'Guard 1 walked a full round'. "
                + "Tot slot geeft de server hem de opdracht wave: hij stopt midden in zijn rondje, draait links-rechts, en "
                + "loopt dan verder waar hij was.",
                Vec3.atBottomCenterOf(ROBOT_BAY.offset(5, 0, 11)), 180, 25, s -> {
            s.onServer(0, ShowcaseFeatures::buildRobotBay)
                    .onServer(0, player -> {
                        buildBayServer(player);
                        placeRobot(player, ROBOT_BAY.offset(6, 0, 2), com.senne.imttech.robot.GuardRobotItem.create(
                                new com.senne.imttech.robot.RobotData(new com.senne.imttech.robot.RobotParts(
                                        com.senne.imttech.robot.RobotStyle.COMMANDO, com.senne.imttech.robot.RobotStyle.BATTLE,
                                        com.senne.imttech.robot.RobotStyle.BATTLE, com.senne.imttech.robot.RobotStyle.BATTLE).pack(),
                                        com.senne.imttech.bot.BotBoard.EMPTY, "Guard 1", 1.0f)));
                    })
                    .pause(50)
                    // De app Logic: het robotprogramma Patrol, met de Help.
                    .stand(0, ROBOT_BAY.getX() + 10.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 8.5, 180, 20)
                    .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, ROBOT_BAY.offset(10, 0, 6)))
                    .step(10, () -> desktop(os -> {
                        os.tourLogicNew(com.senne.imttech.logic.program.ProgramKind.ROBOT);
                        os.tourLogic("Patrol", BAY_PATROL, true);
                    }))
                    .pause(120)
                    // De app Robots: Program, en Patrol naar Guard 1.
                    .step(0, () -> desktop(os -> os.tourRobotsProgram()))
                    .pause(60)
                    .onServer(0, player -> {
                        if (player.serverLevel().getBlockEntity(ROBOT_BAY.offset(10, 0, 6))
                                instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
                            robotAt(player, ROBOT_BAY.offset(6, 0, 2), robot -> {
                                net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
                                args.putString("id", robot.getUUID().toString());
                                args.putString("program", "Patrol");
                                com.senne.imttech.security.os.SecurityOs.handleAction(player, new com.senne.imttech.network.OsActionPayload(
                                        ROBOT_BAY.offset(10, 0, 6), "robot_program", args));
                            });
                        }
                    })
                    .step(0, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.ROBOTS)))
                    .pause(80)
                    .close(10)
                    // Aan: hij stapt uit zijn dock en loopt zijn vierkantje.
                    .onServer(0, player -> robotAt(player, ROBOT_BAY.offset(6, 0, 2), robot -> robot.setActive(true)))
                    .stand(0, ROBOT_BAY.getX() + 5.5, ROBOT_BAY.getY() + 2, ROBOT_BAY.getZ() + 11.5, 180, 30)
                    .pause(320)
                    // De server geeft hem een opdracht: wave.
                    .onServer(0, player -> {
                        if (player.serverLevel().getBlockEntity(ROBOT_BAY.offset(10, 0, 6))
                                instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
                            com.senne.imttech.bot.BotLink.order(player.serverLevel(), core, "Guard 1", "wave", null);
                        }
                    })
                    .pause(160);
        });

        add("Robot senses and weapons", "Hoofdstuk 24: zien en schieten. In de werkplaats staan twee Commando-robots naar "
                + "het noorden te kijken, met elk een husk vijf blokken voor zich. De linker heeft een Laser (pin 3), de rechter "
                + "een Gun (pin 4) met 6 Robot Ammo; hun programma: write(pin, true), dus het wapen staat aan en schiet zodra "
                + "het klaar is. Kijk naar de oranjerode laserstraal uit de rechterarm en het gele kogelspoortje uit de "
                + "linkerarm (luister naar de 'pew' en de knal); de husks vallen om. (19 oktober) Dan krijgt de derde robot, "
                + "in het midden, een opdracht met een plek: aim(order.x, order.y, order.z). Hij draait zich naar links en "
                + "schiet de husk daar neer, op de plek die jij kiest. Dan schiet jij met de Ion Blaster op de linker robot: "
                + "een dikke blauwe straal, en hij valt neer op zijn rug (zonder schade: na 60 seconden staat hij weer op). "
                + "Op de desktop de code van die derde robot in de app Logic, en de app Cameras: beide robots hebben een "
                + "Camera-module, dus ze staan ertussen met ROBOT erbij (de neergeschoten robot geeft NO SIGNAL); het grote "
                + "beeld kijkt door de ogen van de rechter robot.",
                Vec3.atBottomCenterOf(ROBOT_BAY.offset(5, 0, 11)), 180, 10, s -> {
            s.onServer(0, ShowcaseFeatures::buildRobotBay)
                    .onServer(0, player -> {
                        buildBayServer(player);
                        ServerLevel level = player.serverLevel();
                        String[] weapons = {"laser", "gun"};
                        for (int i = 0; i < weapons.length; i++) {
                            BlockPos pos = ROBOT_BAY.offset(4 + i * 3, 0, 9);
                            DevWorld.spawnStill(level, net.minecraft.world.entity.EntityType.HUSK, pos.north(5), 0);
                            if (DevWorld.spawnStill(level, com.senne.imttech.registry.ModEntities.GUARD_ROBOT.get(), pos, 180)
                                    instanceof com.senne.imttech.robot.GuardRobotEntity robot) {
                                com.senne.imttech.robot.RobotStyle c = com.senne.imttech.robot.RobotStyle.COMMANDO;
                                robot.setParts(new com.senne.imttech.robot.RobotParts(c, c, c, c));
                                robot.setOwner(player);
                                robot.setCustomName(net.minecraft.network.chat.Component.literal(i == 0 ? "Laser guard" : "Gun guard"));
                                int pin = i == 0 ? 3 : 4;
                                robot.setBoard(com.senne.imttech.bot.BotBoard.EMPTY.with(pin, weapons[i]).with(6, "camera"));
                                robot.setAmmo(6);
                                robot.setProgram(program("Shoot", "when started:\n    write(pin(" + pin + "), true)\n",
                                        com.senne.imttech.logic.program.ProgramKind.ROBOT));
                                robot.setActive(true);
                            }
                        }
                        // De derde robot richt op wat zijn programma kiest (19 oktober).
                        DevWorld.spawnStill(level, net.minecraft.world.entity.EntityType.HUSK, ROBOT_BAY.offset(1, 0, 6), 0);
                        if (DevWorld.spawnStill(level, com.senne.imttech.registry.ModEntities.GUARD_ROBOT.get(), ROBOT_BAY.offset(5, 0, 6), 180)
                                instanceof com.senne.imttech.robot.GuardRobotEntity robot) {
                            com.senne.imttech.robot.RobotStyle c = com.senne.imttech.robot.RobotStyle.COMMANDO;
                            robot.setParts(new com.senne.imttech.robot.RobotParts(c, c, c, c));
                            robot.setOwner(player);
                            robot.setCustomName(net.minecraft.network.chat.Component.literal("Aim guard"));
                            robot.setBoard(com.senne.imttech.bot.BotBoard.EMPTY.with(3, "laser"));
                            robot.setProgram(program("Aim guard", AIM_GUARD, com.senne.imttech.logic.program.ProgramKind.ROBOT));
                            robot.setActive(true);
                        }
                    })
                    .pause(120)
                    .onServer(0, player -> robotNear(player, ROBOT_BAY.offset(5, 0, 6), robot ->
                            robot.brain().order("fire", ROBOT_BAY.offset(1, 1, 6))))
                    .pause(100)
                    // De Ion Blaster: jij schiet de linker robot neer.
                    .onServer(0, player -> robotNear(player, ROBOT_BAY.offset(4, 0, 9), robot -> {
                        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, robot.getBoundingBox().getCenter());
                        com.senne.imttech.robot.IonBlasterItem.fire(player.serverLevel(), player);
                    }))
                    .pause(100)
                    // De app Cameras: de robots met een camera, en het grote beeld van de rechter. (Even wachten: een
                    // nieuwe kern opent eerst zelf Settings, voor een wachtwoord.)
                    .stand(0, ROBOT_BAY.getX() + 10.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 8.5, 180, 20)
                    .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, ROBOT_BAY.offset(10, 0, 6)))
                    .step(30, () -> desktop(os -> os.tourLogic("Aim guard", AIM_GUARD, false)))
                    .pause(120)
                    .step(0, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.CAMERAS)))
                    .pause(40)
                    .step(0, () -> desktop(os -> os.tourRobotCamera("Gun guard")))
                    .pause(240)
                    .close(20);
        });

        add("Security drones", "Hoofdstuk 25: programmeerbare security drones. In de werkplaats staan nu ook twee Drone Docks, "
                + "elk met een warmtegenerator ernaast; op elk zet jij een Security Drone met het item (Drone 1 links, Drone 2 "
                + "rechts). Eerst het scherm van Drone 1 (sneak + rechtsklik): zoals bij een robot, maar zonder onderdelen; dan "
                + "zijn bord met Scan, Face Recognition, Laser en Camera (zie je de modules ook op de drone: een radarkoepel, een "
                + "cyaan lensstrook, een laserloop onder zijn buik, een camera onder zijn neus). Dan de app Logic: na + staat er nu "
                + "ook Drone program, en het droneprogramma Hover patrol (een lichtblauwe D in de lijst): eerst up(2), dan steeds "
                + "3 vooruit en een kwartslag rechts, en onder 30% batterij charge(50). De app Robots toont beide drones met "
                + "'drone' achter hun naam. Dan gaan ze aan: ze stijgen op van hun dock, nog 2 hoger, en vliegen elk hun "
                + "vierkantje, blok voor blok, met draaiende rotors en cyaan lichtjes. (19 oktober) Luister naar de rotors: een "
                + "heel zacht 'brrr' dat bij het opstijgen opspint, iets hoger klinkt als ze vliegen, en wegzakt als een drone "
                + "landt of valt. Drone 2 heeft maar 33% batterij: na een paar zijden vliegt hij terug, landt op zijn dock (zijn "
                + "rotors stoppen), laadt tot 50% (heel zacht zoemen) en vliegt terug naar waar hij was. Daarna kijk je in de app "
                + "Cameras door de camera van Drone 1 (een beetje naar beneden), schiet je hem neer met de Ion Blaster (hij valt "
                + "scheef op de grond), en zet je in de app Devices de schakelaar Drones uit: Drone 2 vliegt naar zijn dock en "
                + "landt.",
                Vec3.atBottomCenterOf(ROBOT_BAY.offset(3, 0, 15)), 180, 15, s -> {
            BlockPos core = ROBOT_BAY.offset(10, 0, 6);
            s.onServer(0, ShowcaseFeatures::buildRobotBay)
                    .onServer(0, player -> {
                        buildBayServer(player);
                        buildDroneDocks(player);
                        placeDrone(player, ROBOT_BAY.offset(1, 0, 7));
                        placeDrone(player, ROBOT_BAY.offset(5, 0, 7));
                        if (player.serverLevel().getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                            be.logic().put(null, program("Hover patrol", HOVER_PATROL, com.senne.imttech.logic.program.ProgramKind.DRONE));
                        }
                        droneAt(player, ROBOT_BAY.offset(1, 0, 7), drone -> {
                            drone.setBoard(com.senne.imttech.bot.BotBoard.EMPTY.with(1, "scan").with(2, "face_recognition")
                                    .with(3, "laser").with(4, "camera"));
                            drone.setProgram(program("Hover patrol", HOVER_PATROL, com.senne.imttech.logic.program.ProgramKind.DRONE));
                        });
                        droneAt(player, ROBOT_BAY.offset(5, 0, 7), drone -> {
                            drone.setBoard(com.senne.imttech.bot.BotBoard.EMPTY.with(1, "solar_panel").with(2, "distance")
                                    .with(3, "pathfinding").with(4, "gun"));
                            drone.setProgram(program("Hover patrol", HOVER_PATROL, com.senne.imttech.logic.program.ProgramKind.DRONE));
                        });
                    })
                    .pause(50)
                    // Het scherm van Drone 1, en zijn bord.
                    .onServer(0, player -> droneAt(player, ROBOT_BAY.offset(1, 0, 7),
                            drone -> com.senne.imttech.robot.Robots.open(player, drone)))
                    .pause(80)
                    .step(0, () -> showDroneBoard(ROBOT_BAY.offset(1, 0, 7)))
                    .pause(80)
                    .close(10)
                    // De app Logic: + met Drone program, en het programma Hover patrol met zijn Help. Dan de app Robots.
                    .stand(0, ROBOT_BAY.getX() + 10.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 8.5, 180, 20)
                    .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, core))
                    .step(30, () -> desktop(os -> os.tourLogicChooseKind()))
                    .pause(60)
                    .step(0, () -> desktop(os -> {
                        os.tourLogicNew(com.senne.imttech.logic.program.ProgramKind.DRONE);
                        os.tourLogic("Hover patrol", HOVER_PATROL, true);
                    }))
                    .pause(120)
                    .step(0, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.ROBOTS)))
                    .pause(80)
                    .close(10)
                    // Aan: ze stijgen op en vliegen hun vierkantje. Drone 2 moet al snel laden.
                    .stand(0, ROBOT_BAY.getX() + 3.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 15.5, 180, 15)
                    .onServer(0, player -> {
                        droneAt(player, ROBOT_BAY.offset(1, 0, 7), drone -> drone.setActive(true));
                        droneAt(player, ROBOT_BAY.offset(5, 0, 7), drone -> {
                            drone.setBattery(drone.maxBattery() * 0.33);
                            drone.setActive(true);
                        });
                    })
                    .pause(520)
                    // Door de camera van Drone 1.
                    .stand(0, ROBOT_BAY.getX() + 10.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 8.5, 180, 20)
                    .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, core))
                    .step(30, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.CAMERAS)))
                    .pause(30)
                    .step(0, () -> desktop(os -> os.tourRobotCamera("Drone 1")))
                    .pause(150)
                    .close(10)
                    // De Ion Blaster: Drone 1 valt neer.
                    .stand(0, ROBOT_BAY.getX() + 3.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 15.5, 180, 15)
                    .onServer(10, player -> droneNear(player, ROBOT_BAY.offset(0, 2, 8), drone -> {
                        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, drone.getBoundingBox().getCenter());
                        com.senne.imttech.robot.IonBlasterItem.fire(player.serverLevel(), player);
                    }))
                    .pause(100)
                    // De schakelaar Drones (app Devices): Drone 2 vliegt naar zijn dock en landt.
                    .stand(0, ROBOT_BAY.getX() + 10.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 8.5, 180, 20)
                    .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, core))
                    .step(30, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.DEVICES)))
                    .pause(30)
                    .onServer(0, player -> networkSwitch(player, core, "drones", false))
                    .pause(80)
                    .close(10)
                    .stand(0, ROBOT_BAY.getX() + 3.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 15.5, 180, 15)
                    .pause(160);
        });

        add("Training", "Hoofdstuk 26: training zoals in een trainingskamp. In de werkplaats staat nu vooraan links een Training "
                + "Start-blok, en achteraan een Training Goal-blok (een geblokte finishvlag) met links een Training End-blok (met "
                + "een redstonelamp erop) en rechts een donkere Super Battle-robot (squad Wave 2). Links en rechts in het midden "
                + "staan twee witte Trainer-robots (squad Wave 1). Alle robots staan uit. Eerst het instelscherm van Start (sneak + "
                + "klik): naam Bootcamp, Training shots, 5 lives, geen tijdslimiet. Dan de mini-editor van End: when trainee_hit: "
                + "say(\"You're hit, cadet\"). Dan klik jij op Start (in survival): I.M.T telt af (3, 2, 1, go!; met Voice "
                + "text uit hoor je het alleen) en je diagram Waves zegt 'Good luck' en zet Wave 1 aan. De Trainers schieten alleen "
                + "op de trainee: geen schade, maar een blauwe flits rond je lijf, een 'zap', je bent even trager en een leven eraf "
                + "(onderaan 'Hit by Trainer 1: 4 lives left'); de lamp op het End-blok knippert en I.M.T zegt 'You're hit, "
                + "cadet'. Jij schiet ze neer met de Ion Blaster: ze blijven liggen zolang de training duurt. Ligt Wave 1 helemaal "
                + "neer, dan zet je diagram Wave 2 aan (when squad_down) en schiet de Super Battle ook. Dan sta je op het "
                + "Goal-blok: geslaagd! I.M.T geeft het rapport (tijd, treffers en door wie, 2 robots down), de Trainers "
                + "staan op en de robots gaan uit. Tot slot de app Training op de desktop met dat rapport.", GameType.SURVIVAL,
                Vec3.atBottomCenterOf(ROBOT_BAY.offset(4, 0, 12)), 180, 15, s -> {
            BlockPos core = ROBOT_BAY.offset(10, 0, 6);
            BlockPos startBlock = ROBOT_BAY.offset(2, 0, 9);
            BlockPos endBlock = ROBOT_BAY.offset(3, 0, 0);
            s.onServer(0, ShowcaseFeatures::buildRobotBay)
                    .onServer(0, player -> {
                        buildBayServer(player);
                        buildTraining(player);
                    })
                    .pause(50)
                    // Het instelscherm van Start (sneak + klik).
                    .stand(0, ROBOT_BAY.getX() + 4.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 11.5, 180, 25)
                    .onServer(10, player -> com.senne.imttech.training.TrainingBlocks.useStart(player, startBlock, true))
                    .pause(100)
                    .close(10)
                    // Achteraan: het End-blok met zijn lamp, het doel en Wave 2. Dan de mini-editor van End.
                    .stand(0, ROBOT_BAY.getX() + 4.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 4.5, 180, 20)
                    .pause(40)
                    .onServer(0, player -> com.senne.imttech.training.TrainingBlocks.useEnd(player, endBlock, true))
                    .pause(100)
                    .close(10)
                    // Klik op Start: jij bent de trainee, met de Ion Blaster in je hand. I.M.T telt af (3 seconden).
                    .stand(0, ROBOT_BAY.getX() + 4.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 11.5, 180, 25)
                    .onServer(10, player -> {
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.ION_BLASTER.get()));
                        com.senne.imttech.training.TrainingBlocks.useStart(player, startBlock, false);
                    })
                    .stand(0, ROBOT_BAY.getX() + 4.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 8.5, 180, 10)
                    // Go: Wave 1 gaat aan en schiet op jou. Na de eerste treffer schiet jij Trainer 1 neer, dan Trainer 2.
                    .pause(65)
                    .onServer(15, player -> ionAt(player, ROBOT_BAY.offset(0, 0, 5)))
                    .onServer(15, player -> ionAt(player, ROBOT_BAY.offset(8, 0, 5)))
                    // Wave 1 ligt neer: je diagram zet Wave 2 aan, de Super Battle bij het doel schiet.
                    .look(2, 180, 10)
                    .pause(33)
                    // Op het doel: geslaagd, het rapport, en de robots staan op.
                    .stand(0, ROBOT_BAY.getX() + 4.5, ROBOT_BAY.getY() + 1, ROBOT_BAY.getZ() + 0.5, 0, 15)
                    .pause(120)
                    // De app Training: het rapport.
                    .stand(0, ROBOT_BAY.getX() + 10.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 8.5, 180, 20)
                    .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, core))
                    .step(30, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.TRAINING)))
                    .pause(140)
                    .close(10);
        });

        add("Break-in drill", "Hoofdstuk 27: hoe moeilijk is het om in je eigen basis in te breken? Dezelfde werkplaats. In het "
                + "instelscherm van het Start-blok staat Kind nu op Break-in drill (oranje: 'you are the intruder'), met 10 levens. "
                + "Dan klik jij op Start (in survival): I.M.T telt af en zegt 'go! Your base treats you as an intruder now. Hack the "
                + "core with a Hacking Laptop'. Een diagram zet Wave 1 aan: de twee Trainers schieten op jou, hun eigenaar "
                + "(trainingsschoten: een leven eraf). Jij schiet ze neer met de Ion Blaster. Dan loop je naar de kern: je eigen "
                + "desktop gaat niet open ('The desktop is locked: you are the intruder in a drill'). Dus zet je de Hacking Laptop "
                + "op de kern: de terminal gaat open. De showcase typt na 8 seconden de oplossing voor je (je ziet ze niet in de "
                + "terminal): 'Core hacked: the drill is passed'. De kern blijft van jou, je krijgt de laptop terug, en "
                + "I.M.T geeft het rapport (hoe lang, 'closest 0 blocks from the core', en wie je raakte). Tot slot de app Training "
                + "met dat rapport bovenaan.", GameType.SURVIVAL,
                Vec3.atBottomCenterOf(ROBOT_BAY.offset(4, 0, 12)), 180, 15, s -> {
            BlockPos core = ROBOT_BAY.offset(10, 0, 6);
            BlockPos startBlock = ROBOT_BAY.offset(2, 0, 9);
            s.onServer(0, ShowcaseFeatures::buildRobotBay)
                    .onServer(0, player -> {
                        buildBayServer(player);
                        buildTraining(player);
                        buildDrill(player);
                    })
                    .pause(50)
                    // Het instelscherm: Kind = Break-in drill.
                    .stand(0, ROBOT_BAY.getX() + 4.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 11.5, 180, 25)
                    .onServer(10, player -> com.senne.imttech.training.TrainingBlocks.useStart(player, startBlock, true))
                    .pause(100)
                    .close(10)
                    // Klik op Start, met de Ion Blaster in je hand. Na het aftellen schiet Wave 1 op jou.
                    .onServer(10, player -> {
                        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.ION_BLASTER.get()));
                        com.senne.imttech.training.TrainingBlocks.useStart(player, startBlock, false);
                    })
                    .stand(0, ROBOT_BAY.getX() + 4.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 8.5, 180, 10)
                    .pause(65)
                    .onServer(15, player -> ionAt(player, ROBOT_BAY.offset(0, 0, 5)))
                    .onServer(15, player -> ionAt(player, ROBOT_BAY.offset(8, 0, 5)))
                    // Naar de kern: je eigen desktop zit op slot.
                    .stand(20, ROBOT_BAY.getX() + 10.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 8.5, 180, 20)
                    .onServer(10, player -> com.senne.imttech.security.os.SecurityOs.open(player, core))
                    .pause(60)
                    // De Hacking Laptop op je eigen kern (dat mag nu), en na 8 seconden de oplossing.
                    .onServer(0, player -> com.senne.imttech.security.TowerHack.placeLaptop(player, core,
                            new ItemStack(ModItems.HACKING_LAPTOP.get())))
                    .pause(com.senne.imttech.security.TowerHack.MIN_TICKS + 10)
                    .onServer(0, player -> solveHack(player, core.above()))
                    .pause(120)
                    // De app Training: het rapport van de oefening bovenaan.
                    .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, core))
                    .step(30, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.TRAINING)))
                    .pause(140)
                    .close(10);
        });

        add("Biebia's Industries Tower: hangar and landing pad", "De toren is van jou (gehackt). In de hangar staan zes Drone Docks "
                + "langs de muur; op vier staan de security drones die Biebia programmeerde (hoofdstuk 25: Tower drone 1 tot 4, "
                + "programma Tower patrol, met Scan, Face Recognition, Laser en Pathfinding). Na de hack zijn ze van jou: ze "
                + "stijgen op en vliegen hun rondjes door de hangar, en op jou schieten ze niet. (Zonder eigen server in de "
                + "toren hebben ze geen verbinding, en dan schieten ze op niemand.) Dan het dak: in het harnas stijg je op, en de "
                + "HUD wijst je landingsplatform aan (groen ruitje, afstand, en dichtbij het landingsvak als "
                + "gestippeld vierkant). Je daalt op het platform: I.M.T zegt 'Welcome home', en je bril "
                + "laadt op (kijk naar het percentage linksboven).", GameType.SURVIVAL,
                towerPoint(20.5, 34, 17.5), 180, 8, s -> {
            BlockPos core = BIEBIA_TOWER.offset(10, 57, 20);
            Vec3 pad = towerPoint(35.5, 100, 20.5);
            s.onServer(0, player -> {
                if (player.serverLevel().getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    towerDrones(player, be);
                }
            })
                    .pause(240)
                    // Het dak: harnas aan, opstijgen, en de HUD wijst het platform aan.
                    .onServer(0, player -> {
                        wearSuit(player);
                        ImtData.setEnergy(ImtData.getWornGlasses(player),
                                ImtData.getMaxEnergy(ImtData.getWornGlasses(player)) / 3);
                    })
                    .stand(0, BIEBIA_TOWER.getX() + 24.5, BIEBIA_TOWER.getY() + 100, BIEBIA_TOWER.getZ() + 12.5, 90, 0)
                    .key(30, () -> Minecraft.getInstance().options.keyJump, true)
                    .key(2, () -> Minecraft.getInstance().options.keyJump, false)
                    .key(2, () -> Minecraft.getInstance().options.keyJump, true)
                    .key(30, () -> Minecraft.getInstance().options.keyJump, false)
                    .lookAt(10, pad)
                    .pause(60)
                    // Boven het platform, en dalen tot je staat.
                    .onServer(0, player -> player.teleportTo(player.serverLevel(), pad.x, pad.y + 7, pad.z, 90, 55))
                    .key(10, () -> Minecraft.getInstance().options.keyShift, true)
                    .key(90, () -> Minecraft.getInstance().options.keyShift, false)
                    .pause(80);
        });

        add("Crashed satellite", "Een nieuwe structuur: het wrak van een oude Biebia's Industries-satelliet in een krater. "
                + "Eerst van boven: de rook (blauw uit de romp, van de reactor), de voor aan de oostkant waar hij "
                + "binnenkwam, het zonnepaneel dat nog schuin omhoog steekt, het afgebroken paneel op de helling, "
                + "de schotel op de noordhelling en overal brokstukken. Dan open je de zwarte doos in de romp: "
                + "netherite, diamanten, computerchips, upgrades. In een echte wereld duiken er raiddrones op je af "
                + "als je er met de bril komt; dat kan de showcase niet nadoen (hij herkent een neergezet wrak "
                + "niet als structuur).", new Vec3(CRASH_SITE.getX() + 30, -46, CRASH_SITE.getZ() + 0.5), 90, 22, s -> {
            Vec3 middle = new Vec3(CRASH_SITE.getX() + 0.5, CRASH_SITE.getY() - 1, CRASH_SITE.getZ() + 0.5);
            s.view(0, CRASH_SITE.getX() + 30, -46, CRASH_SITE.getZ() + 0.5, 90, 22)
                    .lookAt(2, middle)
                    .pause(90)
                    .view(0, CRASH_SITE.getX() - 20, -44, CRASH_SITE.getZ() - 20, -45, 30)
                    .lookAt(2, middle)
                    .pause(90)
                    .view(0, CRASH_SITE.getX() + 7, -50, CRASH_SITE.getZ() + 16, 150, 35)
                    .lookAt(2, middle)
                    .pause(80)
                    // Voor de opengescheurde romp, en de zwarte doos openen.
                    .stand(0, CRASH_SITE.getX() + 2.5, CRASH_SITE.getY(), CRASH_SITE.getZ() + 0.5, 90, 20)
                    .lookAt(10, Vec3.atCenterOf(BLACK_BOX))
                    .pause(40)
                    .step(0, () -> rightClick(BLACK_BOX))
                    .pause(120)
                    .close(0);
        });

        add("Biebia's Industries bunker", "De geheime Raven-basis, uitgehakt in een berg (Winter Soldier). In een echte "
                + "wereld zoekt de mod een steile helling in de bergen; hier staat hij in een zelfgebouwde berg. "
                + "Buiten: de betonnen gevel met het rode embleem en twee Raven-torentjes. Binnen: de gang met "
                + "een controlepost, de grote hal (zuilen, kratten, vaandels, soldaten, een torentje achterin), "
                + "de reactorkamer (twee warmtegeneratoren in lava; hun kabel loopt over het plafond van de hal "
                + "naar achteren), de kluisgang met twee rode laserwanden, en de kluis met Raven-onderdelen en "
                + "een cryo-cel. De laserwanden gaan uit als je de generatoren sloopt of de kabel doorknipt. "
                + "Hier in creative laten de soldaten en torentjes je met rust. Vinden doe je hem door te "
                + "zoeken in de bergen (er ligt geen kaart meer in het satellietwrak).",
                new Vec3(BIEBIA_BUNKER.getX() + 0.5, -52, BIEBIA_BUNKER.getZ() - 22), 0, 12, s -> {
            BlockPos b = BIEBIA_BUNKER;
            s.onServer(0, player -> DevWorld.placeBiebiaBunker(player.serverLevel(), b))
                    .pause(70)
                    .view(0, b.getX() + 0.5, b.getY() + 1, b.getZ() - 3.5, 0, 8)
                    .pause(60)
                    .view(0, b.getX() + 0.5, b.getY() + 2, b.getZ() + 16.5, 0, 12)
                    .pause(70)
                    .view(0, b.getX() + 10.5, b.getY() + 1, b.getZ() + 17.5, 0, 25)
                    .pause(60)
                    .view(0, b.getX() + 0.5, b.getY() + 1, b.getZ() + 30.5, 0, 5)
                    .pause(60)
                    .view(0, b.getX() + 0.5, b.getY() + 2, b.getZ() + 39.5, 0, 20)
                    .pause(30)
                    .stand(0, b.getX() - 3.5, b.getY(), b.getZ() + 40.5, 0, 30)
                    .step(10, () -> rightClick(b.offset(-4, 0, 42)))
                    .pause(90)
                    .close(0);
        });

        // ---------------------------------------------------------------- Schermen en overige
        add("Voice commands", "De editor voor spraakcommando's (het woordenboek). De stem zelf moet "
                + "je eerst installeren via G > Systems; dat doet de showcase niet.", start, 180, 0, s -> s
                .open(10, () -> new VoiceCommandsScreen(null))
                .pause(140)
                .close(0));

        add("AI Brain", "Het scherm G > Systems > AI Brain: woordenboek, lokaal model of Claude (met je eigen "
                + "API-sleutel), en 'Speaks on her own'. Daarna praat je (hier als tekst, zonder microfoon) tegen "
                + "het lokale model: een vraag over je batterij, twee dingen tegelijk, iets onthouden en terugvragen, "
                + "een vage zin (dan vraagt ze wat je bedoelt), en een zin in het Engels. Wat ze hoort en antwoordt "
                + "staat bovenaan. Staat het lokale model niet in de showcase-map, dan zie je alleen het scherm.",
                start, 180, 0, s -> {
            s.step(0, () -> com.senne.imttech.client.voice.ImtMemory.forgetAll())
                    .open(10, com.senne.imttech.client.BrainScreen::new)
                    .pause(100)
                    .close(0);
            // Zinnen zoals het oor ze zou opschrijven; na elke zin wachten tot ze klaar is met denken.
            s.step(0, () -> {
                com.senne.imttech.client.voice.BrainModel.decide(); // welke maat past bij deze pc
                if (com.senne.imttech.client.voice.ImtBrain.ready(com.senne.imttech.client.voice.BrainMode.LOCAL)) {
                    com.senne.imttech.client.voice.ImtBrain.setMode(com.senne.imttech.client.voice.BrainMode.LOCAL);
                }
            });
            for (String said : new String[] {"hoeveel energie heb ik nog", "zet nachtzicht aan en scan naar ertsen",
                    "onthoud dat mijn basis Biebia Hoeve heet", "hoe heet mijn basis?", "doe iets met de ding",
                    "what time is it and is it going to rain"}) {
                s.step(0, () -> {
                    if (com.senne.imttech.client.voice.ImtBrain.mode() == com.senne.imttech.client.voice.BrainMode.LOCAL) {
                        com.senne.imttech.client.voice.VoiceControl.hear(said);
                    }
                })
                        .pause(2)
                        .waitUntil(1200, () -> !com.senne.imttech.client.voice.ImtBrain.isThinking())
                        .pause(70);
            }
            s.step(0, () -> com.senne.imttech.client.voice.ImtBrain.setMode(com.senne.imttech.client.voice.BrainMode.DICTIONARY));
        });

        add("Music (Spotify)", "Het muziekscherm: eenmalig Spotify koppelen, daarna 'I.M.T play X "
                + "by Y'.", start, 180, 0, s -> s
                .open(10, MusicScreen::new)
                .pause(120)
                .close(0));

        add("Advancements", "De prestaties onder 'Biebia's Industries': de rode draad door de mod.",
                start, 180, 0, s -> s
                .step(10, ShowcaseFeatures::openAdvancements)
                .pause(140)
                .close(0));

        add("I.M.T Technology items", "Alles wat de mod toevoegt, in de tien creative-tabbladen van I.M.T, elk twee "
                + "seconden: Resources (chips en upgrades), Equipment (de bril, het harnas, de holster, de pouches en de "
                + "rugzakken), Weapons (alle geweren met hun magazijnen en munitie, de granaten en de banken), Gun Parts (de "
                + "onderdelen van alle geweren, sinds hoofdstuk 104), Attachments (sinds hoofdstuk 104), Power (de kabels en "
                + "de generatoren), Interior (holotafel, werkbank, crusher, compactor), Security (lezers, codepanelen, "
                + "laserwand en -brug), Robots (onderdelen, chips, training) en Datacenter.", start, 180, 0, s -> {
                    int wait = 10;
                    for (DeferredHolder<CreativeModeTab, ? extends CreativeModeTab> tab : ModCreativeTabs.TABS.getEntries()) {
                        s.step(wait, () -> openModTab(tab.get()));
                        wait = 40;
                    }
                    s.pause(40).close(0);
                });

        // ---------------------------------------------------------------- Laserbrug
        // Hoofdstuk 34 (25 oktober), achteraan zodat de nummers van je notities blijven kloppen.
        BlockPos bridge = ShowcaseScene.BRIDGE;
        add("Laser bridge", "Hoofdstuk 34. Een gracht met drie brugzenders in de westrand (in de vloer, met pijlpunten "
                + "erop). Aan (hoofdstuk 39: op zijn naam, zoals I.M.T hem schakelt): de brug schuift uit, drie breed, cyaan, met een fel randje aan de buitenkant en "
                + "aan het eind, en een glans die eroverheen trekt. Je loopt eroverheen (de plaat ligt net onder de rand, "
                + "dus zonder stapje). Dan het instelscherm (sneak + klik): paars. Tot slot de val: je staat midden op de "
                + "brug, hij gaat uit, de brug trekt zich terug en je valt in het water.",
                new Vec3(bridge.getX() + 3.5, bridge.getY() + 4, bridge.getZ() + 9.5), 180, 25, s -> s
                .onServer(0, ShowcaseScene::buildBridge)
                .pause(40)
                .onServer(0, player -> ShowcaseScene.bridge(player.serverLevel(), true))
                .pause(80)
                // Eroverheen lopen, van de westkant naar de oostkant.
                .stand(0, bridge.getX() - 2.5, bridge.getY() + 1, bridge.getZ() + 0.5, -90, 15)
                .pause(20)
                .key(0, () -> Minecraft.getInstance().options.keyUp, true)
                .key(55, () -> Minecraft.getInstance().options.keyUp, false)
                .pause(20)
                // Terug naar de zenders: het instelscherm, en paars.
                .stand(0, bridge.getX() - 1.5, bridge.getY() + 1, bridge.getZ() + 0.5, -90, 40)
                .onServer(10, player -> com.senne.imttech.shield.LaserBridges.open(player, bridge))
                .pause(40)
                .onServer(0, player -> bridge(player, LaserActionPayload.Op.COLOR, ShieldColor.PURPLE.ordinal()))
                .pause(50)
                .close(0)
                // De val: midden op de brug, en dan gaat de redstone uit.
                .stand(10, bridge.getX() + 3.5, bridge.getY() + com.senne.imttech.shield.LaserBridgePlateBlock.TOP / 16.0,
                        bridge.getZ() + 0.5, -90, 50)
                .pause(30)
                .onServer(0, player -> ShowcaseScene.bridge(player.serverLevel(), false))
                .pause(60)
                .onServer(0, player -> bridge(player, LaserActionPayload.Op.COLOR, ShieldColor.CYAN.ordinal())));

        // ---------------------------------------------------------------- Wapens
        // Hoofdstuk 35 (25 oktober), ook achteraan.
        BlockPos weapons = ShowcaseScene.WEAPONS;
        add("EMP and frag grenades", "Hoofdstuk 35. Een veld met een guard robot en een raketturret voor een muur. Je "
                + "gooit een EMP Grenade (de bol): hij stuitert, rolt, piept steeds sneller en gaat af in een bol van "
                + "blauw licht; de robot valt neer en de loop van de turret hangt omlaag (20 seconden). Daarna een Sticky "
                + "EMP Grenade op de muur, en dan de explosieve kopieën: een Sticky Frag Grenade op de muur (een gat, "
                + "barsten eromheen) en een Frag Grenade over de vloer. Tot slot een EMP aan je eigen voeten, met je "
                + "bril op: ruis, en daarna start de bril opnieuw op.",
                new Vec3(weapons.getX() + 0.5, weapons.getY() + 1, weapons.getZ() + 0.5), -90, 10, s -> s
                .onServer(0, ShowcaseScene::buildWeapons)
                .stand(0, weapons.getX() + 0.5, weapons.getY() + 1, weapons.getZ() + 0.5, -90, 8)
                .onServer(20, player -> holdAndThrow(player, ModItems.EMP_GRENADE.get(), false))
                .pause(110)
                .onServer(0, player -> holdAndThrow(player, ModItems.STICKY_EMP_GRENADE.get(), false))
                .pause(80)
                .stand(0, weapons.getX() + 0.5, weapons.getY() + 1, weapons.getZ() - 3.5, -90, 4)
                .onServer(10, player -> holdAndThrow(player, ModItems.STICKY_FRAG_GRENADE.get(), false))
                .pause(80)
                .onServer(0, player -> holdAndThrow(player, ModItems.FRAG_GRENADE.get(), true))
                .pause(110)
                .onServer(0, player -> {
                    DevWorld.wearGlasses(player);
                    com.senne.imttech.weapons.Emp.blast(player.serverLevel(), player.position().add(0, 0.5, 0), player);
                })
                .pause(120)
                .onServer(0, player -> {
                    player.setData(ModAttachments.EMP_UNTIL, 0L);
                    player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
                }));

        add("Smoke grenade and flashbang", "Hoofdstuk 35. De raketturret schiet op een husk; je rolt een Smoke "
                + "Grenade ertussen: hij sist, er groeit een wolk, en de turret stopt (machines kijken niet door rook). "
                + "Dan een rode smokebom (gemaakt met rode kleurstof). Tot slot een Flashbang vlak voor je: je kijkt "
                + "ernaar, dus je scherm wordt helemaal wit, je hoort een piep, en het trekt langzaam weer weg.",
                new Vec3(weapons.getX() - 2.5, weapons.getY() + 2, weapons.getZ() + 9.5), 180, 15, s -> s
                .onServer(0, ShowcaseScene::buildWeapons)
                .onServer(0, player -> DevWorld.spawnStill(player.serverLevel(), EntityType.HUSK,
                        weapons.offset(-2, 1, 3), 90))
                .stand(0, weapons.getX() - 2.5, weapons.getY() + 1, weapons.getZ() + 9.5, 135, 8)
                .pause(60)
                .onServer(0, player -> holdAndThrow(player, ModItems.SMOKE_GRENADE.get(), true))
                .pause(120)
                .onServer(0, player -> {
                    ItemStack red = new ItemStack(ModItems.SMOKE_GRENADE.get());
                    red.set(com.senne.imttech.registry.ModDataComponents.SMOKE_COLOR.get(), net.minecraft.world.item.DyeColor.RED);
                    player.setItemInHand(InteractionHand.MAIN_HAND, red);
                    ModItems.SMOKE_GRENADE.get().use(player.serverLevel(), player, InteractionHand.MAIN_HAND);
                })
                .pause(140)
                .stand(0, weapons.getX() - 6.5, weapons.getY() + 1, weapons.getZ() - 6.5, -90, 10)
                .onServer(10, player -> com.senne.imttech.weapons.Flashbang.bang(player.serverLevel(),
                        new Vec3(weapons.getX() - 3.5, weapons.getY() + 1.5, weapons.getZ() - 6.5), null))
                .pause(140)
                .onServer(0, player -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)));

        add("Laser guns", "Hoofdstuk 35. De vier lasergeweren, zoals in TACZ: het pistool (vanuit de heup, dan door het "
                + "vizier, een paar schoten), het geweer (inhouden: automatisch, met terugslag en laserbouten; dan R: een "
                + "cel uit je magazijnpouch kiezen en wisselen (hoofdstuk 57: zonder pouch haalt R de cel eruit en rechtsklik "
                + "je een cel erin, zoals bij de Glock; U haalt de cel eruit); dan H: bekijken), de sniper (de kijker over je "
                + "scherm, één "
                + "zware straal, de grendel) en de shotgun (een waaier van stralen, de pomp). Rechtsonder je munitie en je "
                + "cellen. Met je eigen armen aan het geweer.",
                new Vec3(weapons.getX() + 0.5, weapons.getY() + 1, weapons.getZ() + 0.5), 90, 4, s -> s
                .onServer(0, ShowcaseScene::buildWeapons)
                .onServer(0, ShowcaseFeatures::giveGuns)
                .stand(0, weapons.getX() + 0.5, weapons.getY() + 1, weapons.getZ() + 0.5, 90, 3)
                .step(0, () -> hotbar(0))
                .pause(40)
                .key(0, () -> Minecraft.getInstance().options.keyUse, true)
                .pause(20)
                .key(0, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .key(8, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .key(8, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .key(20, () -> Minecraft.getInstance().options.keyUse, false)
                .step(10, () -> hotbar(1))
                .pause(30)
                .key(0, () -> Minecraft.getInstance().options.keyAttack, true)
                .key(30, () -> Minecraft.getInstance().options.keyAttack, false)
                .step(20, () -> net.minecraft.client.KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()))
                .step(5, () -> magPick(0))
                .pause(55)
                .step(0, () -> net.minecraft.client.KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_INSPECT.getKey()))
                .pause(60)
                .step(0, () -> hotbar(2))
                .pause(30)
                .key(0, () -> Minecraft.getInstance().options.keyUse, true)
                .pause(30)
                .key(0, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .key(30, () -> Minecraft.getInstance().options.keyUse, false)
                .pause(20)
                .step(0, () -> hotbar(3))
                .pause(30)
                .key(0, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .key(25, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .pause(40)
                .step(0, () -> hotbar(0))
                .onServer(0, player -> player.getInventory().clearContent()));

        // ---------------------------------------------------------------- Het netwerk
        // Hoofdstuk 38 (29 oktober), ook achteraan.
        BlockPos lan = NET_YARD;
        add("Network cables", "Hoofdstuk 38. Een desktop (Security Core) met drie soorten kabel: een groene PoE-kabel naar "
                + "een Network Switch, daarachter een witte netwerkkabel (een stukje onder een blok: met de bril zie je hem "
                + "er wit doorheen) naar een keycardlezer, een PoE-kabel naar een camera, en een grijze stroomkabel die de "
                + "switch voedt. Let op: twee keer loopt er een helder vakje van de lezer naar de kern (wit over de "
                + "netwerkkabel, groen over de PoE-kabel), en de gele streepjes van de stroom (naar de switch, en over de "
                + "PoE-kabel naar de camera). Dan het deurtje van de switch: de poort naar de camera gaat uit (de camera "
                + "verliest stroom en netwerk) en weer aan. Tot slot Devices op de desktop, met de switch en zijn poorten.",
                new Vec3(lan.getX() + 3.5, lan.getY(), lan.getZ() + 6.5), 180, 25, s -> s
                .onServer(0, player -> com.senne.imttech.lan.LanGrid.overrideRequired(true))
                .onServer(0, ShowcaseFeatures::buildNetworkYard)
                .pause(60)
                .onServer(0, player -> com.senne.imttech.lan.CableTraffic.signal(player.serverLevel(), NET_READER, true))
                .pause(50)
                .onServer(0, player -> com.senne.imttech.lan.CableTraffic.signal(player.serverLevel(), NET_READER, true))
                .pause(90)
                // Het deurtje van de switch: de poort naar de camera (achter, noord) uit en weer aan.
                .stand(0, lan.getX() + 3.5, lan.getY(), lan.getZ() + 2.5, 180, 30)
                .onServer(10, player -> com.senne.imttech.lan.NetworkSwitches.open(player, NET_SWITCH))
                .pause(40)
                .onServer(0, player -> com.senne.imttech.lan.NetworkSwitches.setPort(player.serverLevel(), NET_SWITCH,
                        net.minecraft.core.Direction.NORTH, false))
                .pause(50)
                .close(0)
                .lookAt(5, Vec3.atCenterOf(NET_SWITCH.north(3)))
                .pause(80)
                .onServer(0, player -> com.senne.imttech.lan.NetworkSwitches.setPort(player.serverLevel(), NET_SWITCH,
                        net.minecraft.core.Direction.NORTH, true))
                .pause(60)
                // De desktop: Devices, met de poorten van de switch open.
                .stand(0, lan.getX() + 0.5, lan.getY(), lan.getZ() + 1.5, 180, 20)
                .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, NET_YARD))
                .step(30, () -> desktop(com.senne.imttech.client.os.SecurityOsScreen::tourSwitchPorts))
                .pause(100)
                .close(20));

        add("Network button and lever", "Hoofdstuk 38. Een netwerkhendel en een netwerkknop op de vloer, met een PoE-kabel "
                + "naar de desktop, en een diagram op de server: when lever(\"Night mode\") en when button(\"Doorbell\"). De "
                + "hendel gaat om: een vakje loopt over de kabel naar de kern en I.M.T zegt \"Night mode on\" (lever.on). "
                + "Dan de knop: hij duikt even in, en I.M.T zegt dat jij aanbelde (player.name). De hendel terug: \"Night "
                + "mode off\". Geen redstone: alles via het diagram. Tot slot het diagram in de app Logic, en de knoppen en "
                + "hendels in Devices.",
                new Vec3(lan.getX() + 0.5, lan.getY(), lan.getZ() - 3.5), 0, 40, s -> s
                .onServer(0, player -> com.senne.imttech.lan.LanGrid.overrideRequired(true))
                .onServer(0, ShowcaseFeatures::buildNetworkYard)
                .onServer(30, ShowcaseFeatures::wakeNetworkServer)
                .pause(40)
                .onServer(0, player -> useControl(player, NET_LEVER))
                .pause(80)
                .onServer(0, player -> useControl(player, NET_BUTTON))
                .pause(80)
                .onServer(0, player -> useControl(player, NET_LEVER))
                .pause(80)
                .stand(0, lan.getX() + 0.5, lan.getY(), lan.getZ() + 1.5, 180, 20)
                .onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, NET_YARD))
                .step(30, () -> desktop(os -> os.tourLogic("Controls", CONTROLS_CODE, false)))
                .pause(200)
                .step(0, () -> desktop(os -> os.tourDevicesAt("BUTTONS")))
                .pause(90)
                .close(20));

        // ---------------------------------------------------------------- De laptop overal, en computers
        // Hoofdstuk 40 (30 oktober), ook achteraan.
        BlockPos pc = PC_DESK;
        add("Hacking Laptop: log in from anywhere", "Hoofdstuk 40. Rechtsklik met de Hacking Laptop in de lucht: het "
                + "inlogscherm (SSH login) met het IP van een kern en zijn wachtwoord (sterretjes). Eerst een fout "
                + "wachtwoord: rood \"Permission denied\" (en de eigenaar krijgt een alarm). Dan het goede: de desktop van "
                + "de kern opent op afstand, met bovenaan REMOTE SESSION, en de app Status laat het IP zien. Kijk of je de "
                + "tekst goed kunt lezen en of de knoppen op hun plek staan.",
                new Vec3(pc.getX() + 3.5, pc.getY(), pc.getZ() + 4.5), 180, 20, s -> s
                .onServer(0, player -> com.senne.imttech.lan.LanGrid.overrideRequired(true))
                .onServer(0, player -> VisualCheck.buildComputerDesk(player, PC_DESK))
                .onServer(0, player -> player.getInventory().setItem(0,
                        new net.minecraft.world.item.ItemStack(com.senne.imttech.registry.ModItems.HACKING_LAPTOP.get())))
                .step(0, () -> hotbar(0))
                .pause(30)
                .open(0, () -> new com.senne.imttech.client.os.OsLoginScreen(""))
                .step(20, () -> login(screen -> screen.tourType(VisualCheck.computerDeskIp, "wrong1")))
                .pause(40)
                .step(0, () -> login(com.senne.imttech.client.os.OsLoginScreen::tourConnect))
                .pause(70)
                .step(0, () -> login(screen -> screen.tourType(VisualCheck.computerDeskIp, "recept1")))
                .pause(40)
                .step(0, () -> login(com.senne.imttech.client.os.OsLoginScreen::tourConnect))
                .pause(30)
                .step(0, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.STATUS)))
                .pause(100)
                .close(20)
                .onServer(0, player -> player.getInventory().clearContent()));

        add("Computer and monitor", "Hoofdstuk 40. Een receptie: twee Computers aan het netwerk van een kern, elk met een "
                + "Monitor ernaast (het scherm licht op als de computer werkt). Eerst de app Computers op de kern: per "
                + "computer wie hem mag gebruiken, een code, en per app Off, View of Use. Dan rechtsklik op de monitor van "
                + "Reception: bovenaan COMPUTER: Reception, en alleen Status, Cameras, Devices en Logs, om te kijken (\"view "
                + "only\": geen pijltjes bij de camera's, geen knoppen in Devices). In Logs met Show: entries zie je wie er waar "
                + "binnenkwam. Tot slot het codescherm van de tweede computer (Office).",
                new Vec3(pc.getX() + 3.5, pc.getY(), pc.getZ() + 4.5), 180, 20, s -> s
                .onServer(0, player -> com.senne.imttech.lan.LanGrid.overrideRequired(true))
                .onServer(0, player -> VisualCheck.buildComputerDesk(player, PC_DESK))
                .lookAt(20, Vec3.atCenterOf(PC_DESK.east(5)))
                .pause(80)
                .onServer(0, player -> com.senne.imttech.security.os.SecurityOs.open(player, PC_DESK))
                .step(30, () -> desktop(os -> os.tourComputers(PC_DESK.east(4))))
                .pause(140)
                .close(20)
                .onServer(5, player -> com.senne.imttech.computer.Computers.use(player, PC_DESK.east(5)))
                .pause(60)
                .step(0, () -> desktop(os -> os.tourCameras(null)))
                .pause(80)
                .step(0, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.DEVICES)))
                .pause(80)
                .step(0, () -> desktop(com.senne.imttech.client.os.SecurityOsScreen::tourLogsEntries))
                .pause(100)
                .close(20)
                .open(0, () -> new com.senne.imttech.client.os.OsLoginScreen(PC_DESK.east(3).south(), "Office"))
                .pause(80)
                .close(20));

        // ---------------------------------------------------------------- De schermmuur
        // 19 oktober (gebouwd op 31 oktober), ook achteraan.
        BlockPos room = WALL_ROOM;
        add("Camera wall", "Jouw notitie van 19 oktober: fysieke cameraschermen. Zes Camera Monitors tegen een muur zijn samen één "
                + "scherm van 3 x 2, met één PoE-kabel naar de kern. Eerst donkerblauw: er staat nog niets op. Rechtsklik: het "
                + "instelscherm met het hele scherm als rooster en rechts je camera's. Lobby wordt een groot vak van 2 x 2 (je "
                + "ziet het slepen), Vault en de robot Guard 9 de twee vakjes rechts, en elk vak laat meteen zijn beeld zien. Dan "
                + "het echte scherm: live beelden met de naam, REC en scanlijnen. Tot slot zet ik de camera Vault uit: zijn vak geeft "
                + "NO SIGNAL met ruis. Kijk of de beelden scherp zijn en niet uitgerekt, en of de randen kloppen.",
                new Vec3(room.getX() + 0.5, room.getY(), room.getZ() + 0.5), 180, 5, s -> s
                .onServer(0, player -> com.senne.imttech.lan.LanGrid.overrideRequired(true))
                .onServer(0, player -> VisualCheck.buildCameraWall(player, WALL_ROOM))
                .onServer(10, player -> VisualCheck.wakeCameraWall(player, WALL_ROOM))
                .pause(70)
                .onServer(0, player -> com.senne.imttech.security.wall.CameraWall.open(player, WALL_ROOM.offset(0, 1, -6)))
                .pause(50)
                .step(0, () -> VisualCheck.dragCamera("Lobby", 0, 0, 1, 1))
                .pause(50)
                .step(0, VisualCheck::dropCamera)
                .pause(40)
                .step(0, () -> VisualCheck.dragCamera("Vault", 2, 0, 2, 0))
                .pause(25)
                .step(0, VisualCheck::dropCamera)
                .pause(30)
                .step(0, () -> VisualCheck.dragCamera("Guard 9", 2, 1, 2, 1))
                .pause(25)
                .step(0, VisualCheck::dropCamera)
                .pause(80)
                .close(10)
                .pause(160)
                .onServer(0, player -> {
                    ServerLevel level = player.serverLevel();
                    BlockPos vault = WALL_ROOM.offset(-1, 0, -3);
                    level.setBlockAndUpdate(vault, level.getBlockState(vault).setValue(com.senne.imttech.security.CameraBlock.ENABLED, false));
                    if (level.getBlockEntity(vault) instanceof com.senne.imttech.security.CameraBlockEntity camera) {
                        camera.updateOnline(level, vault, level.getBlockState(vault));
                    }
                    VisualCheck.wakeCameraWall(player, WALL_ROOM);
                })
                .pause(100));
        addBattery();
        addHandcuffs();
        addAuth();
        addPhone();
        addRooms();
        addGuns();
        addGear();
        addSkills();
        addLaserParts();
        addBigUpdate();
    }

    /** Hoofdstuk 52, stap 3: de skilltree. */
    private static void addBattery() {
        // ---------------------------------------------------------------- De Power Battery (hoofdstuk 47)
        BlockPos b = BATTERY_YARD;
        BlockPos port = b.offset(0, 0, 2);
        BlockPos lid = b.offset(2, 3, 2);
        add("Power Battery", "Hoofdstuk 47: de Power Battery, zoals bij Mekanism. Een doos van 5 x 4 x 5 Battery Casing met "
                + "een raam van Battery Glass (daarachter de 18 Battery Cells) en een Battery Port links en rechts onderaan. "
                + "Links komt een kabel van een warmtegenerator en een zonnepaneel; rechts gaat een kabel naar een holotafel "
                + "en een camera. Rechtsklik op de poort: het scherm van de batterij, vanaf nu de enige plek waar je het "
                + "hele net ziet: STORED met de balk, wat het net maakt, gebruikt en tekortkomt per tick, de grafiek van de "
                + "laatste vijf minuten (hier alvast gevuld) en de lijsten GENERATORS en DEVICES. Dan haal ik een casing uit "
                + "het dak: het scherm zegt 'The wall is not closed' en waar; het blok terug en de stroom is er nog. Tot "
                + "slot het scherm van de generator: alleen hijzelf, het netoverzicht ernaast is weg.",
                at(b.offset(2, 0, 9)), 180, 8, s -> s
                .onServer(0, player -> VisualCheck.buildBatteryYard(player, BATTERY_YARD))
                .pause(50)
                .lookAt(10, Vec3.atCenterOf(port))
                .pause(30)
                .onServer(0, player -> {
                    if (player.serverLevel().getBlockEntity(port) instanceof com.senne.imttech.battery.BatteryPortBlockEntity entity) {
                        entity.tourHistory();
                    }
                    com.senne.imttech.battery.Batteries.open(player.serverLevel(), port, player);
                })
                .pause(160)
                .close(0)
                .lookAt(10, Vec3.atCenterOf(lid))
                .onServer(10, player -> player.serverLevel().setBlockAndUpdate(lid, Blocks.AIR.defaultBlockState()))
                .pause(40)
                .onServer(0, player -> com.senne.imttech.battery.Batteries.open(player.serverLevel(), port, player))
                .pause(80)
                .close(0)
                .onServer(10, player -> player.serverLevel().setBlockAndUpdate(lid, ModBlocks.BATTERY_CASING.get().defaultBlockState()))
                .pause(30)
                .onServer(0, player -> com.senne.imttech.battery.Batteries.open(player.serverLevel(), port, player))
                .pause(80)
                .close(0)
                .stand(0, b.getX() - 2.5, b.getY(), b.getZ() + 5.5, 180, 25)
                .lookAt(10, Vec3.atCenterOf(port.west(3)))
                .step(20, () -> rightClick(port.west(3)))
                .close(90));
    }

    private static void addHandcuffs() {
        // ---------------------------------------------------------------- Handboeien (hoofdstuk 48)
        BlockPos b = CUFF_YARD;
        Vec3 bram = Vec3.atBottomCenterOf(b.offset(2, 0, 4)).add(0, 1.2, 0);
        Vec3 soldier = Vec3.atBottomCenterOf(b.offset(4, 0, 4)).add(0, 1.2, 0);
        add("Handcuffs", "Hoofdstuk 48 (en 54): handboeien (in survival). Voor je staat Bram met zijn rug naar je toe, rechts "
                + "van hem een Raven-soldaat die bijna dood is, links naast je een guard robot die neerligt; achter je een cel van "
                + "tralies. Boeien kan alleen via de rug. Je houdt rechtsklik op Bram vast: onderaan telt 'Handcuffing Bram... "
                + "2.0 s' af, dan 'You handcuffed Bram': zijn handen gaan op zijn rug met het bandje ertussen, jouw handen gaan "
                + "vooruit aan die boeien, en hij staat vlak voor je met zijn gezicht naar voren. Dan de soldaat: één klik, want "
                + "hij is weerloos. Je loopt achteruit de cel in en de soldaat staat elke stap vlak voor je, met zijn rug naar "
                + "je toe (echt vast, geen lijn met speling). Dan de Handcuff Key op de "
                + "soldaat (je krijgt de boeien terug), terug naar Bram en ook hem los. Tot slot de robot: hij ligt neer, dus "
                + "één klik (in de app Robots staat hij dan op Handcuffed), en de sleutel maakt hem weer vrij.",
                GameType.SURVIVAL, at(b.offset(2, 0, 6)), 180, 5, s -> s
                .onServer(0, player -> VisualCheck.buildCuffYard(player, CUFF_YARD))
                .pause(40)
                .lookAt(10, bram)
                .key(10, () -> Minecraft.getInstance().options.keyUse, true)
                .key(55, () -> Minecraft.getInstance().options.keyUse, false)
                .pause(50)
                .lookAt(0, soldier)
                .tap(15, () -> Minecraft.getInstance().options.keyUse)
                .pause(40)
                // Achteruit de cel in: de soldaat komt aan het touw mee.
                .key(0, () -> Minecraft.getInstance().options.keyDown, true)
                .key(25, () -> Minecraft.getInstance().options.keyDown, false)
                .pause(60)
                .onServer(0, player -> player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HANDCUFF_KEY.get())))
                .step(10, () -> lookAtNearest(RavenSoldierEntity.class))
                .tap(15, () -> Minecraft.getInstance().options.keyUse)
                .pause(40)
                // Terug naar Bram.
                .key(0, () -> Minecraft.getInstance().options.keyUp, true)
                .key(25, () -> Minecraft.getInstance().options.keyUp, false)
                .step(10, () -> lookAtNearest(net.minecraft.world.entity.player.Player.class))
                .tap(10, () -> Minecraft.getInstance().options.keyUse)
                .pause(40)
                // De robot: hij ligt neer, dus meteen.
                .onServer(0, player -> player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HANDCUFFS.get())))
                .step(10, () -> lookAtNearest(com.senne.imttech.robot.GuardRobotEntity.class))
                .tap(10, () -> Minecraft.getInstance().options.keyUse)
                .pause(50)
                .onServer(0, player -> player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.HANDCUFF_KEY.get())))
                .tap(15, () -> Minecraft.getInstance().options.keyUse)
                .pause(50));
    }

    private static void addAuth() {
        // ---------------------------------------------------------------- Autorisatiecodes en de Auth Console (hoofdstuk 49)
        BlockPos y = AUTH_YARD;
        BlockPos panel = y.offset(1, 0, 4);
        BlockPos console = y.offset(3, 0, 4);
        add("Authorization codes and the Auth Console", "Hoofdstuk 49. Achter je een kern met een server; voor je een muurtje "
                + "met een codepaneel 'Vault' en een Auth Console 'Gate', en Bram ernaast. Eerst de desktop: in Status staat de "
                + "autorisatiecode van dit moment (en wanneer de volgende komt); in Settings stel je in hoe lang hij is, om de "
                + "hoeveel minuten, en of de vorige nog mag. Dan typ ik die code op het paneel: groen, en I.M.T zegt 'Vault open "
                + "for Dev' (het diagram kijkt naar auth.valid, er staat geen code in). Dan drukt Bram op de console: oranje, jij "
                + "krijgt een bericht, en in de app Requests staat 'Bram at Gate' met Accept en Deny. Accept: de console groen "
                + "en I.M.T zegt 'Bram may come in' (request.accepted). Bram nog eens, nu Deny: rood, 'Bram stays outside'.",
                at(y.offset(2, 0, 2)), 0, 12, s -> s
                .onServer(0, player -> VisualCheck.buildAuthYard(player, AUTH_YARD))
                .onServer(30, player -> VisualCheck.wakeAuthServer(player, AUTH_YARD))
                .pause(40)
                .onServer(0, player -> com.senne.imttech.security.os.SecurityOs.open(player, AUTH_YARD))
                .step(20, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.STATUS)))
                .pause(90)
                .step(0, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.SETTINGS)))
                .pause(100)
                .close(0)
                .lookAt(10, Vec3.atCenterOf(panel))
                .onServer(20, player -> {
                    if (player.serverLevel().getBlockEntity(AUTH_YARD) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
                        com.senne.imttech.logic.LogicEvents.codePanel(player.serverLevel(), panel, player, core.authCode());
                    }
                })
                .pause(70)
                .lookAt(0, Vec3.atCenterOf(console))
                .onServer(10, player -> VisualCheck.bramAsks(player, console))
                .pause(60)
                .onServer(0, player -> com.senne.imttech.security.os.SecurityOs.open(player, AUTH_YARD))
                .step(20, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.REQUESTS)))
                .pause(80)
                .step(0, () -> desktop(os -> os.tourRequestAnswer(true)))
                .pause(40)
                .close(0)
                .pause(60)
                .onServer(0, player -> VisualCheck.bramAsks(player, console))
                .pause(40)
                .onServer(0, player -> com.senne.imttech.security.os.SecurityOs.open(player, AUTH_YARD))
                .step(20, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.REQUESTS)))
                .pause(60)
                .step(0, () -> desktop(os -> os.tourRequestAnswer(false)))
                .pause(40)
                .close(0)
                .pause(60));
    }

    private static void addPhone() {
        // ---------------------------------------------------------------- De I.M.T Phone (hoofdstuk 50)
        BlockPos y = AUTH_YARD;
        BlockPos console = y.offset(3, 0, 4);
        add("I.M.T Phone", "Hoofdstuk 50. Dezelfde kern als hiervoor, en een I.M.T Phone in je hand. Rechtsklik: het scherm "
                + "'connect to a system' met het IP en het wachtwoord van je kern (zoals de laptop). Dan kies je een PIN van vier "
                + "cijfers, en de desktop van de telefoon gaat open: bovenaan I.M.T PHONE, en alleen Status (met de "
                + "autorisatiecode), Logs, Cameras, Devices, Requests en Messages. Bram drukt op de console: zijn verzoek staat in "
                + "Requests, en je accepteert het vanaf de telefoon. In Messages staat wat I.M.T je zei ('Bram asks for access', "
                + "'Bram may come in'); onderaan kun je zelf een bericht sturen. De telefoon dicht en weer open: hij vraagt je "
                + "PIN. Tot slot de desktop op de kern zelf: in Access staat je telefoon, met de knop Block.",
                at(y.offset(2, 0, 2)), 0, 12, s -> s
                .onServer(0, player -> VisualCheck.buildAuthYard(player, AUTH_YARD))
                .onServer(30, player -> VisualCheck.wakeAuthServer(player, AUTH_YARD))
                .onServer(0, player -> VisualCheck.givePhone(player, AUTH_YARD))
                .pause(40)
                .onServer(0, player -> com.senne.imttech.security.os.Phones.use(player, player.getMainHandItem()))
                .step(20, () -> login(screen -> screen.tourType(VisualCheck.authYardIp, "bitech1")))
                .pause(50)
                .step(0, () -> login(com.senne.imttech.client.os.OsLoginScreen::tourConnect))
                .pause(40)
                .step(0, () -> login(screen -> screen.tourType("", "1234")))
                .pause(50)
                .step(0, () -> login(com.senne.imttech.client.os.OsLoginScreen::tourConnect))
                .pause(40)
                .step(0, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.STATUS)))
                .pause(80)
                .onServer(0, player -> VisualCheck.bramAsks(player, console))
                .step(20, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.REQUESTS)))
                .pause(80)
                .step(0, () -> desktop(os -> os.tourRequestAnswer(true)))
                .pause(40)
                .step(0, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.MESSAGES)))
                .pause(100)
                .close(0)
                .pause(30)
                .onServer(0, player -> com.senne.imttech.security.os.Phones.use(player, player.getMainHandItem()))
                .pause(60)
                .step(0, () -> login(screen -> screen.tourType("", "1234")))
                .pause(30)
                .step(0, () -> login(com.senne.imttech.client.os.OsLoginScreen::tourConnect))
                .pause(60)
                .close(0)
                .onServer(10, player -> com.senne.imttech.security.os.SecurityOs.open(player, AUTH_YARD))
                .step(20, () -> desktop(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.ACCESS)))
                .pause(100)
                .close(0));
    }

    private static void addRooms() {
        // ---------------------------------------------------------------- Torens en bunkers uit kamers (hoofdstuk 51)
        BlockPos y = ROOMS_YARD;
        add("Towers and bunkers built from rooms", "Hoofdstuk 51. In de wereld wordt elke Biebia's Industries Tower nu uit kamers "
                + "gestapeld: de lobby onderaan en de lasergang, de kluis en het dak bovenaan liggen vast; daartussen 5 tot 8 "
                + "verdiepingen in een willekeurige keuze en volgorde (de beveiliging met de kern zit er altijd bij, en sinds "
                + "hoofdstuk 54 ook de generatorkamer), uit het lab, het archief, de hangar, de woonverdieping en zes nieuwe: de "
                + "sportzaal, de kas, de bioscoop, het zwembad, het museum en de serverzaal. De generatorkamer voedt de hele toren: "
                + "vier reactorcellen en de stroomkast achter obsidiaan, met als enige doorgang een poort van rayshields. Erin kom "
                + "je door de kern te hacken, met de staff-keycard van een bewaker (de soldaten in de lobby en voor de poort), of "
                + "via de zwakke plek (het luik VENT 3B van gebarsten tegels in de oostmuur). Hier staan twee van zulke torens "
                + "naast elkaar: kijk hoe ze verschillen (en dat de liftkoker overal doorloopt). Daarna een bunker uit zalen, hier op vlak land: de ingang, 3 tot 6 zalen "
                + "(de reactor altijd; verder de grote hal, de wapenkamer, het lab, de cellen, de garage, de kantine, de "
                + "radiokamer) en de kluis. In een echte wereld zit hij in een berg, zo lang als de berg toelaat.",
                new Vec3(y.getX() + 55.5, y.getY() + 60, y.getZ() + 150.5), 0, 0, s -> s
                .onServer(0, player -> VisualCheck.buildRoomsYard(player, ROOMS_YARD))
                .lookAt(40, new Vec3(y.getX() + 50.5, y.getY() + 45, y.getZ() + 20.5))
                .pause(120)
                .view(0, y.getX() + 20.5, y.getY() + 20, y.getZ() + 75.5, 0, 0)
                .lookAt(5, new Vec3(y.getX() + 20.5, y.getY() + 30, y.getZ() + 20.5))
                .pause(100)
                .view(0, y.getX() + 80.5, y.getY() + 20, y.getZ() + 75.5, 0, 0)
                .lookAt(5, new Vec3(y.getX() + 80.5, y.getY() + 30, y.getZ() + 20.5))
                .pause(100)
                .view(0, y.getX() + 17.5, y.getY() + 12, y.getZ() + 230, 0, 0)
                .lookAt(5, new Vec3(y.getX() + 17.5, y.getY() + 3, y.getZ() + 250.5))
                .pause(100)
                .view(0, y.getX() + 70.5, y.getY() + 40, y.getZ() + 290.5, 0, 0)
                .lookAt(5, new Vec3(y.getX() + 17.5, y.getY() + 4, y.getZ() + 290.5))
                .pause(120));
    }

    /**
     * Hoofdstuk 52: de Glock 19. Op het wapenveld, met de Gunsmith Table en de Reloading Bench naast je.
     */
    private static void addGuns() {
        // ---------------------------------------------------------------- Vuurwapens: de Glock 19 (hoofdstuk 52)
        BlockPos w = ShowcaseScene.WEAPONS;
        Vec3 stand = new Vec3(w.getX() + 0.5, w.getY() + 1, w.getZ() + 0.5);
        add("Glock 19: parts, magazines and casings", "Hoofdstuk 52. Een Glock 19 (uit zes onderdelen, hier van staal). Je richt "
                + "over de korrel en de keep en schiet drie keer: de slede schiet terug, een gele vlam, de huls springt rechts "
                + "uit het pistool en tinkelt op de vloer; rechtsonder telt het magazijn af ('13 +1 / 15'). Dan R: het magazijn "
                + "valt eruit (je hebt geen pouch, dus het komt in je inventaris). Je scrolt naar het magazijn met 4 patronen, "
                + "rechtsklik: het pistool komt erbij en je linkerhand duwt het erin. Terug naar het pistool: de slede gaat "
                + "vanzelf naar voren (de eerste patroon in de kamer, dus 3 + 1). Dan een schot met een storing (de huls blijft "
                + "klem zitten, half uit de uitwerpopening): 'JAMMED - PRESS R', en R doet een tap-rack: een klap op het magazijn, "
                + "de slede naar achteren, de huls valt eruit. Dan H: het pistool bekijken. Dan het lege magazijn in je hand: "
                + "elke R duwt er een patroon in, en U (unload, hoofdstuk 54) haalt er weer een uit. Je kijkt naar de hulzen op "
                + "de grond en raapt ze op met rechtsklik. Dan de Gunsmith Table (een pistool van een frame, slede en "
                + "trekker; links je losse onderdelen om erin te slepen; wat ontbreekt staat gestippeld ernaast, en wat onder "
                + "de slede zit kun je pas pakken als die eraf is). Dan het tabblad Make parts van die tafel (hoofdstuk 78: "
                + "onderdelen maak je alleen nog daar, niet in de crafting table): de stalen slede is gekozen, rechts '5 / 5' "
                + "in het groen, en Make maakt hem (daarna '0 / 5' in het rood, en de tegel wordt donker). Dan de Laser Rifle: "
                + "titanium en netherite naast elkaar, en elk onderdeel kost 2 Nether Quartz erbij. Tot slot de Reloading Bench "
                + "(huls + kogelpunt + Crushed Gunpowder = een 9mm-patroon; dat poeder maakt een Crusher, 10 uit één buskruit, "
                + "hoofdstuk 76).",
                stand, 90, 4, s -> s
                .onServer(0, ShowcaseScene::buildWeapons)
                .onServer(0, player -> buildGunBenches(player, w.above()))
                .onServer(0, ShowcaseFeatures::giveGlock)
                .onServer(0, ShowcaseFeatures::giveGunsmithMaterials)
                .stand(0, stand.x, stand.y, stand.z, 90, 3)
                .step(0, () -> hotbar(0))
                .pause(40)
                .key(0, () -> Minecraft.getInstance().options.keyUse, true)
                .pause(20)
                .key(0, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .key(10, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .key(10, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .key(15, () -> Minecraft.getInstance().options.keyUse, false)
                .pause(20)
                // R zonder pouch: het magazijn eruit; scrollen naar het magazijn van 4, rechtsklik, en terug.
                .step(0, () -> net.minecraft.client.KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()))
                .pause(35)
                .step(0, () -> hotbar(1))
                .pause(20)
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .pause(40)
                .step(0, () -> hotbar(0))
                .pause(45)
                // Een storing, en de tap-rack.
                .onServer(0, player -> com.senne.imttech.config.ImtConfig.Guns.JAM_CHANCE = 100)
                .key(0, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .onServer(5, player -> com.senne.imttech.config.ImtConfig.Guns.JAM_CHANCE = 0.4f)
                .pause(35)
                .step(0, () -> net.minecraft.client.KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()))
                .pause(55)
                .step(0, () -> net.minecraft.client.KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_INSPECT.getKey()))
                .pause(60)
                // Het lege magazijn vullen.
                .step(0, () -> hotbar(3))
                .pause(25)
                .step(0, () -> net.minecraft.client.KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()))
                .step(14, () -> net.minecraft.client.KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()))
                .step(14, () -> net.minecraft.client.KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()))
                .pause(20)
                // U: een patroon er weer uit (hoofdstuk 54).
                .step(0, () -> net.minecraft.client.KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_UNLOAD.getKey()))
                .pause(30)
                // De hulzen oprapen: ze liggen rechts van je (het noorden).
                .step(0, () -> hotbar(4))
                .lookAt(5, new Vec3(stand.x + 0.4, stand.y + 0.05, stand.z - 1.3))
                .tap(20, () -> Minecraft.getInstance().options.keyUse)
                .pause(30)
                // De twee banken.
                .lookAt(0, new Vec3(stand.x - 1.5, stand.y + 0.5, stand.z - 1.5))
                .onServer(15, player -> openBench(player, w.above().offset(-2, 0, -2)))
                .step(2, () -> gunsmithTab(false, com.senne.imttech.client.guns.GunsmithMakePanel.Group.GLOCK, stack -> false))
                .pause(110)
                // Hoofdstuk 78: het tabblad "Make parts": een stalen slede maken, en de onderdelen van de laser rifle.
                .step(0, () -> gunsmithTab(true, com.senne.imttech.client.guns.GunsmithMakePanel.Group.GLOCK,
                        stack -> com.senne.imttech.guns.GunPartItem.partOf(stack) == com.senne.imttech.guns.GunPart.SLIDE
                                && com.senne.imttech.guns.GunPartItem.materialOf(stack) == com.senne.imttech.guns.GunMaterial.STEEL))
                .pause(70)
                .step(0, ShowcaseFeatures::pressMake)
                .pause(50)
                .step(0, () -> gunsmithTab(true, com.senne.imttech.client.guns.GunsmithMakePanel.Group.RIFLE,
                        stack -> com.senne.imttech.weapons.LaserPartItem.partOf(stack) == com.senne.imttech.weapons.LaserPart.FRAME
                                && com.senne.imttech.weapons.LaserPartItem.materialOf(stack) == com.senne.imttech.guns.GunMaterial.TITANIUM))
                .pause(80)
                .step(0, () -> gunsmithTab(false, com.senne.imttech.client.guns.GunsmithMakePanel.Group.GLOCK, stack -> false))
                .close(0)
                .lookAt(5, new Vec3(stand.x - 1.5, stand.y + 0.5, stand.z + 2.5))
                .onServer(15, player -> openBench(player, w.above().offset(-2, 0, 2)))
                .pause(70)
                .close(0)
                .onServer(0, player -> player.getInventory().clearContent()));
    }

    private static void addGear() {
        // ---------------------------------------------------------------- Vuurwapens: de Glock 19 (hoofdstuk 52)
        BlockPos w = ShowcaseScene.WEAPONS;
        Vec3 stand = new Vec3(w.getX() + 0.5, w.getY() + 1, w.getZ() + 0.5);
        add("Holster, pouches and the quickdraw wheel", "Hoofdstuk 52, stap 2. Je draagt een Holster (met je Glock 19 erin), een "
                + "Grenade Pouch (twee EMP-granaten en een flashbang) en een Magazine Pouch (twee magazijnen): eerst zie je ze in "
                + "je inventaris in de drie eigen vakjes rechts van je poppetje (zonder Curios; met Curios zitten ze in "
                + "Curios-slots). Je houdt een steen vast. Dan het wiel (linker Alt): je pistool, je granaten per soort met "
                + "hoeveel, en 'Put away'. Het pistool: de steen valt op de grond en het pistool komt uit je holster omhoog in je "
                + "hand (trekken duurt even). Twee schoten, dan R: de magazijnpouch gaat open en je kiest het volle magazijn; "
                + "het oude komt op zijn plek in de pouch. Dan het wiel: 'Put away', het pistool gaat terug in de holster. Dan "
                + "een EMP-granaat uit het wiel, gooien, en de tweede komt vanzelf in je hand. Dan in derde persoon: de "
                + "holster met het pistool op je rechterheup, de granaatpouch links en de magazijnpouch achter, en je ziet wat "
                + "erin zit (de granaten en de magazijnen steken eruit). Tot slot (hoofdstuk 54) de magazijnpouch open uit het "
                + "wiel: een scherm met zijn vakjes boven je inventaris, om magazijnen erin te stoppen of eruit te halen (er "
                + "zijn ook eigen toetsen voor, in Opties > Besturing).",
                GameType.SURVIVAL, stand, 90, 4, s -> s
                .onServer(0, ShowcaseScene::buildWeapons)
                .onServer(0, ShowcaseFeatures::giveGear)
                .stand(0, stand.x, stand.y, stand.z, 90, 3)
                .step(0, () -> hotbar(0))
                .pause(20)
                .open(0, () -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player))
                .pause(80)
                .close(0)
                .pause(20)
                // Het wiel: het pistool trekken.
                .wheel(0, true)
                .pause(50)
                .step(0, () -> wheelChoose(0))
                .wheel(2, false)
                .pause(40)
                .key(0, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .key(10, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .pause(20)
                // R: de magazijnpouch.
                .step(0, () -> net.minecraft.client.KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()))
                .pause(50)
                .step(0, () -> magPick(0))
                .pause(60)
                // Wegsteken.
                .wheel(0, true)
                .pause(40)
                .step(0, () -> wheelChoosePutAway())
                .wheel(2, false)
                .pause(40)
                // Een granaat, en de volgende komt vanzelf.
                .wheel(0, true)
                .pause(40)
                .step(0, () -> wheelChooseAction(com.senne.imttech.guns.Gear.ACTION_DRAW_GRENADE, -1))
                .wheel(2, false)
                .pause(30)
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .pause(60)
                .camera(0, net.minecraft.client.CameraType.THIRD_PERSON_FRONT)
                .pause(60)
                .camera(0, net.minecraft.client.CameraType.THIRD_PERSON_BACK)
                .pause(50)
                .camera(0, net.minecraft.client.CameraType.FIRST_PERSON)
                // De magazijnpouch open (hoofdstuk 54).
                .onServer(10, player -> com.senne.imttech.guns.Gear.openPouch(player, com.senne.imttech.guns.GearItem.Kind.MAG_POUCH.ordinal()))
                .pause(80)
                .close(0)
                .onServer(0, player -> player.getInventory().clearContent()));
    }

    private static void addSkills() {
        // ---------------------------------------------------------------- Vuurwapens: de Glock 19 (hoofdstuk 52)
        BlockPos w = ShowcaseScene.WEAPONS;
        Vec3 stand = new Vec3(w.getX() + 0.5, w.getY() + 1, w.getZ() + 0.5);
        add("Gun skills", "Hoofdstuk 52, stap 3. Alles wat je met een pistool doet geeft ervaring (een schot, een treffer, "
                + "herladen, een tap-rack, een patroon in een magazijn, trekken); elke keer dat de balk vol is krijg je een "
                + "skillpunt, en elk volgend punt kost meer. Je begint hier met 7 punten. De toets Gun skills (F6): links de "
                + "globale skills (voor elk vuurwapen, 5% per niveau, een niveau kost het dubbele), rechts die van de Glock 19 "
                + "zelf (8% per niveau); ze tellen op. Ik leer Reload op de Glock (1 punt), nog eens (2 punten), en Reload "
                + "globaal (2 punten): onderaan staat wat dat samen oplevert. Daarna een magazijnwissel die merkbaar sneller "
                + "gaat dan in de vorige showcase.",
                stand, 90, 4, s -> s
                .onServer(0, ShowcaseScene::buildWeapons)
                .onServer(0, ShowcaseFeatures::giveGear)
                .onServer(0, player -> player.setData(com.senne.imttech.registry.ModAttachments.GUN_SKILLS,
                        new com.senne.imttech.guns.GunSkills(20, 7, 7, java.util.Map.of())))
                .stand(0, stand.x, stand.y, stand.z, 90, 3)
                .pause(20)
                .open(0, com.senne.imttech.client.guns.SkillTreeScreen::new)
                .pause(70)
                .onServer(0, player -> com.senne.imttech.guns.GunSkillsServer.learn(player, "glock_19", com.senne.imttech.guns.GunSkill.RELOAD))
                .pause(40)
                .onServer(0, player -> com.senne.imttech.guns.GunSkillsServer.learn(player, "glock_19", com.senne.imttech.guns.GunSkill.RELOAD))
                .pause(40)
                .onServer(0, player -> com.senne.imttech.guns.GunSkillsServer.learn(player, com.senne.imttech.guns.GunSkills.GLOBAL, com.senne.imttech.guns.GunSkill.RELOAD))
                .pause(80)
                .close(0)
                // Trekken en herladen, nu sneller.
                .wheel(10, true)
                .pause(30)
                .step(0, () -> wheelChoose(0))
                .wheel(2, false)
                .pause(30)
                .step(0, () -> net.minecraft.client.KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()))
                .pause(25)
                .step(0, () -> magPick(1))
                .pause(60)
                .onServer(0, player -> player.setData(com.senne.imttech.registry.ModAttachments.GUN_SKILLS, com.senne.imttech.guns.GunSkills.EMPTY))
                .onServer(0, player -> player.getInventory().clearContent()));
    }


    private static void addLaserParts() {
        // ---------------------------------------------------------------- Lasergeweren uit onderdelen (hoofdstuk 55)
        BlockPos w = ShowcaseScene.WEAPONS;
        Vec3 stand = new Vec3(w.getX() + 0.5, w.getY() + 1, w.getZ() + 0.5);
        add("Laser guns from parts: the Laser Workbench, the Nether Star and the sling", "Hoofdstuk 55. De lasergeweren bouw je nu "
                + "net als de Glock uit zes onderdelen (frame, receiver, emitter, optics, capacitor, trigger) in titanium of "
                + "netherite, plus een Nether Star als kern en (bij de rifle, sniper en shotgun) een sling. Je houdt een titanium "
                + "rifle vast: je ziet hem uit zijn onderdelen (rechts de gloeiende capacitor, achteraan de Nether Star) en "
                + "schiet drie keer. Dan een netherite sniper: het metaal is donker paarsbruin; je richt en schiet. Dan een rifle "
                + "zonder Nether Star: klik, 'NO NETHER STAR - LASER WORKBENCH'. Dan een rifle met een kapotte emitter: 'EMITTER "
                + "BROKEN'. Tot slot de Laser Workbench met een half gebouwd geweer erop: links je losse onderdelen, de Nether Star "
                + "en de Gun Sling; wat ontbreekt staat gestippeld (optics, capacitor, trigger, core, sling); slepen zoals bij de "
                + "Gunsmith Table. Elk onderdeel slijt een beetje per schot (de helft van de Glock, geen storingen); versleten maakt "
                + "zijn eigen cijfer slechter, kapot = het geweer doet niets tot je het vervangt.",
                stand, 90, 4, s -> s
                .onServer(0, ShowcaseScene::buildWeapons)
                .onServer(0, player -> buildLaserBench(player, w.above()))
                .onServer(0, ShowcaseFeatures::giveLaserParts)
                .stand(0, stand.x, stand.y, stand.z, 90, 3)
                .step(0, () -> hotbar(0))
                .pause(40)
                .key(0, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .key(10, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .key(10, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .pause(25)
                .step(0, () -> hotbar(1))
                .pause(30)
                .key(0, () -> Minecraft.getInstance().options.keyUse, true)
                .pause(25)
                .key(0, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .key(25, () -> Minecraft.getInstance().options.keyUse, false)
                .pause(15)
                .step(0, () -> hotbar(2))
                .pause(25)
                .key(0, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .pause(45)
                .step(0, () -> hotbar(3))
                .pause(25)
                .key(0, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .pause(45)
                // De werkbank.
                .lookAt(0, new Vec3(stand.x - 1.5, stand.y + 0.5, stand.z + 0.5))
                .onServer(15, player -> openBench(player, w.above().offset(-2, 0, 0)))
                .pause(130)
                .close(0)
                .onServer(0, player -> player.getInventory().clearContent()));

        add("Laser pistol in the holster, guns on your slings, cells in the pouch", "Hoofdstuk 55. Een geweer met een sling past in "
                + "een van de twee sling-vakjes (op je rug en voor je borst; in je inventaris staan ze onder het craftraster, met "
                + "Curios in het slot 'Sling'), en het laserpistool past in de holster. Je draagt een lege holster, een rifle op je "
                + "rug, een sniper voor je borst en een magazijnpouch met twee energiecellen en een Glock-magazijn; je houdt je "
                + "Glock vast. Het wiel (linker Alt): het geweer op je rug; de Glock valt NIET op de grond maar gaat in de "
                + "holster (jouw keuze), en de rifle komt van je rug (dat duurt langer dan uit de holster). Twee schoten, dan R: "
                + "de pouch toont de cellen, je kiest de volle; de oude cel komt op zijn plek. Dan het wiel: de Glock uit de holster; "
                + "de rifle gaat terug op je rug. In derde persoon: de rifle schuin over je rug, de sniper voor je borst, de banden "
                + "over je schouders, de cellen in de pouch. Tot slot de skilltree met het tabblad Rifle: elk lasergeweer heeft "
                + "zijn eigen rij (Clearing en Magazine handling zijn er alleen voor de Glock).",
                GameType.SURVIVAL, stand, 90, 4, s -> s
                .onServer(0, ShowcaseScene::buildWeapons)
                .onServer(0, ShowcaseFeatures::giveSlingGear)
                .stand(0, stand.x, stand.y, stand.z, 90, 3)
                .step(0, () -> hotbar(0))
                .pause(20)
                .open(0, () -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player))
                .pause(80)
                .close(0)
                .pause(15)
                // Het wiel: de rifle van je rug; de Glock gaat in de holster.
                .wheel(0, true)
                .pause(50)
                .step(0, () -> wheelChooseAction(com.senne.imttech.guns.Gear.ACTION_DRAW_SLING, 0))
                .wheel(2, false)
                .pause(50)
                .key(0, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .key(10, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .pause(20)
                // R: de cellen in de pouch.
                .step(0, () -> net.minecraft.client.KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()))
                .pause(50)
                .step(0, () -> magPick(0))
                .pause(70)
                // Het wiel: de Glock; de rifle gaat terug op je rug.
                .wheel(0, true)
                .pause(40)
                .step(0, () -> wheelChooseAction(com.senne.imttech.guns.Gear.ACTION_DRAW_GUN, -1))
                .wheel(2, false)
                .pause(50)
                .camera(0, net.minecraft.client.CameraType.THIRD_PERSON_BACK)
                .pause(60)
                .camera(0, net.minecraft.client.CameraType.THIRD_PERSON_FRONT)
                .pause(60)
                .camera(0, net.minecraft.client.CameraType.FIRST_PERSON)
                // De skilltree: het tabblad van de rifle.
                .open(10, com.senne.imttech.client.guns.SkillTreeScreen::new)
                .step(2, () -> skillTab("laser_rifle"))
                .pause(80)
                .close(0)
                .onServer(0, player -> player.getInventory().clearContent()));

        // ---------------------------------------------------------------- How it works (hoofdstuk 63)
        add("How it works (hold W on an item)", "Hoofdstuk 63, zoals Ponder van Create. In je werkbalk ligt een Battery "
                + "Casing en je muis staat erop: in de tooltip staat 'Hold [W]: How it works'. Dan wordt W echt ingedrukt: de "
                + "regel loopt vol en na 0,6 seconde gaat de animatie van de Power Battery open. Let op: de doos van casing valt "
                + "laag voor laag op zijn plek (met een tikje), een raam van Battery Glass dat één groot raam wordt, de cellen "
                + "erin, een poort in de wand, een kabel die van een zonnepaneel en een warmtegenerator naar de poort getekend "
                + "wordt (de muis houdt de rechterknop ingedrukt), het balkje STORED, en het echte batterijscherm als plaatje "
                + "met vier pijltjes. Halverwege springt hij met het pijltje naar het volgende hoofdstukje, draait het wereldje "
                + "rond en zoomt in. Dan Esc (terug in je inventaris), het knopje '?' rechtsboven en het overzicht.",
                at(ShowcaseScene.START), 180, 0, s -> s
                .onServer(0, player -> {
                    player.getInventory().clearContent();
                    player.getInventory().setItem(0, new ItemStack(ModItems.BATTERY_CASING.get()));
                    player.getInventory().selected = 1;
                })
                .open(10, () -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player))
                .step(5, () -> {
                    if (Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen screen) {
                        HowToTour.mouseTo(screen.getGuiLeft() + 16, screen.getGuiTop() + 150);
                    }
                })
                .pause(50)
                .step(0, () -> DevKeys.press(org.lwjgl.glfw.GLFW.GLFW_KEY_W, true))
                .pause(20)
                .step(0, () -> DevKeys.press(org.lwjgl.glfw.GLFW.GLFW_KEY_W, false))
                .step(0, () -> HowToTour.mouseTo(2, 2))
                .pause(420)
                // Het pijltje naar rechts: het volgende hoofdstukje; dan het wereldje draaien en inzoomen.
                .step(0, () -> {
                    if (Minecraft.getInstance().screen != null) {
                        Minecraft.getInstance().screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, 0, 0);
                    }
                })
                .pause(160)
                .step(0, () -> {
                    if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.howto.HowToScreen screen) {
                        screen.devCamera(-35.0f, 50.0f, 1.5f);
                    }
                })
                .pause(160)
                .close(0)
                .step(10, () -> {
                    if (Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen screen) {
                        HowToTour.mouseTo(screen.getGuiLeft() + 164, screen.getGuiTop() + 11);
                    }
                })
                .pause(40)
                .step(0, () -> {
                    if (Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen screen) {
                        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.client.event.ScreenEvent
                                .MouseButtonPressed.Pre(screen, screen.getGuiLeft() + 164, screen.getGuiTop() + 11, 0));
                    }
                })
                .pause(80)
                .close(0)
                .close(5)
                .onServer(0, player -> player.getInventory().clearContent()));

        // ---------------------------------------------------------------- De armory (hoofdstuk 67)
        BlockPos ay = ARMORY_YARD;
        add("Armory: Pegboard and Ammo Box", "Hoofdstuk 67. Een wand van 5 x 2 Pegboards vol wapens (sniper, rifle, shotgun, "
                + "Glock, laserpistool, magazijnen, cellen, granaten, holster, pouches, sling, triggers, Ion Blaster) en vier Ammo "
                + "Boxes ervoor: patronen, hulzen, magazijnen en cellen, met op het etiket een plaatje en het aantal. Let op: met de "
                + "rifle in je hand een blauw kader op het vrije paneel rechts (daar komt hij), rechtsklik: hij hangt. Met een lege "
                + "hand een wit kader om de rifle; sneak + rechtsklik: hij draait (loop naar rechts), rechtsklik: hij is weer in je "
                + "hand. Dan een leeg magazijn op de kist met patronen: rechtsklik ingedrukt houden laadt patroon na patroon (de "
                + "kist telt af, de HUD zegt 'Hold right-click: ... in box'). Met een lege hand komt er een stapel patronen uit "
                + "(in je inventaris, niet in je hand), en met de stapel in je hand gaat hij er weer in.",
                GameType.SURVIVAL, new Vec3(ay.getX() + 0.5, ay.getY() + 1, ay.getZ() - 0.5), 0, 10, s -> s
                .onServer(0, player -> VisualCheck.buildArmoryAt(player.serverLevel(), ay))
                .onServer(0, player -> {
                    player.getInventory().clearContent();
                    ItemStack rifle = com.senne.imttech.weapons.LaserGunItem.complete(com.senne.imttech.weapons.GunType.RIFLE,
                            com.senne.imttech.guns.GunMaterial.NETHERITE);
                    player.getInventory().setItem(0, rifle);
                    player.getInventory().setItem(2, com.senne.imttech.guns.MagazineItem.of(com.senne.imttech.guns.FirearmType.GLOCK_19, 0));
                })
                .stand(0, ay.getX() + 0.5, ay.getY() + 1, ay.getZ() - 0.5, 0, 10)
                .step(0, () -> hotbar(1))
                .lookAt(0, new Vec3(ay.getX() + 0.5, ay.getY() + 2.6, ay.getZ() + 3.9))
                .pause(80)
                // De rifle op het vrije paneel rechts. Hoofdstuk 70: in het blok ervoor (dichtbij genoeg).
                .stand(0, ay.getX() - 1.5, ay.getY() + 1, ay.getZ() + 2.3, 0, 0)
                .step(0, () -> hotbar(0))
                .lookAt(0, new Vec3(ay.getX() - 1.5, ay.getY() + 2.95, ay.getZ() + 3.9))
                .pause(50)
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .pause(40)
                // Lege hand: draaien en weer pakken. Eerst sluipen, dan mikken (sluipen zet je ogen lager).
                .step(0, () -> hotbar(1))
                .pause(30)
                .key(0, () -> Minecraft.getInstance().options.keyShift, true)
                .lookAt(5, new Vec3(ay.getX() - 1.5, ay.getY() + 2.95, ay.getZ() + 3.9))
                .tap(2, () -> Minecraft.getInstance().options.keyUse)
                .key(5, () -> Minecraft.getInstance().options.keyShift, false)
                .lookAt(0, new Vec3(ay.getX() - 1.5, ay.getY() + 2.95, ay.getZ() + 3.9))
                .pause(40)
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .pause(40)
                // Een leeg magazijn op de kist met patronen: rechtsklik inhouden.
                .stand(0, ay.getX() + 1.5, ay.getY() + 1, ay.getZ() + 0.2, 0, 0)
                .step(0, () -> hotbar(2))
                .lookAt(0, new Vec3(ay.getX() + 1.5, ay.getY() + 1.3, ay.getZ() + 2.5))
                .pause(20)
                .key(0, () -> Minecraft.getInstance().options.keyUse, true)
                .pause(80)
                .key(0, () -> Minecraft.getInstance().options.keyUse, false)
                .pause(30)
                // Lege hand: een stapel eruit; met de stapel in je hand weer erin.
                .step(0, () -> hotbar(3))
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .pause(40)
                // De stapel kwam in het eerste lege vakje: vakje 0 (in vakje 1 zit de rifle weer).
                .step(0, () -> hotbar(0))
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .pause(60)
                .onServer(0, player -> player.getInventory().clearContent()));

        // ---------------------------------------------------------------- Granaten terugwerpen (hoofdstuk 69)
        BlockPos tb = ShowcaseScene.WEAPONS;
        add("Throwing grenades back", "Hoofdstuk 69. Je hebt een Glock in je hand en een lege holster om; voor je voeten ligt "
                + "een scherpe EMP-granaat. Rechtsklik erop: let op, de granaat zit nu in je hand (hij glimt), de Glock zit in je "
                + "holster, en onderaan je scherm telt hij af ('Live EMP Grenade: 4.0 s! Right-click to throw it back'). "
                + "Rechtsklik: hij vliegt terug naar de robot bij de muur en gaat daar af, met wat er van zijn lont over was. Dan "
                + "plakt er een Sticky EMP op jou (even van voren bekeken): je eerste rechtsklik trekt hem eraf, en je gooit hem "
                + "tegen de muur. Tot slot hangt er een Remote Frag van 'Raven' op de muur: sneak + rechtsklik met een lege hand "
                + "haalt hem eraf ('Taken off, still live: Raven's Frag Trigger can set it off'), en je hangt hem verderop "
                + "('Hung up, still live'). Raven drukt op zijn trigger: hij gaat af waar jij hem hing.",
                new Vec3(tb.getX() + 0.5, tb.getY() + 1, tb.getZ() + 0.5), -90, 30, s -> s
                .onServer(0, ShowcaseScene::buildWeapons)
                .onServer(0, player -> {
                    player.getInventory().clearContent();
                    player.getInventory().setItem(0, com.senne.imttech.guns.FirearmItem.loaded(
                            com.senne.imttech.guns.FirearmType.GLOCK_19, com.senne.imttech.guns.GunMaterial.STEEL));
                    com.senne.imttech.guns.Gear.set(player, com.senne.imttech.guns.GearItem.Kind.HOLSTER,
                            new ItemStack(ModItems.HOLSTER.get()));
                })
                .step(0, () -> hotbar(0))
                .stand(0, tb.getX() + 0.5, tb.getY() + 1, tb.getZ() + 0.5, -90, 30)
                .onServer(10, player -> {
                    var ball = new com.senne.imttech.weapons.RollingGrenade(
                            com.senne.imttech.registry.ModEntities.ROLLING_EMP_GRENADE.get(), player.serverLevel());
                    ball.setPos(tb.getX() + 2.5, tb.getY() + 1.0, tb.getZ() + 0.5);
                    ball.arm(130);
                    player.serverLevel().addFreshEntity(ball);
                })
                .lookAt(5, new Vec3(tb.getX() + 2.5, tb.getY() + 1.15, tb.getZ() + 0.5))
                .pause(40)
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .pause(50)
                // Terug, met een boogje over de robot heen.
                .lookAt(0, new Vec3(tb.getX() + 7.5, tb.getY() + 3.4, tb.getZ() - 1.5))
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .pause(80)
                // Een sticky plakt op jou.
                .onServer(0, player -> {
                    var sticky = new com.senne.imttech.weapons.StickyGrenade(
                            com.senne.imttech.registry.ModEntities.STICKY_EMP_GRENADE.get(), player.serverLevel());
                    sticky.setPos(player.getX() + 3, player.getY() + 1.0, player.getZ());
                    sticky.shoot(-1, 0.05, 0, 0.8f, 0);
                    player.serverLevel().addFreshEntity(sticky);
                })
                .onServer(8, player -> player.serverLevel().getEntitiesOfClass(com.senne.imttech.weapons.StickyGrenade.class,
                        player.getBoundingBox().inflate(4)).forEach(sticky -> sticky.arm(120)))
                .camera(0, net.minecraft.client.CameraType.THIRD_PERSON_FRONT)
                .pause(40)
                .camera(0, net.minecraft.client.CameraType.FIRST_PERSON)
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .pause(40)
                .lookAt(0, new Vec3(tb.getX() + 10.0, tb.getY() + 3.0, tb.getZ() + 2.5))
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .pause(60)
                // Een Remote Frag van Raven op de muur: eraf, en verderop weer op.
                .onServer(0, player -> {
                    var level = player.serverLevel();
                    BlockPos frag = tb.offset(9, 2, 2);
                    level.setBlockAndUpdate(frag, ModBlocks.REMOTE_FRAG.get().defaultBlockState()
                            .setValue(com.senne.imttech.weapons.RemoteChargeBlock.FACING, net.minecraft.core.Direction.WEST));
                    if (level.getBlockEntity(frag) instanceof com.senne.imttech.weapons.RemoteChargeBlockEntity charge) {
                        charge.restore(new com.senne.imttech.weapons.LiveCharge(RAVEN_TRIGGER, java.util.Optional.empty(), "Raven",
                                java.util.Optional.empty()));
                    }
                })
                .stand(0, tb.getX() + 7.5, tb.getY() + 1, tb.getZ() + 2.5, -90, 10)
                .lookAt(5, new Vec3(tb.getX() + 9.85, tb.getY() + 2.5, tb.getZ() + 2.5))
                .pause(30)
                // Eerst sluipen, dan mikken (sluipen zet je ogen lager).
                .key(0, () -> Minecraft.getInstance().options.keyShift, true)
                .lookAt(5, new Vec3(tb.getX() + 9.85, tb.getY() + 2.5, tb.getZ() + 2.5))
                .tap(2, () -> Minecraft.getInstance().options.keyUse)
                .key(5, () -> Minecraft.getInstance().options.keyShift, false)
                .pause(60)
                .stand(0, tb.getX() + 8.0, tb.getY() + 1, tb.getZ() - 4.5, -90, 10)
                .lookAt(5, new Vec3(tb.getX() + 10.0, tb.getY() + 2.5, tb.getZ() - 4.5))
                .pause(20)
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .pause(60)
                // Ver weg, en Raven drukt op zijn Frag Trigger (die van de Remote Frag hoort nu waar hij hangt).
                .stand(0, tb.getX() - 2.5, tb.getY() + 1, tb.getZ() + 0.5, -100, 5)
                .onServer(30, player -> {
                    ItemStack trigger = new ItemStack(ModItems.FRAG_TRIGGER.get());
                    trigger.set(com.senne.imttech.registry.ModDataComponents.FRAG_TRIGGER.get(),
                            new com.senne.imttech.weapons.TriggerData(RAVEN_TRIGGER, List.of()));
                    com.senne.imttech.weapons.TriggerItem.fire(player.serverLevel(), player, trigger);
                })
                .pause(80)
                .onServer(0, player -> {
                    player.getInventory().clearContent();
                    com.senne.imttech.guns.Gear.set(player, com.senne.imttech.guns.GearItem.Kind.HOLSTER, ItemStack.EMPTY);
                }));

        // ---------------------------------------------------------------- Het Pegboard bijgesteld (hoofdstuk 70)
        Vec3 freePanel = new Vec3(ay.getX() - 1.5, ay.getY() + 2.5, ay.getZ() + 3.9);
        add("Pegboard: lock, shadow, corner", "Hoofdstuk 70 en 71. Het armory-veldje, nu met een zijmuur links: de linkerkolom "
                + "van de wand is een hoek, met een paneel tegen elke muur. Op de zijmuur hangt een vastgezette Laser Rifle (een "
                + "grijs randje eromheen, op het paneel geverfd) en de lichtgrijze schaduw van een shotgun die iemand pakte. "
                + "Onderaan de vastgezette granaten: van de flashbang (bijna wit), de frag (groen) en de EMP (roodachtig) is "
                + "alleen de schaduw over, elk in zijn eigen kleur. Dan sta je 3 blokken van de wand met een magazijn: "
                + "rechtsklik zegt 'Too far away' onderaan, en er komt geen kader. In het blok voor de wand hang je een Glock "
                + "op; sneak + linksklik: een grijs randje eromheen (het paneel breekt niet). Rechtsklik met een lege hand pakt "
                + "hem: zijn schaduw blijft. Met een titanium Glock in je hand staat het blauwe kader precies op de schaduw; "
                + "rechtsklik: hij hangt er, weer met zijn randje. Sneak + linksklik (met een Glock in je hand) haalt het "
                + "randje weg. Tot slot de onderste rij van dichtbij: de flashbang is even groot als de andere granaten, zijn "
                + "gouden pin steekt een beetje boven de rest uit.",
                GameType.SURVIVAL, new Vec3(ay.getX() - 0.5, ay.getY() + 1, ay.getZ() + 0.5), 0, 0, s -> s
                .onServer(0, player -> VisualCheck.buildArmoryAt(player.serverLevel(), ay))
                .onServer(0, player -> {
                    player.getInventory().clearContent();
                    player.getInventory().setItem(0, com.senne.imttech.guns.FirearmItem.loaded(
                            com.senne.imttech.guns.FirearmType.GLOCK_19, com.senne.imttech.guns.GunMaterial.STEEL));
                    player.getInventory().setItem(2, com.senne.imttech.guns.FirearmItem.loaded(
                            com.senne.imttech.guns.FirearmType.GLOCK_19, com.senne.imttech.guns.GunMaterial.TITANIUM));
                    player.getInventory().setItem(3, com.senne.imttech.guns.MagazineItem.of(com.senne.imttech.guns.FirearmType.GLOCK_19, 15));
                })
                // De hoek, en de zijmuur van dichtbij.
                .stand(0, ay.getX() - 0.5, ay.getY() + 1, ay.getZ() + 0.5, 0, 0)
                .step(0, () -> hotbar(1))
                .lookAt(0, new Vec3(ay.getX() + 2.6, ay.getY() + 2.9, ay.getZ() + 3.4))
                .pause(70)
                .stand(0, ay.getX() + 0.9, ay.getY() + 1, ay.getZ() + 2.2, 0, 0)
                .lookAt(0, new Vec3(ay.getX() + 2.94, ay.getY() + 3.5, ay.getZ() + 2.2))
                .pause(70)
                // Te ver weg (3 blokken), met een magazijn.
                .stand(0, ay.getX() - 1.5, ay.getY() + 1, ay.getZ() + 0.3, 0, 0)
                .step(0, () -> hotbar(3))
                .lookAt(0, freePanel)
                .pause(30)
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .pause(60)
                // Dichtbij: de Glock ophangen, vastzetten, pakken, en een andere Glock op zijn schaduw.
                .stand(0, ay.getX() - 1.5, ay.getY() + 1, ay.getZ() + 2.3, 0, 0)
                .step(0, () -> hotbar(0))
                .lookAt(0, freePanel)
                .pause(30)
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .pause(30)
                .step(0, () -> hotbar(1))
                .key(0, () -> Minecraft.getInstance().options.keyShift, true)
                .lookAt(5, freePanel)
                .tap(2, () -> Minecraft.getInstance().options.keyAttack)
                .key(5, () -> Minecraft.getInstance().options.keyShift, false)
                .lookAt(0, freePanel)
                .pause(50)
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .pause(40)
                .step(0, () -> hotbar(2))
                .pause(50)
                .tap(0, () -> Minecraft.getInstance().options.keyUse)
                .pause(40)
                .step(0, () -> hotbar(1))
                .key(0, () -> Minecraft.getInstance().options.keyShift, true)
                .lookAt(5, freePanel)
                .tap(2, () -> Minecraft.getInstance().options.keyAttack)
                .key(5, () -> Minecraft.getInstance().options.keyShift, false)
                .pause(50)
                // De onderste rij van de wand van dichtbij: de flashbang naast de andere granaten.
                .stand(0, ay.getX() + 0.4, ay.getY() + 1, ay.getZ() + 1.6, 0, 0)
                .lookAt(0, new Vec3(ay.getX() + 0.4, ay.getY() + 2.45, ay.getZ() + 3.9))
                .pause(80)
                .onServer(0, player -> player.getInventory().clearContent()));

        // ---------------------------------------------------------------- De militaire rugzakken (hoofdstuk 72)
        BlockPos packs = BackpackTour.YARD;
        add("Military backpacks", "Hoofdstuk 72, stap 1. Een veldje met zes neergezette rugzakken: voor de drie maten (een "
                + "olijfgroene Assault Pack, een rode Patrol Pack en een Rucksack in camo), achter een blauwe Rucksack, een "
                + "zwarte Assault Pack en een oranje Patrol Pack (rood + geel gemengd). Jij draagt een olijfgroene Patrol Pack "
                + "vol spullen. Eerst rits je hem open (anderhalve seconde stilstaan, met een balkje onder je vizier en het "
                + "geluid van een rits); dan staat zijn scherm open, met de sorteerknop rechtsboven: alles schuift netjes op "
                + "een rij. Dan van achteren en van voren in derde persoon: de rugzak op je rug, de banden over je borst. Je "
                + "pakt een Assault Pack in je hand en zet hem met sneak + rechtsklik op de grond; sneak + rechtsklik met een "
                + "lege hand pakt hem weer op (je rug is vol, dus in je inventaris). Tot slot het wiel (linker Alt) met de "
                + "knop Backpack rechts.",
                GameType.SURVIVAL, new Vec3(packs.getX() + 0.5, packs.getY() + 1, packs.getZ() - 3.5), 0, 20, s -> s
                .onServer(0, player -> BackpackTour.buildYard(player.serverLevel(), packs))
                .onServer(0, player -> BackpackTour.wear(player, com.senne.imttech.backpack.BackpackItem.Size.PATROL, -1, false))
                .lookAt(0, new Vec3(packs.getX() + 0.5, packs.getY() + 1.4, packs.getZ() + 2))
                .pause(70)
                // Openritsen, het scherm, sorteren.
                .onServer(0, com.senne.imttech.backpack.Backpacks::openWorn)
                .waitUntil(60, () -> Minecraft.getInstance().screen instanceof com.senne.imttech.client.backpack.BackpackScreen)
                .pause(60)
                .onServer(0, com.senne.imttech.backpack.Backpacks::sort)
                .pause(60)
                .close(10)
                // Van achteren en van voren.
                .camera(0, CameraType.THIRD_PERSON_BACK)
                .pause(50)
                .camera(0, CameraType.THIRD_PERSON_FRONT)
                .pause(50)
                .camera(0, CameraType.FIRST_PERSON)
                // Een Assault Pack neerzetten en oppakken.
                .onServer(0, player -> player.getInventory().setItem(0, BackpackTour.pack(com.senne.imttech.backpack.BackpackItem.Size.ASSAULT, -1)))
                .step(0, () -> hotbar(0))
                .stand(0, packs.getX() + 0.5, packs.getY() + 1, packs.getZ() - 3.5, 0, 0)
                .lookAt(0, new Vec3(packs.getX() + 0.5, packs.getY() + 1, packs.getZ() - 1.5))
                .pause(30)
                .key(0, () -> Minecraft.getInstance().options.keyShift, true)
                .lookAt(5, new Vec3(packs.getX() + 0.5, packs.getY() + 1, packs.getZ() - 1.5))
                .tap(2, () -> Minecraft.getInstance().options.keyUse)
                .pause(40)
                .lookAt(0, new Vec3(packs.getX() + 0.5, packs.getY() + 1.3, packs.getZ() - 1.5))
                .tap(2, () -> Minecraft.getInstance().options.keyUse)
                .key(5, () -> Minecraft.getInstance().options.keyShift, false)
                .pause(40)
                // Het wiel met de rugzakknop.
                .wheel(0, true)
                .pause(60)
                .wheel(0, false)
                .pause(10)
                .onServer(0, player -> {
                    player.getInventory().clearContent();
                    com.senne.imttech.guns.Gear.setBack(player, ItemStack.EMPTY);
                }));
        add("MOLLE pouches", "Hoofdstuk 72, stap 2. Hetzelfde veldje; op de voorste rij rugzakken hangen nu MOLLE-pouches (een IFAK "
                + "met een rood kruis, een dump pouch, magazijn-, granaat- en utility pouches). Jij draagt een Rucksack met vier "
                + "pouches en hebt een Glock in je hand, zonder heup-pouch. Eerst het scherm van de rugzak: rechts de strook "
                + "MOLLE met de vier pouches. Dan het wiel: rechts kleine knoppen voor de rugzak, elke pouch, en de gouden "
                + "appel en het drankje uit je IFAK (met hoeveel). Je opent de IFAK zonder rits, en daarna eet je via het wiel "
                + "meteen een gouden appel (je hartjes worden geel). Dan R: het keuzescherm toont de magazijnen in je "
                + "MOLLE-magazijnpouch (je draagt geen heup-pouch, dus alleen die), je kiest het volle. Je "
                + "schiet een paar keer: de hulzen springen eruit en je dump pouch raapt ze vanzelf op. Tot slot van achteren in "
                + "derde persoon: de pouches op je rugzak.",
                GameType.SURVIVAL, new Vec3(packs.getX() + 0.5, packs.getY() + 1, packs.getZ() - 3.5), 0, 20, s -> s
                .onServer(0, player -> BackpackTour.buildYard(player.serverLevel(), packs))
                .onServer(0, player -> {
                    BackpackTour.wear(player, com.senne.imttech.backpack.BackpackItem.Size.RUCKSACK, -1, false);
                    player.setHealth(12);
                    ItemStack gun = com.senne.imttech.guns.FirearmItem.loaded(com.senne.imttech.guns.FirearmType.GLOCK_19,
                            com.senne.imttech.guns.GunMaterial.STEEL);
                    com.senne.imttech.guns.FirearmItem.setMagazine(gun, com.senne.imttech.guns.MagazineItem.of(com.senne.imttech.guns.FirearmType.GLOCK_19, 2));
                    player.getInventory().setItem(0, gun);
                })
                .step(0, () -> hotbar(0))
                .lookAt(0, new Vec3(packs.getX() + 0.5, packs.getY() + 1.4, packs.getZ() + 1.5))
                .pause(60)
                // Het scherm met de MOLLE-strook.
                .onServer(0, com.senne.imttech.backpack.Backpacks::openWorn)
                .waitUntil(60, () -> Minecraft.getInstance().screen instanceof com.senne.imttech.client.backpack.BackpackScreen)
                .pause(70)
                .close(10)
                // Het wiel, de IFAK openen, en een gouden appel uit het wiel.
                .wheel(0, true)
                .pause(60)
                .wheel(0, false)
                .onServer(5, player -> com.senne.imttech.backpack.Molle.openWornPouch(player, 0))
                .pause(50)
                .close(10)
                .onServer(0, player -> com.senne.imttech.backpack.Molle.useIfak(player, 0, 0))
                .pause(50)
                // R: de magazijnen in de MOLLE-pouch.
                .step(0, () -> net.minecraft.client.KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()))
                .pause(50)
                .step(0, () -> magPick(0))
                .pause(40)
                // Schieten: de dump pouch raapt de hulzen op.
                .lookAt(0, new Vec3(packs.getX() + 4, packs.getY() + 1.2, packs.getZ() + 4))
                .tap(5, () -> Minecraft.getInstance().options.keyAttack)
                .tap(8, () -> Minecraft.getInstance().options.keyAttack)
                .tap(8, () -> Minecraft.getInstance().options.keyAttack)
                .pause(50)
                .camera(0, CameraType.THIRD_PERSON_BACK)
                .pause(60)
                .camera(0, CameraType.FIRST_PERSON)
                .onServer(0, player -> {
                    player.getInventory().clearContent();
                    com.senne.imttech.guns.Gear.setBack(player, ItemStack.EMPTY);
                }));
        add("Opening a backpack from behind", "Hoofdstuk 72, stap 3. Lotte staat met haar rug naar je toe, met een bruine Patrol "
                + "Pack. Jij sluipt achter haar met een lege hand; kijk je naar haar rugzak, dan staat onder je vizier \"Right-click: "
                + "open Lotte's backpack\" (op haar heupen zou het zakkenrollen zijn). Je rits hem open: zachter dan je eigen "
                + "rugzak, maar ze kan het horen. Dan haar scherm, met links de meter NOTICED. Je pakt een paar dingen: bij elke "
                + "greep loopt de meter op, en als je even niets doet zakt hij weer. Dan draait ze zich om en ziet je: betrapt. "
                + "Jouw scherm gaat dicht, zij krijgt een melding met je naam, en jij licht een tijdje op (zie je van achteren in "
                + "derde persoon).",
                GameType.SURVIVAL, new Vec3(packs.getX() + 0.5, packs.getY() + 1, packs.getZ() - 0.2), 180, 15, s -> s
                .onServer(0, player -> BackpackTour.buildYard(player.serverLevel(), packs))
                .onServer(0, player -> {
                    player.getInventory().clearContent();
                    player.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
                    ServerPlayer victim = DevWorld.fakePlayerAt(player.serverLevel(), "Lotte", packs.offset(0, 1, -2));
                    victim.teleportTo(player.serverLevel(), packs.getX() + 0.5, packs.getY() + 1, packs.getZ() - 1.5, 180, 0);
                    victim.setYHeadRot(180);
                    com.senne.imttech.guns.Gear.setBack(victim, BackpackTour.withPouches(BackpackTour.loaded(
                            com.senne.imttech.backpack.BackpackItem.Size.PATROL, 0x6B5A3A)));
                })
                .stand(0, packs.getX() + 0.5, packs.getY() + 1, packs.getZ() + 0.3, 180, 15)
                .key(0, () -> Minecraft.getInstance().options.keyShift, true)
                .lookAt(10, new Vec3(packs.getX() + 0.5, packs.getY() + 2.2, packs.getZ() - 1.4))
                .pause(70)
                .onServer(0, player -> {
                    ServerPlayer victim = player.getServer().getPlayerList().getPlayerByName("Lotte");
                    if (victim != null) {
                        com.senne.imttech.backpack.BackpackTheft.tryStart(player, victim);
                    }
                })
                .waitUntil(60, () -> Minecraft.getInstance().screen instanceof com.senne.imttech.client.backpack.BackpackScreen)
                .key(0, () -> Minecraft.getInstance().options.keyShift, false)
                .pause(50)
                .step(0, () -> theftClick(0))
                .pause(15)
                .step(0, () -> theftClick(27 + 3 + 0))
                .pause(15)
                .step(0, () -> theftClick(1))
                .pause(15)
                .step(0, () -> theftClick(27 + 3 + 1))
                .pause(60)
                .onServer(0, player -> {
                    ServerPlayer victim = player.getServer().getPlayerList().getPlayerByName("Lotte");
                    if (victim != null) {
                        victim.teleportTo(player.serverLevel(), victim.getX(), victim.getY(), victim.getZ(), 0, 0);
                        victim.setYHeadRot(0);
                    }
                })
                .pause(50)
                .camera(0, CameraType.THIRD_PERSON_BACK)
                .pause(60)
                .camera(0, CameraType.FIRST_PERSON)
                .onServer(0, player -> {
                    player.removeEffect(net.minecraft.world.effect.MobEffects.GLOWING);
                    player.getInventory().clearContent();
                    ServerPlayer victim = player.getServer().getPlayerList().getPlayerByName("Lotte");
                    if (victim != null) {
                        victim.teleportTo(player.serverLevel(), victim.getX(), victim.getY() - 40, victim.getZ(), 0, 0);
                    }
                }));
        add("Player bodies", "Hoofdstuk 72, stap 4. Lotte staat voor je, met een helm, een borstplaat, een zwaard, diamanten en haar "
                + "rugzak. Ze valt dood: er valt niets op de grond, maar er ligt een lichaam, op haar buik, met haar harnas aan en "
                + "haar rugzak bovenop. Kijk je naar de rugzak, dan zegt de hint \"Right-click: open the backpack. Sneak: take it "
                + "(3 s)\"; op het lichaam \"Right-click: search Lotte's body\". Je doorzoekt het lichaam: bovenaan haar harnas, "
                + "schild en holster, eronder haar inventaris op dezelfde plekken als bij haar. Je haalt de diamanten eruit. Dan "
                + "rits je haar rugzak open en kijk je erin, en met sneak + rechtsklik haal je hem eraf (3 s stilstaan): nu draag "
                + "jij hem. Tot slot je eigen lichaam (zoals na doodgaan): in het scherm staat rechtsboven \"Everything back\"; "
                + "na 3 s stilstaan zit alles weer op zijn plek (je harnas aan, je Glock en je houweel in je werkbalk).",
                GameType.SURVIVAL, new Vec3(packs.getX() + 0.5, packs.getY() + 1, packs.getZ() - 4), 0, 20, s -> s
                .command(0, "gamerule keepInventory false")
                .onServer(0, player -> {
                    BackpackTour.buildYard(player.serverLevel(), packs);
                    player.getInventory().clearContent();
                    com.senne.imttech.guns.Gear.setBack(player, ItemStack.EMPTY);
                    ServerPlayer victim = DevWorld.fakePlayerAt(player.serverLevel(), "Lotte", packs.offset(0, 1, -2));
                    if (!victim.isAlive()) {
                        victim.setHealth(victim.getMaxHealth());
                    }
                    victim.teleportTo(player.serverLevel(), packs.getX() + 0.5, packs.getY() + 1, packs.getZ() - 1.5, 180, 0);
                    victim.getInventory().setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
                    victim.getInventory().setItem(12, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 9));
                    victim.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(net.minecraft.world.item.Items.IRON_HELMET));
                    victim.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
                    com.senne.imttech.guns.Gear.setBack(victim, BackpackTour.withPouches(BackpackTour.loaded(
                            com.senne.imttech.backpack.BackpackItem.Size.PATROL, 0x6B5A3A)));
                })
                .lookAt(0, new Vec3(packs.getX() + 0.5, packs.getY() + 1.6, packs.getZ() - 1.5))
                .pause(50)
                .onServer(0, player -> {
                    ServerPlayer victim = player.getServer().getPlayerList().getPlayerByName("Lotte");
                    if (victim != null) {
                        victim.hurt(victim.damageSources().fellOutOfWorld(), 1000);
                    }
                })
                .pause(40)
                .stand(0, packs.getX() + 0.5, packs.getY() + 1, packs.getZ() - 3.4, 0, 0)
                .lookAt(5, new Vec3(packs.getX() + 0.4, packs.getY() + 1.4, packs.getZ() - 1.3))
                .pause(50)
                .lookAt(0, new Vec3(packs.getX() + 0.8, packs.getY() + 1.3, packs.getZ() - 2.0))
                .pause(50)
                // Doorzoeken, en de diamanten eruit (vakje 12 van het lichaam staat in rij 1, het vierde: slot 12).
                .onServer(0, player -> {
                    com.senne.imttech.body.PlayerBodyEntity body = BackpackTour.bodyNear(player);
                    if (body != null) {
                        com.senne.imttech.body.BodyMenu.open(player, body);
                    }
                })
                .pause(60)
                .step(0, () -> theftQuickMove(12))
                .pause(40)
                .close(10)
                // De rugzak openritsen en erin kijken, en hem dan eraf halen.
                .onServer(0, player -> {
                    com.senne.imttech.body.PlayerBodyEntity body = BackpackTour.bodyNear(player);
                    if (body != null) {
                        com.senne.imttech.backpack.BackpackZip.start(player, com.senne.imttech.backpack.BackpackPlace.body(body, player));
                    }
                })
                .waitUntil(60, () -> Minecraft.getInstance().screen instanceof com.senne.imttech.client.backpack.BackpackScreen)
                .pause(50)
                .close(10)
                .onServer(0, player -> {
                    com.senne.imttech.body.PlayerBodyEntity body = BackpackTour.bodyNear(player);
                    if (body != null) {
                        com.senne.imttech.body.PlayerBodies.takePack(player, body);
                    }
                })
                .pause(80)
                .camera(0, CameraType.THIRD_PERSON_BACK)
                .pause(40)
                .camera(0, CameraType.FIRST_PERSON)
                // Je eigen lichaam, met "Everything back".
                .onServer(0, player -> {
                    com.senne.imttech.body.PlayerBodyEntity old = BackpackTour.bodyNear(player);
                    if (old != null) {
                        old.discard();
                    }
                    player.getInventory().clearContent();
                    com.senne.imttech.guns.Gear.setBack(player, ItemStack.EMPTY);
                    com.senne.imttech.body.PlayerBodyEntity body = com.senne.imttech.backpack.BackpackRegistry.PLAYER_BODY.get().create(player.serverLevel());
                    if (body == null) {
                        return;
                    }
                    body.moveTo(packs.getX() + 0.5, packs.getY() + 1, packs.getZ() - 1.5, 30, 0);
                    body.setOwner(player.getUUID(), player.getGameProfile().getName());
                    body.loot().setItem(0, com.senne.imttech.guns.FirearmItem.loaded(com.senne.imttech.guns.FirearmType.GLOCK_19,
                            com.senne.imttech.guns.GunMaterial.STEEL));
                    body.loot().setItem(1, new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE));
                    body.loot().setItem(com.senne.imttech.body.PlayerBodyEntity.ARMOR + 2, new ItemStack(net.minecraft.world.item.Items.IRON_CHESTPLATE));
                    body.setPack(BackpackTour.withPouches(BackpackTour.loaded(com.senne.imttech.backpack.BackpackItem.Size.ASSAULT, -1)));
                    player.serverLevel().addFreshEntity(body);
                    com.senne.imttech.body.BodyMenu.open(player, body);
                })
                .pause(70)
                .onServer(0, player -> {
                    if (player.containerMenu instanceof com.senne.imttech.body.BodyMenu menu && menu.body() != null) {
                        com.senne.imttech.body.PlayerBodies.startRestore(player, menu.body());
                    }
                })
                .pause(100)
                .onServer(0, player -> {
                    com.senne.imttech.body.PlayerBodyEntity old = BackpackTour.bodyNear(player);
                    if (old != null) {
                        old.discard();
                    }
                    player.getInventory().clearContent();
                    com.senne.imttech.guns.Gear.setBack(player, ItemStack.EMPTY);
                }));
        add("Backpacks on the pegboard, Raven packs", "Hoofdstuk 72, stap 5. Achter het veldje hangt een wand van zes Pegboards: "
                + "links een olijfgroene Assault Pack, in het midden een Patrol Pack in camo, rechts een blauwe Rucksack, elk met "
                + "zijn MOLLE-pouches erop en met de banden naar je toe; eronder de vijf losse pouches (magazijnen, granaten, dump "
                + "pouch, IFAK, utility). Je loopt naar de Rucksack toe. Dan een Raven-soldaat met zijn rug naar je toe: hij "
                + "draagt een antracietgrijze Assault Pack. Hij valt, en in zijn lichaam (rechtsklik) zit zijn rugzak. Je haalt "
                + "hem eruit (shift-klik), neemt hem in je hand en ritst hem open: patronen of een energiecel, eten, en soms een "
                + "magazijn, een onderdeel van een Glock of fakkels. Tot slot How it works van de Assault Pack: de animatie "
                + "\"Military backpacks\" (de MOLLE-pouches hebben er ook een).",
                GameType.SURVIVAL, new Vec3(packs.getX() + 0.5, packs.getY() + 1, packs.getZ() + 1.2), 0, 8, s -> s
                .onServer(0, player -> {
                    BackpackTour.buildYard(player.serverLevel(), packs);
                    BackpackTour.buildPegboard(player.serverLevel(), packs);
                    player.getInventory().clearContent();
                    com.senne.imttech.guns.Gear.setBack(player, ItemStack.EMPTY);
                })
                .pause(100)
                // Van dichtbij: de Rucksack met zijn vier pouches (het derde paneel, rechts).
                .stand(0, packs.getX() - 0.45, packs.getY() + 1, packs.getZ() + 2.8, 0, 0)
                .lookAt(0, new Vec3(packs.getX() - 0.45, packs.getY() + 2.3, packs.getZ() + 4.9))
                .pause(70)
                // Een Raven-soldaat met zijn rug naar je toe; hij valt, en je doorzoekt zijn lichaam.
                .onServer(0, player -> BackpackTour.spawnPackedRaven(player.serverLevel(), packs.getX() + 3.5, packs.getY() + 1,
                        packs.getZ() - 2.5))
                .stand(10, packs.getX() + 3.5, packs.getY() + 1, packs.getZ() + 0.5, 180, 15)
                .pause(70)
                .onServer(0, BackpackTour::dropRavens)
                .pause(30)
                .stand(0, packs.getX() + 3.5, packs.getY() + 1, packs.getZ() - 0.8, 180, 55)
                .pause(30)
                .onServer(0, BackpackTour::searchRavenBody)
                .pause(60)
                .step(0, BackpackTour::takePackFromScreen)
                .pause(30)
                .close(10)
                .onServer(0, BackpackTour::unzipPackInHand)
                .waitUntil(60, () -> Minecraft.getInstance().screen instanceof com.senne.imttech.client.backpack.BackpackScreen)
                .pause(90)
                .close(10)
                // How it works, zoals W op de rugzak in je inventaris.
                .step(0, () -> com.senne.imttech.client.howto.HowToScreen.open(
                        new ItemStack(com.senne.imttech.backpack.BackpackRegistry.ASSAULT_PACK.get())))
                .pause(300)
                .close(10)
                .onServer(0, player -> {
                    player.serverLevel().getEntitiesOfClass(com.senne.imttech.raven.RavenBodyEntity.class,
                            player.getBoundingBox().inflate(12)).forEach(net.minecraft.world.entity.Entity::discard);
                    player.getInventory().clearContent();
                }));
        // ---------------------------------------------------------------- De body armor (hoofdstuk 109)
        BlockPos vests = VestTour.YARD;
        double vx = vests.getX() + 0.5;
        double vy = vests.getY() + 1;
        double vz = vests.getZ() + 0.5;
        add("Body armor: the Plate Carrier", "Hoofdstuk 109. Je draagt een antracietgrijs vest met SWAT op je rug: eerst van "
                + "achteren in derde persoon (de tekst op het zwarte klittenbandvlak), dan van voren (drie magazijnvakjes op je "
                + "buik, twee met een magazijn erin). Links naast je hartjes staan je twee plates, voor en achter. Dan het scherm "
                + "van het vest: links de stalen plate voor en de polymeren achter (hoeveel ze nog tegenhouden en welk deel van "
                + "een treffer), in het midden het vest van achteren, rechts de magazijnen, de tekst en de 16 kleuren; je typt "
                + "K9 UNIT in het oranje en ziet het meteen op het vest. Daarna een Raven-soldaat met een vest (RAVEN in het rood "
                + "op zijn rug), en tot slot de wand met drie vesten (SWAT, ARMY in camo en POLICE in het blauw) en de vier "
                + "plates, van polymeer tot netherite.",
                GameType.SURVIVAL, new Vec3(vx, vy, vz), 0, 10, s -> s
                .onServer(0, player -> VestTour.buildYard(player.serverLevel(), vests))
                .onServer(0, VestTour::wearSwat)
                .pause(40)
                .camera(0, CameraType.THIRD_PERSON_BACK)
                .pause(60)
                .stand(0, vx, vy, vz, 180, 0)
                .camera(0, CameraType.THIRD_PERSON_FRONT)
                .pause(60)
                .camera(0, CameraType.FIRST_PERSON)
                .stand(0, vx, vy, vz, 0, 10)
                .pause(50)
                .onServer(0, com.senne.imttech.armor.Vests::openWorn)
                .waitUntil(60, () -> Minecraft.getInstance().screen instanceof com.senne.imttech.client.armor.VestScreen)
                .pause(50)
                .step(0, () -> {
                    if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.armor.VestScreen screen) {
                        screen.type("K9 UNIT", net.minecraft.world.item.DyeColor.ORANGE);
                    }
                })
                .pause(70)
                .close(10)
                .onServer(0, player -> VestTour.spawnRaven(player.serverLevel(), vests))
                .stand(10, vests.getX() + 2.5, vy, vests.getZ() - 4.0, 0, 15)
                .pause(70)
                .stand(0, vx, vy, vests.getZ() + 1.5, 0, 5)
                .pause(80)
                .onServer(0, VestTour::cleanUp));
        // ---------------------------------------------------------------- De Glock in de stijl van TACZ (hoofdstuk 74)
        BlockPos range = ShowcaseScene.WEAPONS;
        Vec3 aimFrom = new Vec3(range.getX() + 0.5, range.getY() + 1, range.getZ() + 0.5);
        add("Glock 19 in the style of TACZ", "Hoofdstuk 74. De Glock 19 is een echt 3D-model (nagemeten van een foto van een "
                + "echte Glock 19 Gen 3) met echte geluiden. Eerst van dichtbij in je hand: links GLOCK 19 / AUSTRIA 9x19 op de "
                + "slede, de groeven, de keep met de witte U. Dan H: hij draait en je ziet de rechterkant (de uitwerpopening met "
                + "het blok van de loop, MADE IN AUSTRIA) en de bovenkant. Dan richt je (het witte stipje van de korrel staat "
                + "in de U) en schiet je drie keer: elk schot is een van drie varianten, de slede slaat terug en de hulzen "
                + "stuiteren. Dan R: het magazijn valt eruit. Je pakt het magazijn met 4 patronen (een patroon bovenin) en dan "
                + "het lege (de opvolger bovenin, geen patroon). Tot slot de Glock in derde persoon.",
                aimFrom, 90, 4, s -> s
                .onServer(0, ShowcaseScene::buildWeapons)
                .onServer(0, ShowcaseFeatures::giveGlock)
                .stand(0, aimFrom.x, aimFrom.y, aimFrom.z, 90, 3)
                .step(0, () -> hotbar(0))
                .pause(60)
                .step(0, () -> net.minecraft.client.KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_INSPECT.getKey()))
                .pause(80)
                .key(0, () -> Minecraft.getInstance().options.keyUse, true)
                .pause(25)
                .key(0, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .key(14, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .key(14, () -> Minecraft.getInstance().options.keyAttack, true).key(2, () -> Minecraft.getInstance().options.keyAttack, false)
                .key(20, () -> Minecraft.getInstance().options.keyUse, false)
                .pause(20)
                .step(0, () -> net.minecraft.client.KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()))
                .pause(40)
                .step(0, () -> hotbar(1))
                .pause(50)
                .step(0, () -> hotbar(3))
                .pause(50)
                .step(0, () -> hotbar(0))
                .camera(10, CameraType.THIRD_PERSON_FRONT)
                .pause(60)
                .camera(0, CameraType.FIRST_PERSON)
                .onServer(0, player -> player.getInventory().clearContent()));
    }


    /**
     * Hoofdstuk 103, de grote update: de MP5 en de AK-47 (de schakelaar), de AWP (de grendel, de kijker, de tweepoot), de
     * Remington 870 (de pomp en de buis), de attachments op de Gunsmith Table en op de lasergeweren, en de
     * Raven-soldaten met de nieuwe wapens.
     */
    private static void addBigUpdate() {
        // ---------------------------------------------------------------- De grote update: meer vuurwapens en attachments (hoofdstuk 103)
        BlockPos w = ShowcaseScene.WEAPONS;
        Vec3 stand = new Vec3(w.getX() + 0.5, w.getY() + 1, w.getZ() + 0.5);
        Supplier<KeyMapping> use = () -> Minecraft.getInstance().options.keyUse;
        Supplier<KeyMapping> attack = () -> Minecraft.getInstance().options.keyAttack;
        Supplier<KeyMapping> sneak = () -> Minecraft.getInstance().options.keyShift;
        Runnable reload = () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey());
        Runnable selector = () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_FIRE_MODE.getKey());
        Runnable inspect = () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_INSPECT.getKey());
        int bolt = Math.round(com.senne.imttech.guns.FirearmType.AWP.cycleTicks());
        int pump = Math.round(com.senne.imttech.guns.FirearmType.REMINGTON_870.cycleTicks());
        int shell = Math.round(com.senne.imttech.config.ImtConfig.Guns.LOAD_SHELL_TICKS);

        add("MP5 and AK-47: the fire selector", "Hoofdstuk 103, de grote update. In je werkbalk een MP5 en een AK-47 (echte "
                + "3D-modellen, nagemeten van een foto, zoals de Glock). Eerst de MP5: je richt door de ring van het vizier en "
                + "schiet een keer (SEMI). Dan X: rechtsonder staat BURST, en een klik geeft drie schoten. Nog eens X: AUTO, en "
                + "je houdt de knop in. Nog eens X: SAFE, de trekker doet niets ('Safety on'). Dan R: het magazijn valt eruit; "
                + "je pakt het lange magazijn (het MP5 Extended Magazine, 40 patronen) en duwt het erin met rechtsklik: "
                + "rechtsonder 40 + 1, en het steekt verder onder het wapen uit. Dan de AK-47: een schot, X (AUTO, de AK heeft "
                + "geen BURST) en een salvo: hij klinkt zwaarder en schopt harder. Dan een AK-47 met een vol magazijn en een "
                + "lege kamer: zodra je hem pakt, trekt je rechterhand de spanhendel. Dan H (bekijken) en de AK-47 in derde "
                + "persoon.",
                stand, 90, 3, s -> s
                .onServer(0, ShowcaseScene::buildWeapons)
                .onServer(0, ShowcaseFeatures::giveAutomatics)
                .stand(0, stand.x, stand.y, stand.z, 90, 3)
                .step(0, () -> hotbar(0))
                .pause(50)
                .key(0, use, true)
                .pause(20)
                .key(0, attack, true).key(2, attack, false)
                .pause(20)
                .step(0, selector)
                .pause(20)
                .key(0, attack, true).key(2, attack, false)
                .pause(25)
                .step(0, selector)
                .pause(20)
                .key(0, attack, true).key(24, attack, false)
                .pause(15)
                .key(0, use, false)
                .step(10, selector)
                .pause(20)
                .key(0, attack, true).key(2, attack, false)
                .pause(35)
                .step(0, selector)
                // R zonder pouch: het magazijn eruit; het lange magazijn erin.
                .step(15, reload)
                .pause(35)
                .step(0, () -> hotbar(2))
                .pause(25)
                .tap(0, use)
                .pause(45)
                .step(0, () -> hotbar(0))
                .pause(50)
                // De AK-47.
                .step(0, () -> hotbar(1))
                .pause(50)
                .key(0, use, true)
                .pause(20)
                .key(0, attack, true).key(2, attack, false)
                .pause(20)
                .step(0, selector)
                .pause(20)
                .key(0, attack, true).key(20, attack, false)
                .pause(15)
                .key(0, use, false)
                .pause(20)
                // Een AK-47 met een lege kamer: de spanhendel.
                .step(0, () -> hotbar(5))
                .pause(60)
                .step(0, () -> hotbar(1))
                .step(20, inspect)
                .pause(70)
                .camera(10, CameraType.THIRD_PERSON_FRONT)
                .pause(60)
                .camera(0, CameraType.FIRST_PERSON)
                .onServer(0, player -> player.getInventory().clearContent()));

        add("AWP: the bolt, the Sniper Scope and the bipod", "Hoofdstuk 103. Eerst een AWP zonder kijker: je richt over het "
                + "ijzeren vizier en schiet. Rechtsonder staat nu in het rood PRESS R en de trekker doet niets: het is een "
                + "grendelgeweer. R: je rechterhand tilt de knop op, trekt de grendel naar achteren (de huls springt eruit), "
                + "duwt hem naar voren en drukt de knop omlaag. Dan de AWP met de Sniper Scope en de Bipod: je richt en kijkt "
                + "door de ring met het dradenkruis (geen zwart beeld), en de zoom gaat van 2x naar 4x, 7x en 10x (in het spel "
                + "scroll je; rechtsonder ZOOM). Een schot, en weer R. Dan sneak je en sta je stil: rechtsonder BIPOD DOWN, en "
                + "het schot schopt bijna niet. Tot slot derde persoon terwijl je sneakt: anderen zien de pootjes uitgeklapt.",
                stand, 90, 2, s -> s
                .onServer(0, ShowcaseScene::buildWeapons)
                .onServer(0, ShowcaseFeatures::giveSniper)
                .stand(0, stand.x, stand.y, stand.z, 90, 2)
                .step(0, () -> hotbar(1))
                .pause(50)
                .key(0, use, true)
                .pause(25)
                .key(0, attack, true).key(2, attack, false)
                .pause(20)
                .key(0, use, false)
                .pause(25)
                .key(0, attack, true).key(2, attack, false)
                .pause(25)
                .step(0, reload)
                .pause(bolt + 20)
                // Met de kijker: 2x, 4x, 7x, 10x.
                .step(0, () -> hotbar(0))
                .step(0, () -> com.senne.imttech.client.guns.FirearmControl.setScopeZoom(2))
                .pause(45)
                .key(0, use, true)
                .pause(30)
                .step(0, () -> com.senne.imttech.client.guns.FirearmControl.setScopeZoom(4))
                .pause(20)
                .step(0, () -> com.senne.imttech.client.guns.FirearmControl.setScopeZoom(7))
                .pause(20)
                .step(0, () -> com.senne.imttech.client.guns.FirearmControl.setScopeZoom(10))
                .pause(30)
                .key(0, attack, true).key(2, attack, false)
                .pause(15)
                .key(0, use, false)
                .step(10, reload)
                .pause(bolt + 15)
                // De tweepoot: sneaken en stilstaan.
                .step(0, () -> com.senne.imttech.client.guns.FirearmControl.setScopeZoom(4))
                .key(0, sneak, true)
                .pause(30)
                .key(0, use, true)
                .pause(30)
                .key(0, attack, true).key(2, attack, false)
                .pause(20)
                .key(0, use, false)
                .step(10, reload)
                .pause(bolt + 10)
                .camera(0, CameraType.THIRD_PERSON_FRONT)
                .pause(70)
                .key(0, sneak, false)
                .pause(30)
                .camera(0, CameraType.FIRST_PERSON)
                .onServer(0, player -> player.getInventory().clearContent()));

        add("Remington 870: the pump and the tube", "Hoofdstuk 103. Een pompgeweer: elk schot is acht korrels hagel die "
                + "uitwaaieren (kijk naar de husks: van dichtbij raken ze allemaal). Na het schot staat er PRESS R: R trekt het "
                + "voorhout naar achteren (de rode huls springt eruit) en duwt het weer naar voren. Nog een schot en nog eens "
                + "pompen. Dan een lege 870: je houdt R in en je linkerhand duwt de patronen een voor een door de laadopening "
                + "onderaan (het wapen kantelt), tot de buis vol is (6). R: pompen (5 + 1). U haalt er een patroon weer uit. "
                + "Tot slot H (bekijken) en derde persoon.",
                stand, 90, 3, s -> s
                .onServer(0, ShowcaseScene::buildWeapons)
                .onServer(0, player -> VisualCheck.giveLongGun(player, com.senne.imttech.guns.FirearmType.REMINGTON_870))
                .stand(0, stand.x, stand.y, stand.z, 90, 3)
                .step(0, () -> hotbar(0))
                .pause(50)
                .key(0, use, true)
                .pause(20)
                .key(0, attack, true).key(2, attack, false)
                .pause(25)
                .step(0, reload)
                .pause(pump + 12)
                .key(0, attack, true).key(2, attack, false)
                .pause(20)
                .key(0, use, false)
                .step(5, reload)
                .pause(pump + 20)
                // Een lege 870: R inhouden (eerst ingedrukt, dan de klik: zie VisualCheck.longGunTour).
                .step(0, () -> hotbar(5))
                .pause(35)
                .key(0, () -> com.senne.imttech.client.ImtKeys.GUN_RELOAD, true)
                .step(1, reload)
                .key(shell * 6 + 10, () -> com.senne.imttech.client.ImtKeys.GUN_RELOAD, false)
                .step(shell + 6, reload)
                .pause(pump + 20)
                .step(0, () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_UNLOAD.getKey()))
                .pause(35)
                .step(0, () -> hotbar(0))
                .step(20, inspect)
                .pause(70)
                .camera(10, CameraType.THIRD_PERSON_FRONT)
                .pause(60)
                .camera(0, CameraType.FIRST_PERSON)
                .onServer(0, player -> player.getInventory().clearContent()));

        add("Attachments: sights, suppressors, grips", "Hoofdstuk 103. Attachments maak je en zet je erop op de Gunsmith "
                + "Table. Het scherm van de tafel met een complete AK-47: boven, voor en onder het wapen staan gestippelde vakken "
                + "(SIGHT, MUZZLE, UNDERBARREL), links je attachments (bij de tweepoot 'does not fit': die is voor de AWP). Een "
                + "voor een klikken ze erop: de Red Dot Sight, de Rifle Suppressor, de Vertical Grip; rechts staat wat erop "
                + "zit. Dan het tabblad Make parts onder Attachments (de Red Dot Sight: 2 staal, 1 glasplaat, 2 redstone). "
                + "Daarna vijf wapens met alles erop: de Glock (demper, red dot: het stipje blijft in het midden), de MP5 "
                + "(demper, rechte greep: je linkerhand houdt hem vast), de AK-47 (demper, schuine greep, red dot), de AWP "
                + "(demper, tweepoot, kijker) en de Remington 870 (demper, verlengbuis: 8 patronen). Elk schot is een doffe "
                + "plof zonder vlam. Tot slot de AK-47 in derde persoon.",
                stand, 90, 3, s -> s
                .onServer(0, ShowcaseScene::buildWeapons)
                .onServer(0, player -> buildGunBenches(player, w.above()))
                .onServer(0, ShowcaseFeatures::giveLooseAttachments)
                .onServer(0, player -> VisualCheck.gunOnTable(player, w.above(), com.senne.imttech.guns.FirearmType.AK_47))
                .stand(0, stand.x, stand.y, stand.z, 90, 3)
                .lookAt(5, new Vec3(stand.x - 1.5, stand.y + 0.5, stand.z - 1.5))
                .onServer(15, player -> openBench(player, w.above().offset(-2, 0, -2)))
                .step(2, () -> gunsmithTab(false, com.senne.imttech.client.guns.GunsmithMakePanel.Group.GLOCK, stack -> false))
                .pause(80)
                .onServer(0, player -> VisualCheck.addOnsOnTable(player, w.above(), com.senne.imttech.guns.Attachment.RED_DOT))
                .pause(45)
                .onServer(0, player -> VisualCheck.addOnsOnTable(player, w.above(), com.senne.imttech.guns.Attachment.SUPPRESSOR_RIFLE))
                .pause(45)
                .onServer(0, player -> VisualCheck.addOnsOnTable(player, w.above(), com.senne.imttech.guns.Attachment.VERTICAL_GRIP))
                .pause(80)
                .step(0, () -> gunsmithTab(true, com.senne.imttech.client.guns.GunsmithMakePanel.Group.ATTACHMENTS,
                        stack -> stack.is(ModItems.attachment(com.senne.imttech.guns.Attachment.RED_DOT))))
                .pause(90)
                .step(0, () -> gunsmithTab(false, com.senne.imttech.client.guns.GunsmithMakePanel.Group.GLOCK, stack -> false))
                .close(0)
                // Vijf wapens met alles erop: richten en een gedempt schot.
                .onServer(0, VisualCheck::giveAddOns)
                .stand(5, stand.x, stand.y, stand.z, 90, 3)
                .step(0, () -> com.senne.imttech.client.guns.FirearmControl.setScopeZoom(4))
                .step(0, () -> hotbar(0)).pause(45)
                .key(0, use, true).pause(22)
                .key(0, attack, true).key(2, attack, false).pause(18)
                .key(0, use, false)
                .step(10, () -> hotbar(1)).pause(45)
                .key(0, use, true).pause(22)
                .key(0, attack, true).key(2, attack, false).pause(18)
                .key(0, use, false)
                .step(10, () -> hotbar(2)).pause(45)
                .key(0, use, true).pause(22)
                .key(0, attack, true).key(2, attack, false).pause(18)
                .key(0, use, false)
                .step(10, () -> hotbar(3)).pause(45)
                .key(0, use, true).pause(30)
                .key(0, attack, true).key(2, attack, false).pause(18)
                .key(0, use, false)
                .step(10, () -> hotbar(4)).pause(45)
                .key(0, use, true).pause(22)
                .key(0, attack, true).key(2, attack, false).pause(18)
                .key(0, use, false)
                .step(10, () -> hotbar(2))
                .camera(20, CameraType.THIRD_PERSON_FRONT)
                .pause(70)
                .camera(0, CameraType.FIRST_PERSON)
                .onServer(0, player -> player.getInventory().clearContent()));

        add("Attachments on laser guns", "Hoofdstuk 103. De Red Dot Sight, de Sniper Scope en de grips passen ook op de "
                + "lasergeweren; je zet ze erop op de Laser Workbench. Eerst die tafel met een Laser Rifle: de plekken SIGHT en "
                + "UNDERBARREL, en dan een red dot en een greep erop (het eigen gouden vizier van de rifle verdwijnt: de kijker "
                + "komt in zijn plaats). Dan in je hand: het Laser Pistol met een red dot, de Laser Rifle met een red dot en een "
                + "greep, en de Laser Sniper met de Sniper Scope: je kijkt door de ring en zoomt van 1x tot 10x, zoals bij de "
                + "AWP (zonder kijker heeft hij nog zijn oude 4x met het zwarte beeld).",
                stand, 90, 3, s -> s
                .onServer(0, ShowcaseScene::buildWeapons)
                .onServer(0, player -> buildLaserBench(player, w.above()))
                .onServer(0, ShowcaseFeatures::giveLooseAttachments)
                .onServer(0, player -> {
                    if (player.serverLevel().getBlockEntity(w.above().offset(-2, 0, 0))
                            instanceof com.senne.imttech.weapons.LaserWorkbenchBlockEntity bench) {
                        bench.items().setItem(0, com.senne.imttech.weapons.LaserGunItem.loaded(com.senne.imttech.weapons.GunType.RIFLE));
                    }
                })
                .stand(0, stand.x, stand.y, stand.z, 90, 3)
                .lookAt(5, new Vec3(stand.x - 1.5, stand.y + 0.5, stand.z))
                .onServer(15, player -> openBench(player, w.above().offset(-2, 0, 0)))
                .pause(80)
                .onServer(0, player -> VisualCheck.laserAddOns(player, w.above(), com.senne.imttech.guns.Attachment.RED_DOT))
                .pause(45)
                .onServer(0, player -> VisualCheck.laserAddOns(player, w.above(), com.senne.imttech.guns.Attachment.VERTICAL_GRIP))
                .pause(80)
                .close(0)
                .onServer(0, VisualCheck::giveLaserAttachments)
                .stand(5, stand.x, stand.y, stand.z, 90, 3)
                .step(0, () -> com.senne.imttech.client.guns.FirearmControl.setScopeZoom(2))
                .step(0, () -> hotbar(0)).pause(45)
                .key(0, use, true).pause(22)
                .key(0, attack, true).key(2, attack, false).pause(18)
                .key(0, use, false)
                .step(10, () -> hotbar(1)).pause(45)
                .key(0, use, true).pause(22)
                .key(0, attack, true).key(12, attack, false).pause(18)
                .key(0, use, false)
                .step(10, () -> hotbar(2)).pause(45)
                .key(0, use, true).pause(30)
                .step(0, () -> com.senne.imttech.client.guns.FirearmControl.setScopeZoom(5)).pause(20)
                .step(0, () -> com.senne.imttech.client.guns.FirearmControl.setScopeZoom(10)).pause(30)
                .key(0, attack, true).key(2, attack, false).pause(20)
                .key(0, use, false)
                .step(0, () -> com.senne.imttech.client.guns.FirearmControl.setScopeZoom(4))
                .pause(20)
                .onServer(0, player -> player.getInventory().clearContent()));

        BlockPos yard = VisualCheck.RAVEN_YARD;
        double yardX = yard.getX() + 0.5;
        double yardY = yard.getY() + 1;
        add("Raven soldiers with the new guns", "Hoofdstuk 103. Raven-soldaten dragen nu ook de nieuwe wapens. Vijf soldaten op "
                + "een rij, van links naar rechts met een Glock 19 (met een demper), een MP5 (red dot), een AK-47 (greep), een "
                + "AWP (de sluipschutter, altijd met zijn kijker) en een Remington 870. In rust dragen ze een lang wapen met twee "
                + "handen schuin naar de grond; dan leggen ze aan. Daarna een echte soldaat met een MP5: hij lost salvo's van "
                + "drie (je staat in creative, dus het doet je niets). Dan een sluipschutter op 30 blokken: hij legt een seconde "
                + "aan, schiet, en je hoort hem doorladen. Dan een gevallen soldaat: in zijn lichaam zitten zijn AK-47 (met de "
                + "greep er nog op) en zijn rugzak met munitie voor zijn wapen. Tot slot een kist van de bunker en de kluis van "
                + "de toren: daar vind je af en toe een attachment.",
                new Vec3(yardX, yardY, yard.getZ() - 1.5), 0, 0, s -> s
                .view(0, yardX, yardY, yard.getZ() - 1.5, 0, 0)
                .waitUntil(200, () -> VisualCheck.raidLeaveTicking(yard))
                .onServer(5, VisualCheck::buildRavenYard)
                .pause(80)
                .onServer(0, player -> VisualCheck.ravenRowAims(player, true))
                .pause(70)
                .onServer(0, VisualCheck::clearRavenYard)
                // Een echte soldaat met een MP5: salvo's van drie.
                .view(0, yardX, yardY, yard.getZ() + 10.5, 0, 0)
                .onServer(5, player -> VisualCheck.ravenLive(player, com.senne.imttech.guns.FirearmType.MP5, 9))
                .pause(150)
                .onServer(0, VisualCheck::clearRavenYard)
                // Een sluipschutter op 30 blokken.
                .view(0, yardX, yardY, yard.getZ() - 1.5, 0, 0)
                .onServer(5, player -> VisualCheck.ravenLive(player, com.senne.imttech.guns.FirearmType.AWP, 30))
                .pause(200)
                .onServer(0, VisualCheck::clearRavenYard)
                // Een gevallen soldaat en zijn buit.
                .onServer(0, player -> VisualCheck.ravenFalls(player, new BlockPos(yard.getX(), yard.getY() + 1, yard.getZ() + 8)))
                .view(10, yardX, yardY + 1.5, yard.getZ() + 5.5, 0, 38)
                .pause(50)
                .onServer(0, VisualCheck::searchNearestBody)
                .pause(80)
                .close(0)
                // De kisten.
                .view(0, yardX, yardY, yard.getZ() + 26.5, 0, 10)
                .onServer(5, player -> VisualCheck.ravenChest(player, 0, "raven_barracks"))
                .pause(70)
                .close(0)
                .onServer(5, player -> VisualCheck.ravenChest(player, 4, "biebia_vault"))
                .pause(70)
                .close(0)
                .onServer(0, VisualCheck::clearRavenYard));
    }

    /**
     * Hoofdstuk 103: een MP5 (vak 0) en een AK-47 (vak 1), het lange magazijn van de MP5 (vak 2: 40 patronen), een
     * AK-47-magazijn, losse patronen van beide kalibers, en een AK-47 met een vol magazijn maar een lege kamer (vak 5).
     */
    static void giveAutomatics(ServerPlayer player) {
        var inventory = player.getInventory();
        inventory.clearContent();
        com.senne.imttech.guns.FirearmType mp5 = com.senne.imttech.guns.FirearmType.MP5;
        com.senne.imttech.guns.FirearmType ak = com.senne.imttech.guns.FirearmType.AK_47;
        inventory.setItem(0, com.senne.imttech.guns.FirearmItem.loaded(mp5, com.senne.imttech.guns.GunMaterial.STEEL));
        inventory.setItem(1, com.senne.imttech.guns.FirearmItem.loaded(ak, com.senne.imttech.guns.GunMaterial.STEEL));
        inventory.setItem(2, com.senne.imttech.guns.MagazineItem.of(mp5, true, mp5.capacity(true)));
        inventory.setItem(3, com.senne.imttech.guns.MagazineItem.of(ak, ak.capacity()));
        inventory.setItem(4, new ItemStack(ModItems.ROUND_9MM.get(), 40));
        ItemStack unchambered = com.senne.imttech.guns.FirearmItem.withParts(ak, com.senne.imttech.guns.GunParts.classic(ak));
        com.senne.imttech.guns.FirearmItem.setMagazine(unchambered, com.senne.imttech.guns.MagazineItem.of(ak, ak.capacity()));
        com.senne.imttech.guns.FirearmItem.setSling(unchambered, true);
        inventory.setItem(5, unchambered);
        inventory.setItem(6, new ItemStack(ModItems.ROUND_762.get(), 40));
    }

    /**
     * Hoofdstuk 103: een AWP met de Sniper Scope en de Bipod (vak 0), een AWP zonder kijker (vak 1), een magazijn en
     * losse patronen.
     */
    static void giveSniper(ServerPlayer player) {
        var inventory = player.getInventory();
        inventory.clearContent();
        com.senne.imttech.guns.FirearmType awp = com.senne.imttech.guns.FirearmType.AWP;
        ItemStack scoped = com.senne.imttech.guns.FirearmItem.loaded(awp, com.senne.imttech.guns.GunMaterial.STEEL);
        com.senne.imttech.guns.FirearmItem.setAttachment(scoped, com.senne.imttech.guns.Attachment.SNIPER_SCOPE);
        com.senne.imttech.guns.FirearmItem.setAttachment(scoped, com.senne.imttech.guns.Attachment.BIPOD);
        inventory.setItem(0, scoped);
        inventory.setItem(1, com.senne.imttech.guns.FirearmItem.loaded(awp, com.senne.imttech.guns.GunMaterial.STEEL));
        inventory.setItem(2, com.senne.imttech.guns.MagazineItem.of(awp, awp.capacity()));
        inventory.setItem(3, new ItemStack(ModItems.ROUND_338.get(), 12));
    }

    /**
     * Hoofdstuk 103: losse attachments in je inventaris (voor de Gunsmith Table en de Laser Workbench): de twee kijkers,
     * de Rifle Suppressor, de twee grips en de Bipod, en materiaal om een Red Dot Sight te maken.
     */
    static void giveLooseAttachments(ServerPlayer player) {
        var inventory = player.getInventory();
        inventory.clearContent();
        com.senne.imttech.guns.Attachment[] loose = {com.senne.imttech.guns.Attachment.RED_DOT,
                com.senne.imttech.guns.Attachment.SNIPER_SCOPE, com.senne.imttech.guns.Attachment.SUPPRESSOR_RIFLE,
                com.senne.imttech.guns.Attachment.VERTICAL_GRIP, com.senne.imttech.guns.Attachment.ANGLED_GRIP,
                com.senne.imttech.guns.Attachment.BIPOD};
        for (int i = 0; i < loose.length; i++) {
            inventory.setItem(9 + i, new ItemStack(ModItems.attachment(loose[i])));
        }
        inventory.setItem(18, new ItemStack(ModItems.STEEL_INGOT.get(), 6));
        inventory.setItem(19, new ItemStack(net.minecraft.world.item.Items.GLASS_PANE, 2));
        inventory.setItem(20, new ItemStack(net.minecraft.world.item.Items.REDSTONE, 4));
    }

    /** Hoofdstuk 72, stap 4: shift-klik op dit vakje van het open scherm (een lichaam doorzoeken), zoals met je muis. */
    static void theftQuickMove(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gameMode != null && mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen) {
            mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot, 0,
                    net.minecraft.world.inventory.ClickType.QUICK_MOVE, mc.player);
        }
    }

    /** Hoofdstuk 72, stap 3: een klik (oppakken of neerleggen) op dit vakje van het open rugzakscherm, zoals met je muis. */
    static void theftClick(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gameMode != null && mc.screen instanceof com.senne.imttech.client.backpack.BackpackScreen screen) {
            mc.gameMode.handleInventoryMouseClick(screen.getMenu().containerId, slot, 0,
                    net.minecraft.world.inventory.ClickType.PICKUP, mc.player);
        }
    }

    /** Hoofdstuk 69: de Frag Trigger van "Raven" in de showcase (altijd hetzelfde nummer). */
    static final java.util.UUID RAVEN_TRIGGER = java.util.UUID.nameUUIDFromBytes("imttech-showcase-raven-frag-trigger".getBytes());

    /** Hoofdstuk 67: het veldje van de armory (zie VisualCheck.buildArmoryAt). */
    static final BlockPos ARMORY_YARD = new BlockPos(-130, -61, 330);

    /** Hoofdstuk 55: een Laser Workbench met een half gebouwde rifle erop, naast {@code stand} (tussen de twee banken van de Glock). */
    static void buildLaserBench(ServerPlayer player, BlockPos stand) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        BlockPos bench = stand.offset(-2, 0, 0);
        level.setBlockAndUpdate(bench, ModBlocks.LASER_WORKBENCH.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.EAST));
        if (level.getBlockEntity(bench) instanceof com.senne.imttech.weapons.LaserWorkbenchBlockEntity laser) {
            com.senne.imttech.weapons.LaserParts parts = com.senne.imttech.weapons.LaserParts.EMPTY
                    .with(com.senne.imttech.weapons.LaserPart.FRAME, com.senne.imttech.guns.PartState.fresh(com.senne.imttech.guns.GunMaterial.TITANIUM))
                    .with(com.senne.imttech.weapons.LaserPart.RECEIVER, new com.senne.imttech.guns.PartState(com.senne.imttech.guns.GunMaterial.NETHERITE, 64))
                    .with(com.senne.imttech.weapons.LaserPart.EMITTER, com.senne.imttech.guns.PartState.fresh(com.senne.imttech.guns.GunMaterial.TITANIUM));
            laser.items().setItem(0, com.senne.imttech.weapons.LaserGunItem.withParts(com.senne.imttech.weapons.GunType.RIFLE, parts));
        }
    }

    /**
     * Hoofdstuk 55: een titanium rifle, een netherite sniper, een rifle zonder Nether Star, een rifle met een kapotte
     * emitter, een laserpistool, en losse onderdelen, een Nether Star en een Gun Sling voor de werkbank.
     */
    static void giveLaserParts(ServerPlayer player) {
        var inventory = player.getInventory();
        inventory.clearContent();
        inventory.setItem(0, com.senne.imttech.weapons.LaserGunItem.loaded(com.senne.imttech.weapons.GunType.RIFLE));
        ItemStack sniper = com.senne.imttech.weapons.LaserGunItem.complete(com.senne.imttech.weapons.GunType.SNIPER,
                com.senne.imttech.guns.GunMaterial.NETHERITE);
        com.senne.imttech.weapons.LaserGunItem.setCell(sniper, com.senne.imttech.weapons.EnergyCellItem.FULL);
        inventory.setItem(1, sniper);
        ItemStack noCore = com.senne.imttech.weapons.LaserGunItem.loaded(com.senne.imttech.weapons.GunType.RIFLE);
        com.senne.imttech.weapons.LaserGunItem.setParts(noCore, com.senne.imttech.weapons.LaserGunItem.parts(noCore)
                .without(com.senne.imttech.weapons.LaserPart.CORE));
        inventory.setItem(2, noCore);
        ItemStack broken = com.senne.imttech.weapons.LaserGunItem.loaded(com.senne.imttech.weapons.GunType.RIFLE);
        com.senne.imttech.weapons.LaserGunItem.setParts(broken, com.senne.imttech.weapons.LaserGunItem.parts(broken)
                .with(com.senne.imttech.weapons.LaserPart.EMITTER, new com.senne.imttech.guns.PartState(com.senne.imttech.guns.GunMaterial.TITANIUM, 0)));
        inventory.setItem(3, broken);
        inventory.setItem(4, com.senne.imttech.weapons.LaserGunItem.loaded(com.senne.imttech.weapons.GunType.PISTOL));
        inventory.setItem(5, com.senne.imttech.weapons.EnergyCellItem.withCharge(100));
        inventory.setItem(9, com.senne.imttech.weapons.LaserPartItem.of(com.senne.imttech.weapons.GunType.RIFLE,
                com.senne.imttech.weapons.LaserPart.OPTICS, com.senne.imttech.guns.GunMaterial.TITANIUM));
        inventory.setItem(10, com.senne.imttech.weapons.LaserPartItem.of(com.senne.imttech.weapons.GunType.RIFLE,
                com.senne.imttech.weapons.LaserPart.CAPACITOR, com.senne.imttech.guns.GunMaterial.NETHERITE));
        inventory.setItem(11, com.senne.imttech.weapons.LaserPartItem.of(com.senne.imttech.weapons.GunType.RIFLE,
                com.senne.imttech.weapons.LaserPart.TRIGGER, com.senne.imttech.guns.GunMaterial.TITANIUM, 37));
        inventory.setItem(12, new ItemStack(net.minecraft.world.item.Items.NETHER_STAR));
        inventory.setItem(13, new ItemStack(ModItems.GUN_SLING.get()));
        inventory.setItem(14, com.senne.imttech.weapons.LaserPartItem.of(com.senne.imttech.weapons.GunType.SNIPER,
                com.senne.imttech.weapons.LaserPart.EMITTER, com.senne.imttech.guns.GunMaterial.NETHERITE));
    }

    /**
     * Hoofdstuk 55: een lege holster, een rifle met sling op je rug, een sniper met sling voor je borst, een magazijnpouch
     * met twee cellen en een Glock-magazijn, en je Glock in je hand.
     */
    static void giveSlingGear(ServerPlayer player) {
        var inventory = player.getInventory();
        inventory.clearContent();
        inventory.setItem(0, com.senne.imttech.guns.FirearmItem.loaded(com.senne.imttech.guns.FirearmType.GLOCK_19,
                com.senne.imttech.guns.GunMaterial.STEEL));
        com.senne.imttech.guns.Gear.set(player, com.senne.imttech.guns.GearItem.Kind.HOLSTER, new ItemStack(ModItems.HOLSTER.get()));
        ItemStack magazines = new ItemStack(ModItems.MAG_POUCH.get());
        com.senne.imttech.guns.GearItem.setContents(magazines, java.util.List.of(
                com.senne.imttech.weapons.EnergyCellItem.withCharge(100),
                com.senne.imttech.weapons.EnergyCellItem.withCharge(45),
                com.senne.imttech.guns.MagazineItem.of(com.senne.imttech.guns.FirearmType.GLOCK_19, 15)));
        com.senne.imttech.guns.Gear.set(player, com.senne.imttech.guns.GearItem.Kind.MAG_POUCH, magazines);
        com.senne.imttech.guns.Gear.setSling(player, 0, slungGun(com.senne.imttech.weapons.GunType.RIFLE, 30));
        com.senne.imttech.guns.Gear.setSling(player, 1, slungGun(com.senne.imttech.weapons.GunType.SNIPER, 100));
    }

    /** Een compleet titanium geweer met een sling eraan en zoveel procent in zijn cel. */
    static ItemStack slungGun(com.senne.imttech.weapons.GunType type, float cell) {
        ItemStack gun = com.senne.imttech.weapons.LaserGunItem.complete(type, com.senne.imttech.guns.GunMaterial.TITANIUM);
        com.senne.imttech.weapons.LaserGunItem.setParts(gun, com.senne.imttech.weapons.LaserGunItem.parts(gun)
                .with(com.senne.imttech.weapons.LaserPart.SLING, com.senne.imttech.weapons.LaserParts.extra()));
        com.senne.imttech.weapons.LaserGunItem.setCell(gun, cell);
        return gun;
    }

    /** In het wiel (als het open is) de optie met deze actie (en dit extra getal, -1 = de eerste) kiezen. */
    static void wheelChooseAction(int action, int arg) {
        com.senne.imttech.client.guns.GearWheelScreen wheel = com.senne.imttech.client.guns.GearWheelScreen.current();
        if (wheel != null) {
            wheel.chooseAction(action, arg);
        }
    }

    /** In de skilltree (als die open is) het tabblad van dit wapen tonen. */
    static void skillTab(String scope) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.guns.SkillTreeScreen tree) {
            tree.showWeapon(scope);
        }
    }

    /** Hoofdstuk 52, stap 2: de holster, de pouches en het wiel, op het wapenveld. */

    /** Een holster met een Glock, een granaatpouch (twee EMP, een flashbang) en een magazijnpouch (twee magazijnen); een steen in je hand. */
    static void giveGear(ServerPlayer player) {
        var inventory = player.getInventory();
        inventory.clearContent();
        inventory.setItem(0, new ItemStack(net.minecraft.world.item.Items.STONE, 5));
        ItemStack holster = new ItemStack(ModItems.HOLSTER.get());
        com.senne.imttech.guns.GearItem.setContents(holster, java.util.List.of(
                com.senne.imttech.guns.FirearmItem.loaded(com.senne.imttech.guns.FirearmType.GLOCK_19, com.senne.imttech.guns.GunMaterial.STEEL)));
        ItemStack grenades = new ItemStack(ModItems.GRENADE_POUCH.get());
        com.senne.imttech.guns.GearItem.setContents(grenades, java.util.List.of(new ItemStack(ModItems.EMP_GRENADE.get()),
                new ItemStack(ModItems.EMP_GRENADE.get()), new ItemStack(ModItems.FLASHBANG.get())));
        ItemStack magazines = new ItemStack(ModItems.MAG_POUCH.get());
        com.senne.imttech.guns.GearItem.setContents(magazines, java.util.List.of(
                com.senne.imttech.guns.MagazineItem.of(com.senne.imttech.guns.FirearmType.GLOCK_19, 15),
                com.senne.imttech.guns.MagazineItem.of(com.senne.imttech.guns.FirearmType.GLOCK_19, 9)));
        com.senne.imttech.guns.Gear.set(player, com.senne.imttech.guns.GearItem.Kind.HOLSTER, holster);
        com.senne.imttech.guns.Gear.set(player, com.senne.imttech.guns.GearItem.Kind.GRENADE_POUCH, grenades);
        com.senne.imttech.guns.Gear.set(player, com.senne.imttech.guns.GearItem.Kind.MAG_POUCH, magazines);
    }

    /** In het wiel (als het open is) deze optie kiezen. */
    static void wheelChoose(int index) {
        com.senne.imttech.client.guns.GearWheelScreen wheel = com.senne.imttech.client.guns.GearWheelScreen.current();
        if (wheel != null) {
            wheel.chooseIndex(index);
        }
    }

    /** In het wiel (als het open is) "Put away" kiezen (sinds hoofdstuk 54 niet meer de laatste optie). */
    static void wheelChoosePutAway() {
        com.senne.imttech.client.guns.GearWheelScreen wheel = com.senne.imttech.client.guns.GearWheelScreen.current();
        if (wheel != null) {
            wheel.chooseAction(com.senne.imttech.guns.Gear.ACTION_HOLSTER);
        }
    }

    /** In het keuzescherm van de magazijnpouch dit vakje kiezen. */
    static void magPick(int index) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.guns.MagPickerScreen picker) {
            picker.pickIndex(index);
        }
    }

    /** De Gunsmith Table (met een half pistool erop) en de Reloading Bench (met ingrediënten) naast {@code stand}. */
    static void buildGunBenches(ServerPlayer player, BlockPos stand) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        BlockPos table = stand.offset(-2, 0, -2);
        BlockPos bench = stand.offset(-2, 0, 2);
        level.setBlockAndUpdate(table, ModBlocks.GUNSMITH_TABLE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.EAST));
        level.setBlockAndUpdate(bench, ModBlocks.RELOADING_BENCH.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, net.minecraft.core.Direction.EAST));
        if (level.getBlockEntity(table) instanceof com.senne.imttech.guns.GunsmithTableBlockEntity gunsmith) {
            com.senne.imttech.guns.GunParts parts = com.senne.imttech.guns.GunParts.EMPTY
                    .with(com.senne.imttech.guns.GunPart.FRAME, com.senne.imttech.guns.PartState.fresh(com.senne.imttech.guns.GunMaterial.POLYMER))
                    .with(com.senne.imttech.guns.GunPart.SLIDE, new com.senne.imttech.guns.PartState(com.senne.imttech.guns.GunMaterial.STEEL, 72))
                    .with(com.senne.imttech.guns.GunPart.TRIGGER, com.senne.imttech.guns.PartState.fresh(com.senne.imttech.guns.GunMaterial.STEEL));
            gunsmith.items().setItem(0, com.senne.imttech.guns.FirearmItem.withParts(com.senne.imttech.guns.FirearmType.GLOCK_19, parts));
        }
        if (level.getBlockEntity(bench) instanceof com.senne.imttech.guns.ReloadingBenchBlockEntity reloading) {
            reloading.items().setItem(0, new ItemStack(ModItems.CASING_9MM.get(), 12));
            reloading.items().setItem(1, new ItemStack(ModItems.BULLET_9MM.get(), 9));
            reloading.items().setItem(2, new ItemStack(ModItems.CRUSHED_GUNPOWDER.get(), 20));
        }
    }

    /**
     * Hoofdstuk 78: materiaal om op de Gunsmith Table onderdelen te maken (het tabblad "Make parts"). Genoeg staal voor
     * de slede, het frame, de loop en de trekker van de Glock (niet voor de veren, de slagpin of een magazijn), te weinig
     * polymeer, en titanium, een chip, kwarts en diamant voor het frame en de receiver van de laser rifle. Zo zie je groene
     * tegels (dat kun je maken) naast donkere (dat niet).
     */
    static void giveGunsmithMaterials(ServerPlayer player) {
        var inventory = player.getInventory();
        inventory.setItem(13, new ItemStack(ModItems.STEEL_INGOT.get(), 5));
        inventory.setItem(14, new ItemStack(ModItems.POLYMER.get(), 2));
        inventory.setItem(15, new ItemStack(ModItems.TITANIUM_INGOT.get(), 9));
        inventory.setItem(16, new ItemStack(ModItems.COMPUTER_CHIP.get(), 1));
        inventory.setItem(17, new ItemStack(net.minecraft.world.item.Items.QUARTZ, 4));
        inventory.setItem(18, new ItemStack(net.minecraft.world.item.Items.DIAMOND, 2));
    }

    /**
     * Hoofdstuk 78: op het open scherm van de Gunsmith Table het tabblad "Make parts" ({@code making}, met dit wapen en
     * de eerste tegel die aan {@code select} voldoet) of "Build". Doet niets als dat scherm niet open staat.
     */
    static void gunsmithTab(boolean making, com.senne.imttech.client.guns.GunsmithMakePanel.Group group,
                            java.util.function.Predicate<ItemStack> select) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.guns.PartsTableScreen<?> screen) {
            screen.showMaking(making);
            if (making && screen.makePanel() != null) {
                screen.makePanel().show(group, select);
            }
        }
    }

    /** Hoofdstuk 78: op Make drukken (zoals de knop): het gekozen recept maken. */
    static void pressMake() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof com.senne.imttech.client.guns.PartsTableScreen<?> screen && screen.makePanel() != null
                && mc.gameMode != null) {
            int recipe = screen.makePanel().selectedIndex();
            if (recipe >= 0) {
                mc.gameMode.handleInventoryButtonClick(screen.getMenu().containerId, com.senne.imttech.guns.GunsmithMenu.MAKE + recipe);
            }
        }
    }

    /** Het scherm van de bank op deze plek openen (de Gunsmith Table of de Reloading Bench). */
    static void openBench(ServerPlayer player, BlockPos pos) {
        if (player.serverLevel().getBlockEntity(pos) instanceof net.minecraft.world.MenuProvider provider) {
            player.openMenu(provider, buf -> buf.writeBlockPos(pos));
        }
    }

    /**
     * Een geladen Glock 19 in je hand, een magazijn met 4 patronen, losse patronen, een leeg magazijn, een pistool zonder
     * magazijn (voor sneak + R), een pistool met een kapotte loop, en losse onderdelen voor de Gunsmith Table.
     */
    static void giveGlock(ServerPlayer player) {
        var inventory = player.getInventory();
        inventory.clearContent();
        inventory.setItem(0, com.senne.imttech.guns.FirearmItem.loaded(com.senne.imttech.guns.FirearmType.GLOCK_19,
                com.senne.imttech.guns.GunMaterial.STEEL));
        inventory.setItem(1, com.senne.imttech.guns.MagazineItem.of(com.senne.imttech.guns.FirearmType.GLOCK_19, 4));
        inventory.setItem(2, new ItemStack(ModItems.ROUND_9MM.get(), 24));
        inventory.setItem(3, com.senne.imttech.guns.MagazineItem.of(com.senne.imttech.guns.FirearmType.GLOCK_19, 0));
        ItemStack bare = com.senne.imttech.guns.FirearmItem.withParts(com.senne.imttech.guns.FirearmType.GLOCK_19,
                com.senne.imttech.guns.GunParts.complete(com.senne.imttech.guns.GunMaterial.TITANIUM));
        inventory.setItem(5, bare);
        ItemStack broken = com.senne.imttech.guns.FirearmItem.loaded(com.senne.imttech.guns.FirearmType.GLOCK_19,
                com.senne.imttech.guns.GunMaterial.POLYMER);
        com.senne.imttech.guns.FirearmItem.setParts(broken, com.senne.imttech.guns.FirearmItem.parts(broken)
                .with(com.senne.imttech.guns.GunPart.BARREL, new com.senne.imttech.guns.PartState(com.senne.imttech.guns.GunMaterial.STEEL, 0)));
        inventory.setItem(6, broken);
        inventory.setItem(9, com.senne.imttech.guns.GunPartItem.of(com.senne.imttech.guns.GunPart.BARREL, com.senne.imttech.guns.GunMaterial.TITANIUM));
        inventory.setItem(10, com.senne.imttech.guns.GunPartItem.of(com.senne.imttech.guns.GunPart.SPRINGS, com.senne.imttech.guns.GunMaterial.STEEL));
        inventory.setItem(11, com.senne.imttech.guns.GunPartItem.of(com.senne.imttech.guns.GunPart.STRIKER, com.senne.imttech.guns.GunMaterial.STEEL, 40));
        inventory.setItem(12, com.senne.imttech.guns.GunPartItem.of(com.senne.imttech.guns.GunPart.SLIDE, com.senne.imttech.guns.GunMaterial.TITANIUM));
    }

    /** Waar het veldje van de autorisatiecodes (hoofdstuk 49) ligt (zie VisualCheck.buildAuthYard). */
    static final BlockPos AUTH_YARD = new BlockPos(-130, -60, 270);

    /** Hoofdstuk 49: de autorisatiecodes en de Auth Console. */

    /** Hoofdstuk 50: de I.M.T Phone, op het veldje van hoofdstuk 49 (dezelfde kern, Bram en de console). */

    /** Waar de torens en de bunker uit kamers staan (hoofdstuk 51, zie VisualCheck.buildRoomsYard): ver weg, ze zijn groot. */
    static final BlockPos ROOMS_YARD = new BlockPos(-700, -60, -700);

    /** Hoofdstuk 51: torens en bunkers uit kamers. */

    /** Waar het veldje van de handboeien (hoofdstuk 48) ligt (zie VisualCheck.buildCuffYard). */
    static final BlockPos CUFF_YARD = new BlockPos(-130, -60, 240);

    /** Kijk (in je spel) naar het dichtstbijzijnde wezen van deze soort, op borsthoogte. */
    private static void lookAtNearest(Class<? extends net.minecraft.world.entity.Entity> kind) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.player.LocalPlayer player = mc.player;
        if (mc.level == null || player == null) {
            return;
        }
        net.minecraft.world.entity.Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (net.minecraft.world.entity.Entity entity : mc.level.entitiesForRendering()) {
            double distance = entity.distanceToSqr(player);
            if (kind.isInstance(entity) && entity != player && distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        if (best != null) {
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES,
                    best.position().add(0, best.getBbHeight() * 0.6, 0));
        }
    }

    /** Hoofdstuk 48: de handboeien. */

    /** Waar de Power Battery van hoofdstuk 47 staat (zie VisualCheck.buildBatteryYard). */
    static final BlockPos BATTERY_YARD = new BlockPos(-130, -60, 200);

    /** Hoofdstuk 47: de Power Battery. Apart, zodat de lijst hierboven leesbaar blijft. */

    /** Waar de kamer met de schermmuur staat (zie VisualCheck.buildCameraWall). */
    static final BlockPos WALL_ROOM = new BlockPos(-130, -60, 160);

    // ---------------------------------------------------------------- De laptop overal, en computers (hoofdstuk 40)

    /** Waar de receptie met de computers staat (zie VisualCheck.buildComputerDesk). */
    static final BlockPos PC_DESK = new BlockPos(-130, -60, 120);

    /** Iets doen met het inlogscherm van de laptop (of het codescherm), als het open staat. */
    private static void login(Consumer<com.senne.imttech.client.os.OsLoginScreen> action) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.OsLoginScreen screen) {
            action.accept(screen);
        }
    }

    // ---------------------------------------------------------------- Het netwerk (hoofdstuk 38)

    /** Waar de desktop van het netwerk staat: ver van de rest, met zijn kabels eromheen. */
    static final BlockPos NET_YARD = new BlockPos(-130, -60, 90);
    /** De Network Switch, de keycardlezer, en de netwerkhendel en -knop van het netwerk. */
    static final BlockPos NET_SWITCH = NET_YARD.east(3);
    static final BlockPos NET_READER = NET_YARD.east(6);
    static final BlockPos NET_LEVER = NET_YARD.north(2).west();
    static final BlockPos NET_BUTTON = NET_YARD.north(2).east();

    /** Het diagram van de hendel en de knop. */
    private static final String CONTROLS_CODE = """
            # No redstone: they reach the server over their network cable
            when lever("Night mode"):
                if lever.on:
                    say("Night mode on: the cameras watch the hall")
                else:
                    say("Night mode off")

            when button("Doorbell"):
                say(player.name + " rang the doorbell")
            """;

    /**
     * Bouwt het netwerk (elke keer opnieuw): een desktop van jou met een generator eronder en een serverrek ertegen, een
     * PoE-kabel naar een Network Switch (gevoed door een grijze stroomkabel van een generator verderop), daarachter een
     * witte netwerkkabel (één stukje onder een blok) naar een keycardlezer, een PoE-kabel van de switch naar een camera, en
     * aan de noordkant een PoE-kabel naar een netwerkhendel en een netwerkknop. En het diagram van die twee.
     */
    static void buildNetworkYard(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        BlockPos y = NET_YARD;
        for (BlockPos pos : BlockPos.betweenClosed(y.offset(-3, 0, -5), y.offset(9, 3, 5))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            for (com.senne.imttech.energy.CableKind kind : com.senne.imttech.energy.CableKind.values()) {
                com.senne.imttech.energy.Cables.remove(kind, level, pos);
            }
        }
        BlockPos rack = y.west();
        BlockPos switchPower = NET_SWITCH.south(3);
        for (BlockPos generator : new BlockPos[] {y.below(), rack.below(), NET_READER.below(), switchPower}) {
            level.setBlockAndUpdate(generator, com.senne.imttech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                charger.fill(300_000);
            }
        }
        level.setBlockAndUpdate(y, com.senne.imttech.registry.ModBlocks.SECURITY_CORE.get().defaultBlockState()
                .setValue(com.senne.imttech.security.SecurityCoreBlock.FACING, net.minecraft.core.Direction.SOUTH));
        claimTowerCore(player, y);
        // Het serverrek tegen de kern (zonder kabel op het netwerk), met onderdelen en een fan.
        level.setBlockAndUpdate(rack, com.senne.imttech.registry.ModBlocks.SERVER_RACK.get().defaultBlockState()
                .setValue(com.senne.imttech.logic.ServerRackBlock.FACING, net.minecraft.core.Direction.SOUTH));
        if (level.getBlockEntity(rack) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
            be.insert(new net.minecraft.world.item.ItemStack(com.senne.imttech.registry.ModItems.SERVER_CPU.get()));
            be.insert(new net.minecraft.world.item.ItemStack(com.senne.imttech.registry.ModItems.SERVER_RAM.get()));
            be.insert(new net.minecraft.world.item.ItemStack(com.senne.imttech.registry.ModItems.SERVER_SSD.get()));
            be.insert(new net.minecraft.world.item.ItemStack(com.senne.imttech.registry.ModItems.SERVER_FAN.get()));
        }
        // PoE van de kern naar de switch, de switch met een grijze stroomkabel.
        for (int dx = 1; dx <= 2; dx++) {
            com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.POE, level, y.east(dx), player.getUUID());
        }
        level.setBlockAndUpdate(NET_SWITCH, com.senne.imttech.registry.ModBlocks.NETWORK_SWITCH.get().defaultBlockState()
                .setValue(com.senne.imttech.lan.NetworkSwitchBlock.FACING, net.minecraft.core.Direction.SOUTH));
        if (level.getBlockEntity(NET_SWITCH) instanceof com.senne.imttech.lan.NetworkSwitchBlockEntity be) {
            be.setOwner(player);
            be.setName("Hall switch");
        }
        for (int dz = 1; dz <= 2; dz++) {
            com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.POWER, level, NET_SWITCH.south(dz), player.getUUID());
        }
        // De netwerkkabel naar de lezer; het middelste stukje onder een blok gepolijst andesiet.
        for (int dx = 1; dx <= 2; dx++) {
            com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.NETWORK, level, NET_SWITCH.east(dx), player.getUUID());
        }
        level.setBlockAndUpdate(NET_SWITCH.east(2), Blocks.POLISHED_ANDESITE.defaultBlockState());
        placeInput(level, NET_READER, com.senne.imttech.registry.ModBlocks.KEYCARD_READER.get().defaultBlockState(), "Hall door");
        // PoE van de switch naar een camera (noord).
        for (int dz = 1; dz <= 2; dz++) {
            com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.POE, level, NET_SWITCH.north(dz), player.getUUID());
        }
        level.setBlockAndUpdate(NET_SWITCH.north(3), com.senne.imttech.registry.ModBlocks.SECURITY_CAMERA.get().defaultBlockState()
                .setValue(com.senne.imttech.security.CameraBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(com.senne.imttech.security.CameraBlock.FACING, net.minecraft.core.Direction.SOUTH));
        // PoE naar het noorden, met de hendel en de knop op de vloer ernaast.
        for (int dz = 1; dz <= 2; dz++) {
            com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.POE, level, y.north(dz), player.getUUID());
        }
        placeControl(level, player, NET_LEVER, com.senne.imttech.registry.ModBlocks.NETWORK_LEVER.get().defaultBlockState(), "Night mode");
        placeControl(level, player, NET_BUTTON, com.senne.imttech.registry.ModBlocks.NETWORK_BUTTON.get().defaultBlockState(), "Doorbell");
        if (level.getBlockEntity(y) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
            core.logic().remove("Controls");
            var graph = com.senne.imttech.logic.program.GraphConverter.toGraph(
                    com.senne.imttech.logic.program.ScriptParser.parse(CONTROLS_CODE), null);
            core.logic().put(null, new com.senne.imttech.logic.program.LogicProgram("Controls", true, graph, CONTROLS_CODE));
            core.setChanged();
            com.senne.imttech.security.SecurityDevices.forget(level, core);
            com.senne.imttech.logic.LogicServer.forgetAll();
        }
    }

    /** Een netwerkhendel of -knop van jou op de vloer, met deze naam. */
    private static void placeControl(net.minecraft.server.level.ServerLevel level, net.minecraft.server.level.ServerPlayer player,
                                     BlockPos pos, net.minecraft.world.level.block.state.BlockState state, String name) {
        level.setBlockAndUpdate(pos, state
                .setValue(com.senne.imttech.logic.NetworkControlBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(com.senne.imttech.logic.InputDeviceBlock.FACING, net.minecraft.core.Direction.SOUTH));
        if (level.getBlockEntity(pos) instanceof com.senne.imttech.logic.InputDeviceBlockEntity device) {
            device.setOwner(player);
            device.setName(name);
        }
    }

    /** Het rek van het netwerk meteen laten kijken of het meedraait (anders duurt dat tot twee seconden). */
    static void wakeNetworkServer(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        BlockPos rack = NET_YARD.west();
        if (level.getBlockEntity(rack) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
            be.update(level, rack, level.getBlockState(rack));
        }
        com.senne.imttech.logic.LogicServer.forgetAll();
    }

    /** Rechtsklikken op een netwerkhendel of -knop, zoals jij dat doet (met een lege hand, zonder te sluipen). */
    private static void useControl(net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        level.getBlockState(pos).useWithoutItem(level, player,
                new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(pos), net.minecraft.core.Direction.UP, pos, false));
    }

    /** Zet dit item (granaat) in je hand en gooi het (met {@code toss}: onderhands, zoals met sneak). */
    private static void holdAndThrow(ServerPlayer player, net.minecraft.world.item.Item item, boolean toss) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(item, 4));
        player.setShiftKeyDown(toss);
        item.use(player.serverLevel(), player, InteractionHand.MAIN_HAND);
        player.setShiftKeyDown(false);
    }

    /** De vier lasergeweren met een volle cel in je hotbar, en twee reservecellen. */
    private static void giveGuns(ServerPlayer player) {
        var inventory = player.getInventory();
        inventory.clearContent();
        com.senne.imttech.weapons.GunType[] types = com.senne.imttech.weapons.GunType.values();
        for (int i = 0; i < types.length; i++) {
            ItemStack gun = com.senne.imttech.weapons.LaserGunItem.complete(types[i], com.senne.imttech.guns.GunMaterial.TITANIUM);
            com.senne.imttech.weapons.LaserGunItem.setCell(gun, i == 1 ? 30 : 100);
            inventory.setItem(i, gun);
        }
        inventory.setItem(4, com.senne.imttech.weapons.EnergyCellItem.withCharge(100));
        inventory.setItem(5, com.senne.imttech.weapons.EnergyCellItem.withCharge(100));
        // Hoofdstuk 57: R wisselt alleen nog met een cel uit je magazijnpouch (zonder pouch haalt R de cel eruit).
        ItemStack pouch = new ItemStack(ModItems.MAG_POUCH.get());
        com.senne.imttech.guns.GearItem.setContents(pouch, java.util.List.of(com.senne.imttech.weapons.EnergyCellItem.withCharge(100)));
        com.senne.imttech.guns.Gear.set(player, com.senne.imttech.guns.GearItem.Kind.MAG_POUCH, pouch);
    }

    /** Kies een vakje in je hotbar (in je eigen spel). */
    private static void hotbar(int slot) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.getInventory().selected = slot;
        }
    }

    // ================================================================ Hulpjes

    /** Waar de relais van 13 oktober staan (tags, invert en de emitter): een eigen plekje. */
    static final BlockPos RELAY_YARD = new BlockPos(130, -60, 140);

    /**
     * Drie relais met een redstonelamp erachter ("Door 1" en "Door 2" in de groep "security room", die in
     * "restricted" in "base" zit, en "Garden light" in "garden", omgekeerd), een relay emitter op "base" met een
     * hendel (uit), en een beveiligingskern voor de desktop-app Groups. De relais komen erbij in je lijst, niet
     * in plaats van: de andere relais-demo's gebruiken het eerste relais.
     */
    static void buildRelayYard(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos y = RELAY_YARD;
        var data = player.getData(ModAttachments.RELAYS);
        String[][] relays = {{"Door 1", "security room"}, {"Door 2", "security room"}, {"Garden light", "garden"}};
        for (int i = 0; i < relays.length; i++) {
            BlockPos relay = y.offset(1 + i * 2, 0, 0);
            // Eerst de lamp: een redstonelamp kijkt pas of hij stroom krijgt als er naast hem iets verandert.
            level.setBlockAndUpdate(relay.north(), Blocks.REDSTONE_LAMP.defaultBlockState());
            level.setBlockAndUpdate(relay, com.senne.imttech.registry.ModBlocks.RELAY.get().defaultBlockState()
                    .setValue(com.senne.imttech.redstone.RelayBlock.INVERTED, i == 2));
            int index = data.indexAt(level.dimension(), relay);
            com.senne.imttech.redstone.Relay entry = new com.senne.imttech.redstone.Relay(relays[i][0], relays[i][1], level.dimension(), relay);
            data = index < 0 ? data.with(entry) : data.replace(index, entry);
        }
        data = exampleGroups(data);
        player.setData(ModAttachments.RELAYS, data);
        // Een eigen beveiligingskern (met wachtwoord, anders opent de desktop eerst Settings).
        BlockPos core = y.offset(0, 0, 3);
        level.setBlockAndUpdate(core, com.senne.imttech.registry.ModBlocks.SECURITY_CORE.get().defaultBlockState());
        claimTowerCore(player, core);
        BlockPos emitter = y.offset(6, 0, 2);
        level.setBlockAndUpdate(emitter, com.senne.imttech.registry.ModBlocks.RELAY_EMITTER.get().defaultBlockState());
        if (level.getBlockEntity(emitter) instanceof com.senne.imttech.redstone.RelayEmitterBlockEntity entity) {
            entity.setOwner(player);
            entity.configure(player, "base", com.senne.imttech.redstone.RelayEmitterBlockEntity.Mode.FOLLOW);
        }
        level.setBlockAndUpdate(emitter.east(), Blocks.LEVER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(net.minecraft.world.level.block.LeverBlock.POWERED, false));
    }

    /**
     * Jouw voorbeeld van 14 oktober als groepen: base > visitors > cafe en storage, base > restricted > security
     * room en power room. Ook gebruikt door de screenshots (VisualCheck).
     */
    static com.senne.imttech.redstone.ImtRelays exampleGroups(com.senne.imttech.redstone.ImtRelays data) {
        String[][] tree = {{"base", ""}, {"visitors", "base"}, {"cafe", "visitors"}, {"storage", "visitors"},
                {"restricted", "base"}, {"security room", "restricted"}, {"power room", "restricted"}};
        for (String[] group : tree) {
            data = data.withGroup(group[0], group[1]).withParent(group[0], group[1]);
        }
        return data;
    }

    /** De hendel naast de emitter om. */
    private static void relayLever(ServerPlayer player, boolean on) {
        BlockPos lever = RELAY_YARD.offset(7, 0, 2);
        player.serverLevel().setBlockAndUpdate(lever, player.serverLevel().getBlockState(lever)
                .setValue(net.minecraft.world.level.block.LeverBlock.POWERED, on));
        player.serverLevel().playSound(null, lever, net.minecraft.sounds.SoundEvents.LEVER_CLICK,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.3f, on ? 0.6f : 0.5f);
    }

    /** Waar de generator voor de Arc Lamp staat: ver van de rest, want zijn licht reikt 100 blokken. */
    static final BlockPos LAMP_YARD = new BlockPos(220, -60, -220);

    /**
     * Een volle generator, een kabel over het gras met halverwege een lichtschakelaar (uit), en aan het eind
     * een paal van vier blokken (de kabel loopt erin omhoog) met de Arc Lamp erop.
     */
    static void buildLampYard(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos y = LAMP_YARD;
        level.setBlockAndUpdate(y, com.senne.imttech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(y) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
            generator.fill(200_000);
        }
        for (int x = 1; x <= 7; x++) {
            com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.POWER, level, y.east(x), player.getUUID());
        }
        for (int up = 0; up < 4; up++) {
            level.setBlockAndUpdate(y.east(7).above(up), Blocks.POLISHED_ANDESITE.defaultBlockState());
            com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.POWER, level, y.east(7).above(up), player.getUUID());
        }
        level.setBlockAndUpdate(y.east(7).above(4), com.senne.imttech.registry.ModBlocks.ARC_LAMP.get().defaultBlockState());
        level.setBlockAndUpdate(y.east(3), com.senne.imttech.registry.ModBlocks.LIGHT_SWITCH.get().defaultBlockState()
                .setValue(com.senne.imttech.light.LightSwitchBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(com.senne.imttech.light.LightSwitchBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(com.senne.imttech.light.LightSwitchBlock.ON, false));
    }

    /** Het schuifje "Brightness" in het open instelscherm van de lamp naar dit lichtniveau. */
    private static void lampScreen(int lightLevel) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.light.ArcLampScreen screen) {
            screen.tourBrightness(lightLevel);
        }
    }

    private static Vec3 at(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    /** Waar je naar kijkt om een wezen te raken: ongeveer zijn borst. */
    private static Vec3 mob(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5);
    }

    private static Vec3 eyes(BlockPos pos) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + 1.5, pos.getZ() + 0.5);
    }

    /** Een punt in de Biebia's Industries Tower, gemeten vanaf de hoek van het gebouw (zoals in generate_structure.py). */
    private static Vec3 towerPoint(double x, double y, double z) {
        return new Vec3(BIEBIA_TOWER.getX() + x, BIEBIA_TOWER.getY() + y, BIEBIA_TOWER.getZ() + z);
    }

    /**
     * De kern van de toren is (even) van jou, met een wachtwoord, zodat de desktop meteen opengaat
     * zonder eerst om een wachtwoord te vragen. Alleen voor de showcase.
     */
    private static void claimTowerCore(net.minecraft.server.level.ServerPlayer player, BlockPos core) {
        if (player.serverLevel().getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
            be.setOwner(player);
            be.ensureSystem(player.serverLevel());
            be.setPassword("bitech1", player.serverLevel().random);
        }
    }

    // ---------------------------------------------------------------- Het lab van hoofdstuk 8

    /** Waar het lab staat: zijn beveiligingskern (ver genoeg van de toren, die zijn eigen kern heeft). */
    static final BlockPos LOGIC_LAB = new BlockPos(-50, -60, -50);

    /** Het diagram van het lab, zoals je het in de app Logic ziet. */
    private static final String LAB_CODE = """
            # The lab door: a card of the group lab (or the code 7777) opens it for 3 seconds
            def open_door():
                accept()
                pulse("Lab door lock", 3)

            when keycard("Lab door"):
                if card.in_group("lab"):
                    open_door()
                else:
                    deny()
                    say("Someone tried the lab door")

            when codepanel("Lights panel"):
                if code == "4321":
                    accept()
                    toggle("Lab lights")
                elif code == "7777":
                    open_door()
                else:
                    deny()
            """;

    /**
     * Hoofdstuk 20: een tweede diagram in het lab, met een naam voor het hele programma, een berekend
     * bericht en een lus. Gaat de lamp aan (code 4321 op het paneel), dan telt het, zegt I.M.T hoe
     * vaak, en gaat de deur drie keer even open.
     */
    private static final String BLINK_CODE = """
            count = 0

            when turned_on("Lab lights"):
                count = count + 1
                say("The lights went on " + count + " times")
                for i in range(1, 4):
                    log($"Blink {i} of 3")
                    pulse("Lab door lock", 0.5)
                    wait(1)
            """;

    /** Zet het diagram "Blink" (hierboven) naast "Lab" op de kern van het lab. */
    private static void addBlinkProgram(net.minecraft.server.level.ServerPlayer player) {
        if (player.serverLevel().getBlockEntity(LOGIC_LAB) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
            core.logic().remove("Blink");
            var graph = com.senne.imttech.logic.program.GraphConverter.toGraph(
                    com.senne.imttech.logic.program.ScriptParser.parse(BLINK_CODE), null);
            core.logic().put(null, new com.senne.imttech.logic.program.LogicProgram("Blink", true, graph, BLINK_CODE));
            core.setChanged();
        }
    }

    /**
     * Bouwt het lab (elke keer opnieuw, zodat de showcase altijd hetzelfde laat zien): een kern met
     * stroom, drie racks met onderdelen, een muur met een ijzeren deur, een lezer, een paneel, een
     * lamp, twee relais met een naam (zoals je ze met je bril noemt), en het diagram.
     */
    static void buildLogicLab(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        BlockPos lab = LOGIC_LAB;
        // Opruimen: een lege plek van 12 x 4 x 14.
        for (BlockPos pos : BlockPos.betweenClosed(lab.offset(-3, 0, -2), lab.offset(8, 4, 11))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(lab, com.senne.imttech.registry.ModBlocks.SECURITY_CORE.get().defaultBlockState());
        claimTowerCore(player, lab);
        BlockPos[] racks = {lab.offset(3, 0, 0), lab.offset(3, 1, 0), lab.offset(4, 0, 0)};
        for (BlockPos generator : new BlockPos[] {lab.below(), racks[0].below()}) {
            level.setBlockAndUpdate(generator, com.senne.imttech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                charger.fill(300_000);
            }
        }
        net.minecraft.world.level.block.state.BlockState rack = com.senne.imttech.registry.ModBlocks.SERVER_RACK.get()
                .defaultBlockState().setValue(com.senne.imttech.logic.ServerRackBlock.FACING, net.minecraft.core.Direction.SOUTH);
        net.minecraft.world.item.Item[][] parts = {
                {com.senne.imttech.registry.ModItems.SERVER_CPU.get(), com.senne.imttech.registry.ModItems.SERVER_RAM.get()},
                {com.senne.imttech.registry.ModItems.SERVER_CPU.get(), com.senne.imttech.registry.ModItems.SERVER_SSD.get()},
                {com.senne.imttech.registry.ModItems.SERVER_RAM.get(), com.senne.imttech.registry.ModItems.SERVER_SSD.get()}};
        for (int i = 0; i < racks.length; i++) {
            level.setBlockAndUpdate(racks[i], rack);
            if (level.getBlockEntity(racks[i]) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
                for (int slot = 0; slot < parts[i].length; slot++) {
                    be.insert(new net.minecraft.world.item.ItemStack(parts[i][slot]));
                }
                // Elk rack heeft zijn eigen fan nodig (8 oktober), anders wordt het steeds warmer.
                be.insert(new net.minecraft.world.item.ItemStack(com.senne.imttech.registry.ModItems.SERVER_FAN.get()));
            }
        }
        // De muur (z + 6), met een ijzeren deur, een lamp erin en de lezer en het paneel ervoor.
        for (int dx = -2; dx <= 7; dx++) {
            for (int dy = 0; dy < 3; dy++) {
                level.setBlockAndUpdate(lab.offset(dx, dy, 6), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        BlockPos door = lab.offset(2, 0, 6);
        net.minecraft.world.level.block.state.BlockState doorState = Blocks.IRON_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, net.minecraft.core.Direction.SOUTH);
        level.setBlock(door, doorState.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.LOWER), 3);
        level.setBlock(door.above(), doorState.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 3);
        BlockPos lamp = lab.offset(5, 2, 6);
        level.setBlockAndUpdate(lamp, Blocks.REDSTONE_LAMP.defaultBlockState());
        BlockPos doorRelay = lab.offset(2, 0, 5);
        BlockPos lightRelay = lab.offset(5, 2, 5);
        level.setBlockAndUpdate(doorRelay, com.senne.imttech.registry.ModBlocks.RELAY.get().defaultBlockState());
        level.setBlockAndUpdate(lightRelay, com.senne.imttech.registry.ModBlocks.RELAY.get().defaultBlockState());
        // De twee relais in je redstone-lijst (erbij, niet in plaats van: de relais-demo heeft zijn lamp nodig).
        var relays = player.getData(com.senne.imttech.registry.ModAttachments.RELAYS);
        if (relays.indexAt(level.dimension(), doorRelay) < 0) {
            relays = relays.with(new com.senne.imttech.redstone.Relay("Lab door lock", "lab", level.dimension(), doorRelay));
        }
        if (relays.indexAt(level.dimension(), lightRelay) < 0) {
            relays = relays.with(new com.senne.imttech.redstone.Relay("Lab lights", "lab", level.dimension(), lightRelay));
        }
        player.setData(com.senne.imttech.registry.ModAttachments.RELAYS, relays);
        placeInput(level, lab.offset(1, 1, 7), com.senne.imttech.registry.ModBlocks.KEYCARD_READER.get().defaultBlockState(), "Lab door");
        placeInput(level, lab.offset(5, 1, 7), com.senne.imttech.registry.ModBlocks.CODE_PANEL.get().defaultBlockState(), "Lights panel");
        // Het diagram, en de keycard-groepen van het lab (25 oktober): "lab" en "guest" zitten in "staff".
        if (level.getBlockEntity(lab) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
            com.senne.imttech.logic.LogicDevices.refresh(level, core, player);
            core.logic().addGroup("staff", "");
            core.logic().addGroup("lab", "staff");
            core.logic().addGroup("guest", "staff");
            core.logic().remove("Lab");
            var graph = com.senne.imttech.logic.program.GraphConverter.toGraph(
                    com.senne.imttech.logic.program.ScriptParser.parse(LAB_CODE), null);
            core.logic().put(null, new com.senne.imttech.logic.program.LogicProgram("Lab", true, graph, LAB_CODE));
            core.setChanged();
            com.senne.imttech.security.SecurityDevices.forget(level, core);
            com.senne.imttech.logic.LogicServer.forgetAll();
        }
    }

    // ---------------------------------------------------------------- Inbreken (hoofdstuk 12)

    /** Laat de racks van het lab meteen kijken of ze meedraaien (anders duurt dat tot twee seconden). */
    static void wakeLabServer(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        for (BlockPos rackPos : new BlockPos[] {LOGIC_LAB.offset(3, 0, 0), LOGIC_LAB.offset(3, 1, 0), LOGIC_LAB.offset(4, 0, 0)}) {
            if (level.getBlockEntity(rackPos) instanceof com.senne.imttech.logic.ServerRackBlockEntity rack) {
                rack.update(level, rackPos, level.getBlockState(rackPos));
            }
        }
        com.senne.imttech.bypass.Bypass.clear();
    }

    /** De onderste zender van de laserpoort in het lab (hoofdstuk 33): voor de muur, tussen de lezer en de rand. */
    static final BlockPos LAB_LASER = LOGIC_LAB.offset(-1, 0, 9);

    /**
     * Hoofdstuk 33: een laserpoort van twee zenders op elkaar in het lab (oostwaarts, op een volle generator, met een
     * redstoneblok ernaast), elke keer nieuw, dus met hele draden. De wand loopt tot een pilaartje, zes blokken ver. Jij
     * zette hem neer (dus jij mag hem herstellen), met de Bypass Kit in je hand en één Computer Chip op zak.
     */
    static void buildLabLaser(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos bottom = LAB_LASER;
        for (BlockPos pos : BlockPos.betweenClosed(bottom.offset(-1, 0, 0), bottom.offset(7, 1, 0))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(bottom.below(), com.senne.imttech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(bottom.below()) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
            charger.fill(300_000);
        }
        net.minecraft.world.level.block.state.BlockState emitter = com.senne.imttech.registry.ModBlocks.LASER_EMITTER.get()
                .defaultBlockState().setValue(com.senne.imttech.shield.LaserEmitterBlock.FACING, net.minecraft.core.Direction.EAST);
        for (BlockPos pos : new BlockPos[] {bottom, bottom.above()}) {
            level.setBlockAndUpdate(pos, emitter);
            com.senne.imttech.registry.ModBlocks.LASER_EMITTER.get().setPlacedBy(level, pos, level.getBlockState(pos), player,
                    ItemStack.EMPTY);
        }
        for (int dy = 0; dy < 2; dy++) {
            level.setBlockAndUpdate(bottom.offset(7, dy, 0), Blocks.STONE_BRICKS.defaultBlockState());
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BYPASS_KIT.get()));
        if (player.getInventory().countItem(ModItems.COMPUTER_CHIP.get()) == 0) {
            player.getInventory().add(new ItemStack(ModItems.COMPUTER_CHIP.get()));
        }
        com.senne.imttech.bypass.Bypass.clear();
    }

    /**
     * Seppe kijkt naar het noorden en heeft keycards in zijn zakken: twee beschreven kaarten en twee lege, en
     * in zijn hand een koekje (dat zit niet in een zak). Jij hebt een lege hand.
     */
    static void fillHappysPockets(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.level.ServerPlayer seppe = ShowcaseScene.friend(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (seppe == null) {
            return;
        }
        var inventory = seppe.getInventory();
        inventory.selected = 0;
        inventory.setItem(0, new ItemStack(Items.COOKIE));
        String[][] cards = {{"9", "Biebia's Industries Tower", "5"}, {"10", "Garage", "2"}};
        for (String[] card : cards) {
            ItemStack stack = new ItemStack(ModItems.KEYCARD.get());
            com.senne.imttech.logic.KeycardItem.write(stack, new com.senne.imttech.logic.KeycardData(
                    "10.0.0.7", "seppe-net", 1000 + Integer.parseInt(card[0]), Integer.parseInt(card[2]), card[1]));
            inventory.setItem(Integer.parseInt(card[0]), stack);
        }
        inventory.setItem(11, new ItemStack(ModItems.KEYCARD.get(), 2));
        turnHappy(player, 180);
    }

    /** Opent de zak van Seppe (jij staat achter hem). */
    static void pickHappysPocket(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.level.ServerPlayer seppe = ShowcaseScene.friend(player);
        if (seppe != null) {
            com.senne.imttech.pickpocket.Pickpocket.tryStart(player, seppe);
        }
    }

    /** Laat Seppe deze kant op kijken (0 = zuid, naar jou toe; 180 = noord), ook met zijn hoofd. */
    static void turnHappy(net.minecraft.server.level.ServerPlayer player, float yaw) {
        net.minecraft.server.level.ServerPlayer seppe = ShowcaseScene.friend(player);
        if (seppe != null) {
            seppe.setYRot(yaw);
            seppe.setYHeadRot(yaw);
            seppe.yHeadRotO = yaw;
            seppe.yRotO = yaw;
            seppe.setXRot(0);
        }
    }

    // ---------------------------------------------------------------- De basis met een zone van hoofdstuk 10

    /** Waar de basis met een zone staat (ver van de rest: een kern beschermt 30 blokken om zich heen). */
    static final BlockPos ZONE_BASE = new BlockPos(-130, -60, 60);

    /**
     * Een L-vormige basis van stenen bakstenen (10 x 8, vier hoog, zonder dak, met een deuropening aan
     * de noordkant) met een torentje van twaalf hoog in de zuidoosthoek, en in de westmuur een stuk
     * obsidiaan (voor de Remote Frags). Binnen de kern van jou, op een volle generator, met de zone: twee kubussen.
     * Wat er van een vorige keer stond (gaten, een derde kubus), wordt eerst weggehaald.
     */
    static void buildZoneBase(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos b = ZONE_BASE;
        net.minecraft.world.level.block.state.BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(b.offset(-12, 0, -8), b.offset(20, 13, 24))) {
            level.setBlock(pos, air, 2);
        }
        for (BlockPos pos : BlockPos.betweenClosed(b.offset(-12, -1, -8), b.offset(20, -1, 24))) {
            level.setBlock(pos, Blocks.GRASS_BLOCK.defaultBlockState(), 2);
        }
        net.minecraft.world.level.block.state.BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        for (int x = 2; x <= 11; x++) {
            for (int z = 2; z <= 9; z++) {
                if (x != 2 && x != 11 && z != 2 && z != 9) {
                    continue;
                }
                for (int y = 0; y <= 3; y++) {
                    boolean door = z == 2 && (x == 5 || x == 6) && y <= 1;
                    boolean vault = x == 2 && z >= 4 && z <= 7;
                    level.setBlockAndUpdate(b.offset(x, y, z), door ? air : vault ? Blocks.OBSIDIAN.defaultBlockState() : bricks);
                }
            }
        }
        for (int x = 8; x <= 11; x++) {
            for (int z = 10; z <= 13; z++) {
                if (x == 8 || x == 11 || z == 10 || z == 13) {
                    for (int y = 0; y <= 11; y++) {
                        level.setBlockAndUpdate(b.offset(x, y, z), bricks);
                    }
                }
            }
        }
        BlockPos core = b.offset(4, 0, 5);
        powerZoneBase(player);
        level.setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState());
        if (level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
            be.setOwner(player);
            be.resetAccessForNewOwner();
            be.ensureSystem(level);
            // Eén blok ruimer dan de muren: zo zet ook niemand iets tegen de buitenkant van je muur.
            be.setZoneBoxes(java.util.List.of(
                    com.senne.imttech.security.zone.ZoneBox.of(b.offset(1, -1, 1), b.offset(12, 6, 10)),
                    com.senne.imttech.security.zone.ZoneBox.of(b.offset(7, -1, 11), b.offset(12, 14, 14))));
        }
    }

    /** Een volle generator onder de kern van de basis (de zone heeft stroom nodig). */
    static void powerZoneBase(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos generator = ZONE_BASE.offset(4, -1, 5);
        level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
            charger.fill(200_000);
        }
    }

    /** De kern van de basis met een zone (of null als hij er niet staat). */
    static com.senne.imttech.security.SecurityCoreBlockEntity zoneCore(ServerPlayer player) {
        return player.serverLevel().getBlockEntity(ZONE_BASE.offset(4, 0, 5))
                instanceof com.senne.imttech.security.SecurityCoreBlockEntity core ? core : null;
    }

    /**
     * De basis is nu van Lotte (een speler die niet online is): jij bent er een vreemde. De zone blijft
     * werken, ook als de eigenaar offline is. (Geen nep-speler die inlogt: een tweede keer inloggen met
     * dezelfde naam laat NeoForge struikelen.)
     */
    static void giveZoneBaseToLotte(ServerPlayer player) {
        com.senne.imttech.security.SecurityCoreBlockEntity core = zoneCore(player);
        if (core != null) {
            core.setOwner(java.util.UUID.nameUUIDFromBytes("dev-Lotte".getBytes()), "Lotte");
            core.resetAccessForNewOwner();
        }
    }

    /** Een Remote Frag tegen de westkant van het obsidiaan, gekoppeld aan de Frag Trigger die je bij je hebt. */
    static void stickRemote(ServerPlayer player, BlockPos support) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = support.west();
        level.setBlockAndUpdate(pos, ModBlocks.REMOTE_FRAG.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACING, Direction.WEST));
        ItemStack trigger = com.senne.imttech.weapons.TriggerItem.find(player, com.senne.imttech.weapons.RemoteChargeBlock.Kind.FRAG);
        if (level.getBlockEntity(pos) instanceof com.senne.imttech.weapons.RemoteChargeBlockEntity frag && trigger != null) {
            frag.placedBy(player);
            com.senne.imttech.weapons.TriggerItem.link(level, pos, frag, trigger);
        }
    }

    // ---------------------------------------------------------------- Barsten herstellen (hoofdstuk 11)

    /** Een klapje van 4,5 breekpunten op alleen dit blok (van de zuidkant): barsten, maar het breekt niet. */
    static void dent(ServerLevel level, BlockPos pos) {
        com.senne.imttech.breach.Blast.breach(level, Vec3.atCenterOf(pos).add(0, 0, 0.95), 4.5f, 3.0f, here -> here.equals(pos), null);
    }

    /** De Block Reinforcer (hoofdstuk 44) op de zuidkant van {@code pos} gebruiken, al dan niet sluipend (de hele muur). */
    static void useReinforcer(ServerPlayer player, BlockPos pos, boolean sneak) {
        if (!player.getMainHandItem().is(ModItems.BLOCK_REINFORCER.get())) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BLOCK_REINFORCER.get()));
        }
        player.setShiftKeyDown(sneak);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                Vec3.atCenterOf(pos).relative(Direction.SOUTH, 0.5), Direction.SOUTH, pos, false);
        player.getMainHandItem().useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        player.setShiftKeyDown(false);
    }

    /** Een Crusher en een Compactor met hun voorkant naar het noorden, elk tegen een volle generator. */
    static void buildRepairMachines(ServerPlayer player, BlockPos crusher, BlockPos compactor) {
        ServerLevel level = player.serverLevel();
        level.setBlockAndUpdate(crusher, ModBlocks.CRUSHER.get().defaultBlockState()
                .setValue(com.senne.imttech.repair.CrackMachineBlock.FACING, Direction.NORTH));
        level.setBlockAndUpdate(compactor, ModBlocks.COMPACTOR.get().defaultBlockState()
                .setValue(com.senne.imttech.repair.CrackMachineBlock.FACING, Direction.NORTH));
        for (BlockPos generator : new BlockPos[] {crusher.west(), compactor.east()}) {
            level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                charger.fill(200_000);
            }
        }
    }

    /** Het gebarsten blok uit je inventaris (of nog op de grond naast je) gaat de machine in. */
    static void putInMachine(ServerPlayer player, BlockPos pos) {
        if (!(player.serverLevel().getBlockEntity(pos) instanceof com.senne.imttech.repair.CrackMachineBlockEntity machine)) {
            return;
        }
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(ModItems.CRACKED_BLOCK.get())) {
                machine.setItem(com.senne.imttech.repair.CrackMachineBlockEntity.SLOT_IN, inventory.removeItemNoUpdate(slot));
                return;
            }
        }
        for (net.minecraft.world.entity.item.ItemEntity item : player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(4))) {
            if (item.getItem().is(ModItems.CRACKED_BLOCK.get())) {
                machine.setItem(com.senne.imttech.repair.CrackMachineBlockEntity.SLOT_IN, item.getItem().copy());
                item.discard();
                return;
            }
        }
    }

    /** Opent het scherm van de machine op deze plek. */
    static void openMachine(ServerPlayer player, BlockPos pos) {
        if (player.serverLevel().getBlockEntity(pos) instanceof com.senne.imttech.repair.CrackMachineBlockEntity machine) {
            player.openMenu(machine, buf -> buf.writeVarInt(machine.kind().ordinal()));
        }
    }

    /** Wat uit de Crusher kwam, gaat de Compactor in. */
    static void moveBetweenMachines(ServerPlayer player, BlockPos from, BlockPos to) {
        if (player.serverLevel().getBlockEntity(from) instanceof com.senne.imttech.repair.CrackMachineBlockEntity first
                && player.serverLevel().getBlockEntity(to) instanceof com.senne.imttech.repair.CrackMachineBlockEntity second) {
            second.setItem(com.senne.imttech.repair.CrackMachineBlockEntity.SLOT_IN,
                    first.removeItem(com.senne.imttech.repair.CrackMachineBlockEntity.SLOT_OUT, 64));
        }
    }

    /** Wat uit de machine kwam, neem je in je hand. */
    static void takeFromMachine(ServerPlayer player, BlockPos pos) {
        if (player.serverLevel().getBlockEntity(pos) instanceof com.senne.imttech.repair.CrackMachineBlockEntity machine) {
            player.setItemInHand(InteractionHand.MAIN_HAND,
                    machine.removeItem(com.senne.imttech.repair.CrackMachineBlockEntity.SLOT_OUT, 64));
        }
    }

    // ---------------------------------------------------------------- Het datacenter van hoofdstuk 9

    /** Het tweede datacenter, ver weg (zodat zijn gebied niet over het lab ligt): hier gaan de backups heen. */
    static final BlockPos DATACENTER_VAULT = new BlockPos(-50, -60, -130);

    /**
     * Het lab van hoofdstuk 8, met in het rack rechts twee GPU's erbij en zijn fan eruit (dus geen fan meer: dat rack wordt
     * steeds warmer; de andere twee racks hebben hun fan en blijven koel), en een tweede datacenter "dev-vault" ver weg met
     * daarop al een backup van het lab. De racks heten lab-1, lab-2 en lab-3 (hoofdstuk 64: zo staan ze in de app Monitor).
     */
    static void buildDatacenter(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        buildLogicLab(player);
        BlockPos[] labRacks = {LOGIC_LAB.offset(3, 0, 0), LOGIC_LAB.offset(3, 1, 0), LOGIC_LAB.offset(4, 0, 0)};
        for (int i = 0; i < labRacks.length; i++) {
            if (level.getBlockEntity(labRacks[i]) instanceof com.senne.imttech.logic.ServerRackBlockEntity rack) {
                rack.setName("lab-" + (i + 1));
            }
        }
        if (level.getBlockEntity(LOGIC_LAB.offset(4, 0, 0)) instanceof com.senne.imttech.logic.ServerRackBlockEntity rack) {
            for (int slot = 4; slot < rack.parts().getContainerSize(); slot++) {
                rack.parts().setItem(slot, net.minecraft.world.item.ItemStack.EMPTY); // de fans eruit
            }
            for (int i = 0; i < 2; i++) {
                rack.insert(new net.minecraft.world.item.ItemStack(com.senne.imttech.registry.ModItems.SERVER_GPU.get()));
            }
            rack.refresh();
        }
        // Het tweede datacenter: een kern, een generator en een rack met CPU, RAM en twee SSD's.
        BlockPos vault = DATACENTER_VAULT;
        BlockPos vaultRack = vault.offset(3, 0, 0);
        for (BlockPos pos : BlockPos.betweenClosed(vault.offset(-2, 0, -2), vault.offset(5, 3, 3))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(vault, com.senne.imttech.registry.ModBlocks.SECURITY_CORE.get().defaultBlockState());
        claimTowerCore(player, vault);
        for (BlockPos generator : new BlockPos[] {vault.below(), vaultRack.below()}) {
            level.setBlockAndUpdate(generator, com.senne.imttech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                charger.fill(300_000);
            }
        }
        level.setBlockAndUpdate(vaultRack, com.senne.imttech.registry.ModBlocks.SERVER_RACK.get().defaultBlockState());
        if (level.getBlockEntity(vaultRack) instanceof com.senne.imttech.logic.ServerRackBlockEntity rack) {
            rack.insert(new net.minecraft.world.item.ItemStack(com.senne.imttech.registry.ModItems.SERVER_CPU.get()));
            rack.insert(new net.minecraft.world.item.ItemStack(com.senne.imttech.registry.ModItems.SERVER_RAM.get()));
            rack.insert(new net.minecraft.world.item.ItemStack(com.senne.imttech.registry.ModItems.SERVER_SSD.get()));
            rack.insert(new net.minecraft.world.item.ItemStack(com.senne.imttech.registry.ModItems.SERVER_SSD.get()));
            rack.insert(new net.minecraft.world.item.ItemStack(com.senne.imttech.registry.ModItems.SERVER_FAN.get()));
        }
        if (level.getBlockEntity(vault) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
            core.setHostname("dev-vault");
        }
    }

    /**
     * Na buildDatacenter (een tick later, als de kernen zich gemeld hebben): de racks kijken of ze
     * meedraaien, beide datacenters melden zich (zodat je er een backup naartoe kunt sturen), en het lab
     * maakt een backup naar dev-vault.
     */
    static void connectDatacenters(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        BlockPos vault = DATACENTER_VAULT;
        for (BlockPos pos : new BlockPos[] {vault.offset(3, 0, 0), LOGIC_LAB.offset(3, 0, 0), LOGIC_LAB.offset(3, 1, 0),
                LOGIC_LAB.offset(4, 0, 0)}) {
            if (level.getBlockEntity(pos) instanceof com.senne.imttech.logic.ServerRackBlockEntity rack) {
                com.senne.imttech.security.SecurityCoreBlockEntity core = com.senne.imttech.security.SecurityCores.coreAt(level, pos);
                if (core != null) {
                    com.senne.imttech.security.SecurityDevices.forget(level, core);
                }
                com.senne.imttech.logic.LogicServer.forgetAll();
                rack.update(level, pos, level.getBlockState(pos));
            }
        }
        com.senne.imttech.logic.LogicServer.forgetAll();
        if (level.getBlockEntity(vault) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
            core.datacenter().update(level, core);
        }
        if (level.getBlockEntity(LOGIC_LAB.offset(4, 0, 0)) instanceof com.senne.imttech.logic.ServerRackBlockEntity hot) {
            hot.setTemperature(30);
        }
        if (level.getBlockEntity(LOGIC_LAB) instanceof com.senne.imttech.security.SecurityCoreBlockEntity lab) {
            lab.datacenter().update(level, lab);
            net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
            args.putString("key", com.senne.imttech.logic.Datacenters.key(net.minecraft.core.GlobalPos.of(level.dimension(), vault)));
            com.senne.imttech.logic.Backups.handle(player, lab, "backup_target", args);
            com.senne.imttech.logic.Backups.handle(player, lab, "backup_now", new net.minecraft.nbt.CompoundTag());
        }
    }

    /** De nieuwste backup van het lab terugzetten (zoals de knop Restore in de app Backup). */
    private static void restoreLab(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        com.senne.imttech.logic.Datacenters.Backup backup = com.senne.imttech.logic.Backups.newest(level, player.getUUID());
        if (backup != null && level.getBlockEntity(LOGIC_LAB) instanceof com.senne.imttech.security.SecurityCoreBlockEntity lab) {
            net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
            args.putInt("id", backup.id());
            com.senne.imttech.logic.Backups.handle(player, lab, "backup_restore", args);
        }
    }


    /** Maakt één rack van het lab te heet (en zijn GPU's al wat versleten), zodat je de waarschuwingen ziet. */
    private static void heatUp(net.minecraft.server.level.ServerPlayer player, BlockPos rackPos, double temperature) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        if (level.getBlockEntity(rackPos) instanceof com.senne.imttech.logic.ServerRackBlockEntity rack) {
            for (int slot = 0; slot < 2; slot++) { // de twee GPU's bovenaan
                com.senne.imttech.logic.ServerPartItem.setWear(rack.parts().getItem(slot), 8 + slot * 3);
            }
            rack.setTemperature(temperature);
        }
        if (level.getBlockEntity(LOGIC_LAB) instanceof com.senne.imttech.security.SecurityCoreBlockEntity lab) {
            com.senne.imttech.logic.LogicServer.forgetAll();
            lab.datacenter().update(level, lab);
        }
    }

    private static void placeInput(net.minecraft.server.level.ServerLevel level, BlockPos pos,
                                   net.minecraft.world.level.block.state.BlockState state, String name) {
        level.setBlockAndUpdate(pos, state.setValue(com.senne.imttech.logic.InputDeviceBlock.FACING, net.minecraft.core.Direction.SOUTH));
        if (level.getBlockEntity(pos) instanceof com.senne.imttech.logic.InputDeviceBlockEntity device) {
            device.setName(name);
        }
    }

    /** Een kaart tegen de lezer van het lab: een goede van niveau 3, of een geblokkeerde. */
    static void readCard(net.minecraft.server.level.ServerPlayer player, boolean good) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(LOGIC_LAB) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core)) {
            return;
        }
        // De racks laten meteen kijken of ze meedraaien (anders duurt dat tot twee seconden).
        for (BlockPos rackPos : new BlockPos[] {LOGIC_LAB.offset(3, 0, 0), LOGIC_LAB.offset(3, 1, 0), LOGIC_LAB.offset(4, 0, 0)}) {
            if (level.getBlockEntity(rackPos) instanceof com.senne.imttech.logic.ServerRackBlockEntity rack) {
                rack.update(level, rackPos, level.getBlockState(rackPos));
            }
        }
        // De groepen van het lab (25 oktober): "lab" zit in "staff". Bestaan ze al, dan gebeurt er niets.
        core.logic().addGroup("staff", "");
        core.logic().addGroup("lab", "staff");
        var card = core.logic().issue(good ? "Lab access" : "Lost card", 3, player.getGameProfile().getName(), "lab");
        if (card == null) {
            return;
        }
        if (!good) {
            core.logic().setBlocked(card.number(), true);
        }
        var data = new com.senne.imttech.logic.KeycardData(core.ip(), core.hostname(), card.number(), 3, card.label());
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(com.senne.imttech.registry.ModItems.KEYCARD.get());
        com.senne.imttech.logic.KeycardItem.write(stack, data);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        com.senne.imttech.logic.LogicEvents.clear();
        com.senne.imttech.logic.LogicEvents.keycard(level, LOGIC_LAB.offset(1, 1, 7), player, data);
    }

    // ================================================================ Hoofdstuk 25: security drones

    /** Het droneprogramma van de showcase "Security drones": een vierkant vliegen, en laden als de batterij laag is. */
    private static final String HOVER_PATROL = """
            when started:
                up(2)
                while true:
                    forward(3)
                    turn_right()
                    if drone.battery < 30:
                        charge(50)
            """;

    /**
     * Twee Drone Docks in de werkplaats (voor "Security drones"), elk met een volle warmtegenerator ernaast, en weg met
     * de drones van een vorige keer.
     */
    private static void buildDroneDocks(net.minecraft.server.level.ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (com.senne.imttech.robot.GuardDroneEntity old : level.getEntitiesOfClass(com.senne.imttech.robot.GuardDroneEntity.class,
                new net.minecraft.world.phys.AABB(ROBOT_BAY).inflate(24))) {
            old.discard();
        }
        net.minecraft.world.level.block.state.BlockState dock = com.senne.imttech.registry.ModBlocks.DRONE_DOCK.get().defaultBlockState()
                .setValue(com.senne.imttech.arsenal.DroneDockBlock.FACING, net.minecraft.core.Direction.SOUTH);
        for (int x : new int[] {1, 5}) {
            level.setBlockAndUpdate(ROBOT_BAY.offset(x, 0, 7), dock);
            if (level.getBlockEntity(ROBOT_BAY.offset(x, 0, 7)) instanceof com.senne.imttech.arsenal.DroneDockBlockEntity be) {
                be.setOwner(player.getUUID());
                be.setBot(null);
            }
            BlockPos generator = ROBOT_BAY.offset(x == 1 ? 0 : 6, 0, 7);
            level.setBlockAndUpdate(generator, com.senne.imttech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                charger.fill(200_000);
            }
        }
    }

    /** Zet een nieuwe security drone neer op dit dock, zoals een speler dat doet (met de rechtermuisknop). */
    private static void placeDrone(net.minecraft.server.level.ServerPlayer player, BlockPos dock) {
        ItemStack before = player.getMainHandItem();
        ItemStack drone = com.senne.imttech.robot.GuardDroneItem.create(com.senne.imttech.robot.DroneData.DEFAULT);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, drone);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                Vec3.atCenterOf(dock).add(0, 0.5, 0), net.minecraft.core.Direction.UP, dock, false);
        drone.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, before);
    }

    /** Doe iets met de drone op dit dock. */
    private static void droneAt(net.minecraft.server.level.ServerPlayer player, BlockPos dock,
                                Consumer<com.senne.imttech.robot.GuardDroneEntity> action) {
        for (com.senne.imttech.robot.GuardDroneEntity drone : player.serverLevel().getEntitiesOfClass(
                com.senne.imttech.robot.GuardDroneEntity.class, new net.minecraft.world.phys.AABB(dock))) {
            action.accept(drone);
            return;
        }
    }

    /** Doe iets met de drone die het dichtst bij deze plek hangt (binnen 6 blokken). */
    private static void droneNear(net.minecraft.server.level.ServerPlayer player, BlockPos pos,
                                  Consumer<com.senne.imttech.robot.GuardDroneEntity> action) {
        com.senne.imttech.robot.GuardDroneEntity best = null;
        for (com.senne.imttech.robot.GuardDroneEntity drone : player.serverLevel().getEntitiesOfClass(
                com.senne.imttech.robot.GuardDroneEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(6))) {
            if (best == null || drone.distanceToSqr(Vec3.atCenterOf(pos)) < best.distanceToSqr(Vec3.atCenterOf(pos))) {
                best = drone;
            }
        }
        if (best != null) {
            action.accept(best);
        }
    }

    /** Opent in jouw spel het bordscherm van de drone op dit dock. */
    private static void showDroneBoard(BlockPos dock) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        for (com.senne.imttech.robot.GuardDroneEntity drone : mc.level.getEntitiesOfClass(
                com.senne.imttech.robot.GuardDroneEntity.class, new net.minecraft.world.phys.AABB(dock))) {
            mc.setScreen(new com.senne.imttech.client.robot.BoardScreen(drone.getId(), null));
            return;
        }
    }

    /**
     * Biebia's drones in de hangar van de toren (hoofdstuk 25), van deze speler: weg met die van een vorige keer, de docks
     * weer vrij, de kern zet er nieuwe op, en ze zijn van jou (alsof je de toren net gehackt hebt).
     */
    private static void towerDrones(net.minecraft.server.level.ServerPlayer player,
                                    com.senne.imttech.security.SecurityCoreBlockEntity core) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.phys.AABB tower = new net.minecraft.world.phys.AABB(BIEBIA_TOWER).expandTowards(41, 110, 55);
        for (com.senne.imttech.robot.GuardDroneEntity old : level.getEntitiesOfClass(com.senne.imttech.robot.GuardDroneEntity.class, tower)) {
            old.discard();
        }
        for (int x = 10; x <= 30; x += 4) {
            if (level.getBlockEntity(BIEBIA_TOWER.offset(x, 34, 7)) instanceof com.senne.imttech.arsenal.DroneDockBlockEntity dock) {
                dock.setBot(null);
            }
        }
        com.senne.imttech.robot.TowerDrones.fill(level, core);
        com.senne.imttech.robot.TowerDrones.handOver(level, core, player);
    }

    // ================================================================ Hoofdstuk 26: training

    /** Het robotprogramma van de showcase "Training": de trainee zoeken, schieten als de laser klaar is, en dan even wachten. */
    private static final String TRAINEE_HUNTER = """
            when started:
                write(pin(2), "trainee")
                while true:
                    if read(pin(1)) and read(pin(3)):
                        write(pin(3), true)
                        wait(2)
                    wait(0.25)
            """;

    /**
     * Voor "Training" (hoofdstuk 26), in de werkplaats: vooraan links een Training Start-blok (Bootcamp, trainingsschoten, 5
     * levens); achteraan een Training Goal-blok, links ervan een Training End-blok met een redstonelamp erop en rechts een
     * Super Battle in squad "Wave 2"; in het midden links en rechts twee Trainer-robots in squad "Wave 1" (alle drie uit,
     * met Scan, Face Recognition en Laser); en het diagram Waves.
     */
    private static void buildTraining(net.minecraft.server.level.ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        com.senne.imttech.training.Training.Session old = com.senne.imttech.training.Training.of(player.getUUID());
        if (old != null) {
            com.senne.imttech.training.Training.end(level, old, com.senne.imttech.training.Training.Result.STOPPED);
        }
        BlockPos start = ROBOT_BAY.offset(2, 0, 9);
        level.setBlockAndUpdate(start, ModBlocks.TRAINING_START.get().defaultBlockState());
        if (level.getBlockEntity(start) instanceof com.senne.imttech.training.TrainingStartBlockEntity be) {
            be.setOwner(player);
            be.setSettings(new com.senne.imttech.training.Training.Settings("Bootcamp",
                    com.senne.imttech.training.Training.Mode.TRAINING_SHOTS, 5, 0));
        }
        BlockPos end = ROBOT_BAY.offset(3, 0, 0);
        level.setBlockAndUpdate(end, ModBlocks.TRAINING_END.get().defaultBlockState());
        level.setBlockAndUpdate(end.above(), Blocks.REDSTONE_LAMP.defaultBlockState());
        if (level.getBlockEntity(end) instanceof com.senne.imttech.training.TrainingEndBlockEntity be) {
            be.setOwner(player);
            be.setSource("""
                    # Runs at every hit on the trainee.
                    when trainee_hit:
                        say("You're hit, cadet")
                    """);
        }
        level.setBlockAndUpdate(ROBOT_BAY.offset(4, 0, 0), ModBlocks.TRAINING_GOAL.get().defaultBlockState());
        Object[][] robots = {
                {"Trainer 1", "Wave 1", ROBOT_BAY.offset(0, 0, 5), -90f, com.senne.imttech.robot.RobotStyle.TRAINER},
                {"Trainer 2", "Wave 1", ROBOT_BAY.offset(8, 0, 5), 90f, com.senne.imttech.robot.RobotStyle.TRAINER},
                {"Heavy 1", "Wave 2", ROBOT_BAY.offset(5, 0, 0), 0f, com.senne.imttech.robot.RobotStyle.SUPER_BATTLE}};
        for (Object[] r : robots) {
            if (DevWorld.spawnStill(level, com.senne.imttech.registry.ModEntities.GUARD_ROBOT.get(), (BlockPos) r[2], (float) r[3])
                    instanceof com.senne.imttech.robot.GuardRobotEntity robot) {
                com.senne.imttech.robot.RobotStyle style = (com.senne.imttech.robot.RobotStyle) r[4];
                robot.setParts(new com.senne.imttech.robot.RobotParts(style, style, style, style));
                robot.setOwner(player);
                robot.setCustomName(net.minecraft.network.chat.Component.literal((String) r[0]));
                robot.setSquad((String) r[1]);
                robot.setBoard(com.senne.imttech.bot.BotBoard.EMPTY.with(1, "scan").with(2, "face_recognition").with(3, "laser"));
                robot.setProgram(program("Trainee hunter", TRAINEE_HUNTER, com.senne.imttech.logic.program.ProgramKind.ROBOT));
            }
        }
        if (level.getBlockEntity(ROBOT_BAY.offset(10, 0, 6)) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
            core.logic().put(null, program("Waves", """
                    when training_started("Bootcamp"):
                        say("Good luck, " + trainee)
                        activate("Wave 1")

                    when squad_down("Wave 1"):
                        say("Wave 1 is down. Here comes Wave 2")
                        activate("Wave 2")
                    """, com.senne.imttech.logic.program.ProgramKind.SERVER));
        }
    }

    /**
     * De inbraakoefening (hoofdstuk 27): het Start-blok op Break-in drill "Vault" (10 levens), een diagram dat bij het begin
     * Wave 1 aanzet, en zonder de Super Battle (anders schoot die je tijdens de hack al je levens af).
     */
    private static void buildDrill(net.minecraft.server.level.ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (level.getBlockEntity(ROBOT_BAY.offset(2, 0, 9)) instanceof com.senne.imttech.training.TrainingStartBlockEntity be) {
            be.setSettings(new com.senne.imttech.training.Training.Settings("Vault",
                    com.senne.imttech.training.Training.Mode.TRAINING_SHOTS, 10, 0, com.senne.imttech.training.Training.Kind.BREAK_IN));
        }
        robotAt(player, ROBOT_BAY.offset(5, 0, 0), net.minecraft.world.entity.Entity::discard);
        if (level.getBlockEntity(ROBOT_BAY.offset(10, 0, 6)) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
            core.logic().put(null, program("Drill", """
                    when training_started("Vault"):
                        say(trainee + " is breaking in")
                        activate("Wave 1")
                    """, com.senne.imttech.logic.program.ProgramKind.SERVER));
        }
    }

    /** De showcase speelt de terminal van de laptop uit, zoals een speler die alles in één keer goed doet. */
    private static void solveHack(net.minecraft.server.level.ServerPlayer player, BlockPos laptopPos) {
        if (!(player.serverLevel().getBlockEntity(laptopPos) instanceof com.senne.imttech.security.HackingLaptopBlockEntity laptop)) {
            return;
        }
        var terminal = new com.senne.imttech.security.HackTerminal(laptop.seed(), laptop.ownerName());
        String connect = "connect " + terminal.openPort();
        terminal.run(connect, 0); // pas daarna staat het wachtwoord vast
        for (String command : java.util.List.of(connect, "login " + terminal.password(), "override " + terminal.guardPid(),
                "disable watchdog", "set owner " + laptop.ownerName())) {
            com.senne.imttech.security.TowerHack.handleCommand(player, new com.senne.imttech.network.HackCommandPayload(laptopPos, command));
        }
    }

    /** Jij schiet met de Ion Blaster op de robot die het dichtst bij deze plek staat. */
    private static void ionAt(net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
        robotNear(player, pos, robot -> {
            player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, robot.getBoundingBox().getCenter());
            com.senne.imttech.robot.IonBlasterItem.fire(player.serverLevel(), player);
        });
    }

    // ================================================================ Hoofdstuk 21: de robotwerkplaats

    static final BlockPos ROBOT_BAY = new BlockPos(-220, -60, 140);

    /**
     * De robotwerkplaats: een vloer, links een Robot Assembler (vol: vier onderdelen en een printplaat),
     * rechts twee Robot Docks met een volle warmtegenerator ertussen, en achteraan een beveiligingskern (met
     * een generator eronder) voor de app Robots. Robots van een vorige keer gaan eerst weg.
     */
    private static void buildRobotBay(net.minecraft.server.level.ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (com.senne.imttech.robot.GuardRobotEntity old : level.getEntitiesOfClass(com.senne.imttech.robot.GuardRobotEntity.class,
                new net.minecraft.world.phys.AABB(ROBOT_BAY).inflate(24))) {
            old.discard();
        }
        for (int x = -1; x <= 13; x++) {
            for (int z = -1; z <= 10; z++) {
                level.setBlockAndUpdate(ROBOT_BAY.offset(x, -1, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
                for (int y = 0; y <= 3; y++) {
                    level.setBlockAndUpdate(ROBOT_BAY.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        BlockPos assembler = ROBOT_BAY.offset(2, 0, 2);
        level.setBlockAndUpdate(assembler, com.senne.imttech.registry.ModBlocks.ROBOT_ASSEMBLER.get().defaultBlockState()
                .setValue(com.senne.imttech.robot.RobotAssemblerBlock.FACING, net.minecraft.core.Direction.SOUTH));
        if (level.getBlockEntity(assembler) instanceof com.senne.imttech.robot.RobotAssemblerBlockEntity be) {
            com.senne.imttech.robot.RobotStyle[] styles = {com.senne.imttech.robot.RobotStyle.COMMANDO,
                    com.senne.imttech.robot.RobotStyle.SUPER_BATTLE, com.senne.imttech.robot.RobotStyle.TRAINER,
                    com.senne.imttech.robot.RobotStyle.BATTLE};
            for (com.senne.imttech.robot.RobotSlot slot : com.senne.imttech.robot.RobotSlot.values()) {
                be.items().setItem(slot.ordinal(), new ItemStack(ModItems.robotPart(slot, styles[slot.ordinal()])));
            }
            be.items().setItem(com.senne.imttech.robot.RobotAssemblerBlockEntity.BOARD, new ItemStack(ModItems.ROBOT_CIRCUIT_BOARD.get()));
            be.items().setItem(com.senne.imttech.robot.RobotAssemblerBlockEntity.ROBOT, ItemStack.EMPTY);
        }
        net.minecraft.world.level.block.state.BlockState dock = com.senne.imttech.registry.ModBlocks.ROBOT_DOCK.get().defaultBlockState()
                .setValue(com.senne.imttech.robot.RobotDockBlock.FACING, net.minecraft.core.Direction.SOUTH);
        level.setBlockAndUpdate(ROBOT_BAY.offset(6, 0, 2), dock);
        level.setBlockAndUpdate(ROBOT_BAY.offset(8, 0, 2), dock);
        for (BlockPos generator : new BlockPos[] {ROBOT_BAY.offset(7, 0, 2), ROBOT_BAY.offset(10, -1, 6)}) {
            level.setBlockAndUpdate(generator, com.senne.imttech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                charger.fill(200_000);
            }
        }
        BlockPos core = ROBOT_BAY.offset(10, 0, 6);
        level.setBlockAndUpdate(core, com.senne.imttech.registry.ModBlocks.SECURITY_CORE.get().defaultBlockState());
        if (level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
            be.setOwner(player);
            be.ensureSystem(level);
        }
    }

    /** Het robotprogramma van de showcase "Robot programs": een vierkantje, en zwaaien als de server dat zegt. */
    private static final String BAY_PATROL = """
            when started:
                while true:
                    forward(3)
                    turn_right()
                    send("corner")

            when ordered("wave"):
                for i in range(2):
                    turn_left()
                    turn_right()
            """;

    /** De derde robot van "Robot senses and weapons": schiet op de plek uit een opdracht, en stopt dan weer. */
    private static final String AIM_GUARD = """
            laserPin = pin(3)

            when ordered("fire"):
                aim(order.x, order.y, order.z)
                write(laserPin, true)
                wait(4)
                write(laserPin, false)
                aim()
            """;

    /**
     * Voor "Robot programs" (hoofdstuk 23): een serverrack naast de kern van de werkplaats (met stroom), het
     * robotprogramma Patrol en het diagram Corners op die server.
     */
    private static void buildBayServer(net.minecraft.server.level.ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos rack = ROBOT_BAY.offset(12, 0, 6);
        level.setBlockAndUpdate(rack.below(), com.senne.imttech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(rack.below()) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
            charger.fill(300_000);
        }
        level.setBlockAndUpdate(rack, com.senne.imttech.registry.ModBlocks.SERVER_RACK.get().defaultBlockState()
                .setValue(com.senne.imttech.logic.ServerRackBlock.FACING, net.minecraft.core.Direction.SOUTH));
        if (level.getBlockEntity(rack) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
            be.insert(new ItemStack(ModItems.SERVER_CPU.get()));
            be.insert(new ItemStack(ModItems.SERVER_RAM.get()));
            be.insert(new ItemStack(ModItems.SERVER_SSD.get()));
            be.insert(new ItemStack(ModItems.SERVER_FAN.get()));
        }
        if (level.getBlockEntity(ROBOT_BAY.offset(10, 0, 6)) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
            core.logic().put(null, program("Patrol", BAY_PATROL, com.senne.imttech.logic.program.ProgramKind.ROBOT));
            core.logic().put(null, program("Corners", """
                    corners = 0

                    when message("corner"):
                        corners = corners + 1
                        if corners == 4:
                            say(sender.name + " walked a full round")
                            corners = 0
                    """, com.senne.imttech.logic.program.ProgramKind.SERVER));
        }
    }

    /** Een programma uit deze code, zoals de app Logic hem bewaart. */
    private static com.senne.imttech.logic.program.LogicProgram program(String name, String code,
                                                                       com.senne.imttech.logic.program.ProgramKind kind) {
        return new com.senne.imttech.logic.program.LogicProgram(name, true, com.senne.imttech.logic.program.GraphConverter.toGraph(
                com.senne.imttech.logic.program.ScriptParser.parse(code, kind), null), code, kind);
    }

    /** Zet een robot-item neer op dit dock, zoals een speler dat doet (met de rechtermuisknop). */
    private static void placeRobot(net.minecraft.server.level.ServerPlayer player, BlockPos dock, ItemStack robot) {
        ItemStack before = player.getMainHandItem();
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, robot);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                Vec3.atCenterOf(dock).add(0, 0.5, 0), net.minecraft.core.Direction.UP, dock, false);
        robot.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, before);
    }

    /** Opent in jouw spel het bordscherm van de robot op deze plek, en doet er iets mee (de hittebalk, Inspect). */
    private static void showBoard(BlockPos pos, Consumer<com.senne.imttech.client.robot.BoardScreen> action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        for (com.senne.imttech.robot.GuardRobotEntity robot : mc.level.getEntitiesOfClass(
                com.senne.imttech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(pos))) {
            com.senne.imttech.client.robot.BoardScreen board = new com.senne.imttech.client.robot.BoardScreen(robot.getId(), null);
            mc.setScreen(board);
            action.accept(board);
            return;
        }
    }

    /** Doe iets met de robot die op deze plek staat. */
    private static void robotAt(net.minecraft.server.level.ServerPlayer player, BlockPos pos,
                                Consumer<com.senne.imttech.robot.GuardRobotEntity> action) {
        for (com.senne.imttech.robot.GuardRobotEntity robot : player.serverLevel().getEntitiesOfClass(
                com.senne.imttech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(pos))) {
            action.accept(robot);
            return;
        }
    }

    /** Doe iets met de robot die het dichtst bij deze plek staat (binnen 4 blokken). */
    private static void robotNear(net.minecraft.server.level.ServerPlayer player, BlockPos pos,
                                  Consumer<com.senne.imttech.robot.GuardRobotEntity> action) {
        com.senne.imttech.robot.GuardRobotEntity best = null;
        for (com.senne.imttech.robot.GuardRobotEntity robot : player.serverLevel().getEntitiesOfClass(
                com.senne.imttech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(4))) {
            if (best == null || robot.distanceToSqr(Vec3.atCenterOf(pos)) < best.distanceToSqr(Vec3.atCenterOf(pos))) {
                best = robot;
            }
        }
        if (best != null) {
            action.accept(best);
        }
    }

    /**
     * Een robot die er niet meer is (18 oktober): hij staat alleen in de lijst (BotNetwork), niet in de wereld, zoals een
     * robot die je vóór de fix oppakte. Van deze speler, met deze naam, op deze plek.
     */
    private static void ghostRobot(net.minecraft.server.level.ServerPlayer player, BlockPos pos, String name) {
        com.senne.imttech.robot.GuardRobotEntity ghost = com.senne.imttech.registry.ModEntities.GUARD_ROBOT.get()
                .create(player.serverLevel());
        if (ghost != null) {
            ghost.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            ghost.setOwner(player);
            ghost.setCustomName(net.minecraft.network.chat.Component.literal(name));
            com.senne.imttech.bot.BotNetwork.get(player.serverLevel()).update(ghost);
        }
    }

    /** De knop Delete (na "Sure?") in de app Robots, voor de robot van deze speler met deze naam. */
    private static void deleteRobot(net.minecraft.server.level.ServerPlayer player, BlockPos core, String name) {
        for (com.senne.imttech.bot.BotNetwork.Entry entry : com.senne.imttech.bot.BotNetwork.get(player.serverLevel())
                .ownedBy(player.getUUID())) {
            if (entry.name().equals(name)) {
                net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
                args.putString("id", entry.id().toString());
                com.senne.imttech.security.os.SecurityOs.handleAction(player,
                        new com.senne.imttech.network.OsActionPayload(core, "robot_delete", args));
            }
        }
    }

    /** Iets doen met de Security OS-desktop die nu openstaat (als hij openstaat). */
    private static void desktop(Consumer<com.senne.imttech.client.os.SecurityOsScreen> action) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
            action.accept(os);
        }
    }

    /** Een schakelaar van het hele netwerk, zoals de knoppen in Devices en Access. */
    private static void networkSwitch(net.minecraft.server.level.ServerPlayer player, BlockPos core, String name, boolean on) {
        net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
        args.putString("name", name);
        args.putBoolean("on", on);
        com.senne.imttech.security.os.SecurityOs.handleAction(player,
                new com.senne.imttech.network.OsActionPayload(core, "flag", args));
    }

    /** Een camera een stukje draaien, zoals de pijltjes bij het grote beeld. */
    private static void aimCamera(net.minecraft.server.level.ServerPlayer player, BlockPos core, BlockPos camera, float pan) {
        net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
        args.putInt("x", camera.getX());
        args.putInt("y", camera.getY());
        args.putInt("z", camera.getZ());
        args.putFloat("pan", pan);
        args.putFloat("tilt", 0f);
        com.senne.imttech.security.os.SecurityOs.handleAction(player,
                new com.senne.imttech.network.OsActionPayload(core, "cam_aim", args));
    }

    /**
     * Alle eigen geluiden, één voor één, met hun naam als titel. (Een titel in beeld is Engels,
     * want die zie je in het spel.)
     */
    private static void soundTour(DevScript s) {
        Object[][] sounds = {
                {"Notification", "info, like 'arrived at your destination'", ModSounds.IMT_NOTIFY},
                {"Warning", "something needs your attention", ModSounds.IMT_ALERT},
                {"Denied", "access denied or it failed", ModSounds.IMT_DENIED},
                {"Ore scan", "", ModSounds.IMT_SCAN},
                {"Menu open", "the G menu", ModSounds.IMT_MENU_OPEN},
                {"Select", "choosing an option", ModSounds.IMT_SELECT},
                {"Upgrade installed", "charging station", ModSounds.STATION_UPGRADE},
                {"Drone launch", "", ModSounds.DRONE_DEPLOY},
                {"Drone laser", "", ModSounds.DRONE_LASER},
                {"Drone recall", "", ModSounds.DRONE_RECALL},
                {"Drone hit", "", ModSounds.DRONE_HURT},
                {"Drone destroyed", "", ModSounds.DRONE_DESTROYED},
                {"Satellite lock", "", ModSounds.SATELLITE_LOCK},
                {"Satellite charge", "", ModSounds.SATELLITE_CHARGE},
                {"Satellite strike", "", ModSounds.SATELLITE_FIRE},
                {"Message", "comms", ModSounds.COMMS_MESSAGE},
                {"Ping", "comms", ModSounds.COMMS_PING},
                {"Laser cannon on", "", ModSounds.LASER_CANNON_ARM},
                {"Laser cannon shot", "", ModSounds.LASER_CANNON_FIRE},
                {"Laser cannon off", "", ModSounds.LASER_CANNON_DISARM},
                {"Boot", "putting the glasses on", ModSounds.IMT_BOOT},
                {"Boot step", "loading bar", ModSounds.IMT_BLIP},
                {"Identity confirmed", "", ModSounds.IMT_CONFIRM},
        };
        int number = 1;
        for (Object[] sound : sounds) {
            @SuppressWarnings("unchecked")
            java.util.function.Supplier<net.minecraft.sounds.SoundEvent> event =
                    (java.util.function.Supplier<net.minecraft.sounds.SoundEvent>) sound[2];
            s.title(number == 1 ? 10 : 50, number + ". " + sound[0], (String) sound[1])
                    .step(5, () -> ImtClientEvents.playUi(event.get(), 1.0f));
            number++;
        }
        s.pause(60);
    }

    private static Vec3 towerCentre() {
        return new Vec3(BIEBIA_TOWER.getX() + 20.5, BIEBIA_TOWER.getY() + 20, BIEBIA_TOWER.getZ() + 20.5);
    }

    private static Supplier<KeyMapping> key(Supplier<KeyMapping> key) {
        return key;
    }

    /** Kijk de husks één voor één aan en markeer ze, zoals met R. */
    private static void markAll(DevScript s) {
        for (BlockPos target : TARGETS) {
            s.lookAt(10, mob(target)).step(5, ImtClientEvents::markLookTarget);
        }
        s.pause(20);
    }

    /** Een toets in het open scherm (bijvoorbeeld pijltje rechts in het G-menu). */
    private static void press(int key) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            mc.screen.keyPressed(key, 0, 0);
        }
    }

    /** Navigeren naar waypoint nummer {@code index}, zoals de knop "Navigate". */
    private static void navigate(int index) {
        PacketDistributor.sendToServer(new WaypointActionPayload(WaypointActionPayload.Op.NAVIGATE, index, "", 0));
    }

    private static void relay(ImtRedstone.Op op) {
        PacketDistributor.sendToServer(RelayActionPayload.switchOne(0, op));
    }

    private static void holo(HoloActionPayload.Op op, int value) {
        PacketDistributor.sendToServer(new HoloActionPayload(HOLO_TABLE, op, value));
    }

    /** Een knop uit het scherm van de laserzender LASER_A, zoals jij hem indrukt. */
    private static void laser(ServerPlayer player, LaserActionPayload.Op op, int value) {
        com.senne.imttech.shield.LaserEmitters.handleAction(player, new LaserActionPayload(LASER_A, op, value));
    }

    /** Een knop uit het scherm van de laserbrug, voor de brug van de showcase (hoofdstuk 34). */
    private static void bridge(ServerPlayer player, LaserActionPayload.Op op, int value) {
        com.senne.imttech.shield.LaserBridges.handleAction(player, new LaserActionPayload(ShowcaseScene.BRIDGE, op, value));
    }

    /** Rechtsklik op een blok, zoals met de muis. */
    private static void rightClick(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return;
        }
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.45, 0), Direction.UP, pos, false);
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
    }

    /** Tijdens het richten van een illusie: neerzetten (true) of annuleren, zoals met de muis. */
    private static void placeIllusion(boolean place) {
        PacketDistributor.sendToServer(new IllusionPlacePayload(place));
    }

    /** Rechtsklik in de lucht met wat je in je hand hebt. */
    private static void useItem() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.gameMode != null) {
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        }
    }

    /**
     * Bombardement en schuivende laser zitten op slot tot je ze vrijspeelt (zie SatelliteUpgrade).
     * De showcase speelt ze vrij, anders doet de satelliet in die demo's niets.
     */
    private static void unlockSatelliteModes(ServerPlayer player) {
        SatelliteUpgrades upgrades = player.getData(ModAttachments.SATELLITE);
        for (SatelliteUpgrade mode : List.of(SatelliteUpgrade.PRECISION, SatelliteUpgrade.BOMBARDMENT, SatelliteUpgrade.SWEEP)) {
            if (upgrades.level(mode) == 0) {
                upgrades = upgrades.with(mode, 1);
            }
        }
        player.setData(ModAttachments.SATELLITE, upgrades);
    }

    /** Alle upgrades van de satelliet op hetzelfde niveau (zonder onderdelen of drones). */
    private static void satelliteAt(ServerPlayer player, int level) {
        SatelliteUpgrades upgrades = SatelliteUpgrades.START;
        for (SatelliteUpgrade upgrade : SatelliteUpgrade.values()) {
            upgrades = upgrades.with(upgrade, level);
        }
        player.setData(ModAttachments.SATELLITE, upgrades);
    }

    private static void wearSuit(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.TECH_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.TECH_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.TECH_BOOTS.get()));
    }

    /**
     * Seppe heeft zijn bril vrijgegeven ("Transfer ownership") en jij zet hem op. Wie hem als
     * eerste opzet, wordt de nieuwe eigenaar.
     */
    private static void wearFriendsReleasedGlasses(ServerPlayer player) {
        ServerPlayer friend = ShowcaseScene.friend(player);
        if (friend == null) {
            return;
        }
        ItemStack glasses = DevWorld.ownGlasses(friend);
        ImtOwner owner = ImtData.getOwner(glasses);
        if (owner != null) {
            ImtData.setOwner(glasses, owner.withTransferPending(true));
        }
        player.setItemSlot(EquipmentSlot.HEAD, glasses);
    }

    /** De showcase-speler is normaal onkwetsbaar; voor de schild-demo even niet. */
    private static void invulnerable(ServerPlayer player, boolean on) {
        player.getAbilities().invulnerable = on;
        player.onUpdateAbilities();
    }

    /** Een pijl (van niemand) die vanaf een plek naast de speler recht op zijn borst af vliegt. */
    private static void shootArrowAt(ServerPlayer player, Vec3 offset) {
        ServerLevel level = player.serverLevel();
        Vec3 target = player.position().add(0, 1.2, 0);
        Vec3 from = target.add(offset);
        Vec3 direction = target.subtract(from).normalize();
        Arrow arrow = new Arrow(EntityType.ARROW, level);
        arrow.setPos(from.x, from.y, from.z);
        arrow.shoot(direction.x, direction.y, direction.z, 2.0f, 0);
        arrow.setNoGravity(true);
        level.addFreshEntity(arrow);
    }

    /**
     * Drie raiddrones die buiten de koepel beginnen (aan de noordkant, waar je naar kijkt). Ze
     * willen naar jou toe, maar jij staat binnen.
     */
    private static void raidOutsideDome(ServerPlayer player) {
        Vec3 centre = Vec3.atBottomCenterOf(DOME);
        double[][] spots = {{-14, -22}, {0, -27}, {14, -22}};
        for (double[] spot : spots) {
            Vec3 landing = centre.add(spot[0], 4, spot[1]);
            RaidDroneEntity.spawn(player.serverLevel(), landing.add(spot[0] * 0.5, 30, spot[1] * 0.5), landing);
        }
    }

    /** Hoofdstuk 60: één bewaker twaalf blokken voor je, met zijn rug naar je toe, die een route van tien blokken breed loopt. */
    private static void spawnHitmanGuard(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        RavenSoldierEntity soldier = com.senne.imttech.registry.ModEntities.RAVEN_SOLDIER.get().create(level);
        if (soldier == null) {
            return;
        }
        BlockPos post = BlockPos.containing(player.getX(), player.getY(), player.getZ() - 12);
        soldier.moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 180, 0);
        soldier.finalizeSpawn(level, level.getCurrentDifficultyAt(post), net.minecraft.world.entity.MobSpawnType.SPAWN_EGG, null);
        soldier.setPersistenceRequired();
        soldier.setRoute(List.of(post.west(5), post.east(5)));
        level.addFreshEntity(soldier);
    }

    /** Hoofdstuk 60: de dichtstbijzijnde bewaker valt (zijn lichaam blijft liggen). */
    private static void dropHitmanGuard(ServerPlayer player) {
        RavenSoldierEntity nearest = null;
        for (RavenSoldierEntity soldier : player.serverLevel().getEntitiesOfClass(RavenSoldierEntity.class,
                player.getBoundingBox().inflate(40), RavenSoldierEntity::isAlive)) {
            if (nearest == null || soldier.distanceToSqr(player) < nearest.distanceToSqr(player)) {
                nearest = soldier;
            }
        }
        if (nearest != null) {
            nearest.kill();
        }
    }

    /** Twee Raven-soldaten een eind voor je, met hun geweer, die jou als doelwit hebben. */
    private static void spawnRavenSquad(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (int side = -1; side <= 1; side += 2) {
            RavenSoldierEntity soldier = com.senne.imttech.registry.ModEntities.RAVEN_SOLDIER.get().create(level);
            if (soldier == null) {
                continue;
            }
            soldier.moveTo(player.getX() + side * 4, player.getY(), player.getZ() - 12, 0, 0);
            soldier.finalizeSpawn(level, level.getCurrentDifficultyAt(soldier.blockPosition()),
                    net.minecraft.world.entity.MobSpawnType.SPAWN_EGG, null);
            level.addFreshEntity(soldier);
            soldier.setTarget(player);
        }
    }

    /** De eerste drone uit het arsenaal gehavend maken en terugroepen: hij landt naast je. */
    private static void recallDamagedDrone(ServerPlayer player) {
        player.getInventory().add(new ItemStack(Items.IRON_INGOT, 16));
        List<ArsenalDrone> drones = ArsenalServer.get(player).drones();
        if (drones.isEmpty()) {
            return;
        }
        ArsenalDrone first = drones.getFirst();
        ArsenalServer.setHealth(player, first.id(), first.health() * 0.3f);
        ArsenalServer.recall(player, first.id());
    }

    /** Je teruggeroepen drone, als die naast je staat (of nog aan het zakken is). */
    private static @Nullable DroneEntity groundedDrone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        return mc.level.getEntitiesOfClass(DroneEntity.class, mc.player.getBoundingBox().inflate(6),
                drone -> drone.getMode() == DroneEntity.Mode.GROUNDED)
                .stream().findFirst().orElse(null);
    }

    /**
     * Is hij geland? Hij staat dan stil vlak boven de grond. (Op "onGround" kun je niet wachten:
     * een drone zweeft net boven het blok eronder en raakt het nooit echt.)
     */
    private static boolean droneLandedNearYou() {
        Minecraft mc = Minecraft.getInstance();
        DroneEntity drone = groundedDrone();
        return drone != null && mc.player != null && Math.abs(drone.getY() - drone.yOld) < 0.01
                && drone.getY() - mc.player.getY() < 1.5;
    }

    /** Kijk naar de gelande drone en rechtsklik erop, zoals met de muis. */
    private static void rightClickGroundedDrone() {
        Minecraft mc = Minecraft.getInstance();
        DroneEntity drone = groundedDrone();
        if (drone == null || mc.player == null || mc.gameMode == null) {
            return;
        }
        mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, drone.position());
        mc.gameMode.interact(mc.player, drone, InteractionHand.MAIN_HAND);
    }

    /** Het laadstation vullen: een bijna lege bril (hoofdstuk 54: redstone en upgrades gaan er niet meer in). */
    private static void fillStation(ServerPlayer player) {
        if (player.serverLevel().getBlockEntity(CHARGING_STATION) instanceof ChargingStationBlockEntity station) {
            ItemStack glasses = DevWorld.ownGlasses(player);
            ImtData.setEnergy(glasses, ImtData.getMaxEnergy(glasses) / 5);
            station.setItem(ChargingStationBlockEntity.SLOT_GLASSES, glasses);
        }
    }

    /**
     * Het creative-scherm, meteen op een tabblad van I.M.T. Welk tabblad open staat, onthoudt
     * Minecraft in een verborgen veld (selectedTab); dat zetten we vooraf, dan kiest het scherm
     * bij het openen zelf dat tabblad (en de goede pagina).
     */
    static void openModTab(CreativeModeTab tab) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        try {
            java.lang.reflect.Field selected = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
            selected.setAccessible(true);
            selected.set(null, tab);
        } catch (ReflectiveOperationException | RuntimeException e) {
            ImtTech.LOGGER.warn("[showcase] kan het tabblad {} niet kiezen", tab.getDisplayName().getString(), e);
        }
        mc.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(),
                mc.options.operatorItemsTab().get()));
    }

    /** Seppe typt een commando (bijvoorbeeld een privébericht). */
    private static void friendSays(ServerPlayer player, String command) {
        ServerPlayer friend = ShowcaseScene.friend(player);
        if (friend != null && player.getServer() != null) {
            player.getServer().getCommands().performPrefixedCommand(friend.createCommandSourceStack(), command);
        }
    }

    private static void openAdvancements() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        AdvancementsScreen screen = new AdvancementsScreen(mc.player.connection.getAdvancements());
        mc.setScreen(screen);
        AdvancementHolder root = mc.player.connection.getAdvancements().get(
                ResourceLocation.fromNamespaceAndPath("imttech", "root"));
        if (root != null) {
            mc.player.connection.getAdvancements().setSelectedTab(root, true);
        }
    }

    /** Maakt alles van jouw kant weer schoon: geen scherm, geen ingedrukte toetsen, eigen ogen. */
    static void clientReset() {
        Minecraft mc = Minecraft.getInstance();
        // Toch dood gegaan? Eerst herboren worden, anders blijft het doodsscherm staan.
        if (mc.player != null && mc.player.isDeadOrDying()) {
            mc.player.respawn();
        }
        mc.setScreen(null);
        KeyMapping.releaseAll();
        ImtKeys.ZOOM.setDown(false);
        LaserCannonControl.reset();
        SuitFlight.reset();
        DroneCamera.exit();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.hideGui = false;
    }
}
