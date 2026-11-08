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
                int[] tile = index.devTileCenter(0);
                if (tile != null) {
                    mouseTo(tile[0], tile[1]);
                }
            }
        });
        t.shot(5, "ht18_overview_hover");
        t.step(0, () -> {
            if (Minecraft.getInstance().screen instanceof HowToIndexScreen index) {
                int[] tile = index.devTileCenter(0);
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
        Path out = mc.gameDirectory.toPath().toAbsolutePath().normalize().getParent()
                .resolve("src/main/resources/assets/" + ImtTech.MODID + "/textures/gui/howto/" + name + ".png");
        try (NativeImage full = Screenshot.takeScreenshot(mc.getMainRenderTarget());
             NativeImage crop = new NativeImage(pw, ph, false)) {
            full.copyRect(crop, px, py, 0, 0, pw, ph, false, false);
            Files.createDirectories(out.getParent());
            crop.writeToFile(out);
            ImtTech.LOGGER.info("[visual check] plaatje voor How it works: {} ({} x {})", out, pw, ph);
        } catch (IOException | RuntimeException e) {
            ImtTech.LOGGER.error("[visual check] kon het plaatje {} niet maken", name, e);
        }
    }
}
