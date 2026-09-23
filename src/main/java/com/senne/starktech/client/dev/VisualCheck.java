package com.senne.starktech.client.dev;

import com.senne.starktech.StarkTech;
import com.senne.starktech.arsenal.ArsenalBalance;
import com.senne.starktech.arsenal.ArsenalServer;
import com.senne.starktech.client.ArsenalScreen;
import com.senne.starktech.client.CommsScreen;
import com.senne.starktech.client.EdithMenuScreen;
import com.senne.starktech.client.HoloTableScreen;
import com.senne.starktech.client.MusicScreen;
import com.senne.starktech.client.NavigationScreen;
import com.senne.starktech.client.RepulsorControl;
import com.senne.starktech.client.RelayListScreen;
import com.senne.starktech.client.SpyReportScreen;
import com.senne.starktech.client.WaypointEditScreen;
import com.senne.starktech.client.voice.VoiceCommandsScreen;
import com.senne.starktech.edith.EdithData;
import com.senne.starktech.edith.EdithOwner;
import com.senne.starktech.holo.HoloMode;
import com.senne.starktech.holo.HoloTableBlockEntity;
import com.senne.starktech.nav.Waypoint;
import com.senne.starktech.registry.ModAttachments;
import com.senne.starktech.registry.ModBlocks;
import com.senne.starktech.registry.ModEntities;
import com.senne.starktech.registry.ModItems;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

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
    private static final String WORLD = "VisualCheck";

    /** Waar alles staat in de platte wereld (de grond is daar y = -61, je staat op -60). */
    private static final BlockPos TABLE = new BlockPos(0, -60, 0);
    private static final BlockPos HILL = new BlockPos(14, -60, 10);
    private static final BlockPos PIT = new BlockPos(-14, -61, -8);
    private static final BlockPos TOWER = new BlockPos(-10, -60, 14);
    private static final BlockPos ENEMY = new BlockPos(6, -60, -9);
    private static final BlockPos ANIMAL = new BlockPos(-6, -60, 7);

    /** Eén stop op de rondleiding: eerst zoveel ticks wachten, dan iets doen. */
    private record Step(int waitTicks, Runnable action) {}

    private enum Stage { START, LOADING, TOUR, DONE }

    private static final List<Step> STEPS = new ArrayList<>();
    private static Stage stage = Stage.START;
    private static int ticksInWorld;
    private static int stepIndex;
    private static int waiting;

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
                    quietOptions(mc);
                    createWorld(mc);
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
                if (waiting > 0) {
                    waiting--;
                    return;
                }
                if (stepIndex >= STEPS.size()) {
                    stage = Stage.DONE;
                    StarkTech.LOGGER.info("[visual check] klaar, spel sluit af");
                    mc.stop();
                    return;
                }
                Step step = STEPS.get(stepIndex++);
                step.action().run();
                waiting = stepIndex < STEPS.size() ? STEPS.get(stepIndex).waitTicks() : 20;
            }
            case DONE -> {
            }
        }
    }

    // ================================================================ De rondleiding

    private static void buildTour() {
        // Klaarzetten: de wereld inrichten en de bril opzetten. Het opstarten van de bril
        // (het "boot"-scherm) duurt een paar seconden, dus daarna even wachten.
        step(0, () -> {
            command("gamerule doDaylightCycle false");
            command("gamerule doWeatherCycle false");
            command("gamerule doMobSpawning false");
            command("gamerule starktechRaidGriefing false");
            command("time set noon");
            command("weather clear");
            // Een platte wereld zit vol slijmen; die vullen de kaart met rode stipjes.
            command("kill @e[type=minecraft:slime]");
            onServer(VisualCheck::buildScene);
        });
        view(20, 0.5, -60, -3.2, 0, 22);

        // --- De holotafel
        shot(200, "01_holo_map");
        open(0, () -> new HoloTableScreen(TABLE));
        shot(20, "02_holo_screen");
        close(0);
        onTable(0, table -> table.setBlueprint(new ItemStack(ModItems.CHARGING_STATION.get())));
        shot(30, "03_holo_blueprint");
        open(0, () -> new HoloTableScreen(TABLE));
        shot(20, "04_holo_screen_blueprint");
        close(0);
        onTable(0, table -> table.setMode(HoloMode.ARSENAL));
        shot(30, "05_holo_arsenal");
        onTable(0, table -> {
            table.setMode(HoloMode.MAP);
            table.setScale(2);
            table.setRange(3);
        });
        shot(80, "06_holo_map_big_far");
        onTable(0, table -> {
            table.setScale(0);
            table.setRange(0);
        });
        shot(80, "07_holo_map_small_near");
        onTable(0, table -> {
            table.setScale(1);
            table.setRange(1);
        });
        step(0, () -> command("time set midnight"));
        shot(80, "08_holo_night");
        step(0, () -> command("time set noon"));
        view(0, 0.5, -57.2, -2.2, 0, 55);
        shot(40, "09_holo_from_above");
        view(0, 3.2, -60, 2.8, 130, 18);
        shot(40, "10_holo_side");

        // --- De schermen
        view(0, 0.5, -60, -3.2, 0, 22);
        open(20, EdithMenuScreen::new);
        shot(20, "20_menu");
        open(0, ArsenalScreen::new);
        shot(20, "21_arsenal");
        open(0, NavigationScreen::new);
        shot(20, "22_navigation");
        open(0, () -> new WaypointEditScreen(0));
        shot(20, "23_waypoint_edit");
        open(0, CommsScreen::new);
        shot(20, "24_comms");
        open(0, MusicScreen::new);
        shot(20, "25_music");
        open(0, RelayListScreen::new);
        shot(20, "26_relays");
        open(0, () -> new SpyReportScreen(Component.literal("Steve"), List.of(
                Component.literal("00:30  Steve is at 120, 64, -40"),
                Component.literal("01:00  Steve mined 3 diamond ore"),
                Component.literal("01:30  Steve entered a cave"))));
        shot(20, "27_spy_report");
        open(0, () -> new VoiceCommandsScreen(null));
        shot(20, "28_voice_commands");
        close(0);

        // --- De HUD, kijkend naar een vijand
        view(10, 6.5, -60, -4.5, 180, 8);
        shot(60, "30_hud_target");

        // --- Het harnas
        onServer(0, player -> {
            player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(ModItems.STARK_CHESTPLATE.get()));
            player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(ModItems.STARK_LEGGINGS.get()));
            player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ModItems.STARK_BOOTS.get()));
        });
        view(0, -4.5, -60, -4.5, 225, 10);
        camera(0, CameraType.THIRD_PERSON_FRONT);
        shot(40, "40_suit_front");
        camera(0, CameraType.THIRD_PERSON_BACK);
        shot(20, "41_suit_back");
        camera(0, CameraType.FIRST_PERSON);

        // --- De repulsor: aanzetten, dan de aanvalsknop "indrukken" zoals een speler dat doet
        view(0, 6.5, -60, -4.5, 180, 8);
        step(10, RepulsorControl::toggle);
        shot(30, "42_repulsor_armed");
        step(0, () -> Minecraft.getInstance().options.keyAttack.setDown(true));
        step(2, () -> Minecraft.getInstance().options.keyAttack.setDown(false));
        shot(4, "43_repulsor_fired");
        step(30, RepulsorControl::toggle);

        // --- Vliegen met het harnas. In creative vlieg je gewoon zoals altijd, dus even survival.
        // We drukken de toetsen in zoals een speler en schrijven bij elke stap de snelheid in de
        // log ("[visual check] vlucht"), zodat de natuurkunde na te rekenen is.
        step(0, () -> command("gamemode survival @a"));
        stand(10, 0.5, -60, -24, 180, -5);
        key(40, "jump", true);
        key(2, "jump", false);
        key(2, "jump", true);            // dubbel tikken: stuwraketten aan
        probe(1, "dubbel getikt");
        probe(24, "25 ticks spatie");
        key(0, "jump", false);
        probe(10, "10 ticks los");
        probe(20, "30 ticks los");
        camera(0, CameraType.THIRD_PERSON_BACK);
        // Zonder HUD (zoals F1): anders vallen de vlammen onder je voeten achter de hartjes.
        step(0, () -> Minecraft.getInstance().options.hideGui = true);
        shot(10, "47_flight_jets");
        step(0, () -> Minecraft.getInstance().options.hideGui = false);
        camera(0, CameraType.FIRST_PERSON);
        key(0, "up", true);
        key(0, "sprint", true);
        probe(10, "boost 10");
        probe(10, "boost 20");
        probe(10, "boost 30");
        probe(10, "boost 40");
        shot(0, "44_flight_boost");
        key(0, "left", true);
        shot(10, "45_flight_bank");
        key(0, "left", false);
        key(0, "up", false);
        key(0, "sprint", false);
        probe(10, "uitglijden 10");
        probe(10, "uitglijden 20");
        probe(20, "uitglijden 40");
        shot(0, "46_flight_glide");
        key(0, "shift", true);
        probe(40, "dalen 40");
        probe(40, "dalen 80");
        key(0, "shift", false);
        probe(20, "geland");
        step(0, () -> command("gamemode creative @a"));

        // --- Drones: onze eigen en een raiddrone, stilgezet zodat je ze goed ziet
        onServer(0, VisualCheck::spawnDrones);
        view(0, 0.5, -59, -16, 0, 0);
        shot(40, "50_drones");
        view(0, 0.5, -59, -18.5, 0, 0);
        shot(20, "51_drones_close");
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
        spawnStill(level, EntityType.HUSK, ENEMY, 0);
        spawnStill(level, EntityType.COW, ANIMAL, 90);

        // De bril, vol opgeladen en van jou.
        ItemStack glasses = new ItemStack(ModItems.EDITH_GLASSES.get());
        EdithData.setEnergy(glasses, EdithData.getMaxEnergy(glasses));
        EdithData.setOwner(glasses, new EdithOwner(player.getUUID(), player.getGameProfile().getName(), false));
        player.setItemSlot(EquipmentSlot.HEAD, glasses);

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

    private static void spawnDrones(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        spawnStill(level, ModEntities.STARK_DRONE.get(), new BlockPos(-1, -59, -14), 180);
        spawnStill(level, ModEntities.RAID_DRONE.get(), new BlockPos(2, -59, -14), 180);
    }

    private static void spawnStill(ServerLevel level, EntityType<? extends Entity> type, BlockPos pos, float yaw) {
        Entity entity = type.create(level);
        if (entity == null) {
            return;
        }
        entity.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, yaw, 0);
        entity.setYHeadRot(yaw);
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            mob.yBodyRot = yaw;
        }
        level.addFreshEntity(entity);
    }

    // ================================================================ Bouwstenen van de rondleiding

    private static void step(int waitTicks, Runnable action) {
        STEPS.add(new Step(waitTicks, action));
    }

    /** Een screenshot, na eerst zoveel ticks te wachten. */
    private static void shot(int waitTicks, String name) {
        step(waitTicks, () -> {
            Minecraft mc = Minecraft.getInstance();
            Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                    message -> StarkTech.LOGGER.info("[visual check] {}", message.getString()));
        });
    }

    private static void open(int waitTicks, java.util.function.Supplier<Screen> screen) {
        step(waitTicks, () -> Minecraft.getInstance().setScreen(screen.get()));
    }

    private static void close(int waitTicks) {
        step(waitTicks, () -> Minecraft.getInstance().setScreen(null));
    }

    private static void camera(int waitTicks, CameraType type) {
        step(waitTicks, () -> Minecraft.getInstance().options.setCameraType(type));
    }

    /** Een toets indrukken of loslaten, zoals een speler dat doet. */
    private static void key(int waitTicks, String which, boolean down) {
        step(waitTicks, () -> {
            var options = Minecraft.getInstance().options;
            var mapping = switch (which) {
                case "jump" -> options.keyJump;
                case "up" -> options.keyUp;
                case "left" -> options.keyLeft;
                case "sprint" -> options.keySprint;
                case "shift" -> options.keyShift;
                default -> throw new IllegalArgumentException(which);
            };
            mapping.setDown(down);
        });
    }

    /** Schrijft op waar de speler is en hoe snel hij gaat. */
    private static void probe(int waitTicks, String label) {
        step(waitTicks, () -> {
            var player = Minecraft.getInstance().player;
            if (player == null) {
                return;
            }
            var v = player.getDeltaMovement();
            StarkTech.LOGGER.info(String.format(java.util.Locale.ROOT,
                    "[visual check] vlucht %-16s y=%7.2f  v=(%5.2f, %5.2f, %5.2f)  |v|=%4.2f  aan=%s  grond=%s",
                    label, player.getY(), v.x, v.y, v.z, v.length(),
                    com.senne.starktech.client.SuitFlight.isEngaged(), player.onGround()));
        });
    }

    /** Zet de speler op de grond neer, zonder te laten vliegen (voor survival). */
    private static void stand(int waitTicks, double x, double y, double z, float yaw, float pitch) {
        onServer(waitTicks, player -> player.teleportTo(player.serverLevel(), x, y, z, yaw, pitch));
    }

    /** Zet de speler op een plek, kijkend in een richting (yaw 0 = zuid, pitch + = omlaag). */
    private static void view(int waitTicks, double x, double y, double z, float yaw, float pitch) {
        onServer(waitTicks, player -> {
            player.teleportTo(player.serverLevel(), x, y, z, yaw, pitch);
            // Wie in creative vliegend de grond raakt, stopt met vliegen; dan zou je hier naar
            // beneden vallen en naar de verkeerde kant kijken.
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        });
    }

    private static void onTable(int waitTicks, Consumer<HoloTableBlockEntity> action) {
        onServer(waitTicks, player -> {
            if (player.serverLevel().getBlockEntity(TABLE) instanceof HoloTableBlockEntity table) {
                action.accept(table);
            }
        });
    }

    private static void onServer(int waitTicks, Consumer<ServerPlayer> action) {
        step(waitTicks, () -> onServer(action));
    }

    /** Voert iets uit op de server (in hetzelfde spel), met de echte speler daar. */
    private static void onServer(Consumer<ServerPlayer> action) {
        Minecraft mc = Minecraft.getInstance();
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.player == null) {
            return;
        }
        UUID id = mc.player.getUUID();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                action.accept(player);
            }
        });
    }

    private static void command(String command) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server != null) {
            server.execute(() -> server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(), command));
        }
    }

    // ================================================================ Opstarten

    /** Geen geluid, niet pauzeren als het venster de focus kwijt is. */
    private static void quietOptions(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.options.onboardAccessibility = false;
        // Anders staat de uitleg "Move with W, A, S and D" over de helft van de plaatjes.
        mc.options.tutorialStep = TutorialSteps.NONE;
        mc.getTutorial().setStep(TutorialSteps.NONE);
        mc.options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
        // Geen chat in beeld: die stond over de voeten (en straalvlammen) van het harnas.
        mc.options.chatVisibility().set(net.minecraft.world.entity.player.ChatVisiblity.HIDDEN);
        mc.options.save();
    }

    /** Gooit de testwereld van vorige keer weg en maakt een nieuwe, platte wereld. */
    private static void createWorld(Minecraft mc) {
        Path folder = mc.gameDirectory.toPath().resolve("saves").resolve(WORLD);
        if (Files.exists(folder)) {
            try (Stream<Path> files = Files.walk(folder)) {
                files.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
            } catch (IOException e) {
                StarkTech.LOGGER.warn("[visual check] oude wereld niet weg te krijgen", e);
            }
        }
        LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.EASY, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(1L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(),
                new TitleScreen());
    }
}
