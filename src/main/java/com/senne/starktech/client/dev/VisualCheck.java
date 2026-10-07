package com.senne.starktech.client.dev;

import com.senne.starktech.StarkTech;
import com.senne.starktech.arsenal.ArsenalBalance;
import com.senne.starktech.arsenal.ArsenalServer;
import com.senne.starktech.client.ArsenalScreen;
import com.senne.starktech.edith.EdithAction;
import com.senne.starktech.network.IllusionPlacePayload;
import com.senne.starktech.client.voice.VoiceAction;
import com.senne.starktech.client.voice.VoiceCommandStore;
import com.senne.starktech.client.voice.VoiceDictionary;
import com.senne.starktech.shield.LaserEmitterBlock;
import com.senne.starktech.shield.ShieldColor;
import net.minecraft.core.Direction;
import com.senne.starktech.client.SatelliteScreen;
import com.senne.starktech.satellite.SatelliteUpgrade;
import com.senne.starktech.satellite.SatelliteUpgrades;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import com.senne.starktech.client.CommsScreen;
import com.senne.starktech.client.DomeScreen;
import com.senne.starktech.client.EdithMenuScreen;
import com.senne.starktech.client.HoloTableScreen;
import com.senne.starktech.client.MusicScreen;
import com.senne.starktech.client.NavigationScreen;
import com.senne.starktech.client.RelayListScreen;
import com.senne.starktech.client.RepulsorControl;
import com.senne.starktech.client.SpyReportScreen;
import com.senne.starktech.client.WaypointEditScreen;
import com.senne.starktech.client.voice.VoiceCommandsScreen;
import com.senne.starktech.holo.HoloMode;
import com.senne.starktech.holo.HoloTableBlockEntity;
import com.senne.starktech.nav.Waypoint;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModDataComponents;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.registry.ModItems;
import com.senne.starktech.shield.DomeGeneratorBlockEntity;
import com.senne.starktech.shield.DomeSettings;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Een rondleiding langs alles wat de automatische tests niet kunnen zien, met een screenshot
 * van elke stop. Alleen voor tijdens het ontwikkelen.
 *
 * Starten: {@code gradlew runVisualCheck}. Het spel maakt dan zelf een platte testwereld,
 * zet er een holotafel, een heuvel, een paar dieren en drones in, doet jou de bril en het
 * harnas aan, opent alle schermen één voor één en sluit daarna vanzelf af. De plaatjes staan
 * in {@code run-visual/screenshots}.
 *
 * In een normaal spel doet deze klasse niets: hij staat alleen aan als het spel gestart is
 * met {@code -Dstarktech.visualcheck=true}, en dat doet alleen die ene Gradle-taak.
 */
public final class VisualCheck {
    private VisualCheck() {}

    private static final boolean ENABLED = Boolean.getBoolean("starktech.visualcheck");
    /**
     * Alleen een paar stukken van de rondleiding, bijvoorbeeld "satellite" of "holo,screens"
     * (gradlew runVisualCheck -Ponly=satellite). Leeg = alles.
     */
    private static final Set<String> ONLY = Arrays.stream(System.getProperty("starktech.visualcheck.only", "").split(","))
            .map(String::trim).filter(part -> !part.isEmpty()).collect(Collectors.toSet());
    private static final String WORLD = "VisualCheck";

    /** Waar alles staat in de platte wereld (de grond is daar y = -61, je staat op -60). */
    private static final BlockPos TABLE = new BlockPos(0, -60, 0);
    private static final BlockPos HILL = new BlockPos(14, -60, 10);
    private static final BlockPos PIT = new BlockPos(-14, -61, -8);
    private static final BlockPos TOWER = new BlockPos(-10, -60, 14);
    private static final BlockPos ENEMY = new BlockPos(6, -60, -9);
    private static final BlockPos ANIMAL = new BlockPos(-6, -60, 7);

    private enum Stage { START, LOADING, TOUR, DONE }

    private static final DevScript TOUR = new DevScript();
    private static Stage stage = Stage.START;
    private static int ticksInWorld;

    /** Wordt aangeroepen bij het opstarten; doet niets tenzij de rondleiding aan staat. */
    public static void registerIfEnabled() {
        if (!ENABLED) {
            return;
        }
        StarkTech.LOGGER.info("[visual check] rondleiding staat aan");
        buildTour();
        NeoForge.EVENT_BUS.addListener(VisualCheck::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        switch (stage) {
            case START -> {
                if (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen) {
                    DevWorld.quietOptions(mc, true);
                    DevWorld.createFlatWorld(mc, WORLD);
                    stage = Stage.LOADING;
                }
            }
            case LOADING -> {
                if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && mc.screen == null) {
                    if (++ticksInWorld > 60) {
                        stage = Stage.TOUR;
                    }
                }
            }
            case TOUR -> {
                if (TOUR.tick()) {
                    stage = Stage.DONE;
                    StarkTech.LOGGER.info("[visual check] klaar, spel sluit af");
                    mc.stop();
                }
            }
            case DONE -> {
            }
        }
    }

    // ================================================================ De rondleiding

    private static void buildTour() {
        DevScript t = TOUR;
        // Klaarzetten: de wereld inrichten en de bril opzetten. Het opstarten van de bril
        // (het "boot"-scherm) duurt een paar seconden, dus daarna even wachten.
        t.step(0, () -> {
            DevWorld.command("gamerule doDaylightCycle false");
            DevWorld.command("gamerule doWeatherCycle false");
            DevWorld.command("gamerule doMobSpawning false");
            DevWorld.command("gamerule starktechRaidGriefing false");
            DevWorld.command("time set noon");
            DevWorld.command("weather clear");
            // Een platte wereld zit vol slijmen; die vullen de kaart met rode stipjes.
            DevWorld.command("kill @e[type=minecraft:slime]");
            DevWorld.onServer(VisualCheck::buildScene);
        });
        // --- De opstartanimatie van de bril (die begint zodra de bril op is)
        if (want("boot")) {
            // Logo, laadbalk, identiteitscontrole, bevestigd met welkomstzin, ONLINE.
            t.shot(24, "00_boot_a");
            t.shot(20, "00_boot_b");
            t.shot(26, "00_boot_c");
            t.shot(22, "00_boot_d");
            t.shot(14, "00_boot_e");
        } else {
            t.pause(100); // de bril moet toch even opstarten
        }

        // --- De holotafel
        if (want("holo")) {
            t.view(20, 0.5, -60, -3.2, 0, 22);
            t.shot(200, "01_holo_map");
            t.open(0, () -> new HoloTableScreen(TABLE));
            t.shot(20, "02_holo_screen");
            t.close(0);
            onTable(t, table -> table.setBlueprint(new ItemStack(ModItems.CHARGING_STATION.get())));
            t.shot(30, "03_holo_blueprint");
            t.open(0, () -> new HoloTableScreen(TABLE));
            t.shot(20, "04_holo_screen_blueprint");
            t.close(0);
            onTable(t, table -> table.setMode(HoloMode.ARSENAL));
            t.shot(30, "05_holo_arsenal");
            // Ingezoomd (zonder HUD), om de drones te kunnen zien.
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.step(0, () -> Minecraft.getInstance().options.fov().set(30));
            t.shot(10, "05a_holo_arsenal_close");
            onTable(t, table -> table.setMode(HoloMode.SATELLITE));
            t.view(0, 0.5, -60, -3.2, 0, 4); // de satelliet hangt hoger: iets omhoog kijken
            t.shot(30, "05b_holo_satellite_close");
            t.step(0, () -> Minecraft.getInstance().options.fov().set(70));
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.view(0, 0.5, -60, -3.2, 0, 22);
            t.shot(10, "05b_holo_satellite");
            t.onServer(0, player -> {
                SatelliteUpgrades upgrades = SatelliteUpgrades.START;
                for (SatelliteUpgrade upgrade : SatelliteUpgrade.values()) {
                    upgrades = upgrades.with(upgrade, SatelliteUpgrade.MAX_LEVEL);
                }
                player.setData(ModAttachments.SATELLITE, upgrades);
            });
            t.shot(30, "05c_holo_satellite_max");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.step(0, () -> Minecraft.getInstance().options.fov().set(30));
            t.view(0, 0.5, -60, -3.2, 0, 4);
            t.shot(10, "05d_holo_satellite_max_close");
            t.view(0, 0.5, -60, -3.2, 0, 22);
            t.step(0, () -> Minecraft.getInstance().options.fov().set(70));
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.onServer(0, player -> player.setData(ModAttachments.SATELLITE, SatelliteUpgrades.START));
            // De ertsenstand: eerst wat ertsen onder de tafel leggen (de platte wereld heeft er
            // geen), en lang genoeg wachten tot de tafel opnieuw heeft gekeken.
            t.onServer(0, player -> oresUnder(player.serverLevel(), TABLE));
            onTable(t, table -> table.setMode(HoloMode.ORES));
            t.shot(110, "05e_holo_ores");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.step(0, () -> Minecraft.getInstance().options.fov().set(40));
            t.shot(10, "05f_holo_ores_close");
            t.step(0, () -> Minecraft.getInstance().options.fov().set(70));
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.open(0, () -> new HoloTableScreen(TABLE));
            t.shot(20, "05g_holo_screen_ores");
            t.close(0);
            onTable(t, table -> {
                table.setMode(HoloMode.MAP);
                table.setScale(2);
                table.setRange(3);
            });
            t.shot(80, "06_holo_map_big_far");
            onTable(t, table -> {
                table.setScale(0);
                table.setRange(0);
            });
            t.shot(80, "07_holo_map_small_near");
            onTable(t, table -> {
                table.setScale(1);
                table.setRange(1);
            });
            t.command(0, "time set midnight");
            t.shot(80, "08_holo_night");
            t.command(0, "time set noon");
            t.view(0, 0.5, -57.2, -2.2, 0, 55);
            t.shot(40, "09_holo_from_above");
            t.view(0, 3.2, -60, 2.8, 130, 18);
            t.shot(40, "10_holo_side");
            // Zonder bril: de kaart werkt (zonder waypoints), arsenaal en satelliet niet.
            t.onServer(0, player -> player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY));
            t.view(0, 0.5, -60, -3.2, 0, 22);
            t.shot(40, "11_holo_map_no_glasses");
            onTable(t, table -> table.setMode(HoloMode.ARSENAL));
            t.shot(30, "11a_holo_arsenal_no_glasses");
            t.open(0, () -> new HoloTableScreen(TABLE));
            t.shot(20, "11b_holo_screen_no_glasses");
            t.close(0);
            onTable(t, table -> table.setMode(HoloMode.MAP));
            t.onServer(0, DevWorld::wearGlasses);
        }

        // --- De Hydra-bunker in een (zelfgebouwde) berg: van buiten, en dan kamer voor kamer.
        // De ingang staat op (20, -60, 100) en kijkt naar het noorden; de bunker loopt naar het
        // zuiden. Een plek in het bestand (x, y, z) ligt in de wereld op (3 + x, -61 + y, 100 + z).
        if (want("bunker")) {
            t.onServer(0, player -> DevWorld.placeHydraBunker(player.serverLevel(), new BlockPos(20, -60, 100)));
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.view(40, 20.5, -52, 76, 0, 12);
            t.shot(20, "b01_bunker_outside");
            t.view(0, 20.5, -59, 96.5, 0, 8);
            t.shot(15, "b02_bunker_entrance");
            t.view(0, 20.5, -59, 106.5, 0, 5);
            t.shot(15, "b03_bunker_corridor");
            t.view(0, 20.5, -57.5, 115.5, 0, 12);
            t.shot(15, "b04_bunker_hall");
            t.view(0, 20.5, -57.5, 127.5, 180, 12);
            t.shot(15, "b05_bunker_hall_back");
            t.view(0, 30.5, -58.5, 117.5, 0, 25);
            t.shot(15, "b06_bunker_reactor");
            t.view(0, 20.5, -59, 130.5, 0, 5);
            t.shot(15, "b07_bunker_lasers");
            t.view(0, 20.5, -57.5, 139.5, 0, 20);
            t.shot(15, "b08_bunker_vault");
            t.view(0, 6.5, -58.5, 122.5, 180, 20);
            t.shot(15, "b09_bunker_barracks");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        }

