package com.senne.imttech.client.dev;

import com.senne.imttech.ImtTech;
import com.senne.imttech.arsenal.ArsenalServer;
import com.senne.imttech.client.ArsenalScreen;
import com.senne.imttech.config.ImtConfig;
import com.senne.imttech.imt.ImtAction;
import com.senne.imttech.network.IllusionPlacePayload;
import com.senne.imttech.client.voice.VoiceAction;
import com.senne.imttech.client.voice.VoiceCommandStore;
import com.senne.imttech.client.voice.VoiceDictionary;
import com.senne.imttech.shield.LaserEmitterBlock;
import com.senne.imttech.shield.ShieldColor;
import net.minecraft.core.Direction;
import com.senne.imttech.client.SatelliteScreen;
import com.senne.imttech.satellite.SatelliteUpgrade;
import com.senne.imttech.satellite.SatelliteUpgrades;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import com.senne.imttech.client.CommsScreen;
import com.senne.imttech.client.DomeScreen;
import com.senne.imttech.client.ImtMenuScreen;
import com.senne.imttech.client.HoloTableScreen;
import com.senne.imttech.client.MusicScreen;
import com.senne.imttech.client.NavigationScreen;
import com.senne.imttech.client.RelayListScreen;
import com.senne.imttech.client.LaserCannonControl;
import com.senne.imttech.client.SpyReportScreen;
import com.senne.imttech.client.WaypointEditScreen;
import com.senne.imttech.client.voice.VoiceCommandsScreen;
import com.senne.imttech.holo.HoloMode;
import com.senne.imttech.holo.HoloTableBlockEntity;
import com.senne.imttech.nav.Waypoint;
import com.senne.imttech.registry.ModAttachments;
import com.senne.imttech.registry.ModBlocks;
import com.senne.imttech.registry.ModDataComponents;
import com.senne.imttech.registry.ModEntities;
import com.senne.imttech.registry.ModItems;
import com.senne.imttech.shield.DomeGeneratorBlockEntity;
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
import net.minecraft.world.level.block.state.properties.AttachFace;
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
 * met {@code -Dimttech.visualcheck=true}, en dat doet alleen die ene Gradle-taak.
 */
public final class VisualCheck {
    private VisualCheck() {}

    private static final boolean ENABLED = Boolean.getBoolean("imttech.visualcheck");
    /**
     * Alleen een paar stukken van de rondleiding, bijvoorbeeld "satellite" of "holo,screens"
     * (gradlew runVisualCheck -Ponly=satellite). Leeg = alles.
     */
    private static final Set<String> ONLY = Arrays.stream(System.getProperty("imttech.visualcheck.only", "").split(","))
            .map(String::trim).filter(part -> !part.isEmpty()).collect(Collectors.toSet());
    private static final String WORLD = "VisualCheck";

    /** Waar alles staat in de platte wereld (de grond is daar y = -61, je staat op -60). */
    static final BlockPos TABLE = new BlockPos(0, -60, 0);
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
        ImtTech.LOGGER.info("[visual check] rondleiding staat aan");
        // Hoofdstuk 38: de rondleiding bouwt veel apparaten zonder netwerkkabel (lezers, camera's naast een kern). Die
        // laten we werken zoals vroeger; alleen de stukken over netwerkkabels zetten het netwerk aan.
        com.senne.imttech.lan.LanGrid.overrideRequired(false);
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
                    ImtTech.LOGGER.info("[visual check] klaar, spel sluit af");
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
            DevWorld.command("gamerule imttechRaidGriefing false");
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

