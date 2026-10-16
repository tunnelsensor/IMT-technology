package com.senne.starktech.client.dev;

import static com.senne.starktech.client.dev.ShowcaseScene.BLACK_BOX;
import static com.senne.starktech.client.dev.ShowcaseScene.CHARGING_STATION;
import static com.senne.starktech.client.dev.ShowcaseScene.CRASH_SITE;
import static com.senne.starktech.client.dev.ShowcaseScene.CHEST;
import static com.senne.starktech.client.dev.ShowcaseScene.COW;
import static com.senne.starktech.client.dev.ShowcaseScene.DOME;
import static com.senne.starktech.client.dev.ShowcaseScene.FAR_TARGET;
import static com.senne.starktech.client.dev.ShowcaseScene.FRIEND_SPOT;
import static com.senne.starktech.client.dev.ShowcaseScene.GRID_CUT;
import static com.senne.starktech.client.dev.ShowcaseScene.HEAT_GENERATOR;
import static com.senne.starktech.client.dev.ShowcaseScene.HYDRA_BUNKER;
import static com.senne.starktech.client.dev.ShowcaseScene.HOLO_TABLE;
import static com.senne.starktech.client.dev.ShowcaseScene.HOME;
import static com.senne.starktech.client.dev.ShowcaseScene.HUT;
import static com.senne.starktech.client.dev.ShowcaseScene.LAMP;
import static com.senne.starktech.client.dev.ShowcaseScene.LASER_A;
import static com.senne.starktech.client.dev.ShowcaseScene.LASER_POWER;
import static com.senne.starktech.client.dev.ShowcaseScene.PYRAMID;
import static com.senne.starktech.client.dev.ShowcaseScene.SOLAR_PANEL;
import static com.senne.starktech.client.dev.ShowcaseScene.STARK_TOWER;
import static com.senne.starktech.client.dev.ShowcaseScene.TARGETS;

