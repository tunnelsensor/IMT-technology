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
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.registry.ModItems;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
            t.camera(0, CameraType.FIRST_PERSON);

            // --- De repulsor: aanzetten, dan de aanvalsknop "indrukken" zoals een speler dat doet
            t.view(0, 6.5, -60, -4.5, 180, 8);
            t.step(10, RepulsorControl::toggle);
            t.shot(30, "42_repulsor_armed");
            t.tap(0, () -> Minecraft.getInstance().options.keyAttack);
            t.shot(4, "43_repulsor_fired");
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
            t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
            t.camera(0, CameraType.FIRST_PERSON);
            t.key(0, () -> Minecraft.getInstance().options.keyUp, true);
            t.key(0, () -> Minecraft.getInstance().options.keySprint, true);
            t.probe(10, "boost 10");
            t.probe(10, "boost 20");
            t.probe(10, "boost 30");
            t.probe(10, "boost 40");
            t.shot(0, "44_flight_boost");
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

        // --- Het stroomnet: een warmtegenerator, een kabel met een hoek en een stuk omhoog, en
        // een laadstation. De kabel moet netjes aansluiten op alles wat hij raakt.
        if (want("power")) {
            t.onServer(0, VisualCheck::buildPowerLine);
            t.view(20, 24.5, -58.5, -16.5, 200, 30);
            t.shot(20, "95_power_cables");
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
}

    // ================================================================ De wereld inrichten

    private static void buildScene(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockState stone = Blocks.STONE.defaultBlockState();

        level.setBlockAndUpdate(TABLE, ModBlocks.HOLO_TABLE.get().defaultBlockState());
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
        // Van noord (z 17) naar zuid (z 23), in het rood.
        level.setBlockAndUpdate(new BlockPos(42, -60, 17), emitter(Direction.SOUTH, ShieldColor.RED));
        level.setBlockAndUpdate(new BlockPos(42, -60, 23), emitter(Direction.NORTH, ShieldColor.RED));
        level.setBlockAndUpdate(new BlockPos(42, -60, 16), power);
        level.setBlockAndUpdate(new BlockPos(42, -60, 24), power);
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

    /** Generator - kabels (met een hoek en een stukje omhoog) - laadstation, bij x 20..27, z -20. */
    private static void buildPowerLine(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos start = new BlockPos(20, -60, -20);
        List<BlockPos> path = List.of(start.east(1), start.east(2), start.east(3), start.east(3).south(),
                start.east(3).south(2), start.east(3).south(2).above(), start.east(4).south(2).above(),
                start.east(5).south(2).above());
        level.setBlockAndUpdate(start, ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
        for (BlockPos pos : path) {
            level.setBlockAndUpdate(pos, ModBlocks.CABLE.get().defaultBlockState());
        }
        // Pas na het leggen van alle kabels: dan kent elke kabel zijn buren al.
        for (BlockPos pos : path) {
            BlockState placed = ModBlocks.CABLE.get().getStateForPlacement(
                    new net.minecraft.world.item.context.DirectionalPlaceContext(level, pos, Direction.DOWN, ItemStack.EMPTY, Direction.UP));
            level.setBlockAndUpdate(pos, placed);
        }
        level.setBlockAndUpdate(start.east(6).south(2).above(), ModBlocks.CHARGING_STATION.get().defaultBlockState());
        level.setBlockAndUpdate(start.east(6).south(2), Blocks.STONE.defaultBlockState());
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

    private static void onTable(DevScript t, Consumer<HoloTableBlockEntity> action) {
        t.onServer(0, player -> {
            if (player.serverLevel().getBlockEntity(TABLE) instanceof HoloTableBlockEntity table) {
                action.accept(table);
            }
        });
    }
}
