package com.senne.imttech.client.dev;

import com.mojang.blaze3d.platform.NativeImage;
import com.senne.imttech.ImtTech;
import com.senne.imttech.client.BatteryScreen;
import com.senne.imttech.client.howto.HowToIndexScreen;
import com.senne.imttech.client.howto.HowToScreen;
import com.senne.imttech.howto.scenes.PowerScenes;
import com.senne.imttech.registry.ModItems;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/**
 * Het stuk "How it works" van de rondleiding (hoofdstuk 63), en de plaatjes van echte schermen die in de animaties in
 * beeld schuiven.
 *
 * <ul>
 *   <li>{@code gradlew runVisualCheck -Ponly=howto}: een Battery Casing in je inventaris met de regel "Hold [W]" in zijn
 *   tooltip, W echt ingedrukt (de regel loopt vol, de animatie gaat open), elk hoofdstukje van de batterij, gedraaid en
 *   ingezoomd, een blok aanwijzen, terug naar je inventaris, het knopje en het overzicht.</li>
 *   <li>{@code gradlew runVisualCheck -Ponly=howtopics}: knipt de panelen van echte schermen uit en zet ze in
 *   {@code src/main/resources/assets/imttech/textures/gui/howto/}. Alleen als je erom vraagt (niet in de hele
 *   rondleiding), want het verandert bestanden van de mod.</li>
 * </ul>
 */
final class HowToTour {
    private HowToTour() {}

    /** De rondleiding: screenshots ht01 tot ht20. */
    static void tour(DevScript t) {
        // In creative opent Minecraft het creative-menu in plaats van je inventaris (met het knopje): dus even survival.
        t.command(0, "gamemode survival @a");
        t.onServer(0, player -> {
            player.getInventory().setItem(0, new ItemStack(ModItems.BATTERY_CASING.get()));
            player.getInventory().setItem(1, new ItemStack(ModItems.BATTERY_PORT.get()));
            player.getInventory().selected = 2;
        });
        t.open(10, () -> new InventoryScreen(Minecraft.getInstance().player));
        // De muis op het eerste vakje van de werkbalk (daar ligt de casing): de tooltip met "Hold [W]: How it works".
        t.step(5, () -> inventory(screen -> mouseTo(screen.getGuiLeft() + 16, screen.getGuiTop() + 150)));
        t.shot(10, "ht01_tooltip_hold_w");
        t.step(0, () -> DevKeys.press(GLFW.GLFW_KEY_W, true));
        t.shot(7, "ht02_tooltip_filling");
        t.waitUntil(40, () -> Minecraft.getInstance().screen instanceof HowToScreen);
        t.step(0, () -> DevKeys.press(GLFW.GLFW_KEY_W, false));
        t.step(0, () -> mouseTo(2, 2));
        // Net open, terwijl hij speelt: de vloer valt op zijn plek.
        t.shot(14, "ht02b_floor_falling");
        // Elk hoofdstukje van de Power Battery, stilgezet op een mooi moment.
        show(t, 0, 60, "ht03_box_of_casing");
        show(t, 0, 140, "ht04_glass_window");
        show(t, 1, 40, "ht05_cells");
        show(t, 2, 9, "ht06_click_port");
        show(t, 2, 40, "ht07_port_placed");
        show(t, 3, 30, "ht08_drawing_the_cable");
        show(t, 3, 110, "ht09_charging_meter");
        show(t, 4, 12, "ht10_right_click_screen");
        show(t, 4, 95, "ht11_screen_picture");
        show(t, 5, 30, "ht12_keep_it_closed");
        show(t, -1, 0, "ht13_finished");
        // Gedraaid en ingezoomd (alsof je sleept en scrolt), en een blok aanwijzen: de poort.
        t.step(0, () -> howTo(screen -> {
            screen.devShow(3, 110);
            screen.devCamera(-30.0f, 45.0f, 1.6f);
        }));
        t.shot(5, "ht14_turned_and_zoomed");
        t.step(0, () -> howTo(screen -> screen.devCamera(40.0f, 30.0f, 1.0f)));
        t.step(3, () -> howTo(screen -> {
            float[] at = screen.devProject(Vec3.atCenterOf(PowerScenes.PORT).add(-0.45, 0, 0));
            mouseTo(at[0], at[1]);
        }));
        t.shot(5, "ht15_identify_block");
        t.step(0, () -> mouseTo(2, 2));
        // Esc: terug naar je inventaris, met het knopje rechtsboven.
        t.close(5);
        t.step(5, () -> inventory(screen -> mouseTo(screen.getGuiLeft() + 164, screen.getGuiTop() + 11)));
        t.shot(10, "ht16_inventory_button");
        // Een echte klik op het knopje (zoals de muis hem doorgeeft): het overzicht.
        t.step(0, () -> inventory(screen -> NeoForge.EVENT_BUS.post(
                new ScreenEvent.MouseButtonPressed.Pre(screen, screen.getGuiLeft() + 164, screen.getGuiTop() + 11, 0))));
        t.step(3, () -> mouseTo(2, 2));
        t.shot(10, "ht17_overview");
        t.step(0, () -> {
            if (Minecraft.getInstance().screen instanceof HowToIndexScreen index) {
                int[] tile = index.devTileCenter("power_battery");
                if (tile != null) {
                    mouseTo(tile[0], tile[1]);
                }
            }
        });
        t.shot(5, "ht18_overview_hover");
        t.step(0, () -> {
            if (Minecraft.getInstance().screen instanceof HowToIndexScreen index) {
                int[] tile = index.devTileCenter("power_battery");
                if (tile != null) {
                    index.mouseClicked(tile[0], tile[1], 0);
                }
            }
        });
        t.step(2, () -> mouseTo(2, 2));
        t.shot(30, "ht19_from_the_overview");
        t.close(2);
        t.close(2);
        t.close(2);
        t.command(0, "gamemode creative @a");
    }

    /** Zoveel animaties per onderwerp en hoofdstukjes per animatie fotograferen we hooguit (wat er niet is, slaan we over). */
    private static final int MAX_SCENES = 3;
    private static final int MAX_CHAPTERS = 8;