        // --- De Biebia's Industries-bunker in een (zelfgebouwde) berg: van buiten, en dan kamer voor kamer.
        // De ingang staat op (20, -60, 100) en kijkt naar het noorden; de bunker loopt naar het
        // zuiden. Een plek in het bestand (x, y, z) ligt in de wereld op (3 + x, -61 + y, 100 + z).
        if (want("bunker")) {
            t.onServer(0, player -> DevWorld.placeBiebiaBunker(player.serverLevel(), new BlockPos(20, -60, 100)));
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

        // --- De beveiliging van de Biebia's Industries Tower: een kern met twee torentjes, en de drones die hij
        // de lucht in stuurt. Eerst van niemand (rood), dan "gehackt" door jou (groen en blauw).
        if (want("security")) {
            BlockPos core = new BlockPos(-40, -60, 60);
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState()
                        .setValue(com.senne.imttech.security.SecurityCoreBlock.FACING, net.minecraft.core.Direction.NORTH));
                level.setBlockAndUpdate(core.offset(-4, 0, 0), ModBlocks.SECURITY_TURRET.get().defaultBlockState());
                level.setBlockAndUpdate(core.offset(4, 0, 0), ModBlocks.SECURITY_TURRET.get().defaultBlockState());
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.view(20, -39.5, -58.5, 52.5, 0, 12);
            t.shot(200, "s01_security_unclaimed");
            t.view(0, -39.5, -59.4, 57.8, 0, 10);
            t.shot(10, "s02_security_core");
            t.onServer(0, player -> {
                if (player.serverLevel().getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
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
                    instanceof com.senne.imttech.client.HackScreen hack
                    && hack.terminal().output().stream().anyMatch(line -> line.text().startsWith(text));
            t.onServer(0, player -> {
                player.serverLevel().setBlockAndUpdate(core.above(), net.minecraft.world.level.block.Blocks.AIR
                        .defaultBlockState());
                player.serverLevel().setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState());
            });
            t.view(20, -59.5, -59.5, 57.5, 0, 10);
            t.step(0, com.senne.imttech.client.HackScreen::startAutoPlay);
            t.onServer(0, player -> com.senne.imttech.security.TowerHack.placeLaptop(player, core,
                    new ItemStack(ModItems.HACKING_LAPTOP.get())));
            t.shot(20, "k01_hack_start");
            t.shot(220, "k02_hack_recon");
            t.waitUntil(600, () -> Minecraft.getInstance().screen instanceof com.senne.imttech.client.HackScreen hack
                    && hack.terminal().stage() == com.senne.imttech.security.HackTerminal.Stage.LOGIN);
            // Esc: alleen het scherm gaat dicht.
            t.close(10);
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.shot(10, "k025_hack_laptop_on_core");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.onServer(0, player -> com.senne.imttech.security.TowerHack.open(player, core.above()));
            t.shot(15, "k026_hack_restored");
            t.waitUntil(600, () -> said.test("Still possible"));
            t.shot(5, "k03_hack_login");
            t.waitUntil(600, () -> said.test("- Biebia"));
            t.shot(5, "k035_hack_readme");
            t.shot(70, "k04_hack_shell");
            t.waitUntil(900, () -> Minecraft.getInstance().screen instanceof com.senne.imttech.client.HackScreen hack
                    && hack.terminal().output().stream().anyMatch(line -> line.text().startsWith("SECURITY DAEMON")));
            t.shot(3, "k05_hack_takeover");
            t.waitUntil(900, () -> !(Minecraft.getInstance().screen instanceof com.senne.imttech.client.HackScreen));
            t.step(0, () -> com.senne.imttech.client.HackScreen.autoPlay = false);
            t.shot(40, "k06_hack_core_green");
        }

        // --- De Security OS-desktop op een eigen kern: het Files-venster (met een verstopt
        // security.conf en een valstrik), het rechtsklik-menu, en een notitie in Notes.
        if (want("os")) {
            BlockPos core = new BlockPos(-100, -60, 40);
            java.util.function.Supplier<String> home = () -> "/home/" + com.senne.imttech.security.os.SystemDefaults
                    .homeName(Minecraft.getInstance().getUser().getName());
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState());
                if (level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    be.ensureSystem(level);
                    String dir = "/home/" + com.senne.imttech.security.os.SystemDefaults.homeName(player.getGameProfile().getName());
                    var fs = be.fs();
                    fs.mkdir(dir + "/secret");
                    fs.write(dir + "/secret/passwords.txt", "nice try");
                    fs.setTrap(dir + "/secret/passwords.txt", true);
                    fs.move("/etc/security.conf", dir + "/secret/recipes.txt");
                    fs.write(dir + "/todo.txt", "- buy more turrets\n- ask Seppe about the coffee machine\n"
                            + "- hide security.conf (done: it's a recipe now)\n- set a trap for whoever reads this");
                    be.addTrusted(UUID.randomUUID(), "Lotte");
                    be.addTrusted(UUID.randomUUID(), "Seppe");
                }
                // Stroom voor de kern (dan stuurt hij drones), en twee torentjes in zijn gebied.
                level.setBlockAndUpdate(core.below(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                if (level.getBlockEntity(core.below()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
                    generator.fill(200_000);
                }
                level.setBlockAndUpdate(core.offset(4, 0, 0), ModBlocks.SECURITY_TURRET.get().defaultBlockState());
                level.setBlockAndUpdate(core.offset(-4, 0, 0), ModBlocks.SECURITY_TURRET.get().defaultBlockState());
            });
            t.stand(10, -100.5, -60, 37.5, 0, 20);
            t.onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, core));
            t.shot(20, "o01_os_desktop");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourFiles(home.get() + "/secret", home.get() + "/secret/passwords.txt", false);
                }
            });
            t.shot(10, "o02_os_files");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourFiles(home.get() + "/secret", home.get() + "/secret/passwords.txt", true);
                }
            });
            t.shot(10, "o03_os_files_menu");
            t.onServer(0, player -> {
                net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
                args.putString("path", "/home/" + com.senne.imttech.security.os.SystemDefaults
                        .homeName(player.getGameProfile().getName()) + "/todo.txt");
                com.senne.imttech.security.os.SecurityOs.handleAction(player,
                        new com.senne.imttech.network.OsActionPayload(core, "fs_open", args));
            });
            t.shot(15, "o04_os_notes");
            t.step(40, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.DEVICES);
                }
            });
            t.shot(10, "o05_os_devices");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.ACCESS);
                }
            });
            t.shot(10, "o06_os_access");
            t.close(10);
            // 19 oktober: de desktop onthoudt zijn vensters. Hij ging net dicht (zoals met Esc); weer open = alles staat er
            // nog, op dezelfde plek. (Rechts in de taakbalk de knop Shut down, die ze vergeet.)
            t.onServer(10, player -> com.senne.imttech.security.os.SecurityOs.open(player, core));
            t.shot(20, "o07_os_desktop_restored");
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
                if (level.getBlockEntity(core.below()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
                    generator.fill(200_000);
                }
                if (level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    be.ensureSystem(level);
                    be.setPassword("bitech1", level.random);
                }
            });
            // Jij staat er, met je rug naar de kamer (die ligt in het zuiden; jij kijkt naar het noorden).
            t.stand(10, -139.5, -60, 40.5, 180, 0);
            t.onServer(40, player -> {
                for (BlockPos cam : List.of(roomCam, postCam, ceilingCam)) {
                    if (player.serverLevel().getBlockEntity(cam) instanceof com.senne.imttech.security.CameraBlockEntity be) {
                        be.updateOnline(player.serverLevel(), cam, player.serverLevel().getBlockState(cam));
                    }
                }
                com.senne.imttech.security.os.SecurityOs.open(player, core);
            });
            t.step(5, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourCameras(null);
                }
            });
            t.shot(80, "c01_cameras_wall");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
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
                com.senne.imttech.security.os.SecurityOs.handleAction(player,
                        new com.senne.imttech.network.OsActionPayload(core, "cam_aim", aim));
            });
            t.shot(40, "c03_camera_upstairs_turned");
            // Een andere naam geven: op Rename klikken, typen, en Enter.
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourRenameCamera(roomCam, "Server room");
                }
            });
            t.shot(20, "c03b_camera_rename");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.keyPressed(257, 0, 0); // Enter
                }
            });
            t.shot(20, "c03c_camera_renamed");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourCameras(postCam);
                }
            });
            t.shot(60, "c04_camera_courtyard_big");
            t.onServer(0, player -> {
                net.minecraft.nbt.CompoundTag off = new net.minecraft.nbt.CompoundTag();
                off.putString("name", "cameras");
                off.putBoolean("on", false);
                com.senne.imttech.security.os.SecurityOs.handleAction(player,
                        new com.senne.imttech.network.OsActionPayload(core, "flag", off));
            });
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourCameras(null);
                }
            });
            t.shot(20, "c05_cameras_switched_off");
            t.onServer(0, player -> {
                net.minecraft.nbt.CompoundTag on = new net.minecraft.nbt.CompoundTag();
                on.putString("name", "cameras");
                on.putBoolean("on", true);
                com.senne.imttech.security.os.SecurityOs.handleAction(player,
                        new com.senne.imttech.network.OsActionPayload(core, "flag", on));
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
            // Hoofdstuk 62: elk stuk zweeft met zijn onderkant een halve blok boven de werkbank (y -58.5).
            Vec3 chest = new Vec3(-79.5, -58.05, 60.5);
            Vec3 boots = new Vec3(-79.5, -58.3, 60.5);
            Runnable click = () -> KeyMapping.click(Minecraft.getInstance().options.keyUse.getKey());
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(bench, ModBlocks.SUIT_WORKBENCH.get().defaultBlockState());
                level.setBlockAndUpdate(bench.east(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                if (level.getBlockEntity(bench.east()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
                    generator.fill(200_000);
                }
                if (level.getBlockEntity(bench) instanceof com.senne.imttech.workbench.SuitWorkbenchBlockEntity be) {
                    be.select(null, com.senne.imttech.workbench.HoloPiece.CHEST);
                }
                player.getInventory().clearContent();
                player.getInventory().setItem(1, new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT, 8));
                player.getInventory().setItem(2, new ItemStack(net.minecraft.world.item.Items.AMETHYST_SHARD, 2));
                player.getInventory().setItem(3, new ItemStack(ModItems.COMPUTER_CHIP.get(), 3));
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
                    instanceof com.senne.imttech.workbench.SuitWorkbenchBlockEntity be && !be.flying().isEmpty());
            t.shot(7, "w04_workbench_flying");
            t.shot(30, "w05_workbench_wearing");
            t.camera(0, CameraType.FIRST_PERSON);
            t.onServer(0, player -> player.getInventory().setItem(0, new ItemStack(ModItems.TECH_BOOTS.get())));
            // Hoofdstuk 62: alleen het gekozen stuk staat op het hologram (nog de borstplaat). Laarzen erop leggen kan
            // daar ook: ze gaan naar hun eigen plek, en dan zijn de laarzen gekozen.
            t.lookAt(10, chest);
            t.shot(5, "w06_workbench_hover_lay");
            t.step(0, click);
            t.lookAt(10, boots);
            t.onServer(0, player -> player.getInventory().setItem(0, new ItemStack(ModItems.THRUSTER_UPGRADE.get())));
            t.shot(10, "w07_workbench_hover_install");
            t.step(0, click);
            t.shot(40, "w08_workbench_installing");
            t.onServer(80, player -> player.getInventory().setItem(0, ItemStack.EMPTY));
            t.shot(10, "w09_workbench_upgraded");
            // Hoofdstuk 54 en 62: sneak + klik op de werkbank kiest het vorige stuk (van de laarzen drie keer naar links,
            // rond tot de bril), de bril erop (klik op het hologram), een batterij-upgrade erin, en het station zonder
            // redstone- en upgradevakje.
            t.onServer(0, player -> player.getInventory().setItem(0, DevWorld.ownGlasses(player)));
            Vec3 glassesBox = new Vec3(-79.5, -58.38, 60.5);
            t.stand(0, -79.5, -60, 56.2, 0, 0);
            t.lookAt(5, Vec3.atCenterOf(bench));
            t.key(0, () -> Minecraft.getInstance().options.keyShift, true);
            t.step(5, click);
            t.step(8, click);
            t.step(8, click);
            t.key(5, () -> Minecraft.getInstance().options.keyShift, false);
            t.shot(10, "w10_workbench_glasses_selected");
            t.lookAt(5, glassesBox);
            t.step(5, click);
            t.shot(10, "w11_workbench_glasses_laid");
            t.onServer(5, player -> player.getInventory().setItem(0, new ItemStack(ModItems.BATTERY_UPGRADE.get())));
            t.step(10, click);
            t.shot(30, "w12_workbench_glasses_upgrading");
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(bench.west(), ModBlocks.CHARGING_STATION.get().defaultBlockState());
                if (level.getBlockEntity(bench.west()) instanceof com.senne.imttech.station.ChargingStationBlockEntity station) {
                    station.setItem(com.senne.imttech.station.ChargingStationBlockEntity.SLOT_GLASSES, DevWorld.ownGlasses(player));
                    player.openMenu(station);
                }
            });
            t.shot(15, "w13_charging_station_screen");
            t.close(0);
            t.command(0, "gamemode creative @a");
            t.command(0, "difficulty easy");
        }

        // --- Het AI-brein (lokaal model): zinnen alsof je ze zei, en wat ze ermee doet (zie de log:
        // "I.M.T brain"). Het model moet in run-visual/imttech/voice/brain_medium staan.
        if (want("brain")) {
            t.onServer(0, player -> {
                DevWorld.wearGlasses(player);
                DevWorld.refill(player);
                com.senne.imttech.client.voice.ImtMemory.forgetAll();
                DevWorld.spawnStill(player.serverLevel(), EntityType.ZOMBIE, new BlockPos(0, -60, -8), 0);
            });
            t.step(0, () -> {
                com.senne.imttech.client.ImtClientConfig.BRAIN_MODEL.set("medium");
                com.senne.imttech.client.voice.ImtBrain.setMode(com.senne.imttech.client.voice.BrainMode.LOCAL);
            });
            t.view(20, 0.5, -60, 0.5, 180, 5);
            String[] said = {
                    "hoeveel energie heb ik nog",
                    "zet nachtzicht aan en scan naar ertsen",
                    "onthoud dat mijn basis Biebia Hoeve heet",
                    "hoe heet mijn basis?",
                    "doe iets met de ding",
                    "send the drones after that zombie",
                    "what time is it and is it going to rain",
                    "sla deze plek op als kampvuur",
            };
            for (int i = 0; i < said.length; i++) {
                String text = said[i];
                t.step(0, () -> com.senne.imttech.client.voice.VoiceControl.hear(text));
                t.pause(2);
                t.waitUntil(1200, () -> !com.senne.imttech.client.voice.ImtBrain.isThinking());
                t.shot(10, String.format("b%02d_brain", i + 1));
            }
            // Uit zichzelf praten: je leven zakt onder de drie hartjes, en ze zegt er iets van.
            t.step(0, () -> {
                com.senne.imttech.client.ImtClientConfig.PROACTIVE.set(true);
                com.senne.imttech.client.voice.ImtMemory.clearConversation();
            });
            t.command(0, "gamemode survival @a");
            t.onServer(20, player -> player.setHealth(4.0f));
            t.pause(40);
            t.waitUntil(1200, () -> !com.senne.imttech.client.voice.ImtBrain.isThinking());
            t.shot(30, "b08b_brain_speaks_up");
            t.step(0, () -> com.senne.imttech.client.ImtClientConfig.PROACTIVE.set(false));
            t.command(0, "gamemode creative @a");
            t.onServer(0, player -> player.setHealth(player.getMaxHealth()));
            // Claude met een verkeerde sleutel: ze moet zeggen dat de sleutel niet klopt en het
            // woordenboek gebruiken (zo zien we dat de SDK in het spel werkt, zonder kosten).
            t.step(0, () -> {
                try {
                    com.senne.imttech.client.voice.ImtBrain.saveClaudeKey("sk-ant-api03-not-a-real-key-for-testing");
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
                com.senne.imttech.client.voice.ImtBrain.choose(com.senne.imttech.client.voice.BrainMode.CLAUDE);
            });
            t.step(20, () -> com.senne.imttech.client.voice.VoiceControl.hear("night vision on"));
            t.pause(2);
            t.waitUntil(1200, () -> !com.senne.imttech.client.voice.ImtBrain.isThinking());
            t.shot(20, "b09_brain_claude_bad_key");
            t.step(0, () -> {
                try {
                    com.senne.imttech.client.voice.ImtBrain.saveClaudeKey("");
                } catch (java.io.IOException e) {
                    throw new RuntimeException(e);
                }
                com.senne.imttech.client.voice.ImtBrain.setMode(com.senne.imttech.client.voice.BrainMode.DICTIONARY);
            });
            t.open(10, com.senne.imttech.client.BrainScreen::new);
            t.shot(20, "b10_brain_screen");
            t.close(0);
        }

        // --- Het landingsplatform op de Biebia's Industries Tower: van buiten, van onderen, en de HUD tijdens het vliegen.
        if (want("pad")) {
            int x = BIEBIA_TOWER.getX();
            int y = BIEBIA_TOWER.getY();
            int z = BIEBIA_TOWER.getZ();
            Vec3 pad = new Vec3(x + 35.5, y + 100, z + 20.5);
            t.view(0, x + 20.5, y + 40, z - 40, 0, 0);
            t.command(60, "place template imttech:biebia_tower " + x + " " + y + " " + z);
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
                com.senne.imttech.suit.LandingPads.add(player, net.minecraft.core.GlobalPos.of(
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
            t.open(20, ImtMenuScreen::new);
            for (com.senne.imttech.client.MenuTab tab : com.senne.imttech.client.MenuTab.values()) {
                t.step(0, () -> com.senne.imttech.client.ImtClientState.menuTab = tab);
                t.shot(10, String.format("m%02d_menu_%s", tab.ordinal() + 1, tab.name().toLowerCase()));
            }
            t.step(0, () -> com.senne.imttech.client.ImtClientState.menuTab = com.senne.imttech.client.MenuTab.COMBAT);
            t.close(0);
        }

        // --- De schermen
        if (want("screens")) {
            t.view(0, 0.5, -60, -3.2, 0, 22);
            t.open(20, ImtMenuScreen::new);
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
            // De dreigingsscan (hoofdstuk 54: echte pijlpunten, en de pijl naar een dreiging achter je boven je werkbalk).
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                for (BlockPos at : new BlockPos[] {new BlockPos(6, -60, -16), new BlockPos(2, -60, 6), new BlockPos(14, -60, -2)}) {
                    net.minecraft.world.entity.monster.Husk husk = net.minecraft.world.entity.EntityType.HUSK.create(level);
                    if (husk != null) {
                        husk.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
                        husk.setNoAi(true);
                        husk.setTarget(player); // zo telt hij als dreiging (zie ImtThreats)
                        husk.addTag("imt_visual_threat");
                        level.addFreshEntity(husk);
                    }
                }
            });
            t.step(10, () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.THREAT_SCAN.getKey()));
            t.shot(30, "31_threat_arrows");
            t.step(0, () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.THREAT_SCAN.getKey()));
            t.command(5, "kill @e[tag=imt_visual_threat]");
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

            // --- De laser cannon: aanzetten, dan de aanvalsknop "indrukken" zoals een speler dat doet
            t.view(0, 6.5, -60, -4.5, 180, 8);
            t.step(10, LaserCannonControl::toggle);
            t.shot(30, "42_laser_cannon_armed");
            t.tap(0, () -> Minecraft.getInstance().options.keyAttack);
            t.shot(1, "43_laser_cannon_fired");
            t.shot(3, "43b_laser_cannon_impact");
            // Hoe zien anderen het? De arm vooruit en een gloeiende handpalm.
            t.camera(0, CameraType.THIRD_PERSON_FRONT);
            t.shot(20, "43c_laser_cannon_front");
            t.camera(0, CameraType.THIRD_PERSON_BACK);
            t.tap(0, () -> Minecraft.getInstance().options.keyAttack);
            t.shot(2, "43d_laser_cannon_back_fire");
            t.camera(0, CameraType.FIRST_PERSON);
            t.step(30, LaserCannonControl::toggle);
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
            // Het persoonlijke schild als blauwe bol (hoofdstuk 54), van buitenaf.
            t.close(0);
            t.step(5, () -> com.senne.imttech.client.ImtClientEvents.sendAction(com.senne.imttech.imt.ImtAction.TOGGLE_SHIELD));
            t.camera(10, CameraType.THIRD_PERSON_FRONT);
            t.shot(15, "99b_personal_shield_sphere");
            t.camera(0, CameraType.FIRST_PERSON);
            t.step(0, () -> com.senne.imttech.client.ImtClientEvents.sendAction(com.senne.imttech.imt.ImtAction.TOGGLE_SHIELD));
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
            t.step(0, com.senne.imttech.client.ImtClientEvents::toggleCableXray);
            t.shot(20, "96_power_cables_xray_off");
            t.step(0, com.senne.imttech.client.ImtClientEvents::toggleCableXray);
        }

        // --- Hoofdstuk 38: de drie soorten kabels (stroom grijs, netwerk wit, PoE groen) naast elkaar, alle drie op
        // dezelfde plekken (boven elkaar), en verborgen onder een vloer (met de bril grijs, wit en gloeiend groen).
        if (want("network")) {
            BlockPos y = NETWORK_YARD;
            t.onServer(0, VisualCheck::buildNetworkYard);
            t.onServer(0, player -> {
                player.getInventory().setItem(0, new ItemStack(ModItems.CABLE.get(), 64));
                player.getInventory().setItem(1, new ItemStack(ModItems.NETWORK_CABLE.get(), 64));
                player.getInventory().setItem(2, new ItemStack(ModItems.POE_CABLE.get(), 64));
                player.getInventory().setItem(3, new ItemStack(ModItems.NETWORK_BUTTON.get()));
                player.getInventory().setItem(4, new ItemStack(ModItems.NETWORK_LEVER.get()));
            });
            t.step(0, () -> Minecraft.getInstance().player.getInventory().selected = 1);
            t.view(20, y.getX() - 2.5, y.getY() + 5, y.getZ() + 11.5, 0, 0);
            t.lookAt(5, Vec3.atCenterOf(y.offset(3, 0, 4)));
            t.shot(20, "nw01_cable_colours");
            t.view(0, y.getX() + 2.5, y.getY() + 1.6, y.getZ() + 9.5, 0, 0);
            t.lookAt(5, Vec3.atCenterOf(y.offset(4, 0, 7)));
            t.shot(20, "nw02_cable_lanes");
            t.view(0, y.getX() - 3.5, y.getY() + 5, y.getZ() + 23.5, 0, 0);
            t.lookAt(5, Vec3.atCenterOf(y.offset(3, 0, 14)));
            t.shot(20, "nw03_cable_xray");
            t.step(0, com.senne.imttech.client.ImtClientEvents::toggleCableXray);
            t.shot(20, "nw04_cable_xray_off");
            t.step(0, com.senne.imttech.client.ImtClientEvents::toggleCableXray);
            // Een kern met een Network Switch ernaast (stap 6): het blok met zijn lampjes, het deurtje met de zes
            // poorten, en de app Devices met de poorten van de switch open (de bovenste poort staat uit).
            BlockPos desk = NETWORK_DESK;
            BlockPos networkSwitch = desk.east(3);
            t.step(0, () -> com.senne.imttech.lan.LanGrid.overrideRequired(true));
            t.onServer(0, VisualCheck::buildNetworkDesk);
            t.view(20, desk.getX() + 1.5, desk.getY() + 2.5, desk.getZ() + 5.5, 0, 0);
            t.lookAt(5, Vec3.atCenterOf(networkSwitch));
            t.shot(30, "nw05_switch_block");
            t.onServer(0, player -> com.senne.imttech.lan.NetworkSwitches.open(player, networkSwitch));
            t.shot(15, "nw06_switch_screen");
            t.close(5);
            t.onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, desk));
            t.step(10, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourSwitchPorts();
                }
            });
            t.shot(25, "nw07_devices_switch");
            // Dezelfde app, naar beneden tot de knoppen en hendels (stap 8).
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourDevicesAt("BUTTONS");
                }
            });
            t.shot(10, "nw09_devices_controls");
            // Bovenaan (hoofdstuk 39): de laserbrug en de camera met Rename en Turn off.
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourDevicesAt("LASER BRIDGES");
                }
            });
            t.shot(10, "nw12_devices_named");
            t.close(10);
            // De netwerkknop en -hendel (stap 8): aan de muur en op de vloer, met PoE-kabels naar de kern.
            t.view(0, desk.getX() - 0.3, desk.getY() + 1.5, desk.getZ() + 3.2, 0, 0);
            t.lookAt(5, new Vec3(desk.getX() - 1.6, desk.getY() + 0.6, desk.getZ() + 1.0));
            t.shot(20, "nw08_controls");
            // Stap 9: een signaal van de lezer naar de kern (stilgezet onderweg, op de witte netwerkkabel), en de stroom die
            // als gele streepjes naar de laserbrug en over de PoE-kabel naar de camera loopt.
            BlockPos lobby = desk.east(6);
            t.view(0, desk.getX() + 6.5, desk.getY() + 1.4, desk.getZ() + 3.0, 0, 0);
            t.lookAt(5, Vec3.atCenterOf(desk.east(4)));
            t.onServer(10, player -> com.senne.imttech.lan.CableTraffic.signal(player.serverLevel(), lobby, true));
            t.step(3, () -> com.senne.imttech.client.CableTraffic.freeze(true));
            t.shot(5, "nw10_signal");
            t.step(0, () -> com.senne.imttech.client.CableTraffic.freeze(false));
            t.view(0, desk.getX() + 2.5, desk.getY() + 2.0, desk.getZ() - 5.5, 0, 0);
            t.lookAt(5, Vec3.atCenterOf(desk.north(3)));
            t.step(60, () -> com.senne.imttech.client.CableTraffic.freeze(true));
            t.shot(5, "nw11_power_flow");
            t.step(0, () -> com.senne.imttech.client.CableTraffic.freeze(false));
            t.step(0, () -> com.senne.imttech.lan.LanGrid.overrideRequired(false));
        }

        // --- Hoofdstuk 40: een receptie met een Computer en een Monitor aan het netwerk van een kern, de app Computers op de
        // kern, de desktop van de pc (alleen Cameras, Devices en Logs, om te kijken), het codescherm van een tweede pc, en
        // het inlogscherm van de Hacking Laptop met daarna de desktop op afstand.
        if (want("computers")) {
            BlockPos desk = COMPUTER_DESK;
            BlockPos reception = desk.east(4);
            BlockPos office = desk.east(2).south();
            t.step(0, () -> com.senne.imttech.lan.LanGrid.overrideRequired(true));
            t.onServer(0, player -> buildComputerDesk(player, COMPUTER_DESK));
            t.view(20, desk.getX() + 3.5, desk.getY() + 1.8, desk.getZ() + 4.5, 0, 0);
            t.lookAt(5, Vec3.atCenterOf(reception.east()));
            t.shot(30, "pc01_computer_and_monitor");
            t.onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, desk));
            t.step(10, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourComputers(reception);
                }
            });
            t.shot(25, "pc02_computers_app");
            t.close(10);
            t.onServer(5, player -> com.senne.imttech.computer.Computers.use(player, reception.east()));
            t.shot(25, "pc03_pc_desktop");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.DEVICES);
                }
            });
            t.shot(10, "pc04_pc_devices_view_only");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogsEntries();
                }
            });
            t.shot(10, "pc05_pc_logs_entries");
            t.close(10);
            t.open(10, () -> new com.senne.imttech.client.os.OsLoginScreen(office.east(), "Office"));
            t.shot(15, "pc06_code_screen");
            t.close(5);
            t.onServer(0, player -> player.getInventory().setItem(0, new ItemStack(ModItems.HACKING_LAPTOP.get())));
            t.open(10, () -> new com.senne.imttech.client.os.OsLoginScreen(""));
            t.step(5, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.OsLoginScreen login) {
                    login.tourType(computerDeskIp, "recept1");
                }
            });
            t.shot(10, "pc07_laptop_login");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.OsLoginScreen login) {
                    login.tourConnect();
                }
            });
            t.shot(30, "pc08_remote_session");
            t.close(10);
            t.step(0, () -> com.senne.imttech.lan.LanGrid.overrideRequired(false));
        }

        // --- De Biebia's Industries Tower: van buiten (drie kanten, en 's nachts) en elke verdieping van binnen.
        if (want("tower")) {
            tower(t);
        }

        // --- De zes bewakingscamera's van de Biebia's Industries Tower op de bewakingsmuur (jij neemt de kern even
        // over), en het grote beeld van de lobby en de kluis.
        if (want("towercams")) {
            int x = BIEBIA_TOWER.getX();
            int y = BIEBIA_TOWER.getY();
            int z = BIEBIA_TOWER.getZ();
            BlockPos core = new BlockPos(x + 10, y + 57, z + 20);
            t.view(0, x + 20.5, y + 40, z - 40, 0, 0);
            t.command(60, "place template imttech:biebia_tower " + x + " " + y + " " + z);
            t.view(20, x + 12.5, y + 57, z + 20.5, 270, 0);
            t.onServer(100, player -> {
                ServerLevel level = player.serverLevel();
                if (level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    be.ensureSystem(level);
                    be.setPassword("bitech1", level.random);
                }
                com.senne.imttech.security.os.SecurityOs.open(player, core);
            });
            t.step(5, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourCameras(null);
                }
            });
            t.shot(120, "tc01_tower_camera_wall");
            for (String[] cam : new String[][] {{"Lobby", "tc02_tower_lobby"}, {"Laser hall", "tc03_tower_laser_hall"},
                    {"Vault", "tc04_tower_vault"}}) {
                t.step(0, () -> {
                    if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
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

        // --- Een Raven-soldaat van dichtbij: in rust en met zijn geweer in de aanslag.
        if (want("raven")) {
            t.view(0, 60.5, -60, 60.5, 180, 5);
            t.onServer(20, player -> {
                ServerLevel level = player.serverLevel();
                Entity calm = DevWorld.spawnStill(level, com.senne.imttech.registry.ModEntities.RAVEN_SOLDIER.get(),
                        new BlockPos(59, -60, 57), 0);
                Entity aiming = DevWorld.spawnStill(level, com.senne.imttech.registry.ModEntities.RAVEN_SOLDIER.get(),
                        new BlockPos(62, -60, 57), 0);
                for (Entity soldier : new Entity[] {calm, aiming}) {
                    if (soldier instanceof net.minecraft.world.entity.Mob mob) {
                        // Zijn eigen wapen (hoofdstuk 54: een Glock of een lasergeweer).
                        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()),
                                net.minecraft.world.entity.MobSpawnType.SPAWN_EGG, null);
                    }
                }
                if (aiming instanceof net.minecraft.world.entity.Mob mob) {
                    mob.setAggressive(true);
                }
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.shot(20, "h01_raven_front");
            t.view(0, 66.5, -60, 58.5, 90, 5);
            t.shot(20, "h02_raven_side");
            t.view(0, 60.5, -60, 53.5, 0, 5);
            t.shot(20, "h03_raven_back");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        }

        // --- Hoofdstuk 60: bewakers zoals in Hitman. Een soldaat loopt zijn route met zijn rug naar je toe; lawaai vlak
        // voor je maakt hem achterdochtig ("?") en hij komt kijken; hij ziet je (het oog loopt vol), heeft je ("!") en
        // grijpt naar zijn radio; daarna valt hij en ligt zijn lichaam er, met zijn buit erin (het scherm). Sinds
        // 8 november zonder bril (het oog en het "?"/"!" zie je ook zonder), en op het eind een sluipaanval met een zwaard.
        if (want("guards")) {
            // Je staat 22 blokken achter hem: dichterbij ziet hij je al als hij bij een punt van zijn route rondkijkt.
            t.view(0, 100.5, -60, 82.5, 180, 4);
            t.onServer(0, player -> {
                GUARD_MODE_BEFORE = player.gameMode.getGameModeForPlayer();
                player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); // in creative ziet een bewaker je niet
                player.getAbilities().invulnerable = true;
                player.onUpdateAbilities();
                GUARD_HEAD_BEFORE = player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).copy();
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY); // zonder bril
                spawnPatrollingGuard(player.serverLevel(), new BlockPos(100, -60, 60));
            });
            t.shot(40, "hg01_guard_patrol");
            // Lawaai tien blokken voor je: hij kijkt ernaar ("?") en komt kijken, en dan ziet hij jou (het oog loopt vol).
            t.onServer(0, player -> com.senne.imttech.raven.Noises.make(player.serverLevel(), new Vec3(100.5, -59.5, 72.5), 16,
                    com.senne.imttech.raven.Noises.Kind.BLOCK, player));
            t.shot(12, "hg02_guard_suspicious");
            t.shot(55, "hg03_guard_eye");
            t.waitUntil(300, () -> guardAlert() == com.senne.imttech.raven.RavenAlert.CALLING);
            t.shot(10, "hg04_guard_calling");
            t.onServer(60, VisualCheck::dropNearestGuard);
            t.view(0, 100.5, -58.5, 75.5, 180, 38);
            t.shot(20, "hg05_guard_body");
            t.onServer(0, VisualCheck::searchNearestBody);
            t.shot(15, "hg06_guard_body_search");
            t.close(0);
            // 8 november: een sluipaanval. Een bewaker met zijn rug naar je toe, jij er vlak achter met een zwaard: onder je
            // vizier staat "Takedown"; een klik en hij ligt er, zonder dat iemand iets hoorde.
            t.onServer(0, player -> {
                GUARD_HAND_BEFORE = player.getMainHandItem().copy();
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
                Entity guard = DevWorld.spawnStill(player.serverLevel(), com.senne.imttech.registry.ModEntities.RAVEN_SOLDIER.get(),
                        new BlockPos(112, -60, 60), 180);
                if (guard instanceof net.minecraft.world.entity.Mob mob) {
                    mob.finalizeSpawn(player.serverLevel(), player.serverLevel().getCurrentDifficultyAt(mob.blockPosition()),
                            net.minecraft.world.entity.MobSpawnType.SPAWN_EGG, null);
                }
            });
            t.view(0, 112.5, -60, 62.2, 180, 12);
            t.shot(20, "hg07_guard_takedown");
            // De worsteling in slow motion (zes seconden in plaats van twee), zodat de shots op vaste momenten vallen: eerst
            // zoals jij het ziet, dan van voren (de camera van F5, twee keer) om zijn armen, het zakken en het vallen te zien.
            t.onServer(0, player -> {
                GUARD_TAKEDOWN_BEFORE = com.senne.imttech.config.ImtConfig.Guards.TAKEDOWN_SECONDS;
                com.senne.imttech.config.ImtConfig.Guards.TAKEDOWN_SECONDS = 6.0f;
                takeDownNearestGuard(player);
            });
            t.shot(25, "hg08_takedown_struggle");
            t.step(0, () -> Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
            t.shot(12, "hg09_takedown_struggle_front");
            t.shot(42, "hg10_takedown_limp_front");
            t.shot(24, "hg11_takedown_falling_front");
            t.shot(35, "hg12_guard_taken_down");
            t.step(0, () -> Minecraft.getInstance().options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
            t.onServer(0, player -> {
                com.senne.imttech.config.ImtConfig.Guards.TAKEDOWN_SECONDS = GUARD_TAKEDOWN_BEFORE;
                player.getAbilities().invulnerable = false;
                player.setGameMode(GUARD_MODE_BEFORE);
                player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, GUARD_HEAD_BEFORE);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, GUARD_HAND_BEFORE);
                player.onUpdateAbilities();
            });
        }

        // --- Hoofdstuk 21: guard robots. Vooraan de vier stijlen (Commando, Trainer, Battle, Super Battle),
        // daarachter twee gemengde; van voren, opzij, van achter en 's nachts (de lichtjes).
        if (want("robots")) {
            t.view(0, 60.5, -60, 62.5, 180, 8);
            t.onServer(20, player -> {
                ServerLevel level = player.serverLevel();
                com.senne.imttech.robot.RobotStyle[] styles = com.senne.imttech.robot.RobotStyle.values();
                for (int i = 0; i < styles.length; i++) {
                    robotStill(level, new BlockPos(57 + i * 2, -60, 57), new com.senne.imttech.robot.RobotParts(
                            styles[i], styles[i], styles[i], styles[i]));
                }
                robotStill(level, new BlockPos(59, -60, 53), new com.senne.imttech.robot.RobotParts(
                        com.senne.imttech.robot.RobotStyle.COMMANDO, com.senne.imttech.robot.RobotStyle.SUPER_BATTLE,
                        com.senne.imttech.robot.RobotStyle.TRAINER, com.senne.imttech.robot.RobotStyle.BATTLE));
                robotStill(level, new BlockPos(62, -60, 53), new com.senne.imttech.robot.RobotParts(
                        com.senne.imttech.robot.RobotStyle.BATTLE, com.senne.imttech.robot.RobotStyle.TRAINER,
                        com.senne.imttech.robot.RobotStyle.SUPER_BATTLE, com.senne.imttech.robot.RobotStyle.COMMANDO));
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.shot(20, "rb01_robot_styles_front");
            t.view(0, 60.5, -59, 59.5, 180, 12);
            t.shot(20, "rb02_robot_styles_close");
            t.view(0, 67.5, -60, 57.5, 90, 5);
            t.shot(20, "rb03_robot_styles_side");
            t.view(0, 60.5, -60, 49.5, 0, 5);
            t.shot(20, "rb04_robot_mixed_back");
            t.view(0, 60.5, -59.5, 57.5, 180, 15);
            t.shot(20, "rb05_robot_mixed_front");
            t.command(0, "time set midnight");
            t.view(0, 60.5, -60, 62.5, 180, 8);
            t.shot(40, "rb06_robot_styles_night");
            t.command(0, "time set noon");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            // De Robot Assembler: eerst half gevuld (hoofd en lijf: het voorbeeld toont alleen die), dan vol.
            BlockPos bench = new BlockPos(60, -60, 64);
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(bench, ModBlocks.ROBOT_ASSEMBLER.get().defaultBlockState()
                        .setValue(com.senne.imttech.robot.RobotAssemblerBlock.FACING, net.minecraft.core.Direction.SOUTH));
                if (level.getBlockEntity(bench) instanceof com.senne.imttech.robot.RobotAssemblerBlockEntity be) {
                    be.items().setItem(0, new ItemStack(ModItems.robotPart(com.senne.imttech.robot.RobotSlot.HEAD,
                            com.senne.imttech.robot.RobotStyle.COMMANDO)));
                    be.items().setItem(1, new ItemStack(ModItems.robotPart(com.senne.imttech.robot.RobotSlot.BODY,
                            com.senne.imttech.robot.RobotStyle.SUPER_BATTLE)));
                    player.openMenu(be, bench);
                }
            });
            t.shot(30, "rb07_assembler_half");
            t.onServer(0, player -> {
                if (player.serverLevel().getBlockEntity(bench) instanceof com.senne.imttech.robot.RobotAssemblerBlockEntity be) {
                    be.items().setItem(2, new ItemStack(ModItems.robotPart(com.senne.imttech.robot.RobotSlot.ARMS,
                            com.senne.imttech.robot.RobotStyle.SUPER_BATTLE)));
                    be.items().setItem(3, new ItemStack(ModItems.robotPart(com.senne.imttech.robot.RobotSlot.LEGS,
                            com.senne.imttech.robot.RobotStyle.COMMANDO)));
                    be.items().setItem(4, new ItemStack(ModItems.ROBOT_CIRCUIT_BOARD.get()));
                }
            });
            t.shot(30, "rb08_assembler_ready");
            t.onServer(0, player -> {
                if (player.serverLevel().getBlockEntity(bench) instanceof com.senne.imttech.robot.RobotAssemblerBlockEntity be) {
                    be.assemble();
                }
            });
            t.shot(30, "rb09_assembler_built");
            t.close(0);
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.view(0, 60.5, -60, 66.5, 180, 25);
            t.shot(20, "rb10_assembler_block");
            // Twee Robot Docks naast elkaar: in het linker staat een robot te laden (uit, dus zijn lichtjes uit),
            // het rechter is leeg.
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                BlockState dock = ModBlocks.ROBOT_DOCK.get().defaultBlockState()
                        .setValue(com.senne.imttech.robot.RobotDockBlock.FACING, net.minecraft.core.Direction.SOUTH);
                BlockPos left = new BlockPos(66, -60, 64);
                level.setBlockAndUpdate(left, dock);
                level.setBlockAndUpdate(left.east(2), dock);
                if (DevWorld.spawnStill(level, com.senne.imttech.registry.ModEntities.GUARD_ROBOT.get(), left, 0)
                        instanceof com.senne.imttech.robot.GuardRobotEntity robot
                        && level.getBlockEntity(left) instanceof com.senne.imttech.robot.RobotDockBlockEntity be) {
                    robot.setParts(new com.senne.imttech.robot.RobotParts(com.senne.imttech.robot.RobotStyle.TRAINER,
                            com.senne.imttech.robot.RobotStyle.TRAINER, com.senne.imttech.robot.RobotStyle.TRAINER,
                            com.senne.imttech.robot.RobotStyle.TRAINER));
                    robot.setPos(left.getX() + 0.5, left.getY() + 3.0 / 16.0, left.getZ() + 0.5);
                    robot.setHome(left);
                    be.setRobot(robot.getUUID());
                }
            });
            t.view(0, 67.5, -60, 68.5, 180, 15);
            t.shot(20, "rb11_robot_docks");
            // Het scherm van de robot in het dock (sneak + rechtsklik): halfvolle batterij, en (26 oktober) beschadigd:
            // de balk Health is oranje.
            t.onServer(0, player -> {
                for (com.senne.imttech.robot.GuardRobotEntity robot : player.serverLevel().getEntitiesOfClass(
                        com.senne.imttech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(new BlockPos(66, -60, 64)).inflate(0.5))) {
                    robot.setBattery(robot.maxBattery() * 0.56);
                    robot.setHealth(robot.getMaxHealth() * 0.45f);
                    com.senne.imttech.robot.Robots.open(player, robot);
                }
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.shot(30, "rb12_robot_screen");
            t.close(0);
            // Hoofdstuk 22: het bordscherm. Modules op pin 1, 3 en 6, een verstopte brug tussen 3 en 4; jij hebt
            // een soldeerbout en wat modules. Eerst met de hittebalk boven pin 2, dan met Inspect.
            t.onServer(0, player -> {
                player.getInventory().add(new ItemStack(ModItems.SOLDERING_IRON.get()));
                player.getInventory().add(new ItemStack(ModItems.robotModule(com.senne.imttech.bot.BotModule.SCAN), 2));
                player.getInventory().add(new ItemStack(ModItems.robotModule(com.senne.imttech.bot.BotModule.GUN)));
                for (com.senne.imttech.robot.GuardRobotEntity robot : player.serverLevel().getEntitiesOfClass(
                        com.senne.imttech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(new BlockPos(66, -60, 64)).inflate(0.5))) {
                    robot.setBoard(com.senne.imttech.bot.BotBoard.EMPTY.with(1, "scan").with(3, "laser").with(6, "camera").withBridge(3));
                }
            });
            t.step(10, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    for (com.senne.imttech.robot.GuardRobotEntity robot : mc.level.getEntitiesOfClass(
                            com.senne.imttech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(new BlockPos(66, -60, 64)).inflate(0.5))) {
                        com.senne.imttech.client.robot.BoardScreen board = new com.senne.imttech.client.robot.BoardScreen(robot.getId(), null);
                        mc.setScreen(board);
                        board.tourHeat(com.senne.imttech.bot.BotModule.SCAN, 2, Math.round(com.senne.imttech.config.ImtConfig.Robots.SOLDER_FULL_TICKS * 0.6f));
                    }
                }
            });
            t.shot(20, "rb14_board_heat");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.robot.BoardScreen board) {
                    board.tourHeat(com.senne.imttech.bot.BotModule.SCAN, -1, 0);
                    board.tourInspect(true);
                }
            });
            t.shot(10, "rb15_board_inspect");
            t.close(0);
            // Hoofdstuk 22: de vier robots vooraan met alle modules erop (van voren en van achteren).
            t.onServer(0, player -> {
                com.senne.imttech.bot.BotBoard all = com.senne.imttech.bot.BotBoard.EMPTY;
                String[] ids = {"scan", "distance", "face_recognition", "pathfinding", "laser", "gun", "solar_panel", "camera"};
                for (int i = 0; i < ids.length; i++) {
                    all = all.with(i + 1, ids[i]);
                }
                for (com.senne.imttech.robot.GuardRobotEntity robot : player.serverLevel().getEntitiesOfClass(
                        com.senne.imttech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(56, -60, 57, 65, -58, 58))) {
                    robot.setBoard(all);
                }
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.view(5, 60.5, -59, 60.5, 180, 12);
            t.shot(20, "rb16_modules_front");
            t.view(0, 60.5, -59, 54.5, 0, 12);
            t.shot(20, "rb17_modules_back");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            // De app Robots: een beveiligingskern vlakbij, en de robots van hierboven in zijn lijst.
            BlockPos robotCore = new BlockPos(72, -60, 68);
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(robotCore, ModBlocks.SECURITY_CORE.get().defaultBlockState());
                if (level.getBlockEntity(robotCore) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    be.ensureSystem(level);
                    // Hoofdstuk 23: twee robotprogramma's op deze server. De eerste robot heeft een oudere kopie van
                    // Patrol, de tweede Door guard; de rest niets.
                    be.logic().put(null, robotProgram("Patrol", "when started:\n    while true:\n        forward(4)\n        turn_right()\n"));
                    be.logic().put(null, robotProgram("Door guard", "when started:\n    pass\n"));
                    java.util.List<com.senne.imttech.robot.GuardRobotEntity> robots = level.getEntitiesOfClass(
                            com.senne.imttech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(56, -60, 57, 65, -58, 58));
                    robots.sort(java.util.Comparator.comparingDouble(com.senne.imttech.robot.GuardRobotEntity::getX));
                    if (robots.size() >= 2) {
                        robots.get(0).setProgram(robotProgram("Patrol", "when started:\n    forward(2)\n"));
                        robots.get(1).setProgram(be.logic().program("Door guard"));
                    }
                }
                level.setBlockAndUpdate(robotCore.below(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                if (level.getBlockEntity(robotCore.below()) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                    charger.fill(200_000);
                }
                // Hoofdstuk 24: een serverrack naast de kern, en de robots van de speler: dan hebben ze verbinding
                // (en staan de robots met een camera in de app Cameras).
                BlockPos rack = robotCore.east(2);
                level.setBlockAndUpdate(rack.below(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                if (level.getBlockEntity(rack.below()) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                    charger.fill(200_000);
                }
                level.setBlockAndUpdate(rack, ModBlocks.SERVER_RACK.get().defaultBlockState());
                if (level.getBlockEntity(rack) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
                    be.insert(new ItemStack(ModItems.SERVER_CPU.get()));
                    be.insert(new ItemStack(ModItems.SERVER_RAM.get()));
                    be.insert(new ItemStack(ModItems.SERVER_SSD.get()));
                    be.insert(new ItemStack(ModItems.SERVER_FAN.get()));
                }
                for (com.senne.imttech.robot.GuardRobotEntity robot : level.getEntitiesOfClass(com.senne.imttech.robot.GuardRobotEntity.class,
                        new net.minecraft.world.phys.AABB(robotCore).inflate(24))) {
                    robot.setOwner(player);
                }
            });
            // Even wachten: de robots melden zich om de seconde, nu bij deze kern. (Dichtbij gaan staan: de desktop
            // gaat alleen open als je bij de kern bent.)
            t.view(0, 67.5, -60, 68.5, 180, 15);
            // 18 oktober: een robot die er niet meer is ("far away", met de knop Delete). Hij staat alleen in BotNetwork
            // (nooit in de wereld gezet); na zijn screenshot halen we hem weg zoals de knop dat doet.
            com.senne.imttech.robot.GuardRobotEntity[] ghost = new com.senne.imttech.robot.GuardRobotEntity[1];
            t.onServer(40, player -> {
                ghost[0] = com.senne.imttech.registry.ModEntities.GUARD_ROBOT.get().create(player.serverLevel());
                if (ghost[0] != null) {
                    ghost[0].moveTo(robotCore.getX() - 6.5, robotCore.getY(), robotCore.getZ() + 4.5);
                    ghost[0].setOwner(player);
                    ghost[0].setCustomName(net.minecraft.network.chat.Component.literal("Old guard"));
                    com.senne.imttech.bot.BotNetwork.get(player.serverLevel()).update(ghost[0]);
                }
                com.senne.imttech.security.os.SecurityOs.open(player, robotCore);
            });
            t.step(10, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.ROBOTS);
                }
            });
            t.shot(15, "rb13_os_robots");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourRobotsAskDelete();
                }
            });
            t.shot(5, "rb26_os_robot_delete");
            t.onServer(0, player -> {
                if (ghost[0] != null) {
                    net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
                    args.putString("id", ghost[0].getUUID().toString());
                    com.senne.imttech.security.os.SecurityOs.handleAction(player,
                            new com.senne.imttech.network.OsActionPayload(robotCore, "robot_delete", args));
                }
            });
            // Hoofdstuk 23: de knop Program van de eerste robot: de robotprogramma's van deze server.
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourRobotsProgram();
                }
            });
            t.shot(10, "rb18_os_robot_program");
            // Hoofdstuk 23: in de app Logic een nieuw robotprogramma; de Help kent je robots en hun pinnen.
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicNew(com.senne.imttech.logic.program.ProgramKind.ROBOT);
                    os.tourLogicHelpAt("YOUR ROBOTS");
                }
            });
            t.shot(10, "rb19_logic_robot_help");
            // Hoofdstuk 24: de app Cameras met de robots die een camera hebben, en het grote beeld van de eerste.
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.CAMERAS);
                }
            });
            t.shot(30, "rb22_os_robot_cameras");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourRobotCamera();
                }
            });
            t.shot(30, "rb23_os_robot_camera_big");
            t.close(10);
            // Hoofdstuk 24: een robot kijkt naar het oosten, met een koe zes blokken verder. Eerst schiet hij met zijn
            // laser (rechterarm), daarna met zijn geweer (linkerarm).
            BlockPos shooter = new BlockPos(56, -60, 62);
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                DevWorld.spawnStill(level, net.minecraft.world.entity.EntityType.COW, shooter.east(6), 90);
                if (DevWorld.spawnStill(level, com.senne.imttech.registry.ModEntities.GUARD_ROBOT.get(), shooter, -90)
                        instanceof com.senne.imttech.robot.GuardRobotEntity robot) {
                    com.senne.imttech.robot.RobotStyle c = com.senne.imttech.robot.RobotStyle.COMMANDO;
                    robot.setParts(new com.senne.imttech.robot.RobotParts(c, c, c, c));
                    robot.setBoard(com.senne.imttech.bot.BotBoard.EMPTY.with(3, "laser").with(4, "gun"));
                    robot.setActive(true);
                }
            });
            t.view(10, 59.0, -58.8, 67.5, 180, 12);
            for (com.senne.imttech.bot.BotShot.Weapon weapon : com.senne.imttech.bot.BotShot.Weapon.values()) {
                t.onServer(0, player -> {
                    for (com.senne.imttech.robot.GuardRobotEntity robot : player.serverLevel().getEntitiesOfClass(
                            com.senne.imttech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(shooter).inflate(0.5))) {
                        com.senne.imttech.bot.BotShot.fire(robot, weapon, null);
                    }
                });
                t.shot(2, weapon == com.senne.imttech.bot.BotShot.Weapon.LASER ? "rb20_robot_laser" : "rb21_robot_gun");
                t.step(20, () -> { });
            }
            // 19 oktober: aim(x, y, z). Hij keek naar het oosten; nu richt hij op een koe aan zijn linkerkant, met zijn laser
            // aan: zijn hoofd en lijf draaien erheen.
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                BlockPos side = shooter.north(5);
                DevWorld.spawnStill(level, net.minecraft.world.entity.EntityType.COW, side, 90);
                String code = "when started:\n    aim(" + side.getX() + ", " + (side.getY() + 1) + ", " + side.getZ()
                        + ")\n    write(pin(3), true)\n";
                for (com.senne.imttech.robot.GuardRobotEntity robot : level.getEntitiesOfClass(
                        com.senne.imttech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(shooter).inflate(0.5))) {
                    robot.setProgram(new com.senne.imttech.logic.program.LogicProgram("Aim", true,
                            com.senne.imttech.logic.program.GraphConverter.toGraph(com.senne.imttech.logic.program.ScriptParser.parse(
                                    code, com.senne.imttech.logic.program.ProgramKind.ROBOT), null),
                            code, com.senne.imttech.logic.program.ProgramKind.ROBOT));
                }
            });
            t.shot(4, "rb27_robot_aim");
            t.onServer(20, player -> {
                for (com.senne.imttech.robot.GuardRobotEntity robot : player.serverLevel().getEntitiesOfClass(
                        com.senne.imttech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(shooter).inflate(0.5))) {
                    robot.setProgram(null);
                }
            });
            // De Ion Blaster: jij schiet de robot neer (blauwe straal), en dan ligt hij op zijn rug.
            t.onServer(0, player -> {
                for (com.senne.imttech.robot.GuardRobotEntity robot : player.serverLevel().getEntitiesOfClass(
                        com.senne.imttech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(shooter).inflate(0.5))) {
                    player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, robot.getBoundingBox().getCenter());
                    com.senne.imttech.robot.IonBlasterItem.fire(player.serverLevel(), player);
                }
            });
            t.shot(2, "rb24_ion_blaster");
            t.shot(30, "rb25_robot_down");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        }

        // --- Hoofdstuk 25: security drones. Twee Drone Docks: op de linker staat een drone (uit, met Scan, Face
        // Recognition en Laser), boven de rechter hangt er een die aan staat, met alle modules. Dan hun scherm, hun
        // bord, de app Logic (een droneprogramma), de apps Robots en Cameras, en de Ion Blaster.
        if (want("secdrones")) {
            BlockPos dockA = new BlockPos(60, -60, 72);
            BlockPos dockB = new BlockPos(63, -60, 72);
            BlockPos droneCore = new BlockPos(72, -60, 68);
            t.onServer(20, player -> {
                ServerLevel level = player.serverLevel();
                BlockState dock = ModBlocks.DRONE_DOCK.get().defaultBlockState()
                        .setValue(com.senne.imttech.arsenal.DroneDockBlock.FACING, net.minecraft.core.Direction.SOUTH);
                level.setBlockAndUpdate(dockA, dock);
                level.setBlockAndUpdate(dockB, dock);
                com.senne.imttech.bot.BotBoard all = com.senne.imttech.bot.BotBoard.EMPTY;
                String[] ids = {"scan", "distance", "face_recognition", "pathfinding", "laser", "gun", "solar_panel", "camera"};
                for (int i = 0; i < ids.length; i++) {
                    all = all.with(i + 1, ids[i]);
                }
                com.senne.imttech.robot.GuardDroneEntity parked = securityDrone(level, player, dockA, true);
                parked.setBoard(com.senne.imttech.bot.BotBoard.EMPTY.with(1, "scan").with(2, "face_recognition").with(3, "laser"));
                com.senne.imttech.robot.GuardDroneEntity flying = securityDrone(level, player, dockB.above(2), false);
                flying.setBoard(all);
                flying.setActive(true);
                // Een kern met stroom en een serverrack (zoals bij de robots): dan hebben de drones verbinding.
                ensureBotCore(level, player, droneCore);
                if (level.getBlockEntity(droneCore) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    be.logic().put(null, robotProgram("Hover patrol", "when started:\n    up(2)\n    while true:\n        forward(3)\n        turn_right()\n",
                            com.senne.imttech.logic.program.ProgramKind.DRONE));
                    parked.setProgram(be.logic().program("Hover patrol")); // hij staat uit: de app laat zijn programma zien
                }
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.view(0, 61.5, -59, 76.5, 180, 12);
            t.shot(30, "sd01_drone_docks");
            t.view(0, 63.5, -58.3, 74.2, 180, 18);
            t.shot(20, "sd02_drone_modules_front");
            t.view(0, 65.3, -58.2, 70.5, 60, 20);
            t.shot(20, "sd03_drone_modules_side");
            t.command(0, "time set midnight");
            t.view(0, 63.5, -58.3, 74.2, 180, 18);
            t.shot(40, "sd04_drone_night");
            t.command(0, "time set noon");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            // Het scherm van de drone op het dock (halfvolle batterij), en zijn bord.
            t.onServer(0, player -> {
                for (com.senne.imttech.robot.GuardDroneEntity drone : player.serverLevel().getEntitiesOfClass(
                        com.senne.imttech.robot.GuardDroneEntity.class, new net.minecraft.world.phys.AABB(dockA).inflate(0.5))) {
                    drone.setBattery(drone.maxBattery() * 0.56);
                    com.senne.imttech.robot.Robots.open(player, drone);
                }
            });
            t.shot(30, "sd05_drone_screen");
            t.step(0, () -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.level != null) {
                    for (com.senne.imttech.robot.GuardDroneEntity drone : mc.level.getEntitiesOfClass(
                            com.senne.imttech.robot.GuardDroneEntity.class, new net.minecraft.world.phys.AABB(dockA).inflate(0.5))) {
                        mc.setScreen(new com.senne.imttech.client.robot.BoardScreen(drone.getId(), null));
                    }
                }
            });
            t.shot(15, "sd06_drone_board");
            t.close(0);
            // De desktop: Logic (na + de keuze, met Drone program), een nieuw droneprogramma met zijn Help, de app
            // Robots (met de drones), en Cameras (de drone met een camera).
            t.view(0, 67.5, -60, 68.5, 180, 15);
            t.onServer(40, player -> com.senne.imttech.security.os.SecurityOs.open(player, droneCore));
            t.step(10, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicChooseKind();
                }
            });
            t.shot(10, "sd07_logic_choose_kind");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicNew(com.senne.imttech.logic.program.ProgramKind.DRONE);
                    os.tourLogicHelpAt("MOVE");
                }
            });
            t.shot(10, "sd08_logic_drone_help");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.ROBOTS);
                }
            });
            t.shot(15, "sd09_os_robots_drones");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.CAMERAS);
                }
            });
            t.shot(30, "sd10_os_drone_cameras");
            t.close(10);
            // De Ion Blaster: de drone die aan staat valt neer, en ligt scheef op de grond.
            t.onServer(0, player -> {
                for (com.senne.imttech.robot.GuardDroneEntity drone : player.serverLevel().getEntitiesOfClass(
                        com.senne.imttech.robot.GuardDroneEntity.class, new net.minecraft.world.phys.AABB(dockB).inflate(0.5, 4, 0.5))) {
                    if (drone.isActive()) {
                        player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, drone.getBoundingBox().getCenter());
                        com.senne.imttech.robot.IonBlasterItem.fire(player.serverLevel(), player);
                    }
                }
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.view(0, 63.5, -59, 76.0, 180, 25);
            t.shot(40, "sd11_drone_down");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        }

        // --- Hoofdstuk 26: training. De drie trainingsblokken, het instelscherm van Start, de mini-editor van End, het
        // aftellen, en op de desktop de apps Training (een training bezig, twee rapporten), Robots (met squads) en Logic.
        if (want("training")) {
            BlockPos start = new BlockPos(58, -60, 78);
            BlockPos end = new BlockPos(60, -60, 78);
            BlockPos goal = new BlockPos(62, -60, 78);
            BlockPos trainingCore = new BlockPos(72, -60, 68);
            t.onServer(20, player -> {
                ServerLevel level = player.serverLevel();
                ensureBotCore(level, player, trainingCore);
                level.setBlockAndUpdate(start, ModBlocks.TRAINING_START.get().defaultBlockState());
                level.setBlockAndUpdate(end, ModBlocks.TRAINING_END.get().defaultBlockState());
                level.setBlockAndUpdate(goal, ModBlocks.TRAINING_GOAL.get().defaultBlockState());
                if (level.getBlockEntity(end) instanceof com.senne.imttech.training.TrainingEndBlockEntity be) {
                    be.setSource("""
                            # Runs when the trainee is hit.
                            when trainee_hit:
                                say("You're hit, cadet")
                                if lives == 0:
                                    deactivate("Wave 1")
                                    deactivate("Wave 2")
                            """);
                }
                // Een squad van twee robots en een drone ("Wave 1"), en een drone in "Wave 2".
                for (int i = 0; i < 2; i++) {
                    if (DevWorld.spawnStill(level, com.senne.imttech.registry.ModEntities.GUARD_ROBOT.get(),
                            new BlockPos(65 + i * 2, -60, 79), 180) instanceof com.senne.imttech.robot.GuardRobotEntity robot) {
                        com.senne.imttech.robot.RobotStyle style = com.senne.imttech.robot.RobotStyle.BATTLE;
                        robot.setParts(new com.senne.imttech.robot.RobotParts(style, style, style, style));
                        robot.setOwner(player);
                        robot.setSquad("Wave 1");
                    }
                }
                com.senne.imttech.robot.GuardDroneEntity drone = securityDrone(level, player, new BlockPos(69, -59, 79), false);
                drone.setSquad("Wave 2");
                if (level.getBlockEntity(trainingCore) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    com.senne.imttech.training.Training.Mode shots = com.senne.imttech.training.Training.Mode.TRAINING_SHOTS;
                    long now = level.getGameTime();
                    be.addTrainingReport(new com.senne.imttech.training.TrainingReport("Bootcamp", player.getGameProfile().getName(), shots,
                            com.senne.imttech.training.Training.Result.OUT_OF_LIVES, 1440, 3, 0, "Guard 2 x2, Guard 1 x1", 1, now - 4000));
                    be.addTrainingReport(new com.senne.imttech.training.TrainingReport("Bootcamp", player.getGameProfile().getName(), shots,
                            com.senne.imttech.training.Training.Result.GOAL, 2680, 1, 2, "Guard 1 x1", 2, now - 1200));
                    be.logic().put(null, robotProgram("Waves", """
                            when training_started("Bootcamp"):
                                say("Good luck, " + trainee)
                                activate("Wave 1")

                            when squad_down("Wave 1"):
                                activate("Wave 2")
                            """, com.senne.imttech.logic.program.ProgramKind.SERVER));
                }
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.view(0, 60.5, -59, 82.5, 180, 15);
            t.shot(30, "tr01_training_blocks");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.onServer(0, player -> com.senne.imttech.training.TrainingBlocks.useStart(player, start, true));
            t.shot(20, "tr02_training_start_screen");
            t.close(0);
            t.onServer(0, player -> com.senne.imttech.training.TrainingBlocks.useEnd(player, end, true));
            t.shot(20, "tr03_training_end_editor");
            t.close(0);
            // Klik op Start (zonder sneak): jij bent de trainee, I.M.T telt af.
            t.onServer(0, player -> com.senne.imttech.training.TrainingBlocks.useStart(player, start, false));
            t.shot(25, "tr04_training_countdown");
            // De desktop: de app Training (de training is nu bezig), Robots (met squads) en de Help van Logic.
            t.view(0, 67.5, -60, 68.5, 180, 15);
            t.onServer(40, player -> com.senne.imttech.security.os.SecurityOs.open(player, trainingCore));
            t.step(10, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.TRAINING);
                }
            });
            t.shot(25, "tr05_os_training_app");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    // (Sinds de tabel van 19 oktober staan er meer robots in beeld: we scrollen naar de eerste met een squad.)
                    os.tourRobotsSquad();
                }
            });
            t.shot(15, "tr06_os_robots_squads");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicNew(com.senne.imttech.logic.program.ProgramKind.SERVER);
                    os.tourLogicHelpAt("TRAINING");
                }
            });
            t.shot(10, "tr07_logic_training_help");
            t.close(10);
            t.onServer(0, player -> {
                com.senne.imttech.training.Training.Session session = com.senne.imttech.training.Training.of(player.getUUID());
                if (session != null) {
                    com.senne.imttech.training.Training.end(player.serverLevel(), session, com.senne.imttech.training.Training.Result.STOPPED);
                }
            });
            // Hoofdstuk 27: een inbraakoefening. Het instelscherm met Kind = Break-in drill (eerst weer bij het Start-blok gaan
            // staan: het scherm opent alleen dichtbij), en in de app Training het rapport van een geslaagde hack (bovenaan).
            t.view(0, 60.5, -59, 82.5, 180, 15);
            t.onServer(5, player -> {
                ServerLevel level = player.serverLevel();
                if (level.getBlockEntity(start) instanceof com.senne.imttech.training.TrainingStartBlockEntity be) {
                    be.setSettings(new com.senne.imttech.training.Training.Settings("Vault",
                            com.senne.imttech.training.Training.Mode.TRAINING_SHOTS, 3, 300, com.senne.imttech.training.Training.Kind.BREAK_IN));
                }
                if (level.getBlockEntity(trainingCore) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    be.addTrainingReport(new com.senne.imttech.training.TrainingReport("Vault", player.getGameProfile().getName(),
                            com.senne.imttech.training.Training.Mode.TRAINING_SHOTS, com.senne.imttech.training.Training.Result.HACKED,
                            5040, 1, 2, "Guard 1 x1", 0, level.getGameTime(), com.senne.imttech.training.Training.Kind.BREAK_IN, 0));
                }
                com.senne.imttech.training.TrainingBlocks.useStart(player, start, true);
            });
            t.shot(20, "tr08_drill_start_screen");
            t.close(0);
            t.view(0, 67.5, -60, 68.5, 180, 15);
            t.onServer(10, player -> com.senne.imttech.security.os.SecurityOs.open(player, trainingCore));
            t.step(10, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.TRAINING);
                }
            });
            t.shot(25, "tr09_os_drill_report");
            t.close(10);
            // 19 oktober: bij Lethal zijn er geen levens ("none: it lasts until you die"), en dus geen knoppen - en +.
            t.view(0, 60.5, -59, 82.5, 180, 15);
            t.onServer(5, player -> {
                if (player.serverLevel().getBlockEntity(start) instanceof com.senne.imttech.training.TrainingStartBlockEntity be) {
                    be.setSettings(new com.senne.imttech.training.Training.Settings("Bootcamp",
                            com.senne.imttech.training.Training.Mode.LETHAL, 3, 0));
                }
                com.senne.imttech.training.TrainingBlocks.useStart(player, start, true);
            });
            t.shot(20, "tr10_lethal_start_screen");
            t.close(0);
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
                ItemStack chest = new ItemStack(ModItems.TECH_CHESTPLATE.get());
                chest.set(ModDataComponents.ROCKET_PACK.get(), true);
                stand.setItemSlot(EquipmentSlot.CHEST, chest);
                stand.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.TECH_LEGGINGS.get()));
                stand.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.TECH_BOOTS.get()));
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
                        com.senne.imttech.imt.ImtServer.handleMark(player, husk.getId());
                    }
                }
            });
            t.onServer(20, com.senne.imttech.suit.SuitRockets::fire);
            t.shot(3, "r03_rockets_launch");
            t.shot(5, "r04_rockets_turn");
            t.shot(6, "r05_rockets_hit");
            t.camera(0, CameraType.FIRST_PERSON);
            t.shot(40, "r06_rockets_hud");
        }

        // --- Torentjes: een Biebia's Industries-torentje op stroom dat op twee monsters richt, een Raven-torentje
        // (dat jou in creative negeert en rondkijkt) en een Biebia's Industries-torentje dat uit staat (redstone).
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
                    new ItemStack(ModItems.ROCKET_TURRET.get())));
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
            // De generator in het muurtje, van dichtbij: in de kier langs de kast hoort steen te zitten,
            // geen lucht (14 oktober: "de heatgens geven xray langs de blokken").
            t.step(0, () -> Minecraft.getInstance().options.fov().set(30));
            t.view(0, 51.7, -59.9, 38.0, 31, 5);
            t.shot(20, "98a_models_in_wall");
            t.step(0, () -> Minecraft.getInstance().options.fov().set(70));
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        }

        // --- Illusies richten: eerst een valse muur (van bakstenen), met de drone vooruit
        // gevlogen; dan een nep-leger boven op de heuvel (de zombies moeten óp de heuvel staan).
        if (want("illusion")) {
            t.stand(0, -20.5, -60, -12.5, 180, 15);
            t.onServer(0, player -> player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.BRICKS)));
            t.action(10, ImtAction.ILLUSION_WALL);
            t.key(20, () -> Minecraft.getInstance().options.keyJump, true);
            t.key(12, () -> Minecraft.getInstance().options.keyJump, false);
            t.look(5, 180, 25);
            t.shot(15, "90_illusion_aim_wall");
            t.step(0, () -> PacketDistributor.sendToServer(new IllusionPlacePayload(true)));
            t.shot(25, "91_illusion_wall_placed");
            t.stand(0, 2.5, -60, 10.5, -90, 12);
            t.action(10, ImtAction.ILLUSION_ARMY);
            t.key(20, () -> Minecraft.getInstance().options.keyJump, true);
            t.key(30, () -> Minecraft.getInstance().options.keyJump, false);
            t.look(5, -90, 30);
            t.shot(15, "92_illusion_aim_army");
            t.step(0, () -> PacketDistributor.sendToServer(new IllusionPlacePayload(true)));
            t.shot(25, "93_illusion_army_placed");
            t.action(0, ImtAction.ILLUSION_STOP);
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
            // 13 oktober: een poort van drie hoog die omgekeerd is (aan zonder redstone) en liggend: drie
            // platte velden boven elkaar, als laserstralen. Daarna het instelscherm van de middelste zender.
            t.onServer(0, VisualCheck::buildFlatLaserGate);
            t.view(60, 33.5, -59, 23.5, 0, 12);
            t.shot(0, "73_laser_flat");
            t.view(0, 38.5, -56.5, 31.5, 135, 25);
            t.shot(20, "74_laser_flat_angle");
            t.open(0, () -> new com.senne.imttech.client.shield.LaserEmitterScreen(FLAT_GATE.above(), "Dev", "Flat gate"));
            t.shot(20, "75_laser_screen");
            t.close(0);
        }

        // --- De laserbrug (hoofdstuk 34): drie brugzenders in de rand van een gracht. Eerst uit (de pijlpunten op de
        // zenders, en de spleet aan de voorkant), dan uitgeschoven met een armor stand erop: van opzij, van boven, en
        // staand op de brug. Daarna paars, en het instelscherm.
        if (want("bridge")) {
            t.onServer(0, VisualCheck::buildLaserBridge);
            t.view(40, BRIDGE_YARD.getX() - 2.5, BRIDGE_YARD.getY() + 3, BRIDGE_YARD.getZ() + 0.5, -90, 50);
            t.shot(0, "br01_bridge_off_top");
            t.view(0, BRIDGE_YARD.getX() + 4.5, BRIDGE_YARD.getY() + 0.5, BRIDGE_YARD.getZ() + 0.5, 90, 10);
            t.shot(20, "br02_bridge_off_front");
            t.onServer(0, player -> com.senne.imttech.redstone.ImtRedstone.switchAt(player.serverLevel(), BRIDGE_YARD, -1,
                    com.senne.imttech.redstone.ImtRedstone.Op.ON, 0));
            t.onServer(40, player -> {
                var stand = EntityType.ARMOR_STAND.create(player.serverLevel());
                if (stand != null) {
                    stand.moveTo(BRIDGE_YARD.getX() + 3.5, BRIDGE_YARD.getY() + 1, BRIDGE_YARD.getZ() + 1.5, 90, 0);
                    player.serverLevel().addFreshEntity(stand);
                }
            });
            t.view(20, BRIDGE_YARD.getX() + 3.5, BRIDGE_YARD.getY() + 3, BRIDGE_YARD.getZ() + 9.5, 180, 20);
            t.shot(20, "br03_bridge_side");
            t.view(0, BRIDGE_YARD.getX() + 3.5, BRIDGE_YARD.getY() + 9, BRIDGE_YARD.getZ() + 4.5, 180, 60);
            t.shot(20, "br04_bridge_above");
            t.stand(0, BRIDGE_YARD.getX() + 1.5, BRIDGE_YARD.getY() + 0.875, BRIDGE_YARD.getZ() + 0.5, -90, 25);
            t.shot(30, "br05_bridge_on_it");
            t.onServer(0, player -> com.senne.imttech.shield.LaserBridges.handleAction(player,
                    new com.senne.imttech.network.LaserActionPayload(BRIDGE_YARD,
                            com.senne.imttech.network.LaserActionPayload.Op.COLOR, ShieldColor.PURPLE.ordinal())));
            t.view(10, BRIDGE_YARD.getX() - 1.5, BRIDGE_YARD.getY() + 4, BRIDGE_YARD.getZ() + 5.5, -135, 35);
            t.shot(20, "br06_bridge_purple");
            t.open(0, () -> new com.senne.imttech.client.shield.LaserEmitterScreen(BRIDGE_YARD, "Dev", "Moat bridge"));
            t.shot(20, "br07_bridge_screen");
            t.close(0);
        }

        // --- De EMP-granaten (hoofdstuk 35): allebei in je hand, dan op een veldje met een muur, een robot, een turret
        // en een raiddrone. Een sticky vliegt tegen de muur en plakt, de bol rolt over de vloer. Dan gaat de sticky af
        // (de bol van licht), daarna ligt alles stil, en tot slot een granaat aan je eigen voeten: ruis in je bril.
        if (want("grenades")) {
            t.onServer(0, VisualCheck::buildWeaponYard);
            t.onServer(0, player -> {
                player.getInventory().setItem(0, new ItemStack(ModItems.EMP_GRENADE.get(), 16));
                player.getInventory().setItem(1, new ItemStack(ModItems.STICKY_EMP_GRENADE.get(), 8));
                player.getInventory().setItem(2, new ItemStack(ModItems.FRAG_GRENADE.get(), 16));
                player.getInventory().setItem(3, new ItemStack(ModItems.STICKY_FRAG_GRENADE.get(), 8));
                player.getInventory().setItem(5, new ItemStack(ModItems.SMOKE_GRENADE.get(), 16));
                ItemStack red = new ItemStack(ModItems.SMOKE_GRENADE.get(), 16);
                red.set(com.senne.imttech.registry.ModDataComponents.SMOKE_COLOR.get(), net.minecraft.world.item.DyeColor.RED);
                player.getInventory().setItem(6, red);
                player.getInventory().setItem(7, new ItemStack(ModItems.FLASHBANG.get(), 16));
            });
            t.step(0, () -> selectSlot(0));
            t.stand(20, WEAPON_YARD.getX() + 0.5, WEAPON_YARD.getY() + 1, WEAPON_YARD.getZ() + 0.5, -90, 15);
            t.shot(20, "gr01_grenade_in_hand");
            t.step(0, () -> selectSlot(1));
            t.shot(15, "gr02_sticky_in_hand");
            t.step(0, () -> selectSlot(4));
            // Een sticky tegen de muur en een bol over de vloer; allebei met een lange lont, zodat ze blijven liggen.
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                var sticky = new com.senne.imttech.weapons.StickyGrenade(
                        com.senne.imttech.registry.ModEntities.STICKY_EMP_GRENADE.get(), level);
                sticky.setPos(WEAPON_YARD.getX() + 3.5, WEAPON_YARD.getY() + 2.6, WEAPON_YARD.getZ() + 1.5);
                sticky.shoot(1, 0.1, 0, 0.9f, 0);
                level.addFreshEntity(sticky);
                var ball = new com.senne.imttech.weapons.RollingGrenade(
                        com.senne.imttech.registry.ModEntities.ROLLING_EMP_GRENADE.get(), level);
                ball.setPos(WEAPON_YARD.getX() + 2.5, WEAPON_YARD.getY() + 1.5, WEAPON_YARD.getZ() - 1.5);
                ball.shoot(1, 0.2, 0.15, 0.35f, 0);
                ball.arm(4000);
                level.addFreshEntity(ball);
            });
            t.onServer(15, player -> player.serverLevel().getEntitiesOfClass(
                    com.senne.imttech.weapons.GrenadeEntity.class, player.getBoundingBox().inflate(16))
                    .forEach(grenade -> grenade.arm(4000)));
            t.view(40, WEAPON_YARD.getX() + 2.5, WEAPON_YARD.getY() + 2.6, WEAPON_YARD.getZ() + 4.5, -120, 20);
            t.shot(0, "gr03_grenades_lying");
            t.view(0, WEAPON_YARD.getX() + 5.2, WEAPON_YARD.getY() + 2.3, WEAPON_YARD.getZ() + 1.5, -90, 30);
            t.shot(15, "gr04_sticky_close");
            // De sticky gaat af.
            t.view(0, WEAPON_YARD.getX() - 3.5, WEAPON_YARD.getY() + 3.5, WEAPON_YARD.getZ() + 0.5, -90, 15);
            t.onServer(20, player -> player.serverLevel().getEntitiesOfClass(
                            com.senne.imttech.weapons.StickyGrenade.class, player.getBoundingBox().inflate(16))
                    .forEach(grenade -> grenade.arm(1)));
            t.shot(4, "gr05_emp_blast");
            t.shot(40, "gr06_emp_everything_off");
            t.view(0, WEAPON_YARD.getX() + 2.5, WEAPON_YARD.getY() + 2.5, WEAPON_YARD.getZ() + 0.5, -135, 25);
            t.shot(15, "gr07_emp_robot_down");
            // Een granaat aan je eigen voeten, met de bril op: ruis.
            t.onServer(0, player -> {
                DevWorld.wearGlasses(player);
                com.senne.imttech.weapons.Emp.blast(player.serverLevel(), player.position().add(0, 0.5, 0), player);
            });
            t.shot(15, "gr08_emp_in_your_glasses");
            t.onServer(0, player -> {
                player.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
                player.setData(com.senne.imttech.registry.ModAttachments.EMP_UNTIL, 0L);
            });
            // De frag-kopieën in je hand, en een Sticky Frag Grenade tegen de muur: een gat, geen EMP.
            t.step(0, () -> selectSlot(2));
            t.stand(0, WEAPON_YARD.getX() + 0.5, WEAPON_YARD.getY() + 1, WEAPON_YARD.getZ() + 0.5, -90, 15);
            t.shot(15, "gr09_frag_in_hand");
            t.step(0, () -> selectSlot(3));
            t.shot(10, "gr10_sticky_frag_in_hand");
            t.step(0, () -> selectSlot(4));
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                var frag = new com.senne.imttech.weapons.StickyGrenade(
                        com.senne.imttech.registry.ModEntities.STICKY_FRAG_GRENADE.get(), level);
                frag.setPos(WEAPON_YARD.getX() + 3.5, WEAPON_YARD.getY() + 2.6, WEAPON_YARD.getZ() - 2.5);
                frag.shoot(1, 0.1, 0, 0.9f, 0);
                level.addFreshEntity(frag);
            });
            t.view(15, WEAPON_YARD.getX() + 3.5, WEAPON_YARD.getY() + 2.5, WEAPON_YARD.getZ() - 2.5, -90, 15);
            t.shot(0, "gr11_sticky_frag_on_wall");
            t.view(0, WEAPON_YARD.getX() + 0.5, WEAPON_YARD.getY() + 3.5, WEAPON_YARD.getZ() - 2.5, -90, 15);
            t.shot(30, "gr12_frag_blast");
            t.shot(20, "gr13_frag_hole");
            // Twee smokebommen (grijs en rood) over de vloer gerold, en de flashbang.
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                for (int i = 0; i < 2; i++) {
                    var smoke = new com.senne.imttech.weapons.RollingGrenade(
                            com.senne.imttech.registry.ModEntities.SMOKE_GRENADE.get(), level);
                    smoke.setPos(WEAPON_YARD.getX() + 1.5, WEAPON_YARD.getY() + 1.5, WEAPON_YARD.getZ() + (i == 0 ? -3.5 : 3.5));
                    smoke.setSmokeColor(i == 0 ? null : net.minecraft.world.item.DyeColor.RED);
                    smoke.shoot(1, 0.1, 0, 0.3f, 0);
                    smoke.arm(10);
                    level.addFreshEntity(smoke);
                }
            });
            t.view(0, WEAPON_YARD.getX() - 6.5, WEAPON_YARD.getY() + 4, WEAPON_YARD.getZ() + 0.5, -90, 20);
            t.shot(30, "gr14_smoke_starts");
            t.shot(50, "gr15_smoke_clouds");
            t.view(0, WEAPON_YARD.getX() + 3.5, WEAPON_YARD.getY() + 1.6, WEAPON_YARD.getZ() + 3.5, 0, 0);
            t.shot(10, "gr16_inside_red_smoke");
            // De smokebommen weg: hun rook trekt dan snel op (anders staan de volgende foto's in de rook).
            t.onServer(0, VisualCheck::removeSmokeGrenades);
            // De flashbang: vlak voor je, en je kijkt ernaar.
            t.stand(0, WEAPON_YARD.getX() - 4.5, WEAPON_YARD.getY() + 1, WEAPON_YARD.getZ() + 10.5, -90, 10);
            t.onServer(20, player -> com.senne.imttech.weapons.Flashbang.bang(player.serverLevel(),
                    new Vec3(WEAPON_YARD.getX() - 1.5, WEAPON_YARD.getY() + 1.5, WEAPON_YARD.getZ() + 10.5), null));
            t.shot(4, "gr17_flashbang_white");
            t.shot(60, "gr18_flashbang_fading");
            // De Remote EMP: tegen de muur, met de EMP-trigger in je hand; dan de knop.
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                BlockPos emp = WEAPON_YARD.offset(6, 2, -3);
                level.setBlockAndUpdate(emp, ModBlocks.REMOTE_EMP.get().defaultBlockState()
                        .setValue(com.senne.imttech.weapons.RemoteChargeBlock.FACING, Direction.WEST));
                ItemStack trigger = new ItemStack(ModItems.EMP_TRIGGER.get());
                if (level.getBlockEntity(emp) instanceof com.senne.imttech.weapons.RemoteChargeBlockEntity entity) {
                    com.senne.imttech.weapons.TriggerItem.link(level, emp, entity, trigger);
                }
                player.getInventory().setItem(4, trigger);
            });
            t.step(0, () -> selectSlot(4));
            t.stand(0, WEAPON_YARD.getX() + 3.5, WEAPON_YARD.getY() + 1, WEAPON_YARD.getZ() - 2.5, -90, 12);
            t.shot(20, "gr19_remote_emp_and_trigger");
            t.onServer(0, player -> com.senne.imttech.weapons.TriggerItem.fire(player.serverLevel(), player,
                    player.getMainHandItem()));
            t.shot(4, "gr20_remote_emp_blast");
            // De rook gaat niet door blokken (26 oktober): een smokebom in een schuurtje. De muren en het dak houden hem
            // binnen, door het raam zie je hem, en door de open deur stroomt een beetje naar buiten.
            t.onServer(0, VisualCheck::buildSmokeShed);
            t.stand(0, WEAPON_YARD.getX() + 0.5, WEAPON_YARD.getY() + 1, WEAPON_YARD.getZ() + 8.5, 0, 8);
            t.shot(90, "gr21_smoke_stays_in_the_shed");
            t.view(0, WEAPON_YARD.getX() + 7.5, WEAPON_YARD.getY() + 8, WEAPON_YARD.getZ() + 10.5, 45, 35);
            t.shot(10, "gr22_smoke_not_through_the_roof");
            t.view(0, WEAPON_YARD.getX() - 0.5, WEAPON_YARD.getY() + 2, WEAPON_YARD.getZ() + 11.5, 0, 5);
            t.shot(10, "gr23_smoke_out_of_the_door");
            t.onServer(0, VisualCheck::removeSmokeGrenades);
        }

        // --- Hoofdstuk 69: granaten oprapen en teruggooien, een sticky van je af trekken, een Remote Frag verhangen.
        // Alles met de echte toetsen: rechtsklik (met een Glock in je hand, die anders zou richten) en sneak.
        if (want("throwback")) {
            double gx = WEAPON_YARD.getX(), gy = WEAPON_YARD.getY(), gz = WEAPON_YARD.getZ();
            t.command(0, "gamemode survival @a");
            t.onServer(0, VisualCheck::buildWeaponYard);
            t.onServer(0, player -> {
                // Zonder de raketturret: in survival schiet die op jou (en duwt je weg van de granaat).
                player.serverLevel().setBlockAndUpdate(WEAPON_YARD.offset(5, 1, 3), Blocks.AIR.defaultBlockState());
                player.setHealth(player.getMaxHealth());
                player.getInventory().clearContent();
                player.getInventory().setItem(0, com.senne.imttech.guns.FirearmItem.loaded(
                        com.senne.imttech.guns.FirearmType.GLOCK_19, com.senne.imttech.guns.GunMaterial.STEEL));
                com.senne.imttech.guns.Gear.set(player, com.senne.imttech.guns.GearItem.Kind.HOLSTER,
                        new ItemStack(ModItems.HOLSTER.get()));
            });
            t.step(0, () -> selectSlot(0));
            t.stand(20, gx + 0.5, gy + 1, gz + 0.5, -90, 30);
            // Een EMP-bal rolt voor je voeten (de lont: 5 seconden, zodat er tijd is voor de foto's).
            t.onServer(0, player -> {
                var ball = new com.senne.imttech.weapons.RollingGrenade(ModEntities.ROLLING_EMP_GRENADE.get(), player.serverLevel());
                ball.setPos(gx + 2.5, gy + 1.0, gz + 0.5);
                ball.arm(100);
                player.serverLevel().addFreshEntity(ball);
            });
            t.lookAt(10, new Vec3(gx + 2.5, gy + 1.15, gz + 0.5));
            t.shot(4, "tb01_grenade_at_your_feet");
            t.tap(0, () -> Minecraft.getInstance().options.keyUse);
            t.shot(8, "tb02_live_grenade_in_hand");
            t.camera(0, CameraType.THIRD_PERSON_FRONT);
            t.shot(6, "tb03_glock_in_holster");
            t.camera(0, CameraType.FIRST_PERSON);
            // Terug, naar de muur bij de robot.
            t.look(0, -90, -8);
            t.tap(4, () -> Minecraft.getInstance().options.keyUse);
            t.shot(6, "tb04_thrown_back");
            t.waitUntil(120, () -> Minecraft.getInstance().level != null && Minecraft.getInstance().level
                    .getEntitiesOfClass(com.senne.imttech.weapons.RollingGrenade.class,
                            new net.minecraft.world.phys.AABB(WEAPON_YARD).inflate(16)).isEmpty());
            t.shot(3, "tb05_emp_at_the_robot");
            // Een sticky plakt op jou; je eerste rechtsklik trekt hem eraf.
            t.onServer(30, player -> {
                var sticky = new com.senne.imttech.weapons.StickyGrenade(ModEntities.STICKY_EMP_GRENADE.get(), player.serverLevel());
                sticky.setPos(player.getX() + 3, player.getY() + 1.0, player.getZ());
                sticky.shoot(-1, 0.05, 0, 0.8f, 0);
                player.serverLevel().addFreshEntity(sticky);
            });
            t.onServer(8, player -> player.serverLevel().getEntitiesOfClass(com.senne.imttech.weapons.StickyGrenade.class,
                    player.getBoundingBox().inflate(4)).forEach(sticky -> sticky.arm(100)));
            t.camera(0, CameraType.THIRD_PERSON_FRONT);
            t.shot(4, "tb06_sticky_on_you");
            t.camera(0, CameraType.FIRST_PERSON);
            t.tap(2, () -> Minecraft.getInstance().options.keyUse);
            t.shot(8, "tb07_sticky_pulled_off");
            t.look(0, -90, -8);
            t.tap(2, () -> Minecraft.getInstance().options.keyUse);
            t.shot(6, "tb08_sticky_thrown");
            // Een Remote Frag van iemand anders (Raven) op de muur: sneak + rechtsklik met een lege hand, en hij hangt nu
            // op de vloer, nog altijd aan de trigger van Raven.
            t.onServer(40, player -> {
                ServerLevel level = player.serverLevel();
                BlockPos frag = WEAPON_YARD.offset(6, 2, 2);
                level.setBlockAndUpdate(frag, ModBlocks.REMOTE_FRAG.get().defaultBlockState()
                        .setValue(com.senne.imttech.weapons.RemoteChargeBlock.FACING, Direction.WEST));
                if (level.getBlockEntity(frag) instanceof com.senne.imttech.weapons.RemoteChargeBlockEntity entity) {
                    entity.restore(new com.senne.imttech.weapons.LiveCharge(UUID.randomUUID(), java.util.Optional.empty(), "Raven",
                            java.util.Optional.empty()));
                }
            });
            t.stand(0, gx + 4.5, gy + 1, gz + 2.5, -90, 10);
            t.step(0, () -> selectSlot(0));
            t.lookAt(10, new Vec3(gx + 6.5, gy + 2.5, gz + 2.5));
            t.shot(4, "tb09_remote_frag_on_your_wall");
            // Eerst sluipen, dan mikken: sluipen zet je ogen lager (anders zakt het kruisje onder de lading, op de muur).
            t.key(0, () -> Minecraft.getInstance().options.keyShift, true);
            t.lookAt(4, new Vec3(gx + 6.85, gy + 2.5, gz + 2.5));
            t.tap(2, () -> Minecraft.getInstance().options.keyUse);
            t.key(4, () -> Minecraft.getInstance().options.keyShift, false);
            t.shot(4, "tb10_remote_frag_taken_live");
            t.lookAt(0, new Vec3(gx + 3.5, gy + 1.0, gz + 2.5));
            t.tap(4, () -> Minecraft.getInstance().options.keyUse);
            t.shot(6, "tb11_remote_frag_hung_elsewhere");
        }

        // --- De schermmuur (19 oktober): de monitoren zonder camera, het instelscherm, het live scherm, en wat er misgaat
        if (want("camerawall")) {
            BlockPos core = CAMERA_WALL;
            BlockPos screen = core.offset(0, 1, -6);
            t.step(0, () -> com.senne.imttech.lan.LanGrid.overrideRequired(true));
            t.onServer(0, player -> buildCameraWall(player, CAMERA_WALL));
            t.view(20, core.getX() + 0.5, core.getY() + 1.8, core.getZ() + 0.5, 180, 5);
            t.onServer(5, player -> wakeCameraWall(player, CAMERA_WALL));
            t.shot(30, "cw01_monitors_without_camera");
            t.onServer(5, player -> com.senne.imttech.security.wall.CameraWall.open(player, screen));
            t.shot(15, "cw02_wall_screen_empty");
            t.step(0, () -> dragCamera("Lobby", 0, 0, 1, 1));
            t.shot(5, "cw03_wall_screen_dragging");
            t.step(0, VisualCheck::dropCamera);
            t.step(5, () -> dragCamera("Vault", 2, 0, 2, 0));
            t.step(0, VisualCheck::dropCamera);
            t.step(5, () -> dragCamera("Guard 9", 2, 1, 2, 1));
            t.step(0, VisualCheck::dropCamera);
            t.shot(40, "cw04_wall_screen_filled");
            t.close(5);
            t.onServer(5, player -> wakeCameraWall(player, CAMERA_WALL));
            t.shot(50, "cw05_wall_live");
            // Dichterbij, om de randjes en de tekst te zien.
            t.view(0, core.getX() - 0.2, core.getY() + 2.0, core.getZ() - 3.4, 170, 2);
            t.shot(20, "cw06_wall_close");
            // Een camera uit: NO SIGNAL op zijn tegel.
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                BlockPos vault = core.offset(-1, 0, -3);
                level.setBlockAndUpdate(vault, level.getBlockState(vault).setValue(com.senne.imttech.security.CameraBlock.ENABLED, false));
                if (level.getBlockEntity(vault) instanceof com.senne.imttech.security.CameraBlockEntity camera) {
                    camera.updateOnline(level, vault, level.getBlockState(vault));
                }
                wakeCameraWall(player, CAMERA_WALL);
            });
            t.view(5, core.getX() + 0.5, core.getY() + 1.8, core.getZ() + 0.5, 180, 5);
            t.shot(20, "cw07_wall_no_signal");
            // Een monitor erbij buiten de rechthoek: de melding, en het scherm gaat uit.
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(core.offset(2, 1, -6), ModBlocks.CAMERA_MONITOR.get().defaultBlockState()
                        .setValue(com.senne.imttech.security.wall.CameraMonitorBlock.FACING, Direction.SOUTH));
                wakeCameraWall(player, CAMERA_WALL);
                com.senne.imttech.security.wall.CameraWall.open(player, screen);
            });
            t.shot(10, "cw08_not_a_rectangle");
            t.onServer(0, player -> player.serverLevel().setBlockAndUpdate(core.offset(2, 1, -6), Blocks.AIR.defaultBlockState()));
            // Een scherm van 8 x 6: past het instelscherm op een klein venster?
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                BlockPos far = core.offset(4, 0, 8);
                for (int x = 0; x < 8; x++) {
                    for (int y = 0; y < 6; y++) {
                        level.setBlockAndUpdate(far.offset(-x, y, 0), ModBlocks.CAMERA_MONITOR.get().defaultBlockState()
                                .setValue(com.senne.imttech.security.wall.CameraMonitorBlock.FACING, Direction.NORTH));
                    }
                }
                for (int z = 1; z <= 7; z++) {
                    com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.POE, level, core.south(z));
                }
            });
            t.onServer(5, player -> com.senne.imttech.security.wall.CameraWall.open(player, core.offset(4, 0, 8)));
            t.shot(10, "cw09_wall_screen_8x6");
            t.close(5);
            t.step(0, () -> com.senne.imttech.lan.LanGrid.overrideRequired(null));
        }

        // --- De lasergeweren (hoofdstuk 35): elk geweer vanuit de heup, gericht en schietend; het geweer herladen en
        // bekijken; de kijker van de sniper; de pomp van de shotgun; sprinten; derde persoon; en een geweer zonder cel.
        if (want("guns")) {
            t.onServer(0, VisualCheck::buildGunRange);
            t.onServer(0, player -> {
                var inventory = player.getInventory();
                inventory.clearContent();
                com.senne.imttech.weapons.GunType[] types = com.senne.imttech.weapons.GunType.values();
                for (int i = 0; i < types.length; i++) {
                    ItemStack gun = com.senne.imttech.weapons.LaserGunItem.complete(types[i], com.senne.imttech.guns.GunMaterial.TITANIUM);
                    com.senne.imttech.weapons.LaserGunItem.setCell(gun, i == 1 ? 45 : 100);
                    inventory.setItem(i, gun);
                }
                inventory.setItem(4, com.senne.imttech.weapons.EnergyCellItem.withCharge(100));
                inventory.setItem(5, com.senne.imttech.weapons.EnergyCellItem.withCharge(60));
                inventory.setItem(6, new ItemStack(ModItems.ENERGY_CELL.get()));
                inventory.setItem(8, com.senne.imttech.weapons.LaserGunItem.complete(com.senne.imttech.weapons.GunType.PISTOL,
                        com.senne.imttech.guns.GunMaterial.TITANIUM));
                // Hoofdstuk 57: R kiest een cel uit je magazijnpouch; zonder pouch haalt R de cel eruit.
                ItemStack pouch = new ItemStack(ModItems.MAG_POUCH.get());
                com.senne.imttech.guns.GearItem.setContents(pouch, java.util.List.of(com.senne.imttech.weapons.EnergyCellItem.withCharge(100)));
                com.senne.imttech.guns.Gear.set(player, com.senne.imttech.guns.GearItem.Kind.MAG_POUCH, pouch);
            });
            t.stand(20, GUN_RANGE.getX() + 0.5, GUN_RANGE.getY() + 1, GUN_RANGE.getZ() + 0.5, 0, 2);
            t.step(0, () -> selectSlot(0));
            t.shot(25, "g01_pistol_hip");
            t.key(0, () -> Minecraft.getInstance().options.keyUse, true);
            t.shot(12, "g02_pistol_aim");
            t.key(0, () -> Minecraft.getInstance().options.keyAttack, true);
            t.key(1, () -> Minecraft.getInstance().options.keyAttack, false);
            t.shot(0, "g03_pistol_fire");
            t.key(8, () -> Minecraft.getInstance().options.keyUse, false);
            t.step(0, () -> selectSlot(1));
            t.shot(15, "g04_rifle_hip");
            t.key(0, () -> Minecraft.getInstance().options.keyUse, true);
            t.shot(12, "g05_rifle_aim");
            t.key(0, () -> Minecraft.getInstance().options.keyUse, false);
            t.key(10, () -> Minecraft.getInstance().options.keyAttack, true);
            t.shot(7, "g06_rifle_firing");
            t.key(0, () -> Minecraft.getInstance().options.keyAttack, false);
            t.step(15, () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()));
            t.step(5, () -> ShowcaseFeatures.magPick(0));
            t.shot(10, "g07_rifle_reload_out");
            t.shot(14, "g08_rifle_reload_in");
            // Hoofdstuk 57: U haalt de cel eruit (in je pouch, daar is nu plek); rechtsklik met een cel doet hem er weer in.
            t.step(40, () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_UNLOAD.getKey()));
            t.shot(12, "g08b_rifle_unload_cell_out");
            t.step(30, () -> selectSlot(4));
            t.step(5, () -> KeyMapping.click(Minecraft.getInstance().options.keyUse.getKey()));
            t.shot(10, "g08c_cell_in_hand_right_click");
            t.step(30, () -> selectSlot(1));
            t.shot(15, "g08d_rifle_loaded_by_hand");
            t.step(20, () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_INSPECT.getKey()));
            t.shot(12, "g09_rifle_inspect_side");
            t.shot(18, "g10_rifle_inspect_top");
            t.step(30, () -> selectSlot(2));
            t.shot(15, "g11_sniper_hip");
            t.key(0, () -> Minecraft.getInstance().options.keyUse, true);
            t.shot(15, "g12_sniper_scope");
            t.key(0, () -> Minecraft.getInstance().options.keyAttack, true);
            t.key(1, () -> Minecraft.getInstance().options.keyAttack, false);
            t.shot(0, "g13_sniper_fire_scope");
            t.key(8, () -> Minecraft.getInstance().options.keyUse, false);
            t.shot(10, "g14_sniper_bolt");
            t.step(20, () -> selectSlot(3));
            t.shot(15, "g15_shotgun_hip");
            t.key(0, () -> Minecraft.getInstance().options.keyAttack, true);
            t.key(1, () -> Minecraft.getInstance().options.keyAttack, false);
            t.shot(0, "g16_shotgun_fire");
            t.shot(5, "g17_shotgun_pump");
            // Sprinten: vooruit lopen met de sprinttoets in.
            t.step(15, () -> selectSlot(1));
            t.key(10, () -> Minecraft.getInstance().options.keySprint, true);
            t.key(0, () -> Minecraft.getInstance().options.keyUp, true);
            t.shot(12, "g18_rifle_sprint");
            t.key(0, () -> Minecraft.getInstance().options.keyUp, false);
            t.key(0, () -> Minecraft.getInstance().options.keySprint, false);
            // Derde persoon: van voren, met het geweer vast.
            t.stand(5, GUN_RANGE.getX() + 0.5, GUN_RANGE.getY() + 1, GUN_RANGE.getZ() + 0.5, 0, 2);
            t.camera(5, CameraType.THIRD_PERSON_FRONT);
            t.shot(20, "g19_third_person_front");
            t.camera(0, CameraType.THIRD_PERSON_BACK);
            t.shot(15, "g20_third_person_back");
            t.camera(0, CameraType.FIRST_PERSON);
            // Een pistool zonder cel: de HUD zegt het.
            t.step(5, () -> selectSlot(8));
            t.shot(15, "g21_no_cell");
        }

        // --- De kijker van de sniper in F5 (8 november, jouw opmerking: "als je met een sniper scopet in F5 dan zie je
        // jezelf door de scope"): richten in derde persoon (nog zonder zoom), door de kijker kijk je door je eigen ogen,
        // F5 tijdens het richten kiest de stand voor straks (van voren), en losgelaten sta je meteen weer in derde
        // persoon. De stand van de camera staat telkens in de log ("[dev] scopef5").
        if (want("scopef5")) {
            t.onServer(0, VisualCheck::buildGunRange);
            t.onServer(0, player -> {
                player.getInventory().clearContent();
                ItemStack sniper = com.senne.imttech.weapons.LaserGunItem.complete(com.senne.imttech.weapons.GunType.SNIPER,
                        com.senne.imttech.guns.GunMaterial.TITANIUM);
                com.senne.imttech.weapons.LaserGunItem.setCell(sniper, 100);
                player.getInventory().setItem(0, sniper);
            });
            t.stand(20, GUN_RANGE.getX() + 0.5, GUN_RANGE.getY() + 1, GUN_RANGE.getZ() + 0.5, 0, 2);
            t.step(0, () -> selectSlot(0));
            t.camera(0, CameraType.THIRD_PERSON_BACK);
            t.shot(25, "sf01_f5_sniper_hip");
            t.step(0, () -> logScopeCamera("hip"));
            t.key(0, () -> Minecraft.getInstance().options.keyUse, true);
            t.shot(2, "sf02_f5_sniper_raising");
            t.step(0, () -> logScopeCamera("raising"));
            t.shot(12, "sf03_f5_sniper_scope");
            t.step(0, () -> logScopeCamera("scope"));
            t.key(0, () -> Minecraft.getInstance().options.keyAttack, true);
            t.key(1, () -> Minecraft.getInstance().options.keyAttack, false);
            t.shot(0, "sf04_f5_sniper_fire");
            t.step(10, () -> KeyMapping.click(Minecraft.getInstance().options.keyTogglePerspective.getKey()));
            t.shot(3, "sf05_f5_pressed_while_scoped");
            t.step(0, () -> logScopeCamera("F5 while scoped"));
            t.key(0, () -> Minecraft.getInstance().options.keyUse, false);
            t.shot(12, "sf06_released_front");
            t.step(0, () -> logScopeCamera("released"));
            t.camera(0, CameraType.FIRST_PERSON);
        }

        // --- De acht creative-tabbladen van I.M.T (26 oktober), elk een plaatje; de muis in de hoek, anders ligt er
        // een tooltip over het midden. Het wapentabblad is langer dan het scherm (hoofdstuk 59): twee keer drie rijen
        // verder gescrold, zodat ook de onderdelen van de Glock en de lasergeweren op een plaatje staan.
        if (want("creative")) {
            t.command(0, "gamemode creative @a");
            int number = 1;
            for (var tab : com.senne.imttech.registry.ModCreativeTabs.TABS.getEntries()) {
                String name = String.format(java.util.Locale.ROOT, "ct%02d_%s", number++, tab.getId().getPath());
                t.step(5, () -> ShowcaseFeatures.openModTab(tab.get()));
                t.step(2, VisualCheck::parkMouse);
                t.shot(13, name);
                // Op de id vergelijken: de rondleiding wordt opgebouwd voor de tabbladen geregistreerd zijn (get() zou
                // dan "unbound value" geven).
                if (tab.getId().equals(com.senne.imttech.registry.ModCreativeTabs.WEAPONS.getId())) {
                    t.step(0, () -> scrollScreen(-3));
                    t.shot(10, name + "_b_glock_parts");
                    t.step(0, () -> scrollScreen(-3));
                    t.shot(10, name + "_c_laser_parts");
                }
            }
            t.close(0);
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
                if (level.getBlockEntity(padAt.below()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
                    generator.fill(200_000);
                }
                if (level.getBlockEntity(padAt) instanceof com.senne.imttech.suit.LandingPadBlockEntity pad) {
                    pad.setOwner(player.getUUID());
                    pad.cells().setItem(0, new ItemStack(ModItems.BATTERY_UPGRADE.get(), 2));
                }
            });
            t.stand(0, padAt.getX() + 0.5, -60, padAt.getZ() + 2.5, 180, 30);
            t.onServer(80, player -> {
                if (player.serverLevel().getBlockEntity(padAt) instanceof com.senne.imttech.suit.LandingPadBlockEntity pad) {
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
                    com.senne.imttech.energy.PowerCables.add(level, at.east(i));
                    com.senne.imttech.energy.PowerCables.add(level, at.west(i));
                }
                if (level.getBlockEntity(at) instanceof com.senne.imttech.energy.PowerCabinetBlockEntity cabinet) {
                    cabinet.setOwner(player.getUUID());
                }
                com.senne.imttech.energy.PowerCabinets.setSwitch(level, at, Direction.WEST, false);
            });
            t.stand(10, at.getX() + 0.5, -60, at.getZ() + 3.5, 180, 25);
            t.shot(20, "c01_cabinet");
            t.onServer(0, player -> com.senne.imttech.energy.PowerCabinets.open(player, at));
            t.shot(20, "c02_cabinet_screen");
            t.close(0);
        }

        // --- De knop /tick, /sec, /min, /hour en het overzicht van het net: een warmtegenerator met drie
        // holotafels erop (die gebruiken meer dan hij maakt), het scherm, en vier keer op de knop geklikt.
        if (want("power")) {
            BlockPos at = new BlockPos(-8, -60, 4);
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(at, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                if (level.getBlockEntity(at) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
                    generator.setItem(com.senne.imttech.station.ChargerBlockEntity.SLOT_FUEL,
                            new ItemStack(net.minecraft.world.item.Items.COAL, 8));
                    generator.fill(50_000);
                }
                for (BlockPos table : new BlockPos[] {at.east(), at.west(), at.north()}) {
                    level.setBlockAndUpdate(table, ModBlocks.HOLO_TABLE.get().defaultBlockState());
                }
            });
            t.step(0, () -> com.senne.imttech.client.ImtClientConfig.setPowerUnit(com.senne.imttech.client.PowerUnit.TICK));
            t.stand(10, at.getX() + 0.5, -60, at.getZ() + 3.5, 180, 25);
            // Het net meet 6 seconden voordat het overzicht iets zegt.
            t.onServer(150, player -> {
                if (player.serverLevel().getBlockEntity(at) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
                    player.openMenu(generator, buffer -> buffer.writeBoolean(generator.usesFuel()));
                }
            });
            t.shot(20, "pw01_unit_tick");
            String[] names = {"pw02_unit_second", "pw03_unit_minute", "pw04_unit_hour", "pw05_unit_tick_again"};
            for (String name : names) {
                t.step(0, VisualCheck::clickPowerUnitButton);
                t.shot(5, name);
            }
            t.close(0);
        }

        // --- Hoofdstuk 66: hoe grote getallen eruitzien (5000, 5 000, 5.000, 5,000, 5k). De warmtegenerator met holotafels,
        // de Power Battery, het landingsplatform, het laadstation, het G-menu, de koepel en de Arc Lamp, in elke stijl; de
        // stijl wisselt met een klik op het knopje naast /tick, zoals een speler dat doet.
        if (want("numbers")) {
            BlockPos at = new BlockPos(-8, -60, 4);
            BlockPos padAt = new BlockPos(-4, -61, -4);
            BlockPos lampAt = new BlockPos(-12, -60, 8);
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(at, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                if (level.getBlockEntity(at) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
                    generator.setItem(com.senne.imttech.station.ChargerBlockEntity.SLOT_FUEL,
                            new ItemStack(net.minecraft.world.item.Items.COAL, 8));
                    generator.fill(45_678);
                }
                for (BlockPos table : new BlockPos[] {at.east(), at.west(), at.north()}) {
                    level.setBlockAndUpdate(table, ModBlocks.HOLO_TABLE.get().defaultBlockState());
                }
                level.setBlockAndUpdate(padAt.below(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                level.setBlockAndUpdate(padAt, ModBlocks.LANDING_PAD.get().defaultBlockState());
                if (level.getBlockEntity(padAt.below()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
                    generator.fill(200_000);
                }
                if (level.getBlockEntity(padAt) instanceof com.senne.imttech.suit.LandingPadBlockEntity pad) {
                    pad.setOwner(player.getUUID());
                    pad.cells().setItem(0, new ItemStack(ModItems.BATTERY_UPGRADE.get(), 2));
                }
                level.setBlockAndUpdate(lampAt, ModBlocks.ARC_LAMP.get().defaultBlockState());
                buildBatteryYard(player, BATTERY_YARD);
            });
            t.step(0, () -> com.senne.imttech.client.ImtClientConfig.setPowerUnit(com.senne.imttech.client.PowerUnit.HOUR));
            t.step(0, () -> com.senne.imttech.client.ImtClientConfig.setNumberStyle(com.senne.imttech.config.NumberStyle.PLAIN));
            t.stand(10, at.getX() + 0.5, -60, at.getZ() + 3.5, 180, 25);
            // De generator (het net meet eerst 6 seconden): elke stijl een foto, het knopje telkens een keer verder.
            t.onServer(150, player -> {
                if (player.serverLevel().getBlockEntity(at) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
                    player.openMenu(generator, buffer -> buffer.writeBoolean(generator.usesFuel()));
                }
            });
            for (com.senne.imttech.config.NumberStyle style : com.senne.imttech.config.NumberStyle.values()) {
                t.shot(20, "nb01_generator_" + style.name().toLowerCase());
                t.step(0, VisualCheck::clickNumberStyleButton);
            }
            t.close(0);
            // De Power Battery: de doos met de poorten en cellen (het knopje staat nu weer op PLAIN).
            BlockPos port = BATTERY_YARD.offset(0, 0, 2);
            t.view(10, BATTERY_YARD.getX() + 2.5, BATTERY_YARD.getY() + 1.6, BATTERY_YARD.getZ() + 7.5, 180, 5);
            t.onServer(20, player -> {
                if (player.serverLevel().getBlockEntity(port) instanceof com.senne.imttech.battery.BatteryPortBlockEntity entity) {
                    entity.tourHistory();
                }
                com.senne.imttech.battery.Batteries.open(player.serverLevel(), port, player);
            });
            for (com.senne.imttech.config.NumberStyle style : com.senne.imttech.config.NumberStyle.values()) {
                t.shot(20, "nb02_battery_" + style.name().toLowerCase());
                t.step(0, VisualCheck::clickNumberStyleButton);
            }
            t.close(0);
            // Het landingsplatform en het laadstation hebben geen knopje: de stijl uit de instellingen.
            t.stand(0, padAt.getX() + 0.5, -60, padAt.getZ() + 2.5, 180, 30);
            for (com.senne.imttech.config.NumberStyle style : com.senne.imttech.config.NumberStyle.values()) {
                t.step(0, () -> com.senne.imttech.client.ImtClientConfig.setNumberStyle(style));
                t.onServer(style == com.senne.imttech.config.NumberStyle.PLAIN ? 60 : 5, player -> {
                    if (player.serverLevel().getBlockEntity(padAt) instanceof com.senne.imttech.suit.LandingPadBlockEntity pad) {
                        player.openMenu(pad, padAt);
                    }
                });
                t.shot(20, "nb03_pad_" + style.name().toLowerCase());
                t.close(0);
            }
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(padAt.east(3), ModBlocks.CHARGING_STATION.get().defaultBlockState());
                if (level.getBlockEntity(padAt.east(3)) instanceof com.senne.imttech.station.ChargingStationBlockEntity station) {
                    ItemStack glasses = DevWorld.ownGlasses(player);
                    com.senne.imttech.imt.ImtData.setEnergy(glasses, 87_654);
                    station.setItem(com.senne.imttech.station.ChargingStationBlockEntity.SLOT_GLASSES, glasses);
                }
            });
            for (com.senne.imttech.config.NumberStyle style : new com.senne.imttech.config.NumberStyle[] {
                    com.senne.imttech.config.NumberStyle.PLAIN, com.senne.imttech.config.NumberStyle.SPACE,
                    com.senne.imttech.config.NumberStyle.SHORT}) {
                t.step(0, () -> com.senne.imttech.client.ImtClientConfig.setNumberStyle(style));
                t.onServer(5, player -> {
                    if (player.serverLevel().getBlockEntity(padAt.east(3)) instanceof com.senne.imttech.station.ChargingStationBlockEntity station) {
                        player.openMenu(station);
                    }
                });
                t.shot(20, "nb04_station_" + style.name().toLowerCase());
                t.close(0);
            }
            // Het G-menu in de stijl met punten: Systems (de energie van de bril) en Information (de optie Numbers).
            t.step(0, () -> com.senne.imttech.client.ImtClientConfig.setNumberStyle(com.senne.imttech.config.NumberStyle.DOT));
            t.view(0, 0.5, -60, -3.2, 0, 22);
            t.step(0, () -> com.senne.imttech.client.ImtClientState.menuTab = com.senne.imttech.client.MenuTab.SYSTEMS);
            t.open(20, ImtMenuScreen::new);
            t.shot(20, "nb05_menu_systems_dot");
            t.step(0, () -> com.senne.imttech.client.ImtClientState.menuTab = com.senne.imttech.client.MenuTab.INFORMATION);
            t.shot(10, "nb05b_menu_information_dot");
            t.close(0);
            t.step(0, () -> com.senne.imttech.client.ImtClientState.menuTab = com.senne.imttech.client.MenuTab.COMBAT);
            // De koepel en de Arc Lamp: de twee knopjes naast elkaar.
            t.step(0, () -> com.senne.imttech.client.ImtClientConfig.setNumberStyle(com.senne.imttech.config.NumberStyle.COMMA));
            t.onServer(0, VisualCheck::buildDome);
            t.open(10, () -> new DomeScreen(DOME));
            t.shot(20, "nb06_dome_comma");
            t.close(0);
            t.stand(0, lampAt.getX() + 0.5, -60, lampAt.getZ() + 3.5, 180, 30);
            t.onServer(10, player -> com.senne.imttech.light.ArcLamps.open(player, lampAt));
            t.shot(20, "nb07_lamp_comma");
            t.close(0);
            // Terug naar de standaard, zodat de rest van de rondleiding (en de volgende keer) zoals altijd begint.
            t.step(0, () -> com.senne.imttech.client.ImtClientConfig.setNumberStyle(com.senne.imttech.config.NumberStyle.DEFAULT));
            t.step(0, () -> com.senne.imttech.client.ImtClientConfig.setPowerUnit(com.senne.imttech.client.PowerUnit.TICK));
        }

        // --- Hoofdstuk 67: de armory. Een wand van 5 x 2 Pegboards vol wapens, magazijnen, cellen, granaten en
        // uitrusting, met vier Ammo Boxes ervoor (patronen, hulzen, magazijnen, cellen). Dan: het kader als je een
        // rifle vasthoudt (waar hij komt), ophangen met een rechtsklik, het witte kader met een lege hand, draaien met
        // sneak, en een magazijn laden door rechtsklik in te houden op de kist met patronen.
        if (want("armory")) {
            t.command(0, "gamemode survival @a");
            t.onServer(0, VisualCheck::buildArmory);
            t.onServer(0, player -> {
                player.getInventory().clearContent();
                ItemStack rifle = com.senne.imttech.weapons.LaserGunItem.complete(com.senne.imttech.weapons.GunType.RIFLE,
                        com.senne.imttech.guns.GunMaterial.NETHERITE);
                player.getInventory().setItem(0, rifle);
                player.getInventory().setItem(2, com.senne.imttech.guns.MagazineItem.of(com.senne.imttech.guns.FirearmType.GLOCK_19, 0));
            });
            // De wand: x van ARMORY - 2 tot + 3, y van + 2 tot + 4, de voorkant van de panelen op z + 3,94.
            double wx = ARMORY.getX(), wy = ARMORY.getY(), wz = ARMORY.getZ();
            t.stand(20, wx + 0.5, wy + 1, wz - 0.5, 0, 0);
            t.step(0, () -> selectSlot(1));
            t.lookAt(0, new Vec3(wx + 0.5, wy + 2.6, wz + 3.9));
            t.shot(30, "ar01_wall");
            t.stand(0, wx + 2.0, wy + 1, wz + 1.3, 0, 0);
            t.lookAt(0, new Vec3(wx + 2.0, wy + 2.9, wz + 3.9));
            t.shot(25, "ar02_wall_left_close");
            t.stand(0, wx - 0.3, wy + 1, wz + 1.3, 0, 0);
            t.lookAt(0, new Vec3(wx - 0.3, wy + 2.9, wz + 3.9));
            t.shot(25, "ar03_wall_right_close");
            // De onderste rij van dichterbij (hoofdstuk 68): links de Sticky EMP en Sticky Frag, rechts de Remote EMP en
            // Remote Frag, even groot.
            t.stand(0, wx + 0.4, wy + 1, wz + 1.6, 0, 0);
            t.lookAt(0, new Vec3(wx + 0.4, wy + 2.45, wz + 3.9));
            t.shot(25, "ar03b_grenades_close");
            t.stand(0, wx + 0.0, wy + 1, wz + 0.2, 0, 0);
            t.lookAt(0, new Vec3(wx + 0.0, wy + 1.3, wz + 2.3));
            t.shot(25, "ar04_ammo_boxes");
            // Met een rifle in je hand naar het vrije paneel rechts: het kader zegt waar hij komt.
            t.stand(0, wx - 1.5, wy + 1, wz + 1.4, 0, 0);
            t.step(0, () -> selectSlot(0));
            t.lookAt(0, new Vec3(wx - 1.5, wy + 2.95, wz + 3.9));
            t.shot(25, "ar05_preview_rifle");
            t.tap(0, () -> Minecraft.getInstance().options.keyUse);
            t.shot(15, "ar06_rifle_hung");
            // Een lege hand op de rifle: een wit kader. Dan sneak + rechtsklik: hij draait (loop naar rechts).
            t.step(0, () -> selectSlot(1));
            t.shot(10, "ar07_hover_empty_hand");
            t.key(0, () -> Minecraft.getInstance().options.keyShift, true);
            t.tap(5, () -> Minecraft.getInstance().options.keyUse);
            t.key(5, () -> Minecraft.getInstance().options.keyShift, false);
            t.shot(15, "ar08_rifle_turned");
            // Een leeg magazijn op de kist met patronen: rechtsklik ingedrukt houden = patroon na patroon.
            t.stand(0, wx + 1.5, wy + 1, wz + 0.2, 0, 0);
            t.step(0, () -> selectSlot(2));
            t.lookAt(0, new Vec3(wx + 1.5, wy + 1.3, wz + 2.5));
            t.key(10, () -> Minecraft.getInstance().options.keyUse, true);
            t.shot(30, "ar09_loading_magazine");
            t.step(40, () -> ImtTech.LOGGER.info("[dev] armory: magazine has {} rounds after holding right-click",
                    com.senne.imttech.guns.MagazineItem.rounds(Minecraft.getInstance().player.getMainHandItem())));
            t.key(0, () -> Minecraft.getInstance().options.keyUse, false);
            t.shot(10, "ar10_magazine_loaded");
            t.command(0, "gamemode creative @a");
        }

        // --- Satellietraketten: eentje die valt en inslaat (precisie), en een bombardement
        if (want("missile")) {
            BlockPos aim = new BlockPos(30, -60, -30); // op een lege plek, ver van de heuvel
            Vec3 aimAt = Vec3.atBottomCenterOf(aim);
            t.view(0, aim.getX() + 0.5, -59, aim.getZ() + 24.5, 180, -32);
            t.onServer(20, player -> com.senne.imttech.satellite.SatelliteStrikes.start(player.serverLevel(), aimAt,
                    player.getUUID(), com.senne.imttech.satellite.SatelliteMode.PRECISION, null, null));
            t.shot(46, "90_missile_high");
            t.shot(5, "91_missile_low");
            t.view(0, aim.getX() + 0.5, -59, aim.getZ() + 24.5, 180, -8);
            t.shot(7, "92_missile_impact");
            t.shot(10, "93_missile_after");
            t.onServer(60, player -> com.senne.imttech.satellite.SatelliteStrikes.start(player.serverLevel(), aimAt,
                    player.getUUID(), com.senne.imttech.satellite.SatelliteMode.BOMBARDMENT, null, null));
            t.shot(62, "94_bombardment");
            t.shot(25, "95_bombardment_more");
        }

        // --- De tooltips van de onderdelen van een serverrack (hoofdstuk 64, jouw vraag "kijk of de config ook in de tooltips
        // klopt"): elk onderdeel in de werkbalk, de muis erop, een foto per onderdeel.
        if (want("parttips")) {
            // De items pas opvragen als de foto gemaakt wordt: de rondleiding wordt al opgebouwd terwijl de mod laadt.
            java.util.List<java.util.function.Supplier<? extends net.minecraft.world.item.Item>> parts = java.util.List.of(
                    ModItems.SERVER_CPU, ModItems.SERVER_GPU, ModItems.SERVER_RAM, ModItems.SERVER_SSD, ModItems.SERVER_FAN);
            String[] names = {"cpu", "gpu", "ram", "ssd", "fan"};
            // In creative opent Minecraft het creative-menu in plaats van je inventaris: dus even survival.
            t.command(0, "gamemode survival @a");
            t.onServer(0, player -> {
                for (int i = 0; i < parts.size(); i++) {
                    player.getInventory().setItem(i, new ItemStack(parts.get(i).get()));
                }
                player.getInventory().selected = 8;
            });
            t.open(10, () -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player));
            for (int i = 0; i < parts.size(); i++) {
                int slot = i;
                t.step(5, () -> {
                    if (Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen screen) {
                        HowToTour.mouseTo(screen.getGuiLeft() + 16 + slot * 18, screen.getGuiTop() + 150);
                    }
                });
                t.shot(8, "pt0" + (i + 1) + "_tooltip_" + names[i]);
            }
            t.close(5);
            t.command(0, "gamemode creative @a");
        }

        // --- De server (7 oktober): drie racks tegen elkaar (één generator eronder) en een los rack ernaast dat niet
        // meedraait. Sinds hoofdstuk 64 zonder scherm: je ziet de onderdelen in hun vakjes op de voorkant (dichtbij), het
        // naamscherm (sneak + rechtsklik), en de cijfers van elk rack in de app Monitor.
        if (want("racks")) {
            BlockPos core = new BlockPos(60, -60, -80);
            BlockPos[] server = {new BlockPos(63, -60, -78), new BlockPos(63, -59, -78), new BlockPos(64, -60, -78)};
            BlockPos lone = new BlockPos(67, -60, -78);
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState());
                if (level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    be.ensureSystem(level);
                    // Een druk diagram, zodat de app Monitor iets te meten heeft (8 oktober).
                    String code = """
                            when every(1):
                                log("busy")
                                wait(3)
                                log("done")
                            """;
                    be.logic().put(null, new com.senne.imttech.logic.program.LogicProgram("Busy", true,
                            com.senne.imttech.logic.program.GraphConverter.toGraph(
                                    com.senne.imttech.logic.program.ScriptParser.parse(code), null), code));
                }
                for (BlockPos generator : new BlockPos[] {core.below(), server[0].below(), lone.below()}) {
                    level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                    if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                        charger.fill(200_000);
                    }
                }
                BlockState rack = ModBlocks.SERVER_RACK.get().defaultBlockState()
                        .setValue(com.senne.imttech.logic.ServerRackBlock.FACING, Direction.SOUTH);
                for (BlockPos pos : server) {
                    level.setBlockAndUpdate(pos, rack);
                }
                level.setBlockAndUpdate(lone, rack);
                // Het eerste rack vol (2 x CPU/GPU, RAM en SSD, 4 fans), het tweede half, het derde alleen geheugen.
                ItemStack[][] parts = {
                        {new ItemStack(ModItems.SERVER_CPU.get()), new ItemStack(ModItems.SERVER_GPU.get()), new ItemStack(ModItems.SERVER_RAM.get()),
                                new ItemStack(ModItems.SERVER_SSD.get()), new ItemStack(ModItems.SERVER_FAN.get()), new ItemStack(ModItems.SERVER_FAN.get()),
                                new ItemStack(ModItems.SERVER_FAN.get()), new ItemStack(ModItems.SERVER_FAN.get())},
                        {new ItemStack(ModItems.SERVER_CPU.get()), new ItemStack(ModItems.SERVER_RAM.get())},
                        {new ItemStack(ModItems.SERVER_SSD.get()), new ItemStack(ModItems.SERVER_RAM.get()), new ItemStack(ModItems.SERVER_FAN.get())}};
                String[] names = {"rack1", "rack2", ""};
                for (int i = 0; i < server.length; i++) {
                    if (level.getBlockEntity(server[i]) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
                        for (ItemStack part : parts[i]) {
                            be.insert(part);
                        }
                        be.setName(names[i]);
                        be.refresh();
                    }
                }
                if (level.getBlockEntity(lone) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
                    be.insert(new ItemStack(ModItems.SERVER_CPU.get()));
                    be.setName("spare");
                    be.refresh();
                }
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.stand(10, 65.0, -60, -74.5, 180, 12);
            t.shot(90, "r01_racks");
            // Dichtbij: de acht vakjes op de voorkant, met de onderdelen erin (de lampjes knipperen, de fans draaien).
            t.stand(0, 63.5, -60, -76.2, 180, 10);
            t.shot(20, "r01b_rack_bays");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            // Sneak + rechtsklik met een lege hand: het naamscherm (de naam staat in de app Monitor).
            t.open(5, () -> new com.senne.imttech.client.InputNameScreen(server[1], "rack2", "Dev"));
            t.shot(15, "r02_rack_name");
            t.close(0);
            // Een warm rack (8 oktober: elk rack zijn eigen temperatuur): het tweede rack (zonder fan) krijgt een GPU erbij
            // en is 78 graden. In de app Monitor: zijn eigen cijfers (de hele server op halve snelheid, hoeveel fans erin
            // moeten), het eerste rack (met fans) blijft koel, en het losse rack draait niet mee.
            t.onServer(0, player -> {
                if (player.serverLevel().getBlockEntity(server[1]) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
                    be.insert(new ItemStack(ModItems.SERVER_GPU.get()));
                    be.setTemperature(78);
                    be.refresh();
                }
                com.senne.imttech.logic.LogicServer.forgetAll();
                if (player.serverLevel().getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    be.datacenter().update(player.serverLevel(), be);
                }
            });
            t.stand(0, 60.5, -60, -77.5, 180, 20);
            t.onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, core));
            t.step(10, () -> monitorRack("rack2"));
            t.shot(15, "r05_monitor_rack_hot");
            t.step(0, () -> monitorRack("rack1"));
            t.shot(10, "r05b_monitor_rack_cool");
            t.step(0, () -> monitorRack("spare"));
            t.shot(10, "r03_monitor_rack_not_connected");
            t.close(5);
            // Brand (8 oktober): het rack met de GPU vat vlam (vuur erop, rook), en in de app Monitor knippert ON FIRE: klik
            // je onderdelen eruit. Na 20 seconden is het weg.
            t.onServer(0, player -> {
                if (player.serverLevel().getBlockEntity(server[1]) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
                    be.ignite(player.serverLevel(), player.serverLevel().getGameTime() + 400);
                }
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.stand(0, 65.0, -60, -74.5, 180, 12);
            t.shot(30, "r06_rack_fire");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.stand(0, 60.5, -60, -77.5, 180, 20);
            t.onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, core));
            t.step(10, () -> monitorRack("rack2"));
            t.shot(10, "r07_monitor_rack_fire");
            t.step(5, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.STATUS);
                }
            });
            t.shot(10, "r04_os_status_server");
            // De app Monitor zonder gekozen rack (8 oktober): tegels met de laatste minuut (RAM en SSD in GB en TB, met wat
            // er nodig is), de racks met hun naam (een staat in brand) en de hele server.
            t.step(5, () -> monitorRack(null));
            t.shot(15, "r08_os_monitor");
            t.close(10);
            // De app Backup (8 oktober): een tweede datacenter ver weg ("dev-vault"), een backup met de
            // hand en automatisch elke 15 minuten.
            BlockPos vault = new BlockPos(60, -60, -170);
            BlockPos vaultRack = vault.offset(3, 0, 2);
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(vault, ModBlocks.SECURITY_CORE.get().defaultBlockState());
                for (BlockPos generator : new BlockPos[] {vault.below(), vaultRack.below()}) {
                    level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                    if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                        charger.fill(200_000);
                    }
                }
                level.setBlockAndUpdate(vaultRack, ModBlocks.SERVER_RACK.get().defaultBlockState());
                if (level.getBlockEntity(vaultRack) instanceof com.senne.imttech.logic.ServerRackBlockEntity rack) {
                    rack.insert(new ItemStack(ModItems.SERVER_CPU.get()));
                    rack.insert(new ItemStack(ModItems.SERVER_RAM.get()));
                    rack.insert(new ItemStack(ModItems.SERVER_SSD.get()));
                    rack.insert(new ItemStack(ModItems.SERVER_FAN.get()));
                }
                if (level.getBlockEntity(vault) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    be.ensureSystem(level);
                    be.setHostname("dev-vault");
                }
            });
            t.onServer(45, player -> {
                ServerLevel level = player.serverLevel();
                if (level.getBlockEntity(vaultRack) instanceof com.senne.imttech.logic.ServerRackBlockEntity rack) {
                    rack.update(level, vaultRack, level.getBlockState(vaultRack));
                }
                com.senne.imttech.logic.LogicServer.forgetAll();
                if (level.getBlockEntity(vault) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    be.datacenter().update(level, be);
                }
                if (level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    be.datacenter().update(level, be);
                    net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
                    args.putString("key", com.senne.imttech.logic.Datacenters.key(net.minecraft.core.GlobalPos.of(level.dimension(), vault)));
                    com.senne.imttech.logic.Backups.handle(player, be, "backup_target", args);
                    com.senne.imttech.logic.Backups.handle(player, be, "backup_now", new net.minecraft.nbt.CompoundTag());
                    net.minecraft.nbt.CompoundTag auto = new net.minecraft.nbt.CompoundTag();
                    auto.putInt("minutes", 15);
                    com.senne.imttech.logic.Backups.handle(player, be, "backup_auto", auto);
                }
            });
            t.stand(0, 60.5, -60, -77.5, 180, 20);
            t.onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, core));
            t.step(10, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.BACKUP);
                }
            });
            t.shot(15, "r09_os_backup");
            t.close(10);
            // Het brein op het datacenter (8 oktober): in survival draait het op dev-sec, en zolang dat
            // nog niet sterk is, denkt ze even na (ook met het woordenboek).
            t.command(0, "gamemode survival @a");
            t.pause(20);
            t.open(10, com.senne.imttech.client.BrainScreen::new);
            t.shot(10, "r10_brain_datacenter");
            t.close(0);
            t.step(5, () -> com.senne.imttech.client.voice.VoiceControl.hear("night vision on"));
            t.shot(8, "r11_brain_thinking");
            t.waitUntil(1200, () -> !com.senne.imttech.client.voice.ImtBrain.isThinking());
            t.shot(10, "r12_brain_answered");
            t.command(0, "gamemode creative @a");
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
                if (level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    be.ensureSystem(level);
                    be.setPassword("bitech1", level.random);
                }
                for (BlockPos generator : new BlockPos[] {core.below(), rack.below()}) {
                    level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                    if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                        charger.fill(200_000);
                    }
                }
                level.setBlockAndUpdate(rack, ModBlocks.SERVER_RACK.get().defaultBlockState()
                        .setValue(com.senne.imttech.logic.ServerRackBlock.FACING, Direction.SOUTH));
                if (level.getBlockEntity(rack) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
                    be.insert(new ItemStack(ModItems.SERVER_CPU.get()));
                    be.insert(new ItemStack(ModItems.SERVER_RAM.get()));
                    be.insert(new ItemStack(ModItems.SERVER_SSD.get()));
                }
                for (int dy = 0; dy < 3; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        level.setBlockAndUpdate(wall.offset(dx, dy, 0), Blocks.SMOOTH_STONE.defaultBlockState());
                    }
                }
                level.setBlockAndUpdate(reader, ModBlocks.KEYCARD_READER.get().defaultBlockState()
                        .setValue(com.senne.imttech.logic.InputDeviceBlock.FACING, Direction.SOUTH));
                if (level.getBlockEntity(reader) instanceof com.senne.imttech.logic.InputDeviceBlockEntity be) {
                    be.setName("Lab door");
                }
                level.setBlockAndUpdate(reader.east(), ModBlocks.CODE_PANEL.get().defaultBlockState()
                        .setValue(com.senne.imttech.logic.InputDeviceBlock.FACING, Direction.SOUTH));
                if (level.getBlockEntity(reader.east()) instanceof com.senne.imttech.logic.InputDeviceBlockEntity be) {
                    be.setName("Front door");
                }
                // Een lege kaart en acht beschreven kaarten (één per kleur) in de hotbar.
                player.getInventory().clearContent();
                player.getInventory().setItem(0, new ItemStack(ModItems.KEYCARD.get(), 3));
                String[] labels = {"Visitor", "Garden", "Lab access", "Night guard", "Boss", "Security", "Workshop", "Vault"};
                for (int colour = 1; colour <= 8; colour++) {
                    ItemStack card = new ItemStack(ModItems.KEYCARD.get());
                    com.senne.imttech.logic.KeycardItem.write(card, new com.senne.imttech.logic.KeycardData(
                            "10.0.0.1", "dev-sec", 1000 + colour, colour, labels[colour - 1]));
                    player.getInventory().setItem(colour, card);
                }
                player.getInventory().setItem(9, new ItemStack(ModItems.KEYCARD_READER.get()));
                player.getInventory().selected = 3;
            });
            t.stand(10, 103.0, -60, -75.5, 180, 30);
            t.shot(60, "y01_reader_wall");
            // De kaart tegen de lezer: oranje (de server denkt na).
            t.onServer(0, player -> com.senne.imttech.logic.LogicEvents.keycard(player.serverLevel(), reader, player,
                    com.senne.imttech.logic.KeycardItem.data(player.getInventory().getItem(3))));
            t.shot(4, "y02_reader_busy");
            t.onServer(0, player -> com.senne.imttech.logic.InputDeviceBlock.flash(player.serverLevel(), reader,
                    com.senne.imttech.logic.InputLight.OK));
            t.shot(4, "y03_reader_ok");
            t.onServer(0, player -> com.senne.imttech.logic.InputDeviceBlock.flash(player.serverLevel(), reader,
                    com.senne.imttech.logic.InputLight.DENIED));
            t.shot(4, "y04_reader_denied");
            // Het codepaneel: de toetsen, met vier cijfers ingetypt (je ziet sterretjes).
            t.step(0, () -> {
                Minecraft.getInstance().setScreen(new com.senne.imttech.client.CodePanelScreen(reader.east(), "Front door"));
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.CodePanelScreen panel) {
                    panel.tourType("4321");
                }
            });
            t.shot(10, "y045_code_panel");
            t.close(0);
            // De app Keycards (25 oktober: met groepen links). Eerst de groepen: "security" en "lab" in "staff",
            // "night shift" in "security", en "guest" bovenaan. Dan een kaart schrijven in "lab".
            t.stand(30, 100.5, -60, -77.5, 180, 20);
            t.onServer(5, player -> {
                com.senne.imttech.security.os.SecurityOs.open(player, core);
                for (String[] group : new String[][] {{"staff", ""}, {"security", "staff"}, {"night shift", "security"},
                        {"lab", "staff"}, {"guest", ""}}) {
                    net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
                    args.putString("name", group[0]);
                    args.putString("parent", group[1]);
                    com.senne.imttech.security.os.SecurityOs.handleAction(player,
                            new com.senne.imttech.network.OsActionPayload(core, "cgroup_new", args));
                }
            });
            t.step(10, () -> keycards(os -> {
                os.tourKeycards("Lab access", 3);
                os.tourKeycardGroup("lab");
            }));
            t.shot(10, "y05_keycards_app");
            // Vijf kaarten in hun groepen (Lotte ook in lab), en Guest geblokkeerd.
            t.onServer(0, player -> {
                player.getInventory().add(new ItemStack(ModItems.KEYCARD.get(), 2));
                for (String[] card : new String[][] {{"Lab access", "3", "lab"}, {"Guest", "1", "guest"}, {"Lotte", "5", "security"},
                        {"Night guard", "8", "night shift"}, {"Cleaner", "7", ""}}) {
                    net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
                    args.putString("label", card[0]);
                    args.putInt("colour", Integer.parseInt(card[1]));
                    args.putString("group", card[2]);
                    com.senne.imttech.security.os.SecurityOs.handleAction(player,
                            new com.senne.imttech.network.OsActionPayload(core, "card_write", args));
                }
                net.minecraft.nbt.CompoundTag lotte = new net.minecraft.nbt.CompoundTag();
                lotte.putInt("n", 1003);
                lotte.putString("group", "lab");
                com.senne.imttech.security.os.SecurityOs.handleAction(player,
                        new com.senne.imttech.network.OsActionPayload(core, "card_group_add", lotte));
                net.minecraft.nbt.CompoundTag block = new net.minecraft.nbt.CompoundTag();
                block.putInt("n", 1002);
                block.putBoolean("on", true);
                com.senne.imttech.security.os.SecurityOs.handleAction(player,
                        new com.senne.imttech.network.OsActionPayload(core, "card_block", block));
            });
            t.step(0, () -> keycards(os -> os.tourKeycardGroup(null)));
            t.shot(15, "y06_keycards_list");
            // Links "security" gekozen: Lotte zit erin, Night guard via "night shift" (+1 inside); elke kaart heeft Remove.
            t.step(0, () -> keycards(os -> os.tourKeycardGroup("security")));
            t.shot(5, "y061_keycards_security");
            // New: het naamvak onderaan (de nieuwe groep komt in "security"), en Rename met de oranje waarschuwing.
            t.step(0, () -> keycards(os -> os.tourKeycardInput(false, "day shift")));
            t.shot(5, "y062_keycards_new_group");
            t.step(0, () -> keycards(os -> os.tourKeycardInput(true, "guards")));
            t.shot(5, "y063_keycards_rename");
            // Opruimen: de vraag "Sure?", en daarna alle geblokkeerde kaarten weg.
            t.step(0, () -> keycards(com.senne.imttech.client.os.SecurityOsScreen::tourAskDeleteBlocked));
            t.shot(5, "y07_keycards_delete_sure");
            t.onServer(0, player -> com.senne.imttech.security.os.SecurityOs.handleAction(player,
                    new com.senne.imttech.network.OsActionPayload(core, "card_delete_blocked", new net.minecraft.nbt.CompoundTag())));
            t.shot(15, "y08_keycards_deleted");
            t.close(10);
        }

        // --- De app Devices (8 oktober): de keycardlezers en codepanelen staan erin, en met Rename
        // geef je ze daar een andere naam (typen, dan Save).
        if (want("devrename")) {
            BlockPos core = new BlockPos(130, -60, -80);
            BlockPos rack = new BlockPos(126, -60, -78);
            BlockPos wall = new BlockPos(132, -60, -78);
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState());
                if (level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    be.ensureSystem(level);
                    be.setPassword("bitech1", level.random);
                }
                for (BlockPos generator : new BlockPos[] {core.below(), rack.below()}) {
                    level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                    if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                        charger.fill(200_000);
                    }
                }
                level.setBlockAndUpdate(rack, ModBlocks.SERVER_RACK.get().defaultBlockState()
                        .setValue(com.senne.imttech.logic.ServerRackBlock.FACING, Direction.SOUTH));
                if (level.getBlockEntity(rack) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
                    be.insert(new ItemStack(ModItems.SERVER_CPU.get()));
                    be.insert(new ItemStack(ModItems.SERVER_RAM.get()));
                    be.insert(new ItemStack(ModItems.SERVER_SSD.get()));
                }
                for (int dx = -1; dx <= 1; dx++) {
                    level.setBlockAndUpdate(wall.offset(dx, 0, 0), Blocks.SMOOTH_STONE.defaultBlockState());
                }
                String[] names = {"Reader 1", "Panel 1", "Panel 2"};
                for (int i = 0; i < names.length; i++) {
                    BlockPos pos = wall.offset(i - 1, 0, 1);
                    level.setBlockAndUpdate(pos, (i == 0 ? ModBlocks.KEYCARD_READER.get() : ModBlocks.CODE_PANEL.get())
                            .defaultBlockState().setValue(com.senne.imttech.logic.InputDeviceBlock.FACING, Direction.SOUTH));
                    if (level.getBlockEntity(pos) instanceof com.senne.imttech.logic.InputDeviceBlockEntity be) {
                        be.setName(names[i]);
                    }
                }
            });
            t.stand(30, 130.5, -60, -76.5, 180, 20);
            t.onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, core));
            t.step(10, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.DEVICES);
                }
            });
            t.shot(10, "dr1_devices_readers");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourRenameInput("Lab door");
                }
            });
            t.shot(5, "dr2_devices_renaming");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourSaveRename();
                }
            });
            t.shot(25, "dr3_devices_renamed");
            t.close(10);
            // 13 oktober: dezelfde lezer hernoemen zonder desktop, met sneak + rechtsklik (jij bent de eigenaar van
            // de kern, dus vertrouwd). Typen toont de regel voor je diagram en dat je die ook moet veranderen; Save
            // zegt het boven je hotbar.
            BlockPos reader = wall.offset(-1, 0, 1);
            t.onServer(0, player -> com.senne.imttech.logic.InputDevices.open(player, reader));
            t.shot(15, "dr4_reader_name_screen");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.InputNameScreen screen) {
                    screen.tourType("Lab entrance");
                }
            });
            t.shot(5, "dr5_reader_name_typed");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.InputNameScreen screen) {
                    screen.tourSave();
                }
            });
            t.shot(10, "dr6_reader_renamed");
            // En een codepaneel ("zorg ook dat dit bij code panels werkt"): hetzelfde scherm, met when codepanel(...).
            BlockPos panel = wall.offset(0, 0, 1);
            t.onServer(0, player -> com.senne.imttech.logic.InputDevices.open(player, panel));
            t.shot(15, "dr7_panel_name_screen");
            t.close(5);
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
                com.senne.imttech.logic.LogicEvents.clear();
                com.senne.imttech.logic.LogicEvents.codePanel(player.serverLevel(), lab.offset(5, 1, 7), player, "4321");
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
                    # The lab: a card of the group lab opens the vault for 3 seconds
                    def open_vault():
                        accept()
                        pulse("Vault door", 3)

                    when keycard("Lab door"):
                        if card.in_group("lab"):
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
                if (level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
                    be.setOwner(player);
                    be.ensureSystem(level);
                    be.setPassword("bitech1", level.random);
                }
                for (BlockPos generator : new BlockPos[] {core.below(), rack.below()}) {
                    level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                    if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                        charger.fill(200_000);
                    }
                }
                level.setBlockAndUpdate(rack, ModBlocks.SERVER_RACK.get().defaultBlockState());
                if (level.getBlockEntity(rack) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
                    be.insert(new ItemStack(ModItems.SERVER_CPU.get()));
                    be.insert(new ItemStack(ModItems.SERVER_RAM.get()));
                    be.insert(new ItemStack(ModItems.SERVER_SSD.get()));
                    be.insert(new ItemStack(ModItems.SERVER_SSD.get()));
                }
                String[] names = {"Vault door", "Lab lights"};
                for (int i = 0; i < names.length; i++) {
                    BlockPos relay = core.offset(3 + i, 0, 3);
                    level.setBlockAndUpdate(relay, ModBlocks.RELAY.get().defaultBlockState());
                    var data = player.getData(com.senne.imttech.registry.ModAttachments.RELAYS);
                    player.setData(com.senne.imttech.registry.ModAttachments.RELAYS, data.with(new com.senne.imttech.redstone.Relay(
                            names[i], "", level.dimension(), relay)));
                }
                level.setBlockAndUpdate(core.offset(-3, 0, 3), ModBlocks.KEYCARD_READER.get().defaultBlockState());
                if (level.getBlockEntity(core.offset(-3, 0, 3)) instanceof com.senne.imttech.logic.InputDeviceBlockEntity be) {
                    be.setName("Lab door");
                }
                level.setBlockAndUpdate(core.offset(-4, 0, 3), ModBlocks.CODE_PANEL.get().defaultBlockState());
                if (level.getBlockEntity(core.offset(-4, 0, 3)) instanceof com.senne.imttech.logic.InputDeviceBlockEntity be) {
                    be.setName("Front door");
                }
            });
            t.stand(60, 140.5, -60, -77.5, 180, 20);
            t.onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, core));
            t.step(10, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogic("Lab", code, false);
                }
            });
            t.shot(10, "l01_logic_code");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogic("Lab", code, true);
                }
            });
            t.shot(10, "l02_logic_help");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogic("Lab", code.replace("pulse(\"Vault door\", 3)", "opn(\"Vault door\")"), false);
                }
            });
            t.shot(10, "l03_logic_error");
            // De fout verbeterd, en dan de knop Save: daarna staat hij links in de lijst (groen = aan).
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogic("Lab", code, false);
                    os.tourSaveLogic();
                }
            });
            t.shot(20, "l04_logic_saved");
            // Hetzelfde diagram als blokjes met draadjes, met rechts het palet.
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicBlocks(null);
                }
            });
            t.shot(10, "l05_logic_blocks");
            // Een blokje gekozen: rechts zijn instellingen, met jouw apparaten als suggestie.
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicBlocks(com.senne.imttech.logic.program.Op.PULSE);
                }
            });
            t.shot(10, "l06_logic_block_settings");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicBlocks(com.senne.imttech.logic.program.Op.IF);
                }
            });
            t.shot(10, "l07_logic_block_if");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicBlocks(com.senne.imttech.logic.program.Op.CALL);
                }
            });
            t.shot(10, "l08_logic_block_use_def");
            // Ctrl+Spatie: terug naar de code, dan het lijstje op drie plekken.
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicComplete("open_vault()", "        ");
                }
            });
            t.shot(10, "l09_ctrl_space_actions");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicComplete("if card.valid", "if ");
                }
            });
            t.shot(10, "l10_ctrl_space_checks");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicComplete("pulse(\"Vault door\"", "pulse(\"");
                }
            });
            t.shot(10, "l11_ctrl_space_devices");
            // Hoofdstuk 20: namen, rekenen en lussen, in de code en als blokjes.
            String loops = """
                    count = 0

                    when keycard("Lab door"):
                        count = count + 1
                        say("Card " + count + ": " + card.name)
                        if count >= 3:
                            for i in range(1, 4):
                                say($"Blink {i} of {count}")
                                pulse("Lab lights", 0.5)
                                wait(1)
                            count = 0
                    """;
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogic("Counter", loops, false);
                }
            });
            t.shot(10, "l12_logic_names_loops");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicBlocks(null);
                }
            });
            t.shot(10, "l13_logic_loop_blocks");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicBlocks(com.senne.imttech.logic.program.Op.FOR);
                }
            });
            t.shot(10, "l14_logic_block_for");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicBlocks(com.senne.imttech.logic.program.Op.SET);
                }
            });
            t.shot(10, "l15_logic_block_set");
            // Hoofdstuk 23: na + kies je wat voor programma (een diagram op de server, of een robotprogramma).
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicChooseKind();
                }
            });
            t.shot(10, "l16_logic_new_kind");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicNew(com.senne.imttech.logic.program.ProgramKind.ROBOT);
                }
            });
            t.shot(10, "l17_logic_robot_program");
            // Hoofdstuk 23, stap 2: de woorden van een robot, in code (met Help) en in blokjes.
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogic("Patrol", """
                            scanPin = pin(1)
                            laserPin = pin(3)

                            when started:
                                while true:
                                    if robot.battery < 10:
                                        charge()
                                    forward(5)
                                    turn_right()
                                    if read(scanPin):
                                        alarm("Intruder near " + robot.name)
                                        write(laserPin, true)

                            when ordered("attack"):
                                goto(order.x, order.y, order.z)
                            """, true);
                }
            });
            t.shot(10, "l18_logic_robot_code");
            t.step(0, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourLogicBlocks(com.senne.imttech.logic.program.Op.GOTO);
                }
            });
            t.shot(10, "l19_logic_robot_blocks");
            t.close(10);
        }

        // --- De Remote Frag (hoofdstuk 35, in plaats van C4): drie tegen een muur, op de vloer en onder een overstekje,
        // van dichtbij; de Frag Trigger in je hand met de bordjes boven je ladingen; het alarm "FRAG!" op je zone; dan de
        // klap: een gat in de muur met barsten eromheen, en barsten in obsidiaan (na drie frags nog geen gat).
        if (want("remote_frag")) {
            BlockPos w = FRAG_WALL;
            t.onServer(0, VisualCheck::buildFragWall);
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.stand(10, w.getX() + 8.5, w.getY(), w.getZ() + 3.5, 90, 12);
            t.shot(40, "f01_remote_frags");
            t.view(10, w.getX() + 4.2, w.getY() + 0.6, w.getZ() + 3.5, 90, 8);
            t.shot(20, "f02_remote_frag_close");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            // De Frag Trigger in je hand: rode bordjes boven je ladingen.
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                ItemStack trigger = new ItemStack(ModItems.FRAG_TRIGGER.get());
                for (BlockPos pos : new BlockPos[] {w.offset(2, 2, 3), w.offset(3, 0, 2), w.offset(4, 2, 6)}) {
                    if (level.getBlockEntity(pos) instanceof com.senne.imttech.weapons.RemoteChargeBlockEntity frag) {
                        com.senne.imttech.weapons.TriggerItem.link(level, pos, frag, trigger);
                    }
                }
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, trigger);
            });
            t.stand(10, w.getX() + 8.5, w.getY(), w.getZ() + 5.5, 90, 10);
            t.shot(30, "f03_frag_trigger_markers");
            // Het alarm: de muur wordt jouw zone (een kern met stroom ernaast), en "iemand" plakt er een Remote Frag op.
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                BlockPos corePos = w.offset(5, 0, -3);
                level.setBlockAndUpdate(corePos.below(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                if (level.getBlockEntity(corePos.below()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
                    generator.fill(200_000);
                }
                level.setBlockAndUpdate(corePos, ModBlocks.SECURITY_CORE.get().defaultBlockState());
                if (level.getBlockEntity(corePos) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
                    core.setOwner(player);
                    core.ensureSystem(level);
                    core.setZoneBoxes(java.util.List.of(com.senne.imttech.security.zone.ZoneBox.of(w.offset(0, -1, 0), w.offset(1, 5, 8))));
                }
                stickFrag(level, w.offset(1, 0, 1), Direction.EAST);
                BlockPos planted = w.offset(2, 0, 1);
                if (level.getBlockEntity(planted) instanceof com.senne.imttech.weapons.RemoteChargeBlockEntity frag) {
                    com.senne.imttech.breach.BlastAlarm.planted(level, planted, frag);
                }
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            });
            t.stand(10, w.getX() + 9.5, w.getY(), w.getZ() + 4.5, 100, 8);
            t.shot(30, "f04_frag_zone_alarm");
            // Met een schaar onschadelijk gemaakt; dan gaan de andere drie af (de knop), en drie keer een frag tegen het
            // obsidiaan: veel barsten, nog geen gat (dat is pas na de vierde).
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                BlockPos planted = w.offset(2, 0, 1);
                if (level.getBlockEntity(planted) instanceof com.senne.imttech.weapons.RemoteChargeBlockEntity frag
                        && level.getBlockState(planted).getBlock() instanceof com.senne.imttech.weapons.RemoteChargeBlock block) {
                    block.defuse(level, planted, frag, player);
                }
                for (BlockPos pos : new BlockPos[] {w.offset(2, 2, 3), w.offset(3, 0, 2), w.offset(4, 2, 6)}) {
                    if (level.getBlockEntity(pos) instanceof com.senne.imttech.weapons.RemoteChargeBlockEntity frag) {
                        frag.goOff(level, pos, level.getBlockState(pos));
                    }
                }
                BlockPos onObsidian = w.offset(2, 1, 11);
                for (int i = 0; i < 3; i++) {
                    stickFrag(level, w.offset(1, 1, 11), Direction.EAST);
                    if (level.getBlockEntity(onObsidian) instanceof com.senne.imttech.weapons.RemoteChargeBlockEntity frag) {
                        frag.goOff(level, onObsidian, level.getBlockState(onObsidian));
                    }
                }
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.stand(30, w.getX() + 7.5, w.getY(), w.getZ() + 6.5, 115, 8);
            t.shot(20, "f05_frag_hole_and_cracks");
            t.stand(10, w.getX() + 4.5, w.getY() + 1, w.getZ() + 11.5, 90, 10);
            t.shot(15, "f06_frag_obsidian_cracked");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        }

        // --- Licht (9 oktober): 's nachts een Arc Lamp op een paal, met een kabel van een generator en
        // een lichtschakelaar op de kabel. Eerst uit (donker), dan de schakelaar om: de lamp
        // van dichtbij, het veld van ver weg en van bovenaf (tot 85 blokken vol licht), zijn instelscherm
        // met de lamp op lichtniveau 40 (en dan het veld van bovenaf: een veel kleinere ruit), de
        // schakelaar van dichtbij, en weer uit.
        if (want("light")) {
            BlockPos y = LAMP_YARD;
            Vec3 lamp = Vec3.atCenterOf(y.east(7).above(4));
            t.onServer(0, VisualCheck::buildLampYard);
            t.command(0, "time set midnight");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.stand(10, y.getX() - 1.5, y.getY(), y.getZ() + 7.5, 0, 0);
            t.lookAt(5, lamp.add(0, -2, 0));
            t.shot(60, "lt01_lamp_off_night");
            t.onServer(0, player -> com.senne.imttech.light.LightSwitchBlock.set(player.serverLevel(), y.east(3), true));
            t.shot(80, "lt02_lamp_on_close");
            t.stand(0, y.getX() + 70.5, y.getY(), y.getZ() + 0.5, 90, 0);
            t.lookAt(5, lamp.add(0, -4, 0));
            t.shot(60, "lt03_field_from_far");
            t.view(0, y.getX() + 7.5, y.getY() + 60, y.getZ() + 40.5, 180, 55);
            t.shot(60, "lt04_field_from_above");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            // Het instelscherm (10 oktober): de lamp op lichtniveau 40 zetten, en van bovenaf kijken.
            t.stand(0, y.getX() + 5.5, y.getY(), y.getZ() + 1.5, 160, -40);
            t.onServer(5, player -> com.senne.imttech.light.ArcLamps.open(player, y.east(7).above(4)));
            t.step(10, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.light.ArcLampScreen screen) {
                    screen.tourBrightness(40);
                }
            });
            t.shot(10, "lt05_lamp_screen");
            t.close(0);
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.view(0, y.getX() + 7.5, y.getY() + 60, y.getZ() + 40.5, 180, 55);
            t.shot(80, "lt06_dimmed_from_above");
            t.onServer(0, player -> {
                if (player.serverLevel().getBlockEntity(y.east(7).above(4)) instanceof com.senne.imttech.light.ArcLampBlockEntity entity) {
                    entity.setBrightness(100);
                }
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.stand(0, y.getX() + 3.5, y.getY(), y.getZ() + 2.5, 180, 60);
            t.step(0, () -> Minecraft.getInstance().player.getInventory().selected = 0);
            t.shot(30, "lt07_switch_on_cable");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.onServer(0, player -> com.senne.imttech.light.LightSwitchBlock.set(player.serverLevel(), y.east(3), false));
            t.stand(0, y.getX() - 1.5, y.getY(), y.getZ() + 7.5, 0, 0);
            t.lookAt(5, lamp.add(0, -2, 0));
            t.shot(60, "lt08_lamp_off_again");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.command(0, "time set noon");
        }

        // --- Relais (13 en 14 oktober): twee deuren in de groep "security room" (in "restricted", in "base"), een
        // omgekeerde tuinlamp, en een relay emitter met een hendel die "base" schakelt. Eerst de hendel uit, dan
        // aan (de deurlampen gaan aan), dan de schermen: de boom van groepen (met onderaan waar je groepen instelt),
        // een relais met zijn groep (alleen te lezen sinds 16 oktober), de emitter, en de app Groups op de desktop.
        if (want("relays")) {
            BlockPos y = RELAY_YARD;
            t.onServer(0, VisualCheck::buildRelayYard);
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.stand(10, y.getX() + 3.5, y.getY(), y.getZ() + 6.5, 180, 30);
            t.shot(40, "rl01_relay_yard");
            t.onServer(0, player -> {
                BlockPos lever = y.offset(7, 0, 2);
                player.serverLevel().setBlockAndUpdate(lever, player.serverLevel().getBlockState(lever)
                        .setValue(net.minecraft.world.level.block.LeverBlock.POWERED, true));
            });
            t.shot(20, "rl02_emitter_switched_base");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.open(0, RelayListScreen::new);
            t.shot(20, "rl03_relay_tree");
            t.open(0, () -> {
                var data = Minecraft.getInstance().player.getData(com.senne.imttech.registry.ModAttachments.RELAYS);
                return new com.senne.imttech.client.RelayEditScreen(data.indexAt(Minecraft.getInstance().level.dimension(), y.offset(1, 0, 0)));
            });
            t.shot(20, "rl043_relay_edit_group");
            t.close(0);
            t.open(0, () -> {
                var data = Minecraft.getInstance().player.getData(com.senne.imttech.registry.ModAttachments.RELAYS);
                return new com.senne.imttech.client.RelayEditScreen(data.indexAt(Minecraft.getInstance().level.dimension(), y.offset(5, 0, 0)));
            });
            t.shot(20, "rl05_relay_edit_inverted");
            t.close(0);
            t.onServer(5, player -> com.senne.imttech.redstone.RelayEmitters.open(player, y.offset(6, 0, 2)));
            t.shot(15, "rl06_emitter_screen");
            t.close(0);
            // De app Groups op de Security OS-desktop van een eigen kern in de tuin.
            t.stand(0, y.getX() + 0.5, y.getY(), y.getZ() + 5.5, 180, 30);
            t.onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, y.offset(0, 0, 3)));
            t.step(10, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourGroups("restricted");
                }
            });
            t.shot(10, "rl061_os_groups");
            t.close(0);
            // Van dichtbij, van bovenaf: de twee deuren aan (groen), de tuinlamp omgekeerd (rode rand) en uit.
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.stand(0, y.getX() + 3.5, y.getY(), y.getZ() + 2.5, 180, 65);
            t.shot(30, "rl07_relays_closeup");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        }

        // --- Barsten herstellen (hoofdstuk 11): de Crusher en de Compactor aan het werk, elk naast een
        // generator, hun schermen (gebarsten stenen bakstenen erin en poeder eruit; obsidiaanpoeder erin
        // en obsidiaan eruit), en gebarsten blokken en poeder in je hotbar, in je hand en op de grond.
        if (want("repair")) {
            BlockPos r = REPAIR;
            t.onServer(0, VisualCheck::buildRepairCorner);
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.stand(10, r.getX() + 1.5, r.getY(), r.getZ() + 4.5, 180, 15);
            t.lookAt(5, new Vec3(r.getX() + 1.5, r.getY() + 0.4, r.getZ() + 0.8));
            t.shot(40, "r01_crusher_working");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.onServer(0, player -> {
                if (player.serverLevel().getBlockEntity(r) instanceof com.senne.imttech.repair.CrackMachineBlockEntity machine) {
                    player.openMenu(machine, buf -> buf.writeVarInt(machine.kind().ordinal()));
                }
            });
            t.shot(20, "r02_crusher_screen");
            t.close(5);
            t.onServer(0, player -> {
                if (player.serverLevel().getBlockEntity(r.east(2)) instanceof com.senne.imttech.repair.CrackMachineBlockEntity machine) {
                    player.openMenu(machine, buf -> buf.writeVarInt(machine.kind().ordinal()));
                }
            });
            t.shot(20, "r04_compactor_screen");
            t.close(5);
            // Gebarsten obsidiaan in je hand, en op de grond een gebarsten stenen baksteen.
            t.step(0, () -> Minecraft.getInstance().player.getInventory().selected = 1);
            t.lookAt(5, new Vec3(r.getX() + 1.5, r.getY() + 0.2, r.getZ() + 2.0));
            t.shot(20, "r03_cracked_in_hand");
        }

        // --- De Block Reinforcer (hoofdstuk 44): het linker kwartsmuurtje is verstevigd (een randje door je
        // bril, met het gereedschap in je hand), dan dezelfde klap op allebei: links barsten, rechts een gat.
        if (want("reinforce")) {
            BlockPos r = REINFORCE;
            t.onServer(0, DevWorld::wearGlasses);
            t.onServer(0, VisualCheck::buildReinforceCorner);
            t.stand(10, r.getX() + 4.5, r.getY(), r.getZ() + 6.5, 180, 8);
            t.lookAt(5, new Vec3(r.getX() + 4.5, r.getY() + 1.2, r.getZ() + 0.5));
            t.shot(50, "rf01_reinforced_outline");
            t.onServer(0, player -> {
                ShowcaseFeatures.dent(player.serverLevel(), r.offset(1, 1, 0));
                ShowcaseFeatures.dent(player.serverLevel(), r.offset(7, 1, 0));
            });
            t.shot(40, "rf02_same_blast_left_cracks_right_breaks");
        }

        // --- De Power Battery (hoofdstuk 47): de doos met het raam en de poorten, het scherm (met een gevulde
        // grafiek), en wat het scherm zegt als er een blok uit de wand is.
        if (want("battery")) {
            BlockPos b = BATTERY_YARD;
            BlockPos port = b.offset(0, 0, 2);
            t.onServer(0, player -> buildBatteryYard(player, BATTERY_YARD));
            t.view(20, b.getX() - 3.5, b.getY() + 3.0, b.getZ() + 11.5, 200, 15);
            t.lookAt(5, Vec3.atCenterOf(b.offset(2, 1, 2)));
            t.shot(40, "bt01_battery_box");
            t.view(0, b.getX() + 2.5, b.getY() + 1.6, b.getZ() + 7.5, 180, 5);
            t.lookAt(5, Vec3.atCenterOf(b.offset(2, 2, 4)));
            t.shot(15, "bt01b_battery_glass_close");
            t.onServer(0, player -> {
                if (player.serverLevel().getBlockEntity(port) instanceof com.senne.imttech.battery.BatteryPortBlockEntity entity) {
                    entity.tourHistory();
                }
                com.senne.imttech.battery.Batteries.open(player.serverLevel(), port, player);
            });
            t.shot(30, "bt02_battery_screen");
            t.close(0);
            t.onServer(5, player -> player.serverLevel().setBlockAndUpdate(b.offset(2, 3, 2), Blocks.AIR.defaultBlockState()));
            t.onServer(10, player -> com.senne.imttech.battery.Batteries.open(player.serverLevel(), port, player));
            t.shot(20, "bt03_battery_wall_not_closed");
            t.close(0);
            t.onServer(5, player -> player.serverLevel().setBlockAndUpdate(b.offset(2, 3, 2),
                    ModBlocks.BATTERY_CASING.get().defaultBlockState()));
        }

        // --- De Glock 19 (hoofdstuk 52): vanuit de heup, gericht, een schot, het magazijn eruit en erin, de slede, een
        // storing en de tap-rack, een patroon met de hand in de kamer, bekijken, een magazijn vullen, de hulzen, de twee
        // banken, derde persoon, en een kapotte loop.
        if (want("glock")) {
            BlockPos g = GUN_RANGE.above();
            Vec3 stand = new Vec3(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            t.onServer(0, VisualCheck::buildGunRange);
            t.onServer(0, player -> ShowcaseFeatures.buildGunBenches(player, g));
            t.onServer(0, ShowcaseFeatures::giveGlock);
            t.stand(20, stand.x, stand.y, stand.z, 0, 2);
            t.step(0, () -> selectSlot(0));
            t.shot(25, "gl01_glock_hip");
            t.key(0, () -> Minecraft.getInstance().options.keyUse, true);
            t.shot(12, "gl02_glock_aim");
            t.key(0, () -> Minecraft.getInstance().options.keyAttack, true);
            t.key(1, () -> Minecraft.getInstance().options.keyAttack, false);
            t.shot(0, "gl03_glock_fire");
            t.shot(2, "gl04_glock_slide_back");
            t.key(6, () -> Minecraft.getInstance().options.keyUse, false);
            t.step(10, () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()));
            t.shot(5, "gl05_magazine_out");
            t.step(15, () -> selectSlot(1));
            t.tap(15, () -> Minecraft.getInstance().options.keyUse);
            t.shot(7, "gl06_magazine_in");
            t.step(15, () -> selectSlot(0));
            t.shot(3, "gl07_slide_release");
            t.onServer(20, player -> com.senne.imttech.config.ImtConfig.Guns.JAM_CHANCE = 100);
            t.key(2, () -> Minecraft.getInstance().options.keyAttack, true);
            t.key(1, () -> Minecraft.getInstance().options.keyAttack, false);
            t.onServer(4, player -> com.senne.imttech.config.ImtConfig.Guns.JAM_CHANCE = 0.4f);
            t.shot(8, "gl08_jammed_casing_stuck");
            t.step(5, () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()));
            t.shot(13, "gl09_tap_rack");
            t.step(30, () -> selectSlot(5));
            t.key(15, () -> Minecraft.getInstance().options.keyShift, true);
            t.step(2, () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()));
            t.key(2, () -> Minecraft.getInstance().options.keyShift, false);
            t.shot(10, "gl10_chamber_by_hand");
            t.step(25, () -> selectSlot(0));
            t.step(15, () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_INSPECT.getKey()));
            t.shot(12, "gl11_inspect_side");
            t.shot(18, "gl12_inspect_top");
            t.step(30, () -> selectSlot(3));
            t.shot(15, "gl13_magazine_in_hand");
            t.step(0, () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()));
            t.shot(4, "gl14_magazine_loading");
            // U: een patroon er weer uit (hoofdstuk 54).
            t.step(12, () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_UNLOAD.getKey()));
            t.shot(4, "gl14b_magazine_unloading");
            t.step(15, () -> selectSlot(6));
            t.shot(15, "gl15_barrel_broken");
            t.step(5, () -> selectSlot(4));
            t.lookAt(5, new Vec3(stand.x - 1.2, stand.y + 0.05, stand.z + 0.6));
            t.shot(15, "gl16_casings_on_the_floor");
            t.tap(0, () -> Minecraft.getInstance().options.keyUse);
            t.shot(8, "gl17_casings_picked_up");
            t.onServer(10, player -> ShowcaseFeatures.openBench(player, g.offset(-2, 0, -2)));
            t.shot(25, "gl18_gunsmith_table");
            t.close(5);
            t.onServer(5, player -> ShowcaseFeatures.openBench(player, g.offset(-2, 0, 2)));
            t.shot(25, "gl19_reloading_bench");
            t.close(5);
            t.step(5, () -> selectSlot(0));
            t.stand(5, stand.x, stand.y, stand.z, 0, 2);
            t.camera(5, CameraType.THIRD_PERSON_FRONT);
            t.shot(20, "gl20_third_person");
            t.camera(0, CameraType.FIRST_PERSON);
            t.lookAt(5, new Vec3(stand.x - 1.5, stand.y + 0.5, stand.z - 1.5));
            t.shot(15, "gl21_benches");
        }

        // --- De skilltree (hoofdstuk 52, stap 3): leeg, en met een paar niveaus geleerd.
        if (want("skills")) {
            t.onServer(0, player -> player.setData(com.senne.imttech.registry.ModAttachments.GUN_SKILLS,
                    new com.senne.imttech.guns.GunSkills(25, 7, 7, java.util.Map.of())));
            t.open(10, com.senne.imttech.client.guns.SkillTreeScreen::new);
            t.shot(20, "sk01_skill_tree_with_points");
            t.onServer(0, player -> {
                com.senne.imttech.guns.GunSkillsServer.learn(player, "glock_19", com.senne.imttech.guns.GunSkill.RELOAD);
                com.senne.imttech.guns.GunSkillsServer.learn(player, "glock_19", com.senne.imttech.guns.GunSkill.RELOAD);
                com.senne.imttech.guns.GunSkillsServer.learn(player, com.senne.imttech.guns.GunSkills.GLOBAL, com.senne.imttech.guns.GunSkill.AIM);
            });
            t.shot(20, "sk02_skill_tree_learned");
            t.close(0);
            t.onServer(0, player -> player.setData(com.senne.imttech.registry.ModAttachments.GUN_SKILLS, com.senne.imttech.guns.GunSkills.EMPTY));
        }

        // --- De holster, de pouches en het wiel (hoofdstuk 52, stap 2).
        if (want("gear")) {
            BlockPos g = GUN_RANGE.above();
            Vec3 stand = new Vec3(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            t.onServer(0, VisualCheck::buildGunRange);
            t.onServer(0, ShowcaseFeatures::giveGear);
            t.command(0, "gamemode survival @a");
            t.stand(20, stand.x, stand.y, stand.z, 0, 2);
            t.step(0, () -> selectSlot(0));
            t.open(10, () -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player));
            t.shot(20, "ge01_gear_slots_in_inventory");
            t.close(0);
            t.wheel(10, true);
            t.shot(15, "ge02_quickdraw_wheel");
            t.step(0, () -> ShowcaseFeatures.wheelChoose(0));
            t.wheel(2, false);
            t.shot(5, "ge03_drawing_from_the_holster");
            t.shot(15, "ge04_drawn");
            t.step(5, () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()));
            t.shot(10, "ge05_magazine_pouch_picker");
            t.step(0, () -> ShowcaseFeatures.magPick(0));
            t.shot(12, "ge06_pouch_reload");
            t.wheel(30, true);
            t.step(10, () -> ShowcaseFeatures.wheelChoosePutAway());
            t.wheel(2, false);
            t.camera(20, CameraType.THIRD_PERSON_FRONT);
            t.shot(15, "ge07_holster_on_the_hip_front");
            t.camera(0, CameraType.THIRD_PERSON_BACK);
            t.shot(15, "ge08_pouches_back");
            // Van dichtbij (ingezoomd, zonder HUD): de granaten en de magazijnen in hun vakjes (hoofdstuk 54).
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.step(0, () -> Minecraft.getInstance().options.fov().set(30));
            t.stand(0, stand.x, stand.y, stand.z, 90, 0);
            t.camera(0, CameraType.THIRD_PERSON_FRONT);
            t.shot(12, "ge08b_gear_close_front");
            t.camera(0, CameraType.THIRD_PERSON_BACK);
            t.shot(12, "ge08c_gear_close_back");
            t.stand(0, stand.x, stand.y, stand.z, 0, 0);
            t.camera(0, CameraType.THIRD_PERSON_BACK);
            t.shot(12, "ge08d_gear_close_side");
            t.step(0, () -> Minecraft.getInstance().options.fov().set(70));
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.stand(0, stand.x, stand.y, stand.z, 0, 2);
            t.camera(0, CameraType.FIRST_PERSON);
            // De magazijnpouch open (hoofdstuk 54): zijn vakjes boven je inventaris.
            t.onServer(5, player -> com.senne.imttech.guns.Gear.openPouch(player, com.senne.imttech.guns.GearItem.Kind.MAG_POUCH.ordinal()));
            t.shot(15, "ge09_magazine_pouch_open");
            t.close(0);
            t.command(0, "gamemode creative @a");
        }

        // --- Lasergeweren uit onderdelen (hoofdstuk 55): een titanium rifle en een netherite sniper uit hun onderdelen, zonder
        // Nether Star, met een kapotte emitter, het pistool, de Laser Workbench met een half gebouwd geweer, en de losse onderdelen.
        if (want("laserparts")) {
            BlockPos g = GUN_RANGE.above();
            Vec3 stand = new Vec3(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            t.onServer(0, VisualCheck::buildGunRange);
            t.onServer(0, player -> ShowcaseFeatures.buildLaserBench(player, g));
            t.onServer(0, ShowcaseFeatures::giveLaserParts);
            t.stand(20, stand.x, stand.y, stand.z, 0, 2);
            t.step(0, () -> selectSlot(0));
            t.shot(25, "lp01_rifle_from_parts_hip");
            t.key(0, () -> Minecraft.getInstance().options.keyAttack, true);
            t.key(1, () -> Minecraft.getInstance().options.keyAttack, false);
            t.shot(0, "lp02_rifle_fire");
            t.step(10, () -> selectSlot(1));
            t.shot(20, "lp03_netherite_sniper");
            t.key(0, () -> Minecraft.getInstance().options.keyUse, true);
            t.shot(14, "lp04_netherite_sniper_aim");
            t.key(0, () -> Minecraft.getInstance().options.keyUse, false);
            t.step(10, () -> selectSlot(2));
            t.shot(20, "lp05_no_nether_star");
            t.step(0, () -> selectSlot(3));
            t.shot(20, "lp06_emitter_broken");
            t.step(0, () -> selectSlot(4));
            t.shot(20, "lp07_laser_pistol_from_parts");
            t.step(0, () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_INSPECT.getKey()));
            t.shot(14, "lp08_laser_pistol_inspect");
            t.onServer(30, player -> ShowcaseFeatures.openBench(player, g.offset(-2, 0, 0)));
            t.shot(25, "lp09_laser_workbench");
            t.close(5);
            t.step(0, () -> selectSlot(5));
            t.command(0, "gamemode survival @a");
            t.open(10, () -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player));
            t.shot(20, "lp10_parts_in_inventory");
            t.close(0);
            t.command(0, "gamemode creative @a");
            t.camera(5, CameraType.THIRD_PERSON_FRONT);
            t.step(0, () -> selectSlot(1));
            t.shot(20, "lp11_third_person_sniper");
            t.camera(0, CameraType.FIRST_PERSON);
        }

        // --- Doorlopen met het wiel open (hoofdstuk 56, stap 6), met echte toetsen: Alt en W gaan als Windows-berichten
        // naar het spelvenster (DevKeys), precies zoals van een toetsenbord, zodat ook het stuk vanaf GLFW meetelt. Twee
        // keer: eerst Alt en dan W, en al lopend Alt indrukken. De afstanden staan in de log ("[dev] wheelwalk").
        if (want("wheelwalk")) {
            // [0], [1]: waar de speler bij de vorige meting stond; [2]: de laatste afstand; [3] en [4]: die van A en B.
            double[] mark = new double[5];
            t.onServer(0, ShowcaseFeatures::giveGear);
            t.command(0, "gamemode survival @a");
            t.stand(20, 80.5, -60, 80.5, 0, 10);
            t.step(40, () -> wheelWalkMark(mark));
            t.step(0, () -> DevKeys.press(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT, true));
            t.waitUntil(40, com.senne.imttech.client.guns.GearWheelScreen::isOpen);
            t.step(0, () -> wheelWalkReport("A: wiel open na Alt", mark));
            t.step(2, () -> DevKeys.press(org.lwjgl.glfw.GLFW.GLFW_KEY_W, true));
            t.step(20, () -> wheelWalkReport("A: 1 s W met Alt vast", mark));
            t.step(0, () -> mark[3] = mark[2]);
            t.shot(0, "ge12_wheel_while_walking");
            t.step(0, () -> DevKeys.press(org.lwjgl.glfw.GLFW.GLFW_KEY_W, false));
            t.step(5, () -> DevKeys.press(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT, false));
            t.step(10, () -> wheelWalkReport("A: na loslaten", mark));
            t.step(0, () -> DevKeys.press(org.lwjgl.glfw.GLFW.GLFW_KEY_W, true));
            t.step(10, () -> wheelWalkReport("B: 0,5 s lopen zonder wiel", mark));
            t.step(0, () -> DevKeys.press(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT, true));
            t.waitUntil(40, com.senne.imttech.client.guns.GearWheelScreen::isOpen);
            t.step(20, () -> wheelWalkReport("B: 1 s verder met het wiel open", mark));
            t.step(0, () -> mark[4] = mark[2]);
            t.step(0, () -> DevKeys.press(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT, false));
            t.step(5, () -> DevKeys.press(org.lwjgl.glfw.GLFW.GLFW_KEY_W, false));
            t.step(10, () -> wheelWalkReport("B: na loslaten", mark));
            t.step(0, () -> ImtTech.LOGGER.info(String.format(java.util.Locale.ROOT,
                    "[dev] wheelwalk %s: met het wiel open liep je in 1 s %.2f blokken (Alt eerst) en %.2f blokken (al lopend); minstens 2 verwacht",
                    mark[3] >= 2 && mark[4] >= 2 ? "GOED" : "FOUT", mark[3], mark[4])));
            t.command(0, "gamemode creative @a");
        }

        // --- Holster, slings en cellen (hoofdstuk 55): de sling-vakjes in je inventaris, het wiel met je geweren, trekken van je
        // rug (de Glock gaat in de holster), de cellen in de pouch kiezen, en in derde persoon de geweren op je rug en borst.
        if (want("sling")) {
            BlockPos g = GUN_RANGE.above();
            Vec3 stand = new Vec3(g.getX() + 0.5, g.getY(), g.getZ() + 0.5);
            t.onServer(0, VisualCheck::buildGunRange);
            t.onServer(0, ShowcaseFeatures::giveSlingGear);
            t.command(0, "gamemode survival @a");
            t.stand(20, stand.x, stand.y, stand.z, 0, 2);
            t.step(0, () -> selectSlot(0));
            t.open(10, () -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player));
            t.shot(20, "sl01_sling_slots_in_inventory");
            t.close(0);
            t.wheel(10, true);
            t.shot(15, "sl02_wheel_with_slings");
            t.step(0, () -> ShowcaseFeatures.wheelChooseAction(com.senne.imttech.guns.Gear.ACTION_DRAW_SLING, 0));
            t.wheel(2, false);
            t.shot(6, "sl03_drawing_from_the_back");
            t.shot(22, "sl04_rifle_drawn_glock_holstered");
            t.step(5, () -> KeyMapping.click(com.senne.imttech.client.ImtKeys.GUN_RELOAD.getKey()));
            t.shot(10, "sl05_cell_picker");
            t.step(0, () -> ShowcaseFeatures.magPick(0));
            t.shot(16, "sl06_cell_reload");
            t.wheel(40, true);
            t.step(10, () -> ShowcaseFeatures.wheelChooseAction(com.senne.imttech.guns.Gear.ACTION_DRAW_GUN, -1));
            t.wheel(2, false);
            t.shot(25, "sl07_glock_drawn_rifle_slung");
            t.camera(5, CameraType.THIRD_PERSON_BACK);
            t.shot(15, "sl08_rifle_on_the_back");
            t.camera(0, CameraType.THIRD_PERSON_FRONT);
            t.shot(15, "sl09_sniper_on_the_chest");
            // Van dichtbij (ingezoomd, zonder HUD).
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.step(0, () -> Minecraft.getInstance().options.fov().set(30));
            t.stand(0, stand.x, stand.y, stand.z, 90, 0);
            t.camera(0, CameraType.THIRD_PERSON_BACK);
            t.shot(12, "sl10_back_close");
            t.camera(0, CameraType.THIRD_PERSON_FRONT);
            t.shot(12, "sl11_chest_close");
            t.stand(0, stand.x, stand.y, stand.z, 0, 0);
            t.camera(0, CameraType.THIRD_PERSON_BACK);
            t.shot(12, "sl12_side_close");
            t.step(0, () -> Minecraft.getInstance().options.fov().set(70));
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.stand(0, stand.x, stand.y, stand.z, 0, 2);
            t.camera(0, CameraType.FIRST_PERSON);
            t.open(10, com.senne.imttech.client.guns.SkillTreeScreen::new);
            t.step(2, () -> ShowcaseFeatures.skillTab("laser_rifle"));
            t.shot(15, "sl13_skill_tree_rifle_tab");
            t.close(0);
            t.command(0, "gamemode creative @a");
        }

        // --- Handboeien (hoofdstuk 48): het aftellen tijdens het vasthouden, Bram geboeid met het touw, en de
        // soldaat die aan het touw achter je aan komt.
        if (want("cuffs")) {
            BlockPos c = CUFF_YARD;
            t.onServer(0, player -> buildCuffYard(player, CUFF_YARD));
            t.stand(20, c.getX() + 2.5, c.getY(), c.getZ() + 6.5, 180, 8);
            t.lookAt(10, Vec3.atBottomCenterOf(c.offset(2, 0, 4)).add(0, 1.2, 0));
            t.key(10, () -> Minecraft.getInstance().options.keyUse, true);
            t.shot(25, "hc01_handcuffing_countdown");
            t.key(23, () -> Minecraft.getInstance().options.keyUse, false); // het boeien duurt twee seconden; niet blijven klikken (dat laat weer los)
            // Bram is een nep-speler zonder spel erachter: de zet die hem vóór je trekt, voert niemand uit. Dus zetten we hem
            // zelf op zijn plek (en de boeien om, mocht het aftellen net gemist zijn).
            t.onServer(8, player -> {
                ServerPlayer bram = player.serverLevel().getServer().getPlayerList().getPlayerByName("Bram");
                if (bram != null) {
                    if (!com.senne.imttech.cuffs.Handcuffs.isCuffed(bram)) {
                        com.senne.imttech.cuffs.Handcuffs.cuff(player, bram);
                    }
                    bram.teleportTo(player.serverLevel(), player.getX(), player.getY(), player.getZ() - ImtConfig.Handcuffs.HOLD_DISTANCE, 180, 0);
                    bram.setYBodyRot(180);
                    bram.setYHeadRot(180);
                }
            });
            t.shot(50, "hc02_handcuffed_hands_behind_back"); // het actiebalk-bericht van het aftellen blijft drie seconden staan
            t.lookAt(5, Vec3.atBottomCenterOf(c.offset(4, 0, 4)).add(0, 1.2, 0));
            t.tap(10, () -> Minecraft.getInstance().options.keyUse);
            t.stand(30, c.getX() + 2.5, c.getY(), c.getZ() + 11.5, 180, 10);
            t.shot(25, "hc03_soldier_dragged_along");
        }

        // --- Autorisatiecodes en de Auth Console (hoofdstuk 49): de code in Status, de instellingen in Settings, een
        // verzoek van Bram in de app Requests, en de console groen na Accept.
        if (want("auth")) {
            BlockPos y = AUTH_YARD;
            t.onServer(0, player -> buildAuthYard(player, AUTH_YARD));
            t.onServer(30, player -> wakeAuthServer(player, AUTH_YARD));
            t.stand(20, y.getX() + 2.5, y.getY(), y.getZ() + 2.5, 0, 10);
            t.onServer(10, player -> com.senne.imttech.security.os.SecurityOs.open(player, AUTH_YARD));
            t.step(15, () -> osTour(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.STATUS)));
            t.shot(20, "au01_status_auth_code");
            t.step(0, () -> osTour(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.SETTINGS)));
            t.shot(15, "au02_settings_auth_codes");
            t.close(0);
            t.onServer(10, player -> bramAsks(player, y.offset(3, 0, 4)));
            t.onServer(15, player -> com.senne.imttech.security.os.SecurityOs.open(player, AUTH_YARD));
            t.step(15, () -> osTour(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.REQUESTS)));
            t.shot(20, "au03_requests_app");
            t.step(0, () -> osTour(os -> os.tourRequestAnswer(true)));
            t.close(15);
            t.lookAt(5, Vec3.atCenterOf(y.offset(3, 0, 4)));
            t.shot(15, "au04_console_accepted");
        }

        // --- De I.M.T Phone (hoofdstuk 50): verbinden, een PIN kiezen, de kleine desktop (Status, Requests, Messages), en de
        // telefoon in de app Access van de kern zelf.
        if (want("phone")) {
            BlockPos y = AUTH_YARD;
            t.onServer(0, player -> buildAuthYard(player, AUTH_YARD));
            t.onServer(30, player -> wakeAuthServer(player, AUTH_YARD));
            t.onServer(0, player -> givePhone(player, AUTH_YARD));
            t.stand(20, y.getX() + 2.5, y.getY(), y.getZ() + 2.5, 0, 10);
            t.onServer(10, player -> com.senne.imttech.security.os.Phones.use(player, player.getMainHandItem()));
            t.step(15, () -> loginTour(login -> login.tourType(authYardIp, "bitech1")));
            t.shot(10, "ph01_phone_connect");
            t.step(0, () -> loginTour(com.senne.imttech.client.os.OsLoginScreen::tourConnect));
            t.step(20, () -> loginTour(login -> login.tourType("", "1234")));
            t.shot(10, "ph02_phone_choose_pin");
            t.step(0, () -> loginTour(com.senne.imttech.client.os.OsLoginScreen::tourConnect));
            t.step(25, () -> osTour(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.STATUS)));
            t.shot(15, "ph03_phone_desktop_status");
            t.onServer(0, player -> bramAsks(player, y.offset(3, 0, 4)));
            t.step(15, () -> osTour(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.REQUESTS)));
            t.shot(15, "ph04_phone_requests");
            t.step(0, () -> osTour(os -> os.tourRequestAnswer(true)));
            t.step(15, () -> osTour(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.MESSAGES)));
            t.shot(15, "ph05_phone_messages");
            t.close(0);
            t.onServer(10, player -> com.senne.imttech.security.os.SecurityOs.open(player, AUTH_YARD));
            t.step(15, () -> osTour(os -> os.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.ACCESS)));
            t.shot(15, "ph06_access_phones");
            t.close(0);
        }

        // --- Torens en bunkers uit kamers (hoofdstuk 51): twee willekeurige torens naast elkaar, en een bunker (hier op
        // vlak land, zodat je de zalen ziet; in de wereld zit hij in een berg).
        if (want("rooms")) {
            BlockPos y = ROOMS_YARD;
            // Eerst erheen (dan laadt dat stuk wereld), dan bouwen, en ruim de tijd nemen: het zijn twee torens van 110 hoog
            // en een bunker van 85 lang, ver van alles.
            t.view(0, y.getX() + 50.5, y.getY() + 30, y.getZ() + 110.5, 0, 0);
            t.onServer(60, player -> buildRoomsYard(player, ROOMS_YARD));
            t.view(100, y.getX() + 50.5, y.getY() + 75, y.getZ() + 165.5, 0, 0);
            t.lookAt(100, new Vec3(y.getX() + 50.5, y.getY() + 50, y.getZ() + 20.5));
            t.shot(200, "rm01_two_random_towers");
            t.view(0, y.getX() + 17.5, y.getY() + 12, y.getZ() + 230, 0, 0);
            t.lookAt(60, new Vec3(y.getX() + 17.5, y.getY() + 3, y.getZ() + 250.5));
            t.shot(120, "rm02_random_bunker_from_the_front");
            t.view(0, y.getX() + 70.5, y.getY() + 40, y.getZ() + 290.5, 0, 0);
            t.lookAt(60, new Vec3(y.getX() + 17.5, y.getY() + 4, y.getZ() + 290.5));
            t.shot(120, "rm03_random_bunker_from_the_side");
        }

        // --- Hoofdstuk 54: de generatorkamer van de toren, als los stuk: de poort van rayshields met de lezer en de bewaker,
        // binnenin de cellen, de kast en het rek, en de zwakke plek in de oostmuur.
        if (want("powerroom")) {
            BlockPos y = POWER_YARD;
            t.view(0, y.getX() + 20.5, y.getY() + 2, y.getZ() + 26.5, 180, 4);
            t.onServer(40, player -> com.senne.imttech.structures.RoomPlacer.placeRoom(player.serverLevel(), "power", POWER_YARD,
                    net.minecraft.world.level.block.Rotation.NONE));
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.view(60, y.getX() + 20.5, y.getY() + 2, y.getZ() + 23.5, 180, 4);
            t.shot(40, "pr01_generator_room_gate");
            t.view(0, y.getX() + 20.5, y.getY() + 2, y.getZ() + 18.5, 180, 6);
            t.shot(30, "pr02_inside_the_generator_vault");
            t.view(0, y.getX() + 31.5, y.getY() + 2, y.getZ() + 19.5, 90, 4);
            t.shot(30, "pr03_service_hatch");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        }

        // --- Inbreken (hoofdstuk 12, 9 oktober): een keycardlezer hacken met de Bypass Kit. Het
        // stroomcircuit: een groep kiezen, half opgelost, een stukje dat ALARM raakt (dan Esc), en
        // daarna helemaal (ACCESS, de lezer wordt groen); tot slot een keer betrapt.
        if (want("bypass")) {
            BlockPos lab = BYPASS_LAB;
            BlockPos reader = lab.offset(4, 1, 3);
            t.onServer(0, VisualCheck::buildBypassLab);
            t.stand(10, lab.getX() + 4.5, lab.getY(), lab.getZ() + 6.5, 180, 12);
            t.shot(60, "bp01_reader_and_kit");
            t.onServer(0, player -> com.senne.imttech.bypass.Bypass.start(player, reader,
                    net.minecraft.world.InteractionHand.MAIN_HAND));
            t.shot(10, "bp02_choose_group");
            t.step(0, () -> circuit(screen -> screen.tourChoose("security")));
            t.shot(10, "bp03_circuit_start");
            t.step(0, () -> circuit(screen -> screen.tourSolve(-1)));
            t.shot(10, "bp04_circuit_half");
            t.step(0, () -> circuit(com.senne.imttech.client.bypass.CircuitScreen::tourTouchAlarm));
            t.shot(4, "bp05_circuit_alarm");
            t.close(0);
            t.onServer(5, player -> com.senne.imttech.bypass.Bypass.start(player, reader,
                    net.minecraft.world.InteractionHand.MAIN_HAND));
            t.step(10, () -> circuit(screen -> screen.tourChoose("staff")));
            t.shot(10, "bp06_circuit_staff");
            t.step(0, () -> circuit(screen -> screen.tourSolve(1000)));
            t.shot(14, "bp07_circuit_access");
            t.close(2);
            t.shot(2, "bp08_reader_green");
            t.onServer(20, player -> com.senne.imttech.bypass.Bypass.start(player, reader,
                    net.minecraft.world.InteractionHand.MAIN_HAND));
            t.step(10, () -> circuit(screen -> screen.tourChoose("guest")));
            t.step(5, () -> circuit(com.senne.imttech.client.bypass.CircuitScreen::tourTouchAlarm));
            t.shot(30, "bp09_circuit_caught");
            t.close(30);
            // Het codepaneel ernaast: stoffen, de afdrukken, twee pogingen, en dan gekraakt.
            BlockPos panel = lab.offset(5, 1, 3);
            t.onServer(5, player -> com.senne.imttech.bypass.Bypass.start(player, panel,
                    net.minecraft.world.InteractionHand.MAIN_HAND));
            t.shot(10, "bp10_panel_dust");
            t.step(0, () -> fingerprints(screen -> screen.tourDust(5)));
            t.shot(3, "bp11_panel_half_dusted");
            t.step(0, () -> fingerprints(screen -> screen.tourDust(12)));
            t.shot(5, "bp12_panel_prints");
            t.step(30, () -> fingerprints(screen -> {
                screen.tourType("1147");
                screen.tourTry();
            }));
            t.step(10, () -> fingerprints(screen -> {
                screen.tourType("4117");
                screen.tourTry();
            }));
            t.step(10, () -> fingerprints(screen -> screen.tourType("41")));
            t.shot(5, "bp13_panel_order");
            t.step(0, () -> fingerprints(screen -> {
                screen.tourType("4711");
                screen.tourTry();
            }));
            t.shot(15, "bp14_panel_cracked");
            t.close(0);
            t.shot(4, "bp15_panel_green");
            // Zakkenrollen: alleen het scherm (in deze wereld is er geen tweede speler). De zak van
            // "Lotte": dicht, de rommel opzij, de meter bijna vol, een kaart eruit, en een keer betrapt.
            t.open(5, () -> new com.senne.imttech.client.pickpocket.PocketScreen(-1, "Lotte", 3, 7L));
            t.shot(10, "bp16_pocket");
            t.step(0, () -> pocket(com.senne.imttech.client.pickpocket.PocketScreen::tourUncover));
            t.shot(5, "bp17_pocket_uncovered");
            t.step(0, () -> pocket(screen -> screen.tourNotice(0.86f)));
            t.shot(3, "bp18_pocket_noticing");
            t.step(0, () -> pocket(screen -> {
                screen.tourNotice(0.3f);
                screen.tourPull();
                net.minecraft.nbt.CompoundTag taken = new net.minecraft.nbt.CompoundTag();
                taken.putString("result", "taken");
                taken.putString("name", "Keycard: Lab access");
                taken.putInt("colour", 3);
                taken.putInt("number", 1001);
                screen.onResult(taken);
            }));
            t.shot(5, "bp19_pocket_taken");
            t.close(0);
            t.open(5, () -> new com.senne.imttech.client.pickpocket.PocketScreen(-1, "Lotte", 2, 11L));
            t.step(5, () -> pocket(screen -> {
                net.minecraft.nbt.CompoundTag caught = new net.minecraft.nbt.CompoundTag();
                caught.putString("result", "caught");
                screen.onResult(caught);
            }));
            t.shot(5, "bp20_pocket_caught");
            t.close(0);
            // Hoofdstuk 33: de draden van een laserzender doorknippen. Een poort van twee zenders voor de lezer: de
            // kluwen in het donker, de zaklamp op de draad van de LENS, de goede stekker gekozen en geknipt, de poort uit
            // (vonken), het scherm van de zender met WIRES CUT en Repair wires, hersteld, en tot slot een verkeerde draad.
            BlockPos laser = lab.offset(8, 0, 4);
            t.onServer(0, VisualCheck::buildBypassLaser);
            t.stand(5, lab.getX() + 4.5, lab.getY(), lab.getZ() + 6.5, 180, 12);
            t.lookAt(5, new Vec3(laser.getX() - 2.0, laser.getY() + 1.0, laser.getZ() + 0.5));
            t.shot(60, "bp21_laser_gate");
            t.onServer(0, player -> com.senne.imttech.bypass.Bypass.start(player, laser,
                    net.minecraft.world.InteractionHand.MAIN_HAND));
            t.step(10, () -> wires(com.senne.imttech.client.bypass.WireScreen::tourDark));
            t.shot(3, "bp22_wires_dark");
            t.step(0, () -> wires(screen -> screen.tourTrace(0.4f)));
            t.shot(3, "bp23_wires_flashlight");
            t.step(0, () -> wires(screen -> {
                screen.tourTrace(0.97f);
                screen.tourSelect(-1);
            }));
            t.shot(3, "bp24_wires_selected");
            t.step(0, () -> wires(com.senne.imttech.client.bypass.WireScreen::tourCut));
            t.shot(8, "bp25_wires_cut");
            t.close(0);
            t.shot(25, "bp26_laser_cut");
            t.onServer(0, player -> com.senne.imttech.shield.LaserEmitters.open(player, laser));
            t.shot(10, "bp27_laser_screen_cut");
            t.onServer(0, player -> com.senne.imttech.shield.LaserEmitters.handleAction(player,
                    new com.senne.imttech.network.LaserActionPayload(laser, com.senne.imttech.network.LaserActionPayload.Op.REPAIR, 0)));
            t.shot(10, "bp28_laser_screen_repaired");
            t.close(0);
            t.onServer(40, player -> com.senne.imttech.bypass.Bypass.start(player, laser,
                    net.minecraft.world.InteractionHand.MAIN_HAND));
            t.step(10, () -> wires(screen -> {
                screen.tourDark();
                screen.tourSelect(-2);
                screen.tourCut();
            }));
            t.shot(8, "bp29_wires_zap");
            t.close(0);
        }

        // --- Zones (hoofdstuk 10, 8 oktober): een L-vormige basis met een torentje en de blauwe rand
        // eromheen (waar het torentje tegen de basis staat, hoort geen wand). Zonder stroom wordt de
        // rand rood; met "Zone border" uit zie je hem niet.
        if (want("zones")) {
            t.onServer(0, VisualCheck::buildZoneBase);
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.stand(10, 153.5, -60, 54.5, -40, 8);
            t.shot(60, "z01_zone_outside");
            t.stand(10, 163.5, -60, 66.5, -120, 20);
            t.shot(30, "z02_zone_inside");
            t.stand(10, 178.5, -56, 78.5, 140, 18);
            t.shot(30, "z03_zone_tower_corner");
            // De Zone Marker in je hand: een oranje doos tussen twee aangeklikte hoeken (een aanbouw
            // aan de oostkant), en boven elke kubus zijn nummer.
            t.onServer(0, player -> {
                net.minecraft.world.item.ItemStack marker = new net.minecraft.world.item.ItemStack(ModItems.ZONE_MARKER.get());
                marker.set(com.senne.imttech.registry.ModDataComponents.ZONE_SELECTION.get(),
                        com.senne.imttech.security.zone.ZoneSelection.EMPTY
                                .withFirst(new BlockPos(172, -61, 62)).withSecond(new BlockPos(177, -57, 67)));
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, marker);
            });
            t.stand(10, 186.5, -57, 56.5, 60, 15);
            t.shot(30, "z03b_zone_marker");
            // De app Zone op de desktop van de kern: de kaart met de twee kubussen, de tweede gekozen.
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.stand(10, 166.5, -60, 67.5, 0, 0);
            t.onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, ZONE_CORE));
            t.step(10, () -> {
                if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
                    os.tourZone(1);
                }
            });
            t.shot(15, "z03c_zone_app");
            t.close(5);
            // De Autoscan (hoofdstuk 45): een dichte bunker naast de basis; sluipen + rechtsklik met een lege Zone
            // Marker erin, en de zone ligt om de kamer plus drie blokken muur (de rand zie je met de marker in je hand).
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                BlockPos b = new BlockPos(190, -61, 60);
                for (BlockPos pos : BlockPos.betweenClosed(b, b.offset(8, 6, 8))) {
                    boolean shell = pos.getX() == b.getX() || pos.getX() == b.getX() + 8 || pos.getY() == b.getY()
                            || pos.getY() == b.getY() + 6 || pos.getZ() == b.getZ() || pos.getZ() == b.getZ() + 8;
                    level.setBlockAndUpdate(pos, shell ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
                level.setBlockAndUpdate(b.offset(4, 5, 4), Blocks.GLOWSTONE.defaultBlockState());
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.item.ItemStack(ModItems.ZONE_MARKER.get()));
                player.teleportTo(level, b.getX() + 4.5, b.getY() + 1, b.getZ() + 2.5, 0, 10);
                com.senne.imttech.security.zone.ZoneMarkerItem.autoscan(level, player, b.offset(4, 1, 2), null);
            });
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            t.stand(10, 194.5, -60, 62.5, 0, 12);
            t.shot(40, "zs01_autoscan_bunker_inside");
            t.stand(10, 203.5, -58, 74.5, 135, 18);
            t.shot(30, "zs02_autoscan_bunker_outside");
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.onServer(0, player -> player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                    net.minecraft.world.item.ItemStack.EMPTY));
            t.step(0, () -> Minecraft.getInstance().options.hideGui = true);
            // Geen stroom: de generator onder de kern weg. Binnen twee seconden staat de zone uit.
            t.onServer(0, player -> player.serverLevel().setBlockAndUpdate(ZONE_CORE.below(), Blocks.STONE.defaultBlockState()));
            t.stand(10, 153.5, -60, 54.5, -40, 8);
            t.shot(80, "z04_zone_no_power");
            // De rand uit (G > Information > Zone border), en daarna weer aan.
            t.step(0, () -> {
                if (com.senne.imttech.client.ImtClientConfig.zoneBorder()) {
                    com.senne.imttech.client.ImtClientConfig.toggleZoneBorder();
                }
            });
            t.shot(20, "z05_zone_border_off");
            t.step(0, () -> {
                if (!com.senne.imttech.client.ImtClientConfig.zoneBorder()) {
                    com.senne.imttech.client.ImtClientConfig.toggleZoneBorder();
                }
            });
            // De rand zie je alleen door de bril (jouw wens, 9 oktober): weer stroom, dan de bril af.
            t.onServer(0, player -> {
                ServerLevel level = player.serverLevel();
                level.setBlockAndUpdate(ZONE_CORE.below(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
                if (level.getBlockEntity(ZONE_CORE.below()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
                    generator.fill(200_000);
                }
            });
            t.shot(70, "z05b_zone_with_glasses");
            t.onServer(0, player -> player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY));
            t.shot(20, "z06_zone_no_glasses");
            t.onServer(0, DevWorld::wearGlasses);
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        }

        // --- How it works (hoofdstuk 63): de plaatjes van echte schermen (alleen als je erom vraagt: dat verandert
        // bestanden van de mod), en W op een item, de animatie van de Power Battery, het knopje en het overzicht.
        if (ONLY.contains("howtopics")) {
            HowToTour.pictures(t);
        }
        if (want("howto")) {
            HowToTour.tour(t);
        }
        if (ONLY.contains("howtoall")) {
            HowToTour.allScenes(t, ONLY);
        }
}

    /**
     * Een beveiligingskern van deze speler op {@code core}, met stroom en een serverrack ernaast (dan hebben robots en
     * drones verbinding), als die er nog niet staat. Voor de groepen secdrones en training.
     */
    private static void ensureBotCore(ServerLevel level, ServerPlayer player, BlockPos core) {
        if (level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity) {
            return;
        }
        level.setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState());
        if (level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
            be.setOwner(player);
            be.ensureSystem(level);
        }
        level.setBlockAndUpdate(core.below(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(core.below()) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
            charger.fill(200_000);
        }
        BlockPos rack = core.east(2);
        level.setBlockAndUpdate(rack.below(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(rack.below()) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
            charger.fill(200_000);
        }
        level.setBlockAndUpdate(rack, ModBlocks.SERVER_RACK.get().defaultBlockState());
        if (level.getBlockEntity(rack) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
            be.insert(new ItemStack(ModItems.SERVER_CPU.get()));
            be.insert(new ItemStack(ModItems.SERVER_RAM.get()));
            be.insert(new ItemStack(ModItems.SERVER_SSD.get()));
            be.insert(new ItemStack(ModItems.SERVER_FAN.get()));
        }
    }

    /**
     * Een security drone (hoofdstuk 25) van deze speler: op het Drone Dock op {@code pos} (zijn thuis, uit), of hangend
     * in dat blok.
     */
    static com.senne.imttech.robot.GuardDroneEntity securityDrone(ServerLevel level, net.minecraft.server.level.ServerPlayer player,
                                                                         BlockPos pos, boolean onDock) {
        com.senne.imttech.robot.GuardDroneEntity drone = com.senne.imttech.registry.ModEntities.GUARD_DRONE.get().create(level);
        double y = onDock ? pos.getY() + 4.0 / 16.0 : pos.getY() + com.senne.imttech.robot.GuardDroneEntity.HOVER;
        drone.moveTo(pos.getX() + 0.5, y, pos.getZ() + 0.5, 0, 0);
        drone.setYaw(0);
        drone.brain().body().setFacing(net.minecraft.core.Direction.SOUTH);
        drone.setOwner(player);
        if (onDock && level.getBlockEntity(pos) instanceof com.senne.imttech.arsenal.DroneDockBlockEntity dock) {
            drone.setHome(pos);
            dock.setBot(drone.getUUID());
        }
        level.addFreshEntity(drone);
        return drone;
    }

    /** Een robotprogramma uit deze code (hoofdstuk 23), zoals de app Logic hem bewaart. */
    private static com.senne.imttech.logic.program.LogicProgram robotProgram(String name, String code) {
        return robotProgram(name, code, com.senne.imttech.logic.program.ProgramKind.ROBOT);
    }

    /** Een programma van deze soort (een robot- of droneprogramma) uit deze code. */
    static com.senne.imttech.logic.program.LogicProgram robotProgram(String name, String code,
                                                                                com.senne.imttech.logic.program.ProgramKind kind) {
        return new com.senne.imttech.logic.program.LogicProgram(name, true,
                com.senne.imttech.logic.program.GraphConverter.toGraph(com.senne.imttech.logic.program.ScriptParser.parse(code, kind), null),
                code, kind);
    }

    /** Waar de muur van de Remote Frags staat (zie buildFragWall; eerst stond hier de C4-muur). */
    private static final BlockPos FRAG_WALL = new BlockPos(120, -60, 128);

    /** Een Remote Frag (nog nergens aan gekoppeld) tegen de kant {@code outward} van {@code support}. */
    private static void stickFrag(ServerLevel level, BlockPos support, Direction outward) {
        BlockPos pos = support.relative(outward);
        BlockState state = ModBlocks.REMOTE_FRAG.get().defaultBlockState();
        net.minecraft.world.level.block.state.properties.AttachFace face = outward == Direction.UP
                ? net.minecraft.world.level.block.state.properties.AttachFace.FLOOR
                : outward == Direction.DOWN ? net.minecraft.world.level.block.state.properties.AttachFace.CEILING
                : net.minecraft.world.level.block.state.properties.AttachFace.WALL;
        state = state.setValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACE, face);
        if (face == net.minecraft.world.level.block.state.properties.AttachFace.WALL) {
            state = state.setValue(net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock.FACING, outward);
        }
        level.setBlockAndUpdate(pos, state);
    }

    /**
     * Een muur van stenen bakstenen (twee dik, 9 lang, 5 hoog) met een overstekje, en drie Remote Frags: tegen de muur,
     * op de vloer en onder het overstekje. Ernaast een muurtje obsidiaan. Wat er van een vorige keer stond (een kern,
     * gaten), wordt eerst weggehaald.
     */
    private static void buildFragWall(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos w = FRAG_WALL;
        for (BlockPos pos : BlockPos.betweenClosed(w.offset(-2, 0, -5), w.offset(8, 6, 15))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        for (int x = 0; x <= 1; x++) {
            for (int y = 0; y <= 4; y++) {
                for (int z = 0; z <= 8; z++) {
                    level.setBlockAndUpdate(w.offset(x, y, z), Blocks.STONE_BRICKS.defaultBlockState());
                }
            }
        }
        for (int x = 2; x <= 5; x++) {
            level.setBlockAndUpdate(w.offset(x, 3, 6), Blocks.STONE_BRICKS.defaultBlockState());
        }
        for (int y = 0; y <= 3; y++) {
            for (int z = 10; z <= 13; z++) {
                level.setBlockAndUpdate(w.offset(1, y, z), Blocks.OBSIDIAN.defaultBlockState());
            }
        }
        stickFrag(level, w.offset(1, 2, 3), Direction.EAST);
        stickFrag(level, w.offset(3, -1, 2), Direction.UP);
        stickFrag(level, w.offset(4, 3, 6), Direction.DOWN);
    }

    /** Waar het lab van hoofdstuk 12 staat (zie buildBypassLab). */
    static final BlockPos BYPASS_LAB = new BlockPos(170, -60, 128);

    /** Doet iets met de zak die nu open is (als hij open is). */
    private static void pocket(java.util.function.Consumer<com.senne.imttech.client.pickpocket.PocketScreen> action) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.pickpocket.PocketScreen screen) {
            action.accept(screen);
        }
    }

    /** Doet iets met het vingerafdrukken-scherm dat nu open is (als het open is). */
    private static void fingerprints(java.util.function.Consumer<com.senne.imttech.client.bypass.FingerprintScreen> action) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.bypass.FingerprintScreen screen) {
            action.accept(screen);
        }
    }

    /** Doet iets met het stroomcircuit dat nu open is (als het open is). */
    private static void circuit(java.util.function.Consumer<com.senne.imttech.client.bypass.CircuitScreen> action) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.bypass.CircuitScreen screen) {
            action.accept(screen);
        }
    }

    /** Doet iets met de draden van een laserzender die nu open zijn (als ze open zijn; hoofdstuk 33). */
    private static void wires(java.util.function.Consumer<com.senne.imttech.client.bypass.WireScreen> action) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.bypass.WireScreen screen) {
            action.accept(screen);
        }
    }

    /**
     * Hoofdstuk 33: een laserpoort van twee zenders op elkaar, westwaarts voor de lezer en het paneel (op een volle
     * generator, met een redstoneblok), tot een steen 5 blokken verder. Elke keer nieuw: hele draden, niet op slot.
     * Jij zette hem neer. In je hand de Bypass Kit.
     */
    static void buildBypassLaser(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos bottom = BYPASS_LAB.offset(8, 0, 4);
        for (BlockPos pos : BlockPos.betweenClosed(bottom.offset(-6, 0, 0), bottom.offset(1, 1, 0))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        level.setBlockAndUpdate(bottom.east(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(bottom.east()) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
            charger.fill(200_000);
        }
        BlockState emitter = ModBlocks.LASER_EMITTER.get().defaultBlockState()
                .setValue(com.senne.imttech.shield.LaserEmitterBlock.FACING, Direction.WEST);
        for (BlockPos pos : new BlockPos[] {bottom, bottom.above()}) {
            level.setBlockAndUpdate(pos, emitter);
            ModBlocks.LASER_EMITTER.get().setPlacedBy(level, pos, level.getBlockState(pos), player, ItemStack.EMPTY);
        }
        for (int dy = 0; dy < 2; dy++) {
            level.setBlockAndUpdate(bottom.offset(-6, dy, 0), Blocks.SMOOTH_STONE.defaultBlockState());
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.BYPASS_KIT.get()));
        com.senne.imttech.bypass.Bypass.clear();
    }

    /** Doet iets met de desktop die nu open is (als hij open is), voor de app Keycards. */
    private static void keycards(java.util.function.Consumer<com.senne.imttech.client.os.SecurityOsScreen> action) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen screen) {
            action.accept(screen);
        }
    }

    /**
     * Een kern met een server (een rack met CPU, RAM en SSD; allebei op een volle generator), een muurtje
     * met een keycardlezer "Lab door" en een codepaneel "Front door", en een diagram: een kaart van
     * niveau 3 of hoger geeft groen, anders rood, en de code 4711 op het paneel ook. In je hand de Bypass Kit.
     */
    static void buildBypassLab(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos lab = BYPASS_LAB;
        BlockPos rack = lab.offset(-2, 0, 0);
        level.setBlockAndUpdate(lab, ModBlocks.SECURITY_CORE.get().defaultBlockState());
        for (BlockPos generator : new BlockPos[] {lab.below(), rack.below()}) {
            level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                charger.fill(200_000);
            }
        }
        level.setBlockAndUpdate(rack, ModBlocks.SERVER_RACK.get().defaultBlockState()
                .setValue(com.senne.imttech.logic.ServerRackBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(rack) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
            be.insert(new ItemStack(ModItems.SERVER_CPU.get()));
            be.insert(new ItemStack(ModItems.SERVER_RAM.get()));
            be.insert(new ItemStack(ModItems.SERVER_SSD.get()));
        }
        for (int dx = 3; dx <= 6; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                level.setBlockAndUpdate(lab.offset(dx, dy, 2), Blocks.SMOOTH_STONE.defaultBlockState());
            }
        }
        String[][] inputs = {{"4", "Lab door"}, {"5", "Front door"}};
        for (String[] input : inputs) {
            BlockPos pos = lab.offset(Integer.parseInt(input[0]), 1, 3);
            BlockState state = (input[1].equals("Lab door") ? ModBlocks.KEYCARD_READER.get() : ModBlocks.CODE_PANEL.get())
                    .defaultBlockState().setValue(com.senne.imttech.logic.InputDeviceBlock.FACING, Direction.SOUTH);
            level.setBlockAndUpdate(pos, state);
            if (level.getBlockEntity(pos) instanceof com.senne.imttech.logic.InputDeviceBlockEntity be) {
                be.setName(input[1]);
            }
        }
        if (level.getBlockEntity(lab) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
            core.setOwner(player);
            core.ensureSystem(level);
            // De keycard-groepen (25 oktober): "security" zit in "staff", "guest" niet.
            core.logic().addGroup("staff", "");
            core.logic().addGroup("security", "staff");
            core.logic().addGroup("guest", "");
            String code = """
                    when keycard("Lab door"):
                        if card.in_group("staff"):
                            accept()
                        else:
                            deny()

                    when codepanel("Front door"):
                        if code == "4711":
                            accept()
                        else:
                            deny()
                    """;
            core.logic().put(null, new com.senne.imttech.logic.program.LogicProgram("Lab", true,
                    com.senne.imttech.logic.program.GraphConverter.toGraph(
                            com.senne.imttech.logic.program.ScriptParser.parse(code), null), code));
            core.setChanged();
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.BYPASS_KIT.get()));
    }

    /** Waar de generator voor de Arc Lamp staat (zie "light"): ver van de rest, want zijn licht reikt 100 blokken. */
    static final BlockPos LAMP_YARD = new BlockPos(-260, -60, -260);

    /**
     * Een volle generator, daarnaast een kabel over het gras met halverwege een lichtschakelaar (uit), en
     * aan het eind een paal van vier blokken (met de kabel erin) met de Arc Lamp erop. In je hotbar de lamp
     * en de schakelaar.
     */
    static void buildLampYard(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos y = LAMP_YARD;
        level.setBlockAndUpdate(y, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(y) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
            generator.fill(200_000);
        }
        for (int x = 1; x <= 7; x++) {
            com.senne.imttech.energy.PowerCables.add(level, y.east(x));
        }
        for (int up = 0; up < 4; up++) {
            level.setBlockAndUpdate(y.east(7).above(up), Blocks.POLISHED_ANDESITE.defaultBlockState());
            com.senne.imttech.energy.PowerCables.add(level, y.east(7).above(up));
        }
        level.setBlockAndUpdate(y.east(7).above(4), ModBlocks.ARC_LAMP.get().defaultBlockState());
        level.setBlockAndUpdate(y.east(3), ModBlocks.LIGHT_SWITCH.get().defaultBlockState()
                .setValue(com.senne.imttech.light.LightSwitchBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(com.senne.imttech.light.LightSwitchBlock.FACING, Direction.NORTH)
                .setValue(com.senne.imttech.light.LightSwitchBlock.ON, false));
        player.getInventory().setItem(0, new ItemStack(ModItems.LIGHT_SWITCH.get(), 8));
        player.getInventory().setItem(1, new ItemStack(ModItems.ARC_LAMP.get(), 4));
    }

    /** Waar de relais van 13 oktober staan (zie "relays"): een eigen plekje. */
    private static final BlockPos RELAY_YARD = new BlockPos(-200, -60, -320);

    /**
     * Drie relais op een rij met een redstonelamp erachter: "Door 1" en "Door 2" (groep "security room", in
     * "restricted", in "base") en "Garden light" (groep "garden", omgekeerd: zijn lamp brandt terwijl hij uit
     * staat). Ervoor een relay emitter op "base" (Follow) met een hendel (uit) ernaast, en links een
     * beveiligingskern (van jou, met wachtwoord) voor de app Groups.
     */
    private static void buildRelayYard(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos y = RELAY_YARD;
        var data = com.senne.imttech.redstone.ImtRelays.EMPTY;
        String[][] relays = {{"Door 1", "security room"}, {"Door 2", "security room"}, {"Garden light", "garden"}};
        for (int i = 0; i < relays.length; i++) {
            BlockPos relay = y.offset(1 + i * 2, 0, 0);
            boolean inverted = i == 2;
            // Eerst de lamp: een redstonelamp kijkt pas of hij stroom krijgt als er naast hem iets verandert.
            level.setBlockAndUpdate(relay.north(), Blocks.REDSTONE_LAMP.defaultBlockState());
            level.setBlockAndUpdate(relay, ModBlocks.RELAY.get().defaultBlockState()
                    .setValue(com.senne.imttech.redstone.RelayBlock.INVERTED, inverted));
            data = data.with(new com.senne.imttech.redstone.Relay(relays[i][0], relays[i][1], level.dimension(), relay));
        }
        data = ShowcaseFeatures.exampleGroups(data);
        player.setData(com.senne.imttech.registry.ModAttachments.RELAYS, data);
        BlockPos core = y.offset(0, 0, 3);
        level.setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState());
        if (level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
            be.setOwner(player);
            be.ensureSystem(level);
            be.setPassword("bitech1", level.random);
        }
        BlockPos emitter = y.offset(6, 0, 2);
        level.setBlockAndUpdate(emitter, ModBlocks.RELAY_EMITTER.get().defaultBlockState());
        if (level.getBlockEntity(emitter) instanceof com.senne.imttech.redstone.RelayEmitterBlockEntity entity) {
            entity.setOwner(player);
            entity.configure(player, "base", com.senne.imttech.redstone.RelayEmitterBlockEntity.Mode.FOLLOW);
        }
        level.setBlockAndUpdate(emitter.east(), Blocks.LEVER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LeverBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(net.minecraft.world.level.block.LeverBlock.POWERED, false));
        player.getInventory().setItem(0, new ItemStack(ModItems.RELAY_EMITTER.get(), 4));
        player.getInventory().setItem(1, new ItemStack(ModItems.RELAY.get(), 8));
    }

    /** Waar de twee kwartsmuurtjes van hoofdstuk 44 staan (zie "reinforce"): links verstevigd, rechts gewoon. */
    private static final BlockPos REINFORCE = new BlockPos(140, -60, 148);

    /**
     * Twee muurtjes van 3 x 3 kwarts, vijf blokken uit elkaar; het linker helemaal verstevigd met de Block Reinforcer
     * (via sluipen + klik, zoals jij het doet), de Block Reinforcer in je hand en platen in je rugzak.
     */
    private static void buildReinforceCorner(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (BlockPos base : new BlockPos[] {REINFORCE, REINFORCE.east(6)}) {
            for (int x = 0; x < 3; x++) {
                for (int y = 0; y < 3; y++) {
                    level.setBlockAndUpdate(base.offset(x, y, 0), Blocks.QUARTZ_BLOCK.defaultBlockState());
                }
            }
        }
        player.getInventory().setItem(9, new net.minecraft.world.item.ItemStack(ModItems.REINFORCEMENT_PLATE.get(), 16));
        ShowcaseFeatures.useReinforcer(player, REINFORCE.offset(1, 1, 0), true);
    }

    /** Hoofdstuk 51: twee torens en een bunker uit kamers (zie "rooms"), heel ver weg (ze zijn groot). */
    private static final BlockPos ROOMS_YARD = new BlockPos(-700, -60, -700);
    /** Hoofdstuk 54: de generatorkamer van de toren, alleen (zie "powerroom"). */
    private static final BlockPos POWER_YARD = new BlockPos(-900, -60, -700);

    /**
     * Twee willekeurige torens uit kamers naast elkaar (op base en 60 blokken oostelijker), en 250 blokken zuidelijker een
     * willekeurige bunker uit zalen met de ingang naar het noorden. De indelingen komen in het logboek.
     */
    static void buildRoomsYard(ServerPlayer player, BlockPos base) {
        ServerLevel level = player.serverLevel();
        java.util.Random random = new java.util.Random(51);
        java.util.List<String> left = com.senne.imttech.structures.RoomPlacer.placeTower(level, base, net.minecraft.world.level.block.Rotation.NONE, random);
        java.util.List<String> right = com.senne.imttech.structures.RoomPlacer.placeTower(level, base.east(60), net.minecraft.world.level.block.Rotation.NONE, random);
        java.util.List<String> bunker = com.senne.imttech.structures.RoomPlacer.placeBunker(level, base.offset(17, 0, 250),
                net.minecraft.world.level.block.Rotation.NONE, random);
        com.senne.imttech.ImtTech.LOGGER.info("[rooms] toren links: {}; toren rechts: {}; bunker: {}", left, right, bunker);
    }

    /** Hoofdstuk 49: het veldje van de autorisatiecodes (zie "auth"). */
    static final BlockPos AUTH_YARD = new BlockPos(-400, -60, -340);
    /** Het IP van de kern van dat veldje (de server zet het, het scherm van de telefoon typt het; hoofdstuk 50). */
    static volatile String authYardIp = "";

    /** Het diagram op de server van het veldje: auth.valid op het paneel, request.accepted op de console. */
    static final String AUTH_PROGRAM = """
            when codepanel("Vault"):
                if auth.valid:
                    accept()
                    say("Vault open for " + player.name)
                else:
                    deny()
                    say("Wrong code at the vault")

            when request("Gate"):
                if request.accepted:
                    say(player.name + " may come in")
                else:
                    say(player.name + " stays outside")
            """;

    /**
     * Een kern (van jou, met wachtwoord, naar het zuiden) met een serverrek ertegen, elk op een generator. Vijf blokken
     * zuidelijker een muurtje met daaraan (naar het noorden, naar jou) een codepaneel "Vault" en een Auth Console "Gate",
     * elk met een generator eronder; Bram staat rechts ervan. Op de server het diagram "Entrance" (zie AUTH_PROGRAM); de
     * autorisatiecode: zes cijfers, elke vijf minuten, de vorige mag niet.
     */
    static void buildAuthYard(ServerPlayer player, BlockPos base) {
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-3, 0, -2), base.offset(7, 3, 8))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        BlockPos rack = base.west();
        BlockPos panel = base.offset(1, 0, 4);
        BlockPos console = base.offset(3, 0, 4);
        for (BlockPos generator : new BlockPos[] {base.below(), rack.below(), panel.below(), console.below()}) {
            level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                charger.fill(300_000);
            }
        }
        level.setBlockAndUpdate(base, ModBlocks.SECURITY_CORE.get().defaultBlockState()
                .setValue(com.senne.imttech.security.SecurityCoreBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(rack, ModBlocks.SERVER_RACK.get().defaultBlockState()
                .setValue(com.senne.imttech.logic.ServerRackBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(rack) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
            be.insert(new ItemStack(ModItems.SERVER_CPU.get()));
            be.insert(new ItemStack(ModItems.SERVER_RAM.get()));
            be.insert(new ItemStack(ModItems.SERVER_SSD.get()));
            be.insert(new ItemStack(ModItems.SERVER_FAN.get()));
        }
        // Het muurtje, met het paneel en de console ervoor (naar het noorden: naar jou).
        for (int x = 0; x <= 4; x++) {
            for (int dy = 0; dy <= 1; dy++) {
                level.setBlockAndUpdate(base.offset(x, dy, 5), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        placeInputNorth(level, player, panel, ModBlocks.CODE_PANEL.get().defaultBlockState(), "Vault");
        placeInputNorth(level, player, console, ModBlocks.AUTH_CONSOLE.get().defaultBlockState(), "Gate");
        ServerPlayer bram = DevWorld.fakePlayerAt(level, "Bram", base.offset(5, 0, 3));
        bram.teleportTo(level, base.getX() + 5.5, base.getY(), base.getZ() + 3.5, 90, 0);
        if (level.getBlockEntity(base) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
            core.setOwner(player);
            core.ensureSystem(level);
            core.setPassword("bitech1", level.random);
            com.senne.imttech.logic.program.LogicGraph graph = com.senne.imttech.logic.program.GraphConverter.toGraph(
                    com.senne.imttech.logic.program.ScriptParser.parse(AUTH_PROGRAM), null);
            core.logic().put(null, new com.senne.imttech.logic.program.LogicProgram("Entrance", true, graph, AUTH_PROGRAM));
            core.setAuthSettings(6, 5, false);
            core.authTick(level, level.getGameTime());
            authYardIp = core.ip();
        }
        com.senne.imttech.logic.AuthRequests.clear();
    }

    /** Hoofdstuk 50: een nieuwe (nog niet verbonden) I.M.T Phone in je hand; de telefoons van een vorige keer vergeet de kern. */
    static void givePhone(ServerPlayer player, BlockPos base) {
        ServerLevel level = player.serverLevel();
        if (level.getBlockEntity(base) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
            for (com.senne.imttech.security.SecurityCoreBlockEntity.Phone phone : core.phones()) {
                core.forgetPhone(phone.id());
            }
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.PHONE.get()));
    }

    /** Iets doen met het inlogscherm (van de laptop of de telefoon) als dat open staat. */
    private static void loginTour(java.util.function.Consumer<com.senne.imttech.client.os.OsLoginScreen> action) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.OsLoginScreen login) {
            action.accept(login);
        }
    }

    /** Een lezer, paneel of console dat aan de zuidkant van zijn blok hangt en naar het noorden kijkt, met een naam. */
    private static void placeInputNorth(ServerLevel level, ServerPlayer player, BlockPos pos,
                                        net.minecraft.world.level.block.state.BlockState state, String name) {
        level.setBlockAndUpdate(pos, state.setValue(com.senne.imttech.logic.InputDeviceBlock.FACING, Direction.NORTH));
        if (level.getBlockEntity(pos) instanceof com.senne.imttech.logic.InputDeviceBlockEntity device) {
            device.setOwner(player);
            device.setName(name);
        }
    }

    /** Het serverrek van het veldje meteen laten meedraaien (anders duurt dat tot twee seconden). */
    static void wakeAuthServer(ServerPlayer player, BlockPos base) {
        ServerLevel level = player.serverLevel();
        BlockPos rack = base.west();
        if (level.getBlockEntity(rack) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
            be.update(level, rack, level.getBlockState(rack));
        }
        com.senne.imttech.logic.LogicServer.forgetAll();
    }

    /** Bram (de nep-speler) drukt op de Auth Console op {@code console}. */
    static void bramAsks(ServerPlayer player, BlockPos console) {
        ServerPlayer bram = player.serverLevel().getServer().getPlayerList().getPlayerByName("Bram");
        if (bram != null) {
            com.senne.imttech.logic.AuthRequests.ask(player.serverLevel(), console, bram);
        }
    }

    /** Iets doen met de desktop als die open staat. */
    private static void osTour(java.util.function.Consumer<com.senne.imttech.client.os.SecurityOsScreen> action) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
            action.accept(os);
        }
    }

    /** Waar het veldje van de handboeien (hoofdstuk 48) ligt (zie "cuffs"). */
    private static final BlockPos CUFF_YARD = new BlockPos(-400, -60, -370);

    /**
     * Het veldje van de handboeien: Bram (een nep-speler) staat twee blokken voor je, een Raven-soldaat die bijna dood is
     * (en stil staat) rechts van hem, en links naast je een guard robot die neerligt. Achter je een cel van ijzeren
     * tralies, open aan de noordkant. Je hebt drie paar Handcuffs in je hand en een Handcuff Key ernaast. Wat er van
     * een vorige keer nog stond (de soldaat, de robot), gaat eerst weg.
     */
    static void buildCuffYard(ServerPlayer player, BlockPos base) {
        ServerLevel level = player.serverLevel();
        net.minecraft.world.phys.AABB area = net.minecraft.world.phys.AABB.encapsulatingFullBlocks(base.offset(-2, -1, -2), base.offset(8, 4, 14));
        for (Entity old : level.getEntities((Entity) null, area, e -> e instanceof com.senne.imttech.raven.RavenSoldierEntity
                || e instanceof com.senne.imttech.robot.GuardRobotEntity)) {
            old.discard();
        }
        for (int x = 0; x <= 4; x++) {
            for (int z = 8; z <= 12; z++) {
                level.setBlockAndUpdate(base.offset(x, -1, z), Blocks.STONE_BRICKS.defaultBlockState());
                boolean wall = x == 0 || x == 4 || z == 12;
                for (int y = 0; y < 3; y++) {
                    level.setBlockAndUpdate(base.offset(x, y, z), wall ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        ServerPlayer bram = DevWorld.fakePlayerAt(level, "Bram", base.offset(2, 0, 4));
        bram.teleportTo(level, base.getX() + 2.5, base.getY(), base.getZ() + 4.5, 180, 0); // naar het noorden: met zijn rug naar jou (hoofdstuk 54)
        bram.setYBodyRot(180);
        bram.setYHeadRot(180);
        bram.yBodyRotO = 180;
        bram.yHeadRotO = 180;
        com.senne.imttech.cuffs.Handcuffs.release(bram, null, false);
        if (DevWorld.spawnStill(level, com.senne.imttech.registry.ModEntities.RAVEN_SOLDIER.get(), base.offset(4, 0, 4), 180)
                instanceof com.senne.imttech.raven.RavenSoldierEntity soldier) {
            soldier.setHealth(1.0f);
        }
        if (DevWorld.spawnStill(level, com.senne.imttech.registry.ModEntities.GUARD_ROBOT.get(), base.offset(0, 0, 6), 90)
                instanceof com.senne.imttech.robot.GuardRobotEntity robot) {
            robot.setParts(com.senne.imttech.robot.RobotParts.DEFAULT);
            robot.setOwner(player);
            robot.setCustomName(net.minecraft.network.chat.Component.literal("Guard 9"));
            robot.setActive(true);
            robot.knockDown(2400);
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ModItems.HANDCUFFS.get(), 3));
        player.getInventory().setItem(1, new ItemStack(ModItems.HANDCUFF_KEY.get()));
    }

    /** Waar de Power Battery van hoofdstuk 47 staat (zie "battery"), ver van de rest. */
    private static final BlockPos BATTERY_YARD = new BlockPos(-400, -60, -400);

    /**
     * Een Power Battery van 5 x 4 x 5 (binnenin 18 Battery Cells) met een raam van Battery Glass van 3 x 2 in de zuidkant en
     * een Battery Port onderaan in de west- en de oostwand. Links (west) een kabel over het gras naar een warmtegenerator
     * met kolen en een zonnepaneel; rechts (oost) een kabel naar een holotafel en een camera op een paal, die stroom
     * uit de batterij halen. De batterij heeft al wat stroom, en de generator is bijna vol, zodat hij meteen laadt.
     */
    static void buildBatteryYard(ServerPlayer player, BlockPos base) {
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : BlockPos.betweenClosed(base, base.offset(4, 3, 4))) {
            boolean wall = pos.getX() == base.getX() || pos.getX() == base.getX() + 4 || pos.getY() == base.getY()
                    || pos.getY() == base.getY() + 3 || pos.getZ() == base.getZ() || pos.getZ() == base.getZ() + 4;
            boolean window = pos.getZ() == base.getZ() + 4 && pos.getX() > base.getX() && pos.getX() < base.getX() + 4
                    && pos.getY() > base.getY() && pos.getY() < base.getY() + 3;
            net.minecraft.world.level.block.Block block = window ? ModBlocks.BATTERY_GLASS.get()
                    : wall ? ModBlocks.BATTERY_CASING.get() : ModBlocks.BATTERY_CELL.get();
            level.setBlockAndUpdate(pos.immutable(), block.defaultBlockState());
        }
        BlockPos west = base.offset(0, 0, 2);
        BlockPos east = base.offset(4, 0, 2);
        level.setBlockAndUpdate(west, ModBlocks.BATTERY_PORT.get().defaultBlockState());
        level.setBlockAndUpdate(east, ModBlocks.BATTERY_PORT.get().defaultBlockState());
        // Links: een kabel naar de warmtegenerator (met kolen) en het zonnepaneel.
        level.setBlockAndUpdate(west.west(), ModBlocks.CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(west.west(2), ModBlocks.CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(west.west(3), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(west.west(3)) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
            generator.setItem(com.senne.imttech.station.ChargerBlockEntity.SLOT_FUEL,
                    new ItemStack(net.minecraft.world.item.Items.COAL, 16));
            generator.fill(45_000);
        }
        level.setBlockAndUpdate(west.west(2).south(), ModBlocks.SOLAR_PANEL.get().defaultBlockState());
        if (level.getBlockEntity(west.west(2).south()) instanceof com.senne.imttech.station.ChargerBlockEntity panel) {
            panel.fill(40_000);
        }
        // Rechts: een kabel naar de holotafel en een camera op een paal.
        level.setBlockAndUpdate(east.east(), ModBlocks.CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(east.east(2), ModBlocks.CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(east.east(3), ModBlocks.HOLO_TABLE.get().defaultBlockState());
        BlockPos pole = east.east(2).south();
        level.setBlockAndUpdate(pole, Blocks.STONE_BRICK_WALL.defaultBlockState());
        level.setBlockAndUpdate(pole.above(), Blocks.STONE_BRICK_WALL.defaultBlockState());
        level.setBlockAndUpdate(pole.above(2), ModBlocks.SECURITY_CAMERA.get().defaultBlockState()
                .setValue(com.senne.imttech.security.CameraBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(com.senne.imttech.security.CameraBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(pole.east(), ModBlocks.CABLE.get().defaultBlockState());
        // De batterij heeft al een derde van haar stroom.
        if (level.getBlockEntity(west) instanceof com.senne.imttech.battery.BatteryPortBlockEntity port) {
            port.revalidate(level);
            port.fill(6_000_000L);
        }
    }

    /** Waar de Crusher van hoofdstuk 11 staat; de Compactor staat twee blokken oostelijker (zie "repair"). */
    static final BlockPos REPAIR = new BlockPos(140, -60, 128);

    /**
     * Een Crusher en een Compactor (voorkant naar het zuiden), elk tegen een volle generator: de Crusher
     * met twaalf gebarsten stenen bakstenen erin en al drie poeders eruit, de Compactor met vijf
     * obsidiaanpoeders erin en al twee blokken obsidiaan eruit. Een gebarsten stenen baksteen op de
     * grond, en in je hotbar gebarsten blokken en poeder van verschillende blokken, en Repair Paste.
     */
    static void buildRepairCorner(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos r = REPAIR;
        net.minecraft.world.item.Item cracked = ModItems.CRACKED_BLOCK.get();
        net.minecraft.world.item.Item powder = ModItems.BLOCK_POWDER.get();
        level.setBlockAndUpdate(r, ModBlocks.CRUSHER.get().defaultBlockState()
                .setValue(com.senne.imttech.repair.CrackMachineBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(r.west(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(r.west()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
            generator.fill(200_000);
        }
        if (level.getBlockEntity(r) instanceof com.senne.imttech.repair.CrackMachineBlockEntity machine) {
            machine.setItem(com.senne.imttech.repair.CrackMachineBlockEntity.SLOT_IN,
                    com.senne.imttech.repair.FromBlockItem.of(cracked, Blocks.STONE_BRICKS, 12));
            machine.setItem(com.senne.imttech.repair.CrackMachineBlockEntity.SLOT_OUT,
                    com.senne.imttech.repair.FromBlockItem.of(powder, Blocks.STONE_BRICKS, 3));
        }
        BlockPos press = r.east(2);
        level.setBlockAndUpdate(press, ModBlocks.COMPACTOR.get().defaultBlockState()
                .setValue(com.senne.imttech.repair.CrackMachineBlock.FACING, Direction.SOUTH));
        level.setBlockAndUpdate(press.east(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(press.east()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
            generator.fill(200_000);
        }
        if (level.getBlockEntity(press) instanceof com.senne.imttech.repair.CrackMachineBlockEntity machine) {
            machine.setItem(com.senne.imttech.repair.CrackMachineBlockEntity.SLOT_IN,
                    com.senne.imttech.repair.FromBlockItem.of(powder, Blocks.OBSIDIAN, 5));
            machine.setItem(com.senne.imttech.repair.CrackMachineBlockEntity.SLOT_OUT, new net.minecraft.world.item.ItemStack(Blocks.OBSIDIAN, 2));
        }
        net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(level,
                r.getX() + 1.5, r.getY() + 0.1, r.getZ() + 2.0,
                com.senne.imttech.repair.FromBlockItem.of(cracked, Blocks.STONE_BRICKS, 1));
        drop.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        drop.setPickUpDelay(32767);
        drop.setUnlimitedLifetime();
        level.addFreshEntity(drop);
        net.minecraft.world.entity.player.Inventory inventory = player.getInventory();
        net.minecraft.world.level.block.Block[] blocks = {Blocks.STONE_BRICKS, Blocks.OBSIDIAN, Blocks.OAK_PLANKS, Blocks.DEEPSLATE_BRICKS};
        for (int i = 0; i < blocks.length; i++) {
            inventory.setItem(i, com.senne.imttech.repair.FromBlockItem.of(cracked, blocks[i], 3 + i));
        }
        net.minecraft.world.level.block.Block[] powders = {Blocks.STONE_BRICKS, Blocks.OBSIDIAN, Blocks.BRICKS, Blocks.OAK_PLANKS};
        for (int i = 0; i < powders.length; i++) {
            inventory.setItem(4 + i, com.senne.imttech.repair.FromBlockItem.of(powder, powders[i], 2 + i));
        }
        inventory.setItem(8, new net.minecraft.world.item.ItemStack(ModItems.REPAIR_PASTE.get(), 16));
    }

    /** De kern van de basis met een zone in de rondleiding (zie buildZoneBase). */
    private static final BlockPos ZONE_CORE = new BlockPos(164, -60, 65);

    /**
     * Een L-vormige basis van stenen bakstenen (10 x 8, vier hoog, zonder dak, met een deuropening)
     * met een torentje van twaalf hoog in de zuidoosthoek. De zone: twee kubussen er precies omheen,
     * van de vloer tot drie boven de muren. De kern staat binnen, op een volle generator.
     */
    private static void buildZoneBase(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        for (int x = 162; x <= 171; x++) {
            for (int z = 62; z <= 69; z++) {
                boolean wall = x == 162 || x == 171 || z == 62 || z == 69;
                for (int y = -60; y <= -57; y++) {
                    boolean door = z == 62 && (x == 165 || x == 166) && y <= -59;
                    level.setBlockAndUpdate(new BlockPos(x, y, z), wall && !door ? bricks : Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int x = 168; x <= 171; x++) {
            for (int z = 70; z <= 73; z++) {
                boolean wall = x == 168 || x == 171 || z == 70 || z == 73;
                for (int y = -60; y <= -49; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), wall ? bricks : Blocks.AIR.defaultBlockState());
                }
            }
        }
        level.setBlockAndUpdate(ZONE_CORE.below(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(ZONE_CORE.below()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
            generator.fill(200_000);
        }
        level.setBlockAndUpdate(ZONE_CORE, ModBlocks.SECURITY_CORE.get().defaultBlockState());
        if (level.getBlockEntity(ZONE_CORE) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
            core.setOwner(player);
            core.ensureSystem(level);
            core.setZoneBoxes(java.util.List.of(
                    com.senne.imttech.security.zone.ZoneBox.of(new BlockPos(162, -61, 62), new BlockPos(171, -54, 69)),
                    com.senne.imttech.security.zone.ZoneBox.of(new BlockPos(168, -61, 70), new BlockPos(171, -46, 73))));
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
                ArsenalServer.setHealth(player, id, (float) (ImtConfig.Drones.HEALTH * 0.6));
            } else if (i % 4 == 3) {
                ArsenalServer.setHealth(player, id, (float) (ImtConfig.Drones.HEALTH * 0.2));
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
                .setValue(com.senne.imttech.security.CameraBlock.FACE, face)
                .setValue(com.senne.imttech.security.CameraBlock.FACING, facing));
        level.setBlockAndUpdate(power, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(power) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
            generator.fill(200_000);
        }
        if (!name.isEmpty() && level.getBlockEntity(pos) instanceof com.senne.imttech.security.CameraBlockEntity camera) {
            camera.setName(name);
        }
    }

    private static void buildLaserGates(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        // Van west (x 30) naar oost (x 36), twee blokken hoog. Ze staan aan zodra ze stroom hebben (hoofdstuk 39).
        for (int y = -60; y <= -59; y++) {
            level.setBlockAndUpdate(new BlockPos(30, y, 20), emitter(Direction.EAST, ShieldColor.BLUE));
            level.setBlockAndUpdate(new BlockPos(36, y, 20), emitter(Direction.WEST, ShieldColor.BLUE));
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
        DevWorld.powerNextTo(level, new BlockPos(43, -60, 17));
        DevWorld.powerNextTo(level, new BlockPos(43, -60, 23));
    }

    /** De onderste zender van de liggende poort (zie de sectie "laser"). */
    private static final BlockPos FLAT_GATE = new BlockPos(30, -60, 28);

    /**
     * Drie zenders boven elkaar (cyaan, omgekeerd en liggend), met een stenen muur aan het eind en een
     * warmtegenerator tegen de onderste. Zonder redstone gaan ze vanzelf aan (omgekeerd).
     */
    private static void buildFlatLaserGate(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        DevWorld.powerNextTo(level, FLAT_GATE.south());
        // Eerst de muur: een zender meet bij het aangaan hoe ver zijn wand reikt, en hij gaat al aan zodra
        // de volgende zender ertegen komt.
        for (int i = 0; i < 3; i++) {
            level.setBlockAndUpdate(FLAT_GATE.above(i).east(6), Blocks.STONE_BRICKS.defaultBlockState());
        }
        for (int i = 0; i < 3; i++) {
            level.setBlockAndUpdate(FLAT_GATE.above(i), emitter(Direction.EAST, ShieldColor.CYAN)
                    .setValue(LaserEmitterBlock.TURNED, true));
        }
    }

    /** De middelste van drie brugzenders in de rand van een gracht (zie de sectie "bridge"). */
    private static final BlockPos BRIDGE_YARD = new BlockPos(-260, -61, -200);

    /**
     * De laserbrug (hoofdstuk 34): een gracht van 6 breed en 3 diep met water onderin, en in de westrand drie
     * brugzenders naast elkaar (in de grond, naar het oosten), van jou. Een warmtegenerator op magma onder de
     * middelste; het redstoneblok (onder de noordelijke) zet de sectie zelf neer.
     */
    private static void buildLaserBridge(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : BlockPos.betweenClosed(BRIDGE_YARD.offset(1, 0, -4), BRIDGE_YARD.offset(6, -1, 4))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(BRIDGE_YARD.offset(1, -2, -4), BRIDGE_YARD.offset(6, -2, 4))) {
            level.setBlockAndUpdate(pos, Blocks.WATER.defaultBlockState());
        }
        for (int dz = -1; dz <= 1; dz++) {
            BlockPos pos = BRIDGE_YARD.offset(0, 0, dz);
            level.setBlockAndUpdate(pos, ModBlocks.LASER_BRIDGE.get().defaultBlockState()
                    .setValue(com.senne.imttech.shield.LaserBridgeBlock.FACING, Direction.EAST)
                    .setValue(com.senne.imttech.shield.LaserBridgeBlock.SWITCHED_ON, false));
            if (level.getBlockEntity(pos) instanceof com.senne.imttech.shield.LaserBridgeBlockEntity bridge) {
                bridge.setOwner(player);
            }
        }
        DevWorld.powerNextTo(level, BRIDGE_YARD.below());
    }

    /** De schietbaan voor de lasergeweren (hoofdstuk 35). */
    private static final BlockPos GUN_RANGE = new BlockPos(-300, -61, -280);

    /**
     * Een schietbaan: een vloer van gladde steen, drie husks op 8 tot 12 blokken (die doen niets) en een muur van
     * stenen erachter.
     */
    private static void buildGunRange(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : BlockPos.betweenClosed(GUN_RANGE.offset(-8, 1, -4), GUN_RANGE.offset(8, 6, 16))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(GUN_RANGE.offset(-8, 0, -4), GUN_RANGE.offset(8, 0, 16))) {
            level.setBlockAndUpdate(pos, Blocks.SMOOTH_STONE.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(GUN_RANGE.offset(-8, 1, 15), GUN_RANGE.offset(8, 5, 15))) {
            level.setBlockAndUpdate(pos, Blocks.STONE_BRICKS.defaultBlockState());
        }
        int[][] spots = {{-3, 9}, {0, 11}, {3, 8}};
        for (int[] spot : spots) {
            DevWorld.spawnStill(level, EntityType.HUSK, GUN_RANGE.offset(spot[0], 1, spot[1]), 180);
        }
    }

    /** De armory (hoofdstuk 67): een muur met Pegboards en Ammo Boxes ervoor. */
    private static final BlockPos ARMORY = new BlockPos(-220, -61, -200);

    /**
     * Een vloer van gladde steen, een muur van stenen bakstenen (4 blokken verder, naar het zuiden) met 5 x 2 Pegboards
     * die naar het noorden kijken, vol wapens en uitrusting, en vier Ammo Boxes op de grond ervoor.
     */
    private static void buildArmory(ServerPlayer player) {
        buildArmoryAt(player.serverLevel(), ARMORY);
    }

    /** Hetzelfde op een andere plek ({@code origin} = de vloer; ook voor de showcase). */
    static void buildArmoryAt(ServerLevel level, BlockPos origin) {
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-6, 1, -6), origin.offset(6, 6, 6))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-6, 0, -6), origin.offset(6, 0, 6))) {
            level.setBlockAndUpdate(pos, Blocks.SMOOTH_STONE.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-4, 1, 4), origin.offset(4, 4, 4))) {
            level.setBlockAndUpdate(pos, Blocks.STONE_BRICKS.defaultBlockState());
        }
        com.senne.imttech.armory.ArmoryDisplay.build(level, origin.offset(2, 2, 3), origin.offset(1, 1, 2));
    }

    /** Het veldje voor de wapens (hoofdstuk 35). */
    private static final BlockPos WEAPON_YARD = new BlockPos(-300, -61, -200);

    /**
     * De wapens (hoofdstuk 35): een vloer van gladde steen met 7 blokken verder een muur, en ertussen een guard robot en
     * een raketturret op stroom (allebei in de bol van een granaat tegen de muur). Een raiddrone kan hier niet: in deze
     * wereld staat het spel op peaceful, en dan verdwijnen monsters.
     */
    private static void buildWeaponYard(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        for (BlockPos pos : BlockPos.betweenClosed(WEAPON_YARD.offset(-6, 1, -6), WEAPON_YARD.offset(10, 6, 6))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(WEAPON_YARD.offset(-6, 0, -6), WEAPON_YARD.offset(10, 0, 6))) {
            level.setBlockAndUpdate(pos, Blocks.SMOOTH_STONE.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(WEAPON_YARD.offset(7, 1, -4), WEAPON_YARD.offset(7, 4, 4))) {
            level.setBlockAndUpdate(pos, Blocks.STONE_BRICKS.defaultBlockState());
        }
        BlockPos turret = WEAPON_YARD.offset(5, 1, 3);
        level.setBlockAndUpdate(turret, ModBlocks.ROCKET_TURRET.get().defaultBlockState());
        DevWorld.powerNextTo(level, turret.below());
        DevWorld.spawnStill(level, com.senne.imttech.registry.ModEntities.GUARD_ROBOT.get(), WEAPON_YARD.offset(5, 1, -2), 90);
    }

    /**
     * Een schuurtje van stenen bakstenen ten zuiden van het wapenveld (7 bij 7, drie hoog binnen, met een dak), met een
     * glazen raam en een open deur in de noordmuur, en binnen een grijze smokebom die meteen gaat roken.
     */
    private static void buildSmokeShed(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos shed = WEAPON_YARD.offset(0, 0, 17);
        for (BlockPos pos : BlockPos.betweenClosed(shed.offset(-6, 1, -9), shed.offset(6, 6, 6))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(shed.offset(-6, 0, -9), shed.offset(6, 0, 6))) {
            level.setBlockAndUpdate(pos, Blocks.SMOOTH_STONE.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(shed.offset(-3, 1, -3), shed.offset(3, 4, 3))) {
            boolean inside = Math.abs(pos.getX() - shed.getX()) < 3 && Math.abs(pos.getZ() - shed.getZ()) < 3
                    && pos.getY() - shed.getY() < 4;
            if (!inside) {
                level.setBlockAndUpdate(pos, Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        level.setBlockAndUpdate(shed.offset(1, 2, -3), Blocks.GLASS.defaultBlockState());
        level.setBlockAndUpdate(shed.offset(2, 2, -3), Blocks.GLASS.defaultBlockState());
        BlockState door = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(net.minecraft.world.level.block.DoorBlock.FACING, Direction.NORTH)
                .setValue(net.minecraft.world.level.block.DoorBlock.OPEN, true);
        level.setBlock(shed.offset(-1, 1, -3), door, 2);
        level.setBlock(shed.offset(-1, 2, -3), door.setValue(net.minecraft.world.level.block.DoorBlock.HALF,
                net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER), 2);
        var smoke = new com.senne.imttech.weapons.RollingGrenade(
                com.senne.imttech.registry.ModEntities.SMOKE_GRENADE.get(), level);
        smoke.setPos(shed.getX() + 0.5, shed.getY() + 1, shed.getZ() + 0.5);
        smoke.arm(5);
        level.addFreshEntity(smoke);
    }

    /** Haalt de smokebommen rond het wapenveld weg (hun rook trekt dan op). */
    private static void removeSmokeGrenades(ServerPlayer player) {
        for (var grenade : player.serverLevel().getEntitiesOfClass(com.senne.imttech.weapons.RollingGrenade.class,
                new net.minecraft.world.phys.AABB(WEAPON_YARD).inflate(40))) {
            grenade.discard();
        }
    }

    /** Kiest een vakje in je hotbar (in je eigen spel: de server bepaalt dat niet). */
    private static void selectSlot(int slot) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.getInventory().selected = slot;
        }
    }

    /** Voor -Ponly=scopef5: de stand van de camera, kijk je door de kijker, en de kijkhoek van de wereld. */
    private static void logScopeCamera(String label) {
        Minecraft mc = Minecraft.getInstance();
        ImtTech.LOGGER.info(String.format(java.util.Locale.ROOT, "[dev] scopef5 %-16s camera=%s scoped=%s fov=%.1f", label,
                mc.options.getCameraType(), com.senne.imttech.client.weapons.GunControl.scoped(1),
                com.senne.imttech.client.weapons.GunControl.worldFov()));
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

    /**
     * De muis in de hoek linksboven, weg van de vakjes (anders staat er een tooltip over het plaatje). Via GLFW
     * (glfwSetCursorPos) komt er geen muisbeweging door, dus rechtstreeks in de MouseHandler, zoals
     * ShowcaseFeatures.openModTab het tabblad kiest.
     */
    private static void parkMouse() {
        Minecraft mc = Minecraft.getInstance();
        try {
            for (String name : new String[] {"xpos", "ypos"}) {
                java.lang.reflect.Field field = net.minecraft.client.MouseHandler.class.getDeclaredField(name);
                field.setAccessible(true);
                field.setDouble(mc.mouseHandler, 2);
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            ImtTech.LOGGER.warn("[dev] kan de muis niet in de hoek zetten", e);
        }
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
        dome.setRadius(Math.round(ImtConfig.Dome.DEFAULT_SIZE));
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

    /** Waar de rondleiding de Biebia's Industries Tower neerzet (ver van de rest, op de vlakte). */
    private static final BlockPos BIEBIA_TOWER = new BlockPos(200, -61, 200);
    /** Waar het wrak van de satelliet komt (het midden, op het gras). */
    private static final BlockPos CRASH_SITE = new BlockPos(-150, -61, 150);

    /** Het wrak van de neergestorte satelliet: neerzetten (op een heuveltje), dan foto's. */
    /** Klikt op de knop /tick (/sec, /min, /hour) in het scherm dat open staat, zoals een speler dat doet. */
    private static void clickPowerUnitButton() {
        if (Minecraft.getInstance().screen == null) {
            return;
        }
        for (var child : Minecraft.getInstance().screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.Button button
                    && button.getMessage().getString().startsWith("/")) {
                button.onPress();
                return;
            }
        }
    }

    /** Klikt op het knopje met de stijl van grote getallen (naast /tick) in het scherm dat open staat (hoofdstuk 66). */
    private static void clickNumberStyleButton() {
        if (Minecraft.getInstance().screen == null) {
            return;
        }
        String label = com.senne.imttech.config.NumberStyle.local().example();
        for (var child : Minecraft.getInstance().screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.Button button && button.getMessage().getString().equals(label)) {
                button.onPress();
                com.senne.imttech.ImtTech.LOGGER.info("[dev] numbers: style {} ({})",
                        com.senne.imttech.config.NumberStyle.local(), com.senne.imttech.config.NumberStyle.local().example());
                return;
            }
        }
        com.senne.imttech.ImtTech.LOGGER.warn("[dev] numbers: no style button '{}' on {}", label, Minecraft.getInstance().screen);
    }

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
     * De Biebia's Industries Tower: neerzetten, dan foto's van buiten en van elke verdieping van binnen. Binnen
     * staan we in de zuidoosthoek (met een wijde blik) en kijken we schuin naar het midden.
     */
    /** Waar de camera met deze naam in de neergezette Biebia's Industries Tower hangt (zie tools/generate_structure.py). */
    private static @org.jetbrains.annotations.Nullable BlockPos towerCamera(String name) {
        int x = BIEBIA_TOWER.getX();
        int y = BIEBIA_TOWER.getY();
        int z = BIEBIA_TOWER.getZ();
        return switch (name) {
            case "Lobby" -> new BlockPos(x + 25, y + 7, z + 14);
            case "Laser hall" -> new BlockPos(x + 20, y + 75, z + 29);
            case "Vault" -> new BlockPos(x + 20, y + 82, z + 10);
            default -> null;
        };
    }

    private static void tower(DevScript t) {
        int x = BIEBIA_TOWER.getX();
        int y = BIEBIA_TOWER.getY();
        int z = BIEBIA_TOWER.getZ();
        // Eerst ernaartoe, zodat dat stuk wereld geladen is, dan pas neerzetten.
        t.view(0, x + 20.5, y + 40, z - 40, 0, 0);
        t.command(60, "place template imttech:biebia_tower " + x + " " + y + " " + z);
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
        // Hoofdstuk 25: de hangar, met Biebia's security drones die rondvliegen (de kern zette ze op de docks).
        t.view(0, x + 20.5, y + 36, z + 22.5, 180, 12);
        t.shot(40, "t25_hangar_biebia_drones");
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

        // Een eind opzij (buiten de andere foto's): een generator ingemetseld in steen, met steen eronder,
        // links, rechts, erboven en erachter, en de voorkant vrij. Zo zie je of de kieren langs zijn kast
        // dwars door de wereld kijken.
        BlockPos inWall = new BlockPos(50, -59, 40);
        for (BlockPos pos : BlockPos.betweenClosed(inWall.offset(-3, -1, -3), inWall.offset(3, 3, 3))) {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(inWall.offset(-1, -1, 0), inWall.offset(1, 1, 1))) {
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        }
        level.setBlockAndUpdate(inWall, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
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
            com.senne.imttech.energy.PowerCables.add(level, pos);
        }
        level.setBlockAndUpdate(new BlockPos(27, -60, -20), ModBlocks.CHARGING_STATION.get().defaultBlockState());
        // De verborgen kabel: over de vloer, en dan stenen erbovenop gebouwd.
        for (int x = 20; x <= 26; x++) {
            BlockPos pos = new BlockPos(x, -60, -17);
            com.senne.imttech.energy.PowerCables.add(level, pos);
            level.setBlockAndUpdate(pos, Blocks.POLISHED_ANDESITE.defaultBlockState());
        }
        com.senne.imttech.energy.PowerCables.add(level, new BlockPos(20, -60, -18));
        com.senne.imttech.energy.PowerCables.add(level, new BlockPos(20, -60, -19));
    }

    /** Hoofdstuk 38: het veldje met de drie soorten kabels (ver weg van de rest, zodat niets erdoorheen loopt). */
    private static final BlockPos NETWORK_YARD = new BlockPos(-340, -60, -340);
    /** Hoofdstuk 38: een kern met een switch, naast het veldje met kabels. */
    private static final BlockPos NETWORK_DESK = new BlockPos(-320, -60, -340);

    /**
     * Bij NETWORK_DESK: een kern (van jou) met een generator eronder, een PoE-kabel naar een Network Switch ("Lobby
     * switch", met een eigen generator), en vanaf de switch een netwerkkabel naar een keycardlezer en een PoE-kabel naar een
     * camera (die krijgt zijn stroom van de kern, door de switch heen). De bovenste poort van de switch staat uit. Achter
     * de kern een laserbrug met een netwerkkabel (en een eigen generator en redstone, zodat hij er ligt).
     */
    /** Hoofdstuk 40: de receptie met computers (zie "computers" hierboven). */
    static final BlockPos COMPUTER_DESK = new BlockPos(-300, -60, -340);
    /** Waar de schermmuur staat (19 oktober, zie {@link #buildCameraWall}). */
    private static final BlockPos CAMERA_WALL = new BlockPos(-300, -60, -390);

    /**
     * Een kamer met een schermmuur (19 oktober): een stenen muur met 3 x 2 Camera Monitors, een PoE-kabel van de kern naar het
     * scherm (netwerk en stroom), en langs die kabel drie camera's die elk iets anders zien (rode wol en een koe, blauwe wol
     * en een husk, gele wol), plus een robot met een Camera-module. De camera's heten Lobby, Vault en Hall, de robot Guard 9.
     */
    static void buildCameraWall(ServerPlayer player, BlockPos core) {
        ServerLevel level = player.serverLevel();
        for (com.senne.imttech.robot.GuardRobotEntity old : level.getEntitiesOfClass(com.senne.imttech.robot.GuardRobotEntity.class,
                new net.minecraft.world.phys.AABB(core).inflate(16))) {
            old.discard();
        }
        for (int x = -9; x <= 9; x++) {
            for (int z = -8; z <= 8; z++) {
                level.setBlockAndUpdate(core.offset(x, -1, z), Blocks.POLISHED_ANDESITE.defaultBlockState());
                for (int y = 0; y <= 4; y++) {
                    level.setBlockAndUpdate(core.offset(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (BlockPos generator : new BlockPos[] {core.below(), core.west().below()}) {
            level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                charger.fill(200_000);
            }
        }
        level.setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState()
                .setValue(com.senne.imttech.security.SecurityCoreBlock.FACING, Direction.SOUTH));
        if (!(level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity coreEntity)) {
            return;
        }
        coreEntity.setOwner(player);
        coreEntity.ensureSystem(level);
        coreEntity.powerTick(level);
        // Het serverrek tegen de kern: daardoor praat de robot met de server (en staat hij bij de camera's).
        BlockPos rack = core.west();
        level.setBlockAndUpdate(rack, ModBlocks.SERVER_RACK.get().defaultBlockState()
                .setValue(com.senne.imttech.logic.ServerRackBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(rack) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
            be.insert(new ItemStack(ModItems.SERVER_CPU.get()));
            be.insert(new ItemStack(ModItems.SERVER_RAM.get()));
            be.insert(new ItemStack(ModItems.SERVER_SSD.get()));
            be.insert(new ItemStack(ModItems.SERVER_FAN.get()));
        }
        // De stenen muur, met het scherm ervoor (het kijkt naar het zuiden, de kamer in).
        for (int x = -5; x <= 5; x++) {
            for (int y = 0; y <= 4; y++) {
                level.setBlockAndUpdate(core.offset(x, y, -7), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = 1; y <= 2; y++) {
                level.setBlockAndUpdate(core.offset(x, y, -6), ModBlocks.CAMERA_MONITOR.get().defaultBlockState()
                        .setValue(com.senne.imttech.security.wall.CameraMonitorBlock.FACING, Direction.SOUTH));
                if (level.getBlockEntity(core.offset(x, y, -6)) instanceof com.senne.imttech.security.wall.CameraMonitorBlockEntity monitor) {
                    monitor.setOwner(player);
                }
            }
        }
        for (int z = 1; z <= 6; z++) {
            com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.POE, level, core.north(z));
        }
        // De drie camera's langs de kabel, en iets om naar te kijken.
        Object[][] cameras = {{core.offset(1, 0, -3), Direction.EAST, "Lobby"}, {core.offset(-1, 0, -3), Direction.WEST, "Vault"},
                {core.offset(1, 0, -1), Direction.SOUTH, "Hall"}};
        for (Object[] row : cameras) {
            BlockPos pos = (BlockPos) row[0];
            level.setBlockAndUpdate(pos, ModBlocks.SECURITY_CAMERA.get().defaultBlockState()
                    .setValue(com.senne.imttech.security.CameraBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                    .setValue(com.senne.imttech.security.CameraBlock.FACING, (Direction) row[1]));
            if (level.getBlockEntity(pos) instanceof com.senne.imttech.security.CameraBlockEntity camera) {
                camera.setName((String) row[2]);
            }
        }
        for (int y = 0; y <= 3; y++) {
            level.setBlockAndUpdate(core.offset(8, y, -3), Blocks.RED_WOOL.defaultBlockState());
            level.setBlockAndUpdate(core.offset(-8, y, -3), Blocks.BLUE_WOOL.defaultBlockState());
            level.setBlockAndUpdate(core.offset(1, y, 7), Blocks.YELLOW_WOOL.defaultBlockState());
        }
        DevWorld.spawnStill(level, EntityType.COW, core.offset(6, 0, -3), 90);
        DevWorld.spawnStill(level, EntityType.HUSK, core.offset(-5, 0, -3), -90);
        if (DevWorld.spawnStill(level, com.senne.imttech.registry.ModEntities.GUARD_ROBOT.get(), core.offset(0, 0, 4), 180)
                instanceof com.senne.imttech.robot.GuardRobotEntity robot) {
            com.senne.imttech.robot.RobotStyle c = com.senne.imttech.robot.RobotStyle.COMMANDO;
            robot.setParts(new com.senne.imttech.robot.RobotParts(c, c, c, c));
            robot.setOwner(player);
            robot.setCustomName(Component.literal("Guard 9"));
            robot.setBoard(com.senne.imttech.bot.BotBoard.EMPTY.with(6, "camera"));
            robot.setActive(true);
        }
    }

    /** Het scherm en de camera's meteen laten kijken of ze werken (anders duurt dat tot twee seconden). */
    static void wakeCameraWall(ServerPlayer player, BlockPos core) {
        ServerLevel level = player.serverLevel();
        com.senne.imttech.logic.LogicServer.forgetAll();
        if (level.getBlockEntity(core.west()) instanceof com.senne.imttech.logic.ServerRackBlockEntity rack) {
            rack.update(level, core.west(), level.getBlockState(core.west()));
        }
        com.senne.imttech.logic.LogicServer.forgetAll();
        // De robot meldt zich anders pas binnen een seconde bij zijn server (dan staat hij nog niet tussen de camera's).
        for (com.senne.imttech.robot.GuardRobotEntity robot : level.getEntitiesOfClass(com.senne.imttech.robot.GuardRobotEntity.class,
                new net.minecraft.world.phys.AABB(core).inflate(16))) {
            com.senne.imttech.bot.BotNetwork.get(level).update(robot);
        }
        if (level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity coreEntity) {
            com.senne.imttech.security.SecurityDevices.forget(level, coreEntity);
            for (BlockPos pos : com.senne.imttech.security.SecurityDevices.scan(level, coreEntity).cameras()) {
                if (level.getBlockEntity(pos) instanceof com.senne.imttech.security.CameraBlockEntity camera) {
                    camera.updateOnline(level, pos, level.getBlockState(pos));
                }
            }
        }
        for (int x = -1; x <= 1; x++) {
            for (int y = 1; y <= 2; y++) {
                if (level.getBlockEntity(core.offset(x, y, -6)) instanceof com.senne.imttech.security.wall.CameraMonitorBlockEntity monitor) {
                    monitor.updateOn(level);
                }
            }
        }
    }

    /** Sleept de camera met deze naam op het instelscherm van de schermmuur (als dat open staat). */
    static void dragCamera(String name, int fromColumn, int fromRow, int toColumn, int toRow) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.CameraWallScreen wall) {
            if (wall.tourSelect(name)) {
                wall.tourDrag(fromColumn, fromRow, toColumn, toRow);
            }
        }
    }

    /** Laat het vak los op het instelscherm van de schermmuur. */
    static void dropCamera() {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.CameraWallScreen wall) {
            wall.tourDrop();
        }
    }
    /** Het IP van de kern van de receptie (de server zet het, het inlogscherm van de laptop typt het; ook de showcase). */
    static String computerDeskIp = "";

    /**
     * Een kern (wachtwoord "recept1") met een netwerkkabel naar twee computers met elk een monitor: "Reception" (voor
     * iedereen; Status, Cameras, Devices en Logs om te kijken) en "Office" (voor vertrouwde spelers, met een code, Keycards
     * en Logs). Een keycardlezer "Front door" en een camera "Lobby" aan hetzelfde netwerk, en een paar regels in het
     * logboek van wie er binnenkwam. De showcase (functie 103 en 104) bouwt hem ook, op een andere plek.
     */
    static void buildComputerDesk(ServerPlayer player, BlockPos core) {
        ServerLevel level = player.serverLevel();
        BlockPos reception = core.east(4);
        BlockPos office = core.east(2).south();
        BlockPos reader = core.west(2);
        BlockPos camera = core.north(2);
        for (BlockPos generator : new BlockPos[] {core.below(), reception.below(), office.below(), reader.below()}) {
            level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                charger.fill(200_000);
            }
        }
        level.setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState()
                .setValue(com.senne.imttech.security.SecurityCoreBlock.FACING, Direction.SOUTH));
        if (!(level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity coreEntity)) {
            return;
        }
        coreEntity.setOwner(player);
        coreEntity.ensureSystem(level);
        coreEntity.setPassword("recept1", level.random);
        coreEntity.powerTick(level);
        computerDeskIp = coreEntity.ip();
        for (int dx = 1; dx <= 3; dx++) {
            com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.NETWORK, level, core.east(dx));
        }
        com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.NETWORK, level, core.west());
        com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.POE, level, core.north());
        level.setBlockAndUpdate(reader, ModBlocks.KEYCARD_READER.get().defaultBlockState()
                .setValue(com.senne.imttech.logic.InputDeviceBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(reader) instanceof com.senne.imttech.logic.InputDeviceBlockEntity be) {
            be.setName("Front door");
        }
        level.setBlockAndUpdate(camera, ModBlocks.SECURITY_CAMERA.get().defaultBlockState()
                .setValue(com.senne.imttech.security.CameraBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(com.senne.imttech.security.CameraBlock.FACING, Direction.SOUTH));
        // De twee computers, elk met een monitor ernaast (naar het zuiden, naar de camera van de screenshot).
        Object[][] computers = {
                {reception, "Reception", com.senne.imttech.computer.ComputerBlockEntity.Who.EVERYONE,
                        new String[] {"STATUS", "CAMERAS", "DEVICES", "LOGS"}, ""},
                {office, "Office", com.senne.imttech.computer.ComputerBlockEntity.Who.TRUSTED,
                        new String[] {"KEYCARDS", "LOGS"}, "desk42"}};
        for (Object[] row : computers) {
            BlockPos pos = (BlockPos) row[0];
            level.setBlockAndUpdate(pos, ModBlocks.COMPUTER.get().defaultBlockState()
                    .setValue(com.senne.imttech.computer.ComputerBlock.FACING, Direction.SOUTH));
            level.setBlockAndUpdate(pos.east(), ModBlocks.MONITOR.get().defaultBlockState()
                    .setValue(com.senne.imttech.computer.MonitorBlock.FACING, Direction.SOUTH));
            if (level.getBlockEntity(pos) instanceof com.senne.imttech.computer.ComputerBlockEntity be) {
                be.setOwner(player);
                be.setName((String) row[1]);
                be.setWho((com.senne.imttech.computer.ComputerBlockEntity.Who) row[2], "");
                com.senne.imttech.security.os.DesktopRights rights = be.rights();
                for (String app : (String[]) row[3]) {
                    rights = rights.with(app, app.equals("KEYCARDS") ? com.senne.imttech.security.os.DesktopRights.Access.USE
                            : com.senne.imttech.security.os.DesktopRights.Access.VIEW);
                }
                be.setRights(rights);
                be.setCode((String) row[4], level.random);
            }
        }
        com.senne.imttech.security.SecurityDevices.forget(level, coreEntity);
        for (BlockPos pos : new BlockPos[] {reception, office}) {
            if (level.getBlockEntity(pos) instanceof com.senne.imttech.computer.ComputerBlockEntity be) {
                be.updateOn(level);
            }
        }
        // Wie er vandaag binnenkwam (de app Logs, Show: entries), tussen een paar andere regels.
        coreEntity.logEvent(com.senne.imttech.security.os.EventLog.Type.ENTRY, "Lotte used keycard #1001 at Front door");
        coreEntity.logEvent(com.senne.imttech.security.os.EventLog.Type.DEVICE, "Senne set Cameras on Reception to view");
        coreEntity.logEvent(com.senne.imttech.security.os.EventLog.Type.ENTRY, "Senne entered a code at Front door");
        coreEntity.logEvent(com.senne.imttech.security.os.EventLog.Type.LOGIN, "Lotte logged in at Reception");
        coreEntity.logEvent(com.senne.imttech.security.os.EventLog.Type.ENTRY, "Jonas used keycard #1002 at Front door");
    }

    private static void buildNetworkDesk(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos core = NETWORK_DESK;
        BlockPos networkSwitch = core.east(3);
        BlockPos reader = networkSwitch.east(3);
        BlockPos camera = networkSwitch.north(3);
        for (BlockPos generator : new BlockPos[] {core.below(), networkSwitch.below(), reader.below()}) {
            level.setBlockAndUpdate(generator, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(generator) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
                charger.fill(200_000);
            }
        }
        level.setBlockAndUpdate(core, ModBlocks.SECURITY_CORE.get().defaultBlockState()
                .setValue(com.senne.imttech.security.SecurityCoreBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(core) instanceof com.senne.imttech.security.SecurityCoreBlockEntity be) {
            be.setOwner(player);
            be.ensureSystem(level);
            be.setPassword("bitech1", level.random);
        }
        for (int dx = 1; dx <= 2; dx++) {
            com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.POE, level, core.east(dx));
        }
        level.setBlockAndUpdate(networkSwitch, ModBlocks.NETWORK_SWITCH.get().defaultBlockState()
                .setValue(com.senne.imttech.lan.NetworkSwitchBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(networkSwitch) instanceof com.senne.imttech.lan.NetworkSwitchBlockEntity be) {
            be.setName("Lobby switch");
            be.updateOnline(level);
        }
        com.senne.imttech.lan.NetworkSwitches.setPort(level, networkSwitch, Direction.UP, false);
        for (int dx = 1; dx <= 2; dx++) {
            com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.NETWORK, level, networkSwitch.east(dx));
        }
        level.setBlockAndUpdate(reader, ModBlocks.KEYCARD_READER.get().defaultBlockState()
                .setValue(com.senne.imttech.logic.InputDeviceBlock.FACING, Direction.SOUTH));
        if (level.getBlockEntity(reader) instanceof com.senne.imttech.logic.InputDeviceBlockEntity be) {
            be.setName("Lobby door");
        }
        for (int dz = 1; dz <= 2; dz++) {
            com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.POE, level, networkSwitch.north(dz));
        }
        level.setBlockAndUpdate(camera, ModBlocks.SECURITY_CAMERA.get().defaultBlockState()
                .setValue(com.senne.imttech.security.CameraBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.FLOOR)
                .setValue(com.senne.imttech.security.CameraBlock.FACING, Direction.SOUTH));
        // Achter de kern een laserbrug (stap 7) met een netwerkkabel ernaartoe: die staat in Devices bij LASER BRIDGES. Zijn
        // stroom komt over een stroomkabel van een generator verderop (stap 9: daar zie je de gele streepjes lopen).
        BlockPos bridge = core.north(2);
        com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.NETWORK, level, core.north());
        level.setBlockAndUpdate(core.north(5), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(core.north(5)) instanceof com.senne.imttech.station.ChargerBlockEntity charger) {
            charger.fill(200_000);
        }
        com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.POWER, level, core.north(3));
        com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.POWER, level, core.north(4));
        level.setBlockAndUpdate(bridge, ModBlocks.LASER_BRIDGE.get().defaultBlockState()
                .setValue(com.senne.imttech.shield.LaserBridgeBlock.FACING, Direction.WEST));
        if (level.getBlockEntity(bridge) instanceof com.senne.imttech.shield.LaserBridgeBlockEntity be) {
            be.setOwner(player);
        }
        // Links van de kern (stap 8): PoE-kabels naar een muurtje met een netwerkknop en een netwerkhendel (die staat aan),
        // en op de vloer nog een hendel en een knop (die is net ingedrukt).
        for (BlockPos poe : new BlockPos[] {core.west(), core.west().above(), core.west().south()}) {
            com.senne.imttech.energy.Cables.add(com.senne.imttech.energy.CableKind.POE, level, poe);
        }
        level.setBlockAndUpdate(core.west(3), Blocks.STONE_BRICKS.defaultBlockState());
        level.setBlockAndUpdate(core.west(3).above(), Blocks.STONE_BRICKS.defaultBlockState());
        placeControl(level, player, core.west(2), ModBlocks.NETWORK_BUTTON.get().defaultBlockState(), AttachFace.WALL,
                Direction.EAST, "Front bell");
        placeControl(level, player, core.west(2).above(), ModBlocks.NETWORK_LEVER.get().defaultBlockState()
                .setValue(com.senne.imttech.logic.NetworkLeverBlock.ON, true), AttachFace.WALL, Direction.EAST, "Night mode");
        placeControl(level, player, core.west(2).south(), ModBlocks.NETWORK_LEVER.get().defaultBlockState(), AttachFace.FLOOR,
                Direction.EAST, "Alarm test");
        placeControl(level, player, core.west().south(2), ModBlocks.NETWORK_BUTTON.get().defaultBlockState()
                .setValue(com.senne.imttech.logic.NetworkButtonBlock.PRESSED, true), AttachFace.FLOOR, Direction.SOUTH, "Desk button");
    }

    /** Een netwerkknop of -hendel (stap 8) van jou, met deze naam, op de muur, de vloer of het plafond. */
    private static void placeControl(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, AttachFace face,
                                     Direction facing, String name) {
        level.setBlockAndUpdate(pos, state.setValue(com.senne.imttech.logic.NetworkControlBlock.FACE, face)
                .setValue(com.senne.imttech.logic.InputDeviceBlock.FACING, facing));
        if (level.getBlockEntity(pos) instanceof com.senne.imttech.logic.InputDeviceBlockEntity be) {
            be.setOwner(player);
            be.setName(name);
        }
    }

    /**
     * Bij NETWORK_YARD: drie kabels naast elkaar over de vloer en een muurtje op (stroom, netwerk, PoE), dan alle drie op
     * dezelfde plekken (die liggen boven elkaar), en verder naar het zuiden dezelfde drie verborgen onder een strook
     * gepolijst andesiet, en nog een strook met alle drie samen erin.
     */
    private static void buildNetworkYard(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos y = NETWORK_YARD;
        com.senne.imttech.energy.CableKind[] kinds = com.senne.imttech.energy.CableKind.values();
        // Het muurtje van drie hoog aan de oostkant.
        for (int dy = 0; dy <= 2; dy++) {
            for (int dz = -1; dz <= 8; dz++) {
                level.setBlockAndUpdate(y.offset(6, dy, dz), Blocks.STONE_BRICKS.defaultBlockState());
            }
        }
        // Drie kabels naast elkaar, over de vloer en tegen het muurtje op.
        for (int i = 0; i < kinds.length; i++) {
            for (int dx = 0; dx <= 5; dx++) {
                com.senne.imttech.energy.Cables.add(kinds[i], level, y.offset(dx, 0, i * 2));
            }
            for (int dy = 1; dy <= 2; dy++) {
                com.senne.imttech.energy.Cables.add(kinds[i], level, y.offset(5, dy, i * 2));
            }
        }
        // Alle drie op dezelfde plekken: boven elkaar (de banen). Het eerste stuk alleen netwerk en PoE.
        for (int dx = 0; dx <= 5; dx++) {
            for (com.senne.imttech.energy.CableKind kind : kinds) {
                if (dx >= 2 || kind != com.senne.imttech.energy.CableKind.POWER) {
                    com.senne.imttech.energy.Cables.add(kind, level, y.offset(dx, 0, 7));
                }
            }
        }
        for (int dy = 1; dy <= 2; dy++) {
            for (com.senne.imttech.energy.CableKind kind : kinds) {
                com.senne.imttech.energy.Cables.add(kind, level, y.offset(5, dy, 7));
            }
        }
        // Verborgen onder stroken steen: elke soort apart (een blok uit elkaar, anders zit een PoE-kabel vast aan de
        // netwerkkabel ernaast), en alle drie samen.
        for (int dx = 0; dx <= 5; dx++) {
            for (int i = 0; i < kinds.length; i++) {
                BlockPos pos = y.offset(dx, 0, 11 + i * 2);
                com.senne.imttech.energy.Cables.add(kinds[i], level, pos);
                level.setBlockAndUpdate(pos, Blocks.POLISHED_ANDESITE.defaultBlockState());
            }
            BlockPos together = y.offset(dx, 0, 17);
            for (com.senne.imttech.energy.CableKind kind : kinds) {
                com.senne.imttech.energy.Cables.add(kind, level, together);
            }
            level.setBlockAndUpdate(together, Blocks.POLISHED_ANDESITE.defaultBlockState());
        }
    }

    /** Een guard robot die aan staat, stil, en naar het zuiden kijkt (naar de camera). */
    private static void robotStill(ServerLevel level, BlockPos pos, com.senne.imttech.robot.RobotParts parts) {
        if (DevWorld.spawnStill(level, com.senne.imttech.registry.ModEntities.GUARD_ROBOT.get(), pos, 0)
                instanceof com.senne.imttech.robot.GuardRobotEntity robot) {
            robot.setParts(parts);
            robot.setActive(true);
        }
    }

    /** Onthoudt waar de speler nu staat (voor wheelWalkReport). */
    private static void wheelWalkMark(double[] mark) {
        var player = Minecraft.getInstance().player;
        if (player != null) {
            mark[0] = player.getX();
            mark[1] = player.getZ();
        }
    }

    /** Schrijft in de log hoe ver de speler sinds de vorige meting liep, en of het wiel en de W-toets "aan" staan. */
    private static void wheelWalkReport(String label, double[] mark) {
        Minecraft mc = Minecraft.getInstance();
        var player = mc.player;
        if (player == null) {
            return;
        }
        double moved = Math.hypot(player.getX() - mark[0], player.getZ() - mark[1]);
        mark[2] = moved;
        long window = mc.getWindow().getWindow();
        ImtTech.LOGGER.info(String.format(java.util.Locale.ROOT,
                "[dev] wheelwalk %-34s %.2f blokken, wiel open=%s, W aan=%s (GLFW: %s), Alt (GLFW): %s, v=%.2f",
                label, moved, com.senne.imttech.client.guns.GearWheelScreen.isOpen(), mc.options.keyUp.isDown(),
                com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_W),
                com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT),
                player.getDeltaMovement().horizontalDistance()));
        wheelWalkMark(mark);
    }

    /** Hoort dit stuk bij de rondleiding van vandaag (zie ONLY)? */
    /** De spelmodus voor het stuk "guards" (hoofdstuk 60): daar ben je even survival, want in creative ziet een bewaker je niet. */
    private static net.minecraft.world.level.GameType GUARD_MODE_BEFORE = net.minecraft.world.level.GameType.CREATIVE;
    /** Wat je op je hoofd en in je hand had voor het stuk "guards" (daar ben je zonder bril, en met een zwaard). */
    private static ItemStack GUARD_HEAD_BEFORE = ItemStack.EMPTY;
    private static ItemStack GUARD_HAND_BEFORE = ItemStack.EMPTY;
    /** Hoe lang een sluipaanval duurde voor de rondleiding hem vertraagde (zie het stuk "guards"). */
    private static float GUARD_TAKEDOWN_BEFORE = 2.0f;

    /** 8 november: je grijpt de dichtstbijzijnde bewaker van achteren met je zwaard (een gewone klik: Takedown). */
    static void takeDownNearestGuard(ServerPlayer player) {
        com.senne.imttech.raven.RavenSoldierEntity nearest = null;
        for (com.senne.imttech.raven.RavenSoldierEntity soldier : player.serverLevel().getEntitiesOfClass(
                com.senne.imttech.raven.RavenSoldierEntity.class, player.getBoundingBox().inflate(6), Entity::isAlive)) {
            if (nearest == null || soldier.distanceToSqr(player) < nearest.distanceToSqr(player)) {
                nearest = soldier;
            }
        }
        if (nearest != null) {
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
            player.attack(nearest);
        }
    }

    /** Hoofdstuk 60: een bewaker met zijn rug naar het zuiden (naar jou), die een route van tien blokken breed loopt. */
    static void spawnPatrollingGuard(ServerLevel level, BlockPos post) {
        for (Entity old : level.getEntities((Entity) null, new net.minecraft.world.phys.AABB(post).inflate(16),
                e -> e instanceof com.senne.imttech.raven.RavenSoldierEntity || e instanceof com.senne.imttech.raven.RavenBodyEntity)) {
            old.discard();
        }
        com.senne.imttech.raven.RavenSoldierEntity soldier = com.senne.imttech.registry.ModEntities.RAVEN_SOLDIER.get().create(level);
        if (soldier == null) {
            return;
        }
        soldier.moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, 180, 0);
        soldier.finalizeSpawn(level, level.getCurrentDifficultyAt(post), net.minecraft.world.entity.MobSpawnType.SPAWN_EGG, null);
        soldier.setPersistenceRequired();
        soldier.setRoute(java.util.List.of(post.west(5), post.east(5)));
        level.addFreshEntity(soldier);
    }

    /** Hoe alert de dichtstbijzijnde bewaker is, zoals je spel het ziet (voor waitUntil). */
    private static com.senne.imttech.raven.RavenAlert guardAlert() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return com.senne.imttech.raven.RavenAlert.CALM;
        }
        com.senne.imttech.raven.RavenSoldierEntity nearest = null;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof com.senne.imttech.raven.RavenSoldierEntity soldier
                    && (nearest == null || soldier.distanceToSqr(mc.player) < nearest.distanceToSqr(mc.player))) {
                nearest = soldier;
            }
        }
        return nearest == null ? com.senne.imttech.raven.RavenAlert.CALM : nearest.alertState();
    }

    /** De dichtstbijzijnde bewaker valt: zijn lichaam blijft liggen (hoofdstuk 60, stap 5). */
    static void dropNearestGuard(ServerPlayer player) {
        com.senne.imttech.raven.RavenSoldierEntity nearest = null;
        for (com.senne.imttech.raven.RavenSoldierEntity soldier : player.serverLevel().getEntitiesOfClass(
                com.senne.imttech.raven.RavenSoldierEntity.class, player.getBoundingBox().inflate(40), Entity::isAlive)) {
            if (nearest == null || soldier.distanceToSqr(player) < nearest.distanceToSqr(player)) {
                nearest = soldier;
            }
        }
        if (nearest != null) {
            nearest.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 2.0f); // zijn wapen zit dan zeker in het lichaam
            nearest.kill();
        }
        // Het lichaam op een vaste plek voor de camera, dwars (het ligt waar hij viel, maar daar kijkt de rondleiding niet).
        for (com.senne.imttech.raven.RavenBodyEntity body : player.serverLevel().getEntitiesOfClass(
                com.senne.imttech.raven.RavenBodyEntity.class, player.getBoundingBox().inflate(40))) {
            body.moveTo(100.5, -60, 70.5, 90, 0);
            body.setYBodyRot(90);
            body.setYHeadRot(90);
        }
    }

    /** Het dichtstbijzijnde lichaam doorzoeken (het scherm "Search the body"). */
    static void searchNearestBody(ServerPlayer player) {
        for (com.senne.imttech.raven.RavenBodyEntity body : player.serverLevel().getEntitiesOfClass(
                com.senne.imttech.raven.RavenBodyEntity.class, player.getBoundingBox().inflate(40))) {
            body.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
            return;
        }
    }

    private static boolean want(String section) {
        return ONLY.isEmpty() || ONLY.contains(section);
    }

    /** Op de open desktop: de app Monitor met het rack met deze naam gekozen (hoofdstuk 64), of null = de hele server. */
    static void monitorRack(@org.jetbrains.annotations.Nullable String name) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen os) {
            os.tourMonitorRack(name);
        }
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
        BlockPos rocket = new BlockPos(78, -60, 60);
        level.setBlockAndUpdate(rocket, ModBlocks.ROCKET_TURRET.get().defaultBlockState());
        if (level.getBlockEntity(rocket) instanceof com.senne.imttech.turret.TurretBlockEntity turret) {
            turret.setOwner(player);
        }
        level.setBlockAndUpdate(rocket.east(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(rocket.east()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
            generator.fill(100_000);
        }
        BlockPos laser = new BlockPos(83, -60, 60);
        level.setBlockAndUpdate(laser, ModBlocks.SECURITY_TURRET.get().defaultBlockState());
        level.setBlockAndUpdate(laser.north(), ModBlocks.SECURITY_CORE.get().defaultBlockState());
        level.setBlockAndUpdate(laser.north().below(), ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        if (level.getBlockEntity(laser.north().below()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
            generator.fill(100_000);
        }
        BlockPos off = new BlockPos(87, -60, 60);
        level.setBlockAndUpdate(off, ModBlocks.ROCKET_TURRET.get().defaultBlockState());
        level.setBlockAndUpdate(off, level.getBlockState(off).setValue(com.senne.imttech.turret.TurretBlock.ENABLED, false));
    }

    private static void wearSuit(ServerPlayer player) {
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.TECH_CHESTPLATE.get()));
        player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.TECH_LEGGINGS.get()));
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.TECH_BOOTS.get()));
    }

    private static void spawnDrones(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        DevWorld.spawnStill(level, ModEntities.DRONE.get(), new BlockPos(-1, -59, -14), 180);
        DevWorld.spawnStill(level, ModEntities.RAID_DRONE.get(), new BlockPos(2, -59, -14), 180);
    }

    /**
     * Wat ertsen onder de holotafel (de platte wereld heeft er geen): een diamantader, goud,
     * redstone, lapis, een kluit kolen en wat ijzer, op twee en drie blokken diep.
     */
    static void oresUnder(ServerLevel level, BlockPos table) {
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

    static void onTable(DevScript t, Consumer<HoloTableBlockEntity> action) {
        t.onServer(0, player -> {
            if (player.serverLevel().getBlockEntity(TABLE) instanceof HoloTableBlockEntity table) {
                action.accept(table);
            }
        });
    }
}