import com.mojang.blaze3d.platform.InputConstants;
import com.senne.starktech.StarkTech;
import com.senne.starktech.arsenal.ArsenalDrone;
import com.senne.starktech.arsenal.ArsenalServer;
import com.senne.starktech.client.ArsenalScreen;
import com.senne.starktech.client.CommsScreen;
import com.senne.starktech.client.DomeScreen;
import com.senne.starktech.client.DroneCamera;
import com.senne.starktech.client.EdithClientEvents;
import com.senne.starktech.client.EdithKeys;
import com.senne.starktech.client.EdithMenuScreen;
import com.senne.starktech.client.MusicScreen;
import com.senne.starktech.client.NavigationScreen;
import com.senne.starktech.client.RelayEditScreen;
import com.senne.starktech.client.RelayListScreen;
import com.senne.starktech.client.RepulsorControl;
import com.senne.starktech.client.SatelliteScreen;
import com.senne.starktech.client.SuitFlight;
import com.senne.starktech.client.WaypointEditScreen;
import com.senne.starktech.client.voice.VoiceCommandsScreen;
import com.senne.starktech.comms.EdithComms;
import com.senne.starktech.drone.StarkDroneEntity;
import com.senne.starktech.edith.EdithAction;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithOwner;
import com.senne.starktech.hydra.HydraSoldierEntity;
import com.senne.starktech.holo.HoloMode;
import com.senne.starktech.network.CommsPayload;
import com.senne.starktech.network.HoloActionPayload;
import com.senne.starktech.network.IllusionPlacePayload;
import com.senne.starktech.network.LaserActionPayload;
import com.senne.starktech.network.RelayActionPayload;
import com.senne.starktech.network.SatelliteUpgradePayload;
import com.senne.starktech.network.WaypointActionPayload;
import com.senne.starktech.raid.EdithRaids;
import com.senne.starktech.raid.RaidDroneEntity;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModCreativeTabs;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.satellite.SatelliteUpgrade;
import com.senne.starktech.satellite.SatelliteUpgrades;
import com.senne.starktech.shield.ShieldColor;
import com.senne.starktech.station.ChargingStationBlockEntity;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Alle functies van de mod, in de volgorde waarin de showcase ze laat zien.
 *
 * Elke functie heeft:
 * - een titel (Engels, want die verschijnt in het spel),
 * - een uitleg voor in je terminal: waar je op moet letten (Nederlands, zie je niet in het spel),
 * - een spelmodus en een plek waar je begint,
 * - een draaiboek: wat er gebeurt. Waar het kan via precies dezelfde weg als jouw toetsen
 *   (EdithAction), zodat je de echte functie ziet en geen nagebootste.
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
        add("E.D.I.T.H. glasses", "De opstart van de bril (ruim 5 seconden): groot E.D.I.T.H.-logo in "
                + "reactorringen, een laadbalk met wat er geladen wordt (je echte drones en batterij), dan "
                + "scant hij je gezicht terwijl de balk op 90% wacht, IDENTITY CONFIRMED met je naam, en "
                + "E.D.I.T.H. begroet je (met stem als die geïnstalleerd is, anders in beeld). Geluid: 'arc "
                + "reactor'. Daarna de HUD: energie links boven, kompas boven, meldingen in het midden.",
                start, 180, 0, s -> s
                .onServer(0, player -> player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY))
                .pause(40)
                .onServer(0, DevWorld::wearGlasses)
                .pause(200));

        add("E.D.I.T.H. sounds", "Alle eigen geluiden van E.D.I.T.H., één voor één, met hun naam in beeld: "
                + "meldingen (info zoals 'aangekomen', waarschuwing, geweigerd), scan, menu, laadstation, "
                + "drones, satelliet, comms, repulsor en het opstarten. Zeg bij een nummer welke je anders wilt.",
                start, 180, 0, ShowcaseFeatures::soundTour);

        add("Owner lock", "Een bril van iemand anders (Happy): 'Access denied'. Alleen de eigenaar "
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
                + "'Transfer ownership'): E.D.I.T.H. meldt dat de volgende die hem opzet de nieuwe eigenaar "
                + "wordt; nog eens drukken = annuleren. Daarna heeft Happy zíjn bril vrijgegeven en zet jij "
                + "hem op: geen 'Access denied' zoals bij 'Owner lock', maar de opstart met jouw naam, want "
                + "nu is hij van jou.", start, 180, 0, s -> s
                .action(10, EdithAction.AUTHORIZE_TRANSFER)
                .pause(90)
                .action(0, EdithAction.AUTHORIZE_TRANSFER)
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
                .action(10, EdithAction.TOGGLE_THREAT_SCAN)
                .onServer(40, ShowcaseScene::aggroBunker)
                .onServer(40, ShowcaseScene::aggroBunker)
                .onServer(40, ShowcaseScene::aggroBunker)
                .pause(40)
                .action(0, EdithAction.TOGGLE_THREAT_SCAN)
                .pause(20));

        add("Night vision (N)", "Het wordt nacht. Eerst zonder, dan met nachtzicht van de bril.",
                start, -90, 0, s -> s
                .command(0, "time set midnight")
                .lookAt(5, Vec3.atCenterOf(PYRAMID))
                .pause(70)
                .action(0, EdithAction.TOGGLE_NIGHT_VISION)
                .pause(100)
                .action(0, EdithAction.TOGGLE_NIGHT_VISION)
                .command(10, "time set noon"));

        add("Zoom (V)", "Inzoomen op de Stark Tower in de verte, met een afstandsmeter naast het vizier.",
                start, -90, 0, s -> s
                .lookAt(5, towerCentre().add(0, 10, 0))
                .pause(30)
                .key(0, () -> EdithKeys.ZOOM, true)
                .pause(110)
                .key(0, () -> EdithKeys.ZOOM, false)
                .pause(20));

        add("Ore scanner (B)", "Een scan door de grond: ertsen onder je voeten worden gemarkeerd, "
                + "met hun soort en kleur.", start, 180, 55, s -> s
                .action(10, EdithAction.ORE_SCAN)
                .pause(160));

        add("E.D.I.T.H. menu (G)", "Het ronde menu met alle functies, tabblad voor tabblad: Combat "
                + "(repulsor, raketten, rayshield), Satellite & Arsenal (de drie satellietaanvallen apart, "
                + "het satellietscherm en het arsenaal), Drones, Recon drones (camera en spion), Transport, "
                + "Tech, Illusion, Information (ertsen, dreigingen, doelanalyse), Navigation en Systems "
                + "(nu met nachtzicht). Bij een stand die je nog niet vrijspeelde staat LOCKED.", start, 180, 0, s -> {
            s.open(10, EdithMenuScreen::new);
            for (int i = 0; i < com.senne.starktech.client.MenuTab.values().length; i++) {
                s.step(45, () -> press(InputConstants.KEY_RIGHT));
            }
            s.close(45);
        });

        // ---------------------------------------------------------------- Drones
        add("Drone swarm (J)", "Markeer de husks en stuur de zwerm: de drones duiken van heel hoog "
                + "uit hun baan naar beneden en vallen de doelwitten aan.", range, 180, 0, s -> {
            markAll(s);
            s.action(5, EdithAction.LAUNCH_DRONES)
                    .look(20, 180, -45)
                    .pause(100)
                    .lookAt(0, mob(TARGETS.get(1)))
                    .pause(220);
        });

        add("Single drone (L)", "Eén drone op één doelwit: goedkoper dan een zwerm, en hij jaagt "
                + "tot het doelwit dood is.", range, 180, 0, s -> s
                .lookAt(10, mob(TARGETS.get(0)))
                .action(5, EdithAction.DRONE_STRIKE)
                .pause(300));

        add("Escort drones", "Drones die om je heen vliegen en je beschermen, tot je ze uitzet.",
                start, 180, -10, s -> s
                .action(10, EdithAction.TOGGLE_GUARD_DRONES)
                .pause(140)
                .look(0, 90, -15)
                .pause(80)
                .look(0, 0, -15)
                .pause(80)
                .action(0, EdithAction.TOGGLE_GUARD_DRONES)
                .pause(60));

        add("Drone formations", "Je escorte in een vorm (G > Drones > Formation), van buitenaf bekeken. Eerst "
                + "Orbit (rondcirkelen, zoals altijd), dan Shield (een snel draaiende ring dicht om je heen: wat "
                + "op je af komt, raakt eerst een drone), Arrow (een V schuin boven en achter je; kijk hoe hij "
                + "meedraait als je je omdraait) en Circle (een wijde, trage ring). In Shield en Arrow blijven ze "
                + "in formatie als ze schieten.", start, 180, 15, s -> s
                .action(10, EdithAction.TOGGLE_GUARD_DRONES)
                .camera(0, CameraType.THIRD_PERSON_BACK)
                .pause(110)
                .action(0, EdithAction.DRONE_FORMATION)
                .pause(90)
                .action(0, EdithAction.DRONE_FORMATION)
                .pause(50)
                .look(0, 90, 15)
                .pause(50)
                .look(0, 180, 15)
                .pause(40)
                .action(0, EdithAction.DRONE_FORMATION)
                .pause(100)
                .action(0, EdithAction.DRONE_FORMATION)
                .camera(0, CameraType.FIRST_PERSON)
                .action(10, EdithAction.TOGGLE_GUARD_DRONES)
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
            s.action(5, EdithAction.LAUNCH_DRONES)
                    .pause(220)
                    .action(0, EdithAction.STAND_DOWN)
                    .look(10, 180, -40)
                    .pause(140);
        });

        add("Satellite: precision (K)", "De satelliet: eerst een rode richtstraal en een ring op de "
                + "grond, dan valt er een echte raket uit de ruimte (je hoort hem aankomen) die precies op nul "
                + "inslaat. Geen standen meer: K vuurt meteen. Richt op de verre husk.", range, 180, 0, s -> s
                .onServer(0, ShowcaseFeatures::unlockSatelliteModes)
                .lookAt(10, mob(FAR_TARGET))
                .action(10, EdithAction.STRIKE_PRECISION)
                .pause(220));

        add("Satellite: bombardment (P)", "Een eigen toets, P: een regen van raketten rond het doel, "
                + "elk met een krater.", range, 180, 0, s -> s
                .onServer(0, ShowcaseFeatures::unlockSatelliteModes)
                .lookAt(10, mob(TARGETS.get(1)))
                .action(10, EdithAction.STRIKE_BOMBARDMENT)
                .pause(260));

        add("Satellite: sweep (C)", "De schuivende laser op C, met een zelfgekozen begin en eind: kijk naar de "
                + "linker husk en druk C (het beginpunt krijgt een markering), kijk naar de rechter husk en druk "
                + "nog eens C. Na het opladen trekt de laser van links naar rechts over alle drie.",
                range, 180, 0, s -> s
                .onServer(0, ShowcaseFeatures::unlockSatelliteModes)
                .lookAt(10, mob(TARGETS.get(0)))
                .action(10, EdithAction.STRIKE_SWEEP)
                .pause(30)
                .lookAt(20, mob(TARGETS.get(2)))
                .action(10, EdithAction.STRIKE_SWEEP)
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
                .action(10, EdithAction.DRONE_CAMERA)
                .pause(120)
                .key(0, key(() -> Minecraft.getInstance().options.keyUp), true)
                .key(60, () -> Minecraft.getInstance().options.keyJump, true)
                .key(30, () -> Minecraft.getInstance().options.keyJump, false)
                .key(40, () -> Minecraft.getInstance().options.keyUp, false)
                .pause(40)
                .action(0, EdithAction.DRONE_CAMERA)
                .pause(60));

        add("Spy drone (.)", "Een spionagedrone volgt Happy in het geheim. Links zie je waar hij is; "
                + "daarna kijk je even live mee en krijg je het rapport.", new Vec3(0.5, -60, -2.5), 180, 0, s -> s
                .lookAt(10, eyes(FRIEND_SPOT))
                .action(10, EdithAction.SPY_DRONE)
                .pause(220)
                .action(0, EdithAction.SPY_VIEW)
                .pause(120)
                .action(0, EdithAction.SPY_VIEW)
                .action(10, EdithAction.SPY_REPORT)
                .pause(140)
                .close(0)
                .action(10, EdithAction.SPY_DRONE)
                .pause(40));

        add("Hack a player (;)", "Hack Happy: zijn privéberichten (en de antwoorden) verschijnen "
                + "boven zijn hoofd, zolang jij naar hem kijkt.", new Vec3(2.5, -60, -3.5), 180, 0, s -> s
                .lookAt(10, eyes(FRIEND_SPOT))
                .action(10, EdithAction.HACK_PLAYER)
                .onServer(40, player -> friendSays(player, "msg " + player.getGameProfile().getName()
                        + " Did you see the tower?"))
                .onServer(60, player -> player.getServer().getCommands().performPrefixedCommand(
                        player.createCommandSourceStack(), "msg " + ShowcaseScene.FRIEND + " On my way."))
                .onServer(60, player -> friendSays(player, "msg " + player.getGameProfile().getName()
                        + " Bring diamonds."))
                .pause(120)
                .action(0, EdithAction.HACK_STOP)
                .pause(20));

        add("Comms and pings (O, U)", "Het comms-scherm, dan een E.D.I.T.H.-bericht van Happy, en "
                + "een ping op de grond die iedereen met een bril ziet.", start, 180, 0, s -> s
                .open(10, CommsScreen::new)
                .close(70)
                .onServer(10, player -> {
                    ServerPlayer friend = ShowcaseScene.friend(player);
                    if (friend != null) {
                        EdithComms.handleMessage(friend, new CommsPayload(player.getGameProfile().getName(),
                                "Meet me at the tower."));
                    }
                })
                .look(60, 180, 35)
                .step(10, EdithClientEvents::placePing)
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
                .action(10, EdithAction.TRANSPORT_RIDE)
                .pause(180)
                .key(0, () -> Minecraft.getInstance().options.keyUp, true)
                .look(40, -45, 5)
                .key(60, () -> Minecraft.getInstance().options.keyUp, false)
                .action(20, EdithAction.TRANSPORT_RIDE)
                .pause(140));

        add("Autopilot", "Navigeer naar 'Home' en laat de drones je er zelf heen brengen.",
                GameType.SURVIVAL, start, -45, 0, s -> s
                .step(5, () -> navigate(0))
                .action(10, EdithAction.TRANSPORT_AUTOPILOT)
                .pause(500));

        add("Cargo drone", "Kijk naar de kist: een vrachtdrone haalt de spullen op en brengt ze naar "
                + "de kist bij 'Home'.", start, 180, 0, s -> s
                .step(5, () -> navigate(0))
                .lookAt(10, Vec3.atCenterOf(CHEST))
                .action(10, EdithAction.CARGO_DRONE)
                .pause(120)
                .lookAt(0, Vec3.atCenterOf(HOME))
                .pause(260));

        add("Carry a creature", "Kijk naar de koe: drones pakken haar op en zetten haar bij 'Home' neer.",
                start, 180, 0, s -> s
                .step(5, () -> navigate(0))
                .lookAt(10, mob(COW))
                .action(10, EdithAction.CARRY_ENTITY)
                .pause(140)
                .lookAt(0, Vec3.atCenterOf(HOME))
                .pause(280));

        // ---------------------------------------------------------------- Tech
        add("Redstone relay (')", "Het relaisscherm, dan zet E.D.I.T.H. de lamp op afstand aan en uit "
                + "(ook met je stem: 'turn on the lamp').", new Vec3(3.5, -60, 2.5), 0, 20, s -> s
                .open(10, RelayListScreen::new)
                .close(80)
                .lookAt(5, Vec3.atCenterOf(LAMP))
                .step(20, () -> relay(EdithRedstone.Op.TOGGLE))
                .step(40, () -> relay(EdithRedstone.Op.TOGGLE))
                .step(40, () -> relay(EdithRedstone.Op.TOGGLE))
                .step(40, () -> relay(EdithRedstone.Op.PULSE))
                .pause(60)
                .step(0, () -> relay(EdithRedstone.Op.OFF)));

        add("Relay: name and group", "Een relais een naam en een groep geven (rechtsklik op het relais, ook zonder "
                + "bril, of 'rename' in het relaisscherm). De knop 'Group' toont waar het in zit ('No group' als het "
                + "nergens in zit); klik erop om een groep te kiezen. Alles in dezelfde groep zet je met één commando "
                + "tegelijk aan of uit.", new Vec3(3.5, -60, 2.5), 0, 20, s -> s
                .open(10, () -> new RelayEditScreen(0))
                .pause(150)
                .close(0));

        add("Relay groups, invert and emitter", "13 en 14 oktober. Drie relais met een redstonelamp erachter: "
                + "'Door 1' en 'Door 2' in de groep 'security room' (in 'restricted', in 'base', jouw voorbeeld), en "
                + "'Garden light' in 'garden', omgekeerd (Invert, rode rand om zijn lampje): zijn lamp brandt terwijl "
                + "het relais UIT staat. Rechts een relay emitter met een hendel. De hendel gaat aan: de emitter (op "
                + "'base', Follow) zet beide deuren aan, en weer uit. Dan het redstone-scherm als boom (base > "
                + "restricted > security room > deuren), het groepen-scherm (links de boom, rechts het vinkje bij "
                + "'restricted'; 'base' is grijs, want 'restricted' zit al in 'base'), het scherm van deur 1 met de "
                + "groepskiezer, het scherm van de tuinlamp (Invert: ON), het scherm van de emitter, en tot slot de "
                + "Stark OS-desktop met de app Groups (dezelfde boom, met de relais erin; slepen zet iets in een groep).",
                at(RELAY_YARD.offset(3, 0, 6)), 180, 30, s -> s
                .onServer(0, ShowcaseFeatures::buildRelayYard)
                .pause(60)
                .onServer(0, player -> relayLever(player, true))
                .pause(70)
                .onServer(0, player -> relayLever(player, false))
                .pause(50)
                .open(0, RelayListScreen::new)
                .pause(100)
                .open(0, com.senne.starktech.client.RelayGroupsScreen::new)
                .step(5, () -> {
                    if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.RelayGroupsScreen screen) {
                        screen.tourSelect("security room");
                    }
                })
                .pause(110)
                .close(0)
                .open(0, () -> {
                    RelayEditScreen door = new RelayEditScreen(Minecraft.getInstance().player.getData(ModAttachments.RELAYS)
                            .indexAt(Minecraft.getInstance().level.dimension(), RELAY_YARD.offset(1, 0, 0)));
                    return new com.senne.starktech.client.RelayGroupsScreen(door, "security room");
                })
                .pause(90)
                .close(0)
                .open(0, () -> new RelayEditScreen(Minecraft.getInstance().player.getData(ModAttachments.RELAYS)
                        .indexAt(Minecraft.getInstance().level.dimension(), RELAY_YARD.offset(5, 0, 0))))
                .pause(90)
                .close(0)
                .onServer(0, player -> com.senne.starktech.redstone.RelayEmitters.open(player, RELAY_YARD.offset(6, 0, 2)))
                .pause(100)
                .close(0)
                .stand(0, RELAY_YARD.getX() + 0.5, RELAY_YARD.getY(), RELAY_YARD.getZ() + 5.5, 180, 30)
                .onServer(5, player -> com.senne.starktech.security.os.StarkOs.open(player, RELAY_YARD.offset(0, 0, 3)))
                .step(10, () -> desktop(os -> os.tourGroups("restricted")))
                .pause(140)
                .close(0)
                .stand(0, RELAY_YARD.getX() + 3.5, RELAY_YARD.getY(), RELAY_YARD.getZ() + 2.5, 180, 65)
                .pause(60));

        add("Rayshield: personal shield", "Een energieschild om je heen (van buitenaf bekeken). Dan komen er "
                + "drie pijlen van opzij op je af: het schild vangt ze op (blauwe flits en een tik) en elke "
                + "treffer kost wat energie van de bril (kijk links boven).", GameType.SURVIVAL,
                start, 180, 0, s -> s
                .action(10, EdithAction.TOGGLE_SHIELD)
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
                .action(0, EdithAction.TOGGLE_SHIELD));

        add("Rayshield: laser gate", "Een laserzender (vol blok met een lens): met redstone schuift de "
                + "energiewand uit de lens naar de overkant, altijd van één kant (niet meer van twee kanten naar "
                + "het midden). Zonder redstone schuift hij terug de zender in. Een tweede zender aan de "
                + "overkant is niet nodig. Hij draait op stroom van de warmtegenerator ernaast; zonder stroom "
                + "gaat hij niet aan. Sneak + rechtsklik op een zender = zijn instelscherm (de volgende functie).",
                new Vec3(13.5, -60, 4.5), 0, 5, s -> s
                .onServer(20, player -> player.serverLevel().setBlockAndUpdate(LASER_POWER, Blocks.REDSTONE_BLOCK.defaultBlockState()))
                .pause(120)
                .onServer(0, player -> player.serverLevel().setBlockAndUpdate(LASER_POWER, Blocks.AIR.defaultBlockState()))
                .pause(80));

        add("Rayshield: laser settings", "13 oktober. Gewoon rechtsklikken op een zender doet niets meer (zo leg je "
                + "makkelijk een kabel); sneak + rechtsklik met een lege hand opent zijn scherm, alleen voor wie hem "
                + "neerzette of wie vertrouwd is in de zone. Rood; dan Inverted: zonder redstone schuift de wand er "
                + "nu uit; dan Horizontal: de wand ligt plat. Weer Normal: de wand schuift terug.",
                // Dichterbij dan bij de vorige functie: het scherm werkt tot 6 blokken van de zender.
                new Vec3(13.5, -60, 7.5), 0, 5, s -> s
                .onServer(0, player -> {
                    if (player.serverLevel().getBlockEntity(LASER_A) instanceof com.senne.starktech.shield.LaserEmitterBlockEntity emitter) {
                        emitter.setOwner(player);
                    }
                    com.senne.starktech.shield.LaserEmitters.open(player, LASER_A);
                })
                .pause(40)
                .onServer(0, player -> laser(player, LaserActionPayload.Op.COLOR, ShieldColor.RED.ordinal()))
                .pause(40)
                .onServer(0, player -> laser(player, LaserActionPayload.Op.INVERT, 1))
                .pause(80)
                .onServer(0, player -> laser(player, LaserActionPayload.Op.TURN, 1))
                .pause(80)
                .onServer(0, player -> laser(player, LaserActionPayload.Op.INVERT, 0))
                .pause(60)
                .close(0)
                .onServer(0, player -> {
                    laser(player, LaserActionPayload.Op.TURN, 0);
                    laser(player, LaserActionPayload.Op.COLOR, ShieldColor.BLUE.ordinal());
                }));

        add("Rayshield: dome", "De schildkoepel gaat aan: eerst een straal omhoog, dan vouwt hij bovenin open "
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
                .action(20, EdithAction.ILLUSION_WALL)
                .key(20, () -> Minecraft.getInstance().options.keyJump, true)
                .key(12, () -> Minecraft.getInstance().options.keyJump, false)
                .look(5, 160, 22)
                .look(40, 200, 22)
                .look(40, 180, 25)
                .pause(30)
                .step(0, () -> placeIllusion(true))
                .pause(160)
                .action(0, EdithAction.ILLUSION_STOP));

        add("Illusion: army", "Een nep-leger (zombies, of wat er in het spawn-ei in je hand zit). Ook "
                + "richten met de drone: kijk naar de piramide, dan staan de zombies op de treden (niet "
                + "in de grond). Dan gaan ze op Happy af: ze lopen de piramide af, gaan in een kring om hem "
                + "heen staan en slaan (armen vooruit, een tik), maar zonder schade: het is alleen beeld. Zo "
                + "leid je iemand af.", start, -90, 5, s -> s
                .action(10, EdithAction.ILLUSION_ARMY)
                .key(20, () -> Minecraft.getInstance().options.keyJump, true)
                .key(20, () -> Minecraft.getInstance().options.keyJump, false)
                .lookAt(5, Vec3.atCenterOf(PYRAMID))
                .pause(60)
                .step(0, () -> placeIllusion(true))
                .pause(80)
                .lookAt(20, eyes(FRIEND_SPOT))
                .pause(180)
                .action(0, EdithAction.ILLUSION_STOP));

        add("Illusion: cloak", "Alles in het veld verdwijnt uit beeld: kijk naar de koe en Happy.",
                start, 180, 5, s -> s
                .lookAt(5, mob(COW))
                .action(10, EdithAction.ILLUSION_CLOAK)
                .pause(200)
                .action(0, EdithAction.ILLUSION_STOP));

        add("E.M.P.", "Eerst een valse muur, dan een E.M.P.-puls: alle illusies in de buurt vallen weg.",
                start, 180, 12, s -> s
                .action(10, EdithAction.ILLUSION_WALL)
                .step(30, () -> placeIllusion(true))
                .onServer(0, player -> player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.EMP.get())))
                .pause(140)
                .step(0, ShowcaseFeatures::useItem)
                .pause(120));

        // ---------------------------------------------------------------- Het harnas
        add("Stark suit", "Het harnas: borstplaat, broek en laarzen (rood en goud). Een eigen 3D-model: "
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

        add("Repulsor (I, then attack)", "Toets I zet de repulsor aan (geluid + ring rond het vizier): "
                + "je arm gaat omhoog, de hand geknikt met een zacht gloeiende handpalm. Linkermuisknop "
                + "vuurt: de palm flitst, je arm slaat terug, en er schiet een zelf getekende straal (witte "
                + "kern, blauwe gloed, drukgolfringen bij de hand, flits en ring bij de inslag). Na elk schot "
                + "laadt de ring weer op. Op het eind van buitenaf (F5): de arm vooruit en de palm gloeit.",
                range, 180, 0, s -> s
                .onServer(0, ShowcaseFeatures::wearSuit)
                .lookAt(20, mob(TARGETS.get(1)))
                .step(5, RepulsorControl::toggle)
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
                .step(10, RepulsorControl::toggle)
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
                .step(5, EdithClientEvents::markLookTarget)
                .lookAt(20, mob(TARGETS.get(2)))
                .step(5, EdithClientEvents::markLookTarget)
                .look(15, 180, -5)
                .camera(0, CameraType.THIRD_PERSON_BACK)
                .step(10, () -> KeyMapping.click(EdithKeys.ROCKETS.getKey()))
                .pause(70)
                .camera(0, CameraType.FIRST_PERSON)
                .pause(30));

        add("Suit workbench hologram", "Tony's lab: boven de harnas-werkbank zweeft het harnas als hologram "
                + "(de werkbank heeft stroom van de warmtegenerator ernaast). Er is geen scherm meer. Kijk naar een "
                + "stuk: het licht op, en naast je vizier staat wat een klik doet en wat het kost (groen = genoeg bij "
                + "je, rood = te weinig). Rechtsklik op de borstplaat: de materialen gaan uit je rugzak, de borstplaat "
                + "bouwt zich plaatje voor plaatje op in het hologram en vliegt dan naar je toe (hij gaat meteen aan). "
                + "Daarna laarzen in je hand + klik = ze liggen op het hologram, en een Thruster Upgrade in je hand + "
                + "klik op de laarzen = inbouwen (de ring loopt vol).", GameType.SURVIVAL, start, 180, 20, s -> {
            BlockPos bench = ShowcaseScene.START.offset(0, 0, -3);
            Vec3 chest = new Vec3(0.5, -57.8, -2.5);
            Vec3 boots = new Vec3(0.5, -58.8, -2.5);
            Runnable click = () -> KeyMapping.click(Minecraft.getInstance().options.keyUse.getKey());
            s.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(bench, com.senne.starktech.registry.ModBlocks.SUIT_WORKBENCH.get().defaultBlockState());
                level.setBlockAndUpdate(bench.east(), com.senne.starktech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                if (level.getBlockEntity(bench.east()) instanceof com.senne.starktech.station.ChargerBlockEntity generator) {
                    generator.fill(20_000);
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
                player.getInventory().setItem(3, new ItemStack(ModItems.STARK_CHIP.get(), 4));
                player.getInventory().selected = 0;
            })
                    .lookAt(40, chest)
                    .step(40, click)
                    .camera(150, CameraType.THIRD_PERSON_BACK)
                    .pause(90)
                    .camera(0, CameraType.FIRST_PERSON)
                    .onServer(0, player -> player.getInventory().setItem(0, new ItemStack(ModItems.STARK_BOOTS.get())))
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
                .onServer(0, player -> player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ModItems.STARK_DRONE.get())))
                .step(15, ShowcaseFeatures::useItem)
                .look(10, 180, -60)
                .pause(160));

        add("Charging station", "Het laadstation: bril laden en upgraden. Stroom komt uit redstone of "
                + "van het net (de kabel eronder), maar alleen als er een bril in ligt die nog niet vol is.",
                new Vec3(-2.5, -60, 3.5), 0, 20, s -> s
                .step(10, () -> rightClick(CHARGING_STATION))
                .pause(120)
                .close(0));

        add("Glasses upgrades", "In het laadstation een bril upgraden: een tweede bril (bijna leeg), redstone "
                + "als stroom en drie batterij-upgrades. De bril laadt op, de voortgangsbalk van de upgrade "
                + "loopt vol, 'ding', en de volgende gaat erin (3 seconden per stuk). Er zijn drie soorten: "
                + "batterij (meer energie), scanner (grotere ertsscan) en drones (meer drones in de zwerm).",
                new Vec3(-2.5, -60, 3.5), 0, 20, s -> s
                .onServer(0, ShowcaseFeatures::fillStation)
                .step(10, () -> rightClick(CHARGING_STATION))
                .pause(230)
                .close(0));

        add("Solar panel and heat generator", "Stroom maken: een zonnepaneel (een schuin paneel op een "
                + "paaltje) en een warmtegenerator op magma (een kast met koelribben, koperen pijpen achter "
                + "en voorin een rooster dat oranje gloeit zolang hij warmte opwekt). Daarna hun schermen.",
                new Vec3(-6, -60, 3.5), 0, 20, s -> s
                .step(10, () -> rightClick(SOLAR_PANEL))
                .close(90)
                .step(10, () -> rightClick(HEAT_GENERATOR))
                .close(90));

        add("Power grid (Stark cables)", "Het stroomnet: de Stark-kabel in de grond verbindt de "
                + "warmtegenerator en het zonnepaneel met het laadstation en de holotafel (kabels sluiten "
                + "vanzelf aan). Dan wordt de kabel doorgeknipt: de holotafel valt uit (alleen een rood "
                + "ringetje). Kabel terug: hij gaat weer aan.", new Vec3(-2.5, -58.5, 1.5), 0, 40, s -> s
                .lookAt(10, new Vec3(-2.5, -61, 6.5))
                .pause(60)
                .onServer(0, player -> player.serverLevel().setBlockAndUpdate(GRID_CUT, Blocks.AIR.defaultBlockState()))
                .lookAt(20, Vec3.atCenterOf(HOLO_TABLE).add(0, 1, 0))
                .pause(80)
                .onServer(0, player -> player.serverLevel().setBlockAndUpdate(GRID_CUT,
                        com.senne.starktech.registry.ModBlocks.CABLE.get().defaultBlockState()))
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
                .onServer(5, player -> com.senne.starktech.light.ArcLamps.open(player, LAMP_YARD.east(7).above(4)))
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
                            instanceof com.senne.starktech.light.ArcLampBlockEntity lamp) {
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
                + "om je heen en schieten. Ze komen vanzelf als je de bril draagt.", GameType.SURVIVAL,
                start, 180, -20, s -> s
                .onServer(10, player -> EdithRaids.startRaid(player, 3))
                .pause(120)
                .look(0, 0, -10)
                .pause(160));

        add("Hydra soldiers", "Nieuwe vijanden: Hydra-soldaten, die straks de Hydra-bunker bewaken. Zwart tactisch "
                + "pak, rode bril, het rode embleem op borst en helm. Zien ze je, dan leggen ze hun lasergeweer aan "
                + "(twee handen) en schieten ze rode laserstralen. Ze schieten ook op je drones, en raak je er "
                + "één, dan komen de anderen ook. Soms laten ze een Hydra-onderdeel vallen.",
                GameType.SURVIVAL, start, 180, 0, s -> s
                .onServer(10, ShowcaseFeatures::spawnHydraSquad)
                .pause(80)
                .camera(0, CameraType.THIRD_PERSON_BACK)
                .pause(80)
                .camera(0, CameraType.FIRST_PERSON)
                .pause(60));

        add("Automatic turrets", "Een Stark-torentje (gemaakt met een Hydra-onderdeel) met een warmtegenerator "
                + "ernaast: de kop kijkt rustig rond tot er monsters komen (eerst misschien een slijmpje), "
                + "draait dan naar het dichtstbijzijnde en blijft daarop schieten tot het neer is: een blauwe "
                + "laser (60 stroom per schot). Er zitten 16 raketten in (Rocket Ammo, met "
                + "rechtsklik of een trechter), dus om de paar seconden gaat er ook een raket in een boogje op "
                + "af. Jou raakt hij nooit. Dan komt er een redstoneblok naast: het torentje staat uit en de "
                + "loop hangt omlaag (E.D.I.T.H. kan hem ook uitzetten, hij staat in je redstone-lijst onder "
                + "Turrets). Rechts een Hydra-torentje uit de bunker: dat schiet rode lasers op jou.",
                GameType.SURVIVAL, at(ShowcaseScene.TURRET.offset(-4, 0, 3)), 200, 10, s -> s
                .onServer(0, ShowcaseScene::buildTurrets)
                .pause(40)
                .onServer(0, ShowcaseScene::turretAttackers)
                .pause(240)
                .onServer(0, player -> player.serverLevel().setBlockAndUpdate(ShowcaseScene.TURRET.west(),
                        net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState()))
                .pause(50)
                .look(0, 150, 10)
                .pause(70));

        add("Raid drones: breaking in", "Verstop je in een dichte hut (stenen muren, glazen dak) en de "
                + "raiddrones zien je niet. Dan komen ze dichterbij en schieten ze zich naar binnen: om de "
                + "anderhalve seconde verdwijnt er een blok (laserstraal en vonken), tot ze je zien. Kijk "
                + "omhoog door het dak. Wie dat slopen niet wil: gamerule starktechRaidGriefing false.",
                GameType.SURVIVAL, at(HUT), 180, -55, s -> s
                .onServer(0, player -> ShowcaseScene.buildHut(player.serverLevel()))
                .command(0, "gamerule starktechRaidGriefing true")
                .onServer(20, player -> EdithRaids.startRaid(player, 3))
                .pause(150)
                .look(0, 0, -35)
                .pause(110)
                .look(0, 90, -10)
                .pause(110)
                .command(0, "gamerule starktechRaidGriefing false"));

        add("Stark Tower", "Het gebouw waar je de bril vindt: rondvliegen om de Stark Tower. Let op het "
                + "STARK-logo van koperen lampen op de gevel, de lampjes langs elk terras, de kluis bovenin "
                + "(donker, maar met hetzelfde ritme als de rest) en de blauwe lichtstraal uit de kroon.",
                new Vec3(STARK_TOWER.getX() - 30, -20, STARK_TOWER.getZ() + 20), -90, 10, s -> {
            Vec3 centre = towerCentre();
            double[][] around = {{-30, 20}, {20, -45}, {70, 20}, {20, 85}};
            for (double[] spot : around) {
                double x = STARK_TOWER.getX() + spot[0];
                double z = STARK_TOWER.getZ() + spot[1];
                double y = -10;
                float yaw = (float) Math.toDegrees(Math.atan2(-(centre.x - x), centre.z - z));
                s.view(0, x, y, z, yaw, 5).pause(80);
            }
            s.view(0, centre.x, 60, centre.z - 60, 0, 30).pause(80);
        });

        add("Stark Tower: laser corridor", "Binnen in de toren: de lasergang naar de kluis (acht rijen "
                + "rayshields). Ze draaien op stroom van een verborgen krachtcentrale een verdieping lager: "
                + "daarna zie je het dichtgemetselde blok in de hoek en de kabels in het plafond. Knip je "
                + "die door, dan gaan de lasers uit.", towerPoint(20.5, 68, 30.2), 180, 0, s -> s
                .pause(120)
                .view(0, STARK_TOWER.getX() + 22.5, STARK_TOWER.getY() + 57, STARK_TOWER.getZ() + 18.5, 180, 0)
                .lookAt(10, towerPoint(27.5, 62, 12.5))
                .pause(80)
                .lookAt(10, towerPoint(21, 66.5, 14))
                .pause(80));

        add("Stark Tower: floors", "Elke verdieping van binnen, van onder naar boven: lobby (receptie met "
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
                s.title(0, names[i], "").view(0, STARK_TOWER.getX() + corner, STARK_TOWER.getY() + floors[i] + 1,
                        STARK_TOWER.getZ() + corner, 135, 12).pause(70);
            }
        });

        add("Stark Tower: vault trap", "De val in de kluis: voor de kluiskist ligt een drukplaat, met in de "
                + "vloer eromheen vier laserzenders die omhoog kijken. Stap je erop, dan schieten er vier rode "
                + "laserzuilen tot aan het plafond: je staat in een kooi. Eruit door een zender uit de vloer "
                + "te hakken, of door de stroom af te snijden (ze hangen aan de krachtcentrale).",
                towerPoint(20.5, 79, 13.5), 0, 15, s -> s
                .pause(60)
                .stand(0, STARK_TOWER.getX() + 20.5, STARK_TOWER.getY() + 79.1, STARK_TOWER.getZ() + 23.5, 0, 10)
                .pause(60)
                .look(30, 0, -55)
                .pause(60)
                .view(20, STARK_TOWER.getX() + 20.5, STARK_TOWER.getY() + 80, STARK_TOWER.getZ() + 15.5, 0, 25)
                .pause(80));

        add("Stark Tower: security and hack", "De beveiliging van de toren, in survival. Je staat op de "
                + "beveiligingsverdieping naast de Stark Security Core (de serverkast met het rode lampje, in de rij "
                + "serverkasten). De kern stuurt vier beveiligingsdrones met rode lampjes de lucht in, en die en de "
                + "beveiligingstorentjes schieten op je (hier ben je onkwetsbaar). Dan klik je met de Stark Laptop op "
                + "de kern: de laptop staat opengeklapt bovenop de kern, het alarm gaat af en de terminal gaat open "
                + "(hier speelt hij zichzelf): de open poort zoeken, het wachtwoord raden, Tony's README lezen, en in "
                + "de console van het beveiligingsprogramma de waakhond uit en jezelf eigenaar. Halverwege gaat het "
                + "scherm even dicht (Esc) en weer open: je gaat verder waar je was. Daarna is de toren van jou: het "
                + "lampje wordt groen, de drones worden blauw en laten je met rust, en je krijgt de laptop terug.", GameType.SURVIVAL, towerPoint(12.5, 57, 20.5), 90, 12, s -> {
            BlockPos core = STARK_TOWER.offset(10, 57, 20);
            s.onServer(0, player -> {
                if (player.serverLevel().getBlockEntity(core) instanceof com.senne.starktech.security.SecurityCoreBlockEntity be) {
                    be.clearOwner();
                }
                // Een laptop van een eerdere keer eraf, zodat de hack echt opnieuw begint.
                player.serverLevel().removeBlock(core.above(), false);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(ModItems.STARK_LAPTOP.get()));
            })
                    .pause(180)
                    .step(0, com.senne.starktech.client.HackScreen::startAutoPlay)
                    .onServer(0, player -> com.senne.starktech.security.TowerHack.placeLaptop(player, core,
                            player.getMainHandItem()))
                    .pause(260)
                    .close(40)
                    .onServer(0, player -> com.senne.starktech.security.TowerHack.open(player, core.above()))
                    .pause(700)
                    .step(0, () -> com.senne.starktech.client.HackScreen.autoPlay = false)
                    .pause(80);
        });

        add("Stark OS desktop", "De Stark OS-desktop op de beveiligingskern (hier is de toren van jou). "
                + "Rechtsklik met een lege hand op de kern opent hem: sleepbare vensters, icoontjes links en een "
                + "taakbalk. Achter elkaar: Files met de map /etc (security.conf is een systeembestand: SYS, "
                + "verplaatsen en hernoemen mag, weggooien niet), Tony's map met het rechtsklik-menu (Open, "
                + "Rename, Delete, Mark as trap), zijn notitie in Notes, dan Devices (de torentjes, drones, "
                + "laserpoorten en camera's van de toren, met hun status en schakelaars), Access (monsters en "
                + "spelers aanvallen, vertrouwde spelers) en Logs (wat er op het systeem gebeurde).",
                towerPoint(12.5, 57, 20.5), 90, 12, s -> {
            BlockPos core = STARK_TOWER.offset(10, 57, 20);
            s.onServer(0, player -> {
                claimTowerCore(player, core);
                com.senne.starktech.security.os.StarkOs.open(player, core);
            })
                    .pause(60)
                    .step(0, () -> desktop(os -> os.tourFiles("/etc", "/etc/security.conf", false)))
                    .pause(90)
                    .step(0, () -> desktop(os -> os.tourFiles("/home/tony", "/home/tony/notes.txt", true)))
                    .pause(80)
                    .onServer(0, player -> {
                        net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
                        args.putString("path", "/home/tony/notes.txt");
                        com.senne.starktech.security.os.StarkOs.handleAction(player,
                                new com.senne.starktech.network.OsActionPayload(core, "fs_open", args));
                    })
                    .pause(90)
                    .step(0, () -> desktop(os -> os.tourOpen(com.senne.starktech.client.os.StarkOsScreen.App.DEVICES)))
                    .pause(110)
                    .step(0, () -> desktop(os -> os.tourOpen(com.senne.starktech.client.os.StarkOsScreen.App.ACCESS)))
                    .pause(80)
                    .step(0, () -> desktop(os -> os.tourOpen(com.senne.starktech.client.os.StarkOsScreen.App.LOGS)))
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
            BlockPos core = STARK_TOWER.offset(10, 57, 20);
            BlockPos lobby = STARK_TOWER.offset(25, 7, 14);
            BlockPos vault = STARK_TOWER.offset(20, 82, 10);
            s.onServer(0, player -> {
                claimTowerCore(player, core);
                networkSwitch(player, core, "cameras", true);
                com.senne.starktech.security.os.StarkOs.open(player, core);
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
                + "Eerst de kaart (niveau 3) tegen de lezer: oranje, dan groen, en de deur gaat 3 seconden open "
                + "(het diagram doet pulse(\"Lab door lock\", 3)). Dan een kaart die geblokkeerd is: rood. Dan de "
                + "code 4321 op het paneel: de lamp gaat aan. Dan sneak + rechtsklik op de lezer (13 oktober): zijn "
                + "naamscherm, met 'Lab entrance' getypt (eronder de regel voor je diagram, en in oranje dat je die "
                + "ook moet veranderen); Esc laat het zoals het was. Bij het codepaneel werkt dat net zo. Daarna de "
                + "app Logic op de desktop: de code van het "
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
                        Minecraft.getInstance().setScreen(new com.senne.starktech.client.CodePanelScreen(
                                LOGIC_LAB.offset(5, 1, 7), "Lights panel"));
                        if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.CodePanelScreen panel) {
                            panel.tourType("4321");
                        }
                    })
                    .pause(50)
                    .close(0)
                    .onServer(0, player -> {
                        com.senne.starktech.logic.LogicEvents.clear();
                        com.senne.starktech.logic.LogicEvents.codePanel(player.serverLevel(), LOGIC_LAB.offset(5, 1, 7),
                                player, "4321");
                    })
                    .pause(60)
                    // 13 oktober: sneak + rechtsklik op de lezer = zijn naamscherm (jij bent de eigenaar van de kern).
                    // "Lab entrance" getypt; Esc laat het zoals het was, anders vindt het diagram hem niet meer.
                    .onServer(0, player -> com.senne.starktech.logic.InputDevices.open(player, LOGIC_LAB.offset(1, 1, 7)))
                    .step(10, () -> {
                        if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.InputNameScreen screen) {
                            screen.tourType("Lab entrance");
                        }
                    })
                    .pause(90)
                    .close(0)
                    // De racks van dichtbij, en dan de desktop bij de kern.
                    .stand(0, LOGIC_LAB.getX() + 3.5, LOGIC_LAB.getY(), LOGIC_LAB.getZ() + 3.5, 180, 15)
                    .pause(50)
                    .stand(0, LOGIC_LAB.getX() + 0.5, LOGIC_LAB.getY(), LOGIC_LAB.getZ() + 3.5, 180, 20)
                    .onServer(5, player -> com.senne.starktech.security.os.StarkOs.open(player, LOGIC_LAB))
                    .step(10, () -> desktop(os -> os.tourOpen(com.senne.starktech.client.os.StarkOsScreen.App.LOGIC)))
                    .pause(120)
                    .step(0, () -> desktop(os -> os.tourLogicBlocks(null)))
                    .pause(120)
                    .step(0, () -> desktop(os -> os.tourLogicBlocks(com.senne.starktech.logic.program.Op.IF)))
                    .pause(80)
                    .close(20);
        });

        add("Logic: names, sums and loops", "Hoofdstuk 20: de taal groeit. Hetzelfde lab, met een tweede diagram "
                + "'Blink': count = 0 helemaal links (een naam voor het hele programma), en als de lamp aangaat telt "
                + "het één op, zegt E.D.I.T.H. 'The lights went on 1 times' (een berekend bericht: tekst + een getal), "
                + "en gaat de deur drie keer even open (repeat(3) met pulse en wait). Eerst de code 4321 op het "
                + "paneel: de lamp gaat aan, E.D.I.T.H. zegt het, en de deur gaat drie keer open en dicht. Dan de app "
                + "Logic: de code (de nieuwe woorden roze, = en + ook), en als blokjes: AT START en een geel "
                + "SET-blokje, en het roze REPEAT-blokje met zijn uitgangen do (rechtsboven, de stappen erin) en "
                + "after (onderaan); daarna het REPEAT-blokje gekozen (rechts: Times). Het palet rechts scrolt nu "
                + "met het muiswiel (pijltje onderaan).",
                Vec3.atBottomCenterOf(LOGIC_LAB.offset(3, 0, 11)), 180, 10, s -> {
            s.onServer(0, ShowcaseFeatures::buildLogicLab)
                    .onServer(0, ShowcaseFeatures::addBlinkProgram)
                    .lookAt(40, Vec3.atCenterOf(LOGIC_LAB.offset(3, 1, 6)))
                    .pause(30)
                    // De code 4321: "Lab" zet de lamp aan, en "Blink" hoort dat (when turned_on).
                    .onServer(0, player -> {
                        com.senne.starktech.logic.LogicEvents.clear();
                        com.senne.starktech.logic.LogicEvents.codePanel(player.serverLevel(), LOGIC_LAB.offset(5, 1, 7),
                                player, "4321");
                    })
                    .pause(130)
                    .stand(0, LOGIC_LAB.getX() + 0.5, LOGIC_LAB.getY(), LOGIC_LAB.getZ() + 3.5, 180, 20)
                    .onServer(5, player -> com.senne.starktech.security.os.StarkOs.open(player, LOGIC_LAB))
                    .step(10, () -> desktop(os -> os.tourLogic("Blink", BLINK_CODE, false)))
                    .pause(120)
                    .step(0, () -> desktop(os -> os.tourLogicBlocks(null)))
                    .pause(100)
                    .step(0, () -> desktop(os -> os.tourLogicBlocks(com.senne.starktech.logic.program.Op.REPEAT)))
                    .pause(80)
                    .close(20);
        });

        add("The datacenter", "Hoofdstuk 9: het datacenter (in survival). Het lab van hiervoor: elk rack heeft "
                + "zijn eigen temperatuur en dus zijn eigen fan, maar het rack rechts heeft nu RAM, een SSD en vier "
                + "GPU's en geen fan meer. Eerst zijn scherm: zes vakjes in twee kolommen, en onderaan RACK TEMP "
                + "('rising': zonder fan raakt de warmte er niet uit), HEAT/COOL en hoeveel fans erin moeten. Dan is "
                + "dat rack te heet (96 graden): de hele server op halve snelheid, E.D.I.T.H. waarschuwt (met waar "
                + "het rack staat), en de GPU's slijten (een balkje onder het icoon). Daarna vat het rack vlam: vuur, "
                + "rook, het scherm knippert ON FIRE, en na 8 seconden is het weg, met alles erin; de andere twee "
                + "racks blijven koel. Er zat een SSD in: E.D.I.T.H. zegt dat alle diagrammen en keycards weg zijn. "
                + "Dan de desktop: de app Monitor (tegels met de laatste minuut, elk rack met zijn eigen temperatuur, "
                + "wat er bezig is) en de app Backup: er staat een backup op een tweede "
                + "datacenter ver weg (dev-vault), en Restore zet alles terug. Tot slot het scherm AI Brain "
                + "(rechtsboven: op welk datacenter het brein draait, en hoeveel vertraging) en 'night vision on': "
                + "eerst THINKING..., dan doet ze het.",
                GameType.SURVIVAL, Vec3.atBottomCenterOf(LOGIC_LAB.offset(3, 0, 4)), 180, 20, s -> {
            BlockPos hot = LOGIC_LAB.offset(4, 0, 0);
            s.onServer(0, ShowcaseFeatures::buildDatacenter)
                    // Een nieuwe kern meldt zich pas een tick later: daarna pas verbinden en de backup.
                    .onServer(30, ShowcaseFeatures::connectDatacenters)
                    .pause(20)
                    // Het scherm van het rack zonder fan: RACK TEMP "rising", "No fan: ... Add 4 fans!".
                    .onServer(0, player -> openRack(player, hot))
                    .pause(90)
                    .close(0)
                    // Te heet: halve snelheid, waarschuwing, slijten.
                    .onServer(0, player -> heatUp(player, hot, 96))
                    .pause(20)
                    .onServer(0, player -> openRack(player, hot))
                    .pause(90)
                    .close(0)
                    // Brand: het te hete rack vat vlam en is na 8 seconden weg (de andere twee niet).
                    .onServer(0, player -> {
                        if (player.serverLevel().getBlockEntity(hot) instanceof com.senne.starktech.logic.ServerRackBlockEntity rack) {
                            rack.ignite(player.serverLevel(), player.serverLevel().getGameTime() + 160);
                        }
                    })
                    .pause(40)
                    .onServer(0, player -> openRack(player, hot))
                    .pause(50)
                    .close(0)
                    .pause(90)
                    // De desktop: Monitor en Backup.
                    .stand(0, LOGIC_LAB.getX() + 0.5, LOGIC_LAB.getY(), LOGIC_LAB.getZ() + 3.5, 180, 20)
                    .onServer(5, player -> com.senne.starktech.security.os.StarkOs.open(player, LOGIC_LAB))
                    .step(10, () -> desktop(os -> os.tourOpen(com.senne.starktech.client.os.StarkOsScreen.App.MONITOR)))
                    .pause(140)
                    .step(0, () -> desktop(os -> os.tourOpen(com.senne.starktech.client.os.StarkOsScreen.App.BACKUP)))
                    .pause(60)
                    // Restore: de backup van dev-vault zet de diagrammen en keycards terug.
                    .onServer(0, ShowcaseFeatures::restoreLab)
                    .pause(140)
                    .close(10)
                    // Het brein draait op een datacenter: het scherm AI Brain, en dan even nadenken.
                    .open(10, com.senne.starktech.client.BrainScreen::new)
                    .pause(90)
                    .close(0)
                    .step(10, () -> com.senne.starktech.client.voice.VoiceControl.hear("night vision on"))
                    .pause(2)
                    .waitUntil(600, () -> !com.senne.starktech.client.voice.EdithBrain.isThinking())
                    .pause(70);
        });

        add("Protected zones", "Hoofdstuk 10: zones (in survival). Een L-vormige basis met een torentje; de kern binnen "
                + "is van jou. Je ziet de blauwe rand: twee kubussen (waar ze tegen elkaar liggen, geen wand), met een "
                + "raster op elke blokrand. Dan de Zone Marker in je hand: een oranje doos (een schuurtje aan de "
                + "oostkant) en boven elke kubus zijn nummer; die doos wordt kubus 3. Daarna de app Zone op de desktop "
                + "(de kaart van bovenaf, de getallen, per kant - en +). De stroom valt uit: de rand wordt rood "
                + "(niemand beschermt nu), en weer blauw. Dan is de basis van iemand anders (Pepper): jij ziet de rand "
                + "niet meer. Je slaat op de muur: onderaan 'Protected zone' en een piepje, het blok breekt niet. Een "
                + "steen neerzetten: lukt niet. Een ladder: lukt wel (klimmen mag altijd, net als in het echt).",
                GameType.SURVIVAL, Vec3.atBottomCenterOf(ZONE_BASE.offset(-6, 0, -5)), -40, 8, s -> {
            s.onServer(0, ShowcaseFeatures::buildZoneBase)
                    .pause(90)
                    // De Zone Marker: een oranje doos, die kubus 3 wordt.
                    .onServer(0, player -> {
                        ItemStack marker = new ItemStack(ModItems.ZONE_MARKER.get());
                        marker.set(com.senne.starktech.registry.ModDataComponents.ZONE_SELECTION.get(),
                                com.senne.starktech.security.zone.ZoneSelection.EMPTY
                                        .withFirst(ZONE_BASE.offset(12, -1, 2)).withSecond(ZONE_BASE.offset(16, 4, 7)));
                        player.setItemInHand(InteractionHand.MAIN_HAND, marker);
                    })
                    .stand(0, ZONE_BASE.getX() + 22.5, ZONE_BASE.getY(), ZONE_BASE.getZ() - 4.5, 50, 10)
                    .pause(80)
                    .onServer(0, player -> {
                        com.senne.starktech.security.SecurityCoreBlockEntity core = zoneCore(player);
                        if (core != null) {
                            java.util.List<com.senne.starktech.security.zone.ZoneBox> boxes = new java.util.ArrayList<>(core.zoneBoxes());
                            boxes.add(com.senne.starktech.security.zone.ZoneBox.of(ZONE_BASE.offset(12, -1, 2), ZONE_BASE.offset(16, 4, 7)));
                            core.setZoneBoxes(boxes);
                        }
                        player.getMainHandItem().set(com.senne.starktech.registry.ModDataComponents.ZONE_SELECTION.get(),
                                com.senne.starktech.security.zone.ZoneSelection.EMPTY);
                    })
                    .pause(60)
                    // De app Zone op de desktop van de kern.
                    .onServer(0, player -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY))
                    .stand(0, ZONE_BASE.getX() + 6.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 6.5, 90, 20)
                    .onServer(5, player -> com.senne.starktech.security.os.StarkOs.open(player, ZONE_BASE.offset(4, 0, 5)))
                    .step(10, () -> desktop(os -> os.tourZone(2)))
                    .pause(110)
                    .close(10)
                    // Geen stroom: rood. Dan weer stroom: blauw.
                    .stand(0, ZONE_BASE.getX() - 5.5, ZONE_BASE.getY(), ZONE_BASE.getZ() - 4.5, -40, 8)
                    .onServer(0, player -> player.serverLevel().setBlockAndUpdate(ZONE_BASE.offset(4, -1, 5), Blocks.STONE.defaultBlockState()))
                    .pause(90)
                    .onServer(0, ShowcaseFeatures::powerZoneBase)
                    .pause(70)
                    // De basis is van Pepper: jij bent hier een vreemde.
                    .onServer(0, ShowcaseFeatures::giveZoneBaseToPepper)
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

        add("C4", "Hoofdstuk 10: C4 (in survival). Eerst ben jij de eigenaar van de basis: iemand plakt C4 op je muur. "
                + "E.D.I.T.H. zegt waar ('C4 planted at your zone', zonder naam) en je ziet een knipperend rood bordje "
                + "'C4!'. Met een schaar knip je de draad door: hij valt eruit, jij hebt hem nu. Dan ben je de inbreker "
                + "(de basis is van Pepper). Je zet de timer op 0:05 (rechtsklik in de lucht), plakt hem op de muur: hij "
                + "piept steeds sneller, telt af op zijn schermpje, en blaast een gat van drie hoog in de stenen muur, "
                + "met barsten eromheen. Daarna de obsidiaan muur aan de westkant: met remote C4 en de detonator (rode "
                + "bordjes boven je ladingen). Na de eerste en tweede klap staat het obsidiaan er nog (barsten zie je "
                + "bijna niet op obsidiaan), na de derde is er een gat.",
                GameType.SURVIVAL, Vec3.atBottomCenterOf(ZONE_BASE.offset(4, 0, 15)), 180, 5, s -> {
            BlockPos wall = ZONE_BASE.offset(3, 1, 9);
            BlockPos obsidian = ZONE_BASE.offset(2, 1, 5);
            s.onServer(0, ShowcaseFeatures::buildZoneBase)
                    .pause(40)
                    // Iemand plakt C4 op jouw muur: het alarm.
                    .onServer(0, player -> {
                        ServerLevel level = player.serverLevel();
                        BlockPos planted = wall.east().south();
                        level.setBlockAndUpdate(planted, ModBlocks.TIMED_C4.get().defaultBlockState()
                                .setValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACING, Direction.SOUTH));
                        if (level.getBlockEntity(planted) instanceof com.senne.starktech.c4.C4BlockEntity c4) {
                            c4.detonateIn(60 * 20);
                            com.senne.starktech.c4.C4Alarm.planted(level, planted, c4);
                        }
                    })
                    .pause(80)
                    // Jij knipt hem door.
                    .onServer(0, player -> player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS)))
                    .stand(0, ZONE_BASE.getX() + 4.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 12.5, 180, 3)
                    .pause(30)
                    .tap(0, () -> Minecraft.getInstance().options.keyUse)
                    .pause(60)
                    // Nu ben jij de inbreker: de basis is van Pepper.
                    .onServer(0, ShowcaseFeatures::giveZoneBaseToPepper)
                    .onServer(0, player -> {
                        ItemStack c4 = new ItemStack(ModItems.TIMED_C4.get());
                        c4.set(com.senne.starktech.registry.ModDataComponents.C4_TIMER.get(), 5);
                        player.setItemInHand(InteractionHand.MAIN_HAND, c4);
                    })
                    .step(10, () -> com.senne.starktech.client.c4.C4TimerScreen.open(InteractionHand.MAIN_HAND))
                    .pause(50)
                    .close(0)
                    .stand(0, ZONE_BASE.getX() + 4.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 12.5, 180, 0)
                    .pause(20)
                    .tap(0, () -> Minecraft.getInstance().options.keyUse)
                    .pause(30)
                    // Achteruit, buiten de klap.
                    .stand(0, ZONE_BASE.getX() + 4.5, ZONE_BASE.getY(), ZONE_BASE.getZ() + 21.5, 180, 3)
                    .pause(120)
                    // De obsidiaan muur: drie keer een remote C4 en de detonator.
                    .onServer(0, player -> player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.DETONATOR.get())))
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
                    .pause(90)
                    .onServer(0, player -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY));
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

        add("Hacking and pickpocketing", "Hoofdstuk 12: inbreken (in survival). In het lab van hiervoor, met de "
                + "Bypass Kit in je hand. Eerst de keycardlezer: je kiest een kaart van niveau 3, en draait de stukjes "
                + "draad tot het blauwe signaal van CARD naar ACCEPT loopt (raakt het even een ALARM, dan knippert "
                + "alles rood). Het pad wordt groen, de lezer leest een nagemaakte kaart, en de deur gaat 3 seconden "
                + "open. Dan het codepaneel: je veegt poeder over de toetsen (lichtgele afdrukken op 1, 2, 3 en 4) en "
                + "raadt de volgorde; rechts zie je hoeveel er goed stonden en hoeveel volgordes er nog kunnen. Bij "
                + "de vierde poging: CODE CRACKED 4321, en de lamp gaat aan. Dan zakkenrollen bij Happy: je sluipt "
                + "achter hem, en in zijn zak liggen drie kaarten tussen rommel. Je schuift de rommel opzij en trekt "
                + "een kaart naar boven (de meter NOTICED loopt wat op): hij draait om en je ziet welke het is, en hij "
                + "zit in je hotbar. De tweede keer draait Happy zich om terwijl jij in zijn zak zit: CAUGHT!, en jij "
                + "licht op.",
                GameType.SURVIVAL, Vec3.atBottomCenterOf(LOGIC_LAB.offset(3, 0, 11)), 180, 10, s -> {
            BlockPos reader = LOGIC_LAB.offset(1, 1, 7);
            BlockPos panel = LOGIC_LAB.offset(5, 1, 7);
            s.onServer(0, ShowcaseFeatures::buildLogicLab)
                    .onServer(5, player -> {
                        wakeLabServer(player);
                        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BYPASS_KIT.get()));
                    })
                    // De keycardlezer: het stroomcircuit speelt zichzelf (niveau 3), de deur gaat open.
                    .stand(0, LOGIC_LAB.getX() + 1.5, LOGIC_LAB.getY(), LOGIC_LAB.getZ() + 9.5, 180, 15)
                    .lookAt(10, Vec3.atCenterOf(reader))
                    .pause(30)
                    .step(0, () -> com.senne.starktech.client.bypass.CircuitScreen.autoPlay = true)
                    .onServer(0, player -> com.senne.starktech.bypass.Bypass.start(player, reader, InteractionHand.MAIN_HAND))
                    .pause(250)
                    .step(0, () -> com.senne.starktech.client.bypass.CircuitScreen.autoPlay = false)
                    .close(0)
                    .pause(30)
                    // Het codepaneel: stoffen, de volgorde raden, en de lamp gaat aan.
                    .stand(0, LOGIC_LAB.getX() + 5.5, LOGIC_LAB.getY(), LOGIC_LAB.getZ() + 9.5, 180, 15)
                    .lookAt(10, Vec3.atCenterOf(panel))
                    .pause(20)
                    .step(0, () -> com.senne.starktech.client.bypass.FingerprintScreen.autoPlay = true)
                    .onServer(0, player -> com.senne.starktech.bypass.Bypass.start(player, panel, InteractionHand.MAIN_HAND))
                    .pause(310)
                    .step(0, () -> com.senne.starktech.client.bypass.FingerprintScreen.autoPlay = false)
                    .close(0)
                    .pause(40)
                    // Zakkenrollen bij Happy: sluipend achter hem, rommel opzij, een kaart eruit.
                    .onServer(0, ShowcaseFeatures::fillHappysPockets)
                    .stand(0, FRIEND_SPOT.getX() + 0.5, FRIEND_SPOT.getY(), FRIEND_SPOT.getZ() + 1.9, 180, 25)
                    .key(10, () -> Minecraft.getInstance().options.keyShift, true)
                    .pause(20)
                    .step(0, () -> com.senne.starktech.client.pickpocket.PocketScreen.autoPlay = true)
                    .onServer(0, ShowcaseFeatures::pickHappysPocket)
                    .pause(200)
                    .step(0, () -> com.senne.starktech.client.pickpocket.PocketScreen.autoPlay = false)
                    .close(0)
                    // Nog een keer, maar nu draait Happy zich om: betrapt.
                    .onServer(30, player -> {
                        com.senne.starktech.pickpocket.Pickpocket.clear();
                        pickHappysPocket(player);
                    })
                    .pause(50)
                    .onServer(0, player -> turnHappy(player, 0))
                    .pause(90)
                    .close(0)
                    .key(0, () -> Minecraft.getInstance().options.keyShift, false)
                    .onServer(40, player -> turnHappy(player, 180));
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
                + "desktop: beide robots met hun toestand en batterij.",
                Vec3.atBottomCenterOf(ROBOT_BAY.offset(5, 0, 9)), 180, 12, s -> {
            s.onServer(0, ShowcaseFeatures::buildRobotBay)
                    .pause(30)
                    // De assembler, vol: eerst kijken, dan Assemble.
                    .onServer(0, player -> {
                        if (player.serverLevel().getBlockEntity(ROBOT_BAY.offset(2, 0, 2))
                                instanceof com.senne.starktech.robot.RobotAssemblerBlockEntity be) {
                            player.openMenu(be, ROBOT_BAY.offset(2, 0, 2));
                        }
                    })
                    .pause(90)
                    .onServer(0, player -> {
                        if (player.serverLevel().getBlockEntity(ROBOT_BAY.offset(2, 0, 2))
                                instanceof com.senne.starktech.robot.RobotAssemblerBlockEntity be) {
                            be.assemble();
                        }
                    })
                    .pause(60)
                    .close(10)
                    // Guard 1 op het linker dock, de Commando (halfvol) op het rechter.
                    .onServer(0, player -> {
                        if (player.serverLevel().getBlockEntity(ROBOT_BAY.offset(2, 0, 2))
                                instanceof com.senne.starktech.robot.RobotAssemblerBlockEntity be) {
                            ItemStack built = be.items().getItem(com.senne.starktech.robot.RobotAssemblerBlockEntity.ROBOT).copy();
                            be.items().setItem(com.senne.starktech.robot.RobotAssemblerBlockEntity.ROBOT, ItemStack.EMPTY);
                            placeRobot(player, ROBOT_BAY.offset(6, 0, 2), built);
                        }
                        com.senne.starktech.robot.RobotStyle c = com.senne.starktech.robot.RobotStyle.COMMANDO;
                        placeRobot(player, ROBOT_BAY.offset(8, 0, 2), com.senne.starktech.robot.GuardRobotItem.create(
                                new com.senne.starktech.robot.RobotData(new com.senne.starktech.robot.RobotParts(c, c, c, c).pack(),
                                        com.senne.starktech.bot.BotBoard.EMPTY, "", 0.4f)));
                    })
                    .pause(80)
                    // Aan: hij stapt uit zijn dock.
                    .onServer(0, player -> robotAt(player, ROBOT_BAY.offset(6, 0, 2), robot -> robot.setActive(true)))
                    .pause(80)
                    .onServer(0, player -> robotNear(player, ROBOT_BAY.offset(6, 0, 4),
                            robot -> com.senne.starktech.robot.Robots.open(player, robot)))
                    .pause(110)
                    .close(10)
                    // Uit: hij loopt terug en gaat in zijn dock staan.
                    .onServer(0, player -> robotNear(player, ROBOT_BAY.offset(6, 0, 4), robot -> robot.setActive(false)))
                    .pause(100)
                    // De app Robots.
                    .stand(0, ROBOT_BAY.getX() + 10.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 8.5, 180, 20)
                    .onServer(5, player -> com.senne.starktech.security.os.StarkOs.open(player, ROBOT_BAY.offset(10, 0, 6)))
                    .step(10, () -> desktop(os -> os.tourOpen(com.senne.starktech.client.os.StarkOsScreen.App.ROBOTS)))
                    .pause(100)
                    .close(20);
        });

        add("Soldering modules", "Hoofdstuk 22: modules solderen. Dezelfde werkplaats; op de printplaat van de Commando "
                + "rechts zitten al een Scan (pin 1) en een Laser (pin 3), en er loopt een verstopte soldeerbrug van pin 3 "
                + "naar pin 4. Eerst het bordscherm (Board in het robotscherm): de hittebalk boven pin 2 (je houdt er "
                + "een Camera op; groen vak = goed). Dan Inspect: de brug wordt een roze balk, met 'switching on now "
                + "would short!'. Dan zet hij aan zonder dat iemand de brug weghaalde: kortsluiting! Vonken, rook, een "
                + "knal; de Laser op pin 3 is nu een Burnt Module (E.D.I.T.H. zegt het, rood) en hij blijft uit. Daarna "
                + "het bord nog eens: op pin 3 de verbrande module, en de brug is weg. Tot slot de vier robots van "
                + "dichtbij met alle modules: een schoteltje op hun hoofd (Scan), een camera opzij, een cyaan lensstrook "
                + "(Face Recognition), een sensor op de borst (Distance), een laser rechts en een geweer links.",
                Vec3.atBottomCenterOf(ROBOT_BAY.offset(5, 0, 9)), 180, 12, s -> {
            s.onServer(0, ShowcaseFeatures::buildRobotBay)
                    .onServer(0, player -> {
                        com.senne.starktech.robot.RobotStyle c = com.senne.starktech.robot.RobotStyle.COMMANDO;
                        placeRobot(player, ROBOT_BAY.offset(8, 0, 2), com.senne.starktech.robot.GuardRobotItem.create(
                                new com.senne.starktech.robot.RobotData(new com.senne.starktech.robot.RobotParts(c, c, c, c).pack(),
                                        com.senne.starktech.bot.BotBoard.EMPTY.with(1, "scan").with(3, "laser").withBridge(3), "", 1.0f)));
                        player.getInventory().add(new ItemStack(ModItems.SOLDERING_IRON.get()));
                        player.getInventory().add(new ItemStack(ModItems.robotModule(com.senne.starktech.bot.BotModule.CAMERA)));
                    })
                    .pause(30)
                    .step(0, () -> showBoard(ROBOT_BAY.offset(8, 0, 2), board -> board.tourHeat(
                            com.senne.starktech.bot.BotModule.CAMERA, 2,
                            Math.round(com.senne.starktech.config.StarkConfig.Robots.SOLDER_FULL_TICKS * 0.6f))))
                    .pause(80)
                    .step(0, () -> {
                        if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.robot.BoardScreen board) {
                            board.tourHeat(com.senne.starktech.bot.BotModule.CAMERA, -1, 0);
                            board.tourInspect(true);
                        }
                    })
                    .pause(80)
                    .close(10)
                    // Aan, met de brug: kortsluiting (voor de showcase brandt hij niet helemaal af).
                    .onServer(0, player -> robotAt(player, ROBOT_BAY.offset(8, 0, 2), robot -> {
                        float burn = com.senne.starktech.config.StarkConfig.Robots.BURN_CHANCE;
                        com.senne.starktech.config.StarkConfig.Robots.BURN_CHANCE = 0.0f;
                        robot.setActive(true);
                        com.senne.starktech.config.StarkConfig.Robots.BURN_CHANCE = burn;
                    }))
                    .pause(80)
                    .step(0, () -> showBoard(ROBOT_BAY.offset(8, 0, 2), board -> board.tourInspect(true)))
                    .pause(80)
                    .close(10)
                    // De vier stijlen met alle modules.
                    .onServer(0, player -> {
                        com.senne.starktech.bot.BotBoard all = com.senne.starktech.bot.BotBoard.EMPTY;
                        String[] ids = {"scan", "distance", "face_recognition", "pathfinding", "laser", "gun", "solar_panel", "camera"};
                        for (int i = 0; i < ids.length; i++) {
                            all = all.with(i + 1, ids[i]);
                        }
                        com.senne.starktech.robot.RobotStyle[] styles = com.senne.starktech.robot.RobotStyle.values();
                        for (int i = 0; i < styles.length; i++) {
                            com.senne.starktech.robot.RobotStyle st = styles[i];
                            if (DevWorld.spawnStill(player.serverLevel(), com.senne.starktech.registry.ModEntities.GUARD_ROBOT.get(),
                                    ROBOT_BAY.offset(1 + i * 3, 0, 7), 0) instanceof com.senne.starktech.robot.GuardRobotEntity robot) {
                                robot.setParts(new com.senne.starktech.robot.RobotParts(st, st, st, st));
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
                + "Corners (when message(\"corner\")): na elke vier hoeken zegt E.D.I.T.H. 'Guard 1 walked a full round'. "
                + "Tot slot geeft de server hem de opdracht wave: hij stopt midden in zijn rondje, draait links-rechts, en "
                + "loopt dan verder waar hij was.",
                Vec3.atBottomCenterOf(ROBOT_BAY.offset(5, 0, 11)), 180, 25, s -> {
            s.onServer(0, ShowcaseFeatures::buildRobotBay)
                    .onServer(0, player -> {
                        buildBayServer(player);
                        placeRobot(player, ROBOT_BAY.offset(6, 0, 2), com.senne.starktech.robot.GuardRobotItem.create(
                                new com.senne.starktech.robot.RobotData(new com.senne.starktech.robot.RobotParts(
                                        com.senne.starktech.robot.RobotStyle.COMMANDO, com.senne.starktech.robot.RobotStyle.BATTLE,
                                        com.senne.starktech.robot.RobotStyle.BATTLE, com.senne.starktech.robot.RobotStyle.BATTLE).pack(),
                                        com.senne.starktech.bot.BotBoard.EMPTY, "Guard 1", 1.0f)));
                    })
                    .pause(50)
                    // De app Logic: het robotprogramma Patrol, met de Help.
                    .stand(0, ROBOT_BAY.getX() + 10.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 8.5, 180, 20)
                    .onServer(5, player -> com.senne.starktech.security.os.StarkOs.open(player, ROBOT_BAY.offset(10, 0, 6)))
                    .step(10, () -> desktop(os -> {
                        os.tourLogicNew(com.senne.starktech.logic.program.ProgramKind.ROBOT);
                        os.tourLogic("Patrol", BAY_PATROL, true);
                    }))
                    .pause(120)
                    // De app Robots: Program, en Patrol naar Guard 1.
                    .step(0, () -> desktop(os -> os.tourRobotsProgram()))
                    .pause(60)
                    .onServer(0, player -> {
                        if (player.serverLevel().getBlockEntity(ROBOT_BAY.offset(10, 0, 6))
                                instanceof com.senne.starktech.security.SecurityCoreBlockEntity core) {
                            robotAt(player, ROBOT_BAY.offset(6, 0, 2), robot -> {
                                net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
                                args.putString("id", robot.getUUID().toString());
                                args.putString("program", "Patrol");
                                com.senne.starktech.security.os.StarkOs.handleAction(player, new com.senne.starktech.network.OsActionPayload(
                                        ROBOT_BAY.offset(10, 0, 6), "robot_program", args));
                            });
                        }
                    })
                    .step(0, () -> desktop(os -> os.tourOpen(com.senne.starktech.client.os.StarkOsScreen.App.ROBOTS)))
                    .pause(80)
                    .close(10)
                    // Aan: hij stapt uit zijn dock en loopt zijn vierkantje.
                    .onServer(0, player -> robotAt(player, ROBOT_BAY.offset(6, 0, 2), robot -> robot.setActive(true)))
                    .stand(0, ROBOT_BAY.getX() + 5.5, ROBOT_BAY.getY() + 2, ROBOT_BAY.getZ() + 11.5, 180, 30)
                    .pause(320)
                    // De server geeft hem een opdracht: wave.
                    .onServer(0, player -> {
                        if (player.serverLevel().getBlockEntity(ROBOT_BAY.offset(10, 0, 6))
                                instanceof com.senne.starktech.security.SecurityCoreBlockEntity core) {
                            com.senne.starktech.bot.BotLink.order(player.serverLevel(), core, "Guard 1", "wave", null);
                        }
                    })
                    .pause(160);
        });

        add("Robot senses and weapons", "Hoofdstuk 24: zien en schieten. In de werkplaats staan twee Commando-robots naar "
                + "het noorden te kijken, met elk een husk vijf blokken voor zich. De linker heeft een Laser (pin 3), de rechter "
                + "een Gun (pin 4) met 6 Robot Ammo; hun programma: zodra het wapen klaar is, schieten. Kijk naar de "
                + "oranjerode laserstraal uit de rechterarm en het gele kogelspoortje uit de linkerarm (luister naar de "
                + "'pew' en de knal); de husks vallen om. Dan schiet jij met de Ion Blaster op de linker robot: een dikke "
                + "blauwe straal, en hij valt neer op zijn rug (zonder schade: na 60 seconden staat hij weer op). Tot slot de "
                + "app Cameras op de desktop: beide robots hebben een Camera-module, dus ze staan ertussen met ROBOT erbij "
                + "(de neergeschoten robot geeft NO SIGNAL); het grote beeld kijkt door de ogen van de rechter robot.",
                Vec3.atBottomCenterOf(ROBOT_BAY.offset(5, 0, 11)), 180, 10, s -> {
            s.onServer(0, ShowcaseFeatures::buildRobotBay)
                    .onServer(0, player -> {
                        buildBayServer(player);
                        ServerLevel level = player.serverLevel();
                        String[] weapons = {"laser", "gun"};
                        for (int i = 0; i < weapons.length; i++) {
                            BlockPos pos = ROBOT_BAY.offset(4 + i * 3, 0, 9);
                            DevWorld.spawnStill(level, net.minecraft.world.entity.EntityType.HUSK, pos.north(5), 0);
                            if (DevWorld.spawnStill(level, com.senne.starktech.registry.ModEntities.GUARD_ROBOT.get(), pos, 180)
                                    instanceof com.senne.starktech.robot.GuardRobotEntity robot) {
                                com.senne.starktech.robot.RobotStyle c = com.senne.starktech.robot.RobotStyle.COMMANDO;
                                robot.setParts(new com.senne.starktech.robot.RobotParts(c, c, c, c));
                                robot.setOwner(player);
                                robot.setCustomName(net.minecraft.network.chat.Component.literal(i == 0 ? "Laser guard" : "Gun guard"));
                                int pin = i == 0 ? 3 : 4;
                                robot.setBoard(com.senne.starktech.bot.BotBoard.EMPTY.with(pin, weapons[i]).with(6, "camera"));
                                robot.setAmmo(6);
                                robot.setProgram(program("Shoot", "when started:\n    forever:\n        if read(pin(" + pin + ")):\n"
                                        + "            write(pin(" + pin + "), true)\n        wait(0.5)\n",
                                        com.senne.starktech.logic.program.ProgramKind.ROBOT));
                                robot.setActive(true);
                            }
                        }
                    })
                    .pause(120)
                    // De Ion Blaster: jij schiet de linker robot neer.
                    .onServer(0, player -> robotNear(player, ROBOT_BAY.offset(4, 0, 9), robot -> {
                        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, robot.getBoundingBox().getCenter());
                        com.senne.starktech.robot.IonBlasterItem.fire(player.serverLevel(), player);
                    }))
                    .pause(100)
                    // De app Cameras: de robots met een camera, en het grote beeld van de rechter. (Even wachten: een
                    // nieuwe kern opent eerst zelf Settings, voor een wachtwoord.)
                    .stand(0, ROBOT_BAY.getX() + 10.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 8.5, 180, 20)
                    .onServer(5, player -> com.senne.starktech.security.os.StarkOs.open(player, ROBOT_BAY.offset(10, 0, 6)))
                    .step(30, () -> desktop(os -> os.tourOpen(com.senne.starktech.client.os.StarkOsScreen.App.CAMERAS)))
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
                + "vierkantje, blok voor blok, met draaiende rotors en cyaan lichtjes. Drone 2 heeft maar 33% batterij: na een "
                + "paar zijden vliegt hij terug, landt op zijn dock, laadt tot 50% (heel zacht zoemen) en vliegt terug naar waar "
                + "hij was. Daarna kijk je in de app Cameras door de camera van Drone 1 (een beetje naar beneden), schiet je hem "
                + "neer met de Ion Blaster (hij valt scheef op de grond), en zet je in de app Devices de schakelaar Drones uit: "
                + "Drone 2 vliegt naar zijn dock en landt.",
                Vec3.atBottomCenterOf(ROBOT_BAY.offset(3, 0, 15)), 180, 15, s -> {
            BlockPos core = ROBOT_BAY.offset(10, 0, 6);
            s.onServer(0, ShowcaseFeatures::buildRobotBay)
                    .onServer(0, player -> {
                        buildBayServer(player);
                        buildDroneDocks(player);
                        placeDrone(player, ROBOT_BAY.offset(1, 0, 7));
                        placeDrone(player, ROBOT_BAY.offset(5, 0, 7));
                        if (player.serverLevel().getBlockEntity(core) instanceof com.senne.starktech.security.SecurityCoreBlockEntity be) {
                            be.logic().put(null, program("Hover patrol", HOVER_PATROL, com.senne.starktech.logic.program.ProgramKind.DRONE));
                        }
                        droneAt(player, ROBOT_BAY.offset(1, 0, 7), drone -> {
                            drone.setBoard(com.senne.starktech.bot.BotBoard.EMPTY.with(1, "scan").with(2, "face_recognition")
                                    .with(3, "laser").with(4, "camera"));
                            drone.setProgram(program("Hover patrol", HOVER_PATROL, com.senne.starktech.logic.program.ProgramKind.DRONE));
                        });
                        droneAt(player, ROBOT_BAY.offset(5, 0, 7), drone -> {
                            drone.setBoard(com.senne.starktech.bot.BotBoard.EMPTY.with(1, "solar_panel").with(2, "distance")
                                    .with(3, "pathfinding").with(4, "gun"));
                            drone.setProgram(program("Hover patrol", HOVER_PATROL, com.senne.starktech.logic.program.ProgramKind.DRONE));
                        });
                    })
                    .pause(50)
                    // Het scherm van Drone 1, en zijn bord.
                    .onServer(0, player -> droneAt(player, ROBOT_BAY.offset(1, 0, 7),
                            drone -> com.senne.starktech.robot.Robots.open(player, drone)))
                    .pause(80)
                    .step(0, () -> showDroneBoard(ROBOT_BAY.offset(1, 0, 7)))
                    .pause(80)
                    .close(10)
                    // De app Logic: + met Drone program, en het programma Hover patrol met zijn Help. Dan de app Robots.
                    .stand(0, ROBOT_BAY.getX() + 10.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 8.5, 180, 20)
                    .onServer(5, player -> com.senne.starktech.security.os.StarkOs.open(player, core))
                    .step(30, () -> desktop(os -> os.tourLogicChooseKind()))
                    .pause(60)
                    .step(0, () -> desktop(os -> {
                        os.tourLogicNew(com.senne.starktech.logic.program.ProgramKind.DRONE);
                        os.tourLogic("Hover patrol", HOVER_PATROL, true);
                    }))
                    .pause(120)
                    .step(0, () -> desktop(os -> os.tourOpen(com.senne.starktech.client.os.StarkOsScreen.App.ROBOTS)))
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
                    .onServer(5, player -> com.senne.starktech.security.os.StarkOs.open(player, core))
                    .step(30, () -> desktop(os -> os.tourOpen(com.senne.starktech.client.os.StarkOsScreen.App.CAMERAS)))
                    .pause(30)
                    .step(0, () -> desktop(os -> os.tourRobotCamera("Drone 1")))
                    .pause(150)
                    .close(10)
                    // De Ion Blaster: Drone 1 valt neer.
                    .stand(0, ROBOT_BAY.getX() + 3.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 15.5, 180, 15)
                    .onServer(10, player -> droneNear(player, ROBOT_BAY.offset(0, 2, 8), drone -> {
                        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, drone.getBoundingBox().getCenter());
                        com.senne.starktech.robot.IonBlasterItem.fire(player.serverLevel(), player);
                    }))
                    .pause(100)
                    // De schakelaar Drones (app Devices): Drone 2 vliegt naar zijn dock en landt.
                    .stand(0, ROBOT_BAY.getX() + 10.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 8.5, 180, 20)
                    .onServer(5, player -> com.senne.starktech.security.os.StarkOs.open(player, core))
                    .step(30, () -> desktop(os -> os.tourOpen(com.senne.starktech.client.os.StarkOsScreen.App.DEVICES)))
                    .pause(30)
                    .onServer(0, player -> networkSwitch(player, core, "drones", false))
                    .pause(80)
                    .close(10)
                    .stand(0, ROBOT_BAY.getX() + 3.5, ROBOT_BAY.getY(), ROBOT_BAY.getZ() + 15.5, 180, 15)
                    .pause(160);
        });

        add("Stark Tower: hangar and landing pad", "De toren is van jou (gehackt). In de hangar staan zes Drone Docks "
                + "langs de muur; op vier staan de security drones die Tony programmeerde (hoofdstuk 25: Tower drone 1 tot 4, "
                + "programma Tower patrol, met Scan, Face Recognition, Laser en Pathfinding). Na de hack zijn ze van jou: ze "
                + "stijgen op en vliegen hun rondjes door de hangar, en op jou schieten ze niet. (Zonder eigen server in de "
                + "toren hebben ze geen verbinding, en dan schieten ze op niemand.) Dan het dak: in het harnas stijg je op, en de "
                + "HUD wijst je landingsplatform aan (groen ruitje, afstand, en dichtbij het landingsvak als "
                + "gestippeld vierkant). Je daalt op het platform: E.D.I.T.H. zegt 'Welcome home', en je bril "
                + "laadt op (kijk naar het percentage linksboven).", GameType.SURVIVAL,
                towerPoint(20.5, 34, 17.5), 180, 8, s -> {
            BlockPos core = STARK_TOWER.offset(10, 57, 20);
            Vec3 pad = towerPoint(35.5, 100, 20.5);
            s.onServer(0, player -> {
                if (player.serverLevel().getBlockEntity(core) instanceof com.senne.starktech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    towerDrones(player, be);
                }
            })
                    .pause(240)
                    // Het dak: harnas aan, opstijgen, en de HUD wijst het platform aan.
                    .onServer(0, player -> {
                        wearSuit(player);
                        EdithData.setEnergy(EdithData.getWornGlasses(player),
                                EdithData.getMaxEnergy(EdithData.getWornGlasses(player)) / 3);
                    })
                    .stand(0, STARK_TOWER.getX() + 24.5, STARK_TOWER.getY() + 100, STARK_TOWER.getZ() + 12.5, 90, 0)
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

        add("Crashed satellite", "Een nieuwe structuur: het wrak van een oude Stark-satelliet in een krater. "
                + "Eerst van boven: de rook (blauw uit de romp, van de reactor), de voor aan de oostkant waar hij "
                + "binnenkwam, het zonnepaneel dat nog schuin omhoog steekt, het afgebroken paneel op de helling, "
                + "de schotel op de noordhelling en overal brokstukken. Dan open je de zwarte doos in de romp: "
                + "netherite, diamanten, Stark-chips, upgrades. In een echte wereld duiken er raiddrones op je af "
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

        add("Hydra bunker", "De geheime Hydra-basis, uitgehakt in een berg (Winter Soldier). In een echte "
                + "wereld zoekt de mod een steile helling in de bergen; hier staat hij in een zelfgebouwde berg. "
                + "Buiten: de betonnen gevel met het rode embleem en twee Hydra-torentjes. Binnen: de gang met "
                + "een controlepost, de grote hal (zuilen, kratten, vaandels, soldaten, een torentje achterin), "
                + "de reactorkamer (twee warmtegeneratoren in lava; hun kabel loopt over het plafond van de hal "
                + "naar achteren), de kluisgang met twee rode laserwanden, en de kluis met Hydra-onderdelen en "
                + "een cryo-cel. De laserwanden gaan uit als je de generatoren sloopt of de kabel doorknipt. "
                + "Hier in creative laten de soldaten en torentjes je met rust. Vinden doe je hem door te "
                + "zoeken in de bergen (er ligt geen kaart meer in het satellietwrak).",
                new Vec3(HYDRA_BUNKER.getX() + 0.5, -52, HYDRA_BUNKER.getZ() - 22), 0, 12, s -> {
            BlockPos b = HYDRA_BUNKER;
            s.onServer(0, player -> DevWorld.placeHydraBunker(player.serverLevel(), b))
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
            s.step(0, () -> com.senne.starktech.client.voice.EdithMemory.forgetAll())
                    .open(10, com.senne.starktech.client.BrainScreen::new)
                    .pause(100)
                    .close(0);
            // Zinnen zoals het oor ze zou opschrijven; na elke zin wachten tot ze klaar is met denken.
            s.step(0, () -> {
                com.senne.starktech.client.voice.BrainModel.decide(); // welke maat past bij deze pc
                if (com.senne.starktech.client.voice.EdithBrain.ready(com.senne.starktech.client.voice.BrainMode.LOCAL)) {
                    com.senne.starktech.client.voice.EdithBrain.setMode(com.senne.starktech.client.voice.BrainMode.LOCAL);
                }
            });
            for (String said : new String[] {"hoeveel energie heb ik nog", "zet nachtzicht aan en scan naar ertsen",
                    "onthoud dat mijn basis Stark Hoeve heet", "hoe heet mijn basis?", "doe iets met de ding",
                    "what time is it and is it going to rain"}) {
                s.step(0, () -> {
                    if (com.senne.starktech.client.voice.EdithBrain.mode() == com.senne.starktech.client.voice.BrainMode.LOCAL) {
                        com.senne.starktech.client.voice.VoiceControl.hear(said);
                    }
                })
                        .pause(2)
                        .waitUntil(1200, () -> !com.senne.starktech.client.voice.EdithBrain.isThinking())
                        .pause(70);
            }
            s.step(0, () -> com.senne.starktech.client.voice.EdithBrain.setMode(com.senne.starktech.client.voice.BrainMode.DICTIONARY));
        });

        add("Music (Spotify)", "Het muziekscherm: eenmalig Spotify koppelen, daarna 'E.D.I.T.H. play X "
                + "by Y'.", start, 180, 0, s -> s
                .open(10, MusicScreen::new)
                .pause(120)
                .close(0));

        add("Advancements", "De prestaties onder 'Stark Industries': de rode draad door de mod.",
                start, 180, 0, s -> s
                .step(10, ShowcaseFeatures::openAdvancements)
                .pause(140)
                .close(0));

        add("Stark Tech items", "Alles wat de mod toevoegt, in het creative-tabblad 'Stark Tech': de bril "
                + "(leeg en opgeladen), de Stark-chip, de drone, het harnas, de drie upgrades, de E.M.P. en "
                + "alle blokken (laadstation, zonnepaneel, kabel, warmtegenerator, relais, laserzender, "
                + "koepelgenerator en holotafel).", start, 180, 0, s -> s
                .step(10, ShowcaseFeatures::openStarkTab)
                .pause(160)
                .close(0));
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
            level.setBlockAndUpdate(relay, com.senne.starktech.registry.ModBlocks.RELAY.get().defaultBlockState()
                    .setValue(com.senne.starktech.redstone.RelayBlock.INVERTED, i == 2));
            int index = data.indexAt(level.dimension(), relay);
            com.senne.starktech.redstone.Relay entry = new com.senne.starktech.redstone.Relay(relays[i][0], relays[i][1], level.dimension(), relay);
            data = index < 0 ? data.with(entry) : data.replace(index, entry);
        }
        data = exampleGroups(data);
        player.setData(ModAttachments.RELAYS, data);
        // Een eigen beveiligingskern (met wachtwoord, anders opent de desktop eerst Settings).
        BlockPos core = y.offset(0, 0, 3);
        level.setBlockAndUpdate(core, com.senne.starktech.registry.ModBlocks.SECURITY_CORE.get().defaultBlockState());
        claimTowerCore(player, core);
        BlockPos emitter = y.offset(6, 0, 2);
        level.setBlockAndUpdate(emitter, com.senne.starktech.registry.ModBlocks.RELAY_EMITTER.get().defaultBlockState());
        if (level.getBlockEntity(emitter) instanceof com.senne.starktech.redstone.RelayEmitterBlockEntity entity) {
            entity.setOwner(player);
            entity.configure(player, "base", com.senne.starktech.redstone.RelayEmitterBlockEntity.Mode.FOLLOW);
        }
        level.setBlockAndUpdate(emitter.east(), Blocks.LEVER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(net.minecraft.world.level.block.LeverBlock.POWERED, false));
    }

    /**
     * Jouw voorbeeld van 14 oktober als groepen: base > visitors > cafe en storage, base > restricted > security
     * room en power room. Ook gebruikt door de screenshots (VisualCheck).
     */
    static com.senne.starktech.redstone.EdithRelays exampleGroups(com.senne.starktech.redstone.EdithRelays data) {
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
        level.setBlockAndUpdate(y, com.senne.starktech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(y) instanceof com.senne.starktech.station.ChargerBlockEntity generator) {
            generator.fill(20_000);
        }
        for (int x = 1; x <= 7; x++) {
            com.senne.starktech.energy.StarkCables.add(level, y.east(x));
        }
        for (int up = 0; up < 4; up++) {
            level.setBlockAndUpdate(y.east(7).above(up), Blocks.POLISHED_ANDESITE.defaultBlockState());
            com.senne.starktech.energy.StarkCables.add(level, y.east(7).above(up));
        }
        level.setBlockAndUpdate(y.east(7).above(4), com.senne.starktech.registry.ModBlocks.ARC_LAMP.get().defaultBlockState());
        level.setBlockAndUpdate(y.east(3), com.senne.starktech.registry.ModBlocks.LIGHT_SWITCH.get().defaultBlockState()
                .setValue(com.senne.starktech.light.LightSwitchBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(com.senne.starktech.light.LightSwitchBlock.FACING, net.minecraft.core.Direction.NORTH)
                .setValue(com.senne.starktech.light.LightSwitchBlock.ON, false));
    }

    /** Het schuifje "Brightness" in het open instelscherm van de lamp naar dit lichtniveau. */
    private static void lampScreen(int lightLevel) {
        if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.light.ArcLampScreen screen) {
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

    /** Een punt in de Stark Tower, gemeten vanaf de hoek van het gebouw (zoals in generate_structure.py). */
    private static Vec3 towerPoint(double x, double y, double z) {
        return new Vec3(STARK_TOWER.getX() + x, STARK_TOWER.getY() + y, STARK_TOWER.getZ() + z);
    }

    /**
     * De kern van de toren is (even) van jou, met een wachtwoord, zodat de desktop meteen opengaat
     * zonder eerst om een wachtwoord te vragen. Alleen voor de showcase.
     */
    private static void claimTowerCore(net.minecraft.server.level.ServerPlayer player, BlockPos core) {
        if (player.serverLevel().getBlockEntity(core) instanceof com.senne.starktech.security.SecurityCoreBlockEntity be) {
            be.setOwner(player);
            be.ensureSystem(player.serverLevel());
            be.setPassword("jarvis1", player.serverLevel().random);
        }
    }

    // ---------------------------------------------------------------- Het lab van hoofdstuk 8

    /** Waar het lab staat: zijn beveiligingskern (ver genoeg van de toren, die zijn eigen kern heeft). */
    static final BlockPos LOGIC_LAB = new BlockPos(-50, -60, -50);

    /** Het diagram van het lab, zoals je het in de app Logic ziet. */
    private static final String LAB_CODE = """
            # The lab door: a level 3 card (or the code 7777) opens it for 3 seconds
            def open_door():
                accept()
                pulse("Lab door lock", 3)

            when keycard("Lab door"):
                if card.valid and card.level >= 3:
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
     * bericht en een lus. Gaat de lamp aan (code 4321 op het paneel), dan telt het, zegt E.D.I.T.H. hoe
     * vaak, en gaat de deur drie keer even open.
     */
    private static final String BLINK_CODE = """
            count = 0

            when turned_on("Lab lights"):
                count = count + 1
                say("The lights went on " + count + " times")
                repeat(3):
                    pulse("Lab door lock", 0.5)
                    wait(1)
            """;

    /** Zet het diagram "Blink" (hierboven) naast "Lab" op de kern van het lab. */
    private static void addBlinkProgram(net.minecraft.server.level.ServerPlayer player) {
        if (player.serverLevel().getBlockEntity(LOGIC_LAB) instanceof com.senne.starktech.security.SecurityCoreBlockEntity core) {
            core.logic().remove("Blink");
            var graph = com.senne.starktech.logic.program.GraphConverter.toGraph(
                    com.senne.starktech.logic.program.ScriptParser.parse(BLINK_CODE), null);
            core.logic().put(null, new com.senne.starktech.logic.program.LogicProgram("Blink", true, graph, BLINK_CODE));
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
        level.setBlockAndUpdate(lab, com.senne.starktech.registry.ModBlocks.SECURITY_CORE.get().defaultBlockState());
        claimTowerCore(player, lab);
        BlockPos[] racks = {lab.offset(3, 0, 0), lab.offset(3, 1, 0), lab.offset(4, 0, 0)};
        for (BlockPos generator : new BlockPos[] {lab.below(), racks[0].below()}) {
            level.setBlockAndUpdate(generator, com.senne.starktech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof com.senne.starktech.station.ChargerBlockEntity charger) {
                charger.fill(30_000);
            }
        }
        net.minecraft.world.level.block.state.BlockState rack = com.senne.starktech.registry.ModBlocks.SERVER_RACK.get()
                .defaultBlockState().setValue(com.senne.starktech.logic.ServerRackBlock.FACING, net.minecraft.core.Direction.SOUTH);
        net.minecraft.world.item.Item[][] parts = {
                {com.senne.starktech.registry.ModItems.SERVER_CPU.get(), com.senne.starktech.registry.ModItems.SERVER_RAM.get()},
                {com.senne.starktech.registry.ModItems.SERVER_CPU.get(), com.senne.starktech.registry.ModItems.SERVER_SSD.get()},
                {com.senne.starktech.registry.ModItems.SERVER_RAM.get(), com.senne.starktech.registry.ModItems.SERVER_SSD.get()}};
        for (int i = 0; i < racks.length; i++) {
            level.setBlockAndUpdate(racks[i], rack);
            if (level.getBlockEntity(racks[i]) instanceof com.senne.starktech.logic.ServerRackBlockEntity be) {
                for (int slot = 0; slot < parts[i].length; slot++) {
                    be.parts().setItem(slot, new net.minecraft.world.item.ItemStack(parts[i][slot]));
                }
                // Elk rack heeft zijn eigen fan nodig (8 oktober), anders wordt het steeds warmer.
                be.parts().setItem(5, new net.minecraft.world.item.ItemStack(com.senne.starktech.registry.ModItems.SERVER_FAN.get()));
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
        level.setBlockAndUpdate(doorRelay, com.senne.starktech.registry.ModBlocks.RELAY.get().defaultBlockState());
        level.setBlockAndUpdate(lightRelay, com.senne.starktech.registry.ModBlocks.RELAY.get().defaultBlockState());
        // De twee relais in je redstone-lijst (erbij, niet in plaats van: de relais-demo heeft zijn lamp nodig).
        var relays = player.getData(com.senne.starktech.registry.ModAttachments.RELAYS);
        if (relays.indexAt(level.dimension(), doorRelay) < 0) {
            relays = relays.with(new com.senne.starktech.redstone.Relay("Lab door lock", "lab", level.dimension(), doorRelay));
        }
        if (relays.indexAt(level.dimension(), lightRelay) < 0) {
            relays = relays.with(new com.senne.starktech.redstone.Relay("Lab lights", "lab", level.dimension(), lightRelay));
        }
        player.setData(com.senne.starktech.registry.ModAttachments.RELAYS, relays);
        placeInput(level, lab.offset(1, 1, 7), com.senne.starktech.registry.ModBlocks.KEYCARD_READER.get().defaultBlockState(), "Lab door");
        placeInput(level, lab.offset(5, 1, 7), com.senne.starktech.registry.ModBlocks.CODE_PANEL.get().defaultBlockState(), "Lights panel");
        // Het diagram, en een kaart van niveau 3 in je hand.
        if (level.getBlockEntity(lab) instanceof com.senne.starktech.security.SecurityCoreBlockEntity core) {
            com.senne.starktech.logic.LogicDevices.refresh(level, core, player);
            core.logic().remove("Lab");
            var graph = com.senne.starktech.logic.program.GraphConverter.toGraph(
                    com.senne.starktech.logic.program.ScriptParser.parse(LAB_CODE), null);
            core.logic().put(null, new com.senne.starktech.logic.program.LogicProgram("Lab", true, graph, LAB_CODE));
            core.setChanged();
            com.senne.starktech.security.SecurityDevices.forget(level, core);
            com.senne.starktech.logic.LogicServer.forgetAll();
        }
    }

    // ---------------------------------------------------------------- Inbreken (hoofdstuk 12)

    /** Laat de racks van het lab meteen kijken of ze meedraaien (anders duurt dat tot twee seconden). */
    static void wakeLabServer(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        for (BlockPos rackPos : new BlockPos[] {LOGIC_LAB.offset(3, 0, 0), LOGIC_LAB.offset(3, 1, 0), LOGIC_LAB.offset(4, 0, 0)}) {
            if (level.getBlockEntity(rackPos) instanceof com.senne.starktech.logic.ServerRackBlockEntity rack) {
                rack.update(level, rackPos, level.getBlockState(rackPos));
            }
        }
        com.senne.starktech.bypass.Bypass.clear();
    }

    /**
     * Happy kijkt naar het noorden en heeft keycards in zijn zakken: twee beschreven kaarten en twee lege, en
     * in zijn hand een koekje (dat zit niet in een zak). Jij hebt een lege hand.
     */
    static void fillHappysPockets(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.level.ServerPlayer happy = ShowcaseScene.friend(player);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        if (happy == null) {
            return;
        }
        var inventory = happy.getInventory();
        inventory.selected = 0;
        inventory.setItem(0, new ItemStack(Items.COOKIE));
        String[][] cards = {{"9", "Stark Tower", "5"}, {"10", "Garage", "2"}};
        for (String[] card : cards) {
            ItemStack stack = new ItemStack(ModItems.KEYCARD.get());
            com.senne.starktech.logic.KeycardItem.write(stack, new com.senne.starktech.logic.KeycardData(
                    "10.0.0.7", "happy-net", 1000 + Integer.parseInt(card[0]), Integer.parseInt(card[2]), card[1]));
            inventory.setItem(Integer.parseInt(card[0]), stack);
        }
        inventory.setItem(11, new ItemStack(ModItems.KEYCARD.get(), 2));
        turnHappy(player, 180);
    }

    /** Opent de zak van Happy (jij staat achter hem). */
    static void pickHappysPocket(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.level.ServerPlayer happy = ShowcaseScene.friend(player);
        if (happy != null) {
            com.senne.starktech.pickpocket.Pickpocket.tryStart(player, happy);
        }
    }

    /** Laat Happy deze kant op kijken (0 = zuid, naar jou toe; 180 = noord), ook met zijn hoofd. */
    static void turnHappy(net.minecraft.server.level.ServerPlayer player, float yaw) {
        net.minecraft.server.level.ServerPlayer happy = ShowcaseScene.friend(player);
        if (happy != null) {
            happy.setYRot(yaw);
            happy.setYHeadRot(yaw);
            happy.yHeadRotO = yaw;
            happy.yRotO = yaw;
            happy.setXRot(0);
        }
    }

    // ---------------------------------------------------------------- De basis met een zone van hoofdstuk 10

    /** Waar de basis met een zone staat (ver van de rest: een kern beschermt 30 blokken om zich heen). */
    static final BlockPos ZONE_BASE = new BlockPos(-130, -60, 60);

    /**
     * Een L-vormige basis van stenen bakstenen (10 x 8, vier hoog, zonder dak, met een deuropening aan
     * de noordkant) met een torentje van twaalf hoog in de zuidoosthoek, en in de westmuur een stuk
     * obsidiaan (voor C4). Binnen de kern van jou, op een volle generator, met de zone: twee kubussen.
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
        if (level.getBlockEntity(core) instanceof com.senne.starktech.security.SecurityCoreBlockEntity be) {
            be.setOwner(player);
            be.resetAccessForNewOwner();
            be.ensureSystem(level);
            // Eén blok ruimer dan de muren: zo zet ook niemand iets tegen de buitenkant van je muur.
            be.setZoneBoxes(java.util.List.of(
                    com.senne.starktech.security.zone.ZoneBox.of(b.offset(1, -1, 1), b.offset(12, 6, 10)),
                    com.senne.starktech.security.zone.ZoneBox.of(b.offset(7, -1, 11), b.offset(12, 14, 14))));
        }
    }

    /** Een volle generator onder de kern van de basis (de zone heeft stroom nodig). */
    static void powerZoneBase(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos generator = ZONE_BASE.offset(4, -1, 5);
        level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(generator) instanceof com.senne.starktech.station.ChargerBlockEntity charger) {
            charger.fill(20_000);
        }
    }

    /** De kern van de basis met een zone (of null als hij er niet staat). */
    static com.senne.starktech.security.SecurityCoreBlockEntity zoneCore(ServerPlayer player) {
        return player.serverLevel().getBlockEntity(ZONE_BASE.offset(4, 0, 5))
                instanceof com.senne.starktech.security.SecurityCoreBlockEntity core ? core : null;
    }

    /**
     * De basis is nu van Pepper (een speler die niet online is): jij bent er een vreemde. De zone blijft
     * werken, ook als de eigenaar offline is. (Geen nep-speler die inlogt: een tweede keer inloggen met
     * dezelfde naam laat NeoForge struikelen.)
     */
    static void giveZoneBaseToPepper(ServerPlayer player) {
        com.senne.starktech.security.SecurityCoreBlockEntity core = zoneCore(player);
        if (core != null) {
            core.setOwner(java.util.UUID.nameUUIDFromBytes("dev-Pepper".getBytes()), "Pepper");
            core.resetAccessForNewOwner();
        }
    }

    /** Een remote C4 tegen de westkant van het obsidiaan, gekoppeld aan de detonator in je hand. */
    static void stickRemote(ServerPlayer player, BlockPos support) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = support.west();
        level.setBlockAndUpdate(pos, ModBlocks.REMOTE_C4.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACING, Direction.WEST));
        if (level.getBlockEntity(pos) instanceof com.senne.starktech.c4.C4BlockEntity c4
                && player.getMainHandItem().getItem() instanceof com.senne.starktech.c4.DetonatorItem) {
            com.senne.starktech.c4.DetonatorItem.link(level, pos, c4, player.getMainHandItem());
        }
    }

    // ---------------------------------------------------------------- Barsten herstellen (hoofdstuk 11)

    /** Een klapje van 4,5 breekpunten op alleen dit blok (van de zuidkant): barsten, maar het breekt niet. */
    static void dent(ServerLevel level, BlockPos pos) {
        com.senne.starktech.c4.C4Blast.breach(level, Vec3.atCenterOf(pos).add(0, 0, 0.95), 4.5f, 3.0f, here -> here.equals(pos), null);
    }

    /** Een Crusher en een Compactor met hun voorkant naar het noorden, elk tegen een volle generator. */
    static void buildRepairMachines(ServerPlayer player, BlockPos crusher, BlockPos compactor) {
        ServerLevel level = player.serverLevel();
        level.setBlockAndUpdate(crusher, ModBlocks.CRUSHER.get().defaultBlockState()
                .setValue(com.senne.starktech.repair.CrackMachineBlock.FACING, Direction.NORTH));
        level.setBlockAndUpdate(compactor, ModBlocks.COMPACTOR.get().defaultBlockState()
                .setValue(com.senne.starktech.repair.CrackMachineBlock.FACING, Direction.NORTH));
        for (BlockPos generator : new BlockPos[] {crusher.west(), compactor.east()}) {
            level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof com.senne.starktech.station.ChargerBlockEntity charger) {
                charger.fill(20_000);
            }
        }
    }

    /** Het gebarsten blok uit je inventaris (of nog op de grond naast je) gaat de machine in. */
    static void putInMachine(ServerPlayer player, BlockPos pos) {
        if (!(player.serverLevel().getBlockEntity(pos) instanceof com.senne.starktech.repair.CrackMachineBlockEntity machine)) {
            return;
        }
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(ModItems.CRACKED_BLOCK.get())) {
                machine.setItem(com.senne.starktech.repair.CrackMachineBlockEntity.SLOT_IN, inventory.removeItemNoUpdate(slot));
                return;
            }
        }
        for (net.minecraft.world.entity.item.ItemEntity item : player.serverLevel().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(4))) {
            if (item.getItem().is(ModItems.CRACKED_BLOCK.get())) {
                machine.setItem(com.senne.starktech.repair.CrackMachineBlockEntity.SLOT_IN, item.getItem().copy());
                item.discard();
                return;
            }
        }
    }

    /** Opent het scherm van de machine op deze plek. */
    static void openMachine(ServerPlayer player, BlockPos pos) {
        if (player.serverLevel().getBlockEntity(pos) instanceof com.senne.starktech.repair.CrackMachineBlockEntity machine) {
            player.openMenu(machine, buf -> buf.writeVarInt(machine.kind().ordinal()));
        }
    }

    /** Wat uit de Crusher kwam, gaat de Compactor in. */
    static void moveBetweenMachines(ServerPlayer player, BlockPos from, BlockPos to) {
        if (player.serverLevel().getBlockEntity(from) instanceof com.senne.starktech.repair.CrackMachineBlockEntity first
                && player.serverLevel().getBlockEntity(to) instanceof com.senne.starktech.repair.CrackMachineBlockEntity second) {
            second.setItem(com.senne.starktech.repair.CrackMachineBlockEntity.SLOT_IN,
                    first.removeItem(com.senne.starktech.repair.CrackMachineBlockEntity.SLOT_OUT, 64));
        }
    }

    /** Wat uit de machine kwam, neem je in je hand. */
    static void takeFromMachine(ServerPlayer player, BlockPos pos) {
        if (player.serverLevel().getBlockEntity(pos) instanceof com.senne.starktech.repair.CrackMachineBlockEntity machine) {
            player.setItemInHand(InteractionHand.MAIN_HAND,
                    machine.removeItem(com.senne.starktech.repair.CrackMachineBlockEntity.SLOT_OUT, 64));
        }
    }

    // ---------------------------------------------------------------- Het datacenter van hoofdstuk 9

    /** Het tweede datacenter, ver weg (zodat zijn gebied niet over het lab ligt): hier gaan de backups heen. */
    static final BlockPos DATACENTER_VAULT = new BlockPos(-50, -60, -130);

    /**
     * Het lab van hoofdstuk 8, met in het rack rechts vier GPU's erbij, ook op de plek van zijn fan (en
     * dus geen fan meer: dat rack wordt steeds warmer; de andere twee racks hebben hun fan en blijven koel),
     * en een tweede datacenter "dev-vault" ver weg met daarop al een backup van het lab.
     */
    static void buildDatacenter(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        buildLogicLab(player);
        if (level.getBlockEntity(LOGIC_LAB.offset(4, 0, 0)) instanceof com.senne.starktech.logic.ServerRackBlockEntity rack) {
            for (int slot = 2; slot < 6; slot++) {
                rack.parts().setItem(slot, new net.minecraft.world.item.ItemStack(com.senne.starktech.registry.ModItems.SERVER_GPU.get()));
            }
        }
        // Het tweede datacenter: een kern, een generator en een rack met CPU, RAM en twee SSD's.
        BlockPos vault = DATACENTER_VAULT;
        BlockPos vaultRack = vault.offset(3, 0, 0);
        for (BlockPos pos : BlockPos.betweenClosed(vault.offset(-2, 0, -2), vault.offset(5, 3, 3))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(vault, com.senne.starktech.registry.ModBlocks.SECURITY_CORE.get().defaultBlockState());
        claimTowerCore(player, vault);
        for (BlockPos generator : new BlockPos[] {vault.below(), vaultRack.below()}) {
            level.setBlockAndUpdate(generator, com.senne.starktech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof com.senne.starktech.station.ChargerBlockEntity charger) {
                charger.fill(30_000);
            }
        }
        level.setBlockAndUpdate(vaultRack, com.senne.starktech.registry.ModBlocks.SERVER_RACK.get().defaultBlockState());
        if (level.getBlockEntity(vaultRack) instanceof com.senne.starktech.logic.ServerRackBlockEntity rack) {
            rack.parts().setItem(0, new net.minecraft.world.item.ItemStack(com.senne.starktech.registry.ModItems.SERVER_CPU.get()));
            rack.parts().setItem(1, new net.minecraft.world.item.ItemStack(com.senne.starktech.registry.ModItems.SERVER_RAM.get()));
            rack.parts().setItem(2, new net.minecraft.world.item.ItemStack(com.senne.starktech.registry.ModItems.SERVER_SSD.get()));
            rack.parts().setItem(3, new net.minecraft.world.item.ItemStack(com.senne.starktech.registry.ModItems.SERVER_SSD.get()));
            rack.parts().setItem(4, new net.minecraft.world.item.ItemStack(com.senne.starktech.registry.ModItems.SERVER_FAN.get()));
        }
        if (level.getBlockEntity(vault) instanceof com.senne.starktech.security.SecurityCoreBlockEntity core) {
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
            if (level.getBlockEntity(pos) instanceof com.senne.starktech.logic.ServerRackBlockEntity rack) {
                com.senne.starktech.security.SecurityCoreBlockEntity core = com.senne.starktech.security.SecurityCores.coreAt(level, pos);
                if (core != null) {
                    com.senne.starktech.security.SecurityDevices.forget(level, core);
                }
                com.senne.starktech.logic.LogicServer.forgetAll();
                rack.update(level, pos, level.getBlockState(pos));
            }
        }
        com.senne.starktech.logic.LogicServer.forgetAll();
        if (level.getBlockEntity(vault) instanceof com.senne.starktech.security.SecurityCoreBlockEntity core) {
            core.datacenter().update(level, core);
        }
        if (level.getBlockEntity(LOGIC_LAB.offset(4, 0, 0)) instanceof com.senne.starktech.logic.ServerRackBlockEntity hot) {
            hot.setTemperature(30);
        }
        if (level.getBlockEntity(LOGIC_LAB) instanceof com.senne.starktech.security.SecurityCoreBlockEntity lab) {
            lab.datacenter().update(level, lab);
            net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
            args.putString("key", com.senne.starktech.logic.Datacenters.key(net.minecraft.core.GlobalPos.of(level.dimension(), vault)));
            com.senne.starktech.logic.Backups.handle(player, lab, "backup_target", args);
            com.senne.starktech.logic.Backups.handle(player, lab, "backup_now", new net.minecraft.nbt.CompoundTag());
        }
    }

    /** De nieuwste backup van het lab terugzetten (zoals de knop Restore in de app Backup). */
    private static void restoreLab(net.minecraft.server.level.ServerPlayer player) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        com.senne.starktech.logic.Datacenters.Backup backup = com.senne.starktech.logic.Backups.newest(level, player.getUUID());
        if (backup != null && level.getBlockEntity(LOGIC_LAB) instanceof com.senne.starktech.security.SecurityCoreBlockEntity lab) {
            net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
            args.putInt("id", backup.id());
            com.senne.starktech.logic.Backups.handle(player, lab, "backup_restore", args);
        }
    }

    /** Opent het scherm van een rack. */
    private static void openRack(net.minecraft.server.level.ServerPlayer player, BlockPos pos) {
        if (player.serverLevel().getBlockEntity(pos) instanceof com.senne.starktech.logic.ServerRackBlockEntity rack) {
            player.openMenu(rack, pos);
        }
    }

    /** Maakt één rack van het lab te heet (en zijn GPU's al wat versleten), zodat je de waarschuwingen ziet. */
    private static void heatUp(net.minecraft.server.level.ServerPlayer player, BlockPos rackPos, double temperature) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        if (level.getBlockEntity(rackPos) instanceof com.senne.starktech.logic.ServerRackBlockEntity rack) {
            for (int slot = 2; slot < 6; slot++) {
                com.senne.starktech.logic.ServerPartItem.setWear(rack.parts().getItem(slot), 6 + slot * 2);
            }
            rack.setTemperature(temperature);
        }
        if (level.getBlockEntity(LOGIC_LAB) instanceof com.senne.starktech.security.SecurityCoreBlockEntity lab) {
            com.senne.starktech.logic.LogicServer.forgetAll();
            lab.datacenter().update(level, lab);
        }
    }

    private static void placeInput(net.minecraft.server.level.ServerLevel level, BlockPos pos,
                                   net.minecraft.world.level.block.state.BlockState state, String name) {
        level.setBlockAndUpdate(pos, state.setValue(com.senne.starktech.logic.InputDeviceBlock.FACING, net.minecraft.core.Direction.SOUTH));
        if (level.getBlockEntity(pos) instanceof com.senne.starktech.logic.InputDeviceBlockEntity device) {
            device.setName(name);
        }
    }

    /** Een kaart tegen de lezer van het lab: een goede van niveau 3, of een geblokkeerde. */
    static void readCard(net.minecraft.server.level.ServerPlayer player, boolean good) {
        net.minecraft.server.level.ServerLevel level = player.serverLevel();
        if (!(level.getBlockEntity(LOGIC_LAB) instanceof com.senne.starktech.security.SecurityCoreBlockEntity core)) {
            return;
        }
        // De racks laten meteen kijken of ze meedraaien (anders duurt dat tot twee seconden).
        for (BlockPos rackPos : new BlockPos[] {LOGIC_LAB.offset(3, 0, 0), LOGIC_LAB.offset(3, 1, 0), LOGIC_LAB.offset(4, 0, 0)}) {
            if (level.getBlockEntity(rackPos) instanceof com.senne.starktech.logic.ServerRackBlockEntity rack) {
                rack.update(level, rackPos, level.getBlockState(rackPos));
            }
        }
        var card = core.logic().issue(good ? "Lab access" : "Lost card", 3, player.getGameProfile().getName());
        if (card == null) {
            return;
        }
        if (!good) {
            core.logic().setBlocked(card.number(), true);
        }
        var data = new com.senne.starktech.logic.KeycardData(core.ip(), core.hostname(), card.number(), 3, card.label());
        net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(com.senne.starktech.registry.ModItems.KEYCARD.get());
        com.senne.starktech.logic.KeycardItem.write(stack, data);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
        com.senne.starktech.logic.LogicEvents.clear();
        com.senne.starktech.logic.LogicEvents.keycard(level, LOGIC_LAB.offset(1, 1, 7), player, data);
    }

    // ================================================================ Hoofdstuk 25: security drones

    /** Het droneprogramma van de showcase "Security drones": een vierkant vliegen, en laden als de batterij laag is. */
    private static final String HOVER_PATROL = """
            when started:
                up(2)
                forever:
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
        for (com.senne.starktech.robot.GuardDroneEntity old : level.getEntitiesOfClass(com.senne.starktech.robot.GuardDroneEntity.class,
                new net.minecraft.world.phys.AABB(ROBOT_BAY).inflate(24))) {
            old.discard();
        }
        net.minecraft.world.level.block.state.BlockState dock = com.senne.starktech.registry.ModBlocks.DRONE_DOCK.get().defaultBlockState()
                .setValue(com.senne.starktech.arsenal.DroneDockBlock.FACING, net.minecraft.core.Direction.SOUTH);
        for (int x : new int[] {1, 5}) {
            level.setBlockAndUpdate(ROBOT_BAY.offset(x, 0, 7), dock);
            if (level.getBlockEntity(ROBOT_BAY.offset(x, 0, 7)) instanceof com.senne.starktech.arsenal.DroneDockBlockEntity be) {
                be.setOwner(player.getUUID());
                be.setBot(null);
            }
            BlockPos generator = ROBOT_BAY.offset(x == 1 ? 0 : 6, 0, 7);
            level.setBlockAndUpdate(generator, com.senne.starktech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof com.senne.starktech.station.ChargerBlockEntity charger) {
                charger.fill(20_000);
            }
        }
    }

    /** Zet een nieuwe security drone neer op dit dock, zoals een speler dat doet (met de rechtermuisknop). */
    private static void placeDrone(net.minecraft.server.level.ServerPlayer player, BlockPos dock) {
        ItemStack before = player.getMainHandItem();
        ItemStack drone = com.senne.starktech.robot.GuardDroneItem.create(com.senne.starktech.robot.DroneData.DEFAULT);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, drone);
        net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                Vec3.atCenterOf(dock).add(0, 0.5, 0), net.minecraft.core.Direction.UP, dock, false);
        drone.useOn(new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND, hit));
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, before);
    }

    /** Doe iets met de drone op dit dock. */
    private static void droneAt(net.minecraft.server.level.ServerPlayer player, BlockPos dock,
                                Consumer<com.senne.starktech.robot.GuardDroneEntity> action) {
        for (com.senne.starktech.robot.GuardDroneEntity drone : player.serverLevel().getEntitiesOfClass(
                com.senne.starktech.robot.GuardDroneEntity.class, new net.minecraft.world.phys.AABB(dock))) {
            action.accept(drone);
            return;
        }
    }

    /** Doe iets met de drone die het dichtst bij deze plek hangt (binnen 6 blokken). */
    private static void droneNear(net.minecraft.server.level.ServerPlayer player, BlockPos pos,
                                  Consumer<com.senne.starktech.robot.GuardDroneEntity> action) {
        com.senne.starktech.robot.GuardDroneEntity best = null;
        for (com.senne.starktech.robot.GuardDroneEntity drone : player.serverLevel().getEntitiesOfClass(
                com.senne.starktech.robot.GuardDroneEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(6))) {
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
        for (com.senne.starktech.robot.GuardDroneEntity drone : mc.level.getEntitiesOfClass(
                com.senne.starktech.robot.GuardDroneEntity.class, new net.minecraft.world.phys.AABB(dock))) {
            mc.setScreen(new com.senne.starktech.client.robot.BoardScreen(drone.getId(), null));
            return;
        }
    }

    /**
     * Tony's drones in de hangar van de toren (hoofdstuk 25), van deze speler: weg met die van een vorige keer, de docks
     * weer vrij, de kern zet er nieuwe op, en ze zijn van jou (alsof je de toren net gehackt hebt).
     */
    private static void towerDrones(net.minecraft.server.level.ServerPlayer player,
                                    com.senne.starktech.security.SecurityCoreBlockEntity core) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.phys.AABB tower = new net.minecraft.world.phys.AABB(STARK_TOWER).expandTowards(41, 110, 55);
        for (com.senne.starktech.robot.GuardDroneEntity old : level.getEntitiesOfClass(com.senne.starktech.robot.GuardDroneEntity.class, tower)) {
            old.discard();
        }
        for (int x = 10; x <= 30; x += 4) {
            if (level.getBlockEntity(STARK_TOWER.offset(x, 34, 7)) instanceof com.senne.starktech.arsenal.DroneDockBlockEntity dock) {
                dock.setBot(null);
            }
        }
        com.senne.starktech.robot.TowerDrones.fill(level, core);
        com.senne.starktech.robot.TowerDrones.handOver(level, core, player);
    }

    /** Iets doen met de Stark OS-desktop die nu openstaat (als hij openstaat). */
    // ================================================================ Hoofdstuk 21: de robotwerkplaats

    static final BlockPos ROBOT_BAY = new BlockPos(-220, -60, 140);

    /**
     * De robotwerkplaats: een vloer, links een Robot Assembler (vol: vier onderdelen en een printplaat),
     * rechts twee Robot Docks met een volle warmtegenerator ertussen, en achteraan een beveiligingskern (met
     * een generator eronder) voor de app Robots. Robots van een vorige keer gaan eerst weg.
     */
    private static void buildRobotBay(net.minecraft.server.level.ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (com.senne.starktech.robot.GuardRobotEntity old : level.getEntitiesOfClass(com.senne.starktech.robot.GuardRobotEntity.class,
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
        level.setBlockAndUpdate(assembler, com.senne.starktech.registry.ModBlocks.ROBOT_ASSEMBLER.get().defaultBlockState()
                .setValue(com.senne.starktech.robot.RobotAssemblerBlock.FACING, net.minecraft.core.Direction.SOUTH));
        if (level.getBlockEntity(assembler) instanceof com.senne.starktech.robot.RobotAssemblerBlockEntity be) {
            com.senne.starktech.robot.RobotStyle[] styles = {com.senne.starktech.robot.RobotStyle.COMMANDO,
                    com.senne.starktech.robot.RobotStyle.SUPER_BATTLE, com.senne.starktech.robot.RobotStyle.TRAINER,
                    com.senne.starktech.robot.RobotStyle.BATTLE};
            for (com.senne.starktech.robot.RobotSlot slot : com.senne.starktech.robot.RobotSlot.values()) {
                be.items().setItem(slot.ordinal(), new ItemStack(ModItems.robotPart(slot, styles[slot.ordinal()])));
            }
            be.items().setItem(com.senne.starktech.robot.RobotAssemblerBlockEntity.BOARD, new ItemStack(ModItems.ROBOT_CIRCUIT_BOARD.get()));
            be.items().setItem(com.senne.starktech.robot.RobotAssemblerBlockEntity.ROBOT, ItemStack.EMPTY);
        }
        net.minecraft.world.level.block.state.BlockState dock = com.senne.starktech.registry.ModBlocks.ROBOT_DOCK.get().defaultBlockState()
                .setValue(com.senne.starktech.robot.RobotDockBlock.FACING, net.minecraft.core.Direction.SOUTH);
        level.setBlockAndUpdate(ROBOT_BAY.offset(6, 0, 2), dock);
        level.setBlockAndUpdate(ROBOT_BAY.offset(8, 0, 2), dock);
        for (BlockPos generator : new BlockPos[] {ROBOT_BAY.offset(7, 0, 2), ROBOT_BAY.offset(10, -1, 6)}) {
            level.setBlockAndUpdate(generator, com.senne.starktech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof com.senne.starktech.station.ChargerBlockEntity charger) {
                charger.fill(20_000);
            }
        }
        BlockPos core = ROBOT_BAY.offset(10, 0, 6);
        level.setBlockAndUpdate(core, com.senne.starktech.registry.ModBlocks.SECURITY_CORE.get().defaultBlockState());
        if (level.getBlockEntity(core) instanceof com.senne.starktech.security.SecurityCoreBlockEntity be) {
            be.setOwner(player);
            be.ensureSystem(level);
        }
    }

    /** Het robotprogramma van de showcase "Robot programs": een vierkantje, en zwaaien als de server dat zegt. */
    private static final String BAY_PATROL = """
            when started:
                forever:
                    forward(3)
                    turn_right()
                    send("corner")

            when ordered("wave"):
                repeat(2):
                    turn_left()
                    turn_right()
            """;

    /**
     * Voor "Robot programs" (hoofdstuk 23): een serverrack naast de kern van de werkplaats (met stroom), het
     * robotprogramma Patrol en het diagram Corners op die server.
     */
    private static void buildBayServer(net.minecraft.server.level.ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos rack = ROBOT_BAY.offset(12, 0, 6);
        level.setBlockAndUpdate(rack.below(), com.senne.starktech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(rack.below()) instanceof com.senne.starktech.station.ChargerBlockEntity charger) {
            charger.fill(30_000);
        }
        level.setBlockAndUpdate(rack, com.senne.starktech.registry.ModBlocks.SERVER_RACK.get().defaultBlockState()
                .setValue(com.senne.starktech.logic.ServerRackBlock.FACING, net.minecraft.core.Direction.SOUTH));
        if (level.getBlockEntity(rack) instanceof com.senne.starktech.logic.ServerRackBlockEntity be) {
            be.parts().setItem(0, new ItemStack(ModItems.SERVER_CPU.get()));
            be.parts().setItem(1, new ItemStack(ModItems.SERVER_RAM.get()));
            be.parts().setItem(2, new ItemStack(ModItems.SERVER_SSD.get()));
            be.parts().setItem(5, new ItemStack(ModItems.SERVER_FAN.get()));
        }
        if (level.getBlockEntity(ROBOT_BAY.offset(10, 0, 6)) instanceof com.senne.starktech.security.SecurityCoreBlockEntity core) {
            core.logic().put(null, program("Patrol", BAY_PATROL, com.senne.starktech.logic.program.ProgramKind.ROBOT));
            core.logic().put(null, program("Corners", """
                    corners = 0

                    when message("corner"):
                        corners = corners + 1
                        if corners == 4:
                            say(sender.name + " walked a full round")
                            corners = 0
                    """, com.senne.starktech.logic.program.ProgramKind.SERVER));
        }
    }

    /** Een programma uit deze code, zoals de app Logic hem bewaart. */
    private static com.senne.starktech.logic.program.LogicProgram program(String name, String code,
                                                                       com.senne.starktech.logic.program.ProgramKind kind) {
        return new com.senne.starktech.logic.program.LogicProgram(name, true, com.senne.starktech.logic.program.GraphConverter.toGraph(
                com.senne.starktech.logic.program.ScriptParser.parse(code, kind), null), code, kind);
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
    private static void showBoard(BlockPos pos, Consumer<com.senne.starktech.client.robot.BoardScreen> action) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        for (com.senne.starktech.robot.GuardRobotEntity robot : mc.level.getEntitiesOfClass(
                com.senne.starktech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(pos))) {
            com.senne.starktech.client.robot.BoardScreen board = new com.senne.starktech.client.robot.BoardScreen(robot.getId(), null);
            mc.setScreen(board);
            action.accept(board);
            return;
        }
    }

    /** Doe iets met de robot die op deze plek staat. */
    private static void robotAt(net.minecraft.server.level.ServerPlayer player, BlockPos pos,
                                Consumer<com.senne.starktech.robot.GuardRobotEntity> action) {
        for (com.senne.starktech.robot.GuardRobotEntity robot : player.serverLevel().getEntitiesOfClass(
                com.senne.starktech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(pos))) {
            action.accept(robot);
            return;
        }
    }

    /** Doe iets met de robot die het dichtst bij deze plek staat (binnen 4 blokken). */
    private static void robotNear(net.minecraft.server.level.ServerPlayer player, BlockPos pos,
                                  Consumer<com.senne.starktech.robot.GuardRobotEntity> action) {
        com.senne.starktech.robot.GuardRobotEntity best = null;
        for (com.senne.starktech.robot.GuardRobotEntity robot : player.serverLevel().getEntitiesOfClass(
                com.senne.starktech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(4))) {
            if (best == null || robot.distanceToSqr(Vec3.atCenterOf(pos)) < best.distanceToSqr(Vec3.atCenterOf(pos))) {
                best = robot;
            }
        }
        if (best != null) {
            action.accept(best);
        }
    }

    private static void desktop(Consumer<com.senne.starktech.client.os.StarkOsScreen> action) {
        if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
            action.accept(os);
        }
    }

    /** Een schakelaar van het hele netwerk, zoals de knoppen in Devices en Access. */
    private static void networkSwitch(net.minecraft.server.level.ServerPlayer player, BlockPos core, String name, boolean on) {
        net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
        args.putString("name", name);
        args.putBoolean("on", on);
        com.senne.starktech.security.os.StarkOs.handleAction(player,
                new com.senne.starktech.network.OsActionPayload(core, "flag", args));
    }

    /** Een camera een stukje draaien, zoals de pijltjes bij het grote beeld. */
    private static void aimCamera(net.minecraft.server.level.ServerPlayer player, BlockPos core, BlockPos camera, float pan) {
        net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
        args.putInt("x", camera.getX());
        args.putInt("y", camera.getY());
        args.putInt("z", camera.getZ());
        args.putFloat("pan", pan);
        args.putFloat("tilt", 0f);
        com.senne.starktech.security.os.StarkOs.handleAction(player,
                new com.senne.starktech.network.OsActionPayload(core, "cam_aim", args));
    }

    /**
     * Alle eigen geluiden, één voor één, met hun naam als titel. (Een titel in beeld is Engels,
     * want die zie je in het spel.)
     */
    private static void soundTour(DevScript s) {
        Object[][] sounds = {
                {"Notification", "info, like 'arrived at your destination'", ModSounds.EDITH_NOTIFY},
                {"Warning", "something needs your attention", ModSounds.EDITH_ALERT},
                {"Denied", "access denied or it failed", ModSounds.EDITH_DENIED},
                {"Ore scan", "", ModSounds.EDITH_SCAN},
                {"Menu open", "the G menu", ModSounds.EDITH_MENU_OPEN},
                {"Select", "choosing an option", ModSounds.EDITH_SELECT},
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
                {"Repulsor on", "", ModSounds.REPULSOR_ARM},
                {"Repulsor shot", "", ModSounds.REPULSOR_FIRE},
                {"Repulsor off", "", ModSounds.REPULSOR_DISARM},
                {"Boot", "putting the glasses on", ModSounds.EDITH_BOOT},
                {"Boot step", "loading bar", ModSounds.EDITH_BLIP},
                {"Identity confirmed", "", ModSounds.EDITH_CONFIRM},
        };
        int number = 1;
        for (Object[] sound : sounds) {
            @SuppressWarnings("unchecked")
            java.util.function.Supplier<net.minecraft.sounds.SoundEvent> event =
                    (java.util.function.Supplier<net.minecraft.sounds.SoundEvent>) sound[2];
            s.title(number == 1 ? 10 : 50, number + ". " + sound[0], (String) sound[1])
                    .step(5, () -> EdithClientEvents.playUi(event.get(), 1.0f));
            number++;
        }
        s.pause(60);
    }

    private static Vec3 towerCentre() {
        return new Vec3(STARK_TOWER.getX() + 20.5, STARK_TOWER.getY() + 20, STARK_TOWER.getZ() + 20.5);
    }

    private static Supplier<KeyMapping> key(Supplier<KeyMapping> key) {
        return key;
    }

    /** Kijk de husks één voor één aan en markeer ze, zoals met R. */
    private static void markAll(DevScript s) {
        for (BlockPos target : TARGETS) {
            s.lookAt(10, mob(target)).step(5, EdithClientEvents::markLookTarget);
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

    private static void relay(EdithRedstone.Op op) {
        PacketDistributor.sendToServer(RelayActionPayload.switchOne(0, op));
    }

    private static void holo(HoloActionPayload.Op op, int value) {
        PacketDistributor.sendToServer(new HoloActionPayload(HOLO_TABLE, op, value));
    }

    /** Een knop uit het scherm van de laserzender LASER_A, zoals jij hem indrukt. */
    private static void laser(ServerPlayer player, LaserActionPayload.Op op, int value) {
        com.senne.starktech.shield.LaserEmitters.handleAction(player, new LaserActionPayload(LASER_A, op, value));
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
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.STARK_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.STARK_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.STARK_BOOTS.get()));
    }

    /**
     * Happy heeft zijn bril vrijgegeven ("Transfer ownership") en jij zet hem op. Wie hem als
     * eerste opzet, wordt de nieuwe eigenaar.
     */
    private static void wearFriendsReleasedGlasses(ServerPlayer player) {
        ServerPlayer friend = ShowcaseScene.friend(player);
        if (friend == null) {
            return;
        }
        ItemStack glasses = DevWorld.ownGlasses(friend);
        EdithOwner owner = EdithData.getOwner(glasses);
        if (owner != null) {
            EdithData.setOwner(glasses, owner.withTransferPending(true));
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

    /** Twee Hydra-soldaten een eind voor je, met hun geweer, die jou als doelwit hebben. */
    private static void spawnHydraSquad(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (int side = -1; side <= 1; side += 2) {
            HydraSoldierEntity soldier = com.senne.starktech.registry.ModEntities.HYDRA_SOLDIER.get().create(level);
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
    private static @Nullable StarkDroneEntity groundedDrone() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return null;
        }
        return mc.level.getEntitiesOfClass(StarkDroneEntity.class, mc.player.getBoundingBox().inflate(6),
                drone -> drone.getMode() == StarkDroneEntity.Mode.GROUNDED)
                .stream().findFirst().orElse(null);
    }

    /**
     * Is hij geland? Hij staat dan stil vlak boven de grond. (Op "onGround" kun je niet wachten:
     * een drone zweeft net boven het blok eronder en raakt het nooit echt.)
     */
    private static boolean droneLandedNearYou() {
        Minecraft mc = Minecraft.getInstance();
        StarkDroneEntity drone = groundedDrone();
        return drone != null && mc.player != null && Math.abs(drone.getY() - drone.yOld) < 0.01
                && drone.getY() - mc.player.getY() < 1.5;
    }

    /** Kijk naar de gelande drone en rechtsklik erop, zoals met de muis. */
    private static void rightClickGroundedDrone() {
        Minecraft mc = Minecraft.getInstance();
        StarkDroneEntity drone = groundedDrone();
        if (drone == null || mc.player == null || mc.gameMode == null) {
            return;
        }
        mc.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, drone.position());
        mc.gameMode.interact(mc.player, drone, InteractionHand.MAIN_HAND);
    }

    /** Het laadstation vullen: een bijna lege bril, redstone en drie batterij-upgrades. */
    private static void fillStation(ServerPlayer player) {
        if (player.serverLevel().getBlockEntity(CHARGING_STATION) instanceof ChargingStationBlockEntity station) {
            ItemStack glasses = DevWorld.ownGlasses(player);
            EdithData.setEnergy(glasses, EdithData.getMaxEnergy(glasses) / 5);
            station.setItem(ChargingStationBlockEntity.SLOT_GLASSES, glasses);
            station.setItem(ChargingStationBlockEntity.SLOT_FUEL, new ItemStack(Items.REDSTONE, 16));
            station.setItem(ChargingStationBlockEntity.SLOT_UPGRADE, new ItemStack(ModItems.BATTERY_UPGRADE.get(), 3));
        }
    }

    /**
     * Het creative-scherm, meteen op het tabblad "Stark Tech". Welk tabblad open staat, onthoudt
     * Minecraft in een verborgen veld (selectedTab); dat zetten we vooraf, dan kiest het scherm
     * bij het openen zelf dat tabblad (en de goede pagina).
     */
    private static void openStarkTab() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        try {
            java.lang.reflect.Field selected = CreativeModeInventoryScreen.class.getDeclaredField("selectedTab");
            selected.setAccessible(true);
            selected.set(null, ModCreativeTabs.STARK_TECH.get());
        } catch (ReflectiveOperationException | RuntimeException e) {
            StarkTech.LOGGER.warn("[showcase] kan het tabblad Stark Tech niet kiezen", e);
        }
        mc.setScreen(new CreativeModeInventoryScreen(mc.player, mc.player.connection.enabledFeatures(),
                mc.options.operatorItemsTab().get()));
    }

    /** Happy typt een commando (bijvoorbeeld een privébericht). */
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
                ResourceLocation.fromNamespaceAndPath("starktech", "root"));
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
        EdithKeys.ZOOM.setDown(false);
        RepulsorControl.reset();
        SuitFlight.reset();
        DroneCamera.exit();
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        mc.options.hideGui = false;
    }
}