    /**
     * {@code -Ponly=howtoall}: van elke animatie die klaar is een screenshot per hoofdstukje (op 55% en 90% ervan), om ze
     * allemaal na te kijken: {@code ha_<onderwerp>_<animatie>_<hoofdstukje><a of b>}. Het draaiboek van de rondleiding
     * wordt gemaakt voor de blokken van Minecraft bestaan; de animaties maken we dus pas tijdens de rondleiding zelf, en
     * wat er niet is (een tweede animatie, een achtste hoofdstukje), slaan we dan over.
     */
    static void allScenes(DevScript t, java.util.Set<String> only) {
        // Met "howto:turrets" (en meer) in -Ponly alleen die onderwerpen, anders allemaal.
        java.util.Set<String> wanted = new java.util.HashSet<>();
        for (String part : only) {
            if (part.startsWith("howto:")) {
                wanted.add(part.substring("howto:".length()));
            }
        }
        for (com.senne.imttech.howto.HowToTopic topic : com.senne.imttech.howto.HowToTopics.ALL) {
            if (!topic.ready() || (!wanted.isEmpty() && !wanted.contains(topic.id()))) {
                continue;
            }
            for (int scene = 0; scene < MAX_SCENES; scene++) {
                final int index = scene;
                t.step(2, () -> {
                    Minecraft.getInstance().setScreen(null);
                    if (index < topic.buildScenes().size()) {
                        HowToScreen.open(topic);
                        howTo(screen -> screen.devScene(index));
                    }
                });
                t.step(2, () -> mouseTo(2, 2));
                for (int chapter = 0; chapter < MAX_CHAPTERS; chapter++) {
                    final int c = chapter;
                    for (String part : new String[] {"a", "b"}) {
                        float fraction = part.equals("a") ? 0.55f : 0.9f;
                        String name = "ha_" + topic.id() + "_" + scene + "_" + chapter + part;
                        t.step(0, () -> {
                            if (Minecraft.getInstance().screen instanceof HowToScreen screen && c < screen.devChapters()) {
                                screen.devShowFraction(c, fraction);
                            }
                        });
                        t.step(3, () -> {
                            Minecraft mc = Minecraft.getInstance();
                            if (mc.screen instanceof HowToScreen screen && c < screen.devChapters()) {
                                Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(),
                                        message -> ImtTech.LOGGER.info("[dev] {}", message.getString()));
                            }
                        });
                    }
                }
            }
        }
        t.step(2, () -> Minecraft.getInstance().setScreen(null));
    }

    /** Springt in de open animatie naar een hoofdstukje (+ ticks) en maakt een screenshot. */
    private static void show(DevScript t, int chapter, int offset, String shot) {
        t.step(0, () -> howTo(screen -> screen.devShow(chapter, offset)));
        t.shot(4, shot);
    }

    private static void howTo(Consumer<HowToScreen> action) {
        if (Minecraft.getInstance().screen instanceof HowToScreen screen) {
            action.accept(screen);
        } else {
            ImtTech.LOGGER.warn("[visual check] het scherm van How it works staat niet open (maar {})", Minecraft.getInstance().screen);
        }
    }

    private static void inventory(Consumer<InventoryScreen> action) {
        if (Minecraft.getInstance().screen instanceof InventoryScreen screen) {
            action.accept(screen);
        } else {
            ImtTech.LOGGER.warn("[visual check] de inventaris staat niet open (maar {})", Minecraft.getInstance().screen);
        }
    }

    /** Zet de muis op een plek van het scherm (in de maat van het scherm, niet in pixels van het venster). */
    static void mouseTo(double guiX, double guiY) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        try {
            java.lang.reflect.Field x = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            java.lang.reflect.Field y = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            x.setAccessible(true);
            y.setAccessible(true);
            x.setDouble(mc.mouseHandler, guiX * scale);
            y.setDouble(mc.mouseHandler, guiY * scale);
        } catch (ReflectiveOperationException | RuntimeException e) {
            ImtTech.LOGGER.warn("[visual check] kan de muis niet verzetten", e);
        }
    }

    // ================================================================ Plaatjes van echte schermen

    /**
     * Maakt de plaatjes van de schermen die in de animaties voorkomen (nu: het scherm van de Power Battery). Het echte
     * scherm, maar met voorbeeldcijfers (een batterij met wat generatoren en apparaten): in de testwereld staat alles nog
     * op 0 per tick, en dat lijkt in een uitleg kapot. Je kijkt recht omhoog, zodat er achter het paneel alleen lucht is.
     */
    static void pictures(DevScript t) {
        t.look(5, 0.0f, -90.0f);
        t.open(10, () -> new BatteryScreen(demoBattery()));
        t.step(5, () -> mouseTo(1, 1));
        t.step(20, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof BatteryScreen screen) {
                capture("battery", (screen.width - BatteryScreen.W) / 2, (screen.height - BatteryScreen.H) / 2,
                        BatteryScreen.W, BatteryScreen.H);
            } else {
                ImtTech.LOGGER.warn("[visual check] het scherm van de batterij staat niet open (maar {})", mc.screen);
            }
        });
        t.close(0);
        t.look(0, 0.0f, 10.0f);
        securityPictures(t);
    }

    /** Waar de kern voor de plaatjes van de beveiliging staat (ver van de rest van de rondleiding). */
    private static final BlockPos DEMO_CORE = new BlockPos(-460, -60, -460);
    private static final BlockPos DEMO_EMITTER = DEMO_CORE.offset(4, 0, 0);

    /** Het diagram op het plaatje "logic" (de keycard en het codepaneel van de animaties openen samen "Lab lock"). */
    private static final String DEMO_CODE = String.join("\n",
            "when keycard(\"Lab door\"):",
            "    if card.in_group(\"lab\"):",
            "        accept()",
            "        pulse(\"Lab lock\", 3)",
            "    else:",
            "        deny()",
            "",
            "when codepanel(\"Front door\"):",
            "    if code == \"4321\":",
            "        accept()",
            "        pulse(\"Lab lock\", 3)",
            "    else:",
            "        deny()");

    /**
     * De plaatjes van de beveiliging (hoofdstuk 63, stap 8): de desktop van een kern, de app Keycards met een paar groepen
     * en kaarten, de app Logic met het diagram van de deur, het codepaneel met een getypte code, en het instelscherm van
     * een laserzender. Allemaal het hele venster (de desktop) of het paneel (de kleine schermen).
     */
    private static void securityPictures(DevScript t) {
        t.onServer(0, player -> {
            net.minecraft.server.level.ServerLevel level = player.serverLevel();
            level.setBlockAndUpdate(DEMO_CORE.below(), com.senne.imttech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(DEMO_CORE.below()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
                generator.fill(200_000);
            }
            level.setBlockAndUpdate(DEMO_CORE, com.senne.imttech.registry.ModBlocks.SECURITY_CORE.get().defaultBlockState());
            if (level.getBlockEntity(DEMO_CORE) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
                core.setOwner(player);
                core.ensureSystem(level);
                // Met een wachtwoord opent de desktop niet meteen het venster Settings.
                core.setPassword("bitech1", level.random);
            }
            // Een serverrek tegen de kern (met een generator eronder), zodat Logic "online" is.
            BlockPos rack = DEMO_CORE.east();
            level.setBlockAndUpdate(rack.below(), com.senne.imttech.registry.ModBlocks.HEAT_GENERATOR.get().defaultBlockState());
            if (level.getBlockEntity(rack.below()) instanceof com.senne.imttech.station.ChargerBlockEntity generator) {
                generator.fill(200_000);
            }
            level.setBlockAndUpdate(rack, com.senne.imttech.registry.ModBlocks.SERVER_RACK.get().defaultBlockState());
            if (level.getBlockEntity(rack) instanceof com.senne.imttech.logic.ServerRackBlockEntity be) {
                // Hoofdstuk 64: twee CPU's, RAM en een SSD, twee fans, en een naam (zo staat hij in de app Monitor).
                be.insert(new ItemStack(ModItems.SERVER_CPU.get()));
                be.insert(new ItemStack(ModItems.SERVER_CPU.get()));
                be.insert(new ItemStack(ModItems.SERVER_RAM.get()));
                be.insert(new ItemStack(ModItems.SERVER_SSD.get()));
                be.insert(new ItemStack(ModItems.SERVER_FAN.get()));
                be.insert(new ItemStack(ModItems.SERVER_FAN.get()));
                be.setName("rack1");
            }
            level.setBlockAndUpdate(DEMO_EMITTER, com.senne.imttech.registry.ModBlocks.LASER_EMITTER.get().defaultBlockState()
                    .setValue(com.senne.imttech.shield.LaserEmitterBlock.FACING, net.minecraft.core.Direction.SOUTH));
            player.getInventory().setItem(8, new ItemStack(ModItems.KEYCARD.get(), 8));
        });
        t.stand(10, DEMO_CORE.getX() + 0.5, DEMO_CORE.getY(), DEMO_CORE.getZ() + 3.5, 180, 0);
        t.look(5, 0.0f, -90.0f);
        // De desktop, zonder vensters.
        t.onServer(10, player -> com.senne.imttech.security.os.SecurityOs.open(player, DEMO_CORE));
        t.step(5, () -> mouseTo(1, 1));
        t.step(5, () -> os(com.senne.imttech.client.os.SecurityOsScreen::tourCloseAll));
        t.step(15, () -> captureScreen("security_os"));
        // De app Keycards: groepen en kaarten zoals in de animatie (lab, security, guest).
        t.onServer(0, player -> {
            for (String[] group : new String[][] {{"staff", ""}, {"lab", "staff"}, {"security", "staff"}, {"guest", ""}}) {
                net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
                args.putString("name", group[0]);
                args.putString("parent", group[1]);
                com.senne.imttech.security.os.SecurityOs.handleAction(player,
                        new com.senne.imttech.network.OsActionPayload(DEMO_CORE, "cgroup_new", args));
            }
            for (String[] card : new String[][] {{"Lab access", "2", "lab"}, {"Lotte", "5", "security"},
                    {"Visitor", "6", "guest"}, {"Night guard", "8", "security"}}) {
                net.minecraft.nbt.CompoundTag args = new net.minecraft.nbt.CompoundTag();
                args.putString("label", card[0]);
                args.putInt("colour", Integer.parseInt(card[1]));
                args.putString("group", card[2]);
                com.senne.imttech.security.os.SecurityOs.handleAction(player,
                        new com.senne.imttech.network.OsActionPayload(DEMO_CORE, "card_write", args));
            }
        });
        // Wachten tot de melding onderaan ("Card written") weg is (vier seconden), anders staat die over de taakbalk.
        t.step(95, () -> os(screen -> {
            screen.tourKeycards("Lab access", 2);
            screen.tourKeycardGroup(null);
        }));
        t.step(15, () -> captureScreen("keycards_app"));
        // De app Logic met het diagram van de deur (alleen dat venster open).
        t.step(0, () -> os(screen -> {
            screen.tourCloseAll();
            screen.tourLogic("Lab door", DEMO_CODE, false);
        }));
        t.step(15, () -> captureScreen("logic"));
        // Logic met een knop en een hendel (voor de animatie van de netwerkknop en -hendel).
        t.step(0, () -> os(screen -> {
            screen.tourCloseAll();
            screen.tourLogic("Front hall", BUTTON_CODE, false);
        }));
        t.step(15, () -> captureScreen("logic_button"));
        t.close(5);
        t.close(5);
        // Het codepaneel met vier cijfers ingetypt.
        t.step(5, () -> {
            Minecraft.getInstance().setScreen(new com.senne.imttech.client.CodePanelScreen(DEMO_CORE.east(), "Front door"));
            if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.CodePanelScreen panel) {
                panel.tourType("4321");
            }
        });
        t.step(15, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                capture("code_panel", (mc.screen.width - 94) / 2, (mc.screen.height - 150) / 2, 94, 150);
            }
        });
        t.close(5);
        // Het instelscherm van een laserzender.
        t.open(5, () -> new com.senne.imttech.client.shield.LaserEmitterScreen(DEMO_EMITTER, "Dev", "Gate 1"));
        t.step(15, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                int top = Math.max(6, (mc.screen.height - 186) / 2);
                capture("laser_settings", (mc.screen.width - 270) / 2, top, 270, 186);
            }
        });
        t.close(5);
        // De app Monitor met het rek naast de kern gekozen (hoofdstuk 64: het rek heeft geen eigen scherm meer, zijn cijfers
        // staan in de Monitor onder zijn naam).
        t.onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, DEMO_CORE));
        t.step(10, () -> mouseTo(1, 1));
        t.step(5, () -> os(screen -> {
            screen.tourCloseAll();
            screen.tourMonitorRack("rack1");
        }));
        t.step(40, () -> captureScreen("monitor_rack"));
        t.close(5);
        // Het scherm van een koepelgenerator.
        t.onServer(0, player -> player.serverLevel().setBlockAndUpdate(DEMO_DOME,
                com.senne.imttech.registry.ModBlocks.DOME_GENERATOR.get().defaultBlockState()));
        t.open(10, () -> new com.senne.imttech.client.DomeScreen(DEMO_DOME));
        t.step(15, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                capture("dome_screen", (mc.screen.width - 300) / 2, Math.max(6, (mc.screen.height - 206) / 2), 300, 206);
            }
        });
        t.close(5);
        // De bril: de HUD (zonder scherm) en het G-menu op het tabblad Drones, met de wereld erachter.
        t.look(5, 30.0f, 8.0f);
        t.step(30, () -> captureScreen("glasses_hud"));
        t.step(0, () -> com.senne.imttech.client.ImtClientState.menuTab = com.senne.imttech.client.MenuTab.DRONES);
        t.open(0, com.senne.imttech.client.ImtMenuScreen::new);
        t.step(20, () -> captureScreen("glasses_menu"));
        t.close(5);
        t.step(0, () -> com.senne.imttech.client.ImtClientState.menuTab = com.senne.imttech.client.MenuTab.COMBAT);
        t.look(0, 0.0f, 10.0f);
        robotPictures(t);
        weaponPictures(t);
        securityItemPictures(t);
    }

    // ================================================================ De robots

    private static final BlockPos ROBOT_DOCK = DEMO_CORE.offset(-2, 0, 2);
    private static final BlockPos DRONE_DOCK = DEMO_CORE.offset(2, 0, 2);
    private static final BlockPos ASSEMBLER = DEMO_CORE.offset(-3, 0, 4);
    private static final BlockPos TRAINING_START = DEMO_CORE.offset(-1, 0, 5);
    private static final BlockPos TRAINING_END = DEMO_CORE.offset(1, 0, 5);
    private static final BlockPos TRAINING_GOAL = DEMO_CORE.offset(3, 0, 5);

    /** Het robotprogramma op het plaatje "logic_robot": rondjes lopen, en een opdracht van de server. */
    private static final String PATROL_CODE = String.join("\n",
            "when started:",
            "    while true:",
            "        if robot.battery < 20:",
            "            charge(80)",
            "        forward(3)",
            "        turn_right()",
            "        send(\"corner\")",
            "",
            "when ordered(\"attack\"):",
            "    goto(order.x, order.y, order.z)");

    /** Het robotprogramma op het plaatje "logic_guard": de modules met read en write. */
    private static final String GUARD_CODE = String.join("\n",
            "scanPin = pin(1)",
            "facePin = pin(2)",
            "laserPin = pin(3)",
            "",
            "when started:",
            "    write(facePin, \"strangers\")",
            "    write(laserPin, true)",
            "",
            "when every(1):",
            "    if read(scanPin):",
            "        alarm(\"Intruder near \" + robot.name)");

    /** Het droneprogramma op het plaatje "logic_drone". */
    private static final String DRONE_CODE = String.join("\n",
            "when started:",
            "    up(1)",
            "    while true:",
            "        if drone.battery < 30:",
            "            charge(50)",
            "        forward(3)",
            "        turn_right()");

    /** Het programmaatje van het Training End-blok op het plaatje "training_end". */
    private static final String END_CODE = String.join("\n",
            "# Runs when the trainee is hit.",
            "when trainee_hit:",
            "    say(\"You're hit, cadet\")",
            "    if lives == 0:",
            "        deactivate(\"Wave 1\")",
            "        deactivate(\"Wave 2\")",
            "");

    /**
     * De plaatjes van de robots (hoofdstuk 63, de robots): de Robot Assembler met een gemengde robot, het robotscherm, het
     * bordscherm met de hittebalk en met Inspect, de app Logic met een robot-, bewakers- en droneprogramma, de app Robots
     * met de knop Program, de app Training met twee rapporten, het instelscherm van Training Start en de mini-editor van
     * Training End. Bij de kern van de plaatjes van de beveiliging (met een serverrek: dan hebben de robots verbinding).
     */
    private static void robotPictures(DevScript t) {
        t.onServer(0, player -> {
            net.minecraft.server.level.ServerLevel level = player.serverLevel();
            // Een robot in zijn dock (uit, halfvol, wat schade), met modules op pin 1, 3 en 6 en een verstopte brug 3-4.
            level.setBlockAndUpdate(ROBOT_DOCK, com.senne.imttech.registry.ModBlocks.ROBOT_DOCK.get().defaultBlockState()
                    .setValue(com.senne.imttech.robot.RobotDockBlock.FACING, net.minecraft.core.Direction.SOUTH));
            if (DevWorld.spawnStill(level, com.senne.imttech.registry.ModEntities.GUARD_ROBOT.get(), ROBOT_DOCK, 0)
                    instanceof com.senne.imttech.robot.GuardRobotEntity robot
                    && level.getBlockEntity(ROBOT_DOCK) instanceof com.senne.imttech.robot.RobotDockBlockEntity dock) {
                robot.setParts(com.senne.imttech.howto.scenes.RobotScenes.MIX);
                robot.setPos(ROBOT_DOCK.getX() + 0.5, ROBOT_DOCK.getY() + 3.0 / 16.0, ROBOT_DOCK.getZ() + 0.5);
                robot.setOwner(player);
                robot.setHome(ROBOT_DOCK);
                dock.setRobot(robot.getUUID());
                robot.setBattery(robot.maxBattery() * 0.56);
                robot.setHealth(robot.getMaxHealth() * 0.8f);
                robot.setBoard(com.senne.imttech.bot.BotBoard.EMPTY.with(1, "scan").with(3, "laser").with(6, "camera")
                        .withBridge(3));
            }
            com.senne.imttech.robot.GuardDroneEntity drone = VisualCheck.securityDrone(level, player, DRONE_DOCK, false);
            level.setBlockAndUpdate(DRONE_DOCK, com.senne.imttech.registry.ModBlocks.DRONE_DOCK.get().defaultBlockState()
                    .setValue(com.senne.imttech.arsenal.DroneDockBlock.FACING, net.minecraft.core.Direction.SOUTH));
            drone.moveTo(DRONE_DOCK.getX() + 0.5, DRONE_DOCK.getY() + 4.0 / 16.0, DRONE_DOCK.getZ() + 0.5, 0, 0);
            drone.setHome(DRONE_DOCK);
            if (level.getBlockEntity(DRONE_DOCK) instanceof com.senne.imttech.arsenal.DroneDockBlockEntity dock) {
                dock.setBot(drone.getUUID());
            }
            // Wat je bij je hebt om te solderen.
            player.getInventory().add(new ItemStack(ModItems.SOLDERING_IRON.get()));
            player.getInventory().add(new ItemStack(ModItems.robotModule(com.senne.imttech.bot.BotModule.SCAN), 2));
            player.getInventory().add(new ItemStack(ModItems.robotModule(com.senne.imttech.bot.BotModule.GUN)));
            player.getInventory().add(new ItemStack(ModItems.robotModule(com.senne.imttech.bot.BotModule.FACE_RECOGNITION)));
            // De programma's op de server, de training en zijn blokken.
            if (level.getBlockEntity(DEMO_CORE) instanceof com.senne.imttech.security.SecurityCoreBlockEntity core) {
                core.logic().put(null, VisualCheck.robotProgram("Patrol", PATROL_CODE + "\n",
                        com.senne.imttech.logic.program.ProgramKind.ROBOT));
                core.logic().put(null, VisualCheck.robotProgram("Guard", GUARD_CODE + "\n",
                        com.senne.imttech.logic.program.ProgramKind.ROBOT));
                core.logic().put(null, VisualCheck.robotProgram("Flight 1", DRONE_CODE + "\n",
                        com.senne.imttech.logic.program.ProgramKind.DRONE));
                com.senne.imttech.training.Training.Mode shots = com.senne.imttech.training.Training.Mode.TRAINING_SHOTS;
                long now = level.getGameTime();
                String name = player.getGameProfile().getName();
                core.addTrainingReport(new com.senne.imttech.training.TrainingReport("Bootcamp", name, shots,
                        com.senne.imttech.training.Training.Result.OUT_OF_LIVES, 1440, 3, 0, "Guard 2 x2, Guard 1 x1", 1, now - 4000));
                core.addTrainingReport(new com.senne.imttech.training.TrainingReport("Bootcamp", name, shots,
                        com.senne.imttech.training.Training.Result.GOAL, 2680, 1, 2, "Guard 1 x1", 2, now - 1200));
            }
            level.setBlockAndUpdate(TRAINING_START, com.senne.imttech.registry.ModBlocks.TRAINING_START.get().defaultBlockState());
            level.setBlockAndUpdate(TRAINING_END, com.senne.imttech.registry.ModBlocks.TRAINING_END.get().defaultBlockState());
            level.setBlockAndUpdate(TRAINING_GOAL, com.senne.imttech.registry.ModBlocks.TRAINING_GOAL.get().defaultBlockState());
            if (level.getBlockEntity(TRAINING_START) instanceof com.senne.imttech.training.TrainingStartBlockEntity be) {
                be.setSettings(new com.senne.imttech.training.Training.Settings("Bootcamp",
                        com.senne.imttech.training.Training.Mode.TRAINING_SHOTS, 3, 0, com.senne.imttech.training.Training.Kind.TRAINING));
            }
            if (level.getBlockEntity(TRAINING_END) instanceof com.senne.imttech.training.TrainingEndBlockEntity be) {
                be.setSource(END_CODE);
            }
            // De Robot Assembler met dezelfde gemengde robot en een printplaat.
            level.setBlockAndUpdate(ASSEMBLER, com.senne.imttech.registry.ModBlocks.ROBOT_ASSEMBLER.get().defaultBlockState()
                    .setValue(com.senne.imttech.robot.RobotAssemblerBlock.FACING, net.minecraft.core.Direction.SOUTH));
            if (level.getBlockEntity(ASSEMBLER) instanceof com.senne.imttech.robot.RobotAssemblerBlockEntity be) {
                com.senne.imttech.robot.RobotParts mix = com.senne.imttech.howto.scenes.RobotScenes.MIX;
                be.items().setItem(0, new ItemStack(ModItems.robotPart(com.senne.imttech.robot.RobotSlot.HEAD, mix.head())));
                be.items().setItem(1, new ItemStack(ModItems.robotPart(com.senne.imttech.robot.RobotSlot.BODY, mix.body())));
                be.items().setItem(2, new ItemStack(ModItems.robotPart(com.senne.imttech.robot.RobotSlot.ARMS, mix.arms())));
                be.items().setItem(3, new ItemStack(ModItems.robotPart(com.senne.imttech.robot.RobotSlot.LEGS, mix.legs())));
                be.items().setItem(4, new ItemStack(ModItems.ROBOT_CIRCUIT_BOARD.get()));
            }
        });
        t.stand(10, DEMO_CORE.getX() + 0.5, DEMO_CORE.getY(), DEMO_CORE.getZ() + 3.5, 180, 0);
        t.look(5, 0.0f, -90.0f);
        // Zonder de HUD van de bril achter de schermen, en zonder meldingen rechtsboven (nieuwe recepten van de modules).
        t.step(40, () -> {
            Minecraft.getInstance().options.hideGui = true;
            noToasts();
        });
        // De Robot Assembler.
        t.onServer(5, player -> {
            if (player.serverLevel().getBlockEntity(ASSEMBLER) instanceof com.senne.imttech.robot.RobotAssemblerBlockEntity be) {
                player.openMenu(be, ASSEMBLER);
            }
        });
        t.step(5, () -> mouseTo(1, 1));
        t.step(0, HowToTour::noToasts);
        t.step(30, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                capture("robot_assembler", (mc.screen.width - 176) / 2, (mc.screen.height - 186) / 2, 176, 186);
            }
        });
        t.close(5);
        // Het robotscherm (sneak + rechtsklik).
        t.onServer(5, player -> {
            for (com.senne.imttech.robot.GuardRobotEntity robot : player.serverLevel().getEntitiesOfClass(
                    com.senne.imttech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(ROBOT_DOCK).inflate(0.5))) {
                com.senne.imttech.robot.Robots.open(player, robot);
            }
        });
        t.step(20, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                capture("robot_screen", (mc.screen.width - 300) / 2, Math.max(6, (mc.screen.height - 176) / 2), 300, 176);
            }
        });
        t.close(5);
        // Het bordscherm: de hittebalk boven pin 2, dan Inspect met de brug tussen 3 en 4.
        t.step(5, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                for (com.senne.imttech.robot.GuardRobotEntity robot : mc.level.getEntitiesOfClass(
                        com.senne.imttech.robot.GuardRobotEntity.class, new net.minecraft.world.phys.AABB(ROBOT_DOCK).inflate(0.5))) {
                    com.senne.imttech.client.robot.BoardScreen board = new com.senne.imttech.client.robot.BoardScreen(robot.getId(), null);
                    mc.setScreen(board);
                    board.tourHeat(com.senne.imttech.bot.BotModule.SCAN, 2,
                            Math.round(com.senne.imttech.config.ImtConfig.Robots.SOLDER_FULL_TICKS * 0.6f));
                }
            }
        });
        t.step(15, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                capture("robot_board", (mc.screen.width - 300) / 2, Math.max(6, (mc.screen.height - 196) / 2), 300, 196);
            }
        });
        t.step(0, () -> {
            if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.robot.BoardScreen board) {
                board.tourHeat(com.senne.imttech.bot.BotModule.SCAN, -1, 0);
                board.tourInspect(true);
            }
        });
        t.step(10, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                capture("robot_board_inspect", (mc.screen.width - 300) / 2, Math.max(6, (mc.screen.height - 196) / 2), 300, 196);
            }
        });
        t.close(5);
        // De desktop: Logic met de drie programma's, de app Robots (Program) en de app Training.
        t.onServer(10, player -> com.senne.imttech.security.os.SecurityOs.open(player, DEMO_CORE));
        t.step(5, () -> mouseTo(1, 1));
        t.step(40, () -> os(screen -> {
            screen.tourCloseAll();
            screen.tourLogicOpen("Patrol");
        }));
        t.step(15, () -> captureScreen("logic_robot"));
        t.step(0, () -> os(screen -> {
            screen.tourCloseAll();
            screen.tourLogicOpen("Guard");
        }));
        t.step(15, () -> captureScreen("logic_guard"));
        t.step(0, () -> os(screen -> {
            screen.tourCloseAll();
            screen.tourLogicOpen("Flight 1");
        }));
        t.step(15, () -> captureScreen("logic_drone"));
        t.step(0, () -> os(screen -> {
            screen.tourCloseAll();
            screen.tourRobotsProgram();
        }));
        t.step(15, () -> captureScreen("robots_program"));
        t.step(0, () -> os(screen -> {
            screen.tourCloseAll();
            screen.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.TRAINING);
        }));
        t.step(15, () -> captureScreen("training_app"));
        t.close(5);
        t.close(5);
        // Het instelscherm van Training Start en de mini-editor van Training End.
        t.onServer(5, player -> com.senne.imttech.training.TrainingBlocks.useStart(player, TRAINING_START, true));
        t.step(20, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                capture("training_start", (mc.screen.width - 316) / 2, Math.max(6, (mc.screen.height - 204) / 2), 316, 204);
            }
        });
        t.close(5);
        t.onServer(5, player -> com.senne.imttech.training.TrainingBlocks.useEnd(player, TRAINING_END, true));
        t.step(20, () -> {
            // Het paneel van de mini-editor met zijn rand (zie TrainingEndScreen: 20 kleiner dan het venster, hooguit 420 x 240).
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                int pw = Math.min(mc.screen.width - 20, 420);
                int ph = Math.min(mc.screen.height - 20, 240);
                capture("training_end", (mc.screen.width - pw) / 2 - 4, (mc.screen.height - ph) / 2 - 4, pw + 8, ph + 8);
            }
        });
        t.close(5);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        t.look(0, 0.0f, 10.0f);
    }

    // ================================================================ De wapens

    /** Waar je staat voor de plaatjes van de wapens: de banken staan links van je (zie ShowcaseFeatures.buildGunBenches). */
    private static final BlockPos GUN_STAND = DEMO_CORE.offset(10, 0, 8);

    /**
     * De plaatjes van de wapens (hoofdstuk 63, de wapens): de Gunsmith Table met een half pistool, de Reloading Bench, de
     * Laser Workbench met een half geweer, de skilltree, de HUD met een lasergeweer en met de Glock, je inventaris met de
     * vakjes van je uitrusting, het wiel, de magazijnpouch en het witte scherm van een flashbang. In survival: in creative
     * opent je inventaris het creative-menu (zonder de vakjes van je uitrusting).
     */
    private static void weaponPictures(DevScript t) {
        t.command(0, "gamemode survival @a");
        t.onServer(5, player -> {
            ShowcaseFeatures.buildGunBenches(player, GUN_STAND);
            ShowcaseFeatures.buildLaserBench(player, GUN_STAND);
            ShowcaseFeatures.giveGlock(player);
        });
        t.stand(10, GUN_STAND.getX() + 0.5, GUN_STAND.getY(), GUN_STAND.getZ() + 0.5, 0, -90);
        t.step(30, () -> {
            Minecraft.getInstance().options.hideGui = true;
            noToasts();
        });
        // De Gunsmith Table en de Reloading Bench.
        t.onServer(5, player -> ShowcaseFeatures.openBench(player, GUN_STAND.offset(-2, 0, -2)));
        t.step(5, () -> mouseTo(1, 1));
        t.step(25, () -> captureCentered("gunsmith_table", 328, 186));
        t.close(5);
        t.onServer(5, player -> ShowcaseFeatures.openBench(player, GUN_STAND.offset(-2, 0, 2)));
        t.step(5, () -> mouseTo(1, 1));
        t.step(20, () -> captureCentered("reloading_bench", 176, 166));
        t.close(5);
        // De Laser Workbench (met de laseronderdelen in je inventaris).
        t.onServer(5, ShowcaseFeatures::giveLaserParts);
        t.step(5, HowToTour::noToasts);
        t.onServer(5, player -> ShowcaseFeatures.openBench(player, GUN_STAND.offset(-2, 0, 0)));
        t.step(5, () -> mouseTo(1, 1));
        t.step(25, () -> captureCentered("laser_workbench", 328, 186));
        t.close(5);
        // De skilltree, met punten om te verdelen en een paar niveaus geleerd.
        t.onServer(0, player -> {
            player.setData(com.senne.imttech.registry.ModAttachments.GUN_SKILLS,
                    new com.senne.imttech.guns.GunSkills(25, 7, 7, java.util.Map.of()));
            com.senne.imttech.guns.GunSkillsServer.learn(player, "glock_19", com.senne.imttech.guns.GunSkill.RELOAD);
            com.senne.imttech.guns.GunSkillsServer.learn(player, "glock_19", com.senne.imttech.guns.GunSkill.RELOAD);
            com.senne.imttech.guns.GunSkillsServer.learn(player, com.senne.imttech.guns.GunSkills.GLOBAL,
                    com.senne.imttech.guns.GunSkill.AIM);
        });
        t.open(10, com.senne.imttech.client.guns.SkillTreeScreen::new);
        t.step(20, () -> captureScreen("gun_skills"));
        t.close(5);
        t.onServer(0, player -> player.setData(com.senne.imttech.registry.ModAttachments.GUN_SKILLS,
                com.senne.imttech.guns.GunSkills.EMPTY));
        // De HUD: eerst met de laser rifle in je hand (vak 0 na giveLaserParts), dan met de Glock. Je kijkt over de grond.
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        t.look(5, 0.0f, 12.0f);
        t.step(0, () -> select(0));
        t.step(30, HowToTour::noToasts);
        t.step(15, () -> captureScreen("laser_hud"));
        t.onServer(0, ShowcaseFeatures::giveGlock);
        t.step(5, () -> select(0));
        t.step(5, HowToTour::noToasts);
        t.step(30, () -> captureScreen("glock_hud"));
        // De vakjes van je uitrusting in je inventaris: een holster met de Glock, twee pouches, en twee geweren aan een sling.
        t.onServer(0, player -> {
            ShowcaseFeatures.giveGear(player);
            com.senne.imttech.guns.Gear.setSling(player, 0, ShowcaseFeatures.slungGun(com.senne.imttech.weapons.GunType.RIFLE, 60));
            com.senne.imttech.guns.Gear.setSling(player, 1, ShowcaseFeatures.slungGun(com.senne.imttech.weapons.GunType.SNIPER, 100));
        });
        t.look(5, 0.0f, -90.0f);
        t.step(5, () -> Minecraft.getInstance().options.hideGui = true);
        t.step(10, HowToTour::noToasts);
        t.open(5, () -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(Minecraft.getInstance().player));
        t.step(5, () -> mouseTo(1, 1));
        t.step(15, () -> captureCentered("gear_inventory", 176, 166));
        t.close(5);
        // Het wiel (linker Alt vasthouden), met de wereld erachter.
        t.look(5, 0.0f, 12.0f);
        t.wheel(10, true);
        t.step(15, () -> captureScreen("weapon_wheel"));
        t.wheel(2, false);
        t.step(10, () -> Minecraft.getInstance().options.hideGui = true);
        // De magazijnpouch open.
        t.look(5, 0.0f, -90.0f);
        t.onServer(5, player -> com.senne.imttech.guns.Gear.openPouch(player, com.senne.imttech.guns.GearItem.Kind.MAG_POUCH.ordinal()));
        t.step(5, () -> mouseTo(1, 1));
        t.step(15, () -> captureCentered("mag_pouch", 176, 166));
        t.close(5);
        // Een flashbang vlak voor je, en je kijkt ernaar: het witte scherm.
        t.look(5, 0.0f, 10.0f);
        t.onServer(10, player -> com.senne.imttech.weapons.Flashbang.bang(player.serverLevel(),
                new Vec3(GUN_STAND.getX() + 0.5, GUN_STAND.getY() + 1.5, GUN_STAND.getZ() + 3.5), null));
        // Even later: nog bijna wit, maar de wereld schemert er al doorheen (zie FlashOverlay: eerst wit, dan vaagt het weg).
        t.step(55, () -> captureScreen("flashbang"));
        t.step(60, () -> Minecraft.getInstance().options.hideGui = false);
        t.command(0, "gamemode creative @a");
        t.look(0, 0.0f, 10.0f);
    }

    // ================================================================ De laatste beveiligingsitems

    /** Waar de kern staat die we voor het plaatje van de terminal hacken (niet van jou). */
    private static final BlockPos HACK_CORE = DEMO_CORE.offset(-8, 0, 10);

    /**
     * De plaatjes van de laatste beveiligingsitems (hoofdstuk 63, stap 10): de app Computers en de desktop van een computer
     * (op het bureau van de rondleiding), de inlog van de laptop en de desktop op afstand, de autorisatiecode in Status en
     * een verzoek in Requests (op het veldje van de Auth Console), de PIN en de desktop van de telefoon, de drie spelletjes
     * van de Bypass Kit en de zak van iemand anders (in het lab van de Bypass Kit), en de terminal van de laptop op de kern
     * van een ander. Allemaal het hele venster, zonder de HUD.
     */
    private static void securityItemPictures(DevScript t) {
        BlockPos desk = VisualCheck.COMPUTER_DESK;
        BlockPos reception = desk.east(4);
        t.step(0, () -> {
            com.senne.imttech.lan.LanGrid.overrideRequired(true);
            Minecraft.getInstance().options.hideGui = true;
        });
        t.onServer(0, player -> VisualCheck.buildComputerDesk(player, desk));
        t.stand(20, desk.getX() + 1.5, desk.getY(), desk.getZ() + 2.5, 180, 10);
        t.step(20, HowToTour::noToasts);
        t.onServer(5, player -> com.senne.imttech.security.os.SecurityOs.open(player, desk));
        t.step(5, () -> mouseTo(1, 1));
        t.step(20, () -> os(screen -> {
            screen.tourCloseAll();
            screen.tourComputers(reception);
        }));
        t.step(20, () -> captureScreen("computers_app"));
        t.close(5);
        t.onServer(5, player -> com.senne.imttech.computer.Computers.use(player, reception.east()));
        t.step(5, () -> mouseTo(1, 1));
        t.step(25, () -> captureScreen("pc_desktop"));
        t.close(5);
        // De laptop: inloggen op die kern, en dan de desktop op afstand (zonder vensters).
        t.onServer(0, player -> player.getInventory().setItem(0, new ItemStack(ModItems.HACKING_LAPTOP.get())));
        t.open(10, () -> new com.senne.imttech.client.os.OsLoginScreen(""));
        t.step(5, () -> login(screen -> screen.tourType(VisualCheck.computerDeskIp, "recept1")));
        t.step(10, () -> captureScreen("ssh_login"));
        t.step(0, () -> login(com.senne.imttech.client.os.OsLoginScreen::tourConnect));
        t.step(30, () -> os(com.senne.imttech.client.os.SecurityOsScreen::tourCloseAll));
        t.step(10, () -> captureScreen("remote_session"));
        t.close(5);
        t.step(0, () -> com.senne.imttech.lan.LanGrid.overrideRequired(false));

        // De autorisatiecode en een verzoek van Bram aan de Auth Console.
        BlockPos yard = VisualCheck.AUTH_YARD;
        t.onServer(0, player -> VisualCheck.buildAuthYard(player, yard));
        t.onServer(30, player -> VisualCheck.wakeAuthServer(player, yard));
        t.stand(20, yard.getX() + 2.5, yard.getY(), yard.getZ() + 2.5, 0, 10);
        t.onServer(10, player -> com.senne.imttech.security.os.SecurityOs.open(player, yard));
        t.step(15, () -> os(screen -> {
            screen.tourCloseAll();
            screen.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.STATUS);
        }));
        t.step(20, () -> captureScreen("auth_status"));
        t.close(5);
        t.onServer(10, player -> VisualCheck.bramAsks(player, yard.offset(3, 0, 4)));
        t.onServer(15, player -> com.senne.imttech.security.os.SecurityOs.open(player, yard));
        t.step(15, () -> os(screen -> {
            screen.tourCloseAll();
            screen.tourOpen(com.senne.imttech.client.os.SecurityOsScreen.App.REQUESTS);
        }));
        t.step(20, () -> captureScreen("auth_requests"));
        t.step(0, () -> os(screen -> screen.tourRequestAnswer(true)));
        t.close(15);
        // De telefoon: verbinden, een PIN kiezen, en de kleine desktop. Bram gaat eerst opzij: hij stond half in beeld
        // achter het inlogscherm.
        t.onServer(0, player -> {
            net.minecraft.server.level.ServerPlayer bram = player.server.getPlayerList().getPlayerByName("Bram");
            if (bram != null) {
                bram.teleportTo(player.serverLevel(), yard.getX() + 9.5, yard.getY(), yard.getZ() + 0.5, 90, 0);
            }
        });
        t.onServer(0, player -> VisualCheck.givePhone(player, yard));
        t.onServer(10, player -> com.senne.imttech.security.os.Phones.use(player, player.getMainHandItem()));
        t.step(15, () -> login(screen -> screen.tourType(VisualCheck.authYardIp, "bitech1")));
        t.step(0, () -> login(com.senne.imttech.client.os.OsLoginScreen::tourConnect));
        t.step(20, () -> login(screen -> screen.tourType("", "1234")));
        t.step(10, () -> captureScreen("phone_pin"));
        t.step(0, () -> login(com.senne.imttech.client.os.OsLoginScreen::tourConnect));
        t.step(25, () -> os(com.senne.imttech.client.os.SecurityOsScreen::tourCloseAll));
        t.step(10, () -> captureScreen("phone_desktop"));
        t.close(5);

        // De Bypass Kit: het stroomcircuit van een lezer (half opgelost), de afdrukken op een paneel, de zak van Lotte, en de
        // draden van een laserzender met de zaklamp erop.
        BlockPos lab = VisualCheck.BYPASS_LAB;
        BlockPos reader = lab.offset(4, 1, 3);
        BlockPos panel = lab.offset(5, 1, 3);
        t.onServer(0, VisualCheck::buildBypassLab);
        t.stand(10, lab.getX() + 4.5, lab.getY(), lab.getZ() + 6.5, 180, 12);
        t.onServer(30, player -> {
            sameEveryTime(player, 11L);
            com.senne.imttech.bypass.Bypass.start(player, reader, net.minecraft.world.InteractionHand.MAIN_HAND);
        });
        t.step(10, () -> screenOf(com.senne.imttech.client.bypass.CircuitScreen.class, screen -> screen.tourChoose("staff")));
        t.step(5, () -> screenOf(com.senne.imttech.client.bypass.CircuitScreen.class, screen -> screen.tourSolve(-1)));
        t.step(10, () -> captureScreen("bypass_circuit"));
        t.close(5);
        t.onServer(20, player -> {
            sameEveryTime(player, 12L);
            com.senne.imttech.bypass.Bypass.start(player, panel, net.minecraft.world.InteractionHand.MAIN_HAND);
        });
        t.step(10, () -> screenOf(com.senne.imttech.client.bypass.FingerprintScreen.class, screen -> screen.tourDust(12)));
        t.step(30, () -> screenOf(com.senne.imttech.client.bypass.FingerprintScreen.class, screen -> {
            screen.tourType("1147");
            screen.tourTry();
        }));
        t.step(10, () -> screenOf(com.senne.imttech.client.bypass.FingerprintScreen.class, screen -> screen.tourType("41")));
        t.step(5, () -> captureScreen("bypass_prints"));
        t.close(5);
        t.open(5, () -> new com.senne.imttech.client.pickpocket.PocketScreen(-1, "Lotte", 3, 7L));
        t.step(5, () -> screenOf(com.senne.imttech.client.pickpocket.PocketScreen.class,
                com.senne.imttech.client.pickpocket.PocketScreen::tourUncover));
        t.step(3, () -> screenOf(com.senne.imttech.client.pickpocket.PocketScreen.class, screen -> screen.tourNotice(0.55f)));
        t.step(5, () -> captureScreen("pickpocket"));
        t.close(5);
        BlockPos laser = lab.offset(8, 0, 4);
        t.onServer(0, VisualCheck::buildBypassLaser);
        t.stand(5, lab.getX() + 4.5, lab.getY(), lab.getZ() + 6.5, 180, 12);
        t.onServer(30, player -> {
            sameEveryTime(player, 13L);
            com.senne.imttech.bypass.Bypass.start(player, laser, net.minecraft.world.InteractionHand.MAIN_HAND);
        });
        t.step(10, () -> screenOf(com.senne.imttech.client.bypass.WireScreen.class, screen -> screen.tourTrace(0.55f)));
        t.step(3, () -> captureScreen("bypass_wires"));
        t.close(5);

        // De terminal: de laptop op een kern die niet van jou is, en hij speelt zichzelf tot de geheugendump.
        t.onServer(0, player -> {
            net.minecraft.server.level.ServerLevel level = player.serverLevel();
            level.setBlockAndUpdate(HACK_CORE.above(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(HACK_CORE, com.senne.imttech.registry.ModBlocks.SECURITY_CORE.get().defaultBlockState());
        });
        t.stand(20, HACK_CORE.getX() + 0.5, HACK_CORE.getY(), HACK_CORE.getZ() + 2.5, 180, 10);
        t.step(0, com.senne.imttech.client.HackScreen::startAutoPlay);
        t.onServer(5, player -> {
            sameEveryTime(player, 14L);
            com.senne.imttech.security.TowerHack.placeLaptop(player, HACK_CORE, new ItemStack(ModItems.HACKING_LAPTOP.get()));
        });
        t.waitUntil(900, () -> Minecraft.getInstance().screen instanceof com.senne.imttech.client.HackScreen hack
                && hack.terminal().output().stream().anyMatch(line -> line.text().startsWith("Still possible")));
        t.step(3, () -> captureScreen("hack_terminal"));
        t.step(0, () -> com.senne.imttech.client.HackScreen.autoPlay = false);
        t.close(5);
        t.step(0, () -> Minecraft.getInstance().options.hideGui = false);
        t.look(0, 0.0f, 10.0f);
    }

    /**
     * Zet het toeval van de wereld op een vast getal, vlak voor iets dat er zelf een kiest (het raster van een lezer, de
     * draden van een laserzender, de woorden van de terminal). Anders is het plaatje bij elke ronde anders, en wijzen de
     * pijltjes in de animatie naar de verkeerde plek (CARD en ACCEPT stonden de tweede keer op een andere rij).
     */
    private static void sameEveryTime(net.minecraft.server.level.ServerPlayer player, long seed) {
        player.serverLevel().random.setSeed(seed);
    }

    /** Doet iets met het inlogscherm (van de laptop of de telefoon) als dat open staat. */
    private static void login(Consumer<com.senne.imttech.client.os.OsLoginScreen> action) {
        screenOf(com.senne.imttech.client.os.OsLoginScreen.class, action);
    }

    /** Doet iets met het scherm dat nu open staat, als het van deze soort is (anders een regel in de log). */
    private static <T> void screenOf(Class<T> kind, Consumer<T> action) {
        Object screen = Minecraft.getInstance().screen;
        if (kind.isInstance(screen)) {
            action.accept(kind.cast(screen));
        } else {
            ImtTech.LOGGER.warn("[visual check] verwacht {}, maar open staat {}", kind.getSimpleName(), screen);
        }
    }

    /** Een paneel in het midden van het scherm (een scherm met vakjes: {@code w} x {@code h}) als plaatje. */
    private static void captureCentered(String name, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            capture(name, (mc.screen.width - w) / 2, (mc.screen.height - h) / 2, w, h);
        } else {
            ImtTech.LOGGER.warn("[visual check] voor het plaatje {} staat geen scherm open", name);
        }
    }

    /** Dit vak van je werkbalk in je hand (ook op de server, zoals met het scrollwiel). */
    private static void select(int slot) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.getConnection() != null) {
            mc.player.getInventory().selected = slot;
            mc.getConnection().send(new net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket(slot));
        }
    }

    /** Weg met de meldingen rechtsboven (een nieuw recept, een prestatie): die staan anders op een plaatje. */
    private static void noToasts() {
        Minecraft.getInstance().getToasts().clear();
    }

    private static final BlockPos DEMO_DOME = DEMO_CORE.offset(-4, 0, 0);

    /** Het diagram op het plaatje "logic_button": een netwerkknop en een netwerkhendel. */
    private static final String BUTTON_CODE = String.join("\n",
            "when button(\"Doorbell\"):",
            "    say(\"Doorbell: \" + player.name)",
            "    pulse(\"Bell\", 1)",
            "",
            "when lever(\"Night mode\"):",
            "    if lever.on:",
            "        off(\"Lobby lights\")",
            "    else:",
            "        on(\"Lobby lights\")");

    private static void os(Consumer<com.senne.imttech.client.os.SecurityOsScreen> action) {
        if (Minecraft.getInstance().screen instanceof com.senne.imttech.client.os.SecurityOsScreen screen) {
            action.accept(screen);
        } else {
            ImtTech.LOGGER.warn("[visual check] de desktop staat niet open (maar {})", Minecraft.getInstance().screen);
        }
    }

    /** Het hele venster als plaatje (de desktop vult het hele scherm). */
    private static void captureScreen(String name) {
        // Ook zonder scherm (de HUD van de bril): het hele venster.
        Minecraft mc = Minecraft.getInstance();
        capture(name, 0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
    }

    /**
     * Een batterij van 5 x 4 x 5 met 18 cellen, voor een derde vol, met twee zonnepanelen en een warmtegenerator, en een
     * paar apparaten. De plek bestaat niet (geen poort), dus de server stuurt er geen echte cijfers overheen.
     */
    private static com.senne.imttech.network.BatteryStatsPayload demoBattery() {
        long capacity = 18L * Math.round(com.senne.imttech.config.ImtConfig.Power.BATTERY_CELL_CAPACITY);
        int history = com.senne.imttech.battery.BatteryPortBlockEntity.HISTORY;
        int[] made = new int[history];
        int[] used = new int[history];
        for (int i = 0; i < history; i++) {
            made[i] = (int) Math.round(148 * 100 * (1.0 + 0.22 * Math.sin(i / 6.0)));
            used[i] = (int) Math.round(46 * 100 * (1.0 + 0.5 * Math.sin(i / 4.0 + 1.0)));
        }
        List<com.senne.imttech.network.BatteryStatsPayload.Entry> generators = List.of(
                new com.senne.imttech.network.BatteryStatsPayload.Entry("Heat generator", 1, 100.0f),
                new com.senne.imttech.network.BatteryStatsPayload.Entry("Solar panel", 2, 48.0f));
        List<com.senne.imttech.network.BatteryStatsPayload.Entry> devices = List.of(
                new com.senne.imttech.network.BatteryStatsPayload.Entry("Laser emitter", 3, 30.0f),
                new com.senne.imttech.network.BatteryStatsPayload.Entry("Security camera", 2, 10.0f),
                new com.senne.imttech.network.BatteryStatsPayload.Entry("Holo table", 1, 6.0f));
        return new com.senne.imttech.network.BatteryStatsPayload(BlockPos.ZERO.below(64), true, true, "", "5 x 4 x 5", 18,
                capacity / 3 + 12_345, capacity, 148.0f, 46.0f, 0.0f, generators, devices, made, used);
    }

    /**
     * Knipt het stuk ({@code x}, {@code y}, {@code w}, {@code h}) (in de maat van het scherm) uit het laatste beeld en zet
     * het in de mod als {@code textures/gui/howto/<name>.png}.
     */
    static void capture(String name, int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        int px = (int) Math.round(x * scale);
        int py = (int) Math.round(y * scale);
        int pw = (int) Math.round(w * scale);
        int ph = (int) Math.round(h * scale);
        try (NativeImage full = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            // Niet buiten het beeld: de breedte van het scherm is naar boven afgerond (1280 / 3 = 427).
            px = Math.max(0, Math.min(px, full.getWidth() - 1));
            py = Math.max(0, Math.min(py, full.getHeight() - 1));
            pw = Math.min(pw, full.getWidth() - px);
            ph = Math.min(ph, full.getHeight() - py);
            writeCrop(full, px, py, pw, ph, name);
        } catch (IOException | RuntimeException e) {
            ImtTech.LOGGER.error("[visual check] kon het plaatje {} niet maken", name, e);
        }
    }

    private static void writeCrop(NativeImage full, int px, int py, int pw, int ph, String name) throws IOException {
        Minecraft mc = Minecraft.getInstance();
        Path out = mc.gameDirectory.toPath().toAbsolutePath().normalize().getParent()
                .resolve("src/main/resources/assets/" + ImtTech.MODID + "/textures/gui/howto/" + name + ".png");
        try (NativeImage crop = new NativeImage(pw, ph, false)) {
            full.copyRect(crop, px, py, 0, 0, pw, ph, false, false);
            Files.createDirectories(out.getParent());
            crop.writeToFile(out);
            // Met "blur" is het plaatje verkleind in de animatie niet korrelig.
            Path meta = out.resolveSibling(name + ".png.mcmeta");
            if (!Files.exists(meta)) {
                Files.writeString(meta, String.join("\n", "{", "  \"texture\": {", "    \"blur\": true", "  }", "}", ""));
            }
            ImtTech.LOGGER.info("[visual check] plaatje voor How it works: {} ({} x {})", out, pw, ph);
        } catch (IOException | RuntimeException e) {
            ImtTech.LOGGER.error("[visual check] kon het plaatje {} niet maken", name, e);
        }
    }
}
