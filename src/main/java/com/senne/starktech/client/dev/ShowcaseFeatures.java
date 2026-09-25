package com.senne.starktech.client.dev;

import static com.senne.starktech.client.dev.ShowcaseScene.CHARGING_STATION;
import static com.senne.starktech.client.dev.ShowcaseScene.CHEST;
import static com.senne.starktech.client.dev.ShowcaseScene.COW;
import static com.senne.starktech.client.dev.ShowcaseScene.DOME;
import static com.senne.starktech.client.dev.ShowcaseScene.FAR_TARGET;
import static com.senne.starktech.client.dev.ShowcaseScene.FRIEND_SPOT;
import static com.senne.starktech.client.dev.ShowcaseScene.GRID_CUT;
import static com.senne.starktech.client.dev.ShowcaseScene.HEAT_GENERATOR;
import static com.senne.starktech.client.dev.ShowcaseScene.HOLO_TABLE;
import static com.senne.starktech.client.dev.ShowcaseScene.HOME;
import static com.senne.starktech.client.dev.ShowcaseScene.LAMP;
import static com.senne.starktech.client.dev.ShowcaseScene.LASER_POWER;
import static com.senne.starktech.client.dev.ShowcaseScene.PYRAMID;
import static com.senne.starktech.client.dev.ShowcaseScene.SOLAR_PANEL;
import static com.senne.starktech.client.dev.ShowcaseScene.STARK_TOWER;
import static com.senne.starktech.client.dev.ShowcaseScene.TARGETS;

import com.mojang.blaze3d.platform.InputConstants;
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
import com.senne.starktech.client.RelayListScreen;
import com.senne.starktech.client.RepulsorControl;
import com.senne.starktech.client.SatelliteScreen;
import com.senne.starktech.client.SuitFlight;
import com.senne.starktech.client.voice.VoiceCommandsScreen;
import com.senne.starktech.comms.EdithComms;
import com.senne.starktech.edith.EdithAction;
import com.senne.starktech.holo.HoloMode;
import com.senne.starktech.network.CommsPayload;
import com.senne.starktech.network.HoloActionPayload;
import com.senne.starktech.network.IllusionPlacePayload;
import com.senne.starktech.network.RelayActionPayload;
import com.senne.starktech.network.SatelliteUpgradePayload;
import com.senne.starktech.network.WaypointActionPayload;
import com.senne.starktech.raid.EdithRaids;
import com.senne.starktech.redstone.EdithRedstone;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.registry.ModSounds;
import com.senne.starktech.satellite.SatelliteUpgrade;
import com.senne.starktech.satellite.SatelliteUpgrades;
import com.senne.starktech.shield.ShieldColor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

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

        add("E.D.I.T.H. menu (G)", "Het ronde menu met alle functies, tabblad voor tabblad "
                + "(combat, drones, transport, tech, illusie, scanner, navigatie, systems).", start, 180, 0, s -> {
            s.open(10, EdithMenuScreen::new);
            for (int i = 0; i < 8; i++) {
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
                + "grond, dan een dunne, felle straal. Richt op de verre husk.", range, 180, 0, s -> s
                .onServer(0, ShowcaseFeatures::unlockSatelliteModes)
                .action(5, EdithAction.SATELLITE_PRECISION)
                .lookAt(10, mob(FAR_TARGET))
                .action(10, EdithAction.SATELLITE_STRIKE)
                .pause(220));

        add("Satellite: bombardment", "Dezelfde satelliet, andere stand: een regen van inslagen rond "
                + "het doel.", range, 180, 0, s -> s
                .onServer(0, ShowcaseFeatures::unlockSatelliteModes)
                .action(5, EdithAction.SATELLITE_BOMBARDMENT)
                .lookAt(10, mob(TARGETS.get(1)))
                .action(10, EdithAction.SATELLITE_STRIKE)
                .pause(240));

        add("Satellite: sweep", "De schuivende laser: hij trekt een baan van je af, over de doelwitten.",
                range, 180, 0, s -> s
                .onServer(0, ShowcaseFeatures::unlockSatelliteModes)
                .action(5, EdithAction.SATELLITE_SWEEP)
                .lookAt(10, mob(TARGETS.get(0)))
                .action(10, EdithAction.SATELLITE_STRIKE)
                .pause(260));

        add("Satellite upgrades", "Het satellietscherm (G > Combat > Satellite): links de satelliet met al "
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

        add("Rayshield: personal shield", "Een energieschild om je heen (van buitenaf bekeken).",
                start, 180, 0, s -> s
                .action(10, EdithAction.TOGGLE_SHIELD)
                .camera(10, CameraType.THIRD_PERSON_FRONT)
                .pause(140)
                .camera(0, CameraType.FIRST_PERSON)
                .action(0, EdithAction.TOGGLE_SHIELD));

        add("Rayshield: laser gate", "Twee laserzenders (volle blokken met een lens): met redstone schuift "
                + "er een energiewand tussen, als één rechte wand (---). Zonder redstone schuift hij weer weg. "
                + "Hij draait op stroom van de warmtegenerator ernaast; zonder stroom gaat hij niet aan. "
                + "Rechtsklik op een zender = andere kleur.", new Vec3(13.5, -60, 4.5), 0, 5, s -> s
                .onServer(20, player -> player.serverLevel().setBlockAndUpdate(LASER_POWER, Blocks.REDSTONE_BLOCK.defaultBlockState()))
                .pause(120)
                .onServer(0, player -> player.serverLevel().setBlockAndUpdate(LASER_POWER, Blocks.AIR.defaultBlockState()))
                .pause(80));

        add("Rayshield: dome", "De schildkoepel gaat aan: eerst een straal omhoog, dan vouwt hij bovenin open "
                + "en zakt hij als koepel naar beneden. Een pijl van buiten stuit op de rand (flits, en de "
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
                + "in de grond).", start, -90, 5, s -> s
                .action(10, EdithAction.ILLUSION_ARMY)
                .key(20, () -> Minecraft.getInstance().options.keyJump, true)
                .key(20, () -> Minecraft.getInstance().options.keyJump, false)
                .lookAt(5, Vec3.atCenterOf(PYRAMID))
                .pause(60)
                .step(0, () -> placeIllusion(true))
                .pause(200)
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

        // ---------------------------------------------------------------- Vijanden en wereld
        add("Raid drones", "Vijandige drones (donker, oranje lampjes) duiken uit de lucht, cirkelen "
                + "om je heen en schieten. Ze komen vanzelf als je de bril draagt.", GameType.SURVIVAL,
                start, 180, -20, s -> s
                .onServer(10, player -> EdithRaids.startRaid(player, 3))
                .pause(120)
                .look(0, 0, -10)
                .pause(160));

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

        // ---------------------------------------------------------------- Schermen en overige
        add("Voice commands", "De editor voor spraakcommando's (het woordenboek). De stem zelf moet "
                + "je eerst installeren via G > Systems; dat doet de showcase niet.", start, 180, 0, s -> s
                .open(10, () -> new VoiceCommandsScreen(null))
                .pause(140)
                .close(0));

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
    }

    // ================================================================ Hulpjes

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