        // --- De beveiliging van de Stark Tower: een kern met twee torentjes, en de drones die hij
        // de lucht in stuurt. Eerst van niemand (rood), dan "gehackt" door jou (groen en blauw).
        if (want("security")) {
            BlockPos core = new BlockPos(-40, -60, 60);
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState()
                        .setValue(com.senne.starktech.security.SecurityCoreBlock.FACING, net.minecraft.core.Direction.NORTH));
                level.setBlockAndUpdate(core.offset(-4, 0, 0), ModBlocks.SECURITY_TURRET.get().defaultBlockState());
                level.setBlockAndUpdate(core.offset(4, 0, 0), ModBlocks.SECURITY_TURRET.get().defaultBlockState());
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.view(20, -39.5, -58.5, 52.5, 0, 12);
            t.shot(200, "s01_security_unclaimed");
            t.view(0, -39.5, -59.4, 57.8, 0, 10);
            t.shot(10, "s02_security_core");
            t.onServer(0, player -> {
                if (player.serverLevel().getBlockEntity(core) instanceof com.senne.starktech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                }
            });
            t.view(0, -39.5, -58.5, 52.5, 0, 12);
            t.shot(40, "s03_security_claimed");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        }

        // --- De laptop-hack: een tweede kern (van niemand), de laptop erop, en de terminal die
        // zichzelf speelt. Halverwege Esc: de laptop staat op de kern en weet nog alles.
        if (want("hack")) {
            BlockPos core = new BlockPos(-60, -60, 60);
            java.util.function.Predicate<String> said = text -> Minecraft.getInstance().screen
                    instanceof com.senne.starktech.client.HackScreen hack
                    && hack.terminal().output().stream().anyMatch(line -> line.text().startsWith(text));
            t.onServer(0, player -> {
                player.serverLevel().setBlockAndUpdate(core.above(), net.minecraft.world.level.block.Blocks.AIR
                        .defaultBlockState());
                player.serverLevel().setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState());
            });
            t.view(20, -59.5, -59.5, 57.5, 0, 10);
            t.step(0, com.senne.starktech.client.HackScreen::startAutoPlay);
            t.onServer(0, player -> com.senne.starktech.security.TowerHack.placeLaptop(player, core,
                    new ItemStack(ModItems.STARK_LAPTOP.get())));
            t.shot(20, "k01_hack_start");
            t.shot(220, "k02_hack_recon");
            t.waitUntil(600, () -> Minecraft.getInstance().screen instanceof com.senne.starktech.client.HackScreen hack
                    && hack.terminal().stage() == com.senne.starktech.security.HackTerminal.Stage.LOGIN);
            // Esc: alleen het scherm gaat dicht.
            t.close(10);
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.shot(10, "k025_hack_laptop_on_core");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.onServer(0, player -> com.senne.starktech.security.TowerHack.open(player, core.above()));
            t.shot(15, "k026_hack_restored");
            t.waitUntil(600, () -> said.test("Still possible"));
            t.shot(5, "k03_hack_login");
            t.waitUntil(600, () -> said.test("- Tony"));
            t.shot(5, "k035_hack_readme");
            t.shot(70, "k04_hack_shell");
            t.waitUntil(900, () -> Minecraft.getInstance().screen instanceof com.senne.starktech.client.HackScreen hack
                    && hack.terminal().output().stream().anyMatch(line -> line.text().startsWith("SECURITY DAEMON")));
            t.shot(3, "k05_hack_takeover");
            t.waitUntil(900, () -> !(Minecraft.getInstance().screen instanceof com.senne.starktech.client.HackScreen));
            t.step(0, () -> com.senne.starktech.client.HackScreen.autoPlay = false);
            t.shot(40, "k06_hack_core_green");
        }

        // --- De Stark OS-desktop op een eigen kern: het Files-venster (met een verstopt
        // security.conf en een valstrik), het rechtsklik-menu, en een notitie in Notes.
        if (want("os")) {
            BlockPos core = new BlockPos(-100, -60, 40);
            java.util.function.Supplier<String> home = () -> "/home/" + com.senne.starktech.security.os.SystemDefaults
                    .homeName(Minecraft.getInstance().getUser().getName());
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState());
                if (level.getBlockEntity(core) instanceof com.senne.starktech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    be.ensureSystem(level);
                    String dir = "/home/" + com.senne.starktech.security.os.SystemDefaults.homeName(player.getGameProfile().getName());
                    var fs = be.fs();
                    fs.mkdir(dir + "/secret");
                    fs.write(dir + "/secret/passwords.txt", "nice try");
                    fs.setTrap(dir + "/secret/passwords.txt", true);
                    fs.move("/etc/security.conf", dir + "/secret/recipes.txt");
                    fs.write(dir + "/todo.txt", "- buy more turrets\n- ask Happy about the coffee machine\n"
                            + "- hide security.conf (done: it's a recipe now)\n- set a trap for whoever reads this");
                    be.addTrusted(UUID.randomUUID(), "Pepper");
                    be.addTrusted(UUID.randomUUID(), "Happy");
                }
                // Stroom voor de kern (dan stuurt hij drones), en twee torentjes in zijn gebied.
                level.setBlockAndUpdate(core.below(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                if (level.getBlockEntity(core.below()) instanceof com.senne.starktech.station.ChargerBlockEntity generator) {
                    generator.fill(20_000);
                }
                level.setBlockAndUpdate(core.offset(4, 0, 0), ModBlocks.SECURITY_TURRET.get().defaultBlockState());
                level.setBlockAndUpdate(core.offset(-4, 0, 0), ModBlocks.SECURITY_TURRET.get().defaultBlockState());
            });
            t.stand(10, -100.5, -60, 37.5, 0, 20);
            t.onServer(5, player -> com.senne.starktech.security.os.StarkOs.open(player, core));
            t.shot(20, "o01_os_desktop");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourFiles(home.get() + "/secret", home.get() + "/secret/passwords.txt", false);
                }
            });
            t.shot(10, "o02_os_files");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourFiles(home.get() + "/secret", home.get() + "/secret/passwords.txt", true);
                }
            });
            t.shot(10, "o03_os_files_menu");
            t.onServer(0, player -> {
                net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
                args.putString("path", "/home/" + com.senne.starktech.security.os.SystemDefaults
                        .homeName(player.getGameProfile().getName()) + "/todo.txt");
                com.senne.starktech.security.os.StarkOs.handleAction(player,
                        new com.senne.starktech.network.OsActionPayload(core, "fs_open", args));
            });
            t.shot(15, "o04_os_notes");
            t.step(40, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourOpen(com.senne.starktech.client.os.StarkOsScreen.App.DEVICES);
                }
            });
            t.shot(10, "o05_os_devices");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourOpen(com.senne.starktech.client.os.StarkOsScreen.App.ACCESS);
                }
            });
            t.shot(10, "o06_os_access");
            t.close(10);
        }

        // --- De bewakingscamera's en de bewakingsmuur. Een dichte kamer op een "verdieping" boven de
        // grond, ACHTER je (die heeft je spel dus nooit getekend): de camera erin moet hem toch laten
        // zien. Plus een camera aan een paaltje die naar jou kijkt, en een aan een plafond.
        if (want("cameras")) {
            BlockPos core = new BlockPos(-140, -60, 43);
            BlockPos roomCam = new BlockPos(-135, -53, 48);
            BlockPos postCam = new BlockPos(-140, -58, 46);
            BlockPos ceilingCam = new BlockPos(-131, -57, 44);
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                // De kamer: binnen x -135..-129, y -55..-52, z 45..51, met wanden van steen.
                for (int x = -136; x <= -128; x++) {
                    for (int y = -56; y <= -51; y++) {
                        for (int z = 44; z <= 52; z++) {
                            boolean wall = x == -136 || x == -128 || y == -56 || y == -51 || z == 44 || z == 52;
                            level.setBlockAndUpdate(new BlockPos(x, y, z), wall
                                    ? Blocks.SMOOTH_STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                        }
                    }
                }
                // Licht, een gekleurde muur, een kist en een torentje om naar te kijken.
                level.setBlockAndUpdate(new BlockPos(-132, -51, 48), Blocks.GLOWSTONE.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(-130, -51, 46), Blocks.GLOWSTONE.defaultBlockState());
                net.minecraft.world.level.block.Block[] wool = {Blocks.RED_WOOL, Blocks.ORANGE_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.LIGHT_BLUE_WOOL,
                        Blocks.BLUE_WOOL, Blocks.PURPLE_WOOL};
                for (int z = 45; z <= 51; z++) {
                    for (int y = -55; y <= -52; y++) {
                        level.setBlockAndUpdate(new BlockPos(-128, y, z), wool[z - 45].defaultBlockState());
                    }
                }
                level.setBlockAndUpdate(new BlockPos(-129, -55, 46), Blocks.CHEST.defaultBlockState());
                level.setBlockAndUpdate(new BlockPos(-130, -55, 50), ModBlocks.SECURITY_TURRET.get().defaultBlockState());
                var cow = EntityType.COW.create(level);
                if (cow != null) {
                    cow.moveTo(-131.5, -55, 48.5, 90, 0);
                    cow.setNoAi(true);
                    level.addFreshEntity(cow);
                }
                // De camera's, elk met een warmtegenerator ernaast (stroom).
                placeCamera(level, roomCam, net.minecraft.world.level.block.state.properties.AttachFace.WALL, Direction.EAST,
                        roomCam.below(), "Upstairs");
                level.setBlockAndUpdate(postCam.south(), Blocks.POLISHED_ANDESITE.defaultBlockState());
                level.setBlockAndUpdate(postCam.south().below(), Blocks.POLISHED_ANDESITE.defaultBlockState());
                placeCamera(level, postCam, net.minecraft.world.level.block.state.properties.AttachFace.WALL, Direction.NORTH,
                        postCam.below(), "Courtyard");
                placeCamera(level, ceilingCam, net.minecraft.world.level.block.state.properties.AttachFace.CEILING, Direction.WEST,
                        ceilingCam.east(), "");
                // De kern (van jou, met een wachtwoord, en stroom).
                level.setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState());
                level.setBlockAndUpdate(core.below(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                if (level.getBlockEntity(core.below()) instanceof com.senne.starktech.station.ChargerBlockEntity generator) {
                    generator.fill(20_000);
                }
                if (level.getBlockEntity(core) instanceof com.senne.starktech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    be.ensureSystem(level);
                    be.setPassword("jarvis1", level.random);
                }
            });
            // Jij staat er, met je rug naar de kamer (die ligt in het zuiden; jij kijkt naar het noorden).
            t.stand(10, -139.5, -60, 40.5, 180, 0);
            t.onServer(40, player -> {
                for (BlockPos cam : List.of(roomCam, postCam, ceilingCam)) {
                    if (player.serverLevel().getBlockEntity(cam) instanceof com.senne.starktech.security.CameraBlockEntity be) {
                        be.updateOnline(player.serverLevel(), cam, player.serverLevel().getBlockState(cam));
                    }
                }
                com.senne.starktech.security.os.StarkOs.open(player, core);
            });
            t.step(5, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourCameras(null);
                }
            });
            t.shot(80, "c01_cameras_wall");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourCameras(roomCam);
                }
            });
            t.shot(60, "c02_camera_upstairs_big");
            t.onServer(0, player -> {
                net.minecraft.nbt.CompoundTag aim = new net.minecraft.nbt.CompoundTag();
                aim.putInt("x", roomCam.getX());
                aim.putInt("y", roomCam.getY());
                aim.putInt("z", roomCam.getZ());
                aim.putFloat("pan", 20f);
                aim.putFloat("tilt", -10f);
                com.senne.starktech.security.os.StarkOs.handleAction(player,
                        new com.senne.starktech.network.OsActionPayload(core, "cam_aim", aim));
            });
            t.shot(40, "c03_camera_upstairs_turned");
            // Een andere naam geven: op Rename klikken, typen, en Enter.
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourRenameCamera(roomCam, "Server room");
                }
            });
            t.shot(20, "c03b_camera_rename");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.keyPressed(257, 0, 0); // Enter
                }
            });
            t.shot(20, "c03c_camera_renamed");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourCameras(postCam);
                }
            });
            t.shot(60, "c04_camera_courtyard_big");
            t.onServer(0, player -> {
                net.minecraft.nbt.CompoundTag off = new net.minecraft.nbt.CompoundTag();
                off.putString("name", "cameras");
                off.putBoolean("on", false);
                com.senne.starktech.security.os.StarkOs.handleAction(player,
                        new com.senne.starktech.network.OsActionPayload(core, "flag", off));
            });
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourCameras(null);
                }
            });
            t.shot(20, "c05_cameras_switched_off");
            t.onServer(0, player -> {
                net.minecraft.nbt.CompoundTag on = new net.minecraft.nbt.CompoundTag();
                on.putString("name", "cameras");
                on.putBoolean("on", true);
                com.senne.starktech.security.os.StarkOs.handleAction(player,
                        new com.senne.starktech.network.OsActionPayload(core, "flag", on));
            });
            t.close(10);
            // De camera aan het paaltje, van dichtbij (zijn lampje brandt weer).
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.view(10, -138.4, -58.6, 43.6, 22, 12);
            t.shot(10, "c06_camera_block");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        }

        // --- De harnas-werkbank: het hologram, erop klikken om te bouwen (het stuk vliegt naar je
        // toe), en laarzen erop leggen en er een upgrade in bouwen. Met echte rechtsklikken.
        if (want("workbench")) {
            BlockPos bench = new BlockPos(-80, -60, 60);
            Vec3 chest = new Vec3(-79.5, -57.8, 60.5);
            Vec3 boots = new Vec3(-79.5, -58.8, 60.5);
            Runnable click = () -> KeyMapping.click(Minecraft.getInstance().options.keyUse.getKey());
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(bench, ModBlocks.SUIT_WORKBENCH.get().defaultBlockState());
                level.setBlockAndUpdate(bench.east(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                if (level.getBlockEntity(bench.east()) instanceof com.senne.starktech.station.ChargerBlockEntity generator) {
                    generator.fill(20_000);
                }
                player.getInventory().clearContent();
                player.getInventory().setItem(1, new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT, 8));
                player.getInventory().setItem(2, new ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD, 2));
                player.getInventory().setItem(3, new ItemStack(ModItems.STARK_CHIP.get(), 3));
                player.getInventory().selected = 0;
            });
            // In survival (om te zien wat het kost), maar zonder slijmen die je wegduwen.
            t.command(0, "difficulty peaceful");
            t.command(0, "gamemode survival @a");
            t.close(10);
            t.stand(0, -77.8, -60, 58.2, 50, 15);
            t.lookAt(10, new Vec3(-79.5, -58.6, 60.5));
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.shot(20, "w01_workbench_hologram");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.stand(0, -79.5, -60, 57.6, 0, 0);
            t.lookAt(10, chest);
            t.shot(10, "w02_workbench_hover_chest");
            t.step(0, click);
            t.shot(80, "w03_workbench_building");
            t.camera(0, CameraType.THIRD_PERSON_BACK);
            t.waitUntil(400, () -> Minecraft.getInstance().level.getBlockEntity(bench)
                    instanceof com.senne.starktech.workbench.SuitWorkbenchBlockEntity be && !be.flying().isEmpty());
            t.shot(7, "w04_workbench_flying");
            t.shot(30, "w05_workbench_wearing");
            t.camera(0, CameraType.FIRST_PERSON);
            t.onServer(0, player -> player.getInventory().setItem(0, new ItemStack(ModItems.STARK_BOOTS.get())));
            t.lookAt(10, boots);
            t.shot(5, "w06_workbench_hover_lay");
            t.step(0, click);
            t.onServer(10, player -> player.getInventory().setItem(0, new ItemStack(ModItems.THRUSTER_UPGRADE.get())));
            t.shot(10, "w07_workbench_hover_install");
            t.step(0, click);
            t.shot(40, "w08_workbench_installing");
            t.onServer(80, player -> player.getInventory().setItem(0, ItemStack.EMPTY));
            t.shot(10, "w09_workbench_upgraded");
            t.command(0, "gamemode creative @a");
            t.command(0, "difficulty easy");
        }

        // --- Het AI-brein (lokaal model): zinnen alsof je ze zei, en wat ze ermee doet (zie de log:
        // "E.D.I.T.H. brain"). Het model moet in run-visual/starktech/voice/brain_medium staan.
        if (want("brain")) {
            t.onServer(0, player -> {
                DevWorld.wearGlasses(player);
                DevWorld.refill(player);
                com.senne.starktech.client.voice.EdithMemory.forgetAll();
                DevWorld.spawnStill(player.serverLevel(), EntityType.ZOMBIE, new BlockPos(0, -60, -8), 0);
            });
            t.step(0, () -> {
                com.senne.starktech.client.EdithClientConfig.BRAIN_MODEL.set("medium");
                com.senne.starktech.client.voice.EdithBrain.setMode(com.senne.starktech.client.voice.BrainMode.LOCAL);
            });
            t.view(20, 0.5, -60, 0.5, 180, 5);
            String[] said = {
                    "hoeveel energie heb ik nog",
                    "zet nachtzicht aan en scan naar ertsen",
                    "onthoud dat mijn basis Stark Hoeve heet",
                    "hoe heet mijn basis?",
                    "doe iets met de ding",
                    "send the drones after that zombie",
                    "what time is it and is it going to rain",
                    "sla deze plek op als kampvuur",
            };
            for (int i = 0; i < said.length; i++) {
                String text = said[i];
                t.step(0, () -> com.senne.starktech.client.voice.VoiceControl.hear(text));
                t.pause(2);
                t.waitUntil(1200, () -> !com.senne.starktech.client.voice.EdithBrain.isThinking());
                t.shot(10, String.format("b%02d_brain", i + 1));
            }
            // Uit zichzelf praten: je leven zakt onder de drie hartjes, en ze zegt er iets van.
            t.step(0, () -> {
                com.senne.starktech.client.EdithClientConfig.PROACTIVE.set(true);
                com.senne.starktech.client.voice.EdithMemory.clearConversation();
            });
            t.command(0, "gamemode survival @a");
            t.onServer(20, player -> player.setHealth(4.0f));
            t.pause(40);
            t.waitUntil(1200, () -> !com.senne.starktech.client.voice.EdithBrain.isThinking());
            t.shot(30, "b08b_brain_speaks_up");
            t.step(0, () -> com.senne.starktech.client.EdithClientConfig.PROACTIVE.set(false));
            t.command(0, "gamemode creative @a");
            t.onServer(0, player -> player.setHealth(player.getMaxHealth()));
            // Claude met een verkeerde sleutel: ze moet zeggen dat de sleutel niet klopt en het
            // woordenboek gebruiken (zo zien we dat de SDK in het spel werkt, zonder kosten).
            t.step(0, () -> {
                try {
                    com.senne.starktech.client.voice.EdithBrain.saveClaudeKey("sk-ant-api03-not-a-real-key-for-testing");
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
                com.senne.starktech.client.voice.EdithBrain.choose(com.senne.starktech.client.voice.BrainMode.CLAUDE);
            });
            t.step(20, () -> com.senne.starktech.client.voice.VoiceControl.hear("night vision on"));
            t.pause(2);
            t.waitUntil(1200, () -> !com.senne.starktech.client.voice.EdithBrain.isThinking());
            t.shot(20, "b09_brain_claude_bad_key");
            t.step(0, () -> {
                try {
                    com.senne.starktech.client.voice.EdithBrain.saveClaudeKey("");
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
                com.senne.starktech.client.voice.EdithBrain.setMode(com.senne.starktech.client.voice.BrainMode.DICTIONARY);
            });
            t.open(10, com.senne.starktech.client.BrainScreen::new);
            t.shot(20, "b10_brain_screen");
            t.close(0);
        }

        // --- Drone-docks: drie drones uit je arsenaal (één beschadigd), één vertrekt en komt terug.
        if (want("docks")) {
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                player.setData(com.senne.starktech.registry.ModAttachments.ARSENAL,
                        com.senne.starktech.arsenal.EdithArsenal.EMPTY);
                for (int i = 0; i < 3; i++) {
                    java.util.UUID id = com.senne.starktech.arsenal.ArsenalServer.add(player);
                    if (i == 1) {
                        com.senne.starktech.arsenal.ArsenalServer.returnToOrbit(player, id, 4.0f);
                    }
                }
                level.setBlockAndUpdate(new BlockPos(-85, -60, 66), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                if (level.getBlockEntity(new BlockPos(-85, -60, 66)) instanceof com.senne.starktech.station.ChargerBlockEntity generator) {
                    generator.fill(20_000);
                }
                for (int x = -84; x <= -82; x++) {
                    BlockPos pos = new BlockPos(x, -60, 66);
                    level.setBlockAndUpdate(pos, ModBlocks.DRONE_DOCK.get().defaultBlockState()
                            .setValue(com.senne.starktech.arsenal.DroneDockBlock.FACING, net.minecraft.core.Direction.NORTH));
                    if (level.getBlockEntity(pos) instanceof com.senne.starktech.arsenal.DroneDockBlockEntity dock) {
                        dock.setOwner(player.getUUID());
                    }
                }
            });
            t.close(10);
            t.view(0, -82.5, -59, 62.5, 0, 25);
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.shot(40, "d01_docks");
            // Eén drone gaat op pad (zoals bij een zwerm): hij stijgt op, en daarna een hologram.
            t.onServer(0, player -> com.senne.starktech.arsenal.ArsenalServer.checkOut(player, 1));
            t.shot(16, "d02_docks_liftoff");
            t.shot(60, "d03_docks_away");
            t.onServer(0, player -> {
                for (com.senne.starktech.arsenal.ArsenalDrone drone : com.senne.starktech.arsenal.ArsenalServer.get(player).drones()) {
                    if (drone.state() == com.senne.starktech.arsenal.ArsenalDrone.State.DEPLOYED) {
                        com.senne.starktech.arsenal.ArsenalServer.returnToOrbit(player, drone.id(), drone.health());
                    }
                }
            });
            t.shot(18, "d04_docks_landing");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.open(20, ArsenalScreen::new);
            t.shot(20, "d05_docks_arsenal");
            t.close(0);
        }

        // --- Het landingsplatform op de Stark Tower: van buiten, van onderen, en de HUD tijdens het vliegen.
        if (want("pad")) {
            int x = STARK_TOWER.getX();
            int y = STARK_TOWER.getY();
            int z = STARK_TOWER.getZ();
            Vec3 pad = new Vec3(x + 35.5, y + 100, z + 20.5);
            t.view(0, x + 20.5, y + 40, z - 40, 0, 0);
            t.command(60, "place template starktech:stark_tower " + x + " " + y + " " + z);
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.view(40, x + 62, y + 108, z + 2, 0, 0);
            t.lookAt(2, pad);
            t.shot(30, "p01_pad_outside");
            t.view(0, x + 46, y + 90, z + 8, 0, 0);
            t.lookAt(2, pad);
            t.shot(30, "p02_pad_below");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            // Vliegen: bril en harnas aan, het platform op je lijst, dan opstijgen vanaf het dak.
            t.onServer(0, player -> {
                DevWorld.wearGlasses(player);
                DevWorld.refill(player);
                wearSuit(player);
                com.senne.starktech.suit.LandingPads.add(player, net.minecraft.core.GlobalPos.of(
                        player.serverLevel().dimension(), BlockPos.containing(pad).below()));
            });
            t.command(0, "gamemode survival @a");
            t.stand(10, x + 24.5, y + 100, z + 12.5, 0, 0);
            t.key(40, () -> Minecraft.getInstance().options.keyJump, true);
            t.key(2, () -> Minecraft.getInstance().options.keyJump, false);
            t.key(2, () -> Minecraft.getInstance().options.keyJump, true);
            t.key(30, () -> Minecraft.getInstance().options.keyJump, false);
            t.lookAt(10, pad);
            t.shot(10, "p03_pad_hud_near");
            t.onServer(0, player -> player.teleportTo(player.serverLevel(), x + 110, y + 118, z + 70, 0, 0));
            t.lookAt(10, pad);
            t.shot(20, "p04_pad_hud_far");
            t.command(0, "gamemode creative @a");
        }

        // --- Het G-menu: elk tabblad apart.
        if (want("menu")) {
            t.view(0, 0.5, -60, -3.2, 0, 22);
            t.open(20, EdithMenuScreen::new);
            for (com.senne.starktech.client.MenuTab tab : com.senne.starktech.client.MenuTab.values()) {
                t.step(0, () -> com.senne.starktech.client.EdithClientState.menuTab = tab);
                t.shot(10, String.format("m%02d_menu_%s", tab.ordinal() + 1, tab.name().toLowerCase()));
            }
            t.step(0, () -> com.senne.starktech.client.EdithClientState.menuTab = com.senne.starktech.client.MenuTab.COMBAT);
            t.close(0);
        }

        // --- De schermen
        if (want("screens")) {
            t.view(0, 0.5, -60, -3.2, 0, 22);
            t.open(20, EdithMenuScreen::new);
            t.shot(20, "20_menu");
            t.open(0, ArsenalScreen::new);
            t.shot(20, "21_arsenal");
            t.open(0, NavigationScreen::new);
            t.shot(20, "22_navigation");
            t.open(0, () -> new WaypointEditScreen(0));
            t.shot(20, "23_waypoint_edit");
            t.open(0, CommsScreen::new);
            t.shot(20, "24_comms");
            t.open(0, MusicScreen::new);
            t.shot(20, "25_music");
            t.open(0, RelayListScreen::new);
            t.shot(20, "26_relays");
            t.open(0, () -> new SpyReportScreen(Component.literal("Steve"), List.of(
                    Component.literal("00:30  Steve is at 120, 64, -40"),
                    Component.literal("01:00  Steve mined 3 diamond ore"),
                    Component.literal("01:30  Steve entered a cave"))));
            t.shot(20, "27_spy_report");
            t.open(0, () -> new VoiceCommandsScreen(null));
            t.shot(20, "28_voice_commands");
            t.close(0);
        }

        // --- De HUD, kijkend naar een vijand
        if (want("hud")) {
            t.view(10, 6.5, -60, -4.5, 180, 8);
            t.shot(60, "30_hud_target");
        }

        // --- Het harnas
        if (want("suit")) {
            t.onServer(0, VisualCheck::wearSuit);
            t.view(0, -4.5, -60, -4.5, 225, 10);
            t.camera(0, CameraType.THIRD_PERSON_FRONT);
            t.shot(40, "40_suit_front");
            t.camera(0, CameraType.THIRD_PERSON_BACK);
            t.shot(20, "41_suit_back");
            // Van dichtbij (ingezoomd, zonder HUD): de 3D-stukken van het harnas.
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.step(0, () -> Minecraft.getInstance().options.fov().set(30));
            t.view(0, -4.5, -60, -4.5, 200, 0);
            t.camera(0, CameraType.THIRD_PERSON_FRONT);
            t.shot(10, "41b_suit_close_front");
            t.camera(0, CameraType.THIRD_PERSON_BACK);
            t.shot(10, "41c_suit_close_back");
            t.view(0, -4.5, -60, -4.5, 120, 0);
            t.shot(10, "41d_suit_close_side");
            t.step(0, () -> Minecraft.getInstance().options.fov().set(70));
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.camera(0, CameraType.FIRST_PERSON);

            // --- De repulsor: aanzetten, dan de aanvalsknop "indrukken" zoals een speler dat doet
            t.view(0, 6.5, -60, -4.5, 180, 8);
            t.step(10, RepulsorControl::toggle);
            t.shot(30, "42_repulsor_armed");
            t.tap(0, () -> Minecraft.getInstance().options.keyAttack);
            t.shot(1, "43_repulsor_fired");
            t.shot(3, "43b_repulsor_impact");
            // Hoe zien anderen het? De arm vooruit en een gloeiende handpalm.
            t.camera(0, CameraType.THIRD_PERSON_FRONT);
            t.shot(20, "43c_repulsor_front");
            t.camera(0, CameraType.THIRD_PERSON_BACK);
            t.tap(0, () -> Minecraft.getInstance().options.keyAttack);
            t.shot(2, "43d_repulsor_back_fire");
            t.camera(0, CameraType.FIRST_PERSON);
            t.step(30, RepulsorControl::toggle);
        }

        // --- Vliegen met het harnas. In creative vlieg je gewoon zoals altijd, dus even survival.
        if (want("flight")) {
            t.onServer(0, VisualCheck::wearSuit);
            // We drukken de toetsen in zoals een speler en schrijven bij elke stap de snelheid in de
            // log ("[dev]"), zodat de natuurkunde na te rekenen is.
            t.command(0, "gamemode survival @a");
            t.stand(10, 0.5, -60, -24, 180, -5);
            t.key(40, () -> Minecraft.getInstance().options.keyJump, true);
            t.key(2, () -> Minecraft.getInstance().options.keyJump, false);
            t.key(2, () -> Minecraft.getInstance().options.keyJump, true);   // dubbel tikken: stuwraketten aan
            t.probe(1, "dubbel getikt");
            t.probe(24, "25 ticks spatie");
            t.key(0, () -> Minecraft.getInstance().options.keyJump, false);
            t.probe(10, "10 ticks los");
            t.probe(20, "30 ticks los");
            t.camera(0, CameraType.THIRD_PERSON_BACK);
            // Zonder HUD (zoals F1): anders vallen de vlammen onder je voeten achter de hartjes.
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.shot(10, "47_flight_jets");
            t.camera(0, CameraType.THIRD_PERSON_FRONT);
            t.shot(5, "47b_flight_jets_front");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.camera(0, CameraType.FIRST_PERSON);
            t.key(0, () -> Minecraft.getInstance().options.keyUp, true);
            t.key(0, () -> Minecraft.getInstance().options.keySprint, true);
            t.probe(10, "boost 10");
            t.probe(10, "boost 20");
            t.probe(10, "boost 30");
            t.probe(10, "boost 40");
            t.shot(0, "44_flight_boost");
            // Van buitenaf bij volle vaart: helt hij voorover, met de benen recht (FlightPose)?
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.camera(0, CameraType.THIRD_PERSON_FRONT);
            t.shot(5, "44b_flight_pose_front");
            t.camera(0, CameraType.THIRD_PERSON_BACK);
            t.shot(5, "44c_flight_pose_back");
            t.camera(0, CameraType.FIRST_PERSON);
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.key(0, () -> Minecraft.getInstance().options.keyLeft, true);
            t.shot(10, "45_flight_bank");
            t.key(0, () -> Minecraft.getInstance().options.keyLeft, false);
            t.key(0, () -> Minecraft.getInstance().options.keyUp, false);
            t.key(0, () -> Minecraft.getInstance().options.keySprint, false);
            t.probe(10, "uitglijden 10");
            t.probe(10, "uitglijden 20");
            t.probe(20, "uitglijden 40");
            t.shot(0, "46_flight_glide");
            t.key(0, () -> Minecraft.getInstance().options.keyShift, true);
            t.probe(40, "dalen 40");
            t.probe(40, "dalen 80");
            t.key(0, () -> Minecraft.getInstance().options.keyShift, false);
            t.probe(20, "geland");
            t.command(0, "gamemode creative @a");
        }

        // --- Drones: onze eigen en een raiddrone, stilgezet zodat je ze goed ziet
        if (want("drones")) {
            t.onServer(0, VisualCheck::spawnDrones);
            t.view(0, 0.5, -59, -16, 0, 0);
            t.shot(40, "50_drones");
            t.view(0, 0.5, -59, -18.5, 0, 0);
            t.shot(20, "51_drones_close");

            // --- Schade: een rij drones van heel tot bijna kapot, met rook en vonken
            t.onServer(0, player -> DevWorld.damagedDroneRow(player.serverLevel(), new BlockPos(20, -59, -30), 0));
            t.view(0, 24.5, -59.2, -35.5, 0, 5);
            t.shot(60, "52_drones_damaged");
            t.view(0, 24.5, -59.5, -33.2, 0, 8);
            t.shot(20, "53_drones_damaged_close");
        }

        // --- De schildkoepel: aanzetten en de animatie in stapjes vastleggen (straal, openvouwen,
        // naar beneden zakken, staan), dan een pijl die erop stuit, het scherm, en van binnen.
        if (want("dome")) {
            t.onServer(0, VisualCheck::buildDome);
            t.view(10, 0.5, -55, 64.5, 180, 6);
            t.onServer(20, player -> domeAt(player).setEnabled(true));
            t.shot(8, "97_dome_a");
            t.shot(6, "97_dome_b");
            t.shot(6, "97_dome_c");
            t.shot(8, "97_dome_d");
            t.shot(10, "97_dome_e");
            t.shot(30, "97_dome_up");
            t.onServer(0, VisualCheck::shootAtDome);
            t.shot(3, "98_dome_arrow");
            t.open(20, () -> new DomeScreen(DOME));
            t.shot(20, "99_dome_screen");
            t.close(0);
            t.view(0, 3.5, -59, 38.5, 160, -35);
            t.shot(20, "99_dome_inside");
        }

        // --- Het stroomnet: een warmtegenerator, een kabel met een hoek en een stuk omhoog, en
        // een laadstation. De kabel moet netjes aansluiten op alles wat hij raakt.
        if (want("power")) {
            t.onServer(0, VisualCheck::buildPowerLine);
            t.view(20, 30.5, -56.5, -13.5, 128, 28);
            t.shot(20, "95_power_cables");
            t.view(0, 22.5, -57.0, -12.5, 170, 40);
            t.shot(20, "96_power_cables_hidden");
            // Kabel-xray uit (G > Information): de verborgen kabel moet nu weg zijn. Daarna weer aan.
            t.step(0, com.senne.starktech.client.EdithClientEvents::toggleCableXray);
            t.shot(20, "96_power_cables_xray_off");
            t.step(0, com.senne.starktech.client.EdithClientEvents::toggleCableXray);
        }

        // --- De Stark Tower: van buiten (drie kanten, en 's nachts) en elke verdieping van binnen.
        if (want("tower")) {
            tower(t);
        }

        // --- De zes bewakingscamera's van de Stark Tower op de bewakingsmuur (jij neemt de kern even
        // over), en het grote beeld van de lobby en de kluis.
        if (want("towercams")) {
            int x = STARK_TOWER.getX();
            int y = STARK_TOWER.getY();
            int z = STARK_TOWER.getZ();
            BlockPos core = new BlockPos(x + 10, y + 57, z + 20);
            t.view(0, x + 20.5, y + 40, z - 40, 0, 0);
            t.command(60, "place template starktech:stark_tower " + x + " " + y + " " + z);
            t.view(20, x + 12.5, y + 57, z + 20.5, 270, 0);
            t.onServer(100, player -> {
                ServerLevel level = player.serverLevel();
                if (level.getBlockEntity(core) instanceof com.senne.starktech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    be.ensureSystem(level);
                    be.setPassword("jarvis1", level.random);
                }
                com.senne.starktech.security.os.StarkOs.open(player, core);
            });
            t.step(5, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourCameras(null);
                }
            });
            t.shot(120, "tc01_tower_camera_wall");
            for (String[] cam : new String[][] {{"Lobby", "tc02_tower_lobby"}, {"Laser hall", "tc03_tower_laser_hall"},
                    {"Vault", "tc04_tower_vault"}}) {
                t.step(0, () -> {
                    if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                        os.tourCameras(towerCamera(cam[0]));
                    }
                });
                t.shot(60, cam[1]);
            }
            t.close(10);
        }

        // --- Het wrak van de neergestorte satelliet: van boven, van dichtbij en 's nachts.
        if (want("crash")) {
            crashSite(t);
        }

        // --- Een Hydra-soldaat van dichtbij: in rust en met zijn geweer in de aanslag.
        if (want("hydra")) {
            t.view(0, 60.5, -60, 60.5, 180, 5);
            t.onServer(20, player -> {
                ServerLevel level = player.serverLevel();
                Entity calm = DevWorld.spawnStill(level, com.senne.starktech.registry.ModEntities.HYDRA_SOLDIER.get(),
                        new BlockPos(59, -60, 57), 0);
                Entity aiming = DevWorld.spawnStill(level, com.senne.starktech.registry.ModEntities.HYDRA_SOLDIER.get(),
                        new BlockPos(62, -60, 57), 0);
                for (Entity soldier : new Entity[] {calm, aiming}) {
                    if (soldier instanceof net.minecraft.world.entity.Mob mob) {
                        mob.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND,
                                new ItemStack(ModItems.HYDRA_RIFLE.get()));
                    }
                }
                if (aiming instanceof net.minecraft.world.entity.Mob mob) {
                    mob.setAggressive(true);
                }
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.shot(20, "h01_hydra_front");
            t.view(0, 66.5, -60, 58.5, 90, 5);
            t.shot(20, "h02_hydra_side");
            t.view(0, 60.5, -60, 53.5, 0, 5);
            t.shot(20, "h03_hydra_back");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        }

        // --- Mini-raketten: eerst de raketbuizen op de rug (van dichtbij), dan twee gemarkeerde
        // doelwitten en een salvo, gevolgd van achter de speler.
        if (want("rockets")) {
            t.onServer(0, player -> {
                wearSuit(player);
                player.getItemBySlot(EquipmentSlot.CHEST).set(ModDataComponents.ROCKET_PACK.get(), true);
            });
            // Een harnasstandaard met het harnas en het raketpakket, zodat we er omheen kunnen lopen.
            t.onServer(0, player -> {
                net.minecraft.world.entity.decoration.ArmorStand stand = new net.minecraft.world.entity.decoration.ArmorStand(
                        player.serverLevel(), 60.5, -60, 56.5);
                stand.setYRot(180);
                stand.setYBodyRot(180);
                ItemStack chest = new ItemStack(ModItems.STARK_CHESTPLATE.get());
                chest.set(ModDataComponents.ROCKET_PACK.get(), true);
                stand.setItemSlot(EquipmentSlot.CHEST, chest);
                stand.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.STARK_LEGGINGS.get()));
                stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.STARK_BOOTS.get()));
                player.serverLevel().addFreshEntity(stand);
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.view(10, 60.5, -59.2, 59.5, 180, 10);
            t.shot(20, "r01_pods_back");
            t.view(0, 63.5, -59.2, 56.5, 90, 10);
            t.shot(10, "r02_pods_side");
            t.view(0, 62.6, -59.2, 58.6, 135, 10);
            t.shot(10, "r02b_pods_diagonal");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.onServer(0, player -> player.serverLevel().getEntitiesOfClass(
                    net.minecraft.world.entity.decoration.ArmorStand.class,
                    player.getBoundingBox().inflate(10)).forEach(Entity::discard));

            t.view(0, 60.5, -60, 64.5, 180, 0);
            t.camera(0, CameraType.THIRD_PERSON_BACK);
            t.onServer(10, player -> {
                ServerLevel level = player.serverLevel();
                for (BlockPos pos : new BlockPos[] {new BlockPos(57, -60, 50), new BlockPos(64, -60, 50)}) {
                    Entity husk = DevWorld.spawnStill(level, net.minecraft.world.entity.EntityType.HUSK, pos, 0);
                    if (husk != null) {
                        com.senne.starktech.edith.EdithServer.handleMark(player, husk.getId());
                    }
                }
            });
            t.onServer(20, com.senne.starktech.suit.SuitRockets::fire);
            t.shot(3, "r03_rockets_launch");
            t.shot(5, "r04_rockets_turn");
            t.shot(6, "r05_rockets_hit");
            t.camera(0, CameraType.FIRST_PERSON);
            t.shot(40, "r06_rockets_hud");
        }

        // --- Torentjes: een Stark-torentje op stroom dat op twee monsters richt, een Hydra-torentje
        // (dat jou in creative negeert en rondkijkt) en een Stark-torentje dat uit staat (redstone).
        if (want("turrets")) {
            t.onServer(0, VisualCheck::buildTurrets);
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.view(10, 81.5, -59.5, 67.5, 180, 12);
            t.shot(30, "t01_turrets_front");
            t.onServer(0, player -> {
                DevWorld.spawnStill(player.serverLevel(), net.minecraft.world.entity.EntityType.HUSK,
                        new BlockPos(71, -60, 58), 0);
                DevWorld.spawnStill(player.serverLevel(), net.minecraft.world.entity.EntityType.HUSK,
                        new BlockPos(74, -60, 52), 0);
            });
            t.shot(25, "t02_turrets_aim");
            t.shot(10, "t03_turrets_fire");
            t.view(0, 75.5, -59.5, 62.5, 225, 20);
            t.shot(15, "t04_turrets_close");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.onServer(0, player -> player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    new ItemStack(ModItems.STARK_TURRET.get())));
            t.shot(10, "t05_turret_item");
        }

        // --- De 3D-modellen van het zonnepaneel en de warmtegenerator (aan en uit), van voren,
        // schuin van achteren, en 's nachts (dan zie je het rooster gloeien).
        if (want("models")) {
            t.onServer(0, VisualCheck::buildModelDisplay);
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.view(40, 40.5, -60, 37.0, 0, 22);
            t.shot(20, "96_models_front");
            t.view(0, 43.5, -60, 43.5, 135, 25);
            t.shot(20, "97_models_back");
            t.command(0, "time set midnight");
            t.view(0, 40.5, -60, 37.0, 0, 22);
            t.shot(30, "98_models_night");
            t.command(0, "time set noon");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        }

        // --- Illusies richten: eerst een valse muur (van bakstenen), met de drone vooruit
        // gevlogen; dan een nep-leger boven op de heuvel (de zombies moeten óp de heuvel staan).
        if (want("illusion")) {
            t.stand(0, -20.5, -60, -12.5, 180, 15);
            t.onServer(0, player -> player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BRICKS)));
            t.action(10, EdithAction.ILLUSION_WALL);
            t.key(20, () -> Minecraft.getInstance().options.keyJump, true);
            t.key(12, () -> Minecraft.getInstance().options.keyJump, false);
            t.look(5, 180, 25);
            t.shot(15, "90_illusion_aim_wall");
            t.step(0, () -> PacketDistributor.sendToServer(new IllusionPlacePayload(true)));
            t.shot(25, "91_illusion_wall_placed");
            t.stand(0, 2.5, -60, 10.5, -90, 12);
            t.action(10, EdithAction.ILLUSION_ARMY);
            t.key(20, () -> Minecraft.getInstance().options.keyJump, true);
            t.key(30, () -> Minecraft.getInstance().options.keyJump, false);
            t.look(5, -90, 30);
            t.shot(15, "92_illusion_aim_army");
            t.step(0, () -> PacketDistributor.sendToServer(new IllusionPlacePayload(true)));
            t.shot(25, "93_illusion_army_placed");
            t.action(0, EdithAction.ILLUSION_STOP);
        }

        // --- Spraakcommando's: een commando met vier acties bovenaan, zodat de kolom rechts
        // niet meer past en moet scrollen. Eerst bovenaan, dan helemaal naar beneden gescrold.
        if (want("commands")) {
            t.step(0, VisualCheck::addBusyCommand);
            t.open(10, () -> new VoiceCommandsScreen(null));
            t.shot(20, "80_commands_busy");
            t.step(0, () -> scrollScreen(-10));
            t.shot(10, "81_commands_scrolled");
            t.close(0);
        }

        // --- De laserpoort: een poort van oost naar west (twee hoog) en een van noord naar zuid.
        // De velden moeten één doorlopende wand vormen (---), geen losse plaatjes (| | |).
        if (want("laser")) {
            t.onServer(0, VisualCheck::buildLaserGates);
            t.view(40, 33.5, -60, 14.5, 0, 5);
            t.shot(0, "70_laser_front");
            t.view(0, 36.5, -53, 21.5, 0, 89);
            t.shot(20, "71_laser_above");
            t.view(0, 39.5, -59.5, 14.5, 35, 10);
            t.shot(20, "72_laser_angle");
        }

        // --- De satelliet: het scherm bij niveau 0, 5 en 10 (dan zie je hem groeien)
        if (want("satellite")) {
            t.view(0, 0.5, -60, -3.2, 0, 22);
            t.onServer(0, player -> satelliteAt(player, 0));
            t.open(10, SatelliteScreen::new);
            t.shot(30, "60_satellite_start");
            t.onServer(0, player -> satelliteAt(player, 5));
            t.shot(30, "61_satellite_level5");
            t.onServer(0, player -> satelliteAt(player, 10));
            t.shot(30, "62_satellite_max");
            t.close(0);
            t.open(0, ArsenalScreen::new);
            t.shot(30, "63_arsenal_with_satellite");
            t.close(0);
        }

        // --- Het scherm van een landingsplatform (zijn batterij), met twee batterijcellen erin
        if (want("padscreen")) {
            BlockPos padAt = new BlockPos(-4, -61, -4);
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(padAt.below(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                level.setBlockAndUpdate(padAt, ModBlocks.LANDING_PAD.get().defaultBlockState());
                if (level.getBlockEntity(padAt.below()) instanceof com.senne.starktech.station.ChargerBlockEntity generator) {
                    generator.fill(20_000);
                }
                if (level.getBlockEntity(padAt) instanceof com.senne.starktech.suit.LandingPadBlockEntity pad) {
                    pad.setOwner(player.getUUID());
                    pad.cells().setItem(0, new ItemStack(ModItems.BATTERY_UPGRADE.get(), 2));
                }
            });
            t.stand(0, padAt.getX() + 0.5, -60, padAt.getZ() + 2.5, 180, 30);
            t.onServer(80, player -> {
                if (player.serverLevel().getBlockEntity(padAt) instanceof com.senne.starktech.suit.LandingPadBlockEntity pad) {
                    player.openMenu(pad, padAt);
                }
            });
            t.shot(30, "p05_pad_screen");
            t.close(0);
        }

        // --- De stroomkast: een generator erachter, kabels links en rechts, één kant uit
        if (want("cabinet")) {
            BlockPos at = new BlockPos(-4, -60, 4);
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(at, ModBlocks.POWER_CABINET.get().defaultBlockState());
                level.setBlockAndUpdate(at.north(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                for (int i = 1; i <= 3; i++) {
                    com.senne.starktech.energy.StarkCables.add(level, at.east(i));
                    com.senne.starktech.energy.StarkCables.add(level, at.west(i));
                }
                if (level.getBlockEntity(at) instanceof com.senne.starktech.energy.PowerCabinetBlockEntity cabinet) {
                    cabinet.setOwner(player.getUUID());
                }
                com.senne.starktech.energy.PowerCabinets.setSwitch(level, at, Direction.WEST, false);
            });
            t.stand(10, at.getX() + 0.5, -60, at.getZ() + 3.5, 180, 25);
            t.shot(20, "c01_cabinet");
            t.onServer(0, player -> com.senne.starktech.energy.PowerCabinets.open(player, at));
            t.shot(20, "c02_cabinet_screen");
            t.close(0);
        }

        // --- Satellietraketten: eentje die valt en inslaat (precisie), en een bombardement
        if (want("missile")) {
            BlockPos aim = new BlockPos(30, -60, -30); // op een lege plek, ver van de heuvel
            Vec3 aimAt = Vec3.atBottomCenterOf(aim);
            t.view(0, aim.getX() + 0.5, -59, aim.getZ() + 24.5, 180, -32);
            t.onServer(20, player -> com.senne.starktech.satellite.SatelliteStrikes.start(player.serverLevel(), aimAt,
                    player.getUUID(), com.senne.starktech.satellite.SatelliteMode.PRECISION, null, null));
            t.shot(46, "90_missile_high");
            t.shot(5, "91_missile_low");
            t.view(0, aim.getX() + 0.5, -59, aim.getZ() + 24.5, 180, -8);
            t.shot(7, "92_missile_impact");
            t.shot(10, "93_missile_after");
            t.onServer(60, player -> com.senne.starktech.satellite.SatelliteStrikes.start(player.serverLevel(), aimAt,
                    player.getUUID(), com.senne.starktech.satellite.SatelliteMode.BOMBARDMENT, null, null));
            t.shot(62, "94_bombardment");
            t.shot(25, "95_bombardment_more");
        }

        // --- De server (7 oktober): drie racks tegen elkaar (één generator eronder), een los rack
        // ernaast dat niet meedraait, het scherm van een rack, en de server in de app Status.
        if (want("racks")) {
            BlockPos core = new BlockPos(60, -60, -80);
            BlockPos[] server = {new BlockPos(63, -60, -78), new BlockPos(63, -59, -78), new BlockPos(64, -60, -78)};
            BlockPos lone = new BlockPos(67, -60, -78);
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState());
                if (level.getBlockEntity(core) instanceof com.senne.starktech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    be.ensureSystem(level);
                }
                for (BlockPos generator : new BlockPos[] {core.below(), server[0].below(), lone.below()}) {
                    level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                    if (level.getBlockEntity(generator) instanceof com.senne.starktech.station.ChargerBlockEntity charger) {
                        charger.fill(20_000);
                    }
                }
                BlockState rack = ModBlocks.SERVER_RACK.get().defaultBlockState()
                        .setValue(com.senne.starktech.logic.ServerRackBlock.FACING, Direction.SOUTH);
                for (BlockPos pos : server) {
                    level.setBlockAndUpdate(pos, rack);
                }
                level.setBlockAndUpdate(lone, rack);
                ItemStack[][] parts = {
                        {new ItemStack(ModItems.SERVER_CPU.get()), new ItemStack(ModItems.SERVER_RAM.get()), new ItemStack(ModItems.SERVER_SSD.get())},
                        {new ItemStack(ModItems.SERVER_CPU.get()), new ItemStack(ModItems.SERVER_RAM.get())},
                        {new ItemStack(ModItems.SERVER_SSD.get())}};
                for (int i = 0; i < server.length; i++) {
                    if (level.getBlockEntity(server[i]) instanceof com.senne.starktech.logic.ServerRackBlockEntity be) {
                        for (int slot = 0; slot < parts[i].length; slot++) {
                            be.parts().setItem(slot, parts[i][slot]);
                        }
                    }
                }
                if (level.getBlockEntity(lone) instanceof com.senne.starktech.logic.ServerRackBlockEntity be) {
                    be.parts().setItem(0, new ItemStack(ModItems.SERVER_CPU.get()));
                }
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.stand(10, 65.0, -60, -74.5, 180, 12);
            t.shot(90, "r01_racks");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.onServer(0, player -> {
                if (player.serverLevel().getBlockEntity(server[0]) instanceof com.senne.starktech.logic.ServerRackBlockEntity be) {
                    player.openMenu(be, server[0]);
                }
            });
            t.shot(20, "r02_rack_screen");
            t.close(0);
            t.onServer(0, player -> {
                if (player.serverLevel().getBlockEntity(lone) instanceof com.senne.starktech.logic.ServerRackBlockEntity be) {
                    player.openMenu(be, lone);
                }
            });
            t.shot(20, "r03_rack_not_connected");
            t.close(0);
            t.stand(0, 60.5, -60, -77.5, 180, 20);
            t.onServer(5, player -> com.senne.starktech.security.os.StarkOs.open(player, core));
            t.step(10, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourOpen(com.senne.starktech.client.os.StarkOsScreen.App.STATUS);
                }
            });
            t.shot(10, "r04_os_status_server");
            t.close(10);
        }

        // --- Keycards (7 oktober): een lezer aan een muur (met een server, dus hij reageert), de
        // kaarten in vijf kleuren in je hotbar, en de app Keycards (een kaart schrijven, de lijst).
        if (want("keycards")) {
            BlockPos core = new BlockPos(100, -60, -80);
            BlockPos wall = new BlockPos(102, -60, -78);
            BlockPos reader = wall.south();
            BlockPos rack = new BlockPos(96, -60, -78);
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState());
                if (level.getBlockEntity(core) instanceof com.senne.starktech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    be.ensureSystem(level);
                    be.setPassword("jarvis1", level.random);
                }
                for (BlockPos generator : new BlockPos[] {core.below(), rack.below()}) {
                    level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                    if (level.getBlockEntity(generator) instanceof com.senne.starktech.station.ChargerBlockEntity charger) {
                        charger.fill(20_000);
                    }
                }
                level.setBlockAndUpdate(rack, ModBlocks.SERVER_RACK.get().defaultBlockState()
                        .setValue(com.senne.starktech.logic.ServerRackBlock.FACING, Direction.SOUTH));
                if (level.getBlockEntity(rack) instanceof com.senne.starktech.logic.ServerRackBlockEntity be) {
                    be.parts().setItem(0, new ItemStack(ModItems.SERVER_CPU.get()));
                    be.parts().setItem(1, new ItemStack(ModItems.SERVER_RAM.get()));
                    be.parts().setItem(2, new ItemStack(ModItems.SERVER_SSD.get()));
                }
                for (int dy = 0; dy < 3; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        level.setBlockAndUpdate(wall.offset(dx, dy, 0), Blocks.SMOOTH_STONE.defaultBlockState());
                    }
                }
                level.setBlockAndUpdate(reader, ModBlocks.KEYCARD_READER.get().defaultBlockState()
                        .setValue(com.senne.starktech.logic.InputDeviceBlock.FACING, Direction.SOUTH));
                if (level.getBlockEntity(reader) instanceof com.senne.starktech.logic.InputDeviceBlockEntity be) {
                    be.setName("Lab door");
                }
                level.setBlockAndUpdate(reader.east(), ModBlocks.CODE_PANEL.get().defaultBlockState()
                        .setValue(com.senne.starktech.logic.InputDeviceBlock.FACING, Direction.SOUTH));
                if (level.getBlockEntity(reader.east()) instanceof com.senne.starktech.logic.InputDeviceBlockEntity be) {
                    be.setName("Front door");
                }
                // Een lege kaart en vijf beschreven kaarten (één per niveau) in de hotbar.
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(ModItems.KEYCARD.get(), 3));
                for (int lvl = 1; lvl <= 5; lvl++) {
                    ItemStack card = new ItemStack(ModItems.KEYCARD.get());
                    com.senne.starktech.logic.KeycardItem.write(card, new com.senne.starktech.logic.KeycardData(
                            "10.0.0.1", "dev-sec", 1000 + lvl, lvl, "Level " + lvl));
                    player.getInventory().setItem(lvl, card);
                }
                player.getInventory().setItem(6, new ItemStack(ModItems.KEYCARD_READER.get()));
                player.getInventory().selected = 3;
            });
            t.stand(10, 103.0, -60, -75.5, 180, 30);
            t.shot(60, "y01_reader_wall");
            // De kaart tegen de lezer: oranje (de server denkt na).
            t.onServer(0, player -> com.senne.starktech.logic.LogicEvents.keycard(player.serverLevel(), reader, player,
                    com.senne.starktech.logic.KeycardItem.data(player.getInventory().getItem(3))));
            t.shot(4, "y02_reader_busy");
            t.onServer(0, player -> com.senne.starktech.logic.InputDeviceBlock.flash(player.serverLevel(), reader,
                    com.senne.starktech.logic.InputLight.OK));
            t.shot(4, "y03_reader_ok");
            t.onServer(0, player -> com.senne.starktech.logic.InputDeviceBlock.flash(player.serverLevel(), reader,
                    com.senne.starktech.logic.InputLight.DENIED));
            t.shot(4, "y04_reader_denied");
            // Het codepaneel: de toetsen, met vier cijfers ingetypt (je ziet sterretjes).
            t.step(0, () -> {
                Minecraft.getInstance().setScreen(new com.senne.starktech.client.CodePanelScreen(reader.east(), "Front door"));
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.CodePanelScreen panel) {
                    panel.tourType("4321");
                }
            });
            t.shot(10, "y045_code_panel");
            t.close(0);
            // De app Keycards: een kaart schrijven.
            t.stand(30, 100.5, -60, -77.5, 180, 20);
            t.onServer(5, player -> com.senne.starktech.security.os.StarkOs.open(player, core));
            t.step(10, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourKeycards("Lab access", 3);
                }
            });
            t.shot(10, "y05_keycards_app");
            t.onServer(0, player -> {
                for (String[] card : new String[][] {{"Lab access", "3"}, {"Guest", "1"}, {"Pepper", "5"}}) {
                    net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
                    args.putString("label", card[0]);
                    args.putInt("level", Integer.parseInt(card[1]));
                    com.senne.starktech.security.os.StarkOs.handleAction(player,
                            new com.senne.starktech.network.OsActionPayload(core, "card_write", args));
                }
                net.minecraft.nbt.CompoundTag block = new net.minecraft.nbt.CompoundTag();
                block.putInt("n", 1002);
                block.putBoolean("on", true);
                com.senne.starktech.security.os.StarkOs.handleAction(player,
                        new com.senne.starktech.network.OsActionPayload(core, "card_block", block));
            });
            t.shot(15, "y06_keycards_list");
            t.close(10);
        }

        // --- Het lab van de showcase (hoofdstuk 8): de deur met keycard, het paneel met de lamp.
        if (want("lab")) {
            BlockPos lab = ShowcaseFeatures.LOGIC_LAB;
            t.onServer(0, ShowcaseFeatures::buildLogicLab);
            t.stand(10, lab.getX() + 3.5, lab.getY(), lab.getZ() + 11.5, 180, 10);
            t.shot(60, "lab1_lab");
            t.onServer(0, player -> ShowcaseFeatures.readCard(player, true));
            t.shot(4, "lab2_lab_card_busy");
            t.shot(12, "lab3_lab_card_ok_door_open");
            t.shot(70, "lab4_lab_door_closed_again");
            t.onServer(0, player -> ShowcaseFeatures.readCard(player, false));
            t.shot(16, "lab5_lab_blocked_card");
            t.onServer(30, player -> {
                com.senne.starktech.logic.LogicEvents.clear();
                com.senne.starktech.logic.LogicEvents.codePanel(player.serverLevel(), lab.offset(5, 1, 7), player, "4321");
            });
            t.shot(16, "lab6_lab_lights_on");
            t.stand(10, lab.getX() + 3.5, lab.getY(), lab.getZ() + 3.5, 180, 15);
            t.shot(20, "lab7_lab_racks");
        }

        // --- De app Logic (7 oktober): een kern met een server, twee relais met een naam (zoals je
        // ze met je bril noemt), een lezer en een paneel; dan de code, Help, een fout, en bewaren.
        if (want("logic")) {
            BlockPos core = new BlockPos(140, -60, -80);
            BlockPos rack = new BlockPos(136, -60, -78);
            String code = """
                    # The lab: level 3 or higher opens the vault for 3 seconds
                    def open_vault():
                        accept()
                        pulse("Vault door", 3)

                    when keycard("Lab door"):
                        if card.valid and card.level >= 3:
                            open_vault()
                        else:
                            deny()
                            alarm("Wrong card at the lab")

                    when codepanel("Front door"):
                        if code == "4321" and player.trusted:
                            open_vault()
                            on("Lab lights")
                        else:
                            deny()

                    when every(60):
                        if is_night and not is_on("Lab lights"):
                            on("Lab lights")
                    """;
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState());
                if (level.getBlockEntity(core) instanceof com.senne.starktech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    be.ensureSystem(level);
                    be.setPassword("jarvis1", level.random);
                }
                for (BlockPos generator : new BlockPos[] {core.below(), rack.below()}) {
                    level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                    if (level.getBlockEntity(generator) instanceof com.senne.starktech.station.ChargerBlockEntity charger) {
                        charger.fill(20_000);
                    }
                }
                level.setBlockAndUpdate(rack, ModBlocks.SERVER_RACK.get().defaultBlockState());
                if (level.getBlockEntity(rack) instanceof com.senne.starktech.logic.ServerRackBlockEntity be) {
                    be.parts().setItem(0, new ItemStack(ModItems.SERVER_CPU.get()));
                    be.parts().setItem(1, new ItemStack(ModItems.SERVER_RAM.get()));
                    be.parts().setItem(2, new ItemStack(ModItems.SERVER_SSD.get()));
                    be.parts().setItem(3, new ItemStack(ModItems.SERVER_SSD.get()));
                }
                String[] names = {"Vault door", "Lab lights"};
                for (int i = 0; i < names.length; i++) {
                    BlockPos relay = core.offset(3 + i, 0, 3);
                    level.setBlockAndUpdate(relay, ModBlocks.RELAY.get().defaultBlockState());
                    var data = player.getData(com.senne.starktech.registry.ModAttachments.RELAYS);
                    player.setData(com.senne.starktech.registry.ModAttachments.RELAYS, data.with(new com.senne.starktech.redstone.Relay(
                            names[i], com.senne.starktech.redstone.Relay.DEFAULT_GROUP, level.dimension(), relay)));
                }
                level.setBlockAndUpdate(core.offset(-3, 0, 3), ModBlocks.KEYCARD_READER.get().defaultBlockState());
                if (level.getBlockEntity(core.offset(-3, 0, 3)) instanceof com.senne.starktech.logic.InputDeviceBlockEntity be) {
                    be.setName("Lab door");
                }
                level.setBlockAndUpdate(core.offset(-4, 0, 3), ModBlocks.CODE_PANEL.get().defaultBlockState());
                if (level.getBlockEntity(core.offset(-4, 0, 3)) instanceof com.senne.starktech.logic.InputDeviceBlockEntity be) {
                    be.setName("Front door");
                }
            });
            t.stand(60, 140.5, -60, -77.5, 180, 20);
            t.onServer(5, player -> com.senne.starktech.security.os.StarkOs.open(player, core));
            t.step(10, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourLogic("Lab", code, false);
                }
            });
            t.shot(10, "l01_logic_code");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourLogic("Lab", code, true);
                }
            });
            t.shot(10, "l02_logic_help");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourLogic("Lab", code.replace("pulse(\"Vault door\", 3)", "opn(\"Vault door\")"), false);
                }
            });
            t.shot(10, "l03_logic_error");
            // De fout verbeterd, en dan de knop Save: daarna staat hij links in de lijst (groen = aan).
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourLogic("Lab", code, false);
                    os.tourSaveLogic();
                }
            });
            t.shot(20, "l04_logic_saved");
            // Hetzelfde diagram als blokjes met draadjes, met rechts het palet.
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourLogicBlocks(null);
                }
            });
            t.shot(10, "l05_logic_blocks");
            // Een blokje gekozen: rechts zijn instellingen, met jouw apparaten als suggestie.
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourLogicBlocks(com.senne.starktech.logic.program.Op.PULSE);
                }
            });
            t.shot(10, "l06_logic_block_settings");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourLogicBlocks(com.senne.starktech.logic.program.Op.IF);
                }
            });
            t.shot(10, "l07_logic_block_if");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.starktech.client.os.StarkOsScreen os) {
                    os.tourLogicBlocks(com.senne.starktech.logic.program.Op.CALL);
                }
            });
            t.shot(10, "l08_logic_block_use_def");
            t.close(10);
        }
}

    // ================================================================ De wereld inrichten

    private static void buildScene(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();

        level.setBlockAndUpdate(TABLE, ModBlocks.HOLO_TABLE.get().defaultBlockState());
        DevWorld.powerNextTo(level, TABLE.east());
        // Een heuvel (piramide), een kuil en een toren: dan heeft de kaart iets te laten zien.
        for (int layer = 0; layer < 9; layer++) {
            int r = 8 - layer;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    level.setBlockAndUpdate(HILL.offset(dx, layer, dz), stone);
                }
            }
        }
        for (int dx = 0; dx < 6; dx++) {
            for (int dz = 0; dz < 6; dz++) {
                for (int dy = 0; dy < 3; dy++) {
                    level.setBlockAndUpdate(PIT.offset(dx, -dy, dz), Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int dy = 0; dy < 15; dy++) {
            level.setBlockAndUpdate(TOWER.above(dy), Blocks.IRON_BLOCK.defaultBlockState());
        }

        // Een vijand en een dier die stil blijven staan (een husk verbrandt niet in de zon).
        DevWorld.spawnStill(level, EntityType.HUSK, ENEMY, 0);
        DevWorld.spawnStill(level, EntityType.COW, ANIMAL, 90);

        DevWorld.wearGlasses(player);

        // Een arsenaal met een paar gehavende drones, zodat je alle kleuren ziet.
        for (int i = 0; i < 12; i++) {
            UUID id = ArsenalServer.add(player);
            if (i % 4 == 1) {
                ArsenalServer.setHealth(player, id, (float) (ArsenalBalance.DRONE_MAX_HEALTH * 0.6));
            } else if (i % 4 == 3) {
                ArsenalServer.setHealth(player, id, (float) (ArsenalBalance.DRONE_MAX_HEALTH * 0.2));
            }
        }

        // Twee waypoints binnen het bereik van de kaart.
        player.setData(ModAttachments.WAYPOINTS, player.getData(ModAttachments.WAYPOINTS)
                .with(new Waypoint("Home", level.dimension(), new BlockPos(18, -60, -16), 0x5CFF9D))
                .with(new Waypoint("Mine", level.dimension(), new BlockPos(-18, -60, 16), 0xFFA93F)));

        player.getAbilities().flying = true;
        player.onUpdateAbilities();
    }

    /** Twee laserpoorten met een redstoneblok achter elke zender (zie de sectie "laser"). */
    /**
     * Hangt een bewakingscamera op (aan de muur, het plafond of de vloer; het blok waaraan hij hangt
     * moet er al staan) met een volle warmtegenerator op {@code power}, en geeft hem een naam.
     */
    private static void placeCamera(ServerLevel level, BlockPos pos, net.minecraft.world.level.block.state.properties.AttachFace face,
                                    Direction facing, BlockPos power, String name) {
        level.setBlockAndUpdate(pos, ModBlocks.SECURITY_CAMERA.get().defaultBlockState()
                .setValue(com.senne.starktech.security.CameraBlock.FACE, face)
                .setValue(com.senne.starktech.security.CameraBlock.FACING, facing));
        level.setBlockAndUpdate(power, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(power) instanceof com.senne.starktech.station.ChargerBlockEntity generator) {
            generator.fill(20_000);
        }
        if (!name.isEmpty() && level.getBlockEntity(pos) instanceof com.senne.starktech.security.CameraBlockEntity camera) {
            camera.setName(name);
        }
    }

    private static void buildLaserGates(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockState power = Blocks.REDSTONE_BLOCK.defaultBlockState();
        // Van west (x 30) naar oost (x 36), twee blokken hoog.
        for (int y = -60; y <= -59; y++) {
            level.setBlockAndUpdate(new BlockPos(30, y, 20), emitter(Direction.EAST, ShieldColor.BLUE));
            level.setBlockAndUpdate(new BlockPos(36, y, 20), emitter(Direction.WEST, ShieldColor.BLUE));
            level.setBlockAndUpdate(new BlockPos(29, y, 20), power);
            level.setBlockAndUpdate(new BlockPos(37, y, 20), power);
        }
        // Stroom: een warmtegenerator tegen de onderste zender, en een stukje kabel erboven naar
        // de bovenste.
        for (int x : new int[] {30, 36}) {
            DevWorld.powerNextTo(level, new BlockPos(x, -60, 21));
            level.setBlockAndUpdate(new BlockPos(x, -59, 21), ModBlocks.CABLE.get().defaultBlockState());
        }
        // Van noord (z 17) naar zuid (z 23), in het rood.
        level.setBlockAndUpdate(new BlockPos(42, -60, 17), emitter(Direction.SOUTH, ShieldColor.RED));
        level.setBlockAndUpdate(new BlockPos(42, -60, 23), emitter(Direction.NORTH, ShieldColor.RED));
        level.setBlockAndUpdate(new BlockPos(42, -60, 16), power);
        level.setBlockAndUpdate(new BlockPos(42, -60, 24), power);
        DevWorld.powerNextTo(level, new BlockPos(43, -60, 17));
        DevWorld.powerNextTo(level, new BlockPos(43, -60, 23));
    }

    private static BlockState emitter(Direction facing, ShieldColor colour) {
        return ModBlocks.LASER_EMITTER.get().defaultBlockState()
                .setValue(LaserEmitterBlock.FACING, facing)
                .setValue(LaserEmitterBlock.COLOR, colour);
    }

    /**
     * Zet (alleen in het geheugen, niet op schijf) een commando met vier acties bovenaan het
     * woordenboek. Het scherm kiest het eerste commando, dus dat zie je meteen.
     */
    private static void addBusyCommand() {
        VoiceDictionary.Command busy = new VoiceDictionary.Command("Lock down the base");
        busy.actions.add(new VoiceDictionary.ActionRef(VoiceAction.REDSTONE_ON, "front door"));
        busy.actions.add(new VoiceDictionary.ActionRef(VoiceAction.REDSTONE_OFF, "lights"));
        busy.actions.add(new VoiceDictionary.ActionRef(VoiceAction.REDSTONE_PULSE, "alarm"));
        busy.actions.add(new VoiceDictionary.ActionRef(VoiceAction.MUSIC_PLAY, "back in black"));
        busy.reply = "Base locked down.";
        busy.phrases.add("lock down the base");
        busy.phrases.add("lockdown");
        busy.phrases.add("we are under attack");
        VoiceCommandStore.dictionary().commands.addFirst(busy);
    }

    /** Het wieltje van de muis, midden rechts in het open scherm (negatief = naar beneden). */
    private static void scrollScreen(double amount) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            mc.screen.mouseScrolled(mc.screen.width * 0.62, mc.screen.height * 0.3, 0, amount);
        }
    }

    /** Waar de koepel in de rondleiding staat. */
    private static final BlockPos DOME = new BlockPos(0, -60, 36);

    /** Een koepelgenerator (nog uit) met een volle warmtegenerator ernaast; van jou, bereik 16. */
    private static void buildDome(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.setBlockAndUpdate(DOME, ModBlocks.DOME_GENERATOR.get().defaultBlockState());
        DevWorld.powerNextTo(level, DOME.east());
        DomeGeneratorBlockEntity dome = domeAt(player);
        dome.setOwner(player);
        dome.setEnabled(false);
        dome.setRadius(DomeSettings.DEFAULT_RADIUS);
        dome.setColour(ShieldColor.CYAN);
    }

    private static DomeGeneratorBlockEntity domeAt(ServerPlayer player) {
        return (DomeGeneratorBlockEntity) player.serverLevel().getBlockEntity(DOME);
    }

    /** Een pijl (van niemand) die van buiten op de koepel af vliegt. */
    private static void shootAtDome(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.entity.projectile.Arrow arrow =
                new net.minecraft.world.entity.projectile.Arrow(EntityType.ARROW, level);
        arrow.setPos(DOME.getX() + 0.5, DOME.getY() + 6, DOME.getZ() + 22);
        arrow.setDeltaMovement(0, 0, -2.0);
        arrow.setNoGravity(true);
        level.addFreshEntity(arrow);
    }

    /** Waar de rondleiding de Stark Tower neerzet (ver van de rest, op de vlakte). */
    private static final BlockPos STARK_TOWER = new BlockPos(200, -61, 200);
    /** Waar het wrak van de satelliet komt (het midden, op het gras). */
    private static final BlockPos CRASH_SITE = new BlockPos(-150, -61, 150);

    /** Het wrak van de neergestorte satelliet: neerzetten (op een heuveltje), dan foto's. */
    private static void crashSite(DevScript t) {
        int x = CRASH_SITE.getX();
        int z = CRASH_SITE.getZ();
        Vec3 middle = new Vec3(x + 0.5, CRASH_SITE.getY() - 1, z + 0.5);
        // Eerst ernaartoe, zodat dat stuk wereld geladen is, dan pas neerzetten.
        t.view(0, x + 30, -46, z + 0.5, 90, 22);
        t.onServer(60, player -> DevWorld.placeCrashSite(player.serverLevel(), CRASH_SITE));
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.view(40, x + 30, -46, z + 0.5, 90, 22);
        t.lookAt(2, middle);
        t.shot(30, "c01_crash_from_furrow");
        t.view(0, x - 20, -44, z - 20, -45, 30);
        t.lookAt(2, middle);
        t.shot(30, "c02_crash_northwest");
        t.view(0, x + 7, -52, z + 14, 150, 35);
        t.lookAt(2, middle);
        t.shot(30, "c03_crash_close");
        t.view(0, x + 0.5, -30, z + 0.5, 0, 90);
        t.shot(30, "c04_crash_from_above");
        t.command(0, "time set midnight");
        t.view(0, x + 30, -46, z + 0.5, 90, 22);
        t.lookAt(2, middle);
        t.shot(40, "c05_crash_night");
        t.command(0, "time set noon");
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
    }

    /**
     * De Stark Tower: neerzetten, dan foto's van buiten en van elke verdieping van binnen. Binnen
     * staan we in de zuidoosthoek (met een wijde blik) en kijken we schuin naar het midden.
     */
    /** Waar de camera met deze naam in de neergezette Stark Tower hangt (zie tools/generate_structure.py). */
    private static @org.jetbrains.annotations.Nullable BlockPos towerCamera(String name) {
        int x = STARK_TOWER.getX();
        int y = STARK_TOWER.getY();
        int z = STARK_TOWER.getZ();
        return switch (name) {
            case "Lobby" -> new BlockPos(x + 25, y + 7, z + 14);
            case "Laser hall" -> new BlockPos(x + 20, y + 75, z + 29);
            case "Vault" -> new BlockPos(x + 20, y + 82, z + 10);
            default -> null;
        };
    }

    private static void tower(DevScript t) {
        int x = STARK_TOWER.getX();
        int y = STARK_TOWER.getY();
        int z = STARK_TOWER.getZ();
        // Eerst ernaartoe, zodat dat stuk wereld geladen is, dan pas neerzetten.
        t.view(0, x + 20.5, y + 40, z - 40, 0, 0);
        t.command(60, "place template starktech:stark_tower " + x + " " + y + " " + z);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
        t.view(40, x + 20.5, y + 45, z + 90, 180, 8);
        t.shot(40, "t01_outside_south");
        t.view(0, x - 40, y + 70, z - 30, -50, 25);
        t.shot(30, "t02_outside_northwest_high");
        t.view(0, x + 20.5, y + 3, z + 70, 180, -8);
        t.shot(30, "t03_outside_entrance");
        t.view(0, x + 34, y + 22, z + 72, 155, 32);
        t.shot(30, "t06_forecourt");
        t.view(0, x + 20.5, y + 112, z + 30, 180, 25);
        t.shot(30, "t05_crown");
        t.command(0, "time set midnight");
        t.view(0, x + 20.5, y + 45, z + 90, 180, 8);
        t.shot(30, "t04_outside_night");
        t.command(0, "time set noon");
        t.step(0, () -> Minecraft.getInstance().options.fov().set(95));
        String[] names = {"lobby", "lab", "archive", "hangar", "living", "security", "lasers", "vault",
                "penthouse", "roof"};
        int[] floors = {1, 12, 23, 34, 45, 56, 67, 77, 88, 99};
        int[] insets = {2, 4, 5, 6, 7, 8, 9, 9, 10, 10};
        for (int i = 0; i < floors.length; i++) {
            double corner = 41 - 1 - insets[i] - 1.5;
            t.view(0, x + corner, y + floors[i] + 1, z + corner, 135, 12);
            t.shot(30, String.format("t%02d_%s", 10 + i, names[i]));
        }
        // De nieuwe inrichting van dichtbij.
        t.view(0, x + 20.5, y + 13, z + 19.5, 180, 8);
        t.shot(30, "t20_hall_of_armor");
        t.view(0, x + 29.5, y + 2, z + 17.5, 180, 10);
        t.shot(30, "t21_meeting_room");
        t.view(0, x + 9.5, y + 2, z + 14.5, 180, 10);
        t.shot(30, "t22_coffee_bar");
        t.view(0, x + 24.5, y + 46, z + 16, 270, 5);
        t.shot(30, "t23_aquarium");
        t.view(0, x + 12.5, y + 25, z + 22.5, 0, -10);
        t.shot(30, "t24_globe");
        t.step(0, () -> Minecraft.getInstance().options.fov().set(70));
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
    }

    /**
     * Een rijtje om de modellen te bekijken, met de voorkanten naar het noorden: een
     * zonnepaneel, een warmtegenerator op magma (die werkt, dus gloeit) en een zonder hitte.
     */
    private static void buildModelDisplay(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos centre = new BlockPos(40, -60, 40);
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-4, 0, -4), centre.offset(4, 3, 4))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(centre.offset(-2, 0, 0), ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        level.setBlockAndUpdate(centre.below(), Blocks.MAGMA_BLOCK.defaultBlockState());
        level.setBlockAndUpdate(centre, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        level.setBlockAndUpdate(centre.offset(2, 0, 0), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
    }

    /**
     * Getekende kabels bij x 20..28, z -20: van een generator over de vloer, tegen een muurtje op,
     * eroverheen en aan de andere kant weer naar beneden, naar een laadstation. Ernaast een tweede
     * kabel waar een rij stenen overheen gebouwd is (die zie je alleen met de bril, door de stenen heen).
     */
    private static void buildPowerLine(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos start = new BlockPos(20, -60, -20);
        level.setBlockAndUpdate(start, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        // Het muurtje van drie hoog bij x = 24.
        for (int y = -60; y <= -58; y++) {
            for (int z = -21; z <= -19; z++) {
                level.setBlockAndUpdate(new BlockPos(24, y, z), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        List<BlockPos> path = new java.util.ArrayList<>();
        for (int x = 21; x <= 23; x++) {
            path.add(new BlockPos(x, -60, -20));           // over de vloer
        }
        path.add(new BlockPos(23, -59, -20));              // tegen het muurtje op
        path.add(new BlockPos(23, -58, -20));
        path.add(new BlockPos(23, -57, -20));
        path.add(new BlockPos(24, -57, -20));              // eroverheen
        path.add(new BlockPos(25, -57, -20));
        path.add(new BlockPos(25, -58, -20));              // aan de andere kant naar beneden
        path.add(new BlockPos(25, -59, -20));
        path.add(new BlockPos(25, -60, -20));
        path.add(new BlockPos(26, -60, -20));
        for (BlockPos pos : path) {
            com.senne.starktech.energy.StarkCables.add(level, pos);
        }
        level.setBlockAndUpdate(new BlockPos(27, -60, -20), ModBlocks.CHARGING_STATION.get().defaultBlockState());
        // De verborgen kabel: over de vloer, en dan stenen erbovenop gebouwd.
        for (int x = 20; x <= 26; x++) {
            BlockPos pos = new BlockPos(x, -60, -17);
            com.senne.starktech.energy.StarkCables.add(level, pos);
            level.setBlockAndUpdate(pos, Blocks.POLISHED_ANDESITE.defaultBlockState());
        }
        com.senne.starktech.energy.StarkCables.add(level, new BlockPos(20, -60, -18));
        com.senne.starktech.energy.StarkCables.add(level, new BlockPos(20, -60, -19));
    }

    /** Hoort dit stuk bij de rondleiding van vandaag (zie ONLY)? */
    private static boolean want(String section) {
        return ONLY.isEmpty() || ONLY.contains(section);
    }

    /** Alle upgrades van de satelliet op hetzelfde niveau; de drie schietstanden minstens vrijgespeeld. */
    private static void satelliteAt(ServerPlayer player, int level) {
        SatelliteUpgrades upgrades = SatelliteUpgrades.START;
        for (SatelliteUpgrade upgrade : SatelliteUpgrade.values()) {
            upgrades = upgrades.with(upgrade, upgrade.isMode() ? Math.max(1, level) : level);
        }
        player.setData(ModAttachments.SATELLITE, upgrades);
    }

    /** Drie torentjes op een rij, met een volle warmtegenerator naast het eerste. */
    private static void buildTurrets(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos stark = new BlockPos(78, -60, 60);
        level.setBlockAndUpdate(stark, ModBlocks.STARK_TURRET.get().defaultBlockState());
        if (level.getBlockEntity(stark) instanceof com.senne.starktech.turret.TurretBlockEntity turret) {
            turret.setOwner(player);
        }
        level.setBlockAndUpdate(stark.east(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(stark.east()) instanceof com.senne.starktech.station.ChargerBlockEntity generator) {
            generator.fill(10_000);
        }
        level.setBlockAndUpdate(new BlockPos(83, -60, 60), ModBlocks.HYDRA_TURRET.get().defaultBlockState());
        BlockPos off = new BlockPos(87, -60, 60);
        level.setBlockAndUpdate(off, ModBlocks.STARK_TURRET.get().defaultBlockState());
        level.setBlockAndUpdate(off.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
    }

    private static void wearSuit(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.STARK_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.STARK_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.STARK_BOOTS.get()));
    }

    private static void spawnDrones(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        DevWorld.spawnStill(level, ModEntities.STARK_DRONE.get(), new BlockPos(-1, -59, -14), 180);
        DevWorld.spawnStill(level, ModEntities.RAID_DRONE.get(), new BlockPos(2, -59, -14), 180);
    }

    /**
     * Wat ertsen onder de holotafel (de platte wereld heeft er geen): een diamantader, goud,
     * redstone, lapis, een kluit kolen en wat ijzer, op twee en drie blokken diep.
     */
    private static void oresUnder(ServerLevel level, BlockPos table) {
        int[][] spots = {{3, 2}, {4, 2}, {4, 3}, {-5, 4}, {-6, 4}, {8, -6}, {-9, -3}, {-9, -4}, {-10, -3},
                {12, 9}, {-12, 10}, {6, -11}, {7, -11}, {0, 13}, {-3, -14}, {14, -2}};
        BlockState[] kinds = {Blocks.DIAMOND_ORE.defaultBlockState(), Blocks.DIAMOND_ORE.defaultBlockState(),
                Blocks.DIAMOND_ORE.defaultBlockState(), Blocks.GOLD_ORE.defaultBlockState(), Blocks.GOLD_ORE.defaultBlockState(),
                Blocks.REDSTONE_ORE.defaultBlockState(), Blocks.COAL_ORE.defaultBlockState(), Blocks.COAL_ORE.defaultBlockState(),
                Blocks.COAL_ORE.defaultBlockState(), Blocks.LAPIS_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(),
                Blocks.EMERALD_ORE.defaultBlockState(), Blocks.COPPER_ORE.defaultBlockState(), Blocks.IRON_ORE.defaultBlockState(),
                Blocks.REDSTONE_ORE.defaultBlockState(), Blocks.COAL_ORE.defaultBlockState()};
        for (int i = 0; i < spots.length; i++) {
            level.setBlockAndUpdate(table.offset(spots[i][0], -2 - (i % 2), spots[i][1]), kinds[i]);
        }
    }

    private static void onTable(DevScript t, Consumer<HoloTableBlockEntity> action) {
        t.onServer(0, player -> {
            if (player.serverLevel().getBlockEntity(TABLE) instanceof HoloTableBlockEntity table) {
                action.accept(table);
            }
        });
    }
}
