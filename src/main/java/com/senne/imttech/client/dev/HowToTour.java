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
                be.parts().setItem(0, new ItemStack(ModItems.SERVER_CPU.get()));
                be.parts().setItem(1, new ItemStack(ModItems.SERVER_RAM.get()));
                be.parts().setItem(2, new ItemStack(ModItems.SERVER_SSD.get()));
                be.parts().setItem(3, new ItemStack(ModItems.SERVER_FAN.get()));
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
        // Het scherm van een serverrek (het rek naast de kern, met een CPU, RAM, een SSD en een fan).
        t.onServer(5, player -> {
            // Net zoals het rek het zelf doet: met zijn plek erbij (anders verbreekt het spel de verbinding).
            if (player.serverLevel().getBlockEntity(DEMO_CORE.east()) instanceof net.minecraft.world.MenuProvider rack) {
                player.openMenu(rack, buf -> buf.writeBlockPos(DEMO_CORE.east()));
            }
        });
        t.step(15, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                capture("server_rack", (mc.screen.width - 176) / 2, (mc.screen.height - 190) / 2, 176, 190);
            }
        });
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
